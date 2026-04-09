package app

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"
)

const (
	membershipTermDays   = 30
	topupPackRemaining   = 100
	topupPackPrice       = 6.0
	topupPackActiveLimit = 1
	plusTierPrice        = 0.0
	proTierPrice         = 0.0
)

var tierLimits = map[Tier]int{
	TierFree: 6,
	TierPlus: 25,
	TierPro:  40,
}

type sqlExecer interface {
	ExecContext(context.Context, string, ...any) (sql.Result, error)
	QueryRowContext(context.Context, string, ...any) *sql.Row
}

func (s *Store) EnsureUser(ctx context.Context, userID string, tierHint Tier) error {
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO user_entitlement(user_id, tier, tier_expire_at, updated_at)
		 VALUES (?, ?, NULL, ?)
		 ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at), tier = COALESCE(user_entitlement.tier, VALUES(tier))`,
		userID,
		string(tierHint),
		time.Now().UnixMilli(),
	)
	return err
}

func (s *Store) GetTierForUser(ctx context.Context, userID string, fallback Tier) (Tier, *int64, error) {
	var tier sql.NullString
	var expireAt sql.NullInt64
	err := s.db.QueryRowContext(
		ctx,
		"SELECT tier, tier_expire_at FROM user_entitlement WHERE user_id = ? LIMIT 1",
		userID,
	).Scan(&tier, &expireAt)
	if err == sql.ErrNoRows {
		return fallback, nil, nil
	}
	if err != nil {
		return "", nil, err
	}

	resultTier := fallback
	switch Tier(tier.String) {
	case TierFree, TierPlus, TierPro:
		resultTier = Tier(tier.String)
	}
	if !expireAt.Valid {
		return resultTier, nil, nil
	}
	value := expireAt.Int64
	return resultTier, &value, nil
}

func (s *Store) GetDailyStatus(ctx context.Context, userID string, tier Tier, dayCN string) (DailyQuotaStatus, error) {
	used, err := s.getOrCreateDailyUsage(ctx, nil, userID, dayCN)
	if err != nil {
		return DailyQuotaStatus{}, err
	}
	limit := tierLimits[tier]
	return DailyQuotaStatus{
		DayCN:     dayCN,
		Tier:      tier,
		Used:      used,
		Limit:     limit,
		Remaining: maxInt(0, limit-used),
	}, nil
}

func (s *Store) WasProcessed(ctx context.Context, userID string, clientMsgID string) (bool, error) {
	var ledgerID int64
	err := s.db.QueryRowContext(
		ctx,
		"SELECT id FROM quota_ledger WHERE user_id = ? AND client_msg_id = ? LIMIT 1",
		userID,
		clientMsgID,
	).Scan(&ledgerID)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return true, nil
}

func (s *Store) ConsumeOnDone(ctx context.Context, userID string, tier Tier, clientMsgID string, dayCN string) (ConsumeResult, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return ConsumeResult{}, err
	}
	defer rollbackQuietly(tx)

	var ledgerID int64
	err = tx.QueryRowContext(
		ctx,
		"SELECT id FROM quota_ledger WHERE user_id = ? AND client_msg_id = ? LIMIT 1 FOR UPDATE",
		userID,
		clientMsgID,
	).Scan(&ledgerID)
	if err == nil {
		if err := tx.Commit(); err != nil {
			return ConsumeResult{}, err
		}
		status, err := s.GetDailyStatus(ctx, userID, tier, dayCN)
		if err != nil {
			return ConsumeResult{}, err
		}
		return ConsumeResult{
			Deducted: false,
			Status:   status,
		}, nil
	}
	if err != nil && err != sql.ErrNoRows {
		return ConsumeResult{}, err
	}

	used, err := s.getOrCreateDailyUsage(ctx, tx, userID, dayCN)
	if err != nil {
		return ConsumeResult{}, err
	}

	now := time.Now().UnixMilli()
	var source *QuotaSource
	if used < tierLimits[tier] {
		if _, err := tx.ExecContext(
			ctx,
			"UPDATE daily_usage SET used = used + 1 WHERE user_id = ? AND day_cn = ?",
			userID,
			dayCN,
		); err != nil {
			return ConsumeResult{}, err
		}
		value := QuotaSourceDaily
		source = &value
	} else {
		value, err := s.consumeOverflowQuota(ctx, tx, userID, now)
		if err != nil {
			return ConsumeResult{}, err
		}
		source = value
	}

	if source == nil {
		return ConsumeResult{}, fmt.Errorf("QUOTA_EXHAUSTED")
	}

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO quota_ledger(user_id, client_msg_id, day_cn, source, delta, created_at)
		 VALUES (?, ?, ?, ?, 1, ?)`,
		userID,
		clientMsgID,
		dayCN,
		string(*source),
		now,
	); err != nil {
		return ConsumeResult{}, err
	}
	if err := tx.Commit(); err != nil {
		return ConsumeResult{}, err
	}

	status, err := s.GetDailyStatus(ctx, userID, tier, dayCN)
	if err != nil {
		return ConsumeResult{}, err
	}
	return ConsumeResult{
		Deducted: true,
		Source:   source,
		Status:   status,
	}, nil
}

func (s *Store) GetTopupStatus(ctx context.Context, userID string) (int, *int64, error) {
	var total sql.NullInt64
	var earliest sql.NullInt64
	now := time.Now().UnixMilli()
	err := s.db.QueryRowContext(
		ctx,
		`SELECT COALESCE(SUM(remaining),0) AS total_remaining, MIN(expire_at) AS earliest_expire_at
		 FROM topup_packs
		 WHERE user_id = ? AND status = 'active' AND remaining > 0 AND (expire_at IS NULL OR expire_at > ?)`,
		userID,
		now,
	).Scan(&total, &earliest)
	if err != nil {
		return 0, nil, err
	}
	if !earliest.Valid {
		return int(total.Int64), nil, nil
	}
	value := earliest.Int64
	return int(total.Int64), &value, nil
}

func (s *Store) GetUpgradeRemaining(ctx context.Context, userID string) (int, error) {
	var remaining sql.NullInt64
	var expireAt sql.NullInt64
	err := s.db.QueryRowContext(
		ctx,
		"SELECT remaining, expire_at FROM upgrade_credits WHERE user_id = ? LIMIT 1",
		userID,
	).Scan(&remaining, &expireAt)
	if err == sql.ErrNoRows {
		return 0, nil
	}
	if err != nil {
		return 0, err
	}
	now := time.Now().UnixMilli()
	if expireAt.Valid && expireAt.Int64 <= now {
		return 0, nil
	}
	return maxInt(0, int(remaining.Int64)), nil
}

func (s *Store) RenewPlus(ctx context.Context, userID string, orderID string) (bool, Tier, int64, error) {
	return s.renewTier(ctx, userID, orderID, TierPlus)
}

func (s *Store) RenewPro(ctx context.Context, userID string, orderID string) (bool, Tier, int64, error) {
	return s.renewTier(ctx, userID, orderID, TierPro)
}

func (s *Store) BuyTopupPack(ctx context.Context, userID string, orderID string) (bool, string, *int64, int, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return false, "", nil, 0, err
	}
	defer rollbackQuietly(tx)

	if replay, payload, err := s.readOrderReplay(ctx, tx, orderID, userID); err != nil {
		return false, "", nil, 0, err
	} else if replay {
		if err := tx.Commit(); err != nil {
			return false, "", nil, 0, err
		}
		return true, asString(payload["pack_id"]), payloadOptionalInt64(payload["expire_at"]), int(payloadFloat64(payload["remaining"])), nil
	}

	var tier sql.NullString
	err = tx.QueryRowContext(
		ctx,
		"SELECT tier FROM user_entitlement WHERE user_id = ? LIMIT 1 FOR UPDATE",
		userID,
	).Scan(&tier)
	if err != nil && err != sql.ErrNoRows {
		return false, "", nil, 0, err
	}
	if Tier(tier.String) != TierPlus && Tier(tier.String) != TierPro {
		return false, "", nil, 0, fmt.Errorf("FORBIDDEN_TIER")
	}

	var activeCount sql.NullInt64
	now := time.Now().UnixMilli()
	if err := tx.QueryRowContext(
		ctx,
		`SELECT COUNT(1) AS cnt
		 FROM topup_packs
		 WHERE user_id = ? AND status = 'active' AND remaining > 0 AND (expire_at IS NULL OR expire_at > ?)`,
		userID,
		now,
	).Scan(&activeCount); err != nil {
		return false, "", nil, 0, err
	}
	if activeCount.Int64 >= topupPackActiveLimit {
		return false, "", nil, 0, fmt.Errorf("TOPUP_LIMIT_REACHED")
	}

	packID := "pack_" + orderID
	result := map[string]any{
		"pack_id":   packID,
		"expire_at": nil,
		"remaining": topupPackRemaining,
	}
	resultJSON, _ := json.Marshal(result)

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO topup_packs(pack_id, user_id, remaining, expire_at, status, created_at)
		 VALUES (?, ?, ?, ?, 'active', ?)`,
		packID,
		userID,
		topupPackRemaining,
		nil,
		now,
	); err != nil {
		return false, "", nil, 0, err
	}
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO orders(order_id, user_id, type, amount, created_at, status, result_json)
		 VALUES (?, ?, 'buy_topup', ?, ?, 'success', ?)`,
		orderID,
		userID,
		topupPackPrice,
		now,
		string(resultJSON),
	); err != nil {
		return false, "", nil, 0, err
	}
	if err := tx.Commit(); err != nil {
		return false, "", nil, 0, err
	}
	return false, packID, nil, topupPackRemaining, nil
}

func (s *Store) UpgradePlusToPro(ctx context.Context, userID string, orderID string) (bool, int, Tier, int64, int, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return false, 0, "", 0, 0, err
	}
	defer rollbackQuietly(tx)

	if replay, payload, err := s.readOrderReplay(ctx, tx, orderID, userID); err != nil {
		return false, 0, "", 0, 0, err
	} else if replay {
		if err := tx.Commit(); err != nil {
			return false, 0, "", 0, 0, err
		}
		return true,
			int(payloadFloat64(payload["compensation"])),
			Tier(asString(payload["tier"])),
			payloadInt64(payload["tier_expire_at"]),
			int(payloadFloat64(payload["upgrade_remaining"])),
			nil
	}

	var currentTier sql.NullString
	var tierExpireAt sql.NullInt64
	err = tx.QueryRowContext(
		ctx,
		"SELECT tier, tier_expire_at FROM user_entitlement WHERE user_id = ? LIMIT 1 FOR UPDATE",
		userID,
	).Scan(&currentTier, &tierExpireAt)
	if err != nil && err != sql.ErrNoRows {
		return false, 0, "", 0, 0, err
	}

	switch Tier(currentTier.String) {
	case TierPro:
		return false, 0, "", 0, 0, fmt.Errorf("ALREADY_PRO")
	case TierPlus:
	default:
		return false, 0, "", 0, 0, fmt.Errorf("FORBIDDEN_TIER")
	}

	now := time.Now().UnixMilli()
	dayCN := GetTodayKeyCN(s.shanghai, time.Now())
	usedToday, err := s.getOrCreateDailyUsage(ctx, tx, userID, dayCN)
	if err != nil {
		return false, 0, "", 0, 0, err
	}
	todayRemainingPlus := maxInt(0, tierLimits[TierPlus]-usedToday)

	expireAtOld := now
	if tierExpireAt.Valid {
		expireAtOld = tierExpireAt.Int64
	}
	remainingFullDays := maxInt(0, dayIndexFromTsCN(s.shanghai, expireAtOld)-dayIndexFromTsCN(s.shanghai, now))
	compensation := todayRemainingPlus + remainingFullDays*tierLimits[TierPlus]
	newTierExpireAt := addDays(now, membershipTermDays)

	if _, err := tx.ExecContext(
		ctx,
		"UPDATE user_entitlement SET tier = ?, tier_expire_at = ?, updated_at = ? WHERE user_id = ?",
		string(TierPro),
		newTierExpireAt,
		now,
		userID,
	); err != nil {
		return false, 0, "", 0, 0, err
	}
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO upgrade_credits(user_id, remaining, expire_at, updated_at)
		 VALUES (?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   remaining = remaining + VALUES(remaining),
		   expire_at = NULL,
		   updated_at = VALUES(updated_at)`,
		userID,
		compensation,
		nil,
		now,
	); err != nil {
		return false, 0, "", 0, 0, err
	}

	var upgradeRemaining sql.NullInt64
	if err := tx.QueryRowContext(
		ctx,
		"SELECT remaining FROM upgrade_credits WHERE user_id = ? LIMIT 1",
		userID,
	).Scan(&upgradeRemaining); err != nil {
		return false, 0, "", 0, 0, err
	}

	result := map[string]any{
		"compensation":      compensation,
		"tier":              string(TierPro),
		"tier_expire_at":    newTierExpireAt,
		"upgrade_remaining": int(upgradeRemaining.Int64),
	}
	resultJSON, _ := json.Marshal(result)
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO orders(order_id, user_id, type, amount, created_at, status, result_json)
		 VALUES (?, ?, 'upgrade_plus_to_pro', 29.9, ?, 'success', ?)`,
		orderID,
		userID,
		now,
		string(resultJSON),
	); err != nil {
		return false, 0, "", 0, 0, err
	}

	if err := tx.Commit(); err != nil {
		return false, 0, "", 0, 0, err
	}
	return false, compensation, TierPro, newTierExpireAt, int(upgradeRemaining.Int64), nil
}

func (s *Store) renewTier(ctx context.Context, userID string, orderID string, targetTier Tier) (bool, Tier, int64, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return false, "", 0, err
	}
	defer rollbackQuietly(tx)

	if replay, payload, err := s.readOrderReplay(ctx, tx, orderID, userID); err != nil {
		return false, "", 0, err
	} else if replay {
		if err := tx.Commit(); err != nil {
			return false, "", 0, err
		}
		return true, Tier(asString(payload["tier"])), payloadInt64(payload["tier_expire_at"]), nil
	}

	var currentTier sql.NullString
	var tierExpireAt sql.NullInt64
	err = tx.QueryRowContext(
		ctx,
		"SELECT tier, tier_expire_at FROM user_entitlement WHERE user_id = ? LIMIT 1 FOR UPDATE",
		userID,
	).Scan(&currentTier, &tierExpireAt)
	if err != nil && err != sql.ErrNoRows {
		return false, "", 0, err
	}

	switch {
	case targetTier == TierPlus && Tier(currentTier.String) == TierPro:
		return false, "", 0, fmt.Errorf("FORBIDDEN_TIER")
	case targetTier == TierPro && Tier(currentTier.String) == TierPlus:
		return false, "", 0, fmt.Errorf("USE_UPGRADE_PLUS_TO_PRO")
	}

	now := time.Now().UnixMilli()
	currentExpireAt := int64(0)
	if tierExpireAt.Valid {
		currentExpireAt = tierExpireAt.Int64
	}
	newTierExpireAt := extendTierExpireAt(now, currentExpireAt)

	if _, err := tx.ExecContext(
		ctx,
		"UPDATE user_entitlement SET tier = ?, tier_expire_at = ?, updated_at = ? WHERE user_id = ?",
		string(targetTier),
		newTierExpireAt,
		now,
		userID,
	); err != nil {
		return false, "", 0, err
	}

	amount := plusTierPrice
	orderType := "renew_plus"
	if targetTier == TierPro {
		amount = proTierPrice
		orderType = "renew_pro"
	}
	result := map[string]any{
		"tier":           string(targetTier),
		"tier_expire_at": newTierExpireAt,
	}
	resultJSON, _ := json.Marshal(result)
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO orders(order_id, user_id, type, amount, created_at, status, result_json)
		 VALUES (?, ?, ?, ?, ?, 'success', ?)`,
		orderID,
		userID,
		orderType,
		amount,
		now,
		string(resultJSON),
	); err != nil {
		return false, "", 0, err
	}

	if err := tx.Commit(); err != nil {
		return false, "", 0, err
	}
	return false, targetTier, newTierExpireAt, nil
}

func (s *Store) consumeOverflowQuota(ctx context.Context, tx *sql.Tx, userID string, now int64) (*QuotaSource, error) {
	var upgradeRemaining sql.NullInt64
	err := tx.QueryRowContext(
		ctx,
		`SELECT remaining
		 FROM upgrade_credits
		 WHERE user_id = ? AND remaining > 0 AND (expire_at IS NULL OR expire_at > ?)
		 LIMIT 1
		 FOR UPDATE`,
		userID,
		now,
	).Scan(&upgradeRemaining)
	if err == nil {
		if _, err := tx.ExecContext(
			ctx,
			"UPDATE upgrade_credits SET remaining = remaining - 1, updated_at = ? WHERE user_id = ?",
			now,
			userID,
		); err != nil {
			return nil, err
		}
		value := QuotaSourceUpgrade
		return &value, nil
	}
	if err != nil && err != sql.ErrNoRows {
		return nil, err
	}

	var packID sql.NullString
	err = tx.QueryRowContext(
		ctx,
		`SELECT pack_id
		 FROM topup_packs
		 WHERE user_id = ? AND status = 'active' AND remaining > 0 AND (expire_at IS NULL OR expire_at > ?)
		 ORDER BY CASE WHEN expire_at IS NULL THEN 1 ELSE 0 END ASC, expire_at ASC, created_at ASC
		 LIMIT 1
		 FOR UPDATE`,
		userID,
		now,
	).Scan(&packID)
	if err == nil {
		if _, err := tx.ExecContext(
			ctx,
			`UPDATE topup_packs
			 SET remaining = remaining - 1,
			     status = CASE WHEN remaining - 1 <= 0 THEN 'used_up' ELSE status END
			 WHERE pack_id = ?`,
			packID.String,
		); err != nil {
			return nil, err
		}
		value := QuotaSourceTopup
		return &value, nil
	}
	if err != nil && err != sql.ErrNoRows {
		return nil, err
	}
	return nil, nil
}

func (s *Store) getOrCreateDailyUsage(ctx context.Context, tx *sql.Tx, userID string, dayCN string) (int, error) {
	execer := sqlExecer(s.db)
	if tx != nil {
		execer = tx
	}

	if _, err := execContext(execer, ctx,
		`INSERT INTO daily_usage(user_id, day_cn, used) VALUES (?, ?, 0)
		 ON DUPLICATE KEY UPDATE user_id = user_id`,
		userID,
		dayCN,
	); err != nil {
		return 0, err
	}

	var used sql.NullInt64
	if err := queryRowContext(execer, ctx,
		"SELECT used FROM daily_usage WHERE user_id = ? AND day_cn = ? LIMIT 1",
		userID,
		dayCN,
	).Scan(&used); err != nil {
		return 0, err
	}
	return int(used.Int64), nil
}

func (s *Store) readOrderReplay(ctx context.Context, tx *sql.Tx, orderID string, userID string) (bool, map[string]any, error) {
	var existingOrderID sql.NullString
	var resultJSON sql.NullString
	err := tx.QueryRowContext(
		ctx,
		"SELECT order_id, result_json FROM orders WHERE order_id = ? AND user_id = ? LIMIT 1 FOR UPDATE",
		orderID,
		userID,
	).Scan(&existingOrderID, &resultJSON)
	if err == sql.ErrNoRows {
		return false, nil, nil
	}
	if err != nil {
		return false, nil, err
	}

	payload := map[string]any{}
	if resultJSON.Valid && strings.TrimSpace(resultJSON.String) != "" {
		if err := json.Unmarshal([]byte(resultJSON.String), &payload); err != nil {
			return false, nil, err
		}
	}
	return true, payload, nil
}

func GetTodayKeyCN(loc *time.Location, date time.Time) string {
	if loc == nil {
		loc = time.FixedZone("Asia/Shanghai", 8*60*60)
	}
	return date.In(loc).Format("20060102")
}

func addDays(ts int64, days int) int64 {
	return ts + int64(days)*24*60*60*1000
}

func extendTierExpireAt(now int64, currentExpireAt int64) int64 {
	base := now
	if currentExpireAt > now {
		base = currentExpireAt
	}
	return addDays(base, membershipTermDays)
}

func dayIndexFromTsCN(loc *time.Location, ts int64) int {
	if loc == nil {
		loc = time.FixedZone("Asia/Shanghai", 8*60*60)
	}
	date := time.UnixMilli(ts).In(loc)
	year, month, day := date.Date()
	utc := time.Date(year, month, day, 0, 0, 0, 0, time.UTC)
	return int(utc.Unix() / 86400)
}

func execContext(execer sqlExecer, ctx context.Context, query string, args ...any) (sql.Result, error) {
	return execer.ExecContext(ctx, query, args...)
}

func queryRowContext(execer sqlExecer, ctx context.Context, query string, args ...any) *sql.Row {
	return execer.QueryRowContext(ctx, query, args...)
}

func payloadFloat64(value any) float64 {
	switch v := value.(type) {
	case float64:
		return v
	case int:
		return float64(v)
	case int64:
		return float64(v)
	default:
		return 0
	}
}

func payloadInt64(value any) int64 {
	switch v := value.(type) {
	case float64:
		return int64(v)
	case int:
		return int64(v)
	case int64:
		return v
	default:
		return 0
	}
}

func payloadOptionalInt64(value any) *int64 {
	switch v := value.(type) {
	case float64:
		result := int64(v)
		return &result
	case int64:
		return &v
	case nil:
		return nil
	default:
		return nil
	}
}

func asString(value any) string {
	text, _ := value.(string)
	return text
}

func maxInt(a int, b int) int {
	if a > b {
		return a
	}
	return b
}

func requireTier(tier Tier) error {
	switch tier {
	case TierFree, TierPlus, TierPro:
		return nil
	default:
		return errors.New("invalid tier")
	}
}

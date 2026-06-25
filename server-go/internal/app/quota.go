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
	topupPackRemaining   = 80
	topupPackPrice       = 6.0
	topupPackActiveLimit = 1
	plusTierPrice        = 19.9
	proTierPrice         = 29.9
)

var tierLimits = map[Tier]int{
	TierFree: 6,
	TierPlus: 25,
	TierPro:  40,
}

var ErrOrderIDConflict = errors.New("order id already belongs to another user or product")

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

	return effectiveTierFromRow(tier, expireAt, fallback, time.Now().UnixMilli())
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
	return s.consumeOnDoneAt(ctx, userID, tier, clientMsgID, dayCN, time.Now().UnixMilli())
}

func (s *Store) consumeOnDoneAt(ctx context.Context, userID string, tier Tier, clientMsgID string, dayCN string, now int64) (ConsumeResult, error) {
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

type QuotaConsumeOutboxJob struct {
	ID           int64
	UserID       string
	ClientMsgID  string
	DayCN        string
	Tier         Tier
	CompletionAt int64
	Attempts     int
	CreatedAt    int64
}

type QuotaConsumeOutboxAdminEntry struct {
	ID            int64  `json:"id"`
	UserID        string `json:"user_id"`
	ClientMsgID   string `json:"client_msg_id"`
	DayCN         string `json:"day_cn"`
	Tier          string `json:"tier_at_completion"`
	CompletionAt  int64  `json:"completion_at"`
	Status        string `json:"status"`
	Attempts      int    `json:"attempts"`
	LastError     string `json:"last_error,omitempty"`
	NextAttemptAt int64  `json:"next_attempt_at,omitempty"`
	RepairedAt    int64  `json:"repaired_at,omitempty"`
	CreatedAt     int64  `json:"created_at"`
	UpdatedAt     int64  `json:"updated_at"`
}

func (s *Store) insertPendingQuotaConsumeOutboxTx(ctx context.Context, tx *sql.Tx, userID string, clientMsgID string, tier Tier, dayCN string, completionAt int64) error {
	if strings.TrimSpace(userID) == "" || strings.TrimSpace(clientMsgID) == "" || strings.TrimSpace(dayCN) == "" {
		return nil
	}
	if _, ok := tierLimits[tier]; !ok {
		tier = TierFree
	}
	_, err := tx.ExecContext(
		ctx,
		`INSERT INTO quota_consume_outbox(
		   user_id, client_msg_id, day_cn, tier_at_completion, completion_at,
		   status, attempts, next_attempt_at, created_at, updated_at
		 )
		 VALUES (?, ?, ?, ?, ?, 'pending', 0, 0, ?, ?)
		 ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)`,
		userID,
		clientMsgID,
		dayCN,
		string(tier),
		completionAt,
		completionAt,
		completionAt,
	)
	return err
}

func (s *Store) MarkQuotaConsumeOutboxDone(ctx context.Context, userID string, clientMsgID string, repairedAt int64) error {
	_, err := s.db.ExecContext(
		ctx,
		`UPDATE quota_consume_outbox
		 SET status = 'done',
		     last_error = NULL,
		     next_attempt_at = 0,
		     repaired_at = ?,
		     updated_at = ?
		 WHERE user_id = ? AND client_msg_id = ? AND status IN ('pending','failed','needs_ops')`,
		repairedAt,
		repairedAt,
		userID,
		clientMsgID,
	)
	return err
}

func (s *Store) MarkQuotaConsumeOutboxFailed(ctx context.Context, userID string, clientMsgID string, lastError string, nextAttemptAt int64, nowMs int64) error {
	_, err := s.db.ExecContext(
		ctx,
		`UPDATE quota_consume_outbox
		 SET status = 'failed',
		     attempts = attempts + 1,
		     last_error = ?,
		     next_attempt_at = ?,
		     updated_at = ?
		 WHERE user_id = ? AND client_msg_id = ? AND status IN ('pending','failed')`,
		truncateRunes(strings.TrimSpace(lastError), 255),
		nextAttemptAt,
		nowMs,
		userID,
		clientMsgID,
	)
	return err
}

func (s *Store) MarkQuotaConsumeOutboxNeedsOps(ctx context.Context, userID string, clientMsgID string, lastError string, nextAttemptAt int64, nowMs int64) error {
	_, err := s.db.ExecContext(
		ctx,
		`UPDATE quota_consume_outbox
		 SET status = 'needs_ops',
		     attempts = attempts + 1,
		     last_error = ?,
		     next_attempt_at = ?,
		     updated_at = ?
		 WHERE user_id = ? AND client_msg_id = ? AND status IN ('pending','failed','needs_ops')`,
		truncateRunes(strings.TrimSpace(lastError), 255),
		nextAttemptAt,
		nowMs,
		userID,
		clientMsgID,
	)
	return err
}

func (s *Store) MarkQuotaConsumeOutboxUncollectable(ctx context.Context, id int64, lastError string, nowMs int64) error {
	if id <= 0 {
		return nil
	}
	_, err := s.db.ExecContext(
		ctx,
		`UPDATE quota_consume_outbox
		 SET status = 'uncollectable',
		     last_error = ?,
		     next_attempt_at = 0,
		     repaired_at = ?,
		     updated_at = ?
		 WHERE id = ? AND status IN ('pending','failed','needs_ops')`,
		truncateRunes(strings.TrimSpace(lastError), 255),
		nowMs,
		nowMs,
		id,
	)
	return err
}

func (s *Store) MarkQuotaConsumeOutboxUncollectableAfterAttempt(ctx context.Context, id int64, lastError string, attempts int, nowMs int64) error {
	if id <= 0 {
		return nil
	}
	if attempts <= 0 {
		return s.MarkQuotaConsumeOutboxUncollectable(ctx, id, lastError, nowMs)
	}
	_, err := s.db.ExecContext(
		ctx,
		`UPDATE quota_consume_outbox
		 SET status = 'uncollectable',
		     attempts = CASE WHEN attempts < ? THEN ? ELSE attempts END,
		     last_error = ?,
		     next_attempt_at = 0,
		     repaired_at = ?,
		     updated_at = ?
		 WHERE id = ? AND status IN ('pending','failed','needs_ops')`,
		attempts,
		attempts,
		truncateRunes(strings.TrimSpace(lastError), 255),
		nowMs,
		nowMs,
		id,
	)
	return err
}

func (s *Store) UpdateQuotaConsumeOutboxAdminStatus(ctx context.Context, id int64, status string, note string, nowMs int64) (QuotaConsumeOutboxAdminEntry, bool, error) {
	status = normalizeQuotaConsumeOutboxAdminStatus(status)
	if id <= 0 || status == "" {
		return QuotaConsumeOutboxAdminEntry{}, false, nil
	}
	var query string
	var args []any
	switch status {
	case "pending":
		query = `UPDATE quota_consume_outbox
		          SET status = 'pending',
		              attempts = 0,
		              last_error = NULLIF(?, ''),
		              next_attempt_at = 0,
		              repaired_at = NULL,
		              updated_at = ?
		        WHERE id = ? AND status IN ('pending','failed','needs_ops')`
		args = []any{truncateRunes(strings.TrimSpace(note), 255), nowMs, id}
	case "waived", "uncollectable":
		query = `UPDATE quota_consume_outbox
		          SET status = ?,
		              last_error = ?,
		              next_attempt_at = 0,
		              repaired_at = ?,
		              updated_at = ?
		        WHERE id = ? AND status = 'needs_ops'`
		args = []any{status, truncateRunes(strings.TrimSpace(note), 255), nowMs, nowMs, id}
	default:
		return QuotaConsumeOutboxAdminEntry{}, false, nil
	}
	result, err := s.db.ExecContext(ctx, query, args...)
	if err != nil {
		return QuotaConsumeOutboxAdminEntry{}, false, err
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return QuotaConsumeOutboxAdminEntry{}, false, err
	}
	if rows == 0 {
		return QuotaConsumeOutboxAdminEntry{}, false, nil
	}
	entry, found, err := s.GetQuotaConsumeOutboxAdminEntry(ctx, id)
	if err != nil {
		return QuotaConsumeOutboxAdminEntry{}, false, err
	}
	return entry, found, nil
}

func (s *Store) ClaimDueQuotaConsumeOutboxForRepair(ctx context.Context, id int64, nowMs int64, leaseUntilMs int64) (bool, error) {
	if id <= 0 {
		return false, nil
	}
	if leaseUntilMs <= nowMs {
		leaseUntilMs = nowMs + int64(defaultQuotaConsumeRepairTimeout/time.Millisecond)
	}
	result, err := s.db.ExecContext(
		ctx,
		`UPDATE quota_consume_outbox
		   SET status = 'pending',
		       next_attempt_at = ?,
		       updated_at = ?
		 WHERE id = ?
		   AND (
		     (status IN ('pending','failed') AND next_attempt_at <= ?)
		     OR (status = 'needs_ops' AND next_attempt_at <= ?)
		   )`,
		leaseUntilMs,
		nowMs,
		id,
		nowMs,
		nowMs,
	)
	if err != nil {
		return false, err
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return false, err
	}
	return rows > 0, nil
}

func normalizeQuotaConsumeOutboxAdminStatus(status string) string {
	switch strings.TrimSpace(strings.ToLower(status)) {
	case "pending", "waived", "uncollectable":
		return strings.TrimSpace(strings.ToLower(status))
	default:
		return ""
	}
}

func (s *Store) ListDueQuotaConsumeOutbox(ctx context.Context, limit int, nowMs int64) ([]QuotaConsumeOutboxJob, error) {
	if limit <= 0 {
		limit = 20
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT id, user_id, client_msg_id, day_cn, tier_at_completion, completion_at, attempts, created_at
		 FROM quota_consume_outbox
		 WHERE (status IN ('pending','failed') AND next_attempt_at <= ?)
		    OR (status = 'needs_ops' AND next_attempt_at <= ?)
		 ORDER BY id ASC
		 LIMIT ?`,
		nowMs,
		nowMs,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	jobs := []QuotaConsumeOutboxJob{}
	for rows.Next() {
		var job QuotaConsumeOutboxJob
		var tier string
		if err := rows.Scan(&job.ID, &job.UserID, &job.ClientMsgID, &job.DayCN, &tier, &job.CompletionAt, &job.Attempts, &job.CreatedAt); err != nil {
			return nil, err
		}
		job.Tier = Tier(tier)
		if _, ok := tierLimits[job.Tier]; !ok {
			job.Tier = TierFree
		}
		jobs = append(jobs, job)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return jobs, nil
}

func (s *Store) CountPendingQuotaConsumeOutbox(ctx context.Context) (int64, error) {
	var count sql.NullInt64
	err := s.db.QueryRowContext(
		ctx,
		"SELECT COUNT(*) FROM quota_consume_outbox WHERE status IN ('pending','failed','needs_ops')",
	).Scan(&count)
	if err != nil {
		return 0, err
	}
	return count.Int64, nil
}

func (s *Store) ListQuotaConsumeOutboxAdminEntries(ctx context.Context, status string, limit int) ([]QuotaConsumeOutboxAdminEntry, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	status = strings.TrimSpace(strings.ToLower(status))
	where := "status IN ('pending','failed','needs_ops')"
	args := []any{}
	switch status {
	case "pending", "failed", "needs_ops", "waived", "uncollectable", "done":
		where = "status = ?"
		args = append(args, status)
	case "", "active":
	default:
		status = "active"
	}
	args = append(args, limit)
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT id, user_id, client_msg_id, day_cn, tier_at_completion, completion_at,
		        status, attempts, last_error, next_attempt_at, repaired_at, created_at, updated_at
		   FROM quota_consume_outbox
		  WHERE `+where+`
		  ORDER BY updated_at ASC, id ASC
		  LIMIT ?`,
		args...,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	entries := []QuotaConsumeOutboxAdminEntry{}
	for rows.Next() {
		entry, err := scanQuotaConsumeOutboxAdminEntry(rows)
		if err != nil {
			return nil, err
		}
		entries = append(entries, entry)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return entries, nil
}

func (s *Store) GetQuotaConsumeOutboxAdminEntry(ctx context.Context, id int64) (QuotaConsumeOutboxAdminEntry, bool, error) {
	row := s.db.QueryRowContext(
		ctx,
		`SELECT id, user_id, client_msg_id, day_cn, tier_at_completion, completion_at,
		        status, attempts, last_error, next_attempt_at, repaired_at, created_at, updated_at
		   FROM quota_consume_outbox
		  WHERE id = ?
		  LIMIT 1`,
		id,
	)
	entry, err := scanQuotaConsumeOutboxAdminEntry(row)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return QuotaConsumeOutboxAdminEntry{}, false, nil
		}
		return QuotaConsumeOutboxAdminEntry{}, false, err
	}
	return entry, true, nil
}

type quotaConsumeOutboxAdminScanner interface {
	Scan(dest ...any) error
}

func scanQuotaConsumeOutboxAdminEntry(scanner quotaConsumeOutboxAdminScanner) (QuotaConsumeOutboxAdminEntry, error) {
	var entry QuotaConsumeOutboxAdminEntry
	var lastError sql.NullString
	var nextAttemptAt sql.NullInt64
	var repairedAt sql.NullInt64
	if err := scanner.Scan(
		&entry.ID,
		&entry.UserID,
		&entry.ClientMsgID,
		&entry.DayCN,
		&entry.Tier,
		&entry.CompletionAt,
		&entry.Status,
		&entry.Attempts,
		&lastError,
		&nextAttemptAt,
		&repairedAt,
		&entry.CreatedAt,
		&entry.UpdatedAt,
	); err != nil {
		return QuotaConsumeOutboxAdminEntry{}, err
	}
	if lastError.Valid {
		entry.LastError = lastError.String
	}
	if nextAttemptAt.Valid {
		entry.NextAttemptAt = nextAttemptAt.Int64
	}
	if repairedAt.Valid {
		entry.RepairedAt = repairedAt.Int64
	}
	return entry, nil
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

	if replay, payload, err := s.readOrderReplay(ctx, tx, orderID, userID, "buy_topup"); err != nil {
		return false, "", nil, 0, err
	} else if replay {
		if err := tx.Commit(); err != nil {
			return false, "", nil, 0, err
		}
		return true, asString(payload["pack_id"]), payloadOptionalInt64(payload["expire_at"]), int(payloadFloat64(payload["remaining"])), nil
	}

	var tier sql.NullString
	var tierExpireAt sql.NullInt64
	err = tx.QueryRowContext(
		ctx,
		"SELECT tier, tier_expire_at FROM user_entitlement WHERE user_id = ? LIMIT 1 FOR UPDATE",
		userID,
	).Scan(&tier, &tierExpireAt)
	if err != nil && err != sql.ErrNoRows {
		return false, "", nil, 0, err
	}
	now := time.Now().UnixMilli()
	effectiveTier, _, err := effectiveTierFromRow(tier, tierExpireAt, TierFree, now)
	if err != nil {
		return false, "", nil, 0, err
	}
	if effectiveTier != TierPlus && effectiveTier != TierPro {
		return false, "", nil, 0, fmt.Errorf("FORBIDDEN_TIER")
	}

	var activeCount sql.NullInt64
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
	if topupPackActiveLimit > 0 && activeCount.Int64 >= topupPackActiveLimit {
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

	if replay, payload, err := s.readOrderReplay(ctx, tx, orderID, userID, "upgrade_plus_to_pro"); err != nil {
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

	nowTime := time.Now()
	now := nowTime.UnixMilli()
	effectiveTier, _, err := effectiveTierFromRow(currentTier, tierExpireAt, TierFree, now)
	if err != nil {
		return false, 0, "", 0, 0, err
	}
	switch effectiveTier {
	case TierPro:
		return false, 0, "", 0, 0, fmt.Errorf("ALREADY_PRO")
	case TierPlus:
	default:
		return false, 0, "", 0, 0, fmt.Errorf("FORBIDDEN_TIER")
	}

	dayCN := GetTodayKeyCN(s.shanghai, nowTime)
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

	if replay, payload, err := s.readOrderReplay(ctx, tx, orderID, userID, orderTypeForTierRenew(targetTier)); err != nil {
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

	now := time.Now().UnixMilli()
	effectiveTier, _, err := effectiveTierFromRow(currentTier, tierExpireAt, TierFree, now)
	if err != nil {
		return false, "", 0, err
	}
	switch {
	case targetTier == TierPlus && effectiveTier == TierPro:
		return false, "", 0, fmt.Errorf("FORBIDDEN_TIER")
	case targetTier == TierPro && effectiveTier == TierPlus:
		return false, "", 0, fmt.Errorf("USE_UPGRADE_PLUS_TO_PRO")
	}

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
	orderType := orderTypeForTierRenew(targetTier)
	if targetTier == TierPro {
		amount = proTierPrice
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

func effectiveTierFromRow(tier sql.NullString, expireAt sql.NullInt64, fallback Tier, now int64) (Tier, *int64, error) {
	resultTier := fallback
	switch Tier(tier.String) {
	case TierFree, TierPlus, TierPro:
		resultTier = Tier(tier.String)
	}

	if resultTier == TierPlus || resultTier == TierPro {
		if !expireAt.Valid || expireAt.Int64 <= now {
			return TierFree, nil, nil
		}
	}

	if !expireAt.Valid {
		return resultTier, nil, nil
	}
	value := expireAt.Int64
	return resultTier, &value, nil
}

func (s *Store) consumeOverflowQuota(ctx context.Context, tx *sql.Tx, userID string, now int64) (*QuotaSource, error) {
	var upgradeRemaining sql.NullInt64
	err := tx.QueryRowContext(
		ctx,
		`SELECT remaining
		 FROM upgrade_credits
		 WHERE user_id = ? AND remaining > 0 AND updated_at <= ? AND (expire_at IS NULL OR expire_at > ?)
		 LIMIT 1
		 FOR UPDATE`,
		userID,
		now,
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
	var packRemaining sql.NullInt64
	err = tx.QueryRowContext(
		ctx,
		`SELECT pack_id, remaining
		 FROM topup_packs
		 WHERE user_id = ? AND status = 'active' AND remaining > 0 AND created_at <= ? AND (expire_at IS NULL OR expire_at > ?)
		 ORDER BY CASE WHEN expire_at IS NULL THEN 1 ELSE 0 END ASC, expire_at ASC, created_at ASC
		 LIMIT 1
		 FOR UPDATE`,
		userID,
		now,
		now,
	).Scan(&packID, &packRemaining)
	if err == nil {
		if _, err := tx.ExecContext(
			ctx,
			`UPDATE topup_packs
			 SET remaining = remaining - 1,
			     status = ?
			 WHERE pack_id = ?`,
			topupPackStatusAfterConsume(packRemaining.Int64),
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

func topupPackStatusAfterConsume(remainingBeforeConsume int64) string {
	if remainingBeforeConsume <= 1 {
		return "used_up"
	}
	return "active"
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

	query := "SELECT used FROM daily_usage WHERE user_id = ? AND day_cn = ? LIMIT 1"
	if tx != nil {
		query += " FOR UPDATE"
	}
	var used sql.NullInt64
	if err := queryRowContext(execer, ctx, query, userID, dayCN).Scan(&used); err != nil {
		return 0, err
	}
	return int(used.Int64), nil
}

func (s *Store) readOrderReplay(ctx context.Context, tx *sql.Tx, orderID string, userID string, expectedType string) (bool, map[string]any, error) {
	var existingUserID sql.NullString
	var existingType sql.NullString
	var resultJSON sql.NullString
	err := tx.QueryRowContext(
		ctx,
		"SELECT user_id, type, result_json FROM orders WHERE order_id = ? LIMIT 1 FOR UPDATE",
		orderID,
	).Scan(&existingUserID, &existingType, &resultJSON)
	if err == sql.ErrNoRows {
		return false, nil, nil
	}
	if err != nil {
		return false, nil, err
	}
	if existingUserID.Valid && strings.TrimSpace(existingUserID.String) != strings.TrimSpace(userID) {
		return false, nil, ErrOrderIDConflict
	}
	if strings.TrimSpace(expectedType) != "" && strings.TrimSpace(existingType.String) != strings.TrimSpace(expectedType) {
		return false, nil, ErrOrderIDConflict
	}

	payload := map[string]any{}
	if resultJSON.Valid && strings.TrimSpace(resultJSON.String) != "" {
		if err := json.Unmarshal([]byte(resultJSON.String), &payload); err != nil {
			return false, nil, err
		}
	}
	return true, payload, nil
}

func orderTypeForTierRenew(targetTier Tier) string {
	if targetTier == TierPro {
		return "renew_pro"
	}
	return "renew_plus"
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

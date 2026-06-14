package app

import (
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"os"
	"strings"
	"time"
)

var (
	errAccountPhoneInvalid       = errors.New("account_phone_invalid")
	errAccountPhoneSecretMissing = errors.New("account_phone_secret_missing")
)

type AccountLoginResult struct {
	UserID                string `json:"user_id"`
	PhoneMask             string `json:"phone_mask"`
	Token                 string `json:"token"`
	ExpiresAt             int64  `json:"expires_at"`
	LegacyMigrationStatus string `json:"-"`
}

func (s *Store) LoginWithVerifiedPhone(
	ctx context.Context,
	phoneNumber string,
	legacyUserID string,
	deviceID string,
	secret string,
	sessionTTL time.Duration,
) (AccountLoginResult, error) {
	phone := normalizeMainlandPhone(phoneNumber)
	if phone == "" {
		return AccountLoginResult{}, fmt.Errorf("invalid_phone")
	}
	phoneHash := hashPhone(phone, secret)
	if phoneHash == "" {
		return AccountLoginResult{}, fmt.Errorf("phone_hash_secret_missing")
	}
	phoneMask := maskPhone(phone)
	phoneCiphertext, err := encryptAccountPhoneNumberWithSecret(phone, secret)
	if err != nil {
		return AccountLoginResult{}, err
	}
	now := time.Now()
	nowMs := now.UnixMilli()
	sessionID, err := randomHexString(16)
	if err != nil {
		return AccountLoginResult{}, err
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return AccountLoginResult{}, err
	}
	defer rollbackQuietly(tx)

	userID, err := s.getOrCreateAccountForPhoneTx(ctx, tx, phoneHash, phoneMask, phoneCiphertext, nowMs)
	if err != nil {
		return AccountLoginResult{}, err
	}
	legacyMigrationStatus, err := s.mergeLegacyUserIntoAccountTx(ctx, tx, normalizeUserID(legacyUserID), userID, nowMs)
	if err != nil {
		return AccountLoginResult{}, err
	}

	token, expiresAt, err := issueAuthToken(userID, sessionID, now, sessionTTL, secret)
	if err != nil {
		return AccountLoginResult{}, err
	}
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO auth_sessions(session_id, user_id, device_id, token_expires_at, created_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?)`,
		sessionID,
		userID,
		nullableAuthString(normalizeDeviceID(deviceID)),
		expiresAt,
		nowMs,
		nowMs,
	); err != nil {
		return AccountLoginResult{}, err
	}
	if _, err := tx.ExecContext(
		ctx,
		"UPDATE app_accounts SET phone_mask = ?, phone_ciphertext = ?, last_login_at = ?, updated_at = ? WHERE user_id = ?",
		phoneMask,
		phoneCiphertext,
		nowMs,
		nowMs,
		userID,
	); err != nil {
		return AccountLoginResult{}, err
	}
	if err := tx.Commit(); err != nil {
		return AccountLoginResult{}, err
	}
	return AccountLoginResult{
		UserID:                userID,
		PhoneMask:             phoneMask,
		Token:                 token,
		ExpiresAt:             expiresAt,
		LegacyMigrationStatus: legacyMigrationStatus,
	}, nil
}

func (s *Store) IsAuthSessionActive(ctx context.Context, userID string, sessionID string, nowMs int64) (bool, error) {
	if strings.TrimSpace(userID) == "" || strings.TrimSpace(sessionID) == "" {
		return false, nil
	}
	var existing string
	err := s.db.QueryRowContext(
		ctx,
		`SELECT session_id
		 FROM auth_sessions
		 WHERE session_id = ? AND user_id = ? AND revoked_at IS NULL AND token_expires_at > ?
		 LIMIT 1`,
		sessionID,
		userID,
		nowMs,
	).Scan(&existing)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return existing != "", nil
}

func (s *Store) RevokeAuthSession(ctx context.Context, userID string, sessionID string, nowMs int64) error {
	userID = strings.TrimSpace(userID)
	sessionID = strings.TrimSpace(sessionID)
	if userID == "" || sessionID == "" {
		return fmt.Errorf("auth_session_identity_missing")
	}
	_, err := s.db.ExecContext(
		ctx,
		`UPDATE auth_sessions
		 SET revoked_at = ?, updated_at = ?
		 WHERE session_id = ? AND user_id = ? AND revoked_at IS NULL`,
		nowMs,
		nowMs,
		sessionID,
		userID,
	)
	return err
}

func (s *Store) getOrCreateAccountForPhoneTx(ctx context.Context, tx *sql.Tx, phoneHash string, phoneMask string, phoneCiphertext string, nowMs int64) (string, error) {
	var userID string
	err := tx.QueryRowContext(
		ctx,
		"SELECT user_id FROM app_accounts WHERE phone_hash = ? LIMIT 1 FOR UPDATE",
		phoneHash,
	).Scan(&userID)
	if err == nil {
		return userID, nil
	}
	if err != sql.ErrNoRows {
		return "", err
	}
	randomPart, err := randomHexString(16)
	if err != nil {
		return "", err
	}
	userID = "acct_" + randomPart
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO app_accounts(user_id, phone_hash, phone_mask, phone_ciphertext, created_at, updated_at, last_login_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?)`,
		userID,
		phoneHash,
		phoneMask,
		phoneCiphertext,
		nowMs,
		nowMs,
		nowMs,
	); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO user_entitlement(user_id, tier, tier_expire_at, updated_at)
		 VALUES (?, 'free', NULL, ?)
		 ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)`,
		userID,
		nowMs,
	); err != nil {
		return "", err
	}
	return userID, nil
}

func encryptAccountPhoneNumberWithSecret(phone string, secret string) (string, error) {
	phone = normalizeMainlandPhone(phone)
	secret = strings.TrimSpace(secret)
	if phone == "" {
		return "", errAccountPhoneInvalid
	}
	if secret == "" {
		return "", errAccountPhoneSecretMissing
	}
	key := accountPhoneCipherKey(secret)
	block, err := aes.NewCipher(key[:])
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", err
	}
	sealed := gcm.Seal(nil, nonce, []byte(phone), []byte("account_phone:v1"))
	payload := append(nonce, sealed...)
	return "v1:" + base64.RawURLEncoding.EncodeToString(payload), nil
}

func decryptAccountPhoneNumber(ciphertext string) (string, error) {
	return decryptAccountPhoneNumberWithSecret(ciphertext, os.Getenv("APP_SECRET"))
}

func decryptAccountPhoneNumberWithSecret(ciphertext string, secret string) (string, error) {
	ciphertext = strings.TrimSpace(ciphertext)
	secret = strings.TrimSpace(secret)
	if ciphertext == "" {
		return "", nil
	}
	if secret == "" {
		return "", errAccountPhoneSecretMissing
	}
	if !strings.HasPrefix(ciphertext, "v1:") {
		return "", fmt.Errorf("unsupported account phone ciphertext")
	}
	raw, err := base64.RawURLEncoding.DecodeString(strings.TrimPrefix(ciphertext, "v1:"))
	if err != nil {
		return "", err
	}
	key := accountPhoneCipherKey(secret)
	block, err := aes.NewCipher(key[:])
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	if len(raw) <= gcm.NonceSize() {
		return "", fmt.Errorf("invalid account phone ciphertext")
	}
	nonce := raw[:gcm.NonceSize()]
	sealed := raw[gcm.NonceSize():]
	plain, err := gcm.Open(nil, nonce, sealed, []byte("account_phone:v1"))
	if err != nil {
		return "", err
	}
	return normalizeMainlandPhone(string(plain)), nil
}

func accountPhoneCipherKey(secret string) [32]byte {
	return sha256.Sum256([]byte("nongjiqiancha:account_phone:v1:" + strings.TrimSpace(secret)))
}

func (s *Store) mergeLegacyUserIntoAccountTx(ctx context.Context, tx *sql.Tx, oldUserID string, newUserID string, nowMs int64) (string, error) {
	oldUserID = normalizeUserID(oldUserID)
	newUserID = normalizeUserID(newUserID)
	if oldUserID == "" || newUserID == "" || oldUserID == newUserID {
		return "skipped_same_or_empty", nil
	}
	if strings.HasPrefix(oldUserID, "acct_") {
		return "skipped_account_id", nil
	}
	status := "migrated"
	var existingMigrationTarget sql.NullString
	err := tx.QueryRowContext(
		ctx,
		"SELECT new_user_id FROM user_id_migrations WHERE old_user_id = ? LIMIT 1 FOR UPDATE",
		oldUserID,
	).Scan(&existingMigrationTarget)
	if err == nil {
		if strings.TrimSpace(existingMigrationTarget.String) != newUserID {
			return "conflict_skipped", nil
		}
		status = "already_same_target"
	} else if err == sql.ErrNoRows {
		if _, err := tx.ExecContext(
			ctx,
			`INSERT INTO user_id_migrations(old_user_id, new_user_id, migrated_at)
			 VALUES (?, ?, ?)`,
			oldUserID,
			newUserID,
			nowMs,
		); err != nil {
			return "", err
		}
	} else {
		return "", err
	}

	if _, err := tx.ExecContext(ctx, "UPDATE topup_packs SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, "UPDATE orders SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, "UPDATE support_messages SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
		return "", err
	}
	if err := syncMergedSupportConversationTx(ctx, tx, oldUserID, newUserID, nowMs); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, "UPDATE client_app_logs SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(
		ctx,
		`UPDATE account_deletion_requests
		    SET user_id = ?,
		        phone_mask = CASE
		          WHEN phone_mask IS NULL OR phone_mask = '' THEN (
		            SELECT phone_mask FROM app_accounts WHERE user_id = ? LIMIT 1
		          )
		          ELSE phone_mask
		        END,
		        updated_at = CASE
		          WHEN status IN ('pending','processing') THEN ?
		          ELSE updated_at
		        END
		  WHERE user_id = ?`,
		newUserID,
		newUserID,
		nowMs,
		oldUserID,
	); err != nil {
		return "", err
	}
	var phoneMask string
	_ = tx.QueryRowContext(ctx, "SELECT phone_mask FROM app_accounts WHERE user_id = ? LIMIT 1", newUserID).Scan(&phoneMask)
	if _, err := tx.ExecContext(
		ctx,
		`UPDATE gift_cards
		    SET redeemed_user_id = ?,
		        redeemed_phone_mask = CASE
		          WHEN redeemed_phone_mask IS NULL OR redeemed_phone_mask = '' THEN ?
		          ELSE redeemed_phone_mask
		        END,
		        updated_at = ?
		  WHERE redeemed_user_id = ?`,
		newUserID,
		nullableTrimmed(phoneMask),
		nowMs,
		oldUserID,
	); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, "UPDATE gift_card_redemption_attempts SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
		return "", err
	}
	// Inflight streams are transient. Do not migrate them across identities because
	// the single-active-stream unique index can make login fail on stale leases.
	if _, err := tx.ExecContext(ctx, "DELETE FROM chat_stream_inflight WHERE user_id = ?", oldUserID); err != nil {
		return "", err
	}

	targetTier, targetExpireAt, err := effectiveEntitlementForUserTx(ctx, tx, newUserID, nowMs)
	if err != nil {
		return "", err
	}
	sourceTier, sourceExpireAt, err := effectiveEntitlementForUserTx(ctx, tx, oldUserID, nowMs)
	if err != nil {
		return "", err
	}
	mergedTier, mergedExpireAt := mergeEffectiveEntitlements(targetTier, targetExpireAt, sourceTier, sourceExpireAt)
	upgradeCompensation, err := s.legacyPlusUpgradeCompensationTx(
		ctx,
		tx,
		oldUserID,
		newUserID,
		targetTier,
		targetExpireAt,
		sourceTier,
		sourceExpireAt,
		nowMs,
	)
	if err != nil {
		return "", err
	}

	if _, err := tx.ExecContext(ctx, mergeDailyUsageSQL, newUserID, oldUserID); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM daily_usage WHERE user_id = ?", oldUserID); err != nil {
		return "", err
	}

	if err := copyRowsToNewUserID(ctx, tx, "quota_ledger", oldUserID, newUserID, "id"); err != nil {
		return "", err
	}
	if err := copyRowsToNewUserID(ctx, tx, "session_round_ledger", oldUserID, newUserID, "id"); err != nil {
		return "", err
	}
	if err := copyRowsToNewUserID(ctx, tx, "session_round_archive", oldUserID, newUserID, "id"); err != nil {
		return "", err
	}

	if _, err := tx.ExecContext(ctx, mergeUpgradeCreditsSQL, newUserID, nowMs, oldUserID); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM upgrade_credits WHERE user_id = ?", oldUserID); err != nil {
		return "", err
	}
	if upgradeCompensation > 0 {
		if _, err := tx.ExecContext(ctx, insertUpgradeCreditCompensationSQL, newUserID, upgradeCompensation, nowMs); err != nil {
			return "", err
		}
	}

	if _, err := tx.ExecContext(ctx, mergeSessionGenerationSQL, newUserID, nowMs, oldUserID); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM session_generation WHERE user_id = ?", oldUserID); err != nil {
		return "", err
	}

	var targetSession string
	err = tx.QueryRowContext(ctx, "SELECT user_id FROM session_ab WHERE user_id = ? LIMIT 1 FOR UPDATE", newUserID).Scan(&targetSession)
	if err == sql.ErrNoRows {
		if _, err := tx.ExecContext(ctx, "UPDATE session_ab SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
			return "", err
		}
	} else if err != nil {
		return "", err
	} else {
		if _, err := tx.ExecContext(ctx, mergeSessionABSQL, oldUserID, nowMs, newUserID); err != nil {
			return "", err
		}
		if _, err := tx.ExecContext(ctx, "DELETE FROM session_ab WHERE user_id = ?", oldUserID); err != nil {
			return "", err
		}
	}

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO user_entitlement(user_id, tier, tier_expire_at, updated_at)
		 VALUES (?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   tier = VALUES(tier),
		   tier_expire_at = VALUES(tier_expire_at),
		   updated_at = VALUES(updated_at)`,
		newUserID,
		string(mergedTier),
		nullableInt64Ptr(mergedExpireAt),
		nowMs,
	); err != nil {
		return "", err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM user_entitlement WHERE user_id = ?", oldUserID); err != nil {
		return "", err
	}
	return status, nil
}

const mergeDailyUsageSQL = `INSERT INTO daily_usage(user_id, day_cn, used)
 SELECT ?, source.day_cn, source.used
 FROM (SELECT day_cn, used FROM daily_usage WHERE user_id = ?) AS source
 ON DUPLICATE KEY UPDATE used = daily_usage.used + VALUES(used)`

const mergeUpgradeCreditsSQL = `INSERT INTO upgrade_credits(user_id, remaining, expire_at, updated_at)
 SELECT ?, source.remaining, source.expire_at, ?
 FROM (SELECT remaining, expire_at FROM upgrade_credits WHERE user_id = ?) AS source
 ON DUPLICATE KEY UPDATE
   remaining = upgrade_credits.remaining + VALUES(remaining),
   expire_at = CASE
     WHEN upgrade_credits.expire_at IS NULL OR VALUES(expire_at) IS NULL THEN NULL
     WHEN upgrade_credits.expire_at > VALUES(expire_at) THEN upgrade_credits.expire_at
     ELSE VALUES(expire_at)
   END,
   updated_at = VALUES(updated_at)`

const insertUpgradeCreditCompensationSQL = `INSERT INTO upgrade_credits(user_id, remaining, expire_at, updated_at)
 VALUES (?, ?, NULL, ?)
 ON DUPLICATE KEY UPDATE
   remaining = upgrade_credits.remaining + VALUES(remaining),
   expire_at = NULL,
   updated_at = VALUES(updated_at)`

const mergeSessionGenerationSQL = `INSERT INTO session_generation(user_id, generation, cleared_at, updated_at)
 SELECT ?, source.generation, source.cleared_at, ?
 FROM (SELECT generation, cleared_at FROM session_generation WHERE user_id = ?) AS source
 ON DUPLICATE KEY UPDATE
   generation = GREATEST(session_generation.generation, VALUES(generation)),
   cleared_at = GREATEST(COALESCE(session_generation.cleared_at, 0), COALESCE(VALUES(cleared_at), 0)),
   updated_at = VALUES(updated_at)`

const mergeSessionABSQL = `UPDATE session_ab AS target
 JOIN session_ab AS source ON source.user_id = ?
 SET
   target.a_json = CASE
     WHEN (target.a_json IS NULL OR JSON_LENGTH(target.a_json) = 0) AND source.a_json IS NOT NULL THEN source.a_json
     ELSE target.a_json
   END,
   target.b_summary = CASE
     WHEN NULLIF(TRIM(COALESCE(source.b_summary, '')), '') IS NULL THEN target.b_summary
     WHEN NULLIF(TRIM(COALESCE(target.b_summary, '')), '') IS NULL THEN source.b_summary
     WHEN TRIM(target.b_summary) = TRIM(source.b_summary) THEN target.b_summary
     ELSE TRIM(CONCAT(TRIM(target.b_summary), CHAR(10), CHAR(10), TRIM(source.b_summary)))
   END,
   target.pending_retry_b = CASE
     WHEN COALESCE(source.pending_retry_b, 0) <> 0 THEN 1
     WHEN NULLIF(TRIM(COALESCE(source.b_summary, '')), '') IS NOT NULL
       AND NULLIF(TRIM(COALESCE(target.b_summary, '')), '') IS NOT NULL
       AND TRIM(target.b_summary) <> TRIM(source.b_summary) THEN 1
     ELSE target.pending_retry_b
   END,
   target.round_total = GREATEST(COALESCE(target.round_total, 0), COALESCE(source.round_total, 0)),
   target.updated_at = ?
 WHERE target.user_id = ?`

func effectiveEntitlementForUserTx(ctx context.Context, tx *sql.Tx, userID string, nowMs int64) (Tier, *int64, error) {
	var tier sql.NullString
	var expireAt sql.NullInt64
	err := tx.QueryRowContext(
		ctx,
		"SELECT tier, tier_expire_at FROM user_entitlement WHERE user_id = ? LIMIT 1 FOR UPDATE",
		userID,
	).Scan(&tier, &expireAt)
	if err == sql.ErrNoRows {
		return TierFree, nil, nil
	}
	if err != nil {
		return "", nil, err
	}
	return effectiveTierFromRow(tier, expireAt, TierFree, nowMs)
}

func mergeEffectiveEntitlements(targetTier Tier, targetExpireAt *int64, sourceTier Tier, sourceExpireAt *int64) (Tier, *int64) {
	if tierRank(sourceTier) > tierRank(targetTier) {
		return sourceTier, cloneInt64Ptr(sourceExpireAt)
	}
	if tierRank(sourceTier) < tierRank(targetTier) {
		return targetTier, cloneInt64Ptr(targetExpireAt)
	}
	if targetTier == TierFree {
		return TierFree, nil
	}
	return targetTier, maxInt64Ptr(targetExpireAt, sourceExpireAt)
}

func (s *Store) legacyPlusUpgradeCompensationTx(
	ctx context.Context,
	tx *sql.Tx,
	oldUserID string,
	newUserID string,
	targetTier Tier,
	targetExpireAt *int64,
	sourceTier Tier,
	sourceExpireAt *int64,
	nowMs int64,
) (int, error) {
	if targetTier == TierPro && sourceTier == TierPlus {
		return s.remainingPlusQuotaCompensationTx(ctx, tx, oldUserID, sourceExpireAt, nowMs)
	}
	if targetTier == TierPlus && sourceTier == TierPro {
		return s.remainingPlusQuotaCompensationTx(ctx, tx, newUserID, targetExpireAt, nowMs)
	}
	return 0, nil
}

func (s *Store) remainingPlusQuotaCompensationTx(ctx context.Context, tx *sql.Tx, userID string, expireAt *int64, nowMs int64) (int, error) {
	dayCN := GetTodayKeyCN(s.shanghai, time.UnixMilli(nowMs))
	usedToday, err := s.getOrCreateDailyUsage(ctx, tx, userID, dayCN)
	if err != nil {
		return 0, err
	}
	return remainingPlusQuotaCompensation(usedToday, expireAt, nowMs, s.shanghai), nil
}

func remainingPlusQuotaCompensation(usedToday int, expireAt *int64, nowMs int64, loc *time.Location) int {
	todayRemainingPlus := maxInt(0, tierLimits[TierPlus]-usedToday)
	expireAtOld := nowMs
	if expireAt != nil {
		expireAtOld = *expireAt
	}
	remainingFullDays := maxInt(0, dayIndexFromTsCN(loc, expireAtOld)-dayIndexFromTsCN(loc, nowMs))
	return todayRemainingPlus + remainingFullDays*tierLimits[TierPlus]
}

func cloneInt64Ptr(value *int64) *int64 {
	if value == nil {
		return nil
	}
	copyValue := *value
	return &copyValue
}

func maxInt64Ptr(left *int64, right *int64) *int64 {
	if left == nil {
		return cloneInt64Ptr(right)
	}
	if right == nil || *left >= *right {
		return cloneInt64Ptr(left)
	}
	return cloneInt64Ptr(right)
}

func nullableInt64Ptr(value *int64) any {
	if value == nil {
		return nil
	}
	return *value
}

func copyRowsToNewUserID(ctx context.Context, tx *sql.Tx, table string, oldUserID string, newUserID string, orderColumn string) error {
	// Table names here are hard-coded by the caller; never pass user input as table.
	rows, err := tx.QueryContext(ctx, "SELECT * FROM "+table+" WHERE user_id = ? ORDER BY "+orderColumn+" ASC", oldUserID)
	if err != nil {
		return err
	}
	defer rows.Close()

	columns, err := rows.Columns()
	if err != nil {
		return err
	}
	userIDIndex := -1
	for i, column := range columns {
		if column == "user_id" {
			userIDIndex = i
			break
		}
	}
	if userIDIndex < 0 {
		return fmt.Errorf("user_id column missing in %s", table)
	}

	insertColumns := make([]string, 0, len(columns))
	insertIndexes := make([]int, 0, len(columns))
	for i, column := range columns {
		if column == orderColumn {
			continue
		}
		insertColumns = append(insertColumns, column)
		insertIndexes = append(insertIndexes, i)
	}
	placeholders := strings.TrimRight(strings.Repeat("?,", len(insertColumns)), ",")
	insertQuery := "INSERT IGNORE INTO " + table + "(" + strings.Join(insertColumns, ",") + ") VALUES (" + placeholders + ")"
	values := make([]any, len(columns))
	valuePtrs := make([]any, len(columns))
	for i := range values {
		valuePtrs[i] = &values[i]
	}
	insertValues := make([]any, len(insertIndexes))
	for rows.Next() {
		for i := range values {
			values[i] = nil
		}
		if err := rows.Scan(valuePtrs...); err != nil {
			return err
		}
		values[userIDIndex] = newUserID
		for i, sourceIndex := range insertIndexes {
			insertValues[i] = values[sourceIndex]
		}
		if _, err := tx.ExecContext(ctx, insertQuery, insertValues...); err != nil {
			return err
		}
	}
	if err := rows.Err(); err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, "DELETE FROM "+table+" WHERE user_id = ?", oldUserID)
	return err
}

func normalizeMainlandPhone(raw string) string {
	phone := strings.TrimSpace(raw)
	phone = strings.TrimPrefix(phone, "+86")
	phone = strings.TrimPrefix(phone, "86")
	phone = strings.ReplaceAll(phone, " ", "")
	phone = strings.ReplaceAll(phone, "-", "")
	if len(phone) != 11 || phone[0] != '1' {
		return ""
	}
	for _, ch := range phone {
		if ch < '0' || ch > '9' {
			return ""
		}
	}
	return phone
}

func normalizeUserID(raw string) string {
	value := strings.TrimSpace(raw)
	if len(value) > 128 {
		value = value[:128]
	}
	return value
}

func normalizeDeviceID(raw string) string {
	value := strings.TrimSpace(raw)
	if len(value) > 128 {
		value = value[:128]
	}
	return value
}

func maskPhone(phone string) string {
	if len(phone) != 11 {
		return "unknown"
	}
	return phone[:3] + "****" + phone[7:]
}

func hashPhone(phone string, secret string) string {
	secret = strings.TrimSpace(secret)
	if secret == "" {
		return ""
	}
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(phone))
	return hex.EncodeToString(mac.Sum(nil))
}

func accountPhoneHashForSearch(raw string) string {
	phone := normalizeMainlandPhone(raw)
	if phone == "" {
		return ""
	}
	return hashPhone(phone, os.Getenv("APP_SECRET"))
}

func nullableAuthString(value string) any {
	if strings.TrimSpace(value) == "" {
		return nil
	}
	return value
}

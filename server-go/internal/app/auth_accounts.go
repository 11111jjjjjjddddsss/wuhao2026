package app

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"fmt"
	"strings"
	"time"
)

type AccountLoginResult struct {
	UserID    string `json:"user_id"`
	PhoneMask string `json:"phone_mask"`
	Token     string `json:"token"`
	ExpiresAt int64  `json:"expires_at"`
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

	userID, err := s.getOrCreateAccountForPhoneTx(ctx, tx, phoneHash, phoneMask, nowMs)
	if err != nil {
		return AccountLoginResult{}, err
	}
	if err := s.mergeLegacyUserIntoAccountTx(ctx, tx, normalizeUserID(legacyUserID), userID, nowMs); err != nil {
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
		"UPDATE app_accounts SET phone_mask = ?, last_login_at = ?, updated_at = ? WHERE user_id = ?",
		phoneMask,
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
		UserID:    userID,
		PhoneMask: phoneMask,
		Token:     token,
		ExpiresAt: expiresAt,
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

func (s *Store) getOrCreateAccountForPhoneTx(ctx context.Context, tx *sql.Tx, phoneHash string, phoneMask string, nowMs int64) (string, error) {
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
		`INSERT INTO app_accounts(user_id, phone_hash, phone_mask, created_at, updated_at, last_login_at)
		 VALUES (?, ?, ?, ?, ?, ?)`,
		userID,
		phoneHash,
		phoneMask,
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

func (s *Store) mergeLegacyUserIntoAccountTx(ctx context.Context, tx *sql.Tx, oldUserID string, newUserID string, nowMs int64) error {
	oldUserID = normalizeUserID(oldUserID)
	newUserID = normalizeUserID(newUserID)
	if oldUserID == "" || newUserID == "" || oldUserID == newUserID {
		return nil
	}
	if strings.HasPrefix(oldUserID, "acct_") {
		return nil
	}
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO user_id_migrations(old_user_id, new_user_id, migrated_at)
		 VALUES (?, ?, ?)
		 ON DUPLICATE KEY UPDATE new_user_id = VALUES(new_user_id)`,
		oldUserID,
		newUserID,
		nowMs,
	); err != nil {
		return err
	}

	if _, err := tx.ExecContext(ctx, "UPDATE topup_packs SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, "UPDATE orders SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, "UPDATE support_messages SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, "UPDATE client_app_logs SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
		return err
	}
	// Inflight streams are transient. Do not migrate them across identities because
	// the single-active-stream unique index can make login fail on stale leases.
	if _, err := tx.ExecContext(ctx, "DELETE FROM chat_stream_inflight WHERE user_id = ?", oldUserID); err != nil {
		return err
	}

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO daily_usage(user_id, day_cn, used)
		 SELECT ?, day_cn, used FROM daily_usage WHERE user_id = ?
		 ON DUPLICATE KEY UPDATE used = daily_usage.used + VALUES(used)`,
		newUserID,
		oldUserID,
	); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM daily_usage WHERE user_id = ?", oldUserID); err != nil {
		return err
	}

	if err := copyRowsToNewUserID(ctx, tx, "quota_ledger", oldUserID, newUserID, "id"); err != nil {
		return err
	}
	if err := copyRowsToNewUserID(ctx, tx, "session_round_ledger", oldUserID, newUserID, "id"); err != nil {
		return err
	}
	if err := copyRowsToNewUserID(ctx, tx, "session_round_archive", oldUserID, newUserID, "id"); err != nil {
		return err
	}

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO upgrade_credits(user_id, remaining, expire_at, updated_at)
		 SELECT ?, remaining, expire_at, ? FROM upgrade_credits WHERE user_id = ?
		 ON DUPLICATE KEY UPDATE
		   remaining = upgrade_credits.remaining + VALUES(remaining),
		   expire_at = CASE
		     WHEN upgrade_credits.expire_at IS NULL OR VALUES(expire_at) IS NULL THEN NULL
		     WHEN upgrade_credits.expire_at > VALUES(expire_at) THEN upgrade_credits.expire_at
		     ELSE VALUES(expire_at)
		   END,
		   updated_at = VALUES(updated_at)`,
		newUserID,
		nowMs,
		oldUserID,
	); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM upgrade_credits WHERE user_id = ?", oldUserID); err != nil {
		return err
	}

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO session_generation(user_id, generation, cleared_at, updated_at)
		 SELECT ?, generation, cleared_at, ? FROM session_generation WHERE user_id = ?
		 ON DUPLICATE KEY UPDATE
		   generation = GREATEST(session_generation.generation, VALUES(generation)),
		   cleared_at = GREATEST(COALESCE(session_generation.cleared_at, 0), COALESCE(VALUES(cleared_at), 0)),
		   updated_at = VALUES(updated_at)`,
		newUserID,
		nowMs,
		oldUserID,
	); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM session_generation WHERE user_id = ?", oldUserID); err != nil {
		return err
	}

	var targetSession string
	err := tx.QueryRowContext(ctx, "SELECT user_id FROM session_ab WHERE user_id = ? LIMIT 1 FOR UPDATE", newUserID).Scan(&targetSession)
	if err == sql.ErrNoRows {
		if _, err := tx.ExecContext(ctx, "UPDATE session_ab SET user_id = ? WHERE user_id = ?", newUserID, oldUserID); err != nil {
			return err
		}
	} else if err != nil {
		return err
	} else {
		if _, err := tx.ExecContext(ctx, "DELETE FROM session_ab WHERE user_id = ?", oldUserID); err != nil {
			return err
		}
	}

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO user_entitlement(user_id, tier, tier_expire_at, updated_at)
		 SELECT ?, tier, tier_expire_at, ? FROM user_entitlement WHERE user_id = ?
		 ON DUPLICATE KEY UPDATE
		   tier = CASE
		     WHEN user_entitlement.tier = 'free' AND VALUES(tier) IN ('plus','pro') THEN VALUES(tier)
		     WHEN user_entitlement.tier = 'plus' AND VALUES(tier) = 'pro' THEN VALUES(tier)
		     ELSE user_entitlement.tier
		   END,
		   tier_expire_at = CASE
		     WHEN user_entitlement.tier_expire_at IS NULL THEN VALUES(tier_expire_at)
		     WHEN VALUES(tier_expire_at) IS NULL THEN user_entitlement.tier_expire_at
		     WHEN user_entitlement.tier_expire_at > VALUES(tier_expire_at) THEN user_entitlement.tier_expire_at
		     ELSE VALUES(tier_expire_at)
		   END,
		   updated_at = VALUES(updated_at)`,
		newUserID,
		nowMs,
		oldUserID,
	); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM user_entitlement WHERE user_id = ?", oldUserID); err != nil {
		return err
	}
	return nil
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

func nullableAuthString(value string) any {
	if strings.TrimSpace(value) == "" {
		return nil
	}
	return value
}

package app

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"time"
)

const chatStreamInflightLease = 30 * time.Minute

func (s *Store) TryAcquireChatStreamInflight(ctx context.Context, userID string, clientMsgID string, now time.Time) (bool, string, error) {
	nowMs := now.UnixMilli()
	leaseUntilMs := now.Add(chatStreamInflightLease).UnixMilli()
	leaseToken, err := newChatStreamInflightToken()
	if err != nil {
		return false, "", err
	}
	result, err := s.db.ExecContext(
		ctx,
		`INSERT INTO chat_stream_inflight(user_id, client_msg_id, lease_token, lease_until, created_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   lease_token = IF(lease_until <= VALUES(updated_at), VALUES(lease_token), lease_token),
		   lease_until = IF(lease_until <= VALUES(updated_at), VALUES(lease_until), lease_until),
		   updated_at = IF(lease_until <= VALUES(updated_at), VALUES(updated_at), updated_at)`,
		userID,
		clientMsgID,
		leaseToken,
		leaseUntilMs,
		nowMs,
		nowMs,
	)
	if err != nil {
		return false, "", err
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return false, "", err
	}
	if affected <= 0 {
		return false, "", nil
	}
	return true, leaseToken, nil
}

func (s *Store) ReleaseChatStreamInflight(ctx context.Context, userID string, clientMsgID string, leaseToken string) error {
	_, err := s.db.ExecContext(
		ctx,
		"DELETE FROM chat_stream_inflight WHERE user_id = ? AND client_msg_id = ? AND lease_token = ?",
		userID,
		clientMsgID,
		leaseToken,
	)
	return err
}

func newChatStreamInflightToken() (string, error) {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(raw[:]), nil
}

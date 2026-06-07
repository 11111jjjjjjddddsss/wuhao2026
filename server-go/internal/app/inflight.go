package app

import (
	"context"
	"crypto/rand"
	"crypto/sha1"
	"database/sql"
	"encoding/hex"
	"errors"
	"time"
)

const (
	chatStreamInflightLease      = 30 * time.Minute
	chatStreamInflightLeaseGrace = 5 * time.Minute
)

var ErrChatStreamGateBusy = errors.New("chat stream gate busy")

func (s *Store) WithUserChatStreamGate(ctx context.Context, userID string, fn func(context.Context) error) error {
	conn, err := s.db.Conn(ctx)
	if err != nil {
		return err
	}
	defer conn.Close()

	lockName := chatStreamGateLockName(userID)
	var acquired sql.NullInt64
	if err := conn.QueryRowContext(ctx, "SELECT GET_LOCK(?, 5)", lockName).Scan(&acquired); err != nil {
		return err
	}
	if !acquired.Valid || acquired.Int64 != 1 {
		return ErrChatStreamGateBusy
	}
	defer func() {
		_, _ = conn.ExecContext(context.Background(), "SELECT RELEASE_LOCK(?)", lockName)
	}()

	return fn(ctx)
}

func (s *Store) TryAcquireChatStreamInflight(ctx context.Context, userID string, clientMsgID string, now time.Time, leaseDuration time.Duration) (bool, string, error) {
	nowMs := now.UnixMilli()
	if leaseDuration <= 0 {
		leaseDuration = chatStreamInflightLease
	}
	leaseUntilMs := now.Add(leaseDuration).UnixMilli()
	leaseToken, err := newChatStreamInflightToken()
	if err != nil {
		return false, "", err
	}
	if _, err := s.db.ExecContext(
		ctx,
		`INSERT INTO chat_stream_inflight(user_id, client_msg_id, lease_token, lease_until, created_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   client_msg_id = IF(lease_until <= VALUES(updated_at), VALUES(client_msg_id), client_msg_id),
		   lease_token = IF(lease_until <= VALUES(updated_at), VALUES(lease_token), lease_token),
		   lease_until = IF(lease_until <= VALUES(updated_at), VALUES(lease_until), lease_until),
		   updated_at = IF(lease_until <= VALUES(updated_at), VALUES(updated_at), updated_at)`,
		userID,
		clientMsgID,
		leaseToken,
		leaseUntilMs,
		nowMs,
		nowMs,
	); err != nil {
		return false, "", err
	}

	var storedToken string
	err = s.db.QueryRowContext(
		ctx,
		"SELECT lease_token FROM chat_stream_inflight WHERE user_id = ? AND client_msg_id = ? LIMIT 1",
		userID,
		clientMsgID,
	).Scan(&storedToken)
	if err != nil {
		if err == sql.ErrNoRows {
			return false, "", nil
		}
		return false, "", err
	}
	return storedToken == leaseToken, leaseToken, nil
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

func (s *Store) HasActiveChatStreamInflight(ctx context.Context, userID string, clientMsgID string, now time.Time) (bool, error) {
	var leaseToken string
	err := s.db.QueryRowContext(
		ctx,
		"SELECT lease_token FROM chat_stream_inflight WHERE user_id = ? AND client_msg_id = ? AND lease_until > ? LIMIT 1",
		userID,
		clientMsgID,
		now.UnixMilli(),
	).Scan(&leaseToken)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return true, nil
}

func (s *Store) HasAnyActiveChatStreamInflight(ctx context.Context, userID string, now time.Time) (bool, error) {
	var leaseToken string
	err := s.db.QueryRowContext(
		ctx,
		"SELECT lease_token FROM chat_stream_inflight WHERE user_id = ? AND lease_until > ? LIMIT 1",
		userID,
		now.UnixMilli(),
	).Scan(&leaseToken)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return true, nil
}

func newChatStreamInflightToken() (string, error) {
	var raw [16]byte
	if _, err := rand.Read(raw[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(raw[:]), nil
}

func chatStreamGateLockName(userID string) string {
	sum := sha1.Sum([]byte(userID))
	return "nongji_chat_" + hex.EncodeToString(sum[:])
}

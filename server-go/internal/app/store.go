package app

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"strings"
	"time"
)

type Store struct {
	db       *sql.DB
	shanghai *time.Location
}

const (
	sessionRoundArchiveRetention = 30 * 24 * time.Hour
	sessionRoundArchiveUILimit   = 30
)

func NewStore(db *sql.DB, shanghai *time.Location) *Store {
	return &Store{
		db:       db,
		shanghai: shanghai,
	}
}

func (s *Store) AppendSessionRoundComplete(
	ctx context.Context,
	userID string,
	clientMsgID string,
	round SessionRound,
	aWindowRounds int,
	bEveryRounds int,
	cEveryRounds int,
	archiveSource string,
) (bool, *SessionSnapshot, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return false, nil, err
	}
	defer rollbackQuietly(tx)

	var ledgerID int64
	err = tx.QueryRowContext(
		ctx,
		"SELECT id FROM session_round_ledger WHERE user_id = ? AND client_msg_id = ? LIMIT 1 FOR UPDATE",
		userID,
		clientMsgID,
	).Scan(&ledgerID)
	if err == nil {
		snapshot, err := s.readSnapshotForUpdateTx(ctx, tx, userID)
		if err != nil {
			return false, nil, err
		}
		if err := tx.Commit(); err != nil {
			return false, nil, err
		}
		return true, snapshot, nil
	}
	if err != nil && err != sql.ErrNoRows {
		return false, nil, err
	}

	nowMs := time.Now().UnixMilli()
	round.CreatedAt = nowMs
	if _, err := tx.ExecContext(
		ctx,
		"INSERT INTO session_round_ledger(user_id, client_msg_id, created_at) VALUES (?, ?, ?)",
		userID,
		clientMsgID,
		nowMs,
	); err != nil {
		return false, nil, err
	}

	if err := s.archiveSessionRoundTx(ctx, tx, userID, clientMsgID, round, archiveSource, nowMs); err != nil {
		return false, nil, err
	}
	if err := s.pruneExpiredSessionRoundArchiveTx(ctx, tx, nowMs); err != nil {
		return false, nil, err
	}

	snapshot, err := s.appendRoundAndUpsertSnapshotTx(ctx, tx, userID, round, aWindowRounds, bEveryRounds, cEveryRounds)
	if err != nil {
		return false, nil, err
	}
	if err := tx.Commit(); err != nil {
		return false, nil, err
	}
	return false, snapshot, nil
}

func (s *Store) WriteUserBSummary(ctx context.Context, userID string, summary string) error {
	normalized := strings.TrimSpace(summary)
	if normalized == "" {
		return fmt.Errorf("b_summary empty")
	}
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO session_ab(user_id, a_json, b_summary, c_summary, round_total, updated_at)
		 VALUES (?, ?, ?, ?, 0, ?)
		 ON DUPLICATE KEY UPDATE b_summary = VALUES(b_summary), pending_retry_b = 0, updated_at = VALUES(updated_at)`,
		userID,
		"[]",
		normalized,
		"",
		time.Now().UnixMilli(),
	)
	return err
}

func (s *Store) WriteUserCSummary(ctx context.Context, userID string, summary string) error {
	normalized := strings.TrimSpace(summary)
	if normalized == "" {
		return fmt.Errorf("c_summary empty")
	}
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO session_ab(user_id, a_json, b_summary, c_summary, round_total, updated_at)
		 VALUES (?, ?, ?, ?, 0, ?)
		 ON DUPLICATE KEY UPDATE c_summary = VALUES(c_summary), pending_retry_c = 0, updated_at = VALUES(updated_at)`,
		userID,
		"[]",
		"",
		normalized,
		time.Now().UnixMilli(),
	)
	return err
}

func (s *Store) SetUserSummaryPending(ctx context.Context, userID string, layer SummaryLayer, pending bool) error {
	pendingRetryB := 0
	pendingRetryC := 0
	column := "pending_retry_b"
	if layer == SummaryLayerB && pending {
		pendingRetryB = 1
	}
	if layer == SummaryLayerC {
		column = "pending_retry_c"
		if pending {
			pendingRetryC = 1
		}
	}

	query := fmt.Sprintf(
		`INSERT INTO session_ab(user_id, a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?, 0, ?)
		 ON DUPLICATE KEY UPDATE %s = VALUES(%s), updated_at = VALUES(updated_at)`,
		column,
		column,
	)
	_, err := s.db.ExecContext(
		ctx,
		query,
		userID,
		"[]",
		"",
		"",
		pendingRetryB,
		pendingRetryC,
		time.Now().UnixMilli(),
	)
	return err
}

func (s *Store) GetSessionSnapshot(ctx context.Context, userID string) (*SessionSnapshot, error) {
	return s.readSnapshotRow(
		s.db.QueryRowContext(
			ctx,
			"SELECT a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1",
			userID,
		),
		userID,
	)
}

func (s *Store) TouchSessionContext(
	ctx context.Context,
	userID string,
	region string,
	source RegionSource,
	reliability RegionReliability,
	seenAt int64,
) error {
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO session_ab(
		   user_id,
		   a_json,
		   b_summary,
		   c_summary,
		   round_total,
		   updated_at,
		   last_region,
		   last_region_source,
		   last_region_reliability,
		   last_seen_at
		 )
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   last_region = VALUES(last_region),
		   last_region_source = VALUES(last_region_source),
		   last_region_reliability = VALUES(last_region_reliability),
		   last_seen_at = VALUES(last_seen_at),
		   updated_at = VALUES(updated_at)`,
		userID,
		"[]",
		"",
		"",
		0,
		seenAt,
		region,
		string(source),
		string(reliability),
		seenAt,
	)
	return err
}

func (s *Store) GetSessionRoundsForUI(ctx context.Context, userID string) ([]SessionRound, error) {
	cutoffMs := time.Now().Add(-sessionRoundArchiveRetention).UnixMilli()
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT client_msg_id, user_text, user_images_json, assistant_text, created_at, region, region_source, region_reliability
		 FROM session_round_archive
		 WHERE user_id = ? AND created_at >= ?
		 ORDER BY created_at DESC, id DESC
		 LIMIT ?`,
		userID,
		cutoffMs,
		sessionRoundArchiveUILimit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	rounds := []SessionRound{}
	for rows.Next() {
		var (
			clientMsgID   string
			userText      string
			userImagesRaw sql.NullString
			assistantText string
			createdAt     int64
			region        sql.NullString
			regionSource  sql.NullString
			regionReliab  sql.NullString
		)
		if err := rows.Scan(&clientMsgID, &userText, &userImagesRaw, &assistantText, &createdAt, &region, &regionSource, &regionReliab); err != nil {
			return nil, err
		}

		userImages := []string{}
		if userImagesRaw.Valid && strings.TrimSpace(userImagesRaw.String) != "" {
			if err := json.Unmarshal([]byte(userImagesRaw.String), &userImages); err != nil {
				return nil, err
			}
		}
		rounds = append(rounds, SessionRound{
			ClientMsgID:       clientMsgID,
			User:              userText,
			UserImages:        userImages,
			Assistant:         assistantText,
			CreatedAt:         createdAt,
			Region:            region.String,
			RegionSource:      RegionSource(regionSource.String),
			RegionReliability: RegionReliability(regionReliab.String),
		})
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	for i, j := 0, len(rounds)-1; i < j; i, j = i+1, j-1 {
		rounds[i], rounds[j] = rounds[j], rounds[i]
	}
	return rounds, nil
}

func (s *Store) appendRoundAndUpsertSnapshotTx(
	ctx context.Context,
	tx *sql.Tx,
	userID string,
	round SessionRound,
	aWindowRounds int,
	bEveryRounds int,
	cEveryRounds int,
) (*SessionSnapshot, error) {
	var (
		aJSON         sql.NullString
		bSummary      sql.NullString
		cSummary      sql.NullString
		pendingRetryB sql.NullInt64
		pendingRetryC sql.NullInt64
		roundTotal    sql.NullInt64
		updatedAt     sql.NullInt64
	)

	err := tx.QueryRowContext(
		ctx,
		"SELECT a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1 FOR UPDATE",
		userID,
	).Scan(&aJSON, &bSummary, &cSummary, &pendingRetryB, &pendingRetryC, &roundTotal, &updatedAt)
	if err != nil && err != sql.ErrNoRows {
		return nil, err
	}

	rounds := []SessionRound{}
	if aJSON.Valid && strings.TrimSpace(aJSON.String) != "" {
		if err := json.Unmarshal([]byte(aJSON.String), &rounds); err != nil {
			return nil, err
		}
	}
	rounds = append(rounds, round)
	for len(rounds) > aWindowRounds {
		rounds = rounds[1:]
	}

	nextRoundTotal := int(roundTotal.Int64) + 1
	nextUpdatedAt := time.Now().UnixMilli()
	nextBSummary := bSummary.String
	nextCSummary := cSummary.String
	nextPendingB := pendingRetryB.Int64 != 0 || (bEveryRounds > 0 && nextRoundTotal%bEveryRounds == 0)
	nextPendingC := pendingRetryC.Int64 != 0 || (cEveryRounds > 0 && nextRoundTotal%cEveryRounds == 0)

	encodedRounds, err := json.Marshal(rounds)
	if err != nil {
		return nil, err
	}

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO session_ab(user_id, a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   a_json = VALUES(a_json),
		   pending_retry_b = VALUES(pending_retry_b),
		   pending_retry_c = VALUES(pending_retry_c),
		   round_total = VALUES(round_total),
		   updated_at = VALUES(updated_at)`,
		userID,
		string(encodedRounds),
		nextBSummary,
		nextCSummary,
		boolToInt(nextPendingB),
		boolToInt(nextPendingC),
		nextRoundTotal,
		nextUpdatedAt,
	); err != nil {
		return nil, err
	}

	return &SessionSnapshot{
		UserID:        userID,
		ARoundsFull:   rounds,
		BSummary:      nextBSummary,
		CSummary:      nextCSummary,
		PendingRetryB: nextPendingB,
		PendingRetryC: nextPendingC,
		RoundTotal:    nextRoundTotal,
		UpdatedAt:     nextUpdatedAt,
	}, nil
}

func (s *Store) archiveSessionRoundTx(
	ctx context.Context,
	tx *sql.Tx,
	userID string,
	clientMsgID string,
	round SessionRound,
	source string,
	nowMs int64,
) error {
	userImagesJSON, err := json.Marshal(round.UserImages)
	if err != nil {
		return err
	}
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO session_round_archive(
		   user_id,
		   client_msg_id,
		   user_text,
		   user_images_json,
		   assistant_text,
		   source,
		   region,
		   region_source,
		   region_reliability,
		   created_at,
		   updated_at
		 )
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   user_text = VALUES(user_text),
		   user_images_json = VALUES(user_images_json),
		   assistant_text = VALUES(assistant_text),
		   source = VALUES(source),
		   region = VALUES(region),
		   region_source = VALUES(region_source),
		   region_reliability = VALUES(region_reliability),
		   updated_at = VALUES(updated_at)`,
		userID,
		clientMsgID,
		round.User,
		string(userImagesJSON),
		round.Assistant,
		normalizeArchiveSource(source),
		nullString(strings.TrimSpace(round.Region)),
		nullString(string(round.RegionSource)),
		nullString(string(round.RegionReliability)),
		nowMs,
		nowMs,
	); err != nil {
		return err
	}
	return nil
}

func (s *Store) pruneExpiredSessionRoundArchiveTx(ctx context.Context, tx *sql.Tx, nowMs int64) error {
	cutoffMs := nowMs - int64(sessionRoundArchiveRetention/time.Millisecond)
	_, err := tx.ExecContext(
		ctx,
		"DELETE FROM session_round_archive WHERE created_at < ? LIMIT 1000",
		cutoffMs,
	)
	return err
}

func (s *Store) readSnapshotForUpdateTx(ctx context.Context, tx *sql.Tx, userID string) (*SessionSnapshot, error) {
	snapshot, err := s.readSnapshotRow(
		tx.QueryRowContext(
			ctx,
			"SELECT a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1 FOR UPDATE",
			userID,
		),
		userID,
	)
	if err != nil {
		return nil, err
	}
	if snapshot != nil {
		return snapshot, nil
	}

	empty := &SessionSnapshot{
		UserID:        userID,
		ARoundsFull:   []SessionRound{},
		BSummary:      "",
		CSummary:      "",
		PendingRetryB: false,
		PendingRetryC: false,
		RoundTotal:    0,
		UpdatedAt:     time.Now().UnixMilli(),
	}
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO session_ab(user_id, a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at)
		 VALUES (?, ?, ?, ?, 0, 0, 0, ?)
		 ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)`,
		userID,
		"[]",
		"",
		"",
		empty.UpdatedAt,
	); err != nil {
		return nil, err
	}
	return empty, nil
}

func (s *Store) readSnapshotRow(row *sql.Row, userID string) (*SessionSnapshot, error) {
	var (
		aJSON         sql.NullString
		bSummary      sql.NullString
		cSummary      sql.NullString
		pendingRetryB sql.NullInt64
		pendingRetryC sql.NullInt64
		roundTotal    sql.NullInt64
		updatedAt     sql.NullInt64
	)

	err := row.Scan(&aJSON, &bSummary, &cSummary, &pendingRetryB, &pendingRetryC, &roundTotal, &updatedAt)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}

	rounds := []SessionRound{}
	if aJSON.Valid && strings.TrimSpace(aJSON.String) != "" {
		if err := json.Unmarshal([]byte(aJSON.String), &rounds); err != nil {
			return nil, err
		}
	}

	return &SessionSnapshot{
		UserID:        userID,
		ARoundsFull:   rounds,
		BSummary:      bSummary.String,
		CSummary:      cSummary.String,
		PendingRetryB: pendingRetryB.Int64 != 0,
		PendingRetryC: pendingRetryC.Int64 != 0,
		RoundTotal:    int(roundTotal.Int64),
		UpdatedAt:     updatedAt.Int64,
	}, nil
}

func rollbackQuietly(tx *sql.Tx) {
	if tx == nil {
		return
	}
	_ = tx.Rollback()
}

func boolToInt(value bool) int {
	if value {
		return 1
	}
	return 0
}

func nullString(value string) sql.NullString {
	normalized := strings.TrimSpace(value)
	if normalized == "" {
		return sql.NullString{}
	}
	return sql.NullString{String: normalized, Valid: true}
}

func normalizeArchiveSource(source string) string {
	normalized := strings.TrimSpace(source)
	if normalized == "" {
		return "round_complete"
	}
	if len(normalized) > 32 {
		return normalized[:32]
	}
	return normalized
}

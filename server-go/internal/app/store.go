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

type Store struct {
	db       *sql.DB
	shanghai *time.Location
}

type dbQueryer interface {
	QueryContext(ctx context.Context, query string, args ...any) (*sql.Rows, error)
}

const (
	sessionRoundArchiveRetention = 30 * 24 * time.Hour
	sessionRoundArchiveUILimit   = 30
)

var (
	ErrSessionRoundRequestConflict = errors.New("session round request conflict")
	ErrSessionRoundArchiveMissing  = errors.New("session round archive missing")
	ErrTodayAgriAnchorNotArchived  = errors.New("today agri anchor not archived")
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
	requestHash string,
	round SessionRound,
	aWindowRounds int,
	memoryEveryRounds int,
	completionAtMs int64,
	tierAtCompletion Tier,
	dayCN string,
	archiveSource string,
) (bool, *SessionSnapshot, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return false, nil, err
	}
	defer rollbackQuietly(tx)

	var ledgerID int64
	var storedRequestHash sql.NullString
	err = tx.QueryRowContext(
		ctx,
		"SELECT id, request_hash FROM session_round_ledger WHERE user_id = ? AND client_msg_id = ? LIMIT 1 FOR UPDATE",
		userID,
		clientMsgID,
	).Scan(&ledgerID, &storedRequestHash)
	if err == nil {
		if storedRequestHash.Valid && strings.TrimSpace(storedRequestHash.String) != "" && strings.TrimSpace(storedRequestHash.String) != strings.TrimSpace(requestHash) {
			return false, nil, ErrSessionRoundRequestConflict
		}
		archiveExists, err := s.sessionRoundArchiveExistsTx(ctx, tx, userID, clientMsgID)
		if err != nil {
			return false, nil, err
		}
		if !archiveExists {
			return false, nil, ErrSessionRoundArchiveMissing
		}
		snapshot, err := s.readSnapshotForUpdateTx(ctx, tx, userID)
		if err != nil {
			return false, nil, err
		}
		if snapshot != nil {
			generation, err := s.currentSessionGenerationForUpdateTx(ctx, tx, userID)
			if err != nil {
				return false, nil, err
			}
			snapshot.SessionGeneration = generation
		}
		if err := tx.Commit(); err != nil {
			return false, nil, err
		}
		return true, snapshot, nil
	}
	if err != nil && err != sql.ErrNoRows {
		return false, nil, err
	}

	nowMs := completionAtMs
	if nowMs <= 0 {
		nowMs = time.Now().UnixMilli()
	}
	roundCreatedAtMs := round.CreatedAt
	if roundCreatedAtMs <= 0 {
		roundCreatedAtMs = nowMs
	}
	round.CreatedAt = roundCreatedAtMs
	if _, err := tx.ExecContext(
		ctx,
		"INSERT INTO session_round_ledger(user_id, client_msg_id, request_hash, created_at) VALUES (?, ?, ?, ?)",
		userID,
		clientMsgID,
		nullableTrimmed(requestHash),
		roundCreatedAtMs,
	); err != nil {
		return false, nil, err
	}

	if err := s.archiveSessionRoundTx(ctx, tx, userID, clientMsgID, round, archiveSource, nowMs); err != nil {
		return false, nil, err
	}
	if err := s.insertPendingQuotaConsumeOutboxTx(ctx, tx, userID, clientMsgID, tierAtCompletion, dayCN, nowMs); err != nil {
		return false, nil, err
	}
	if err := s.pruneExpiredSessionRoundArchiveTx(ctx, tx, nowMs); err != nil {
		return false, nil, err
	}

	snapshot, err := s.appendRoundAndUpsertSnapshotTx(ctx, tx, userID, round, aWindowRounds, memoryEveryRounds)
	if err != nil {
		return false, nil, err
	}
	if snapshot != nil {
		generation, err := s.currentSessionGenerationForUpdateTx(ctx, tx, userID)
		if err != nil {
			return false, nil, err
		}
		snapshot.SessionGeneration = generation
	}
	if err := tx.Commit(); err != nil {
		return false, nil, err
	}
	return false, snapshot, nil
}

func (s *Store) GetSessionRoundCompletion(ctx context.Context, userID string, clientMsgID string) (SessionRoundCompletion, error) {
	var completion SessionRoundCompletion
	var requestHash sql.NullString
	var archiveExists int
	err := s.db.QueryRowContext(
		ctx,
		`SELECT l.created_at,
		        l.request_hash,
		        CASE WHEN a.client_msg_id IS NULL THEN 0 ELSE 1 END AS archive_exists
		 FROM session_round_ledger l
		 LEFT JOIN session_round_archive a
		   ON a.user_id = l.user_id AND a.client_msg_id = l.client_msg_id
		 WHERE l.user_id = ? AND l.client_msg_id = ?
		 LIMIT 1`,
		userID,
		clientMsgID,
	).Scan(&completion.CreatedAt, &requestHash, &archiveExists)
	if err == sql.ErrNoRows {
		return SessionRoundCompletion{}, nil
	}
	if err != nil {
		return SessionRoundCompletion{}, err
	}
	completion.RequestHash = nullStringValue(requestHash)
	if archiveExists <= 0 {
		completion.ArchiveMissing = true
		return completion, nil
	}
	completion.Completed = true
	return completion, nil
}

func (s *Store) sessionRoundArchiveExistsTx(ctx context.Context, tx *sql.Tx, userID string, clientMsgID string) (bool, error) {
	var exists int
	err := tx.QueryRowContext(
		ctx,
		"SELECT 1 FROM session_round_archive WHERE user_id = ? AND client_msg_id = ? LIMIT 1",
		userID,
		clientMsgID,
	).Scan(&exists)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, err
	}
	return exists == 1, nil
}

func (s *Store) CountSessionRoundsAfterClientMsgID(ctx context.Context, userID string, clientMsgID string) (int64, bool, error) {
	userID = strings.TrimSpace(userID)
	clientMsgID = strings.TrimSpace(clientMsgID)
	if userID == "" || clientMsgID == "" {
		return 0, false, nil
	}

	var (
		anchorID        int64
		anchorCreatedAt int64
	)
	err := s.db.QueryRowContext(
		ctx,
		"SELECT id, created_at FROM session_round_archive WHERE user_id = ? AND client_msg_id = ? LIMIT 1",
		userID,
		clientMsgID,
	).Scan(&anchorID, &anchorCreatedAt)
	if err == sql.ErrNoRows {
		return 0, false, nil
	}
	if err != nil {
		return 0, false, err
	}

	var count sql.NullInt64
	err = s.db.QueryRowContext(
		ctx,
		`SELECT COUNT(*)
		 FROM session_round_archive
		 WHERE user_id = ?
		   AND (created_at > ? OR (created_at = ? AND id > ?))`,
		userID,
		anchorCreatedAt,
		anchorCreatedAt,
		anchorID,
	).Scan(&count)
	if err != nil {
		return 0, false, err
	}
	return count.Int64, true, nil
}

func (s *Store) WriteUserMemoryDocumentIfCurrent(ctx context.Context, userID string, memoryDocument string, expectedRoundTotal int, expectedUpdatedAt int64, expectedSessionGeneration int, completedPendingJobIndex int) (bool, error) {
	normalized := strings.TrimSpace(memoryDocument)
	if normalized == "" {
		return false, fmt.Errorf("memory_document empty")
	}
	if expectedUpdatedAt <= 0 {
		return false, nil
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return false, err
	}
	defer rollbackQuietly(tx)

	var pendingJobsRaw sql.NullString
	err = tx.QueryRowContext(
		ctx,
		`SELECT pending_memory_jobs_json
		   FROM session_ab
		  WHERE user_id = ?
		    AND round_total = ?
		    AND updated_at = ?
		    AND COALESCE((SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1), 0) = ?
		    AND COALESCE((SELECT cleared_at FROM session_generation WHERE user_id = ? LIMIT 1), 0) <= ?
		  LIMIT 1
		  FOR UPDATE`,
		userID,
		expectedRoundTotal,
		expectedUpdatedAt,
		userID,
		expectedSessionGeneration,
		userID,
		expectedUpdatedAt,
	).Scan(&pendingJobsRaw)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, err
	}

	pendingJobs, err := parseMemoryExtractionJobs(pendingJobsRaw.String)
	if err != nil {
		return false, err
	}
	remainingJobs := removePendingMemoryJobAt(pendingJobs, completedPendingJobIndex)
	encodedRemainingJobs, err := encodeMemoryExtractionJobs(remainingJobs)
	if err != nil {
		return false, err
	}
	nextPendingMemory := len(remainingJobs) > 0
	result, err := tx.ExecContext(
		ctx,
		`UPDATE session_ab
		 SET b_summary = ?, pending_retry_b = ?, pending_memory_jobs_json = ?, updated_at = ?
		 WHERE user_id = ?
		   AND round_total = ?
		   AND updated_at = ?
		   AND COALESCE((SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1), 0) = ?
		   AND COALESCE((SELECT cleared_at FROM session_generation WHERE user_id = ? LIMIT 1), 0) <= ?`,
		normalized,
		boolToInt(nextPendingMemory),
		encodedRemainingJobs,
		time.Now().UnixMilli(),
		userID,
		expectedRoundTotal,
		expectedUpdatedAt,
		userID,
		expectedSessionGeneration,
		userID,
		expectedUpdatedAt,
	)
	if err != nil {
		return false, err
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return false, err
	}
	if affected == 0 {
		return false, nil
	}
	if err := tx.Commit(); err != nil {
		return false, err
	}
	return true, nil
}

func (s *Store) SetUserMemoryPending(ctx context.Context, userID string, pending bool) error {
	pendingMemory := 0
	if pending {
		pendingMemory = 1
	}

	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO session_ab(user_id, a_json, b_summary, pending_retry_b, pending_memory_jobs_json, round_total, updated_at)
		 VALUES (?, ?, ?, ?, NULL, 0, ?)
		 ON DUPLICATE KEY UPDATE
		   pending_retry_b = VALUES(pending_retry_b),
		   pending_memory_jobs_json = IF(VALUES(pending_retry_b) = 0, NULL, pending_memory_jobs_json),
		   updated_at = VALUES(updated_at)`,
		userID,
		"[]",
		"",
		pendingMemory,
		time.Now().UnixMilli(),
	)
	return err
}

func (s *Store) SetUserMemoryPendingIfCurrent(ctx context.Context, userID string, pending bool, expectedRoundTotal int, expectedUpdatedAt int64, expectedSessionGeneration int) (bool, error) {
	if expectedUpdatedAt <= 0 {
		return false, nil
	}
	pendingMemory := 0
	if pending {
		pendingMemory = 1
	}
	result, err := s.db.ExecContext(
		ctx,
		`UPDATE session_ab
		 SET pending_retry_b = ?, pending_memory_jobs_json = IF(? = 0, NULL, pending_memory_jobs_json), updated_at = ?
		 WHERE user_id = ?
		   AND round_total = ?
		   AND updated_at = ?
		   AND COALESCE((SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1), 0) = ?
		   AND COALESCE((SELECT cleared_at FROM session_generation WHERE user_id = ? LIMIT 1), 0) <= ?`,
		pendingMemory,
		pendingMemory,
		time.Now().UnixMilli(),
		userID,
		expectedRoundTotal,
		expectedUpdatedAt,
		userID,
		expectedSessionGeneration,
		userID,
		expectedUpdatedAt,
	)
	if err != nil {
		return false, err
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return false, err
	}
	return affected > 0, nil
}

func (s *Store) ListPendingMemoryUserIDs(ctx context.Context, limit int) ([]string, error) {
	if limit <= 0 {
		return nil, nil
	}
	if limit > 100 {
		limit = 100
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT user_id
		   FROM session_ab
		  WHERE pending_retry_b = 1
		     OR COALESCE(JSON_LENGTH(pending_memory_jobs_json), 0) > 0
		  ORDER BY updated_at ASC
		  LIMIT ?`,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	userIDs := make([]string, 0, limit)
	for rows.Next() {
		var userID string
		if err := rows.Scan(&userID); err != nil {
			return nil, err
		}
		userID = strings.TrimSpace(userID)
		if userID != "" {
			userIDs = append(userIDs, userID)
		}
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return userIDs, nil
}

func (s *Store) GetSessionSnapshot(ctx context.Context, userID string) (*SessionSnapshot, error) {
	snapshot, err := s.readSnapshotRow(
		s.db.QueryRowContext(
			ctx,
			"SELECT a_json, b_summary, pending_retry_b, pending_memory_jobs_json, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1",
			userID,
		),
		userID,
	)
	if err != nil || snapshot == nil {
		return snapshot, err
	}
	state, err := s.GetSessionGenerationState(ctx, userID)
	if err != nil {
		return nil, err
	}
	snapshot.SessionGeneration = state.Generation
	return snapshot, nil
}

func (s *Store) GetSessionGenerationState(ctx context.Context, userID string) (SessionGenerationState, error) {
	var state SessionGenerationState
	err := s.db.QueryRowContext(
		ctx,
		"SELECT generation, cleared_at FROM session_generation WHERE user_id = ? LIMIT 1",
		userID,
	).Scan(&state.Generation, &state.ClearedAt)
	if err != nil {
		if err == sql.ErrNoRows {
			return SessionGenerationState{}, nil
		}
		return SessionGenerationState{}, err
	}
	return state, nil
}

type SessionSnapshotUIWarnings struct {
	ArchiveErr   error
	TodayAgriErr error
}

func (s *Store) GetSessionSnapshotForUI(ctx context.Context, userID string, todayDayCN string, todayLimit int) (*SessionSnapshot, []SessionRound, []TodayAgriUserItem, SessionSnapshotUIWarnings, error) {
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{
		Isolation: sql.LevelRepeatableRead,
		ReadOnly:  true,
	})
	if err != nil {
		return nil, nil, nil, SessionSnapshotUIWarnings{}, err
	}
	defer rollbackQuietly(tx)

	var generation int
	err = tx.QueryRowContext(
		ctx,
		"SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1",
		userID,
	).Scan(&generation)
	if err == sql.ErrNoRows {
		generation = 0
	} else if err != nil {
		return nil, nil, nil, SessionSnapshotUIWarnings{}, err
	}
	snapshot, err := s.readSnapshotRow(
		tx.QueryRowContext(
			ctx,
			"SELECT a_json, b_summary, pending_retry_b, pending_memory_jobs_json, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1",
			userID,
		),
		userID,
	)
	if err != nil {
		return nil, nil, nil, SessionSnapshotUIWarnings{}, err
	}
	if snapshot == nil {
		snapshot = &SessionSnapshot{
			UserID:            userID,
			ARoundsFull:       []SessionRound{},
			MemoryDocument:    "",
			PendingMemory:     false,
			PendingMemoryJobs: []MemoryExtractionJob{},
			RoundTotal:        0,
			UpdatedAt:         time.Now().UnixMilli(),
		}
	}
	snapshot.SessionGeneration = generation

	var warnings SessionSnapshotUIWarnings
	cutoffMs := time.Now().Add(-sessionRoundArchiveRetention).UnixMilli()
	archivedRounds, err := s.listSessionRoundArchiveWith(ctx, tx, userID, sessionRoundArchiveUILimit, cutoffMs)
	if err != nil {
		if ctxErr := ctx.Err(); ctxErr != nil {
			return nil, nil, nil, warnings, ctxErr
		}
		warnings.ArchiveErr = err
		archivedRounds = nil
	}
	todayItems, err := s.getTodayAgriUserItemsWith(ctx, tx, userID, todayDayCN, todayLimit)
	if err != nil {
		if ctxErr := ctx.Err(); ctxErr != nil {
			return nil, nil, nil, warnings, ctxErr
		}
		warnings.TodayAgriErr = err
		todayItems = nil
	}
	if err := tx.Commit(); err != nil {
		return nil, nil, nil, warnings, err
	}
	return snapshot, archivedRounds, todayItems, warnings, nil
}

func (s *Store) currentSessionGenerationForUpdateTx(ctx context.Context, tx *sql.Tx, userID string) (int, error) {
	var generation int
	err := tx.QueryRowContext(
		ctx,
		"SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1 FOR UPDATE",
		userID,
	).Scan(&generation)
	if err == sql.ErrNoRows {
		return 0, nil
	}
	if err != nil {
		return 0, err
	}
	return generation, nil
}

func (s *Store) ClearSessionHistory(ctx context.Context, userID string) (int, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer rollbackQuietly(tx)

	nowMs := time.Now().UnixMilli()
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO session_generation(user_id, generation, cleared_at, updated_at)
		 VALUES (?, 1, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   generation = generation + 1,
		   cleared_at = VALUES(cleared_at),
		   updated_at = VALUES(updated_at)`,
		userID,
		nowMs,
		nowMs,
	); err != nil {
		return 0, err
	}
	var generation int
	if err := tx.QueryRowContext(
		ctx,
		"SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1 FOR UPDATE",
		userID,
	).Scan(&generation); err != nil {
		return 0, err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM session_round_archive WHERE user_id = ?", userID); err != nil {
		return 0, err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM today_agri_user_items WHERE user_id = ?", userID); err != nil {
		return 0, err
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM session_ab WHERE user_id = ?", userID); err != nil {
		return 0, err
	}
	if err := tx.Commit(); err != nil {
		return 0, err
	}
	return generation, nil
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
		   round_total,
		   updated_at,
		   last_region,
		   last_region_source,
		   last_region_reliability,
		   last_seen_at
		 )
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   last_region = VALUES(last_region),
		   last_region_source = VALUES(last_region_source),
		   last_region_reliability = VALUES(last_region_reliability),
		   last_seen_at = VALUES(last_seen_at)`,
		userID,
		"[]",
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
	return s.listSessionRoundArchive(ctx, userID, sessionRoundArchiveUILimit, cutoffMs)
}

func (s *Store) GetRecentSessionRoundsForSummary(ctx context.Context, userID string, limit int) ([]SessionRound, error) {
	return s.listSessionRoundArchive(ctx, userID, limit, 0)
}

func (s *Store) listSessionRoundArchive(ctx context.Context, userID string, limit int, cutoffMs int64) ([]SessionRound, error) {
	return s.listSessionRoundArchiveWith(ctx, s.db, userID, limit, cutoffMs)
}

func (s *Store) listSessionRoundArchiveWith(ctx context.Context, q dbQueryer, userID string, limit int, cutoffMs int64) ([]SessionRound, error) {
	if limit <= 0 {
		return []SessionRound{}, nil
	}
	query := `SELECT client_msg_id, user_text, user_images_json, assistant_text, created_at, region, region_source, region_reliability
		 FROM session_round_archive
		 WHERE user_id = ?
		 ORDER BY created_at DESC, id DESC
		 LIMIT ?`
	args := []any{userID, limit}
	if cutoffMs > 0 {
		query = `SELECT client_msg_id, user_text, user_images_json, assistant_text, created_at, region, region_source, region_reliability
		 FROM session_round_archive
		 WHERE user_id = ? AND created_at >= ?
		 ORDER BY created_at DESC, id DESC
		 LIMIT ?`
		args = []any{userID, cutoffMs, limit}
	}
	rows, err := q.QueryContext(
		ctx,
		query,
		args...,
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
	memoryEveryRounds int,
) (*SessionSnapshot, error) {
	var (
		aJSON          sql.NullString
		memoryDoc      sql.NullString
		pendingMemory  sql.NullInt64
		pendingJobsRaw sql.NullString
		roundTotal     sql.NullInt64
		updatedAt      sql.NullInt64
	)

	err := tx.QueryRowContext(
		ctx,
		"SELECT a_json, b_summary, pending_retry_b, pending_memory_jobs_json, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1 FOR UPDATE",
		userID,
	).Scan(&aJSON, &memoryDoc, &pendingMemory, &pendingJobsRaw, &roundTotal, &updatedAt)
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
	nextMemoryDocument := memoryDoc.String
	pendingJobs, err := parseMemoryExtractionJobs(pendingJobsRaw.String)
	if err != nil {
		return nil, err
	}
	shouldCreateMemoryJob := memoryEveryRounds > 0 && nextRoundTotal%memoryEveryRounds == 0
	if pendingMemory.Int64 != 0 && len(pendingJobs) == 0 {
		shouldCreateMemoryJob = true
	}
	if shouldCreateMemoryJob {
		pendingJobs = appendMemoryExtractionJobIfMissing(pendingJobs, MemoryExtractionJob{
			RoundTotal: nextRoundTotal,
			Rounds:     cloneSessionRounds(rounds),
		})
	}
	nextPendingMemory := len(pendingJobs) > 0
	encodedPendingJobs, err := encodeMemoryExtractionJobs(pendingJobs)
	if err != nil {
		return nil, err
	}

	encodedRounds, err := json.Marshal(rounds)
	if err != nil {
		return nil, err
	}

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO session_ab(user_id, a_json, b_summary, pending_retry_b, pending_memory_jobs_json, round_total, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   a_json = VALUES(a_json),
		   pending_retry_b = VALUES(pending_retry_b),
		   pending_memory_jobs_json = VALUES(pending_memory_jobs_json),
		   round_total = VALUES(round_total),
		   updated_at = VALUES(updated_at)`,
		userID,
		string(encodedRounds),
		nextMemoryDocument,
		boolToInt(nextPendingMemory),
		encodedPendingJobs,
		nextRoundTotal,
		nextUpdatedAt,
	); err != nil {
		return nil, err
	}

	return &SessionSnapshot{
		UserID:            userID,
		ARoundsFull:       rounds,
		MemoryDocument:    nextMemoryDocument,
		PendingMemory:     nextPendingMemory,
		PendingMemoryJobs: pendingJobs,
		RoundTotal:        nextRoundTotal,
		UpdatedAt:         nextUpdatedAt,
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
		round.CreatedAt,
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

func (s *Store) PruneExpiredSessionRoundArchive(ctx context.Context, nowMs int64) (int64, error) {
	cutoffMs := nowMs - int64(sessionRoundArchiveRetention/time.Millisecond)
	result, err := s.db.ExecContext(
		ctx,
		"DELETE FROM session_round_archive WHERE created_at < ? LIMIT 1000",
		cutoffMs,
	)
	if err != nil {
		return 0, err
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return 0, err
	}
	return rows, nil
}

func (s *Store) readSnapshotForUpdateTx(ctx context.Context, tx *sql.Tx, userID string) (*SessionSnapshot, error) {
	snapshot, err := s.readSnapshotRow(
		tx.QueryRowContext(
			ctx,
			"SELECT a_json, b_summary, pending_retry_b, pending_memory_jobs_json, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1 FOR UPDATE",
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
		UserID:            userID,
		ARoundsFull:       []SessionRound{},
		MemoryDocument:    "",
		PendingMemory:     false,
		PendingMemoryJobs: []MemoryExtractionJob{},
		RoundTotal:        0,
		UpdatedAt:         time.Now().UnixMilli(),
	}
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO session_ab(user_id, a_json, b_summary, pending_retry_b, pending_memory_jobs_json, round_total, updated_at)
		 VALUES (?, ?, ?, 0, NULL, 0, ?)
		 ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)`,
		userID,
		"[]",
		"",
		empty.UpdatedAt,
	); err != nil {
		return nil, err
	}
	return empty, nil
}

func (s *Store) readSnapshotRow(row *sql.Row, userID string) (*SessionSnapshot, error) {
	var (
		aJSON          sql.NullString
		memoryDoc      sql.NullString
		pendingMemory  sql.NullInt64
		pendingJobsRaw sql.NullString
		roundTotal     sql.NullInt64
		updatedAt      sql.NullInt64
	)

	err := row.Scan(&aJSON, &memoryDoc, &pendingMemory, &pendingJobsRaw, &roundTotal, &updatedAt)
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
	pendingJobs, err := parseMemoryExtractionJobs(pendingJobsRaw.String)
	if err != nil {
		return nil, err
	}
	pending := pendingMemory.Int64 != 0 || len(pendingJobs) > 0

	return &SessionSnapshot{
		UserID:            userID,
		ARoundsFull:       rounds,
		MemoryDocument:    memoryDoc.String,
		PendingMemory:     pending,
		PendingMemoryJobs: pendingJobs,
		RoundTotal:        int(roundTotal.Int64),
		UpdatedAt:         updatedAt.Int64,
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

func parseMemoryExtractionJobs(raw string) ([]MemoryExtractionJob, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return []MemoryExtractionJob{}, nil
	}
	var jobs []MemoryExtractionJob
	if err := json.Unmarshal([]byte(raw), &jobs); err != nil {
		return nil, err
	}
	cleaned := make([]MemoryExtractionJob, 0, len(jobs))
	for _, job := range jobs {
		if len(job.Rounds) == 0 {
			continue
		}
		cleaned = append(cleaned, MemoryExtractionJob{
			RoundTotal: job.RoundTotal,
			Rounds:     cloneSessionRounds(job.Rounds),
		})
	}
	return cleaned, nil
}

func encodeMemoryExtractionJobs(jobs []MemoryExtractionJob) (any, error) {
	if len(jobs) == 0 {
		return nil, nil
	}
	encoded, err := json.Marshal(jobs)
	if err != nil {
		return nil, err
	}
	return string(encoded), nil
}

func appendMemoryExtractionJobIfMissing(jobs []MemoryExtractionJob, job MemoryExtractionJob) []MemoryExtractionJob {
	if len(job.Rounds) == 0 {
		return jobs
	}
	for _, current := range jobs {
		if current.RoundTotal == job.RoundTotal {
			return jobs
		}
	}
	return append(jobs, MemoryExtractionJob{
		RoundTotal: job.RoundTotal,
		Rounds:     cloneSessionRounds(job.Rounds),
	})
}

func cloneSessionRounds(rounds []SessionRound) []SessionRound {
	if len(rounds) == 0 {
		return []SessionRound{}
	}
	cloned := make([]SessionRound, len(rounds))
	copy(cloned, rounds)
	for index := range cloned {
		if len(cloned[index].UserImages) > 0 {
			cloned[index].UserImages = append([]string(nil), cloned[index].UserImages...)
		}
	}
	return cloned
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

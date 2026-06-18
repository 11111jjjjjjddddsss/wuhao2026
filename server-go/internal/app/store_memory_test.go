package app

import (
	"context"
	"regexp"
	"testing"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestWriteUserMemoryDocumentIfCurrentGuardsSnapshotGenerationAndUpdatedAt(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	defer db.Close()

	store := NewStore(db, nil)
	userID := "acct_memory_guard"
	expectedRoundTotal := 6
	expectedUpdatedAt := int64(1700000000000)
	expectedGeneration := 2
	selectQuery := regexp.QuoteMeta(`SELECT pending_memory_jobs_json
		   FROM session_ab
		  WHERE user_id = ?
		    AND round_total = ?
		    AND updated_at = ?
		    AND COALESCE((SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1), 0) = ?
		    AND COALESCE((SELECT cleared_at FROM session_generation WHERE user_id = ? LIMIT 1), 0) <= ?
		  LIMIT 1
		  FOR UPDATE`)
	updateQuery := regexp.QuoteMeta(`UPDATE session_ab
		 SET b_summary = ?, pending_retry_b = ?, pending_memory_jobs_json = ?, updated_at = ?
		 WHERE user_id = ?
		   AND round_total = ?
		   AND updated_at = ?
		   AND COALESCE((SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1), 0) = ?
		   AND COALESCE((SELECT cleared_at FROM session_generation WHERE user_id = ? LIMIT 1), 0) <= ?`)

	mock.ExpectBegin()
	mock.ExpectQuery(selectQuery).
		WithArgs(
			userID,
			expectedRoundTotal,
			expectedUpdatedAt,
			userID,
			expectedGeneration,
			userID,
			expectedUpdatedAt,
		).
		WillReturnRows(sqlmock.NewRows([]string{"pending_memory_jobs_json"}).
			AddRow(`[{"round_total":6,"rounds":[{"user":"番茄叶片发黄","assistant":"先看新叶老叶差异"}]}]`))
	mock.ExpectExec(updateQuery).
		WithArgs(
			"短期记忆：继续核对番茄叶片发黄。",
			0,
			nil,
			sqlmock.AnyArg(),
			userID,
			expectedRoundTotal,
			expectedUpdatedAt,
			userID,
			expectedGeneration,
			userID,
			expectedUpdatedAt,
		).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectCommit()

	ok, err := store.WriteUserMemoryDocumentIfCurrent(
		context.Background(),
		userID,
		"  短期记忆：继续核对番茄叶片发黄。  ",
		expectedRoundTotal,
		expectedUpdatedAt,
		expectedGeneration,
	)
	if err != nil {
		t.Fatalf("WriteUserMemoryDocumentIfCurrent failed: %v", err)
	}
	if !ok {
		t.Fatalf("expected current snapshot write to succeed")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet sql expectations: %v", err)
	}
}

func TestWriteUserMemoryDocumentIfCurrentKeepsPendingWhenMoreJobsRemain(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	defer db.Close()

	store := NewStore(db, nil)
	userID := "acct_memory_queue"
	expectedRoundTotal := 12
	expectedUpdatedAt := int64(1700000000000)
	expectedGeneration := 2
	selectQuery := regexp.QuoteMeta(`SELECT pending_memory_jobs_json
		   FROM session_ab
		  WHERE user_id = ?
		    AND round_total = ?
		    AND updated_at = ?
		    AND COALESCE((SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1), 0) = ?
		    AND COALESCE((SELECT cleared_at FROM session_generation WHERE user_id = ? LIMIT 1), 0) <= ?
		  LIMIT 1
		  FOR UPDATE`)
	updateQuery := regexp.QuoteMeta(`UPDATE session_ab
		 SET b_summary = ?, pending_retry_b = ?, pending_memory_jobs_json = ?, updated_at = ?
		 WHERE user_id = ?
		   AND round_total = ?
		   AND updated_at = ?
		   AND COALESCE((SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1), 0) = ?
		   AND COALESCE((SELECT cleared_at FROM session_generation WHERE user_id = ? LIMIT 1), 0) <= ?`)

	mock.ExpectBegin()
	mock.ExpectQuery(selectQuery).
		WithArgs(userID, expectedRoundTotal, expectedUpdatedAt, userID, expectedGeneration, userID, expectedUpdatedAt).
		WillReturnRows(sqlmock.NewRows([]string{"pending_memory_jobs_json"}).
			AddRow(`[{"round_total":6,"rounds":[{"user":"第六轮","assistant":"第六轮答"}]},{"round_total":12,"rounds":[{"user":"第十二轮","assistant":"第十二轮答"}]}]`))
	mock.ExpectExec(updateQuery).
		WithArgs(
			"短期记忆：已整理第六轮。",
			1,
			sqlmock.AnyArg(),
			sqlmock.AnyArg(),
			userID,
			expectedRoundTotal,
			expectedUpdatedAt,
			userID,
			expectedGeneration,
			userID,
			expectedUpdatedAt,
		).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectCommit()

	ok, err := store.WriteUserMemoryDocumentIfCurrent(
		context.Background(),
		userID,
		"短期记忆：已整理第六轮。",
		expectedRoundTotal,
		expectedUpdatedAt,
		expectedGeneration,
	)
	if err != nil {
		t.Fatalf("WriteUserMemoryDocumentIfCurrent failed: %v", err)
	}
	if !ok {
		t.Fatalf("expected queued memory write to succeed")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet sql expectations: %v", err)
	}
}

func TestWriteUserMemoryDocumentIfCurrentRejectsUnknownSnapshotUpdatedAt(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	defer db.Close()

	store := NewStore(db, nil)
	ok, err := store.WriteUserMemoryDocumentIfCurrent(
		context.Background(),
		"acct_memory_guard",
		"短期记忆：继续核对番茄叶片发黄。",
		6,
		0,
		2,
	)
	if err != nil {
		t.Fatalf("WriteUserMemoryDocumentIfCurrent failed: %v", err)
	}
	if ok {
		t.Fatalf("snapshot without updated_at must not be written")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unexpected sql calls: %v", err)
	}
}

func TestSetUserMemoryPendingIfCurrentGuardsSnapshotGenerationAndUpdatedAt(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	defer db.Close()

	store := NewStore(db, nil)
	userID := "acct_memory_guard"
	expectedRoundTotal := 6
	expectedUpdatedAt := int64(1700000000000)
	expectedGeneration := 2
	query := regexp.QuoteMeta(`UPDATE session_ab
		 SET pending_retry_b = ?, pending_memory_jobs_json = IF(? = 0, NULL, pending_memory_jobs_json), updated_at = ?
		 WHERE user_id = ?
		   AND round_total = ?
		   AND updated_at = ?
		   AND COALESCE((SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1), 0) = ?
		   AND COALESCE((SELECT cleared_at FROM session_generation WHERE user_id = ? LIMIT 1), 0) <= ?`)

	mock.ExpectExec(query).
		WithArgs(
			1,
			1,
			sqlmock.AnyArg(),
			userID,
			expectedRoundTotal,
			expectedUpdatedAt,
			userID,
			expectedGeneration,
			userID,
			expectedUpdatedAt,
		).
		WillReturnResult(sqlmock.NewResult(0, 1))

	ok, err := store.SetUserMemoryPendingIfCurrent(
		context.Background(),
		userID,
		true,
		expectedRoundTotal,
		expectedUpdatedAt,
		expectedGeneration,
	)
	if err != nil {
		t.Fatalf("SetUserMemoryPendingIfCurrent failed: %v", err)
	}
	if !ok {
		t.Fatalf("expected current snapshot pending write to succeed")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet sql expectations: %v", err)
	}
}

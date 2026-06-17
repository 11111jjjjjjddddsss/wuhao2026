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
	query := regexp.QuoteMeta(`UPDATE session_ab
		 SET b_summary = ?, pending_retry_b = 0, updated_at = ?
		 WHERE user_id = ?
		   AND round_total = ?
		   AND updated_at = ?
		   AND COALESCE((SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1), 0) = ?
		   AND COALESCE((SELECT cleared_at FROM session_generation WHERE user_id = ? LIMIT 1), 0) <= ?`)

	mock.ExpectExec(query).
		WithArgs(
			"短期记忆：继续核对番茄叶片发黄。",
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
		 SET pending_retry_b = ?, updated_at = ?
		 WHERE user_id = ?
		   AND round_total = ?
		   AND updated_at = ?
		   AND COALESCE((SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1), 0) = ?
		   AND COALESCE((SELECT cleared_at FROM session_generation WHERE user_id = ? LIMIT 1), 0) <= ?`)

	mock.ExpectExec(query).
		WithArgs(
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

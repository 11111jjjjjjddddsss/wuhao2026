package app

import (
	"context"
	"errors"
	"testing"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestGetSessionRoundCompletionRequiresArchive(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	mock.ExpectQuery("SELECT l\\.created_at, l\\.request_hash").
		WithArgs("acct_replay", "cm_replay").
		WillReturnRows(sqlmock.NewRows([]string{"created_at", "request_hash", "archive_exists"}).
			AddRow(int64(1800000000000), "hash-1", 0))

	completion, err := store.GetSessionRoundCompletion(context.Background(), "acct_replay", "cm_replay")
	if err != nil {
		t.Fatalf("GetSessionRoundCompletion failed: %v", err)
	}
	if completion.Completed {
		t.Fatalf("ledger-only completion should not be replayable: %#v", completion)
	}
	if !completion.ArchiveMissing {
		t.Fatalf("ledger-only completion should be marked archive-missing: %#v", completion)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestGetSessionRoundCompletionReturnsReplayWhenArchiveExists(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	mock.ExpectQuery("SELECT l\\.created_at, l\\.request_hash").
		WithArgs("acct_replay", "cm_replay").
		WillReturnRows(sqlmock.NewRows([]string{"created_at", "request_hash", "archive_exists"}).
			AddRow(int64(1800000000000), "hash-1", 1))

	completion, err := store.GetSessionRoundCompletion(context.Background(), "acct_replay", "cm_replay")
	if err != nil {
		t.Fatalf("GetSessionRoundCompletion failed: %v", err)
	}
	if !completion.Completed || completion.ArchiveMissing || completion.CreatedAt != 1800000000000 || completion.RequestHash != "hash-1" {
		t.Fatalf("completion mismatch: %#v", completion)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestAppendSessionRoundCompleteRejectsLedgerWithoutArchiveReplay(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	mock.ExpectBegin()
	mock.ExpectQuery("SELECT id, request_hash FROM session_round_ledger").
		WithArgs("acct_replay", "cm_replay").
		WillReturnRows(sqlmock.NewRows([]string{"id", "request_hash"}).AddRow(int64(7), "hash-1"))
	mock.ExpectQuery("SELECT 1 FROM session_round_archive").
		WithArgs("acct_replay", "cm_replay").
		WillReturnRows(sqlmock.NewRows([]string{"exists"}))
	mock.ExpectRollback()

	_, _, err := store.AppendSessionRoundComplete(
		context.Background(),
		"acct_replay",
		"cm_replay",
		"hash-1",
		SessionRound{ClientMsgID: "cm_replay", User: "病叶", Assistant: "建议先观察", CreatedAt: 1800000000000},
		6,
		6,
		1800000000000,
		TierFree,
		"20260617",
		"stream",
	)
	if !errors.Is(err, ErrSessionRoundArchiveMissing) {
		t.Fatalf("AppendSessionRoundComplete err = %v, want ErrSessionRoundArchiveMissing", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestAppendSessionRoundCompleteReturnsCurrentGeneration(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	nowMs := int64(1800000000000)
	mock.ExpectBegin()
	mock.ExpectQuery("SELECT id, request_hash FROM session_round_ledger").
		WithArgs("acct_generation", "cm_generation").
		WillReturnRows(sqlmock.NewRows([]string{"id", "request_hash"}))
	mock.ExpectExec("INSERT INTO session_round_ledger").
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectExec("INSERT INTO session_round_archive").
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectExec("INSERT INTO quota_consume_outbox").
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectExec("DELETE FROM session_round_archive").
		WillReturnResult(sqlmock.NewResult(0, 0))
	mock.ExpectQuery("SELECT a_json, b_summary, pending_retry_b, round_total, updated_at FROM session_ab").
		WithArgs("acct_generation").
		WillReturnRows(sqlmock.NewRows([]string{"a_json", "b_summary", "pending_retry_b", "round_total", "updated_at"}))
	mock.ExpectExec("INSERT INTO session_ab").
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectQuery("SELECT generation FROM session_generation").
		WithArgs("acct_generation").
		WillReturnRows(sqlmock.NewRows([]string{"generation"}).AddRow(2))
	mock.ExpectCommit()

	_, snapshot, err := store.AppendSessionRoundComplete(
		context.Background(),
		"acct_generation",
		"cm_generation",
		"hash-generation",
		SessionRound{ClientMsgID: "cm_generation", User: "番茄叶片发黄", Assistant: "先看新叶老叶差异", CreatedAt: nowMs},
		6,
		1,
		nowMs,
		TierFree,
		"20260618",
		"stream",
	)
	if err != nil {
		t.Fatalf("AppendSessionRoundComplete failed: %v", err)
	}
	if snapshot == nil || snapshot.SessionGeneration != 2 {
		t.Fatalf("snapshot generation = %#v, want 2", snapshot)
	}
	if !snapshot.PendingMemory {
		t.Fatalf("snapshot should be pending memory at memoryEveryRounds=1")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

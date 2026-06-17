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
		WillReturnRows(sqlmock.NewRows([]string{"created_at", "request_hash"}))

	completion, err := store.GetSessionRoundCompletion(context.Background(), "acct_replay", "cm_replay")
	if err != nil {
		t.Fatalf("GetSessionRoundCompletion failed: %v", err)
	}
	if completion.Completed {
		t.Fatalf("ledger-only completion should not be replayable: %#v", completion)
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
		WillReturnRows(sqlmock.NewRows([]string{"created_at", "request_hash"}).AddRow(int64(1800000000000), "hash-1"))

	completion, err := store.GetSessionRoundCompletion(context.Background(), "acct_replay", "cm_replay")
	if err != nil {
		t.Fatalf("GetSessionRoundCompletion failed: %v", err)
	}
	if !completion.Completed || completion.CreatedAt != 1800000000000 || completion.RequestHash != "hash-1" {
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

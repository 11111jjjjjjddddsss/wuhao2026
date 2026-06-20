package app

import (
	"context"
	"regexp"
	"testing"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestClearSessionHistoryIncrementsGenerationAndDeletesChatOnly(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	defer db.Close()

	store := NewStore(db, nil)
	userID := "acct_clear_history"

	mock.ExpectBegin()
	mock.ExpectExec(regexp.QuoteMeta(`INSERT INTO session_generation(user_id, generation, cleared_at, updated_at)
		 VALUES (?, 1, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   generation = generation + 1,
		   cleared_at = VALUES(cleared_at),
		   updated_at = VALUES(updated_at)`)).
		WithArgs(userID, sqlmock.AnyArg(), sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectQuery(regexp.QuoteMeta("SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1 FOR UPDATE")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"generation"}).AddRow(3))
	mock.ExpectExec(regexp.QuoteMeta("DELETE FROM session_round_archive WHERE user_id = ?")).
		WithArgs(userID).
		WillReturnResult(sqlmock.NewResult(0, 5))
	mock.ExpectExec(regexp.QuoteMeta("DELETE FROM today_agri_user_items WHERE user_id = ?")).
		WithArgs(userID).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectExec(regexp.QuoteMeta("DELETE FROM session_ab WHERE user_id = ?")).
		WithArgs(userID).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectCommit()

	generation, err := store.ClearSessionHistory(context.Background(), userID)
	if err != nil {
		t.Fatalf("ClearSessionHistory failed: %v", err)
	}
	if generation != 3 {
		t.Fatalf("generation mismatch: got %d want 3", generation)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet sql expectations: %v", err)
	}
}

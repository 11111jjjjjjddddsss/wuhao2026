package app

import (
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"regexp"
	"testing"
	"time"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestHandleSessionSnapshotReportsTodayAgriItemsUnavailable(t *testing.T) {
	t.Setenv("AUTH_STRICT", "")
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	shanghai, err := time.LoadLocation("Asia/Shanghai")
	if err != nil {
		t.Fatalf("load shanghai location: %v", err)
	}
	userID := "acct_snapshot_today_handler_fail"
	dayCN := GetTodayKeyCN(shanghai, time.Now())
	aJSON := `[{"client_msg_id":"msg-1","user":"问题","assistant":"回答","created_at":1700000000000}]`

	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta("SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"generation"}).AddRow(5))
	mock.ExpectQuery(regexp.QuoteMeta("SELECT a_json, b_summary, pending_retry_b, pending_memory_jobs_json, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"a_json", "b_summary", "pending_retry_b", "pending_memory_jobs_json", "round_total", "updated_at"}).
			AddRow(aJSON, "memory", 0, nil, 1, int64(1700000000100)))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT client_msg_id, user_text, user_images_json, assistant_text, created_at, region, region_source, region_reliability
		 FROM session_round_archive
		 WHERE user_id = ? AND created_at >= ?
		 ORDER BY created_at DESC, id DESC
		 LIMIT ?`)).
		WithArgs(userID, sqlmock.AnyArg(), sessionRoundArchiveUILimit).
		WillReturnRows(sqlmock.NewRows([]string{"client_msg_id", "user_text", "user_images_json", "assistant_text", "created_at", "region", "region_source", "region_reliability"}))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT day_cn, anchor_client_msg_id, content_json, created_at, updated_at
		 FROM today_agri_user_items
		 WHERE user_id = ? AND day_cn = ?
		 ORDER BY updated_at DESC, day_cn DESC
		 LIMIT ?`)).
		WithArgs(userID, dayCN, 1).
		WillReturnError(errors.New("today agri item unavailable"))
	mock.ExpectCommit()

	server := &Server{
		logger:   slog.New(slog.NewTextHandler(io.Discard, nil)),
		store:    store,
		shanghai: shanghai,
	}
	req := httptest.NewRequest(http.MethodGet, "/api/session/snapshot", nil)
	req.Header.Set("X-User-Id", userID)
	rec := httptest.NewRecorder()

	server.handleSessionSnapshot(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d body=%s", rec.Code, rec.Body.String())
	}
	var body struct {
		TodayAgriItemsUnavailable bool                `json:"today_agri_items_unavailable"`
		TodayAgriItems            []TodayAgriUserItem `json:"today_agri_items"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if !body.TodayAgriItemsUnavailable {
		t.Fatalf("today_agri_items_unavailable=false, want true; body=%s", rec.Body.String())
	}
	if len(body.TodayAgriItems) != 0 {
		t.Fatalf("today agri items should degrade to empty, got %+v", body.TodayAgriItems)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestHandleSessionSnapshotReportsArchiveUnavailable(t *testing.T) {
	t.Setenv("AUTH_STRICT", "")
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	shanghai, err := time.LoadLocation("Asia/Shanghai")
	if err != nil {
		t.Fatalf("load shanghai location: %v", err)
	}
	userID := "acct_snapshot_archive_handler_fail"
	dayCN := GetTodayKeyCN(shanghai, time.Now())
	aJSON := `[{"client_msg_id":"msg-1","user":"问题","assistant":"回答","created_at":1700000000000}]`

	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta("SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"generation"}).AddRow(5))
	mock.ExpectQuery(regexp.QuoteMeta("SELECT a_json, b_summary, pending_retry_b, pending_memory_jobs_json, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"a_json", "b_summary", "pending_retry_b", "pending_memory_jobs_json", "round_total", "updated_at"}).
			AddRow(aJSON, "memory", 0, nil, 1, int64(1700000000100)))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT client_msg_id, user_text, user_images_json, assistant_text, created_at, region, region_source, region_reliability
		 FROM session_round_archive
		 WHERE user_id = ? AND created_at >= ?
		 ORDER BY created_at DESC, id DESC
		 LIMIT ?`)).
		WithArgs(userID, sqlmock.AnyArg(), sessionRoundArchiveUILimit).
		WillReturnError(errors.New("archive unavailable"))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT day_cn, anchor_client_msg_id, content_json, created_at, updated_at
		 FROM today_agri_user_items
		 WHERE user_id = ? AND day_cn = ?
		 ORDER BY updated_at DESC, day_cn DESC
		 LIMIT ?`)).
		WithArgs(userID, dayCN, 1).
		WillReturnRows(sqlmock.NewRows([]string{"day_cn", "anchor_client_msg_id", "content_json", "created_at", "updated_at"}))
	mock.ExpectCommit()

	server := &Server{
		logger:   slog.New(slog.NewTextHandler(io.Discard, nil)),
		store:    store,
		shanghai: shanghai,
	}
	req := httptest.NewRequest(http.MethodGet, "/api/session/snapshot", nil)
	req.Header.Set("X-User-Id", userID)
	rec := httptest.NewRecorder()

	server.handleSessionSnapshot(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d body=%s", rec.Code, rec.Body.String())
	}
	var body struct {
		ArchiveUnavailable bool `json:"archive_unavailable"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if !body.ArchiveUnavailable {
		t.Fatalf("archive_unavailable=false, want true; body=%s", rec.Body.String())
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

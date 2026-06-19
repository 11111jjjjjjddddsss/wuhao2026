package app

import (
	"bytes"
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"regexp"
	"strings"
	"testing"
	"time"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func testDailyAgriCard(dayCN string) DailyAgriCard {
	return DailyAgriCard{
		DateCN:      dayCN,
		Title:       "今日农情",
		GeneratedAt: 1700000000000,
		Items: []DailyAgriCardItem{
			{Title: "病虫监测", Summary: "多地提醒加强田间巡查。", Source: "全国农技中心"},
			{Title: "栽培管理", Summary: "雨后注意排水和控旺。", Source: "农业农村部"},
			{Title: "产地流通", Summary: "部分蔬菜产区供应恢复。", Source: "人民资讯"},
		},
	}
}

func TestSaveTodayAgriItemRateLimitRunsAfterBasicValidation(t *testing.T) {
	source, err := os.ReadFile("daily_agri.go")
	if err != nil {
		t.Fatalf("read daily_agri.go: %v", err)
	}
	text := string(source)
	start := strings.Index(text, "func (s *Server) handleSaveTodayAgriItem")
	if start < 0 {
		t.Fatalf("handleSaveTodayAgriItem not found")
	}
	end := strings.Index(text[start:], "type dailyAgriPublicCard")
	if end < 0 {
		t.Fatalf("dailyAgriPublicCard marker not found")
	}
	block := text[start : start+end]
	decodeAt := strings.Index(block, "decodeJSONBodyLimited")
	dayAt := strings.Index(block, "normalizeTodayAgriContextDay")
	anchorAt := strings.Index(block, `s.writeError(w, http.StatusBadRequest, "invalid_anchor")`)
	readyAt := strings.Index(block, `"today_agri_card_not_ready"`)
	consumeAt := strings.Index(block, "consumeTodayAgriItemSaveRateLimit")
	if decodeAt < 0 || dayAt < 0 || anchorAt < 0 || readyAt < 0 || consumeAt < 0 {
		t.Fatalf("save today agri handler missing expected validation or rate-limit call")
	}
	if !(decodeAt < consumeAt && dayAt < consumeAt && anchorAt < consumeAt && readyAt < consumeAt) {
		t.Fatalf("today agri item save rate limit should run after JSON, day, anchor and card-ready validation")
	}
}

func TestUpsertTodayAgriUserItemRejectsStaleGeneration(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	userID := "acct_agri_stale"
	expectedGeneration := 1

	mock.ExpectBegin()
	mock.ExpectExec(regexp.QuoteMeta(`INSERT INTO session_generation(user_id, generation, cleared_at, updated_at)
		 VALUES (?, 0, 0, ?)
		 ON DUPLICATE KEY UPDATE user_id = user_id`)).
		WithArgs(userID, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT generation, cleared_at
		 FROM session_generation
		 WHERE user_id = ?
		 LIMIT 1 FOR UPDATE`)).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"generation", "cleared_at"}).AddRow(2, int64(1700000000000)))
	mock.ExpectCommit()

	saved, err := store.UpsertTodayAgriUserItem(context.Background(), userID, "20260617", "assistant-1", testDailyAgriCard("20260617"), &expectedGeneration)
	if err != nil {
		t.Fatalf("UpsertTodayAgriUserItem returned error: %v", err)
	}
	if saved {
		t.Fatalf("stale generation must not save today agri main item")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestUpsertTodayAgriUserItemSavesCurrentRecordAndDropsOtherDays(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	userID := "acct_agri_ok"
	dayCN := "20260617"
	anchorID := "assistant_msg-1"
	normalizedAnchorID := "msg-1"
	expectedGeneration := 3

	mock.ExpectBegin()
	mock.ExpectExec(regexp.QuoteMeta(`INSERT INTO session_generation(user_id, generation, cleared_at, updated_at)
		 VALUES (?, 0, 0, ?)
		 ON DUPLICATE KEY UPDATE user_id = user_id`)).
		WithArgs(userID, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT generation, cleared_at
		 FROM session_generation
		 WHERE user_id = ?
		 LIMIT 1 FOR UPDATE`)).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"generation", "cleared_at"}).AddRow(expectedGeneration, int64(0)))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT 1 FROM session_round_archive WHERE user_id = ? AND client_msg_id = ? LIMIT 1`)).
		WithArgs(userID, normalizedAnchorID).
		WillReturnRows(sqlmock.NewRows([]string{"1"}).AddRow(1))
	mock.ExpectExec(regexp.QuoteMeta(`DELETE FROM today_agri_user_items
		 WHERE user_id = ? AND day_cn <> ?`)).
		WithArgs(userID, dayCN).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectExec(regexp.QuoteMeta(`INSERT INTO today_agri_user_items(user_id, day_cn, anchor_client_msg_id, content_json, created_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   anchor_client_msg_id = VALUES(anchor_client_msg_id),
		   content_json = VALUES(content_json),
		   updated_at = VALUES(updated_at)`)).
		WithArgs(userID, dayCN, normalizedAnchorID, sqlmock.AnyArg(), sqlmock.AnyArg(), sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectCommit()

	saved, err := store.UpsertTodayAgriUserItem(context.Background(), userID, dayCN, anchorID, testDailyAgriCard(dayCN), &expectedGeneration)
	if err != nil {
		t.Fatalf("UpsertTodayAgriUserItem returned error: %v", err)
	}
	if !saved {
		t.Fatalf("matching generation should save today agri main item")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestUpsertTodayAgriUserItemRejectsMissingArchivedAnchor(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	userID := "acct_agri_missing_anchor"
	dayCN := "20260617"
	expectedGeneration := 3

	mock.ExpectBegin()
	mock.ExpectExec(regexp.QuoteMeta(`INSERT INTO session_generation(user_id, generation, cleared_at, updated_at)
		 VALUES (?, 0, 0, ?)
		 ON DUPLICATE KEY UPDATE user_id = user_id`)).
		WithArgs(userID, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT generation, cleared_at
		 FROM session_generation
		 WHERE user_id = ?
		 LIMIT 1 FOR UPDATE`)).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"generation", "cleared_at"}).AddRow(expectedGeneration, int64(0)))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT 1 FROM session_round_archive WHERE user_id = ? AND client_msg_id = ? LIMIT 1`)).
		WithArgs(userID, "missing").
		WillReturnRows(sqlmock.NewRows([]string{"1"}))
	mock.ExpectRollback()

	saved, err := store.UpsertTodayAgriUserItem(
		context.Background(),
		userID,
		dayCN,
		"assistant_missing",
		testDailyAgriCard(dayCN),
		&expectedGeneration,
	)
	if !errors.Is(err, ErrTodayAgriAnchorNotArchived) {
		t.Fatalf("expected ErrTodayAgriAnchorNotArchived, got saved=%v err=%v", saved, err)
	}
	if saved {
		t.Fatalf("missing archive anchor must not save today agri main item")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestSanitizeTodayAgriMainItemCardDropsURLsAndKeepsPublicSources(t *testing.T) {
	card := testDailyAgriCard("")
	card.SourceType = "manual"
	card.ManualLocked = true
	card.ManualBy = "owner"
	card.ManualAt = 1700000001000
	card.Items[0].Source = "2026年农产品供需形势分析报告"
	card.Items[0].PublishedDate = "2026-06-17"
	card.Items[0].URL = "https://example.com/news/1"
	card.Items[1].PublishedDate = "2026-06-16"
	card.Items[1].URL = "https://example.org/news/2"

	got := sanitizeTodayAgriMainItemCard(card, "20260617")

	if got.DateCN != "20260617" {
		t.Fatalf("DateCN=%q", got.DateCN)
	}
	if got.Title != "今日农情" {
		t.Fatalf("Title=%q", got.Title)
	}
	if got.SourceType != "" || got.ManualLocked || got.ManualBy != "" || got.ManualAt != 0 {
		t.Fatalf("internal manual fields should be stripped from main item card: %#v", got)
	}
	for idx, item := range got.Items {
		if item.URL != "" {
			t.Fatalf("item %d URL should be stripped, got %q", idx, item.URL)
		}
		if item.PublishedDate != "" {
			t.Fatalf("item %d published date should be stripped, got %q", idx, item.PublishedDate)
		}
	}
	if got.Items[0].Source != "example.com" {
		t.Fatalf("expected host fallback source, got %q", got.Items[0].Source)
	}
	if got.Items[1].Source != "农业农村部" {
		t.Fatalf("expected source name to remain, got %q", got.Items[1].Source)
	}
}

func TestHandleSaveTodayAgriItemRejectsNonCurrentDay(t *testing.T) {
	t.Setenv("AUTH_STRICT", "")
	shanghai := time.FixedZone("Asia/Shanghai", 8*60*60)
	yesterday := GetTodayKeyCN(shanghai, time.Now().AddDate(0, 0, -1))
	server := &Server{
		logger:   slog.New(slog.NewTextHandler(io.Discard, nil)),
		shanghai: shanghai,
	}
	body := []byte(`{"day_cn":"` + yesterday + `","anchor_client_msg_id":"assistant-1","session_generation":1}`)
	req := httptest.NewRequest(http.MethodPost, "/api/today-agri-item", bytes.NewReader(body))
	req.Header.Set("X-User-Id", "acct_agri")
	rec := httptest.NewRecorder()

	server.handleSaveTodayAgriItem(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status=%d body=%s", rec.Code, rec.Body.String())
	}
	if !strings.Contains(rec.Body.String(), "today_agri_item_day_not_current") {
		t.Fatalf("expected non-current day error, got %s", rec.Body.String())
	}
}

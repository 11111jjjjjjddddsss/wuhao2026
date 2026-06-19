package app

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"regexp"
	"testing"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestPublishManualDailyAgriCardWritesReadyLockedCard(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	mock.ExpectExec(regexp.QuoteMeta(`INSERT INTO daily_agri_cards(
		     day_cn, scope, status, content_json, sources_json,
		     model, search_strategy, prompt_version, source_type, manual_locked, manual_by, manual_at,
		     lease_token, lease_until, generated_at, error, created_at, updated_at
		   )
		   VALUES (?, ?, 'ready', ?, ?, 'manual', 'manual', 'manual', ?, 1, ?, ?, NULL, 0, ?, NULL, ?, ?)
		   ON DUPLICATE KEY UPDATE
		     status = 'ready',
		     content_json = VALUES(content_json),
		     sources_json = VALUES(sources_json),
		     model = 'manual',
		     search_strategy = 'manual',
		     prompt_version = 'manual',
		     source_type = VALUES(source_type),
		     manual_locked = 1,
		     manual_by = VALUES(manual_by),
		     manual_at = VALUES(manual_at),
		     lease_token = NULL,
		     lease_until = 0,
		     generated_at = VALUES(generated_at),
		     error = NULL,
		     updated_at = VALUES(updated_at)`)).
		WithArgs(
			"20260618",
			dailyAgriDefaultScope,
			sqlmock.AnyArg(),
			sqlmock.AnyArg(),
			dailyAgriSourceTypeManual,
			"codex",
			sqlmock.AnyArg(),
			sqlmock.AnyArg(),
			sqlmock.AnyArg(),
			sqlmock.AnyArg(),
		).
		WillReturnResult(sqlmock.NewResult(0, 1))

	card, err := store.PublishManualDailyAgriCard(context.Background(), ManualDailyAgriPublishInput{
		DayCN:       "20260618",
		Scope:       dailyAgriDefaultScope,
		PublishedBy: "codex",
		Items: []DailyAgriCardItem{
			{Title: "夏收进度继续推进", Summary: "多地小麦机收进入尾声，注意抢晴收晒和通风归仓。", Source: "中央气象台"},
			{Title: "夏玉米播种进入窗口", Summary: "黄淮海北部力争适期完成夏玉米播种，墒情不足地块先造墒。", Source: "全国农技中心"},
			{Title: "果蔬绿色生产强化", Summary: "果蔬专家组强调推进绿色防控、水肥一体化和设施栽培提质。", Source: "全国农技中心"},
		},
	})
	if err != nil {
		t.Fatalf("PublishManualDailyAgriCard returned error: %v", err)
	}
	if card.SourceType != dailyAgriSourceTypeManual || !card.ManualLocked {
		t.Fatalf("manual metadata not set: %#v", card)
	}
	if card.ManualBy != "codex" || len(card.Items) != dailyAgriTargetItemCount {
		t.Fatalf("unexpected card: %#v", card)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestTryAcquireDailyAgriCardGenerationSkipsManualLockedCard(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	mock.ExpectBegin()
	mock.ExpectExec(regexp.QuoteMeta(`INSERT INTO daily_agri_cards(day_cn, scope, status, model, search_strategy, prompt_version, source_type, manual_locked, lease_token, lease_until, created_at, updated_at)
		 VALUES (?, ?, 'pending', ?, ?, ?, ?, 0, '', 0, ?, ?)
		 ON DUPLICATE KEY UPDATE updated_at = updated_at`)).
		WithArgs("20260618", dailyAgriDefaultScope, "qwen3.5-plus", "turbo", "prompt-v1", dailyAgriSourceTypeAuto, sqlmock.AnyArg(), sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT status, lease_until, content_json, manual_locked
		 FROM daily_agri_cards
		 WHERE day_cn = ? AND scope = ?
		 LIMIT 1 FOR UPDATE`)).
		WithArgs("20260618", dailyAgriDefaultScope).
		WillReturnRows(sqlmock.NewRows([]string{"status", "lease_until", "content_json", "manual_locked"}).
			AddRow("ready", int64(0), `{"title":"今日农情","items":[{"title":"一","summary":"一"},{"title":"二","summary":"二"},{"title":"三","summary":"三"}]}`, true))
	mock.ExpectCommit()

	acquired, err := store.TryAcquireDailyAgriCardGeneration(
		context.Background(),
		"20260618",
		dailyAgriDefaultScope,
		"qwen3.5-plus",
		"turbo",
		"prompt-v1",
		"lease-token",
		1700000000000,
	)
	if err != nil {
		t.Fatalf("TryAcquireDailyAgriCardGeneration returned error: %v", err)
	}
	if acquired {
		t.Fatalf("manual locked card must not be acquired for auto generation")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestHandleInternalTodayAgriCardStatusReturnsManualLock(t *testing.T) {
	t.Setenv("DAILY_AGRI_JOB_SECRET", "secret")
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()
	server := &Server{
		store: store,
	}

	content := `{"title":"今日农情","items":[{"title":"一","summary":"一"},{"title":"二","summary":"二"},{"title":"三","summary":"三"}]}`
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT status, content_json, generated_at, source_type, manual_locked, manual_by, manual_at, lease_until, error
		 FROM daily_agri_cards
		 WHERE day_cn = ? AND scope = ?
		 LIMIT 1`)).
		WithArgs("20260618", dailyAgriDefaultScope).
		WillReturnRows(sqlmock.NewRows([]string{"status", "content_json", "generated_at", "source_type", "manual_locked", "manual_by", "manual_at", "lease_until", "error"}).
			AddRow("ready", content, int64(1800000000000), dailyAgriSourceTypeManual, true, "codex", int64(1800000000000), int64(0), nil))

	req := httptest.NewRequest(http.MethodGet, "/internal/jobs/today-agri-card/status?day_cn=20260618", nil)
	req.Header.Set("X-Internal-Job-Secret", "secret")
	rec := httptest.NewRecorder()
	server.handleInternalTodayAgriCardStatus(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body=%s", rec.Code, rec.Body.String())
	}
	var got map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if got["status"] != "ready" || got["source_type"] != dailyAgriSourceTypeManual || got["manual_locked"] != true {
		t.Fatalf("unexpected response: %#v", got)
	}
	if got["ready"] != true || got["content_valid"] != true || got["content_present"] != true {
		t.Fatalf("expected usable ready content: %#v", got)
	}
	if got["item_count"] != float64(3) {
		t.Fatalf("item_count = %#v, want 3", got["item_count"])
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestHandleInternalTodayAgriCardStatusReturnsManualLockForInvalidContent(t *testing.T) {
	t.Setenv("DAILY_AGRI_JOB_SECRET", "secret")
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()
	server := &Server{
		store: store,
	}

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT status, content_json, generated_at, source_type, manual_locked, manual_by, manual_at, lease_until, error
		 FROM daily_agri_cards
		 WHERE day_cn = ? AND scope = ?
		 LIMIT 1`)).
		WithArgs("20260618", dailyAgriDefaultScope).
		WillReturnRows(sqlmock.NewRows([]string{"status", "content_json", "generated_at", "source_type", "manual_locked", "manual_by", "manual_at", "lease_until", "error"}).
			AddRow("ready", "{bad-json", int64(1800000000000), dailyAgriSourceTypeManual, true, "codex", int64(1800000000000), int64(0), nil))

	req := httptest.NewRequest(http.MethodGet, "/internal/jobs/today-agri-card/status?day_cn=20260618", nil)
	req.Header.Set("X-Internal-Job-Secret", "secret")
	rec := httptest.NewRecorder()
	server.handleInternalTodayAgriCardStatus(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, body=%s", rec.Code, rec.Body.String())
	}
	var got map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &got); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if got["status"] != "ready" || got["source_type"] != dailyAgriSourceTypeManual || got["manual_locked"] != true {
		t.Fatalf("manual lock metadata should survive invalid content: %#v", got)
	}
	if got["ready"] != false || got["content_valid"] != false || got["content_present"] != true {
		t.Fatalf("invalid content should not be reported as display-ready: %#v", got)
	}
	if got["item_count"] != float64(0) {
		t.Fatalf("item_count = %#v, want 0", got["item_count"])
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

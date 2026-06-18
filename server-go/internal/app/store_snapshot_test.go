package app

import (
	"context"
	"errors"
	"regexp"
	"testing"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestGetSessionSnapshotForUIUsesReadOnlyConsistentTransaction(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	userID := "acct_snapshot"
	dayCN := "20260618"
	aJSON := `[{"client_msg_id":"msg-1","user":"问题","assistant":"回答","created_at":1700000000000}]`
	cardJSON := `{"date_cn":"20260618","title":"今日农情","items":[{"title":"病虫监测","summary":"加强巡查。","source":"全国农技中心"},{"title":"栽培管理","summary":"雨后排水。","source":"农业农村部"},{"title":"产地流通","summary":"供应稳定。","source":"人民资讯"}]}`

	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta("SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"generation"}).AddRow(3))
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
		WillReturnRows(sqlmock.NewRows([]string{"day_cn", "anchor_client_msg_id", "content_json", "created_at", "updated_at"}).
			AddRow(dayCN, "msg-1", cardJSON, int64(1700000000200), int64(1700000000300)))
	mock.ExpectCommit()

	snapshot, archived, todayItems, warnings, err := store.GetSessionSnapshotForUI(context.Background(), userID, dayCN, 1)
	if err != nil {
		t.Fatalf("GetSessionSnapshotForUI returned error: %v", err)
	}
	if warnings.ArchiveErr != nil || warnings.TodayAgriErr != nil {
		t.Fatalf("unexpected warnings: %+v", warnings)
	}
	if snapshot.SessionGeneration != 3 {
		t.Fatalf("SessionGeneration=%d, want 3", snapshot.SessionGeneration)
	}
	if len(snapshot.ARoundsFull) != 1 || snapshot.ARoundsFull[0].ClientMsgID != "msg-1" {
		t.Fatalf("unexpected rounds: %+v", snapshot.ARoundsFull)
	}
	if len(archived) != 0 {
		t.Fatalf("archived rounds should be empty, got %+v", archived)
	}
	if len(todayItems) != 1 || todayItems[0].AnchorClientMsgID != "msg-1" {
		t.Fatalf("unexpected today items: %+v", todayItems)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestGetSessionSnapshotForUINewUserDoesNotCreateGenerationRow(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	userID := "acct_new_snapshot"
	dayCN := "20260618"

	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta("SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"generation"}))
	mock.ExpectQuery(regexp.QuoteMeta("SELECT a_json, b_summary, pending_retry_b, pending_memory_jobs_json, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"a_json", "b_summary", "pending_retry_b", "pending_memory_jobs_json", "round_total", "updated_at"}))
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
		WillReturnRows(sqlmock.NewRows([]string{"day_cn", "anchor_client_msg_id", "content_json", "created_at", "updated_at"}))
	mock.ExpectCommit()

	snapshot, archived, todayItems, warnings, err := store.GetSessionSnapshotForUI(context.Background(), userID, dayCN, 1)
	if err != nil {
		t.Fatalf("GetSessionSnapshotForUI returned error: %v", err)
	}
	if warnings.ArchiveErr != nil || warnings.TodayAgriErr != nil {
		t.Fatalf("unexpected warnings: %+v", warnings)
	}
	if snapshot == nil || snapshot.SessionGeneration != 0 || len(snapshot.ARoundsFull) != 0 {
		t.Fatalf("unexpected empty snapshot: %+v", snapshot)
	}
	if len(archived) != 0 || len(todayItems) != 0 {
		t.Fatalf("expected empty archived/today items, got archived=%+v today=%+v", archived, todayItems)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestGetSessionSnapshotForUIDegradesArchiveReadFailure(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	userID := "acct_snapshot_archive_fail"
	dayCN := "20260618"
	aJSON := `[{"client_msg_id":"msg-1","user":"问题","assistant":"回答","created_at":1700000000000}]`

	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta("SELECT generation FROM session_generation WHERE user_id = ? LIMIT 1")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"generation"}).AddRow(4))
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

	snapshot, archived, todayItems, warnings, err := store.GetSessionSnapshotForUI(context.Background(), userID, dayCN, 1)
	if err != nil {
		t.Fatalf("GetSessionSnapshotForUI returned error: %v", err)
	}
	if snapshot == nil || snapshot.SessionGeneration != 4 || len(snapshot.ARoundsFull) != 1 {
		t.Fatalf("unexpected snapshot: %+v", snapshot)
	}
	if warnings.ArchiveErr == nil || warnings.TodayAgriErr != nil {
		t.Fatalf("unexpected warnings: %+v", warnings)
	}
	if len(archived) != 0 || len(todayItems) != 0 {
		t.Fatalf("expected degraded empty archive/today items, got archived=%+v today=%+v", archived, todayItems)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestGetSessionSnapshotForUIDegradesTodayAgriReadFailure(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	userID := "acct_snapshot_today_fail"
	dayCN := "20260618"
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

	snapshot, archived, todayItems, warnings, err := store.GetSessionSnapshotForUI(context.Background(), userID, dayCN, 1)
	if err != nil {
		t.Fatalf("GetSessionSnapshotForUI returned error: %v", err)
	}
	if snapshot == nil || snapshot.SessionGeneration != 5 || len(snapshot.ARoundsFull) != 1 {
		t.Fatalf("unexpected snapshot: %+v", snapshot)
	}
	if warnings.ArchiveErr != nil || warnings.TodayAgriErr == nil {
		t.Fatalf("unexpected warnings: %+v", warnings)
	}
	if len(archived) != 0 || len(todayItems) != 0 {
		t.Fatalf("expected empty archive/today items, got archived=%+v today=%+v", archived, todayItems)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

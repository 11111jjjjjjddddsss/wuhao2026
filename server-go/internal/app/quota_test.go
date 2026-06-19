package app

import (
	"context"
	"database/sql"
	"errors"
	"regexp"
	"testing"
	"time"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestQuotaBusinessConstantsMatchCurrentRules(t *testing.T) {
	if plusTierPrice != 19.9 {
		t.Fatalf("plus renew price mismatch: %v", plusTierPrice)
	}
	if proTierPrice != 29.9 {
		t.Fatalf("pro renew price mismatch: %v", proTierPrice)
	}
	if topupPackPrice != 6.0 {
		t.Fatalf("topup pack price mismatch: %v", topupPackPrice)
	}
	if topupPackRemaining != 80 {
		t.Fatalf("topup pack remaining mismatch: %d", topupPackRemaining)
	}
	if topupPackActiveLimit != 1 {
		t.Fatalf("topup active limit mismatch: %d", topupPackActiveLimit)
	}
}

func TestDevOrderEndpointsAreDisabledByDefault(t *testing.T) {
	t.Setenv("APP_ENV", "")
	t.Setenv("ALLOW_DEV_ORDER_ENDPOINTS", "")

	if devOrderEndpointsEnabled() {
		t.Fatal("dev order endpoints should be disabled by default")
	}
}

func TestDevOrderEndpointsRequireExplicitOptIn(t *testing.T) {
	t.Setenv("APP_ENV", "development")
	t.Setenv("ALLOW_DEV_ORDER_ENDPOINTS", "true")

	if !devOrderEndpointsEnabled() {
		t.Fatal("dev order endpoints should be enabled by explicit dev opt-in")
	}
}

func TestDevOrderEndpointsRequireDevelopmentEnvironment(t *testing.T) {
	t.Setenv("APP_ENV", "")
	t.Setenv("ALLOW_DEV_ORDER_ENDPOINTS", "true")

	if devOrderEndpointsEnabled() {
		t.Fatal("dev order endpoints should require an explicit development environment")
	}
}

func TestDevOrderEndpointsStayDisabledInProduction(t *testing.T) {
	t.Setenv("APP_ENV", "production")
	t.Setenv("ALLOW_DEV_ORDER_ENDPOINTS", "true")

	if devOrderEndpointsEnabled() {
		t.Fatal("dev order endpoints should stay disabled in production")
	}
}

func TestEffectiveTierFromRowExpiresPaidTier(t *testing.T) {
	now := int64(1_700_000_000_000)

	tier, expireAt, err := effectiveTierFromRow(
		sql.NullString{String: string(TierPlus), Valid: true},
		sql.NullInt64{Int64: now - 1, Valid: true},
		TierFree,
		now,
	)
	if err != nil {
		t.Fatalf("effective tier failed: %v", err)
	}
	if tier != TierFree {
		t.Fatalf("expired plus should become free, got %s", tier)
	}
	if expireAt != nil {
		t.Fatalf("expired tier should not expose active expireAt, got %v", *expireAt)
	}
}

func TestEffectiveTierFromRowTreatsPaidTierWithoutExpiryAsFree(t *testing.T) {
	now := int64(1_700_000_000_000)

	tier, expireAt, err := effectiveTierFromRow(
		sql.NullString{String: string(TierPlus), Valid: true},
		sql.NullInt64{},
		TierFree,
		now,
	)
	if err != nil {
		t.Fatalf("effective tier failed: %v", err)
	}
	if tier != TierFree {
		t.Fatalf("paid tier without expireAt should become free, got %s", tier)
	}
	if expireAt != nil {
		t.Fatalf("paid tier without expireAt should not expose active expireAt, got %v", *expireAt)
	}
}

func TestEffectiveTierFromRowKeepsActivePaidTier(t *testing.T) {
	now := int64(1_700_000_000_000)

	tier, expireAt, err := effectiveTierFromRow(
		sql.NullString{String: string(TierPro), Valid: true},
		sql.NullInt64{Int64: now + 1, Valid: true},
		TierFree,
		now,
	)
	if err != nil {
		t.Fatalf("effective tier failed: %v", err)
	}
	if tier != TierPro {
		t.Fatalf("active pro should stay pro, got %s", tier)
	}
	if expireAt == nil || *expireAt != now+1 {
		t.Fatalf("active tier expireAt mismatch: %v", expireAt)
	}
}

func TestGetTodayKeyCNUsesShanghaiMidnight(t *testing.T) {
	shanghai := time.FixedZone("Asia/Shanghai", 8*60*60)
	beforeMidnightUTC := time.Date(2026, 5, 4, 15, 59, 59, 0, time.UTC)
	afterMidnightUTC := time.Date(2026, 5, 4, 16, 0, 0, 0, time.UTC)

	if got := GetTodayKeyCN(shanghai, beforeMidnightUTC); got != "20260504" {
		t.Fatalf("before Shanghai midnight mismatch: got %s", got)
	}
	if got := GetTodayKeyCN(shanghai, afterMidnightUTC); got != "20260505" {
		t.Fatalf("after Shanghai midnight mismatch: got %s", got)
	}
}

func TestTopupPackStatusAfterConsumeUsesRemainingBeforeConsume(t *testing.T) {
	if got := topupPackStatusAfterConsume(2); got != "active" {
		t.Fatalf("2 remaining should stay active after one consume, got %s", got)
	}
	if got := topupPackStatusAfterConsume(1); got != "used_up" {
		t.Fatalf("1 remaining should become used_up after one consume, got %s", got)
	}
}

func TestCountPendingQuotaConsumeOutboxCountsActiveRows(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	mock.ExpectQuery(regexp.QuoteMeta("SELECT COUNT(*) FROM quota_consume_outbox WHERE status IN ('pending','failed','needs_ops')")).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(int64(3)))

	count, err := store.CountPendingQuotaConsumeOutbox(context.Background())
	if err != nil {
		t.Fatalf("CountPendingQuotaConsumeOutbox failed: %v", err)
	}
	if count != 3 {
		t.Fatalf("pending count = %d, want 3", count)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestUpdateQuotaConsumeOutboxAdminStatusRetryMakesRowPending(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	nowMs := int64(1_800_000_000_000)
	mock.ExpectExec("UPDATE quota_consume_outbox").
		WithArgs("owner retry", nowMs, int64(42)).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectQuery("SELECT id, user_id, client_msg_id, day_cn, tier_at_completion, completion_at,").
		WithArgs(int64(42)).
		WillReturnRows(quotaOutboxAdminRows().AddRow(
			int64(42),
			"acct_quota_retry",
			"cm_retry",
			"20260619",
			"plus",
			nowMs-1000,
			"pending",
			0,
			nil,
			int64(0),
			nil,
			nowMs-2000,
			nowMs,
		))

	entry, updated, err := store.UpdateQuotaConsumeOutboxAdminStatus(context.Background(), 42, "pending", "owner retry", nowMs)
	if err != nil {
		t.Fatalf("UpdateQuotaConsumeOutboxAdminStatus failed: %v", err)
	}
	if !updated {
		t.Fatal("expected row to be updated")
	}
	if entry.Status != "pending" || entry.Attempts != 0 || entry.UserID != "acct_quota_retry" {
		t.Fatalf("entry mismatch: %#v", entry)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestUpdateQuotaConsumeOutboxAdminStatusWaivesRowTerminal(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	nowMs := int64(1_800_000_001_000)
	mock.ExpectExec("UPDATE quota_consume_outbox").
		WithArgs("waived", "owner waived after manual review", nowMs, nowMs, int64(43)).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectQuery("SELECT id, user_id, client_msg_id, day_cn, tier_at_completion, completion_at,").
		WithArgs(int64(43)).
		WillReturnRows(quotaOutboxAdminRows().AddRow(
			int64(43),
			"acct_quota_waive",
			"cm_waive",
			"20260619",
			"free",
			nowMs-1000,
			"waived",
			12,
			"owner waived after manual review",
			int64(0),
			nowMs,
			nowMs-2000,
			nowMs,
		))

	entry, updated, err := store.UpdateQuotaConsumeOutboxAdminStatus(context.Background(), 43, "waived", "owner waived after manual review", nowMs)
	if err != nil {
		t.Fatalf("UpdateQuotaConsumeOutboxAdminStatus failed: %v", err)
	}
	if !updated {
		t.Fatal("expected row to be updated")
	}
	if entry.Status != "waived" || entry.RepairedAt != nowMs || entry.LastError == "" {
		t.Fatalf("entry mismatch: %#v", entry)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestMarkQuotaConsumeOutboxNeedsOpsSchedulesSlowRetry(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	nowMs := int64(1_800_000_002_000)
	nextAttemptAt := nowMs + int64(6*time.Hour/time.Millisecond)
	mock.ExpectExec("UPDATE quota_consume_outbox").
		WithArgs("database still unavailable", nextAttemptAt, nowMs, "acct_quota_needs_ops", "cm_needs_ops").
		WillReturnResult(sqlmock.NewResult(0, 1))

	if err := store.MarkQuotaConsumeOutboxNeedsOps(context.Background(), "acct_quota_needs_ops", "cm_needs_ops", "database still unavailable", nextAttemptAt, nowMs); err != nil {
		t.Fatalf("MarkQuotaConsumeOutboxNeedsOps failed: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestMarkQuotaConsumeOutboxUncollectableClosesBusinessMismatch(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	nowMs := int64(1_800_000_002_500)
	mock.ExpectExec("UPDATE quota_consume_outbox").
		WithArgs("QUOTA_EXHAUSTED", nowMs, nowMs, int64(45)).
		WillReturnResult(sqlmock.NewResult(0, 1))

	if err := store.MarkQuotaConsumeOutboxUncollectable(context.Background(), 45, "QUOTA_EXHAUSTED", nowMs); err != nil {
		t.Fatalf("MarkQuotaConsumeOutboxUncollectable failed: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestMarkQuotaConsumeOutboxUncollectableAfterAttemptRecordsFinalAttempt(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	nowMs := int64(1_800_000_002_700)
	mock.ExpectExec("UPDATE quota_consume_outbox").
		WithArgs(40, 40, "database still unavailable", nowMs, nowMs, int64(46)).
		WillReturnResult(sqlmock.NewResult(0, 1))

	if err := store.MarkQuotaConsumeOutboxUncollectableAfterAttempt(context.Background(), 46, "database still unavailable", 40, nowMs); err != nil {
		t.Fatalf("MarkQuotaConsumeOutboxUncollectableAfterAttempt failed: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestListDueQuotaConsumeOutboxIncludesNeedsOpsForAutomaticRetry(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	nowMs := int64(1_800_000_003_000)
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT id, user_id, client_msg_id, day_cn, tier_at_completion, completion_at, attempts, created_at
		 FROM quota_consume_outbox
		 WHERE (status IN ('pending','failed') AND next_attempt_at <= ?)
		    OR (status = 'needs_ops' AND next_attempt_at <= ?)
		 ORDER BY id ASC
		 LIMIT ?`)).
		WithArgs(nowMs, nowMs, 20).
		WillReturnRows(sqlmock.NewRows([]string{"id", "user_id", "client_msg_id", "day_cn", "tier_at_completion", "completion_at", "attempts", "created_at"}).
			AddRow(int64(44), "acct_quota_needs_ops", "cm_needs_ops", "20260619", "pro", nowMs-1000, 12, nowMs-int64(24*time.Hour/time.Millisecond)))

	jobs, err := store.ListDueQuotaConsumeOutbox(context.Background(), 20, nowMs)
	if err != nil {
		t.Fatalf("ListDueQuotaConsumeOutbox failed: %v", err)
	}
	if len(jobs) != 1 || jobs[0].ClientMsgID != "cm_needs_ops" || jobs[0].Attempts != 12 || jobs[0].Tier != TierPro || jobs[0].CreatedAt == 0 {
		t.Fatalf("jobs mismatch: %#v", jobs)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestQuotaConsumeShouldAutoTerminalAfterLongRetryWindow(t *testing.T) {
	nowMs := int64(1_800_000_004_000)
	oldJob := QuotaConsumeOutboxJob{
		ID:        45,
		UserID:    "acct_quota_old",
		Attempts:  quotaConsumeRepairNeedsOpsAttempts,
		CreatedAt: nowMs - int64(defaultQuotaConsumeAutoTerminalAge/time.Millisecond) - 1,
	}
	if !quotaConsumeShouldAutoTerminal(oldJob, quotaConsumeRepairNeedsOpsAttempts, nowMs) {
		t.Fatal("expected old needs_ops quota outbox job to auto terminal")
	}
	freshJob := oldJob
	freshJob.CreatedAt = nowMs - int64(time.Hour/time.Millisecond)
	if quotaConsumeShouldAutoTerminal(freshJob, quotaConsumeRepairNeedsOpsAttempts, nowMs) {
		t.Fatal("fresh needs_ops quota outbox job should keep retrying")
	}
	if !quotaConsumeShouldAutoTerminal(freshJob, quotaConsumeRepairTerminalAttempts, nowMs) {
		t.Fatal("high-attempt quota outbox job should auto terminal")
	}
}

func TestClaimDueQuotaConsumeOutboxForRepairClaimsActionableDueRow(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	nowMs := int64(1_800_000_003_500)
	leaseUntilMs := nowMs + int64(10*time.Minute/time.Millisecond)
	mock.ExpectExec("UPDATE quota_consume_outbox").
		WithArgs(leaseUntilMs, nowMs, int64(46), nowMs, nowMs).
		WillReturnResult(sqlmock.NewResult(0, 1))

	claimed, err := store.ClaimDueQuotaConsumeOutboxForRepair(context.Background(), 46, nowMs, leaseUntilMs)
	if err != nil {
		t.Fatalf("ClaimDueQuotaConsumeOutboxForRepair failed: %v", err)
	}
	if !claimed {
		t.Fatal("expected due actionable row to be claimed")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestClaimDueQuotaConsumeOutboxForRepairSkipsTerminalOrAlreadyClaimedRow(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	nowMs := int64(1_800_000_003_600)
	leaseUntilMs := nowMs + int64(10*time.Minute/time.Millisecond)
	mock.ExpectExec("UPDATE quota_consume_outbox").
		WithArgs(leaseUntilMs, nowMs, int64(47), nowMs, nowMs).
		WillReturnResult(sqlmock.NewResult(0, 0))

	claimed, err := store.ClaimDueQuotaConsumeOutboxForRepair(context.Background(), 47, nowMs, leaseUntilMs)
	if err != nil {
		t.Fatalf("ClaimDueQuotaConsumeOutboxForRepair failed: %v", err)
	}
	if claimed {
		t.Fatal("terminal or already claimed row must not be claimed")
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestQuotaConsumeShouldAutoMarkUncollectableOnlyForQuotaExhausted(t *testing.T) {
	if !quotaConsumeShouldAutoMarkUncollectable(errors.New("QUOTA_EXHAUSTED")) {
		t.Fatal("quota exhausted should be terminally closed instead of charging future quota")
	}
	if quotaConsumeShouldAutoMarkUncollectable(errors.New("lock wait timeout exceeded")) {
		t.Fatal("temporary database errors should keep slow automatic retry")
	}
}

func quotaOutboxAdminRows() *sqlmock.Rows {
	return sqlmock.NewRows([]string{
		"id",
		"user_id",
		"client_msg_id",
		"day_cn",
		"tier_at_completion",
		"completion_at",
		"status",
		"attempts",
		"last_error",
		"next_attempt_at",
		"repaired_at",
		"created_at",
		"updated_at",
	})
}

func TestConsumeOnDoneAtDoesNotUseBenefitsCreatedAfterCompletion(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	completionAt := int64(1_800_000_000_000)
	userID := "acct_completion_time"
	clientMsgID := "cm_completion_time"
	dayCN := "20260617"

	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta("SELECT id FROM quota_ledger WHERE user_id = ? AND client_msg_id = ? LIMIT 1 FOR UPDATE")).
		WithArgs(userID, clientMsgID).
		WillReturnRows(sqlmock.NewRows([]string{"id"}))
	mock.ExpectExec("INSERT INTO daily_usage").
		WithArgs(userID, dayCN).
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectQuery(regexp.QuoteMeta("SELECT used FROM daily_usage WHERE user_id = ? AND day_cn = ? LIMIT 1 FOR UPDATE")).
		WithArgs(userID, dayCN).
		WillReturnRows(sqlmock.NewRows([]string{"used"}).AddRow(tierLimits[TierFree]))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT remaining
		 FROM upgrade_credits
		 WHERE user_id = ? AND remaining > 0 AND updated_at <= ? AND (expire_at IS NULL OR expire_at > ?)
		 LIMIT 1
		 FOR UPDATE`)).
		WithArgs(userID, completionAt, completionAt).
		WillReturnRows(sqlmock.NewRows([]string{"remaining"}))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT pack_id, remaining
		 FROM topup_packs
		 WHERE user_id = ? AND status = 'active' AND remaining > 0 AND created_at <= ? AND (expire_at IS NULL OR expire_at > ?)
		 ORDER BY CASE WHEN expire_at IS NULL THEN 1 ELSE 0 END ASC, expire_at ASC, created_at ASC
		 LIMIT 1
		 FOR UPDATE`)).
		WithArgs(userID, completionAt, completionAt).
		WillReturnRows(sqlmock.NewRows([]string{"pack_id", "remaining"}))
	mock.ExpectRollback()

	_, err := store.consumeOnDoneAt(context.Background(), userID, TierFree, clientMsgID, dayCN, completionAt)
	if err == nil || err.Error() != "QUOTA_EXHAUSTED" {
		t.Fatalf("consumeOnDoneAt err = %v, want QUOTA_EXHAUSTED", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestReadOrderReplayReturnsConflictForOtherUserOrderID(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	mock.ExpectBegin()
	tx, err := store.db.BeginTx(context.Background(), nil)
	if err != nil {
		t.Fatalf("BeginTx failed: %v", err)
	}

	mock.ExpectQuery(regexp.QuoteMeta("SELECT user_id, type, result_json FROM orders WHERE order_id = ? LIMIT 1 FOR UPDATE")).
		WithArgs("order_shared").
		WillReturnRows(sqlmock.NewRows([]string{"user_id", "type", "result_json"}).AddRow("acct_other", "renew_plus", `{"tier":"plus","tier_expire_at":1700000000000}`))

	replay, payload, err := store.readOrderReplay(context.Background(), tx, "order_shared", "acct_current", "renew_plus")
	if !errors.Is(err, ErrOrderIDConflict) {
		t.Fatalf("readOrderReplay err = %v, want ErrOrderIDConflict", err)
	}
	if replay {
		t.Fatal("cross-user order should not be treated as replay")
	}
	if payload != nil {
		t.Fatalf("cross-user conflict payload = %#v, want nil", payload)
	}
	mock.ExpectRollback()
	if err := tx.Rollback(); err != nil {
		t.Fatalf("rollback failed: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestReadOrderReplayReturnsConflictForDifferentProductType(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	mock.ExpectBegin()
	tx, err := store.db.BeginTx(context.Background(), nil)
	if err != nil {
		t.Fatalf("BeginTx failed: %v", err)
	}

	mock.ExpectQuery(regexp.QuoteMeta("SELECT user_id, type, result_json FROM orders WHERE order_id = ? LIMIT 1 FOR UPDATE")).
		WithArgs("order_reused").
		WillReturnRows(sqlmock.NewRows([]string{"user_id", "type", "result_json"}).AddRow("acct_current", "renew_plus", `{"tier":"plus","tier_expire_at":1700000000000}`))

	replay, payload, err := store.readOrderReplay(context.Background(), tx, "order_reused", "acct_current", "buy_topup")
	if !errors.Is(err, ErrOrderIDConflict) {
		t.Fatalf("readOrderReplay err = %v, want ErrOrderIDConflict", err)
	}
	if replay {
		t.Fatal("cross-product order should not be treated as replay")
	}
	if payload != nil {
		t.Fatalf("cross-product conflict payload = %#v, want nil", payload)
	}
	mock.ExpectRollback()
	if err := tx.Rollback(); err != nil {
		t.Fatalf("rollback failed: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

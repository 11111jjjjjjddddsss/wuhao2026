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

func TestReadOrderReplayReturnsConflictForOtherUserOrderID(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	mock.ExpectBegin()
	tx, err := store.db.BeginTx(context.Background(), nil)
	if err != nil {
		t.Fatalf("BeginTx failed: %v", err)
	}

	mock.ExpectQuery(regexp.QuoteMeta("SELECT user_id, result_json FROM orders WHERE order_id = ? LIMIT 1 FOR UPDATE")).
		WithArgs("order_shared").
		WillReturnRows(sqlmock.NewRows([]string{"user_id", "result_json"}).AddRow("acct_other", `{"tier":"plus","tier_expire_at":1700000000000}`))

	replay, payload, err := store.readOrderReplay(context.Background(), tx, "order_shared", "acct_current")
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

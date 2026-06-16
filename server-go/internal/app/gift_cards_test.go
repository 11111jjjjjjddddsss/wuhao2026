package app

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"regexp"
	"strings"
	"testing"
	"time"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestGiftCardCodeCipherRoundTrip(t *testing.T) {
	code := "NQ-M7AB-CD23-EF45-GH67"
	ciphertext, err := encryptGiftCardCodeWithSecret(code, "unit-test-secret")
	if err != nil {
		t.Fatalf("encryptGiftCardCodeWithSecret failed: %v", err)
	}
	if ciphertext == "" || !strings.HasPrefix(ciphertext, "v1:") {
		t.Fatalf("ciphertext = %q, want v1 payload", ciphertext)
	}
	if strings.Contains(ciphertext, strings.TrimSpace(code)) {
		t.Fatalf("ciphertext leaked gift card code: %q", ciphertext)
	}

	plain, err := decryptGiftCardCodeWithSecret(ciphertext, "unit-test-secret")
	if err != nil {
		t.Fatalf("decryptGiftCardCodeWithSecret failed: %v", err)
	}
	if plain != strings.TrimSpace(code) {
		t.Fatalf("plain = %q, want code %q", plain, strings.TrimSpace(code))
	}

	if _, err := decryptGiftCardCodeWithSecret(ciphertext, "wrong-secret"); err == nil {
		t.Fatalf("decrypting gift card code with wrong secret should fail")
	}
}

func TestGiftCardCodeCipherRequiresSecret(t *testing.T) {
	if _, err := encryptGiftCardCodeWithSecret("NQ-M7AB-CD23-EF45-GH67", ""); !errors.Is(err, errGiftCardSecretMissing) {
		t.Fatalf("encrypt missing secret err = %v, want errGiftCardSecretMissing", err)
	}
	if _, err := decryptGiftCardCodeWithSecret("v1:abc", ""); !errors.Is(err, errGiftCardSecretMissing) {
		t.Fatalf("decrypt missing secret err = %v, want errGiftCardSecretMissing", err)
	}
}

func TestGiftCardTextLooksSensitiveRejectsSeparatedPhonesAndSecrets(t *testing.T) {
	for _, text := range []string{
		"请联系 138-0013-8000 处理",
		"手机号 138 0013 8000",
		"token=secret-value",
		"NQ-M7AB-CD23-EF45-GH67",
	} {
		if !giftCardTextLooksSensitive(text) {
			t.Fatalf("expected sensitive text to be rejected: %q", text)
		}
	}
}

func TestGiftCardTextLooksSensitiveAllowsOperationalNotes(t *testing.T) {
	for _, text := range []string{
		"电话已沟通，等待用户确认",
		"客服已线下核验会员和订单",
		"用户申请注销，礼品卡权益已核对",
	} {
		if giftCardTextLooksSensitive(text) {
			t.Fatalf("expected operational note to be allowed: %q", text)
		}
	}
}

func TestNormalizeGiftCardBatchInputUsesImmediateValidFrom(t *testing.T) {
	now := time.Date(2026, 6, 16, 10, 30, 0, 0, time.UTC)
	input, reason := normalizeGiftCardBatchInput(adminGiftCardCreateBatchRequest{
		Name:         "代理测试卡",
		Tier:         "plus",
		DurationDays: 30,
		Quantity:     1,
	}, "owner", now)
	if reason != "" {
		t.Fatalf("normalizeGiftCardBatchInput reason = %q, want empty", reason)
	}
	if input.ValidFrom != now.UnixMilli() {
		t.Fatalf("valid_from = %d, want now %d", input.ValidFrom, now.UnixMilli())
	}
	if input.ValidUntil == nil || *input.ValidUntil <= input.ValidFrom {
		t.Fatalf("valid_until = %v, want after valid_from %d", input.ValidUntil, input.ValidFrom)
	}
}

func TestScanGiftCardEntryOnlyDecryptsWhenAllowed(t *testing.T) {
	t.Setenv("APP_SECRET", "unit-test-secret")
	code := "NQ-M7AB-CD23-EF45-GH67"
	ciphertext, err := encryptGiftCardCode(code)
	if err != nil {
		t.Fatalf("encryptGiftCardCode failed: %v", err)
	}

	hidden, err := scanGiftCardEntry(giftCardScannerFixture(ciphertext), false)
	if err != nil {
		t.Fatalf("scan hidden gift card failed: %v", err)
	}
	if hidden.Code != "" {
		t.Fatalf("hidden gift card code = %q, want empty", hidden.Code)
	}

	visible, err := scanGiftCardEntry(giftCardScannerFixture(ciphertext), true)
	if err != nil {
		t.Fatalf("scan visible gift card failed: %v", err)
	}
	if visible.Code != code {
		t.Fatalf("visible gift card code = %q, want %q", visible.Code, code)
	}
}

func TestRedeemGiftCardSameUserReplayDoesNotExtendEntitlement(t *testing.T) {
	t.Setenv("APP_SECRET", "unit-test-secret")
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	code := "NQ-M7AB-CD23-EF45-GH67"
	userID := "acct_gift_replay"
	redeemedAt := int64(1_700_000_001_000)
	expireAt := int64(1_702_592_001_000)
	nowMs := redeemedAt + 10_000
	region := RegionContext{
		Region:      "河南省 周口市",
		Source:      RegionSourceGPS,
		Reliability: RegionReliable,
	}

	mock.ExpectBegin()
	mock.ExpectQuery(giftCardForUpdateQueryPattern()).
		WithArgs(giftCardCodeHash(code)).
		WillReturnRows(giftCardSQLRows().AddRow(
			"gcc_replay",
			"gcb_replay",
			"NQ-M7AB-****-GH67",
			"GH67",
			nil,
			string(TierPlus),
			30,
			"redeemed",
			int64(1_700_000_000_000),
			nil,
			"owner",
			nil,
			userID,
			"138****8000",
			"河南省 周口市",
			string(RegionSourceGPS),
			string(RegionReliable),
			redeemedAt,
			expireAt,
			nil,
			int64(1_700_000_000_000),
			redeemedAt,
		))
	mock.ExpectExec("INSERT INTO gift_card_redemption_attempts").
		WithArgs(sqlmock.AnyArg(), userID, 1, nil, "1.2.*.*", region.Region, string(region.Source), string(region.Reliability), nowMs).
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectCommit()

	result, err := store.RedeemGiftCard(context.Background(), code, userID, region, "1.2.*.*", nowMs)
	if err != nil {
		t.Fatalf("RedeemGiftCard replay failed: %v", err)
	}
	if !result.OK || result.CardID != "gcc_replay" || result.MembershipExpireAt != expireAt || result.RedeemedAt != redeemedAt {
		t.Fatalf("replay result mismatch: %#v", result)
	}
	if !result.Replay {
		t.Fatal("same-user replay should be marked as replay")
	}
	if result.AppliedTier != TierPlus {
		t.Fatalf("replay applied tier = %s, want plus", result.AppliedTier)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestRedeemGiftCardRequiresSingleActiveStatusUpdate(t *testing.T) {
	t.Setenv("APP_SECRET", "unit-test-secret")
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	code := "NQ-M7AB-CD23-EF45-GH67"
	userID := "acct_gift_conflict"
	nowMs := int64(1_700_000_010_000)
	region := RegionContext{
		Region:      "河南省 周口市",
		Source:      RegionSourceGPS,
		Reliability: RegionReliable,
	}

	mock.ExpectBegin()
	mock.ExpectQuery(giftCardForUpdateQueryPattern()).
		WithArgs(giftCardCodeHash(code)).
		WillReturnRows(giftCardSQLRows().AddRow(
			"gcc_conflict",
			"gcb_conflict",
			"NQ-M7AB-****-GH67",
			"GH67",
			nil,
			string(TierPlus),
			30,
			"active",
			int64(1_700_000_000_000),
			nil,
			"owner",
			nil,
			nil,
			nil,
			nil,
			nil,
			nil,
			nil,
			nil,
			nil,
			int64(1_700_000_000_000),
			int64(1_700_000_000_000),
		))
	mock.ExpectQuery(regexp.QuoteMeta("SELECT phone_mask FROM app_accounts WHERE user_id = ? LIMIT 1")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"phone_mask"}).AddRow("138****8000"))
	mock.ExpectQuery(regexp.QuoteMeta("SELECT tier, tier_expire_at FROM user_entitlement WHERE user_id = ? LIMIT 1 FOR UPDATE")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"tier", "tier_expire_at"}))
	mock.ExpectExec("INSERT INTO user_entitlement").
		WithArgs(userID, string(TierPlus), sqlmock.AnyArg(), nowMs).
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectExec("UPDATE gift_cards").
		WithArgs(userID, "138****8000", region.Region, string(region.Source), string(region.Reliability), nowMs, sqlmock.AnyArg(), nowMs, "gcc_conflict").
		WillReturnResult(sqlmock.NewResult(0, 0))
	mock.ExpectRollback()

	_, err := store.RedeemGiftCard(context.Background(), code, userID, region, "1.2.*.*", nowMs)
	if !errors.Is(err, errGiftCardInactive) {
		t.Fatalf("RedeemGiftCard update conflict err = %v, want errGiftCardInactive", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestApplyGiftCardTierPlusToProCreatesUpgradeCredit(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	userID := "acct_gift_upgrade"
	shanghai := time.FixedZone("Asia/Shanghai", 8*60*60)
	store.shanghai = shanghai
	now := time.Date(2026, 6, 16, 10, 0, 0, 0, shanghai)
	nowMs := now.UnixMilli()
	currentExpireAt := now.AddDate(0, 0, 2).UnixMilli()
	newExpireAt := now.AddDate(0, 0, 30).UnixMilli()
	dayCN := GetTodayKeyCN(shanghai, time.Now())

	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta("SELECT tier, tier_expire_at FROM user_entitlement WHERE user_id = ? LIMIT 1 FOR UPDATE")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"tier", "tier_expire_at"}).AddRow(string(TierPlus), currentExpireAt))
	mock.ExpectExec("INSERT INTO daily_usage").
		WithArgs(userID, dayCN).
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectQuery(regexp.QuoteMeta("SELECT used FROM daily_usage WHERE user_id = ? AND day_cn = ? LIMIT 1 FOR UPDATE")).
		WithArgs(userID, dayCN).
		WillReturnRows(sqlmock.NewRows([]string{"used"}).AddRow(7))
	mock.ExpectExec("INSERT INTO upgrade_credits").
		WithArgs(userID, 68, nil, nowMs).
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectExec("INSERT INTO user_entitlement").
		WithArgs(userID, string(TierPro), newExpireAt, nowMs).
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectCommit()

	tx, err := store.db.BeginTx(context.Background(), nil)
	if err != nil {
		t.Fatalf("begin tx: %v", err)
	}
	appliedTier, membershipExpireAt, err := store.applyGiftCardTierTx(context.Background(), tx, userID, TierPro, 30, nowMs)
	if err != nil {
		t.Fatalf("applyGiftCardTierTx plus->pro failed: %v", err)
	}
	if err := tx.Commit(); err != nil {
		t.Fatalf("commit tx: %v", err)
	}
	if appliedTier != TierPro {
		t.Fatalf("applied tier = %s, want pro", appliedTier)
	}
	if membershipExpireAt != newExpireAt {
		t.Fatalf("membership expire = %d, want %d", membershipExpireAt, newExpireAt)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

func TestApplyGiftCardTierRejectsLowerTierCard(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	userID := "acct_gift_lower_tier"
	nowMs := int64(1_700_000_010_000)
	currentExpireAt := nowMs + int64(30*24*time.Hour/time.Millisecond)

	mock.ExpectBegin()
	mock.ExpectQuery(regexp.QuoteMeta("SELECT tier, tier_expire_at FROM user_entitlement WHERE user_id = ? LIMIT 1 FOR UPDATE")).
		WithArgs(userID).
		WillReturnRows(sqlmock.NewRows([]string{"tier", "tier_expire_at"}).AddRow(string(TierPro), currentExpireAt))
	mock.ExpectRollback()

	tx, err := store.db.BeginTx(context.Background(), nil)
	if err != nil {
		t.Fatalf("begin tx: %v", err)
	}
	_, _, err = store.applyGiftCardTierTx(context.Background(), tx, userID, TierPlus, 30, nowMs)
	if !errors.Is(err, errGiftCardLowerTier) {
		t.Fatalf("apply lower-tier gift card err = %v, want errGiftCardLowerTier", err)
	}
	if rollbackErr := tx.Rollback(); rollbackErr != nil {
		t.Fatalf("rollback tx: %v", rollbackErr)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet SQL expectations: %v", err)
	}
}

type giftCardTestScanner []any

func (s giftCardTestScanner) Scan(dest ...any) error {
	if len(dest) != len(s) {
		return fmt.Errorf("scan destination count = %d, want %d", len(dest), len(s))
	}
	for idx, value := range s {
		switch target := dest[idx].(type) {
		case *string:
			*target = value.(string)
		case *int:
			*target = value.(int)
		case *int64:
			*target = value.(int64)
		case *sql.NullString:
			*target = value.(sql.NullString)
		case *sql.NullInt64:
			*target = value.(sql.NullInt64)
		default:
			return fmt.Errorf("unsupported scan target %T", dest[idx])
		}
	}
	return nil
}

func giftCardScannerFixture(ciphertext string) giftCardTestScanner {
	return giftCardTestScanner{
		"gcc_test",
		"gcb_test",
		"NQ-M7AB-****-GH67",
		"GH67",
		sql.NullString{String: ciphertext, Valid: true},
		string(TierPlus),
		30,
		"active",
		int64(1700000000000),
		sql.NullInt64{},
		"owner",
		sql.NullString{},
		sql.NullString{},
		sql.NullString{},
		sql.NullString{},
		sql.NullString{},
		sql.NullString{},
		sql.NullInt64{},
		sql.NullInt64{},
		sql.NullInt64{},
		int64(1700000000000),
		int64(1700000000000),
	}
}

func newGiftCardSQLMock(t *testing.T) (*Store, sqlmock.Sqlmock, func()) {
	t.Helper()
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	return &Store{db: db}, mock, func() {
		mock.ExpectClose()
		if err := db.Close(); err != nil {
			t.Fatalf("close sqlmock db: %v", err)
		}
	}
}

func giftCardForUpdateQueryPattern() string {
	return `(?s)SELECT card_id, batch_id, code_mask, code_suffix, NULL AS code_ciphertext, tier, duration_days, status, valid_from, valid_until, created_by, note, redeemed_user_id, redeemed_phone_mask, redeemed_region, redeemed_region_source, redeemed_region_reliability, redeemed_at, membership_expire_at, voided_at, created_at, updated_at\s+FROM gift_cards\s+WHERE code_hash = \? LIMIT 1 FOR UPDATE`
}

func giftCardSQLRows() *sqlmock.Rows {
	return sqlmock.NewRows([]string{
		"card_id",
		"batch_id",
		"code_mask",
		"code_suffix",
		"code_ciphertext",
		"tier",
		"duration_days",
		"status",
		"valid_from",
		"valid_until",
		"created_by",
		"note",
		"redeemed_user_id",
		"redeemed_phone_mask",
		"redeemed_region",
		"redeemed_region_source",
		"redeemed_region_reliability",
		"redeemed_at",
		"membership_expire_at",
		"voided_at",
		"created_at",
		"updated_at",
	})
}

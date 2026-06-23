package app

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"database/sql"
	"encoding/base64"
	"fmt"
	"net/url"
	"strings"
	"testing"
	"time"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestPaymentAmountCentsParsing(t *testing.T) {
	cases := map[string]int{
		"6":     600,
		"6.0":   600,
		"6.00":  600,
		"19.90": 1990,
		"29.9":  2990,
	}
	for raw, want := range cases {
		got, err := parseAmountCents(raw)
		if err != nil {
			t.Fatalf("parseAmountCents(%q) error: %v", raw, err)
		}
		if got != want {
			t.Fatalf("parseAmountCents(%q)=%d want %d", raw, got, want)
		}
	}
	if _, err := parseAmountCents("6.001"); err == nil {
		t.Fatal("parseAmountCents should reject sub-cent precision")
	}
}

func TestAlipayBuildAppPayOrderSignatureVerifies(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	client := &AlipayClient{
		appID:      "2021006162639387",
		sellerID:   "2088000000000001",
		notifyURL:  "https://api.nongjiqiancha.cn/api/payments/alipay/notify",
		privateKey: key,
		publicKey:  &key.PublicKey,
		loc:        time.FixedZone("Asia/Shanghai", 8*60*60),
	}
	product, ok := paymentProductByType("topup_80")
	if !ok {
		t.Fatal("topup product missing")
	}
	orderString, err := client.BuildAppPayOrder("NJ20260623120000ABCDEF", product, time.Date(2026, 6, 23, 12, 0, 0, 0, client.loc))
	if err != nil {
		t.Fatalf("BuildAppPayOrder error: %v", err)
	}
	values, err := url.ParseQuery(orderString)
	if err != nil {
		t.Fatalf("ParseQuery orderString: %v", err)
	}
	if values.Get("app_id") != client.appID {
		t.Fatalf("app_id=%q", values.Get("app_id"))
	}
	if values.Get("sign") == "" {
		t.Fatal("sign is empty")
	}
	signContent := alipaySignContent(values, "sign", "sign_type")
	if strings.Contains(signContent, "sign_type=") {
		t.Fatalf("order sign content should exclude sign_type: %s", signContent)
	}
	if err := verifyRSA2(signContent, values.Get("sign"), &key.PublicKey); err != nil {
		t.Fatalf("verify order signature: %v", err)
	}
}

func TestAlipayNotifyVerify(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	publicDER, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if err != nil {
		t.Fatalf("marshal public key: %v", err)
	}
	client := &AlipayClient{
		appID:      "2021006162639387",
		sellerID:   "2088000000000001",
		notifyURL:  "https://api.nongjiqiancha.cn/api/payments/alipay/notify",
		privateKey: key,
		publicKey:  mustParseTestRSAPublicKey(t, base64.StdEncoding.EncodeToString(publicDER)),
	}
	values := url.Values{}
	values.Set("app_id", client.appID)
	values.Set("notify_id", "2026062300000001")
	values.Set("out_trade_no", "NJ20260623120000ABCDEF")
	values.Set("trade_no", "20260623220000000001")
	values.Set("buyer_id", "2088000000000000")
	values.Set("seller_id", "2088000000000001")
	values.Set("trade_status", "TRADE_SUCCESS")
	values.Set("total_amount", "29.90")
	values.Set("sign_type", "RSA2")
	signContent := alipaySignContent(values, "sign", "sign_type")
	if strings.Contains(signContent, "sign_type=") {
		t.Fatalf("notify sign content should exclude sign_type: %s", signContent)
	}
	signature, err := signRSA2(signContent, key)
	if err != nil {
		t.Fatalf("sign notify: %v", err)
	}
	values.Set("sign", signature)
	payload, err := client.VerifyNotify(values)
	if err != nil {
		t.Fatalf("VerifyNotify error: %v", err)
	}
	if payload.OutTradeNo != "NJ20260623120000ABCDEF" || payload.TotalAmountCents != 2990 {
		t.Fatalf("payload mismatch: %+v", payload)
	}
	values.Set("total_amount", "29.91")
	if _, err := client.VerifyNotify(values); err == nil {
		t.Fatal("VerifyNotify should reject tampered amount")
	}
}

func TestGrantPaidPaymentOrderKeepsAlipayRetryWhenAlreadyProcessing(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	defer db.Close()

	store := &Store{db: db}
	outTradeNo := "NJ20260623120000ABCDEF"
	nowMs := int64(1782200000000)

	mock.ExpectExec("UPDATE payment_orders").
		WithArgs(
			paymentGrantProcessing,
			nowMs,
			nowMs,
			outTradeNo,
			paymentStatusPaid,
			paymentGrantPending,
			paymentGrantFailed,
			paymentGrantProcessing,
			sqlmock.AnyArg(),
		).
		WillReturnResult(sqlmock.NewResult(0, 0))
	mock.ExpectQuery("SELECT out_trade_no, user_id, provider, product_type").
		WithArgs(outTradeNo).
		WillReturnRows(paymentOrderRows().AddRow(
			outTradeNo,
			"acct_payment_user",
			paymentProviderAlipay,
			paymentProductRenewPro,
			2990,
			"CNY",
			"农技千查 Pro 会员30天",
			paymentStatusPaid,
			"20260623220000000001",
			"TRADE_SUCCESS",
			nil,
			paymentGrantProcessing,
			nil,
			nowMs-60000,
			nowMs,
			nowMs-1000,
			nil,
		))

	err = store.grantPaidPaymentOrder(context.Background(), outTradeNo, nowMs)
	if err == nil || !strings.Contains(err.Error(), "PAYMENT_GRANT_IN_PROGRESS") {
		t.Fatalf("grantPaidPaymentOrder error=%v, want PAYMENT_GRANT_IN_PROGRESS", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestGrantPaidPaymentOrderMarksNeedsOpsForBusinessConflict(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	defer db.Close()

	store := &Store{db: db}
	outTradeNo := "NJ20260623120000ABCDEF"
	entitlementOrderID := "pay_" + outTradeNo
	nowMs := int64(1782200000000)

	mock.ExpectExec("UPDATE payment_orders").
		WithArgs(
			paymentGrantProcessing,
			nowMs,
			nowMs,
			outTradeNo,
			paymentStatusPaid,
			paymentGrantPending,
			paymentGrantFailed,
			paymentGrantProcessing,
			sqlmock.AnyArg(),
		).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectQuery("SELECT out_trade_no, user_id, provider, product_type").
		WithArgs(outTradeNo).
		WillReturnRows(paymentOrderRows().AddRow(
			outTradeNo,
			"acct_payment_user",
			paymentProviderAlipay,
			paymentProductUpgradePlusPro,
			2990,
			"CNY",
			"农技千查升级 Pro 会员30天",
			paymentStatusPaid,
			"20260623220000000001",
			"TRADE_SUCCESS",
			nil,
			paymentGrantProcessing,
			nil,
			nowMs-60000,
			nowMs,
			nowMs-1000,
			nil,
		))
	mock.ExpectBegin()
	mock.ExpectQuery("SELECT user_id, type, result_json FROM orders").
		WithArgs(entitlementOrderID).
		WillReturnError(sql.ErrNoRows)
	mock.ExpectQuery("SELECT tier, tier_expire_at FROM user_entitlement").
		WithArgs("acct_payment_user").
		WillReturnRows(sqlmock.NewRows([]string{"tier", "tier_expire_at"}).AddRow(string(TierPro), time.Now().Add(24*time.Hour).UnixMilli()))
	mock.ExpectRollback()
	mock.ExpectExec("UPDATE payment_orders").
		WithArgs(paymentGrantNeedsOps, "ALREADY_PRO", entitlementOrderID, sqlmock.AnyArg(), outTradeNo).
		WillReturnResult(sqlmock.NewResult(0, 1))

	if err := store.grantPaidPaymentOrder(context.Background(), outTradeNo, nowMs); err != nil {
		t.Fatalf("grantPaidPaymentOrder error=%v, want nil needs_ops handling", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestCompleteAlipayPaymentDoesNotDowngradePaidOrderOnClosedNotify(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	defer db.Close()

	store := &Store{db: db}
	outTradeNo := "NJ20260623120000ABCDEF"
	nowMs := int64(1782200000000)
	payload := alipayNotifyPayload{
		AppID:            "2021006162639387",
		NotifyID:         "2026062300000001",
		OutTradeNo:       outTradeNo,
		TradeNo:          "20260623220000009999",
		TradeStatus:      "TRADE_CLOSED",
		TotalAmountCents: 2990,
		RawSummary:       map[string]any{"trade_status": "TRADE_CLOSED"},
	}

	mock.ExpectBegin()
	mock.ExpectQuery("SELECT out_trade_no, user_id, provider, product_type").
		WithArgs(outTradeNo, paymentProviderAlipay).
		WillReturnRows(paymentOrderRows().AddRow(
			outTradeNo,
			"acct_payment_user",
			paymentProviderAlipay,
			paymentProductRenewPro,
			2990,
			"CNY",
			"农技千查 Pro 会员30天",
			paymentStatusPaid,
			"20260623220000000001",
			"TRADE_SUCCESS",
			"pay_"+outTradeNo,
			paymentGrantSuccess,
			nil,
			nowMs-60000,
			nowMs-1000,
			nowMs-1000,
			nowMs-500,
		))
	mock.ExpectExec("UPDATE payment_orders").
		WithArgs(
			sqlmock.AnyArg(),
			nowMs,
			outTradeNo,
		).
		WillReturnResult(sqlmock.NewResult(0, 1))
	mock.ExpectCommit()

	if err := store.CompleteAlipayPayment(context.Background(), payload, nowMs); err != nil {
		t.Fatalf("CompleteAlipayPayment error: %v", err)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestSafePaymentErrorCodeDoesNotExposePaymentIDs(t *testing.T) {
	outTradeNo := "NJ20260623120000ABCDEF"
	tradeNo := "20260623220000000001"
	err := fmt.Errorf("Error 1062: Duplicate entry 'alipay-%s-%s' for key 'uniq_payment_provider_trade'", outTradeNo, tradeNo)

	got := safePaymentErrorCode(err)
	if got != "DB_DUPLICATE_PROVIDER_TRADE" {
		t.Fatalf("safePaymentErrorCode=%q", got)
	}
	if strings.Contains(got, outTradeNo) || strings.Contains(got, tradeNo) {
		t.Fatalf("safePaymentErrorCode leaked payment ids: %q", got)
	}
}

func paymentOrderRows() *sqlmock.Rows {
	return sqlmock.NewRows([]string{
		"out_trade_no",
		"user_id",
		"provider",
		"product_type",
		"amount_cents",
		"currency",
		"subject",
		"status",
		"provider_trade_no",
		"provider_status",
		"entitlement_order_id",
		"grant_status",
		"grant_error",
		"created_at",
		"updated_at",
		"paid_at",
		"granted_at",
	})
}

func mustParseTestRSAPublicKey(t *testing.T, raw string) *rsa.PublicKey {
	t.Helper()
	key, err := parseRSAPublicKey(raw)
	if err != nil {
		t.Fatalf("parse test public key: %v", err)
	}
	return key
}

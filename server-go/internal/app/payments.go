package app

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"sort"
	"strconv"
	"strings"
	"time"
)

const (
	paymentProviderAlipay = "alipay"

	paymentProductRenewPlus      = "renew_plus"
	paymentProductRenewPro       = "renew_pro"
	paymentProductUpgradePlusPro = "upgrade_plus_to_pro"
	paymentProductBuyTopup       = "buy_topup"

	paymentStatusPending = "pending"
	paymentStatusPaid    = "paid"
	paymentStatusClosed  = "closed"

	paymentGrantPending    = "pending"
	paymentGrantProcessing = "processing"
	paymentGrantSuccess    = "success"
	paymentGrantFailed     = "failed"
	paymentGrantNeedsOps   = "needs_ops"

	paymentGrantProcessingRetryAfter      = 5 * time.Minute
	paymentMembershipMinRemainingForOrder = 45 * time.Minute

	alipayPaymentPublicEnabledEnv     = "ALIPAY_PAYMENT_PUBLIC_ENABLED"
	alipayPaymentAllowedUserIDsEnv    = "ALIPAY_PAYMENT_ALLOWED_USER_IDS"
	alipayPaymentAllowedBuildTypesEnv = "ALIPAY_PAYMENT_ALLOWED_BUILD_TYPES"
	alipayPaymentTestAmountCentsEnv   = "ALIPAY_PAYMENT_TEST_AMOUNT_CENTS"
	alipayPaymentMaxTestAmountCents   = 100
)

type paymentProduct struct {
	Type        string
	Subject     string
	Body        string
	AmountCents int
}

type paymentOrder struct {
	OutTradeNo          string `json:"out_trade_no"`
	UserID              string `json:"user_id,omitempty"`
	Provider            string `json:"provider"`
	ProductType         string `json:"product_type"`
	AmountCents         int    `json:"amount_cents"`
	OriginalAmountCents int    `json:"original_amount_cents,omitempty"`
	Currency            string `json:"currency"`
	Subject             string `json:"subject"`
	Status              string `json:"status"`
	ProviderTradeNo     string `json:"provider_trade_no,omitempty"`
	ProviderStatus      string `json:"provider_status,omitempty"`
	EntitlementOrderID  string `json:"entitlement_order_id,omitempty"`
	GrantStatus         string `json:"grant_status"`
	GrantError          string `json:"grant_error,omitempty"`
	IsTestOrder         bool   `json:"is_test_order,omitempty"`
	CreatedAt           int64  `json:"created_at"`
	UpdatedAt           int64  `json:"updated_at"`
	PaidAt              *int64 `json:"paid_at,omitempty"`
	GrantedAt           *int64 `json:"granted_at,omitempty"`
}

type createAlipayOrderRequest struct {
	ProductType       string `json:"product_type"`
	ClientAppVersion  string `json:"client_app_version,omitempty"`
	ClientPlatform    string `json:"client_platform,omitempty"`
	ClientBuildType   string `json:"client_build_type,omitempty"`
	ClientVersionCode int    `json:"client_version_code,omitempty"`
}

type AlipayClient struct {
	appID      string
	sellerID   string
	notifyURL  string
	privateKey *rsa.PrivateKey
	publicKey  *rsa.PublicKey
	loc        *time.Location
}

type alipayBizContent struct {
	OutTradeNo     string `json:"out_trade_no"`
	TotalAmount    string `json:"total_amount"`
	Subject        string `json:"subject"`
	Body           string `json:"body,omitempty"`
	SellerID       string `json:"seller_id,omitempty"`
	ProductCode    string `json:"product_code"`
	TimeoutExpress string `json:"timeout_express,omitempty"`
}

type alipayNotifyPayload struct {
	AppID            string
	AuthAppID        string
	NotifyID         string
	OutTradeNo       string
	TradeNo          string
	BuyerID          string
	SellerID         string
	TradeStatus      string
	TotalAmountCents int
	RawSummary       map[string]any
}

func NewAlipayClientFromEnv(loc *time.Location) (*AlipayClient, error) {
	appID := strings.TrimSpace(firstNonEmpty(os.Getenv("ALIPAY_APP_ID"), os.Getenv("ALIPAY_OPEN_APP_ID")))
	privateRaw, err := readEnvOrFile("ALIPAY_APP_PRIVATE_KEY", "ALIPAY_APP_PRIVATE_KEY_FILE")
	if err != nil {
		return nil, err
	}
	publicRaw, err := readEnvOrFile("ALIPAY_PUBLIC_KEY", "ALIPAY_PUBLIC_KEY_FILE")
	if err != nil {
		return nil, err
	}
	notifyURL := strings.TrimSpace(firstNonEmpty(
		os.Getenv("ALIPAY_NOTIFY_URL"),
		"https://api.nongjiqiancha.cn/api/payments/alipay/notify",
	))
	sellerID := strings.TrimSpace(os.Getenv("ALIPAY_SELLER_ID"))

	if appID == "" && strings.TrimSpace(privateRaw) == "" && strings.TrimSpace(publicRaw) == "" {
		return &AlipayClient{loc: loc}, nil
	}
	if appID == "" || strings.TrimSpace(privateRaw) == "" || strings.TrimSpace(publicRaw) == "" || sellerID == "" {
		return nil, fmt.Errorf("alipay config incomplete")
	}
	privateKey, err := parseRSAPrivateKey(privateRaw)
	if err != nil {
		return nil, fmt.Errorf("parse alipay app private key: %w", err)
	}
	publicKey, err := parseRSAPublicKey(publicRaw)
	if err != nil {
		return nil, fmt.Errorf("parse alipay public key: %w", err)
	}
	if loc == nil {
		loc = time.FixedZone("Asia/Shanghai", 8*60*60)
	}
	return &AlipayClient{
		appID:      appID,
		sellerID:   sellerID,
		notifyURL:  notifyURL,
		privateKey: privateKey,
		publicKey:  publicKey,
		loc:        loc,
	}, nil
}

func (c *AlipayClient) Enabled() bool {
	return c != nil && c.appID != "" && c.sellerID != "" && c.privateKey != nil && c.publicKey != nil && c.notifyURL != ""
}

func (c *AlipayClient) HealthStatus() string {
	if c != nil && c.Enabled() {
		return "ok"
	}
	return "missing_config"
}

func alipayPaymentOrderGateAllows(userID string, body createAlipayOrderRequest) bool {
	if alipayPaymentPublicEnabled() {
		return true
	}
	allowedUsers := paymentEnvTokenSet(os.Getenv(alipayPaymentAllowedUserIDsEnv), normalizePaymentAllowlistUserID)
	allowedBuildTypes := paymentEnvTokenSet(os.Getenv(alipayPaymentAllowedBuildTypesEnv), normalizeAlipayClientBuildType)
	if len(allowedUsers) == 0 || len(allowedBuildTypes) == 0 {
		return false
	}
	normalizedUserID := normalizePaymentAllowlistUserID(userID)
	if !allowedUsers[normalizedUserID] {
		return false
	}
	normalizedBuildType := normalizeAlipayClientBuildType(body.ClientBuildType)
	if normalizedBuildType != "debug" {
		return false
	}
	if !allowedBuildTypes[normalizedBuildType] {
		return false
	}
	return true
}

func alipayPaymentOrderGateStatus() string {
	if alipayPaymentPublicEnabled() {
		return "public"
	}
	allowedUsers := paymentEnvTokenSet(os.Getenv(alipayPaymentAllowedUserIDsEnv), normalizePaymentAllowlistUserID)
	allowedBuildTypes := paymentEnvTokenSet(os.Getenv(alipayPaymentAllowedBuildTypesEnv), normalizeAlipayClientBuildType)
	if len(allowedUsers) > 0 && allowedBuildTypes["debug"] {
		return "limited"
	}
	return "closed"
}

func applyAlipayPaymentTestAmount(product paymentProduct, userID string, body createAlipayOrderRequest) (paymentProduct, bool) {
	if alipayPaymentPublicEnabled() {
		return product, false
	}
	raw := strings.TrimSpace(os.Getenv(alipayPaymentTestAmountCentsEnv))
	if raw == "" {
		return product, false
	}
	cents, err := strconv.Atoi(raw)
	if err != nil || cents <= 0 || cents > alipayPaymentMaxTestAmountCents {
		return product, false
	}
	allowedUsers := paymentEnvTokenSet(os.Getenv(alipayPaymentAllowedUserIDsEnv), normalizePaymentAllowlistUserID)
	if len(allowedUsers) == 0 || !allowedUsers[normalizePaymentAllowlistUserID(userID)] {
		return product, false
	}
	normalizedBuildType := normalizeAlipayClientBuildType(body.ClientBuildType)
	allowedBuildTypes := paymentEnvTokenSet(os.Getenv(alipayPaymentAllowedBuildTypesEnv), normalizeAlipayClientBuildType)
	if len(allowedBuildTypes) == 0 || !allowedBuildTypes[normalizedBuildType] || normalizedBuildType != "debug" {
		return product, false
	}
	product.AmountCents = cents
	product.Subject = truncateRunes(product.Subject+"（联调测试）", 96)
	product.Body = truncateRunes(product.Body+"（内测联调）", 128)
	return product, true
}

func alipayPaymentPublicEnabled() bool {
	return parseBoolEnv(os.Getenv(alipayPaymentPublicEnabledEnv))
}

func paymentEnvTokenSet(raw string, normalize func(string) string) map[string]bool {
	result := map[string]bool{}
	for _, part := range strings.FieldsFunc(raw, func(r rune) bool {
		return r == ',' || r == ';' || r == '\n' || r == '\r' || r == '\t' || r == ' '
	}) {
		value := normalize(part)
		if value != "" {
			result[value] = true
		}
	}
	return result
}

func normalizePaymentAllowlistUserID(value string) string {
	return strings.TrimSpace(value)
}

func normalizeAlipayClientBuildType(value string) string {
	normalized := strings.ToLower(strings.TrimSpace(value))
	if normalized == "" || len(normalized) > 32 {
		return ""
	}
	for _, r := range normalized {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') || r == '_' || r == '-' || r == '.' {
			continue
		}
		return ""
	}
	return normalized
}

func (c *AlipayClient) BuildAppPayOrder(outTradeNo string, product paymentProduct, now time.Time) (string, error) {
	if !c.Enabled() {
		return "", fmt.Errorf("ALIPAY_NOT_CONFIGURED")
	}
	if strings.TrimSpace(outTradeNo) == "" || product.Type == "" || product.AmountCents <= 0 {
		return "", fmt.Errorf("invalid alipay order")
	}
	if now.IsZero() {
		now = time.Now()
	}
	if c.loc != nil {
		now = now.In(c.loc)
	}
	biz := alipayBizContent{
		OutTradeNo:     outTradeNo,
		TotalAmount:    formatAmountCents(product.AmountCents),
		Subject:        product.Subject,
		Body:           product.Body,
		SellerID:       c.sellerID,
		ProductCode:    "QUICK_MSECURITY_PAY",
		TimeoutExpress: "30m",
	}
	bizContent, err := json.Marshal(biz)
	if err != nil {
		return "", err
	}
	params := url.Values{}
	params.Set("app_id", c.appID)
	params.Set("method", "alipay.trade.app.pay")
	params.Set("charset", "utf-8")
	params.Set("format", "json")
	params.Set("sign_type", "RSA2")
	params.Set("timestamp", now.Format("2006-01-02 15:04:05"))
	params.Set("version", "1.0")
	params.Set("notify_url", c.notifyURL)
	params.Set("biz_content", string(bizContent))

	// APP 支付 orderInfo 的签名串需要保留 sign_type；异步通知验签仍按 V1 规则排除 sign_type。
	content := alipaySignContent(params, "sign")
	signature, err := signRSA2(content, c.privateKey)
	if err != nil {
		return "", err
	}
	params.Set("sign", signature)
	return params.Encode(), nil
}

func (c *AlipayClient) VerifyNotify(values url.Values) (alipayNotifyPayload, error) {
	if !c.Enabled() {
		return alipayNotifyPayload{}, fmt.Errorf("ALIPAY_NOT_CONFIGURED")
	}
	signature := strings.TrimSpace(values.Get("sign"))
	if signature == "" {
		return alipayNotifyPayload{}, fmt.Errorf("missing sign")
	}
	if signType := strings.TrimSpace(values.Get("sign_type")); signType != "" && !strings.EqualFold(signType, "RSA2") {
		return alipayNotifyPayload{}, fmt.Errorf("unsupported sign_type")
	}
	content := alipaySignContent(values, "sign", "sign_type")
	if err := verifyRSA2(content, signature, c.publicKey); err != nil {
		return alipayNotifyPayload{}, err
	}
	amountCents, err := parseAmountCents(values.Get("total_amount"))
	if err != nil {
		return alipayNotifyPayload{}, err
	}
	payload := alipayNotifyPayload{
		AppID:            strings.TrimSpace(values.Get("app_id")),
		AuthAppID:        strings.TrimSpace(values.Get("auth_app_id")),
		NotifyID:         strings.TrimSpace(values.Get("notify_id")),
		OutTradeNo:       strings.TrimSpace(values.Get("out_trade_no")),
		TradeNo:          strings.TrimSpace(values.Get("trade_no")),
		BuyerID:          strings.TrimSpace(values.Get("buyer_id")),
		SellerID:         strings.TrimSpace(values.Get("seller_id")),
		TradeStatus:      strings.TrimSpace(values.Get("trade_status")),
		TotalAmountCents: amountCents,
		RawSummary:       alipayNotifySummary(values),
	}
	if payload.AppID != c.appID {
		return payload, fmt.Errorf("app_id mismatch")
	}
	if c.sellerID != "" && payload.SellerID != c.sellerID {
		return payload, fmt.Errorf("seller_id mismatch")
	}
	if payload.OutTradeNo == "" || payload.TradeNo == "" {
		return payload, fmt.Errorf("trade identity missing")
	}
	return payload, nil
}

func (s *Server) handleCreateAlipayPaymentOrder(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if s.alipay == nil || !s.alipay.Enabled() {
		s.writeError(w, http.StatusServiceUnavailable, "ALIPAY_NOT_CONFIGURED")
		return
	}
	var body createAlipayOrderRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	product, ok := paymentProductByType(body.ProductType)
	if !ok {
		s.writeError(w, http.StatusBadRequest, "INVALID_PRODUCT")
		return
	}
	originalAmountCents := product.AmountCents
	if !alipayPaymentOrderGateAllows(auth.UserID, body) {
		s.logger.Info("alipay order blocked by payment gate", "product", body.ProductType, "clientBuildType", normalizeAlipayClientBuildType(body.ClientBuildType), "gate", alipayPaymentOrderGateStatus())
		s.writeError(w, http.StatusServiceUnavailable, "ALIPAY_NOT_CONFIGURED")
		return
	}
	isTestOrder := false
	if overridden, applied := applyAlipayPaymentTestAmount(product, auth.UserID, body); applied {
		product = overridden
		isTestOrder = true
		s.logger.Info("alipay test amount applied", "product", product.Type, "amountCents", product.AmountCents)
	}
	ctx := r.Context()
	if err := s.store.EnsureUser(ctx, auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed before payment", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if err := s.store.ValidatePaymentProduct(ctx, auth.UserID, product.Type); err != nil {
		s.writeError(w, paymentProductHTTPStatus(err), err.Error())
		return
	}
	outTradeNo, err := newPaymentOutTradeNo()
	if err != nil {
		s.logger.Error("create payment out_trade_no failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	now := time.Now()
	orderString, err := s.alipay.BuildAppPayOrder(outTradeNo, product, now)
	if err != nil {
		s.logger.Error("build alipay order failed", "userId", auth.UserID, "product", product.Type, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	order := paymentOrder{
		OutTradeNo:          outTradeNo,
		UserID:              auth.UserID,
		Provider:            paymentProviderAlipay,
		ProductType:         product.Type,
		AmountCents:         product.AmountCents,
		OriginalAmountCents: originalAmountCents,
		Currency:            "CNY",
		Subject:             product.Subject,
		Status:              paymentStatusPending,
		GrantStatus:         paymentGrantPending,
		IsTestOrder:         isTestOrder,
		CreatedAt:           now.UnixMilli(),
		UpdatedAt:           now.UnixMilli(),
	}
	if err := s.store.CreatePaymentOrder(ctx, order, body.ClientAppVersion, body.ClientPlatform, body.ClientBuildType, body.ClientVersionCode, maskIP(GetClientIP(r))); err != nil {
		s.logger.Error("create payment order failed", "userId", auth.UserID, "product", product.Type, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.logger.Info("alipay order created", "userId", auth.UserID, "outTradeSuffix", paymentIDLogSuffix(outTradeNo), "product", product.Type, "amountCents", product.AmountCents)
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":           true,
		"provider":     paymentProviderAlipay,
		"out_trade_no": outTradeNo,
		"product_type": product.Type,
		"subject":      product.Subject,
		"amount_cents": product.AmountCents,
		"order_string": orderString,
	})
}

func (s *Server) handleGetPaymentOrder(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	outTradeNo := strings.TrimSpace(r.URL.Query().Get("out_trade_no"))
	if outTradeNo == "" || len(outTradeNo) > 64 {
		s.writeError(w, http.StatusBadRequest, "INVALID_OUT_TRADE_NO")
		return
	}
	order, found, err := s.store.GetPaymentOrder(ctxWithRequest(r), auth.UserID, outTradeNo)
	if err != nil {
		s.logger.Error("get payment order failed", "userId", auth.UserID, "outTradeSuffix", paymentIDLogSuffix(outTradeNo), "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if !found {
		s.writeError(w, http.StatusNotFound, "ORDER_NOT_FOUND")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":    true,
		"order": order,
	})
}

func (s *Server) handleAlipayPaymentNotify(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, defaultJSONBodyLimit)
	if err := r.ParseForm(); err != nil {
		s.writeAlipayNotifyResult(w, false)
		return
	}
	if !hasAlipayNotifyIdentity(r.PostForm) {
		s.logger.Warn(
			"alipay notify missing required identity",
			"hasOutTradeNo", strings.TrimSpace(r.PostForm.Get("out_trade_no")) != "",
			"hasNotifyID", strings.TrimSpace(r.PostForm.Get("notify_id")) != "",
			"hasSign", strings.TrimSpace(r.PostForm.Get("sign")) != "",
		)
		s.writeAlipayNotifyResult(w, false)
		return
	}
	var (
		payload alipayNotifyPayload
		err     error
	)
	signatureValid := false
	if s.alipay != nil && s.alipay.Enabled() {
		payload, err = s.alipay.VerifyNotify(r.PostForm)
		signatureValid = err == nil
	} else {
		err = fmt.Errorf("ALIPAY_NOT_CONFIGURED")
	}
	processStatus := "verified"
	errorCode := ""
	if err != nil {
		processStatus = "rejected"
		errorCode = truncateRunes(safePaymentErrorCode(err), 64)
		payload = alipayNotifyPayload{
			AppID:            strings.TrimSpace(r.PostForm.Get("app_id")),
			NotifyID:         strings.TrimSpace(r.PostForm.Get("notify_id")),
			OutTradeNo:       strings.TrimSpace(r.PostForm.Get("out_trade_no")),
			TradeNo:          strings.TrimSpace(r.PostForm.Get("trade_no")),
			SellerID:         strings.TrimSpace(r.PostForm.Get("seller_id")),
			TradeStatus:      strings.TrimSpace(r.PostForm.Get("trade_status")),
			TotalAmountCents: bestEffortAmountCents(r.PostForm.Get("total_amount")),
			RawSummary:       alipayNotifySummary(r.PostForm),
		}
	}
	nowMs := time.Now().UnixMilli()
	if recordErr := s.store.RecordPaymentNotification(r.Context(), payload, signatureValid, processStatus, errorCode, nowMs); recordErr != nil {
		s.logger.Warn("record alipay notify failed", "outTradeSuffix", paymentIDLogSuffix(payload.OutTradeNo), "tradeSuffix", paymentIDLogSuffix(payload.TradeNo), "errorCode", safePaymentErrorCode(recordErr))
	}
	if err != nil {
		s.logger.Warn("alipay notify rejected", "outTradeSuffix", paymentIDLogSuffix(payload.OutTradeNo), "tradeSuffix", paymentIDLogSuffix(payload.TradeNo), "errorCode", errorCode)
		s.writeAlipayNotifyResult(w, false)
		return
	}
	if err := s.store.CompleteAlipayPayment(r.Context(), payload, nowMs); err != nil {
		if updateErr := s.store.UpdatePaymentNotificationProcessStatus(r.Context(), paymentProviderAlipay, payload.NotifyID, "failed", safePaymentErrorCode(err), time.Now().UnixMilli()); updateErr != nil {
			s.logger.Warn("update alipay notify failed status failed", "outTradeSuffix", paymentIDLogSuffix(payload.OutTradeNo), "notifyID", paymentIDLogSuffix(payload.NotifyID), "errorCode", safePaymentErrorCode(updateErr))
		}
		s.logger.Error("complete alipay payment failed", "outTradeSuffix", paymentIDLogSuffix(payload.OutTradeNo), "tradeSuffix", paymentIDLogSuffix(payload.TradeNo), "errorCode", safePaymentErrorCode(err))
		s.writeAlipayNotifyResult(w, false)
		return
	}
	if err := s.store.UpdatePaymentNotificationProcessStatus(r.Context(), paymentProviderAlipay, payload.NotifyID, "processed", "", time.Now().UnixMilli()); err != nil {
		s.logger.Warn("update alipay notify processed status failed", "outTradeSuffix", paymentIDLogSuffix(payload.OutTradeNo), "notifyID", paymentIDLogSuffix(payload.NotifyID), "errorCode", safePaymentErrorCode(err))
	}
	s.writeAlipayNotifyResult(w, true)
}

func (s *Server) writeAlipayNotifyResult(w http.ResponseWriter, ok bool) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	if ok {
		_, _ = w.Write([]byte("success"))
		return
	}
	_, _ = w.Write([]byte("failure"))
}

func (s *Store) ValidatePaymentProduct(ctx context.Context, userID string, productType string) error {
	tier, expireAt, err := s.GetTierForUser(ctx, userID, TierFree)
	if err != nil {
		return err
	}
	membershipExpiringSoon := func() bool {
		if expireAt == nil {
			return false
		}
		remaining := *expireAt - time.Now().UnixMilli()
		return remaining > 0 && remaining < int64(paymentMembershipMinRemainingForOrder/time.Millisecond)
	}
	switch productType {
	case paymentProductBuyTopup:
		if tier != TierPlus && tier != TierPro {
			return fmt.Errorf("FORBIDDEN_TIER")
		}
		topupRemaining, _, err := s.GetTopupStatus(ctx, userID)
		if err != nil {
			return err
		}
		if topupRemaining > 0 {
			return fmt.Errorf("TOPUP_LIMIT_REACHED")
		}
		if membershipExpiringSoon() {
			return fmt.Errorf("MEMBERSHIP_EXPIRING_SOON")
		}
	case paymentProductRenewPlus:
		if tier == TierPro {
			return fmt.Errorf("FORBIDDEN_TIER")
		}
	case paymentProductRenewPro:
		if tier == TierPlus {
			return fmt.Errorf("USE_UPGRADE_PLUS_TO_PRO")
		}
	case paymentProductUpgradePlusPro:
		if tier == TierPro {
			return fmt.Errorf("ALREADY_PRO")
		}
		if tier != TierPlus {
			return fmt.Errorf("FORBIDDEN_TIER")
		}
		if membershipExpiringSoon() {
			return fmt.Errorf("MEMBERSHIP_EXPIRING_SOON")
		}
	default:
		return fmt.Errorf("INVALID_PRODUCT")
	}
	return nil
}

func (s *Store) CreatePaymentOrder(ctx context.Context, order paymentOrder, clientAppVersion string, clientPlatform string, clientBuildType string, clientVersionCode int, clientIPMask string) error {
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO payment_orders(
		   out_trade_no, user_id, provider, product_type, amount_cents, currency, subject,
		   status, grant_status, client_app_version, client_platform, client_build_type, client_version_code,
		   client_ip_mask, is_test_order, original_amount_cents,
		   created_at, updated_at
		 )
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		order.OutTradeNo,
		order.UserID,
		firstNonEmpty(order.Provider, paymentProviderAlipay),
		order.ProductType,
		order.AmountCents,
		firstNonEmpty(order.Currency, "CNY"),
		order.Subject,
		paymentStatusPending,
		paymentGrantPending,
		nullableTrimmed(clientAppVersion),
		nullableTrimmed(clientPlatform),
		nullableTrimmed(normalizeAlipayClientBuildType(clientBuildType)),
		nullableInt(clientVersionCode),
		nullableTrimmed(clientIPMask),
		boolToInt(order.IsTestOrder),
		nullableInt(order.OriginalAmountCents),
		order.CreatedAt,
		order.UpdatedAt,
	)
	return err
}

func (s *Store) GetPaymentOrder(ctx context.Context, userID string, outTradeNo string) (paymentOrder, bool, error) {
	row := s.db.QueryRowContext(
		ctx,
		`SELECT out_trade_no, user_id, provider, product_type, amount_cents, currency, subject,
		        original_amount_cents, is_test_order,
		        status, provider_trade_no, provider_status, entitlement_order_id, grant_status, grant_error,
		        created_at, updated_at, paid_at, granted_at
		   FROM payment_orders
		  WHERE out_trade_no = ? AND user_id = ?
		  LIMIT 1`,
		outTradeNo,
		userID,
	)
	order, err := scanPaymentOrder(row)
	if err == sql.ErrNoRows {
		return paymentOrder{}, false, nil
	}
	if err != nil {
		return paymentOrder{}, false, err
	}
	return order, true, nil
}

func (s *Store) RecordPaymentNotification(ctx context.Context, payload alipayNotifyPayload, signatureValid bool, processStatus string, errorCode string, nowMs int64) error {
	summaryJSON, _ := json.Marshal(payload.RawSummary)
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO payment_notifications(
		   provider, out_trade_no, provider_trade_no, notify_id, app_id, seller_id,
		   trade_status, total_amount_cents, signature_valid, process_status, error_code,
		   received_at, summary_json
		 )
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   out_trade_no = VALUES(out_trade_no),
		   provider_trade_no = VALUES(provider_trade_no),
		   app_id = VALUES(app_id),
		   seller_id = VALUES(seller_id),
		   trade_status = VALUES(trade_status),
		   total_amount_cents = VALUES(total_amount_cents),
		   signature_valid = VALUES(signature_valid),
		   process_status = VALUES(process_status),
		   error_code = VALUES(error_code),
		   received_at = VALUES(received_at),
		   processed_at = NULL,
		   summary_json = VALUES(summary_json)`,
		paymentProviderAlipay,
		payload.OutTradeNo,
		nullableTrimmed(payload.TradeNo),
		nullableTrimmed(payload.NotifyID),
		nullableTrimmed(payload.AppID),
		nullableTrimmed(payload.SellerID),
		nullableTrimmed(payload.TradeStatus),
		nullableInt(payload.TotalAmountCents),
		boolToInt(signatureValid),
		firstNonEmpty(processStatus, "received"),
		nullableTrimmed(errorCode),
		nowMs,
		string(summaryJSON),
	)
	return err
}

func (s *Store) UpdatePaymentNotificationProcessStatus(ctx context.Context, provider string, notifyID string, status string, errorCode string, processedAt int64) error {
	if strings.TrimSpace(notifyID) == "" {
		return nil
	}
	_, err := s.db.ExecContext(
		ctx,
		`UPDATE payment_notifications
		    SET process_status = ?, error_code = ?, processed_at = ?
		  WHERE provider = ? AND notify_id = ?`,
		status,
		nullableTrimmed(errorCode),
		processedAt,
		firstNonEmpty(provider, paymentProviderAlipay),
		notifyID,
	)
	return err
}

func (s *Store) CompleteAlipayPayment(ctx context.Context, payload alipayNotifyPayload, nowMs int64) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer rollbackQuietly(tx)

	order, err := scanPaymentOrder(tx.QueryRowContext(
		ctx,
		`SELECT out_trade_no, user_id, provider, product_type, amount_cents, currency, subject,
		        original_amount_cents, is_test_order,
		        status, provider_trade_no, provider_status, entitlement_order_id, grant_status, grant_error,
		        created_at, updated_at, paid_at, granted_at
		   FROM payment_orders
		  WHERE out_trade_no = ? AND provider = ?
		  LIMIT 1
		  FOR UPDATE`,
		payload.OutTradeNo,
		paymentProviderAlipay,
	))
	if err != nil {
		return err
	}
	if order.AmountCents != payload.TotalAmountCents {
		return fmt.Errorf("PAYMENT_AMOUNT_MISMATCH")
	}
	if !alipayTradePaid(payload.TradeStatus) {
		if order.Status == paymentStatusPaid {
			if _, err := tx.ExecContext(
				ctx,
				`UPDATE payment_orders
				    SET last_notify_json = ?, updated_at = ?
				  WHERE out_trade_no = ?`,
				mustJSON(payload.RawSummary),
				nowMs,
				payload.OutTradeNo,
			); err != nil {
				return err
			}
			return tx.Commit()
		}
		nextStatus := order.Status
		if strings.EqualFold(payload.TradeStatus, "TRADE_CLOSED") {
			nextStatus = paymentStatusClosed
		}
		if _, err := tx.ExecContext(
			ctx,
			`UPDATE payment_orders
			    SET status = ?, provider_trade_no = ?, provider_buyer_id = ?, provider_status = ?,
			        last_notify_json = ?, updated_at = ?
			  WHERE out_trade_no = ?`,
			nextStatus,
			nullableTrimmed(payload.TradeNo),
			nullableTrimmed(payload.BuyerID),
			nullableTrimmed(payload.TradeStatus),
			mustJSON(payload.RawSummary),
			nowMs,
			payload.OutTradeNo,
		); err != nil {
			return err
		}
		return tx.Commit()
	}
	if _, err := tx.ExecContext(
		ctx,
		`UPDATE payment_orders
		    SET status = ?,
		        provider_trade_no = ?,
		        provider_buyer_id = ?,
		        provider_status = ?,
		        last_notify_json = ?,
		        paid_at = COALESCE(paid_at, ?),
		        updated_at = ?
		  WHERE out_trade_no = ?`,
		paymentStatusPaid,
		nullableTrimmed(payload.TradeNo),
		nullableTrimmed(payload.BuyerID),
		nullableTrimmed(payload.TradeStatus),
		mustJSON(payload.RawSummary),
		nowMs,
		nowMs,
		payload.OutTradeNo,
	); err != nil {
		return err
	}
	if err := tx.Commit(); err != nil {
		return err
	}
	return s.grantPaidPaymentOrder(ctx, payload.OutTradeNo, nowMs)
}

func (s *Store) grantPaidPaymentOrder(ctx context.Context, outTradeNo string, nowMs int64) error {
	staleProcessingBeforeMs := nowMs - int64(paymentGrantProcessingRetryAfter/time.Millisecond)
	result, err := s.db.ExecContext(
		ctx,
		`UPDATE payment_orders
		    SET grant_status = ?, grant_error = NULL, grant_claimed_at = ?, updated_at = ?
		  WHERE out_trade_no = ?
		    AND status = ?
		    AND (
		        grant_status IN (?, ?)
		        OR (grant_status = ? AND COALESCE(grant_claimed_at, updated_at) < ?)
		    )`,
		paymentGrantProcessing,
		nowMs,
		nowMs,
		outTradeNo,
		paymentStatusPaid,
		paymentGrantPending,
		paymentGrantFailed,
		paymentGrantProcessing,
		staleProcessingBeforeMs,
	)
	if err != nil {
		return err
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		order, err := s.getPaymentOrderByOutTradeNo(ctx, outTradeNo)
		if err != nil {
			return err
		}
		switch {
		case order.GrantStatus == paymentGrantSuccess:
			return nil
		case order.GrantStatus == paymentGrantProcessing:
			return fmt.Errorf("PAYMENT_GRANT_IN_PROGRESS")
		case order.Status != paymentStatusPaid:
			return fmt.Errorf("PAYMENT_NOT_PAID")
		default:
			return fmt.Errorf("PAYMENT_GRANT_NOT_CLAIMED")
		}
	}
	order, err := s.getPaymentOrderByOutTradeNo(ctx, outTradeNo)
	if err != nil {
		return err
	}
	entitlementOrderID := "pay_" + outTradeNo
	if err := s.grantPaymentProduct(ctx, order.UserID, order.ProductType, entitlementOrderID); err != nil {
		grantStatus := paymentGrantFailed
		if paymentGrantNeedsManualOps(err) {
			grantStatus = paymentGrantNeedsOps
		}
		if _, updateErr := s.db.ExecContext(
			ctx,
			`UPDATE payment_orders
			    SET grant_status = ?, grant_error = ?, grant_claimed_at = NULL, entitlement_order_id = ?, updated_at = ?
			  WHERE out_trade_no = ?`,
			grantStatus,
			truncateRunes(safePaymentErrorCode(err), 255),
			entitlementOrderID,
			time.Now().UnixMilli(),
			outTradeNo,
		); updateErr != nil {
			return updateErr
		}
		if grantStatus == paymentGrantNeedsOps {
			return nil
		}
		return err
	}
	_, err = s.db.ExecContext(
		ctx,
		`UPDATE payment_orders
		    SET grant_status = ?, grant_error = NULL, grant_claimed_at = NULL, entitlement_order_id = ?, granted_at = COALESCE(granted_at, ?), updated_at = ?
		  WHERE out_trade_no = ?`,
		paymentGrantSuccess,
		entitlementOrderID,
		time.Now().UnixMilli(),
		time.Now().UnixMilli(),
		outTradeNo,
	)
	return err
}

func (s *Store) grantPaymentProduct(ctx context.Context, userID string, productType string, entitlementOrderID string) error {
	switch productType {
	case paymentProductRenewPlus:
		_, _, _, err := s.RenewPlus(ctx, userID, entitlementOrderID)
		return err
	case paymentProductRenewPro:
		_, _, _, err := s.RenewPro(ctx, userID, entitlementOrderID)
		return err
	case paymentProductUpgradePlusPro:
		_, _, _, _, _, err := s.UpgradePlusToPro(ctx, userID, entitlementOrderID)
		return err
	case paymentProductBuyTopup:
		_, _, _, _, err := s.BuyTopupPack(ctx, userID, entitlementOrderID)
		return err
	default:
		return fmt.Errorf("INVALID_PRODUCT")
	}
}

func paymentGrantNeedsManualOps(err error) bool {
	if err == nil {
		return false
	}
	if errors.Is(err, ErrOrderIDConflict) {
		return true
	}
	switch strings.ToUpper(strings.TrimSpace(err.Error())) {
	case "FORBIDDEN_TIER", "USE_UPGRADE_PLUS_TO_PRO", "ALREADY_PRO", "TOPUP_LIMIT_REACHED", "MEMBERSHIP_EXPIRING_SOON":
		return true
	default:
		return false
	}
}

func (s *Store) getPaymentOrderByOutTradeNo(ctx context.Context, outTradeNo string) (paymentOrder, error) {
	return scanPaymentOrder(s.db.QueryRowContext(
		ctx,
		`SELECT out_trade_no, user_id, provider, product_type, amount_cents, currency, subject,
		        original_amount_cents, is_test_order,
		        status, provider_trade_no, provider_status, entitlement_order_id, grant_status, grant_error,
		        created_at, updated_at, paid_at, granted_at
		   FROM payment_orders
		  WHERE out_trade_no = ?
		  LIMIT 1`,
		outTradeNo,
	))
}

type paymentOrderScanner interface {
	Scan(dest ...any) error
}

func scanPaymentOrder(scanner paymentOrderScanner) (paymentOrder, error) {
	var (
		order              paymentOrder
		providerTradeNo    sql.NullString
		providerStatus     sql.NullString
		entitlementOrderID sql.NullString
		grantError         sql.NullString
		originalAmount     sql.NullInt64
		isTestOrder        sql.NullInt64
		paidAt             sql.NullInt64
		grantedAt          sql.NullInt64
	)
	if err := scanner.Scan(
		&order.OutTradeNo,
		&order.UserID,
		&order.Provider,
		&order.ProductType,
		&order.AmountCents,
		&order.Currency,
		&order.Subject,
		&originalAmount,
		&isTestOrder,
		&order.Status,
		&providerTradeNo,
		&providerStatus,
		&entitlementOrderID,
		&order.GrantStatus,
		&grantError,
		&order.CreatedAt,
		&order.UpdatedAt,
		&paidAt,
		&grantedAt,
	); err != nil {
		return paymentOrder{}, err
	}
	order.ProviderTradeNo = nullStringValue(providerTradeNo)
	order.ProviderStatus = nullStringValue(providerStatus)
	order.EntitlementOrderID = nullStringValue(entitlementOrderID)
	order.GrantError = nullStringValue(grantError)
	if originalAmount.Valid && originalAmount.Int64 > 0 {
		order.OriginalAmountCents = int(originalAmount.Int64)
	}
	order.IsTestOrder = isTestOrder.Valid && isTestOrder.Int64 != 0
	order.PaidAt = nullInt64ToPtr(paidAt)
	order.GrantedAt = nullInt64ToPtr(grantedAt)
	return order, nil
}

func paymentProductByType(raw string) (paymentProduct, bool) {
	switch normalizePaymentProductType(raw) {
	case paymentProductRenewPlus:
		return paymentProduct{
			Type:        paymentProductRenewPlus,
			Subject:     "农技千查 Plus 会员30天",
			Body:        "每天25次问诊，会员有效期30天",
			AmountCents: 1990,
		}, true
	case paymentProductRenewPro:
		return paymentProduct{
			Type:        paymentProductRenewPro,
			Subject:     "农技千查 Pro 会员30天",
			Body:        "每天40次问诊，会员有效期30天",
			AmountCents: 2990,
		}, true
	case paymentProductUpgradePlusPro:
		return paymentProduct{
			Type:        paymentProductUpgradePlusPro,
			Subject:     "农技千查升级 Pro 会员30天",
			Body:        "升级为 Pro 会员30天，Plus剩余权益补为次数",
			AmountCents: 2990,
		}, true
	case paymentProductBuyTopup:
		return paymentProduct{
			Type:        paymentProductBuyTopup,
			Subject:     "农技千查加油包80次",
			Body:        "额外80次问诊次数，长期保留",
			AmountCents: 600,
		}, true
	default:
		return paymentProduct{}, false
	}
}

func normalizePaymentProductType(raw string) string {
	switch strings.TrimSpace(strings.ToLower(raw)) {
	case "plus", "renew_plus", "open_plus":
		return paymentProductRenewPlus
	case "pro", "renew_pro", "open_pro":
		return paymentProductRenewPro
	case "upgrade_pro", "upgrade_plus_to_pro", "plus_to_pro":
		return paymentProductUpgradePlusPro
	case "topup", "topup_80", "buy_topup", "topup_pack":
		return paymentProductBuyTopup
	default:
		return ""
	}
}

func paymentProductHTTPStatus(err error) int {
	switch {
	case err == nil:
		return http.StatusOK
	case errors.Is(err, ErrOrderIDConflict):
		return http.StatusConflict
	}
	switch err.Error() {
	case "FORBIDDEN_TIER":
		return http.StatusForbidden
	case "USE_UPGRADE_PLUS_TO_PRO", "ALREADY_PRO", "TOPUP_LIMIT_REACHED", "MEMBERSHIP_EXPIRING_SOON":
		return http.StatusConflict
	case "INVALID_PRODUCT":
		return http.StatusBadRequest
	default:
		return http.StatusInternalServerError
	}
}

func newPaymentOutTradeNo() (string, error) {
	suffix, err := randomHexString(6)
	if err != nil {
		return "", err
	}
	return "NJ" + time.Now().UTC().Format("20060102150405") + strings.ToUpper(suffix), nil
}

func formatAmountCents(cents int) string {
	if cents <= 0 {
		return "0.00"
	}
	return fmt.Sprintf("%d.%02d", cents/100, cents%100)
}

func parseAmountCents(raw string) (int, error) {
	normalized := strings.TrimSpace(raw)
	if normalized == "" {
		return 0, fmt.Errorf("amount empty")
	}
	parts := strings.Split(normalized, ".")
	if len(parts) > 2 || parts[0] == "" {
		return 0, fmt.Errorf("invalid amount")
	}
	yuan, err := strconv.Atoi(parts[0])
	if err != nil || yuan < 0 {
		return 0, fmt.Errorf("invalid amount")
	}
	fen := 0
	if len(parts) == 2 {
		decimal := parts[1]
		if len(decimal) == 0 {
			decimal = "00"
		}
		if len(decimal) == 1 {
			decimal += "0"
		}
		if len(decimal) > 2 {
			return 0, fmt.Errorf("invalid amount precision")
		}
		fen, err = strconv.Atoi(decimal)
		if err != nil || fen < 0 || fen > 99 {
			return 0, fmt.Errorf("invalid amount")
		}
	}
	return yuan*100 + fen, nil
}

func bestEffortAmountCents(raw string) int {
	value, err := parseAmountCents(raw)
	if err != nil {
		return 0
	}
	return value
}

func alipaySignContent(values url.Values, excluded ...string) string {
	excludedSet := map[string]struct{}{}
	for _, key := range excluded {
		excludedSet[strings.TrimSpace(key)] = struct{}{}
	}
	keys := make([]string, 0, len(values))
	for key := range values {
		if _, ok := excludedSet[key]; ok {
			continue
		}
		if strings.TrimSpace(key) == "" {
			continue
		}
		keys = append(keys, key)
	}
	sort.Strings(keys)
	parts := make([]string, 0, len(keys))
	for _, key := range keys {
		value := strings.TrimSpace(values.Get(key))
		if value == "" {
			continue
		}
		parts = append(parts, key+"="+value)
	}
	return strings.Join(parts, "&")
}

func signRSA2(content string, key *rsa.PrivateKey) (string, error) {
	if key == nil {
		return "", fmt.Errorf("private key missing")
	}
	digest := sha256.Sum256([]byte(content))
	signature, err := rsa.SignPKCS1v15(rand.Reader, key, crypto.SHA256, digest[:])
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(signature), nil
}

func verifyRSA2(content string, signature string, key *rsa.PublicKey) error {
	if key == nil {
		return fmt.Errorf("public key missing")
	}
	decoded, err := base64.StdEncoding.DecodeString(strings.TrimSpace(signature))
	if err != nil {
		return err
	}
	digest := sha256.Sum256([]byte(content))
	return rsa.VerifyPKCS1v15(key, crypto.SHA256, digest[:], decoded)
}

func parseRSAPrivateKey(raw string) (*rsa.PrivateKey, error) {
	der, err := decodeKeyDER(raw)
	if err != nil {
		return nil, err
	}
	if key, err := x509.ParsePKCS8PrivateKey(der); err == nil {
		rsaKey, ok := key.(*rsa.PrivateKey)
		if !ok {
			return nil, fmt.Errorf("not rsa private key")
		}
		return rsaKey, nil
	}
	return x509.ParsePKCS1PrivateKey(der)
}

func parseRSAPublicKey(raw string) (*rsa.PublicKey, error) {
	der, err := decodeKeyDER(raw)
	if err != nil {
		return nil, err
	}
	if key, err := x509.ParsePKIXPublicKey(der); err == nil {
		rsaKey, ok := key.(*rsa.PublicKey)
		if !ok {
			return nil, fmt.Errorf("not rsa public key")
		}
		return rsaKey, nil
	}
	return x509.ParsePKCS1PublicKey(der)
}

func decodeKeyDER(raw string) ([]byte, error) {
	normalized := strings.TrimSpace(raw)
	if normalized == "" {
		return nil, fmt.Errorf("key empty")
	}
	if block, _ := pem.Decode([]byte(normalized)); block != nil {
		return block.Bytes, nil
	}
	normalized = strings.NewReplacer("\r", "", "\n", "", "\t", "", " ", "").Replace(normalized)
	return base64.StdEncoding.DecodeString(normalized)
}

func readEnvOrFile(valueEnv string, fileEnv string) (string, error) {
	if value := strings.TrimSpace(os.Getenv(valueEnv)); value != "" {
		return value, nil
	}
	path := strings.TrimSpace(os.Getenv(fileEnv))
	if path == "" {
		return "", nil
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("read %s: %w", fileEnv, err)
	}
	return string(raw), nil
}

func alipayNotifySummary(values url.Values) map[string]any {
	keys := []string{
		"notify_id", "app_id", "auth_app_id", "out_trade_no", "trade_no", "buyer_id",
		"seller_id", "trade_status", "total_amount", "receipt_amount", "gmt_payment",
		"notify_time", "sign_type",
	}
	summary := make(map[string]any, len(keys))
	for _, key := range keys {
		if value := strings.TrimSpace(values.Get(key)); value != "" {
			summary[key] = value
		}
	}
	return summary
}

func hasAlipayNotifyIdentity(values url.Values) bool {
	if values == nil {
		return false
	}
	return strings.TrimSpace(values.Get("out_trade_no")) != "" &&
		strings.TrimSpace(values.Get("notify_id")) != "" &&
		strings.TrimSpace(values.Get("sign")) != ""
}

func alipayTradePaid(status string) bool {
	return strings.EqualFold(status, "TRADE_SUCCESS") || strings.EqualFold(status, "TRADE_FINISHED")
}

func nullableInt(value int) any {
	if value <= 0 {
		return nil
	}
	return value
}

func mustJSON(value any) string {
	raw, err := json.Marshal(value)
	if err != nil {
		return "{}"
	}
	return string(raw)
}

func safePaymentErrorCode(err error) string {
	if err == nil {
		return ""
	}
	if errors.Is(err, ErrOrderIDConflict) {
		return "ORDER_ID_CONFLICT"
	}
	raw := strings.TrimSpace(err.Error())
	if raw == "" {
		return "UNKNOWN"
	}
	upper := strings.ToUpper(raw)
	for _, code := range []string{
		"PAYMENT_AMOUNT_MISMATCH",
		"PAYMENT_GRANT_IN_PROGRESS",
		"PAYMENT_NOT_PAID",
		"PAYMENT_GRANT_NOT_CLAIMED",
		"FORBIDDEN_TIER",
		"USE_UPGRADE_PLUS_TO_PRO",
		"ALREADY_PRO",
		"TOPUP_LIMIT_REACHED",
		"MEMBERSHIP_EXPIRING_SOON",
		"INVALID_PRODUCT",
	} {
		if upper == code || strings.Contains(upper, code) {
			return code
		}
	}
	lower := strings.ToLower(raw)
	switch {
	case strings.Contains(lower, "app_id mismatch"):
		return "ALIPAY_APP_ID_MISMATCH"
	case strings.Contains(lower, "seller_id mismatch"):
		return "ALIPAY_SELLER_ID_MISMATCH"
	case strings.Contains(lower, "signature invalid"):
		return "ALIPAY_SIGNATURE_INVALID"
	case strings.Contains(lower, "sign missing"):
		return "ALIPAY_SIGN_MISSING"
	case strings.Contains(lower, "sign_type unsupported"):
		return "ALIPAY_SIGN_TYPE_UNSUPPORTED"
	case strings.Contains(lower, "trade identity missing"):
		return "ALIPAY_TRADE_IDENTITY_MISSING"
	case strings.Contains(lower, "amount empty") || strings.Contains(lower, "invalid amount"):
		return "ALIPAY_AMOUNT_INVALID"
	case strings.Contains(lower, "duplicate entry") && strings.Contains(lower, "uniq_payment_provider_trade"):
		return "DB_DUPLICATE_PROVIDER_TRADE"
	case strings.Contains(lower, "duplicate entry"):
		return "DB_DUPLICATE"
	case strings.Contains(lower, "deadlock"):
		return "DB_DEADLOCK"
	case strings.Contains(lower, "lock wait timeout"):
		return "DB_LOCK_WAIT_TIMEOUT"
	case strings.Contains(lower, "context deadline exceeded"):
		return "CONTEXT_DEADLINE_EXCEEDED"
	case strings.Contains(lower, "context canceled"):
		return "CONTEXT_CANCELED"
	default:
		return "INTERNAL_ERROR"
	}
}

func paymentIDLogSuffix(raw string) string {
	value := strings.TrimSpace(raw)
	if value == "" {
		return ""
	}
	runes := []rune(value)
	if len(runes) <= 8 {
		return value
	}
	return string(runes[len(runes)-8:])
}

func ctxWithRequest(r *http.Request) context.Context {
	if r == nil {
		return context.Background()
	}
	return r.Context()
}

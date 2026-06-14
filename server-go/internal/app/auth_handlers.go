package app

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
)

const authJSONBodyLimit = 8 * 1024
const (
	defaultAuthFusionTokenRateLimitWindow        = 10 * time.Minute
	defaultAuthFusionTokenRateLimitMaxHits       = 20
	defaultAuthFusionTokenRateLimitPruneInterval = 10 * time.Minute

	defaultAuthFusionLoginRateLimitWindow        = 10 * time.Minute
	defaultAuthFusionLoginRateLimitMaxHits       = 20
	defaultAuthFusionLoginRateLimitPruneInterval = 10 * time.Minute

	defaultAuthSMSRateLimitWindow        = 10 * time.Minute
	defaultAuthSMSRateLimitMaxHits       = 5
	defaultAuthSMSRateLimitPruneInterval = 10 * time.Minute

	defaultAuthSMSIPRateLimitWindow        = 10 * time.Minute
	defaultAuthSMSIPRateLimitMaxHits       = 20
	defaultAuthSMSIPRateLimitPruneInterval = 10 * time.Minute

	defaultAuthSMSLoginRateLimitWindow        = 10 * time.Minute
	defaultAuthSMSLoginRateLimitMaxHits       = 10
	defaultAuthSMSLoginRateLimitPruneInterval = 10 * time.Minute

	defaultFusionVerifiedPhoneTTL = 2 * time.Minute
	defaultSMSCodeCacheTimeout    = time.Second
)

type authLoginRequest struct {
	VerifyToken  string `json:"verify_token,omitempty"`
	PhoneNumber  string `json:"phone_number,omitempty"`
	VerifyCode   string `json:"verify_code,omitempty"`
	LegacyUserID string `json:"legacy_user_id,omitempty"`
	DeviceID     string `json:"device_id,omitempty"`
}

type authSMSSendRequest struct {
	PhoneNumber string `json:"phone_number"`
}

func authFusionCompatEnabled() bool {
	return parseBoolEnv(os.Getenv("AUTH_FUSION_COMPAT_ENABLED"))
}

func (s *Server) writeFusionCompatDisabled(w http.ResponseWriter) {
	s.writeError(w, http.StatusGone, "fusion_auth_disabled")
}

func (s *Server) handleAuthFusionToken(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		s.writeError(w, http.StatusMethodNotAllowed, "method_not_allowed")
		return
	}
	if !authFusionCompatEnabled() {
		s.writeFusionCompatDisabled(w)
		return
	}
	if s.fusionTokenLimiter != nil {
		limitKey := authIPRateLimitKey("fusion_token", GetClientIP(r))
		if allowed, retryAfter := s.fusionTokenLimiter.Consume(limitKey, time.Now()); !allowed {
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":               "rate_limited",
				"retry_after_seconds": retryAfter,
			})
			return
		}
	}
	token, err := s.dypns.GetFusionAuthToken(r.Context())
	if err != nil {
		s.logger.Warn("fusion auth token failed", "error", sanitizeProviderError(err))
		s.writeError(w, http.StatusServiceUnavailable, "fusion_auth_not_configured")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"auth_token":  token,
		"scheme_code": s.dypns.FusionSchemeCode(),
		"expires_in":  1800,
	})
}

func (s *Server) handleAuthFusionLogin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		s.writeError(w, http.StatusMethodNotAllowed, "method_not_allowed")
		return
	}
	if !authFusionCompatEnabled() {
		s.writeFusionCompatDisabled(w)
		return
	}
	var body authLoginRequest
	if err := decodeJSONBodyLimited(r, &body, authJSONBodyLimit); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	verifyToken := strings.TrimSpace(body.VerifyToken)
	if verifyToken == "" || len(verifyToken) > 4096 {
		s.writeError(w, http.StatusBadRequest, "invalid_verify_token")
		return
	}
	if s.fusionLoginLimiter != nil {
		limitKey := authIPRateLimitKey("fusion_login", GetClientIP(r))
		if allowed, retryAfter := s.fusionLoginLimiter.Consume(limitKey, time.Now()); !allowed {
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":               "rate_limited",
				"retry_after_seconds": retryAfter,
			})
			return
		}
	}
	cachedPhone, cachedErr := s.consumeCachedFusionVerifiedPhone(r.Context(), verifyToken)
	if cachedErr == nil && cachedPhone != "" {
		s.finishPhoneLogin(w, r, cachedPhone, body.LegacyUserID, body.DeviceID)
		return
	}
	if cachedErr != nil {
		s.logger.Warn("fusion verified cache consume failed", "error", sanitizeProviderError(cachedErr), "masked_ip", maskIP(GetClientIP(r)))
	}
	phone, err := s.dypns.VerifyFusionToken(r.Context(), verifyToken)
	if err != nil {
		s.logger.Warn("fusion login verify failed", "error", sanitizeProviderError(err), "masked_ip", maskIP(GetClientIP(r)))
		s.writeError(w, http.StatusUnauthorized, "auth_verify_failed")
		return
	}
	s.finishPhoneLogin(w, r, phone, body.LegacyUserID, body.DeviceID)
}

func (s *Server) handleAuthFusionVerify(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		s.writeError(w, http.StatusMethodNotAllowed, "method_not_allowed")
		return
	}
	if !authFusionCompatEnabled() {
		s.writeFusionCompatDisabled(w)
		return
	}
	var body authLoginRequest
	if err := decodeJSONBodyLimited(r, &body, authJSONBodyLimit); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	verifyToken := strings.TrimSpace(body.VerifyToken)
	if verifyToken == "" || len(verifyToken) > 4096 {
		s.writeError(w, http.StatusBadRequest, "invalid_verify_token")
		return
	}
	if s.fusionLoginLimiter != nil {
		limitKey := authIPRateLimitKey("fusion_verify", GetClientIP(r))
		if allowed, retryAfter := s.fusionLoginLimiter.Consume(limitKey, time.Now()); !allowed {
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":               "rate_limited",
				"retry_after_seconds": retryAfter,
			})
			return
		}
	}
	phone, err := s.dypns.VerifyFusionToken(r.Context(), verifyToken)
	if err != nil {
		s.logger.Warn("fusion verify failed", "error", sanitizeProviderError(err), "masked_ip", maskIP(GetClientIP(r)))
		s.writeError(w, http.StatusUnauthorized, "auth_verify_failed")
		return
	}
	if err := s.cacheFusionVerifiedPhone(r.Context(), verifyToken, phone); err != nil {
		s.logger.Warn("fusion verify cache failed", "error", sanitizeProviderError(err), "masked_ip", maskIP(GetClientIP(r)))
		s.writeError(w, http.StatusServiceUnavailable, "fusion_verify_cache_failed")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":         true,
		"phone_mask": maskPhone(phone),
	})
}

func (s *Server) handleAuthSMSSend(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		s.writeError(w, http.StatusMethodNotAllowed, "method_not_allowed")
		return
	}
	var body authSMSSendRequest
	if err := decodeJSONBodyLimited(r, &body, authJSONBodyLimit); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	phone := normalizeMainlandPhone(body.PhoneNumber)
	if phone == "" {
		s.writeError(w, http.StatusBadRequest, "invalid_phone")
		return
	}
	if s.smsIPLimiter != nil {
		limitKey := authIPRateLimitKey("sms_send_ip", GetClientIP(r))
		if allowed, retryAfter := s.smsIPLimiter.Consume(limitKey, time.Now()); !allowed {
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":               "rate_limited",
				"retry_after_seconds": retryAfter,
			})
			return
		}
	}
	if s.smsLimiter != nil {
		limitKey := authRateLimitKey("sms_send", phone, GetClientIP(r))
		if allowed, retryAfter := s.smsLimiter.Consume(limitKey, time.Now()); !allowed {
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":               "rate_limited",
				"retry_after_seconds": retryAfter,
			})
			return
		}
	}
	code, err := randomSMSCode(defaultSMSCodeLength)
	if err != nil {
		s.logger.Error("sms code generate failed", "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if err := s.cacheSMSCode(r.Context(), phone, code); err != nil {
		s.logger.Warn("sms code cache failed", "error", sanitizeProviderError(err), "phone_mask", maskPhone(phone))
		s.writeError(w, http.StatusServiceUnavailable, "sms_cache_unavailable")
		return
	}
	outID, err := randomHexString(12)
	if err != nil {
		_ = s.clearSMSCode(r.Context(), phone)
		s.logger.Error("sms out id failed", "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if err := s.sms.SendLoginCode(r.Context(), phone, code, outID); err != nil {
		s.logger.Warn("sms send failed", "error", sanitizeProviderError(err), "phone_mask", maskPhone(phone))
		s.writeError(w, http.StatusServiceUnavailable, smsSendErrorCode(err))
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":                  true,
		"retry_after_seconds": 60,
	})
}

func newAuthFusionTokenRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("AUTH_FUSION_TOKEN_RATE_LIMIT_WINDOW_SECONDS", defaultAuthFusionTokenRateLimitWindow),
		MaxHits:       envIntWithDefault("AUTH_FUSION_TOKEN_RATE_LIMIT_MAX_HITS", defaultAuthFusionTokenRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("AUTH_FUSION_TOKEN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultAuthFusionTokenRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultAuthFusionTokenRateLimitWindow, defaultAuthFusionTokenRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(config)
}

func newAuthFusionLoginRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("AUTH_FUSION_LOGIN_RATE_LIMIT_WINDOW_SECONDS", defaultAuthFusionLoginRateLimitWindow),
		MaxHits:       envIntWithDefault("AUTH_FUSION_LOGIN_RATE_LIMIT_MAX_HITS", defaultAuthFusionLoginRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("AUTH_FUSION_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultAuthFusionLoginRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultAuthFusionLoginRateLimitWindow, defaultAuthFusionLoginRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(config)
}

func newAuthSMSRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("AUTH_SMS_RATE_LIMIT_WINDOW_SECONDS", defaultAuthSMSRateLimitWindow),
		MaxHits:       envIntWithDefault("AUTH_SMS_RATE_LIMIT_MAX_HITS", defaultAuthSMSRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("AUTH_SMS_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultAuthSMSRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultAuthSMSRateLimitWindow, defaultAuthSMSRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(config)
}

func newAuthSMSIPRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("AUTH_SMS_IP_RATE_LIMIT_WINDOW_SECONDS", defaultAuthSMSIPRateLimitWindow),
		MaxHits:       envIntWithDefault("AUTH_SMS_IP_RATE_LIMIT_MAX_HITS", defaultAuthSMSIPRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("AUTH_SMS_IP_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultAuthSMSIPRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultAuthSMSIPRateLimitWindow, defaultAuthSMSIPRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(config)
}

func newAuthSMSLoginRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("AUTH_SMS_LOGIN_RATE_LIMIT_WINDOW_SECONDS", defaultAuthSMSLoginRateLimitWindow),
		MaxHits:       envIntWithDefault("AUTH_SMS_LOGIN_RATE_LIMIT_MAX_HITS", defaultAuthSMSLoginRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("AUTH_SMS_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultAuthSMSLoginRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultAuthSMSLoginRateLimitWindow, defaultAuthSMSLoginRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(config)
}

func (s *Server) handleAuthSMSLogin(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		s.writeError(w, http.StatusMethodNotAllowed, "method_not_allowed")
		return
	}
	var body authLoginRequest
	if err := decodeJSONBodyLimited(r, &body, authJSONBodyLimit); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	phone := normalizeMainlandPhone(body.PhoneNumber)
	if phone == "" {
		s.writeError(w, http.StatusBadRequest, "invalid_phone")
		return
	}
	if strings.TrimSpace(body.VerifyCode) == "" {
		s.writeError(w, http.StatusBadRequest, "invalid_code")
		return
	}
	if s.smsLoginLimiter != nil {
		limitKey := authRateLimitKey("sms_login", phone, GetClientIP(r))
		if allowed, retryAfter := s.smsLoginLimiter.Consume(limitKey, time.Now()); !allowed {
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":               "rate_limited",
				"retry_after_seconds": retryAfter,
			})
			return
		}
	}
	ok, err := s.verifySMSCode(r.Context(), phone, body.VerifyCode)
	if err != nil {
		s.logger.Warn("sms login cache verify failed", "error", sanitizeProviderError(err), "phone_mask", maskPhone(phone), "masked_ip", maskIP(GetClientIP(r)))
		s.writeError(w, http.StatusServiceUnavailable, "sms_cache_unavailable")
		return
	}
	if !ok {
		s.logger.Warn("sms login verify failed", "phone_mask", maskPhone(phone), "masked_ip", maskIP(GetClientIP(r)))
		s.writeError(w, http.StatusUnauthorized, "auth_verify_failed")
		return
	}
	if s.finishPhoneLogin(w, r, phone, body.LegacyUserID, body.DeviceID) {
		if err := s.clearSMSCode(r.Context(), phone); err != nil {
			s.logger.Warn("sms code clear after login failed", "error", sanitizeProviderError(err), "phone_mask", maskPhone(phone))
		}
	}
}

func (s *Server) handleAuthLogout(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		s.writeError(w, http.StatusMethodNotAllowed, "method_not_allowed")
		return
	}
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if auth.AuthMode != AuthModeToken || strings.TrimSpace(auth.SessionID) == "" {
		s.writeError(w, http.StatusBadRequest, "token_session_required")
		return
	}
	if err := s.store.RevokeAuthSession(r.Context(), auth.UserID, auth.SessionID, time.Now().UnixMilli()); err != nil {
		s.logger.Error("logout session revoke failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func authRateLimitKey(scope string, phone string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	return strings.TrimSpace(scope) + ":" + rateLimitHash(phone, secret) + ":" + rateLimitHash(ip, secret)
}

func authIPRateLimitKey(scope string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	return strings.TrimSpace(scope) + ":" + rateLimitHash(ip, secret)
}

func (s *Server) cacheFusionVerifiedPhone(ctx context.Context, verifyToken string, phone string) error {
	if s == nil || s.redisClient == nil {
		return nil
	}
	phone = normalizeMainlandPhone(phone)
	if phone == "" {
		return nil
	}
	key := fusionVerifiedPhoneCacheKey(verifyToken)
	if key == "" {
		return nil
	}
	ciphertext, err := encryptAccountPhoneNumberWithSecret(phone, strings.TrimSpace(os.Getenv("APP_SECRET")))
	if err != nil {
		return err
	}
	cacheCtx, cancel := context.WithTimeout(ctx, envDurationWithDefault("REDIS_AUTH_CACHE_TIMEOUT_SECONDS", time.Second))
	defer cancel()
	return s.redisClient.Set(cacheCtx, key, ciphertext, envDurationWithDefault("AUTH_FUSION_VERIFY_CACHE_SECONDS", defaultFusionVerifiedPhoneTTL)).Err()
}

func (s *Server) consumeCachedFusionVerifiedPhone(ctx context.Context, verifyToken string) (string, error) {
	if s == nil || s.redisClient == nil {
		return "", nil
	}
	key := fusionVerifiedPhoneCacheKey(verifyToken)
	if key == "" {
		return "", nil
	}
	cacheCtx, cancel := context.WithTimeout(ctx, envDurationWithDefault("REDIS_AUTH_CACHE_TIMEOUT_SECONDS", time.Second))
	defer cancel()
	value, err := s.redisClient.GetDel(cacheCtx, key).Result()
	if err != nil {
		if err == redis.Nil {
			return "", nil
		}
		return "", err
	}
	phone, err := decryptAccountPhoneNumberWithSecret(value, strings.TrimSpace(os.Getenv("APP_SECRET")))
	if err != nil {
		return "", err
	}
	return normalizeMainlandPhone(phone), nil
}

func (s *Server) cacheSMSCode(ctx context.Context, phone string, code string) error {
	if s == nil || s.redisClient == nil {
		return fmt.Errorf("sms_cache_not_configured")
	}
	key := smsCodeCacheKey(phone)
	digest := smsCodeDigest(phone, code)
	if key == "" || digest == "" {
		return fmt.Errorf("sms_invalid_request")
	}
	cacheCtx, cancel := context.WithTimeout(ctx, envDurationWithDefault("REDIS_AUTH_CACHE_TIMEOUT_SECONDS", defaultSMSCodeCacheTimeout))
	defer cancel()
	ttl := envDurationWithDefault("AUTH_SMS_CODE_TTL_SECONDS", defaultSMSCodeTTL)
	return s.redisClient.Set(cacheCtx, key, digest, ttl).Err()
}

func (s *Server) verifySMSCode(ctx context.Context, phone string, code string) (bool, error) {
	if s == nil || s.redisClient == nil {
		return false, fmt.Errorf("sms_cache_not_configured")
	}
	key := smsCodeCacheKey(phone)
	digest := smsCodeDigest(phone, code)
	if key == "" || digest == "" {
		return false, nil
	}
	cacheCtx, cancel := context.WithTimeout(ctx, envDurationWithDefault("REDIS_AUTH_CACHE_TIMEOUT_SECONDS", defaultSMSCodeCacheTimeout))
	defer cancel()
	value, err := s.redisClient.Get(cacheCtx, key).Result()
	if err != nil {
		if err == redis.Nil {
			return false, nil
		}
		return false, err
	}
	if !hmac.Equal([]byte(strings.TrimSpace(value)), []byte(digest)) {
		return false, nil
	}
	return true, nil
}

func (s *Server) clearSMSCode(ctx context.Context, phone string) error {
	if s == nil || s.redisClient == nil {
		return nil
	}
	key := smsCodeCacheKey(phone)
	if key == "" {
		return nil
	}
	cacheCtx, cancel := context.WithTimeout(ctx, envDurationWithDefault("REDIS_AUTH_CACHE_TIMEOUT_SECONDS", defaultSMSCodeCacheTimeout))
	defer cancel()
	return s.redisClient.Del(cacheCtx, key).Err()
}

func smsSendErrorCode(err error) string {
	if err == nil {
		return "sms_send_failed"
	}
	text := strings.ToLower(err.Error())
	switch {
	case strings.Contains(text, "not_configured"):
		return "sms_send_not_configured"
	case strings.Contains(text, "isv.businesslimitcontrol"), strings.Contains(text, "frequency"), strings.Contains(text, "rate"):
		return "sms_provider_rate_limited"
	case strings.Contains(text, "invalid"), strings.Contains(text, "template"), strings.Contains(text, "sign"):
		return "sms_provider_config_invalid"
	default:
		return "sms_send_failed"
	}
}

func fusionVerifiedPhoneCacheKey(verifyToken string) string {
	hash := sensitiveTokenHash(verifyToken)
	if hash == "" {
		return ""
	}
	return "nj:auth:fusion:verified:" + hash
}

func sensitiveTokenHash(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	if secret == "" {
		sum := sha256.Sum256([]byte(value))
		return hex.EncodeToString(sum[:])
	}
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(value))
	return hex.EncodeToString(mac.Sum(nil))
}

func sanitizeProviderError(err error) string {
	if err == nil {
		return ""
	}
	text := truncateRunes(strings.TrimSpace(err.Error()), 200)
	if text == "" {
		text = "provider_error"
	}
	for _, sensitive := range []string{
		"PhoneNumber",
		"VerifyToken",
		"TemplateParam",
		"AccessKey",
		"Secret",
		"Token",
		"Authorization",
	} {
		text = strings.ReplaceAll(text, sensitive, sensitive[:minInt(len(sensitive), 5)]+"***")
	}
	text = maskLongDigitRuns(text)
	return fmt.Sprintf("%T:%s", err, text)
}

func maskLongDigitRuns(text string) string {
	var builder strings.Builder
	digitRun := 0
	for _, r := range text {
		if r >= '0' && r <= '9' {
			digitRun++
			if digitRun >= 4 {
				builder.WriteByte('*')
				continue
			}
		} else {
			digitRun = 0
		}
		builder.WriteRune(r)
	}
	return builder.String()
}

func (s *Server) handleAuthSession(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"user_id": auth.UserID,
	})
}

func (s *Server) finishPhoneLogin(w http.ResponseWriter, r *http.Request, phone string, legacyUserID string, deviceID string) bool {
	acceptedLegacyUserID := acceptedLegacyUserIDFromLoginRequest(r, legacyUserID)
	result, err := s.store.LoginWithVerifiedPhone(
		r.Context(),
		phone,
		acceptedLegacyUserID,
		deviceID,
		strings.TrimSpace(os.Getenv("APP_SECRET")),
		time.Duration(envIntWithDefault("AUTH_SESSION_DAYS", 3650))*24*time.Hour,
	)
	if err != nil {
		s.logger.Error("finish phone login failed", "phone_mask", maskPhone(phone), "legacyMigrationRequested", normalizeUserID(legacyUserID) != "", "legacyMigrationAccepted", acceptedLegacyUserID != "", "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return false
	}
	if acceptedLegacyUserID != "" {
		s.logger.Info(
			"auth legacy user migration handled",
			"legacy_user_id_hash", sensitiveTokenHash(acceptedLegacyUserID),
			"legacy_user_id_kind", legacyUserIDLogKind(acceptedLegacyUserID),
			"target_user_id", result.UserID,
			"migration_status", result.LegacyMigrationStatus,
		)
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"user_id":    result.UserID,
		"phone_mask": result.PhoneMask,
		"token":      result.Token,
		"expires_at": result.ExpiresAt,
	})
	return true
}

func acceptedLegacyUserIDFromLoginRequest(r *http.Request, requestedLegacyUserID string) string {
	legacyUserID := normalizeUserID(requestedLegacyUserID)
	if legacyUserID == "" || strings.HasPrefix(legacyUserID, "acct_") {
		return ""
	}
	if legacyUserIDProvenByAuthHeader(r, legacyUserID) {
		return legacyUserID
	}
	if isUnprovenLegacyUUIDBridgeAllowed() && isLocalLegacyUserID(legacyUserID) {
		return legacyUserID
	}
	return ""
}

func legacyUserIDProvenByAuthHeader(r *http.Request, legacyUserID string) bool {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	if secret == "" {
		return false
	}
	authHeader := strings.TrimSpace(r.Header.Get("Authorization"))
	if !strings.HasPrefix(authHeader, "Bearer ") {
		return false
	}
	token := strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))
	var provenUserID string
	var ok bool
	if strings.HasPrefix(token, "v2.") {
		provenUserID, _, ok = verifyV2Token(token, secret)
	} else {
		provenUserID, ok = verifyLegacyToken(token, secret)
	}
	return ok && normalizeUserID(provenUserID) == legacyUserID
}

func isUnprovenLegacyUUIDBridgeAllowed() bool {
	return parseBoolEnv(os.Getenv("AUTH_ALLOW_UNPROVEN_LEGACY_UUID"))
}

func isLocalLegacyUserID(value string) bool {
	value = strings.ToLower(strings.TrimSpace(value))
	if len(value) != 36 {
		return false
	}
	for i, ch := range value {
		switch i {
		case 8, 13, 18, 23:
			if ch != '-' {
				return false
			}
		default:
			if (ch < '0' || ch > '9') && (ch < 'a' || ch > 'f') {
				return false
			}
		}
	}
	return true
}

func legacyUserIDLogKind(value string) string {
	if isLocalLegacyUserID(value) {
		return "local_uuid"
	}
	return "signed_legacy_token"
}

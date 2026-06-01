package app

import (
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

	defaultAuthSMSRateLimitWindow        = 10 * time.Minute
	defaultAuthSMSRateLimitMaxHits       = 5
	defaultAuthSMSRateLimitPruneInterval = 10 * time.Minute

	defaultAuthSMSLoginRateLimitWindow        = 10 * time.Minute
	defaultAuthSMSLoginRateLimitMaxHits       = 10
	defaultAuthSMSLoginRateLimitPruneInterval = 10 * time.Minute
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

func (s *Server) handleAuthFusionToken(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		s.writeError(w, http.StatusMethodNotAllowed, "method_not_allowed")
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
		s.logger.Warn("fusion auth token failed", "error", err)
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
	var body authLoginRequest
	if err := decodeJSONBodyLimited(r, &body, authJSONBodyLimit); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	phone, err := s.dypns.VerifyFusionToken(r.Context(), body.VerifyToken)
	if err != nil {
		s.logger.Warn("fusion login verify failed", "error", err, "masked_ip", maskIP(GetClientIP(r)))
		s.writeError(w, http.StatusUnauthorized, "auth_verify_failed")
		return
	}
	s.finishPhoneLogin(w, r, phone, body.LegacyUserID, body.DeviceID)
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
	outID, err := randomHexString(12)
	if err != nil {
		s.logger.Error("sms out id failed", "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if err := s.dypns.SendSMSCode(r.Context(), phone, outID); err != nil {
		s.logger.Warn("sms send failed", "error", err, "phone_mask", maskPhone(phone))
		s.writeError(w, http.StatusServiceUnavailable, "sms_auth_not_configured")
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
	if err := s.dypns.CheckSMSCode(r.Context(), phone, body.VerifyCode); err != nil {
		s.logger.Warn("sms login verify failed", "error", err, "phone_mask", maskPhone(phone), "masked_ip", maskIP(GetClientIP(r)))
		s.writeError(w, http.StatusUnauthorized, "auth_verify_failed")
		return
	}
	s.finishPhoneLogin(w, r, phone, body.LegacyUserID, body.DeviceID)
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

func (s *Server) handleAuthSession(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"user_id": auth.UserID,
	})
}

func (s *Server) finishPhoneLogin(w http.ResponseWriter, r *http.Request, phone string, legacyUserID string, deviceID string) {
	result, err := s.store.LoginWithVerifiedPhone(
		r.Context(),
		phone,
		legacyUserID,
		deviceID,
		strings.TrimSpace(os.Getenv("APP_SECRET")),
		time.Duration(envIntWithDefault("AUTH_SESSION_DAYS", 3650))*24*time.Hour,
	)
	if err != nil {
		s.logger.Error("finish phone login failed", "phone_mask", maskPhone(phone), "legacyUserId", normalizeUserID(legacyUserID), "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"user_id":    result.UserID,
		"phone_mask": result.PhoneMask,
		"token":      result.Token,
		"expires_at": result.ExpiresAt,
	})
}

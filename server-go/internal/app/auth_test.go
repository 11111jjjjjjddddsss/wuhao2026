package app

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"
)

func TestResolveAuthUserIDAllowsHeaderWhenNotStrict(t *testing.T) {
	t.Setenv("AUTH_STRICT", "")
	t.Setenv("APP_SECRET", "")

	req := httptest.NewRequest("GET", "/api/me", nil)
	req.Header.Set("X-User-Id", "u-header")

	auth := ResolveAuthUserID(req)
	if auth.UserID != "u-header" || auth.AuthMode != AuthModeHeader {
		t.Fatalf("header auth mismatch: user=%q mode=%s", auth.UserID, auth.AuthMode)
	}
}

func TestResolveAuthUserIDRejectsHeaderWhenStrict(t *testing.T) {
	t.Setenv("AUTH_STRICT", "true")
	t.Setenv("APP_SECRET", "")

	req := httptest.NewRequest("GET", "/api/me", nil)
	req.Header.Set("X-User-Id", "u-header")

	auth := ResolveAuthUserID(req)
	if auth.AuthMode != AuthModeUnauthorized {
		t.Fatalf("strict mode should reject header auth, got mode=%s user=%q", auth.AuthMode, auth.UserID)
	}
}

func TestResolveAuthUserIDRejectsLegacyBearerTokenWhenStrict(t *testing.T) {
	secret := "test-secret"
	t.Setenv("AUTH_STRICT", "true")
	t.Setenv("APP_SECRET", secret)

	req := httptest.NewRequest("GET", "/api/me", nil)
	req.Header.Set("X-User-Id", "u-header")
	req.Header.Set("Authorization", "Bearer "+makeAuthTestToken("u-token", secret))

	auth := ResolveAuthUserID(req)
	if auth.AuthMode != AuthModeUnauthorized {
		t.Fatalf("strict mode should reject legacy token, got user=%q mode=%s", auth.UserID, auth.AuthMode)
	}
}

func TestResolveAuthUserIDAllowsLegacyBearerTokenWhenExplicitlyEnabled(t *testing.T) {
	secret := "test-secret"
	userID := "u-token"
	t.Setenv("AUTH_STRICT", "true")
	t.Setenv("AUTH_ALLOW_LEGACY_TOKEN", "true")
	t.Setenv("APP_SECRET", secret)

	req := httptest.NewRequest("GET", "/api/me", nil)
	req.Header.Set("Authorization", "Bearer "+makeAuthTestToken(userID, secret))

	auth := ResolveAuthUserID(req)
	if auth.UserID != userID || auth.AuthMode != AuthModeToken || auth.SessionID != "" {
		t.Fatalf("legacy token auth mismatch: user=%q session=%q mode=%s", auth.UserID, auth.SessionID, auth.AuthMode)
	}
}

func TestResolveAuthUserIDAllowsV2BearerTokenWhenStrict(t *testing.T) {
	secret := "test-secret"
	userID := "acct_test"
	sessionID := "session_test"
	t.Setenv("AUTH_STRICT", "true")
	t.Setenv("APP_SECRET", secret)

	token, _, err := issueAuthToken(userID, sessionID, time.Now(), time.Hour, secret)
	if err != nil {
		t.Fatalf("issueAuthToken failed: %v", err)
	}
	req := httptest.NewRequest("GET", "/api/me", nil)
	req.Header.Set("X-User-Id", "u-header")
	req.Header.Set("Authorization", "Bearer "+token)

	auth := ResolveAuthUserID(req)
	if auth.UserID != userID || auth.SessionID != sessionID || auth.AuthMode != AuthModeToken {
		t.Fatalf("v2 token auth mismatch: user=%q session=%q mode=%s", auth.UserID, auth.SessionID, auth.AuthMode)
	}
}

func TestVerifyV2TokenRejectsExpiredToken(t *testing.T) {
	secret := "test-secret"
	token, _, err := issueAuthToken("acct_test", "session_test", time.Now().Add(-2*time.Hour), time.Hour, secret)
	if err != nil {
		t.Fatalf("issueAuthToken failed: %v", err)
	}
	if userID, sessionID, ok := verifyBearerToken(token, secret); ok || userID != "" || sessionID != "" {
		t.Fatalf("expired token should be rejected, user=%q session=%q ok=%v", userID, sessionID, ok)
	}
}

func TestAuthRateLimitKeyHashesSensitiveInputs(t *testing.T) {
	t.Setenv("APP_SECRET", "test-secret")

	key := authRateLimitKey("sms_send", "13800138000", "203.0.113.8")
	if key == "" || strings.Contains(key, "13800138000") || strings.Contains(key, "203.0.113.8") {
		t.Fatalf("authRateLimitKey leaked sensitive input: %q", key)
	}
	if !strings.HasPrefix(key, "sms_send:") {
		t.Fatalf("authRateLimitKey prefix mismatch: %q", key)
	}

	ipKey := authIPRateLimitKey("fusion_token", "203.0.113.8")
	if ipKey == "" || strings.Contains(ipKey, "203.0.113.8") {
		t.Fatalf("authIPRateLimitKey leaked sensitive input: %q", ipKey)
	}
	if !strings.HasPrefix(ipKey, "fusion_token:") {
		t.Fatalf("authIPRateLimitKey prefix mismatch: %q", ipKey)
	}
}

func TestAuthSMSLimitersUseSeparateEnv(t *testing.T) {
	t.Setenv("AUTH_FUSION_TOKEN_RATE_LIMIT_WINDOW_SECONDS", "30")
	t.Setenv("AUTH_FUSION_TOKEN_RATE_LIMIT_MAX_HITS", "2")
	t.Setenv("AUTH_FUSION_TOKEN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "45")
	t.Setenv("AUTH_FUSION_LOGIN_RATE_LIMIT_WINDOW_SECONDS", "35")
	t.Setenv("AUTH_FUSION_LOGIN_RATE_LIMIT_MAX_HITS", "5")
	t.Setenv("AUTH_FUSION_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "55")
	t.Setenv("AUTH_SMS_RATE_LIMIT_WINDOW_SECONDS", "60")
	t.Setenv("AUTH_SMS_RATE_LIMIT_MAX_HITS", "3")
	t.Setenv("AUTH_SMS_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "90")
	t.Setenv("AUTH_SMS_IP_RATE_LIMIT_WINDOW_SECONDS", "70")
	t.Setenv("AUTH_SMS_IP_RATE_LIMIT_MAX_HITS", "6")
	t.Setenv("AUTH_SMS_IP_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "95")
	t.Setenv("AUTH_SMS_LOGIN_RATE_LIMIT_WINDOW_SECONDS", "120")
	t.Setenv("AUTH_SMS_LOGIN_RATE_LIMIT_MAX_HITS", "4")
	t.Setenv("AUTH_SMS_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "150")

	fusionLimiter, ok := newAuthFusionTokenRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newAuthFusionTokenRateLimiter returned %T, want *chatRateLimiter fallback", newAuthFusionTokenRateLimiter(nil))
	}
	if fusionLimiter.window != 30*time.Second || fusionLimiter.maxHits != 2 || fusionLimiter.pruneInterval != 45*time.Second {
		t.Fatalf("fusion limiter config mismatch: window=%s max=%d prune=%s", fusionLimiter.window, fusionLimiter.maxHits, fusionLimiter.pruneInterval)
	}

	fusionLoginLimiter, ok := newAuthFusionLoginRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newAuthFusionLoginRateLimiter returned %T, want *chatRateLimiter fallback", newAuthFusionLoginRateLimiter(nil))
	}
	if fusionLoginLimiter.window != 35*time.Second || fusionLoginLimiter.maxHits != 5 || fusionLoginLimiter.pruneInterval != 55*time.Second {
		t.Fatalf("fusion login limiter config mismatch: window=%s max=%d prune=%s", fusionLoginLimiter.window, fusionLoginLimiter.maxHits, fusionLoginLimiter.pruneInterval)
	}

	sendLimiter, ok := newAuthSMSRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newAuthSMSRateLimiter returned %T, want *chatRateLimiter fallback", newAuthSMSRateLimiter(nil))
	}
	if sendLimiter.window != time.Minute || sendLimiter.maxHits != 3 || sendLimiter.pruneInterval != 90*time.Second {
		t.Fatalf("send limiter config mismatch: window=%s max=%d prune=%s", sendLimiter.window, sendLimiter.maxHits, sendLimiter.pruneInterval)
	}

	sendIPLimiter, ok := newAuthSMSIPRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newAuthSMSIPRateLimiter returned %T, want *chatRateLimiter fallback", newAuthSMSIPRateLimiter(nil))
	}
	if sendIPLimiter.window != 70*time.Second || sendIPLimiter.maxHits != 6 || sendIPLimiter.pruneInterval != 95*time.Second {
		t.Fatalf("send IP limiter config mismatch: window=%s max=%d prune=%s", sendIPLimiter.window, sendIPLimiter.maxHits, sendIPLimiter.pruneInterval)
	}

	loginLimiter, ok := newAuthSMSLoginRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newAuthSMSLoginRateLimiter returned %T, want *chatRateLimiter fallback", newAuthSMSLoginRateLimiter(nil))
	}
	if loginLimiter.window != 2*time.Minute || loginLimiter.maxHits != 4 || loginLimiter.pruneInterval != 150*time.Second {
		t.Fatalf("login limiter config mismatch: window=%s max=%d prune=%s", loginLimiter.window, loginLimiter.maxHits, loginLimiter.pruneInterval)
	}
}

func TestGetClientIPTrustsProxyHeadersOnlyFromTrustedRemote(t *testing.T) {
	req := httptest.NewRequest("GET", "/api/me", nil)
	req.RemoteAddr = "127.0.0.1:53000"
	req.Header.Set("X-Real-IP", "203.0.113.8")
	req.Header.Set("X-Forwarded-For", "198.51.100.9, 203.0.113.8")
	if got := GetClientIP(req); got != "203.0.113.8" {
		t.Fatalf("trusted proxy client IP=%q, want 203.0.113.8", got)
	}

	untrusted := httptest.NewRequest("GET", "/api/me", nil)
	untrusted.RemoteAddr = "198.51.100.10:53000"
	untrusted.Header.Set("X-Real-IP", "203.0.113.8")
	if got := GetClientIP(untrusted); got != "198.51.100.10" {
		t.Fatalf("untrusted remote should ignore proxy headers, got %q", got)
	}
}

func TestGetClientIPUsesRightmostForwardedForFromTrustedProxy(t *testing.T) {
	req := httptest.NewRequest("GET", "/api/me", nil)
	req.RemoteAddr = "127.0.0.1:53000"
	req.Header.Set("X-Forwarded-For", "198.51.100.200, 203.0.113.8")
	if got := GetClientIP(req); got != "203.0.113.8" {
		t.Fatalf("forwarded IP=%q, want rightmost trusted-proxy value", got)
	}
}

func makeAuthTestToken(userID string, secret string) string {
	ts := strconv.FormatInt(time.Now().Unix(), 10)
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(userID + ":" + ts))
	signature := hex.EncodeToString(mac.Sum(nil))
	return base64.StdEncoding.EncodeToString([]byte(userID + ":" + ts + ":" + signature))
}

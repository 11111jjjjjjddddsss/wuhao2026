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

func TestResolveAuthUserIDAllowsBearerTokenWhenStrict(t *testing.T) {
	secret := "test-secret"
	userID := "u-token"
	t.Setenv("AUTH_STRICT", "true")
	t.Setenv("APP_SECRET", secret)

	req := httptest.NewRequest("GET", "/api/me", nil)
	req.Header.Set("X-User-Id", "u-header")
	req.Header.Set("Authorization", "Bearer "+makeAuthTestToken(userID, secret))

	auth := ResolveAuthUserID(req)
	if auth.UserID != userID || auth.AuthMode != AuthModeToken {
		t.Fatalf("token auth mismatch: user=%q mode=%s", auth.UserID, auth.AuthMode)
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
}

func TestAuthSMSLimitersUseSeparateEnv(t *testing.T) {
	t.Setenv("AUTH_SMS_RATE_LIMIT_WINDOW_SECONDS", "60")
	t.Setenv("AUTH_SMS_RATE_LIMIT_MAX_HITS", "3")
	t.Setenv("AUTH_SMS_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "90")
	t.Setenv("AUTH_SMS_LOGIN_RATE_LIMIT_WINDOW_SECONDS", "120")
	t.Setenv("AUTH_SMS_LOGIN_RATE_LIMIT_MAX_HITS", "4")
	t.Setenv("AUTH_SMS_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "150")

	sendLimiter, ok := newAuthSMSRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newAuthSMSRateLimiter returned %T, want *chatRateLimiter fallback", newAuthSMSRateLimiter(nil))
	}
	if sendLimiter.window != time.Minute || sendLimiter.maxHits != 3 || sendLimiter.pruneInterval != 90*time.Second {
		t.Fatalf("send limiter config mismatch: window=%s max=%d prune=%s", sendLimiter.window, sendLimiter.maxHits, sendLimiter.pruneInterval)
	}

	loginLimiter, ok := newAuthSMSLoginRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newAuthSMSLoginRateLimiter returned %T, want *chatRateLimiter fallback", newAuthSMSLoginRateLimiter(nil))
	}
	if loginLimiter.window != 2*time.Minute || loginLimiter.maxHits != 4 || loginLimiter.pruneInterval != 150*time.Second {
		t.Fatalf("login limiter config mismatch: window=%s max=%d prune=%s", loginLimiter.window, loginLimiter.maxHits, loginLimiter.pruneInterval)
	}
}

func makeAuthTestToken(userID string, secret string) string {
	ts := strconv.FormatInt(time.Now().Unix(), 10)
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(userID + ":" + ts))
	signature := hex.EncodeToString(mac.Sum(nil))
	return base64.StdEncoding.EncodeToString([]byte(userID + ":" + ts + ":" + signature))
}

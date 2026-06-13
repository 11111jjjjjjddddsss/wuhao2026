package app

import (
	"strings"
	"testing"
	"time"
)

func TestAdminLoginRateLimiterUsesEnv(t *testing.T) {
	t.Setenv("ADMIN_LOGIN_RATE_LIMIT_WINDOW_SECONDS", "30")
	t.Setenv("ADMIN_LOGIN_RATE_LIMIT_MAX_HITS", "3")
	t.Setenv("ADMIN_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "90")

	limiter, ok := newAdminLoginRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newAdminLoginRateLimiter returned %T, want *chatRateLimiter fallback", newAdminLoginRateLimiter(nil))
	}
	if limiter.window != 30*time.Second || limiter.maxHits != 3 || limiter.pruneInterval != 90*time.Second {
		t.Fatalf("admin login limiter config mismatch: window=%s max=%d prune=%s", limiter.window, limiter.maxHits, limiter.pruneInterval)
	}
}

func TestAdminLoginRateLimitKeyHashesUsernameAndIP(t *testing.T) {
	t.Setenv("APP_SECRET", "test-secret")

	key := adminLoginRateLimitKey("Owner@Example.COM", "203.0.113.9")
	if key == "" || !strings.HasPrefix(key, "admin_login:") {
		t.Fatalf("admin login key prefix mismatch: %q", key)
	}
	if strings.Contains(key, "Owner") || strings.Contains(key, "owner") || strings.Contains(key, "203.0.113.9") {
		t.Fatalf("admin login key leaked raw username or ip: %q", key)
	}
	if got := adminLoginRateLimitKey("owner@example.com", "203.0.113.9"); got != key {
		t.Fatalf("admin login key should normalize username: %q != %q", got, key)
	}
}

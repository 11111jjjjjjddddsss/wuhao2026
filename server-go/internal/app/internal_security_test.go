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

func TestInternalProbeRateLimiterUsesTighterEnv(t *testing.T) {
	t.Setenv("INTERNAL_PROBE_RATE_LIMIT_WINDOW_SECONDS", "45")
	t.Setenv("INTERNAL_PROBE_RATE_LIMIT_MAX_HITS", "2")
	t.Setenv("INTERNAL_PROBE_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "120")

	limiter, ok := newInternalProbeRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newInternalProbeRateLimiter returned %T, want *chatRateLimiter fallback", newInternalProbeRateLimiter(nil))
	}
	if limiter.window != 45*time.Second || limiter.maxHits != 2 || limiter.pruneInterval != 120*time.Second {
		t.Fatalf("internal probe limiter config mismatch: window=%s max=%d prune=%s", limiter.window, limiter.maxHits, limiter.pruneInterval)
	}
}

func TestTodayAgriItemSaveRateLimiterUsesEnv(t *testing.T) {
	t.Setenv("TODAY_AGRI_ITEM_RATE_LIMIT_WINDOW_SECONDS", "60")
	t.Setenv("TODAY_AGRI_ITEM_RATE_LIMIT_MAX_HITS", "4")
	t.Setenv("TODAY_AGRI_ITEM_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "180")

	limiter, ok := newTodayAgriItemSaveRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newTodayAgriItemSaveRateLimiter returned %T, want *chatRateLimiter fallback", newTodayAgriItemSaveRateLimiter(nil))
	}
	if limiter.window != 60*time.Second || limiter.maxHits != 4 || limiter.pruneInterval != 180*time.Second {
		t.Fatalf("today agri item limiter config mismatch: window=%s max=%d prune=%s", limiter.window, limiter.maxHits, limiter.pruneInterval)
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

func TestTodayAgriItemSaveRateLimitKeyHashesUserAndIP(t *testing.T) {
	t.Setenv("APP_SECRET", "test-secret")

	key := todayAgriItemSaveRateLimitKey("acct_123", "203.0.113.9")
	if key == "" || !strings.HasPrefix(key, "today_agri_item:") {
		t.Fatalf("today agri item key prefix mismatch: %q", key)
	}
	if strings.Contains(key, "acct_123") || strings.Contains(key, "203.0.113.9") {
		t.Fatalf("today agri item key leaked raw user or ip: %q", key)
	}
}

package app

import (
	"strings"
	"testing"
	"time"
)

func TestUploadRateLimitKeyHashesSensitiveInputs(t *testing.T) {
	t.Setenv("APP_SECRET", "test-secret")
	key := uploadRateLimitKey("acct_sensitive_user", "203.0.113.9")
	if key == "" || strings.Contains(key, "acct_sensitive_user") || strings.Contains(key, "203.0.113.9") {
		t.Fatalf("uploadRateLimitKey leaked sensitive input: %q", key)
	}
	if !strings.HasPrefix(key, "upload:") {
		t.Fatalf("uploadRateLimitKey prefix mismatch: %q", key)
	}
}

func TestUploadRateLimiterUsesEnv(t *testing.T) {
	t.Setenv("UPLOAD_RATE_LIMIT_WINDOW_SECONDS", "30")
	t.Setenv("UPLOAD_RATE_LIMIT_MAX_HITS", "2")
	t.Setenv("UPLOAD_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "45")

	limiter, ok := newUploadRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newUploadRateLimiter returned %T, want *chatRateLimiter fallback", newUploadRateLimiter(nil))
	}
	if limiter.window != 30*time.Second || limiter.maxHits != 2 || limiter.pruneInterval != 45*time.Second {
		t.Fatalf("upload limiter config mismatch: window=%s max=%d prune=%s", limiter.window, limiter.maxHits, limiter.pruneInterval)
	}
}

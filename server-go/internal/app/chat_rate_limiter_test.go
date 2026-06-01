package app

import (
	"strings"
	"testing"
	"time"
)

func TestChatRateLimiterRejectsAfterConfiguredHits(t *testing.T) {
	limiter := newChatRateLimiterWithConfig(rateLimitConfig{
		Window:        time.Minute,
		MaxHits:       2,
		PruneInterval: time.Minute,
	})
	now := time.Unix(1000, 0)

	if allowed, retryAfter := limiter.Consume("user-a", now); !allowed || retryAfter != 0 {
		t.Fatalf("first consume allowed=%v retryAfter=%d, want allowed", allowed, retryAfter)
	}
	if allowed, retryAfter := limiter.Consume("user-a", now.Add(time.Second)); !allowed || retryAfter != 0 {
		t.Fatalf("second consume allowed=%v retryAfter=%d, want allowed", allowed, retryAfter)
	}
	allowed, retryAfter := limiter.Consume("user-a", now.Add(2*time.Second))
	if allowed {
		t.Fatalf("third consume allowed, want rejected")
	}
	if retryAfter <= 0 {
		t.Fatalf("retryAfter=%d, want positive", retryAfter)
	}
}

func TestChatRateLimiterAllowsAfterWindow(t *testing.T) {
	limiter := newChatRateLimiterWithConfig(rateLimitConfig{
		Window:        time.Second,
		MaxHits:       1,
		PruneInterval: time.Minute,
	})
	now := time.Unix(1000, 0)

	if allowed, _ := limiter.Consume("user-a", now); !allowed {
		t.Fatalf("first consume rejected, want allowed")
	}
	if allowed, _ := limiter.Consume("user-a", now.Add(500*time.Millisecond)); allowed {
		t.Fatalf("consume inside window allowed, want rejected")
	}
	if allowed, retryAfter := limiter.Consume("user-a", now.Add(2*time.Second)); !allowed || retryAfter != 0 {
		t.Fatalf("consume after window allowed=%v retryAfter=%d, want allowed", allowed, retryAfter)
	}
}

func TestChatRateLimiterPrunesIdleBuckets(t *testing.T) {
	limiter := newChatRateLimiterWithConfig(rateLimitConfig{
		Window:        time.Second,
		MaxHits:       2,
		PruneInterval: time.Second,
	})
	now := time.Unix(1000, 0)

	limiter.Consume("user-a", now)
	limiter.Consume("user-b", now)
	limiter.Consume("user-a", now.Add(2*time.Second))

	if _, ok := limiter.buckets["user-b"]; ok {
		t.Fatalf("idle bucket was not pruned")
	}
	if _, ok := limiter.buckets["user-a"]; !ok {
		t.Fatalf("active bucket was pruned")
	}
}

func TestResolveChatRateLimitConfigUsesEnvAndFallbacks(t *testing.T) {
	t.Setenv("CHAT_RATE_LIMIT_WINDOW_SECONDS", "30")
	t.Setenv("CHAT_RATE_LIMIT_MAX_HITS", "7")
	t.Setenv("CHAT_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "90")

	config := resolveChatRateLimitConfig()
	if config.Window != 30*time.Second {
		t.Fatalf("Window=%s, want 30s", config.Window)
	}
	if config.MaxHits != 7 {
		t.Fatalf("MaxHits=%d, want 7", config.MaxHits)
	}
	if config.PruneInterval != 90*time.Second {
		t.Fatalf("PruneInterval=%s, want 90s", config.PruneInterval)
	}

	t.Setenv("CHAT_RATE_LIMIT_WINDOW_SECONDS", "0")
	t.Setenv("CHAT_RATE_LIMIT_MAX_HITS", "0")
	t.Setenv("CHAT_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "0")
	config = resolveChatRateLimitConfig()
	if config.Window != defaultChatRateLimitWindow {
		t.Fatalf("Window=%s, want default %s", config.Window, defaultChatRateLimitWindow)
	}
	if config.MaxHits != defaultChatRateLimitMaxHits {
		t.Fatalf("MaxHits=%d, want default %d", config.MaxHits, defaultChatRateLimitMaxHits)
	}
	if config.PruneInterval != defaultChatRateLimitPruneInterval {
		t.Fatalf("PruneInterval=%s, want default %s", config.PruneInterval, defaultChatRateLimitPruneInterval)
	}
}

func TestNewChatRateLimiterFallsBackToProcessLimiter(t *testing.T) {
	limiter, ok := newChatRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newChatRateLimiter(nil) returned %T, want *chatRateLimiter fallback", newChatRateLimiter(nil))
	}
	if limiter.window != defaultChatRateLimitWindow || limiter.maxHits != defaultChatRateLimitMaxHits {
		t.Fatalf("fallback limiter config mismatch: window=%s max=%d", limiter.window, limiter.maxHits)
	}
}

func TestChatRateLimitKeyHashesUserID(t *testing.T) {
	t.Setenv("APP_SECRET", "test-secret")

	key := chatRateLimitKey("acct_user_123")
	if key == "" || strings.Contains(key, "acct_user_123") {
		t.Fatalf("chatRateLimitKey leaked user id: %q", key)
	}
	if !strings.HasPrefix(key, "chat:") {
		t.Fatalf("chatRateLimitKey prefix mismatch: %q", key)
	}
}

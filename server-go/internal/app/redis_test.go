package app

import (
	"testing"
	"time"

	"github.com/redis/go-redis/v9"
)

func TestResolveRedisConfigFromEnvMissing(t *testing.T) {
	t.Setenv("REDIS_URL", "")
	t.Setenv("REDIS_ADDR", "")

	_, ok, err := resolveRedisConfigFromEnv()
	if err != nil {
		t.Fatalf("resolveRedisConfigFromEnv returned err=%v", err)
	}
	if ok {
		t.Fatalf("resolveRedisConfigFromEnv ok=true, want false")
	}
}

func TestResolveRedisConfigFromAddr(t *testing.T) {
	t.Setenv("REDIS_URL", "")
	t.Setenv("REDIS_ADDR", "redis.internal")
	t.Setenv("REDIS_USERNAME", "default")
	t.Setenv("REDIS_PASSWORD", "secret")
	t.Setenv("REDIS_DB", "2")

	config, ok, err := resolveRedisConfigFromEnv()
	if err != nil {
		t.Fatalf("resolveRedisConfigFromEnv returned err=%v", err)
	}
	if !ok {
		t.Fatalf("resolveRedisConfigFromEnv ok=false, want true")
	}
	if config.Addr != "redis.internal:6379" {
		t.Fatalf("Addr=%q, want host with default port", config.Addr)
	}
	if config.Username != "default" || config.Password != "secret" || config.DB != 2 {
		t.Fatalf("unexpected config: %#v", config)
	}
}

func TestParseRedisURL(t *testing.T) {
	config, err := parseRedisURL("redis://user:pass@redis.internal:6379/3")
	if err != nil {
		t.Fatalf("parseRedisURL returned err=%v", err)
	}
	if config.Addr != "redis.internal:6379" || config.Username != "user" || config.Password != "pass" || config.DB != 3 {
		t.Fatalf("unexpected config: %#v", config)
	}
}

func TestRateLimitHashDoesNotExposeInput(t *testing.T) {
	got := rateLimitHash("13800138000", "secret")
	if got == "" || got == "13800138000" {
		t.Fatalf("rateLimitHash returned unsafe value %q", got)
	}
	if got != rateLimitHash("13800138000", "secret") {
		t.Fatalf("rateLimitHash should be stable")
	}
}

func TestRedisRateLimiterFailsClosedByDefault(t *testing.T) {
	client := redis.NewClient(&redis.Options{
		Addr:         "127.0.0.1:1",
		DialTimeout:  10 * time.Millisecond,
		ReadTimeout:  10 * time.Millisecond,
		WriteTimeout: 10 * time.Millisecond,
	})
	defer client.Close()

	limiter := newRedisRateLimiter(client, rateLimitConfig{Window: time.Second, MaxHits: 1}, redisRateLimitPrefix, time.Second, 1)
	allowed, _ := limiter.Consume("test", time.Now())
	if allowed {
		t.Fatalf("security-sensitive redis limiter should fail closed on redis error")
	}
}

func TestRedisRateLimiterFailOpenAllowsOnRedisError(t *testing.T) {
	client := redis.NewClient(&redis.Options{
		Addr:         "127.0.0.1:1",
		DialTimeout:  10 * time.Millisecond,
		ReadTimeout:  10 * time.Millisecond,
		WriteTimeout: 10 * time.Millisecond,
	})
	defer client.Close()

	limiter := newRedisRateLimiterFailOpen(client, rateLimitConfig{Window: time.Second, MaxHits: 1}, redisRateLimitPrefix, time.Second, 1)
	allowed, retryAfter := limiter.Consume("test", time.Now())
	if !allowed || retryAfter != 0 {
		t.Fatalf("non-critical redis limiter should fail open on redis error: allowed=%v retryAfter=%d", allowed, retryAfter)
	}
}

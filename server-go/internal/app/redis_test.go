package app

import "testing"

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

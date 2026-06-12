package app

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log/slog"
	"net"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
)

const (
	defaultRedisDialTimeout = 3 * time.Second
	defaultRedisPingTimeout = 3 * time.Second
	redisRateLimitPrefix    = "nj:rl:"
	redisRateLimitScript    = `
local count = redis.call("INCR", KEYS[1])
if count == 1 then
  redis.call("PEXPIRE", KEYS[1], ARGV[1])
end
local ttl = redis.call("PTTL", KEYS[1])
return {count, ttl}
`
)

type redisConfig struct {
	Addr     string
	Username string
	Password string
	DB       int
}

func newOptionalRedisClient(ctx context.Context, logger *slog.Logger) (*redis.Client, error) {
	config, ok, err := resolveRedisConfigFromEnv()
	if err != nil {
		return nil, err
	}
	if !ok {
		return nil, nil
	}
	client := redis.NewClient(&redis.Options{
		Addr:         config.Addr,
		Username:     config.Username,
		Password:     config.Password,
		DB:           config.DB,
		DialTimeout:  envDurationWithDefault("REDIS_DIAL_TIMEOUT_SECONDS", defaultRedisDialTimeout),
		ReadTimeout:  envDurationWithDefault("REDIS_READ_TIMEOUT_SECONDS", defaultRedisDialTimeout),
		WriteTimeout: envDurationWithDefault("REDIS_WRITE_TIMEOUT_SECONDS", defaultRedisDialTimeout),
	})
	pingCtx, cancel := context.WithTimeout(ctx, envDurationWithDefault("REDIS_PING_TIMEOUT_SECONDS", defaultRedisPingTimeout))
	defer cancel()
	if err := client.Ping(pingCtx).Err(); err != nil {
		_ = client.Close()
		return nil, fmt.Errorf("redis ping failed: %w", err)
	}
	if logger != nil {
		logger.Info("redis client ready", "addr", config.Addr, "db", config.DB)
	}
	return client, nil
}

func redisHealthStatus(ctx context.Context, client *redis.Client) string {
	if client == nil {
		return "missing_config"
	}
	if ctx == nil {
		ctx = context.Background()
	}
	pingCtx, cancel := context.WithTimeout(ctx, envDurationWithDefault("REDIS_HEALTH_TIMEOUT_SECONDS", defaultRedisPingTimeout))
	defer cancel()
	if err := client.Ping(pingCtx).Err(); err != nil {
		return "error"
	}
	return "ok"
}

func resolveRedisConfigFromEnv() (redisConfig, bool, error) {
	rawURL := strings.TrimSpace(os.Getenv("REDIS_URL"))
	if rawURL != "" {
		config, err := parseRedisURL(rawURL)
		if err != nil {
			return redisConfig{}, false, err
		}
		return config, true, nil
	}

	addr := strings.TrimSpace(os.Getenv("REDIS_ADDR"))
	if addr == "" {
		return redisConfig{}, false, nil
	}
	if !strings.Contains(addr, ":") {
		addr = net.JoinHostPort(addr, "6379")
	}
	db, err := parseRedisDB(strings.TrimSpace(os.Getenv("REDIS_DB")))
	if err != nil {
		return redisConfig{}, false, err
	}
	return redisConfig{
		Addr:     addr,
		Username: strings.TrimSpace(os.Getenv("REDIS_USERNAME")),
		Password: strings.TrimSpace(os.Getenv("REDIS_PASSWORD")),
		DB:       db,
	}, true, nil
}

func parseRedisURL(raw string) (redisConfig, error) {
	parsed, err := url.Parse(raw)
	if err != nil {
		return redisConfig{}, fmt.Errorf("invalid REDIS_URL: %w", err)
	}
	if parsed.Scheme != "redis" {
		return redisConfig{}, fmt.Errorf("invalid REDIS_URL scheme")
	}
	if parsed.Host == "" {
		return redisConfig{}, fmt.Errorf("REDIS_URL host is missing")
	}
	password, _ := parsed.User.Password()
	username := parsed.User.Username()
	db := 0
	if path := strings.Trim(parsed.Path, "/"); path != "" {
		parsedDB, err := parseRedisDB(path)
		if err != nil {
			return redisConfig{}, err
		}
		db = parsedDB
	}
	return redisConfig{
		Addr:     parsed.Host,
		Username: username,
		Password: password,
		DB:       db,
	}, nil
}

func parseRedisDB(raw string) (int, error) {
	if strings.TrimSpace(raw) == "" {
		return 0, nil
	}
	db, err := strconv.Atoi(strings.TrimSpace(raw))
	if err != nil || db < 0 {
		return 0, fmt.Errorf("invalid REDIS_DB")
	}
	return db, nil
}

type redisRateLimiter struct {
	client          *redis.Client
	prefix          string
	window          time.Duration
	maxHits         int
	failOpenOnError bool
}

func newRedisRateLimiter(client *redis.Client, config rateLimitConfig, prefix string, fallbackWindow time.Duration, fallbackMaxHits int) *redisRateLimiter {
	return newRedisRateLimiterWithFailureMode(client, config, prefix, fallbackWindow, fallbackMaxHits, false)
}

func newRedisRateLimiterFailOpen(client *redis.Client, config rateLimitConfig, prefix string, fallbackWindow time.Duration, fallbackMaxHits int) *redisRateLimiter {
	return newRedisRateLimiterWithFailureMode(client, config, prefix, fallbackWindow, fallbackMaxHits, true)
}

func newRedisRateLimiterWithFailureMode(client *redis.Client, config rateLimitConfig, prefix string, fallbackWindow time.Duration, fallbackMaxHits int, failOpenOnError bool) *redisRateLimiter {
	if client == nil {
		return nil
	}
	config = normalizeRateLimitConfig(config, fallbackWindow, fallbackMaxHits, fallbackWindow)
	prefix = strings.TrimSpace(prefix)
	if prefix == "" {
		prefix = redisRateLimitPrefix
	}
	return &redisRateLimiter{
		client:          client,
		prefix:          prefix,
		window:          config.Window,
		maxHits:         config.MaxHits,
		failOpenOnError: failOpenOnError,
	}
}

func (l *redisRateLimiter) Consume(key string, now time.Time) (bool, int) {
	if l == nil || l.client == nil {
		return true, 0
	}
	ctx, cancel := context.WithTimeout(context.Background(), envDurationWithDefault("REDIS_RATE_LIMIT_TIMEOUT_SECONDS", time.Second))
	defer cancel()

	redisKey := l.prefix + strings.TrimSpace(key)
	result, err := l.client.Eval(ctx, redisRateLimitScript, []string{redisKey}, int64(l.window/time.Millisecond)).Result()
	if err != nil {
		return l.onRedisRateLimitFailure()
	}
	values, ok := result.([]any)
	if !ok || len(values) != 2 {
		return l.onRedisRateLimitFailure()
	}
	count, ok := values[0].(int64)
	if !ok {
		return l.onRedisRateLimitFailure()
	}
	ttlMs, _ := values[1].(int64)
	if count > int64(l.maxHits) {
		if ttlMs <= 0 {
			return false, maxInt(1, int(l.window.Seconds()))
		}
		return false, maxInt(1, int((time.Duration(ttlMs)*time.Millisecond)/time.Second)+1)
	}
	return true, 0
}

func (l *redisRateLimiter) onRedisRateLimitFailure() (bool, int) {
	if l != nil && l.failOpenOnError {
		return true, 0
	}
	return false, maxInt(1, int(l.window.Seconds()))
}

func rateLimitHash(value string, secret string) string {
	value = strings.TrimSpace(value)
	secret = strings.TrimSpace(secret)
	if value == "" {
		return "unknown"
	}
	if secret == "" {
		sum := sha256.Sum256([]byte(value))
		return hex.EncodeToString(sum[:])
	}
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(value))
	return hex.EncodeToString(mac.Sum(nil))
}

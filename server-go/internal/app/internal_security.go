package app

import (
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
)

const (
	defaultInternalSecretRateLimitWindow        = 10 * time.Minute
	defaultInternalSecretRateLimitMaxHits       = 120
	defaultInternalSecretRateLimitPruneInterval = 10 * time.Minute
)

func newInternalSecretRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("INTERNAL_SECRET_RATE_LIMIT_WINDOW_SECONDS", defaultInternalSecretRateLimitWindow),
		MaxHits:       envIntWithDefault("INTERNAL_SECRET_RATE_LIMIT_MAX_HITS", defaultInternalSecretRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("INTERNAL_SECRET_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultInternalSecretRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultInternalSecretRateLimitWindow, defaultInternalSecretRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(normalizeRateLimitConfig(
		config,
		defaultInternalSecretRateLimitWindow,
		defaultInternalSecretRateLimitMaxHits,
		defaultInternalSecretRateLimitPruneInterval,
	))
}

func (s *Server) consumeInternalSecretRateLimit(w http.ResponseWriter, r *http.Request, scope string) bool {
	if s == nil || s.internalSecretLimiter == nil {
		return true
	}
	allowed, retryAfterSec := s.internalSecretLimiter.Consume(internalSecretRateLimitKey(scope, GetClientIP(r)), time.Now())
	if allowed {
		return true
	}
	w.Header().Set("Retry-After", fmt.Sprintf("%d", retryAfterSec))
	s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
		"error":           "RATE_LIMITED",
		"retry_after_sec": retryAfterSec,
	})
	return false
}

func internalSecretRateLimitKey(scope string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	scope = strings.TrimSpace(scope)
	if scope == "" {
		scope = "internal"
	}
	return "internal_secret:" + scope + ":" + rateLimitHash(ip, secret)
}

package app

import (
	"fmt"
	"net"
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
	defaultInternalProbeRateLimitWindow         = 10 * time.Minute
	defaultInternalProbeRateLimitMaxHits        = 5
	defaultInternalProbeRateLimitPruneInterval  = 10 * time.Minute
	defaultTodayAgriItemRateLimitWindow         = 10 * time.Minute
	defaultTodayAgriItemRateLimitMaxHits        = 30
	defaultTodayAgriItemRateLimitPruneInterval  = 10 * time.Minute
	defaultAdminLoginRateLimitWindow            = 10 * time.Minute
	defaultAdminLoginRateLimitMaxHits           = 10
	defaultAdminLoginRateLimitPruneInterval     = 10 * time.Minute
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

func newInternalProbeRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("INTERNAL_PROBE_RATE_LIMIT_WINDOW_SECONDS", defaultInternalProbeRateLimitWindow),
		MaxHits:       envIntWithDefault("INTERNAL_PROBE_RATE_LIMIT_MAX_HITS", defaultInternalProbeRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("INTERNAL_PROBE_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultInternalProbeRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultInternalProbeRateLimitWindow, defaultInternalProbeRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(normalizeRateLimitConfig(
		config,
		defaultInternalProbeRateLimitWindow,
		defaultInternalProbeRateLimitMaxHits,
		defaultInternalProbeRateLimitPruneInterval,
	))
}

func newTodayAgriItemSaveRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("TODAY_AGRI_ITEM_RATE_LIMIT_WINDOW_SECONDS", defaultTodayAgriItemRateLimitWindow),
		MaxHits:       envIntWithDefault("TODAY_AGRI_ITEM_RATE_LIMIT_MAX_HITS", defaultTodayAgriItemRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("TODAY_AGRI_ITEM_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultTodayAgriItemRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultTodayAgriItemRateLimitWindow, defaultTodayAgriItemRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(normalizeRateLimitConfig(
		config,
		defaultTodayAgriItemRateLimitWindow,
		defaultTodayAgriItemRateLimitMaxHits,
		defaultTodayAgriItemRateLimitPruneInterval,
	))
}

func newAdminLoginRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("ADMIN_LOGIN_RATE_LIMIT_WINDOW_SECONDS", defaultAdminLoginRateLimitWindow),
		MaxHits:       envIntWithDefault("ADMIN_LOGIN_RATE_LIMIT_MAX_HITS", defaultAdminLoginRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("ADMIN_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultAdminLoginRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultAdminLoginRateLimitWindow, defaultAdminLoginRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(normalizeRateLimitConfig(
		config,
		defaultAdminLoginRateLimitWindow,
		defaultAdminLoginRateLimitMaxHits,
		defaultAdminLoginRateLimitPruneInterval,
	))
}

func (s *Server) consumeInternalProbeRateLimit(w http.ResponseWriter, r *http.Request, scope string) bool {
	if s == nil || s.internalProbeLimiter == nil {
		return true
	}
	allowed, retryAfterSec := s.internalProbeLimiter.Consume(internalSecretRateLimitKey("probe:"+scope, internalRequestClientIP(r)), time.Now())
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

func (s *Server) consumeInternalSecretRateLimit(w http.ResponseWriter, r *http.Request, scope string) bool {
	if s == nil || s.internalSecretLimiter == nil {
		return true
	}
	allowed, retryAfterSec := s.internalSecretLimiter.Consume(internalSecretRateLimitKey(scope, internalRequestClientIP(r)), time.Now())
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

func (s *Server) consumeTodayAgriItemSaveRateLimit(w http.ResponseWriter, r *http.Request, userID string) bool {
	if s == nil || s.todayAgriItemLimiter == nil {
		return true
	}
	allowed, retryAfterSec := s.todayAgriItemLimiter.Consume(todayAgriItemSaveRateLimitKey(userID, GetClientIP(r)), time.Now())
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

func (s *Server) refundTodayAgriItemSaveRateLimit(r *http.Request, userID string) {
	if s == nil || s.todayAgriItemLimiter == nil {
		return
	}
	refundRateLimit(s.todayAgriItemLimiter, todayAgriItemSaveRateLimitKey(userID, GetClientIP(r)))
}

func isInternalRequestClient(r *http.Request) bool {
	clientIP := normalizeIPLiteral(internalRequestClientIP(r))
	parsed := net.ParseIP(clientIP)
	if parsed == nil {
		return false
	}
	return parsed.IsLoopback() || parsed.IsPrivate()
}

func internalRequestClientIP(r *http.Request) string {
	remoteIP := normalizeIPFromAddress(r.RemoteAddr)
	parsed := net.ParseIP(remoteIP)
	if parsed != nil && parsed.IsLoopback() {
		if realIP := normalizeIPLiteral(r.Header.Get("X-Real-IP")); realIP != "" {
			return realIP
		}
		if forwardedIP := forwardedForClientIP(r.Header.Get("X-Forwarded-For")); forwardedIP != "" {
			return forwardedIP
		}
	}
	return remoteIP
}

func (s *Server) requireInternalRequestClient(w http.ResponseWriter, r *http.Request) bool {
	if isInternalRequestClient(r) {
		return true
	}
	s.writeError(w, http.StatusForbidden, "internal_ip_required")
	return false
}

func (s *Server) requireInternalJobSecret(w http.ResponseWriter, r *http.Request, scope string) bool {
	if !s.requireInternalRequestClient(w, r) {
		return false
	}
	if !s.consumeInternalSecretRateLimit(w, r, scope) {
		return false
	}
	if !validateInternalJobSecret(r) {
		s.writeError(w, http.StatusUnauthorized, "unauthorized")
		return false
	}
	return true
}

func internalSecretRateLimitKey(scope string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	scope = strings.TrimSpace(scope)
	if scope == "" {
		scope = "internal"
	}
	return "internal_secret:" + scope + ":" + rateLimitHash(ip, secret)
}

func todayAgriItemSaveRateLimitKey(userID string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	userID = strings.TrimSpace(userID)
	if userID == "" {
		userID = "anonymous"
	}
	return "today_agri_item:" + rateLimitHash(userID, secret) + ":" + rateLimitHash(ip, secret)
}

func adminLoginRateLimitKey(username string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	username = strings.TrimSpace(strings.ToLower(username))
	if username == "" {
		username = "anonymous"
	}
	return "admin_login:" + rateLimitHash(username, secret) + ":" + rateLimitHash(ip, secret)
}

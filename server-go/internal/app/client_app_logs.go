package app

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"
	"unicode"

	"github.com/redis/go-redis/v9"
)

const (
	clientAppLogMaxBodyBytes  = 8 * 1024
	clientAppLogMaxAttrsBytes = 2048
	clientAppLogMaxAttrsCount = 20

	defaultClientAppLogRateLimitWindow        = 10 * time.Minute
	defaultClientAppLogRateLimitMaxHits       = 60
	defaultClientAppLogRateLimitPruneInterval = 10 * time.Minute
)

type clientAppLogRequest struct {
	Level          string         `json:"level"`
	Event          string         `json:"event"`
	Message        string         `json:"message"`
	Attrs          map[string]any `json:"attrs"`
	Platform       string         `json:"platform"`
	AppVersionCode *int           `json:"app_version_code"`
	AppVersionName string         `json:"app_version_name"`
	OSVersion      string         `json:"os_version"`
	DeviceModel    string         `json:"device_model"`
	ClientTimeMs   *int64         `json:"client_time_ms"`
}

type ClientAppLogInput struct {
	UserID         string
	Level          string
	Event          string
	Message        string
	AttrsJSON      any
	Platform       string
	AppVersionCode *int
	AppVersionName string
	OSVersion      string
	DeviceModel    string
	ClientTimeMs   *int64
	CreatedAt      int64
	MaskedIP       string
}

func (s *Server) handleCreateClientAppLog(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if s.clientAppLogLimiter != nil {
		limitKey := clientAppLogRateLimitKey(auth.UserID, GetClientIP(r))
		if allowed, retryAfter := s.clientAppLogLimiter.Consume(limitKey, time.Now()); !allowed {
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":               "rate_limited",
				"retry_after_seconds": retryAfter,
			})
			return
		}
	}
	r.Body = http.MaxBytesReader(w, r.Body, clientAppLogMaxBodyBytes)
	var body clientAppLogRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	input, validationError := normalizeClientAppLogPayload(auth.UserID, auth.MaskedIP, body, time.Now().UnixMilli())
	if validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if err := s.store.CreateClientAppLog(r.Context(), input); err != nil {
		s.logger.Error("create client app log failed", "userId", auth.UserID, "event", input.Event, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	logAttrs := []any{
		"userId", auth.UserID,
		"event", input.Event,
		"message", input.Message,
		"platform", input.Platform,
		"appVersionCode", input.AppVersionCodeValue(),
	}
	switch input.Level {
	case "error":
		s.logger.Error("client app log", logAttrs...)
	case "warn":
		s.logger.Warn("client app log", logAttrs...)
	default:
		s.logger.Info("client app log", logAttrs...)
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func newClientAppLogRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("CLIENT_APP_LOG_RATE_LIMIT_WINDOW_SECONDS", defaultClientAppLogRateLimitWindow),
		MaxHits:       envIntWithDefault("CLIENT_APP_LOG_RATE_LIMIT_MAX_HITS", defaultClientAppLogRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("CLIENT_APP_LOG_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultClientAppLogRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultClientAppLogRateLimitWindow, defaultClientAppLogRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(config)
}

func clientAppLogRateLimitKey(userID string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	return "client_app_log:" + rateLimitHash(userID, secret) + ":" + rateLimitHash(ip, secret)
}

func normalizeClientAppLogPayload(userID string, maskedIP string, body clientAppLogRequest, createdAt int64) (ClientAppLogInput, string) {
	event := normalizeClientLogIdentifier(body.Event, 96)
	if event == "" {
		return ClientAppLogInput{}, "event required"
	}
	level := strings.ToLower(strings.TrimSpace(body.Level))
	switch level {
	case "info", "warn", "error":
	default:
		return ClientAppLogInput{}, "invalid level"
	}
	message := truncateRunes(strings.TrimSpace(body.Message), 255)
	if message == "" {
		message = event
	}
	platform := normalizeClientLogIdentifier(body.Platform, 32)
	if platform == "" {
		platform = "android"
	}
	attrsJSON, validationError := normalizeClientLogAttrs(body.Attrs)
	if validationError != "" {
		return ClientAppLogInput{}, validationError
	}
	return ClientAppLogInput{
		UserID:         userID,
		Level:          level,
		Event:          event,
		Message:        message,
		AttrsJSON:      attrsJSON,
		Platform:       platform,
		AppVersionCode: body.AppVersionCode,
		AppVersionName: truncateRunes(strings.TrimSpace(body.AppVersionName), 64),
		OSVersion:      truncateRunes(strings.TrimSpace(body.OSVersion), 64),
		DeviceModel:    truncateRunes(strings.TrimSpace(body.DeviceModel), 128),
		ClientTimeMs:   body.ClientTimeMs,
		CreatedAt:      createdAt,
		MaskedIP:       truncateRunes(strings.TrimSpace(maskedIP), 64),
	}, ""
}

func normalizeClientLogAttrs(raw map[string]any) (any, string) {
	if len(raw) == 0 {
		return nil, ""
	}
	normalized := make(map[string]any, minInt(len(raw), clientAppLogMaxAttrsCount))
	count := 0
	for key, value := range raw {
		if count >= clientAppLogMaxAttrsCount {
			break
		}
		normalizedKey := normalizeClientLogIdentifier(key, 64)
		if normalizedKey == "" {
			continue
		}
		normalizedValue, ok := normalizeClientLogAttrValue(value)
		if !ok {
			continue
		}
		normalized[normalizedKey] = normalizedValue
		count++
	}
	if len(normalized) == 0 {
		return nil, ""
	}
	data, err := json.Marshal(normalized)
	if err != nil {
		return nil, "invalid attrs"
	}
	if len(data) > clientAppLogMaxAttrsBytes {
		return nil, "attrs too large"
	}
	return string(data), ""
}

func normalizeClientLogAttrValue(value any) (any, bool) {
	switch v := value.(type) {
	case nil:
		return nil, true
	case bool:
		return v, true
	case float64:
		return v, true
	case float32:
		return v, true
	case int:
		return v, true
	case int64:
		return v, true
	case json.Number:
		return v.String(), true
	case string:
		return truncateRunes(strings.TrimSpace(v), 160), true
	case map[string]any, []any:
		return nil, false
	default:
		return truncateRunes(strings.TrimSpace(fmt.Sprint(v)), 160), true
	}
}

func (input ClientAppLogInput) AppVersionCodeValue() any {
	if input.AppVersionCode == nil {
		return nil
	}
	return *input.AppVersionCode
}

func normalizeClientLogIdentifier(raw string, maxRunes int) string {
	trimmed := strings.TrimSpace(strings.ToLower(raw))
	if trimmed == "" {
		return ""
	}
	var builder strings.Builder
	for _, r := range trimmed {
		if unicode.IsLetter(r) || unicode.IsDigit(r) || r == '_' || r == '-' || r == '.' || r == ':' {
			builder.WriteRune(r)
		}
	}
	return truncateRunes(builder.String(), maxRunes)
}

func truncateRunes(value string, maxRunes int) string {
	runes := []rune(value)
	if maxRunes <= 0 || len(runes) <= maxRunes {
		return value
	}
	return string(runes[:maxRunes])
}

func minInt(left int, right int) int {
	if left < right {
		return left
	}
	return right
}

func (s *Store) CreateClientAppLog(ctx context.Context, input ClientAppLogInput) error {
	var appVersionCode any
	if input.AppVersionCode != nil {
		appVersionCode = *input.AppVersionCode
	}
	var clientTimeMs any
	if input.ClientTimeMs != nil {
		clientTimeMs = *input.ClientTimeMs
	}
	var attrsJSON sql.NullString
	if text, ok := input.AttrsJSON.(string); ok && strings.TrimSpace(text) != "" {
		attrsJSON = sql.NullString{String: text, Valid: true}
	}
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO client_app_logs(
		   user_id,
		   level,
		   event,
		   message,
		   attrs_json,
		   platform,
		   app_version_code,
		   app_version_name,
		   os_version,
		   device_model,
		   client_time_ms,
		   created_at,
		   masked_ip
		 )
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		input.UserID,
		input.Level,
		input.Event,
		input.Message,
		nullableString(attrsJSON),
		input.Platform,
		appVersionCode,
		nullableTrimmed(input.AppVersionName),
		nullableTrimmed(input.OSVersion),
		nullableTrimmed(input.DeviceModel),
		clientTimeMs,
		input.CreatedAt,
		nullableTrimmed(input.MaskedIP),
	)
	return err
}

func nullableString(value sql.NullString) any {
	if !value.Valid || strings.TrimSpace(value.String) == "" {
		return nil
	}
	return value.String
}

func nullableTrimmed(value string) any {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return nil
	}
	return trimmed
}

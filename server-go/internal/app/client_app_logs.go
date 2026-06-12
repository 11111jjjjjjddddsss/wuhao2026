package app

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
	"unicode"

	"github.com/redis/go-redis/v9"
)

const (
	clientAppLogMaxBodyBytes  = 8 * 1024
	clientAppLogMaxAttrsBytes = 2048
	clientAppLogMaxAttrsCount = 20
	clientAppLogPreAuthUserID = "preauth"

	defaultClientAppLogRateLimitWindow        = 10 * time.Minute
	defaultClientAppLogRateLimitMaxHits       = 60
	defaultClientAppLogRateLimitPruneInterval = 10 * time.Minute

	defaultClientAppLogInternalListLimit = 100
	maxClientAppLogInternalListLimit     = 200
	clientAppLogInternalSummaryLimit     = 50
)

type clientAppLogRequest struct {
	Level          string         `json:"level"`
	Event          string         `json:"event"`
	Message        string         `json:"message"`
	Attrs          map[string]any `json:"attrs"`
	Platform       string         `json:"platform"`
	BuildType      string         `json:"build_type"`
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
	BuildType      string
	AppVersionCode *int
	AppVersionName string
	OSVersion      string
	DeviceModel    string
	ClientTimeMs   *int64
	CreatedAt      int64
	MaskedIP       string
}

type ClientAppLogQuery struct {
	UserID         string `json:"user_id,omitempty"`
	Level          string `json:"level,omitempty"`
	Event          string `json:"event,omitempty"`
	EventPrefix    string `json:"event_prefix,omitempty"`
	Platform       string `json:"platform,omitempty"`
	BuildType      string `json:"build_type,omitempty"`
	AppVersionCode *int   `json:"app_version_code,omitempty"`
	AppVersionName string `json:"app_version_name,omitempty"`
	OSVersion      string `json:"os_version,omitempty"`
	DeviceModel    string `json:"device_model,omitempty"`
	SinceMs        int64  `json:"since_ms"`
	Limit          int    `json:"limit"`
}

type ClientAppLogEntry struct {
	ID             int64           `json:"id"`
	UserID         string          `json:"user_id"`
	Level          string          `json:"level"`
	Event          string          `json:"event"`
	Message        string          `json:"message"`
	Attrs          json.RawMessage `json:"attrs,omitempty"`
	Platform       string          `json:"platform"`
	BuildType      string          `json:"build_type,omitempty"`
	AppVersionCode *int            `json:"app_version_code,omitempty"`
	AppVersionName string          `json:"app_version_name,omitempty"`
	OSVersion      string          `json:"os_version,omitempty"`
	DeviceModel    string          `json:"device_model,omitempty"`
	ClientTimeMs   *int64          `json:"client_time_ms,omitempty"`
	CreatedAt      int64           `json:"created_at"`
	MaskedIP       string          `json:"masked_ip,omitempty"`
}

type ClientAppLogSummaryEntry struct {
	Event string `json:"event"`
	Level string `json:"level"`
	Count int64  `json:"count"`
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
		"platform", input.Platform,
		"buildType", input.BuildType,
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

func (s *Server) handleCreatePreAuthClientAppLog(w http.ResponseWriter, r *http.Request) {
	if s.clientAppLogLimiter != nil {
		limitKey := clientAppLogRateLimitKey(clientAppLogPreAuthUserID, GetClientIP(r))
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
	input, validationError := normalizeClientAppLogPayload(clientAppLogPreAuthUserID, maskIP(GetClientIP(r)), body, time.Now().UnixMilli())
	if validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if !strings.HasPrefix(input.Event, "auth.") {
		s.writeError(w, http.StatusBadRequest, "event_not_allowed")
		return
	}
	if err := s.store.CreateClientAppLog(r.Context(), input); err != nil {
		s.logger.Error("create preauth client app log failed", "event", input.Event, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	logAttrs := []any{
		"userId", clientAppLogPreAuthUserID,
		"event", input.Event,
		"platform", input.Platform,
		"buildType", input.BuildType,
		"appVersionCode", input.AppVersionCodeValue(),
	}
	switch input.Level {
	case "error":
		s.logger.Error("preauth client app log", logAttrs...)
	case "warn":
		s.logger.Warn("preauth client app log", logAttrs...)
	default:
		s.logger.Info("preauth client app log", logAttrs...)
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleInternalClientAppLogs(w http.ResponseWriter, r *http.Request) {
	if !s.requireSupportAdminSecret(w, r) {
		return
	}
	filter, validationError := parseClientAppLogQuery(r.URL.Query(), time.Now())
	if validationError != "" {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.app.logs.list", "client_app_logs", "", filter.UserID, false, http.StatusBadRequest, map[string]any{"error_code": validationError})
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	logs, err := s.store.ListClientAppLogs(r.Context(), filter)
	if err != nil {
		s.logger.Error("list client app logs failed", "error", err)
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.app.logs.list", "client_app_logs", "", filter.UserID, false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	summary, err := s.store.SummarizeClientAppLogs(r.Context(), filter)
	if err != nil {
		s.logger.Error("summarize client app logs failed", "error", err)
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.app.logs.list", "client_app_logs", "", filter.UserID, false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, "support_admin_secret", "internal.app.logs.list", "client_app_logs", "", filter.UserID, true, http.StatusOK, map[string]any{
		"event":            filter.Event,
		"event_prefix":     filter.EventPrefix,
		"level":            filter.Level,
		"platform":         filter.Platform,
		"build_type":       filter.BuildType,
		"app_version_code": filter.AppVersionCode,
		"app_version_name": filter.AppVersionName,
		"os_version":       filter.OSVersion,
		"device_model":     filter.DeviceModel,
		"limit":            filter.Limit,
		"since_ms":         filter.SinceMs,
		"row_count":        len(logs),
		"summary_count":    len(summary),
	})
	s.writeJSON(w, http.StatusOK, map[string]any{
		"logs":    logs,
		"summary": summary,
		"filter":  filter,
	})
}

func newClientAppLogRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("CLIENT_APP_LOG_RATE_LIMIT_WINDOW_SECONDS", defaultClientAppLogRateLimitWindow),
		MaxHits:       envIntWithDefault("CLIENT_APP_LOG_RATE_LIMIT_MAX_HITS", defaultClientAppLogRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("CLIENT_APP_LOG_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultClientAppLogRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiterFailOpen(redisClient, config, redisRateLimitPrefix, defaultClientAppLogRateLimitWindow, defaultClientAppLogRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(config)
}

func clientAppLogRateLimitKey(userID string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	return "client_app_log:" + rateLimitHash(userID, secret) + ":" + rateLimitHash(ip, secret)
}

func parseClientAppLogQuery(values url.Values, now time.Time) (ClientAppLogQuery, string) {
	filter := ClientAppLogQuery{
		UserID:         strings.TrimSpace(values.Get("user_id")),
		Event:          normalizeClientLogIdentifier(values.Get("event"), 96),
		EventPrefix:    normalizeClientLogIdentifier(values.Get("event_prefix"), 96),
		Platform:       normalizeClientLogIdentifier(values.Get("platform"), 32),
		BuildType:      normalizeClientLogIdentifier(values.Get("build_type"), 32),
		AppVersionName: truncateRunes(strings.TrimSpace(values.Get("app_version_name")), 64),
		OSVersion:      truncateRunes(strings.TrimSpace(values.Get("os_version")), 64),
		DeviceModel:    truncateRunes(strings.TrimSpace(values.Get("device_model")), 128),
		SinceMs:        now.Add(-24 * time.Hour).UnixMilli(),
		Limit:          defaultClientAppLogInternalListLimit,
	}
	if rawVersionCode := strings.TrimSpace(values.Get("app_version_code")); rawVersionCode != "" {
		versionCode, err := strconv.Atoi(rawVersionCode)
		if err != nil || versionCode < 0 {
			return ClientAppLogQuery{}, "invalid_app_version_code"
		}
		filter.AppVersionCode = &versionCode
	}
	if rawLevel := strings.TrimSpace(values.Get("level")); rawLevel != "" {
		level := strings.ToLower(rawLevel)
		switch level {
		case "info", "warn", "error":
			filter.Level = level
		default:
			return ClientAppLogQuery{}, "invalid_level"
		}
	}
	if rawSince := strings.TrimSpace(values.Get("since_ms")); rawSince != "" {
		since, err := strconv.ParseInt(rawSince, 10, 64)
		if err != nil || since < 0 {
			return ClientAppLogQuery{}, "invalid_since_ms"
		}
		filter.SinceMs = since
	}
	if rawLimit := strings.TrimSpace(values.Get("limit")); rawLimit != "" {
		limit, err := strconv.Atoi(rawLimit)
		if err != nil || limit <= 0 {
			return ClientAppLogQuery{}, "invalid_limit"
		}
		if limit > maxClientAppLogInternalListLimit {
			limit = maxClientAppLogInternalListLimit
		}
		filter.Limit = limit
	}
	return filter, ""
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
	message := normalizeClientLogMessage(body.Message, event)
	platform := normalizeClientLogIdentifier(body.Platform, 32)
	if platform == "" {
		platform = "android"
	}
	buildType := normalizeClientLogIdentifier(body.BuildType, 32)
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
		BuildType:      buildType,
		AppVersionCode: body.AppVersionCode,
		AppVersionName: truncateRunes(strings.TrimSpace(body.AppVersionName), 64),
		OSVersion:      truncateRunes(strings.TrimSpace(body.OSVersion), 64),
		DeviceModel:    truncateRunes(strings.TrimSpace(body.DeviceModel), 128),
		ClientTimeMs:   body.ClientTimeMs,
		CreatedAt:      createdAt,
		MaskedIP:       truncateRunes(strings.TrimSpace(maskedIP), 64),
	}, ""
}

func normalizeClientLogMessage(raw string, fallback string) string {
	message := truncateRunes(strings.TrimSpace(raw), 255)
	if message == "" || containsSensitiveClientLogText(message) {
		return fallback
	}
	return message
}

func containsSensitiveClientLogText(value string) bool {
	normalized := strings.ToLower(strings.TrimSpace(value))
	if giftCardTextLooksSensitive(value) {
		return true
	}
	for _, marker := range []string{
		"http://",
		"https://",
		"bearer ",
		"authorization",
		"token",
		"api_key",
		"access_key",
		"accesskey",
		"secret",
		"password",
	} {
		if strings.Contains(normalized, marker) {
			return true
		}
	}
	digits := 0
	for _, r := range normalized {
		if r >= '0' && r <= '9' {
			digits++
			if digits >= 11 {
				return true
			}
		} else {
			digits = 0
		}
	}
	return false
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
		if isSensitiveClientLogAttrKey(normalizedKey) {
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

func isSensitiveClientLogAttrKey(key string) bool {
	normalized := strings.ToLower(strings.TrimSpace(key))
	compact := strings.NewReplacer("_", "", "-", "", ".", "", ":", "", " ", "").Replace(normalized)
	switch normalized {
	case "token", "key", "url", "uri", "body", "message", "content":
		return true
	}
	if strings.Contains(compact, "apikey") ||
		strings.Contains(compact, "accesskey") ||
		strings.Contains(compact, "modelkey") ||
		strings.Contains(normalized, "phone") ||
		strings.Contains(normalized, "token") ||
		strings.Contains(normalized, "password") ||
		strings.Contains(normalized, "secret") ||
		strings.Contains(normalized, "authorization") ||
		strings.Contains(normalized, "api_key") ||
		strings.Contains(normalized, "access_key") ||
		strings.Contains(normalized, "model_key") {
		return true
	}
	for _, suffix := range []string{
		"_url", "-url", ".url", ":url", "urls",
		"_uri", "-uri", ".uri", ":uri", "uris",
		"_body", "-body", ".body", ":body",
		"_message", "-message", ".message", ":message",
		"_content", "-content", ".content", ":content",
	} {
		if strings.HasSuffix(normalized, suffix) {
			return true
		}
	}
	return false
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
		text := truncateRunes(strings.TrimSpace(v.String()), 160)
		if containsSensitiveClientLogText(text) {
			return nil, false
		}
		return text, true
	case string:
		text := truncateRunes(strings.TrimSpace(v), 160)
		if text == "" || containsSensitiveClientLogText(text) {
			return nil, false
		}
		return text, true
	case map[string]any, []any:
		return nil, false
	default:
		text := truncateRunes(strings.TrimSpace(fmt.Sprint(v)), 160)
		if text == "" || containsSensitiveClientLogText(text) {
			return nil, false
		}
		return text, true
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

func buildClientAppLogWhere(filter ClientAppLogQuery) (string, []any) {
	clauses := []string{"created_at >= ?"}
	args := []any{filter.SinceMs}
	if strings.TrimSpace(filter.UserID) != "" {
		clauses = append(clauses, "user_id = ?")
		args = append(args, strings.TrimSpace(filter.UserID))
	}
	if strings.TrimSpace(filter.Level) != "" {
		clauses = append(clauses, "level = ?")
		args = append(args, strings.TrimSpace(filter.Level))
	}
	if strings.TrimSpace(filter.Event) != "" {
		clauses = append(clauses, "event = ?")
		args = append(args, strings.TrimSpace(filter.Event))
	} else if strings.TrimSpace(filter.EventPrefix) != "" {
		clauses = append(clauses, "event LIKE ?")
		args = append(args, strings.TrimSpace(filter.EventPrefix)+"%")
	}
	if strings.TrimSpace(filter.Platform) != "" {
		clauses = append(clauses, "platform = ?")
		args = append(args, strings.TrimSpace(filter.Platform))
	}
	if strings.TrimSpace(filter.BuildType) != "" {
		clauses = append(clauses, "build_type = ?")
		args = append(args, strings.TrimSpace(filter.BuildType))
	}
	if filter.AppVersionCode != nil {
		clauses = append(clauses, "app_version_code = ?")
		args = append(args, *filter.AppVersionCode)
	}
	if strings.TrimSpace(filter.AppVersionName) != "" {
		clauses = append(clauses, "app_version_name LIKE ? ESCAPE '='")
		args = append(args, sqlLikePrefixPattern(filter.AppVersionName))
	}
	if strings.TrimSpace(filter.OSVersion) != "" {
		clauses = append(clauses, "os_version LIKE ? ESCAPE '='")
		args = append(args, sqlLikePrefixPattern(filter.OSVersion))
	}
	if strings.TrimSpace(filter.DeviceModel) != "" {
		clauses = append(clauses, "device_model LIKE ? ESCAPE '='")
		args = append(args, sqlLikePrefixPattern(filter.DeviceModel))
	}
	return " WHERE " + strings.Join(clauses, " AND "), args
}

func sqlLikePrefixPattern(value string) string {
	var builder strings.Builder
	for _, r := range strings.TrimSpace(value) {
		if r == '=' || r == '%' || r == '_' {
			builder.WriteRune('=')
		}
		builder.WriteRune(r)
	}
	builder.WriteRune('%')
	return builder.String()
}

func (s *Store) ListClientAppLogs(ctx context.Context, filter ClientAppLogQuery) ([]ClientAppLogEntry, error) {
	whereClause, args := buildClientAppLogWhere(filter)
	query := `SELECT
		   id,
		   user_id,
		   level,
		   event,
		   message,
		   attrs_json,
		   platform,
		   build_type,
		   app_version_code,
		   app_version_name,
		   os_version,
		   device_model,
		   client_time_ms,
		   created_at,
		   masked_ip
		 FROM client_app_logs` + whereClause + `
		 ORDER BY created_at DESC, id DESC
		 LIMIT ?`
	args = append(args, filter.Limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	entries := []ClientAppLogEntry{}
	for rows.Next() {
		var entry ClientAppLogEntry
		var attrsJSON sql.NullString
		var buildType sql.NullString
		var appVersionCode sql.NullInt64
		var appVersionName sql.NullString
		var osVersion sql.NullString
		var deviceModel sql.NullString
		var clientTimeMs sql.NullInt64
		var maskedIP sql.NullString
		if err := rows.Scan(
			&entry.ID,
			&entry.UserID,
			&entry.Level,
			&entry.Event,
			&entry.Message,
			&attrsJSON,
			&entry.Platform,
			&buildType,
			&appVersionCode,
			&appVersionName,
			&osVersion,
			&deviceModel,
			&clientTimeMs,
			&entry.CreatedAt,
			&maskedIP,
		); err != nil {
			return nil, err
		}
		if attrsJSON.Valid && strings.TrimSpace(attrsJSON.String) != "" {
			if raw, ok := validRawJSON(attrsJSON.String); ok {
				entry.Attrs = raw
			}
		}
		entry.BuildType = nullStringValue(buildType)
		entry.AppVersionCode = nullIntToPtr(appVersionCode)
		entry.AppVersionName = nullStringValue(appVersionName)
		entry.OSVersion = nullStringValue(osVersion)
		entry.DeviceModel = nullStringValue(deviceModel)
		entry.ClientTimeMs = nullInt64ToPtr(clientTimeMs)
		entry.MaskedIP = nullStringValue(maskedIP)
		entries = append(entries, entry)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if entries == nil {
		return []ClientAppLogEntry{}, nil
	}
	return entries, nil
}

func (s *Store) SummarizeClientAppLogs(ctx context.Context, filter ClientAppLogQuery) ([]ClientAppLogSummaryEntry, error) {
	whereClause, args := buildClientAppLogWhere(filter)
	limit := clientAppLogSummaryLimit(filter.Limit)
	query := `SELECT event, level, COUNT(*) AS event_count
		 FROM client_app_logs` + whereClause + `
		 GROUP BY event, level
		 ORDER BY event_count DESC, event ASC, level ASC
		 LIMIT ?`
	args = append(args, limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var summary []ClientAppLogSummaryEntry
	for rows.Next() {
		var entry ClientAppLogSummaryEntry
		if err := rows.Scan(&entry.Event, &entry.Level, &entry.Count); err != nil {
			return nil, err
		}
		summary = append(summary, entry)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if summary == nil {
		return []ClientAppLogSummaryEntry{}, nil
	}
	return summary, nil
}

func (s *Store) summarizeClientAppLogsByPrefix(ctx context.Context, sinceMs int64, limit int, prefix string, exactEvent string) ([]ClientAppLogSummaryEntry, error) {
	cappedLimit := clientAppLogSummaryLimit(limit)
	likePattern := strings.TrimSpace(prefix) + "%"
	query := `SELECT event, level, COUNT(*) AS event_count
		 FROM client_app_logs
		WHERE created_at >= ?
		  AND (event LIKE ? OR event = ?)
		 GROUP BY event, level
		 ORDER BY event_count DESC, event ASC, level ASC
		 LIMIT ?`
	rows, err := s.db.QueryContext(ctx, query, sinceMs, likePattern, strings.TrimSpace(exactEvent), cappedLimit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var summary []ClientAppLogSummaryEntry
	for rows.Next() {
		var entry ClientAppLogSummaryEntry
		if err := rows.Scan(&entry.Event, &entry.Level, &entry.Count); err != nil {
			return nil, err
		}
		summary = append(summary, entry)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if summary == nil {
		return []ClientAppLogSummaryEntry{}, nil
	}
	return summary, nil
}

func clientAppLogSummaryLimit(limit int) int {
	if limit <= 0 || limit > clientAppLogInternalSummaryLimit {
		return clientAppLogInternalSummaryLimit
	}
	return limit
}

func nullStringValue(value sql.NullString) string {
	if !value.Valid {
		return ""
	}
	return strings.TrimSpace(value.String)
}

func nullIntToPtr(value sql.NullInt64) *int {
	if !value.Valid {
		return nil
	}
	converted := int(value.Int64)
	return &converted
}

func nullInt64ToPtr(value sql.NullInt64) *int64 {
	if !value.Valid {
		return nil
	}
	converted := value.Int64
	return &converted
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
		   build_type,
		   app_version_code,
		   app_version_name,
		   os_version,
		   device_model,
		   client_time_ms,
		   created_at,
		   masked_ip
		 )
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		input.UserID,
		input.Level,
		input.Event,
		input.Message,
		nullableString(attrsJSON),
		input.Platform,
		nullableTrimmed(input.BuildType),
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

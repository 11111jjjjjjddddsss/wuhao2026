package app

import (
	"context"
	"database/sql"
	"encoding/json"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
	"unicode"
)

const (
	defaultAdminAuditLogListLimit = 100
	maxAdminAuditLogListLimit     = 200
	adminAuditDetailsMaxBytes     = 2048
)

type AdminAuditLogInput struct {
	Actor        string
	Action       string
	TargetType   string
	TargetID     string
	TargetUserID string
	Success      bool
	StatusCode   int
	DetailsJSON  any
	MaskedIP     string
	UserAgent    string
	CreatedAt    int64
}

type AdminAuditLogQuery struct {
	Action       string `json:"action,omitempty"`
	TargetUserID string `json:"target_user_id,omitempty"`
	Success      *bool  `json:"success,omitempty"`
	SinceMs      int64  `json:"since_ms"`
	Limit        int    `json:"limit"`
}

type AdminAuditLogEntry struct {
	ID           int64           `json:"id"`
	Actor        string          `json:"actor"`
	Action       string          `json:"action"`
	TargetType   string          `json:"target_type"`
	TargetID     string          `json:"target_id,omitempty"`
	TargetUserID string          `json:"target_user_id,omitempty"`
	Success      bool            `json:"success"`
	StatusCode   int             `json:"status_code,omitempty"`
	Details      json.RawMessage `json:"details,omitempty"`
	MaskedIP     string          `json:"masked_ip,omitempty"`
	UserAgent    string          `json:"user_agent,omitempty"`
	CreatedAt    int64           `json:"created_at"`
}

func (s *Server) handleInternalAdminAuditLogs(w http.ResponseWriter, r *http.Request) {
	if !s.requireInternalSupportAdminSecret(w, r) {
		return
	}
	filter, validationError := parseAdminAuditLogQuery(r.URL.Query(), time.Now())
	if validationError != "" {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.admin.audit_logs.list", "admin_audit_logs", "", "", false, http.StatusBadRequest, map[string]any{"error_code": validationError})
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	logs, err := s.store.ListAdminAuditLogs(r.Context(), filter)
	if err != nil {
		s.logger.Error("list admin audit logs failed", "error", err)
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.admin.audit_logs.list", "admin_audit_logs", "", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, "support_admin_secret", "internal.admin.audit_logs.list", "admin_audit_logs", "", filter.TargetUserID, true, http.StatusOK, map[string]any{
		"action":    filter.Action,
		"limit":     filter.Limit,
		"since_ms":  filter.SinceMs,
		"success":   adminAuditBoolPtrValue(filter.Success),
		"row_count": len(logs),
	})
	s.writeJSON(w, http.StatusOK, map[string]any{
		"logs":   logs,
		"filter": filter,
	})
}

func parseAdminAuditLogQuery(values url.Values, now time.Time) (AdminAuditLogQuery, string) {
	filter := AdminAuditLogQuery{
		Action:       normalizeClientLogIdentifier(values.Get("action"), 96),
		TargetUserID: strings.TrimSpace(values.Get("target_user_id")),
		SinceMs:      now.Add(-24 * time.Hour).UnixMilli(),
		Limit:        defaultAdminAuditLogListLimit,
	}
	if rawSince := strings.TrimSpace(values.Get("since_ms")); rawSince != "" {
		since, err := strconv.ParseInt(rawSince, 10, 64)
		if err != nil || since < 0 {
			return AdminAuditLogQuery{}, "invalid_since_ms"
		}
		filter.SinceMs = since
	}
	if rawLimit := strings.TrimSpace(values.Get("limit")); rawLimit != "" {
		limit, err := strconv.Atoi(rawLimit)
		if err != nil || limit <= 0 {
			return AdminAuditLogQuery{}, "invalid_limit"
		}
		if limit > maxAdminAuditLogListLimit {
			limit = maxAdminAuditLogListLimit
		}
		filter.Limit = limit
	}
	if rawSuccess := strings.TrimSpace(values.Get("success")); rawSuccess != "" {
		switch strings.ToLower(rawSuccess) {
		case "true", "1":
			value := true
			filter.Success = &value
		case "false", "0":
			value := false
			filter.Success = &value
		default:
			return AdminAuditLogQuery{}, "invalid_success"
		}
	}
	return filter, ""
}

func (s *Server) recordAdminAuditLog(r *http.Request, fallbackActor string, action string, targetType string, targetID string, targetUserID string, success bool, statusCode int, details map[string]any) {
	if s == nil || s.store == nil {
		return
	}
	detailsJSON, err := adminAuditDetailsJSON(details)
	if err != nil {
		s.logger.Warn("admin audit details dropped", "action", action, "error", err)
	}
	ctx, cancel := context.WithTimeout(contextBackground(), 2*time.Second)
	defer cancel()
	input := AdminAuditLogInput{
		Actor:        adminActorFromRequest(r, fallbackActor),
		Action:       normalizeClientLogIdentifier(action, 96),
		TargetType:   normalizeClientLogIdentifier(targetType, 64),
		TargetID:     truncateRunes(strings.TrimSpace(targetID), 191),
		TargetUserID: truncateRunes(strings.TrimSpace(targetUserID), 191),
		Success:      success,
		StatusCode:   statusCode,
		DetailsJSON:  detailsJSON,
		MaskedIP:     maskIP(GetClientIP(r)),
		UserAgent:    normalizeAdminAuditUserAgent(r.UserAgent()),
		CreatedAt:    time.Now().UnixMilli(),
	}
	if input.Action == "" || input.TargetType == "" {
		return
	}
	if err := s.store.CreateAdminAuditLog(ctx, input); err != nil {
		s.logger.Warn("create admin audit log failed", "action", input.Action, "error", err)
	}
}

func normalizeAdminAuditUserAgent(raw string) string {
	return sanitizeLoggedUserAgent(raw)
}

func adminActorFromRequest(r *http.Request, fallback string) string {
	fallbackActor := normalizeAdminActor(fallback)
	if fallbackActor != "" && fallbackActor != "support_admin_secret" {
		return fallbackActor
	}
	for _, header := range []string{"X-Admin-Actor", "X-Support-Admin-Actor"} {
		if actor := normalizeAdminActor(r.Header.Get(header)); actor != "" {
			return actor
		}
	}
	if fallbackActor != "" {
		return fallbackActor
	}
	return "internal"
}

func normalizeAdminActor(raw string) string {
	trimmed := strings.TrimSpace(strings.ToLower(raw))
	if trimmed == "" {
		return ""
	}
	var builder strings.Builder
	for _, r := range trimmed {
		if unicode.IsLetter(r) || unicode.IsDigit(r) || r == '_' || r == '-' || r == '.' || r == ':' || r == '@' {
			builder.WriteRune(r)
		}
	}
	return truncateRunes(builder.String(), 96)
}

func adminAuditDetailsJSON(raw map[string]any) (any, error) {
	if len(raw) == 0 {
		return nil, nil
	}
	normalized := make(map[string]any, len(raw))
	for key, value := range raw {
		normalizedKey := normalizeClientLogIdentifier(key, 64)
		if normalizedKey == "" || isSensitiveAdminAuditDetailKey(normalizedKey) {
			continue
		}
		normalizedValue, ok := normalizeClientLogAttrValue(value)
		if !ok {
			continue
		}
		normalized[normalizedKey] = normalizedValue
	}
	if len(normalized) == 0 {
		return nil, nil
	}
	data, err := json.Marshal(normalized)
	if err != nil {
		return nil, err
	}
	if len(data) > adminAuditDetailsMaxBytes {
		return nil, errJSONBodyTooLarge
	}
	return string(data), nil
}

func isSensitiveAdminAuditDetailKey(key string) bool {
	normalized := strings.ToLower(strings.TrimSpace(key))
	if isSensitiveClientLogAttrKey(normalized) {
		return true
	}
	return strings.Contains(normalized, "image") ||
		strings.Contains(normalized, "attachment") ||
		strings.Contains(normalized, "phone")
}

func adminAuditBoolPtrValue(value *bool) any {
	if value == nil {
		return nil
	}
	return *value
}

func buildAdminAuditLogWhere(filter AdminAuditLogQuery) (string, []any) {
	clauses := []string{"created_at >= ?"}
	args := []any{filter.SinceMs}
	if strings.TrimSpace(filter.Action) != "" {
		clauses = append(clauses, "action = ?")
		args = append(args, strings.TrimSpace(filter.Action))
	}
	if strings.TrimSpace(filter.TargetUserID) != "" {
		clauses = append(clauses, "target_user_id = ?")
		args = append(args, strings.TrimSpace(filter.TargetUserID))
	}
	if filter.Success != nil {
		clauses = append(clauses, "success = ?")
		if *filter.Success {
			args = append(args, 1)
		} else {
			args = append(args, 0)
		}
	}
	return " WHERE " + strings.Join(clauses, " AND "), args
}

func (s *Store) CreateAdminAuditLog(ctx context.Context, input AdminAuditLogInput) error {
	var statusCode any
	if input.StatusCode > 0 {
		statusCode = input.StatusCode
	}
	var detailsJSON sql.NullString
	if text, ok := input.DetailsJSON.(string); ok && strings.TrimSpace(text) != "" {
		detailsJSON = sql.NullString{String: text, Valid: true}
	}
	success := 0
	if input.Success {
		success = 1
	}
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO admin_audit_logs(
		   actor,
		   action,
		   target_type,
		   target_id,
		   target_user_id,
		   success,
		   status_code,
		   details_json,
		   masked_ip,
		   user_agent,
		   created_at
		 )
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		truncateRunes(strings.TrimSpace(input.Actor), 96),
		truncateRunes(strings.TrimSpace(input.Action), 96),
		truncateRunes(strings.TrimSpace(input.TargetType), 64),
		nullableTrimmed(input.TargetID),
		nullableTrimmed(input.TargetUserID),
		success,
		statusCode,
		nullableString(detailsJSON),
		nullableTrimmed(input.MaskedIP),
		nullableTrimmed(input.UserAgent),
		input.CreatedAt,
	)
	return err
}

func (s *Store) ListAdminAuditLogs(ctx context.Context, filter AdminAuditLogQuery) ([]AdminAuditLogEntry, error) {
	whereClause, args := buildAdminAuditLogWhere(filter)
	query := `SELECT
		   id,
		   actor,
		   action,
		   target_type,
		   target_id,
		   target_user_id,
		   success,
		   status_code,
		   details_json,
		   masked_ip,
		   user_agent,
		   created_at
		 FROM admin_audit_logs` + whereClause + `
		 ORDER BY created_at DESC, id DESC
		 LIMIT ?`
	args = append(args, filter.Limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	entries := []AdminAuditLogEntry{}
	for rows.Next() {
		var entry AdminAuditLogEntry
		var targetID sql.NullString
		var targetUserID sql.NullString
		var success int
		var statusCode sql.NullInt64
		var detailsJSON sql.NullString
		var maskedIP sql.NullString
		var userAgent sql.NullString
		if err := rows.Scan(
			&entry.ID,
			&entry.Actor,
			&entry.Action,
			&entry.TargetType,
			&targetID,
			&targetUserID,
			&success,
			&statusCode,
			&detailsJSON,
			&maskedIP,
			&userAgent,
			&entry.CreatedAt,
		); err != nil {
			return nil, err
		}
		entry.TargetID = nullStringValue(targetID)
		entry.TargetUserID = nullStringValue(targetUserID)
		entry.Success = success != 0
		if statusCode.Valid {
			entry.StatusCode = int(statusCode.Int64)
		}
		if detailsJSON.Valid && strings.TrimSpace(detailsJSON.String) != "" {
			if raw, ok := validRawJSON(detailsJSON.String); ok {
				entry.Details = raw
			}
		}
		entry.MaskedIP = nullStringValue(maskedIP)
		entry.UserAgent = nullStringValue(userAgent)
		entries = append(entries, entry)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if entries == nil {
		return []AdminAuditLogEntry{}, nil
	}
	return entries, nil
}

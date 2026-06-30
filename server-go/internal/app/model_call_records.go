package app

import (
	"context"
	"database/sql"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const (
	modelCallRecordInsertTimeout    = 1500 * time.Millisecond
	defaultModelCallRecordRetention = 14 * 24 * time.Hour
	modelCallRecordPruneBatchLimit  = 1000
	defaultModelCallRecordLimit     = 100
	maxModelCallRecordLimit         = 300
)

type ModelCallRecordInput struct {
	RecordType             string
	Chain                  string
	UserID                 string
	ClientMsgID            string
	RequestID              string
	UpstreamRequestID      string
	Provider               string
	ProviderLabel          string
	ProviderSlot           string
	KeySlot                string
	Model                  string
	Status                 string
	FallbackReason         string
	ErrorKind              string
	HTTPStatus             int
	Attempt                int
	MaxAttempts            int
	Tier                   string
	ImageCount             int
	PromptHasImages        bool
	ForcedSearch           bool
	SearchStrategy         string
	ReasoningEffort        string
	ThinkingEnabled        bool
	ThinkingBudget         int
	OpenMs                 int64
	RequestToOpenMs        int64
	FirstVisibleMs         int64
	UpstreamFirstVisibleMs int64
	TotalMs                int64
	InputTokens            int
	OutputTokens           int
	TotalTokens            int
	ReasoningTokens        int
	CachedTokens           int
	SearchCount            int
	ReplyChars             int
	ClientDisconnected     bool
	CreatedAt              int64
	UpdatedAt              int64
}

type AdminModelCallRecordQuery struct {
	SinceMs     int64  `json:"since_ms"`
	Limit       int    `json:"limit"`
	Provider    string `json:"provider,omitempty"`
	Status      string `json:"status,omitempty"`
	RecordType  string `json:"record_type,omitempty"`
	UserID      string `json:"user_id,omitempty"`
	ClientMsgID string `json:"client_msg_id,omitempty"`
}

type AdminModelCallRecordEntry struct {
	ID                     int64  `json:"id"`
	RecordType             string `json:"record_type"`
	Chain                  string `json:"chain"`
	UserID                 string `json:"user_id,omitempty"`
	ClientMsgID            string `json:"client_msg_id,omitempty"`
	RequestID              string `json:"request_id,omitempty"`
	UpstreamRequestID      string `json:"upstream_request_id,omitempty"`
	Provider               string `json:"provider"`
	ProviderLabel          string `json:"provider_label,omitempty"`
	ProviderSlot           string `json:"provider_slot,omitempty"`
	KeySlot                string `json:"key_slot,omitempty"`
	Model                  string `json:"model,omitempty"`
	Status                 string `json:"status"`
	FallbackReason         string `json:"fallback_reason,omitempty"`
	ErrorKind              string `json:"error_kind,omitempty"`
	HTTPStatus             int    `json:"http_status,omitempty"`
	Attempt                int    `json:"attempt,omitempty"`
	MaxAttempts            int    `json:"max_attempts,omitempty"`
	Tier                   string `json:"tier,omitempty"`
	ImageCount             int    `json:"image_count"`
	PromptHasImages        bool   `json:"prompt_has_images"`
	ForcedSearch           bool   `json:"forced_search"`
	SearchStrategy         string `json:"search_strategy,omitempty"`
	ReasoningEffort        string `json:"reasoning_effort,omitempty"`
	ThinkingEnabled        bool   `json:"thinking_enabled"`
	ThinkingBudget         int    `json:"thinking_budget,omitempty"`
	OpenMs                 int64  `json:"open_ms"`
	RequestToOpenMs        int64  `json:"request_to_open_ms"`
	FirstVisibleMs         int64  `json:"first_visible_ms"`
	UpstreamFirstVisibleMs int64  `json:"upstream_first_visible_ms"`
	TotalMs                int64  `json:"total_ms"`
	InputTokens            int    `json:"input_tokens"`
	OutputTokens           int    `json:"output_tokens"`
	TotalTokens            int    `json:"total_tokens"`
	ReasoningTokens        int    `json:"reasoning_tokens"`
	CachedTokens           int    `json:"cached_tokens"`
	SearchCount            int    `json:"search_count"`
	ReplyChars             int    `json:"reply_chars"`
	ClientDisconnected     bool   `json:"client_disconnected"`
	CreatedAt              int64  `json:"created_at"`
	UpdatedAt              int64  `json:"updated_at"`
}

func (s *Store) InsertModelCallRecord(ctx context.Context, input ModelCallRecordInput) error {
	if s == nil || s.db == nil {
		return nil
	}
	nowMs := input.CreatedAt
	if nowMs <= 0 {
		nowMs = time.Now().UnixMilli()
	}
	updatedAt := input.UpdatedAt
	if updatedAt <= 0 {
		updatedAt = nowMs
	}
	_, err := s.db.ExecContext(ctx, `INSERT INTO model_call_records(
	   record_type, chain, user_id, client_msg_id, request_id, upstream_request_id,
	   provider, provider_label, provider_slot, key_slot, model, status, fallback_reason, error_kind,
	   http_status, attempt, max_attempts, tier, image_count, prompt_has_images, forced_search,
	   search_strategy, reasoning_effort, thinking_enabled, thinking_budget, open_ms, request_to_open_ms,
	   first_visible_ms, upstream_first_visible_ms, total_ms, input_tokens, output_tokens, total_tokens,
	   reasoning_tokens, cached_tokens, search_count, reply_chars, client_disconnected, created_at, updated_at
	 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		modelCallRecordType(input.RecordType),
		firstNonEmpty(strings.TrimSpace(input.Chain), "main_chat"),
		truncateRunes(strings.TrimSpace(input.UserID), 128),
		truncateRunes(strings.TrimSpace(input.ClientMsgID), 128),
		truncateRunes(strings.TrimSpace(input.RequestID), 128),
		truncateRunes(strings.TrimSpace(input.UpstreamRequestID), 128),
		modelCallProvider(input.Provider),
		safeModelCallLabel(input.ProviderLabel, 64),
		safeModelCallSlot(input.ProviderSlot, 64),
		safeModelCallSlot(input.KeySlot, 96),
		safeModelCallLabel(input.Model, 64),
		modelCallStatus(input.Status),
		safeModelCallSlot(input.FallbackReason, 64),
		safeModelCallSlot(input.ErrorKind, 64),
		input.HTTPStatus,
		nonNegativeInt(input.Attempt),
		nonNegativeInt(input.MaxAttempts),
		safeModelCallSlot(input.Tier, 32),
		nonNegativeInt(input.ImageCount),
		input.PromptHasImages,
		input.ForcedSearch,
		safeModelCallSlot(input.SearchStrategy, 64),
		safeModelCallSlot(input.ReasoningEffort, 32),
		input.ThinkingEnabled,
		nonNegativeInt(input.ThinkingBudget),
		input.OpenMs,
		input.RequestToOpenMs,
		input.FirstVisibleMs,
		input.UpstreamFirstVisibleMs,
		input.TotalMs,
		nonNegativeInt(input.InputTokens),
		nonNegativeInt(input.OutputTokens),
		nonNegativeInt(input.TotalTokens),
		nonNegativeInt(input.ReasoningTokens),
		nonNegativeInt(input.CachedTokens),
		nonNegativeInt(input.SearchCount),
		nonNegativeInt(input.ReplyChars),
		input.ClientDisconnected,
		nowMs,
		updatedAt,
	)
	return err
}

func (s *Store) ListAdminModelCallRecords(ctx context.Context, filter AdminModelCallRecordQuery) ([]AdminModelCallRecordEntry, error) {
	if s == nil || s.db == nil {
		return []AdminModelCallRecordEntry{}, nil
	}
	where, args := buildAdminModelCallRecordWhere(filter)
	limit := filter.Limit
	if limit <= 0 {
		limit = defaultModelCallRecordLimit
	}
	if limit > maxModelCallRecordLimit {
		limit = maxModelCallRecordLimit
	}
	query := `SELECT
	   id, record_type, chain, user_id, client_msg_id, request_id, upstream_request_id,
	   provider, provider_label, provider_slot, key_slot, model, status, fallback_reason, error_kind,
	   http_status, attempt, max_attempts, tier, image_count, prompt_has_images, forced_search,
	   search_strategy, reasoning_effort, thinking_enabled, thinking_budget, open_ms, request_to_open_ms,
	   first_visible_ms, upstream_first_visible_ms, total_ms, input_tokens, output_tokens, total_tokens,
	   reasoning_tokens, cached_tokens, search_count, reply_chars, client_disconnected, created_at, updated_at
	 FROM model_call_records` + where + `
	 ORDER BY created_at DESC, id DESC
	 LIMIT ?`
	args = append(args, limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	records := []AdminModelCallRecordEntry{}
	for rows.Next() {
		var entry AdminModelCallRecordEntry
		if err := rows.Scan(
			&entry.ID,
			&entry.RecordType,
			&entry.Chain,
			&entry.UserID,
			&entry.ClientMsgID,
			&entry.RequestID,
			&entry.UpstreamRequestID,
			&entry.Provider,
			&entry.ProviderLabel,
			&entry.ProviderSlot,
			&entry.KeySlot,
			&entry.Model,
			&entry.Status,
			&entry.FallbackReason,
			&entry.ErrorKind,
			&entry.HTTPStatus,
			&entry.Attempt,
			&entry.MaxAttempts,
			&entry.Tier,
			&entry.ImageCount,
			&entry.PromptHasImages,
			&entry.ForcedSearch,
			&entry.SearchStrategy,
			&entry.ReasoningEffort,
			&entry.ThinkingEnabled,
			&entry.ThinkingBudget,
			&entry.OpenMs,
			&entry.RequestToOpenMs,
			&entry.FirstVisibleMs,
			&entry.UpstreamFirstVisibleMs,
			&entry.TotalMs,
			&entry.InputTokens,
			&entry.OutputTokens,
			&entry.TotalTokens,
			&entry.ReasoningTokens,
			&entry.CachedTokens,
			&entry.SearchCount,
			&entry.ReplyChars,
			&entry.ClientDisconnected,
			&entry.CreatedAt,
			&entry.UpdatedAt,
		); err != nil {
			return nil, err
		}
		records = append(records, entry)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return records, nil
}

func (s *Store) PruneExpiredModelCallRecords(ctx context.Context, nowMs int64) (int64, error) {
	if s == nil || s.db == nil {
		return 0, nil
	}
	retention := envDurationWithDefault("MODEL_CALL_RECORD_RETENTION_SECONDS", defaultModelCallRecordRetention)
	if retention <= 0 {
		return 0, nil
	}
	cutoffMs := nowMs - int64(retention/time.Millisecond)
	if cutoffMs <= 0 {
		return 0, nil
	}
	result, err := s.db.ExecContext(
		ctx,
		"DELETE FROM model_call_records WHERE created_at < ? LIMIT ?",
		cutoffMs,
		modelCallRecordPruneBatchLimit,
	)
	if err != nil {
		return 0, err
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return 0, err
	}
	return rows, nil
}

func parseAdminModelCallRecordQuery(values url.Values, now time.Time) (AdminModelCallRecordQuery, string) {
	filter := AdminModelCallRecordQuery{
		SinceMs: now.Add(-24 * time.Hour).UnixMilli(),
		Limit:   defaultModelCallRecordLimit,
	}
	if rawSince := strings.TrimSpace(values.Get("since_ms")); rawSince != "" {
		since, err := strconv.ParseInt(rawSince, 10, 64)
		if err != nil || since < 0 {
			return AdminModelCallRecordQuery{}, "invalid_since_ms"
		}
		filter.SinceMs = since
	}
	if rawLimit := strings.TrimSpace(values.Get("limit")); rawLimit != "" {
		limit, err := strconv.Atoi(rawLimit)
		if err != nil || limit <= 0 {
			return AdminModelCallRecordQuery{}, "invalid_limit"
		}
		if limit > maxModelCallRecordLimit {
			limit = maxModelCallRecordLimit
		}
		filter.Limit = limit
	}
	filter.Provider = normalizeModelCallFilterValue(values.Get("provider"), 32)
	filter.Status = normalizeModelCallFilterValue(values.Get("status"), 32)
	filter.RecordType = normalizeModelCallFilterValue(values.Get("record_type"), 32)
	filter.UserID = truncateRunes(strings.TrimSpace(values.Get("user_id")), 128)
	filter.ClientMsgID = truncateRunes(strings.TrimSpace(values.Get("client_msg_id")), 128)
	return filter, ""
}

func buildAdminModelCallRecordWhere(filter AdminModelCallRecordQuery) (string, []any) {
	clauses := []string{"created_at >= ?"}
	args := []any{filter.SinceMs}
	if filter.Provider != "" {
		clauses = append(clauses, "provider = ?")
		args = append(args, filter.Provider)
	}
	if filter.Status != "" {
		clauses = append(clauses, "status = ?")
		args = append(args, filter.Status)
	}
	if filter.RecordType != "" {
		clauses = append(clauses, "record_type = ?")
		args = append(args, filter.RecordType)
	}
	if strings.TrimSpace(filter.UserID) != "" {
		clauses = append(clauses, "user_id = ?")
		args = append(args, strings.TrimSpace(filter.UserID))
	}
	if strings.TrimSpace(filter.ClientMsgID) != "" {
		clauses = append(clauses, "client_msg_id = ?")
		args = append(args, strings.TrimSpace(filter.ClientMsgID))
	}
	return " WHERE " + strings.Join(clauses, " AND "), args
}

func modelCallRecordType(value string) string {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "key_attempt", "stream_final", "stream_open_failed":
		return strings.ToLower(strings.TrimSpace(value))
	default:
		return "stream_final"
	}
}

func modelCallProvider(value string) string {
	normalized := normalizeModelCallFilterValue(value, 32)
	if normalized == "" {
		return "unknown"
	}
	return normalized
}

func modelCallStatus(value string) string {
	normalized := normalizeModelCallFilterValue(value, 32)
	if normalized == "" {
		return "unknown"
	}
	return normalized
}

func safeModelCallLabel(value string, limit int) string {
	cleaned := sanitizeDashScopeErrorMessage(strings.TrimSpace(value))
	if cleaned == "" || strings.Contains(cleaned, "[redacted]") || strings.Contains(cleaned, "[url]") || strings.Contains(cleaned, "[phone]") {
		return ""
	}
	return truncateRunes(cleaned, limit)
}

func safeModelCallSlot(value string, limit int) string {
	return truncateRunes(normalizeModelCallFilterValue(value, limit), limit)
}

func normalizeModelCallFilterValue(value string, maxRunes int) string {
	trimmed := strings.ToLower(strings.TrimSpace(value))
	if trimmed == "" {
		return ""
	}
	var builder strings.Builder
	for _, r := range trimmed {
		if (r >= 'a' && r <= 'z') ||
			(r >= '0' && r <= '9') ||
			r == '_' ||
			r == '-' ||
			r == ':' ||
			r == '.' {
			builder.WriteRune(r)
		}
	}
	return truncateRunes(builder.String(), maxRunes)
}

func nonNegativeInt(value int) int {
	if value < 0 {
		return 0
	}
	return value
}

func nullableModelCallString(value string) any {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return nil
	}
	return trimmed
}

func scanNullableString(value sql.NullString) string {
	if value.Valid {
		return value.String
	}
	return ""
}

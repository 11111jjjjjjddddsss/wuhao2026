package app

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"testing"
	"time"
)

func TestNormalizeSupportMessagePayloadAllowsTextImageAndImageOnly(t *testing.T) {
	body, images, validationError := normalizeSupportMessagePayload("  反馈内容  ", []string{
		"https://example.com/uploads/a.jpg",
	})
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if body != "反馈内容" {
		t.Fatalf("body = %q, want trimmed text", body)
	}
	if len(images) != 1 {
		t.Fatalf("images len = %d, want 1", len(images))
	}

	body, images, validationError = normalizeSupportMessagePayload(" ", []string{
		"https://example.com/uploads/a.jpg",
	})
	if validationError != "" {
		t.Fatalf("image-only message should pass, got %s", validationError)
	}
	if body != "" || len(images) != 1 {
		t.Fatalf("image-only payload mismatch: body=%q images=%v", body, images)
	}
}

func TestNormalizeSupportMessagePayloadRejectsInvalidInput(t *testing.T) {
	tests := []struct {
		name   string
		body   string
		images []string
		want   string
	}{
		{
			name: "empty",
			want: "body or images required",
		},
		{
			name:   "too many images",
			images: []string{"1", "2", "3", "4", "5"},
			want:   "single request supports up to 4 images",
		},
		{
			name: "body too long",
			body: strings.Repeat("字", supportMessageMaxRunes+1),
			want: "body too long",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, _, validationError := normalizeSupportMessagePayload(tt.body, tt.images)
			if validationError != tt.want {
				t.Fatalf("validationError = %q, want %q", validationError, tt.want)
			}
		})
	}
}

func TestSupportImageURLsJSON(t *testing.T) {
	raw, err := supportImageURLsJSON([]string{
		"https://example.com/uploads/a.jpg",
		"",
		"https://example.com/uploads/b.jpg",
	})
	if err != nil {
		t.Fatalf("supportImageURLsJSON failed: %v", err)
	}
	encoded, ok := raw.(string)
	if !ok {
		t.Fatalf("raw = %#v, want string", raw)
	}

	var decoded []string
	if err := json.Unmarshal([]byte(encoded), &decoded); err != nil {
		t.Fatalf("decode image urls json: %v", err)
	}
	if len(decoded) != 2 {
		t.Fatalf("decoded len = %d, want 2", len(decoded))
	}

	empty, err := supportImageURLsJSON(nil)
	if err != nil {
		t.Fatalf("empty supportImageURLsJSON failed: %v", err)
	}
	if empty != nil {
		t.Fatalf("empty = %#v, want nil", empty)
	}
}

func TestSupportMessageRateLimitKeyHashesSensitiveInputs(t *testing.T) {
	t.Setenv("APP_SECRET", "test-secret")
	key := supportMessageRateLimitKey("acct_sensitive_user", "203.0.113.9")
	if key == "" || strings.Contains(key, "acct_sensitive_user") || strings.Contains(key, "203.0.113.9") {
		t.Fatalf("supportMessageRateLimitKey leaked sensitive input: %q", key)
	}
	if !strings.HasPrefix(key, "support_message:") {
		t.Fatalf("supportMessageRateLimitKey prefix mismatch: %q", key)
	}
}

func TestSupportMessageRateLimiterUsesEnv(t *testing.T) {
	t.Setenv("SUPPORT_MESSAGE_RATE_LIMIT_WINDOW_SECONDS", "30")
	t.Setenv("SUPPORT_MESSAGE_RATE_LIMIT_MAX_HITS", "2")
	t.Setenv("SUPPORT_MESSAGE_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "45")

	limiter, ok := newSupportMessageRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newSupportMessageRateLimiter returned %T, want *chatRateLimiter fallback", newSupportMessageRateLimiter(nil))
	}
	if limiter.window != 30*time.Second || limiter.maxHits != 2 || limiter.pruneInterval != 45*time.Second {
		t.Fatalf("support message limiter config mismatch: window=%s max=%d prune=%s", limiter.window, limiter.maxHits, limiter.pruneInterval)
	}
}

func TestParseSupportConversationQuery(t *testing.T) {
	filter, validationError := parseSupportConversationQuery(url.Values{
		"since_ms": {"123"},
		"limit":    {"999"},
	}, time.UnixMilli(10_000))
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if filter.SinceMs != 123 || filter.Limit != maxSupportConversationListLimit {
		t.Fatalf("filter mismatch: %#v", filter)
	}

	defaultFilter, validationError := parseSupportConversationQuery(url.Values{}, time.UnixMilli(10_000))
	if validationError != "" {
		t.Fatalf("unexpected default validation error: %s", validationError)
	}
	if defaultFilter.Limit != defaultSupportConversationListLimit ||
		defaultFilter.SinceMs != time.UnixMilli(10_000).Add(-defaultSupportConversationSinceDuration).UnixMilli() {
		t.Fatalf("default filter mismatch: %#v", defaultFilter)
	}
}

func TestParseSupportConversationQueryRejectsInvalidFilters(t *testing.T) {
	tests := []struct {
		name   string
		values url.Values
		want   string
	}{
		{name: "invalid since", values: url.Values{"since_ms": {"-1"}}, want: "invalid_since_ms"},
		{name: "invalid limit", values: url.Values{"limit": {"0"}}, want: "invalid_limit"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, got := parseSupportConversationQuery(tt.values, time.Now())
			if got != tt.want {
				t.Fatalf("validation error = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestRequireSupportAdminSecret(t *testing.T) {
	server := &Server{logger: slog.New(slog.NewTextHandler(os.Stdout, nil))}

	t.Setenv("SUPPORT_ADMIN_SECRET", "")
	req := httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	rec := httptest.NewRecorder()
	if server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected missing secret to reject")
	}
	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("missing secret status = %d, want %d", rec.Code, http.StatusServiceUnavailable)
	}

	t.Setenv("SUPPORT_ADMIN_SECRET", "secret")
	req = httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.Header.Set("X-Support-Admin-Secret", "secret")
	rec = httptest.NewRecorder()
	if !server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected header secret to pass")
	}

	req = httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.Header.Set("Authorization", "Bearer secret")
	rec = httptest.NewRecorder()
	if !server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected bearer secret to pass")
	}

	req = httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.Header.Set("X-Support-Admin-Secret", "wrong")
	rec = httptest.NewRecorder()
	if server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected wrong secret to reject")
	}
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("wrong secret status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}
}

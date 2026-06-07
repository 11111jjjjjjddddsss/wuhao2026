package app

import (
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"
)

func TestNormalizeClientAppLogPayloadAcceptsMinimalSafePayload(t *testing.T) {
	versionCode := 12
	clientTime := int64(1710000000000)
	input, validationError := normalizeClientAppLogPayload("user-1", "1.2.*.*", clientAppLogRequest{
		Level:          "WARN",
		Event:          "chat.stream-interrupted",
		Message:        "  stream interrupted  ",
		Platform:       "Android",
		AppVersionCode: &versionCode,
		AppVersionName: "1.0.12",
		OSVersion:      "Android 15",
		DeviceModel:    "Brand Model",
		ClientTimeMs:   &clientTime,
		Attrs: map[string]any{
			"reason":      "network",
			"text_length": float64(42),
			"ignored":     map[string]any{"nested": true},
		},
	}, 123)
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if input.Level != "warn" || input.Event != "chat.stream-interrupted" || input.Message != "stream interrupted" {
		t.Fatalf("normalized input mismatch: %#v", input)
	}
	if input.Platform != "android" || input.AppVersionCode == nil || *input.AppVersionCode != versionCode {
		t.Fatalf("platform/version mismatch: %#v", input)
	}
	if input.AttrsJSON == nil {
		t.Fatalf("expected attrs json")
	}
}

func TestNormalizeClientAppLogPayloadDropsSensitiveAttrs(t *testing.T) {
	input, validationError := normalizeClientAppLogPayload("user-1", "1.2.*.*", clientAppLogRequest{
		Level:   "warn",
		Event:   "chat.stream_interrupted",
		Message: "safe",
		Attrs: map[string]any{
			"reason":        "network",
			"detail":        "https://api.example.com/uploads/a.jpg",
			"note":          "13800138000",
			"fallback":      "token=secret",
			"body_length":   12,
			"token":         "secret-token",
			"access_key":    "ak-value",
			"phone_number":  "13800138000",
			"image_urls":    "https://example.com/uploads/a.jpg",
			"response_body": "用户填写内容",
		},
	}, 123)
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	attrs, ok := input.AttrsJSON.(string)
	if !ok {
		t.Fatalf("attrs json = %#v, want string", input.AttrsJSON)
	}
	for _, allowed := range []string{"reason", "body_length"} {
		if !strings.Contains(attrs, allowed) {
			t.Fatalf("attrs = %q, want safe %q", attrs, allowed)
		}
	}
	for _, forbidden := range []string{"token", "ak-value", "13800138000", "image_urls", "用户填写内容", "detail", "fallback"} {
		if strings.Contains(attrs, forbidden) {
			t.Fatalf("attrs leaked %q: %s", forbidden, attrs)
		}
	}
}

func TestNormalizeClientAppLogPayloadSanitizesSensitiveMessage(t *testing.T) {
	input, validationError := normalizeClientAppLogPayload("user-1", "1.2.*.*", clientAppLogRequest{
		Level:   "error",
		Event:   "chat.stream_failed",
		Message: "https://api.example.com/uploads/a.jpg token=secret 13800138000",
	}, 123)
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if input.Message != "chat.stream_failed" {
		t.Fatalf("message = %q, want event fallback", input.Message)
	}
}

func TestNormalizeClientAppLogPayloadRejectsInvalidPayload(t *testing.T) {
	tests := []struct {
		name string
		body clientAppLogRequest
		want string
	}{
		{name: "missing event", body: clientAppLogRequest{Level: "warn"}, want: "event required"},
		{name: "bad level", body: clientAppLogRequest{Level: "debug", Event: "x"}, want: "invalid level"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, got := normalizeClientAppLogPayload("user-1", "", tt.body, 123)
			if got != tt.want {
				t.Fatalf("validation error = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestClientAppLogRateLimitKeyHashesSensitiveInputs(t *testing.T) {
	t.Setenv("APP_SECRET", "test-secret")
	key := clientAppLogRateLimitKey("acct_sensitive_user", "203.0.113.9")
	if key == "" || strings.Contains(key, "acct_sensitive_user") || strings.Contains(key, "203.0.113.9") {
		t.Fatalf("clientAppLogRateLimitKey leaked sensitive input: %q", key)
	}
	if !strings.HasPrefix(key, "client_app_log:") {
		t.Fatalf("clientAppLogRateLimitKey prefix mismatch: %q", key)
	}
}

func TestClientAppLogRateLimiterUsesEnv(t *testing.T) {
	t.Setenv("CLIENT_APP_LOG_RATE_LIMIT_WINDOW_SECONDS", "30")
	t.Setenv("CLIENT_APP_LOG_RATE_LIMIT_MAX_HITS", "2")
	t.Setenv("CLIENT_APP_LOG_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "45")

	limiter, ok := newClientAppLogRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newClientAppLogRateLimiter returned %T, want *chatRateLimiter fallback", newClientAppLogRateLimiter(nil))
	}
	if limiter.window != 30*time.Second || limiter.maxHits != 2 || limiter.pruneInterval != 45*time.Second {
		t.Fatalf("client app log limiter config mismatch: window=%s max=%d prune=%s", limiter.window, limiter.maxHits, limiter.pruneInterval)
	}
}

func TestParseClientAppLogQueryNormalizesAndCapsLimit(t *testing.T) {
	now := time.UnixMilli(10_000)
	values := url.Values{
		"user_id":  {" acct_123 "},
		"event":    {" Chat.Stream_Interrupted "},
		"level":    {"WARN"},
		"since_ms": {"1234"},
		"limit":    {"500"},
	}
	filter, validationError := parseClientAppLogQuery(values, now)
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if filter.UserID != "acct_123" ||
		filter.Event != "chat.stream_interrupted" ||
		filter.Level != "warn" ||
		filter.SinceMs != 1234 ||
		filter.Limit != maxClientAppLogInternalListLimit {
		t.Fatalf("filter mismatch: %#v", filter)
	}
}

func TestParseClientAppLogQueryDefaultsToRecentLogs(t *testing.T) {
	now := time.UnixMilli(48 * 60 * 60 * 1000)
	filter, validationError := parseClientAppLogQuery(url.Values{}, now)
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if filter.SinceMs != now.Add(-24*time.Hour).UnixMilli() {
		t.Fatalf("since_ms = %d, want last 24h", filter.SinceMs)
	}
	if filter.Limit != defaultClientAppLogInternalListLimit {
		t.Fatalf("limit = %d, want default", filter.Limit)
	}
}

func TestParseClientAppLogQueryRejectsInvalidFilters(t *testing.T) {
	tests := []struct {
		name   string
		values url.Values
		want   string
	}{
		{name: "invalid level", values: url.Values{"level": {"debug"}}, want: "invalid_level"},
		{name: "invalid since", values: url.Values{"since_ms": {"-1"}}, want: "invalid_since_ms"},
		{name: "invalid limit", values: url.Values{"limit": {"0"}}, want: "invalid_limit"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, got := parseClientAppLogQuery(tt.values, time.Now())
			if got != tt.want {
				t.Fatalf("validation error = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestHandleCreateClientAppLogRejectsOversizedBodyWith413(t *testing.T) {
	server := &Server{logger: slog.New(slog.NewTextHandler(io.Discard, nil))}
	body := `{"level":"warn","event":"chat.failure","message":"` + strings.Repeat("x", clientAppLogMaxBodyBytes) + `"}`
	request := httptest.NewRequest(http.MethodPost, "/api/app/logs", strings.NewReader(body))
	request.Header.Set("X-User-Id", "user-1")
	recorder := httptest.NewRecorder()

	server.handleCreateClientAppLog(recorder, request)

	if recorder.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusRequestEntityTooLarge)
	}
	if !strings.Contains(recorder.Body.String(), "body_too_large") {
		t.Fatalf("body = %q, want body_too_large", recorder.Body.String())
	}
}

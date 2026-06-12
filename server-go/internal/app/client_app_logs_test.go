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

	"github.com/redis/go-redis/v9"
)

func TestNormalizeClientAppLogPayloadAcceptsMinimalSafePayload(t *testing.T) {
	versionCode := 12
	clientTime := int64(1710000000000)
	input, validationError := normalizeClientAppLogPayload("user-1", "1.2.*.*", clientAppLogRequest{
		Level:          "WARN",
		Event:          "chat.stream-interrupted",
		Message:        "  stream interrupted  ",
		Platform:       "Android",
		BuildType:      "Debug",
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
	if input.Level != "warn" || input.Event != "chat.stream-interrupted" || input.Message != "chat.stream-interrupted" {
		t.Fatalf("normalized input mismatch: %#v", input)
	}
	if input.Platform != "android" || input.BuildType != "debug" || input.AppVersionCode == nil || *input.AppVersionCode != versionCode {
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
			"reason":          "network",
			"detail":          "https://api.example.com/uploads/a.jpg",
			"note":            "13800138000",
			"fallback":        "token=secret",
			"body_length":     12,
			"token":           "secret-token",
			"access_key":      "ak-value",
			"apiKey":          "api-key-value",
			"accessKeyId":     "access-key-id-value",
			"accessKeySecret": "access-key-secret-value",
			"modelKey":        "model-key-value",
			"phone_number":    "13800138000",
			"image_urls":      "https://example.com/uploads/a.jpg",
			"response_body":   "用户填写内容",
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
	for _, forbidden := range []string{"token", "ak-value", "api-key-value", "access-key-id-value", "access-key-secret-value", "model-key-value", "13800138000", "image_urls", "用户填写内容", "detail", "fallback"} {
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

func TestClientAppLogRedisRateLimiterFailsOpen(t *testing.T) {
	client := redis.NewClient(&redis.Options{
		Addr:         "127.0.0.1:1",
		DialTimeout:  10 * time.Millisecond,
		ReadTimeout:  10 * time.Millisecond,
		WriteTimeout: 10 * time.Millisecond,
	})
	defer client.Close()

	limiter, ok := newClientAppLogRateLimiter(client).(*redisRateLimiter)
	if !ok {
		t.Fatalf("newClientAppLogRateLimiter returned %T, want *redisRateLimiter", newClientAppLogRateLimiter(client))
	}
	if !limiter.failOpenOnError {
		t.Fatalf("client app log redis limiter should fail open on redis errors")
	}
	allowed, retryAfter := limiter.Consume("client-app-log-test", time.Now())
	if !allowed || retryAfter != 0 {
		t.Fatalf("client app log redis limiter should allow on redis error: allowed=%v retryAfter=%d", allowed, retryAfter)
	}
}

func TestParseClientAppLogQueryNormalizesAndCapsLimit(t *testing.T) {
	now := time.UnixMilli(10_000)
	values := url.Values{
		"user_id":          {" acct_123 "},
		"event":            {" Chat.Stream_Interrupted "},
		"event_prefix":     {" Auth. "},
		"level":            {"WARN"},
		"platform":         {" Android "},
		"build_type":       {" Debug "},
		"app_version_code": {"12"},
		"app_version_name": {" 1.0.12 "},
		"os_version":       {" Android 15 "},
		"device_model":     {" Brand Model "},
		"since_ms":         {"1234"},
		"limit":            {"500"},
	}
	filter, validationError := parseClientAppLogQuery(values, now)
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if filter.UserID != "acct_123" ||
		filter.Event != "chat.stream_interrupted" ||
		filter.EventPrefix != "auth." ||
		filter.Level != "warn" ||
		filter.Platform != "android" ||
		filter.BuildType != "debug" ||
		filter.AppVersionCode == nil ||
		*filter.AppVersionCode != 12 ||
		filter.AppVersionName != "1.0.12" ||
		filter.OSVersion != "Android 15" ||
		filter.DeviceModel != "Brand Model" ||
		filter.SinceMs != 1234 ||
		filter.Limit != maxClientAppLogInternalListLimit {
		t.Fatalf("filter mismatch: %#v", filter)
	}
}

func TestBuildClientAppLogWhereSupportsEventPrefix(t *testing.T) {
	where, args := buildClientAppLogWhere(ClientAppLogQuery{
		SinceMs:     123,
		EventPrefix: "auth.",
		Limit:       20,
	})
	if where != " WHERE created_at >= ? AND event LIKE ?" {
		t.Fatalf("where = %q, want event prefix LIKE clause", where)
	}
	if len(args) != 2 || args[0] != int64(123) || args[1] != "auth.%" {
		t.Fatalf("args = %#v, want since_ms and auth prefix", args)
	}
}

func TestBuildClientAppLogWherePrefersExactEventOverPrefix(t *testing.T) {
	where, args := buildClientAppLogWhere(ClientAppLogQuery{
		SinceMs:     123,
		Event:       "auth.app_crash",
		EventPrefix: "auth.",
		Limit:       20,
	})
	if where != " WHERE created_at >= ? AND event = ?" {
		t.Fatalf("where = %q, want exact event clause", where)
	}
	if len(args) != 2 || args[0] != int64(123) || args[1] != "auth.app_crash" {
		t.Fatalf("args = %#v, want since_ms and exact event", args)
	}
}

func TestBuildClientAppLogWhereSupportsVersionAndDeviceFilters(t *testing.T) {
	versionCode := 12
	where, args := buildClientAppLogWhere(ClientAppLogQuery{
		SinceMs:        123,
		Platform:       "android",
		BuildType:      "release",
		AppVersionCode: &versionCode,
		AppVersionName: "1.0",
		OSVersion:      "Android 15",
		DeviceModel:    "Brand",
		Limit:          20,
	})
	wantWhere := " WHERE created_at >= ? AND platform = ? AND build_type = ? AND app_version_code = ? AND app_version_name LIKE ? ESCAPE '=' AND os_version LIKE ? ESCAPE '=' AND device_model LIKE ? ESCAPE '='"
	if where != wantWhere {
		t.Fatalf("where = %q, want %q", where, wantWhere)
	}
	wantArgs := []any{int64(123), "android", "release", 12, "1.0%", "Android 15%", "Brand%"}
	if len(args) != len(wantArgs) {
		t.Fatalf("args = %#v, want %#v", args, wantArgs)
	}
	for i := range wantArgs {
		if args[i] != wantArgs[i] {
			t.Fatalf("args[%d] = %#v, want %#v (all args %#v)", i, args[i], wantArgs[i], args)
		}
	}
}

func TestSQLLikePrefixPatternEscapesWildcards(t *testing.T) {
	if got := sqlLikePrefixPattern(" A_%= "); got != "A=_=%==%" {
		t.Fatalf("sqlLikePrefixPattern = %q, want escaped prefix", got)
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
		{name: "invalid app version code", values: url.Values{"app_version_code": {"x"}}, want: "invalid_app_version_code"},
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

func TestClientAppLogSummaryLimitHonorsSmallerCallerLimit(t *testing.T) {
	if got := clientAppLogSummaryLimit(10); got != 10 {
		t.Fatalf("summary limit = %d, want 10", got)
	}
	if got := clientAppLogSummaryLimit(0); got != clientAppLogInternalSummaryLimit {
		t.Fatalf("default summary limit = %d, want %d", got, clientAppLogInternalSummaryLimit)
	}
	if got := clientAppLogSummaryLimit(500); got != clientAppLogInternalSummaryLimit {
		t.Fatalf("capped summary limit = %d, want %d", got, clientAppLogInternalSummaryLimit)
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

func TestHandleCreatePreAuthClientAppLogRejectsNonAuthEvent(t *testing.T) {
	server := &Server{logger: slog.New(slog.NewTextHandler(io.Discard, nil))}
	request := httptest.NewRequest(http.MethodPost, "/api/app/logs/preauth", strings.NewReader(`{"level":"warn","event":"chat.failure","message":"safe"}`))
	recorder := httptest.NewRecorder()

	server.handleCreatePreAuthClientAppLog(recorder, request)

	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusBadRequest)
	}
	if !strings.Contains(recorder.Body.String(), "event_not_allowed") {
		t.Fatalf("body = %q, want event_not_allowed", recorder.Body.String())
	}
}

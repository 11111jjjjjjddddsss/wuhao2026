package app

import (
	"bytes"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestAccessLogAddsRequestIDAndAvoidsSensitiveFields(t *testing.T) {
	var logs bytes.Buffer
	logger := slog.New(slog.NewJSONHandler(&logs, nil))
	server := &Server{
		logger: logger,
		mux:    http.NewServeMux(),
	}
	server.mux.HandleFunc("GET /test", func(w http.ResponseWriter, _ *http.Request) {
		_, _ = io.WriteString(w, "ok")
	})

	request := httptest.NewRequest(http.MethodGet, "/test?token=secret-token", nil)
	request.Header.Set(requestIDHeader, "req_test-123")
	request.Header.Set("Authorization", "Bearer secret-token")
	request.Header.Set("User-Agent", "unit-test")
	recorder := httptest.NewRecorder()

	server.Handler().ServeHTTP(recorder, request)

	if got := recorder.Header().Get(requestIDHeader); got != "req_test-123" {
		t.Fatalf("%s = %q, want req_test-123", requestIDHeader, got)
	}
	output := logs.String()
	for _, want := range []string{`"msg":"http_request"`, `"request_id":"req_test-123"`, `"path":"/test"`, `"status":200`} {
		if !strings.Contains(output, want) {
			t.Fatalf("log output missing %s: %s", want, output)
		}
	}
	for _, forbidden := range []string{"secret-token", "Authorization", "token=secret"} {
		if strings.Contains(output, forbidden) {
			t.Fatalf("log output leaked %q: %s", forbidden, output)
		}
	}
}

func TestSanitizeLoggedUserAgentRedactsClientControlledSecrets(t *testing.T) {
	cases := []string{
		"Mozilla token=secret-token",
		"Client 13800138000",
		"Aliyun LTAIabcdef",
	}
	for _, raw := range cases {
		if got := sanitizeLoggedUserAgent(raw); got != "[redacted]" {
			t.Fatalf("sanitizeLoggedUserAgent(%q) = %q, want [redacted]", raw, got)
		}
	}
	if got := sanitizeLoggedUserAgent("Mozilla/5.0 NongjiTest"); got != "Mozilla/5.0 NongjiTest" {
		t.Fatalf("expected ordinary user agent preserved, got %q", got)
	}
}

func TestAccessLogMarksSlowRequest(t *testing.T) {
	t.Setenv("ACCESS_LOG_SLOW_MS", "1")
	var logs bytes.Buffer
	logger := slog.New(slog.NewJSONHandler(&logs, nil))
	server := &Server{
		logger: logger,
		mux:    http.NewServeMux(),
	}
	server.mux.HandleFunc("GET /slow", func(w http.ResponseWriter, _ *http.Request) {
		time.Sleep(5 * time.Millisecond)
		_, _ = io.WriteString(w, "ok")
	})

	server.Handler().ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/slow", nil))

	output := logs.String()
	for _, want := range []string{`"level":"WARN"`, `"msg":"http_request_slow"`, `"path":"/slow"`, `"status":200`} {
		if !strings.Contains(output, want) {
			t.Fatalf("log output missing %s: %s", want, output)
		}
	}
}

func TestAccessLogSuppressesHealthyHealthz(t *testing.T) {
	var logs bytes.Buffer
	logger := slog.New(slog.NewJSONHandler(&logs, nil))
	server := &Server{
		logger: logger,
		mux:    http.NewServeMux(),
	}
	server.mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, _ *http.Request) {
		_, _ = io.WriteString(w, "ok")
	})

	server.Handler().ServeHTTP(httptest.NewRecorder(), httptest.NewRequest(http.MethodGet, "/healthz", nil))

	if output := logs.String(); strings.TrimSpace(output) != "" {
		t.Fatalf("healthz should be suppressed, got logs: %s", output)
	}
}

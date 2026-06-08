package app

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"
	"time"
)

func TestOpenStreamUsesUnifiedTemperature(t *testing.T) {
	var captured map[string]any
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/chat/completions" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	response, err := NewBailianClient().OpenStream(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "hello"}},
	)
	if err != nil {
		t.Fatalf("open stream: %v", err)
	}
	defer response.Body.Close()

	if got := captured["model"]; got != "qwen3.5-plus" {
		t.Fatalf("model mismatch: %#v", got)
	}
	if got := captured["stream"]; got != true {
		t.Fatalf("stream mismatch: %#v", got)
	}
	if got, ok := captured["temperature"].(float64); !ok || got != unifiedModelTemperature {
		t.Fatalf("temperature mismatch: %#v", captured["temperature"])
	}
	extraBody, ok := captured["extra_body"].(map[string]any)
	if !ok {
		t.Fatalf("missing extra_body: %#v", captured["extra_body"])
	}
	if got := extraBody["enable_thinking"]; got != false {
		t.Fatalf("enable_thinking mismatch: %#v", got)
	}
	if got := extraBody["enable_search"]; got != true {
		t.Fatalf("enable_search mismatch: %#v", got)
	}
	searchOptions, ok := extraBody["search_options"].(map[string]any)
	if !ok {
		t.Fatalf("missing search_options: %#v", extraBody["search_options"])
	}
	if got := searchOptions["search_strategy"]; got != "turbo" {
		t.Fatalf("search_strategy mismatch: %#v", got)
	}
	if got := searchOptions["forced_search"]; got != false {
		t.Fatalf("forced_search mismatch: %#v", got)
	}
}

func TestBailianKeysSupportDedicatedSlotsAndDeduplicate(t *testing.T) {
	t.Setenv("DASHSCOPE_API_KEY", "legacy")
	t.Setenv("DASHSCOPE_API_KEY_1", "primary")
	t.Setenv("DASHSCOPE_API_KEY_2", "second")
	t.Setenv("DASHSCOPE_API_KEY_3", "third")
	t.Setenv("DASHSCOPE_API_KEYS", "second, fourth; fifth\nsixth; legacy")

	got := NewBailianClient().keys()
	want := []string{"primary", "second", "third", "legacy", "fourth", "fifth", "sixth"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("keys mismatch:\n got %#v\nwant %#v", got, want)
	}
}

func TestNewBailianClientConfiguresStreamingSafeTransport(t *testing.T) {
	t.Setenv("DASHSCOPE_DIAL_TIMEOUT_SECONDS", "8")
	t.Setenv("DASHSCOPE_TLS_HANDSHAKE_TIMEOUT_SECONDS", "9")
	t.Setenv("DASHSCOPE_RESPONSE_HEADER_TIMEOUT_SECONDS", "70")
	t.Setenv("DASHSCOPE_IDLE_CONN_TIMEOUT_SECONDS", "80")

	client := NewBailianClient()
	if client.httpClient.Timeout != 0 {
		t.Fatalf("http client Timeout = %v, want 0 so SSE body can stream", client.httpClient.Timeout)
	}
	transport, ok := client.httpClient.Transport.(*http.Transport)
	if !ok {
		t.Fatalf("Transport = %T, want *http.Transport", client.httpClient.Transport)
	}
	if transport.TLSHandshakeTimeout != 9*time.Second {
		t.Fatalf("TLSHandshakeTimeout = %v, want 9s", transport.TLSHandshakeTimeout)
	}
	if transport.ResponseHeaderTimeout != 70*time.Second {
		t.Fatalf("ResponseHeaderTimeout = %v, want 70s", transport.ResponseHeaderTimeout)
	}
	if transport.IdleConnTimeout != 80*time.Second {
		t.Fatalf("IdleConnTimeout = %v, want 80s", transport.IdleConnTimeout)
	}
	if transport.MaxIdleConns != 100 || transport.MaxIdleConnsPerHost != 10 {
		t.Fatalf("idle pool = %d/%d, want 100/10", transport.MaxIdleConns, transport.MaxIdleConnsPerHost)
	}
}

func TestReadLimitedResponseBodyCapsPayload(t *testing.T) {
	body, err := readLimitedResponseBody(strings.NewReader(strings.Repeat("x", 10)), 4)
	if !errors.Is(err, errResponseBodyTooLarge) {
		t.Fatalf("readLimitedResponseBody error = %v, want errResponseBodyTooLarge", err)
	}
	if got := string(body); got != "xxxx" {
		t.Fatalf("body = %q, want capped preview", got)
	}
}

func TestOpenStreamFailsOverToNextKeyOnRateLimit(t *testing.T) {
	authHeaders := []string{}
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeaders = append(authHeaders, r.Header.Get("Authorization"))
		if r.Header.Get("Authorization") == "Bearer primary-key" {
			w.WriteHeader(http.StatusTooManyRequests)
			_, _ = w.Write([]byte(`{"code":"Throttling","message":"Requests rate limit exceeded"}`))
			return
		}
		if r.Header.Get("Authorization") != "Bearer secondary-key" {
			t.Fatalf("unexpected authorization: %s", r.Header.Get("Authorization"))
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "")
	t.Setenv("DASHSCOPE_API_KEY_1", "primary-key")
	t.Setenv("DASHSCOPE_API_KEY_2", "secondary-key")
	t.Setenv("DASHSCOPE_API_KEY_3", "")
	t.Setenv("DASHSCOPE_API_KEYS", "")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	response, err := NewBailianClient().OpenStream(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "hello"}},
	)
	if err != nil {
		t.Fatalf("open stream: %v", err)
	}
	defer response.Body.Close()
	body, _ := io.ReadAll(response.Body)

	if response.StatusCode != http.StatusOK {
		t.Fatalf("unexpected status: %d body=%s", response.StatusCode, string(body))
	}
	wantAuthHeaders := []string{"Bearer primary-key", "Bearer secondary-key"}
	if !reflect.DeepEqual(authHeaders, wantAuthHeaders) {
		t.Fatalf("authorization sequence mismatch:\n got %#v\nwant %#v", authHeaders, wantAuthHeaders)
	}
}

func TestOpenStreamPrefersPrimaryKeyWhileHealthy(t *testing.T) {
	authHeaders := []string{}
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeaders = append(authHeaders, r.Header.Get("Authorization"))
		if r.Header.Get("Authorization") != "Bearer primary-key" {
			t.Fatalf("unexpected authorization: %s", r.Header.Get("Authorization"))
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "")
	t.Setenv("DASHSCOPE_API_KEY_1", "primary-key")
	t.Setenv("DASHSCOPE_API_KEY_2", "secondary-key")
	t.Setenv("DASHSCOPE_API_KEY_3", "")
	t.Setenv("DASHSCOPE_API_KEYS", "")
	t.Setenv("DASHSCOPE_KEY_SELECTION_MODE", "fallback")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	client := NewBailianClient()
	for i := 0; i < 2; i++ {
		response, err := client.OpenStream(
			context.Background(),
			[]BailianMessage{{Role: "user", Content: "hello"}},
		)
		if err != nil {
			t.Fatalf("open stream %d: %v", i+1, err)
		}
		_ = response.Body.Close()
	}

	wantAuthHeaders := []string{"Bearer primary-key", "Bearer primary-key"}
	if !reflect.DeepEqual(authHeaders, wantAuthHeaders) {
		t.Fatalf("authorization sequence mismatch:\n got %#v\nwant %#v", authHeaders, wantAuthHeaders)
	}
}

func TestOpenStreamUsesRoundRobinInSmoothMode(t *testing.T) {
	authHeaders := []string{}
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeaders = append(authHeaders, r.Header.Get("Authorization"))
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "")
	t.Setenv("DASHSCOPE_API_KEY_1", "primary-key")
	t.Setenv("DASHSCOPE_API_KEY_2", "secondary-key")
	t.Setenv("DASHSCOPE_API_KEY_3", "")
	t.Setenv("DASHSCOPE_API_KEYS", "")
	t.Setenv("DASHSCOPE_KEY_SELECTION_MODE", "round_robin")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	client := NewBailianClient()
	for i := 0; i < 4; i++ {
		response, err := client.OpenStream(
			context.Background(),
			[]BailianMessage{{Role: "user", Content: "hello"}},
		)
		if err != nil {
			t.Fatalf("open stream %d: %v", i+1, err)
		}
		_ = response.Body.Close()
	}

	wantAuthHeaders := []string{
		"Bearer primary-key",
		"Bearer secondary-key",
		"Bearer primary-key",
		"Bearer secondary-key",
	}
	if !reflect.DeepEqual(authHeaders, wantAuthHeaders) {
		t.Fatalf("authorization sequence mismatch:\n got %#v\nwant %#v", authHeaders, wantAuthHeaders)
	}
}

func TestOpenStreamAutoModeTurnsOnRoundRobinWhenRequestBurstIsHigh(t *testing.T) {
	authHeaders := []string{}
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeaders = append(authHeaders, r.Header.Get("Authorization"))
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "")
	t.Setenv("DASHSCOPE_API_KEY_1", "primary-key")
	t.Setenv("DASHSCOPE_API_KEY_2", "secondary-key")
	t.Setenv("DASHSCOPE_API_KEY_3", "")
	t.Setenv("DASHSCOPE_API_KEYS", "")
	t.Setenv("DASHSCOPE_KEY_SELECTION_MODE", "auto")
	t.Setenv("DASHSCOPE_AUTO_ROUND_ROBIN_MIN_REQUESTS", "2")
	t.Setenv("DASHSCOPE_AUTO_ROUND_ROBIN_WINDOW_SECONDS", "60")
	t.Setenv("DASHSCOPE_AUTO_ROUND_ROBIN_HOLD_SECONDS", "60")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	client := NewBailianClient()
	for i := 0; i < 4; i++ {
		response, err := client.OpenStream(
			context.Background(),
			[]BailianMessage{{Role: "user", Content: "hello"}},
		)
		if err != nil {
			t.Fatalf("open stream %d: %v", i+1, err)
		}
		_ = response.Body.Close()
	}

	wantAuthHeaders := []string{
		"Bearer primary-key",
		"Bearer primary-key",
		"Bearer secondary-key",
		"Bearer primary-key",
	}
	if !reflect.DeepEqual(authHeaders, wantAuthHeaders) {
		t.Fatalf("authorization sequence mismatch:\n got %#v\nwant %#v", authHeaders, wantAuthHeaders)
	}
}

func TestOpenStreamSkipsCoolingKeyOnNextRequest(t *testing.T) {
	authHeaders := []string{}
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeaders = append(authHeaders, r.Header.Get("Authorization"))
		if r.Header.Get("Authorization") == "Bearer primary-key" {
			w.WriteHeader(http.StatusTooManyRequests)
			_, _ = w.Write([]byte(`{"code":"Throttling","message":"Requests rate limit exceeded"}`))
			return
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "")
	t.Setenv("DASHSCOPE_API_KEY_1", "primary-key")
	t.Setenv("DASHSCOPE_API_KEY_2", "secondary-key")
	t.Setenv("DASHSCOPE_API_KEY_3", "")
	t.Setenv("DASHSCOPE_API_KEYS", "")
	t.Setenv("DASHSCOPE_KEY_SELECTION_MODE", "fallback")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	client := NewBailianClient()
	for i := 0; i < 2; i++ {
		response, err := client.OpenStream(
			context.Background(),
			[]BailianMessage{{Role: "user", Content: "hello"}},
		)
		if err != nil {
			t.Fatalf("open stream %d: %v", i+1, err)
		}
		_ = response.Body.Close()
	}

	wantAuthHeaders := []string{"Bearer primary-key", "Bearer secondary-key", "Bearer secondary-key"}
	if !reflect.DeepEqual(authHeaders, wantAuthHeaders) {
		t.Fatalf("authorization sequence mismatch:\n got %#v\nwant %#v", authHeaders, wantAuthHeaders)
	}
}

func TestOpenStreamCanDisableKeyCooldown(t *testing.T) {
	authHeaders := []string{}
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeaders = append(authHeaders, r.Header.Get("Authorization"))
		if r.Header.Get("Authorization") == "Bearer primary-key" {
			w.WriteHeader(http.StatusTooManyRequests)
			_, _ = w.Write([]byte(`{"code":"Throttling","message":"Requests rate limit exceeded"}`))
			return
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "")
	t.Setenv("DASHSCOPE_API_KEY_1", "primary-key")
	t.Setenv("DASHSCOPE_API_KEY_2", "secondary-key")
	t.Setenv("DASHSCOPE_API_KEY_3", "")
	t.Setenv("DASHSCOPE_API_KEYS", "")
	t.Setenv("DASHSCOPE_KEY_COOLDOWN_SECONDS", "0")
	t.Setenv("DASHSCOPE_KEY_SELECTION_MODE", "fallback")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	client := NewBailianClient()
	for i := 0; i < 2; i++ {
		response, err := client.OpenStream(
			context.Background(),
			[]BailianMessage{{Role: "user", Content: "hello"}},
		)
		if err != nil {
			t.Fatalf("open stream %d: %v", i+1, err)
		}
		_ = response.Body.Close()
	}

	wantAuthHeaders := []string{
		"Bearer primary-key",
		"Bearer secondary-key",
		"Bearer primary-key",
		"Bearer secondary-key",
	}
	if !reflect.DeepEqual(authHeaders, wantAuthHeaders) {
		t.Fatalf("authorization sequence mismatch:\n got %#v\nwant %#v", authHeaders, wantAuthHeaders)
	}
}

func TestOpenStreamFailoverStatusBoundaries(t *testing.T) {
	tests := []struct {
		name          string
		primaryStatus int
		primaryBody   string
		wantHeaders   []string
		wantStatus    int
	}{
		{
			name:          "unauthorized",
			primaryStatus: http.StatusUnauthorized,
			primaryBody:   `{"code":"InvalidApiKey"}`,
			wantHeaders:   []string{"Bearer primary-key", "Bearer secondary-key"},
			wantStatus:    http.StatusOK,
		},
		{
			name:          "forbidden",
			primaryStatus: http.StatusForbidden,
			primaryBody:   `{"code":"Forbidden"}`,
			wantHeaders:   []string{"Bearer primary-key", "Bearer secondary-key"},
			wantStatus:    http.StatusOK,
		},
		{
			name:          "quota bad request",
			primaryStatus: http.StatusBadRequest,
			primaryBody:   `{"message":"Allocated quota has been exceeded"}`,
			wantHeaders:   []string{"Bearer primary-key", "Bearer secondary-key"},
			wantStatus:    http.StatusOK,
		},
		{
			name:          "ordinary bad request",
			primaryStatus: http.StatusBadRequest,
			primaryBody:   `{"message":"invalid request payload"}`,
			wantHeaders:   []string{"Bearer primary-key"},
			wantStatus:    http.StatusBadRequest,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			authHeaders := []string{}
			modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				authHeaders = append(authHeaders, r.Header.Get("Authorization"))
				if r.Header.Get("Authorization") == "Bearer primary-key" {
					w.WriteHeader(tt.primaryStatus)
					_, _ = w.Write([]byte(tt.primaryBody))
					return
				}
				w.Header().Set("Content-Type", "text/event-stream")
				_, _ = w.Write([]byte("data: [DONE]\n\n"))
			}))
			defer modelServer.Close()

			t.Setenv("DASHSCOPE_API_KEY", "")
			t.Setenv("DASHSCOPE_API_KEY_1", "primary-key")
			t.Setenv("DASHSCOPE_API_KEY_2", "secondary-key")
			t.Setenv("DASHSCOPE_API_KEY_3", "")
			t.Setenv("DASHSCOPE_API_KEYS", "")
			t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

			response, err := NewBailianClient().OpenStream(
				context.Background(),
				[]BailianMessage{{Role: "user", Content: "hello"}},
			)
			if err != nil {
				t.Fatalf("open stream: %v", err)
			}
			defer response.Body.Close()

			if response.StatusCode != tt.wantStatus {
				body, _ := io.ReadAll(response.Body)
				t.Fatalf("status = %d body=%s, want %d", response.StatusCode, string(body), tt.wantStatus)
			}
			if !reflect.DeepEqual(authHeaders, tt.wantHeaders) {
				t.Fatalf("authorization sequence mismatch:\n got %#v\nwant %#v", authHeaders, tt.wantHeaders)
			}
		})
	}
}

func TestGenerateDailyAgriCardUsesUnifiedTemperature(t *testing.T) {
	var captured map[string]any
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/responses" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"output":[{"type":"web_search_call","action":{"sources":[{"type":"url","url":"https://www.example.com/news-1"},{"type":"url","url":"https://www.example.com/news-2"}]}},{"type":"message","content":[{"type":"output_text","text":"{\"card_name\":\"今日农情\",\"items\":[]}"}]}],"output_text":""}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	content, sources, err := NewBailianClient().GenerateDailyAgriCard(
		context.Background(),
		[]BailianMessage{
			{Role: "system", Content: "你是一个只输出 JSON 的助手。"},
			{Role: "user", Content: "生成今日农情"},
		},
	)
	if err != nil {
		t.Fatalf("generate daily agri card: %v", err)
	}
	if content != "{\"card_name\":\"今日农情\",\"items\":[]}" {
		t.Fatalf("content mismatch: %q", content)
	}
	if len(sources) != 2 {
		t.Fatalf("source count mismatch: %d", len(sources))
	}

	if got := captured["model"]; got != dailyAgriCardModel {
		t.Fatalf("model mismatch: %#v", got)
	}
	if got := captured["temperature"]; got != unifiedModelTemperature {
		t.Fatalf("temperature mismatch: %#v", got)
	}
	if got := captured["tool_choice"]; got != "required" {
		t.Fatalf("tool_choice mismatch: %#v", got)
	}
	if got := captured["store"]; got != false {
		t.Fatalf("store mismatch: %#v", got)
	}
	tools, ok := captured["tools"].([]any)
	if !ok || len(tools) != 1 {
		t.Fatalf("tools mismatch: %#v", captured["tools"])
	}
	reasoning, ok := captured["reasoning"].(map[string]any)
	if !ok {
		t.Fatalf("reasoning mismatch: %#v", captured["reasoning"])
	}
	if got := reasoning["effort"]; got != "none" {
		t.Fatalf("reasoning.effort mismatch: %#v", got)
	}
	if got := captured["instructions"]; got != "你是一个只输出 JSON 的助手。" {
		t.Fatalf("instructions mismatch: %#v", got)
	}
	if got := captured["input"]; got != "生成今日农情" {
		t.Fatalf("input mismatch: %#v", got)
	}
}

func TestGenerateDailyAgriCardStatusErrorDoesNotIncludeBody(t *testing.T) {
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/responses" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		w.WriteHeader(http.StatusBadGateway)
		_, _ = w.Write([]byte(`{"message":"https://api.example.com token=secret 13800138000"}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	_, _, err := NewBailianClient().GenerateDailyAgriCard(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "生成今日农情"}},
	)
	if err == nil {
		t.Fatalf("expected error")
	}
	got := err.Error()
	if got != "dashscope status 502" {
		t.Fatalf("error = %q, want sanitized status only", got)
	}
	for _, forbidden := range []string{"https://", "token", "13800138000"} {
		if strings.Contains(got, forbidden) {
			t.Fatalf("error leaked %q: %s", forbidden, got)
		}
	}
}

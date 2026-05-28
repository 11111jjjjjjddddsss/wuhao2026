package app

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"reflect"
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
	t.Setenv("DASHSCOPE_API_KEY", "primary")
	t.Setenv("DASHSCOPE_API_KEY_1", "primary")
	t.Setenv("DASHSCOPE_API_KEY_2", "second")
	t.Setenv("DASHSCOPE_API_KEY_3", "third")
	t.Setenv("DASHSCOPE_API_KEYS", "second, fourth; fifth\nsixth")

	got := NewBailianClient().keys()
	want := []string{"primary", "second", "third", "fourth", "fifth", "sixth"}
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

func TestGenerateDailyAgriCardUsesUnifiedTemperature(t *testing.T) {
	var captured map[string]any
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/services/aigc/text-generation/generation" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"output":{"choices":[{"message":{"content":"{\"items\":[]}"}}]}}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("DASHSCOPE_BASE_URL", modelServer.URL)

	_, _, err := NewBailianClient().GenerateDailyAgriCard(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "生成今日农情"}},
	)
	if err != nil {
		t.Fatalf("generate daily agri card: %v", err)
	}

	if got := captured["model"]; got != dailyAgriCardModel {
		t.Fatalf("model mismatch: %#v", got)
	}
	parameters, ok := captured["parameters"].(map[string]any)
	if !ok {
		t.Fatalf("missing parameters: %#v", captured["parameters"])
	}
	if got, ok := parameters["temperature"].(float64); !ok || got != unifiedModelTemperature {
		t.Fatalf("temperature mismatch: %#v", parameters["temperature"])
	}
	if got := parameters["enable_thinking"]; got != false {
		t.Fatalf("enable_thinking mismatch: %#v", got)
	}
	if got := parameters["enable_search"]; got != true {
		t.Fatalf("enable_search mismatch: %#v", got)
	}
	searchOptions, ok := parameters["search_options"].(map[string]any)
	if !ok {
		t.Fatalf("missing search_options: %#v", parameters["search_options"])
	}
	if got := searchOptions["search_strategy"]; got != dailyAgriSearchStrategy {
		t.Fatalf("search_strategy mismatch: %#v", got)
	}
	if got := searchOptions["forced_search"]; got != true {
		t.Fatalf("forced_search mismatch: %#v", got)
	}
	if got := searchOptions["enable_source"]; got != true {
		t.Fatalf("enable_source mismatch: %#v", got)
	}
	if got, ok := searchOptions["freshness"].(float64); !ok || got != 7 {
		t.Fatalf("freshness mismatch: %#v", searchOptions["freshness"])
	}
}

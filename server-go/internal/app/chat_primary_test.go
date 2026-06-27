package app

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestPrimaryChatClientChatModeUsesMinimalOpenAICompatibleBodyByDefault(t *testing.T) {
	var captured map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/chat/completions" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer sk-primary-test" {
			t.Fatalf("authorization header mismatch: %q", got)
		}
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer server.Close()

	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_MODE", "chat")
	t.Setenv("CHAT_PRIMARY_BASE_URL", server.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "sk-primary-test")
	t.Setenv("CHAT_PRIMARY_MODEL", "gpt-5.5")

	response, err := NewPrimaryChatClientFromEnv().OpenStream(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "hello"}},
		BailianStreamOptions{},
	)
	if err != nil {
		t.Fatalf("open primary stream: %v", err)
	}
	defer response.Body.Close()

	if got := captured["model"]; got != "gpt-5.5" {
		t.Fatalf("model mismatch: %#v", got)
	}
	if got := captured["stream"]; got != true {
		t.Fatalf("stream mismatch: %#v", got)
	}
	if got := captured["messages"]; got == nil {
		t.Fatalf("missing messages: %#v", captured)
	}
	if _, ok := captured["temperature"]; ok {
		t.Fatalf("primary chat should leave temperature to provider default: %#v", captured["temperature"])
	}
	if _, ok := captured["top_p"]; ok {
		t.Fatalf("primary chat should leave top_p to provider default: %#v", captured["top_p"])
	}
	if _, ok := captured["enable_thinking"]; ok {
		t.Fatalf("primary chat should leave thinking to provider default unless explicitly configured: %#v", captured["enable_thinking"])
	}
	if _, ok := captured["reasoning_effort"]; ok {
		t.Fatalf("primary chat should leave reasoning effort to provider default unless explicitly configured: %#v", captured["reasoning_effort"])
	}
	if _, ok := captured["thinking_budget"]; ok {
		t.Fatalf("thinking_budget should be omitted when primary thinking is disabled: %#v", captured["thinking_budget"])
	}
	if _, ok := captured["enable_search"]; ok {
		t.Fatalf("primary chat should not send custom search params by default: %#v", captured["enable_search"])
	}
	if _, ok := captured["search_options"]; ok {
		t.Fatalf("primary chat should not send search_options by default: %#v", captured["search_options"])
	}
	if _, ok := captured["stream_options"]; ok {
		t.Fatalf("primary chat should not request usage chunks by default: %#v", captured["stream_options"])
	}
}

func TestPrimaryChatClientChatModeCanOptIntoSearchAndNoThinking(t *testing.T) {
	var captured map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer server.Close()

	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_MODE", "chat")
	t.Setenv("CHAT_PRIMARY_BASE_URL", server.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "sk-primary-test")
	t.Setenv("CHAT_PRIMARY_CHAT_ENABLE_SEARCH", "true")
	t.Setenv("CHAT_PRIMARY_CHAT_DISABLE_THINKING", "true")
	t.Setenv("CHAT_PRIMARY_CHAT_INCLUDE_USAGE", "true")
	t.Setenv("CHAT_PRIMARY_FORCE_SEARCH", "true")
	t.Setenv("CHAT_PRIMARY_REASONING_EFFORT", "none")

	response, err := NewPrimaryChatClientFromEnv().OpenStream(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "hello"}},
		BailianStreamOptions{},
	)
	if err != nil {
		t.Fatalf("open primary stream: %v", err)
	}
	defer response.Body.Close()

	if got := captured["enable_thinking"]; got != false {
		t.Fatalf("enable_thinking mismatch: %#v", got)
	}
	if got := captured["reasoning_effort"]; got != "none" {
		t.Fatalf("reasoning_effort mismatch: %#v", got)
	}
	streamOptions, ok := captured["stream_options"].(map[string]any)
	if !ok || streamOptions["include_usage"] != true {
		t.Fatalf("stream_options mismatch: %#v", captured["stream_options"])
	}
	if got := captured["enable_search"]; got != true {
		t.Fatalf("enable_search mismatch: %#v", got)
	}
	searchOptions, ok := captured["search_options"].(map[string]any)
	if !ok {
		t.Fatalf("missing search_options: %#v", captured["search_options"])
	}
	if got := searchOptions["forced_search"]; got != true {
		t.Fatalf("forced_search mismatch: %#v", got)
	}
}

func TestPrimaryChatClientDoesNotForceSearchForImageMessages(t *testing.T) {
	var captured map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer server.Close()

	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_MODE", "chat")
	t.Setenv("CHAT_PRIMARY_BASE_URL", server.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "sk-primary-test")
	t.Setenv("CHAT_PRIMARY_CHAT_ENABLE_SEARCH", "true")
	t.Setenv("CHAT_PRIMARY_FORCE_SEARCH", "true")

	response, err := NewPrimaryChatClientFromEnv().OpenStream(
		context.Background(),
		[]BailianMessage{{
			Role: "user",
			Content: []map[string]any{
				{"type": "text", "text": "看图诊断"},
				{"type": "image_url", "image_url": map[string]any{"url": "https://example.com/a.jpg"}},
			},
		}},
		BailianStreamOptions{ForceSearch: true},
	)
	if err != nil {
		t.Fatalf("open primary stream: %v", err)
	}
	defer response.Body.Close()

	searchOptions, ok := captured["search_options"].(map[string]any)
	if !ok {
		t.Fatalf("missing search_options: %#v", captured["search_options"])
	}
	if got := searchOptions["forced_search"]; got != false {
		t.Fatalf("forced_search for image message = %#v, want false", got)
	}
	if _, ok := captured["reasoning_effort"]; ok {
		t.Fatalf("reasoning_effort should be omitted when not configured: %#v", captured["reasoning_effort"])
	}
}

func TestPrimaryChatClientResponsesModeUsesAutoWebSearchAndLowReasoning(t *testing.T) {
	var captured map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/responses" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer sk-primary-test" {
			t.Fatalf("authorization header mismatch: %q", got)
		}
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {\"response\":{\"usage\":{\"input_tokens\":1,\"output_tokens\":1,\"total_tokens\":2}}}\n\n"))
	}))
	defer server.Close()

	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_MODE", "responses")
	t.Setenv("CHAT_PRIMARY_BASE_URL", server.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "sk-primary-test")
	t.Setenv("CHAT_PRIMARY_MODEL", "gpt-5.5")
	t.Setenv("CHAT_PRIMARY_REASONING_EFFORT", "none")

	response, err := NewPrimaryChatClientFromEnv().OpenStream(
		context.Background(),
		[]BailianMessage{
			{Role: "system", Content: "系统锚点"},
			{Role: "user", Content: "今天尿素价格怎么样"},
		},
		BailianStreamOptions{ForceSearch: true},
	)
	if err != nil {
		t.Fatalf("open primary responses stream: %v", err)
	}
	defer response.Body.Close()

	if got := captured["model"]; got != "gpt-5.5" {
		t.Fatalf("model mismatch: %#v", got)
	}
	if got := captured["stream"]; got != true {
		t.Fatalf("stream mismatch: %#v", got)
	}
	if _, ok := captured["temperature"]; ok {
		t.Fatalf("responses mode should leave temperature to provider default: %#v", captured["temperature"])
	}
	if _, ok := captured["max_output_tokens"]; ok {
		t.Fatalf("responses mode should not hard-cap output tokens: %#v", captured["max_output_tokens"])
	}
	if _, ok := captured["max_tokens"]; ok {
		t.Fatalf("responses mode should not set max_tokens: %#v", captured["max_tokens"])
	}
	if got := captured["tool_choice"]; got != "auto" {
		t.Fatalf("tool_choice mismatch: %#v", got)
	}
	tools, ok := captured["tools"].([]any)
	if !ok || len(tools) != 1 {
		t.Fatalf("tools mismatch: %#v", captured["tools"])
	}
	tool, _ := tools[0].(map[string]any)
	if got := tool["type"]; got != "web_search" {
		t.Fatalf("tool type mismatch: %#v", got)
	}
	if got := tool["search_context_size"]; got != "low" {
		t.Fatalf("search_context_size mismatch: %#v", got)
	}
	reasoning, ok := captured["reasoning"].(map[string]any)
	if !ok {
		t.Fatalf("missing reasoning: %#v", captured["reasoning"])
	}
	if got := reasoning["effort"]; got != "high" {
		t.Fatalf("responses reasoning effort should ignore chat-mode env and default high, got %#v", got)
	}
	instructions, _ := captured["instructions"].(string)
	if !strings.Contains(instructions, "系统锚点") || !strings.Contains(instructions, "仅当本轮问题涉及最新信息") {
		t.Fatalf("instructions missing expected text: %q", instructions)
	}
	input, ok := captured["input"].([]any)
	if !ok || len(input) != 1 {
		t.Fatalf("input mismatch: %#v", captured["input"])
	}
	first, _ := input[0].(map[string]any)
	if got := first["role"]; got != "user" {
		t.Fatalf("input role mismatch: %#v", got)
	}
	if got := first["content"]; got != "今天尿素价格怎么样" {
		t.Fatalf("input content mismatch: %#v", got)
	}
}

func TestPrimaryChatResponsesModeConvertsImageURLContent(t *testing.T) {
	var captured map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {}\n\n"))
	}))
	defer server.Close()

	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_MODE", "responses")
	t.Setenv("CHAT_PRIMARY_BASE_URL", server.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "sk-primary-test")

	response, err := NewPrimaryChatClientFromEnv().OpenStream(
		context.Background(),
		[]BailianMessage{{
			Role: "user",
			Content: []map[string]any{
				{"type": "text", "text": "看图诊断"},
				{"type": "image_url", "image_url": map[string]any{"url": "https://example.com/a.jpg"}},
			},
		}},
		BailianStreamOptions{},
	)
	if err != nil {
		t.Fatalf("open primary responses stream: %v", err)
	}
	defer response.Body.Close()

	input := captured["input"].([]any)
	first := input[0].(map[string]any)
	content := first["content"].([]any)
	if got := content[0].(map[string]any)["type"]; got != "input_text" {
		t.Fatalf("text part type = %#v", got)
	}
	if got := content[1].(map[string]any)["type"]; got != "input_image" {
		t.Fatalf("image part type = %#v", got)
	}
	if got := content[1].(map[string]any)["image_url"]; got != "https://example.com/a.jpg" {
		t.Fatalf("image_url = %#v", got)
	}
}

func TestPrimaryChatClientRoundRobinsConfiguredKeyPool(t *testing.T) {
	authHeaders := []string{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeaders = append(authHeaders, r.Header.Get("Authorization"))
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {}\n\n"))
	}))
	defer server.Close()

	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_MODE", "responses")
	t.Setenv("CHAT_PRIMARY_BASE_URL", server.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "")
	t.Setenv("CHAT_PRIMARY_API_KEYS", "sk-primary-a;sk-primary-b;sk-primary-c")

	client := NewPrimaryChatClientFromEnv()
	for i := 0; i < 3; i++ {
		response, err := client.OpenStream(
			context.Background(),
			[]BailianMessage{{Role: "user", Content: "hello"}},
			BailianStreamOptions{},
		)
		if err != nil {
			t.Fatalf("open primary stream %d: %v", i, err)
		}
		_ = response.Body.Close()
	}

	want := []string{"Bearer sk-primary-a", "Bearer sk-primary-b", "Bearer sk-primary-c"}
	if strings.Join(authHeaders, "|") != strings.Join(want, "|") {
		t.Fatalf("authorization rotation = %#v, want %#v", authHeaders, want)
	}
}

func TestPrimaryChatClientRetriesRetryableStatusWithNextKey(t *testing.T) {
	authHeaders := []string{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeaders = append(authHeaders, r.Header.Get("Authorization"))
		if len(authHeaders) == 1 {
			w.WriteHeader(http.StatusServiceUnavailable)
			_, _ = w.Write([]byte(`{"error":"temporary"}`))
			return
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {}\n\n"))
	}))
	defer server.Close()

	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_MODE", "responses")
	t.Setenv("CHAT_PRIMARY_BASE_URL", server.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "")
	t.Setenv("CHAT_PRIMARY_API_KEYS", "sk-primary-a;sk-primary-b")
	t.Setenv("CHAT_PRIMARY_KEY_MAX_ATTEMPTS", "2")

	response, err := NewPrimaryChatClientFromEnv().OpenStream(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "hello"}},
		BailianStreamOptions{},
	)
	if err != nil {
		t.Fatalf("open primary stream: %v", err)
	}
	_ = response.Body.Close()

	want := []string{"Bearer sk-primary-a", "Bearer sk-primary-b"}
	if strings.Join(authHeaders, "|") != strings.Join(want, "|") {
		t.Fatalf("authorization retry rotation = %#v, want %#v", authHeaders, want)
	}
}

func TestPrimaryChatKeyEntriesAcceptLabeledValuesAndDeduplicate(t *testing.T) {
	t.Setenv("CHAT_PRIMARY_API_KEY", "")
	t.Setenv("CHAT_PRIMARY_API_KEYS", "吴浩-a sk-primary-a;label=sk-primary-b;label:sk-primary-c;sk-primary-b")

	entries := primaryChatKeyEntries()
	if len(entries) != 3 {
		t.Fatalf("key pool size = %d, want 3", len(entries))
	}
	want := []string{"sk-primary-a", "sk-primary-b", "sk-primary-c"}
	got := []string{}
	for _, entry := range entries {
		got = append(got, entry.Value)
	}
	if strings.Join(got, "|") != strings.Join(want, "|") {
		t.Fatalf("keys = %#v, want %#v", got, want)
	}
}

func TestPrimaryChatRejectsUnsafeEndpointURL(t *testing.T) {
	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_KEY", "sk-primary-test")
	t.Setenv("CHAT_PRIMARY_BASE_URL", "http://evil.example")
	if primaryChatConfigured() {
		t.Fatalf("primary chat should reject non-local http endpoint")
	}

	t.Setenv("CHAT_PRIMARY_BASE_URL", "https://user:pass@example.com")
	if primaryChatConfigured() {
		t.Fatalf("primary chat should reject endpoint with userinfo")
	}

	t.Setenv("CHAT_PRIMARY_BASE_URL", "http://127.0.0.1:8080")
	if !primaryChatConfigured() {
		t.Fatalf("primary chat should allow local http endpoint for tests")
	}
}

func TestPrimaryChatProviderLabelFallsBackForUnsafeValues(t *testing.T) {
	t.Setenv("CHAT_PRIMARY_PROVIDER_LABEL", "中转联盟")
	if got := primaryChatProviderLabel(); got != "中转联盟" {
		t.Fatalf("safe provider label = %q", got)
	}

	t.Setenv("CHAT_PRIMARY_PROVIDER_LABEL", "https://gateway.example/v1")
	if got := primaryChatProviderLabel(); got != "中转站" {
		t.Fatalf("url provider label should fall back, got %q", got)
	}

	t.Setenv("CHAT_PRIMARY_PROVIDER_LABEL", "sk-secret-looking-label")
	if got := primaryChatProviderLabel(); got != "中转站" {
		t.Fatalf("secret-looking provider label should fall back, got %q", got)
	}
}

func TestPrimaryChatForceSearchDisabledWhenAnyMessageHasImage(t *testing.T) {
	t.Setenv("CHAT_PRIMARY_FORCE_SEARCH", "true")

	messages := []BailianMessage{
		{Role: "user", Content: "上一轮文字"},
		{Role: "assistant", Content: "上一轮回答"},
		{
			Role: "user",
			Content: []any{
				map[string]any{"type": "text", "text": "历史图片问题"},
				map[string]any{"type": "image_url", "image_url": map[string]any{"url": "https://example.com/historical.jpg"}},
			},
		},
		{Role: "user", Content: "本轮追问"},
	}

	if got := primaryChatForceSearch(true, messages); got {
		t.Fatalf("primaryChatForceSearch with image in message history = true, want false")
	}
}

func TestPrimaryChatClientDefaultsToSixSecondOpenTimeouts(t *testing.T) {
	transport, ok := NewPrimaryChatClientFromEnv().httpClient.Transport.(*http.Transport)
	if !ok {
		t.Fatalf("transport type = %T, want *http.Transport", NewPrimaryChatClientFromEnv().httpClient.Transport)
	}
	if got := transport.TLSHandshakeTimeout; got != 6*time.Second {
		t.Fatalf("TLSHandshakeTimeout = %v, want 6s", got)
	}
	if got := transport.ResponseHeaderTimeout; got != 6*time.Second {
		t.Fatalf("ResponseHeaderTimeout = %v, want 6s", got)
	}
}

func TestOpenValidatedChatStreamPrefersPrimaryWhenHealthy(t *testing.T) {
	primaryHits := 0
	primaryServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		primaryHits++
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer primaryServer.Close()
	bailianHits := 0
	bailianServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		bailianHits++
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer bailianServer.Close()

	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_MODE", "chat")
	t.Setenv("CHAT_PRIMARY_BASE_URL", primaryServer.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "sk-primary-test")
	t.Setenv("DASHSCOPE_API_KEY", "dashscope-key")
	t.Setenv("BAILIAN_BASE_URL", bailianServer.URL)

	server := &Server{
		logger:      slog.New(slog.NewTextHandler(io.Discard, nil)),
		primaryChat: NewPrimaryChatClientFromEnv(),
		bailian:     NewBailianClient(),
	}
	response, provider, err := server.openValidatedChatStreamWithFallback(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "hello"}},
		BailianStreamOptions{},
	)
	if err != nil {
		t.Fatalf("open chat stream: %v", err)
	}
	defer response.Body.Close()
	if provider != "primary" {
		t.Fatalf("provider = %q, want primary", provider)
	}
	if primaryHits != 1 || bailianHits != 0 {
		t.Fatalf("hits primary=%d bailian=%d, want 1/0", primaryHits, bailianHits)
	}
}

func TestOpenValidatedChatStreamFallsBackToBailianWhenPrimaryFailsBeforeStream(t *testing.T) {
	primaryServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		_, _ = w.Write([]byte(`{"error":"bad gateway"}`))
	}))
	defer primaryServer.Close()
	bailianHits := 0
	bailianServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		bailianHits++
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer bailianServer.Close()

	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_MODE", "chat")
	t.Setenv("CHAT_PRIMARY_BASE_URL", primaryServer.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "sk-primary-test")
	t.Setenv("DASHSCOPE_API_KEY", "dashscope-key")
	t.Setenv("BAILIAN_BASE_URL", bailianServer.URL)

	server := &Server{
		logger:      slog.New(slog.NewTextHandler(io.Discard, nil)),
		primaryChat: NewPrimaryChatClientFromEnv(),
		bailian:     NewBailianClient(),
	}
	response, provider, err := server.openValidatedChatStreamWithFallback(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "hello"}},
		BailianStreamOptions{},
	)
	if err != nil {
		t.Fatalf("open chat stream should fall back: %v", err)
	}
	defer response.Body.Close()
	if provider != "bailian" {
		t.Fatalf("provider = %q, want bailian", provider)
	}
	if bailianHits != 1 {
		t.Fatalf("bailian hits=%d, want 1", bailianHits)
	}
}

func TestOpenValidatedChatStreamSkipsPrimaryForForcedSearch(t *testing.T) {
	primaryHits := 0
	primaryServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		primaryHits++
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer primaryServer.Close()
	var bailianCaptured map[string]any
	bailianServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if err := json.NewDecoder(r.Body).Decode(&bailianCaptured); err != nil {
			t.Fatalf("decode bailian request: %v", err)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer bailianServer.Close()

	t.Setenv("CHAT_PRIMARY_ENABLED", "true")
	t.Setenv("CHAT_PRIMARY_API_MODE", "chat")
	t.Setenv("CHAT_PRIMARY_BASE_URL", primaryServer.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "sk-primary-test")
	t.Setenv("DASHSCOPE_API_KEY", "dashscope-key")
	t.Setenv("BAILIAN_BASE_URL", bailianServer.URL)

	server := &Server{
		logger:      slog.New(slog.NewTextHandler(io.Discard, nil)),
		primaryChat: NewPrimaryChatClientFromEnv(),
		bailian:     NewBailianClient(),
	}
	response, provider, err := server.openValidatedChatStreamWithFallback(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "查一下今天小麦价格"}},
		BailianStreamOptions{ForceSearch: true},
	)
	if err != nil {
		t.Fatalf("open chat stream: %v", err)
	}
	defer response.Body.Close()
	if provider != "bailian" {
		t.Fatalf("provider = %q, want bailian", provider)
	}
	if primaryHits != 0 {
		t.Fatalf("primary hits=%d, want 0", primaryHits)
	}
	searchOptions, ok := bailianCaptured["search_options"].(map[string]any)
	if !ok {
		t.Fatalf("missing search_options: %#v", bailianCaptured["search_options"])
	}
	if got := searchOptions["forced_search"]; got != true {
		t.Fatalf("bailian forced_search=%#v, want true", got)
	}
}

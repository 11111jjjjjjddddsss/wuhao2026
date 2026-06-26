package app

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestPrimaryChatClientUsesConfiguredModelSearchAndNoThinking(t *testing.T) {
	var captured map[string]any
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/chat/completions" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer primary-key" {
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
	t.Setenv("CHAT_PRIMARY_BASE_URL", server.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "primary-key")
	t.Setenv("CHAT_PRIMARY_MODEL", "gpt-5.5")
	t.Setenv("CHAT_PRIMARY_FORCE_SEARCH", "true")

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
	if got, ok := captured["temperature"].(float64); !ok || got != unifiedModelTemperature {
		t.Fatalf("temperature mismatch: %#v", captured["temperature"])
	}
	if _, ok := captured["top_p"]; ok {
		t.Fatalf("primary chat should leave top_p to provider default: %#v", captured["top_p"])
	}
	if got := captured["enable_thinking"]; got != false {
		t.Fatalf("enable_thinking mismatch: %#v", got)
	}
	if _, ok := captured["thinking_budget"]; ok {
		t.Fatalf("thinking_budget should be omitted when primary thinking is disabled: %#v", captured["thinking_budget"])
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
	t.Setenv("CHAT_PRIMARY_BASE_URL", server.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "primary-key")
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
	t.Setenv("CHAT_PRIMARY_BASE_URL", primaryServer.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "primary-key")
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
	t.Setenv("CHAT_PRIMARY_BASE_URL", primaryServer.URL)
	t.Setenv("CHAT_PRIMARY_API_KEY", "primary-key")
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

package app

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

func TestGPTRelayOpenStreamUsesMinimalResponsesPayload(t *testing.T) {
	var captured map[string]any
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/v1/responses" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer sk-test-1" {
			t.Fatalf("unexpected Authorization header: %q", got)
		}
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {\"type\":\"response.completed\"}\n\n"))
	}))
	defer modelServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_BASE_URL", modelServer.URL)
	t.Setenv("GPT_RELAY_API_KEYS", "label sk-test-1")

	response, err := NewGPTRelayClientFromEnv().OpenStream(
		context.Background(),
		[]BailianMessage{
			{Role: "system", Content: "anchor"},
			{Role: "user", Content: []map[string]any{
				{"type": "text", "text": "看看这张图"},
				{"type": "image_url", "image_url": map[string]any{"url": "https://example.com/image.jpg"}},
			}},
		},
	)
	if err != nil {
		t.Fatalf("open stream: %v", err)
	}
	defer response.Body.Close()

	if got := captured["model"]; got != defaultGPTRelayModel {
		t.Fatalf("model mismatch: %#v", got)
	}
	if got := captured["stream"]; got != true {
		t.Fatalf("stream mismatch: %#v", got)
	}
	if _, ok := captured["temperature"]; ok {
		t.Fatalf("gpt relay should not set temperature: %#v", captured["temperature"])
	}
	if _, ok := captured["top_p"]; ok {
		t.Fatalf("gpt relay should not set top_p: %#v", captured["top_p"])
	}
	if _, ok := captured["max_tokens"]; ok {
		t.Fatalf("gpt relay should not set max_tokens: %#v", captured["max_tokens"])
	}
	if _, ok := captured["max_output_tokens"]; ok {
		t.Fatalf("gpt relay should not set max_output_tokens: %#v", captured["max_output_tokens"])
	}
	allowedKeys := map[string]bool{
		"model":        true,
		"stream":       true,
		"instructions": true,
		"input":        true,
		"tools":        true,
		"tool_choice":  true,
		"reasoning":    true,
	}
	for key := range captured {
		if !allowedKeys[key] {
			t.Fatalf("unexpected gpt relay request field %q in %#v", key, captured)
		}
	}
	reasoning, ok := captured["reasoning"].(map[string]any)
	if !ok || reasoning["effort"] != "medium" {
		t.Fatalf("reasoning mismatch: %#v", captured["reasoning"])
	}
	assertOnlyMapKeys(t, reasoning, "effort")
	tools, ok := captured["tools"].([]any)
	if !ok || len(tools) != 1 {
		t.Fatalf("tools mismatch: %#v", captured["tools"])
	}
	tool, _ := tools[0].(map[string]any)
	if tool["type"] != "web_search" || tool["search_context_size"] != "low" {
		t.Fatalf("web_search tool mismatch: %#v", tool)
	}
	assertOnlyMapKeys(t, tool, "type", "search_context_size")
	if got := captured["tool_choice"]; got != "auto" {
		t.Fatalf("tool_choice mismatch: %#v", got)
	}
	instructions := asString(captured["instructions"])
	if !strings.Contains(instructions, "必须只搜索一次") || !strings.Contains(instructions, "拿到够用信息后立刻快速回答") {
		t.Fatalf("instructions missing one-shot search rule: %q", instructions)
	}
	input, ok := captured["input"].([]any)
	if !ok || len(input) != 1 {
		t.Fatalf("input mismatch: %#v", captured["input"])
	}
	content, ok := input[0].(map[string]any)["content"].([]any)
	if !ok || len(content) != 2 {
		t.Fatalf("content mismatch: %#v", input[0])
	}
	imagePart, _ := content[1].(map[string]any)
	if imagePart["type"] != "input_image" || imagePart["image_url"] != "https://example.com/image.jpg" {
		t.Fatalf("image part mismatch: %#v", imagePart)
	}
	assertOnlyMapKeys(t, imagePart, "type", "image_url")
	if _, ok := imagePart["detail"]; ok {
		t.Fatalf("gpt relay should not set image detail: %#v", imagePart)
	}
}

func assertOnlyMapKeys(t *testing.T, values map[string]any, allowed ...string) {
	t.Helper()
	allowedSet := map[string]struct{}{}
	for _, key := range allowed {
		allowedSet[key] = struct{}{}
	}
	for key := range values {
		if _, ok := allowedSet[key]; !ok {
			t.Fatalf("unexpected key %q in %#v", key, values)
		}
	}
}

func TestGPTRelayReasoningAllowsMediumOrHighAndSearchStaysLow(t *testing.T) {
	t.Setenv("GPT_RELAY_REASONING_EFFORT", "high")
	t.Setenv("GPT_RELAY_SEARCH_CONTEXT_SIZE", "high")

	if got := gptRelayReasoningEffort(); got != "high" {
		t.Fatalf("reasoning effort = %q, want high", got)
	}
	if got := gptRelaySearchContextSize(); got != "low" {
		t.Fatalf("search context size = %q, want low", got)
	}

	t.Setenv("GPT_RELAY_REASONING_EFFORT", "xhigh")
	if got := gptRelayReasoningEffort(); got != "medium" {
		t.Fatalf("unsupported reasoning effort = %q, want medium", got)
	}
}

func TestOpenValidatedChatStreamFallsBackToBailianWhenGPTRelayOpenFails(t *testing.T) {
	gptHits := int32(0)
	gptServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&gptHits, 1)
		http.Error(w, "bad relay", http.StatusBadGateway)
	}))
	defer gptServer.Close()
	bailianServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/chat/completions" {
			t.Fatalf("unexpected bailian path: %s", r.URL.Path)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer bailianServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_BASE_URL", gptServer.URL)
	t.Setenv("GPT_RELAY_API_KEY", "sk-test-relay")
	t.Setenv("BAILIAN_BASE_URL", bailianServer.URL)
	t.Setenv("DASHSCOPE_API_KEY", "test-bailian")

	server := &Server{
		logger:   slog.New(slog.NewTextHandler(io.Discard, nil)),
		bailian:  NewBailianClient(),
		gptRelay: NewGPTRelayClientFromEnv(),
	}
	response, provider, cancelProvider, err := server.openValidatedChatStreamWithFallback(
		context.Background(),
		time.Now(),
		[]BailianMessage{{Role: "user", Content: "bailian"}},
		[]BailianMessage{{Role: "user", Content: "gpt"}},
		BailianStreamOptions{},
	)
	if err != nil {
		t.Fatalf("open stream: %v", err)
	}
	defer response.Body.Close()
	if provider != "bailian" {
		t.Fatalf("provider = %q, want bailian", provider)
	}
	cancelProvider()
	if got := atomic.LoadInt32(&gptHits); got != 1 {
		t.Fatalf("gpt relay should not have an outer retry, hits=%d", got)
	}
}

func TestOpenValidatedChatStreamDoesNotCallGPTRelayWhenDisabled(t *testing.T) {
	gptHits := int32(0)
	gptServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&gptHits, 1)
		http.Error(w, "unexpected relay hit", http.StatusInternalServerError)
	}))
	defer gptServer.Close()
	bailianServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer bailianServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "")
	t.Setenv("GPT_RELAY_BASE_URL", gptServer.URL)
	t.Setenv("GPT_RELAY_API_KEY", "sk-test-relay")
	t.Setenv("BAILIAN_BASE_URL", bailianServer.URL)
	t.Setenv("DASHSCOPE_API_KEY", "test-bailian")

	server := &Server{
		logger:   slog.New(slog.NewTextHandler(io.Discard, nil)),
		bailian:  NewBailianClient(),
		gptRelay: NewGPTRelayClientFromEnv(),
	}
	response, provider, cancelProvider, err := server.openValidatedChatStreamWithFallback(
		context.Background(),
		time.Now(),
		[]BailianMessage{{Role: "user", Content: "bailian"}},
		[]BailianMessage{{Role: "user", Content: "gpt"}},
		BailianStreamOptions{},
	)
	if err != nil {
		t.Fatalf("open stream: %v", err)
	}
	defer response.Body.Close()
	if provider != "bailian" {
		t.Fatalf("provider = %q, want bailian", provider)
	}
	cancelProvider()
	if got := atomic.LoadInt32(&gptHits); got != 0 {
		t.Fatalf("disabled gpt relay should not be called, hits=%d", got)
	}
}

func TestGPTRelayFirstVisibleTimeoutDefaultAndClamp(t *testing.T) {
	t.Setenv("CHAT_STREAM_MAX_DURATION_SECONDS", "30")
	t.Setenv("GPT_RELAY_FIRST_VISIBLE_TIMEOUT_SECONDS", "")
	if got := resolveChatStreamFirstVisibleTimeoutForProvider(gptRelayProvider); got != defaultGPTRelayFirstVisibleTimeout {
		t.Fatalf("default gpt relay first visible timeout = %s, want %s", got, defaultGPTRelayFirstVisibleTimeout)
	}

	t.Setenv("CHAT_STREAM_MAX_DURATION_SECONDS", "5")
	t.Setenv("GPT_RELAY_FIRST_VISIBLE_TIMEOUT_SECONDS", "10")
	if got := resolveChatStreamFirstVisibleTimeoutForProvider(gptRelayProvider); got != 5*time.Second {
		t.Fatalf("gpt relay first visible timeout should clamp to max duration, got %s", got)
	}
}

func TestGPTRelayFirstVisibleTimeoutCountsFromRequestReceived(t *testing.T) {
	t.Setenv("CHAT_STREAM_MAX_DURATION_SECONDS", "30")
	t.Setenv("GPT_RELAY_FIRST_VISIBLE_TIMEOUT_SECONDS", "15")

	remaining := resolveChatStreamFirstVisibleTimeoutForProviderAfter(gptRelayProvider, 13*time.Second)
	if remaining < 1900*time.Millisecond || remaining > 2100*time.Millisecond {
		t.Fatalf("remaining first visible timeout = %s, want about 2s", remaining)
	}

	if got := resolveChatStreamFirstVisibleTimeoutForProviderAfter(gptRelayProvider, 16*time.Second); got != time.Millisecond {
		t.Fatalf("exhausted first visible timeout = %s, want 1ms", got)
	}
}

func TestGPTRelayHealthStatusDisabledAndMissingConfig(t *testing.T) {
	t.Setenv("GPT_RELAY_ENABLED", "")
	if got := gptRelayHealthStatus(NewGPTRelayClientFromEnv()); got != "disabled" {
		t.Fatalf("disabled health = %q, want disabled", got)
	}

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_BASE_URL", "")
	t.Setenv("GPT_RELAY_API_KEY", "")
	if got := gptRelayHealthStatus(NewGPTRelayClientFromEnv()); got != "missing_config" {
		t.Fatalf("missing config health = %q, want missing_config", got)
	}
}

func TestGPTRelayNetworkingInstructionIncludesExplicitSearchRequest(t *testing.T) {
	instruction := gptRelayNetworkingInstruction()
	for _, want := range []string{
		"用户明确要求查一下、搜一下、联网查、看最新信息时，要联网。",
		"如需联网，必须只搜索一次。",
		"拿到够用信息后立刻快速回答。",
	} {
		if !strings.Contains(instruction, want) {
			t.Fatalf("networking instruction missing %q in:\n%s", want, instruction)
		}
	}
}

func TestGPTRelayPromptVariantOmitsChatOutputConstraint(t *testing.T) {
	server := &Server{systemAnchor: "anchor"}
	snapshot := &SessionSnapshot{
		MemoryDocument: "memory",
		ARoundsFull: []SessionRound{
			{User: "old", Assistant: "old answer"},
		},
	}
	messages, usedCount, hasMemory := server.buildPromptMessagesWithOptions(
		snapshot,
		6,
		"current",
		nil,
		"context",
		"",
		false,
	)
	if usedCount != 1 || !hasMemory {
		t.Fatalf("prompt metadata mismatch used=%d hasMemory=%v", usedCount, hasMemory)
	}
	for _, message := range messages {
		if message.Role == "system" && message.Content == chatOutputConstraint {
			t.Fatalf("gpt relay prompt must not include chat output constraint")
		}
	}
	last := messages[len(messages)-1]
	if last.Role != "user" || last.Content != "current" {
		t.Fatalf("expected current user message at tail, got %#v", last)
	}
}

func TestGPTRelayResponsesConversion(t *testing.T) {
	var assistant strings.Builder
	var hasCitations atomic.Bool
	var hasSources atomic.Bool
	var usage bailianModelUsage
	searchCount := 0

	clientData, shouldForward, done, failed := convertGPTRelayResponsesStreamDataForClient(
		"response.output_text.delta",
		`{"type":"response.output_text.delta","delta":"能行。"}`,
		&assistant,
		&hasCitations,
		&hasSources,
		&usage,
		&searchCount,
	)
	if !shouldForward || done || failed {
		t.Fatalf("delta conversion flags mismatch forward=%v done=%v failed=%v", shouldForward, done, failed)
	}
	if !strings.Contains(clientData, "能行。") || assistant.String() != "能行。" {
		t.Fatalf("delta conversion mismatch data=%q assistant=%q", clientData, assistant.String())
	}

	clientData, shouldForward, done, failed = convertGPTRelayResponsesStreamDataForClient(
		"response.output_text.delta",
		`{"type":"response.output_text.delta","delta":"\n\n"}`,
		&assistant,
		&hasCitations,
		&hasSources,
		&usage,
		&searchCount,
	)
	if !shouldForward || done || failed {
		t.Fatalf("whitespace delta after visible text should forward, forward=%v done=%v failed=%v", shouldForward, done, failed)
	}
	if assistant.String() != "能行。\n\n" || !strings.Contains(clientData, `\n\n`) {
		t.Fatalf("whitespace delta conversion mismatch data=%q assistant=%q", clientData, assistant.String())
	}

	clientData, shouldForward, done, failed = convertGPTRelayResponsesStreamDataForClient(
		"response.output_text.done",
		`{"type":"response.output_text.done","text":"能行。\n\n再查叶背。"}`,
		&assistant,
		&hasCitations,
		&hasSources,
		&usage,
		&searchCount,
	)
	if !shouldForward || done || failed {
		t.Fatalf("output_text.done suffix should forward, forward=%v done=%v failed=%v", shouldForward, done, failed)
	}
	if assistant.String() != "能行。\n\n再查叶背。" || !strings.Contains(clientData, "再查叶背。") {
		t.Fatalf("output_text.done conversion mismatch data=%q assistant=%q", clientData, assistant.String())
	}

	_, _, _, _ = convertGPTRelayResponsesStreamDataForClient(
		"response.web_search_call.completed",
		`{"type":"response.web_search_call.completed"}`,
		&assistant,
		&hasCitations,
		&hasSources,
		&usage,
		&searchCount,
	)
	if searchCount != 1 || !hasSources.Load() {
		t.Fatalf("search event should count once, count=%d sources=%v", searchCount, hasSources.Load())
	}

	clientData, shouldForward, done, failed = convertGPTRelayResponsesStreamDataForClient(
		"response.completed",
		`{"type":"response.completed","response":{"usage":{"input_tokens":11,"output_tokens":7,"total_tokens":18,"output_tokens_details":{"reasoning_tokens":3}}}}`,
		&assistant,
		&hasCitations,
		&hasSources,
		&usage,
		&searchCount,
	)
	if !shouldForward || !done || failed {
		t.Fatalf("completed conversion flags mismatch forward=%v done=%v failed=%v", shouldForward, done, failed)
	}
	if !strings.Contains(clientData, `"finish_reason":"stop"`) {
		t.Fatalf("finish payload mismatch: %q", clientData)
	}
	if usage.InputTokens != 11 || usage.OutputTokens != 7 || usage.TotalTokens != 18 || usage.ReasoningTokens != 3 || usage.searchCount() != 1 {
		t.Fatalf("usage mismatch: %#v", usage)
	}

	clientData, shouldForward, done, failed = convertGPTRelayResponsesStreamDataForClient(
		"response.failed",
		`{"type":"response.failed"}`,
		&assistant,
		&hasCitations,
		&hasSources,
		&usage,
		&searchCount,
	)
	if clientData != "" || shouldForward || done || !failed {
		t.Fatalf("failed conversion flags mismatch data=%q forward=%v done=%v failed=%v", clientData, shouldForward, done, failed)
	}
}

func TestGPTRelaySafeEndpointURL(t *testing.T) {
	allowed := []string{
		"https://example.com/v1/responses",
		"http://localhost:8080/v1/responses",
		"http://127.0.0.1:8080/v1/responses",
	}
	for _, raw := range allowed {
		if got := gptRelaySafeEndpointURL(raw); got == "" {
			t.Fatalf("expected endpoint allowed: %s", raw)
		}
	}
	blocked := []string{
		"http://example.com/v1/responses",
		"https://user@example.com/v1/responses",
		"https://example.com/v1/responses?key=x",
		"https://example.com/v1/responses#frag",
	}
	for _, raw := range blocked {
		if got := gptRelaySafeEndpointURL(raw); got != "" {
			t.Fatalf("expected endpoint blocked: %s -> %s", raw, got)
		}
	}
}

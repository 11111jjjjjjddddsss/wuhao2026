package app

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
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
	clearGPTRelayKeyEnvForTest(t)
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

func TestGPTRelayKeyEntriesUseNonSecretSlotLabels(t *testing.T) {
	clearGPTRelayKeyEnvForTest(t)
	t.Setenv("GPT_RELAY_API_KEY_1", "sk-slot-one")
	t.Setenv("GPT_RELAY_API_KEY_2", "sk-slot-two")
	t.Setenv("GPT_RELAY_API_KEY", "sk-single")
	t.Setenv("GPT_RELAY_API_KEYS", "name sk-list-one,sk-list-two")

	entries := gptRelayKeyEntries()
	if len(entries) != 5 {
		t.Fatalf("entry count = %d, want 5: %#v", len(entries), entries)
	}
	for _, entry := range entries {
		if entry.Label == "" {
			t.Fatalf("entry label should be non-empty: %#v", entry)
		}
		if strings.Contains(entry.Label, "sk-") {
			t.Fatalf("entry label leaked key material: %#v", entry)
		}
	}
	wantLabels := []string{
		"GPT_RELAY_API_KEY_1",
		"GPT_RELAY_API_KEY_2",
		"GPT_RELAY_API_KEY",
		"GPT_RELAY_API_KEYS_1",
		"GPT_RELAY_API_KEYS_2",
	}
	for i, want := range wantLabels {
		if entries[i].Label != want {
			t.Fatalf("label[%d]=%q, want %q", i, entries[i].Label, want)
		}
	}
}

func clearGPTRelayKeyEnvForTest(t *testing.T) {
	t.Helper()
	clearPrefix := func(prefix string) {
		t.Setenv(prefix+"_BASE_URL", "")
		t.Setenv(prefix+"_RESPONSES_URL", "")
		t.Setenv(prefix+"_LABEL", "")
		for i := 1; i <= defaultGPTRelayMaxConfiguredKeySlot; i++ {
			t.Setenv(fmt.Sprintf("%s_API_KEY_%d", prefix, i), "")
		}
		t.Setenv(prefix+"_API_KEY", "")
		t.Setenv(prefix+"_API_KEYS", "")
	}
	clearPrefix("GPT_RELAY")
	for i := 1; i <= defaultGPTRelayMaxConfiguredProviderSlot; i++ {
		clearPrefix(fmt.Sprintf("GPT_RELAY_PROVIDER_%d", i))
	}
}

func TestGPTRelayRequestEntriesInterleaveProviderSpecificKeys(t *testing.T) {
	clearGPTRelayKeyEnvForTest(t)
	t.Setenv("GPT_RELAY_BASE_URL", "https://legacy.example")
	t.Setenv("GPT_RELAY_API_KEY_1", "sk-legacy")
	t.Setenv("GPT_RELAY_PROVIDER_1_BASE_URL", "https://provider-one.example")
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_1", "sk-p1-1")
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_2", "sk-p1-2")
	t.Setenv("GPT_RELAY_PROVIDER_2_BASE_URL", "https://provider-two.example/v1")
	t.Setenv("GPT_RELAY_PROVIDER_2_API_KEYS", "name sk-p2-1,sk-p2-2")

	entries := gptRelayRequestEntries()
	if len(entries) != 4 {
		t.Fatalf("entry count = %d, want 4: %#v", len(entries), entries)
	}
	want := []struct {
		label    string
		endpoint string
	}{
		{"GPT_RELAY_PROVIDER_1_API_KEY_1", "https://provider-one.example/v1/responses"},
		{"GPT_RELAY_PROVIDER_2_API_KEYS_1", "https://provider-two.example/v1/responses"},
		{"GPT_RELAY_PROVIDER_1_API_KEY_2", "https://provider-one.example/v1/responses"},
		{"GPT_RELAY_PROVIDER_2_API_KEYS_2", "https://provider-two.example/v1/responses"},
	}
	for i, expected := range want {
		if entries[i].Label != expected.label || entries[i].Endpoint != expected.endpoint {
			t.Fatalf("entry[%d]=%#v, want label=%q endpoint=%q", i, entries[i], expected.label, expected.endpoint)
		}
		if strings.Contains(entries[i].Label, "sk-") || strings.Contains(entries[i].Endpoint, "sk-") {
			t.Fatalf("entry leaked key material: %#v", entries[i])
		}
	}
}

func TestGPTRelayProviderSpecificLabelsAreDecorated(t *testing.T) {
	clearGPTRelayKeyEnvForTest(t)
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {\"type\":\"response.completed\"}\n\n"))
	}))
	defer modelServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_PROVIDER_1_BASE_URL", modelServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_1_LABEL", "Relay One")
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_1", "sk-provider-one")
	t.Setenv("GPT_RELAY_PROVIDER_2_BASE_URL", modelServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_2_LABEL", "Relay Two")
	t.Setenv("GPT_RELAY_PROVIDER_2_API_KEY_1", "sk-provider-two")

	entries := gptRelayRequestEntries()
	if len(entries) != 2 {
		t.Fatalf("entry count=%d, want 2", len(entries))
	}
	if entries[0].ProviderLabel != "Relay One" || entries[1].ProviderLabel != "Relay Two" {
		t.Fatalf("provider labels=%q/%q, want Relay One/Relay Two", entries[0].ProviderLabel, entries[1].ProviderLabel)
	}
	client := NewGPTRelayClientFromEnv()
	response, err := client.openStreamWithCursor(context.Background(), []BailianMessage{{Role: "user", Content: "test"}}, client.newRequestCursor())
	if err != nil {
		t.Fatalf("open stream: %v", err)
	}
	defer response.Body.Close()
	if got := response.Header.Get(gptRelayHeaderProviderLabel); got != "Relay One" {
		t.Fatalf("decorated provider label=%q, want Relay One", got)
	}
}

func TestGPTRelayOpenStreamDoesNotRetryAcrossProviderSpecificEndpoints(t *testing.T) {
	clearGPTRelayKeyEnvForTest(t)
	firstHits := int32(0)
	firstServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&firstHits, 1)
		http.Error(w, "bad relay", http.StatusBadGateway)
	}))
	defer firstServer.Close()

	secondHits := int32(0)
	secondServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&secondHits, 1)
		if r.URL.Path != "/v1/responses" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if got := r.Header.Get("Authorization"); got != "Bearer sk-provider-two" {
			t.Fatalf("unexpected Authorization header: %q", got)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {\"type\":\"response.completed\"}\n\n"))
	}))
	defer secondServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_PROVIDER_1_BASE_URL", firstServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_1", "sk-provider-one")
	t.Setenv("GPT_RELAY_PROVIDER_2_BASE_URL", secondServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_2_API_KEY_1", "sk-provider-two")

	response, err := NewGPTRelayClientFromEnv().OpenStream(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "test"}},
	)
	if err != nil {
		t.Fatalf("open stream: %v", err)
	}
	_ = response.Body.Close()
	if response.StatusCode != http.StatusBadGateway {
		t.Fatalf("status=%d, want %d", response.StatusCode, http.StatusBadGateway)
	}
	if got := atomic.LoadInt32(&firstHits); got != 1 {
		t.Fatalf("first provider hits=%d, want 1", got)
	}
	if got := atomic.LoadInt32(&secondHits); got != 0 {
		t.Fatalf("second provider should not be hit within the same request, hits=%d", got)
	}
}

func TestGPTRelayProviderRoundRobinIgnoresRetiredCooldownEnv(t *testing.T) {
	clearGPTRelayKeyEnvForTest(t)
	firstHits := int32(0)
	firstServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&firstHits, 1)
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {\"type\":\"response.completed\"}\n\n"))
	}))
	defer firstServer.Close()

	secondHits := int32(0)
	secondServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&secondHits, 1)
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {\"type\":\"response.completed\"}\n\n"))
	}))
	defer secondServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_KEY_COOLDOWN_SECONDS", "60")
	t.Setenv("GPT_RELAY_PROVIDER_1_BASE_URL", firstServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_1", "sk-provider-one")
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_2", "sk-provider-one-backup")
	t.Setenv("GPT_RELAY_PROVIDER_2_BASE_URL", secondServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_2_API_KEY_1", "sk-provider-two")

	client := NewGPTRelayClientFromEnv()
	firstResponse, err := client.OpenStream(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "test"}},
	)
	if err != nil {
		t.Fatalf("first open stream: %v", err)
	}
	if got := firstResponse.Header.Get(gptRelayHeaderProviderSlot); got != "provider_1" {
		t.Fatalf("first provider slot=%q, want provider_1", got)
	}
	_ = firstResponse.Body.Close()

	secondResponse, err := client.OpenStream(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "test"}},
	)
	if err != nil {
		t.Fatalf("second open stream: %v", err)
	}
	if got := secondResponse.Header.Get(gptRelayHeaderProviderSlot); got != "provider_2" {
		t.Fatalf("second provider slot=%q, want provider_2", got)
	}
	_ = secondResponse.Body.Close()

	thirdResponse, err := client.OpenStream(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "test"}},
	)
	if err != nil {
		t.Fatalf("third open stream: %v", err)
	}
	if got := thirdResponse.Header.Get(gptRelayHeaderProviderSlot); got != "provider_1" {
		t.Fatalf("third provider slot=%q, want provider_1", got)
	}
	if got := thirdResponse.Header.Get(gptRelayHeaderKeySlot); got != "GPT_RELAY_PROVIDER_1_API_KEY_2" {
		t.Fatalf("third key slot=%q, want GPT_RELAY_PROVIDER_1_API_KEY_2", got)
	}
	_ = thirdResponse.Body.Close()

	if got := atomic.LoadInt32(&firstHits); got != 2 {
		t.Fatalf("first provider hits=%d, want 2", got)
	}
	if got := atomic.LoadInt32(&secondHits); got != 1 {
		t.Fatalf("second provider hits=%d, want 1", got)
	}
}

func TestGPTRelayRequestCursorPinsOneProviderPerRequest(t *testing.T) {
	clearGPTRelayKeyEnvForTest(t)
	firstServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {\"type\":\"response.completed\"}\n\n"))
	}))
	defer firstServer.Close()

	secondServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("event: response.completed\n"))
		_, _ = w.Write([]byte("data: {\"type\":\"response.completed\"}\n\n"))
	}))
	defer secondServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_PROVIDER_1_BASE_URL", firstServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_1", "sk-provider-one")
	t.Setenv("GPT_RELAY_PROVIDER_2_BASE_URL", secondServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_2_API_KEY_1", "sk-provider-two")

	client := NewGPTRelayClientFromEnv()
	firstRequestCursor := client.newRequestCursor()
	firstResponse, err := client.openStreamWithCursor(context.Background(), []BailianMessage{{Role: "user", Content: "test"}}, firstRequestCursor)
	if err != nil {
		t.Fatalf("first request first relay: %v", err)
	}
	if got := firstResponse.Header.Get(gptRelayHeaderProviderSlot); got != "provider_1" {
		t.Fatalf("first request first provider slot=%q, want provider_1", got)
	}
	_ = firstResponse.Body.Close()

	firstRetryResponse, err := client.openStreamWithCursor(context.Background(), []BailianMessage{{Role: "user", Content: "test"}}, firstRequestCursor)
	if err != nil {
		t.Fatalf("first request retry relay: %v", err)
	}
	if got := firstRetryResponse.Header.Get(gptRelayHeaderProviderSlot); got != "provider_1" {
		t.Fatalf("first request retry provider slot=%q, want provider_1", got)
	}
	_ = firstRetryResponse.Body.Close()

	secondRequestCursor := client.newRequestCursor()
	secondResponse, err := client.openStreamWithCursor(context.Background(), []BailianMessage{{Role: "user", Content: "test"}}, secondRequestCursor)
	if err != nil {
		t.Fatalf("second request first relay: %v", err)
	}
	if got := secondResponse.Header.Get(gptRelayHeaderProviderSlot); got != "provider_2" {
		t.Fatalf("second request first provider slot=%q, want provider_2", got)
	}
	_ = secondResponse.Body.Close()
}

func TestGPTRelayAttemptLogDoesNotLeakAPIKey(t *testing.T) {
	var logs bytes.Buffer
	client := NewGPTRelayClientFromEnv()
	client.SetLogger(slog.New(slog.NewJSONHandler(&logs, nil)))

	client.logKeyAttempt("gpt relay key attempt failed",
		"attempt", 1,
		"key_slot", "GPT_RELAY_API_KEY_1",
		"error_kind", "response_header_timeout",
	)

	text := logs.String()
	if !strings.Contains(text, "GPT_RELAY_API_KEY_1") {
		t.Fatalf("log should include key slot label: %s", text)
	}
	if strings.Contains(text, "sk-") {
		t.Fatalf("log leaked key material: %s", text)
	}
}

func TestClassifyGPTRelayAttemptError(t *testing.T) {
	cases := map[string]string{
		"net/http: timeout awaiting response headers": "response_header_timeout",
		"TLS handshake timeout":                       "tls_handshake_timeout",
		"dial tcp: i/o timeout":                       "io_timeout",
		"context deadline exceeded":                   "context_deadline_exceeded",
		"context canceled":                            "context_canceled",
		"read: connection reset by peer":              "connection_reset",
		"EOF":                                         "eof",
	}
	for input, want := range cases {
		err := errString(input)
		if got := classifyGPTRelayAttemptError(err); got != want {
			t.Fatalf("classify(%q)=%q, want %q", input, got, want)
		}
	}
}

type errString string

func (e errString) Error() string {
	return string(e)
}

func TestNormalizeGPTRelayAPIKeyAcceptsProviderTokenFormats(t *testing.T) {
	cases := map[string]string{
		"label sk-test-1":        "sk-test-1",
		"label relay-token-1":    "relay-token-1",
		"api_key=relay-token-2":  "relay-token-2",
		"provider:relay-token-3": "relay-token-3",
		"relay-token-4":          "relay-token-4",
		"  relay-token-5  ":      "relay-token-5",
		"":                       "",
		"   ":                    "",
	}
	for raw, want := range cases {
		if got := normalizeGPTRelayAPIKey(raw); got != want {
			t.Fatalf("normalizeGPTRelayAPIKey(%q) = %q, want %q", raw, got, want)
		}
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
	response, provider, cancelProvider, _, err := server.openValidatedChatStreamWithFallback(
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

func TestOpenValidatedChatStreamFallsBackToBailianWhenInitialProviderOpenFails(t *testing.T) {
	firstHits := int32(0)
	firstServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&firstHits, 1)
		http.Error(w, "bad relay", http.StatusBadGateway)
	}))
	defer firstServer.Close()
	secondHits := int32(0)
	secondServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&secondHits, 1)
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer secondServer.Close()
	bailianHits := int32(0)
	bailianServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&bailianHits, 1)
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer bailianServer.Close()

	clearGPTRelayKeyEnvForTest(t)
	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_PROVIDER_1_BASE_URL", firstServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_1", "sk-provider-one")
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_2", "sk-provider-one-backup")
	t.Setenv("GPT_RELAY_PROVIDER_2_BASE_URL", secondServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_2_API_KEY_1", "sk-provider-two")
	t.Setenv("BAILIAN_BASE_URL", bailianServer.URL)
	t.Setenv("DASHSCOPE_API_KEY", "test-bailian")

	server := &Server{
		logger:   slog.New(slog.NewTextHandler(io.Discard, nil)),
		bailian:  NewBailianClient(),
		gptRelay: NewGPTRelayClientFromEnv(),
	}
	response, provider, cancelProvider, _, err := server.openValidatedChatStreamWithFallback(
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
	cancelProvider()
	if provider != "bailian" {
		t.Fatalf("provider = %q, want bailian", provider)
	}
	if got := atomic.LoadInt32(&firstHits); got != 2 {
		t.Fatalf("first provider hits=%d, want 2", got)
	}
	if got := atomic.LoadInt32(&secondHits); got != 0 {
		t.Fatalf("second provider should not be hit within the same request, hits=%d", got)
	}
	if got := atomic.LoadInt32(&bailianHits); got != 1 {
		t.Fatalf("bailian should be hit after the selected relay provider fails, hits=%d", got)
	}
}

func TestGPTRelayRetriesQuotaBadRequestWithinSameProvider(t *testing.T) {
	clearGPTRelayKeyEnvForTest(t)
	firstHits := int32(0)
	firstServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&firstHits, 1)
		switch r.Header.Get("Authorization") {
		case "Bearer sk-provider-one-1":
			http.Error(w, `{"error":{"message":"insufficient quota"}}`, http.StatusBadRequest)
		case "Bearer sk-provider-one-2":
			w.Header().Set("Content-Type", "text/event-stream")
			_, _ = w.Write([]byte("event: response.completed\n"))
			_, _ = w.Write([]byte("data: {\"type\":\"response.completed\"}\n\n"))
		default:
			t.Fatalf("unexpected Authorization header: %q", r.Header.Get("Authorization"))
		}
	}))
	defer firstServer.Close()

	secondHits := int32(0)
	secondServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&secondHits, 1)
		http.Error(w, "should not hit second provider", http.StatusInternalServerError)
	}))
	defer secondServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_PROVIDER_1_BASE_URL", firstServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_1", "sk-provider-one-1")
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_2", "sk-provider-one-2")
	t.Setenv("GPT_RELAY_PROVIDER_2_BASE_URL", secondServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_2_API_KEY_1", "sk-provider-two")

	response, err := NewGPTRelayClientFromEnv().OpenStream(
		context.Background(),
		[]BailianMessage{{Role: "user", Content: "test"}},
	)
	if err != nil {
		t.Fatalf("open stream: %v", err)
	}
	_ = response.Body.Close()
	if response.StatusCode != http.StatusOK {
		t.Fatalf("status=%d, want %d", response.StatusCode, http.StatusOK)
	}
	if got := atomic.LoadInt32(&firstHits); got != 2 {
		t.Fatalf("first provider hits=%d, want 2", got)
	}
	if got := atomic.LoadInt32(&secondHits); got != 0 {
		t.Fatalf("second provider should not be hit within the same request, hits=%d", got)
	}
}

func TestGPTRelayKeyMaxAttemptsIsCappedAtFive(t *testing.T) {
	clearGPTRelayKeyEnvForTest(t)
	hits := int32(0)
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&hits, 1)
		http.Error(w, "bad relay", http.StatusBadGateway)
	}))
	defer modelServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_KEY_MAX_ATTEMPTS", "20")
	t.Setenv("GPT_RELAY_PROVIDER_1_BASE_URL", modelServer.URL)
	for i := 1; i <= 6; i++ {
		t.Setenv(fmt.Sprintf("GPT_RELAY_PROVIDER_1_API_KEY_%d", i), fmt.Sprintf("sk-provider-one-%d", i))
	}

	client := NewGPTRelayClientFromEnv()
	response, err := client.OpenStream(context.Background(), []BailianMessage{{Role: "user", Content: "test"}})
	if err != nil {
		t.Fatalf("open stream: %v", err)
	}
	defer response.Body.Close()
	if got := atomic.LoadInt32(&hits); got != 5 {
		t.Fatalf("provider key attempts=%d, want 5", got)
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
	response, provider, cancelProvider, _, err := server.openValidatedChatStreamWithFallback(
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

func TestOpenValidatedChatStreamIgnoresRetiredCircuitEnv(t *testing.T) {
	gptHits := int32(0)
	gptServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&gptHits, 1)
		http.Error(w, "bad relay", http.StatusBadGateway)
	}))
	defer gptServer.Close()
	bailianHits := int32(0)
	bailianServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt32(&bailianHits, 1)
		w.Header().Set("Content-Type", "text/event-stream")
		_, _ = w.Write([]byte("data: [DONE]\n\n"))
	}))
	defer bailianServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_BASE_URL", gptServer.URL)
	t.Setenv("GPT_RELAY_API_KEY", "sk-test-relay")
	t.Setenv("GPT_RELAY_CIRCUIT_CONSECUTIVE_FAILURES", "1")
	t.Setenv("GPT_RELAY_CIRCUIT_OPEN_SECONDS", "60")
	t.Setenv("BAILIAN_BASE_URL", bailianServer.URL)
	t.Setenv("DASHSCOPE_API_KEY", "test-bailian")

	server := &Server{
		logger:   slog.New(slog.NewTextHandler(io.Discard, nil)),
		bailian:  NewBailianClient(),
		gptRelay: NewGPTRelayClientFromEnv(),
	}
	for i := 0; i < 2; i++ {
		response, provider, cancelProvider, _, err := server.openValidatedChatStreamWithFallback(
			context.Background(),
			time.Now(),
			[]BailianMessage{{Role: "user", Content: "bailian"}},
			[]BailianMessage{{Role: "user", Content: "gpt"}},
			BailianStreamOptions{},
		)
		if err != nil {
			t.Fatalf("open stream %d: %v", i+1, err)
		}
		_ = response.Body.Close()
		cancelProvider()
		if provider != "bailian" {
			t.Fatalf("provider %d = %q, want bailian", i+1, provider)
		}
	}
	if got := atomic.LoadInt32(&gptHits); got != 2 {
		t.Fatalf("retired circuit env should not skip gpt relay, gpt hits=%d", got)
	}
	if got := atomic.LoadInt32(&bailianHits); got != 2 {
		t.Fatalf("both requests should reach bailian fallback, hits=%d", got)
	}
}

func TestOpenValidatedChatStreamCanceledRequestFailsWithoutCircuitState(t *testing.T) {
	gptServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "should not count canceled request", http.StatusBadGateway)
	}))
	defer gptServer.Close()
	bailianServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
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
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	response, _, cancelProvider, _, err := server.openValidatedChatStreamWithFallback(
		ctx,
		time.Now(),
		[]BailianMessage{{Role: "user", Content: "bailian"}},
		[]BailianMessage{{Role: "user", Content: "gpt"}},
		BailianStreamOptions{},
	)
	if response != nil {
		_ = response.Body.Close()
	}
	cancelProvider()
	if err == nil {
		t.Fatal("expected canceled context to fail opening fallback stream")
	}
}

func TestGPTRelayFirstVisibleTimeoutDefaultAndClamp(t *testing.T) {
	t.Setenv("CHAT_STREAM_MAX_DURATION_SECONDS", "50")
	t.Setenv("GPT_RELAY_FIRST_VISIBLE_TIMEOUT_SECONDS", "45")
	if got := resolveChatStreamFirstVisibleTimeoutForProvider(gptRelayProvider); got != defaultGPTRelayFirstVisibleTimeout {
		t.Fatalf("default gpt relay first visible timeout = %s, want %s", got, defaultGPTRelayFirstVisibleTimeout)
	}

	t.Setenv("CHAT_STREAM_MAX_DURATION_SECONDS", "5")
	t.Setenv("GPT_RELAY_FIRST_VISIBLE_TIMEOUT_SECONDS", "10")
	if got := resolveChatStreamFirstVisibleTimeoutForProvider(gptRelayProvider); got != 5*time.Second {
		t.Fatalf("gpt relay first visible timeout should clamp to max duration, got %s", got)
	}
}

func TestGPTRelayHTTPClientDoesNotUseNetworkPhaseTimeouts(t *testing.T) {
	t.Setenv("GPT_RELAY_DIAL_TIMEOUT_SECONDS", "99")
	t.Setenv("GPT_RELAY_TLS_HANDSHAKE_TIMEOUT_SECONDS", "99")
	t.Setenv("GPT_RELAY_RESPONSE_HEADER_TIMEOUT_SECONDS", "99")
	client := newGPTRelayHTTPClient()
	transport, ok := client.Transport.(*http.Transport)
	if !ok {
		t.Fatalf("transport type=%T, want *http.Transport", client.Transport)
	}
	if transport.TLSHandshakeTimeout != 0 {
		t.Fatalf("TLSHandshakeTimeout=%s, want 0", transport.TLSHandshakeTimeout)
	}
	if transport.ResponseHeaderTimeout != 0 {
		t.Fatalf("ResponseHeaderTimeout=%s, want 0", transport.ResponseHeaderTimeout)
	}
}

func TestGPTRelayRecordsCanceledOpenAttempt(t *testing.T) {
	clearGPTRelayKeyEnvForTest(t)
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(100 * time.Millisecond)
	}))
	defer modelServer.Close()

	t.Setenv("GPT_RELAY_ENABLED", "true")
	t.Setenv("GPT_RELAY_PROVIDER_1_BASE_URL", modelServer.URL)
	t.Setenv("GPT_RELAY_PROVIDER_1_LABEL", "Relay One")
	t.Setenv("GPT_RELAY_PROVIDER_1_API_KEY_1", "sk-provider-one")

	client := NewGPTRelayClientFromEnv()
	var recorded atomic.Bool
	var recordedAttempt gptRelayAttemptRecord
	client.SetAttemptRecorder(func(ctx context.Context, attempt gptRelayAttemptRecord) {
		recorded.Store(true)
		recordedAttempt = attempt
	})
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Millisecond)
	defer cancel()
	_, err := client.OpenStream(ctx, []BailianMessage{{Role: "user", Content: "test"}})
	if err == nil {
		t.Fatal("expected timeout opening relay stream")
	}
	if !recorded.Load() {
		t.Fatal("expected canceled attempt to be recorded")
	}
	if recordedAttempt.ProviderLabel != "Relay One" || recordedAttempt.KeySlot != "GPT_RELAY_PROVIDER_1_API_KEY_1" {
		t.Fatalf("recorded attempt=%#v, want provider label and key slot", recordedAttempt)
	}
	if recordedAttempt.Status != "failed" {
		t.Fatalf("recorded status=%q, want failed", recordedAttempt.Status)
	}
	if recordedAttempt.ErrorKind != "context_deadline_exceeded" && recordedAttempt.ErrorKind != "context_canceled" {
		t.Fatalf("recorded error kind=%q", recordedAttempt.ErrorKind)
	}
}

func TestGPTRelayFirstVisibleTimeoutCountsFromRequestReceived(t *testing.T) {
	t.Setenv("CHAT_STREAM_MAX_DURATION_SECONDS", "50")
	t.Setenv("GPT_RELAY_FIRST_VISIBLE_TIMEOUT_SECONDS", "45")

	remaining := resolveChatStreamFirstVisibleTimeoutForProviderAfter(gptRelayProvider, 44*time.Second)
	if remaining < 900*time.Millisecond || remaining > 1100*time.Millisecond {
		t.Fatalf("remaining first visible timeout = %s, want about 1s", remaining)
	}

	if got := resolveChatStreamFirstVisibleTimeoutForProviderAfter(gptRelayProvider, 46*time.Second); got != time.Millisecond {
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

func TestGPTRelayPromptVariantIncludesChatOutputConstraintAndNetworkingRule(t *testing.T) {
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
		true,
	)
	if usedCount != 1 || !hasMemory {
		t.Fatalf("prompt metadata mismatch used=%d hasMemory=%v", usedCount, hasMemory)
	}
	foundOutputConstraint := false
	for _, message := range messages {
		if message.Role == "system" && message.Content == chatOutputConstraint {
			foundOutputConstraint = true
		}
	}
	if !foundOutputConstraint {
		t.Fatalf("gpt relay prompt must include chat output constraint")
	}
	last := messages[len(messages)-1]
	if last.Role != "user" || last.Content != "current" {
		t.Fatalf("expected current user message at tail, got %#v", last)
	}
	instructions, input := buildGPTRelayResponsesPrompt(messages)
	if !strings.Contains(instructions, "anchor") {
		t.Fatalf("gpt relay instructions missing anchor:\n%s", instructions)
	}
	if !strings.Contains(instructions, "【输出约束】") {
		t.Fatalf("gpt relay instructions missing output constraint:\n%s", instructions)
	}
	if !strings.Contains(instructions, "【GPT专用规则】") {
		t.Fatalf("gpt relay instructions missing GPT-specific rule:\n%s", instructions)
	}
	if !strings.Contains(instructions, "带图或高风险问题，必须深度思考。") {
		t.Fatalf("gpt relay instructions missing image reasoning rule:\n%s", instructions)
	}
	anchorIndex := strings.Index(instructions, "anchor")
	outputIndex := strings.Index(instructions, "【输出约束】")
	gptRuleIndex := strings.Index(instructions, "【GPT专用规则】")
	contextIndex := strings.Index(instructions, "context")
	memoryIndex := strings.Index(instructions, "memory")
	if anchorIndex < 0 || outputIndex < 0 || gptRuleIndex < 0 || contextIndex < 0 || memoryIndex < 0 {
		t.Fatalf("gpt relay instructions missing order markers:\n%s", instructions)
	}
	if !(anchorIndex < outputIndex && outputIndex < gptRuleIndex && gptRuleIndex < memoryIndex && memoryIndex < contextIndex) {
		t.Fatalf("gpt relay stable instructions should precede dynamic context for cache reuse:\n%s", instructions)
	}
	if len(input) == 0 {
		t.Fatalf("gpt relay input should keep non-system messages")
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

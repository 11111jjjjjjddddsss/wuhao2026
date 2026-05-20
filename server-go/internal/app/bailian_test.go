package app

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
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

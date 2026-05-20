package app

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestExtractSummaryUsesQwen35FlashWithThinkingDisabled(t *testing.T) {
	var captured map[string]any
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/chat/completions" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"新的摘要"}}]}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	assetDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(assetDir, "b_extraction_prompt.txt"), []byte("B prompt"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(assetDir, "c_extraction_prompt.txt"), []byte("C prompt"), 0o600); err != nil {
		t.Fatal(err)
	}

	service := &SummaryService{
		prompts: NewPromptLoader(assetDir),
		bailian: NewBailianClient(),
	}

	summary, err := service.extractSummary(
		context.Background(),
		SummaryLayerB,
		"old summary",
		"user: 问题\nassistant: 回复",
	)
	if err != nil {
		t.Fatalf("extract summary: %v", err)
	}
	if summary != "新的摘要" {
		t.Fatalf("summary mismatch: %q", summary)
	}
	if got := captured["model"]; got != "qwen3.5-flash" {
		t.Fatalf("model mismatch: %#v", got)
	}
	if got := captured["stream"]; got != false {
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
}

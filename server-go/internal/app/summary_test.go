package app

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
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

func TestProcessCSummaryUsesRecentArchivedRounds(t *testing.T) {
	var captured map[string]any
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"新的长期记忆"}}]}`))
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

	archiveRounds := make([]SessionRound, 0, cSummaryArchiveRounds)
	for i := 1; i <= cSummaryArchiveRounds; i++ {
		archiveRounds = append(archiveRounds, SessionRound{
			User:      fmt.Sprintf("归档第%d轮用户", i),
			Assistant: fmt.Sprintf("归档第%d轮回复", i),
		})
	}
	store := &fakeSummaryStore{archiveRounds: archiveRounds}
	service := &SummaryService{
		store:   store,
		prompts: NewPromptLoader(assetDir),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(io.Discard, nil)),
	}

	service.ProcessSessionSummaries("u1", &SessionSnapshot{
		UserID:        "u1",
		ARoundsFull:   []SessionRound{{User: "A窗口用户", Assistant: "A窗口回复"}},
		CSummary:      "旧长期记忆",
		PendingRetryC: true,
		RoundTotal:    20,
	})

	if store.archiveLimit != cSummaryArchiveRounds {
		t.Fatalf("archive limit mismatch: %d", store.archiveLimit)
	}
	if store.cSummary != "新的长期记忆" || store.cExpectedRoundTotal != 20 {
		t.Fatalf("C summary write mismatch: summary=%q round=%d", store.cSummary, store.cExpectedRoundTotal)
	}
	messages, _ := captured["messages"].([]any)
	if len(messages) != 2 {
		t.Fatalf("messages mismatch: %#v", captured["messages"])
	}
	userMessage, _ := messages[1].(map[string]any)
	content := asString(userMessage["content"])
	if !strings.Contains(content, "归档第1轮用户") || !strings.Contains(content, "归档第20轮用户") {
		t.Fatalf("expected archived rounds in C summary input, got %q", content)
	}
	if strings.Contains(content, "A窗口用户") {
		t.Fatalf("C summary input should not fall back to A window when archive exists: %q", content)
	}
}

func TestProcessCSummaryKeepsPendingWhenArchiveHasFewerThanTwentyRounds(t *testing.T) {
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("model should not be called before C archive has 20 rounds")
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

	store := &fakeSummaryStore{
		archiveRounds: []SessionRound{{User: "只有1轮", Assistant: "回复"}},
	}
	service := &SummaryService{
		store:   store,
		prompts: NewPromptLoader(assetDir),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(io.Discard, nil)),
	}

	service.ProcessSessionSummaries("u1", &SessionSnapshot{
		UserID:        "u1",
		ARoundsFull:   []SessionRound{{User: "A窗口用户", Assistant: "A窗口回复"}},
		PendingRetryC: true,
		RoundTotal:    20,
	})

	if store.pendingLayer != SummaryLayerC || !store.pendingValue {
		t.Fatalf("expected C pending retry to remain true, got layer=%q pending=%v", store.pendingLayer, store.pendingValue)
	}
	if store.cSummary != "" {
		t.Fatalf("C summary should not be written with insufficient archive rounds: %q", store.cSummary)
	}
}

func TestProcessSummaryTimeoutReleasesRunningGuard(t *testing.T) {
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(200 * time.Millisecond)
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"迟到摘要"}}]}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	previousTimeout := summaryExtractionTimeout
	summaryExtractionTimeout = 30 * time.Millisecond
	defer func() {
		summaryExtractionTimeout = previousTimeout
	}()

	assetDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(assetDir, "b_extraction_prompt.txt"), []byte("B prompt"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(assetDir, "c_extraction_prompt.txt"), []byte("C prompt"), 0o600); err != nil {
		t.Fatal(err)
	}

	store := &fakeSummaryStore{}
	service := &SummaryService{
		store:   store,
		prompts: NewPromptLoader(assetDir),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(io.Discard, nil)),
	}

	started := time.Now()
	service.ProcessSessionSummaries("u1", &SessionSnapshot{
		UserID:        "u1",
		ARoundsFull:   []SessionRound{{User: "用户问题", Assistant: "助手回复"}},
		PendingRetryB: true,
		RoundTotal:    6,
	})
	if elapsed := time.Since(started); elapsed > time.Second {
		t.Fatalf("summary timeout took too long: %s", elapsed)
	}
	if store.pendingLayer != SummaryLayerB || !store.pendingValue {
		t.Fatalf("expected B pending retry to remain true, got layer=%q pending=%v", store.pendingLayer, store.pendingValue)
	}
	if !service.tryStartLayer("u1", SummaryLayerB) {
		t.Fatal("summary running guard should be released after timeout")
	}
	service.finishLayer("u1", SummaryLayerB)
}

type fakeSummaryStore struct {
	archiveRounds       []SessionRound
	archiveLimit        int
	cSummary            string
	cExpectedRoundTotal int
	pendingLayer        SummaryLayer
	pendingValue        bool
}

func (f *fakeSummaryStore) WriteUserBSummaryIfCurrent(ctx context.Context, userID string, summary string, expectedRoundTotal int) (bool, error) {
	return true, nil
}

func (f *fakeSummaryStore) WriteUserCSummaryIfCurrent(ctx context.Context, userID string, summary string, expectedRoundTotal int) (bool, error) {
	f.cSummary = summary
	f.cExpectedRoundTotal = expectedRoundTotal
	return true, nil
}

func (f *fakeSummaryStore) SetUserSummaryPending(ctx context.Context, userID string, layer SummaryLayer, pending bool) error {
	f.pendingLayer = layer
	f.pendingValue = pending
	return nil
}

func (f *fakeSummaryStore) GetRecentSessionRoundsForSummary(ctx context.Context, userID string, limit int) ([]SessionRound, error) {
	f.archiveLimit = limit
	return f.archiveRounds, nil
}

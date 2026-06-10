package app

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync"
	"testing"
	"time"
)

func TestExtractSummaryUsesFixedQwenPlusWithoutThinking(t *testing.T) {
	var captured map[string]any
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/chat/completions" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"短期承接：用户正在核对番茄叶片发黄问题，需继续追问新叶老叶差异。\n长期背景：暂无稳定长期背景可沉淀\n用户画像：暂无稳定用户画像可沉淀\n农业重点事件：近期提到番茄叶片发黄，仍需核对水肥、根系和病虫害线索。"}}],"usage":{"prompt_tokens":120,"completion_tokens":30}}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)
	service := &SummaryService{
		prompts: summaryTestPromptLoader(t),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(os.Stderr, nil)),
	}
	result, err := service.extractSummary(context.Background(), "短期承接：旧记忆", "user: 番茄叶片发黄\nassistant: 先看新叶老叶差异")
	if err != nil {
		t.Fatalf("extract summary: %v", err)
	}

	if !strings.Contains(result.MemoryDocument, "短期承接：用户正在核对番茄叶片发黄问题") {
		t.Fatalf("memory document mismatch: %q", result.MemoryDocument)
	}
	if got := captured["model"]; got != summaryExtractionModel {
		t.Fatalf("summary model = %#v, want %q", got, summaryExtractionModel)
	}
	if got := captured["enable_thinking"]; got != false {
		t.Fatalf("enable_thinking = %#v, want false at top level", got)
	}
	if _, ok := captured["extra_body"]; ok {
		t.Fatalf("summary request should not use extra_body for thinking control: %#v", captured["extra_body"])
	}
	if got, ok := captured["temperature"].(float64); !ok || got != unifiedModelTemperature {
		t.Fatalf("temperature = %#v, want %v", captured["temperature"], unifiedModelTemperature)
	}
	messages, ok := captured["messages"].([]any)
	if !ok || len(messages) != 2 {
		t.Fatalf("messages mismatch: %#v", captured["messages"])
	}
	userMessage, _ := messages[1].(map[string]any)
	userContent, _ := userMessage["content"].(string)
	if !strings.Contains(userContent, "[已有记忆文档]") || !strings.Contains(userContent, "[最近对话]") {
		t.Fatalf("user content missing memory/dialogue blocks: %q", userContent)
	}
	for _, forbidden := range []string{"[已有短期记忆]", "[已有长期组合记忆]", "B层", "C层"} {
		if strings.Contains(userContent, forbidden) {
			t.Fatalf("user content contains old summary label %q: %q", forbidden, userContent)
		}
	}
}

func TestNormalizeMemoryDocumentKeepsOldMissingSections(t *testing.T) {
	oldMemory := "短期承接：旧短期\n长期背景：旧长期\n用户画像：旧画像\n农业重点事件：旧农业事件"

	result, err := normalizeMemoryDocumentExtraction("短期承接：新短期\n农业重点事件：新农业事件", oldMemory)
	if err != nil {
		t.Fatalf("normalize memory document: %v", err)
	}
	got := result.MemoryDocument
	if !strings.Contains(got, "短期承接：新短期") {
		t.Fatalf("new short-term section not applied: %q", got)
	}
	if !strings.Contains(got, "长期背景：旧长期") {
		t.Fatalf("missing long-term section should keep old value: %q", got)
	}
	if !strings.Contains(got, "用户画像：旧画像") {
		t.Fatalf("missing profile section should keep old value: %q", got)
	}
	if !strings.Contains(got, "农业重点事件：新农业事件") {
		t.Fatalf("new agri section not applied: %q", got)
	}
}

func TestNormalizeMemoryDocumentAllowsExplicitEmptyFallback(t *testing.T) {
	oldMemory := "短期承接：旧短期\n长期背景：旧长期\n用户画像：旧画像\n农业重点事件：旧农业事件"

	result, err := normalizeMemoryDocumentExtraction("用户画像：暂无稳定用户画像可沉淀", oldMemory)
	if err != nil {
		t.Fatalf("normalize memory document: %v", err)
	}
	if !strings.Contains(result.MemoryDocument, "用户画像：暂无稳定用户画像可沉淀") {
		t.Fatalf("explicit empty profile should overwrite old value: %q", result.MemoryDocument)
	}
}

func TestProcessSessionSummariesKeepsPendingOnTimeout(t *testing.T) {
	previousTimeout := summaryExtractionTimeout
	summaryExtractionTimeout = 20 * time.Millisecond
	defer func() { summaryExtractionTimeout = previousTimeout }()

	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(200 * time.Millisecond)
		w.WriteHeader(http.StatusGatewayTimeout)
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	store := &summaryFakeStore{}
	service := &SummaryService{
		store:   store,
		prompts: summaryTestPromptLoader(t),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(os.Stderr, nil)),
	}
	snapshot := &SessionSnapshot{
		UserID:         "u1",
		PendingMemory:  true,
		MemoryDocument: "短期承接：旧短期",
		RoundTotal:     6,
		ARoundsFull: []SessionRound{
			{User: "番茄叶片发黄", Assistant: "先看新叶老叶差异"},
		},
	}

	service.ProcessSessionSummaries("u1", snapshot)

	if store.writeCalls != 0 {
		t.Fatalf("memory document should not be written on timeout")
	}
	if !store.pendingSet || !store.pendingValue {
		t.Fatalf("pending memory should be kept on timeout: %#v", store)
	}
	if !snapshot.PendingMemory {
		t.Fatalf("snapshot pending flag should remain true")
	}
}

type summaryFakeStore struct {
	mu           sync.Mutex
	writeCalls   int
	pendingSet   bool
	pendingValue bool
}

func (s *summaryFakeStore) WriteUserMemoryDocumentIfCurrent(ctx context.Context, userID string, memoryDocument string, expectedRoundTotal int) (bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.writeCalls++
	return true, nil
}

func (s *summaryFakeStore) SetUserMemoryPending(ctx context.Context, userID string, pending bool) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.pendingSet = true
	s.pendingValue = pending
	return nil
}

func summaryTestPromptLoader(t *testing.T) *PromptLoader {
	t.Helper()
	dir := t.TempDir()
	prompt := "请输出短期承接、长期背景、用户画像、农业重点事件四段纯文字。"
	if err := os.WriteFile(dir+"/summary_extraction_prompt.txt", []byte(prompt), 0o600); err != nil {
		t.Fatalf("write summary prompt: %v", err)
	}
	return NewPromptLoader(dir)
}

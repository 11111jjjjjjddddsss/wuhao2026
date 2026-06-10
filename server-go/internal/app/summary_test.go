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

func TestExtractSummaryUsesDefaultQwen35FlashWithThinkingDisabled(t *testing.T) {
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
	if got := captured["enable_thinking"]; got != false {
		t.Fatalf("enable_thinking mismatch: %#v", got)
	}
}

func TestExtractSummaryCanOverrideCModelOnly(t *testing.T) {
	var capturedModels []string
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var captured map[string]any
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		capturedModels = append(capturedModels, asString(captured["model"]))
		if got := captured["enable_thinking"]; got != false {
			t.Fatalf("enable_thinking mismatch: %#v", got)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"新的摘要"}}]}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)
	t.Setenv("C_SUMMARY_MODEL", "qwen-plus")

	service := &SummaryService{
		prompts: newTestSummaryPromptLoader(t),
		bailian: NewBailianClient(),
	}

	if _, err := service.extractSummary(context.Background(), SummaryLayerB, "", "user: 问题\nassistant: 回复"); err != nil {
		t.Fatalf("extract B summary: %v", err)
	}
	if _, err := service.extractSummary(context.Background(), SummaryLayerC, "", "user: 问题\nassistant: 回复"); err != nil {
		t.Fatalf("extract C summary: %v", err)
	}
	want := []string{"qwen3.5-flash", "qwen-plus"}
	if len(capturedModels) != len(want) {
		t.Fatalf("captured models = %#v, want %#v", capturedModels, want)
	}
	for i := range want {
		if capturedModels[i] != want[i] {
			t.Fatalf("captured models = %#v, want %#v", capturedModels, want)
		}
	}
}

func TestExtractSummaryDoesNotExposeHTTPErrorBody(t *testing.T) {
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, strings.Repeat("x", bailianBodyPreviewLimit+1024), http.StatusBadGateway)
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	service := &SummaryService{
		prompts: newTestSummaryPromptLoader(t),
		bailian: NewBailianClient(),
	}

	_, err := service.extractSummary(context.Background(), SummaryLayerB, "", "user: 问题\nassistant: 回复")
	if err == nil {
		t.Fatal("expected extract summary error")
	}
	message := err.Error()
	if !strings.Contains(message, "B_EXTRACT_HTTP_502") {
		t.Fatalf("error = %q, want B_EXTRACT_HTTP_502", message)
	}
	if strings.Contains(message, strings.Repeat("x", 16)) {
		t.Fatalf("error body leaked into summary error: %q", message)
	}
}

func TestExtractSummaryRejectsLargeSuccessBody(t *testing.T) {
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(strings.Repeat("x", summaryResponseLimit+1)))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	service := &SummaryService{
		prompts: newTestSummaryPromptLoader(t),
		bailian: NewBailianClient(),
	}

	_, err := service.extractSummary(context.Background(), SummaryLayerB, "", "user: 问题\nassistant: 回复")
	if err == nil || !strings.Contains(err.Error(), "B_EXTRACT_RESPONSE_TOO_LARGE") {
		t.Fatalf("extract summary error = %v, want B_EXTRACT_RESPONSE_TOO_LARGE", err)
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

func TestProductionCSummaryPromptGuardsAgainstDiagnosisUpgrade(t *testing.T) {
	prompt, err := NewPromptLoader(filepath.Join("..", "..", "assets")).SummaryPrompt(SummaryLayerC)
	if err != nil {
		t.Fatalf("load C prompt: %v", err)
	}
	for _, want := range []string{
		"不得使用“确诊”“确定”“确认为”“已排除”“已经证实”等定性词",
		"必须保持为倾向或待核对状态",
		"不用编号、项目符号、Markdown 加粗或表格",
	} {
		if !strings.Contains(prompt, want) {
			t.Fatalf("C prompt missing guard %q: %s", want, prompt)
		}
	}
}

func TestProductionBAndCSummaryPromptsGuardLiveSampleDrift(t *testing.T) {
	loader := NewPromptLoader(filepath.Join("..", "..", "assets"))
	bPrompt, err := loader.SummaryPrompt(SummaryLayerB)
	if err != nil {
		t.Fatalf("load B prompt: %v", err)
	}
	for _, want := range []string{
		"只能写成暂未发现或仍需检查",
		"不能改写成“已排除虫害/已排除某方向”",
		"用户明确说“好了/已解决/不用管了”的 APP 操作问题直接删除",
	} {
		if !strings.Contains(bPrompt, want) {
			t.Fatalf("B prompt missing guard %q: %s", want, bPrompt)
		}
	}

	cPrompt, err := loader.SummaryPrompt(SummaryLayerC)
	if err != nil {
		t.Fatalf("load C prompt: %v", err)
	}
	for _, want := range []string{
		"一次“帮亲戚看地/临时代管/另一个棚”等描述",
		"不得写成用户长期画像或长期通用事实",
		"暂无稳定用户画像可沉淀",
	} {
		if !strings.Contains(cPrompt, want) {
			t.Fatalf("C prompt missing profile guard %q: %s", want, cPrompt)
		}
	}
}

func newTestSummaryPromptLoader(t *testing.T) *PromptLoader {
	t.Helper()
	assetDir := t.TempDir()
	if err := os.WriteFile(filepath.Join(assetDir, "b_extraction_prompt.txt"), []byte("B prompt"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(assetDir, "c_extraction_prompt.txt"), []byte("C prompt"), 0o600); err != nil {
		t.Fatal(err)
	}
	return NewPromptLoader(assetDir)
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

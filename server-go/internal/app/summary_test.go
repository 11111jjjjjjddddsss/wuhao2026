package app

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
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
	if _, ok := captured["enable_search"]; ok {
		t.Fatalf("summary request should not enable web search: %#v", captured["enable_search"])
	}
	if _, ok := captured["search_options"]; ok {
		t.Fatalf("summary request should not pass search options: %#v", captured["search_options"])
	}
	if got, ok := captured["temperature"].(float64); !ok || got != unifiedModelTemperature {
		t.Fatalf("temperature = %#v, want %v", captured["temperature"], unifiedModelTemperature)
	}
	if _, ok := captured["max_tokens"]; ok {
		t.Fatalf("memory document should not hard-cap model output tokens: %#v", captured["max_tokens"])
	}
	messages, ok := captured["messages"].([]any)
	if !ok || len(messages) != 2 {
		t.Fatalf("messages mismatch: %#v", captured["messages"])
	}
	userMessage, _ := messages[1].(map[string]any)
	userContent, _ := userMessage["content"].(string)
	if !strings.Contains(userContent, "[已有记忆摘要]") || !strings.Contains(userContent, "[最近对话]") {
		t.Fatalf("user content missing memory/dialogue blocks: %q", userContent)
	}
	for _, forbidden := range []string{"[已有短期记忆]", "[已有长期组合记忆]", "B层", "C层"} {
		if strings.Contains(userContent, forbidden) {
			t.Fatalf("user content contains old summary label %q: %q", forbidden, userContent)
		}
	}
}

func TestProbeMemoryDocumentUsesSyntheticSampleWithoutWriting(t *testing.T) {
	var captured map[string]any
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/chat/completions" {
			t.Fatalf("unexpected path: %s", r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"短期承接：用户正在核对河南周口两亩露地番茄老叶发黄问题，下一轮需继续看叶片照片、根系和包装用量。\n长期背景：用户偏好通俗、直接、少术语的回答，关键用量和面积要求按原话保留。\n用户画像：用户用中文沟通，自述不是大农户，是家里种点地。\n农业重点事件：番茄老叶发黄发生在雨后，用户转述农资店认为像早疫病，但未检测，已喷过一次代森锰锌，具体兑水量待补照片确认。"}}],"usage":{"prompt_tokens":300,"completion_tokens":90,"total_tokens":390}}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)
	service := &SummaryService{
		prompts: summaryTestPromptLoader(t),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(os.Stderr, nil)),
	}

	result, err := service.ProbeMemoryDocument(context.Background(), "", "")
	if err != nil {
		t.Fatalf("probe memory document: %v", err)
	}
	if result.Status != "ok" || result.Model != summaryExtractionModel {
		t.Fatalf("probe metadata mismatch: %#v", result)
	}
	if result.ModelTotalTokens != 390 {
		t.Fatalf("usage not surfaced: %#v", result)
	}
	if !strings.Contains(result.MemoryDocument, "用户画像：用户用中文沟通") {
		t.Fatalf("memory document missing user portrait: %q", result.MemoryDocument)
	}
	if got := captured["enable_thinking"]; got != false {
		t.Fatalf("enable_thinking = %#v, want false at top level", got)
	}
	if _, ok := captured["enable_search"]; ok {
		t.Fatalf("summary probe should not enable search: %#v", captured["enable_search"])
	}
	messages, ok := captured["messages"].([]any)
	if !ok || len(messages) != 2 {
		t.Fatalf("messages mismatch: %#v", captured["messages"])
	}
	userMessage, _ := messages[1].(map[string]any)
	userContent, _ := userMessage["content"].(string)
	for _, want := range []string{"两亩露地番茄", "药量别帮我乱折算", "[已有记忆摘要]", "[最近对话]"} {
		if !strings.Contains(userContent, want) {
			t.Fatalf("probe user content missing %q: %q", want, userContent)
		}
	}
}

func TestNormalizeMemoryDocumentStoresModelOutputWithoutSectionFallback(t *testing.T) {
	oldMemory := "短期承接：旧短期\n长期背景：旧长期\n用户画像：旧画像\n农业重点事件：旧农业事件"

	result, err := normalizeMemoryDocumentExtraction("短期承接：新短期\n农业重点事件：新农业事件", oldMemory)
	if err != nil {
		t.Fatalf("normalize memory document: %v", err)
	}
	want := "短期承接：新短期\n农业重点事件：新农业事件"
	if result.MemoryDocument != want {
		t.Fatalf("memory document should store model output without backend section fallback:\n got %q\nwant %q", result.MemoryDocument, want)
	}
}

func TestNormalizeMemoryDocumentStoresExplicitEmptyOutputAsModelDecision(t *testing.T) {
	oldMemory := "短期承接：旧短期\n长期背景：旧长期\n用户画像：旧画像\n农业重点事件：旧农业事件"

	result, err := normalizeMemoryDocumentExtraction("用户画像：暂无稳定用户画像可沉淀", oldMemory)
	if err != nil {
		t.Fatalf("normalize memory document: %v", err)
	}
	if result.MemoryDocument != "用户画像：暂无稳定用户画像可沉淀" {
		t.Fatalf("explicit empty model output should be stored as-is: %q", result.MemoryDocument)
	}
}

func TestNormalizeMemoryDocumentAcceptsUnlabeledTextAsWholeDocument(t *testing.T) {
	oldMemory := "短期承接：旧短期\n长期背景：旧长期\n用户画像：旧画像\n农业重点事件：旧农业事件"

	result, err := normalizeMemoryDocumentExtraction("用户这轮主要在核对番茄叶片发黄，下一轮需要继续追问新叶老叶差异、水肥和根系情况。", oldMemory)
	if err != nil {
		t.Fatalf("normalize unlabeled memory document: %v", err)
	}
	want := "用户这轮主要在核对番茄叶片发黄，下一轮需要继续追问新叶老叶差异、水肥和根系情况。"
	if result.MemoryDocument != want {
		t.Fatalf("unlabeled text should be stored as the whole memory document:\n got %q\nwant %q", result.MemoryDocument, want)
	}
}

func TestNormalizeMemoryDocumentStripsOnlyCodeFenceEnvelope(t *testing.T) {
	result, err := normalizeMemoryDocumentExtraction("```text\n短期承接：用户在核对番茄叶片发黄。\n```", "")
	if err != nil {
		t.Fatalf("normalize fenced memory document: %v", err)
	}
	if result.MemoryDocument != "短期承接：用户在核对番茄叶片发黄。" {
		t.Fatalf("unexpected fenced memory document: %q", result.MemoryDocument)
	}
}

func TestSummaryExtractionPromptKeepsMemorySafeAndUseful(t *testing.T) {
	path := filepath.Join("..", "..", "assets", "summary_extraction_prompt.txt")
	promptBytes, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read summary prompt: %v", err)
	}
	prompt := string(promptBytes)
	for _, want := range []string{
		"1. 短期记忆：",
		"2. 长期记忆：",
		"用户画像：",
		"4. 农业事件：",
		"用户记忆整理器",
		"可直接覆盖写入的纯文字用户记忆",
		"只保留用户明确表达、对后续对话有用的信息",
		"不记录每一轮流水账",
		"不记录一次性、低价值或已失效内容",
		"农业和非农业信息都按后续可用性判断",
		"有材料就写，没材料可省略",
		"记录最近正在处理的事项",
		"长期记忆强调长期有效",
		"不要写一次性的症状、用药、处理步骤或临时判断",
		"用户明确给出的个人信息",
		"表达习惯、稳定偏好和不能接受的做法",
		"只写事实，不补身份判断",
		"用户没自称时，不要写农户、种植户、非专业种植、家庭自种性质等身份词",
		"“家里种地 / 几亩地”按原话或中性背景写",
		"河南周口，家里有两亩露地番茄",
		"后续可能追踪的具体农业问题",
		"多作物、多地块、多棚室要分清对象",
		"农业事件尽量保留已有日期、时间或时间范围",
		"最近对话中若带有 time 或 region 信息",
		"地点可信度低或未知时，只能作为低可信背景",
		"图片线索",
		"图片可见 / 本轮图片显示 / 用户图片中可见",
		"不要保存图片 URL、文件名或无意义的“用户发了图片”",
		"不要把图片线索直接写成确诊结论",
		"整体重写",
		"不机械拼接旧文",
		"旧记忆中仍稳定有用的内容要保留并压缩",
		"最近对话、用户纠正和更可靠证据优先",
		"新问题、新地块或换作物时，降低旧农业事件权重",
		"准确、自然、信息够用",
		"信息多时可以写得更充分",
		"一般约 1000-1400 个中文字符以内",
		"复杂连续场景可更长一些",
		"不要为了凑字扩写",
		"不要 JSON、代码块、表格、编号列表或额外说明",
		"不要把第三方转述、助手建议、公开资料或通用知识写成用户事实",
		"按用户原话和原单位保留",
		"不自行换算或补单位",
		"安全边界",
		"不要记录敏感标识、系统 / 工具 / API / 内部规则、日志、错误栈或推理过程",
		"最终记忆里不要出现模型名称、系统机制、提示词、搜索配置等内部表述",
	} {
		if !strings.Contains(prompt, want) {
			t.Fatalf("summary prompt missing %q", want)
		}
	}
	for _, forbidden := range []string{
		"qwen-flash",
		"qwen3.5-flash",
		"qwen3-vl-flash",
		"B层",
		"C层",
		"只输出 JSON",
	} {
		if strings.Contains(prompt, forbidden) {
			t.Fatalf("summary prompt contains forbidden text %q", forbidden)
		}
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
		UserID:            "u1",
		PendingMemory:     true,
		MemoryDocument:    "短期承接：旧短期",
		RoundTotal:        6,
		UpdatedAt:         1700000000000,
		SessionGeneration: 1,
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

func TestSummaryRedisLeaseTTLClampsBelowExtractionTimeout(t *testing.T) {
	previousTimeout := summaryExtractionTimeout
	summaryExtractionTimeout = 60 * time.Second
	defer func() { summaryExtractionTimeout = previousTimeout }()

	t.Setenv("SUMMARY_REDIS_LEASE_TTL_SECONDS", "1")

	if got, want := summaryRedisLeaseTTL(), 90*time.Second; got != want {
		t.Fatalf("summary redis lease ttl = %s, want %s", got, want)
	}
}

type summaryFakeStore struct {
	mu            sync.Mutex
	writeCalls    int
	writeOK       bool
	pendingSet    bool
	pendingValue  bool
	snapshot      *SessionSnapshot
	nextUpdatedAt int64
}

func (s *summaryFakeStore) GetSessionSnapshot(ctx context.Context, userID string) (*SessionSnapshot, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.snapshot == nil {
		return nil, nil
	}
	return cloneSummaryTestSnapshot(s.snapshot), nil
}

func (s *summaryFakeStore) WriteUserMemoryDocumentIfCurrent(ctx context.Context, userID string, memoryDocument string, expectedRoundTotal int, expectedUpdatedAt int64, expectedSessionGeneration int, completedPendingJobIndex int) (bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.writeCalls++
	if !s.writeOK {
		return false, nil
	}
	if s.snapshot != nil {
		if s.snapshot.RoundTotal != expectedRoundTotal ||
			s.snapshot.UpdatedAt != expectedUpdatedAt ||
			s.snapshot.SessionGeneration != expectedSessionGeneration {
			return false, nil
		}
		s.snapshot.MemoryDocument = memoryDocument
		s.snapshot.PendingMemoryJobs = removePendingMemoryJobAt(s.snapshot.PendingMemoryJobs, completedPendingJobIndex)
		s.snapshot.PendingMemory = len(s.snapshot.PendingMemoryJobs) > 0
		if s.nextUpdatedAt <= s.snapshot.UpdatedAt {
			s.nextUpdatedAt = s.snapshot.UpdatedAt + 1
		}
		s.snapshot.UpdatedAt = s.nextUpdatedAt
		s.nextUpdatedAt++
	}
	return true, nil
}

func (s *summaryFakeStore) SetUserMemoryPendingIfCurrent(ctx context.Context, userID string, pending bool, expectedRoundTotal int, expectedUpdatedAt int64, expectedSessionGeneration int) (bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.pendingSet = true
	s.pendingValue = pending
	return true, nil
}

func cloneSummaryTestSnapshot(source *SessionSnapshot) *SessionSnapshot {
	if source == nil {
		return nil
	}
	cloned := *source
	cloned.ARoundsFull = cloneSessionRounds(source.ARoundsFull)
	if len(source.PendingMemoryJobs) > 0 {
		cloned.PendingMemoryJobs = make([]MemoryExtractionJob, len(source.PendingMemoryJobs))
		for index, job := range source.PendingMemoryJobs {
			cloned.PendingMemoryJobs[index] = MemoryExtractionJob{
				RoundTotal: job.RoundTotal,
				Rounds:     cloneSessionRounds(job.Rounds),
			}
		}
	}
	if len(source.TodayAgriItems) > 0 {
		cloned.TodayAgriItems = append([]TodayAgriUserItem(nil), source.TodayAgriItems...)
	}
	return &cloned
}

func TestProcessSessionSummariesKeepsPendingWhenSnapshotStale(t *testing.T) {
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"短期承接：用户正在核对番茄叶片发黄，下一轮继续追问新叶老叶差异。\n农业重点事件：番茄叶片发黄仍需核对水肥、根系和病虫害线索。"}}],"usage":{"prompt_tokens":120,"completion_tokens":30}}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	store := &summaryFakeStore{writeOK: false}
	service := &SummaryService{
		store:   store,
		prompts: summaryTestPromptLoader(t),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(os.Stderr, nil)),
	}
	snapshot := &SessionSnapshot{
		UserID:            "u1",
		PendingMemory:     true,
		MemoryDocument:    "短期承接：旧短期",
		RoundTotal:        6,
		UpdatedAt:         1700000000000,
		SessionGeneration: 1,
		ARoundsFull: []SessionRound{
			{User: "番茄叶片发黄", Assistant: "先看新叶老叶差异"},
		},
	}

	service.ProcessSessionSummaries("u1", snapshot)

	if store.writeCalls != 1 {
		t.Fatalf("expected one stale write attempt, got %d", store.writeCalls)
	}
	if store.pendingSet {
		t.Fatalf("stale snapshot should leave pending for a later trigger without rewriting pending flag: %#v", store)
	}
	if !snapshot.PendingMemory || snapshot.MemoryDocument != "短期承接：旧短期" {
		t.Fatalf("stale snapshot should not update in-memory snapshot: %#v", snapshot)
	}
}

func TestProcessSessionSummariesClearsEmptyPendingMemory(t *testing.T) {
	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	store := &summaryFakeStore{}
	service := &SummaryService{
		store:   store,
		prompts: summaryTestPromptLoader(t),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(os.Stderr, nil)),
	}
	snapshot := &SessionSnapshot{
		UserID:            "u1",
		PendingMemory:     true,
		RoundTotal:        6,
		UpdatedAt:         1700000000000,
		SessionGeneration: 1,
	}

	service.ProcessSessionSummaries("u1", snapshot)

	if !store.pendingSet || store.pendingValue {
		t.Fatalf("empty pending memory should be cleared, store=%#v", store)
	}
	if snapshot.PendingMemory || len(snapshot.PendingMemoryJobs) != 0 {
		t.Fatalf("snapshot pending should be cleared: %#v", snapshot)
	}
}

func TestProcessSessionSummariesUsesFrozenPendingJobWindow(t *testing.T) {
	var captured map[string]any
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"短期承接：已整理冻结窗口。\n农业重点事件：继续核对番茄。"}}],"usage":{"prompt_tokens":120,"completion_tokens":30}}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	store := &summaryFakeStore{writeOK: true}
	service := &SummaryService{
		store:   store,
		prompts: summaryTestPromptLoader(t),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(os.Stderr, nil)),
	}
	snapshot := &SessionSnapshot{
		UserID:            "u1",
		PendingMemory:     true,
		MemoryDocument:    "短期承接：旧短期",
		RoundTotal:        7,
		UpdatedAt:         1700000000000,
		SessionGeneration: 1,
		PendingMemoryJobs: []MemoryExtractionJob{
			{
				RoundTotal: 6,
				Rounds: []SessionRound{
					{User: "冻结第六轮番茄问题", Assistant: "冻结第六轮回答"},
				},
			},
		},
		ARoundsFull: []SessionRound{
			{User: "第七轮新问题不应混入旧窗口", Assistant: "第七轮回答"},
		},
	}

	service.ProcessSessionSummaries("u1", snapshot)

	if store.writeCalls != 1 {
		t.Fatalf("expected memory write, got %d", store.writeCalls)
	}
	if snapshot.PendingMemory || len(snapshot.PendingMemoryJobs) != 0 {
		t.Fatalf("successful extraction should pop the frozen job: %#v", snapshot.PendingMemoryJobs)
	}
	messages, ok := captured["messages"].([]any)
	if !ok || len(messages) != 2 {
		t.Fatalf("messages mismatch: %#v", captured["messages"])
	}
	userMessage, _ := messages[1].(map[string]any)
	userContent, _ := userMessage["content"].(string)
	if !strings.Contains(userContent, "冻结第六轮番茄问题") {
		t.Fatalf("summary input should include frozen job window: %q", userContent)
	}
	if strings.Contains(userContent, "第七轮新问题") {
		t.Fatalf("summary input should not slide into later A window: %q", userContent)
	}
}

func TestProcessSessionSummariesRemovesSelectedPendingJobWhenQueueStartsWithEmptyJob(t *testing.T) {
	var captured map[string]any
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if err := json.NewDecoder(r.Body).Decode(&captured); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"短期承接：已整理有效冻结窗口。\n农业重点事件：有效冻结窗口。"}}]}`))
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	store := &summaryFakeStore{writeOK: true}
	service := &SummaryService{
		store:   store,
		prompts: summaryTestPromptLoader(t),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(os.Stderr, nil)),
	}
	snapshot := &SessionSnapshot{
		UserID:            "u1",
		PendingMemory:     true,
		MemoryDocument:    "短期承接：旧短期",
		RoundTotal:        7,
		UpdatedAt:         1700000000000,
		SessionGeneration: 1,
		PendingMemoryJobs: []MemoryExtractionJob{
			{RoundTotal: 6, Rounds: nil},
			{
				RoundTotal: 7,
				Rounds: []SessionRound{
					{User: "有效冻结窗口问题", Assistant: "有效冻结窗口回答"},
				},
			},
		},
	}

	service.ProcessSessionSummaries("u1", snapshot)

	if store.writeCalls != 1 {
		t.Fatalf("expected one memory write, got %d", store.writeCalls)
	}
	if len(snapshot.PendingMemoryJobs) != 1 || snapshot.PendingMemoryJobs[0].RoundTotal != 6 {
		t.Fatalf("successful extraction should remove selected non-empty job, got %#v", snapshot.PendingMemoryJobs)
	}
	messages, ok := captured["messages"].([]any)
	if !ok || len(messages) != 2 {
		t.Fatalf("messages mismatch: %#v", captured["messages"])
	}
	userMessage, _ := messages[1].(map[string]any)
	userContent, _ := userMessage["content"].(string)
	if !strings.Contains(userContent, "有效冻结窗口问题") {
		t.Fatalf("summary input should use the selected non-empty job: %q", userContent)
	}
}

func TestProcessSessionSummariesCatchesUpMultiplePendingJobsWithFreshSnapshot(t *testing.T) {
	requests := 0
	modelServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests++
		var payload map[string]any
		if err := json.NewDecoder(r.Body).Decode(&payload); err != nil {
			t.Fatalf("decode request: %v", err)
		}
		messages, ok := payload["messages"].([]any)
		if !ok || len(messages) != 2 {
			t.Fatalf("messages mismatch: %#v", payload["messages"])
		}
		userMessage, _ := messages[1].(map[string]any)
		userContent, _ := userMessage["content"].(string)
		switch requests {
		case 1:
			if !strings.Contains(userContent, "第六轮") || strings.Contains(userContent, "第十二轮") {
				t.Fatalf("first extraction should use first frozen job only: %q", userContent)
			}
			_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"短期承接：已整理第六轮。\n农业重点事件：第六轮。"}}]}`))
		case 2:
			if !strings.Contains(userContent, "已整理第六轮") || !strings.Contains(userContent, "第十二轮") {
				t.Fatalf("second extraction should use fresh memory and second job: %q", userContent)
			}
			_, _ = w.Write([]byte(`{"choices":[{"message":{"content":"短期承接：已整理第六轮和第十二轮。\n农业重点事件：第十二轮。"}}]}`))
		default:
			t.Fatalf("unexpected extra extraction request %d", requests)
		}
	}))
	defer modelServer.Close()

	t.Setenv("DASHSCOPE_API_KEY", "test-key")
	t.Setenv("BAILIAN_BASE_URL", modelServer.URL)

	snapshot := &SessionSnapshot{
		UserID:            "u1",
		PendingMemory:     true,
		MemoryDocument:    "短期承接：旧短期",
		RoundTotal:        12,
		UpdatedAt:         1700000000000,
		SessionGeneration: 2,
		PendingMemoryJobs: []MemoryExtractionJob{
			{
				RoundTotal: 6,
				Rounds: []SessionRound{
					{User: "第六轮问题", Assistant: "第六轮回答"},
				},
			},
			{
				RoundTotal: 12,
				Rounds: []SessionRound{
					{User: "第十二轮问题", Assistant: "第十二轮回答"},
				},
			},
		},
	}
	store := &summaryFakeStore{
		writeOK:       true,
		snapshot:      cloneSummaryTestSnapshot(snapshot),
		nextUpdatedAt: 1700000000100,
	}
	service := &SummaryService{
		store:   store,
		prompts: summaryTestPromptLoader(t),
		bailian: NewBailianClient(),
		logger:  slog.New(slog.NewTextHandler(os.Stderr, nil)),
	}

	service.ProcessSessionSummaries("u1", snapshot)

	if requests != 2 || store.writeCalls != 2 {
		t.Fatalf("expected two catch-up writes, requests=%d writes=%d", requests, store.writeCalls)
	}
	if store.snapshot.PendingMemory || len(store.snapshot.PendingMemoryJobs) != 0 {
		t.Fatalf("all pending jobs should be popped: %#v", store.snapshot.PendingMemoryJobs)
	}
	if snapshot.PendingMemory != true || len(snapshot.PendingMemoryJobs) != 1 {
		t.Fatalf("original stale snapshot should only reflect the first local pop: %#v", snapshot.PendingMemoryJobs)
	}
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

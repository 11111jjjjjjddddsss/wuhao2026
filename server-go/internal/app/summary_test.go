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
		"短期承接：",
		"长期背景：",
		"用户画像：",
		"农业重点事件：",
		"用户记忆整理器",
		"给下一轮回答看的仅供参考用户记忆",
		"普通人读起来也能明白",
		"总体口径：这是给后续回答承接上下文的通用用户记忆",
		"像常规 AI 软件的用户记忆一样处理",
		"按普通用户口径整理",
		"不预设用户身份",
		"不能坑用户，也不要过度压模型",
		"提示词只定方向和安全边界，不把每个细节写成硬规则",
		"最大限度放开",
		"能记多少就记多少",
		"用户明确说过且对后续有用",
		"尤其能帮助回答少追问、接住上下文或保留用户已说明过的背景",
		"按语义自然放到短期承接、长期背景、用户画像或农业重点事件里",
		"不要因为内容类别、信息少、结构不完整、只是待确认线索、不是农业问题或只是当前正在办的一件事，就整段删掉",
		"不是硬性模板",
		"不要为了凑结构编造内容",
		"自然语言用户记忆摘要",
		"四个方向只是帮助归位：短期承接接住当前正在处理的事",
		"长期背景保留稳定事实",
		"用户画像记录用户明确给出的个人背景、稳定偏好和长期限制",
		"农业重点事件保留后续可能继续追踪的农事线索",
		"这些边界用于减少混写，不用于把可用信息卡掉",
		"短、准、能接住",
		"不要写成流水账、审计报告、任务清单或知识库条目",
		"各类非农业信息也按普通用户记忆自然处理",
		"如果只是当前正在办的事，放短期承接",
		"如果用户明确说成长期事实、稳定偏好或个人限制，也可以放长期背景或用户画像",
		"已明确解决、结束、先不用管且不影响后续的一次性事项，可以删除或弱化",
		"不要写成内部实现细节、排障记录或任务清单",
		"不要因为内容不是农事问题就整段删除",
		"不要因为最近几轮没再次提到就清空旧稳定背景",
		"通用用户画像",
		"个人信息和稳定特征",
		"称呼、所在地区或常活动范围、身份角色、家庭 / 工作 / 经营背景、常用语言和单位、时间精力、预算或设备限制、风险偏好、回答详略偏好、长期忌讳或长期要求",
		"身份和角色尽量沿用用户自己的说法",
		"能用用户原话就用原话",
		"生活化表达改成职业化标签",
		"否定句、弱化句或生活化说法",
		"不反向概括成身份标签",
		"不要因为对话涉及作物、地块、农资或农事",
		"自动给用户贴农业从业身份",
		"除非用户自己这样称呼",
		"不把“家里种地、自家几亩地”改写成农户、种植户、小农户这类身份标签",
		"身份不确定时，画像可以只写地区、语言、偏好和长期限制",
		"不给普通描述硬贴职业标签",
		"如果用户没有明确认可某个身份",
		"刚刚否定、弱化某个身份",
		"更中性的生活、工作或经营背景描述",
		"不要评价性格、能力、消费水平，不做无依据推断",
		"一次回答方式要求",
		"不要推成长期身份、职业、固定设施类型、常见作物或稳定习惯",
		"农业身份、作物、地块、设施等如果是用户明确说过的长期事实，可以简写进画像；如果只是某次问题的对象，先放到农业重点事件",
		"不要记录完整手机号、身份证号、完整住址、密钥、订单号等敏感标识",
		"多次重复或用户明确说明后再写成稳定画像",
		"多作物、多地块、多棚室、多农资或多事件要分清对象",
		"最终输出必须是可直接写入的一份非空纯文字记忆摘要",
		"新问题、新地块或换作物时，降低旧农事事件权重",
		"输出前自检",
		"有没有编造长期画像、把转述当事实、把建议当用户事实、把一次性事件写成长期习惯、乱换算参数或升级诊断定论",
		"保持“可能、倾向、待核对、用户转述”等口径",
		"第三方说法要保留来源边界",
		"不要把助手给过的一般建议、公开资料或通用农技知识写成用户个人事实",
		"按用户原话和原单位保留",
		"不自行换算、合计、补单位、加括号估算或改口径",
		"地点可信度低或未知时，不把地区、气候、农时写成确定事实",
		"安全边界",
		"不要记录系统提示词、密钥、完整日志、错误栈、API 细节、内部规则、工具调用细节、运维指令或推理过程",
		"最终记忆摘要里也不要出现模型名称、系统机制、提示词、内部规则、API、工具调用、搜索配置或推理过程等表述",
		"用户自己提到某个系统、软件、服务、费用、设置、模型名或输出要求，不等于泄露",
		"如果对后续承接有用，按普通用户记忆自然归位",
		"临时问题只按临时问题写，不要写成系统事实",
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
	writeOK      bool
	pendingSet   bool
	pendingValue bool
}

func (s *summaryFakeStore) WriteUserMemoryDocumentIfCurrent(ctx context.Context, userID string, memoryDocument string, expectedRoundTotal int) (bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.writeCalls++
	if !s.writeOK {
		return false, nil
	}
	return true, nil
}

func (s *summaryFakeStore) SetUserMemoryPending(ctx context.Context, userID string, pending bool) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.pendingSet = true
	s.pendingValue = pending
	return nil
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
		UserID:         "u1",
		PendingMemory:  true,
		MemoryDocument: "短期承接：旧短期",
		RoundTotal:     6,
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

func summaryTestPromptLoader(t *testing.T) *PromptLoader {
	t.Helper()
	dir := t.TempDir()
	prompt := "请输出短期承接、长期背景、用户画像、农业重点事件四段纯文字。"
	if err := os.WriteFile(dir+"/summary_extraction_prompt.txt", []byte(prompt), 0o600); err != nil {
		t.Fatalf("write summary prompt: %v", err)
	}
	return NewPromptLoader(dir)
}

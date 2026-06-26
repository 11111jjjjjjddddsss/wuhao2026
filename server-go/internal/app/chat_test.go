package app

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"net/http/httptest"
	"os"
	"regexp"
	"strings"
	"testing"
	"time"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestSessionRoundAppendRetryPolicy(t *testing.T) {
	if isRetryableSessionRoundAppendError(nil) {
		t.Fatalf("nil append error must not be retryable")
	}
	if isRetryableSessionRoundAppendError(ErrSessionRoundRequestConflict) {
		t.Fatalf("client message conflict must not be retried")
	}
	if isRetryableSessionRoundAppendError(ErrSessionRoundArchiveMissing) {
		t.Fatalf("archive-missing conflict must not be retried")
	}
	if isRetryableSessionRoundAppendError(errors.Join(context.DeadlineExceeded, ErrSessionRoundRequestConflict)) {
		t.Fatalf("wrapped conflict must not be retried")
	}
	if !isRetryableSessionRoundAppendError(context.DeadlineExceeded) {
		t.Fatalf("transient database/context append failure should be retried")
	}
}

func TestChatOutputConstraintText(t *testing.T) {
	for _, want := range []string{
		"【输出约束】",
		"禁止表格，不要用 Markdown 表格、竖线或横线画表；关键点少量加粗，排版适合手机阅读。",
		"Markdown 表格",
		"竖线或横线画表",
		"关键点少量加粗",
		"排版适合手机阅读",
		"禁止表格",
		"多用自然换行",
		"有多个要点、步骤、提醒或对比内容时",
		"对比内容",
		"优先使用编号列表或项目列表",
		"禁止英文",
		"默认只用中文",
		"除非是专业名词、商品名、登记标签或用户原文需要保留",
		"否则不要夹带英文",
	} {
		if !strings.Contains(chatOutputConstraint, want) {
			t.Fatalf("output constraint missing %q: %q", want, chatOutputConstraint)
		}
	}
	for _, blocked := range []string{
		"【诊断约束】",
		"候选判断要说明支持点",
		"反证和验证方法",
		"处理建议要说明适用前提",
		"唯一答案",
		"带图时先详细描述",
		"证据不足要明确",
		"单张图片或单轮描述写成确诊",
		"涉及病虫害、药害肥害、生理异常或农技判断时",
		"以本轮图片和客观信息为准",
		"时间、地点、天气、历史对话和记忆摘要只作风险背景",
		"不能盖过本轮证据",
		"结构以自然分段为主",
		"不限固定格式",
		"默认必须使用中文表达",
		"每段通常一到三句",
		"以短段落为主",
		"每段通常一到二句",
		"优先自然换行分开",
		"需要列要点时，用编号列表或项目列表",
		"优先用自然换行分开",
		"不要把多个要点挤进同一长段",
		"不要刻意套固定模板",
		"根据本轮问题自然组织",
		"避免呆板机械",
		"不要中英混写",
		"普通英文词和英文短语要改成中文说法",
		"确需保留的专业名词、商品名、登记标签或用户原文除外",
		"不得直接夹在中文句子里",
		"农药/肥料成分、品种名、病原拉丁学名",
		"才使用英文，并尽量先给中文解释",
		"排版尽量简洁",
		"多用短段落和适当空行",
		"不要使用多级列表和项目符号列表",
	} {
		if strings.Contains(chatOutputConstraint, blocked) {
			t.Fatalf("output constraint should not include template phrase %q: %q", blocked, chatOutputConstraint)
		}
	}
	if !strings.HasPrefix(chatOutputConstraint, "【输出约束】\n禁止表格，") {
		t.Fatalf("output constraint should put table ban first: %q", chatOutputConstraint)
	}
}

func TestResolveChatThinkingOptionsDefaultsToAllTurns(t *testing.T) {
	t.Setenv("CHAT_THINKING_MODE", "")

	if got := resolveChatThinkingOptions("普通文字问题", nil); !got.EnableThinking || got.ThinkingBudget != defaultChatThinkingBudget {
		t.Fatalf("text-only thinking options = %#v, want enabled with default budget", got)
	}

	got := resolveChatThinkingOptions("这张叶片咋了", []string{"https://img/current.jpg"})
	if !got.EnableThinking || got.ThinkingBudget != defaultChatThinkingBudget {
		t.Fatalf("image thinking options = %#v, want enabled with default budget", got)
	}
}

func TestResolveChatThinkingOptionsEnablesHistoricalImageContext(t *testing.T) {
	t.Setenv("CHAT_THINKING_MODE", "")

	got := resolveChatThinkingOptionsForImageContext(true)
	if !got.EnableThinking || got.ThinkingBudget != defaultChatThinkingBudget {
		t.Fatalf("historical image context thinking options = %#v, want enabled with default budget", got)
	}

	t.Setenv("CHAT_THINKING_MODE", "off")
	if got := resolveChatThinkingOptionsForImageContext(true); got.EnableThinking {
		t.Fatalf("off mode should disable thinking even for historical image context: %#v", got)
	}
}

func TestResolveChatThinkingOptionsCanLimitToImageContext(t *testing.T) {
	t.Setenv("CHAT_THINKING_MODE", "image")

	got := resolveChatThinkingOptions("普通文字问题", nil)
	if got.EnableThinking || got.ThinkingBudget != 0 {
		t.Fatalf("image mode should not enable text-only thinking: %#v", got)
	}

	got = resolveChatThinkingOptions("这张叶片咋了", []string{"https://img/current.jpg"})
	if !got.EnableThinking || got.ThinkingBudget != defaultChatThinkingBudget {
		t.Fatalf("image thinking options = %#v, want enabled with fixed budget", got)
	}
}

func TestResolveChatThinkingOptionsEnablesTextOnlyWhenConfiguredOn(t *testing.T) {
	t.Setenv("CHAT_THINKING_MODE", "on")

	got := resolveChatThinkingOptions("葡萄叶片这是什么病，怎么治", nil)
	if !got.EnableThinking || got.ThinkingBudget != defaultChatThinkingBudget {
		t.Fatalf("on mode should enable text-only thinking: %#v", got)
	}

	got = resolveChatThinkingOptions("这张叶片咋了", []string{"https://img/current.jpg"})
	if !got.EnableThinking || got.ThinkingBudget != defaultChatThinkingBudget {
		t.Fatalf("image thinking options = %#v, want enabled with fixed budget", got)
	}
}

func TestResolveChatThinkingOptionsCanDisableAndKeepsFixedBudget(t *testing.T) {
	t.Setenv("CHAT_THINKING_MODE", "off")

	if got := resolveChatThinkingOptions("这张图是什么病", []string{"https://img/current.jpg"}); got.EnableThinking {
		t.Fatalf("off mode should disable thinking even for images: %#v", got)
	}

	t.Setenv("CHAT_THINKING_MODE", "enabled")

	if got := resolveChatThinkingOptions("普通文字问题", nil); !got.EnableThinking || got.ThinkingBudget != defaultChatThinkingBudget {
		t.Fatalf("enabled mode should enable fixed-budget text-only thinking: %#v", got)
	}

	got := resolveChatThinkingOptions("带图问题", []string{"https://img/current.jpg"})
	if !got.EnableThinking || got.ThinkingBudget != defaultChatThinkingBudget {
		t.Fatalf("image thinking options = %#v, want fixed budget", got)
	}
}

func TestShouldForceSearchForExplicitSearchIntent(t *testing.T) {
	for _, text := range []string{
		"你全网搜索一下，网上有没有销售？",
		"这个肥料多少钱",
		"查一下最新价格和购买渠道",
		"河南小麦行情怎么样",
		"这个产品最新报价是多少",
		"帮我查一下这个牌子",
		"查查这个产品有没有渠道",
		"查价这个肥料",
		"淘宝上有没有同款",
		"查一下这张叶片是什么病",
		"查一查这瓶药网上有卖吗",
		"查查这个葡萄叶子咋回事",
		"帮我查这个芒果咋了",
	} {
		if !shouldForceSearchForChatText(text) {
			t.Fatalf("expected force search for %q", text)
		}
	}

	for _, text := range []string{
		"这张叶片是什么病",
		"帮我看看怎么用肥",
		"亩产多少算正常",
		"这个药多少天见效",
		"葡萄叶子发黄怎么办",
		"最新这片地苗情咋样",
	} {
		if shouldForceSearchForChatText(text) {
			t.Fatalf("did not expect force search for %q", text)
		}
	}
}

func TestResolveChatStreamWatchdogTimeouts(t *testing.T) {
	t.Setenv("CHAT_STREAM_MAX_DURATION_SECONDS", "5")
	t.Setenv("CHAT_STREAM_FIRST_VISIBLE_TIMEOUT_SECONDS", "10")
	t.Setenv("CHAT_STREAM_IDLE_TIMEOUT_SECONDS", "8")

	if got := resolveChatStreamFirstVisibleTimeout(); got != 5*time.Second {
		t.Fatalf("first visible timeout should clamp to max duration, got %s", got)
	}
	if got := resolveChatStreamIdleTimeout(); got != 5*time.Second {
		t.Fatalf("idle timeout should clamp to max duration, got %s", got)
	}

	t.Setenv("CHAT_STREAM_MAX_DURATION_SECONDS", "1800")
	t.Setenv("CHAT_STREAM_FIRST_VISIBLE_TIMEOUT_SECONDS", "0")
	t.Setenv("CHAT_STREAM_IDLE_TIMEOUT_SECONDS", "0")
	if got := resolveChatStreamFirstVisibleTimeout(); got != chatStreamFirstVisibleTimeout {
		t.Fatalf("non-positive first visible timeout should use default, got %s", got)
	}
	if got := resolveChatStreamIdleTimeout(); got != chatStreamIdleTimeout {
		t.Fatalf("non-positive idle timeout should use default, got %s", got)
	}
}

func TestPromptIncludesImageContextUsesCurrentOrPreviousRoundImage(t *testing.T) {
	if !promptIncludesImageContext(nil, 6, []string{"https://img/current.jpg"}) {
		t.Fatalf("current images should mark prompt as image context")
	}
	snapshot := &SessionSnapshot{
		ARoundsFull: []SessionRound{
			{User: "old", UserImages: []string{"https://img/old.jpg"}},
		},
	}
	if !promptIncludesImageContext(snapshot, 6, nil) {
		t.Fatalf("previous round image should mark prompt as image context")
	}
	if promptIncludesImageContext(snapshot, 0, nil) {
		t.Fatalf("previous image outside A window should not mark prompt as image context")
	}
	snapshot.ARoundsFull = append(snapshot.ARoundsFull, SessionRound{User: "text follow-up"})
	if promptIncludesImageContext(snapshot, 6, nil) {
		t.Fatalf("only the previous prompt image is preserved, older image should not mark current prompt")
	}
}

func TestSanitizeUpstreamErrorPreviewRedactsSensitivePieces(t *testing.T) {
	raw := `{"message":"bad image https://api.nongjiqiancha.cn/uploads/support/private-name.jpg","auth":"Bearer sk-abc.DEF_123"}`

	got := sanitizeUpstreamErrorPreview(raw)
	if strings.Contains(got, "private-name") || strings.Contains(got, "sk-abc") {
		t.Fatalf("preview leaked sensitive pieces: %q", got)
	}
	if !strings.Contains(got, "/uploads/REDACTED.jpg") || !strings.Contains(got, "Bearer REDACTED") {
		t.Fatalf("preview did not include redacted markers: %q", got)
	}
}

func TestFilterBailianStreamDataForClientDropsReasoningOnlyChunks(t *testing.T) {
	raw := `{"choices":[{"delta":{"reasoning_content":"内部推理","content":""},"finish_reason":null,"index":0}],"usage":null}`

	filtered, ok := filterBailianStreamDataForClient(raw)
	if ok || filtered != "" {
		t.Fatalf("reasoning-only chunk should be dropped, got ok=%v data=%q", ok, filtered)
	}
}

func TestFilterBailianStreamDataForClientRedactsReasoningWhenContentExists(t *testing.T) {
	raw := `{"choices":[{"delta":{"reasoning_content":"内部推理","content":"可见正文"},"finish_reason":null,"index":0}],"usage":null}`

	filtered, ok := filterBailianStreamDataForClient(raw)
	if !ok {
		t.Fatalf("content chunk should be forwarded")
	}
	if strings.Contains(filtered, "内部推理") || strings.Contains(filtered, "reasoning_content") {
		t.Fatalf("filtered chunk leaked reasoning content: %q", filtered)
	}
	if !strings.Contains(filtered, "可见正文") {
		t.Fatalf("filtered chunk dropped visible content: %q", filtered)
	}
}

func TestFilterBailianStreamDataForClientDropsInvalidData(t *testing.T) {
	filtered, ok := filterBailianStreamDataForClient("not-json")
	if ok || filtered != "" {
		t.Fatalf("invalid upstream data should be dropped, got ok=%v data=%q", ok, filtered)
	}
}

func TestBuildPromptMessagesOnlyKeepsImagesForPreviousRoundAndCurrentRound(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}
	recent := time.Now().Add(-1 * time.Hour).UnixMilli()

	snapshot := &SessionSnapshot{
		UserID: "u1",
		ARoundsFull: []SessionRound{
			{
				ClientMsgID: "r1",
				User:        "old-1",
				UserImages:  []string{"https://img/old-1.jpg"},
				Assistant:   "a1",
			},
			{
				ClientMsgID: "r2",
				User:        "old-2",
				UserImages:  []string{"https://img/old-2.jpg"},
				Assistant:   "a2",
				CreatedAt:   recent,
			},
		},
	}

	messages, usedCount, hasMemoryDocument := server.buildPromptMessages(
		snapshot,
		6,
		"current",
		[]string{"https://img/current.jpg"},
		"context",
		"",
	)

	if usedCount != 2 {
		t.Fatalf("expected 2 historical rounds, got %d", usedCount)
	}
	if hasMemoryDocument {
		t.Fatalf("expected no memory document, got hasMemoryDocument=%v", hasMemoryDocument)
	}
	if len(messages) != 8 {
		t.Fatalf("expected 8 messages, got %d", len(messages))
	}

	firstHistoricalUser := messages[2]
	if firstHistoricalUser.Role != "user" {
		t.Fatalf("expected first historical message to be user, got %q", firstHistoricalUser.Role)
	}
	if _, ok := firstHistoricalUser.Content.(string); !ok {
		t.Fatalf("expected older historical round to be text-only")
	}

	secondHistoricalUser := messages[4]
	content, ok := secondHistoricalUser.Content.([]map[string]any)
	if !ok {
		t.Fatalf("expected previous round to keep images in context")
	}
	if len(content) != 2 {
		t.Fatalf("expected previous round vision content length 2, got %d", len(content))
	}
	if got := content[1]["image_url"].(map[string]any)["url"]; got != "https://img/old-2.jpg" {
		t.Fatalf("expected previous round image preserved, got %#v", got)
	}

	if messages[6].Role != "system" || messages[6].Content != chatOutputConstraint {
		t.Fatalf("expected output constraint immediately before current user input, got %#v", messages[6])
	}
	currentUser := messages[7]
	currentContent, ok := currentUser.Content.([]map[string]any)
	if !ok || len(currentContent) != 2 {
		t.Fatalf("expected current round to keep images in context, got %#v", currentUser.Content)
	}
	if got := currentContent[1]["image_url"].(map[string]any)["url"]; got != "https://img/current.jpg" {
		t.Fatalf("expected current image preserved, got %#v", got)
	}
}

func TestBuildPromptMessagesDropsExpiredPreviousRoundImages(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}
	expired := time.Now().Add(-chatHistoricalImageContextTTL - time.Hour).UnixMilli()

	snapshot := &SessionSnapshot{
		UserID: "u1",
		ARoundsFull: []SessionRound{
			{
				ClientMsgID: "r1",
				User:        "old image",
				UserImages:  []string{"https://img/expired.jpg"},
				Assistant:   "a1",
				CreatedAt:   expired,
			},
		},
	}

	messages, usedCount, _ := server.buildPromptMessages(
		snapshot,
		6,
		"current",
		nil,
		"context",
		"",
	)

	if usedCount != 1 {
		t.Fatalf("expected 1 historical round, got %d", usedCount)
	}
	historicalUser := messages[2]
	if historicalUser.Role != "user" {
		t.Fatalf("expected historical message to be user, got %q", historicalUser.Role)
	}
	if _, ok := historicalUser.Content.(string); !ok {
		t.Fatalf("expected expired previous image to be dropped to text-only, got %#v", historicalUser.Content)
	}
	payload, _ := json.Marshal(historicalUser.Content)
	if strings.Contains(string(payload), "expired.jpg") {
		t.Fatalf("expired image URL leaked into prompt content: %#v", historicalUser.Content)
	}
}

func TestResolveTodayAgriChatContextRequiresSavedUserItem(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	server := &Server{store: store}
	userID := "acct_today_context"
	dayCN := "20260618"

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT day_cn, anchor_client_msg_id, content_json, created_at, updated_at
		 FROM today_agri_user_items
		 WHERE user_id = ? AND day_cn = ?
		 ORDER BY updated_at DESC, day_cn DESC
		 LIMIT ?`)).
		WithArgs(userID, dayCN, 1).
		WillReturnRows(sqlmock.NewRows([]string{"day_cn", "anchor_client_msg_id", "content_json", "created_at", "updated_at"}))

	if got := server.resolveTodayAgriChatContext(context.Background(), userID, dayCN, dayCN); got != "" {
		t.Fatalf("context without saved item = %q, want empty", got)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestResolveTodayAgriChatContextUsesSavedDisplayCopy(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	server := &Server{store: store}
	userID := "acct_today_context"
	dayCN := "20260618"
	card := testDailyAgriCard(dayCN)
	card.Items[0].Summary = "保存后的展示摘要。"
	raw, err := json.Marshal(card)
	if err != nil {
		t.Fatalf("marshal card: %v", err)
	}

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT day_cn, anchor_client_msg_id, content_json, created_at, updated_at
		 FROM today_agri_user_items
		 WHERE user_id = ? AND day_cn = ?
		 ORDER BY updated_at DESC, day_cn DESC
		 LIMIT ?`)).
		WithArgs(userID, dayCN, 1).
		WillReturnRows(
			sqlmock.NewRows([]string{"day_cn", "anchor_client_msg_id", "content_json", "created_at", "updated_at"}).
				AddRow(dayCN, "assistant_anchor_1", string(raw), int64(1700000000000), int64(1700000001000)),
		)
	mock.ExpectQuery(regexp.QuoteMeta("SELECT id, created_at FROM session_round_archive WHERE user_id = ? AND client_msg_id = ? LIMIT 1")).
		WithArgs(userID, "anchor_1").
		WillReturnRows(sqlmock.NewRows([]string{"id", "created_at"}).AddRow(int64(11), int64(1700000000000)))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT COUNT(*)
		 FROM session_round_archive
		 WHERE user_id = ?
		   AND (created_at > ? OR (created_at = ? AND id > ?))`)).
		WithArgs(userID, int64(1700000000000), int64(1700000000000), int64(11)).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(int64(1)))

	got := server.resolveTodayAgriChatContext(context.Background(), userID, dayCN, dayCN)
	if !strings.Contains(got, "今日农情界面上下文") {
		t.Fatalf("context missing heading: %q", got)
	}
	if !strings.Contains(got, "保存后的展示摘要。") {
		t.Fatalf("context did not use saved display copy: %q", got)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestResolveTodayAgriChatContextStopsAfterTwoArchivedRounds(t *testing.T) {
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()

	server := &Server{store: store}
	userID := "acct_today_context"
	dayCN := "20260618"
	raw, err := json.Marshal(testDailyAgriCard(dayCN))
	if err != nil {
		t.Fatalf("marshal card: %v", err)
	}

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT day_cn, anchor_client_msg_id, content_json, created_at, updated_at
		 FROM today_agri_user_items
		 WHERE user_id = ? AND day_cn = ?
		 ORDER BY updated_at DESC, day_cn DESC
		 LIMIT ?`)).
		WithArgs(userID, dayCN, 1).
		WillReturnRows(
			sqlmock.NewRows([]string{"day_cn", "anchor_client_msg_id", "content_json", "created_at", "updated_at"}).
				AddRow(dayCN, "assistant_anchor_1", string(raw), int64(1700000000000), int64(1700000001000)),
		)
	mock.ExpectQuery(regexp.QuoteMeta("SELECT id, created_at FROM session_round_archive WHERE user_id = ? AND client_msg_id = ? LIMIT 1")).
		WithArgs(userID, "anchor_1").
		WillReturnRows(sqlmock.NewRows([]string{"id", "created_at"}).AddRow(int64(11), int64(1700000000000)))
	mock.ExpectQuery(regexp.QuoteMeta(`SELECT COUNT(*)
		 FROM session_round_archive
		 WHERE user_id = ?
		   AND (created_at > ? OR (created_at = ? AND id > ?))`)).
		WithArgs(userID, int64(1700000000000), int64(1700000000000), int64(11)).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(int64(2)))

	if got := server.resolveTodayAgriChatContext(context.Background(), userID, dayCN, dayCN); got != "" {
		t.Fatalf("context after two rounds = %q, want empty", got)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestReadLimitedSSELineRejectsOversizedLine(t *testing.T) {
	reader := bufio.NewReader(strings.NewReader("data: " + strings.Repeat("x", 12) + "\n"))
	line, err := readLimitedSSELine(reader, 8)
	if err != errSSELineTooLarge {
		t.Fatalf("err = %v, want errSSELineTooLarge", err)
	}
	if line != "" {
		t.Fatalf("oversized line should not be returned, got %q", line)
	}
}

func TestWriteSSEHeadersDisableNginxBuffering(t *testing.T) {
	recorder := httptest.NewRecorder()
	server := &Server{}

	server.writeSSEHeaders(recorder)

	if got := recorder.Header().Get("Content-Type"); got != "text/event-stream; charset=utf-8" {
		t.Fatalf("Content-Type = %q", got)
	}
	if got := recorder.Header().Get("X-Accel-Buffering"); got != "no" {
		t.Fatalf("X-Accel-Buffering = %q, want no", got)
	}
}

func TestChatStreamDoesNotGateOnQuotaConsumeOutbox(t *testing.T) {
	source, err := os.ReadFile("chat.go")
	if err != nil {
		t.Fatalf("read chat.go: %v", err)
	}
	text := string(source)
	for _, forbidden := range []string{
		"QUOTA_SETTLEMENT_PENDING",
		"CountPendingQuotaConsumeOutboxForUser",
		"上次回答正在结算",
	} {
		if strings.Contains(text, forbidden) {
			t.Fatalf("chat stream must not block users on quota consume outbox marker %q", forbidden)
		}
	}
}

func TestBuildPromptMessagesAddsMemoryDocumentWhenPresent(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}

	snapshot := &SessionSnapshot{
		UserID:         "u1",
		MemoryDocument: "短期承接：b-summary\n长期背景：c-summary",
		ARoundsFull:    []SessionRound{},
	}

	messages, usedCount, hasMemoryDocument := server.buildPromptMessages(
		snapshot,
		6,
		"hello",
		nil,
		"context",
		"",
	)

	if usedCount != 0 {
		t.Fatalf("expected no historical rounds, got %d", usedCount)
	}
	if !hasMemoryDocument {
		t.Fatalf("expected memory document present")
	}
	if len(messages) != 5 {
		t.Fatalf("expected 5 messages, got %d", len(messages))
	}
	if messages[2].Role != "system" {
		t.Fatalf("expected memory document to be inserted as system message")
	}
	if !strings.HasPrefix(messages[2].Content.(string), "后台背景信息中的记忆摘要（仅供参考；回答应聚焦用户本轮问题。") {
		t.Fatalf("expected memory document label, got %#v", messages[2].Content)
	}
	for _, forbidden := range []string{"后台参考", "后台摘要", "B层", "C层", "内部机制"} {
		if strings.Contains(messages[2].Content.(string), forbidden) {
			t.Fatalf("memory document prompt should not expose legacy label %q: %#v", forbidden, messages[2].Content)
		}
	}
	if messages[3].Role != "system" || messages[3].Content != chatOutputConstraint {
		t.Fatalf("expected output constraint before current user input, got %#v", messages[3])
	}
	if messages[4].Content != "hello" {
		t.Fatalf("expected current text-only user message, got %#v", messages[4].Content)
	}
}

func TestBuildPromptMessagesDoesNotDuplicatePendingMemoryJobStillInWindow(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}
	rounds := []SessionRound{
		{ClientMsgID: "r1", User: "第1轮", Assistant: "答1"},
		{ClientMsgID: "r2", User: "第2轮", Assistant: "答2"},
	}
	snapshot := &SessionSnapshot{
		UserID:      "u1",
		ARoundsFull: rounds,
		PendingMemoryJobs: []MemoryExtractionJob{
			{RoundTotal: 2, Rounds: rounds},
		},
	}

	messages, usedCount, hasMemoryDocument := server.buildPromptMessages(
		snapshot,
		6,
		"继续",
		nil,
		"context",
		"",
	)

	if usedCount != 2 || hasMemoryDocument {
		t.Fatalf("usedCount=%d hasMemoryDocument=%v", usedCount, hasMemoryDocument)
	}
	for _, message := range messages {
		if message.Role == "system" && strings.Contains(message.Content.(string), "待补偿历史片段") {
			t.Fatalf("pending memory job still inside active window should not add extra system context: %#v", message.Content)
		}
	}
}

func TestBuildPromptMessagesAddsPendingMemoryJobAfterItSlidesOutOfWindow(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}
	pendingRounds := []SessionRound{
		{ClientMsgID: "r1", User: "第1轮", Assistant: "答1", CreatedAt: 1800000000000},
		{ClientMsgID: "r2", User: "第2轮关键病害描述", Assistant: "答2"},
	}
	activeRounds := []SessionRound{
		{ClientMsgID: "r3", User: "第3轮", Assistant: "答3"},
		{ClientMsgID: "r4", User: "第4轮", Assistant: "答4"},
	}
	snapshot := &SessionSnapshot{
		UserID:      "u1",
		ARoundsFull: activeRounds,
		PendingMemoryJobs: []MemoryExtractionJob{
			{RoundTotal: 2, Rounds: pendingRounds},
		},
	}

	messages, usedCount, hasMemoryDocument := server.buildPromptMessages(
		snapshot,
		2,
		"继续",
		nil,
		"context",
		"",
	)

	if usedCount != 2 || hasMemoryDocument {
		t.Fatalf("usedCount=%d hasMemoryDocument=%v", usedCount, hasMemoryDocument)
	}
	if len(messages) < 4 || messages[2].Role != "system" {
		t.Fatalf("expected pending memory context before active rounds, got %#v", messages)
	}
	content := messages[2].Content.(string)
	if !strings.HasPrefix(content, "后台背景信息中的待补偿历史片段（仅供参考；回答仍聚焦用户本轮问题。") ||
		!strings.Contains(content, "第2轮关键病害描述") {
		t.Fatalf("pending memory context mismatch: %q", content)
	}
}

func TestBuildPromptMessagesOnlyAddsPendingMemoryRoundsMissingFromActiveWindow(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}
	pendingRounds := []SessionRound{
		{ClientMsgID: "r1", User: "第1轮已滑出", Assistant: "答1"},
		{ClientMsgID: "r2", User: "第2轮仍在滑窗", Assistant: "答2"},
		{ClientMsgID: "r3", User: "第3轮仍在滑窗", Assistant: "答3"},
	}
	activeRounds := []SessionRound{
		{ClientMsgID: "r2", User: "第2轮仍在滑窗", Assistant: "答2"},
		{ClientMsgID: "r3", User: "第3轮仍在滑窗", Assistant: "答3"},
		{ClientMsgID: "r4", User: "第4轮", Assistant: "答4"},
	}
	snapshot := &SessionSnapshot{
		UserID:      "u1",
		ARoundsFull: activeRounds,
		PendingMemoryJobs: []MemoryExtractionJob{
			{RoundTotal: 3, Rounds: pendingRounds},
		},
	}

	messages, usedCount, hasMemoryDocument := server.buildPromptMessages(
		snapshot,
		3,
		"继续",
		nil,
		"context",
		"",
	)

	if usedCount != 3 || hasMemoryDocument {
		t.Fatalf("usedCount=%d hasMemoryDocument=%v", usedCount, hasMemoryDocument)
	}
	if len(messages) < 4 || messages[2].Role != "system" {
		t.Fatalf("expected pending memory context before active rounds, got %#v", messages)
	}
	content := messages[2].Content.(string)
	if !strings.Contains(content, "第1轮已滑出") {
		t.Fatalf("missing slid-out pending round in fallback context: %q", content)
	}
	for _, duplicate := range []string{"第2轮仍在滑窗", "第3轮仍在滑窗"} {
		if strings.Contains(content, duplicate) {
			t.Fatalf("pending fallback should not duplicate active-window round %q: %q", duplicate, content)
		}
	}
}

func TestBuildPromptMessagesAddsAllPendingMemoryJobsAfterLongOutage(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}
	firstPendingRounds := []SessionRound{
		{ClientMsgID: "r1", User: "第一批第1轮", Assistant: "答1"},
		{ClientMsgID: "r2", User: "第一批第2轮", Assistant: "答2"},
	}
	secondPendingRounds := []SessionRound{
		{ClientMsgID: "r3", User: "第二批第3轮", Assistant: "答3"},
		{ClientMsgID: "r4", User: "第二批第4轮", Assistant: "答4"},
	}
	activeRounds := []SessionRound{
		{ClientMsgID: "r5", User: "当前滑窗第5轮", Assistant: "答5"},
		{ClientMsgID: "r6", User: "当前滑窗第6轮", Assistant: "答6"},
	}
	snapshot := &SessionSnapshot{
		UserID:      "u1",
		ARoundsFull: activeRounds,
		PendingMemoryJobs: []MemoryExtractionJob{
			{RoundTotal: 2, Rounds: firstPendingRounds},
			{RoundTotal: 4, Rounds: secondPendingRounds},
		},
	}

	messages, usedCount, hasMemoryDocument := server.buildPromptMessages(
		snapshot,
		2,
		"继续",
		nil,
		"context",
		"",
	)

	if usedCount != 2 || hasMemoryDocument {
		t.Fatalf("usedCount=%d hasMemoryDocument=%v", usedCount, hasMemoryDocument)
	}
	if len(messages) < 4 || messages[2].Role != "system" {
		t.Fatalf("expected pending memory context before active rounds, got %#v", messages)
	}
	content := messages[2].Content.(string)
	for _, want := range []string{"第一批第1轮", "第一批第2轮", "第二批第3轮", "第二批第4轮"} {
		if !strings.Contains(content, want) {
			t.Fatalf("missing queued pending round %q in fallback context: %q", want, content)
		}
	}
	if strings.Contains(content, "当前滑窗第5轮") || strings.Contains(content, "当前滑窗第6轮") {
		t.Fatalf("pending fallback should not duplicate active-window rounds: %q", content)
	}
}

func TestBuildPromptMessagesDeduplicatesOverlappingPendingMemoryJobs(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}
	snapshot := &SessionSnapshot{
		UserID:      "u1",
		ARoundsFull: []SessionRound{{ClientMsgID: "r4", User: "当前轮", Assistant: "答4"}},
		PendingMemoryJobs: []MemoryExtractionJob{
			{
				RoundTotal: 2,
				Rounds: []SessionRound{
					{ClientMsgID: "r1", User: "只应出现一次的第1轮", Assistant: "答1"},
					{ClientMsgID: "r2", User: "只应出现一次的第2轮", Assistant: "答2"},
				},
			},
			{
				RoundTotal: 3,
				Rounds: []SessionRound{
					{ClientMsgID: "r2", User: "只应出现一次的第2轮", Assistant: "答2"},
					{ClientMsgID: "r3", User: "只应出现一次的第3轮", Assistant: "答3"},
				},
			},
		},
	}

	messages, _, _ := server.buildPromptMessages(snapshot, 1, "继续", nil, "context", "")

	if len(messages) < 4 || messages[2].Role != "system" {
		t.Fatalf("expected pending memory context before active rounds, got %#v", messages)
	}
	content := messages[2].Content.(string)
	if got := strings.Count(content, "只应出现一次的第2轮"); got != 1 {
		t.Fatalf("overlapping pending rounds should be deduplicated once, got %d in %q", got, content)
	}
	for _, want := range []string{"只应出现一次的第1轮", "只应出现一次的第3轮"} {
		if !strings.Contains(content, want) {
			t.Fatalf("missing pending round %q in fallback context: %q", want, content)
		}
	}
	if strings.Contains(content, "当前轮") {
		t.Fatalf("pending fallback should not duplicate active-window round: %q", content)
	}
}

func TestBuildPromptMessagesAddsTodayAgriContextWhenProvided(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}
	todayAgriContext := "今日农情界面上下文（来自 App 主界面最近展示的当天资讯；不是用户地块、症状、诊断、长期记忆或账户信息。）\n今日农情 · 6月17日\n\n一、病虫监测"

	messages, usedCount, hasMemoryDocument := server.buildPromptMessages(
		&SessionSnapshot{UserID: "u1"},
		6,
		"刚才第二条什么意思",
		nil,
		"context",
		todayAgriContext,
	)

	if usedCount != 0 {
		t.Fatalf("expected no historical rounds, got %d", usedCount)
	}
	if hasMemoryDocument {
		t.Fatalf("expected no memory document")
	}
	if len(messages) != 5 {
		t.Fatalf("expected 5 messages, got %d", len(messages))
	}
	if messages[2].Role != "system" || messages[2].Content != todayAgriContext {
		t.Fatalf("expected today agri context before output constraint, got %#v", messages[2])
	}
	if messages[3].Role != "system" || messages[3].Content != chatOutputConstraint {
		t.Fatalf("expected output constraint as the last system message before user input, got %#v", messages[3])
	}
	if messages[4].Role != "user" || messages[4].Content != "刚才第二条什么意思" {
		t.Fatalf("expected current user message after output constraint, got %#v", messages[4])
	}
}

func TestFormatTodayAgriChatContextKeepsBoundaryAndReadableItems(t *testing.T) {
	context := formatTodayAgriChatContext(&DailyAgriCard{
		DateCN: "20260617",
		Items: []DailyAgriCardItem{
			{Title: "病虫监测", Summary: "多地提醒加强田间巡查。", Source: "全国农技中心", URL: "https://example.com/a"},
			{Title: "栽培管理", Summary: "雨后注意排水。", Source: "农业农村部"},
			{Title: "产地流通", Summary: "部分蔬菜产区供应恢复。"},
		},
	})

	for _, want := range []string{
		"今日农情界面上下文",
		"不是用户地块、症状、诊断、长期记忆或账户信息",
		"今日农情 · 6月17日",
		"一、病虫监测",
		"二、栽培管理",
		"三、产地流通",
		"来源：全国农技中心",
	} {
		if !strings.Contains(context, want) {
			t.Fatalf("expected context to contain %q, got %q", want, context)
		}
	}
	if strings.Contains(context, "https://example.com") {
		t.Fatalf("today agri chat context must not expose raw source URLs: %q", context)
	}
}

func TestNormalizeTodayAgriContextDay(t *testing.T) {
	cases := map[string]string{
		"20260617":   "20260617",
		"2026-06-17": "20260617",
		" 20260617 ": "20260617",
		"2026061":    "",
		"202606170":  "",
		"today":      "",
	}
	for input, want := range cases {
		if got := normalizeTodayAgriContextDay(input); got != want {
			t.Fatalf("normalizeTodayAgriContextDay(%q) = %q, want %q", input, got, want)
		}
	}
}

func TestChatStreamRequestHashIncludesTodayAgriContextDay(t *testing.T) {
	base := chatStreamRequestHash("hello", nil, "")
	withDashedDay := chatStreamRequestHash("hello", nil, "2026-06-17")
	withCompactDay := chatStreamRequestHash("hello", nil, "20260617")
	if base == withCompactDay {
		t.Fatalf("expected today agri context day to affect request hash")
	}
	if withDashedDay != withCompactDay {
		t.Fatalf("expected normalized today agri context day to keep stable hash")
	}
}

func TestBuildPromptMessagesIncludesHistoricalRoundTimeWhenAvailable(t *testing.T) {
	shanghai := time.FixedZone("Asia/Shanghai", 8*60*60)
	server := &Server{
		systemAnchor: "anchor",
		shanghai:     shanghai,
	}
	createdAt := time.Date(2026, 4, 28, 21, 34, 10, 0, shanghai).UnixMilli()

	snapshot := &SessionSnapshot{
		UserID: "u1",
		ARoundsFull: []SessionRound{
			{
				ClientMsgID:       "r1",
				User:              "番茄叶子发黄",
				Assistant:         "先看新叶老叶差异",
				CreatedAt:         createdAt,
				Region:            "山东寿光",
				RegionSource:      RegionSourceGPS,
				RegionReliability: RegionReliable,
			},
		},
	}

	messages, usedCount, _ := server.buildPromptMessages(
		snapshot,
		6,
		"今天又黄了",
		nil,
		"context",
		"",
	)

	if usedCount != 1 {
		t.Fatalf("expected 1 historical round, got %d", usedCount)
	}
	historicalUser, ok := messages[2].Content.(string)
	if !ok {
		t.Fatalf("expected historical user message to be text, got %#v", messages[2].Content)
	}
	if !strings.Contains(historicalUser, "后台背景时间：2026-04-28 21:34:10（Asia/Shanghai，仅供参考）") {
		t.Fatalf("expected historical time prefix, got %q", historicalUser)
	}
	if !strings.Contains(historicalUser, "后台背景地点：山东寿光；地点可信度：reliable（仅供参考）") {
		t.Fatalf("expected historical region prefix, got %q", historicalUser)
	}
	if !strings.Contains(historicalUser, "番茄叶子发黄") {
		t.Fatalf("expected original user text preserved, got %q", historicalUser)
	}
}

func TestBuildVisionUserContentAllowsImageOnly(t *testing.T) {
	content, ok := buildVisionUserContent("", []string{"https://img/current.jpg"}).([]map[string]any)
	if !ok {
		t.Fatalf("expected multimodal content for image-only input")
	}
	if len(content) != 2 {
		t.Fatalf("expected text hint plus image block for image-only input, got %d", len(content))
	}
	if got := content[0]["type"]; got != "text" {
		t.Fatalf("expected image-only input to include internal text hint first, got %#v", got)
	}
	if got, _ := content[0]["text"].(string); !strings.Contains(got, "只上传了图片") {
		t.Fatalf("expected image-only internal hint, got %#v", got)
	}
	if got := content[1]["type"]; got != "image_url" {
		t.Fatalf("expected image block after internal text hint, got %#v", got)
	}
}

func TestValidateChatStreamInputMatchesCurrentRules(t *testing.T) {
	cases := []struct {
		name        string
		clientMsgID string
		text        string
		images      []string
		want        string
	}{
		{
			name:        "text only allowed",
			clientMsgID: "msg-1",
			text:        "hello",
			want:        "",
		},
		{
			name:        "image only allowed",
			clientMsgID: "msg-2",
			images:      []string{"https://img/current.jpg"},
			want:        "",
		},
		{
			name:        "text and images allowed",
			clientMsgID: "msg-3",
			text:        "hello",
			images:      []string{"https://img/current.jpg"},
			want:        "",
		},
		{
			name:        "reject empty payload",
			clientMsgID: "msg-4",
			want:        "text or images required",
		},
		{
			name:        "reject too many images",
			clientMsgID: "msg-5",
			images: []string{
				"https://img/1.jpg",
				"https://img/2.jpg",
				"https://img/3.jpg",
				"https://img/4.jpg",
				"https://img/5.jpg",
			},
			want: "single request supports up to 4 images",
		},
		{
			name:        "reject too long client msg id",
			clientMsgID: strings.Repeat("x", maxClientMsgIDLength+1),
			text:        "hello",
			want:        "client_msg_id too long",
		},
		{
			name:        "reject text over app input limit",
			clientMsgID: "msg-6",
			text:        strings.Repeat("农", maxChatTextRunes+1),
			want:        "text too long",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := validateChatStreamInput(tc.clientMsgID, tc.text, tc.images); got != tc.want {
				t.Fatalf("validation mismatch: got %q want %q", got, tc.want)
			}
		})
	}
}

func TestValidateChatStreamImageURLsRequiresUploadedJPEG(t *testing.T) {
	t.Setenv("BASE_PUBLIC_URL", "https://api.example.com")
	server := &Server{}
	req := httptest.NewRequest("POST", "https://api.example.com/api/chat/stream", nil)

	cases := []struct {
		name   string
		images []string
		want   string
	}{
		{
			name:   "empty allowed",
			images: nil,
			want:   "",
		},
		{
			name:   "uploaded jpg allowed",
			images: []string{"https://api.example.com/uploads/abc123.jpg"},
			want:   "",
		},
		{
			name:   "support image rejected from chat",
			images: []string{"https://api.example.com/uploads/support/abc123.jpg"},
			want:   "invalid image url",
		},
		{
			name:   "external host rejected",
			images: []string{"https://other.example.com/uploads/abc123.jpg"},
			want:   "invalid image url",
		},
		{
			name:   "non upload path rejected",
			images: []string{"https://api.example.com/assets/abc123.jpg"},
			want:   "invalid image url",
		},
		{
			name:   "non jpg rejected",
			images: []string{"https://api.example.com/uploads/abc123.png"},
			want:   "invalid image url",
		},
		{
			name:   "query rejected",
			images: []string{"https://api.example.com/uploads/abc123.jpg?x=1"},
			want:   "invalid image url",
		},
		{
			name:   "userinfo rejected",
			images: []string{"https://user@api.example.com/uploads/abc123.jpg"},
			want:   "invalid image url",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := server.validateChatStreamImageURLs(req, tc.images); got != tc.want {
				t.Fatalf("validation mismatch: got %q want %q", got, tc.want)
			}
		})
	}
}

func TestValidateChatStreamImageURLsRequiresConfiguredPublicBase(t *testing.T) {
	t.Setenv("BASE_PUBLIC_URL", "")
	t.Setenv("UPLOAD_BASE_URL", "")
	server := &Server{}
	req := httptest.NewRequest("POST", "https://api.example.com/api/chat/stream", nil)
	req.Header.Set("X-Forwarded-Host", "api.example.com")
	req.Header.Set("X-Forwarded-Proto", "https")

	if got := server.validateChatStreamImageURLs(req, []string{"https://api.example.com/uploads/abc123.jpg"}); got != "image host not configured" {
		t.Fatalf("validation mismatch: got %q want %q", got, "image host not configured")
	}
}

func TestSessionGenerationRejectsMissingGenerationAfterClear(t *testing.T) {
	state := SessionGenerationState{Generation: 2, ClearedAt: 1000}

	if !isStaleForSessionGenerationState(state, nil) {
		t.Fatalf("missing generation after a clear should be stale")
	}

	expected := 2
	if isStaleForSessionGenerationState(state, &expected) {
		t.Fatalf("matching generation should not be stale")
	}

	expected = 1
	if !isStaleForSessionGenerationState(state, &expected) {
		t.Fatalf("old generation should be stale")
	}
}

func TestSessionRoundReplayRequiresCurrentGenerationAfterClear(t *testing.T) {
	state := SessionGenerationState{Generation: 2, ClearedAt: 1000}
	completion := SessionRoundCompletion{Completed: true, CreatedAt: 1001}

	if !isSessionRoundCompletionStaleForSessionGeneration(completion, state, nil) {
		t.Fatalf("replay without generation after clear should be stale")
	}

	expected := 1
	if !isSessionRoundCompletionStaleForSessionGeneration(completion, state, &expected) {
		t.Fatalf("replay with old generation should be stale")
	}

	expected = 2
	if isSessionRoundCompletionStaleForSessionGeneration(completion, state, &expected) {
		t.Fatalf("replay with current generation and post-clear completion should be allowed")
	}
}

func TestSessionRoundCompletionBeforeClearIsStale(t *testing.T) {
	state := SessionGenerationState{Generation: 1, ClearedAt: 1000}

	if !isSessionRoundCompletionBeforeClear(
		SessionRoundCompletion{Completed: true, CreatedAt: 999},
		state,
	) {
		t.Fatalf("completion before clear should be treated as stale replay")
	}

	if !isSessionRoundCompletionBeforeClear(
		SessionRoundCompletion{Completed: true, CreatedAt: 1000},
		state,
	) {
		t.Fatalf("completion at clear timestamp should be treated as stale replay")
	}

	if isSessionRoundCompletionBeforeClear(
		SessionRoundCompletion{Completed: true, CreatedAt: 1001},
		state,
	) {
		t.Fatalf("completion after clear should be replayable")
	}
}

func TestTierWindowsAndSummaryIntervalsMatchBusinessRules(t *testing.T) {
	if got := getAWindowByTier(TierFree); got != 6 {
		t.Fatalf("free a-window mismatch: %d", got)
	}
	if got := getAWindowByTier(TierPlus); got != 6 {
		t.Fatalf("plus a-window mismatch: %d", got)
	}
	if got := getAWindowByTier(TierPro); got != 9 {
		t.Fatalf("pro a-window mismatch: %d", got)
	}

	if got := GetMemoryDocumentInterval(TierFree); got != 6 {
		t.Fatalf("free/plus memory interval mismatch: %d", got)
	}
	if got := GetMemoryDocumentInterval(TierPro); got != 9 {
		t.Fatalf("pro memory interval mismatch: %d", got)
	}
}

package app

import (
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestBuildPromptMessagesOnlyKeepsImagesForPreviousRoundAndCurrentRound(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}

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
			},
		},
	}

	messages, usedCount, hasB, hasC := server.buildPromptMessages(
		snapshot,
		6,
		"current",
		[]string{"https://img/current.jpg"},
		"context",
	)

	if usedCount != 2 {
		t.Fatalf("expected 2 historical rounds, got %d", usedCount)
	}
	if hasB || hasC {
		t.Fatalf("expected no summaries, got hasB=%v hasC=%v", hasB, hasC)
	}
	if len(messages) != 7 {
		t.Fatalf("expected 7 messages, got %d", len(messages))
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

	currentUser := messages[6]
	currentContent, ok := currentUser.Content.([]map[string]any)
	if !ok || len(currentContent) != 2 {
		t.Fatalf("expected current round to keep images in context, got %#v", currentUser.Content)
	}
	if got := currentContent[1]["image_url"].(map[string]any)["url"]; got != "https://img/current.jpg" {
		t.Fatalf("expected current image preserved, got %#v", got)
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

func TestBuildPromptMessagesAddsBCSummariesWhenPresent(t *testing.T) {
	server := &Server{
		systemAnchor: "anchor",
	}

	snapshot := &SessionSnapshot{
		UserID:      "u1",
		BSummary:    "b-summary",
		CSummary:    "c-summary",
		ARoundsFull: []SessionRound{},
	}

	messages, usedCount, hasB, hasC := server.buildPromptMessages(
		snapshot,
		6,
		"hello",
		nil,
		"context",
	)

	if usedCount != 0 {
		t.Fatalf("expected no historical rounds, got %d", usedCount)
	}
	if !hasB || !hasC {
		t.Fatalf("expected both summaries present, got hasB=%v hasC=%v", hasB, hasC)
	}
	if len(messages) != 5 {
		t.Fatalf("expected 5 messages, got %d", len(messages))
	}
	if messages[2].Role != "system" || messages[3].Role != "system" {
		t.Fatalf("expected summary prompts to be inserted as system messages")
	}
	if !strings.HasPrefix(messages[2].Content.(string), "B层短期记忆（仅供参考）\n") {
		t.Fatalf("expected B short-term memory label, got %#v", messages[2].Content)
	}
	if !strings.HasPrefix(messages[3].Content.(string), "C层长期记忆（仅供参考）\n") {
		t.Fatalf("expected C long-term memory label, got %#v", messages[3].Content)
	}
	if messages[4].Content != "hello" {
		t.Fatalf("expected current text-only user message, got %#v", messages[4].Content)
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

	messages, usedCount, _, _ := server.buildPromptMessages(
		snapshot,
		6,
		"今天又黄了",
		nil,
		"context",
	)

	if usedCount != 1 {
		t.Fatalf("expected 1 historical round, got %d", usedCount)
	}
	historicalUser, ok := messages[2].Content.(string)
	if !ok {
		t.Fatalf("expected historical user message to be text, got %#v", messages[2].Content)
	}
	if !strings.Contains(historicalUser, "历史轮次时间：2026-04-28 21:34:10（Asia/Shanghai）") {
		t.Fatalf("expected historical time prefix, got %q", historicalUser)
	}
	if !strings.Contains(historicalUser, "历史轮次地点：山东寿光；地点可信度：reliable") {
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

	b, c := GetSummaryIntervals(TierFree)
	if b != 6 || c != 20 {
		t.Fatalf("free/plus summary intervals mismatch: b=%d c=%d", b, c)
	}
	b, c = GetSummaryIntervals(TierPro)
	if b != 9 || c != 20 {
		t.Fatalf("pro summary intervals mismatch: b=%d c=%d", b, c)
	}
}

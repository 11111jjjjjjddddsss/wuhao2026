package app

import "testing"

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
	if messages[4].Content != "hello" {
		t.Fatalf("expected current text-only user message, got %#v", messages[4].Content)
	}
}

func TestBuildVisionUserContentAllowsImageOnly(t *testing.T) {
	content, ok := buildVisionUserContent("", []string{"https://img/current.jpg"}).([]map[string]any)
	if !ok {
		t.Fatalf("expected multimodal content for image-only input")
	}
	if len(content) != 1 {
		t.Fatalf("expected single image block for image-only input, got %d", len(content))
	}
	if got := content[0]["type"]; got != "image_url" {
		t.Fatalf("expected image-only input to omit empty text block, got %#v", got)
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
	if b != 6 || c != 25 {
		t.Fatalf("free/plus summary intervals mismatch: b=%d c=%d", b, c)
	}
	b, c = GetSummaryIntervals(TierPro)
	if b != 9 || c != 25 {
		t.Fatalf("pro summary intervals mismatch: b=%d c=%d", b, c)
	}
}

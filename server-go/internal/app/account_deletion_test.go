package app

import (
	"testing"
	"time"
)

func TestAccountDeletionStatusTransitions(t *testing.T) {
	allowed := []struct {
		from string
		to   string
	}{
		{"pending", "processing"},
		{"pending", "completed"},
		{"pending", "rejected"},
		{"pending", "cancelled"},
		{"processing", "completed"},
		{"processing", "rejected"},
		{"processing", "cancelled"},
	}
	for _, tc := range allowed {
		if !canTransitionAccountDeletionStatus(tc.from, tc.to) {
			t.Fatalf("expected transition %s -> %s to be allowed", tc.from, tc.to)
		}
	}

	blocked := []struct {
		from string
		to   string
	}{
		{"pending", "pending"},
		{"processing", "pending"},
		{"processing", "processing"},
		{"completed", "processing"},
		{"completed", "pending"},
		{"rejected", "processing"},
		{"cancelled", "processing"},
	}
	for _, tc := range blocked {
		if canTransitionAccountDeletionStatus(tc.from, tc.to) {
			t.Fatalf("expected transition %s -> %s to be blocked", tc.from, tc.to)
		}
	}
}

func TestNormalizeAccountDeletionFreeTextRejectsSensitiveValues(t *testing.T) {
	if got, code := normalizeAccountDeletionFreeText("请联系 138-0013-8000", "note"); code != "" || got == "" {
		t.Fatalf("numeric contact note should be accepted and trimmed, got %q code=%q", got, code)
	}
	if got, code := normalizeAccountDeletionFreeText("电话已沟通，等待用户确认", "note"); code != "" || got == "" {
		t.Fatalf("expected operational note to be allowed, got value=%q code=%q", got, code)
	}
}

func TestRedactAccountDeletionFreeTextForReadOnlyRoles(t *testing.T) {
	requests := []AccountDeletionRequest{
		{
			RequestID:   "adr_1",
			UserID:      "acct_test",
			Reason:      "app_request",
			UserMessage: "请联系 138-0013-8000",
			HandlerNote: "客服已沟通订单和礼品卡",
		},
	}

	redactAccountDeletionFreeText(requests, false)
	if requests[0].UserMessage != "" || requests[0].HandlerNote != "" {
		t.Fatalf("readonly account deletion free text should be hidden: %#v", requests[0])
	}
	if requests[0].Reason != "app_request" || requests[0].RequestID == "" || requests[0].UserID == "" {
		t.Fatalf("redaction should keep non-free-text fields: %#v", requests[0])
	}

	requests[0].UserMessage = "用户补充说明"
	requests[0].HandlerNote = "客服处理备注"
	redactAccountDeletionFreeText(requests, true)
	if requests[0].UserMessage == "" || requests[0].HandlerNote == "" {
		t.Fatalf("support-visible account deletion free text should be preserved: %#v", requests[0])
	}
}

func TestAccountDeletionSLASkipsWeekends(t *testing.T) {
	shanghai := time.FixedZone("Asia/Shanghai", 8*60*60)
	start := time.Date(2026, 6, 5, 10, 0, 0, 0, shanghai) // Friday
	got := time.UnixMilli(addBusinessDaysUnixMilli(start.UnixMilli(), 15)).In(shanghai)
	want := time.Date(2026, 6, 26, 10, 0, 0, 0, shanghai)
	if !got.Equal(want) {
		t.Fatalf("deadline mismatch: got %s want %s", got.Format(time.RFC3339), want.Format(time.RFC3339))
	}
}

func TestAccountDeletionSLAThresholdSkipsWeekends(t *testing.T) {
	shanghai := time.FixedZone("Asia/Shanghai", 8*60*60)
	now := time.Date(2026, 6, 30, 10, 0, 0, 0, shanghai) // Tuesday
	got := time.UnixMilli(accountDeletionSLAThresholdMs(now.UnixMilli())).In(shanghai)
	want := time.Date(2026, 6, 9, 10, 0, 0, 0, shanghai)
	if !got.Equal(want) {
		t.Fatalf("threshold mismatch: got %s want %s", got.Format(time.RFC3339), want.Format(time.RFC3339))
	}
}

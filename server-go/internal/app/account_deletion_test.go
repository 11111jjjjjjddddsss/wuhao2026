package app

import "testing"

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
	if _, code := normalizeAccountDeletionFreeText("请联系 138-0013-8000", "note"); code != "note_contains_sensitive_value" {
		t.Fatalf("expected sensitive note to be rejected, got %q", code)
	}
	if got, code := normalizeAccountDeletionFreeText("电话已沟通，等待用户确认", "note"); code != "" || got == "" {
		t.Fatalf("expected operational note to be allowed, got value=%q code=%q", got, code)
	}
}

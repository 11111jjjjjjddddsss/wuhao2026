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

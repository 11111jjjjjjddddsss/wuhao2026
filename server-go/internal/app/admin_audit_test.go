package app

import (
	"encoding/json"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"
)

func TestAdminActorFromRequestUsesExplicitHeader(t *testing.T) {
	req := httptest.NewRequest("GET", "/internal/admin/audit-logs", nil)
	req.Header.Set("X-Admin-Actor", " Ops.User@example.COM ")

	got := adminActorFromRequest(req, "support_admin_secret")
	if got != "ops.user@example.com" {
		t.Fatalf("actor = %q, want normalized header actor", got)
	}
}

func TestAdminActorFromRequestFallsBackWithoutLeakingSecretValue(t *testing.T) {
	req := httptest.NewRequest("GET", "/internal/admin/audit-logs", nil)

	got := adminActorFromRequest(req, "support_admin_secret")
	if got != "support_admin_secret" {
		t.Fatalf("actor = %q, want safe fallback label", got)
	}
}

func TestAdminAuditDetailsJSONDropsSensitiveFields(t *testing.T) {
	raw, err := adminAuditDetailsJSON(map[string]any{
		"event":       "upload.failed",
		"message":     "full user message should not be stored",
		"phone":       "13800138000",
		"image_count": 2,
		"media_count": 2,
	})
	if err != nil {
		t.Fatalf("adminAuditDetailsJSON failed: %v", err)
	}
	encoded, ok := raw.(string)
	if !ok {
		t.Fatalf("raw = %#v, want string", raw)
	}
	if strings.Contains(encoded, "13800138000") ||
		strings.Contains(encoded, "full user message") ||
		strings.Contains(encoded, "image_count") {
		t.Fatalf("details leaked sensitive fields: %s", encoded)
	}
	var decoded map[string]any
	if err := json.Unmarshal([]byte(encoded), &decoded); err != nil {
		t.Fatalf("decode details: %v", err)
	}
	if _, ok := decoded["event"]; ok {
		t.Fatalf("details should drop event-like text fields: %#v", decoded)
	}
	if decoded["media_count"] != float64(2) {
		t.Fatalf("details mismatch: %#v", decoded)
	}
}

func TestParseAdminAuditLogQueryNormalizesAndCapsLimit(t *testing.T) {
	filter, validationError := parseAdminAuditLogQuery(url.Values{
		"action":         {" Internal.App.Logs.List "},
		"target_user_id": {" acct_123 "},
		"success":        {"1"},
		"since_ms":       {"123"},
		"limit":          {"999"},
	}, time.UnixMilli(10_000))
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if filter.Action != "internal.app.logs.list" ||
		filter.TargetUserID != "acct_123" ||
		filter.Success == nil ||
		!*filter.Success ||
		filter.SinceMs != 123 ||
		filter.Limit != maxAdminAuditLogListLimit {
		t.Fatalf("filter mismatch: %#v", filter)
	}
}

func TestParseAdminAuditLogQueryRejectsInvalidFilters(t *testing.T) {
	tests := []struct {
		name   string
		values url.Values
		want   string
	}{
		{name: "invalid success", values: url.Values{"success": {"maybe"}}, want: "invalid_success"},
		{name: "invalid since", values: url.Values{"since_ms": {"-1"}}, want: "invalid_since_ms"},
		{name: "invalid limit", values: url.Values{"limit": {"0"}}, want: "invalid_limit"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, got := parseAdminAuditLogQuery(tt.values, time.Now())
			if got != tt.want {
				t.Fatalf("validation error = %q, want %q", got, tt.want)
			}
		})
	}
}

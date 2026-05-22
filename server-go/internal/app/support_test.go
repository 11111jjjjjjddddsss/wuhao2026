package app

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"
)

func TestNormalizeSupportMessagePayloadAllowsTextImageAndImageOnly(t *testing.T) {
	body, images, validationError := normalizeSupportMessagePayload("  反馈内容  ", []string{
		"https://example.com/uploads/a.jpg",
	})
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if body != "反馈内容" {
		t.Fatalf("body = %q, want trimmed text", body)
	}
	if len(images) != 1 {
		t.Fatalf("images len = %d, want 1", len(images))
	}

	body, images, validationError = normalizeSupportMessagePayload(" ", []string{
		"https://example.com/uploads/a.jpg",
	})
	if validationError != "" {
		t.Fatalf("image-only message should pass, got %s", validationError)
	}
	if body != "" || len(images) != 1 {
		t.Fatalf("image-only payload mismatch: body=%q images=%v", body, images)
	}
}

func TestNormalizeSupportMessagePayloadRejectsInvalidInput(t *testing.T) {
	tests := []struct {
		name   string
		body   string
		images []string
		want   string
	}{
		{
			name: "empty",
			want: "body or images required",
		},
		{
			name:   "too many images",
			images: []string{"1", "2", "3", "4", "5"},
			want:   "single request supports up to 4 images",
		},
		{
			name: "body too long",
			body: strings.Repeat("字", supportMessageMaxRunes+1),
			want: "body too long",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, _, validationError := normalizeSupportMessagePayload(tt.body, tt.images)
			if validationError != tt.want {
				t.Fatalf("validationError = %q, want %q", validationError, tt.want)
			}
		})
	}
}

func TestSupportImageURLsJSON(t *testing.T) {
	raw, err := supportImageURLsJSON([]string{
		"https://example.com/uploads/a.jpg",
		"",
		"https://example.com/uploads/b.jpg",
	})
	if err != nil {
		t.Fatalf("supportImageURLsJSON failed: %v", err)
	}
	encoded, ok := raw.(string)
	if !ok {
		t.Fatalf("raw = %#v, want string", raw)
	}

	var decoded []string
	if err := json.Unmarshal([]byte(encoded), &decoded); err != nil {
		t.Fatalf("decode image urls json: %v", err)
	}
	if len(decoded) != 2 {
		t.Fatalf("decoded len = %d, want 2", len(decoded))
	}

	empty, err := supportImageURLsJSON(nil)
	if err != nil {
		t.Fatalf("empty supportImageURLsJSON failed: %v", err)
	}
	if empty != nil {
		t.Fatalf("empty = %#v, want nil", empty)
	}
}

func TestRequireSupportAdminSecret(t *testing.T) {
	server := &Server{logger: slog.New(slog.NewTextHandler(os.Stdout, nil))}

	t.Setenv("SUPPORT_ADMIN_SECRET", "")
	req := httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	rec := httptest.NewRecorder()
	if server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected missing secret to reject")
	}
	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("missing secret status = %d, want %d", rec.Code, http.StatusServiceUnavailable)
	}

	t.Setenv("SUPPORT_ADMIN_SECRET", "secret")
	req = httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.Header.Set("X-Support-Admin-Secret", "secret")
	rec = httptest.NewRecorder()
	if !server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected header secret to pass")
	}

	req = httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.Header.Set("Authorization", "Bearer secret")
	rec = httptest.NewRecorder()
	if !server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected bearer secret to pass")
	}

	req = httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.Header.Set("X-Support-Admin-Secret", "wrong")
	rec = httptest.NewRecorder()
	if server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected wrong secret to reject")
	}
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("wrong secret status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}
}

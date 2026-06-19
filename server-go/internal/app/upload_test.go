package app

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func TestHandleUploadsStaticCacheControlByPurpose(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "plain.jpg"), []byte("plain"), 0o644); err != nil {
		t.Fatalf("write plain upload: %v", err)
	}
	if err := os.MkdirAll(filepath.Join(dir, "support"), 0o755); err != nil {
		t.Fatalf("mkdir support upload dir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(dir, "support", "case.jpg"), []byte("support"), 0o644); err != nil {
		t.Fatalf("write support upload: %v", err)
	}
	server := &Server{uploadStore: LocalUploadStore{dir: dir}}

	tests := []struct {
		name      string
		path      string
		wantCache string
	}{
		{name: "plain", path: "/uploads/plain.jpg", wantCache: "public, max-age=3600"},
		{name: "support", path: "/uploads/support/case.jpg", wantCache: "private, no-store"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req := httptest.NewRequest(http.MethodHead, tt.path, nil)
			rec := httptest.NewRecorder()
			server.handleUploadsStatic(rec, req)
			if rec.Code != http.StatusOK {
				t.Fatalf("status=%d, want 200", rec.Code)
			}
			if got := rec.Header().Get("Cache-Control"); got != tt.wantCache {
				t.Fatalf("Cache-Control=%q, want %q", got, tt.wantCache)
			}
		})
	}
}

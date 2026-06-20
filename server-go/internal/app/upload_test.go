package app

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
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

	req := httptest.NewRequest(http.MethodHead, "/uploads/plain.jpg", nil)
	rec := httptest.NewRecorder()
	server.handleUploadsStatic(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("plain status=%d, want 200", rec.Code)
	}
	if got := rec.Header().Get("Cache-Control"); got != "public, max-age=3600" {
		t.Fatalf("plain Cache-Control=%q, want public", got)
	}
	if got := rec.Header().Get("Content-Length"); got != "5" {
		t.Fatalf("plain Content-Length=%q, want %d", got, len("plain"))
	}

	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()
	mock.ExpectQuery("SELECT 1").
		WithArgs("acct_support_owner", "support/case.jpg").
		WillReturnRows(sqlmock.NewRows([]string{"1"}).AddRow(1))
	server.store = store

	req = httptest.NewRequest(http.MethodHead, "/uploads/support/case.jpg", nil)
	req.Header.Set("X-User-Id", "acct_support_owner")
	rec = httptest.NewRecorder()
	server.handleUploadsStatic(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("support status=%d, want 200", rec.Code)
	}
	if got := rec.Header().Get("Cache-Control"); got != "private, no-store" {
		t.Fatalf("support Cache-Control=%q, want private no-store", got)
	}
	if got := rec.Header().Get("Content-Length"); got != "7" {
		t.Fatalf("support Content-Length=%q, want %d", got, len("support"))
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

func TestHandleUploadsStaticRequiresSupportImageOwner(t *testing.T) {
	dir := t.TempDir()
	if err := os.MkdirAll(filepath.Join(dir, "support"), 0o755); err != nil {
		t.Fatalf("mkdir support upload dir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(dir, "support", "case.jpg"), []byte("support"), 0o644); err != nil {
		t.Fatalf("write support upload: %v", err)
	}
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()
	mock.ExpectQuery("SELECT 1").
		WithArgs("acct_other", "support/case.jpg").
		WillReturnRows(sqlmock.NewRows([]string{"1"}))
	server := &Server{uploadStore: LocalUploadStore{dir: dir}, store: store}

	req := httptest.NewRequest(http.MethodGet, "/uploads/support/case.jpg", nil)
	req.Header.Set("X-User-Id", "acct_other")
	rec := httptest.NewRecorder()
	server.handleUploadsStatic(rec, req)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("status=%d, want 404", rec.Code)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

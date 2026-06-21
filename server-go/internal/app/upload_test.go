package app

import (
	"bytes"
	"errors"
	"io"
	"log/slog"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
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
	server := &Server{uploadStore: LocalUploadStore{dir: dir}, store: store, logger: slog.New(slog.NewTextHandler(io.Discard, nil))}

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

func TestHandleUploadRejectsOversizedFileAsTooLarge(t *testing.T) {
	dir := t.TempDir()
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	part, err := writer.CreateFormFile("file", "oversized.jpg")
	if err != nil {
		t.Fatalf("CreateFormFile: %v", err)
	}
	if _, err := part.Write(bytes.Repeat([]byte{0xff}, maxUploadFileSize+1)); err != nil {
		t.Fatalf("write file: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close multipart writer: %v", err)
	}
	server := &Server{uploadStore: LocalUploadStore{dir: dir}}

	req := httptest.NewRequest(http.MethodPost, "/upload", body)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("X-User-Id", "acct_upload_owner")
	rec := httptest.NewRecorder()
	server.handleUpload(rec, req)

	if rec.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("status=%d, want 413 body=%s", rec.Code, rec.Body.String())
	}
	if !strings.Contains(rec.Body.String(), "body_too_large") {
		t.Fatalf("body=%q, want body_too_large", rec.Body.String())
	}
}

func TestHandleUploadDeletesSupportObjectWhenOwnershipRecordFails(t *testing.T) {
	t.Setenv("BASE_PUBLIC_URL", "https://api.nongjiqiancha.cn")
	dir := t.TempDir()
	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	if err := writer.WriteField("purpose", uploadPurposeSupport); err != nil {
		t.Fatalf("WriteField: %v", err)
	}
	part, err := writer.CreateFormFile("file", "support.jpg")
	if err != nil {
		t.Fatalf("CreateFormFile: %v", err)
	}
	jpegBytes := []byte{0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01, 0x01, 0x00, 0xff, 0xd9}
	if _, err := part.Write(jpegBytes); err != nil {
		t.Fatalf("write file: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close multipart writer: %v", err)
	}
	store, mock, cleanup := newGiftCardSQLMock(t)
	defer cleanup()
	mock.ExpectExec("INSERT INTO support_upload_ownership").
		WithArgs(sqlmock.AnyArg(), "acct_support_owner", sqlmock.AnyArg()).
		WillReturnError(errors.New("db down"))
	server := &Server{
		uploadStore: LocalUploadStore{dir: dir},
		store:       store,
		logger:      slog.New(slog.NewTextHandler(io.Discard, nil)),
	}

	req := httptest.NewRequest(http.MethodPost, "/upload", body)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("X-User-Id", "acct_support_owner")
	rec := httptest.NewRecorder()
	server.handleUpload(rec, req)

	if rec.Code != http.StatusInternalServerError {
		t.Fatalf("status=%d, want 500 body=%s", rec.Code, rec.Body.String())
	}
	entries, err := os.ReadDir(filepath.Join(dir, uploadPurposeSupport))
	if err != nil {
		t.Fatalf("ReadDir support upload dir: %v", err)
	}
	if len(entries) != 0 {
		t.Fatalf("support upload object was not cleaned up, entries=%v", entries)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("sql expectations: %v", err)
	}
}

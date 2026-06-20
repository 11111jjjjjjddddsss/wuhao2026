package app

import (
	"context"
	"io"
	"strings"
	"testing"
)

func TestNewUploadStoreDefaultsToLocal(t *testing.T) {
	t.Setenv("UPLOAD_STORAGE_BACKEND", "")
	t.Setenv("UPLOAD_STORE", "")

	store, err := NewUploadStoreFromEnv(t.TempDir())
	if err != nil {
		t.Fatalf("NewUploadStoreFromEnv returned error: %v", err)
	}
	if store.Name() != "local" {
		t.Fatalf("expected local upload store, got %q", store.Name())
	}
}

func TestLocalUploadStoreSaveAndOpen(t *testing.T) {
	store := LocalUploadStore{dir: t.TempDir()}
	const filename = "sample.jpg"
	const body = "jpeg-bytes"

	if err := store.Save(context.Background(), filename, "image/jpeg", []byte(body)); err != nil {
		t.Fatalf("Save returned error: %v", err)
	}
	reader, contentType, contentLength, err := store.Open(context.Background(), filename)
	if err != nil {
		t.Fatalf("Open returned error: %v", err)
	}
	defer reader.Close()
	data, err := io.ReadAll(reader)
	if err != nil {
		t.Fatalf("ReadAll returned error: %v", err)
	}
	if string(data) != body {
		t.Fatalf("unexpected body %q", string(data))
	}
	if contentType != "" {
		t.Fatalf("local store should not synthesize content type, got %q", contentType)
	}
	if contentLength != int64(len(body)) {
		t.Fatalf("contentLength=%d, want %d", contentLength, len(body))
	}
}

func TestLocalUploadStoreSaveAndOpenSupportObject(t *testing.T) {
	store := LocalUploadStore{dir: t.TempDir()}
	const filename = "support/sample.jpg"
	const body = "support-jpeg-bytes"

	if err := store.Save(context.Background(), filename, "image/jpeg", []byte(body)); err != nil {
		t.Fatalf("Save returned error: %v", err)
	}
	reader, contentType, contentLength, err := store.Open(context.Background(), filename)
	if err != nil {
		t.Fatalf("Open returned error: %v", err)
	}
	defer reader.Close()
	data, err := io.ReadAll(reader)
	if err != nil {
		t.Fatalf("ReadAll returned error: %v", err)
	}
	if string(data) != body {
		t.Fatalf("unexpected body %q", string(data))
	}
	if contentType != "" {
		t.Fatalf("local store should not synthesize content type, got %q", contentType)
	}
	if contentLength != int64(len(body)) {
		t.Fatalf("contentLength=%d, want %d", contentLength, len(body))
	}
}

func TestNewUploadStoreRejectsIncompleteOSSConfig(t *testing.T) {
	t.Setenv("UPLOAD_STORAGE_BACKEND", "oss")
	t.Setenv("OSS_BUCKET", "")
	t.Setenv("ALIYUN_OSS_BUCKET", "")
	t.Setenv("OSS_ACCESS_KEY_ID", "")
	t.Setenv("ALIYUN_OSS_ACCESS_KEY_ID", "")
	t.Setenv("ALIBABA_CLOUD_ACCESS_KEY_ID", "")
	t.Setenv("OSS_ACCESS_KEY_SECRET", "")
	t.Setenv("ALIYUN_OSS_ACCESS_KEY_SECRET", "")
	t.Setenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET", "")

	_, err := NewUploadStoreFromEnv(t.TempDir())
	if err == nil || !strings.Contains(err.Error(), "OSS upload storage requires") {
		t.Fatalf("expected missing OSS config error, got %v", err)
	}
}

func TestCleanOSSObjectPrefix(t *testing.T) {
	tests := map[string]string{
		"":          "uploads/",
		"uploads":   "uploads/",
		"/uploads/": "uploads/",
		"support":   "support/",
	}
	for input, want := range tests {
		if got := cleanOSSObjectPrefix(input); got != want {
			t.Fatalf("cleanOSSObjectPrefix(%q) = %q, want %q", input, got, want)
		}
	}
}

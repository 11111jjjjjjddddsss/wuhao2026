package app

import (
	"bytes"
	"context"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"github.com/aliyun/aliyun-oss-go-sdk/oss"
)

type UploadStore interface {
	Name() string
	Save(ctx context.Context, filename string, contentType string, data []byte) error
	Open(ctx context.Context, filename string) (io.ReadCloser, string, error)
}

func NewUploadStoreFromEnv(localDir string) (UploadStore, error) {
	backend := strings.ToLower(strings.TrimSpace(firstNonEmpty(os.Getenv("UPLOAD_STORAGE_BACKEND"), os.Getenv("UPLOAD_STORE"))))
	if backend == "" || backend == "local" {
		return LocalUploadStore{dir: localDir}, nil
	}
	if backend != "oss" {
		return nil, fmt.Errorf("unsupported upload storage backend %q", backend)
	}

	endpoint := strings.TrimSpace(firstNonEmpty(
		os.Getenv("OSS_ENDPOINT"),
		os.Getenv("ALIYUN_OSS_ENDPOINT"),
		"https://oss-cn-beijing-internal.aliyuncs.com",
	))
	bucketName := strings.TrimSpace(firstNonEmpty(os.Getenv("OSS_BUCKET"), os.Getenv("ALIYUN_OSS_BUCKET")))
	accessKeyID := strings.TrimSpace(firstNonEmpty(
		os.Getenv("OSS_ACCESS_KEY_ID"),
		os.Getenv("ALIYUN_OSS_ACCESS_KEY_ID"),
		os.Getenv("ALIBABA_CLOUD_ACCESS_KEY_ID"),
	))
	accessKeySecret := strings.TrimSpace(firstNonEmpty(
		os.Getenv("OSS_ACCESS_KEY_SECRET"),
		os.Getenv("ALIYUN_OSS_ACCESS_KEY_SECRET"),
		os.Getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET"),
	))
	if bucketName == "" || accessKeyID == "" || accessKeySecret == "" {
		return nil, fmt.Errorf("OSS upload storage requires OSS_BUCKET, OSS_ACCESS_KEY_ID and OSS_ACCESS_KEY_SECRET")
	}

	client, err := oss.New(endpoint, accessKeyID, accessKeySecret)
	if err != nil {
		return nil, err
	}
	bucket, err := client.Bucket(bucketName)
	if err != nil {
		return nil, err
	}
	return &OSSUploadStore{bucket: bucket, prefix: cleanOSSObjectPrefix(os.Getenv("OSS_UPLOAD_PREFIX"))}, nil
}

type LocalUploadStore struct {
	dir string
}

func (s LocalUploadStore) Name() string {
	return "local"
}

func (s LocalUploadStore) Save(_ context.Context, filename string, _ string, data []byte) error {
	path := filepath.Join(s.dir, filepath.FromSlash(filename))
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	return os.WriteFile(path, data, 0o644)
}

func (s LocalUploadStore) Open(_ context.Context, filename string) (io.ReadCloser, string, error) {
	file, err := os.Open(filepath.Join(s.dir, filepath.FromSlash(filename)))
	return file, "", err
}

type OSSUploadStore struct {
	bucket *oss.Bucket
	prefix string
}

func (s *OSSUploadStore) Name() string {
	return "oss"
}

func (s *OSSUploadStore) Save(_ context.Context, filename string, contentType string, data []byte) error {
	return s.bucket.PutObject(s.objectKey(filename), bytes.NewReader(data), oss.ContentType(contentType))
}

func (s *OSSUploadStore) Open(_ context.Context, filename string) (io.ReadCloser, string, error) {
	key := s.objectKey(filename)
	meta, err := s.bucket.GetObjectDetailedMeta(key)
	if err != nil {
		return nil, "", err
	}
	reader, err := s.bucket.GetObject(key)
	if err != nil {
		return nil, "", err
	}
	return reader, meta.Get("Content-Type"), nil
}

func (s *OSSUploadStore) objectKey(filename string) string {
	if isServableUploadObjectName(filename) && strings.Contains(filename, "/") {
		return filename
	}
	return s.prefix + filename
}

func cleanOSSObjectPrefix(raw string) string {
	prefix := strings.Trim(strings.TrimSpace(raw), "/")
	if prefix == "" {
		return "uploads/"
	}
	return prefix + "/"
}

func uploadStoreHealthStatus(store UploadStore) string {
	if store == nil {
		return "missing"
	}
	return store.Name()
}

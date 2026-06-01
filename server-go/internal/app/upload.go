package app

import (
	"crypto/rand"
	"encoding/hex"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
)

const maxUploadFileSize = 1024 * 1024
const (
	defaultUploadRateLimitWindow        = 10 * time.Minute
	defaultUploadRateLimitMaxHits       = 120
	defaultUploadRateLimitPruneInterval = 10 * time.Minute
)

var uploadExtensions = map[string]string{
	"image/jpeg": ".jpg",
}

func (s *Server) handleUpload(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if s.uploadLimiter != nil {
		limitKey := uploadRateLimitKey(auth.UserID, GetClientIP(r))
		if allowed, retryAfter := s.uploadLimiter.Consume(limitKey, time.Now()); !allowed {
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":               "rate_limited",
				"retry_after_seconds": retryAfter,
			})
			return
		}
	}

	r.Body = http.MaxBytesReader(w, r.Body, maxUploadFileSize+1024)
	if err := r.ParseMultipartForm(maxUploadFileSize); err != nil {
		s.writeError(w, http.StatusBadRequest, "missing file field")
		return
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "missing file field")
		return
	}
	defer file.Close()

	contentType, data, err := readUpload(file)
	if err != nil {
		s.writeError(w, http.StatusBadRequest, "upload failed")
		return
	}
	if _, ok := uploadExtensions[contentType]; !ok {
		s.writeError(w, http.StatusBadRequest, "only jpeg allowed")
		return
	}

	publicBaseURL := resolvePublicBaseURL(r)
	if !strings.HasPrefix(publicBaseURL, "https://") {
		s.writeError(w, http.StatusServiceUnavailable, "BASE_PUBLIC_URL must be configured as public https url")
		return
	}

	filename, err := randomFilename(uploadExtensions[contentType], header)
	if err != nil {
		s.logger.Error("build upload filename failed", "error", err)
		s.writeError(w, http.StatusInternalServerError, "upload failed")
		return
	}

	if err := s.uploadStore.Save(r.Context(), filename, contentType, data); err != nil {
		s.logger.Error("upload save failed", "filename", filename, "backend", s.uploadStore.Name(), "error", err)
		s.writeError(w, http.StatusInternalServerError, "upload failed")
		return
	}

	s.writeJSON(w, http.StatusOK, map[string]any{
		"url": strings.TrimRight(publicBaseURL, "/") + "/uploads/" + filename,
	})
}

func (s *Server) handleUploadsStatic(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		w.Header().Set("Allow", "GET, HEAD")
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	name := strings.TrimPrefix(r.URL.Path, "/uploads/")
	if name == "" || strings.Contains(name, "..") || strings.Contains(name, "/") || strings.Contains(name, "\\") {
		http.NotFound(w, r)
		return
	}

	reader, contentType, err := s.uploadStore.Open(r.Context(), name)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	defer reader.Close()
	if contentType == "" {
		contentType = "image/jpeg"
	}
	w.Header().Set("Content-Type", contentType)
	w.Header().Set("Cache-Control", "public, max-age=3600")
	if r.Method == http.MethodHead {
		return
	}
	_, _ = io.Copy(w, reader)
}

func readUpload(file multipart.File) (string, []byte, error) {
	data, err := io.ReadAll(io.LimitReader(file, maxUploadFileSize+1))
	if err != nil {
		return "", nil, err
	}
	if len(data) == 0 || len(data) > maxUploadFileSize {
		return "", nil, io.ErrUnexpectedEOF
	}
	contentType := http.DetectContentType(data)
	return contentType, data, nil
}

func resolvePublicBaseURL(_ *http.Request) string {
	configured := strings.TrimRight(strings.TrimSpace(firstNonEmpty(
		os.Getenv("BASE_PUBLIC_URL"),
		os.Getenv("UPLOAD_BASE_URL"),
	)), "/")
	if strings.HasPrefix(configured, "http://") || strings.HasPrefix(configured, "https://") {
		return configured
	}
	return ""
}

func randomFilename(ext string, header *multipart.FileHeader) (string, error) {
	if ext == "" {
		ext = strings.ToLower(filepath.Ext(header.Filename))
	}
	raw := make([]byte, 16)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	return hex.EncodeToString(raw) + ext, nil
}

func newUploadRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("UPLOAD_RATE_LIMIT_WINDOW_SECONDS", defaultUploadRateLimitWindow),
		MaxHits:       envIntWithDefault("UPLOAD_RATE_LIMIT_MAX_HITS", defaultUploadRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("UPLOAD_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultUploadRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultUploadRateLimitWindow, defaultUploadRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(config)
}

func uploadRateLimitKey(userID string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	return "upload:" + rateLimitHash(userID, secret) + ":" + rateLimitHash(ip, secret)
}

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		value = strings.TrimSpace(value)
		if value != "" {
			return value
		}
	}
	return ""
}

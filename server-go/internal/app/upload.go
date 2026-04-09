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
)

const maxUploadFileSize = 10 * 1024 * 1024

var uploadExtensions = map[string]string{
	"image/jpeg": ".jpg",
	"image/png":  ".png",
	"image/webp": ".webp",
}

func (s *Server) handleUpload(w http.ResponseWriter, r *http.Request) {
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
		s.writeError(w, http.StatusBadRequest, "only jpeg/png/webp allowed")
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

	targetPath := filepath.Join(s.uploadsDir, filename)
	if err := os.WriteFile(targetPath, data, 0o644); err != nil {
		s.logger.Error("upload write failed", "path", targetPath, "error", err)
		s.writeError(w, http.StatusInternalServerError, "upload failed")
		return
	}

	s.writeJSON(w, http.StatusOK, map[string]any{
		"url": strings.TrimRight(publicBaseURL, "/") + "/uploads/" + filename,
	})
}

func (s *Server) handleUploadsStatic(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		http.NotFound(w, r)
		return
	}

	name := strings.TrimPrefix(r.URL.Path, "/uploads/")
	if name == "" || strings.Contains(name, "..") || strings.Contains(name, "/") || strings.Contains(name, "\\") {
		http.NotFound(w, r)
		return
	}

	target := filepath.Join(s.uploadsDir, name)
	info, err := os.Stat(target)
	if err != nil || info.IsDir() {
		http.NotFound(w, r)
		return
	}
	http.ServeFile(w, r, target)
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

func resolvePublicBaseURL(r *http.Request) string {
	configured := strings.TrimRight(strings.TrimSpace(firstNonEmpty(
		os.Getenv("BASE_PUBLIC_URL"),
		os.Getenv("UPLOAD_BASE_URL"),
	)), "/")
	if strings.HasPrefix(configured, "http://") || strings.HasPrefix(configured, "https://") {
		return configured
	}

	host := strings.TrimSpace(firstNonEmpty(r.Header.Get("X-Forwarded-Host"), r.Host))
	proto := strings.TrimSpace(firstNonEmpty(r.Header.Get("X-Forwarded-Proto"), ternary(r.TLS != nil, "https", "http")))
	if host == "" || proto == "" {
		return ""
	}
	return strings.TrimRight(proto+"://"+host, "/")
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

func firstNonEmpty(values ...string) string {
	for _, value := range values {
		value = strings.TrimSpace(value)
		if value != "" {
			return value
		}
	}
	return ""
}

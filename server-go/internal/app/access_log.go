package app

import (
	"context"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"
)

const (
	defaultAccessLogSlowMs             = 3000
	requestIDHeader                    = "X-Request-Id"
	maxRequestIDLength                 = 64
	maxLoggedUserAgentRunes            = 160
	maxLoggedUserIDRunes               = 128
	requestIDContextKey     contextKey = "request_id"
)

type contextKey string

func RequestIDFromContext(ctx context.Context) string {
	if ctx == nil {
		return ""
	}
	if value, ok := ctx.Value(requestIDContextKey).(string); ok {
		return value
	}
	return ""
}

func (s *Server) withAccessLog(next http.Handler) http.Handler {
	if next == nil {
		next = http.NotFoundHandler()
	}
	logger := s.logger
	if logger == nil {
		logger = slog.Default()
	}

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requestID := resolveRequestID(r.Header.Get(requestIDHeader))
		w.Header().Set(requestIDHeader, requestID)

		startedAt := time.Now()
		recorder := &accessLogResponseWriter{ResponseWriter: w}
		auth := ResolveAuthUserID(r)
		ctx := context.WithValue(r.Context(), requestIDContextKey, requestID)
		next.ServeHTTP(recorder, r.WithContext(ctx))

		status := recorder.Status()
		duration := time.Since(startedAt)
		if shouldSuppressAccessLog(r.URL.Path, status, duration) {
			return
		}

		attrs := accessLogAttrs(r, requestID, auth, recorder, duration)
		switch {
		case status >= http.StatusInternalServerError:
			logger.Error("http_request_error", attrs...)
		case isSlowAccessLog(duration):
			logger.Warn("http_request_slow", attrs...)
		default:
			logger.Info("http_request", attrs...)
		}
	})
}

func resolveRequestID(raw string) string {
	if id := sanitizeRequestID(raw); id != "" {
		return id
	}
	generated, err := randomHexString(12)
	if err != nil {
		return "req_" + time.Now().UTC().Format("20060102150405.000000000")
	}
	return "req_" + generated
}

func sanitizeRequestID(raw string) string {
	value := strings.TrimSpace(raw)
	if value == "" {
		return ""
	}
	if len(value) > maxRequestIDLength {
		value = value[:maxRequestIDLength]
	}
	for _, ch := range value {
		if (ch >= 'a' && ch <= 'z') ||
			(ch >= 'A' && ch <= 'Z') ||
			(ch >= '0' && ch <= '9') ||
			ch == '-' || ch == '_' || ch == '.' || ch == ':' {
			continue
		}
		return ""
	}
	return value
}

func accessLogAttrs(r *http.Request, requestID string, auth AuthInfo, recorder *accessLogResponseWriter, duration time.Duration) []any {
	attrs := []any{
		"request_id", requestID,
		"method", r.Method,
		"path", accessLogPath(r),
		"status", recorder.Status(),
		"duration_ms", duration.Milliseconds(),
		"response_bytes", recorder.BytesWritten(),
		"masked_ip", auth.MaskedIP,
		"auth_mode", string(auth.AuthMode),
		"content_length", r.ContentLength,
	}
	if auth.UserID != "" {
		attrs = append(attrs, "user_id", truncateRunes(auth.UserID, maxLoggedUserIDRunes))
	}
	if userAgent := truncateRunes(strings.TrimSpace(r.UserAgent()), maxLoggedUserAgentRunes); userAgent != "" {
		attrs = append(attrs, "user_agent", userAgent)
	}
	return attrs
}

func accessLogPath(r *http.Request) string {
	if r == nil || r.URL == nil {
		return ""
	}
	path := strings.TrimSpace(r.URL.Path)
	if path == "" {
		return "/"
	}
	return path
}

func shouldSuppressAccessLog(path string, status int, duration time.Duration) bool {
	if status >= http.StatusBadRequest || isSlowAccessLog(duration) {
		return false
	}
	if path == "/healthz" {
		return true
	}
	if strings.HasPrefix(path, "/uploads/") {
		return true
	}
	return false
}

func isSlowAccessLog(duration time.Duration) bool {
	thresholdMs := envIntWithDefault("ACCESS_LOG_SLOW_MS", defaultAccessLogSlowMs)
	if thresholdMs <= 0 {
		return false
	}
	return duration >= time.Duration(thresholdMs)*time.Millisecond
}

type accessLogResponseWriter struct {
	http.ResponseWriter
	statusCode   int
	bytesWritten int64
}

func (w *accessLogResponseWriter) WriteHeader(statusCode int) {
	if w.statusCode != 0 {
		return
	}
	w.statusCode = statusCode
	w.ResponseWriter.WriteHeader(statusCode)
}

func (w *accessLogResponseWriter) Write(data []byte) (int, error) {
	if w.statusCode == 0 {
		w.WriteHeader(http.StatusOK)
	}
	n, err := w.ResponseWriter.Write(data)
	w.bytesWritten += int64(n)
	return n, err
}

func (w *accessLogResponseWriter) Flush() {
	if w.statusCode == 0 {
		w.WriteHeader(http.StatusOK)
	}
	if flusher, ok := w.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

func (w *accessLogResponseWriter) ReadFrom(reader io.Reader) (int64, error) {
	if w.statusCode == 0 {
		w.WriteHeader(http.StatusOK)
	}
	if readerFrom, ok := w.ResponseWriter.(io.ReaderFrom); ok {
		n, err := readerFrom.ReadFrom(reader)
		w.bytesWritten += n
		return n, err
	}
	n, err := io.Copy(w.ResponseWriter, reader)
	w.bytesWritten += n
	return n, err
}

func (w *accessLogResponseWriter) Unwrap() http.ResponseWriter {
	return w.ResponseWriter
}

func (w *accessLogResponseWriter) Status() int {
	if w.statusCode == 0 {
		return http.StatusOK
	}
	return w.statusCode
}

func (w *accessLogResponseWriter) BytesWritten() int64 {
	return w.bytesWritten
}

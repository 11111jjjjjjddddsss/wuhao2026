package main

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"time"

	"github.com/joho/godotenv"

	"nongji-server-go/internal/app"
)

const (
	defaultReadHeaderTimeout = 5 * time.Second
	defaultReadTimeout       = 15 * time.Second
	defaultIdleTimeout       = 90 * time.Second
	defaultShutdownTimeout   = 30 * time.Second
	defaultMaxHeaderBytes    = 1 << 20
)

func main() {
	_ = godotenv.Load()
	if strings.TrimSpace(os.Getenv("TZ")) == "" {
		_ = os.Setenv("TZ", "Asia/Shanghai")
	}

	logger, closeLogger, err := setupLogger()
	if err != nil {
		slog.New(slog.NewJSONHandler(os.Stdout, nil)).Error("logger setup failed", "error", err)
		os.Exit(1)
	}
	defer closeLogger()

	server, err := app.NewServer(logger)
	if err != nil {
		logger.Error("server bootstrap failed", "error", err)
		os.Exit(1)
	}
	defer func() {
		if err := server.Close(); err != nil {
			logger.Warn("server resource close failed", "error", err)
		}
	}()

	addr := resolveListenAddr()
	httpServer := buildHTTPServer(addr, server.Handler())
	serverErr := make(chan error, 1)
	go func() {
		logger.Info(
			"server listening",
			"addr", httpServer.Addr,
			"read_header_timeout", httpServer.ReadHeaderTimeout.String(),
			"read_timeout", httpServer.ReadTimeout.String(),
			"write_timeout", httpServer.WriteTimeout.String(),
			"idle_timeout", httpServer.IdleTimeout.String(),
			"max_header_bytes", httpServer.MaxHeaderBytes,
		)
		serverErr <- httpServer.ListenAndServe()
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	select {
	case sig := <-stop:
		logger.Info("server shutdown requested", "signal", sig.String())
	case err := <-serverErr:
		if errors.Is(err, http.ErrServerClosed) {
			return
		}
		logger.Error("server stopped", "error", err)
		os.Exit(1)
	}

	shutdownTimeout := envDurationWithDefault("HTTP_SHUTDOWN_TIMEOUT_SECONDS", defaultShutdownTimeout)
	ctx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
	defer cancel()
	if err := httpServer.Shutdown(ctx); err != nil {
		logger.Error("server graceful shutdown failed", "error", err)
		_ = httpServer.Close()
		os.Exit(1)
	}
	logger.Info("server shutdown complete")
}

func setupLogger() (*slog.Logger, func() error, error) {
	writers := []io.Writer{os.Stdout}
	closers := []io.Closer{}

	logFilePath := strings.TrimSpace(os.Getenv("LOG_FILE_PATH"))
	if logFilePath != "" {
		if err := os.MkdirAll(filepath.Dir(logFilePath), 0o750); err != nil {
			return nil, nil, err
		}
		file, err := os.OpenFile(logFilePath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o640)
		if err != nil {
			return nil, nil, err
		}
		writers = append(writers, file)
		closers = append(closers, file)
	}

	logger := slog.New(slog.NewJSONHandler(io.MultiWriter(writers...), &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))
	closeFn := func() error {
		var closeErr error
		for _, closer := range closers {
			if err := closer.Close(); err != nil && closeErr == nil {
				closeErr = err
			}
		}
		return closeErr
	}
	return logger, closeFn, nil
}

func resolveListenAddr() string {
	if addr := strings.TrimSpace(os.Getenv("LISTEN_ADDR")); addr != "" {
		return addr
	}

	host := strings.TrimSpace(os.Getenv("LISTEN_HOST"))
	if host == "" {
		host = "127.0.0.1"
	}
	port := strings.TrimSpace(os.Getenv("PORT"))
	if port == "" {
		port = "3000"
	}

	return host + ":" + port
}

func buildHTTPServer(addr string, handler http.Handler) *http.Server {
	return &http.Server{
		Addr:              addr,
		Handler:           handler,
		ReadHeaderTimeout: envDurationWithDefault("HTTP_READ_HEADER_TIMEOUT_SECONDS", defaultReadHeaderTimeout),
		ReadTimeout:       envDurationWithDefault("HTTP_READ_TIMEOUT_SECONDS", defaultReadTimeout),
		WriteTimeout:      envDurationWithDefault("HTTP_WRITE_TIMEOUT_SECONDS", 0),
		IdleTimeout:       envDurationWithDefault("HTTP_IDLE_TIMEOUT_SECONDS", defaultIdleTimeout),
		MaxHeaderBytes:    envBytesWithDefault("HTTP_MAX_HEADER_BYTES", defaultMaxHeaderBytes),
	}
}

func envDurationWithDefault(name string, fallback time.Duration) time.Duration {
	raw := strings.TrimSpace(os.Getenv(name))
	if raw == "" {
		return fallback
	}
	seconds, err := strconv.Atoi(raw)
	if err != nil || seconds < 0 {
		return fallback
	}
	return time.Duration(seconds) * time.Second
}

func envBytesWithDefault(name string, fallback int) int {
	raw := strings.TrimSpace(os.Getenv(name))
	if raw == "" {
		return fallback
	}
	value, err := strconv.Atoi(raw)
	if err != nil || value <= 0 {
		return fallback
	}
	return value
}

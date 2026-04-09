package main

import (
	"log/slog"
	"net/http"
	"os"
	"strings"

	"github.com/joho/godotenv"

	"nongji-server-go/internal/app"
)

func main() {
	_ = godotenv.Load()
	if strings.TrimSpace(os.Getenv("TZ")) == "" {
		_ = os.Setenv("TZ", "Asia/Shanghai")
	}

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))

	server, err := app.NewServer(logger)
	if err != nil {
		logger.Error("server bootstrap failed", "error", err)
		os.Exit(1)
	}

	port := strings.TrimSpace(os.Getenv("PORT"))
	if port == "" {
		port = "3000"
	}

	addr := ":" + port
	logger.Info("server listening", "addr", addr)
	if err := http.ListenAndServe(addr, server.Handler()); err != nil {
		logger.Error("server stopped", "error", err)
		os.Exit(1)
	}
}

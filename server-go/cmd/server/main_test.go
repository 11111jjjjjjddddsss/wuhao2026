package main

import (
	"net/http"
	"testing"
	"time"
)

func TestResolveListenAddrDefaultsToLoopback(t *testing.T) {
	t.Setenv("LISTEN_ADDR", "")
	t.Setenv("LISTEN_HOST", "")
	t.Setenv("PORT", "")

	if got := resolveListenAddr(); got != "127.0.0.1:3000" {
		t.Fatalf("resolveListenAddr() = %q, want %q", got, "127.0.0.1:3000")
	}
}

func TestResolveListenAddrUsesPortAndHost(t *testing.T) {
	t.Setenv("LISTEN_ADDR", "")
	t.Setenv("LISTEN_HOST", "0.0.0.0")
	t.Setenv("PORT", "8080")

	if got := resolveListenAddr(); got != "0.0.0.0:8080" {
		t.Fatalf("resolveListenAddr() = %q, want %q", got, "0.0.0.0:8080")
	}
}

func TestResolveListenAddrOverride(t *testing.T) {
	t.Setenv("LISTEN_ADDR", ":9000")
	t.Setenv("LISTEN_HOST", "127.0.0.1")
	t.Setenv("PORT", "8080")

	if got := resolveListenAddr(); got != ":9000" {
		t.Fatalf("resolveListenAddr() = %q, want %q", got, ":9000")
	}
}

func TestBuildHTTPServerDefaults(t *testing.T) {
	t.Setenv("HTTP_READ_HEADER_TIMEOUT_SECONDS", "")
	t.Setenv("HTTP_READ_TIMEOUT_SECONDS", "")
	t.Setenv("HTTP_WRITE_TIMEOUT_SECONDS", "")
	t.Setenv("HTTP_IDLE_TIMEOUT_SECONDS", "")
	t.Setenv("HTTP_MAX_HEADER_BYTES", "")

	server := buildHTTPServer("127.0.0.1:3000", http.NewServeMux())

	if server.ReadHeaderTimeout != 5*time.Second {
		t.Fatalf("ReadHeaderTimeout = %v, want 5s", server.ReadHeaderTimeout)
	}
	if server.ReadTimeout != 15*time.Second {
		t.Fatalf("ReadTimeout = %v, want 15s", server.ReadTimeout)
	}
	if server.WriteTimeout != 0 {
		t.Fatalf("WriteTimeout = %v, want 0 for SSE", server.WriteTimeout)
	}
	if server.IdleTimeout != 90*time.Second {
		t.Fatalf("IdleTimeout = %v, want 90s", server.IdleTimeout)
	}
	if server.MaxHeaderBytes != 1<<20 {
		t.Fatalf("MaxHeaderBytes = %d, want %d", server.MaxHeaderBytes, 1<<20)
	}
}

func TestBuildHTTPServerUsesEnvOverrides(t *testing.T) {
	t.Setenv("HTTP_READ_HEADER_TIMEOUT_SECONDS", "7")
	t.Setenv("HTTP_READ_TIMEOUT_SECONDS", "21")
	t.Setenv("HTTP_WRITE_TIMEOUT_SECONDS", "600")
	t.Setenv("HTTP_IDLE_TIMEOUT_SECONDS", "33")
	t.Setenv("HTTP_MAX_HEADER_BYTES", "65536")

	server := buildHTTPServer("127.0.0.1:3000", http.NewServeMux())

	if server.ReadHeaderTimeout != 7*time.Second {
		t.Fatalf("ReadHeaderTimeout = %v, want 7s", server.ReadHeaderTimeout)
	}
	if server.ReadTimeout != 21*time.Second {
		t.Fatalf("ReadTimeout = %v, want 21s", server.ReadTimeout)
	}
	if server.WriteTimeout != 600*time.Second {
		t.Fatalf("WriteTimeout = %v, want 600s", server.WriteTimeout)
	}
	if server.IdleTimeout != 33*time.Second {
		t.Fatalf("IdleTimeout = %v, want 33s", server.IdleTimeout)
	}
	if server.MaxHeaderBytes != 65536 {
		t.Fatalf("MaxHeaderBytes = %d, want 65536", server.MaxHeaderBytes)
	}
}

package main

import "testing"

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

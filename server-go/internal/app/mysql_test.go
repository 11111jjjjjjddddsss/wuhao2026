package app

import (
	"testing"
	"time"
)

func TestResolveDBPoolConfigDefaults(t *testing.T) {
	t.Setenv("MYSQL_MAX_OPEN_CONNS", "")
	t.Setenv("MYSQL_MAX_IDLE_CONNS", "")
	t.Setenv("MYSQL_CONN_MAX_IDLE_SECONDS", "")
	t.Setenv("MYSQL_CONN_MAX_LIFETIME_SECONDS", "")

	config := resolveDBPoolConfig()
	if config.MaxOpenConns != 10 {
		t.Fatalf("MaxOpenConns = %d, want 10", config.MaxOpenConns)
	}
	if config.MaxIdleConns != 10 {
		t.Fatalf("MaxIdleConns = %d, want 10", config.MaxIdleConns)
	}
	if config.ConnMaxIdleTime != 5*time.Minute {
		t.Fatalf("ConnMaxIdleTime = %s, want 5m", config.ConnMaxIdleTime)
	}
	if config.ConnMaxLifetime != 30*time.Minute {
		t.Fatalf("ConnMaxLifetime = %s, want 30m", config.ConnMaxLifetime)
	}
}

func TestResolveDBPoolConfigFromEnv(t *testing.T) {
	t.Setenv("MYSQL_MAX_OPEN_CONNS", "32")
	t.Setenv("MYSQL_MAX_IDLE_CONNS", "16")
	t.Setenv("MYSQL_CONN_MAX_IDLE_SECONDS", "120")
	t.Setenv("MYSQL_CONN_MAX_LIFETIME_SECONDS", "900")

	config := resolveDBPoolConfig()
	if config.MaxOpenConns != 32 {
		t.Fatalf("MaxOpenConns = %d, want 32", config.MaxOpenConns)
	}
	if config.MaxIdleConns != 16 {
		t.Fatalf("MaxIdleConns = %d, want 16", config.MaxIdleConns)
	}
	if config.ConnMaxIdleTime != 120*time.Second {
		t.Fatalf("ConnMaxIdleTime = %s, want 120s", config.ConnMaxIdleTime)
	}
	if config.ConnMaxLifetime != 900*time.Second {
		t.Fatalf("ConnMaxLifetime = %s, want 900s", config.ConnMaxLifetime)
	}
}

func TestResolveDBPoolConfigClampsIdleToOpen(t *testing.T) {
	t.Setenv("MYSQL_MAX_OPEN_CONNS", "8")
	t.Setenv("MYSQL_MAX_IDLE_CONNS", "20")
	t.Setenv("MYSQL_CONN_MAX_IDLE_SECONDS", "bad")
	t.Setenv("MYSQL_CONN_MAX_LIFETIME_SECONDS", "-1")

	config := resolveDBPoolConfig()
	if config.MaxOpenConns != 8 {
		t.Fatalf("MaxOpenConns = %d, want 8", config.MaxOpenConns)
	}
	if config.MaxIdleConns != 8 {
		t.Fatalf("MaxIdleConns = %d, want 8", config.MaxIdleConns)
	}
	if config.ConnMaxIdleTime != 5*time.Minute {
		t.Fatalf("ConnMaxIdleTime = %s, want default 5m", config.ConnMaxIdleTime)
	}
	if config.ConnMaxLifetime != 30*time.Minute {
		t.Fatalf("ConnMaxLifetime = %s, want default 30m", config.ConnMaxLifetime)
	}
}

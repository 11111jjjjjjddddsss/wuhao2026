package app

import (
	"testing"
	"time"

	mysqlDriver "github.com/go-sql-driver/mysql"
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

func TestBuildMySQLDSNDefaultsCollationForRawDSN(t *testing.T) {
	dsn, err := buildMySQLDSN("user:pass@tcp(127.0.0.1:3306)/nongjiqiancha?parseTime=true")
	if err != nil {
		t.Fatalf("buildMySQLDSN returned error: %v", err)
	}
	cfg, err := mysqlDriver.ParseDSN(dsn)
	if err != nil {
		t.Fatalf("ParseDSN returned error: %v", err)
	}
	if cfg.Collation != defaultMySQLCollation {
		t.Fatalf("Collation = %q, want %q", cfg.Collation, defaultMySQLCollation)
	}
	if !cfg.MultiStatements {
		t.Fatalf("MultiStatements = false, want true")
	}
}

func TestBuildMySQLDSNDefaultsCollationForMySQLURL(t *testing.T) {
	dsn, err := buildMySQLDSN("mysql://user:pass@127.0.0.1:3306/nongjiqiancha?parseTime=true")
	if err != nil {
		t.Fatalf("buildMySQLDSN returned error: %v", err)
	}
	cfg, err := mysqlDriver.ParseDSN(dsn)
	if err != nil {
		t.Fatalf("ParseDSN returned error: %v", err)
	}
	if cfg.Collation != defaultMySQLCollation {
		t.Fatalf("Collation = %q, want %q", cfg.Collation, defaultMySQLCollation)
	}
	if !cfg.MultiStatements {
		t.Fatalf("MultiStatements = false, want true")
	}
}

func TestBuildMySQLDSNRespectsExplicitCollationOverride(t *testing.T) {
	dsn, err := buildMySQLDSN("mysql://user:pass@127.0.0.1:3306/nongjiqiancha?collation=utf8mb4_0900_ai_ci")
	if err != nil {
		t.Fatalf("buildMySQLDSN returned error: %v", err)
	}
	cfg, err := mysqlDriver.ParseDSN(dsn)
	if err != nil {
		t.Fatalf("ParseDSN returned error: %v", err)
	}
	if cfg.Collation != "utf8mb4_0900_ai_ci" {
		t.Fatalf("Collation = %q, want explicit override", cfg.Collation)
	}
}

func TestBuildMySQLDSNRespectsExplicitRawMultiStatementsFalse(t *testing.T) {
	dsn, err := buildMySQLDSN("user:pass@tcp(127.0.0.1:3306)/nongjiqiancha?parseTime=true&multiStatements=false")
	if err != nil {
		t.Fatalf("buildMySQLDSN returned error: %v", err)
	}
	cfg, err := mysqlDriver.ParseDSN(dsn)
	if err != nil {
		t.Fatalf("ParseDSN returned error: %v", err)
	}
	if cfg.MultiStatements {
		t.Fatalf("MultiStatements = true, want explicit false")
	}
}

func TestBuildMySQLDSNRespectsExplicitURLMultiStatementsFalse(t *testing.T) {
	dsn, err := buildMySQLDSN("mysql://user:pass@127.0.0.1:3306/nongjiqiancha?multiStatements=false")
	if err != nil {
		t.Fatalf("buildMySQLDSN returned error: %v", err)
	}
	cfg, err := mysqlDriver.ParseDSN(dsn)
	if err != nil {
		t.Fatalf("ParseDSN returned error: %v", err)
	}
	if cfg.MultiStatements {
		t.Fatalf("MultiStatements = true, want explicit false")
	}
}

package app

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net"
	"net/url"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"time"

	_ "github.com/go-sql-driver/mysql"
	mysqlDriver "github.com/go-sql-driver/mysql"
)

const defaultMySQLCollation = "utf8mb4_unicode_ci"

func OpenDB() (*sql.DB, error) {
	rawURL := strings.TrimSpace(os.Getenv("MYSQL_URL"))
	if rawURL == "" {
		return nil, fmt.Errorf("MYSQL_URL is missing")
	}

	dsn, err := buildMySQLDSN(rawURL)
	if err != nil {
		return nil, err
	}

	db, err := sql.Open("mysql", dsn)
	if err != nil {
		return nil, err
	}

	pool := resolveDBPoolConfig()
	db.SetMaxOpenConns(pool.MaxOpenConns)
	db.SetMaxIdleConns(pool.MaxIdleConns)
	db.SetConnMaxIdleTime(pool.ConnMaxIdleTime)
	db.SetConnMaxLifetime(pool.ConnMaxLifetime)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, err
	}
	return db, nil
}

type dbPoolConfig struct {
	MaxOpenConns    int
	MaxIdleConns    int
	ConnMaxIdleTime time.Duration
	ConnMaxLifetime time.Duration
}

const defaultMySQLMigrationTimeout = 2 * time.Minute

func resolveDBPoolConfig() dbPoolConfig {
	maxOpen := envIntWithDefault("MYSQL_MAX_OPEN_CONNS", 10)
	maxIdle := envIntWithDefault("MYSQL_MAX_IDLE_CONNS", 10)
	if maxIdle > maxOpen && maxOpen > 0 {
		maxIdle = maxOpen
	}

	return dbPoolConfig{
		MaxOpenConns:    maxOpen,
		MaxIdleConns:    maxIdle,
		ConnMaxIdleTime: envDurationWithDefault("MYSQL_CONN_MAX_IDLE_SECONDS", 5*time.Minute),
		ConnMaxLifetime: envDurationWithDefault("MYSQL_CONN_MAX_LIFETIME_SECONDS", 30*time.Minute),
	}
}

func envIntWithDefault(name string, fallback int) int {
	raw := strings.TrimSpace(os.Getenv(name))
	if raw == "" {
		return fallback
	}
	value, err := strconv.Atoi(raw)
	if err != nil || value < 0 {
		return fallback
	}
	return value
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

func InitMySQL(ctx context.Context, db *sql.DB, migrationsDir string) (err error) {
	conn, err := db.Conn(ctx)
	if err != nil {
		return err
	}
	defer conn.Close()

	releaseMigrationLock, err := acquireMySQLMigrationLock(ctx, conn)
	if err != nil {
		return err
	}
	defer func() {
		if releaseErr := releaseMigrationLock(); releaseErr != nil {
			if err != nil {
				err = errors.Join(err, releaseErr)
			} else {
				err = releaseErr
			}
		}
	}()

	entries, err := os.ReadDir(migrationsDir)
	if err != nil {
		return err
	}

	files := make([]string, 0, len(entries))
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".sql") {
			continue
		}
		files = append(files, filepath.Join(migrationsDir, entry.Name()))
	}
	sort.Strings(files)

	for _, file := range files {
		content, err := os.ReadFile(file)
		if err != nil {
			return err
		}
		if strings.TrimSpace(string(content)) == "" {
			continue
		}
		if _, err := conn.ExecContext(ctx, string(content)); err != nil {
			return fmt.Errorf("run migration %s: %w", filepath.Base(file), err)
		}
	}
	return nil
}

const mysqlSchemaMigrationLockName = "nongji_schema_migration"

func acquireMySQLMigrationLock(ctx context.Context, conn *sql.Conn) (func() error, error) {
	var acquired sql.NullInt64
	if err := conn.QueryRowContext(ctx, "SELECT GET_LOCK(?, 30)", mysqlSchemaMigrationLockName).Scan(&acquired); err != nil {
		return nil, err
	}
	if !acquired.Valid || acquired.Int64 != 1 {
		return nil, fmt.Errorf("mysql schema migration lock busy")
	}
	return func() error {
		releaseCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		var released sql.NullInt64
		if err := conn.QueryRowContext(releaseCtx, "SELECT RELEASE_LOCK(?)", mysqlSchemaMigrationLockName).Scan(&released); err != nil {
			return err
		}
		if !released.Valid || released.Int64 != 1 {
			return fmt.Errorf("mysql schema migration lock release failed")
		}
		return nil
	}, nil
}

func buildMySQLDSN(raw string) (string, error) {
	if !strings.Contains(raw, "://") {
		cfg, err := mysqlDriver.ParseDSN(raw)
		if err != nil {
			return "", err
		}
		if !rawDSNHasMultiStatements(raw) {
			cfg.MultiStatements = true
		}
		applyDefaultMySQLConfig(cfg)
		return cfg.FormatDSN(), nil
	}

	parsed, err := url.Parse(raw)
	if err != nil {
		return "", err
	}
	if parsed.Scheme != "mysql" {
		return "", fmt.Errorf("unsupported MYSQL_URL scheme: %s", parsed.Scheme)
	}

	cfg := mysqlDriver.NewConfig()
	cfg.Net = "tcp"
	cfg.User = parsed.User.Username()
	if password, ok := parsed.User.Password(); ok {
		cfg.Passwd = password
	}

	host := parsed.Host
	if host == "" {
		host = "127.0.0.1:3306"
	} else if !strings.Contains(host, ":") {
		host = net.JoinHostPort(host, "3306")
	}
	cfg.Addr = host
	cfg.DBName = strings.TrimPrefix(parsed.Path, "/")
	cfg.ParseTime = true
	cfg.Loc = time.UTC
	cfg.MultiStatements = true
	cfg.AllowNativePasswords = true
	cfg.Params = map[string]string{}
	applyDefaultMySQLConfig(cfg)

	query := parsed.Query()
	for key, values := range query {
		if len(values) == 0 {
			continue
		}
		value := values[0]
		switch strings.ToLower(key) {
		case "charset":
			cfg.Params["charset"] = value
		case "collation":
			cfg.Collation = value
		case "parsetime":
			cfg.ParseTime = value == "1" || strings.EqualFold(value, "true")
		case "multistatements":
			cfg.MultiStatements = value == "1" || strings.EqualFold(value, "true")
		case "loc":
			location, err := time.LoadLocation(value)
			if err == nil {
				cfg.Loc = location
			}
		default:
			cfg.Params[key] = value
		}
	}

	return cfg.FormatDSN(), nil
}

func rawDSNHasMultiStatements(raw string) bool {
	lower := strings.ToLower(raw)
	return strings.Contains(lower, "multistatements=")
}

func applyDefaultMySQLConfig(cfg *mysqlDriver.Config) {
	if cfg == nil {
		return
	}
	if cfg.Params == nil {
		cfg.Params = map[string]string{}
	}
	if strings.TrimSpace(cfg.Params["charset"]) == "" {
		cfg.Params["charset"] = "utf8mb4"
	}
	if strings.TrimSpace(cfg.Collation) == "" {
		cfg.Collation = defaultMySQLCollation
	}
}

func resolveExistingDir(envName string, candidates ...string) (string, error) {
	if direct := strings.TrimSpace(os.Getenv(envName)); direct != "" {
		info, err := os.Stat(direct)
		if err == nil && info.IsDir() {
			return direct, nil
		}
	}

	allCandidates := resolveCandidates(candidates...)
	for _, candidate := range allCandidates {
		info, err := os.Stat(candidate)
		if err == nil && info.IsDir() {
			return candidate, nil
		}
	}

	if len(allCandidates) == 0 {
		return "", fmt.Errorf("%s not found", envName)
	}
	return "", fmt.Errorf("%s not found; tried %s", envName, strings.Join(allCandidates, ", "))
}

func resolveOrCreateDir(envName string, fallbackCandidates ...string) (string, error) {
	if direct := strings.TrimSpace(os.Getenv(envName)); direct != "" {
		if err := os.MkdirAll(direct, 0o755); err != nil {
			return "", err
		}
		return direct, nil
	}

	candidates := resolveCandidates(fallbackCandidates...)
	if len(candidates) == 0 {
		return "", fmt.Errorf("%s directory candidates missing", envName)
	}
	target := candidates[0]
	if err := os.MkdirAll(target, 0o755); err != nil {
		return "", err
	}
	return target, nil
}

func resolveCandidates(relatives ...string) []string {
	seen := map[string]struct{}{}
	add := func(target string, out *[]string) {
		if target == "" {
			return
		}
		clean := filepath.Clean(target)
		if _, ok := seen[clean]; ok {
			return
		}
		seen[clean] = struct{}{}
		*out = append(*out, clean)
	}

	out := []string{}
	cwd, _ := os.Getwd()
	for _, rel := range relatives {
		if cwd != "" {
			add(filepath.Join(cwd, rel), &out)
		}
	}

	if exe, err := os.Executable(); err == nil {
		exeDir := filepath.Dir(exe)
		for _, rel := range relatives {
			add(filepath.Join(exeDir, rel), &out)
		}
	}

	if _, file, _, ok := runtime.Caller(0); ok {
		repoRoot := filepath.Clean(filepath.Join(filepath.Dir(file), "..", "..", ".."))
		for _, rel := range relatives {
			add(filepath.Join(repoRoot, rel), &out)
		}
	}

	return out
}

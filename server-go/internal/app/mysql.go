package app

import (
	"context"
	"database/sql"
	"fmt"
	"net"
	"net/url"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"time"

	_ "github.com/go-sql-driver/mysql"
	mysqlDriver "github.com/go-sql-driver/mysql"
)

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
	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(10)
	db.SetConnMaxIdleTime(5 * time.Minute)
	db.SetConnMaxLifetime(30 * time.Minute)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		_ = db.Close()
		return nil, err
	}
	return db, nil
}

func InitMySQL(ctx context.Context, db *sql.DB, migrationsDir string) error {
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
		if _, err := db.ExecContext(ctx, string(content)); err != nil {
			return fmt.Errorf("run migration %s: %w", filepath.Base(file), err)
		}
	}
	return nil
}

func buildMySQLDSN(raw string) (string, error) {
	if !strings.Contains(raw, "://") {
		return raw, nil
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
	cfg.Params = map[string]string{
		"charset": "utf8mb4",
	}

	query := parsed.Query()
	for key, values := range query {
		if len(values) == 0 {
			continue
		}
		value := values[0]
		switch strings.ToLower(key) {
		case "charset":
			cfg.Params["charset"] = value
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

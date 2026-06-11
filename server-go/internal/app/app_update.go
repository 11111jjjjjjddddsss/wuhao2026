package app

import (
	"context"
	"database/sql"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
)

type AppUpdateInfo struct {
	Platform           string `json:"platform"`
	CurrentVersionCode int    `json:"current_version_code"`
	CurrentVersionName string `json:"current_version_name,omitempty"`
	LatestVersionCode  int    `json:"latest_version_code"`
	LatestVersionName  string `json:"latest_version_name,omitempty"`
	HasUpdate          bool   `json:"has_update"`
	ForceUpdate        bool   `json:"force_update"`
	APKURL             string `json:"apk_url,omitempty"`
	APKChecksumSHA256  string `json:"apk_sha256,omitempty"`
	ReleaseNotes       string `json:"release_notes,omitempty"`
	FileSizeBytes      int64  `json:"file_size_bytes,omitempty"`
}

type androidUpdateConfig struct {
	Enabled           bool
	LatestVersionCode int
	LatestVersionName string
	APKURL            string
	APKChecksumSHA256 string
	ReleaseNotes      string
	ForceUpdate       bool
	FileSizeBytes     int64
}

type androidUpdateConfigRecord struct {
	Config    androidUpdateConfig
	Source    string
	UpdatedBy string
	UpdatedAt int64
}

func (s *Server) handleAppUpdate(w http.ResponseWriter, r *http.Request) {
	platform := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("platform")))
	if platform == "" {
		platform = "android"
	}
	if platform != "android" {
		s.writeError(w, http.StatusBadRequest, "unsupported_platform")
		return
	}

	currentVersionCode, err := strconv.Atoi(strings.TrimSpace(r.URL.Query().Get("version_code")))
	if err != nil || currentVersionCode < 0 {
		currentVersionCode = 0
	}
	currentVersionName := strings.TrimSpace(r.URL.Query().Get("version_name"))
	cfgRecord, cfgErr := s.store.ReadAndroidUpdateConfigRecord(r.Context())
	cfg := cfgRecord.Config
	if cfgErr != nil {
		cfg = readAndroidUpdateConfig(os.Getenv)
		if s.logger != nil {
			s.logger.Warn("read android update config failed", "error", cfgErr)
		}
	}
	info := buildAndroidUpdateInfo(currentVersionCode, currentVersionName, cfg)
	if cfg.LatestVersionCode > currentVersionCode && !info.HasUpdate && s.logger != nil {
		reason := androidUpdateIgnoredReason(cfg)
		s.logger.Warn(
			"android update config ignored",
			"reason", reason,
			"latestVersionCode", cfg.LatestVersionCode,
			"currentVersionCode", currentVersionCode,
		)
	}
	s.writeJSON(w, http.StatusOK, info)
}

func readAndroidUpdateConfig(getenv func(string) string) androidUpdateConfig {
	latestVersionCode := parsePositiveInt(getenv("APP_ANDROID_LATEST_VERSION_CODE"))
	apkURL := strings.TrimSpace(getenv("APP_ANDROID_APK_URL"))
	enabled := parseBoolEnv(getenv("APP_ANDROID_UPDATE_ENABLED"))
	if !enabled && latestVersionCode > 0 && apkURL != "" {
		enabled = true
	}
	return androidUpdateConfig{
		Enabled:           enabled,
		LatestVersionCode: latestVersionCode,
		LatestVersionName: strings.TrimSpace(getenv("APP_ANDROID_LATEST_VERSION_NAME")),
		APKURL:            apkURL,
		APKChecksumSHA256: normalizeSHA256Hex(getenv("APP_ANDROID_APK_SHA256")),
		ReleaseNotes:      strings.TrimSpace(getenv("APP_ANDROID_RELEASE_NOTES")),
		ForceUpdate:       parseBoolEnv(getenv("APP_ANDROID_FORCE_UPDATE")),
		FileSizeBytes:     parsePositiveInt64(getenv("APP_ANDROID_FILE_SIZE_BYTES")),
	}
}

func buildAndroidUpdateInfo(currentVersionCode int, currentVersionName string, cfg androidUpdateConfig) AppUpdateInfo {
	latestVersionCode := cfg.LatestVersionCode
	if latestVersionCode <= 0 {
		latestVersionCode = currentVersionCode
	}
	latestVersionName := cfg.LatestVersionName
	if latestVersionName == "" {
		latestVersionName = currentVersionName
	}
	apkURL := cfg.APKURL
	hasUpdate := cfg.Enabled && latestVersionCode > currentVersionCode && androidUpdateDownloadArtifactsComplete(cfg)
	apkChecksumSHA256 := cfg.APKChecksumSHA256
	releaseNotes := cfg.ReleaseNotes
	fileSizeBytes := cfg.FileSizeBytes
	if !hasUpdate {
		apkURL = ""
		apkChecksumSHA256 = ""
		releaseNotes = ""
		fileSizeBytes = 0
	}
	return AppUpdateInfo{
		Platform:           "android",
		CurrentVersionCode: currentVersionCode,
		CurrentVersionName: currentVersionName,
		LatestVersionCode:  latestVersionCode,
		LatestVersionName:  latestVersionName,
		HasUpdate:          hasUpdate,
		ForceUpdate:        hasUpdate && cfg.ForceUpdate,
		APKURL:             apkURL,
		APKChecksumSHA256:  apkChecksumSHA256,
		ReleaseNotes:       releaseNotes,
		FileSizeBytes:      fileSizeBytes,
	}
}

func androidUpdateIgnoredReason(cfg androidUpdateConfig) string {
	if !cfg.Enabled {
		return "disabled"
	}
	if strings.TrimSpace(cfg.APKURL) == "" {
		return "missing_apk_url"
	}
	if !isHTTPSURL(cfg.APKURL) {
		return "invalid_apk_url"
	}
	if strings.TrimSpace(cfg.APKChecksumSHA256) == "" || cfg.FileSizeBytes <= 0 {
		return "missing_release_artifacts"
	}
	return "not_available"
}

func androidUpdateConfigValid(cfg androidUpdateConfig) bool {
	return cfg.LatestVersionCode > 0 && (strings.TrimSpace(cfg.APKURL) == "" || isHTTPSURL(cfg.APKURL))
}

func androidUpdateDownloadArtifactsComplete(cfg androidUpdateConfig) bool {
	return strings.TrimSpace(cfg.APKURL) != "" &&
		isHTTPSURL(cfg.APKURL) &&
		strings.TrimSpace(cfg.APKChecksumSHA256) != "" &&
		cfg.FileSizeBytes > 0
}

func isHTTPSURL(raw string) bool {
	parsed, err := url.Parse(raw)
	return err == nil && parsed.Scheme == "https" && parsed.Host != ""
}

func parsePositiveInt(raw string) int {
	value, err := strconv.Atoi(strings.TrimSpace(raw))
	if err != nil || value <= 0 {
		return 0
	}
	return value
}

func parsePositiveInt64(raw string) int64 {
	value, err := strconv.ParseInt(strings.TrimSpace(raw), 10, 64)
	if err != nil || value <= 0 {
		return 0
	}
	return value
}

func parseBoolEnv(raw string) bool {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "1", "true", "yes", "y", "on":
		return true
	default:
		return false
	}
}

func normalizeSHA256Hex(raw string) string {
	value := strings.ToLower(strings.ReplaceAll(strings.TrimSpace(raw), ":", ""))
	if len(value) != 64 {
		return ""
	}
	for _, ch := range value {
		if (ch < '0' || ch > '9') && (ch < 'a' || ch > 'f') {
			return ""
		}
	}
	return value
}

func (s *Store) ReadAndroidUpdateConfigRecord(ctx context.Context) (androidUpdateConfigRecord, error) {
	record := androidUpdateConfigRecord{
		Config: readAndroidUpdateConfig(os.Getenv),
		Source: "env",
	}
	var enabled bool
	err := s.db.QueryRowContext(
		ctx,
		`SELECT enabled, latest_version_code, latest_version_name, apk_url, apk_sha256, release_notes, force_update, file_size_bytes, updated_by, updated_at
		   FROM app_release_configs
		  WHERE platform = 'android'
		  LIMIT 1`,
	).Scan(
		&enabled,
		&record.Config.LatestVersionCode,
		&record.Config.LatestVersionName,
		&record.Config.APKURL,
		&record.Config.APKChecksumSHA256,
		&record.Config.ReleaseNotes,
		&record.Config.ForceUpdate,
		&record.Config.FileSizeBytes,
		&record.UpdatedBy,
		&record.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return record, nil
	}
	if err != nil {
		return record, err
	}
	record.Config.Enabled = enabled
	record.Config.APKURL = strings.TrimSpace(record.Config.APKURL)
	record.Config.APKChecksumSHA256 = normalizeSHA256Hex(record.Config.APKChecksumSHA256)
	record.Config.ReleaseNotes = strings.TrimSpace(record.Config.ReleaseNotes)
	record.Source = "database"
	return record, nil
}

func (s *Store) UpsertAndroidUpdateConfigRecord(ctx context.Context, cfg androidUpdateConfig, actor string, nowMs int64) error {
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO app_release_configs(
			platform, enabled, latest_version_code, latest_version_name, apk_url, apk_sha256, release_notes, force_update, file_size_bytes, updated_by, created_at, updated_at
		) VALUES (
			'android', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
		) ON DUPLICATE KEY UPDATE
			enabled = VALUES(enabled),
			latest_version_code = VALUES(latest_version_code),
			latest_version_name = VALUES(latest_version_name),
			apk_url = VALUES(apk_url),
			apk_sha256 = VALUES(apk_sha256),
			release_notes = VALUES(release_notes),
			force_update = VALUES(force_update),
			file_size_bytes = VALUES(file_size_bytes),
			updated_by = VALUES(updated_by),
			updated_at = VALUES(updated_at)`,
		cfg.Enabled,
		cfg.LatestVersionCode,
		cfg.LatestVersionName,
		cfg.APKURL,
		cfg.APKChecksumSHA256,
		cfg.ReleaseNotes,
		cfg.ForceUpdate,
		cfg.FileSizeBytes,
		strings.TrimSpace(actor),
		nowMs,
		nowMs,
	)
	return err
}

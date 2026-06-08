package app

import (
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
	LatestVersionCode int
	LatestVersionName string
	APKURL            string
	APKChecksumSHA256 string
	ReleaseNotes      string
	ForceUpdate       bool
	FileSizeBytes     int64
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
	cfg := readAndroidUpdateConfig(os.Getenv)
	info := buildAndroidUpdateInfo(currentVersionCode, currentVersionName, cfg)
	if cfg.LatestVersionCode > currentVersionCode && !info.HasUpdate && s.logger != nil {
		reason := "missing_apk_url"
		if strings.TrimSpace(cfg.APKURL) != "" && !isHTTPSURL(cfg.APKURL) {
			reason = "invalid_apk_url"
		}
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
	return androidUpdateConfig{
		LatestVersionCode: parsePositiveInt(getenv("APP_ANDROID_LATEST_VERSION_CODE")),
		LatestVersionName: strings.TrimSpace(getenv("APP_ANDROID_LATEST_VERSION_NAME")),
		APKURL:            strings.TrimSpace(getenv("APP_ANDROID_APK_URL")),
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
	hasUpdate := latestVersionCode > currentVersionCode && apkURL != "" && isHTTPSURL(apkURL)
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

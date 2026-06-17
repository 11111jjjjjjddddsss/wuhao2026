package app

import "testing"

func TestBuildAndroidUpdateInfoNoConfig(t *testing.T) {
	info := buildAndroidUpdateInfo(3, "1.0.3", androidUpdateConfig{})
	if info.HasUpdate {
		t.Fatalf("expected no update, got %#v", info)
	}
	if info.LatestVersionCode != 3 {
		t.Fatalf("latest version code = %d, want 3", info.LatestVersionCode)
	}
	if info.APKURL != "" {
		t.Fatalf("apk url = %q, want empty", info.APKURL)
	}
}

func TestBuildAndroidUpdateInfoHasHTTPSUpdate(t *testing.T) {
	t.Setenv("APP_UPDATE_ALLOW_FORCE_UPDATE", "true")
	info := buildAndroidUpdateInfo(3, "1.0.3", androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		LatestVersionName: "1.0.4",
		APKURL:            "https://download.example.com/nongjiqianwen-1.0.4.apk",
		APKChecksumSHA256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		ReleaseNotes:      "修复已知问题",
		ForceUpdate:       true,
		FileSizeBytes:     12_345,
	})
	if !info.HasUpdate {
		t.Fatalf("expected update, got %#v", info)
	}
	if !info.ForceUpdate {
		t.Fatalf("expected force update")
	}
	if info.APKURL == "" {
		t.Fatalf("expected apk url")
	}
	if info.APKChecksumSHA256 == "" {
		t.Fatalf("expected apk checksum")
	}
}

func TestReadAndroidUpdateConfigRequiresExplicitEnvEnable(t *testing.T) {
	values := map[string]string{
		"APP_ANDROID_LATEST_VERSION_CODE": "4",
		"APP_ANDROID_LATEST_VERSION_NAME": "1.0.4",
		"APP_ANDROID_APK_URL":             "https://download.example.com/nongjiqiancha-1.0.4.apk",
		"APP_ANDROID_APK_SHA256":          "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		"APP_ANDROID_FILE_SIZE_BYTES":     "12345",
	}
	cfg := readAndroidUpdateConfig(func(key string) string {
		return values[key]
	})
	if cfg.Enabled {
		t.Fatalf("env fallback must not implicitly enable update without APP_ANDROID_UPDATE_ENABLED=true")
	}
	values["APP_ANDROID_UPDATE_ENABLED"] = "true"
	cfg = readAndroidUpdateConfig(func(key string) string {
		return values[key]
	})
	if !cfg.Enabled {
		t.Fatalf("env fallback should enable update when APP_ANDROID_UPDATE_ENABLED=true")
	}
}

func TestBuildAndroidUpdateInfoDoesNotForceUpdateByDefault(t *testing.T) {
	info := buildAndroidUpdateInfo(3, "1.0.3", androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		LatestVersionName: "1.0.4",
		APKURL:            "https://download.example.com/nongjiqiancha-1.0.4.apk",
		APKChecksumSHA256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		ForceUpdate:       true,
		FileSizeBytes:     12_345,
	})
	if !info.HasUpdate {
		t.Fatalf("expected update, got %#v", info)
	}
	if info.ForceUpdate {
		t.Fatalf("force update must stay disabled unless APP_UPDATE_ALLOW_FORCE_UPDATE is explicitly enabled")
	}
}

func TestBuildAndroidUpdateInfoUsesDefaultReleaseNotes(t *testing.T) {
	info := buildAndroidUpdateInfo(3, "1.0.3", androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		LatestVersionName: "1.0.4",
		APKURL:            "https://download.example.com/nongjiqiancha-1.0.4.apk",
		APKChecksumSHA256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		FileSizeBytes:     12_345,
	})
	if !info.HasUpdate {
		t.Fatalf("expected update, got %#v", info)
	}
	if info.ReleaseNotes != defaultAndroidUpdateReleaseNotes {
		t.Fatalf("release notes = %q, want %q", info.ReleaseNotes, defaultAndroidUpdateReleaseNotes)
	}
}

func TestBuildAndroidUpdateInfoRejectsNonHTTPSAPKURL(t *testing.T) {
	info := buildAndroidUpdateInfo(3, "1.0.3", androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		LatestVersionName: "1.0.4",
		APKURL:            "http://download.example.com/nongjiqianwen-1.0.4.apk",
	})
	if info.HasUpdate {
		t.Fatalf("expected invalid apk url to disable update, got %#v", info)
	}
	if info.APKURL != "" {
		t.Fatalf("apk url = %q, want empty", info.APKURL)
	}
}

func TestBuildAndroidUpdateInfoRejectsInternalTestAPKURL(t *testing.T) {
	cfg := androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		LatestVersionName: "1.0.4",
		APKURL:            "https://download.example.com/test-apks/debug/nongjiqiancha-debug-internal.apk",
		APKChecksumSHA256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		FileSizeBytes:     12_345,
	}
	info := buildAndroidUpdateInfo(3, "1.0.3", cfg)
	if info.HasUpdate {
		t.Fatalf("expected internal test apk url to disable update, got %#v", info)
	}
	if info.APKURL != "" {
		t.Fatalf("apk url = %q, want empty", info.APKURL)
	}
	if got := androidUpdateIgnoredReason(cfg); got != "invalid_apk_url" {
		t.Fatalf("ignored reason = %q, want invalid_apk_url", got)
	}
	if androidUpdateConfigValid(cfg) {
		t.Fatalf("internal test apk config must be invalid")
	}
	if androidUpdateDownloadArtifactsComplete(cfg) {
		t.Fatalf("internal test apk must not count as complete release artifact")
	}
}

func TestBuildAndroidUpdateInfoRejectsEscapedInternalTestAPKURL(t *testing.T) {
	cfg := androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		LatestVersionName: "1.0.4",
		APKURL:            "https://download.example.com/test-apks/%64ebug/nongjiqiancha-%69nternal.apk",
		APKChecksumSHA256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		FileSizeBytes:     12_345,
	}
	info := buildAndroidUpdateInfo(3, "1.0.3", cfg)
	if info.HasUpdate {
		t.Fatalf("expected escaped internal test apk url to disable update, got %#v", info)
	}
	if got := androidUpdateIgnoredReason(cfg); got != "invalid_apk_url" {
		t.Fatalf("ignored reason = %q, want invalid_apk_url", got)
	}
}

func TestBuildAndroidUpdateInfoRejectsShortLivedSignedAPKURL(t *testing.T) {
	cfg := androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		LatestVersionName: "1.0.4",
		APKURL:            "https://download.example.com/releases/nongjiqiancha-1.0.4.apk?OSSAccessKeyId=abc&Expires=1781941394&Signature=sig",
		APKChecksumSHA256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		FileSizeBytes:     12_345,
	}
	info := buildAndroidUpdateInfo(3, "1.0.3", cfg)
	if info.HasUpdate {
		t.Fatalf("expected short-lived signed apk url to disable update, got %#v", info)
	}
	if got := androidUpdateIgnoredReason(cfg); got != "invalid_apk_url" {
		t.Fatalf("ignored reason = %q, want invalid_apk_url", got)
	}
	if androidUpdateConfigValid(cfg) {
		t.Fatalf("short-lived signed apk config must be invalid")
	}
	if androidUpdateDownloadArtifactsComplete(cfg) {
		t.Fatalf("short-lived signed apk must not count as complete release artifact")
	}
}

func TestBuildAndroidUpdateInfoRequiresDownloadArtifacts(t *testing.T) {
	info := buildAndroidUpdateInfo(3, "1.0.3", androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		LatestVersionName: "1.0.4",
		APKURL:            "https://download.example.com/nongjiqianwen-1.0.4.apk",
	})
	if info.HasUpdate {
		t.Fatalf("expected missing artifacts to disable update, got %#v", info)
	}
	if info.APKURL != "" {
		t.Fatalf("apk url = %q, want empty", info.APKURL)
	}
	if got := androidUpdateIgnoredReason(androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		APKURL:            "https://download.example.com/nongjiqianwen-1.0.4.apk",
	}); got != "missing_release_artifacts" {
		t.Fatalf("ignored reason = %q, want missing_release_artifacts", got)
	}
}

func TestBuildAndroidUpdateInfoRejectsOversizedAPK(t *testing.T) {
	info := buildAndroidUpdateInfo(3, "1.0.3", androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		LatestVersionName: "1.0.4",
		APKURL:            "https://download.example.com/nongjiqiancha-1.0.4.apk",
		APKChecksumSHA256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		FileSizeBytes:     maxAndroidAPKBytes + 1,
	})
	if info.HasUpdate {
		t.Fatalf("expected oversized apk to disable update, got %#v", info)
	}
	if info.APKURL != "" {
		t.Fatalf("apk url = %q, want empty", info.APKURL)
	}
	if got := androidUpdateIgnoredReason(androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		APKURL:            "https://download.example.com/nongjiqiancha-1.0.4.apk",
		APKChecksumSHA256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		FileSizeBytes:     maxAndroidAPKBytes + 1,
	}); got != "apk_too_large" {
		t.Fatalf("ignored reason = %q, want apk_too_large", got)
	}
	if androidUpdateConfigValid(androidUpdateConfig{
		LatestVersionCode: 4,
		APKURL:            "https://download.example.com/nongjiqiancha-1.0.4.apk",
		FileSizeBytes:     maxAndroidAPKBytes + 1,
	}) {
		t.Fatalf("oversized apk config must be invalid")
	}
}

func TestBuildAndroidUpdateInfoHidesChecksumWhenNoUpdate(t *testing.T) {
	info := buildAndroidUpdateInfo(4, "1.0.4", androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 4,
		APKURL:            "https://download.example.com/nongjiqianwen-1.0.4.apk",
		APKChecksumSHA256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
	})
	if info.HasUpdate {
		t.Fatalf("expected no update, got %#v", info)
	}
	if info.APKChecksumSHA256 != "" {
		t.Fatalf("checksum = %q, want empty", info.APKChecksumSHA256)
	}
}

func TestNormalizeSHA256Hex(t *testing.T) {
	input := "01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF"
	got := normalizeSHA256Hex(input)
	want := "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
	if got != want {
		t.Fatalf("normalized sha = %q, want %q", got, want)
	}
	if normalizeSHA256Hex("not-a-sha") != "" {
		t.Fatalf("expected invalid sha to normalize empty")
	}
}

func TestAndroidUpdateEventAction(t *testing.T) {
	tests := []struct {
		name string
		cfg  androidUpdateConfig
		want string
	}{
		{name: "disabled", cfg: androidUpdateConfig{}, want: "disable"},
		{name: "publish", cfg: androidUpdateConfig{Enabled: true}, want: "publish"},
		{name: "force publish", cfg: androidUpdateConfig{Enabled: true, ForceUpdate: true}, want: "force_publish"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := androidUpdateEventAction(tt.cfg); got != tt.want {
				t.Fatalf("androidUpdateEventAction = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestAdminAppUpdateWriteConfirmationRequiresVersionCodeForEnabledRelease(t *testing.T) {
	cfg := androidUpdateConfig{
		Enabled:           true,
		LatestVersionCode: 8,
	}
	body := adminAppUpdateWriteRequest{
		Enabled:           true,
		LatestVersionCode: 8,
		Confirmation:      "7",
	}
	if got := adminAppUpdateWriteConfirmationError(body, cfg); got != "release_confirmation_required" {
		t.Fatalf("confirmation error = %q, want release_confirmation_required", got)
	}
	body.Confirmation = "8"
	if got := adminAppUpdateWriteConfirmationError(body, cfg); got != "" {
		t.Fatalf("confirmation error = %q, want empty", got)
	}
}

func TestAdminAppUpdateWriteConfirmationRequiresDisableText(t *testing.T) {
	cfg := androidUpdateConfig{Enabled: false}
	body := adminAppUpdateWriteRequest{
		Enabled:      false,
		Confirmation: "stop",
	}
	if got := adminAppUpdateWriteConfirmationError(body, cfg); got != "disable_confirmation_required" {
		t.Fatalf("confirmation error = %q, want disable_confirmation_required", got)
	}
	body.Confirmation = "停更"
	if got := adminAppUpdateWriteConfirmationError(body, cfg); got != "" {
		t.Fatalf("confirmation error = %q, want empty", got)
	}
}

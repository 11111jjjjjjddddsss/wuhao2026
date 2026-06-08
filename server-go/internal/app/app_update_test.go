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

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
		LatestVersionCode: 4,
		LatestVersionName: "1.0.4",
		APKURL:            "https://download.example.com/nongjiqianwen-1.0.4.apk",
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
}

func TestBuildAndroidUpdateInfoRejectsNonHTTPSAPKURL(t *testing.T) {
	info := buildAndroidUpdateInfo(3, "1.0.3", androidUpdateConfig{
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

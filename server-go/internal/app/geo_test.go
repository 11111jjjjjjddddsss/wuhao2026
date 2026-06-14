package app

import (
	"net/http"
	"testing"
	"time"
)

func TestParseIP2RegionNameChinaProvinceCity(t *testing.T) {
	got := parseIP2RegionName("中国|山东省|潍坊市|联通|CN")
	if got != "山东省 潍坊市" {
		t.Fatalf("got %q", got)
	}
}

func TestParseIP2RegionNameDirectCity(t *testing.T) {
	got := parseIP2RegionName("中国|北京市|北京市|电信|CN")
	if got != "北京市" {
		t.Fatalf("got %q", got)
	}
}

func TestParseIP2RegionNameForeign(t *testing.T) {
	got := parseIP2RegionName("United States|California|San Jose|xTom|US")
	if got != "United States California San Jose" {
		t.Fatalf("got %q", got)
	}
}

func TestParseIP2RegionNameUnknown(t *testing.T) {
	for _, raw := range []string{"", "0|0|0|0|0", "中国|0|0|0|CN"} {
		if got := parseIP2RegionName(raw); got != "" {
			t.Fatalf("raw %q got %q", raw, got)
		}
	}
}

func TestResolveRegionByIPReturnsUnknownWithoutConfiguredXDB(t *testing.T) {
	t.Setenv(ip2RegionV4PathEnv, "")
	t.Setenv(ip2RegionV6PathEnv, "")
	t.Setenv(ip2RegionLegacyV4PathEnv, "")
	resetIP2RegionStateForTest()

	got := ResolveRegionByIP("8.8.8.8")
	if got.Region != "未知" || got.Source != RegionSourceIP || got.Reliability != RegionUnreliable {
		t.Fatalf("unexpected context: %+v", got)
	}
}

func TestResolveRegionByIPSkipsPrivateIP(t *testing.T) {
	t.Setenv(ip2RegionV4PathEnv, "missing.xdb")
	resetIP2RegionStateForTest()

	got := ResolveRegionByIP("192.168.1.1")
	if got.Region != "未知" || got.Source != RegionSourceIP || got.Reliability != RegionUnreliable {
		t.Fatalf("unexpected context: %+v", got)
	}
}

func TestParseRegionFromHeadersRejectsUnknownRegion(t *testing.T) {
	header := http.Header{}
	header.Set("X-User-Region", "!!!")
	header.Set("X-Region-Source", "gps")
	header.Set("X-Region-Reliability", "reliable")

	if got := ParseRegionFromHeaders(header); got != nil {
		t.Fatalf("expected nil region context, got %+v", got)
	}
}

func TestParseRegionFromHeadersKeepsValidGPSRegion(t *testing.T) {
	header := http.Header{}
	header.Set("X-User-Region", "山东省 潍坊市 寿光市")
	header.Set("X-Region-Source", "gps")
	header.Set("X-Region-Reliability", "reliable")

	got := ParseRegionFromHeaders(header)
	if got == nil {
		t.Fatal("expected region context")
	}
	if got.Region != "山东省 潍坊市 寿光市" || got.Source != RegionSourceGPS || got.Reliability != RegionReliable {
		t.Fatalf("unexpected context: %+v", got)
	}
}

func TestParseRegionValuesKeepsJSONBodyRegion(t *testing.T) {
	got := ParseRegionValues("河南省 郑州市 管城回族区", "gps", "reliable")
	if got == nil {
		t.Fatal("expected region context")
	}
	if got.Region != "河南省 郑州市 管城回族区" || got.Source != RegionSourceGPS || got.Reliability != RegionReliable {
		t.Fatalf("unexpected context: %+v", got)
	}
}

func TestIP2RegionPathEnvFallback(t *testing.T) {
	t.Setenv(ip2RegionV4PathEnv, "")
	t.Setenv(ip2RegionLegacyV4PathEnv, "legacy.xdb")
	t.Setenv(ip2RegionV6PathEnv, "")
	resetIP2RegionStateForTest()

	_, err := getIP2RegionResolver()
	if err == nil {
		t.Fatal("expected missing file error")
	}
	if ip2RegionState.configKey != "legacy.xdb\x00" {
		t.Fatalf("unexpected config key %q", ip2RegionState.configKey)
	}
}

func resetIP2RegionStateForTest() {
	ip2RegionState.mu.Lock()
	defer ip2RegionState.mu.Unlock()
	if ip2RegionState.resolver != nil {
		ip2RegionState.resolver.Close()
	}
	ip2RegionState.configKey = ""
	ip2RegionState.resolver = nil
	ip2RegionState.initErr = nil
	ip2RegionState.lastAttempt = time.Time{}
}

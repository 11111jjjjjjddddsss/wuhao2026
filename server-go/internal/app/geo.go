package app

import (
	"errors"
	"net"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"
	"unicode"

	"github.com/lionsoul2014/ip2region/binding/golang/service"
)

const (
	ip2RegionV4PathEnv       = "IP2REGION_V4_XDB_PATH"
	ip2RegionV6PathEnv       = "IP2REGION_V6_XDB_PATH"
	ip2RegionLegacyV4PathEnv = "IP2REGION_XDB_PATH"
)

var ip2RegionState = struct {
	mu          sync.Mutex
	configKey   string
	resolver    *service.Ip2Region
	initErr     error
	lastAttempt time.Time
}{}

func ParseRegionFromHeaders(header http.Header) *RegionContext {
	return ParseRegionValues(
		header.Get("X-User-Region"),
		header.Get("X-Region-Source"),
		header.Get("X-Region-Reliability"),
	)
}

func ParseRegionValues(regionValue, sourceValue, reliabilityValue string) *RegionContext {
	regionRaw := strings.TrimSpace(regionValue)
	if regionRaw == "" {
		return nil
	}
	region := normalizeRegion(regionRaw)
	if region == "" || region == "未知" {
		return nil
	}

	source := RegionSourceNone
	switch RegionSource(strings.ToLower(strings.TrimSpace(sourceValue))) {
	case RegionSourceGPS:
		source = RegionSourceGPS
	case RegionSourceIP:
		source = RegionSourceIP
	case RegionSourceNone:
		source = RegionSourceNone
	}

	reliability := RegionUnreliable
	if source == RegionSourceGPS && RegionReliability(strings.ToLower(strings.TrimSpace(reliabilityValue))) == RegionReliable {
		reliability = RegionReliable
	}

	return &RegionContext{
		Region:      region,
		Source:      source,
		Reliability: reliability,
	}
}

func ResolveRegionByIP(ip string) RegionContext {
	ip = normalizeIPLiteral(ip)
	if !isPublicRegionLookupIP(ip) {
		return unknownIPRegionContext()
	}

	resolver, err := getIP2RegionResolver()
	if err != nil || resolver == nil {
		return unknownIPRegionContext()
	}

	rawRegion, err := resolver.Search(ip)
	if err != nil {
		return unknownIPRegionContext()
	}
	region := parseIP2RegionName(rawRegion)
	if region == "" {
		return unknownIPRegionContext()
	}

	return RegionContext{
		Region:      region,
		Source:      RegionSourceIP,
		Reliability: RegionUnreliable,
	}
}

func unknownIPRegionContext() RegionContext {
	return RegionContext{
		Region:      "未知",
		Source:      RegionSourceIP,
		Reliability: RegionUnreliable,
	}
}

func getIP2RegionResolver() (*service.Ip2Region, error) {
	v4Path := strings.TrimSpace(os.Getenv(ip2RegionV4PathEnv))
	if v4Path == "" {
		v4Path = strings.TrimSpace(os.Getenv(ip2RegionLegacyV4PathEnv))
	}
	v6Path := strings.TrimSpace(os.Getenv(ip2RegionV6PathEnv))
	if v4Path == "" && v6Path == "" {
		return nil, errors.New("ip2region xdb path is not configured")
	}

	configKey := v4Path + "\x00" + v6Path
	now := time.Now()

	ip2RegionState.mu.Lock()
	defer ip2RegionState.mu.Unlock()

	if ip2RegionState.resolver != nil && ip2RegionState.configKey == configKey {
		return ip2RegionState.resolver, nil
	}
	if ip2RegionState.initErr != nil && ip2RegionState.configKey == configKey && now.Sub(ip2RegionState.lastAttempt) < time.Minute {
		return nil, ip2RegionState.initErr
	}
	if ip2RegionState.resolver != nil {
		ip2RegionState.resolver.Close()
		ip2RegionState.resolver = nil
	}

	ip2RegionState.configKey = configKey
	ip2RegionState.lastAttempt = now
	resolver, err := newIP2RegionResolver(v4Path, v6Path)
	if err != nil {
		ip2RegionState.initErr = err
		return nil, err
	}

	ip2RegionState.resolver = resolver
	ip2RegionState.initErr = nil
	return resolver, nil
}

func newIP2RegionResolver(v4Path string, v6Path string) (*service.Ip2Region, error) {
	var v4Config *service.Config
	var err error
	if v4Path != "" {
		v4Config, err = service.NewV4Config(service.BufferCache, v4Path, 8)
		if err != nil {
			return nil, err
		}
	}

	var v6Config *service.Config
	if v6Path != "" {
		v6Config, err = service.NewV6Config(service.BufferCache, v6Path, 8)
		if err != nil {
			return nil, err
		}
	}

	return service.NewIp2Region(v4Config, v6Config)
}

func isPublicRegionLookupIP(ip string) bool {
	parsed := net.ParseIP(strings.TrimSpace(ip))
	if parsed == nil {
		return false
	}
	return !parsed.IsLoopback() &&
		!parsed.IsPrivate() &&
		!parsed.IsUnspecified() &&
		!parsed.IsMulticast() &&
		!parsed.IsLinkLocalUnicast() &&
		!parsed.IsLinkLocalMulticast()
}

func parseIP2RegionName(raw string) string {
	parts := strings.Split(strings.TrimSpace(raw), "|")
	if len(parts) == 0 {
		return ""
	}

	cleanPart := func(index int) string {
		if index >= len(parts) {
			return ""
		}
		value := strings.TrimSpace(parts[index])
		switch strings.ToLower(value) {
		case "", "0", "unknown", "null", "none", "内网ip", "内网":
			return ""
		default:
			return value
		}
	}

	country := cleanPart(0)
	province := cleanPart(1)
	city := cleanPart(2)

	if country == "" && province == "" && city == "" {
		return ""
	}
	if country == "中国" {
		switch {
		case province != "" && city != "" && province != city:
			return normalizeRegion(province + " " + city)
		case city != "":
			return normalizeRegion(city)
		case province != "":
			return normalizeRegion(province)
		default:
			return ""
		}
	}

	values := make([]string, 0, 3)
	for _, value := range []string{country, province, city} {
		if value != "" && (len(values) == 0 || values[len(values)-1] != value) {
			values = append(values, value)
		}
	}
	if len(values) == 0 {
		return ""
	}
	return normalizeRegion(strings.Join(values, " "))
}

func FormatShanghaiNowToSecond(loc *time.Location, date time.Time) string {
	if loc == nil {
		loc = time.FixedZone("Asia/Shanghai", 8*60*60)
	}
	return date.In(loc).Format("2006-01-02 15:04:05")
}

func FormatShanghaiUnixMilliToSecond(loc *time.Location, ts int64) string {
	if ts <= 0 {
		return ""
	}
	return FormatShanghaiNowToSecond(loc, time.UnixMilli(ts))
}

func normalizeRegion(raw string) string {
	var builder strings.Builder
	builder.Grow(len(raw))
	for _, r := range raw {
		switch {
		case unicode.In(r, unicode.Han), unicode.IsLetter(r), unicode.IsDigit(r), r == ' ', r == '-', r == '/':
			builder.WriteRune(r)
		default:
			builder.WriteRune(' ')
		}
	}

	normalized := strings.Join(strings.Fields(builder.String()), " ")
	if normalized == "" {
		return "未知"
	}
	runes := []rune(normalized)
	if len(runes) > 64 {
		return string(runes[:64])
	}
	return normalized
}

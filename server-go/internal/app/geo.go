package app

import (
	"net/http"
	"strings"
	"time"
	"unicode"
)

func ParseRegionFromHeaders(header http.Header) *RegionContext {
	regionRaw := strings.TrimSpace(header.Get("X-User-Region"))
	if regionRaw == "" {
		return nil
	}

	source := RegionSourceNone
	switch RegionSource(strings.ToLower(strings.TrimSpace(header.Get("X-Region-Source")))) {
	case RegionSourceGPS:
		source = RegionSourceGPS
	case RegionSourceIP:
		source = RegionSourceIP
	case RegionSourceNone:
		source = RegionSourceNone
	}

	reliability := RegionUnreliable
	if RegionReliability(strings.ToLower(strings.TrimSpace(header.Get("X-Region-Reliability")))) == RegionReliable {
		reliability = RegionReliable
	}
	if source == RegionSourceGPS && reliability != RegionReliable {
		reliability = RegionUnreliable
	}

	return &RegionContext{
		Region:      normalizeRegion(regionRaw),
		Source:      source,
		Reliability: reliability,
	}
}

func ResolveRegionByIP(_ string) RegionContext {
	return RegionContext{
		Region:      "未知",
		Source:      RegionSourceIP,
		Reliability: RegionUnreliable,
	}
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

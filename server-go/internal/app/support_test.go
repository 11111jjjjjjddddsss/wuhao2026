package app

import (
	"database/sql"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"strings"
	"testing"
	"time"
)

func TestNormalizeSupportMessagePayloadAllowsTextImageAndImageOnly(t *testing.T) {
	body, images, validationError := normalizeSupportMessagePayload("  反馈内容  ", []string{
		"https://example.com/uploads/a.jpg",
	})
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if body != "反馈内容" {
		t.Fatalf("body = %q, want trimmed text", body)
	}
	if len(images) != 1 {
		t.Fatalf("images len = %d, want 1", len(images))
	}

	body, images, validationError = normalizeSupportMessagePayload(" ", []string{
		"https://example.com/uploads/a.jpg",
	})
	if validationError != "" {
		t.Fatalf("image-only message should pass, got %s", validationError)
	}
	if body != "" || len(images) != 1 {
		t.Fatalf("image-only payload mismatch: body=%q images=%v", body, images)
	}
}

func TestNormalizeSupportMessagePayloadRejectsInvalidInput(t *testing.T) {
	tests := []struct {
		name   string
		body   string
		images []string
		want   string
	}{
		{
			name: "empty",
			want: "body or images required",
		},
		{
			name:   "too many images",
			images: []string{"1", "2", "3", "4", "5"},
			want:   "single request supports up to 4 images",
		},
		{
			name: "body too long",
			body: strings.Repeat("字", supportMessageMaxRunes+1),
			want: "body too long",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, _, validationError := normalizeSupportMessagePayload(tt.body, tt.images)
			if validationError != tt.want {
				t.Fatalf("validationError = %q, want %q", validationError, tt.want)
			}
		})
	}
}

func TestSupportImageURLsJSON(t *testing.T) {
	raw, err := supportImageURLsJSON([]string{
		"https://example.com/uploads/a.jpg",
		"",
		"https://example.com/uploads/b.jpg",
	})
	if err != nil {
		t.Fatalf("supportImageURLsJSON failed: %v", err)
	}
	encoded, ok := raw.(string)
	if !ok {
		t.Fatalf("raw = %#v, want string", raw)
	}

	var decoded []string
	if err := json.Unmarshal([]byte(encoded), &decoded); err != nil {
		t.Fatalf("decode image urls json: %v", err)
	}
	if len(decoded) != 2 {
		t.Fatalf("decoded len = %d, want 2", len(decoded))
	}

	empty, err := supportImageURLsJSON(nil)
	if err != nil {
		t.Fatalf("empty supportImageURLsJSON failed: %v", err)
	}
	if empty != nil {
		t.Fatalf("empty = %#v, want nil", empty)
	}
}

func TestSupportMessageRateLimitKeyHashesSensitiveInputs(t *testing.T) {
	t.Setenv("APP_SECRET", "test-secret")
	key := supportMessageRateLimitKey("acct_sensitive_user", "203.0.113.9")
	if key == "" || strings.Contains(key, "acct_sensitive_user") || strings.Contains(key, "203.0.113.9") {
		t.Fatalf("supportMessageRateLimitKey leaked sensitive input: %q", key)
	}
	if !strings.HasPrefix(key, "support_message:") {
		t.Fatalf("supportMessageRateLimitKey prefix mismatch: %q", key)
	}
}

func TestSupportMessageRateLimiterUsesEnv(t *testing.T) {
	t.Setenv("SUPPORT_MESSAGE_RATE_LIMIT_WINDOW_SECONDS", "30")
	t.Setenv("SUPPORT_MESSAGE_RATE_LIMIT_MAX_HITS", "2")
	t.Setenv("SUPPORT_MESSAGE_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", "45")

	limiter, ok := newSupportMessageRateLimiter(nil).(*chatRateLimiter)
	if !ok {
		t.Fatalf("newSupportMessageRateLimiter returned %T, want *chatRateLimiter fallback", newSupportMessageRateLimiter(nil))
	}
	if limiter.window != 30*time.Second || limiter.maxHits != 2 || limiter.pruneInterval != 45*time.Second {
		t.Fatalf("support message limiter config mismatch: window=%s max=%d prune=%s", limiter.window, limiter.maxHits, limiter.pruneInterval)
	}
}

func TestShouldCreateSupportAutoReply(t *testing.T) {
	now := time.Unix(2*24*60*60, 0).UnixMilli()
	if !shouldCreateSupportAutoReply(nil, now, supportAutoReplyBody) {
		t.Fatalf("new support conversation should create auto reply")
	}

	recentUserMessage := &SupportMessage{
		SenderType: "user",
		CreatedAt:  now - int64((defaultSupportAutoReplyCooldown-time.Millisecond)/time.Millisecond),
	}
	if shouldCreateSupportAutoReply(recentUserMessage, now, supportAutoReplyBody) {
		t.Fatalf("recent user follow-up should not create auto reply")
	}

	oldUserMessage := &SupportMessage{
		SenderType: "user",
		CreatedAt:  now - int64(defaultSupportAutoReplyCooldown/time.Millisecond),
	}
	if !shouldCreateSupportAutoReply(oldUserMessage, now, supportAutoReplyBody) {
		t.Fatalf("old user conversation should create auto reply")
	}

	latestAdminMessage := &SupportMessage{
		SenderType: "admin",
		CreatedAt:  now - int64((defaultSupportAutoReplyCooldown+time.Hour)/time.Millisecond),
	}
	if shouldCreateSupportAutoReply(latestAdminMessage, now, supportAutoReplyBody) {
		t.Fatalf("latest admin message should not create auto reply")
	}

	futureUserMessage := &SupportMessage{
		SenderType: "user",
		CreatedAt:  now + 1,
	}
	if shouldCreateSupportAutoReply(futureUserMessage, now, supportAutoReplyBody) {
		t.Fatalf("future latest message should not create auto reply")
	}

	recentUserForGreeting := &SupportMessage{
		SenderType: "user",
		CreatedAt:  now - int64((defaultSupportFAQAutoReplyCooldown-time.Millisecond)/time.Millisecond),
	}
	if shouldCreateSupportAutoReply(recentUserForGreeting, now, supportGreetingAutoReplyBody) {
		t.Fatalf("very recent greeting follow-up should not create auto reply")
	}
	oldEnoughUserForGreeting := &SupportMessage{
		SenderType: "user",
		CreatedAt:  now - int64(defaultSupportFAQAutoReplyCooldown/time.Millisecond),
	}
	if !shouldCreateSupportAutoReply(oldEnoughUserForGreeting, now, supportGreetingAutoReplyBody) {
		t.Fatalf("greeting follow-up after cooldown should create auto reply")
	}

	recentSameSystemReply := &SupportMessage{
		SenderType: "system",
		Body:       supportGreetingAutoReplyBody,
		CreatedAt:  now - int64((defaultSupportAutoReplyRepeatCooldown-time.Millisecond)/time.Millisecond),
	}
	if shouldCreateSupportAutoReply(recentSameSystemReply, now, supportGreetingAutoReplyBody) {
		t.Fatalf("same system reply should respect repeat cooldown")
	}

	recentSameRegularSystemReply := &SupportMessage{
		SenderType: "system",
		Body:       supportAutoReplyBody,
		CreatedAt:  now - int64((defaultSupportAutoReplyCooldown-time.Millisecond)/time.Millisecond),
	}
	if shouldCreateSupportAutoReply(recentSameRegularSystemReply, now, supportAutoReplyBody) {
		t.Fatalf("regular submit confirmation should respect 24h cooldown even when latest is system")
	}
	if shouldCreateSupportAutoReply(recentSameRegularSystemReply, now, supportImageOnlyAutoReplyBody) {
		t.Fatalf("generic screenshot confirmation should share 24h cooldown with regular submit confirmation")
	}
	oldSameRegularSystemReply := &SupportMessage{
		SenderType: "system",
		Body:       supportAutoReplyBody,
		CreatedAt:  now - int64(defaultSupportAutoReplyCooldown/time.Millisecond),
	}
	if !shouldCreateSupportAutoReply(oldSameRegularSystemReply, now, supportAutoReplyBody) {
		t.Fatalf("regular submit confirmation should be allowed after 24h")
	}
}

func TestSupportAutoReplyBodyFor(t *testing.T) {
	if got := supportAutoReplyBodyFor("  你好！ ", nil); got != supportGreetingAutoReplyBody {
		t.Fatalf("greeting auto reply = %q, want greeting body", got)
	}
	if got := supportAutoReplyBodyFor("你好", []string{"https://example.com/uploads/a.jpg"}); got != supportGreetingAutoReplyBody {
		t.Fatalf("text should be classified before image fallback: got %q, want greeting body", got)
	}
	if got := supportAutoReplyBodyFor("", []string{"https://example.com/uploads/a.jpg"}); got != supportImageOnlyAutoReplyBody {
		t.Fatalf("image-only feedback auto reply = %q, want image-only body", got)
	}
	if got := supportAutoReplyBodyFor("见图", []string{"https://example.com/uploads/a.jpg"}); got != supportImageOnlyAutoReplyBody {
		t.Fatalf("image-only style text with image = %q, want image-only body", got)
	}
	if got := supportAutoReplyBodyFor("登录失败截图", []string{"https://example.com/uploads/a.jpg"}); got != supportAutoReplyBody {
		t.Fatalf("specific app issue = %q, want regular body", got)
	}
	if got := supportAutoReplyBodyFor("检查更新失败了", nil); got != supportAutoReplyBody {
		t.Fatalf("update issue auto reply = %q, want regular body", got)
	}
	if got := supportAutoReplyBodyFor("小麦叶片照片发黄", nil); got != supportAutoReplyBody {
		t.Fatalf("agri question in support page = %q, want regular body", got)
	}
	if got := supportAutoReplyBodyFor("无法提交反馈", nil); got != supportAutoReplyBody {
		t.Fatalf("regular feedback auto reply = %q, want regular body", got)
	}
}

func TestSupportConversationNeedsReply(t *testing.T) {
	tests := []struct {
		name string
		in   sql.NullString
		want bool
	}{
		{
			name: "latest user",
			in:   sql.NullString{String: "user", Valid: true},
			want: true,
		},
		{
			name: "latest admin",
			in:   sql.NullString{String: "admin", Valid: true},
			want: false,
		},
		{
			name: "no non-system message",
			in:   sql.NullString{},
			want: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := supportConversationNeedsReply(tt.in); got != tt.want {
				t.Fatalf("supportConversationNeedsReply(%#v) = %v, want %v", tt.in, got, tt.want)
			}
		})
	}
}

func TestParseSupportConversationQuery(t *testing.T) {
	filter, validationError := parseSupportConversationQuery(url.Values{
		"since_ms": {"123"},
		"limit":    {"999"},
	}, time.UnixMilli(10_000))
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if filter.SinceMs != 123 || filter.Limit != maxSupportConversationListLimit {
		t.Fatalf("filter mismatch: %#v", filter)
	}

	defaultFilter, validationError := parseSupportConversationQuery(url.Values{}, time.UnixMilli(10_000))
	if validationError != "" {
		t.Fatalf("unexpected default validation error: %s", validationError)
	}
	if defaultFilter.Limit != defaultSupportConversationListLimit ||
		defaultFilter.SinceMs != time.UnixMilli(10_000).Add(-defaultSupportConversationSinceDuration).UnixMilli() {
		t.Fatalf("default filter mismatch: %#v", defaultFilter)
	}
}

func TestParseSupportConversationQueryRejectsInvalidFilters(t *testing.T) {
	tests := []struct {
		name   string
		values url.Values
		want   string
	}{
		{name: "invalid since", values: url.Values{"since_ms": {"-1"}}, want: "invalid_since_ms"},
		{name: "invalid limit", values: url.Values{"limit": {"0"}}, want: "invalid_limit"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, got := parseSupportConversationQuery(tt.values, time.Now())
			if got != tt.want {
				t.Fatalf("validation error = %q, want %q", got, tt.want)
			}
		})
	}
}

func TestRequireSupportAdminSecret(t *testing.T) {
	server := &Server{
		logger:                slog.New(slog.NewTextHandler(os.Stdout, nil)),
		internalSecretLimiter: newChatRateLimiterWithConfig(rateLimitConfig{Window: time.Minute, MaxHits: 100, PruneInterval: time.Minute}),
	}

	t.Setenv("SUPPORT_ADMIN_SECRET", "")
	req := httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	rec := httptest.NewRecorder()
	if server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected missing secret to reject")
	}
	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("missing secret status = %d, want %d", rec.Code, http.StatusServiceUnavailable)
	}

	t.Setenv("SUPPORT_ADMIN_SECRET", "secret")
	req = httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.Header.Set("X-Support-Admin-Secret", "secret")
	rec = httptest.NewRecorder()
	if !server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected header secret to pass")
	}

	req = httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.Header.Set("Authorization", "Bearer secret")
	rec = httptest.NewRecorder()
	if !server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected bearer secret to pass")
	}

	req = httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.Header.Set("X-Support-Admin-Secret", "wrong")
	rec = httptest.NewRecorder()
	if server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected wrong secret to reject")
	}
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("wrong secret status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}
}

func TestRequireSupportAdminSecretRateLimitsByIP(t *testing.T) {
	t.Setenv("SUPPORT_ADMIN_SECRET", "secret")
	server := &Server{
		logger:                slog.New(slog.NewTextHandler(os.Stdout, nil)),
		internalSecretLimiter: newChatRateLimiterWithConfig(rateLimitConfig{Window: time.Minute, MaxHits: 1, PruneInterval: time.Minute}),
	}

	req := httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.RemoteAddr = "203.0.113.9:1234"
	req.Header.Set("X-Support-Admin-Secret", "wrong")
	rec := httptest.NewRecorder()
	if server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected wrong secret to reject")
	}
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("first status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}

	req = httptest.NewRequest(http.MethodGet, "/internal/support/messages", nil)
	req.RemoteAddr = "203.0.113.9:1234"
	req.Header.Set("X-Support-Admin-Secret", "secret")
	rec = httptest.NewRecorder()
	if server.requireSupportAdminSecret(rec, req) {
		t.Fatalf("expected second attempt from same IP to be rate limited")
	}
	if rec.Code != http.StatusTooManyRequests {
		t.Fatalf("second status = %d, want %d", rec.Code, http.StatusTooManyRequests)
	}
}

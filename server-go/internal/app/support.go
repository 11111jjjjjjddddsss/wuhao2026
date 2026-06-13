package app

import (
	"context"
	"crypto/sha1"
	"crypto/subtle"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/redis/go-redis/v9"
)

const (
	supportMessageMaxRunes                      = 2000
	supportMessageListLimit                     = 100
	defaultSupportConversationListLimit         = 100
	maxSupportConversationListLimit             = 200
	defaultSupportConversationSinceDuration     = 30 * 24 * time.Hour
	defaultSupportMessageRateLimitWindow        = 10 * time.Minute
	defaultSupportMessageRateLimitMaxHits       = 20
	defaultSupportMessageRateLimitPruneInterval = 10 * time.Minute
	defaultSupportAutoReplyCooldown             = 24 * time.Hour
	defaultSupportAutoReplyRepeatCooldown       = 5 * time.Minute
	defaultSupportFAQAutoReplyCooldown          = time.Minute
	supportAutoReplyBody                        = "已收到您的反馈。为便于客服核实，请补充发生时间、页面提示或相关截图；后续回复会在本页显示。农业技术咨询可返回主聊天页继续提问。"
	supportImageOnlyAutoReplyBody               = "已收到您上传的图片。请补充具体问题、发生时间或页面提示；后续回复会在本页显示。"
	supportGreetingAutoReplyBody                = "您好，请说明您遇到的问题或反馈内容，客服会在本页跟进回复。"
)

var errSupportMessageBusy = errors.New("support message busy")

type SupportMessage struct {
	ID           int64    `json:"id"`
	UserID       string   `json:"user_id,omitempty"`
	SenderType   string   `json:"sender_type"`
	Body         string   `json:"body"`
	ImageURLs    []string `json:"image_urls,omitempty"`
	CreatedAt    int64    `json:"created_at"`
	ReadByUserAt *int64   `json:"read_by_user_at,omitempty"`
}

type SupportSummary struct {
	UnreadCount   int             `json:"unread_count"`
	LatestMessage *SupportMessage `json:"latest_message,omitempty"`
}

type SupportConversationQuery struct {
	SinceMs int64  `json:"since_ms"`
	Limit   int    `json:"limit"`
	Status  string `json:"status,omitempty"`
	Query   string `json:"query,omitempty"`
}

type SupportConversationEntry struct {
	UserID            string         `json:"user_id"`
	PhoneMask         string         `json:"phone_mask,omitempty"`
	PhoneCiphertext   string         `json:"-"`
	PhoneHash         string         `json:"-"`
	LatestMessage     SupportMessage `json:"latest_message"`
	MessageCount      int            `json:"message_count"`
	UnreadByUserCount int            `json:"unread_by_user_count"`
	NeedsReply        bool           `json:"needs_reply"`
	Status            string         `json:"status"`
	AssignedTo        string         `json:"assigned_to,omitempty"`
	Note              string         `json:"note,omitempty"`
	LatestUserAt      *int64         `json:"latest_user_message_at,omitempty"`
	LatestAdminAt     *int64         `json:"latest_admin_message_at,omitempty"`
	ClosedAt          *int64         `json:"closed_at,omitempty"`
	UpdatedAt         int64          `json:"updated_at"`
}

type supportMessageRequest struct {
	Body   string   `json:"body"`
	Images []string `json:"images"`
}

type supportReadRequest struct {
	LastSeenMessageID int64 `json:"last_seen_message_id"`
}

type supportAdminMessageRequest struct {
	UserID string   `json:"user_id"`
	Body   string   `json:"body"`
	Images []string `json:"images"`
}

type supportConversationStatusRequest struct {
	UserID string `json:"user_id"`
	Status string `json:"status"`
	Note   string `json:"note"`
}

func (s *Server) handleSupportSummary(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if err := s.store.EnsureUser(r.Context(), auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	summary, err := s.store.GetSupportSummary(r.Context(), auth.UserID)
	if err != nil {
		s.logger.Error("get support summary failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, summary)
}

func (s *Server) handleSupportMessages(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if err := s.store.EnsureUser(r.Context(), auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	messages, err := s.store.ListSupportMessages(r.Context(), auth.UserID, supportMessageListLimit)
	if err != nil {
		s.logger.Error("list support messages failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"messages": messages})
}

func (s *Server) handleCreateSupportMessage(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if s.supportMessageLimiter != nil {
		limitKey := supportMessageRateLimitKey(auth.UserID, GetClientIP(r))
		if allowed, retryAfter := s.supportMessageLimiter.Consume(limitKey, time.Now()); !allowed {
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":               "rate_limited",
				"retry_after_seconds": retryAfter,
			})
			return
		}
	}
	var body supportMessageRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	normalized, imageURLs, validationError := normalizeSupportMessagePayload(body.Body, body.Images)
	if validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if validationError := s.validateSupportImageURLs(r, imageURLs); validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if err := s.store.EnsureUser(r.Context(), auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	nowMs := time.Now().UnixMilli()
	replyBody := supportAutoReplyBodyFor(normalized, imageURLs)
	message, autoReply, err := s.store.CreateUserSupportMessageWithAutoReply(r.Context(), auth.UserID, normalized, imageURLs, nowMs, replyBody)
	if err != nil {
		if errors.Is(err, errSupportMessageBusy) {
			s.writeError(w, http.StatusConflict, "support_message_in_progress")
			return
		}
		s.logger.Error("create support message failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"message":    message,
		"auto_reply": autoReply,
	})
}

func (s *Server) handleMarkSupportRead(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	var body supportReadRequest
	if err := decodeJSONBody(r, &body); err != nil && err != io.EOF {
		s.writeJSONDecodeError(w, err)
		return
	}
	if err := s.store.MarkSupportMessagesRead(r.Context(), auth.UserID, time.Now().UnixMilli(), body.LastSeenMessageID); err != nil {
		s.logger.Error("mark support messages read failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleInternalSupportMessages(w http.ResponseWriter, r *http.Request) {
	if !s.requireSupportAdminSecret(w, r) {
		return
	}
	userID := strings.TrimSpace(r.URL.Query().Get("user_id"))
	if userID == "" {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.list", "support_messages", "", "", false, http.StatusBadRequest, map[string]any{"error_code": "user_id required"})
		s.writeError(w, http.StatusBadRequest, "user_id required")
		return
	}
	messages, err := s.store.ListSupportMessages(r.Context(), userID, supportMessageListLimit)
	if err != nil {
		s.logger.Error("internal list support messages failed", "userId", userID, "error", err)
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.list", "support_messages", "", userID, false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.list", "support_messages", "", userID, true, http.StatusOK, map[string]any{"row_count": len(messages)})
	s.writeJSON(w, http.StatusOK, map[string]any{"messages": messages})
}

func (s *Server) handleInternalSupportConversations(w http.ResponseWriter, r *http.Request) {
	if !s.requireSupportAdminSecret(w, r) {
		return
	}
	filter, validationError := parseSupportConversationQuery(r.URL.Query(), time.Now())
	if validationError != "" {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.conversations.list", "support_messages", "", "", false, http.StatusBadRequest, map[string]any{"error_code": validationError})
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	conversations, err := s.store.ListSupportConversations(r.Context(), filter)
	if err != nil {
		s.logger.Error("internal list support conversations failed", "error", err)
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.conversations.list", "support_messages", "", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.conversations.list", "support_messages", "", "", true, http.StatusOK, map[string]any{
		"limit":     filter.Limit,
		"since_ms":  filter.SinceMs,
		"row_count": len(conversations),
	})
	s.writeJSON(w, http.StatusOK, map[string]any{
		"conversations": conversations,
		"filter":        filter,
	})
}

func (s *Server) handleInternalCreateSupportMessage(w http.ResponseWriter, r *http.Request) {
	if !s.requireSupportAdminSecret(w, r) {
		return
	}
	var body supportAdminMessageRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", "", "", false, http.StatusBadRequest, map[string]any{"error_code": "invalid_json"})
		s.writeJSONDecodeError(w, err)
		return
	}
	userID := strings.TrimSpace(body.UserID)
	if userID == "" {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", "", "", false, http.StatusBadRequest, map[string]any{"error_code": "user_id required"})
		s.writeError(w, http.StatusBadRequest, "user_id required")
		return
	}
	normalized, imageURLs, validationError := normalizeAdminSupportMessagePayload(body.Body, body.Images)
	if validationError != "" {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", "", userID, false, http.StatusBadRequest, map[string]any{"error_code": validationError})
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if validationError := s.validateSupportImageURLs(r, imageURLs); validationError != "" {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", "", userID, false, http.StatusBadRequest, map[string]any{"error_code": validationError})
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	existingMessages, err := s.store.ListSupportMessages(r.Context(), userID, 1)
	if err != nil {
		s.logger.Error("check support conversation before admin reply failed", "userId", userID, "error", err)
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", "", userID, false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if len(existingMessages) == 0 {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", "", userID, false, http.StatusNotFound, map[string]any{"error_code": "support conversation not found"})
		s.writeError(w, http.StatusNotFound, "support conversation not found")
		return
	}
	message, err := s.store.CreateSupportMessage(r.Context(), userID, "admin", normalized, imageURLs, time.Now().UnixMilli())
	if err != nil {
		s.logger.Error("internal create support message failed", "userId", userID, "error", err)
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", "", userID, false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", strconv.FormatInt(message.ID, 10), userID, true, http.StatusOK, map[string]any{
		"body_runes":  utf8.RuneCountInString(normalized),
		"media_count": len(imageURLs),
	})
	s.writeJSON(w, http.StatusOK, map[string]any{"message": message})
}

func (s *Server) requireSupportAdminSecret(w http.ResponseWriter, r *http.Request) bool {
	if !s.consumeInternalSecretRateLimit(w, r, "support_admin") {
		return false
	}
	secret := strings.TrimSpace(os.Getenv("SUPPORT_ADMIN_SECRET"))
	if secret == "" {
		s.writeError(w, http.StatusServiceUnavailable, "SUPPORT_ADMIN_NOT_CONFIGURED")
		return false
	}
	provided := strings.TrimSpace(r.Header.Get("X-Support-Admin-Secret"))
	if provided == "" {
		authHeader := strings.TrimSpace(r.Header.Get("Authorization"))
		if strings.HasPrefix(authHeader, "Bearer ") {
			provided = strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))
		}
	}
	if provided == "" || len(provided) != len(secret) || subtle.ConstantTimeCompare([]byte(provided), []byte(secret)) != 1 {
		s.writeError(w, http.StatusUnauthorized, "unauthorized")
		return false
	}
	return true
}

func normalizeSupportMessagePayload(raw string, images []string) (string, []string, string) {
	body := strings.TrimSpace(raw)
	imageURLs := normalizeImages(images)
	if utf8.RuneCountInString(body) > supportMessageMaxRunes {
		return "", nil, "body too long"
	}
	if len(imageURLs) > 4 {
		return "", nil, "single request supports up to 4 images"
	}
	if body == "" && len(imageURLs) == 0 {
		return "", nil, "body or images required"
	}
	return body, imageURLs, ""
}

func normalizeAdminSupportMessagePayload(raw string, images []string) (string, []string, string) {
	body, imageURLs, validationError := normalizeSupportMessagePayload(raw, images)
	if validationError != "" {
		return "", nil, validationError
	}
	if giftCardTextLooksSensitive(body) {
		return "", nil, "body_contains_sensitive_value"
	}
	return body, imageURLs, ""
}

func shouldCreateSupportAutoReply(latest *SupportMessage, nowMs int64, replyBody string) bool {
	if latest == nil {
		return true
	}
	if latest.CreatedAt <= 0 || nowMs < latest.CreatedAt || latest.SenderType == "admin" {
		return false
	}
	elapsed := time.Duration(nowMs-latest.CreatedAt) * time.Millisecond
	if latest.SenderType == "system" {
		if isGenericSupportAutoReplyBody(replyBody) && isGenericSupportAutoReplyBody(latest.Body) {
			return elapsed >= defaultSupportAutoReplyCooldown
		}
		return latest.Body != replyBody || elapsed >= defaultSupportAutoReplyRepeatCooldown
	}
	if latest.SenderType != "user" {
		return false
	}
	if !isGenericSupportAutoReplyBody(replyBody) {
		return elapsed >= defaultSupportFAQAutoReplyCooldown
	}
	return elapsed >= defaultSupportAutoReplyCooldown
}

func supportAutoReplyBodyFor(body string, imageURLs []string) string {
	normalized := normalizeSupportAutoReplyText(body)
	if isShortSupportGreeting(normalized) {
		return supportGreetingAutoReplyBody
	}
	if normalized == "" && len(imageURLs) > 0 {
		return supportImageOnlyAutoReplyBody
	}
	if len(imageURLs) > 0 && isImageOnlySupportText(normalized) {
		return supportImageOnlyAutoReplyBody
	}
	return supportAutoReplyBody
}

func isGenericSupportAutoReplyBody(body string) bool {
	return body == supportAutoReplyBody || body == supportImageOnlyAutoReplyBody
}

func isImageOnlySupportText(normalized string) bool {
	switch normalized {
	case "截图", "见图", "看图", "如图", "见截图", "看截图", "图片", "照片", "已发图", "我发图了":
		return true
	default:
		return false
	}
}

func normalizeSupportAutoReplyText(raw string) string {
	normalized := strings.ToLower(strings.TrimSpace(raw))
	normalized = strings.Trim(normalized, " \t\r\n,.，。!！?？~～、")
	normalized = strings.ReplaceAll(normalized, " ", "")
	return normalized
}

func isShortSupportGreeting(normalized string) bool {
	switch normalized {
	case "你好", "你好啊", "您好", "您好啊", "在吗", "在不在", "有人吗", "有人在吗", "客服", "客服在吗", "hi", "hello", "hey":
		return true
	default:
		return false
	}
}

func supportTextContainsAny(text string, keywords ...string) bool {
	for _, keyword := range keywords {
		if strings.Contains(text, strings.ToLower(keyword)) {
			return true
		}
	}
	return false
}

func newSupportMessageRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("SUPPORT_MESSAGE_RATE_LIMIT_WINDOW_SECONDS", defaultSupportMessageRateLimitWindow),
		MaxHits:       envIntWithDefault("SUPPORT_MESSAGE_RATE_LIMIT_MAX_HITS", defaultSupportMessageRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("SUPPORT_MESSAGE_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultSupportMessageRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiterFailOpen(redisClient, config, redisRateLimitPrefix, defaultSupportMessageRateLimitWindow, defaultSupportMessageRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(config)
}

func supportMessageRateLimitKey(userID string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	return "support_message:" + rateLimitHash(userID, secret) + ":" + rateLimitHash(ip, secret)
}

func parseSupportConversationQuery(values url.Values, now time.Time) (SupportConversationQuery, string) {
	filter := SupportConversationQuery{
		SinceMs: now.Add(-defaultSupportConversationSinceDuration).UnixMilli(),
		Limit:   defaultSupportConversationListLimit,
	}
	if rawSince := strings.TrimSpace(values.Get("since_ms")); rawSince != "" {
		since, err := strconv.ParseInt(rawSince, 10, 64)
		if err != nil || since < 0 {
			return SupportConversationQuery{}, "invalid_since_ms"
		}
		filter.SinceMs = since
	}
	if rawLimit := strings.TrimSpace(values.Get("limit")); rawLimit != "" {
		limit, err := strconv.Atoi(rawLimit)
		if err != nil || limit <= 0 {
			return SupportConversationQuery{}, "invalid_limit"
		}
		if limit > maxSupportConversationListLimit {
			limit = maxSupportConversationListLimit
		}
		filter.Limit = limit
	}
	filter.Status = normalizeSupportConversationStatus(values.Get("status"))
	filter.Query = truncateRunes(strings.TrimSpace(values.Get("query")), 128)
	return filter, ""
}

func (s *Store) GetSupportSummary(ctx context.Context, userID string) (*SupportSummary, error) {
	var unread int
	if err := s.db.QueryRowContext(
		ctx,
		`SELECT COUNT(*)
		   FROM support_messages
		  WHERE user_id = ?
		    AND sender_type IN ('admin', 'system')
		    AND read_by_user_at IS NULL`,
		userID,
	).Scan(&unread); err != nil {
		return nil, err
	}
	latest, err := s.getLatestSupportMessage(ctx, userID)
	if err != nil {
		return nil, err
	}
	return &SupportSummary{
		UnreadCount:   unread,
		LatestMessage: latest,
	}, nil
}

func (s *Store) ListSupportMessages(ctx context.Context, userID string, limit int) ([]SupportMessage, error) {
	if limit <= 0 || limit > 200 {
		limit = supportMessageListLimit
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT id, user_id, sender_type, body, image_urls_json, created_at, read_by_user_at
		   FROM support_messages
		  WHERE user_id = ?
		  ORDER BY created_at DESC, id DESC
		  LIMIT ?`,
		userID,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	messages := make([]SupportMessage, 0, limit)
	for rows.Next() {
		message, err := scanSupportMessage(rows)
		if err != nil {
			return nil, err
		}
		messages = append(messages, message)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	for left, right := 0, len(messages)-1; left < right; left, right = left+1, right-1 {
		messages[left], messages[right] = messages[right], messages[left]
	}
	return messages, nil
}

func (s *Store) ListSupportConversations(ctx context.Context, filter SupportConversationQuery) ([]SupportConversationEntry, error) {
	if filter.Limit <= 0 || filter.Limit > maxSupportConversationListLimit {
		filter.Limit = defaultSupportConversationListLimit
	}
	query := `SELECT *
		 FROM (
		   SELECT
		     latest_message.user_id,
		     accounts.phone_mask,
		     accounts.phone_ciphertext,
		     accounts.phone_hash,
		     latest_message.id,
		     latest_message.sender_type,
		     latest_message.body,
		     latest_message.image_urls_json,
		     latest_message.created_at,
		     latest_message.read_by_user_at,
		     latest_non_system_summary.sender_type AS latest_non_system_sender_type,
		     COALESCE(message_counts.message_count, 0) AS message_count,
		     COALESCE(unread_counts.unread_count, 0) AS unread_count,
		     CASE
		       WHEN conversations.status = 'closed' AND latest_user_summary.latest_user_at > COALESCE(conversations.closed_at, 0) THEN 'open'
		       WHEN conversations.status IN ('open', 'replied', 'closed') THEN conversations.status
		       WHEN latest_non_system_summary.sender_type = 'user' THEN 'open'
		       ELSE 'replied'
		     END AS conversation_status,
		     conversations.assigned_to,
		     conversations.note,
		     latest_user_summary.latest_user_at,
		     latest_admin_summary.latest_admin_at,
		     conversations.closed_at,
		     COALESCE(conversations.updated_at, latest_message.created_at) AS conversation_updated_at
		   FROM support_messages latest_message
		   JOIN (
		     SELECT user_id, MAX(id) AS latest_id
		       FROM support_messages
		      GROUP BY user_id
		   ) latest ON latest.latest_id = latest_message.id
		   LEFT JOIN app_accounts accounts ON accounts.user_id = latest_message.user_id
		   LEFT JOIN support_conversations conversations ON conversations.user_id = latest_message.user_id
		   LEFT JOIN (
		     SELECT user_id, COUNT(*) AS message_count
		       FROM support_messages
		      GROUP BY user_id
		   ) message_counts ON message_counts.user_id = latest_message.user_id
		   LEFT JOIN (
		     SELECT user_id, COUNT(*) AS unread_count
		       FROM support_messages
		      WHERE sender_type IN ('admin', 'system')
		        AND read_by_user_at IS NULL
		      GROUP BY user_id
		   ) unread_counts ON unread_counts.user_id = latest_message.user_id
		   LEFT JOIN (
		     SELECT latest_non_system_message.user_id, latest_non_system_message.sender_type
		       FROM support_messages latest_non_system_message
		       JOIN (
		         SELECT user_id, MAX(id) AS latest_id
		           FROM support_messages
		          WHERE sender_type <> 'system'
		          GROUP BY user_id
		       ) latest_non_system_ids ON latest_non_system_ids.latest_id = latest_non_system_message.id
		   ) latest_non_system_summary ON latest_non_system_summary.user_id = latest_message.user_id
		   LEFT JOIN (
		     SELECT user_id, MAX(created_at) AS latest_user_at
		       FROM support_messages
		      WHERE sender_type = 'user'
		      GROUP BY user_id
		   ) latest_user_summary ON latest_user_summary.user_id = latest_message.user_id
		   LEFT JOIN (
		     SELECT user_id, MAX(created_at) AS latest_admin_at
		       FROM support_messages
		      WHERE sender_type = 'admin'
		      GROUP BY user_id
		   ) latest_admin_summary ON latest_admin_summary.user_id = latest_message.user_id
		 ) support_queue`
	args := []any{}
	where := []string{"(created_at >= ? OR conversation_status = 'open')"}
	args = append(args, filter.SinceMs)
	if filter.Status != "" {
		where = append(where, "conversation_status = ?")
		args = append(args, filter.Status)
	}
	if filter.Query != "" {
		like := "%" + filter.Query + "%"
		where = append(where, "(user_id LIKE ? OR phone_mask LIKE ? OR body LIKE ?)")
		args = append(args, like, like, like)
		if hash := accountPhoneHashForSearch(filter.Query); hash != "" {
			where[len(where)-1] = strings.TrimSuffix(where[len(where)-1], ")") + " OR phone_hash = ?)"
			args = append(args, hash)
		}
	}
	if len(where) > 0 {
		query += " WHERE " + strings.Join(where, " AND ")
	}
	query += ` ORDER BY
		  CASE conversation_status WHEN 'open' THEN 0 WHEN 'replied' THEN 1 WHEN 'closed' THEN 2 ELSE 3 END,
		  created_at DESC,
		  id DESC
		 LIMIT ?`
	args = append(args, filter.Limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	conversations := make([]SupportConversationEntry, 0, filter.Limit)
	for rows.Next() {
		var entry SupportConversationEntry
		var message SupportMessage
		var phoneMask, phoneCiphertext, phoneHash sql.NullString
		var imageURLsJSON sql.NullString
		var readByUserAt sql.NullInt64
		var latestNonSystemSenderType sql.NullString
		var status, assignedTo, note sql.NullString
		var latestUserAt, latestAdminAt, closedAt, updatedAt sql.NullInt64
		if err := rows.Scan(
			&entry.UserID,
			&phoneMask,
			&phoneCiphertext,
			&phoneHash,
			&message.ID,
			&message.SenderType,
			&message.Body,
			&imageURLsJSON,
			&message.CreatedAt,
			&readByUserAt,
			&latestNonSystemSenderType,
			&entry.MessageCount,
			&entry.UnreadByUserCount,
			&status,
			&assignedTo,
			&note,
			&latestUserAt,
			&latestAdminAt,
			&closedAt,
			&updatedAt,
		); err != nil {
			return nil, err
		}
		message.UserID = entry.UserID
		entry.PhoneMask = nullStringValue(phoneMask)
		entry.PhoneCiphertext = nullStringValue(phoneCiphertext)
		entry.PhoneHash = nullStringValue(phoneHash)
		if readByUserAt.Valid {
			value := readByUserAt.Int64
			message.ReadByUserAt = &value
		}
		if imageURLsJSON.Valid && strings.TrimSpace(imageURLsJSON.String) != "" {
			var imageURLs []string
			if err := json.Unmarshal([]byte(imageURLsJSON.String), &imageURLs); err == nil {
				message.ImageURLs = normalizeImages(imageURLs)
			}
		}
		entry.LatestMessage = message
		entry.Status = normalizeSupportConversationStatus(nullStringValue(status))
		if entry.Status == "" {
			entry.Status = "replied"
		}
		entry.AssignedTo = nullStringValue(assignedTo)
		entry.Note = nullStringValue(note)
		entry.LatestUserAt = nullInt64ToPtr(latestUserAt)
		entry.LatestAdminAt = nullInt64ToPtr(latestAdminAt)
		entry.ClosedAt = nullInt64ToPtr(closedAt)
		if updatedAt.Valid {
			entry.UpdatedAt = updatedAt.Int64
		} else {
			entry.UpdatedAt = message.CreatedAt
		}
		entry.NeedsReply = entry.Status == "open" && supportConversationNeedsReply(latestNonSystemSenderType)
		conversations = append(conversations, entry)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if conversations == nil {
		return []SupportConversationEntry{}, nil
	}
	return conversations, nil
}

func supportConversationNeedsReply(latestNonSystemSenderType sql.NullString) bool {
	return latestNonSystemSenderType.Valid && latestNonSystemSenderType.String == "user"
}

func (s *Store) CreateSupportMessage(ctx context.Context, userID string, senderType string, body string, imageURLs []string, createdAt int64) (*SupportMessage, error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer rollbackQuietly(tx)
	message, err := createSupportMessageTx(ctx, tx, userID, senderType, body, imageURLs, createdAt)
	if err != nil {
		return nil, err
	}
	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return message, nil
}

func (s *Store) CreateUserSupportMessageWithAutoReply(ctx context.Context, userID string, body string, imageURLs []string, createdAt int64, replyBody string) (*SupportMessage, *SupportMessage, error) {
	conn, err := s.db.Conn(ctx)
	if err != nil {
		return nil, nil, err
	}
	defer conn.Close()

	lockName := supportMessageLockName(userID)
	var acquired sql.NullInt64
	if err := conn.QueryRowContext(ctx, "SELECT GET_LOCK(?, 5)", lockName).Scan(&acquired); err != nil {
		return nil, nil, err
	}
	if !acquired.Valid || acquired.Int64 != 1 {
		return nil, nil, errSupportMessageBusy
	}
	defer func() {
		_, _ = conn.ExecContext(context.Background(), "SELECT RELEASE_LOCK(?)", lockName)
	}()

	tx, err := conn.BeginTx(ctx, nil)
	if err != nil {
		return nil, nil, err
	}
	defer rollbackQuietly(tx)

	latest, err := getLatestSupportMessageTx(ctx, tx, userID)
	if err != nil {
		return nil, nil, err
	}
	message, err := createSupportMessageTx(ctx, tx, userID, "user", body, imageURLs, createdAt)
	if err != nil {
		return nil, nil, err
	}
	var autoReply *SupportMessage
	if shouldCreateSupportAutoReply(latest, createdAt, replyBody) {
		autoReply, err = createSupportMessageTx(ctx, tx, userID, "system", replyBody, nil, createdAt+1)
		if err != nil {
			return nil, nil, err
		}
	}
	if err := tx.Commit(); err != nil {
		return nil, nil, err
	}
	return message, autoReply, nil
}

func createSupportMessageTx(ctx context.Context, tx *sql.Tx, userID string, senderType string, body string, imageURLs []string, createdAt int64) (*SupportMessage, error) {
	imagesJSON, err := supportImageURLsJSON(imageURLs)
	if err != nil {
		return nil, err
	}
	result, err := tx.ExecContext(
		ctx,
		`INSERT INTO support_messages(user_id, sender_type, body, image_urls_json, created_at)
		 VALUES (?, ?, ?, ?, ?)`,
		userID,
		senderType,
		body,
		imagesJSON,
		createdAt,
	)
	if err != nil {
		return nil, err
	}
	id, err := result.LastInsertId()
	if err != nil {
		return nil, err
	}
	if err := upsertSupportConversationTx(ctx, tx, userID, senderType, id, createdAt); err != nil {
		return nil, err
	}
	return &SupportMessage{
		ID:         id,
		UserID:     userID,
		SenderType: senderType,
		Body:       body,
		ImageURLs:  imageURLs,
		CreatedAt:  createdAt,
	}, nil
}

func supportMessageLockName(userID string) string {
	sum := sha1.Sum([]byte(strings.TrimSpace(userID)))
	return "support_message:" + hex.EncodeToString(sum[:])
}

func (s *Store) MarkSupportMessagesRead(ctx context.Context, userID string, readAt int64, lastSeenMessageID int64) error {
	query := `UPDATE support_messages
		    SET read_by_user_at = ?
		  WHERE user_id = ?
		    AND sender_type IN ('admin', 'system')
		    AND read_by_user_at IS NULL`
	args := []any{readAt, userID}
	if lastSeenMessageID > 0 {
		query += " AND id <= ?"
		args = append(args, lastSeenMessageID)
	}
	_, err := s.db.ExecContext(ctx, query, args...)
	return err
}

func (s *Store) UpdateSupportConversationStatus(ctx context.Context, userID string, status string, actor string, note string, nowMs int64) error {
	status = normalizeSupportConversationStatus(status)
	if status == "" {
		return fmt.Errorf("invalid support conversation status")
	}
	userID = strings.TrimSpace(userID)
	if userID == "" {
		return sql.ErrNoRows
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer rollbackQuietly(tx)
	var latestID sql.NullInt64
	var latestAt sql.NullInt64
	var messageCount sql.NullInt64
	var latestUserAt sql.NullInt64
	var latestAdminAt sql.NullInt64
	err = tx.QueryRowContext(
		ctx,
		`SELECT MAX(id),
		        MAX(created_at),
		        COUNT(*),
		        MAX(CASE WHEN sender_type = 'user' THEN created_at ELSE NULL END),
		        MAX(CASE WHEN sender_type = 'admin' THEN created_at ELSE NULL END)
		   FROM support_messages
		  WHERE user_id = ?`,
		userID,
	).Scan(&latestID, &latestAt, &messageCount, &latestUserAt, &latestAdminAt)
	if err != nil {
		return err
	}
	if !latestID.Valid || messageCount.Int64 == 0 {
		return sql.ErrNoRows
	}
	var closedAt any
	if status == "closed" {
		closedAt = nowMs
	}
	_, err = tx.ExecContext(
		ctx,
		`INSERT INTO support_conversations(
		     user_id, status, assigned_to, note, message_count, latest_message_id, latest_message_at,
		     latest_user_message_at, latest_admin_message_at, closed_at, created_at, updated_at
		   )
		   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		   ON DUPLICATE KEY UPDATE
		     status = VALUES(status),
		     assigned_to = VALUES(assigned_to),
		     note = VALUES(note),
		     message_count = VALUES(message_count),
		     latest_message_id = VALUES(latest_message_id),
		     latest_message_at = VALUES(latest_message_at),
		     latest_user_message_at = VALUES(latest_user_message_at),
		     latest_admin_message_at = VALUES(latest_admin_message_at),
		     closed_at = VALUES(closed_at),
		     updated_at = VALUES(updated_at)`,
		userID,
		status,
		nullableTrimmed(actor),
		nullableTrimmed(truncateRunes(strings.TrimSpace(note), 255)),
		int(messageCount.Int64),
		latestID.Int64,
		latestAt.Int64,
		nullableInt64FromNull(latestUserAt),
		nullableInt64FromNull(latestAdminAt),
		closedAt,
		nowMs,
		nowMs,
	)
	if err != nil {
		return err
	}
	return tx.Commit()
}

func (s *Store) AssignSupportConversation(ctx context.Context, userID string, actor string, nowMs int64) error {
	userID = strings.TrimSpace(userID)
	if userID == "" {
		return sql.ErrNoRows
	}
	result, err := s.db.ExecContext(
		ctx,
		`UPDATE support_conversations
		    SET assigned_to = ?,
		        updated_at = ?
		  WHERE user_id = ?`,
		nullableTrimmed(actor),
		nowMs,
		userID,
	)
	if err != nil {
		return err
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		return sql.ErrNoRows
	}
	return nil
}

func upsertSupportConversationTx(ctx context.Context, tx *sql.Tx, userID string, senderType string, messageID int64, createdAt int64) error {
	status := "replied"
	closedAt := any(nil)
	if senderType == "user" {
		status = "open"
	}
	var latestUserAt any
	var latestAdminAt any
	if senderType == "user" {
		latestUserAt = createdAt
	}
	if senderType == "admin" {
		latestAdminAt = createdAt
	}
	statusExpr := `CASE
		       WHEN VALUES(status) = 'open' THEN 'open'
		       WHEN VALUES(status) = 'replied' THEN 'replied'
		       WHEN support_conversations.status = 'closed' THEN 'closed'
		       ELSE support_conversations.status
		     END`
	closedAtExpr := "CASE WHEN VALUES(status) = 'open' THEN NULL ELSE support_conversations.closed_at END"
	if senderType == "admin" {
		closedAtExpr = "CASE WHEN VALUES(status) IN ('open', 'replied') THEN NULL ELSE support_conversations.closed_at END"
	}
	if senderType == "system" {
		statusExpr = "support_conversations.status"
		status = "replied"
		closedAtExpr = "support_conversations.closed_at"
	}
	_, err := tx.ExecContext(
		ctx,
		`INSERT INTO support_conversations(
		     user_id, status, message_count, latest_message_id, latest_message_at,
		     latest_user_message_at, latest_admin_message_at, closed_at, created_at, updated_at
		   )
		   VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?)
		   ON DUPLICATE KEY UPDATE
		     status = `+statusExpr+`,
		     message_count = support_conversations.message_count + 1,
		     latest_message_id = VALUES(latest_message_id),
		     latest_message_at = VALUES(latest_message_at),
		     latest_user_message_at = COALESCE(VALUES(latest_user_message_at), support_conversations.latest_user_message_at),
		     latest_admin_message_at = COALESCE(VALUES(latest_admin_message_at), support_conversations.latest_admin_message_at),
		     closed_at = `+closedAtExpr+`,
		     updated_at = VALUES(updated_at)`,
		userID,
		status,
		messageID,
		createdAt,
		latestUserAt,
		latestAdminAt,
		closedAt,
		createdAt,
		createdAt,
	)
	return err
}

func syncMergedSupportConversationTx(ctx context.Context, tx *sql.Tx, oldUserID string, newUserID string, nowMs int64) error {
	oldUserID = strings.TrimSpace(oldUserID)
	newUserID = strings.TrimSpace(newUserID)
	if oldUserID == "" || newUserID == "" || oldUserID == newUserID {
		return nil
	}

	var existingStatus sql.NullString
	var assignedTo sql.NullString
	var note sql.NullString
	var existingClosedAt sql.NullInt64
	err := tx.QueryRowContext(
		ctx,
		`SELECT status, assigned_to, note, closed_at
		   FROM support_conversations
		  WHERE user_id IN (?, ?)
		  ORDER BY CASE WHEN user_id = ? THEN 0 ELSE 1 END
		  LIMIT 1
		  FOR UPDATE`,
		newUserID,
		oldUserID,
		newUserID,
	).Scan(&existingStatus, &assignedTo, &note, &existingClosedAt)
	if err != nil && err != sql.ErrNoRows {
		return err
	}

	var latestID sql.NullInt64
	var latestAt sql.NullInt64
	var messageCount sql.NullInt64
	var latestUserAt sql.NullInt64
	var latestAdminAt sql.NullInt64
	var latestSender sql.NullString
	err = tx.QueryRowContext(
		ctx,
		`SELECT MAX(id),
		        MAX(created_at),
		        COUNT(*),
		        MAX(CASE WHEN sender_type = 'user' THEN created_at ELSE NULL END),
		        MAX(CASE WHEN sender_type = 'admin' THEN created_at ELSE NULL END),
		        (SELECT sender_type
		           FROM support_messages
		          WHERE user_id = ? AND sender_type <> 'system'
		          ORDER BY id DESC
		          LIMIT 1)
		   FROM support_messages
		  WHERE user_id = ?`,
		newUserID,
		newUserID,
	).Scan(&latestID, &latestAt, &messageCount, &latestUserAt, &latestAdminAt, &latestSender)
	if err != nil {
		return err
	}
	if !latestID.Valid || messageCount.Int64 == 0 {
		_, err = tx.ExecContext(ctx, "DELETE FROM support_conversations WHERE user_id IN (?, ?)", oldUserID, newUserID)
		return err
	}

	status := normalizeSupportConversationStatus(existingStatus.String)
	if status == "closed" {
		if !existingClosedAt.Valid || (latestUserAt.Valid && latestUserAt.Int64 > existingClosedAt.Int64) {
			status = "open"
		}
	} else if latestSender.Valid && latestSender.String == "user" {
		status = "open"
	} else {
		status = "replied"
	}

	var closedAt any
	if status == "closed" {
		closedAt = nullableInt64FromNull(existingClosedAt)
		if closedAt == nil {
			closedAt = nowMs
		}
	}

	_, err = tx.ExecContext(
		ctx,
		`INSERT INTO support_conversations(
		     user_id, status, assigned_to, note, message_count, latest_message_id, latest_message_at,
		     latest_user_message_at, latest_admin_message_at, closed_at, created_at, updated_at
		   )
		   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		   ON DUPLICATE KEY UPDATE
		     status = VALUES(status),
		     assigned_to = COALESCE(VALUES(assigned_to), support_conversations.assigned_to),
		     note = COALESCE(VALUES(note), support_conversations.note),
		     message_count = VALUES(message_count),
		     latest_message_id = VALUES(latest_message_id),
		     latest_message_at = VALUES(latest_message_at),
		     latest_user_message_at = VALUES(latest_user_message_at),
		     latest_admin_message_at = VALUES(latest_admin_message_at),
		     closed_at = VALUES(closed_at),
		     updated_at = VALUES(updated_at)`,
		newUserID,
		status,
		nullableTrimmed(assignedTo.String),
		nullableTrimmed(note.String),
		int(messageCount.Int64),
		latestID.Int64,
		latestAt.Int64,
		nullableInt64FromNull(latestUserAt),
		nullableInt64FromNull(latestAdminAt),
		closedAt,
		nowMs,
		nowMs,
	)
	if err != nil {
		return err
	}
	_, err = tx.ExecContext(ctx, "DELETE FROM support_conversations WHERE user_id = ?", oldUserID)
	return err
}

func normalizeSupportConversationStatus(raw string) string {
	status := strings.ToLower(strings.TrimSpace(raw))
	switch status {
	case "open", "todo", "pending", "needs_reply":
		return "open"
	case "replied", "done", "handled":
		return "replied"
	case "closed", "close":
		return "closed"
	case "", "all":
		return ""
	default:
		return ""
	}
}

func nullableInt64FromNull(value sql.NullInt64) any {
	if !value.Valid {
		return nil
	}
	return value.Int64
}

func (s *Store) getLatestSupportMessage(ctx context.Context, userID string) (*SupportMessage, error) {
	row := s.db.QueryRowContext(
		ctx,
		`SELECT id, user_id, sender_type, body, image_urls_json, created_at, read_by_user_at
		   FROM support_messages
		  WHERE user_id = ?
		  ORDER BY created_at DESC, id DESC
		  LIMIT 1`,
		userID,
	)
	message, err := scanSupportMessage(row)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &message, nil
}

func getLatestSupportMessageTx(ctx context.Context, tx *sql.Tx, userID string) (*SupportMessage, error) {
	row := tx.QueryRowContext(
		ctx,
		`SELECT id, user_id, sender_type, body, image_urls_json, created_at, read_by_user_at
		   FROM support_messages
		  WHERE user_id = ?
		  ORDER BY created_at DESC, id DESC
		  LIMIT 1`,
		userID,
	)
	message, err := scanSupportMessage(row)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &message, nil
}

type supportMessageScanner interface {
	Scan(dest ...any) error
}

func scanSupportMessage(scanner supportMessageScanner) (SupportMessage, error) {
	var message SupportMessage
	var imagesJSON sql.NullString
	var readByUserAt sql.NullInt64
	err := scanner.Scan(
		&message.ID,
		&message.UserID,
		&message.SenderType,
		&message.Body,
		&imagesJSON,
		&message.CreatedAt,
		&readByUserAt,
	)
	if err != nil {
		return SupportMessage{}, err
	}
	if readByUserAt.Valid {
		value := readByUserAt.Int64
		message.ReadByUserAt = &value
	}
	if imagesJSON.Valid && strings.TrimSpace(imagesJSON.String) != "" {
		var imageURLs []string
		if err := json.Unmarshal([]byte(imagesJSON.String), &imageURLs); err == nil {
			message.ImageURLs = normalizeImages(imageURLs)
		}
	}
	return message, nil
}

func supportImageURLsJSON(imageURLs []string) (any, error) {
	normalized := normalizeImages(imageURLs)
	if len(normalized) == 0 {
		return nil, nil
	}
	data, err := json.Marshal(normalized)
	if err != nil {
		return nil, err
	}
	return string(data), nil
}

func (s *Server) validateSupportImageURLs(r *http.Request, images []string) string {
	if len(images) == 0 {
		return ""
	}
	publicBaseURL := resolvePublicBaseURL(r)
	baseURL, err := url.Parse(publicBaseURL)
	if err != nil || baseURL.Scheme != "https" || baseURL.Host == "" {
		return "image host not configured"
	}
	for _, image := range images {
		parsed, err := url.Parse(image)
		if err != nil ||
			parsed.Scheme != "https" ||
			!strings.EqualFold(parsed.Host, baseURL.Host) ||
			parsed.RawQuery != "" ||
			parsed.Fragment != "" {
			return "invalid image url"
		}
		name := strings.TrimPrefix(parsed.Path, "/uploads/")
		if name == parsed.Path || !strings.HasPrefix(name, uploadPurposeSupport+"/") || !isServableUploadObjectName(name) {
			return "invalid image url"
		}
	}
	return ""
}

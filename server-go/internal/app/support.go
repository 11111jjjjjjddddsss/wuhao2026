package app

import (
	"context"
	"crypto/subtle"
	"database/sql"
	"encoding/json"
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
	supportAutoReplyBody                        = "您的反馈已提交。为便于定位，请继续补充问题发生时间、操作步骤或截图；如需进一步沟通，我们会通过本页面回复您。"
	supportGreetingAutoReplyBody                = "您好，请描述您遇到的问题，并尽量补充发生时间、操作步骤或截图；后续可在本页面查看回复。"
	supportHowToUseAutoReplyBody                = "您可以回到主聊天页，输入农业问题或上传作物图片进行咨询；本页主要用于提交 App 使用问题、意见反馈和故障截图。"
	supportLoginAutoReplyBody                   = "如果遇到登录或验证码问题，请先确认已勾选协议、网络正常，并可尝试切换验证码登录或稍后重试。若仍失败，请补充失败时间、页面提示和截图，我们会继续核实。"
	supportUpdateAutoReplyBody                  = "检查更新可在设置页进入“检查更新”。如果下载、校验或安装失败，请补充当前版本、失败提示和截图，便于我们定位。"
	supportImageAutoReplyBody                   = "图片相关问题请确认单次最多 4 张，图片清晰且网络正常；如果拍照、选图或上传失败，请补充失败时间、页面提示和截图。"
	supportMembershipAutoReplyBody              = "会员档位、剩余次数和权益请以会员中心展示为准。涉及订单、扣费或权益异常时，请补充页面截图和发生时间，我们会进一步核实。"
	supportHistoryAutoReplyBody                 = "历史对话以当前登录账号为准；如需清空，可在账号管理页执行删除历史。若历史恢复或删除异常，请补充发生时间、操作步骤和截图。"
	supportPrivacyAutoReplyBody                 = "服务协议、隐私政策和风险提示可在设置页查看。若您对账号数据、隐私或注销规则有疑问，请说明具体问题，我们会继续核实。"
	supportAgriQuestionAutoReplyBody            = "农业技术问题请回到主聊天页咨询，那里支持文字、图片和图文混合问诊。本页主要处理 App 使用问题、故障反馈和客服沟通。"
)

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
	SinceMs int64 `json:"since_ms"`
	Limit   int   `json:"limit"`
}

type SupportConversationEntry struct {
	UserID            string         `json:"user_id"`
	LatestMessage     SupportMessage `json:"latest_message"`
	MessageCount      int            `json:"message_count"`
	UnreadByUserCount int            `json:"unread_by_user_count"`
	NeedsReply        bool           `json:"needs_reply"`
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
	if validationError := s.validateChatStreamImageURLs(r, imageURLs); validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if err := s.store.EnsureUser(r.Context(), auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	nowMs := time.Now().UnixMilli()
	latest, latestErr := s.store.getLatestSupportMessage(r.Context(), auth.UserID)
	if latestErr != nil {
		s.logger.Warn("get latest support message before auto reply failed", "userId", auth.UserID, "error", latestErr)
	}
	message, err := s.store.CreateSupportMessage(r.Context(), auth.UserID, "user", normalized, imageURLs, nowMs)
	if err != nil {
		s.logger.Error("create support message failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	var autoReply *SupportMessage
	replyBody := supportAutoReplyBodyFor(normalized, imageURLs)
	if latestErr == nil && shouldCreateSupportAutoReply(latest, nowMs, replyBody) {
		autoReply, err = s.store.CreateSupportMessage(r.Context(), auth.UserID, "system", replyBody, nil, nowMs+1)
		if err != nil {
			s.logger.Error("create support auto reply failed", "userId", auth.UserID, "messageId", message.ID, "error", err)
			autoReply = nil
		}
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
	normalized, imageURLs, validationError := normalizeSupportMessagePayload(body.Body, body.Images)
	if validationError != "" {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", "", userID, false, http.StatusBadRequest, map[string]any{"error_code": validationError})
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if validationError := s.validateChatStreamImageURLs(r, imageURLs); validationError != "" {
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", "", userID, false, http.StatusBadRequest, map[string]any{"error_code": validationError})
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if err := s.store.EnsureUser(r.Context(), userID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", userID, "error", err)
		s.recordAdminAuditLog(r, "support_admin_secret", "internal.support.messages.create", "support_messages", "", userID, false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
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
	if provided == "" || subtle.ConstantTimeCompare([]byte(provided), []byte(secret)) != 1 {
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

func shouldCreateSupportAutoReply(latest *SupportMessage, nowMs int64, replyBody string) bool {
	if latest == nil {
		return true
	}
	if latest.CreatedAt <= 0 || nowMs < latest.CreatedAt || latest.SenderType == "admin" {
		return false
	}
	elapsed := time.Duration(nowMs-latest.CreatedAt) * time.Millisecond
	if latest.SenderType == "system" {
		if replyBody == supportAutoReplyBody && latest.Body == replyBody {
			return elapsed >= defaultSupportAutoReplyCooldown
		}
		return latest.Body != replyBody || elapsed >= defaultSupportAutoReplyRepeatCooldown
	}
	if latest.SenderType != "user" {
		return false
	}
	if replyBody != supportAutoReplyBody {
		return elapsed >= defaultSupportFAQAutoReplyCooldown
	}
	return elapsed >= defaultSupportAutoReplyCooldown
}

func supportAutoReplyBodyFor(body string, imageURLs []string) string {
	if len(imageURLs) > 0 {
		return supportAutoReplyBody
	}
	normalized := normalizeSupportAutoReplyText(body)
	if isShortSupportGreeting(normalized) {
		return supportGreetingAutoReplyBody
	}
	if supportTextContainsAny(normalized, "怎么用", "如何用", "使用方法", "怎么玩", "新手", "教程") {
		return supportHowToUseAutoReplyBody
	}
	if supportTextContainsAny(normalized, "农业问题", "作物问题", "病虫害", "病害", "虫害", "打药", "农药", "施肥", "小麦", "玉米", "水稻", "棉花", "果树", "叶片发黄", "黄叶") {
		return supportAgriQuestionAutoReplyBody
	}
	if supportTextContainsAny(normalized, "登录", "登陆", "验证码", "一键登录", "手机号", "认证", "收不到码", "短信") {
		return supportLoginAutoReplyBody
	}
	if supportTextContainsAny(normalized, "检查更新", "更新", "版本", "升级app", "安装包", "apk", "下载失败", "安装失败") {
		return supportUpdateAutoReplyBody
	}
	if supportTextContainsAny(normalized, "图片", "照片", "拍照", "相机", "上传", "选图", "图传不上", "发图") {
		return supportImageAutoReplyBody
	}
	if supportTextContainsAny(normalized, "会员", "次数", "额度", "加油包", "订单", "订购", "购买", "扣费", "支付", "退款", "权益") {
		return supportMembershipAutoReplyBody
	}
	if supportTextContainsAny(normalized, "历史", "记录", "清空", "删除", "恢复", "找不到", "换手机", "重装") {
		return supportHistoryAutoReplyBody
	}
	if supportTextContainsAny(normalized, "隐私", "协议", "服务协议", "注销", "个人信息", "风险提示") {
		return supportPrivacyAutoReplyBody
	}
	return supportAutoReplyBody
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
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultSupportMessageRateLimitWindow, defaultSupportMessageRateLimitMaxHits)
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
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT
		   latest_message.user_id,
		   latest_message.id,
		   latest_message.sender_type,
		   latest_message.body,
		   latest_message.image_urls_json,
		   latest_message.created_at,
		   latest_message.read_by_user_at,
		   latest_non_system_summary.sender_type AS latest_non_system_sender_type,
		   COALESCE(message_counts.message_count, 0) AS message_count,
		   COALESCE(unread_counts.unread_count, 0) AS unread_count
		 FROM support_messages latest_message
		 JOIN (
		   SELECT user_id, MAX(id) AS latest_id
		     FROM support_messages
		    GROUP BY user_id
		 ) latest ON latest.latest_id = latest_message.id
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
		 WHERE latest_message.created_at >= ?
		 ORDER BY latest_message.created_at DESC, latest_message.id DESC
		 LIMIT ?`,
		filter.SinceMs,
		filter.Limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	conversations := make([]SupportConversationEntry, 0, filter.Limit)
	for rows.Next() {
		var entry SupportConversationEntry
		var message SupportMessage
		var imageURLsJSON sql.NullString
		var readByUserAt sql.NullInt64
		var latestNonSystemSenderType sql.NullString
		if err := rows.Scan(
			&entry.UserID,
			&message.ID,
			&message.SenderType,
			&message.Body,
			&imageURLsJSON,
			&message.CreatedAt,
			&readByUserAt,
			&latestNonSystemSenderType,
			&entry.MessageCount,
			&entry.UnreadByUserCount,
		); err != nil {
			return nil, err
		}
		message.UserID = entry.UserID
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
		entry.NeedsReply = supportConversationNeedsReply(latestNonSystemSenderType)
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
	imagesJSON, err := supportImageURLsJSON(imageURLs)
	if err != nil {
		return nil, err
	}
	result, err := s.db.ExecContext(
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
	return &SupportMessage{
		ID:         id,
		UserID:     userID,
		SenderType: senderType,
		Body:       body,
		ImageURLs:  imageURLs,
		CreatedAt:  createdAt,
	}, nil
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

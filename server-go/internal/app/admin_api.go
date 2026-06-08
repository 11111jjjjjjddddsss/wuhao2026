package app

import (
	"context"
	"database/sql"
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"time"
)

const (
	defaultAdminListLimit = 50
	maxAdminListLimit     = 100
	adminExcerptRunes     = 96
)

type AdminOverview struct {
	Health AdminHealthStatus `json:"health"`
	Today  AdminTodayMetrics `json:"today"`
	Queues AdminQueueMetrics `json:"queues"`
	Notes  []AdminStatusNote `json:"notes"`
	NowMs  int64             `json:"now_ms"`
}

type AdminHealthStatus struct {
	API               string `json:"api"`
	Bailian           string `json:"bailian"`
	Dypns             string `json:"dypns"`
	DypnsFusion       string `json:"dypns_fusion"`
	DypnsSMS          string `json:"dypns_sms"`
	Redis             string `json:"redis"`
	UploadStorage     string `json:"upload_storage"`
	AuthStrict        bool   `json:"auth_strict"`
	DevOrderEndpoints bool   `json:"dev_order_endpoints"`
}

type AdminTodayMetrics struct {
	RegisteredUsers      int64  `json:"registered_users"`
	ActiveAuthSessions   int64  `json:"active_auth_sessions"`
	ChatRounds           int64  `json:"chat_rounds"`
	ChatUsers            int64  `json:"chat_users"`
	ImageChatRounds      int64  `json:"image_chat_rounds"`
	QuotaDeductions      int64  `json:"quota_deductions"`
	AppErrors            int64  `json:"app_errors"`
	SupportConversations int64  `json:"support_conversations"`
	SupportNeedsReply    int64  `json:"support_needs_reply"`
	DailyAgriStatus      string `json:"daily_agri_status"`
}

type AdminQueueMetrics struct {
	AppErrorTop []ClientAppLogSummaryEntry `json:"app_error_top"`
}

type AdminStatusNote struct {
	Title string `json:"title"`
	Body  string `json:"body"`
	Level string `json:"level"`
}

type AdminMonitoring struct {
	Health       AdminHealthStatus           `json:"health"`
	Windows      []AdminMonitoringWindow     `json:"windows"`
	Queues       AdminMonitoringQueues       `json:"queues"`
	LaunchReady  []AdminMonitoringLaunchItem `json:"launch_readiness"`
	ActionItems  []AdminMonitoringActionItem `json:"action_items"`
	Capabilities []AdminMonitoringCapability `json:"capabilities"`
	UserRegions  AdminUserRegionOverview     `json:"user_regions"`
	TopRegions   []AdminRegionMetric         `json:"top_regions"`
	TopAppErrors []ClientAppLogSummaryEntry  `json:"top_app_errors"`
	Notes        []AdminStatusNote           `json:"notes"`
	NowMs        int64                       `json:"now_ms"`
}

type AdminMonitoringWindow struct {
	Key                  string `json:"key"`
	Label                string `json:"label"`
	SinceMs              int64  `json:"since_ms"`
	NewUsers             int64  `json:"new_users"`
	ActiveSessions       int64  `json:"active_sessions"`
	RecentAuthSessions   int64  `json:"recent_auth_sessions"`
	ChatRounds           int64  `json:"chat_rounds"`
	ChatUsers            int64  `json:"chat_users"`
	ImageChatRounds      int64  `json:"image_chat_rounds"`
	QuotaDeductions      int64  `json:"quota_deductions"`
	AppErrors            int64  `json:"app_errors"`
	AppWarns             int64  `json:"app_warns"`
	SupportMessages      int64  `json:"support_messages"`
	SupportUsers         int64  `json:"support_users"`
	GiftCardRedeems      int64  `json:"gift_card_redeems"`
	GiftCardFailures     int64  `json:"gift_card_failures"`
	AuditFailures        int64  `json:"audit_failures"`
	AdminActions         int64  `json:"admin_actions"`
	DailyAgriFailedCards int64  `json:"daily_agri_failed_cards"`
}

type AdminMonitoringQueues struct {
	SupportNeedsReply      int64                    `json:"support_needs_reply"`
	SupportOpen            int64                    `json:"support_open"`
	SupportReplied         int64                    `json:"support_replied"`
	SupportClosed          int64                    `json:"support_closed"`
	SupportOldestPendingAt *int64                   `json:"support_oldest_pending_at,omitempty"`
	DailyAgriStatus        string                   `json:"daily_agri_status"`
	DailyAgriUpdatedAt     *int64                   `json:"daily_agri_updated_at,omitempty"`
	DailyAgriError         string                   `json:"daily_agri_error,omitempty"`
	AppUpdate              AdminMonitoringAppUpdate `json:"app_update"`
	GiftCardBatchCount     int64                    `json:"gift_card_batch_count"`
	GiftCardTotal          int64                    `json:"gift_card_total"`
	GiftCardActive         int64                    `json:"gift_card_active"`
	GiftCardRedeemed       int64                    `json:"gift_card_redeemed"`
	GiftCardFailedAttempts int64                    `json:"gift_card_failed_attempts"`
	AuditFailures          int64                    `json:"audit_failures"`
	AppErrors              int64                    `json:"app_errors"`
	UnreadyDependencyCount int64                    `json:"unready_dependency_count"`
}

type AdminMonitoringAppUpdate struct {
	Enabled                   bool   `json:"enabled"`
	ConfigValid               bool   `json:"config_valid"`
	DownloadArtifactsComplete bool   `json:"download_artifacts_complete"`
	HasAPKURL                 bool   `json:"has_apk_url"`
	HasSHA256                 bool   `json:"has_sha256"`
	HasFileSize               bool   `json:"has_file_size"`
	LatestVersionCode         int    `json:"latest_version_code"`
	LatestVersionName         string `json:"latest_version_name,omitempty"`
	ForceUpdate               bool   `json:"force_update"`
}

type AdminMonitoringActionItem struct {
	Title string `json:"title"`
	Body  string `json:"body"`
	Level string `json:"level"`
	Route string `json:"route,omitempty"`
	Count int64  `json:"count,omitempty"`
}

type AdminMonitoringCapability struct {
	Title  string `json:"title"`
	Status string `json:"status"`
	Body   string `json:"body"`
	Route  string `json:"route,omitempty"`
}

type AdminMonitoringLaunchItem struct {
	Title  string `json:"title"`
	Status string `json:"status"`
	Body   string `json:"body"`
	Route  string `json:"route,omitempty"`
	Owner  string `json:"owner,omitempty"`
}

type AdminRegionMetric struct {
	Region      string `json:"region"`
	Source      string `json:"source,omitempty"`
	Reliability string `json:"reliability,omitempty"`
	Count       int64  `json:"count"`
	UserCount   int64  `json:"user_count"`
	LastSeenAt  int64  `json:"last_seen_at"`
}

type AdminUserRegionOverview struct {
	RegisteredTotal      int64               `json:"registered_total"`
	RegisteredWithRegion int64               `json:"registered_with_region"`
	MemberTotal          int64               `json:"member_total"`
	MemberWithRegion     int64               `json:"member_with_region"`
	RegisteredTop        []AdminRegionMetric `json:"registered_top"`
	MemberTop            []AdminRegionMetric `json:"member_top"`
}

type AdminUserListEntry struct {
	UserID                string            `json:"user_id"`
	PhoneMask             string            `json:"phone_mask,omitempty"`
	PhoneNumber           string            `json:"phone_number,omitempty"`
	CreatedAt             int64             `json:"created_at,omitempty"`
	UpdatedAt             int64             `json:"updated_at,omitempty"`
	LastLoginAt           *int64            `json:"last_login_at,omitempty"`
	Tier                  Tier              `json:"tier"`
	TierExpireAt          *int64            `json:"tier_expire_at,omitempty"`
	Daily                 DailyQuotaStatus  `json:"daily"`
	TopupRemaining        int               `json:"topup_remaining"`
	TopupExpireAt         *int64            `json:"topup_expire_at,omitempty"`
	UpgradeRemaining      int               `json:"upgrade_remaining"`
	RoundTotal            int               `json:"round_total"`
	LastSeenAt            *int64            `json:"last_seen_at,omitempty"`
	LastRegion            string            `json:"last_region,omitempty"`
	LastRegionSource      RegionSource      `json:"last_region_source,omitempty"`
	LastRegionReliability RegionReliability `json:"last_region_reliability,omitempty"`
	ActiveSessions        int64             `json:"active_sessions"`
	ErrorCount24h         int64             `json:"error_count_24h"`
	SupportNeedsReply     bool              `json:"support_needs_reply"`
	SupportMessageCount   int64             `json:"support_message_count"`
}

type AdminUserDetail struct {
	User             AdminUserListEntry      `json:"user"`
	QuotaLedger      []AdminQuotaLedgerEntry `json:"quota_ledger"`
	TopupPacks       []AdminTopupPackEntry   `json:"topup_packs"`
	UpgradeCredits   []AdminUpgradeCredit    `json:"upgrade_credits"`
	RecentRounds     []AdminRoundExcerpt     `json:"recent_rounds"`
	RecentAppLogs    []ClientAppLogEntry     `json:"recent_app_logs"`
	SupportSummary   *SupportSummary         `json:"support_summary,omitempty"`
	SupportMessages  []AdminSupportMessage   `json:"support_messages"`
	Orders           []AdminOrderEntry       `json:"orders"`
	GiftCards        []AdminGiftCardEntry    `json:"gift_cards"`
	GiftCardAttempts []AdminGiftCardAttempt  `json:"gift_card_attempts"`
}

type AdminEntitlementSummary struct {
	RegisteredUsers          int64 `json:"registered_users"`
	AccountMemberUsers       int64 `json:"account_member_users"`
	MemberUsers              int64 `json:"member_users"`
	LegacyMemberUsers        int64 `json:"legacy_member_users"`
	FreeUsers                int64 `json:"free_users"`
	PlusUsers                int64 `json:"plus_users"`
	ProUsers                 int64 `json:"pro_users"`
	ExpiringIn7d             int64 `json:"expiring_in_7d"`
	ExpiringIn30d            int64 `json:"expiring_in_30d"`
	DailyLimitExhaustedUsers int64 `json:"daily_limit_exhausted_users"`
	TopupActiveUsers         int64 `json:"topup_active_users"`
	UpgradeCreditUsers       int64 `json:"upgrade_credit_users"`
	NowMs                    int64 `json:"now_ms"`
}

type AdminQuotaLedgerEntry struct {
	ID          int64  `json:"id"`
	ClientMsgID string `json:"client_msg_id"`
	DayCN       string `json:"day_cn"`
	Source      string `json:"source"`
	Delta       int    `json:"delta"`
	CreatedAt   int64  `json:"created_at"`
}

type AdminTopupPackEntry struct {
	PackID    string `json:"pack_id"`
	UserID    string `json:"user_id"`
	OrderID   string `json:"order_id,omitempty"`
	Initial   int    `json:"initial"`
	Remaining int    `json:"remaining"`
	Used      int    `json:"used"`
	ExpireAt  *int64 `json:"expire_at,omitempty"`
	Status    string `json:"status"`
	CreatedAt int64  `json:"created_at"`
}

type AdminUpgradeCredit struct {
	UserID    string `json:"user_id"`
	Remaining int    `json:"remaining"`
	ExpireAt  *int64 `json:"expire_at,omitempty"`
	UpdatedAt int64  `json:"updated_at"`
}

type AdminRoundExcerpt struct {
	ClientMsgID       string            `json:"client_msg_id"`
	UserExcerpt       string            `json:"user_excerpt"`
	AssistantExcerpt  string            `json:"assistant_excerpt"`
	HasImages         bool              `json:"has_images"`
	ImageCount        int               `json:"image_count"`
	Region            string            `json:"region,omitempty"`
	RegionSource      RegionSource      `json:"region_source,omitempty"`
	RegionReliability RegionReliability `json:"region_reliability,omitempty"`
	CreatedAt         int64             `json:"created_at"`
}

type AdminSupportConversation struct {
	UserID            string              `json:"user_id"`
	PhoneMask         string              `json:"phone_mask,omitempty"`
	PhoneNumber       string              `json:"phone_number,omitempty"`
	LatestMessage     AdminSupportMessage `json:"latest_message"`
	MessageCount      int                 `json:"message_count"`
	UnreadByUserCount int                 `json:"unread_by_user_count"`
	NeedsReply        bool                `json:"needs_reply"`
	Status            string              `json:"status"`
	AssignedTo        string              `json:"assigned_to,omitempty"`
	Note              string              `json:"note,omitempty"`
	LatestUserAt      *int64              `json:"latest_user_message_at,omitempty"`
	LatestAdminAt     *int64              `json:"latest_admin_message_at,omitempty"`
	ClosedAt          *int64              `json:"closed_at,omitempty"`
	UpdatedAt         int64               `json:"updated_at"`
}

type AdminSupportMessage struct {
	ID           int64    `json:"id"`
	UserID       string   `json:"user_id,omitempty"`
	SenderType   string   `json:"sender_type"`
	Body         string   `json:"body,omitempty"`
	BodyExcerpt  string   `json:"body_excerpt"`
	HasImages    bool     `json:"has_images"`
	ImageCount   int      `json:"image_count"`
	ImageURLs    []string `json:"image_urls,omitempty"`
	CreatedAt    int64    `json:"created_at"`
	ReadByUserAt *int64   `json:"read_by_user_at,omitempty"`
}

type AdminOrderEntry struct {
	OrderID   string          `json:"order_id"`
	UserID    string          `json:"user_id"`
	Type      string          `json:"type"`
	Amount    string          `json:"amount"`
	CreatedAt int64           `json:"created_at"`
	Status    string          `json:"status"`
	Result    json.RawMessage `json:"result,omitempty"`
}

type AdminPlaceholderStatus struct {
	Status string `json:"status"`
	Note   string `json:"note"`
}

type AdminDailyAgriEntry struct {
	DayCN          string          `json:"day_cn"`
	Scope          string          `json:"scope"`
	Status         string          `json:"status"`
	Title          string          `json:"title,omitempty"`
	ItemCount      int             `json:"item_count"`
	SourceCount    int             `json:"source_count"`
	Model          string          `json:"model,omitempty"`
	SearchStrategy string          `json:"search_strategy,omitempty"`
	PromptVersion  string          `json:"prompt_version,omitempty"`
	Content        json.RawMessage `json:"content,omitempty"`
	Sources        json.RawMessage `json:"sources,omitempty"`
	LeaseUntil     int64           `json:"lease_until,omitempty"`
	GeneratedAt    *int64          `json:"generated_at,omitempty"`
	Error          string          `json:"error,omitempty"`
	CreatedAt      int64           `json:"created_at"`
	UpdatedAt      int64           `json:"updated_at"`
}

type AdminAppUpdateConfig struct {
	Enabled                   bool   `json:"enabled"`
	Source                    string `json:"source,omitempty"`
	LatestVersionCode         int    `json:"latest_version_code"`
	LatestVersionName         string `json:"latest_version_name,omitempty"`
	APKURL                    string `json:"apk_url,omitempty"`
	APKChecksumSHA256         string `json:"apk_sha256,omitempty"`
	ReleaseNotes              string `json:"release_notes,omitempty"`
	ForceUpdate               bool   `json:"force_update"`
	FileSizeBytes             int64  `json:"file_size_bytes,omitempty"`
	UpdatedBy                 string `json:"updated_by,omitempty"`
	UpdatedAt                 int64  `json:"updated_at,omitempty"`
	ConfigValid               bool   `json:"config_valid"`
	DownloadArtifactsComplete bool   `json:"download_artifacts_complete"`
	HasAPKURL                 bool   `json:"has_apk_url"`
	HasSHA256                 bool   `json:"has_sha256"`
	HasFileSize               bool   `json:"has_file_size"`
}

func (s *Server) handleAdminOverview(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r)
	if !ok {
		return
	}
	overview, err := s.store.BuildAdminOverview(r.Context(), s.adminHealthStatus(), GetTodayKeyCN(s.shanghai, time.Now()), adminDayStartMs(s.shanghai, time.Now()), time.Now().UnixMilli())
	if err != nil {
		s.logger.Error("admin overview failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.overview", "dashboard", "", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.overview", "dashboard", "", "", true, http.StatusOK, map[string]any{"app_errors": overview.Today.AppErrors})
	s.writeJSON(w, http.StatusOK, overview)
}

func (s *Server) handleAdminMonitoring(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "ops_readonly", "auditor", "support", "finance_ops", "content_ops", "release_ops")
	if !ok {
		return
	}
	now := time.Now()
	updateRecord, err := s.store.ReadAndroidUpdateConfigRecord(r.Context())
	if err != nil {
		s.logger.Error("admin monitoring update config failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.monitoring", "dashboard", "", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	report, err := s.store.BuildAdminMonitoring(
		r.Context(),
		s.adminHealthStatus(),
		GetTodayKeyCN(s.shanghai, now),
		adminDayStartMs(s.shanghai, now),
		now.UnixMilli(),
		updateRecord.Config,
	)
	if err != nil {
		s.logger.Error("admin monitoring failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.monitoring", "dashboard", "", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	report.ActionItems = filterAdminMonitoringActionRoutes(report.ActionItems, admin.User.Role)
	report.LaunchReady = filterAdminMonitoringLaunchRoutes(report.LaunchReady, admin.User.Role)
	report.Capabilities = filterAdminMonitoringCapabilityRoutes(report.Capabilities, admin.User.Role)
	s.recordAdminAuditLog(r, admin.User.Username, "admin.monitoring", "dashboard", "", "", true, http.StatusOK, map[string]any{
		"app_errors":          report.Queues.AppErrors,
		"support_needs_reply": report.Queues.SupportNeedsReply,
		"unready_deps":        report.Queues.UnreadyDependencyCount,
	})
	s.writeJSON(w, http.StatusOK, report)
}

func (s *Server) handleAdminUsers(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "ops_readonly", "support", "finance_ops")
	if !ok {
		return
	}
	filter := AdminUserQuery{
		Query:              strings.TrimSpace(r.URL.Query().Get("query")),
		DayCN:              GetTodayKeyCN(s.shanghai, time.Now()),
		Limit:              parseAdminLimit(r.URL.Query().Get("limit")),
		NowMs:              time.Now().UnixMilli(),
		SinceMs:            time.Now().Add(-24 * time.Hour).UnixMilli(),
		IncludePhoneNumber: adminCanViewAccountPhone(admin.User.Role),
	}
	users, err := s.store.ListAdminUsers(r.Context(), filter)
	if err != nil {
		s.logger.Error("admin list users failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.users.list", "app_accounts", "", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.users.list", "app_accounts", "", "", true, http.StatusOK, map[string]any{"limit": filter.Limit, "row_count": len(users), "phone_number_visible": filter.IncludePhoneNumber})
	s.writeJSON(w, http.StatusOK, map[string]any{"users": users, "filter": filter})
}

func (s *Server) handleAdminEntitlementSummary(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "ops_readonly", "support", "finance_ops")
	if !ok {
		return
	}
	nowMs := time.Now().UnixMilli()
	summary, err := s.store.ReadAdminEntitlementSummary(r.Context(), GetTodayKeyCN(s.shanghai, time.Now()), nowMs)
	if err != nil {
		s.logger.Error("admin entitlement summary failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.entitlements.summary", "user_entitlement", "", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.entitlements.summary", "user_entitlement", "", "", true, http.StatusOK, map[string]any{
		"member_users": summary.MemberUsers,
		"expiring_7d":  summary.ExpiringIn7d,
	})
	s.writeJSON(w, http.StatusOK, summary)
}

func (s *Server) handleAdminUserDetail(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "ops_readonly", "support", "finance_ops")
	if !ok {
		return
	}
	userID := normalizeUserID(r.URL.Query().Get("user_id"))
	if userID == "" {
		s.writeError(w, http.StatusBadRequest, "user_id_required")
		return
	}
	detail, err := s.store.GetAdminUserDetail(r.Context(), userID, GetTodayKeyCN(s.shanghai, time.Now()), time.Now().UnixMilli(), adminCanViewAccountPhone(admin.User.Role))
	if err != nil {
		status := http.StatusInternalServerError
		code := "internal_error"
		if err == sql.ErrNoRows {
			status = http.StatusNotFound
			code = "user_not_found"
		}
		s.recordAdminAuditLog(r, admin.User.Username, "admin.users.detail", "app_accounts", userID, userID, false, status, map[string]any{"error_code": code})
		s.writeError(w, status, code)
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.users.detail", "app_accounts", userID, userID, true, http.StatusOK, map[string]any{
		"recent_rounds":        len(detail.RecentRounds),
		"recent_logs":          len(detail.RecentAppLogs),
		"phone_number_visible": adminCanViewAccountPhone(admin.User.Role),
	})
	s.writeJSON(w, http.StatusOK, detail)
}

func (s *Server) handleAdminSupportConversations(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "support", "ops_readonly", "auditor")
	if !ok {
		return
	}
	filter, validationError := parseSupportConversationQuery(r.URL.Query(), time.Now())
	if validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	conversations, err := s.store.ListSupportConversations(r.Context(), filter)
	if err != nil {
		s.logger.Error("admin list support conversations failed", "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	output := make([]AdminSupportConversation, 0, len(conversations))
	includePhoneNumber := adminCanViewAccountPhone(admin.User.Role)
	for _, item := range conversations {
		phoneNumber := ""
		if includePhoneNumber && item.PhoneCiphertext != "" {
			if phone, err := decryptAccountPhoneNumber(item.PhoneCiphertext); err == nil {
				phoneNumber = phone
			}
		}
		output = append(output, AdminSupportConversation{
			UserID:            item.UserID,
			PhoneMask:         item.PhoneMask,
			PhoneNumber:       phoneNumber,
			LatestMessage:     adminSupportMessageFromSupport(item.LatestMessage, false),
			MessageCount:      item.MessageCount,
			UnreadByUserCount: item.UnreadByUserCount,
			NeedsReply:        item.NeedsReply,
			Status:            item.Status,
			AssignedTo:        item.AssignedTo,
			Note:              item.Note,
			LatestUserAt:      item.LatestUserAt,
			LatestAdminAt:     item.LatestAdminAt,
			ClosedAt:          item.ClosedAt,
			UpdatedAt:         item.UpdatedAt,
		})
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.support.conversations", "support_messages", "", "", true, http.StatusOK, map[string]any{"row_count": len(output), "status": filter.Status, "phone_number_visible": includePhoneNumber})
	s.writeJSON(w, http.StatusOK, map[string]any{"conversations": output, "filter": filter})
}

func (s *Server) handleAdminSupportMessages(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "support", "ops_readonly", "auditor")
	if !ok {
		return
	}
	userID := normalizeUserID(r.URL.Query().Get("user_id"))
	if userID == "" {
		s.writeError(w, http.StatusBadRequest, "user_id_required")
		return
	}
	messages, err := s.store.ListSupportMessages(r.Context(), userID, supportMessageListLimit)
	if err != nil {
		s.logger.Error("admin list support messages failed", "userId", userID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	output := make([]AdminSupportMessage, 0, len(messages))
	for _, message := range messages {
		output = append(output, adminSupportMessageFromSupport(message, true))
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.support.messages", "support_messages", "", userID, true, http.StatusOK, map[string]any{"row_count": len(output)})
	s.writeJSON(w, http.StatusOK, map[string]any{"messages": output})
}

func (s *Server) handleAdminCreateSupportMessage(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "support")
	if !ok {
		return
	}
	var body supportAdminMessageRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	userID := normalizeUserID(body.UserID)
	if userID == "" {
		s.writeError(w, http.StatusBadRequest, "user_id_required")
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
	existing, err := s.store.ListSupportMessages(r.Context(), userID, 1)
	if err != nil {
		s.logger.Error("admin check support conversation failed", "userId", userID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if len(existing) == 0 {
		s.writeError(w, http.StatusNotFound, "support_conversation_not_found")
		return
	}
	message, err := s.store.CreateSupportMessage(r.Context(), userID, "admin", normalized, imageURLs, time.Now().UnixMilli())
	if err != nil {
		s.logger.Error("admin create support message failed", "userId", userID, "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.support.reply", "support_messages", "", userID, false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if err := s.store.AssignSupportConversation(r.Context(), userID, admin.User.Username, time.Now().UnixMilli()); err != nil {
		s.logger.Warn("admin assign support conversation failed", "userId", userID, "actor", admin.User.Username, "error", err)
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.support.reply", "support_messages", strconv.FormatInt(message.ID, 10), userID, true, http.StatusOK, map[string]any{"has_images": len(imageURLs) > 0})
	s.writeJSON(w, http.StatusOK, map[string]any{"message": adminSupportMessageFromSupport(*message, true)})
}

func (s *Server) handleAdminUpdateSupportConversationStatus(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "support")
	if !ok {
		return
	}
	var body supportConversationStatusRequest
	if err := decodeJSONBodyLimited(r, &body, 4*1024); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	userID := normalizeUserID(body.UserID)
	status := normalizeSupportConversationStatus(body.Status)
	if userID == "" {
		s.writeError(w, http.StatusBadRequest, "user_id_required")
		return
	}
	if status == "" {
		s.writeError(w, http.StatusBadRequest, "invalid_status")
		return
	}
	if err := s.store.UpdateSupportConversationStatus(r.Context(), userID, status, admin.User.Username, body.Note, time.Now().UnixMilli()); err != nil {
		httpStatus := http.StatusInternalServerError
		code := "internal_error"
		if err == sql.ErrNoRows {
			httpStatus = http.StatusNotFound
			code = "support_conversation_not_found"
		}
		s.recordAdminAuditLog(r, admin.User.Username, "admin.support.status", "support_conversations", userID, userID, false, httpStatus, map[string]any{"error_code": code, "status": status})
		s.writeError(w, httpStatus, code)
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.support.status", "support_conversations", userID, userID, true, http.StatusOK, map[string]any{"status": status})
	s.writeJSON(w, http.StatusOK, map[string]any{"ok": true, "user_id": userID, "status": status})
}

func (s *Server) handleAdminAppLogs(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "ops_readonly", "support", "auditor")
	if !ok {
		return
	}
	filter, validationError := parseClientAppLogQuery(r.URL.Query(), time.Now())
	if validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	logs, err := s.store.ListClientAppLogs(r.Context(), filter)
	if err != nil {
		s.logger.Error("admin list app logs failed", "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	summary, err := s.store.SummarizeClientAppLogs(r.Context(), filter)
	if err != nil {
		s.logger.Error("admin summarize app logs failed", "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.app_logs", "client_app_logs", "", filter.UserID, true, http.StatusOK, map[string]any{"row_count": len(logs), "event": filter.Event, "level": filter.Level})
	s.writeJSON(w, http.StatusOK, map[string]any{"logs": logs, "summary": summary, "filter": filter})
}

func (s *Server) handleAdminAuditLogs(w http.ResponseWriter, r *http.Request) {
	_, ok := s.requireAdmin(w, r, "auditor", "ops_readonly")
	if !ok {
		return
	}
	filter, validationError := parseAdminAuditLogQuery(r.URL.Query(), time.Now())
	if validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	logs, err := s.store.ListAdminAuditLogs(r.Context(), filter)
	if err != nil {
		s.logger.Error("admin list audit logs failed", "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"logs": logs, "filter": filter})
}

func (s *Server) handleAdminTodayAgriCards(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "content_ops", "ops_readonly", "auditor")
	if !ok {
		return
	}
	limit := parseAdminLimit(r.URL.Query().Get("limit"))
	cards, err := s.store.ListAdminDailyAgriCards(r.Context(), dailyAgriDefaultScope, limit)
	if err != nil {
		s.logger.Error("admin list daily agri cards failed", "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.today_agri.cards", "daily_agri_cards", "", "", true, http.StatusOK, map[string]any{"row_count": len(cards)})
	s.writeJSON(w, http.StatusOK, map[string]any{"cards": cards})
}

func (s *Server) handleAdminGenerateTodayAgriCard(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "owner", "content_ops")
	if !ok {
		return
	}
	dayCN := GetTodayKeyCN(s.dailyAgri.shanghai, time.Now())
	card, status, err := s.dailyAgri.GenerateToday(r.Context())
	if err != nil {
		s.logger.Error("admin generate today agri card failed", "status", status, "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.today_agri.generate", "daily_agri_cards", dayCN, "", false, http.StatusBadGateway, map[string]any{
			"status":     status,
			"error_code": "generation_failed",
		})
		s.writeJSON(w, http.StatusBadGateway, map[string]any{
			"status": status,
			"error":  "generation_failed",
		})
		return
	}
	itemCount := 0
	if card != nil {
		itemCount = len(card.Items)
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.today_agri.generate", "daily_agri_cards", dayCN, "", true, http.StatusOK, map[string]any{
		"status":     status,
		"item_count": itemCount,
	})
	s.writeJSON(w, http.StatusOK, map[string]any{
		"status":     status,
		"item_count": itemCount,
		"has_card":   card != nil,
	})
}

func (s *Server) handleAdminAppUpdateAndroid(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "owner", "release_ops", "ops_readonly", "auditor")
	if !ok {
		return
	}
	record, err := s.store.ReadAndroidUpdateConfigRecord(r.Context())
	if err != nil {
		s.logger.Error("admin app update read failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.app_update.read", "app_update", "android", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	result := buildAdminAppUpdateConfig(record)
	s.recordAdminAuditLog(r, admin.User.Username, "admin.app_update.read", "app_update", "android", "", true, http.StatusOK, map[string]any{"latest_version_code": result.LatestVersionCode, "enabled": result.Enabled, "source": result.Source})
	s.writeJSON(w, http.StatusOK, result)
}

type adminAppUpdateWriteRequest struct {
	Enabled           bool   `json:"enabled"`
	LatestVersionCode int    `json:"latest_version_code"`
	LatestVersionName string `json:"latest_version_name"`
	APKURL            string `json:"apk_url"`
	APKChecksumSHA256 string `json:"apk_sha256"`
	ReleaseNotes      string `json:"release_notes"`
	ForceUpdate       bool   `json:"force_update"`
	FileSizeBytes     int64  `json:"file_size_bytes"`
}

func buildAdminAppUpdateConfig(record androidUpdateConfigRecord) AdminAppUpdateConfig {
	cfg := record.Config
	return AdminAppUpdateConfig{
		Enabled:                   cfg.Enabled,
		Source:                    record.Source,
		LatestVersionCode:         cfg.LatestVersionCode,
		LatestVersionName:         cfg.LatestVersionName,
		APKURL:                    cfg.APKURL,
		APKChecksumSHA256:         cfg.APKChecksumSHA256,
		ReleaseNotes:              cfg.ReleaseNotes,
		ForceUpdate:               cfg.ForceUpdate,
		FileSizeBytes:             cfg.FileSizeBytes,
		UpdatedBy:                 record.UpdatedBy,
		UpdatedAt:                 record.UpdatedAt,
		ConfigValid:               androidUpdateConfigValid(cfg),
		DownloadArtifactsComplete: androidUpdateDownloadArtifactsComplete(cfg),
		HasAPKURL:                 strings.TrimSpace(cfg.APKURL) != "",
		HasSHA256:                 strings.TrimSpace(cfg.APKChecksumSHA256) != "",
		HasFileSize:               cfg.FileSizeBytes > 0,
	}
}

func (s *Server) handleAdminAppUpdateAndroidWrite(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "owner", "release_ops")
	if !ok {
		return
	}
	var body adminAppUpdateWriteRequest
	if err := decodeJSONBodyLimited(r, &body, 8*1024); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid_json")
		return
	}
	cfg := androidUpdateConfig{
		Enabled:           body.Enabled,
		LatestVersionCode: body.LatestVersionCode,
		LatestVersionName: strings.TrimSpace(body.LatestVersionName),
		APKURL:            strings.TrimSpace(body.APKURL),
		APKChecksumSHA256: normalizeSHA256Hex(body.APKChecksumSHA256),
		ReleaseNotes:      strings.TrimSpace(body.ReleaseNotes),
		ForceUpdate:       body.ForceUpdate,
		FileSizeBytes:     body.FileSizeBytes,
	}
	hasAnyConfig := cfg.LatestVersionCode > 0 ||
		cfg.LatestVersionName != "" ||
		cfg.APKURL != "" ||
		strings.TrimSpace(body.APKChecksumSHA256) != "" ||
		cfg.ReleaseNotes != "" ||
		cfg.ForceUpdate ||
		cfg.FileSizeBytes > 0
	if hasAnyConfig && cfg.LatestVersionCode <= 0 {
		s.writeError(w, http.StatusBadRequest, "latest_version_code_required")
		return
	}
	if strings.TrimSpace(body.APKChecksumSHA256) != "" && cfg.APKChecksumSHA256 == "" {
		s.writeError(w, http.StatusBadRequest, "invalid_apk_sha256")
		return
	}
	if cfg.APKURL != "" && !isHTTPSURL(cfg.APKURL) {
		s.writeError(w, http.StatusBadRequest, "invalid_apk_url")
		return
	}
	if len([]rune(cfg.ReleaseNotes)) > 200 {
		s.writeError(w, http.StatusBadRequest, "release_notes_too_long")
		return
	}
	if cfg.FileSizeBytes < 0 {
		s.writeError(w, http.StatusBadRequest, "invalid_file_size")
		return
	}
	if cfg.Enabled && !androidUpdateDownloadArtifactsComplete(cfg) {
		s.writeError(w, http.StatusBadRequest, "missing_release_artifacts")
		return
	}
	nowMs := time.Now().UnixMilli()
	if err := s.store.UpsertAndroidUpdateConfigRecord(r.Context(), cfg, admin.User.Username, nowMs); err != nil {
		s.logger.Error("admin app update write failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.app_update.write", "app_update", "android", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	record, err := s.store.ReadAndroidUpdateConfigRecord(r.Context())
	if err != nil {
		s.logger.Error("admin app update reload failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.app_update.write", "app_update", "android", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	result := buildAdminAppUpdateConfig(record)
	s.recordAdminAuditLog(r, admin.User.Username, "admin.app_update.write", "app_update", "android", "", true, http.StatusOK, map[string]any{
		"enabled":             result.Enabled,
		"latest_version_code": result.LatestVersionCode,
		"has_apk_url":         result.HasAPKURL,
		"has_sha256":          result.HasSHA256,
		"has_file_size":       result.HasFileSize,
		"force_update":        result.ForceUpdate,
	})
	s.writeJSON(w, http.StatusOK, result)
}

type AdminUserQuery struct {
	Query              string `json:"query,omitempty"`
	DayCN              string `json:"day_cn"`
	Limit              int    `json:"limit"`
	NowMs              int64  `json:"now_ms,omitempty"`
	SinceMs            int64  `json:"since_ms,omitempty"`
	IncludePhoneNumber bool   `json:"-"`
}

func adminCanViewAccountPhone(role string) bool {
	return adminRoleAllowed(role, "support", "finance_ops")
}

func (s *Store) BuildAdminOverview(ctx context.Context, health AdminHealthStatus, dayCN string, sinceMs int64, nowMs int64) (AdminOverview, error) {
	var overview AdminOverview
	overview.Health = health
	overview.NowMs = nowMs
	var err error
	if overview.Today.RegisteredUsers, err = s.countQuery(ctx, "SELECT COUNT(*) FROM app_accounts", nil); err != nil {
		return overview, err
	}
	if overview.Today.ActiveAuthSessions, err = s.countQuery(ctx, "SELECT COUNT(*) FROM auth_sessions WHERE revoked_at IS NULL AND token_expires_at > ?", []any{nowMs}); err != nil {
		return overview, err
	}
	if overview.Today.ChatRounds, err = s.countQuery(ctx, "SELECT COUNT(*) FROM session_round_archive WHERE created_at >= ?", []any{sinceMs}); err != nil {
		return overview, err
	}
	if overview.Today.ChatUsers, err = s.countQuery(ctx, `SELECT COUNT(DISTINCT COALESCE(migration.new_user_id, archive.user_id))
		FROM session_round_archive archive
		LEFT JOIN user_id_migrations migration ON migration.old_user_id = archive.user_id
		WHERE archive.created_at >= ?`, []any{sinceMs}); err != nil {
		return overview, err
	}
	if overview.Today.ImageChatRounds, err = s.countQuery(ctx, "SELECT COUNT(*) FROM session_round_archive WHERE created_at >= ? AND JSON_LENGTH(user_images_json) > 0", []any{sinceMs}); err != nil {
		return overview, err
	}
	if overview.Today.QuotaDeductions, err = s.countQuery(ctx, "SELECT COALESCE(SUM(delta),0) FROM quota_ledger WHERE created_at >= ?", []any{sinceMs}); err != nil {
		return overview, err
	}
	if overview.Today.AppErrors, err = s.countQuery(ctx, "SELECT COUNT(*) FROM client_app_logs WHERE created_at >= ? AND level = 'error'", []any{sinceMs}); err != nil {
		return overview, err
	}
	conversations, err := s.ListSupportConversations(ctx, SupportConversationQuery{SinceMs: time.Now().Add(-30 * 24 * time.Hour).UnixMilli(), Limit: maxSupportConversationListLimit})
	if err != nil {
		return overview, err
	}
	overview.Today.SupportConversations = int64(len(conversations))
	for _, item := range conversations {
		if item.NeedsReply {
			overview.Today.SupportNeedsReply++
		}
	}
	var status sql.NullString
	err = s.db.QueryRowContext(ctx, "SELECT status FROM daily_agri_cards WHERE day_cn = ? AND scope = ? LIMIT 1", dayCN, dailyAgriDefaultScope).Scan(&status)
	if err == sql.ErrNoRows {
		overview.Today.DailyAgriStatus = "missing"
	} else if err != nil {
		return overview, err
	} else {
		overview.Today.DailyAgriStatus = status.String
	}
	summary, err := s.SummarizeClientAppLogs(ctx, ClientAppLogQuery{Level: "error", SinceMs: sinceMs, Limit: 10})
	if err != nil {
		return overview, err
	}
	overview.Queues.AppErrorTop = summary
	overview.Notes = []AdminStatusNote{
		{Title: "订单 / 支付", Body: "支付尚未接入正式回调，当前后台仅保留只读入口。", Level: "info"},
		{Title: "礼品卡", Body: "礼品卡批次创建、卡状态、作废未兑换卡、兑换尝试和 Android 用户侧兑换已接入。", Level: "info"},
		{Title: "产品洞察", Body: "后续从脱敏反馈、App 日志和归档摘要生成，不直接铺完整聊天全文。", Level: "info"},
	}
	return overview, nil
}

func (s *Store) BuildAdminMonitoring(ctx context.Context, health AdminHealthStatus, dayCN string, dayStartMs int64, nowMs int64, updateCfg androidUpdateConfig) (AdminMonitoring, error) {
	report := AdminMonitoring{
		Health: health,
		NowMs:  nowMs,
		Notes: []AdminStatusNote{
			{Title: "不是完整告警中心", Body: "本页先展示业务表、App 自动日志、审计和健康检查聚合；SLS 自动告警、完整 Nginx access 仪表盘后续再接。", Level: "info"},
			{Title: "发版后先看这些", Body: "发版或切 slot 后，优先看服务异常、App 报错、后台失败、未回复反馈和今日农情状态。", Level: "info"},
			{Title: "用户隐私", Body: "监控面板不展示聊天全文、图片 URL、手机号全文、token、模型 Key 或 AccessKey。", Level: "info"},
		},
	}
	windows := []struct {
		key     string
		label   string
		sinceMs int64
	}{
		{key: "today", label: "今日", sinceMs: dayStartMs},
		{key: "24h", label: "最近24小时", sinceMs: nowMs - int64(24*time.Hour/time.Millisecond)},
		{key: "7d", label: "最近7天", sinceMs: nowMs - int64(7*24*time.Hour/time.Millisecond)},
	}
	for _, item := range windows {
		window, err := s.buildAdminMonitoringWindow(ctx, item.key, item.label, item.sinceMs, nowMs)
		if err != nil {
			return report, err
		}
		report.Windows = append(report.Windows, window)
	}
	queues, err := s.buildAdminMonitoringQueues(ctx, health, dayCN, nowMs, updateCfg)
	if err != nil {
		return report, err
	}
	report.Queues = queues
	userRegions, err := s.ReadAdminUserRegionOverview(ctx, nowMs, 10)
	if err != nil {
		return report, err
	}
	report.UserRegions = userRegions
	regions, err := s.ListAdminRegionMetrics(ctx, nowMs-int64(30*24*time.Hour/time.Millisecond), 12)
	if err != nil {
		return report, err
	}
	report.TopRegions = regions
	topErrors, err := s.SummarizeClientAppLogs(ctx, ClientAppLogQuery{Level: "error", SinceMs: nowMs - int64(24*time.Hour/time.Millisecond), Limit: 10})
	if err != nil {
		return report, err
	}
	report.TopAppErrors = topErrors
	report.LaunchReady = buildAdminMonitoringLaunchReadiness(report)
	report.ActionItems = buildAdminMonitoringActionItems(report)
	report.Capabilities = buildAdminMonitoringCapabilities()
	return report, nil
}

func (s *Store) buildAdminMonitoringWindow(ctx context.Context, key string, label string, sinceMs int64, nowMs int64) (AdminMonitoringWindow, error) {
	window := AdminMonitoringWindow{Key: key, Label: label, SinceMs: sinceMs}
	var err error
	if window.NewUsers, err = s.countQuery(ctx, "SELECT COUNT(*) FROM app_accounts WHERE created_at >= ?", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.ActiveSessions, err = s.countQuery(ctx, "SELECT COUNT(*) FROM auth_sessions WHERE revoked_at IS NULL AND token_expires_at > ?", []any{nowMs}); err != nil {
		return window, err
	}
	if window.RecentAuthSessions, err = s.countQuery(ctx, "SELECT COUNT(*) FROM auth_sessions WHERE created_at >= ?", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.ChatRounds, err = s.countQuery(ctx, "SELECT COUNT(*) FROM session_round_archive WHERE created_at >= ?", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.ChatUsers, err = s.countQuery(ctx, `SELECT COUNT(DISTINCT COALESCE(migration.new_user_id, archive.user_id))
		FROM session_round_archive archive
		LEFT JOIN user_id_migrations migration ON migration.old_user_id = archive.user_id
		WHERE archive.created_at >= ?`, []any{sinceMs}); err != nil {
		return window, err
	}
	if window.ImageChatRounds, err = s.countQuery(ctx, "SELECT COUNT(*) FROM session_round_archive WHERE created_at >= ? AND JSON_LENGTH(user_images_json) > 0", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.QuotaDeductions, err = s.countQuery(ctx, "SELECT COALESCE(SUM(delta),0) FROM quota_ledger WHERE created_at >= ?", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.AppErrors, err = s.countQuery(ctx, "SELECT COUNT(*) FROM client_app_logs WHERE created_at >= ? AND level = 'error'", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.AppWarns, err = s.countQuery(ctx, "SELECT COUNT(*) FROM client_app_logs WHERE created_at >= ? AND level = 'warn'", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.SupportMessages, err = s.countQuery(ctx, "SELECT COUNT(*) FROM support_messages WHERE created_at >= ? AND sender_type = 'user'", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.SupportUsers, err = s.countQuery(ctx, `SELECT COUNT(DISTINCT COALESCE(migration.new_user_id, messages.user_id))
		FROM support_messages messages
		LEFT JOIN user_id_migrations migration ON migration.old_user_id = messages.user_id
		WHERE messages.created_at >= ?
		  AND messages.sender_type = 'user'`, []any{sinceMs}); err != nil {
		return window, err
	}
	if window.GiftCardRedeems, err = s.countQuery(ctx, "SELECT COUNT(*) FROM gift_card_redemption_attempts WHERE created_at >= ? AND success = 1", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.GiftCardFailures, err = s.countQuery(ctx, "SELECT COUNT(*) FROM gift_card_redemption_attempts WHERE created_at >= ? AND success = 0", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.AuditFailures, err = s.countQuery(ctx, "SELECT COUNT(*) FROM admin_audit_logs WHERE created_at >= ? AND success = 0", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.AdminActions, err = s.countQuery(ctx, "SELECT COUNT(*) FROM admin_audit_logs WHERE created_at >= ?", []any{sinceMs}); err != nil {
		return window, err
	}
	if window.DailyAgriFailedCards, err = s.countQuery(ctx, "SELECT COUNT(*) FROM daily_agri_cards WHERE updated_at >= ? AND status = 'failed'", []any{sinceMs}); err != nil {
		return window, err
	}
	return window, nil
}

func (s *Store) buildAdminMonitoringQueues(ctx context.Context, health AdminHealthStatus, dayCN string, nowMs int64, updateCfg androidUpdateConfig) (AdminMonitoringQueues, error) {
	var queues AdminMonitoringQueues
	var err error
	queues.SupportNeedsReply, queues.SupportOldestPendingAt, err = s.countAdminSupportPending(ctx)
	if err != nil {
		return queues, err
	}
	queues.SupportOpen, err = s.countAdminSupportConversationsByStatus(ctx, "open")
	if err != nil {
		return queues, err
	}
	queues.SupportReplied, err = s.countAdminSupportConversationsByStatus(ctx, "replied")
	if err != nil {
		return queues, err
	}
	queues.SupportClosed, err = s.countAdminSupportConversationsByStatus(ctx, "closed")
	if err != nil {
		return queues, err
	}
	queues.DailyAgriStatus, queues.DailyAgriUpdatedAt, queues.DailyAgriError, err = s.readAdminDailyAgriQueue(ctx, dayCN)
	if err != nil {
		return queues, err
	}
	hasAPKURL := strings.TrimSpace(updateCfg.APKURL) != ""
	hasSHA256 := strings.TrimSpace(updateCfg.APKChecksumSHA256) != ""
	hasFileSize := updateCfg.FileSizeBytes > 0
	queues.AppUpdate = AdminMonitoringAppUpdate{
		Enabled:                   updateCfg.Enabled,
		ConfigValid:               androidUpdateConfigValid(updateCfg),
		DownloadArtifactsComplete: androidUpdateDownloadArtifactsComplete(updateCfg),
		HasAPKURL:                 hasAPKURL,
		HasSHA256:                 hasSHA256,
		HasFileSize:               hasFileSize,
		LatestVersionCode:         updateCfg.LatestVersionCode,
		LatestVersionName:         updateCfg.LatestVersionName,
		ForceUpdate:               updateCfg.ForceUpdate,
	}
	if queues.GiftCardBatchCount, err = s.countQuery(ctx, "SELECT COUNT(*) FROM gift_card_batches", nil); err != nil {
		return queues, err
	}
	if queues.GiftCardTotal, err = s.countQuery(ctx, "SELECT COUNT(*) FROM gift_cards", nil); err != nil {
		return queues, err
	}
	if queues.GiftCardActive, err = s.countQuery(ctx, "SELECT COUNT(*) FROM gift_cards WHERE status = 'active' AND valid_from <= ? AND (valid_until IS NULL OR valid_until > ?)", []any{nowMs, nowMs}); err != nil {
		return queues, err
	}
	if queues.GiftCardRedeemed, err = s.countQuery(ctx, "SELECT COUNT(*) FROM gift_cards WHERE status = 'redeemed'", nil); err != nil {
		return queues, err
	}
	if queues.GiftCardFailedAttempts, err = s.countQuery(ctx, "SELECT COUNT(*) FROM gift_card_redemption_attempts WHERE created_at >= ? AND success = 0", []any{nowMs - int64(24*time.Hour/time.Millisecond)}); err != nil {
		return queues, err
	}
	if queues.AuditFailures, err = s.countQuery(ctx, "SELECT COUNT(*) FROM admin_audit_logs WHERE created_at >= ? AND success = 0", []any{nowMs - int64(24*time.Hour/time.Millisecond)}); err != nil {
		return queues, err
	}
	if queues.AppErrors, err = s.countQuery(ctx, "SELECT COUNT(*) FROM client_app_logs WHERE created_at >= ? AND level = 'error'", []any{nowMs - int64(24*time.Hour/time.Millisecond)}); err != nil {
		return queues, err
	}
	queues.UnreadyDependencyCount = countUnreadyAdminDependencies(health)
	return queues, nil
}

func buildAdminMonitoringActionItems(report AdminMonitoring) []AdminMonitoringActionItem {
	queues := report.Queues
	items := make([]AdminMonitoringActionItem, 0, 8)
	if queues.UnreadyDependencyCount > 0 {
		items = append(items, AdminMonitoringActionItem{
			Title: "服务依赖异常",
			Body:  "模型、登录、Redis 或上传存储有异常，先打开服务健康页确认是哪一项。",
			Level: "bad",
			Route: "health",
			Count: queues.UnreadyDependencyCount,
		})
	}
	if queues.AppErrors > 0 {
		level := "warn"
		if queues.AppErrors >= 10 {
			level = "bad"
		}
		items = append(items, AdminMonitoringActionItem{
			Title: "App 报错需要看",
			Body:  "最近 24 小时有 error 自动日志，先看 Top 事件和具体版本。",
			Level: level,
			Route: "app-logs",
			Count: queues.AppErrors,
		})
	}
	if queues.SupportNeedsReply > 0 {
		items = append(items, AdminMonitoringActionItem{
			Title: "有用户反馈待回复",
			Body:  "帮助反馈已有工单状态，先处理 open 队列；回复后可关闭或标已处理。",
			Level: "warn",
			Route: "support",
			Count: queues.SupportNeedsReply,
		})
	}
	switch strings.ToLower(strings.TrimSpace(queues.DailyAgriStatus)) {
	case "ready":
	case "failed":
		items = append(items, AdminMonitoringActionItem{
			Title: "今日农情生成失败",
			Body:  firstNonEmpty(queues.DailyAgriError, "打开今日农情页查看最近失败原因，并直接补跑当天卡片。"),
			Level: "bad",
			Route: "today-agri",
		})
	case "pending", "running", "missing", "disabled", "":
		items = append(items, AdminMonitoringActionItem{
			Title: "今日农情未就绪",
			Body:  "今天的农情卡片还没有 ready，发布前或早晨巡检时需要确认；必要时可在后台直接补跑。",
			Level: "warn",
			Route: "today-agri",
		})
	}
	if queues.GiftCardBatchCount == 0 || queues.GiftCardTotal == 0 {
		items = append(items, AdminMonitoringActionItem{
			Title: "还没有生产礼品卡",
			Body:  "生产库没有礼品卡批次和卡码；先在礼品卡页生成 1 张正式卡，再用 Android 礼品卡入口兑换测试。",
			Level: "warn",
			Route: "gift-cards",
		})
	} else if queues.GiftCardActive == 0 {
		items = append(items, AdminMonitoringActionItem{
			Title: "没有可兑换礼品卡",
			Body:  "已有礼品卡记录，但当前没有生效且未过期的 active 卡；测试兑换前需要新生成或检查有效期。",
			Level: "warn",
			Route: "gift-cards",
			Count: queues.GiftCardTotal,
		})
	}
	if queues.GiftCardFailedAttempts > 0 {
		items = append(items, AdminMonitoringActionItem{
			Title: "礼品卡兑换失败偏多",
			Body:  "先看尾号、失败原因和用户，判断是输错码、过期、已作废还是撞库尝试。",
			Level: "warn",
			Route: "gift-cards",
			Count: queues.GiftCardFailedAttempts,
		})
	}
	if queues.AuditFailures > 0 {
		items = append(items, AdminMonitoringActionItem{
			Title: "后台操作失败",
			Body:  "最近 24 小时有后台失败动作，优先看审计页确认是否权限、CSRF 或接口错误。",
			Level: "bad",
			Route: "audit",
			Count: queues.AuditFailures,
		})
	}
	if !queues.AppUpdate.ConfigValid {
		items = append(items, AdminMonitoringActionItem{
			Title: "安装包配置非法",
			Body:  "检查更新配置至少要有版本号；如果配置 APK 地址，必须使用 HTTPS。",
			Level: "warn",
			Route: "app-update",
		})
	}
	if queues.AppUpdate.ConfigValid && !queues.AppUpdate.DownloadArtifactsComplete {
		items = append(items, AdminMonitoringActionItem{
			Title: "正式 APK 下载物料未齐",
			Body:  "上架前建议同时配置 HTTPS APK、SHA-256 和文件大小，避免用户下载不可控文件。",
			Level: "warn",
			Route: "app-update",
		})
	}
	if len(items) == 0 {
		items = append(items, AdminMonitoringActionItem{
			Title: "当前没有必须马上处理的事项",
			Body:  "继续观察 App 报错、反馈、今日农情和礼品卡兑换即可。",
			Level: "ok",
		})
	}
	return items
}

func buildAdminMonitoringLaunchReadiness(report AdminMonitoring) []AdminMonitoringLaunchItem {
	queues := report.Queues
	health := report.Health
	items := make([]AdminMonitoringLaunchItem, 0, 10)
	dependenciesOK := queues.UnreadyDependencyCount == 0 && health.AuthStrict && !health.DevOrderEndpoints
	items = append(items, AdminMonitoringLaunchItem{
		Title:  "后端生产健康",
		Status: ternary(dependenciesOK, "ready", "blocked"),
		Body: ternary(
			dependenciesOK,
			"API、模型、登录依赖、Redis、上传存储和严格鉴权当前可作为生产运行基础。",
			"服务依赖、严格鉴权或开发期订单开关存在异常，先看服务健康。",
		),
		Route: "health",
		Owner: "运维",
	})
	loginDepsOK := health.AuthStrict &&
		strings.EqualFold(health.Dypns, "ok") &&
		strings.EqualFold(health.DypnsFusion, "ok") &&
		strings.EqualFold(health.DypnsSMS, "ok") &&
		strings.EqualFold(health.Redis, "ok")
	items = append(items, AdminMonitoringLaunchItem{
		Title:  "手机号登录",
		Status: ternary(loginDepsOK, "attention", "blocked"),
		Body: ternary(
			loginDepsOK,
			"云端配置和后端限流正常；正式上架前仍必须用真机确认一键登录和短信验证码。",
			"一键登录 / 短信 / Redis / 严格鉴权任一异常都会挡住正式登录。",
		),
		Route: "health",
		Owner: "Android / 后端",
	})
	modelOK := strings.EqualFold(health.API, "ok") && strings.EqualFold(health.Bailian, "ok")
	items = append(items, AdminMonitoringLaunchItem{
		Title:  "模型问诊",
		Status: ternary(modelOK, "ready", "blocked"),
		Body: ternary(
			modelOK,
			"主模型 Key 已配置，后端统一发起模型调用；文字 / 图片问诊链路具备生产运行条件。",
			"模型或 API 健康异常时，主聊天不能作为正式可用状态。",
		),
		Route: "health",
		Owner: "后端",
	})
	giftStatus := "ready"
	giftBody := "有可兑换礼品卡，可在 Android 设置页兑换并在后台追溯账号ID。"
	if queues.GiftCardBatchCount == 0 || queues.GiftCardTotal == 0 {
		giftStatus = "blocked"
		giftBody = "生产库还没有生成礼品卡；先在后台生成正式卡，才能支撑权益发放。"
	} else if queues.GiftCardActive == 0 {
		giftStatus = "blocked"
		giftBody = "已有礼品卡记录，但当前没有生效且未过期的 active 卡；先生成或检查有效期。"
	} else if queues.GiftCardFailedAttempts > 0 {
		giftStatus = "attention"
		giftBody = "已有可兑换卡，但最近 24 小时存在失败尝试；先看尾号和失败原因。"
	}
	items = append(items, AdminMonitoringLaunchItem{
		Title:  "礼品卡权益",
		Status: giftStatus,
		Body:   giftBody,
		Route:  "gift-cards",
		Owner:  "运营 / 后端",
	})
	updateStatus := "ready"
	updateBody := "版本号、HTTPS APK、SHA-256 和文件大小已齐，可进入真机覆盖安装验证。"
	if !queues.AppUpdate.ConfigValid {
		updateStatus = "blocked"
		updateBody = "检查更新配置非法；至少需要合法版本号，APK 地址必须是 HTTPS。"
	} else if !queues.AppUpdate.Enabled {
		updateStatus = "attention"
		updateBody = "检查更新当前处于停更状态；用户点“检查更新”不会拿到新包。"
	} else if !queues.AppUpdate.DownloadArtifactsComplete {
		updateStatus = "attention"
		updateBody = "检查更新配置合法，但正式下载物料未齐；上架前补 HTTPS APK、SHA-256 和文件大小。"
	}
	items = append(items, AdminMonitoringLaunchItem{
		Title:  "安装包更新",
		Status: updateStatus,
		Body:   updateBody,
		Route:  "app-update",
		Owner:  "发布",
	})
	items = append(items, AdminMonitoringLaunchItem{
		Title:  "支付接入",
		Status: "blocked",
		Body:   "微信 / 支付宝支付申请和真实回调未完成；不能把会员订单当正式收费功能，礼品卡先支撑正式权益发放。",
		Route:  "orders",
		Owner:  "外部申请 / 后端",
	})
	items = append(items, AdminMonitoringLaunchItem{
		Title:  "备案与上架材料",
		Status: "blocked",
		Body:   "App 备案、App 公安备案、应用商店物料和上线前 AccessKey 轮换仍是正式上架阻塞项。",
		Owner:  "法务 / 运营",
	})
	slsStatus := "attention"
	if queues.AppErrors >= 10 || queues.AuditFailures > 0 {
		slsStatus = "blocked"
	}
	items = append(items, AdminMonitoringLaunchItem{
		Title:  "日志告警",
		Status: slsStatus,
		Body:   "Go 日志和 Nginx error 已进 SLS；自动告警和仪表盘还未接，先用后台 App 日志 / 审计 / 服务健康巡检。",
		Route:  "app-logs",
		Owner:  "运维",
	})
	supportStatus := "ready"
	supportBody := "后台可看反馈会话并回复；正式运营前继续补工单状态、分配和搜索。"
	if queues.SupportNeedsReply > 0 {
		supportStatus = "attention"
		supportBody = "有用户反馈待回复；先处理，再继续公开测试。"
	}
	items = append(items, AdminMonitoringLaunchItem{
		Title:  "客服反馈",
		Status: supportStatus,
		Body:   supportBody,
		Route:  "support",
		Owner:  "客服 / 运营",
	})
	return items
}

func buildAdminMonitoringCapabilities() []AdminMonitoringCapability {
	return []AdminMonitoringCapability{
		{Title: "服务健康", Status: "ready", Body: "API、模型、登录、Redis、OSS、严格鉴权都能集中看。", Route: "health"},
		{Title: "账号登录", Status: "ready", Body: "手机号、一键登录、短信登录统一归到账号ID；旧本机 UUID 只做迁移桥。", Route: "users"},
		{Title: "App 日志", Status: "ready", Body: "自动日志明细和事件 Top 已接入，不展示聊天正文或图片 URL。", Route: "app-logs"},
		{Title: "帮助反馈", Status: "ready", Body: "可看待回复 / 已回复 / 已关闭队列，发送后台回复并关闭或重开会话。", Route: "support"},
		{Title: "礼品卡", Status: "ready", Body: "可生成批次、直接查看完整卡码、按账号ID / 批次 / 尾号追溯、查失败原因并作废未兑换卡。", Route: "gift-cards"},
		{Title: "今日农情", Status: "ready", Body: "可看生成状态、来源数量和失败原因；owner / content_ops 可直接补跑当天卡片。", Route: "today-agri"},
		{Title: "检查更新", Status: "ready", Body: "后台可直接维护 Android 版本、APK、SHA-256、文件大小、强制更新和停更状态；用户仍通过“检查更新”拉取新包。", Route: "app-update"},
		{Title: "订单支付", Status: "planned", Body: "真实支付、退款、对账、自动续费和补发权益仍未接入。", Route: "orders"},
		{Title: "SLS 告警", Status: "planned", Body: "日志已采集到 SLS，自动告警和仪表盘还要继续补。", Route: "health"},
		{Title: "产品洞察", Status: "planned", Body: "后续做脱敏聚合报表，不直接铺完整聊天内容。", Route: "insights"},
	}
}

func filterAdminMonitoringActionRoutes(items []AdminMonitoringActionItem, role string) []AdminMonitoringActionItem {
	for idx := range items {
		if items[idx].Route != "" && !adminRouteAllowed(role, items[idx].Route) {
			items[idx].Route = ""
		}
	}
	return items
}

func filterAdminMonitoringCapabilityRoutes(items []AdminMonitoringCapability, role string) []AdminMonitoringCapability {
	for idx := range items {
		if items[idx].Route != "" && !adminRouteAllowed(role, items[idx].Route) {
			items[idx].Route = ""
		}
	}
	return items
}

func filterAdminMonitoringLaunchRoutes(items []AdminMonitoringLaunchItem, role string) []AdminMonitoringLaunchItem {
	for idx := range items {
		if items[idx].Route != "" && !adminRouteAllowed(role, items[idx].Route) {
			items[idx].Route = ""
		}
	}
	return items
}

func adminRouteAllowed(role string, route string) bool {
	switch strings.TrimSpace(route) {
	case "", "overview", "monitoring", "health", "insights":
		return adminRoleAllowed(role)
	case "users", "entitlements", "orders":
		return adminRoleAllowed(role, "ops_readonly", "support", "finance_ops")
	case "gift-cards":
		return adminRoleAllowed(role, "finance_ops", "ops_readonly", "auditor")
	case "support":
		return adminRoleAllowed(role, "support", "ops_readonly", "auditor")
	case "app-logs":
		return adminRoleAllowed(role, "ops_readonly", "support", "auditor")
	case "today-agri":
		return adminRoleAllowed(role, "content_ops", "ops_readonly", "auditor")
	case "app-update":
		return adminRoleAllowed(role, "release_ops", "ops_readonly", "auditor")
	case "audit":
		return adminRoleAllowed(role, "auditor", "ops_readonly")
	default:
		return false
	}
}

func (s *Store) countAdminSupportPending(ctx context.Context) (int64, *int64, error) {
	var count int64
	var oldest sql.NullInt64
	err := s.db.QueryRowContext(
		ctx,
		`SELECT COUNT(*), MIN(latest_user_at)
		   FROM (
		     SELECT
		       CASE
		         WHEN conversations.status = 'closed' AND latest_user_summary.latest_user_at > COALESCE(conversations.closed_at, 0) THEN 'open'
		         WHEN conversations.status IN ('open', 'replied', 'closed') THEN conversations.status
		         WHEN latest_non_system_message.sender_type = 'user' THEN 'open'
		         ELSE 'replied'
		       END AS conversation_status,
		       latest_user_summary.latest_user_at
		     FROM support_messages latest_non_system_message
		     JOIN (
		       SELECT user_id, MAX(id) AS latest_id
		         FROM support_messages
		        WHERE sender_type <> 'system'
		        GROUP BY user_id
		     ) latest_ids ON latest_ids.latest_id = latest_non_system_message.id
		     LEFT JOIN support_conversations conversations ON conversations.user_id = latest_non_system_message.user_id
		     LEFT JOIN (
		       SELECT user_id, MAX(created_at) AS latest_user_at
		         FROM support_messages
		        WHERE sender_type = 'user'
		        GROUP BY user_id
		     ) latest_user_summary ON latest_user_summary.user_id = latest_non_system_message.user_id
		   ) support_status
		  WHERE conversation_status = 'open'`,
	).Scan(&count, &oldest)
	if err != nil {
		return 0, nil, err
	}
	if oldest.Valid {
		return count, int64Ptr(oldest.Int64), nil
	}
	return count, nil, nil
}

func (s *Store) countAdminSupportConversationsByStatus(ctx context.Context, status string) (int64, error) {
	var count int64
	err := s.db.QueryRowContext(
		ctx,
		`SELECT COUNT(*)
		   FROM (
		     SELECT
		       CASE
		         WHEN conversations.status = 'closed' AND latest_user_summary.latest_user_at > COALESCE(conversations.closed_at, 0) THEN 'open'
		         WHEN conversations.status IN ('open', 'replied', 'closed') THEN conversations.status
		         WHEN latest_non_system_message.sender_type = 'user' THEN 'open'
		         ELSE 'replied'
		       END AS conversation_status
		       FROM support_messages latest_non_system_message
		       JOIN (
		         SELECT user_id, MAX(id) AS latest_id
		           FROM support_messages
		          WHERE sender_type <> 'system'
		          GROUP BY user_id
		       ) latest_ids ON latest_ids.latest_id = latest_non_system_message.id
		       LEFT JOIN support_conversations conversations ON conversations.user_id = latest_non_system_message.user_id
		       LEFT JOIN (
		         SELECT user_id, MAX(created_at) AS latest_user_at
		           FROM support_messages
		          WHERE sender_type = 'user'
		          GROUP BY user_id
		       ) latest_user_summary ON latest_user_summary.user_id = latest_non_system_message.user_id
		   ) support_status
		  WHERE conversation_status = ?`,
		status,
	).Scan(&count)
	return count, err
}

func (s *Store) readAdminDailyAgriQueue(ctx context.Context, dayCN string) (string, *int64, string, error) {
	var status, errorText sql.NullString
	var updatedAt sql.NullInt64
	err := s.db.QueryRowContext(
		ctx,
		"SELECT status, updated_at, error FROM daily_agri_cards WHERE day_cn = ? AND scope = ? LIMIT 1",
		dayCN,
		dailyAgriDefaultScope,
	).Scan(&status, &updatedAt, &errorText)
	if err == sql.ErrNoRows {
		return "missing", nil, "", nil
	}
	if err != nil {
		return "", nil, "", err
	}
	return nullStringValue(status), nullInt64ToPtr(updatedAt), nullStringValue(errorText), nil
}

func (s *Store) ListAdminRegionMetrics(ctx context.Context, sinceMs int64, limit int) ([]AdminRegionMetric, error) {
	if limit <= 0 || limit > 50 {
		limit = 12
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT
		   archive.region,
		   CASE
		     WHEN SUM(CASE WHEN archive.region_source = 'gps' THEN 1 ELSE 0 END) > 0 THEN 'gps'
		     WHEN SUM(CASE WHEN archive.region_source = 'ip' THEN 1 ELSE 0 END) > 0 THEN 'ip'
		     ELSE COALESCE(MAX(archive.region_source), '')
		   END AS source,
		   CASE
		     WHEN SUM(CASE WHEN archive.region_reliability = 'reliable' THEN 1 ELSE 0 END) > 0 THEN 'reliable'
		     WHEN SUM(CASE WHEN archive.region_reliability = 'unreliable' THEN 1 ELSE 0 END) > 0 THEN 'unreliable'
		     ELSE COALESCE(MAX(archive.region_reliability), '')
		   END AS reliability,
		   COUNT(*) AS count_value,
		   COUNT(DISTINCT COALESCE(migration.new_user_id, archive.user_id)) AS user_count,
		   MAX(archive.created_at) AS last_seen_at
		 FROM session_round_archive archive
		 LEFT JOIN user_id_migrations migration ON migration.old_user_id = archive.user_id
		 WHERE archive.created_at >= ?
		   AND archive.region IS NOT NULL
		   AND archive.region <> ''
		   AND archive.region <> '未知'
		 GROUP BY archive.region
		 ORDER BY count_value DESC, last_seen_at DESC
		 LIMIT ?`,
		sinceMs,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var entries []AdminRegionMetric
	for rows.Next() {
		var entry AdminRegionMetric
		if err := rows.Scan(&entry.Region, &entry.Source, &entry.Reliability, &entry.Count, &entry.UserCount, &entry.LastSeenAt); err != nil {
			return nil, err
		}
		entries = append(entries, entry)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	if entries == nil {
		return []AdminRegionMetric{}, nil
	}
	return entries, nil
}

func (s *Store) ReadAdminUserRegionOverview(ctx context.Context, nowMs int64, limit int) (AdminUserRegionOverview, error) {
	overview := AdminUserRegionOverview{
		RegisteredTop: []AdminRegionMetric{},
		MemberTop:     []AdminRegionMetric{},
	}
	var err error
	overview.RegisteredTotal, overview.RegisteredWithRegion, err = s.readAdminAccountRegionCoverage(ctx, nowMs, false)
	if err != nil {
		return overview, err
	}
	overview.MemberTotal, overview.MemberWithRegion, err = s.readAdminAccountRegionCoverage(ctx, nowMs, true)
	if err != nil {
		return overview, err
	}
	overview.RegisteredTop, err = s.ListAdminAccountRegionMetrics(ctx, nowMs, limit, false)
	if err != nil {
		return overview, err
	}
	overview.MemberTop, err = s.ListAdminAccountRegionMetrics(ctx, nowMs, limit, true)
	if err != nil {
		return overview, err
	}
	return overview, nil
}

func (s *Store) readAdminAccountRegionCoverage(ctx context.Context, nowMs int64, paidOnly bool) (int64, int64, error) {
	base := `SELECT
	  COUNT(*) AS total_count,
	  COALESCE(SUM(CASE WHEN sa.last_region IS NOT NULL AND sa.last_region <> '' AND sa.last_region <> '未知' THEN 1 ELSE 0 END), 0) AS with_region
	FROM app_accounts a
	LEFT JOIN session_ab sa ON sa.user_id = a.user_id`
	args := []any{}
	if paidOnly {
		base += `
	INNER JOIN user_entitlement e ON e.user_id = a.user_id
	WHERE e.tier IN ('plus', 'pro') AND e.tier_expire_at IS NOT NULL AND e.tier_expire_at > ?`
		args = append(args, nowMs)
	}
	var total int64
	var withRegion int64
	if err := s.db.QueryRowContext(ctx, base, args...).Scan(&total, &withRegion); err != nil {
		return 0, 0, err
	}
	return total, withRegion, nil
}

func (s *Store) ListAdminAccountRegionMetrics(ctx context.Context, nowMs int64, limit int, paidOnly bool) ([]AdminRegionMetric, error) {
	if limit <= 0 || limit > 50 {
		limit = 10
	}
	query := `SELECT
	   sa.last_region AS region,
	   CASE
	     WHEN SUM(CASE WHEN sa.last_region_source = 'gps' THEN 1 ELSE 0 END) > 0 THEN 'gps'
	     WHEN SUM(CASE WHEN sa.last_region_source = 'ip' THEN 1 ELSE 0 END) > 0 THEN 'ip'
	     ELSE COALESCE(MAX(sa.last_region_source), '')
	   END AS source,
	   CASE
	     WHEN SUM(CASE WHEN sa.last_region_reliability = 'reliable' THEN 1 ELSE 0 END) > 0 THEN 'reliable'
	     WHEN SUM(CASE WHEN sa.last_region_reliability = 'unreliable' THEN 1 ELSE 0 END) > 0 THEN 'unreliable'
	     ELSE COALESCE(MAX(sa.last_region_reliability), '')
	   END AS reliability,
	   COUNT(*) AS count_value,
	   COUNT(*) AS user_count,
	   MAX(COALESCE(sa.last_seen_at, a.last_login_at, a.updated_at, a.created_at)) AS last_seen_at
	 FROM app_accounts a
	 INNER JOIN session_ab sa ON sa.user_id = a.user_id`
	args := []any{}
	if paidOnly {
		query += `
	 INNER JOIN user_entitlement e ON e.user_id = a.user_id`
	}
	query += `
	 WHERE sa.last_region IS NOT NULL
	   AND sa.last_region <> ''
	   AND sa.last_region <> '未知'`
	if paidOnly {
		query += `
	   AND e.tier IN ('plus', 'pro')
	   AND e.tier_expire_at IS NOT NULL
	   AND e.tier_expire_at > ?`
		args = append(args, nowMs)
	}
	query += `
	 GROUP BY sa.last_region
	 ORDER BY count_value DESC, last_seen_at DESC
	 LIMIT ?`
	args = append(args, limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	entries := []AdminRegionMetric{}
	for rows.Next() {
		var entry AdminRegionMetric
		if err := rows.Scan(&entry.Region, &entry.Source, &entry.Reliability, &entry.Count, &entry.UserCount, &entry.LastSeenAt); err != nil {
			return nil, err
		}
		entries = append(entries, entry)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return entries, nil
}

func countUnreadyAdminDependencies(health AdminHealthStatus) int64 {
	var count int64
	if strings.ToLower(strings.TrimSpace(health.API)) != "ok" {
		count++
	}
	if strings.ToLower(strings.TrimSpace(health.Bailian)) != "ok" {
		count++
	}
	if strings.ToLower(strings.TrimSpace(health.Dypns)) != "ok" {
		count++
	}
	if strings.ToLower(strings.TrimSpace(health.DypnsFusion)) != "ok" {
		count++
	}
	if strings.ToLower(strings.TrimSpace(health.DypnsSMS)) != "ok" {
		count++
	}
	if strings.ToLower(strings.TrimSpace(health.Redis)) != "ok" {
		count++
	}
	upload := strings.ToLower(strings.TrimSpace(health.UploadStorage))
	if upload != "oss" && upload != "ok" {
		count++
	}
	if !health.AuthStrict {
		count++
	}
	if health.DevOrderEndpoints {
		count++
	}
	return count
}

func (s *Store) ListAdminUsers(ctx context.Context, filter AdminUserQuery) ([]AdminUserListEntry, error) {
	limit := filter.Limit
	if limit <= 0 || limit > maxAdminListLimit {
		limit = defaultAdminListLimit
	}
	nowMs := filter.NowMs
	if nowMs <= 0 {
		nowMs = time.Now().UnixMilli()
	}
	sinceMs := filter.SinceMs
	if sinceMs <= 0 {
		sinceMs = time.Now().Add(-24 * time.Hour).UnixMilli()
	}
	query := `SELECT
	   a.user_id,
	   a.phone_mask,
	   a.phone_ciphertext,
	   a.created_at,
	   a.updated_at,
	   a.last_login_at,
	   COALESCE(e.tier, 'free') AS tier,
	   e.tier_expire_at,
	   COALESCE(sa.round_total, 0) AS round_total,
	   sa.last_seen_at,
	   sa.last_region,
	   sa.last_region_source,
	   sa.last_region_reliability,
	   (SELECT COUNT(*) FROM auth_sessions auth WHERE auth.user_id = a.user_id AND auth.revoked_at IS NULL AND auth.token_expires_at > ?) AS active_sessions,
	   (SELECT COUNT(*) FROM client_app_logs logs WHERE logs.user_id = a.user_id AND logs.created_at >= ? AND logs.level = 'error') AS error_count_24h,
	   (SELECT COUNT(*) FROM support_messages sm WHERE sm.user_id = a.user_id) AS support_message_count,
	   (SELECT sm.sender_type FROM support_messages sm WHERE sm.user_id = a.user_id AND sm.sender_type <> 'system' ORDER BY sm.id DESC LIMIT 1) AS latest_support_sender
	 FROM app_accounts a
	 LEFT JOIN user_entitlement e ON e.user_id = a.user_id
	 LEFT JOIN session_ab sa ON sa.user_id = a.user_id`
	args := []any{nowMs, sinceMs}
	where := []string{}
	if strings.TrimSpace(filter.Query) != "" {
		like := "%" + strings.TrimSpace(filter.Query) + "%"
		where = append(where, "(a.user_id LIKE ? OR a.phone_mask LIKE ?")
		args = append(args, like, like)
		if hash := accountPhoneHashForSearch(filter.Query); hash != "" {
			where[len(where)-1] += " OR a.phone_hash = ?"
			args = append(args, hash)
		}
		where[len(where)-1] += ")"
	}
	if len(where) > 0 {
		query += " WHERE " + strings.Join(where, " AND ")
	}
	query += " ORDER BY COALESCE(a.last_login_at, a.updated_at, a.created_at) DESC LIMIT ?"
	args = append(args, limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	users := []AdminUserListEntry{}
	for rows.Next() {
		user, err := scanAdminUserListEntry(rows, filter.DayCN, filter.IncludePhoneNumber)
		if err != nil {
			return nil, err
		}
		if err := s.fillAdminUserQuota(ctx, &user, filter.DayCN); err != nil {
			return nil, err
		}
		users = append(users, user)
	}
	return users, rows.Err()
}

func (s *Store) GetAdminUserDetail(ctx context.Context, userID string, dayCN string, nowMs int64, includePhoneNumber bool) (AdminUserDetail, error) {
	users, err := s.ListAdminUsers(ctx, AdminUserQuery{Query: userID, DayCN: dayCN, Limit: 1, NowMs: nowMs, SinceMs: time.Now().Add(-24 * time.Hour).UnixMilli(), IncludePhoneNumber: includePhoneNumber})
	if err != nil {
		return AdminUserDetail{}, err
	}
	var user AdminUserListEntry
	for _, item := range users {
		if item.UserID == userID {
			user = item
			break
		}
	}
	if user.UserID == "" {
		return AdminUserDetail{}, sql.ErrNoRows
	}
	ledger, err := s.ListAdminQuotaLedger(ctx, userID, 50)
	if err != nil {
		return AdminUserDetail{}, err
	}
	topupPacks, err := s.ListAdminTopupPacks(ctx, userID, 20)
	if err != nil {
		return AdminUserDetail{}, err
	}
	upgradeCredits, err := s.ListAdminUpgradeCredits(ctx, userID)
	if err != nil {
		return AdminUserDetail{}, err
	}
	rounds, err := s.ListAdminRoundExcerpts(ctx, userID, 12)
	if err != nil {
		return AdminUserDetail{}, err
	}
	appLogs, err := s.ListClientAppLogs(ctx, ClientAppLogQuery{UserID: userID, SinceMs: time.Now().Add(-7 * 24 * time.Hour).UnixMilli(), Limit: 20})
	if err != nil {
		return AdminUserDetail{}, err
	}
	supportSummary, err := s.GetSupportSummary(ctx, userID)
	if err != nil {
		return AdminUserDetail{}, err
	}
	supportRaw, err := s.ListSupportMessages(ctx, userID, 10)
	if err != nil {
		return AdminUserDetail{}, err
	}
	supportMessages := make([]AdminSupportMessage, 0, len(supportRaw))
	for _, message := range supportRaw {
		supportMessages = append(supportMessages, adminSupportMessageFromSupport(message, false))
	}
	orders, err := s.ListAdminOrders(ctx, userID, 20)
	if err != nil {
		return AdminUserDetail{}, err
	}
	giftCards, err := s.ListGiftCards(ctx, GiftCardListQuery{UserID: userID, Limit: 20})
	if err != nil {
		return AdminUserDetail{}, err
	}
	giftAttempts, err := s.ListGiftCardAttempts(ctx, GiftCardAttemptQuery{UserID: userID, Limit: 20})
	if err != nil {
		return AdminUserDetail{}, err
	}
	return AdminUserDetail{
		User:             user,
		QuotaLedger:      ledger,
		TopupPacks:       topupPacks,
		UpgradeCredits:   upgradeCredits,
		RecentRounds:     rounds,
		RecentAppLogs:    appLogs,
		SupportSummary:   supportSummary,
		SupportMessages:  supportMessages,
		Orders:           orders,
		GiftCards:        giftCards,
		GiftCardAttempts: giftAttempts,
	}, nil
}

func (s *Store) fillAdminUserQuota(ctx context.Context, user *AdminUserListEntry, dayCN string) error {
	if user == nil {
		return nil
	}
	status, err := s.ReadDailyStatus(ctx, user.UserID, user.Tier, dayCN)
	if err != nil {
		return err
	}
	user.Daily = status
	topup, topupExpire, err := s.GetTopupStatus(ctx, user.UserID)
	if err != nil {
		return err
	}
	user.TopupRemaining = topup
	user.TopupExpireAt = topupExpire
	upgrade, err := s.GetUpgradeRemaining(ctx, user.UserID)
	if err != nil {
		return err
	}
	user.UpgradeRemaining = upgrade
	return nil
}

func (s *Store) ReadDailyStatus(ctx context.Context, userID string, tier Tier, dayCN string) (DailyQuotaStatus, error) {
	var used sql.NullInt64
	err := s.db.QueryRowContext(ctx, "SELECT used FROM daily_usage WHERE user_id = ? AND day_cn = ? LIMIT 1", userID, dayCN).Scan(&used)
	if err == sql.ErrNoRows {
		used.Int64 = 0
	} else if err != nil {
		return DailyQuotaStatus{}, err
	}
	limit := tierLimits[tier]
	if limit <= 0 {
		limit = tierLimits[TierFree]
	}
	return DailyQuotaStatus{DayCN: dayCN, Tier: tier, Used: int(used.Int64), Limit: limit, Remaining: maxInt(0, limit-int(used.Int64))}, nil
}

func (s *Store) ReadAdminEntitlementSummary(ctx context.Context, dayCN string, nowMs int64) (AdminEntitlementSummary, error) {
	summary := AdminEntitlementSummary{NowMs: nowMs}
	var err error
	if summary.RegisteredUsers, err = s.countQuery(ctx, "SELECT COUNT(*) FROM app_accounts", nil); err != nil {
		return summary, err
	}
	if summary.PlusUsers, err = s.countQuery(ctx, "SELECT COUNT(*) FROM user_entitlement WHERE tier = 'plus' AND tier_expire_at IS NOT NULL AND tier_expire_at > ?", []any{nowMs}); err != nil {
		return summary, err
	}
	if summary.ProUsers, err = s.countQuery(ctx, "SELECT COUNT(*) FROM user_entitlement WHERE tier = 'pro' AND tier_expire_at IS NOT NULL AND tier_expire_at > ?", []any{nowMs}); err != nil {
		return summary, err
	}
	summary.MemberUsers = summary.PlusUsers + summary.ProUsers
	if summary.LegacyMemberUsers, err = s.countQuery(ctx, `SELECT COUNT(*)
		FROM user_entitlement e
		LEFT JOIN app_accounts a ON a.user_id = e.user_id
		WHERE e.tier IN ('plus','pro')
		  AND e.tier_expire_at IS NOT NULL
		  AND e.tier_expire_at > ?
		  AND a.user_id IS NULL`, []any{nowMs}); err != nil {
		return summary, err
	}
	summary.AccountMemberUsers = summary.MemberUsers - summary.LegacyMemberUsers
	if summary.AccountMemberUsers < 0 {
		summary.AccountMemberUsers = 0
	}
	summary.FreeUsers = summary.RegisteredUsers - summary.AccountMemberUsers
	if summary.FreeUsers < 0 {
		summary.FreeUsers = 0
	}
	day7Ms := nowMs + int64(7*24*time.Hour/time.Millisecond)
	day30Ms := nowMs + int64(30*24*time.Hour/time.Millisecond)
	if summary.ExpiringIn7d, err = s.countQuery(ctx, "SELECT COUNT(*) FROM user_entitlement WHERE tier IN ('plus','pro') AND tier_expire_at IS NOT NULL AND tier_expire_at > ? AND tier_expire_at <= ?", []any{nowMs, day7Ms}); err != nil {
		return summary, err
	}
	if summary.ExpiringIn30d, err = s.countQuery(ctx, "SELECT COUNT(*) FROM user_entitlement WHERE tier IN ('plus','pro') AND tier_expire_at IS NOT NULL AND tier_expire_at > ? AND tier_expire_at <= ?", []any{nowMs, day30Ms}); err != nil {
		return summary, err
	}
	if summary.DailyLimitExhaustedUsers, err = s.countQuery(ctx, `SELECT COUNT(*)
		FROM daily_usage du
		LEFT JOIN user_entitlement e ON e.user_id = du.user_id
		WHERE du.day_cn = ?
		  AND du.used >= CASE
		    WHEN e.tier = 'pro' AND e.tier_expire_at IS NOT NULL AND e.tier_expire_at > ? THEN 40
		    WHEN e.tier = 'plus' AND e.tier_expire_at IS NOT NULL AND e.tier_expire_at > ? THEN 25
		    ELSE 6
		  END`, []any{dayCN, nowMs, nowMs}); err != nil {
		return summary, err
	}
	if summary.TopupActiveUsers, err = s.countQuery(ctx, "SELECT COUNT(DISTINCT user_id) FROM topup_packs WHERE remaining > 0 AND (expire_at IS NULL OR expire_at > ?)", []any{nowMs}); err != nil {
		return summary, err
	}
	if summary.UpgradeCreditUsers, err = s.countQuery(ctx, "SELECT COUNT(DISTINCT user_id) FROM upgrade_credits WHERE remaining > 0 AND (expire_at IS NULL OR expire_at > ?)", []any{nowMs}); err != nil {
		return summary, err
	}
	return summary, nil
}

func (s *Store) ListAdminQuotaLedger(ctx context.Context, userID string, limit int) ([]AdminQuotaLedgerEntry, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT id, client_msg_id, day_cn, source, delta, created_at
		   FROM quota_ledger
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
	entries := []AdminQuotaLedgerEntry{}
	for rows.Next() {
		var entry AdminQuotaLedgerEntry
		if err := rows.Scan(&entry.ID, &entry.ClientMsgID, &entry.DayCN, &entry.Source, &entry.Delta, &entry.CreatedAt); err != nil {
			return nil, err
		}
		entries = append(entries, entry)
	}
	return entries, rows.Err()
}

func (s *Store) ListAdminTopupPacks(ctx context.Context, userID string, limit int) ([]AdminTopupPackEntry, error) {
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT pack_id, user_id, remaining, expire_at, status, created_at
		   FROM topup_packs
		  WHERE user_id = ?
		  ORDER BY created_at DESC, pack_id DESC
		  LIMIT ?`,
		userID,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	packs := []AdminTopupPackEntry{}
	for rows.Next() {
		var pack AdminTopupPackEntry
		var expire sql.NullInt64
		if err := rows.Scan(&pack.PackID, &pack.UserID, &pack.Remaining, &expire, &pack.Status, &pack.CreatedAt); err != nil {
			return nil, err
		}
		pack.Initial = topupPackRemaining
		pack.Used = maxInt(0, pack.Initial-pack.Remaining)
		if strings.HasPrefix(pack.PackID, "pack_") {
			pack.OrderID = strings.TrimPrefix(pack.PackID, "pack_")
		}
		if expire.Valid {
			pack.ExpireAt = int64Ptr(expire.Int64)
		}
		packs = append(packs, pack)
	}
	return packs, rows.Err()
}

func (s *Store) ListAdminUpgradeCredits(ctx context.Context, userID string) ([]AdminUpgradeCredit, error) {
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT user_id, remaining, expire_at, updated_at
		   FROM upgrade_credits
		  WHERE user_id = ?
		  ORDER BY updated_at DESC`,
		userID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	credits := []AdminUpgradeCredit{}
	for rows.Next() {
		var credit AdminUpgradeCredit
		var expire sql.NullInt64
		if err := rows.Scan(&credit.UserID, &credit.Remaining, &expire, &credit.UpdatedAt); err != nil {
			return nil, err
		}
		if expire.Valid {
			credit.ExpireAt = int64Ptr(expire.Int64)
		}
		credits = append(credits, credit)
	}
	return credits, rows.Err()
}

func (s *Store) ListAdminRoundExcerpts(ctx context.Context, userID string, limit int) ([]AdminRoundExcerpt, error) {
	if limit <= 0 || limit > 50 {
		limit = 12
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT client_msg_id, user_text, user_images_json, assistant_text, created_at, region, region_source, region_reliability
		   FROM session_round_archive
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
	entries := []AdminRoundExcerpt{}
	for rows.Next() {
		var clientMsgID, userText, assistantText string
		var imagesRaw sql.NullString
		var createdAt int64
		var region, regionSource, regionReliability sql.NullString
		if err := rows.Scan(&clientMsgID, &userText, &imagesRaw, &assistantText, &createdAt, &region, &regionSource, &regionReliability); err != nil {
			return nil, err
		}
		imageCount := 0
		if imagesRaw.Valid && strings.TrimSpace(imagesRaw.String) != "" {
			var images []string
			if err := json.Unmarshal([]byte(imagesRaw.String), &images); err == nil {
				imageCount = len(images)
			}
		}
		entries = append(entries, AdminRoundExcerpt{
			ClientMsgID:       clientMsgID,
			UserExcerpt:       truncateRunes(strings.TrimSpace(userText), adminExcerptRunes),
			AssistantExcerpt:  truncateRunes(strings.TrimSpace(assistantText), adminExcerptRunes),
			HasImages:         imageCount > 0,
			ImageCount:        imageCount,
			Region:            nullStringValue(region),
			RegionSource:      RegionSource(nullStringValue(regionSource)),
			RegionReliability: RegionReliability(nullStringValue(regionReliability)),
			CreatedAt:         createdAt,
		})
	}
	return entries, rows.Err()
}

func (s *Store) ListAdminOrders(ctx context.Context, userID string, limit int) ([]AdminOrderEntry, error) {
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT order_id, user_id, type, CAST(amount AS CHAR), created_at, status, result_json
		   FROM orders
		  WHERE user_id = ?
		  ORDER BY created_at DESC
		  LIMIT ?`,
		userID,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	entries := []AdminOrderEntry{}
	for rows.Next() {
		var entry AdminOrderEntry
		var result sql.NullString
		if err := rows.Scan(&entry.OrderID, &entry.UserID, &entry.Type, &entry.Amount, &entry.CreatedAt, &entry.Status, &result); err != nil {
			return nil, err
		}
		if result.Valid && strings.TrimSpace(result.String) != "" {
			entry.Result = json.RawMessage(result.String)
		}
		entries = append(entries, entry)
	}
	return entries, rows.Err()
}

func (s *Store) ListAdminDailyAgriCards(ctx context.Context, scope string, limit int) ([]AdminDailyAgriEntry, error) {
	if limit <= 0 || limit > 100 {
		limit = 30
	}
	scope = normalizeDailyAgriScope(scope)
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT day_cn, scope, status, content_json, sources_json, model, search_strategy, prompt_version, lease_until, generated_at, error, created_at, updated_at
		   FROM daily_agri_cards
		  WHERE scope = ?
		  ORDER BY day_cn DESC
		  LIMIT ?`,
		scope,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	entries := []AdminDailyAgriEntry{}
	for rows.Next() {
		var entry AdminDailyAgriEntry
		var content, sources, model, searchStrategy, promptVersion, errorText sql.NullString
		var generated sql.NullInt64
		if err := rows.Scan(&entry.DayCN, &entry.Scope, &entry.Status, &content, &sources, &model, &searchStrategy, &promptVersion, &entry.LeaseUntil, &generated, &errorText, &entry.CreatedAt, &entry.UpdatedAt); err != nil {
			return nil, err
		}
		entry.Model = nullStringValue(model)
		entry.SearchStrategy = nullStringValue(searchStrategy)
		entry.PromptVersion = nullStringValue(promptVersion)
		entry.Error = nullStringValue(errorText)
		if generated.Valid {
			entry.GeneratedAt = int64Ptr(generated.Int64)
		}
		if content.Valid && strings.TrimSpace(content.String) != "" {
			entry.Content = json.RawMessage(content.String)
			var card DailyAgriCard
			if err := json.Unmarshal([]byte(content.String), &card); err == nil {
				entry.Title = card.Title
				entry.ItemCount = len(card.Items)
			}
		}
		if sources.Valid && strings.TrimSpace(sources.String) != "" {
			entry.Sources = json.RawMessage(sources.String)
			var sourceList []DailyAgriSearchSource
			if err := json.Unmarshal([]byte(sources.String), &sourceList); err == nil {
				entry.SourceCount = len(sourceList)
			}
		}
		entries = append(entries, entry)
	}
	return entries, rows.Err()
}

func (s *Store) countQuery(ctx context.Context, query string, args []any) (int64, error) {
	var value sql.NullInt64
	if err := s.db.QueryRowContext(ctx, query, args...).Scan(&value); err != nil {
		return 0, err
	}
	return value.Int64, nil
}

func scanAdminUserListEntry(rows *sql.Rows, dayCN string, includePhoneNumber bool) (AdminUserListEntry, error) {
	var user AdminUserListEntry
	var lastLogin, tierExpire, lastSeen sql.NullInt64
	var tierRaw, phoneCiphertext sql.NullString
	var region, regionSource, regionReliability sql.NullString
	var latestSupportSender sql.NullString
	var activeSessions, errorCount, supportMessageCount sql.NullInt64
	var roundTotal sql.NullInt64
	if err := rows.Scan(
		&user.UserID,
		&user.PhoneMask,
		&phoneCiphertext,
		&user.CreatedAt,
		&user.UpdatedAt,
		&lastLogin,
		&tierRaw,
		&tierExpire,
		&roundTotal,
		&lastSeen,
		&region,
		&regionSource,
		&regionReliability,
		&activeSessions,
		&errorCount,
		&supportMessageCount,
		&latestSupportSender,
	); err != nil {
		return user, err
	}
	tier, expireAt, _ := effectiveTierFromRow(tierRaw, tierExpire, TierFree, time.Now().UnixMilli())
	user.Tier = tier
	user.TierExpireAt = expireAt
	user.RoundTotal = int(roundTotal.Int64)
	user.ActiveSessions = activeSessions.Int64
	user.ErrorCount24h = errorCount.Int64
	user.SupportMessageCount = supportMessageCount.Int64
	user.SupportNeedsReply = latestSupportSender.Valid && latestSupportSender.String == "user"
	if includePhoneNumber && phoneCiphertext.Valid {
		if phone, err := decryptAccountPhoneNumber(phoneCiphertext.String); err == nil {
			user.PhoneNumber = phone
		}
	}
	if lastLogin.Valid {
		user.LastLoginAt = int64Ptr(lastLogin.Int64)
	}
	if lastSeen.Valid {
		user.LastSeenAt = int64Ptr(lastSeen.Int64)
	}
	user.LastRegion = nullStringValue(region)
	user.LastRegionSource = RegionSource(nullStringValue(regionSource))
	user.LastRegionReliability = RegionReliability(nullStringValue(regionReliability))
	user.Daily = DailyQuotaStatus{DayCN: dayCN, Tier: tier, Limit: tierLimits[tier], Remaining: tierLimits[tier]}
	return user, nil
}

func adminSupportMessageFromSupport(message SupportMessage, includeBody bool) AdminSupportMessage {
	result := AdminSupportMessage{
		ID:           message.ID,
		UserID:       message.UserID,
		SenderType:   message.SenderType,
		BodyExcerpt:  truncateRunes(strings.TrimSpace(message.Body), adminExcerptRunes),
		HasImages:    len(message.ImageURLs) > 0,
		ImageCount:   len(message.ImageURLs),
		CreatedAt:    message.CreatedAt,
		ReadByUserAt: message.ReadByUserAt,
	}
	if includeBody {
		result.Body = message.Body
		result.ImageURLs = message.ImageURLs
	}
	return result
}

func parseAdminLimit(raw string) int {
	limit, err := strconv.Atoi(strings.TrimSpace(raw))
	if err != nil || limit <= 0 {
		return defaultAdminListLimit
	}
	if limit > maxAdminListLimit {
		return maxAdminListLimit
	}
	return limit
}

func adminDayStartMs(loc *time.Location, now time.Time) int64 {
	if loc == nil {
		loc = time.FixedZone("Asia/Shanghai", 8*60*60)
	}
	local := now.In(loc)
	start := time.Date(local.Year(), local.Month(), local.Day(), 0, 0, 0, 0, loc)
	return start.UnixMilli()
}

func (s *Server) adminHealthStatus() AdminHealthStatus {
	return AdminHealthStatus{
		API:               "ok",
		Bailian:           ternary(s.bailian.HasKeyConfigured(), "ok", "missing_key"),
		Dypns:             ternary(s.dypns.HasClientConfigured(), "ok", "missing_key"),
		DypnsFusion:       ternary(s.dypns.HasFusionConfigured(), "ok", "missing_config"),
		DypnsSMS:          ternary(s.dypns.HasSMSConfigured(), "ok", "missing_config"),
		Redis:             ternary(s.redisClient != nil, "ok", "missing_config"),
		UploadStorage:     uploadStoreHealthStatus(s.uploadStore),
		AuthStrict:        IsAuthStrict(),
		DevOrderEndpoints: devOrderEndpointsEnabled(),
	}
}

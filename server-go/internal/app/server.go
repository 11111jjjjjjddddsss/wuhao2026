package app

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/redis/go-redis/v9"
)

const (
	sseHeartbeatInterval = 20 * time.Second
	defaultJSONBodyLimit = 64 * 1024
)

var (
	errJSONBodyTooLarge     = errors.New("json body too large")
	errJSONBodyTrailingData = errors.New("json body has trailing data")
)

type Server struct {
	logger                *slog.Logger
	mux                   *http.ServeMux
	store                 *Store
	prompts               *PromptLoader
	bailian               *BailianClient
	gptRelay              *GPTRelayClient
	summary               *SummaryService
	dailyAgri             *DailyAgriCardService
	dypns                 *DypnsClient
	sms                   *SMSClient
	alipay                *AlipayClient
	shanghai              *time.Location
	assetDir              string
	uploadsDir            string
	uploadStore           UploadStore
	systemAnchor          string
	redisClient           *redis.Client
	rateLimiter           rateLimiter
	fusionTokenLimiter    rateLimiter
	fusionLoginLimiter    rateLimiter
	smsLimiter            rateLimiter
	smsIPLimiter          rateLimiter
	smsLoginLimiter       rateLimiter
	clientAppLogLimiter   rateLimiter
	supportMessageLimiter rateLimiter
	uploadLimiter         rateLimiter
	internalSecretLimiter rateLimiter
	internalProbeLimiter  rateLimiter
	todayAgriItemLimiter  rateLimiter
	adminLoginLimiter     rateLimiter
	giftCardRedeemLimiter rateLimiter
	backgroundStop        chan struct{}
	backgroundStopOnce    sync.Once
}

type orderRequest struct {
	OrderID string `json:"order_id"`
}

func NewServer(logger *slog.Logger) (*Server, error) {
	if logger == nil {
		logger = slog.New(slog.NewJSONHandler(os.Stdout, nil))
	}

	shanghai, err := time.LoadLocation("Asia/Shanghai")
	if err != nil {
		shanghai = time.FixedZone("Asia/Shanghai", 8*60*60)
	}

	assetDir, err := resolveExistingDir(
		"ASSET_DIR",
		filepath.Join("server-go", "assets"),
		filepath.Join(".", "assets"),
	)
	if err != nil {
		return nil, err
	}

	migrationsDir, err := resolveExistingDir(
		"MIGRATIONS_DIR",
		filepath.Join("server-go", "migrations"),
		filepath.Join(".", "migrations"),
	)
	if err != nil {
		return nil, err
	}

	uploadsDir, err := resolveOrCreateDir(
		"UPLOADS_DIR",
		filepath.Join("server-go", "uploads"),
		filepath.Join(".", "uploads"),
	)
	if err != nil {
		return nil, err
	}

	db, err := OpenDB()
	if err != nil {
		return nil, err
	}
	migrationCtx, cancelMigration := context.WithTimeout(contextBackground(), envDurationWithDefault("MYSQL_MIGRATION_TIMEOUT_SECONDS", defaultMySQLMigrationTimeout))
	defer cancelMigration()
	if err := InitMySQL(migrationCtx, db, migrationsDir); err != nil {
		return nil, err
	}

	prompts := NewPromptLoader(assetDir)
	systemAnchor, err := prompts.SystemAnchor()
	if err != nil {
		return nil, err
	}
	logger.Info("system anchor loaded", "anchor_source", "file", "anchor_path", prompts.SystemAnchorPath(), "anchor_chars", len(systemAnchor))

	result := prompts.ProbeSummaryPrompt()
	if result.OK {
		logger.Info("summary prompt precheck ok", "prompt_path", result.Path, "prompt_chars", result.Chars)
	} else {
		logger.Warn("summary prompt precheck failed", "prompt_path", result.Path, "error", result.Error)
	}

	store := NewStore(db, shanghai)
	if err := store.EnsureBootstrapAdminFromEnv(contextBackground(), logger); err != nil {
		return nil, err
	}
	bailian := NewBailianClient()
	gptRelay := NewGPTRelayClientFromEnv()
	dypns, err := NewDypnsClientFromEnv()
	if err != nil {
		return nil, err
	}
	sms, err := NewSMSClientFromEnv()
	if err != nil {
		return nil, err
	}
	alipay, err := NewAlipayClientFromEnv(shanghai)
	if err != nil {
		return nil, err
	}
	uploadStore, err := NewUploadStoreFromEnv(uploadsDir)
	if err != nil {
		return nil, err
	}
	redisClient, err := newOptionalRedisClient(contextBackground(), logger)
	if err != nil {
		return nil, err
	}
	server := &Server{
		logger:                logger,
		mux:                   http.NewServeMux(),
		store:                 store,
		prompts:               prompts,
		bailian:               bailian,
		gptRelay:              gptRelay,
		summary:               NewSummaryService(store, prompts, bailian, logger, redisClient),
		dailyAgri:             NewDailyAgriCardService(store, bailian, logger, shanghai),
		dypns:                 dypns,
		sms:                   sms,
		alipay:                alipay,
		shanghai:              shanghai,
		assetDir:              assetDir,
		uploadsDir:            uploadsDir,
		uploadStore:           uploadStore,
		systemAnchor:          systemAnchor,
		redisClient:           redisClient,
		rateLimiter:           newChatRateLimiter(redisClient),
		fusionTokenLimiter:    newAuthFusionTokenRateLimiter(redisClient),
		fusionLoginLimiter:    newAuthFusionLoginRateLimiter(redisClient),
		smsLimiter:            newAuthSMSRateLimiter(redisClient),
		smsIPLimiter:          newAuthSMSIPRateLimiter(redisClient),
		smsLoginLimiter:       newAuthSMSLoginRateLimiter(redisClient),
		clientAppLogLimiter:   newClientAppLogRateLimiter(redisClient),
		supportMessageLimiter: newSupportMessageRateLimiter(redisClient),
		uploadLimiter:         newUploadRateLimiter(redisClient),
		internalSecretLimiter: newInternalSecretRateLimiter(redisClient),
		internalProbeLimiter:  newInternalProbeRateLimiter(redisClient),
		todayAgriItemLimiter:  newTodayAgriItemSaveRateLimiter(redisClient),
		adminLoginLimiter:     newAdminLoginRateLimiter(redisClient),
		giftCardRedeemLimiter: newGiftCardRedeemRateLimiter(redisClient),
		backgroundStop:        make(chan struct{}),
	}
	server.registerRoutes()
	server.startQuotaConsumeRepairWorker()
	server.startDataMaintenanceWorker()
	server.startMemorySummaryDrainWorker()
	return server, nil
}

func (s *Server) Handler() http.Handler {
	return s.withAccessLog(s.mux)
}

func (s *Server) Close() error {
	if s == nil {
		return nil
	}
	if s.backgroundStop != nil {
		s.backgroundStopOnce.Do(func() {
			close(s.backgroundStop)
		})
	}
	if s.redisClient == nil {
		return nil
	}
	return s.redisClient.Close()
}

func (s *Server) registerRoutes() {
	s.mux.HandleFunc("GET /healthz", s.handleHealthz)
	s.mux.HandleFunc("POST /admin-api/v1/auth/login", s.handleAdminLogin)
	s.mux.HandleFunc("GET /admin-api/v1/auth/me", s.handleAdminMe)
	s.mux.HandleFunc("POST /admin-api/v1/auth/change-password", s.handleAdminChangePassword)
	s.mux.HandleFunc("POST /admin-api/v1/auth/logout", s.handleAdminLogout)
	s.mux.HandleFunc("GET /admin-api/v1/overview", s.handleAdminOverview)
	s.mux.HandleFunc("GET /admin-api/v1/monitoring", s.handleAdminMonitoring)
	s.mux.HandleFunc("GET /admin-api/v1/insights", s.handleAdminInsights)
	s.mux.HandleFunc("GET /admin-api/v1/entitlements/summary", s.handleAdminEntitlementSummary)
	s.mux.HandleFunc("GET /admin-api/v1/quota-consume-outbox", s.handleAdminQuotaConsumeOutbox)
	s.mux.HandleFunc("POST /admin-api/v1/quota-consume-outbox/action", s.handleAdminQuotaConsumeOutboxAction)
	s.mux.HandleFunc("GET /admin-api/v1/orders", s.handleAdminOrders)
	s.mux.HandleFunc("POST /admin-api/v1/orders/grant", s.handleAdminGrantPaymentOrder)
	s.mux.HandleFunc("POST /admin-api/v1/orders/query", s.handleAdminQueryPaymentOrder)
	s.mux.HandleFunc("POST /admin-api/v1/orders/refund", s.handleAdminRefundPaymentOrder)
	s.mux.HandleFunc("POST /admin-api/v1/orders/close-expired", s.handleAdminCloseExpiredPaymentOrders)
	s.mux.HandleFunc("GET /admin-api/v1/orders/reconciliation", s.handleAdminPaymentReconciliation)
	s.mux.HandleFunc("GET /admin-api/v1/users", s.handleAdminUsers)
	s.mux.HandleFunc("GET /admin-api/v1/users/detail", s.handleAdminUserDetail)
	s.mux.HandleFunc("GET /admin-api/v1/support/conversations", s.handleAdminSupportConversations)
	s.mux.HandleFunc("GET /admin-api/v1/support/messages", s.handleAdminSupportMessages)
	s.mux.HandleFunc("POST /admin-api/v1/support/messages", s.handleAdminCreateSupportMessage)
	s.mux.HandleFunc("POST /admin-api/v1/support/conversations/status", s.handleAdminUpdateSupportConversationStatus)
	s.mux.HandleFunc("GET /admin-api/v1/app-logs", s.handleAdminAppLogs)
	s.mux.HandleFunc("GET /admin-api/v1/audit-logs", s.handleAdminAuditLogs)
	s.mux.HandleFunc("GET /admin-api/v1/today-agri/cards", s.handleAdminTodayAgriCards)
	s.mux.HandleFunc("POST /admin-api/v1/today-agri/generate", s.handleAdminGenerateTodayAgriCard)
	s.mux.HandleFunc("POST /admin-api/v1/today-agri/manual", s.handleAdminPublishManualTodayAgriCard)
	s.mux.HandleFunc("GET /admin-api/v1/app-update/android", s.handleAdminAppUpdateAndroid)
	s.mux.HandleFunc("GET /admin-api/v1/app-update/android/events", s.handleAdminAppUpdateAndroidEvents)
	s.mux.HandleFunc("POST /admin-api/v1/app-update/android", s.handleAdminAppUpdateAndroidWrite)
	s.mux.HandleFunc("GET /admin-api/v1/gift-cards/batches", s.handleAdminGiftCardBatches)
	s.mux.HandleFunc("POST /admin-api/v1/gift-cards/batches", s.handleAdminCreateGiftCardBatch)
	s.mux.HandleFunc("GET /admin-api/v1/gift-cards/summary", s.handleAdminGiftCardSummary)
	s.mux.HandleFunc("GET /admin-api/v1/gift-cards/cards", s.handleAdminGiftCards)
	s.mux.HandleFunc("POST /admin-api/v1/gift-cards/void", s.handleAdminVoidGiftCard)
	s.mux.HandleFunc("GET /admin-api/v1/gift-cards/attempts", s.handleAdminGiftCardAttempts)
	s.mux.HandleFunc("GET /admin-api/v1/account-deletion-requests", s.handleAdminAccountDeletionRequests)
	s.mux.HandleFunc("POST /admin-api/v1/account-deletion-requests/status", s.handleAdminUpdateAccountDeletionStatus)
	s.mux.HandleFunc("POST /api/auth/fusion/token", s.handleAuthFusionToken)
	s.mux.HandleFunc("POST /api/auth/fusion/verify", s.handleAuthFusionVerify)
	s.mux.HandleFunc("POST /api/auth/fusion/login", s.handleAuthFusionLogin)
	s.mux.HandleFunc("POST /api/auth/sms/send", s.handleAuthSMSSend)
	s.mux.HandleFunc("POST /api/auth/sms/login", s.handleAuthSMSLogin)
	s.mux.HandleFunc("POST /api/auth/logout", s.handleAuthLogout)
	s.mux.HandleFunc("GET /api/auth/session", s.handleAuthSession)
	s.mux.HandleFunc("POST /api/account/deletion-requests", s.handleCreateAccountDeletionRequest)
	s.mux.HandleFunc("GET /api/me", s.handleGetMe)
	s.mux.HandleFunc("POST /api/session/b", s.handleSessionB)
	s.mux.HandleFunc("POST /api/session/c", s.handleSessionC)
	s.mux.HandleFunc("GET /api/session/snapshot", s.handleSessionSnapshot)
	s.mux.HandleFunc("POST /api/session/clear", s.handleSessionClear)
	s.mux.HandleFunc("POST /api/session/round_complete", s.handleSessionRoundComplete)
	s.mux.HandleFunc("POST /api/topup/buy", s.handleTopupBuy)
	s.mux.HandleFunc("POST /api/tier/renew_plus", s.handleRenewPlus)
	s.mux.HandleFunc("POST /api/tier/renew_pro", s.handleRenewPro)
	s.mux.HandleFunc("POST /api/tier/upgrade_plus_to_pro", s.handleUpgradePlusToPro)
	s.mux.HandleFunc("POST /api/payments/alipay/orders", s.handleCreateAlipayPaymentOrder)
	s.mux.HandleFunc("GET /api/payments/orders", s.handleGetPaymentOrder)
	s.mux.HandleFunc("POST /api/payments/alipay/notify", s.handleAlipayPaymentNotify)
	s.mux.HandleFunc("POST /api/today-agri-item", s.handleSaveTodayAgriItem)
	s.mux.HandleFunc("GET /api/today-agri-card", s.handleTodayAgriCard)
	s.mux.HandleFunc("GET /api/today-agri-cards", s.handleTodayAgriCards)
	s.mux.HandleFunc("GET /api/app/update", s.handleAppUpdate)
	s.mux.HandleFunc("POST /api/gift-cards/redeem", s.handleGiftCardRedeem)
	s.mux.HandleFunc("POST /api/app/logs/preauth", s.handleCreatePreAuthClientAppLog)
	s.mux.HandleFunc("POST /api/app/logs", s.handleCreateClientAppLog)
	s.mux.HandleFunc("GET /api/support/summary", s.handleSupportSummary)
	s.mux.HandleFunc("GET /api/support/messages", s.handleSupportMessages)
	s.mux.HandleFunc("POST /api/support/messages", s.handleCreateSupportMessage)
	s.mux.HandleFunc("POST /api/support/read", s.handleMarkSupportRead)
	s.mux.HandleFunc("POST /internal/jobs/today-agri-card/generate", s.handleGenerateTodayAgriCard)
	s.mux.HandleFunc("GET /internal/jobs/today-agri-card/status", s.handleInternalTodayAgriCardStatus)
	s.mux.HandleFunc("POST /internal/jobs/today-agri-card/probe", s.handleProbeTodayAgriCard)
	s.mux.HandleFunc("POST /internal/jobs/today-agri-card/manual", s.handleInternalPublishManualTodayAgriCard)
	s.mux.HandleFunc("POST /internal/jobs/memory-document/probe", s.handleProbeMemoryDocument)
	s.mux.HandleFunc("GET /internal/app/logs", s.handleInternalClientAppLogs)
	s.mux.HandleFunc("GET /internal/admin/audit-logs", s.handleInternalAdminAuditLogs)
	s.mux.HandleFunc("GET /internal/support/conversations", s.handleInternalSupportConversations)
	s.mux.HandleFunc("GET /internal/support/messages", s.handleInternalSupportMessages)
	s.mux.HandleFunc("POST /internal/support/messages", s.handleInternalCreateSupportMessage)
	s.mux.HandleFunc("POST /api/chat/stream", s.handleChatStream)
	s.mux.HandleFunc("POST /upload", s.handleUpload)
	s.mux.HandleFunc("/uploads/", s.handleUploadsStatic)
}

func (s *Server) handleHealthz(w http.ResponseWriter, r *http.Request) {
	redisStatus := redisHealthStatus(r.Context(), s.redisClient)
	smsStatus := ternary(s.sms.HasConfigured() && redisStatus == "ok", "ok", "missing_config")
	payload := map[string]any{
		"ok":                  true,
		"bailian":             ternary(s.bailian.HasKeyConfigured(), "ok", "missing_key"),
		"gpt_relay":           gptRelayHealthStatus(s.gptRelay),
		"dypns":               ternary(s.dypns.HasClientConfigured(), "ok", "missing_key"),
		"dypns_fusion":        ternary(s.dypns.HasFusionConfigured(), "ok", "missing_config"),
		"dypns_sms":           smsStatus,
		"sms":                 smsStatus,
		"alipay":              s.alipay.HealthStatus(),
		"alipay_payment_gate": alipayPaymentOrderGateStatus(),
		"redis":               redisStatus,
		"upload_storage":      uploadStoreHealthStatus(s.uploadStore),
		"auth_strict":         IsAuthStrict(),
		"dev_order_endpoints": devOrderEndpointsEnabled(),
		"revision":            deploymentRevision(),
	}
	s.writeJSON(w, http.StatusOK, payload)
}

func deploymentRevision() string {
	if revision := sanitizeDeploymentRevision(os.Getenv("DEPLOY_COMMIT")); revision != "" {
		return revision
	}
	paths := []string{"REVISION"}
	if exe, err := os.Executable(); err == nil && strings.TrimSpace(exe) != "" {
		paths = append(paths, filepath.Join(filepath.Dir(exe), "REVISION"))
	}
	for _, path := range paths {
		raw, err := os.ReadFile(path)
		if err != nil {
			continue
		}
		if revision := sanitizeDeploymentRevision(string(raw)); revision != "" {
			return revision
		}
	}
	return ""
}

func sanitizeDeploymentRevision(raw string) string {
	revision := strings.TrimSpace(raw)
	if revision == "" || len(revision) > 64 {
		return ""
	}
	for _, r := range revision {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '.' || r == '_' || r == '-' {
			continue
		}
		return ""
	}
	return revision
}

func (s *Server) handleGetMe(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}

	ctx := r.Context()
	if err := s.store.EnsureUser(ctx, auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	tier, tierExpireAt, err := s.store.GetTierForUser(ctx, auth.UserID, TierFree)
	if err != nil {
		s.logger.Error("get tier failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	status, err := s.store.GetDailyStatus(ctx, auth.UserID, tier, GetTodayKeyCN(s.shanghai, time.Now()))
	if err != nil {
		s.logger.Error("get daily status failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	topupRemaining, topupExpireAt, err := s.store.GetTopupStatus(ctx, auth.UserID)
	if err != nil {
		s.logger.Error("get topup status failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	membershipSource := ""
	var giftCardRedeemedAt *int64
	if tier == TierPlus || tier == TierPro {
		if viaGiftCard, redeemedAt, err := s.store.GetCurrentGiftCardMembership(ctx, auth.UserID, tier, tierExpireAt); err != nil {
			s.logger.Warn("get membership source failed", "userId", auth.UserID, "error", err)
		} else if viaGiftCard {
			membershipSource = "gift_card"
			giftCardRedeemedAt = redeemedAt
		}
	}

	s.writeJSON(w, http.StatusOK, map[string]any{
		"tier":                     tier,
		"tier_expire_at":           tierExpireAt,
		"daily_remaining":          status.Remaining,
		"topup_remaining":          topupRemaining,
		"topup_earliest_expire_at": topupExpireAt,
		"upgrade_remaining":        0,
		"membership_source":        membershipSource,
		"gift_card_redeemed_at":    giftCardRedeemedAt,
	})
}

func (s *Server) handleSessionB(w http.ResponseWriter, r *http.Request) {
	_, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	s.writeJSON(w, http.StatusGone, map[string]any{"error": "DEPRECATED_ENDPOINT"})
}

func (s *Server) handleSessionC(w http.ResponseWriter, r *http.Request) {
	_, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	s.writeJSON(w, http.StatusGone, map[string]any{"error": "DEPRECATED_ENDPOINT"})
}

func (s *Server) handleSessionSnapshot(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}

	snapshot, archivedRounds, todayAgriItems, snapshotWarnings, err := s.store.GetSessionSnapshotForUI(r.Context(), auth.UserID, GetTodayKeyCN(s.shanghai, time.Now()), 1)
	if err != nil {
		s.logger.Error("get session snapshot failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if snapshotWarnings.ArchiveErr != nil {
		s.logger.Warn("get session snapshot archive failed; returning base snapshot", "userId", auth.UserID, "error", snapshotWarnings.ArchiveErr)
	}
	if snapshotWarnings.TodayAgriErr != nil {
		s.logger.Warn("get session snapshot today agri item failed; returning snapshot without today agri item", "userId", auth.UserID, "error", snapshotWarnings.TodayAgriErr)
	}

	safe := safeSnapshot(auth.UserID, snapshot)
	uiRounds := safe.ARoundsFull
	if len(archivedRounds) > 0 {
		uiRounds = mergeSessionRoundsForUI(safe.ARoundsFull, archivedRounds)
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"user_id":                      safe.UserID,
		"a_json":                       safe.ARoundsFull,
		"a_rounds_full":                safe.ARoundsFull,
		"a_rounds_for_ui":              uiRounds,
		"archive_unavailable":          snapshotWarnings.ArchiveErr != nil,
		"today_agri_items":             todayAgriItems,
		"today_agri_items_unavailable": snapshotWarnings.TodayAgriErr != nil,
		"memory_document":              safe.MemoryDocument,
		"b_summary":                    safe.MemoryDocument,
		"round_total":                  safe.RoundTotal,
		"updated_at":                   safe.UpdatedAt,
		"session_generation":           safe.SessionGeneration,
	})
}

func (s *Server) handleSessionClear(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}

	var hasActiveStream bool
	var sessionGeneration int
	err := s.store.WithUserChatStreamGate(r.Context(), auth.UserID, func(ctx context.Context) error {
		var checkErr error
		hasActiveStream, checkErr = s.store.HasAnyActiveChatStreamInflight(ctx, auth.UserID, time.Now())
		if checkErr != nil || hasActiveStream {
			return checkErr
		}
		sessionGeneration, checkErr = s.store.ClearSessionHistory(ctx, auth.UserID)
		return checkErr
	})
	if err != nil {
		if err == ErrChatStreamGateBusy {
			s.writeJSON(w, http.StatusConflict, map[string]any{"error": "ACTIVE_CHAT_STREAM"})
			return
		}
		s.logger.Error("clear session history failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if hasActiveStream {
		s.writeJSON(w, http.StatusConflict, map[string]any{"error": "ACTIVE_CHAT_STREAM"})
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":                 true,
		"session_generation": sessionGeneration,
	})
}

func (s *Server) handleSessionRoundComplete(w http.ResponseWriter, r *http.Request) {
	_, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	s.writeJSON(w, http.StatusGone, map[string]any{"error": "DEPRECATED_ENDPOINT"})
}

func (s *Server) handleTopupBuy(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if !s.allowDevOrderEndpoint(w) {
		return
	}
	var body orderRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	orderID := strings.TrimSpace(body.OrderID)
	if orderID == "" {
		s.writeError(w, http.StatusBadRequest, "order_id required")
		return
	}

	ctx := r.Context()
	if err := s.store.EnsureUser(ctx, auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	replay, packID, expireAt, remaining, err := s.store.BuyTopupPack(ctx, auth.UserID, orderID)
	if err != nil {
		switch err.Error() {
		case "FORBIDDEN_TIER":
			s.writeError(w, http.StatusForbidden, err.Error())
		case "TOPUP_LIMIT_REACHED":
			s.writeError(w, http.StatusConflict, err.Error())
		default:
			if errors.Is(err, ErrOrderIDConflict) {
				s.writeError(w, http.StatusConflict, "ORDER_ID_CONFLICT")
				return
			}
			s.logger.Error("buy topup failed", "userId", auth.UserID, "orderId", orderID, "error", err)
			s.writeError(w, http.StatusInternalServerError, "internal_error")
		}
		return
	}

	s.logger.Info("topup buy", "userId", auth.UserID, "orderId", orderID, "replay", replay, "packId", packID)
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":        true,
		"replay":    replay,
		"pack_id":   packID,
		"expire_at": expireAt,
		"remaining": remaining,
	})
}

func mergeSessionRoundsForUI(fallbackRounds []SessionRound, archivedRounds []SessionRound) []SessionRound {
	if len(archivedRounds) == 0 {
		return fallbackRounds
	}

	archivedKeys := make(map[string]struct{}, len(archivedRounds))
	for _, round := range archivedRounds {
		key := strings.TrimSpace(round.ClientMsgID)
		if key != "" {
			archivedKeys[key] = struct{}{}
		}
	}

	merged := make([]SessionRound, 0, len(fallbackRounds)+len(archivedRounds))
	for _, round := range fallbackRounds {
		key := strings.TrimSpace(round.ClientMsgID)
		if key == "" {
			merged = append(merged, round)
			continue
		}
		if _, archived := archivedKeys[key]; !archived {
			merged = append(merged, round)
		}
	}
	merged = append(merged, archivedRounds...)

	if len(merged) > sessionRoundArchiveUILimit {
		return merged[len(merged)-sessionRoundArchiveUILimit:]
	}
	return merged
}

func (s *Server) handleRenewPlus(w http.ResponseWriter, r *http.Request) {
	s.handleRenewTier(w, r, TierPlus)
}

func (s *Server) handleRenewPro(w http.ResponseWriter, r *http.Request) {
	s.handleRenewTier(w, r, TierPro)
}

func (s *Server) handleRenewTier(w http.ResponseWriter, r *http.Request, targetTier Tier) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if !s.allowDevOrderEndpoint(w) {
		return
	}
	var body orderRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	orderID := strings.TrimSpace(body.OrderID)
	if orderID == "" {
		s.writeError(w, http.StatusBadRequest, "order_id required")
		return
	}

	ctx := r.Context()
	if err := s.store.EnsureUser(ctx, auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	var (
		replay       bool
		tier         Tier
		tierExpireAt int64
		err          error
	)
	if targetTier == TierPlus {
		replay, tier, tierExpireAt, err = s.store.RenewPlus(ctx, auth.UserID, orderID)
	} else {
		replay, tier, tierExpireAt, err = s.store.RenewPro(ctx, auth.UserID, orderID)
	}
	if err != nil {
		switch err.Error() {
		case "FORBIDDEN_TIER":
			s.writeError(w, http.StatusForbidden, err.Error())
		case "USE_UPGRADE_PLUS_TO_PRO":
			s.writeError(w, http.StatusConflict, err.Error())
		default:
			if errors.Is(err, ErrOrderIDConflict) {
				s.writeError(w, http.StatusConflict, "ORDER_ID_CONFLICT")
				return
			}
			s.logger.Error("renew tier failed", "userId", auth.UserID, "orderId", orderID, "targetTier", targetTier, "error", err)
			s.writeError(w, http.StatusInternalServerError, "internal_error")
		}
		return
	}

	s.logger.Info("tier renew", "userId", auth.UserID, "orderId", orderID, "replay", replay, "tier", tier, "tierExpireAt", tierExpireAt)
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":             true,
		"replay":         replay,
		"tier":           tier,
		"tier_expire_at": tierExpireAt,
	})
}

func (s *Server) handleUpgradePlusToPro(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if !s.allowDevOrderEndpoint(w) {
		return
	}
	var body orderRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	orderID := strings.TrimSpace(body.OrderID)
	if orderID == "" {
		s.writeError(w, http.StatusBadRequest, "order_id required")
		return
	}

	ctx := r.Context()
	if err := s.store.EnsureUser(ctx, auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	replay, compensation, tier, tierExpireAt, _, err := s.store.UpgradePlusToPro(ctx, auth.UserID, orderID)
	if err != nil {
		switch err.Error() {
		case "ALREADY_PRO":
			s.writeError(w, http.StatusConflict, err.Error())
		case "FORBIDDEN_TIER":
			s.writeError(w, http.StatusForbidden, err.Error())
		default:
			if errors.Is(err, ErrOrderIDConflict) {
				s.writeError(w, http.StatusConflict, "ORDER_ID_CONFLICT")
				return
			}
			s.logger.Error("upgrade plus to pro failed", "userId", auth.UserID, "orderId", orderID, "error", err)
			s.writeError(w, http.StatusInternalServerError, "internal_error")
		}
		return
	}

	s.logger.Info("tier upgrade plus->pro", "userId", auth.UserID, "orderId", orderID, "replay", replay, "compensation", compensation)
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":                true,
		"replay":            replay,
		"compensation":      compensation,
		"tier":              tier,
		"tier_expire_at":    tierExpireAt,
		"upgrade_remaining": 0,
	})
}

func (s *Server) allowDevOrderEndpoint(w http.ResponseWriter) bool {
	if devOrderEndpointsEnabled() {
		return true
	}
	s.writeError(w, http.StatusServiceUnavailable, "PAYMENT_NOT_CONFIGURED")
	return false
}

func devOrderEndpointsEnabled() bool {
	raw := strings.ToLower(strings.TrimSpace(os.Getenv("ALLOW_DEV_ORDER_ENDPOINTS")))
	if raw != "1" && raw != "true" && raw != "yes" {
		return false
	}
	env := strings.ToLower(strings.TrimSpace(firstNonEmpty(
		os.Getenv("APP_ENV"),
		os.Getenv("ENV"),
		os.Getenv("GO_ENV"),
	)))
	return env == "local" || env == "dev" || env == "development" || env == "test"
}

func isProductionEnv() bool {
	raw := strings.ToLower(strings.TrimSpace(firstNonEmpty(
		os.Getenv("APP_ENV"),
		os.Getenv("ENV"),
		os.Getenv("GO_ENV"),
	)))
	return raw == "prod" || raw == "production"
}

func (s *Server) writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(payload); err != nil {
		s.logger.Error("write json failed", "error", err)
	}
}

func (s *Server) writeError(w http.ResponseWriter, status int, code string) {
	s.writeJSON(w, status, map[string]any{"error": code})
}

func decodeJSONBody[T any](r *http.Request, dst *T) error {
	return decodeJSONBodyLimited(r, dst, defaultJSONBodyLimit)
}

func decodeJSONBodyLimited[T any](r *http.Request, dst *T, maxBytes int64) error {
	defer r.Body.Close()
	if maxBytes <= 0 {
		maxBytes = defaultJSONBodyLimit
	}
	reader := &io.LimitedReader{R: r.Body, N: maxBytes + 1}
	decoder := json.NewDecoder(reader)
	if err := decoder.Decode(dst); err != nil {
		if reader.N <= 0 || isJSONBodyTooLargeError(err) {
			return errJSONBodyTooLarge
		}
		return err
	}
	var extra any
	if err := decoder.Decode(&extra); err != io.EOF {
		if reader.N <= 0 {
			return errJSONBodyTooLarge
		}
		if err == nil {
			return errJSONBodyTrailingData
		}
		return err
	}
	if reader.N <= 0 {
		return errJSONBodyTooLarge
	}
	return nil
}

func (s *Server) writeJSONDecodeError(w http.ResponseWriter, err error) {
	status, code := jsonDecodeErrorStatusAndCode(err)
	s.writeError(w, status, code)
}

func jsonDecodeErrorStatusAndCode(err error) (int, string) {
	if errors.Is(err, errJSONBodyTooLarge) || isJSONBodyTooLargeError(err) {
		return http.StatusRequestEntityTooLarge, "body_too_large"
	}
	return http.StatusBadRequest, "invalid_json"
}

func isJSONBodyTooLargeError(err error) bool {
	var maxBytesError *http.MaxBytesError
	return errors.As(err, &maxBytesError)
}

func (s *Server) requireAuth(w http.ResponseWriter, r *http.Request) (*AuthInfo, bool) {
	auth := ResolveAuthUserID(r)
	if auth.AuthMode == AuthModeUnauthorized {
		s.logger.Warn("auth unauthorized", "masked_ip", auth.MaskedIP)
		s.writeError(w, http.StatusUnauthorized, "unauthorized")
		return nil, false
	}
	if IsAuthStrict() && auth.AuthMode == AuthModeToken && auth.SessionID == "" {
		s.logger.Warn("auth legacy token rejected in strict mode", "masked_ip", auth.MaskedIP)
		s.writeError(w, http.StatusUnauthorized, "unauthorized")
		return nil, false
	}
	if auth.AuthMode == AuthModeToken && auth.SessionID != "" {
		if IsAuthStrict() && !isAccountUserID(auth.UserID) {
			s.logger.Warn("auth session non-account user rejected", "userId", auth.UserID, "masked_ip", auth.MaskedIP)
			s.writeError(w, http.StatusUnauthorized, "unauthorized")
			return nil, false
		}
		active, err := s.store.IsAuthSessionActive(r.Context(), auth.UserID, auth.SessionID, time.Now().UnixMilli())
		if err != nil {
			s.logger.Error("auth session check failed", "userId", auth.UserID, "error", err)
			s.writeError(w, http.StatusInternalServerError, "internal_error")
			return nil, false
		}
		if !active {
			s.logger.Warn("auth session inactive", "userId", auth.UserID, "masked_ip", auth.MaskedIP)
			s.writeError(w, http.StatusUnauthorized, "unauthorized")
			return nil, false
		}
	}
	return &auth, true
}

func ternary[T any](condition bool, left T, right T) T {
	if condition {
		return left
	}
	return right
}

func contextBackground() context.Context {
	return context.Background()
}

func getAWindowByTier(tier Tier) int {
	if tier == TierPro {
		return 9
	}
	return 6
}

func safeSnapshot(userID string, snapshot *SessionSnapshot) SessionSnapshot {
	if snapshot != nil {
		return cloneSessionSnapshot(*snapshot)
	}
	return SessionSnapshot{
		UserID:            userID,
		ARoundsFull:       []SessionRound{},
		MemoryDocument:    "",
		PendingMemory:     false,
		RoundTotal:        0,
		UpdatedAt:         time.Now().UnixMilli(),
		SessionGeneration: 0,
	}
}

func cloneSessionSnapshot(snapshot SessionSnapshot) SessionSnapshot {
	snapshot.ARoundsFull = cloneSessionRounds(snapshot.ARoundsFull)
	if len(snapshot.PendingMemoryJobs) > 0 {
		clonedJobs := make([]MemoryExtractionJob, len(snapshot.PendingMemoryJobs))
		for index, job := range snapshot.PendingMemoryJobs {
			clonedJobs[index] = MemoryExtractionJob{
				RoundTotal: job.RoundTotal,
				Rounds:     cloneSessionRounds(job.Rounds),
			}
		}
		snapshot.PendingMemoryJobs = clonedJobs
	}
	if len(snapshot.TodayAgriItems) > 0 {
		clonedItems := make([]TodayAgriUserItem, len(snapshot.TodayAgriItems))
		copy(clonedItems, snapshot.TodayAgriItems)
		snapshot.TodayAgriItems = clonedItems
	}
	return snapshot
}

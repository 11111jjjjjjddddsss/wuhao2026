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
	summary               *SummaryService
	dailyAgri             *DailyAgriCardService
	dypns                 *DypnsClient
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
	giftCardRedeemLimiter rateLimiter
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

	for _, layer := range []SummaryLayer{SummaryLayerB, SummaryLayerC} {
		result := prompts.ProbeSummaryPrompt(layer)
		if result.OK {
			logger.Info("summary prompt precheck ok", "layer", layer, "prompt_path", result.Path, "prompt_chars", result.Chars)
		} else {
			logger.Warn("summary prompt precheck failed", "layer", layer, "prompt_path", result.Path, "error", result.Error)
		}
	}

	store := NewStore(db, shanghai)
	if err := store.EnsureBootstrapAdminFromEnv(contextBackground(), logger); err != nil {
		return nil, err
	}
	bailian := NewBailianClient()
	dypns, err := NewDypnsClientFromEnv()
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
		summary:               NewSummaryService(store, prompts, bailian, logger),
		dailyAgri:             NewDailyAgriCardService(store, bailian, logger, shanghai),
		dypns:                 dypns,
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
		giftCardRedeemLimiter: newGiftCardRedeemRateLimiter(redisClient),
	}
	server.registerRoutes()
	return server, nil
}

func (s *Server) Handler() http.Handler {
	return s.withAccessLog(s.mux)
}

func (s *Server) Close() error {
	if s == nil || s.redisClient == nil {
		return nil
	}
	return s.redisClient.Close()
}

func (s *Server) registerRoutes() {
	s.mux.HandleFunc("GET /healthz", s.handleHealthz)
	s.mux.HandleFunc("POST /admin-api/v1/auth/login", s.handleAdminLogin)
	s.mux.HandleFunc("GET /admin-api/v1/auth/me", s.handleAdminMe)
	s.mux.HandleFunc("POST /admin-api/v1/auth/logout", s.handleAdminLogout)
	s.mux.HandleFunc("GET /admin-api/v1/overview", s.handleAdminOverview)
	s.mux.HandleFunc("GET /admin-api/v1/monitoring", s.handleAdminMonitoring)
	s.mux.HandleFunc("GET /admin-api/v1/users", s.handleAdminUsers)
	s.mux.HandleFunc("GET /admin-api/v1/users/detail", s.handleAdminUserDetail)
	s.mux.HandleFunc("GET /admin-api/v1/support/conversations", s.handleAdminSupportConversations)
	s.mux.HandleFunc("GET /admin-api/v1/support/messages", s.handleAdminSupportMessages)
	s.mux.HandleFunc("POST /admin-api/v1/support/messages", s.handleAdminCreateSupportMessage)
	s.mux.HandleFunc("GET /admin-api/v1/app-logs", s.handleAdminAppLogs)
	s.mux.HandleFunc("GET /admin-api/v1/audit-logs", s.handleAdminAuditLogs)
	s.mux.HandleFunc("GET /admin-api/v1/today-agri/cards", s.handleAdminTodayAgriCards)
	s.mux.HandleFunc("GET /admin-api/v1/app-update/android", s.handleAdminAppUpdateAndroid)
	s.mux.HandleFunc("GET /admin-api/v1/gift-cards/batches", s.handleAdminGiftCardBatches)
	s.mux.HandleFunc("POST /admin-api/v1/gift-cards/batches", s.handleAdminCreateGiftCardBatch)
	s.mux.HandleFunc("GET /admin-api/v1/gift-cards/summary", s.handleAdminGiftCardSummary)
	s.mux.HandleFunc("GET /admin-api/v1/gift-cards/cards", s.handleAdminGiftCards)
	s.mux.HandleFunc("POST /admin-api/v1/gift-cards/void", s.handleAdminVoidGiftCard)
	s.mux.HandleFunc("GET /admin-api/v1/gift-cards/attempts", s.handleAdminGiftCardAttempts)
	s.mux.HandleFunc("POST /api/auth/fusion/token", s.handleAuthFusionToken)
	s.mux.HandleFunc("POST /api/auth/fusion/verify", s.handleAuthFusionVerify)
	s.mux.HandleFunc("POST /api/auth/fusion/login", s.handleAuthFusionLogin)
	s.mux.HandleFunc("POST /api/auth/sms/send", s.handleAuthSMSSend)
	s.mux.HandleFunc("POST /api/auth/sms/login", s.handleAuthSMSLogin)
	s.mux.HandleFunc("POST /api/auth/logout", s.handleAuthLogout)
	s.mux.HandleFunc("GET /api/auth/session", s.handleAuthSession)
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
	s.mux.HandleFunc("GET /api/today-agri-card", s.handleTodayAgriCard)
	s.mux.HandleFunc("GET /api/app/update", s.handleAppUpdate)
	s.mux.HandleFunc("POST /api/gift-cards/redeem", s.handleGiftCardRedeem)
	s.mux.HandleFunc("POST /api/app/logs", s.handleCreateClientAppLog)
	s.mux.HandleFunc("GET /api/support/summary", s.handleSupportSummary)
	s.mux.HandleFunc("GET /api/support/messages", s.handleSupportMessages)
	s.mux.HandleFunc("POST /api/support/messages", s.handleCreateSupportMessage)
	s.mux.HandleFunc("POST /api/support/read", s.handleMarkSupportRead)
	s.mux.HandleFunc("POST /internal/jobs/today-agri-card/generate", s.handleGenerateTodayAgriCard)
	s.mux.HandleFunc("GET /internal/app/logs", s.handleInternalClientAppLogs)
	s.mux.HandleFunc("GET /internal/admin/audit-logs", s.handleInternalAdminAuditLogs)
	s.mux.HandleFunc("GET /internal/support/conversations", s.handleInternalSupportConversations)
	s.mux.HandleFunc("GET /internal/support/messages", s.handleInternalSupportMessages)
	s.mux.HandleFunc("POST /internal/support/messages", s.handleInternalCreateSupportMessage)
	s.mux.HandleFunc("POST /api/chat/stream", s.handleChatStream)
	s.mux.HandleFunc("POST /upload", s.handleUpload)
	s.mux.HandleFunc("/uploads/", s.handleUploadsStatic)
}

func (s *Server) handleHealthz(w http.ResponseWriter, _ *http.Request) {
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":                  true,
		"bailian":             ternary(s.bailian.HasKeyConfigured(), "ok", "missing_key"),
		"dypns":               ternary(s.dypns.HasClientConfigured(), "ok", "missing_key"),
		"dypns_fusion":        ternary(s.dypns.HasFusionConfigured(), "ok", "missing_config"),
		"dypns_sms":           ternary(s.dypns.HasSMSConfigured(), "ok", "missing_config"),
		"redis":               ternary(s.redisClient != nil, "ok", "missing_config"),
		"upload_storage":      uploadStoreHealthStatus(s.uploadStore),
		"auth_strict":         IsAuthStrict(),
		"dev_order_endpoints": devOrderEndpointsEnabled(),
	})
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

	upgradeRemaining, err := s.store.GetUpgradeRemaining(ctx, auth.UserID)
	if err != nil {
		s.logger.Error("get upgrade remaining failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	s.writeJSON(w, http.StatusOK, map[string]any{
		"tier":                     tier,
		"tier_expire_at":           tierExpireAt,
		"daily_remaining":          status.Remaining,
		"topup_remaining":          topupRemaining,
		"topup_earliest_expire_at": topupExpireAt,
		"upgrade_remaining":        upgradeRemaining,
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

	snapshot, err := s.store.GetSessionSnapshot(r.Context(), auth.UserID)
	if err != nil {
		s.logger.Error("get session snapshot failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	safe := safeSnapshot(auth.UserID, snapshot)
	generationState, err := s.store.GetSessionGenerationState(r.Context(), auth.UserID)
	if err != nil {
		s.logger.Error("get session generation failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	safe.SessionGeneration = generationState.Generation
	uiRounds := safe.ARoundsFull
	if archivedRounds, err := s.store.GetSessionRoundsForUI(r.Context(), auth.UserID); err != nil {
		s.logger.Warn("get session ui archive failed", "userId", auth.UserID, "error", err)
	} else if len(archivedRounds) > 0 {
		uiRounds = mergeSessionRoundsForUI(safe.ARoundsFull, archivedRounds)
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"user_id":            safe.UserID,
		"a_json":             safe.ARoundsFull,
		"a_rounds_full":      safe.ARoundsFull,
		"a_rounds_for_ui":    uiRounds,
		"b_summary":          safe.BSummary,
		"c_summary":          safe.CSummary,
		"round_total":        safe.RoundTotal,
		"updated_at":         safe.UpdatedAt,
		"session_generation": safe.SessionGeneration,
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
			s.logger.Error("buy topup failed", "userId", auth.UserID, "orderId", orderID, "error", err)
			s.writeError(w, http.StatusInternalServerError, err.Error())
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
			s.logger.Error("renew tier failed", "userId", auth.UserID, "orderId", orderID, "targetTier", targetTier, "error", err)
			s.writeError(w, http.StatusInternalServerError, err.Error())
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

	replay, compensation, tier, tierExpireAt, upgradeRemaining, err := s.store.UpgradePlusToPro(ctx, auth.UserID, orderID)
	if err != nil {
		switch err.Error() {
		case "ALREADY_PRO":
			s.writeError(w, http.StatusConflict, err.Error())
		case "FORBIDDEN_TIER":
			s.writeError(w, http.StatusForbidden, err.Error())
		default:
			s.logger.Error("upgrade plus to pro failed", "userId", auth.UserID, "orderId", orderID, "error", err)
			s.writeError(w, http.StatusInternalServerError, err.Error())
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
		"upgrade_remaining": upgradeRemaining,
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
	if errors.Is(err, errJSONBodyTooLarge) || isJSONBodyTooLargeError(err) {
		s.writeError(w, http.StatusRequestEntityTooLarge, "body_too_large")
		return
	}
	s.writeError(w, http.StatusBadRequest, "invalid_json")
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
	if auth.AuthMode == AuthModeToken && auth.SessionID != "" {
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
		BSummary:          "",
		CSummary:          "",
		RoundTotal:        0,
		UpdatedAt:         time.Now().UnixMilli(),
		SessionGeneration: 0,
	}
}

func cloneSessionSnapshot(snapshot SessionSnapshot) SessionSnapshot {
	clonedRounds := make([]SessionRound, len(snapshot.ARoundsFull))
	copy(clonedRounds, snapshot.ARoundsFull)
	snapshot.ARoundsFull = clonedRounds
	return snapshot
}

package app

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

const (
	sseHeartbeatInterval = 20 * time.Second
)

type Server struct {
	logger       *slog.Logger
	mux          *http.ServeMux
	store        *Store
	prompts      *PromptLoader
	bailian      *BailianClient
	summary      *SummaryService
	shanghai     *time.Location
	assetDir     string
	uploadsDir   string
	systemAnchor string
	rateLimiter  *chatRateLimiter
}

type summaryRequest struct {
	BSummary string `json:"b_summary"`
	CSummary string `json:"c_summary"`
}

type roundCompleteRequest struct {
	ClientMsgID   string   `json:"client_msg_id"`
	UserText      string   `json:"user_text"`
	UserImages    []string `json:"user_images,omitempty"`
	AssistantText string   `json:"assistant_text"`
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
	if err := InitMySQL(contextBackground(), db, migrationsDir); err != nil {
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
	bailian := NewBailianClient()
	server := &Server{
		logger:       logger,
		mux:          http.NewServeMux(),
		store:        store,
		prompts:      prompts,
		bailian:      bailian,
		summary:      NewSummaryService(store, prompts, bailian, logger),
		shanghai:     shanghai,
		assetDir:     assetDir,
		uploadsDir:   uploadsDir,
		systemAnchor: systemAnchor,
		rateLimiter:  newChatRateLimiter(),
	}
	server.registerRoutes()
	return server, nil
}

func (s *Server) Handler() http.Handler {
	return s.mux
}

func (s *Server) registerRoutes() {
	s.mux.HandleFunc("GET /healthz", s.handleHealthz)
	s.mux.HandleFunc("GET /api/me", s.handleGetMe)
	s.mux.HandleFunc("POST /api/session/b", s.handleSessionB)
	s.mux.HandleFunc("POST /api/session/c", s.handleSessionC)
	s.mux.HandleFunc("GET /api/session/snapshot", s.handleSessionSnapshot)
	s.mux.HandleFunc("POST /api/session/round_complete", s.handleSessionRoundComplete)
	s.mux.HandleFunc("POST /api/topup/buy", s.handleTopupBuy)
	s.mux.HandleFunc("POST /api/tier/renew_plus", s.handleRenewPlus)
	s.mux.HandleFunc("POST /api/tier/renew_pro", s.handleRenewPro)
	s.mux.HandleFunc("POST /api/tier/upgrade_plus_to_pro", s.handleUpgradePlusToPro)
	s.mux.HandleFunc("POST /api/chat/stream", s.handleChatStream)
	s.mux.HandleFunc("POST /upload", s.handleUpload)
	s.mux.HandleFunc("/uploads/", s.handleUploadsStatic)
}

func (s *Server) handleHealthz(w http.ResponseWriter, _ *http.Request) {
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":          true,
		"bailian":     ternary(s.bailian.HasKeyConfigured(), "ok", "missing_key"),
		"auth_strict": IsAuthStrict(),
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
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}

	var body summaryRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid_json")
		return
	}
	if strings.TrimSpace(body.BSummary) == "" {
		s.writeError(w, http.StatusBadRequest, "b_summary required")
		return
	}

	if err := s.store.WriteUserBSummary(r.Context(), auth.UserID, body.BSummary); err != nil {
		s.logger.Error("write B summary failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) handleSessionC(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}

	var body summaryRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid_json")
		return
	}
	if strings.TrimSpace(body.CSummary) == "" {
		s.writeError(w, http.StatusBadRequest, "c_summary required")
		return
	}

	if err := s.store.WriteUserCSummary(r.Context(), auth.UserID, body.CSummary); err != nil {
		s.logger.Error("write C summary failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"ok": true})
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
	uiRounds := safe.ARoundsFull
	if archivedRounds, err := s.store.GetSessionRoundsForUI(r.Context(), auth.UserID); err != nil {
		s.logger.Warn("get session ui archive failed", "userId", auth.UserID, "error", err)
	} else if len(archivedRounds) > 0 {
		uiRounds = mergeSessionRoundsForUI(safe.ARoundsFull, archivedRounds)
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"user_id":         safe.UserID,
		"a_json":          safe.ARoundsFull,
		"a_rounds_full":   safe.ARoundsFull,
		"a_rounds_for_ui": uiRounds,
		"b_summary":       safe.BSummary,
		"c_summary":       safe.CSummary,
		"round_total":     safe.RoundTotal,
		"updated_at":      safe.UpdatedAt,
	})
}

func (s *Server) handleSessionRoundComplete(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}

	var body roundCompleteRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid_json")
		return
	}

	clientMsgID := strings.TrimSpace(body.ClientMsgID)
	userText := strings.TrimSpace(body.UserText)
	userImages := normalizeImages(body.UserImages)
	assistantText := strings.TrimSpace(body.AssistantText)
	if validationError := validateRoundCompleteInput(clientMsgID, userText, userImages, assistantText); validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}

	ctx := r.Context()
	if err := s.store.EnsureUser(ctx, auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	tier, _, err := s.store.GetTierForUser(ctx, auth.UserID, TierFree)
	if err != nil {
		s.logger.Error("get tier failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	aWindowRounds := getAWindowByTier(tier)
	bEveryRounds, cEveryRounds := GetSummaryIntervals(tier)
	clientIP := GetClientIP(r)
	region := ParseRegionFromHeaders(r.Header)
	if region == nil {
		resolved := ResolveRegionByIP(clientIP)
		region = &resolved
	}
	if err := s.store.TouchSessionContext(ctx, auth.UserID, region.Region, region.Source, region.Reliability, time.Now().UnixMilli()); err != nil {
		s.logger.Warn("touch session context failed", "userId", auth.UserID, "error", err)
	}
	replay, snapshot, err := s.store.AppendSessionRoundComplete(
		ctx,
		auth.UserID,
		clientMsgID,
		SessionRound{
			ClientMsgID:       clientMsgID,
			User:              userText,
			UserImages:        userImages,
			Assistant:         assistantText,
			Region:            region.Region,
			RegionSource:      region.Source,
			RegionReliability: region.Reliability,
		},
		aWindowRounds,
		bEveryRounds,
		cEveryRounds,
		"round_complete",
	)
	if err != nil {
		s.logger.Error("append session round failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	if snapshot != nil {
		snapshotCopy := cloneSessionSnapshot(*snapshot)
		go s.summary.ProcessSessionSummaries(auth.UserID, &snapshotCopy)
	}
	safe := safeSnapshot(auth.UserID, snapshot)

	s.logger.Info("session round_complete",
		"userId", auth.UserID,
		"clientMsgId", clientMsgID,
		"replay", replay,
		"tier", tier,
		"a_size", len(safe.ARoundsFull),
		"round_total", safe.RoundTotal,
	)

	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":          true,
		"replay":      replay,
		"a_json":      safe.ARoundsFull,
		"round_total": safe.RoundTotal,
		"updated_at":  safe.UpdatedAt,
	})
}

func (s *Server) handleTopupBuy(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	var body orderRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid_json")
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

func validateRoundCompleteInput(clientMsgID string, userText string, userImages []string, assistantText string) string {
	if clientMsgID == "" {
		return "client_msg_id required"
	}
	if len(userImages) > 4 {
		return "single request supports up to 4 images"
	}
	if strings.TrimSpace(userText) == "" && len(userImages) == 0 {
		return "user_text or user_images required"
	}
	if strings.TrimSpace(assistantText) == "" {
		return "assistant_text required"
	}
	return ""
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
	var body orderRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid_json")
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
	var body orderRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid_json")
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
	defer r.Body.Close()
	decoder := json.NewDecoder(r.Body)
	return decoder.Decode(dst)
}

func (s *Server) requireAuth(w http.ResponseWriter, r *http.Request) (*AuthInfo, bool) {
	auth := ResolveAuthUserID(r)
	if auth.AuthMode == AuthModeUnauthorized {
		s.logger.Warn("auth unauthorized", "masked_ip", auth.MaskedIP)
		s.writeError(w, http.StatusUnauthorized, "unauthorized")
		return nil, false
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
		UserID:      userID,
		ARoundsFull: []SessionRound{},
		BSummary:    "",
		CSummary:    "",
		RoundTotal:  0,
		UpdatedAt:   time.Now().UnixMilli(),
	}
}

func cloneSessionSnapshot(snapshot SessionSnapshot) SessionSnapshot {
	clonedRounds := make([]SessionRound, len(snapshot.ARoundsFull))
	copy(clonedRounds, snapshot.ARoundsFull)
	snapshot.ARoundsFull = clonedRounds
	return snapshot
}

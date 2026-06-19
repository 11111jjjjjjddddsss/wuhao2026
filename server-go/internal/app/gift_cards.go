package app

import (
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"math/big"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
)

const (
	defaultGiftCardRedeemRateLimitWindow        = time.Hour
	defaultGiftCardRedeemRateLimitMaxHits       = 10
	defaultGiftCardRedeemRateLimitPruneInterval = 10 * time.Minute

	defaultGiftCardDurationDays = 30
	defaultGiftCardValidDays    = 365
	maxGiftCardBatchQuantity    = 200
	giftCardCodeAlphabet        = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
)

var (
	errGiftCardNotFound      = errors.New("gift_card_not_found")
	errGiftCardInactive      = errors.New("gift_card_inactive")
	errGiftCardExpired       = errors.New("gift_card_expired")
	errGiftCardLowerTier     = errors.New("gift_card_lower_tier")
	errGiftCardInvalidCode   = errors.New("gift_card_invalid_code")
	errGiftCardSecretMissing = errors.New("gift_card_secret_missing")
)

type AdminGiftCardBatch struct {
	BatchID       string `json:"batch_id"`
	Name          string `json:"name"`
	Tier          Tier   `json:"tier"`
	DurationDays  int    `json:"duration_days"`
	Quantity      int    `json:"quantity"`
	ActiveCount   int64  `json:"active_count"`
	RedeemedCount int64  `json:"redeemed_count"`
	VoidCount     int64  `json:"void_count"`
	ValidFrom     int64  `json:"valid_from"`
	ValidUntil    *int64 `json:"valid_until,omitempty"`
	CreatedBy     string `json:"created_by"`
	Note          string `json:"note,omitempty"`
	CreatedAt     int64  `json:"created_at"`
}

type AdminGiftCardEntry struct {
	CardID                    string            `json:"card_id"`
	BatchID                   string            `json:"batch_id"`
	Code                      string            `json:"code,omitempty"`
	CodeMask                  string            `json:"code_mask"`
	CodeSuffix                string            `json:"code_suffix"`
	Tier                      Tier              `json:"tier"`
	DurationDays              int               `json:"duration_days"`
	Status                    string            `json:"status"`
	ValidFrom                 int64             `json:"valid_from"`
	ValidUntil                *int64            `json:"valid_until,omitempty"`
	CreatedBy                 string            `json:"created_by"`
	Note                      string            `json:"note,omitempty"`
	RedeemedUserID            string            `json:"redeemed_user_id,omitempty"`
	RedeemedPhoneMask         string            `json:"redeemed_phone_mask,omitempty"`
	RedeemedRegion            string            `json:"redeemed_region,omitempty"`
	RedeemedRegionSource      RegionSource      `json:"redeemed_region_source,omitempty"`
	RedeemedRegionReliability RegionReliability `json:"redeemed_region_reliability,omitempty"`
	RedeemedAt                *int64            `json:"redeemed_at,omitempty"`
	MembershipExpireAt        *int64            `json:"membership_expire_at,omitempty"`
	VoidedAt                  *int64            `json:"voided_at,omitempty"`
	CreatedAt                 int64             `json:"created_at"`
	UpdatedAt                 int64             `json:"updated_at"`
}

type AdminGiftCardAttempt struct {
	ID                int64             `json:"id"`
	CodeSuffix        string            `json:"code_suffix,omitempty"`
	UserID            string            `json:"user_id,omitempty"`
	Success           bool              `json:"success"`
	FailureReason     string            `json:"failure_reason,omitempty"`
	MaskedIP          string            `json:"masked_ip,omitempty"`
	Region            string            `json:"region,omitempty"`
	RegionSource      RegionSource      `json:"region_source,omitempty"`
	RegionReliability RegionReliability `json:"region_reliability,omitempty"`
	CreatedAt         int64             `json:"created_at"`
}

type AdminGiftCardSummary struct {
	BatchCount        int64                        `json:"batch_count"`
	ActiveCount       int64                        `json:"active_count"`
	RedeemableCount   int64                        `json:"redeemable_count"`
	RedeemedCount     int64                        `json:"redeemed_count"`
	VoidCount         int64                        `json:"void_count"`
	FailedAttempts24h int64                        `json:"failed_attempts_24h"`
	FailureReasons    []AdminGiftCardFailureReason `json:"failure_reasons"`
}

type AdminGiftCardFailureReason struct {
	Reason string `json:"reason"`
	Count  int64  `json:"count"`
}

type AdminGiftCardCreatedCode struct {
	CardID       string `json:"card_id"`
	Code         string `json:"code"`
	CodeMask     string `json:"code_mask"`
	CodeSuffix   string `json:"code_suffix"`
	Tier         Tier   `json:"tier"`
	DurationDays int    `json:"duration_days"`
	ValidUntil   *int64 `json:"valid_until,omitempty"`
}

type adminGiftCardCreateBatchRequest struct {
	Name         string `json:"name"`
	Tier         string `json:"tier"`
	DurationDays int    `json:"duration_days"`
	Quantity     int    `json:"quantity"`
	ValidUntil   *int64 `json:"valid_until,omitempty"`
	Note         string `json:"note"`
	Confirmation string `json:"confirmation"`
}

type adminGiftCardVoidRequest struct {
	CardID       string `json:"card_id"`
	Reason       string `json:"reason"`
	Confirmation string `json:"confirmation"`
}

type giftCardRedeemRequest struct {
	Code string `json:"code"`
}

type GiftCardRedeemResult struct {
	OK                 bool              `json:"ok"`
	Replay             bool              `json:"replay"`
	CardID             string            `json:"card_id"`
	BatchID            string            `json:"batch_id"`
	Tier               Tier              `json:"tier"`
	AppliedTier        Tier              `json:"applied_tier"`
	DurationDays       int               `json:"duration_days"`
	MembershipExpireAt int64             `json:"membership_expire_at"`
	RedeemedAt         int64             `json:"redeemed_at"`
	Region             string            `json:"region,omitempty"`
	RegionSource       RegionSource      `json:"region_source,omitempty"`
	RegionReliability  RegionReliability `json:"region_reliability,omitempty"`
}

func newGiftCardRedeemRateLimiter(redisClient *redis.Client) rateLimiter {
	config := rateLimitConfig{
		Window:        envDurationWithDefault("GIFT_CARD_REDEEM_RATE_LIMIT_WINDOW_SECONDS", defaultGiftCardRedeemRateLimitWindow),
		MaxHits:       envIntWithDefault("GIFT_CARD_REDEEM_RATE_LIMIT_MAX_HITS", defaultGiftCardRedeemRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("GIFT_CARD_REDEEM_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultGiftCardRedeemRateLimitPruneInterval),
	}
	if redisClient != nil {
		return newRedisRateLimiter(redisClient, config, redisRateLimitPrefix, defaultGiftCardRedeemRateLimitWindow, defaultGiftCardRedeemRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(normalizeRateLimitConfig(
		config,
		defaultGiftCardRedeemRateLimitWindow,
		defaultGiftCardRedeemRateLimitMaxHits,
		defaultGiftCardRedeemRateLimitPruneInterval,
	))
}

func giftCardRedeemRateLimitKey(userID string, ip string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	return "gift_card_redeem:" + rateLimitHash(userID, secret) + ":" + rateLimitHash(ip, secret)
}

func (s *Server) handleAdminGiftCardBatches(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "finance_ops", "ops_readonly", "auditor")
	if !ok {
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), adminDashboardTimeout)
	defer cancel()
	batches, err := s.store.ListGiftCardBatches(ctx, parseAdminLimit(r.URL.Query().Get("limit")))
	if err != nil {
		s.logger.Error("admin list gift card batches failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.batches", "gift_cards", "", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.batches", "gift_cards", "", "", true, http.StatusOK, map[string]any{"row_count": len(batches)})
	s.writeJSON(w, http.StatusOK, map[string]any{"batches": batches})
}

func (s *Server) handleAdminCreateGiftCardBatch(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "finance_ops")
	if !ok {
		return
	}
	var body adminGiftCardCreateBatchRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.recordAdminGiftCardBatchValidationFailure(r, admin.User.Username, body, "invalid_json")
		s.writeJSONDecodeError(w, err)
		return
	}
	input, validationError := normalizeGiftCardBatchInput(body, admin.User.Username, time.Now())
	if validationError != "" {
		s.recordAdminGiftCardBatchValidationFailure(r, admin.User.Username, body, validationError)
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if code := adminGiftCardBatchConfirmationError(body, input.Quantity, input.Tier, input.DurationDays); code != "" {
		s.recordAdminGiftCardBatchValidationFailure(r, admin.User.Username, body, code)
		s.writeError(w, http.StatusBadRequest, code)
		return
	}
	batch, codes, err := s.store.CreateGiftCardBatch(r.Context(), input)
	if err != nil {
		s.logger.Error("admin create gift card batch failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.create_batch", "gift_cards", "", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.create_batch", "gift_card_batch", batch.BatchID, "", true, http.StatusOK, map[string]any{
		"tier":          string(batch.Tier),
		"quantity":      batch.Quantity,
		"duration_days": batch.DurationDays,
	})
	s.writeJSON(w, http.StatusOK, map[string]any{
		"batch":   batch,
		"codes":   codes,
		"warning": "完整卡码会加密保存，后台礼品卡列表可直接查看和复制；不要把卡码写入备注、审计原因或公开文档。",
	})
}

func (s *Server) handleAdminGiftCards(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "finance_ops", "ops_readonly", "auditor")
	if !ok {
		return
	}
	filter := GiftCardListQuery{
		BatchID:     strings.TrimSpace(r.URL.Query().Get("batch_id")),
		Status:      normalizeGiftCardStatus(r.URL.Query().Get("status")),
		UserID:      normalizeUserID(r.URL.Query().Get("user_id")),
		CodeSuffix:  normalizeGiftCardCodeSuffix(r.URL.Query().Get("code_suffix")),
		Limit:       parseAdminLimit(r.URL.Query().Get("limit")),
		IncludeCode: adminCanViewGiftCardCodes(admin.User.Role),
	}
	ctx, cancel := context.WithTimeout(r.Context(), adminDashboardTimeout)
	defer cancel()
	cards, err := s.store.ListGiftCards(ctx, filter)
	if err != nil {
		s.logger.Error("admin list gift cards failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.cards", "gift_cards", "", filter.UserID, false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	codeVisible := filter.IncludeCode
	if !codeVisible {
		stripGiftCardCodes(cards)
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.cards", "gift_cards", "", filter.UserID, true, http.StatusOK, map[string]any{"row_count": len(cards), "status": filter.Status, "code_visible": codeVisible})
	s.writeJSON(w, http.StatusOK, map[string]any{"cards": cards, "filter": filter})
}

func (s *Server) handleAdminGiftCardSummary(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "finance_ops", "ops_readonly", "auditor")
	if !ok {
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), adminDashboardTimeout)
	defer cancel()
	summary, err := s.store.GetGiftCardSummary(ctx, time.Now().UnixMilli())
	if err != nil {
		s.logger.Error("admin gift card summary failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.summary", "gift_cards", "", "", false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.summary", "gift_cards", "", "", true, http.StatusOK, map[string]any{
		"failed_attempts_24h": summary.FailedAttempts24h,
		"active_count":        summary.ActiveCount,
	})
	s.writeJSON(w, http.StatusOK, map[string]any{"summary": summary})
}

func (s *Server) handleAdminVoidGiftCard(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "finance_ops")
	if !ok {
		return
	}
	var body adminGiftCardVoidRequest
	if err := decodeJSONBodyLimited(r, &body, 4*1024); err != nil {
		s.recordAdminGiftCardVoidValidationFailure(r, admin.User.Username, body, "invalid_json")
		s.writeJSONDecodeError(w, err)
		return
	}
	cardID := strings.TrimSpace(body.CardID)
	reason := strings.TrimSpace(body.Reason)
	if cardID == "" {
		s.recordAdminGiftCardVoidValidationFailure(r, admin.User.Username, body, "card_id_required")
		s.writeError(w, http.StatusBadRequest, "card_id_required")
		return
	}
	if reason == "" {
		s.recordAdminGiftCardVoidValidationFailure(r, admin.User.Username, body, "reason_required")
		s.writeError(w, http.StatusBadRequest, "reason_required")
		return
	}
	if code := adminGiftCardVoidConfirmationError(body); code != "" {
		s.recordAdminGiftCardVoidValidationFailure(r, admin.User.Username, body, code)
		s.writeError(w, http.StatusBadRequest, code)
		return
	}
	if err := s.store.VoidGiftCard(r.Context(), cardID, time.Now().UnixMilli()); err != nil {
		status := http.StatusBadRequest
		code := "gift_card_inactive"
		if !errors.Is(err, errGiftCardInactive) {
			status = http.StatusInternalServerError
			code = "internal_error"
			s.logger.Error("admin void gift card failed", "cardID", cardID, "error", err)
		}
		s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.void", "gift_cards", cardID, "", false, status, map[string]any{
			"error_code": code,
			"reason":     truncateRunes(reason, 160),
		})
		s.writeError(w, status, code)
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.void", "gift_cards", cardID, "", true, http.StatusOK, map[string]any{
		"reason": truncateRunes(reason, 160),
	})
	s.writeJSON(w, http.StatusOK, map[string]any{"ok": true, "card_id": cardID})
}

func (s *Server) handleAdminGiftCardAttempts(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "finance_ops", "ops_readonly", "auditor")
	if !ok {
		return
	}
	filter := GiftCardAttemptQuery{
		UserID:        normalizeUserID(r.URL.Query().Get("user_id")),
		CodeSuffix:    normalizeGiftCardCodeSuffix(r.URL.Query().Get("code_suffix")),
		SuccessFilter: normalizeGiftCardAttemptSuccess(r.URL.Query().Get("success")),
		FailureReason: strings.TrimSpace(r.URL.Query().Get("failure_reason")),
		Limit:         parseAdminLimit(r.URL.Query().Get("limit")),
	}
	ctx, cancel := context.WithTimeout(r.Context(), adminDashboardTimeout)
	defer cancel()
	attempts, err := s.store.ListGiftCardAttempts(ctx, filter)
	if err != nil {
		s.logger.Error("admin list gift card attempts failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.attempts", "gift_card_redemption_attempts", "", filter.UserID, false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.gift_cards.attempts", "gift_card_redemption_attempts", "", filter.UserID, true, http.StatusOK, map[string]any{"row_count": len(attempts)})
	s.writeJSON(w, http.StatusOK, map[string]any{"attempts": attempts, "filter": filter})
}

func (s *Server) handleGiftCardRedeem(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if err := s.store.EnsureUser(r.Context(), auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user for gift card redeem failed", "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if s.giftCardRedeemLimiter != nil {
		if allowed, retryAfterSec := s.giftCardRedeemLimiter.Consume(giftCardRedeemRateLimitKey(auth.UserID, GetClientIP(r)), time.Now()); !allowed {
			w.Header().Set("Retry-After", strconv.Itoa(retryAfterSec))
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{"error": "rate_limited", "retry_after_sec": retryAfterSec})
			return
		}
	}
	var body giftCardRedeemRequest
	if err := decodeJSONBodyLimited(r, &body, 4*1024); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	region := giftCardRegionFromRequest(r)
	result, err := s.store.RedeemGiftCard(r.Context(), body.Code, auth.UserID, region, maskIP(GetClientIP(r)), time.Now().UnixMilli())
	if err != nil {
		code := giftCardErrorCode(err)
		status := giftCardRedeemHTTPStatus(err)
		if status >= http.StatusInternalServerError {
			s.logger.Error("gift card redeem system failed", "error", err)
		}
		s.recordAdminAuditLog(r, "app_user:"+auth.UserID, "gift_card.redeem", "gift_cards", "", auth.UserID, false, status, map[string]any{"error_code": code})
		s.writeError(w, status, code)
		return
	}
	s.recordAdminAuditLog(r, "app_user:"+auth.UserID, "gift_card.redeem", "gift_cards", result.CardID, auth.UserID, true, http.StatusOK, map[string]any{"tier": string(result.Tier), "applied_tier": string(result.AppliedTier), "duration_days": result.DurationDays, "replay": result.Replay})
	s.writeJSON(w, http.StatusOK, result)
}

type GiftCardBatchInput struct {
	Name         string
	Tier         Tier
	DurationDays int
	Quantity     int
	ValidFrom    int64
	ValidUntil   *int64
	CreatedBy    string
	Note         string
}

type GiftCardListQuery struct {
	BatchID     string `json:"batch_id,omitempty"`
	Status      string `json:"status,omitempty"`
	UserID      string `json:"user_id,omitempty"`
	CodeSuffix  string `json:"code_suffix,omitempty"`
	Limit       int    `json:"limit"`
	IncludeCode bool   `json:"-"`
}

type GiftCardAttemptQuery struct {
	UserID        string `json:"user_id,omitempty"`
	CodeSuffix    string `json:"code_suffix,omitempty"`
	SuccessFilter string `json:"success,omitempty"`
	FailureReason string `json:"failure_reason,omitempty"`
	Limit         int    `json:"limit"`
}

func (s *Store) CreateGiftCardBatch(ctx context.Context, input GiftCardBatchInput) (AdminGiftCardBatch, []AdminGiftCardCreatedCode, error) {
	batchID, err := randomGiftCardID("gcb")
	if err != nil {
		return AdminGiftCardBatch{}, nil, err
	}
	now := time.Now().UnixMilli()
	batch := AdminGiftCardBatch{
		BatchID:      batchID,
		Name:         input.Name,
		Tier:         input.Tier,
		DurationDays: input.DurationDays,
		Quantity:     input.Quantity,
		ValidFrom:    input.ValidFrom,
		ValidUntil:   input.ValidUntil,
		CreatedBy:    input.CreatedBy,
		Note:         input.Note,
		CreatedAt:    now,
	}
	codes := make([]AdminGiftCardCreatedCode, 0, input.Quantity)
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return AdminGiftCardBatch{}, nil, err
	}
	defer rollbackQuietly(tx)
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO gift_card_batches(batch_id, name, tier, duration_days, quantity, valid_from, valid_until, created_by, note, created_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		batch.BatchID,
		batch.Name,
		string(batch.Tier),
		batch.DurationDays,
		batch.Quantity,
		batch.ValidFrom,
		nullableInt64(batch.ValidUntil),
		batch.CreatedBy,
		nullableTrimmed(batch.Note),
		batch.CreatedAt,
	); err != nil {
		return AdminGiftCardBatch{}, nil, err
	}
	for i := 0; i < input.Quantity; i++ {
		cardID, err := randomGiftCardID("gcc")
		if err != nil {
			return AdminGiftCardBatch{}, nil, err
		}
		code, err := generateGiftCardCode()
		if err != nil {
			return AdminGiftCardBatch{}, nil, err
		}
		codeHash := giftCardCodeHash(code)
		if codeHash == "" {
			return AdminGiftCardBatch{}, nil, errGiftCardSecretMissing
		}
		mask := giftCardCodeMask(code)
		suffix := giftCardCodeSuffix(code)
		codeCiphertext, err := encryptGiftCardCode(code)
		if err != nil {
			return AdminGiftCardBatch{}, nil, err
		}
		if _, err := tx.ExecContext(
			ctx,
			`INSERT INTO gift_cards(card_id, batch_id, code_hash, code_mask, code_suffix, code_ciphertext, tier, duration_days, status, valid_from, valid_until, created_by, note, updated_at, created_at)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'active', ?, ?, ?, ?, ?, ?)`,
			cardID,
			batch.BatchID,
			codeHash,
			mask,
			suffix,
			codeCiphertext,
			string(input.Tier),
			input.DurationDays,
			input.ValidFrom,
			nullableInt64(input.ValidUntil),
			input.CreatedBy,
			nullableTrimmed(input.Note),
			now,
			now,
		); err != nil {
			return AdminGiftCardBatch{}, nil, err
		}
		codes = append(codes, AdminGiftCardCreatedCode{
			CardID:       cardID,
			Code:         code,
			CodeMask:     mask,
			CodeSuffix:   suffix,
			Tier:         input.Tier,
			DurationDays: input.DurationDays,
			ValidUntil:   input.ValidUntil,
		})
	}
	if err := tx.Commit(); err != nil {
		return AdminGiftCardBatch{}, nil, err
	}
	batch.ActiveCount = int64(input.Quantity)
	return batch, codes, nil
}

func (s *Store) ListGiftCardBatches(ctx context.Context, limit int) ([]AdminGiftCardBatch, error) {
	if limit <= 0 || limit > maxAdminListLimit {
		limit = defaultAdminListLimit
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT
		   b.batch_id,
		   b.name,
		   b.tier,
		   b.duration_days,
		   b.quantity,
		   b.valid_from,
		   b.valid_until,
		   b.created_by,
		   b.note,
		   b.created_at,
		   COALESCE(SUM(CASE WHEN c.status = 'active' THEN 1 ELSE 0 END),0) AS active_count,
		   COALESCE(SUM(CASE WHEN c.status = 'redeemed' THEN 1 ELSE 0 END),0) AS redeemed_count,
		   COALESCE(SUM(CASE WHEN c.status = 'void' THEN 1 ELSE 0 END),0) AS void_count
		 FROM gift_card_batches b
		 LEFT JOIN gift_cards c ON c.batch_id = b.batch_id
		 GROUP BY b.batch_id, b.name, b.tier, b.duration_days, b.quantity, b.valid_from, b.valid_until, b.created_by, b.note, b.created_at
		 ORDER BY b.created_at DESC
		 LIMIT ?`,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	batches := []AdminGiftCardBatch{}
	for rows.Next() {
		var batch AdminGiftCardBatch
		var tier string
		var validUntil sql.NullInt64
		var note sql.NullString
		if err := rows.Scan(&batch.BatchID, &batch.Name, &tier, &batch.DurationDays, &batch.Quantity, &batch.ValidFrom, &validUntil, &batch.CreatedBy, &note, &batch.CreatedAt, &batch.ActiveCount, &batch.RedeemedCount, &batch.VoidCount); err != nil {
			return nil, err
		}
		batch.Tier = Tier(tier)
		if validUntil.Valid {
			batch.ValidUntil = int64Ptr(validUntil.Int64)
		}
		batch.Note = nullStringValue(note)
		batches = append(batches, batch)
	}
	return batches, rows.Err()
}

func (s *Store) ListGiftCards(ctx context.Context, filter GiftCardListQuery) ([]AdminGiftCardEntry, error) {
	limit := filter.Limit
	if limit <= 0 || limit > maxAdminListLimit {
		limit = defaultAdminListLimit
	}
	codeCiphertextSelect := "NULL AS code_ciphertext"
	if filter.IncludeCode {
		codeCiphertextSelect = "code_ciphertext"
	}
	query := `SELECT card_id, batch_id, code_mask, code_suffix, ` + codeCiphertextSelect + `, tier, duration_days, status, valid_from, valid_until, created_by, note, redeemed_user_id, redeemed_phone_mask, redeemed_region, redeemed_region_source, redeemed_region_reliability, redeemed_at, membership_expire_at, voided_at, created_at, updated_at FROM gift_cards`
	clauses := []string{}
	args := []any{}
	if strings.TrimSpace(filter.BatchID) != "" {
		clauses = append(clauses, "batch_id = ?")
		args = append(args, strings.TrimSpace(filter.BatchID))
	}
	if strings.TrimSpace(filter.Status) != "" {
		clauses = append(clauses, "status = ?")
		args = append(args, strings.TrimSpace(filter.Status))
	}
	if strings.TrimSpace(filter.UserID) != "" {
		clauses = append(clauses, "redeemed_user_id = ?")
		args = append(args, strings.TrimSpace(filter.UserID))
	}
	if strings.TrimSpace(filter.CodeSuffix) != "" {
		clauses = append(clauses, "code_suffix = ?")
		args = append(args, strings.TrimSpace(filter.CodeSuffix))
	}
	if len(clauses) > 0 {
		query += " WHERE " + strings.Join(clauses, " AND ")
	}
	query += " ORDER BY created_at DESC LIMIT ?"
	args = append(args, limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	cards := []AdminGiftCardEntry{}
	for rows.Next() {
		card, err := scanGiftCardEntry(rows, filter.IncludeCode)
		if err != nil {
			return nil, err
		}
		cards = append(cards, card)
	}
	return cards, rows.Err()
}

func (s *Store) ListGiftCardAttempts(ctx context.Context, filter GiftCardAttemptQuery) ([]AdminGiftCardAttempt, error) {
	limit := filter.Limit
	if limit <= 0 || limit > maxAdminListLimit {
		limit = defaultAdminListLimit
	}
	query := `SELECT id, code_suffix, user_id, success, failure_reason, masked_ip, region, region_source, region_reliability, created_at FROM gift_card_redemption_attempts`
	clauses := []string{}
	args := []any{}
	if strings.TrimSpace(filter.UserID) != "" {
		clauses = append(clauses, "user_id = ?")
		args = append(args, strings.TrimSpace(filter.UserID))
	}
	if strings.TrimSpace(filter.CodeSuffix) != "" {
		clauses = append(clauses, "code_suffix = ?")
		args = append(args, strings.TrimSpace(filter.CodeSuffix))
	}
	if filter.SuccessFilter == "success" {
		clauses = append(clauses, "success = 1")
	} else if filter.SuccessFilter == "failed" {
		clauses = append(clauses, "success = 0")
	}
	if strings.TrimSpace(filter.FailureReason) != "" {
		clauses = append(clauses, "failure_reason = ?")
		args = append(args, strings.TrimSpace(filter.FailureReason))
	}
	if len(clauses) > 0 {
		query += " WHERE " + strings.Join(clauses, " AND ")
	}
	query += " ORDER BY created_at DESC, id DESC LIMIT ?"
	args = append(args, limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	attempts := []AdminGiftCardAttempt{}
	for rows.Next() {
		var attempt AdminGiftCardAttempt
		var suffix, userID, failure, maskedIP, region, regionSource, regionReliability sql.NullString
		var success int
		if err := rows.Scan(&attempt.ID, &suffix, &userID, &success, &failure, &maskedIP, &region, &regionSource, &regionReliability, &attempt.CreatedAt); err != nil {
			return nil, err
		}
		attempt.CodeSuffix = nullStringValue(suffix)
		attempt.UserID = nullStringValue(userID)
		attempt.Success = success == 1
		attempt.FailureReason = nullStringValue(failure)
		attempt.MaskedIP = nullStringValue(maskedIP)
		attempt.Region = nullStringValue(region)
		attempt.RegionSource = RegionSource(nullStringValue(regionSource))
		attempt.RegionReliability = RegionReliability(nullStringValue(regionReliability))
		attempts = append(attempts, attempt)
	}
	return attempts, rows.Err()
}

func (s *Store) GetGiftCardSummary(ctx context.Context, nowMs int64) (AdminGiftCardSummary, error) {
	summary := AdminGiftCardSummary{
		FailureReasons: []AdminGiftCardFailureReason{},
	}
	if err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM gift_card_batches").Scan(&summary.BatchCount); err != nil {
		return summary, err
	}
	if err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM gift_cards WHERE status = 'active'").Scan(&summary.ActiveCount); err != nil {
		return summary, err
	}
	if err := s.db.QueryRowContext(
		ctx,
		`SELECT COUNT(*)
		   FROM gift_cards
		  WHERE status = 'active'
		    AND (valid_until IS NULL OR valid_until > ?)`,
		nowMs,
	).Scan(&summary.RedeemableCount); err != nil {
		return summary, err
	}
	if err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM gift_cards WHERE status = 'redeemed'").Scan(&summary.RedeemedCount); err != nil {
		return summary, err
	}
	if err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM gift_cards WHERE status = 'void'").Scan(&summary.VoidCount); err != nil {
		return summary, err
	}
	if err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM gift_card_redemption_attempts WHERE created_at >= ? AND success = 0", nowMs-int64(24*time.Hour/time.Millisecond)).Scan(&summary.FailedAttempts24h); err != nil {
		return summary, err
	}
	reasons, err := s.ListGiftCardFailureReasons(ctx, nowMs-int64(7*24*time.Hour/time.Millisecond), 8)
	if err != nil {
		return summary, err
	}
	if reasons == nil {
		reasons = []AdminGiftCardFailureReason{}
	}
	summary.FailureReasons = reasons
	return summary, nil
}

func (s *Store) ListGiftCardFailureReasons(ctx context.Context, sinceMs int64, limit int) ([]AdminGiftCardFailureReason, error) {
	if limit <= 0 || limit > 20 {
		limit = 8
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT COALESCE(NULLIF(failure_reason, ''), 'unknown') AS reason, COUNT(*) AS count_value
		   FROM gift_card_redemption_attempts
		  WHERE created_at >= ? AND success = 0
		  GROUP BY reason
		  ORDER BY count_value DESC, reason ASC
		  LIMIT ?`,
		sinceMs,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	entries := []AdminGiftCardFailureReason{}
	for rows.Next() {
		var entry AdminGiftCardFailureReason
		if err := rows.Scan(&entry.Reason, &entry.Count); err != nil {
			return nil, err
		}
		entries = append(entries, entry)
	}
	return entries, rows.Err()
}

func (s *Store) VoidGiftCard(ctx context.Context, cardID string, nowMs int64) error {
	result, err := s.db.ExecContext(
		ctx,
		`UPDATE gift_cards
		    SET status = 'void',
		        voided_at = ?,
		        updated_at = ?
		  WHERE card_id = ? AND status = 'active'`,
		nowMs,
		nowMs,
		strings.TrimSpace(cardID),
	)
	if err != nil {
		return err
	}
	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if rowsAffected == 0 {
		return errGiftCardInactive
	}
	return nil
}

func (s *Store) RedeemGiftCard(ctx context.Context, rawCode string, userID string, region RegionContext, maskedIP string, nowMs int64) (GiftCardRedeemResult, error) {
	code := normalizeGiftCardCode(rawCode)
	codeSuffix := giftCardCodeSuffix(code)
	if code == "" {
		tx, err := s.db.BeginTx(ctx, nil)
		if err != nil {
			return GiftCardRedeemResult{}, err
		}
		defer rollbackQuietly(tx)
		if err := insertGiftCardAttempt(ctx, tx, codeSuffix, userID, false, "invalid_code", maskedIP, region, nowMs); err != nil {
			return GiftCardRedeemResult{}, err
		}
		if err := tx.Commit(); err != nil {
			return GiftCardRedeemResult{}, err
		}
		return GiftCardRedeemResult{}, errGiftCardInvalidCode
	}
	codeHash := giftCardCodeHash(code)
	if codeHash == "" {
		return GiftCardRedeemResult{}, errGiftCardSecretMissing
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return GiftCardRedeemResult{}, err
	}
	defer rollbackQuietly(tx)

	card, err := getGiftCardForUpdate(ctx, tx, codeHash)
	if err != nil {
		if err == sql.ErrNoRows {
			if err := insertGiftCardAttempt(ctx, tx, codeSuffix, userID, false, "not_found", maskedIP, region, nowMs); err != nil {
				return GiftCardRedeemResult{}, err
			}
			if err := tx.Commit(); err != nil {
				return GiftCardRedeemResult{}, err
			}
			return GiftCardRedeemResult{}, errGiftCardNotFound
		}
		return GiftCardRedeemResult{}, err
	}
	if card.Status != "active" {
		if card.Status == "redeemed" && card.RedeemedUserID == userID && card.RedeemedAt != nil && card.MembershipExpireAt != nil {
			if err := insertGiftCardAttempt(ctx, tx, codeSuffix, userID, true, "", maskedIP, region, nowMs); err != nil {
				return GiftCardRedeemResult{}, err
			}
			if err := tx.Commit(); err != nil {
				return GiftCardRedeemResult{}, err
			}
			return GiftCardRedeemResult{
				OK:                 true,
				Replay:             true,
				CardID:             card.CardID,
				BatchID:            card.BatchID,
				Tier:               card.Tier,
				AppliedTier:        card.Tier,
				DurationDays:       card.DurationDays,
				MembershipExpireAt: *card.MembershipExpireAt,
				RedeemedAt:         *card.RedeemedAt,
				Region:             card.RedeemedRegion,
				RegionSource:       card.RedeemedRegionSource,
				RegionReliability:  card.RedeemedRegionReliability,
			}, nil
		}
		if err := insertGiftCardAttempt(ctx, tx, codeSuffix, userID, false, card.Status, maskedIP, region, nowMs); err != nil {
			return GiftCardRedeemResult{}, err
		}
		if err := tx.Commit(); err != nil {
			return GiftCardRedeemResult{}, err
		}
		return GiftCardRedeemResult{}, errGiftCardInactive
	}
	if card.ValidUntil != nil && *card.ValidUntil <= nowMs {
		if err := insertGiftCardAttempt(ctx, tx, codeSuffix, userID, false, "expired", maskedIP, region, nowMs); err != nil {
			return GiftCardRedeemResult{}, err
		}
		if err := tx.Commit(); err != nil {
			return GiftCardRedeemResult{}, err
		}
		return GiftCardRedeemResult{}, errGiftCardExpired
	}

	phoneMask := ""
	_ = tx.QueryRowContext(ctx, "SELECT phone_mask FROM app_accounts WHERE user_id = ? LIMIT 1", userID).Scan(&phoneMask)
	appliedTier, expireAt, err := s.applyGiftCardTierTx(ctx, tx, userID, card.Tier, card.DurationDays, nowMs)
	if err != nil {
		if errors.Is(err, errGiftCardLowerTier) {
			if attemptErr := insertGiftCardAttempt(ctx, tx, codeSuffix, userID, false, "lower_tier", maskedIP, region, nowMs); attemptErr != nil {
				return GiftCardRedeemResult{}, attemptErr
			}
			if commitErr := tx.Commit(); commitErr != nil {
				return GiftCardRedeemResult{}, commitErr
			}
		}
		return GiftCardRedeemResult{}, err
	}
	updateResult, err := tx.ExecContext(
		ctx,
		`UPDATE gift_cards
		    SET status = 'redeemed',
		        redeemed_user_id = ?,
		        redeemed_phone_mask = ?,
		        redeemed_region = ?,
		        redeemed_region_source = ?,
		        redeemed_region_reliability = ?,
		        redeemed_at = ?,
		        membership_expire_at = ?,
		        updated_at = ?
		  WHERE card_id = ? AND status = 'active'`,
		userID,
		nullableTrimmed(phoneMask),
		nullableTrimmed(region.Region),
		nullableTrimmed(string(region.Source)),
		nullableTrimmed(string(region.Reliability)),
		nowMs,
		expireAt,
		nowMs,
		card.CardID,
	)
	if err != nil {
		return GiftCardRedeemResult{}, err
	}
	rowsAffected, err := updateResult.RowsAffected()
	if err != nil {
		return GiftCardRedeemResult{}, err
	}
	if rowsAffected != 1 {
		return GiftCardRedeemResult{}, errGiftCardInactive
	}
	if err := insertGiftCardAttempt(ctx, tx, codeSuffix, userID, true, "", maskedIP, region, nowMs); err != nil {
		return GiftCardRedeemResult{}, err
	}
	if err := tx.Commit(); err != nil {
		return GiftCardRedeemResult{}, err
	}
	return GiftCardRedeemResult{
		OK:                 true,
		CardID:             card.CardID,
		BatchID:            card.BatchID,
		Tier:               card.Tier,
		AppliedTier:        appliedTier,
		DurationDays:       card.DurationDays,
		MembershipExpireAt: expireAt,
		RedeemedAt:         nowMs,
		Region:             region.Region,
		RegionSource:       region.Source,
		RegionReliability:  region.Reliability,
	}, nil
}

func normalizeGiftCardBatchInput(body adminGiftCardCreateBatchRequest, actor string, now time.Time) (GiftCardBatchInput, string) {
	tier := Tier(strings.ToLower(strings.TrimSpace(body.Tier)))
	if tier != TierPlus && tier != TierPro {
		return GiftCardBatchInput{}, "invalid_tier"
	}
	durationDays := body.DurationDays
	if durationDays <= 0 {
		durationDays = defaultGiftCardDurationDays
	}
	if durationDays > 366 {
		return GiftCardBatchInput{}, "invalid_duration_days"
	}
	quantity := body.Quantity
	if quantity <= 0 {
		return GiftCardBatchInput{}, "invalid_quantity"
	}
	if quantity > maxGiftCardBatchQuantity {
		return GiftCardBatchInput{}, "quantity_too_large"
	}
	validFrom := now.UnixMilli()
	validUntil := body.ValidUntil
	if validUntil == nil {
		value := now.AddDate(0, 0, defaultGiftCardValidDays).UnixMilli()
		validUntil = &value
	}
	if validUntil != nil && *validUntil <= validFrom {
		return GiftCardBatchInput{}, "invalid_valid_until"
	}
	note := truncateRunes(strings.TrimSpace(body.Note), 255)
	return GiftCardBatchInput{
		Name:         truncateRunes(strings.TrimSpace(body.Name), 128),
		Tier:         tier,
		DurationDays: durationDays,
		Quantity:     quantity,
		ValidFrom:    validFrom,
		ValidUntil:   validUntil,
		CreatedBy:    normalizeAdminActor(actor),
		Note:         note,
	}, ""
}

func getGiftCardForUpdate(ctx context.Context, tx *sql.Tx, codeHash string) (AdminGiftCardEntry, error) {
	row := tx.QueryRowContext(
		ctx,
		`SELECT card_id, batch_id, code_mask, code_suffix, NULL AS code_ciphertext, tier, duration_days, status, valid_from, valid_until, created_by, note, redeemed_user_id, redeemed_phone_mask, redeemed_region, redeemed_region_source, redeemed_region_reliability, redeemed_at, membership_expire_at, voided_at, created_at, updated_at
		   FROM gift_cards
		  WHERE code_hash = ?
		  LIMIT 1
		  FOR UPDATE`,
		codeHash,
	)
	return scanGiftCardEntry(row, false)
}

func (s *Store) applyGiftCardTierTx(ctx context.Context, tx *sql.Tx, userID string, cardTier Tier, durationDays int, nowMs int64) (Tier, int64, error) {
	var tierRaw sql.NullString
	var expireRaw sql.NullInt64
	err := tx.QueryRowContext(ctx, "SELECT tier, tier_expire_at FROM user_entitlement WHERE user_id = ? LIMIT 1 FOR UPDATE", userID).Scan(&tierRaw, &expireRaw)
	if err != nil && err != sql.ErrNoRows {
		return "", 0, err
	}
	currentTier, currentExpire, err := effectiveTierFromRow(tierRaw, expireRaw, TierFree, nowMs)
	if err != nil {
		return "", 0, err
	}
	appliedTier := cardTier
	if tierRank(currentTier) > tierRank(cardTier) {
		return "", 0, errGiftCardLowerTier
	}
	base := nowMs
	if currentExpire != nil && *currentExpire > base && currentTier == cardTier {
		base = *currentExpire
	}
	newExpire := time.UnixMilli(base).AddDate(0, 0, durationDays).UnixMilli()
	if currentTier == TierPlus && cardTier == TierPro {
		dayCN := GetTodayKeyCN(s.shanghai, time.UnixMilli(nowMs))
		usedToday, err := s.getOrCreateDailyUsage(ctx, tx, userID, dayCN)
		if err != nil {
			return "", 0, err
		}
		todayRemainingPlus := maxInt(0, tierLimits[TierPlus]-usedToday)
		expireAtOld := nowMs
		if currentExpire != nil {
			expireAtOld = *currentExpire
		}
		remainingFullDays := maxInt(0, dayIndexFromTsCN(s.shanghai, expireAtOld)-dayIndexFromTsCN(s.shanghai, nowMs))
		compensation := todayRemainingPlus + remainingFullDays*tierLimits[TierPlus]
		if _, err := tx.ExecContext(
			ctx,
			`INSERT INTO upgrade_credits(user_id, remaining, expire_at, updated_at)
			 VALUES (?, ?, ?, ?)
			 ON DUPLICATE KEY UPDATE
			   remaining = remaining + VALUES(remaining),
			   expire_at = NULL,
			   updated_at = VALUES(updated_at)`,
			userID,
			compensation,
			nil,
			nowMs,
		); err != nil {
			return "", 0, err
		}
	}
	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO user_entitlement(user_id, tier, tier_expire_at, updated_at)
		 VALUES (?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE tier = VALUES(tier), tier_expire_at = VALUES(tier_expire_at), updated_at = VALUES(updated_at)`,
		userID,
		string(appliedTier),
		newExpire,
		nowMs,
	); err != nil {
		return "", 0, err
	}
	return appliedTier, newExpire, nil
}

func (s *Store) GetCurrentGiftCardMembership(ctx context.Context, userID string, tier Tier, membershipExpireAt *int64) (bool, *int64, error) {
	if userID == "" || membershipExpireAt == nil || *membershipExpireAt <= 0 {
		return false, nil, nil
	}
	if tier != TierPlus && tier != TierPro {
		return false, nil, nil
	}
	var redeemedAt sql.NullInt64
	err := s.db.QueryRowContext(
		ctx,
		`SELECT redeemed_at
		   FROM gift_cards
		  WHERE redeemed_user_id = ?
		    AND status = 'redeemed'
		    AND tier = ?
		    AND membership_expire_at = ?
		  ORDER BY redeemed_at DESC
		  LIMIT 1`,
		userID,
		string(tier),
		*membershipExpireAt,
	).Scan(&redeemedAt)
	if err == sql.ErrNoRows {
		return false, nil, nil
	}
	if err != nil {
		return false, nil, err
	}
	return true, nullInt64ToPtr(redeemedAt), nil
}

func insertGiftCardAttempt(ctx context.Context, tx *sql.Tx, codeSuffix string, userID string, success bool, failureReason string, maskedIP string, region RegionContext, createdAt int64) error {
	successInt := 0
	if success {
		successInt = 1
	}
	_, err := tx.ExecContext(
		ctx,
		`INSERT INTO gift_card_redemption_attempts(code_suffix, user_id, success, failure_reason, masked_ip, region, region_source, region_reliability, created_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		nullableTrimmed(codeSuffix),
		nullableTrimmed(userID),
		successInt,
		nullableTrimmed(failureReason),
		nullableTrimmed(maskedIP),
		nullableTrimmed(region.Region),
		nullableTrimmed(string(region.Source)),
		nullableTrimmed(string(region.Reliability)),
		createdAt,
	)
	return err
}

type giftCardScanner interface {
	Scan(dest ...any) error
}

func scanGiftCardEntry(scanner giftCardScanner, includeCode bool) (AdminGiftCardEntry, error) {
	var card AdminGiftCardEntry
	var tier string
	var validUntil, redeemedAt, membershipExpireAt, voidedAt sql.NullInt64
	var codeCiphertext, note, redeemedUser, redeemedPhone, region, regionSource, regionReliability sql.NullString
	if err := scanner.Scan(
		&card.CardID,
		&card.BatchID,
		&card.CodeMask,
		&card.CodeSuffix,
		&codeCiphertext,
		&tier,
		&card.DurationDays,
		&card.Status,
		&card.ValidFrom,
		&validUntil,
		&card.CreatedBy,
		&note,
		&redeemedUser,
		&redeemedPhone,
		&region,
		&regionSource,
		&regionReliability,
		&redeemedAt,
		&membershipExpireAt,
		&voidedAt,
		&card.CreatedAt,
		&card.UpdatedAt,
	); err != nil {
		return card, err
	}
	card.Tier = Tier(tier)
	if includeCode && codeCiphertext.Valid && strings.TrimSpace(codeCiphertext.String) != "" {
		code, err := decryptGiftCardCode(codeCiphertext.String)
		if err == nil {
			card.Code = code
		}
	}
	card.Note = nullStringValue(note)
	card.RedeemedUserID = nullStringValue(redeemedUser)
	card.RedeemedPhoneMask = nullStringValue(redeemedPhone)
	card.RedeemedRegion = nullStringValue(region)
	card.RedeemedRegionSource = RegionSource(nullStringValue(regionSource))
	card.RedeemedRegionReliability = RegionReliability(nullStringValue(regionReliability))
	if validUntil.Valid {
		card.ValidUntil = int64Ptr(validUntil.Int64)
	}
	if redeemedAt.Valid {
		card.RedeemedAt = int64Ptr(redeemedAt.Int64)
	}
	if membershipExpireAt.Valid {
		card.MembershipExpireAt = int64Ptr(membershipExpireAt.Int64)
	}
	if voidedAt.Valid {
		card.VoidedAt = int64Ptr(voidedAt.Int64)
	}
	return card, nil
}

func giftCardRegionFromRequest(r *http.Request) RegionContext {
	if region := ParseRegionFromHeaders(r.Header); region != nil {
		return *region
	}
	return ResolveRegionByIP(GetClientIP(r))
}

func giftCardErrorCode(err error) string {
	switch {
	case errors.Is(err, errGiftCardNotFound):
		return "gift_card_not_found"
	case errors.Is(err, errGiftCardInactive):
		return "gift_card_inactive"
	case errors.Is(err, errGiftCardExpired):
		return "gift_card_expired"
	case errors.Is(err, errGiftCardLowerTier):
		return "gift_card_lower_tier"
	case errors.Is(err, errGiftCardInvalidCode):
		return "gift_card_invalid_code"
	case errors.Is(err, errGiftCardSecretMissing):
		return "gift_card_not_configured"
	default:
		return "gift_card_redeem_failed"
	}
}

func giftCardRedeemHTTPStatus(err error) int {
	switch {
	case errors.Is(err, errGiftCardNotFound),
		errors.Is(err, errGiftCardInactive),
		errors.Is(err, errGiftCardExpired),
		errors.Is(err, errGiftCardLowerTier),
		errors.Is(err, errGiftCardInvalidCode):
		return http.StatusBadRequest
	case errors.Is(err, errGiftCardSecretMissing):
		return http.StatusServiceUnavailable
	default:
		return http.StatusInternalServerError
	}
}

func adminGiftCardBatchConfirmationError(body adminGiftCardCreateBatchRequest, normalizedQuantity int, normalizedTier Tier, normalizedDurationDays int) string {
	confirmation := strings.Join(strings.Fields(strings.TrimSpace(body.Confirmation)), " ")
	if normalizedQuantity <= 0 || normalizedTier == "" || normalizedDurationDays <= 0 {
		return "gift_card_batch_confirmation_required"
	}
	tierText := strings.ToLower(string(normalizedTier))
	if tierText != "" {
		tierText = strings.ToUpper(tierText[:1]) + tierText[1:]
	}
	expected := fmt.Sprintf("%d %s %d", normalizedQuantity, tierText, normalizedDurationDays)
	if !strings.EqualFold(confirmation, expected) {
		return "gift_card_batch_confirmation_required"
	}
	return ""
}

func adminGiftCardVoidConfirmationError(body adminGiftCardVoidRequest) string {
	if strings.TrimSpace(body.Confirmation) != "作废" {
		return "gift_card_void_confirmation_required"
	}
	return ""
}

func (s *Server) recordAdminGiftCardBatchValidationFailure(r *http.Request, actor string, body adminGiftCardCreateBatchRequest, code string) {
	s.recordAdminAuditLog(r, actor, "admin.gift_cards.create_batch", "gift_cards", "", "", false, http.StatusBadRequest, map[string]any{
		"error_code":    code,
		"tier":          strings.ToLower(strings.TrimSpace(body.Tier)),
		"quantity":      body.Quantity,
		"duration_days": body.DurationDays,
		"has_note":      strings.TrimSpace(body.Note) != "",
	})
}

func (s *Server) recordAdminGiftCardVoidValidationFailure(r *http.Request, actor string, body adminGiftCardVoidRequest, code string) {
	cardID := strings.TrimSpace(body.CardID)
	reason := strings.TrimSpace(body.Reason)
	s.recordAdminAuditLog(r, actor, "admin.gift_cards.void", "gift_cards", cardID, "", false, http.StatusBadRequest, map[string]any{
		"error_code":            code,
		"has_card_id":           cardID != "",
		"has_reason":            reason != "",
		"reason_length":         len([]rune(reason)),
		"reason_sensitive_like": giftCardTextLooksSensitive(reason),
	})
}

func normalizeGiftCardStatus(raw string) string {
	status := strings.ToLower(strings.TrimSpace(raw))
	switch status {
	case "active", "redeemed", "void":
		return status
	default:
		return ""
	}
}

func normalizeGiftCardAttemptSuccess(raw string) string {
	value := strings.ToLower(strings.TrimSpace(raw))
	switch value {
	case "success", "ok", "true", "1":
		return "success"
	case "failed", "fail", "false", "0":
		return "failed"
	default:
		return ""
	}
}

func normalizeGiftCardCodeSuffix(raw string) string {
	code := normalizeGiftCardCode(raw)
	if len(code) > 4 {
		code = code[len(code)-4:]
	}
	return code
}

func normalizeGiftCardCode(raw string) string {
	var builder strings.Builder
	for _, ch := range strings.ToUpper(strings.TrimSpace(raw)) {
		if (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') {
			builder.WriteRune(ch)
		}
	}
	return builder.String()
}

func giftCardCodeHash(code string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	if secret == "" {
		return ""
	}
	normalized := normalizeGiftCardCode(code)
	if normalized == "" {
		return ""
	}
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(normalized))
	return hex.EncodeToString(mac.Sum(nil))
}

func encryptGiftCardCode(code string) (string, error) {
	return encryptGiftCardCodeWithSecret(code, os.Getenv("APP_SECRET"))
}

func encryptGiftCardCodeWithSecret(code string, secret string) (string, error) {
	code = strings.TrimSpace(code)
	secret = strings.TrimSpace(secret)
	if code == "" {
		return "", errGiftCardInvalidCode
	}
	if secret == "" {
		return "", errGiftCardSecretMissing
	}
	key := giftCardCodeCipherKey(secret)
	block, err := aes.NewCipher(key[:])
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", err
	}
	sealed := gcm.Seal(nil, nonce, []byte(code), []byte("gift_card_code:v1"))
	payload := append(nonce, sealed...)
	return "v1:" + base64.RawURLEncoding.EncodeToString(payload), nil
}

func decryptGiftCardCode(ciphertext string) (string, error) {
	return decryptGiftCardCodeWithSecret(ciphertext, os.Getenv("APP_SECRET"))
}

func decryptGiftCardCodeWithSecret(ciphertext string, secret string) (string, error) {
	ciphertext = strings.TrimSpace(ciphertext)
	secret = strings.TrimSpace(secret)
	if ciphertext == "" {
		return "", nil
	}
	if secret == "" {
		return "", errGiftCardSecretMissing
	}
	if !strings.HasPrefix(ciphertext, "v1:") {
		return "", fmt.Errorf("unsupported gift card code ciphertext")
	}
	raw, err := base64.RawURLEncoding.DecodeString(strings.TrimPrefix(ciphertext, "v1:"))
	if err != nil {
		return "", err
	}
	key := giftCardCodeCipherKey(secret)
	block, err := aes.NewCipher(key[:])
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	if len(raw) <= gcm.NonceSize() {
		return "", fmt.Errorf("invalid gift card code ciphertext")
	}
	nonce := raw[:gcm.NonceSize()]
	sealed := raw[gcm.NonceSize():]
	plain, err := gcm.Open(nil, nonce, sealed, []byte("gift_card_code:v1"))
	if err != nil {
		return "", err
	}
	return string(plain), nil
}

func giftCardCodeCipherKey(secret string) [32]byte {
	return sha256.Sum256([]byte("nongjiqiancha:gift_card_code:v1:" + strings.TrimSpace(secret)))
}

func giftCardCodeMask(code string) string {
	normalized := normalizeGiftCardCode(code)
	if len(normalized) <= 6 {
		return "****"
	}
	return normalized[:2] + "-****-****-" + normalized[len(normalized)-4:]
}

func giftCardCodeSuffix(code string) string {
	normalized := normalizeGiftCardCode(code)
	if len(normalized) <= 4 {
		return normalized
	}
	return normalized[len(normalized)-4:]
}

func generateGiftCardCode() (string, error) {
	parts := make([]string, 0, 3)
	for i := 0; i < 3; i++ {
		part, err := randomGiftCardPart(4)
		if err != nil {
			return "", err
		}
		parts = append(parts, part)
	}
	return "NQ-" + strings.Join(parts, "-"), nil
}

func randomGiftCardPart(length int) (string, error) {
	var builder strings.Builder
	max := big.NewInt(int64(len(giftCardCodeAlphabet)))
	for i := 0; i < length; i++ {
		n, err := rand.Int(rand.Reader, max)
		if err != nil {
			return "", err
		}
		builder.WriteByte(giftCardCodeAlphabet[n.Int64()])
	}
	return builder.String(), nil
}

func randomGiftCardID(prefix string) (string, error) {
	token, err := randomHexToken(8)
	if err != nil {
		return "", err
	}
	return strings.TrimSpace(prefix) + "_" + token, nil
}

func adminCanViewGiftCardCodes(role string) bool {
	return adminRoleAllowed(role, "finance_ops")
}

func stripGiftCardCodes(cards []AdminGiftCardEntry) {
	for idx := range cards {
		cards[idx].Code = ""
	}
}

func giftCardTextLooksSensitive(text string) bool {
	trimmed := strings.TrimSpace(text)
	if trimmed == "" {
		return false
	}
	lower := strings.ToLower(trimmed)
	for _, marker := range []string{"accesskey", "access key", "secret", "token", "api key", "apikey", "密码", "密钥"} {
		if strings.Contains(lower, marker) {
			return true
		}
	}
	if textContainsSeparatedMainlandPhone(trimmed) {
		return true
	}
	digitRun := 0
	for _, ch := range trimmed {
		if ch >= '0' && ch <= '9' {
			digitRun++
			if digitRun >= 11 {
				return true
			}
			continue
		}
		digitRun = 0
	}
	upper := strings.ToUpper(trimmed)
	normalized := normalizeGiftCardCode(trimmed)
	if strings.Contains(upper, "NQ") && len(normalized) >= 10 {
		return true
	}
	if len(normalized) >= 12 {
		matchChars := 0
		for _, ch := range normalized {
			if strings.ContainsRune(giftCardCodeAlphabet, ch) {
				matchChars++
			}
		}
		if matchChars >= 10 {
			return true
		}
	}
	return false
}

func textContainsSeparatedMainlandPhone(text string) bool {
	digits := make([]rune, 0, len(text))
	check := func() bool {
		for i := 0; i+11 <= len(digits); i++ {
			if digits[i] == '1' && digits[i+1] >= '3' && digits[i+1] <= '9' {
				return true
			}
		}
		return false
	}
	for _, ch := range text {
		if ch >= '0' && ch <= '9' {
			digits = append(digits, ch)
			continue
		}
		if ch == ' ' || ch == '-' || ch == '_' || ch == '(' || ch == ')' || ch == '（' || ch == '）' || ch == '.' {
			continue
		}
		if check() {
			return true
		}
		digits = digits[:0]
	}
	return check()
}

func tierRank(tier Tier) int {
	switch tier {
	case TierPro:
		return 3
	case TierPlus:
		return 2
	default:
		return 1
	}
}

func nullableInt64(value *int64) any {
	if value == nil {
		return nil
	}
	return *value
}

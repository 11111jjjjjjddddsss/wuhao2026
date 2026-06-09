package app

import (
	"context"
	"database/sql"
	"errors"
	"net/http"
	"strings"
	"time"
)

const accountDeletionDefaultReason = "app_request"

var errAccountDeletionNotFound = errors.New("account_deletion_request_not_found")
var errAccountDeletionInvalidTransition = errors.New("account_deletion_invalid_status_transition")
var errAccountDeletionBusy = errors.New("account_deletion_request_busy")

type AccountDeletionRequest struct {
	RequestID   string `json:"request_id"`
	UserID      string `json:"user_id"`
	PhoneMask   string `json:"phone_mask,omitempty"`
	Status      string `json:"status"`
	Reason      string `json:"reason,omitempty"`
	UserMessage string `json:"user_message,omitempty"`
	HandledBy   string `json:"handled_by,omitempty"`
	HandlerNote string `json:"handler_note,omitempty"`
	HandledAt   *int64 `json:"handled_at,omitempty"`
	CreatedAt   int64  `json:"created_at"`
	UpdatedAt   int64  `json:"updated_at"`
}

type AccountDeletionQuery struct {
	UserID string `json:"user_id,omitempty"`
	Status string `json:"status,omitempty"`
	Limit  int    `json:"limit"`
}

type accountDeletionRequestBody struct {
	Reason  string `json:"reason"`
	Message string `json:"message"`
}

type adminAccountDeletionStatusBody struct {
	RequestID string `json:"request_id"`
	Status    string `json:"status"`
	Note      string `json:"note"`
}

func (s *Server) handleCreateAccountDeletionRequest(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		s.writeError(w, http.StatusMethodNotAllowed, "method_not_allowed")
		return
	}
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	var body accountDeletionRequestBody
	if err := decodeJSONBodyLimited(r, &body, 4*1024); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	reason := normalizeAccountDeletionReason(body.Reason)
	if reason == "" {
		reason = accountDeletionDefaultReason
	}
	message, validationError := normalizeAccountDeletionFreeText(body.Message, "message")
	if validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	request, created, err := s.store.CreateAccountDeletionRequest(r.Context(), auth.UserID, reason, message, time.Now().UnixMilli())
	if err != nil {
		if errors.Is(err, errAccountDeletionBusy) {
			s.writeError(w, http.StatusConflict, "account_deletion_request_busy")
			return
		}
		s.logger.Error("create account deletion request failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if auth.AuthMode == AuthModeToken && strings.TrimSpace(auth.SessionID) != "" {
		if err := s.store.RevokeAuthSession(r.Context(), auth.UserID, auth.SessionID, time.Now().UnixMilli()); err != nil {
			s.logger.Warn("revoke session after account deletion request failed", "userId", auth.UserID, "error", err)
		}
	}
	s.recordAdminAuditLog(r, "app_user:"+auth.UserID, "account_deletion.request", "account_deletion_requests", request.RequestID, auth.UserID, true, http.StatusOK, map[string]any{
		"created": created,
		"status":  request.Status,
	})
	s.writeJSON(w, http.StatusOK, map[string]any{
		"ok":      true,
		"created": created,
		"request": request,
	})
}

func (s *Server) handleAdminAccountDeletionRequests(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "support", "ops_readonly", "auditor", "finance_ops")
	if !ok {
		return
	}
	filter := AccountDeletionQuery{
		UserID: normalizeUserID(r.URL.Query().Get("user_id")),
		Status: normalizeAccountDeletionStatus(r.URL.Query().Get("status")),
		Limit:  parseAdminLimit(r.URL.Query().Get("limit")),
	}
	requests, err := s.store.ListAccountDeletionRequests(r.Context(), filter)
	if err != nil {
		s.logger.Error("admin list account deletion requests failed", "error", err)
		s.recordAdminAuditLog(r, admin.User.Username, "admin.account_deletion.list", "account_deletion_requests", "", filter.UserID, false, http.StatusInternalServerError, map[string]any{"error_code": "internal_error"})
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.account_deletion.list", "account_deletion_requests", "", filter.UserID, true, http.StatusOK, map[string]any{"row_count": len(requests), "status": filter.Status})
	s.writeJSON(w, http.StatusOK, map[string]any{"requests": requests, "filter": filter})
}

func (s *Server) handleAdminUpdateAccountDeletionStatus(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r, "support")
	if !ok {
		return
	}
	var body adminAccountDeletionStatusBody
	if err := decodeJSONBodyLimited(r, &body, 4*1024); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}
	requestID := strings.TrimSpace(body.RequestID)
	status := normalizeAccountDeletionStatus(body.Status)
	if requestID == "" {
		s.writeError(w, http.StatusBadRequest, "request_id_required")
		return
	}
	if status == "" {
		s.writeError(w, http.StatusBadRequest, "invalid_status")
		return
	}
	note, validationError := normalizeAccountDeletionFreeText(body.Note, "note")
	if validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	request, err := s.store.UpdateAccountDeletionRequestStatus(r.Context(), requestID, status, admin.User.Username, note, time.Now().UnixMilli())
	if err != nil {
		statusCode := http.StatusInternalServerError
		code := "internal_error"
		if errors.Is(err, errAccountDeletionNotFound) {
			statusCode = http.StatusNotFound
			code = "account_deletion_request_not_found"
		} else if errors.Is(err, errAccountDeletionInvalidTransition) {
			statusCode = http.StatusConflict
			code = "account_deletion_invalid_status_transition"
		}
		s.recordAdminAuditLog(r, admin.User.Username, "admin.account_deletion.status", "account_deletion_requests", requestID, "", false, statusCode, map[string]any{"error_code": code, "status": status})
		s.writeError(w, statusCode, code)
		return
	}
	s.recordAdminAuditLog(r, admin.User.Username, "admin.account_deletion.status", "account_deletion_requests", request.RequestID, request.UserID, true, http.StatusOK, map[string]any{"status": request.Status})
	s.writeJSON(w, http.StatusOK, map[string]any{"ok": true, "request": request})
}

func (s *Store) CreateAccountDeletionRequest(ctx context.Context, userID string, reason string, message string, nowMs int64) (AccountDeletionRequest, bool, error) {
	userID = normalizeUserID(userID)
	if userID == "" {
		return AccountDeletionRequest{}, false, sql.ErrNoRows
	}
	conn, err := s.db.Conn(ctx)
	if err != nil {
		return AccountDeletionRequest{}, false, err
	}
	defer conn.Close()

	lockName := accountDeletionLockName(userID)
	var acquired sql.NullInt64
	if err := conn.QueryRowContext(ctx, "SELECT GET_LOCK(?, 5)", lockName).Scan(&acquired); err != nil {
		return AccountDeletionRequest{}, false, err
	}
	if !acquired.Valid || acquired.Int64 != 1 {
		return AccountDeletionRequest{}, false, errAccountDeletionBusy
	}
	defer func() {
		_, _ = conn.ExecContext(context.Background(), "SELECT RELEASE_LOCK(?)", lockName)
	}()

	return s.createAccountDeletionRequestLocked(ctx, conn, userID, reason, message, nowMs)
}

func (s *Store) createAccountDeletionRequestLocked(ctx context.Context, conn *sql.Conn, userID string, reason string, message string, nowMs int64) (AccountDeletionRequest, bool, error) {
	existing, err := getOpenAccountDeletionRequestQuery(ctx, conn, userID)
	if err == nil {
		return existing, false, nil
	}
	if err != sql.ErrNoRows {
		return AccountDeletionRequest{}, false, err
	}

	requestID, err := randomHexToken(12)
	if err != nil {
		return AccountDeletionRequest{}, false, err
	}
	requestID = "adr_" + requestID
	var phoneMask sql.NullString
	if err := conn.QueryRowContext(ctx, "SELECT phone_mask FROM app_accounts WHERE user_id = ? LIMIT 1", userID).Scan(&phoneMask); err != nil && err != sql.ErrNoRows {
		return AccountDeletionRequest{}, false, err
	}
	request := AccountDeletionRequest{
		RequestID:   requestID,
		UserID:      userID,
		PhoneMask:   nullStringValue(phoneMask),
		Status:      "pending",
		Reason:      reason,
		UserMessage: message,
		CreatedAt:   nowMs,
		UpdatedAt:   nowMs,
	}
	_, err = conn.ExecContext(
		ctx,
		`INSERT INTO account_deletion_requests(request_id, user_id, phone_mask, status, reason, user_message, created_at, updated_at)
		 VALUES (?, ?, ?, 'pending', ?, ?, ?, ?)`,
		request.RequestID,
		request.UserID,
		nullableTrimmed(request.PhoneMask),
		nullableTrimmed(request.Reason),
		nullableTrimmed(request.UserMessage),
		nowMs,
		nowMs,
	)
	if err != nil {
		return AccountDeletionRequest{}, false, err
	}
	return request, true, nil
}

func (s *Store) getOpenAccountDeletionRequest(ctx context.Context, userID string) (AccountDeletionRequest, error) {
	return getOpenAccountDeletionRequestQuery(ctx, s.db, userID)
}

type accountDeletionQueryer interface {
	QueryRowContext(ctx context.Context, query string, args ...any) *sql.Row
}

func getOpenAccountDeletionRequestQuery(ctx context.Context, q accountDeletionQueryer, userID string) (AccountDeletionRequest, error) {
	row := q.QueryRowContext(
		ctx,
		`SELECT request_id, user_id, phone_mask, status, reason, user_message, handled_by, handler_note, handled_at, created_at, updated_at
		   FROM account_deletion_requests
		  WHERE user_id = ? AND status IN ('pending','processing')
		  ORDER BY created_at DESC
		  LIMIT 1`,
		userID,
	)
	return scanAccountDeletionRequest(row)
}

func (s *Store) ListAccountDeletionRequests(ctx context.Context, filter AccountDeletionQuery) ([]AccountDeletionRequest, error) {
	if filter.Limit <= 0 || filter.Limit > maxAdminListLimit {
		filter.Limit = defaultAdminListLimit
	}
	query := `SELECT request_id, user_id, phone_mask, status, reason, user_message, handled_by, handler_note, handled_at, created_at, updated_at
	   FROM account_deletion_requests`
	args := []any{}
	where := []string{}
	if filter.UserID != "" {
		where = append(where, "user_id = ?")
		args = append(args, filter.UserID)
	}
	if filter.Status != "" {
		where = append(where, "status = ?")
		args = append(args, filter.Status)
	}
	if len(where) > 0 {
		query += " WHERE " + strings.Join(where, " AND ")
	}
	query += " ORDER BY FIELD(status, 'pending', 'processing', 'rejected', 'cancelled', 'completed'), created_at DESC LIMIT ?"
	args = append(args, filter.Limit)
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	requests := []AccountDeletionRequest{}
	for rows.Next() {
		request, err := scanAccountDeletionRequest(rows)
		if err != nil {
			return nil, err
		}
		requests = append(requests, request)
	}
	return requests, rows.Err()
}

func (s *Store) UpdateAccountDeletionRequestStatus(ctx context.Context, requestID string, status string, actor string, note string, nowMs int64) (AccountDeletionRequest, error) {
	requestID = strings.TrimSpace(requestID)
	status = normalizeAccountDeletionStatus(status)
	if requestID == "" || status == "" {
		return AccountDeletionRequest{}, errAccountDeletionNotFound
	}
	handledAt := any(nil)
	handledBy := any(nil)
	if status == "cancelled" || status == "rejected" || status == "completed" {
		handledAt = nowMs
		handledBy = normalizeAdminActor(actor)
	}
	result, err := s.db.ExecContext(
		ctx,
		`UPDATE account_deletion_requests
		    SET status = ?,
		        handled_by = ?,
		        handler_note = ?,
		        handled_at = ?,
		        updated_at = ?
		  WHERE request_id = ?
		    AND status IN ('pending', 'processing')
		    AND ? IN ('processing', 'completed', 'rejected', 'cancelled')
		    AND (status = 'pending' OR ? <> 'processing')
		    AND (status <> ?)`,
		status,
		handledBy,
		nullableTrimmed(note),
		handledAt,
		nowMs,
		requestID,
		status,
		status,
		status,
	)
	if err != nil {
		return AccountDeletionRequest{}, err
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return AccountDeletionRequest{}, err
	}
	if affected == 0 {
		existing, getErr := s.GetAccountDeletionRequest(ctx, requestID)
		if getErr != nil {
			if getErr == sql.ErrNoRows {
				return AccountDeletionRequest{}, errAccountDeletionNotFound
			}
			return AccountDeletionRequest{}, getErr
		}
		if !canTransitionAccountDeletionStatus(existing.Status, status) {
			return AccountDeletionRequest{}, errAccountDeletionInvalidTransition
		}
		return AccountDeletionRequest{}, errAccountDeletionNotFound
	}
	return s.GetAccountDeletionRequest(ctx, requestID)
}

func (s *Store) GetAccountDeletionRequest(ctx context.Context, requestID string) (AccountDeletionRequest, error) {
	row := s.db.QueryRowContext(
		ctx,
		`SELECT request_id, user_id, phone_mask, status, reason, user_message, handled_by, handler_note, handled_at, created_at, updated_at
		   FROM account_deletion_requests
		  WHERE request_id = ?
		  LIMIT 1`,
		strings.TrimSpace(requestID),
	)
	return scanAccountDeletionRequest(row)
}

type accountDeletionScanner interface {
	Scan(dest ...any) error
}

func scanAccountDeletionRequest(scanner accountDeletionScanner) (AccountDeletionRequest, error) {
	var request AccountDeletionRequest
	var phoneMask, reason, message, handledBy, handlerNote sql.NullString
	var handledAt sql.NullInt64
	if err := scanner.Scan(
		&request.RequestID,
		&request.UserID,
		&phoneMask,
		&request.Status,
		&reason,
		&message,
		&handledBy,
		&handlerNote,
		&handledAt,
		&request.CreatedAt,
		&request.UpdatedAt,
	); err != nil {
		return request, err
	}
	request.PhoneMask = nullStringValue(phoneMask)
	request.Reason = nullStringValue(reason)
	request.UserMessage = nullStringValue(message)
	request.HandledBy = nullStringValue(handledBy)
	request.HandlerNote = nullStringValue(handlerNote)
	if handledAt.Valid {
		request.HandledAt = int64Ptr(handledAt.Int64)
	}
	return request, nil
}

func normalizeAccountDeletionStatus(raw string) string {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "pending", "processing", "cancelled", "rejected", "completed":
		return strings.ToLower(strings.TrimSpace(raw))
	default:
		return ""
	}
}

func canTransitionAccountDeletionStatus(from string, to string) bool {
	from = normalizeAccountDeletionStatus(from)
	to = normalizeAccountDeletionStatus(to)
	switch from {
	case "pending":
		return to == "processing" || to == "completed" || to == "rejected" || to == "cancelled"
	case "processing":
		return to == "completed" || to == "rejected" || to == "cancelled"
	default:
		return false
	}
}

func normalizeAccountDeletionReason(raw string) string {
	value := strings.ToLower(strings.TrimSpace(raw))
	if value == "" {
		return ""
	}
	var builder strings.Builder
	for _, ch := range value {
		if (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' {
			builder.WriteRune(ch)
		}
	}
	return truncateRunes(builder.String(), 64)
}

func normalizeAccountDeletionFreeText(raw string, field string) (string, string) {
	value := truncateRunes(strings.TrimSpace(raw), 255)
	if giftCardTextLooksSensitive(value) {
		return "", strings.TrimSpace(field) + "_contains_sensitive_value"
	}
	return value, ""
}

func accountDeletionLockName(userID string) string {
	hash := rateLimitHash(userID, "")
	if len(hash) > 40 {
		hash = hash[:40]
	}
	return "nongji_account_deletion_" + hash
}

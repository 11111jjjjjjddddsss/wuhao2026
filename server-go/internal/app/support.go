package app

import (
	"context"
	"crypto/subtle"
	"database/sql"
	"net/http"
	"os"
	"strings"
	"time"
	"unicode/utf8"
)

const (
	supportMessageMaxRunes  = 2000
	supportMessageListLimit = 100
)

type SupportMessage struct {
	ID           int64  `json:"id"`
	UserID       string `json:"user_id,omitempty"`
	SenderType   string `json:"sender_type"`
	Body         string `json:"body"`
	CreatedAt    int64  `json:"created_at"`
	ReadByUserAt *int64 `json:"read_by_user_at,omitempty"`
}

type SupportSummary struct {
	UnreadCount   int             `json:"unread_count"`
	LatestMessage *SupportMessage `json:"latest_message,omitempty"`
}

type supportMessageRequest struct {
	Body string `json:"body"`
}

type supportAdminMessageRequest struct {
	UserID string `json:"user_id"`
	Body   string `json:"body"`
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
	var body supportMessageRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid_json")
		return
	}
	normalized, validationError := normalizeSupportMessageBody(body.Body)
	if validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if err := s.store.EnsureUser(r.Context(), auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	message, err := s.store.CreateSupportMessage(r.Context(), auth.UserID, "user", normalized, time.Now().UnixMilli())
	if err != nil {
		s.logger.Error("create support message failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"message": message})
}

func (s *Server) handleMarkSupportRead(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if err := s.store.MarkSupportMessagesRead(r.Context(), auth.UserID, time.Now().UnixMilli()); err != nil {
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
		s.writeError(w, http.StatusBadRequest, "user_id required")
		return
	}
	messages, err := s.store.ListSupportMessages(r.Context(), userID, supportMessageListLimit)
	if err != nil {
		s.logger.Error("internal list support messages failed", "userId", userID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"messages": messages})
}

func (s *Server) handleInternalCreateSupportMessage(w http.ResponseWriter, r *http.Request) {
	if !s.requireSupportAdminSecret(w, r) {
		return
	}
	var body supportAdminMessageRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid_json")
		return
	}
	userID := strings.TrimSpace(body.UserID)
	if userID == "" {
		s.writeError(w, http.StatusBadRequest, "user_id required")
		return
	}
	normalized, validationError := normalizeSupportMessageBody(body.Body)
	if validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if err := s.store.EnsureUser(r.Context(), userID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", userID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	message, err := s.store.CreateSupportMessage(r.Context(), userID, "admin", normalized, time.Now().UnixMilli())
	if err != nil {
		s.logger.Error("internal create support message failed", "userId", userID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
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

func normalizeSupportMessageBody(raw string) (string, string) {
	body := strings.TrimSpace(raw)
	if body == "" {
		return "", "body required"
	}
	if utf8.RuneCountInString(body) > supportMessageMaxRunes {
		return "", "body too long"
	}
	return body, ""
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
		`SELECT id, user_id, sender_type, body, created_at, read_by_user_at
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

func (s *Store) CreateSupportMessage(ctx context.Context, userID string, senderType string, body string, createdAt int64) (*SupportMessage, error) {
	result, err := s.db.ExecContext(
		ctx,
		`INSERT INTO support_messages(user_id, sender_type, body, created_at)
		 VALUES (?, ?, ?, ?)`,
		userID,
		senderType,
		body,
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
		CreatedAt:  createdAt,
	}, nil
}

func (s *Store) MarkSupportMessagesRead(ctx context.Context, userID string, readAt int64) error {
	_, err := s.db.ExecContext(
		ctx,
		`UPDATE support_messages
		    SET read_by_user_at = ?
		  WHERE user_id = ?
		    AND sender_type IN ('admin', 'system')
		    AND read_by_user_at IS NULL`,
		readAt,
		userID,
	)
	return err
}

func (s *Store) getLatestSupportMessage(ctx context.Context, userID string) (*SupportMessage, error) {
	row := s.db.QueryRowContext(
		ctx,
		`SELECT id, user_id, sender_type, body, created_at, read_by_user_at
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
	var readByUserAt sql.NullInt64
	err := scanner.Scan(
		&message.ID,
		&message.UserID,
		&message.SenderType,
		&message.Body,
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
	return message, nil
}

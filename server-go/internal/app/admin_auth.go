package app

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"hash"
	"log/slog"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
	"unicode"
)

const (
	adminSessionCookieName = "nq_admin_session"
	adminCSRFCookieName    = "nq_admin_csrf"

	adminPasswordHashAlgorithm = "pbkdf2_sha256"
	adminPasswordSaltBytes     = 16
	adminPasswordKeyBytes      = 32
	defaultAdminPBKDF2Rounds   = 120000

	defaultAdminSessionDays = 1
	minAdminPasswordRunes   = 8
)

type AdminUser struct {
	ID                 int64  `json:"id"`
	Username           string `json:"username"`
	DisplayName        string `json:"display_name"`
	Role               string `json:"role"`
	Enabled            bool   `json:"enabled"`
	MustChangePassword bool   `json:"must_change_password"`
	CreatedAt          int64  `json:"created_at,omitempty"`
	UpdatedAt          int64  `json:"updated_at,omitempty"`
	LastLoginAt        *int64 `json:"last_login_at,omitempty"`
}

type AdminSession struct {
	AdminUser AdminUser
	ExpiresAt int64
	CSRFHash  string
}

type adminLoginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type adminChangePasswordRequest struct {
	CurrentPassword string `json:"current_password"`
	NewPassword     string `json:"new_password"`
}

type adminRequestContext struct {
	User      AdminUser
	ExpiresAt int64
}

func (s *Store) EnsureBootstrapAdminFromEnv(ctx context.Context, logger *slog.Logger) error {
	username := normalizeAdminUsername(os.Getenv("ADMIN_BOOTSTRAP_USERNAME"))
	password := os.Getenv("ADMIN_BOOTSTRAP_PASSWORD")
	if username == "" && strings.TrimSpace(password) == "" {
		return nil
	}
	if username == "" || strings.TrimSpace(password) == "" {
		return fmt.Errorf("ADMIN_BOOTSTRAP_USERNAME and ADMIN_BOOTSTRAP_PASSWORD must be set together")
	}
	if runeCount(password) < minAdminPasswordRunes {
		return fmt.Errorf("ADMIN_BOOTSTRAP_PASSWORD is too short")
	}
	role := normalizeAdminRole(os.Getenv("ADMIN_BOOTSTRAP_ROLE"))
	if role == "" {
		role = "owner"
	}
	displayName := truncateRunes(strings.TrimSpace(os.Getenv("ADMIN_BOOTSTRAP_DISPLAY_NAME")), 96)
	if displayName == "" {
		displayName = username
	}
	passwordHash, err := createAdminPasswordHash(password)
	if err != nil {
		return err
	}

	now := time.Now().UnixMilli()
	updateExisting := parseBoolEnv(os.Getenv("ADMIN_BOOTSTRAP_UPDATE_EXISTING"))
	if updateExisting {
		_, err = s.db.ExecContext(
			ctx,
			`INSERT INTO admin_users(username, display_name, password_hash, role, enabled, must_change_password, created_at, updated_at)
			 VALUES (?, ?, ?, ?, 1, 0, ?, ?)
			 ON DUPLICATE KEY UPDATE
			   display_name = VALUES(display_name),
			   password_hash = VALUES(password_hash),
			   role = VALUES(role),
			   enabled = 1,
			   updated_at = VALUES(updated_at)`,
			username,
			displayName,
			passwordHash,
			role,
			now,
			now,
		)
	} else {
		_, err = s.db.ExecContext(
			ctx,
			`INSERT IGNORE INTO admin_users(username, display_name, password_hash, role, enabled, must_change_password, created_at, updated_at)
			 VALUES (?, ?, ?, ?, 1, 0, ?, ?)`,
			username,
			displayName,
			passwordHash,
			role,
			now,
			now,
		)
	}
	if err != nil {
		return err
	}
	if logger != nil {
		logger.Info("admin bootstrap checked", "username", username, "role", role, "update_existing", updateExisting)
	}
	return nil
}

func (s *Server) handleAdminLogin(w http.ResponseWriter, r *http.Request) {
	if s.internalSecretLimiter != nil {
		if allowed, retryAfterSec := s.internalSecretLimiter.Consume(internalSecretRateLimitKey("admin_login", GetClientIP(r)), time.Now()); !allowed {
			w.Header().Set("Retry-After", strconv.Itoa(retryAfterSec))
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":           "rate_limited",
				"retry_after_sec": retryAfterSec,
			})
			return
		}
	}
	var body adminLoginRequest
	if err := decodeJSONBodyLimited(r, &body, 8*1024); err != nil {
		s.recordAdminAuditLog(r, "anonymous", "admin.login", "admin_user", "", "", false, http.StatusBadRequest, map[string]any{"error_code": "invalid_json"})
		s.writeJSONDecodeError(w, err)
		return
	}
	username := normalizeAdminUsername(body.Username)
	if s.adminLoginLimiter != nil {
		if allowed, retryAfterSec := s.adminLoginLimiter.Consume(adminLoginRateLimitKey(username, GetClientIP(r)), time.Now()); !allowed {
			w.Header().Set("Retry-After", strconv.Itoa(retryAfterSec))
			s.recordAdminAuditLog(r, "anonymous", "admin.login", "admin_user", username, "", false, http.StatusTooManyRequests, map[string]any{"error_code": "rate_limited"})
			s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
				"error":           "rate_limited",
				"retry_after_sec": retryAfterSec,
			})
			return
		}
	}
	user, passwordHash, err := s.store.GetAdminUserForLogin(r.Context(), username)
	if err != nil {
		if err != sql.ErrNoRows {
			s.logger.Error("admin login user lookup failed", "username", username, "error", err)
		}
		s.recordAdminAuditLog(r, "anonymous", "admin.login", "admin_user", username, "", false, http.StatusUnauthorized, map[string]any{"error_code": "unauthorized"})
		s.writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	if !user.Enabled || !verifyAdminPasswordHash(body.Password, passwordHash) {
		s.recordAdminAuditLog(r, "anonymous", "admin.login", "admin_user", username, "", false, http.StatusUnauthorized, map[string]any{"error_code": "unauthorized"})
		s.writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	rawSession, err := randomURLSafeToken(32)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	rawCSRF, err := randomURLSafeToken(32)
	if err != nil {
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	now := time.Now()
	expiresAt := now.Add(resolveAdminSessionDuration()).UnixMilli()
	if err := s.store.CreateAdminSession(r.Context(), user.ID, user.Role, hashAdminToken(rawSession), hashAdminToken(rawCSRF), now.UnixMilli(), expiresAt); err != nil {
		s.logger.Error("create admin session failed", "username", username, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	s.setAdminCookies(w, rawSession, rawCSRF, expiresAt)
	s.recordAdminAuditLog(r, username, "admin.login", "admin_user", username, "", true, http.StatusOK, map[string]any{"role": user.Role})
	user.LastLoginAt = int64Ptr(now.UnixMilli())
	s.writeJSON(w, http.StatusOK, map[string]any{
		"admin_user": user,
		"expires_at": expiresAt,
		"csrf_token": rawCSRF,
	})
}

func (s *Server) handleAdminMe(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r)
	if !ok {
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"admin_user": admin.User,
		"expires_at": admin.ExpiresAt,
		"csrf_token": adminCSRFTokenFromCookie(r),
	})
}

func (s *Server) handleAdminChangePassword(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r)
	if !ok {
		return
	}
	var body adminChangePasswordRequest
	if err := decodeJSONBodyLimited(r, &body, 8*1024); err != nil {
		s.recordAdminAuditLog(r, admin.User.Username, "admin.password.change", "admin_user", admin.User.Username, "", false, http.StatusBadRequest, map[string]any{"error_code": "invalid_json"})
		s.writeJSONDecodeError(w, err)
		return
	}
	if strings.TrimSpace(body.CurrentPassword) == "" || strings.TrimSpace(body.NewPassword) == "" {
		s.recordAdminAuditLog(r, admin.User.Username, "admin.password.change", "admin_user", admin.User.Username, "", false, http.StatusBadRequest, map[string]any{"error_code": "password_required"})
		s.writeError(w, http.StatusBadRequest, "password_required")
		return
	}
	if runeCount(body.NewPassword) < minAdminPasswordRunes {
		s.recordAdminAuditLog(r, admin.User.Username, "admin.password.change", "admin_user", admin.User.Username, "", false, http.StatusBadRequest, map[string]any{"error_code": "password_too_short"})
		s.writeError(w, http.StatusBadRequest, "password_too_short")
		return
	}
	currentHash, err := s.store.GetAdminPasswordHash(r.Context(), admin.User.ID)
	if err != nil {
		if err == sql.ErrNoRows {
			s.writeError(w, http.StatusUnauthorized, "unauthorized")
			return
		}
		s.logger.Error("admin password lookup failed", "admin", admin.User.Username, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if !verifyAdminPasswordHash(body.CurrentPassword, currentHash) {
		s.recordAdminAuditLog(r, admin.User.Username, "admin.password.change", "admin_user", admin.User.Username, "", false, http.StatusUnauthorized, map[string]any{"error_code": "current_password_invalid"})
		s.writeError(w, http.StatusUnauthorized, "current_password_invalid")
		return
	}
	if verifyAdminPasswordHash(body.NewPassword, currentHash) {
		s.recordAdminAuditLog(r, admin.User.Username, "admin.password.change", "admin_user", admin.User.Username, "", false, http.StatusBadRequest, map[string]any{"error_code": "password_unchanged"})
		s.writeError(w, http.StatusBadRequest, "password_unchanged")
		return
	}
	newHash, err := createAdminPasswordHash(body.NewPassword)
	if err != nil {
		s.logger.Error("admin password hash failed", "admin", admin.User.Username, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	nowMs := time.Now().UnixMilli()
	if err := s.store.UpdateAdminPassword(r.Context(), admin.User.ID, newHash, nowMs); err != nil {
		if err == sql.ErrNoRows {
			s.writeError(w, http.StatusUnauthorized, "unauthorized")
			return
		}
		s.logger.Error("admin password update failed", "admin", admin.User.Username, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	revokedOtherSessions := false
	rawSession := adminSessionTokenFromRequest(r)
	if rawSession != "" {
		if err := s.store.RevokeOtherAdminSessions(r.Context(), admin.User.ID, hashAdminToken(rawSession), nowMs); err != nil {
			s.logger.Warn("revoke other admin sessions failed", "admin", admin.User.Username, "error", err)
		} else {
			revokedOtherSessions = true
		}
	}
	admin.User.MustChangePassword = false
	admin.User.UpdatedAt = nowMs
	s.recordAdminAuditLog(r, admin.User.Username, "admin.password.change", "admin_user", admin.User.Username, "", true, http.StatusOK, map[string]any{"revoked_other_sessions": revokedOtherSessions})
	s.writeJSON(w, http.StatusOK, map[string]any{
		"admin_user": admin.User,
		"expires_at": admin.ExpiresAt,
		"csrf_token": adminCSRFTokenFromCookie(r),
	})
}

func (s *Server) handleAdminLogout(w http.ResponseWriter, r *http.Request) {
	admin, ok := s.requireAdmin(w, r)
	if !ok {
		return
	}
	rawSession := adminSessionTokenFromRequest(r)
	if rawSession != "" {
		if err := s.store.RevokeAdminSession(r.Context(), hashAdminToken(rawSession), time.Now().UnixMilli()); err != nil {
			s.logger.Warn("revoke admin session failed", "admin", admin.User.Username, "error", err)
		}
	}
	s.clearAdminCookies(w)
	s.recordAdminAuditLog(r, admin.User.Username, "admin.logout", "admin_user", admin.User.Username, "", true, http.StatusOK, nil)
	s.writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (s *Server) requireAdmin(w http.ResponseWriter, r *http.Request, roles ...string) (*adminRequestContext, bool) {
	rawSession := adminSessionTokenFromRequest(r)
	if rawSession == "" {
		s.writeError(w, http.StatusUnauthorized, "unauthorized")
		return nil, false
	}
	session, err := s.store.GetActiveAdminSession(r.Context(), hashAdminToken(rawSession), time.Now().UnixMilli())
	if err != nil {
		if err != sql.ErrNoRows {
			s.logger.Error("admin session lookup failed", "error", err)
		}
		s.writeError(w, http.StatusUnauthorized, "unauthorized")
		return nil, false
	}
	if !adminRoleAllowed(session.AdminUser.Role, roles...) {
		s.recordAdminAuditLog(r, session.AdminUser.Username, "admin.authorization.denied", "admin_api", r.URL.Path, "", false, http.StatusForbidden, map[string]any{"role": session.AdminUser.Role})
		s.writeError(w, http.StatusForbidden, "forbidden")
		return nil, false
	}
	if requiresAdminCSRF(r.Method) {
		headerToken := strings.TrimSpace(r.Header.Get("X-Admin-CSRF"))
		if headerToken == "" || hashAdminToken(headerToken) != session.CSRFHash {
			s.recordAdminAuditLog(r, session.AdminUser.Username, "admin.csrf.denied", "admin_api", r.URL.Path, "", false, http.StatusForbidden, nil)
			s.writeError(w, http.StatusForbidden, "csrf_required")
			return nil, false
		}
	}
	if session.AdminUser.MustChangePassword && !adminPasswordChangeAllowedPath(r.URL.Path) {
		s.writeError(w, http.StatusPreconditionRequired, "password_change_required")
		return nil, false
	}
	_ = s.store.TouchAdminSession(contextBackground(), hashAdminToken(rawSession), time.Now().UnixMilli())
	return &adminRequestContext{User: session.AdminUser, ExpiresAt: session.ExpiresAt}, true
}

func (s *Server) setAdminCookies(w http.ResponseWriter, sessionToken string, csrfToken string, expiresAt int64) {
	expires := time.UnixMilli(expiresAt)
	commonPath := "/"
	http.SetCookie(w, &http.Cookie{
		Name:     adminSessionCookieName,
		Value:    sessionToken,
		Path:     commonPath,
		Expires:  expires,
		HttpOnly: true,
		Secure:   adminCookieSecure(),
		SameSite: http.SameSiteLaxMode,
	})
	http.SetCookie(w, &http.Cookie{
		Name:     adminCSRFCookieName,
		Value:    csrfToken,
		Path:     commonPath,
		Expires:  expires,
		HttpOnly: false,
		Secure:   adminCookieSecure(),
		SameSite: http.SameSiteLaxMode,
	})
}

func (s *Server) clearAdminCookies(w http.ResponseWriter) {
	for _, name := range []string{adminSessionCookieName, adminCSRFCookieName} {
		http.SetCookie(w, &http.Cookie{
			Name:     name,
			Value:    "",
			Path:     "/",
			Expires:  time.Unix(0, 0),
			MaxAge:   -1,
			HttpOnly: name == adminSessionCookieName,
			Secure:   adminCookieSecure(),
			SameSite: http.SameSiteLaxMode,
		})
	}
}

func (s *Store) GetAdminUserForLogin(ctx context.Context, username string) (AdminUser, string, error) {
	var user AdminUser
	var passwordHash string
	var enabled int
	var mustChange int
	var lastLogin sql.NullInt64
	err := s.db.QueryRowContext(
		ctx,
		`SELECT id, username, display_name, password_hash, role, enabled, must_change_password, last_login_at, created_at, updated_at
		   FROM admin_users
		  WHERE username = ?
		  LIMIT 1`,
		username,
	).Scan(&user.ID, &user.Username, &user.DisplayName, &passwordHash, &user.Role, &enabled, &mustChange, &lastLogin, &user.CreatedAt, &user.UpdatedAt)
	if err != nil {
		return AdminUser{}, "", err
	}
	user.Enabled = enabled != 0
	user.MustChangePassword = mustChange != 0
	if lastLogin.Valid {
		user.LastLoginAt = int64Ptr(lastLogin.Int64)
	}
	return user, passwordHash, nil
}

func (s *Store) GetAdminPasswordHash(ctx context.Context, adminUserID int64) (string, error) {
	var passwordHash string
	err := s.db.QueryRowContext(
		ctx,
		`SELECT password_hash
		   FROM admin_users
		  WHERE id = ?
		    AND enabled = 1
		  LIMIT 1`,
		adminUserID,
	).Scan(&passwordHash)
	return passwordHash, err
}

func (s *Store) UpdateAdminPassword(ctx context.Context, adminUserID int64, passwordHash string, nowMs int64) error {
	result, err := s.db.ExecContext(
		ctx,
		`UPDATE admin_users
		    SET password_hash = ?,
		        must_change_password = 0,
		        updated_at = ?
		  WHERE id = ?
		    AND enabled = 1`,
		passwordHash,
		nowMs,
		adminUserID,
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

func (s *Store) CreateAdminSession(ctx context.Context, adminUserID int64, role string, sessionHash string, csrfHash string, nowMs int64, expiresAt int64) error {
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO admin_sessions(session_hash, admin_user_id, csrf_hash, role, created_at, last_seen_at, expires_at, revoked_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, NULL)`,
		sessionHash,
		adminUserID,
		csrfHash,
		role,
		nowMs,
		nowMs,
		expiresAt,
	)
	if err != nil {
		return err
	}
	_, err = s.db.ExecContext(ctx, "UPDATE admin_users SET last_login_at = ?, updated_at = ? WHERE id = ?", nowMs, nowMs, adminUserID)
	return err
}

func (s *Store) GetActiveAdminSession(ctx context.Context, sessionHash string, nowMs int64) (*AdminSession, error) {
	var session AdminSession
	var enabled int
	var mustChange int
	var lastLogin sql.NullInt64
	err := s.db.QueryRowContext(
		ctx,
		`SELECT
		   u.id,
		   u.username,
		   u.display_name,
		   u.role,
		   u.enabled,
		   u.must_change_password,
		   u.last_login_at,
		   u.created_at,
		   u.updated_at,
		   sess.csrf_hash,
		   sess.expires_at
		 FROM admin_sessions sess
		 JOIN admin_users u ON u.id = sess.admin_user_id
		 WHERE sess.session_hash = ?
		   AND sess.revoked_at IS NULL
		   AND sess.expires_at > ?
		   AND u.enabled = 1
		 LIMIT 1`,
		sessionHash,
		nowMs,
	).Scan(
		&session.AdminUser.ID,
		&session.AdminUser.Username,
		&session.AdminUser.DisplayName,
		&session.AdminUser.Role,
		&enabled,
		&mustChange,
		&lastLogin,
		&session.AdminUser.CreatedAt,
		&session.AdminUser.UpdatedAt,
		&session.CSRFHash,
		&session.ExpiresAt,
	)
	if err != nil {
		return nil, err
	}
	session.AdminUser.Enabled = enabled != 0
	session.AdminUser.MustChangePassword = mustChange != 0
	if lastLogin.Valid {
		session.AdminUser.LastLoginAt = int64Ptr(lastLogin.Int64)
	}
	return &session, nil
}

func (s *Store) TouchAdminSession(ctx context.Context, sessionHash string, nowMs int64) error {
	_, err := s.db.ExecContext(ctx, "UPDATE admin_sessions SET last_seen_at = ? WHERE session_hash = ? AND revoked_at IS NULL", nowMs, sessionHash)
	return err
}

func (s *Store) RevokeAdminSession(ctx context.Context, sessionHash string, revokedAt int64) error {
	_, err := s.db.ExecContext(ctx, "UPDATE admin_sessions SET revoked_at = ? WHERE session_hash = ? AND revoked_at IS NULL", revokedAt, sessionHash)
	return err
}

func (s *Store) RevokeOtherAdminSessions(ctx context.Context, adminUserID int64, keepSessionHash string, revokedAt int64) error {
	_, err := s.db.ExecContext(
		ctx,
		`UPDATE admin_sessions
		    SET revoked_at = ?
		  WHERE admin_user_id = ?
		    AND session_hash <> ?
		    AND revoked_at IS NULL`,
		revokedAt,
		adminUserID,
		keepSessionHash,
	)
	return err
}

func adminPasswordChangeAllowedPath(path string) bool {
	switch strings.TrimSpace(path) {
	case "/admin-api/v1/auth/me", "/admin-api/v1/auth/logout", "/admin-api/v1/auth/change-password":
		return true
	default:
		return false
	}
}

func normalizeAdminUsername(raw string) string {
	trimmed := strings.TrimSpace(strings.ToLower(raw))
	var builder strings.Builder
	for _, r := range trimmed {
		if unicode.IsLetter(r) || unicode.IsDigit(r) || r == '_' || r == '-' || r == '.' || r == '@' {
			builder.WriteRune(r)
		}
	}
	return truncateRunes(builder.String(), 96)
}

func normalizeAdminRole(raw string) string {
	role := normalizeClientLogIdentifier(raw, 32)
	switch role {
	case "owner", "ops_readonly", "support", "content_ops", "release_ops", "finance_ops", "auditor":
		return role
	default:
		return ""
	}
}

func adminRoleAllowed(role string, allowed ...string) bool {
	role = normalizeAdminRole(role)
	if role == "owner" {
		return true
	}
	if len(allowed) == 0 {
		return role != ""
	}
	for _, item := range allowed {
		if role == normalizeAdminRole(item) {
			return true
		}
	}
	return false
}

func requiresAdminCSRF(method string) bool {
	switch strings.ToUpper(strings.TrimSpace(method)) {
	case http.MethodGet, http.MethodHead, http.MethodOptions:
		return false
	default:
		return true
	}
}

func adminSessionTokenFromRequest(r *http.Request) string {
	cookie, err := r.Cookie(adminSessionCookieName)
	if err != nil {
		return ""
	}
	return strings.TrimSpace(cookie.Value)
}

func adminCSRFTokenFromCookie(r *http.Request) string {
	cookie, err := r.Cookie(adminCSRFCookieName)
	if err != nil {
		return ""
	}
	return strings.TrimSpace(cookie.Value)
}

func adminCookieSecure() bool {
	raw := strings.TrimSpace(os.Getenv("ADMIN_COOKIE_SECURE"))
	if raw == "" {
		return isProductionEnv()
	}
	switch strings.ToLower(raw) {
	case "1", "true", "yes", "y", "on":
		return true
	case "0", "false", "no", "n", "off":
		return false
	default:
		return isProductionEnv()
	}
}

func resolveAdminSessionDuration() time.Duration {
	days := envIntWithDefault("ADMIN_SESSION_DAYS", defaultAdminSessionDays)
	if days <= 0 || days > 30 {
		days = defaultAdminSessionDays
	}
	return time.Duration(days) * 24 * time.Hour
}

func createAdminPasswordHash(password string) (string, error) {
	salt := make([]byte, adminPasswordSaltBytes)
	if _, err := rand.Read(salt); err != nil {
		return "", err
	}
	key := pbkdf2Key([]byte(password), salt, defaultAdminPBKDF2Rounds, adminPasswordKeyBytes, sha256.New)
	return strings.Join([]string{
		adminPasswordHashAlgorithm,
		strconv.Itoa(defaultAdminPBKDF2Rounds),
		base64.RawStdEncoding.EncodeToString(salt),
		base64.RawStdEncoding.EncodeToString(key),
	}, "$"), nil
}

func verifyAdminPasswordHash(password string, encoded string) bool {
	parts := strings.Split(encoded, "$")
	if len(parts) != 4 || parts[0] != adminPasswordHashAlgorithm {
		return false
	}
	rounds, err := strconv.Atoi(parts[1])
	if err != nil || rounds <= 0 || rounds > 1000000 {
		return false
	}
	salt, err := base64.RawStdEncoding.DecodeString(parts[2])
	if err != nil || len(salt) == 0 {
		return false
	}
	expected, err := base64.RawStdEncoding.DecodeString(parts[3])
	if err != nil || len(expected) == 0 {
		return false
	}
	actual := pbkdf2Key([]byte(password), salt, rounds, len(expected), sha256.New)
	return subtle.ConstantTimeCompare(actual, expected) == 1
}

func pbkdf2Key(password []byte, salt []byte, iter int, keyLen int, h func() hash.Hash) []byte {
	prf := hmac.New(h, password)
	hashLen := prf.Size()
	numBlocks := (keyLen + hashLen - 1) / hashLen
	var derived []byte
	var block [4]byte
	for i := 1; i <= numBlocks; i++ {
		block[0] = byte(i >> 24)
		block[1] = byte(i >> 16)
		block[2] = byte(i >> 8)
		block[3] = byte(i)
		prf.Reset()
		prf.Write(salt)
		prf.Write(block[:])
		u := prf.Sum(nil)
		t := make([]byte, len(u))
		copy(t, u)
		for j := 1; j < iter; j++ {
			prf.Reset()
			prf.Write(u)
			u = prf.Sum(nil)
			for k := range t {
				t[k] ^= u[k]
			}
		}
		derived = append(derived, t...)
	}
	return derived[:keyLen]
}

func randomURLSafeToken(bytesLen int) (string, error) {
	if bytesLen <= 0 {
		bytesLen = 32
	}
	data := make([]byte, bytesLen)
	if _, err := rand.Read(data); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(data), nil
}

func hashAdminToken(token string) string {
	sum := sha256.Sum256([]byte(strings.TrimSpace(token)))
	return hex.EncodeToString(sum[:])
}

func runeCount(value string) int {
	count := 0
	for range value {
		count++
	}
	return count
}

func int64Ptr(value int64) *int64 {
	return &value
}

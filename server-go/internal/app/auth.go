package app

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

func IsAuthStrict() bool {
	raw := strings.ToLower(strings.TrimSpace(os.Getenv("AUTH_STRICT")))
	return raw == "1" || raw == "true" || raw == "yes"
}

func ResolveAuthUserID(r *http.Request) AuthInfo {
	ip := GetClientIP(r)
	maskedIP := maskIP(ip)
	authHeader := strings.TrimSpace(r.Header.Get("Authorization"))
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))

	if strings.HasPrefix(authHeader, "Bearer ") && secret != "" {
		token := strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))
		if userID, sessionID, ok := verifyBearerToken(token, secret); ok {
			return AuthInfo{
				UserID:    userID,
				SessionID: sessionID,
				AuthMode:  AuthModeToken,
				MaskedIP:  maskedIP,
			}
		}
	}

	if IsAuthStrict() {
		return AuthInfo{
			UserID:   "",
			AuthMode: AuthModeUnauthorized,
			MaskedIP: maskedIP,
		}
	}

	headerUserID := strings.TrimSpace(r.Header.Get("X-User-Id"))
	if len(headerUserID) > 128 {
		headerUserID = headerUserID[:128]
	}
	if headerUserID != "" {
		return AuthInfo{
			UserID:   headerUserID,
			AuthMode: AuthModeHeader,
			MaskedIP: maskedIP,
		}
	}

	return AuthInfo{
		UserID:   "",
		AuthMode: AuthModeUnauthorized,
		MaskedIP: maskedIP,
	}
}

func GetClientIP(r *http.Request) string {
	xffRaw := strings.TrimSpace(r.Header.Get("X-Forwarded-For"))
	if xffRaw != "" {
		parts := strings.Split(xffRaw, ",")
		candidates := make([]string, 0, len(parts))
		for _, part := range parts {
			ip := strings.TrimSpace(strings.TrimPrefix(part, "::ffff:"))
			if ip != "" {
				candidates = append(candidates, ip)
			}
		}
		for _, ip := range candidates {
			if !isPrivateIPv4(ip) {
				return ip
			}
		}
		if len(candidates) > 0 {
			return candidates[0]
		}
	}

	host := strings.TrimSpace(r.RemoteAddr)
	if host == "" {
		return ""
	}
	if idx := strings.LastIndex(host, ":"); idx > 0 {
		host = host[:idx]
	}
	return strings.TrimPrefix(host, "::ffff:")
}

func isPrivateIPv4(ip string) bool {
	parts := strings.Split(ip, ".")
	if len(parts) != 4 {
		return false
	}
	values := make([]int, 0, 4)
	for _, part := range parts {
		value, err := strconv.Atoi(part)
		if err != nil {
			return false
		}
		values = append(values, value)
	}

	switch {
	case values[0] == 10:
		return true
	case values[0] == 127:
		return true
	case values[0] == 192 && values[1] == 168:
		return true
	case values[0] == 172 && values[1] >= 16 && values[1] <= 31:
		return true
	default:
		return false
	}
}

func maskIP(ip string) string {
	if strings.Count(ip, ".") == 3 {
		parts := strings.Split(ip, ".")
		return parts[0] + "." + parts[1] + ".*.*"
	}
	if strings.Contains(ip, ":") {
		parts := strings.Split(ip, ":")
		if len(parts) >= 2 {
			return parts[0] + ":" + parts[1] + ":*"
		}
	}
	return "unknown"
}

func verifyToken(token string, secret string) (string, bool) {
	userID, _, ok := verifyBearerToken(token, secret)
	return userID, ok
}

func verifyBearerToken(token string, secret string) (string, string, bool) {
	if strings.HasPrefix(token, "v2.") {
		return verifyV2Token(token, secret)
	}
	userID, ok := verifyLegacyToken(token, secret)
	return userID, "", ok
}

func verifyLegacyToken(token string, secret string) (string, bool) {
	decoded, err := base64.StdEncoding.DecodeString(token)
	if err != nil {
		decoded, err = base64.RawStdEncoding.DecodeString(token)
		if err != nil {
			return "", false
		}
	}

	parts := strings.Split(string(decoded), ":")
	if len(parts) != 3 {
		return "", false
	}

	userID := parts[0]
	tsRaw := parts[1]
	signature := parts[2]
	if userID == "" || tsRaw == "" || signature == "" {
		return "", false
	}

	ts, err := strconv.ParseInt(tsRaw, 10, 64)
	if err != nil {
		return "", false
	}
	nowSec := time.Now().Unix()
	maxAgeSec := int64(30 * 24 * 60 * 60)
	if nowSec-ts > maxAgeSec || ts-nowSec > maxAgeSec {
		return "", false
	}

	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(userID + ":" + tsRaw))
	expected := hex.EncodeToString(mac.Sum(nil))
	if !hmac.Equal([]byte(expected), []byte(signature)) {
		return "", false
	}

	return userID, true
}

type authTokenPayload struct {
	UserID    string `json:"uid"`
	SessionID string `json:"sid"`
	IssuedAt  int64  `json:"iat"`
	ExpiresAt int64  `json:"exp"`
}

func issueAuthToken(userID string, sessionID string, now time.Time, ttl time.Duration, secret string) (string, int64, error) {
	userID = strings.TrimSpace(userID)
	sessionID = strings.TrimSpace(sessionID)
	secret = strings.TrimSpace(secret)
	if userID == "" || sessionID == "" {
		return "", 0, fmt.Errorf("auth token identity missing")
	}
	if secret == "" {
		return "", 0, fmt.Errorf("APP_SECRET is missing")
	}
	expiresAt := now.Add(ttl).UnixMilli()
	payload := authTokenPayload{
		UserID:    userID,
		SessionID: sessionID,
		IssuedAt:  now.UnixMilli(),
		ExpiresAt: expiresAt,
	}
	payloadJSON, err := json.Marshal(payload)
	if err != nil {
		return "", 0, err
	}
	encodedPayload := base64.RawURLEncoding.EncodeToString(payloadJSON)
	signature := signAuthPayload(encodedPayload, secret)
	return "v2." + encodedPayload + "." + signature, expiresAt, nil
}

func verifyV2Token(token string, secret string) (string, string, bool) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 || parts[0] != "v2" || parts[1] == "" || parts[2] == "" {
		return "", "", false
	}
	expected := signAuthPayload(parts[1], secret)
	if !hmac.Equal([]byte(expected), []byte(parts[2])) {
		return "", "", false
	}
	payloadJSON, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return "", "", false
	}
	var payload authTokenPayload
	if err := json.Unmarshal(payloadJSON, &payload); err != nil {
		return "", "", false
	}
	if payload.UserID == "" || payload.SessionID == "" || payload.ExpiresAt <= 0 {
		return "", "", false
	}
	nowMs := time.Now().UnixMilli()
	maxSkewMs := int64(24 * time.Hour / time.Millisecond)
	if nowMs > payload.ExpiresAt || payload.IssuedAt-nowMs > maxSkewMs {
		return "", "", false
	}
	return payload.UserID, payload.SessionID, true
}

func signAuthPayload(encodedPayload string, secret string) string {
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(encodedPayload))
	return hex.EncodeToString(mac.Sum(nil))
}

func randomHexString(bytesCount int) (string, error) {
	if bytesCount <= 0 {
		bytesCount = 16
	}
	buf := make([]byte, bytesCount)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf), nil
}

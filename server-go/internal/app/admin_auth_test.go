package app

import "testing"

func TestAdminPasswordHashVerifiesOnlyOriginalPassword(t *testing.T) {
	hash, err := createAdminPasswordHash("correct-password")
	if err != nil {
		t.Fatalf("createAdminPasswordHash failed: %v", err)
	}
	if !verifyAdminPasswordHash("correct-password", hash) {
		t.Fatalf("password hash should verify original password")
	}
	if verifyAdminPasswordHash("wrong-password", hash) {
		t.Fatalf("password hash should reject wrong password")
	}
}

func TestAdminPasswordChangeAllowedPaths(t *testing.T) {
	allowed := []string{
		"/admin-api/v1/auth/me",
		"/admin-api/v1/auth/logout",
		"/admin-api/v1/auth/change-password",
	}
	for _, path := range allowed {
		if !adminPasswordChangeAllowedPath(path) {
			t.Fatalf("path should be allowed during forced password change: %s", path)
		}
	}
	blocked := []string{
		"/admin-api/v1/overview",
		"/admin-api/v1/monitoring",
		"/admin-api/v1/users",
	}
	for _, path := range blocked {
		if adminPasswordChangeAllowedPath(path) {
			t.Fatalf("path should not be allowed during forced password change: %s", path)
		}
	}
}

func TestAdminCookieSecureInvalidValueFallsBackToProductionDefault(t *testing.T) {
	t.Setenv("APP_ENV", "production")
	t.Setenv("ENV", "")
	t.Setenv("GO_ENV", "")
	t.Setenv("ADMIN_COOKIE_SECURE", "ture")
	if !adminCookieSecure() {
		t.Fatalf("invalid ADMIN_COOKIE_SECURE must not disable Secure cookies in production")
	}

	t.Setenv("ADMIN_COOKIE_SECURE", "false")
	if adminCookieSecure() {
		t.Fatalf("explicit false ADMIN_COOKIE_SECURE should remain available for non-production tests")
	}
}

package app

import "testing"

func TestSanitizeDeploymentRevision(t *testing.T) {
	if got := sanitizeDeploymentRevision(" c8caace6\n"); got != "c8caace6" {
		t.Fatalf("sanitizeDeploymentRevision() = %q, want c8caace6", got)
	}
	for _, input := range []string{
		"",
		"abc/def",
		"abc def",
		"abc:def",
		"01234567890123456789012345678901234567890123456789012345678901234",
	} {
		if got := sanitizeDeploymentRevision(input); got != "" {
			t.Fatalf("sanitizeDeploymentRevision(%q) = %q, want empty", input, got)
		}
	}
}

func TestDeploymentRevisionUsesEnvironment(t *testing.T) {
	t.Setenv("DEPLOY_COMMIT", "c8caace6")
	if got := deploymentRevision(); got != "c8caace6" {
		t.Fatalf("deploymentRevision() = %q, want c8caace6", got)
	}
}

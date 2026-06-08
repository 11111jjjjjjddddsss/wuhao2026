package app

import (
	"errors"
	"strings"
	"testing"
)

func TestGiftCardCodeCipherRoundTrip(t *testing.T) {
	code := "NQ-M7AB-CD23-EF45-GH67"
	ciphertext, err := encryptGiftCardCodeWithSecret(code, "unit-test-secret")
	if err != nil {
		t.Fatalf("encryptGiftCardCodeWithSecret failed: %v", err)
	}
	if ciphertext == "" || !strings.HasPrefix(ciphertext, "v1:") {
		t.Fatalf("ciphertext = %q, want v1 payload", ciphertext)
	}
	if strings.Contains(ciphertext, strings.TrimSpace(code)) {
		t.Fatalf("ciphertext leaked gift card code: %q", ciphertext)
	}

	plain, err := decryptGiftCardCodeWithSecret(ciphertext, "unit-test-secret")
	if err != nil {
		t.Fatalf("decryptGiftCardCodeWithSecret failed: %v", err)
	}
	if plain != strings.TrimSpace(code) {
		t.Fatalf("plain = %q, want code %q", plain, strings.TrimSpace(code))
	}

	if _, err := decryptGiftCardCodeWithSecret(ciphertext, "wrong-secret"); err == nil {
		t.Fatalf("decrypting gift card code with wrong secret should fail")
	}
}

func TestGiftCardCodeCipherRequiresSecret(t *testing.T) {
	if _, err := encryptGiftCardCodeWithSecret("NQ-M7AB-CD23-EF45-GH67", ""); !errors.Is(err, errGiftCardSecretMissing) {
		t.Fatalf("encrypt missing secret err = %v, want errGiftCardSecretMissing", err)
	}
	if _, err := decryptGiftCardCodeWithSecret("v1:abc", ""); !errors.Is(err, errGiftCardSecretMissing) {
		t.Fatalf("decrypt missing secret err = %v, want errGiftCardSecretMissing", err)
	}
}

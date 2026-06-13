package app

import (
	"database/sql"
	"errors"
	"fmt"
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

func TestGiftCardTextLooksSensitiveRejectsSeparatedPhonesAndSecrets(t *testing.T) {
	for _, text := range []string{
		"请联系 138-0013-8000 处理",
		"手机号 138 0013 8000",
		"token=secret-value",
		"NQ-M7AB-CD23-EF45-GH67",
	} {
		if !giftCardTextLooksSensitive(text) {
			t.Fatalf("expected sensitive text to be rejected: %q", text)
		}
	}
}

func TestGiftCardTextLooksSensitiveAllowsOperationalNotes(t *testing.T) {
	for _, text := range []string{
		"电话已沟通，等待用户确认",
		"客服已线下核验会员和订单",
		"用户申请注销，礼品卡权益已核对",
	} {
		if giftCardTextLooksSensitive(text) {
			t.Fatalf("expected operational note to be allowed: %q", text)
		}
	}
}

func TestScanGiftCardEntryOnlyDecryptsWhenAllowed(t *testing.T) {
	t.Setenv("APP_SECRET", "unit-test-secret")
	code := "NQ-M7AB-CD23-EF45-GH67"
	ciphertext, err := encryptGiftCardCode(code)
	if err != nil {
		t.Fatalf("encryptGiftCardCode failed: %v", err)
	}

	hidden, err := scanGiftCardEntry(giftCardScannerFixture(ciphertext), false)
	if err != nil {
		t.Fatalf("scan hidden gift card failed: %v", err)
	}
	if hidden.Code != "" {
		t.Fatalf("hidden gift card code = %q, want empty", hidden.Code)
	}

	visible, err := scanGiftCardEntry(giftCardScannerFixture(ciphertext), true)
	if err != nil {
		t.Fatalf("scan visible gift card failed: %v", err)
	}
	if visible.Code != code {
		t.Fatalf("visible gift card code = %q, want %q", visible.Code, code)
	}
}

type giftCardTestScanner []any

func (s giftCardTestScanner) Scan(dest ...any) error {
	if len(dest) != len(s) {
		return fmt.Errorf("scan destination count = %d, want %d", len(dest), len(s))
	}
	for idx, value := range s {
		switch target := dest[idx].(type) {
		case *string:
			*target = value.(string)
		case *int:
			*target = value.(int)
		case *int64:
			*target = value.(int64)
		case *sql.NullString:
			*target = value.(sql.NullString)
		case *sql.NullInt64:
			*target = value.(sql.NullInt64)
		default:
			return fmt.Errorf("unsupported scan target %T", dest[idx])
		}
	}
	return nil
}

func giftCardScannerFixture(ciphertext string) giftCardTestScanner {
	return giftCardTestScanner{
		"gcc_test",
		"gcb_test",
		"NQ-M7AB-****-GH67",
		"GH67",
		sql.NullString{String: ciphertext, Valid: true},
		string(TierPlus),
		30,
		"active",
		int64(1700000000000),
		sql.NullInt64{},
		"owner",
		sql.NullString{},
		sql.NullString{},
		sql.NullString{},
		sql.NullString{},
		sql.NullString{},
		sql.NullString{},
		sql.NullInt64{},
		sql.NullInt64{},
		sql.NullInt64{},
		int64(1700000000000),
		int64(1700000000000),
	}
}

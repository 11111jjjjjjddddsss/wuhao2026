package app

import (
	"errors"
	"strings"
	"testing"
	"time"
)

func TestNormalizeMainlandPhone(t *testing.T) {
	cases := map[string]string{
		"13800138000":    "13800138000",
		"+8613800138000": "13800138000",
		"8613800138000":  "13800138000",
		"138 0013 8000":  "13800138000",
		"138-0013-8000":  "13800138000",
		"23800138000":    "",
		"1380013800":     "",
	}
	for input, want := range cases {
		if got := normalizeMainlandPhone(input); got != want {
			t.Fatalf("normalizeMainlandPhone(%q)=%q want %q", input, got, want)
		}
	}
}

func TestHashPhoneRequiresSecret(t *testing.T) {
	if got := hashPhone("13800138000", ""); got != "" {
		t.Fatalf("hashPhone without secret=%q want empty", got)
	}
	if got := hashPhone("13800138000", "secret"); len(got) != 64 {
		t.Fatalf("hashPhone length=%d want 64", len(got))
	}
}

func TestAccountPhoneCipherRoundTrip(t *testing.T) {
	phone := "13800138000"
	ciphertext, err := encryptAccountPhoneNumberWithSecret(phone, "unit-test-secret")
	if err != nil {
		t.Fatalf("encryptAccountPhoneNumberWithSecret failed: %v", err)
	}
	if ciphertext == "" || !strings.HasPrefix(ciphertext, "v1:") {
		t.Fatalf("ciphertext = %q, want v1 payload", ciphertext)
	}
	if strings.Contains(ciphertext, phone) {
		t.Fatalf("ciphertext leaked phone number: %q", ciphertext)
	}
	plain, err := decryptAccountPhoneNumberWithSecret(ciphertext, "unit-test-secret")
	if err != nil {
		t.Fatalf("decryptAccountPhoneNumberWithSecret failed: %v", err)
	}
	if plain != phone {
		t.Fatalf("plain = %q, want %q", plain, phone)
	}
	if _, err := decryptAccountPhoneNumberWithSecret(ciphertext, "wrong-secret"); err == nil {
		t.Fatalf("decrypt with wrong secret succeeded")
	}
}

func TestAccountPhoneCipherRequiresSecret(t *testing.T) {
	if _, err := encryptAccountPhoneNumberWithSecret("13800138000", ""); !errors.Is(err, errAccountPhoneSecretMissing) {
		t.Fatalf("encrypt missing secret err = %v, want errAccountPhoneSecretMissing", err)
	}
	if _, err := decryptAccountPhoneNumberWithSecret("v1:abc", ""); !errors.Is(err, errAccountPhoneSecretMissing) {
		t.Fatalf("decrypt missing secret err = %v, want errAccountPhoneSecretMissing", err)
	}
}

func TestAccountPhoneHashForSearch(t *testing.T) {
	t.Setenv("APP_SECRET", "unit-test-secret")
	hash := accountPhoneHashForSearch("+86 138-0013-8000")
	if hash == "" {
		t.Fatalf("accountPhoneHashForSearch returned empty hash")
	}
	if strings.Contains(hash, "13800138000") {
		t.Fatalf("search hash leaked phone number: %q", hash)
	}
	if hash != hashPhone("13800138000", "unit-test-secret") {
		t.Fatalf("search hash mismatch")
	}
	if got := accountPhoneHashForSearch("not-a-phone"); got != "" {
		t.Fatalf("invalid phone search hash = %q, want empty", got)
	}
}

func TestMergeEffectiveEntitlementsTreatsExpiredPaidAsFree(t *testing.T) {
	sourceExpireAt := int64(3000)
	tier, expireAt := mergeEffectiveEntitlements(TierFree, nil, TierPlus, &sourceExpireAt)
	if tier != TierPlus || expireAt == nil || *expireAt != sourceExpireAt {
		t.Fatalf("merged tier=%s expire=%v, want plus %d", tier, expireAt, sourceExpireAt)
	}
}

func TestMergeEffectiveEntitlementsKeepsHigherActiveTier(t *testing.T) {
	targetExpireAt := int64(2000)
	sourceExpireAt := int64(5000)
	tier, expireAt := mergeEffectiveEntitlements(TierPro, &targetExpireAt, TierPlus, &sourceExpireAt)
	if tier != TierPro || expireAt == nil || *expireAt != targetExpireAt {
		t.Fatalf("merged tier=%s expire=%v, want pro %d", tier, expireAt, targetExpireAt)
	}
}

func TestMergeEffectiveEntitlementsExtendsSameTier(t *testing.T) {
	targetExpireAt := int64(2000)
	sourceExpireAt := int64(5000)
	tier, expireAt := mergeEffectiveEntitlements(TierPlus, &targetExpireAt, TierPlus, &sourceExpireAt)
	if tier != TierPlus || expireAt == nil || *expireAt != sourceExpireAt {
		t.Fatalf("merged tier=%s expire=%v, want plus %d", tier, expireAt, sourceExpireAt)
	}
}

func TestRemainingPlusQuotaCompensationPreservesCoveredPlusValue(t *testing.T) {
	shanghai := time.FixedZone("Asia/Shanghai", 8*60*60)
	now := time.Date(2026, 6, 12, 10, 0, 0, 0, shanghai).UnixMilli()
	expireAt := time.Date(2026, 6, 15, 10, 0, 0, 0, shanghai).UnixMilli()

	got := remainingPlusQuotaCompensation(7, &expireAt, now, shanghai)
	want := (tierLimits[TierPlus] - 7) + 3*tierLimits[TierPlus]
	if got != want {
		t.Fatalf("compensation=%d, want %d", got, want)
	}
}

func TestRemainingPlusQuotaCompensationClampsOverusedToday(t *testing.T) {
	shanghai := time.FixedZone("Asia/Shanghai", 8*60*60)
	now := time.Date(2026, 6, 12, 10, 0, 0, 0, shanghai).UnixMilli()
	expireAt := time.Date(2026, 6, 13, 10, 0, 0, 0, shanghai).UnixMilli()

	got := remainingPlusQuotaCompensation(30, &expireAt, now, shanghai)
	if got != tierLimits[TierPlus] {
		t.Fatalf("compensation=%d, want one full future day %d", got, tierLimits[TierPlus])
	}
}

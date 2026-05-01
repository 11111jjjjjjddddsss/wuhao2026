package app

import (
	"database/sql"
	"testing"
)

func TestQuotaBusinessConstantsMatchCurrentRules(t *testing.T) {
	if plusTierPrice != 19.9 {
		t.Fatalf("plus renew price mismatch: %v", plusTierPrice)
	}
	if proTierPrice != 29.9 {
		t.Fatalf("pro renew price mismatch: %v", proTierPrice)
	}
	if topupPackPrice != 6.0 {
		t.Fatalf("topup pack price mismatch: %v", topupPackPrice)
	}
	if topupPackActiveLimit != 1 {
		t.Fatalf("topup active limit mismatch: %d", topupPackActiveLimit)
	}
}

func TestEffectiveTierFromRowExpiresPaidTier(t *testing.T) {
	now := int64(1_700_000_000_000)

	tier, expireAt, err := effectiveTierFromRow(
		sql.NullString{String: string(TierPlus), Valid: true},
		sql.NullInt64{Int64: now - 1, Valid: true},
		TierFree,
		now,
	)
	if err != nil {
		t.Fatalf("effective tier failed: %v", err)
	}
	if tier != TierFree {
		t.Fatalf("expired plus should become free, got %s", tier)
	}
	if expireAt != nil {
		t.Fatalf("expired tier should not expose active expireAt, got %v", *expireAt)
	}
}

func TestEffectiveTierFromRowKeepsActivePaidTier(t *testing.T) {
	now := int64(1_700_000_000_000)

	tier, expireAt, err := effectiveTierFromRow(
		sql.NullString{String: string(TierPro), Valid: true},
		sql.NullInt64{Int64: now + 1, Valid: true},
		TierFree,
		now,
	)
	if err != nil {
		t.Fatalf("effective tier failed: %v", err)
	}
	if tier != TierPro {
		t.Fatalf("active pro should stay pro, got %s", tier)
	}
	if expireAt == nil || *expireAt != now+1 {
		t.Fatalf("active tier expireAt mismatch: %v", expireAt)
	}
}

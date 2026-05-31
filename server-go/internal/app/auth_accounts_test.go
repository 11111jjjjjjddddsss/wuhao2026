package app

import "testing"

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

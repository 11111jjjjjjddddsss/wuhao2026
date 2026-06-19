package app

import (
	"reflect"
	"testing"
)

func TestAdminSupportMessageConvertsSupportImageURLsToSameOriginPaths(t *testing.T) {
	t.Setenv("BASE_PUBLIC_URL", "https://api.nongjiqiancha.cn")
	message := SupportMessage{
		ID:         1,
		UserID:     "acct_test",
		SenderType: "user",
		Body:       "看图",
		ImageURLs: []string{
			"https://api.nongjiqiancha.cn/uploads/support/a.jpg",
			"/uploads/support/b.jpg",
			"https://evil.example/uploads/support/c.jpg",
			"https://api.nongjiqiancha.cn/uploads/plain.jpg",
			"https://api.nongjiqiancha.cn/uploads/support/d.jpg?x=1",
		},
	}

	got := adminSupportMessageFromSupport(message, true).ImageURLs
	want := []string{"/uploads/support/a.jpg", "/uploads/support/b.jpg"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("admin support image urls = %#v, want %#v", got, want)
	}
}

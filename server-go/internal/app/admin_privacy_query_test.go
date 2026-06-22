package app

import (
	"context"
	"fmt"
	"strings"
	"testing"
	"time"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func newAdminPrivacySQLMock(t *testing.T, matcher func(expected string, actual string) error) (*Store, sqlmock.Sqlmock, func()) {
	t.Helper()
	db, mock, err := sqlmock.New(sqlmock.QueryMatcherOption(sqlmock.QueryMatcherFunc(matcher)))
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	cleanup := func() {
		mock.ExpectClose()
		if err := db.Close(); err != nil {
			t.Fatalf("close sqlmock db: %v", err)
		}
		if err := mock.ExpectationsWereMet(); err != nil {
			t.Fatalf("unmet sql expectations: %v", err)
		}
	}
	return &Store{db: db, shanghai: time.FixedZone("Asia/Shanghai", 8*3600)}, mock, cleanup
}

func TestAdminUserSearchRespectsPhoneHashSearchPermission(t *testing.T) {
	t.Setenv("APP_SECRET", "unit-test-secret")
	rows := sqlmock.NewRows([]string{"user_id"})

	store, mock, cleanup := newAdminPrivacySQLMock(t, func(expected string, actual string) error {
		if strings.Contains(expected, "without-phone-hash") && strings.Contains(actual, "phone_hash = ?") {
			return fmt.Errorf("readonly user search unexpectedly includes phone_hash predicate: %s", actual)
		}
		if strings.Contains(expected, "with-phone-hash") && !strings.Contains(actual, "phone_hash = ?") {
			return fmt.Errorf("authorized user search should include phone_hash predicate: %s", actual)
		}
		return nil
	})
	defer cleanup()

	mock.ExpectQuery("without-phone-hash").
		WithArgs(sqlmock.AnyArg(), sqlmock.AnyArg(), "%13800138000%", "%13800138000%", 10).
		WillReturnRows(rows)
	if _, err := store.ListAdminUsers(context.Background(), AdminUserQuery{
		Query:                "13800138000",
		DayCN:                "20260619",
		Limit:                10,
		NowMs:                1700000000000,
		SinceMs:              1699990000000,
		AllowPhoneHashSearch: false,
	}); err != nil {
		t.Fatalf("ListAdminUsers without hash permission failed: %v", err)
	}

	mock.ExpectQuery("with-phone-hash").
		WithArgs(sqlmock.AnyArg(), sqlmock.AnyArg(), "%13800138000%", "%13800138000%", sqlmock.AnyArg(), 10).
		WillReturnRows(sqlmock.NewRows([]string{"user_id"}))
	if _, err := store.ListAdminUsers(context.Background(), AdminUserQuery{
		Query:                "13800138000",
		DayCN:                "20260619",
		Limit:                10,
		NowMs:                1700000000000,
		SinceMs:              1699990000000,
		AllowPhoneHashSearch: true,
	}); err != nil {
		t.Fatalf("ListAdminUsers with hash permission failed: %v", err)
	}
}

func TestSupportConversationSearchRespectsBodyAndPhoneHashPermissions(t *testing.T) {
	t.Setenv("APP_SECRET", "unit-test-secret")
	store, mock, cleanup := newAdminPrivacySQLMock(t, func(expected string, actual string) error {
		if strings.Contains(expected, "without-sensitive-search") {
			if strings.Contains(actual, "body LIKE ?") || strings.Contains(actual, "phone_hash = ?") {
				return fmt.Errorf("readonly support search unexpectedly includes sensitive predicates: %s", actual)
			}
		}
		if strings.Contains(expected, "with-sensitive-search") {
			if !strings.Contains(actual, "body LIKE ?") || !strings.Contains(actual, "phone_hash = ?") {
				return fmt.Errorf("authorized support search should include body and phone_hash predicates: %s", actual)
			}
		}
		return nil
	})
	defer cleanup()

	mock.ExpectQuery("without-sensitive-search").
		WithArgs(int64(1699990000000), "%13800138000%", "%13800138000%", 10).
		WillReturnRows(sqlmock.NewRows([]string{"user_id"}))
	if _, err := store.ListSupportConversations(context.Background(), SupportConversationQuery{
		SinceMs:              1699990000000,
		Limit:                10,
		Query:                "13800138000",
		AllowPhoneHashSearch: false,
		AllowBodySearch:      false,
	}); err != nil {
		t.Fatalf("ListSupportConversations without sensitive permissions failed: %v", err)
	}

	mock.ExpectQuery("with-sensitive-search").
		WithArgs(int64(1699990000000), "%13800138000%", "%13800138000%", "%13800138000%", sqlmock.AnyArg(), 10).
		WillReturnRows(sqlmock.NewRows([]string{"user_id"}))
	if _, err := store.ListSupportConversations(context.Background(), SupportConversationQuery{
		SinceMs:              1699990000000,
		Limit:                10,
		Query:                "13800138000",
		AllowPhoneHashSearch: true,
		AllowBodySearch:      true,
	}); err != nil {
		t.Fatalf("ListSupportConversations with sensitive permissions failed: %v", err)
	}
}

func TestAdminOwnerInheritsSensitiveVisibility(t *testing.T) {
	if !adminCanViewAccountPhone("owner") {
		t.Fatal("owner should be allowed to view full account phone")
	}
	if !adminCanSearchAccountPhone("owner") {
		t.Fatal("owner should be allowed to search by full account phone")
	}
	if !adminCanViewSupportMessageBody("owner") {
		t.Fatal("owner should be allowed to view support message body")
	}
	if !adminCanViewSupportConversationNote("owner") {
		t.Fatal("owner should be allowed to view support conversation note")
	}
	if !adminCanViewChatRoundExcerpts("owner") {
		t.Fatal("owner should be allowed to view chat round excerpts")
	}
}

func TestAdminListEndpointsExposeFullPhoneNumbersForOperatorRoles(t *testing.T) {
	maskedRoles := []string{"", "viewer", "ops_readonly", "auditor", "content_ops", "release_ops"}
	for _, role := range maskedRoles {
		t.Run(role, func(t *testing.T) {
			if adminCanViewAccountPhoneInUserList(role) {
				t.Fatalf("user list should not expose full phone for role %q", role)
			}
			if adminCanViewAccountPhoneInSupportConversationList(role) {
				t.Fatalf("support conversation list should not expose full phone for role %q", role)
			}
		})
	}
	operatorRoles := []string{"support", "finance_ops", "owner"}
	for _, role := range operatorRoles {
		t.Run(role, func(t *testing.T) {
			if !adminCanViewAccountPhoneInUserList(role) {
				t.Fatalf("user list should expose full phone for operator role %q", role)
			}
			if !adminCanViewAccountPhoneInSupportConversationList(role) {
				t.Fatalf("support conversation list should expose full phone for operator role %q", role)
			}
		})
	}
	if !adminCanViewAccountPhone("owner") {
		t.Fatal("owner should still be allowed to view full account phone on audited detail endpoints")
	}
	if !adminCanSearchAccountPhone("owner") {
		t.Fatal("owner should still be allowed to search by full account phone hash")
	}
}

func TestScanAdminUserListEntryNeedsReplyRequiresOpenConversation(t *testing.T) {
	cases := []struct {
		name               string
		latestSender       any
		conversationStatus any
		wantNeedsReply     bool
	}{
		{name: "open user latest", latestSender: "user", conversationStatus: "open", wantNeedsReply: true},
		{name: "closed user latest", latestSender: "user", conversationStatus: "closed", wantNeedsReply: false},
		{name: "replied user latest", latestSender: "user", conversationStatus: "replied", wantNeedsReply: false},
		{name: "legacy missing status falls back to user latest", latestSender: "user", conversationStatus: nil, wantNeedsReply: true},
		{name: "open admin latest", latestSender: "admin", conversationStatus: "open", wantNeedsReply: false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			db, mock, err := sqlmock.New()
			if err != nil {
				t.Fatalf("sqlmock.New failed: %v", err)
			}
			rows := sqlmock.NewRows([]string{
				"user_id",
				"phone_mask",
				"phone_ciphertext",
				"created_at",
				"updated_at",
				"last_login_at",
				"tier",
				"tier_expire_at",
				"round_total",
				"last_seen_at",
				"last_region",
				"last_region_source",
				"last_region_reliability",
				"active_sessions",
				"error_count_24h",
				"support_message_count",
				"latest_support_sender",
				"support_conversation_status",
			}).AddRow(
				"acct_demo",
				"138****8000",
				nil,
				int64(1700000000000),
				int64(1700000000000),
				nil,
				nil,
				nil,
				int64(0),
				nil,
				nil,
				nil,
				nil,
				int64(0),
				int64(0),
				int64(1),
				tc.latestSender,
				tc.conversationStatus,
			)
			mock.ExpectQuery("SELECT").WillReturnRows(rows)
			rawRows, err := db.Query("SELECT")
			if err != nil {
				t.Fatalf("query rows: %v", err)
			}
			if !rawRows.Next() {
				t.Fatal("expected one row")
			}
			user, err := scanAdminUserListEntry(rawRows, "20260622", false)
			if err != nil {
				t.Fatalf("scanAdminUserListEntry failed: %v", err)
			}
			if user.SupportNeedsReply != tc.wantNeedsReply {
				t.Fatalf("SupportNeedsReply = %v, want %v", user.SupportNeedsReply, tc.wantNeedsReply)
			}
			if err := rawRows.Err(); err != nil {
				t.Fatalf("rows err: %v", err)
			}
			if err := rawRows.Close(); err != nil {
				t.Fatalf("close rows: %v", err)
			}
			mock.ExpectClose()
			if err := db.Close(); err != nil {
				t.Fatalf("close db: %v", err)
			}
			if err := mock.ExpectationsWereMet(); err != nil {
				t.Fatalf("unmet sql expectations: %v", err)
			}
		})
	}
}

func TestAdminSupportSummaryFromSupportRedactsBodyAndImages(t *testing.T) {
	summary := &SupportSummary{
		UnreadCount: 2,
		LatestMessage: &SupportMessage{
			ID:         12,
			UserID:     "acct_demo",
			SenderType: "user",
			Body:       "我的手机号是 13800138000，礼品卡码是 ABCD-1234。",
			ImageURLs:  []string{"/uploads/support/example.jpg"},
			CreatedAt:  1700000000000,
		},
	}

	redacted := adminSupportSummaryFromSupport(summary, false)
	if redacted == nil || redacted.LatestMessage == nil {
		t.Fatal("redacted support summary should keep latest message metadata")
	}
	if redacted.UnreadCount != 2 {
		t.Fatalf("unexpected unread count: %d", redacted.UnreadCount)
	}
	if redacted.LatestMessage.Body != "" || redacted.LatestMessage.BodyExcerpt != "" {
		t.Fatalf("redacted support summary leaked body: %#v", redacted.LatestMessage)
	}
	if len(redacted.LatestMessage.ImageURLs) != 0 {
		t.Fatalf("redacted support summary leaked image URLs: %#v", redacted.LatestMessage.ImageURLs)
	}
	if !redacted.LatestMessage.BodyRedacted || !redacted.LatestMessage.ImagesRedacted {
		t.Fatalf("redacted flags were not set: %#v", redacted.LatestMessage)
	}

	visible := adminSupportSummaryFromSupport(summary, true)
	if visible == nil || visible.LatestMessage == nil {
		t.Fatal("visible support summary should keep latest message")
	}
	if visible.LatestMessage.Body == "" || visible.LatestMessage.BodyExcerpt == "" {
		t.Fatalf("visible support summary should include body and excerpt: %#v", visible.LatestMessage)
	}
	if len(visible.LatestMessage.ImageURLs) != 1 {
		t.Fatalf("visible support summary should include image URLs: %#v", visible.LatestMessage.ImageURLs)
	}
}

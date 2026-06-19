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

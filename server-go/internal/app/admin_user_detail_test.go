package app

import (
	"context"
	"strings"
	"testing"

	sqlmock "github.com/DATA-DOG/go-sqlmock"
)

func TestListAdminRoundExcerptsIncludesFullTextAndExcerpts(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("sqlmock.New failed: %v", err)
	}
	defer db.Close()
	store := NewStore(db, nil)

	userText := strings.Repeat("用户描述叶片发黄并带图片。", 12)
	assistantText := strings.Repeat("这是后台排障需要查看的完整 AI 回复正文。", 18)
	mock.ExpectQuery("SELECT client_msg_id, user_text, user_images_json, assistant_text").
		WithArgs("acct_admin_detail", 12).
		WillReturnRows(sqlmock.NewRows([]string{
			"client_msg_id",
			"user_text",
			"user_images_json",
			"assistant_text",
			"created_at",
			"region",
			"region_source",
			"region_reliability",
		}).AddRow(
			"msg-1",
			userText,
			`["/uploads/a.jpg","/uploads/b.jpg"]`,
			assistantText,
			int64(1700000000000),
			"河南郑州",
			string(RegionSourceGPS),
			string(RegionReliable),
		))

	rounds, err := store.ListAdminRoundExcerpts(context.Background(), "acct_admin_detail", 12)
	if err != nil {
		t.Fatalf("ListAdminRoundExcerpts failed: %v", err)
	}
	if len(rounds) != 1 {
		t.Fatalf("round count = %d, want 1", len(rounds))
	}
	round := rounds[0]
	if round.UserText != userText {
		t.Fatalf("full user text was not returned")
	}
	if round.AssistantText != assistantText {
		t.Fatalf("full assistant text was not returned")
	}
	if len([]rune(round.UserExcerpt)) != adminExcerptRunes || round.UserExcerpt == round.UserText {
		t.Fatalf("user excerpt not truncated as expected: len=%d", len([]rune(round.UserExcerpt)))
	}
	if len([]rune(round.AssistantExcerpt)) != adminExcerptRunes || round.AssistantExcerpt == round.AssistantText {
		t.Fatalf("assistant excerpt not truncated as expected: len=%d", len([]rune(round.AssistantExcerpt)))
	}
	if !round.HasImages || round.ImageCount != 2 {
		t.Fatalf("image metadata = has:%v count:%d, want has true count 2", round.HasImages, round.ImageCount)
	}
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Fatalf("unmet sql expectations: %v", err)
	}
}

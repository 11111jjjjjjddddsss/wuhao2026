package app

import (
	"strings"
	"testing"
	"time"
)

func TestBuildDailyAgriMessagesIncludesRecentHistory(t *testing.T) {
	recent := []DailyAgriCard{
		{
			DateCN: "20260512",
			Items: []DailyAgriCardItem{
				{
					Title:   "小麦赤霉病预警",
					Summary: "江苏发布小麦赤霉病防控提醒，建议抓住晴好天气及时用药。",
					URL:     "https://www.gov.cn/agri/old-1",
					Source:  "中国政府网",
				},
			},
		},
	}

	messages := buildDailyAgriMessages(time.Date(2026, 5, 13, 8, 0, 0, 0, time.FixedZone("Asia/Shanghai", 8*60*60)), recent)
	if len(messages) != 2 {
		t.Fatalf("message count mismatch: %d", len(messages))
	}
	prompt, ok := messages[1].Content.(string)
	if !ok {
		t.Fatalf("expected string prompt, got %#v", messages[1].Content)
	}
	for _, want := range []string{
		"近7天已推送过的今日农情",
		"小麦赤霉病预警",
		"今天不要重复同一链接、同一标题或同一事件",
		"少选空泛会议、一般部署、表态新闻",
	} {
		if !strings.Contains(prompt, want) {
			t.Fatalf("prompt missing %q: %s", want, prompt)
		}
	}
}

func TestParseDailyAgriCardSkipsRecentAndCurrentDuplicates(t *testing.T) {
	recent := []DailyAgriCard{
		{
			DateCN: "20260512",
			Items: []DailyAgriCardItem{
				{Title: "小麦赤霉病预警", URL: "https://www.gov.cn/agri/old-1"},
			},
		},
	}
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "https://www.gov.cn/agri/old-1", SiteName: "中国政府网"},
		{Index: 2, URL: "https://www.gov.cn/agri/old-2", SiteName: "中国政府网"},
		{Index: 3, URL: "https://www.gov.cn/agri/new-1", SiteName: "中国政府网"},
		{Index: 4, URL: "https://www.gov.cn/agri/new-2", SiteName: "中国政府网"},
		{Index: 5, URL: "https://www.gov.cn/agri/new-3", SiteName: "中国政府网"},
		{Index: 6, URL: "https://www.gov.cn/agri/new-4", SiteName: "中国政府网"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"新麦收进度提醒","summary":"华北小麦机收进入集中期，各地提示关注天气窗口，及时组织收割。","source_index":1,"source_name":"中国政府网","published_date":"2026-05-12"},
	    {"title":"小麦赤霉病预警","summary":"多地提醒小麦赤霉病防控窗口，种植户需关注田间湿度变化。","source_index":2,"source_name":"中国政府网","published_date":"2026-05-12"},
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":3,"source_name":"中国政府网","published_date":"2026-05-12"},
	    {"title":"玉米苗情管理提醒","summary":"东北玉米苗期管理继续推进，农户需关注墒情和田间出苗整齐度。","source_index":4,"source_name":"中国政府网","published_date":"2026-05-12"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","source_index":5,"source_name":"中国政府网","published_date":"2026-05-12"},
	    {"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。","source_index":6,"source_name":"中国政府网","published_date":"2026-05-12"}
	  ]
	}`

	card, err := parseDailyAgriCard(content, sources, "20260513", recent)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 3 {
		t.Fatalf("expected 3 items, got %d", len(card.Items))
	}
	gotTitles := []string{card.Items[0].Title, card.Items[1].Title, card.Items[2].Title}
	wantTitles := []string{"玉米苗情管理提醒", "水稻移栽天气提示", "苹果产区降雨关注"}
	for i := range wantTitles {
		if gotTitles[i] != wantTitles[i] {
			t.Fatalf("title[%d] mismatch: got %q want %q", i, gotTitles[i], wantTitles[i])
		}
	}
}

func TestParseDailyAgriCardRequiresFixedCardName(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "https://www.gov.cn/agri/new-1", SiteName: "中国政府网"},
		{Index: 2, URL: "https://www.gov.cn/agri/new-2", SiteName: "中国政府网"},
		{Index: 3, URL: "https://www.gov.cn/agri/new-3", SiteName: "中国政府网"},
	}
	content := `{
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":1,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","source_index":2,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。","source_index":3,"source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	if _, err := parseDailyAgriCard(content, sources, "20260513", nil); err == nil {
		t.Fatalf("expected missing card_name to be rejected")
	}
}

func TestValidateDailyAgriPublishedDateRejectsFutureDate(t *testing.T) {
	if err := validateDailyAgriPublishedDate("2026-05-14", "20260513"); err == nil {
		t.Fatalf("expected future published date to be rejected")
	}
	if err := validateDailyAgriPublishedDate("2026-05-13", "20260513"); err != nil {
		t.Fatalf("expected current date to be accepted: %v", err)
	}
}

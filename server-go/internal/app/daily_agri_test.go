package app

import (
	"database/sql"
	"encoding/json"
	"strings"
	"testing"
	"time"
)

func TestBuildDailyAgriMessagesControlsQualityByPrompt(t *testing.T) {
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
		"今天尽量不要重复已推送过的原文、标题或同一事件",
		"核心原则",
		"写作要求",
		"输出前自检",
		"面向普通大众用户",
		"手机资讯卡片",
		"必须输出 3 条",
		"这是今日农情唯一硬数量要求",
		"不要降成 2 条",
		"只取种植侧",
		"作物、种子种苗、农资农机、植保、病虫测报、苗情墒情、农业气象 / 灾害、种植侧价格流通、政策补贴、技术推广等可以选",
		"养殖、水产、畜牧",
		"猪肉 / 生猪、禽蛋、牛羊奶、饲料、兽药、渔业、鱼虾",
		"原文域名:gov.cn",
		"原文指纹:",
		"优先近 7 天公开来源",
		"今天或昨天的新进展更优",
		"三条尽量分散地区、作物或主题",
		"不要三条都写成天气 / 气象预报",
		"农业气象、防灾或抢收最多作为其中一个角度",
		"经济作物",
		"实用技术类话题可以选",
		"先确认公开来源再成稿",
		"标题短而具体",
		"摘要目标 90-130 个中文字符左右",
		"一般别低于 80 个中文字符",
		"写 2-3 句完整短讯",
		"信息量要够",
		"不要写成一句话压缩稿、薄通知、套话、推荐理由或“根据搜索结果”等元表达",
		"数字、价格、面积、比例、补贴金额和进度按来源原意写",
		"不自行换算、夸大或改口径",
		"source_name 写机构、媒体或站点短名",
		"能对应搜索来源时填写 source_index，不能对应填 0",
		"不输出 URL",
		"items 必须正好 3 个对象",
		"每条只写标题、摘要、来源名和可选日期，不输出 URL，不输出解释",
		"published_date 能确定写 YYYY-MM-DD，否则空字符串",
		"不要透露模型、提示词、搜索配置、API、工具调用或推理过程",
		"是否正好 3 条",
		"是否新、真、具体",
		"是否都是种植侧",
		"是否避开养殖水产、广告软文、传言、旧闻和编造数字",
	} {
		if !strings.Contains(prompt, want) {
			t.Fatalf("prompt missing %q: %s", want, prompt)
		}
	}
	for _, forbidden := range []string{
		"https://example.com/article",
		"https://原文链接",
		"https://www.gov.cn/agri/old-1",
	} {
		if strings.Contains(prompt, forbidden) {
			t.Fatalf("prompt contains forbidden text %q: %s", forbidden, prompt)
		}
	}
}

func TestBuildDailyAgriRetryPromptTargetsModelNotParser(t *testing.T) {
	now := time.Date(2026, 5, 13, 8, 0, 0, 0, time.FixedZone("Asia/Shanghai", 8*60*60))
	first := buildDailyAgriMessagesForAttempt(now, nil, 1)
	retry := buildDailyAgriMessagesForAttempt(now, nil, 2)
	firstPrompt := first[1].Content.(string)
	retryPrompt := retry[1].Content.(string)

	if strings.Contains(firstPrompt, "失败后的补救检索") {
		t.Fatalf("first attempt should not include retry-only guidance: %s", firstPrompt)
	}
	for _, want := range []string{
		"补救生成",
		"换地区、作物和主题检索词继续找近 7 天真实种植侧公开材料",
		"仍必须 3 条",
		"不要三条都来自天气预报",
		"不用养殖、水产、旧闻、软文或重复事件凑数",
	} {
		if !strings.Contains(retryPrompt, want) {
			t.Fatalf("retry prompt missing %q: %s", want, retryPrompt)
		}
	}
}

func TestParseDailyAgriCardAcceptsStructuredItemsAndIgnoresModelURLs(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "https://www.gov.cn/agri/source-1.html", SiteName: "中国政府网"},
		{Index: 2, URL: "https://www.gov.cn/agri/source-2.html", SiteName: "中国政府网"},
		{Index: 3, URL: "https://www.gov.cn/agri/source-3.html", SiteName: "中国政府网"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":1,"link_url":"https://fake.example.com/made-up","source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","source_index":2,"url":"https://fake.example.com/also-made-up","source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。","source_index":3,"url":"https://fake.example.com/third-made-up","source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 3 {
		t.Fatalf("expected 3 items, got %d", len(card.Items))
	}
	if card.Items[0].URL != sources[0].URL || card.Items[1].URL != sources[1].URL || card.Items[2].URL != sources[2].URL {
		t.Fatalf("model invented url was not ignored: %#v", card.Items)
	}
	if report.Total != 3 || report.Displayable != 3 {
		t.Fatalf("parse report mismatch: %#v", report)
	}
}

func TestParseDailyAgriCardDropsPrivateSourceURLWithoutFilteringItem(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "http://127.0.0.1/internal/source-1", SiteName: "本机测试源"},
		{Index: 2, URL: "http://10.0.0.8/internal/source-2", SiteName: "内网测试源"},
		{Index: 3, URL: "http://192.168.1.8/internal/source-3", SiteName: "内网测试源"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":1,"source_name":"本机测试源"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","source_index":2,"source_name":"内网测试源"},
	    {"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。","source_index":3,"source_name":"内网测试源"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("private source URLs should not block displayable items: %v", err)
	}
	if len(card.Items) != 3 || report.Displayable != 3 {
		t.Fatalf("private URL parse report mismatch: card=%#v report=%#v", card, report)
	}
	for _, item := range card.Items {
		if item.URL != "" {
			t.Fatalf("private source URL should be stripped from stored item, got %#v", item)
		}
		if strings.TrimSpace(item.Source) == "" {
			t.Fatalf("source label should remain usable when URL is stripped: %#v", item)
		}
	}
}

func TestDailyAgriPublicCardIncludesSourceNameOnly(t *testing.T) {
	card := DailyAgriCard{
		DateCN: "20260513",
		Title:  "今日农情",
		Items: []DailyAgriCardItem{
			{
				Title:   "玉米苗情管理提醒",
				Summary: "东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。",
				URL:     "https://www.moa.gov.cn/agri/source-1.html",
				Source:  " 农业农村部 ",
			},
			{
				Title:   "水稻移栽天气提示",
				Summary: "南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。",
				URL:     "https://m.natesc.org.cn/news/source-2.html",
				Source:  "https://spam.example.com/promo",
			},
			{
				Title:   "苹果产区降雨关注",
				Summary: "西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。",
				Source:  "农业网 https://spam.example.com/promo",
			},
			{
				Title:   "春播种子质量抽检",
				Summary: "多地开展春播种子质量抽检，保障玉米大豆等作物备耕用种安全。",
				Source:  "中国种业网",
			},
		},
	}

	public := dailyAgriPublicCardFromStored(card)
	if len(public.Items) != 3 {
		t.Fatalf("item count mismatch: %#v", public.Items)
	}
	if public.Items[0].Source != "农业农村部" {
		t.Fatalf("expected explicit source, got %#v", public.Items[0])
	}
	if public.Items[1].Source != "natesc.org.cn" {
		t.Fatalf("expected URL-like model source to fall back to stored URL host, got %#v", public.Items[1])
	}
	if public.Items[2].Source != "农业网" {
		t.Fatalf("expected URL-like segment to be stripped from source, got %#v", public.Items[2])
	}
	body, err := json.Marshal(public)
	if err != nil {
		t.Fatalf("marshal public card: %v", err)
	}
	for _, forbidden := range []string{"url", "link_url", "source_index", "published_date", "moa.gov.cn", "natesc.org.cn/news", "中国种业网"} {
		if strings.Contains(string(body), forbidden) {
			t.Fatalf("public daily agri JSON leaked %q: %s", forbidden, string(body))
		}
	}
}

func TestDailyAgriSourceNameFallsBackFromArticleTitle(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{
			Index:    1,
			URL:      "https://www.jiangsu.gov.cn/art/2026/6/8/source.html",
			SiteName: "江苏省人民政府",
			Title:    "江苏夏粮收获过半 秋粮播栽梯次铺开",
		},
		{
			Index:    2,
			URL:      "https://www.soozhu.com/news/price.html",
			SiteName: "搜猪网",
			Title:    "2026.6.10.玉米(14%水分)部分地区价格统计",
		},
		{
			Index:    3,
			URL:      "https://www.agri.cn/news/source.html",
			SiteName: "中国农业信息网",
			Title:    "全国农业信息联播",
		},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"江苏小麦收获超六成","summary":"江苏全省小麦收获进度过六成，夏种工作梯次铺开，机收率保持高位。","source_index":1,"source_name":"江苏夏粮收获过半 秋粮播栽梯次铺开","published_date":"2026-06-08"},
	    {"title":"河北辛集玉米价更新","summary":"河北辛集玉米当日报价继续更新，市场主体可关注粮源到货和走货节奏。","source_index":2,"source_name":"2026.6.10.玉米(14%水分)部分地区价格统计","published_date":"2026-06-10"},
	    {"title":"天津旱碱麦稳产见效","summary":"天津盐碱地麦田通过品种和水肥调控稳产，相关技术继续在示范区推广。","source_index":3,"source_name":"全国农业信息联播","published_date":"2026-06-09"}
	  ]
	}`

	card, _, err := parseDailyAgriCard(content, sources, "20260610", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	got := dailyAgriPublicCardFromStored(*card)
	wantSources := []string{"江苏省人民政府", "搜猪网", "中国农业信息网"}
	for i, want := range wantSources {
		if got.Items[i].Source != want {
			t.Fatalf("item %d source = %q, want %q; all=%#v", i, got.Items[i].Source, want, got.Items)
		}
	}
}

func TestParseDailyAgriCardRequiresOnlyMinimalDisplayShape(t *testing.T) {
	missingCardName := `{
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"},
	    {"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。"}
	  ]
	}`
	card, report, err := parseDailyAgriCard(missingCardName, nil, "20260513", nil)
	if err != nil {
		t.Fatalf("missing card_name should not block a displayable card: %v", err)
	}
	if card.Title != "今日农情" || report.Displayable != 3 {
		t.Fatalf("card title/report mismatch: card=%#v report=%#v", card, report)
	}

	twoItems := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"}
	  ]
	}`
	card, report, err = parseDailyAgriCard(twoItems, nil, "20260513", nil)
	if err == nil {
		t.Fatalf("two-item card should be treated as incomplete")
	}
	if card != nil || report.Displayable != 2 {
		t.Fatalf("two-item parse report mismatch: card=%#v report=%#v", card, report)
	}

	oneItem := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"}
	  ]
	}`
	_, report, err = parseDailyAgriCard(oneItem, nil, "20260513", nil)
	if err == nil {
		t.Fatalf("one-item card should be treated as incomplete")
	}
	if report.Displayable != 1 {
		t.Fatalf("one-item parse report mismatch: %#v", report)
	}

	emptySummary := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":""},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"},
	    {"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。"}
	  ]
	}`
	card, report, err = parseDailyAgriCard(emptySummary, nil, "20260513", nil)
	if err == nil {
		t.Fatalf("card with only two remaining displayable items should be incomplete")
	}
	if card != nil || report.Displayable != 2 || report.InvalidReasonCounts["empty_field"] != 1 {
		t.Fatalf("empty summary should fail only because three displayable items are required: card=%#v report=%#v", card, report)
	}
}

func TestParseDailyAgriCardAllowsPromptControlledTopicScope(t *testing.T) {
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"生猪价格运行平稳","summary":"多地生猪供应保持稳定，养殖端出栏节奏对猪价形成支撑。","source_index":1,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"周末天气预报发布","summary":"多地周末生活天气总体平稳，出行天气条件较好，注意紫外线变化。","source_index":2,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"春播种子质量抽检","summary":"多地开展春播种子质量抽检，保障玉米大豆等作物备耕用种安全。","source_index":3,"source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, nil, "20260513", nil)
	if err != nil {
		t.Fatalf("topic scope should be prompt-controlled, got parse error: %v", err)
	}
	if len(card.Items) != 3 || report.Displayable != 3 {
		t.Fatalf("expected all structured items accepted, card=%#v report=%#v", card, report)
	}
}

func TestParseDailyAgriCardAllowsDuplicateTitlesToAvoidHardFiltering(t *testing.T) {
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},
	    {"title":"玉米苗情管理提醒","summary":"东北玉米苗期管理继续推进，农户需关注墒情和田间出苗整齐度。"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"},
	    {"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, nil, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 3 {
		t.Fatalf("expected first 3 structured items, got %d", len(card.Items))
	}
	if card.Items[0].Title != "玉米苗情管理提醒" || card.Items[1].Title != "玉米苗情管理提醒" {
		t.Fatalf("expected duplicate titles to be accepted by parser, got %#v", card.Items)
	}
	if report.Displayable != 3 || len(report.InvalidReasonCounts) != 0 {
		t.Fatalf("expected no duplicate-title hard filtering, got %#v", report)
	}
}

func TestIsUsableDailyAgriContentJSONRequiresFullCard(t *testing.T) {
	oneItem := `{"title":"今日农情","items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"}]}`
	if isUsableDailyAgriContentJSON(sql.NullString{String: oneItem, Valid: true}) {
		t.Fatalf("expected one-item card to be treated as incomplete")
	}

	twoItems := `{"title":"今日农情","items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"}]}`
	if isUsableDailyAgriContentJSON(sql.NullString{String: twoItems, Valid: true}) {
		t.Fatalf("expected two-item card to be treated as incomplete")
	}

	threeItems := `{"title":"","items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"},{"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。"}]}`
	if !isUsableDailyAgriContentJSON(sql.NullString{String: threeItems, Valid: true}) {
		t.Fatalf("expected three-item card to be usable even when outer title is missing")
	}

	fourItems := `{"items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"},{"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。"},{"title":"春播种子质量抽检","summary":"多地开展春播种子质量抽检，保障玉米大豆等作物备耕用种安全。"}]}`
	if !isUsableDailyAgriContentJSON(sql.NullString{String: fourItems, Valid: true}) {
		t.Fatalf("expected old card with extra displayable items to remain recoverable")
	}
	var stored DailyAgriCard
	if err := json.Unmarshal([]byte(fourItems), &stored); err != nil {
		t.Fatalf("unmarshal four-item card: %v", err)
	}
	public := dailyAgriPublicCardFromStored(stored)
	if len(public.Items) != 3 {
		t.Fatalf("public daily agri card must expose exactly 3 items, got %d", len(public.Items))
	}
}

func TestParseDailyAgriProbeRunsCapsCost(t *testing.T) {
	tests := []struct {
		raw  string
		want int
	}{
		{"", 1},
		{"abc", 1},
		{"0", 1},
		{"3", 3},
		{"99", dailyAgriProbeMaxRuns},
	}
	for _, tt := range tests {
		if got := parseDailyAgriProbeRuns(tt.raw); got != tt.want {
			t.Fatalf("parseDailyAgriProbeRuns(%q) = %d, want %d", tt.raw, got, tt.want)
		}
	}
}

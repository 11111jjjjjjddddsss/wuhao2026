package app

import (
	"database/sql"
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
		"今天不要重复同一原文、同一标题或同一事件",
		"原文域名:gov.cn",
		"原文指纹:",
		"items 优先输出 3 条成稿内容",
		"确实只有 2 条高质量内容时也可只输出 2 条",
		"不要为了凑满候选而反复扩大检索",
		"少选空泛会议、一般部署、表态新闻",
		"不要把农业新闻只理解成病虫害或政策",
		"只取种植侧",
		"养殖侧全部排除",
		"种子/种苗/种业",
		"品种审定推广",
		"病虫草害预警",
		"普通天气预报",
		"农资供需或价格",
		"来源选择不要固定在少数网站",
		"地方农业信息、市场流通、农资、种业、植保",
		"用户端只展示标题和摘要内容",
		"不要写“点击查看”“来源链接”“打开原文”",
		"不要自拟、改写、补全或猜测任何 URL",
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
			t.Fatalf("prompt leaked raw url %q: %s", forbidden, prompt)
		}
	}
	if strings.Contains(prompt, "至少输出 6 条") || strings.Contains(prompt, "最多输出 8 条") {
		t.Fatalf("prompt still asks for too many candidates: %s", prompt)
	}
	if strings.Contains(prompt, "畜牧水产") {
		t.Fatalf("prompt should not list livestock/aquaculture as a positive topic: %s", prompt)
	}
}

func TestBuildDailyAgriRetryPromptTargetsContentQuality(t *testing.T) {
	now := time.Date(2026, 5, 13, 8, 0, 0, 0, time.FixedZone("Asia/Shanghai", 8*60*60))
	first := buildDailyAgriMessagesForAttempt(now, nil, 1)
	retry := buildDailyAgriMessagesForAttempt(now, nil, 2)
	firstPrompt := first[1].Content.(string)
	retryPrompt := retry[1].Content.(string)

	if strings.Contains(firstPrompt, "失败后的补救检索") {
		t.Fatalf("first attempt should not include retry-only guidance: %s", firstPrompt)
	}
	for _, want := range []string{
		"失败后的补救检索",
		"泛农业、养殖侧、广告味、空泛会议",
		"旧闻或重复事件",
		"种植业 近7天",
		"本卡只做种植，养殖全部排除",
		"用户端不点击链接",
		"优先保证三条内容像新闻、事实清楚、短而好读",
	} {
		if !strings.Contains(retryPrompt, want) {
			t.Fatalf("retry prompt missing %q: %s", want, retryPrompt)
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

	card, report, err := parseDailyAgriCard(content, sources, "20260513", recent)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if report.Total != 6 || report.Accepted != 3 {
		t.Fatalf("rejection report mismatch: %#v", report)
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

	if _, _, err := parseDailyAgriCard(content, sources, "20260513", nil); err == nil {
		t.Fatalf("expected missing card_name to be rejected")
	}
}

func TestParseDailyAgriCardIgnoresModelInventedURLs(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "https://www.gov.cn/agri/source-1.html", SiteName: "中国政府网"},
		{Index: 2, URL: "https://www.gov.cn/agri/source-2.html", SiteName: "中国政府网"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":1,"link_url":"https://fake.example.com/made-up","source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","source_index":2,"url":"https://fake.example.com/also-made-up","source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(card.Items))
	}
	if card.Items[0].URL != sources[0].URL || card.Items[1].URL != sources[1].URL {
		t.Fatalf("model invented url was not ignored: %#v", card.Items)
	}
	if report.Total != 2 || report.Accepted != 2 {
		t.Fatalf("rejection report mismatch: %#v", report)
	}
}

func TestParseDailyAgriCardRepairsMissingSourceIndexByUniqueTitleMatch(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{
			Index:    7,
			Title:    "玉米苗情管理提醒",
			URL:      "https://www.gov.cn/agri/source-7.html",
			SiteName: "中国政府网",
		},
		{
			Index:    8,
			Title:    "水稻移栽天气提示",
			URL:      "https://www.gov.cn/agri/source-8.html",
			SiteName: "中国政府网",
		},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":0,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","source_index":99,"source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(card.Items))
	}
	if card.Items[0].URL != sources[0].URL || card.Items[1].URL != sources[1].URL {
		t.Fatalf("source index repair did not use search source URL: %#v", card.Items)
	}
	if report.ReasonCounts["source_index_missing"] != 0 {
		t.Fatalf("unexpected source index rejection: %#v", report.ReasonCounts)
	}
}

func TestParseDailyAgriCardAllowsAmbiguousSourceWhenContentIsValid(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, Title: "玉米苗情管理提醒", URL: "https://www.gov.cn/agri/source-1.html", SiteName: "中国政府网"},
		{Index: 2, Title: "玉米苗情管理提醒", URL: "https://www.gov.cn/agri/source-2.html", SiteName: "中国政府网"},
		{Index: 3, Title: "水稻移栽天气提示", URL: "https://www.gov.cn/agri/source-3.html", SiteName: "中国政府网"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":0,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","source_index":3,"source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 2 {
		t.Fatalf("expected valid content-only items, got %d", len(card.Items))
	}
	if card.Items[0].URL != "" {
		t.Fatalf("ambiguous source should not attach an arbitrary URL: %#v", card.Items[0])
	}
	if report.Accepted != 2 {
		t.Fatalf("expected both content items accepted, got %#v", report)
	}
}

func TestParseDailyAgriCardKeepsIndexedSourceForInternalTraceOnly(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, Title: "中国农业农村信息网", URL: "https://www.agri.cn/", SiteName: "中国农业信息网"},
		{Index: 2, Title: "玉米苗情管理提醒", URL: "https://www.gov.cn/agri/source-2.html", SiteName: "中国政府网"},
		{Index: 3, Title: "水稻移栽天气提示", URL: "https://www.gov.cn/agri/source-3.html", SiteName: "中国政府网"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":1,"source_name":"中国农业信息网","published_date":"2026-05-13"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","source_index":3,"source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(card.Items))
	}
	if card.Items[0].URL != "https://www.agri.cn" {
		t.Fatalf("expected indexed source kept only for internal trace: %#v", card.Items[0])
	}
	if report.ReasonCounts["low_value_source_url"] != 0 {
		t.Fatalf("URL quality should not reject content-only cards, got %#v", report.ReasonCounts)
	}
}

func TestParseDailyAgriCardDoesNotRejectHomepageLikeSourcesAlone(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "https://www.gov.cn/", SiteName: "中国政府网"},
		{Index: 2, URL: "https://www.gov.cn/index.html", SiteName: "中国政府网"},
		{Index: 3, URL: "https://www.farmer.com.cn/index.shtml", SiteName: "农民日报"},
		{Index: 4, URL: "https://www.farmer.com.cn/wap.shtml", SiteName: "农民日报"},
		{Index: 5, URL: "https://xczx.news.cn/zxyw.htm", SiteName: "新华社"},
		{Index: 6, URL: "https://www.gov.cn/agri/source-3.html", SiteName: "中国政府网"},
		{Index: 7, URL: "https://www.gov.cn/agri/source-4.html", SiteName: "中国政府网"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"春播供种进度加快","summary":"多地春播供种和质量抽检推进，玉米大豆等作物备耕用种保障加强。","source_index":1,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"小麦收获窗口临近","summary":"黄淮麦区陆续进入收获准备期，农机调度和天气窗口成为田间重点。","source_index":2,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"农资配送保障春管","summary":"基层网点加快化肥农药配送，服务春管用肥用药和田间植保需求。","source_index":3,"source_name":"农民日报","published_date":"2026-05-13"},
	    {"title":"果园病害防控提醒","summary":"部分苹果产区降雨增多，果园需关注叶部病害和套袋前田间管理。","source_index":4,"source_name":"农民日报","published_date":"2026-05-13"},
	    {"title":"乡村频道要闻提示","summary":"新华社乡村频道聚合近日报告，关注多地种植生产和农资流通信息。","source_index":5,"source_name":"新华社","published_date":"2026-05-13"},
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":6,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","source_index":7,"source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 3 {
		t.Fatalf("expected 3 valid items, got %d", len(card.Items))
	}
	if report.ReasonCounts["low_value_source_url"] != 0 {
		t.Fatalf("URL shape should not reject content-only cards, got %#v", report.ReasonCounts)
	}
	if card.Items[0].URL != "https://www.gov.cn" {
		t.Fatalf("expected homepage-like source kept only for internal trace: %#v", card.Items[0])
	}
}

func TestParseDailyAgriCardAllowsHTTPSourceForInternalTrace(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "http://www.agri.cn/V20/ZX/qgxxlb_1/202605/t20260513_123456.htm", SiteName: "中国农业信息网"},
		{Index: 2, URL: "http://www.farmer.com.cn/2026/05/13/998877.html", SiteName: "农民日报"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":1,"source_name":"中国农业信息网","published_date":"2026-05-13"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","source_index":2,"source_name":"农民日报","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 2 {
		t.Fatalf("expected 2 http fallback items, got %d", len(card.Items))
	}
	if report.Accepted != 2 {
		t.Fatalf("expected both items accepted, got %#v", report)
	}
}

func TestParseDailyAgriCardSanitizesBlockedSourceHostsOnly(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "https://shop.taobao.com/agri/source-1.html", SiteName: "示例网"},
		{Index: 2, URL: "https://www.cls.cn/subject/1385", SiteName: "财联社"},
		{Index: 3, URL: "https://www.gov.cn/agri/crop-1.html", SiteName: "中国政府网"},
		{Index: 4, URL: "https://www.gov.cn/agri/crop-2.html", SiteName: "中国政府网"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","source_index":1,"source_name":"示例网","published_date":"2026-05-13"},
	    {"title":"春播种子抽检推进","summary":"多地开展春播种子质量抽检，玉米大豆等备耕用种安全继续加强。","source_index":2,"source_name":"财联社","published_date":"2026-05-13"},
	    {"title":"小麦赤霉病预警","summary":"江苏多地进入小麦病害防控窗口，田间用药需关注降雨和适期。","source_index":3,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"化肥供应保障春耕","summary":"主产区化肥供应保持稳定，基层网点加强配送保障春耕用肥。","source_index":4,"source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, _, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 3 {
		t.Fatalf("expected 3 valid content items, got %d", len(card.Items))
	}
	if card.Items[0].URL != "" {
		t.Fatalf("blocked ecommerce URL should be stripped from internal trace: %#v", card.Items[0])
	}
	if card.Items[1].URL != sources[1].URL {
		t.Fatalf("non-ecommerce source should be kept for internal trace: %#v", card.Items[1])
	}
}

func TestParseDailyAgriCardRejectsOutOfScopeLivestockAndAquaculture(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "https://www.gov.cn/agri/livestock-1.html", SiteName: "中国政府网"},
		{Index: 2, URL: "https://www.gov.cn/agri/crop-1.html", SiteName: "中国政府网"},
		{Index: 3, URL: "https://www.gov.cn/agri/crop-2.html", SiteName: "中国政府网"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"生猪价格运行平稳","summary":"多地生猪供应保持稳定，养殖端出栏节奏对猪价形成支撑。","source_index":1,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"小麦赤霉病预警","summary":"江苏多地进入小麦病害防控窗口，田间用药需关注降雨和适期。","source_index":2,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"化肥供应保障春耕","summary":"主产区化肥供应保持稳定，基层网点加强配送保障春耕用肥。","source_index":3,"source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 2 {
		t.Fatalf("expected 2 planting-related items, got %d", len(card.Items))
	}
	if report.ReasonCounts["out_of_scope_topic"] != 1 {
		t.Fatalf("expected out-of-scope rejection, got %#v", report.ReasonCounts)
	}
	for _, item := range card.Items {
		if containsOutOfScopeDailyAgriTopic(strings.ToLower(item.Title + item.Summary + item.Source + item.URL)) {
			t.Fatalf("out-of-scope item leaked into card: %#v", item)
		}
	}
}

func TestParseDailyAgriCardRejectsBroaderBreedingSideTopics(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "https://www.gov.cn/agri/egg-1.html", SiteName: "中国政府网"},
		{Index: 2, URL: "https://www.gov.cn/agri/dairy-1.html", SiteName: "中国政府网"},
		{Index: 3, URL: "https://www.gov.cn/agri/fish-1.html", SiteName: "中国政府网"},
		{Index: 4, URL: "https://www.gov.cn/agri/crop-1.html", SiteName: "中国政府网"},
		{Index: 5, URL: "https://www.gov.cn/agri/crop-2.html", SiteName: "中国政府网"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"鸡蛋供应保持稳定","summary":"蛋鸡存栏和鸡蛋流通保持稳定，市场供应节奏总体正常。","source_index":1,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"奶业生产调度推进","summary":"多地推进奶业生产调度，保障乳制品加工和奶牛养殖稳定。","source_index":2,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"鱼虾养殖技术提示","summary":"水产养殖户需关注鱼虾池塘水质变化，做好投喂和巡塘管理。","source_index":3,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"小麦赤霉病预警","summary":"江苏多地进入小麦病害防控窗口，田间用药需关注降雨和适期。","source_index":4,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"化肥供应保障春耕","summary":"主产区化肥供应保持稳定，基层网点加强配送保障春耕用肥。","source_index":5,"source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 2 {
		t.Fatalf("expected 2 planting-related items, got %d", len(card.Items))
	}
	if report.ReasonCounts["out_of_scope_topic"] != 3 {
		t.Fatalf("expected broader breeding-side rejections, got %#v", report.ReasonCounts)
	}
}

func TestParseDailyAgriCardRejectsPlainWeatherButAllowsAgWeatherRisk(t *testing.T) {
	sources := []DailyAgriSearchSource{
		{Index: 1, URL: "https://www.gov.cn/agri/weather-1.html", SiteName: "中国政府网"},
		{Index: 2, URL: "https://www.gov.cn/agri/crop-weather-1.html", SiteName: "中国政府网"},
		{Index: 3, URL: "https://www.gov.cn/agri/seed-1.html", SiteName: "中国政府网"},
	}
	content := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"周末天气预报发布","summary":"多地周末生活天气总体平稳，出行天气条件较好，注意紫外线变化。","source_index":1,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"小麦干热风防范","summary":"华北麦区需关注干热风风险，灌浆期田间管理应结合墒情及时落实。","source_index":2,"source_name":"中国政府网","published_date":"2026-05-13"},
	    {"title":"春播种子质量抽检","summary":"多地开展春播种子质量抽检，保障玉米大豆等作物备耕用种安全。","source_index":3,"source_name":"中国政府网","published_date":"2026-05-13"}
	  ]
	}`

	card, report, err := parseDailyAgriCard(content, sources, "20260513", nil)
	if err != nil {
		t.Fatalf("parse card: %v", err)
	}
	if len(card.Items) != 2 {
		t.Fatalf("expected 2 ag-weather/seed items, got %d", len(card.Items))
	}
	if report.ReasonCounts["out_of_scope_topic"] != 1 {
		t.Fatalf("expected plain-weather rejection, got %#v", report.ReasonCounts)
	}
	gotTitles := []string{card.Items[0].Title, card.Items[1].Title}
	wantTitles := []string{"小麦干热风防范", "春播种子质量抽检"}
	for i := range wantTitles {
		if gotTitles[i] != wantTitles[i] {
			t.Fatalf("title[%d] mismatch: got %q want %q", i, gotTitles[i], wantTitles[i])
		}
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

func TestIsUsableDailyAgriContentJSONRejectsMalformedOrIncomplete(t *testing.T) {
	if isUsableDailyAgriContentJSON(sql.NullString{String: "{", Valid: true}) {
		t.Fatalf("expected malformed json to be unusable")
	}
	incomplete := `{"title":"今日农情","items":[{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","url":"https://www.gov.cn/agri/new-1","source":"中国政府网","published_date":"2026-05-13"}]}`
	if isUsableDailyAgriContentJSON(sql.NullString{String: incomplete, Valid: true}) {
		t.Fatalf("expected incomplete card to be unusable")
	}
}

func TestIsUsableDailyAgriContentJSONAcceptsTwoReliableItems(t *testing.T) {
	content := `{"title":"今日农情","items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","url":"https://www.gov.cn/agri/new-1","source":"中国政府网","published_date":"2026-05-13"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","url":"https://www.gov.cn/agri/new-2","source":"中国政府网","published_date":"2026-05-13"}]}`
	if !isUsableDailyAgriContentJSON(sql.NullString{String: content, Valid: true}) {
		t.Fatalf("expected two-item card to be usable")
	}
}

func TestIsUsableDailyAgriContentJSONAcceptsItemsWithoutDates(t *testing.T) {
	content := `{"title":"今日农情","items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"}]}`
	if !isUsableDailyAgriContentJSON(sql.NullString{String: content, Valid: true}) {
		t.Fatalf("expected date-less two-item card to be usable")
	}
}

func TestIsUsableDailyAgriContentJSONAcceptsHTTPSourceForInternalTrace(t *testing.T) {
	content := `{"title":"今日农情","items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","url":"http://www.agri.cn/V20/ZX/qgxxlb_1/202605/t20260513_123456.htm","source":"中国农业信息网","published_date":"2026-05-13"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","url":"http://www.farmer.com.cn/2026/05/13/998877.html","source":"农民日报","published_date":"2026-05-13"}]}`
	if !isUsableDailyAgriContentJSON(sql.NullString{String: content, Valid: true}) {
		t.Fatalf("expected http internal trace card to be usable")
	}
}

func TestIsUsableDailyAgriContentJSONAcceptsHTTPHomepagesForContentOnlyCard(t *testing.T) {
	content := `{"title":"今日农情","items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","url":"http://www.agri.cn/","source":"中国农业信息网","published_date":"2026-05-13"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","url":"http://www.farmer.com.cn/index.shtml","source":"农民日报","published_date":"2026-05-13"}]}`
	if !isUsableDailyAgriContentJSON(sql.NullString{String: content, Valid: true}) {
		t.Fatalf("expected homepage-like internal trace card to be usable")
	}
}

func TestIsUsableDailyAgriContentJSONAcceptsCompleteCard(t *testing.T) {
	content := `{"title":"今日农情","items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。","url":"https://www.gov.cn/agri/new-1","source":"中国政府网","published_date":"2026-05-13"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。","url":"https://www.gov.cn/agri/new-2","source":"中国政府网","published_date":"2026-05-13"},{"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。","url":"https://www.gov.cn/agri/new-3","source":"中国政府网","published_date":"2026-05-13"}]}`
	if !isUsableDailyAgriContentJSON(sql.NullString{String: content, Valid: true}) {
		t.Fatalf("expected complete card to be usable")
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

package app

import (
	"database/sql"
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
		"published_date 能确认近 7 天发布日期",
		"不要因为日期字段不确定就只输出 2 条",
		"原文域名:gov.cn",
		"原文指纹:",
		"items 必须正好 3 条",
		"最终不要输出 2 条",
		"质量不完美时优先选择",
		"有公开来源、有具体事实、和种植生产/农资/农时有关",
		"最大限度全网宽搜",
		"不限定固定网站",
		"交叉使用“近7天 / 今日 / 最新 / 地区 / 作物 / 品类 / 农资 / 农时 / 预警 / 价格”",
		"本卡只取种植侧；养殖、水产不要",
		"不要选择畜牧、水产、养殖",
		"如果搜索结果主要是这些方向，请换关键词继续找种植",
		"种子/种苗/种业",
		"病虫草害/植保",
		"农产品产地价格/批发流通/农资供需",
		"普通天气预报",
		"标题 10-14 个中文字符左右，最多不超过 16 个中文字符",
		"尽量包含地区、作物、品类或事件中的至少一个具体对象",
		"不要为了压缩字数生造生硬简称或怪词",
		"约 3 行体量",
		"必须先确认有公开来源材料再成稿",
		"用户端只展示标题、摘要和来源名称",
		"source_name 必须写机构、媒体或站点短名",
		"不要写文章标题、网页标题、频道页、站点口号",
		"不要输出 link_url / url 字段",
		"不要自拟或猜测 URL",
		"published_date 能确定才写 YYYY-MM-DD，不能确定就写空字符串",
		"近 7 天发布 / 更新优先",
	} {
		if !strings.Contains(prompt, want) {
			t.Fatalf("prompt missing %q: %s", want, prompt)
		}
	}
	for _, forbidden := range []string{
		"https://example.com/article",
		"https://原文链接",
		"https://www.gov.cn/agri/old-1",
		"确实只有 2 条高质量内容时也可只输出 2 条",
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
		"失败后的补救检索",
		"泛农业、养殖水产、广告味、空泛会议",
		"旧闻、重复事件、代码块或不够像新闻",
		"种植业 近7天 今日 最新",
		"本卡只做种植侧",
		"养殖、水产不要",
		"如果结果偏养殖水产，就继续换词找种植侧",
		"用户端不点击链接",
		"优先保证三条内容像新闻、事实清楚、来源清楚、短而好读",
		"不要因为日期字段、来源层级或标题不够完美就只给 2 条",
	} {
		if !strings.Contains(retryPrompt, want) {
			t.Fatalf("retry prompt missing %q: %s", want, retryPrompt)
		}
	}
}

func TestParseDailyAgriCardRequiresThreeStructuredItems(t *testing.T) {
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
	if report.Total != 3 || report.Accepted != 3 {
		t.Fatalf("rejection report mismatch: %#v", report)
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
			},
			{
				Title:   "苹果产区降雨关注",
				Summary: "西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。",
				URL:     "https://www.farmer.com.cn/news/source-3.html",
				Source:  "https://spam.example.com/promo",
			},
			{
				Title:   "春播种子质量抽检",
				Summary: "多地开展春播种子质量抽检，保障玉米大豆等作物备耕用种安全。",
				Source:  "农业网 https://spam.example.com/promo",
			},
		},
	}

	public := dailyAgriPublicCardFromStored(card)
	if len(public.Items) != 4 {
		t.Fatalf("item count mismatch: %#v", public.Items)
	}
	if public.Items[0].Source != "农业农村部" {
		t.Fatalf("expected explicit source, got %#v", public.Items[0])
	}
	if public.Items[1].Source != "natesc.org.cn" {
		t.Fatalf("expected source host fallback without URL, got %#v", public.Items[1])
	}
	if public.Items[2].Source != "farmer.com.cn" {
		t.Fatalf("expected URL-like model source to fall back to stored URL host, got %#v", public.Items[2])
	}
	if public.Items[3].Source != "农业网" {
		t.Fatalf("expected URL-like segment to be stripped from source, got %#v", public.Items[3])
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
	if card.Title != "今日农情" || report.Accepted != 3 {
		t.Fatalf("card title/report mismatch: card=%#v report=%#v", card, report)
	}

	twoItems := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"}
	  ]
	}`
	if _, _, err := parseDailyAgriCard(twoItems, nil, "20260513", nil); err == nil {
		t.Fatalf("expected two-item card to be rejected")
	}

	emptySummary := `{
	  "card_name": "今日农情",
	  "items": [
	    {"title":"玉米苗情管理提醒","summary":""},
	    {"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"},
	    {"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。"}
	  ]
	}`
	if _, _, err := parseDailyAgriCard(emptySummary, nil, "20260513", nil); err == nil {
		t.Fatalf("expected empty summary card to be rejected")
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
	if len(card.Items) != 3 || report.Accepted != 3 {
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
	if report.Accepted != 3 || len(report.ReasonCounts) != 0 {
		t.Fatalf("expected no duplicate-title hard rejection, got %#v", report)
	}
}

func TestIsUsableDailyAgriContentJSONRequiresThreeItems(t *testing.T) {
	twoItems := `{"title":"今日农情","items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"}]}`
	if isUsableDailyAgriContentJSON(sql.NullString{String: twoItems, Valid: true}) {
		t.Fatalf("expected two-item card to be unusable")
	}

	threeItems := `{"title":"","items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"},{"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。"}]}`
	if !isUsableDailyAgriContentJSON(sql.NullString{String: threeItems, Valid: true}) {
		t.Fatalf("expected three-item card to be usable even when outer title is missing")
	}

	fourItems := `{"items":[{"title":"玉米苗情管理提醒","summary":"东北部分产区进入玉米苗期管理阶段，建议查看缺苗断垄和墒情。"},{"title":"水稻移栽天气提示","summary":"南方部分稻区迎来移栽窗口，低温阴雨地区需关注返青和排水。"},{"title":"苹果产区降雨关注","summary":"西北苹果产区需关注降雨和病害风险，及时巡园查看叶片果面。"},{"title":"春播种子质量抽检","summary":"多地开展春播种子质量抽检，保障玉米大豆等作物备耕用种安全。"}]}`
	if !isUsableDailyAgriContentJSON(sql.NullString{String: fourItems, Valid: true}) {
		t.Fatalf("expected card with extra displayable items to be usable")
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

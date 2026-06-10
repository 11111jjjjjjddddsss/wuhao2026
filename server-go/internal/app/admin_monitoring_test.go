package app

import (
	"strings"
	"testing"
)

func TestAdminMonitoringActionItemsContract(t *testing.T) {
	report := AdminMonitoring{
		Queues: AdminMonitoringQueues{
			UnreadyDependencyCount: 1,
			AppErrors:              10,
			SupportNeedsReply:      2,
			DailyAgriStatus:        "failed",
			GiftCardFailedAttempts: 3,
			AuditFailures:          4,
			AuthFailures:           5,
			CrashReports:           1,
			AppUpdate: AdminMonitoringAppUpdate{
				ConfigValid:               true,
				DownloadArtifactsComplete: false,
			},
		},
	}
	items := buildAdminMonitoringActionItems(report)
	if len(items) == 0 {
		t.Fatalf("expected action items")
	}
	validLevels := map[string]bool{"ok": true, "warn": true, "bad": true}
	for _, item := range items {
		if !validLevels[item.Level] {
			t.Fatalf("unexpected action level %q for %#v", item.Level, item)
		}
		if item.Route != "" && !adminRouteAllowed("owner", item.Route) {
			t.Fatalf("unexpected action route %q for %#v", item.Route, item)
		}
	}
	if !hasAdminMonitoringActionRoute(items, "health") ||
		!hasAdminMonitoringActionRoute(items, "app-logs") ||
		!hasAdminMonitoringActionRoute(items, "support") ||
		!hasAdminMonitoringActionRoute(items, "today-agri") ||
		!hasAdminMonitoringActionRoute(items, "gift-cards") ||
		!hasAdminMonitoringActionRoute(items, "audit") ||
		!hasAdminMonitoringActionRoute(items, "app-update") {
		t.Fatalf("missing expected action routes: %#v", items)
	}
	if !hasAdminMonitoringActionTitle(items, "登录失败需要看") ||
		!hasAdminMonitoringActionTitle(items, "App 闪退补报") {
		t.Fatalf("missing auth/crash action items: %#v", items)
	}
}

func TestAdminMonitoringCapabilitiesContract(t *testing.T) {
	capabilities := buildAdminMonitoringCapabilities()
	if len(capabilities) == 0 {
		t.Fatalf("expected capabilities")
	}
	validStatuses := map[string]bool{"ready": true, "partial": true, "planned": true}
	for _, item := range capabilities {
		if !validStatuses[item.Status] {
			t.Fatalf("unexpected capability status %q for %#v", item.Status, item)
		}
		if item.Route != "" && !adminRouteAllowed("owner", item.Route) {
			t.Fatalf("unexpected capability route %q for %#v", item.Route, item)
		}
	}
	if !hasAdminMonitoringCapabilityStatus(capabilities, "产品洞察", "partial") {
		t.Fatalf("product insights capability should be partial after first aggregate dashboard: %#v", capabilities)
	}
	if !hasAdminMonitoringCapabilityStatus(capabilities, "SLS 告警", "partial") {
		t.Fatalf("SLS alerts capability should be partial after AlertHub minimum alerts: %#v", capabilities)
	}
}

func TestAdminMonitoringModelUsagePolicyContract(t *testing.T) {
	rows := buildAdminMonitoringModelUsagePolicy()
	if len(rows) != 3 {
		t.Fatalf("model usage row count = %d, want 3: %#v", len(rows), rows)
	}
	if !hasAdminMonitoringModelPolicy(rows, "主聊天问诊", mainChatModel, mainChatSearchStrategy, false) {
		t.Fatalf("missing main chat model usage policy: %#v", rows)
	}
	if !hasAdminMonitoringModelPolicy(rows, "记忆文档摘要", summaryExtractionModel, "", false) {
		t.Fatalf("missing summary model usage policy: %#v", rows)
	}
	if !hasAdminMonitoringModelPolicy(rows, "今日农情", defaultDailyAgriCardModel, dailyAgriSearchStrategy, true) {
		t.Fatalf("missing daily agri model usage policy: %#v", rows)
	}
	for _, row := range rows {
		if !row.ThinkingDisabled {
			t.Fatalf("thinking should be disabled for admin model policy row: %#v", row)
		}
		if row.Model == "qwen-turbo" {
			t.Fatalf("qwen-turbo should not appear in current backend model policy: %#v", rows)
		}
	}
	dailyRow := findAdminMonitoringModelPolicy(rows, "今日农情")
	if dailyRow == nil {
		t.Fatalf("missing daily agri model usage policy: %#v", rows)
	}
	if dailyRow.Protocol != "DashScope text-generation 非流式" {
		t.Fatalf("daily agri protocol = %q", dailyRow.Protocol)
	}
	if !dailyRow.ThinkingDisabled {
		t.Fatalf("daily agri thinking should be disabled for default qwen-plus path: %#v", dailyRow)
	}
	if !strings.Contains(dailyRow.CostNote, "当前生产默认链") {
		t.Fatalf("daily agri cost note should mention current default path: %#v", dailyRow)
	}
}

func TestAdminMonitoringSummaryModelPolicyUsesFixedQwenPlus(t *testing.T) {
	rows := buildAdminMonitoringModelUsagePolicy()
	summaryRow := findAdminMonitoringModelPolicy(rows, "记忆文档摘要")
	if summaryRow == nil {
		t.Fatalf("missing summary model usage policy: %#v", rows)
	}
	if summaryRow.Model != summaryExtractionModel {
		t.Fatalf("summary model label = %q", summaryRow.Model)
	}
	if !summaryRow.ThinkingDisabled {
		t.Fatalf("summary thinking should stay disabled: %#v", summaryRow)
	}
}

func TestAdminMonitoringDailyAgriUsesFixedQwenPlus(t *testing.T) {
	rows := buildAdminMonitoringModelUsagePolicy()
	dailyRow := findAdminMonitoringModelPolicy(rows, "今日农情")
	if dailyRow == nil {
		t.Fatalf("missing daily agri model usage policy: %#v", rows)
	}
	if dailyRow.Model != defaultDailyAgriCardModel {
		t.Fatalf("daily agri model = %q", dailyRow.Model)
	}
	if dailyRow.Protocol != "DashScope text-generation 非流式" {
		t.Fatalf("daily agri protocol = %q", dailyRow.Protocol)
	}
	if !dailyRow.ThinkingDisabled {
		t.Fatalf("daily agri thinking should be disabled: %#v", dailyRow)
	}
}

func TestAdminMonitoringFiltersRoutesByRole(t *testing.T) {
	items := []AdminMonitoringActionItem{
		{Title: "support", Level: "warn", Route: "support"},
		{Title: "gift", Level: "warn", Route: "gift-cards"},
		{Title: "audit", Level: "bad", Route: "audit"},
	}
	got := filterAdminMonitoringActionRoutes(items, "support")
	if got[0].Route != "support" {
		t.Fatalf("support route = %q, want support", got[0].Route)
	}
	if got[1].Route != "" || got[2].Route != "" {
		t.Fatalf("unauthorized routes were not cleared: %#v", got)
	}
}

func TestAdminCanViewAccountPhone(t *testing.T) {
	allowed := []string{"owner", "support", "finance_ops"}
	for _, role := range allowed {
		if !adminCanViewAccountPhone(role) {
			t.Fatalf("role %q should view account phone", role)
		}
	}
	blocked := []string{"ops_readonly", "auditor", "content_ops", "release_ops", ""}
	for _, role := range blocked {
		if adminCanViewAccountPhone(role) {
			t.Fatalf("role %q should not view account phone", role)
		}
	}
}

func TestAndroidUpdateConfigValidityContract(t *testing.T) {
	tests := []struct {
		name          string
		cfg           androidUpdateConfig
		wantValid     bool
		wantArtifacts bool
	}{
		{
			name:      "version only is valid config but no download artifacts",
			cfg:       androidUpdateConfig{LatestVersionCode: 4},
			wantValid: true,
		},
		{
			name:      "https apk without checksum is valid config but incomplete artifacts",
			cfg:       androidUpdateConfig{LatestVersionCode: 4, APKURL: "https://download.example.com/app.apk"},
			wantValid: true,
		},
		{
			name: "complete artifacts",
			cfg: androidUpdateConfig{
				LatestVersionCode: 4,
				APKURL:            "https://download.example.com/app.apk",
				APKChecksumSHA256: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
				FileSizeBytes:     123,
			},
			wantValid:     true,
			wantArtifacts: true,
		},
		{
			name:      "non https apk is invalid",
			cfg:       androidUpdateConfig{LatestVersionCode: 4, APKURL: "http://download.example.com/app.apk"},
			wantValid: false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := androidUpdateConfigValid(tt.cfg); got != tt.wantValid {
				t.Fatalf("androidUpdateConfigValid = %v, want %v", got, tt.wantValid)
			}
			if got := androidUpdateDownloadArtifactsComplete(tt.cfg); got != tt.wantArtifacts {
				t.Fatalf("androidUpdateDownloadArtifactsComplete = %v, want %v", got, tt.wantArtifacts)
			}
		})
	}
}

func hasAdminMonitoringActionRoute(items []AdminMonitoringActionItem, route string) bool {
	for _, item := range items {
		if item.Route == route {
			return true
		}
	}
	return false
}

func hasAdminMonitoringActionTitle(items []AdminMonitoringActionItem, title string) bool {
	for _, item := range items {
		if item.Title == title {
			return true
		}
	}
	return false
}

func hasAdminMonitoringCapabilityStatus(items []AdminMonitoringCapability, title string, status string) bool {
	for _, item := range items {
		if item.Title == title && item.Status == status {
			return true
		}
	}
	return false
}

func hasAdminMonitoringModelPolicy(items []AdminMonitoringModelUsageRow, title string, model string, searchStrategy string, forcedSearch bool) bool {
	for _, item := range items {
		if item.Title == title &&
			item.Model == model &&
			item.SearchStrategy == searchStrategy &&
			item.ForcedSearch == forcedSearch {
			return true
		}
	}
	return false
}

func findAdminMonitoringModelPolicy(items []AdminMonitoringModelUsageRow, title string) *AdminMonitoringModelUsageRow {
	for idx := range items {
		if items[idx].Title == title {
			return &items[idx]
		}
	}
	return nil
}

package app

import (
	"os"
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
	if !hasAdminMonitoringCapabilityStatus(capabilities, "账号登录", "partial") {
		t.Fatalf("login capability should stay partial until one-click and SMS are validated on real devices: %#v", capabilities)
	}
}

func TestAdminMonitoringSLSCopyMatchesCurrentOpsState(t *testing.T) {
	report := AdminMonitoring{
		Health: AdminHealthStatus{
			API:               "ok",
			Bailian:           "ok",
			Dypns:             "ok",
			DypnsFusion:       "ok",
			DypnsSMS:          "ok",
			SMS:               "ok",
			Redis:             "ok",
			UploadStorage:     "oss",
			AuthStrict:        true,
			DevOrderEndpoints: false,
		},
		Queues: AdminMonitoringQueues{
			AppUpdate:          AdminMonitoringAppUpdate{Enabled: true, ConfigValid: true, DownloadArtifactsComplete: true},
			GiftCardBatchCount: 1,
			GiftCardTotal:      1,
			GiftCardActive:     1,
		},
	}
	launchItem := findAdminMonitoringLaunchItem(buildAdminMonitoringLaunchReadiness(report), "日志告警")
	if launchItem == nil {
		t.Fatalf("missing SLS launch readiness item")
	}
	assertContainsAll(t, launchItem.Body, "邮件行动策略", "最小仪表盘", "云监控邮件", "首封告警邮件")
	assertNotContains(t, launchItem.Body, "行动策略和仪表盘仍需补", "资源水位告警仍待补")

	capability := findAdminMonitoringCapability(buildAdminMonitoringCapabilities(), "SLS 告警")
	if capability == nil {
		t.Fatalf("missing SLS capability item")
	}
	assertContainsAll(t, capability.Body, "邮件行动策略", "最小仪表盘", "云监控邮件", "首封 SLS 告警邮件")
	assertNotContains(t, capability.Body, "行动策略、仪表盘和资源水位告警仍待补")
}

func TestAdminMonitoringHealthCountsPlainSMSStatus(t *testing.T) {
	health := AdminHealthStatus{
		API:               "ok",
		Bailian:           "ok",
		Dypns:             "ok",
		DypnsFusion:       "ok",
		DypnsSMS:          "ok",
		SMS:               "missing_config",
		Redis:             "ok",
		UploadStorage:     "oss",
		AuthStrict:        true,
		DevOrderEndpoints: false,
	}
	if got := countUnreadyAdminDependencies(health); got != 1 {
		t.Fatalf("unready count = %d, want 1 for plain sms status", got)
	}
}

func TestAdminSupportReplyUsesSupportImageValidation(t *testing.T) {
	source := mustReadFileForTest(t, "admin_api.go")
	block := functionBlockForTest(source, "func (s *Server) handleAdminCreateSupportMessage")
	if !strings.Contains(block, "validateSupportImageURLs") {
		t.Fatalf("admin support reply must validate support upload URLs")
	}
	if !strings.Contains(block, "normalizeAdminSupportMessagePayload") {
		t.Fatalf("admin support reply must use admin-sensitive payload validation")
	}
	if strings.Contains(block, "validateChatStreamImageURLs") {
		t.Fatalf("admin support reply must not use chat image URL validation")
	}
}

func TestAdminMonitoringLegacyFusionCopyMatchesCurrentLoginPolicy(t *testing.T) {
	report := AdminMonitoring{
		AuthLogs: AdminMonitoringAuthLogs{
			EnvBlocked:  1,
			EnvWarnings: 1,
		},
	}
	items := buildAdminMonitoringActionItems(report)
	blocked := findAdminMonitoringActionItem(items, "旧包一键登录环境不满足")
	if blocked == nil {
		t.Fatalf("missing blocked environment item: %#v", items)
	}
	assertContainsAll(t, blocked.Body, "旧安装包", "无网络", "无 SIM", "新包已改为短信验证码登录")
	assertNotContains(t, blocked.Body, "VPN", "系统代理")

	warning := findAdminMonitoringActionItem(items, "旧包一键登录混合网络记录")
	if warning == nil {
		t.Fatalf("missing warning environment item: %#v", items)
	}
	assertContainsAll(t, warning.Body, "旧安装包", "4G+WiFi", "VPN", "新包不再拉 SDK")
}

func TestAdminMonitoringAuthLogsExposeLatestCrashTime(t *testing.T) {
	source := mustReadFileForTest(t, "admin_api.go")
	if !strings.Contains(source, "LatestCrashAt") || !strings.Contains(source, `json:"latest_crash_at,omitempty"`) {
		t.Fatalf("auth logs must expose latest_crash_at for operator-readable crash recency")
	}
	block := functionBlockForTest(source, "func (s *Store) buildAdminMonitoringAuthLogs")
	assertContainsAll(t, block,
		"latestCrash",
		"MAX(CASE WHEN event IN ('app.crash', 'auth.app_crash') THEN created_at ELSE NULL END)",
		"authLogs.LatestCrashAt",
	)
}

func TestAdminAuthFunnelStageMappingCoversKnownLoginEvents(t *testing.T) {
	tests := map[string]string{
		"auth.fusion_start_requested":            "legacy_fusion",
		"auth.fusion_env_blocked":                "legacy_fusion",
		"auth.fusion_env_warning":                "legacy_fusion",
		"auth.fusion_permission_request":         "legacy_fusion",
		"auth.fusion_permission_ready":           "legacy_fusion",
		"auth.fusion_permission_denied":          "legacy_fusion",
		"auth.fusion_token_failed":               "legacy_fusion",
		"auth.fusion_sdk_init_failed":            "legacy_fusion",
		"auth.fusion_scene_start_failed":         "legacy_fusion",
		"auth.fusion_protocol_load_failed":       "legacy_fusion",
		"auth.fusion_sdk_token_auth_failed":      "legacy_fusion",
		"auth.fusion_verify_failed":              "legacy_fusion",
		"auth.fusion_get_phone_for_verification": "legacy_fusion",
		"auth.fusion_login_failed":               "legacy_fusion",
		"auth.fusion_login_success":              "legacy_fusion",
		"auth.login_network_failed":              "server_login",
		"auth.sms_send_failed":                   "sms",
		"auth.sms_login_failed":                  "sms",
		"auth.sms_login_success":                 "sms",
		"auth.app_crash":                         "crash",
		"app.crash":                              "crash",
		"app_update.check_failed":                "",
		"auth.future_event_kept_in_top_events":   "",
	}
	for event, want := range tests {
		if got := adminAuthFunnelStageKeyForEvent(event); got != want {
			t.Fatalf("stage for %s = %q, want %q", event, got, want)
		}
	}
}

func TestAdminAuthFunnelSuccessEventsStayExplicit(t *testing.T) {
	successEvents := []string{
		"auth.fusion_permission_ready",
		"auth.fusion_scene_start_invoked",
		"auth.fusion_login_success",
		"auth.sms_login_success",
		"auth.login_success",
	}
	for _, event := range successEvents {
		if !adminAuthFunnelEventIsSuccess(event) {
			t.Fatalf("event %s should count as explicit success", event)
		}
	}
	notSuccessEvents := []string{
		"auth.fusion_start_requested",
		"auth.fusion_sdk_init_start",
		"auth.fusion_env_warning",
		"auth.sms_send_failed",
		"auth.app_crash",
	}
	for _, event := range notSuccessEvents {
		if adminAuthFunnelEventIsSuccess(event) {
			t.Fatalf("event %s should not count as explicit success", event)
		}
	}
}

func TestAdminMonitoringNotesMatchCurrentOpsState(t *testing.T) {
	notes := buildAdminMonitoringNotes()
	if len(notes) == 0 {
		t.Fatalf("expected monitoring notes")
	}
	joined := ""
	for _, note := range notes {
		joined += note.Title + note.Body
	}
	assertContainsAll(t, joined, "邮件行动策略", "最小仪表盘", "云监控邮件", "首封 SLS 告警邮件")
	assertNotContains(t, joined, "外部通知、资源水位", "外部通知、资源水位和完整 Nginx access 仪表盘后续再接")
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
	if dailyRow.Protocol != "OpenAI兼容非流式" {
		t.Fatalf("daily agri protocol = %q", dailyRow.Protocol)
	}
	if !dailyRow.ThinkingDisabled {
		t.Fatalf("daily agri thinking should be disabled for default qwen3.5-plus path: %#v", dailyRow)
	}
	if !strings.Contains(dailyRow.CostNote, "当前生产默认链") {
		t.Fatalf("daily agri cost note should mention current default path: %#v", dailyRow)
	}
}

func TestAdminMonitoringLaunchReadinessRequiresRealChatEvidence(t *testing.T) {
	base := AdminMonitoring{
		Health: AdminHealthStatus{
			API:               "ok",
			Bailian:           "ok",
			Dypns:             "ok",
			DypnsFusion:       "ok",
			DypnsSMS:          "ok",
			Redis:             "ok",
			UploadStorage:     "oss",
			AuthStrict:        true,
			DevOrderEndpoints: false,
		},
		Queues: AdminMonitoringQueues{
			AppUpdate: AdminMonitoringAppUpdate{ConfigValid: true, DownloadArtifactsComplete: true, Enabled: true},
		},
	}

	items := buildAdminMonitoringLaunchReadiness(base)
	modelItem := findAdminMonitoringLaunchItem(items, "模型问诊")
	if modelItem == nil {
		t.Fatalf("missing model launch item: %#v", items)
	}
	if modelItem.Status != "attention" || !strings.Contains(modelItem.Body, "没有真实问诊记录") {
		t.Fatalf("model launch item should require real chat evidence, got %#v", modelItem)
	}

	base.Windows = []AdminMonitoringWindow{{Key: "24h", ChatRounds: 2}}
	items = buildAdminMonitoringLaunchReadiness(base)
	modelItem = findAdminMonitoringLaunchItem(items, "模型问诊")
	if modelItem == nil {
		t.Fatalf("missing model launch item after text evidence: %#v", items)
	}
	if modelItem.Status != "attention" || !strings.Contains(modelItem.Body, "图片问诊") {
		t.Fatalf("text-only evidence should still require image chat validation, got %#v", modelItem)
	}

	base.Windows = []AdminMonitoringWindow{{Key: "24h", ChatRounds: 3, ImageChatRounds: 1}}
	items = buildAdminMonitoringLaunchReadiness(base)
	modelItem = findAdminMonitoringLaunchItem(items, "模型问诊")
	if modelItem == nil {
		t.Fatalf("missing model launch item after image evidence: %#v", items)
	}
	if modelItem.Status != "ready" {
		t.Fatalf("text and image evidence should mark model chat ready, got %#v", modelItem)
	}
}

func TestAdminMonitoringLaunchReadinessStatusContract(t *testing.T) {
	report := AdminMonitoring{
		Health: AdminHealthStatus{
			API:               "ok",
			Bailian:           "ok",
			Dypns:             "ok",
			DypnsFusion:       "ok",
			DypnsSMS:          "ok",
			Redis:             "ok",
			UploadStorage:     "oss",
			AuthStrict:        true,
			DevOrderEndpoints: false,
		},
		Queues: AdminMonitoringQueues{
			AppUpdate:              AdminMonitoringAppUpdate{ConfigValid: true, DownloadArtifactsComplete: true, Enabled: true},
			AccountDeletionPending: 1,
			GiftCardBatchCount:     1,
			GiftCardTotal:          1,
			GiftCardActive:         1,
		},
	}
	items := buildAdminMonitoringLaunchReadiness(report)
	validStatuses := map[string]bool{"ready": true, "attention": true, "blocked": true}
	for _, item := range items {
		if !validStatuses[item.Status] {
			t.Fatalf("unexpected launch readiness status %q for %#v", item.Status, item)
		}
	}
	accountDeletion := findAdminMonitoringLaunchItem(items, "注销申请")
	if accountDeletion == nil {
		t.Fatalf("missing account deletion launch item: %#v", items)
	}
	if accountDeletion.Status != "attention" {
		t.Fatalf("account deletion launch item status = %q, want attention", accountDeletion.Status)
	}
	payment := findAdminMonitoringLaunchItem(items, "支付接入")
	if payment == nil {
		t.Fatalf("missing payment launch item: %#v", items)
	}
	if payment.Status != "attention" || payment.Manual {
		t.Fatalf("payment before purchase launch should be attention and not a manual go-live item, got %#v", payment)
	}
	assertContainsAll(t, payment.Body, "购买入口保持关闭", "不阻塞免费版", "开放真实收费前")
}

func TestAdminMonitoringLaunchReadinessRequiresCompleteAppUpdateArtifacts(t *testing.T) {
	report := AdminMonitoring{
		Health: AdminHealthStatus{
			API:               "ok",
			Bailian:           "ok",
			Dypns:             "ok",
			DypnsFusion:       "ok",
			DypnsSMS:          "ok",
			Redis:             "ok",
			AuthStrict:        true,
			DevOrderEndpoints: false,
		},
		Queues: AdminMonitoringQueues{
			AppUpdate: AdminMonitoringAppUpdate{Enabled: true, ConfigValid: true, DownloadArtifactsComplete: false},
		},
	}
	items := buildAdminMonitoringLaunchReadiness(report)
	updateItem := findAdminMonitoringLaunchItem(items, "安装包更新")
	if updateItem == nil {
		t.Fatalf("missing app update launch item: %#v", items)
	}
	if updateItem.Status != "attention" || !strings.Contains(updateItem.Body, "不会下发新包") {
		t.Fatalf("incomplete app update artifacts should stay attention with explicit body, got %#v", updateItem)
	}
}

func TestAdminMonitoringLaunchReadinessKeepsAppUpdateAttentionUntilDeviceInstall(t *testing.T) {
	report := AdminMonitoring{
		Health: AdminHealthStatus{
			API:               "ok",
			Bailian:           "ok",
			Dypns:             "ok",
			DypnsFusion:       "ok",
			DypnsSMS:          "ok",
			Redis:             "ok",
			AuthStrict:        true,
			DevOrderEndpoints: false,
		},
		Queues: AdminMonitoringQueues{
			AppUpdate: AdminMonitoringAppUpdate{Enabled: true, ConfigValid: true, DownloadArtifactsComplete: true},
		},
	}
	items := buildAdminMonitoringLaunchReadiness(report)
	updateItem := findAdminMonitoringLaunchItem(items, "安装包更新")
	if updateItem == nil {
		t.Fatalf("missing app update launch item: %#v", items)
	}
	if updateItem.Status != "attention" || !strings.Contains(updateItem.Body, "覆盖安装") || !strings.Contains(updateItem.Body, "正式验收") {
		t.Fatalf("complete app update artifacts should still require device install validation, got %#v", updateItem)
	}
}

func TestAdminMonitoringLaunchReadinessManualItemsAreExplicit(t *testing.T) {
	report := AdminMonitoring{
		Health: AdminHealthStatus{
			API:               "ok",
			Bailian:           "ok",
			Dypns:             "ok",
			DypnsFusion:       "ok",
			DypnsSMS:          "ok",
			SMS:               "ok",
			Redis:             "ok",
			UploadStorage:     "oss",
			AuthStrict:        true,
			DevOrderEndpoints: false,
		},
		Queues: AdminMonitoringQueues{
			AppUpdate:          AdminMonitoringAppUpdate{Enabled: true, ConfigValid: true, DownloadArtifactsComplete: true},
			GiftCardBatchCount: 1,
			GiftCardTotal:      1,
			GiftCardActive:     1,
		},
	}
	items := buildAdminMonitoringLaunchReadiness(report)
	appICP := findAdminMonitoringLaunchItem(items, "App 备案")
	if appICP == nil {
		t.Fatalf("missing App ICP launch item: %#v", items)
	}
	if appICP.Status != "ready" || appICP.Manual {
		t.Fatalf("App ICP should be ready and no longer require manual confirmation: %#v", appICP)
	}
	if !strings.Contains(appICP.Body, "京ICP备2026031728号-2A") || !strings.Contains(appICP.Body, "工信部备案查询") {
		t.Fatalf("App ICP launch item should mention the filing number and query link: %#v", appICP)
	}
	manualTitles := []string{
		"App 公安备案",
		"AccessKey 轮换",
		"最终真机回归",
		"短信套餐余额",
		"费用 / 套餐成本",
		"最终 release 物料",
		"日志告警",
	}
	for _, title := range manualTitles {
		item := findAdminMonitoringLaunchItem(items, title)
		if item == nil {
			t.Fatalf("missing manual launch item %q in %#v", title, items)
		}
		if !item.Manual {
			t.Fatalf("launch item %q should be marked manual: %#v", title, item)
		}
		if item.Status != "attention" {
			t.Fatalf("manual launch item %q status = %q, want attention", title, item.Status)
		}
		if strings.TrimSpace(item.ConfirmHint) == "" {
			t.Fatalf("manual launch item %q should include a confirmation hint: %#v", title, item)
		}
	}
	costItem := findAdminMonitoringLaunchItem(items, "费用 / 套餐成本")
	if costItem == nil {
		t.Fatalf("missing cost confirmation launch item: %#v", items)
	}
	if !strings.Contains(costItem.Body, "DYPNS") || !strings.Contains(costItem.Body, "模型资源包") {
		t.Fatalf("cost confirmation item should mention DYPNS and model packages: %#v", costItem)
	}
	if !strings.Contains(costItem.ConfirmHint, "check-aliyun-costs.ps1") || !strings.Contains(costItem.ConfirmHint, "AccessKey") {
		t.Fatalf("cost confirmation hint should point to the cost script and sensitive data boundary: %#v", costItem)
	}
	serviceHealth := findAdminMonitoringLaunchItem(items, "后端生产健康")
	if serviceHealth == nil {
		t.Fatalf("missing service health launch item: %#v", items)
	}
	if serviceHealth.Manual {
		t.Fatalf("service health should not be shown as a manual confirmation item: %#v", serviceHealth)
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

func TestAdminMonitoringDailyAgriUsesDefaultQwen35Plus(t *testing.T) {
	rows := buildAdminMonitoringModelUsagePolicy()
	dailyRow := findAdminMonitoringModelPolicy(rows, "今日农情")
	if dailyRow == nil {
		t.Fatalf("missing daily agri model usage policy: %#v", rows)
	}
	if dailyRow.Model != defaultDailyAgriCardModel {
		t.Fatalf("daily agri model = %q", dailyRow.Model)
	}
	if dailyRow.Protocol != "OpenAI兼容非流式" {
		t.Fatalf("daily agri protocol = %q", dailyRow.Protocol)
	}
	if !dailyRow.ThinkingDisabled {
		t.Fatalf("daily agri thinking should be disabled: %#v", dailyRow)
	}
	if !strings.Contains(dailyRow.CostNote, "qwen3.5-plus") ||
		!strings.Contains(dailyRow.CostNote, "turbo") ||
		!strings.Contains(dailyRow.CostNote, "主提示词") {
		t.Fatalf("daily agri cost note should describe qwen3.5-plus turbo prompt-controlled path: %#v", dailyRow)
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

func TestAdminCanViewGiftCardCodes(t *testing.T) {
	allowed := []string{"owner", "finance_ops"}
	for _, role := range allowed {
		if !adminCanViewGiftCardCodes(role) {
			t.Fatalf("role %q should view gift card codes", role)
		}
	}
	blocked := []string{"support", "ops_readonly", "auditor", "content_ops", "release_ops", ""}
	for _, role := range blocked {
		if adminCanViewGiftCardCodes(role) {
			t.Fatalf("role %q should not view gift card codes", role)
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
			name:      "disabled empty config is a valid stop state",
			cfg:       androidUpdateConfig{Enabled: false},
			wantValid: true,
		},
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

func findAdminMonitoringActionItem(items []AdminMonitoringActionItem, title string) *AdminMonitoringActionItem {
	for idx := range items {
		if items[idx].Title == title {
			return &items[idx]
		}
	}
	return nil
}

func hasAdminMonitoringCapabilityStatus(items []AdminMonitoringCapability, title string, status string) bool {
	for _, item := range items {
		if item.Title == title && item.Status == status {
			return true
		}
	}
	return false
}

func findAdminMonitoringCapability(items []AdminMonitoringCapability, title string) *AdminMonitoringCapability {
	for idx := range items {
		if items[idx].Title == title {
			return &items[idx]
		}
	}
	return nil
}

func assertContainsAll(t *testing.T, text string, needles ...string) {
	t.Helper()
	for _, needle := range needles {
		if !strings.Contains(text, needle) {
			t.Fatalf("text %q should contain %q", text, needle)
		}
	}
}

func assertNotContains(t *testing.T, text string, needles ...string) {
	t.Helper()
	for _, needle := range needles {
		if strings.Contains(text, needle) {
			t.Fatalf("text %q should not contain %q", text, needle)
		}
	}
}

func mustReadFileForTest(t *testing.T, path string) string {
	t.Helper()
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read %s: %v", path, err)
	}
	return string(data)
}

func functionBlockForTest(source string, signature string) string {
	start := strings.Index(source, signature)
	if start < 0 {
		return ""
	}
	open := strings.Index(source[start:], "{")
	if open < 0 {
		return ""
	}
	open += start
	depth := 0
	for idx := open; idx < len(source); idx++ {
		switch source[idx] {
		case '{':
			depth++
		case '}':
			depth--
			if depth == 0 {
				return source[start : idx+1]
			}
		}
	}
	return source[start:]
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

func findAdminMonitoringLaunchItem(items []AdminMonitoringLaunchItem, title string) *AdminMonitoringLaunchItem {
	for idx := range items {
		if items[idx].Title == title {
			return &items[idx]
		}
	}
	return nil
}

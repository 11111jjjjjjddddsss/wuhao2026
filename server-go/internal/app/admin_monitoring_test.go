package app

import "testing"

func TestAdminMonitoringActionItemsContract(t *testing.T) {
	report := AdminMonitoring{
		Queues: AdminMonitoringQueues{
			UnreadyDependencyCount: 1,
			AppErrors:              10,
			SupportNeedsReply:      2,
			DailyAgriStatus:        "failed",
			GiftCardFailedAttempts: 3,
			AuditFailures:          4,
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

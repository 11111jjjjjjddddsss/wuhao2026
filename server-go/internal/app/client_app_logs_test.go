package app

import "testing"

func TestNormalizeClientAppLogPayloadAcceptsMinimalSafePayload(t *testing.T) {
	versionCode := 12
	clientTime := int64(1710000000000)
	input, validationError := normalizeClientAppLogPayload("user-1", "1.2.*.*", clientAppLogRequest{
		Level:          "WARN",
		Event:          "chat.stream-interrupted",
		Message:        "  stream interrupted  ",
		Platform:       "Android",
		AppVersionCode: &versionCode,
		AppVersionName: "1.0.12",
		OSVersion:      "Android 15",
		DeviceModel:    "Brand Model",
		ClientTimeMs:   &clientTime,
		Attrs: map[string]any{
			"reason":      "network",
			"text_length": float64(42),
			"ignored":     map[string]any{"nested": true},
		},
	}, 123)
	if validationError != "" {
		t.Fatalf("unexpected validation error: %s", validationError)
	}
	if input.Level != "warn" || input.Event != "chat.stream-interrupted" || input.Message != "stream interrupted" {
		t.Fatalf("normalized input mismatch: %#v", input)
	}
	if input.Platform != "android" || input.AppVersionCode == nil || *input.AppVersionCode != versionCode {
		t.Fatalf("platform/version mismatch: %#v", input)
	}
	if input.AttrsJSON == nil {
		t.Fatalf("expected attrs json")
	}
}

func TestNormalizeClientAppLogPayloadRejectsInvalidPayload(t *testing.T) {
	tests := []struct {
		name string
		body clientAppLogRequest
		want string
	}{
		{name: "missing event", body: clientAppLogRequest{Level: "warn"}, want: "event required"},
		{name: "bad level", body: clientAppLogRequest{Level: "debug", Event: "x"}, want: "invalid level"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, got := normalizeClientAppLogPayload("user-1", "", tt.body, 123)
			if got != tt.want {
				t.Fatalf("validation error = %q, want %q", got, tt.want)
			}
		})
	}
}

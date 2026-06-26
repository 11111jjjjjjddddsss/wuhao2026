package app

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"os"
	"strings"
	"time"
)

const (
	defaultPrimaryChatModel                 = "gpt-5.5"
	defaultPrimaryChatDialTimeout           = 6 * time.Second
	defaultPrimaryChatTLSHandshakeTimeout   = 6 * time.Second
	defaultPrimaryChatResponseHeaderTimeout = 6 * time.Second
	defaultPrimaryChatIdleConnTimeout       = 60 * time.Second
)

type PrimaryChatClient struct {
	httpClient *http.Client
}

func NewPrimaryChatClientFromEnv() *PrimaryChatClient {
	return &PrimaryChatClient{
		httpClient: newPrimaryChatHTTPClient(),
	}
}

func newPrimaryChatHTTPClient() *http.Client {
	return &http.Client{
		Transport: &http.Transport{
			Proxy: http.ProxyFromEnvironment,
			DialContext: (&net.Dialer{
				Timeout:   envDurationWithDefault("CHAT_PRIMARY_DIAL_TIMEOUT_SECONDS", defaultPrimaryChatDialTimeout),
				KeepAlive: 30 * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   envDurationWithDefault("CHAT_PRIMARY_TLS_HANDSHAKE_TIMEOUT_SECONDS", defaultPrimaryChatTLSHandshakeTimeout),
			ResponseHeaderTimeout: envDurationWithDefault("CHAT_PRIMARY_RESPONSE_HEADER_TIMEOUT_SECONDS", defaultPrimaryChatResponseHeaderTimeout),
			IdleConnTimeout:       envDurationWithDefault("CHAT_PRIMARY_IDLE_CONN_TIMEOUT_SECONDS", defaultPrimaryChatIdleConnTimeout),
			ExpectContinueTimeout: 1 * time.Second,
			MaxIdleConns:          50,
			MaxIdleConnsPerHost:   8,
		},
	}
}

func (c *PrimaryChatClient) Enabled() bool {
	return primaryChatConfigured()
}

func (c *PrimaryChatClient) HasKeyConfigured() bool {
	return strings.TrimSpace(os.Getenv("CHAT_PRIMARY_API_KEY")) != ""
}

func (c *PrimaryChatClient) OpenStream(ctx context.Context, messages []BailianMessage, options BailianStreamOptions) (*http.Response, error) {
	if !c.Enabled() {
		return nil, fmt.Errorf("primary chat model disabled")
	}
	body := map[string]any{
		"model":           primaryChatModelName(),
		"stream":          true,
		"temperature":     unifiedModelTemperature,
		"enable_thinking": false,
		"stream_options": map[string]any{
			"include_usage": true,
		},
		"enable_search": true,
		"search_options": map[string]any{
			"forced_search": primaryChatForceSearch(options.ForceSearch, messages),
		},
		"messages": messages,
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, primaryChatCompletionsURL(), bytes.NewReader(payload))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+strings.TrimSpace(os.Getenv("CHAT_PRIMARY_API_KEY")))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "text/event-stream")
	req.Header.Set("Cache-Control", "no-cache")
	return c.httpClient.Do(req)
}

func primaryChatEnabled() bool {
	return parseBoolEnv(os.Getenv("CHAT_PRIMARY_ENABLED"))
}

func primaryChatConfigured() bool {
	return primaryChatEnabled() &&
		strings.TrimSpace(os.Getenv("CHAT_PRIMARY_API_KEY")) != "" &&
		primaryChatCompletionsURL() != ""
}

func primaryChatHealthStatus(client *PrimaryChatClient) string {
	if !primaryChatEnabled() {
		return "disabled"
	}
	if client == nil || !primaryChatConfigured() {
		return "missing_config"
	}
	return "ok"
}

func primaryChatModelName() string {
	return firstNonEmpty(strings.TrimSpace(os.Getenv("CHAT_PRIMARY_MODEL")), defaultPrimaryChatModel)
}

func primaryChatForceSearch(requestForceSearch bool, messages []BailianMessage) bool {
	if primaryChatMessagesContainImage(messages) {
		return false
	}
	if parseBoolEnv(os.Getenv("CHAT_PRIMARY_FORCE_SEARCH")) {
		return true
	}
	return requestForceSearch
}

func primaryChatMessagesContainImage(messages []BailianMessage) bool {
	for _, message := range messages {
		if contentContainsImageURL(message.Content) {
			return true
		}
	}
	return false
}

func contentContainsImageURL(content any) bool {
	switch value := content.(type) {
	case []map[string]any:
		for _, item := range value {
			if itemType, _ := item["type"].(string); strings.EqualFold(itemType, "image_url") {
				return true
			}
		}
	case []any:
		for _, item := range value {
			if contentContainsImageURL(item) {
				return true
			}
		}
	case map[string]any:
		if itemType, _ := value["type"].(string); strings.EqualFold(itemType, "image_url") {
			return true
		}
	}
	return false
}

func primaryChatCompletionsURL() string {
	if direct := strings.TrimSpace(os.Getenv("CHAT_PRIMARY_CHAT_COMPLETIONS_URL")); direct != "" {
		return direct
	}
	baseURL := strings.TrimSpace(os.Getenv("CHAT_PRIMARY_BASE_URL"))
	if baseURL == "" {
		return ""
	}
	trimmed := strings.TrimRight(baseURL, "/")
	lower := strings.ToLower(trimmed)
	switch {
	case strings.HasSuffix(lower, "/chat/completions"):
		return trimmed
	case strings.HasSuffix(lower, "/v1"):
		return trimmed + "/chat/completions"
	default:
		return trimmed + "/v1/chat/completions"
	}
}

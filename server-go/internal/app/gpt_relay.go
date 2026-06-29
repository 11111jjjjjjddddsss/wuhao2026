package app

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"time"
)

const (
	gptRelayProvider                         = "gpt_relay"
	defaultGPTRelayModel                     = "gpt-5.5"
	defaultGPTRelayReasoningEffort           = "medium"
	defaultGPTRelaySearchContextSize         = "low"
	defaultGPTRelayFirstVisibleTimeout       = 15 * time.Second
	defaultGPTRelayDialTimeout               = 6 * time.Second
	defaultGPTRelayTLSHandshakeTimeout       = 6 * time.Second
	defaultGPTRelayResponseHeaderTimeout     = 6 * time.Second
	defaultGPTRelayIdleConnTimeout           = 60 * time.Second
	defaultGPTRelayKeyCooldown               = 30 * time.Second
	defaultGPTRelayKeyMaxAttempts            = 5
	defaultGPTRelayMaxConfiguredKeySlot      = 50
	defaultGPTRelayMaxSearchCallsInstruction = "如需联网，必须只搜索一次。\n拿到够用信息后立刻快速回答。\n不要解释搜索过程。"
)

type GPTRelayClient struct {
	httpClient      *http.Client
	cooldownMu      sync.Mutex
	keyCooldown     map[string]time.Time
	selectionMu     sync.Mutex
	keySelectionIdx int
}

type gptRelayAPIKeyEntry struct {
	Value string
}

func NewGPTRelayClientFromEnv() *GPTRelayClient {
	return &GPTRelayClient{
		httpClient:  newGPTRelayHTTPClient(),
		keyCooldown: map[string]time.Time{},
	}
}

func newGPTRelayHTTPClient() *http.Client {
	return &http.Client{
		Transport: &http.Transport{
			Proxy: http.ProxyFromEnvironment,
			DialContext: (&net.Dialer{
				Timeout:   envDurationWithDefault("GPT_RELAY_DIAL_TIMEOUT_SECONDS", defaultGPTRelayDialTimeout),
				KeepAlive: 30 * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   envDurationWithDefault("GPT_RELAY_TLS_HANDSHAKE_TIMEOUT_SECONDS", defaultGPTRelayTLSHandshakeTimeout),
			ResponseHeaderTimeout: envDurationWithDefault("GPT_RELAY_RESPONSE_HEADER_TIMEOUT_SECONDS", defaultGPTRelayResponseHeaderTimeout),
			IdleConnTimeout:       envDurationWithDefault("GPT_RELAY_IDLE_CONN_TIMEOUT_SECONDS", defaultGPTRelayIdleConnTimeout),
			ExpectContinueTimeout: 1 * time.Second,
			MaxIdleConns:          120,
			MaxIdleConnsPerHost:   40,
		},
	}
}

func (c *GPTRelayClient) Enabled() bool {
	return gptRelayConfigured()
}

func (c *GPTRelayClient) HasKeyConfigured() bool {
	return len(gptRelayKeyEntries()) > 0
}

func (c *GPTRelayClient) OpenStream(ctx context.Context, messages []BailianMessage) (*http.Response, error) {
	if c == nil || !c.Enabled() {
		return nil, fmt.Errorf("gpt relay disabled")
	}
	instructions, input := buildGPTRelayResponsesPrompt(messages)
	body := map[string]any{
		"model":        gptRelayModelName(),
		"stream":       true,
		"instructions": instructions,
		"input":        input,
		"tools": []map[string]any{
			{
				"type":                "web_search",
				"search_context_size": gptRelaySearchContextSize(),
			},
		},
		"tool_choice": "auto",
		"reasoning": map[string]any{
			"effort": gptRelayReasoningEffort(),
		},
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	return c.doPayloadRequest(ctx, gptRelayResponsesURL(), payload)
}

func (c *GPTRelayClient) doPayloadRequest(ctx context.Context, endpoint string, payload []byte) (*http.Response, error) {
	keys := gptRelayKeyEntries()
	if len(keys) == 0 {
		return nil, fmt.Errorf("GPT_RELAY_API_KEY(S) is missing")
	}
	maxAttempts := envIntWithDefault("GPT_RELAY_KEY_MAX_ATTEMPTS", defaultGPTRelayKeyMaxAttempts)
	if maxAttempts <= 0 {
		maxAttempts = 1
	}
	if maxAttempts > len(keys) {
		maxAttempts = len(keys)
	}

	attempted := map[string]bool{}
	var lastErr error
	for attempt := 0; attempt < maxAttempts; attempt++ {
		key, ok := c.pickNextKeyEntry(keys, attempted)
		if !ok {
			break
		}
		attempted[key.Value] = true
		resp, err := c.sendPayload(ctx, endpoint, payload, key.Value)
		if err != nil {
			c.coolDownKey(key.Value)
			lastErr = err
			if ctx.Err() != nil {
				return nil, err
			}
			continue
		}
		if isGPTRelayRetryableStatus(resp.StatusCode) {
			c.coolDownKey(key.Value)
		}
		if isGPTRelayRetryableStatus(resp.StatusCode) && attempt+1 < maxAttempts && ctx.Err() == nil {
			_, _ = readLimitedResponseBody(resp.Body, bailianBodyPreviewLimit)
			_ = resp.Body.Close()
			continue
		}
		return resp, nil
	}
	if lastErr != nil {
		return nil, lastErr
	}
	return nil, fmt.Errorf("GPT_RELAY_API_KEY(S) is missing")
}

func (c *GPTRelayClient) sendPayload(ctx context.Context, endpoint string, payload []byte, apiKey string) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(payload))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "text/event-stream")
	req.Header.Set("Cache-Control", "no-cache")
	return c.httpClient.Do(req)
}

func gptRelayEnabled() bool {
	return parseBoolEnv(os.Getenv("GPT_RELAY_ENABLED"))
}

func gptRelayConfigured() bool {
	return gptRelayEnabled() && len(gptRelayKeyEntries()) > 0 && gptRelayResponsesURL() != ""
}

func gptRelayHealthStatus(client *GPTRelayClient) string {
	if !gptRelayEnabled() {
		return "disabled"
	}
	if client == nil || !gptRelayConfigured() {
		return "missing_config"
	}
	return "ok"
}

func gptRelayKeyEntries() []gptRelayAPIKeyEntry {
	result := []gptRelayAPIKeyEntry{}
	addKey := func(value string) {
		key := normalizeGPTRelayAPIKey(value)
		if key == "" {
			return
		}
		for _, current := range result {
			if current.Value == key {
				return
			}
		}
		result = append(result, gptRelayAPIKeyEntry{Value: key})
	}

	for i := 1; i <= defaultGPTRelayMaxConfiguredKeySlot; i++ {
		addKey(os.Getenv(fmt.Sprintf("GPT_RELAY_API_KEY_%d", i)))
	}
	addKey(os.Getenv("GPT_RELAY_API_KEY"))
	for _, key := range splitConfiguredKeys(os.Getenv("GPT_RELAY_API_KEYS")) {
		addKey(key)
	}
	return result
}

func normalizeGPTRelayAPIKey(value string) string {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" {
		return ""
	}
	for _, field := range strings.Fields(trimmed) {
		field = strings.TrimSpace(field)
		if strings.HasPrefix(field, "sk-") {
			return field
		}
	}
	if idx := strings.Index(trimmed, "="); idx >= 0 {
		right := strings.TrimSpace(trimmed[idx+1:])
		if strings.HasPrefix(right, "sk-") {
			return right
		}
	}
	if idx := strings.LastIndex(trimmed, ":"); idx >= 0 {
		right := strings.TrimSpace(trimmed[idx+1:])
		if strings.HasPrefix(right, "sk-") {
			return right
		}
	}
	if strings.HasPrefix(trimmed, "sk-") {
		return trimmed
	}
	return ""
}

func gptRelayKeyPoolSize() int {
	return len(gptRelayKeyEntries())
}

func (c *GPTRelayClient) pickNextKeyEntry(keys []gptRelayAPIKeyEntry, attempted map[string]bool) (gptRelayAPIKeyEntry, bool) {
	if len(keys) == 0 {
		return gptRelayAPIKeyEntry{}, false
	}
	now := time.Now()
	start := c.nextSelectionOffset(len(keys))
	var fallback *gptRelayAPIKeyEntry
	for offset := 0; offset < len(keys); offset++ {
		idx := (start + offset) % len(keys)
		key := keys[idx]
		if attempted[key.Value] {
			continue
		}
		if fallback == nil {
			candidate := key
			fallback = &candidate
		}
		if !c.isKeyCoolingDown(key.Value, now) {
			return key, true
		}
	}
	if fallback != nil {
		return *fallback, true
	}
	return gptRelayAPIKeyEntry{}, false
}

func (c *GPTRelayClient) nextSelectionOffset(modulo int) int {
	if modulo <= 0 {
		return 0
	}
	c.selectionMu.Lock()
	defer c.selectionMu.Unlock()
	start := c.keySelectionIdx
	c.keySelectionIdx = (c.keySelectionIdx + 1) % modulo
	return start
}

func (c *GPTRelayClient) isKeyCoolingDown(key string, now time.Time) bool {
	c.cooldownMu.Lock()
	defer c.cooldownMu.Unlock()
	until, ok := c.keyCooldown[key]
	if !ok {
		return false
	}
	if !now.Before(until) {
		delete(c.keyCooldown, key)
		return false
	}
	return true
}

func (c *GPTRelayClient) coolDownKey(key string) {
	c.cooldownMu.Lock()
	defer c.cooldownMu.Unlock()
	duration := envDurationWithDefault("GPT_RELAY_KEY_COOLDOWN_SECONDS", defaultGPTRelayKeyCooldown)
	if duration <= 0 {
		delete(c.keyCooldown, key)
		return
	}
	c.keyCooldown[key] = time.Now().Add(duration)
}

func isGPTRelayRetryableStatus(status int) bool {
	return status == http.StatusUnauthorized ||
		status == http.StatusForbidden ||
		status == http.StatusTooManyRequests ||
		status == http.StatusInternalServerError ||
		status == http.StatusBadGateway ||
		status == http.StatusServiceUnavailable ||
		status == http.StatusGatewayTimeout
}

func gptRelayModelName() string {
	return firstNonEmpty(strings.TrimSpace(os.Getenv("GPT_RELAY_MODEL")), defaultGPTRelayModel)
}

func gptRelayProviderLabel() string {
	label := strings.TrimSpace(os.Getenv("GPT_RELAY_PROVIDER_LABEL"))
	if label == "" {
		return "GPT Relay"
	}
	safe := sanitizeDashScopeErrorMessage(label)
	if safe == "" || strings.Contains(safe, "[redacted]") || strings.Contains(safe, "[url]") || strings.Contains(safe, "[phone]") {
		return "GPT Relay"
	}
	return truncateRunes(safe, 20)
}

func gptRelayReasoningEffort() string {
	value := strings.ToLower(strings.TrimSpace(os.Getenv("GPT_RELAY_REASONING_EFFORT")))
	switch value {
	case "high":
		return "high"
	case "", defaultGPTRelayReasoningEffort:
		return defaultGPTRelayReasoningEffort
	default:
		return defaultGPTRelayReasoningEffort
	}
}

func gptRelaySearchContextSize() string {
	value := strings.ToLower(strings.TrimSpace(os.Getenv("GPT_RELAY_SEARCH_CONTEXT_SIZE")))
	if value == "" || value == defaultGPTRelaySearchContextSize {
		return defaultGPTRelaySearchContextSize
	}
	return defaultGPTRelaySearchContextSize
}

func resolveGPTRelayFirstVisibleTimeout(maxDuration time.Duration) time.Duration {
	duration := envDurationWithDefault("GPT_RELAY_FIRST_VISIBLE_TIMEOUT_SECONDS", defaultGPTRelayFirstVisibleTimeout)
	if duration <= 0 {
		return defaultGPTRelayFirstVisibleTimeout
	}
	if maxDuration > 0 && duration > maxDuration {
		return maxDuration
	}
	return duration
}

func gptRelayResponsesURL() string {
	if direct := strings.TrimSpace(os.Getenv("GPT_RELAY_RESPONSES_URL")); direct != "" {
		return gptRelaySafeEndpointURL(direct)
	}
	baseURL := strings.TrimSpace(os.Getenv("GPT_RELAY_BASE_URL"))
	if baseURL == "" {
		return ""
	}
	trimmed := strings.TrimRight(baseURL, "/")
	lower := strings.ToLower(trimmed)
	switch {
	case strings.HasSuffix(lower, "/responses"):
		return gptRelaySafeEndpointURL(trimmed)
	case strings.HasSuffix(lower, "/v1"):
		return gptRelaySafeEndpointURL(trimmed + "/responses")
	default:
		return gptRelaySafeEndpointURL(trimmed + "/v1/responses")
	}
}

func gptRelaySafeEndpointURL(raw string) string {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return ""
	}
	parsed, err := url.Parse(trimmed)
	if err != nil || parsed.Scheme == "" || parsed.Host == "" || parsed.User != nil || parsed.RawQuery != "" || parsed.Fragment != "" {
		return ""
	}
	scheme := strings.ToLower(parsed.Scheme)
	switch {
	case scheme == "https":
		return trimmed
	case scheme == "http" && gptRelayLocalHTTPHost(parsed.Hostname()):
		return trimmed
	default:
		return ""
	}
}

func gptRelayLocalHTTPHost(host string) bool {
	normalized := strings.ToLower(strings.Trim(host, "[]"))
	if normalized == "localhost" {
		return true
	}
	ip := net.ParseIP(normalized)
	return ip != nil && ip.IsLoopback()
}

func buildGPTRelayResponsesPrompt(messages []BailianMessage) (string, []map[string]any) {
	instructionParts := []string{}
	input := make([]map[string]any, 0, len(messages))
	for _, message := range messages {
		role := strings.ToLower(strings.TrimSpace(message.Role))
		if role == "system" {
			if text := gptRelayInstructionText(message.Content); text != "" {
				instructionParts = append(instructionParts, text)
			}
			continue
		}
		input = append(input, map[string]any{
			"role":    gptRelayResponsesRole(role),
			"content": gptRelayResponsesContent(message.Content),
		})
	}
	instructionParts = append(instructionParts, gptRelayNetworkingInstruction())
	return strings.Join(instructionParts, "\n\n"), input
}

func gptRelayInstructionText(content any) string {
	switch value := content.(type) {
	case string:
		return strings.TrimSpace(value)
	default:
		raw, err := json.Marshal(value)
		if err != nil {
			return ""
		}
		return strings.TrimSpace(string(raw))
	}
}

func gptRelayResponsesRole(role string) string {
	switch role {
	case "assistant":
		return "assistant"
	default:
		return "user"
	}
}

func gptRelayResponsesContent(content any) any {
	if parts, ok := gptRelayResponsesContentParts(content); ok && len(parts) > 0 {
		return parts
	}
	return content
}

func gptRelayResponsesContentParts(content any) ([]map[string]any, bool) {
	switch value := content.(type) {
	case []map[string]any:
		return gptRelayResponsesContentPartsFromMaps(value), true
	case []any:
		maps := make([]map[string]any, 0, len(value))
		for _, item := range value {
			if itemMap, ok := item.(map[string]any); ok {
				maps = append(maps, itemMap)
			}
		}
		if len(maps) == 0 && len(value) > 0 {
			return nil, false
		}
		return gptRelayResponsesContentPartsFromMaps(maps), true
	default:
		return nil, false
	}
}

func gptRelayResponsesContentPartsFromMaps(items []map[string]any) []map[string]any {
	result := make([]map[string]any, 0, len(items))
	for _, item := range items {
		itemType := strings.ToLower(strings.TrimSpace(asString(item["type"])))
		switch itemType {
		case "text", "input_text":
			if text := strings.TrimSpace(asString(item["text"])); text != "" {
				result = append(result, map[string]any{
					"type": "input_text",
					"text": text,
				})
			}
		case "image_url", "input_image":
			if imageURL := gptRelayImageURLValue(item); imageURL != "" {
				result = append(result, map[string]any{
					"type":      "input_image",
					"image_url": imageURL,
				})
			}
		}
	}
	return result
}

func gptRelayImageURLValue(item map[string]any) string {
	if direct := strings.TrimSpace(asString(item["image_url"])); direct != "" {
		return direct
	}
	imageURL, _ := item["image_url"].(map[string]any)
	if imageURL == nil {
		return ""
	}
	return strings.TrimSpace(asString(imageURL["url"]))
}

func gptRelayNetworkingInstruction() string {
	return "【联网规则】\n模型可自行判断是否联网。\n\n用户明确要求查一下、搜一下、联网查、看最新信息时，要联网。\n\n涉及今天 / 最新 / 当前 / 实时 / 价格 / 行情 / 政策 / 天气 / 农资登记 / 用药标签 / 禁限用变化 / 购买渠道等实时信息时，再联网。\n\n疑难、复杂、高风险问题，如果需要公开权威信息校准，也可以联网。\n\n普通农技知识和图片可见症状，先直接判断。\n关键田间信息缺失时，先追问，不要用联网代替追问。\n\n" + defaultGPTRelayMaxSearchCallsInstruction
}

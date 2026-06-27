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
	defaultPrimaryChatModel                 = "gpt-5.5"
	defaultPrimaryChatAPIMode               = "responses"
	defaultPrimaryChatResponsesReasoning    = "xhigh"
	defaultPrimaryChatResponsesSearchSize   = "low"
	defaultPrimaryChatFirstVisibleTimeout   = 15 * time.Second
	defaultPrimaryChatDialTimeout           = 6 * time.Second
	defaultPrimaryChatTLSHandshakeTimeout   = 6 * time.Second
	defaultPrimaryChatResponseHeaderTimeout = 6 * time.Second
	defaultPrimaryChatIdleConnTimeout       = 60 * time.Second
	defaultPrimaryChatKeyCooldown           = 30 * time.Second
	defaultPrimaryChatKeyMaxAttempts        = 2
)

type PrimaryChatClient struct {
	httpClient      *http.Client
	cooldownMu      sync.Mutex
	keyCooldown     map[string]time.Time
	selectionMu     sync.Mutex
	keySelectionIdx int
}

func NewPrimaryChatClientFromEnv() *PrimaryChatClient {
	return &PrimaryChatClient{
		httpClient:  newPrimaryChatHTTPClient(),
		keyCooldown: map[string]time.Time{},
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
			MaxIdleConns:          120,
			MaxIdleConnsPerHost:   40,
		},
	}
}

func (c *PrimaryChatClient) Enabled() bool {
	return primaryChatConfigured()
}

func (c *PrimaryChatClient) HasKeyConfigured() bool {
	return len(primaryChatKeyEntries()) > 0
}

func (c *PrimaryChatClient) OpenStream(ctx context.Context, messages []BailianMessage, options BailianStreamOptions) (*http.Response, error) {
	if !c.Enabled() {
		return nil, fmt.Errorf("primary chat model disabled")
	}
	if primaryChatUsesResponses() {
		return c.openResponsesStream(ctx, messages)
	}
	return c.openChatCompletionsStream(ctx, messages, options)
}

func (c *PrimaryChatClient) openChatCompletionsStream(ctx context.Context, messages []BailianMessage, options BailianStreamOptions) (*http.Response, error) {
	body := map[string]any{
		"model":    primaryChatModelName(),
		"stream":   true,
		"messages": messages,
	}
	if parseBoolEnv(os.Getenv("CHAT_PRIMARY_CHAT_INCLUDE_USAGE")) {
		body["stream_options"] = map[string]any{
			"include_usage": true,
		}
	}
	if parseBoolEnv(os.Getenv("CHAT_PRIMARY_CHAT_DISABLE_THINKING")) {
		body["enable_thinking"] = false
	}
	if parseBoolEnv(os.Getenv("CHAT_PRIMARY_CHAT_ENABLE_SEARCH")) {
		body["enable_search"] = true
		body["search_options"] = map[string]any{
			"forced_search": primaryChatForceSearch(options.ForceSearch, messages),
		}
	}
	if effort := primaryChatReasoningEffort(); effort != "" {
		body["reasoning_effort"] = effort
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	return c.doPrimaryChatPayloadRequest(ctx, primaryChatCompletionsURL(), payload)
}

func (c *PrimaryChatClient) openResponsesStream(ctx context.Context, messages []BailianMessage) (*http.Response, error) {
	instructions, input := buildPrimaryChatResponsesPrompt(messages)
	body := map[string]any{
		"model":        primaryChatModelName(),
		"stream":       true,
		"instructions": instructions,
		"input":        input,
		"tools": []map[string]any{
			{
				"type":                "web_search",
				"search_context_size": primaryChatResponsesSearchContextSize(),
			},
		},
		"tool_choice": "auto",
		"reasoning": map[string]any{
			"effort": primaryChatResponsesReasoningEffort(),
		},
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	return c.doPrimaryChatPayloadRequest(ctx, primaryChatResponsesURL(), payload)
}

func (c *PrimaryChatClient) doPrimaryChatPayloadRequest(ctx context.Context, endpoint string, payload []byte) (*http.Response, error) {
	keys := primaryChatKeyEntries()
	if len(keys) == 0 {
		return nil, fmt.Errorf("CHAT_PRIMARY_API_KEY(S) is missing")
	}
	maxAttempts := envIntWithDefault("CHAT_PRIMARY_KEY_MAX_ATTEMPTS", defaultPrimaryChatKeyMaxAttempts)
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
		resp, err := c.sendPrimaryChatPayload(ctx, endpoint, payload, key.Value)
		if err != nil {
			c.coolDownKey(key.Value)
			lastErr = err
			if ctx.Err() != nil {
				return nil, err
			}
			continue
		}
		if isPrimaryChatKeyCooldownStatus(resp.StatusCode) {
			c.coolDownKey(key.Value)
		}
		if isPrimaryChatRetryableStatus(resp.StatusCode) && attempt+1 < maxAttempts && ctx.Err() == nil {
			_, _ = readLimitedResponseBody(resp.Body, bailianBodyPreviewLimit)
			_ = resp.Body.Close()
			continue
		}
		return resp, nil
	}
	if lastErr != nil {
		return nil, lastErr
	}
	return nil, fmt.Errorf("CHAT_PRIMARY_API_KEY(S) is missing")
}

func (c *PrimaryChatClient) sendPrimaryChatPayload(ctx context.Context, endpoint string, payload []byte, apiKey string) (*http.Response, error) {
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

func primaryChatEnabled() bool {
	return parseBoolEnv(os.Getenv("CHAT_PRIMARY_ENABLED"))
}

func primaryChatConfigured() bool {
	if !primaryChatEnabled() || len(primaryChatKeyEntries()) == 0 {
		return false
	}
	if primaryChatUsesResponses() {
		return primaryChatResponsesURL() != ""
	}
	return primaryChatCompletionsURL() != ""
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

type primaryChatAPIKeyEntry struct {
	Value string
}

func primaryChatKeyEntries() []primaryChatAPIKeyEntry {
	result := []primaryChatAPIKeyEntry{}
	addKey := func(value string) {
		key := normalizePrimaryChatAPIKey(value)
		if key == "" {
			return
		}
		for _, current := range result {
			if current.Value == key {
				return
			}
		}
		result = append(result, primaryChatAPIKeyEntry{Value: key})
	}

	for i := 1; i <= 50; i++ {
		addKey(os.Getenv(fmt.Sprintf("CHAT_PRIMARY_API_KEY_%d", i)))
	}
	addKey(os.Getenv("CHAT_PRIMARY_API_KEY"))
	for _, key := range splitConfiguredKeys(os.Getenv("CHAT_PRIMARY_API_KEYS")) {
		addKey(key)
	}
	return result
}

func normalizePrimaryChatAPIKey(value string) string {
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

func primaryChatKeyPoolSize() int {
	return len(primaryChatKeyEntries())
}

func (c *PrimaryChatClient) pickNextKeyEntry(keys []primaryChatAPIKeyEntry, attempted map[string]bool) (primaryChatAPIKeyEntry, bool) {
	if len(keys) == 0 {
		return primaryChatAPIKeyEntry{}, false
	}
	now := time.Now()
	start := c.nextSelectionOffset(len(keys))
	var fallback *primaryChatAPIKeyEntry
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
	return primaryChatAPIKeyEntry{}, false
}

func (c *PrimaryChatClient) nextSelectionOffset(modulo int) int {
	if modulo <= 0 {
		return 0
	}
	c.selectionMu.Lock()
	defer c.selectionMu.Unlock()
	start := c.keySelectionIdx
	c.keySelectionIdx = (c.keySelectionIdx + 1) % modulo
	return start
}

func (c *PrimaryChatClient) isKeyCoolingDown(key string, now time.Time) bool {
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

func (c *PrimaryChatClient) coolDownKey(key string) {
	c.cooldownMu.Lock()
	defer c.cooldownMu.Unlock()
	duration := envDurationWithDefault("CHAT_PRIMARY_KEY_COOLDOWN_SECONDS", defaultPrimaryChatKeyCooldown)
	if duration <= 0 {
		delete(c.keyCooldown, key)
		return
	}
	c.keyCooldown[key] = time.Now().Add(duration)
}

func isPrimaryChatRetryableStatus(status int) bool {
	return status == http.StatusUnauthorized ||
		status == http.StatusForbidden ||
		status == http.StatusTooManyRequests ||
		status == http.StatusInternalServerError ||
		status == http.StatusBadGateway ||
		status == http.StatusServiceUnavailable ||
		status == http.StatusGatewayTimeout
}

func isPrimaryChatKeyCooldownStatus(status int) bool {
	return isPrimaryChatRetryableStatus(status)
}

func primaryChatModelName() string {
	return firstNonEmpty(strings.TrimSpace(os.Getenv("CHAT_PRIMARY_MODEL")), defaultPrimaryChatModel)
}

func primaryChatProviderLabel() string {
	label := strings.TrimSpace(os.Getenv("CHAT_PRIMARY_PROVIDER_LABEL"))
	if label == "" {
		return "中转站"
	}
	safe := sanitizeDashScopeErrorMessage(label)
	if safe == "" || strings.Contains(safe, "[redacted]") || strings.Contains(safe, "[url]") || strings.Contains(safe, "[phone]") {
		return "中转站"
	}
	return truncateRunes(safe, 20)
}

func primaryChatAPIMode() string {
	mode := strings.ToLower(strings.TrimSpace(os.Getenv("CHAT_PRIMARY_API_MODE")))
	if mode == "" {
		return defaultPrimaryChatAPIMode
	}
	switch mode {
	case "chat", "chat_completions", "chat-completions", "completions":
		return "chat"
	case "responses", "response":
		return "responses"
	default:
		return defaultPrimaryChatAPIMode
	}
}

func primaryChatUsesResponses() bool {
	return primaryChatAPIMode() == "responses"
}

func primaryChatReasoningEffort() string {
	return strings.TrimSpace(os.Getenv("CHAT_PRIMARY_REASONING_EFFORT"))
}

func primaryChatResponsesReasoningEffort() string {
	return firstNonEmpty(strings.TrimSpace(os.Getenv("CHAT_PRIMARY_RESPONSES_REASONING_EFFORT")), defaultPrimaryChatResponsesReasoning)
}

func primaryChatResponsesSearchContextSize() string {
	return firstNonEmpty(strings.TrimSpace(os.Getenv("CHAT_PRIMARY_RESPONSES_SEARCH_CONTEXT_SIZE")), defaultPrimaryChatResponsesSearchSize)
}

func resolvePrimaryChatFirstVisibleTimeout(maxDuration time.Duration) time.Duration {
	duration := envDurationWithDefault("CHAT_PRIMARY_FIRST_VISIBLE_TIMEOUT_SECONDS", defaultPrimaryChatFirstVisibleTimeout)
	if duration <= 0 {
		return defaultPrimaryChatFirstVisibleTimeout
	}
	if maxDuration > 0 && duration > maxDuration {
		return maxDuration
	}
	return duration
}

func primaryChatShouldHandle(options BailianStreamOptions) bool {
	if primaryChatUsesResponses() {
		return true
	}
	return !options.ForceSearch
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
		return primaryChatSafeEndpointURL(direct)
	}
	baseURL := strings.TrimSpace(os.Getenv("CHAT_PRIMARY_BASE_URL"))
	if baseURL == "" {
		return ""
	}
	trimmed := strings.TrimRight(baseURL, "/")
	lower := strings.ToLower(trimmed)
	switch {
	case strings.HasSuffix(lower, "/chat/completions"):
		return primaryChatSafeEndpointURL(trimmed)
	case strings.HasSuffix(lower, "/v1"):
		return primaryChatSafeEndpointURL(trimmed + "/chat/completions")
	default:
		return primaryChatSafeEndpointURL(trimmed + "/v1/chat/completions")
	}
}

func primaryChatResponsesURL() string {
	if direct := strings.TrimSpace(os.Getenv("CHAT_PRIMARY_RESPONSES_URL")); direct != "" {
		return primaryChatSafeEndpointURL(direct)
	}
	baseURL := strings.TrimSpace(os.Getenv("CHAT_PRIMARY_BASE_URL"))
	if baseURL == "" {
		return ""
	}
	trimmed := strings.TrimRight(baseURL, "/")
	lower := strings.ToLower(trimmed)
	switch {
	case strings.HasSuffix(lower, "/responses"):
		return primaryChatSafeEndpointURL(trimmed)
	case strings.HasSuffix(lower, "/v1"):
		return primaryChatSafeEndpointURL(trimmed + "/responses")
	default:
		return primaryChatSafeEndpointURL(trimmed + "/v1/responses")
	}
}

func primaryChatSafeEndpointURL(raw string) string {
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
	case scheme == "http" && primaryChatLocalHTTPHost(parsed.Hostname()):
		return trimmed
	default:
		return ""
	}
}

func primaryChatLocalHTTPHost(host string) bool {
	normalized := strings.ToLower(strings.Trim(host, "[]"))
	if normalized == "localhost" {
		return true
	}
	ip := net.ParseIP(normalized)
	return ip != nil && ip.IsLoopback()
}

func buildPrimaryChatResponsesPrompt(messages []BailianMessage) (string, []map[string]any) {
	instructionParts := []string{}
	input := make([]map[string]any, 0, len(messages))
	for _, message := range messages {
		role := strings.ToLower(strings.TrimSpace(message.Role))
		if role == "system" {
			if text := primaryChatInstructionText(message.Content); text != "" {
				instructionParts = append(instructionParts, text)
			}
			continue
		}
		input = append(input, map[string]any{
			"role":    primaryChatResponsesRole(role),
			"content": primaryChatResponsesContent(message.Content),
		})
	}
	instructionParts = append(instructionParts, primaryChatResponsesToolInstruction())
	return strings.Join(instructionParts, "\n\n"), input
}

func primaryChatInstructionText(content any) string {
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

func primaryChatResponsesRole(role string) string {
	switch role {
	case "assistant":
		return "assistant"
	default:
		return "user"
	}
}

func primaryChatResponsesContent(content any) any {
	if parts, ok := primaryChatResponsesContentParts(content); ok && len(parts) > 0 {
		return parts
	}
	return content
}

func primaryChatResponsesContentParts(content any) ([]map[string]any, bool) {
	switch value := content.(type) {
	case []map[string]any:
		return primaryChatResponsesContentPartsFromMaps(value), true
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
		return primaryChatResponsesContentPartsFromMaps(maps), true
	default:
		return nil, false
	}
}

func primaryChatResponsesContentPartsFromMaps(items []map[string]any) []map[string]any {
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
			if imageURL := primaryChatImageURLValue(item); imageURL != "" {
				result = append(result, map[string]any{
					"type":      "input_image",
					"image_url": imageURL,
				})
			}
		}
	}
	return result
}

func primaryChatImageURLValue(item map[string]any) string {
	if direct := strings.TrimSpace(asString(item["image_url"])); direct != "" {
		return direct
	}
	imageURL, _ := item["image_url"].(map[string]any)
	if imageURL == nil {
		return ""
	}
	return strings.TrimSpace(asString(imageURL["url"]))
}

func primaryChatResponsesToolInstruction() string {
	return "联网搜索使用规则：仅当本轮问题涉及最新信息、价格行情、政策公告、购买渠道、天气、灾害预警或其他时效性判断时，以最快速度联网搜索；拿到足够信息后立刻回答，不解释搜索过程。普通农技知识、图片可见信息和非时效性问题直接回答。"
}

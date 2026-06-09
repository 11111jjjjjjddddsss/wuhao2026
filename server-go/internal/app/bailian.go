package app

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	neturl "net/url"
	"os"
	"regexp"
	"strings"
	"sync"
	"time"
)

type BailianClient struct {
	httpClient      *http.Client
	cooldownMu      sync.Mutex
	keyCooldown     map[string]time.Time
	selectionMu     sync.Mutex
	keySelectionIdx int
	keySelection    keySelectionMode
	autoMu          sync.Mutex
	autoRequests    []time.Time
	autoRRUntil     time.Time
}

const (
	unifiedModelTemperature   = 0.8
	defaultBailianKeyCooldown = 1 * time.Second

	keySelectionModeFallback keySelectionMode = iota
	keySelectionModeRoundRobin
	keySelectionModeAuto

	defaultDashScopeDialTimeout           = 10 * time.Second
	defaultDashScopeTLSHandshakeTimeout   = 10 * time.Second
	defaultDashScopeResponseHeaderTimeout = 60 * time.Second
	defaultDashScopeIdleConnTimeout       = 90 * time.Second
	defaultDashScopeAutoRRWindow          = 10 * time.Second
	defaultDashScopeAutoRRHold            = 60 * time.Second
	defaultDashScopeAutoRRMinRequests     = 20
	bailianBodyPreviewLimit               = 64 * 1024
	dashScopeGenerationBodyLimit          = 1024 * 1024
)

type keySelectionMode int

var errResponseBodyTooLarge = errors.New("response body too large")

func NewBailianClient() *BailianClient {
	return &BailianClient{
		httpClient:      newBailianHTTPClient(),
		keyCooldown:     map[string]time.Time{},
		keySelection:    getDashScopeKeySelectionMode(),
		keySelectionIdx: 0,
	}
}

func newBailianHTTPClient() *http.Client {
	return &http.Client{
		Transport: &http.Transport{
			Proxy: http.ProxyFromEnvironment,
			DialContext: (&net.Dialer{
				Timeout:   envDurationWithDefault("DASHSCOPE_DIAL_TIMEOUT_SECONDS", defaultDashScopeDialTimeout),
				KeepAlive: 30 * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   envDurationWithDefault("DASHSCOPE_TLS_HANDSHAKE_TIMEOUT_SECONDS", defaultDashScopeTLSHandshakeTimeout),
			ResponseHeaderTimeout: envDurationWithDefault("DASHSCOPE_RESPONSE_HEADER_TIMEOUT_SECONDS", defaultDashScopeResponseHeaderTimeout),
			IdleConnTimeout:       envDurationWithDefault("DASHSCOPE_IDLE_CONN_TIMEOUT_SECONDS", defaultDashScopeIdleConnTimeout),
			ExpectContinueTimeout: 1 * time.Second,
			MaxIdleConns:          100,
			MaxIdleConnsPerHost:   10,
		},
	}
}

func (c *BailianClient) HasKeyConfigured() bool {
	return len(c.keyEntries()) > 0
}

func (c *BailianClient) OpenStream(ctx context.Context, messages []BailianMessage) (*http.Response, error) {
	body := map[string]any{
		"model":           "qwen3.5-plus",
		"stream":          true,
		"temperature":     unifiedModelTemperature,
		"enable_thinking": false,
		"stream_options": map[string]any{
			"include_usage": true,
		},
		"extra_body": map[string]any{
			"enable_search": true,
			"search_options": map[string]any{
				"search_strategy": "turbo",
				"forced_search":   false,
			},
		},
		"messages": messages,
	}
	return c.doJSONRequest(ctx, "text/event-stream", body)
}

func (c *BailianClient) OpenCompletion(ctx context.Context, body map[string]any) (*http.Response, error) {
	return c.doJSONRequest(ctx, "application/json", body)
}

func (c *BailianClient) GenerateDailyAgriCard(ctx context.Context, messages []BailianMessage) (string, []DailyAgriSearchSource, bailianModelUsage, error) {
	model := dailyAgriCardModel
	if dailyAgriUsesResponsesAPI(model) {
		return c.generateDailyAgriCardWithResponses(ctx, model, messages)
	}
	return c.generateDailyAgriCardWithGeneration(ctx, model, messages)
}

func (c *BailianClient) generateDailyAgriCardWithGeneration(ctx context.Context, model string, messages []BailianMessage) (string, []DailyAgriSearchSource, bailianModelUsage, error) {
	body := map[string]any{
		"model": model,
		"input": map[string]any{
			"messages": messages,
		},
		"parameters": map[string]any{
			"result_format":   "message",
			"temperature":     unifiedModelTemperature,
			"enable_thinking": false,
			"enable_search":   true,
			"search_options": map[string]any{
				"search_strategy": dailyAgriSearchStrategy,
				"forced_search":   true,
				"enable_source":   true,
				"freshness":       7,
				"intention_options": map[string]any{
					"prompt_intervene": "检索近7天内中国种植业生产经营相关的具体新闻、通知、农技文章、市场信息或地方农业信息；同等质量下优先今天或昨天发布的最新材料，避免旧闻和同一事件重复改写。检索阶段不要限定固定网站、不要只围绕少数官网或媒体、不要使用站点白名单思路；请全网宽搜具体事实，再按发布时间、正式来源、农业实用价值和是否种植侧筛选。农业大类按种植和养殖理解，今日农情只取种植侧，养殖侧全部排除。不要只搜农业新闻或农情，请分散覆盖：种子/种苗/种业/品种审定推广/供种备耕/种子质量抽检/种子检疫，病虫草害/植保/农药/除草剂/安全用药/农技推广/作物苗情，影响作物和农时的农业气象灾害或农田防灾减灾（旱涝、霜冻、倒春寒、干热风、高温热害等），农时农事/机收机播/农机作业/粮油菜果茶，肥料/化肥/水肥管理/土壤墒情，农资供需/农产品产地价格/批发市场/流通/补贴保险。来源不限定固定站点；同等质量下优先官方、农业农村部门、农技推广、气象、主流媒体和农业专业媒体，也可选择地方农业信息、市场流通、农资、种业、植保等专业来源。搜索来源只作事实核对和后台排查，用户端只展示标题、摘要和来源名称、不点击链接；不要因为URL像首页或栏目页就丢弃事实清楚的种植业材料，但不能拿没有事实支撑的入口页、广告页或聚合页凑数。排除广告软文、招商加盟、带货导购、品牌推广、联系方式、二维码、电商促销、网传/爆料/传言/谣言/未经证实，以及普通天气预报、生活天气、旅游出行天气、畜牧、水产、养殖、动物疫病、生猪、猪肉、猪价、家禽、禽蛋、牛羊、饲料、兽药、渔业等非种植领域内容。",
				},
			},
		},
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return "", nil, bailianModelUsage{}, err
	}
	resp, err := c.doJSONPayloadRequest(ctx, c.buildDashScopeGenerationURL(), "application/json", payload)
	if err != nil {
		return "", nil, bailianModelUsage{}, err
	}
	defer resp.Body.Close()
	bodyBytes, err := readLimitedResponseBody(resp.Body, dashScopeGenerationBodyLimit)
	if err != nil {
		return "", nil, bailianModelUsage{}, err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", nil, bailianModelUsage{}, formatDashScopeStatusError(resp.StatusCode, bodyBytes)
	}

	var decoded dashScopeGenerationResponse
	if err := json.Unmarshal(bodyBytes, &decoded); err != nil {
		return "", nil, bailianModelUsage{}, err
	}
	if decoded.Code != "" {
		return "", nil, bailianModelUsage{}, fmt.Errorf("dashscope error %s: %s", decoded.Code, decoded.Message)
	}
	if len(decoded.Output.Choices) == 0 {
		return "", nil, bailianModelUsage{}, fmt.Errorf("dashscope response missing choices")
	}
	content := strings.TrimSpace(decoded.Output.Choices[0].Message.Content)
	if content == "" {
		return "", nil, bailianModelUsage{}, fmt.Errorf("dashscope response empty content")
	}
	sources := make([]DailyAgriSearchSource, 0, len(decoded.Output.SearchInfo.SearchResults))
	for _, source := range decoded.Output.SearchInfo.SearchResults {
		url := strings.TrimSpace(source.URL)
		if url == "" {
			continue
		}
		sources = append(sources, DailyAgriSearchSource{
			Index:    source.Index,
			Title:    strings.TrimSpace(source.Title),
			URL:      url,
			SiteName: firstNonBlank(source.SiteName, source.Site, source.Host),
		})
	}
	return content, sources, decoded.Usage, nil
}

func (c *BailianClient) generateDailyAgriCardWithResponses(ctx context.Context, model string, messages []BailianMessage) (string, []DailyAgriSearchSource, bailianModelUsage, error) {
	systemPrompt, userPrompt := splitDailyAgriMessages(messages)
	if strings.TrimSpace(userPrompt) == "" {
		return "", nil, bailianModelUsage{}, fmt.Errorf("daily agri prompt missing user content")
	}
	body := map[string]any{
		"model":       model,
		"input":       userPrompt,
		"tool_choice": "required",
		"tools": []map[string]any{
			{"type": "web_search"},
		},
		"reasoning": map[string]any{
			"effort": "none",
		},
		"temperature": unifiedModelTemperature,
		"store":       false,
	}
	if systemPrompt != "" {
		body["instructions"] = systemPrompt
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return "", nil, bailianModelUsage{}, err
	}
	resp, err := c.doJSONPayloadRequest(ctx, c.buildResponsesURL(), "application/json", payload)
	if err != nil {
		return "", nil, bailianModelUsage{}, err
	}
	defer resp.Body.Close()
	bodyBytes, err := readLimitedResponseBody(resp.Body, dashScopeGenerationBodyLimit)
	if err != nil {
		return "", nil, bailianModelUsage{}, err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", nil, bailianModelUsage{}, formatDashScopeStatusError(resp.StatusCode, bodyBytes)
	}

	var decoded dashScopeResponsesResponse
	if err := json.Unmarshal(bodyBytes, &decoded); err != nil {
		return "", nil, bailianModelUsage{}, err
	}
	if decoded.Error != nil && strings.TrimSpace(decoded.Error.Message) != "" {
		return "", nil, bailianModelUsage{}, fmt.Errorf("dashscope error %s: %s", decoded.Error.Code, decoded.Error.Message)
	}

	content := strings.TrimSpace(decoded.OutputText)
	if content == "" {
		for _, item := range decoded.Output {
			if item.Type != "message" {
				continue
			}
			for _, chunk := range item.Content {
				text := strings.TrimSpace(chunk.Text)
				if text != "" {
					content = text
					break
				}
			}
			if content != "" {
				break
			}
		}
	}
	if content == "" {
		return "", nil, bailianModelUsage{}, fmt.Errorf("dashscope response empty content")
	}

	sources := make([]DailyAgriSearchSource, 0, 8)
	seen := map[string]struct{}{}
	for _, item := range decoded.Output {
		if item.Type != "web_search_call" {
			continue
		}
		for _, source := range item.Action.Sources {
			url := strings.TrimSpace(source.URL)
			if url == "" {
				continue
			}
			normalized := normalizeResponseSourceURL(url)
			if normalized == "" {
				continue
			}
			if _, ok := seen[normalized]; ok {
				continue
			}
			seen[normalized] = struct{}{}
			sources = append(sources, DailyAgriSearchSource{
				Index:    len(sources) + 1,
				Title:    strings.TrimSpace(firstNonBlank(source.Title, source.URL)),
				URL:      url,
				SiteName: strings.TrimSpace(firstNonBlank(source.SiteName, hostLabelFromURL(url), source.Title)),
			})
		}
	}
	if len(sources) == 0 {
		return "", nil, bailianModelUsage{}, fmt.Errorf("dashscope response missing search sources")
	}
	return content, sources, decoded.Usage, nil
}

func (c *BailianClient) doJSONRequest(ctx context.Context, accept string, body map[string]any) (*http.Response, error) {
	payload, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	return c.doJSONPayloadRequest(ctx, c.buildURL(), accept, payload)
}

func (c *BailianClient) doJSONPayloadRequest(ctx context.Context, url string, accept string, payload []byte) (*http.Response, error) {
	keys := c.keyEntries()
	if len(keys) == 0 {
		return nil, fmt.Errorf("DASHSCOPE_API_KEY(S) is missing")
	}
	c.observeAutoKeySelectionRequest(len(keys))

	attempted := map[string]bool{}
	var lastResponse *http.Response
	for attempt := 0; attempt < len(keys); attempt++ {
		key, ok := c.pickNextKeyEntry(keys, attempted)
		if !ok {
			break
		}
		attempted[key.Value] = true

		resp, err := c.sendJSONPayload(ctx, url, accept, payload, key.Value)
		if err != nil {
			return nil, err
		}
		if !isBailianFailoverCandidateStatus(resp.StatusCode) {
			return resp, nil
		}

		remaining := len(attempted) < len(keys)
		if !remaining {
			c.coolDownKey(key.Value)
			return resp, nil
		}

		bodyBytes, readErr := readLimitedResponseBody(resp.Body, bailianBodyPreviewLimit)
		_ = resp.Body.Close()
		if readErr != nil && !errors.Is(readErr, errResponseBodyTooLarge) {
			return nil, readErr
		}
		snapshot := cloneHTTPResponse(resp, bodyBytes)
		if !shouldFailoverBailianResponse(resp.StatusCode, bodyBytes) {
			return snapshot, nil
		}
		c.activateAutoRoundRobin(time.Now())
		c.coolDownKey(key.Value)
		lastResponse = snapshot
	}
	if lastResponse != nil {
		return lastResponse, nil
	}
	return nil, fmt.Errorf("DASHSCOPE_API_KEY(S) is missing")
}

func readLimitedResponseBody(body io.Reader, limit int64) ([]byte, error) {
	if limit <= 0 {
		limit = bailianBodyPreviewLimit
	}
	data, err := io.ReadAll(io.LimitReader(body, limit+1))
	if err != nil {
		return nil, err
	}
	if int64(len(data)) > limit {
		return data[:limit], errResponseBodyTooLarge
	}
	return data, nil
}

func (c *BailianClient) sendJSONPayload(ctx context.Context, url string, accept string, payload []byte, apiKey string) (*http.Response, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(payload))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", accept)
	req.Header.Set("Cache-Control", "no-cache")

	return c.httpClient.Do(req)
}

func (c *BailianClient) buildURL() string {
	baseURL := strings.TrimSpace(os.Getenv("BAILIAN_BASE_URL"))
	if baseURL == "" {
		baseURL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
	}
	return strings.TrimRight(baseURL, "/") + "/chat/completions"
}

func (c *BailianClient) buildResponsesURL() string {
	baseURL := strings.TrimSpace(os.Getenv("BAILIAN_BASE_URL"))
	if baseURL == "" {
		baseURL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
	}
	return strings.TrimRight(baseURL, "/") + "/responses"
}

func (c *BailianClient) buildDashScopeGenerationURL() string {
	baseURL := strings.TrimSpace(os.Getenv("DASHSCOPE_BASE_URL"))
	if baseURL == "" {
		baseURL = "https://dashscope.aliyuncs.com/api/v1"
	}
	return strings.TrimRight(baseURL, "/") + "/services/aigc/text-generation/generation"
}

func (c *BailianClient) keys() []string {
	entries := c.keyEntries()
	result := make([]string, 0, len(entries))
	for _, entry := range entries {
		result = append(result, entry.Value)
	}
	return result
}

type bailianAPIKeyEntry struct {
	Value string
}

func (c *BailianClient) keyEntries() []bailianAPIKeyEntry {
	result := []bailianAPIKeyEntry{}
	addKey := func(value string) {
		key := strings.TrimSpace(value)
		if key == "" {
			return
		}
		for _, current := range result {
			if current.Value == key {
				return
			}
		}
		result = append(result, bailianAPIKeyEntry{Value: key})
	}

	for i := 1; i <= 3; i++ {
		name := fmt.Sprintf("DASHSCOPE_API_KEY_%d", i)
		addKey(os.Getenv(name))
	}
	addKey(os.Getenv("DASHSCOPE_API_KEY"))
	for _, key := range splitConfiguredKeys(os.Getenv("DASHSCOPE_API_KEYS")) {
		addKey(key)
	}
	return result
}

func splitConfiguredKeys(value string) []string {
	return strings.FieldsFunc(value, func(r rune) bool {
		return r == ',' || r == ';' || r == '\n' || r == '\r'
	})
}

func (c *BailianClient) pickNextKeyEntry(keys []bailianAPIKeyEntry, attempted map[string]bool) (bailianAPIKeyEntry, bool) {
	switch c.effectiveKeySelectionMode(len(keys)) {
	case keySelectionModeRoundRobin:
		return c.pickNextKeyEntryRoundRobin(keys, attempted)
	default:
		return c.pickNextKeyEntryFallback(keys, attempted)
	}
}

func (c *BailianClient) effectiveKeySelectionMode(keyCount int) keySelectionMode {
	if c.keySelection != keySelectionModeAuto || keyCount < 2 {
		return c.keySelection
	}
	now := time.Now()
	c.autoMu.Lock()
	defer c.autoMu.Unlock()
	if now.Before(c.autoRRUntil) {
		return keySelectionModeRoundRobin
	}
	return keySelectionModeFallback
}

func (c *BailianClient) pickNextKeyEntryFallback(keys []bailianAPIKeyEntry, attempted map[string]bool) (bailianAPIKeyEntry, bool) {
	if len(keys) == 0 {
		return bailianAPIKeyEntry{}, false
	}
	now := time.Now()
	var fallback *bailianAPIKeyEntry
	for offset := 0; offset < len(keys); offset++ {
		key := keys[offset]
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
	return bailianAPIKeyEntry{}, false
}

func (c *BailianClient) pickNextKeyEntryRoundRobin(keys []bailianAPIKeyEntry, attempted map[string]bool) (bailianAPIKeyEntry, bool) {
	if len(keys) == 0 {
		return bailianAPIKeyEntry{}, false
	}
	now := time.Now()
	start := c.nextSelectionOffset(len(keys))
	var fallback *bailianAPIKeyEntry
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
	return bailianAPIKeyEntry{}, false
}

func (c *BailianClient) nextSelectionOffset(modulo int) int {
	if modulo <= 0 {
		return 0
	}
	c.selectionMu.Lock()
	defer c.selectionMu.Unlock()
	start := c.keySelectionIdx
	c.keySelectionIdx = (c.keySelectionIdx + 1) % modulo
	return start
}

func (c *BailianClient) observeAutoKeySelectionRequest(keyCount int) {
	if c.keySelection != keySelectionModeAuto || keyCount < 2 {
		return
	}
	window := envDurationWithDefault("DASHSCOPE_AUTO_ROUND_ROBIN_WINDOW_SECONDS", defaultDashScopeAutoRRWindow)
	minRequests := envIntWithDefault("DASHSCOPE_AUTO_ROUND_ROBIN_MIN_REQUESTS", defaultDashScopeAutoRRMinRequests)
	if window <= 0 || minRequests <= 0 {
		return
	}

	now := time.Now()
	c.autoMu.Lock()
	defer c.autoMu.Unlock()

	cutoff := now.Add(-window)
	kept := c.autoRequests[:0]
	for _, requestTime := range c.autoRequests {
		if requestTime.After(cutoff) {
			kept = append(kept, requestTime)
		}
	}
	kept = append(kept, now)
	c.autoRequests = kept
	if len(c.autoRequests) >= minRequests {
		c.autoRRUntil = laterTime(c.autoRRUntil, now.Add(autoRoundRobinHoldDuration()))
	}
}

func (c *BailianClient) activateAutoRoundRobin(now time.Time) {
	if c.keySelection != keySelectionModeAuto {
		return
	}
	c.autoMu.Lock()
	defer c.autoMu.Unlock()
	c.autoRRUntil = laterTime(c.autoRRUntil, now.Add(autoRoundRobinHoldDuration()))
}

func (c *BailianClient) isKeyCoolingDown(key string, now time.Time) bool {
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

func (c *BailianClient) coolDownKey(key string) {
	c.cooldownMu.Lock()
	defer c.cooldownMu.Unlock()
	duration := envDurationWithDefault("DASHSCOPE_KEY_COOLDOWN_SECONDS", defaultBailianKeyCooldown)
	if duration <= 0 {
		delete(c.keyCooldown, key)
		return
	}
	c.keyCooldown[key] = time.Now().Add(duration)
}

func isBailianFailoverCandidateStatus(status int) bool {
	return status == http.StatusBadRequest ||
		status == http.StatusUnauthorized ||
		status == http.StatusForbidden ||
		status == http.StatusTooManyRequests
}

func shouldFailoverBailianResponse(status int, body []byte) bool {
	switch status {
	case http.StatusUnauthorized, http.StatusForbidden, http.StatusTooManyRequests:
		return true
	case http.StatusBadRequest:
		lower := strings.ToLower(string(body))
		for _, marker := range []string{
			"rate limit",
			"requests rate",
			"current requests",
			"current quota",
			"allocated quota",
			"quota exceeded",
			"too quickly",
			"throttl",
		} {
			if strings.Contains(lower, marker) {
				return true
			}
		}
	}
	return false
}

func formatDashScopeStatusError(status int, body []byte) error {
	code, message := extractDashScopeErrorCodeMessage(body)
	detail := strings.TrimSpace(strings.Trim(strings.TrimSpace(code)+": "+sanitizeDashScopeErrorMessage(message), ": "))
	if detail == "" {
		return fmt.Errorf("dashscope status %d", status)
	}
	return fmt.Errorf("dashscope status %d: %s", status, detail)
}

func extractDashScopeErrorCodeMessage(body []byte) (string, string) {
	payload := map[string]any{}
	if err := json.Unmarshal(body, &payload); err != nil {
		return "", ""
	}
	code := strings.TrimSpace(asString(payload["code"]))
	message := strings.TrimSpace(asString(payload["message"]))
	if nested, _ := payload["error"].(map[string]any); nested != nil {
		if code == "" {
			code = strings.TrimSpace(asString(nested["code"]))
		}
		if message == "" {
			message = strings.TrimSpace(asString(nested["message"]))
		}
	}
	return code, message
}

var (
	dashScopeURLPattern           = regexp.MustCompile(`https?://[^\s"'<>]+`)
	dashScopeBearerPattern        = regexp.MustCompile(`(?i)bearer\s+[A-Za-z0-9._~+/=-]+`)
	dashScopeAPIKeyPattern        = regexp.MustCompile(`sk-[A-Za-z0-9_-]{8,}`)
	dashScopePhonePattern         = regexp.MustCompile(`1[3-9][0-9]{9}`)
	dashScopeCredentialPattern    = regexp.MustCompile(`(?i)(AccessKey(Id|Secret)?|SecurityToken|Signature(Nonce)?)[=:]\s*[^,\s"']+`)
	dashScopeGenericSecretPattern = regexp.MustCompile(`(?i)\b(token|api[_-]?key|secret|password|passwd|pwd)\b\s*[=:]\s*[^,\s"']+`)
)

func sanitizeDashScopeErrorMessage(message string) string {
	cleaned := strings.TrimSpace(message)
	if cleaned == "" {
		return ""
	}
	cleaned = dashScopeURLPattern.ReplaceAllString(cleaned, "[url]")
	cleaned = dashScopeBearerPattern.ReplaceAllString(cleaned, "Bearer [redacted]")
	cleaned = dashScopeAPIKeyPattern.ReplaceAllString(cleaned, "sk-[redacted]")
	cleaned = dashScopePhonePattern.ReplaceAllString(cleaned, "[phone]")
	cleaned = dashScopeCredentialPattern.ReplaceAllString(cleaned, "[redacted]")
	cleaned = dashScopeGenericSecretPattern.ReplaceAllString(cleaned, "[redacted]")
	cleaned = strings.Join(strings.Fields(cleaned), " ")
	return truncateRunes(cleaned, 160)
}

func cloneHTTPResponse(resp *http.Response, body []byte) *http.Response {
	cloned := new(http.Response)
	*cloned = *resp
	cloned.Body = io.NopCloser(bytes.NewReader(body))
	cloned.ContentLength = int64(len(body))
	return cloned
}

type dashScopeGenerationResponse struct {
	Code    string `json:"code,omitempty"`
	Message string `json:"message,omitempty"`
	Output  struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
		SearchInfo struct {
			SearchResults []struct {
				Index    int    `json:"index"`
				Title    string `json:"title"`
				URL      string `json:"url"`
				SiteName string `json:"site_name"`
				Site     string `json:"site"`
				Host     string `json:"host"`
			} `json:"search_results"`
		} `json:"search_info"`
	} `json:"output"`
	Usage bailianModelUsage `json:"usage"`
}

type dashScopeResponsesResponse struct {
	Output     []dashScopeResponsesOutput `json:"output"`
	OutputText string                     `json:"output_text"`
	Usage      bailianModelUsage          `json:"usage"`
	Error      *struct {
		Code    string `json:"code"`
		Message string `json:"message"`
	} `json:"error"`
}

type bailianModelUsage struct {
	InputTokens      int `json:"input_tokens"`
	OutputTokens     int `json:"output_tokens"`
	TotalTokens      int `json:"total_tokens"`
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
	Plugins          struct {
		Search struct {
			Count int `json:"count"`
		} `json:"search"`
	} `json:"plugins"`
}

func (u bailianModelUsage) normalizedInputTokens() int {
	if u.InputTokens > 0 {
		return u.InputTokens
	}
	return u.PromptTokens
}

func (u bailianModelUsage) normalizedOutputTokens() int {
	if u.OutputTokens > 0 {
		return u.OutputTokens
	}
	return u.CompletionTokens
}

func (u bailianModelUsage) normalizedTotalTokens() int {
	if u.TotalTokens > 0 {
		return u.TotalTokens
	}
	return u.normalizedInputTokens() + u.normalizedOutputTokens()
}

func (u bailianModelUsage) searchCount() int {
	return u.Plugins.Search.Count
}

func (u bailianModelUsage) hasAny() bool {
	return u.normalizedInputTokens() > 0 ||
		u.normalizedOutputTokens() > 0 ||
		u.normalizedTotalTokens() > 0 ||
		u.searchCount() > 0
}

func appendBailianUsageLogAttrs(attrs []any, usage bailianModelUsage) []any {
	if !usage.hasAny() {
		return attrs
	}
	attrs = append(attrs,
		"model_input_tokens", usage.normalizedInputTokens(),
		"model_output_tokens", usage.normalizedOutputTokens(),
		"model_total_tokens", usage.normalizedTotalTokens(),
	)
	if searchCount := usage.searchCount(); searchCount > 0 {
		attrs = append(attrs, "model_search_count", searchCount)
	}
	return attrs
}

type dashScopeResponsesOutput struct {
	Type    string `json:"type"`
	Content []struct {
		Type string `json:"type"`
		Text string `json:"text"`
	} `json:"content"`
	Action struct {
		Type    string `json:"type"`
		Query   string `json:"query"`
		Sources []struct {
			Type     string `json:"type"`
			URL      string `json:"url"`
			Title    string `json:"title"`
			SiteName string `json:"site_name"`
		} `json:"sources"`
	} `json:"action"`
}

func firstNonBlank(values ...string) string {
	for _, value := range values {
		if trimmed := strings.TrimSpace(value); trimmed != "" {
			return trimmed
		}
	}
	return ""
}

func splitDailyAgriMessages(messages []BailianMessage) (string, string) {
	var systemPrompt string
	var userParts []string
	for _, message := range messages {
		text, ok := message.Content.(string)
		if !ok {
			continue
		}
		text = strings.TrimSpace(text)
		if text == "" {
			continue
		}
		switch strings.ToLower(strings.TrimSpace(message.Role)) {
		case "system":
			if systemPrompt == "" {
				systemPrompt = text
			}
		case "user":
			userParts = append(userParts, text)
		}
	}
	return systemPrompt, strings.Join(userParts, "\n\n")
}

func dailyAgriUsesResponsesAPI(model string) bool {
	switch strings.TrimSpace(strings.ToLower(model)) {
	case "qwen3.5-plus", "qwen3.5-plus-2026-02-15", "qwen3.7-plus", "qwen3.7-plus-2026-05-26":
		return true
	default:
		return false
	}
}

func normalizeResponseSourceURL(rawURL string) string {
	parsed, err := neturl.Parse(strings.TrimSpace(rawURL))
	if err != nil || parsed.Scheme == "" || parsed.Host == "" {
		return ""
	}
	parsed.Fragment = ""
	return strings.TrimRight(parsed.String(), "/")
}

func hostLabelFromURL(rawURL string) string {
	parsed, err := neturl.Parse(strings.TrimSpace(rawURL))
	if err != nil {
		return ""
	}
	host := strings.TrimSpace(parsed.Hostname())
	host = strings.TrimPrefix(strings.TrimPrefix(host, "www."), "m.")
	return host
}

func autoRoundRobinHoldDuration() time.Duration {
	duration := envDurationWithDefault("DASHSCOPE_AUTO_ROUND_ROBIN_HOLD_SECONDS", defaultDashScopeAutoRRHold)
	if duration <= 0 {
		return defaultDashScopeAutoRRHold
	}
	return duration
}

func laterTime(left time.Time, right time.Time) time.Time {
	if right.After(left) {
		return right
	}
	return left
}

func getDashScopeKeySelectionMode() keySelectionMode {
	mode := strings.ToLower(strings.TrimSpace(os.Getenv("DASHSCOPE_KEY_SELECTION_MODE")))
	switch mode {
	case "auto", "":
		return keySelectionModeAuto
	case "rr", "roundrobin", "round-robin", "round_robin":
		return keySelectionModeRoundRobin
	case "fallback", "priority", "primary-first", "primary_fallback":
		return keySelectionModeFallback
	default:
		return keySelectionModeAuto
	}
}

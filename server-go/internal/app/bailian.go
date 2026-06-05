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
	"os"
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
		"model":       "qwen3.5-plus",
		"stream":      true,
		"temperature": unifiedModelTemperature,
		"extra_body": map[string]any{
			"enable_thinking": false,
			"enable_search":   true,
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

func (c *BailianClient) GenerateDailyAgriCard(ctx context.Context, messages []BailianMessage) (string, []DailyAgriSearchSource, error) {
	body := map[string]any{
		"model": dailyAgriCardModel,
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
			},
		},
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return "", nil, err
	}
	resp, err := c.doJSONPayloadRequest(ctx, c.buildDashScopeGenerationURL(), "application/json", payload)
	if err != nil {
		return "", nil, err
	}
	defer resp.Body.Close()
	bodyBytes, err := readLimitedResponseBody(resp.Body, dashScopeGenerationBodyLimit)
	if err != nil {
		return "", nil, err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", nil, fmt.Errorf("dashscope status %d: %s", resp.StatusCode, strings.TrimSpace(string(bodyBytes)))
	}

	var decoded dashScopeGenerationResponse
	if err := json.Unmarshal(bodyBytes, &decoded); err != nil {
		return "", nil, err
	}
	if decoded.Code != "" {
		return "", nil, fmt.Errorf("dashscope error %s: %s", decoded.Code, decoded.Message)
	}
	if len(decoded.Output.Choices) == 0 {
		return "", nil, fmt.Errorf("dashscope response missing choices")
	}
	content := strings.TrimSpace(decoded.Output.Choices[0].Message.Content)
	if content == "" {
		return "", nil, fmt.Errorf("dashscope response empty content")
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
	return content, sources, nil
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
}

func firstNonBlank(values ...string) string {
	for _, value := range values {
		if trimmed := strings.TrimSpace(value); trimmed != "" {
			return trimmed
		}
	}
	return ""
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

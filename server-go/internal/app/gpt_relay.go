package app

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
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
	gptRelayHeaderProviderLabel              = "X-Nongji-Gpt-Provider-Label"
	gptRelayHeaderProviderSlot               = "X-Nongji-Gpt-Provider-Slot"
	gptRelayHeaderKeySlot                    = "X-Nongji-Gpt-Key-Slot"
	defaultGPTRelayModel                     = "gpt-5.5"
	defaultGPTRelayReasoningEffort           = "medium"
	defaultGPTRelaySearchContextSize         = "low"
	defaultGPTRelayFirstVisibleTimeout       = 60 * time.Second
	defaultGPTRelayIdleConnTimeout           = 60 * time.Second
	defaultGPTRelayKeyMaxAttempts            = 5
	defaultGPTRelayMaxConfiguredProviderSlot = 10
	defaultGPTRelayMaxConfiguredKeySlot      = 50
	defaultGPTRelayMaxSearchCallsInstruction = "如需联网，必须只搜索一次。\n拿到够用信息后立刻回答。\n不要解释搜索过程。\n不要展示搜索过程。"
)

type GPTRelayClient struct {
	httpClient              *http.Client
	logger                  *slog.Logger
	attemptRecorder         func(context.Context, gptRelayAttemptRecord)
	selectionMu             sync.Mutex
	keySelectionIdx         int
	providerSelectionIdx    int
	providerKeySelectionIdx map[string]int
}

type gptRelayAPIKeyEntry struct {
	Value         string
	Label         string
	Endpoint      string
	ProviderSlot  string
	ProviderLabel string
}

func (e gptRelayAPIKeyEntry) identity() string {
	if e.Endpoint == "" {
		return e.Value
	}
	return e.Endpoint + "\x00" + e.Value
}

type gptRelayProviderKeyGroup struct {
	Endpoint      string
	ProviderSlot  string
	ProviderLabel string
	Entries       []gptRelayAPIKeyEntry
}

type gptRelayAttemptRecord struct {
	ProviderSlot  string
	ProviderLabel string
	KeySlot       string
	Status        string
	ErrorKind     string
	HTTPStatus    int
	Attempt       int
	MaxAttempts   int
	OpenMs        int64
}

type gptRelayRequestCursor struct {
	mu                   sync.Mutex
	providerSelectionIdx int
}

func NewGPTRelayClientFromEnv() *GPTRelayClient {
	return &GPTRelayClient{
		httpClient:              newGPTRelayHTTPClient(),
		providerKeySelectionIdx: map[string]int{},
	}
}

func (c *GPTRelayClient) SetLogger(logger *slog.Logger) {
	if c == nil {
		return
	}
	c.logger = logger
}

func (c *GPTRelayClient) SetAttemptRecorder(recorder func(context.Context, gptRelayAttemptRecord)) {
	if c == nil {
		return
	}
	c.attemptRecorder = recorder
}

func newGPTRelayHTTPClient() *http.Client {
	return &http.Client{
		Transport: &http.Transport{
			Proxy: http.ProxyFromEnvironment,
			DialContext: (&net.Dialer{
				KeepAlive: 30 * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   0,
			ResponseHeaderTimeout: 0,
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
	return len(gptRelayRequestEntries()) > 0
}

func (c *GPTRelayClient) OpenStream(ctx context.Context, messages []BailianMessage) (*http.Response, error) {
	return c.openStreamWithCursor(ctx, messages, nil)
}

func (c *GPTRelayClient) openStreamWithCursor(ctx context.Context, messages []BailianMessage, cursor *gptRelayRequestCursor) (*http.Response, error) {
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
	return c.doPayloadRequest(ctx, payload, cursor)
}

func (c *GPTRelayClient) doPayloadRequest(ctx context.Context, payload []byte, cursor *gptRelayRequestCursor) (*http.Response, error) {
	keys := gptRelayRequestEntries()
	if len(keys) == 0 {
		return nil, fmt.Errorf("GPT_RELAY provider endpoint or API key is missing")
	}
	if c.hasMultipleProviderSlots(keys) {
		keys = c.selectProviderKeysForRequest(keys, cursor)
	}
	return c.doPayloadRequestWithKeys(ctx, payload, keys, cursor)
}

func (c *GPTRelayClient) doPayloadRequestWithKeys(ctx context.Context, payload []byte, keys []gptRelayAPIKeyEntry, cursor *gptRelayRequestCursor) (*http.Response, error) {
	if len(keys) == 0 {
		return nil, fmt.Errorf("GPT_RELAY provider endpoint or API key is missing")
	}
	maxAttempts := envIntWithDefault("GPT_RELAY_KEY_MAX_ATTEMPTS", defaultGPTRelayKeyMaxAttempts)
	if maxAttempts <= 0 {
		maxAttempts = 1
	}
	if maxAttempts > defaultGPTRelayKeyMaxAttempts {
		maxAttempts = defaultGPTRelayKeyMaxAttempts
	}
	if maxAttempts > len(keys) {
		maxAttempts = len(keys)
	}

	attempted := map[string]bool{}
	var lastErr error
	for attempt := 0; attempt < maxAttempts; attempt++ {
		key, ok := c.pickNextKeyEntry(keys, attempted, cursor)
		if !ok {
			break
		}
		keyID := key.identity()
		attempted[keyID] = true
		attemptStartedAt := time.Now()
		resp, err := c.sendPayload(ctx, key.Endpoint, payload, key.Value)
		elapsedMs := time.Since(attemptStartedAt).Milliseconds()
		if err != nil {
			lastErr = err
			willRetry := ctx.Err() == nil && attempt+1 < maxAttempts
			c.logKeyAttempt("gpt relay key attempt failed",
				"attempt", attempt+1,
				"max_attempts", maxAttempts,
				"key_slot", key.Label,
				"elapsed_ms", elapsedMs,
				"error_kind", classifyGPTRelayAttemptError(err),
				"will_retry", willRetry,
			)
			c.recordKeyAttempt(ctx, gptRelayAttemptRecord{
				ProviderSlot:  key.ProviderSlot,
				ProviderLabel: key.ProviderLabel,
				KeySlot:       key.Label,
				Status:        "failed",
				ErrorKind:     classifyGPTRelayAttemptError(err),
				Attempt:       attempt + 1,
				MaxAttempts:   maxAttempts,
				OpenMs:        elapsedMs,
			})
			if ctx.Err() != nil {
				return nil, err
			}
			continue
		}
		retryableStatus, statusPreview := shouldRetryGPTRelayResponse(resp)
		if retryableStatus {
			c.logKeyAttempt("gpt relay key attempt retryable status",
				"attempt", attempt+1,
				"max_attempts", maxAttempts,
				"key_slot", key.Label,
				"elapsed_ms", elapsedMs,
				"status", resp.StatusCode,
				"will_retry", attempt+1 < maxAttempts,
			)
			c.recordKeyAttempt(ctx, gptRelayAttemptRecord{
				ProviderSlot:  key.ProviderSlot,
				ProviderLabel: key.ProviderLabel,
				KeySlot:       key.Label,
				Status:        "retryable_status",
				HTTPStatus:    resp.StatusCode,
				Attempt:       attempt + 1,
				MaxAttempts:   maxAttempts,
				OpenMs:        elapsedMs,
			})
		}
		if retryableStatus && attempt+1 < maxAttempts && ctx.Err() == nil {
			if statusPreview == nil {
				_, _ = readLimitedResponseBody(resp.Body, bailianBodyPreviewLimit)
			}
			_ = resp.Body.Close()
			continue
		}
		if attempt > 0 {
			c.logKeyAttempt("gpt relay key attempt recovered",
				"attempt", attempt+1,
				"max_attempts", maxAttempts,
				"key_slot", key.Label,
				"elapsed_ms", elapsedMs,
				"previous_attempts", attempt,
			)
		}
		c.decorateResponseWithKeyMetadata(resp, key)
		return resp, nil
	}
	if lastErr != nil {
		return nil, lastErr
	}
	return nil, fmt.Errorf("GPT_RELAY provider endpoint or API key is missing")
}

func (c *GPTRelayClient) selectProviderKeysForRequest(keys []gptRelayAPIKeyEntry, cursor *gptRelayRequestCursor) []gptRelayAPIKeyEntry {
	providerOrder, providerKeys := groupGPTRelayKeysByProvider(keys)
	if len(providerOrder) == 0 {
		return keys
	}
	providerID := providerOrder[c.nextProviderSelectionOffset(len(providerOrder), cursor)]
	selected := providerKeys[providerID]
	if len(selected) == 0 {
		return keys
	}
	return selected
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

func (c *GPTRelayClient) logKeyAttempt(msg string, attrs ...any) {
	if c == nil || c.logger == nil {
		return
	}
	c.logger.Info(msg, attrs...)
}

func (c *GPTRelayClient) recordKeyAttempt(ctx context.Context, record gptRelayAttemptRecord) {
	if c == nil || c.attemptRecorder == nil {
		return
	}
	c.attemptRecorder(ctx, record)
}

func (c *GPTRelayClient) decorateResponseWithKeyMetadata(resp *http.Response, key gptRelayAPIKeyEntry) {
	if resp == nil {
		return
	}
	resp.Header.Set(gptRelayHeaderProviderLabel, key.ProviderLabel)
	resp.Header.Set(gptRelayHeaderProviderSlot, key.ProviderSlot)
	resp.Header.Set(gptRelayHeaderKeySlot, key.Label)
}

func gptRelayEnabled() bool {
	return parseBoolEnv(os.Getenv("GPT_RELAY_ENABLED"))
}

func gptRelayConfigured() bool {
	return gptRelayEnabled() && len(gptRelayRequestEntries()) > 0
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
	return gptRelayKeyEntriesForPrefix("GPT_RELAY")
}

func gptRelayKeyEntriesForPrefix(prefix string) []gptRelayAPIKeyEntry {
	result := []gptRelayAPIKeyEntry{}
	addKey := func(value string, label string) {
		key := normalizeGPTRelayAPIKey(value)
		if key == "" {
			return
		}
		for _, current := range result {
			if current.Value == key {
				return
			}
		}
		result = append(result, gptRelayAPIKeyEntry{Value: key, Label: label})
	}

	for i := 1; i <= defaultGPTRelayMaxConfiguredKeySlot; i++ {
		slot := fmt.Sprintf("%s_API_KEY_%d", prefix, i)
		addKey(os.Getenv(slot), slot)
	}
	singleSlot := prefix + "_API_KEY"
	addKey(os.Getenv(singleSlot), singleSlot)
	listSlot := prefix + "_API_KEYS"
	for idx, key := range splitConfiguredKeys(os.Getenv(listSlot)) {
		addKey(key, fmt.Sprintf("%s_%d", listSlot, idx+1))
	}
	return result
}

func gptRelayRequestEntries() []gptRelayAPIKeyEntry {
	if groups := gptRelayProviderKeyGroups(); len(groups) > 0 {
		return interleaveGPTRelayProviderKeyGroups(groups)
	}
	endpoint := gptRelayResponsesURL()
	if endpoint == "" {
		return nil
	}
	return withGPTRelayEndpoint(gptRelayKeyEntries(), endpoint, "gpt_relay", gptRelayProviderLabel())
}

func gptRelayProviderKeyGroups() []gptRelayProviderKeyGroup {
	groups := []gptRelayProviderKeyGroup{}
	for i := 1; i <= defaultGPTRelayMaxConfiguredProviderSlot; i++ {
		prefix := fmt.Sprintf("GPT_RELAY_PROVIDER_%d", i)
		endpoint := gptRelayResponsesURLForPrefix(prefix)
		if endpoint == "" {
			continue
		}
		providerSlot := fmt.Sprintf("provider_%d", i)
		entries := withGPTRelayEndpoint(gptRelayKeyEntriesForPrefix(prefix), endpoint, providerSlot, gptRelayProviderLabelForPrefix(prefix, providerSlot))
		if len(entries) == 0 {
			continue
		}
		groups = append(groups, gptRelayProviderKeyGroup{Endpoint: endpoint, ProviderSlot: providerSlot, ProviderLabel: gptRelayProviderLabelForPrefix(prefix, providerSlot), Entries: entries})
	}
	return groups
}

func withGPTRelayEndpoint(entries []gptRelayAPIKeyEntry, endpoint string, providerSlot string, providerLabel string) []gptRelayAPIKeyEntry {
	if endpoint == "" || len(entries) == 0 {
		return nil
	}
	result := make([]gptRelayAPIKeyEntry, 0, len(entries))
	for _, entry := range entries {
		entry.Endpoint = endpoint
		entry.ProviderSlot = providerSlot
		entry.ProviderLabel = providerLabel
		result = append(result, entry)
	}
	return result
}

func interleaveGPTRelayProviderKeyGroups(groups []gptRelayProviderKeyGroup) []gptRelayAPIKeyEntry {
	maxLen := 0
	for _, group := range groups {
		if len(group.Entries) > maxLen {
			maxLen = len(group.Entries)
		}
	}
	result := []gptRelayAPIKeyEntry{}
	seen := map[string]bool{}
	for idx := 0; idx < maxLen; idx++ {
		for _, group := range groups {
			if idx >= len(group.Entries) {
				continue
			}
			entry := group.Entries[idx]
			id := entry.identity()
			if seen[id] {
				continue
			}
			seen[id] = true
			result = append(result, entry)
		}
	}
	return result
}

func classifyGPTRelayAttemptError(err error) string {
	if err == nil {
		return ""
	}
	msg := strings.ToLower(err.Error())
	switch {
	case strings.Contains(msg, "timeout awaiting response headers"):
		return "response_header_timeout"
	case strings.Contains(msg, "tls handshake timeout"):
		return "tls_handshake_timeout"
	case strings.Contains(msg, "i/o timeout"):
		return "io_timeout"
	case strings.Contains(msg, "context deadline exceeded"):
		return "context_deadline_exceeded"
	case strings.Contains(msg, "context canceled"):
		return "context_canceled"
	case strings.Contains(msg, "connection refused"):
		return "connection_refused"
	case strings.Contains(msg, "connection reset"):
		return "connection_reset"
	case strings.Contains(msg, "eof"):
		return "eof"
	default:
		return "request_error"
	}
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
		if right != "" {
			return right
		}
	}
	if idx := strings.LastIndex(trimmed, ":"); idx >= 0 {
		right := strings.TrimSpace(trimmed[idx+1:])
		if right != "" {
			return right
		}
	}
	fields := strings.Fields(trimmed)
	if len(fields) > 1 {
		return fields[len(fields)-1]
	}
	return trimmed
}

func gptRelayKeyPoolSize() int {
	return len(gptRelayRequestEntries())
}

func (c *GPTRelayClient) pickNextKeyEntry(keys []gptRelayAPIKeyEntry, attempted map[string]bool, cursor *gptRelayRequestCursor) (gptRelayAPIKeyEntry, bool) {
	if len(keys) == 0 {
		return gptRelayAPIKeyEntry{}, false
	}
	if c.hasMultipleProviderSlots(keys) {
		return c.pickNextProviderKeyEntry(keys, attempted, cursor)
	}
	if providerID, ok := singleGPTRelayProviderID(keys); ok {
		key, ok := c.pickKeyFromProvider(providerID, keys, attempted)
		if ok {
			return key, true
		}
		return gptRelayAPIKeyEntry{}, false
	}
	start := c.nextSelectionOffset(len(keys))
	for offset := 0; offset < len(keys); offset++ {
		idx := (start + offset) % len(keys)
		key := keys[idx]
		keyID := key.identity()
		if attempted[keyID] {
			continue
		}
		return key, true
	}
	return gptRelayAPIKeyEntry{}, false
}

func (c *GPTRelayClient) hasMultipleProviderSlots(keys []gptRelayAPIKeyEntry) bool {
	first := ""
	for _, key := range keys {
		providerID := key.providerRoundRobinID()
		if providerID == "" {
			continue
		}
		if first == "" {
			first = providerID
			continue
		}
		if providerID != first {
			return true
		}
	}
	return false
}

func singleGPTRelayProviderID(keys []gptRelayAPIKeyEntry) (string, bool) {
	providerID := ""
	for _, key := range keys {
		id := key.providerRoundRobinID()
		if id == "" {
			return "", false
		}
		if providerID == "" {
			providerID = id
			continue
		}
		if id != providerID {
			return "", false
		}
	}
	return providerID, providerID != ""
}

func (c *GPTRelayClient) pickNextProviderKeyEntry(keys []gptRelayAPIKeyEntry, attempted map[string]bool, cursor *gptRelayRequestCursor) (gptRelayAPIKeyEntry, bool) {
	providerOrder, providerKeys := groupGPTRelayKeysByProvider(keys)
	if len(providerOrder) == 0 {
		return gptRelayAPIKeyEntry{}, false
	}
	providerID := providerOrder[c.nextProviderSelectionOffset(len(providerOrder), cursor)]
	return c.pickKeyFromProvider(providerID, providerKeys[providerID], attempted)
}

func groupGPTRelayKeysByProvider(keys []gptRelayAPIKeyEntry) ([]string, map[string][]gptRelayAPIKeyEntry) {
	order := []string{}
	grouped := map[string][]gptRelayAPIKeyEntry{}
	seen := map[string]bool{}
	for _, key := range keys {
		providerID := key.providerRoundRobinID()
		if providerID == "" {
			providerID = "gpt_relay"
		}
		if !seen[providerID] {
			seen[providerID] = true
			order = append(order, providerID)
		}
		grouped[providerID] = append(grouped[providerID], key)
	}
	return order, grouped
}

func (e gptRelayAPIKeyEntry) providerRoundRobinID() string {
	if e.ProviderSlot != "" {
		return e.ProviderSlot
	}
	return e.Endpoint
}

func (c *GPTRelayClient) pickKeyFromProvider(providerID string, keys []gptRelayAPIKeyEntry, attempted map[string]bool) (gptRelayAPIKeyEntry, bool) {
	if len(keys) == 0 {
		return gptRelayAPIKeyEntry{}, false
	}
	for offset := 0; offset < len(keys); offset++ {
		key := c.nextProviderKeyCandidate(providerID, keys)
		keyID := key.identity()
		if attempted[keyID] {
			continue
		}
		return key, true
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

func (c *GPTRelayClient) newRequestCursor() *gptRelayRequestCursor {
	if c == nil {
		return nil
	}
	c.selectionMu.Lock()
	defer c.selectionMu.Unlock()
	start := c.providerSelectionIdx
	c.providerSelectionIdx++
	return &gptRelayRequestCursor{providerSelectionIdx: start}
}

func (c *GPTRelayClient) nextProviderSelectionOffset(modulo int, cursor *gptRelayRequestCursor) int {
	if modulo <= 0 {
		return 0
	}
	if cursor != nil {
		return cursor.nextProviderSelectionOffset(modulo)
	}
	c.selectionMu.Lock()
	defer c.selectionMu.Unlock()
	start := c.providerSelectionIdx % modulo
	c.providerSelectionIdx = (c.providerSelectionIdx + 1) % modulo
	return start
}

func (c *gptRelayRequestCursor) nextProviderSelectionOffset(modulo int) int {
	if c == nil || modulo <= 0 {
		return 0
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.providerSelectionIdx % modulo
}

func (c *GPTRelayClient) nextProviderKeyCandidate(providerID string, keys []gptRelayAPIKeyEntry) gptRelayAPIKeyEntry {
	if providerID == "" || len(keys) == 0 {
		return gptRelayAPIKeyEntry{}
	}
	c.selectionMu.Lock()
	defer c.selectionMu.Unlock()
	if c.providerKeySelectionIdx == nil {
		c.providerKeySelectionIdx = map[string]int{}
	}
	idx := c.providerKeySelectionIdx[providerID] % len(keys)
	c.providerKeySelectionIdx[providerID] = (idx + 1) % len(keys)
	return keys[idx]
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

func shouldRetryGPTRelayResponse(resp *http.Response) (bool, []byte) {
	if resp == nil {
		return false, nil
	}
	if isGPTRelayRetryableStatus(resp.StatusCode) {
		return true, nil
	}
	if resp.StatusCode != http.StatusBadRequest || resp.Body == nil {
		return false, nil
	}
	preview, _ := readLimitedResponseBody(resp.Body, bailianBodyPreviewLimit)
	_ = resp.Body.Close()
	resp.Body = io.NopCloser(bytes.NewReader(preview))
	return shouldRetryGPTRelayBadRequest(preview), preview
}

func shouldRetryGPTRelayBadRequest(body []byte) bool {
	lower := strings.ToLower(string(body))
	for _, marker := range []string{
		"rate limit",
		"requests rate",
		"current requests",
		"current quota",
		"allocated quota",
		"quota exceeded",
		"insufficient quota",
		"insufficient balance",
		"balance insufficient",
		"billing",
		"credit",
		"too quickly",
		"throttl",
		"余额",
		"额度",
		"限流",
	} {
		if strings.Contains(lower, marker) {
			return true
		}
	}
	return false
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

func gptRelayProviderLabelForPrefix(prefix string, fallback string) string {
	label := strings.TrimSpace(os.Getenv(prefix + "_LABEL"))
	if label == "" {
		return fallback
	}
	safe := sanitizeDashScopeErrorMessage(label)
	if safe == "" || strings.Contains(safe, "[redacted]") || strings.Contains(safe, "[url]") || strings.Contains(safe, "[phone]") {
		return fallback
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
	duration := defaultGPTRelayFirstVisibleTimeout
	if maxDuration > 0 && duration > maxDuration {
		return maxDuration
	}
	return duration
}

func gptRelayResponsesURL() string {
	return gptRelayResponsesURLForPrefix("GPT_RELAY")
}

func gptRelayResponsesURLForPrefix(prefix string) string {
	if direct := strings.TrimSpace(os.Getenv(prefix + "_RESPONSES_URL")); direct != "" {
		return gptRelaySafeEndpointURL(direct)
	}
	baseURL := strings.TrimSpace(os.Getenv(prefix + "_BASE_URL"))
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
	systemParts := []string{}
	input := make([]map[string]any, 0, len(messages))
	for _, message := range messages {
		role := strings.ToLower(strings.TrimSpace(message.Role))
		if role == "system" {
			if text := gptRelayInstructionText(message.Content); text != "" {
				systemParts = append(systemParts, text)
			}
			continue
		}
		input = append(input, map[string]any{
			"role":    gptRelayResponsesRole(role),
			"content": gptRelayResponsesContent(message.Content),
		})
	}
	instructionParts := orderedGPTRelayInstructionParts(systemParts)
	return strings.Join(instructionParts, "\n\n"), input
}

func orderedGPTRelayInstructionParts(systemParts []string) []string {
	instructionParts := []string{}
	if len(systemParts) == 0 {
		return append(instructionParts, gptRelayNetworkingInstruction())
	}

	// Keep the stable, high-value rules at the front so upstream prompt caching can
	// reuse them. Dynamic context such as time, location and memory follows after.
	instructionParts = append(instructionParts, systemParts[0])
	outputConstraint := strings.TrimSpace(chatOutputConstraint)
	dynamicParts := []string{}
	hasOutputConstraint := false
	for _, part := range systemParts[1:] {
		trimmed := strings.TrimSpace(part)
		if trimmed == "" {
			continue
		}
		if trimmed == outputConstraint {
			if !hasOutputConstraint {
				instructionParts = append(instructionParts, trimmed)
				hasOutputConstraint = true
			}
			continue
		}
		dynamicParts = append(dynamicParts, trimmed)
	}
	instructionParts = append(instructionParts, gptRelayNetworkingInstruction())
	instructionParts = append(instructionParts, dynamicParts...)
	return instructionParts
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
	return "【GPT专用规则】\n默认不联网，先基于本轮输入、图片、上下文和已有农业知识直接回答；只有在强时效、强客观核对、用户明确要求联网 / 搜索 / 查询最新信息时，才联网。\n\n不要因为问题复杂、带图或风险高就自动联网；图片问诊、病虫害 / 药害判断、用药思路、种植管理建议，默认不联网。\n\n" + defaultGPTRelayMaxSearchCallsInstruction
}

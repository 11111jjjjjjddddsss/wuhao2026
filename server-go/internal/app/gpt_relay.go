package app

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
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
	gptRelayProvider                          = "gpt_relay"
	gptRelayHeaderProviderLabel               = "X-Nongji-Gpt-Provider-Label"
	gptRelayHeaderProviderSlot                = "X-Nongji-Gpt-Provider-Slot"
	gptRelayHeaderKeySlot                     = "X-Nongji-Gpt-Key-Slot"
	defaultGPTRelayModel                      = "gpt-5.5"
	defaultGPTRelayReasoningEffort            = "medium"
	defaultGPTRelaySearchContextSize          = "low"
	defaultGPTRelayFirstVisibleTimeout        = 7 * time.Second
	defaultGPTRelayDialTimeout                = 4 * time.Second
	defaultGPTRelayTLSHandshakeTimeout        = 4 * time.Second
	defaultGPTRelayResponseHeaderTimeout      = 4 * time.Second
	defaultGPTRelayIdleConnTimeout            = 60 * time.Second
	defaultGPTRelayKeyCooldown                = 30 * time.Second
	defaultGPTRelayKeyMaxAttempts             = 10
	defaultGPTRelayFirstVisibleRetryAttempts  = 1
	defaultGPTRelayFirstVisibleRetryTimeout   = 7 * time.Second
	maxGPTRelayFirstVisibleRetryAttempts      = 2
	defaultGPTRelayMaxConfiguredProviderSlot  = 10
	defaultGPTRelayMaxConfiguredKeySlot       = 50
	defaultGPTRelayCircuitWindow              = 5 * time.Minute
	defaultGPTRelayCircuitOpenDuration        = 2 * time.Minute
	defaultGPTRelayCircuitConsecutiveFailures = 8
	defaultGPTRelayCircuitMinRequests         = 30
	defaultGPTRelayCircuitFailurePercent      = 70
	defaultGPTRelayMaxSearchCallsInstruction  = "如需联网，必须只搜索一次。\n拿到够用信息后立刻快速回答。\n不要解释搜索过程。\n\n带图或高风险问题，必须深度思考。"
)

type GPTRelayClient struct {
	httpClient                 *http.Client
	logger                     *slog.Logger
	attemptRecorder            func(context.Context, gptRelayAttemptRecord)
	cooldownMu                 sync.Mutex
	keyCooldown                map[string]time.Time
	selectionMu                sync.Mutex
	keySelectionIdx            int
	providerSelectionIdx       int
	providerKeySelectionIdx    map[string]int
	circuitMu                  sync.Mutex
	circuitEvents              []gptRelayCircuitEvent
	circuitConsecutiveFailures int
	circuitOpenUntil           time.Time
	circuitHalfOpen            bool
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

type gptRelayCircuitEvent struct {
	At      time.Time
	Failure bool
}

type gptRelayCircuitState struct {
	Allowed             bool
	Open                bool
	HalfOpen            bool
	OpenUntil           time.Time
	ConsecutiveFailures int
	WindowRequests      int
	WindowFailures      int
	Trigger             string
}

func NewGPTRelayClientFromEnv() *GPTRelayClient {
	return &GPTRelayClient{
		httpClient:              newGPTRelayHTTPClient(),
		keyCooldown:             map[string]time.Time{},
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
	return len(gptRelayRequestEntries()) > 0
}

func (c *GPTRelayClient) CircuitAllowRequest(now time.Time) gptRelayCircuitState {
	if c == nil || !gptRelayCircuitBreakerEnabled() {
		return gptRelayCircuitState{Allowed: true}
	}
	if now.IsZero() {
		now = time.Now()
	}
	c.circuitMu.Lock()
	defer c.circuitMu.Unlock()
	c.pruneCircuitEventsLocked(now)
	if !c.circuitOpenUntil.IsZero() && now.Before(c.circuitOpenUntil) {
		state := c.circuitStateLocked(now)
		state.Allowed = false
		return state
	}
	if !c.circuitOpenUntil.IsZero() && !c.circuitHalfOpen {
		c.circuitHalfOpen = true
		state := c.circuitStateLocked(now)
		state.Allowed = true
		return state
	}
	if c.circuitHalfOpen {
		state := c.circuitStateLocked(now)
		state.Allowed = false
		return state
	}
	state := c.circuitStateLocked(now)
	state.Allowed = true
	return state
}

func (c *GPTRelayClient) ObserveCircuitSuccess(now time.Time) gptRelayCircuitState {
	if c == nil || !gptRelayCircuitBreakerEnabled() {
		return gptRelayCircuitState{Allowed: true}
	}
	if now.IsZero() {
		now = time.Now()
	}
	c.circuitMu.Lock()
	defer c.circuitMu.Unlock()
	c.pruneCircuitEventsLocked(now)
	c.circuitEvents = append(c.circuitEvents, gptRelayCircuitEvent{At: now})
	c.circuitConsecutiveFailures = 0
	c.circuitOpenUntil = time.Time{}
	c.circuitHalfOpen = false
	state := c.circuitStateLocked(now)
	state.Allowed = true
	return state
}

func (c *GPTRelayClient) ObserveCircuitFailure(now time.Time, reason string) gptRelayCircuitState {
	if c == nil || !gptRelayCircuitBreakerEnabled() {
		return gptRelayCircuitState{Allowed: true}
	}
	if now.IsZero() {
		now = time.Now()
	}
	c.circuitMu.Lock()
	defer c.circuitMu.Unlock()
	c.pruneCircuitEventsLocked(now)
	c.circuitEvents = append(c.circuitEvents, gptRelayCircuitEvent{At: now, Failure: true})
	c.circuitConsecutiveFailures++
	state := c.circuitStateLocked(now)
	trigger := ""
	if c.circuitHalfOpen {
		trigger = firstNonEmpty(reason, "half_open_failure")
	} else if c.circuitConsecutiveFailures >= gptRelayCircuitConsecutiveFailures() {
		trigger = "consecutive_failures"
	} else if state.WindowRequests >= gptRelayCircuitMinRequests() && state.WindowFailures*100 >= state.WindowRequests*gptRelayCircuitFailurePercent() {
		trigger = "failure_rate"
	}
	if trigger != "" {
		c.circuitOpenUntil = now.Add(gptRelayCircuitOpenDuration())
		c.circuitHalfOpen = false
		state = c.circuitStateLocked(now)
		state.Trigger = trigger
	}
	state.Allowed = !state.Open
	return state
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
	return c.doPayloadRequest(ctx, payload)
}

func (c *GPTRelayClient) doPayloadRequest(ctx context.Context, payload []byte) (*http.Response, error) {
	keys := gptRelayRequestEntries()
	if len(keys) == 0 {
		return nil, fmt.Errorf("GPT_RELAY provider endpoint or API key is missing")
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
		keyID := key.identity()
		attempted[keyID] = true
		attemptStartedAt := time.Now()
		resp, err := c.sendPayload(ctx, key.Endpoint, payload, key.Value)
		elapsedMs := time.Since(attemptStartedAt).Milliseconds()
		if err != nil {
			c.coolDownKey(keyID)
			lastErr = err
			if ctx.Err() != nil {
				return nil, err
			}
			c.logKeyAttempt("gpt relay key attempt failed",
				"attempt", attempt+1,
				"max_attempts", maxAttempts,
				"key_slot", key.Label,
				"elapsed_ms", elapsedMs,
				"error_kind", classifyGPTRelayAttemptError(err),
				"will_retry", attempt+1 < maxAttempts,
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
			continue
		}
		if isGPTRelayRetryableStatus(resp.StatusCode) {
			c.coolDownKey(keyID)
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
		if isGPTRelayRetryableStatus(resp.StatusCode) && attempt+1 < maxAttempts && ctx.Err() == nil {
			_, _ = readLimitedResponseBody(resp.Body, bailianBodyPreviewLimit)
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

func (c *GPTRelayClient) coolDownResponseKey(resp *http.Response) {
	if c == nil || resp == nil {
		return
	}
	providerSlot := strings.TrimSpace(resp.Header.Get(gptRelayHeaderProviderSlot))
	keySlot := strings.TrimSpace(resp.Header.Get(gptRelayHeaderKeySlot))
	if keySlot == "" {
		return
	}
	for _, key := range gptRelayRequestEntries() {
		if key.Label != keySlot {
			continue
		}
		if providerSlot != "" && key.ProviderSlot != providerSlot {
			continue
		}
		c.coolDownKey(key.identity())
		return
	}
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

func gptRelayCircuitBreakerEnabled() bool {
	raw := strings.TrimSpace(os.Getenv("GPT_RELAY_CIRCUIT_BREAKER_ENABLED"))
	if raw == "" {
		return true
	}
	return parseBoolEnv(raw)
}

func gptRelayCircuitWindow() time.Duration {
	duration := envDurationWithDefault("GPT_RELAY_CIRCUIT_WINDOW_SECONDS", defaultGPTRelayCircuitWindow)
	if duration <= 0 {
		return defaultGPTRelayCircuitWindow
	}
	return duration
}

func gptRelayCircuitOpenDuration() time.Duration {
	duration := envDurationWithDefault("GPT_RELAY_CIRCUIT_OPEN_SECONDS", defaultGPTRelayCircuitOpenDuration)
	if duration <= 0 {
		return defaultGPTRelayCircuitOpenDuration
	}
	return duration
}

func gptRelayCircuitConsecutiveFailures() int {
	value := envIntWithDefault("GPT_RELAY_CIRCUIT_CONSECUTIVE_FAILURES", defaultGPTRelayCircuitConsecutiveFailures)
	if value <= 0 {
		return defaultGPTRelayCircuitConsecutiveFailures
	}
	return value
}

func gptRelayCircuitMinRequests() int {
	value := envIntWithDefault("GPT_RELAY_CIRCUIT_MIN_REQUESTS", defaultGPTRelayCircuitMinRequests)
	if value <= 0 {
		return defaultGPTRelayCircuitMinRequests
	}
	return value
}

func gptRelayCircuitFailurePercent() int {
	value := envIntWithDefault("GPT_RELAY_CIRCUIT_FAILURE_PERCENT", defaultGPTRelayCircuitFailurePercent)
	if value <= 0 || value > 100 {
		return defaultGPTRelayCircuitFailurePercent
	}
	return value
}

func (c *GPTRelayClient) pruneCircuitEventsLocked(now time.Time) {
	window := gptRelayCircuitWindow()
	cutoff := now.Add(-window)
	keepFrom := 0
	for keepFrom < len(c.circuitEvents) && c.circuitEvents[keepFrom].At.Before(cutoff) {
		keepFrom++
	}
	if keepFrom > 0 {
		copy(c.circuitEvents, c.circuitEvents[keepFrom:])
		c.circuitEvents = c.circuitEvents[:len(c.circuitEvents)-keepFrom]
	}
}

func (c *GPTRelayClient) circuitStateLocked(now time.Time) gptRelayCircuitState {
	failures := 0
	for _, event := range c.circuitEvents {
		if event.Failure {
			failures++
		}
	}
	return gptRelayCircuitState{
		Open:                !c.circuitOpenUntil.IsZero() && now.Before(c.circuitOpenUntil),
		HalfOpen:            c.circuitHalfOpen,
		OpenUntil:           c.circuitOpenUntil,
		ConsecutiveFailures: c.circuitConsecutiveFailures,
		WindowRequests:      len(c.circuitEvents),
		WindowFailures:      failures,
	}
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

func (c *GPTRelayClient) pickNextKeyEntry(keys []gptRelayAPIKeyEntry, attempted map[string]bool) (gptRelayAPIKeyEntry, bool) {
	if len(keys) == 0 {
		return gptRelayAPIKeyEntry{}, false
	}
	if c.hasMultipleProviderSlots(keys) {
		return c.pickNextProviderKeyEntry(keys, attempted)
	}
	now := time.Now()
	start := c.nextSelectionOffset(len(keys))
	var fallback *gptRelayAPIKeyEntry
	for offset := 0; offset < len(keys); offset++ {
		idx := (start + offset) % len(keys)
		key := keys[idx]
		keyID := key.identity()
		if attempted[keyID] {
			continue
		}
		if fallback == nil {
			candidate := key
			fallback = &candidate
		}
		if !c.isKeyCoolingDown(keyID, now) {
			return key, true
		}
	}
	if fallback != nil {
		return *fallback, true
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

func (c *GPTRelayClient) pickNextProviderKeyEntry(keys []gptRelayAPIKeyEntry, attempted map[string]bool) (gptRelayAPIKeyEntry, bool) {
	providerOrder, providerKeys := groupGPTRelayKeysByProvider(keys)
	if len(providerOrder) == 0 {
		return gptRelayAPIKeyEntry{}, false
	}
	now := time.Now()
	startProvider := c.nextProviderSelectionOffset(len(providerOrder))
	var fallback *gptRelayAPIKeyEntry
	for offset := 0; offset < len(providerOrder); offset++ {
		providerID := providerOrder[(startProvider+offset)%len(providerOrder)]
		key, cooledFallback, ok := c.pickKeyFromProvider(providerID, providerKeys[providerID], attempted, now)
		if ok {
			return key, true
		}
		if fallback == nil && cooledFallback != nil {
			candidate := *cooledFallback
			fallback = &candidate
		}
	}
	if fallback != nil {
		c.advanceProviderKeySelection(fallback.providerRoundRobinID(), providerKeys[fallback.providerRoundRobinID()], fallback.identity())
		return *fallback, true
	}
	return gptRelayAPIKeyEntry{}, false
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

func (c *GPTRelayClient) pickKeyFromProvider(providerID string, keys []gptRelayAPIKeyEntry, attempted map[string]bool, now time.Time) (gptRelayAPIKeyEntry, *gptRelayAPIKeyEntry, bool) {
	if len(keys) == 0 {
		return gptRelayAPIKeyEntry{}, nil, false
	}
	start := c.providerKeySelectionOffset(providerID, len(keys))
	var cooledFallback *gptRelayAPIKeyEntry
	for offset := 0; offset < len(keys); offset++ {
		key := keys[(start+offset)%len(keys)]
		keyID := key.identity()
		if attempted[keyID] {
			continue
		}
		if cooledFallback == nil {
			candidate := key
			cooledFallback = &candidate
		}
		if c.isKeyCoolingDown(keyID, now) {
			continue
		}
		c.advanceProviderKeySelection(providerID, keys, keyID)
		return key, nil, true
	}
	return gptRelayAPIKeyEntry{}, cooledFallback, false
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

func (c *GPTRelayClient) nextProviderSelectionOffset(modulo int) int {
	if modulo <= 0 {
		return 0
	}
	c.selectionMu.Lock()
	defer c.selectionMu.Unlock()
	start := c.providerSelectionIdx % modulo
	c.providerSelectionIdx = (c.providerSelectionIdx + 1) % modulo
	return start
}

func (c *GPTRelayClient) providerKeySelectionOffset(providerID string, modulo int) int {
	if providerID == "" || modulo <= 0 {
		return 0
	}
	c.selectionMu.Lock()
	defer c.selectionMu.Unlock()
	if c.providerKeySelectionIdx == nil {
		c.providerKeySelectionIdx = map[string]int{}
	}
	return c.providerKeySelectionIdx[providerID] % modulo
}

func (c *GPTRelayClient) advanceProviderKeySelection(providerID string, keys []gptRelayAPIKeyEntry, selectedKeyID string) {
	if providerID == "" || len(keys) == 0 {
		return
	}
	next := 0
	for idx, key := range keys {
		if key.identity() == selectedKeyID {
			next = (idx + 1) % len(keys)
			break
		}
	}
	c.selectionMu.Lock()
	defer c.selectionMu.Unlock()
	if c.providerKeySelectionIdx == nil {
		c.providerKeySelectionIdx = map[string]int{}
	}
	c.providerKeySelectionIdx[providerID] = next
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
	duration := envDurationWithDefault("GPT_RELAY_FIRST_VISIBLE_TIMEOUT_SECONDS", defaultGPTRelayFirstVisibleTimeout)
	if duration <= 0 {
		return defaultGPTRelayFirstVisibleTimeout
	}
	if maxDuration > 0 && duration > maxDuration {
		return maxDuration
	}
	return duration
}

func resolveGPTRelayFirstVisibleRetryAttempts() int {
	value := envIntWithDefault("GPT_RELAY_FIRST_VISIBLE_RETRY_ATTEMPTS", defaultGPTRelayFirstVisibleRetryAttempts)
	if value < 0 {
		return 0
	}
	if value > maxGPTRelayFirstVisibleRetryAttempts {
		return maxGPTRelayFirstVisibleRetryAttempts
	}
	return value
}

func resolveGPTRelayFirstVisibleRetryTimeout(maxDuration time.Duration) time.Duration {
	duration := envDurationWithDefault("GPT_RELAY_FIRST_VISIBLE_RETRY_TIMEOUT_SECONDS", defaultGPTRelayFirstVisibleRetryTimeout)
	if duration <= 0 {
		return defaultGPTRelayFirstVisibleRetryTimeout
	}
	firstVisibleTimeout := resolveGPTRelayFirstVisibleTimeout(maxDuration)
	if firstVisibleTimeout > 0 && duration > firstVisibleTimeout {
		duration = firstVisibleTimeout
	}
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
	return "【GPT专用规则】\n模型可自行判断是否联网。\n\n用户明确要求查一下、搜一下、联网查、看最新信息时，要联网。\n\n涉及今天 / 最新 / 当前 / 实时 / 价格 / 行情 / 政策 / 天气 / 农资登记 / 用药标签 / 禁限用变化 / 购买渠道等实时信息时，再联网。\n\n疑难、复杂、高风险问题，如果需要公开权威信息校准，也可以联网。\n\n" + defaultGPTRelayMaxSearchCallsInstruction
}

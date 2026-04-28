package app

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

const (
	chatRateLimitWindow   = 60 * time.Second
	chatRateLimitMaxHits  = 20
	upstreamMaxAttempts   = 2
	upstreamRetryBaseWait = 350 * time.Millisecond
)

type chatRateLimiter struct {
	mu      sync.Mutex
	buckets map[string][]time.Time
}

type upstreamStreamOpenError struct {
	Message      string
	Kind         string
	StatusCode   int
	ResponseBody string
	ContentType  string
}

func (e *upstreamStreamOpenError) Error() string {
	return e.Message
}

func newChatRateLimiter() *chatRateLimiter {
	return &chatRateLimiter{
		buckets: map[string][]time.Time{},
	}
}

func (l *chatRateLimiter) Consume(userID string, now time.Time) (bool, int) {
	l.mu.Lock()
	defer l.mu.Unlock()

	existing := l.buckets[userID]
	valid := existing[:0]
	for _, ts := range existing {
		if now.Sub(ts) < chatRateLimitWindow {
			valid = append(valid, ts)
		}
	}

	if len(valid) >= chatRateLimitMaxHits {
		retryAfter := chatRateLimitWindow - now.Sub(valid[0])
		l.buckets[userID] = append([]time.Time(nil), valid...)
		return false, maxInt(1, int(retryAfter.Seconds())+1)
	}

	valid = append(valid, now)
	l.buckets[userID] = append([]time.Time(nil), valid...)
	return true, 0
}

func (s *Server) handleChatStream(w http.ResponseWriter, r *http.Request) {
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}

	var body ChatStreamRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeError(w, http.StatusBadRequest, "invalid_json")
		return
	}

	clientMsgID := strings.TrimSpace(body.ClientMsgID)
	text := strings.TrimSpace(body.Text)
	images := normalizeImages(body.Images)
	if validationError := validateChatStreamInput(clientMsgID, text, images); validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}

	ctx := r.Context()
	if err := s.store.EnsureUser(ctx, auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	tier, _, err := s.store.GetTierForUser(ctx, auth.UserID, TierFree)
	if err != nil {
		s.logger.Error("get tier failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	aWindowRounds := getAWindowByTier(tier)
	bEveryRounds, cEveryRounds := GetSummaryIntervals(tier)

	snapshot, err := s.store.GetSessionSnapshot(ctx, auth.UserID)
	if err != nil {
		s.logger.Error("get session snapshot failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	clientIP := GetClientIP(r)
	region := ParseRegionFromHeaders(r.Header)
	if region == nil {
		resolved := ResolveRegionByIP(clientIP)
		region = &resolved
	}
	injectedTime := FormatShanghaiNowToSecond(s.shanghai, time.Now())
	contextHeader := fmt.Sprintf("当前时间：%s（Asia/Shanghai）；用户地点：%s；地点可信度：%s", injectedTime, region.Region, region.Reliability)
	promptMessages, usedARoundsCount, hasBSummary, hasCSummary := s.buildPromptMessages(snapshot, aWindowRounds, text, images, contextHeader)
	dayCN := GetTodayKeyCN(s.shanghai, time.Now())

	if err := s.store.TouchSessionContext(ctx, auth.UserID, region.Region, region.Source, region.Reliability, time.Now().UnixMilli()); err != nil {
		s.logger.Warn("touch session context failed", "userId", auth.UserID, "error", err)
	}

	s.logger.Info("chat prompt assembly",
		"userId", auth.UserID,
		"auth_mode", auth.AuthMode,
		"masked_ip", auth.MaskedIP,
		"tier", tier,
		"used_a_rounds_count", usedARoundsCount,
		"has_b_summary", hasBSummary,
		"has_c_summary", hasCSummary,
		"injected_time", injectedTime,
		"region", region.Region,
		"region_source", region.Source,
		"region_reliability", region.Reliability,
	)

	if !s.bailian.HasKeyConfigured() {
		s.writeJSON(w, http.StatusServiceUnavailable, map[string]any{
			"error":   "MODEL_BACKEND_NOT_CONFIGURED",
			"message": "后端未配置大模型服务，当前无法使用真实流式对话",
		})
		return
	}

	processed, err := s.store.WasProcessed(ctx, auth.UserID, clientMsgID)
	if err != nil {
		s.logger.Error("check quota replay failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if processed {
		s.writeSSEHeaders(w)
		s.writeSSEData(w, map[string]any{"ok": true, "replay": true, "client_msg_id": clientMsgID})
		s.writeSSEString(w, "data: [DONE]\n\n")
		return
	}

	allowed, retryAfterSec := s.rateLimiter.Consume(auth.UserID, time.Now())
	if !allowed {
		w.Header().Set("Retry-After", fmt.Sprintf("%d", retryAfterSec))
		s.writeJSON(w, http.StatusTooManyRequests, map[string]any{
			"error":           "RATE_LIMITED",
			"message":         "请求过于频繁，请稍后再试",
			"retry_after_sec": retryAfterSec,
		})
		return
	}

	before, err := s.store.GetDailyStatus(ctx, auth.UserID, tier, dayCN)
	if err != nil {
		s.logger.Error("get daily status failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	topupBefore, _, err := s.store.GetTopupStatus(ctx, auth.UserID)
	if err != nil {
		s.logger.Error("get topup status failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	upgradeBefore, err := s.store.GetUpgradeRemaining(ctx, auth.UserID)
	if err != nil {
		s.logger.Error("get upgrade remaining failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if before.Remaining <= 0 && topupBefore <= 0 && upgradeBefore <= 0 {
		s.writeError(w, http.StatusPaymentRequired, "今日次数用完")
		return
	}

	upstreamCtx, cancelUpstream := context.WithCancel(context.Background())
	defer cancelUpstream()
	upstream, err := s.openValidatedBailianStreamWithRetry(upstreamCtx, promptMessages)
	if err != nil {
		s.respondUpstreamOpenError(w, err)
		return
	}
	defer upstream.Body.Close()

	s.writeSSEHeaders(w)
	flusher, _ := w.(http.Flusher)
	if flusher != nil {
		flusher.Flush()
	}

	upstreamRequestID := firstNonEmpty(upstream.Header.Get("x-request-id"), upstream.Header.Get("request-id"))
	s.logger.Info("bailian search config",
		"request_id", upstreamRequestID,
		"enable_search", true,
		"strategy", "turbo",
		"forced_search", false,
		"single_call_search", true,
	)

	var clientDisconnected atomic.Bool
	var doneReceived atomic.Bool
	var hasCitations atomic.Bool
	var hasSources atomic.Bool
	var writeMu sync.Mutex
	assistantText := strings.Builder{}
	stopHeartbeat := make(chan struct{})

	go func() {
		select {
		case <-r.Context().Done():
			clientDisconnected.Store(true)
		case <-stopHeartbeat:
		}
	}()

	go func() {
		ticker := time.NewTicker(sseHeartbeatInterval)
		defer ticker.Stop()
		for {
			select {
			case <-stopHeartbeat:
				return
			case <-ticker.C:
				if clientDisconnected.Load() {
					continue
				}
				writeMu.Lock()
				_, err := io.WriteString(w, ": ping\n\n")
				if flusher != nil {
					flusher.Flush()
				}
				writeMu.Unlock()
				if err != nil {
					clientDisconnected.Store(true)
				}
			}
		}
	}()

	reader := bufio.NewReader(upstream.Body)
	for {
		line, readErr := reader.ReadString('\n')
		if line != "" {
			trimmedLine := strings.TrimRight(line, "\r\n")
			if trimmedLine != "" && !strings.HasPrefix(trimmedLine, ":") && !strings.HasPrefix(trimmedLine, "event:") && strings.HasPrefix(trimmedLine, "data:") {
				data := strings.TrimLeft(strings.TrimPrefix(trimmedLine, "data:"), " ")
				if data != "[DONE]" {
					updateAssistantAccumulator(data, &assistantText, &hasCitations, &hasSources)
				}
				if !clientDisconnected.Load() {
					writeMu.Lock()
					_, err = io.WriteString(w, "data: "+data+"\n\n")
					if flusher != nil {
						flusher.Flush()
					}
					writeMu.Unlock()
					if err != nil {
						clientDisconnected.Store(true)
					}
				}
				if data == "[DONE]" {
					doneReceived.Store(true)
					break
				}
			}
		}

		if readErr != nil {
			if readErr != io.EOF {
				s.logger.Error("sse relay failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", readErr)
			}
			break
		}
		if doneReceived.Load() {
			break
		}
	}

	close(stopHeartbeat)

	if doneReceived.Load() {
		consume, consumeErr := s.store.ConsumeOnDone(context.Background(), auth.UserID, tier, clientMsgID, dayCN)
		if consumeErr != nil {
			s.logger.Error("quota consume on DONE failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", consumeErr)
		} else {
			s.logger.Info("quota consume on DONE", "userId", auth.UserID, "clientMsgId", clientMsgID, "deducted", consume.Deducted, "source", consume.Source)
		}

		replyText := strings.TrimSpace(assistantText.String())
		if replyText != "" {
			replay, updatedSnapshot, appendErr := s.store.AppendSessionRoundComplete(
				context.Background(),
				auth.UserID,
				clientMsgID,
				SessionRound{
					ClientMsgID: clientMsgID,
					User:        text,
					UserImages:  images,
					Assistant:   replyText,
				},
				aWindowRounds,
				bEveryRounds,
				cEveryRounds,
				"stream_done",
			)
			if appendErr != nil {
				s.logger.Error("append session round after stream failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", appendErr)
			} else if !replay && updatedSnapshot != nil {
				snapshotCopy := cloneSessionSnapshot(*updatedSnapshot)
				go s.summary.ProcessSessionSummaries(auth.UserID, &snapshotCopy)
			}
		}
	}

	s.logger.Info("bailian stream finished",
		"request_id", upstreamRequestID,
		"enable_search", true,
		"strategy", "turbo",
		"forced_search", false,
		"has_citations", hasCitations.Load(),
		"has_sources", hasSources.Load(),
		"done_received", doneReceived.Load(),
		"client_disconnected", clientDisconnected.Load(),
	)
}

func (s *Server) openValidatedBailianStreamWithRetry(ctx context.Context, messages []BailianMessage) (*http.Response, error) {
	var lastErr error
	for attempt := 1; attempt <= upstreamMaxAttempts; attempt++ {
		response, err := s.bailian.OpenStream(ctx, messages)
		if err != nil {
			lastErr = &upstreamStreamOpenError{
				Message:      "upstream request failed",
				Kind:         "request",
				StatusCode:   http.StatusBadGateway,
				ResponseBody: err.Error(),
			}
			if attempt < upstreamMaxAttempts && ctx.Err() == nil {
				s.logger.Warn("upstream open retry scheduled after request failure", "attempt", attempt, "maxAttempts", upstreamMaxAttempts, "error", err)
				if waitErr := waitForRetryDelay(ctx, upstreamRetryBaseWait*time.Duration(attempt)); waitErr != nil {
					return nil, waitErr
				}
				continue
			}
			return nil, lastErr
		}

		contentType := response.Header.Get("Content-Type")
		if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
			body, _ := io.ReadAll(response.Body)
			_ = response.Body.Close()
			lastErr = &upstreamStreamOpenError{
				Message:      fmt.Sprintf("upstream http %d", response.StatusCode),
				Kind:         "http",
				StatusCode:   response.StatusCode,
				ResponseBody: strings.TrimSpace(string(body)),
			}
			if attempt < upstreamMaxAttempts && isRetryableUpstreamStatus(response.StatusCode) && ctx.Err() == nil {
				s.logger.Warn("upstream open retry scheduled after non-200 response", "attempt", attempt, "maxAttempts", upstreamMaxAttempts, "status", response.StatusCode)
				if waitErr := waitForRetryDelay(ctx, upstreamRetryBaseWait*time.Duration(attempt)); waitErr != nil {
					return nil, waitErr
				}
				continue
			}
			return nil, lastErr
		}

		if !strings.Contains(strings.ToLower(contentType), "text/event-stream") {
			body, _ := io.ReadAll(response.Body)
			_ = response.Body.Close()
			lastErr = &upstreamStreamOpenError{
				Message:      "upstream not SSE",
				Kind:         "protocol",
				StatusCode:   http.StatusBadGateway,
				ResponseBody: strings.TrimSpace(string(body)),
				ContentType:  contentType,
			}
			if attempt < upstreamMaxAttempts && ctx.Err() == nil {
				s.logger.Warn("upstream open retry scheduled after non-SSE response", "attempt", attempt, "maxAttempts", upstreamMaxAttempts, "contentType", contentType)
				if waitErr := waitForRetryDelay(ctx, upstreamRetryBaseWait*time.Duration(attempt)); waitErr != nil {
					return nil, waitErr
				}
				continue
			}
			return nil, lastErr
		}

		if attempt > 1 {
			s.logger.Info("upstream open recovered after retry", "attempt", attempt, "maxAttempts", upstreamMaxAttempts)
		}
		return response, nil
	}
	return nil, lastErr
}

func (s *Server) respondUpstreamOpenError(w http.ResponseWriter, err error) {
	openErr, ok := err.(*upstreamStreamOpenError)
	if !ok {
		s.logger.Error("upstream request failed", "error", err)
		s.writeError(w, http.StatusBadGateway, "upstream request failed")
		return
	}

	switch openErr.Kind {
	case "http":
		s.logger.Error("upstream non-200 after retry", "status", openErr.StatusCode, "errorBody", openErr.ResponseBody)
		status := openErr.StatusCode
		if status == 0 {
			status = http.StatusBadGateway
		}
		s.writeJSON(w, status, map[string]any{"error": firstNonEmpty(openErr.ResponseBody, "upstream error")})
	case "protocol":
		s.logger.Error("upstream is not SSE after retry", "contentType", openErr.ContentType, "fallbackBody", openErr.ResponseBody)
		s.writeError(w, http.StatusBadGateway, "upstream not SSE")
	default:
		s.logger.Error("upstream request failed after retry", "error", openErr.ResponseBody)
		s.writeError(w, http.StatusBadGateway, "upstream request failed")
	}
}

func (s *Server) writeSSEHeaders(w http.ResponseWriter) {
	w.Header().Set("Content-Type", "text/event-stream; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.WriteHeader(http.StatusOK)
}

func (s *Server) writeSSEData(w http.ResponseWriter, payload any) {
	raw, err := json.Marshal(payload)
	if err != nil {
		s.logger.Error("marshal sse payload failed", "error", err)
		return
	}
	s.writeSSEString(w, "data: "+string(raw)+"\n\n")
}

func (s *Server) writeSSEString(w http.ResponseWriter, payload string) {
	_, _ = io.WriteString(w, payload)
	if flusher, ok := w.(http.Flusher); ok {
		flusher.Flush()
	}
}

func waitForRetryDelay(ctx context.Context, delay time.Duration) error {
	if delay <= 0 {
		return nil
	}
	timer := time.NewTimer(delay)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}

func isRetryableUpstreamStatus(status int) bool {
	return status == http.StatusRequestTimeout || status == http.StatusTooManyRequests || (status >= 500 && status <= 599)
}

func normalizeImages(images []string) []string {
	result := make([]string, 0, len(images))
	for _, image := range images {
		trimmed := strings.TrimSpace(image)
		if trimmed != "" {
			result = append(result, trimmed)
		}
	}
	return result
}

func (s *Server) buildPromptMessages(snapshot *SessionSnapshot, aWindowRounds int, currentText string, currentImages []string, contextHeader string) ([]BailianMessage, int, bool, bool) {
	rounds := []SessionRound{}
	hasBSummary := false
	hasCSummary := false
	if snapshot != nil {
		hasBSummary = strings.TrimSpace(snapshot.BSummary) != ""
		hasCSummary = strings.TrimSpace(snapshot.CSummary) != ""
		if len(snapshot.ARoundsFull) > aWindowRounds {
			rounds = append(rounds, snapshot.ARoundsFull[len(snapshot.ARoundsFull)-aWindowRounds:]...)
		} else {
			rounds = append(rounds, snapshot.ARoundsFull...)
		}
	}

	messages := []BailianMessage{
		{Role: "system", Content: s.systemAnchor},
		{Role: "system", Content: contextHeader},
	}
	if hasBSummary {
		messages = append(messages, BailianMessage{Role: "system", Content: "B层累计摘要（仅供参考）\n" + strings.TrimSpace(snapshot.BSummary)})
	}
	if hasCSummary {
		messages = append(messages, BailianMessage{Role: "system", Content: "C层长期记忆（仅供参考）\n" + strings.TrimSpace(snapshot.CSummary)})
	}

	previousRoundIndex := len(rounds) - 1
	for index, round := range rounds {
		messages = append(messages, BailianMessage{Role: "user", Content: roundToUserContent(round, index == previousRoundIndex)})
		messages = append(messages, BailianMessage{Role: "assistant", Content: round.Assistant})
	}
	messages = append(messages, BailianMessage{Role: "user", Content: buildVisionUserContent(currentText, currentImages)})
	return messages, len(rounds), hasBSummary, hasCSummary
}

func buildVisionUserContent(text string, images []string) any {
	text = strings.TrimSpace(text)
	images = normalizeImages(images)
	if len(images) == 0 {
		return text
	}
	content := make([]map[string]any, 0, len(images)+1)
	if text != "" {
		content = append(content, map[string]any{
			"type": "text",
			"text": text,
		})
	}
	for _, image := range images {
		content = append(content, map[string]any{
			"type": "image_url",
			"image_url": map[string]any{
				"url": image,
			},
		})
	}
	return content
}

func roundToUserContent(round SessionRound, includeImages bool) any {
	if !includeImages {
		return round.User
	}
	return buildVisionUserContent(round.User, round.UserImages)
}

func validateChatStreamInput(clientMsgID string, text string, images []string) string {
	if clientMsgID == "" {
		return "client_msg_id required"
	}
	if len(images) > 4 {
		return "single request supports up to 4 images"
	}
	if strings.TrimSpace(text) == "" && len(images) == 0 {
		return "text or images required"
	}
	return ""
}

func updateAssistantAccumulator(data string, assistantText *strings.Builder, hasCitations *atomic.Bool, hasSources *atomic.Bool) {
	payload := map[string]any{}
	if err := json.Unmarshal([]byte(data), &payload); err != nil {
		return
	}
	if _, ok := payload["citations"]; ok {
		hasCitations.Store(true)
	}
	if _, ok := payload["sources"]; ok {
		hasSources.Store(true)
	}

	choices, _ := payload["choices"].([]any)
	if len(choices) == 0 {
		return
	}
	first, _ := choices[0].(map[string]any)
	delta, _ := first["delta"].(map[string]any)
	message, _ := first["message"].(map[string]any)

	if delta != nil {
		if _, ok := delta["citations"]; ok {
			hasCitations.Store(true)
		}
		if _, ok := delta["sources"]; ok {
			hasSources.Store(true)
		}
	}
	if message != nil {
		if _, ok := message["citations"]; ok {
			hasCitations.Store(true)
		}
		if _, ok := message["sources"]; ok {
			hasSources.Store(true)
		}
	}

	deltaPiece := asString(anyMapValue(delta, "content"))
	messagePiece := asString(anyMapValue(message, "content"))
	current := assistantText.String()
	switch {
	case deltaPiece != "":
		assistantText.WriteString(deltaPiece)
	case messagePiece != "":
		if strings.HasPrefix(messagePiece, current) {
			assistantText.Reset()
			assistantText.WriteString(messagePiece)
		} else {
			assistantText.WriteString(messagePiece)
		}
	}
}

func anyMapValue(values map[string]any, key string) any {
	if values == nil {
		return nil
	}
	return values[key]
}

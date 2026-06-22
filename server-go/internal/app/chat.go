package app

import (
	"bufio"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/redis/go-redis/v9"
)

const (
	upstreamMaxAttempts              = 1
	upstreamRetryBaseWait            = 350 * time.Millisecond
	chatStreamMaxDuration            = 30 * time.Minute
	chatStreamFirstVisibleTimeout    = 3 * time.Minute
	chatStreamIdleTimeout            = 4 * time.Minute
	maxClientMsgIDLength             = 128
	maxChatTextRunes                 = 6000
	maxBailianSSELineBytes           = 256 * 1024
	appendSessionRoundMaxAttempts    = 3
	appendSessionRoundRetryBaseWait  = 150 * time.Millisecond
	chatStreamFinalizeTimeout        = 15 * time.Second
	chatStreamInflightReleaseTimeout = 5 * time.Second
	defaultChatThinkingMode          = "always"
	defaultChatThinkingBudget        = 1024
	todayAgriContextRoundLimit       = 2
	chatHistoricalImageContextTTL    = 72 * time.Hour
)

const chatDiagnosticConstraint = `【诊断约束】
所有回答禁止表格。排版尽量简洁，多用短段落和适当空行；不要使用多级列表和项目符号列表，避免长段堆叠。回答不要刻意套固定模板；不限固定格式，根据本轮问题自然组织，避免呆板机械。

默认必须使用中文表达，禁止中英混写；普通英文词和英文短语必须改成中文说法，不得直接夹在中文句子里。只有农药/肥料成分、品种名、病原拉丁学名、登记标签、品牌商品名或用户原文确实需要保留时，才使用英文，并尽量先给中文解释。

涉及病虫害、药害肥害、生理异常或农技判断时，以本轮图片和客观信息为准。时间、地点、天气、历史对话和记忆摘要只作风险背景，不能盖过本轮证据。

带图时先详细描述图片里的关键信息。证据不足要明确说“不确定”。不要把单张图片或单轮描述写成确诊。
`

type chatRateLimiter struct {
	mu            sync.Mutex
	buckets       map[string][]time.Time
	window        time.Duration
	maxHits       int
	pruneInterval time.Duration
	lastPrune     time.Time
}

type upstreamStreamOpenError struct {
	Message     string
	Kind        string
	StatusCode  int
	ContentType string
	BodyPreview string
}

func (e *upstreamStreamOpenError) Error() string {
	return e.Message
}

func newChatRateLimiter(redisClient *redis.Client) rateLimiter {
	config := resolveChatRateLimitConfig()
	if redisClient != nil {
		return newRedisRateLimiterFailOpen(redisClient, config, redisRateLimitPrefix, defaultChatRateLimitWindow, defaultChatRateLimitMaxHits)
	}
	return newChatRateLimiterWithConfig(config)
}

func newChatRateLimiterWithConfig(config rateLimitConfig) *chatRateLimiter {
	config = normalizeRateLimitConfig(config, defaultChatRateLimitWindow, defaultChatRateLimitMaxHits, defaultChatRateLimitPruneInterval)
	return &chatRateLimiter{
		buckets:       map[string][]time.Time{},
		window:        config.Window,
		maxHits:       config.MaxHits,
		pruneInterval: config.PruneInterval,
	}
}

func (l *chatRateLimiter) Consume(userID string, now time.Time) (bool, int) {
	l.mu.Lock()
	defer l.mu.Unlock()

	if l.lastPrune.IsZero() {
		l.lastPrune = now
	} else if now.Sub(l.lastPrune) >= l.pruneInterval {
		l.pruneLocked(now)
		l.lastPrune = now
	}

	existing := l.buckets[userID]
	valid := existing[:0]
	for _, ts := range existing {
		if now.Sub(ts) < l.window {
			valid = append(valid, ts)
		}
	}

	if len(valid) >= l.maxHits {
		retryAfter := l.window - now.Sub(valid[0])
		l.buckets[userID] = append([]time.Time(nil), valid...)
		return false, maxInt(1, int(retryAfter.Seconds())+1)
	}

	valid = append(valid, now)
	l.buckets[userID] = append([]time.Time(nil), valid...)
	return true, 0
}

func (l *chatRateLimiter) Refund(userID string) {
	if l == nil {
		return
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	bucket := l.buckets[userID]
	if len(bucket) == 0 {
		return
	}
	if len(bucket) == 1 {
		delete(l.buckets, userID)
		return
	}
	l.buckets[userID] = append([]time.Time(nil), bucket[:len(bucket)-1]...)
}

func (l *chatRateLimiter) pruneLocked(now time.Time) {
	for userID, bucket := range l.buckets {
		valid := bucket[:0]
		for _, ts := range bucket {
			if now.Sub(ts) < l.window {
				valid = append(valid, ts)
			}
		}
		if len(valid) == 0 {
			delete(l.buckets, userID)
			continue
		}
		l.buckets[userID] = append([]time.Time(nil), valid...)
	}
}

func (s *Server) handleChatStream(w http.ResponseWriter, r *http.Request) {
	requestReceivedAtMs := time.Now().UnixMilli()
	auth, ok := s.requireAuth(w, r)
	if !ok {
		return
	}

	var body ChatStreamRequest
	if err := decodeJSONBody(r, &body); err != nil {
		s.writeJSONDecodeError(w, err)
		return
	}

	clientMsgID := strings.TrimSpace(body.ClientMsgID)
	text := strings.TrimSpace(body.Text)
	images := normalizeImages(body.Images)
	if validationError := validateChatStreamInput(clientMsgID, text, images); validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	if validationError := s.validateChatStreamImageURLs(r, images); validationError != "" {
		s.writeError(w, http.StatusBadRequest, validationError)
		return
	}
	todayAgriContextDay := normalizeTodayAgriContextDay(body.TodayAgriContextDay)
	requestHash := chatStreamRequestHash(text, images, todayAgriContextDay)

	ctx := r.Context()
	if err := s.store.EnsureUser(ctx, auth.UserID, TierFree); err != nil {
		s.logger.Error("ensure user failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	if stale, err := s.isStaleChatStreamRequest(ctx, auth.UserID, body.SessionGeneration, requestReceivedAtMs); err != nil {
		s.logger.Error("check session generation failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	} else if stale {
		s.writeJSON(w, http.StatusConflict, map[string]any{
			"error":         "STALE_SESSION_GENERATION",
			"client_msg_id": clientMsgID,
		})
		return
	}

	completion, staleCompletion, err := s.getSessionRoundCompletionForCurrentGeneration(ctx, auth.UserID, clientMsgID, body.SessionGeneration)
	if err != nil {
		s.logger.Error("check session replay failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if staleCompletion {
		s.writeJSON(w, http.StatusConflict, map[string]any{
			"error":         "STALE_SESSION_GENERATION",
			"client_msg_id": clientMsgID,
		})
		return
	}
	if completion.ArchiveMissing {
		s.writeJSON(w, http.StatusConflict, map[string]any{
			"error":         "CLIENT_MSG_ID_CONFLICT",
			"client_msg_id": clientMsgID,
		})
		return
	}
	if completion.Completed {
		if completion.RequestHash != "" && completion.RequestHash != requestHash {
			s.writeJSON(w, http.StatusConflict, map[string]any{
				"error":         "CLIENT_MSG_ID_CONFLICT",
				"client_msg_id": clientMsgID,
			})
			return
		}
		s.writeSSEHeaders(w)
		s.writeSSEData(w, map[string]any{"ok": true, "replay": true, "client_msg_id": clientMsgID})
		s.writeSSEString(w, "data: [DONE]\n\n")
		return
	}

	if !s.bailian.HasKeyConfigured() {
		s.writeJSON(w, http.StatusServiceUnavailable, map[string]any{
			"error":   "MODEL_BACKEND_NOT_CONFIGURED",
			"message": "后端未配置大模型服务，当前无法使用真实流式对话",
		})
		return
	}

	var acquiredInflight bool
	var inflightToken string
	err = s.store.WithUserChatStreamGate(ctx, auth.UserID, func(ctx context.Context) error {
		var acquireErr error
		acquiredInflight, inflightToken, acquireErr = s.store.TryAcquireChatStreamInflight(ctx, auth.UserID, clientMsgID, time.Now(), resolveChatStreamInflightLeaseDuration())
		return acquireErr
	})
	if err != nil {
		if err == ErrChatStreamGateBusy {
			s.writeJSON(w, http.StatusConflict, map[string]any{
				"error":         "STREAM_IN_PROGRESS",
				"client_msg_id": clientMsgID,
			})
			return
		}
		s.logger.Error("acquire chat stream inflight failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if !acquiredInflight {
		s.writeJSON(w, http.StatusConflict, map[string]any{
			"error":         "STREAM_IN_PROGRESS",
			"client_msg_id": clientMsgID,
		})
		return
	}
	defer func() {
		releaseCtx, releaseCancel := context.WithTimeout(context.Background(), chatStreamInflightReleaseTimeout)
		defer releaseCancel()
		if err := s.store.ReleaseChatStreamInflight(releaseCtx, auth.UserID, clientMsgID, inflightToken); err != nil {
			s.logger.Warn("release chat stream inflight failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", err)
		}
	}()

	completion, staleCompletion, err = s.getSessionRoundCompletionForCurrentGeneration(ctx, auth.UserID, clientMsgID, body.SessionGeneration)
	if err != nil {
		s.logger.Error("recheck session replay failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	if staleCompletion {
		s.writeJSON(w, http.StatusConflict, map[string]any{
			"error":         "STALE_SESSION_GENERATION",
			"client_msg_id": clientMsgID,
		})
		return
	}
	if completion.ArchiveMissing {
		s.writeJSON(w, http.StatusConflict, map[string]any{
			"error":         "CLIENT_MSG_ID_CONFLICT",
			"client_msg_id": clientMsgID,
		})
		return
	}
	if completion.Completed {
		if completion.RequestHash != "" && completion.RequestHash != requestHash {
			s.writeJSON(w, http.StatusConflict, map[string]any{
				"error":         "CLIENT_MSG_ID_CONFLICT",
				"client_msg_id": clientMsgID,
			})
			return
		}
		s.writeSSEHeaders(w)
		s.writeSSEData(w, map[string]any{"ok": true, "replay": true, "client_msg_id": clientMsgID})
		s.writeSSEString(w, "data: [DONE]\n\n")
		return
	}

	if stale, err := s.isStaleChatStreamRequest(ctx, auth.UserID, body.SessionGeneration, requestReceivedAtMs); err != nil {
		s.logger.Error("recheck session generation failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	} else if stale {
		s.writeJSON(w, http.StatusConflict, map[string]any{
			"error":         "STALE_SESSION_GENERATION",
			"client_msg_id": clientMsgID,
		})
		return
	}

	tier, _, err := s.store.GetTierForUser(ctx, auth.UserID, TierFree)
	if err != nil {
		s.logger.Error("get tier failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}
	aWindowRounds := getAWindowByTier(tier)
	memoryEveryRounds := GetMemoryDocumentInterval(tier)
	dayCN := GetTodayKeyCN(s.shanghai, time.Now())

	allowed, retryAfterSec := s.rateLimiter.Consume(chatRateLimitKey(auth.UserID), time.Now())
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

	snapshot, err := s.store.GetSessionSnapshot(ctx, auth.UserID)
	if err != nil {
		s.logger.Error("get session snapshot failed", "userId", auth.UserID, "error", err)
		s.writeError(w, http.StatusInternalServerError, "internal_error")
		return
	}

	clientIP := GetClientIP(r)
	region := ParseRegionValues(body.Region, body.RegionSource, body.RegionReliability)
	if region == nil {
		region = ParseRegionFromHeaders(r.Header)
	}
	if region == nil {
		resolved := ResolveRegionByIP(clientIP)
		region = &resolved
	}
	injectedTime := FormatShanghaiNowToSecond(s.shanghai, time.Now())
	contextHeader := fmt.Sprintf(
		"后台背景时间：%s（Asia/Shanghai，仅供参考）；后台背景地点：%s；地点可信度：%s（仅供参考）",
		injectedTime,
		region.Region,
		region.Reliability,
	)
	todayAgriContext := s.resolveTodayAgriChatContext(ctx, auth.UserID, todayAgriContextDay, dayCN)
	promptMessages, usedARoundsCount, hasMemoryDocument := s.buildPromptMessages(snapshot, aWindowRounds, text, images, contextHeader, todayAgriContext)
	promptChars := countBailianMessageContentRunes(promptMessages)
	promptHasImages := promptIncludesImageContext(snapshot, aWindowRounds, images)
	thinkingOptions := resolveChatThinkingOptionsForImageContext(promptHasImages)
	forceSearch := shouldForceSearchForChatText(text)
	thinkingOptions.ForceSearch = forceSearch

	if err := s.store.TouchSessionContext(ctx, auth.UserID, region.Region, region.Source, region.Reliability, time.Now().UnixMilli()); err != nil {
		s.logger.Warn("touch session context failed", "userId", auth.UserID, "error", err)
	}

	s.logger.Info("chat prompt assembly",
		"userId", auth.UserID,
		"auth_mode", auth.AuthMode,
		"masked_ip", auth.MaskedIP,
		"tier", tier,
		"used_a_rounds_count", usedARoundsCount,
		"has_memory_document", hasMemoryDocument,
		"prompt_chars", promptChars,
		"current_text_chars", len([]rune(text)),
		"current_image_count", len(images),
		"prompt_has_images", promptHasImages,
		"injected_time", injectedTime,
		"region", region.Region,
		"region_source", region.Source,
		"region_reliability", region.Reliability,
		"has_today_agri_context", todayAgriContext != "",
		"thinking_enabled", thinkingOptions.EnableThinking,
		"thinking_budget", thinkingOptions.ThinkingBudget,
		"forced_search", forceSearch,
	)

	upstreamCtx, cancelUpstream := context.WithTimeout(context.Background(), resolveChatStreamMaxDuration())
	defer cancelUpstream()
	upstream, err := s.openValidatedBailianStreamWithRetry(upstreamCtx, promptMessages, thinkingOptions)
	if err != nil {
		s.respondUpstreamOpenError(
			w,
			err,
			"request_id", RequestIDFromContext(r.Context()),
			"userId", auth.UserID,
			"clientMsgId", clientMsgID,
			"tier", tier,
			"prompt_chars", promptChars,
			"current_image_count", len(images),
			"thinking_enabled", thinkingOptions.EnableThinking,
			"thinking_budget", thinkingOptions.ThinkingBudget,
			"forced_search", forceSearch,
		)
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
		"forced_search", forceSearch,
		"single_call_search", true,
	)

	var clientDisconnected atomic.Bool
	var doneReceived atomic.Bool
	var hasCitations atomic.Bool
	var hasSources atomic.Bool
	var writeMu sync.Mutex
	assistantText := strings.Builder{}
	var modelUsage bailianModelUsage
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

	type sseReadResult struct {
		line string
		err  error
	}
	reader := bufio.NewReader(upstream.Body)
	readResults := make(chan sseReadResult, 1)
	stopRead := make(chan struct{})
	var stopReadOnce sync.Once
	stopReadLoop := func() {
		stopReadOnce.Do(func() { close(stopRead) })
	}
	defer stopReadLoop()
	go func() {
		for {
			line, err := readLimitedSSELine(reader, maxBailianSSELineBytes)
			select {
			case readResults <- sseReadResult{line: line, err: err}:
			case <-stopRead:
				return
			}
			if err != nil {
				return
			}
		}
	}()
	firstVisibleTimeout := resolveChatStreamFirstVisibleTimeout()
	firstVisibleTimer := time.NewTimer(firstVisibleTimeout)
	defer firstVisibleTimer.Stop()
	idleTimeout := resolveChatStreamIdleTimeout()
	var idleTimer *time.Timer
	var idleTimerC <-chan time.Time
	if idleTimeout > 0 {
		idleTimer = time.NewTimer(idleTimeout)
		if !idleTimer.Stop() {
			<-idleTimer.C
		}
		defer idleTimer.Stop()
	}
	streamTimeoutKind := ""
	for {
		var line string
		var readErr error
		select {
		case result := <-readResults:
			line = result.line
			readErr = result.err
		case <-firstVisibleTimer.C:
			streamTimeoutKind = "first_visible"
			cancelUpstream()
			_ = upstream.Body.Close()
			stopReadLoop()
			if !clientDisconnected.Load() {
				writeMu.Lock()
				s.writeSSEData(w, map[string]any{"error": "UPSTREAM_STREAM_TIMEOUT", "kind": streamTimeoutKind, "client_msg_id": clientMsgID})
				writeMu.Unlock()
			}
			s.logger.Error(
				"bailian stream watchdog timeout",
				"userId", auth.UserID,
				"clientMsgId", clientMsgID,
				"request_id", upstreamRequestID,
				"kind", streamTimeoutKind,
				"timeout_seconds", int(firstVisibleTimeout.Seconds()),
				"forced_search", forceSearch,
				"thinking_enabled", thinkingOptions.EnableThinking,
				"thinking_budget", thinkingOptions.ThinkingBudget,
				"assistant_reply_chars", len([]rune(strings.TrimSpace(assistantText.String()))),
			)
			readErr = context.DeadlineExceeded
		case <-idleTimerC:
			streamTimeoutKind = "idle"
			cancelUpstream()
			_ = upstream.Body.Close()
			stopReadLoop()
			if !clientDisconnected.Load() {
				writeMu.Lock()
				s.writeSSEData(w, map[string]any{"error": "UPSTREAM_IDLE_TIMEOUT", "kind": streamTimeoutKind, "client_msg_id": clientMsgID})
				writeMu.Unlock()
			}
			s.logger.Error(
				"bailian stream watchdog timeout",
				"userId", auth.UserID,
				"clientMsgId", clientMsgID,
				"request_id", upstreamRequestID,
				"kind", streamTimeoutKind,
				"timeout_seconds", int(idleTimeout.Seconds()),
				"forced_search", forceSearch,
				"thinking_enabled", thinkingOptions.EnableThinking,
				"thinking_budget", thinkingOptions.ThinkingBudget,
				"assistant_reply_chars", len([]rune(strings.TrimSpace(assistantText.String()))),
			)
			readErr = context.DeadlineExceeded
		}
		if line != "" {
			trimmedLine := strings.TrimRight(line, "\r\n")
			if trimmedLine != "" && !strings.HasPrefix(trimmedLine, ":") && !strings.HasPrefix(trimmedLine, "event:") && strings.HasPrefix(trimmedLine, "data:") {
				data := strings.TrimLeft(strings.TrimPrefix(trimmedLine, "data:"), " ")
				if data == "[DONE]" {
					doneReceived.Store(true)
					break
				}
				beforeAssistantLen := assistantText.Len()
				updateAssistantAccumulator(data, &assistantText, &hasCitations, &hasSources, &modelUsage)
				if assistantText.Len() > beforeAssistantLen {
					if firstVisibleTimeout > 0 && !firstVisibleTimer.Stop() {
						select {
						case <-firstVisibleTimer.C:
						default:
						}
					}
					if idleTimer != nil {
						idleTimer.Reset(idleTimeout)
						idleTimerC = idleTimer.C
					}
				}
				if !clientDisconnected.Load() {
					clientData, shouldForward := filterBailianStreamDataForClient(data)
					if !shouldForward {
						continue
					}
					writeMu.Lock()
					_, err = io.WriteString(w, "data: "+clientData+"\n\n")
					if flusher != nil {
						flusher.Flush()
					}
					writeMu.Unlock()
					if err != nil {
						clientDisconnected.Store(true)
					}
				}
			}
		}

		if readErr != nil {
			if streamTimeoutKind != "" {
				break
			}
			if errors.Is(readErr, errSSELineTooLarge) {
				s.logger.Error("sse relay line too large", "userId", auth.UserID, "clientMsgId", clientMsgID, "limit_bytes", maxBailianSSELineBytes)
			} else if readErr != io.EOF {
				s.logger.Error("sse relay failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", readErr)
			}
			break
		}
		if doneReceived.Load() {
			break
		}
	}

	stopReadLoop()
	_ = upstream.Body.Close()

	sendDoneAfterArchive := false
	if doneReceived.Load() {
		replyText := strings.TrimSpace(assistantText.String())
		if replyText != "" {
			finalizeCtx, finalizeCancel := context.WithTimeout(context.Background(), chatStreamFinalizeTimeout)
			defer finalizeCancel()
			stale, appendErr := s.isStaleChatStreamRequest(finalizeCtx, auth.UserID, body.SessionGeneration, requestReceivedAtMs)
			if appendErr != nil {
				s.logger.Error("append session round after stream failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", appendErr)
			} else if stale {
				s.logger.Info("drop stale chat stream after session clear", "userId", auth.UserID, "clientMsgId", clientMsgID)
			} else {
				completionAtMs := time.Now().UnixMilli()
				var replay bool
				var updatedSnapshot *SessionSnapshot
				var appendErr error
				for attempt := 1; attempt <= appendSessionRoundMaxAttempts; attempt++ {
					replay, updatedSnapshot, appendErr = s.store.AppendSessionRoundComplete(
						finalizeCtx,
						auth.UserID,
						clientMsgID,
						requestHash,
						SessionRound{
							ClientMsgID:       clientMsgID,
							User:              text,
							UserImages:        images,
							Assistant:         replyText,
							CreatedAt:         requestReceivedAtMs,
							Region:            region.Region,
							RegionSource:      region.Source,
							RegionReliability: region.Reliability,
						},
						aWindowRounds,
						memoryEveryRounds,
						completionAtMs,
						tier,
						dayCN,
						"stream_done",
					)
					if appendErr == nil || !isRetryableSessionRoundAppendError(appendErr) {
						break
					}
					s.logger.Warn(
						"append session round after stream retryable failure",
						"userId", auth.UserID,
						"clientMsgId", clientMsgID,
						"attempt", attempt,
						"error", appendErr,
					)
					if attempt < appendSessionRoundMaxAttempts {
						if waitErr := waitForRetryDelay(finalizeCtx, time.Duration(attempt)*appendSessionRoundRetryBaseWait); waitErr != nil {
							appendErr = waitErr
							break
						}
					}
				}
				if appendErr != nil {
					appendConflict := errors.Is(appendErr, ErrSessionRoundRequestConflict) ||
						errors.Is(appendErr, ErrSessionRoundArchiveMissing)
					if appendConflict && !clientDisconnected.Load() {
						writeMu.Lock()
						s.writeSSEData(w, map[string]any{"error": "CLIENT_MSG_ID_CONFLICT", "client_msg_id": clientMsgID})
						writeMu.Unlock()
					} else if !clientDisconnected.Load() {
						writeMu.Lock()
						s.writeSSEData(w, map[string]any{"error": "STREAM_ARCHIVE_FAILED", "client_msg_id": clientMsgID})
						writeMu.Unlock()
					}
					s.logger.Error("append session round after stream failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", appendErr)
				} else {
					consume, consumeErr := s.store.consumeOnDoneAt(finalizeCtx, auth.UserID, tier, clientMsgID, dayCN, completionAtMs)
					if consumeErr != nil {
						s.logger.Error("quota consume on DONE failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", consumeErr)
						go s.retryQuotaConsumeOnDone(auth.UserID, tier, clientMsgID, dayCN, completionAtMs)
					} else {
						if err := s.store.MarkQuotaConsumeOutboxDone(finalizeCtx, auth.UserID, clientMsgID, time.Now().UnixMilli()); err != nil {
							s.logger.Warn("quota consume outbox mark done failed", "userId", auth.UserID, "clientMsgId", clientMsgID, "error", err)
						}
						s.logger.Info("quota consume on DONE", "userId", auth.UserID, "clientMsgId", clientMsgID, "deducted", consume.Deducted, "source", consume.Source)
					}
					sendDoneAfterArchive = true
					if !replay && updatedSnapshot != nil {
						snapshotCopy := cloneSessionSnapshot(*updatedSnapshot)
						go s.summary.ProcessSessionSummaries(auth.UserID, &snapshotCopy)
					}
				}
			}
		} else {
			if !clientDisconnected.Load() {
				writeMu.Lock()
				s.writeSSEData(w, map[string]any{"error": "EMPTY_ASSISTANT_REPLY", "client_msg_id": clientMsgID})
				writeMu.Unlock()
			}
			s.logger.Error("empty assistant reply after stream done", "userId", auth.UserID, "clientMsgId", clientMsgID)
		}
	}
	if sendDoneAfterArchive && !clientDisconnected.Load() {
		writeMu.Lock()
		_, err = io.WriteString(w, "data: [DONE]\n\n")
		if flusher != nil {
			flusher.Flush()
		}
		writeMu.Unlock()
		if err != nil {
			clientDisconnected.Store(true)
		}
	}
	close(stopHeartbeat)

	logAttrs := []any{
		"userId", auth.UserID,
		"clientMsgId", clientMsgID,
		"tier", tier,
		"request_id", upstreamRequestID,
		"enable_search", true,
		"strategy", "turbo",
		"forced_search", forceSearch,
		"current_image_count", len(images),
		"prompt_has_images", promptHasImages,
		"thinking_enabled", thinkingOptions.EnableThinking,
		"thinking_budget", thinkingOptions.ThinkingBudget,
		"has_citations", hasCitations.Load(),
		"has_sources", hasSources.Load(),
		"done_received", doneReceived.Load(),
		"send_done_after_archive", sendDoneAfterArchive,
		"client_disconnected", clientDisconnected.Load(),
		"stream_timeout_kind", streamTimeoutKind,
		"assistant_reply_chars", len([]rune(strings.TrimSpace(assistantText.String()))),
	}
	s.bailian.ObserveUsage(modelUsage)
	logAttrs = appendBailianUsageLogAttrs(logAttrs, modelUsage)
	s.logger.Info("bailian stream finished", logAttrs...)
}

func chatRateLimitKey(userID string) string {
	secret := strings.TrimSpace(os.Getenv("APP_SECRET"))
	return "chat:" + rateLimitHash(userID, secret)
}

func (s *Server) isStaleChatStreamRequest(ctx context.Context, userID string, expectedGeneration *int, _ int64) (bool, error) {
	state, err := s.store.GetSessionGenerationState(ctx, userID)
	if err != nil {
		return false, err
	}
	return isStaleForSessionGenerationState(state, expectedGeneration), nil
}

func (s *Server) getSessionRoundCompletionForCurrentGeneration(ctx context.Context, userID string, clientMsgID string, expectedGeneration *int) (SessionRoundCompletion, bool, error) {
	completion, err := s.store.GetSessionRoundCompletion(ctx, userID, clientMsgID)
	if err != nil || !completion.Completed {
		return completion, false, err
	}
	state, err := s.store.GetSessionGenerationState(ctx, userID)
	if err != nil {
		return SessionRoundCompletion{}, false, err
	}
	if isSessionRoundCompletionStaleForSessionGeneration(completion, state, expectedGeneration) {
		return completion, true, nil
	}
	return completion, false, nil
}

func isStaleForSessionGenerationState(state SessionGenerationState, expectedGeneration *int) bool {
	if expectedGeneration != nil {
		return *expectedGeneration != state.Generation
	}
	return state.ClearedAt > 0
}

func isSessionRoundCompletionStaleForSessionGeneration(completion SessionRoundCompletion, state SessionGenerationState, expectedGeneration *int) bool {
	if isStaleForSessionGenerationState(state, expectedGeneration) {
		return true
	}
	return isSessionRoundCompletionBeforeClear(completion, state)
}

func isSessionRoundCompletionBeforeClear(completion SessionRoundCompletion, state SessionGenerationState) bool {
	return completion.Completed && state.ClearedAt > 0 && completion.CreatedAt <= state.ClearedAt
}

func isRetryableSessionRoundAppendError(err error) bool {
	if err == nil {
		return false
	}
	return !errors.Is(err, ErrSessionRoundRequestConflict) &&
		!errors.Is(err, ErrSessionRoundArchiveMissing)
}

var errSSELineTooLarge = errors.New("sse line too large")

func readLimitedSSELine(reader *bufio.Reader, limit int) (string, error) {
	if limit <= 0 {
		limit = maxBailianSSELineBytes
	}
	var data []byte
	for {
		part, err := reader.ReadSlice('\n')
		if len(part) > 0 {
			data = append(data, part...)
			if len(data) > limit {
				return "", errSSELineTooLarge
			}
		}
		if err == bufio.ErrBufferFull {
			continue
		}
		return string(data), err
	}
}

func (s *Server) retryQuotaConsumeOnDone(userID string, tier Tier, clientMsgID string, dayCN string, completionAtMs int64) {
	delays := []time.Duration{
		500 * time.Millisecond,
		2 * time.Second,
		5 * time.Second,
	}
	for attempt, delay := range delays {
		time.Sleep(delay)
		consume, err := s.store.consumeOnDoneAt(context.Background(), userID, tier, clientMsgID, dayCN, completionAtMs)
		if err == nil {
			if markErr := s.store.MarkQuotaConsumeOutboxDone(context.Background(), userID, clientMsgID, time.Now().UnixMilli()); markErr != nil {
				s.logger.Warn("quota consume outbox mark done after retry failed", "userId", userID, "clientMsgId", clientMsgID, "error", markErr)
			}
			s.logger.Info(
				"quota consume on DONE retry recovered",
				"userId", userID,
				"clientMsgId", clientMsgID,
				"attempt", attempt+1,
				"deducted", consume.Deducted,
				"source", consume.Source,
			)
			return
		}
		s.logger.Warn(
			"quota consume on DONE retry failed",
			"userId", userID,
			"clientMsgId", clientMsgID,
			"attempt", attempt+1,
			"error", err,
		)
	}
	nowMs := time.Now().UnixMilli()
	nextAttemptAt := nowMs + int64(quotaConsumeRepairBackoff(0)/time.Millisecond)
	if err := s.store.MarkQuotaConsumeOutboxFailed(context.Background(), userID, clientMsgID, "short retry failed", nextAttemptAt, nowMs); err != nil {
		s.logger.Warn("quota consume outbox mark failed failed", "userId", userID, "clientMsgId", clientMsgID, "error", err)
	}
}

func (s *Server) openValidatedBailianStreamWithRetry(ctx context.Context, messages []BailianMessage, options BailianStreamOptions) (*http.Response, error) {
	var lastErr error
	for attempt := 1; attempt <= upstreamMaxAttempts; attempt++ {
		response, err := s.bailian.OpenStream(ctx, messages, options)
		if err != nil {
			lastErr = &upstreamStreamOpenError{
				Message:    "upstream request failed",
				Kind:       "request",
				StatusCode: http.StatusBadGateway,
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
			bodyPreview, _ := readLimitedResponseBody(response.Body, bailianBodyPreviewLimit)
			_ = response.Body.Close()
			lastErr = &upstreamStreamOpenError{
				Message:     fmt.Sprintf("upstream http %d", response.StatusCode),
				Kind:        "http",
				StatusCode:  response.StatusCode,
				BodyPreview: sanitizeUpstreamErrorPreview(string(bodyPreview)),
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
			_, _ = readLimitedResponseBody(response.Body, bailianBodyPreviewLimit)
			_ = response.Body.Close()
			lastErr = &upstreamStreamOpenError{
				Message:     "upstream not SSE",
				Kind:        "protocol",
				StatusCode:  http.StatusBadGateway,
				ContentType: contentType,
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

func (s *Server) respondUpstreamOpenError(w http.ResponseWriter, err error, attrs ...any) {
	openErr, ok := err.(*upstreamStreamOpenError)
	if !ok {
		logAttrs := append([]any{}, attrs...)
		logAttrs = append(logAttrs, "error", err)
		s.logger.Error("upstream request failed", logAttrs...)
		s.writeError(w, http.StatusBadGateway, "upstream request failed")
		return
	}

	switch openErr.Kind {
	case "http":
		logAttrs := append([]any{}, attrs...)
		logAttrs = append(logAttrs, "status", openErr.StatusCode)
		if openErr.BodyPreview != "" {
			logAttrs = append(logAttrs, "upstream_error_preview", openErr.BodyPreview)
		}
		s.logger.Error("upstream non-200 after retry", logAttrs...)
		s.writeError(w, http.StatusBadGateway, "upstream_error")
	case "protocol":
		logAttrs := append([]any{}, attrs...)
		logAttrs = append(logAttrs, "contentType", openErr.ContentType)
		s.logger.Error("upstream is not SSE after retry", logAttrs...)
		s.writeError(w, http.StatusBadGateway, "upstream_error")
	default:
		s.logger.Error("upstream request failed after retry", attrs...)
		s.writeError(w, http.StatusBadGateway, "upstream_error")
	}
}

func (s *Server) writeSSEHeaders(w http.ResponseWriter) {
	w.Header().Set("Content-Type", "text/event-stream; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)
}

func resolveChatStreamMaxDuration() time.Duration {
	duration := envDurationWithDefault("CHAT_STREAM_MAX_DURATION_SECONDS", chatStreamMaxDuration)
	if duration <= 0 {
		return chatStreamMaxDuration
	}
	return duration
}

func resolveChatStreamFirstVisibleTimeout() time.Duration {
	duration := envDurationWithDefault("CHAT_STREAM_FIRST_VISIBLE_TIMEOUT_SECONDS", chatStreamFirstVisibleTimeout)
	if duration <= 0 {
		return chatStreamFirstVisibleTimeout
	}
	if maxDuration := resolveChatStreamMaxDuration(); duration > maxDuration {
		return maxDuration
	}
	return duration
}

func resolveChatStreamIdleTimeout() time.Duration {
	duration := envDurationWithDefault("CHAT_STREAM_IDLE_TIMEOUT_SECONDS", chatStreamIdleTimeout)
	if duration <= 0 {
		return chatStreamIdleTimeout
	}
	if maxDuration := resolveChatStreamMaxDuration(); duration > maxDuration {
		return maxDuration
	}
	return duration
}

func resolveChatStreamInflightLeaseDuration() time.Duration {
	duration := resolveChatStreamMaxDuration() + chatStreamInflightLeaseGrace
	minimum := chatStreamInflightLease + chatStreamInflightLeaseGrace
	if duration < minimum {
		return minimum
	}
	return duration
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

func resolveChatThinkingOptions(text string, images []string) BailianStreamOptions {
	return resolveChatThinkingOptionsForImageContext(len(images) > 0)
}

func resolveChatThinkingOptionsForImageContext(hasImageContext bool) BailianStreamOptions {
	mode := strings.ToLower(strings.TrimSpace(os.Getenv("CHAT_THINKING_MODE")))
	if mode == "" {
		mode = defaultChatThinkingMode
	}
	switch mode {
	case "off", "false", "0", "no", "disabled":
		return BailianStreamOptions{}
	case "image", "images", "vision":
		if hasImageContext {
			return BailianStreamOptions{EnableThinking: true, ThinkingBudget: resolveChatThinkingBudget()}
		}
	case "always", "all", "auto", "on", "true", "1", "enabled":
		return BailianStreamOptions{EnableThinking: true, ThinkingBudget: resolveChatThinkingBudget()}
	default:
		return BailianStreamOptions{EnableThinking: true, ThinkingBudget: resolveChatThinkingBudget()}
	}
	return BailianStreamOptions{}
}

func shouldForceSearchForChatText(text string) bool {
	normalized := strings.ToLower(strings.TrimSpace(text))
	if normalized == "" {
		return false
	}
	searchIntentMarkers := []string{
		"联网",
		"全网",
		"搜索",
		"搜一下",
		"搜搜",
		"查一下",
		"查一查",
		"查查",
		"帮我查",
		"网上",
		"网店",
		"电商",
		"有售",
		"有卖",
		"网上销售",
		"有没有销售",
		"购买渠道",
		"哪里买",
		"在哪买",
		"哪里有卖",
		"价格",
		"报价",
		"多少钱",
		"查价",
		"查报价",
		"行情",
		"查行情",
		"最新价格",
		"最新报价",
		"最新行情",
		"市场价",
		"网购",
		"淘宝",
		"京东",
		"拼多多",
		"1688",
	}
	for _, marker := range searchIntentMarkers {
		if strings.Contains(normalized, marker) {
			return true
		}
	}
	return false
}

func promptIncludesImageContext(snapshot *SessionSnapshot, aWindowRounds int, currentImages []string) bool {
	if len(currentImages) > 0 {
		return true
	}
	if snapshot == nil || aWindowRounds <= 0 || len(snapshot.ARoundsFull) == 0 {
		return false
	}
	rounds := snapshot.ARoundsFull
	if len(rounds) > aWindowRounds {
		rounds = rounds[len(rounds)-aWindowRounds:]
	}
	if len(rounds) == 0 {
		return false
	}
	return len(rounds[len(rounds)-1].UserImages) > 0
}

func resolveChatThinkingBudget() int {
	return defaultChatThinkingBudget
}

func sanitizeUpstreamErrorPreview(raw string) string {
	preview := strings.TrimSpace(raw)
	if preview == "" {
		return ""
	}
	replacements := []string{
		"Authorization: Bearer ",
		"Bearer ",
	}
	for _, prefix := range replacements {
		if idx := strings.Index(preview, prefix); idx >= 0 {
			end := idx + len(prefix)
			for end < len(preview) {
				c := preview[end]
				if !(c == '.' || c == '-' || c == '_' || c == '~' || c == '+' || c == '/' || c == '=' || c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
					break
				}
				end++
			}
			preview = preview[:idx+len(prefix)] + "REDACTED" + preview[end:]
		}
	}
	for searchStart := 0; searchStart < len(preview); {
		idxRel := strings.Index(preview[searchStart:], "/uploads/")
		if idxRel < 0 {
			break
		}
		idx := searchStart + idxRel
		end := idx + len("/uploads/")
		for end < len(preview) {
			c := preview[end]
			if !(c == '/' || c == '.' || c == '-' || c == '_' || c >= '0' && c <= '9' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
				break
			}
			end++
		}
		replacement := "/uploads/REDACTED.jpg"
		preview = preview[:idx] + replacement + preview[end:]
		searchStart = idx + len(replacement)
	}
	if len([]rune(preview)) > 700 {
		preview = string([]rune(preview)[:700])
	}
	return preview
}

func filterBailianStreamDataForClient(data string) (string, bool) {
	payload := map[string]any{}
	if err := json.Unmarshal([]byte(data), &payload); err != nil {
		return "", false
	}

	changed := false
	clientVisible := false
	choices, _ := payload["choices"].([]any)
	if len(choices) == 0 {
		if _, ok := payload["usage"]; ok {
			clientVisible = true
		}
	}
	for _, choice := range choices {
		choiceMap, _ := choice.(map[string]any)
		if choiceMap == nil {
			continue
		}
		if finishReason, exists := choiceMap["finish_reason"]; exists && finishReason != nil && finishReason != "" {
			clientVisible = true
		}
		for _, key := range []string{"delta", "message"} {
			contentMap, _ := choiceMap[key].(map[string]any)
			if contentMap == nil {
				continue
			}
			if _, exists := contentMap["reasoning_content"]; exists {
				delete(contentMap, "reasoning_content")
				changed = true
			}
			if asString(contentMap["content"]) != "" {
				clientVisible = true
			}
			if _, exists := contentMap["citations"]; exists {
				clientVisible = true
			}
			if _, exists := contentMap["sources"]; exists {
				clientVisible = true
			}
		}
	}
	if !clientVisible {
		return "", false
	}
	if !changed {
		return data, true
	}
	raw, err := json.Marshal(payload)
	if err != nil {
		return "", false
	}
	return string(raw), true
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

func normalizeTodayAgriContextDay(raw string) string {
	digits := make([]rune, 0, 8)
	for _, r := range strings.TrimSpace(raw) {
		if r >= '0' && r <= '9' {
			digits = append(digits, r)
		}
		if len(digits) > 8 {
			return ""
		}
	}
	if len(digits) != 8 {
		return ""
	}
	return string(digits)
}

func normalizeTodayAgriAnchorClientMsgID(raw string) string {
	anchor := strings.TrimSpace(raw)
	if strings.HasPrefix(anchor, "assistant_") {
		return strings.TrimSpace(strings.TrimPrefix(anchor, "assistant_"))
	}
	return anchor
}

func (s *Server) resolveTodayAgriChatContext(ctx context.Context, userID string, requestedDay string, currentDayCN string) string {
	dayCN := normalizeTodayAgriContextDay(requestedDay)
	if dayCN == "" || dayCN != currentDayCN || strings.TrimSpace(userID) == "" || s.store == nil {
		return ""
	}
	lookupCtx, cancel := context.WithTimeout(ctx, 2*time.Second)
	defer cancel()
	items, err := s.store.GetTodayAgriUserItems(lookupCtx, userID, dayCN, 1)
	if err != nil {
		if s.logger != nil {
			s.logger.Warn("load saved today agri context failed", "userId", userID, "day_cn", dayCN, "error", err)
		}
		return ""
	}
	if len(items) == 0 || items[0].DayCN != dayCN {
		return ""
	}
	anchorClientMsgID := normalizeTodayAgriAnchorClientMsgID(items[0].AnchorClientMsgID)
	if anchorClientMsgID == "" {
		return ""
	}
	roundsAfterAnchor, foundAnchor, err := s.store.CountSessionRoundsAfterClientMsgID(lookupCtx, userID, anchorClientMsgID)
	if err != nil {
		if s.logger != nil {
			s.logger.Warn("count today agri anchor rounds failed", "userId", userID, "day_cn", dayCN, "error", err)
		}
		return ""
	}
	if !foundAnchor || roundsAfterAnchor >= todayAgriContextRoundLimit {
		return ""
	}
	card := items[0].Card
	return formatTodayAgriChatContext(&card)
}

func formatTodayAgriChatContext(card *DailyAgriCard) string {
	if card == nil {
		return ""
	}
	items := make([]DailyAgriCardItem, 0, dailyAgriTargetItemCount)
	for _, item := range card.Items {
		title := strings.TrimSpace(item.Title)
		summary := strings.TrimSpace(item.Summary)
		if title == "" || summary == "" {
			continue
		}
		items = append(items, item)
		if len(items) == dailyAgriTargetItemCount {
			break
		}
	}
	if len(items) == 0 {
		return ""
	}
	var builder strings.Builder
	builder.WriteString("今日农情界面上下文（来自 App 主界面最近展示的当天资讯；不是用户地块、症状、诊断、长期记忆或账户信息。仅当用户明确追问今日农情、刚才/上面展示的农情，或第几条资讯时参考；否则忽略。）\n")
	builder.WriteString("今日农情")
	if dateText := formatTodayAgriChatDate(card.DateCN); dateText != "" {
		builder.WriteString(" · ")
		builder.WriteString(dateText)
	}
	for index, item := range items {
		builder.WriteString("\n\n")
		builder.WriteString(todayAgriChatItemPrefix(index))
		builder.WriteString(strings.TrimSpace(item.Title))
		builder.WriteString("\n")
		builder.WriteString(strings.TrimSpace(item.Summary))
		if source := dailyAgriPublicSourceName(item); source != "" {
			builder.WriteString("\n来源：")
			builder.WriteString(source)
		}
	}
	return builder.String()
}

func formatTodayAgriChatDate(dayCN string) string {
	dayCN = normalizeTodayAgriContextDay(dayCN)
	if len(dayCN) != 8 {
		return ""
	}
	month := strings.TrimLeft(dayCN[4:6], "0")
	day := strings.TrimLeft(dayCN[6:8], "0")
	if month == "" || day == "" {
		return ""
	}
	return month + "月" + day + "日"
}

func todayAgriChatItemPrefix(index int) string {
	switch index {
	case 0:
		return "一、"
	case 1:
		return "二、"
	case 2:
		return "三、"
	default:
		return fmt.Sprintf("%d. ", index+1)
	}
}

func (s *Server) buildPromptMessages(snapshot *SessionSnapshot, aWindowRounds int, currentText string, currentImages []string, contextHeader string, todayAgriContext string) ([]BailianMessage, int, bool) {
	rounds := []SessionRound{}
	hasMemoryDocument := false
	if snapshot != nil {
		hasMemoryDocument = strings.TrimSpace(snapshot.MemoryDocument) != ""
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
	if hasMemoryDocument {
		messages = append(messages, BailianMessage{Role: "system", Content: "后台背景信息中的记忆摘要（仅供参考；回答应聚焦用户本轮问题。非直接相关时，不要主动提及、展开、串联过往内容，或追加基于记忆的顺带建议）\n" + strings.TrimSpace(snapshot.MemoryDocument)})
	}
	if pendingMemoryContext := buildPendingMemoryPromptContext(snapshot, rounds); pendingMemoryContext != "" {
		messages = append(messages, BailianMessage{Role: "system", Content: pendingMemoryContext})
	}
	if trimmedTodayAgriContext := strings.TrimSpace(todayAgriContext); trimmedTodayAgriContext != "" {
		messages = append(messages, BailianMessage{Role: "system", Content: trimmedTodayAgriContext})
	}

	previousRoundIndex := len(rounds) - 1
	now := time.Now()
	for index, round := range rounds {
		messages = append(messages, BailianMessage{Role: "user", Content: s.roundToUserContent(round, index == previousRoundIndex, now)})
		messages = append(messages, BailianMessage{Role: "assistant", Content: round.Assistant})
	}
	messages = append(messages, BailianMessage{Role: "system", Content: chatDiagnosticConstraint})
	messages = append(messages, BailianMessage{Role: "user", Content: buildVisionUserContent(currentText, currentImages)})
	return messages, len(rounds), hasMemoryDocument
}

func buildPendingMemoryPromptContext(snapshot *SessionSnapshot, activeRounds []SessionRound) string {
	if snapshot == nil || len(snapshot.PendingMemoryJobs) == 0 {
		return ""
	}
	promptRounds := pendingMemoryPromptRoundsForJobs(snapshot.PendingMemoryJobs, activeRounds)
	if len(promptRounds) == 0 {
		return ""
	}
	dialogueText := buildDialogueText(promptRounds)
	if dialogueText == "" {
		return ""
	}
	return "后台背景信息中的待补偿历史片段（仅供参考；回答仍聚焦用户本轮问题。非直接相关时，不要主动提及、展开、串联过往内容，或追加基于背景信息的顺带建议）\n" + dialogueText
}

func pendingMemoryPromptRoundsForJobs(jobs []MemoryExtractionJob, activeRounds []SessionRound) []SessionRound {
	if len(jobs) == 0 {
		return nil
	}
	activeIDs := map[string]struct{}{}
	for _, round := range activeRounds {
		if id := strings.TrimSpace(round.ClientMsgID); id != "" {
			activeIDs[id] = struct{}{}
		}
	}
	seenIDs := map[string]struct{}{}
	promptRounds := []SessionRound{}
	for _, job := range jobs {
		if len(job.Rounds) == 0 {
			continue
		}
		promptRounds = append(
			promptRounds,
			pendingMemoryPromptRoundsExcludingIDs(job, activeIDs, seenIDs)...,
		)
	}
	return promptRounds
}

func pendingMemoryPromptRoundsExcludingIDs(job MemoryExtractionJob, activeIDs map[string]struct{}, seenIDs map[string]struct{}) []SessionRound {
	promptRounds := make([]SessionRound, 0, len(job.Rounds))
	for _, round := range job.Rounds {
		id := strings.TrimSpace(round.ClientMsgID)
		if id == "" {
			promptRounds = append(promptRounds, round)
			continue
		}
		if _, ok := activeIDs[id]; ok {
			continue
		}
		if _, ok := seenIDs[id]; ok {
			continue
		}
		seenIDs[id] = struct{}{}
		promptRounds = append(promptRounds, round)
	}
	return promptRounds
}

func memoryJobNeedsPromptContext(job MemoryExtractionJob, activeRounds []SessionRound) bool {
	return len(pendingMemoryPromptRounds(job, activeRounds)) > 0
}

func pendingMemoryPromptRounds(job MemoryExtractionJob, activeRounds []SessionRound) []SessionRound {
	activeIDs := map[string]struct{}{}
	for _, round := range activeRounds {
		if id := strings.TrimSpace(round.ClientMsgID); id != "" {
			activeIDs[id] = struct{}{}
		}
	}
	return pendingMemoryPromptRoundsExcludingIDs(job, activeIDs, map[string]struct{}{})
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
	} else {
		content = append(content, map[string]any{
			"type": "text",
			"text": "用户本轮只上传了图片，未补充文字描述。请先基于图片可见信息给出农业技术参考判断；若作物、部位、症状或环境信息不足，请明确追问必要信息。",
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

func (s *Server) roundToUserContent(round SessionRound, includeImages bool, now time.Time) any {
	userText := round.userTextWithContextTime(s.shanghai)
	if !includeImages {
		return userText
	}
	if len(round.UserImages) == 0 || !historicalImagesWithinContextTTL(round.CreatedAt, now) {
		return userText
	}
	return buildVisionUserContent(userText, round.UserImages)
}

func historicalImagesWithinContextTTL(createdAt int64, now time.Time) bool {
	if createdAt <= 0 {
		return false
	}
	created := time.UnixMilli(createdAt)
	if now.IsZero() || now.Before(created) {
		return true
	}
	return now.Sub(created) <= chatHistoricalImageContextTTL
}

func (round SessionRound) userTextWithContextTime(loc *time.Location) string {
	contextLines := []string{}
	if timestamp := FormatShanghaiUnixMilliToSecond(loc, round.CreatedAt); timestamp != "" {
		contextLines = append(contextLines, "后台背景时间："+timestamp+"（Asia/Shanghai，仅供参考）")
	}
	if region := strings.TrimSpace(round.Region); region != "" && region != "未知" {
		reliability := strings.TrimSpace(string(round.RegionReliability))
		if reliability == "" {
			reliability = string(RegionUnreliable)
		}
		contextLines = append(contextLines, "后台背景地点："+region+"；地点可信度："+reliability+"（仅供参考）")
	}
	if len(contextLines) == 0 {
		return round.User
	}
	text := strings.TrimSpace(round.User)
	if text == "" {
		return strings.Join(contextLines, "\n")
	}
	return strings.Join(contextLines, "\n") + "\n" + text
}

func validateChatStreamInput(clientMsgID string, text string, images []string) string {
	if clientMsgID == "" {
		return "client_msg_id required"
	}
	if len(clientMsgID) > maxClientMsgIDLength {
		return "client_msg_id too long"
	}
	if len([]rune(strings.TrimSpace(text))) > maxChatTextRunes {
		return "text too long"
	}
	if len(images) > 4 {
		return "single request supports up to 4 images"
	}
	if strings.TrimSpace(text) == "" && len(images) == 0 {
		return "text or images required"
	}
	return ""
}

func chatStreamRequestHash(text string, images []string, todayAgriContextDay string) string {
	payload := struct {
		Text                string   `json:"text"`
		Images              []string `json:"images"`
		TodayAgriContextDay string   `json:"today_agri_context_day,omitempty"`
	}{
		Text:                strings.TrimSpace(text),
		Images:              normalizeImages(images),
		TodayAgriContextDay: normalizeTodayAgriContextDay(todayAgriContextDay),
	}
	raw, _ := json.Marshal(payload)
	sum := sha256.Sum256(raw)
	return hex.EncodeToString(sum[:])
}

func (s *Server) validateChatStreamImageURLs(r *http.Request, images []string) string {
	if len(images) == 0 {
		return ""
	}
	publicBaseURL := resolvePublicBaseURL(r)
	baseURL, err := url.Parse(publicBaseURL)
	if err != nil || baseURL.Scheme != "https" || baseURL.Host == "" {
		return "image host not configured"
	}
	for _, image := range images {
		parsed, err := url.Parse(image)
		if err != nil ||
			parsed.Scheme != "https" ||
			parsed.User != nil ||
			!strings.EqualFold(parsed.Host, baseURL.Host) ||
			parsed.RawQuery != "" ||
			parsed.Fragment != "" ||
			!isUploadedImagePath(parsed.Path) {
			return "invalid image url"
		}
	}
	return ""
}

func isUploadedImagePath(path string) bool {
	name := strings.TrimPrefix(path, "/uploads/")
	if name == path || name == "" {
		return false
	}
	return isPlainUploadFilename(name)
}

func updateAssistantAccumulator(data string, assistantText *strings.Builder, hasCitations *atomic.Bool, hasSources *atomic.Bool, modelUsage *bailianModelUsage) {
	payload := map[string]any{}
	if err := json.Unmarshal([]byte(data), &payload); err != nil {
		return
	}
	if usage, ok := parseBailianUsagePayload(payload["usage"]); ok && modelUsage != nil {
		*modelUsage = usage
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

func parseBailianUsagePayload(raw any) (bailianModelUsage, bool) {
	if raw == nil {
		return bailianModelUsage{}, false
	}
	encoded, err := json.Marshal(raw)
	if err != nil {
		return bailianModelUsage{}, false
	}
	var usage bailianModelUsage
	if err := json.Unmarshal(encoded, &usage); err != nil {
		return bailianModelUsage{}, false
	}
	if usage.ReasoningTokens == 0 {
		usage.ReasoningTokens = usage.OutputTokensDetails.ReasoningTokens
	}
	return usage, usage.hasAny()
}

func anyMapValue(values map[string]any, key string) any {
	if values == nil {
		return nil
	}
	return values[key]
}

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
	upstreamMaxAttempts                 = 1
	upstreamRetryBaseWait               = 350 * time.Millisecond
	chatStreamMaxDuration               = 30 * time.Minute
	chatStreamFirstVisibleTimeout       = 3 * time.Minute
	chatStreamIdleTimeout               = 4 * time.Minute
	maxClientMsgIDLength                = 128
	maxChatTextRunes                    = 6000
	maxBailianSSELineBytes              = 256 * 1024
	appendSessionRoundMaxAttempts       = 3
	appendSessionRoundRetryBaseWait     = 150 * time.Millisecond
	chatStreamFinalizeTimeout           = 15 * time.Second
	chatStreamInflightReleaseTimeout    = 5 * time.Second
	chatStreamFirstVisibleBoundaryGrace = 150 * time.Millisecond
	defaultChatThinkingMode             = "always"
	defaultChatThinkingBudget           = 1024
	chatHistoricalImageContextTTL       = 72 * time.Hour
)

const chatOutputConstraint = `【输出约束】
禁止表格，不要用 Markdown 表格、竖线或横线画表；关键点少量加粗，排版适合手机阅读。必须碎片化排版。像行家聊天，别像客服。冷静客观，禁止客套话。

多用自然换行；有多个要点、步骤、提醒或对比内容时，优先使用编号列表或项目列表。

禁止英文输出，默认只用中文；只有专业名词、商品名、登记标签或用户原文需要原样保留时，才允许出现英文。

【回答输出参考范本。必须模仿语气排版】

基本差不多了。

但准确说，不是“没啥能优化”，而是大框架别再折腾了。现在继续改架构、换模型、加一堆功能，收益不大，反而容易把自己绕进去。

你现在已经差不多有了：

图片问诊。
上下文。
流式输出。
会员次数。
提示词风格。
短版范本。
联网触发思路。
前端滚动和渲染方向。
成本大概也算过了。

这些作为一个早期问诊系统，已经够跑测试了。

下一步重点不是继续“搭系统”，而是拿真实案例磨系统。

还值得优化的，只剩几个关键点：

1. 回答像不像农技员

这个最重要。

别像客服，别像百科。
要像地头农技员：先说大概啥问题，再说怎么验证，再说先怎么处理。

2. 诊断会不会乱跑

尤其是病害、虫害、药害、缺素、生理问题，要能先分大类。

不要求神断，但不能上来就乱确诊、乱开药。

3. 图片差的时候会不会追问

农户拍图肯定乱。

模糊、背光、只拍一片叶、不拍整株，这都是常态。

系统要会说：
这图还不能完全定，你再拍叶背、整株、发病部位、近景。

4. 风险边界要稳

用药、混配、采收期、高温、花期、幼果期，这些不能乱说死。

宁可说方向，不要硬给绝对配方。

5. 反馈闭环

每个案例最好能记录：

作物。
症状。
模型判断。
用户验证。
实际结果。
哪里错了。
下次怎么改。

这个比你再改一百遍提示词有用。

所以结论：

系统主体已经够用了。现在别再大改。

接下来就是：

真实农户试。
真实经销商试。
收集错例。
每周小改一轮。
慢慢把语气、判断、追问、风险提示磨顺。

你现在不是缺功能。
你现在缺的是真实场景把它捶一遍。
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
	requestReceivedAt := time.Now()
	requestReceivedAtMs := requestReceivedAt.UnixMilli()
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
	requestHash := chatStreamRequestHash(text, images)

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
	if before.Remaining <= 0 && topupBefore <= 0 {
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
	promptMessages, usedARoundsCount, hasMemoryDocument := s.buildPromptMessages(snapshot, aWindowRounds, text, images, contextHeader)
	gptRelayPromptMessages, _, _ := s.buildPromptMessagesWithOptions(snapshot, aWindowRounds, text, images, contextHeader, true)
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
		"thinking_enabled", thinkingOptions.EnableThinking,
		"thinking_budget", thinkingOptions.ThinkingBudget,
		"forced_search", forceSearch,
	)

	requestID := RequestIDFromContext(r.Context())
	modelCallMeta := modelCallContext{
		Chain:           "main_chat",
		UserID:          auth.UserID,
		ClientMsgID:     clientMsgID,
		RequestID:       requestID,
		Tier:            string(tier),
		ImageCount:      len(images),
		PromptHasImages: promptHasImages,
		ForcedSearch:    forceSearch,
		SearchStrategy:  "responses_auto_low",
		ReasoningEffort: gptRelayReasoningEffort(),
		ThinkingEnabled: thinkingOptions.EnableThinking,
		ThinkingBudget:  thinkingOptions.ThinkingBudget,
	}
	upstreamCtx, cancelUpstream := context.WithTimeout(withModelCallContext(context.Background(), modelCallMeta), resolveChatStreamMaxDuration())
	defer cancelUpstream()
	upstreamOpenStartedAt := time.Now()
	upstream, upstreamProvider, upstreamProviderCancel, _, err := s.openValidatedChatStreamWithFallback(upstreamCtx, requestReceivedAt, promptMessages, gptRelayPromptMessages, thinkingOptions)
	upstreamOpenedAt := time.Now()
	upstreamOpenMs := upstreamOpenedAt.Sub(upstreamOpenStartedAt).Milliseconds()
	requestToUpstreamOpenMs := upstreamOpenedAt.Sub(requestReceivedAt).Milliseconds()
	if err != nil {
		s.insertModelCallRecordAsync(ModelCallRecordInput{
			RecordType:             "stream_open_failed",
			Chain:                  "main_chat",
			UserID:                 auth.UserID,
			ClientMsgID:            clientMsgID,
			RequestID:              requestID,
			Provider:               upstreamProvider,
			ProviderLabel:          firstNonEmpty(upstreamProvider, "unknown"),
			ProviderSlot:           upstreamProvider,
			Model:                  modelCallModelForProvider(upstreamProvider),
			Status:                 "open_failed",
			ErrorKind:              upstreamOpenErrorKind(err),
			HTTPStatus:             upstreamOpenErrorStatus(err),
			Tier:                   string(tier),
			ImageCount:             len(images),
			PromptHasImages:        promptHasImages,
			ForcedSearch:           forceSearch,
			SearchStrategy:         "turbo",
			ReasoningEffort:        gptRelayReasoningEffort(),
			ThinkingEnabled:        thinkingOptions.EnableThinking,
			ThinkingBudget:         thinkingOptions.ThinkingBudget,
			OpenMs:                 upstreamOpenMs,
			RequestToOpenMs:        requestToUpstreamOpenMs,
			FirstVisibleMs:         -1,
			UpstreamFirstVisibleMs: -1,
			TotalMs:                time.Since(requestReceivedAt).Milliseconds(),
		})
		s.respondUpstreamOpenError(
			w,
			err,
			"request_id", requestID,
			"userId", auth.UserID,
			"clientMsgId", clientMsgID,
			"tier", tier,
			"prompt_chars", promptChars,
			"current_image_count", len(images),
			"upstream_open_ms", upstreamOpenMs,
			"request_to_upstream_open_ms", requestToUpstreamOpenMs,
			"thinking_enabled", thinkingOptions.EnableThinking,
			"thinking_budget", thinkingOptions.ThinkingBudget,
			"forced_search", forceSearch,
		)
		return
	}
	defer upstreamProviderCancel()
	defer upstream.Body.Close()

	s.writeSSEHeaders(w)
	flusher, _ := w.(http.Flusher)
	if flusher != nil {
		flusher.Flush()
	}

	upstreamRequestID := firstNonEmpty(upstream.Header.Get("x-request-id"), upstream.Header.Get("request-id"))
	upstreamEnableSearch, upstreamSearchStrategy, upstreamSingleCallSearch := chatUpstreamSearchLogConfig(upstreamProvider)
	s.logger.Info("chat upstream opened",
		"request_id", upstreamRequestID,
		"provider", upstreamProvider,
		"open_ms", upstreamOpenMs,
		"request_to_upstream_open_ms", requestToUpstreamOpenMs,
		"forced_search", forceSearch,
		"current_image_count", len(images),
	)
	s.logger.Info("chat upstream search config",
		"request_id", upstreamRequestID,
		"provider", upstreamProvider,
		"enable_search", upstreamEnableSearch,
		"strategy", upstreamSearchStrategy,
		"forced_search", forceSearch,
		"single_call_search", upstreamSingleCallSearch,
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
	startReadLoop := func(body io.Reader) (chan sseReadResult, func()) {
		reader := bufio.NewReader(body)
		readResults := make(chan sseReadResult, 1)
		stopRead := make(chan struct{})
		var stopReadOnce sync.Once
		stopReadLoop := func() {
			stopReadOnce.Do(func() { close(stopRead) })
		}
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
		return readResults, stopReadLoop
	}
	readResults, stopReadLoop := startReadLoop(upstream.Body)
	defer func() { stopReadLoop() }()
	firstVisibleTimeout := resolveChatStreamFirstVisibleTimeoutForProviderAfter(upstreamProvider, upstreamOpenedAt.Sub(requestReceivedAt))
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
	currentSSEEvent := ""
	gptRelaySearchCount := 0
	gptRelayFallbackReason := ""
	lastFallbackOpenMs := int64(-1)
	firstVisibleMs := int64(-1)
	upstreamFirstVisibleMs := int64(-1)
	fallbackGPTRelayToBailian := func(reason string) bool {
		if !isGPTRelayProvider(upstreamProvider) || strings.TrimSpace(assistantText.String()) != "" {
			return false
		}
		if clientDisconnected.Load() {
			return false
		}
		if gptRelayFallbackReason == "" {
			gptRelayFallbackReason = reason
			if s.gptRelay != nil {
				providerLabel, providerSlot, keySlot := modelCallProviderMetadata(upstreamProvider, upstream)
				if providerSlot != "" || keySlot != "" {
					status := reason
					if status != "first_visible_timeout" {
						status = "no_visible_text"
					}
					s.insertModelCallRecordAsync(ModelCallRecordInput{
						RecordType:             "key_attempt",
						Chain:                  "main_chat",
						UserID:                 auth.UserID,
						ClientMsgID:            clientMsgID,
						RequestID:              requestID,
						UpstreamRequestID:      upstreamRequestID,
						Provider:               gptRelayProvider,
						ProviderLabel:          providerLabel,
						ProviderSlot:           providerSlot,
						KeySlot:                keySlot,
						Model:                  gptRelayModelName(),
						Status:                 status,
						FallbackReason:         reason,
						Tier:                   string(tier),
						ImageCount:             len(images),
						PromptHasImages:        promptHasImages,
						ForcedSearch:           forceSearch,
						SearchStrategy:         upstreamSearchStrategy,
						ReasoningEffort:        gptRelayReasoningEffort(),
						ThinkingEnabled:        thinkingOptions.EnableThinking,
						ThinkingBudget:         thinkingOptions.ThinkingBudget,
						OpenMs:                 upstreamOpenMs,
						RequestToOpenMs:        requestToUpstreamOpenMs,
						FirstVisibleMs:         -1,
						UpstreamFirstVisibleMs: -1,
						TotalMs:                time.Since(requestReceivedAt).Milliseconds(),
						SearchCount:            gptRelaySearchCount,
					})
				}
				if reason == "first_visible_timeout" {
					s.gptRelay.coolDownResponseKey(upstream)
				}
			}
			if s.gptRelay != nil {
				state := s.gptRelay.ObserveCircuitFailure(time.Now(), reason)
				if state.Trigger != "" {
					s.logger.Warn("gpt relay circuit opened",
						"userId", auth.UserID,
						"clientMsgId", clientMsgID,
						"trigger", state.Trigger,
						"open_until", state.OpenUntil.Format(time.RFC3339),
						"consecutive_failures", state.ConsecutiveFailures,
						"window_requests", state.WindowRequests,
						"window_failures", state.WindowFailures,
						"reason", reason,
					)
				}
			}
		}
		s.logger.Warn(
			"gpt relay stream fallback to bailian",
			"userId", auth.UserID,
			"clientMsgId", clientMsgID,
			"request_id", upstreamRequestID,
			"reason", reason,
			"forced_search", forceSearch,
		)
		_ = upstream.Body.Close()
		upstreamProviderCancel()
		stopReadLoop()
		fallbackOpenStartedAt := time.Now()
		fallbackResponse, fallbackErr := s.openValidatedBailianStreamWithRetry(upstreamCtx, promptMessages, thinkingOptions)
		fallbackOpenedAt := time.Now()
		lastFallbackOpenMs = fallbackOpenedAt.Sub(fallbackOpenStartedAt).Milliseconds()
		if fallbackErr != nil {
			s.logger.Error(
				"gpt relay stream fallback failed",
				"userId", auth.UserID,
				"clientMsgId", clientMsgID,
				"error", fallbackErr,
				"reason", reason,
				"forced_search", forceSearch,
			)
			return false
		}
		upstream = fallbackResponse
		upstreamProviderCancel = func() {}
		upstreamProvider = "bailian"
		upstreamRequestID = firstNonEmpty(upstream.Header.Get("x-request-id"), upstream.Header.Get("request-id"))
		upstreamOpenedAt = fallbackOpenedAt
		upstreamOpenMs = lastFallbackOpenMs
		requestToUpstreamOpenMs = fallbackOpenedAt.Sub(requestReceivedAt).Milliseconds()
		upstreamEnableSearch, upstreamSearchStrategy, upstreamSingleCallSearch = chatUpstreamSearchLogConfig(upstreamProvider)
		currentSSEEvent = ""
		gptRelaySearchCount = 0
		hasSources.Store(false)
		hasCitations.Store(false)
		modelUsage = bailianModelUsage{}
		readResults, stopReadLoop = startReadLoop(upstream.Body)
		firstVisibleTimeout = resolveChatStreamFirstVisibleTimeoutForProviderAfter(upstreamProvider, 0)
		if !firstVisibleTimer.Stop() {
			select {
			case <-firstVisibleTimer.C:
			default:
			}
		}
		firstVisibleTimer.Reset(firstVisibleTimeout)
		s.logger.Info(
			"gpt relay fallback selected bailian",
			"userId", auth.UserID,
			"clientMsgId", clientMsgID,
			"request_id", upstreamRequestID,
			"reason", reason,
			"fallback_open_ms", lastFallbackOpenMs,
			"forced_search", forceSearch,
		)
		return true
	}
	for {
		var line string
		var readErr error
		select {
		case result := <-readResults:
			line = result.line
			readErr = result.err
		case <-firstVisibleTimer.C:
			select {
			case result := <-readResults:
				line = result.line
				readErr = result.err
				firstVisibleTimer.Reset(chatStreamFirstVisibleBoundaryGrace)
			default:
			}
			if line != "" || readErr != nil {
				break
			}
			if fallbackGPTRelayToBailian("first_visible_timeout") {
				continue
			}
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
				"chat stream watchdog timeout",
				"userId", auth.UserID,
				"clientMsgId", clientMsgID,
				"request_id", upstreamRequestID,
				"provider", upstreamProvider,
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
				"chat stream watchdog timeout",
				"userId", auth.UserID,
				"clientMsgId", clientMsgID,
				"request_id", upstreamRequestID,
				"provider", upstreamProvider,
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
			if trimmedLine == "" {
				currentSSEEvent = ""
				continue
			}
			if strings.HasPrefix(trimmedLine, "event:") {
				currentSSEEvent = strings.TrimSpace(strings.TrimPrefix(trimmedLine, "event:"))
				continue
			}
			if !strings.HasPrefix(trimmedLine, ":") && strings.HasPrefix(trimmedLine, "data:") {
				data := strings.TrimLeft(strings.TrimPrefix(trimmedLine, "data:"), " ")
				if data == "[DONE]" {
					if fallbackGPTRelayToBailian("done_before_visible_text") {
						continue
					}
					doneReceived.Store(true)
					break
				}
				hadVisibleAssistantText := strings.TrimSpace(assistantText.String()) != ""
				clientData := ""
				shouldForward := false
				upstreamDone := false
				upstreamFailed := false
				if isGPTRelayProvider(upstreamProvider) {
					clientData, shouldForward, upstreamDone, upstreamFailed = convertGPTRelayResponsesStreamDataForClient(currentSSEEvent, data, &assistantText, &hasCitations, &hasSources, &modelUsage, &gptRelaySearchCount)
				} else {
					updateAssistantAccumulator(data, &assistantText, &hasCitations, &hasSources, &modelUsage)
					clientData, shouldForward = filterBailianStreamDataForClient(data)
				}
				currentSSEEvent = ""
				if !hadVisibleAssistantText && strings.TrimSpace(assistantText.String()) != "" {
					if firstVisibleMs < 0 {
						firstVisibleMs = time.Since(requestReceivedAt).Milliseconds()
						upstreamFirstVisibleMs = time.Since(upstreamOpenedAt).Milliseconds()
					}
					if isGPTRelayProvider(upstreamProvider) && s.gptRelay != nil {
						s.gptRelay.ObserveCircuitSuccess(time.Now())
					}
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
				if isGPTRelayProvider(upstreamProvider) && upstreamFailed {
					if fallbackGPTRelayToBailian("failed_before_visible_text") {
						continue
					}
					streamTimeoutKind = "upstream_failed"
					if !clientDisconnected.Load() {
						writeMu.Lock()
						s.writeSSEData(w, map[string]any{"error": "UPSTREAM_STREAM_FAILED", "client_msg_id": clientMsgID})
						writeMu.Unlock()
					}
					s.logger.Error(
						"gpt relay stream failed after visible text",
						"userId", auth.UserID,
						"clientMsgId", clientMsgID,
						"request_id", upstreamRequestID,
						"assistant_reply_chars", len([]rune(strings.TrimSpace(assistantText.String()))),
					)
					break
				}
				if isGPTRelayProvider(upstreamProvider) && upstreamDone && strings.TrimSpace(assistantText.String()) == "" {
					if fallbackGPTRelayToBailian("completed_without_visible_text") {
						continue
					}
					clientData = ""
					shouldForward = false
				}
				if !clientDisconnected.Load() {
					if !shouldForward {
						if upstreamDone {
							doneReceived.Store(true)
							break
						}
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
				if upstreamDone {
					doneReceived.Store(true)
					break
				}
			}
		}

		if readErr != nil {
			if streamTimeoutKind == "" && fallbackGPTRelayToBailian("stream_ended_before_visible_text") {
				continue
			}
			if streamTimeoutKind == "" && isGPTRelayProvider(upstreamProvider) && strings.TrimSpace(assistantText.String()) != "" && !doneReceived.Load() {
				streamTimeoutKind = "upstream_incomplete"
				if !clientDisconnected.Load() {
					writeMu.Lock()
					s.writeSSEData(w, map[string]any{"error": "UPSTREAM_STREAM_FAILED", "kind": streamTimeoutKind, "client_msg_id": clientMsgID})
					writeMu.Unlock()
				}
				s.logger.Error(
					"gpt relay stream ended before completed event",
					"userId", auth.UserID,
					"clientMsgId", clientMsgID,
					"request_id", upstreamRequestID,
					"assistant_reply_chars", len([]rune(strings.TrimSpace(assistantText.String()))),
				)
				break
			}
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
		"provider", upstreamProvider,
		"tier", tier,
		"request_id", upstreamRequestID,
		"enable_search", upstreamEnableSearch,
		"strategy", upstreamSearchStrategy,
		"forced_search", forceSearch,
		"single_call_search", upstreamSingleCallSearch,
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
		"upstream_open_ms", upstreamOpenMs,
		"request_to_upstream_open_ms", requestToUpstreamOpenMs,
		"first_visible_ms", firstVisibleMs,
		"upstream_first_visible_ms", upstreamFirstVisibleMs,
		"total_ms", time.Since(requestReceivedAt).Milliseconds(),
		"assistant_reply_chars", len([]rune(strings.TrimSpace(assistantText.String()))),
	}
	if gptRelayFallbackReason != "" {
		logAttrs = append(logAttrs,
			"gpt_relay_fallback_reason", gptRelayFallbackReason,
			"gpt_relay_fallback_open_ms", lastFallbackOpenMs,
		)
	}
	if isGPTRelayProvider(upstreamProvider) {
		logAttrs = append(logAttrs,
			"gpt_relay_reasoning_effort", gptRelayReasoningEffort(),
			"gpt_relay_search_context_size", gptRelaySearchContextSize(),
		)
	}
	if upstreamProvider == "bailian" {
		s.bailian.ObserveUsage(modelUsage)
	}
	providerLabel, providerSlot, keySlot := modelCallProviderMetadata(upstreamProvider, upstream)
	visibleText := strings.TrimSpace(assistantText.String())
	s.insertModelCallRecordAsync(ModelCallRecordInput{
		RecordType:        "stream_final",
		Chain:             "main_chat",
		UserID:            auth.UserID,
		ClientMsgID:       clientMsgID,
		RequestID:         requestID,
		UpstreamRequestID: upstreamRequestID,
		Provider:          upstreamProvider,
		ProviderLabel:     providerLabel,
		ProviderSlot:      providerSlot,
		KeySlot:           keySlot,
		Model:             modelCallModelForProvider(upstreamProvider),
		Status:            modelCallStreamStatus(sendDoneAfterArchive, doneReceived.Load(), clientDisconnected.Load(), streamTimeoutKind, visibleText != ""),
		FallbackReason:    gptRelayFallbackReason,
		Tier:              string(tier),
		ImageCount:        len(images),
		PromptHasImages:   promptHasImages,
		ForcedSearch:      forceSearch,
		SearchStrategy:    upstreamSearchStrategy,
		ReasoningEffort: func() string {
			if isGPTRelayProvider(upstreamProvider) {
				return gptRelayReasoningEffort()
			}
			return ""
		}(),
		ThinkingEnabled:        thinkingOptions.EnableThinking,
		ThinkingBudget:         thinkingOptions.ThinkingBudget,
		OpenMs:                 upstreamOpenMs,
		RequestToOpenMs:        requestToUpstreamOpenMs,
		FirstVisibleMs:         firstVisibleMs,
		UpstreamFirstVisibleMs: upstreamFirstVisibleMs,
		TotalMs:                time.Since(requestReceivedAt).Milliseconds(),
		InputTokens:            modelUsage.normalizedInputTokens(),
		OutputTokens:           modelUsage.normalizedOutputTokens(),
		TotalTokens:            modelUsage.normalizedTotalTokens(),
		ReasoningTokens:        modelUsage.normalized().ReasoningTokens,
		CachedTokens:           modelUsage.cachedTokens(),
		SearchCount:            modelCallSearchCount(upstreamProvider, modelUsage, gptRelaySearchCount),
		ReplyChars:             len([]rune(visibleText)),
		ClientDisconnected:     clientDisconnected.Load(),
	})
	logAttrs = appendModelUsageLogAttrs(logAttrs, modelUsage, isGPTRelayProvider(upstreamProvider))
	s.logger.Info("chat stream finished", logAttrs...)
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

func (s *Server) openValidatedChatStreamWithFallback(ctx context.Context, requestReceivedAt time.Time, bailianMessages []BailianMessage, gptRelayMessages []BailianMessage, options BailianStreamOptions) (*http.Response, string, context.CancelFunc, *gptRelayRequestCursor, error) {
	noopCancel := func() {}
	if s.gptRelay != nil && s.gptRelay.Enabled() {
		remaining := resolveGPTRelayFirstVisibleTimeout(resolveChatStreamMaxDuration())
		if !requestReceivedAt.IsZero() {
			remaining -= time.Since(requestReceivedAt)
		}
		if remaining > 0 {
			circuitState := s.gptRelay.CircuitAllowRequest(time.Now())
			if !circuitState.Allowed {
				s.logger.Warn("gpt relay circuit open; fallback to bailian",
					"open_until", circuitState.OpenUntil.Format(time.RFC3339),
					"half_open", circuitState.HalfOpen,
					"consecutive_failures", circuitState.ConsecutiveFailures,
					"window_requests", circuitState.WindowRequests,
					"window_failures", circuitState.WindowFailures,
				)
			} else {
				gptRelayCursor := s.gptRelay.newRequestCursor()
				response, cancelGPT, openFailureReason, err := s.openGPTRelayStreamWithinBudget(ctx, gptRelayMessages, gptRelayCursor, remaining)
				if err == nil {
					return response, gptRelayProvider, cancelGPT, gptRelayCursor, nil
				}
				if ctx.Err() != nil {
					s.logger.Warn("gpt relay open aborted by request context; fallback to bailian", "error", ctx.Err().Error())
				}
				if ctx.Err() == nil {
					state := s.gptRelay.ObserveCircuitFailure(time.Now(), openFailureReason)
					if state.Trigger != "" {
						s.logger.Warn("gpt relay circuit opened",
							"trigger", state.Trigger,
							"open_until", state.OpenUntil.Format(time.RFC3339),
							"consecutive_failures", state.ConsecutiveFailures,
							"window_requests", state.WindowRequests,
							"window_failures", state.WindowFailures,
						)
					}
				}
			}
		} else {
			s.logger.Warn("gpt relay skipped because first visible budget exhausted; fallback to bailian")
		}
	}
	response, err := s.openValidatedBailianStreamWithRetry(ctx, bailianMessages, options)
	if err != nil {
		return nil, "bailian", noopCancel, nil, err
	}
	return response, "bailian", noopCancel, nil, nil
}

func (s *Server) openGPTRelayStreamWithinBudget(ctx context.Context, messages []BailianMessage, cursor *gptRelayRequestCursor, budget time.Duration) (*http.Response, context.CancelFunc, string, error) {
	if budget <= 0 {
		return nil, nil, "open_exceeded_budget", context.DeadlineExceeded
	}
	gptCtx, cancelGPT := context.WithCancel(ctx)
	budgetTimer := time.AfterFunc(budget, cancelGPT)
	response, err := s.openValidatedGPTRelayStream(gptCtx, messages, cursor)
	timerStopped := budgetTimer.Stop()
	if err == nil {
		if !timerStopped || gptCtx.Err() != nil {
			_ = response.Body.Close()
			cancelGPT()
			if ctx.Err() != nil {
				return nil, nil, "request_context_canceled", ctx.Err()
			}
			return nil, nil, "open_exceeded_budget", context.DeadlineExceeded
		}
		return response, cancelGPT, "", nil
	}
	gptCtxErr := gptCtx.Err()
	cancelGPT()
	if ctx.Err() != nil {
		return nil, nil, "request_context_canceled", ctx.Err()
	}
	if !timerStopped || gptCtxErr != nil {
		return nil, nil, "open_exceeded_budget", context.DeadlineExceeded
	}
	return nil, nil, "open_failed", err
}

func chatUpstreamSearchLogConfig(provider string) (bool, string, bool) {
	if provider == gptRelayProvider {
		return true, "responses_auto_low", true
	}
	return true, "turbo", true
}

func isGPTRelayProvider(provider string) bool {
	return provider == gptRelayProvider
}

func (s *Server) openValidatedGPTRelayStream(ctx context.Context, messages []BailianMessage, cursor *gptRelayRequestCursor) (*http.Response, error) {
	return s.openValidatedStreamWithRetry(ctx, gptRelayProvider, 1, func(openCtx context.Context) (*http.Response, error) {
		return s.gptRelay.openStreamWithCursor(openCtx, messages, cursor)
	})
}

func (s *Server) openValidatedBailianStreamWithRetry(ctx context.Context, messages []BailianMessage, options BailianStreamOptions) (*http.Response, error) {
	return s.openValidatedStreamWithRetry(ctx, "bailian", upstreamMaxAttempts, func(openCtx context.Context) (*http.Response, error) {
		return s.bailian.OpenStream(openCtx, messages, options)
	})
}

func (s *Server) openValidatedStreamWithRetry(ctx context.Context, provider string, maxAttempts int, open func(context.Context) (*http.Response, error)) (*http.Response, error) {
	var lastErr error
	if maxAttempts <= 0 {
		maxAttempts = 1
	}
	for attempt := 1; attempt <= maxAttempts; attempt++ {
		response, err := open(ctx)
		if err != nil {
			lastErr = &upstreamStreamOpenError{
				Message:    "upstream request failed",
				Kind:       "request",
				StatusCode: http.StatusBadGateway,
			}
			if attempt < maxAttempts && ctx.Err() == nil {
				s.logger.Warn("upstream open retry scheduled after request failure", "provider", provider, "attempt", attempt, "maxAttempts", maxAttempts, "error", err)
				if waitErr := waitForRetryDelay(ctx, upstreamRetryBaseWait*time.Duration(attempt)); waitErr != nil {
					return nil, waitErr
				}
				continue
			}
			return nil, lastErr
		}

		contentType := response.Header.Get("Content-Type")
		if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
			_, _ = readLimitedResponseBody(response.Body, bailianBodyPreviewLimit)
			_ = response.Body.Close()
			lastErr = &upstreamStreamOpenError{
				Message:    fmt.Sprintf("upstream http %d", response.StatusCode),
				Kind:       "http",
				StatusCode: response.StatusCode,
			}
			if attempt < maxAttempts && isRetryableUpstreamStatus(response.StatusCode) && ctx.Err() == nil {
				s.logger.Warn("upstream open retry scheduled after non-200 response", "provider", provider, "attempt", attempt, "maxAttempts", maxAttempts, "status", response.StatusCode)
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
			if attempt < maxAttempts && ctx.Err() == nil {
				s.logger.Warn("upstream open retry scheduled after non-SSE response", "provider", provider, "attempt", attempt, "maxAttempts", maxAttempts, "contentType", contentType)
				if waitErr := waitForRetryDelay(ctx, upstreamRetryBaseWait*time.Duration(attempt)); waitErr != nil {
					return nil, waitErr
				}
				continue
			}
			return nil, lastErr
		}

		if attempt > 1 {
			s.logger.Info("upstream open recovered after retry", "provider", provider, "attempt", attempt, "maxAttempts", maxAttempts)
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

func resolveChatStreamFirstVisibleTimeoutForProvider(provider string) time.Duration {
	if isGPTRelayProvider(provider) {
		return resolveGPTRelayFirstVisibleTimeout(resolveChatStreamMaxDuration())
	}
	return resolveChatStreamFirstVisibleTimeout()
}

func resolveChatStreamFirstVisibleTimeoutForProviderAfter(provider string, elapsed time.Duration) time.Duration {
	duration := resolveChatStreamFirstVisibleTimeoutForProvider(provider)
	if !isGPTRelayProvider(provider) {
		return duration
	}
	remaining := duration - elapsed
	if remaining <= 0 {
		return time.Millisecond
	}
	return remaining
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

func (s *Server) buildPromptMessages(snapshot *SessionSnapshot, aWindowRounds int, currentText string, currentImages []string, contextHeader string) ([]BailianMessage, int, bool) {
	return s.buildPromptMessagesWithOptions(snapshot, aWindowRounds, currentText, currentImages, contextHeader, true)
}

func (s *Server) buildPromptMessagesWithOptions(snapshot *SessionSnapshot, aWindowRounds int, currentText string, currentImages []string, contextHeader string, includeOutputConstraint bool) ([]BailianMessage, int, bool) {
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
	}
	if includeOutputConstraint {
		messages = append(messages, BailianMessage{Role: "system", Content: chatOutputConstraint})
	}
	if hasMemoryDocument {
		messages = append(messages, BailianMessage{Role: "system", Content: "后台背景信息中的记忆摘要（仅供参考；回答应聚焦用户本轮问题。非直接相关时，不要主动提及、展开、串联过往内容，或追加基于记忆的顺带建议）\n" + strings.TrimSpace(snapshot.MemoryDocument)})
	}
	if pendingMemoryContext := buildPendingMemoryPromptContext(snapshot, rounds); pendingMemoryContext != "" {
		messages = append(messages, BailianMessage{Role: "system", Content: pendingMemoryContext})
	}
	previousRoundIndex := len(rounds) - 1
	now := time.Now()
	for index, round := range rounds {
		messages = append(messages, BailianMessage{Role: "user", Content: s.roundToUserContent(round, index == previousRoundIndex, now)})
		messages = append(messages, BailianMessage{Role: "assistant", Content: round.Assistant})
	}
	if trimmedContextHeader := strings.TrimSpace(contextHeader); trimmedContextHeader != "" {
		messages = append(messages, BailianMessage{Role: "system", Content: trimmedContextHeader})
	}
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

func chatStreamRequestHash(text string, images []string) string {
	payload := struct {
		Text   string   `json:"text"`
		Images []string `json:"images"`
	}{
		Text:   strings.TrimSpace(text),
		Images: normalizeImages(images),
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

func convertGPTRelayResponsesStreamDataForClient(event string, data string, assistantText *strings.Builder, hasCitations *atomic.Bool, hasSources *atomic.Bool, modelUsage *bailianModelUsage, searchCount *int) (string, bool, bool, bool) {
	payload := map[string]any{}
	if err := json.Unmarshal([]byte(data), &payload); err != nil {
		return "", false, false, false
	}
	if payloadType := asString(payload["type"]); payloadType != "" {
		event = payloadType
	}
	switch event {
	case "response.output_text.delta":
		delta := asString(payload["delta"])
		if delta == "" {
			return "", false, false, false
		}
		hadVisibleText := strings.TrimSpace(assistantText.String()) != ""
		assistantText.WriteString(delta)
		if strings.TrimSpace(delta) == "" && !hadVisibleText {
			return "", false, false, false
		}
		clientData, ok := gptRelayChatDeltaPayload(delta)
		if !ok {
			return "", false, false, false
		}
		return clientData, true, false, false
	case "response.output_text.done":
		finalText := asString(payload["text"])
		if finalText == "" {
			return "", false, false, false
		}
		existing := assistantText.String()
		delta := ""
		switch {
		case existing == "":
			delta = finalText
		case strings.HasPrefix(finalText, existing) && len(finalText) > len(existing):
			delta = finalText[len(existing):]
		default:
			return "", false, false, false
		}
		hadVisibleText := strings.TrimSpace(existing) != ""
		assistantText.WriteString(delta)
		if strings.TrimSpace(delta) == "" && !hadVisibleText {
			return "", false, false, false
		}
		clientData, ok := gptRelayChatDeltaPayload(delta)
		if !ok {
			return "", false, false, false
		}
		return clientData, true, false, false
	case "response.web_search_call.in_progress", "response.web_search_call.searching", "response.web_search_call.completed":
		if searchCount != nil && *searchCount == 0 {
			*searchCount = 1
		}
		if hasSources != nil {
			hasSources.Store(true)
		}
		return "", false, false, false
	case "response.failed", "response.incomplete", "response.error", "error":
		return "", false, false, true
	case "response.completed":
		usage := parseGPTRelayResponsesUsage(payload)
		if searchCount != nil && *searchCount > 0 {
			usage.Plugins.Search.Count = *searchCount
		}
		if usage.hasAny() && modelUsage != nil {
			*modelUsage = usage
		}
		finishPayload := map[string]any{
			"choices": []map[string]any{
				{
					"delta":         map[string]any{},
					"finish_reason": "stop",
					"index":         0,
				},
			},
		}
		if usage.hasAny() {
			finishPayload["usage"] = usage
		}
		raw, err := json.Marshal(finishPayload)
		if err != nil {
			return "", false, true, false
		}
		return string(raw), true, true, false
	default:
		return "", false, false, false
	}
}

func gptRelayChatDeltaPayload(delta string) (string, bool) {
	clientPayload := map[string]any{
		"choices": []map[string]any{
			{
				"delta": map[string]any{
					"content": delta,
				},
				"finish_reason": nil,
				"index":         0,
			},
		},
	}
	raw, err := json.Marshal(clientPayload)
	if err != nil {
		return "", false
	}
	return string(raw), true
}

func parseGPTRelayResponsesUsage(payload map[string]any) bailianModelUsage {
	response, _ := payload["response"].(map[string]any)
	rawUsage := payload["usage"]
	if rawUsage == nil && response != nil {
		rawUsage = response["usage"]
	}
	if rawUsage == nil {
		return bailianModelUsage{}
	}
	raw, err := json.Marshal(rawUsage)
	if err != nil {
		return bailianModelUsage{}
	}
	var source struct {
		InputTokens         int `json:"input_tokens"`
		OutputTokens        int `json:"output_tokens"`
		TotalTokens         int `json:"total_tokens"`
		ReasoningTokens     int `json:"reasoning_tokens"`
		OutputTokensDetails struct {
			ReasoningTokens int `json:"reasoning_tokens"`
		} `json:"output_tokens_details"`
	}
	if err := json.Unmarshal(raw, &source); err != nil {
		return bailianModelUsage{}
	}
	usage := bailianModelUsage{
		InputTokens:     source.InputTokens,
		OutputTokens:    source.OutputTokens,
		TotalTokens:     source.TotalTokens,
		ReasoningTokens: source.ReasoningTokens,
	}
	usage.OutputTokensDetails.ReasoningTokens = source.OutputTokensDetails.ReasoningTokens
	if usage.ReasoningTokens == 0 {
		usage.ReasoningTokens = usage.OutputTokensDetails.ReasoningTokens
	}
	return usage
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

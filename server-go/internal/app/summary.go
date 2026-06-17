package app

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"
	"unicode/utf8"

	"github.com/redis/go-redis/v9"
)

type summaryStore interface {
	WriteUserMemoryDocumentIfCurrent(ctx context.Context, userID string, memoryDocument string, expectedRoundTotal int, expectedUpdatedAt int64, expectedSessionGeneration int) (bool, error)
	SetUserMemoryPendingIfCurrent(ctx context.Context, userID string, pending bool, expectedRoundTotal int, expectedUpdatedAt int64, expectedSessionGeneration int) (bool, error)
}

type SummaryService struct {
	store       summaryStore
	prompts     *PromptLoader
	bailian     *BailianClient
	logger      *slog.Logger
	redisClient *redis.Client
	running     sync.Map
}

const (
	summaryResponseLimit              = 64 * 1024
	summaryRedisLeasePrefix           = "nj:summary:lease:"
	defaultSummaryRedisLeaseTTL       = 2 * time.Minute
	defaultSummaryRedisLeaseOpTimeout = time.Second
	summaryRedisLeaseReleaseScript    = `
if redis.call("GET", KEYS[1]) == ARGV[1] then
  return redis.call("DEL", KEYS[1])
end
return 0
`
)

var summaryExtractionTimeout = 60 * time.Second

func NewSummaryService(store *Store, prompts *PromptLoader, bailian *BailianClient, logger *slog.Logger, redisClient *redis.Client) *SummaryService {
	if logger == nil {
		logger = slog.Default()
	}
	return &SummaryService{
		store:       store,
		prompts:     prompts,
		bailian:     bailian,
		logger:      logger,
		redisClient: redisClient,
	}
}

func (s *SummaryService) log() *slog.Logger {
	if s != nil && s.logger != nil {
		return s.logger
	}
	return slog.Default()
}

func GetMemoryDocumentInterval(tier Tier) int {
	if tier == TierPro {
		return 9
	}
	return 6
}

func (s *SummaryService) ProcessSessionSummaries(userID string, snapshot *SessionSnapshot) {
	if s == nil || snapshot == nil || !snapshot.PendingMemory {
		return
	}
	if !s.tryStart(userID) {
		s.log().Info("summary extraction skipped: already running", "userId", userID, "roundTotal", snapshot.RoundTotal)
		return
	}
	defer s.finish(userID)
	releaseLease, acquired := s.tryAcquireRemoteLease(context.Background(), userID)
	if !acquired {
		s.log().Info("summary extraction skipped: remote lease unavailable", "userId", userID, "roundTotal", snapshot.RoundTotal)
		return
	}
	defer releaseLease()
	if s.bailian == nil || !s.bailian.HasKeyConfigured() {
		s.log().Warn("summary extraction skipped: model backend unavailable", "userId", userID)
		return
	}

	dialogueText := buildDialogueText(snapshot.ARoundsFull)
	if dialogueText == "" {
		s.log().Warn("summary extraction skipped: empty dialogue", "userId", userID)
		return
	}

	ctx := context.Background()
	extractCtx, cancelExtract := context.WithTimeout(ctx, summaryExtractionTimeout)
	nextMemory, err := s.extractSummary(extractCtx, snapshot.MemoryDocument, dialogueText)
	cancelExtract()
	if err != nil {
		s.keepPending(ctx, userID, snapshot)
		s.log().Error("summary extraction failed", "userId", userID, "error", err)
		return
	}

	written, err := s.store.WriteUserMemoryDocumentIfCurrent(
		ctx,
		userID,
		nextMemory.MemoryDocument,
		snapshot.RoundTotal,
		snapshot.UpdatedAt,
		snapshot.SessionGeneration,
	)
	if err != nil {
		s.keepPending(ctx, userID, snapshot)
		s.log().Error("write memory document failed", "userId", userID, "error", err)
		return
	}
	if !written {
		s.log().Info("memory document write skipped: snapshot is stale", "userId", userID, "roundTotal", snapshot.RoundTotal)
		return
	}

	snapshot.MemoryDocument = nextMemory.MemoryDocument
	snapshot.PendingMemory = false
	s.log().Info("memory document extraction success", "userId", userID, "memory_chars", len(nextMemory.MemoryDocument))
}

func (s *SummaryService) tryStart(userID string) bool {
	key := userID + ":summary"
	_, loaded := s.running.LoadOrStore(key, struct{}{})
	return !loaded
}

func (s *SummaryService) finish(userID string) {
	key := userID + ":summary"
	s.running.Delete(key)
}

func (s *SummaryService) tryAcquireRemoteLease(ctx context.Context, userID string) (func(), bool) {
	if s == nil || s.redisClient == nil {
		return func() {}, true
	}
	if ctx == nil {
		ctx = context.Background()
	}
	token := newSummaryLeaseToken()
	key := summaryRedisLeasePrefix + rateLimitHash(userID, os.Getenv("APP_SECRET"))
	opCtx, cancel := context.WithTimeout(ctx, summaryRedisLeaseOpTimeout())
	acquired, err := s.redisClient.SetNX(opCtx, key, token, summaryRedisLeaseTTL()).Result()
	cancel()
	if err != nil {
		s.log().Warn("summary extraction remote lease failed", "userId", userID, "error", err)
		return func() {}, false
	}
	if !acquired {
		return func() {}, false
	}
	return func() {
		releaseCtx, releaseCancel := context.WithTimeout(context.Background(), summaryRedisLeaseOpTimeout())
		defer releaseCancel()
		if err := s.redisClient.Eval(releaseCtx, summaryRedisLeaseReleaseScript, []string{key}, token).Err(); err != nil {
			s.log().Warn("summary extraction remote lease release failed", "userId", userID, "error", err)
		}
	}, true
}

func newSummaryLeaseToken() string {
	var bytes [16]byte
	if _, err := rand.Read(bytes[:]); err != nil {
		return fmt.Sprintf("%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(bytes[:])
}

func summaryRedisLeaseTTL() time.Duration {
	ttl := envDurationWithDefault("SUMMARY_REDIS_LEASE_TTL_SECONDS", defaultSummaryRedisLeaseTTL)
	if ttl <= 0 {
		return defaultSummaryRedisLeaseTTL
	}
	return ttl
}

func summaryRedisLeaseOpTimeout() time.Duration {
	timeout := envDurationWithDefault("REDIS_SUMMARY_LEASE_TIMEOUT_SECONDS", defaultSummaryRedisLeaseOpTimeout)
	if timeout <= 0 {
		return defaultSummaryRedisLeaseOpTimeout
	}
	return timeout
}

func (s *SummaryService) keepPending(ctx context.Context, userID string, snapshot *SessionSnapshot) {
	if s == nil || s.store == nil || snapshot == nil {
		return
	}
	if snapshot.PendingMemory {
		written, err := s.store.SetUserMemoryPendingIfCurrent(
			ctx,
			userID,
			true,
			snapshot.RoundTotal,
			snapshot.UpdatedAt,
			snapshot.SessionGeneration,
		)
		if err != nil {
			s.log().Warn("keep memory pending failed", "userId", userID, "error", err)
			return
		}
		if !written {
			s.log().Info("keep memory pending skipped: snapshot is stale", "userId", userID, "roundTotal", snapshot.RoundTotal)
		}
	}
}

type extractedMemoryDocument struct {
	MemoryDocument string
	Model          string
	PromptChars    int
	UserChars      int
	Usage          bailianModelUsage
}

func (s *SummaryService) extractSummary(ctx context.Context, oldMemoryDocument string, dialogueText string) (extractedMemoryDocument, error) {
	prompt, err := s.prompts.SummaryPrompt()
	if err != nil {
		return extractedMemoryDocument{}, err
	}

	userContent := buildSummaryExtractionUserContent(oldMemoryDocument, dialogueText)
	model := summaryExtractionModelName()
	promptChars := utf8.RuneCountInString(prompt)
	userContentChars := utf8.RuneCountInString(userContent)
	s.log().Info("memory document extraction started",
		"model", model,
		"prompt_chars", promptChars,
		"user_content_chars", userContentChars,
	)

	response, err := s.bailian.OpenCompletion(ctx, map[string]any{
		"model":           model,
		"stream":          false,
		"temperature":     unifiedModelTemperature,
		"enable_thinking": false,
		"messages": []BailianMessage{
			{Role: "system", Content: prompt},
			{Role: "user", Content: userContent},
		},
	})
	if err != nil {
		return extractedMemoryDocument{}, err
	}
	defer response.Body.Close()

	if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
		_, readErr := readLimitedResponseBody(response.Body, bailianBodyPreviewLimit)
		if readErr != nil && !errors.Is(readErr, errResponseBodyTooLarge) {
			return extractedMemoryDocument{}, readErr
		}
		return extractedMemoryDocument{}, fmt.Errorf("SUMMARY_EXTRACT_HTTP_%d", response.StatusCode)
	}

	body, readErr := readLimitedResponseBody(response.Body, summaryResponseLimit)
	if readErr != nil {
		return extractedMemoryDocument{}, fmt.Errorf("SUMMARY_EXTRACT_RESPONSE_TOO_LARGE")
	}
	payload := map[string]any{}
	if err := json.Unmarshal(body, &payload); err != nil {
		return extractedMemoryDocument{}, err
	}
	var modelUsage bailianModelUsage
	if usage, ok := parseBailianUsagePayload(payload["usage"]); ok {
		modelUsage = usage
		s.bailian.ObserveUsage(usage)
		s.log().Info("memory document model usage", appendBailianUsageLogAttrs([]any{}, usage)...)
	}
	choices, _ := payload["choices"].([]any)
	if len(choices) == 0 {
		return extractedMemoryDocument{}, fmt.Errorf("SUMMARY_EXTRACT_EMPTY")
	}
	choice, _ := choices[0].(map[string]any)
	message, _ := choice["message"].(map[string]any)
	content := strings.TrimSpace(asString(message["content"]))
	if content == "" {
		return extractedMemoryDocument{}, fmt.Errorf("SUMMARY_EXTRACT_EMPTY")
	}
	result, err := normalizeMemoryDocumentExtraction(content, oldMemoryDocument)
	if err != nil {
		return extractedMemoryDocument{}, err
	}
	result.Model = model
	result.PromptChars = promptChars
	result.UserChars = userContentChars
	result.Usage = modelUsage
	return result, nil
}

func buildSummaryExtractionUserContent(oldMemoryDocument string, dialogueText string) string {
	oldMemory := strings.TrimSpace(oldMemoryDocument)
	if oldMemory == "" {
		oldMemory = "暂无记忆摘要"
	}
	return strings.TrimSpace("[已有记忆摘要]\n" + oldMemory + "\n\n[最近对话]\n" + strings.TrimSpace(dialogueText))
}

func normalizeMemoryDocumentExtraction(content string, _ string) (extractedMemoryDocument, error) {
	text := strings.TrimSpace(stripSummaryTextEnvelope(content))
	if text == "" {
		return extractedMemoryDocument{}, fmt.Errorf("SUMMARY_EXTRACT_EMPTY_FIELDS")
	}
	return extractedMemoryDocument{MemoryDocument: text}, nil
}

func stripSummaryTextEnvelope(content string) string {
	text := strings.TrimSpace(content)
	if strings.HasPrefix(text, "```") {
		lines := strings.Split(text, "\n")
		if len(lines) >= 2 {
			if strings.HasPrefix(strings.TrimSpace(lines[0]), "```") {
				lines = lines[1:]
			}
			if len(lines) > 0 && strings.HasPrefix(strings.TrimSpace(lines[len(lines)-1]), "```") {
				lines = lines[:len(lines)-1]
			}
			text = strings.TrimSpace(strings.Join(lines, "\n"))
		}
	}
	return text
}

func buildDialogueText(rounds []SessionRound) string {
	parts := make([]string, 0, len(rounds))
	for _, round := range rounds {
		contextLines := []string{}
		if timestamp := FormatShanghaiUnixMilliToSecond(nil, round.CreatedAt); timestamp != "" {
			contextLines = append(contextLines, "time: "+timestamp+"（Asia/Shanghai）")
		}
		if region := strings.TrimSpace(round.Region); region != "" && region != "未知" {
			reliability := strings.TrimSpace(string(round.RegionReliability))
			if reliability == "" {
				reliability = string(RegionUnreliable)
			}
			contextLines = append(contextLines, "region: "+region+"; reliability: "+reliability)
		}
		prefix := ""
		if len(contextLines) > 0 {
			prefix = strings.Join(contextLines, "\n") + "\n"
		}
		parts = append(parts, prefix+"user: "+round.User+"\nassistant: "+round.Assistant)
	}
	return strings.TrimSpace(strings.Join(parts, "\n\n"))
}

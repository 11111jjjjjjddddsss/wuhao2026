package app

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"sort"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

type summaryStore interface {
	WriteUserMemoryDocumentIfCurrent(ctx context.Context, userID string, memoryDocument string, expectedRoundTotal int) (bool, error)
	SetUserMemoryPending(ctx context.Context, userID string, pending bool) error
}

type SummaryService struct {
	store   summaryStore
	prompts *PromptLoader
	bailian *BailianClient
	logger  *slog.Logger
	running sync.Map
}

const summaryResponseLimit = 64 * 1024

var summaryExtractionTimeout = 60 * time.Second

func NewSummaryService(store *Store, prompts *PromptLoader, bailian *BailianClient, logger *slog.Logger) *SummaryService {
	if logger == nil {
		logger = slog.Default()
	}
	return &SummaryService{
		store:   store,
		prompts: prompts,
		bailian: bailian,
		logger:  logger,
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

	written, err := s.store.WriteUserMemoryDocumentIfCurrent(ctx, userID, nextMemory.MemoryDocument, snapshot.RoundTotal)
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

func (s *SummaryService) keepPending(ctx context.Context, userID string, snapshot *SessionSnapshot) {
	if s == nil || s.store == nil || snapshot == nil {
		return
	}
	if snapshot.PendingMemory {
		_ = s.store.SetUserMemoryPending(ctx, userID, true)
	}
}

type extractedMemoryDocument struct {
	MemoryDocument string
}

type summaryExtractionPayload struct {
	ShortTermMemory *string
	LongTermMemory  *string
	UserProfile     *string
	AgriMemory      *string
}

func (s *SummaryService) extractSummary(ctx context.Context, oldMemoryDocument string, dialogueText string) (extractedMemoryDocument, error) {
	prompt, err := s.prompts.SummaryPrompt()
	if err != nil {
		return extractedMemoryDocument{}, err
	}

	userContent := buildSummaryExtractionUserContent(oldMemoryDocument, dialogueText)
	model := summaryExtractionModelName()
	s.log().Info("memory document extraction started",
		"model", model,
		"prompt_chars", utf8.RuneCountInString(prompt),
		"user_content_chars", utf8.RuneCountInString(userContent),
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
	if usage, ok := parseBailianUsagePayload(payload["usage"]); ok {
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
	return normalizeMemoryDocumentExtraction(content, oldMemoryDocument)
}

func buildSummaryExtractionUserContent(oldMemoryDocument string, dialogueText string) string {
	oldMemory := strings.TrimSpace(oldMemoryDocument)
	if oldMemory == "" {
		oldMemory = "暂无记忆文档"
	}
	return strings.TrimSpace("[已有记忆文档]\n" + oldMemory + "\n\n[最近对话]\n" + strings.TrimSpace(dialogueText))
}

func normalizeMemoryDocumentExtraction(content string, oldMemoryDocument string) (extractedMemoryDocument, error) {
	decoded := parseSummaryExtractionSections(content)
	if decoded.ShortTermMemory == nil &&
		decoded.LongTermMemory == nil &&
		decoded.UserProfile == nil &&
		decoded.AgriMemory == nil {
		return extractedMemoryDocument{}, fmt.Errorf("SUMMARY_EXTRACT_EMPTY_FIELDS")
	}

	previous := splitMemoryDocument(oldMemoryDocument)
	shortTerm := summaryFieldValue(decoded.ShortTermMemory, stringPtrValue(previous.ShortTermMemory), "暂无短期承接可沉淀")
	longTerm := summaryFieldValue(decoded.LongTermMemory, stringPtrValue(previous.LongTermMemory), "暂无稳定长期背景可沉淀")
	userProfile := summaryFieldValue(decoded.UserProfile, stringPtrValue(previous.UserProfile), "暂无稳定用户画像可沉淀")
	agriMemory := summaryFieldValue(decoded.AgriMemory, stringPtrValue(previous.AgriMemory), "暂无农业重点事件可沉淀")

	return extractedMemoryDocument{
		MemoryDocument: combineMemoryDocument(shortTerm, longTerm, userProfile, agriMemory),
	}, nil
}

func summaryFieldValue(value *string, oldValue string, emptyFallback string) string {
	if value == nil {
		return firstNonBlank(oldValue, emptyFallback)
	}
	return firstNonBlank(*value, oldValue, emptyFallback)
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

type summarySectionMatch struct {
	key        string
	start      int
	valueStart int
}

func parseSummaryExtractionSections(content string) summaryExtractionPayload {
	text := stripSummaryTextEnvelope(content)
	matches := []summarySectionMatch{}
	for _, label := range []struct {
		key      string
		variants []string
	}{
		{key: "short", variants: []string{"短期承接：", "短期承接:", "短期记忆：", "短期记忆:"}},
		{key: "long", variants: []string{"长期背景：", "长期背景:", "长期通用记忆：", "长期通用记忆:"}},
		{key: "profile", variants: []string{"用户画像：", "用户画像:"}},
		{key: "agri", variants: []string{"农业重点事件：", "农业重点事件:", "农业相关重点事件记忆：", "农业相关重点事件记忆:"}},
	} {
		bestStart := -1
		bestLen := 0
		for _, variant := range label.variants {
			idx := strings.Index(text, variant)
			if idx < 0 {
				continue
			}
			if bestStart < 0 || idx < bestStart {
				bestStart = idx
				bestLen = len(variant)
			}
		}
		if bestStart >= 0 {
			matches = append(matches, summarySectionMatch{
				key:        label.key,
				start:      bestStart,
				valueStart: bestStart + bestLen,
			})
		}
	}
	sort.Slice(matches, func(i, j int) bool {
		return matches[i].start < matches[j].start
	})

	result := summaryExtractionPayload{}
	seen := map[string]bool{}
	for idx, match := range matches {
		if seen[match.key] {
			continue
		}
		seen[match.key] = true
		valueEnd := len(text)
		if idx+1 < len(matches) {
			valueEnd = matches[idx+1].start
		}
		value := cleanSummarySectionText(text[match.valueStart:valueEnd])
		switch match.key {
		case "short":
			result.ShortTermMemory = &value
		case "long":
			result.LongTermMemory = &value
		case "profile":
			result.UserProfile = &value
		case "agri":
			result.AgriMemory = &value
		}
	}
	return result
}

func cleanSummarySectionText(value string) string {
	text := strings.TrimSpace(value)
	lines := strings.Split(text, "\n")
	for idx := range lines {
		lines[idx] = strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(lines[idx]), "#"))
	}
	return strings.TrimSpace(strings.Join(lines, "\n"))
}

func combineMemoryDocument(shortTerm string, longTerm string, userProfile string, agriMemory string) string {
	return strings.TrimSpace("短期承接：" + strings.TrimSpace(shortTerm) +
		"\n长期背景：" + strings.TrimSpace(longTerm) +
		"\n用户画像：" + strings.TrimSpace(userProfile) +
		"\n农业重点事件：" + strings.TrimSpace(agriMemory))
}

func splitMemoryDocument(memoryDocument string) summaryExtractionPayload {
	text := strings.TrimSpace(memoryDocument)
	result := summaryExtractionPayload{}
	labels := []struct {
		prefixes []string
		assign   func(string)
	}{
		{[]string{"短期承接：", "短期记忆："}, func(value string) { result.ShortTermMemory = &value }},
		{[]string{"长期背景：", "长期通用记忆："}, func(value string) { result.LongTermMemory = &value }},
		{[]string{"用户画像："}, func(value string) { result.UserProfile = &value }},
		{[]string{"农业重点事件：", "农业相关重点事件记忆："}, func(value string) { result.AgriMemory = &value }},
	}
	if text != "" {
		found := false
		for idx, label := range labels {
			start, prefix := firstLabelIndex(text, label.prefixes)
			if start < 0 {
				continue
			}
			found = true
			valueStart := start + len(prefix)
			valueEnd := len(text)
			for nextIdx := idx + 1; nextIdx < len(labels); nextIdx++ {
				if nextStart, _ := firstLabelIndex(text[valueStart:], labels[nextIdx].prefixes); nextStart >= 0 {
					valueEnd = valueStart + nextStart
					break
				}
			}
			label.assign(strings.TrimSpace(text[valueStart:valueEnd]))
		}
		if !found {
			result.ShortTermMemory = &text
		}
	}

	return result
}

func firstLabelIndex(text string, prefixes []string) (int, string) {
	bestStart := -1
	bestPrefix := ""
	for _, prefix := range prefixes {
		start := strings.Index(text, prefix)
		if start < 0 {
			continue
		}
		if bestStart < 0 || start < bestStart {
			bestStart = start
			bestPrefix = prefix
		}
	}
	return bestStart, bestPrefix
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

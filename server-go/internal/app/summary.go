package app

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

type summaryStore interface {
	WriteUserBSummaryIfCurrent(ctx context.Context, userID string, summary string, expectedRoundTotal int) (bool, error)
	WriteUserCSummaryIfCurrent(ctx context.Context, userID string, summary string, expectedRoundTotal int) (bool, error)
	SetUserSummaryPending(ctx context.Context, userID string, layer SummaryLayer, pending bool) error
	GetRecentSessionRoundsForSummary(ctx context.Context, userID string, limit int) ([]SessionRound, error)
}

type SummaryService struct {
	store   summaryStore
	prompts *PromptLoader
	bailian *BailianClient
	logger  *slog.Logger
	running sync.Map
}

const (
	cSummaryEveryRounds   = 20
	cSummaryArchiveRounds = 20
	summaryResponseLimit  = 64 * 1024
)

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

func GetSummaryIntervals(tier Tier) (int, int) {
	if tier == TierPro {
		return 9, cSummaryEveryRounds
	}
	return 6, cSummaryEveryRounds
}

func (s *SummaryService) ProcessSessionSummaries(userID string, snapshot *SessionSnapshot) {
	ctx := context.Background()
	s.processLayer(ctx, SummaryLayerB, userID, snapshot)
	s.processLayer(ctx, SummaryLayerC, userID, snapshot)
}

func (s *SummaryService) processLayer(ctx context.Context, layer SummaryLayer, userID string, snapshot *SessionSnapshot) {
	pending := snapshot.PendingRetryB
	oldSummary := snapshot.BSummary
	if layer == SummaryLayerC {
		pending = snapshot.PendingRetryC
		oldSummary = snapshot.CSummary
	}
	if !pending {
		return
	}
	if !s.tryStartLayer(userID, layer) {
		s.log().Info("summary extraction skipped: already running", "userId", userID, "layer", layer, "roundTotal", snapshot.RoundTotal)
		return
	}
	defer s.finishLayer(userID, layer)
	if !s.bailian.HasKeyConfigured() {
		s.log().Warn("summary extraction skipped: model backend unavailable", "userId", userID, "layer", layer)
		return
	}

	dialogueRounds := snapshot.ARoundsFull
	if layer == SummaryLayerC && s.store != nil {
		archivedRounds, err := s.store.GetRecentSessionRoundsForSummary(ctx, userID, cSummaryArchiveRounds)
		if err != nil {
			_ = s.store.SetUserSummaryPending(ctx, userID, layer, true)
			s.log().Error("C summary archive load failed", "userId", userID, "roundTotal", snapshot.RoundTotal, "error", err)
			return
		}
		if len(archivedRounds) < cSummaryArchiveRounds {
			_ = s.store.SetUserSummaryPending(ctx, userID, layer, true)
			s.log().Info("C summary extraction skipped: insufficient archived rounds", "userId", userID, "roundTotal", snapshot.RoundTotal, "rounds", len(archivedRounds), "required", cSummaryArchiveRounds)
			return
		}
		dialogueRounds = archivedRounds
	}

	dialogueText := buildDialogueText(dialogueRounds)
	if dialogueText == "" {
		s.log().Warn("summary extraction skipped: empty dialogue", "userId", userID, "layer", layer)
		return
	}

	extractCtx, cancelExtract := context.WithTimeout(ctx, summaryExtractionTimeout)
	nextSummary, err := s.extractSummary(extractCtx, layer, oldSummary, dialogueText)
	cancelExtract()
	if err != nil {
		_ = s.store.SetUserSummaryPending(ctx, userID, layer, true)
		s.log().Error("summary extraction failed", "userId", userID, "layer", layer, "error", err)
		return
	}

	if layer == SummaryLayerB {
		written, err := s.store.WriteUserBSummaryIfCurrent(ctx, userID, nextSummary, snapshot.RoundTotal)
		if err != nil {
			_ = s.store.SetUserSummaryPending(ctx, userID, layer, true)
			s.log().Error("write B summary failed", "userId", userID, "error", err)
			return
		}
		if !written {
			s.log().Info("B summary write skipped: snapshot is stale", "userId", userID, "roundTotal", snapshot.RoundTotal)
			return
		}
		snapshot.BSummary = nextSummary
		snapshot.PendingRetryB = false
	} else {
		written, err := s.store.WriteUserCSummaryIfCurrent(ctx, userID, nextSummary, snapshot.RoundTotal)
		if err != nil {
			_ = s.store.SetUserSummaryPending(ctx, userID, layer, true)
			s.log().Error("write C summary failed", "userId", userID, "error", err)
			return
		}
		if !written {
			s.log().Info("C summary write skipped: snapshot is stale", "userId", userID, "roundTotal", snapshot.RoundTotal)
			return
		}
		snapshot.CSummary = nextSummary
		snapshot.PendingRetryC = false
	}

	s.log().Info("summary extraction success", "userId", userID, "layer", layer, "chars", len(nextSummary))
}

func (s *SummaryService) tryStartLayer(userID string, layer SummaryLayer) bool {
	key := userID + ":" + string(layer)
	_, loaded := s.running.LoadOrStore(key, struct{}{})
	return !loaded
}

func (s *SummaryService) finishLayer(userID string, layer SummaryLayer) {
	key := userID + ":" + string(layer)
	s.running.Delete(key)
}

func (s *SummaryService) extractSummary(ctx context.Context, layer SummaryLayer, oldSummary string, dialogueText string) (string, error) {
	prompt, err := s.prompts.SummaryPrompt(layer)
	if err != nil {
		return "", err
	}

	userContent := "[对话]\n" + dialogueText
	if strings.TrimSpace(oldSummary) != "" {
		userContent = "[历史摘要]\n" + strings.TrimSpace(oldSummary) + "\n\n[对话]\n" + dialogueText
	}
	s.log().Info("summary extraction started",
		"layer", layer,
		"model", summaryExtractionModelForLayer(layer),
		"prompt_chars", utf8.RuneCountInString(prompt),
		"user_content_chars", utf8.RuneCountInString(userContent),
	)

	response, err := s.bailian.OpenCompletion(ctx, map[string]any{
		"model":           summaryExtractionModelForLayer(layer),
		"stream":          false,
		"temperature":     unifiedModelTemperature,
		"enable_thinking": false,
		"messages": []BailianMessage{
			{Role: "system", Content: prompt},
			{Role: "user", Content: userContent},
		},
	})
	if err != nil {
		return "", err
	}
	defer response.Body.Close()

	if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
		_, readErr := readLimitedResponseBody(response.Body, bailianBodyPreviewLimit)
		if readErr != nil && !errors.Is(readErr, errResponseBodyTooLarge) {
			return "", readErr
		}
		return "", fmt.Errorf("%s_EXTRACT_HTTP_%d", layer, response.StatusCode)
	}

	body, readErr := readLimitedResponseBody(response.Body, summaryResponseLimit)
	if readErr != nil {
		return "", fmt.Errorf("%s_EXTRACT_RESPONSE_TOO_LARGE", layer)
	}
	payload := map[string]any{}
	if err := json.Unmarshal(body, &payload); err != nil {
		return "", err
	}
	if usage, ok := parseBailianUsagePayload(payload["usage"]); ok {
		logAttrs := []any{"layer", layer}
		logAttrs = appendBailianUsageLogAttrs(logAttrs, usage)
		s.log().Info("summary model usage", logAttrs...)
	}
	choices, _ := payload["choices"].([]any)
	if len(choices) == 0 {
		return "", fmt.Errorf("%s_EXTRACT_EMPTY", layer)
	}
	choice, _ := choices[0].(map[string]any)
	message, _ := choice["message"].(map[string]any)
	content := strings.TrimSpace(asString(message["content"]))
	if content == "" {
		return "", fmt.Errorf("%s_EXTRACT_EMPTY", layer)
	}
	return content, nil
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

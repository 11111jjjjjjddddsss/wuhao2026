package app

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strings"
)

type SummaryService struct {
	store   *Store
	prompts *PromptLoader
	bailian *BailianClient
	logger  *slog.Logger
}

func NewSummaryService(store *Store, prompts *PromptLoader, bailian *BailianClient, logger *slog.Logger) *SummaryService {
	return &SummaryService{
		store:   store,
		prompts: prompts,
		bailian: bailian,
		logger:  logger,
	}
}

func GetSummaryIntervals(tier Tier) (int, int) {
	if tier == TierPro {
		return 9, 25
	}
	return 6, 25
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
	if !s.bailian.HasKeyConfigured() {
		s.logger.Warn("summary extraction skipped: model backend unavailable", "userId", userID, "layer", layer)
		return
	}

	dialogueText := buildDialogueText(snapshot.ARoundsFull)
	if dialogueText == "" {
		s.logger.Warn("summary extraction skipped: empty dialogue", "userId", userID, "layer", layer)
		return
	}

	nextSummary, err := s.extractSummary(ctx, layer, oldSummary, dialogueText)
	if err != nil {
		_ = s.store.SetUserSummaryPending(ctx, userID, layer, true)
		s.logger.Error("summary extraction failed", "userId", userID, "layer", layer, "error", err)
		return
	}

	if layer == SummaryLayerB {
		if err := s.store.WriteUserBSummary(ctx, userID, nextSummary); err != nil {
			_ = s.store.SetUserSummaryPending(ctx, userID, layer, true)
			s.logger.Error("write B summary failed", "userId", userID, "error", err)
			return
		}
		snapshot.BSummary = nextSummary
		snapshot.PendingRetryB = false
	} else {
		if err := s.store.WriteUserCSummary(ctx, userID, nextSummary); err != nil {
			_ = s.store.SetUserSummaryPending(ctx, userID, layer, true)
			s.logger.Error("write C summary failed", "userId", userID, "error", err)
			return
		}
		snapshot.CSummary = nextSummary
		snapshot.PendingRetryC = false
	}

	s.logger.Info("summary extraction success", "userId", userID, "layer", layer, "chars", len(nextSummary))
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

	response, err := s.bailian.OpenCompletion(ctx, map[string]any{
		"model":  "qwen-flash",
		"stream": false,
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
		body, _ := io.ReadAll(response.Body)
		return "", fmt.Errorf("%s_EXTRACT_HTTP_%d:%s", layer, response.StatusCode, strings.TrimSpace(string(body)))
	}

	payload := map[string]any{}
	if err := json.NewDecoder(response.Body).Decode(&payload); err != nil {
		return "", err
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
		parts = append(parts, "user: "+round.User+"\nassistant: "+round.Assistant)
	}
	return strings.TrimSpace(strings.Join(parts, "\n\n"))
}

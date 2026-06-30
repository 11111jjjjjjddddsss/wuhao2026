package app

import (
	"context"
	"net/http"
	"strings"
)

type modelCallContextKey struct{}

type modelCallContext struct {
	Chain           string
	UserID          string
	ClientMsgID     string
	RequestID       string
	Tier            string
	ImageCount      int
	PromptHasImages bool
	ForcedSearch    bool
	SearchStrategy  string
	ReasoningEffort string
	ThinkingEnabled bool
	ThinkingBudget  int
}

func withModelCallContext(ctx context.Context, meta modelCallContext) context.Context {
	return context.WithValue(ctx, modelCallContextKey{}, meta)
}

func modelCallContextFrom(ctx context.Context) (modelCallContext, bool) {
	meta, ok := ctx.Value(modelCallContextKey{}).(modelCallContext)
	return meta, ok
}

func (s *Server) recordGPTRelayKeyAttempt(ctx context.Context, attempt gptRelayAttemptRecord) {
	meta, _ := modelCallContextFrom(ctx)
	if meta.Chain == "" {
		meta.Chain = "main_chat"
	}
	s.insertModelCallRecordAsync(ModelCallRecordInput{
		RecordType:             "key_attempt",
		Chain:                  meta.Chain,
		UserID:                 meta.UserID,
		ClientMsgID:            meta.ClientMsgID,
		RequestID:              meta.RequestID,
		Provider:               gptRelayProvider,
		ProviderLabel:          attempt.ProviderLabel,
		ProviderSlot:           attempt.ProviderSlot,
		KeySlot:                attempt.KeySlot,
		Model:                  gptRelayModelName(),
		Status:                 attempt.Status,
		ErrorKind:              attempt.ErrorKind,
		HTTPStatus:             attempt.HTTPStatus,
		Attempt:                attempt.Attempt,
		MaxAttempts:            attempt.MaxAttempts,
		Tier:                   meta.Tier,
		ImageCount:             meta.ImageCount,
		PromptHasImages:        meta.PromptHasImages,
		ForcedSearch:           meta.ForcedSearch,
		SearchStrategy:         firstNonEmpty(meta.SearchStrategy, "responses_auto_low"),
		ReasoningEffort:        firstNonEmpty(meta.ReasoningEffort, gptRelayReasoningEffort()),
		ThinkingEnabled:        meta.ThinkingEnabled,
		ThinkingBudget:         meta.ThinkingBudget,
		OpenMs:                 attempt.OpenMs,
		RequestToOpenMs:        -1,
		FirstVisibleMs:         -1,
		UpstreamFirstVisibleMs: -1,
		TotalMs:                attempt.OpenMs,
	})
}

func (s *Server) insertModelCallRecordAsync(record ModelCallRecordInput) {
	if s == nil || s.store == nil {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), modelCallRecordInsertTimeout)
		defer cancel()
		if err := s.store.InsertModelCallRecord(ctx, record); err != nil {
			s.logger.Warn("insert model call record failed",
				"error", err,
				"record_type", record.RecordType,
				"provider", record.Provider,
				"status", record.Status,
			)
		}
	}()
}

func modelCallProviderMetadata(provider string, response *http.Response) (string, string, string) {
	if isGPTRelayProvider(provider) && response != nil {
		return response.Header.Get(gptRelayHeaderProviderLabel),
			response.Header.Get(gptRelayHeaderProviderSlot),
			response.Header.Get(gptRelayHeaderKeySlot)
	}
	if provider == "bailian" {
		return "千问/百炼", "bailian", ""
	}
	if provider == "" {
		return "", "", ""
	}
	return provider, provider, ""
}

func modelCallModelForProvider(provider string) string {
	if isGPTRelayProvider(provider) {
		return gptRelayModelName()
	}
	if provider == "bailian" {
		return strings.TrimSpace(mainChatModel)
	}
	return ""
}

func modelCallStreamStatus(sendDone bool, doneReceived bool, clientDisconnected bool, timeoutKind string, hasVisibleText bool) string {
	if sendDone {
		return "ok"
	}
	if clientDisconnected {
		return "client_disconnected"
	}
	if strings.TrimSpace(timeoutKind) != "" {
		return timeoutKind
	}
	if doneReceived && !hasVisibleText {
		return "empty_reply"
	}
	return "incomplete"
}

func modelCallSearchCount(provider string, usage bailianModelUsage, gptRelaySearchCount int) int {
	if isGPTRelayProvider(provider) && gptRelaySearchCount > 0 {
		return gptRelaySearchCount
	}
	return usage.searchCount()
}

func upstreamOpenErrorKind(err error) string {
	if openErr, ok := err.(*upstreamStreamOpenError); ok {
		return openErr.Kind
	}
	if err == nil {
		return ""
	}
	return "request"
}

func upstreamOpenErrorStatus(err error) int {
	if openErr, ok := err.(*upstreamStreamOpenError); ok {
		return openErr.StatusCode
	}
	return 0
}

package app

import (
	"context"
	"fmt"
	"net/http"
	"strings"
	"unicode/utf8"
)

type memoryDocumentProbeRequest struct {
	OldMemoryDocument string `json:"old_memory_document"`
	DialogueText      string `json:"dialogue_text"`
}

type memoryDocumentProbeResponse struct {
	Status               string `json:"status"`
	Model                string `json:"model"`
	MemoryDocument       string `json:"memory_document"`
	MemoryChars          int    `json:"memory_chars"`
	PromptChars          int    `json:"prompt_chars"`
	UserContentChars     int    `json:"user_content_chars"`
	ModelInputTokens     int    `json:"model_input_tokens,omitempty"`
	ModelOutputTokens    int    `json:"model_output_tokens,omitempty"`
	ModelTotalTokens     int    `json:"model_total_tokens,omitempty"`
	ModelReasoningTokens int    `json:"model_reasoning_tokens,omitempty"`
}

func (s *SummaryService) ProbeMemoryDocument(ctx context.Context, oldMemoryDocument string, dialogueText string) (memoryDocumentProbeResponse, error) {
	if s == nil || s.bailian == nil || !s.bailian.HasKeyConfigured() {
		return memoryDocumentProbeResponse{}, fmt.Errorf("DASHSCOPE_API_KEY(S) is missing")
	}
	if strings.TrimSpace(dialogueText) == "" {
		oldMemoryDocument, dialogueText = defaultMemoryDocumentProbeSample()
	}

	probeCtx, cancel := context.WithTimeout(ctx, summaryExtractionTimeout)
	defer cancel()

	result, err := s.extractSummary(probeCtx, oldMemoryDocument, dialogueText)
	if err != nil {
		return memoryDocumentProbeResponse{}, err
	}
	response := memoryDocumentProbeResponse{
		Status:           "ok",
		Model:            result.Model,
		MemoryDocument:   result.MemoryDocument,
		MemoryChars:      utf8.RuneCountInString(result.MemoryDocument),
		PromptChars:      result.PromptChars,
		UserContentChars: result.UserChars,
	}
	response.ModelInputTokens = result.Usage.normalizedInputTokens()
	response.ModelOutputTokens = result.Usage.normalizedOutputTokens()
	response.ModelTotalTokens = result.Usage.normalizedTotalTokens()
	response.ModelReasoningTokens = result.Usage.ReasoningTokens
	return response, nil
}

func defaultMemoryDocumentProbeSample() (string, string) {
	oldMemory := strings.TrimSpace(`短期承接：用户上次在核对番茄叶片发黄问题，担心用药和水肥判断不准。
长期背景：用户要求回答尽量通俗，关键用量和面积按原话保留，不要替他换算。
用户画像：用户用中文沟通，偏好直接、稳妥、少术语的回答。
农业重点事件：番茄叶片发黄仍需继续核对新叶老叶、根系、水肥、病虫害和近期天气。`)

	dialogue := strings.TrimSpace(`time: 2026-06-12 08:20:00（Asia/Shanghai）
region: 河南省周口市; reliability: user
user: 我在河南周口，家里有两亩露地番茄，不是专业搞这个，就是家里种点地。最近下过雨，老叶发黄，新叶还行。
assistant: 先别急着下结论，需要看黄叶位置、根系、土壤湿度和叶片有没有斑点。

time: 2026-06-12 08:24:00（Asia/Shanghai）
user: 我爸说农资店看了像早疫病，但没做检测。我现在只喷了一遍代森锰锌，兑水量按包装写的，具体多少我回头拍给你。
assistant: 可以先按待确认处理，别把农资店判断当确诊；后续最好补叶片照片、茎基部和根系情况。

time: 2026-06-12 08:28:00（Asia/Shanghai）
user: 你记住，我不懂那些专业词，后面说简单点。药量别帮我乱折算，我说多少就记多少。`)

	return oldMemory, dialogue
}

func (s *Server) handleProbeMemoryDocument(w http.ResponseWriter, r *http.Request) {
	if !validateInternalJobSecret(r) {
		s.writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	if !s.consumeInternalProbeRateLimit(w, r, "memory_document_probe") {
		return
	}

	input := memoryDocumentProbeRequest{}
	if r.ContentLength != 0 {
		if err := decodeJSONBodyLimited(r, &input, 16*1024); err != nil {
			s.writeJSONDecodeError(w, err)
			return
		}
	}

	result, err := s.summary.ProbeMemoryDocument(r.Context(), input.OldMemoryDocument, input.DialogueText)
	if err != nil {
		s.logger.Error("probe memory document failed", "error", err)
		s.recordAdminAuditLog(r, "daily_agri_job_secret", "internal.memory_document.probe", "session_ab", "", "", false, http.StatusBadGateway, map[string]any{
			"error_code": "probe_failed",
		})
		s.writeJSON(w, http.StatusBadGateway, map[string]any{
			"status": "failed",
			"error":  "probe_failed",
		})
		return
	}

	s.recordAdminAuditLog(r, "daily_agri_job_secret", "internal.memory_document.probe", "session_ab", "", "", true, http.StatusOK, map[string]any{
		"memory_chars":       result.MemoryChars,
		"model_total_tokens": result.ModelTotalTokens,
	})
	s.writeJSON(w, http.StatusOK, result)
}

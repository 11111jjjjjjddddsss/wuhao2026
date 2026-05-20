package app

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"sync/atomic"
)

type BailianClient struct {
	httpClient *http.Client
	keyCursor  uint64
}

const unifiedModelTemperature = 0.8

func NewBailianClient() *BailianClient {
	return &BailianClient{
		httpClient: &http.Client{},
	}
}

func (c *BailianClient) HasKeyConfigured() bool {
	return len(c.keys()) > 0
}

func (c *BailianClient) OpenStream(ctx context.Context, messages []BailianMessage) (*http.Response, error) {
	body := map[string]any{
		"model":       "qwen3.5-plus",
		"stream":      true,
		"temperature": unifiedModelTemperature,
		"extra_body": map[string]any{
			"enable_thinking": false,
			"enable_search":   true,
			"search_options": map[string]any{
				"search_strategy": "turbo",
				"forced_search":   false,
			},
		},
		"messages": messages,
	}
	return c.doJSONRequest(ctx, "text/event-stream", body)
}

func (c *BailianClient) OpenCompletion(ctx context.Context, body map[string]any) (*http.Response, error) {
	return c.doJSONRequest(ctx, "application/json", body)
}

func (c *BailianClient) GenerateDailyAgriCard(ctx context.Context, messages []BailianMessage) (string, []DailyAgriSearchSource, error) {
	apiKey, err := c.pickNextKey()
	if err != nil {
		return "", nil, err
	}
	body := map[string]any{
		"model": dailyAgriCardModel,
		"input": map[string]any{
			"messages": messages,
		},
		"parameters": map[string]any{
			"result_format":   "message",
			"temperature":     unifiedModelTemperature,
			"enable_thinking": false,
			"enable_search":   true,
			"search_options": map[string]any{
				"search_strategy": dailyAgriSearchStrategy,
				"forced_search":   true,
				"enable_source":   true,
				"freshness":       7,
			},
		},
	}
	payload, err := json.Marshal(body)
	if err != nil {
		return "", nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.buildDashScopeGenerationURL(), bytes.NewReader(payload))
	if err != nil {
		return "", nil, err
	}
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", nil, err
	}
	defer resp.Body.Close()
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", nil, err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", nil, fmt.Errorf("dashscope status %d: %s", resp.StatusCode, strings.TrimSpace(string(bodyBytes)))
	}

	var decoded dashScopeGenerationResponse
	if err := json.Unmarshal(bodyBytes, &decoded); err != nil {
		return "", nil, err
	}
	if decoded.Code != "" {
		return "", nil, fmt.Errorf("dashscope error %s: %s", decoded.Code, decoded.Message)
	}
	if len(decoded.Output.Choices) == 0 {
		return "", nil, fmt.Errorf("dashscope response missing choices")
	}
	content := strings.TrimSpace(decoded.Output.Choices[0].Message.Content)
	if content == "" {
		return "", nil, fmt.Errorf("dashscope response empty content")
	}
	sources := make([]DailyAgriSearchSource, 0, len(decoded.Output.SearchInfo.SearchResults))
	for _, source := range decoded.Output.SearchInfo.SearchResults {
		url := strings.TrimSpace(source.URL)
		if url == "" {
			continue
		}
		sources = append(sources, DailyAgriSearchSource{
			Index:    source.Index,
			Title:    strings.TrimSpace(source.Title),
			URL:      url,
			SiteName: firstNonBlank(source.SiteName, source.Site, source.Host),
		})
	}
	return content, sources, nil
}

func (c *BailianClient) doJSONRequest(ctx context.Context, accept string, body map[string]any) (*http.Response, error) {
	apiKey, err := c.pickNextKey()
	if err != nil {
		return nil, err
	}

	payload, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.buildURL(), bytes.NewReader(payload))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", accept)
	req.Header.Set("Cache-Control", "no-cache")

	return c.httpClient.Do(req)
}

func (c *BailianClient) buildURL() string {
	baseURL := strings.TrimSpace(os.Getenv("BAILIAN_BASE_URL"))
	if baseURL == "" {
		baseURL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
	}
	return strings.TrimRight(baseURL, "/") + "/chat/completions"
}

func (c *BailianClient) buildDashScopeGenerationURL() string {
	baseURL := strings.TrimSpace(os.Getenv("DASHSCOPE_BASE_URL"))
	if baseURL == "" {
		baseURL = "https://dashscope.aliyuncs.com/api/v1"
	}
	return strings.TrimRight(baseURL, "/") + "/services/aigc/text-generation/generation"
}

func (c *BailianClient) pickNextKey() (string, error) {
	keys := c.keys()
	if len(keys) == 0 {
		return "", fmt.Errorf("DASHSCOPE_API_KEY(S) is missing")
	}
	index := atomic.AddUint64(&c.keyCursor, 1) - 1
	return keys[index%uint64(len(keys))], nil
}

func (c *BailianClient) keys() []string {
	result := []string{}
	if single := strings.TrimSpace(os.Getenv("DASHSCOPE_API_KEY")); single != "" {
		result = append(result, single)
	}
	for _, item := range strings.Split(os.Getenv("DASHSCOPE_API_KEYS"), ",") {
		key := strings.TrimSpace(item)
		if key == "" {
			continue
		}
		exists := false
		for _, current := range result {
			if current == key {
				exists = true
				break
			}
		}
		if !exists {
			result = append(result, key)
		}
	}
	return result
}

type dashScopeGenerationResponse struct {
	Code    string `json:"code,omitempty"`
	Message string `json:"message,omitempty"`
	Output  struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
		SearchInfo struct {
			SearchResults []struct {
				Index    int    `json:"index"`
				Title    string `json:"title"`
				URL      string `json:"url"`
				SiteName string `json:"site_name"`
				Site     string `json:"site"`
				Host     string `json:"host"`
			} `json:"search_results"`
		} `json:"search_info"`
	} `json:"output"`
}

func firstNonBlank(values ...string) string {
	for _, value := range values {
		if trimmed := strings.TrimSpace(value); trimmed != "" {
			return trimmed
		}
	}
	return ""
}

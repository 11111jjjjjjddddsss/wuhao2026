package app

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strings"
	"sync/atomic"
)

type BailianClient struct {
	httpClient *http.Client
	keyCursor  uint64
}

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
		"model":  "qwen3.5-plus",
		"stream": true,
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

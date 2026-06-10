package app

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

type PromptLoader struct {
	assetDir string

	mu                 sync.Mutex
	cachedSystemAnchor string
	cachedSummary      string
}

func NewPromptLoader(assetDir string) *PromptLoader {
	return &PromptLoader{
		assetDir: assetDir,
	}
}

func (p *PromptLoader) SystemAnchorPath() string {
	return filepath.Join(p.assetDir, "system_anchor.txt")
}

func (p *PromptLoader) SummaryPromptPath() string {
	return filepath.Join(p.assetDir, "summary_extraction_prompt.txt")
}

func (p *PromptLoader) SystemAnchor() (string, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.cachedSystemAnchor != "" {
		return p.cachedSystemAnchor, nil
	}
	content, err := readPromptFile(p.SystemAnchorPath(), "SYSTEM_ANCHOR")
	if err != nil {
		return "", err
	}
	p.cachedSystemAnchor = content
	return content, nil
}

func (p *PromptLoader) SummaryPrompt() (string, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.cachedSummary != "" {
		return p.cachedSummary, nil
	}
	content, err := readPromptFile(p.SummaryPromptPath(), "SUMMARY_EXTRACTION_PROMPT")
	if err != nil {
		return "", err
	}
	p.cachedSummary = content
	return content, nil
}

func (p *PromptLoader) ProbeSummaryPrompt() PromptProbeResult {
	path := p.SummaryPromptPath()
	content, err := p.SummaryPrompt()
	if err != nil {
		return PromptProbeResult{
			OK:    false,
			Path:  path,
			Error: err.Error(),
		}
	}
	return PromptProbeResult{
		OK:    true,
		Path:  path,
		Chars: len(content),
	}
}

func readPromptFile(path string, label string) (string, error) {
	raw, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("%s_READ_FAILED:%w", label, err)
	}
	content := strings.TrimSpace(string(raw))
	if content == "" {
		return "", fmt.Errorf("%s_EMPTY", label)
	}
	return content, nil
}

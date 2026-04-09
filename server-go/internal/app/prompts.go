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
	cachedSummary      map[SummaryLayer]string
}

func NewPromptLoader(assetDir string) *PromptLoader {
	return &PromptLoader{
		assetDir:      assetDir,
		cachedSummary: map[SummaryLayer]string{},
	}
}

func (p *PromptLoader) SystemAnchorPath() string {
	return filepath.Join(p.assetDir, "system_anchor.txt")
}

func (p *PromptLoader) SummaryPromptPath(layer SummaryLayer) string {
	switch layer {
	case SummaryLayerB:
		return filepath.Join(p.assetDir, "b_extraction_prompt.txt")
	default:
		return filepath.Join(p.assetDir, "c_extraction_prompt.txt")
	}
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

func (p *PromptLoader) SummaryPrompt(layer SummaryLayer) (string, error) {
	p.mu.Lock()
	defer p.mu.Unlock()
	if content, ok := p.cachedSummary[layer]; ok && content != "" {
		return content, nil
	}
	content, err := readPromptFile(p.SummaryPromptPath(layer), string(layer)+"_SUMMARY_PROMPT")
	if err != nil {
		return "", err
	}
	p.cachedSummary[layer] = content
	return content, nil
}

func (p *PromptLoader) ProbeSummaryPrompt(layer SummaryLayer) PromptProbeResult {
	path := p.SummaryPromptPath(layer)
	content, err := p.SummaryPrompt(layer)
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

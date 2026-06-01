package app

import (
	"time"
)

const (
	defaultChatRateLimitWindow        = 60 * time.Second
	defaultChatRateLimitMaxHits       = 20
	defaultChatRateLimitPruneInterval = 5 * time.Minute
)

type rateLimiter interface {
	Consume(key string, now time.Time) (bool, int)
}

type rateLimitConfig struct {
	Window        time.Duration
	MaxHits       int
	PruneInterval time.Duration
}

func normalizeRateLimitConfig(config rateLimitConfig, fallbackWindow time.Duration, fallbackMaxHits int, fallbackPruneInterval time.Duration) rateLimitConfig {
	if config.Window <= 0 {
		config.Window = fallbackWindow
	}
	if config.MaxHits <= 0 {
		config.MaxHits = fallbackMaxHits
	}
	if config.PruneInterval <= 0 {
		config.PruneInterval = fallbackPruneInterval
	}
	return config
}

func resolveChatRateLimitConfig() rateLimitConfig {
	return normalizeRateLimitConfig(rateLimitConfig{
		Window:        envDurationWithDefault("CHAT_RATE_LIMIT_WINDOW_SECONDS", defaultChatRateLimitWindow),
		MaxHits:       envIntWithDefault("CHAT_RATE_LIMIT_MAX_HITS", defaultChatRateLimitMaxHits),
		PruneInterval: envDurationWithDefault("CHAT_RATE_LIMIT_PRUNE_INTERVAL_SECONDS", defaultChatRateLimitPruneInterval),
	}, defaultChatRateLimitWindow, defaultChatRateLimitMaxHits, defaultChatRateLimitPruneInterval)
}

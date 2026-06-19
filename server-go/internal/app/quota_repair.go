package app

import (
	"context"
	"strings"
	"time"
)

const (
	defaultQuotaConsumeRepairInterval  = 30 * time.Second
	defaultQuotaConsumeRepairTimeout   = 10 * time.Second
	defaultQuotaConsumeRepairLease     = 10 * time.Minute
	quotaConsumeRepairBatchLimit       = 20
	quotaConsumeRepairNeedsOpsAttempts = 12
	quotaConsumeRepairTerminalAttempts = 40
	defaultQuotaConsumeNeedsOpsRetry   = 6 * time.Hour
	defaultQuotaConsumeAutoTerminalAge = 7 * 24 * time.Hour
)

func (s *Server) startQuotaConsumeRepairWorker() {
	if s == nil || s.store == nil || s.backgroundStop == nil {
		return
	}
	interval := envDurationWithDefault("QUOTA_CONSUME_REPAIR_INTERVAL_SECONDS", defaultQuotaConsumeRepairInterval)
	if interval <= 0 {
		interval = defaultQuotaConsumeRepairInterval
	}
	go func() {
		timer := time.NewTimer(5 * time.Second)
		defer timer.Stop()
		for {
			select {
			case <-s.backgroundStop:
				return
			case <-timer.C:
				s.repairDueQuotaConsumes()
				timer.Reset(interval)
			}
		}
	}()
}

func (s *Server) repairDueQuotaConsumes() {
	ctx, cancel := context.WithTimeout(context.Background(), envDurationWithDefault("QUOTA_CONSUME_REPAIR_TIMEOUT_SECONDS", defaultQuotaConsumeRepairTimeout))
	defer cancel()
	nowMs := time.Now().UnixMilli()
	jobs, err := s.store.ListDueQuotaConsumeOutbox(ctx, quotaConsumeRepairBatchLimit, nowMs)
	if err != nil {
		s.logger.Warn("quota consume outbox list failed", "error", err)
		return
	}
	for _, job := range jobs {
		if ctx.Err() != nil {
			return
		}
		claimNowMs := time.Now().UnixMilli()
		claimLeaseUntilMs := claimNowMs + int64(defaultQuotaConsumeRepairLease/time.Millisecond)
		claimed, err := s.store.ClaimDueQuotaConsumeOutboxForRepair(ctx, job.ID, claimNowMs, claimLeaseUntilMs)
		if err != nil {
			s.logger.Warn("quota consume outbox repair claim failed", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "error", err)
			continue
		}
		if !claimed {
			s.logger.Info("quota consume outbox repair skipped stale job", "userId", job.UserID, "clientMsgId", job.ClientMsgID)
			continue
		}
		consume, err := s.store.consumeOnDoneAt(ctx, job.UserID, job.Tier, job.ClientMsgID, job.DayCN, job.CompletionAt)
		if err == nil {
			if markErr := s.store.MarkQuotaConsumeOutboxDone(ctx, job.UserID, job.ClientMsgID, time.Now().UnixMilli()); markErr != nil {
				s.logger.Warn("quota consume outbox repair mark done failed", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "error", markErr)
				continue
			}
			s.logger.Info("quota consume outbox repaired", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "deducted", consume.Deducted, "source", consume.Source)
			continue
		}
		nextNowMs := time.Now().UnixMilli()
		nextAttempts := job.Attempts + 1
		if nextAttempts >= quotaConsumeRepairNeedsOpsAttempts {
			if quotaConsumeShouldAutoMarkUncollectable(err) || quotaConsumeShouldAutoTerminal(job, nextAttempts, nextNowMs) {
				if markErr := s.store.MarkQuotaConsumeOutboxUncollectableAfterAttempt(ctx, job.ID, err.Error(), nextAttempts, nextNowMs); markErr != nil {
					s.logger.Warn("quota consume outbox mark uncollectable failed", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "error", markErr)
					continue
				}
				s.logger.Warn("quota consume outbox auto marked uncollectable", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "attempts", nextAttempts, "error", err)
				continue
			}
			nextAttemptAt := nextNowMs + int64(quotaConsumeNeedsOpsRetryDelay()/time.Millisecond)
			if markErr := s.store.MarkQuotaConsumeOutboxNeedsOps(ctx, job.UserID, job.ClientMsgID, err.Error(), nextAttemptAt, nextNowMs); markErr != nil {
				s.logger.Warn("quota consume outbox mark needs ops failed", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "error", markErr)
				continue
			}
			s.logger.Warn("quota consume outbox needs ops automatic retry scheduled", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "attempts", nextAttempts, "nextAttemptAt", nextAttemptAt, "error", err)
			continue
		}
		nextAttemptAt := nextNowMs + int64(quotaConsumeRepairBackoff(job.Attempts)/time.Millisecond)
		if markErr := s.store.MarkQuotaConsumeOutboxFailed(ctx, job.UserID, job.ClientMsgID, err.Error(), nextAttemptAt, nextNowMs); markErr != nil {
			s.logger.Warn("quota consume outbox repair mark failed failed", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "error", markErr)
			continue
		}
		s.logger.Warn("quota consume outbox repair failed", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "attempts", nextAttempts, "nextAttemptAt", nextAttemptAt, "error", err)
	}
}

func quotaConsumeNeedsOpsRetryDelay() time.Duration {
	delay := envDurationWithDefault("QUOTA_CONSUME_NEEDS_OPS_RETRY_SECONDS", defaultQuotaConsumeNeedsOpsRetry)
	if delay <= 0 {
		return defaultQuotaConsumeNeedsOpsRetry
	}
	return delay
}

func quotaConsumeShouldAutoMarkUncollectable(err error) bool {
	if err == nil {
		return false
	}
	message := strings.ToLower(strings.TrimSpace(err.Error()))
	return strings.Contains(message, "quota_exhausted")
}

func quotaConsumeShouldAutoTerminal(job QuotaConsumeOutboxJob, attempts int, nowMs int64) bool {
	if attempts >= quotaConsumeRepairTerminalAttempts {
		return true
	}
	if attempts < quotaConsumeRepairNeedsOpsAttempts || job.CreatedAt <= 0 || nowMs <= job.CreatedAt {
		return false
	}
	return nowMs-job.CreatedAt >= int64(quotaConsumeAutoTerminalAge()/time.Millisecond)
}

func quotaConsumeAutoTerminalAge() time.Duration {
	age := envDurationWithDefault("QUOTA_CONSUME_AUTO_TERMINAL_AGE_SECONDS", defaultQuotaConsumeAutoTerminalAge)
	if age <= 0 {
		return defaultQuotaConsumeAutoTerminalAge
	}
	return age
}

func quotaConsumeRepairBackoff(attempts int) time.Duration {
	switch {
	case attempts <= 0:
		return time.Minute
	case attempts <= 2:
		return 5 * time.Minute
	case attempts <= 5:
		return 15 * time.Minute
	default:
		return time.Hour
	}
}

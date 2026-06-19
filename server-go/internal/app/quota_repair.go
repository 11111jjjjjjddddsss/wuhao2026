package app

import (
	"context"
	"time"
)

const (
	defaultQuotaConsumeRepairInterval  = 30 * time.Second
	defaultQuotaConsumeRepairTimeout   = 10 * time.Second
	quotaConsumeRepairBatchLimit       = 20
	quotaConsumeRepairNeedsOpsAttempts = 12
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
			if markErr := s.store.MarkQuotaConsumeOutboxNeedsOps(ctx, job.UserID, job.ClientMsgID, err.Error(), nextNowMs); markErr != nil {
				s.logger.Warn("quota consume outbox mark needs ops failed", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "error", markErr)
				continue
			}
			s.logger.Warn("quota consume outbox needs owner repair", "userId", job.UserID, "clientMsgId", job.ClientMsgID, "attempts", nextAttempts, "error", err)
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

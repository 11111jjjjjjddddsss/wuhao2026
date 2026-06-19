package app

import (
	"context"
	"time"
)

const (
	defaultMemorySummaryDrainInterval = 10 * time.Minute
	defaultMemorySummaryDrainTimeout  = 15 * time.Second
	defaultMemorySummaryDrainBatch    = 3
)

func (s *Server) startMemorySummaryDrainWorker() {
	if s == nil || s.store == nil || s.summary == nil || s.backgroundStop == nil {
		return
	}
	interval := envDurationWithDefault("MEMORY_SUMMARY_DRAIN_INTERVAL_SECONDS", defaultMemorySummaryDrainInterval)
	if interval <= 0 {
		interval = defaultMemorySummaryDrainInterval
	}
	batchSize := envIntWithDefault("MEMORY_SUMMARY_DRAIN_BATCH", defaultMemorySummaryDrainBatch)
	if batchSize <= 0 {
		batchSize = defaultMemorySummaryDrainBatch
	}
	go func() {
		timer := time.NewTimer(3 * time.Minute)
		defer timer.Stop()
		for {
			select {
			case <-s.backgroundStop:
				return
			case <-timer.C:
				s.runMemorySummaryDrain(batchSize)
				timer.Reset(interval)
			}
		}
	}()
}

func (s *Server) runMemorySummaryDrain(batchSize int) {
	if s == nil || s.store == nil || s.summary == nil || s.bailian == nil || !s.bailian.HasKeyConfigured() {
		return
	}
	if batchSize <= 0 {
		batchSize = defaultMemorySummaryDrainBatch
	}
	listCtx, cancelList := context.WithTimeout(context.Background(), envDurationWithDefault("MEMORY_SUMMARY_DRAIN_TIMEOUT_SECONDS", defaultMemorySummaryDrainTimeout))
	userIDs, err := s.store.ListPendingMemoryUserIDs(listCtx, batchSize)
	cancelList()
	if err != nil {
		s.logger.Warn("memory summary drain list pending users failed", "error", err)
		return
	}
	for _, userID := range userIDs {
		snapshotCtx, cancelSnapshot := context.WithTimeout(context.Background(), envDurationWithDefault("MEMORY_SUMMARY_DRAIN_TIMEOUT_SECONDS", defaultMemorySummaryDrainTimeout))
		snapshot, err := s.store.GetSessionSnapshot(snapshotCtx, userID)
		cancelSnapshot()
		if err != nil {
			s.logger.Warn("memory summary drain load snapshot failed", "userId", userID, "error", err)
			continue
		}
		if snapshot == nil || !snapshot.PendingMemory {
			continue
		}
		s.summary.ProcessSessionSummaries(userID, snapshot)
	}
}

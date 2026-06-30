package app

import (
	"context"
	"time"
)

const (
	defaultDataMaintenanceInterval = 6 * time.Hour
	defaultDataMaintenanceTimeout  = 15 * time.Second
)

func (s *Server) startDataMaintenanceWorker() {
	if s == nil || s.store == nil || s.backgroundStop == nil {
		return
	}
	interval := envDurationWithDefault("DATA_MAINTENANCE_INTERVAL_SECONDS", defaultDataMaintenanceInterval)
	if interval <= 0 {
		interval = defaultDataMaintenanceInterval
	}
	go func() {
		timer := time.NewTimer(2 * time.Minute)
		defer timer.Stop()
		for {
			select {
			case <-s.backgroundStop:
				return
			case <-timer.C:
				s.runDataMaintenance()
				timer.Reset(interval)
			}
		}
	}()
}

func (s *Server) runDataMaintenance() {
	ctx, cancel := context.WithTimeout(context.Background(), envDurationWithDefault("DATA_MAINTENANCE_TIMEOUT_SECONDS", defaultDataMaintenanceTimeout))
	defer cancel()
	nowMs := time.Now().UnixMilli()

	archiveRows, err := s.store.PruneExpiredSessionRoundArchive(ctx, nowMs)
	if err != nil {
		s.logger.Warn("data maintenance prune session archive failed", "error", err)
	} else if archiveRows > 0 {
		s.logger.Info("data maintenance pruned session archive", "rows", archiveRows)
	}

	logRows, err := s.store.PruneExpiredClientAppLogs(ctx, nowMs)
	if err != nil {
		s.logger.Warn("data maintenance prune client app logs failed", "error", err)
	} else if logRows > 0 {
		s.logger.Info("data maintenance pruned client app logs", "rows", logRows)
	}

	auditRows, err := s.store.PruneExpiredAdminAuditLogs(ctx, nowMs)
	if err != nil {
		s.logger.Warn("data maintenance prune admin audit logs failed", "error", err)
	} else if auditRows > 0 {
		s.logger.Info("data maintenance pruned admin audit logs", "rows", auditRows)
	}

	modelCallRows, err := s.store.PruneExpiredModelCallRecords(ctx, nowMs)
	if err != nil {
		s.logger.Warn("data maintenance prune model call records failed", "error", err)
	} else if modelCallRows > 0 {
		s.logger.Info("data maintenance pruned model call records", "rows", modelCallRows)
	}
}

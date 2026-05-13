package app

import (
	"context"
	"database/sql"
	"encoding/json"
	"strings"
	"time"
	"unicode/utf8"
)

const dailyAgriDefaultScope = "CN"

func (s *Store) GetDailyAgriCard(ctx context.Context, dayCN string, scope string) (*DailyAgriCard, string, error) {
	scope = normalizeDailyAgriScope(scope)
	var (
		status     string
		contentRaw sql.NullString
		generated  sql.NullInt64
	)
	err := s.db.QueryRowContext(
		ctx,
		`SELECT status, content_json, generated_at
		 FROM daily_agri_cards
		 WHERE day_cn = ? AND scope = ?
		 LIMIT 1`,
		dayCN,
		scope,
	).Scan(&status, &contentRaw, &generated)
	if err == sql.ErrNoRows {
		return nil, "missing", nil
	}
	if err != nil {
		return nil, "", err
	}
	if status != "ready" || !contentRaw.Valid || strings.TrimSpace(contentRaw.String) == "" {
		return nil, status, nil
	}
	var card DailyAgriCard
	if err := json.Unmarshal([]byte(contentRaw.String), &card); err != nil {
		return nil, "", err
	}
	if card.GeneratedAt == 0 {
		card.GeneratedAt = generated.Int64
	}
	return &card, status, nil
}

func (s *Store) ListRecentDailyAgriCards(ctx context.Context, sinceDayCN string, beforeDayCN string, scope string, limit int) ([]DailyAgriCard, error) {
	scope = normalizeDailyAgriScope(scope)
	if limit <= 0 {
		return nil, nil
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT day_cn, content_json, generated_at
		 FROM daily_agri_cards
		 WHERE scope = ? AND day_cn >= ? AND day_cn < ? AND status = 'ready' AND content_json IS NOT NULL
		 ORDER BY day_cn DESC
		 LIMIT ?`,
		scope,
		sinceDayCN,
		beforeDayCN,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	cards := make([]DailyAgriCard, 0, limit)
	for rows.Next() {
		var (
			dayCN      string
			contentRaw string
			generated  sql.NullInt64
		)
		if err := rows.Scan(&dayCN, &contentRaw, &generated); err != nil {
			return nil, err
		}
		if strings.TrimSpace(contentRaw) == "" {
			continue
		}
		var card DailyAgriCard
		if err := json.Unmarshal([]byte(contentRaw), &card); err != nil {
			return nil, err
		}
		if card.DateCN == "" {
			card.DateCN = dayCN
		}
		if card.GeneratedAt == 0 {
			card.GeneratedAt = generated.Int64
		}
		cards = append(cards, card)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return cards, nil
}

func (s *Store) TryAcquireDailyAgriCardGeneration(
	ctx context.Context,
	dayCN string,
	scope string,
	model string,
	searchStrategy string,
	promptVersion string,
	leaseToken string,
	leaseUntil int64,
) (bool, error) {
	scope = normalizeDailyAgriScope(scope)
	now := time.Now().UnixMilli()
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return false, err
	}
	defer rollbackQuietly(tx)

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO daily_agri_cards(day_cn, scope, status, model, search_strategy, prompt_version, lease_token, lease_until, created_at, updated_at)
		 VALUES (?, ?, 'pending', ?, ?, ?, '', 0, ?, ?)
		 ON DUPLICATE KEY UPDATE updated_at = updated_at`,
		dayCN,
		scope,
		model,
		searchStrategy,
		promptVersion,
		now,
		now,
	); err != nil {
		return false, err
	}

	var (
		status          string
		existingLeaseTo int64
	)
	if err := tx.QueryRowContext(
		ctx,
		`SELECT status, lease_until
		 FROM daily_agri_cards
		 WHERE day_cn = ? AND scope = ?
		 LIMIT 1 FOR UPDATE`,
		dayCN,
		scope,
	).Scan(&status, &existingLeaseTo); err != nil {
		return false, err
	}
	if status == "ready" {
		if err := tx.Commit(); err != nil {
			return false, err
		}
		return false, nil
	}
	if status == "pending" && existingLeaseTo > now {
		if err := tx.Commit(); err != nil {
			return false, err
		}
		return false, nil
	}

	if _, err := tx.ExecContext(
		ctx,
		`UPDATE daily_agri_cards
		 SET status = 'pending',
		     model = ?,
		     search_strategy = ?,
		     prompt_version = ?,
		     lease_token = ?,
		     lease_until = ?,
		     error = NULL,
		     updated_at = ?
		 WHERE day_cn = ? AND scope = ?`,
		model,
		searchStrategy,
		promptVersion,
		leaseToken,
		leaseUntil,
		now,
		dayCN,
		scope,
	); err != nil {
		return false, err
	}
	if err := tx.Commit(); err != nil {
		return false, err
	}
	return true, nil
}

func (s *Store) PublishDailyAgriCard(
	ctx context.Context,
	dayCN string,
	scope string,
	leaseToken string,
	card DailyAgriCard,
	sources []DailyAgriSearchSource,
) error {
	scope = normalizeDailyAgriScope(scope)
	now := time.Now().UnixMilli()
	card.DateCN = dayCN
	card.Title = "今日农情"
	card.GeneratedAt = now
	contentJSON, err := json.Marshal(card)
	if err != nil {
		return err
	}
	sourcesJSON, err := json.Marshal(sources)
	if err != nil {
		return err
	}
	result, err := s.db.ExecContext(
		ctx,
		`UPDATE daily_agri_cards
		 SET status = 'ready',
		     content_json = ?,
		     sources_json = ?,
		     lease_token = NULL,
		     lease_until = 0,
		     generated_at = ?,
		     error = NULL,
		     updated_at = ?
		 WHERE day_cn = ? AND scope = ? AND lease_token = ?`,
		string(contentJSON),
		string(sourcesJSON),
		now,
		now,
		dayCN,
		scope,
		leaseToken,
	)
	if err != nil {
		return err
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		return sql.ErrNoRows
	}
	return nil
}

func (s *Store) MarkDailyAgriCardFailed(ctx context.Context, dayCN string, scope string, leaseToken string, message string) error {
	scope = normalizeDailyAgriScope(scope)
	now := time.Now().UnixMilli()
	message = truncateUTF8Bytes(message, 240)
	result, err := s.db.ExecContext(
		ctx,
		`UPDATE daily_agri_cards
		 SET status = 'failed',
		     lease_token = NULL,
		     lease_until = 0,
		     error = ?,
		     updated_at = ?
		 WHERE day_cn = ? AND scope = ? AND lease_token = ?`,
		message,
		now,
		dayCN,
		scope,
		leaseToken,
	)
	if err != nil {
		return err
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if affected == 0 {
		return sql.ErrNoRows
	}
	return nil
}

func truncateUTF8Bytes(value string, maxBytes int) string {
	if maxBytes <= 0 || len(value) <= maxBytes {
		return value
	}
	for maxBytes > 0 && !utf8.ValidString(value[:maxBytes]) {
		maxBytes--
	}
	return value[:maxBytes]
}

func normalizeDailyAgriScope(scope string) string {
	normalized := strings.ToUpper(strings.TrimSpace(scope))
	if normalized == "" {
		return dailyAgriDefaultScope
	}
	if len(normalized) > 32 {
		return normalized[:32]
	}
	return normalized
}

package app

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"strings"
	"time"
	"unicode/utf8"
)

const (
	dailyAgriDefaultScope     = "CN"
	dailyAgriSourceTypeAuto   = "auto"
	dailyAgriSourceTypeManual = "manual"
)

type ManualDailyAgriPublishInput struct {
	DayCN       string
	Scope       string
	Items       []DailyAgriCardItem
	PublishedBy string
}

type DailyAgriCardRawStatus struct {
	DayCN          string
	Scope          string
	Status         string
	SourceType     string
	ManualLocked   bool
	ManualBy       string
	ManualAt       int64
	GeneratedAt    int64
	LeaseUntil     int64
	ErrorMessage   string
	ContentValid   bool
	ItemCount      int
	ContentPresent bool
}

func (s *Store) GetDailyAgriCard(ctx context.Context, dayCN string, scope string) (*DailyAgriCard, string, error) {
	scope = normalizeDailyAgriScope(scope)
	var (
		status     string
		contentRaw sql.NullString
		generated  sql.NullInt64
		sourceType sql.NullString
		manualLock bool
		manualBy   sql.NullString
		manualAt   sql.NullInt64
	)
	err := s.db.QueryRowContext(
		ctx,
		`SELECT status, content_json, generated_at, source_type, manual_locked, manual_by, manual_at
		 FROM daily_agri_cards
		 WHERE day_cn = ? AND scope = ?
		 LIMIT 1`,
		dayCN,
		scope,
	).Scan(&status, &contentRaw, &generated, &sourceType, &manualLock, &manualBy, &manualAt)
	if err == sql.ErrNoRows {
		return nil, "missing", nil
	}
	if err != nil {
		return nil, "", err
	}
	contentText := strings.TrimSpace(contentRaw.String)
	if status != "ready" || !contentRaw.Valid || contentText == "" {
		return nil, status, nil
	}
	if !json.Valid([]byte(contentText)) {
		return nil, "invalid_content", nil
	}
	var card DailyAgriCard
	if err := json.Unmarshal([]byte(contentText), &card); err != nil {
		return nil, "invalid_content", nil
	}
	if !isUsableStoredDailyAgriCard(card) {
		return nil, "invalid_content", nil
	}
	card.Title = "今日农情"
	if len(card.Items) > dailyAgriTargetItemCount {
		card.Items = card.Items[:dailyAgriTargetItemCount]
	}
	if card.GeneratedAt == 0 {
		card.GeneratedAt = generated.Int64
	}
	card.SourceType = normalizeDailyAgriSourceType(nullStringValue(sourceType))
	card.ManualLocked = manualLock
	card.ManualBy = nullStringValue(manualBy)
	if manualAt.Valid {
		card.ManualAt = manualAt.Int64
	}
	return &card, status, nil
}

func (s *Store) GetDailyAgriCardRawStatus(ctx context.Context, dayCN string, scope string) (DailyAgriCardRawStatus, error) {
	scope = normalizeDailyAgriScope(scope)
	result := DailyAgriCardRawStatus{
		DayCN:  dayCN,
		Scope:  scope,
		Status: "missing",
	}
	var (
		contentRaw sql.NullString
		sourceType sql.NullString
		manualBy   sql.NullString
		generated  sql.NullInt64
		manualAt   sql.NullInt64
		leaseUntil sql.NullInt64
		errMsg     sql.NullString
	)
	err := s.db.QueryRowContext(
		ctx,
		`SELECT status, content_json, generated_at, source_type, manual_locked, manual_by, manual_at, lease_until, error
		 FROM daily_agri_cards
		 WHERE day_cn = ? AND scope = ?
		 LIMIT 1`,
		dayCN,
		scope,
	).Scan(&result.Status, &contentRaw, &generated, &sourceType, &result.ManualLocked, &manualBy, &manualAt, &leaseUntil, &errMsg)
	if err == sql.ErrNoRows {
		return result, nil
	}
	if err != nil {
		return DailyAgriCardRawStatus{}, err
	}
	result.SourceType = normalizeDailyAgriSourceType(nullStringValue(sourceType))
	result.ManualBy = nullStringValue(manualBy)
	result.ErrorMessage = nullStringValue(errMsg)
	if generated.Valid {
		result.GeneratedAt = generated.Int64
	}
	if manualAt.Valid {
		result.ManualAt = manualAt.Int64
	}
	if leaseUntil.Valid {
		result.LeaseUntil = leaseUntil.Int64
	}
	contentText := strings.TrimSpace(contentRaw.String)
	result.ContentPresent = contentRaw.Valid && contentText != ""
	if result.ContentPresent && json.Valid([]byte(contentText)) {
		var card DailyAgriCard
		if err := json.Unmarshal([]byte(contentText), &card); err == nil && isUsableStoredDailyAgriCard(card) {
			result.ContentValid = true
			result.ItemCount = len(card.Items)
			if result.ItemCount > dailyAgriTargetItemCount {
				result.ItemCount = dailyAgriTargetItemCount
			}
		}
	}
	return result, nil
}

func (s *Store) ListRecentDailyAgriCards(ctx context.Context, sinceDayCN string, beforeDayCN string, scope string, limit int) ([]DailyAgriCard, error) {
	scope = normalizeDailyAgriScope(scope)
	if limit <= 0 {
		return nil, nil
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT day_cn, content_json, generated_at, source_type, manual_locked, manual_by, manual_at
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
			sourceType sql.NullString
			manualLock bool
			manualBy   sql.NullString
			manualAt   sql.NullInt64
		)
		if err := rows.Scan(&dayCN, &contentRaw, &generated, &sourceType, &manualLock, &manualBy, &manualAt); err != nil {
			return nil, err
		}
		contentText := strings.TrimSpace(contentRaw)
		if contentText == "" || !json.Valid([]byte(contentText)) {
			continue
		}
		var card DailyAgriCard
		if err := json.Unmarshal([]byte(contentText), &card); err != nil {
			continue
		}
		if !isUsableStoredDailyAgriCard(card) {
			continue
		}
		card.Title = "今日农情"
		if len(card.Items) > dailyAgriTargetItemCount {
			card.Items = card.Items[:dailyAgriTargetItemCount]
		}
		if card.DateCN == "" {
			card.DateCN = dayCN
		}
		if card.GeneratedAt == 0 {
			card.GeneratedAt = generated.Int64
		}
		card.SourceType = normalizeDailyAgriSourceType(nullStringValue(sourceType))
		card.ManualLocked = manualLock
		card.ManualBy = nullStringValue(manualBy)
		if manualAt.Valid {
			card.ManualAt = manualAt.Int64
		}
		cards = append(cards, card)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return cards, nil
}

func (s *Store) UpsertTodayAgriUserItem(ctx context.Context, userID string, dayCN string, anchorClientMsgID string, card DailyAgriCard, expectedGeneration *int) (bool, error) {
	now := time.Now().UnixMilli()
	card = sanitizeTodayAgriMainItemCard(card, dayCN)
	content, err := json.Marshal(card)
	if err != nil {
		return false, err
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return false, err
	}
	defer rollbackQuietly(tx)

	if _, err := tx.ExecContext(
		ctx,
		`INSERT INTO session_generation(user_id, generation, cleared_at, updated_at)
		 VALUES (?, 0, 0, ?)
		 ON DUPLICATE KEY UPDATE user_id = user_id`,
		userID,
		now,
	); err != nil {
		return false, err
	}
	var state SessionGenerationState
	if err := tx.QueryRowContext(
		ctx,
		`SELECT generation, cleared_at
		 FROM session_generation
		 WHERE user_id = ?
		 LIMIT 1 FOR UPDATE`,
		userID,
	).Scan(&state.Generation, &state.ClearedAt); err != nil {
		return false, err
	}
	if isStaleForSessionGenerationState(state, expectedGeneration) {
		if err := tx.Commit(); err != nil {
			return false, err
		}
		return false, nil
	}
	normalizedAnchorClientMsgID := normalizeTodayAgriAnchorClientMsgID(anchorClientMsgID)
	if normalizedAnchorClientMsgID == "" || len(normalizedAnchorClientMsgID) > 128 {
		return false, ErrTodayAgriAnchorNotArchived
	}
	anchorExists, err := s.sessionRoundArchiveExistsTx(ctx, tx, userID, normalizedAnchorClientMsgID)
	if err != nil {
		return false, err
	}
	if !anchorExists {
		return false, ErrTodayAgriAnchorNotArchived
	}
	if _, err := tx.ExecContext(
		ctx,
		`DELETE FROM today_agri_user_items
		 WHERE user_id = ? AND day_cn <> ?`,
		userID,
		dayCN,
	); err != nil {
		return false, err
	}
	_, err = tx.ExecContext(
		ctx,
		`INSERT INTO today_agri_user_items(user_id, day_cn, anchor_client_msg_id, content_json, created_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?)
		 ON DUPLICATE KEY UPDATE
		   anchor_client_msg_id = VALUES(anchor_client_msg_id),
		   content_json = VALUES(content_json),
		   updated_at = VALUES(updated_at)`,
		userID,
		dayCN,
		normalizedAnchorClientMsgID,
		string(content),
		now,
		now,
	)
	if err != nil {
		return false, err
	}
	if err := tx.Commit(); err != nil {
		return false, err
	}
	return true, nil
}

func (s *Store) GetTodayAgriUserItems(ctx context.Context, userID string, dayCN string, limit int) ([]TodayAgriUserItem, error) {
	return s.getTodayAgriUserItemsWith(ctx, s.db, userID, dayCN, limit)
}

func (s *Store) getTodayAgriUserItemsWith(ctx context.Context, q dbQueryer, userID string, dayCN string, limit int) ([]TodayAgriUserItem, error) {
	if limit <= 0 {
		return []TodayAgriUserItem{}, nil
	}
	rows, err := q.QueryContext(
		ctx,
		`SELECT day_cn, anchor_client_msg_id, content_json, created_at, updated_at
		 FROM today_agri_user_items
		 WHERE user_id = ? AND day_cn = ?
		 ORDER BY updated_at DESC, day_cn DESC
		 LIMIT ?`,
		userID,
		dayCN,
		limit,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]TodayAgriUserItem, 0, limit)
	for rows.Next() {
		var item TodayAgriUserItem
		var contentRaw string
		if err := rows.Scan(&item.DayCN, &item.AnchorClientMsgID, &contentRaw, &item.CreatedAt, &item.UpdatedAt); err != nil {
			return nil, err
		}
		if strings.TrimSpace(contentRaw) == "" || !json.Valid([]byte(contentRaw)) {
			continue
		}
		var card DailyAgriCard
		if err := json.Unmarshal([]byte(contentRaw), &card); err != nil {
			continue
		}
		if !isUsableStoredDailyAgriCard(card) {
			continue
		}
		card = sanitizeTodayAgriMainItemCard(card, item.DayCN)
		item.Card = card
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return items, nil
}

func sanitizeTodayAgriMainItemCard(card DailyAgriCard, dayCN string) DailyAgriCard {
	card.DateCN = strings.TrimSpace(card.DateCN)
	if card.DateCN == "" {
		card.DateCN = dayCN
	}
	card.Title = "今日农情"
	card.SourceType = ""
	card.ManualLocked = false
	card.ManualBy = ""
	card.ManualAt = 0
	if len(card.Items) > dailyAgriTargetItemCount {
		card.Items = card.Items[:dailyAgriTargetItemCount]
	}
	for idx := range card.Items {
		item := &card.Items[idx]
		item.Title = strings.TrimSpace(item.Title)
		item.Summary = strings.TrimSpace(item.Summary)
		item.Source = dailyAgriPublicSourceName(*item)
		item.PublishedDate = ""
		item.URL = ""
	}
	return card
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
		`INSERT INTO daily_agri_cards(day_cn, scope, status, model, search_strategy, prompt_version, source_type, manual_locked, lease_token, lease_until, created_at, updated_at)
		 VALUES (?, ?, 'pending', ?, ?, ?, ?, 0, '', 0, ?, ?)
		 ON DUPLICATE KEY UPDATE updated_at = updated_at`,
		dayCN,
		scope,
		model,
		searchStrategy,
		promptVersion,
		dailyAgriSourceTypeAuto,
		now,
		now,
	); err != nil {
		return false, err
	}

	var (
		status          string
		existingLeaseTo int64
		existingContent sql.NullString
		manualLocked    bool
	)
	if err := tx.QueryRowContext(
		ctx,
		`SELECT status, lease_until, content_json, manual_locked
		 FROM daily_agri_cards
		 WHERE day_cn = ? AND scope = ?
		 LIMIT 1 FOR UPDATE`,
		dayCN,
		scope,
	).Scan(&status, &existingLeaseTo, &existingContent, &manualLocked); err != nil {
		return false, err
	}
	if manualLocked {
		if err := tx.Commit(); err != nil {
			return false, err
		}
		return false, nil
	}
	if status == "ready" && isUsableDailyAgriContentJSON(existingContent) {
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
		     source_type = ?,
		     manual_locked = 0,
		     manual_by = NULL,
		     manual_at = NULL,
		     lease_token = ?,
		     lease_until = ?,
		     error = NULL,
		     updated_at = ?
		 WHERE day_cn = ? AND scope = ?`,
		model,
		searchStrategy,
		promptVersion,
		dailyAgriSourceTypeAuto,
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

func isUsableDailyAgriContentJSON(raw sql.NullString) bool {
	contentText := strings.TrimSpace(raw.String)
	if !raw.Valid || contentText == "" || !json.Valid([]byte(contentText)) {
		return false
	}
	var card DailyAgriCard
	if err := json.Unmarshal([]byte(contentText), &card); err != nil {
		return false
	}
	return isUsableStoredDailyAgriCard(card)
}

func isUsableStoredDailyAgriCard(card DailyAgriCard) bool {
	if len(card.Items) < dailyAgriMinPublishItems {
		return false
	}
	checkCount := minInt(len(card.Items), dailyAgriTargetItemCount)
	for _, item := range card.Items[:checkCount] {
		if strings.TrimSpace(item.Title) == "" ||
			strings.TrimSpace(item.Summary) == "" {
			return false
		}
	}
	return true
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
	card.SourceType = dailyAgriSourceTypeAuto
	card.ManualLocked = false
	card.ManualBy = ""
	card.ManualAt = 0
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
		     source_type = ?,
		     manual_locked = 0,
		     manual_by = NULL,
		     manual_at = NULL,
		     lease_token = NULL,
		     lease_until = 0,
		     generated_at = ?,
		     error = NULL,
		     updated_at = ?
		 WHERE day_cn = ? AND scope = ? AND lease_token = ?`,
		string(contentJSON),
		string(sourcesJSON),
		dailyAgriSourceTypeAuto,
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

func (s *Store) PublishManualDailyAgriCard(ctx context.Context, input ManualDailyAgriPublishInput) (DailyAgriCard, error) {
	dayCN := normalizeTodayAgriContextDay(input.DayCN)
	if dayCN == "" {
		return DailyAgriCard{}, fmt.Errorf("invalid_day_cn")
	}
	if _, err := time.Parse("20060102", dayCN); err != nil {
		return DailyAgriCard{}, fmt.Errorf("invalid_day_cn")
	}
	scope := normalizeDailyAgriScope(input.Scope)
	items, err := normalizeManualDailyAgriItems(input.Items)
	if err != nil {
		return DailyAgriCard{}, err
	}
	publishedBy := truncateUTF8Bytes(strings.TrimSpace(input.PublishedBy), 128)
	if publishedBy == "" {
		publishedBy = "admin"
	}
	now := time.Now().UnixMilli()
	card := DailyAgriCard{
		DateCN:       dayCN,
		Title:        "今日农情",
		Items:        items,
		GeneratedAt:  now,
		SourceType:   dailyAgriSourceTypeManual,
		ManualLocked: true,
		ManualBy:     publishedBy,
		ManualAt:     now,
	}
	contentJSON, err := json.Marshal(card)
	if err != nil {
		return DailyAgriCard{}, err
	}
	sources := manualDailyAgriSources(items)
	sourcesJSON, err := json.Marshal(sources)
	if err != nil {
		return DailyAgriCard{}, err
	}
	_, err = s.db.ExecContext(
		ctx,
		`INSERT INTO daily_agri_cards(
		     day_cn, scope, status, content_json, sources_json,
		     model, search_strategy, prompt_version, source_type, manual_locked, manual_by, manual_at,
		     lease_token, lease_until, generated_at, error, created_at, updated_at
		   )
		   VALUES (?, ?, 'ready', ?, ?, 'manual', 'manual', 'manual', ?, 1, ?, ?, NULL, 0, ?, NULL, ?, ?)
		   ON DUPLICATE KEY UPDATE
		     status = 'ready',
		     content_json = VALUES(content_json),
		     sources_json = VALUES(sources_json),
		     model = 'manual',
		     search_strategy = 'manual',
		     prompt_version = 'manual',
		     source_type = VALUES(source_type),
		     manual_locked = 1,
		     manual_by = VALUES(manual_by),
		     manual_at = VALUES(manual_at),
		     lease_token = NULL,
		     lease_until = 0,
		     generated_at = VALUES(generated_at),
		     error = NULL,
		     updated_at = VALUES(updated_at)`,
		dayCN,
		scope,
		string(contentJSON),
		string(sourcesJSON),
		dailyAgriSourceTypeManual,
		publishedBy,
		now,
		now,
		now,
		now,
	)
	if err != nil {
		return DailyAgriCard{}, err
	}
	return card, nil
}

func normalizeManualDailyAgriItems(rawItems []DailyAgriCardItem) ([]DailyAgriCardItem, error) {
	if len(rawItems) != dailyAgriTargetItemCount {
		return nil, fmt.Errorf("invalid_item_count")
	}
	items := make([]DailyAgriCardItem, 0, dailyAgriTargetItemCount)
	for idx, raw := range rawItems {
		title := truncateUTF8Bytes(strings.TrimSpace(raw.Title), 96)
		summary := truncateUTF8Bytes(strings.TrimSpace(raw.Summary), 420)
		source := sanitizeDailyAgriPublicSourceLabel(raw.Source)
		if title == "" || summary == "" {
			return nil, fmt.Errorf("invalid_item_%d", idx+1)
		}
		items = append(items, DailyAgriCardItem{
			Title:   title,
			Summary: summary,
			Source:  source,
		})
	}
	return items, nil
}

func manualDailyAgriSources(items []DailyAgriCardItem) []DailyAgriSearchSource {
	sources := make([]DailyAgriSearchSource, 0, len(items))
	for idx, item := range items {
		if strings.TrimSpace(item.Source) == "" {
			continue
		}
		sources = append(sources, DailyAgriSearchSource{
			Index:    idx + 1,
			Title:    item.Title,
			SiteName: item.Source,
		})
	}
	return sources
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

func normalizeDailyAgriSourceType(sourceType string) string {
	normalized := strings.ToLower(strings.TrimSpace(sourceType))
	if normalized == dailyAgriSourceTypeManual {
		return dailyAgriSourceTypeManual
	}
	return dailyAgriSourceTypeAuto
}

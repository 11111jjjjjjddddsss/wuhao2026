SET @idx_gift_cards_code_suffix_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gift_cards'
    AND INDEX_NAME = 'idx_gift_cards_code_suffix'
);
SET @stmt := IF(
  @idx_gift_cards_code_suffix_exists = 0,
  'ALTER TABLE gift_cards ADD INDEX idx_gift_cards_code_suffix (code_suffix, created_at)',
  'SELECT 1'
);
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @idx_gift_card_attempts_suffix_created_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gift_card_redemption_attempts'
    AND INDEX_NAME = 'idx_gift_card_attempts_suffix_created'
);
SET @stmt := IF(
  @idx_gift_card_attempts_suffix_created_exists = 0,
  'ALTER TABLE gift_card_redemption_attempts ADD INDEX idx_gift_card_attempts_suffix_created (code_suffix, created_at)',
  'SELECT 1'
);
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @idx_gift_card_attempts_reason_created_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gift_card_redemption_attempts'
    AND INDEX_NAME = 'idx_gift_card_attempts_reason_created'
);
SET @stmt := IF(
  @idx_gift_card_attempts_reason_created_exists = 0,
  'ALTER TABLE gift_card_redemption_attempts ADD INDEX idx_gift_card_attempts_reason_created (failure_reason, created_at)',
  'SELECT 1'
);
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

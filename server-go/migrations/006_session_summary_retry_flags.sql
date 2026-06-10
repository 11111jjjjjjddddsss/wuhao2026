SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_ab'
    AND COLUMN_NAME = 'pending_retry_b'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE session_ab ADD COLUMN pending_retry_b TINYINT(1) NOT NULL DEFAULT 0 AFTER b_summary',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

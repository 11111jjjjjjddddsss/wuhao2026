SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_ab'
    AND COLUMN_NAME = 'c_summary'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE session_ab ADD COLUMN c_summary TEXT NULL AFTER b_summary',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

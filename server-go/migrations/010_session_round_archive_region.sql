SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_round_archive'
    AND COLUMN_NAME = 'region'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE session_round_archive ADD COLUMN region VARCHAR(255) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_round_archive'
    AND COLUMN_NAME = 'region_source'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE session_round_archive ADD COLUMN region_source VARCHAR(32) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_round_archive'
    AND COLUMN_NAME = 'region_reliability'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE session_round_archive ADD COLUMN region_reliability VARCHAR(32) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

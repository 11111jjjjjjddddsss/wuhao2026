SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_ab'
    AND COLUMN_NAME = 'last_region'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE session_ab ADD COLUMN last_region VARCHAR(255) NULL',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_ab'
    AND COLUMN_NAME = 'last_region_source'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE session_ab ADD COLUMN last_region_source ENUM(''gps'',''ip'',''none'') NULL',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_ab'
    AND COLUMN_NAME = 'last_region_reliability'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE session_ab ADD COLUMN last_region_reliability ENUM(''reliable'',''unreliable'') NULL',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_ab'
    AND COLUMN_NAME = 'last_seen_at'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE session_ab ADD COLUMN last_seen_at BIGINT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

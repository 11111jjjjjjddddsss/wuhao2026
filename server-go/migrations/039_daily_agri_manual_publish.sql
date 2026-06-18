SET @needs_column := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'daily_agri_cards'
     AND COLUMN_NAME = 'source_type'
);
SET @ddl := IF(@needs_column = 0,
  'ALTER TABLE daily_agri_cards ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT ''auto'' AFTER prompt_version',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @needs_column := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'daily_agri_cards'
     AND COLUMN_NAME = 'manual_locked'
);
SET @ddl := IF(@needs_column = 0,
  'ALTER TABLE daily_agri_cards ADD COLUMN manual_locked TINYINT(1) NOT NULL DEFAULT 0 AFTER source_type',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @needs_column := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'daily_agri_cards'
     AND COLUMN_NAME = 'manual_by'
);
SET @ddl := IF(@needs_column = 0,
  'ALTER TABLE daily_agri_cards ADD COLUMN manual_by VARCHAR(128) NULL AFTER manual_locked',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @needs_column := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'daily_agri_cards'
     AND COLUMN_NAME = 'manual_at'
);
SET @ddl := IF(@needs_column = 0,
  'ALTER TABLE daily_agri_cards ADD COLUMN manual_at BIGINT NULL AFTER manual_by',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

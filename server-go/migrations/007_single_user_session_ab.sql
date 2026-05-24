SET @session_id_column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_ab'
    AND COLUMN_NAME = 'session_id'
);
SET @ddl := IF(
  @session_id_column_exists > 0,
  'ALTER TABLE session_ab DROP PRIMARY KEY, DROP COLUMN session_id, ADD PRIMARY KEY (user_id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

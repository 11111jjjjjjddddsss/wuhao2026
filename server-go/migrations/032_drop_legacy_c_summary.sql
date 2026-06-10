SET @c_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_ab'
    AND COLUMN_NAME = 'c_summary'
);
SET @ddl := IF(
  @c_exists > 0,
  'UPDATE session_ab SET b_summary = TRIM(CONCAT(COALESCE(NULLIF(TRIM(b_summary), ''''), ''''), CASE WHEN NULLIF(TRIM(b_summary), '''') IS NOT NULL AND NULLIF(TRIM(c_summary), '''') IS NOT NULL THEN CHAR(10) ELSE '''' END, COALESCE(NULLIF(TRIM(c_summary), ''''), ''''))) WHERE NULLIF(TRIM(c_summary), '''') IS NOT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pending_c_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_ab'
    AND COLUMN_NAME = 'pending_retry_c'
);
SET @ddl := IF(
  @pending_c_exists > 0,
  'UPDATE session_ab SET pending_retry_b = IF(pending_retry_b <> 0 OR pending_retry_c <> 0, 1, 0)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  @pending_c_exists > 0,
  'ALTER TABLE session_ab DROP COLUMN pending_retry_c',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  @c_exists > 0,
  'ALTER TABLE session_ab DROP COLUMN c_summary',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

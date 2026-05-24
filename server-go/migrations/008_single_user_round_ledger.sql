SET @session_round_session_id_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_round_ledger'
    AND COLUMN_NAME = 'session_id'
);
SET @session_round_msg_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_round_ledger'
    AND INDEX_NAME = 'uk_session_round_msg'
);
SET @ddl := IF(
  @session_round_session_id_exists > 0 AND @session_round_msg_index_exists > 0,
  'ALTER TABLE session_round_ledger DROP INDEX uk_session_round_msg',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  @session_round_session_id_exists > 0,
  'ALTER TABLE session_round_ledger DROP COLUMN session_id',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @session_round_msg_index_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'session_round_ledger'
    AND INDEX_NAME = 'uk_session_round_msg'
);
SET @ddl := IF(
  @session_round_msg_index_exists = 0,
  'ALTER TABLE session_round_ledger ADD UNIQUE KEY uk_session_round_msg (user_id, client_msg_id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

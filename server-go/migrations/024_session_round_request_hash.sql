SET @session_round_request_hash_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'session_round_ledger'
    AND column_name = 'request_hash'
);

SET @session_round_request_hash_sql = IF(
  @session_round_request_hash_exists = 0,
  'ALTER TABLE session_round_ledger ADD COLUMN request_hash CHAR(64) NULL AFTER client_msg_id',
  'DO 0'
);
PREPARE session_round_request_hash_stmt FROM @session_round_request_hash_sql;
EXECUTE session_round_request_hash_stmt;
DEALLOCATE PREPARE session_round_request_hash_stmt;

SET @needs_quota_consume_outbox_terminal_states := (
  SELECT COUNT(*)
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'quota_consume_outbox'
     AND COLUMN_NAME = 'status'
     AND COLUMN_TYPE LIKE '%needs_ops%'
     AND COLUMN_TYPE LIKE '%waived%'
     AND COLUMN_TYPE LIKE '%uncollectable%'
);
SET @ddl := IF(@needs_quota_consume_outbox_terminal_states = 0,
  'ALTER TABLE quota_consume_outbox MODIFY status ENUM(''pending'',''done'',''failed'',''needs_ops'',''waived'',''uncollectable'') NOT NULL DEFAULT ''pending''',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

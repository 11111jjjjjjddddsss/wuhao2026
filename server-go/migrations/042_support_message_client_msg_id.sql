SET @support_client_msg_id_column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'support_messages'
    AND COLUMN_NAME = 'client_msg_id'
);
SET @ddl := IF(
  @support_client_msg_id_column_exists = 0,
  'ALTER TABLE support_messages ADD COLUMN client_msg_id VARCHAR(128) NULL AFTER user_id',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @support_client_msg_id_unique_exists := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'support_messages'
    AND index_name = 'uq_support_messages_user_sender_client_msg'
);
SET @ddl := IF(
  @support_client_msg_id_unique_exists = 0,
  'CREATE UNIQUE INDEX uq_support_messages_user_sender_client_msg ON support_messages (user_id, sender_type, client_msg_id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

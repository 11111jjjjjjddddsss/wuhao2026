SET @index_exists := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'support_messages'
    AND INDEX_NAME = 'idx_support_messages_created_id'
);
SET @ddl := IF(
  @index_exists = 0,
  'ALTER TABLE support_messages ADD INDEX idx_support_messages_created_id (created_at, id)',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

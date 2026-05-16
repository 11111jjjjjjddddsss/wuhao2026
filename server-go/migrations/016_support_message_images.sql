SET @column_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'support_messages'
    AND COLUMN_NAME = 'image_urls_json'
);
SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE support_messages ADD COLUMN image_urls_json JSON NULL AFTER body',
  'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

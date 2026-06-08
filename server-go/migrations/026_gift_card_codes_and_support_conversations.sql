SET @gift_code_ciphertext_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'gift_cards'
    AND COLUMN_NAME = 'code_ciphertext'
);
SET @stmt := IF(
  @gift_code_ciphertext_exists = 0,
  'ALTER TABLE gift_cards ADD COLUMN code_ciphertext VARCHAR(512) NULL AFTER code_suffix',
  'SELECT 1'
);
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS support_conversations (
  user_id VARCHAR(191) PRIMARY KEY,
  status VARCHAR(32) NOT NULL DEFAULT 'open',
  assigned_to VARCHAR(96) NULL,
  note VARCHAR(255) NULL,
  message_count INT NOT NULL DEFAULT 0,
  latest_message_id BIGINT NULL,
  latest_message_at BIGINT NULL,
  latest_user_message_at BIGINT NULL,
  latest_admin_message_at BIGINT NULL,
  closed_at BIGINT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  KEY idx_support_conversations_status_updated (status, updated_at),
  KEY idx_support_conversations_assigned (assigned_to, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

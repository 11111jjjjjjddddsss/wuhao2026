CREATE TABLE IF NOT EXISTS account_deletion_requests (
  request_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(128) NOT NULL,
  phone_mask VARCHAR(32) NULL,
  status ENUM('pending','processing','cancelled','rejected','completed') NOT NULL DEFAULT 'pending',
  reason VARCHAR(255) NULL,
  user_message VARCHAR(255) NULL,
  handled_by VARCHAR(96) NULL,
  handler_note VARCHAR(255) NULL,
  handled_at BIGINT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  KEY idx_account_deletion_status_created (status, created_at),
  KEY idx_account_deletion_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

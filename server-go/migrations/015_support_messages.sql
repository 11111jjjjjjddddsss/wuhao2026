CREATE TABLE IF NOT EXISTS support_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(191) NOT NULL,
  sender_type ENUM('user', 'admin', 'system') NOT NULL,
  body TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  read_by_user_at BIGINT NULL,
  INDEX idx_support_messages_user_created (user_id, created_at, id),
  INDEX idx_support_messages_user_unread (user_id, sender_type, read_by_user_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

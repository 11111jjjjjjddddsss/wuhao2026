CREATE TABLE IF NOT EXISTS today_agri_user_items (
  user_id VARCHAR(128) NOT NULL,
  day_cn VARCHAR(8) NOT NULL,
  anchor_client_msg_id VARCHAR(128) NOT NULL,
  content_json TEXT NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (user_id, day_cn),
  KEY idx_today_agri_user_items_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

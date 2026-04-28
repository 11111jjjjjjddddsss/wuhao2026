CREATE TABLE IF NOT EXISTS session_round_archive (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(128) NOT NULL,
  client_msg_id VARCHAR(128) NOT NULL,
  user_text MEDIUMTEXT NOT NULL,
  user_images_json JSON NULL,
  assistant_text MEDIUMTEXT NOT NULL,
  source VARCHAR(32) NOT NULL DEFAULT 'round_complete',
  region VARCHAR(255) NULL,
  region_source VARCHAR(32) NULL,
  region_reliability VARCHAR(32) NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  UNIQUE KEY uk_session_round_archive_user_msg (user_id, client_msg_id),
  KEY idx_session_round_archive_user_created (user_id, created_at),
  KEY idx_session_round_archive_created (created_at)
);

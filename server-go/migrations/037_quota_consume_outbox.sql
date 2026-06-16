CREATE TABLE IF NOT EXISTS quota_consume_outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(128) NOT NULL,
  client_msg_id VARCHAR(128) NOT NULL,
  day_cn VARCHAR(8) NOT NULL,
  tier_at_completion ENUM('free','plus','pro') NOT NULL DEFAULT 'free',
  completion_at BIGINT NOT NULL,
  status ENUM('pending','done','failed') NOT NULL DEFAULT 'pending',
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(255) NULL,
  next_attempt_at BIGINT NOT NULL DEFAULT 0,
  repaired_at BIGINT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  UNIQUE KEY uk_quota_consume_outbox_user_msg (user_id, client_msg_id),
  KEY idx_quota_consume_outbox_status_next (status, next_attempt_at, id),
  KEY idx_quota_consume_outbox_completion (completion_at, status)
);

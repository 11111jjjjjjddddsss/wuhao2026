CREATE TABLE IF NOT EXISTS gift_card_batches (
  batch_id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL DEFAULT '',
  tier ENUM('plus','pro') NOT NULL,
  duration_days INT NOT NULL DEFAULT 30,
  quantity INT NOT NULL,
  valid_from BIGINT NOT NULL,
  valid_until BIGINT NULL,
  created_by VARCHAR(96) NOT NULL,
  note VARCHAR(255) NULL,
  created_at BIGINT NOT NULL,
  KEY idx_gift_card_batches_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gift_cards (
  card_id VARCHAR(64) PRIMARY KEY,
  batch_id VARCHAR(64) NOT NULL,
  code_hash CHAR(64) NOT NULL,
  code_mask VARCHAR(32) NOT NULL,
  code_suffix VARCHAR(16) NOT NULL,
  tier ENUM('plus','pro') NOT NULL,
  duration_days INT NOT NULL DEFAULT 30,
  status ENUM('active','redeemed','void') NOT NULL DEFAULT 'active',
  valid_from BIGINT NOT NULL,
  valid_until BIGINT NULL,
  created_by VARCHAR(96) NOT NULL,
  note VARCHAR(255) NULL,
  redeemed_user_id VARCHAR(128) NULL,
  redeemed_phone_mask VARCHAR(32) NULL,
  redeemed_region VARCHAR(128) NULL,
  redeemed_region_source VARCHAR(32) NULL,
  redeemed_region_reliability VARCHAR(32) NULL,
  redeemed_at BIGINT NULL,
  membership_expire_at BIGINT NULL,
  voided_at BIGINT NULL,
  updated_at BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE KEY uk_gift_cards_code_hash (code_hash),
  KEY idx_gift_cards_batch (batch_id),
  KEY idx_gift_cards_redeemed_user (redeemed_user_id, redeemed_at),
  KEY idx_gift_cards_status (status, created_at),
  CONSTRAINT fk_gift_cards_batch
    FOREIGN KEY (batch_id) REFERENCES gift_card_batches(batch_id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gift_card_redemption_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code_suffix VARCHAR(16) NULL,
  user_id VARCHAR(128) NULL,
  success TINYINT(1) NOT NULL,
  failure_reason VARCHAR(64) NULL,
  masked_ip VARCHAR(64) NULL,
  region VARCHAR(128) NULL,
  region_source VARCHAR(32) NULL,
  region_reliability VARCHAR(32) NULL,
  created_at BIGINT NOT NULL,
  KEY idx_gift_card_attempts_user (user_id, created_at),
  KEY idx_gift_card_attempts_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

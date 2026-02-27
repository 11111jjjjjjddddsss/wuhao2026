CREATE TABLE IF NOT EXISTS user_entitlement (
  user_id VARCHAR(128) PRIMARY KEY,
  tier ENUM('free','plus','pro') NOT NULL DEFAULT 'free',
  tier_expire_at BIGINT NULL,
  updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS daily_usage (
  user_id VARCHAR(128) NOT NULL,
  day_cn VARCHAR(8) NOT NULL,
  used INT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, day_cn)
);

CREATE TABLE IF NOT EXISTS quota_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(128) NOT NULL,
  client_msg_id VARCHAR(128) NOT NULL,
  day_cn VARCHAR(8) NOT NULL,
  source ENUM('daily','topup','upgrade') NOT NULL,
  delta INT NOT NULL DEFAULT 1,
  created_at BIGINT NOT NULL,
  UNIQUE KEY uk_ledger_user_msg (user_id, client_msg_id)
);

CREATE TABLE IF NOT EXISTS topup_packs (
  pack_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(128) NOT NULL,
  remaining INT NOT NULL DEFAULT 0,
  expire_at BIGINT NOT NULL,
  status ENUM('active','used_up','expired') NOT NULL DEFAULT 'active',
  created_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS upgrade_credits (
  user_id VARCHAR(128) PRIMARY KEY,
  remaining INT NOT NULL DEFAULT 0,
  expire_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS session_ab (
  user_id VARCHAR(128) NOT NULL,
  session_id VARCHAR(128) NOT NULL,
  a_json JSON NULL,
  b_summary TEXT NULL,
  round_total INT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (user_id, session_id)
);


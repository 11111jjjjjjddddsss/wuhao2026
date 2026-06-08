CREATE TABLE IF NOT EXISTS app_accounts (
  user_id VARCHAR(128) PRIMARY KEY,
  phone_hash CHAR(64) NOT NULL,
  phone_mask VARCHAR(32) NOT NULL,
  phone_ciphertext VARCHAR(512) NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  last_login_at BIGINT NULL,
  UNIQUE KEY uk_app_accounts_phone_hash (phone_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auth_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(128) NOT NULL,
  device_id VARCHAR(128) NULL,
  token_expires_at BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  revoked_at BIGINT NULL,
  KEY idx_auth_sessions_user_updated (user_id, updated_at),
  KEY idx_auth_sessions_expire (token_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_id_migrations (
  old_user_id VARCHAR(128) PRIMARY KEY,
  new_user_id VARCHAR(128) NOT NULL,
  migrated_at BIGINT NOT NULL,
  KEY idx_user_id_migrations_new_user (new_user_id, migrated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

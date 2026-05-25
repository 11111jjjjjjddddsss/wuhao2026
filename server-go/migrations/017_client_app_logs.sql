CREATE TABLE IF NOT EXISTS client_app_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(191) NOT NULL,
  level ENUM('info', 'warn', 'error') NOT NULL,
  event VARCHAR(96) NOT NULL,
  message VARCHAR(255) NOT NULL,
  attrs_json JSON NULL,
  platform VARCHAR(32) NOT NULL DEFAULT 'android',
  app_version_code INT NULL,
  app_version_name VARCHAR(64) NULL,
  os_version VARCHAR(64) NULL,
  device_model VARCHAR(128) NULL,
  client_time_ms BIGINT NULL,
  created_at BIGINT NOT NULL,
  masked_ip VARCHAR(64) NULL,
  INDEX idx_client_app_logs_created (created_at, id),
  INDEX idx_client_app_logs_user_created (user_id, created_at, id),
  INDEX idx_client_app_logs_event_created (event, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

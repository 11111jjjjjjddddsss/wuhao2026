CREATE TABLE IF NOT EXISTS admin_audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  actor VARCHAR(96) NOT NULL,
  action VARCHAR(96) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id VARCHAR(191) NULL,
  target_user_id VARCHAR(191) NULL,
  success TINYINT(1) NOT NULL,
  status_code INT NULL,
  details_json JSON NULL,
  masked_ip VARCHAR(64) NULL,
  user_agent VARCHAR(255) NULL,
  created_at BIGINT NOT NULL,
  KEY idx_admin_audit_logs_created (created_at, id),
  KEY idx_admin_audit_logs_action_created (action, created_at, id),
  KEY idx_admin_audit_logs_target_user_created (target_user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

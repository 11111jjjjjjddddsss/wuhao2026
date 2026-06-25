CREATE TABLE IF NOT EXISTS payment_orders (
  out_trade_no VARCHAR(64) NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  provider VARCHAR(32) NOT NULL DEFAULT 'alipay',
  product_type VARCHAR(32) NOT NULL,
  amount_cents INT NOT NULL,
  currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
  subject VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  provider_trade_no VARCHAR(128) NULL,
  provider_buyer_id VARCHAR(128) NULL,
  provider_status VARCHAR(64) NULL,
  entitlement_order_id VARCHAR(128) NULL,
  grant_status VARCHAR(32) NOT NULL DEFAULT 'pending',
  grant_error VARCHAR(255) NULL,
  grant_claimed_at BIGINT NULL,
  client_app_version VARCHAR(32) NULL,
  client_platform VARCHAR(32) NULL,
  client_build_type VARCHAR(32) NULL,
  client_version_code INT NULL,
  client_ip_mask VARCHAR(64) NULL,
  is_test_order TINYINT(1) NOT NULL DEFAULT 0,
  original_amount_cents INT NULL,
  last_notify_json JSON NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  paid_at BIGINT NULL,
  granted_at BIGINT NULL,
  PRIMARY KEY (out_trade_no),
  UNIQUE KEY uniq_payment_provider_trade (provider, provider_trade_no),
  KEY idx_payment_orders_user_created (user_id, created_at),
  KEY idx_payment_orders_status_updated (status, updated_at),
  KEY idx_payment_orders_grant_status_updated (grant_status, updated_at),
  KEY idx_payment_orders_grant_status_claimed (grant_status, grant_claimed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @payment_orders_grant_claimed_at_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'grant_claimed_at'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN grant_claimed_at BIGINT NULL AFTER grant_error'
  )
);
PREPARE payment_orders_grant_claimed_at_stmt FROM @payment_orders_grant_claimed_at_sql;
EXECUTE payment_orders_grant_claimed_at_stmt;
DEALLOCATE PREPARE payment_orders_grant_claimed_at_stmt;

SET @payment_orders_grant_claimed_idx_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND INDEX_NAME = 'idx_payment_orders_grant_status_claimed'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD KEY idx_payment_orders_grant_status_claimed (grant_status, grant_claimed_at)'
  )
);
PREPARE payment_orders_grant_claimed_idx_stmt FROM @payment_orders_grant_claimed_idx_sql;
EXECUTE payment_orders_grant_claimed_idx_stmt;
DEALLOCATE PREPARE payment_orders_grant_claimed_idx_stmt;

CREATE TABLE IF NOT EXISTS payment_notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  provider VARCHAR(32) NOT NULL DEFAULT 'alipay',
  out_trade_no VARCHAR(64) NOT NULL,
  provider_trade_no VARCHAR(128) NULL,
  notify_id VARCHAR(128) NULL,
  app_id VARCHAR(64) NULL,
  seller_id VARCHAR(64) NULL,
  trade_status VARCHAR(64) NULL,
  total_amount_cents INT NULL,
  signature_valid TINYINT(1) NOT NULL DEFAULT 0,
  process_status VARCHAR(32) NOT NULL DEFAULT 'received',
  error_code VARCHAR(64) NULL,
  received_at BIGINT NOT NULL,
  processed_at BIGINT NULL,
  summary_json JSON NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_payment_notify_provider_notify (provider, notify_id),
  KEY idx_payment_notify_out_trade (out_trade_no, received_at),
  KEY idx_payment_notify_trade (provider, provider_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

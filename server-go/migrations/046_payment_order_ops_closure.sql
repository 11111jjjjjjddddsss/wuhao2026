SET @payment_orders_refund_status_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'refund_status'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN refund_status VARCHAR(32) NULL AFTER grant_error'
  )
);
PREPARE payment_orders_refund_status_stmt FROM @payment_orders_refund_status_sql;
EXECUTE payment_orders_refund_status_stmt;
DEALLOCATE PREPARE payment_orders_refund_status_stmt;

SET @payment_orders_refund_amount_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'refund_amount_cents'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN refund_amount_cents INT NOT NULL DEFAULT 0 AFTER refund_status'
  )
);
PREPARE payment_orders_refund_amount_stmt FROM @payment_orders_refund_amount_sql;
EXECUTE payment_orders_refund_amount_stmt;
DEALLOCATE PREPARE payment_orders_refund_amount_stmt;

SET @payment_orders_refunded_at_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'refunded_at'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN refunded_at BIGINT NULL AFTER granted_at'
  )
);
PREPARE payment_orders_refunded_at_stmt FROM @payment_orders_refunded_at_sql;
EXECUTE payment_orders_refunded_at_stmt;
DEALLOCATE PREPARE payment_orders_refunded_at_stmt;

SET @payment_orders_closed_at_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'closed_at'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN closed_at BIGINT NULL AFTER refunded_at'
  )
);
PREPARE payment_orders_closed_at_stmt FROM @payment_orders_closed_at_sql;
EXECUTE payment_orders_closed_at_stmt;
DEALLOCATE PREPARE payment_orders_closed_at_stmt;

SET @payment_orders_last_query_at_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'last_query_at'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN last_query_at BIGINT NULL AFTER closed_at'
  )
);
PREPARE payment_orders_last_query_at_stmt FROM @payment_orders_last_query_at_sql;
EXECUTE payment_orders_last_query_at_stmt;
DEALLOCATE PREPARE payment_orders_last_query_at_stmt;

SET @payment_orders_last_query_error_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'last_query_error'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN last_query_error VARCHAR(128) NULL AFTER last_query_at'
  )
);
PREPARE payment_orders_last_query_error_stmt FROM @payment_orders_last_query_error_sql;
EXECUTE payment_orders_last_query_error_stmt;
DEALLOCATE PREPARE payment_orders_last_query_error_stmt;

SET @payment_orders_refund_idx_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND INDEX_NAME = 'idx_payment_orders_refund_status_updated'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD KEY idx_payment_orders_refund_status_updated (refund_status, updated_at)'
  )
);
PREPARE payment_orders_refund_idx_stmt FROM @payment_orders_refund_idx_sql;
EXECUTE payment_orders_refund_idx_stmt;
DEALLOCATE PREPARE payment_orders_refund_idx_stmt;

SET @payment_orders_closed_idx_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND INDEX_NAME = 'idx_payment_orders_closed_at'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD KEY idx_payment_orders_closed_at (closed_at)'
  )
);
PREPARE payment_orders_closed_idx_stmt FROM @payment_orders_closed_idx_sql;
EXECUTE payment_orders_closed_idx_stmt;
DEALLOCATE PREPARE payment_orders_closed_idx_stmt;

CREATE TABLE IF NOT EXISTS payment_refunds (
  id BIGINT NOT NULL AUTO_INCREMENT,
  provider VARCHAR(32) NOT NULL DEFAULT 'alipay',
  out_trade_no VARCHAR(64) NOT NULL,
  refund_request_no VARCHAR(80) NOT NULL,
  provider_trade_no VARCHAR(128) NULL,
  amount_cents INT NOT NULL,
  reason VARCHAR(300) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  provider_status VARCHAR(64) NULL,
  error_code VARCHAR(128) NULL,
  requested_by VARCHAR(64) NULL,
  requested_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  refunded_at BIGINT NULL,
  provider_response_json JSON NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uniq_payment_refund_request (provider, refund_request_no),
  KEY idx_payment_refunds_out_trade (out_trade_no, requested_at),
  KEY idx_payment_refunds_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

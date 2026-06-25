SET @payment_orders_client_build_type_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'client_build_type'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN client_build_type VARCHAR(32) NULL AFTER client_platform'
  )
);
PREPARE payment_orders_client_build_type_stmt FROM @payment_orders_client_build_type_sql;
EXECUTE payment_orders_client_build_type_stmt;
DEALLOCATE PREPARE payment_orders_client_build_type_stmt;

SET @payment_orders_client_version_code_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'client_version_code'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN client_version_code INT NULL AFTER client_build_type'
  )
);
PREPARE payment_orders_client_version_code_stmt FROM @payment_orders_client_version_code_sql;
EXECUTE payment_orders_client_version_code_stmt;
DEALLOCATE PREPARE payment_orders_client_version_code_stmt;

SET @payment_orders_is_test_order_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'is_test_order'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN is_test_order TINYINT(1) NOT NULL DEFAULT 0 AFTER client_ip_mask'
  )
);
PREPARE payment_orders_is_test_order_stmt FROM @payment_orders_is_test_order_sql;
EXECUTE payment_orders_is_test_order_stmt;
DEALLOCATE PREPARE payment_orders_is_test_order_stmt;

SET @payment_orders_original_amount_cents_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND COLUMN_NAME = 'original_amount_cents'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD COLUMN original_amount_cents INT NULL AFTER is_test_order'
  )
);
PREPARE payment_orders_original_amount_cents_stmt FROM @payment_orders_original_amount_cents_sql;
EXECUTE payment_orders_original_amount_cents_stmt;
DEALLOCATE PREPARE payment_orders_original_amount_cents_stmt;

SET @payment_orders_test_created_idx_sql := (
  SELECT IF(
    EXISTS(
      SELECT 1
        FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE()
         AND TABLE_NAME = 'payment_orders'
         AND INDEX_NAME = 'idx_payment_orders_test_created'
    ),
    'SELECT 1',
    'ALTER TABLE payment_orders ADD KEY idx_payment_orders_test_created (is_test_order, created_at)'
  )
);
PREPARE payment_orders_test_created_idx_stmt FROM @payment_orders_test_created_idx_sql;
EXECUTE payment_orders_test_created_idx_stmt;
DEALLOCATE PREPARE payment_orders_test_created_idx_stmt;

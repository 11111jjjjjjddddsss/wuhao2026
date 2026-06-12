SET @schema_name := DATABASE();

SET @needs_idx_orders_user_created := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'orders'
    AND index_name = 'idx_orders_user_created'
);
SET @ddl := IF(
  @needs_idx_orders_user_created = 0,
  'CREATE INDEX idx_orders_user_created ON orders (user_id, created_at, order_id)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_idx_gift_card_attempts_success_created_reason := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'gift_card_redemption_attempts'
    AND index_name = 'idx_gift_card_attempts_success_created_reason'
);
SET @ddl := IF(
  @needs_idx_gift_card_attempts_success_created_reason = 0,
  'CREATE INDEX idx_gift_card_attempts_success_created_reason ON gift_card_redemption_attempts (success, created_at, failure_reason)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

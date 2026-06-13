SET @upgrade_credits_expire_at_nullable := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE upgrade_credits MODIFY expire_at BIGINT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'upgrade_credits'
    AND COLUMN_NAME = 'expire_at'
    AND IS_NULLABLE = 'YES'
);
PREPARE upgrade_credits_expire_at_stmt FROM @upgrade_credits_expire_at_nullable;
EXECUTE upgrade_credits_expire_at_stmt;
DEALLOCATE PREPARE upgrade_credits_expire_at_stmt;

UPDATE upgrade_credits
SET expire_at = NULL;

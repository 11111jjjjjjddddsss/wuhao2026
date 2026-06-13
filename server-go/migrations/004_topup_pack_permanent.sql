SET @topup_packs_expire_at_nullable := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE topup_packs MODIFY expire_at BIGINT NULL',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'topup_packs'
    AND COLUMN_NAME = 'expire_at'
    AND IS_NULLABLE = 'YES'
);
PREPARE topup_packs_expire_at_stmt FROM @topup_packs_expire_at_nullable;
EXECUTE topup_packs_expire_at_stmt;
DEALLOCATE PREPARE topup_packs_expire_at_stmt;

UPDATE topup_packs
SET expire_at = NULL
WHERE status = 'active';

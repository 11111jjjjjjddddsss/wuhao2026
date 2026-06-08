SET @account_phone_ciphertext_exists := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'app_accounts'
    AND COLUMN_NAME = 'phone_ciphertext'
);
SET @stmt := IF(
  @account_phone_ciphertext_exists = 0,
  'ALTER TABLE app_accounts ADD COLUMN phone_ciphertext VARCHAR(512) NULL AFTER phone_mask',
  'SELECT 1'
);
PREPARE s FROM @stmt;
EXECUTE s;
DEALLOCATE PREPARE s;

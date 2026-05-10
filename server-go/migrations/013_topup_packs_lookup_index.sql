SET @topup_packs_lookup_index_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'topup_packs'
    AND index_name = 'idx_topup_packs_user_status_expire_created'
);

SET @topup_packs_lookup_index_sql = IF(
  @topup_packs_lookup_index_exists = 0,
  'ALTER TABLE topup_packs ADD KEY idx_topup_packs_user_status_expire_created (user_id, status, expire_at, created_at)',
  'DO 0'
);
PREPARE topup_packs_lookup_index_stmt FROM @topup_packs_lookup_index_sql;
EXECUTE topup_packs_lookup_index_stmt;
DEALLOCATE PREPARE topup_packs_lookup_index_stmt;

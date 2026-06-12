SET @schema_name := DATABASE();

SET @needs_idx_app_accounts_created := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'app_accounts'
    AND index_name = 'idx_app_accounts_created'
);
SET @ddl := IF(
  @needs_idx_app_accounts_created = 0,
  'CREATE INDEX idx_app_accounts_created ON app_accounts (created_at, user_id)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_idx_user_entitlement_tier_expire := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'user_entitlement'
    AND index_name = 'idx_user_entitlement_tier_expire'
);
SET @ddl := IF(
  @needs_idx_user_entitlement_tier_expire = 0,
  'CREATE INDEX idx_user_entitlement_tier_expire ON user_entitlement (tier, tier_expire_at, user_id)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_idx_daily_usage_day_used := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'daily_usage'
    AND index_name = 'idx_daily_usage_day_used'
);
SET @ddl := IF(
  @needs_idx_daily_usage_day_used = 0,
  'CREATE INDEX idx_daily_usage_day_used ON daily_usage (day_cn, used, user_id)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_idx_quota_ledger_created := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'quota_ledger'
    AND index_name = 'idx_quota_ledger_created'
);
SET @ddl := IF(
  @needs_idx_quota_ledger_created = 0,
  'CREATE INDEX idx_quota_ledger_created ON quota_ledger (created_at, source, user_id)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_idx_topup_packs_active := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'topup_packs'
    AND index_name = 'idx_topup_packs_active'
);
SET @ddl := IF(
  @needs_idx_topup_packs_active = 0,
  'CREATE INDEX idx_topup_packs_active ON topup_packs (remaining, expire_at, user_id)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_idx_upgrade_credits_active := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'upgrade_credits'
    AND index_name = 'idx_upgrade_credits_active'
);
SET @ddl := IF(
  @needs_idx_upgrade_credits_active = 0,
  'CREATE INDEX idx_upgrade_credits_active ON upgrade_credits (remaining, expire_at, user_id)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

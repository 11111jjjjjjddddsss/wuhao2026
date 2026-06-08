SET @schema_name := DATABASE();

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'user_entitlement'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE user_entitlement CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'daily_usage'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE daily_usage CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'quota_ledger'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE quota_ledger CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'topup_packs'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE topup_packs CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'upgrade_credits'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE upgrade_credits CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'session_ab'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE session_ab CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'orders'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE orders CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'session_round_ledger'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE session_round_ledger CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'session_round_archive'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE session_round_archive CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'chat_stream_inflight'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE chat_stream_inflight CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'session_generation'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE session_generation CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_collation_fix := (
  SELECT COUNT(*)
  FROM information_schema.tables t
  LEFT JOIN information_schema.columns c
    ON c.table_schema = t.table_schema
   AND c.table_name = t.table_name
   AND c.collation_name IS NOT NULL
  WHERE t.table_schema = @schema_name
    AND t.table_name = 'daily_agri_cards'
    AND (t.table_collation <> 'utf8mb4_unicode_ci' OR c.collation_name <> 'utf8mb4_unicode_ci')
);
SET @ddl := IF(@needs_collation_fix > 0, 'ALTER TABLE daily_agri_cards CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

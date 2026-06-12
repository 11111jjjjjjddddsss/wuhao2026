SET @schema_name := DATABASE();

SET @needs_idx_client_app_logs_level_created := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'client_app_logs'
    AND index_name = 'idx_client_app_logs_level_created'
);
SET @ddl := IF(
  @needs_idx_client_app_logs_level_created = 0,
  'CREATE INDEX idx_client_app_logs_level_created ON client_app_logs (level, created_at, id)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_idx_client_app_logs_event_level_created := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'client_app_logs'
    AND index_name = 'idx_client_app_logs_event_level_created'
);
SET @ddl := IF(
  @needs_idx_client_app_logs_event_level_created = 0,
  'CREATE INDEX idx_client_app_logs_event_level_created ON client_app_logs (event, level, created_at, id)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_idx_auth_sessions_revoked_expire := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'auth_sessions'
    AND index_name = 'idx_auth_sessions_revoked_expire'
);
SET @ddl := IF(
  @needs_idx_auth_sessions_revoked_expire = 0,
  'CREATE INDEX idx_auth_sessions_revoked_expire ON auth_sessions (revoked_at, token_expires_at)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_idx_support_messages_sender_created := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'support_messages'
    AND index_name = 'idx_support_messages_sender_created'
);
SET @ddl := IF(
  @needs_idx_support_messages_sender_created = 0,
  'CREATE INDEX idx_support_messages_sender_created ON support_messages (sender_type, created_at, id)',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

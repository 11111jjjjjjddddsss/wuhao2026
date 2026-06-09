SET @schema_name := DATABASE();

SET @needs_column := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'client_app_logs'
    AND column_name = 'build_type'
);
SET @ddl := IF(@needs_column = 0, 'ALTER TABLE client_app_logs ADD COLUMN build_type VARCHAR(32) NULL AFTER platform', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @needs_index := (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = @schema_name
    AND table_name = 'client_app_logs'
    AND index_name = 'idx_client_app_logs_build_created'
);
SET @ddl := IF(@needs_index = 0, 'CREATE INDEX idx_client_app_logs_build_created ON client_app_logs (build_type, created_at, id)', 'SELECT 1');
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

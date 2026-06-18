SET @schema_name := DATABASE();

SET @needs_pending_memory_jobs_json := (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'session_ab'
    AND column_name = 'pending_memory_jobs_json'
);
SET @ddl := IF(
  @needs_pending_memory_jobs_json = 0,
  'ALTER TABLE session_ab ADD COLUMN pending_memory_jobs_json JSON NULL AFTER pending_retry_b',
  'SELECT 1'
);
PREPARE s FROM @ddl;
EXECUTE s;
DEALLOCATE PREPARE s;

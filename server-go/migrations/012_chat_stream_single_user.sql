DELETE i
FROM chat_stream_inflight i
JOIN chat_stream_inflight j
  ON i.user_id = j.user_id
 AND (
   i.lease_until < j.lease_until
   OR (i.lease_until = j.lease_until AND i.updated_at < j.updated_at)
   OR (i.lease_until = j.lease_until AND i.updated_at = j.updated_at AND i.client_msg_id < j.client_msg_id)
 );

SET @chat_stream_single_user_index_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'chat_stream_inflight'
    AND index_name = 'uk_chat_stream_inflight_user'
);

SET @chat_stream_single_user_sql = IF(
  @chat_stream_single_user_index_exists = 0,
  'ALTER TABLE chat_stream_inflight ADD UNIQUE KEY uk_chat_stream_inflight_user (user_id)',
  'DO 0'
);
PREPARE chat_stream_single_user_stmt FROM @chat_stream_single_user_sql;
EXECUTE chat_stream_single_user_stmt;
DEALLOCATE PREPARE chat_stream_single_user_stmt;

DELETE i
FROM chat_stream_inflight i
JOIN chat_stream_inflight j
  ON i.user_id = j.user_id
 AND (
   i.lease_until < j.lease_until
   OR (i.lease_until = j.lease_until AND i.updated_at < j.updated_at)
   OR (i.lease_until = j.lease_until AND i.updated_at = j.updated_at AND i.client_msg_id < j.client_msg_id)
 );

ALTER TABLE chat_stream_inflight
  ADD UNIQUE KEY uk_chat_stream_inflight_user (user_id);

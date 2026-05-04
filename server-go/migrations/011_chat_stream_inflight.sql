CREATE TABLE IF NOT EXISTS chat_stream_inflight (
  user_id VARCHAR(128) NOT NULL,
  client_msg_id VARCHAR(128) NOT NULL,
  lease_token VARCHAR(64) NOT NULL,
  lease_until BIGINT NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (user_id, client_msg_id),
  KEY idx_chat_stream_inflight_lease (lease_until)
);

CREATE TABLE IF NOT EXISTS session_round_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(128) NOT NULL,
  session_id VARCHAR(128) NOT NULL,
  client_msg_id VARCHAR(128) NOT NULL,
  created_at BIGINT NOT NULL,
  UNIQUE KEY uk_session_round_msg (user_id, session_id, client_msg_id)
);


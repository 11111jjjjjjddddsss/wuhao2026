ALTER TABLE session_round_ledger
  DROP INDEX uk_session_round_msg,
  DROP COLUMN IF EXISTS session_id,
  ADD UNIQUE KEY uk_session_round_msg (user_id, client_msg_id);

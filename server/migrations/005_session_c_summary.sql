ALTER TABLE session_ab
  ADD COLUMN IF NOT EXISTS c_summary TEXT NULL AFTER b_summary;

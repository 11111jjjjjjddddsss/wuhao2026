ALTER TABLE upgrade_credits
  MODIFY expire_at BIGINT NULL;

UPDATE upgrade_credits
SET expire_at = NULL;

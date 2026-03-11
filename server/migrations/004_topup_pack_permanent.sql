ALTER TABLE topup_packs
  MODIFY expire_at BIGINT NULL;

UPDATE topup_packs
SET expire_at = NULL
WHERE status = 'active';

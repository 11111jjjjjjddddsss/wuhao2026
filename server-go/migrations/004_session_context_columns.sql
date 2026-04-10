ALTER TABLE session_ab
  ADD COLUMN IF NOT EXISTS last_region VARCHAR(255) NULL,
  ADD COLUMN IF NOT EXISTS last_region_source ENUM('gps','ip','none') NULL,
  ADD COLUMN IF NOT EXISTS last_region_reliability ENUM('reliable','unreliable') NULL,
  ADD COLUMN IF NOT EXISTS last_seen_at BIGINT NULL;

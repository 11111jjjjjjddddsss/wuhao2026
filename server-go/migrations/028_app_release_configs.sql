CREATE TABLE IF NOT EXISTS app_release_configs (
  platform VARCHAR(32) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  latest_version_code INT NOT NULL DEFAULT 0,
  latest_version_name VARCHAR(64) NOT NULL DEFAULT '',
  apk_url TEXT NOT NULL,
  apk_sha256 VARCHAR(64) NOT NULL DEFAULT '',
  release_notes TEXT NOT NULL,
  force_update TINYINT(1) NOT NULL DEFAULT 0,
  file_size_bytes BIGINT NOT NULL DEFAULT 0,
  updated_by VARCHAR(64) NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

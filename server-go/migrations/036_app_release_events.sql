CREATE TABLE IF NOT EXISTS app_release_events (
  id BIGINT NOT NULL AUTO_INCREMENT,
  platform VARCHAR(32) NOT NULL,
  action VARCHAR(32) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  latest_version_code INT NOT NULL DEFAULT 0,
  latest_version_name VARCHAR(64) NOT NULL DEFAULT '',
  apk_url TEXT NOT NULL,
  apk_sha256 VARCHAR(64) NOT NULL DEFAULT '',
  release_notes TEXT NOT NULL,
  force_update TINYINT(1) NOT NULL DEFAULT 0,
  file_size_bytes BIGINT NOT NULL DEFAULT 0,
  actor VARCHAR(64) NOT NULL DEFAULT '',
  created_at BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_app_release_events_platform_created (platform, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

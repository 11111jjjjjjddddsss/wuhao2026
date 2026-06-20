CREATE TABLE IF NOT EXISTS support_upload_ownership (
  object_name VARCHAR(255) NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  purpose VARCHAR(32) NOT NULL DEFAULT 'support',
  created_at BIGINT NOT NULL,
  PRIMARY KEY (object_name),
  KEY idx_support_upload_ownership_user (user_id, object_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO support_upload_ownership(object_name, user_id, purpose, created_at)
SELECT
  SUBSTRING(j.image_url, LOCATE('/uploads/', j.image_url) + CHAR_LENGTH('/uploads/')) AS object_name,
  messages.user_id,
  'support',
  COALESCE(messages.created_at, UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000)
FROM support_messages AS messages
JOIN JSON_TABLE(
  COALESCE(messages.image_urls_json, JSON_ARRAY()),
  '$[*]' COLUMNS(image_url VARCHAR(512) PATH '$')
) AS j
WHERE messages.user_id <> ''
  AND LOCATE('/uploads/support/', j.image_url) > 0
  AND SUBSTRING(j.image_url, LOCATE('/uploads/', j.image_url) + CHAR_LENGTH('/uploads/')) REGEXP '^support/[0-9a-fA-F]{32}\\.jpg$';

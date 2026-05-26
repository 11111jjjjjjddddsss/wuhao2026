CREATE TABLE IF NOT EXISTS session_generation (
  user_id VARCHAR(191) NOT NULL,
  generation INT NOT NULL DEFAULT 0,
  cleared_at BIGINT NOT NULL DEFAULT 0,
  updated_at BIGINT NOT NULL,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

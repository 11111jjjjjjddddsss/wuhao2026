-- 会话 A/B 表（PolarDB/MySQL 兼容）
-- 真相落库：GET snapshot / POST append-a / POST update-b 均读写此表；服务重启后数据不丢。
-- 规则：A 可累计 >24；仅当 POST update-b 成功后才清空 A；B 失败不清 A。

CREATE TABLE IF NOT EXISTS session_ab (
  user_id     VARCHAR(128) NOT NULL,
  session_id  VARCHAR(128) NOT NULL,
  b_summary   TEXT         NOT NULL DEFAULT '',
  a_rounds_json LONGTEXT   NOT NULL DEFAULT '[]' COMMENT 'JSON array of {user, assistant}',
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

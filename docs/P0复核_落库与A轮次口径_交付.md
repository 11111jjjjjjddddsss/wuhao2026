# P0 复核 — 落库 + A 轮次口径 — 交付与自查

## 1) 禁止 SESSION_STORE 作为真相：落库（PolarDB/MySQL）

### 建表 SQL

见 **upload-server/sql/session_ab.sql**：

```sql
CREATE TABLE IF NOT EXISTS session_ab (
  user_id       VARCHAR(128) NOT NULL,
  session_id    VARCHAR(128) NOT NULL,
  b_summary     TEXT         NOT NULL DEFAULT '',
  a_rounds_json LONGTEXT     NOT NULL DEFAULT '[]',
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (user_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 三接口真实读写

- **GET /api/session/snapshot**：`SELECT b_summary, a_rounds_json FROM session_ab WHERE user_id=? AND session_id=?`；解析 JSON 得到全量 A，再 `slice(-24)` 得到 for_ui；返回 `{ b_summary, a_rounds_full, a_rounds_for_ui }`。
- **POST /api/session/append-a**：`SELECT a_rounds_json` → 解析、push 一轮 → `INSERT ... ON DUPLICATE KEY UPDATE a_rounds_json=?`，**只追加，不清 A**。
- **POST /api/session/update-b**：`INSERT ... ON DUPLICATE KEY UPDATE b_summary=?, a_rounds_json='[]'`，**仅在此接口成功时清空 A**；B 失败不调此接口，故不清 A。

环境变量：`DB_HOST` / `DB_USER` / `DB_PASSWORD` / `DB_DATABASE`（或 `POLARDB_*`）。未配置时 session 接口返回 503。

### 重启前后 snapshot 一致（日志/证据）

- **操作**：先 POST append-a 若干轮 → GET snapshot 得到 a_rounds_full 长度 N → 重启进程 → 再次 GET snapshot。
- **预期**：重启后 GET snapshot 仍返回相同 b_summary、相同 a_rounds_full 长度 N。
- **日志示例**：
  - 重启前：`[SESSION] GET snapshot user_id=U1 session_id=S1 b_len=0 a_rounds_full=5 a_rounds_for_ui=5`
  - 重启后：`[SESSION] GET snapshot user_id=U1 session_id=S1 b_len=0 a_rounds_full=5 a_rounds_for_ui=5`

---

## 2) A 轮次口径：全量保留直到 B 成功

### 规则

- A 可累计 **>24**（如 26 轮）；仅当 **B 写入成功**（POST update-b 成功）后才清 A；B 失败不清 A。
- snapshot 返回 **a_rounds_full**（全量）+ **a_rounds_for_ui**（最近 24）；客户端 B 提取用 full，UI 注入用 for_ui。

### 接口返回样例（snapshot JSON）

```json
{
  "b_summary": "当前会话 B 摘要内容",
  "a_rounds_full": [
    { "user": "用户第1条", "assistant": "助手第1条" },
    { "user": "用户第2条", "assistant": "助手第2条" },
    ...
  ],
  "a_rounds_for_ui": [
    { "user": "最近第1条", "assistant": "..." },
    ...
  ]
}
```

（a_rounds_for_ui 为 a_rounds_full 的最近 24 条；若全量不足 24 则与 full 相同。）

### 日志要求

- **append-a**：`[SESSION] POST append-a user_id=U1 session_id=S1 a_rounds=26`（写入后该 session 共 26 轮）。
- **update-b 成功清 A**：`[SESSION] POST update-b user_id=U1 session_id=S1 b_len=xxx a_cleared`；之后 GET snapshot 的 a_rounds_full 长度为 0。
- **B 失败不清 A**：不调用 update-b，故无 a_cleared；下次 GET snapshot 仍为完整累计（如 26 轮）。

### 用例：A 累计 26 轮 → B 提取用 26 轮；B 失败则下次仍 26；B 成功则 A 清空

1. 连续 append-a 至 26 轮。
2. 客户端用 **a_rounds_full（26 轮）** 做 B 提取；若 B 校验失败（如摘要超长），不调 update-b，后端 A 仍为 26 轮；下次 GET snapshot 仍返回 a_rounds_full.length=26。
3. B 提取成功 → 调用 update-b → 后端清空 A；下次 GET snapshot 为 a_rounds_full=[]，b_summary 为新值。

---

## 3) 换机/重装模拟

- 清空本地（或新设备）：不读本地 A/B。
- 仅靠 **GET snapshot** 恢复：用 **a_rounds_for_ui** 渲染最近 24 条；用 **b_summary** 显示 B 摘要；用 **a_rounds_full** 作为当前会话 A 全量缓存（供后续 B 提取）。
- 证据：重启/换机后打开会话，界面显示最近 24 条 + B 摘要正确，且下一轮 B 提取仍能基于全量 A（若未清空）。

---

## 4) 代码落点

| 项 | 位置 |
|----|------|
| 建表 SQL | upload-server/sql/session_ab.sql |
| DB 连接与三接口 | upload-server/server.js（mysql2 pool、ensureSessionTable、GET snapshot / POST append-a / POST update-b） |
| snapshot 结构 | 响应含 a_rounds_full、a_rounds_for_ui |
| 客户端 | SessionSnapshot.kt（a_rounds_full、a_rounds_for_ui）；SessionApi 解析 full/for_ui；ABLayerManager.loadSnapshot 用 full 写 cache；MainActivity 用 for_ui 注入 WebView |

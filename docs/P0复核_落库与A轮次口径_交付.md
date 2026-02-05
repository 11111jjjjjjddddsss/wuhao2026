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

### 必须统一的正确口径（无歧义）

- **A 是“累计队列”**，不会被固定在某个轮数；只要未发生「B 写入成功」，A 就不会被清空，会继续累积。
- **B 提取输入** = 触发时刻 A 的「全量快照」（此刻 A 有多少轮，就用多少轮），不做固定轮数假设。
- **仅当 POST /api/session/update-b 成功写入 B 时**，才允许清空 A；B 失败/超时/空输出/写入失败 → 禁止清 A。

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

（a_rounds_for_ui 为 a_rounds_full 的最近 24 条；若全量不足 24 则与 full 相同。代码逻辑不做任何“限制轮数”处理：snapshot 全量 A + UI 仅取 for_ui 最近 24 展示即可。）

### 日志要求

- **append-a**：`[SESSION] POST append-a user_id=U1 session_id=S1 a_rounds=N`（写入后该 session 共 N 轮，N 随追加变化）。
- **update-b 成功清 A**：`[SESSION] POST update-b user_id=U1 session_id=S1 b_len=xxx a_cleared`；之后 GET snapshot 的 a_rounds_full 长度为 0。
- **B 失败不清 A**：不调用 update-b，故无 a_cleared；下次 GET snapshot 仍为当时 A 的完整累计（是否仍为同一轮数取决于失败后有没有新增 done 轮次，见下例）。

### 用例（举例且随新增轮次变化）

当 A 累计到 26 轮时触发 B 提取，B 输入为**当时 A 的 26 轮全量快照**；若提取失败，则不清空 A。之后：

- **若失败后没有新增「完整完成轮次」**（done=true 才写 A）：再次触发时输入仍为 26 轮。
- **若失败后新增了 n 轮完整完成轮次**：再次触发时输入为 **26+n** 轮（全量快照随 A 累积变化）。

**仅当 update-b 成功写入后，才清空 A。**

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

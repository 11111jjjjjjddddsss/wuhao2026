# App 自动日志接收

最后更新：2026-06-03

## 当前定位

这是给后续后台监控面板预留的最小自动日志骨架，不是完整监控后台，也不是用户手动上传日志。

当前主链：
- Android 在关键失败点自动调用 `POST /api/app/logs`
- 后端走现有用户鉴权，写入 `client_app_logs` 表，并同步打一条结构化服务日志
- 接口有 8KiB body 上限、字段长度限制和短期限流：默认每个 `user_id + IP` 10 分钟 60 次，配置 Redis 后跨进程共享，未配置 Redis 时回退单进程内限流
- 后端已提供只读内部查询入口 `GET /internal/app/logs`，暂复用 `SUPPORT_ADMIN_SECRET` 保护，用于运维和后续后台监控面板接入；它不是完整后台账号 / 权限系统
- 后续管理后台或 SLS 可以基于这张表 / 服务日志做查询、统计和告警

## 当前自动上报事件

- `session.snapshot_failed`
- `session.snapshot_parse_failed`
- `chat.stream_interrupted`
- `chat.background_stream_failed`
- `image.upload_failed`
- `support.send_failed`
- `app_update.check_failed`
- `app_update.parse_failed`

## 隐私边界

Android 只上报结构化错误信息：
- App 版本号 / 版本名
- Android 系统版本
- 设备型号
- 事件名、等级、短消息
- 少量错误分类字段，例如 `reason`、`http_status`、`image_count`、`text_length`

禁止上报：
- 聊天正文
- AI 回复全文
- 图片内容或图片 URL
- 手机号、token、模型 Key、数据库密码
- 用户主动填写的反馈正文

服务端也会限制单次请求大小、事件名长度、消息长度和 attrs 大小。

## 限流参数

- `CLIENT_APP_LOG_RATE_LIMIT_WINDOW_SECONDS`：默认 600 秒
- `CLIENT_APP_LOG_RATE_LIMIT_MAX_HITS`：默认 60 次
- `CLIENT_APP_LOG_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：默认 600 秒
- 配置 Redis 后限流 key 只保存 `user_id` hash 和 IP hash，不保存明文手机号、token、聊天正文、图片内容或用户反馈正文

## 后续接后台面板

第一版面板只建议做：
- 按时间筛选错误事件
- 按事件名聚合数量
- 按用户查最近失败事件
- 看 App 版本、系统版本、设备型号分布
- 导出或复制单条事件用于排障

不要把这套自动日志当客服对话；用户需要补充说明仍走“帮助与反馈”。

## 内部查询接口

`GET /internal/app/logs`

鉴权：

- Header `X-Support-Admin-Secret: <SUPPORT_ADMIN_SECRET>`
- 或 `Authorization: Bearer <SUPPORT_ADMIN_SECRET>`

查询参数：

- `since_ms`：起始时间毫秒时间戳，默认最近 24 小时
- `limit`：返回明细条数，默认 100，最大 200
- `user_id`：可选，按用户过滤
- `event`：可选，按事件名过滤
- `level`：可选，`info` / `warn` / `error`

返回：

- `logs`：按 `created_at DESC, id DESC` 返回明细
- `summary`：按 `event + level` 聚合数量，最多 50 组
- `filter`：本次实际使用的过滤条件

示例：

```bash
curl -H "X-Support-Admin-Secret: $SUPPORT_ADMIN_SECRET" \
  "http://127.0.0.1:3000/internal/app/logs?level=error&limit=50"
```

该接口只读，不返回聊天正文、AI 回复全文、图片内容 / URL、手机号、token 或模型 Key。

## SQL 查询示例

```sql
SELECT event, level, COUNT(*) AS count
FROM client_app_logs
WHERE created_at >= ?
GROUP BY event, level
ORDER BY count DESC;
```

```sql
SELECT id, user_id, level, event, message, attrs_json, app_version_code, os_version, device_model, created_at
FROM client_app_logs
WHERE user_id = ?
ORDER BY created_at DESC, id DESC
LIMIT 50;
```

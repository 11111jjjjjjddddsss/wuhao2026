# App 自动日志接收

最后更新：2026-06-12

## 当前定位

这是后台监控面板和 SLS 最小告警共用的自动日志骨架，不是完整告警中心，也不是用户手动上传日志。

当前主链：
- Android 在关键失败点自动调用 `POST /api/app/logs`
- 登录后日志走现有用户鉴权，写入 `client_app_logs` 表，并同步打一条结构化服务日志
- 登录前认证失败日志走 `POST /api/app/logs/preauth`，只允许 `auth.` 前缀事件，统一写成 `user_id=preauth`，用于排查一键登录 / 短信登录还没拿到账号 token 前的失败
- Android 现在有最小闪退补报：进程崩溃时只在本机 SharedPreferences 保存异常类型、顶层代码位置、登录阶段和时间戳等安全摘要；下次启动后自动上报。未登录 / 登录页阶段崩溃走 `auth.app_crash` 预登录日志，已登录后的普通运行崩溃走 `app.crash`。待补报记录不会在第一次上传前就删除，最多保留 3 次上报尝试，attrs 会带 `report_attempt`
- 接口有 8KiB body 上限、字段长度限制和短期限流：默认每个 `user_id + IP` 10 分钟 60 次，配置 Redis 后跨进程共享，未配置 Redis 时回退单进程内限流
- Android 端和后端都会按敏感 attr key 和敏感 value 过滤，丢弃 `phone / token / url / uri / body / message / content` 等字段名对应的值，也会丢弃包含 URL、token、AccessKey、手机号等敏感文本的普通字段值；Android 图片上传 DEBUG 日志也只打印脱敏 URL 和响应长度
- 后端已提供只读内部查询入口 `GET /internal/app/logs`，暂复用 `SUPPORT_ADMIN_SECRET` 保护；第一版网页后台另提供 `GET /admin-api/v1/app-logs`，走后台账号 session / CSRF / 角色校验。两个查询入口都支持按精确 `event`、事件前缀 `event_prefix`、平台、包类型 `build_type`、App 版本号 / 版本名、Android 系统版本、设备型号和等级过滤，精确事件名优先于前缀筛选
- `client_app_logs` 已补面向后台监控和排障的 `level + created_at`、`event + level + created_at` 索引，便于最近 24 小时错误、登录整组事件、检查更新整组事件和按版本 / 机型筛选；索引只优化查询，不改变日志脱敏和保留边界
- SLS 已接入 Go 服务 JSON 日志、Nginx error log 和 5 条 AlertHub 最小告警；5 条应用告警已绑定邮件行动策略和最小仪表盘；后续仍要补更细的版本 / 设备 / 地区聚合趋势

## 当前自动上报事件

- `session.snapshot_failed`
- `session.snapshot_parse_failed`
- `chat.stream_interrupted`
- `chat.background_stream_failed`
- `image.upload_failed`
- `support.send_failed`
- `app_update.check_started`
- `app_update.available`
- `app_update.no_update`
- `app_update.check_failed`
- `app_update.install_permission_required`
- `app_update.download_started`
- `app_update.download_failed`
- `app_update.install_intent_failed`
- `app_update.install_started`
- `auth.fusion_env_blocked`
- `auth.fusion_env_warning`
- `auth.fusion_activity_unavailable`
- `auth.fusion_scene_cancelled`
- `auth.fusion_token_failed`
- `auth.fusion_token_refresh_failed`
- `auth.fusion_sdk_init_failed`
- `auth.fusion_sdk_token_auth_failed`
- `auth.fusion_callback_attach_failed`
- `auth.fusion_scene_start_failed`
- `auth.fusion_ui_model_null`
- `auth.fusion_ui_config_failed`
- `auth.fusion_empty_verify_token`
- `auth.fusion_verify_duplicate`
- `auth.fusion_verify_failed`
- `auth.fusion_verify_failed_ignored`
- `auth.fusion_get_phone_for_verification`
- `auth.fusion_verify_interrupt`
- `auth.fusion_login_failed`
- `auth.fusion_timeout`
- `auth.fusion_template_finished`
- `auth.fusion_template_finish_ignored`
- `auth.fusion_halfway_unexpected`
- `auth.fusion_auth_event`
- `auth.fusion_protocol_url_unavailable`
- `auth.fusion_protocol_navigation_blocked`
- `auth.fusion_protocol_load_failed`
- `auth.login_network_failed`
- `auth.sms_send_failed`
- `auth.sms_login_failed`
- `auth.logout_failed`
- `account.deletion_request_failed`
- `entitlement.fetch_failed`
- `gift_card.redeem_failed`
- `today_agri.fetch_failed`
- `today_agri.recent_fetch_failed`
- `support.summary_fetch_failed`
- `support.messages_fetch_failed`
- `support.mark_read_failed`
- `session.clear_failed`
- `auth.app_crash`
- `app.crash`

## 隐私边界

Android 只上报结构化错误信息：
- App 版本号 / 版本名
- App 包类型：`debug` / `release`
- Android 系统版本
- 设备型号
- 事件名、等级、短消息
- 少量错误分类字段，例如 `reason`、`http_status`、`image_count`、`text_length`
- 闪退补报只保存异常类名、顶层类 / 方法 / 行号、线程名、登录阶段、崩溃时间和安全的上报尝试次数，不保存完整堆栈正文

禁止上报：
- 聊天正文
- AI 回复全文
- 图片内容或图片 URL
- 手机号、token、模型 Key、数据库密码
- 用户主动填写的反馈正文

服务端也会限制单次请求大小、事件名长度、消息长度和 attrs 大小。
当前服务端会保存脱敏后的 `masked_ip`，用于排查同一网络出口的异常失败聚集；内部查询只返回 masked IP，不返回完整 IP。
账号注销、历史删除、自动日志保留天数和批量清理策略仍需在公开运营前明确，不能把这张表当成无限期保存用户运行痕迹的仓库。

## 限流参数

- `CLIENT_APP_LOG_RATE_LIMIT_WINDOW_SECONDS`：默认 600 秒
- `CLIENT_APP_LOG_RATE_LIMIT_MAX_HITS`：默认 60 次
- `CLIENT_APP_LOG_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：默认 600 秒
- 配置 Redis 后限流 key 只保存 `user_id` hash 和 IP hash，不保存明文手机号、token、聊天正文、图片内容或用户反馈正文；登录前日志统一用固定 `preauth` 作为 user_id 参与限流，不保存手机号或 verify token

## 后续接后台面板

第一版网页后台已提供只读查询；监控面板已单独聚合最近 24 小时登录排障数据，展示认证失败、一键登录环境预检、短信失败、登录前日志数量、闪退补报和 Top 事件，并提供按钮直达 App 日志筛选。`auth.fusion_env_blocked` 表示 App 前置判断无网络、无 SIM、SIM 未就绪、VPN / 系统代理或没有可用移动数据，一键登录不应继续硬拉 SDK；`auth.fusion_env_warning` 表示 4G+WiFi 或当前活动网络非蜂窝但移动数据可用，App 会放行一键登录尝试；`auth.login_network_failed` 表示登录请求本身网络失败；`auth.fusion_token_refresh_failed` 表示 SDK 场景内刷新融合认证 token 超时或不可用；`auth.fusion_empty_verify_token` 表示 100001 最终 `onVerifySuccess` 回来但 token 为空，App 会立即回落验证码登录，不再等 30 秒超时。后台“登录排障”卡会把这些事件纳入 `auth.*` 整组筛选，待处理事项也会提示先查 SIM / 默认数据卡 / 移动数据 / VPN / 系统代理 / 生产 API 可达性，避免把手机环境问题、代理问题、SDK 授权页问题和服务端 token 校验问题混成一个“登录失败”；验证码登录只要生产 HTTPS 后端可达，在 WiFi 或代理环境下也应可用。后台排障按钮既支持用 `event_prefix=auth.` 查看全部登录相关日志，也会按真实上报事件拆开：取 fusion token、SDK 初始化、授权页拉起、SDK token auth、最终取号、服务端换号、超时、授权页未完成、短信发送和短信登录校验。监控面板也已单独聚合最近 24 小时 `app_update.*` 检查更新排障日志，展示检查失败、下载失败、安装页失败、安装未知应用权限确认和 Top 事件；排障按钮支持 `event_prefix=app_update.` 查看全部检查更新日志，也支持按具体阶段精确过滤。App 日志页还可按 `platform`、`build_type`、`app_version_code`、`app_version_name`、`os_version`、`device_model` 过滤，方便上线前真机回归时区分测试包 / 正式包、具体版本、系统版本或机型问题。下载失败 attrs 只带安全 reason，例如网络 / HTTP、非 HTTPS 跳转、文件过大、大小不一致、SHA-256 不一致、包名不一致或 `versionCode` 未升版本，不带 APK URL、SHA-256 原文或安装包内容。后续继续补：
- SDK 协议页承接页会额外上报 `auth.fusion_protocol_url_unavailable`、`auth.fusion_protocol_navigation_blocked`、`auth.fusion_protocol_load_failed`，只带 URL 是否缺失 / 非法、导航 scheme、主 frame 标记和 WebView 错误码，不上传完整协议 URL。
- 更细的版本 / 设备 / 地区聚合趋势
- SLS 趋势图、第一封告警邮件送达确认和复制单条事件用于排障

不要把这套自动日志当客服对话；用户需要补充说明仍走“帮助与反馈”。

## 内部查询接口

`GET /internal/app/logs`

鉴权：

- Header `X-Support-Admin-Secret: <SUPPORT_ADMIN_SECRET>`
- 或 `Authorization: Bearer <SUPPORT_ADMIN_SECRET>`
- 可选 `X-Admin-Actor: <operator>`，只用于审计日志标记操作人，不替代鉴权
- 内部 secret 入口默认按 scope + IP 做 10 分钟 120 次短期限流，配置 Redis 时跨实例共享

查询参数：

- `since_ms`：起始时间毫秒时间戳，默认最近 24 小时
- `limit`：返回明细条数，默认 100，最大 200
- `user_id`：可选，按用户过滤
- `event`：可选，按事件名过滤
- `event_prefix`：可选，按事件名前缀过滤，例如 `auth.` 或 `app_update.`；如果同时传 `event`，以精确 `event` 为准
- `platform`：可选，按平台过滤，当前 Android 默认为 `android`
- `build_type`：可选，按包类型过滤，Android 当前上报 `debug` 或 `release`
- `app_version_code`：可选，按 App `versionCode` 精确过滤
- `app_version_name`：可选，按 App 版本名前缀过滤
- `os_version`：可选，按 Android 系统版本前缀过滤
- `device_model`：可选，按设备型号前缀过滤
- `level`：可选，`info` / `warn` / `error`

排查登录前失败时，可以用 `user_id=preauth` 过滤全量登录前日志；若要看整条登录链，优先用 `event_prefix=auth.`；若要看具体阶段，再按 `event=auth.fusion_token_failed`、`event=auth.fusion_token_refresh_failed`、`event=auth.fusion_sdk_init_failed`、`event=auth.fusion_scene_start_failed`、`event=auth.fusion_sdk_token_auth_failed`、`event=auth.fusion_empty_verify_token`、`event=auth.fusion_verify_failed`、`event=auth.fusion_login_failed`、`event=auth.fusion_timeout`、`event=auth.fusion_protocol_url_unavailable`、`event=auth.fusion_protocol_load_failed`、`event=auth.sms_send_failed` 或 `event=auth.sms_login_failed` 精确过滤。排查检查更新时可先用 `event_prefix=app_update.` 看整组检查 / 下载 / 安装日志。

返回：

- `logs`：按 `created_at DESC, id DESC` 返回明细，包含脱敏 `masked_ip`
- `summary`：按 `event + level` 聚合数量，最多 50 组
- `filter`：本次实际使用的过滤条件

示例：

```bash
curl -H "X-Support-Admin-Secret: $SUPPORT_ADMIN_SECRET" \
  --resolve api.nongjiqiancha.cn:443:127.0.0.1 \
  "https://api.nongjiqiancha.cn/internal/app/logs?level=error&limit=50"
```

```bash
curl -H "X-Support-Admin-Secret: $SUPPORT_ADMIN_SECRET" \
  --resolve api.nongjiqiancha.cn:443:127.0.0.1 \
  "https://api.nongjiqiancha.cn/internal/app/logs?event_prefix=auth.&limit=100"
```

该接口只读，不返回聊天正文、AI 回复全文、图片内容 / URL、手机号、token 或模型 Key。成功或失败查询都会写入最小内部审计日志，只保存动作、过滤条件摘要、返回条数、脱敏 IP、UA 和时间，不保存密钥或日志 attrs 原文以外的额外敏感内容。

## SQL 查询示例

```sql
SELECT event, level, COUNT(*) AS count
FROM client_app_logs
WHERE created_at >= ?
GROUP BY event, level
ORDER BY count DESC;
```

```sql
SELECT id, user_id, masked_ip, level, event, message, attrs_json, app_version_code, os_version, device_model, created_at
FROM client_app_logs
WHERE user_id = ?
ORDER BY created_at DESC, id DESC
LIMIT 50;
```

# 后端数据边界与归属巡检

最后更新：2026-06-15

## 目的

记录农技千查后端数据到底放在哪里、谁是真相、哪些只是短期状态，以及上线前如何只读检查账号资产是否都收敛到 `acct_...`。本 runbook 不记录数据库密码、手机号明文、聊天正文、图片 URL、礼品卡完整码、token 或模型 Key。

## 当前数据真相

- MySQL / RDS 是业务主真相：账号、session、旧 ID 迁移、会员权益、每日额度、额度流水、加油包、升级补偿、订单、聊天 A 层、用户记忆文档、30 天聊天归档、主聊天进行中租约、今日农情、帮助反馈、App 自动日志、后台用户 / session / 审计、礼品卡、注销申请和检查更新配置都在 MySQL
- Redis 只做短期状态和限流：融合认证 token、短信验证码摘要、手机号 / IP 限流、主聊天用户级频控、App 自动日志入口限流、帮助反馈发消息限流和上传限流。Redis 不承载聊天正文、会员资产、额度、订单、礼品卡、图片或长期用户画像
- OSS 只放图片对象：当前 `/upload` 写私有 Bucket `nongjiqiancha-prod`，普通问诊图写 `uploads/` 并通过后端 `/uploads/<file>.jpg` 访问，帮助与反馈图片写 `support/` 并通过后端 `/uploads/support/<file>.jpg` 访问；不把 OSS AK/SK 下发 Android。Bucket 已开启默认服务端 AES256 加密
- SLS / 本地日志只做排障和监控：Go 请求日志、Nginx error log 和 App 自动日志必须脱敏，不写手机号明文、聊天正文、图片 URL、token、AccessKey、模型 Key 或礼品卡完整码
- 手机号只作为登录凭证和回访线索：`app_accounts` 保存 `phone_hash`、`phone_mask` 和加密手机号，业务主键统一是账号ID `acct_...`

## 关键表边界

- `app_accounts`：手机号归一后的账号ID，`phone_hash / phone_mask / phone_ciphertext`
- `auth_sessions`：后端可吊销的 v2 session，正式登录态只应签给 `acct_...`
- `user_id_migrations`：旧本机 UUID 到账号ID的受控迁移映射，`new_user_id` 必须是 `acct_...`，`old_user_id` 不应是 `acct_...`
- `user_entitlement / daily_usage / quota_ledger / topup_packs / upgrade_credits / orders`：会员、额度、加油包、升级补偿和订单资产，必须按账号ID归属
- `session_ab`：当前 A 层窗口和一份自然语言用户记忆文档，物理字段仍是 `b_summary`，对外叫 `memory_document`
- `session_round_archive / session_round_ledger`：最近 30 天问答归档和同 `client_msg_id` 幂等真源
- `chat_stream_inflight`：同一用户活跃主聊天流租约，防重复开模型和并发串账
- `support_messages / support_conversations`：帮助反馈消息和会话状态，必须按账号ID归属；客服图片只是 `image_urls_json` 里的附件 URL，不是聊天记录真源
- `client_app_logs`：App 自动日志，登录前允许 `user_id=preauth`，登录后必须按账号ID归属
- `daily_agri_cards`：今日农情每日卡片，独立于用户聊天、记忆和问诊扣次
- `gift_card_batches / gift_cards / gift_card_redemption_attempts`：礼品卡批次、卡、兑换尝试；卡码用 hash 校验、mask 展示、ciphertext 授权查看，兑换用户必须是账号ID
- `account_deletion_requests`：注销申请，必须按账号ID归属
- `app_release_configs`：检查更新配置，后台维护；环境变量只作为兜底

## 只读巡检脚本

固定入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-backend-data-boundaries.ps1
```

脚本行为：

- 通过 Cloud Assistant 在 ECS 内读取 `/etc/nongjiqiancha/server.env` 的 `MYSQL_URL`
- 支持 `mysql://...` 和 Go `user:pass@tcp(...)/db` 两种 DSN 形态
- 使用临时 `--defaults-extra-file` 调用 mysql client，避免把数据库密码放进命令行参数或输出
- 只输出表计数、活跃 session / 活跃会员 / App 24h 错误计数、今日农情状态计数、`acct_...` 归属异常计数、`acct_...` 孤儿记录和缺加密手机号账号计数
- 额外输出最近 24 小时 App warn / error Top 事件，只展示 `event / level / build_type / app_version_code / count / latest_created_at / latest_created_at_cn`，其中 `latest_created_at_cn` 是北京时间可读时间；不输出日志 attrs、message、IP、手机号、URL、正文或 token
- 不查询手机号明文、聊天正文、反馈正文、图片 URL、礼品卡完整码、token 或模型 Key
- 只要会员、订单、礼品卡、聊天、反馈、日志、注销等需要账号归属的表出现非 `acct_...`，或 `acct_...` 业务记录找不到对应 `app_accounts`，或 `app_accounts.phone_ciphertext` 缺失，脚本失败

## 只读留存与成本巡检

固定入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-data-retention-cost.ps1
```

脚本行为：

- 通过 Cloud Assistant 在 ECS 内部只读查询 MySQL，不输出数据库密码、正文、图片 URL、手机号、token 或密钥。
- 统计 `session_round_archive / client_app_logs / support_messages / admin_audit_logs / session_round_ledger / quota_ledger / orders / gift_card_redemption_attempts / daily_agri_cards` 的行数、最早 / 最新记录时间和表体量。
- 默认把聊天完整归档超过 31 天、App 自动日志超过 90 天、客服文字 / 后台审计 / 幂等 ledger 超过 365 天或单表超过 1GB 作为 attention；`-FailOnWarning` 可在严格巡检里把 attention 变成失败。
- `check-resource-capacity.ps1 -Strict` 已接入该脚本，和云资源、OSS 生命周期、SLS 低成本护栏一起巡检。

2026-06-15 生产只读结果：

- `session_round_archive=12`，最早约 1.5 天前，表体量约 0.109MB。
- `client_app_logs=298`，最早约 6.5 天前，表体量约 0.203MB。
- `support_messages=3`，最早约 1.5 天前，表体量约 0.078MB。
- `admin_audit_logs=454`，最早约 7.6 天前，表体量约 0.219MB。
- 重点追踪表合计约 0.828MB，`warnings=0 / errors=0 / status=ready`。

## 2026-06-15 线上只读结果

- `app_accounts=1 / auth_sessions=11 / auth_sessions_active=5 / user_entitlement=1 / orders=0 / gift_cards=0 / session_ab=1 / session_round_archive=12`
- `client_app_logs=285`，其中 24h error 14、auth warn/error 6，属于当前登录联调和排障日志，不是用户资产
- 24h App warn / error Top 事件主要仍是旧包阶段闪退与认证排障：`app.crash` 最新停在 2026-06-14 17:47，`auth.app_crash` 最新停在 2026-06-14 08:58；用户 2026-06-14 21:31 安装的新 debug 包之后，本轮未见更晚闪退补报
- `daily_agri_cards=7`，ready 6、failed 1
- `admin_users=1 / admin_sessions_active=1 / admin_audit_logs=452`
- 所有账号归属检查均为 0：会员、额度、订单、聊天、帮助反馈、App 日志、礼品卡兑换、注销申请等没有非 `acct_...` 资产归属
- 所有账号完整性检查均为 0：`acct_...` 业务记录均能关联到 `app_accounts`，`app_accounts_missing_phone_ciphertext=0`

## 2026-06-14 线上只读结果

- `app_accounts=1 / auth_sessions=2 / auth_sessions_active=0 / user_entitlement=1 / orders=0 / gift_cards=0 / session_ab=1 / session_round_archive=1`
- `client_app_logs=134`，其中 24h error 5、auth warn/error 6，属于当前登录联调和排障日志，不是用户资产
- 24h App warn / error Top 事件为：`app.crash=4`、`auth.fusion_verify_interrupt=3`、`auth.fusion_env_blocked=1`、`auth.fusion_activity_unavailable=1`、`auth.app_crash=1`
- `daily_agri_cards=6`，ready 5、failed 1
- `admin_users=1 / admin_sessions_active=0 / admin_audit_logs=423`
- 所有账号归属检查均为 0：会员、额度、订单、聊天、帮助反馈、App 日志、礼品卡兑换、注销申请等没有非 `acct_...` 资产归属

## 2026-06-12 线上只读结果（历史基线）

- `app_accounts=0 / auth_sessions=0 / user_entitlement=0 / orders=0 / gift_cards=0 / session_ab=0 / session_round_archive=0`
- `client_app_logs=74`，其中 24h error 11、auth warn/error 19，属于当前登录联调和排障日志，不是用户资产
- 24h App warn / error Top 事件为：`auth.fusion_env_blocked=6`、`auth.app_crash=5`、`auth.sms_send_failed=4`、`auth.sms_login_failed=2`、`auth.fusion_timeout=1`、`auth.fusion_verify_interrupt=1`
- `daily_agri_cards=5`，ready 4、failed 1
- `admin_users=1 / admin_sessions_active=1 / admin_audit_logs=404`
- 所有账号归属检查均为 0：会员、额度、订单、聊天、帮助反馈、App 日志、礼品卡兑换、注销申请等没有非 `acct_...` 资产归属

## 当前注意点

- 线上当前已有首个手机号账号联调记录，但还没有真实付费会员、订单或礼品卡资产；这是继续收紧账号和资产边界的好窗口
- 帮助与反馈图片当前复用 `/upload`，但 Android 会传 `purpose=support`，后端实际写到 OSS `support/` 前缀并按 30 天生命周期过期；普通问诊图仍写 `uploads/` 并按 3 天过期。客服聊天记录正文、发送人、时间和已读状态保存在 MySQL，不随图片生命周期自动删除
- `client_app_logs` 登录前允许 `preauth`，脚本会排除它；除 `preauth` 外，App 日志也应按账号ID归属
- 后续接真实支付前，订单和权益发放必须继续由服务端验签回调 / 对账驱动，不能让 Android 或开发期接口直接改会员资产

# 帮助与反馈 Runbook

最后更新：2026-06-06

本 runbook 记录当前“帮助与反馈”首版的真实入口。首版不是实时 IM，也不是完整工单系统；它是一条按 `user_id` 归属的站内消息线，Android 负责展示 / 发送 / 已读，后台后续管理面板负责读取和回复。代码和接口内部仍沿用 `support` 命名，不影响用户侧文案。

## 数据真源

- 表：`support_messages`
- 迁移：`server-go/migrations/015_support_messages.sql`
- 一条用户会话线按 `user_id` 聚合，不单独建 thread 表
- `sender_type` 当前取值：`user`、`admin`、`system`；`system` 当前用于固定自动确认回复和后续系统提示，不是模型客服
- 设置页红点只看 `sender_type IN ('admin', 'system') AND read_by_user_at IS NULL`
- 图片附件存储在 `image_urls_json`，只保存本后端 `/upload` 返回的公开 HTTPS 图片 URL

## 用户侧接口

所有用户侧接口都走当前 App 鉴权：`Authorization: Bearer ...` 或开发期 `X-User-Id` 兜底。

- `GET /api/support/summary`
  - 返回 `unread_count` 和最近一条消息
  - Android 设置页用它决定“帮助与反馈”行是否显示红点
- `GET /api/support/messages`
  - 返回当前用户最近 100 条帮助与反馈消息，按时间正序
  - Android 进入帮助与反馈页时拉取历史
- `POST /api/support/messages`
  - 请求体：`{"body":"...","images":["https://.../uploads/xxx.jpg"]}`
  - 当前限制正文最多 2000 字
  - 支持纯文字、纯图片或图文混合；单次最多 4 张图片
  - Android 先复用主聊天图片链压缩并上传到 `/upload`，再把返回 URL 写入帮助与反馈消息
  - 默认同一 `user_id + IP` 10 分钟最多 20 条；配置 Redis 时跨进程共享，未配置 Redis 时回退本进程限流。Redis key 只保存 user_id hash 和 IP hash，不保存正文、图片内容、手机号或 token
  - 用户首次提交反馈、提出可规则识别的常见 App 使用问题，或距上一条用户反馈已超过 24 小时再次提交时，后端会额外写入一条 `sender_type=system` 的固定自动回复，响应体会带 `auto_reply`；Android 新版会立即展示并标记已读。该自动回复不调用模型、不承诺 SLA、不替代后台人工回复。当前只按关键词兜住“你好 / 在吗 / 怎么用 / 登录验证码 / 检查更新 / 图片上传 / 会员次数 / 历史记录 / 隐私协议 / 农业问题请回主聊天”等轻量问答；有具体故障内容或图片时，自动回复走正式提交确认口径
- `POST /api/support/read`
  - 请求体可选：`{"last_seen_message_id":"..."}`
  - 把当前用户已加载到的未读后台 / 系统消息标记为已读；传入 `last_seen_message_id` 时，只标记不晚于该消息的后台 / 系统消息
  - Android 成功拉取帮助与反馈页历史后调用，随后设置页红点消失

## 内部后台接口

内部接口必须配置环境变量 `SUPPORT_ADMIN_SECRET`，请求时带 `X-Support-Admin-Secret: <secret>`；也兼容 `Authorization: Bearer <secret>`。可选 `X-Admin-Actor: <operator>` 或 `X-Support-Admin-Actor: <operator>` 用于审计标记。不要把密钥写进仓库。

- `GET /internal/support/messages?user_id=<user_id>`
  - 读取指定用户最近 100 条帮助与反馈消息
  - 查询动作会写入 `admin_audit_logs`，只记录操作元信息和返回条数，不记录反馈正文或图片 URL
- `GET /internal/support/conversations`
  - 查询最近有帮助与反馈消息的用户会话列表
  - 支持 `since_ms` 和 `limit`，默认查最近 30 天，limit 默认 100、最大 200
  - 返回每个用户的最新消息、消息数、用户未读后台 / 系统消息数和 `needs_reply` 标记，方便后续后台列表页接入
  - `needs_reply` 按最新非 `system` 消息判断；自动确认回复不会把“用户待回复”状态盖掉
  - 查询动作会写入 `admin_audit_logs`，只记录筛选条件和返回条数，不记录反馈正文或图片 URL
- `POST /internal/support/messages`
  - 请求体：`{"user_id":"<user_id>","body":"后台回复内容","images":[]}`
  - 写入一条 `sender_type=admin` 消息
  - 用户下次打开 App 设置页或刷新摘要时会看到红点
  - 回复动作会写入 `admin_audit_logs`，记录目标用户、消息 ID、正文字数和图片数量，不记录回复正文、图片 URL、密钥或手机号

## 当前没有的能力

- 没有网页管理后台
- 没有后台账号 / 坐席权限；当前只有最小内部操作审计，不等于正式坐席系统
- 没有工单状态、分配、关闭、搜索和 SLA
- 没有系统通知 / 推送
- 当前后台接口只适合早期内测 / 运维使用；公开运营前至少要补后台账号、角色权限和未处理列表，并把当前最小审计升级到正式后台权限模型下
- OSS 上传后端已接入，`support/` 预留图片生命周期当前按 30 天自动删除；公开运营前仍要验证公网 HTTPS 图片链、帮助与反馈远端图片过期占位和删除 / 保留策略
- 后续账号注销 / 数据删除规则必须明确帮助与反馈消息和图片是否删除、保留多久、由谁操作

后续做统一运营面板时，优先复用上述内部接口或在同一张表上扩展，不要把帮助与反馈历史存到 Android 本地当真源。

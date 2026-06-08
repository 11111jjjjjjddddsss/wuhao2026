# 帮助与反馈 Runbook

最后更新：2026-06-08

本 runbook 记录当前“帮助与反馈”的真实入口。它不是实时 IM，也不是完整客服坐席系统；当前是一条按账号ID（底层字段仍叫 `user_id`）归属的站内消息线，Android 负责展示 / 发送 / 已读，管理后台负责会话队列、读取、回复、关闭和重开。代码和接口内部仍沿用 `support` 命名，不影响用户侧文案。

## 数据真源

- 表：`support_messages`、`support_conversations`
- 迁移：`server-go/migrations/015_support_messages.sql`、`server-go/migrations/026_gift_card_codes_and_support_conversations.sql`
- 一条用户会话线按账号ID聚合，`support_conversations.user_id` 是轻量会话状态主键，不单独开放多 thread
- `sender_type` 当前取值：`user`、`admin`、`system`；`system` 当前用于固定自动确认回复和后续系统提示，不是模型客服
- `support_conversations.status` 当前取值：`open`、`replied`、`closed`；用户发新消息会把会话重新打开为 `open`，后台回复会标为 `replied`，后台可手动关闭或重开
- `assigned_to` 当前记录最后处理人，`note` 当前用于关闭备注；不要写手机号全文、密钥、token、图片 URL 或敏感正文
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
  - 用户首次提交反馈，或距上一条通用反馈已超过 24 小时时，后端会额外写入一条 `sender_type=system` 的固定自动回复，响应体会带 `auto_reply`；Android 新版会立即展示并标记已读。该自动回复不调用模型、不承诺 SLA、不替代后台人工回复。当前逻辑保持简单：短问候请用户说明问题并提示客服会在本页跟进；纯图片或“见图 / 看图”提示已收到图片并请补充具体问题；其他内容统一走通用兜底，说明本页主要处理 App 使用反馈、后续回复会在本页显示，农业技术咨询可回主聊天页继续问
  - Android 发送反馈时会在输入框上方显示轻量状态：带图先显示“正在上传图片...”，上传完成后显示“正在提交反馈...”；纯文字直接显示“正在提交反馈...”
- `POST /api/support/read`
  - 请求体可选：`{"last_seen_message_id":"..."}`
  - 把当前用户已加载到的未读后台 / 系统消息标记为已读；传入 `last_seen_message_id` 时，只标记不晚于该消息的后台 / 系统消息
  - Android 成功拉取帮助与反馈页历史后调用，随后设置页红点消失

## 内部后台接口

内部接口必须配置环境变量 `SUPPORT_ADMIN_SECRET`，请求时带 `X-Support-Admin-Secret: <secret>`；也兼容 `Authorization: Bearer <secret>`。内部 secret 入口默认按 scope + IP 做 10 分钟 120 次短期限流，配置 Redis 时跨实例共享。可选 `X-Admin-Actor: <operator>` 或 `X-Support-Admin-Actor: <operator>` 用于审计标记。不要把密钥写进仓库。

- `GET /internal/support/messages?user_id=<user_id>`
  - 读取指定用户最近 100 条帮助与反馈消息
  - 查询动作会写入 `admin_audit_logs`，只记录操作元信息和返回条数，不记录反馈正文或图片 URL
- `GET /internal/support/conversations`
  - 查询最近有帮助与反馈消息的用户会话列表
  - 支持 `since_ms`、`limit`、`status` 和 `query`，默认查最近 30 天，limit 默认 100、最大 200
  - 默认时间窗只限制已回复 / 已关闭等普通队列；待回复 `open` 会话不受 30 天窗口漏掉
  - 返回每个用户的最新消息、消息数、用户未读后台 / 系统消息数、脱敏手机号、`status`、处理人、关闭备注和 `needs_reply` 标记
  - `needs_reply` 按最新非 `system` 消息判断；自动确认回复不会把“用户待回复”状态盖掉
  - 查询动作会写入 `admin_audit_logs`，只记录筛选条件和返回条数，不记录反馈正文或图片 URL
- `POST /internal/support/messages`
  - 请求体：`{"user_id":"<user_id>","body":"后台回复内容","images":[]}`
  - 写入一条 `sender_type=admin` 消息
  - 用户下次打开 App 设置页或刷新摘要时会看到红点
  - 回复动作会写入 `admin_audit_logs`，记录目标用户、消息 ID、正文字数和图片数量，不记录回复正文、图片 URL、密钥或手机号

## 管理后台入口

- `GET /admin-api/v1/support/conversations`
  - 走后台账号 session / CSRF / 角色权限，不暴露内部 shared secret 给浏览器
  - 支持按 `status=open|replied|closed` 和 `query` 筛选；`query` 可查账号ID、脱敏手机号和最近消息
  - 列表排序为待回复优先，其次按最新消息时间倒序，避免大量消息时把未处理会话压到下面
- `GET /admin-api/v1/support/messages?user_id=<user_id>`
  - 读取指定账号ID的最近消息，按时间正序展示
- `POST /admin-api/v1/support/messages`
  - 后台回复用户，会写 `sender_type=admin` 消息、更新会话状态并写审计
- `POST /admin-api/v1/support/conversations/status`
  - 请求体：`{"user_id":"acct_...","status":"open|replied|closed","note":"可选"}`
  - 用于重开待回复、标已回复和关闭会话；关闭可写备注，但不能写手机号全文、密钥或其他敏感信息

## 当前没有的能力

- 当前已有网页管理后台、后台账号、角色权限、会话列表、详情、回复、状态筛选、搜索、关闭和重开。
- 当前仍缺正式坐席分配、标签、站外通知、客服绩效、SLA、自动归档和更细的消息保存 / 删除规则。
- 没有系统通知 / 推送
- 当前后台仍只适合早期内测 / 小规模运营使用；公开大规模运营前至少要补坐席分配、标签和消息保存 / 删除规则，并继续完善后台审计
- OSS 上传后端已接入，Bucket 里已预留 `support/` 30 天生命周期规则，但当前帮助与反馈图片仍复用主聊天 `/upload` -> `/uploads/*.jpg` 链路，实际按 `uploads/` 3 天生命周期处理；公开运营前如需要更长客服追溯期，应新增 support 专用上传目的或接口，再把 Android 帮助与反馈图片切到 `support/`
- 后续账号注销 / 数据删除规则必须明确帮助与反馈消息和图片是否删除、保留多久、由谁操作

后续做统一运营面板时，优先复用上述内部接口或在同一张表上扩展，不要把帮助与反馈历史存到 Android 本地当真源。

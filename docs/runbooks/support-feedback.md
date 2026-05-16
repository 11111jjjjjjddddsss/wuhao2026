# 客服反馈 Runbook

本 runbook 记录当前客服反馈首版的真实入口。首版不是实时 IM，也不是完整工单系统；它是一条按 `user_id` 归属的站内消息线，Android 负责展示 / 发送 / 已读，后台后续管理面板负责读取和回复。

## 数据真源

- 表：`support_messages`
- 迁移：`server-go/migrations/015_support_messages.sql`
- 一条用户会话线按 `user_id` 聚合，不单独建 thread 表
- `sender_type` 当前取值：`user`、`admin`、`system`
- 设置页红点只看 `sender_type IN ('admin', 'system') AND read_by_user_at IS NULL`

## 用户侧接口

所有用户侧接口都走当前 App 鉴权：`Authorization: Bearer ...` 或开发期 `X-User-Id` 兜底。

- `GET /api/support/summary`
  - 返回 `unread_count` 和最近一条消息
  - Android 设置页用它决定“客服反馈”行是否显示红点
- `GET /api/support/messages`
  - 返回当前用户最近 100 条客服消息，按时间正序
  - Android 进入客服反馈页时拉取历史
- `POST /api/support/messages`
  - 请求体：`{"body":"..."}`
  - 当前限制正文最多 2000 字
  - 用于用户向后台发送反馈
- `POST /api/support/read`
  - 把当前用户所有未读客服 / 系统消息标记为已读
  - Android 成功拉取客服页历史后调用，随后设置页红点消失

## 内部后台接口

内部接口必须配置环境变量 `SUPPORT_ADMIN_SECRET`，请求时带 `X-Support-Admin-Secret: <secret>`；也兼容 `Authorization: Bearer <secret>`。不要把密钥写进仓库。

- `GET /internal/support/messages?user_id=<user_id>`
  - 读取指定用户最近 100 条客服消息
- `POST /internal/support/messages`
  - 请求体：`{"user_id":"<user_id>","body":"客服回复内容"}`
  - 写入一条 `sender_type=admin` 消息
  - 用户下次打开 App 设置页或刷新摘要时会看到红点

## 当前没有的能力

- 没有网页管理后台
- 没有客服账号 / 坐席权限
- 没有工单状态、分配、关闭、搜索和 SLA
- 没有图片附件
- 没有系统通知 / 推送

后续做统一运营面板时，优先复用上述内部接口或在同一张表上扩展，不要把客服历史存到 Android 本地当真源。

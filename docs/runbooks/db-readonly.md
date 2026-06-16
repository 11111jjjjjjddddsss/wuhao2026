# 数据库只读排查 Runbook

最后更新：2026-06-12

## 目的

记录 RDS MySQL / MySQL 兼容数据库相关只读排查的当前有效入口。PolarDB 暂只作为后续升级选项；若将来升级，再同步更新本 runbook。

## 当前现状

- RDS MySQL 实例 `rm-2zes3vmj76p85n8g1` 已运行，数据库 `nongjiqiancha` 和应用账号 `nongji_app` 已创建，RDS 白名单当前只放 `192.168.1.237`
- `server-go` 已从 ECS 通过内网连接 RDS 并完成迁移，当前可见业务表包括 `app_accounts`、`auth_sessions`、`user_id_migrations`、`user_entitlement`、`daily_usage`、`quota_ledger`、`quota_consume_outbox`、`topup_packs`、`upgrade_credits`、`session_ab`、`session_round_ledger`、`session_round_archive`、`chat_stream_inflight`、`daily_agri_cards`、`orders`、`support_messages`、`support_conversations`、`client_app_logs`、`admin_audit_logs`、`admin_users`、`admin_sessions`、`gift_card_batches`、`gift_cards`、`gift_card_redemption_attempts`、`account_deletion_requests`、`app_release_configs`
- 主规则已明确人工查看优先 DMS；Codex 优先只读查询、迁移脚本、备份脚本
- 当前已有账号资产归属只读巡检脚本 [check-backend-data-boundaries.ps1](D:/wuhao/scripts/check-backend-data-boundaries.ps1)，用于查表计数和 `acct_...` 归属异常，不查询手机号明文、聊天正文、反馈正文、图片 URL、礼品卡完整码、token 或模型 Key

## 当前临时查询方式

优先用固定脚本做账号资产归属巡检：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-backend-data-boundaries.ps1
```

脚本通过 Cloud Assistant 在 ECS 内读取 `/etc/nongjiqiancha/server.env` 的 `MYSQL_URL`，使用临时 mysql defaults file 连接 RDS，避免把数据库密码写进命令参数或输出。脚本只输出表计数和归属异常计数。

临时自定义查询也可通过 Cloud Assistant 在 ECS 内用 `/etc/nongjiqiancha/server.env` 的连接信息执行，但命令不得打印数据库密码。

示例只查表名：

```bash
SHOW TABLES;
```

## 后续补充要求

- 继续补正式只读数据库账号，避免长期用应用账号做只读排查
- 后续若新增会员、支付、客服、内容或运营表，要把对应账号归属检查补进 [check-backend-data-boundaries.ps1](D:/wuhao/scripts/check-backend-data-boundaries.ps1)
- 敏感表查询仍只能看计数、状态和脱敏字段；不要导出手机号明文、聊天正文、反馈正文、图片 URL、礼品卡完整码、token 或模型 Key

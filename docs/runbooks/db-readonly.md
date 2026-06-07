# 数据库只读排查 Runbook

最后更新：2026-06-07

## 目的

记录 RDS MySQL / MySQL 兼容数据库相关只读排查的当前有效入口。PolarDB 暂只作为后续升级选项；若将来升级，再同步更新本 runbook。

## 当前现状

- RDS MySQL 实例 `rm-2zes3vmj76p85n8g1` 已运行，数据库 `nongjiqiancha` 和应用账号 `nongji_app` 已创建，RDS 白名单当前只放 `192.168.1.237`
- `server-go` 已从 ECS 通过内网连接 RDS 并完成迁移，当前可见业务表包括 `user_entitlement`、`daily_usage`、`quota_ledger`、`topup_packs`、`upgrade_credits`、`session_ab`、`session_round_ledger`、`session_round_archive`、`chat_stream_inflight`、`daily_agri_cards`、`orders`、`support_messages`、`client_app_logs`、`admin_audit_logs`、`admin_users`、`admin_sessions`、`gift_card_batches`、`gift_cards`、`gift_card_redemption_attempts`
- 主规则已明确人工查看优先 DMS；Codex 优先只读查询、迁移脚本、备份脚本
- 当前仓库尚未提供统一的只读查询脚本或固定 SQL 清单；在只读账号创建前，不要把应用账号密码写入脚本或文档

## 当前临时查询方式

短期可通过 Cloud Assistant 在 ECS 内用 `/etc/nongjiqiancha/server.env` 的连接信息做临时只读查询，但命令不得打印数据库密码。

示例只查表名：

```bash
SHOW TABLES;
```

## 后续补充要求

- 一旦形成稳定排查方式，应补齐：
  - 只读连接入口
  - 查询前安全约束
  - 常用查询模板
  - 敏感表注意事项
  - 与迁移、备份脚本的关系

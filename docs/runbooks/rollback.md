# 回滚 Runbook

最后更新：2026-06-07

## 目的

记录农技千查当前有效的 ECS 后端回滚入口和验证口径。当前生产后端是单台 ECS 双端口 slot：Nginx 在 `127.0.0.1:3000` / `127.0.0.1:3001` 之间切换 active upstream，回滚也按 slot 切换执行。

## 当前入口

查看 ECS 上可回滚的二进制备份：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\rollback-ecs-server.ps1
```

执行回滚：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\rollback-ecs-server.ps1 -Apply
```

脚本会通过 Cloud Assistant 在 ECS 上执行，不打印真实密钥。回滚前会清理旧的 drain-stop 定时任务，选择当前非 active slot 启动备份二进制，通过 healthz 后再切 Nginx upstream，旧 slot 只在排空窗口后停止。

## 回滚前检查

- 确认本次问题是后端二进制 / Nginx slot 发版导致，而不是 RDS、Redis、OSS、DNS、证书、模型服务或阿里云认证服务异常。
- 先跑：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-ecs-readiness.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\query-ecs-logs.ps1 -Lines 240
```

- 如果只是当前 active slot 被误停，优先按 readiness 输出恢复该 slot；不要盲目回滚。
- 如果涉及数据库迁移，先看本次提交是否有 DDL。脚本只回滚后端二进制和 Nginx slot，不会自动回滚数据库结构或业务数据。

## 回滚后验证

回滚完成后必须复查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-ecs-readiness.ps1
curl.exe --resolve api.nongjiqiancha.cn:443:39.106.1.151 https://api.nongjiqiancha.cn/healthz
```

期望 healthz 至少包含：

- `ok=true`
- `auth_strict=true`
- `bailian=ok`
- `sms=ok`
- `dev_order_endpoints=false`
- `redis=ok`
- `upload_storage=oss`

如果回滚后仍失败，继续按 [logs-sls.md](D:/wuhao/docs/runbooks/logs-sls.md) 查 Go / Nginx / SLS 日志，并保留本次提交、回滚备份名、active slot、healthz 输出和主要错误码。

## 不自动回滚的内容

- MySQL 表结构、已写入的业务数据、额度 / 订单 / 会员资产
- Redis 中的短期验证码、限流桶和历史兼容认证临时状态
- OSS 中已上传图片
- ECS `/etc/nongjiqiancha/server.env` 配置
- DNS、证书、安全组、SLS 采集配置

这些内容需要按对应 runbook 单独处理，不能指望二进制回滚自动恢复。

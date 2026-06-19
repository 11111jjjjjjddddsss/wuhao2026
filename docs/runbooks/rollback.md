# 回滚 Runbook

最后更新：2026-06-19

## 目的

记录农技千查当前有效的 ECS 后端回滚入口和验证口径。当前生产后端是单台 ECS 双端口 slot：Nginx 在 `127.0.0.1:3000` / `127.0.0.1:3001` 之间切换 active upstream，回滚也按 slot 切换执行。

## 当前入口

查看 ECS 上可回滚的二进制备份：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\rollback-ecs-server.ps1
```

执行回滚：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\rollback-ecs-server.ps1 -BackupName nongji-server.bak-YYYYMMDDHHMMSS -Apply
```

先运行不带参数的命令查看最新备份名，再把要回滚的 `BackupName` 填进执行命令；只传 `-Apply` 不会执行回滚。

脚本会通过 Cloud Assistant 在 ECS 上执行，不打印真实密钥。回滚前会清理旧的 drain-stop 定时任务，选择当前非 active slot 启动备份二进制，并按同一个备份时间后缀恢复 `assets`、`migrations`、`go.mod`、`go.sum` 的对应备份；如果这些运行期资源备份不完整，脚本会拒绝混版本回滚。通过 healthz 后再切 Nginx upstream，旧 slot 只在排空窗口后停止。

## 回滚前检查

- 确认本次问题是后端二进制 / Nginx slot 发版导致，而不是 RDS、Redis、OSS、DNS、证书、模型服务或阿里云认证服务异常。
- 先跑：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-ecs-readiness.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\query-ecs-logs.ps1 -Lines 240
```

- 如果只是当前 active slot 被误停，优先按 readiness 输出恢复该 slot；不要盲目回滚。
- 如果涉及数据库迁移，先看本次提交是否有 DDL。脚本会回滚后端二进制、同后缀运行资源文件和 Nginx slot，但不会自动回滚数据库结构或业务数据。

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

这些内容需要按对应 runbook 单独处理，不能指望后端回滚脚本自动恢复。若脚本输出某个同后缀 `assets / migrations / go.mod / go.sum` 备份不存在，说明目标备份来自旧部署脚本或备份不完整；当前脚本会拒绝继续混用旧二进制和当前运行资源文件，需选择完整备份后缀或按日志手工恢复。

# ECS 发版 Runbook

最后更新：2026-06-01

## 目的

记录农技千查后端部署到阿里云 ECS 的当前有效入口。当前首版后端路线按 ECS 传统部署推进，替代此前的 SAE + ACR 镜像托管路线。

## 当前现状

- ECS：`i-2ze5nrem0jrchln4f0eh` / `iZ2ze5nrem0jrchln4f0ehZ`，地域 `cn-beijing`，可用区 `cn-beijing-l`，规格 `ecs.u1-c1m2.large`（2 vCPU / 4 GiB），Ubuntu 22.04 64 位，公网 IP `39.106.1.151`，私网 IP `192.168.1.237`，生产 VPC `nongjiqiancha-prod-vpc` / `vpc-2zeax2zowza2398b9dzot`，生产交换机 `nongjiqiancha-prod-beijing-l` / `vsw-2zemsq82lj2kp8za90aky`，系统路由表 `nongjiqiancha-prod-system-rt` / `vtb-2ze7xjciht46x324zgt7z`，安全组 `sg-2ze4tilwxw1h5w77lwl1`，固定公网带宽 5 Mbps，到期时间 2027-05-24
- 2026-05-30 已清理北京区空闲网络资源：删除旧 SAE 自动交换机 `vsw-2ze3elcd2iad6n1madi5g`、空默认交换机 `vsw-2zemrmbor6c886z5rul20` 和空默认 VPC `vpc-2zeceqyrcmnxhoaxxzjks`；当前北京区只保留生产 VPC / 交换机
- 旧 SAE demo 应用已删除，当前没有 SAE 应用承载后端或对外流量
- 已初始化服务器用户和目录：系统用户 `nongji`，部署目录 `/opt/nongjiqiancha/server`，运行配置 `/etc/nongjiqiancha/server.env`，上传目录 `/var/lib/nongjiqiancha/uploads`，日志目录 `/var/log/nongjiqiancha`
- 已安装并启用：Nginx、fail2ban、logrotate、MariaDB client、Go 1.26.2
- `server-go` 已部署为 `systemd` 服务 `nongji-server.service`，默认监听本机 `127.0.0.1:3000`，由 Nginx 按 `api.nongjiqiancha.cn` 反代；只有显式设置 `LISTEN_ADDR` 或 `LISTEN_HOST` 时才允许改成其他监听地址
- Go HTTP 服务使用显式 `http.Server`，默认 `ReadHeaderTimeout=5s`、`ReadTimeout=15s`、`IdleTimeout=90s`、`MaxHeaderBytes=1MiB`；`WriteTimeout` 默认保持 `0`，避免把正常 SSE 长回答按写超时杀掉。如需调整，可通过 `HTTP_READ_HEADER_TIMEOUT_SECONDS`、`HTTP_READ_TIMEOUT_SECONDS`、`HTTP_WRITE_TIMEOUT_SECONDS`、`HTTP_IDLE_TIMEOUT_SECONDS`、`HTTP_MAX_HEADER_BYTES` 和 `HTTP_SHUTDOWN_TIMEOUT_SECONDS` 配置
- 模型出站 HTTP client 不设置全局 `Timeout`，避免误杀 SSE 正文流；只限制拨号、TLS 握手、响应头等待和空闲连接，默认 `DASHSCOPE_DIAL_TIMEOUT_SECONDS=10`、`DASHSCOPE_TLS_HANDSHAKE_TIMEOUT_SECONDS=10`、`DASHSCOPE_RESPONSE_HEADER_TIMEOUT_SECONDS=60`、`DASHSCOPE_IDLE_CONN_TIMEOUT_SECONDS=90`。主聊天流另有 `CHAT_STREAM_MAX_DURATION_SECONDS` 兜底，默认 30 分钟；SSE 响应会带 `X-Accel-Buffering: no`，提示 Nginx 不缓冲流式响应
- 上游模型错误响应 / 非 SSE 响应只读取 64KiB 预览用于判断和日志，B/C 摘要非流式响应读取上限为 64KiB，今日农情 JSON 响应读取上限为 1MiB；正常主聊天 SSE 正文仍走流式转发
- 通用 JSON body 解析默认只读取 64KiB，并拒绝多段 JSON；App 日志接口仍有更小的 8KiB 上限且超限返回 `413 body_too_large`，图片上传仍按单张 JPEG `<=1MiB` 处理
- 主聊天应用层用户限流默认保持 `20 次 / 60 秒`，可用 `CHAT_RATE_LIMIT_MAX_HITS`、`CHAT_RATE_LIMIT_WINDOW_SECONDS` 和 `CHAT_RATE_LIMIT_PRUNE_INTERVAL_SECONDS` 调整；限流器会定期清理过期用户桶，避免长时间运行时被大量过期 user_id 撑大内存。Nginx 仍承担 IP 级限流，Go 侧限流只作为用户维度的第二层保护
- 服务启动迁移会先用 MySQL `GET_LOCK('nongji_schema_migration', 30)` 拿全局锁，避免未来滚动发布 / 多实例同时跑 DDL；迁移整体默认 2 分钟超时，可用 `MYSQL_MIGRATION_TIMEOUT_SECONDS` 调整；迁移锁释放失败会作为启动错误暴露，不再静默吞掉
- 2026-06-01 09:37（北京时间）已通过 Cloud Assistant 将后端源码包 `065a3f73` 部署到 ECS：分片上传源码包、ECS 上校验 SHA-256、运行 `go test ./...`、编译、备份旧二进制、替换并重启 `nongji-server`；重启瞬间 Nginx healthz 曾短暂 502，随后 readiness 复查显示 systemd active、Nginx 配置 OK、Host healthz 200。
- 生产 ECS 已切到 OSS 上传后端，并已配置 Redis 认证限流环境变量。当前健康检查：`curl -H 'Host: api.nongjiqiancha.cn' http://127.0.0.1/healthz` 返回 `ok=true`、`auth_strict=true`、`bailian=missing_key`、`dypns=missing_key`、`dypns_fusion=missing_config`、`dypns_sms=missing_config`、`dev_order_endpoints=false`、`redis=ok`、`upload_storage=oss`。
- 本机新增只读生产就绪检查脚本 [check-ecs-readiness.ps1](D:/wuhao/scripts/check-ecs-readiness.ps1)，通过 Cloud Assistant 检查 `nongji-server`、Nginx、Host healthz、关键环境变量是否 set/missing/empty、本机上传目录和端口监听；脚本只输出脱敏状态，不打印真实密钥值。2026-06-01 最新检查显示 systemd active、Nginx 配置 OK、Host healthz 200、`redis=ok`、`upload_storage=oss`；DYPNS 融合认证 / 短信配置已补齐，当前仍缺 DashScope 模型 Key
- 阿里云 DNS 已创建 A 记录 `api.nongjiqiancha.cn -> 39.106.1.151`，ECS 内 `getent hosts api.nongjiqiancha.cn` 和域名 HTTP healthz 均已解析到本机并返回 200；本机 Windows 若处在代理 / fake DNS 模式下可能仍看到 `198.18.x.x`，不能作为云端解析失败依据
- 当前未配置 DashScope 模型 Key，真实聊天接口会返回 `MODEL_BACKEND_NOT_CONFIGURED`，不会开模型流或消耗模型费用
- 当前还没有 HTTPS、ICP备案 / App 备案闭环；正式 App 不应切到生产域名直到这些完成

## 安全组

- 已开放：TCP 80 / 443，来源 `0.0.0.0/0`
- 已移除：公网 TCP 3389
- 暂时保留：公网 TCP 22，来源 `0.0.0.0/0`，并在 ECS 内启用 fail2ban；后续确认云助手 / SSH 运维方式后应限制来源 IP 或关闭公网 SSH
- 仍开放：ICMP，来源 `0.0.0.0/0`；不是当前关键风险，后续可按需要收口

## RDS

- RDS 实例：`rm-2zes3vmj76p85n8g1`
- 内网地址：`rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`
- 数据库：`nongjiqiancha`
- 应用账号：`nongji_app`
- RDS 白名单：`192.168.1.237`
- 数据库密码只保存在本机 `%USERPROFILE%\.nongjiqiancha\prod-secrets.json` 和 ECS `/etc/nongjiqiancha/server.env`，不写入仓库或文档

## 常用命令

通过阿里云 CLI / Cloud Assistant 执行命令，不需要在聊天或仓库里暴露服务器密码。

查看服务状态：

```powershell
aliyun ecs RunCommand --RegionId cn-beijing --Type RunShellScript --InstanceId.1 i-2ze5nrem0jrchln4f0eh --CommandContent "systemctl status nongji-server --no-pager" --Timeout 120
```

服务器内常用命令：

```bash
systemctl status nongji-server --no-pager
journalctl -u nongji-server -n 120 --no-pager
systemctl restart nongji-server
nginx -t
systemctl reload nginx
curl -H 'Host: api.nongjiqiancha.cn' http://127.0.0.1/healthz
```

本机验证：

```powershell
cd D:\wuhao\server-go
go test ./...
go build ./...
```

Android 生产域名构建前提：

```powershell
.\gradlew.bat :app:compileDebugKotlin -PUPLOAD_BASE_URL=https://api.nongjiqiancha.cn
```

正式 release 构建还会额外检查固定 release 签名和 https `UPLOAD_BASE_URL`；Android 已移除静态 `SESSION_API_TOKEN` 登录绕过，正式包只走手机号账号 session token。

## 当前发布方式

当前没有把 GitHub 凭据放到 ECS。仓库在 ECS 上不能直接 `git clone` 私有仓库，因此采用脚本 [deploy-ecs-server.ps1](D:/wuhao/scripts/deploy-ecs-server.ps1) 在本机打包并通过 Cloud Assistant 发布：

1. 本地打包 `server-go` 源码、assets、migrations、go.mod、go.sum
2. 用 ECS Cloud Assistant `SendFile` 分片下发到 `/tmp/nongji-deploy-chunks-<commit>`
3. ECS 本机用 Go 1.26.2 编译到 `/opt/nongjiqiancha/server/nongji-server`
4. 备份旧二进制，替换新二进制，复制 assets / migrations / go.mod / go.sum
5. 重启 `nongji-server.service`，执行 `nginx -t` 和 Host healthz

只验证打包不部署：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\deploy-ecs-server.ps1 -PackageOnly
```

发布当前 HEAD：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\deploy-ecs-server.ps1
```

脚本不得打印或写入任何真实密钥；不要在发布命令中读取 `/etc/nongjiqiancha/server.env`、`printenv`、`env` 或 `systemctl show -p Environment`。

查看可回滚二进制备份：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\rollback-ecs-server.ps1
```

只读生产就绪检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-ecs-readiness.ps1
```

按备份名回滚，必须显式加 `-Apply`：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\rollback-ecs-server.ps1 -BackupName nongji-server.bak-YYYYMMDDHHMMSS -Apply
```

## Nginx

- 当前 Nginx 只监听 HTTP 80；HTTPS 证书未配置
- 服务器环境变量 `BASE_PUBLIC_URL / UPLOAD_BASE_URL` 当前已配置为 `https://api.nongjiqiancha.cn`，所以图片上传和 Android 生产链路必须等 443 证书配置完成后才算真正可用
- `/api/chat/stream` 关闭 proxy buffering，`proxy_read_timeout=600s`
- 已配置基础 IP 级限流：
  - 普通 API：`60r/m`，burst 80
  - 主聊天流：`6r/m`，burst 3
  - 上传：`20r/m`，burst 8
  - 单 IP 连接数：聊天 2，上传 4，普通 API 20
- 未命中 `api.nongjiqiancha.cn` 的 Host 默认返回 444；HTTPS 正式就绪前，公网直连 IP 不作为稳定验证入口

## 当前禁止

- 不把数据库密码、模型 Key、短信 Key、AccessKey、`APP_SECRET`、`DAILY_AGRI_JOB_SECRET`、`SUPPORT_ADMIN_SECRET` 写进本文、代码或聊天记忆
- 不把旧 SAE AppId 当成可部署目标
- 不在真实 App 上传 / 读取 / 模型拉图链路验证通过前扩到两台 ECS 或多后端实例
- 不打开 `ALLOW_DEV_ORDER_ENDPOINTS`
- 不把共享静态 token / 测试用户 ID 当正式登录方案

## 下一步

1. 配置 DashScope 模型 Key 到 ECS 环境文件并重启服务。
2. 完成 `api.nongjiqiancha.cn` HTTPS 证书、ICP备案 / App 备案。
3. 配置阿里云手机号认证 `DYPNS_*`、短信签名模板，保持 Android 一键登录 SDK 与当前后端 `scheme_code` 链路一致。
4. HTTPS / 模型 Key 就绪后，用真实 App 链路验证 `/upload`、`/uploads/`、模型拉图和历史图片过期占位。
5. 给发布 / 回滚脚本补更完整的异常处理和发布记录归档。

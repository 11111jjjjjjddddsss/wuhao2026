# ECS 发版 Runbook

最后更新：2026-06-27

## 目的

记录农技千查后端部署到阿里云 ECS 的当前有效入口。当前首版后端路线按 ECS 传统部署推进，替代此前的 SAE + ACR 镜像托管路线。

## 当前现状

- ECS：`i-2ze5nrem0jrchln4f0eh` / `iZ2ze5nrem0jrchln4f0ehZ`，地域 `cn-beijing`，可用区 `cn-beijing-l`，规格 `ecs.u1-c1m2.large`（2 vCPU / 4 GiB），Ubuntu 22.04 64 位，公网 IP `39.106.1.151`，私网 IP `192.168.1.237`，生产 VPC `nongjiqiancha-prod-vpc` / `vpc-2zeax2zowza2398b9dzot`，生产交换机 `nongjiqiancha-prod-beijing-l` / `vsw-2zemsq82lj2kp8za90aky`，系统路由表 `nongjiqiancha-prod-system-rt` / `vtb-2ze7xjciht46x324zgt7z`，安全组 `sg-2ze4tilwxw1h5w77lwl1`，固定公网带宽 5 Mbps，CLI 当前显示到期时间 `2027-06-01T16:00Z`
- 2026-05-30 已清理北京区空闲网络资源：删除旧 SAE 自动交换机 `vsw-2ze3elcd2iad6n1madi5g`、空默认交换机 `vsw-2zemrmbor6c886z5rul20` 和空默认 VPC `vpc-2zeceqyrcmnxhoaxxzjks`；当前北京区只保留生产 VPC / 交换机
- 旧 SAE demo 应用已删除，当前没有 SAE 应用承载后端或对外流量
- 已初始化服务器用户和目录：系统用户 `nongji`，部署目录 `/opt/nongjiqiancha/server`，运行配置 `/etc/nongjiqiancha/server.env`，上传目录 `/var/lib/nongjiqiancha/uploads`，日志目录 `/var/log/nongjiqiancha`，免费离线 IP 粗定位库目录 `/opt/nongjiqiancha/ip2region`
- 已安装并启用：Nginx、fail2ban、logrotate、MariaDB client、Go 1.26.x（仓库 `server-go/go.mod` 通过 `toolchain go1.26.4` 钉住补丁版构建工具链）、阿里云云安全中心免费版 agent
- 操作系统当前保留 Ubuntu 22.04 LTS：它仍在标准安全维护期内，且当前 Nginx、certbot、Cloud Assistant、Logtail、云监控和 Go 后端链路已经跑稳。上线前不要为了“最新系统”在单台生产 ECS 上原地大版本升级；后续若升级到 Ubuntu 24.04 LTS，推荐新开 ECS 演练部署、跑 readiness / 公网黑盒 / 真机回归，再切流量或纳入多 ECS / SLB 方案
- `server-go` 当前采用单台 ECS 双端口发版：Nginx 在 `127.0.0.1:3000` 和 `127.0.0.1:3001` 之间切换上游，systemd slot 服务为 `nongji-server-3000.service` / `nongji-server-3001.service`；历史 `nongji-server.service` 仅作为迁移前旧服务名保留，双端口迁移成功后已禁用，旧 slot 会按排空窗口延迟停止，避免打断正在进行的 SSE。不要在生产环境显式配置 `LISTEN_ADDR` 或 `LISTEN_HOST`，否则双端口脚本无法通过 `PORT=3000/3001` 切换监听地址
- Go HTTP 服务使用显式 `http.Server`，默认 `ReadHeaderTimeout=5s`、`ReadTimeout=15s`、`IdleTimeout=90s`、`MaxHeaderBytes=1MiB`；`WriteTimeout` 默认保持 `0`，避免把正常 SSE 长回答按写超时杀掉。如需调整，可通过 `HTTP_READ_HEADER_TIMEOUT_SECONDS`、`HTTP_READ_TIMEOUT_SECONDS`、`HTTP_WRITE_TIMEOUT_SECONDS`、`HTTP_IDLE_TIMEOUT_SECONDS`、`HTTP_MAX_HEADER_BYTES` 和 `HTTP_SHUTDOWN_TIMEOUT_SECONDS` 配置
- 模型出站 HTTP client 不设置全局 `Timeout`，避免误杀 SSE 正文流；只限制拨号、TLS 握手、响应头等待和空闲连接，默认 `DASHSCOPE_DIAL_TIMEOUT_SECONDS=10`、`DASHSCOPE_TLS_HANDSHAKE_TIMEOUT_SECONDS=10`、`DASHSCOPE_RESPONSE_HEADER_TIMEOUT_SECONDS=60`、`DASHSCOPE_IDLE_CONN_TIMEOUT_SECONDS=90`。主聊天流另有 `CHAT_STREAM_MAX_DURATION_SECONDS` 兜底，默认 30 分钟；SSE 响应会带 `X-Accel-Buffering: no`，提示 Nginx 不缓冲流式响应
- 上游模型错误响应 / 非 SSE 响应只读取 64KiB 预览用于判断和日志，记忆文档摘要非流式响应读取上限为 64KiB，今日农情 JSON 响应读取上限为 1MiB；正常主聊天 SSE 正文仍走流式转发
- 通用 JSON body 解析默认只读取 64KiB，并拒绝多段 JSON；App 日志接口仍有更小的 8KiB 上限且超限返回 `413 body_too_large`，图片上传仍按单张 JPEG `<=1MiB` 处理
- 主聊天应用层用户限流默认保持 `20 次 / 60 秒`，可用 `CHAT_RATE_LIMIT_MAX_HITS`、`CHAT_RATE_LIMIT_WINDOW_SECONDS` 和 `CHAT_RATE_LIMIT_PRUNE_INTERVAL_SECONDS` 调整；配置 Redis 时该限流跨进程共享，未配置 Redis 时回退单进程限流并定期清理过期用户桶。Nginx 仍承担 IP 级限流，Go 侧限流只作为用户维度的第二层保护
- 服务启动迁移会先用 MySQL `GET_LOCK('nongji_schema_migration', 30)` 拿全局锁，避免未来滚动发布 / 多实例同时跑 DDL；迁移整体默认 2 分钟超时，可用 `MYSQL_MIGRATION_TIMEOUT_SECONDS` 调整；迁移锁释放失败会作为启动错误暴露，不再静默吞掉。双端口 slot 不能让数据库 DDL 自动回滚：新 slot 一启动，迁移仍会作用到同一个生产 RDS。2026-06-18 起，`deploy-ecs-server.ps1` 在打包前会调用 [check-server-migration-risk.ps1](D:/wuhao/scripts/check-server-migration-risk.ps1) 扫描 `线上 REVISION..待部署 commit` 范围内的 `server-go/migrations/*.sql`；如果线上 revision 无法读取或本地不存在，则回退脚本默认 base，并在输出中标明 `migration_diff_base=default`。遇到 `ALTER / DROP / RENAME / TRUNCATE / UPDATE / DELETE / REPLACE / MODIFY` 等高风险 SQL 默认拦截；确认为安全迁移时才显式传 `-AllowHighRiskMigrations`。这只是防手滑门禁，不等于迁移可自动回滚；进入多 ECS / SLB 前仍应评估独立迁移步骤和 `schema_migrations` 表。
- 2026-06-01 已通过 Cloud Assistant 将包含手机号登录 / 融合认证后端改动的源码包部署到 ECS：分片上传源码包、ECS 上校验 SHA-256、运行 `go test ./...`、编译、备份旧二进制、替换并重启 `nongji-server`；重启瞬间 Nginx healthz 曾短暂 502，随后 readiness 复查显示 systemd active、Nginx 配置 OK、Host healthz 200。
- 生产 ECS 已切到 OSS 上传后端，并已配置 Redis 认证限流、普通短信验证码环境变量、DashScope / 百炼主组 / 副组模型 Key 分组槽位和 `ip2region` v4 xdb 本地库路径。当前健康检查应走本机 HTTPS：`curl --resolve api.nongjiqiancha.cn:443:127.0.0.1 https://api.nongjiqiancha.cn/healthz` 返回 `ok=true`、`auth_strict=true`、`bailian=ok`、`sms=ok`、`dev_order_endpoints=false`、`redis=ok`、`upload_storage=oss`；`dypns_*` 字段只作旧包兼容状态参考，不再作为新 Android 登录主链门槛。
- 本机新增只读生产就绪检查脚本 [check-ecs-readiness.ps1](D:/wuhao/scripts/check-ecs-readiness.ps1)，通过 Cloud Assistant 检查 `nongji-server`、Nginx、HTTPS healthz、关键环境变量是否 set/missing/empty、本机上传目录、`ip2region` v4 xdb 是否可读、端口监听和后台 `/admin-api/` 上游是否跟随 API active slot；脚本只输出脱敏状态，不打印真实密钥值。当前脚本会在 `nginx -t` 配置检测失败、active upstream slot 未 active、inactive slot 仍 active 且没有 active / activating 的 drain-stop 单元、后台上游端口与 API active slot 不一致、HTTPS healthz 非 200、未登录后台鉴权接口不是 401，或生产 healthz 缺少 `ok/auth_strict/bailian/sms/dev_order_endpoints=false/redis/upload_storage/revision` 关键标记时直接失败；同时会硬拦 `AUTH_ALLOW_LEGACY_TOKEN=true`、`AUTH_ALLOW_UNPROVEN_LEGACY_UUID=true`、`AUTH_FUSION_COMPAT_ENABLED=true` 和 `ALLOW_DEV_ORDER_ENDPOINTS=true`，也会拦截 `ADMIN_COOKIE_SECURE=false/no/off/0` 或拼写错误的非空值，避免 Nginx 配置错误、502、登录认证配置异常、旧 bearer token 兼容、未证明旧 UUID 资产迁移、旧融合接口误开、后台反代漂移、后台 Cookie 安全配置漂移、开发订单入口误开、旧 slot worker 长期并跑或线上版本号缺失被误判成通过。2026-06-13 起，[deploy-ecs-server.ps1](D:/wuhao/scripts/deploy-ecs-server.ps1) 发布切换后也会显式校验后台 `/admin-api/` 上游端口已跟随目标 slot，不再只靠后台未登录 401 判断。2026-06-19 起，发布脚本会把本次 git commit 写入 `/opt/nongjiqiancha/server/REVISION`，`/healthz` 返回 `revision`，发布切流前后会校验 revision 与本次 commit 一致，readiness 会打印并要求 `server_revision` 非空；Nginx API / 后台 upstream 切换写入后，脚本会保留切换前配置备份，若 reload 后公网 healthz、后台鉴权或 final slot 检查失败，会恢复切换前 Nginx 配置并停止新 slot，降低半切换残留风险。最新 active upstream 和线上 revision 以 [current-status.md](D:/wuhao/docs/project-state/current-status.md) 和脚本实时输出为准；不要在 runbook 里长期写死 `3000` 或 `3001` 当当前真相。
- 今日农情每日生成当前推荐走 ECS systemd timer：本机脚本 [configure-ecs-daily-agri-job.ps1](D:/wuhao/scripts/configure-ecs-daily-agri-job.ps1) 会通过 Cloud Assistant 在 ECS 写入 `nongji-daily-agri.service` / `nongji-daily-agri.timer` 和 `/usr/local/bin/nongji-generate-today-agri.sh`，脚本从 `/etc/nongjiqiancha/server.env` 读取 `DAILY_AGRI_JOB_SECRET` 并调用 `POST /internal/jobs/today-agri-card/generate`；默认 `OnCalendar=*-*-* 21:35:00 UTC`，对应北京时间约 `05:35`
- 阿里云 DNS 已创建 A 记录 `api.nongjiqiancha.cn -> 39.106.1.151`，ECS 内 `getent hosts api.nongjiqiancha.cn` 会解析到本机；HTTPS healthz 返回 200，HTTP healthz 返回 301 跳 HTTPS 属于预期。本机 Windows 若处在代理 / fake DNS 模式下可能仍看到 `198.18.x.x`，不能作为云端解析失败依据
- DashScope / 百炼模型 Key 已通过 Cloud Assistant 写入 ECS 分组槽位并重启，真实 Key 值不进入仓库、文档、提交信息或聊天记忆；当前生产按 `DASHSCOPE_PRIMARY_API_KEY_1...4` 作为主账号 Key 组，健康时在主组内按请求轮询；`DASHSCOPE_SECONDARY_API_KEY_1` 作为副账号兜底，只在主组开流前全部失败后才尝试。旧 `DASHSCOPE_API_KEY_*`、`DASHSCOPE_API_KEY` 和 `DASHSCOPE_API_KEYS` 仅作兼容 / 回滚入口；详细口径见 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md)。
- 网站 ICP 备案已通过：主体备案号 `京ICP备2026031728号`，网站备案号 `京ICP备2026031728号-1`；App 备案已通过，App 备案号 `京ICP备2026031728号-2A`，Android 设置页底部低调展示该编号，服务协议 / 隐私基础信息也同步展示该编号；2026-06-05 已通过 Let’s Encrypt / certbot 为 `api.nongjiqiancha.cn` 配置 Nginx 443 HTTPS，并公网验证 `https://api.nongjiqiancha.cn/healthz` 返回 200。当前仍缺 App 公安备案和真机登录 / 主聊天 / 图片问诊回归，正式 App 切生产域名前仍需最终回归

## 安全组

- 已开放：TCP 80 / 443，来源 `0.0.0.0/0`
- 已移除：公网 TCP 3389
- 已移除：公网 TCP 22，来源 `0.0.0.0/0`；ECS 本机 `ssh` 服务也已停用并 disabled。当前生产运维优先走阿里云 CLI + Cloud Assistant；若后续确需 SSH，必须通过 Cloud Assistant 临时启动 ssh，并按固定来源 IP 放通安全组，用完再关
- 仍开放：ICMP，来源 `0.0.0.0/0`；不是当前关键风险，后续可按需要收口
- 免费优先安全加固入口见 [security-hardening.md](D:/wuhao/docs/runbooks/security-hardening.md)

## RDS

- RDS 实例：`rm-2zes3vmj76p85n8g1`
- 内网地址：`rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`
- 数据库：`nongjiqiancha`
- 应用账号：`nongji_app`
- RDS 白名单：`192.168.1.237`
- 数据库密码只保存在本机 `%USERPROFILE%\.nongjiqiancha\prod-secrets.json` 和 ECS `/etc/nongjiqiancha/server.env`，不写入仓库或文档

## 常用命令

通过阿里云 CLI / Cloud Assistant 执行命令，不需要在聊天或仓库里暴露服务器密码。

查看当前 active upstream 和服务状态；2026-06-28 起生产支付门禁为 `public`，正式巡检必须显式传 `-ExpectedAlipayPaymentGate public`，并确保生产环境不再保留 0.01 测试金额和 limited 白名单变量。若未来回滚到内部联调态，才按 `limited` 门禁单独说明：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-ecs-readiness.ps1 -ExpectedAlipayPaymentGate public
```

正式收费相关发布总门禁还要显式打开 public 支付验收，不允许只跑默认检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-launch-readiness.ps1 -ReleaseGate -PaymentFormalAccepted
```

服务器内常用命令：

```bash
grep -oE 'proxy_pass http://127\.0\.0\.1:(3000|3001);' /etc/nginx/sites-available/nongjiqiancha-api | head -1
systemctl status nongji-server-3000 nongji-server-3001 --no-pager
journalctl -u nongji-server-3000 -u nongji-server-3001 -n 120 --no-pager
nginx -t
systemctl reload nginx
curl --resolve api.nongjiqiancha.cn:443:127.0.0.1 https://api.nongjiqiancha.cn/healthz
curl -I http://api.nongjiqiancha.cn/healthz
```

本机验证：

```powershell
cd D:\wuhao\server-go
go test ./...
go build ./...
```

Android 生产域名构建前提：

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Android 构建固定使用 `UPLOAD_BASE_URL=https://api.nongjiqiancha.cn`，Android Studio 直接 Run 的 debug 包和正式 release 包都接正式 HTTPS 后端；`USE_BACKEND_AB` 固定开启，不再通过 Gradle 参数关闭后端主链或切换业务后端地址。正式 release 构建还会额外检查固定 release 签名和 https 生产地址；Android 已移除静态 `SESSION_API_TOKEN` 登录绕过，正式包只走手机号账号 session token。

## 当前发布方式

当前没有把 GitHub 凭据放到 ECS。仓库在 ECS 上不能直接 `git clone` 私有仓库，因此采用脚本 [deploy-ecs-server.ps1](D:/wuhao/scripts/deploy-ecs-server.ps1) 在本机打包并通过 Cloud Assistant 发布：

1. 本地先跑迁移风险守卫，再打包 `server-go` 源码、assets、migrations、go.mod、go.sum
2. 用 ECS Cloud Assistant `SendFile` 分片下发到 `/tmp/nongji-deploy-chunks-<commit>`
3. ECS 本机按 `server-go/go.mod` 的 Go toolchain 声明编译到 `/opt/nongjiqiancha/server/nongji-server`；当前要求 `toolchain go1.26.4`
4. 备份旧二进制，替换新二进制，复制 assets / migrations / go.mod / go.sum；发布脚本也会检查 `server-go` 新增顶层运行时代码目录是否被打包，避免未来新增 `pkg/` 等目录后线上漏文件
5. 读取 Nginx 当前上游端口，选择另一个端口作为新 slot
6. 启动 `nongji-server-3000.service` 或 `nongji-server-3001.service` 中的非当前 slot，并先检查该端口本机 `/healthz`
7. 通过 `nginx -t` 后把 API Nginx 上游和后台 `/admin-api/` 上游一起切到新 slot，reload Nginx，再由脚本检查本机 HTTPS healthz、生产 health 标记和后台未登录鉴权接口
8. 新入口健康后启用新 slot、禁用旧 slot / 历史 `nongji-server.service`，并通过 transient systemd timer 延迟停止旧进程，给已有 SSE 连接排空时间；每次部署 / 回滚前都会先清理旧 `nongji-drain-stop-*` transient 任务，避免多次发布叠加后把当前 active slot 误停成 502
9. 发布成功后脚本会清理本次 `/tmp/nongji-*` 上传、解包、编译和健康检查临时文件，并裁剪旧部署 / Nginx 备份，默认只保留最近 8 组可回滚材料；这是低成本磁盘清理，不删除数据库、OSS 正式包、用户上传图、日志留存、环境变量或密钥文件。确需调整时用 `-RemoteBackupRetentionCount` 和 `-RemoteTempRetentionDays`，不要手工 `rm -rf` 生产目录。

2026-06-11 起，仓库内 Cloud Assistant 运维脚本使用 [cloud-assistant-safe.ps1](D:/wuhao/scripts/cloud-assistant-safe.ps1) 的 `SendFile` 辅助函数下发脚本正文，再用短 `RunCommand` 执行远端脚本文件。不要新增 `echo <base64> | base64 -d | bash` 形态的长命令；该形态已触发过云安全中心“云助手异常命令”高危告警，也更容易在终端和审计里夹带内部命令细节。需要读取 ECS 环境变量的运维脚本只能在远端本机读取，并输出脱敏状态，不打印真实密钥。

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

安装 / 更新今日农情定时任务并立即跑一次：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\configure-ecs-daily-agri-job.ps1 -RunOnce
```

按备份名回滚，必须显式加 `-Apply`：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\rollback-ecs-server.ps1 -BackupName nongji-server.bak-YYYYMMDDHHMMSS -Apply
```

回滚脚本也必须和发布脚本一样切换 API 与后台 `/admin-api/` 两份 Nginx 上游，验证 `nginx -t`、生产 health 标记、`revision` 和后台未登录鉴权 401；如果后台上游没有跟随目标 slot，或带 `REVISION.bak-*` 的备份在 upstream / public healthz 中返回的 `revision` 不等于目标回滚 revision，会自动恢复 Nginx 配置并失败退出，不允许返回“回滚成功但后台半小时后 502 / 线上版本号仍显示新提交”的假成功。2026-06-19 起，回滚同一备份后缀会同步恢复 `REVISION.bak-*`；legacy 老备份没有 revision 文件时，会写入 `rollback-后缀` 并只在生产 health 标记、后台鉴权和 Nginx 切流都通过后带提示放行。回到 legacy 备份后，日常 readiness 仍会因为线上缺少机器可读 `revision` 而提醒重新部署到带 revision 的版本。

## Nginx

- 当前 Nginx 同时监听 HTTP 80 和 HTTPS 443；API HTTP 80 只保留 ACME challenge 并把其他请求 301 跳 HTTPS，真正业务请求走 443；`server-go` 只监听本机端口，公网统一由 Nginx 反代。发版时 Nginx 会在 `127.0.0.1:3000` / `127.0.0.1:3001` 两个上游之间切换
- 2026-06-05 已通过 Cloud Assistant 在 ECS 安装 certbot，并用 Let’s Encrypt HTTP-01 webroot 为 `api.nongjiqiancha.cn` 签发免费 DV 证书；证书有效期到 2026-09-03，certbot 自动续期 timer 已启用
- Nginx 配置文件：`/etc/nginx/sites-available/nongjiqiancha-api`；本次 HTTPS 前备份：`/etc/nginx/sites-available/nongjiqiancha-api.bak-20260605211327`
- 证书路径：`/etc/letsencrypt/live/api.nongjiqiancha.cn/fullchain.pem`；私钥路径：`/etc/letsencrypt/live/api.nongjiqiancha.cn/privkey.pem`。只记录路径，不在仓库、聊天或日志打印私钥内容
- 公网验证：`https://api.nongjiqiancha.cn/healthz` 返回 200；`http://api.nongjiqiancha.cn/healthz` 返回 301 到 HTTPS；HTTP 80 不再直接反代业务 API
- 服务器环境变量 `BASE_PUBLIC_URL / UPLOAD_BASE_URL` 当前已配置为 `https://api.nongjiqiancha.cn`，HTTPS 和模型 Key 槽位已就绪；仍需用真实 App 链路验证图片上传、图片读取和模型拉图
- `/api/chat/stream` 关闭 proxy buffering，`proxy_read_timeout=600s`
- 已配置基础 IP 级限流：
  - 普通 API：`60r/m`，burst 80
  - 主聊天流：`60r/m`，burst 80
  - 上传：`20r/m`，burst 8
  - 单 IP 连接数：上传提交 `/upload` 4，普通 API 20；2026-06-13 已移除主聊天流 `limit_conn`，2026-06-21 已为图片读取 `/uploads/` 单独去掉 `limit_conn`，避免模型供应商共享出口 IP 拉图被误伤
- 当前 App / Nginx / Go / Android 各层限流、大小、超时和 UI 窗口边界统一记录在 [app-traffic-limits.md](D:/wuhao/docs/runbooks/app-traffic-limits.md)。2026-06-13 已把线上主聊天流 Nginx IP 级限制调宽到 `60r/m`、burst 80，并移除聊天 `limit_conn`；若真机仍出现 429，先结合响应体和日志确认来源，不新增全局主聊天并发硬闸、不通过模型输出上限解决流量问题。
- 未命中 `api.nongjiqiancha.cn` 的 Host 默认返回 444；公网直连 IP 不作为稳定验证入口
- 已写入全局 Nginx 安全头配置 `/etc/nginx/conf.d/nongjiqiancha-security.conf`：`server_tokens off`、`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy` 和 HSTS

## 当前禁止

- 不把数据库密码、模型 Key、短信 Key、AccessKey、`APP_SECRET`、`DAILY_AGRI_JOB_SECRET`、`SUPPORT_ADMIN_SECRET` 写进本文、代码或聊天记忆
- 不把旧 SAE AppId 当成可部署目标
- 不在真实 App 上传 / 读取 / 模型拉图链路验证通过前扩到两台 ECS 或多后端实例
- 不打开 `ALLOW_DEV_ORDER_ENDPOINTS`
- 不把共享静态 token / 测试用户 ID 当正式登录方案

## 下一步

1. 跟进 App 公安备案；网站 ICP 已于 2026-06-05 通过，App 备案已通过并取得 `京ICP备2026031728号-2A`，网站公安联网备案号已于 2026-06-10 下发并已补官网 footer，`api.nongjiqiancha.cn` HTTPS 已于 2026-06-05 配置完成。
2. 上线前轮换已暴露过的主账号 AccessKey，优先改成最小权限 RAM 用户，并重新写入 ECS 普通短信 `SMS_*`、OSS、模型等生产环境变量；`DYPNS_*` 只在明确需要历史融合兼容时保留，不作为新 Android 登录主链配置。
3. 用真实 App 链路验证短信验证码登录、`/upload`、`/uploads/`、模型拉图、主聊天流和历史图片过期占位。
4. 后续若要做到跨实例高可用，再升级为多 ECS / SLB 滚动发布；当前双端口只解决单机重启空窗，不等于多机容灾。

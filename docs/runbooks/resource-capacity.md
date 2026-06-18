# 云资源容量与续费巡检

最后更新：2026-06-18

## 目的

记录农技千查当前已购买云资源是否够用、怎么用 CLI 复查、以及什么时候必须提前提醒升级或续费。以后只要发现资源接近阈值，Codex 应主动提示用户，不等用户先发现卡顿、502 或欠费风险。

本 runbook 只记录规格、用量、阈值和巡检入口；不记录 AccessKey、数据库密码、模型 Key、Redis 密码、服务器环境变量内容或公安备案数据码。

## 2026-06-18 巡检结论

结论：当前 ECS / RDS / Redis / OSS 容量都很宽裕，不需要立刻升配；2026-06-12 已补云监控邮件联系人组、9 条资源水位告警、ECS 系统盘自动快照、SLS 应用日志邮件行动策略和最小仪表盘。2026-06-14 已按阿里云官方推荐给 ECS 补装 CloudMonitor C++ 插件，用于操作系统层内存等指标；本轮资源巡检显示 9 条云监控资源规则均为 `OK`，SLS 5 条应用日志告警为 `ready`。巡检脚本会把云监控规则 `INSUFFICIENT_DATA` 暴露成 warning / attention，不再把“规则存在但无数据”当成全绿。2026-06-15 已按阿里云官方文档复核释放保护口径：ECS 释放保护只适用于按量付费实例，RDS MySQL 释放保护只适用于按量付费或 Serverless；当前 ECS / RDS 均为包年包月，脚本会显示 `deletion_protection=not_applicable_prepaid`，不要把旧输出里的 `False` 误读成缺少保护。2026-06-17 资源巡检已收紧 OSS 生命周期校验，会按 XML 逐条确认 `uploads/` 3 天、`support/` 30 天、`test-apks/` 3 天和未完成分片 1 天在同一条启用规则里成立，避免前缀和天数分属不同规则时假绿。2026-06-18 起严格资源巡检会同时调用下载域名检查，验证 `download.nongjiqiancha.cn` 的 DNS、OSS CNAME、证书绑定和签名 HEAD 探针，避免正式包下载链路漂移但资源门禁仍假绿。公网黑盒脚本已加官网备案号、公安备案号、协议页和警徽图标探测；剩余更该补的是把黑盒探测接成自动定时通知、登录 / 模型用量趋势和帮助反馈图片生命周期取舍。

- ECS：`ecs.u1-c1m2.large`，2 vCPU / 4 GiB，固定公网出带宽 5 Mbps；实例 Running，到期 `2027-06-01T16:00Z`。ECS 实时负载约 0，内存可用约 2.9 GiB，系统盘 79 GiB 已用约 12 GiB（16%），近 7 天未见 OOM
- 安全组：公网入站只有 `80/443` 和 ICMP，未放行 `22/3389`；ECS 本机 ssh 服务仍按前序加固口径停用
- ECS 系统盘：80 GiB ESSD Entry，已绑定普通低频自动快照策略 `sp-2ze9ufwsu2i5hxm2wmrk` / `nongjiqiancha-prod-basic-7d`：每周二、周六北京时间 04:00 创建，保留 7 天，不启用跨地域复制或归档。ECS 是包年包月 `PrePaid`，阿里云 ECS 释放保护只适用于按量付费实例，脚本输出为 `deletion_protection=not_applicable_prepaid`
- RDS MySQL 是包年包月 `Prepaid`，阿里云 RDS MySQL 释放保护只适用于按量付费或 Serverless 实例，脚本输出为 `deletion_protection=not_applicable_prepaid`
- RDS MySQL：基础版 1 核 / 2 GiB / 50 GiB，最大连接数 600，到期 `2027-05-24T16:00:00Z`。磁盘约 2.98 GiB（约 5.97%）；近 30 分钟 QPS/TPS 峰值约 10.03、IOPS 约 5.93、内存 / CPU 指标约 11.76%、连接约 4；备份保留 7 天，日志备份已启用
- Redis：256 MiB 标准高可用主备，到期 `2027-05-30T16:00:00Z`。近 30 分钟内存约 5.39 MiB / 256 MiB（约 2.11%），CPU 峰值约 0.13%，连接使用约 0.04%；释放保护已开启
- OSS：Bucket `nongjiqiancha-prod` ACL private、Standard、LRS；生命周期为 `uploads/` 3 天、`support/` 30 天、`test-apks/` 3 天、未完成分片 1 天。2026-06-12 已开启 Bucket 默认服务端加密，`SSEAlgorithm=AES256`；2026-06-17 起 `check-resource-capacity.ps1` 会解析生命周期 XML 并逐前缀输出 `lifecycle prefix=... status=... expiration_days=... abort_multipart_days=...`
- DNS / 域名 / HTTPS：`@ / www / api / admin` A 记录均指向 `39.106.1.151` 且 ENABLE；`download.nongjiqiancha.cn` CNAME 到 `nongjiqiancha-prod.oss-cn-beijing.aliyuncs.com`，用于 OSS 低成本 APK 下载；域名到期 `2027-05-24 19:23:07`；ECS 上 Let’s Encrypt 证书由 `certbot.timer` 自动续期，`download.nongjiqiancha.cn` 的 OSS 自定义域名证书已绑定。2026-06-17 已重签下载域名证书并加密同步到 OSS，当前到期日 `2026-09-15 07:03:04 UTC`；证书续期后需用 `scripts/sync-oss-download-certificate.ps1` 同步到 OSS CNAME 配置，该脚本不再让 Cloud Assistant 输出明文私钥
- 云监控：联系人 `NongjiOwner` 的邮件通道已激活，联系人组 `NongjiQianchaOps` 已创建；已配置 9 条资源水位规则，覆盖 ECS CPU / 内存、RDS CPU / 内存 / 磁盘 / 连接、Redis CPU / 内存 / 连接，均挂到该联系人组。ECS 已补装 CloudMonitor C++ 插件，ECS 上 `cloudmonitor.service` / `argusagent` 已 running；本轮严格巡检里 ECS 内存规则已回到 `OK`。若未来云监控返回 `INSUFFICIENT_DATA`，严格巡检会以 warning / attention 提醒。该组用于资源不足提前邮件提醒，不走短信 / 电话
- ARMS / 云监控系统事件：2026-06-18 阿里云 App 里出现 `InstanceStatus:ArmsStopped` 警告，事件内容指向默认 CMS 工作空间 `default-cms-1159547719787456-cn-beijing` 且原因为 `user_stop`。只读核对后结论是：这不是 ECS 服务器停机，不是 Go 后端 / RDS / Redis / OSS 故障，也不是农技千查主业务告警；ARMS 当前无活跃告警、通知策略或用户自建事件规则。默认 CMS 工作空间下仍有 CloudMonitor 2.0 底层免费指标容器在运行，不能为了清红点贸然删除工作空间，也不能关闭 `NongjiQianchaOps`、SLS 应用告警或 9 条资源水位规则。该系统事件历史本身不像云安全中心事件那样可手工“处理”；如果未来每天继续新增同类噪音，再只针对 ARMS stopped 消息订阅 / 事件通知流做静音评估，不动主业务监控。
- SLS：5 条最小 AlertHub 告警均存在并启用，告警查询、触发条件、重复提醒已按脚本期望校验；应用日志邮件行动策略 `nongji-prod-email` 和 dashboard `nongji-prod-ops` 绑定均为 `5/5`，`check-sls-alert-readiness.ps1 -RequireExternalNotification -RequireDashboard -FailOnWarning` 返回 `status=ready`
- 云安全中心：2026-06-18 已把 1 条提醒级 `云产品威胁检测-OSS可疑访问行为` 事件按“我已手工处理”收口，待处理列表和风险等级计数均为 0；该事件不是资源水位告警，核对后与本项目近期 OSS CNAME / 证书 / 配置巡检运维行为一致。本轮未创建长期白名单，后续同类事件仍逐条核对。聊天和仓库文档不记录 AK、IP、UserAgent 或其它敏感字段；后续若出现来源不明、写操作、删除操作、失败调用或越权类告警，不能静默忽略，应按安全事件处理并优先轮换相关凭证。
- 普通短信服务：已购买国内通用短信套餐包，新 Android 登录消耗普通短信余量；DYPNS / 融合认证统计只作为历史兼容观察。`check-sms-usage.ps1` 会额外调用费用中心有效资源包 API 做交叉检查，但 2026-06-15 当前只返回百炼推理资源包和 OSS 存储包，没有返回短信类套餐包；因此短信套餐包余额、到期、余量预警和自动复购仍需以短信服务控制台为准
- 数据留存与文字表成本：2026-06-15 新增 `scripts/check-data-retention-cost.ps1` 并接入 `check-resource-capacity.ps1`。该脚本只读统计重点文字 / 日志 / 账本表的行数、最早 / 最新时间和表体量，默认守护聊天归档 31 天、App 自动日志 30 天、客服文字 / 审计 / 幂等 ledger 365 天复核窗口、单表 1GB、重点表合计 10GB。当前生产实测重点表合计约 0.828MB，`warnings=0 / errors=0 / status=ready`；因此当前成本很低。App 自动日志已按低成本窗口在写入路径限频清理，公开运营前仍需继续补客服文字、后台审计和注销联动的自动清理 / 去标识化策略
- 费用中心账单巡检：2026-06-15 新增 `scripts/check-aliyun-costs.ps1`，通过阿里云费用中心 BSS OpenAPI 只读查询账户余额、当月 / 上月产品账单、当前月明细、百炼每日走势、有效资源包和有效实例；脚本会重试费用中心偶发超时，统一脱敏请求签名和账号字段，不买资源、不续费、不退订、不释放实例。当前实测账户可用余额约 `628.35` 元；2026-06 当月产品账单税前合计约 `130.1225` 元，其中百炼约 `60.2716` 元、短信套餐包 `35` 元、DYPNS / 融合认证套餐 `34.85` 元、SLS 约 `0.0009` 元。百炼成本主要来自 2026-06-08 到 2026-06-10 的提示词 / 探针集中测试，最近 5 天百炼税前合计约 `0.357` 元、均值约 `0.0714` 元 / 天；`qwen-plus` 推理资源包剩余 `11,489,501 / 12,000,000 tokens`，OSS 标准存储包剩余 `100 / 100GB`。DYPNS / 融合认证已按已购沉没成本处理，新 Android 不再使用；当前两个融合认证包均为 `ManualRenewal`，不是自动续费，其中 2026-05-31 包实付 `0` 元、2026-06-06 包实付约 `34.85` 元；CLI 安全询价 `InquiryPriceRefundInstance` 对两个包均返回 `CommodityNotSupported`，不走 CLI 退订。脚本只在 DYPNS / 融合认证出现自动续费或新增购买等情况时提醒；短信套餐包在资源包 API 中不可见、百炼节省计划临近到期等仍列为 attention，这是经营成本提醒，不等同于服务故障。当前建议是保留低价 ECS / RDS / Redis / OSS / 域名 / 短信套餐，不升配；百炼按真实用户量再买资源包或节省计划。

统一只读资源巡检脚本会复查容量、到期、证书、OSS、云监控规则、SLS 告警、下载域名和认证用量，输出会脱敏，不打印密钥。2026-06-12 起，云监控 9 条规则不只看是否存在，还会校验资源实例、warn / critical 阈值、连续周期和统计周期，避免“规则名存在但挂错资源 / 阈值飘了”的假绿；2026-06-14 起，云监控规则若返回 `INSUFFICIENT_DATA` 也会输出 warning / attention，避免 ECS 内存等操作系统指标无数据时假绿；2026-06-17 起，OSS 生命周期不再只用文本包含关系粗看，而是解析生命周期 XML 并确认 `uploads/`、`support/`、`test-apks/` 前缀各自对应正确过期天数和 1 天未完成分片清理；2026-06-18 起，下载域名检查会作为严格资源门禁的一部分，失败时 `-Strict` 直接失败；2026-06-13 起，SLS 规则查询、严重级别、触发条件、重复提醒、行动策略或仪表盘出现漂移时，默认资源巡检会输出 attention，不再吞成 ready：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-resource-capacity.ps1
```

当前脚本汇总状态以实时输出为准。ECS / RDS 当前是包年包月，释放保护不适用，脚本会以 `not_applicable_prepaid` 表达；Redis 释放保护已开启。ECS 自动快照已按省钱策略开启，后续只需观察快照容量费用；SLS 应用日志 action policy / 仪表盘已闭环到邮件 + 最小图表。2026-06-15 严格巡检输出 `warnings=0 / errors=0 / status=ready`。

下载域名和 OSS CNAME HTTPS 单独巡检：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-android-download-domain.ps1
```

如果 `download.nongjiqiancha.cn` 的 ECS 免费证书已经续期，但 OSS CNAME 证书仍是旧有效期，运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\sync-oss-download-certificate.ps1
```

本机 Codex 已创建每周“农技千查续费与证书巡检”自动化，默认检查 ECS / RDS / Redis / OSS 包、域名、免费证书、下载域名 OSS 证书、短信套餐、模型资源包 / 节省计划和异常账单；它只做巡检和必要的 OSS 证书同步，不购买、不续费、不退订、不释放任何付费资源。

代理测试或疑似卡顿前后，可加跑服务器性能快检：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-server-performance.ps1
```

该脚本只读查看 ECS 资源、Go / Nginx 进程、systemd 重启数、本机 healthz 延迟、24 小时 Go 错误 / 慢请求 / SSE 数量和 Nginx 429 / 5xx 计数，用来判断是否真是服务器性能问题。2026-06-16 实测 Go 进程 RSS 约 15MB、CPU 约 0，Nginx 与 Go 24 小时无 5xx / 429，只有 1 条今日农情内部生成慢请求，说明当前瓶颈不在 Go 运行性能。

费用中心账单、资源包和模型日成本只读巡检：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-aliyun-costs.ps1
```

该脚本会把“成本需要关注”输出成 `status=attention`，例如当月总额超过阈值、短信套餐包没有被资源包 API 暴露、百炼节省计划即将到期、DYPNS / 融合认证出现自动续费或新增购买等。已购融合认证包本身按沉没成本记录，不再作为必须退款 / 退订动作。`attention` 不是发布失败，也不是服务不可用；处理方式是复核经营决策、确认不自动续费 / 不新增购买、控制台续费 / 购买资源包或继续观察。2026-06-15 起，[check-launch-readiness.ps1](D:/wuhao/scripts/check-launch-readiness.ps1) 也会在云资源段调用该脚本，并把非 ready 结果显示为单独的 `aliyun costs` attention，避免上线总门禁漏看账单 / 套餐 / 模型资源包状态。

公网黑盒只读巡检脚本会从当前运行机器直接请求公网域名，不通过 ECS 本机 `--resolve`，用于确认外部用户实际能访问 API、官网、www 和后台入口，并确认后台未登录仍是 401、HTTP 会跳 HTTPS：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-public-blackbox.ps1
```

当前脚本会检查：

- `https://api.nongjiqiancha.cn/healthz` 返回 200，且包含 `auth_strict=true`、`bailian=ok`、DYPNS / 短信 / Redis / OSS 等关键生产标记。
- `https://nongjiqiancha.cn/`、`https://www.nongjiqiancha.cn/`、`https://admin.nongjiqiancha.cn/` 返回 200。
- `https://admin.nongjiqiancha.cn/admin-api/v1/auth/me` 未登录返回 401。
- `http://api.nongjiqiancha.cn/healthz`、根域名、www 和后台 HTTP 入口返回 301 / 302 / 307 / 308 到对应 HTTPS 地址。

如果把 SLS 外部通知、仪表盘、查询语句、严重级别、触发条件和重复提醒漂移都作为上线硬门槛，可用严格模式；当前生产应通过，若失败说明告警、行动策略、仪表盘或规则配置发生漂移：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-resource-capacity.ps1 -Strict
```

## 资源水位邮件告警

2026-06-12 已在阿里云云监控创建联系人组 `NongjiQianchaOps`，挂联系人 `NongjiOwner`，邮件通道已激活。当前只走邮件提醒，不走短信 / 电话，避免通知费用和噪音。

| 规则 ID | 资源 | 指标 | Warn | Critical | 说明 |
| --- | --- | --- | --- | --- | --- |
| `nq-ecs-cpu-high` | ECS | `CPUUtilization` | 70% 连续 3 个 5 分钟周期 | 85% 连续 3 个 5 分钟周期 | 服务器 CPU 高水位 |
| `nq-ecs-memory-high` | ECS | `memory_usedutilization` | 70% | 85% | 服务器内存高水位 |
| `nq-rds-cpu-high` | RDS | `CpuUsage` | 70% | 85% | 数据库 CPU 高水位 |
| `nq-rds-memory-high` | RDS | `MemoryUsage` | 70% | 85% | 数据库内存高水位 |
| `nq-rds-disk-high` | RDS | `DiskUsage` | 70% | 85% | 数据库磁盘高水位 |
| `nq-rds-connection-high` | RDS | `ConnectionUsage` | 60% | 80% | 数据库连接数高水位 |
| `nq-redis-cpu-high` | Redis | `StandardCpuUsage` | 70% | 85% | Redis CPU 高水位 |
| `nq-redis-memory-high` | Redis | `StandardMemoryUsage` | 70% | 85% | Redis 内存高水位 |
| `nq-redis-connection-high` | Redis | `StandardConnectionUsage` | 60% | 80% | Redis 连接数高水位 |

这些规则只覆盖资源水位。Go 5xx、慢请求、今日农情失败等应用事件走 SLS AlertHub + `nongji-prod-email` 邮件行动策略 + `nongji-prod-ops` 最小仪表盘，两套告警各管一块。

## 2026-06-06 巡检结论

结论：当前资源足够支撑备案等待期、真机联调、早期内测和小流量上线。当前最需要继续补的是监控 / 告警 / 自动恢复，不是立刻升配。

- ECS：`ecs.u1-c1m2.large`，2 vCPU / 4 GiB，固定公网出带宽 5 Mbps；CLI 显示实例 Running，到期 `2027-06-01T16:00Z`。ECS 实时负载约 0，内存可用约 2.9 GiB，系统盘 79 GiB 已用约 5.0 GiB（7%），无 OOM 记录
- RDS MySQL：基础版 1 核 / 2 GiB / 50 GiB，最大连接数 600，到期 `2027-05-24T16:00:00Z`。CLI 显示磁盘使用约 3.0 GiB（约 6%）；近 15 分钟 CPU 约 0.7%，内存约 11%，IOPS 约 5，连接约 0 到 1
- Redis：256 MiB 标准主备，到期 `2027-05-30T16:00:00Z`。CLI 显示实例 Normal、连接上限 10000、QPS 规格 100000；近 10 分钟监控内存约 4.4 MiB / 256 MiB（约 1.7%）、CPU 0 到 0.4%、连接使用约 0.03% 到 0.05%
- OSS：`nongjiqiancha-prod` 当前用量很低，100 GiB 标准-本地冗余资源包足够；`uploads/` 3 天、`support/` 30 天、`test-apks/` 3 天生命周期仍是控制成本的关键
- CDN：当前未启用，也不是早期必需项。阿里云 CDN 不是纯免费资源，按官方口径会产生下行流量 / 带宽等基础费用，HTTPS 静态请求有月度免费额度但超出仍计费；早期问诊图片继续走后端 `/uploads/` 中转 + OSS 生命周期，不把私有问诊图片直接改成 CDN 长缓存。若后续 APK 下载、官网静态资源或公开图片流量明显挤占 5 Mbps ECS 出口，再评估 CDN / OSS 下行分流 / 带宽升级
- 普通短信服务：上线后重点看短信套餐包余量、发送成功率、失败码和异常手机号 / IP 请求；历史 [check-auth-usage.ps1](D:/wuhao/scripts/check-auth-usage.ps1) 仍可辅助查询旧 DYPNS / 融合认证统计，但新 Android 不再消耗融合认证
- API / 官网：2026-06-06 巡检时发现一次 502，根因不是资源不足，而是双端口发布的多个 drain-stop 定时任务叠加，把当前 slot 也停掉；已启动当前 active slot 恢复 `https://api.nongjiqiancha.cn/healthz` 200，并修复部署 / 回滚脚本以清理旧 drain 任务，就绪检查脚本也改成 HTTPS healthz 非 200 或 active slot inactive 时直接失败。后续复查最近 6 小时 Go 服务未见业务错误，Nginx error log 主要是公网扫描 `.env / phpinfo / json key` 等探测请求被限流拦截，未见新的 API 5xx 计数；API HTTP 80 已改为 ACME challenge + 301 HTTPS 跳转，不再直接反代业务 API

## 固定巡检命令

ECS 就绪检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-ecs-readiness.ps1
```

资源容量、到期、OSS、证书、云监控资源水位、SLS 和认证用量统一巡检：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-resource-capacity.ps1
```

公网黑盒巡检：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-public-blackbox.ps1
```

ECS 规格：

```powershell
aliyun ecs DescribeInstances --RegionId cn-beijing --InstanceIds '["i-2ze5nrem0jrchln4f0eh"]'
```

RDS 规格与磁盘：

```powershell
aliyun rds DescribeDBInstanceAttribute --RegionId cn-beijing --DBInstanceId rm-2zes3vmj76p85n8g1
```

RDS 近 15 分钟性能：

```powershell
$start=(Get-Date).ToUniversalTime().AddMinutes(-15).ToString('yyyy-MM-ddTHH:mmZ')
$end=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mmZ')
aliyun rds DescribeDBInstancePerformance --RegionId cn-beijing --DBInstanceId rm-2zes3vmj76p85n8g1 --StartTime $start --EndTime $end --Key MySQL_MemCpuUsage,MySQL_Sessions,MySQL_IOPS,MySQL_QPSTPS,MySQL_SpaceUsage
```

Redis 规格：

```powershell
aliyun r-kvstore DescribeInstanceAttribute --RegionId cn-beijing --InstanceId r-2zet46zvmoo9wu3bic
```

Redis 近 10 分钟性能：

```powershell
$start=(Get-Date).ToUniversalTime().AddMinutes(-10).ToString('yyyy-MM-ddTHH:mm:ssZ')
$end=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
aliyun r-kvstore DescribeHistoryMonitorValues --RegionId cn-beijing --InstanceId r-2zet46zvmoo9wu3bic --StartTime $start --EndTime $end --IntervalForHistory 01m --MonitorKeys UsedMemory,quotaMemory,CpuUsage,ConnectionUsage
```

OSS Bucket 用量：

```powershell
aliyun oss du oss://nongjiqiancha-prod --block-size MB
```

普通短信发送统计：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-sms-usage.ps1
```

脚本会校验阿里云短信统计接口状态，默认不按短信签名过滤，统计为空时默认短暂重试一次，并输出发送总量、成功、失败和无回执汇总；如果最终仍为空，只能说明所选日期范围统计没有返回明细或供应商统计存在延迟，不等于套餐包余额充足，也不等于线上一定没有验证码请求。脚本输出的原始统计 JSON 也走手机号字段脱敏，避免后续接口字段变化时意外打印敏感信息。脚本还会调用费用中心 `QueryResourcePackageInstances` 查询当前有效资源包，并输出是否能看到短信类套餐包；当前该 API 未返回短信类套餐包时会输出 `sms_package_status=not_visible_manual_required`。若手动传 `-SignName` 导致空统计，应再用默认总量口径复查。旧 DYPNS / 融合认证用量脚本 [check-auth-usage.ps1](D:/wuhao/scripts/check-auth-usage.ps1) 只保留给历史包排障；新 Android 包不再消耗融合认证主链。短信套餐包余量、到期、余量预警、自动复购和账单仍以短信服务控制台 / 费用中心页面为准，脚本用于看普通短信发送统计、失败趋势和资源包 API 可见性。

## 提前提醒阈值

只要达到以下任一条件，Codex 后续巡检时必须主动提醒用户升级、扩容、购买资源包或调整架构：

- ECS CPU 连续 30 分钟超过 70%，或 1 分钟负载长期大于 vCPU 数
- ECS 可用内存低于 25%，出现 OOM、swap 压力，或 Go 服务 RSS 持续增长
- ECS 系统盘使用超过 70%；超过 80% 时优先清理日志 / 扩盘，不能拖
- 5 Mbps 出口带宽接近打满、图片下载明显慢、官网 / APK 下载开始被带宽限制时，优先评估 CDN / OSS 下行 / 带宽升级
- RDS CPU 连续超过 70%，内存超过 70%，IOPS 接近上限，慢查询持续增加，或连接数超过 60% 上限
- RDS 磁盘超过 70% 要提示扩容；超过 80% 要立即安排低峰扩容
- Redis 内存超过 70%、连接超过 60%、出现 evicted keys / rejected connections / latency 明显升高时，提示升配或拆分用途
- OSS 资源包使用超过 70% 提醒观察，超过 85% 提醒购买更大资源包或调整生命周期
- 模型 Key / 百炼账号出现连续限流、余额不足、套餐消耗接近上限或主副账号账单不可控时，提示充值、购买节省计划或统一账号
- 普通短信发送量突然超过真实用户访问规模、失败率超过 20%、验证码防盗刷监控触发、套餐包余量下降异常或月账单明显异常时，提示排查限流、短信模板、密钥、异常 IP / 手机号和是否被盗刷；上线早期若单日短信发送量超过 100 条也要提醒复查，后续按真实用户量重设阈值
- ECS、RDS、Redis、OSS 资源包、域名、HTTPS 证书、短信 / 认证服务到期前 60 / 30 / 7 天都要提醒；证书虽有自动续期，也要在到期前人工复查一次

## 当前不急着升级的原因

- 聊天内容、会员资产、归档、额度和订单主真相仍在 MySQL，图片二进制已进 OSS，不会把 RDS 存储快速打爆
- Redis 当前只做短期认证 / 限流 / App 日志等轻量用途，256 MiB 仍非常宽裕
- ECS 当前 CPU / 内存 / 磁盘余量都很大，首版单实例足够早期联调和小流量内测
- 真正风险在第一封告警邮件送达确认、日志趋势细化、自动恢复、黑盒探测和真机回归，而不是规格马上不够

## 后续动作

- 云监控资源水位邮件告警已覆盖 ECS / RDS / Redis 高水位；ECS 系统盘已开启普通低频自动快照；SLS 应用日志已绑定邮件行动策略和最小仪表盘；继续补 API healthz / Go 服务 inactive 这类应用可用性告警
- 当前已有独立公网黑盒只读脚本；`check-ecs-readiness.ps1` 能人工 / CI 巡检服务器内部和反代状态，`check-public-blackbox.ps1` 能从公网视角确认 API / 官网 / 后台可达。后续仍应把黑盒探测接成自动定时通知，而不是只靠人工跑脚本
- 登录链路监控至少覆盖普通短信发送量 / 成功率 / 失败率 / 套餐包余量 / 账单、后端 `/api/auth/sms/*` 入口错误率和历史 `/api/auth/fusion/*` 是否仍被旧包误打
- 若继续使用单 ECS 双端口发布，部署 / 回滚前必须清理旧 `nongji-drain-stop-*` transient systemd 任务，避免多个排空任务叠加
- 管理后台上线后，容量快照、到期时间、5xx 和 App 自动日志应做成只读运维面板

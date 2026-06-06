# 云资源容量与续费巡检

最后更新：2026-06-06

## 目的

记录农技千查当前已购买云资源是否够用、怎么用 CLI 复查、以及什么时候必须提前提醒升级或续费。以后只要发现资源接近阈值，Codex 应主动提示用户，不等用户先发现卡顿、502 或欠费风险。

本 runbook 只记录规格、用量、阈值和巡检入口；不记录 AccessKey、数据库密码、模型 Key、Redis 密码、服务器环境变量内容或公安备案数据码。

## 2026-06-06 巡检结论

结论：当前资源足够支撑备案等待期、真机联调、早期内测和小流量上线。当前最需要继续补的是监控 / 告警 / 自动恢复，不是立刻升配。

- ECS：`ecs.u1-c1m2.large`，2 vCPU / 4 GiB，固定公网出带宽 5 Mbps；CLI 显示实例 Running，到期 `2027-06-01T16:00Z`。ECS 实时负载约 0，内存可用约 2.9 GiB，系统盘 79 GiB 已用约 5.0 GiB（7%），无 OOM 记录
- RDS MySQL：基础版 1 核 / 2 GiB / 50 GiB，最大连接数 600，到期 `2027-05-24T16:00:00Z`。CLI 显示磁盘使用约 3.0 GiB（约 6%）；近 15 分钟 CPU 约 0.7%，内存约 11%，IOPS 约 5，连接约 0 到 1
- Redis：256 MiB 标准主备，到期 `2027-05-30T16:00:00Z`。CLI 显示实例 Normal、连接上限 10000、QPS 规格 100000；近 10 分钟监控内存约 4.4 MiB / 256 MiB（约 1.7%）、CPU 0 到 0.4%、连接使用约 0.03% 到 0.05%
- OSS：`nongjiqiancha-prod` 当前 `du` 为 0 MB，100 GiB 标准-本地冗余资源包足够；`uploads/` 3 天、`support/` 30 天生命周期仍是控制成本的关键
- CDN：当前未启用，也不是早期必需项。阿里云 CDN 不是纯免费资源，按官方口径会产生下行流量 / 带宽等基础费用，HTTPS 静态请求有月度免费额度但超出仍计费；早期问诊图片继续走后端 `/uploads/` 中转 + OSS 生命周期，不把私有问诊图片直接改成 CDN 长缓存。若后续 APK 下载、官网静态资源或公开图片流量明显挤占 5 Mbps ECS 出口，再评估 CDN / OSS 下行分流 / 带宽升级
- DYPNS 融合认证 / 短信认证：已新增 [check-auth-usage.ps1](D:/wuhao/scripts/check-auth-usage.ps1) 查询一键登录和短信认证统计 / 月账单。2026-06-06 默认查询最近 7 天时统计均为 `no_data`，说明当前尚未形成真实认证消耗
- API / 官网：2026-06-06 巡检时发现一次 502，根因不是资源不足，而是双端口发布的多个 drain-stop 定时任务叠加，把当前 slot 也停掉；已启动当前 active slot 恢复 `https://api.nongjiqiancha.cn/healthz` 200，并修复部署 / 回滚脚本以清理旧 drain 任务，就绪检查脚本也改成 HTTPS healthz 非 200 或 active slot inactive 时直接失败。后续复查最近 6 小时 Go 服务未见业务错误，Nginx error log 主要是公网扫描 `.env / phpinfo / json key` 等探测请求被限流拦截，未见新的 API 5xx 计数；API HTTP 80 已改为 ACME challenge + 301 HTTPS 跳转，不再直接反代业务 API

## 固定巡检命令

ECS 就绪检查：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-ecs-readiness.ps1
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

一键登录 / 短信认证次数和账单：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-auth-usage.ps1
```

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
- DYPNS 一键登录 / 短信认证调用量突然超过真实用户访问规模、失败率超过 20%、未知率持续不为 0，或月账单明显异常时，提示排查 SDK、限流、密钥和半程 / 最终校验链路；上线早期若单日认证调用量超过 100 次也要提醒复查，后续按真实用户量重设阈值
- ECS、RDS、Redis、OSS 资源包、域名、HTTPS 证书、短信 / 认证服务到期前 60 / 30 / 7 天都要提醒；证书虽有自动续期，也要在到期前人工复查一次

## 当前不急着升级的原因

- 聊天内容、会员资产、归档、额度和订单主真相仍在 MySQL，图片二进制已进 OSS，不会把 RDS 存储快速打爆
- Redis 当前只做短期认证 / 限流 / App 日志等轻量用途，256 MiB 仍非常宽裕
- ECS 当前 CPU / 内存 / 磁盘余量都很大，首版单实例足够早期联调和小流量内测
- 真正风险在监控、告警、日志检索、自动恢复和真机回归，而不是规格马上不够

## 后续动作

- 尽快补农技千查专用日志 / 告警，至少覆盖 API healthz、Nginx 5xx、Go 服务 inactive、RDS / Redis / ECS 高水位
- 登录链路监控至少覆盖 DYPNS 一键登录次数 / 成功率 / 失败率 / 账单、短信认证次数 / 账单、后端 `/api/auth/fusion/*` 和 `/api/auth/sms/*` 入口错误率
- 若继续使用单 ECS 双端口发布，部署 / 回滚前必须清理旧 `nongji-drain-stop-*` transient systemd 任务，避免多个排空任务叠加
- 管理后台上线后，容量快照、到期时间、5xx 和 App 自动日志应做成只读运维面板

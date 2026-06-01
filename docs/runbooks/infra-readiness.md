# 上线前基础设施准备清单

最后更新：2026-06-01

## 目的

记录“农技千查”上线所需的基础设施、依赖顺序、关键配置和补文档动作。当前 ECS / RDS / Redis / OSS Bucket 已部分落地，本文继续作为后续配置和部署前检查入口。

## 当前现状

- Android 客户端与 `server-go` 后端代码主线已存在
- 仓库内已有 SAE / 日志 / 回滚 / 数据库只读 runbook 骨架；[operations-blueprint.md](D:/wuhao/docs/runbooks/operations-blueprint.md) 已把后期 Codex 协助整体 App、后端、管理后台、发布、回滚、日志和数据运维的范围先固定下来
- 下一阶段上线推进顺序已沉淀到 [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md)：买服务器 / 域名后立刻启动 ICP / App 备案，手机号登录、后端部署、RDS、OSS、SLS 和真实接口联调在备案等待期间并行推进
- 买服务器前功能巡检记录已开始沉淀到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)：当前已巡检会员中心 / 额度体系，以及 Go 后端高并发 / 性能边界
- 正式云资源已部分落地：Region 选定 `华北2（北京）/ cn-beijing`；首版后端部署路线已从“SAE 镜像托管优先”转向“ECS 传统部署优先”。ECS `i-2ze5nrem0jrchln4f0eh` 已购买并运行，可用区 L，规格 `ecs.u1-c1m2.large`（2 vCPU / 4 GiB），Ubuntu 22.04 64 位，公网 IP `39.106.1.151`，私网 IP `192.168.1.237`，生产 VPC `nongjiqiancha-prod-vpc` / `vpc-2zeax2zowza2398b9dzot`，生产交换机 `nongjiqiancha-prod-beijing-l` / `vsw-2zemsq82lj2kp8za90aky`，安全组 `sg-2ze4tilwxw1h5w77lwl1`，固定公网带宽 5 Mbps，到期时间 2027-05-24；2026-05-30 已删除空闲默认 VPC / 默认交换机和旧 SAE 自动交换机，当前北京区只保留生产 VPC / 交换机；已部署真实 `server-go`，当前健康检查 OK，但模型 Key 未配置。阿里云 DNS 已创建 A 记录 `api.nongjiqiancha.cn -> 39.106.1.151`，ECS 内解析和 HTTP healthz 已生效。此前曾创建标准版 SAE 应用 `nongjiqiancha`，AppId `366147d5-3760-4548-bd68-f38debbc5f23`，规格 `0.5 核 / 1GB / 单实例`，自动弹性未开启，但该应用只是默认 demo 镜像且已删除；删除后 SAE `ListApplications` 返回空列表，`TotalSize=0`。RDS MySQL 实例 `rm-2zes3vmj76p85n8g1` 已创建并运行，MySQL 8.0、基础版、1 核 2GB、50GB、北京可用区 L、同一交换机 `vsw-2zemsq82lj2kp8za90aky` / `192.168.1.0/24`、内网地址 `rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`，当前自动备份保留 7 天；已创建库 `nongjiqiancha`、账号 `nongji_app`，白名单放通 ECS 私网 IP `192.168.1.237`。Redis 开源版实例 `nongjiqiancha-prod-redis` / `r-2zet46zvmoo9wu3bic` 已购买并运行，256MB、Redis 7.0、标准高可用主备、同生产 VPC / 北京可用区 L，内网地址 `r-2zet46zvmoo9wu3bic.redis.rds.aliyuncs.com:6379`，白名单放通 ECS 私网 IP；`server-go` 已接可选 Redis 客户端，生产 ECS 已配置 `REDIS_*` 并通过 `/healthz redis=ok` 验证，当前只用于手机号认证短期限流，不接管聊天、额度、归档、摘要或订单。域名 `nongjiqiancha.cn` 已购买，用户口头确认实名认证 / 模板审核已通过，仍待备案、HTTPS 和正式后端入口绑定。OSS 标准-本地冗余存储包（华北2）100GB 已购买并生效，资源包实例 `OSSBAG-cn-mqq4sqfvr001`；Bucket `nongjiqiancha-prod` 已创建为北京私有标准本地冗余，并配置 `uploads/` 3 天、`support/` 30 天生命周期，代码已新增 OSS 上传后端，生产 ECS 环境变量已收口并通过 `upload_storage=oss` 验证。SLS 服务本体已开通，但未创建农技千查专用日志项目
- 当前尚未购买 / 接入：SLS 节省计划 / 资源包、CDN / OSS 下行流量包。当前尚未完成：DashScope 模型 Key、HTTPS、备案、阿里云融合认证 Android SDK / SchemeCode / 短信模板；默认 7 天 RDS 备份策略是否调整仍待确认

## 最小上线资源清单

### 必需

- 阿里云 ECS：当前首版优先的 `server-go` 运行载体。实例 `i-2ze5nrem0jrchln4f0eh` 已购买并运行；后续需要固化系统用户、部署目录、systemd、反向代理、HTTPS、日志和回滚入口
- 阿里云 RDS MySQL：首版主业务数据。PolarDB 暂作为后续高并发 / 更高规格升级选项，不再作为个人创业首版默认采购项。当前实例 `rm-2zes3vmj76p85n8g1` 已创建并运行，配置为 MySQL 8.0、基础版、1 核 2GB、50GB、北京可用区 L、生产 VPC `vpc-2zeax2zowza2398b9dzot`、生产交换机 `vsw-2zemsq82lj2kp8za90aky`，内网地址 `rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`，自动备份保留 7 天
- 域名与 HTTPS 证书：对外 API 入口。当前 `nongjiqiancha.cn` 已购买且用户口头确认实名认证 / 模板审核已通过，但正式 API 域名、证书和后端入口绑定尚未完成
- 基础密钥与环境变量托管：模型 Key、数据库连接、JWT / Session 密钥等

### 高优先级可选

- OSS：图片上传与短期保存。当前已购买华北2 100GB 标准-本地冗余存储包，Bucket `nongjiqiancha-prod` 已创建并配置生命周期，后端已新增 OSS 存储后端；生产 ECS 已配置环境变量并通过 `upload_storage=oss` 验证
- SLS：后端日志检索与告警
- Redis：验证码、缓存、临时会话或限流。当前已购买 256MB 实例，并已在 `server-go` 中接入认证短期限流；生产 ECS 已配置 `REDIS_*` 且 `/healthz redis=ok`。当前不得写成已接管聊天流、额度、归档、摘要或订单

## 建议采购顺序

1. 已完成：Region 选定 `cn-beijing`，旧 SAE demo 应用已删除，域名 `nongjiqiancha.cn` 已购买并口头确认过审，ECS / RDS MySQL / OSS 100GB 存储包已购买
2. 已完成：ECS 基础系统环境、Nginx、systemd、RDS 数据库 / 账号 / 白名单和 `server-go` 首版部署
3. 已完成：购买 Redis 开源版 256MB 最小实例，放入生产 VPC，并把融合认证 token、认证短信发送 / 登录校验短期限流接到 Redis；生产 ECS 已部署验证 `redis=ok`
4. 下一步：补 DashScope 模型 Key、HTTPS、融合认证 SDK / SchemeCode / 短信模板和日志方案；SLS 是否首版接入再按成本和排障需求确认

## 采购前必须拍板的问题

- 生产环境当前优先按北京 ECS 单环境跑通；北京 SAE demo 应用已删除，是否后续重新启用 SAE / 拆测试或预发环境仍待真实联调后确认
- 数据库首版倾向直接使用 RDS MySQL；当前只需确认规格、备份策略、连接白名单 / VPC 连接和后续是否需要平滑升级 PolarDB
- 图片首版按 OSS 短期保存推进；资源包、Bucket、生命周期、代码后端、生产 ECS 最小权限凭证、环境变量和部署验证均已就绪
- 日志是否首版就接农技千查专用 SLS；如果不是，后端先用 `journalctl` / Nginx 本地日志过渡
- 是否一开始就拆测试 / 生产两套环境，还是先单环境跑通
- 首版已接 OSS；若计划两台 ECS 或回到 SAE 多实例，仍需先用真实 App 链路验证图片上传、读取和模型拉图
- 多实例发布前，数据库迁移是否改成单独发布步骤或加迁移锁，避免多个实例首次启动同时跑迁移
- RDS 规格确认后，`MYSQL_MAX_OPEN_CONNS`、`MYSQL_MAX_IDLE_CONNS`、`MYSQL_CONN_MAX_IDLE_SECONDS`、`MYSQL_CONN_MAX_LIFETIME_SECONDS` 是否按实例数和连接数上限重新配置

## 扩容与升级判断

- 首版 RDS MySQL 1 核 2GB / 50GB 先服务早期真实联调和小流量内测；图片二进制不进数据库，后续应优先落 OSS
- 优先看监控再升级：CPU 长期超过 70%、活跃连接接近连接上限、慢查询持续增加、IOPS / 磁盘使用率接近上限、接口 P95/P99 延迟明显变差
- 存储空间不足时优先扩容存储；计算资源不足时从 1 核 2GB 升到 2 核 4GB 或更高；读多写少且主库压力明显后再考虑只读实例或 PolarDB
- 变配前先确认自动备份、低峰期执行、应用连接池和自动重连；RDS 变配通常不改实例 ID 和连接地址，但可能出现短暂连接闪断，生产变配必须提前公告或避开高峰
- ECS 先单台跑通；需要扩容时先确保图片已迁 OSS、数据库迁移不抢跑、摘要和限流有跨实例保护，再升配或增加第二台 ECS 并接入 ALB / SLB。若后续回到 SAE，同样先满足这些前置条件

## 资源买完后必须回填仓库的地方

- `docs/runbooks/deploy-sae.md`：仅在后续重新启用 SAE 时补真实发版入口、命令、成功判定；当前已固化 ECS 部署入口
- `docs/runbooks/rollback.md`：真实回滚入口
- `docs/runbooks/logs-sls.md`：真实日志项目 / 查询入口
- `docs/runbooks/db-readonly.md`：真实数据库只读连接方式
- `docs/runbooks/operations-blueprint.md`：回填真实 App / 后端 / 管理后台运维入口和权限边界
- `docs/project-state/current-status.md`：环境现状
- `docs/project-state/open-risks.md`：把“未采购正式云资源”风险收敛为“已采购但未部署 / 未配置”风险
- `docs/project-state/pending-decisions.md`：移除已拍板项

## 暂行原则

- 只把已真实落地的资源写成既成事实：当前可写旧 SAE demo 应用已删除、ECS 实例 `i-2ze5nrem0jrchln4f0eh`、RDS 实例 `rm-2zes3vmj76p85n8g1`、RDS 库 / 账号 / 白名单、ECS 上运行中的 `server-go`、域名 `nongjiqiancha.cn`、OSS 100GB 存储包、OSS Bucket `nongjiqiancha-prod` 与生命周期、Redis 实例和 SLS 服务开通状态；HTTPS 证书、备案号、模型 Key 已配置和后台地址仍不得伪造
- 未真正采购 / 配置完成前，runbook 只记录“要拍板什么”和“买完后补哪里”，不伪造部署命令
- 一旦出现第一套真实环境，必须同次把对应 runbook 补成可执行入口
- Go 后端首版不做盲目性能调参；先用默认连接池和单实例 / 小规格跑通，接入 SLS / RDS 监控后再按真实连接数、慢查询、SSE 中断率和模型限流调参

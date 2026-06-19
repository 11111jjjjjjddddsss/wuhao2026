# 上线前基础设施准备清单

最后更新：2026-06-20

## 目的

记录“农技千查”上线所需的基础设施、依赖顺序、关键配置和补文档动作。当前 ECS / RDS / Redis / OSS / SLS / 官网 / 管理后台已经落地，本文继续作为上线前资源与运行设计的巡检入口；可执行总门禁见 [scripts/check-launch-readiness.ps1](D:/wuhao/scripts/check-launch-readiness.ps1)。

## 当前现状

- Android 客户端与 `server-go` 后端代码主线已存在
- 仓库内已有 SAE / 日志 / 回滚 / 数据库只读 runbook 骨架；[operations-blueprint.md](D:/wuhao/docs/runbooks/operations-blueprint.md) 已把后期 Codex 协助整体 App、后端、管理后台、发布、回滚、日志和数据运维的范围先固定下来
- 下一阶段上线推进顺序已沉淀到 [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md)：买服务器 / 域名后立刻启动 ICP / App 备案，手机号登录、后端部署、RDS、OSS、SLS 和真实接口联调在备案等待期间并行推进；当前网站 ICP 和 App 备案均已通过，App 备案号为 `京ICP备2026031728号-2A`
- 买服务器前功能巡检记录已开始沉淀到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)：当前已巡检会员中心 / 额度体系，以及 Go 后端高并发 / 性能边界
- 正式云资源已落地为首版单 ECS 生产链：Region 选定 `华北2（北京）/ cn-beijing`；ECS `i-2ze5nrem0jrchln4f0eh`、RDS MySQL `rm-2zes3vmj76p85n8g1`、Redis `r-2zet46zvmoo9wu3bic`、OSS Bucket `nongjiqiancha-prod`、域名 / DNS / HTTPS、SLS Project / Logstore、管理后台 `admin.nongjiqiancha.cn` 均已配置。后端以双端口 slot + Nginx 反代运行，`scripts/check-ecs-readiness.ps1` 是线上 readiness 真相入口，当前要求 `auth_strict=true / bailian=ok / sms=ok / redis=ok / upload_storage=oss / dev_order_endpoints=false`；`dypns_*` 只作旧包兼容状态参考。RDS 自动备份当前保留 7 天，ECS 系统盘已绑定每周二 / 周六普通低频自动快照 7 天保留；资源水位由阿里云云监控邮件告警覆盖，SLS 应用日志按 180 天 TTL、7 天热存储、173 天低频存储留存，并且 AlertHub 最小告警已绑定邮件行动策略和最小仪表盘。公网入口可用 [scripts/check-public-blackbox.ps1](D:/wuhao/scripts/check-public-blackbox.ps1) 从外部用户视角检查 API、官网、www、后台首页、未登录后台 401、下载域名 / OSS CNAME 签名链和 HTTP->HTTPS 跳转。
- 当前尚未购买 / 接入：SLS 节省计划 / 资源包、CDN / OSS 下行流量包。以当前“一个用户都还没有”的阶段，这些不是上线硬前置；后续按 [resource-capacity.md](D:/wuhao/docs/runbooks/resource-capacity.md) 和真实用量再提示购买或升级。当前尚未完成：App 公安备案、短信验证码登录 / 主聊天 / 图片问诊真机回归、旧包检查更新覆盖安装回归、首封 SLS 告警邮件送达确认、上线前已暴露 AccessKey 轮换、真实支付渠道申请和回调链路。

## 最小上线资源清单

### 必需

- 阿里云 ECS：当前首版优先的 `server-go` 运行载体。实例 `i-2ze5nrem0jrchln4f0eh` 已购买并运行；系统用户、部署目录、systemd、反向代理、`api` HTTPS、日志、最小告警、检查更新发布历史、黑盒只读巡检、低成本公网黑盒 timer 告警和回滚入口已固化，后续继续补检查更新 APK 上传 / 完整回滚入口和后台运维细项
- 阿里云 RDS MySQL：首版主业务数据。PolarDB 暂作为后续高并发 / 更高规格升级选项，不再作为个人创业首版默认采购项。当前实例 `rm-2zes3vmj76p85n8g1` 已创建并运行，配置为 MySQL 8.0、基础版、1 核 2GB、50GB、北京可用区 L、生产 VPC `vpc-2zeax2zowza2398b9dzot`、生产交换机 `vsw-2zemsq82lj2kp8za90aky`，内网地址 `rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`，自动备份保留 7 天
- 域名与 HTTPS 证书：对外 API、官网和后台入口。当前 `nongjiqiancha.cn` 已购买且用户口头确认实名认证 / 模板审核已通过，`api.nongjiqiancha.cn` A 记录、Nginx 443、Let’s Encrypt 证书和后端反代已完成；`nongjiqiancha.cn` / `www.nongjiqiancha.cn` A 记录、Nginx 静态官网和 Let’s Encrypt 证书也已完成；`admin.nongjiqiancha.cn` 后台 HTTPS 和 `/admin-api/` 反代已完成；下载域名后续若启用需单独配置入口和证书
- 基础密钥与环境变量托管：模型 Key、数据库连接、JWT / Session 密钥等

### 高优先级可选

- OSS：图片上传与短期保存。当前已购买华北2 100GB 标准-本地冗余存储包，Bucket `nongjiqiancha-prod` 已创建并配置生命周期，后端已新增 OSS 存储后端；生产 ECS 已配置环境变量并通过 `upload_storage=oss` 验证
- SLS：后端日志检索与告警
- Redis：验证码、缓存、临时会话或限流。当前已购买 256MB 实例，并已在 `server-go` 中接入认证、App 自动日志接收、帮助与反馈用户发消息和上传短期限流；生产 ECS 已配置 `REDIS_*` 且 `/healthz redis=ok`。当前不得写成已接管聊天流、额度、归档、摘要或订单

## 建议采购顺序

1. 已完成：Region 选定 `cn-beijing`，旧 SAE demo 应用已删除，域名 `nongjiqiancha.cn` 已购买并口头确认过审，ECS / RDS MySQL / OSS 100GB 存储包已购买
2. 已完成：ECS 基础系统环境、Nginx、systemd、RDS 数据库 / 账号 / 白名单和 `server-go` 首版部署
3. 已完成：购买 Redis 开源版 256MB 最小实例，放入生产 VPC，并把普通短信验证码发送 / 登录校验、App 自动日志接收、帮助与反馈用户发消息、上传短期限流接到 Redis；生产 ECS 已部署验证 `redis=ok`
4. 下一步：跟进 App 公安备案、真机登录 / 主聊天 / 图片问诊回归、上线前 AccessKey 轮换、第一封 SLS 告警邮件送达确认和旧包检查更新覆盖安装回归；App 备案和网站公安备案号已补到对应展示位置，SLS 最小日志集、AlertHub 最小告警、公网黑盒只读巡检脚本、低成本公网黑盒 timer 告警和上线总门禁脚本已先接入用于排障

## 采购前必须拍板的问题

- 生产环境当前优先按北京 ECS 单环境跑通；北京 SAE demo 应用已删除，是否后续重新启用 SAE / 拆测试或预发环境仍待真实联调后确认
- 数据库首版直接使用 RDS MySQL；当前自动备份 7 天、白名单 / VPC 连接和应用账号已落地，后续是否调备份保留天数、升级规格或迁移 PolarDB 只按真实用户量、慢查询、连接数和成本再定
- 图片首版按 OSS 短期保存推进；资源包、Bucket、生命周期、代码后端、生产 ECS 最小权限凭证、环境变量和部署验证均已就绪
- SLS 外部通知 / 仪表盘已按邮件 + 最小图表首版启用；当前专用 SLS 最小日志集和 AlertHub 最小告警已接入，仍保留 `journalctl` / Nginx 本地日志作为兜底；首封告警邮件送达仍需真实或测试触发确认
- 是否一开始就拆测试 / 生产两套环境，还是先单环境跑通
- 首版已接 OSS；若计划两台 ECS 或回到 SAE 多实例，仍需先用真实 App 链路验证图片上传、读取和模型拉图
- 多实例发布前，数据库迁移是否改成单独发布步骤或加迁移锁，避免多个实例首次启动同时跑迁移
- RDS 规格确认后，`MYSQL_MAX_OPEN_CONNS`、`MYSQL_MAX_IDLE_CONNS`、`MYSQL_CONN_MAX_IDLE_SECONDS`、`MYSQL_CONN_MAX_LIFETIME_SECONDS` 是否按实例数和连接数上限重新配置

## 扩容与升级判断

- 首版 RDS MySQL 1 核 2GB / 50GB 先服务早期真实联调和小流量内测；图片二进制不进数据库，后续应优先落 OSS
- 真实容量巡检、到期提醒和升级阈值以 [resource-capacity.md](D:/wuhao/docs/runbooks/resource-capacity.md) 为准；后续只要 CLI / 监控发现资源接近阈值，应提前提醒用户升级、续费或购买资源包
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

- 只把已真实落地的资源写成既成事实：当前可写旧 SAE demo 应用已删除、ECS 实例 `i-2ze5nrem0jrchln4f0eh`、RDS 实例 `rm-2zes3vmj76p85n8g1`、RDS 库 / 账号 / 白名单、ECS 上运行中的 `server-go`、`api.nongjiqiancha.cn` DNS / HTTPS / Nginx 443、根域名官网 DNS / HTTPS / Nginx 静态站、域名 `nongjiqiancha.cn`、网站 ICP 备案号、App 备案号、网站公安备案号、OSS 100GB 存储包、OSS Bucket `nongjiqiancha-prod` 与生命周期、Redis 实例、SLS 服务开通状态和模型 Key 槽位配置状态；模型 Key 只记录配置状态不记录真实值，App 公安备案号和未真实落地的后台入口仍不得伪造
- 未真正采购 / 配置完成前，runbook 只记录“要拍板什么”和“买完后补哪里”，不伪造部署命令
- 一旦出现第一套真实环境，必须同次把对应 runbook 补成可执行入口
- Go 后端首版不做盲目性能调参；先用默认连接池和单实例 / 小规格跑通，接入 SLS / RDS 监控后再按真实连接数、慢查询、SSE 中断率和模型限流调参

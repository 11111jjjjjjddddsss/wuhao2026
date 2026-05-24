# 上线前基础设施准备清单

最后更新：2026-05-24

## 目的

在正式购买云资源前，把“农技千查”上线所需的基础设施、依赖顺序、关键配置和补文档动作先固定下来，避免后面边买边猜。

## 当前现状

- Android 客户端与 `server-go` 后端代码主线已存在
- 仓库内已有 SAE / 日志 / 回滚 / 数据库只读 runbook 骨架；[operations-blueprint.md](D:/wuhao/docs/runbooks/operations-blueprint.md) 已把后期 Codex 协助整体 App、后端、管理后台、发布、回滚、日志和数据运维的范围先固定下来
- 下一阶段上线推进顺序已沉淀到 [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md)：买服务器 / 域名后立刻启动 ICP / App 备案，手机号登录、SAE、RDS、OSS、SLS 和真实接口联调在备案等待期间并行推进
- 买服务器前功能巡检记录已开始沉淀到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)：当前已巡检会员中心 / 额度体系，以及 Go 后端高并发 / 性能边界
- 正式云资源已部分落地：Region 选定 `华北2（北京）/ cn-beijing`；标准版 SAE 应用 `nongjiqiancha` 已创建，AppId `366147d5-3760-4548-bd68-f38debbc5f23`，规格 `0.5 核 / 1GB / 单实例`，自动弹性未开启，当前仍是默认 demo 镜像；域名 `nongjiqiancha.cn` 已购买，仍待实名认证 / 模板审核、DNS、备案、HTTPS 和 SAE 绑定
- 当前尚未购买 / 接入：RDS MySQL、OSS、SLS、Redis、真实后端镜像仓库 / 部署流水线

## 最小上线资源清单

### 必需

- 阿里云 SAE：部署 `server-go`。当前 SAE 壳子已创建，但还没部署真实后端镜像
- 阿里云 RDS MySQL：首版主业务数据。PolarDB 暂作为后续高并发 / 更高规格升级选项，不再作为个人创业首版默认采购项。当前 RDS 尚未购买
- 域名与 HTTPS 证书：对外 API 入口。当前 `nongjiqiancha.cn` 已购买，但正式 API 域名、证书和 SAE 绑定尚未完成
- 基础密钥与环境变量托管：模型 Key、数据库连接、JWT / Session 密钥等

### 高优先级可选

- OSS：图片上传与持久化
- SLS：后端日志检索与告警
- Redis：验证码、缓存、临时会话或限流

## 建议采购顺序

1. 已完成：Region 选定 `cn-beijing`，SAE 壳子已创建，域名 `nongjiqiancha.cn` 已购买
2. 下一步：购买 RDS MySQL，并确认与 SAE 同 Region / 同 VPC 或可连通网络
3. 然后补域名解析、HTTPS、真实后端镜像部署、OSS、SLS
4. 最后再看 Redis 是否要在首版一起上

## 采购前必须拍板的问题

- 生产环境当前先用已创建的北京 SAE 单环境跑通；是否后续拆测试 / 预发环境仍待真实联调后确认
- 数据库首版倾向直接使用 RDS MySQL；当前只需确认规格、备份策略、连接白名单 / VPC 连接和后续是否需要平滑升级 PolarDB
- 图片是否首版就落 OSS；如果不是，前端图片能力要不要先受限
- 日志是否首版就接 SLS；如果不是，后端最小日志保留方案是什么
- 是否一开始就拆测试 / 生产两套环境，还是先单环境跑通
- 首版若不接 OSS，SAE 是否明确保持单实例；若计划多实例，图片上传必须先接 OSS 或等价共享对象存储
- 多实例发布前，数据库迁移是否改成单独发布步骤或加迁移锁，避免多个实例首次启动同时跑迁移
- RDS 规格确认后，`MYSQL_MAX_OPEN_CONNS`、`MYSQL_MAX_IDLE_CONNS`、`MYSQL_CONN_MAX_IDLE_SECONDS`、`MYSQL_CONN_MAX_LIFETIME_SECONDS` 是否按实例数和连接数上限重新配置

## 资源买完后必须回填仓库的地方

- `docs/runbooks/deploy-sae.md`：真实发版入口、命令、成功判定
- `docs/runbooks/rollback.md`：真实回滚入口
- `docs/runbooks/logs-sls.md`：真实日志项目 / 查询入口
- `docs/runbooks/db-readonly.md`：真实数据库只读连接方式
- `docs/runbooks/operations-blueprint.md`：回填真实 App / 后端 / 管理后台运维入口和权限边界
- `docs/project-state/current-status.md`：环境现状
- `docs/project-state/open-risks.md`：关闭“未采购正式云资源”相关风险
- `docs/project-state/pending-decisions.md`：移除已拍板项

## 暂行原则

- 只把已真实落地的资源写成既成事实：当前可写 SAE 应用 `nongjiqiancha` 和域名 `nongjiqiancha.cn`；RDS、OSS、SLS、HTTPS 证书、备案号和后台地址仍不得伪造
- 未真正采购 / 配置完成前，runbook 只记录“要拍板什么”和“买完后补哪里”，不伪造部署命令
- 一旦出现第一套真实环境，必须同次把对应 runbook 补成可执行入口
- Go 后端首版不做盲目性能调参；先用默认连接池和单实例 / 小规格跑通，接入 SLS / RDS 监控后再按真实连接数、慢查询、SSE 中断率和模型限流调参

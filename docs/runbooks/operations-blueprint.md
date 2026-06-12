# Codex 协助运维总蓝图

最后更新：2026-06-10

## 目的

把后期“农技千查”整体 App 和后端怎么运维先存进仓库，避免以后买服务器、发版本、查问题、做管理后台时再从聊天记录里翻。

第一版网页后台代码已落地并部署到 `https://admin.nongjiqiancha.cn/`；`api.nongjiqiancha.cn` HTTPS、根域名官网 HTTPS、管理后台 HTTPS、SLS 最小日志集和 5 条 SLS AlertHub 最小告警已落地，下载域名仍需后续单独配置。本文记录当前真实环境和后续生产运维里 Codex 应该协助你的范围、入口和安全边界。

## 总原则

- 你用自然语言拍板，例如“发新版”“停更新”“查今天发送失败”“给这个用户补权益”
- Codex 负责读代码、读 runbook、核对官方文档、执行脚本 / CLI / OpenAPI / 管理后台操作
- 危险动作必须先确认，包括发版、回滚、改环境变量、改数据库、补会员权益、发礼品卡、删除或导出用户数据
- 账号、密钥、支付后台和阿里云二次验证仍由你掌握；仓库只记录密钥来源和注入方式，不记录真实密钥
- 每次形成真实操作入口后，同步更新 runbook、项目记忆并提交推送

## 覆盖范围

### Android 整体 App

- 版本号和 `versionCode` 管理
- debug / release 构建、签名、APK 产物检查
- 自有服务器 APK 检查更新：`GET /api/app/update`、后台 `app_release_configs` 发布配置、HTTPS APK 下载链接、SHA-256、文件大小和 `APP_ANDROID_*` 环境变量兜底
- 真机回归：聊天、图片、会员中心、帮助与反馈、礼品卡、检查更新、设置页、预览面板
- Baseline Profile / 关键路径性能回归
- 崩溃、弱网、清数据、后台恢复、输入法和不同导航模式检查

### Go 后端 `server-go`

- ECS 部署、环境变量、域名、HTTPS、健康检查；SAE 当前仅保留历史备选文档
- `/api/chat/stream`、图片上传、会员额度、摘要、今日农情、帮助与反馈、检查更新等接口运维
- 日志、错误率、模型调用失败、SSE 中断、上传失败、摘要失败、今日农情生成失败排查
- Go 后端统一请求日志：`X-Request-Id`、`http_request`、`http_request_slow`、`http_request_error`，以及只读脚本 [query-ecs-logs.ps1](D:/wuhao/scripts/query-ecs-logs.ps1)
- App 自动日志接收：`POST /api/app/logs`、`client_app_logs`、后台监控面板、SLS 最小日志集和 SLS AlertHub 最小告警接入
- 回滚、停更、临时降级开关和灰度策略

### 数据和后台业务

- RDS MySQL / MySQL 兼容数据库只读排查
- 数据库迁移、备份、恢复和对账
- 用户、会员、加油包、升级补偿、礼品卡、订单、客服反馈、今日农情、版本发布记录
- 生产数据修改必须走脚本或后台操作并保留审计，不在聊天里随手拼危险 SQL

### 日志、监控和成本

- ECS / SLS / ARMS 或后续等价监控入口
- Android 关键失败事件自动上报：SSE 中断、上传失败、快照失败、帮助与反馈失败、检查更新失败
- 每日请求量、失败率、模型 tokens、搜索调用、图片上传量、成本估算
- 关键告警：接口 5xx、SSE 大量中断、上传失败、额度扣减异常、数据库连接异常、今日农情连续失败、检查更新 APK 下载异常

## 统一管理后台的阶段规划

### P0：服务器落地前规划（历史阶段）

- 保留当前 runbook 和项目记忆
- 继续用内部接口、脚本和手工真机回归兜底
- 不提前硬做完整后台，避免账号体系、权限和真实数据结构没定就返工

该阶段已结束：ECS / RDS / Redis / OSS / DNS / 部署脚本、部分内部接口和第一版网页后台代码已经落地。P1 后台入口也已部署到 `admin.nongjiqiancha.cn`，并完成后台域名、Nginx、HTTPS、bootstrap 账号和 bootstrap 环境变量清理。

### P1：服务器落地后，先做最小网站运营入口

- 已落第一版代码并上线：Vite `admin` 前端 + `server-go` `/admin-api/v1/*`，覆盖登录、总览、监控面板、用户、会员额度、订单、礼品卡、帮助反馈、App 日志、今日农情、检查更新、审计和服务健康。
- 当前监控面板已接真实业务表、App 自动日志、后台审计、健康检查、地区聚合和客服反馈状态队列；红黄绿状态用于早期排障，不伪造未接入能力。礼品卡“可用”按当前可兑换口径统计，完整卡码可在后台页面内查看 / 复制，App 错误 Top 尊重 Top10 limit，检查更新把配置合法性和正式下载物料齐全度分开展示，监控入口会按后台角色裁剪，避免无权限账号点进 403。
- 继续补齐：SLS 外部通知 / 仪表盘、数据库只读脚本、客服正式坐席分配 / 标签、发布记录表、礼品卡批量发放 / 发放对象管理、高风险操作二次确认和支付正式订单链路。

### P2：接支付和礼品卡后

- 会员 / 订阅 / 加油包订单查询
- 礼品卡生成、发放、兑换、作废、使用记录
- 用户权益补偿和人工调整，但必须有操作人、原因、时间和变更前后值
- 成本面板：模型输入 / 输出 tokens、搜索次数、图片上传量、用户成本分布

### P3：正式运维后台

- 后台账号、角色权限、操作审计
- 发布 / 回滚 / 停更开关
- 告警中心和日报
- 数据导出、用户删除、隐私合规流程
- 生产 / 测试环境隔离

## 你以后怎么说就行

- “发一个新版本”：Codex 检查 Android 和后端改动，构建 APK，更新发布 runbook，配置检查更新
- “先停这个更新”：Codex 优先在管理后台取消“对外启用更新”并验证旧 App 不再提示坏包；必要时再清空兜底 `APP_ANDROID_*`
- “查一下今天为什么发不出去”：Codex 查 App 现象、后端日志、SLS、数据库和相关接口
- “给某个用户补次数”：Codex 先查用户状态，提出变更 SQL / 后台操作，等你确认后执行并记录
- “客服有新消息吗”：Codex 查帮助与反馈后台入口或未来管理后台
- “今天成本咋样”：Codex 汇总模型 tokens、搜索、图片和用户请求量

## 服务器落地后已回填 / 仍需回填

- 已回填：阿里云 Region、ECS 实例、RDS MySQL、Redis、OSS bucket、DNS `api/@/www/admin` A 记录、`api.nongjiqiancha.cn` HTTPS / Nginx 443、根域名官网 HTTPS / 静态站、管理后台 HTTPS / 静态站 / `/admin-api/` 反代、ECS 后端双端口部署 / 回滚脚本、官网部署脚本、管理后台部署脚本、生产 readiness 检查、Go 后端请求级日志、ECS 日志只读查询脚本、农技千查专用 SLS Project / Logstore、SLS 只读查询脚本、帮助与反馈内部会话列表 / 详情 / 回复接口、App 自动日志内部查询、今日农情内部生成入口、第一版网页后台代码、监控面板和后台账号 / session / CSRF / 角色 / 审计骨架。
- 仍需回填：App 备案通过、App 公安备案后的正式公网入口、SLS 外部通知 / 仪表盘、数据库只读排查脚本、App release 发布记录表或后台发布入口；下载域名若启用，需要单独补入口和证书。网站 ICP 备案已于 2026-06-05 通过，App 备案已提交阿里云初审，网站公安备案号已下发并已补官网 footer，公安备案数据码不写入仓库或聊天交接。
- 若后续重新启用 SAE，再补新的 SAE 应用名 / 应用 ID 和镜像部署 / 回滚入口。
- 管理后台权限细化、二次确认、高风险动作和审计验收

## 当前不做的事

- 不伪造还没购买的服务器实例名、域名、数据库地址
- 不把真实密钥写进仓库
- 不做静默安装 APK；Android 普通 App 只能调起系统安装确认
- 不在生产后台高风险权限和二次确认未验收前，把高风险运营动作一次性打开
- 不让 Android 客户端承担后端业务真相；会员、礼品卡、扣次、模型调用仍以后端为准

## 已有入口

- [infra-readiness.md](D:/wuhao/docs/runbooks/infra-readiness.md)：正式云资源采购前准备清单
- [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md)：下一阶段上线推进计划
- [deploy-ecs.md](D:/wuhao/docs/runbooks/deploy-ecs.md)：ECS 后端发版入口
- [deploy-sae.md](D:/wuhao/docs/runbooks/deploy-sae.md)：SAE 历史备选入口
- [rollback.md](D:/wuhao/docs/runbooks/rollback.md)：后端回滚入口
- [logs-sls.md](D:/wuhao/docs/runbooks/logs-sls.md)：日志排查入口
- [app-client-logs.md](D:/wuhao/docs/runbooks/app-client-logs.md)：App 自动日志接收入口
- [db-readonly.md](D:/wuhao/docs/runbooks/db-readonly.md)：数据库只读排查入口
- [app-update.md](D:/wuhao/docs/runbooks/app-update.md)：Android APK 检查更新和停更入口
- [support-feedback.md](D:/wuhao/docs/runbooks/support-feedback.md)：帮助与反馈站内消息入口
- [gift-card.md](D:/wuhao/docs/runbooks/gift-card.md)：礼品卡后端、后台批次和用户侧兑换接口
- [legal-privacy.md](D:/wuhao/docs/runbooks/legal-privacy.md)：服务协议、隐私政策、风险提示和权限清单入口
- [today-agri-card.md](D:/wuhao/docs/runbooks/today-agri-card.md)：今日农情生成与排查入口
- [management-backend.md](D:/wuhao/docs/runbooks/management-backend.md)：统一管理后台分期、权限和审计入口
- [payments.md](D:/wuhao/docs/runbooks/payments.md)：真实支付、会员订单、回调验签和对账入口
- [scaling-readiness.md](D:/wuhao/docs/runbooks/scaling-readiness.md)：后续单机升级、多 ECS、数据库扩容和轻量队列预案

## 参考资料

- 当前首版优先 ECS，后续补 ECS 部署和日志入口时再把真实官方链接回填到本文。
- 以下 SAE 资料仅作为历史备选路线参考；若后续重新启用 SAE，需重新按官方文档复核并更新 runbook：
- [阿里云 SAE 应用托管概述](https://help.aliyun.com/zh/sae/application-deployment-overview)：SAE 支持应用生命周期管理、部署、升级回滚、日志和监控等运维能力
- [阿里云 SAE 分批发布和回滚](https://help.aliyun.com/zh/sae/perform-a-phased-release-for-an-application)：多实例时可分批发布，异常时可回滚；单实例或早期低成本阶段仍要优先用低峰发布和明确回滚入口兜底
- [阿里云 SAE 日志收集到 SLS](https://help.aliyun.com/zh/sae/serverless-app-engine-classic/user-guide/configure-log-collection-to-log-service)：真实接入日志前需要按当前官方文档复核 Project / Logstore / 采集配置
- [阿里云 SAE 环境变量](https://www.alibabacloud.com/help/doc-detail/96560.html)：真实配置 `APP_ANDROID_*`、模型 Key、数据库连接等变量前需要按当前官方文档复核

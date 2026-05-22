# Codex 协助运维总蓝图

最后更新：2026-05-17

## 目的

把后期“农技千查”整体 App 和后端怎么运维先存进仓库，避免以后买服务器、发版本、查问题、做管理后台时再从聊天记录里翻。

这份文档不是现成后台，也不伪造还没购买的服务器信息；它记录后续真实环境落地后，Codex 应该协助你的范围、入口和安全边界。

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
- 自有服务器 APK 检查更新：`GET /api/app/update`、APK 下载链接、SAE `APP_ANDROID_*` 环境变量
- 真机回归：聊天、图片、会员中心、帮助与反馈、礼品卡、检查更新、设置页、预览面板
- Baseline Profile / 关键路径性能回归
- 崩溃、弱网、清数据、后台恢复、输入法和不同导航模式检查

### Go 后端 `server-go`

- SAE 部署、环境变量、域名、HTTPS、健康检查
- `/api/chat/stream`、图片上传、会员额度、摘要、今日农情、帮助与反馈、检查更新等接口运维
- 日志、错误率、模型调用失败、SSE 中断、上传失败、摘要失败、今日农情生成失败排查
- 回滚、停更、临时降级开关和灰度策略

### 数据和后台业务

- RDS MySQL / MySQL 兼容数据库只读排查
- 数据库迁移、备份、恢复和对账
- 用户、会员、加油包、升级补偿、礼品卡、订单、客服反馈、今日农情、版本发布记录
- 生产数据修改必须走脚本或后台操作并保留审计，不在聊天里随手拼危险 SQL

### 日志、监控和成本

- SAE / SLS / ARMS 或后续等价监控入口
- 每日请求量、失败率、模型 tokens、搜索调用、图片上传量、成本估算
- 关键告警：接口 5xx、SSE 大量中断、上传失败、额度扣减异常、数据库连接异常、今日农情连续失败、检查更新 APK 下载异常

## 统一管理后台的阶段规划

### P0：买服务器前

- 保留当前 runbook 和项目记忆
- 继续用内部接口、脚本和手工真机回归兜底
- 不提前硬做完整后台，避免账号体系、权限和真实数据结构没定就返工

### P1：服务器落地后，先做最小运营入口

- 帮助与反馈：按用户查看会话、回复客服消息、查看未读 / 未处理
- 用户查询：按 `user_id` 查会员等级、剩余额度、最近请求和最近反馈
- 检查更新：查看当前发布版本、APK 链接、是否启用更新
- 今日农情：查看当天生成状态、失败原因、手动补跑

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
- “先停这个更新”：Codex 清空或下调 `APP_ANDROID_*`，验证旧 App 不再提示坏包
- “查一下今天为什么发不出去”：Codex 查 App 现象、后端日志、SLS、数据库和相关接口
- “给某个用户补次数”：Codex 先查用户状态，提出变更 SQL / 后台操作，等你确认后执行并记录
- “客服有新消息吗”：Codex 查帮助与反馈后台入口或未来管理后台
- “今天成本咋样”：Codex 汇总模型 tokens、搜索、图片和用户请求量

## 买服务器后必须回填

- 阿里云 Region、账号归属、命名规范
- SAE 应用名 / 应用 ID、部署方式、健康检查、实例数、环境变量来源
- RDS MySQL 实例、库名、只读账号、备份策略、白名单
- 域名、HTTPS 证书、公开 API 基地址
- OSS bucket、上传目录、公开访问策略或签名访问策略
- SLS Project / Logstore、关键日志查询、告警规则
- Redis 是否接入、用途和过期策略
- 管理后台域名、后台登录方式、权限模型和审计表

## 当前不做的事

- 不伪造还没购买的服务器实例名、域名、数据库地址
- 不把真实密钥写进仓库
- 不做静默安装 APK；Android 普通 App 只能调起系统安装确认
- 不在没有正式账号 / 权限 / 审计设计前，把完整管理后台一次性做重
- 不让 Android 客户端承担后端业务真相；会员、礼品卡、扣次、模型调用仍以后端为准

## 已有入口

- [infra-readiness.md](D:/wuhao/docs/runbooks/infra-readiness.md)：正式云资源采购前准备清单
- [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md)：下一阶段上线推进计划
- [deploy-sae.md](D:/wuhao/docs/runbooks/deploy-sae.md)：SAE 后端发版入口
- [rollback.md](D:/wuhao/docs/runbooks/rollback.md)：后端回滚入口
- [logs-sls.md](D:/wuhao/docs/runbooks/logs-sls.md)：日志排查入口
- [db-readonly.md](D:/wuhao/docs/runbooks/db-readonly.md)：数据库只读排查入口
- [app-update.md](D:/wuhao/docs/runbooks/app-update.md)：Android APK 检查更新和停更入口
- [support-feedback.md](D:/wuhao/docs/runbooks/support-feedback.md)：帮助与反馈站内消息入口
- [gift-card.md](D:/wuhao/docs/runbooks/gift-card.md)：礼品卡占位状态与后续兑换系统入口
- [today-agri-card.md](D:/wuhao/docs/runbooks/today-agri-card.md)：今日农情生成与排查入口

## 参考资料

- [阿里云 SAE 应用托管概述](https://help.aliyun.com/zh/sae/application-deployment-overview)：SAE 支持应用生命周期管理、部署、升级回滚、日志和监控等运维能力
- [阿里云 SAE 分批发布和回滚](https://help.aliyun.com/zh/sae/perform-a-phased-release-for-an-application)：多实例时可分批发布，异常时可回滚；单实例或早期低成本阶段仍要优先用低峰发布和明确回滚入口兜底
- [阿里云 SAE 日志收集到 SLS](https://help.aliyun.com/zh/sae/serverless-app-engine-classic/user-guide/configure-log-collection-to-log-service)：真实接入日志前需要按当前官方文档复核 Project / Logstore / 采集配置
- [阿里云 SAE 环境变量](https://www.alibabacloud.com/help/doc-detail/96560.html)：真实配置 `APP_ANDROID_*`、模型 Key、数据库连接等变量前需要按当前官方文档复核

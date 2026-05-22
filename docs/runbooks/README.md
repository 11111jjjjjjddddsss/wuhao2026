# 运维 Runbook 入口

本目录用于沉淀“可审计、可交接、可复用”的运维入口，目标是让 Codex 和人工在换窗口、换人、换时间后都能快速找到正确入口。

当前 runbook：

- [chat-ui-regression.md](D:/wuhao/docs/runbooks/chat-ui-regression.md)：聊天 UI 回归与接手入口
- [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md)：下一阶段上线推进计划
- [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)：买服务器前按功能逐个深度巡检的记录
- [infra-readiness.md](D:/wuhao/docs/runbooks/infra-readiness.md)：正式云资源采购前的基础设施准备清单
- [operations-blueprint.md](D:/wuhao/docs/runbooks/operations-blueprint.md)：Codex 协助整体 App / 后端 / 管理后台运维的总蓝图
- [deploy-sae.md](D:/wuhao/docs/runbooks/deploy-sae.md)：SAE 发版入口
- [rollback.md](D:/wuhao/docs/runbooks/rollback.md)：回滚入口
- [logs-sls.md](D:/wuhao/docs/runbooks/logs-sls.md)：日志排查入口
- [db-readonly.md](D:/wuhao/docs/runbooks/db-readonly.md)：数据库只读排查入口
- [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md)：后端模型 Key 池和多账号限流排查入口
- [today-agri-card.md](D:/wuhao/docs/runbooks/today-agri-card.md)：今日农情每日卡片生成与排查入口
- [support-feedback.md](D:/wuhao/docs/runbooks/support-feedback.md)：帮助与反馈站内消息和后台回复入口
- [app-update.md](D:/wuhao/docs/runbooks/app-update.md)：自有服务器 APK 分发与检查更新入口
- [gift-card.md](D:/wuhao/docs/runbooks/gift-card.md)：礼品卡占位状态与后续兑换系统入口
- [legal-privacy.md](D:/wuhao/docs/runbooks/legal-privacy.md)：服务协议、隐私政策、风险提示、第三方清单和权限口径
- [management-backend.md](D:/wuhao/docs/runbooks/management-backend.md)：统一管理后台分期、权限和审计入口

维护规则：

- 只写当前有效入口，不堆过期截图和一次性口令
- 若实际操作步骤变化，必须同次更新对应 runbook
- 涉及密钥时只记录来源和注入方式，不把密钥写进仓库

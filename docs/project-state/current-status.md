# 当前状态

最后更新：2026-04-14

## 项目概况

- 项目：农技千问
- 目标：面向农作物问题提供农业技术参考建议，不提供绝对诊断
- 当前仓库两条主线：Android 客户端 `app`，Go 后端 `server-go`

## 当前代码真相

- Android 端当前使用 Jetpack Compose 聊天界面，不再依赖 WebView 模板页面
- 聊天列表当前唯一底座是 `LazyColumn`；`ChatRecyclerViewHost.kt` 只是历史文件名残留，运行时已无 active `RecyclerView` 链
- 发送起步锚点当前围绕 assistant waiting 宿主的真实可见底边对齐工作线，不再依赖 waiting 壳高度估算
- 后端是唯一业务真相来源，前端只负责 UI、输入与展示
- 主对话锚点与摘要提示词真源位于 `server-go/assets`
- Android 与 Go 均已有基础 CI，但项目交接记忆、ADR、运维 runbook 体系此前缺失，已从本次开始补齐

## 当前交接入口

- 主规则：`AGENTS.md`
- Android 局部补充：`app/AGENTS.md`
- Go 后端局部补充：`server-go/AGENTS.md`
- 当前风险：`docs/project-state/open-risks.md`
- 待定事项：`docs/project-state/pending-decisions.md`
- 近期变更：`docs/project-state/recent-changes.md`
- 关键决策：`docs/adr`
- 运维与排障入口：`docs/runbooks`

## 当前阶段判断

- 代码主干已存在，当前更需要的是把“规则、状态、方案取舍、运维入口”固化进仓库，降低换窗口和长期接手成本
- 目前运维 runbook 还是骨架版，真实部署、回滚、日志、只读查库命令还需要后续在实际运维过程中逐步补实

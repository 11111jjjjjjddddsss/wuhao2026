# 当前状态

最后更新：2026-04-15

## 项目概况

- 项目：农技千问
- 目标：面向农作物问题提供农业技术参考建议，不提供绝对诊断
- 当前仓库两条主线：Android 客户端 `app`，Go 后端 `server-go`

## 当前代码真相

- Android 端当前使用 Jetpack Compose 聊天界面，不再依赖 WebView 模板页面
- 聊天列表当前唯一底座是 `LazyColumn(reverseLayout = true)`；`ChatRecyclerViewHost.kt` 只是历史文件名残留，运行时已无 active `RecyclerView` 链
- 运行时消息状态仍保持正常时间顺序，但传给列表的显示顺序已改为 `asReversed()`；视觉上最新消息固定贴近底部工作线
- 发送起步当前不再走正向列表那套“先算 offset 再 requestScrollToItem(index, offset)”链；发送时只在用户明确滑离底部锚点后才请求 `requestScrollToItem(0)` 回到底部，底部附近的小偏移不再强推
- 小球所在的 assistant waiting 宿主当前依然是发送起步锚点；反向底座下它天然贴近工作线，用户消息自然位于其上方
- `ChatScrollCoordinator` 当前已不再在 streaming 期间主动 `scrollBy` 追工作线；反向底座下 AutoFollow 只保留控制权切换，底部锚定主要交给 `reverseLayout` 的天然行为
- 远端历史 hydrate 当前已不再用 `replaceMessages(clear + addAll)` 整表重建；`replaceMessages(...)` 改为按消息 `id` 做原地增量更新，减少冷启动/恢复阶段的整表震荡
- waiting/streaming 首行的物理高度已固定化，避免首字出现时宿主高度突变
- 输入框已回到单行且未聚焦时，列表底部保留高度当前优先继续走稳定单行高度，不再立即切回实时 `composerTop` 测量，减少锚点刚对齐后又被底部几何改写
- 发送窗口（`sendStartBottomPaddingLockActive`）当前只继续承担输入区收口期的几何稳定职责，不再冻结视口高度，也不再参与发送起步 offset 计算
- 所有只服务正向底座的发送起步变量都已退出主链：`pendingStartAnchorScrollOffsetPx`、`sendStartViewportHeightPx`、`sendStartWorklineBottomPx` 已删除
- 首次进入聊天页当前直接 `scrollToItem(0)` 贴到底部；从后台切回时不默认自动贴底
- 回到底部按钮在 streaming 场景下当前也直接走 `scrollToBottom(false)`，不再额外走正向列表的底边差值补推
- 本地 fake streaming 在切后台时改为同步收口成 completed 消息，并同步写回本地聊天窗口、清掉 streaming draft，避免秒切后台/前台时把半截流式状态带回屏幕
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

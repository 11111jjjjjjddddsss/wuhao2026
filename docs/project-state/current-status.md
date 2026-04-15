# 当前状态

最后更新：2026-04-16

## 项目概况

- 项目：农技千问
- 目标：面向农作物问题提供农业技术参考建议，不提供绝对诊断
- 当前仓库两条主线：Android 客户端 `app`，Go 后端 `server-go`

## 当前代码真相

- Android 端当前使用 Jetpack Compose 聊天界面，不再依赖 WebView 模板页面
- 聊天列表当前唯一底座是 `LazyColumn(reverseLayout = true)`；`ChatRecyclerViewHost.kt` 只是历史文件名残留，运行时已无 active `RecyclerView` 链
- 运行时消息状态仍保持正常时间顺序，但传给列表的显示顺序已改为 `asReversed()`；视觉上最新消息固定贴近底部工作线
- 发送起步当前不再走正向列表那套“先算 offset 再 requestScrollToItem(index, offset)”链；发送时会在插入用户消息和 assistant placeholder 的同一发送事务里立即请求 `requestScrollToItem(0)` 回到底部锚点，用来覆盖 `LazyColumn` 对旧可见项的默认位置保护，避免新插入的小球先悬空一拍再掉出视口
- 小球所在的 assistant waiting 宿主当前依然是发送起步锚点；反向底座下它天然贴近工作线，用户消息自然位于其上方
- `ChatScrollCoordinator` 当前已不再在 streaming 期间主动 `scrollBy` 追工作线；反向底座下 AutoFollow 只保留控制权切换，底部锚定主要交给 `reverseLayout` 的天然行为
- 远端历史 hydrate 当前已不再用 `replaceMessages(clear + addAll)` 整表重建；`replaceMessages(...)` 改为按消息 `id` 做原地增量更新，减少冷启动/恢复阶段的整表震荡
- waiting 小球与 streaming 首块当前已收敛到同一个 `ChatStreamingRenderer` 内容宿主里切换，不再走两套 streaming 宿主分支，进一步减少首字出现时的物理高度跳变
- streaming 分支最外层宿主当前已从 `TopStart` 改为 `BottomStart`，让正文在反向底座里沿同一物理底边向上生长，减少流式换行时“先往下掉一下再被拉回”的体感
- streaming / settled Markdown 正文当前都不再依赖父级 `Column(spacedBy(...))` 推块间距；块间距已改为挂在各 block 自身的 top padding 上，减少段落 / 列表新块诞生时把既有内容整体往下踹一拍
- 列表底部保留高度与工作线当前只在 streaming 进行时才参考实时 `composerTop` 测量；普通 idle 状态下，不论是否停在底部，列表都改回稳定 bottom bar / overlay 高度，避免聚焦输入框时把历史区或底部最新消息一起向上带动
- 会诊协作口径当前已收紧：后续针对 UI 抖动、滚动链、渲染时序这类问题，默认先由 Codex 本地锁定到具体代码点，再把文件路径、函数名、关键状态、已排除项和限制条件一起打包给 Gemini / Claude，避免外部方案继续停留在抽象猜测层
- 当前外部会诊现实约束已明确：Gemini / Claude 等外部模型默认看不到本地仓库和文件链接，只能依赖用户通过聊天软件转发的代码片段、日志、截图；因此会诊稿必须自包含，关键代码不能只报文件名不贴内容
- `sendStartBottomPaddingLockActive` 已退出工作线和 `recyclerBottomPaddingPx` 的运行时计算主链；`sendUiSettling` 与 `composerSettlingMinHeightPx / composerSettlingChromeHeightPx` 仍只服务输入框自身收口和 overlay 生命周期
- 所有只服务正向底座的发送起步变量都已退出主链：`pendingStartAnchorScrollOffsetPx`、`sendStartViewportHeightPx`、`sendStartWorklineBottomPx` 已删除
- 首次进入聊天页当前直接 `scrollToItem(0)` 贴到底部；从后台切回时不默认自动贴底
- 回到底部按钮当前只走 `scrollToBottom(false)` 的 `scrollToItem(0)` 主链，不再串 `alignChatListBottom()` 那套 8 帧 `scrollBy` 底边补偿；streaming 完成态也不再保留 `pendingFinalBottomSnap` 这类额外补滚
- 本地 fake streaming 在切后台时改为同步收口成 completed 消息，并同步写回本地聊天窗口、清掉 streaming draft，避免秒切后台/前台时把半截流式状态带回屏幕
- 本地 fake streaming 在正常结束时也不再等待 `currentStreamingOverflowDelta()` 这类旧 overflow 指标回落后才 finish；正文 reveal 完成后直接进入完成态收口
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

## 当前调试焦点

- 当前最值得新窗口优先接手的热点，仍是 Android 聊天 UI 的最后两处顽固抖动；接手时先看这里，再回到完整代码真相
- 焦点 1：发送瞬间整块消息区会轻微上下抖一下
  - 当前最可疑代码点：`ChatScreen.kt` 的 `commitSendMessage(...)`
  - 当前真实顺序仍是：先 `prepareComposerCollapse(...)`、`input.value = TextFieldValue("")`、`clearFocus/hide keyboard`，然后才 `upsertUserMessage(...)`、`upsertAssistantMessagePlaceholder(...)`、`requestScrollToItem(0)`
  - 下一刀的正确方向：保留发送门禁和 streaming 起始态原位，只把真正引发输入区高度塌陷的收口动作后移到“placeholder 已进入 Layout 且命中底部容差”之后
- 焦点 2：streaming 过程中正文仍会“往下掉一下再弹回”
  - 当前最可疑代码点：`ChatStreamingRenderer.kt` 的 `RendererAssistantStreamingContentImpl(...)`
  - 当前真实结构仍是：`completedModels.forEachIndexed { ... }` 一套 completed 分支，`activeModel?.let { ... }` 一套 active 分支
  - 下一刀的正确方向：参考同文件里 `RendererAssistantMarkdownContentImpl(...)` 已存在的单循环 + `blockModifier` 模式，把 streaming 改成块级同构宿主；不要再回到行级 `stableLines / activeLine` 层反复试错
- 已明确排除、不要再回滚的方向：
  - 旧 `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout` 主链
  - `alignChatListBottom()` 的 8 帧 `scrollBy` 补偿
  - `pendingFinalBottomSnap`
  - 发送链里的 `withFrameNanos` 延迟回底
  - 普通 idle 聚焦输入框时带着历史区一起联动
- 推荐回归入口：`docs/runbooks/chat-ui-regression.md`

## 当前阶段判断

- 代码主干已存在，当前更需要的是把“规则、状态、方案取舍、运维入口”固化进仓库，降低换窗口和长期接手成本
- 目前运维 runbook 还是骨架版，真实部署、回滚、日志、只读查库命令还需要后续在实际运维过程中逐步补实

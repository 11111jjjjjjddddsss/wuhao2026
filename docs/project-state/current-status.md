# 当前状态

最后更新：2026-04-17

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
- `RendererAssistantStreamingContentImpl(...)` 当前已改为 unified block host：completed / active blocks 先拍平成同一个 `unifiedModels` 列表，再由同一个外壳宿主承接 spacing / divider，active -> committed 交接时不再跨 sibling subtree 搬家
- unified streaming block 当前使用 append-only 场景下稳定的 block index 作为外壳 key，不再把 `hashCode()` 混进 key 里触发流式阶段的连续 remount
- streaming 分支最外层宿主当前已从 `TopStart` 改为 `BottomStart`，让正文在反向底座里沿同一物理底边向上生长，减少流式换行时“先往下掉一下再被拉回”的体感
- `ChatStreamingRenderer(...)` 当前继续收口到同一个最外层宿主：streaming / settled 都改为共用外层 `Column` 承接 `boundsReportingModifier` 和 `fillMaxWidth()`，streaming 所需的 `BottomStart` 对齐仅保留在内部 `Box`；这样生成完成从 streaming 切 settled 时，不再因为最外层 `Box -> Column` 换树而额外重测一拍
- `RendererAssistantStreamingCommittedBlockImpl(...)` 当前也不再直接把正文整段交给单个 `Text(annotated)` 自由换行；heading / quote / paragraph 以及 bullet / numbered 的正文都改为先基于 `AnnotatedString` 做逐行测量，再复用和 active block 一样的逐行堆叠布局，尽量减少 streaming -> settled 时行高 / 行间距算法切换带来的“最后一拍微调”
- `ChatRecyclerViewHost(...)` 当前已把动态底部留白从 `LazyColumn.contentPadding.bottom` 挪到了列表内部的专用 bottom spacer item；该 spacer 在 layout 阶段才读取 `recyclerBottomPaddingPx`，避免发送瞬间继续走 `composerTopInViewportPx -> composition phase padding` 这条一帧滞后的链路
- streaming / settled Markdown 正文当前都不再依赖父级 `Column(spacedBy(...))` 推块间距；其中 streaming 分支已不再把块间距直接挂在 unified block 外壳 modifier 上，而是改成在非首块前插入独立 `Spacer(height = MARKDOWN_BLOCK_SPACING)`，尽量减少新 block 诞生时把既有内容整体往下踹一拍
- streaming 期间 unified block 外壳当前已继续收口到单一测量实现：不论 block 逻辑状态是 completed 还是 active，流式渲染都统一复用 `RendererAssistantStreamingActiveBlockImpl(...)`；只有最后一个 active block 继续吃 fresh tail 高亮，避免 active -> committed 中途交接时因为内部测量树不同构而产生额外高度重算
- 列表底部保留高度与工作线当前只在 streaming 进行且不处于发送 / 输入区收口窗口时才参考实时 `composerTop`；`sendUiSettling` 或 `composerSettlingMinHeightPx / composerSettlingChromeHeightPx` 仍在结算时，列表会强制回退到稳定 bottom bar / overlay 高度，避免“输入框瞬间回缩 + 小球立即出现”这一拍把消息区一起抖动
- streaming 正常结束与本地 fake streaming 的后台同步完结，当前统一走“两阶段 finalize”收口：第一阶段先把最终内容落进 completed 消息并保留 streaming 几何口径，同时清掉该消息旧 streaming bounds；第二阶段等同一条消息的 completed fresh bounds 真正上报后，再原子切 `isStreaming / streamingMessageId / scrollRuntime`，并只在仍离底时按需单发 `requestScrollToItem(0)`。这样完成那一拍不再出现工作线口径、底部判定源、内容宿主同时换挡
- 发送链当前不再把“输入框收口”和“消息插入 + 回底请求”拆到两拍：`commitSendMessage()` 已把 `upsertUserMessage`、assistant placeholder、`prepareScrollRuntimeForStreamingStart(...)`、`requestScrollToItem(0)` 收回到同步 UI 事务里，网络/SSE 仅保留在后续协程，专门收“发送瞬间上下抖一下”的事务分帧问题
- 发送起步窗口当前额外放开了实时 composer 几何：`shouldUseRealtimeComposerGeometry` 现在在 `sendUiSettling == true` 时不再被 `isComposerSettling` 一刀切断，避免输入框瞬间清空回缩时，工作线和 bottom reserved height 先断崖回退、再配合 `requestScrollToItem(0)` 制造整块上下抖
- `ChatStreamingRenderer.kt` 当前已彻底移除 `rememberRendererLockedStreamingRenderedLinesImpl()` / `buildLockedStreamingActivePreview()` 这层 fresh line 锁预览，stable / active 行都直接用原始 `StreamingRenderedLines` 渲染；不再允许 activeLine 在某一拍被锁成预览串或空串，专门收口 streaming 过程中偶发“往下掉一下再弹回”的 1 帧高度塌陷
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

- 当前最值得新窗口继续装机回归的，仍是 Android 聊天 UI 的两处顽固体感问题；但代码主刀已落地，下一窗口不该再从抽象层重想方案，而应该先拿真机验证这两刀是否真正收口
- 焦点 1：发送瞬间整块消息区会轻微上下抖一下
  - 本轮已落地代码点：`ChatScreen.kt` 的 `isComposerSettling` / `shouldUseRealtimeComposerGeometry`
  - 当前真实顺序仍保持产品要求：先即时 `prepareComposerCollapse(...)`、`input.value = TextFieldValue("")`、`clearFocus/hide keyboard`，再 `upsertUserMessage(...)`、`upsertAssistantMessagePlaceholder(...)`、`requestScrollToItem(0)`
  - 本轮真实改法：没有再引入 delay / `withTimeoutOrNull` 延后收口，而是把发送和收口窗口里的实时 `composerTop` 联动冻结掉，让列表在稳定底部几何上完成回底
- 焦点 2：streaming 过程中正文仍会“往下掉一下再弹回”
  - 本轮已落地代码点：`ChatStreamingRenderer.kt` 的 `RendererAssistantStreamingContentImpl(...)`
  - 当前真实结构已不再是 `completedModels.forEachIndexed { ... } + activeModel?.let { ... }` 两棵 sibling subtree；streaming blocks 已改为单循环 unified host
  - 本轮真实改法：completed / active 共用同一个 block 外壳，append-only 外壳 key 收口到稳定 block index；同时 streaming 期间统一复用 `RendererAssistantStreamingActiveBlockImpl(...)` 这套测量路径，并把非首块间距改成外壳外的独立 `Spacer`
- 已明确排除、不要再回滚的方向：
  - 旧 `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout` 主链
  - `alignChatListBottom()` 的 8 帧 `scrollBy` 补偿
  - `pendingFinalBottomSnap`
  - 发送链里的 `withFrameNanos` 延迟回底
  - 发送链里的 `withTimeoutOrNull` 延后收口实验
  - 普通 idle 聚焦输入框时带着历史区一起联动
- 本轮新增回归修复点：
- `finishStreaming()` / `completeStreamingImmediatelyFromBackground()` 当前不再在完成同一拍直接切 `isStreaming = false`。新的主链是：先写 completed 消息，再用 `pendingStreamingFinalizeMessageId` 把 active assistant 临时切到 settled renderMode，等同一条消息的 fresh completed bounds 真正到位后，再一次性切掉 streaming 状态；第二阶段已不再使用 `200ms` 这类短超时硬切，并且后台期间会暂停等待、回到前台后再继续等 settled bounds，专门收口“生成结束后偶发上跳、底部留白”和“切后台再回来底部留白”的时序竞态
- 推荐回归入口：`docs/runbooks/chat-ui-regression.md`

## 当前阶段判断

- 代码主干已存在，当前更需要的是把“规则、状态、方案取舍、运维入口”固化进仓库，降低换窗口和长期接手成本
- 目前运维 runbook 还是骨架版，真实部署、回滚、日志、只读查库命令还需要后续在实际运维过程中逐步补实

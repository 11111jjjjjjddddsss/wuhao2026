# 当前状态

最后更新：2026-04-18

## 项目概况

- 项目：农技千问
- 目标：面向农作物问题提供农业技术参考建议，不提供绝对诊断
- 当前仓库两条主线：Android 客户端 `app`，Go 后端 `server-go`

## 当前代码真相

- Android 端当前使用 Jetpack Compose 聊天界面，不再依赖 WebView 模板页面
- 聊天列表当前唯一底座已切回正向 `LazyColumn(reverseLayout = false)`；`ChatRecyclerViewHost.kt` 只是历史文件名残留，运行时已无 active `RecyclerView` 链
- `ChatRecyclerViewHost.kt` 当前额外启用了 `LazyColumn(verticalArrangement = Arrangement.Bottom)`；在正向列表下，当历史内容总高度不足一屏时，列表会优先把短内容压在底部工作区，而不是默认趴到顶部遮罩下面
- 运行时消息状态与显示顺序当前保持一致，不再通过 `asReversed()` 翻转；旧消息在上，新消息在下
- 发送起步当前重新启用正向列表的单次 offset 起步：发送事务会基于工作线和 waiting 首行高度前馈计算 `startAnchorScrollOffsetPx`，再对 assistant placeholder 执行单次 `requestScrollToItem(index, offset)`，把小球起步宿主对齐到工作线
- 小球所在的 assistant waiting 宿主当前依然是发送起步锚点；在正向列表下它不再依赖“反向天然贴底”，而是依赖显式起步对位和后续底部归位
- `ChatScrollCoordinator` 当前重新承担 streaming 期间的继续贴底责任；用户未打断时，如果最新 assistant 宿主偏离工作线，AutoFollow 已不再对每个 fake-stream chunk 反复走 `scrollToBottom(false)` 整体重定位，而是只按当前底边偏差做单次 delta `scrollBy` 微调，优先压“假文本生成过程中像重叠一样持续闪烁”的体感
- 远端历史 hydrate 当前已不再用 `replaceMessages(clear + addAll)` 整表重建；`replaceMessages(...)` 改为按消息 `id` 做原地增量更新，减少冷启动/恢复阶段的整表震荡
- waiting 小球与 streaming 首块当前已收敛到同一个 `ChatStreamingRenderer` 内容宿主里切换，不再走两套 streaming 宿主分支，进一步减少首字出现时的物理高度跳变
- `RendererAssistantStreamingContentImpl(...)` 当前已改为 unified block host：completed / active blocks 先拍平成同一个 `unifiedModels` 列表，再由同一个外壳宿主承接 spacing / divider，active -> committed 交接时不再跨 sibling subtree 搬家
- unified streaming block 当前使用 append-only 场景下稳定的 block index 作为外壳 key，不再把 `hashCode()` 混进 key 里触发流式阶段的连续 remount
- streaming 分支最外层宿主当前仍保持 `BottomStart` 口径，让正文沿同一物理底边向上生长；这条修复与列表正反向无关，继续保留
- `ChatStreamingRenderer(...)` 当前继续收口到同一个最外层宿主：streaming / settled 都改为共用外层 `Column` 承接 `boundsReportingModifier` 和 `fillMaxWidth()`，streaming 所需的 `BottomStart` 对齐仅保留在内部 `Box`；这样生成完成从 streaming 切 settled 时，不再因为最外层 `Box -> Column` 换树而额外重测一拍
- `RendererAssistantStreamingCommittedBlockImpl(...)` 当前也不再直接把正文整段交给单个 `Text(annotated)` 自由换行；heading / quote / paragraph 以及 bullet / numbered 的正文都改为先基于 `AnnotatedString` 做逐行测量，再复用和 active block 一样的逐行堆叠布局，尽量减少 streaming -> settled 时行高 / 行间距算法切换带来的“最后一拍微调”
- streaming / settled Markdown 正文当前都不再依赖父级 `Column(spacedBy(...))` 推块间距；其中 streaming 分支已不再把块间距直接挂在 unified block 外壳 modifier 上，而是改成在非首块前插入独立 `Spacer(height = MARKDOWN_BLOCK_SPACING)`，尽量减少新 block 诞生时把既有内容整体往下踹一拍
- streaming 期间 unified block 外壳当前已继续收口到单一测量实现：不论 block 逻辑状态是 completed 还是 active，流式渲染都统一复用 `RendererAssistantStreamingActiveBlockImpl(...)`；只有最后一个 active block 继续吃 fresh tail 高亮，避免 active -> committed 中途交接时因为内部测量树不同构而产生额外高度重算
- 聊天页“消息列表 + composer”当前已改成共享 measure 宿主：`ChatScreen.kt` 里用 `SubcomposeLayout` 先测 composer，再把同一拍的真实底部 reserve 直接喂给 `ChatRecyclerViewHost` 的 `bottomPaddingPx`，不再让 `LazyColumn` 的实际 contentPadding 继续完全依赖 `composerTopInViewportPx` 这条晚一帧的异步回写链
- 共享 measure 宿主当前已补上关键约束：`SubcomposeLayout` 里测 composer slot 时只保留精确宽度，并显式放松 `minHeight = 0`；不再把父级整屏 `minHeight` 原样传给 composer。此前这会把 composer 量成接近整屏高，进而把欢迎语和历史列表的底部 reserve 一起撑爆，表现成“欢迎语不显示 / 有历史也整页空白”
- 首屏启动门槛当前已重新收敛：列表 reveal、欢迎语 reveal 和 `LaunchUiGate.chatReady` 只再依赖 hydration barrier，不再继续额外等待 `messageViewportMeasured`；`startupLayoutReady` 继续只服务 jump button、部分启动辅助几何与 composer 真值相关逻辑，不再把 `composerMeasured` 直接当成首屏显示硬门槛
- 首屏启动当前还会在 `uiRuntimeResetKey` 进入时主动清空 saveable 的 streaming runtime（`isStreaming / streamingMessageId / streamingMessageContent / streamingRevealBuffer` 等）；`hasStreamingItem` 也已收紧成“当前 streaming 状态存在且 `messages` 中确实存在对应消息”。这样冷启动或系统杀进程恢复时，不会再因为残留假 streaming 状态把欢迎语关掉、却只 reveal 出空列表白页
- 首屏显示与首次贴底当前已经彻底解耦：只要 hydration barrier 通过且 `messages` 非空，历史列表就直接 reveal，不再等待 `initialBottomSnapDone`，也不再额外等待 `messageViewportMeasured`；首次贴底保留为独立补一发 `scrollToBottom(false)` 的辅助动作，并继续单独等待 viewport 已测量
- 首次打开且本地已有历史消息时，聊天列表当前已不再从 `index = 0` 起步；`LazyListState` 会先以最后一条历史消息作为初始可见项，避免 reveal 放行后先把顶部旧历史露出来，然后继续交给现有首次贴底 effect 做一次精确 `scrollToBottom(false)` 校正
- 聊天列表当前不再用 `rememberSaveable(..., saver = LazyListState.Saver)` 恢复上次会话的 `LazyListState`；启动时如果先恢复到某条长 assistant 文本的中段，再由首次贴底 effect 改回当前位置，会表现成“前几秒文本重影 / 一直在闪”。现在冷启动与重进聊天页都统一从“最后一条历史消息起步，而不是恢复旧停留位置”进入，再交给首次贴底主链收口
- 首屏与普通静态历史当前不再沿用 streaming 工作线做“是否贴到底部”的判定；`currentUnifiedBottomTargetPx()` 在非 streaming 场景下已恢复旧的静态底线口径：优先以 `composerTopInViewportPx - BOTTOM_OVERLAY_CONTENT_CLEARANCE(4dp)` 为目标，没有实时 composer 几何时再回退到 `viewportHeight - bottomBarHeight - 4dp`。只有 streaming / waiting 期间才继续使用 `streamingWorklineBottomPx`
- 共享 measure 宿主当前又进一步收口了底部 reserve：`conversationBottomPaddingPx` 不再在 settled 完成态和首屏历史态无条件叠加 `STREAM_VISIBLE_BOTTOM_GAP(64dp)`；这段 gap 现在只在 `isStreaming || hasStreamingItem` 时进入 `contentPadding.bottom`，避免最后一条消息始终被一段空白 padding 顶在工作线附近、贴不到 composer 上方的真实列表底部
- `composerTopInViewportPx`、`messageViewportTopPx`、`inputFieldBoundsInWindow`、overlay snapshot 这组旧几何链当前继续保留，但职责已降级为 selection / overlay / bounds / workline 辅助口径，不再单独决定列表底部保留高度
- streaming 正常结束与本地 fake streaming 的后台同步完结，当前统一走“两阶段 finalize”收口：第一阶段先把最终内容落进 completed 消息并保留 streaming 几何口径，同时清掉该消息旧 streaming bounds；第二阶段等同一条消息的 completed fresh bounds 真正上报后，再原子切 `isStreaming / streamingMessageId / scrollRuntime`，并只在仍离底时按需补一次到底归位。完成态归位当前已明确复用 `scrollToBottom(false)` 静态底线主链，不再使用 `requestScrollToItem(lastIndex)` 这种把最后一条消息顶到视口顶部的 top-anchor
- 发送链当前重新收回到“正向列表 + 单次起步 offset”口径：`commitSendMessage()` 会先完成输入框收口、`upsertUserMessage`、assistant placeholder、`prepareScrollRuntimeForStreamingStart(...)`，再按 assistant placeholder 的真实位置请求 `requestScrollToItem(index, offset)`；网络/SSE 仅保留在后续协程
- `sendUiSettling` 当前已重新收紧成“只覆盖发送起步同步窗口”的短锁：输入框收口、消息原地增改、首发 `requestScrollToItem(index, offset)` 一完成就立即释放，不再把长文本 composer 的多行高度锁到整段 fake streaming / SSE 结束，专门收口“发送长文本后输入框有时不回缩”的时序竞态
- 发送起步窗口当前重新启用 `sendStartViewportHeightPx / sendStartWorklineBottomPx / pendingStartAnchorScrollOffsetPx` 这组前馈量，但只服务正向列表的单次起步定位，不再恢复成旧的多拍补偿链
- `ChatStreamingRenderer.kt` 当前已彻底移除 `rememberRendererLockedStreamingRenderedLinesImpl()` / `buildLockedStreamingActivePreview()` 这层 fresh line 锁预览，stable / active 行都直接用原始 `StreamingRenderedLines` 渲染；不再允许 activeLine 在某一拍被锁成预览串或空串，专门收口 streaming 过程中偶发“往下掉一下再弹回”的 1 帧高度塌陷
- 会诊协作口径当前已收紧：后续针对 UI 抖动、滚动链、渲染时序这类问题，默认先由 Codex 本地锁定到具体代码点，再把文件路径、函数名、关键状态、已排除项和限制条件一起打包给 Gemini / Claude，避免外部方案继续停留在抽象猜测层
- 当前外部会诊现实约束已明确：Gemini / Claude 等外部模型默认看不到本地仓库和文件链接，只能依赖用户通过聊天软件转发的代码片段、日志、截图；因此会诊稿必须自包含，关键代码不能只报文件名不贴内容
- `sendStartBottomPaddingLockActive` 当前重新参与正向发送起步窗口，但只服务 `sendStartViewportHeightPx` 锁定和起步 offset 计算，不再充当长期冻结列表 reserve 的几何锁
- 首次进入聊天页当前直接 `scrollToBottom(false)` 贴到底部；从后台切回时不默认自动贴底
- 回到底部按钮当前继续只走 `scrollToBottom(false)` 这条主链；但在正向列表下，这条主链当前已改成“最后一条已可见时直接 `alignChatListBottom()` 精修，只有完全不在可见区时才先 `scrollToItem(lastIndex)` 再补对齐”，专门避免首次贴底 / finalize 归位时先 top-anchor 到消息开头
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

- 当前 Android 聊天 UI 按最新真机口径重新收敛为 3 条关注点：本轮刚落地的“首次进入聊天页有历史时直接贴到 composer 上方真实底部”、以及“生成完成后不要停在工作线、而要下沉到真实底部”都需要真机回归确认；另外发送瞬间整块消息区仍会轻微上下抖一下
- 上述已收口问题的“现象 / 根因 / 当前修法 / 禁止回退”已统一固化进根 `AGENTS.md` 的 `7.5 已修复问题的成因与禁改清单`；后续新窗口如果又想改聊天滚动链，必须先对照这份清单，避免把旧问题重新带回
- 焦点 1：首次进入有历史时直接贴底
  - 当前主要代码点：`ChatScreen.kt` 的 `chatListState` 初始位置与首次贴底 effect
  - 当前代码已不再从 `LazyListState(0, 0)` 起步；如果本地已有历史，会先用最后一条历史消息作为初始可见项，再等待 `messageViewportMeasured` 后补一发 `scrollToBottom(false)` 做精确贴底
  - 后续限制：不要再把首屏 reveal / 欢迎语重新绑回 `messageViewportMeasured`、`composerMeasured` 或 `initialBottomSnapDone`
- 焦点 2：生成完成后不要跳到长文本开头
  - 当前主要代码点：`ChatScreen.kt` 的 `finalizeStreamingStop()` 收口后补归位逻辑
  - 本轮最新尝试：完成态若仍离底，不再用 `requestScrollToItem(lastIndex)` 把最后一条消息顶到视口顶部，而是改为复用现有 `scrollToBottom(false)` 静态底线主链
  - 后续限制：不要把两阶段 finalize 改回同拍硬切、短超时硬切或 `pendingFinalBottomSnap`
- 焦点 3：发送瞬间整块消息区轻微上下抖
  - 当前主要代码点：`ChatScreen.kt` 的 `commitSendMessage()` 与共享 measure 宿主
  - 当前真实顺序仍保持产品要求：先即时 `prepareComposerCollapse(...)`、`input.value = TextFieldValue("")`、`clearFocus/hide keyboard`，再 `upsertUserMessage(...)`、`upsertAssistantMessagePlaceholder(...)`、`requestScrollToItem(index, offset)`
  - 当前最新尝试：列表已切回正向口径，同时保留 composer/list 共享测量宿主、两阶段 finalize、streaming/settled 同构等近几轮修复；发送起步、AutoFollow、静态贴底是否全部重新跑顺，仍需用户真机回归
- 已明确排除、不要再回滚的方向：
  - 旧 `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout` 主链
  - `alignChatListBottom()` 的 8 帧 `scrollBy` 补偿
  - `pendingFinalBottomSnap`
  - 发送链里的 `withFrameNanos` 延迟回底
  - 发送链里的 `withTimeoutOrNull` 延后收口实验
  - 发送链里的 `Snapshot.withMutableSnapshot { ... }` 单次 snapshot 提交实验
  - 普通 idle 聚焦输入框时带着历史区一起联动
- 本轮新增回归修复点：
- `finishStreaming()` / `completeStreamingImmediatelyFromBackground()` 当前不再在完成同一拍直接切 `isStreaming = false`。新的主链是：先写 completed 消息，再用 `pendingStreamingFinalizeMessageId` 把 active assistant 临时切到 settled renderMode，等同一条消息的 fresh completed bounds 真正到位后，再一次性切掉 streaming 状态；第二阶段已不再使用 `200ms` 这类短超时硬切，并且后台期间会暂停等待、回到前台后再继续等 settled bounds，专门收口“生成结束后偶发上跳、底部留白”和“切后台再回来底部留白”的时序竞态
- 推荐回归入口：`docs/runbooks/chat-ui-regression.md`

## 当前阶段判断

- 代码主干已存在，当前更需要的是把“规则、状态、方案取舍、运维入口”固化进仓库，降低换窗口和长期接手成本
- 目前运维 runbook 还是骨架版，真实部署、回滚、日志、只读查库命令还需要后续在实际运维过程中逐步补实

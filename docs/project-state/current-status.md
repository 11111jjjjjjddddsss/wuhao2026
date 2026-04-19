# 当前状态

最后更新：2026-04-19

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
- 首屏显示与首次贴底当前已经彻底解耦：只要 hydration barrier 通过且 `messages` 非空，历史列表就直接 reveal，不再等待 `initialBottomSnapDone`，也不再额外等待 `messageViewportMeasured`；首次贴底保留为独立 effect，但当前会等 `startupLayoutReady` 后再启动，并且只有已经命中底部容差才把 `initialBottomSnapDone` 记完成，避免首屏第一次 `scrollToBottom(false)` 发生在目标线或内容 bounds 仍未稳定的窗口里时“一次滚完就提前收工”
- 为了把首屏历史贴底的最后几像素压准到真实工作线，`ChatScreen.kt` 当前允许“首次进入且 `initialBottomSnapDone` 还没命中”的短窗口临时参考实时 composer 几何；一旦首屏已经命中底部容差，就立即退回现有静态工作线口径，不把 idle / 历史浏览重新带回“输入框一动、消息区跟着动”
- 首次打开且本地已有历史消息时，聊天列表当前已不再从 `index = 0` 起步；`LazyListState` 会先以最后一条历史消息作为初始可见项，避免 reveal 放行后先把顶部旧历史露出来，然后继续交给现有首次贴底 effect 做一次精确 `scrollToBottom(false)` 校正
- 聊天列表当前不再用 `rememberSaveable(..., saver = LazyListState.Saver)` 恢复上次会话的 `LazyListState`；启动时如果先恢复到某条长 assistant 文本的中段，再由首次贴底 effect 改回当前位置，会表现成“前几秒文本重影 / 一直在闪”。现在冷启动与重进聊天页都统一从“最后一条历史消息起步，而不是恢复旧停留位置”进入，再交给首次贴底主链收口
- 首屏与普通静态历史当前继续沿用工作线做“是否贴到底部”的判定；`currentUnifiedBottomTargetPx()` 在 streaming、settled 完成态和首屏历史态下都重新收口到 `streamingWorklineBottomPx` 这一条工作线口径，确保正文收尾继续落在工作线，而不是压到 composer 顶边
- 共享 measure 宿主当前继续把 `STREAM_VISIBLE_BOTTOM_GAP(64dp)` 计入 `conversationBottomPaddingPx`；这段 gap 不是多余空白，而是工作线以下要露出来的 breathing gap，用来给尾部提示词 / 免责声明预留可见空间，不能在 settled 完成态和首屏历史态被收掉
- `composerTopInViewportPx`、`messageViewportTopPx`、`inputFieldBoundsInWindow`、overlay snapshot 这组旧几何链当前继续保留，但职责已降级为 selection / overlay / bounds / workline 辅助口径，不再单独决定列表底部保留高度
- streaming 正常结束与本地 fake streaming 的后台同步完结，当前统一走“两阶段 finalize”收口：第一阶段先把最终内容落进 completed 消息并保留 streaming 几何口径，同时清掉该消息旧 streaming bounds；第二阶段等同一条消息的 completed fresh bounds 真正上报后，再原子切 `isStreaming / streamingMessageId / scrollRuntime`，并只在仍离底时按需补一次到底归位。完成态归位当前已明确复用 `scrollToBottom(false)` 静态底线主链，不再使用 `requestScrollToItem(lastIndex)` 这种把最后一条消息顶到视口顶部的 top-anchor
- 本地聊天窗口持久化当前又补了一刀：两阶段 finalize 的第一阶段只要已经把 completed assistant 写回 `messages`，`persistableMessagesSnapshot()` 就不再把这条 assistant 当成 transient streaming item 过滤掉。这样 fake streaming 正常结束、切后台同步完结、以及“生成完刚落地就切出去”这几类场景，本地聊天窗口都会带上 assistant 完成态，不再只剩用户消息
- 本地聊天窗口快照当前继续扩成“消息 + 失败态 metadata”一起落盘：失败 user 的 `未发送/重发`、失败 assistant 的 `回复未完成/重试` 现在都会和正文一起保存在同一个本地 snapshot 里，重进后仍能恢复 footer；同时继续兼容旧 `List<ChatMessage>` 数组格式缓存
- 带后端模式下，远端 hydrate 当前也不再无脑覆盖本地失败尾巴：如果远端快照还没覆盖这些本地失败消息，首屏会把本地失败消息和对应 failed-state metadata 一起并回 hydrated snapshot，而不是再被远端历史擦成“只剩普通历史 / 只剩用户消息”；当前 footer 语义仍保持 `重发 / 重试`，不是“继续生成”
- assistant 失败态当前又补齐到 0 token 场景：如果 assistant 在首 token 前就中断，`ChatScreen.kt` 也会保留对应的 assistant placeholder item，并写入 failed assistant state；本地 snapshot 不再把这类“空内容但 failed assistant”当成普通空壳过滤掉。这样重进后依然能恢复 `回复未完成 / 重试`，不再只剩顶部 hint 或只剩用户消息
- 发送链当前重新收回到“正向列表 + 单次起步 offset”口径：`commitSendMessage()` 会先完成输入框收口、`upsertUserMessage`、assistant placeholder、`prepareScrollRuntimeForStreamingStart(...)`，再按 assistant placeholder 的真实位置请求 `requestScrollToItem(index, offset)`；网络/SSE 仅保留在后续协程
- `sendUiSettling` 当前已重新收紧成“只覆盖发送起步同步窗口”的短锁：输入框收口、消息原地增改、首发 `requestScrollToItem(index, offset)` 一完成就立即释放，不再把长文本 composer 的多行高度锁到整段 fake streaming / SSE 结束，专门收口“发送长文本后输入框有时不回缩”的时序竞态
- 发送起步窗口当前重新启用 `sendStartViewportHeightPx / sendStartWorklineBottomPx / pendingStartAnchorScrollOffsetPx` 这组前馈量，但只服务正向列表的单次起步定位，不再恢复成旧的多拍补偿链
- 发送起步窗口当前又新增了一层更窄的保护：`commitSendMessage()` 在发出 `requestScrollToItem(index, offset)` 前，会先给 `LazyColumn` 选出一个临时 `bottomPaddingPx` 锁值。普通发送且会收口 composer 时，这个锁值当前优先使用最近一次观察到的稳定收口 reserve（`observedCollapsedBottomReservePx`）再叠加 `STREAM_VISIBLE_BOTTOM_GAP`；如果观察值暂时还没采到，才按现有 `stableComposerBottomBarHeightPx / bottomBarHeightPx` 链兜底。只有 `collapseComposer = false` 这类不收口发送才退回当前 `conversationBottomPaddingPx` 快照。`sendStartAnchorActive` 期间，`ChatRecyclerViewHost` 只临时把这份锁值作为 `LazyColumn` 的 `bottomPaddingPx` 消费值，保护窗口退出后立即退回实时值。它只锁列表 contentPadding 消费点，不锁 workline、overflow 判定或全局 reserve，专门验证 `conversationBottomPaddingPx` 连续变化是否就是发送微抖的直接触发器
- 发送起步 offset 当前也已和这份发送期 `bottomPaddingPx` 锁对齐：`commitSendMessage()` 选出 `conversationBottomPaddingLockPx` 后，会直接用这份锁值反推本次 `requestScrollToItem(index, offset)` 的最终工作线位置，不再让列表实际吃进去的是“锁定 padding”，而发送起步 offset 仍继续沿用另一条预先派生的工作线快照。`pendingStartAnchorScrollOffsetPx` 现在只保留为锁值拿不到时的兜底
- 最新一刀继续把发送起步共同基准收窄：普通发送且会收口 composer 时，`conversationBottomPaddingLockPx` 已不再吃当前多行输入框量出来的 `stableComposerBottomBarHeightPx`，也不再吃拍脑袋的 collapsed 常量；运行时会在“输入为空 + 无 focus + IME 已收起 + composer 非 settling”的稳定收口窗口记录真实底部 reserve（`messageViewportHeightPx - composerTopInViewportPx`），发送起步优先直接复用这份观察值，专门压“长文本发送时工作线被算高、小球更靠上”的现象；`stableComposerBottomBarHeightPx` 仍保留给普通运行时几何和 streaming 主链使用，不和发送期 lock 混用
- 为了减少冷启动第一次发送时“观察值还没采到”导致的小球略高/略低，`observedCollapsedBottomReservePx` 当前会先从共享 measure 宿主已拿到的 `latestConversationBottomPaddingPx` 预热（减去 `STREAM_VISIBLE_BOTTOM_GAP`）；只要页面处于“输入为空 + 无 focus + IME 已收起 + composer 非 settling + 未处于 sendStart lock”的稳定收口窗口，就允许把这份真实 reserve 记下来。`composerTopInViewportPx` 那条旧观察链继续保留，用来后续校准
- 静态态贴底当前又收紧了一刀：首屏历史贴底、完成态归位和静态回到底部按钮，已经不再和 streaming 共用同一个“16dp 算到底”口径，而是单独走更紧的静态容差；同时 `ChatRecyclerViewHost` 已移除列表尾部那颗额外的 1dp footer spacer，静态态真实底边只再由最后一条消息和 `conversationBottomPaddingPx` 决定。streaming 工作线命中带保持原样，专门收“开机进入 / 生成完成后还能再扒出一丁点空白”的剩余体感
- `ChatStreamingRenderer.kt` 当前已彻底移除 `rememberRendererLockedStreamingRenderedLinesImpl()` / `buildLockedStreamingActivePreview()` 这层 fresh line 锁预览，stable / active 行都直接用原始 `StreamingRenderedLines` 渲染；不再允许 activeLine 在某一拍被锁成预览串或空串，专门收口 streaming 过程中偶发“往下掉一下再弹回”的 1 帧高度塌陷
- 会诊协作口径当前已收紧：后续针对 UI 抖动、滚动链、渲染时序这类问题，默认先由 Codex 本地锁定到具体代码点，再把文件路径、函数名、关键状态、已排除项和限制条件一起整理成发给 Claude 的短稿，避免外部方案继续停留在抽象猜测层
- 当前外部会诊现实约束已明确：Claude 等外部模型默认看不到本地仓库和文件链接，只能依赖用户通过聊天软件转发的代码片段、日志、截图；因此会诊稿必须自包含，关键代码不能只报文件名不贴内容
- `sendStartBottomPaddingLockActive` 当前重新参与正向发送起步窗口，但只服务 `sendStartViewportHeightPx` 锁定和起步 offset 计算，不再充当长期冻结列表 reserve 的几何锁
- 首次进入聊天页当前直接 `scrollToBottom(false)` 贴到底部；从后台切回时不默认自动贴底
- 回到底部主链当前继续只走 `scrollToBottom(false)`；其中非动画路径已进一步收紧成“只有最后一条底边已经进入可视区，才允许只做 `alignChatListBottom()` 精修”。如果最后一条只是顶部露头、底边仍远在可视区外，正向列表会先做一次大位移滚到底，再由 `alignChatListBottom()` 把最后几像素压回工作线，专门收口首屏进入仍差一大段文字的问题
- 按最新真机反馈，首次进入聊天页且本地有历史时的贴底已确认收口；当前工作线以下的 breathing gap 继续保持可见，不需要再为“首屏不贴底”单独开新链
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

- 当前 Android 聊天 UI 按最新真机口径，主滚动 / 发送抖动 / 首屏贴底 / finalize 归位问题都已收口。当前产品口径继续保持：发送瞬间的小球锚点稳定在工作线，而 streaming / settled / 首屏历史态也继续围绕工作线收口
- 历史上“把小球抬到中部以上会带出底部空白”这件事，当前已经确认不是一个单独成立的规律。旧问题的本质是：当时发送起步抖动、`conversationBottomPaddingPx` 连续变化、以及 finalize 几何链都还没收口，小球上抬只是把抖动视觉稀释了，没修掉底层几何；真正的底部空白根因在 finalize / 底部几何切换，而不在小球首发位置本身。现在这几条底层链路虽然已经分开收口，但按最新产品回归，失败态和短文本收口在“小球上抬”时仍会变差，所以当前仍固定回工作线
- 上述已收口问题的“现象 / 根因 / 当前修法 / 禁止回退”已统一固化进根 `AGENTS.md` 的 `7.5 已修复问题的成因与禁改清单`；后续新窗口如果又想改聊天滚动链，必须先对照这份清单，避免把旧问题重新带回
- 焦点 1：发送起步锚点稳定贴工作线后的回归观察
  - 当前主要代码点：`ChatScreen.kt` 的 `commitSendMessage()`、composer/list 共享 measure 宿主、`pendingStartAnchorScrollOffsetPx` 与发送起步那一拍的 `requestScrollToItem(index, offset)`
  - 当前真实顺序仍保持产品要求：先即时 `prepareComposerCollapse(...)`、`input.value = TextFieldValue("")`、`clearFocus/hide keyboard`，再 `upsertUserMessage(...)`、`upsertAssistantMessagePlaceholder(...)`、`requestScrollToItem(index, offset)`
  - 最新真机逐帧 trace 已确认：发送微抖主因不在 release gate，也不在 follow delta，而在发送起步窗口里 `conversationBottomPaddingPx` 连续变化带来的原生重排；当前“列表 `bottomPaddingPx` 锁”已经把这条问题压住
  - 当前最值得继续盯的真实代码点：`sendStartWorklineBottomPx`、共享 measure 宿主里的 `conversationBottomPaddingPx`，以及发送起步那一拍的 `requestScrollToItem(index, offset)` 是否继续和 streaming / finalize 主链互不打架
- 当前最新验证刀法：发送事务发出 `requestScrollToItem(index, offset)` 前，普通发送会先把 `LazyColumn` 的 `bottomPaddingPx` 锁到“最近一次观察到的稳定收口 reserve + `STREAM_VISIBLE_BOTTOM_GAP`”这份终态值；如果观察值还没采到，才退回现有 `stableComposerBottomBarHeightPx / bottomBarHeightPx` 兜底链；重试/不收口发送仍退回当前 `conversationBottomPaddingPx` 快照。同时本次发送真正发出的 `requestScrollToItem(index, offset)` 也会直接复用这份锁值反推工作线，不再让“列表锁定 padding”和“发送起步 offset”各吃一套基准。发送瞬间的小球继续贴工作线，不再单独抬到中部偏上。目的不是重新把两类问题绑死，而是在已经证明“底部空白”和“小球位置”可拆之后，继续维持当前更稳的产品口径
  - 当前排查限制：不要再恢复 `withFrameNanos` / `withTimeoutOrNull` / `Snapshot.withMutableSnapshot` 这类发送期补丁；也不要把旧 `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout`、`pendingFinalBottomSnap`、fresh-line lock 预览层或历史区输入框联动链带回来；同时不要再把主因继续压回 `ChatScrollCoordinator.kt` 的 release gate / follow delta
  - 当前规则结论：理论上“小球第一次出现在哪”和“底部几何真相”已经可以拆开看；但当前产品口径继续固定在工作线。后续若再想重开中部上抬，仍然只能单改发送首发链，不能去碰 `streamingWorklineBottomPx`、`currentUnifiedBottomTargetPx()`、两阶段 finalize
- 回归观察项：
  - 首次进入有历史时继续直接贴底，并保持工作线以下 breathing gap 可见
  - 生成完成后不要跳到长 assistant 文本开头
  - 发送长文本后输入框要稳定回缩，不要再次出现“有时回缩、有时不回缩”的竞态
- 推荐回归入口：`docs/runbooks/chat-ui-regression.md`

## 当前阶段判断

- 代码主干已存在，当前更需要的是把“规则、状态、方案取舍、运维入口”固化进仓库，降低换窗口和长期接手成本
- 目前运维 runbook 还是骨架版，真实部署、回滚、日志、只读查库命令还需要后续在实际运维过程中逐步补实
- 正式云资源当前还没采购；本轮已新增 `docs/runbooks/infra-readiness.md` 作为采购前检查单，先把“上线前必须拍板什么”固化进仓库，等第一套真实环境落地后再把 deploy / rollback / logs / db-readonly 补成可执行入口

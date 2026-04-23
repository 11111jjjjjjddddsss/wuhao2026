# 当前状态

最后更新：2026-04-23

## 项目概况

- 项目：农技千问
- 目标：面向农作物问题提供农业技术参考建议，不提供绝对诊断
- 当前仓库两条主线：Android 客户端 `app`，Go 后端 `server-go`

## 当前代码真相

- Android 端当前使用 Jetpack Compose 聊天界面，不再依赖 WebView 模板页面
- 底部 composer 当前已先按“只换壳子、不动主链”的口径改成更接近悬浮卡片的输入壳：宿主白底去掉、输入框改为圆角浮层卡片、加号收进左下、发送键收进右下；当前只动 `ChatComposerPanel.kt` 和少量尺寸/颜色参数，不改底部活跃区主链、历史列表回底、工作线算法或 finalize 主链
- 聊天页当前又补了一轮纯样式调参：整页底色已从纯白统一收成中性浅灰 `#F6F7F8`，输入框壳子继续保持纯白；`ComposerInputShell` 已改成偏下沉的双层 `dropShadow()`，重点加强“下边比上边更重”的悬浮感；输入框常态高度当前为 `92/96dp`（窄屏/常规），长文本最高高度当前为 `232/248dp`，输入区 `maxLines` 已放到 8 行。这一轮仍属于纯外观与长文本上限调参，不改工作线、发送起步 offset、streaming / finalize 主链
- 输入框占位文案“描述种植问题”当前不再被整段 streaming 状态压住。发送后只要输入框已清空且 composer / IME 收口几何稳定，占位会提前回到输入框内；生成中发送按钮仍按 `isStreaming || sendUiSettling` 禁用，避免并发发送。这条只改 composer 视觉显示门，不参与工作线、streaming wrap guard、AutoFollow 或 finalize 主链
- 输入框长文本编辑当前又补了一刀手势修正：聊天页最外层用于“点空白处收键盘”的 `pointerInput`，现在只会在“外部轻点”时才执行 `clearFocus + hide keyboard`；输入框内部长文本上下拖动、以及从输入框内部起手的拖动，不再被误判成外部失焦手势。这样长文本编辑时上下滑查看内容，不应再把输入法突然收掉并打乱中间插字位置；同时“列表滑动时收键盘”当前只在用户手指正在拖动列表（`chatListUserDragging`）时触发，不再把 fling 惯性滚动（`recyclerScrollInProgress`）当成收键盘理由，避免列表惯性未停时点输入框又被立刻关掉
- 聊天列表当前唯一底座已切回正向 `LazyColumn(reverseLayout = false)`；`ChatRecyclerViewHost.kt` 只是历史文件名残留，运行时已无 active `RecyclerView` 链
- `ChatRecyclerViewHost.kt` 当前额外启用了 `LazyColumn(verticalArrangement = Arrangement.Bottom)`；在正向列表下，当历史内容总高度不足一屏时，列表会优先把短内容压在底部工作区，而不是默认趴到顶部遮罩下面
- 聊天列表上下滑动丝滑度当前已先做低风险减负：`ChatRecyclerViewHost.kt` 直接接收消息对象和稳定 key，不再让每个可见 item 通过 id 反查 `messages`；`LazyColumn` 的 `PaddingValues` 也改为按 padding/density 缓存。`ChatScreen.kt` 同步给 chat metrics、message content bounds、root/viewport/composer bounds 等高频回写点加了相同值去重，并进一步把 message content bounds 跟踪范围收窄到最后一条消息 / active streaming / pending finalize，把仅服务 jump button 的首项滚动 offset 按 24px 分桶，减少滚动期无意义 state 写入。动态 streaming 侧当前只额外给 `onAdvance` wrap guard 的 active block pre-measure 加了 4 条小缓存，复用同内容同宽度的测量结果；这刀不改工作线、发送起步、wrap guard 语义、AutoFollow 或 finalize 主链
- 聊天列表丝滑度后续又补了 3 个当前保留的低风险优化：`ChatRecyclerViewHost.kt` 的 `LazyColumn` 已按消息 `role` 设置 `contentType`，只区分 `USER / ASSISTANT`，不拆 assistant 的 waiting / streaming / settled，避免 finalize remount；`ChatStreamingRenderer.kt` 的 settled assistant markdown 已把 committed blocks 的宽度和 `TextMeasurer` 收敛到 message 级，committed bullet / numbered 不再各自套 `BoxWithConstraints`；`ChatScreen.kt` 的 `messageSelectionBoundsById` 不再在普通滑动时被每条可见消息每帧写入 Compose state map，普通滑动只更新非 state bounds cache，只有当前文字选择或 pending toolbar 的消息才写 state 以驱动 toolbar 跟随。这 3 刀都不改工作线、AutoFollow、SelectionContainer 结构、streaming wrap guard 或 finalize 主链
- Android 客户端当前已新增 `:baselineprofile` 模块并在 `app` 模块接入 `androidx.baselineprofile`。它用于把“冷启动进入聊天页、长文区域上下滑、输入框聚焦 / 输入 / 收起”等关键 UI 路径生成 Baseline Profile / Macrobenchmark，不点发送、不触发后端 / 模型调用。后续如果聊天页、输入框、Selection、Markdown 或 LazyColumn 主路径明显变化，或正式发版前需要刷新冷启动预编译覆盖面，应按 [docs/runbooks/android-baseline-profile.md](D:/wuhao/docs/runbooks/android-baseline-profile.md) 重新生成 / 验证
- 运行时消息状态与显示顺序当前保持一致，不再通过 `asReversed()` 翻转；旧消息在上，新消息在下
- 聊天页生成态当前已从“assistant-only overlay + 列表 placeholder + overlay 追滚桥”升级到**底部统一活跃区宿主**：`ChatScreen.kt` 新增 `BottomActiveZoneSlice / resolveBottomActiveZoneSlice(...)`，在 `StreamingLocation.OVERLAY` 下把“当前轮用户消息 + 当前 assistant（waiting / streaming / settled）+ 前置 1 条历史尾巴”从 `messages` 中切出来，由 `renderBottomActiveZone()` 在 `SubcomposeLayout` 里统一渲染；`LazyColumn` 此时只再渲染更早的 `historyMessages`。当前已经删除旧 placeholder、高度跟随与 `SideEffect + dispatchRawDelta(...)` 的 overlay 追滚桥，不再让 active assistant body 同时在 Overlay 和列表里双持
- `ChatScreen.kt` 刚又补了一轮只针对底部统一活跃区回归的最小修正：1）底部活跃区根宿主现在会在竖向拖动起手时直接切 `scrollMode = UserBrowsing`、把 `streamingLocation` 交回 `LAZY_COLUMN`，并把本次 drag delta 直接打给 `chatListState.dispatchRawDelta(...)`，解决“生成中/静态底部文本在活跃区里完全划不动、像抢手”的问题；2）Overlay 可见时，`scrollToBottom(false)` 不再把 history list 的 `currentBottomAlignDeltaPx` 写死成 0，而是改为按 history list 自己的 viewportEndOffset 做精修，继续收掉发送后 / finalize 后的大块底部空白与“不贴底”体感；3）发送起步重新补回了短窗口 `bottomPaddingPx` 锁，锁值优先吃 `observedCollapsedBottomReservePx + STREAM_VISIBLE_BOTTOM_GAP`，直到 `requestSendStartBottomSnap()` 真正执行完 `scrollToBottom(false)` 后才释放，专门压这轮结构刀把“小球和历史文本先抖一下”重新带回来的回归
- `ChatScreen.kt` 当前又把底部统一活跃区的量算口径往前收了一刀：`LazyColumn` 在 `SubcomposeLayout` 里不再只量到 `conversationBodyHeightPx`，而是重新量满整个消息区高度；Overlay 可见时，列表底部 padding 现在统一吃“composer reserve + breathing gap + active zone height”，不再把 composer reserve 和 active zone 分别落在“裁短列表高度”和“底部 padding”两处双重避让。同时，history list 的底边目标与 send-start 锁也改成沿用这同一套 padding 口径，专门压“输入框上方像多出一整节白色高度、静态态底部文本下方空白过大、发送瞬间仍然抖一下”这组仍在排查中的回归。代码已编译通过，但是否真正收掉这组回归还没有新的真机结论
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 当前又把底部统一活跃区的两条“还没收住的抖动链”重新接回主线：1）发送事务里重新把 `sendStartAnchorActive` 置回 `true`，让 `BindChatListScrollEffects(...)` 继续承担发送起步保护窗口；其 release 条件也已收紧成“命中工作线、composer 已稳定、并且列表已经停止滚动”后再连续一拍放行，避免 history list 发送回底尚未停稳时就提前让权；2）active zone 手动浏览后不再是“永久不自动恢复 Overlay”，而是改成：拖动起手先把 `bottomActiveZoneOverlayRestoreArmed` 置 `false` 并交回 `LAZY_COLUMN`，只有等列表真正停下、滚动模式回到 `AutoFollow`、且仍命中工作线后，才先 re-arm，再下一拍恢复 `OVERLAY`。这刀的目标是同时压“发送瞬间仍抖一下”和“用户上下拖动时正文乱窜 / 与用户消息重叠”，代码已编译通过，但还没有新的真机结论
- 小球所在的 assistant waiting 宿主当前依然是发送起步锚点；在正向列表下它不再依赖“反向天然贴底”，而是依赖“当前轮切入底部活跃区 + 历史列表回底”的新发送起步链
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
- streaming 正常结束与本地 fake streaming 的后台同步完结，当前已经分成两条 finalize 口径：`StreamingLocation.OVERLAY` 下，当前轮 assistant 会在同一个底部活跃区宿主里直接从 streaming 切 settled，再执行 `finalizeStreamingStop(...)`，不再走列表里的 `alpha(0f)` completed twin 或 overlay/list 双树交接；只有 `StreamingLocation.LAZY_COLUMN` 的用户浏览 fallback，才继续走旧的两阶段 finalize（`beginPendingStreamingFinalize(...) -> fresh bounds -> finalizeStreamingStop(...)`）
- 本地聊天窗口持久化当前又补了一刀：两阶段 finalize 的第一阶段只要已经把 completed assistant 写回 `messages`，`persistableMessagesSnapshot()` 就不再把这条 assistant 当成 transient streaming item 过滤掉。这样 fake streaming 正常结束、切后台同步完结、以及“生成完刚落地就切出去”这几类场景，本地聊天窗口都会带上 assistant 完成态，不再只剩用户消息
- 本地聊天窗口快照当前继续扩成“消息 + 失败态 metadata”一起落盘：失败 user 的 `未发送/重发`、失败 assistant 的 `回复未完成/重试` 现在都会和正文一起保存在同一个本地 snapshot 里，重进后仍能恢复 footer；同时继续兼容旧 `List<ChatMessage>` 数组格式缓存
- 带后端模式下，远端 hydrate 当前也不再无脑覆盖本地失败尾巴：如果远端快照还没覆盖这些本地失败消息，首屏会把本地失败消息和对应 failed-state metadata 一起并回 hydrated snapshot，而不是再被远端历史擦成“只剩普通历史 / 只剩用户消息”；当前 footer 语义仍保持 `重发 / 重试`，不是“继续生成”
- assistant 失败态当前又补齐到 0 token 场景：如果 assistant 在首 token 前就中断，`ChatScreen.kt` 也会保留对应的 assistant placeholder item，并写入 failed assistant state；本地 snapshot 不再把这类“空内容但 failed assistant”当成普通空壳过滤掉。这样重进后依然能恢复 `回复未完成 / 重试`，不再只剩顶部 hint 或只剩用户消息
- 发送链当前也已随底部活跃区重构继续收口：`commitSendMessage()` 仍然在同一发送事务里完成输入框收口、`upsertUserMessage(...)`、assistant placeholder、`prepareScrollRuntimeForStreamingStart(...)`，并在同一拍就切 `StreamingLocation.OVERLAY`，不再等首个 bottom snap 后才切层；发送起步真正做的，是让 `requestSendStartBottomSnap()` 只对 `historyMessages` 做一次 `scrollToBottom(false)`，而当前轮 user / assistant 从发送瞬间起就由底部活跃区贴底承接。这样避免了“先滚完整列表、下一帧再切 active zone”带来的发送瞬间抖动
- `sendUiSettling` 当前仍保持“只覆盖发送起步同步窗口”的短锁，但它保护的已不再是 `requestScrollToItem(index, offset)`，而是“输入框收口 + 当前轮切入底部活跃区 + 历史列表发送起步回底”这一小段同步窗口；不再把长文本 composer 的多行高度锁到整段 fake streaming / SSE 结束
- 发送起步窗口当前只保留 reserve 真值链，不再保留 list-side placeholder 行锚定链：运行时继续维护 `observedCollapsedBottomReservePx`、`latestConversationBottomPaddingPx` 和 `lockedConversationBottomPaddingPx`，用来给共享 measure 宿主、收口窗口和列表 reserve 兜底；但 `pendingStartAnchorScrollOffsetPx / sendStartWorklineBottomPx / requestScrollToItem(...)` 已退出主链，发送起步真正要做的只剩“切入底部活跃区 + 历史列表回底”
- 最新一刀继续把发送起步共同基准收窄：普通发送且会收口 composer 时，reserve 真值当前仍优先吃 `observedCollapsedBottomReservePx + STREAM_VISIBLE_BOTTOM_GAP`，但这份观察值的主来源已经重新收平到共享 measure 宿主。只要列表已渲染且页面处于“输入为空 + 无 focus + IME 已收起 + composer 非 settling + 未处于 sendStart lock”的稳定窗口，`renderChatList(...)` 会在首个有效 `bottomPaddingPx` 出现的同拍先种下 `latestConversationBottomPaddingPx - STREAM_VISIBLE_BOTTOM_GAP`，稳定态 `LaunchedEffect` 再继续校准；`stableComposerBottomBarHeightPx` 仍只保留给普通运行时几何和发送期最后兜底，不再和这份共享 measure 真值抢主导
- Overlay 下的工作线 / restore 几何口径当前又收了一刀：底部活跃区接管渲染后，`isNearStreamingWorkline()`、`isAtStreamingWorklineStrict()` 在 `StreamingLocation.OVERLAY` 下不再继续吃单条 assistant 的 `streamingContentBottomPx`，而是直接视为命中工作线；同时静态 `atBottom` / restore 判定在底部活跃区仍可见时，改为比较 history list 自己的 viewportEndOffset 与最后一条历史消息的 bottom。这样 finalize 收尾、静态回到底部按钮和 Overlay 内 restore scroll 不再因为 assistant bounds 偏高而误判。
- `composerTopInViewportPx` 那条旧观察链当前只剩启动 fallback 身份：只有当列表侧的 `latestConversationBottomPaddingPx` 仍未产出时，才允许用 `messageViewportHeightPx - composerTopInViewportPx` 去写 `observedCollapsedBottomReservePx`；一旦共享 measure 真值已经到位，旧链就不再继续回写同一个状态。同时，上一版“只要拿到首个有效 `latestConversationBottomPaddingPx` 就无条件做 cold-start 预热”的宽松写法已经删除，避免 clean-state 首发后把 focus/send 锁窗口里的 padding 误记成稳定 reserve
- 静态态贴底当前又收紧了一刀：首屏历史贴底、完成态归位和静态回到底部按钮，已经不再和 streaming 共用同一个“16dp 算到底”口径，而是单独走更紧的静态容差；同时 `ChatRecyclerViewHost` 已移除列表尾部那颗额外的 1dp footer spacer，`ChatScreen.kt` 当前把静态容差继续收到了 `0.dp`，静态态真实底边仍只由最后一条消息和 `conversationBottomPaddingPx` 决定。streaming 工作线命中带保持原样，专门收“开机进入 / 生成完成后还能再扒出一丁点空白”的剩余体感；如果真机上还剩最后一丝，再单独评估 Float 精度，不直接引入 scrollBy 探针
- `ChatStreamingRenderer.kt` 当前已彻底移除 `rememberRendererLockedStreamingRenderedLinesImpl()` / `buildLockedStreamingActivePreview()` 这层 fresh line 锁预览，stable / active 行都直接用原始 `StreamingRenderedLines` 渲染；不再允许 activeLine 在某一拍被锁成预览串或空串，专门收口 streaming 过程中偶发“往下掉一下再弹回”的 1 帧高度塌陷
- [ADR-0002](D:/wuhao/docs/adr/ADR-0002-streaming-overlay-for-active-assistant.md) 当前已经从“assistant-only overlay 第一刀”推进到“底部统一活跃区宿主”版本：`StreamingLocation.OVERLAY` 下，底部活跃区不再只是单独承接 assistant body，而是统一承接当前轮 user / assistant 与 1 条历史尾巴；`SubcomposeLayout` 先测 composer、再测底部活跃区，再把 `LazyColumn` 只量到“除去 composer 和底部活跃区之后的剩余空间”，不再让列表和底部正文双层重叠。旧 overlay placeholder、高度追滚、`SideEffect + dispatchRawDelta(...)`、pending finalize twin tree 都已退出当前主链，只保留 `LAZY_COLUMN` fallback 下的 wrap guard / strict follow / 两阶段 finalize 兜底
- 为避免旧 reveal 方案继续误导，`ChatStreamingRenderer.kt` / `ChatScreen.kt` 已把不再接线的 `StreamingRevealMode.Conservative`、`strictLineReveal`、`lineRevealLocked`、`streamingLineAdvanceTick` 这条空转 plumbing 一并移除；当前 streaming 新行问题只再围绕 `onAdvance` 提交口、`streamingWrapGuardTargetLineCount` 和 active block pre-measure 这条链排查
- streaming follow 当前又和发送起步 release gate 正式拆开了容差：`ChatScreen.kt` 继续保留原 `isNearStreamingWorkline()` 给发送起步保护释放等“宽容差”消费者使用，但新增了只给 streaming follow 抑制链使用的 `isAtStreamingWorklineStrict()`，把“工作线下方还算到位”的上容差从一整行高度收紧到 `BOTTOM_POSITION_TOLERANCE(16dp)`。`ChatScrollCoordinator.kt` 的 follow suppression 现在只认这个严格版 gate，专门压“工作线下面先露出一整行才开始跟随”的现象；sendStart release gate、jump button 等原宽容差消费者保持不变
- 会诊协作口径当前已收紧：后续针对 UI 抖动、滚动链、渲染时序这类问题，默认先由 Codex 本地锁定到具体代码点，再把文件路径、函数名、关键状态、已排除项和限制条件一起整理成发给 Claude 的短稿，避免外部方案继续停留在抽象猜测层
- 当前外部会诊现实约束已明确：Claude 等外部模型默认看不到本地仓库和文件链接，只能依赖用户通过聊天软件转发的代码片段、日志、截图；因此会诊稿必须自包含，关键代码不能只报文件名不贴内容
- `sendStartBottomPaddingLockActive` 当前仍参与发送起步窗口，但只服务发送当拍的 viewport / reserve 稳定，不再去计算 list-side 起步 offset，也不再充当长期冻结列表 reserve 的几何锁
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

- 当前 Android 聊天 UI 里，旧的发送抖动、首屏贴底和大块 finalize 归位问题已基本收口；但这轮底部统一活跃区结构刀仍待真机验证。当前产品口径继续保持：发送瞬间的小球锚点稳定在工作线，而 streaming / settled / 首屏历史态继续围绕同一条工作线收口；本轮主要待确认的是连接处擦边重影、尾部 finalize 微抖，以及 streaming 过程中上下拖动时的发虚/不稳
- 当前 Android 聊天 UI 的主修法已经不再是“继续压那一帧 residual 重影的局部补丁”，而是底部统一活跃区结构刀；当前主要待验证风险已经从单一“下一行一闪一消失”转为三件事：连接处重影/擦边感、尾部 finalize 微抖、以及 streaming 过程中上下拖动时的发虚/不稳。继续新增 Android UI 改动时，默认先围绕这条底部活跃区主线排查，不再把旧 placeholder / overlay follow / visual offset catch-up / requestScrollToItem 行锚定这些已撤路线重新带回来
- 历史上“把小球抬到中部以上会带出底部空白”这件事，当前已经确认不是一个单独成立的规律。旧问题的本质是：当时发送起步抖动、`conversationBottomPaddingPx` 连续变化、以及 finalize 几何链都还没收口，小球上抬只是把抖动视觉稀释了，没修掉底层几何；真正的底部空白根因在 finalize / 底部几何切换，而不在小球首发位置本身。现在这几条底层链路虽然已经分开收口，但按最新产品回归，失败态和短文本收口在“小球上抬”时仍会变差，所以当前仍固定回工作线
- 上述已收口问题的“现象 / 根因 / 当前修法 / 禁止回退”已统一固化进根 `AGENTS.md` 的 `7.5 已修复问题的成因与禁改清单`；后续新窗口如果又想改聊天滚动链，必须先对照这份清单，避免把旧问题重新带回
- 焦点 1：底部统一活跃区下的发送起步与历史尾部空间归属
  - 当前主要代码点：`ChatScreen.kt` 的 `BottomActiveZoneSlice / resolveBottomActiveZoneSlice(...)`、`commitSendMessage()`、`renderBottomActiveZone()`、`renderChatList(...)` 以及 `SubcomposeLayout` 里的 `conversation_bottom_active_zone`
  - 当前真实顺序：发送时先即时 `prepareComposerCollapse(...)`、`input.value = TextFieldValue("")`、`clearFocus/hide keyboard`，再 `upsertUserMessage(...)`、`upsertAssistantMessagePlaceholder(...)`，同一拍直接切 `streamingLocation = OVERLAY`；随后首个 `requestSendStartBottomSnap()` 只再对 `historyMessages` 做一次 `scrollToBottom(false)`，把更早历史消息压到底部活跃区上边界，而当前轮 user / assistant 从发送瞬间起就由底部活跃区贴底承接
  - 当前列表与 active zone 的空间分配也已改成“padding 覆盖”而不是“裁短列表高度”：`SubcomposeLayout` 里 `LazyColumn` 继续量到完整的 `conversationBodyHeightPx`，但在 `bottomActiveZoneVisible` 时会把 `bottomActiveZoneHeightPx` 作为 `listBottomPaddingPx` 直接喂给列表，`currentHistoryListBottomTargetPx()` 也同步改为在 Overlay 下用 `viewportEndOffset - measuredBottomActiveZoneHeightPx` 作为 history list 的正确底边目标。这样底部活跃区占用的是列表自己的 bottom padding 空区，不再把 history list 整体硬裁短后留下额外白块
  - 当前退场顺序：`finalizeStreamingStop()`、`resetStreamingUiState(...)` 和 `uiRuntimeResetKey` 进入时都会显式把 `streamingLocation` 收回 `LAZY_COLUMN`，并清掉 `anchoredUserMessageId`。这条口径仍然保留，用来避免上一轮 settled 尾巴继续残留在底部活跃区里，表现成“输入框上方像多了一截白色空白 / 静态底部文本拖不动 / 重进聊天页不贴底”
  - 当前最值得继续盯的真实代码点：`resolveBottomActiveZoneSlice(...)` 是否已经把最关键的连接处尾巴吞进 active zone、`SubcomposeLayout` 是否已经让列表和活跃区真正分区而不是继续双层重叠、以及 `scrollToBottom(false)` 在 Overlay 模式下是否只再对 `historyMessages` 生效
  - 发送起步的当前保护口径：`requestSendStartBottomSnap()` 仍只对 `historyMessages` 做一次 `scrollToBottom(false)`，但 `sendStartAnchorActive` 已重新接回现主链，用来在这次历史列表回底尚未停稳、composer 刚完成收口的窗口里临时冻结 follow / restore；不要再把“发送起步保护”误解成恢复旧 `requestScrollToItem(...)` 行锚定
  - 手动浏览的当前恢复口径：active zone 拖动起手时仍会立刻交回 `LAZY_COLUMN`，但 Overlay 恢复已经改成“先禁恢复、静止且回到底部后再 re-arm，再下一拍恢复”。这条链的目的不是永久停在列表态，而是避免拖动中的 OVERLAY / LAZY_COLUMN 来回抢渲染所有权，带出正文乱窜和与用户消息打架
  - 当前排查限制：不要再恢复 `withFrameNanos` / `withTimeoutOrNull` / `Snapshot.withMutableSnapshot` 这类发送期补丁；也不要把旧 `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout`、`pendingFinalBottomSnap`、fresh-line lock 预览层或历史区输入框联动链带回来；同时不要再把主因继续压回 `ChatScrollCoordinator.kt` 的 release gate / follow delta
  - 当前规则结论：理论上“小球第一次出现在哪”和“底部几何真相”已经可以拆开看；但当前产品口径继续固定在工作线。后续若再想重开中部上抬，仍然只能单改发送首发链，不能去碰 `streamingWorklineBottomPx`、`currentUnifiedBottomTargetPx()`、两阶段 finalize
- 回归观察项：
  - 首次进入有历史时继续直接贴底，并保持工作线以下 breathing gap 可见
  - 生成完成后不要跳到长 assistant 文本开头
  - 发送长文本后输入框要稳定回缩，不要再次出现“有时回缩、有时不回缩”的竞态
  - streaming 长段落换行时，工作线下面的新一行不要在上一行尚未上推完成前提前冒头；重点观察 `onAdvance` 的 wrap guard + 前馈预滚后是否还会残留“下一行一闪一消失”，或带来新的极轻微 batch 级停顿 / 过补偿
- 下个窗口入口：
  - 聊天滚动链默认先视为已收口；新窗口如果继续 Android UI，只盯“下一行提前冒头 / 一闪一消失”这一条，并先检查 `measureStreamingActiveBlockLayout(...)` 的宽度/样式是否和真实 active block 一致
  - 会诊时默认直接带上当前冻结基线：保留 `ff4480f`，主修法是 `onAdvance` 的 reveal-layer wrap guard，旧叶子门闩 / clip / reveal 空转参数都已移除；不要把已收住的发送微抖 / 首屏贴底 / finalize 归位重新打散
- 推荐回归入口：`docs/runbooks/chat-ui-regression.md`

## 当前阶段判断

- 代码主干已存在，当前更需要的是把“规则、状态、方案取舍、运维入口”固化进仓库，降低换窗口和长期接手成本
- 目前运维 runbook 还是骨架版，真实部署、回滚、日志、只读查库命令还需要后续在实际运维过程中逐步补实
- 正式云资源当前还没采购；本轮已新增 `docs/runbooks/infra-readiness.md` 作为采购前检查单，先把“上线前必须拍板什么”固化进仓库，等第一套真实环境落地后再把 deploy / rollback / logs / db-readonly 补成可执行入口

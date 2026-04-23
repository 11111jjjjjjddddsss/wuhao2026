# 近期重要变更

说明：本文件默认只保留最近 20 条重要变更；更早内容以 git 历史和 ADR 为准。
说明补充：本文件允许保留旧方案的历史记录；旧条目里若出现“反向列表 / requestScrollToItem(0) / asReversed()”或旧会诊对象选择等表述，默认都只是历史过程，不代表当前运行时真相或当前协作口径。当前真相始终以根 `AGENTS.md` 和 `docs/project-state/current-status.md` 为准。

## 2026-04-23

- `ChatScreen.kt` 继续把 Overlay 第一刀的收尾交接补成更接近 completed 同构：`pendingStreamingFinalizeMessageId` 非空但 Overlay 仍在场时，Overlay 已不再固定走 `StreamingRenderMode.Streaming + showDisclaimer = false`，而是切到 `StreamingRenderMode.Settled + showDisclaimer = true`。这刀不重写 Overlay 主结构、不动发送起步小球链、不重开 `dispatchRawDelta` / wrap guard，只专门压“Overlay 消失、列表 completed alpha 恢复那一拍”因为 renderMode / disclaimer 高度差带来的尾部轻微抖动风险。`./gradlew.bat :app:compileDebugKotlin` 已通过，真机尾抖是否继续减轻待回归。

## 2026-04-22
- `ChatScreen.kt` 按 ADR-0002 落地 Bottom-Anchored Streaming Overlay 第一刀。新增 `StreamingLocation.OVERLAY / LAZY_COLUMN`：发送起步仍走原 `LazyColumn` 小球锚点链；streaming 正文有内容且用户停留底部时，正文由 `SubcomposeLayout` 同层 Overlay 承接，底边锚在 composer 上方工作线，`LazyColumn` 内 active assistant item 不再双画同一份正文。Overlay 模式下 `onAdvance` 跳过 `resolveStreamingWrapGuardDecision(...)` / `dispatchRawDelta(...)` / wrap guard hold，`BindChatListScrollEffects(...)` 也不再对 streaming 正文做 follow delta。用户进入 `UserBrowsing` 时交回 `LazyColumn`；回到底部且仍 streaming、无文字选择/输入选择/拖动/fling/程序滚动/发送锚点保护/composer settling 时恢复 Overlay。`./gradlew.bat :app:compileDebugKotlin` 已通过，真机体感待验证。
- 新增 [ADR-0002](D:/wuhao/docs/adr/ADR-0002-streaming-overlay-for-active-assistant.md)，正式把下一轮 Android 生成态 UI 方向从局部补丁收敛到 Bottom-Anchored Streaming Overlay。当前判断：streaming 下一行残影、每行上推轻微发抖、完成态尾部轻微抖动共同来自“生成态 assistant 正文在正向 `LazyColumn` item 内动态长高”。已排除并禁止继续盲试的方向包括 clip/mask、renderer gate、32ms hold、requestScrollToItem 行锚定、hard bounds wait 和继续调 `dispatchRawDelta`。下一窗口若继续 Android UI，应直接按 ADR-0002 写实施计划并开 Overlay 第一刀：底部 AutoFollow 态 streaming 正文进 Overlay，小球锚点和当前发送起步链不重写。
- ADR-0002 随最新产品目标补充：用户上滑时可以把当前 streaming 正文交回 `LazyColumn`，避免 overlay 遮挡历史；但只要用户回到底部且仍在 streaming，就必须恢复 Overlay。也就是说“本轮不再回 Overlay”只能作为临时降级，不是最终方案。切回条件必须避开文字选择、输入选择、手指拖动和惯性滚动中途，其他滚动链仍不重写。
- `ChatComposerPanel.kt` 把输入框占位文案“描述种植问题”的隐藏条件从“生成中或发送收口中都隐藏”收窄为“仅 composer / IME 收口几何仍未稳定时隐藏”。当前生成中只要输入框已经清空且收口稳定，占位文案会提前回来，不再等 streaming finalize 最后一拍才和 completed 切换、发送按钮恢复一起出现。这刀只改输入框视觉显示门，不改发送禁用逻辑、工作线、streaming wrap guard、AutoFollow 或 finalize 主链。

## 2026-04-21

- 按真机“尾部完成又开始抖”的反馈，已把 `9d36f10` 之后围绕“继续压 streaming 下一行冒头残影”的实验刀整体回退，回到“Baseline Profile 预热系统完成后”的代码口径。也就是说，`requestScrollToItem(index, scrollOffset)` remeasure 行锚定、32ms time-based hold、terminal drain 禁新锚定、以及相关文档口径都撤掉；当前 streaming 新行仍以此前的 reveal-layer wrap guard + active block pre-measure + strict follow gate 为基线，先优先恢复尾部收口稳定，再决定是否重新会诊残影问题
- 根 `AGENTS.md` 和 [docs/runbooks/android-baseline-profile.md](D:/wuhao/docs/runbooks/android-baseline-profile.md) 已把“聊天页关键 UI 改动要同步检查预热系统”固化成规则：后续如果改 `ChatScreen.kt`、`ChatStreamingRenderer.kt`、`ChatComposerPanel.kt`、`ChatComposerCoordinator.kt`、`ChatRecyclerViewHost.kt`，或 Selection / Markdown / LazyColumn / 输入框 IME 主链，Codex 必须主动判断是否要更新 `:baselineprofile` 脚本、是否要重新生成 Baseline Profile；小样式 / 文案通常不用，但发版前关键路径变更要重跑 `:app:generateReleaseBaselineProfile`
- Android 客户端新增 `:baselineprofile` 模块并在 `app` 模块接入 `androidx.baselineprofile`：当前 `BaselineProfileGenerator` 覆盖冷启动进入聊天页、长文区域上下滑、输入框聚焦 / 输入 / 收起这条关键 UI 预热路径；`ChatMacrobenchmark` 同时提供 startup 与聊天滑动 / 输入框帧耗验证。该模块不点发送、不触发后端 / 模型调用，只服务 Compose 首次组合、TextLayout、Selection、LazyColumn 和输入框链路的冷启动预编译。新增 [docs/runbooks/android-baseline-profile.md](D:/wuhao/docs/runbooks/android-baseline-profile.md) 记录生成与验证命令
- `ChatScreen.kt` 继续做了一刀低风险滑动减负：消息选择 toolbar 用的 `messageSelectionBoundsById` 不再在普通滑动时被每条可见消息的 `onGloballyPositioned` 每帧写入 Compose `mutableStateMapOf`。当前普通滑动只刷新一份非 state 的 bounds cache，保证长按复制仍能立刻拿到位置；只有当前消息正在文字选择或已有 pending toolbar 等待 bounds 时，才把该消息 bounds 写入 state map 触发 toolbar 跟随。`messageContentBoundsById`、工作线、AutoFollow、streaming wrap guard 和 SelectionContainer 结构都不动
- `ChatRecyclerViewHost.kt` / `ChatScreen.kt` 给正向 `LazyColumn` 补上了按消息角色分组的 `contentType`：当前只区分 `USER` / `ASSISTANT`，不继续拆 assistant 的 waiting / streaming / settled，避免完成态切换时因为 item 类型变化导致整棵 assistant 内容树 remount。这刀只优化 LazyColumn 的节点复用池，降低用户气泡和长 assistant markdown 在滑入滑出时互相复用造成的轻微帧感；不碰工作线、AutoFollow、SelectionContainer、streaming wrap guard 或 markdown 宽度测量主链
- `ChatStreamingRenderer.kt` 继续按真机“静态 / 动态长文本滑动都有轻微不丝滑”的方向做了一刀更靠近根因的渲染减负：settled assistant markdown 现在只在整条消息外层保留一个 `BoxWithConstraints` 取得宽度，并把同一个 message 级 `TextMeasurer` 和 `availableWidthPx` 传给各个 committed block；committed paragraph/heading/bullet/numbered 不再各自创建 `TextMeasurer` 或在 bullet/numbered 内部再套 `BoxWithConstraints`。这刀只覆盖完成态 markdown 的 committed blocks，最后一个 active block、streaming 生成主链、SelectionContainer、bounds 上报和 LazyColumn 滚动链都不动
- `ChatScreen.kt` 修复了一个偶发“生成已结束、用户已输入新文字，但发送键仍保持灰色，直到把列表扒到底才亮”的状态卡住问题。根因是两阶段 finalize 在用户浏览态下也继续等待 completed assistant 的 fresh bounds 上报；如果最终 assistant 当时不可见，bounds 不会上报，`isStreaming` 就会长期保持 true，composer 的 `isStreamingOrSettling` 也会继续禁用发送。现在只有需要恢复底部锚点时才等待 fresh bounds；用户浏览态这种不需要回底的 finalize 会直接结束 streaming 状态，不再阻塞下一次输入发送
- `ChatStreamingRenderer.kt` 对 assistant markdown 渲染做了一刀很小的滑动减负：settled/streaming block 内的 paragraph / heading / bullet / numbered `TextStyle` 改为局部 `remember` 复用，并把 settled markdown block key 从 `index + model.hashCode()` 收成仅按 block index。目的只是减少长文本滑入视口时的对象创建、字符串 key 拼接和 bullet/number 宽度测量缓存噪音；没有动 `BoxWithConstraints`、SelectionContainer、bounds 上报、LazyColumn 滚动链或 streaming wrap guard，避免把上一刀已撤回的“文字调整感”重新带回来
- 已回退 `323f507 Reduce chat selection scroll churn`。那一刀把普通滑动期的 message selection bounds 改成普通缓存，并给 `snapshotFlow(readChatListMetrics)` 额外加了分桶 distinct；真机体感反馈可能带来文字“调整 / 动一下”的感觉，所以先撤回，避免把丝滑度问题越修越玄。当前保留上一版较安全的减负基线：item 线性反查移除、列表 padding 缓存、bounds/chat metrics 相同值去重、content bounds 跟踪收窄、jump button offset 分桶、streaming wrap guard pre-measure 小缓存
- `ChatScreen.kt` 修复了“聊天列表还在滚动/惯性中点输入框，输入框刚打开又立刻被关掉”的小 bug。原逻辑在 `recyclerScrollInProgress && imeVisible` 时会立即 `clearFocus + hide keyboard`，把 fling 惯性滚动也当成需要收键盘；现在这条收键盘链只在用户手指正在拖动列表（`chatListUserDragging`）时触发，列表惯性未停时点输入框不再被误杀。正常在消息区主动拖动时收键盘的行为仍保留
- `ChatRecyclerViewHost.kt` / `ChatScreen.kt` 先做了一轮聊天文本上下滑动丝滑度的低风险优化：列表宿主改为直接接收消息对象和稳定 key，移除 `itemIds = messages.map { ... }` 与每个可见 item 内部的 `messages.firstOrNull { ... }` 线性反查；`LazyColumn` 的 `PaddingValues` 改为按 padding/density `remember` 缓存；`ChatScreen.kt` 对 chat metrics、message content bounds、root/viewport/composer bounds 等高频回写点增加相同值去重，并进一步把 content bounds 跟踪收窄到最后一条消息 / active streaming / pending finalize，把 jump button 需要的首项滚动 offset 按 24px 分桶。动态生成侧只额外给 `onAdvance` wrap guard 的 active block pre-measure 加了 4 条小缓存，减少同内容同宽度的重复 `TextMeasurer` 测量。这刀不处理 streaming 下一行冒头残影，也不改工作线 / wrap guard 语义 / AutoFollow / finalize 主链

## 2026-04-20

- `ChatScreen.kt` / `ChatStreamingRenderer.kt` 把 streaming 新行防闪主链继续收口到 reveal 提交口：`onAdvance` 不再对 `advance.content` 无条件 commit，而是新增 `streamingWrapGuardTargetLineCount` 这层一次性 wrap guard。当前若 active block pre-measure 检测到“这批字符会让物理行数增加”，代码会先按实测高度差 `dispatchRawDelta(...)` 预滚，并暂时 hold 这一拍的 content/fresh 提交；下一拍同一目标行数再次出现时再放行真正 commit。这样遵守的是“上一行没完全推上去前，下一行不出现”，不是等新行进树后再做遮挡
- `ChatScreen.kt` 又继续收紧了一刀 wrap guard 的 release：当前不再按“同一目标 lineCount 再来一次”直接放行，而是要求先观察到当前已渲染内容底边已真实上移到工作线之上（`currentStreamingContentBottomPx() < currentStreamingLegalBottomPx()`）后才允许 commit；同时在 `LazyListState.canScrollBackward == false` 时直接放弃 hold，避免短内容或无可用预滚空间场景卡死
- `ChatStreamingRenderer.kt` / `ChatScreen.kt` 同次把旧 reveal 残留口径清掉了：`StreamingRevealMode.Conservative`、`strictLineReveal`、`lineRevealLocked`、`streamingLineAdvanceTick` 这一整条空转 plumbing 已删除，`ensureStreamingRevealJob(...)` 也去掉了空的 `onTick` 回调，避免下一窗口再误以为仓库里还存在一套可接线的旧行级 gate
- `ChatScreen.kt` / `ChatStreamingRenderer.kt` 把 streaming 新行防闪从“叶子 renderer 显示门闩”切到了 `onAdvance` 前馈预滚：`rememberGatedStreamingRenderedLines(...)` 已删除，两个 active renderer 现在直接渲染原始 `StreamingRenderedLines`；同时新增 `measureStreamingActiveBlockLayout(...)` 用当前 active block 的真实样式和宽度做 pre-measure，`onAdvance` 在写入下一拍 `streamingMessageContent` 前若检测到物理行数增加，就先按实测高度差 `scrollBy(deltaPx)`，旧 bounds -> follow 主链继续保留做后续精修。随后又补了一刀精度：前馈宽度与真实 assistant 宿主的 `chromeMaxWidth` 对齐，补偿高度也改成“文本实测高度”和“最小行高 * 行数”取更大值，避免只轻一点但仍残留几像素闪露
- `ChatScreen.kt` 继续收口了前馈预滚的时机：`preScrollStreamingLineAdvanceIfNeeded(...)` 已从挂起式 `listState.scrollBy(...)` 改成同步 `listState.dispatchRawDelta(...)`。目的不是改补偿量，而是避免 `scrollBy` 在旧内容仍较短时先进入 scroll session、delta 被当前 layout bounds 提前截断；现在前馈偏移会和同一个 `onAdvance` 同步块里的 content 更新一起进入下一帧 layout，再看真机是否能把剩余那一层“轻微残影”压掉
- `ChatScreen.kt` 继续收口了 clean-state 发送起步观察值的写入链：共享 measure 宿主现在会在 `renderChatList(...)` 的 `SideEffect` 里，于“输入为空 + 无 focus + IME 已收起 + composer 非 settling + 未处于 sendStart lock”的稳定窗口中，直接用首个有效 `bottomPaddingPx - STREAM_VISIBLE_BOTTOM_GAP` 种下 `observedCollapsedBottomReservePx`；同时把 `composerTopInViewportPx` 那条旧观察链收窄成“只有列表侧 `latestConversationBottomPaddingPx` 还没产出时才允许写入”的启动 fallback。上一版无条件 cold-start 预热已删除，避免 clean-state 首发后把 focus/send 锁窗口里的 padding 误记成稳定 reserve

## 2026-04-19
- `ChatScreen.kt` 把右侧用户消息气泡的最大宽度从 `chromeMaxWidth * 0.8 / 432.dp` 小幅放宽到 `0.84 / 448.dp`。目的只是让长段中文别那么早换行，视觉上更舒展一点；当前仍保持用户气泡是右侧消息气泡，不把它放大到接近整屏
- `ChatScreen.kt` 补了一刀用户消息气泡对比度：在聊天页底色改成中性浅灰后，用户消息原来的 `#F4F4F7` 底色和页面背景太接近，边界开始发糊。当前已把用户气泡改成纯白底，并补了一条极轻的描边，让右侧用户消息在浅灰页面上重新有清晰边界；这刀只动用户气泡样式，不改 assistant 文本区、工作线或滚动链
- `ChatScreen.kt` 修正了聊天页最外层“点空白处收键盘”的手势判定：现在会先区分轻点和拖动，并同时检查手势起点/终点是否都在输入框外。输入框内部长文本上下滑动、以及从输入框内部起手的拖动，不再误触发 `clearFocus + hide keyboard`，专门压“长文本编辑时一滑就收输入法、光标位置跟着乱跳”的问题
- `ChatComposerPanel.kt` / `ChatScreen.kt` 继续做了一轮输入区纯样式收口：聊天页底色统一改成中性浅灰 `#F6F7F8`，输入框保持纯白；外壳阴影从均匀 `shadow()` 收成偏下沉的双层 `dropShadow()`，让下边立体感更重；同时把输入框常态高度抬到 `92/96dp`，长文本上限放到 `232/248dp`，`maxLines` 提到 8。当前这刀仍只动外观和长文本增长上限，不改工作线、滚动链、发送期 `bottomPaddingPx` 锁或 finalize 主链
- `ChatComposerPanel.kt` / `ChatScreen.kt` 先做了一版“只换壳子”的输入区重排：宿主白底去掉，输入框改成更接近悬浮卡片的圆角浮层，加号收进左下、发送键收进右下，并同步调整了输入区尺寸与描边/阴影参数。当前这刀只动 composer 外壳与尺寸参数，不改单次 `requestScrollToItem(index, offset)`、发送期 `bottomPaddingPx` 锁、工作线算法或 finalize 主链；工作线与底部免责空白仍继续跟随 composer 的真实测量高度
- `8fb410f` 已回退 `1cdbf23 Tighten streaming line promotion gate`。当前冻结基线继续保留 `ff4480f` 的 strict follow gate 和 `283f118` 的基础显示门闩；“工作线下面下一行提前冒头 / 一闪一消失”仍按未收口风险处理，明天若继续会诊就从这条基线出发，不再顺手多撤其他滚动链修复
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 把 streaming follow 的“是否已经回到工作线”判断从发送起步 release gate 里拆开：原 `isNearStreamingWorkline()` 继续给 sendStart 保护释放等宽容差消费者用，但新增了只给 follow suppression 使用的 `isAtStreamingWorklineStrict()`，把上容差从 `assistantLineStepPx`（一整行高度）收到 `BOTTOM_POSITION_TOLERANCE(16dp)`。这样代码不再主动允许工作线下方先露出一整行才开始 follow；发送起步 release gate 则保持原宽容差，不把已收住的发送保护重新打坏
- `ChatStreamingRenderer.kt` 新增了一个只落在显示层的 streaming 行级门闩：当 `buildStableStreamingLineBuffer(...)` 第一次测出“上一行升格为 stable、下一行开始成为 activeLine”时，渲染层先继续保留上一拍已经显示出来的整组行结果，等 activeLine 后续再次真实吐字时再放行新的整组 `stableLines + activeLine`。这刀只改 renderer 单文件，不改 `ChatScreen.kt` 的 `revealMode = Free`、`onTick = {}` 或 `streamingLineAdvanceTick` 接线，专门压“工作线下面下一行提前冒头、一闪一消失”的行级 reveal 问题，同时避免恢复旧 fresh-line lock preview 的锁空串塌陷
- 静态态贴底精度继续做了最小一刀：`ChatScreen.kt` 把 `STATIC_BOTTOM_POSITION_TOLERANCE` 从 `1.dp` 收到 `0.dp`，只取消“代码主动允许 1dp 偏差存在”这件事，不动工作线、`STREAM_VISIBLE_BOTTOM_GAP`、bounds 取源和现有 `alignChatListBottom()` 主链。当前策略是先用最小风险的容差收口看能否压掉“最后一丝”，若真机上仍残留，再单独评估 Float 精度，而不是直接上 scrollBy 探针
- 静态态贴底又收紧了一刀：`ChatRecyclerViewHost.kt` 已移除正向列表尾部那颗额外的 `1dp` footer spacer，`ChatScreen.kt` 里的静态到底容差也继续收紧；首屏历史贴底、完成态归位和静态回到底部按钮不再把这颗尾项误当成“还没到底的真实内容”，专门压“看起来只差一丝、往上还能轻轻扒一点”的残余体感，同时不动工作线以下真正要保留的 `STREAM_VISIBLE_BOTTOM_GAP`
- assistant 失败态继续补齐到 0 token 场景：`ChatScreen.kt` 现在不会再把“首 token 前就失败”的 assistant 直接删掉并只弹顶部 hint，而是会保留对应的 assistant placeholder item、写入 failed assistant state，并允许本地 snapshot 持久化这类“空内容但 failed assistant”的 item。这样切后台 / 杀进程 / 重进后，`回复未完成 / 重试` 仍然有稳定锚点
- `docs/runbooks/chat-ui-regression.md` 当前已补上“切后台 / 杀进程后，完成态正文和失败态 footer 都必须恢复”的回归项，后续真机回归需要明确覆盖 completed assistant、`未发送/重发`、`回复未完成/重试` 这 3 类场景，避免只测正文不测 footer
- 本地聊天窗口持久化继续扩成 snapshot 口径：`ChatScreen.kt` 当前不再只落 `List<ChatMessage>`，而是把消息正文、failed user state、failed assistant state 一起保存到同一个本地 snapshot；读取时继续兼容旧数组格式缓存。这样 `未发送/重发`、`回复未完成/重试` 这两类 footer 在切后台、杀进程、重进后也能跟着消息一起恢复，不再只剩正文或只剩用户消息
- 带后端模式下的首屏 hydrate 也补了一刀：`SessionApi.getSnapshot()` 回来的远端历史如果还没覆盖本地失败尾巴，`ChatScreen.kt` 会把这些本地失败消息和 failed-state metadata 一起并进 hydrated snapshot，而不是让远端快照无脑把它们擦掉；同时启动时的 trailing recoverable user 也会跳过本地 failed user，避免把“未发送”误当成需要对远端做 assistant recovery 的正常尾轮
- 发送起步高度基准继续收窄：`ChatScreen.kt` 普通发送且会收口 composer 时，`conversationBottomPaddingLockPx` 不再吃当前多行输入框量出来的 `stableComposerBottomBarHeightPx`，也不再吃拍脑袋的 collapsed 常量；运行时会在“输入为空 + 无 focus + IME 已收起 + composer 非 settling”的稳定收口窗口记录真实底部 reserve（`observedCollapsedBottomReservePx`），发送起步优先直接复用这份观察值，`requestScrollToItem(index, offset)` 也继续从这份 lock 反推最终工作线，专门压“小球长文本更高、短文本更低”的现象
- 为了减少冷启动第一次发送时观察值还没采到的风险，`ChatScreen.kt` 当前又补了一层更早的预热：只要页面处于“输入为空 + 无 focus + IME 已收起 + composer 非 settling + 未处于 sendStart lock”的稳定收口窗口，就会优先从共享 measure 宿主已经拿到的 `latestConversationBottomPaddingPx` 预热 `observedCollapsedBottomReservePx`（减去 `STREAM_VISIBLE_BOTTOM_GAP`）；`composerTopInViewportPx` 那条旧观察链继续保留，作为后续校准
- 静态态贴底精度继续收口：`ChatScreen.kt` 当前把“首屏历史贴底 / 完成态归位 / 静态回到底部按钮”的到底容差从 streaming 工作线命中带里拆开，改用更紧的静态容差；专门压“已经到底但还能再往上扒出一丁点空白”的感觉，不动 streaming 过程中的工作线跟随口径
- 本地聊天窗口持久化补了一刀：`ChatScreen.kt` 的 `persistableMessagesSnapshot()` 不再把 `pendingStreamingFinalizeMessageId` 对应的 assistant 当成 transient streaming item 过滤掉。两阶段 finalize 第一阶段一旦已经把 completed 内容写回 `messages`，不论是正常 finish 还是切后台同步收口，本地落盘都会带上这条 assistant，专门修“明明生成过、切出去回来记录里只剩用户消息”
- 发送起步工作线又补了一刀同源修正：`ChatScreen.kt` 里 `commitSendMessage()` 在选定这次要锁给 `LazyColumn` 的 `conversationBottomPaddingLockPx` 后，会直接用这份锁值反推本次 `requestScrollToItem(index, offset)` 的最终 offset，不再让列表实际吃进去的是“锁定 padding”，而发送起步 offset 还继续沿用另一条预先派生的工作线快照；`pendingStartAnchorScrollOffsetPx` 只保留为锁值拿不到时的兜底
- 历史规则已单独落库：仓库当前明确把“发送瞬间小球首发位置”和“底部空白 / 完成态上跳”拆成两类问题。旧历史里多次出现“小球一上抬就出事”，本质是当时发送起步抖动、`conversationBottomPaddingPx` 连续变化和 finalize 几何链都没收口；现在发送微抖已由列表 `bottomPaddingPx` 锁压住，底部空白也已由两阶段 finalize 收口，所以这两类问题理论上已经拆开。只是按最新产品回归，小球一旦上抬到中部以上，失败态和短文本收口又会变差，所以当前仍固定回工作线
- “中部偏上首发锚点”方案已回收：`ChatScreen.kt` 恢复由 `sendStartWorklineBottomPx` 驱动 `pendingStartAnchorScrollOffsetPx`，发送瞬间的小球重新稳定贴在工作线；发送期 `bottomPaddingPx` 锁、streaming / finalize / 首屏历史态主链都保持不动
- Android 回归协作口径补充：后续这台机器上的 Android 改动默认只做 `./gradlew.bat :app:compileDebugKotlin` 编译验证，不再主动执行 `:app:installDebug`；真机安装与回归默认由用户自行完成，只有用户明确要求 Codex 装机时才执行安装
- 按真机逐帧 trace 继续收口发送微抖：`ChatScreen.kt` 当前在发送事务里新增了“列表 `bottomPaddingPx` 快照锁”，并已继续把普通发送的锁值从“发送瞬间旧 padding”修正成“最近一次观察到的稳定收口 reserve + gap”这一份终态值；如果观察值还没采到，才回退现有 `stableComposerBottomBarHeightPx / bottomBarHeightPx` 兜底链。现在只有 `collapseComposer = false` 的不收口发送才退回当前 `conversationBottomPaddingPx` 快照；`sendStartAnchorActive` 期间 `ChatRecyclerViewHost` 只继续把这份锁值喂给 `LazyColumn` 的 `bottomPaddingPx`，保护窗口退出后再退回实时值。它只锁列表 contentPadding 消费点，不锁 workline / overflow / 全局 reserve
- 本地临时日志已完成一次“加日志 -> 真机抓逐帧 trace -> 立即删除日志”的排查闭环：当前已确认发送微抖发生时 `sendStartAnchorActive` 仍为 `true`，`followStreamingByDelta(...)` 一次都没执行；真正先连续变化的是 `composerTopInViewportPx`、共享 measure 宿主给出的 `conversationBottomPaddingPx`、`streamingWorklineBottomPx` 与 `firstVisibleItemScrollOffset`。本轮没有把临时日志留在主链里，运行时代码已恢复到排查前状态
- 继续收口“发送瞬间先上再下”的微抖：`ChatScrollCoordinator.kt` 的发送起步保护释放条件已从“命中工作线容差就放行”收紧成“命中工作线且 composer 已稳定后，再连续一帧命中才放行”。当前不改发送顺序、不改几何计算，只延后 `sendStartAnchorActive` 的真正 release，避免 `requestScrollToItem(index, offset)` 首次命中后，composer 仍在 settling 或刚稳定的边界帧就过早让 follow delta 接管
- 按最新真机反馈，首次进入聊天页且本地有历史时的贴底已确认收口：`ChatScreen.kt` 的 `startupLayoutReady + isWithinBottomTolerance()` 首屏重试链、启动窗口临时 realtime composer geometry，以及 `ChatScrollCoordinator.kt` 里 `scrollToBottom(false)` 非动画路径的正向 hard bottom reposition 这三处修正继续作为当前真相保留
- 文档口径继续收平：根 `AGENTS.md`、`docs/project-state/current-status.md`、`docs/project-state/open-risks.md` 与 `docs/runbooks/chat-ui-regression.md` 已统一把当前主问题收敛为“发送瞬间整块消息区轻微上下抖一下”；首屏贴底改为已收口事项，完成态跳到长文本开头和发送后输入框回缩改为回归观察项
- 会诊规则按最新用户偏好收口：本项目后续如需外部会诊，默认整理成发给 Claude 的自包含短稿，不再继续把 Gemini 当成 Android UI 默认会诊对象
- 基础设施记忆继续补齐：新增 `docs/runbooks/infra-readiness.md`，把“服务器还没买”这一现状对应到正式云资源采购前检查单；`pending-decisions.md` 与 `open-risks.md` 也同步补上“正式云资源首版怎么落 / 正式云资源尚未采购”两项，避免后续第一次上云时再靠聊天记录回忆

## 2026-04-18

- 正向底座的 `scrollToBottom(false)` 非动画路径已收紧：如果最后一条底边仍未进入可视区，会先做一次 hard bottom reposition，再交给 `alignChatListBottom()` 做工作线精修
- 首屏首次贴底 effect 改为等 `startupLayoutReady` 后持续重试，只有命中底部容差才把 `initialBottomSnapDone` 记完成，避免“一次滚完就关门”
- 启动窗口允许临时参考一次 realtime composer geometry，把首屏最后几像素压准到工作线；命中后立即退回静态口径，不把历史区输入框联动带回来
- `LazyListState` 启动若本地有历史，会先从最后一条历史消息起步，并关闭 `rememberSaveable` 的旧滚动恢复，减少首几秒落在长文本中段造成的重影 / 闪动
- `scrollToBottom(false)` 与 finalize 收口都不再使用 `requestScrollToItem(lastIndex)` 这类 top-anchor，把“跳到长文本开头”改为复用现有静态底线主链
- `ChatScrollCoordinator.kt` 的 streaming AutoFollow 改为按“当前内容底边 - 工作线底边”的单次 delta 做 `scrollBy` 微调，减少 fake streaming 过程中的重叠闪烁
- 首屏显示链继续清理：列表 / 欢迎语 reveal 只再依赖 hydration barrier，stale streaming runtime 在冷启动 reset 时主动清空，继续收口首屏白屏与空列表

## 2026-04-17

- `ChatScreen.kt` 的消息列表与 composer 改为 `SubcomposeLayout` 共享 measure 宿主，同拍产出底部 reserve，不再完全依赖 `composerTopInViewportPx` 异步回写链
- `sendUiSettling` 收紧成只覆盖发送起步同步窗口，专门压“发送长文本后输入框有时不回缩”的时序竞态
- `ChatStreamingRenderer.kt` 统一最外层宿主、删除 fresh-line lock 预览、把 block 间距改成独立 `Spacer`，继续减少 streaming 下掉与完成态微重排
- 两阶段 finalize 进一步收紧为“等 settled fresh bounds 到位后再切 streaming 状态”，后台期间暂停等待，继续压完成后上跳和底部留白
- 根 `AGENTS.md` 新增已修复问题禁改清单与会诊稿规则，要求外部会诊稿默认自包含代码片段、限制条件和已排除项

## 2026-04-16

- 发送链撤掉 `withTimeoutOrNull` 延后收口实验，回到“输入框瞬间清空回缩 + placeholder 同事务插入”的单一发送主链
- 聊天 UI 当前热点与 runbook 开始沉淀进仓库，`scripts/check_project_memory.py` 与 CI 已接入项目记忆检查
- streaming block 改为 unified host，外壳 key 收口为稳定 block index，减少流式阶段 remount
- 普通 idle / 历史浏览状态下不再让列表长期追实时 composer 几何，避免历史区和输入框重新联动

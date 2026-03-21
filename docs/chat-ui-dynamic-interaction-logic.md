# Chat UI 动态交互逻辑

适用文件：
- `app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt`

本文档统一描述主聊天页的动态交互规则。  
后面不再拆第二份“生成区规则文档”，文本生成、滚动 owner、键盘联动、回到底部按钮，都以这份为准。

## 目标

聊天页最重要的不是静态排版，而是下面几条动态体验同时成立：

- 发送后，用户消息上抬到固定视觉区域。
- 生成中，只有活动尾巴变化，旧内容尽量稳定。
- 手动上滑看历史时不抢手。
- 只有真正回到底部后，自动跟随才重新接管。
- 键盘、输入框、免责声明、回到底部按钮之间不能互相打架。
- clean-state 下也必须成立，不能依赖旧历史、旧视口、旧草稿。

## 关键状态

### 1. 滚动 owner

- `autoScrollMode`
  - `Idle`
  - `AnchorUser`
  - `StreamAnchorFollow`
- `userInteracting`
- `userDetachedFromBottom`
- `programmaticScroll`
- `pendingResumeAutoFollow`
- `pendingFinalBottomSnap`

结论：
- 聊天页同一时刻只能有一个滚动 owner。
- 发送后上抬归 `AnchorUser`。
- 正文生成跟随归 `StreamAnchorFollow`。
- 用户手动浏览优先级高于自动跟随。

### 2. 发送后锚点

- `anchoredUserMessageId`
- `streamBottomSpacerPx`
- `streamingAnchorTopPx`
- `streamBottomFollowActive`

结论：
- 发送后上抬依赖尾部锚点 spacer。
- 这个 spacer 只在生成中且用户没有脱离底部时生效。
- 回到底部按钮在生成中不能先滚到 spacer 空白，再滚正文。

### 3. 生成渲染

- `streamingMessageContent`
- `streamingRevealBuffer`
- `streamingLineAdvanceTick`
- `lineRevealLocked`
- `streamingContentBottomPx`
- `streamingWorklineBottomPx`
- `streamingFreshStart`
- `streamingFreshEnd`
- `streamingFreshTick`

结论：
- 已完成块尽量稳定。
- 新鲜下一行必须延迟释放，避免出现两次。
- 工作线只认输入框本体，不认输入法高度。
- 新增尾巴可以做很轻的显示润滑，但不能做位移动画。

### 4. 键盘与输入框

- `bottomBarHeightPx`
- `inputChromeRowHeightPx`
- `composerTopInViewportPx`
- `restoreBottomAfterImeClose`
- `suppressJumpButtonForImeTransition`

结论：
- 输入区高度要用输入框本体高度加底部安全区，不要用带 `imePadding()` 的整块高度直接回灌列表。
- 键盘开关期间，回到底部按钮需要短暂抑制。
- 键盘收起后的静默补底，只能在静态场景生效，不能干扰发送后锚点链路。

## 主流程

### 1. 发送消息

入口：
- `commitSendMessage()`

顺序：
1. 写入用户消息。
2. 记录 `anchoredUserMessageId`。
3. 清空输入框并收起键盘。
4. 初始化 streaming 状态。
5. 计算 `streamBottomSpacerPx`。
6. 切到 `AutoScrollMode.AnchorUser`。
7. `sendTick++` 后由 `LaunchedEffect(sendTick)` 执行 `scrollAfterSendAnchor()`。

约束：
- 发送时不能有别的到底部补偿链路插进来。
- 发送后的第一个滚动 owner 只能是 `AnchorUser`。

### 2. 从发送后上抬切到正文跟随

入口：
- `LaunchedEffect(sendTick)`
- `LaunchedEffect(isStreaming, streamingMessageContent.isNotBlank(), autoScrollMode, userDetachedFromBottom)`

顺序：
1. 用户消息先被锚到视觉工作区。
2. 当 streaming 正文真正出现后，模式从 `AnchorUser` 切到 `StreamAnchorFollow`。

约束：
- 正文没出来前，不能提前切跟随。
- 用户如果已经脱离底部，不能强行切回跟随。
- 必须等正文底边真正压到工作线以下，才允许从 `AnchorUser` 切到 `StreamAnchorFollow`。
- 切换当帧如果存在 `userInteracting`、`listState.isScrollInProgress` 或 `programmaticScroll`，必须继续留在 `AnchorUser`。

这条是为了防止“刚出半行/一行时用户上滑看历史，用户气泡突然往下掉”的回归。提早 handoff 会把发送后锚点 spacer 撤早，表现就是用户消息猛地下坠。

### 3. 生成中自动跟随

入口：
- 自动跟随相关 `LaunchedEffect(...)`

顺序：
1. 用 `currentStreamingOverflowDelta()` 算出活动尾巴超出工作线的距离。
2. 按小步 `scrollBy()` 连续追平。
3. 每次真实上推时递增 `streamingLineAdvanceTick`。

约束：
- 自动跟随只负责把活动尾巴贴住工作线。
- 它不应该驱动用户浏览状态变化。

### 4. 新鲜下一行释放

入口：
- `rememberLockedStreamingRenderedLines()`

规则：
- 只有存在 `stableLines` 和 `activeLine` 时，才进入严格门槛。
- `lineRevealLocked` 为真时，新鲜下一行只保留锁定预览，不正式露出。
- 解锁后还要再等很短的 settle 帧数，保证上一行先站稳。
- 目标是“下一行第一次出现即为正式行”，不要先露头再变位置。
- `lineRevealLocked` 不是单阈值开关，而是带锁定/解锁双阈值的滞回门槛；不要再改回“一帧锁、一帧放”的单阈值方案。
- 当前 settle 规则分两档：正常释放要多等一点；如果刚发生过真实上推，则仍要等，但会稍短一点。不要把这层缓冲砍掉。

当前基线数字：
- `STREAM_FRESH_LINE_SETTLE_FRAMES = 5`
- `STREAM_FRESH_LINE_AFTER_FOLLOW_SETTLE_FRAMES = 4`

这条是当前生成稳定性的核心。

### 5. 手动浏览与回到底部按钮

入口：
- `snapshotFlow` 监听列表滚动
- `shouldOfferJumpButton`
- `jumpToBottom()`

规则：
- 上滑看历史后，`userDetachedFromBottom = true`。
- 生成中，只有真正手动脱离底部后才允许出现回到底部按钮。
- 点击回到底部按钮时，如果还在 streaming，先对齐正文尾部，不先滚到锚点 spacer。
- 只有真正回到底部后，自动跟随才重新接管。

### 6. 键盘开关

入口：
- `LaunchedEffect(imeVisible)`

规则：
- 打开键盘时，只记录是否需要静默恢复到底部，并抑制回到底部按钮。
- 关闭键盘时，只在静态场景做一次静默补底。
- streaming 中、发送后上抬中、锚点 spacer 生效中，都不能让键盘补底逻辑插手。

### 7. 生命周期切后台与返回

入口：
- `LifecycleEventObserver`
- `LaunchedEffect(restoreBottomAfterLifecycleResume, isStreaming, messages.size, hasStreamingItem)`

规则：
- 如果正在 streaming 且切到后台，不做真后台续流；当前实现是直接收尾成完成态，保证回来后排版稳定。
- 如果静态场景下本来就在底部，切后台时只记录一个“恢复时静默贴底”标记。
- 回到前台后，这个静默贴底只在静态场景执行；执行期间需要压住回到底部按钮，避免因为底部差一丢丢就误冒。

结论：
- 当前目标是“回来后稳定、排版正常、按钮不误冒”，不是“后台持续流式生成”。
- 后续如果接真 SSE 并想做后台续流，那是新能力，不要误以为当前链路已经支持。

### 8. 消息文本选区

入口：
- 长按消息后的黑底操作卡片
- 原位消息内选择态
- 新版 foundation text context menu

规则：
- 静态消息默认不再“直接长按进入原生选区”；先弹一个黑底白字小卡片，只保留 `全部复制 / 选择文字` 两个动作。
- 这个黑卡片不要再走 `Popup` 单独窗口；要挂在聊天根容器里按长按点位原位浮出，避免长按手势和 `Popup` 外部点击/窗口定位互相打架，导致“明明长按了但什么都看不到”。
- 长按入口优先走 `combinedClickable` 这类标准上下文菜单链，不要再依赖更脆的 `detectTapGestures(onLongPress)` 去撑消息操作卡片。
- `全部复制` 只复制当前这一条消息的全文，并触发轻震动。
- `选择文字` 不走覆盖层，不弹单独大框；而是把当前这条消息切到原位选择态，在消息本身上进入只读选区。
- 原位选择态会带一个稍长一点的初始选区，避免首次只起选一两个字。
- `ComposeFoundationFlags.isNewContextMenuEnabled = true`，保留新版原生文本工具条。
- `ComposeFoundationFlags.isSmartSelectionEnabled = false`，因为它会让长按起点更容易偏离用户手指落点，造成“按这里却飞到别处”的观感。
- 不要因为刚进入滚动态就清掉消息选区；用户可能需要保留选区后上下查看已选内容。
- 手动滚动列表时，只隐藏消息文本工具条和长按黑卡片，不主动清空选区；不要让 `复制/全选` 菜单跟着列表滚到顶部或输入框附近。
- 用户消息右对齐 owner 必须是最外层固定 `Row(... Arrangement.End)`，不要再让 `SelectionContainer` 直接决定用户气泡位置。

结论：
- 当前目标是“先分流动作，再在原消息上选择文字”，避免直接长按底部消息时立刻打进原生选区链带来的上窜。
- 当前实现仍然优先保留原消息上的局部拖选能力，但通过 `全部复制 / 选择文字` 把“整条复制”和“局部选择”分开，减少误触和跳动。

## 文本生成动态规则

这部分是现在生成区为什么比早期稳定的核心，不是单个参数问题。

### 1. 先按块切开，不再整段一起重排

入口：
- `splitStreamingBlockState()`
- `AssistantStreamingContent()`

规则：
- streaming 内容拆成 `completedBlocks + activeBlock`
- 已完成块尽量稳定
- 只有最后一个活动块继续变化

作用：
- 旧字不会因为尾巴长了就整段一起跳

### 2. 再按视觉行切分，老行固定，只有最后一行活着

入口：
- `buildStableStreamingLineBuffer()`

规则：
- 先用 `TextMeasurer` 对整段只测一次
- 再用 `layout.getLineStart / getLineEnd` 切视觉行
- 前面的视觉行进 `stableLines`
- 只有最后一行作为 `activeLine`

作用：
- 不再是“每多一个字就整段重新量一遍”
- 中长段落生成和滚动更稳

### 3. 标题、列表、引用要提前预测

入口：
- `classifyActiveStreamingLine()`

规则：
- 在活动行阶段就提前识别：
  - `#` 标题
  - `-` / `*` 列表
  - `1.` 编号
  - `>` 引用
- 只要前缀已经满足结构条件，就直接用对应样式渲染

作用：
- 不会先按普通正文小字出来，后面再突然变成标题或列表
- 这就是“标题排版提前预测”

### 4. 流式态和完成态尽量走同一套块级逻辑

入口：
- `AssistantStreamingActiveBlock()`
- `AssistantStreamingCommittedBlock()`
- `AssistantMarkdownContent()`

规则：
- 生成中和完成后，尽量保持同一套块级结构认知
- 避免 streaming 结束后切到另一套完全不同的段落排版器

作用：
- “先紧后松、结束后再整体重排”会明显减轻

### 5. 新的一行不能抢跑露头

入口：
- `rememberLockedStreamingRenderedLines()`

规则：
- 下一行没到正式释放条件前，不允许抢跑露字
- 不能先在左下角闪一下，再被推到正式位置
- 允许存在极轻微自然动态感，但不允许“出现两次”

作用：
- 压住“飞字”“冒头”“闪字”

### 6. 新增尾巴只做轻微颜色提亮，不做扫光、阴影或位移动画

入口：
- `StreamingAnimatedLineText()`

规则：
- 只对活动行尾巴最后一小段字做短时颜色提亮，再回到正文色。
- 当前实现是 `STREAM_FRESH_SUFFIX_HIGHLIGHT_COLOR -> style.color` 的颜色回落，不再叠阴影、灰底条、扫光或白色拖带。
- 当前最少覆盖尾巴最后 `6` 个字；不是单字闪，也不是整行亮。
- 高亮不是每批字都重启，而是按时间桶触发；当前间隔约 `620ms`，或遇到换行时强制触发一次。
- 不做 `translateY`
- 不做整行缩放
- 不做行高动画
- 不做 shimmer / sweep gradient / 明显的白光扫过

当前基线数字：
- `STREAM_FRESH_SUFFIX_MIN_HIGHLIGHT_CHARS = 6`
- `STREAM_FRESH_SUFFIX_HIGHLIGHT_MS = 180`
- `STREAM_FRESH_SUFFIX_TRIGGER_INTERVAL_MS = 620L`
- `STREAM_FRESH_SUFFIX_HIGHLIGHT_COLOR = 0xFFDDE1E6`

作用：
- 正文有一点 GPT 式尾巴活性，但不会回到“高频闪眼”“灰影”“黑带”的副作用。
- 不会破坏已经调稳的释放门槛和上推节奏。

### 7. GPT 感主要来自节奏和稳定性，不来自重动画

当前真实来源是三件事叠加：
- reveal 走小批量连续吐字，而不是单字打字机。
- 已完成块和已成型行尽量稳定不重排。
- 活动尾巴只保留非常轻的颜色提亮。

当前节奏基线：
- `STREAM_REVEAL_FRAME_BUDGET_MS = 40L`
- `STREAM_REVEAL_MAX_TOKENS_PER_BATCH = 4`
- `STREAM_DELAY_MULTIPLIER = 1.18`
- `STREAM_FRESH_SUFFIX_HIGHLIGHT_MS = 180`
- `STREAM_FRESH_SUFFIX_TRIGGER_INTERVAL_MS = 620L`

结论：
- 正文不要追求明显扫光、明显 shimmer、明显位移动画。
- 如果后面要继续逼近 GPT 观感，优先调 reveal 节奏和尾巴提亮频率，不要先加更重动画。
- GPT 正文的“顺”更像是小批量 flush + 稳定排版 + 极轻尾巴活性，不像思考态标签那种扫光。

### 8. 文本生成这条链路的调参顺序

如果以后要再调生成观感，按下面顺序收，不要反着来：

1. 先判断问题属于 reveal 节奏、尾巴提亮、下一行释放、还是滚动 handoff。
2. 如果观感太像打字机，先看 batch size 和整体 delay，不要先加重动画。
3. 如果观感太闪眼，先收提亮频率，再收亮度对比和高亮窗口，不要直接降正文速度。
4. 如果出现飞字/冒头，优先检查 `lineRevealLocked`、滞回阈值和 settle 帧，不要先碰颜色动画。
5. 如果出现用户消息下坠、空白页、误冒回底按钮，优先检查 owner/handoff/静默补底链路，不要去改 Markdown 或样式层。

### 9. 当前关键布局数字

这些数字不需要每次小调都全文抄一遍，但当前版本的关键基线应该明确：

- `ASSISTANT_START_ANCHOR_TOP = 196.dp`
  发送后用户消息上抬的目标视觉区域
- `STREAM_VISIBLE_BOTTOM_GAP = 44.dp`
  生成工作线和输入框之间的目标距离
- `BOTTOM_OVERLAY_CONTENT_CLEARANCE = 4.dp`
  静态正文/免责声明离输入框的底部避让
- `SECTION_DIVIDER_TOP_EXTRA_GAP = 16.dp`
- `SECTION_DIVIDER_GAP = 28.dp`
  这两项决定标题分割线出现时的上下留白；如果这里观感太跳，不要先上动画，优先检查是否需要提前占位或轻收 gap

## 当前必须守住的铁规则

- 不要再让 `streamBottomSpacerPx` 同时决定布局和到底部补偿结果。
- 不要让键盘补底逻辑干扰发送后上抬和 streaming 跟随。
- 不要把 `AnchorUser -> StreamAnchorFollow` 的切换提前到正文真正碰到工作线之前。
- 不要让新鲜下一行在正式出现前先露字。
- 不要把 `lineRevealLocked` 改回没有滞回的单阈值开关。
- 不要让完成态和 streaming 态切换到两套完全不同的排版器。
- 不要把这些交互规则做成本地设置项。
- 不要给活动行加位移动画、缩放动画、行高动画。
- 不要把正文尾巴提亮改成明显阴影、灰底条、扫光或大面积白光。
- 不要把标题/列表在活动态退回普通正文样式。

## 后续改动顺序

如果未来还要改聊天页，优先按下面顺序判断：

1. 这次改动属于发送后上抬、streaming 跟随、手动浏览、键盘联动中的哪一条。
2. 这条改动会不会引入第二个滚动 owner。
3. 这条改动会不会破坏“下一行第一次出现就是正式行”。
4. 这条改动会不会让标题/结构行退回“先普通正文、后切样式”。
5. 这条改动在 clean-state 下是否也成立。

## 回归重点

除了 [docs/chat-ui-clean-state-checklist.md](D:/wuhao/docs/chat-ui-clean-state-checklist.md)，主聊天页还要额外盯这几条：

- 发送后不能出现整页空白。
- 生成中下一行不能先在左下角闪一下再上去。
- 标题、列表、引用不能先按正文出来，再突然变样式。
- 点输入框时，工作线不能跟着键盘一起明显上抬。
- 收键盘时，回到底部按钮不能误冒出来。
- 生成中上滑、下滑、点回到底部按钮，不能出现空白页。
- 正文刚出半行/一行时上滑看历史，用户消息不能猛地往下掉。
- 尾段最后一两行不能突然整块蹦出来。
- 切后台十几分钟再回来，如果之前就在底部，不能因为差一丢丢就把回到底部按钮误叫出来。

## 当前残余风险

- `ChatScreen.kt` 仍然过大，动态交互状态集中在一个文件里，后续修改容易误伤不相干链路。
- 部分状态值仍然通过多个 `LaunchedEffect` 共同维护，未来改动要先看 owner 是否冲突。
- 尾巴提亮现在是“非常轻”的正文活性提示，不是重动画。后续如果只盯视觉效果加重，很容易重新引入闪眼、灰影、黑带或高频触发副作用。

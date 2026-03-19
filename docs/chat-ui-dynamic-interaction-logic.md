# Chat UI 动态交互逻辑

适用文件：

- `app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt`

本文档只描述主聊天页的动态交互逻辑，不描述静态按钮样式。

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

结论：

- 已完成块尽量稳定。
- 新鲜下一行必须延迟释放，避免出现两次。
- 工作线只认输入框本体，不认输入法高度。

### 4. 键盘与输入框

- `bottomBarHeightPx`
- `inputChromeRowHeightPx`
- `composerTopInViewportPx`
- `restoreBottomAfterImeClose`
- `suppressJumpButtonForImeTransition`

结论：

- 输入区高度要用输入框本体高度加底部安全区，不要用带 `imePadding()` 的整块高度直接回灌列表。
- 键盘开关期间，回到底部按钮需要短暂抑制。
- 键盘收起后的静默补底，只能在静态场景生效，不能干扰发送后的锚点链路。

## 主流程

### 1. 发送消息

入口：`commitSendMessage()`

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

### 3. 生成中自动跟随

入口：

- `LaunchedEffect(autoScrollMode, isStreaming, hasStreamingItem, userInteracting, userDetachedFromBottom, streamTick, streamingContentBottomPx, lineRevealLocked)`

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

- 只有存在 `stableLines` 和 `activeLine` 时才进入严格门槛。
- `lineRevealLocked` 为真时，新鲜下一行只保留锁定预览，不正式露出。
- 解除锁后仍要再等一小段 settle 帧数，保证上一行先站稳。
- 目标是“下一行第一次出现即为正式行”，不要先露头再变位置。

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

## 当前必须守住的铁规则

- 不要再让 `streamBottomSpacerPx` 同时决定布局和到底部补偿结果。
- 不要让键盘补底逻辑干扰发送后上抬和 streaming 跟随。
- 不要让新鲜下一行在正式出现前先露字。
- 不要让完成态和 streaming 态切换到两套完全不同的排版器。
- 不要把这些交互规则做成本地设置项。

## 后续改动顺序

如果未来还要改聊天页，优先按下面顺序判断：

1. 这次改动属于发送后上抬、streaming 跟随、手动浏览、键盘联动中的哪一条。
2. 这条改动会不会引入第二个滚动 owner。
3. 这条改动会不会让“下一行第一次出现”被破坏。
4. 这条改动在 clean-state 下是否也成立。

## 回归重点

除了 [docs/chat-ui-clean-state-checklist.md](D:/wuhao/docs/chat-ui-clean-state-checklist.md)，主聊天页还要额外盯这几条：

- 发送后不能出现整页空白。
- 生成中下一行不能先在左下角闪一下再上去。
- 点输入框时，工作线不能跟着键盘一起明显上抬。
- 收键盘时，回到底部按钮不能误冒出来。
- 生成中上滑、下滑、点回到底部按钮，不能出现空白页。

## 当前残余风险

- `ChatScreen.kt` 仍然过大，动态交互状态集中在一个文件里，后续修改容易误伤不相干链路。
- `applyStreamingLineRevealGate()` 目前是空壳，后续如果误以为它还在接管 reveal，容易走偏。
- 部分状态值仍然通过多个 `LaunchedEffect` 共同维护，未来改动要先看 owner 是否冲突。

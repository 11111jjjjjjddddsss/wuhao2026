# ADR-0003 正向列表聊天 UI 稳定主链

日期：2026-04-28

## 状态

Accepted

## 背景

农技千问聊天页在 2026 年 4 月经历过多轮方案切换：

- 正向列表 + 高度补偿
- streaming overlay / bottom active zone
- 反向 `LazyColumn(reverseLayout = true)`
- streaming 小分割 / block item 化
- 回到单一正向 `LazyColumn`

旧方案共同问题是：为了解一个局部抖动，容易引入第二套坐标系、第二个消息主人、异步高度补偿或渲染树切换，最终在发送起步、用户滚动、完成态收口、输入框 IME 之间互相打架。

当前用户真机反馈：正向列表主链在滚动、收口、手动回底、工作线贴底上已经比较稳定。因此需要把当前主链固化成规则，避免后续窗口再把已废弃方案带回来。

## 决策

聊天消息运行时当前只保留一个主人：正向 `LazyColumn`。

关键规则：

1. `messages` 按 oldest -> newest 存储并直接传给列表，视觉底部最新消息是 `lastIndex`。
2. `ChatRecyclerViewHost.kt` 使用普通 `LazyColumn` 和 `verticalArrangement = Arrangement.Bottom`，不再使用 `reverseLayout` / `items.asReversed()`。
3. 回到底部和 AutoFollow 使用最新消息 `lastIndex + FORWARD_LIST_BOTTOM_SCROLL_OFFSET`。
4. 工作线 gap 固定为 `96.dp`。waiting 小球、streaming 正文底边、开机历史态、完成态尾部都围绕这条工作线；工作线以下空白必须完整露出来。
5. 小球锚点稳定不是靠 overlay，也不是靠 streaming 小分割。它依赖：
   - 折叠态 composer 宿主的稳定实测高度；
   - 发送期 `lockedConversationBottomPaddingPx / sendStartBottomPaddingLockActive` 把列表 reserve 短窗口锁住；
   - `sendStartAnchorActive` 避免发送当拍被其他滚动逻辑接管；
   - 发送事务里同步请求最新消息底部锚点；
   - AutoFollow reveal 前预锚定 + `SideEffect` 同帧底部锚定。
6. composer 是页面底部兄弟层，自己吃 `imePadding()`，根容器和列表不吃 IME 动画帧。输入框内容高度不能进入聊天列表 reserve。
7. streaming 正文和 settled 文本共用同一条 assistant item 和同一套 soft-wrap renderer；完成态继续保留两阶段 finalize。
8. 用户进入 `UserBrowsing` 后让权。用户手动滑回正向列表物理底部后，先请求一次底部锚点，再恢复 `AutoFollow`。

## 禁止回退

以下方案已经证明会放大问题或污染判断，禁止在没有新 ADR 的情况下恢复：

- mixed active-zone / overlay 作为第二消息主人；
- `StreamingLocation`、`BottomActiveZoneSlice`、`renderBottomActiveZone()`；
- `requestSendStartBottomSnap()` history-only 发送起步链；
- streaming 小分割 / block item 化；
- `scrollBy(...)` / `dispatchRawDelta(...)` 作为 streaming 高度补偿；
- 旧 committed 物理行预切 / TextMeasurer 渲染链；
- 同拍直接切 settled，绕过两阶段 finalize；
- 把输入框当前内容高度、IME 动画高度、附件预览高度偷渡进消息列表 bottom reserve。

## 结果

收益：

- 小球首发锚点稳定，不再被长输入框或键盘动画顶高。
- streaming 下一行冒头闪由 `SideEffect` 同帧锚定压住。
- 用户上滑 / 下滑时由单一列表状态机接管，不再发生 overlay / list 切管。
- 完成态收口不再换成另一套渲染树，也不再主动把长回复重新锚到上方。
- 输入框 IME 动画与消息工作线解耦。

代价：

- 当前依赖 Compose 正向列表里 positive `scrollOffset` 会把 item 向上推并在末端 clamp 的实现语义。
- 仍需要继续用不同机型、字体缩放、输入法和 Android 版本回归，确认 `SideEffect` 同帧锚定和底部 reserve 在低端设备上也稳定。
- 真表格渲染、C+ 长期资产、精确地点采集、天气 API 等都不是本 ADR 处理范围。

## 回归入口

见 `docs/runbooks/chat-ui-regression.md`。

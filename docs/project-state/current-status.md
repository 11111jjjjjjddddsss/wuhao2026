# 当前状态

最后更新：2026-04-25

## 项目概况

- 项目：农技千问
- 目标：面向农作物问题提供农业技术参考建议，不提供绝对诊断
- 当前仓库两条主线：Android 客户端 `app`，Go 后端 `server-go`

## 当前代码真相

- Android 端当前使用 Jetpack Compose 聊天界面，不再依赖 WebView 模板页面
- Android Auto Backup 当前已关闭；本地聊天窗口快照、流式草稿等都只作为本机运行时缓存，不允许被系统云备份在清数据 / 重装后恢复成旧 UI 状态。后端仍是业务真相来源
- 底部 composer 仍是页面底部的独立 UI 宿主，继续负责输入、IME、placeholder、发送禁用与收口视觉；**它不是消息运行时主人**
- composer 内部内容高度不再作为聊天列表 reserve 真值。多行文字、未来图片预览、附件缩略图、图文混排等只能影响输入框内部布局 / 内部滚动 / composer 自身视觉高度；聊天列表 bottom padding 只允许吃折叠态 composer 外壳、safe area / IME / 底部外部几何、发送期锁定 reserve 和工作线 gap
- 聊天消息运行时当前已切回**单一列表主人**
- 当前主线代码已删除 mixed active-zone 运行时的核心切管结构：
  - `StreamingLocation`
  - `BottomActiveZoneSlice / resolveBottomActiveZoneSlice(...)`
  - `renderBottomActiveZone()`
  - active-zone 拖动接管 / Overlay 恢复门
  - `requestSendStartBottomSnap()` 这条“只滚 historyMessages”的发送起步链
- `ChatRecyclerViewHost.kt` 当前已重新切回反向列表底座：
  - `LazyColumn(reverseLayout = true)`
  - `items.asReversed()`
  - 列表成为当前轮 user / assistant / streaming / settled 的唯一消息主人
- `ChatScreen.kt` 当前已经按单主人口径收平：
  - `chatListMessages = messages`
  - `currentLastMessageContentBottomPx()` 的 fallback 已改回 reverse-list 口径，底部最新显示项按 index `0` 取值
  - `currentBottomOverflowPx()` 不再走 active-zone / history list 分支；现在按 reverse-list 单主人口径只计算“最新消息可见底边低于统一底部目标”的欠滚距离，内容底边已经高于目标时视作已到底，避免过滚误触发补滚
  - `isNearStreamingWorkline()` / `isAtStreamingWorklineStrict()` 已不再包含 Overlay 快捷分支
- 发送起步当前重新回到 list-side 单主人口径：
  - 仍保留 `lockedConversationBottomPaddingPx` / `sendStartBottomPaddingLockActive`
  - 仍保留 `sendStartAnchorActive` 作为发送起步保护窗口
  - `sendStartBottomPaddingLockActive` 期间，列表 bottom padding 与 streaming 工作线共用同一份锁定几何：`streamingWorklineBottomPx = lockedMessageViewportHeightPx - lockedConversationBottomPaddingPx`，避免长文本输入框的实时高度把小球锚点顶高
  - `observedCollapsedBottomReservePx`、`bottomBarHeightPx`、`latestConversationBottomPaddingPx` 等 reserve 值不能从输入框内容扩展中学习；后续图片预览如果进入 composer，也按输入内容处理，不允许偷渡成聊天列表外部 reserve
  - 不再通过 active zone 切层后再 `scrollToBottom(false)`
  - 当前是在 `commitSendMessage()` 内同步插入 user + assistant placeholder 后，按 reverse-list 口径同步 `requestScrollToItem(0)`，让新 assistant placeholder 稳定占住视觉底部
  - `prepareScrollRuntimeForStreamingStart(...)` 会同步把滚动模式置为 `AutoFollow`，避免用户浏览态发送后还残留 `UserBrowsing` 语义
- 完成态收口当前**保留**两阶段 finalize：
  - `beginPendingStreamingFinalize(...)`
  - fresh bounds 到位后 `finalizeStreamingStop(...)`
  - 没有回退到 old reverse 那种“直接切 settled 不等 fresh bounds”的简化版
- `ChatScrollCoordinator.kt` 当前也已回到单主人口径：
  - `scrollToBottom(false)` 重新按 reverse-list 走 `scrollToItem(lastIndex)`，其中当前 `lastIndex` 在聊天页主调处按 `0` 传入
  - active-zone 时代专用的 `streamingBodyFollowEnabled` 开关已经从 coordinator 主链里移除
  - 旧正向 / overlay 时代的 streaming raw follow 链已移除：反向列表不再在 streaming 正文高度变化时额外调用 `followStreamingByDelta(...)` / `scrollBy(...)` 追滚，`streamBottomFollowActive` 空壳状态也已删除，避免和用户拖动、reverse-layout 自身底部锚定打架
  - streaming 期间任何非程序性的列表滚动都会立即进入 `UserBrowsing`，不再只等 `collectIsDraggedAsState()` 命中；只有列表停稳且重新回到工作线后，才允许恢复 `AutoFollow`

## 当前调试焦点

- 这轮“反向列表单主人”重构的目标，是同时保住两件事：
  - 不再出现 mixed owner 带来的发送瞬间抖动、拖动乱窜、与用户消息重叠
  - 不直接回退到 overlay 之前那套正向单列表主链，以免把 streaming 冒头/闪烁和 finalize 收口抖动原样带回
- 当前最需要真机验证的是：
  1. 首屏进入有历史时是否稳定贴底
  2. 发送瞬间的小球 / 历史文本是否仍抖动
  3. streaming 过程中上下拖动是否不再乱窜、重叠、抢手
  4. finalize 收口是否仍保持前几轮好不容易压下去的稳定度
  5. 输入框上方和静态文本底部是否不再出现额外白块

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

- mixed active-zone / overlay 架构经过多轮补丁后，已经证明不适合作为最终方案；继续补只会在“发送抖、拖动乱窜、消息重叠”和“闪烁 / 收口抖”之间来回换问题
- 直接回到 overlay 之前的正向单列表主链，也大概率会把 streaming 冒头/闪烁和 finalize 收口问题重新放大
- 当前仓库已正式转向“**单一运行时主人 + 反向列表**”这条路线；这不是回滚到旧 reverse 的全部历史实现，而是：
  - 恢复 reverse-list 的物理模型
  - 保留 send-start 保护窗口
  - 保留两阶段 finalize
  - 删除 mixed active-zone 的切管桥

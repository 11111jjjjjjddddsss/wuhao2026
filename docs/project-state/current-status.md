# 当前状态

最后更新：2026-04-26

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
- streaming 用户浏览态当前新增同一列表内的展示层拆分：用户触碰/上滑接管后，`ChatScreen.kt` 会把当时已经渲染的 assistant 内容冻结成稳定前缀 item，并把后续新 token 放入同一个 `LazyColumn` 的 tail item。原 assistant key 保留给稳定前缀，使 Compose 的可见锚点迁到不再长高的 item；tail 仍是视觉底部 index `0`，继续正常吐字。这个拆分共享同一个 `LazyListState`，不属于 overlay / active-zone / 第二消息主人；点击回到底部会清掉拆分，恢复完整 streaming item 并继续跟随
- `ChatScreen.kt` 当前已经按单主人口径收平：
  - `messages` 仍是 oldest -> newest 的唯一消息数据源；列表显示层通过 `chatListItems` 派生普通消息 item，以及 streaming 用户浏览态下的稳定前缀 / 活跃 tail item
  - `currentLastMessageContentBottomPx()` 的 fallback 已改回 reverse-list 口径，底部最新显示项按 index `0` 取值
  - `currentBottomOverflowPx()` 不再走 active-zone / history list 分支；现在按 reverse-list 单主人口径只计算“最新消息可见底边低于统一底部目标”的欠滚距离，内容底边已经高于目标时视作已到底，避免过滚误触发补滚
  - `isNearStreamingWorkline()` / `isAtStreamingWorklineStrict()` 已不再包含 Overlay 快捷分支
- 当前工作线视觉 gap 为 `80.dp`：也就是最新消息 / 小球 / streaming 底边会落在 composer 折叠外壳上方约 80dp 的位置；这个 gap 是产品预留给免责声明 / 极端说明 / 底部呼吸区的固定设计值，不从输入框内部文字或图片内容高度派生
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
  - pending finalize 当前不再主动调用 `alignVisibleChatListBottom(...)` 或完整 `scrollToBottom(false)` 做完成瞬间底部精修；吐完后只等 settled fresh bounds 到位再清 streaming 状态，避免长回复完成时窗口被重新锚到上方
  - 若用户浏览态已经触发 streaming prefix/tail 拆分，完成时会在浏览态暂时保留拆分，避免把正在看的稳定前缀重新合回巨型 item 造成重锚；回到底部或下一轮发送会清掉拆分
- `ChatScrollCoordinator.kt` 当前也已回到单主人口径：
  - `scrollToBottom(false)` 重新按 reverse-list 走 `scrollToItem(lastIndex)`，其中当前 `lastIndex` 在聊天页主调处按 `0` 传入
  - `alignVisibleChatListBottom(...)` 这条只服务 finalize 的主动底部精修 helper 已移除，避免与完成态渲染树切换打架
  - active-zone 时代专用的 `streamingBodyFollowEnabled` 开关已经从 coordinator 主链里移除
  - 旧正向 / overlay 时代的 streaming raw follow 链已移除：反向列表不再在 streaming 正文高度变化时额外调用 `followStreamingByDelta(...)` / `scrollBy(...)` 追滚，`streamBottomFollowActive` 空壳状态也已删除，避免和用户拖动、reverse-layout 自身底部锚定打架
  - streaming 期间用户触碰 / 拖动优先级高于程序滚动；一旦检测到用户接管，会先结束程序滚动标记并立即进入 `UserBrowsing`，不再让 `programmaticScroll` 分支吞掉用户手势。`scrollToBottom(...)` 这类程序对齐循环也会逐帧检查用户是否已接管，接管后立即停止。本轮不会自动恢复 `AutoFollow`；回到底部恢复跟随只走显式按钮 / 显式跳底链
  - 当前已决定输入框 / IME 与消息列表解耦：streaming 过程中键盘抬起只移动输入框自己，不再抬升消息工作线。输入框内部文字或图片内容高度仍不允许顶起聊天列表
- 回到底部按钮当前不再用消息 bounds / 工作线 `atBottom` 判断按钮资格。按钮资格统一为：消息非空、键盘不可见、生命周期未抑制，并且反向列表真实离底 `firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset > 0`，或 streaming 态用户触碰消息列表进入 `UserBrowsing`。开机 / 程序贴底 / bounds 初次上报不会点亮按钮；发送后 IME 过渡伪锁已删除，避免发过消息后按钮长期被压死；继续滚动会续亮，停止滚动后自动隐藏；点击按钮直接 `scrollToItem(0)` 回到反向列表真实底部并恢复对应滚动模式

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

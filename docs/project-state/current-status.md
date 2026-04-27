# 当前状态

最后更新：2026-04-27

## 项目概况

- 项目：农技千问
- 目标：面向农作物问题提供农业技术参考建议，不提供绝对诊断
- 当前仓库两条主线：Android 客户端 `app`，Go 后端 `server-go`

## 当前代码真相

- Android 端当前使用 Jetpack Compose 聊天界面，不再依赖 WebView 模板页面
- Android Auto Backup 当前已关闭；本地聊天窗口快照、流式草稿等都只作为本机运行时缓存，不允许被系统云备份在清数据 / 重装后恢复成旧 UI 状态。后端仍是业务真相来源
- 聊天消息运行时当前是**单一正向列表主人**：`ChatRecyclerViewHost.kt` 使用普通 `LazyColumn`，`messages` 仍按 oldest -> newest 存储并直接传给列表，视觉底部最新消息是 `lastIndex`
- 底部 composer 仍是页面底部的独立 UI 宿主，继续负责输入、IME、placeholder、发送禁用与收口视觉；**它不是消息运行时主人**
- composer 内部内容高度不作为聊天列表 reserve 真值。多行文字、未来图片预览、附件缩略图、图文混排等只能影响输入框内部布局 / 内部滚动 / composer 自身视觉高度；聊天列表 bottom padding 只允许吃折叠态 composer 外壳、safe area / IME / 底部外部几何、发送期锁定 reserve 和工作线 gap
- 当前工作线视觉 gap 为 `96.dp`，也就是小球、streaming 正文底边、开机历史态和完成态尾部都应落在 composer 折叠外壳上方约 96dp 的位置；工作线以下的空白必须露出来，用于免责声明 / 极端说明 / 底部呼吸区，不能把尾部文字压到输入框后面

## 聊天 UI 主链

- `ChatRecyclerViewHost.kt` 当前是正向 `LazyColumn`，没有 `reverseLayout`，没有 `items.asReversed()`
- `ChatRecyclerViewHost.kt` 使用 `verticalArrangement = Arrangement.Bottom`，确保短内容不满一屏时也贴在底部工作线附近，而不是停在顶部 padding
- `ChatScreen.kt` 当前直接使用 `messages` 作为列表数据源，不再通过 `chatListItems` 派生 streaming block item
- streaming 小分割 / block item 化已撤掉：`StreamingBlockChatListItem / StreamingTextBlock / streamingBrowseBlockSnapshot / activeStreamingBlockIndex / streaming_tail` 等符号在主链无残留
- mixed active-zone / overlay 运行时已退出主链：
  - `StreamingLocation`
  - `BottomActiveZoneSlice / resolveBottomActiveZoneSlice(...)`
  - `renderBottomActiveZone()`
  - active-zone 拖动接管 / Overlay 恢复门
  - `requestSendStartBottomSnap()`
- 当前禁止恢复 streaming 高度追滚补偿：`followStreamingByDelta(...)`、`scrollBy(...)`、`dispatchRawDelta(...)`、`streamBottomFollowActive` 不在主链运行
- 回到底部 / AutoFollow 使用最新消息 `lastIndex + FORWARD_LIST_BOTTOM_SCROLL_OFFSET`；该 offset 依赖 Compose 正向列表 positive `scrollOffset` 会把 item 继续向上推并在列表末端 clamp 的语义
- 正向列表传给 `LazyColumn` 的 bottom padding 会扣掉 `CHAT_MESSAGE_ITEM_VERTICAL_PADDING`，只补偿消息 item 外层 padding，不改变工作线本身
- 每次 reveal 提交前，AutoFollow 会先请求一次最新消息底部锚点；提交新的 `streamingMessageContent` 后，`ChatScreen.kt` 顶层通过 `SideEffect` 在同帧 apply changes 后、layout 前再次请求最新消息底部锚点，专门压正向列表下一行先从工作线下方冒头的一帧
- 大白话口径：以前是“新字 / 新行先长出来，列表下一拍才把它拉回工作线”，所以用户能看见下面冒一下；现在是“新字 / 新行要长出来的同一拍，先把列表底部钉在工作线”，画到屏幕时已经归位，所以冒头闪被压住
- 高频 reveal 底部锚点请求带 generation 守护，一帧后只允许最新请求关闭 `programmaticScroll`，避免旧取消任务把新程序滚动提前关掉后被误判成用户浏览
- 启动显示门不再把本地已有消息 / 首次欢迎空态硬等到 hydrate barrier 后才显示；有本地消息、已有 streaming item 或尚未开始过对话时，列表/欢迎壳可以先显示，减少开机白屏时间。历史消息贴底仍走正向列表最新消息 `lastIndex + FORWARD_LIST_BOTTOM_SCROLL_OFFSET` 主链
- 静态 / 开机 / 完成态到底必须同时满足文本底边命中 96dp 工作线以及 `chatListState.canScrollForward == false`，避免“文本看似贴线，但工作线以下空白还没完整露出、还能继续往上扒”的状态被误判为到底
- 首屏历史贴底恢复成多帧确认：等 `startupLayoutReady` 后最多连续重试 6 帧，只有文本底边命中 96dp 工作线且 `canScrollForward == false` 时才把 `initialBottomSnapDone` 记完成，避免一次 `scrollToBottom(false)` 尚未真正露出底部空白就关门
- 开机历史态 / 完成态在输入为空、无 focus、IME 收起、composer 非 settling、非发送锁的折叠稳定窗口中，列表 bottom padding 优先吃同拍实测的 composer 高度再加 96dp 工作线 gap，不再只靠启动估值 / 旧观察值；这是为了保证工作线以下空白完整露出来
- 用户进入 `UserBrowsing` 后，必须连续 5 帧稳定命中底部才允许恢复 `AutoFollow`，避免正向 pre-anchor 造成的瞬态到底把用户小幅上滑重新吸回
- 回到底部按钮仍保留 56dp 安全区：用户滑动过程中不显示，停止滑动后如果正向列表仍可向前滚动且最新消息底边离 96dp 工作线超过安全区，才短暂出现；点击后滚到最新消息 `lastIndex` 并恢复对应滚动模式

## 渲染与收口

- waiting 小球、streaming 正文、settled 完成态共用同一条 assistant 消息 item，不再切第二滚动主人
- `ChatStreamingRenderer.kt` 当前 active streaming 和 settled Markdown 都走 soft-wrap block renderer，并复用 inline Markdown cache 保留加粗 / 链接 / code；旧 committed 物理行预切 / TextMeasurer 渲染链已移除，避免 streaming -> settled 收口时换渲染模型导致行高 / 行宽微动
- active Markdown 仍实时吐字，但 `# ` / `- ` / `1. ` / `> ` 这类结构前缀必须等后面已有非空正文才结构化，避免只有符号的半成品先变标题 / 列表再重排
- streaming 期间不提前显示免责声明文字；如果内容已满足免责声明触发条件，只预留同等几何高度，settled 后才显示真实文案，避免尾部收口当拍突然增高
- 完成态收口继续保留两阶段 finalize：
  - `beginPendingStreamingFinalize(...)`
  - fresh bounds 到位后 `finalizeStreamingStop(...)`
  - 不回退成同拍直接切 settled
  - 不在 finalize 当拍恢复旧 bottom align 精修；若仍处于 AutoFollow，只在 fresh bounds 到位后请求一次正向底部锚点，避免长回复完成时窗口被重新锚到上方

## 当前调试焦点

- 这轮正向列表的目标，是牺牲反向列表那套“最新 item 天然视觉底部”的物理模型，换回用户上滑浏览时更稳定的正向滚动体感
- 当前最需要真机验证的是：
  1. 首屏进入有历史时是否稳定贴底
  2. 发送瞬间小球是否第一时间出现在工作线，历史文本是否不抖
  3. streaming 工作线下一行冒头闪已由用户初测确认被 `SideEffect` 同帧锚定压住，后续只需继续观察长回复 / 不同机型是否复现
  4. 用户上滑 / 下滑是否不抢手，想停哪里能停哪里
  5. 用户点击回到底部后是否恢复 AutoFollow
  6. finalize 收口是否稳定，尤其含免责声明答案是否不再尾部增高微跳
  7. 输入框上方和静态文本底部是否不再出现额外白块

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
- 反向列表单主人虽然压住了大部分底部冒头闪，但在最新 assistant item 内小幅上滑时仍会被动态长高锚点带一下，用户体感不可接受
- 当前仓库已按用户拍板切回“**单一运行时主人 + 正向列表**”路线；这不是恢复旧正向 overlay / placeholder / 追滚补偿，而是：
  - 正向 `LazyColumn`
  - 保留 send-start 保护窗口
  - 保留两阶段 finalize
  - 保留 unified soft-wrap renderer
  - 删除 mixed active-zone、小分割 itemization、streaming raw delta / scrollBy 追滚

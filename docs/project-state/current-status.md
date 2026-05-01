# 当前状态

最后更新：2026-05-01

## 项目概况

- 项目：农技千问
- 目标：面向农作物问题提供农业技术参考建议，不提供绝对诊断
- 当前仓库两条主线：Android 客户端 `app`，Go 后端 `server-go`

## 当前代码真相

- Android 端当前使用 Jetpack Compose 聊天界面，不再依赖 WebView 模板页面
- Android Auto Backup / Data Extraction 当前已关闭并显式排除：`allowBackup=false`，同时通过 `backup_rules.xml` / `data_extraction_rules.xml` 排除 cloud backup、device transfer、shared preferences、files、databases 和 external 数据。本地聊天窗口快照、流式草稿、`app_ids`、旧 UI metrics 等都只作为本机运行时缓存，不允许被系统云备份 / 设备迁移在清数据 / 重装后恢复成旧 UI 状态。后端仍是业务真相来源
- Go 后端当前同时保存两类资产：`session_ab.a_json` 仍是 A 层滑窗，写入新轮次后会裁剪到 Free / Plus 6 轮、Pro 9 轮；`b_summary` / `c_summary` 仍是摘要文本；成功完成的问答轮次会额外写入 `session_round_archive`，按 30 天滚动保留，用于 UI 历史恢复和后续批量抽取。当前尚未实现 C+ 的用户农业画像 / 用户农业档案字段
- `/api/session/snapshot` 当前继续返回 `a_json` / `a_rounds_full` 作为 A 层窗口，同时 `a_rounds_for_ui` 会优先返回 30 天内最近 30 轮 `session_round_archive`。前端本地仍会用 `LOCAL_RENDER_ROUND_LIMIT = 30` 裁 UI 窗口；换机 / 重装后只要用户身份能对上后端 `user_id`，UI 可拉到最近 30 轮业务聊天记录。当前项目还没有手机号 / 账号登录体系，实际主要依赖本机 `user_id`；若清数据后本机 `user_id` 丢失并生成新身份，则不会恢复旧记录
- 主对话每轮都会由后端注入当前时间和地点：`chat.go` 组装 `当前时间：yyyy-MM-dd HH:mm:ss（Asia/Shanghai）；用户地点：...；地点可信度：...`。历史轮次当前也会携带 `created_at / region / region_source / region_reliability`，进入模型上下文时以前缀“历史轮次时间：...”和“历史轮次地点：...”注入，让模型知道这轮距离当前大概多久、当时大概在哪里；前端暂不显示每条消息时间戳或地点条。当前 Android 还没有定位权限 / 地区选择主链，若请求里未带 `X-User-Region`，后端只能用 IP / 未知兜底，因此“地点详细点”需要后续单独做用户地区采集。天气 API 暂不接入，后续只在确有强需求和成本预算时再评估
- 当前产品策略倾向已记录为待决策：C 层后续可能升级为 `C+ = 长期摘要 + 用户农业画像 + 用户农业档案`，并评估改用 `Qwen3.5-Flash` 做 C+ 抽取；在代码落地前，当前真实实现仍是现有 `c_summary`
- 基础设施首版采购倾向已调整为 `SAE + RDS MySQL`：SAE 继续适合当前无运维团队阶段，数据库首版倾向使用阿里云 RDS MySQL 以降低成本和运维复杂度；PolarDB 暂作为后续高并发 / 更高规格升级选项，不再作为个人创业首版默认采购项
- 聊天消息运行时当前是**单一正向列表主人**：`ChatRecyclerViewHost.kt` 使用普通 `LazyColumn`，`messages` 仍按 oldest -> newest 存储并直接传给列表，视觉底部最新消息是 `lastIndex`
- 底部 composer 仍是页面底部的独立 UI 宿主，继续负责输入、IME、placeholder、发送禁用与收口视觉；**它不是消息运行时主人**
- `ChatScreen.kt` 当前把消息列表和 composer 作为页面 `Box` 内的兄弟层渲染：列表铺满消息区，composer 用 `align(Alignment.BottomCenter)` 固定在底部。composer 已从旧 `SubcomposeLayout` 测量链里拆出，键盘动画不再每帧拖着列表一起 remeasure；composer 自己继续吃 `imePadding()`，根容器不吃 IME padding，以保持“键盘只移动输入框，不抬升消息工作线”
- composer 内部内容高度不作为聊天列表 reserve 真值。多行文字、当前图片缩略图预览、未来附件缩略图、图文混排等只能影响输入框内部布局 / 内部滚动 / composer 自身视觉高度；聊天列表 bottom padding 只允许吃折叠态 composer 外壳、safe area / navigation bar、发送期锁定 reserve 和工作线 gap。IME 动画只移动 composer，不进入列表 reserve
- Android 端 `+` 入口当前已接入首版图片输入：点击后从底部弹出附件卡片，只开放“相机 / 照片”两项，图片先进入输入框缩略图预览，最多 4 张；发送时客户端读取本地 URI、压缩到单张 <=1MB 后上传，按用户选择顺序把远端 URL 写入 `SessionApi.StreamOptions.images`。用户消息会优先展示本地 `imageUris`，冷启动 / 远端 hydrate 后用 `imageUrls` 兜底显示；图片预览高度只影响 composer 内部，不进入列表 reserve / 96dp 工作线
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
- 首屏历史贴底恢复成多帧确认：等 `startupLayoutReady` 和底部固定 composer 宿主稳定实测高度都到位后最多连续重试 6 帧，只有文本底边命中 96dp 工作线且 `canScrollForward == false` 时才把 `initialBottomSnapDone` 记完成；贴底刚完成后如果 stable bottom reserve 又更新，且用户还没开始新对话 / 没触碰滚动，会再做一次非动画回底修正，避免一次 `scrollToBottom(false)` 尚未真正露出底部空白就关门
- 开机历史态 / 完成态在输入为空、无 focus、IME 收起、composer 非 settling、非发送锁的折叠稳定窗口中，列表 bottom padding 优先吃底部固定 composer 宿主的稳定实测高度再加 96dp 工作线 gap，不再只靠启动估值 / 旧观察值；这是为了保证工作线以下空白完整露出来，同时避免 IME 动画帧进入列表测量链
- 用户进入 `UserBrowsing` 后，如果用户明确滑回正向列表物理底部（`canScrollForward == false`、手指已抬起、列表已停止），会先请求一次正向底部锚点再恢复 `AutoFollow`；连续 2 帧工作线稳定命中只保留为兜底，避免 streaming 持续吐字打断容差导致手动回底后长时间不跟随
- 回到底部按钮仍保留 56dp 安全区：用户滑动过程中不显示，停止滑动后如果正向列表仍可向前滚动且最新消息底边离 96dp 工作线超过安全区，才短暂出现；点击后滚到最新消息 `lastIndex` 并恢复对应滚动模式
- 2026-04-28 用户真机反馈：当前正向列表滚动链整体“确实很稳”，小米 / MiMo 会诊后落地的 SideEffect 同帧锚定、物理底部恢复 AutoFollow、96dp 工作线贴底等主链规则继续作为当前稳定基线保留；后续不要再恢复反向列表、小分割、overlay 或 raw delta

## 渲染与收口

- waiting 小球、streaming 正文、settled 完成态共用同一条 assistant 消息 item，不再切第二滚动主人
- `ChatStreamingRenderer.kt` 当前 active streaming 和 settled Markdown 都走 soft-wrap block renderer，并复用 inline Markdown cache 保留加粗 / 链接 / code；旧 committed 物理行预切 / TextMeasurer 渲染链已移除，避免 streaming -> settled 收口时换渲染模型导致行高 / 行宽微动
- active Markdown 仍实时吐字，但 `# ` / `- ` / `1. ` / `> ` 这类结构前缀必须等后面已有非空正文才结构化，避免只有符号的半成品先变标题 / 列表再重排
- streaming reveal 当前对中文通常 1 到 2 个字一拍，英文 / 数字仍按词块吐出，减少“几个中文字一坨蹦出来”的体感；仍不恢复新字尾部灰色高亮动画，也不把吐字频率推到每个汉字都单独重组
- 标准 Markdown 表格当前不做真表格控件，renderer 会把表格行降级成普通项目行文本，保证模型偶发输出表格时至少可读、不撑乱聊天布局；代码块内的 `|` 不做表格降级。emoji / 表情若偶发输出，继续按普通文本由 Compose `Text` 承接
- streaming 期间不提前显示免责声明文字；如果内容已满足免责声明触发条件，只预留同等几何高度，settled 后才显示真实文案，避免尾部收口当拍突然增高
- 完成态收口继续保留两阶段 finalize：
  - `beginPendingStreamingFinalize(...)`
  - fresh bounds 到位后 `finalizeStreamingStop(...)`
  - 不回退成同拍直接切 settled
  - 不在 finalize 当拍恢复旧 bottom align 精修；若仍处于 AutoFollow，只在 fresh bounds 到位后请求一次正向底部锚点，避免长回复完成时窗口被重新锚到上方

## 当前调试焦点

- 这轮正向列表的目标，是牺牲反向列表那套“最新 item 天然视觉底部”的物理模型，换回用户上滑浏览时更稳定的正向滚动体感
- 输入框发送收口当前已取消旧高度锁：发送时不再用发送前的输入内容高度 / chrome 高度撑住 `composerSettlingMinHeightPx` / `composerSettlingChromeHeightPx`，优先让 composer 随输入清空直接回到空态高度；所有主动收键盘路径统一只调用 `focusManager.clearFocus(force = true)`，不再同帧额外调用 `keyboardController.hide()`，避免部分输入法在双触发下出现多一拍残影。已经没有显示入口的 composer collapse overlay prewarm snapshot 协程也已删除，后续若仍有明显迟钝，再单独评估 `WindowInsetsAnimationCompat`，不先动消息列表主链
- DEBUG 包新增 `ChatStartup` 诊断日志，用来区分清数据 / 重装后看到旧内容时到底来自本地 `chat_ui_cache`、本地 streaming draft，还是后端 `SessionApi.getSnapshot()` hydrate。日志不参与 release 行为，不改变 UI 逻辑；真机排查时可用 `adb logcat -s ChatStartup`
- 异常与生命周期兜底当前继续围绕“不中断主滚动链”小范围加固：日额度耗尽后，当前本地会话当天会把发送键置灰但保留点击提示；断网连续点同一段输入时复用已有失败用户消息，不再刷出多条相同失败消息；后端 streaming 进程被杀后若远端 snapshot 追不回答案，会在用户消息下方补一个 `回复未完成 · 点击重试` 的 assistant 失败入口；切后台 / 锁屏时会收起输入焦点并清掉消息 / 输入选择菜单，避免回来还挂着复制黑卡片；`SessionApi` 的当前 SSE call 会等响应读循环退出后再清空引用，保证 reset / cancel 能尽量取消正在读的远端流
- 当前最需要真机验证的是：
  1. 首屏进入有历史时是否稳定贴底
  2. 发送瞬间小球是否第一时间出现在工作线，历史文本是否不抖
  3. streaming 工作线下一行冒头闪已由用户初测确认被 `SideEffect` 同帧锚定压住，后续只需继续观察长回复 / 不同机型是否复现
  4. 用户上滑 / 下滑是否不抢手，想停哪里能停哪里
  5. 用户点击回到底部、或手动下滑到物理底部后是否恢复 AutoFollow
  6. finalize 收口是否稳定，尤其含免责声明答案是否不再尾部增高微跳
  7. 输入框上方和静态文本底部是否不再出现额外白块
  8. 冷启动首次点输入框、键盘弹起 / 回缩是否比旧 `SubcomposeLayout` 版本更利索；如果仍有残影，下一步只评估 `WindowInsetsAnimationCompat` 或输入法平台差异，不先动消息列表主链

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

# 近期重要变更

说明：本文件默认只保留最近 20 条重要变更；更早内容以 git 历史和 ADR 为准。

## 2026-04-17

- 调整 completed 宿主收口时序：`ChatScreen.kt` 当前改成“两阶段 finalize”。第一阶段先把最终内容写入 completed 消息，但暂不切 `isStreaming`，并按最终消息 `id` 清掉旧 streaming bounds；第二阶段等同一条消息的 fresh completed bounds 真实测量出来后，再原子切掉 streaming 状态，并只在仍离底时按需单发 `requestScrollToItem(0)`，专门收“生成结束偶发上跳、底部露白”和“后台直接完结再回来露白”的竞态
- 当前仓库真相已同步收口：根 `AGENTS.md` 与 `docs/project-state/current-status.md` 已明确完成态归位仍不是旧 `pendingFinalBottomSnap` 状态机，也不是任何多帧 `scrollBy` 补偿链；旧 final snap 口径继续废弃

## 2026-04-16

- 发送链继续做减法并清旧方案：`ChatScreen.kt` 已撤掉一度试验过的 `withTimeoutOrNull + snapshotFlow` 延后收口链，重新回到“输入框瞬间清空回缩、小球同拍出现”的单一发送事务，不再让即时收口和延后收口两套方案并存
- 发送窗口的列表几何已收紧到单一真相：新增 `isComposerSettling` / `shouldUseRealtimeComposerGeometry`，发送与输入区收口窗口里，`streamingWorklineBottomPx` 和 `bottomContentReservedHeightPx` 都会回退到稳定 bottom bar / overlay 高度，不再继续吃实时 `composerTop` 把消息区一起带着抖
- streaming 块级宿主已改为 unified host：`ChatStreamingRenderer.kt` 现在先把 completed / active blocks 拍平成同一个 `unifiedModels` 列表，再由同一个外壳承接 spacing / divider，active -> committed flush 时不再跨 sibling subtree 搬家
- unified streaming block 的外壳 key 已收口：append-only 场景下改用稳定的 block index，删除 `hashCode()` 参与 key 生成，减少流式阶段内容变化触发的连续 remount
- Android 本轮验证已完成 `./gradlew.bat :app:compileDebugKotlin` 与 `./gradlew.bat :app:installDebug`；App 可在当前连接设备上启动，但 adb 自动化只做到有限冒烟，尚未替代人工体感回归
- 记忆系统继续做减法和收口：`current-status.md` 新增“当前调试焦点”，把聊天 UI 剩余热点明确收敛到发送事务时序和 streaming 块级宿主交接；同时新增 `docs/runbooks/chat-ui-regression.md` 作为统一复现/验收入口，方便新窗口和外部会诊前自查
- `open-risks.md` 与 `pending-decisions.md` 已同步收平：不再继续写“没有程序化校验”这一过期口径，改为承认 `scripts/check_project_memory.py` 与 CI 已接入，同时明确当前校验粒度仍偏粗
- 主规则补充“外部会诊现实约束”：Gemini / Claude 等外部模型默认看不到本地仓库与文件链接，只能依赖用户转发的文字、代码片段、截图和日志；因此后续会诊稿必须自包含，不能只报文件名让对方自己猜
- 主规则新增“会诊稿规则”：以后会诊前必须先完成本地代码排查；发给 Gemini / Claude 的会诊稿默认要落到具体文件、函数、状态、必要代码片段、已排除项和限制条件，禁止只发抽象症状或让外部模型假设仓库里不存在的接口
- 输入框与文本区继续解耦：`ChatScreen.kt` 现在只在 streaming 进行时才让工作线和 `recyclerBottomPaddingPx` 参考实时 `composerTop`；普通 idle 状态下，不论停在底部还是历史区，列表都改回稳定 bottom bar / overlay 高度，避免输入框弹起时把整段消息区一起往上拽

## 2026-04-15

- 发送起步继续做减法：`ChatScreen.kt` 已删掉发送链里的 `withFrameNanos`；插入用户消息和 assistant placeholder 后，同一发送事务内立即 `requestScrollToItem(0)`，减少新消息先悬空一拍、下一帧再砸回工作线的可见下坠
- 发送期几何继续做减法：`ChatScreen.kt` 已不再让 `sendStartBottomPaddingLockActive` 或“稳定单行几何”参与工作线 / `recyclerBottomPaddingPx` 的运行时计算；发送后底部保留高度优先跟随实时 `composerTop`，只有测量暂不可用或 overlay 接管时才回退到现有 bottom bar / overlay 高度
- Markdown block 间距已从父级 `Column(spacedBy(...))` 挪到各 block 自身的 top padding；`ChatStreamingRenderer` 的 streaming / settled 正文现在不再依赖父级统一分缝，减少段落或列表新块出现时把已有内容整体向下推一拍
- 调整 streaming 宿主生长方向：`ChatStreamingRenderer` 的 streaming 外层宿主已从 `Alignment.TopStart` 改为 `Alignment.BottomStart`，让正文围绕同一物理底边向上长，优先压制流式换行时“先下掉再回弹”的体感
- 修复一次中间坏提交的 CI：`ChatScreen.kt` 曾补上 `withFrameNanos` import，恢复过“发送插入后下一帧请求 `requestScrollToItem(0)`”这条路径的可编译状态；该过渡口径现已再次收口，不再作为当前运行时真相
- 撤回“发送死区”误判：发送事件重新恢复为对新插入 assistant placeholder 无条件回到底部；当前已进一步收口为同一发送事务内立即 `requestScrollToItem(0)`，用来覆盖 keyed `LazyColumn` 对旧可见项的默认位置保护，避免 waiting 小球被挤出视口底部
- 删除 `pendingFinalBottomSnap` 完成态补滚链：streaming finish / interrupted / 后台同步收口都不再额外置位“再补一拍到底”，完成态直接交给反向列表天然锚定，方便暴露真实剩余几何问题
- streaming 渲染继续清旧链：`ChatStreamingRenderer` 的 waiting 小球与 streaming 首块已收敛到同一个内容宿主里切换，不再保留“waiting 一套 / 首字后一套”的 streaming 分支，减少首字上屏时的物理宿主切换
- 删除 `scrollToBottom(false)` 里仍在并行的 `alignChatListBottom()` 8 帧 `scrollBy` 补偿链；回到底部和完成态收口当前只保留 `scrollToItem(0)` / `animateScrollToItem(0)` 这条反向列表主链
- 本地 fake streaming 结束前已不再等待 `currentStreamingOverflowDelta()` 这类旧 overflow 指标收平；正文 reveal 完成后直接 finish，避免尾帧继续被旧收口口径拖出轻微回弹
- 发送起步再次收敛到单一真相：删除“回底死区”分叉，发送事件在插入用户消息和 assistant placeholder 后统一直接 `requestScrollToItem(0)`，避免反向列表继续按旧 key 维持上一条消息导致 waiting 小球时灵时不灵地掉出工作线
- 发送期几何锁窗口继续收紧：`sendStartBottomPaddingLockActive` 不再只看 `sendUiSettling`，现在还会覆盖 `composerSettlingMinHeightPx / composerSettlingChromeHeightPx` 的真实收口期，减少 waiting / 早期 streaming 阶段工作线提前切回实时几何导致的小球下掉和轻微上下弹
- 主规则新增“会诊优先级”：以后遇到 Android Compose UI / 滚动链 / 渲染时序问题，优先只建议用户找 Gemini；遇到 Go 后端 / 架构边界 / 规则归纳问题，优先建议找 Claude，避免再默认双向并行会诊
- 远端历史 hydrate 路径继续收口：`replaceMessages(...)` 已从 `messages.clear() + addAll()` 改为按消息 `id` 原地 `set/add/move/remove` 的增量更新，减少冷启动/恢复期的整表重排和滚动缓存丢失
- 删除反向底座下仍在并行的旧 streaming 手动补偿链：`snapStreamingToWorkline`、`performStreamingFollowStep`、`resolveStreamingFollowStepPx`、`currentStreamingAlignDeltaPx` 已退出运行时主链；`ChatScrollCoordinator` 现在只保留 `Idle / AutoFollow / UserBrowsing` 的控制权切换，不再在 streaming 期间主动 `scrollBy` 追工作线
- 聊天底座已翻为 `LazyColumn(reverseLayout = true)`：运行时消息状态仍保持正序，但传给列表的显示顺序改为 `asReversed()`，让最新消息天然贴近底部工作线，减少正向底座底部插入时的整体回弹
- 删除所有只服务正向发送起步的 offset / 视口快照链：`pendingStartAnchorScrollOffsetPx`、`sendStartViewportHeightPx`、`sendStartWorklineBottomPx` 已退出运行时主链；发送时改为直接 `requestScrollToItem(0)`
- 首次进入聊天页的贴底逻辑已改为 `scrollToItem(0)`；不再依赖 footer + 两次到底补推
- streaming 场景下“回到底部”按钮已统一走 `scrollToBottom(false)`，不再额外走正向列表的 `snapStreamingToWorkline` 补差链
- 发送起步 offset 继续收敛：删掉 `SideEffect -> latestPendingStartAnchorScrollOffsetPx` 的缓存桥，发送事件现在直接读取当前作用域里的 `pendingStartAnchorScrollOffsetPx` 真值，减少发送瞬间先用旧 offset 排版再被新 offset 拉回的上下弹动

## 2026-04-14
- 建立仓库内长期记忆骨架：新增 `docs/project-state`、`docs/adr`、`docs/runbooks`
- 根 `AGENTS.md` 增加“项目记忆机制”，要求 Codex 默认读取并自动维护状态、风险、待决策、变更记录和 runbook
- 新增 `app/AGENTS.md` 与 `server-go/AGENTS.md`，让新窗口进入子系统时自动获取局部口径
- 新增 ADR-0001，明确项目长期交接依赖“仓库内结构化记忆”，而不是依赖聊天历史或模型自带记忆
- Compose 聊天底座继续收敛：发送起步锚点改为按 assistant waiting 宿主真实可见底边对齐工作线，删除旧的 waiting 高度估算链
- 滚动状态机收紧用户浏览判定：仅真实手指拖动才进入 `UserBrowsing`，程序化滚动尾帧不再误杀 `AutoFollow`
- 新增 `scripts/check_project_memory.py` 并接入 Android CI：关键真相文件变更但未同步更新仓库记忆时直接报警
- `UserBrowsing -> AutoFollow` 回接条件继续收紧：只有生成行真实回到工作线命中带后才重新接回，避免用户上滑查看历史时被主链往下拽
- 修复项目记忆检查在 GitHub Actions 中的误报：`actions/checkout` 改为 `fetch-depth: 0`，避免浅克隆下 `base..head` revision range 无法解析
- 发送起步锚点继续收紧：waiting 阶段改为量“小球所在首行宿主底边”而不是内层呼吸球本体 bottom，减少 waiting/首字 streaming 切换时的锚点跳变
- 本地 fake streaming 的生命周期收口改为同步执行：`ON_PAUSE / ON_STOP` 直接在主线程完成 completed 消息落盘与 draft 清理，减少“切后台再回来只剩半截文本且列表卡住”的竞态窗口
- `UserBrowsing` 回接条件继续收紧：不再按泛底部容差直接接回，而是要求生成行真实回到工作线附近，减少用户上滑时被 AutoFollow 过早往下拽
- 发送起步目标线改为优先使用单行收口后的稳定 composer 保留高度，减少长文本发送时“小球先顶高再掉回去”的锚点漂移
- 输入框已收口成单行后，列表底部保留高度改为继续优先使用稳定单行高度，不再马上切回实时 `composerTop` 测量，减少锚点刚对齐后又被底部几何二次改写
- 发送窗口内的几何参考系进一步冻结：`sendStartBottomPaddingLockActive` 覆盖期间，工作线和底部保留高度都强制锁定到稳定单行高度，减少发送瞬间闪烁和锚点漂移
- waiting 宿主壳子的额外高度已删除，waiting 与 streaming 首行改为共享同一物理基线，减少首字上屏时历史区轻微下掉
- 发送起步保护期的释放条件已收紧：只有正文真实越过工作线、出现正向 overflow 后才交给 `AutoFollow`，减少“刚碰线就切主”带来的插入瞬时抖动
- 发送起步定位链已从“两拍式反馈修正”改为“单拍前馈定位”：删除 waiting 宿主测量回调与 `scrollBy` 精修，只保留基于固定工作线、列表 top padding 和首行宿主固定高度计算出的单次 `scrollToItem(index, offset)`
- 发送起步继续收口到同步定位：滚动指令已从 `ChatRecyclerViewHost.kt` 的 UI `SideEffect` 移回发送事件源；现在由 `ChatScreen.kt` 在插入用户消息和 assistant placeholder 的同一段事件代码里直接调用 `requestScrollToItem(index, offset)`，避免新旧起步方案并行
- 发送期几何继续收口：`ChatScreen.kt` 新增 `sendStartViewportHeightPx` 视口快照，发送窗口内的 workline 与底部保留高度都优先使用这份快照，减少输入框收口时外层视口高度抖动把新消息再弹一下

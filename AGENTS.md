# 农技千问仓库主规则

本文件是本仓库唯一主规则文档。以后任何 GPT、Codex、子窗口、接手窗口，在开始改代码前都先看这里。

当前仓库实际只有两条主线：
- Android 客户端：`app`
- Go 后端：`server-go`

历史调参、旧链路、旧口径都留在 git 历史里，不再继续堆在本文。

## 1. 文档规则

- 规则文档只保留这一份：[AGENTS.md](D:/wuhao/AGENTS.md)
- `app/AGENTS.md`、`server-go/AGENTS.md` 允许存在，但只作为各自目录树内的局部执行补充，不承担仓库主规则职责，口径必须服从根 [AGENTS.md](D:/wuhao/AGENTS.md)
- [docs/chat-ui-dynamic-interaction-logic.md](D:/wuhao/docs/chat-ui-dynamic-interaction-logic.md)、[docs/chat-ui-clean-state-checklist.md](D:/wuhao/docs/chat-ui-clean-state-checklist.md)、[docs/backend-boundaries.md](D:/wuhao/docs/backend-boundaries.md) 只作参考，不再承担主规则职责
- [docs/project-state/current-status.md](D:/wuhao/docs/project-state/current-status.md)、[docs/project-state/open-risks.md](D:/wuhao/docs/project-state/open-risks.md)、[docs/project-state/pending-decisions.md](D:/wuhao/docs/project-state/pending-decisions.md)、[docs/project-state/recent-changes.md](D:/wuhao/docs/project-state/recent-changes.md) 是项目交接记忆，不是主规则；后续任何 Codex 都应主动读取并自动维护
- [docs/adr](D:/wuhao/docs/adr) 用于沉淀“为什么这么定”；[docs/runbooks](D:/wuhao/docs/runbooks) 用于沉淀运维和排障入口；二者都必须和当前代码保持一致
- 参考文档允许保留历史分析，但必须明确标注“历史归档 / 仅供参考”，不允许继续冒充 active 真相
- 规则变更、实现边界变化、唯一真相变化，必须同次同步更新本文件
- 如果本文件与当前代码不一致，优先修本文，不允许放着过期规则不管

### 1.1 项目记忆机制

目标：
- 让新开的 Codex 窗口在不依赖用户重复口述的前提下，快速接上当前项目真相
- 让“当前状态 / 风险 / 待决策 / 运维入口 / 关键历史决策”都固化在仓库里，而不是散落在聊天记录里

默认读取顺序：
1. 根 [AGENTS.md](D:/wuhao/AGENTS.md)
2. 当前工作目录命中的局部 `AGENTS.md`
3. [docs/project-state/current-status.md](D:/wuhao/docs/project-state/current-status.md)
4. [docs/project-state/open-risks.md](D:/wuhao/docs/project-state/open-risks.md)
5. [docs/project-state/pending-decisions.md](D:/wuhao/docs/project-state/pending-decisions.md)
6. [docs/project-state/recent-changes.md](D:/wuhao/docs/project-state/recent-changes.md)
7. 当前任务相关的 ADR / runbook / 参考文档

自动维护规则：
- 以上项目记忆文件默认由 Codex 自己维护，不要求用户手工更新
- 每次任务完成后，若涉及当前状态、风险、待决策、运维入口、方案取舍、历史方案淘汰，必须同次同步更新对应文档
- 已废弃方案不能与现方案并列长期共存；若保留历史说明，必须明确标记“已废弃 / 仅供参考 / 被什么替代”
- `current-status.md` 只保留当前真相，不堆历史流水账
- `open-risks.md` 只保留未关闭风险；已解决项应移出或转入变更记录
- `pending-decisions.md` 只保留仍待拍板事项；已定事项转 ADR 或变更记录
- `recent-changes.md` 记录最近一段时间的重要变更，默认保留最新 20 条；更早内容以 git 历史和 ADR 为准
- 运维动作若新增脚本、命令、平台入口或回滚方法，必须同步更新对应 runbook
- 若发现记忆文件失真、过期、互相矛盾，修正文档本身属于本次任务的一部分，不能留到以后

## 2. 产品与系统真相

项目名称：农技千问

产品定位：
- 农业 AI 问诊系统
- 用户通过文字、图片、图文混合咨询农作物问题
- AI 只提供农业技术参考建议，不提供绝对诊断

系统边界：
- 前端只负责 UI、输入、展示、交互
- 后端是唯一真相来源
- 以下业务逻辑必须由后端控制：用户鉴权、会员等级、调用次数、上下文组装、模型调用、成本统计

部署与基础设施：
- 部署：阿里云 SAE
- 数据库：PolarDB（MySQL 兼容）
- 可选组件：Redis、OSS、SLS

## 3. 模型、上下文与会员

模型：
- 主模型：Qwen3.5-Plus，用于农业问诊分析、图片理解、推理判断
- 摘要模型：Qwen-Flash，用于 B 层摘要、C 层摘要

上下文结构：
- A 层历史滑窗：Free / Plus 6 轮，Pro 9 轮
- B 层中期摘要：约 550 tokens
- C 层长期摘要：约 300 tokens，每 25 轮更新一次
- 锚点信息：约 1000 tokens，每轮必注入

图片规则：
- 单轮最多 4 张
- 压缩：最长边 <= 1024px，单张 <= 1MB
- 估算：1 张图约 1000 tokens
- 图片上下文只保留当前轮和上一轮；更早图片只保留文字结论与摘要

联网搜索：
- 默认能联网就联网，优先官方、正式、权威资料
- 仅在强时效、事实核对、外部数据查询等场景触发
- 不允许凭印象硬改

会员与计费：
- Free：6 次 / 天
- Plus：19.9 元 / 月，25 次 / 天
- Pro：29.9 元 / 月，40 次 / 天
- 加油包：6 元 / 100 次，仅 Plus / Pro 可买
- 同一时刻只允许 1 个 active 加油包，用完再续
- 续费订单金额以后端 `orders.amount` 记账为准：Plus 19.9，Pro 29.9
- 当前代码里的超额消耗顺序：先每日额度，再升级补偿额度，再加油包；如果后续业务口径调整，再单独同步改代码和本文

## 4. 提示词与后端真源

当前真源文件：
- 主对话锚点：[server-go/assets/system_anchor.txt](D:/wuhao/server-go/assets/system_anchor.txt)
- B 层摘要提示词：[server-go/assets/b_extraction_prompt.txt](D:/wuhao/server-go/assets/b_extraction_prompt.txt)
- C 层摘要提示词：[server-go/assets/c_extraction_prompt.txt](D:/wuhao/server-go/assets/c_extraction_prompt.txt)

规则：
- 三个文件职责不同，不允许合并
- 锚点规则改动时，必须同次同步更新真源文件和 [AGENTS.md](D:/wuhao/AGENTS.md)
- 主对话锚点缺失或为空，属于主链配置问题，应 fail-fast
- B/C 层失败不拖垮主对话，但必须保留 `pending_retry`，后续继续补账

当前锚点执行重点：
- 当前轮输入优先，历史 / 摘要 / 联网只作参考
- 信息不足时列 2 到 3 种可能性，并追问 1 到 2 个关键问题
- 联网搜索同一轮最多一次
- 证件 / 登记 / 备案 / 审定类不做真伪裁决，只给权威平台查询方法

## 5. 开发与交付规则

改代码原则：
- 先读代码，再下刀
- 小范围改动，优先复用现有逻辑
- 不假设不存在的接口、文件或路径
- 能查官方资料就先查

风险控制：
- 一次只收一个明确问题
- 改完必须复盘影响范围，避免改了这个坏了那个
- 临时日志、debug 面板只服务当前问题，用完尽快删除
- 同一个问题如果已经连续两轮修改仍未解决，第三轮开始前必须先会诊，再继续改

会诊优先级：
- 不要默认同时找多个外部模型并行会诊；先选一个最匹配的对象，避免用户重复复制和多头口径互相干扰
- 当前用户已明确不再使用 Gemini；本项目如需外部会诊，默认优先整理成发给 Claude 的短稿。除非用户之后明确指定，否则不要再建议 Gemini
- Android Compose UI、滚动链、动画、渲染时序、布局抖动、输入区/工作线几何问题：当前也默认优先建议用户找 Claude 会诊，但前提仍是 Codex 先本地锁定到具体代码点，再外发短稿
- 后端 Go、业务边界、接口职责、数据流、架构复盘、文档归纳：继续优先建议用户找 Claude 会诊
- OpenAI / Codex / API / 官方产品能力与限制：优先查官方文档或官方资料，不把 Claude 等外部模型当真源
- Codex 的职责始终是：结合仓库当前代码落地修改、检查旧方案是否并行、编译验证、更新文档并提交推送
- 如果问题类型已经明显匹配上面某一类，后续窗口应直接点名建议用户找对应对象，不要再让用户自己猜“该问谁”

会诊稿规则：
- 会诊前先完成本地代码排查；先确认当前唯一主链、已废弃旧链、仍在运行时生效的真实代码位置，再决定问什么
- 发给外部模型的会诊稿必须锚定当前仓库代码，至少写清：相关文件路径、关键函数/状态名、必要时的代码片段或行号、当前现象、已排除项、怀疑点、限制条件
- 会诊问题必须尽量收敛到具体实现，不要只发“为什么会抖”“怎么优化”这类抽象描述；要明确让对方基于当前代码回答，不要假设仓库里不存在的接口、状态或组件
- 若当前问题已经定位到某几个可疑点，会诊稿应直接要求对方围绕这些代码点给最小可落地方案，并明确禁止跑题到已被排除的方向
- 收到会诊建议后，Codex 必须先对照当前代码核验：有没有假设不存在的字段、有没有和现有主链冲突、有没有把旧方案重新带回来；核验不过不直接下刀
- 当前默认现实前提：外部会诊对象（如 Claude）通常无法直接读取本地仓库、文件链接或真实运行环境；它们看到的只有用户手动转发过去的文字、截图、代码片段与日志
- 因此外发会诊稿必须自包含：不能只写“看 ChatScreen.kt”“看仓库最新代码”，而要把对方作答所必需的关键代码片段、状态名、调用顺序、限制条件直接贴进消息里；若缺少这些上下文，会诊结果默认不可信
- 如果外部会诊通过手机聊天软件进行，优先压缩成“问题说明 + 关键代码片段 + 明确追问”的短稿，避免对方因为上下文不全继续脑补仓库结构

交付要求：
- Android 改动后编译：`./gradlew.bat :app:compileDebugKotlin`
- Android 改动默认只做编译验证，不主动执行 `:app:installDebug`；真机安装与回归默认由用户自行完成，只有用户明确要求 Codex 装机时才执行安装
- Android 若改动聊天页关键 UI 预热路径（`ChatScreen.kt`、`ChatStreamingRenderer.kt`、`ChatComposerPanel.kt`、`ChatComposerCoordinator.kt`、`ChatRecyclerViewHost.kt`，或 Selection / Markdown / LazyColumn / 输入框 IME 主链），必须同步检查 [docs/runbooks/android-baseline-profile.md](D:/wuhao/docs/runbooks/android-baseline-profile.md)：确认是否需要更新 `:baselineprofile` 的关键路径脚本、是否需要重新生成 Baseline Profile。小样式 / 文案通常不需要，但发版前若关键路径变更，应重跑 `:app:generateReleaseBaselineProfile`
- Go 后端改动后编译：`cd server-go && go build ./...`
- 每次改动后都要检查影响面
- 若改动影响当前真相、风险、待决策、运维口径或方案取舍，必须同步更新 `docs/project-state`、`docs/adr`、`docs/runbooks`
- 每次改动后都要提交本地 git，并推送到 `origin/master`

## 6. Chat UI 总原则

总原则：
- 聊天 UI 必须由当前真实代码和当前真实状态直接决定
- 不允许靠本地历史、本地草稿、本地视口假状态“看起来正常”

Clean-State 定义：
- 清除 app 数据后首次启动
- 无本地聊天记录、无本地底部视口、无本地流式草稿、无旧 user_id 状态

Clean-State 必做回归的范围：
- 聊天列表布局
- 流式生成渲染
- 发送后上抬 / 小球锚点
- 自动滚动 / 手动打断 / 回到底部按钮
- 底部输入区
- 复制链
- 免责声明
- 冷启动 / 重进 app / 历史恢复

聊天框分层边界：
- [ChatScrollCoordinator.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt)：滚动状态机、发送后锚点、回到底部按钮
- [ChatStreamingRenderer.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatStreamingRenderer.kt)：waiting / streaming / settled 渲染
- [ChatComposerCoordinator.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatComposerCoordinator.kt)：输入框动态、IME、发送收口
- [ChatComposerPanel.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatComposerPanel.kt)：底部输入区 UI 宿主
- [ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt)：页面组装、测量值采集、状态接线
- [ChatRecyclerViewHost.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatRecyclerViewHost.kt)：纯 Compose `LazyColumn` 底座与 bottom padding 宿主；文件名只是历史命名残留，运行时已不是 `RecyclerView`

旧 `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout / frozenBottom / retainedBottomGap` 等旧滚动术语全部视为历史归档，不再执行。

## 7. 当前 Compose 列表滚动链唯一真相

### 7.1 总口径

- 聊天消息运行时当前只允许有一个主人：`LazyColumn(reverseLayout = true)`
- `ChatRecyclerViewHost.kt` 当前使用：
  - `LazyColumn(reverseLayout = true)`
  - `items.asReversed()`
  - 原因是仓库里的 `messages` 仍按“旧在前、新在后”存储，反向列表需要用反转后的显示序列把最新消息放到视觉底部
- 底部 composer 仍是页面底部的独立 UI 宿主，负责输入、IME、placeholder、发送禁用与收口视觉；**它不是消息运行时主人**
- composer 内部内容高度不属于聊天列表 bottom reserve。长文本、未来图片预览、附件缩略图、图文混排等只能影响输入框内部布局 / 内部滚动 / composer 自身视觉高度，不能直接把历史消息区顶上去；聊天列表 reserve 只允许吃折叠态 composer 外壳、safe area / IME / 底部外部几何、发送期锁定 reserve、工作线 gap。若未来产品明确要“附件栏顶起聊天区”，必须作为单独 external tray 重新设计和命名，不能复用输入内容高度偷渡进滚动链
- waiting 小球、streaming 正文、settled 完成态当前继续共用同一条 assistant item 渲染主线；不再允许在运行时从列表摘出去交给第二个消息宿主
- mixed active-zone / overlay 运行时当前已退出主链：
  - `StreamingLocation`
  - `BottomActiveZoneSlice / resolveBottomActiveZoneSlice(...)`
  - `renderBottomActiveZone()`
  - active-zone 拖动接管 / Overlay 恢复门
  - `requestSendStartBottomSnap()` 那条“只滚 historyMessages”的发送起步链
- 工作线和静态贴底线继续共用同一个物理锚点；当前口径是“列表里最新消息的可见底边 + 共享 measure 宿主同拍产出的 composer reserve”
- 底部不应再出现额外可见空白；历史区浏览时，输入框弹起 / 收起也不应再带着消息区整体联动

### 7.2 五环节铁律

1. 发送起步
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt)
- 做法：在同一发送事务里同步完成输入框收口、`upsertUserMessage(...)`、assistant placeholder、`prepareScrollRuntimeForStreamingStart(...)`、发送期 reserve 锁，以及按 reverse-list 口径同步 `requestScrollToItem(0)`
- 当前目标：发送起步仍保持单一列表主人；不再靠 active zone / overlay 切主人来承接当前轮消息

2. AutoFollow / 回到底部
- 主人：[ChatScrollCoordinator.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt)
- 作用：继续只维护一套 `Idle / AutoFollow / UserBrowsing` 滚动状态机；`scrollToBottom(false)`、jump button 和 follow 都只围绕反向列表主链派生，不允许并存第二条补滚链

3. 发送期几何稳定
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt) 的“消息列表 + composer 共享 measure 宿主”
- 作用：发送当拍允许即时清空输入框并立即插入消息，但列表实际吃到的底部 reserve 仍必须和 composer 在同一轮 measure 里产出；旧 `composerTopInViewportPx` 只保留为辅助几何 / fallback，不再重新升回唯一真相

4. 完成态收口
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt) 的两阶段 finalize
- 作用：streaming -> settled 继续在同一个列表 item 内完成；当前仍保留 `beginPendingStreamingFinalize(...) -> fresh bounds -> finalizeStreamingStop(...)` 这条两阶段 finalize，不允许回退成“同拍直接切 settled”的简化版

5. 用户浏览与首次进入
- 主人：用户手指 / [ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt)
- 作用：用户进入 `UserBrowsing` 后立即让权，但仍在同一个列表主人里浏览，不再发生 overlay/list 切管；冷启动且已有历史时，继续走 `scrollToBottom(false)` 贴到底部；从后台切回时不默认自动贴底

铁律：
- 同一时刻只能有一个消息主人控制滚动与渲染
- 新增任何 scroll 调用前，必须说明它属于哪一个环节
- 回到底部按钮只能从单一列表主链派生，不能另挂第二套“overlay 恢复 / active zone 回收”链

### 7.3 当前实现细则

- `ChatRecyclerViewHost.kt` 当前已恢复反向列表底座；如果后续再调整顺序，必须连同当前 `messages` 的真实存储顺序一起检查，不能只改 `reverseLayout` 或只改 `items.asReversed()`
- `ChatScreen.kt` 当前已回到：
  - `chatListMessages = messages`
  - `currentLastMessageContentBottomPx()` 的 fallback 按 reverse-list 使用可见项 index `0`
  - `currentBottomOverflowPx()` 按 reverse-list 单主人口径只计算“最新消息可见底边低于统一底部目标”的欠滚距离；如果内容底边已经高于目标，视作已到底，避免过滚误触发补滚
- 发送起步当前保留的旧保护只有两样：
  - `lockedConversationBottomPaddingPx / sendStartBottomPaddingLockActive`
  - `sendStartAnchorActive`
- 这些保护当前只服务“发送起步短窗口”的 reserve / 放权稳定，**不是**旧 active-zone 时代那种运行时切管门
- `sendStartBottomPaddingLockActive` 期间，列表 bottom padding 与 streaming 工作线必须使用同一份锁定几何：`streamingWorklineBottomPx = lockedMessageViewportHeightPx - lockedConversationBottomPaddingPx`。不允许列表吃 locked padding、工作线却继续吃当前长文本输入框或实时 composer 高度，否则小球锚点会被长输入框顶高
- `observedCollapsedBottomReservePx`、`bottomBarHeightPx`、`latestConversationBottomPaddingPx` 等列表 reserve 相关值，不能从输入框当前内容高度中学习。输入框多行文字、图片预览、附件缩略图导致的 composer 内容扩展，只能停留在 composer 内部；只有键盘 / navigation bar / composer 外壳这类外部几何变化能进入聊天列表 bottom padding
- 当前已决定输入框 / IME 与消息列表解耦：streaming 过程中键盘抬起只移动输入框自己，不再抬升消息工作线；用户只要在生成中触碰消息列表，就立即进入 `UserBrowsing`，本轮不再自动恢复 `AutoFollow`，回到底部恢复跟随后续单独走显式按钮 / 显式跳底链
- streaming 过程中用户进入 `UserBrowsing` 后，如果仍停留在最新 assistant item 内，`ChatScreen.kt` 会临时冻结该 active streaming item 的测量高度，避免 `LazyColumn(reverseLayout = true)` 因 index `0` item 继续长高而把用户轻微上滑带回工作线；这只是用户浏览态的测量高度保护，不是旧 `scrollBy(...)` / `dispatchRawDelta(...)` 高度追滚
- `commitSendMessage()` 当前的真实顺序是：
  1. 输入框收口
  2. `upsertUserMessage(...)`
  3. 插入 assistant placeholder
  4. `prepareScrollRuntimeForStreamingStart(...)`
  5. 置 `sendStartAnchorActive = true`
  6. 按 reverse-list 口径同步 `requestScrollToItem(0)`，让新插入的底部 assistant placeholder 成为视觉底部锚点
- `scrollToBottom(false)` 当前已经回到 reverse-list 主链口径；聊天页主调处应继续把“视觉底部最新消息”的 index 按 `0` 传给 coordinator，而不是沿用正向列表的 `lastIndex`
- 反向列表主链下不再运行旧 streaming 高度追滚：`BindChatListScrollEffects(...)` 不允许再调用 `followStreamingByDelta(...)` 或直接 `scrollBy(...)` 去追 streaming 正文高度，`streamBottomFollowActive` 空壳状态也不再保留；streaming 期间只维护单一 `Idle / AutoFollow / UserBrowsing` 状态机与发送起步保护
- `prepareScrollRuntimeForStreamingStart(...)` 当前会把 `scrollMode` 直接置为 `AutoFollow`，因为用户按发送本身就是回到底部看新回复的明确意图；不要在发送后继续保留 `UserBrowsing`
- 回到底部按钮不允许开机、程序回底、bounds 初次上报自己冒出来。按钮资格统一为：消息非空、键盘不可见、生命周期未抑制，并且反向列表真实离底 `firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset > 0`，或 streaming 态用户触碰消息列表进入 `UserBrowsing`。按钮不要再用消息 bounds / 工作线 `atBottom` 口径决定资格，也不要再加发送后 IME 过渡伪锁。按钮显示是短 pulse：用户继续滚动可续亮，停止滚动后自动隐藏；点击按钮必须直接回到反向列表真实底部 `scrollToItem(0)` 并清掉 pulse
- 两阶段 finalize 当前必须继续保留，不能为了“看起来简单”回退到同拍 `isStreaming = false` 的旧写法
- `composerTopInViewportPx`、`messageViewportTopPx`、`inputFieldBoundsInWindow` 等旧几何状态继续保留给 selection / bounds / fallback 使用；后续不要再把它们升格为“第二套消息运行时主人”的真值来源
- 远端 hydrate、发送事务和本地 snapshot 继续只允许原地增改；不要再把消息替换链改回 `clear() + addAll()`

### 7.4 当前禁改清单

- 不要重新引入 mixed active-zone / overlay 运行时，不要再恢复：
  - `StreamingLocation`
  - `BottomActiveZoneSlice`
  - `renderBottomActiveZone()`
  - active-zone 拖动接管 / Overlay 恢复门
  - `requestSendStartBottomSnap()`
- 不要在没有明确拍板的前提下，再切回“正向列表 + overlay/placeholder/追滚补偿”那套旧主链
- 不要恢复 visual offset catch-up、overlay height follow、history-only send-start snap、placeholder/twin-tree finalize、clip/mask 这类已废弃路线
- 不要删除 `sendStartAnchorActive` 或发送期 reserve 锁，除非有同等级保护替代；发送瞬间抖动之前就是靠这条短窗口保护压住的
- 不要把两阶段 finalize 改回“同拍直接切 settled”；这会把尾部收口抖动和底部空白再带回来

### 7.5 当前回归重点

- 首屏进入且本地有历史时，是否仍稳定贴底
- 发送瞬间小球和历史文本是否仍抖动
- streaming 过程中上下拖动是否不再乱窜、重叠、抢手
- finalize 收口是否仍保持前几轮压下去的稳定度
- 输入框上方和静态文本底部是否不再出现额外白块

## 8. 其他聊天 UI 基线

消息复制链：
- 用户消息与 AI 消息共用同一条主链
- 用户消息保持纯文本显示
- AI 复制的是渲染后的可见正文，不是 Markdown 原文
- 黑卡片只保留：`复制`、`全文复制`

输入区选择菜单：
- 走系统选区和手柄
- 自定义黑底白字菜单
- 菜单保留：复制、粘贴、剪切、全选
- 菜单贴输入框上方，不压输入框，不飘到标题区

免责声明：
- streaming 期间不提前显示免责声明文字
- 如最终会出现免责声明，streaming 期间只允许保留稳定几何占位

Markdown 表格：
- 当前不做真表格渲染
- 模型若输出 Markdown 表格，完成态自动降级成普通可读文本块

消息链接：
- 当前只支持 assistant 完成态正文点击链接
- 单击直接走系统浏览器
- 不允许为链接再加额外悬浮层，不能破坏现有复制链

失败提示与重试：
- 用户发送失败：`未发送  重发`
- AI 回复未完成：`回复未完成  重试`
- footer 必须渲染在各自消息 item 内部，不允许做成悬浮层
- `重发 / 重试` 不新增用户消息，不重复计轮次
- 失败消息正文和 footer 当前必须随本地聊天窗口快照一起持久化；切后台 / 杀进程 / 重进后仍要一起恢复，不能只剩正文、只剩历史，或只剩用户消息

自动恢复：
- 依赖稳定 `client_msg_id`
- 优先走后端快照对账，不是重新发一轮
- 同一 `client_msg_id` 不允许并发多条恢复链
- 恢复失败后才回落到 `回复未完成  重试`

## 9. 后端与运维边界

当前后端目录唯一真相：
- [server-go](D:/wuhao/server-go)

规则：
- 不再使用不存在的 `server` 目录口径
- 图片上传、会员、上下文、模型、摘要、恢复等后端能力，默认都以 `server-go` 为准
- `/api/chat/stream` 当前允许纯文字、纯图片、图文混合；只有文字和图片都为空时才拒绝
- `/api/session/round_complete` 同步接受 `user_images`，避免纯图轮次和上一轮图片上下文丢失
- 以后如需 Codex 参与运维，优先走脚本、CLI、OpenAPI 这类可审计入口

优先级：
- SAE：脚本 / CLI / OpenAPI 优先
- PolarDB：人工查看优先 DMS；Codex 优先只读查询、迁移脚本、备份脚本
- 日志：优先脚本化查询，不靠临时手点控制台

如果后续长期让 Codex 辅助发版、回滚、查日志、查库，应把入口固化进仓库，例如部署脚本、回滚脚本、日志脚本、数据库只读脚本。

## 10. 参考文档

以下文档只作参考：
- [README.md](D:/wuhao/README.md)
- [docs/chat-ui-dynamic-interaction-logic.md](D:/wuhao/docs/chat-ui-dynamic-interaction-logic.md)
- [docs/chat-ui-clean-state-checklist.md](D:/wuhao/docs/chat-ui-clean-state-checklist.md)
- [docs/backend-boundaries.md](D:/wuhao/docs/backend-boundaries.md)

如果参考文档与本文件冲突，以 [AGENTS.md](D:/wuhao/AGENTS.md) 为准。

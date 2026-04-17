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
- Android Compose UI、滚动链、动画、渲染时序、布局抖动、输入区/工作线几何问题：优先建议用户只找 Gemini 会诊
- 后端 Go、业务边界、接口职责、数据流、架构复盘、文档归纳：优先建议用户找 Claude 会诊
- OpenAI / Codex / API / 官方产品能力与限制：优先查官方文档或官方资料，不把 Claude / Gemini 当真源
- Codex 的职责始终是：结合仓库当前代码落地修改、检查旧方案是否并行、编译验证、更新文档并提交推送
- 如果问题类型已经明显匹配上面某一类，后续窗口应直接点名建议用户找对应对象，不要再让用户自己猜“该问谁”

会诊稿规则：
- 会诊前先完成本地代码排查；先确认当前唯一主链、已废弃旧链、仍在运行时生效的真实代码位置，再决定问什么
- 发给外部模型的会诊稿必须锚定当前仓库代码，至少写清：相关文件路径、关键函数/状态名、必要时的代码片段或行号、当前现象、已排除项、怀疑点、限制条件
- 会诊问题必须尽量收敛到具体实现，不要只发“为什么会抖”“怎么优化”这类抽象描述；要明确让对方基于当前代码回答，不要假设仓库里不存在的接口、状态或组件
- 若当前问题已经定位到某几个可疑点，会诊稿应直接要求对方围绕这些代码点给最小可落地方案，并明确禁止跑题到已被排除的方向
- 收到会诊建议后，Codex 必须先对照当前代码核验：有没有假设不存在的字段、有没有和现有主链冲突、有没有把旧方案重新带回来；核验不过不直接下刀
- 当前默认现实前提：外部会诊对象（如 Gemini、Claude）通常无法直接读取本地仓库、文件链接或真实运行环境；它们看到的只有用户手动转发过去的文字、截图、代码片段与日志
- 因此外发会诊稿必须自包含：不能只写“看 ChatScreen.kt”“看仓库最新代码”，而要把对方作答所必需的关键代码片段、状态名、调用顺序、限制条件直接贴进消息里；若缺少这些上下文，会诊结果默认不可信
- 如果外部会诊通过手机聊天软件进行，优先压缩成“问题说明 + 关键代码片段 + 明确追问”的短稿，避免对方因为上下文不全继续脑补仓库结构

交付要求：
- Android 改动后编译：`./gradlew.bat :app:compileDebugKotlin`
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

- 用户消息按正常消息流从上往下排
- waiting 小球、streaming 正文、settled 完成态共用同一个 assistant 内容宿主
- 列表底座当前使用 `LazyColumn(reverseLayout = true)`；显示顺序与状态顺序分离，视觉上最新消息固定贴近底部工作线
- 发送起步时，小球所在的 assistant 起步宿主天然贴近工作线；用户消息自然位于其上方
- 正文从工作线开始向上增长；在底部锚定成立时，不再依赖正向列表那套“先算 offset 再矫正”的发送起步链
- 用户拖动立即让权，不允许隐藏第二条链抢手
- 完成态和静态贴底围绕同一条工作线附近目标线收口，不再保留明显更低的第二条底线
- 底部不应再出现额外可见空白
- 历史区浏览时，输入框弹起 / 收起不应再带着消息区整体联动；底部态也应尽量减轻这种联动
- 当前除“发送瞬间轻微上下抖一下”外，其它主滚动 / streaming / finalize 体感问题都已按现阶段真机反馈收口

### 7.2 五环节铁律

1. 发送起步
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt)
- 做法：在发送事件源里，插入用户消息和 assistant placeholder 后，立即请求 `LazyListState.requestScrollToItem(0)` 回到底部锚点，覆盖稳定 key 对旧可见项的默认保护，确保新插入的小球回到视口底部工作线
- 当前锚点：小球所在的 assistant 起步宿主可见底边
- 当前目标：小球第一次出现就落在工作线；用户消息在其上方，正文从工作线开始长

2. AutoFollow
- 主人：[ChatScrollCoordinator.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt)
- 作用：在反向底座里只维护“谁拥有滚动控制权”；当用户未打断时，生成内容继续沿底部锚定自然向上长，不再执行正向列表那套 `snap + scrollBy` overflow 追赶链

3. 发送期几何稳定
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt) 的工作线 / `recyclerBottomPaddingPx` 计算
- 作用：发送当拍仍允许即时清空输入框并立即插入消息，但这一个窗口里的工作线与底部保留高度必须保持单一真相；除发送起步这个极短窗口外，`isComposerSettling` 期间应优先回退到稳定 bottom bar / overlay 高度，避免输入区收口时把消息区整体带着抖

4. 完成态收口
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt) 的两阶段 finalize
- 作用：streaming 结束时不再在同一拍直接切 `isStreaming = false`；必须先把最终内容写入 completed 消息，并保持 streaming 几何口径继续生效，同时清掉该消息旧 streaming bounds，等待 completed 宿主 fresh bounds 真正上报后，再原子切换 `isStreaming / streamingMessageId / scrollRuntime`。第二阶段不能靠短超时硬切；若 app 已退后台，应等回前台后再继续等 fresh bounds，避免完成那一拍工作线口径、底部判定源、内容宿主一起切换导致偶发上跳与底部留白

5. 用户浏览
- 主人：用户手指
- 作用：进入 `UserBrowsing` 后立即让权；不看滑动方向，不做额外恢复链。只有当生成行真实回到工作线命中带并且用户结束交互时，或通过“回到底部”/新一轮发送，才重新接回主链

6. 首次进入贴底
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt)
- 作用：冷启动且已有历史消息时，直接滚到反向列表的 `index = 0`，让最新消息天然贴住底部工作线；从后台切回时不默认自动贴底

铁律：
- 同一时刻只能有一个主人控制滚动
- 新增任何 scroll 调用前，必须说明它属于哪一个环节
- 回到底部按钮只能从主链派生，不能另挂第二套补滚链

### 7.3 当前实现细则

- 工作线和静态贴底线必须共用同一个物理锚点；当前以反向列表的底部锚定 + `recyclerBottomPaddingPx` 共同定义工作线
- 列表底座当前为纯 Compose `LazyColumn(reverseLayout = true)`，不再保留 `RecyclerView` / `stackFromEnd`
- 消息区容器高度保持固定；`ChatComposerBottomBar` 已从 `Scaffold.bottomBar` 挪到内容层底部 overlay，输入区高度变化只再影响 `recyclerBottomPaddingPx`，不再直接挤压消息区容器
- sending / streaming / completed 不允许再切换成不同内容宿主上报底边
- waiting 小球与 streaming 首行共用稳定宿主外壳；waiting 壳子高度必须接近首行正文高度，避免首字出现时宿主突然变高
- streaming 渲染当前不再区分“waiting 专用宿主”和“首字后专用宿主”；waiting 小球与 streaming 首块已收敛到同一个 `ChatStreamingRenderer` 内容宿主内切换，首字上屏前后保持同一物理外壳
- `ChatStreamingRenderer` 当前不再让 streaming / settled 走两套最外层宿主；两种 renderMode 已统一复用同一个最外层 `Column` 承接 `boundsReportingModifier` 和宽度约束，streaming 需要的 `Alignment.BottomStart` 底对齐已下沉到内部 `Box`，减少完成态切换那一拍因为外壳换树导致的轻微上抬 / 重排感
- `ChatStreamingRenderer` 当前不再用父级 `Column(spacedBy(...))` 统一分发 Markdown block 间距；streaming 非首块改为在 block 前插入独立 `Spacer(height = MARKDOWN_BLOCK_SPACING)`，减少新区块出现时把已有内容整体向下踹一拍
- 不再做中部上抬；用户消息、waiting 小球、streaming、完成态、失败态的最低边界统一围绕工作线
- 发送起步和后续跟随都只走 `LazyListState`，运行时已无 active `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout / scrollToPositionWithOffset` 链
- 当前已删除所有只服务正向底座的发送起步 offset 链：`pendingStartAnchorScrollOffsetPx`、`sendStartViewportHeightPx`、`sendStartWorklineBottomPx` 均不再参与运行时定位
- 发送事件当前不再计算“视口高度 - item 高度”的正向 offset；底部回位统一走反向列表 `index = 0`
- 发送事件在插入用户消息和 assistant placeholder 后，会立即请求 `requestScrollToItem(0)` 回到底部锚点；这样能直接覆盖 `LazyColumn` 对旧可见项的默认位置保护，避免小球先悬空一拍再掉回工作线
- 发送事务当前必须在进入网络 / SSE 协程前，同步完成输入框收口、用户消息 upsert、assistant placeholder、`prepareScrollRuntimeForStreamingStart(...)` 与 `requestScrollToItem(0)`；不允许再把“输入框清空”和“消息插入 + 回底请求”拆成两拍，否则会重新带回发送瞬间上下抖
- waiting / streaming 首行必须共用同一物理高度；发送起步不允许再保留额外 waiting 壳高或“测完再修”的旧反馈链
- `ChatScrollCoordinator` 当前不再在 streaming 期间主动 `scrollBy` 追工作线；反向底座下 streaming 只保留 `Idle / AutoFollow / UserBrowsing` 控制权切换，运行时已无 active `snapStreamingToWorkline / performStreamingFollowStep / resolveStreamingFollowStepPx` 链
- `scrollToBottom(false)` 当前只保留 `scrollToItem(0)` / `animateScrollToItem(0)` 这一条主链，不再串 `alignChatListBottom()` 那套 8 帧 `scrollBy` 底边补偿
- `recyclerBottomPaddingPx` 仍负责把底部输入区和工作线留出来；反向底座下最新消息天然贴着这条底部保留线，不再需要额外 footer 或双重到底补推
- `recyclerBottomPaddingPx` 与工作线当前在大多数 `isComposerSettling` 窗口里仍会回退到稳定 bottom bar / overlay 高度；但发送起步这一个极短窗口现在是例外：若 `sendUiSettling` 正在生效，仍允许继续参考实时 `composerTop`，避免发送当拍地基断崖回退后再配合 `requestScrollToItem(0)` 造成整块上下抖
- `sendStartBottomPaddingLockActive` 已退出工作线和底部保留高度的运行时主链；当前真正负责冻结实时 composer 几何的是 `sendUiSettling` 与 `composerSettlingMinHeightPx / composerSettlingChromeHeightPx` 这组输入区收口状态
- 发送当拍只允许对消息列表做原地增改（`upsert` 用户消息 + assistant placeholder），不允许再用 `messages.clear() + addAll()` 清空列表后重建
- 远端历史 hydrate 当前也不再使用 `messages.clear() + addAll()`；`replaceMessages(...)` 已改为按消息 `id` 原地 `set/add/move/remove` 的增量更新，尽量保留反向列表的 item 缓存和滚动锚点
- 首次进入聊天页的贴底当前由 [ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt) 直接 `scrollToItem(0)`；从后台切回时不默认自动贴底
- 本地 fake streaming 在 `ON_PAUSE / ON_STOP` 时必须同步收口成 completed 消息，并同步落本地聊天窗口、清 streaming draft；切回前台时不允许再靠异步恢复链把半截 draft 重新拉回屏幕
- 本地 fake streaming 结束前不再等待 `currentStreamingOverflowDelta()` 这类旧 overflow 口径“自行收平”后再 finish；正文刷完后直接进入完成态收口，避免旧收口链继续制造尾帧回弹
- streaming 行级 reveal 当前不再走 `rememberRendererLockedStreamingRenderedLinesImpl()` / `buildLockedStreamingActivePreview()` 这层 fresh line 锁预览；运行时必须直接用原始 `StreamingRenderedLines` 渲染，禁止再把 `activeLine` 锁成预览串或空串，避免 activeLine 升格为 stableLine 时出现 1 帧高度塌陷
- `finishStreaming()` 与后台同步完结当前都会在用户未进入 `UserBrowsing` 时补一发 `requestScrollToItem(0)`；这不是旧 `pendingFinalBottomSnap` 状态机，也不是多帧 `scrollBy` 补偿，只是让 completed 宿主在下一次 remeasure 里重新咬回 `index = 0` 的单次归位

### 7.4 当前已收口的交互规则记忆

- 历史区浏览：用户进入 `UserBrowsing` 后，主链立即让权；输入框弹起 / 收起不再作为把历史区一起推着走的理由
- 底部态生成：waiting 小球、streaming 首行、正文增长、completed 收口都围绕同一条工作线和同一条底边宿主进行，不再允许等待态、首字态、完成态各走一套壳子
- 自动跟随：当前只有 `Idle / AutoFollow / UserBrowsing` 这一套控制权真相；不再允许背后并存第二条 `scrollBy` 补偿链
- 完成态：当前统一走两阶段 finalize，不再允许 `isStreaming` 同拍切换、短超时硬切、旧 `pendingFinalBottomSnap`、旧尾帧补滚
- 生命周期：本地 fake streaming 在切后台时必须直接收口为 completed，不再允许前后台切换把半截 streaming draft 拉回屏幕
- 底部空白：完成态、切后台恢复、历史 hydrate 当前都不应再制造底部额外空白；若新改动再次出现底部空白，优先检查 finalize 时序和宿主 bounds 上报，而不是先怀疑底座类型
- 当前唯一未关闭体感问题：发送瞬间整块消息区仍会轻微上下抖一下；后续排查应继续只盯 `commitSendMessage()`、发送期几何切换和 `requestScrollToItem(0)` 这一拍，不再把已收口的 streaming / finalize 问题重新并列回来

当前排查顺序：
1. assistant 真实内容底边是否仍由同一宿主上报
2. 工作线与静态贴底线是否仍共用同一物理锚点
3. `Idle / AutoFollow / UserBrowsing` 是否仍是唯一滚动状态真相
4. 回到底部按钮是否仍只读主链派生

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

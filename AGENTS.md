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
- [ChatRecyclerViewHost.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatRecyclerViewHost.kt)：纯 Compose `LazyColumn` 底座、bottom padding 锚点、发送起步定位；文件名只是历史命名残留，运行时已不是 `RecyclerView`

旧 `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout / frozenBottom / retainedBottomGap` 等旧滚动术语全部视为历史归档，不再执行。

## 7. 当前 Compose 列表滚动链唯一真相

### 7.1 总口径

- 用户消息按正常消息流从上往下排
- waiting 小球、streaming 正文、settled 完成态共用同一个 assistant 内容宿主
- 发送起步时，assistant 起步宿主的可见底边围绕工作线落位；小球第一次出现应命中工作线附近
- 只有正文尾部真正接近工作线后，才允许进入 AutoFollow
- 用户拖动立即让权，不允许隐藏第二条链抢手
- 完成态和静态贴底围绕同一条工作线附近目标线收口，不再保留明显更低的第二条底线
- 底部不应再出现额外可见空白

### 7.2 五环节铁律

1. 发送起步
- 主人：[ChatRecyclerViewHost.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatRecyclerViewHost.kt)
- 做法：`LazyColumn` 内用 `LazyListState.scrollToItem(...)` + `scrollBy(...)` 一次性把 assistant 起步宿主可见底边对齐工作线
- 当前锚点：小球所在的 assistant 起步宿主可见底边
- 当前目标：小球第一次出现就落在工作线；用户消息在其上方，正文从工作线开始长

2. AutoFollow
- 主人：[ChatScrollCoordinator.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt)
- 作用：正文超过工作线后，按 overflow 直接跟随，不再多帧小步追赶

3. 发送起步保护期
- 主人：`sendStartAnchorActive`
- 作用：发送起步定位完成后，到正文真实命中工作线前，禁止 `snapStreamingToWorkline()` 和 `AutoFollow` 提前接管

4. 完成态收口
- 主人：`scrollToBottom(false)`
- 作用：completed 宿主真实底边到位后收口到目标线

5. 用户浏览
- 主人：用户手指
- 作用：进入 `UserBrowsing` 后立即让权；不看滑动方向，不做额外恢复链。只有当生成行真实回到工作线命中带并且用户结束交互时，或通过“回到底部”/新一轮发送，才重新接回主链

6. 首次进入贴底
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt)
- 作用：冷启动且已有历史消息时，直接滚到列表 footer 并补一次到底推送，把底部保留空白露完整；从后台切回时不默认自动贴底

铁律：
- 同一时刻只能有一个主人控制滚动
- 新增任何 scroll 调用前，必须说明它属于哪一个环节
- 回到底部按钮和 final snap 只能从主链派生，不能另挂第二套补滚链

### 7.3 当前实现细则

- 工作线和静态贴底线必须共用同一个物理锚点，优先使用真实 `composerTopInViewportPx`
- 列表底座当前为纯 Compose `LazyColumn`，不再保留 `RecyclerView` / `stackFromEnd`
- 消息区容器高度保持固定；`ChatComposerBottomBar` 已从 `Scaffold.bottomBar` 挪到内容层底部 overlay，输入区高度变化只再影响 `recyclerBottomPaddingPx`，不再直接挤压消息区容器
- sending / streaming / completed 不允许再切换成不同内容宿主上报底边
- waiting 小球与 streaming 首行共用稳定宿主外壳；waiting 壳子高度必须接近首行正文高度，避免首字出现时宿主突然变高
- 不再做中部上抬；用户消息、waiting 小球、streaming、完成态、失败态的最低边界统一围绕工作线
- 发送起步和后续跟随都只走 `LazyListState`，运行时已无 active `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout / scrollToPositionWithOffset` 链
- `sendStartAnchorActive` 必须覆盖 waiting 和早期首字阶段；正文真实命中工作线前，不允许 `Idle snap` 或 `AutoFollow` 抢到发送起步的控制权
- 发送当拍只允许对消息列表做原地增改（`upsert` 用户消息 + assistant placeholder），不允许再用 `messages.clear() + addAll()` 清空列表后重建
- 首次进入聊天页的贴底当前由 [ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt) 直接对 footer 做两次到底推送；从后台切回时不默认自动贴底

当前排查顺序：
1. assistant 真实内容底边是否仍由同一宿主上报
2. 工作线与静态贴底线是否仍共用同一物理锚点
3. `Idle / AutoFollow / UserBrowsing` 是否仍是唯一滚动状态真相
4. 回到底部按钮与 final snap 是否仍只读主链派生

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

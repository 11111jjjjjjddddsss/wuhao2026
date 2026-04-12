# 农技千问仓库主规则

本文件是本仓库唯一主规则文档。以后任何 GPT、Codex、子窗口、接手窗口，在开始改代码前都先看这里。

当前仓库实际只有两条主线：
- Android 客户端：`app`
- Go 后端：`server-go`

历史调参、旧链路、旧口径都留在 git 历史里，不再继续堆在本文。

## 1. 文档规则

- 规则文档只保留这一份：[AGENTS.md](D:/wuhao/AGENTS.md)
- [docs/chat-ui-dynamic-interaction-logic.md](D:/wuhao/docs/chat-ui-dynamic-interaction-logic.md)、[docs/chat-ui-clean-state-checklist.md](D:/wuhao/docs/chat-ui-clean-state-checklist.md)、[docs/backend-boundaries.md](D:/wuhao/docs/backend-boundaries.md) 只作参考，不再承担主规则职责
- 规则变更、实现边界变化、唯一真相变化，必须同次同步更新本文件
- 如果本文件与当前代码不一致，优先修本文，不允许放着过期规则不管

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
- [ChatRecyclerViewHost.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatRecyclerViewHost.kt)：RecyclerView 底座、bottom padding 锚点、发送起步定位

旧 `LazyColumn / listState / sendTick / frozenBottom / retainedBottomGap` 等旧滚动术语全部视为历史归档，不再执行。

## 7. 当前 RecyclerView 滚动链唯一真相

### 7.1 总口径

- 用户消息和 assistant 消息都先按正常消息流从上往下排
- waiting 小球、streaming 正文、settled 完成态共用同一个 assistant 内容宿主
- 只有正文尾部真正接近工作线后，才允许进入 AutoFollow
- 用户拖动立即让权，不允许隐藏第二条链抢手
- 完成态和静态贴底围绕同一条工作线附近目标线收口，不再保留明显更低的第二条底线
- 底部不应再出现额外可见空白

### 7.2 五环节铁律

1. 发送起步
- 主人：[ChatRecyclerViewHost.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatRecyclerViewHost.kt)
- 做法：新消息插入后，在首帧预绘制前一次性 `scrollToPositionWithOffset(...)`
- 当前锚点：assistant 起步宿主顶边
- 当前目标：以 `280dp` 为基础上抬，再按本轮用户消息真实已布局高度做动态上抬，并钳在中部偏上的可视区间内

2. 起步保护期
- 主人：`sendStartAnchorActive`
- 作用：正文真正接近工作线前，禁止 `snapStreamingToWorkline()` 和 `AutoFollow` 提前接管

3. AutoFollow
- 主人：[ChatScrollCoordinator.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt)
- 作用：正文超过工作线后，按 overflow 直接跟随，不再多帧小步追赶

4. 完成态收口
- 主人：`scrollToBottom(false)`
- 作用：completed 宿主真实底边到位后收口到目标线

5. 用户浏览
- 主人：用户手指
- 作用：进入 `UserBrowsing` 后立即让权；不再按滑动方向自动恢复 `AutoFollow`，只有当用户手动回到底部工作线附近、列表已经不能再继续下滑、并且结束交互时，或通过“回到底部”/新一轮发送，才重新接回主链

铁律：
- 同一时刻只能有一个主人控制滚动
- 新增任何 scroll 调用前，必须说明它属于哪一个环节
- 回到底部按钮和 final snap 只能从主链派生，不能另挂第二套补滚链

### 7.3 当前实现细则

- 工作线和静态贴底线必须共用同一个物理锚点，优先使用真实 `composerTopInViewportPx`
- `RecyclerView` 已关闭 `stackFromEnd`
- sending / streaming / completed 不允许再切换成不同内容宿主上报底边
- 发送起步不再靠 assistant 宿主内部 `minHeight` 预留抬高，起步高度只认 `ChatRecyclerViewHost` 的外层锚点
- 发送起步外层锚点不再写死同一个落点，而是以本轮用户消息真实已布局高度做动态修正，让“用户消息 + waiting 小球”整体落在中部偏上的稳定区间
- 发送起步期间允许冻结发送当拍的 bottom padding，避免 IME / composer 回落把文本区重新拖低
- 如果发送起步会暴露一帧坏帧，允许先隐藏“本轮用户消息 + assistant 起步宿主”
- 如果旧历史列表仍会在整表重排时露出轻微挪动，允许短时冻结整个 `RecyclerView` 视觉快照，等起步定位与 reveal 稳定后再硬切释放
- 上述隐藏、快照冻结都只是在遮坏帧，不属于新增第二条滚动链
- 首次进入聊天页时，如果当前有历史消息且不在底部附近，允许补一次 `scrollToBottom(false)`；从后台切回时不默认自动贴底

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

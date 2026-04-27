# 当前未关闭风险

最后更新：2026-04-27

## R1 运维入口仍以文档骨架为主

- 状态：未关闭
- 说明：`docs/runbooks` 已建立，但仓库内尚未沉淀完整的 SAE 部署、回滚、日志、数据库只读脚本和实际命令
- 风险：换窗口时能知道要看哪里，但真正执行运维仍可能依赖人工补充
- 后续动作：后面一旦发生真实发版、回滚、查日志、查库，就把实际可执行入口补进 runbook 和脚本

## R2 项目记忆已有程序化检查，但覆盖仍偏粗

- 状态：未关闭
- 说明：仓库里已有 `scripts/check_project_memory.py`，CI 也会在关键真相文件变更时检查是否同步更新了项目记忆文件；但当前检查粒度仍是“至少改了一份 memory 文件”，还不会判断 `current-status / open-risks / pending-decisions / recent-changes` 之间是否互相一致
- 风险：即使 CI 通过，记忆文件之间仍可能出现轻微漂移或局部过期，影响新窗口对“当前真相 / 已关闭风险 / 仍待决策”的判断
- 后续动作：后面视情况继续把校验收紧到“按变更类型要求更新特定文件或特定章节”，减少文档之间互相打架

## R3 GitHub 协作层尚未结构化

- 状态：未关闭
- 说明：目前仓库已有 CI，但还未补充 Issue 模板、PR 模板、CODEOWNERS、项目字段等协作骨架
- 风险：跨窗口协作能接住仓库内真相，但任务排队、责任归属、变更说明仍不够结构化
- 后续动作：需要时再按最小改动补 GitHub 协作层

## R4 聊天运行时已切回正向列表单主人，但仍待真机验证

- 状态：未关闭
- 说明：当前聊天消息运行时已按用户拍板从反向列表切回单一正向 `LazyColumn` 主人：`ChatRecyclerViewHost.kt` 不再使用 `reverseLayout` / `items.asReversed()`，`messages` 仍按 oldest -> newest 直接显示，最新消息在 `lastIndex`。mixed active-zone / overlay、小分割 list itemization、streaming `scrollBy` / `dispatchRawDelta` 高度补偿都不在主链运行
- 风险：这次正向列表改动已完成编译和静态自查，但还没有完整用户真机回归。当前最需要确认的是：1) 首屏进入有历史时是否稳定贴到 96dp 工作线，并露出工作线以下空白；2) 发送瞬间小球是否仍第一时间出现在 96dp 工作线，历史文本是否不抖；3) 正向列表 streaming 时下一行是否还有工作线下方冒头闪；4) 用户上滑 / 下滑是否不抢手，想停哪里能停哪里；5) 点击回到底部后是否恢复 AutoFollow；6) streaming -> settled 共用 soft-wrap renderer 且 streaming 预留免责声明几何后，finalize 收口是否仍贴 96dp 工作线且不出现行高 / 行宽 / 尾部高度微动；7) 输入框上方和静态文本底部是否不再出现额外白块；8) composer 从 `SubcomposeLayout` 拆出后，冷启动首次点输入框、键盘弹起 / 回缩是否更利索且不破坏发送收口
- 风险补充：正向列表底部锚定当前依赖 `lastIndex + FORWARD_LIST_BOTTOM_SCROLL_OFFSET` 的列表末端 clamp 语义；短内容不满一屏时由 `verticalArrangement = Arrangement.Bottom` 贴底；`UserBrowsing -> AutoFollow` 当前需要连续 2 帧稳定到底，避免 pre-anchor 的瞬态到底误吸回，同时让手动往下滑回底部后的自动跟随恢复更利索。当前 reveal 提交前会先请求底部锚点，内容提交后改由顶层 `SideEffect` 在同帧 apply changes 后、layout 前请求底部锚点；开机显示门也放宽以减少白屏。用户真机初测已确认这条 SideEffect 同帧锚定压住了“下一行冒头闪”。首屏贴底已补 bottom reserve ready guard 和一次 post-snap 修正，但仍需真机确认工作线以下 96dp 空白是否每次都完整露出。后续若在长回复 / 不同机型上复现，下一刀优先检查 reveal 提交节奏 / 工作线 bounds / contentPadding 与 item padding 的几何关系，不恢复 overlay、小分割或 raw delta
- 风险补充：pending finalize 仍不主动 bottom align，主要依赖两阶段 fresh bounds、unified soft-wrap renderer 和 streaming 免责声明几何占位。若含免责声明答案收口仍微跳，优先在同一消息主人内复核高度来源，不恢复完整 `scrollToBottom(false)` 精修
- 风险补充：composer 当前只是完成 P0 拆链，`ChatComposerCollapseOverlay` / prewarm 仍保留。若真机仍有输入框残影或冷启动首点迟钝，下一步优先检查 overlay 交接、focus clear / keyboard hide 时序和是否需要 `WindowInsetsAnimationCompat`，不要把 IME padding 挪到根容器去抬升消息列表
- 后续动作：先让用户真机回归这轮正向列表单主人主线；若冒头闪或尾部收口仍有问题，先围绕正向列表底部锚点、reveal 提交前锚定、renderer 高度一致性继续收紧；若用户拖动仍抢手，先检查是否有程序滚动误判或 AutoFollow 没放权，不恢复反向列表、小分割或 overlay 切管

## R5 外部会诊仍依赖人工转发上下文

- 状态：未关闭
- 说明：小米 / MiMo 免费版、Claude 等外部模型默认看不到本地仓库，只能依赖用户通过聊天软件转发的代码片段、日志和截图；即使仓库内规则已收紧，会诊结果仍受转发上下文完整度影响。当前用户偏好已调整为：本项目后续外部会诊默认优先整理给小米 / MiMo
- 风险：如果外发内容不自包含，对方仍可能脑补仓库结构、假设不存在的接口，导致方案听起来合理但无法直接落地
- 后续动作：继续坚持“问题说明 + 关键代码片段 + 明确追问 + 已排除项 + 限制条件”的短稿格式；尤其发给小米 / MiMo 时要把当前真实代码结构、关键函数 / 状态和不能碰的旧方案写清楚。收到方案后先由 Codex 对照当前代码核验再下刀

## R6 正式云资源尚未采购

- 状态：未关闭
- 说明：当前仓库已经有 `server-go` 主线和 SAE / 回滚 / 日志 / 数据库只读 runbook 骨架，但正式服务器、数据库、域名、HTTPS 和日志项目都还未真正落地
- 风险：后续一旦开始后端联调、真实发版、环境变量注入或图片存储接入，容易因为真实环境参数缺失而临时拍脑袋，导致 runbook 和实际入口再次脱节
- 后续动作：采购前先按 `docs/runbooks/infra-readiness.md` 把 Region、环境命名、数据库、SAE、域名/HTTPS、OSS/SLS/Redis 是否首版接入这些问题拍板；第一套真实环境落地后，同次回填 deploy / rollback / logs / db-readonly runbook

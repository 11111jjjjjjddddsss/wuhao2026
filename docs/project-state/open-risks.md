# 当前未关闭风险

最后更新：2026-04-19

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

## R4 聊天滚动链仍需持续装机回归

- 状态：未关闭
- 说明：聊天底座当前已切回正向 `LazyColumn(reverseLayout = false)`；共享 measure 宿主、两阶段 finalize、streaming/settled 同构、首屏贴底 hard reposition 等近几轮修复继续保留。按最新真机反馈，发送微抖、首屏首次进入贴底、完成态归位与 streaming 闪烁都已收口；当前运行时代码已保留“只锁 `LazyColumn` `bottomPaddingPx` 消费点”的发送期保护，并把发送瞬间的小球锚点继续固定在工作线，避免失败态和短文本收口再次变差
- 风险：当前主要风险不再是“抖动没修掉”，而是工作线锚点这条产品口径能否在所有场景里继续稳定成立：发送期 `bottomPaddingPx` 锁已经压住抖动，普通发送时的锁值当前优先使用最近一次观察到的稳定收口 reserve，理论上不再随多行输入框高度漂移；并且当前已经补了“从 `latestConversationBottomPaddingPx` 预热观察值”这道提前量，冷启动首发走兜底链的概率会更低。但这刀仍需要继续靠真机确认长文本/短文本场景里，小球与工作线的距离是否真的收平。另一个边界是：如果某次首发发生在观察值和预热值都还没拿到之前，代码仍会短暂退回 `stableComposerBottomBarHeightPx / bottomBarHeightPx` 兜底链，这一拍的首发位置还需要继续留意。另外，`collapseComposer = false` 的失败重发/不收口分支仍继续走旧快照兜底，如果后续用户又开始盯失败态首发位置，这里仍可能成为新的体感差异源。同时，如果后续又为了调首发位置把旧发送补丁、旧滚动补偿、旧历史区联动链带回运行时，也很容易把已经收口的首屏贴底、streaming 闪烁和 finalize 归位再次打坏
- 风险补充：streaming 行级 reveal 当前已经在 `ChatStreamingRenderer.kt` 继续收紧成“至少 2 个可见字符 + 下一次 freshTick 才放行升格”的显示门闩，用来压“工作线下面下一行提前冒头、一闪一消失”。这刀不动 `revealMode = Free`、`onTick = {}` 和 `streamingLineAdvanceTick` 接线，风险比全链接回 reveal 参数低，但仍需要真机确认三类边界：一是大 chunk 一次跨过多个换行点时，是否还会残留极短暂的提前冒头；二是门闩收紧后会不会把新行首字显得过迟，带出明显的延迟见字感；三是 finalize 后 `freshTick` 回落口径是否始终能让整条消息完整显示，不会把最后几行误卡在门闩里
- 风险再补充：streaming follow 当前已从发送起步 release gate 里拆出了严格版 `isAtStreamingWorklineStrict()`，把上容差从一整行收到 16dp。这样能先把“代码主动允许下方露一整行”的主因拿掉，但也意味着 fake stream 吐字很快时，follow `scrollBy` 触发会更积极。真机仍需留意两类残留：一是如果还有极轻微的一瞬闪烁，多半就是 bounds -> follow 的 1 帧 lag，还需要再评估是否把 fast-path 搬进 bounds 回调；二是如果 follow 频率变高后反而带出新的微抖，需要再在 16dp / 24dp / 32dp 之间找平衡，但不要回到一整行高度
- 后续动作：下一轮真机回归优先只看 4 件事：发送瞬间小球是否稳定贴在工作线、发送瞬间是否仍不抖、发送后输入框是否稳定回缩、生成完成后是否仍按现有 finalize 主链回到工作线而不跳到长 assistant 文本开头。若以后再次评估小球首发位置，只围绕发送首发链、共享 measure 宿主里的 `conversationBottomPaddingPx`、`conversationBottomPaddingLockPx`、`pendingStartAnchorScrollOffsetPx` 和 `requestScrollToItem(index, offset)` 这一条正向主链排查；不要再把旧 `withFrameNanos` / `withTimeoutOrNull` / `Snapshot.withMutableSnapshot`、`scrollToBottom(false)` 多拍补偿链，或 release gate / follow delta 假根因重新扩回发送期

## R5 外部会诊仍依赖人工转发上下文

- 状态：未关闭
- 说明：Claude 等外部模型默认看不到本地仓库，只能依赖用户通过聊天软件转发的代码片段、日志和截图；即使仓库内规则已收紧，会诊结果仍受转发上下文完整度影响
- 风险：如果外发内容不自包含，对方仍可能脑补仓库结构、假设不存在的接口，导致方案听起来合理但无法直接落地
- 后续动作：继续坚持“问题说明 + 关键代码片段 + 明确追问 + 已排除项 + 限制条件”的短稿格式；收到方案后先由 Codex 对照当前代码核验再下刀

## R6 正式云资源尚未采购

- 状态：未关闭
- 说明：当前仓库已经有 `server-go` 主线和 SAE / 回滚 / 日志 / 数据库只读 runbook 骨架，但正式服务器、数据库、域名、HTTPS 和日志项目都还未真正落地
- 风险：后续一旦开始后端联调、真实发版、环境变量注入或图片存储接入，容易因为真实环境参数缺失而临时拍脑袋，导致 runbook 和实际入口再次脱节
- 后续动作：采购前先按 `docs/runbooks/infra-readiness.md` 把 Region、环境命名、数据库、SAE、域名/HTTPS、OSS/SLS/Redis 是否首版接入这些问题拍板；第一套真实环境落地后，同次回填 deploy / rollback / logs / db-readonly runbook

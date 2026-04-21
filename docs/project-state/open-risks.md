# 当前未关闭风险

最后更新：2026-04-21

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

## R4 聊天滚动链已基本稳，但 streaming 行级冒头/闪仍待会诊

- 状态：未关闭
- 说明：聊天底座当前已切回正向 `LazyColumn(reverseLayout = false)`；共享 measure 宿主、两阶段 finalize、streaming/settled 同构、首屏贴底 hard reposition 等近几轮修复继续保留。按最新代码，发送微抖、首屏首次进入贴底和完成态归位都已收口；streaming 新行防闪当前基线已切到 `ChatScreen.kt` 的 `onAdvance` reveal-layer wrap guard（基于 `measureStreamingActiveBlockLayout(...)` 的 active block pre-measure）+ `ff4480f` 的 strict follow gate 精修。旧 `rememberGatedStreamingRenderedLines(...)`、preview lock、draw-phase clip 和 `StreamingRevealMode.Conservative / strictLineReveal / lineRevealLocked / streamingLineAdvanceTick` 这条空转 reveal plumbing 都已移除，不再作为候选方向
- 风险：当前唯一仍开放的聊天 UI 主要体感风险，是 streaming 长段落换行时“工作线下面下一行提前冒头 / 一闪一消失”。虽然主修法已经切到 reveal-layer wrap guard，但这条问题仍需要真机验证，不能现在就按“streaming 闪烁已收口”处理，也不要因为它把整条滚动链重新判回未收口
- 风险补充：发送期 `bottomPaddingPx` 锁已经压住抖动，普通发送时的锁值当前优先使用最近一次观察到的稳定收口 reserve，理论上不再随多行输入框高度漂移；`observedCollapsedBottomReservePx` 现在也已经明确收成“共享 measure 为主、`composerTopInViewportPx` 只在列表侧 `latestConversationBottomPaddingPx` 尚未产出时才负责启动 fallback”。这比之前更不容易被旧观察链反向覆盖，但如果某次首发发生在共享 measure 真值和尚未就绪的启动 fallback 之间，代码仍会短暂退回 `stableComposerBottomBarHeightPx / bottomBarHeightPx` 兜底链；另外，`collapseComposer = false` 的失败重发/不收口分支仍继续走旧快照兜底，这两条边界仍要继续留意
- 风险再补充：当前 wrap guard 依赖 ChatScreen 侧缓存的 active block 可用宽度与 style 映射做 pre-measure。paragraph / heading / quote / bullet / numbered 已尽量按当前 renderer 语义对齐，但如果某些 block 的真实宽度、gutter 或 style 与前馈测量仍有偏差，最坏会出现漏补偿（仍有轻微闪露）或轻微过补偿（被 bounds refine 再拉回）。另外，当前 hold 的是整批 reveal batch，而不是精确 wrap cutoff；虽然最新代码已经把 release 条件从“重复 lineCount”收紧成“已观察到旧内容底边真实上移后再放行”，但它仍然不是精确字符级 cutoff，理论上依旧可能留下极轻微 batch 级停顿，或在某些宽度/高度估值不准的 block 上残留少量影子
- 风险补充 2026-04-21：静态/动态文本上下滑动的丝滑度当前只先做了低风险减负（移除 item 线性反查、缓存列表 padding、bounds/chat metrics 相同值去重、收窄 message content bounds 跟踪范围、jump button offset 分桶、streaming wrap guard pre-measure 小缓存、selection bounds 普通缓存化）。这刀不改变滚动主链语义，也不承诺已经完全消除所有滑动不丝滑；若真机仍觉得发涩，下一步再单独评估长 Markdown/SelectableText 的重组成本和真实帧耗，不能借这个问题去重开工作线、wrap guard 或 finalize 链
- 后续动作：下一轮会诊或真机回归优先只看 5 件事：发送瞬间小球是否稳定贴在工作线、发送瞬间是否仍不抖、发送后输入框是否稳定回缩、生成完成后是否仍按现有 finalize 主链回到工作线而不跳到长 assistant 文本开头、streaming 长段落换行时下一行是否还会提前冒头。如果继续找 Claude，会诊稿默认直接带上这条冻结基线：保留 `ff4480f`，主修法已经切到 `onAdvance` 的 reveal-layer wrap guard，旧叶子门闩 / clip / Conservative reveal 参数都已移除；不要顺手多撤其他滚动链修复

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

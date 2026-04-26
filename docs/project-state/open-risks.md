# 当前未关闭风险

最后更新：2026-04-26

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

## R4 聊天运行时已切回反向列表单主人，但仍待真机验证

- 状态：未关闭
- 说明：当前聊天消息运行时已经从 mixed active-zone / overlay 架构切回单一列表主人：`ChatRecyclerViewHost.kt` 使用 `LazyColumn(reverseLayout = true)` + `items.asReversed()`，`ChatScreen.kt` 中的 `StreamingLocation`、`BottomActiveZoneSlice`、`renderBottomActiveZone()`、Overlay 恢复门和 `requestSendStartBottomSnap()` 已退出主链；发送起步回到 list-side 口径，继续保留 `sendStartBottomPaddingLockActive` / `lockedConversationBottomPaddingPx` 与 `sendStartAnchorActive`，同时完成态继续保留两阶段 finalize
- 风险：这次反向列表单主人重构已经完成编译和静态自查，但还没有完整用户真机回归。当前最需要确认的是：1) 首屏进入有历史时是否仍稳定贴底；2) 发送瞬间的小球 / 历史文本是否仍抖动；3) streaming 过程中上下拖动是否不再乱窜、重叠、抢手；4) 小分割 / block item 化撤掉后，文本上滑时“重新找位置 / 行先变长再缩短”的感觉是否消失；5) 撤掉小分割后，原先“小幅上滑仍在本条 assistant 内时被 index 0 长高带一下”的抢手是否回归，以及回归程度是否可接受；6) 用户手动回到真实底部后是否能恢复 AutoFollow，且半路不会自动吸回；7) finalize 收口是否不再出现明显重排闪动；8) 输入框上方和静态文本底部是否不再出现额外白块
- 风险补充：上一次 reverse-layout 尝试（`8730933` / `a6996b9` / `b9aee22`）后来被 `93ce82f` 切回正向，不代表反向物理模型本身错误，而是当时还背着旧 streaming follow、旧 startup/finalize 链和旧发送起步包袱。本轮已继续清理旧包袱：移除 streaming raw follow 追滚、发送起步始终 `requestScrollToItem(0)`、UserBrowsing 恢复改为严格命中工作线、pending finalize 前复核用户是否已接管。若真机上仍出现问题，下一刀应继续优先排查旧正向列表假设是否残留，而不是恢复 mixed active-zone runtime
- 风险补充：针对“吐完后窗口掉头跑到长回复上方”的反馈，当前已撤掉 pending finalize 主动底部精修；后续仍需真机确认完成态底部是否自然稳定，如果只是差少量底部误差，优先接受或单独设计更小的完成态策略，不再恢复大幅滚动精修
- 后续动作：先让用户真机回归这轮反向列表单主人主线；若 send-start 仍抖，优先对照 reverse-layout 旧稳定期的 list-side 锚点释放条件继续收紧；若拖动仍乱窜或重叠，先检查是否还有 forward-list / active-zone 口径残留，而不是恢复 overlay 切管

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

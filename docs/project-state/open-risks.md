# 当前未关闭风险

最后更新：2026-04-28

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

## R4 聊天运行时正向列表主链已基本稳定，仍保留边角观察

- 状态：未关闭
- 说明：当前聊天消息运行时已按用户拍板从反向列表切回单一正向 `LazyColumn` 主人：`ChatRecyclerViewHost.kt` 不再使用 `reverseLayout` / `items.asReversed()`，`messages` 仍按 oldest -> newest 直接显示，最新消息在 `lastIndex`。mixed active-zone / overlay、小分割 list itemization、streaming `scrollBy` / `dispatchRawDelta` 高度补偿都不在主链运行
- 已验证：2026-04-28 用户真机反馈当前滚动链“确实很稳”。核心链路包括：`SideEffect` 同帧底部锚定压 streaming 下一行冒头闪；用户滑回正向列表物理底部后快速恢复 AutoFollow；96dp 工作线以下空白按当前贴底链露出；上滑 / 下滑整体不再抢手
- 剩余风险：仍需在更长回复、不同输入法 / 设备、含免责声明答案、冷启动首次点输入框等边角场景继续观察；如果再次出现冒头闪、尾部收口微动或手动回底不跟随，下一刀优先检查当前正向列表底部锚点、reveal 提交节奏、工作线 bounds、contentPadding 与 item padding 的几何关系，不恢复 overlay、小分割或 raw delta
- 风险补充：pending finalize 仍不主动 bottom align，主要依赖两阶段 fresh bounds、unified soft-wrap renderer 和 streaming 免责声明几何占位。若含免责声明答案收口仍微跳，优先在同一消息主人内复核高度来源，不恢复完整 `scrollToBottom(false)` 精修
- 风险补充：composer 当前已完成 P0 拆链、取消发送旧高度锁、统一收键盘路径为 `clearFocus(force = true)`，并删除死链 overlay prewarm snapshot 协程。若真机仍有输入框残影或冷启动首点迟钝，下一步才评估是否需要 `WindowInsetsAnimationCompat`；不要把 IME padding 挪到根容器去抬升消息列表，也不要动滚动主链
- 后续动作：把当前正向列表滚动链作为稳定基线保留；短期只做边角验证和低风险清理，不再主动重构滚动主链

## R5 外部会诊仍依赖人工转发上下文

- 状态：未关闭
- 说明：小米 / MiMo 免费版、Claude 等外部模型默认看不到本地仓库，只能依赖用户通过聊天软件转发的代码片段、日志和截图；即使仓库内规则已收紧，会诊结果仍受转发上下文完整度影响。当前用户偏好已调整为：本项目后续外部会诊默认优先整理给小米 / MiMo
- 风险：如果外发内容不自包含，对方仍可能脑补仓库结构、假设不存在的接口，导致方案听起来合理但无法直接落地
- 后续动作：继续坚持“问题说明 + 关键代码片段 + 明确追问 + 已排除项 + 限制条件”的短稿格式；尤其发给小米 / MiMo 时要把当前真实代码结构、关键函数 / 状态和不能碰的旧方案写清楚。收到方案后先由 Codex 对照当前代码核验再下刀

## R6 正式云资源尚未采购

- 状态：未关闭
- 说明：当前仓库已经有 `server-go` 主线和 SAE / 回滚 / 日志 / 数据库只读 runbook 骨架，但正式服务器、数据库、域名、HTTPS 和日志项目都还未真正落地。首版采购倾向已从 PolarDB 调整为 RDS MySQL，PolarDB 暂作为后续高规格升级选项
- 风险：后续一旦开始后端联调、真实发版、环境变量注入或图片存储接入，容易因为真实环境参数缺失而临时拍脑袋，导致 runbook 和实际入口再次脱节
- 后续动作：采购前先按 `docs/runbooks/infra-readiness.md` 把 Region、环境命名、RDS MySQL 规格 / 备份 / 白名单、SAE、域名/HTTPS、OSS/SLS/Redis 是否首版接入这些问题拍板；第一套真实环境落地后，同次回填 deploy / rollback / logs / db-readonly runbook

## R7 C+ 长期资产抽取尚未落地

- 状态：未关闭
- 说明：`server-go` 已新增 `session_round_archive` 保存成功完成轮次，并按 30 天滚动保留；`/api/session/snapshot` 的 `a_rounds_for_ui` 可优先返回 30 天内最近 30 轮归档。但这些原始记录当前只是“可恢复 / 可批处理”的材料，尚未抽取成 C+ 用户农业画像 / 用户农业档案
- 风险：如果后续迟迟不做批量抽取，30 天外原始问答会被滚动删除；长期护城河仍主要停留在现有 B/C 摘要，无法形成更稳定的用户农业画像和农业档案
- 后续动作：后续评估 C+ schema、更新频率和抽取模型，优先用归档记录做离线 / 低频批处理，不在当前第一刀里实时抽取、不改 prompt、不把归档内容每轮喂给模型；图片文件本身的 OSS / 本地 uploads 生命周期还需单独按成本和隐私策略配置

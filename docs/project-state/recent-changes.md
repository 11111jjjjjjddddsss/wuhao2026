# 近期重要变更

说明：本文件默认只保留最近 20 条重要变更；更早内容以 git 历史和 ADR 为准。
说明补充：本文件允许保留旧方案的历史记录；旧条目里若出现“反向列表 / requestScrollToItem(0) / asReversed()”或旧会诊对象选择等表述，默认都只是历史过程，不代表当前运行时真相或当前协作口径。当前真相始终以根 `AGENTS.md` 和 `docs/project-state/current-status.md` 为准。

## 2026-04-19

- 继续收口“发送瞬间先上再下”的微抖：`ChatScrollCoordinator.kt` 的发送起步保护释放条件已从“命中工作线容差就放行”收紧成“命中工作线且 composer 已稳定后，再连续一帧命中才放行”。当前不改发送顺序、不改几何计算，只延后 `sendStartAnchorActive` 的真正 release，避免 `requestScrollToItem(index, offset)` 首次命中后，composer 仍在 settling 或刚稳定的边界帧就过早让 follow delta 接管
- 按最新真机反馈，首次进入聊天页且本地有历史时的贴底已确认收口：`ChatScreen.kt` 的 `startupLayoutReady + isWithinBottomTolerance()` 首屏重试链、启动窗口临时 realtime composer geometry，以及 `ChatScrollCoordinator.kt` 里 `scrollToBottom(false)` 非动画路径的正向 hard bottom reposition 这三处修正继续作为当前真相保留
- 文档口径继续收平：根 `AGENTS.md`、`docs/project-state/current-status.md`、`docs/project-state/open-risks.md` 与 `docs/runbooks/chat-ui-regression.md` 已统一把当前主问题收敛为“发送瞬间整块消息区轻微上下抖一下”；首屏贴底改为已收口事项，完成态跳到长文本开头和发送后输入框回缩改为回归观察项
- 会诊规则按最新用户偏好收口：本项目后续如需外部会诊，默认整理成发给 Claude 的自包含短稿，不再继续把 Gemini 当成 Android UI 默认会诊对象
- 基础设施记忆继续补齐：新增 `docs/runbooks/infra-readiness.md`，把“服务器还没买”这一现状对应到正式云资源采购前检查单；`pending-decisions.md` 与 `open-risks.md` 也同步补上“正式云资源首版怎么落 / 正式云资源尚未采购”两项，避免后续第一次上云时再靠聊天记录回忆

## 2026-04-18

- 正向底座的 `scrollToBottom(false)` 非动画路径已收紧：如果最后一条底边仍未进入可视区，会先做一次 hard bottom reposition，再交给 `alignChatListBottom()` 做工作线精修
- 首屏首次贴底 effect 改为等 `startupLayoutReady` 后持续重试，只有命中底部容差才把 `initialBottomSnapDone` 记完成，避免“一次滚完就关门”
- 启动窗口允许临时参考一次 realtime composer geometry，把首屏最后几像素压准到工作线；命中后立即退回静态口径，不把历史区输入框联动带回来
- `LazyListState` 启动若本地有历史，会先从最后一条历史消息起步，并关闭 `rememberSaveable` 的旧滚动恢复，减少首几秒落在长文本中段造成的重影 / 闪动
- `scrollToBottom(false)` 与 finalize 收口都不再使用 `requestScrollToItem(lastIndex)` 这类 top-anchor，把“跳到长文本开头”改为复用现有静态底线主链
- `ChatScrollCoordinator.kt` 的 streaming AutoFollow 改为按“当前内容底边 - 工作线底边”的单次 delta 做 `scrollBy` 微调，减少 fake streaming 过程中的重叠闪烁
- 首屏显示链继续清理：列表 / 欢迎语 reveal 只再依赖 hydration barrier，stale streaming runtime 在冷启动 reset 时主动清空，继续收口首屏白屏与空列表

## 2026-04-17

- `ChatScreen.kt` 的消息列表与 composer 改为 `SubcomposeLayout` 共享 measure 宿主，同拍产出底部 reserve，不再完全依赖 `composerTopInViewportPx` 异步回写链
- `sendUiSettling` 收紧成只覆盖发送起步同步窗口，专门压“发送长文本后输入框有时不回缩”的时序竞态
- `ChatStreamingRenderer.kt` 统一最外层宿主、删除 fresh-line lock 预览、把 block 间距改成独立 `Spacer`，继续减少 streaming 下掉与完成态微重排
- 两阶段 finalize 进一步收紧为“等 settled fresh bounds 到位后再切 streaming 状态”，后台期间暂停等待，继续压完成后上跳和底部留白
- 根 `AGENTS.md` 新增已修复问题禁改清单与会诊稿规则，要求外部会诊稿默认自包含代码片段、限制条件和已排除项

## 2026-04-16

- 发送链撤掉 `withTimeoutOrNull` 延后收口实验，回到“输入框瞬间清空回缩 + placeholder 同事务插入”的单一发送主链
- 聊天 UI 当前热点与 runbook 开始沉淀进仓库，`scripts/check_project_memory.py` 与 CI 已接入项目记忆检查
- streaming block 改为 unified host，外壳 key 收口为稳定 block index，减少流式阶段 remount
- 普通 idle / 历史浏览状态下不再让列表长期追实时 composer 几何，避免历史区和输入框重新联动

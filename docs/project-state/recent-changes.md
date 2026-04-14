# 近期重要变更

说明：本文件默认只保留最近 20 条重要变更；更早内容以 git 历史和 ADR 为准。

## 2026-04-14

- 建立仓库内长期记忆骨架：新增 `docs/project-state`、`docs/adr`、`docs/runbooks`
- 根 `AGENTS.md` 增加“项目记忆机制”，要求 Codex 默认读取并自动维护状态、风险、待决策、变更记录和 runbook
- 新增 `app/AGENTS.md` 与 `server-go/AGENTS.md`，让新窗口进入子系统时自动获取局部口径
- 新增 ADR-0001，明确项目长期交接依赖“仓库内结构化记忆”，而不是依赖聊天历史或模型自带记忆
- Compose 聊天底座继续收敛：发送起步锚点改为按 assistant waiting 宿主真实可见底边对齐工作线，删除旧的 waiting 高度估算链
- 滚动状态机收紧用户浏览判定：仅真实手指拖动才进入 `UserBrowsing`，程序化滚动尾帧不再误杀 `AutoFollow`
- 新增 `scripts/check_project_memory.py` 并接入 Android CI：关键真相文件变更但未同步更新仓库记忆时直接报警
- `UserBrowsing -> AutoFollow` 回接条件继续收紧：只有生成行真实回到工作线命中带后才重新接回，避免用户上滑查看历史时被主链往下拽
- 修复项目记忆检查在 GitHub Actions 中的误报：`actions/checkout` 改为 `fetch-depth: 0`，避免浅克隆下 `base..head` revision range 无法解析
- 发送起步锚点继续收紧：waiting 阶段改为量“小球所在首行宿主底边”而不是内层呼吸球本体 bottom，减少 waiting/首字 streaming 切换时的锚点跳变
- 本地 fake streaming 的生命周期收口改为同步执行：`ON_PAUSE / ON_STOP` 直接在主线程完成 completed 消息落盘与 draft 清理，减少“切后台再回来只剩半截文本且列表卡住”的竞态窗口
- `UserBrowsing` 回接条件继续收紧：不再按泛底部容差直接接回，而是要求生成行真实回到工作线附近，减少用户上滑时被 AutoFollow 过早往下拽
- 发送起步目标线改为优先使用单行收口后的稳定 composer 保留高度，减少长文本发送时“小球先顶高再掉回去”的锚点漂移
- 输入框已收口成单行后，列表底部保留高度改为继续优先使用稳定单行高度，不再马上切回实时 `composerTop` 测量，减少锚点刚对齐后又被底部几何二次改写

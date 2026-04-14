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
- 发送起步锚点继续收紧：由 assistant 内容宿主底边改为 waiting 小球本体底边，避免 waiting/首字 streaming 切换导致锚点忽高忽低

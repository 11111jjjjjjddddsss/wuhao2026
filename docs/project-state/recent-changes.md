# 近期重要变更

说明：本文件默认只保留最近 20 条重要变更；更早内容以 git 历史和 ADR 为准。

## 2026-04-14

- 建立仓库内长期记忆骨架：新增 `docs/project-state`、`docs/adr`、`docs/runbooks`
- 根 `AGENTS.md` 增加“项目记忆机制”，要求 Codex 默认读取并自动维护状态、风险、待决策、变更记录和 runbook
- 新增 `app/AGENTS.md` 与 `server-go/AGENTS.md`，让新窗口进入子系统时自动获取局部口径
- 新增 ADR-0001，明确项目长期交接依赖“仓库内结构化记忆”，而不是依赖聊天历史或模型自带记忆

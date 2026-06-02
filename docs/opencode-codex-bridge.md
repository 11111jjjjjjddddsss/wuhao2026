# OpenCode 接手提示词

本文件只给 OpenCode 做加载入口，不是新的项目真相。任何冲突都以仓库根 `AGENTS.md`、当前代码和 `docs/project-state` 为准。

OpenCode 是本项目在 Codex / GPT 不可用时的备选接手工具。它不能依赖聊天记录或模型自带记忆，必须把仓库文件当作唯一可持久化记忆来源。

本接手规则不绑定任何固定模型。无论当前使用哪家模型，只要在本仓库工作，都必须先加载并服从这些项目文件。

重要身份规则：文档里的“小米 / MiMo”只表示历史外部会诊对象，不代表当前 OpenCode 模型身份。当前本机 OpenCode 接入的是 My Claude / Claude 兼容中转站；回答“你是谁 / 你是哪个模型”时，不要自称 MiMo、小米或 `mimo-v2.5-pro`。

## 开始任务前

- 先读根 `AGENTS.md`，再读当前目录命中的局部 `AGENTS.md`。
- 必读项目记忆：`docs/project-state/current-status.md`、`docs/project-state/open-risks.md`、`docs/project-state/pending-decisions.md`、`docs/project-state/recent-changes.md`。
- 按任务再读相关 ADR、runbook、代码文件和官方资料。能联网核对就联网核对，不凭印象硬改。
- 先确认当前唯一主链、旧方案是否仍并存、旧方案是否会影响现在逻辑，再决定怎么动手。

## 动手原则

- 用中文和用户沟通。
- 先看代码、理解后再改；最小改动，优先复用现有逻辑。
- 不假设不存在的接口、文件、字段或路径。
- 不恢复已废弃方案，不让新旧方案长期并存。
- 不修改无关代码，不顺手重构。
- 不回滚用户已有改动；遇到脏工作区先分清哪些是自己改的。
- 不使用破坏性 git 命令，例如 `git reset --hard`、`git checkout --`、`git clean`，除非用户明确要求。

## 修改完成后

- 自查影响范围：改了什么、为什么这么改、有没有旧方案残留、会不会连带破坏其他逻辑。
- Android 改动后运行 `./gradlew.bat :app:compileDebugKotlin`。
- Go 后端改动后运行 `cd server-go && go build ./...`。
- 如果改动影响当前状态、风险、待决策、方案取舍、运维入口或项目记忆，必须同步更新对应的 `docs/project-state`、ADR 或 runbook。
- 每次仓库改动完成后提交本地 git，并推送到 `origin/master`。

## 失败与会诊

- 同一个问题连续两轮修改仍未解决，第三轮开始前先暂停，整理会诊稿。
- 会诊稿必须自包含：相关文件路径、关键函数或状态名、必要代码片段、现象、已排除项、怀疑点、限制条件。
- 外部会诊对象以用户当次指定为准；历史“小米 / MiMo”口径只表示曾经的会诊目标，不代表当前 OpenCode 模型身份。收到任何外部建议后必须先对照当前代码核验，不能直接照抄。

## 项目底线

- 项目名：农技千查。
- Android 客户端在 `app`，Go 后端在 `server-go`。
- 后端是业务唯一真相来源；前端只负责 UI、输入、展示和交互。
- 产品是农业技术顾问型 AI，只给农业技术参考建议，不做绝对诊断。
- 聊天 UI 当前稳定主链是单一正向 `LazyColumn`，不要恢复反向列表、overlay、active-zone、小分割、raw delta 或旧滚动补偿链。

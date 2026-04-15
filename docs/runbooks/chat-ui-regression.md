# Chat UI 回归与接手 Runbook

最后更新：2026-04-16

## 目的

为新窗口、外部会诊前自查、以及每次聊天 UI 调整后的快速回归提供统一入口，避免每个人脑内的“复现步骤”和“修复标准”不一致。

## 当前接手建议

- 先读根 `AGENTS.md`
- 再读 `docs/project-state/current-status.md` 的“当前调试焦点”
- 只在需要看全量背景时再回看 `open-risks.md`、`recent-changes.md`

## 当前热点问题

### 1. 发送瞬间整块消息区上下抖

- 当前最可疑位置：`ChatScreen.kt` 的 `commitSendMessage(...)`
- 当前判断：发送事务里“先收口输入区、后插消息回底”仍可能导致底部地基在对齐前先塌陷
- 下一刀重点：保留发送门禁和 streaming 起始态，只后移 `prepareComposerCollapse(...)`、清空文本、清焦点、收键盘

### 2. Streaming 过程中正文往下掉一下再弹回

- 当前最可疑位置：`ChatStreamingRenderer.kt` 的 `RendererAssistantStreamingContentImpl(...)`
- 当前判断：`completedModels.forEachIndexed { ... }` 和 `activeModel?.let { ... }` 双分支在 flush 时仍可能触发块级宿主切换
- 下一刀重点：参考 `RendererAssistantMarkdownContentImpl(...)` 的单循环结构，改成块级同构宿主

## 回归场景

### A. 历史区聚焦输入框不联动

- 步骤：打开已有历史消息的聊天页，停在历史区，点击输入框唤起键盘
- 预期：现有历史消息不应跟着整体上抬
- 若失败，优先排查：`ChatScreen.kt` 中工作线 / `recyclerBottomPaddingPx` 是否在 idle 仍引用实时 `composerTop`

### B. 底部 idle 聚焦输入框尽量不联动

- 步骤：停在底部最新消息附近，点击输入框
- 预期：消息区不应出现明显整体位移；允许极轻微系统级抖动，但不允许再次回到“整块跟着输入框跑”
- 若失败，优先排查：`ChatScreen.kt` 中 `listShouldTrackRealtimeComposerGeometry`

### C. 发送 1 个字不应上下弹

- 步骤：输入单个字，点击发送
- 预期：用户消息与 waiting 小球应直接落位，不应先上再下或整块文本区明显弹动
- 若失败，优先排查：`ChatScreen.kt` 的发送事务顺序

### D. 长文本 streaming 应持续向上长

- 步骤：触发一段包含段落/列表/标题的较长 streaming 回复
- 预期：正文应围绕同一底边持续向上生长；不应在 flush 新 block 时“往下掉一下再弹回”
- 若失败，优先排查：`ChatStreamingRenderer.kt` 的 completed / active block 交接

### E. 切后台再回来不应回灌半截 draft

- 步骤：streaming 过程中切后台，再切回前台
- 预期：不应重新看到半截旧 draft 回灌到屏幕；后台期间若已收口，应保持 completed 结果
- 若失败，优先排查：本地 fake streaming 的 pause/stop 收口与本地 draft 清理

## 会诊提示

- 外部会诊对象默认看不到本地仓库
- 发给 Gemini / Claude 的内容必须自包含：问题说明 + 关键代码片段 + 已排除项 + 限制条件
- 不要只发文件名，不要只发抽象症状

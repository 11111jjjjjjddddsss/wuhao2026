# ADR-0002: 用 Bottom-Anchored Overlay 承接生成中的 assistant 正文

日期：2026-04-22

状态：已决策，待实现

## 背景

当前聊天页使用正向 `LazyColumn(reverseLayout = false)`。发送起步、小球锚点、首屏贴底、输入框 reserve、两阶段 finalize、静态贴底和大部分滚动主链已经按真机反馈收口。

仍未彻底解决的三个体感问题是：

- AI streaming 长段落换行时，工作线下方仍能看到下一行字的轻微残影 / 冒头。
- AI 完成输出后，尾部收口偶尔轻微抖一下。
- 每一行往上排版时，仍有一点轻微发抖，不够像成熟聊天 App 那种天然贴底向上生长。

这些问题的共同根因是：**正在生成的 assistant 正文仍作为 `LazyColumn` item 在列表内动态长高**。正向列表天然是 top-down 测量；item 变高会先改变列表内布局，再由 `dispatchRawDelta`、bounds、follow、finalize 归位等链路尝试补偿 bottom anchor。这类补偿已经可以把问题减轻，但无法做到 100% 无残影、无尾部微抖。

已经试过并排除的局部方案：

- draw-phase clip / mask：会裁掉真实文字，不接受。
- renderer 叶子 gate / fresh-line preview / activeLine lock：会憋字、整块出现或高度塌陷，不接受。
- 32ms time hold：仍有残影。
- `requestScrollToItem(index, currentTop - deltaPx)` 行锚定：残影减轻，但引入尾部收口抖动，已整体回退。
- 硬等 bounds 完整上移 `requiredDeltaPx` 再 release：会卡住吐字，一行一卡，已回退。
- 继续调 `dispatchRawDelta` delta：只能减轻，不能根治。

## 决策

采用 **Bottom-Anchored Streaming Overlay** 作为下一轮聊天 UI 生成态改造方向。

核心原则：

- `LazyColumn` 继续负责历史消息、用户消息、已完成 assistant、首屏贴底、用户手动滑动、回到底部按钮。
- 正在生成的 assistant 正文，在用户停留底部观看时，临时从 `LazyColumn` 中拿出来，放到一个浮在列表上方的 Overlay 层。
- Overlay 底边固定在输入框上方的工作线，内部继续复用现有 `ChatStreamingRenderer`。文本变高时只能向上长，不会向工作线下方冒头。
- 发送起步小球锚点不改。用户消息和 assistant placeholder / 小球仍按当前 `LazyColumn` 起步链进入列表，继续复用当前 `requestScrollToItem(index, offset)` 发送锚点。
- 用户手动上滑时采用简单版策略：立即把当前 streaming 内容交回 `LazyColumn`，关闭 Overlay，进入 `UserBrowsing`；本轮后续不再切回 Overlay。
- 生成完成时，把完整 assistant 内容写回 `LazyColumn`，再关闭 Overlay；交接这一拍是本方案最需要精修的风险点。

这不是重写整条滚动链，而是把“生成中的动态正文”从滚动链中剥离出来，让 `LazyColumn` 不再承担 bottom-up 高频生长动画。

## 非目标

本 ADR 不要求：

- 重写 `ChatScrollCoordinator` 整体状态机。
- 改小球首发锚点。
- 改用户消息、历史消息、首屏贴底、输入框工作线或底部 reserve 主链。
- 恢复旧 `RecyclerView` / reverseLayout / footer probe / preview lock / renderer gate / clip / 32ms hold / requestScrollToItem 行锚定。
- 在用户浏览历史时继续悬浮一个大面积 streaming overlay 遮挡历史内容。

## 第一刀实施边界

第一刀目标：只覆盖“用户未打断、停留底部看 AI 生成”的主场景。

建议新增状态：

```kotlin
private enum class StreamingLocation {
    OVERLAY,
    LAZY_COLUMN
}
```

第一刀期望行为：

- `commitSendMessage()` 发送起步后，`streamingLocation = OVERLAY`。
- `LazyColumn` 内仍保留 assistant placeholder / waiting 小球锚点；Overlay 模式下不在列表 item 内渲染 streaming 正文。
- `onAdvance` 在 Overlay 模式下直接更新 `streamingMessageContent` / fresh range / reveal buffer，不再走 `resolveStreamingWrapGuardDecision(...)`、`dispatchRawDelta(...)` 或 wrap guard hold。
- 页面外层增加 `StreamingOverlayHost`，位置与当前工作线一致，宽度和 `chromeMaxWidth / listHorizontalPadding` 口径一致，内部复用 `ChatStreamingRenderer(renderMode = Streaming/Waiting)`。
- Overlay 模式下 `ChatScrollCoordinator` 不应再对 streaming 正文执行 follow delta，因为正文不在列表内长高。
- 生成完成时先保证 completed assistant 写回 `messages`，再关闭 Overlay。若第一刀无法做到完美交接，至少要让交接路径集中，方便第二刀精修。

## 后续分刀

第二刀：用户上滑交回 `LazyColumn`。

- 一旦用户拖动进入 `UserBrowsing`，把当前 `streamingMessageContent` 写回列表内 active assistant item，`streamingLocation = LAZY_COLUMN`。
- 本轮后续不再回 Overlay，避免 overlay 与列表之间反复交接引入新抖动。

第三刀：完成态交接精修。

- 重点验证 Overlay 内容和 completed item 在交接前后的屏幕底边是否重合。
- 如仍有尾部微抖，再考虑短暂双渲染重叠、同帧隐藏 Overlay、或利用 completed fresh bounds 后做一次精确静态底线归位。
- 不允许恢复已排除的 requestScrollToItem 行锚定 / hard bounds wait / clip。

## 需要禁用或隔离的旧链路

当 `streamingLocation == OVERLAY`：

- `resolveStreamingWrapGuardDecision(...)` 应直接跳过或不调用。
- `streamingWrapGuardTargetLineCount` 应保持 `-1`。
- 不调用 `listState.dispatchRawDelta(...)` 做 streaming line pre-scroll。
- 不让 `ChatScrollCoordinator` 依据 overlay 的 content bottom 做 `followStreamingByDelta(...)`。
- 列表内 active assistant item 不应同时渲染同一份 streaming 正文，避免 overlay 与 LazyColumn 双画。

当 `streamingLocation == LAZY_COLUMN`：

- 保持当前基线：reveal-layer wrap guard + active block pre-measure + strict follow gate 仍可作为用户浏览态 / 回退态兜底。

## 验收项

第一刀后必须真机验证：

- 发送起步小球仍稳定落在工作线。
- 用户不滑、盯着底部看生成时，下一行不再在工作线下方冒头。
- 每行向上排版时，不再出现明显的列表补滚式轻抖。
- 生成完成后，尾部收口不比当前基线更差；若仍轻微抖，记录到第三刀处理。
- 用户手动上滑时不抢手、不遮挡历史。
- 输入框 reserve、placeholder、发送禁用逻辑不回退。
- completed assistant 正常落盘，切后台 / 杀进程后仍能恢复。

## 回滚策略

Overlay 改造必须分刀提交。若第一刀引入明显回归，可整体 revert Overlay 相关提交，回到当前 `2041651 Show composer placeholder during streaming` 之后的基线。不要在坏基线上继续叠加新旧双方案。

# ADR-0005 聊天 Markdown 渲染器先学套路不整包替换

日期：2026-06-18

## 状态

Accepted

## 背景

主聊天需要在 Android Compose 里同时满足：

- AI 一边生成一边渲染，不等完成后整段展示。
- 正向 `LazyColumn` 工作线、AutoFollow、用户上滑浏览和两阶段 finalize 不能被第二套渲染树打断。
- 完成态、streaming 和远端 snapshot 历史回放显示一致。
- 表格在手机窄屏上要可读，当前采用横向滚动的正常表格，复制按钮在表格上方，不再使用纵向分组卡片。
- 复制链要区分“复制可见文本”“全文复制”和“复制表格”，不能复制半截 streaming 内容。

用户提出“不能直接用网上成熟渲染组件，也要学习它们的套路”。本轮联网校准了常见候选：

- Markwon：成熟 Android Markdown 库，基于 commonmark-java，渲染到 Android `TextView` / Spannable，不是原生 Compose 主链。
- commonmark-java：成熟 parser / AST 库，当前可作为后续解析 POC 候选，但它本身不提供 Compose UI。
- mikepenz multiplatform-markdown-renderer：Compose Multiplatform 方向更接近，但当前版本和 Kotlin / Compose 版本兼容性、streaming 交互、复制链、表格窄屏策略仍需 POC。
- jeziellago compose-markdown：Compose 外壳内包 TextView 渲染，且表格快速更新等问题仍需验证，不适合直接接入当前主链。

## 决策

上线前不整包替换当前 `ChatStreamingRenderer.kt` 主链。

可以学习并逐步吸收成熟库套路：

1. 解析层尽量向 AST / token / block model 靠拢，减少散落正则。
2. 明确区分 stable blocks 和 active streaming block。
3. 表格、链接、行内代码、标题等结构单独建模，再交给 Compose 组件渲染。
4. 复制 / 点击 / 选择等交互以“整条消息是否 settled”为边界，不能只看局部 block 是否已经稳定。
5. 后续如做 POC，优先用真实历史回复样本离线对比 commonmark-java 或 Compose renderer 的解析结果，不先接入主聊天 UI。

## 结果

当前继续保留轻量 renderer，因为它已经接住项目最敏感的运行时条件：streaming、正向列表工作线、表格窄屏展示、复制链和远端历史回放。

本轮直接落地的收口：

- `ChatScreen.kt` 删除旧 Markdown parser / cache / block UI 残留，避免两套渲染口径并存。
- 表格“复制表格”按钮必须等整条 AI 消息完成后才可用，避免 streaming 中复制到半截表格。
- 今日农情 snapshot 区分“确定没有展示项”和“展示项读取失败”，读取失败时 Android 不清掉当前已显示的农情。

后续如果继续增强渲染，不应从“换库”开始，而应从“离线 parser POC + 样本对比 + 小步迁移”开始。

## 参考

- commonmark-java GitHub / Maven Central
- Markwon GitHub / 官方文档
- mikepenz multiplatform-markdown-renderer GitHub / Maven Central
- jeziellago compose-markdown GitHub

# Android 子树执行补充

本文件只在 `app` 目录树内生效，用于补充 Android 客户端的局部执行口径；主规则仍以根 [AGENTS.md](D:/wuhao/AGENTS.md) 为准。

## 开始前先读

1. 根 [AGENTS.md](D:/wuhao/AGENTS.md)
2. [docs/project-state/current-status.md](D:/wuhao/docs/project-state/current-status.md)
3. [docs/project-state/open-risks.md](D:/wuhao/docs/project-state/open-risks.md)
4. [docs/project-state/recent-changes.md](D:/wuhao/docs/project-state/recent-changes.md)
5. 本次任务相关的聊天 UI 参考文档

## 当前客户端真相

- Android 客户端唯一主线在 `app`
- 当前聊天页为 Jetpack Compose，不再依赖 WebView 模板页面
- 前端只负责 UI、输入、展示、交互；业务规则以后端为准
- 主对话链路是后端 `/api/chat/stream` SSE
- 单轮最多 4 张图；图片、上下文、会员、扣费逻辑以后端口径为准

## 执行要求

- 先复用现有状态机、协调器、渲染器，不新开第二套并行链路
- 若发现旧 UI 方案残留影响当前逻辑，需同次明确清理或标注废弃，避免新老并存
- 若更改聊天 UI 真实交互口径，除代码外还应同步更新根 [AGENTS.md](D:/wuhao/AGENTS.md) 与相关项目记忆文件
- Android Compose UI、滚动链、IME、输入框、工作线、渲染时序等问题连续多轮未收口时，默认优先整理成发给小米 / MiMo 的自包含会诊稿；会诊稿必须直接贴关键代码片段、状态名、调用顺序、已排除方案和限制条件，不能只写“看仓库 / 看 ChatScreen.kt”
- Android 改动完成后执行：`./gradlew.bat :app:compileDebugKotlin`

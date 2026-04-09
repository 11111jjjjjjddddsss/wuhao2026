# Chat UI 动态交互逻辑（历史归档）

本文件不再承担聊天滚动主规则职责。

当前唯一 active 规则只认：
- [AGENTS.md](D:/wuhao/AGENTS.md) 第 20.4 节
- [AGENTS.md](D:/wuhao/AGENTS.md) 第 20.5 节

## 当前代码入口

- `app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt`
- `app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt`
- `app/src/main/kotlin/com/nongjiqianwen/ChatStreamingRenderer.kt`
- `app/src/main/kotlin/com/nongjiqianwen/ChatRecyclerViewHost.kt`

## 当前简化口径

- 用户消息和 assistant 消息先按正常消息流从上往下排。
- waiting 小球必须贴着上一条用户消息起步，不直接跳到工作线。
- 正文从 waiting 位置继续往下长，只有正文尾部接近工作线后才进入 `AutoFollow`。
- 用户拖动立即让权，回到底部工作线附近后才允许恢复自动跟随。
- 完成态与静态贴底围绕同一条工作线附近目标线收口，不再额外保留第二条更低的静态底线。

## 已失效的旧口径

旧 Compose / LazyColumn 时代的滚动术语和补滚链路都已归档，不再是 active 实现。

后续如果本文件和代码不一致，以 [AGENTS.md](D:/wuhao/AGENTS.md) 为准，并优先同步修正本文件。

# Chat UI 动态交互逻辑（历史归档）

本文件只保留历史说明，不再承担聊天滚动主规则职责。

当前唯一 active 真相只认：
- [AGENTS.md](D:/wuhao/AGENTS.md) 第 6 节：Chat UI 总原则
- [AGENTS.md](D:/wuhao/AGENTS.md) 第 7 节：当前 Compose 列表滚动链唯一真相

## 当前运行时代码入口

- `app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt`
- `app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt`
- `app/src/main/kotlin/com/nongjiqianwen/ChatStreamingRenderer.kt`
- `app/src/main/kotlin/com/nongjiqianwen/ChatRecyclerViewHost.kt`

## 当前已确认的口径

- 聊天列表运行时底座已经是纯 Compose `LazyColumn`
- `ChatRecyclerViewHost.kt` 这个文件名只是历史命名残留，运行时不再是 `RecyclerView`
- 发送起步、AutoFollow、完成态收口、用户浏览、首次进入贴底，都以 [AGENTS.md](D:/wuhao/AGENTS.md) 第 7 节为准
- 任何旧 `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout / scrollToPositionWithOffset` 说法，都只属于 git 历史，不再是 active 实现

## 使用规则

- 如果本文件和代码不一致，以 [AGENTS.md](D:/wuhao/AGENTS.md) 为准
- 如果本文件和 [AGENTS.md](D:/wuhao/AGENTS.md) 不一致，优先修 [AGENTS.md](D:/wuhao/AGENTS.md)
- 后续不再把新的 active 滚动规则补写到本文件，只在 [AGENTS.md](D:/wuhao/AGENTS.md) 收敛

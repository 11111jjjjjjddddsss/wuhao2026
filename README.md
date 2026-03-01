# 农技千问

当前客户端入口为原生 Android（Jetpack Compose），不再依赖 WebView 模板页面。

## 客户端
- 启动页：`MainActivity`（Compose `ChatScreen` 占位）
- 主对话链路：后端 `/api/chat/stream`（SSE）
- 输入边界：单次最多 4 张图（有图必须带文字）；文本长度限制按客户端当前规则

## 锚点真源
系统锚点唯一真源在 `server`：
1. 优先 `SYSTEM_ANCHOR` 环境变量
2. 否则读取 `server/assets/system_anchor.txt`

两者同时缺失时服务端会 fail-fast 启动失败。

## 构建
- Android Kotlin 编译：`./gradlew :app:compileDebugKotlin`
- Server 构建：`cd server && npm ci && npm run build`
- 乱码扫描：`python3 scripts/check_mojibake.py`

# 农技千问

当前客户端入口为原生 Android（Jetpack Compose），不再依赖 WebView 模板页面。

## 客户端
- 启动页：`MainActivity`（Compose `ChatScreen` 占位）
- 主对话链路：后端 `/api/chat/stream`（SSE）
- 输入边界：单次最多 4 张图（有图必须带文字）；文本长度限制按客户端当前规则

## 提示词真源
- 主对话锚点：`server-go/assets/system_anchor.txt`
- B 层摘要提示词：`server-go/assets/b_extraction_prompt.txt`
- C 层摘要提示词：`server-go/assets/c_extraction_prompt.txt`

主对话锚点缺失或为空时，服务端会 fail-fast 启动失败。  
B/C 提示词会在启动时预检查并打日志；若运行时该层失败，只影响对应摘要层并保留重试。

## 构建
- Android Kotlin 编译：`./gradlew :app:compileDebugKotlin`
- Go 后端构建：`cd server-go && go build ./...`
- Go 后端启动：`cd server-go && go run ./cmd/server`
- Go Docker 镜像：`docker build -f server-go/Dockerfile .`
- 乱码扫描：`python3 scripts/check_mojibake.py`

## 后端迁移
- 当前 Go 后端目录：`server-go`
- 当前仓库唯一后端目录：`server-go`
- `server-go` 自带 `assets`、`migrations` 与 `scripts`，用于保持提示词、数据库结构和运行入口独立闭环

# 农技千问

农业指导型 AI 应用（Android + WebView，通义千问多模态）。

## 构建

- Debug：`./gradlew assembleDebug` 或 `build_apk.bat`
- 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 运行时关键 assets（必须存在且可读取）

- `app/src/main/assets/system_anchor.txt`：系统锚点（由 `SystemAnchor.kt` 注入 system role）
- `app/src/main/assets/b_extraction_prompt.txt`：B 层摘要提取 prompt（由 `BExtractionPrompt.kt` 加载）
- `app/src/main/assets/gpt-demo.html`：WebView 对话页

除上述 assets 外，其余文档不参与 prompt/messages 构造。

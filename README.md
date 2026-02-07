# 农技千问

农业指导型 AI 应用（Android + WebView，通义千问多模态）。

## 构建

- **Debug**：`./gradlew assembleDebug` 或 `build_apk.bat`  
- 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 运行时关键 assets（必须存在且被读取）

- `app/src/main/assets/system_anchor.txt` — 系统锚点，SystemAnchor.kt 加载，注入为 system role  
- `app/src/main/assets/b_extraction_prompt.txt` — B 层摘要提取 prompt，BExtractionPrompt.kt 加载  
- `app/src/main/assets/gpt-demo.html` — WebView 对话页

除上述 assets 外，其余文档不参与 prompt/messages 构造。

## 联网搜索冒烟用例（3+1）

验收时过滤 `adb logcat -s QwenClient:D` 中 `P0_SMOKE` 行，确认与下表一致。

| 场景 | 预期 | P0_SMOKE 关键字段 |
|------|------|-------------------|
| **1) 联网成功** | 灰块出现、URL 可点；Stop 可取消 | `phase=2 tool_calls=true show_tool_block=true cancelled=false` |
| **2) 不触发联网** | 无灰块，正常回复 | `phase=0 tool_calls=false show_tool_block=false cancelled=false` |
| **3) 联网失败/未命中** | 无灰块，正文正常或兜底 | `phase=2 tool_calls=true show_tool_block=false cancelled=false` |
| **4) 二次阶段 Stop** | 显示「网络波动，已停止」、UI 恢复 | `phase=2 tool_calls=true show_tool_block=false cancelled=true` |

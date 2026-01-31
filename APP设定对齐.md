# 农技千问 App 设定（助理对齐用）

## 一、产品定位
- 农业指导型 AI 应用，非确诊、非权威裁决
- 核心能力：视觉识别 + 推理建议（参考性建议）

## 二、当前已实现

| 模块 | 状态 | 说明 |
|------|------|------|
| 纯文本对话 | ✅ 已就绪 | SSE 流式输出，OpenAI 兼容接口，qwen3-vl-flash |
| 图片上传 | 🔒 协议冻结 | UPLOAD_BASE_URL 为空则全部 fail，禁发；配置后 POST /upload，最多 4 张 |
| UI 模板 | ✅ 单模板 | `gpt-demo.html`，ChatGPT 绿白风格，手机宽度容器 |
| AB/会员 | 占位 | request_id/user_id/session_id 留空，无真实逻辑 |

## 三、阶段冻结
- 文字核心链路：窗口化、加载更多、SSE 节流、后台可取消
- 图片：协议/状态机/上传 URL 形态不改
- 查证模块：**已删除，暂不做**

## 四、配置与构建
- 模型接口：`local.properties` 中 `API_KEY=sk-xxx` 或环境变量 `BAILIAN_API_KEY`（禁止在 gradle.properties 填真实 key）
- 图片上传：`gradle.properties` 中 `UPLOAD_BASE_URL`（空=不上传）
- 打包：`build_apk.bat`，输出 `app/build/outputs/apk/debug/app-debug.apk`

## 五、待办优先级
1. **P0**：纯文本验收（连续 10 条不崩、断网可恢复）
2. **P1**：图片闭环（配置后端后验收，不动主链路）
3. **P2**：服务端常驻 API、会员/鉴权（等指挥）

## 六、禁止事项
- 未讨论的功能、模块、隐性逻辑
- 动 AB 核心、接口形态、登录/会员/查证（查证已取消）

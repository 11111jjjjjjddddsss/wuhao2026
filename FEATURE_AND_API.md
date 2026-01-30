# 当前功能清单 + 待办列表 + 关键接口字段表

## 一、当前功能清单

### 纯文本链路（本周优先级，已就绪）
- **接口**：OpenAI 兼容 `/compatible-mode/v1/chat/completions`，`stream=true`
- **真流式 SSE**：逐行解析 `data:`，拼接 `delta.content`，前端 chunk 直接 append
- **错误兜底**：超时/断流/403/429/5xx 给用户可理解提示（无堆栈），UI 可恢复可输入
- **日志**：`requestId`、最终 payload（脱敏）、SSE 事件、DONE、`finish_reason`、耗时

### 图片模块（协议已冻结，闭环暂缓）
- **UI 与状态机**：4 张上限，转圈/对勾/叉/重试/删除，发送键 gating（uploading/fail 禁发）
- **UPLOAD_BASE_URL 为空**：所有图片保持 fail，提示「未配置上传服务」，禁止发送（不降级纯文本）
- **UPLOAD_BASE_URL 非空**：`POST {UPLOAD_BASE_URL}/upload`，multipart 字段 `file`；成功 200 + `{"url":"https://..."}`，失败非 200 + `{"error":"..."}`
- **压缩链路**：EXIF 矫正 → 长边 1600 → JPEG q=75（不裁剪）
- **禁止**：静默截断、降级发送、提示用户手动压缩、硬大小阈值

### AB 层骨架（仅占位）
- **客户端**：`request_id` / `user_id` / `session_id` 已留（先填 UUID），供后期会员/配额/审计
- **服务端接口形态占位**：`/chat`（文本）、`/chat/stream`（SSE）、`/usage`（配额）、`/auth`（鉴权）
- **数据结构占位**：`a_messages`（最近 N 轮）、`b_summary`（字符串）、`meta`（作物/地区/时间等）；不做 B 层摘要/锚点真实逻辑

---

## 二、待办列表（按优先级）

| 优先级 | 事项 | 说明 |
|--------|------|------|
| P0 | 纯文本验收 | 连续发 10 条不崩，SSE 持续输出，断网/超时可恢复 |
| P0 | 图片未配置验收 | 未配置 UPLOAD_BASE_URL 时，图片显示失败且发送键禁用 |
| P1 | 图片闭环（后期） | 配置后端 + ngrok/公网 URL 后，仅改配置与 upload-server，不动 App 主链路 |
| P2 | 服务端常驻 API | 接入 /chat、/chat/stream、/usage、/auth，鉴权与配额 |
| P2 | 会员与 AB 层 | B 层摘要/锚点注入、会员规则（等指挥） |

---

## 三、关键接口字段表

### 1. 当前模型请求（DashScope 兼容）

| 字段 | 类型 | 说明 |
|------|------|------|
| `requestId` | string | 客户端生成，日志与审计（如 `req_<uuid>`） |
| `model` | string | 固定 `qwen3-vl-flash` |
| `stream` | boolean | 固定 `true` |
| `messages` | array | `[{ role, content }]`，content 为数组：图在前（`type: image_url`）、`text` 在后 |
| 图数量 | — | 最多 4 张，有图必有文字 |

### 2. 图片上传协议（已冻结）

| 项目 | 说明 |
|------|------|
| URL | `POST {UPLOAD_BASE_URL}/upload` |
| 请求 | `multipart/form-data`，字段名 `file` |
| 成功 | HTTP 200 + JSON 根级 `{"url":"https://..."}` |
| 失败 | HTTP ≠200 + JSON 根级 `{"error":"..."}` |
| UPLOAD_BASE_URL 为空 | 不上传，统一 fail，提示「未配置上传服务」，禁发 |

### 3. 服务端常驻 API 占位（未实现）

| 路径 | 用途 |
|------|------|
| `/chat` | 文本对话 |
| `/chat/stream` | SSE 流式对话 |
| `/usage` | 配额查询 |
| `/auth` | 鉴权占位 |

### 4. 客户端标识（占位）

| 字段 | 说明 |
|------|------|
| `request_id` | 每次请求唯一（当前由 ApiConfig.nextRequestId()） |
| `user_id` | 用户 ID（先 UUID，后期会员） |
| `session_id` | 会话 ID（先 UUID，后期审计） |

### 5. AB 层数据结构占位

| 字段 | 类型 | 说明 |
|------|------|------|
| `a_messages` | list | 最近 N 轮对话 |
| `b_summary` | string | B 层摘要（占位） |
| `meta` | object | 作物/地区/时间等（占位） |

---

**变更范围**：仅稳定性/接口占位/日志，未改 AB 层核心规则文档内容。  
**云端**：CNB + DIFF 推送。

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
- **声明**：AB 仅占位，不生效、不入 prompt、不入 payload。模型请求的 messages/content 仅由 `QwenClient.callApi` 内 `userMessage` + `imageUrlList` 拼装，无 AB 字段混入。

**阶段0 证据（纯文本请求 payload 与引用点）**  
- 纯文本时最终 payload 形态（脱敏）：`{"model":"qwen3-vl-flash","stream":true,"messages":[{"role":"user","content":[{"type":"text","text":"用户输入内容"}]}]}`  
- 代码引用点：`QwenClient.kt` 第 62–101 行构建 `requestBody`，仅含 `model`、`stream`、`messages`；`messages[0].content` 仅由 `imageUrlList` + `userMessage` 拼装，无 `a_messages`/`b_summary`/`meta`/`user_id`/`session_id`。`ApiConfig` 仅被 `ModelService.getReply` 用于生成 `requestId`（日志），不参与 payload。`ABContextPlaceholder` 全库无引用。

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

---

## 四、阶段验收证据（本轮纯文本体验与稳定性）

### 阶段0：AB 占位不污染主链路
- 引用点：`ApiConfig` 仅 `ModelService.getReply` 用于 `requestId`（日志）；`ABContextPlaceholder` 全库无引用。payload 仅由 `QwenClient.kt` 第 62–101 行 `userMessage` + `imageUrlList` 拼装。
- 证据：一次纯文本请求的 FINAL_REQUEST_JSON（脱敏）仅含 `model`/`stream`/`messages`，无 AB 字段。

### 阶段1：纯文本 SSE 弱网/切后台不挂死
- SSE Watchdog：readTimeout 15s，连续无 delta 则断开；UI 提示「网络不稳定，已中断，可重试」，输入可恢复。
- 切后台：onPause/onStop 时 `QwenClient.cancelCurrentRequest()`；assistant 消息标记「（已中断）」；onResume 不续用旧连接。
- 超时：connectTimeout 30s、readTimeout 15s、callTimeout 120s。
- 重试：不自动重试，仅用户手动重发。
- 证据：Logcat 含 requestId/耗时/finish_reason/HTTP 状态码；弱网/切后台 10s 后能进入「已中断」并恢复；切 App 回来无一直转圈/禁用输入。

### 阶段2：长聊不卡顿
- 消息窗口化：仅渲染最近 80 条，更老折叠为「加载更多（N 条）」点击追加 20 条。
- SSE 节流：chunk 每 60ms 合并 append，减少重排。
- 证据：连续 200 条对话可顺滑滚动，CPU/卡顿改善（录屏约 10s）。

### 阶段3：图片链路保持冻结
- UPLOAD_BASE_URL 未配置：图片卡片统一 fail，文案「未配置上传服务」，发送键禁用（有图时）；禁止上传失败降级纯文本发送。
- 证据：未配置时选图即 fail；有图无法发送；纯文本可发送且流式正常。

---

## 五、阶段验收证据清单（必须交付，没证据不算完成）

### 1）阶段0：AB 占位不污染主链路
- **操作**：发一条纯文本请求。
- **抓取**：Logcat 中该次请求的 `FINAL_REQUEST_JSON（脱敏）` 整段。
- **要求**：payload 里**只出现** `model` / `stream` / `messages`；**不得出现**任何 AB 字段或摘要字段（如 a_messages、b_summary、meta、user_id、session_id 等）。
- **证据**：贴一条完整脱敏 payload + 说明“仅含 model/stream/messages”。

### 2）阶段1：弱网/切后台不挂死（Watchdog + 可取消）
- **同一会话做 3 次测试**：
  - **A. 正常网络**：发一条文本 → 必须看到 `STREAM_COMPLETE` + `finish_reason` + 耗时 日志。
  - **B. 发送后立刻切后台/开飞行模式 10 秒再回来** → UI 必须出现“已中断/可重试”，输入框可继续用，不允许一直转圈/卡死。
  - **C. 连续快速发送 3 次（上一次未结束就发下一次）** → 必须能 cancel 前一次，不允许串流/错绑消息。
- **证据**：每次贴 **requestId** + **状态**（complete / interrupted / canceled）+ **耗时**（脱敏）。共 3 条记录。

### 3）阶段2：长聊不卡顿（窗口化 + SSE 节流）
- **操作**：连续对话累计 ≥ 200 条（可用脚本快速发）；滚动 10 秒；“加载更多”点击后只扩展历史，不应触发重新窗口化导致闪跳。
- **证据**：录屏 10 秒 + 当前渲染条数 / 加载更多剩余条数的日志或截图。

### 4）阶段3：图片冻结（未配置上传服务时行为稳定）
- **条件**：未配置 `UPLOAD_BASE_URL`。
- **要求**：选图后每张都 fail，文案“未配置上传服务”；发送键在存在 fail 时必须禁用；纯文本仍可正常发送。
- **证据**：截图（缩略图叉 + 文案 + 发送键灰）+ Logcat 中对应错误回调（如 `onError("未配置上传服务")` / `onImageUploadStatus(..., 'fail', ..., '未配置上传服务')`）。

---

## 六、本轮冻结点（Freeze）

- **冻结点**：阶段 0–3 行为冻结；后续改动**不得破坏**上述验收口径。
- **范围**：  
  - 阶段0：模型请求 payload 仅含 model/stream/messages，AB 不参与。  
  - 阶段1：SSE Watchdog 15s、切后台可取消、正常/中断/快速连发 3 次可区分且不串流。  
  - 阶段2：最近 80 条窗口化、“加载更多”仅扩展历史不闪跳、SSE 节流 60ms。  
  - 阶段3：未配置上传服务时图片统一 fail + 文案“未配置上传服务”+ 发送键 gating，不降级纯文本发送。
- **标记**：tag `v0.1-text-stable`（或 commit 标记 Freeze）。  
- **P1**：仅当 P0 验收通过后进入下一阶段；文字核心链路先稳定跑一周，再决定是否启用图片上传后端/查证模块。

---

**变更范围**：仅稳定性/接口占位/日志/渲染层，未改 AB 层核心规则。  
**云端**：CNB + DIFF 推送。

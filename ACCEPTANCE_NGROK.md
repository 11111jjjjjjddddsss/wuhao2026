# 本机+ngrok 一次性验收（不买服务器）

按顺序执行，验收通过后**图片模块冻结**，转做文字主线（AB 层骨架/会员）。

---

## 1. 启动 ngrok + upload-server

**终端 A（ngrok）：**
```bash
ngrok http 3000
```
复制显示的 **https://xxxx.ngrok-free.app**（或 xxx.ngrok.io），记为 `UPLOAD_BASE_URL`，**不要带 /upload**。

**终端 B（上传服务）：**
```bash
set BASE_PUBLIC_URL=https://xxxx.ngrok-free.app
cd upload-server
npm install
npm start
```
保持两个终端运行。

---

## 2. 验证上传协议（必须返回根级 url）

**仓库根目录：**
```bat
test_upload.bat https://xxxx.ngrok-free.app
```
**期望**：HTTP 200，返回 `{"url":"https://..."}`（根级 url）。

**交付证据①**：贴 bat 完整输出（脱敏域名可留）。

---

## 3. 验证模型侧能拉取

用第 2 步返回的 `url` 值（整段 https 链接）执行：
```bat
set API_KEY=你的DashScope密钥
node upload-server/verify-model-url.js "第2步返回的url"
```
**期望**：有 `choices[0].message.content`（模型对图片的描述）。

**交付证据②**：贴 verify 脚本完整输出（请求 payload 不含 key + 响应 content）。

---

## 4. App 端到端验证

- 在 **gradle.properties** 中配置（把下面换成你的 ngrok 地址）：
  ```properties
  UPLOAD_BASE_URL=https://xxxx.ngrok-free.app
  ```
- 重新打包运行 App：选图 → 转圈 → 对勾 → 可发送 → 模型图文回复（SSE 正常）。

**交付证据③**：App 截图 + Logcat 中 UPLOAD_URLS（脱敏）、FINAL_REQUEST_JSON（脱敏）、模型输出片段。

---

## 验收通过后

- **图片模块冻结**：不再改上传协议、状态机、前端图片 UI。
- **下一步**：AB 层骨架、会员等文字主线。

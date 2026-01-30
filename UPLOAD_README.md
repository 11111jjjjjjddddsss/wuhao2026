# 图片上传后端闭环说明

## 闭环验收步骤（按顺序做完并贴证据，脱敏）

1. **跑通上传服务并拿到公网 https**  
   - 任选：ngrok / Railway / Render / ECS  
   - 得到 `UPLOAD_BASE_URL=https://xxxx`（不要带 `/upload`）  
   - 贴证据：服务启动日志 + `/upload` 实际可用证明（如 curl 返回 `{"url":"https://..."}`）

2. **curl 验证上传协议**  
   - 仓库根目录执行：`test_upload.bat <UPLOAD_BASE_URL>`  
   - 期望：HTTP 200，返回 JSON 根级 `{"url":"https://..."}`  
   - 贴证据：返回内容（脱敏域名可留）

3. **模型侧能拉取该 URL**  
   - 用第 2 步返回的 url 执行：  
     `set API_KEY=你的key` 然后  
     `node upload-server/verify-model-url.js "第2步返回的url"`  
   - 期望：响应里有 `choices[0].message.content`（模型描述图片）  
   - 贴证据：请求 payload（不含 key）+ 响应 `choices[0].message.content`  
   - 若失败说明 URL 对模型端不可达（手机可达不算）

4. **上传服务最低安全（已实现）**  
   - 仅允许 jpeg/png/webp  
   - 单文件 10MB，超出返回 413 + `{"error":"单文件不超过 10MB"}`  
   - 文件名 uuid，不用原始文件名  
   - `/uploads` 仅静态文件，`index: false` 禁目录列表  

5. **App 端到端验证**  
   - `gradle.properties` 配好 `UPLOAD_BASE_URL`  
   - App：选图 → 转圈 → 对勾 → 可发送 → 模型图文回复  
   - 贴证据：Logcat 中 UPLOAD_URLS（脱敏）+ FINAL_REQUEST_JSON（脱敏）+ 模型正常输出  

---

## 协议（已冻结）

- **URL**：`POST {UPLOAD_BASE_URL}/upload`
- **请求**：`multipart/form-data`，字段名固定 `file`
- **成功**：HTTP 200 + JSON 根级 `{"url":"https://..."}`（公网可访问、https）
- **失败**：HTTP 非 200 + JSON 根级 `{"error":"..."}`

## 1. 后端实现（已就绪）

- 目录：`upload-server/`
- 实现：本地存储 + BASE_PUBLIC_URL，返回公网 https URL（ngrok/部署域名），无需买服务器
- 本地运行：须设置 `BASE_PUBLIC_URL` 后 `cd upload-server && npm install && npm start`（默认 3000 端口）

## 2. 获得公网 UPLOAD_BASE_URL

任选一种方式，得到 https 地址后填进第 3 步。

### 方式 0：本地 + ngrok（零云控制台）

1. 新开终端运行 ngrok：`ngrok http 3000`（需先安装 https://ngrok.com ），复制给出的 **https://xxx.ngrok.io**
2. 本机运行上传服务并设置公网地址：  
   `set BASE_PUBLIC_URL=https://xxx.ngrok.io` 然后 `cd upload-server && npm install && npm start`
3. 把 **https://xxx.ngrok.io** 作为 **UPLOAD_BASE_URL** 填进 gradle.properties（不要带 `/upload`）
4. 手机/模拟器需能访问该 ngrok 地址（同一网络或 ngrok 已暴露公网）

### 方式 A：Railway（免费额度）

1. 打开 https://railway.app ，用 GitHub 登录
2. New Project → Deploy from GitHub repo → 选本仓库
3. 若仓库根目录不是 upload-server：Settings → Root Directory 填 `upload-server`；或使用根目录的 Procfile（会执行 `cd upload-server && npm install && npm start`）
4. 部署完成后 Settings → Generate Domain，得到 `https://xxx.up.railway.app`
5. **UPLOAD_BASE_URL** = `https://xxx.up.railway.app`（不要带 `/upload`）

### 方式 B：Render

1. https://render.com 注册，New → Web Service，连接 GitHub 选本仓库
2. Root Directory 填 `upload-server`，Build: `npm install`，Start: `npm start`
3. 生成域名后 **UPLOAD_BASE_URL** = `https://xxx.onrender.com`

### 方式 C：自有服务器

在服务器上：`cd upload-server && npm install && PORT=80 node server.js`，前面用 nginx 配好 https，**UPLOAD_BASE_URL** = `https://你的域名`

## 3. 配置到 App

在项目根目录 **gradle.properties** 中设置（把下面换成你实际的上传服务地址）：

```properties
UPLOAD_BASE_URL=https://你的上传服务域名
```

不要写 `/upload`，代码里会自动拼上。保存后重新打包 APK。

## 4. 验收

### ① curl 测试（得到 `{"url":...}`）

在项目根目录执行（把 `https://你的域名` 换成第 2 步得到的地址）：

```bat
test_upload.bat https://你的域名
```

或先 `set UPLOAD_BASE_URL=https://你的域名`，再执行 `test_upload.bat`。

若没有 test-image.jpg，脚本会自动运行 `node upload-server/create-test-image.js` 生成最小测试图。

### ② App 端

- 选图后：转圈 → 对勾（失败则叉 + 可重试）
- 任意 uploading/fail 时发送键灰；全部 success 才能发送
- 发出后模型收到 image_url，左侧 SSE 真流式逐字输出

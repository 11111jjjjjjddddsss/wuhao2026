# 图片上传后端

- **协议**：`POST {UPLOAD_BASE_URL}/upload`，`multipart/form-data`，字段名 `file`
- **成功**：HTTP 200 + `{"url":"https://..."}`
- **失败**：HTTP 非 200 + `{"error":"..."}`

实现：本地存储到 `uploads/`，返回 `BASE_PUBLIC_URL/uploads/文件名`；需设置公网 BASE_PUBLIC_URL（如 ngrok 或部署域名）。

## 本地运行（配合 ngrok 获得 https）

```bash
cd upload-server
npm install
# 先启动 ngrok: ngrok http 3000，复制得到的 https://xxx.ngrok.io
set BASE_PUBLIC_URL=https://xxx.ngrok.io
npm start
```

默认端口 3000。**必须**设置环境变量 **BASE_PUBLIC_URL** 为公网 https 地址（如 ngrok 或部署域名），否则 POST /upload 返回 503。

## 部署到公网（获得 https UPLOAD_BASE_URL）

任选其一即可。

### 方式一：Railway（推荐，免费额度）

1. 打开 https://railway.app ，用 GitHub 登录
2. New Project → Deploy from GitHub repo → 选本仓库
3. 根目录改为 `upload-server`（Settings → Root Directory 填 `upload-server`），或只把 `upload-server` 目录单独建一个仓库再部署
4. 部署完成后 Settings → Domains → 生成域名，得到 `https://xxx.up.railway.app`
5. **UPLOAD_BASE_URL** = `https://xxx.up.railway.app`（不要带 `/upload`）

### 方式二：Render

1. https://render.com 注册，New → Web Service
2. 连接 GitHub，选仓库，Root Directory 填 `upload-server`
3. Build: `npm install`，Start: `npm start`
4. 生成域名后 **UPLOAD_BASE_URL** = `https://xxx.onrender.com`

### 方式三：自有服务器

在服务器上执行：

```bash
cd upload-server
npm install
PORT=80 node server.js
```

建议用 nginx 反代并配置 HTTPS，**UPLOAD_BASE_URL** = `https://你的域名`

## 配置到 App

在项目根目录 `gradle.properties` 中设置（把下面换成你实际的上传服务地址）：

```properties
UPLOAD_BASE_URL=https://你的上传服务域名
```

不要写 `/upload`，代码里会自动拼上。然后重新打包 APK。

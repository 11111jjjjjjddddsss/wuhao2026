# 图片闭环一次性验收清单

**目标**：本机 + ngrok 完成验收 → 通过后冻结图片模块，转 AB 层/会员。

---

## 一、操作顺序

### 1. 跑起 upload-server + ngrok，拿到 UPLOAD_BASE_URL

**终端 A（ngrok）：**
```bash
ngrok http 3000
```
复制给出的 **https 地址**，例如 `https://xxxx-xx-xx.ngrok-free.app` → 即为 **UPLOAD_BASE_URL**（不要带 `/upload`）。

**终端 B（上传服务）：**
```bash
set BASE_PUBLIC_URL=https://你的ngrok地址
cd upload-server
npm install
npm start
```
保持两个终端都运行。

---

### 2. 运行 test_upload.bat（证据①）

在项目**根目录** `d:\wuhao` 下：

```bat
test_upload.bat https://你的ngrok地址
```

**必须**看到响应里包含根级 `{"url":"https://..."}`，且最后一行 `HTTP_CODE:200`。  
→ **证据①**：截屏或复制该次完整输出（可脱敏域名）。

---

### 3. 运行 verify-model-url.js（证据②）

用第 2 步返回的 **url**（即 `{"url":"https://..."}` 里的那个完整 https 地址）：

```bat
set API_KEY=你的DashScope密钥
node upload-server\verify-model-url.js "第2步返回的完整url"
```

**必须**看到控制台输出 `choices[0].message.content:` 及模型对图片的描述。  
→ **证据②**：截屏或复制该次完整输出（请求 payload + 响应 content，脱敏 key）。

---

### 4. Android 配置并跑 App（证据③）

在 **gradle.properties** 中设置（与 ngrok 地址一致）：

```properties
UPLOAD_BASE_URL=https://你的ngrok地址
```

保存后重新构建并安装 App：

- 选图 → 转圈 → 对勾 → 可发送
- 发送后模型图文回复，SSE 正常逐字输出

→ **证据③**：  
- App 截图：选图→对勾→发送→模型回复界面  
- Logcat 截图：含 `UPLOAD_URLS`、`FINAL_REQUEST_JSON` 等（可脱敏）

---

## 二、交付证据（三份）

| 证据 | 内容 |
|------|------|
| **① bat 结果** | `test_upload.bat <UPLOAD_BASE_URL>` 的完整输出，含 `{"url":"https://..."}` 和 `HTTP_CODE:200` |
| **② verify 输出** | `node upload-server/verify-model-url.js "<url>"` 的完整输出，含 `choices[0].message.content` |
| **③ App/Logcat** | App 选图→对勾→发送→模型回复截图 + Logcat 中上传/请求相关日志截图 |

---

## 三、验收通过后

- **冻结图片模块**：不再改上传/状态机/协议相关代码。
- **后续主线**：AB 层骨架、会员；云端 CNB + DIFF。

# 官方网站 Runbook

最后更新：2026-06-17

## 当前状态

官网首版在 `site` 目录，使用 Vite 静态站实现。当前只做克制暗色一页展示：左侧品牌与产品主张“看清作物问题”、右侧三项能力“原生视觉感知 / 多模态线索融合 / 稳健农技推理”、安卓版下载按钮、服务边界和 footer 公司主体 / ICP / 公安备案；不做顶部导航、对话演示、管理后台，不保存后台 secret，不承载用户数据，不公开点名 `Qwen3.5-Plus`，也不把“联网校准”单独包装成公开核心卖点。

2026-06-06 已部署到 ECS Nginx 静态站；2026-06-10 已重新部署并验证公安备案 footer、警徽图标和独立协议页面：

- 入口：`https://nongjiqiancha.cn/`、`https://www.nongjiqiancha.cn/`
- DNS：`@` 和 `www` A 记录均指向 ECS 公网 IP `39.106.1.151`
- Nginx 配置：`/etc/nginx/sites-available/nongjiqiancha-site`
- 站点目录：`/var/www/nongjiqiancha-site/current`
- 内部测试包推荐下载主链：`download.nongjiqiancha.cn + OSS private object + signed URL`，脚本默认只保留最新 1 个 debug/internal APK；ECS `/test-apks/` 只作为显式 `-UseEcsDownloadFallback` 的临时回退，不挂官网正式下载按钮，不进入检查更新
- 证书：Let's Encrypt / certbot，路径 `/etc/letsencrypt/live/nongjiqiancha.cn/`，有效期到 2026-09-04，自动续期 timer 已启用；只记录证书路径，不记录私钥内容
- 公网验证：根域名 HTTP 会 301 到 HTTPS，根域名 / www HTTPS 返回官网首页 200；`/gongan.png` 返回图片；`/legal/user-agreement/` 和 `/legal/privacy-policy/` 在根域名与 www 域名下均返回 200；`api.nongjiqiancha.cn` healthz 仍独立走 API Nginx 配置
- Nginx 静态站 HTTPS 配置包含 `X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy`、`Content-Security-Policy` 和 HSTS；全局安全头兜底见 [security-hardening.md](D:/wuhao/docs/runbooks/security-hardening.md)
- 2026-06-10 代码和线上均已补独立用户协议 / 隐私政策网页、公安备案 footer 和本地警徽图标；部署脚本会在本地构建和远端发布后校验首页、`/legal/user-agreement/`、`/legal/privacy-policy/`、`/gongan.png`、ICP 号、公安备案号和公安查询链接

## 本地开发

```powershell
cd site
npm install
npm run dev
```

## 构建

```powershell
cd site
npm run build
```

产物在 `site/dist`。

## 部署到 ECS

官网通过 [deploy-ecs-site.ps1](D:/wuhao/scripts/deploy-ecs-site.ps1) 部署。脚本会：

1. 本地构建 `site`
2. 同步 `@` / `www` A 记录到 ECS 公网 IP
3. 分片上传 `site/dist` 打包产物到 ECS
4. 发布到 `/var/www/nongjiqiancha-site/releases/<sha>` 并切换 `current` symlink
5. 写入 / 刷新 Nginx 静态站配置
6. 通过 certbot 申请或续用 `nongjiqiancha.cn` / `www.nongjiqiancha.cn` 免费 HTTPS 证书
7. 验证入口状态码和关键内容：证书存在时根域名 HTTP 301、协议页 HTTP 301、根域名 HTTPS 200、`www` HTTPS 200、`/gongan.png` 200，且首页 / 协议页正文包含 ICP、公安备案号、公安查询链接和对应页面标题；证书尚未签发时至少断言 HTTP 首页、协议页和警徽可访问

`site/vite.config.ts` 当前关闭官网 CSS 压缩，避免 Vite / Lightning CSS 把传统 `max-width` 媒体查询压成部分旧安卓浏览器不识别的范围语法，导致手机浏览器文字竖排。不要为了产物更小随手恢复 CSS 压缩；如要恢复，必须先用旧安卓浏览器或对应真机页面截图验证。

只构建打包、不改 DNS / 服务器：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\deploy-ecs-site.ps1 -PackageOnly
```

正式部署：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\deploy-ecs-site.ps1
```

## 下载入口

官网下载按钮通过 `VITE_ANDROID_APK_URL` 注入真实 APK 下载地址：

```powershell
$env:VITE_ANDROID_APK_URL="https://download.nongjiqiancha.cn/android/releases/<versionCode>/nongjiqiancha-release.apk"
npm run build
```

未设置时下载按钮保持不可点击，不展示“准备中”或备案 / 回归等内部流程。只有 `VITE_ANDROID_APK_URL` 是 `https://download.nongjiqiancha.cn/android/releases/...apk` 这种自有下载域名下的稳定正式 release 地址，且原始或 URL 编码后的地址都不包含 `test-apks`、`debug`、`internal`、`staging` 等测试包标记，也不带 `Expires / Signature / OSSAccessKeyId / security-token / x-oss-*` 等短签名参数时，页面才启用下载按钮；首版不要在 App 公安备案、真机回归和正式发版口令完成前写死不存在或未验证的 APK 链接。

[deploy-ecs-site.ps1](D:/wuhao/scripts/deploy-ecs-site.ps1) 默认不允许带 `VITE_ANDROID_APK_URL` 部署官网；脚本会同时检查当前环境变量和 `site/.env*` 文件，避免本机残留环境文件把 APK 链接带进正式站。只有用户明确要求发布正式下载入口时，才允许传 `-AllowOfficialDownloadUrl`，且脚本仍会拒绝测试包路径、编码后的测试包标记、短签名 URL、外部下载域名和非 `/android/releases/` 正式路径。代理测试 / 管理层试用的 debug 包只走 [android-test-package.md](D:/wuhao/docs/runbooks/android-test-package.md)，不要放到官网正式下载按钮。

APK 发布仍以 [app-update.md](D:/wuhao/docs/runbooks/app-update.md) 为准：APK 必须是固定 release 签名、包名 `com.nongjiqiancha`、versionCode 递增，并记录文件大小和 SHA-256。

## 备案 footer

- 网站 ICP 备案号：`京ICP备2026031728号-1`
- footer 备案号必须链接到 `https://beian.miit.gov.cn/`
- 网站公安备案号：`京公网安备11010602202723号`
- 公安备案链接：`https://beian.mps.gov.cn/#/query/webSearch?code=11010602202723`
- footer 展示公司主体“北京农技千问科技有限公司”、网站 ICP 备案号、公安备案号和本地警徽图标 `/gongan.png`
- 公安备案数据码、账号、证件号等不写入仓库、文档或前端代码

## 后续可加但当前不做

- 下载页独立路径和 APK 版本记录
- 管理后台登录入口
- 官网访问日志与静态资源 CDN

管理后台如果做网页，必须先做服务端登录 / session 层。浏览器前端不能直接持有 `SUPPORT_ADMIN_SECRET`、`DAILY_AGRI_JOB_SECRET` 或任何模型 / 云资源密钥。

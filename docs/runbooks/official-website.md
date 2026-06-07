# 官方网站 Runbook

最后更新：2026-06-07

## 当前状态

官网首版在 `site` 目录，使用 Vite 静态站实现。当前只做克制暗色一页展示：左侧品牌与产品主张“看清作物问题”、右侧三项能力“原生视觉感知 / 多模态线索融合 / 稳健农技推理”、安卓版下载按钮、服务边界和 footer 公司主体 / ICP；不做顶部导航、对话演示、管理后台，不保存后台 secret，不承载用户数据，不公开点名 `Qwen3.5-Plus`，也不把“联网校准”单独包装成公开核心卖点。

2026-06-06 已部署到 ECS Nginx 静态站：

- 入口：`https://nongjiqiancha.cn/`、`https://www.nongjiqiancha.cn/`
- DNS：`@` 和 `www` A 记录均指向 ECS 公网 IP `39.106.1.151`
- Nginx 配置：`/etc/nginx/sites-available/nongjiqiancha-site`
- 站点目录：`/var/www/nongjiqiancha-site/current`
- 证书：Let's Encrypt / certbot，路径 `/etc/letsencrypt/live/nongjiqiancha.cn/`，有效期到 2026-09-04，自动续期 timer 已启用；只记录证书路径，不记录私钥内容
- 公网验证：根域名 HTTP 会 301 到 HTTPS，根域名 / www HTTPS 返回官网首页 200；`api.nongjiqiancha.cn` healthz 仍独立走 API Nginx 配置
- Nginx 静态站 HTTPS 配置包含 `X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Permissions-Policy`、`Content-Security-Policy` 和 HSTS；全局安全头兜底见 [security-hardening.md](D:/wuhao/docs/runbooks/security-hardening.md)

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
7. 验证入口状态码：证书存在时根域名 HTTP 301、根域名 HTTPS 200、`www` HTTPS 200；证书尚未签发时至少断言 HTTP 200

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
$env:VITE_ANDROID_APK_URL="https://your-download-host/nongjiqiancha.apk"
npm run build
```

未设置时下载按钮保持不可点击，不展示“准备中”或备案 / 回归等内部流程。只有 `VITE_ANDROID_APK_URL` 是合法 `https://...apk` 时，页面才启用下载按钮；首版不要在 App 备案、公安备案和真机回归完成前写死不存在或未验证的 APK 链接。

APK 发布仍以 [app-update.md](D:/wuhao/docs/runbooks/app-update.md) 为准：APK 必须是固定 release 签名、包名 `com.nongjiqiancha`、versionCode 递增，并记录文件大小和 SHA-256。

## 备案 footer

- 网站 ICP 备案号：`京ICP备2026031728号-1`
- footer 备案号必须链接到 `https://beian.miit.gov.cn/`
- 公安联网备案通过后，再按全国互联网安全管理服务平台提供的 HTML / 图标 / 链接补真实公安备案号
- footer 展示公司主体“北京农技千问科技有限公司”和网站 ICP 备案号
- 公安备案号未下发前，公开官网 footer 不展示任何未完成占位
- 公安备案数据码、账号、证件号等不写入仓库、文档或前端代码

## 后续可加但当前不做

- 隐私政策 / 用户协议独立网页 URL
- 下载页独立路径和 APK 版本记录
- 管理后台登录入口
- 公安备案图标和真实公安备案号
- 官网访问日志与静态资源 CDN

管理后台如果做网页，必须先做服务端登录 / session 层。浏览器前端不能直接持有 `SUPPORT_ADMIN_SECRET`、`DAILY_AGRI_JOB_SECRET` 或任何模型 / 云资源密钥。

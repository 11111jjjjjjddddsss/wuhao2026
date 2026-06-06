# 官方网站 Runbook

最后更新：2026-06-06

## 当前状态

官网首版在 `site` 目录，使用 Vite 静态站实现。当前只做农技千查 App 介绍、安卓下载入口、服务边界说明和备案 footer，不做管理后台、不保存后台 secret、不承载用户数据。

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

产物在 `site/dist`。部署根域名 `nongjiqiancha.cn` 时，可把该目录作为 Nginx 静态站点根目录或上传到后续静态托管 / OSS + CDN。

## 下载入口

官网下载按钮通过 `VITE_ANDROID_APK_URL` 注入真实 APK 下载地址：

```powershell
$env:VITE_ANDROID_APK_URL="https://your-download-host/nongjiqiancha.apk"
npm run build
```

未设置时页面会显示“安卓下载地址待开放”。首版不要在 App 备案、公安备案和真机回归完成前写死不存在或未验证的 APK 链接。

APK 发布仍以 [app-update.md](D:/wuhao/docs/runbooks/app-update.md) 为准：APK 必须是固定 release 签名、包名 `com.nongjiqiancha`、versionCode 递增，并记录文件大小和 SHA-256。

## 备案 footer

- 网站 ICP 备案号：`京ICP备2026031728号-1`
- footer 备案号必须链接到 `https://beian.miit.gov.cn/`
- 公安联网备案通过后，再按全国互联网安全管理服务平台提供的 HTML / 图标 / 链接补真实公安备案号
- 公安备案号未下发前，只保留“公安备案号待补充”文本，不伪造编号
- 公安备案数据码、账号、证件号等不写入仓库、文档或前端代码

## 后续可加但当前不做

- 隐私政策 / 用户协议独立网页 URL
- 下载页独立路径和 APK 版本记录
- 管理后台登录入口
- 公安备案图标和真实公安备案号
- 官网访问日志与静态资源 CDN

管理后台如果做网页，必须先做服务端登录 / session 层。浏览器前端不能直接持有 `SUPPORT_ADMIN_SECRET`、`DAILY_AGRI_JOB_SECRET` 或任何模型 / 云资源密钥。

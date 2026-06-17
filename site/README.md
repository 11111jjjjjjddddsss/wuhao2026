# 农技千查官网

这是农技千查官网首版静态站，使用 Vite 构建。当前定位是克制的一页式产品展示、安卓下载入口、公司主体、ICP 备案和网站公安备案信息展示，不承载后台管理功能。

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

构建产物在 `site/dist`，部署到根域名 `nongjiqiancha.cn` 的静态站点或 Nginx 静态目录即可。

## 安卓下载地址

官网不在代码里写死 APK 链接。构建时通过环境变量注入：

```powershell
$env:VITE_ANDROID_APK_URL="https://download.nongjiqiancha.cn/android/releases/<versionCode>/nongjiqiancha-<versionName>.apk"
npm run build
```

未设置合法 `https://...apk` 下载地址时，页面保留下载按钮视觉入口，但按钮不可点击，也不展示内部准备状态。

正式下载地址只能使用自有下载域名 `download.nongjiqiancha.cn` 下的 `/android/releases/...apk` 稳定 release 路径。内部测试包 `test-apks/debug/...`、短期签名 URL、OSS 默认公网域名和外部 APK 域名不能写进官网正式下载按钮。部署官网正式下载按钮前，先按 App 更新 runbook 完成 release APK 校验、下载回验和后台物料对账，并在部署脚本中显式使用 `-AllowOfficialDownloadUrl`。

## 备案展示

- 当前 footer 展示公司主体“北京农技千问科技有限公司”、网站备案号 `京ICP备2026031728号-1` 和网站公安联网备案号 `京公网安备11010602202723号`。
- ICP 备案号链接到工信部备案管理系统；公安备案号使用本地警徽图标 `/gongan.png` 并链接到全国互联网安全管理服务平台查询页。
- App 备案号为 `京ICP备2026031728号-2A`，当前展示在 Android 设置页底部和 App 内服务协议 / 隐私政策基础信息里；官网 footer 仍只展示网站备案和网站公安备案信息。App 公安备案信息尚未下发，不要提前伪造；公安备案数据码也不要写进仓库。

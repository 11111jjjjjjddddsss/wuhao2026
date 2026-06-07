# 农技千查官网

这是农技千查官网首版静态站，使用 Vite 构建。当前定位是克制的一页式产品展示、安卓下载入口、公司主体和 ICP 备案信息展示，不承载后台管理功能。

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
$env:VITE_ANDROID_APK_URL="https://example.com/nongjiqiancha.apk"
npm run build
```

未设置合法 `https://...apk` 下载地址时，页面保留下载按钮视觉入口，但按钮不可点击，也不展示内部准备状态。

## 备案展示

- 当前 footer 展示公司主体“北京农技千问科技有限公司”和网站备案号 `京ICP备2026031728号-1`，备案号链接到工信部备案管理系统。
- 公安联网备案通过后，再在 footer 增补真实公安备案号和平台要求的图标 / 链接；未通过前公开官网只展示 ICP 备案号。
- 不要提前伪造公安备案号，也不要把公安备案数据码写进仓库。

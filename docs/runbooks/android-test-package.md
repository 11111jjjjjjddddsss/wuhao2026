# Android 测试包 Runbook

最后更新：2026-06-19

本 runbook 只用于给用户、代理或管理层临时安装测试包。测试包不是正式发布包，不进入 App 内检查更新，也不替代应用商店 / 官网正式下载物料。

## 当前边界

- 测试包默认使用 `debug` 构建，包名仍是 `com.nongjiqiancha`，后端仍接 `https://api.nongjiqiancha.cn`。
- 测试包可以包含 debug-only 预览面板和调试日志；除此之外，业务链路应尽量和正式包一致。
- 测试包文件名和 OSS 路径必须显式包含 `debug`、`internal` 或 `test-apks`，避免和正式 release 物料混淆。
- 测试包默认先上传到 OSS 私有对象，再通过 `download.nongjiqiancha.cn` 自有 HTTPS 下载域名生成签名链接；阿里云 OSS 默认公网 endpoint 不允许直接分发 APK，不能把 `*.oss-cn-beijing.aliyuncs.com` 签名 URL 发给测试用户。低成本下载方案见 [android-download-distribution.md](D:/wuhao/docs/runbooks/android-download-distribution.md)。
- 上传脚本只负责生成 / 上传 debug/internal 测试包和输出签名下载链接，不再主动清理 OSS 旧测试包，也不再清 ECS `/test-apks/` 旧调试包镜像；需要清理时由用户明确喊 Codex 再单次人工处理。OSS `test-apks/` 前缀 3 天游走期仍是云端生命周期兜底。
- 没有用户明确发版口令时，不生成并对外发布正式 release APK，不配置 App 内检查更新，不改官网正式下载按钮。

## 生成测试包

测试包默认走 OSS 自有下载域名签名链：

```powershell
.\scripts\publish-android-test-apk.ps1
```

脚本会执行：

1. 检查 git 工作区默认必须干净，保证测试包能追到明确 commit。
2. 运行 `:app:assembleDebug`。
3. 读取 `app/build/outputs/apk/debug/app-debug.apk`。
4. 校验 APK 包名、debuggable 状态、versionCode / versionName 和签名证书，要求测试包签名仍匹配本机固定 release 证书指纹。
5. 计算 SHA-256 和文件大小。
6. 上传到私有 OSS `test-apks/debug/<日期>/nongjiqiancha-debug-internal-<时间>-<commit>.apk`。
7. 通过 `download.nongjiqiancha.cn` 生成限时签名下载链接和 HEAD 探针。
8. 输出自有下载域名测试链接、commit、SHA-256、文件大小、签名指纹和有效期口径。

如果只是本机已构建好的 APK，可传 `-NoBuild -ApkPath <path>`，但脚本仍会读取 APK 本体确认它是 debuggable debug 包，并比对固定 release 证书指纹；release APK 不能通过这个脚本发给用户或代理。如果确实要发布未提交工作区的临时包，必须显式传 `-AllowDirty`，并在对外说明中标注这是未提交测试包。日常不要这样做。

只有内部 staging 或排查云端对象时才可显式传 `-SkipEcsDownloadPublish`。这种情况下脚本只输出 `test_apk_status=staged_only` 和 `test_apk_url=none`，不会给出可发给用户的公网下载链接。若下载域名临时异常，才允许显式传 `-UseEcsDownloadFallback` 回退到 ECS 官网 `/test-apks/` 路径；不要裸跑出旧 ECS 直链。

## 清理边界

仓库不再保留内部测试包清理脚本，测试包发布脚本也不再发布后主动删除旧对象或旧 ECS 镜像。需要清理云端测试包、本机 APK 构建产物或旧 ECS `/test-apks/` 镜像时，由用户明确提出后，Codex 再按当时状态单次检查、列出目标并人工执行；不要把测试包清理做成后台自动任务。正式包保留策略见 [app-update.md](D:/wuhao/docs/runbooks/app-update.md)。

## 禁止混用

测试包链接禁止写入以下位置：

- 管理后台“检查更新”页。
- `APP_ANDROID_APK_URL`、`APP_ANDROID_*` 环境变量。
- 官网 `VITE_ANDROID_APK_URL` 或正式下载按钮。
- 应用商店、备案或正式发版材料。

正式更新仍按 [app-update.md](D:/wuhao/docs/runbooks/app-update.md) 执行：构建 release APK、校验 release 物料、上传正式下载路径、后台配置检查更新、跑 release-match、旧包真机覆盖安装验证。

## 发给用户时必须包含

- 明确写“测试包 / debug / internal”。
- 下载链接。
- SHA-256。
- 文件大小。
- 对应 commit。
- 链接有效期。
- 说明“这不是正式发版，不会通过 App 内检查更新下发”。

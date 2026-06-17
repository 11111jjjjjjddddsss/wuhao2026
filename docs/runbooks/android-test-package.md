# Android 测试包 Runbook

最后更新：2026-06-17

本 runbook 只用于给用户、代理或管理层临时安装测试包。测试包不是正式发布包，不进入 App 内检查更新，也不替代应用商店 / 官网正式下载物料。

## 当前边界

- 测试包默认使用 `debug` 构建，包名仍是 `com.nongjiqiancha`，后端仍接 `https://api.nongjiqiancha.cn`。
- 测试包可以包含 debug-only 预览面板和调试日志；除此之外，业务链路应尽量和正式包一致。
- 测试包文件名和 OSS 路径必须显式包含 `debug`、`internal` 或 `test-apks`，避免和正式 release 物料混淆。
- 测试包链接默认使用 OSS 私有对象临时签名 URL，到期后失效；它只用于当次测试，不作为长期下载地址。
- OSS `test-apks/` 前缀按 3 天生命周期自动清理，避免内部测试包长期堆积；签名链接过期不等于对象立即删除。
- 没有用户明确发版口令时，不生成并对外发布正式 release APK，不配置 App 内检查更新，不改官网正式下载按钮。

## 生成测试包

测试包只走：

```powershell
.\scripts\publish-android-test-apk.ps1
```

脚本会执行：

1. 检查 git 工作区默认必须干净，保证测试包能追到明确 commit。
2. 运行 `:app:assembleDebug`。
3. 读取 `app/build/outputs/apk/debug/app-debug.apk`。
4. 计算 SHA-256 和文件大小。
5. 上传到私有 OSS `test-apks/debug/<日期>/nongjiqiancha-debug-internal-<时间>-<commit>.apk`。
6. 输出临时签名下载链接、commit、SHA-256、文件大小和有效期。

如果只是本机已构建好的 APK，可传 `-NoBuild -ApkPath <path>`，但脚本仍会读取 APK 本体确认它是 debuggable debug 包；release APK 不能通过这个脚本发给用户或代理。如果确实要发布未提交工作区的临时包，必须显式传 `-AllowDirty`，并在对外说明中标注这是未提交测试包。日常不要这样做。

## 本机 APK 清理

测试包上传成功后，本机只需要保留最近少量构建产物。清理本机生成 APK 时运行：

```powershell
.\scripts\clean-local-android-apks.ps1
```

该脚本只清理仓库内 Gradle / tmp 生成的 APK，默认删除 `app/build/intermediates/apk` 的重复中间产物，并按变体目录保留最近 1 个 `app/build/outputs/apk` 产物；不删除云端测试包、正式 release 记录、签名配置或源码。

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

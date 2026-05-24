# App 更新 Runbook

当前 Android “检查更新”走自有服务器 APK 分发，不走应用商店，也不做静默安装。

## 用户侧入口

- Android 设置页点击“检查更新”
- App 请求 `GET /api/app/update?platform=android&version_code=<当前versionCode>&version_name=<当前versionName>`
- 无更新：提示“已是最新版本”
- 有更新：弹“发现新版本”卡片，按钮为“稍后 / 立即更新”
- 点“立即更新”：App 下载后端返回的 `apk_url` 到本地 cache，并通过 FileProvider 调起 Android 系统安装页
- Android 8+ 如果用户还没允许本 App 安装未知应用，会先打开系统授权页；用户授权后需要重新点击“立即更新”

Android 普通 App 不能静默安装 APK，最终一定要经过系统安装确认。

## 实际发布时你只做这几步

1. 让 Codex 帮你把 Android `versionCode` 加 1，并构建 release APK
2. 把这个 APK 上传到你自己的服务器 / OSS，拿到一个 `https://...apk` 下载链接
3. 让 Codex 或运维把后端运行环境里的 `APP_ANDROID_*` 环境变量改成新版本和新 APK 链接
4. 用旧版 App 点“检查更新”，看到“发现新版本”就对了

这件事不需要你手写接口，也不需要你自己拼 JSON。

## 回滚时你只做这几步

1. 如果新 APK 有问题，先告诉 Codex 或运维“停掉这个更新”
2. 把后端运行环境里的 `APP_ANDROID_APK_URL` 清空，或者把 `APP_ANDROID_LATEST_VERSION_CODE` 改回稳定版本号
3. 后端会返回“无更新”，旧版 App 就不会继续提示下载那个坏包

已经点进系统安装页并完成安装的用户，需要后续再发一个更高 `versionCode` 的修复包来覆盖。

## 后端配置

接口：`GET /api/app/update`

通过环境变量控制 Android 最新版本：

- `APP_ANDROID_LATEST_VERSION_CODE`：最新 APK 的 `versionCode`，必须大于客户端当前 `versionCode` 才会返回有更新
- `APP_ANDROID_LATEST_VERSION_NAME`：最新 APK 的展示版本名
- `APP_ANDROID_APK_URL`：APK 下载地址，必须是公网 `https://` URL；非 https 会被后端视为无可用更新
- `APP_ANDROID_APK_SHA256`：可选但正式发布建议填写；APK 文件的 SHA-256，用于客户端下载后校验文件是否被传错、截断或替换
- `APP_ANDROID_RELEASE_NOTES`：更新说明，直接展示在更新卡片里；当前默认建议写“优化产品体验”
- `APP_ANDROID_FORCE_UPDATE`：可选，`true / 1 / yes / on` 表示强制更新；强制更新卡片不展示“稍后”
- `APP_ANDROID_FILE_SIZE_BYTES`：可选，APK 字节大小，用于更新卡片展示；填写后客户端会要求下载后的文件大小一致

客户端下载后会在调起系统安装页前做基础校验：

- 下载最终响应仍必须是 https。
- 如果配置了 `APP_ANDROID_FILE_SIZE_BYTES`，下载文件大小必须一致。
- 如果配置了 `APP_ANDROID_APK_SHA256`，下载文件哈希必须一致。
- APK 包名必须等于当前 App 包名，APK `versionCode` 必须等于后端下发的最新版本号，且大于当前已安装版本。

## 发布流程

1. 构建 release APK，并确认 `app/build.gradle.kts` 里的 `versionCode` 比线上旧包更大
2. 记录 APK 文件大小和 SHA-256
3. 把 APK 上传到自有服务器或 OSS，确保可以通过公网 https 下载，不建议让 Go 后端动态服务大 APK
4. 在后端运行环境变量里配置上述 `APP_ANDROID_*` 值
5. 重启 / 重新部署后端服务
6. 用旧版本 App 点击“检查更新”验证：应出现“发现新版本”卡片
7. 点“立即更新”验证下载、校验、未知来源授权和系统安装页是否能正常打开

## 回滚

- 若新包有问题，把 `APP_ANDROID_LATEST_VERSION_CODE` 调回当前稳定包版本，或清空 `APP_ANDROID_APK_URL`
- 后端会返回无更新，客户端不再提示用户下载该 APK
- 已经下载到用户手机 cache 的 APK 不会被主动安装，除非用户已经进入系统安装页并继续安装
- 已经完成安装的用户，不能用低 `versionCode` 覆盖回去，只能再发一个更高 `versionCode` 的修复包

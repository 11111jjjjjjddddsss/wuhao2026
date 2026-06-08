# App 更新 Runbook

最后更新：2026-06-08

当前 Android “检查更新”走自有服务器 APK 分发，不走应用商店，也不做静默安装。

当前对外包名已定为 `com.nongjiqiancha`。历史内测旧包 `com.nongjiqianwen` 不能通过“检查更新”平滑覆盖安装为新包；Android 系统会把它们视为两个 App，客户端包名校验也会拒绝错包。测试机如果装过旧包，应先卸载旧包再安装新包。后续自更新只适用于 `com.nongjiqiancha -> com.nongjiqiancha` 的同包名升级。

## 用户侧入口

- App 启动进入主界面后，会静默请求一次 `GET /api/app/update`；如果服务端返回了更高版本，且当前设备还没对这个 `latest_version_code` 看过弹窗，App 会自动弹一次“发现新版本”，用户只需点“稍后 / 立即更新”
- Android 设置页点击“检查更新”
- App 请求 `GET /api/app/update?platform=android&version_code=<当前versionCode>&version_name=<当前versionName>`
- 无更新：提示“已是最新版本”
- 有更新：弹“发现新版本”卡片，按钮为“稍后 / 立即更新”
- 点“立即更新”：App 下载后端返回的 `apk_url` 到本地 cache，并通过 FileProvider 调起 Android 系统安装页
- Android 8+ 如果用户还没允许本 App 安装未知应用，会先打开系统授权页；用户授权后需要重新点击“立即更新”

自动提醒不会变成系统通知，也不会在同一个版本号上反复骚扰用户；只有后台把 `latest_version_code` 提高后，App 才会再对这个新版本自动弹一次。若后台开启强制更新，弹窗仍会按强更口径不展示“稍后”。

Android 普通 App 不能静默安装 APK，最终一定要经过系统安装确认。

## 实际发布时你只做这几步

你不需要记命令或手工拼配置。以后可以直接说：

- “修这个 bug”
- “发一个新版本”
- “停掉这个更新”
- “这个版本有问题，发修复包”

Codex 默认按下面流程处理：

1. 先判断问题属于 Android、后端、官网、配置还是云资源；如果只是后端问题，优先只发后端，不打 APK
2. 如果必须发 Android 新包，Codex 负责把 Android `versionCode` 加 1，并用固定 release 签名构建 `com.nongjiqiancha` APK；Android 构建默认使用正式 `UPLOAD_BASE_URL=https://api.nongjiqiancha.cn`，如需特殊环境才显式覆盖
3. Codex 负责记录 APK 文件大小、SHA-256、包名、`versionCode`、签名指纹和更新说明
4. Codex 负责把 APK 上传到自有服务器 / OSS，拿到一个公网 `https://...apk` 下载链接
5. Codex 或运维把后端运行环境里的 `APP_ANDROID_*` 环境变量改成新版本和新 APK 链接，并用旧版 App 点“检查更新”验证
6. 真机回归至少覆盖登录、文字问诊、图片问诊、历史恢复、帮助与反馈、会员中心、检查更新和系统安装页

这件事不需要你手写接口，也不需要你自己拼 JSON。

## 修 bug 时怎么判断要不要发 APK

- 只改 Go 后端、模型提示词、联网策略、Key 池、Nginx、证书、今日农情生成、帮助与反馈后台回复等：通常只部署后端，不需要用户更新 App
- 改 Android UI、登录 SDK、图片选择 / 上传前置逻辑、检查更新客户端校验、权限、协议页、聊天滚动链、会员中心展示等：需要打新 APK
- 改官网介绍、下载按钮、备案 footer：只部署官网，不需要发 App
- 改后端配置导致旧 App 能继续工作：不需要发 APK
- 新包如果用户已经安装，不能用低版本回退，只能发一个更高 `versionCode` 的修复包覆盖

## 回滚时你只做这几步

1. 如果新 APK 有问题，先告诉 Codex 或运维“停掉这个更新”
2. 把后端运行环境里的 `APP_ANDROID_APK_URL` 清空，或者把 `APP_ANDROID_LATEST_VERSION_CODE` 改回稳定版本号
3. 后端会返回“无更新”，旧版 App 就不会继续提示下载那个坏包

已经点进系统安装页并完成安装的用户，需要后续再发一个更高 `versionCode` 的修复包来覆盖。

## 后端配置

接口：

- 用户侧：`GET /api/app/update`
- 后台侧：`GET /admin-api/v1/app-update/android`、`POST /admin-api/v1/app-update/android`

当前 Android 检查更新优先读取数据库表 `app_release_configs` 里的 `android` 记录；如果数据库里还没有记录，才回退读后端环境变量。

环境变量兼容字段仍然保留：

- `APP_ANDROID_LATEST_VERSION_CODE`：最新 APK 的 `versionCode`，必须大于客户端当前 `versionCode` 才会返回有更新
- `APP_ANDROID_LATEST_VERSION_NAME`：最新 APK 的展示版本名
- `APP_ANDROID_APK_URL`：APK 下载地址，必须是公网 `https://` URL；非 https 会被后端视为无可用更新
- `APP_ANDROID_APK_SHA256`：可选但正式发布建议填写；APK 文件的 SHA-256，用于客户端下载后校验文件是否被传错、截断或替换
- `APP_ANDROID_RELEASE_NOTES`：更新说明，直接展示在更新卡片里；当前默认建议写“优化产品体验”
- `APP_ANDROID_FORCE_UPDATE`：可选，`true / 1 / yes / on` 表示强制更新；强制更新卡片不展示“稍后”
- `APP_ANDROID_FILE_SIZE_BYTES`：可选，APK 字节大小，用于更新卡片展示；填写后客户端会要求下载后的文件大小一致
- `APP_ANDROID_UPDATE_ENABLED`：可选，兼容环境变量开关；未配置时若版本号和 APK URL 都存在，默认视为启用

管理后台“检查更新”页现在已经可以直接维护 Android 更新配置：版本号、版本名、HTTPS APK、SHA-256、文件大小、更新说明、是否强制更新、是否对外启用。后台保存后立即写入 `app_release_configs`，`/api/app/update` 会优先按这份配置对外返回；取消“对外启用更新”并保存，就是停更。

管理后台“检查更新”页和监控面板把两个口径分开展示：`config_valid` 表示版本号 / APK URL 这组配置是否合法；`download_artifacts_complete` 表示正式下载物料是否齐全，只有 HTTPS APK、SHA-256 和文件大小都配置时才为 true。上线或发包前以后者判断“正式包物料是否已经齐”。

客户端下载后会在调起系统安装页前做基础校验：

- 下载最终响应仍必须是 https。
- 如果配置了 `APP_ANDROID_FILE_SIZE_BYTES`，下载文件大小必须一致。
- 如果配置了 `APP_ANDROID_APK_SHA256`，下载文件哈希必须一致。
- APK 包名必须等于当前 App 包名，APK `versionCode` 必须等于后端下发的最新版本号，且大于当前已安装版本。

## 发布流程

1. 构建 release APK，并确认 `app/build.gradle.kts` 里的 `versionCode` 比线上旧包更大、`applicationId` 仍是 `com.nongjiqiancha`，且 release 构建使用 https `UPLOAD_BASE_URL`
2. 记录 APK 文件大小和 SHA-256
3. 把 APK 上传到自有服务器或 OSS，确保可以通过公网 https 下载，不建议让 Go 后端动态服务大 APK
4. 在管理后台“检查更新”页填写版本号、HTTPS APK、SHA-256、文件大小和更新说明，勾上“对外启用更新”后保存；如暂时不走后台，也可继续改 `APP_ANDROID_*` 环境变量
5. 用旧版本 App 点击“检查更新”验证：应出现“发现新版本”卡片
6. 点“立即更新”验证下载、校验、未知来源授权和系统安装页是否能正常打开

发布时还要记录签名证书指纹。当前 release 签名公钥和指纹信息保存在本机 `%USERPROFILE%\\.nongjiqiancha\\android-release-public-info.txt`；签名密码配置保存在 `%USERPROFILE%\\.nongjiqiancha\\android-release-signing.properties`，不能提交到 git 或写入公开文档。

## 回滚

- 若新包有问题，最直接的是在管理后台“检查更新”页取消“对外启用更新”并保存；兼容路线仍可清空 `APP_ANDROID_APK_URL`
- 后端会返回无更新，客户端不再提示用户下载该 APK
- 已经下载到用户手机 cache 的 APK 不会被主动安装，除非用户已经进入系统安装页并继续安装
- 已经完成安装的用户，不能用低 `versionCode` 覆盖回去，只能再发一个更高 `versionCode` 的修复包

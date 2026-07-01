# App 更新 Runbook

最后更新：2026-07-01

当前 Android “检查更新”走自有服务器 APK 分发，不走应用商店，也不做静默安装。

当前对外包名已定为 `com.nongjiqiancha`。历史内测旧包 `com.nongjiqianwen` 不能通过“检查更新”平滑覆盖安装为新包；Android 系统会把它们视为两个 App，客户端包名校验也会拒绝错包。测试机如果装过旧包，应先卸载旧包再安装新包。后续自更新只适用于 `com.nongjiqiancha -> com.nongjiqiancha` 的同包名升级。

## 测试包边界

内部测试包不走本 runbook 的正式检查更新链路。用户只说“给我一个测试包 / 代理测试 / 管理层试用”时，使用 [android-test-package.md](D:/wuhao/docs/runbooks/android-test-package.md) 和 [publish-android-test-apk.ps1](D:/wuhao/scripts/publish-android-test-apk.ps1) 生成 debug/internal 临时下载链接。

测试包链接禁止写入管理后台“检查更新”、`APP_ANDROID_APK_URL`、`APP_ANDROID_*` 环境变量、官网 `VITE_ANDROID_APK_URL` 或正式下载按钮。正式检查更新和官网下载当前只接受 `https://download.nongjiqiancha.cn/android/releases/...apk` 这种自有下载域名下的稳定 release 裸地址；URL 不能带 userinfo、query string 或 fragment。正式发版校验脚本会对原始 URL 和 URL 解码后的路径一起拒绝包含 `test-apks`、`debug`、`internal` 或 `staging` 的 APK URL，避免把测试包误下发给用户。

## 正式包留存与本地产物

正式 release APK 不按测试包 3 天生命周期删除。已经对外发布过的正式包属于回滚、审计和问题定位材料，云端 / 发布记录至少要保留“当前正在下发的正式包 + 最近若干个可回滚正式包 + 对应 `versionCode / versionName / SHA-256 / 文件大小 / commit / 签名指纹`”。APK 体积很小，早期不建议为了省极低的 OSS 存储费删除最近正式物料；后续如果版本很多，再按“保留最近 10 个或最近 12 个月正式包，老包只保留发布记录和 SHA”的口径单独清理。

当前正在下发的正式包为 `1.0.19(20)`，APK URL 是 `https://download.nongjiqiancha.cn/android/releases/20/nongjiqiancha-1.0.19-v20-9508c565.apk`，SHA-256 为 `2f2d202d64893a6fcf72b14929bd6f8555e435123e7ef2be324e62fa319bf41a`，文件大小 `14,426,056` 字节，`force_update=false`。后台检查更新、官网正式下载按钮和 `download.nongjiqiancha.cn` 公网下载都已对齐该物料；旧正式包 `1.0.18(19)` / `1.0.17(18)` / `1.0.16(17)` / `1.0.15(16)` / `1.0.14(15)` / `1.0.13(14)` / `1.0.12(13)` / `1.0.11(12)` / `1.0.10(11)` / `1.0.9(10)` / `1.0.8(9)` / `1.0.7(8)` / `1.0.6(7)` / `1.0.5(6)` / `1.0.4(5)` / `1.0.3(4)` / `1.0.2(3)` / `1.0.1(2)` 分别保留在 `android/releases/19/` / `android/releases/18/` / `android/releases/17/` / `android/releases/16/` / `android/releases/15/` / `android/releases/14/` / `android/releases/13/` / `android/releases/12/` / `android/releases/11/` / `android/releases/10/` / `android/releases/9/` / `android/releases/8/` / `android/releases/7/` / `android/releases/6/` / `android/releases/5/` / `android/releases/4/` / `android/releases/3/` / `android/releases/2/` 和发布历史里，不再作为当前下发目标。

本机不作为正式包仓库，不需要长期堆 APK。仓库不再保留本机 APK 清理脚本，也不做后台自动清理；需要整理本机 APK 构建产物时，由用户明确提出后，Codex 再先列出目标路径和体积，确认只涉及生成物后单次人工处理。任何清理都不得删除源码、签名配置、Git 记录、云端正式包或后台发布历史。

## 用户侧入口

- App 启动进入主界面后，会静默请求一次 `GET /api/app/update`；如果服务端返回了更高版本，且当前设备还没对这个 `latest_version_code` 看过弹窗，App 会自动弹一次“发现新版本”，用户只需点“稍后 / 立即更新”
- Android 设置页点击“检查更新”
- App 请求 `GET /api/app/update?platform=android&version_code=<当前versionCode>&version_name=<当前versionName>`
- 无更新：提示“当前没有可用更新”
- 有更新：弹“发现新版本”卡片，按钮为“稍后 / 立即更新”
- 点“立即更新”：App 下载后端返回的 `apk_url` 到本地 cache，下载中会显示百分比进度和进度条；网络慢时点“稍后”会取消本次下载、清理临时 APK，并提示用户可稍后再更新。下载完成后通过 FileProvider 调起 Android 系统安装页；Android 侧优先使用系统包安装器 action，失败时保留通用 APK `ACTION_VIEW` 兜底，降低不同 ROM 安装页兼容风险
- 客户端也会 fail closed：只有更高 `versionCode`、`download.nongjiqiancha.cn/android/releases/` 下的 HTTPS APK 裸地址、合法 SHA-256 和正数文件大小都齐全时，才会把服务端响应当成可用更新；APK URL 若带 userinfo、query string、fragment、测试包标记或非正式路径，会按物料非法处理。下载入口若遇到物料缺失 / 非法，也会直接失败并上报 `MissingReleaseMetadata`，用户侧统一提示“当前没有可用更新”
- Android 8+ 如果用户还没允许本 App 安装未知应用，会先打开系统授权页；用户授权后返回 App，会自动继续本次下载 / 安装流程
- App 会把检查更新关键阶段通过自动日志上报到后台：检查开始、有新版本、手动检查无新版本、检查失败、需要安装未知应用权限、开始下载、下载失败、安装页打开失败、已拉起系统安装页、安装完成和安装未完成。日志只包含阶段、版本号、是否强更、是否配置 APK / SHA / 文件大小、失败原因、HTTP 状态和已安装版本号，不上传 APK URL、SHA-256、手机号、token 或其他敏感内容。
- 用户在未开始下载时点“稍后”会记录该版本已经提示过，避免同一个版本在每次启动时反复弹窗；用户仍可在设置页手动点“检查更新”再次打开同一版本的更新卡片。用户在下载中点“稍后”表示取消本次下载，不记录成安装失败。
- 用户点“立即更新”并成功拉起系统安装页后，App 会把正在安装的目标版本落到本地偏好里；如果用户取消安装、返回 App、安装页期间进程被杀或重启后版本仍没变化，客户端会清掉该版本的提示抑制，让后续自动检查仍能再次提醒。同一版本安装成功后才继续保留已提示记录。
- 如果多次测试更新后本机 cache 里残留旧下载文件，可在设置页“账号管理”点“清理临时缓存”。该入口只清理检查更新下载残留和相机临时文件，不删除登录态、聊天历史、会员权益、礼品卡、帮助反馈或待发送图片。

自动提醒不会变成系统通知，也不会在同一个版本号上反复骚扰用户；只有后台把 `latest_version_code` 提高后，App 才会再对这个新版本自动弹一次。默认只做普通更新，不启用强制更新。

更新说明默认统一为“优化使用体验。”。以后普通发版时后台“更新说明”可以留空，后端会向 App 返回这句默认文案，App 端也有同一句兜底；除非用户明确要求，不单独编写花哨更新说明。

Android 普通 App 不能静默安装 APK，最终一定要经过系统安装确认。Android O 及以上还需要先检查本 App 是否被允许“安装未知应用”；未允许时只跳系统授权页，用户返回 App 且授权成功后，再继续同一份更新下载 / 安装流程。

## 修复和正式发布口令

你不需要记命令或手工拼配置。以后如果你只说“修这个 bug / 改这个问题 / 全盘挑刺后修一下”，默认只做代码修改、测试、提交、推送和必要的后端 / 后台 / 官网部署，不构建 release APK，不上传正式 APK，不配置检查更新，也不对外下发新版本。

只有你明确说“发一个新版本 / 发布新版本 / 打正式包 / 对外下发 / 配置检查更新 / 发修复包”时，才进入下面的正式 Android 发版流程。

可以直接说：

- “发一个新版本”
- “停掉这个更新”
- “这个版本有问题，发修复包”

进入正式发布流程后，Codex 默认按下面流程处理：

1. 先判断问题属于 Android、后端、官网、配置还是云资源；如果只是后端问题，优先只发后端，不打 APK
2. 如果必须发 Android 新包，Codex 负责把 Android `versionCode` 加 1，并用固定 release 签名构建 `com.nongjiqiancha` APK；Android 构建固定使用正式 `UPLOAD_BASE_URL=https://api.nongjiqiancha.cn`，当前不支持通过 Gradle 参数或环境变量临时覆盖，特殊联调必须先同步更新仓库规则、构建门禁和项目记忆
3. Codex 负责运行 [check-android-release-artifact.ps1](D:/wuhao/scripts/check-android-release-artifact.ps1)，用最终 `app-release.apk` 本体校验包名、`versionCode`、`versionName`、release 不可调试、权限白名单、签名证书指纹，并确认 APK 不早于当前 Android 构建输入且没有未提交 Android 构建输入，避免旧包或本地脏包被误判可发布；脚本会输出 APK 文件大小和 SHA-256，更新说明默认留空，展示统一默认文案
4. Codex 负责把 APK 上传到自有服务器 / OSS，拿到一个公网 `https://download.nongjiqiancha.cn/android/releases/...apk` 下载链接；低成本长期分发优先走 `download.nongjiqiancha.cn` + OSS，发版前先跑 [check-android-download-domain.ps1](D:/wuhao/scripts/check-android-download-domain.ps1)，具体见 [android-download-distribution.md](D:/wuhao/docs/runbooks/android-download-distribution.md)。内部测试包的 72 小时签名链接不能直接写入正式检查更新；正式发版当前默认应使用长期稳定 release 裸地址。后端按需生成正式下载签名是未来可选方案，未另行实现和验收前不能当作现有正式能力。当前后端、Android、后台、官网和 release-match 脚本都会拒绝外部 APK 域名、非 `/android/releases/` 路径，以及任何带 userinfo、query string 或 fragment 的 APK URL
5. Codex 或运维在管理后台“检查更新”页填写新版本、HTTPS APK、SHA-256 和文件大小；启用更新时页面和服务端都要求输入本次 `versionCode` 作为确认，停更时页面和服务端都要求输入“停更”作为确认；后台每次保存 / 停更都会追加一条 `app_release_events` 发布历史；如必须走环境变量兜底，也要同时配置版本号、HTTPS APK、SHA-256 和文件大小
6. 保存后台配置后，Codex 负责运行 [check-app-update-release-match.ps1](D:/wuhao/scripts/check-app-update-release-match.ps1) 只读核对：本地最终 APK 的 `versionCode / versionName / SHA-256 / 文件大小` 必须和后台“检查更新”配置一致；正式对外下发必须加 `-VerifyDownload`，脚本要从公网下载最终 APK 并重新校验大小和 SHA-256，避免后台配置看起来完整但 OSS 对象不存在、未公开或路径写错
7. 如果这是要给旧包用户推送的自更新包，必须带上旧包 `versionCode` 跑 `-PreviousVersionCode <旧包版本号> -ProbePreviousVersionUpdate`，证明本地 APK、后台配置和公网 `/api/app/update` 都会对这个旧版本返回可更新；上线总门禁必须使用 `check-launch-readiness.ps1 -AppUpdateReleaseGate -AppUpdatePreviousVersionCode <旧包版本号>`，不要只用普通 `-ReleaseGate` 代替检查更新发版门禁
8. 真机回归至少覆盖登录、文字问诊、图片问诊、历史恢复、帮助与反馈、会员中心、检查更新和系统安装页

这件事不需要你手写接口，也不需要你自己拼 JSON。

## 修 bug 时怎么判断要不要发 APK

- 只改 Go 后端、模型提示词、联网策略、Key 池、Nginx、证书、今日农情生成、帮助与反馈后台回复等：通常只部署后端，不需要用户更新 App
- 改 Android UI、登录 SDK、图片选择 / 上传前置逻辑、检查更新客户端校验、权限、协议页、聊天滚动链、会员中心展示等：需要打新 APK
- 改官网介绍、下载按钮、备案 footer：只部署官网，不需要发 App
- 改后端配置导致旧 App 能继续工作：不需要发 APK
- 新包如果用户已经安装，不能用低版本回退，只能发一个更高 `versionCode` 的修复包覆盖

## 回滚时你只做这几步

1. 如果新 APK 有问题，先告诉 Codex 或运维“停掉这个更新”
2. 优先在管理后台“检查更新”页取消“对外启用更新”并保存；这会写入发布历史，旧版 App 后续检查更新会返回“无更新”
3. 如果后台不可用，再把后端运行环境里的 `APP_ANDROID_APK_URL` 清空，或者把 `APP_ANDROID_LATEST_VERSION_CODE` 改回稳定版本号

已经点进系统安装页并完成安装的用户，需要后续再发一个更高 `versionCode` 的修复包来覆盖。

## 后端配置

接口：

- 用户侧：`GET /api/app/update`
- 后台侧：`GET /admin-api/v1/app-update/android`、`POST /admin-api/v1/app-update/android`、`GET /admin-api/v1/app-update/android/events`

当前 Android 检查更新优先读取数据库表 `app_release_configs` 里的 `android` 记录；如果数据库里还没有记录，才回退读后端环境变量。

环境变量兼容字段仍然保留：

- `APP_ANDROID_LATEST_VERSION_CODE`：最新 APK 的 `versionCode`，必须大于客户端当前 `versionCode` 才会返回有更新
- `APP_ANDROID_LATEST_VERSION_NAME`：最新 APK 的展示版本名
- `APP_ANDROID_APK_URL`：APK 下载地址，必须是公网 `https://` URL；非 https 会被后端视为无可用更新
- `APP_ANDROID_APK_SHA256`：必填；APK 文件的 SHA-256，用于客户端下载后校验文件是否被传错、截断或替换。缺失或格式不合法时，后端不会对外返回可用更新
- `APP_ANDROID_RELEASE_NOTES`：更新说明，直接展示在更新卡片里；普通发版默认留空，由后端 / App 统一展示“优化使用体验。”
- `APP_ANDROID_FORCE_UPDATE`：兼容保留字段，普通发版默认不使用；当前后端默认会压成 false，只有未来显式配置 `APP_UPDATE_ALLOW_FORCE_UPDATE=true` 并完成产品 / 合规确认后才允许强制更新
- `APP_UPDATE_ALLOW_FORCE_UPDATE`：强制更新总开关，默认不设置；当前普通发版和后台发布页都不应启用
- `APP_ANDROID_FILE_SIZE_BYTES`：必填，APK 字节大小，用于更新卡片展示和下载后校验；缺失或小于等于 0 时，后端不会对外返回可用更新
- APK 文件大小不能超过 200MB；Android、后端用户接口、后台保存入口和发版校验都按这个上限处理，避免后台保存了一个客户端永远不会下载 / 安装的包
- `APP_ANDROID_UPDATE_ENABLED`：兼容环境变量开关；只有显式配置为 `true / yes / on / 1` 才会启用环境变量兜底更新。未配置时即使版本号、APK URL、SHA-256 和文件大小都存在，也不会下发更新，避免残留环境变量绕过后台发布开关

管理后台“检查更新”页现在已经可以直接维护 Android 更新配置：版本号、版本名、HTTPS APK、SHA-256、文件大小、更新说明和是否对外启用。启用更新时前端会要求输入本次 `versionCode`，停更时要求输入“停更”；服务端 `POST /admin-api/v1/app-update/android` 也会校验同一个确认字段，绕过前端直接调 API 也不能无确认写入启用 / 停更配置。后台保存后会在同一事务里更新 `app_release_configs` 并追加 `app_release_events` 发布历史，`/api/app/update` 会优先按当前配置对外返回；取消“对外启用更新”并保存，就是停更，也会留下停更记录。发布历史只记录版本、物料状态、操作人、时间和更新说明，不替代 APK 文件上传、真机覆盖安装验收或正式回滚演练。

管理后台“检查更新”页和监控面板把两个口径分开展示：`config_valid` 表示版本号 / APK URL / 文件大小上限这组配置是否合法；`download_artifacts_complete` 表示正式下载物料是否齐全，只有自有下载域名下的 HTTPS release APK、SHA-256 和 1 到 200MB 的文件大小都配置时才为 true。上线或发包前以后者判断“正式包物料是否已经齐”；公开 `/api/app/update` 也按这条口径下发，物料不齐时返回无更新并在服务端记录 `missing_release_artifacts` 或 `apk_too_large`。

日常 readiness / 公网黑盒按“先证明入口可达，再按发版口令验证旧包更新”的口径运行：`check-ecs-readiness.ps1` 会拦截 `APP_ANDROID_UPDATE_ENABLED=true` 和 `APP_UPDATE_ALLOW_FORCE_UPDATE=true` 这类误开的环境变量开关；`check-public-blackbox.ps1` 默认只验证 `/api/app/update` 可达并打印当前探针结果，也会顺手跑下载域名 / OSS CNAME 探针，避免检查更新接口或下载链路证书 / CNAME 已坏。正式发版或下发验证必须走 `check-app-update-release-match.ps1 -PreviousVersionCode <旧包versionCode> -ProbePreviousVersionUpdate`，或在公网黑盒中同时显式传 `-PreviousAndroidVersionCode` 和 `-ExpectedAndroidUpdateVersionCode`，证明真实旧包会看到 `has_update=true`。

管理后台“监控面板”已新增“检查更新排障”卡，聚合最近 24 小时 `app_update.*` 自动日志，并提供直达 App 日志筛选按钮。若真机测试更新失败，优先按下面顺序看：

1. `app_update.check_failed`：App 请求 `/api/app/update` 失败、返回非 2xx 或响应体异常。
2. `app_update.install_permission_required`: Android 8+ 需要用户开启“安装未知应用”权限。
3. `app_update.download_failed`：下载失败、最终响应非 HTTPS、文件过大、大小不一致、SHA / 包名 / versionCode 校验失败或写入 cache 失败。
4. `app_update.install_intent_failed`：APK 已下载但系统安装页没有成功打开。
5. `app_update.install_started`：已成功拉起系统安装页，后续是否确认安装由 Android 系统和用户操作决定。
6. `app_update.install_not_completed`：系统安装页已拉起，但用户取消、安装失败、进程重启后版本仍未变化，App 会清掉本版本提示抑制，后续可再次提醒。

客户端下载后会在调起系统安装页前做基础校验：

- 下载最终响应仍必须是 https。
- 后端只会在文件大小和 SHA-256 都已配置时下发更新；客户端也要求文件大小为正且不超过 200MB，下载后文件大小必须一致。
- 后端只会在 SHA-256 合法时下发更新；客户端也要求 SHA-256 合法，下载后文件哈希必须一致。
- APK 包名必须等于当前 App 包名，APK `versionCode` 必须等于后端下发的最新版本号，且大于当前已安装版本。
- 安装包必须来自 App 自己的 `cacheDir/app_updates` 并通过 `${applicationId}.fileprovider` 授予临时读取权限，不能把裸文件路径暴露给其它应用。`scripts/check-android-build-parity.ps1` 会锁住 `REQUEST_INSTALL_PACKAGES`、FileProvider `app_updates` 路径、未知来源授权返回续下、下载进度百分比、下载中可取消、下载中防重复点击、官方包安装 action 和 `ACTION_VIEW` 兜底。

## 发布流程

1. 构建 release APK，并确认 `app/build.gradle.kts` 里的 `versionCode` 比线上旧包更大、`applicationId` 仍是 `com.nongjiqiancha`，且 release 构建使用 https `UPLOAD_BASE_URL`
2. 运行：

```powershell
.\scripts\check-android-release-artifact.ps1
```

脚本会直接检查最终 `app/build/outputs/apk/release/app-release.apk`，并输出 `apk_size_bytes`、`apk_sha256`、`apk_package`、`apk_version_code`、`apk_version_name` 和 `apk_cert_sha256`。其中 `apk_size_bytes`、`apk_sha256` 和版本号用于填写后台“检查更新”页；证书指纹用于确认仍是固定 release 签名。
3. 把 APK 上传到自有服务器或 OSS，确保可以通过公网 https 下载，不建议让 Go 后端动态服务大 APK；低成本长期分发优先走 `download.nongjiqiancha.cn + OSS` 的 `/android/releases/` 正式路径，发版前先跑 [check-android-download-domain.ps1](D:/wuhao/scripts/check-android-download-domain.ps1)，具体见 [android-download-distribution.md](D:/wuhao/docs/runbooks/android-download-distribution.md)。内部测试包短签名链接不能进入正式检查更新，非测试路径的短期签名 URL 也不能作为正式 APK 地址；正式 APK URL 必须是无 userinfo、无 query、无 fragment 的稳定裸地址
4. 在管理后台“检查更新”页填写版本号、HTTPS APK、SHA-256 和文件大小，更新说明留空即可，勾上“对外启用更新”后保存；保存成功后检查“发布历史”出现本次记录；如暂时不走后台，也可继续改 `APP_ANDROID_*` 环境变量，但环境变量兜底不会自动写发布历史
5. 运行后台配置对账：

```powershell
.\scripts\check-app-update-release-match.ps1 -RequireEnabled -VerifyDownload
```

该脚本会先复用最终 APK 物料校验，再登录后台只读读取 `/admin-api/v1/app-update/android`，核对后台版本号、版本名、SHA-256 和文件大小是否与本地最终 APK 一致，并确认 APK URL 是公网 HTTPS。正式对外下发时必须保留 `-VerifyDownload`，让脚本从公网下载后台 APK 链接并重新比对大小和 SHA-256，同时确认最终下载地址没有从 HTTPS 跳到 HTTP。若这是一次给旧包用户推送的自更新发版，再加 `-PreviousVersionCode <旧包版本号> -ProbePreviousVersionUpdate`，证明本地 APK 和后台版本都大于旧包，并且公网 `/api/app/update` 会对该旧版本返回 `has_update=true`。
6. 用旧版本 App 点击“检查更新”验证：应出现“发现新版本”卡片
7. 点“立即更新”验证下载、校验、未知来源授权和系统安装页是否能正常打开

发布时还要记录签名证书指纹。当前 release 签名公钥和指纹信息保存在本机 `%USERPROFILE%\\.nongjiqiancha\\android-release-public-info.txt`；签名密码配置保存在 `%USERPROFILE%\\.nongjiqiancha\\android-release-signing.properties`，不能提交到 git 或写入公开文档。

## 回滚

- 若新包有问题，最直接的是在管理后台“检查更新”页取消“对外启用更新”并保存；兼容路线仍可清空 `APP_ANDROID_APK_URL`
- 后端会返回无更新，客户端不再提示用户下载该 APK
- 已经下载到用户手机 cache 的 APK 不会被主动安装，除非用户已经进入系统安装页并继续安装
- 已经完成安装的用户，不能用低 `versionCode` 覆盖回去，只能再发一个更高 `versionCode` 的修复包

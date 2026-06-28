# Android 下载分发 Runbook

最后更新：2026-06-23

本 runbook 记录 Android APK 的低成本下载方案。它只解决“安装包从哪里下载、如何校验、如何控制成本”，不等于正式发布口令。

## 当前推荐方案

首版低成本路线：

1. APK 文件放 OSS `nongjiqiancha-prod`。
2. 绑定自有下载域名 `download.nongjiqiancha.cn` 到 OSS Bucket。
3. 给下载域名配置 HTTPS 证书。
4. 测试包和正式包走不同前缀：
   - 内部测试包：`test-apks/debug/...`，短期测试链接。发布脚本在新包上传并通过自有下载域名探针后，会清理同前缀旧 debug/internal APK，只保留最新 1 个，避免用户下载错包；OSS `test-apks/` 3 天生命周期仍作为兜底。
   - 正式包：`android/releases/<versionCode>/...apk`，长期保留当前版本和最近若干可回滚版本。
5. 早期不默认上 CDN，不买高防。下载量或管理层集中下载造成明显卡顿时，再把 CDN 放在 OSS 前面。

这套方案的成本主要是 OSS 下行流量和请求费，没有固定高防或 CDN 月费；当前正式 APK 约 14MB，早期几十到几百次下载成本很低。OSS 已有 100GB 标准存储包，覆盖的是存储容量，不覆盖所有下行流量。

## 为什么不直接用 OSS 默认链接

阿里云 OSS 默认公网 endpoint 不适合作为 APK 对外下载地址，实测会出现 `ApkDownloadForbidden`。对外发给用户、管理层、代理或写入 App 检查更新的链接，必须使用自有 HTTPS 下载域名，例如：

```text
https://download.nongjiqiancha.cn/android/releases/...
```

不要使用：

```text
https://nongjiqiancha-prod.oss-cn-beijing.aliyuncs.com/...
```

## 当前状态

截至 2026-06-23，`download.nongjiqiancha.cn + OSS` 已跑通，作为内部测试包和正式包的低成本下载主链：

- DNS：`download.nongjiqiancha.cn` CNAME 到 `nongjiqiancha-prod.oss-cn-beijing.aliyuncs.com`。
- OSS：Bucket 仍保持 private 主口径；为支持正式检查更新和官网下载长期裸 URL，Bucket 级 Block Public Access 已关闭，并配置 Bucket Policy 仅允许匿名 `oss:GetObject` 访问 `android/releases/*`。不要把策略扩大到 `uploads/*`、`support/*`、`test-apks/*` 或整个 Bucket。内部测试包仍通过签名 URL 下载，不走公开读。
- HTTPS：下载域名已绑定免费 Let’s Encrypt 证书，当前证书到期日为 `2026-09-15 07:03:04 UTC`；2026-06-17 已因旧同步脚本曾把私钥放进 Cloud Assistant 输出而强制重签该证书，并用加固后的脚本重新同步到 OSS。ECS 上 `certbot.timer` 负责后续免费证书续期。
- 验证：`scripts/check-android-download-domain.ps1` 会检查 DNS、OSS CNAME、HTTPS 证书可见性、公网 TLS 证书到期时间，并用自有域名签名 HEAD 探针验证访问；证书临期时会提示同步 OSS CNAME 证书。2026-06-18 起，`scripts/check-resource-capacity.ps1 -Strict` 也会调用这条下载域名检查；正式发布前资源门禁不能漏掉下载域名。
- 发布：`scripts/publish-android-test-apk.ps1` 默认会上传 debug/internal APK 到 OSS `test-apks/debug/...`，生成 `https://download.nongjiqiancha.cn/...` 签名链接，默认 72 小时有效；新包 HEAD / range 下载探针通过后，脚本会删除同前缀旧 `nongjiqiancha-debug-internal-*.apk`，只保留最新 1 个。发布脚本会在上传前只读校验 OSS `test-apks/` 生命周期规则仍启用；该 3 天生命周期是兜底，不再作为主要清理策略。脚本不写本机 / ECS 清理 cron；`-UseEcsDownloadFallback` 已退役并会被脚本拒绝，不能再临时回退旧 ECS `/test-apks/` 路径。

发测试包前先跑：

```powershell
.\scripts\check-android-download-domain.ps1
```

日常应看到 `status=ready` 且命令正常退出。若脚本输出 `attention` 或 `failed`，不要把下载链接发给代理或管理层，先按脚本提示修 DNS、OSS CNAME、HTTPS 证书或签名链；`-AllowAttentionExitZero` 只适合作为报告模式，不作为发布前放行门禁。

签名工具 `scripts/sign-oss-cname-url.py` 默认只允许内部测试包前缀 `test-apks/debug/` 和只读探针前缀 `download-probes/`；其它对象必须显式 `--allow-unsafe`，不能把它当成随手给任意 OSS 对象签长期链接的工具。

旧的官网 `/test-apks/` 路径已停用，不再作为临时回退：

```text
https://nongjiqiancha.cn/test-apks/debug/...
```

这条路径走 ECS 5Mbps 公网带宽，容易被多人同时下载卡住，且当前官网 Nginx 黑盒要求 `/test-apks/` 返回 404。若下载域名异常，应先修 OSS CNAME、证书或签名链；不要把 ECS `/test-apks/` 恢复成常规发布路径，更不能进入 App 内检查更新、官网正式下载按钮或应用商店材料。

## 证书续期

ECS 上 `certbot.timer` 会自动续期免费证书，但 OSS 自定义域名证书绑定不是 Nginx 自动读取文件，续期后需要把新证书同步到 OSS CNAME 配置。

同步脚本：

```powershell
.\scripts\sync-oss-download-certificate.ps1
```

脚本会先在本机生成一次性 RSA 公钥，把公钥随远端脚本发到 ECS；ECS 读取 `download.nongjiqiancha.cn` 的证书和私钥后，用随机 AES key 加密证书包，并用一次性 RSA 公钥加密 AES key。Cloud Assistant 输出里只包含证书 subject / issuer / 有效期和加密后的 payload，不再出现明文私钥；本机解密后调用 OSS CNAME 证书配置接口更新绑定，临时密钥和 XML 文件会在脚本结束时删除。执行后再跑：

```powershell
.\scripts\check-android-download-domain.ps1
```

当前本机已创建每周自动巡检“农技千查续费与证书巡检”：它会检查证书、云资源到期、资源包和费用，并在发现 ECS 证书已续期但 OSS 下载域名证书未同步时尝试运行同步脚本。该自动化不购买、不续费、不退订、不删除任何付费资源。

## 正式包发布边界

正式发布仍必须等用户明确口令。没有口令时，不做这些动作：

- 不生成并对外发布正式 release APK。
- 不配置后台“检查更新”启用。
- 不改官网正式下载按钮。
- 不上传应用商店。

正式包发布时必须保留：

- `versionCode / versionName`
- APK URL
- SHA-256
- 文件大小
- commit
- 签名证书指纹

当前正在下发的正式包记录：

- `versionName=1.0.12`
- `versionCode=13`
- APK URL：`https://download.nongjiqiancha.cn/android/releases/13/nongjiqiancha-1.0.12-v13-a0aaaa6e.apk`
- SHA-256：`c4092eef8590a1cbd5e73eb1e150b5348ac8302fec1dcf0c5b9f297e07487f13`
- 文件大小：`14,426,056` 字节
- 发布提交：`a0aaaa6e`

上一版 `1.0.11(12)`、`1.0.10(11)`、`1.0.9(10)`、`1.0.8(9)`、`1.0.7(8)`、`1.0.6(7)`、`1.0.5(6)`、`1.0.4(5)`、`1.0.3(4)`、`1.0.2(3)` 和首个正式包 `1.0.1(2)` 仍作为历史正式包保留在 OSS `android/releases/12/`、`android/releases/11/`、`android/releases/10/`、`android/releases/9/`、`android/releases/8/`、`android/releases/7/`、`android/releases/6/`、`android/releases/5/`、`android/releases/4/`、`android/releases/3/`、`android/releases/2/` 和后台发布历史中，用于审计、排障和必要时对照；已经安装 `versionCode=13` 的用户不能用低版本覆盖，只能继续发更高 `versionCode` 修复包。

注意：正式包不能长期写死 72 小时测试签名 URL。正式发版时要使用长期稳定的正式 release 裸地址，或由后端检查更新接口另行实现并验收“按需生成可用下载链接”的完整方案；当前后台检查更新、官网、后端、Android 和 release-match 脚本都会拒绝带 userinfo、query string 或 fragment 的 APK URL，并继续校验 HTTPS、SHA-256、文件大小、包名、签名和 `versionCode`。

后台“检查更新”启用前继续跑：

```powershell
.\scripts\check-app-update-release-match.ps1 -RequireEnabled -VerifyDownload
```

正式对外下发时必须保留 `-VerifyDownload`，并在旧包自更新场景追加 `-PreviousVersionCode <旧包版本号> -ProbePreviousVersionUpdate` 或直接跑 `check-launch-readiness.ps1 -AppUpdateReleaseGate -AppUpdatePreviousVersionCode <旧包版本号>`；普通 `-ReleaseGate` 不能替代检查更新发版门禁。

## 后续什么时候上 CDN

先不上 CDN。满足任一条件时再评估：

- 管理层 / 代理 / 用户下载 APK 经常卡在中途。
- 官网或 App 内更新下载量明显上来。
- ECS 5Mbps 被测试包下载挤占，影响官网或其它公网访问。
- 需要更稳定的 Range 下载、就近访问或更细流量报表。

上 CDN 时，仍以 OSS 为源站，下载域名改 CNAME 到 CDN，HTTPS 证书在 CDN 侧配置；App 内检查更新仍只认 HTTPS APK、SHA-256 和文件大小，不因 CDN 改变校验口径。

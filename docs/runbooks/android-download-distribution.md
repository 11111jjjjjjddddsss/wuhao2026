# Android 下载分发 Runbook

最后更新：2026-06-17

本 runbook 记录 Android APK 的低成本下载方案。它只解决“安装包从哪里下载、如何校验、如何控制成本”，不等于正式发布口令。

## 当前推荐方案

首版低成本路线：

1. APK 文件放 OSS `nongjiqiancha-prod`。
2. 绑定自有下载域名 `download.nongjiqiancha.cn` 到 OSS Bucket。
3. 给下载域名配置 HTTPS 证书。
4. 测试包和正式包走不同前缀：
   - 内部测试包：`test-apks/debug/...`，短期保留，默认只留最新。
   - 正式包：`android/releases/<versionCode>/...apk`，长期保留当前版本和最近若干可回滚版本。
5. 早期不默认上 CDN，不买高防。下载量或管理层集中下载造成明显卡顿时，再把 CDN 放在 OSS 前面。

这套方案的成本主要是 OSS 下行流量和请求费，没有固定高防或 CDN 月费；当前 APK 约 20MB，早期几十到几百次下载成本很低。OSS 已有 100GB 标准存储包，覆盖的是存储容量，不覆盖所有下行流量。

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

截至 2026-06-17，`download.nongjiqiancha.cn + OSS` 已跑通，作为内部测试包的推荐低成本下载主链：

- DNS：`download.nongjiqiancha.cn` CNAME 到 `nongjiqiancha-prod.oss-cn-beijing.aliyuncs.com`。
- OSS：Bucket 仍保持 private，不开放公共读；测试包通过签名 URL 下载。
- HTTPS：下载域名已绑定免费 Let’s Encrypt 证书，当前证书到期日为 `2026-09-15 07:03:04 UTC`；2026-06-17 已因旧同步脚本曾把私钥放进 Cloud Assistant 输出而强制重签该证书，并用加固后的脚本重新同步到 OSS。ECS 上 `certbot.timer` 负责后续免费证书续期。
- 验证：`scripts/check-android-download-domain.ps1` 会检查 DNS、OSS CNAME、HTTPS 证书可见性，并用自有域名签名 HEAD 探针验证访问。
- 发布：`scripts/publish-android-test-apk.ps1` 默认会上传 debug/internal APK 到 OSS `test-apks/debug/...`，生成 `https://download.nongjiqiancha.cn/...` 签名链接，默认 72 小时有效，并清理旧测试包只留最新；只有显式 `-UseEcsDownloadFallback` 才允许临时回退旧 ECS `/test-apks/` 路径。

发测试包前先跑：

```powershell
.\scripts\check-android-download-domain.ps1 -AllowAttentionExitZero
```

日常应看到 `status=ready`。若脚本输出 `attention` 或 `failed`，不要把下载链接发给代理或管理层，先按脚本提示修 DNS、OSS CNAME、HTTPS 证书或签名链。

旧的官网路径仍可作为临时回退：

```text
https://nongjiqiancha.cn/test-apks/debug/...
```

这条路径走 ECS 5Mbps 公网带宽，容易被多人同时下载卡住；现在不作为推荐主链。若临时回退到 ECS 路径，仍必须只用于 debug/internal 测试包，不进入 App 内检查更新、官网正式下载按钮或应用商店材料。

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

注意：正式包不能长期写死 72 小时测试签名 URL。正式发版时要使用长期稳定的正式 release 地址，或由后端检查更新接口按需生成可用下载链接；后台检查更新和 release-match 脚本会拒绝带 `Expires / Signature / OSSAccessKeyId / x-oss-signature` 等短签名参数的 APK URL，并继续校验 HTTPS、SHA-256、文件大小、包名、签名和 `versionCode`。

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

# 下一阶段上线推进计划

最后更新：2026-06-15

## 目的

把“农技千查”从当前本地开发状态推进到可备案、可部署、可上架的顺序固定下来。后续新窗口先看本文件，不再靠聊天记录回忆下一步。

这份计划只记录执行顺序和依赖关系；已购买的 ECS / RDS / OSS / 域名、已通过的网站 ICP 备案、已下发的网站公安备案号、已配置的 HTTPS 和模型 Key 槽位写成当前事实，未落地的 App 备案号、App 公安备案号、真实密钥值、后端服务地址或后台地址不伪造。

## 总判断

- 备案不要等手机号登录做完后才开始；买好符合备案条件的中国内地云资源和域名后，应立即启动 ICP / App 备案。当前网站 ICP 已通过，App 备案已提交阿里云初审，仍待审核通过。
- 手机号登录、ECS 后端部署、RDS、OSS、SLS、帮助与反馈、检查更新、模型 Key 池等技术联调，可以在备案审核等待期间并行推进。
- 应用商店审核和备案不是同一个口：商标注册通常不是上架硬前置，但 App 名称、图标、软著 / 电子版权、备案号、隐私合规和截图物料需要尽量一致。
- 当前 App 名称按“农技千查”推进；公司主体仍是“北京农技千问科技有限公司”，不要把公司名机械改成产品名。
- Android 对外安装身份已定为 `applicationId = com.nongjiqiancha`；Kotlin 源码包 / namespace 暂保留 `com.nongjiqianwen` 作为内部代码命名空间，不决定备案或安装身份。

## P0：部署和备案前先定死的材料

目标：减少备案和应用商店物料返工。

- App 名称：已按“农技千查”准备。
- App 图标：当前备案使用 APK 内真实黑底绿色旋叶图标，与 `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` 和桌面备案资料中的 `农技千查-App图标-512.png` 保持一致；不要把临时生成或对比预览图误当正式图标上传。
- 包名、签名证书、公钥、指纹：Android 对外包名已定为 `com.nongjiqiancha`；本机已生成 release 签名，备案用指纹和 RSA 公钥信息保存在 `%USERPROFILE%\\.nongjiqiancha\\android-release-public-info.txt`，签名配置保存在 `%USERPROFILE%\\.nongjiqiancha\\android-release-signing.properties`，这些私钥和密码不进仓库。
- 包名切换策略：如果此前只有内测 / 本机安装过旧 `com.nongjiqianwen` 包，则新包按全新 App 身份安装，测试机需要卸旧包重装；不能指望旧包通过“检查更新”覆盖安装成新包。如果已经有公开用户装过旧包，必须另做迁移 / 告知方案。
- 协议材料：App 内 6 个服务协议页面已按当前真实功能重写为成品口径；正式公开推广、上架或接真实支付前，仍需按真实服务商、权限、支付、备案号和法务意见复核。
- 软著 / 电子版权：尽早准备；它比商标更常用于国内应用商店上架材料。
- 阿里云账号与账单：确认生产资源归属到可长期控制的账号；朋友的百炼 Key 可以短期兜底，但生产账单、权限和密钥轮换最终应收回到自己可控体系。

## P1：云资源已买后立刻做

目标：先把等待周期最长的备案跑起来。

1. 已完成：域名 `nongjiqiancha.cn` 已购买，用户口头确认实名认证 / 模板审核已通过。
2. 已完成：按 `ECS + RDS MySQL + OSS + 域名 / HTTPS` 落最小生产链的主要资源，其中 ECS / RDS / OSS 存储包已买，OSS Bucket / 生命周期和生产上传后端已切通，DNS 已创建 `api.nongjiqiancha.cn`、`nongjiqiancha.cn`、`www.nongjiqiancha.cn` 到 `39.106.1.151` 的 A 记录，`server-go` 已部署到 ECS 并由 Nginx 双端口 slot 反代，`api.nongjiqiancha.cn` HTTPS 已配置并公网验证通过，根域名官网 HTTPS 已部署并公网验证通过，DashScope 主 / 副模型 Key 已配置并显示 `bailian=ok`；App 备案通过、App 公安备案和真机登录 / 主聊天 / 图片问诊回归尚未完成。
3. 已完成：网站 ICP 备案已通过，主体备案号 `京ICP备2026031728号`，网站备案号 `京ICP备2026031728号-1`，网站名称“农技千查”，域名 `nongjiqiancha.cn`。
4. 已提交：2026-06-05 20:03 左右 App 备案订单进入阿里云初审，订单号 `2036780517515`；截至 2026-06-12 仓库尚未记录 App 备案号、通过或补正结论，后续需登录阿里云备案控制台确认当前审核 / 补正状态，并按阿里云 / 管局要求接听审核电话、处理短信核验和补正材料。
5. 如果备案控制台提示当前资源不满足备案校验，再补最低成本的可备案云产品兜底；后端当前优先部署在 ECS，若后续因平台能力重新启用 SAE，必须同步更新 runbook。

注意：
- 网站 ICP 已过，`api.nongjiqiancha.cn` HTTPS、根域名官网 HTTPS 和模型 Key 主 / 副槽位已配，但 App 备案、App 公安备案和真机登录 / 主聊天 / 图片问诊回归未闭环前，仍不要把它当成完整生产上线。
- 备案过程中若 App 图标、名称、包名、签名等核心物料改动，可能需要重新提交或后续变更。
- 公安联网备案数据码只用于全国互联网安全管理服务平台提交，不写入仓库或聊天交接。

## P2：备案等待期间并行开发 / 联调

目标：不要干等备案，把真实后端链路补齐。

- 继续维护 ECS 上的 `server-go`、`systemd`、Nginx 反向代理、健康检查、环境变量和基础日志；生产由 systemd slot 设置 `PORT=3000/3001`，Nginx 切 active upstream，3000 只是本地默认 fallback，公网只走 Nginx。
- 接 RDS MySQL，跑迁移，确认备份、白名单和只读排查方式。
- DashScope 主 / 副模型 Key 已配置；继续按 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md) 固化来源、充值、告警和轮换责任。若后续要扩真实并发，必须使用不同阿里云主账号 Key；同一主账号多 Key 只适合轮换或应急。
- 接手机号登录 / 服务端可验证 token，并在公开生产环境开启 `AUTH_STRICT=true`，逐步关闭裸 `X-User-Id` 兜底；正式 release APK 不使用共享静态 `SESSION_API_TOKEN`，由后端按真实用户动态签发 per-user token。
- 接 OSS 图片存储，配置 `BASE_PUBLIC_URL / UPLOAD_BASE_URL`，确保模型能访问 https 图片。
- SLS 最小日志集已接入服务端 JSON 日志和 Nginx error log，并已有 5 条 AlertHub 最小告警；已补邮件通知 / 最小仪表盘，后续继续覆盖主对话、上传、帮助与反馈、今日农情、检查更新、资源水位和模型调用失败。
- 若首版暂不接 OSS，则 ECS 必须先保持单台；计划多后端实例前必须先把 `/upload` 和 `/uploads/` 从本机磁盘迁到 OSS 或等价共享对象存储。
- 数据库迁移不要在多实例首次启动时抢跑；多实例发布前应把迁移改成单独发布步骤或补迁移锁。
- 验证主聊天 SSE、图片上传、记忆文档摘要、今日农情、会员额度、帮助与反馈、礼品卡占位页 / 后端兑换接口、检查更新 APK 链路。
- 准备应用商店物料：软著 / 电子版权、隐私政策链接或页面、测试账号、截图、应用描述、权限说明和备案信息占位。

## P3：网站 ICP 通过后马上补

目标：让 App、后端和材料进入可公开测试状态。

- 官网首版代码已在 `site` 目录准备好并通过 [deploy-ecs-site.ps1](D:/wuhao/scripts/deploy-ecs-site.ps1) 部署到 `https://nongjiqiancha.cn/` 和 `https://www.nongjiqiancha.cn/`，包含 App 介绍、安卓下载入口和备案 footer；网站 ICP 备案号已展示在公开页脚并链接工信部备案系统。App 内协议 / 隐私政策先记录网站 ICP 备案状态，App 备案号必须等 App 备案通过后再补。
- 跟进 App 备案阿里云初审、管局提交、工信部短信核验和管局审核；当前网站 ICP 已通过不等于 App 备案已通过。
- `api.nongjiqiancha.cn` HTTPS、根域名官网 HTTPS、后端公开 API 基地址、图片公开基地址和 `admin.nongjiqiancha.cn` 后台 HTTPS 已完成；后续下载域名如果启用，需要单独配置入口和证书。
- 确认 `APP_ANDROID_*` 检查更新环境变量指向正确 APK、版本号、文件大小、SHA-256 和更新说明。
- 做一次完整真机回归：清数据、登录、文字问诊、图片问诊、历史恢复、删除历史对话、帮助与反馈、检查更新、会员中心、礼品卡页、协议页、今日农情。
- 网站公安联网备案号已于 2026-06-10 下发，官网 footer 使用真实编号 `京公网安备11010602202723号`、警徽图标和查询链接。App 备案通过并开通后，再按实际要求补 App 对应公安备案信息。

## 上线门禁脚本

上线前综合巡检入口为 [scripts/check-launch-readiness.ps1](D:/wuhao/scripts/check-launch-readiness.ps1)。

默认运行：

```powershell
.\scripts\check-launch-readiness.ps1
```

默认会串联项目记忆校验、后台 surface 合同、Android debug / release 业务一致性、ECS readiness、公网黑盒、SLS 告警严格巡检、资源容量严格巡检、短信发送统计巡检、后端账号资产归属 / 账号完整性巡检和“人工上线确认项”；如果当前 PowerShell 没有临时设置后台 smoke 凭据，登录后后台 smoke 会标成 attention，脚本退出码为 2，不再假绿。推荐使用 `NONGJI_ADMIN_USERNAME` / `NONGJI_ADMIN_PASSWORD`；`ADMIN_SMOKE_USERNAME` / `ADMIN_SMOKE_PASSWORD` 是与单独 smoke 脚本一致的兼容别名。日常只想看报告时可显式加 `-AllowAttentionExitZero`，正式上线门禁不要加。短信统计巡检会校验阿里云接口状态，默认不按签名过滤，空统计默认短暂重试一次，并能看近期发送成功 / 失败 / 无回执趋势；它还会查询费用中心有效资源包，若没看到短信类套餐包会输出 `sms_package_status=not_visible_manual_required`。这些都不等于短信套餐包余额已人工确认；真实上架前仍要在短信服务控制台确认套餐包余量、到期、余量预警和自动复购。

人工上线确认项用于把脚本无法自动证明的事项直接暴露在总门禁末尾。正式上架前逐项确认后，可在当前 PowerShell 临时设置对应环境变量为 `1 / true / yes / ok / ready / confirmed / done`；不要把这些确认变量写进仓库或长期 shell 配置。当前确认项包括：

| 环境变量 | 确认内容 |
| --- | --- |
| `NONGJI_APP_ICP_CONFIRMED` | App 备案已通过，备案号和材料可用于上架 |
| `NONGJI_APP_POLICE_CONFIRMED` | App 公安备案已按最终 App 信息处理 |
| `NONGJI_ACCESS_KEYS_ROTATED_CONFIRMED` | 已暴露或主账号 AccessKey 已轮换，生产改用最小权限凭证 |
| `NONGJI_FINAL_DEVICE_REGRESSION_CONFIRMED` | 最终 APK 已真机跑完清数据、登录、聊天、图片、今日农情、设置、会员、帮助反馈和检查更新 |
| `NONGJI_SLS_EMAIL_CONFIRMED` | SLS 首封告警邮件已通过真实或测试触发确认送达 |
| `NONGJI_SMS_BALANCE_CONFIRMED` | 普通短信套餐包余额、到期和账单 / 预警已人工确认 |
| `NONGJI_RELEASE_ARTIFACT_CONFIRMED` | 最终 release APK、包名、签名、versionCode 和应用商店材料一致 |

纯技术日常巡检如果暂时不想看人工项，可显式加 `-SkipManualGoLiveChecklist`；正式上线门禁不要跳过。

发版前完整慢检：

```powershell
.\scripts\check-launch-readiness.ps1 -IncludeBuilds
```

`-IncludeBuilds` 会额外跑 `server-go` 测试 / build、`admin` production build、Android debug + release 双包构建、最终 release APK 物料校验，再重新跑 parity。release APK 物料校验由 [check-android-release-artifact.ps1](D:/wuhao/scripts/check-android-release-artifact.ps1) 读取最终 APK 本体，核对包名、版本、权限、不可调试、固定 release 证书指纹，并输出 `apk_size_bytes` 和 `apk_sha256`。这个脚本不替代真机回归、App 备案 / App 公安备案、支付渠道申请、首封 SLS 告警邮件送达确认或 AccessKey 轮换；这些仍必须按本 runbook 和 `open-risks.md` 人工闭环。

如果这次正式包已经上传到后台“检查更新”并准备对外启用，再加后台配置对账：

```powershell
.\scripts\check-launch-readiness.ps1 -IncludeBuilds -CheckAppUpdateReleaseMatch
```

这会额外调用 [check-app-update-release-match.ps1](D:/wuhao/scripts/check-app-update-release-match.ps1)，确认后台当前发布配置的 `versionCode / versionName / SHA-256 / 文件大小` 与本地最终 APK 一致，并要求后台已启用更新。若要连后台 APK 链接也下载回来重算大小和 SHA-256，可再加 `-VerifyAppUpdateDownload`。日常巡检或首版还未通过自有更新分发时，不默认强制这一步，避免把“暂未启用更新”误报成程序故障。

真正准备打正式包 / 提交上架前，优先使用正式上线门禁：

```powershell
.\scripts\check-launch-readiness.ps1 -ReleaseGate
```

`-ReleaseGate` 会强制启用 `-IncludeBuilds`、最终 release APK 物料校验和后台登录后 owner smoke，且不允许搭配 `-AllowAttentionExitZero` 或任何 `-Skip*` 跳过项；这条命令用于最后放行，不用于日常快速看报告。日常巡检可以继续用默认命令或 `-AllowAttentionExitZero` 看完整输出。

## P4：提交应用商店

目标：先上最小可用版本，不把完整管理后台和支付做成上架前硬门槛。

- 使用最终图标、最终名称、稳定包名和同一签名证书打 release 包。
- release 构建必须使用本机 release 签名配置；Android 构建默认使用正式 https 后端地址 `https://api.nongjiqiancha.cn`，只有特殊联调才覆盖 `UPLOAD_BASE_URL`。缺少 `NONGJI_ANDROID_RELEASE_*` 签名配置或最终 `UPLOAD_BASE_URL` 不是 https 时，不允许产出正式 release 包。
- 提交备案号、软著 / 电子版权、隐私政策、用户协议、权限说明、测试账号和截图。
- 若应用市场因图标重复、名称混淆、截图不一致、权限说明或隐私问题打回，优先按打回原因小改，不顺手重构功能。
- 商标注册可以继续推进，但不作为首版上架的唯一前置；若平台或投诉方要求权利证明，再按实际情况补材料。

## P5：首版上线后观察

目标：先保证能用、可查、可回滚，再逐步做运营后台。

- 观察主聊天成功率、SSE 中断、图片上传失败、模型限流、记忆文档摘要失败、今日农情生成失败、检查更新下载失败。
- 记录真实 tokens、搜索次数、图片量和单轮成本，校准会员价格与加油包规则。
- 观察 RDS 会话连接、连接数利用率、TPS / QPS、慢查询、行锁、IOPS、ECS CPU / 内存，再决定是否调整 `MYSQL_MAX_OPEN_CONNS` 等连接池参数、ECS 规格 / 实例数或 Redis / 网关限流。
- 帮助与反馈先用第一版网页后台处理；当前已接状态队列、搜索、关闭和重开，继续补正式坐席分配、标签、站外通知和消息保存 / 删除规则。
- 后台第一阶段代码已覆盖按用户查看反馈 / 回复、用户额度查询、礼品卡、检查更新状态、检查更新发布历史、今日农情状态、App 日志和审计；上线后继续补公网黑盒自动定时通知、数据库只读脚本、检查更新 APK 上传 / 完整回滚入口和高风险动作二次确认。
- 每次真实发版、回滚、查日志、查库、补权益或处理客服，都要把可执行入口回填到对应 runbook。

## 当前不要做

- 不等手机号登录全部做完才启动备案。
- 不提前做重型完整管理后台。
- 不把模型 Key、短信 Key、数据库密码或支付密钥写进仓库。
- 不在最终图标、包名、签名证书未稳定前反复提交应用市场。
- 不把朋友账号 Key 当长期生产资源；它只能作为前中期限流兜底。

## 相关入口

- [infra-readiness.md](D:/wuhao/docs/runbooks/infra-readiness.md)：云资源采购前检查单
- [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)：买服务器前功能巡检记录
- [operations-blueprint.md](D:/wuhao/docs/runbooks/operations-blueprint.md)：后期 Codex 协助运维总蓝图
- [deploy-ecs.md](D:/wuhao/docs/runbooks/deploy-ecs.md)：ECS 部署入口
- [deploy-sae.md](D:/wuhao/docs/runbooks/deploy-sae.md)：SAE 历史备选入口
- [app-update.md](D:/wuhao/docs/runbooks/app-update.md)：自有 APK 检查更新入口
- [official-website.md](D:/wuhao/docs/runbooks/official-website.md)：官网静态站构建、下载入口和备案 footer
- [support-feedback.md](D:/wuhao/docs/runbooks/support-feedback.md)：帮助与反馈入口

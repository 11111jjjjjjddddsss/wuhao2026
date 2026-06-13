# 手机号登录与融合认证 Runbook

最后更新：2026-06-12

## 当前状态

- 后端已新增手机号账号骨架：`app_accounts`、`auth_sessions`、`user_id_migrations`。生产业务唯一长期身份是账号 ID `acct_...`；底层表和接口字段仍可能叫 `user_id`，但语义应按“账号ID”理解。手机号是登录凭证和回访线索，不作为业务主键；账号表保存 `phone_hash`、`phone_mask` 和用 `APP_SECRET` 派生密钥加密的 `phone_ciphertext`。
- Android 已新增登录门和验证码登录页；登录成功后保存后端签发的长期 v2 bearer token
- 后端已新增 `POST /api/auth/logout` 当前设备退出接口：只吊销当前 token 对应的 `auth_sessions` 记录，Android 账号管理页“退出设备”会调用该接口、清本地 auth token 并回到登录门；完整设备管理 / 远程吊销后续再迭代
- 登录成功后，Android 会随一键登录和短信登录 payload 提交旧本机 `legacy_user_id` 作为迁移线索；生产后端默认只有请求同时携带可证明同一旧 ID 的旧 bearer token 时才接受迁移，不再凭裸本机 UUID 直接合并资产。`acct_...` 永远不能作为 legacy bridge，避免账号之间互相合并。旧 bearer token 证明只用于登录迁移桥，不代表生产业务接口可以开启旧 token 鉴权；公开生产仍保持 `AUTH_STRICT=true` 且不设置 `AUTH_ALLOW_LEGACY_TOKEN`。迁移目标统一是手机号账号 `acct_...`，迁移覆盖记忆文档、聊天归档、会员 / 额度 / 加油包 / 订单、帮助反馈、App 日志、礼品卡、兑换尝试和注销申请；服务端会记录脱敏迁移审计日志，只写 legacy ID hash、迁移来源类型和目标账号ID，不打印手机号、验证码或原始旧 ID。若极特殊迁移窗口确需接受未证明 UUID，必须显式设置 `AUTH_ALLOW_UNPROVEN_LEGACY_UUID=true`，且生产 readiness 会把它视为失败配置。
- 阿里云融合认证 Android 方案已通过 CLI 创建，DYPNS AccessKey / Secret、`DYPNS_FUSION_SCHEME_CODE`、包名和签名已写入本机密钥文件与 ECS `/etc/nongjiqiancha/server.env`
- Android 一键登录 SDK / AAR 已导入并接入登录页；当前主链按阿里云融合认证 100001 一键登录流程拉取服务端 fusion token、初始化 SDK、拉起授权页，Android 不在 `onHalfWayVerifySuccess` 中调用后端校验，也不消费中途 token；最终只在 `onVerifySuccess` 收到 token 后提交给 `/api/auth/fusion/login`，由后端调用一次 `VerifyWithFusionAuthToken` 换手机号并签账号 token，不再用静态 token 或测试 ID 绕过登录。仓库当前使用的是融合认证 `AlicomFusionBusiness` / `fusionauth-1.2.15-online-release.aar` 链路，不是普通 `PhoneNumberAuthHelper` 三 AAR 直连链路；当前 AAR 内部已包含普通网关认证、日志类、native so 和授权页 Activity，并且 debug / release merged manifest 已合入 `FusionNumberAuthActivity`、`FusionSmsActivity`、`AlicomFusionUpSmsActivity`、`FusionGraphAuthActivity`、`LoginAuthActivity` 和 `PrivacyDialogActivity`。后续会诊时不要把普通一键登录文档里的“三个 AAR”直接当成当前仓库缺包结论；只有真机 logcat 出现 `NoClassDefFoundError` / `ClassNotFoundException` 指向 SDK logger / main / gatewayauth 类时，才重新下载完整融合认证 SDK 包核对。
- Android 授权页已接 `AlicomFusionAuthUICallBack` 自定义 UI，拉开手机号、登录按钮、其他手机号登录和协议区位置，SDK 协议勾选框可见且默认未勾选；用户点其他手机号登录、SDK 失败、取消或超时都会回到 App 自己的验证码登录页。2026-06-09 已按阿里云 Android 文档补融合认证协议页承接：Manifest 新增 `FusionAuthProtocolActivity`，`intent-filter` action 为包名专属 `com.nongjiqiancha.FUSION_AUTH_PROTOCOL`，`FusionOneLoginClient` 初始化后和授权页 UI model 均显式设置同一 `protocolAction`，并在 UI model builder 上同步 `setPackageName(BuildConfig.APPLICATION_ID)`，避免自定义协议 action 找不到当前 App Activity。2026-06-13 起协议页只承接 `nongjiqiancha.cn` / `www.nongjiqiancha.cn` 官方 HTTPS 页面，WebView 关闭 JS、file/content 访问，禁止明文 HTTP 和外域跳转；网页缺失或主页面加载失败时显示 App 内置协议要点兜底，避免点击服务协议 / 隐私政策时空白退出。协议 URL 缺失 / 非法、非法跳转和主页面加载失败会通过 `auth.fusion_protocol_url_unavailable`、`auth.fusion_protocol_navigation_blocked`、`auth.fusion_protocol_load_failed` 上报安全摘要，只带 reason / scheme / WebView 错误码，不上传完整 URL。
- Android 端会在申请 `READ_PHONE_STATE` 权限和拉取 fusion token 前先做网络 / SIM 环境预检：无网络、无 SIM、SIM 未就绪、纯 WiFi / 未检测到可用移动数据会直接回落验证码登录并上报 `auth.fusion_env_blocked`，不先弹电话状态权限；有 SIM 且检测到可用移动数据能力时，即使当前开着 WiFi、VPN 或系统代理，也会放行一键登录尝试并上报 `auth.fusion_env_warning`，失败再回 App 自己的验证码登录。系统代理识别同时检查 active network 的 `LinkProperties.httpProxy`、Java proxy properties 和旧 Android proxy fallback，用于排障日志，不再作为有移动数据时的硬阻断。2026-06-09 已给 `FusionOneLoginClient` 补同一时间只允许一条一键登录、Activity `finish/destroy` 前置兜底、SDK token 成功后拉授权页前再次检查 Activity、`onSDKTokenAuthFailure` 失败收口、`onGetPhoneNumberForVerification` 非空手机号兜底并回验证码、`onVerifyInterrupt` 只记录不强行继续、`onVerifySuccess` 后忽略后到的模板结束 / verify failed 抢跑回调、重复 `onVerifySuccess` 只记录不重复提交，以及 100001 场景下意外 `onHalfWayVerifySuccess` 直接失败回验证码登录。2026-06-13 起，fusion token 首次请求使用短超时认证客户端，弱网下更快回验证码兜底。登录页销毁时会主动停止当前融合认证场景；不要把 SDK 授权页正常拉起造成的主 Activity `onPause` 误当成必须停止场景的信号。Android 端还会在一键登录、短信发送和短信登录阶段设置最小崩溃阶段标记，若进程直接退出，下次启动会通过 `auth.app_crash` 补报安全摘要
- Android 主 manifest 已显式声明 `READ_PHONE_STATE`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE` 和 `CHANGE_NETWORK_STATE`，减少 release 构建依赖 AAR manifest merge 的不确定性，并满足少数 Wi-Fi + 移动数据切换取号场景；2026-06-12 起，登录页在用户明确点击“一键登录”后先做环境预检，明显应回验证码的环境不先弹 `READ_PHONE_STATE`，只有看起来可能走一键取号时才按需申请电话状态权限；拒绝授权时直接切换验证码登录并上报 `auth.fusion_permission_denied`，不再在缺权限状态下继续尝试拉起授权页。正式 release 前仍要检查 merged manifest，并继续用真机确认该 manifest 权限在当前融合认证链路和 ROM 组合下是否仍有必要保留
- Android 网络安全配置默认仍禁止明文 HTTP，仅按阿里云 SDK FAQ 对移动 / 联通 / 电信取号网关 `onekey.cmpassport.com`、`enrichgw.10010.com`、`uac.189.cn` 做域名级明文放行；不要为了一键登录直接全局开启 `cleartextTrafficPermitted=true`
- 短信登录后端当前走阿里云普通 Dysms `SendSms`：后端生成 6 位验证码，只把 HMAC 摘要写入 Redis，短信发送使用模板变量 `code`，`/api/auth/sms/login` 校验 Redis 摘要并且只在账号登录成功后删除验证码，避免账号迁移 SQL 临时失败时把用户刚收到的验证码提前消费掉。Android 登录页只允许提交 6 位验证码，避免 4 / 5 位输入也打到后端造成无意义失败；页面不额外写“5分钟有效”，仍保持“验证码已发送”的简洁提示。若短信供应商返回超时 / 异常，后端也不再主动清掉刚缓存的验证码，因为供应商可能实际已经把短信发出；这类场景前端可能看到“发送失败”，但用户若已经收到新验证码，仍可尝试登录。ECS 当前已配置短信签名和验证码模板；`/healthz` 已显示 `dypns_sms=ok` 和 `sms=ok`
- 网站 ICP 已通过，`api.nongjiqiancha.cn` HTTPS 已于 2026-06-05 配好并公网验证通过；2026-06-01 曾为真机登录联调临时允许 `39.106.1.151` Host 直连反代到 Go 服务，并生成临时调试 APK 走 `http://39.106.1.151`。当前 Android 构建固定使用 `UPLOAD_BASE_URL=https://api.nongjiqiancha.cn`，Android Studio 直接 Run 的 debug 包和正式 release 包都接正式 HTTPS 后端；`USE_BACKEND_AB` 固定开启，不再通过 Gradle 参数关闭后端主链或切换业务后端地址。本机存在固定 release 签名配置时，debug 构建也使用同一把 release 签名并开启一键登录，让测试包和正式包保持同包名、同签名、同业务链路；缺少 release 签名配置的环境下，debug 包会关闭一键登录并退到验证码登录。debug 包与正式包的差异只保留 debug-only 预览面板和调试日志。仓库已新增 [check-android-build-parity.ps1](D:/wuhao/scripts/check-android-build-parity.ps1) 并接入 GitHub Android CI，用于自动检查 debug / release 后端地址、签名一键登录、网络安全配置、100001 最终 token 登录主链和 debug-only 预览隔离。
- Redis 已购买并在 `server-go` 里接成可选认证限流后端：生产 ECS 已配置 `REDIS_*` 且 `/healthz redis=ok`，融合认证 token、融合认证登录校验、短信发送和短信登录校验会走 Redis 分布式限流；未配置 Redis 的其他环境仍回退单进程内限流
- 阿里云侧认证次数和账单查询已纳入巡检：本机脚本 [check-auth-usage.ps1](D:/wuhao/scripts/check-auth-usage.ps1) 会调用 DYPNS 统计 / 账单 OpenAPI 查询一键登录和短信认证用量，不输出任何密钥。2026-06-06 默认查询最近 7 天时，一键登录和短信认证统计均为 `no_data`，月账单接口未返回费用明细，说明当前尚未形成真实认证消耗

## 后端接口

- `POST /api/auth/fusion/token`：服务端向阿里云获取融合认证 token，并返回 `auth_token + scheme_code` 给 Android SDK 使用；默认按 IP hash 做 10 分钟 20 次限流，配置 Redis 后跨进程共享
- `POST /api/auth/fusion/verify`：历史兼容 / 非 100001 中途验证备用接口；当前 Android 100001 主链不调用它，避免 token 被提前消费。该接口只校验 token 并返回脱敏手机号，不签发账号 token、不迁移旧用户数据；默认复用融合认证登录限流配置，按 IP hash 做防刷
- `POST /api/auth/fusion/login`：Android SDK 在最终 `onVerifySuccess` 拿到 token 后提交，后端只在这里调用一次 `VerifyWithFusionAuthToken` 换手机号并签发账号 token；默认按 IP hash 做 10 分钟 20 次限流，配置 Redis 后跨进程共享，避免伪造 token 反复打阿里云校验接口
- `POST /api/auth/sms/send`：发送短信验证码，默认同一手机号 + IP 10 分钟 5 次，同时同一 IP 10 分钟 20 次；配置 Redis 后跨进程共享
- `POST /api/auth/sms/login`：校验短信验证码并签发账号 token，默认 10 分钟 10 次限流；配置 Redis 后跨进程共享
- `POST /api/auth/logout`：校验当前 bearer token 后吊销当前 `session_id`，后续该 token 会被 `requireAuth` 拒绝
- `GET /api/auth/session`：校验当前 bearer token

## 必要环境变量

- `APP_SECRET`：手机号 hash 和 v2 token 签名必须依赖它
- `AUTH_STRICT=true`：生产必须开启，关闭裸 `X-User-Id` 兜底；默认也会拒绝无 `session_id` 的旧 bearer token，只接受可查库吊销的 v2 session token
- `AUTH_ALLOW_LEGACY_TOKEN=true`：只允许迁移期 / 本地兼容使用；生产公开入口不要开启，否则旧 bearer token 无法被 `POST /api/auth/logout` 吊销
- `AUTH_ALLOW_UNPROVEN_LEGACY_UUID=true`：只允许极短迁移窗口 / 本地兼容使用；生产公开入口不要开启，否则只凭一个旧本机 UUID 就可能触发资产合并
- `AUTH_SESSION_DAYS`：登录保持天数，默认 3650；当前按“长期保持登录、省认证次数”口径处理，主动退出设备已通过 `POST /api/auth/logout` 吊销当前 session，完整设备管理 / 远程吊销后续再迭代
- `DYPNS_ACCESS_KEY_ID` / `DYPNS_ACCESS_KEY_SECRET`：阿里云融合认证 / 短信 API 凭证，也兼容 `ALIBABA_CLOUD_ACCESS_KEY_ID` / `ALIBABA_CLOUD_ACCESS_KEY_SECRET`
- `DYPNS_REGION_ID`：默认 `cn-hangzhou`
- `DYPNS_FUSION_SCHEME_CODE`：阿里云融合认证 SchemeCode，当前已创建并写入本机 / ECS
- `DYPNS_ANDROID_PACKAGE_NAME`：默认 `com.nongjiqiancha`
- `DYPNS_ANDROID_PACKAGE_SIGN`：Android release 签名 MD5，去掉冒号并转小写，例如 `26df23b0d32bf5a4fcd616cba22cadca`
- `SMS_ACCESS_KEY_ID` / `SMS_ACCESS_KEY_SECRET`：普通 Dysms 短信发送凭证，未单独配置时兼容复用 `DYPNS_ACCESS_KEY_ID` / `DYPNS_ACCESS_KEY_SECRET`
- `SMS_REGION_ID`：普通 Dysms 地域，未设置时兼容 `DYPNS_REGION_ID`，默认 `cn-hangzhou`
- `SMS_SIGN_NAME` / `DYPNS_SMS_SIGN_NAME`：短信签名；优先使用 `SMS_SIGN_NAME`，未设置时兼容旧 `DYPNS_SMS_SIGN_NAME`
- `SMS_TEMPLATE_CODE` / `DYPNS_SMS_TEMPLATE_CODE`：短信模板 Code；优先使用 `SMS_TEMPLATE_CODE`，未设置时兼容旧 `DYPNS_SMS_TEMPLATE_CODE`
- 普通 Dysms 发送会由服务端传 `{"code":"实际6位验证码"}`。不要再传旧 `{"code":"##code##"}`，也不要把 DYPNS `SendSmsVerifyCode / CheckSmsVerifyCode` 的代管验证码参数口径混到当前短信登录主链
- `DYPNS_SMS_SCHEME_NAME`：仅保留给旧 DYPNS 代管短信认证链路参考；当前普通 Dysms 短信登录主链不使用该变量
- `AUTH_FUSION_TOKEN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_FUSION_TOKEN_RATE_LIMIT_MAX_HITS` / `AUTH_FUSION_TOKEN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：融合认证 token 获取限流，默认 10 分钟 20 次
- `AUTH_FUSION_LOGIN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_FUSION_LOGIN_RATE_LIMIT_MAX_HITS` / `AUTH_FUSION_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：融合认证登录校验限流，默认 10 分钟 20 次
- `AUTH_SMS_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信发送限流，默认 10 分钟 5 次
- `AUTH_SMS_IP_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_IP_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_IP_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信发送 IP 级总限流，默认 10 分钟 20 次，防止同一 IP 轮换手机号消耗短信
- `AUTH_SMS_LOGIN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_LOGIN_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信登录校验限流，默认 10 分钟 10 次
- `REDIS_ADDR` / `REDIS_USERNAME` / `REDIS_PASSWORD` / `REDIS_DB`：可选 Redis 连接配置；配置后认证限流从单进程内存切到 Redis，主聊天流和业务真相不受影响

## 阿里云侧待办

1. 在融合认证控制台确认 `农技千查` Android 方案，包名 `com.nongjiqiancha`，签名 MD5 与 release 包一致。
2. 阿里云融合认证 Android SDK 已接入；后续真机回归时重点确认运营商网络、双卡 / 无 SIM、Wi-Fi-only、VPN / 代理和 SDK 取消态：无 SIM / 无移动数据应回落验证码登录；有移动数据但开 WiFi / VPN / 代理应先尝试一键登录，失败再回验证码登录；同时确认荣耀 / 华为 / Android 15+ 在按需电话权限审批后，一键登录授权页仍可稳定拉起。
3. 当前 100001 一键登录只在最终 `onVerifySuccess` 后由 `/api/auth/fusion/login` 消费一次 token；真机回归时重点确认授权页可稳定拉起、SDK 协议勾选后最终登录成功、取消 / 超时 / 失败会回落验证码登录。
4. 短信服务资质、签名和验证码模板已通过 CLI 配置；ECS 已写入 `DYPNS_SMS_SIGN_NAME`、`DYPNS_SMS_TEMPLATE_CODE` 并重启 `nongji-server`。
5. 上线前轮换已暴露过的主账号 AccessKey，优先改成最小权限或专用 RAM 用户口径，并重新配置 `DYPNS_ACCESS_KEY_ID` / `DYPNS_ACCESS_KEY_SECRET`。
6. 配置完成后重启 `nongji-server`，检查 `/healthz` 中 `dypns / dypns_fusion / dypns_sms / sms / redis` 是否为 `ok`；当前这些项均已为 `ok`。

## 次数与费用巡检

通过阿里云 CLI 查询认证用量：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-auth-usage.ps1
```

脚本默认查最近 7 天的一键登录和短信认证统计，以及当月一键登录 / 短信认证账单：

- 统计接口 `query-gate-verify-statistic-public`：`authentication-type=1` 表示一键登录，`authentication-type=3` 表示短信认证
- 账单接口 `query-gate-verify-billing-public`：`authentication-type=1` 表示一键登录，`authentication-type=4` 表示短信认证
- 如果需要查指定时间，可传 `-StartDate 20260601 -EndDate 20260606 -Month 202606`

后续要重点看：

- 一键登录总调用量、成功量、失败量、未知量
- 短信认证发送 / 校验相关消耗
- 失败率或未知率是否异常升高，尤其是授权页拉起失败、最终 login token 校验失败、SDK 重试或用户网络 / 代理导致的运营商认证失败
- 调用量是否明显超过真实用户访问量，防脚本刷 token 或伪造 verify token 消耗阿里云认证额度

## Android 构建一致性巡检

每次改 Android 登录、构建、manifest 或网络安全配置后，先跑：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-android-build-parity.ps1
```

该脚本检查仓库源码，并在 debug / release 的 merged / packaged manifest 已生成时继续校验最终构建产物；它不读取 release 签名密码或云端密钥。它会挡住这些高风险回退：

- debug / release 指向不同业务后端，或允许通过 Gradle property / 环境变量把普通包切到非生产后端
- debug 不再在本机存在 release 签名配置时使用同一签名和一键登录
- release 一键登录被关闭，或 release 打包缺少签名 / HTTPS 守卫
- debug 单独新增 manifest / 网络安全配置，绕开 release 基线
- 已生成的 debug / release merged manifest 里，融合认证 SDK Activity 没有吃到本地主题或 `exported=false`
- 全局放开明文 HTTP，或新增非阿里云运营商取号网关的明文域名；当前仅允许移动 `onekey.cmpassport.com`、联通 `enrichgw.10010.com`、电信 `uac.189.cn`
- Android 100001 一键登录重新调用半程 `/api/auth/fusion/verify`，或在 `onHalfWayVerifySuccess` 里消费 token
- VPN / 系统代理或纯 WiFi / 无可用移动数据时仍继续硬拉阿里云融合认证 SDK，或把验证码登录也误拦住；4G+WiFi 混合环境不应被误判成 WiFi-only 而禁止一键尝试
- 验证码登录放宽到 4 / 5 位也请求后端，或 release logcat 打印阿里云 SDK 原始 `errorMsg / innerMsg`
- debug-only 预览面板脱离 `BuildConfig.DEBUG` 守卫

CI 会自动执行同一脚本；本地既可以用当前 PowerShell，也可以用系统 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-android-build-parity.ps1` 复核。脚本失败时不要绕过检查，应先确认测试包和正式包仍保持同包名、同签名、同后端、同登录主链。

## 安全与成本边界

- 不在数据库保存可直接读取的明文手机号；`app_accounts` 保存 `APP_SECRET` HMAC 后的 `phone_hash`、脱敏 `phone_mask` 和 AES-GCM 加密的 `phone_ciphertext`
- 后台 `owner`、`support`、`finance_ops` 可在用户 / 反馈页面查看和复制完整手机号，用于回访；用户管理和帮助反馈搜索框输入完整 11 位手机号时，服务端用 `phone_hash` 精确匹配账号，不把明文手机号写入日志或审计。`ops_readonly`、`auditor` 等只读巡检角色继续只看脱敏手机号。审计只记录是否展示了完整号，不记录手机号值
- 后台监控面板已新增“登录排障”聚合：最近 24 小时认证失败、一键登录失败、短信失败、登录前日志、`auth.fusion_env_blocked / auth.fusion_env_warning` 环境预检、`auth.login_network_failed` 登录请求网络失败、`auth.app_crash / app.crash` 闪退补报和 Top 事件会直接展示，并提供按钮跳转 App 日志筛选；一键登录按钮按取 fusion token、SDK 初始化、授权页拉起、SDK token auth、最终取号、服务端换号、超时和授权页未完成拆开，短信也拆成发送失败和登录校验失败；登录前日志统一 `user_id=preauth`
- 旧账号如果还没有 `phone_ciphertext`，无法从 hash / mask 反推完整手机号，必须等用户下一次一键登录或短信登录后自动补齐
- 账号 ID `acct_...` 是会员、每日额度、加油包、礼品卡、订单、帮助反馈、App 日志、A 层滑窗、记忆文档和聊天归档的统一归属 ID。旧本机 UUID 只用于登录时一次性迁移桥，不作为生产长期身份继续扩展。
- 旧本机 ID 迁移默认要求旧 bearer token 证明同一旧身份，不再凭“形态正确的本机 UUID”直接合并资产。只有极短迁移窗口或本地兼容场景显式设置 `AUTH_ALLOW_UNPROVEN_LEGACY_UUID=true` 才会接受未证明 UUID，且生产 readiness 会失败，不能作为公开生产常态。
- 不把阿里云 AccessKey、短信模板变量、APP_SECRET 写进仓库
- Android 只在用户同意协议并点击本机号码一键登录后请求 `/api/auth/fusion/token`，把 `auth_token + scheme_code` 交给官方 SDK；失败 / 取消时回落验证码登录，不走假登录或测试 ID 绕过
- `/api/auth/fusion/token`、`/api/auth/fusion/verify` 和 `/api/auth/fusion/login` 都已有 Redis / 单进程短期限流；当前 Android 主链只调用 token + login，verify 接口仅保留历史兼容，避免 SDK 接入后被脚本反复刷 token 或伪造 token 消耗阿里云认证配额
- 阿里云 DYPNS 统计 / 账单必须和后端日志一起看：云侧是真实消耗，后端日志是真实业务入口；如果云侧调用量明显高于后端入口次数，优先排查密钥泄露、脚本刷接口、SDK 重试或授权页 / 最终校验链路异常
- `/api/auth/sms/send` 同时有手机号 + IP 和 IP 总量两层限流，避免同一出口轮换手机号消耗短信
- 生产公开入口保持 `AUTH_STRICT=true` 且不设置 `AUTH_ALLOW_LEGACY_TOKEN`，让退出设备 / session 吊销真正生效
- 生产公开入口保持不设置 `AUTH_ALLOW_UNPROVEN_LEGACY_UUID`；旧本机 ID 迁移默认要求旧 token 证明，避免手机号账号把不属于自己的会员、礼品卡、反馈或聊天资产合并走
- 登录迁移当前覆盖 A 层滑窗、记忆文档、聊天归档、额度 / 会员 / 加油包 / 订单、帮助与反馈、App 日志、礼品卡兑换和兑换尝试等长期业务数据；`chat_stream_inflight` 是临时租约，登录迁移时直接丢弃旧本机租约，不迁到手机号账号
- 多 ECS / 多实例前，认证限流必须保持 Redis 可用；验证码短期状态、失败计数和后台任务 claim 可再按需补 Redis，但不要把聊天正文或长期用户资产放入 Redis

## 参考官方文档

- 阿里云 `CreateSchemeConfig`
- 阿里云 `GetFusionAuthToken`：服务端获取鉴权 Token，Android 场景必须携带 SchemeCode、PackageName、PackageSign、Platform 等关键参数。
- 阿里云 `VerifyWithFusionAuthToken`：客户端 SDK 最终拿到统一认证 Token 后，服务端调用该接口获取认证结果。
- 阿里云普通短信 `SendSms`
- 阿里云登录 / 注册场景文档明确：号码认证失败时应继续流程，或点“其他手机号登录”进入短信认证
- 阿里云 SDK FAQ 提醒：`checkEnvAvailable` 返回 false 时需检查 SIM 卡、移动数据和网络权限；双卡手机按默认移动数据卡认证；开启 VPN、网络切换中、2G / 3G、SIM 未激活或欠费都可能造成失败。FAQ 对 Android 取号网关给出 `networkSecurityConfig` 域名级明文示例，不需要全局放开所有 HTTP。

## 双卡 / 代理 / 网络切换排障口径

- 一键登录依赖运营商网关取号能力，不是 App 自己读取短信或联系人。双卡手机上，通常以当前默认移动数据卡 / 当前蜂窝数据链路作为本机号码认证目标；App 不应承诺在授权页里直接选择某一张 SIM 卡。
- 如果用户想用另一张卡做一键登录，先到系统设置切换默认移动数据卡，再回 App 重试；如果仍失败，直接走验证码登录，这是正式兜底，不是临时测试方案。
- App 一键登录失败提示按“一键登录排障”和“验证码兜底”分开收口：测一键登录时先关闭代理 / VPN、打开移动数据、确认默认数据卡；用户不需要反复尝试一键登录，验证码登录就是正式兜底路径，只要生产 HTTPS 后端可达，WiFi 或代理环境下也应可用。
- VPN / 国外代理、纯 Wi-Fi、移动数据关闭、SIM 卡欠费 / 未激活、运营商网络切换中、部分 5G 兼容性问题，都可能导致 SDK 环境检测、授权页拉起或取号失败。App 当前对无 SIM、SIM 未就绪、纯 WiFi / 无可用移动数据不硬拉 SDK，优先切到验证码登录；对 4G+WiFi、当前活动网络不是蜂窝、VPN 或系统代理但仍能检测到移动数据能力的情况只记录 warning 并允许一键尝试，失败后仍回 App 验证码页。验证码登录是正式兜底，必须在 WiFi、代理、移动数据环境下都可用。
- 明显会回落验证码的环境（纯 WiFi / 无可用移动数据 / 无 SIM / SIM 未就绪）不应先弹 `READ_PHONE_STATE` 权限；只有环境看起来可能走一键取号时，用户点击“一键登录”才按需申请电话状态权限。
- 如果一键登录和短信登录同时失败或都触发闪退，不要只盯运营商取号 SDK。优先把它当成“登录公共闭环”问题排查：生产 API 是否可达、debug / release 是否仍同包名同签名同后端、登录成功后 session token 是否写入本地、`IdManager` 是否切到账号ID、登录页是否在 Compose 重组 / Activity 生命周期变化时重复触发、登录成功后跳转主界面 / hydrate / 权益拉取是否抛异常。此时后台 `auth.*` 只能说明卡在哪个阶段，最终仍要配合 `AndroidRuntime` logcat 看共同崩溃栈。
- 真机排障顺序：测一键登录时优先关闭代理 / VPN、打开移动数据、确认默认数据卡和目标手机号一致；测验证码登录时不要求关闭 WiFi 或代理，重点确认短信能到、生产 HTTPS 后端可达。失败后立刻看后台监控面板“登录排障”和 App 日志 `auth.*` / `auth.app_crash` 事件。若看到 `auth.fusion_env_blocked`，先处理无网络 / 无 SIM / SIM 未就绪 / 无可用移动数据；若看到 `auth.fusion_env_warning`，说明 4G+WiFi、VPN、系统代理或混合网络已放行一键登录尝试，继续看后续 SDK 事件；若看到 `auth.fusion_sdk_token_auth_failed` 或 `auth.fusion_scene_start_failed`，优先核对包名、签名、SchemeCode 和阿里云控制台方案；若看到 `auth.fusion_protocol_url_unavailable`、`auth.fusion_protocol_navigation_blocked` 或 `auth.fusion_protocol_load_failed`，优先查 SDK 协议页 action、协议参数和 WebView 网络加载，不把这类问题误判成最终取号失败；若看到 `auth.fusion_login_failed`，优先查服务端 `VerifyWithFusionAuthToken` 返回和 token 是否被重复消费。若 App 直接退出但后台没有 `auth.app_crash`，继续抓 `adb logcat -s AndroidRuntime FusionAuth AlicomFusion PhoneNumberAuth DEFENDER jaffer`，重点看 `ActivityNotFoundException`、`NoClassDefFoundError`、`NoSuchMethodError`、`cleartext HTTP traffic not permitted`、`BackgroundActivityStartNotAllowedException`、`DEFENDER behavior: 0` 或重签名 / root / 调试检测日志；这类 native / 系统 kill / 加固主动退出不一定能被 Android 最小崩溃补报捕获。

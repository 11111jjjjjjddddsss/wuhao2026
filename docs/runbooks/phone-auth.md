# 手机号登录与融合认证 Runbook

最后更新：2026-06-13

## 当前结论

- 生产业务唯一长期身份是账号 ID `acct_...`；手机号只是登录凭证和回访线索，不作为业务主键。
- 同一个手机号在不同设备登录，会归到同一个 `acct_...` 账号；每台设备各有自己的 `auth_sessions` session。
- `AUTH_SESSION_DAYS` 默认 3650 天，当前按“长期保持登录、省认证次数”处理。用户不主动退出、服务端不吊销、本机 token 不丢失时，可以长期保持登录。
- Android 新包只保留一个用户可见登录入口：融合认证短信登录。登录页不再展示本机号码一键登录，不再展示 App 自己的验证码发送 / 输入 / 登录备用表单，也不再申请 `READ_PHONE_STATE`。
- 新包登录链路是：用户输入手机号并勾选协议 -> Android 请求 `/api/auth/fusion/token` -> 拉起阿里云融合认证 100001 短信节点 -> SDK 最终 `onVerifySuccess` 返回 token -> Android 调 `/api/auth/fusion/login` -> 后端换取手机号并签发 `acct_...` session。
- 普通 `/api/auth/sms/send` 和 `/api/auth/sms/login` 后端接口仍保留给旧包、应急和本地排障兼容；新 Android 登录页不调用它们，避免出现“两套短信”和套餐抵扣口径混乱。
- 如果要消耗融合认证解决方案套餐内的短信认证次数，必须继续走融合认证 SDK / 融合策略里的短信节点；完全绕开 SDK 的普通 Dysms `SendSms` 不应假定会抵扣融合认证套餐。

## Android 当前实现

- 登录页只显示品牌、手机号输入框、`验证码登录` 主按钮、协议勾选和低噪声提示。
- 用户未勾选协议时，不请求后端、不拉起融合认证 SDK、不初始化本机业务身份。
- `BuildConfig.ENABLE_FUSION_SMS_LOGIN` 控制当前包是否启用融合短信登录；debug 在本机存在 release 签名时与 release 一样启用，release 必须启用。
- `FusionOneLoginClient.startSmsLogin(...)` 是新包登录入口；`LoginScreen.kt` 不允许再直接调用 `SessionApi.sendSmsCode(...)` 或 `SessionApi.loginWithSms(...)`。
- Android manifest 用 `tools:node="remove"` 明确移除 `READ_PHONE_STATE`，最终 debug / release merged manifest 不应包含该权限。
- 融合 SDK 短信页只做安全样式收口：白底、黑色主按钮、灰色提示、标题“验证码登录”。不替换 SDK 内部校验流程，不猜测未公开接口。
- `FusionOneLoginClient` 继续保留请求代际、重复拉起锁、Activity 生命周期兜底、SDK token 刷新、最终 token 只消费一次、失败/取消/超时收口、图形认证模块关闭和敏感日志脱敏。

## 阿里云控制台策略

- 第 4 项“融合认证解决方案”必须保持开启；关闭它会让融合短信主链也不可用。
- 当前 Android 方案 Code 应是 `FA000000009823740907`，场景为 `100001`。
- 当前用户可见策略应只保留短信认证节点；号码认证 / 本机号码一键登录节点和图形验证节点都应关闭或删除。
- “功能开启”页里关闭独立图形认证，不等于当前融合方案策略中没有图形节点；仍要进“认证策略设置”的全局策略和方案策略检查流程图。
- 若真机仍弹“请在下图依次点击”类图形验证码，优先查云端 100001 策略是否仍有图形节点或风控兜底，而不是改 Android 代码。

## 后端接口

- `POST /api/auth/fusion/token`：服务端向阿里云获取融合认证 token，返回 `auth_token + scheme_code` 给 Android SDK。
- `POST /api/auth/fusion/login`：Android SDK 最终 `onVerifySuccess` 后提交 token；后端只在这里调用一次 `VerifyWithFusionAuthToken` 换手机号并签发账号 token。
- `POST /api/auth/fusion/verify`：历史兼容 / 非 100001 中途验证备用接口；当前 Android 新包不调用它。
- `POST /api/auth/sms/send`：普通 Dysms 短信发送接口，保留兼容，不是新包可见入口。
- `POST /api/auth/sms/login`：普通 Dysms 验证码登录接口，保留兼容，不是新包可见入口。
- `POST /api/auth/logout`：吊销当前设备 session，不删除聊天历史、会员权益、礼品卡或反馈记录。
- `GET /api/auth/session`：校验当前 bearer token。

## 账号和资产边界

- `app_accounts` 保存 `phone_hash`、`phone_mask` 和用 `APP_SECRET` 派生密钥加密的 `phone_ciphertext`。
- 后台授权角色可在用户 / 反馈详情中查看和复制完整手机号；日志、审计、SLS、Redis、文档和聊天输出不得打印完整手机号。
- Android 登录成功后只接受 `acct_...` 账号ID；后端生产正式 v2 token 也只允许签给 `acct_...`。
- 旧本机 UUID 只作为登录时迁移桥；`acct_...` 永远不能当 legacy bridge，避免账号之间互相合并。
- 迁移覆盖记忆文档、聊天归档、会员 / 额度 / 加油包 / 订单、帮助反馈、App 日志、礼品卡、兑换尝试和注销申请等长期业务数据。
- 生产公开入口保持 `AUTH_STRICT=true`，不设置 `AUTH_ALLOW_LEGACY_TOKEN`，也不设置 `AUTH_ALLOW_UNPROVEN_LEGACY_UUID`。

## 必要环境变量

- `APP_SECRET`：手机号 hash、手机号加密和 v2 token 签名依赖它。
- `AUTH_STRICT=true`：生产必须开启。
- `AUTH_SESSION_DAYS`：登录保持天数，默认 3650。
- `DYPNS_ACCESS_KEY_ID` / `DYPNS_ACCESS_KEY_SECRET`：阿里云融合认证凭证，也兼容 `ALIBABA_CLOUD_ACCESS_KEY_ID` / `ALIBABA_CLOUD_ACCESS_KEY_SECRET`。
- `DYPNS_REGION_ID`：默认 `cn-hangzhou`。
- `DYPNS_FUSION_SCHEME_CODE`：阿里云融合认证 SchemeCode。
- `DYPNS_ANDROID_PACKAGE_NAME`：默认 `com.nongjiqiancha`。
- `DYPNS_ANDROID_PACKAGE_SIGN`：Android release 签名 MD5，去掉冒号并转小写。
- `SMS_ACCESS_KEY_ID` / `SMS_ACCESS_KEY_SECRET`、`SMS_SIGN_NAME`、`SMS_TEMPLATE_CODE`：普通 Dysms 兼容接口使用；新包可见登录页不调用普通 Dysms。
- `AUTH_FUSION_TOKEN_RATE_LIMIT_*` / `AUTH_FUSION_LOGIN_RATE_LIMIT_*`：融合认证 token 获取和最终登录校验限流。
- `AUTH_SMS_*`：普通 Dysms 兼容接口限流。
- `REDIS_ADDR` / `REDIS_USERNAME` / `REDIS_PASSWORD` / `REDIS_DB`：可选 Redis 连接配置；配置后认证限流跨进程共享。

## 构建一致性巡检

每次改 Android 登录、manifest、构建或网络安全配置后运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-android-build-parity.ps1
```

该脚本会挡住这些回退：

- debug / release 指向不同业务后端。
- release 未启用融合短信登录。
- debug 在本机有 release 签名时没有跟 release 同签名、同包名、同后端、同登录链路。
- `LoginScreen.kt` 重新出现普通短信发送 / 登录调用、电话权限申请、一键登录按钮或备用验证码 UI。
- merged manifest 重新包含 `READ_PHONE_STATE`。
- 100001 主链重新在 Android 端消费半程 `/api/auth/fusion/verify`。
- SDK 失败路径重新交给图形认证或其它不可控后续节点。
- debug-only 预览面板脱离 `BuildConfig.DEBUG` 守卫。

## 真机排障顺序

1. 确认安装的是最新 debug / release 包，旧包不会自动变成新登录 UI。
2. 确认阿里云当前方案 Code、包名和签名 MD5 与 release 签名一致。
3. 确认 100001 策略只保留短信认证节点，图形验证和号码认证节点已关闭 / 删除。
4. 点击 `验证码登录` 后若 SDK 页未拉起，看后台 App 日志 `auth.fusion_token_failed`、`auth.fusion_sdk_init_failed`、`auth.fusion_scene_start_failed`。
5. SDK 页能拉起但最终登录失败，看 `auth.fusion_verify_failed`、`auth.fusion_verify_interrupt`、`auth.fusion_login_failed`，再配合 logcat。
6. 若 App 直接退出，看 `auth.app_crash` / `app.crash`，必要时抓 `adb logcat -s AndroidRuntime FusionAuth AlicomFusion PhoneNumberAuth DEFENDER jaffer`。

## 次数与费用巡检

通过阿里云 CLI 查询认证用量：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-auth-usage.ps1
```

重点看：

- 短信认证发送 / 校验消耗。
- 一键登录消耗是否已经停止增长。
- 云侧调用量是否明显高于后端 `auth.fusion_*` 入口次数。
- 失败率或未知率是否异常升高。

## 仍需注意

- 融合短信仍由阿里云 SDK 承接短信发送 / 校验页面，不能完全当成 App 自己的 Compose 表单来写；否则就会变成普通 Dysms 链路。
- 若未来决定彻底放弃融合认证套餐，只用 App 自己的短信验证码 UI，需要另购或按量使用普通短信认证 / Dysms，并把本 runbook、隐私文案、后台日志和构建护栏同步改回普通短信主链。
- 上线前仍要轮换已经暴露过的主账号 AccessKey。

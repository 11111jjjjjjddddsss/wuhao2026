# 手机号登录与融合认证 Runbook

最后更新：2026-06-06

## 当前状态

- 后端已新增手机号账号骨架：`app_accounts`、`auth_sessions`、`user_id_migrations`
- Android 已新增登录门和验证码登录页；登录成功后保存后端签发的长期 v2 bearer token
- 后端已新增 `POST /api/auth/logout` 当前设备退出接口：只吊销当前 token 对应的 `auth_sessions` 记录，Android 账号管理页“退出设备”会调用该接口、清本地 auth token 并回到登录门；完整设备管理 / 远程吊销后续再迭代
- 登录成功后，旧本机 `user_id` 作为迁移桥，后端会尽量把旧用户数据迁到手机号账号 `acct_...`
- 阿里云融合认证 Android 方案已通过 CLI 创建，DYPNS AccessKey / Secret、`DYPNS_FUSION_SCHEME_CODE`、包名和签名已写入本机密钥文件与 ECS `/etc/nongjiqiancha/server.env`
- Android 一键登录 SDK / AAR 已导入并接入登录页；当前主链按官方 SDK 流程拉取服务端 fusion token、初始化 SDK，SDK 半程校验只调用后端 verify-only 接口，Android 要求响应体 `ok=true` 才算半程通过，最终成功节点才提交 verify token 给后端登录接口，不再用静态 token 或测试 ID 绕过登录
- Android 主 manifest 已显式声明 `READ_PHONE_STATE`、`ACCESS_NETWORK_STATE` 和 `ACCESS_WIFI_STATE`，减少 release 构建依赖 AAR manifest merge 的不确定性；正式 release 前仍要检查 merged manifest
- 短信登录后端接口已接阿里云 Dypns API，ECS 当前已配置 DYPNS 基础凭证、短信签名和验证码模板；`/healthz` 已显示 `dypns_sms=ok`
- 网站 ICP 已通过，`api.nongjiqiancha.cn` HTTPS 已于 2026-06-05 配好并公网验证通过；2026-06-01 曾为真机登录联调临时允许 `39.106.1.151` Host 直连反代到 Go 服务，并生成 debug APK 走 `http://39.106.1.151`，后续正式回归应优先使用 `https://api.nongjiqiancha.cn`
- Redis 已购买并在 `server-go` 里接成可选认证限流后端：生产 ECS 已配置 `REDIS_*` 且 `/healthz redis=ok`，融合认证 token、融合认证登录校验、短信发送和短信登录校验会走 Redis 分布式限流；未配置 Redis 的其他环境仍回退单进程内限流
- 阿里云侧认证次数和账单查询已纳入巡检：本机脚本 [check-auth-usage.ps1](D:/wuhao/scripts/check-auth-usage.ps1) 会调用 DYPNS 统计 / 账单 OpenAPI 查询一键登录和短信认证用量，不输出任何密钥。2026-06-06 默认查询最近 7 天时，一键登录和短信认证统计均为 `no_data`，月账单接口未返回费用明细，说明当前尚未形成真实认证消耗

## 后端接口

- `POST /api/auth/fusion/token`：服务端向阿里云获取融合认证 token，并返回 `auth_token + scheme_code` 给 Android SDK 使用；默认按 IP hash 做 10 分钟 20 次限流，配置 Redis 后跨进程共享
- `POST /api/auth/fusion/verify`：Android SDK 半程校验用，服务端只校验 verify token 并返回脱敏手机号，不签发账号 token、不迁移旧用户数据；默认复用融合认证登录限流配置，按 IP hash 做防刷
- `POST /api/auth/fusion/login`：Android SDK 拿到 `verify_token` 后提交，后端校验手机号并签发账号 token；默认按 IP hash 做 10 分钟 20 次限流，配置 Redis 后跨进程共享，避免伪造 verify token 反复打阿里云校验接口
- `POST /api/auth/sms/send`：发送短信验证码，默认同一手机号 + IP 10 分钟 5 次，同时同一 IP 10 分钟 20 次；配置 Redis 后跨进程共享
- `POST /api/auth/sms/login`：校验短信验证码并签发账号 token，默认 10 分钟 10 次限流；配置 Redis 后跨进程共享
- `POST /api/auth/logout`：校验当前 bearer token 后吊销当前 `session_id`，后续该 token 会被 `requireAuth` 拒绝
- `GET /api/auth/session`：校验当前 bearer token

## 必要环境变量

- `APP_SECRET`：手机号 hash 和 v2 token 签名必须依赖它
- `AUTH_STRICT=true`：生产必须开启，关闭裸 `X-User-Id` 兜底；默认也会拒绝无 `session_id` 的旧 bearer token，只接受可查库吊销的 v2 session token
- `AUTH_ALLOW_LEGACY_TOKEN=true`：只允许迁移期 / 本地兼容使用；生产公开入口不要开启，否则旧 bearer token 无法被 `POST /api/auth/logout` 吊销
- `AUTH_SESSION_DAYS`：登录保持天数，默认 3650；当前按“长期保持登录、省认证次数”口径处理，主动退出设备已通过 `POST /api/auth/logout` 吊销当前 session，完整设备管理 / 远程吊销后续再迭代
- `DYPNS_ACCESS_KEY_ID` / `DYPNS_ACCESS_KEY_SECRET`：阿里云融合认证 / 短信 API 凭证，也兼容 `ALIBABA_CLOUD_ACCESS_KEY_ID` / `ALIBABA_CLOUD_ACCESS_KEY_SECRET`
- `DYPNS_REGION_ID`：默认 `cn-hangzhou`
- `DYPNS_FUSION_SCHEME_CODE`：阿里云融合认证 SchemeCode，当前已创建并写入本机 / ECS
- `DYPNS_ANDROID_PACKAGE_NAME`：默认 `com.nongjiqiancha`
- `DYPNS_ANDROID_PACKAGE_SIGN`：Android release 签名 MD5，去掉冒号并转小写，例如 `26df23b0d32bf5a4fcd616cba22cadca`
- `DYPNS_SMS_SIGN_NAME`：短信签名
- `DYPNS_SMS_TEMPLATE_CODE`：短信模板 Code
- `DYPNS_SMS_TEMPLATE_PARAM`：默认 `{"code":"##code##","min":"5"}`
- `AUTH_FUSION_TOKEN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_FUSION_TOKEN_RATE_LIMIT_MAX_HITS` / `AUTH_FUSION_TOKEN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：融合认证 token 获取限流，默认 10 分钟 20 次
- `AUTH_FUSION_LOGIN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_FUSION_LOGIN_RATE_LIMIT_MAX_HITS` / `AUTH_FUSION_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：融合认证登录校验限流，默认 10 分钟 20 次
- `AUTH_SMS_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信发送限流，默认 10 分钟 5 次
- `AUTH_SMS_IP_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_IP_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_IP_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信发送 IP 级总限流，默认 10 分钟 20 次，防止同一 IP 轮换手机号消耗短信
- `AUTH_SMS_LOGIN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_LOGIN_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信登录校验限流，默认 10 分钟 10 次
- `REDIS_ADDR` / `REDIS_USERNAME` / `REDIS_PASSWORD` / `REDIS_DB`：可选 Redis 连接配置；配置后认证限流从单进程内存切到 Redis，主聊天流和业务真相不受影响

## 阿里云侧待办

1. 在融合认证控制台确认 `农技千查` Android 方案，包名 `com.nongjiqiancha`，签名 MD5 与 release 包一致。
2. 阿里云融合认证 Android SDK 已接入；后续真机回归时重点确认运营商网络、双卡 / 无 SIM、Wi-Fi-only、拒绝电话状态权限和 SDK 取消态都会回落验证码登录。
3. 当前半程 verify-only 和最终 login 都会对 SDK verify token 发起服务端校验；真机回归时必须确认 verify token 是否允许该顺序、是否存在一次性消耗语义、是否增加计费或失败率。如果发现最终 login 因半程校验后 token 失效，再按官方 SDK 语义调整流程。
4. 短信服务资质、签名和验证码模板已通过 CLI 配置；ECS 已写入 `DYPNS_SMS_SIGN_NAME`、`DYPNS_SMS_TEMPLATE_CODE` 并重启 `nongji-server`。
5. 上线前轮换已暴露过的主账号 AccessKey，优先改成最小权限或专用 RAM 用户口径，并重新配置 `DYPNS_ACCESS_KEY_ID` / `DYPNS_ACCESS_KEY_SECRET`。
6. 配置完成后重启 `nongji-server`，检查 `/healthz` 中 `dypns / dypns_fusion / dypns_sms` 是否为 `ok`；当前三项均已为 `ok`。

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
- 失败率或未知率是否异常升高，尤其是半程 verify-only 和最终 login 是否造成 verify token 二次校验失败
- 调用量是否明显超过真实用户访问量，防脚本刷 token 或伪造 verify token 消耗阿里云认证额度

## 安全与成本边界

- 不在数据库保存明文手机号，只保存 `APP_SECRET` HMAC 后的 `phone_hash` 和脱敏 `phone_mask`
- 不把阿里云 AccessKey、短信模板变量、APP_SECRET 写进仓库
- Android 只在用户同意协议并点击本机号码一键登录后请求 `/api/auth/fusion/token`，把 `auth_token + scheme_code` 交给官方 SDK；失败 / 取消时回落验证码登录，不走假登录或测试 ID 绕过
- `/api/auth/fusion/token`、`/api/auth/fusion/verify` 和 `/api/auth/fusion/login` 都已有 Redis / 单进程短期限流，避免 SDK 接入后被脚本反复刷 token 或伪造 verify token 消耗阿里云认证配额
- 阿里云 DYPNS 统计 / 账单必须和后端日志一起看：云侧是真实消耗，后端日志是真实业务入口；如果云侧调用量明显高于后端入口次数，优先排查密钥泄露、脚本刷接口、SDK 重试或半程 / 最终校验链路重复消耗
- `/api/auth/sms/send` 同时有手机号 + IP 和 IP 总量两层限流，避免同一出口轮换手机号消耗短信
- 生产公开入口保持 `AUTH_STRICT=true` 且不设置 `AUTH_ALLOW_LEGACY_TOKEN`，让退出设备 / session 吊销真正生效
- `chat_stream_inflight` 是临时租约，登录迁移时直接丢弃旧本机租约，不迁到手机号账号
- 多 ECS / 多实例前，认证限流必须保持 Redis 可用；验证码短期状态、失败计数和后台任务 claim 可再按需补 Redis，但不要把聊天正文或长期用户资产放入 Redis

## 参考官方文档

- 阿里云 `CreateSchemeConfig`
- 阿里云 `GetFusionAuthToken`
- 阿里云 `VerifyWithFusionAuthToken`
- 阿里云 `SendSmsVerifyCode`
- 阿里云 `CheckSmsVerifyCode`

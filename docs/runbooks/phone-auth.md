# 手机号登录与融合认证 Runbook

最后更新：2026-06-01

## 当前状态

- 后端已新增手机号账号骨架：`app_accounts`、`auth_sessions`、`user_id_migrations`
- Android 已新增登录门和验证码登录页；登录成功后保存后端签发的长期 v2 bearer token
- 登录成功后，旧本机 `user_id` 作为迁移桥，后端会尽量把旧用户数据迁到手机号账号 `acct_...`
- 阿里云融合认证 Android 方案已通过 CLI 创建，DYPNS AccessKey / Secret、`DYPNS_FUSION_SCHEME_CODE`、包名和签名已写入本机密钥文件与 ECS `/etc/nongjiqiancha/server.env`
- Android 一键登录 SDK / AAR 尚未导入；生产标准应按官方 SDK 链路接入，不再用静态 token 或测试 ID 绕过登录
- 短信登录后端接口已接阿里云 Dypns API，ECS 当前已配置 DYPNS 基础凭证、短信签名和验证码模板；`/healthz` 已显示 `dypns_sms=ok`
- 备案 / HTTPS 完成前，`api.nongjiqiancha.cn` 公网访问会被阿里云拦截；2026-06-01 为真机登录联调，Nginx 临时允许 `39.106.1.151` Host 直连反代到 Go 服务，并生成 debug APK 走 `http://39.106.1.151`
- Redis 已购买并在 `server-go` 里接成可选认证限流后端：生产 ECS 已配置 `REDIS_*` 且 `/healthz redis=ok`，融合认证 token、短信发送和短信登录校验会走 Redis 分布式限流；未配置 Redis 的其他环境仍回退单进程内限流

## 后端接口

- `POST /api/auth/fusion/token`：服务端向阿里云获取融合认证 token，后续给 Android SDK 使用；默认按 IP hash 做 10 分钟 20 次限流，配置 Redis 后跨进程共享
- `POST /api/auth/fusion/login`：Android SDK 拿到 `verify_token` 后提交，后端校验手机号并签发账号 token
- `POST /api/auth/sms/send`：发送短信验证码，默认 10 分钟 5 次限流；配置 Redis 后跨进程共享
- `POST /api/auth/sms/login`：校验短信验证码并签发账号 token，默认 10 分钟 10 次限流；配置 Redis 后跨进程共享
- `GET /api/auth/session`：校验当前 bearer token

## 必要环境变量

- `APP_SECRET`：手机号 hash 和 v2 token 签名必须依赖它
- `AUTH_STRICT=true`：生产必须开启，关闭裸 `X-User-Id` 兜底
- `AUTH_SESSION_DAYS`：登录保持天数，默认 3650；当前按“长期保持登录、省认证次数”口径处理，后续如接设备管理 / 主动退出再按后台吊销 session
- `DYPNS_ACCESS_KEY_ID` / `DYPNS_ACCESS_KEY_SECRET`：阿里云融合认证 / 短信 API 凭证，也兼容 `ALIBABA_CLOUD_ACCESS_KEY_ID` / `ALIBABA_CLOUD_ACCESS_KEY_SECRET`
- `DYPNS_REGION_ID`：默认 `cn-hangzhou`
- `DYPNS_FUSION_SCHEME_CODE`：阿里云融合认证 SchemeCode，当前已创建并写入本机 / ECS
- `DYPNS_ANDROID_PACKAGE_NAME`：默认 `com.nongjiqiancha`
- `DYPNS_ANDROID_PACKAGE_SIGN`：Android release 签名 MD5，去掉冒号并转小写，例如 `26df23b0d32bf5a4fcd616cba22cadca`
- `DYPNS_SMS_SIGN_NAME`：短信签名
- `DYPNS_SMS_TEMPLATE_CODE`：短信模板 Code
- `DYPNS_SMS_TEMPLATE_PARAM`：默认 `{"code":"##code##","min":"5"}`
- `AUTH_FUSION_TOKEN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_FUSION_TOKEN_RATE_LIMIT_MAX_HITS` / `AUTH_FUSION_TOKEN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：融合认证 token 获取限流，默认 10 分钟 20 次
- `AUTH_SMS_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信发送限流，默认 10 分钟 5 次
- `AUTH_SMS_LOGIN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_LOGIN_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信登录校验限流，默认 10 分钟 10 次
- `REDIS_ADDR` / `REDIS_USERNAME` / `REDIS_PASSWORD` / `REDIS_DB`：可选 Redis 连接配置；配置后认证限流从单进程内存切到 Redis，主聊天流和业务真相不受影响

## 阿里云侧待办

1. 在融合认证控制台确认 `农技千查` Android 方案，包名 `com.nongjiqiancha`，签名 MD5 与 release 包一致。
2. 下载 / 接入阿里云融合认证 Android SDK，并按官方文档把服务端返回的 fusion auth token 交给 SDK。
3. 短信服务资质、签名和验证码模板已通过 CLI 配置；ECS 已写入 `DYPNS_SMS_SIGN_NAME`、`DYPNS_SMS_TEMPLATE_CODE` 并重启 `nongji-server`。
4. 上线前轮换已暴露过的主账号 AccessKey，优先改成最小权限或专用 RAM 用户口径，并重新配置 `DYPNS_ACCESS_KEY_ID` / `DYPNS_ACCESS_KEY_SECRET`。
5. 配置完成后重启 `nongji-server`，检查 `/healthz` 中 `dypns / dypns_fusion / dypns_sms` 是否为 `ok`；当前三项均已为 `ok`。

## 安全与成本边界

- 不在数据库保存明文手机号，只保存 `APP_SECRET` HMAC 后的 `phone_hash` 和脱敏 `phone_mask`
- 不把阿里云 AccessKey、短信模板变量、APP_SECRET 写进仓库
- 一键登录 SDK 接入后，Android 才请求 `/api/auth/fusion/token` 并把 token 交给官方 SDK；SDK 未接好前不走假登录或测试 ID 绕过
- `/api/auth/fusion/token` 已有 Redis / 单进程短期限流，避免 SDK 接入后被脚本反复刷 token 消耗阿里云试用次数或认证配额
- `chat_stream_inflight` 是临时租约，登录迁移时直接丢弃旧本机租约，不迁到手机号账号
- 多 ECS / 多实例前，认证限流必须保持 Redis 可用；验证码短期状态、失败计数和后台任务 claim 可再按需补 Redis，但不要把聊天正文或长期用户资产放入 Redis

## 参考官方文档

- 阿里云 `CreateSchemeConfig`
- 阿里云 `GetFusionAuthToken`
- 阿里云 `VerifyWithFusionAuthToken`
- 阿里云 `SendSmsVerifyCode`
- 阿里云 `CheckSmsVerifyCode`

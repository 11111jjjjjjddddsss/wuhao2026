# 手机号登录与融合认证 Runbook

最后更新：2026-05-31

## 当前状态

- 后端已新增手机号账号骨架：`app_accounts`、`auth_sessions`、`user_id_migrations`
- Android 已新增登录门和验证码登录页；登录成功后保存后端签发的长期 v2 bearer token
- 登录成功后，旧本机 `user_id` 作为迁移桥，后端会尽量把旧用户数据迁到手机号账号 `acct_...`
- 阿里云融合认证服务已能通过 CLI 创建方案配置并返回 `OK`，但本机 CLI 未返回 `SchemeCode`，需要到控制台核对 / 复制
- Android 一键登录 SDK / AAR 尚未导入；生产标准应按官方 SDK 链路接入，不再用静态 token 或测试 ID 绕过登录
- 短信登录后端接口已接阿里云 Dypns API，但 ECS 还没配置短信签名和模板
- Redis 已购买但业务代码尚未接入；当前短信发送只有单 ECS 进程内限流，后续应迁到 Redis

## 后端接口

- `POST /api/auth/fusion/token`：服务端向阿里云获取融合认证 token，后续给 Android SDK 使用
- `POST /api/auth/fusion/login`：Android SDK 拿到 `verify_token` 后提交，后端校验手机号并签发账号 token
- `POST /api/auth/sms/send`：发送短信验证码，当前有进程内限流
- `POST /api/auth/sms/login`：校验短信验证码并签发账号 token
- `GET /api/auth/session`：校验当前 bearer token

## 必要环境变量

- `APP_SECRET`：手机号 hash 和 v2 token 签名必须依赖它
- `AUTH_STRICT=true`：生产必须开启，关闭裸 `X-User-Id` 兜底
- `AUTH_SESSION_DAYS`：登录保持天数，默认 3650；当前按“长期保持登录、省认证次数”口径处理，后续如接设备管理 / 主动退出再按后台吊销 session
- `DYPNS_ACCESS_KEY_ID` / `DYPNS_ACCESS_KEY_SECRET`：阿里云融合认证 / 短信 API 凭证，也兼容 `ALIBABA_CLOUD_ACCESS_KEY_ID` / `ALIBABA_CLOUD_ACCESS_KEY_SECRET`
- `DYPNS_REGION_ID`：默认 `cn-hangzhou`
- `DYPNS_FUSION_SCHEME_CODE`：阿里云融合认证 SchemeCode，需从控制台拿到
- `DYPNS_ANDROID_PACKAGE_NAME`：默认 `com.nongjiqiancha`
- `DYPNS_ANDROID_PACKAGE_SIGN`：Android release 签名 MD5，去掉冒号并转小写，例如 `26df23b0d32bf5a4fcd616cba22cadca`
- `DYPNS_SMS_SIGN_NAME`：短信签名
- `DYPNS_SMS_TEMPLATE_CODE`：短信模板 Code
- `DYPNS_SMS_TEMPLATE_PARAM`：默认 `{"code":"##code##","min":"5"}`
- `AUTH_SMS_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信发送进程内限流，默认 10 分钟 5 次

## 阿里云侧待办

1. 在融合认证控制台确认 `农技千查` Android 方案，包名 `com.nongjiqiancha`，签名 MD5 与 release 包一致。
2. 复制 SchemeCode，写入 ECS `/etc/nongjiqiancha/server.env` 的 `DYPNS_FUSION_SCHEME_CODE`。
3. 下载 / 接入阿里云融合认证 Android SDK，并按官方文档把服务端返回的 fusion auth token 交给 SDK。
4. 申请短信签名和登录验证码模板，写入 `DYPNS_SMS_SIGN_NAME`、`DYPNS_SMS_TEMPLATE_CODE`。
5. 配置完成后重启 `nongji-server`，检查 `/healthz` 中 `dypns / dypns_fusion / dypns_sms` 是否为 `ok`。

## 安全与成本边界

- 不在数据库保存明文手机号，只保存 `APP_SECRET` HMAC 后的 `phone_hash` 和脱敏 `phone_mask`
- 不把阿里云 AccessKey、短信模板变量、APP_SECRET 写进仓库
- 一键登录 SDK 接入后，Android 才请求 `/api/auth/fusion/token` 并把 token 交给官方 SDK；SDK 未接好前不走假登录或测试 ID 绕过
- `chat_stream_inflight` 是临时租约，登录迁移时直接丢弃旧本机租约，不迁到手机号账号
- 多 ECS / 多实例前，验证码发送限流、失败计数、fusion nonce 等应迁到 Redis

## 参考官方文档

- 阿里云 `CreateSchemeConfig`
- 阿里云 `GetFusionAuthToken`
- 阿里云 `VerifyWithFusionAuthToken`
- 阿里云 `SendSmsVerifyCode`
- 阿里云 `CheckSmsVerifyCode`

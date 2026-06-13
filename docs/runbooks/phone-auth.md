# 手机号登录与短信验证码 Runbook

最后更新：2026-06-13

## 当前结论

- 生产业务唯一长期身份是账号 ID `acct_...`；手机号只是登录凭证和回访线索，不作为业务主键。
- 同一个手机号在不同设备登录，会归到同一个 `acct_...` 账号；每台设备各有自己的 `auth_sessions` session。
- `AUTH_SESSION_DAYS` 默认 3650 天，当前按“长期保持登录、省认证次数”处理。用户不主动退出、服务端不吊销、本机 token 不丢失时，可以长期保持登录。
- Android 新包只保留一个用户可见登录入口：普通短信验证码登录。用户输入手机号、点击发送验证码、填写 6 位验证码、勾选协议后登录。
- Android 不再集成阿里云融合认证 AAR，不再拉起阿里云 SDK 页面，不再声明 `READ_PHONE_STATE / ACCESS_WIFI_STATE / CHANGE_NETWORK_STATE`，也不再为运营商取号网关放开明文 HTTP。
- 后端普通短信链路走阿里云短信服务 `SendSms`，对应用户已购买的“国内通用短信套餐包”；不再把 Android 登录建立在融合认证 SDK 或融合策略页面上。
- 已注册过的账号 ID 不受影响；同手机号再次登录仍回到同一个 `acct_...`，会员、礼品卡、聊天归档、记忆文档、反馈和日志继续按账号 ID 归属。

## 阿里云短信配置

官方口径：

- 国内短信要先完成资质、签名和模板审核；签名审核通过后才能申请模板，模板审核通过后才能发送。
- 发送接口是 `SendSms`，关键参数是手机号、短信签名、模板 Code 和模板变量。
- 验证码模板变量传真实 code，例如 `{"code":"123456"}`；不要传 `##code##` 这种占位符。
- `SendSms` 是计费接口，国内短信按运营商回执状态计费；接口 QPS 很高，但 App 侧仍必须做防盗刷。

当前账号查询结果：

- 短信签名：`北京农技千问科技`，审核状态 `AUDIT_STATE_PASS`，类型为通用类型。
- 验证码模板：`SMS_507135108`，审核状态 `AUDIT_STATE_PASS`，模板名“农技千查登录验证码”，内容为“验证码${code}，您正在登录农技千查，5分钟内有效，请勿泄露。”
- 用户已购买普通短信服务国内通用短信套餐包；该套餐抵扣普通短信服务发送量，不依赖 Android 融合认证 SDK。

## 后端接口

- `POST /api/auth/sms/send`：后端生成 6 位验证码，写 Redis HMAC 摘要，调用阿里云短信服务 `SendSms` 发送。
- `POST /api/auth/sms/login`：校验手机号和验证码，归一到 `acct_...` 账号并签发 v2 session token。
- `POST /api/auth/logout`：吊销当前设备 session，不删除聊天历史、会员权益、礼品卡或反馈记录。
- `GET /api/auth/session`：校验当前 bearer token。
- `/api/auth/fusion/*` 服务端旧接口暂保留给历史包 / 运维兼容，但新 Android 包不调用。

## 发送频率和有效期

- Android 发送按钮成功后显示 60 秒倒计时。
- 后端默认同一手机号 10 分钟最多 5 次发送，同一 IP 10 分钟最多 20 次发送。
- 后端默认同一手机号 10 分钟最多 10 次验证码登录校验。
- 验证码默认 5 分钟有效。
- 供应商返回超时或异常时，不主动清理刚缓存的验证码，避免“短信实际到了但后端验证码被删了”。

## 必要环境变量

- `APP_SECRET`：手机号 hash、手机号加密、验证码摘要和 v2 token 签名依赖它。
- `AUTH_STRICT=true`：生产必须开启。
- `AUTH_SESSION_DAYS`：登录保持天数，默认 3650。
- `SMS_ACCESS_KEY_ID` / `SMS_ACCESS_KEY_SECRET`：普通短信服务 API 凭证；当前代码也兼容读取 `DYSMS_*`、`DYPNS_*`、`ALIYUN_DYPNS_*` 和 `ALIBABA_CLOUD_*`。
- `SMS_REGION_ID`：默认 `cn-hangzhou`。
- `SMS_SIGN_NAME`：普通短信签名；当前代码也兼容 `DYPNS_SMS_SIGN_NAME`。
- `SMS_TEMPLATE_CODE`：普通短信模板 Code；当前代码也兼容 `DYPNS_SMS_TEMPLATE_CODE`。
- `AUTH_SMS_*`：短信发送 / 登录校验限流。
- `REDIS_ADDR` / `REDIS_USERNAME` / `REDIS_PASSWORD` / `REDIS_DB`：Redis 连接配置；配置后验证码和认证限流跨进程共享。

## Android 当前实现

- 登录页保留“农技千查 + 图标”品牌头。
- 登录页字段：手机号、验证码、发送、登录、协议勾选、服务协议 / 隐私政策入口。
- 登录前必须勾选协议；未勾选时不初始化 `IdManager`、不补报崩溃日志、不请求后端。
- `SessionApi.sendSmsCode(...)` 调 `/api/auth/sms/send`。
- `SessionApi.loginWithSms(...)` 调 `/api/auth/sms/login`。
- 登录成功后只接受 `acct_...` 账号 ID；Android 不保存非账号 ID 登录态。

## 构建一致性巡检

每次改 Android 登录、manifest、构建或网络安全配置后运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-android-build-parity.ps1
```

该脚本会挡住这些回退：

- debug / release 指向不同业务后端。
- 重新引入融合认证 AAR、融合登录开关、融合 Activity 或客户端调用。
- 重新声明 `READ_PHONE_STATE / ACCESS_WIFI_STATE / CHANGE_NETWORK_STATE`。
- 重新给运营商取号域名放开明文 HTTP。
- 登录页重新出现本机号码一键登录、电话权限申请或融合 SDK 调用。
- 登录页不再要求 6 位验证码或 60 秒发送倒计时。
- debug-only 预览面板脱离 `BuildConfig.DEBUG` 守卫。

## 真机排障顺序

1. 确认安装的是最新 debug / release 包，旧包不会自动变成新短信 UI。
2. 确认登录页显示手机号、验证码、发送、登录，不再出现阿里云 SDK 页面。
3. 点击发送后若失败，先看 App 日志 `auth.sms_send_failed`，再查后端日志里的 `sms send failed`。
4. 验证码收到但登录失败，看 App 日志 `auth.sms_login_failed` 和后端 `sms login verify failed`。
5. 线上健康检查应显示 `sms=ok / redis=ok / auth_strict=true`。
6. 如果短信供应商返回 `PORT_NOT_REGISTERED`，说明签名实名制报备仍未完全通过或运营商侧拦截，需要进短信服务控制台看报备状态。

## 次数与费用巡检

- 普通短信发送会消耗短信服务国内短信套餐包或按量计费。
- 后续要在阿里云短信控制台开启验证码防盗刷监控、发送量预警、套餐包余量预警和发送频率预警。
- 上线早期重点看：验证码发送成功率、失败码、短信包余量、同 IP / 同手机号异常请求和后端 429 比例。

## 仍需注意

- 普通短信验证码登录比融合认证简单稳定，但短信发送本身是成本入口，不能把后端限流全部去掉。
- 当前服务端旧融合接口和相关健康字段暂未删除，主要为历史包兼容和降低一次性后端变更风险；新 Android 包不调用它们。后续确认不再需要旧包兼容后，可单独删服务端融合接口、环境变量和监控字段。
- 上线前仍要轮换已经暴露过的主账号 AccessKey。

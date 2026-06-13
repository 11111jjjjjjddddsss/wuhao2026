# Redis Runbook

最后更新：2026-06-08

## 当前现状

- 阿里云 Redis 开源版实例已购买：`nongjiqiancha-prod-redis` / `r-2zet46zvmoo9wu3bic`
- 地域 / 可用区：`cn-beijing` / `cn-beijing-l`
- 规格：Redis 7.0、标准高可用主备、`256MB`、包年包月，到期时间 `2027-05-30T16:00:00Z`
- 网络：生产 VPC `vpc-2zeax2zowza2398b9dzot`，交换机 `vsw-2zemsq82lj2kp8za90aky`
- 内网地址：`r-2zet46zvmoo9wu3bic.redis.rds.aliyuncs.com:6379`
- 白名单：`127.0.0.1`、ECS 私网 IP `192.168.1.237`
- ECS 已验证内网 DNS、TCP 6379 和 `default` 账号密码认证可用
- `server-go` 已新增可选 Redis 客户端：只要配置 `REDIS_ADDR` / `REDIS_USERNAME` / `REDIS_PASSWORD`，启动时会先 ping Redis，失败则 fail-fast
- 生产 ECS 已配置 `REDIS_ADDR / REDIS_USERNAME / REDIS_PASSWORD / REDIS_DB`，并已部署验证短信发送、短信登录、主聊天用户级频控、App 自动日志接收、帮助与反馈用户发消息和上传限流；`/healthz` 当前返回 `redis=ok`
- 当前 Redis 只接短期认证状态和短期限流：`POST /api/auth/sms/send`、`POST /api/auth/sms/login`、`POST /api/chat/stream` 用户级频控、`POST /api/app/logs`、`POST /api/app/logs/preauth`、`POST /api/support/messages` 和 `/upload`；服务端旧 `POST /api/auth/fusion/token`、`POST /api/auth/fusion/login` 兼容接口默认已停用，只有显式 `AUTH_FUSION_COMPAT_ENABLED=true` 的极短历史包兼容窗口才会用到对应短期限流，新 Android 不调用。Redis key 只包含 scope、手机号 HMAC / SHA256 hash（短信相关）、user_id hash（主聊天 / App 日志 / 帮助与反馈 / 上传相关；登录前日志统一使用固定 `preauth`）和 IP hash，不保存明文手机号、验证码、verify token、auth token、聊天正文、反馈正文或图片内容
- 主聊天 `/api/chat/stream` 的内容、归档、额度和同用户单流仍使用 MySQL 业务真相、MySQL 用户级锁和 `chat_stream_inflight`；用户级频控配置 Redis 时跨进程共享，未配置 Redis 时回退本进程限流。不要把 Redis 写成已经接管聊天内容、额度、订单、归档、摘要锁或会员资产
- Redis 限流失败策略按链路区分：短信、登录、上传、礼品卡和内部 secret 等安全 / 成本敏感入口 fail closed；主聊天、帮助反馈和 App 自动日志在 Redis 限流操作错误或超时时 fail open，避免 Redis 短抖挡住正常问诊、客服反馈和排障日志。Redis 正常时这些链路仍按各自频率限制。

## 预期用途

- 手机号验证码：验证码短期存储、发送频率限制、错误次数限制
- 限流：用户 / IP / 设备维度的短期频控
- 多实例保护：后续多台 ECS 或回到 SAE 多实例后，用于分布式锁、摘要任务 claim、热点状态
- 缓存：会员状态、配置、今日农情等低风险热点数据

## 当前不做

- 不把聊天正文、图片内容、模型 Key、手机号明文或长期用户画像放进 Redis
- 不把 Redis 作为唯一真相来源；会员、额度、订单、聊天归档和摘要仍以 MySQL 为真相
- 不开放公网访问或 `0.0.0.0/0` 白名单
- 不把主聊天 SSE、额度扣减、归档、摘要提取和支付订单切到 Redis

## 常用检查命令

```powershell
aliyun r-kvstore DescribeInstanceAttribute --InstanceId r-2zet46zvmoo9wu3bic
aliyun r-kvstore DescribeSecurityIps --InstanceId r-2zet46zvmoo9wu3bic
```

从 ECS 检查内网连通：

```powershell
aliyun ecs RunCommand --RegionId cn-beijing --Type RunShellScript --InstanceId.1 i-2ze5nrem0jrchln4f0eh --CommandContent "getent hosts r-2zet46zvmoo9wu3bic.redis.rds.aliyuncs.com; timeout 3 bash -lc '</dev/tcp/r-2zet46zvmoo9wu3bic.redis.rds.aliyuncs.com/6379' && echo redis_tcp_ok" --Timeout 120
```

## 环境变量

- `REDIS_URL`：可选，格式 `redis://user:password@host:6379/0`
- `REDIS_ADDR`：可选，生产推荐内网地址 `r-2zet46zvmoo9wu3bic.redis.rds.aliyuncs.com:6379`
- `REDIS_USERNAME`：阿里云 Redis 7 ACL 账号，当前生产使用 `default`
- `REDIS_PASSWORD`：只保存到本机 secret 和 ECS 环境文件，不进仓库 / 文档 / Android
- `REDIS_DB`：默认 `0`
- `REDIS_DIAL_TIMEOUT_SECONDS` / `REDIS_READ_TIMEOUT_SECONDS` / `REDIS_WRITE_TIMEOUT_SECONDS` / `REDIS_PING_TIMEOUT_SECONDS`：默认 3 秒
- `REDIS_RATE_LIMIT_TIMEOUT_SECONDS`：短期限流访问 Redis 超时，默认 1 秒

认证限流参数：

- `AUTH_FUSION_TOKEN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_FUSION_TOKEN_RATE_LIMIT_MAX_HITS` / `AUTH_FUSION_TOKEN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：历史融合认证 token 获取限流，默认 10 分钟 20 次；只有 `AUTH_FUSION_COMPAT_ENABLED=true` 时才会用到，新 Android 不调用
- `AUTH_FUSION_LOGIN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_FUSION_LOGIN_RATE_LIMIT_MAX_HITS` / `AUTH_FUSION_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：历史融合认证登录校验限流，默认 10 分钟 20 次；只有 `AUTH_FUSION_COMPAT_ENABLED=true` 时才会用到，新 Android 不调用
- `AUTH_SMS_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信发送限流，默认 10 分钟 5 次
- `AUTH_SMS_IP_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_IP_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_IP_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信发送 IP 总量限流，默认 10 分钟 20 次
- `AUTH_SMS_LOGIN_RATE_LIMIT_WINDOW_SECONDS` / `AUTH_SMS_LOGIN_RATE_LIMIT_MAX_HITS` / `AUTH_SMS_LOGIN_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：短信登录校验限流，默认 10 分钟 10 次
- `CLIENT_APP_LOG_RATE_LIMIT_WINDOW_SECONDS` / `CLIENT_APP_LOG_RATE_LIMIT_MAX_HITS` / `CLIENT_APP_LOG_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：App 自动日志接收限流，默认 10 分钟 60 次
- `CHAT_RATE_LIMIT_WINDOW_SECONDS` / `CHAT_RATE_LIMIT_MAX_HITS` / `CHAT_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：主聊天用户级频控，默认 60 秒 20 次；配置 Redis 时跨进程共享，未配置 Redis 时回退单进程限流
- `SUPPORT_MESSAGE_RATE_LIMIT_WINDOW_SECONDS` / `SUPPORT_MESSAGE_RATE_LIMIT_MAX_HITS` / `SUPPORT_MESSAGE_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：帮助与反馈用户发消息限流，默认 10 分钟 20 条
- `UPLOAD_RATE_LIMIT_WINDOW_SECONDS` / `UPLOAD_RATE_LIMIT_MAX_HITS` / `UPLOAD_RATE_LIMIT_PRUNE_INTERVAL_SECONDS`：图片上传限流，默认 10 分钟 120 次

## 接入前检查

- 先确定用途：验证码、限流、缓存或分布式锁，不要为了“已经买了”强行把主链切过去
- 设计 key 前缀、TTL、失败降级策略和最大内存策略
- 生产密码只保存到本机 secret 文件和 ECS 环境文件，不进入仓库或聊天记忆
- 接入后同步复核隐私政策 / 第三方信息共享清单 / 个人信息收集清单

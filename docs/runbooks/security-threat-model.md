# 农技千查安全威胁模型

最后更新：2026-06-10

## 审计范围

本轮按外部攻击者视角复查 Android App、Go 后端、Nginx、公网 API、上传链路、内部接口、日志与基础云资源。目标是找能被公网脚本、恶意客户端、撞库/枚举、刷接口、诱导明文请求、图片滥用或后台密钥误用利用的入口。

## 关键资产

- 用户账号 session token、手机号 hash、旧本机 user_id 迁移关系
- 聊天归档、A 层滑窗、记忆文档、图片上传 URL、帮助与反馈消息
- 每日额度、会员状态、订单、礼品卡批次 / 兑换记录和后台审计
- 模型 Key、DYPNS、OSS、RDS、Redis、SLS、内部 job / support secret
- ECS / Nginx / systemd slot 发布入口和 Cloud Assistant 运维链路

## 外部攻击面

- `https://api.nongjiqiancha.cn`：认证、上传、主聊天 SSE、App 日志、帮助与反馈、检查更新、今日农情、内部 `/internal/*`
- `https://nongjiqiancha.cn` / `www`：静态官网和下载入口
- Android 客户端：本地 auth token、检查更新 APK 安装、FileProvider、定位权限、图片导入与上传
- 云资源入口：ECS 公网 80 / 443、RDS / Redis 内网白名单、OSS 私有 Bucket、SLS 日志采集

## 已确认防护

- 生产 Go 服务只监听 `127.0.0.1:3000/3001`，公网统一经 Nginx；安全组公网只放 `80 / 443` 和 ICMP，SSH 22 已关闭且本机 ssh 服务 disabled
- `api.nongjiqiancha.cn` 的 HTTP 80 已改为 ACME challenge + 301 HTTPS 跳转，避免业务接口继续接受明文请求
- 生产 `AUTH_STRICT=true`，业务接口要求 per-user bearer session token；旧裸 `X-User-Id` 在生产被拒绝
- 上传要求登录、单张 `<=1MiB` JPEG、随机文件名、后端 HTTPS base URL；聊天图片 URL 只接受本后端 `/uploads/*.jpg`
- 主聊天有用户级 Redis 限流、单用户活跃 SSE 租约、`client_msg_id` 幂等、64KiB JSON body 上限和 30 分钟 SSE 时长兜底
- 融合认证、短信、上传、App 日志、帮助与反馈均有 Redis / IP 或用户级短期限流
- 内部 secret 接口已新增 Redis / IP 频控；第一版后台 API 已有后台账号、HttpOnly session、CSRF、角色校验和 `admin_audit_logs`
- Android release 要求固定签名和 HTTPS 后端，Auto Backup / Data Extraction 已关闭，检查更新只在 HTTPS APK、SHA-256 和文件大小齐全时下发，并校验包名、版本、文件大小和 SHA-256
- SLS 只采集 Go JSON 请求日志和 Nginx error log，不采聊天正文、图片 URL、手机号、token 或模型 Key

## 本轮修复

- `server-go/internal/app/chat.go`：`client_msg_id` 增加 128 字节长度上限，提前挡住超长幂等 ID
- `server-go/internal/app/internal_security.go`：新增内部 secret 入口限流，默认同 IP / scope 10 分钟 120 次，Redis 可跨实例共享
- `server-go/internal/app/support.go` / `daily_agri.go`：内部 support / app logs / audit / 今日农情 job 入口接入上述频控
- ECS Nginx：API HTTP 80 改为 ACME challenge + HTTPS 跳转；脚本改用本机 HTTPS `--resolve` 做生产 healthz

## 剩余风险

- 第一版管理后台已部署到 `https://admin.nongjiqiancha.cn/`，并完成后台域名、Nginx 静态托管、`/admin-api/` 反代、HTTPS、bootstrap 初始化和 bootstrap 环境变量清理；当前 `/internal/*` 仍保留共享 secret 过渡入口给脚本兼容，不能把 secret 放进浏览器前端
- Android 长期 auth token 仍保存在普通 SharedPreferences；备份已禁用，但 Root、恶意调试或设备被拿到时仍可能被窃取。后续可评估 EncryptedSharedPreferences、设备管理和远程吊销
- 没买 WAF / 高防时，普通 Web 扫描和脚本刷接口主要靠 Nginx / Go / Redis 限流；大流量 DDoS 超过基础防护仍可能不可用
- SLS 已有 5 条 AlertHub 最小告警，但还没有短信 / 电话 / 机器人等外部通知和仪表盘；现在能查日志、能在 AlertHub 里看到最小规则，但还不能自动叫醒或自动处置
- 多 ECS 前，记忆文档摘要 running guard 仍需升级为 Redis / MySQL lease，避免跨实例重复抽取

## 后续优先级

1. 管理后台继续补高风险操作二次确认、角色细化、SLS 外部通知 / 仪表盘、发布 / 回滚记录和数据库只读排查入口。
2. SLS 继续补 healthz、RDS / Redis / ECS 高水位和认证用量 / 模型成本告警，并给现有 AlertHub 规则配置通知渠道。
3. 上线前轮换已暴露过的主账号 AccessKey，并优先改成专用最小权限 RAM 用户。
4. 真机回归手机号登录、验证码登录、主聊天、图片上传 / 读取 / 模型拉图和检查更新。

# ECS / 官网基础安全加固 Runbook

最后更新：2026-06-06

## 当前结论

首版不先购买阿里云付费 WAF、云防火墙或 DDoS 高防。当前单台 ECS + Nginx + Go 后端阶段，优先把免费 / 低成本基础防护做足：

- 安全组只开放公网 `80 / 443` 和 ICMP；公网 SSH `22` 已撤掉，服务器本机 `ssh` 服务也已停用并 disabled
- 阿里云云安全中心免费版已生效，ECS 安全中心 agent 在线，当前显示免费版、客户端 online、风险计数 0
- 阿里云 DDoS 原生防护基础版默认免费开启，按官方口径提供基础 DDoS 防护能力，但不能抵御无限攻击流量
- Nginx 已有 API 级 IP 限流、连接数限制、Host 未命中返回 444、HTTPS 和安全响应头
- Go 后端已有严格鉴权、请求体大小限制、用户 / IP / Redis 短期限流、SSE 时长兜底、模型出站超时边界
- ECS 已启用 fail2ban，当前 jail 为 `sshd`
- RDS / Redis 只放通 ECS 私网 IP，OSS Bucket 私有读写，Android 不持有 OSS 或模型密钥

## 免费防护项

阿里云侧免费 / 默认可用：

- 安全组：ECS 外层入站端口控制，只留 `80 / 443` 给 Nginx
- DDoS 基础防护：公网 IP 默认基础防护；大流量攻击超过基础阈值时仍可能触发黑洞或不可用
- 云安全中心免费版：基础异常登录检测、漏洞扫描、应急漏洞检测、AK 泄露检测和合规检查；免费版偏检测，不负责自动处置
- 云监控基础指标：后续用于 ECS / RDS / Redis 资源和可用性告警

服务器 / 应用侧免费防护：

- Nginx `limit_req / limit_conn`：挡普通脚本刷接口、上传和聊天流
- Nginx 安全响应头：隐藏版本号、`nosniff`、`frame-ancestors / X-Frame-Options`、HSTS、Referrer Policy
- Go 接口限流和 body 限制：即使绕过 Nginx 也有第二层保护
- fail2ban：识别 SSH 暴力尝试；公网 SSH 已关后主要作为兜底观察
- 日志巡检：Nginx error/access、`journalctl` 和 App 自动日志先顶住，后续再接 SLS

## 已执行加固

2026-06-06 已执行：

- 通过阿里云 CLI 撤销安全组 `sg-2ze4tilwxw1h5w77lwl1` 的公网 `TCP 22 / 0.0.0.0/0` 入站规则
- 通过 Cloud Assistant 停止并禁用 ECS 本机 `ssh` 服务，避免未来误开安全组时机器继续监听公网 22
- 复查安全组当前公网只放行 `TCP 80`、`TCP 443` 和 ICMP
- 通过 Cloud Assistant 写入 `/etc/nginx/conf.d/nongjiqiancha-security.conf`
- Nginx 配置验证通过并 reload
- 复查云安全中心免费版：ECS `i-2ze5nrem0jrchln4f0eh` 客户端 online，版本为免费版，当前风险计数 0
- 复查 fail2ban：服务 active，`sshd` jail 存在

## 自动加固脚本

本机执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\harden-ecs-security.ps1
```

脚本会：

1. 查询安全组并撤销公网 `22/22` 入站规则
2. 写入 Nginx 安全头配置
3. 停止并禁用 ECS 本机 `ssh` 服务
4. 执行 `nginx -t` 并 reload
5. 输出 fail2ban、云安全中心 agent 和监听端口状态
6. 输出当前安全组入站放行列表

脚本不读取、不打印、不修改任何密钥。

## 付费产品判断

- Web 应用防火墙 WAF：主要防 SQL 注入、XSS、Web 扫描、CC、Bot 等 HTTP/HTTPS 层攻击。等 App 公开上线、真实访问量上来，或 Nginx 日志出现持续 Web 攻击 / CC 压力时，优先考虑 WAF
- 云防火墙：更偏云上网络边界、VPC 间、互联网边界和资产统一策略管理。当前只有单台 ECS + RDS / Redis 内网白名单，不是首版最优先购买项
- DDoS 高防 / 原生防护企业版：只有遇到明显大流量 DDoS、基础防护触发黑洞、业务公网 IP 长时间不可用时再买；它解决的是大流量攻击，不解决应用逻辑刷接口

## 日常巡检

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-ecs-readiness.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\harden-ecs-security.ps1
```

服务器内只读查看：

```bash
nginx -t
systemctl status fail2ban --no-pager
fail2ban-client status
fail2ban-client status sshd
cat /etc/nginx/conf.d/nongjiqiancha-security.conf
journalctl -u nongji-server-3000 -u nongji-server-3001 -n 120 --no-pager
tail -n 120 /var/log/nginx/error.log
```

## 仍需注意

- 关闭公网 SSH 不等于服务器不会被攻击；它只是减少最常见入口
- 当前 SSH 服务已停用；若后续确需临时 SSH，先通过 Cloud Assistant 启动 `ssh`，再按固定来源 IP 临时放通安全组，用完必须关闭
- HTTP/HTTPS 对外开放是业务必须入口，仍需要 Nginx / Go / Redis 限流和日志观察
- 同行恶意刷接口时，优先看 Nginx 429、Go 侧限流日志、模型调用量和 DYPNS / OSS / RDS 成本曲线
- 真正大流量 DDoS 超过基础防护能力时，免费策略无法保证持续可用，需要临时购买高防或让云厂商清洗
- 管理后台上线前必须做后台账号、权限、审计和限流，不能把内部 secret 暴露给浏览器前端

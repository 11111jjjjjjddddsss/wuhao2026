# 扩容预案 Runbook

最后更新：2026-06-16

## 目的

把以后“用户多了怎么办”提前拆成低风险步骤。当前不做微服务、不上 Kubernetes、不引入重消息队列；先保持 Go 单体，把状态尽量放在 MySQL / Redis / OSS，确保以后从单 ECS 扩到多 ECS 时不需要推倒重来。

## 当前扩容基础

- Go 服务是无本地业务真相的单体：账号、会员、额度、聊天归档、上下文、订单和反馈都在 MySQL
- 图片在 OSS，Android 不持有 OSS 凭证
- 认证 / 短信 / 上传 / 帮助与反馈 / App 日志 / 主聊天用户级限流已支持 Redis 分布式限流
- 主聊天同用户单流控制使用 MySQL `GET_LOCK` + `chat_stream_inflight`，可跨多台 ECS 生效
- 数据库迁移启动时使用 MySQL 全局锁，降低多实例同时跑 DDL 风险
- 记忆文档摘要失败重试标记已经落在 MySQL `session_ab.pending_retry_b`，待提取窗口队列保存在 `session_ab.pending_memory_jobs_json`：Free / Plus 每 6 条完整 AI 回复、Pro 每 9 条完整 AI 回复触发时冻结当时 A 层 6 / 9 轮窗口为一个 job；模型失败、超时、写库失败、旧快照写回过期或 Redis 租约暂不可用时都会保留 pending 和队首冻结 job，后续每一条新的完整 AI 回复归档后都会马上补提取，不等下一个 6 / 9 轮整除点；2026-06-20 起后端还会由 `startMemorySummaryDrainWorker` 默认每 10 分钟小批量扫描仍 pending 的账号继续补提取，避免用户停聊后 pending 长期挂着。补提取仍只读取该冻结 6 / 9 轮窗口 + 旧记忆文档，不扩大到 30 轮归档输入，不把失败 job 灌进主聊天上下文；成功写回且 `round_total + updated_at + session_generation + cleared_at` 匹配后只弹出队首 job，还有剩余 job 时继续 pending
- 当前记忆文档摘要的同用户运行中保护是“单进程 `running` guard + Redis TTL 租约”；没有配置 Redis 的本地 / 简化环境会回退到单进程保护。扩多台 ECS 前应灰度验证 Redis 租约、pending 重试和 `round_total` 写回保护，而不是再复制一份纯进程内 map
- 部署脚本已支持本机打包后通过 Cloud Assistant 下发，不依赖 ECS 上保存 GitHub 凭据

## 2026-06-16 复查结论

当前不建议立刻升配，也不建议为了“看起来更安全”先买一堆付费防护。

刚跑过的只读巡检结论：

- ECS 2C4G 负载接近 0，可用内存约 2.8GiB，系统盘约 15%
- RDS 1C2G 磁盘约 5.9%，近 30 分钟 CPU / 内存 / 连接都很低
- Redis 256MiB 当前内存约 2.2%，连接和 CPU 都很低
- 公网黑盒、ECS readiness、资源容量严格巡检、SLS 成本守卫、数据留存成本守卫均为 ready
- Go 侧最近 240 行日志没有业务 5xx 或 429；可见慢请求只有今日农情内部生成，属于模型搜索生成耗时，不是 API 性能瓶颈
- 公网扫描主要是根路径、`/mcp`、`/sse`、`/login` 等探测，当前返回 404 或被既有限流 / Nginx 规则挡住，没有看到需要马上买 WAF 的信号

当前最值得做的是继续保持低成本监控、真机回归和上线前 AccessKey 轮换；等真实用户量上来后，再按下面路线扩。

## 升级触发线

不要靠感觉升配，先看指标：

- ECS：CPU 或内存连续多个 5 分钟周期超过 70%，或 Go 日志出现持续 5xx / 上游超时 / goroutine 堆积迹象
- RDS：CPU / 内存 / 连接数 / IOPS 连续高水位，或后台聚合、用户历史、会员 / 礼品卡查询出现慢 SQL
- Redis：内存超过 70%、连接数或 CPU 持续高水位，且限流 / 验证码链路出现异常
- 带宽：5Mbps 出口被 APK 下载、图片回看、扫描流量或突发访问打满
- 攻击：Nginx 429 / 403 / 444、SLS 5xx、云安全中心、DDoS 基础防护黑洞或成本异常持续出现

指标没触发前，不盲目调大 MySQL 连接池、不加全局聊天并发闸、不用模型 `max_tokens` 截断用户体验。

## 不急着做

- 不拆微服务
- 不上 Kubernetes
- 不上 Kafka
- 不提前做复杂消息队列
- 不把聊天正文、会员资产、额度或订单放进 Redis

## 阶段路线

### 阶段 1：单机原地升级

触发条件：
- CPU / 内存长期接近瓶颈
- Nginx / Go / MySQL 连接数出现压力
- 用户量增长但还没到需要多实例

动作：
- ECS 从 2C4G 原地升到 4C8G 或更高
- 按 RDS 规格调整 `MYSQL_MAX_OPEN_CONNS / MYSQL_MAX_IDLE_CONNS`
- 根据真实访问量调整 Nginx IP 限流和 `CHAT_RATE_LIMIT_*`
- 接 SLS 后看 5xx、SSE 中断、上传失败、模型失败

注意：

- 包年包月 ECS 改规格通常需要按阿里云控制台 / OpenAPI 支持的目标规格执行，并安排低峰窗口；涉及停机或重启时，先发公告或至少避开代理集中测试时间
- 升配前先跑 `check-ecs-readiness.ps1`、`check-resource-capacity.ps1 -Strict`、`check-public-blackbox.ps1` 和 `check-server-performance.ps1`，升配后重复跑同一组脚本
- 若只是图片 / APK 下行挤占 5Mbps，优先考虑下载域名、OSS / CDN 分流或提高公网带宽，不一定先加 CPU

### 阶段 2：SLB + 多 ECS

触发条件：
- 单机升级后仍不够
- 需要发布时不中断服务
- 高峰期单机风险不可接受

上线前检查：
- `REDIS_*` 在所有 ECS 都配置一致
- `UPLOAD_STORAGE_BACKEND=oss`，不能回退本地文件
- `BASE_PUBLIC_URL / UPLOAD_BASE_URL` 一致
- `APP_SECRET` 一致，否则 token / 手机号 hash 会失效
- `DASHSCOPE_API_KEY_1/2/3` 主备池一致或按实例分配
- `AUTH_STRICT=true`

仍需补的准备：
- 记忆文档摘要已从本进程 map 进一步加了 Redis TTL 租约，lease key 按账号维度脱敏派生；抢到 lease 的实例才允许调用摘要模型，写回仍必须保留现有 `round_total + updated_at + session_generation + cleared_at` 校验，失败、超时或 Redis 租约暂不可用继续保留 `pending_retry_b`
- 发布脚本支持多实例滚动发布和逐台 healthz 验证
- SLS 统一采集所有 ECS 的 `nongji-server` 和 Nginx 日志
- Nginx / SLB 后的真实 IP 链路必须复核：当前单 ECS 直连公网阶段，Nginx 覆盖 `X-Real-IP $remote_addr` 和 `X-Forwarded-For $remote_addr`，不把客户端自带 XFF 链传给 Go；Go `GetClientIP` 只信任本机 / 内网代理来源的转发头。后续接 SLB / ALB / CDN / WAF 时，必须先配置可信上游 `real_ip_header` / `set_real_ip_from` 并重新验证 `X-Forwarded-For`、`X-Real-IP`、Go `GetClientIP`、Redis 限流 IP hash、地区推断都取到真实客户端 IP，不能把 SLB 内网 IP 当成所有用户来源，也不能重新信任公网客户端伪造的 XFF
- 多实例必须保持 `APP_SECRET`、`AUTH_STRICT`、Redis、OSS、MySQL、模型 Key 策略和 `UPLOAD_BASE_URL / BASE_PUBLIC_URL` 一致；`APP_SECRET` 不支持随意轮换，见 `security-hardening.md`

### 阶段 3：数据库扩容

触发条件：
- MySQL CPU / IOPS / 慢 SQL 成为瓶颈
- 读多写少接口开始影响主写链路

动作：
- 先升 RDS 规格
- 增加只读实例，查询型后台 / 报表走只读
- 复查 `session_round_archive`、`client_app_logs`、`support_messages` 的索引和保留策略
- 长期历史做归档分层，不影响 30 天 UI 恢复主链

### 阶段 4：轻量队列

触发条件：
- 今日农情、记忆文档摘要、反馈洞察、日志聚合、支付对账这类后台任务开始影响在线请求
- 需要统一失败重试、延迟任务和消费监控

建议：
- 优先阿里云 MNS 或 Redis Stream
- 只搬非实时任务
- 主聊天 SSE 仍保持请求内实时链路，不经队列

## 现在已提前完成的准备

- Redis 已上线并健康
- OSS 已上线并健康
- 主聊天单用户活跃流已经落 MySQL 锁和租约
- 主聊天用户级限流已支持 Redis
- 上传、认证、反馈、日志限流已支持 Redis
- 数据库迁移有 MySQL 全局锁
- ECS 发版和回滚脚本已有基础入口

## 还没做但扩机前必须做

1. 灰度验证记忆文档摘要 Redis TTL 租约；不要退回只靠当前进程内 `sync.Map` 保护的多实例形态
2. 多 ECS 滚动发布脚本
3. SLS 日志采集和最小告警
4. DashScope 多账号 Key 池和成本监控
5. RDS 慢 SQL / 连接数监控

## 安全升级路线

安全也按信号升级：

1. 当前：安全组只开 80 / 443 / ICMP，SSH 关闭；Nginx + Go + Redis 限流；SLS / 云监控邮件；阿里云基础 DDoS 和云安全中心免费版
2. 出现持续 Web 扫描、CC、后台登录攻击或明显异常 429 / 5xx：先调 Nginx / Go 限流、加黑名单或更细规则，再评估 WAF
3. 出现公网带宽被攻击流量打满、DDoS 基础防护黑洞或业务长时间不可用：再考虑 DDoS 高防 / 原生防护企业版
4. 静态官网、APK 下载或公开资源流量挤占 API 出口：再评估 CDN / OSS 下行分流；私有问诊图片不能直接改成长缓存公开 CDN

## 给用户的口径

你不用理解所有细节。以后扩容时按这个顺序走：

1. 先原地升 ECS
2. 仍不够再加 SLB 和第二台 ECS
3. 数据库压力起来再升 RDS / 加只读
4. 后台任务多了再上轻量队列

当前项目已经按这个方向留好了基础，不需要现在推倒重来。

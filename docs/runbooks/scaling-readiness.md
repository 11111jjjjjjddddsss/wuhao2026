# 扩容预案 Runbook

最后更新：2026-06-06

## 目的

把以后“用户多了怎么办”提前拆成低风险步骤。当前不做微服务、不上 Kubernetes、不引入重消息队列；先保持 Go 单体，把状态尽量放在 MySQL / Redis / OSS，确保以后从单 ECS 扩到多 ECS 时不需要推倒重来。

## 当前扩容基础

- Go 服务是无本地业务真相的单体：账号、会员、额度、聊天归档、上下文、订单和反馈都在 MySQL
- 图片在 OSS，Android 不持有 OSS 凭证
- 认证 / 短信 / 上传 / 帮助与反馈 / App 日志 / 主聊天用户级限流已支持 Redis 分布式限流
- 主聊天同用户单流控制使用 MySQL `GET_LOCK` + `chat_stream_inflight`，可跨多台 ECS 生效
- 数据库迁移启动时使用 MySQL 全局锁，降低多实例同时跑 DDL 风险
- B/C 摘要失败重试标记已经落在 MySQL `session_ab.pending_retry_b / pending_retry_c`：模型失败、超时、写库失败、C 层归档不足 20 轮或旧快照写回过期时都会保留 pending，后续轮次完成后继续补提取；成功写回且 `round_total` 匹配后才清 pending
- 当前 B/C 摘要的同用户同层运行中保护仍是单进程 `running` guard，只适合当前单 ECS / 单 active slot 主链；扩多台 ECS 前必须升级为 Redis / MySQL lease，避免多机同时抽取同一层、重复消耗 Qwen3.5-Flash token 或同一 `round_total` 下非确定性覆盖
- 部署脚本已支持本机打包后通过 Cloud Assistant 下发，不依赖 ECS 上保存 GitHub 凭据

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
- B/C 摘要 `running` guard 从本进程 map 升级为 Redis / MySQL lease，lease key 至少包含 `user_id + layer`，并记录触发时 `round_total`、过期时间和 owner；抢到 lease 的实例才允许调用 Qwen3.5-Flash，写回仍必须保留现有 `round_total` 校验，失败继续保留 `pending_retry_b/c`
- 发布脚本支持多实例滚动发布和逐台 healthz 验证
- SLS 统一采集所有 ECS 的 `nongji-server` 和 Nginx 日志

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
- 今日农情、B/C 摘要、反馈洞察、日志聚合、支付对账这类后台任务开始影响在线请求
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

1. B/C 摘要 running guard 改为 Redis / MySQL lease；不要只复制当前进程内 `sync.Map` 保护到多实例
2. 多 ECS 滚动发布脚本
3. SLS 日志采集和最小告警
4. DashScope 多账号 Key 池和成本监控
5. RDS 慢 SQL / 连接数监控

## 给用户的口径

你不用理解所有细节。以后扩容时按这个顺序走：

1. 先原地升 ECS
2. 仍不够再加 SLB 和第二台 ECS
3. 数据库压力起来再升 RDS / 加只读
4. 后台任务多了再上轻量队列

当前项目已经按这个方向留好了基础，不需要现在推倒重来。

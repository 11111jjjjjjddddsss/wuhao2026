# 统一管理后台 Runbook

最后更新：2026-06-08

## 目的

记录“农技千查”统一管理后台当前实现、上线方式、第一版页面能力和仍需补齐的安全边界。

当前第一版后台已进入代码并已部署到生产：`admin` 是 Vite 静态前端，`server-go` 暴露 `/admin-api/v1/*` 管理 API，并新增后台账号 / session / CSRF、角色校验和审计。生产入口为 `https://admin.nongjiqiancha.cn/`，Nginx 静态托管后台前端并同域反代 `/admin-api/` 到当前 active Go slot；一次性 bootstrap 环境变量已用于初始化 owner 账号，随后已从 ECS 环境文件清理。

详细页面结构、筛选项、指标和版面建议见 [admin-dashboard-design.md](D:/wuhao/docs/runbooks/admin-dashboard-design.md)。

## 当前真相

- 管理后台前端目录：`admin`。本地开发：`cd admin && npm install && npm run dev -- --host 127.0.0.1 --port 5174`。生产构建：`npm run build`。
- 生产部署脚本：[deploy-ecs-admin.ps1](D:/wuhao/scripts/deploy-ecs-admin.ps1)。脚本会构建 `admin/dist`、同步 `admin` A 记录、上传静态包、配置 Nginx、签发 / 复用 Let's Encrypt HTTPS 证书，并验证首页和未登录 API 状态。
- 生产入口：`https://admin.nongjiqiancha.cn/`。HTTP 80 只用于 ACME challenge 和 301 跳转；HTTPS 下 `/admin-api/` 由 Nginx 反代到当前 active Go slot。
- 管理后台 API：`/admin-api/v1/*`，由 `server-go` 提供，不单独起第二套后端。
- 后台登录：`POST /admin-api/v1/auth/login`，成功后写 HttpOnly session cookie 和 CSRF cookie，前端请求带 `X-Admin-CSRF`。
- 后台账号：服务启动时可用 `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` 初始化；密码会以 PBKDF2-SHA256 hash 存入 `admin_users`，明文不得写入仓库、文档或前端。
- 后台角色：首版支持 `owner`、`ops_readonly`、`support`、`content_ops`、`release_ops`、`finance_ops`、`auditor`；服务端校验权限，不能靠前端隐藏按钮。前端侧栏和监控快捷入口会按同一角色矩阵隐藏无权页面，减少误点和 403，但这只是体验收敛，不是安全边界。
- 后台审计：登录、登出、查询用户、客服回复、日志查询、今日农情、检查更新、礼品卡生成 / 查询 / 作废 / 用户兑换等会写审计记录。
- Android 没有后台入口，也没有调用任何 `/internal/*` 或 `/admin-api/*` 接口。

当前已落地的后台页面 / API：

- 总览：`GET /admin-api/v1/overview`，展示健康状态、今日问诊、App 错误、未回复反馈和今日农情状态。
- 监控面板：`GET /admin-api/v1/monitoring`，聚合服务健康、今日 / 24h / 7d 使用情况、App 自动日志错误、待回复反馈、今日农情、礼品卡兑换异常、后台操作失败、地区分布和 App 错误 Top；响应额外返回 `action_items` 和 `capabilities`，前端已收成“当前结论 / 登录与账号ID / 礼品卡与权益 / App质量”决策卡、快捷入口、关键队列和明细表，让非运维也能先看出今天能不能继续测试。监控窗口里的 `active_sessions` 表示当前有效 App session 总量，`recent_auth_sessions` 表示该窗口内新创建 / 登录 session。未接入的 SLS 自动告警、发布 / 回滚按钮不会伪装成已完成。
- 用户管理：`GET /admin-api/v1/users`、`GET /admin-api/v1/users/detail`，按账号ID（底层字段仍叫 `user_id`）/ 脱敏手机号查询，展示会员、额度、加油包、升级补偿、订单、礼品卡、最近问诊、App 日志和反馈。
- 会员额度：用户级只读展示当前档位、到期时间、每日额度、`quota_ledger` 扣次流水、`topup_packs` 加油包包明细、`upgrade_credits` 升级补偿、订单记录和礼品卡兑换记录。
- 礼品卡：`GET/POST /admin-api/v1/gift-cards/batches`、`GET /admin-api/v1/gift-cards/summary`、`GET /admin-api/v1/gift-cards/cards`、`POST /admin-api/v1/gift-cards/void`、`GET /admin-api/v1/gift-cards/attempts`；可创建 Plus / Pro 礼品卡批次、查询全局汇总、按批次 / 状态 / 账号ID / 卡码尾号追溯卡状态，按账号ID / 尾号 / 成功状态 / 失败原因查询兑换尝试，并可作废未兑换卡。完整卡码只在生成响应当次返回，数据库只存 hash / 掩码 / 尾号。
- 用户侧礼品卡兑换：`POST /api/gift-cards/redeem`，鉴权后事务内校验卡状态并发会员权益，记录成功 / 失败尝试、地区和脱敏 IP；Android 设置页“礼品卡”已经接真实兑换接口。
- 帮助与反馈：`GET /admin-api/v1/support/conversations`、`GET /admin-api/v1/support/messages`、`POST /admin-api/v1/support/messages`。
- App 自动日志：`GET /admin-api/v1/app-logs`，继承自动日志脱敏规则，不展示聊天正文、图片 URL、手机号或 token。
- 后台审计：`GET /admin-api/v1/audit-logs`。
- 今日农情：`GET /admin-api/v1/today-agri/cards`。
- 检查更新：`GET /admin-api/v1/app-update/android`，当前只读展示 `APP_ANDROID_*` 环境变量配置。

仍保留的内部共享密钥接口：

- 帮助与反馈、App 自动日志、内部审计、今日农情生成仍保留 `/internal/*` 入口作为脚本 / 运维兼容入口，但浏览器后台不应持有 `SUPPORT_ADMIN_SECRET` 或 `DAILY_AGRI_JOB_SECRET`。

## 当前不要误解

- 买服务器不等于自动有管理后台。
- `SUPPORT_ADMIN_SECRET`、`DAILY_AGRI_JOB_SECRET` 仍只是共享密钥，不能给浏览器前端使用；正式后台浏览器入口必须走 `admin_users` / `admin_sessions` / CSRF。
- `/admin-api/v1/monitoring`、`/admin-api/v1/app-logs` 和 `/internal/app/logs` 是给运维和后台面板用的只读 / 聚合入口，不是完整 SLS 告警中心。
- `/api/app/update` 只是读取版本配置，不是发布系统；正式发布仍需要记录 APK 链接、SHA-256、大小、签名指纹、操作人和时间。
- 礼品卡后端、后台和 Android 兑换入口已接入首版；Android 只在 `/api/gift-cards/redeem` 返回成功后展示“兑换成功”，没有后端成功结果时不能弹真实成功。历史完整卡码不可导出是安全设计，因为数据库只保存 hash / 掩码 / 尾号。
- 开发期会员接口不是正式支付回调，生产必须保持关闭。
- Android 客户端不能承载后台逻辑；客服回复、补权益、发礼品卡、停更新和删除用户数据都必须在服务端或后台完成。

## P0：服务器落地前规划（历史阶段）

- 不提前硬做完整后台，避免账号体系、权限模型、数据库结构和真实运维入口未定时返工。
- 继续用 runbook、内部接口和只读脚本规划兜底。
- 继续按功能巡检，把当前真相和后续必补项写入 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。

该阶段已经结束：ECS / RDS / Redis / OSS / DNS、内部接口和第一版网页后台代码已经落地。P1 后台生产入口也已部署到 `admin.nongjiqiancha.cn`，并完成管理员 bootstrap、清理 bootstrap 环境变量、HTTPS、首页、登录和总览 API 验收；后续重点是补 SLS 告警 / 仪表盘、数据库只读脚本和更细的运营动作。

## P1：服务器落地后的最小网站后台

第一版目标是一个受保护的网站后台，做到“能查、能回复、能停错、能留痕”，不是做重型运营系统。后台逻辑必须在服务端，Android 不调用 `/internal/*`，也不承载客服、发版、补权益或审计逻辑。

### 建议技术架构

- 前端：继续用 Vite 静态前端，建议放在 `admin` 或 `site-admin` 目录，不混进公开官网 `site`。公开官网仍只做展示；后台是独立受保护入口。
- 后端：继续复用 `server-go`，新增 `/internal/admin/*` 或 `/admin-api/*` JSON API。不要为第一版另起 Node / Java / Python 后端，避免多一套服务、部署、日志和密钥面。
- 部署：首版仍走当前 ECS + Nginx。后台前端 build 成静态文件，由 Nginx 单独域名或路径托管；后台 API 仍反代到 Go 服务。
- 域名：优先用独立子域名，例如 `admin.nongjiqiancha.cn`。如果暂时不想新配证书，也可以先用隐藏路径，但正式运营前更推荐独立域名、独立 Nginx location 和更严格安全头。
- 登录：必须先做服务端后台账号 / session / CSRF 防护。浏览器前端不能持有 `SUPPORT_ADMIN_SECRET`、`DAILY_AGRI_JOB_SECRET`、模型 Key、云资源 AccessKey 或数据库密码。
- 数据真相：后台只通过 Go API 读写 MySQL / Redis / OSS / SLS，不让浏览器直接连数据库、SLS 或阿里云 OpenAPI。
- 审计：所有后台登录、查询敏感用户、回复反馈、改版本、补权益、礼品卡生成 / 作废、今日农情补跑 / 停用都写服务端审计。
- 权限：第一版也要有最小角色，而不是所有人一个超级密码。建议 `owner`、`ops_readonly`、`support`、`content_ops`、`release_ops`、`finance_ops`、`auditor`。
- 安全：后台入口必须 HTTPS、SameSite Cookie、接口限流、登录失败限制、密码哈希、服务端授权校验和审计；不要把“前端隐藏按钮”当权限。
- 账号初始化：后台初始账号只能通过一次性环境变量、Cloud Assistant 脚本或本机安全脚本写入数据库 hash；账号名和明文密码不能写进仓库、文档、前端代码或部署脚本。初始化成功后应禁用 bootstrap，并要求首次登录改密码。
- 生产初始化状态：2026-06-07 已通过一次性 bootstrap 创建 owner 账号，随后已从 `/etc/nongjiqiancha/server.env` 删除 `ADMIN_BOOTSTRAP_*` 并重启 active slot；后续若忘记密码，应通过临时 bootstrap 或受控运维脚本重置，仍不得在仓库或文档中记录明文密码。

### 第一版页面建议

第一版按“可运营、可排障、少误操作”做，不追求花哨。推荐左侧导航：

- 总览：服务健康、今日请求量、错误量、登录 / 短信 / 一键登录状态、模型 Key 池健康、Redis / RDS / OSS / SLS 状态、最近 5xx / 慢请求。
- 用户查询：按 `user_id`、脱敏手机号 hash / 后续手机号查询入口、最近活跃时间、App 版本、设备、地区可信度、会员状态、额度、加油包、最近反馈、最近 App 自动日志。
- 用户地区 / 来源：按注册、最近活跃、问诊、图片问诊、会员成交、加油包购买和帮助反馈聚合省市分布；优先使用 GPS 反查地区，IP 粗定位只作为低可信参考，不保存经纬度或轨迹。
- 会员与额度：只读展示当前档位、到期时间、每日额度、今日已用、加油包余额、升级补偿和 `quota_ledger`。人工补偿先不开放，或只做 owner 二次确认。
- 订单 / 订购：支付未接入前只做占位和开发期订单表只读说明；支付接入后再接正式订单、回调、对账、退款和异常补偿。
- 礼品卡：首版已接入批次、生成、兑换、卡状态、失败尝试查询、失败原因聚合和未兑换卡作废；批量发放、发放对象管理和更细风控后续再补。
- 帮助与反馈：会话列表、未回复队列、详情、后台回复、处理状态、标签、搜索；首版直接接现有 `/internal/support/*`，后续补工单状态。
- App 自动日志：按时间、用户、事件名、level、App 版本、系统版本、设备筛选；接 `GET /internal/app/logs`，后续再并入 SLS 摘要。
- 今日农情：当天卡片状态、来源、生成时间、失败原因、手动补跑、停用当天卡片，所有动作审计。
- 检查更新：当前发布版本、APK URL、SHA-256、文件大小、是否启用、停更开关、发布记录；后续落 `app_releases` 表，不长期手改环境变量。
- 后台操作审计：登录、查询、回复、改配置、补权益、礼品卡操作、发版、今日农情补跑等操作记录，支持按 actor / action / target_user / 时间筛选。
- 产品洞察：首版只展示脱敏聚合，不直接铺完整聊天正文。来源包括帮助与反馈、App 自动日志、后续用户行为聚合和人工标签。
- 用户聊天 / 反馈洞察：后续允许由后台任务定期扫描最近 N 天脱敏归档和反馈，抽取问题标签、影响人数、严重度、代表短摘和建议改动，写入产品洞察报表；Codex 后续优先读洞察报表，不直接长期读取生产库完整聊天全文。

### 当前后端接入矩阵

| 模块 | 当前能否直接接 | 当前真源 | 第一版要补 |
|---|---|---|---|
| 服务健康 / 监控面板 | 已接入首版 | `/healthz`、管理 API、业务表、App 自动日志、后台审计 | SLS 自动告警 / 仪表盘、Nginx access 聚合、登录精准漏斗 |
| 帮助与反馈 | 可直接接 | `support_messages`、`/internal/support/*` | 会话状态、标签、处理人、搜索 |
| App 自动日志 | 已接入首版 | `client_app_logs`、`/internal/app/logs`、`/admin-api/v1/app-logs` | 更细的版本 / 设备 / 地区聚合和告警 |
| 后台审计 | 可直接接 | `admin_audit_logs`、`/internal/admin/audit-logs` | 后台账号 actor、角色、请求 ID |
| 用户查询 | 已接入首版 | `app_accounts`、`auth_sessions`、`session_ab`、`session_round_archive`、`/admin-api/v1/users*` | session 管理、更多筛选和导出审批 |
| 会员 / 额度 | 已接入用户级只读 | `user_entitlement`、`daily_usage`、`quota_ledger`、`topup_packs`、`upgrade_credits` | 全局统计、人工补偿二次确认和审计 |
| 今日农情 | 已接入只读状态 | `daily_agri_cards`、内部生成接口、`/admin-api/v1/today-agri/cards` | 补跑 / 停用 API 和告警 |
| 检查更新 | 已接入只读配置 | `APP_ANDROID_*` 环境变量、`/api/app/update`、`/admin-api/v1/app-update/android` | `app_releases` 表、发布 / 停更 / 回滚 API |
| 订单 / 订购 | 不能当正式功能接 | 当前 `orders` 仅开发期记录 | 正式订单、支付回调、退款、对账和幂等表 |
| 礼品卡 | 已接入首版 | `gift_card_batches`、`gift_cards`、`gift_card_redemption_attempts`、`/api/gift-cards/redeem`、`/admin-api/v1/gift-cards/*` | 批量发放、发放对象管理、更细风控；历史完整卡码不导出 |
| 产品洞察 | 未完整接入 | 反馈、App 日志、聊天归档可作为来源 | 脱敏聚合任务和洞察报表表 |

注意：当前迁移仍按 SQL 文件幂等执行，没有独立 `schema_migrations` 版本表；后续新增后台表必须继续保持幂等，避免重复 `ALTER` 造成启动风险。

### 第一版不要做的事

- 不做后台直接读写数据库的浏览器页面。
- 不把内部共享 secret 写进前端。
- 不开放大范围删除用户、导出全量数据、批量补权益、批量发礼品卡。
- 不在支付未接入前伪造真实订单后台。
- 不把聊天全文、图片 URL、手机号、token、模型 Key 铺到后台列表。
- 不把官网和管理后台做成同一个公开页面。

最小页面：

- 登录页和后台首页健康状态。
- 帮助与反馈：会话列表、详情、回复、未读 / 未处理队列、处理状态。
- 用户详情：按手机号 / user_id 查询会员、额度、扣次流水、反馈、订单、后续礼品卡和最近 App 自动日志。
- 用户来源与地区：展示注册 / 最近活跃的 masked IP、后端推断地区、用户自选地区、地区可信度、App 版本和设备型号；不在后台列表明文铺手机号或完整 IP。
- 用户真实反馈 / 产品洞察：从帮助与反馈、App 自动日志和聊天归档中做脱敏聚合，提取高频 bug、登录 / 上传 / 历史恢复卡点、用户不满意或反复追问的问诊场景、常见作物 / 病虫害、模型答偏线索和可改 UI / 提示词 / 后端规则的证据。第一版只展示聚合统计、标签、短脱敏片段和处理状态，不直接把手机号、token、密钥、图片原图或完整聊天原文铺到后台。
- App 自动日志：按时间、等级、事件名、用户、App 版本、系统版本、设备型号筛选，先接 `GET /internal/app/logs` 和 SLS 最小日志集，后续再补 SLS 告警 / 仪表盘。
- 检查更新：当前版本、APK 链接、SHA-256、文件大小、是否启用、停更入口。
- 今日农情：当天状态、失败原因、来源链接、手动补跑、停用当天卡片。
- 审计日志：先接 `GET /internal/admin/audit-logs` 查看内部操作记录，后续接后台账号和角色权限。

最小表或结构：

- `admin_users`：后台账号。
- `admin_roles` 或等价角色字段：只读、客服、内容运营、发布运营、财务 / 订单、管理员。
- `admin_sessions` 或等价登录态。
- `admin_audit_logs`：已落地最小版本，记录 actor、动作、目标类型 / ID、目标用户、成功 / 失败、状态码、脱敏 IP、UA 和时间；当前不保存正文、图片 URL、手机号、token 或密钥。后续正式后台账号接入后再补角色、request_id、原因、变更前后等字段或等价扩展。
- 帮助与反馈可先复用 `support_messages`；当前已补最小会话列表、详情和回复内部接口，后续还要补未处理状态、处理人、关闭状态、搜索能力，或新增轻量会话表。
- 用户真实反馈 / 产品洞察建议新增独立聚合表或日报表，例如 `product_insight_reports`、`product_insight_items`、`product_insight_sources`；source 只保存来源类型、脱敏引用、时间、标签和必要短摘，不保存原始手机号、token、密钥、图片内容或完整聊天正文。若用户删除历史或后续做账号注销，必须同步设计洞察来源引用的清理 / 去标识化口径。
- 检查更新建议补 `app_releases`，不要长期只靠环境变量手改。
- 今日农情可先复用 `daily_agri_cards`，后台只做状态查看、补跑、停用和审计。

## P2：支付和礼品卡接入后

- 会员订单查询、支付回调状态、退款 / 对账状态。
- 人工补偿权益，但必须二次确认并写审计。
- 礼品卡生成、批次、发放、兑换、作废、导出和使用记录。
- 礼品卡卡号只存哈希或受控脱敏，不在后台列表里明文铺开。
- 连续兑换失败、撞库、已兑换、已过期和权益发放失败要能查。

## P3：正式运营后台

- 后台账号权限分层，关键动作二次确认。
- 发布 / 停更 / 回滚入口。
- 日报、告警中心和成本面板。
- 用户反馈洞察日报 / 周报：定期聚合真实反馈，区分“用户主动反馈”和“聊天行为推断”，避免把单个问诊误当普遍需求；Codex 后续可以基于脱敏报表给出产品、提示词和排障建议，但不应直接定期读取生产库原始聊天全文。
- 用户数据导出、删除、注销、隐私合规流程。
- 生产 / 测试环境隔离。

## 安全与审计底线

- 所有后台接口必须服务端鉴权和授权，不能只靠前端隐藏按钮。
- 现有内部接口仍使用共享密钥保护；可选 `X-Admin-Actor` / `X-Support-Admin-Actor` 只用于审计标记，不等于身份认证。
- 高风险操作必须写审计：补权益、发礼品卡、作废礼品卡、停更新、手动补跑今日农情、导出 / 删除用户数据、修改后台权限。
- 审计记录不能只存在浏览器本地；必须落服务端数据库或日志系统。
- 后台账号、密钥和数据库密码不能写进仓库。
- 后台上线后继续接 SLS / 等价日志闭环，至少能查登录失败、权限拒绝、关键操作、接口 5xx 和数据库错误；当前 Go 请求日志和 Nginx error 已接 SLS，仍需补告警和仪表盘。

参考：

- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)：后台接口必须做服务端访问控制。
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)：高风险操作、认证和授权失败应纳入可审计日志。

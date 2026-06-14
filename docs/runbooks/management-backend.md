# 统一管理后台 Runbook

最后更新：2026-06-15

## 目的

记录“农技千查”统一管理后台当前实现、上线方式、第一版页面能力和仍需补齐的安全边界。

当前第一版后台已进入代码并已部署到生产：`admin` 是 Vite 静态前端，`server-go` 暴露 `/admin-api/v1/*` 管理 API，并新增后台账号 / session / CSRF、角色校验、账号安全改密和审计。生产入口为 `https://admin.nongjiqiancha.cn/`，Nginx 静态托管后台前端并同域反代 `/admin-api/` 到当前 active Go slot；一次性 bootstrap 环境变量已用于初始化 owner 账号，随后已从 ECS 环境文件清理。

详细页面结构、筛选项、指标和版面建议见 [admin-dashboard-design.md](D:/wuhao/docs/runbooks/admin-dashboard-design.md)。

## 当前真相

- 管理后台前端目录：`admin`。本地开发：`cd admin && npm install && npm run dev -- --host 127.0.0.1 --port 5174`。生产构建：`npm run build`。
- 生产部署脚本：[deploy-ecs-admin.ps1](D:/wuhao/scripts/deploy-ecs-admin.ps1)。脚本会构建 `admin/dist`、同步 `admin` A 记录、上传静态包、配置 Nginx、签发 / 复用 Let's Encrypt HTTPS 证书，并验证首页和未登录 API 状态。
- 生产入口：`https://admin.nongjiqiancha.cn/`。HTTP 80 只用于 ACME challenge 和 301 跳转；HTTPS 下 `/admin-api/` 由 Nginx 反代到当前 active Go slot。
- 登录后只读烟测：[check-admin-authenticated-smoke.ps1](D:/wuhao/scripts/check-admin-authenticated-smoke.ps1)。该脚本不会把后台密码写入仓库或输出到日志；运行前在当前 PowerShell 临时设置 `NONGJI_ADMIN_USERNAME` / `NONGJI_ADMIN_PASSWORD`，或使用兼容别名 `ADMIN_SMOKE_USERNAME` / `ADMIN_SMOKE_PASSWORD`，然后执行 `.\scripts\check-admin-authenticated-smoke.ps1 -RequireOwner`。它会登录后台、访问总览 / 监控 / 洞察 / 用户 / 会员 / 订单 / 礼品卡 / 帮助反馈 / App 日志 / 审计 / 今日农情 / 检查更新 / 注销申请等只读 API，最后退出；这比只看未登录 `/auth/me=401` 更能证明后台登录后核心页面可用。
- 公网黑盒只读烟测：[check-public-blackbox.ps1](D:/wuhao/scripts/check-public-blackbox.ps1)。该脚本不登录、不带后台密码、不读密钥，从公网域名请求 API healthz、官网、www、后台首页、后台未登录 `/auth/me=401` 和 HTTP->HTTPS 跳转，验证外部用户视角的入口可达性；它不能替代登录后 owner smoke，也不是自动告警。
- 管理后台 API：`/admin-api/v1/*`，由 `server-go` 提供，不单独起第二套后端。
- 后台登录：`POST /admin-api/v1/auth/login`，成功后写 HttpOnly session cookie 和 CSRF cookie，前端请求带 `X-Admin-CSRF`。登录入口有两层防刷：外层保留 IP 级内部接口保护，内层按“用户名 hash + IP hash”默认 `10/10min` 限制失败 / 尝试请求，可用 `ADMIN_LOGIN_RATE_LIMIT_*` 调整；限流 key 不保存明文用户名或 IP。
- 后台账号安全：`POST /admin-api/v1/auth/change-password` 支持登录后自助修改当前后台密码；后端验证当前密码、限制新密码最短 8 字符、改密后清 `must_change_password` 并吊销同账号其它后台会话。若账号被标记为必须改密，除 `/auth/me`、`/auth/logout` 和 `/auth/change-password` 外，其它后台 API 会返回 `password_change_required`。
- 后台账号：服务启动时可用 `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` 初始化；密码会以 PBKDF2-SHA256 hash 存入 `admin_users`，明文不得写入仓库、文档或前端。
- 后台角色：首版支持 `owner`、`ops_readonly`、`support`、`content_ops`、`release_ops`、`finance_ops`、`auditor`；服务端校验权限，不能靠前端隐藏按钮。前端侧栏和监控快捷入口会按同一角色矩阵隐藏无权页面，减少误点和 403，但这只是体验收敛，不是安全边界。
- 后台审计：登录、登出、查询用户、客服回复、日志查询、审计日志查询、今日农情、检查更新、检查更新校验失败、礼品卡生成 / 查询 / 作废 / 用户兑换等会写审计记录。
- 后台写操作体验：今日农情补跑、检查更新发布 / 停更、礼品卡作废、帮助反馈状态更新和客服回复等入口会显示按钮忙碌态、阻止重复点击，并在失败时弹出明确错误；检查更新发布 / 停更和客服回复有二次确认；长账号ID、完整卡码和错误字段会自动换行，避免窄屏或宽表撑破页面。
- 后台敏感资产可见性：owner 默认拥有完整手机号和礼品卡完整卡码查看 / 复制权限；`support`、`finance_ops` 可查看完整手机号用于回访，`finance_ops` 可查看礼品卡完整卡码用于发卡 / 追溯。其他只读 / 审计角色只看脱敏信息；礼品卡完整码在后端列表 / 用户详情查询阶段也只对 owner / finance_ops 读取并解密，非授权角色不解密完整卡码。前端同口径展示保护只是体验兜底，真正权限仍以后端为准。
- 后台展示安全：App 日志详情会在前端再次脱敏敏感字段；客服图片只展示后台同源、无 query / hash、单层 `/uploads/support/*.jpg` 图片；今日农情来源只允许安全 HTTPS 链接，避免后台页面被日志或异常数据带偏。
- Android 没有后台入口，也没有调用任何 `/internal/*` 或 `/admin-api/*` 接口。

当前已落地的后台页面 / API：

- 总览：`GET /admin-api/v1/overview`，展示健康状态、今日问诊、App 错误、未回复反馈和今日农情状态。
- 监控面板：`GET /admin-api/v1/monitoring`，聚合服务健康、今日 / 24h / 7d 使用情况、App 自动日志错误、登录前认证失败、闪退补报、待回复反馈、反馈 open / replied / closed 队列、今日农情、礼品卡兑换异常、后台操作失败、最近 30 天问诊地区分布和 App 错误 Top；响应额外返回 `action_items`、`launch_readiness`、`capabilities`、`model_usage_policy`、`user_regions`、`auth_logs` 和 `app_update_logs`。其中 `model_usage_policy` 会列出当前后端真实模型调用口径：主聊天 `qwen3.5-plus + search_strategy=turbo`、记忆文档摘要 `qwen-plus + OpenAI兼容非流式 + enable_thinking=false`、今日农情 `qwen3.5-plus + OpenAI兼容非流式 + search_strategy=turbo + forced_search=true + enable_thinking=false`。当前不保留轻量摘要候选、今日农情其它接口候选或环境变量模型切换入口；面板会提示 `qwen-turbo` 是模型名而不是搜索策略，且不在当前生产链路中，`search_strategy=turbo` 才是联网搜索策略。`launch_readiness` 里的“模型问诊”不再只看 healthz；只有最近 24 小时同时有真实文字问诊和图片问诊记录才标 ready，只有文字问诊或没有真实问诊记录时会保持 attention，避免把“模型 Key 健康”误读成“真机主聊天 / 图片问诊已完整验收”。`user_regions` 会按账号最近一次已识别地区拆出“注册用户地区”和“当前会员地区”两块，让非运维也能大概看出用户主要来自哪里；这不是精确注册地，也不是 100% 覆盖，只是基于账号最近地区做运营近似盘子。`auth_logs` 聚合最近 24 小时 `auth.*`、`auth.app_crash` 和 `app.crash`，用于快速看短信验证码登录、历史旧包融合事件和登录 / 运行期闪退，并额外拆出 `latest_crash_at`、`env_blocked`、`env_warnings`、`login_network_failures` 以及 `funnel` 登录阶段漏斗；`latest_crash_at` 只来自 `client_app_logs.created_at`，不读取崩溃 attrs、正文、手机号、图片 URL、token 或完整堆栈，用于让监控页直接显示“最近闪退：时间”或“24h 无新闪退”。漏斗按短信验证码、账号会话、登录 / 运行闪退和历史旧包融合认证分组，只统计事件名、等级和次数，不读取日志 attrs。登录排障按钮支持一键打开全部 `auth.*` 日志，也会按精确事件名拆开短信发送、短信登录校验、登录成功、请求网络失败、登录闪退、运行闪退和历史旧包融合记录；检查更新排障按钮支持一键打开全部 `app_update.*` 日志，也会按精确事件名拆开检查开始、有新版本、手动无更新、检查失败、需要安装权限、下载开始、下载失败、安装页失败和已拉起安装页，避免一个笼统按钮漏掉真实失败阶段。前端已收成“当前结论 / 就绪-程序需处理-人工确认-上架阻塞 / 上线人工确认项 / 登录与账号ID / 礼品卡与权益 / 客服反馈 / App质量”决策卡、模型调用口径、上线前真机回归清单、正式上架检查、快捷入口、关键队列、登录排障、登录阶段漏斗、检查更新排障和明细表，让非运维也能先看出当前哪里正常、哪里需要处理、哪里挡住正式上架；上线人工确认项会在首屏列出阻塞 / 需处理事项、负责人和入口。真机回归清单会把短信验证码登录、主聊天文字问诊、图片问诊 / 弱网发送、礼品卡兑换、今日农情、检查更新和帮助反馈串成可点击入口，用现有监控数据提示“待真机 / 有登录 / 有记录 / 有兑换 / 有检查 / 看日志 / 先生成卡”；其中登录和检查更新的日志入口会直接带 `event_prefix=auth.` 或 `event_prefix=app_update.`，但不替代真实 Android 回归；短信验证码登录只要生产 HTTPS、Redis 和短信服务可用，WiFi 或代理环境也应可用。监控窗口里的 `active_sessions` 表示当前有效 App session 总量，`recent_auth_sessions` 表示该窗口内新创建 / 登录 session；礼品卡队列同时看批次数、总卡数、可用卡、已兑换和 24h 失败尝试，生产库没有可兑换卡时会直接提示先生成礼品卡。上架检查会把支付、备案、AccessKey 轮换、第一封 SLS 告警邮件送达确认和真机登录回归等未闭环事项标成“程序需处理 / 人工确认 / 上架阻塞”，不伪装成已完成；SLS / 云监控配置以最近严格巡检脚本和仓库记录为准，后台页面不实时读取阿里云告警规则，首封 SLS 告警邮件仍需真实或测试触发确认送达。
- 2026-06-15 起，`launch_readiness` 的每个条目可带 `manual=true`，前端“上线人工确认项”只展示显式人工确认且未 ready 的条目，不再把服务健康、App 错误或登录异常误归到人工确认区。当前显式人工项包括 App 备案、App 公安备案、AccessKey 轮换、最终真机回归、短信套餐余额、最终 release 物料和 SLS 首封邮件确认。支付接入在购买入口关闭、开发期订单端点关闭时保持 `attention`，不作为免费版、礼品卡内测或无内购正式上架的红色阻塞；开放真实收费前仍必须完成支付申请、验签、回调、对账、退款和权益发放闭环。
- 同日监控页首屏新增“程序需处理项”：只展示 `launch_readiness` 中 `manual!=true` 且未 ready 的条目，优先把 blocked 红色项排在前面，并保留负责人、状态和入口。它用于承接代码、配置、部署或后台操作可推进的问题；“上线人工确认项”继续只放备案、AccessKey 轮换、短信余额、SLS 首封邮件、最终 release 物料和真机最终确认等需要人确认的事项。正式上架检查明细卡也会额外显示“程序处理 / 人工确认”小标签，避免人工确认项在明细区被普通“需处理”状态误读。`scripts/check-admin-surface.mjs` 会检查这个区域存在，并拦截人工项被混入程序处理区或明细卡分类标签被删。
- 性能边界：总览、监控面板和产品洞察这三类聚合接口在服务端共享 4 秒查询超时；`033_monitoring_query_indexes.sql` 已给 App 日志等级 / 事件时间窗、有效 session 统计和待回复反馈队列补索引，`034_admin_performance_indexes.sql` 已给账号、会员、额度和加油包后台统计补索引，`035_admin_order_gift_indexes.sql` 已给订单按账号查询和礼品卡失败原因聚合补索引。后台慢查询会按内部错误收口和记录日志，不应长时间占住请求或影响主聊天 SSE。
- 检查更新验收口径：`app_update_logs` 和物料齐全只代表“可测 / 有阶段信号”，不能替代旧包真机覆盖安装；`launch_readiness` 的“安装包更新”在版本号、HTTPS APK、SHA-256 和文件大小都齐时仍保持 `attention`，完成旧包“检查更新 -> 下载 -> 校验 -> 系统安装页 -> 覆盖安装成功”前不要当成正式验收。
- 用户管理：`GET /admin-api/v1/users`、`GET /admin-api/v1/users/detail`，按账号ID（底层字段仍叫 `user_id`）/ 手机号查询，完整手机号查询会在服务端按 `phone_hash` 精确匹配，不记录明文查询值；页面展示会员、额度、加油包、升级补偿、订单、礼品卡、最近问诊、App 日志和反馈；`owner`、`support`、`finance_ops` 可查看和复制加密保存的完整手机号，用于回访，其他只读巡检角色只看脱敏号。
- 会员额度：除用户级只读展示当前档位、到期时间、每日额度、`quota_ledger` 扣次流水、`topup_packs` 加油包包明细、`upgrade_credits` 升级补偿、订单记录和礼品卡兑换记录外，现已补 `GET /admin-api/v1/entitlements/summary` 全局盘子，页面可直接看注册用户、当前会员总数、Free / Plus / Pro 分布、7 / 30 天内到期、今日基础额度用满、有加油包余额和有升级补偿人数，不再只有“按账号ID查单人权益”。
- 订单：`GET /admin-api/v1/orders`，授权角色可按账号ID筛选或留空查看最近开发期订单 / 会员变更记录；页面只做只读核查和粗略统计，不提供补发、退款、对账或手动改权益。
- 礼品卡：`GET/POST /admin-api/v1/gift-cards/batches`、`GET /admin-api/v1/gift-cards/summary`、`GET /admin-api/v1/gift-cards/cards`、`POST /admin-api/v1/gift-cards/void`、`GET /admin-api/v1/gift-cards/attempts`；可创建 Plus / Pro 礼品卡批次、查询全局汇总，owner / finance_ops 可直接查看并复制新生成礼品卡完整卡码，按批次 / 状态 / 账号ID / 卡码尾号追溯卡状态，按账号ID / 尾号 / 成功状态 / 失败原因查询兑换尝试，并可作废未兑换卡。完整卡码使用 `APP_SECRET` 派生密钥加密保存，兑换仍用 hash 校验；后台非授权角色查询礼品卡列表或用户详情时不读取 / 不解密完整卡码；旧卡若没有加密字段，只能显示掩码 / 尾号。
- 用户侧礼品卡兑换：`POST /api/gift-cards/redeem`，鉴权后事务内校验卡状态并发会员权益，记录成功 / 失败尝试、地区和脱敏 IP；Android 设置页“礼品卡”已经接真实兑换接口。
- 帮助与反馈：`GET /admin-api/v1/support/conversations`、`GET /admin-api/v1/support/messages`、`POST /admin-api/v1/support/messages`、`POST /admin-api/v1/support/conversations/status`；支持待回复 / 已回复 / 已关闭队列、账号ID / 手机号 / 最近消息搜索、后台回复、关闭和重开，完整手机号查询同样按 `phone_hash` 精确匹配。用户侧发送消息和系统自动回复走同一条 MySQL 命名锁 + 事务路径，避免同一用户并发连发时重复插入自动回复。授权客服角色可在会话详情直接查看和复制完整手机号，便于电话回访；客服回复前端二次确认，后端回复正文会拒绝手机号、礼品卡完整码、token、密钥等敏感全文；回复图片附件只能使用同源 support 图片校验，状态备注也会拒绝敏感全文，审计里仍不写正文。
- 注销申请：`GET /admin-api/v1/account-deletion-requests`、`POST /admin-api/v1/account-deletion-requests/status`；用户侧 `POST /api/account/deletion-requests` 创建申请后会退出当前设备，后台可按待处理 / 处理中 / 已处理 / 驳回 / 取消推进状态。这里的已处理只表示线下核验和处理流程已收口，不代表系统已经自动物理删除或匿名化全部账号数据；会员、订单、礼品卡、反馈、日志和法定留存范围仍需按合规规则处理。
- App 自动日志：`GET /admin-api/v1/app-logs`，继承自动日志脱敏规则，可按账号ID、精确事件名、事件前缀 `event_prefix`、平台、包类型 `build_type`、App 版本号 / 版本名、Android 系统版本、设备型号、等级和时间范围筛选；精确 `event` 优先于前缀筛选，不展示聊天正文、图片 URL、手机号、token、APK URL 或 SHA-256 原文。
- 后台审计：`GET /admin-api/v1/audit-logs`。
- 今日农情：`GET /admin-api/v1/today-agri/cards`、`POST /admin-api/v1/today-agri/generate`。
- 检查更新：`GET /admin-api/v1/app-update/android`、`POST /admin-api/v1/app-update/android`、`GET /admin-api/v1/app-update/android/events`；后台可直接维护 Android 版本号、HTTPS APK、SHA-256、文件大小和停更状态，每次保存会追加 `app_release_events` 发布历史，对外 `/api/app/update` 优先读取数据库表 `app_release_configs`，无记录时才回退环境变量。当前默认只做普通更新，`force_update` 兼容字段默认不生效，除非未来显式配置 `APP_UPDATE_ALLOW_FORCE_UPDATE=true`。
- 账号安全：`POST /admin-api/v1/auth/change-password`；所有后台角色都可进入“账号安全”页修改自己的密码，强制改密账号登录后会先停留在该页。
- 产品洞察：`GET /admin-api/v1/insights`，首版只读展示今日 / 24h / 7d / 30d 用户增长、登录 session、问诊、图片问诊、App 异常、登录排障、反馈、礼品卡和今日农情失败趋势；同时聚合反馈主题固定关键词命中、App 事件分类、Top App 事件和礼品卡失败原因。该接口只返回计数、比例、事件名和固定分类，不返回聊天全文、反馈正文、图片 URL、手机号、token、模型 Key 或礼品卡完整码。

仍保留的内部共享密钥接口：

- 帮助与反馈、App 自动日志、内部审计、今日农情生成仍保留 `/internal/*` 入口作为脚本 / 运维兼容入口，但浏览器后台不应持有 `SUPPORT_ADMIN_SECRET` 或 `DAILY_AGRI_JOB_SECRET`。

## 当前不要误解

- 买服务器不等于自动有管理后台。
- `SUPPORT_ADMIN_SECRET`、`DAILY_AGRI_JOB_SECRET` 仍只是共享密钥，不能给浏览器前端使用；正式后台浏览器入口必须走 `admin_users` / `admin_sessions` / CSRF。
- `/admin-api/v1/monitoring`、`/admin-api/v1/app-logs` 和 `/internal/app/logs` 是给运维和后台面板用的只读 / 聚合入口，不是完整 SLS 告警中心。
- `/api/app/update` 当前已经接上后台可写发布配置和 `app_release_events` 发布历史，但它仍不是完整应用商店 / 推送中心；正式发布仍要记录 APK 链接、SHA-256、大小、签名指纹、操作人和时间，并做真机覆盖安装回归。
- 礼品卡后端、后台和 Android 兑换入口已接入首版；Android 只在 `/api/gift-cards/redeem` 返回成功后展示“兑换成功”，没有后端成功结果时不能弹真实成功。后台现在可以页面内查看和复制新生成礼品卡完整卡码，但不能把完整卡码写进备注、审计、日志、文档或批量导出文件。
- 开发期会员接口不是正式支付回调，生产必须保持关闭。
- Android 客户端不能承载后台逻辑；客服回复、补权益、发礼品卡、停更新和删除用户数据都必须在服务端或后台完成。

## P0：服务器落地前规划（历史阶段）

- 不提前硬做完整后台，避免账号体系、权限模型、数据库结构和真实运维入口未定时返工。
- 继续用 runbook、内部接口和只读脚本规划兜底。
- 继续按功能巡检，把当前真相和后续必补项写入 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。

该阶段已经结束：ECS / RDS / Redis / OSS / DNS、内部接口和第一版网页后台代码已经落地。P1 后台生产入口也已部署到 `admin.nongjiqiancha.cn`，并完成管理员 bootstrap、清理 bootstrap 环境变量、HTTPS、首页、登录和总览 API 验收；SLS 已有 5 条 AlertHub 最小告警，`scripts/check-sls-alert-readiness.ps1` 可只读巡检规则是否启用、是否绑定行动策略和仪表盘，后续重点是补第一封告警邮件送达确认、数据库只读脚本和更细的运营动作。

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
- 账号初始化：后台初始账号只能通过一次性环境变量、Cloud Assistant 脚本或本机安全脚本写入数据库 hash；账号名和明文密码不能写进仓库、文档、前端代码或部署脚本。初始化成功后必须禁用 bootstrap；当前已有自助改密和 `must_change_password` 强制改密硬兜底，后续多账号运营前仍要补受控重置、账号禁用和角色管理流程。
- 生产初始化状态：2026-06-07 已通过一次性 bootstrap 创建 owner 账号，随后已从 `/etc/nongjiqiancha/server.env` 删除 `ADMIN_BOOTSTRAP_*` 并重启 active slot；`scripts/check-ecs-readiness.ps1` 会把残留 `ADMIN_BOOTSTRAP_*` 视为生产 readiness 失败。后台账号可在“账号安全”页自助改密码；后续若忘记密码，应通过临时 bootstrap 或受控运维脚本重置，仍不得在仓库或文档中记录明文密码。

### 第一版页面建议

第一版按“可运营、可排障、少误操作”做，不追求花哨。推荐左侧导航：

- 总览：服务健康、今日请求量、错误量、登录 / 短信状态、模型 Key 池健康、Redis / RDS / OSS / SLS 状态、最近 5xx / 慢请求。
- 用户查询：按 `user_id`、完整手机号精确匹配 / 脱敏手机号线索、最近活跃时间、App 版本、设备、地区可信度、会员状态、额度、加油包、最近反馈、最近 App 自动日志。
- 用户地区 / 来源：按注册、最近活跃、问诊、图片问诊、会员成交、加油包购买和帮助反馈聚合省市分布；优先使用 GPS 反查地区，IP 粗定位只作为低可信参考，不保存经纬度或轨迹。
- 会员与额度：只读展示当前档位、到期时间、每日额度、今日已用、加油包余额、升级补偿和 `quota_ledger`。人工补偿先不开放，或只做 owner 二次确认。
- 订单 / 订购：支付未接入前已接开发期 `orders` 表只读核查，可按账号ID或最近记录查看权益变更来源；支付接入后再接正式订单、回调、对账、退款和异常补偿。
- 礼品卡：首版已接入批次、生成、兑换、卡状态、失败尝试查询、失败原因聚合和未兑换卡作废；批量发放、发放对象管理和更细风控后续再补。
- 帮助与反馈：会话列表、未回复队列、详情、后台回复、处理状态、关闭 / 重开和搜索已接入；后续补正式坐席分配、标签、站外通知、客服绩效和消息保存 / 删除规则。
- App 自动日志：按时间、用户、事件名、level、App 版本、系统版本、设备筛选；接 `GET /internal/app/logs`，后续再并入 SLS 摘要。
- 今日农情：当天卡片状态、来源、生成时间、失败原因、手动补跑、停用当天卡片，所有动作审计。
- 检查更新：当前发布版本、APK URL、SHA-256、文件大小、是否启用、停更开关、发布历史；不长期手改环境变量，APK 上传和完整回滚入口后续再补。
- 后台操作审计：登录、查询、回复、改配置、补权益、礼品卡操作、发版、今日农情补跑等操作记录，支持按 actor / action / target_user / 时间筛选。
- 产品洞察：首版 `GET /admin-api/v1/insights` 已展示脱敏聚合趋势、反馈主题命中、App 事件分类、Top App 事件和礼品卡失败原因，不直接铺完整聊天正文、反馈正文、图片 URL、手机号、token、模型 Key 或礼品卡完整码。
- 用户聊天 / 反馈洞察：后续再由后台任务定期扫描最近 N 天脱敏归档和反馈，抽取问题标签、影响人数、严重度、代表短摘和建议改动，写入产品洞察日报 / 报表；Codex 后续优先读洞察报表，不直接长期读取生产库完整聊天全文。

### 当前后端接入矩阵

| 模块 | 当前能否直接接 | 当前真源 | 第一版要补 |
|---|---|---|---|
| 服务健康 / 监控面板 | 已接入首版 | `/healthz`、管理 API、业务表、App 自动日志、后台审计、`auth_logs` 登录阶段漏斗、`app_update_logs`、SLS 5 条 AlertHub 最小告警、SLS 告警只读巡检脚本 | 第一封告警邮件送达确认、Nginx access 聚合、更细登录趋势 |
| 帮助与反馈 | 已接入首版 | `support_messages`、`support_conversations`、`/internal/support/*`、`/admin-api/v1/support/*`；客服图片附件走 OSS `support/` 30 天生命周期 | 正式坐席分配、标签、站外通知、客服绩效、聊天记录保存 / 删除规则 |
| 注销申请 | 已接入申请队列 | `account_deletion_requests`、`/api/account/deletion-requests`、`/admin-api/v1/account-deletion-requests*` | 物理删除 / 匿名化规则、法定留存、处理责任和批量清理脚本 |
| App 自动日志 | 已接入首版 | `client_app_logs`、`/internal/app/logs`、`/admin-api/v1/app-logs`、监控页登录排障卡、检查更新排障卡 | 更细的版本 / 设备 / 地区聚合和告警 |
| 后台审计 | 可直接接 | `admin_audit_logs`、`/internal/admin/audit-logs` | 后台账号 actor、角色、请求 ID |
| 后台账号安全 | 已接入首版 | `admin_users`、`admin_sessions`、`/admin-api/v1/auth/change-password` | 受控重置、账号禁用、角色管理 |
| 用户查询 | 已接入首版 | `app_accounts`、`auth_sessions`、`session_ab`、`session_round_archive`、`/admin-api/v1/users*` | session 管理、更多筛选和导出审批 |
| 会员 / 额度 | 已接入用户级只读 | `user_entitlement`、`daily_usage`、`quota_ledger`、`topup_packs`、`upgrade_credits` | 全局统计、人工补偿二次确认和审计 |
| 今日农情 | 已接入状态查看和补跑 | `daily_agri_cards`、内部生成接口、`/admin-api/v1/today-agri/cards`、`/admin-api/v1/today-agri/generate`、`nongji-daily-agri-failed` AlertHub 告警 | 停用 API、首封告警邮件送达确认和发布记录 |
| 检查更新 | 已接入发布 / 停更配置、发布历史和排障日志 | `app_release_configs`、`app_release_events`、`/api/app/update`、`/admin-api/v1/app-update/android*`、`app_update.*` 自动日志 | APK 上传、完整回滚入口和更细二次确认 |
| 订单 / 订购 | 已接只读核查，不能当正式支付功能 | 当前 `orders` 仅开发期记录，`/admin-api/v1/orders` 只读查询 | 正式订单、支付回调、退款、对账和幂等表 |
| 礼品卡 | 已接入首版 | `gift_card_batches`、`gift_cards`、`gift_card_redemption_attempts`、`/api/gift-cards/redeem`、`/admin-api/v1/gift-cards/*` | 批量发放、发放对象管理、更细风控；完整卡码批量导出暂不开放 |
| 产品洞察 | 已接入首版脱敏聚合 | `/admin-api/v1/insights`、`support_messages`、`client_app_logs`、`session_round_archive`、`gift_card_redemption_attempts` | 洞察日报、人工标签、代表短摘、处理状态和独立报表表 |

注意：当前迁移仍按 SQL 文件幂等执行，没有独立 `schema_migrations` 版本表；后续新增后台表必须继续保持幂等，避免重复 `ALTER` 造成启动风险。2026-06-13 已把早期 `004/005` 里 `expire_at` 改 nullable 的 `ALTER` 包成 `information_schema` 条件执行，降低每次启动重跑旧 SQL 的风险，但这不等于已经有正式迁移版本表。

### 第一版不要做的事

- 不做后台直接读写数据库的浏览器页面。
- 不把内部共享 secret 写进前端。
- 不开放大范围删除用户、导出全量数据、批量补权益、批量发礼品卡。
- 不在支付未接入前伪造真实支付订单后台；现有订单页只能只读核查开发期记录。
- 不把聊天全文、图片 URL、token、模型 Key 铺到后台列表；完整手机号只对授权角色在用户 / 反馈详情和必要列表字段中展示，不能写入备注、回复、审计 detail、日志、文档或导出文件。
- 不把官网和管理后台做成同一个公开页面。

最小页面：

- 登录页和后台首页健康状态。
- 帮助与反馈：会话列表、详情、回复、未读 / 未处理队列、处理状态。
- 用户详情：按手机号 / user_id 查询会员、额度、扣次流水、反馈、订单、后续礼品卡和最近 App 自动日志；授权角色可查看完整手机号并复制，方便回访。
- 用户来源与地区：展示注册 / 最近活跃的 masked IP、后端推断地区、用户自选地区、地区可信度、App 版本和设备型号；不展示完整 IP，手机号展示受后台角色权限控制。
- 用户真实反馈 / 产品洞察：首版已从帮助与反馈、App 自动日志、问诊归档计数和礼品卡兑换尝试中做脱敏聚合，展示趋势、固定分类和 Top 事件；下一步再补高频 bug、登录 / 上传 / 历史恢复卡点、用户不满意或反复追问的问诊场景、常见作物 / 病虫害、模型答偏线索和可改 UI / 提示词 / 后端规则的证据。页面不得直接把手机号、token、密钥、图片原图、图片 URL、反馈正文或完整聊天原文铺到后台。
- App 自动日志：按时间、等级、事件名、用户、App 版本、系统版本、设备型号筛选，先接 `GET /internal/app/logs`、SLS 最小日志集和 5 条 AlertHub 最小告警，后续再补更细趋势和告警邮件送达确认。
- 检查更新：当前版本、APK 链接、SHA-256、文件大小、是否启用、停更入口。
- 今日农情：当天状态、失败原因、内部来源追溯、手动补跑、停用当天卡片。
- 审计日志：先接 `GET /internal/admin/audit-logs` 查看内部操作记录，后续接后台账号和角色权限。

最小表或结构：

- `admin_users`：后台账号。
- `admin_roles` 或等价角色字段：只读、客服、内容运营、发布运营、财务 / 订单、管理员。
- `admin_sessions` 或等价登录态。
- `admin_audit_logs`：已落地最小版本，记录 actor、动作、目标类型 / ID、目标用户、成功 / 失败、状态码、脱敏 IP、UA 和时间；当前不保存正文、图片 URL、手机号、token 或密钥。后续正式后台账号接入后再补角色、request_id、原因、变更前后等字段或等价扩展。
- 帮助与反馈复用 `support_messages` 并新增 `support_conversations` 轻量会话状态表；当前已补会话列表、详情、回复、状态筛选、搜索、关闭和重开。客服图片附件走 OSS `support/` 30 天生命周期，但客服聊天记录正文、发送人、时间和已读状态仍保存在 MySQL，不随图片自动过期；后续补坐席分配、标签、站外通知、客服绩效和聊天记录保存 / 删除规则。
- 用户真实反馈 / 产品洞察建议新增独立聚合表或日报表，例如 `product_insight_reports`、`product_insight_items`、`product_insight_sources`；source 只保存来源类型、脱敏引用、时间、标签和必要短摘，不保存原始手机号、token、密钥、图片内容或完整聊天正文。若用户删除历史或后续做账号注销，必须同步设计洞察来源引用的清理 / 去标识化口径。
- 检查更新当前主链为 `app_release_configs` + `app_release_events`，环境变量只作为无数据库记录时的兜底；后续重点是 APK 上传、完整回滚入口和旧包真机覆盖安装验收，不再新增一套并行 `app_releases` 主表。
- 今日农情可先复用 `daily_agri_cards`，后台只做状态查看、补跑、停用和审计。

## P2：支付和礼品卡接入后

- 会员订单查询、支付回调状态、退款 / 对账状态。
- 人工补偿权益，但必须二次确认并写审计。
- 礼品卡生成、批次、发放、兑换、作废和使用记录。
- 礼品卡完整卡码可在后台页面内查看和复制，服务端必须加密保存；批量导出暂不开放，后续若开放必须二次确认和审计。
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
- 后台上线后继续接 SLS / 等价日志闭环，至少能查登录失败、权限拒绝、关键操作、接口 5xx 和数据库错误；当前 Go 请求日志和 Nginx error 已接 SLS，并已有 5 条 AlertHub 最小告警，仍需确认首封邮件送达并补更细趋势。

参考：

- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)：后台接口必须做服务端访问控制。
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)：高风险操作、认证和授权失败应纳入可审计日志。

# 统一管理后台 Runbook

最后更新：2026-06-22

## 目的

记录“农技千查”统一管理后台当前实现、上线方式、第一版页面能力和仍需补齐的安全边界。

当前第一版后台已进入代码并已部署到生产：`admin` 是 Vite 静态前端，`server-go` 暴露 `/admin-api/v1/*` 管理 API，并新增后台账号 / session / CSRF、最小服务端授权、账号安全改密和审计。生产入口为 `https://admin.nongjiqiancha.cn/`，Nginx 静态托管后台前端并同域反代 `/admin-api/` 到当前 active Go slot；客服图片只通过同源 `/uploads/support/` 代理展示，不把 OSS 或 API 绝对地址直接放进后台页面；一次性 bootstrap 环境变量已用于初始化 owner 账号，随后已从 ECS 环境文件清理。生产日常按“一个 owner 主账号自己用的傻瓜后台”设计，页面优先直观操作，不把多人角色当核心概念；底层低权限账号只服务自动巡检和未来扩展。扣次、待补扣和长期失败对账当前按后端 worker 自动追账 / 自动终结口径处理，后台只是可见和应急修账入口，不要求业务负责人每天人工盯。

详细页面结构、筛选项、指标和版面建议见 [admin-dashboard-design.md](D:/wuhao/docs/runbooks/admin-dashboard-design.md)。

## 当前真相

- 模型策略显示口径：主聊天默认仍是百炼 / 千问；旧 `CHAT_PRIMARY_*` 已退役且 readiness 继续拒绝。2026-06-29 起后台健康可显示可选 `GPT_RELAY_*` 状态，`disabled` 属于正常状态，不代表模型问诊故障；只有显式配置并开启后，它才作为主聊天开流前候选链路，失败或 15 秒无可见正文仍回退千问。
- 管理后台前端目录：`admin`。本地开发：`cd admin && npm install && npm run dev -- --host 127.0.0.1 --port 5174`。生产构建：`npm run build`。
- 生产部署脚本：[deploy-ecs-admin.ps1](D:/wuhao/scripts/deploy-ecs-admin.ps1)。脚本会构建 `admin/dist`、同步 `admin` A 记录、上传静态包、配置 Nginx、签发 / 复用 Let's Encrypt HTTPS 证书，并验证首页和未登录 API 状态；发布成功或失败退出时会清理本次 `/tmp` 上传 / 脚本临时文件，成功后只保留最近若干后台静态 release，避免 ECS 静态包长期堆积。
- 生产入口：`https://admin.nongjiqiancha.cn/`。HTTP 80 只用于 ACME challenge 和 301 跳转；HTTPS 下 `/admin-api/` 和 `/uploads/support/` 由 Nginx 反代到当前 active Go slot，其中 `/uploads/support/` 只用于后台展示客服图片。
- 登录后只读烟测：[check-admin-authenticated-smoke.ps1](D:/wuhao/scripts/check-admin-authenticated-smoke.ps1)。该脚本不会把后台密码写入仓库或输出到日志；优先使用当前 PowerShell 的 `NONGJI_ADMIN_USERNAME` / `NONGJI_ADMIN_PASSWORD`，兼容 `ADMIN_SMOKE_USERNAME` / `ADMIN_SMOKE_PASSWORD`，也支持读取本机私密 `%USERPROFILE%\.nongjiqiancha\prod-secrets.json` 里的 `admin_smoke_username` / `admin_smoke_password`。本机已创建生产低权限 `ops_readonly` 巡检账号 `codex_ops_monitor` 并把密码只保存到该私密文件，用于自动巡检；需要 owner 全量 smoke 时仍要临时提供 owner 凭据并显式传 `-RequireOwner`。脚本会登录后台、访问总览 / 监控 / 洞察 / 用户 / 会员 / 订单 / 礼品卡 / 帮助反馈 / App 日志 / 审计 / 今日农情 / 检查更新 / 注销申请等只读 API，最后退出；这比只看未登录 `/auth/me=401` 更能证明后台登录后核心页面可用。
- 公网黑盒只读烟测：[check-public-blackbox.ps1](D:/wuhao/scripts/check-public-blackbox.ps1)。该脚本不登录、不带后台密码，从公网域名请求 API healthz、官网、www、后台首页、后台未登录 `/auth/me=401`、下载域名探针和 HTTP->HTTPS 跳转，验证外部用户视角的入口可达性；下载域名探针会调用本机受控脚本检查 OSS CNAME / 证书 / 签名链。它不能替代登录后 owner smoke，也不是自动告警。
- 管理后台 API：`/admin-api/v1/*`，由 `server-go` 提供，不单独起第二套后端。
- 后台登录：`POST /admin-api/v1/auth/login`，成功后写 HttpOnly session cookie 和 CSRF cookie，前端请求带 `X-Admin-CSRF`。登录入口有两层防刷：外层保留 IP 级内部接口保护，内层按“用户名 hash + IP hash”默认 `10/10min` 限制失败 / 尝试请求，可用 `ADMIN_LOGIN_RATE_LIMIT_*` 调整；限流 key 不保存明文用户名或 IP。
- 后台账号安全：`POST /admin-api/v1/auth/change-password` 支持登录后自助修改当前后台密码；后端验证当前密码、限制新密码最短 8 字符、改密后清 `must_change_password` 并吊销同账号其它后台会话。若账号被标记为必须改密，除 `/auth/me`、`/auth/logout` 和 `/auth/change-password` 外，其它后台 API 会返回 `password_change_required`。
- 后台账号：服务启动时可用 `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD` 初始化；密码会以 PBKDF2-SHA256 hash 存入 `admin_users`，明文不得写入仓库、文档或前端。
- 后台账号口径：生产日常只按 owner 主账号运营，页面文案和操作路径按“主账号直接看、直接点、少解释”收口。底层仍保留 `ops_readonly` 等最小授权字段，用于本机自动巡检和未来多人扩展；服务端校验仍是安全边界，不能靠前端隐藏按钮。
- 后台审计：登录、登出、查询用户、客服回复、日志查询、审计日志查询、今日农情、检查更新、检查更新校验失败、礼品卡生成 / 查询 / 作废 / 用户兑换等会写审计记录。
- 后台写操作体验：今日农情补跑、检查更新发布 / 停更、礼品卡生成 / 作废、帮助反馈状态更新和客服回复等入口会显示按钮忙碌态、阻止重复点击，并在失败时弹出明确错误；检查更新发布 / 停更、礼品卡生成 / 作废和客服回复有二次确认。礼品卡生成和作废不只靠前端确认，后端也强制校验确认字段并审计失败。长账号ID、完整卡码和错误字段会自动换行，避免窄屏或宽表撑破页面。
- 后台手机端和傻瓜化体验：默认按“简单后台”展示，遵循“先看核心任务，高级功能按需展开”的渐进披露口径。侧栏常驻优先展示 `首页 · 今天先看 / 用户管理 / 帮助反馈 / App日志 / 礼品卡`，其它真实页面保留在左侧“更多工具”可见分组中，默认直接展开显示，不再折叠隐藏；顶部可切换“全部功能”，但这只影响导航展示，不改变真实路由、后台 API、服务端权限或审计。首页默认只显示当前结论、就绪摘要、今天先处理、常用操作和核心决策卡；智能问诊链路、手机实测清单、上线前检查、趋势、地区和能力接入情况折叠在“更多监控和上线检查”。顶栏提供账号ID / 手机号全局搜索，手机端提供“看监控 / 查用户 / 看反馈”快捷入口；用户 / 反馈 / 礼品卡 / 注销 / 会员明细 / App 日志 / 审计 / 今日农情 / 检查更新历史等宽表在窄屏下转为字段卡片，按钮、输入框和链接触摸面积按手机使用加大。帮助反馈详情可返回会话队列、打开下一条待回复、打开用户详情，并可点常用回复模板填入草稿；App 日志有短信失败、登录失败、闪退、检查更新失败、下载失败、安装未完成、图片上传失败等常用筛选按钮，筛选可一键清空。顶栏“截图模式”只做前端临时遮罩，用于录屏 / 截图时隐藏完整手机号、完整礼品卡码、反馈正文、问诊全文和图片缩略图；它不是导出脱敏能力，不改变后端返回、权限或审计。
- 后台敏感资产可见性：owner 主账号在用户列表、用户详情、帮助反馈会话列表和反馈详情中可直接查看 / 复制完整手机号，用于回访和排障；用户详情还提供拨打入口，用户列表可直接点“看反馈”跳到该用户反馈会话。owner 也可查看客服正文 / 备注、礼品卡完整卡码并回复客户。低权限巡检账号仍只拿脱敏信息；完整手机号搜索仍走服务端 `phone_hash` 精确匹配，不记录明文查询值。礼品卡完整码在后端列表 / 用户详情查询阶段只对可看完整码的账号读取并解密。完整手机号、完整卡码、客服正文和图片 URL 不得写入审计 detail、服务日志、项目文档、公开截图或批量导出。
- 后台展示安全：App 日志详情会在前端再次脱敏敏感字段；客服图片只展示后台同源、无 query / hash、单层 `/uploads/support/*.jpg` 图片，后端会把合法 API 绝对 URL 归一为后台同源路径，support 图片响应头为 `private, no-store`；今日农情来源只允许安全 HTTPS 链接，避免后台页面被日志或异常数据带偏。当前 support 图片按上传用户归属鉴权，普通用户读取和提交 support 图必须属于本人，后台 owner / support 等可看客服正文角色通过后台 session 访问；后续若支持同一用户多工单隔离，再补会话 / 工单维度归属。
- Android 没有后台入口，也没有调用任何 `/internal/*` 或 `/admin-api/*` 接口。

当前已落地的后台页面 / API；本机 Codex 日常巡检自动化的标准配置见 [codex-automations.md](D:/wuhao/docs/runbooks/codex-automations.md)：

- 总览：`GET /admin-api/v1/overview`，展示健康状态、今日问诊、App 错误、未回复反馈和今日农情状态。
- 监控面板：`GET /admin-api/v1/monitoring`，聚合服务健康、今日 / 24h / 7d 使用情况、App 自动日志错误、登录前认证失败、闪退补报、待回复反馈、反馈 open / replied / closed 队列、今日农情、礼品卡兑换异常、后台操作失败、最近 30 天问诊地区分布和 App 错误 Top；响应额外返回 `action_items`、`launch_readiness`、`capabilities`、`model_usage_policy`、`user_regions`、`auth_logs` 和 `app_update_logs`。其中 `model_usage_policy` 会列出当前后端真实模型调用口径：主聊天默认显示 `qwen3.5-plus + search_strategy=turbo` 千问主链；若显式启用 `GPT_RELAY_*`，显示 GPT relay 候选 + 千问回退；`CHAT_PRIMARY_*` 已退出生产主聊天配置，不再在后台作为优先中转链路展示；记忆文档摘要为 `qwen-plus + OpenAI兼容非流式 + enable_thinking=false`，今日农情为 `qwen3.5-plus + OpenAI兼容非流式 + search_strategy=turbo + forced_search=true + enable_thinking=false`。当前不保留轻量摘要候选、今日农情其它接口候选或环境变量模型切换入口；面板会提示 `qwen-turbo` 是模型名而不是搜索策略，且不在当前生产链路中，`search_strategy=turbo` 才是联网搜索策略。`launch_readiness` 里的“模型问诊”不再只看 healthz；只有最近 24 小时同时有真实文字问诊和图片问诊记录才标 ready，只有文字问诊或没有真实问诊记录时会保持 attention，避免把“模型 Key 健康”误读成“真机主聊天 / 图片问诊已完整验收”。`user_regions` 会按账号最近一次已识别地区拆出“注册用户地区”和“当前会员地区”两块，让非运维也能大概看出用户主要来自哪里；这不是精确注册地，也不是 100% 覆盖，只是基于账号最近地区做运营近似盘子。`auth_logs` 聚合最近 24 小时 `auth.*`、`auth.app_crash` 和 `app.crash`，用于快速看短信验证码登录、历史旧包融合事件和登录 / 运行期闪退，并额外拆出 `latest_crash_at`、`env_blocked`、`env_warnings`、`login_network_failures` 以及 `funnel` 登录阶段漏斗；`latest_crash_at` 只来自 `client_app_logs.created_at`，不读取崩溃 attrs、正文、手机号、图片 URL、token 或完整堆栈，用于让监控页直接显示“最近闪退：时间”或“24h 无新闪退”。漏斗按短信验证码、账号会话、登录 / 运行闪退和历史旧包融合认证分组，只统计事件名、等级和次数，不读取日志 attrs。登录排障按钮支持一键打开全部 `auth.*` 日志，也会按精确事件名拆开短信发送、短信登录校验、登录成功、请求网络失败、登录闪退、运行闪退和历史旧包融合记录；检查更新排障按钮支持一键打开全部 `app_update.*` 日志，也会按精确事件名拆开检查开始、有新版本、手动无更新、检查失败、需要安装权限、下载开始、下载失败、安装页失败和已拉起安装页，避免一个笼统按钮漏掉真实失败阶段。前端已收成“当前结论 / 就绪-程序需处理-人工确认-上架阻塞 / 上线人工确认项 / 登录与账号ID / 礼品卡与权益 / 客服反馈 / App质量”决策卡、模型调用口径、上线前真机回归清单、正式上架检查、快捷入口、关键队列、登录排障、登录阶段漏斗、检查更新排障和明细表，让非运维也能先看出当前哪里正常、哪里需要处理、哪里挡住正式上架；上线人工确认项会在首屏列出阻塞 / 需处理事项、负责人和入口。真机回归清单会把短信验证码登录、主聊天文字问诊、图片问诊 / 弱网发送、礼品卡兑换、今日农情、检查更新和帮助反馈串成可点击入口，用现有监控数据提示“待真机 / 有登录 / 有记录 / 有兑换 / 有检查 / 看日志 / 先生成卡”；其中登录和检查更新的日志入口会直接带 `event_prefix=auth.` 或 `event_prefix=app_update.`，但不替代真实 Android 回归；短信验证码登录只要生产 HTTPS、Redis 和短信服务可用，WiFi 或代理环境也应可用。监控窗口里的 `active_sessions` 表示当前有效 App session 总量，`recent_auth_sessions` 表示该窗口内新创建 / 登录 session；礼品卡队列同时看批次数、总卡数、可用卡、已兑换和 24h 失败尝试，生产库没有可兑换卡时会直接提示先生成礼品卡。上架检查会把支付、备案、AccessKey 轮换、第一封 SLS 告警邮件送达确认和真机登录回归等未闭环事项标成“程序需处理 / 人工确认 / 上架阻塞”，不伪装成已完成；SLS / 云监控配置以最近严格巡检脚本和仓库记录为准，后台页面不实时读取阿里云告警规则，首封 SLS 告警邮件仍需真实或测试触发确认送达。主动巡检脚本 [check-admin-monitoring-actions.ps1](D:/wuhao/scripts/check-admin-monitoring-actions.ps1) 可用上述低权限巡检账号只读读取该接口，把行动项、待补扣、待反馈、闪退、登录失败、今日农情未就绪等脱敏输出成机器可读状态；默认不会把 `launch_readiness` 的上线准备 attention 当成日常故障，除非显式传 `-IncludeLaunchReadiness`。本机 Codex 自动化 `运维自动化` 每天 23:20 已调用该脚本，目标是“异常才提醒”，不是让负责人天天打开后台肉眼查。
- 2026-06-15 起，`launch_readiness` 的每个条目可带 `manual=true`，前端“上线人工确认项”只展示显式人工确认且未 ready 的条目，不再把服务健康、App 错误或登录异常误归到人工确认区；2026-06-20 起条目还可带 `launch_only=true`，用于未发正式更新、暂无礼品卡库存、支付生产验收未完成这类上线准备项，日常行动项脚本默认不把它们当程序故障。App 备案已通过并在后台标为 ready；当前显式人工项包括 App 公安备案、AccessKey 轮换、最终真机回归、短信套餐余额、费用 / 套餐成本、最终 release 物料和 SLS 首封邮件确认。人工项还可带 `confirm_hint`，前端会在“上线人工确认项”和“正式上架检查”卡片里显示“确认方式”，把去哪里确认、看什么证据、哪些敏感材料不能写入仓库或后台备注说清楚。费用 / 套餐成本项只提醒通过脚本或控制台确认账户余额、DYPNS / 融合认证套餐处置、短信套餐余量、qwen-plus 资源包和百炼节省计划，不让浏览器后台持有阿里云密钥，也不把账单敏感截图、AccessKey 或密钥写进仓库、日志或后台备注。支付宝 APP 支付代码已接入，但真实收费放量前仍必须完成生产配置、验签、回调、对账、退款、异常补偿和权益发放验收。
- 同日监控页首屏新增“程序需处理项”：只展示 `launch_readiness` 中 `manual!=true` 且未 ready 的条目，优先把 blocked 红色项排在前面，并保留负责人、状态和入口。它用于承接代码、配置、部署或后台操作可推进的问题；“上线人工确认项”继续只放 App 公安备案、AccessKey 轮换、短信余额、费用 / 套餐成本、SLS 首封邮件、最终 release 物料和真机最终确认等需要人确认的事项。正式上架检查明细卡也会额外显示“程序处理 / 人工确认”小标签和可选确认方式，避免人工确认项在明细区被普通“需处理”状态误读。`scripts/check-admin-surface.mjs` 会检查这个区域存在，并拦截人工项被混入程序处理区、确认方式渲染被删或明细卡分类标签被删。
- 性能边界：总览、监控面板和产品洞察这三类聚合接口在服务端共享 4 秒查询超时；`033_monitoring_query_indexes.sql` 已给 App 日志等级 / 事件时间窗、有效 session 统计和待回复反馈队列补索引，`034_admin_performance_indexes.sql` 已给账号、会员、额度和加油包后台统计补索引，`035_admin_order_gift_indexes.sql` 已给订单按账号查询和礼品卡失败原因聚合补索引。后台慢查询会按内部错误收口和记录日志，不应长时间占住请求或影响主聊天 SSE。
- 检查更新验收口径：`app_update_logs` 和物料齐全只代表“可测 / 有阶段信号”，不能替代旧包真机覆盖安装；`launch_readiness` 的“安装包更新”在版本号、HTTPS APK、SHA-256 和文件大小都齐时仍保持 `attention`，完成旧包“检查更新 -> 下载 -> 校验 -> 系统安装页 -> 覆盖安装成功”前不要当成正式验收。
- 用户管理：`GET /admin-api/v1/users`、`GET /admin-api/v1/users/detail`，按账号ID（底层字段仍叫 `user_id`）/ 手机号查询，完整手机号查询会在服务端按 `phone_hash` 精确匹配，不记录明文查询值；主账号在列表和详情里都能直接看到并复制加密保存的完整手机号，详情可点拨打，账号ID可复制，筛选可清空，反馈列可直接跳到对应帮助反馈会话。页面同时展示会员、额度、加油包、订单、礼品卡、最近问诊、App 日志和反馈；低权限巡检账号只看脱敏号。
- 会员额度：用户级只读展示当前档位、到期时间、每日额度、`quota_ledger` 扣次流水、`topup_packs` 加油包包明细、订单记录和礼品卡兑换记录；`upgrade_credits` 只作为历史遗留表保留，不在当前后台权益页展示。`GET /admin-api/v1/entitlements/summary` 提供全局盘子和扣次自动对账视图，页面可直接看注册用户、当前会员总数、Free / Plus / Pro 分布、7 / 30 天内到期、今日基础额度用满、有加油包余额，以及 `quota_consume_outbox` 自动追账状态；日常不要求负责人手动处理待补扣，owner 修账接口只作为技术应急口保留。监控面板另有“扣次自动对账”卡，普通 pending / needs_ops 表示系统低频追账中，长期无法安全追扣的记录会自动终结为 `uncollectable`。
- 订单：`GET /admin-api/v1/orders`，授权角色可按账号ID筛选或留空查看最近支付订单 / 会员变更记录；`POST /admin-api/v1/orders/grant` 仅允许 `owner / finance_ops` 对已付款但权益未成功的支付订单人工补发权益；后台另提供 `query / refund / close-expired / reconciliation` 等财务运营入口。页面不提供伪造支付成功或随意改权益；退款只允许财务角色发起，正式已发权益订单会被后端拦截并要求人工核查，内部 0.01 测试订单作为历史联调记录保留但不自动扣回测试权益；本地关闭超时待支付订单不等于支付宝关单；对账摘要只能作为核查信息，不得当成完整财务报表。2026-06-28 起支付宝生产门禁已切到 public，正式订单会计入支付金额；历史 0.01 联调订单仍按测试订单排除正式收入统计。
- 礼品卡：`GET/POST /admin-api/v1/gift-cards/batches`、`GET /admin-api/v1/gift-cards/summary`、`GET /admin-api/v1/gift-cards/cards`、`POST /admin-api/v1/gift-cards/void`、`GET /admin-api/v1/gift-cards/attempts`；可创建 Plus / Pro 礼品卡批次、查询全局汇总，owner / finance_ops 可直接查看并复制新生成礼品卡完整卡码，按批次 / 状态 / 账号ID / 卡码尾号追溯卡状态，按账号ID / 尾号 / 成功状态 / 失败原因查询兑换尝试，并可作废未兑换卡。创建批次必须输入“张数 + 档位 + 天数”确认，例如 `3 Pro 30`；作废必须输入“作废”确认，服务端也会强制校验。完整卡码使用 `APP_SECRET` 派生密钥加密保存，兑换仍用 hash 校验；后台非授权角色查询礼品卡列表或用户详情时不读取 / 不解密完整卡码；旧卡若没有加密字段，只能显示掩码 / 尾号。
- 用户侧礼品卡兑换：`POST /api/gift-cards/redeem`，鉴权后事务内校验卡状态并发会员权益，记录成功 / 失败尝试、地区和脱敏 IP；Android 设置页“礼品卡”已经接真实兑换接口。
- 帮助与反馈：`GET /admin-api/v1/support/conversations`、`GET /admin-api/v1/support/messages`、`POST /admin-api/v1/support/messages`、`POST /admin-api/v1/support/conversations/status`；支持待回复 / 已回复 / 已关闭队列、账号ID / 手机号 / 会话消息搜索、后台回复、关闭和重开，输入搜索词时后台会自动查全部队列和全部历史，完整手机号查询同样按 `phone_hash` 精确匹配。主账号在会话列表和详情里都能直接看到完整手机号，账号ID可点回用户详情，筛选可一键清空；详情默认拉最近 200 条，若会话总消息数更多，页面会提示只显示最近消息。用户侧发送消息和系统自动回复走同一条 MySQL 命名锁 + 事务路径，避免同一用户并发连发时重复插入自动回复。用户和客服在客服会话正文、处理备注里可以发送数字、手机号、订单号、礼品卡码等排障必需信息，不做内容拦截；安全边界是这些正文和备注只保存在客服相关表并按后台登录查看，不写入审计 detail、日志、项目文档或低权限巡检响应。客服回复前端保留二次确认；回复图片附件只能使用同源 support 图片校验，审计里仍不写正文。
- 注销申请：`GET /admin-api/v1/account-deletion-requests`、`POST /admin-api/v1/account-deletion-requests/status`；用户侧 `POST /api/account/deletion-requests` 创建申请后会退出当前设备，后台可按待处理 / 处理中 / 已处理 / 驳回 / 取消推进状态。这里的已处理只表示线下核验和处理流程已收口，不代表系统已经自动物理删除或匿名化全部账号数据；会员、订单、礼品卡、反馈、日志和法定留存范围仍需按合规规则处理。
- App 自动日志：`GET /admin-api/v1/app-logs`，继承自动日志脱敏规则，可按账号ID、精确事件名、事件前缀 `event_prefix`、平台、包类型 `build_type`、App 版本号 / 版本名、Android 系统版本、设备型号、等级和时间范围筛选；精确 `event` 优先于前缀筛选，不展示聊天正文、图片 URL、手机号、token、APK URL 或 SHA-256 原文。
- 后台审计：`GET /admin-api/v1/audit-logs`。
- 今日农情：`GET /admin-api/v1/today-agri/cards`、`POST /admin-api/v1/today-agri/generate`、`POST /admin-api/v1/today-agri/manual`；Codex 自动化 / 本机命令行通过 `scripts/publish-today-agri-manual.ps1` 走 Cloud Assistant 进入 ECS，再调用本机 `POST /internal/jobs/today-agri-card/manual`，仍写同一张 `daily_agri_cards`。
- 检查更新：`GET /admin-api/v1/app-update/android`、`POST /admin-api/v1/app-update/android`、`GET /admin-api/v1/app-update/android/events`；后台可直接维护 Android 版本号、HTTPS APK、SHA-256、文件大小和停更状态，每次保存会追加 `app_release_events` 发布历史，对外 `/api/app/update` 优先读取数据库表 `app_release_configs`，无记录时才回退环境变量。当前默认只做普通更新，`force_update` 兼容字段默认不生效，除非未来显式配置 `APP_UPDATE_ALLOW_FORCE_UPDATE=true`。
- 账号安全：`POST /admin-api/v1/auth/change-password`；所有后台角色都可进入“账号安全”页修改自己的密码，强制改密账号登录后会先停留在该页。
- 产品洞察：`GET /admin-api/v1/insights`，首版只读展示今日 / 24h / 7d / 30d 用户增长、登录 session、问诊、图片问诊、App 异常、登录排障、反馈、礼品卡和今日农情失败趋势；同时聚合反馈主题固定关键词命中、App 事件分类、Top App 事件和礼品卡失败原因。该接口只返回计数、比例、事件名和固定分类，不返回聊天全文、反馈正文、图片 URL、手机号、token、模型 Key 或礼品卡完整码。

仍保留的内部共享密钥接口：

- 帮助与反馈、App 自动日志、内部审计、今日农情生成 / 状态 / 探针 / 人工发布仍保留 `/internal/*` 入口作为 ECS 本机脚本、Cloud Assistant 或 VPC 内部工具兼容入口，但必须同时满足 loopback / 私网来源和对应共享密钥；公网脚本不能只凭共享密钥访问这些接口，浏览器后台也不应持有 `SUPPORT_ADMIN_SECRET` 或 `DAILY_AGRI_JOB_SECRET`。正式浏览器后台客服、App 日志、审计和今日农情入口必须走 `/admin-api/v1/*`。

## 当前不要误解

- 买服务器不等于自动有管理后台。
- `SUPPORT_ADMIN_SECRET`、`DAILY_AGRI_JOB_SECRET` 仍只是共享密钥，不能给浏览器前端使用；正式后台浏览器入口必须走 `admin_users` / `admin_sessions` / CSRF。共享密钥接口只作为本机 / 内网脚本兼容，不替代后台账号、角色权限和审计。
- `/admin-api/v1/monitoring`、`/admin-api/v1/app-logs` 和 `/internal/app/logs` 是给运维和后台面板用的只读 / 聚合入口，不是完整 SLS 告警中心。
- `/api/app/update` 当前已经接上后台可写发布配置和 `app_release_events` 发布历史，但它仍不是完整应用商店 / 推送中心；正式发布仍要记录 APK 链接、SHA-256、大小、签名指纹、操作人和时间，并做真机覆盖安装回归。
- 礼品卡后端、后台和 Android 兑换入口已接入首版；Android 只在 `/api/gift-cards/redeem` 返回成功后展示“兑换成功”，没有后端成功结果时不能弹真实成功。后台现在可以页面内查看和复制新生成礼品卡完整卡码，但不能把完整卡码写进备注、审计、日志、文档或批量导出文件。
- 开发期会员接口不是正式支付回调，生产必须保持关闭。
- Android 客户端不能承载后台逻辑；客服回复、补权益、发礼品卡、停更新和删除用户数据都必须在服务端或后台完成。

## P0：服务器落地前规划（历史阶段）

- 不提前硬做完整后台，避免账号体系、权限模型、数据库结构和真实运维入口未定时返工。
- 继续用 runbook、内部接口和只读脚本规划兜底。
- 继续按功能巡检，把当前真相和后续必补项写入 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。

该阶段已经结束：ECS / RDS / Redis / OSS / DNS、内部接口和第一版网页后台代码已经落地。P1 后台生产入口也已部署到 `admin.nongjiqiancha.cn`，并完成管理员 bootstrap、清理 bootstrap 环境变量、HTTPS、首页、登录和总览 API 验收；SLS 已有 AlertHub 最小告警集，`scripts/check-sls-alert-readiness.ps1` 可只读巡检规则是否启用、是否绑定行动策略和仪表盘，后续重点是补第一封告警邮件送达确认、数据库只读脚本和更细的运营动作。

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
- 后台账号：当前生产按一个 owner 主账号使用，不新增复杂多人账号管理。底层保留最小服务端授权字段和低权限巡检账号，避免自动巡检或未来扩展时把主账号密码到处复用。
- 安全：后台入口必须 HTTPS、SameSite Cookie、接口限流、登录失败限制、密码哈希、服务端授权校验和审计；不要把“前端隐藏按钮”当权限。
- 账号初始化：后台初始账号只能通过一次性环境变量、Cloud Assistant 脚本或本机安全脚本写入数据库 hash；账号名和明文密码不能写进仓库、文档、前端代码或部署脚本。初始化成功后必须禁用 bootstrap；当前已有自助改密和 `must_change_password` 强制改密硬兜底，后续多账号运营前仍要补受控重置、账号禁用和角色管理流程。
- 生产初始化状态：2026-06-07 已通过一次性 bootstrap 创建 owner 账号，随后已从 `/etc/nongjiqiancha/server.env` 删除 `ADMIN_BOOTSTRAP_*` 并重启 active slot；`scripts/check-ecs-readiness.ps1` 会把残留 `ADMIN_BOOTSTRAP_*` 视为生产 readiness 失败。后台账号可在“账号安全”页自助改密码；后续若忘记密码，应通过临时 bootstrap 或受控运维脚本重置，仍不得在仓库或文档中记录明文密码。

### 第一版页面建议

第一版按“可运营、可排障、少误操作”做，不追求花哨。推荐左侧导航：

- 总览：服务健康、今日请求量、错误量、登录 / 短信状态、模型 Key 池健康、Redis / RDS / OSS / SLS 状态、最近 5xx / 慢请求。
- 用户查询：按 `user_id`、完整手机号精确匹配 / 脱敏手机号线索、最近活跃时间、App 版本、设备、地区可信度、会员状态、额度、加油包、最近反馈、最近 App 自动日志。
- 用户地区 / 来源：按注册、最近活跃、问诊、图片问诊、会员成交、加油包购买和帮助反馈聚合省市分布；优先使用用户授权粗略定位后的系统反查地区，IP 粗定位只作为低可信参考，不保存经纬度或轨迹。
- 会员与额度：只读展示当前档位、到期时间、每日额度、今日已用、加油包余额、`quota_ledger` 和扣次自动对账状态；`quota_consume_outbox` 由后端 worker 自动追账或自动终结，owner 修账能力只作为技术应急口保留并写审计。
- 订单 / 支付：后台当前可核查支付订单和权益发放状态，可按账号ID或最近记录查看权益变更来源；已付款但权益未成功的支付订单可由 `owner / finance_ops` 二次确认后人工补发。生产放量前仍需完成回调验收、对账、退款和异常补偿流程。
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
| 服务健康 / 监控面板 | 已接入首版 | `/healthz`、管理 API、业务表、App 自动日志、后台审计、`auth_logs` 登录阶段漏斗、`app_update_logs`、SLS AlertHub 最小告警、SLS 告警只读巡检脚本、后台行动项主动巡检脚本、低权限 `ops_readonly` 自动巡检账号 | 第一封告警邮件送达确认、Nginx access 聚合、更细登录趋势 |
| 帮助与反馈 | 已接入首版 | `support_messages`、`support_conversations`、`/internal/support/*`、`/admin-api/v1/support/*`；客服图片附件走 OSS `support/` 30 天生命周期 | 正式坐席分配、标签、站外通知、客服绩效、聊天记录保存 / 删除规则 |
| 注销申请 | 已接入申请队列 | `account_deletion_requests`、`/api/account/deletion-requests`、`/admin-api/v1/account-deletion-requests*` | 物理删除 / 匿名化规则、法定留存、处理责任和批量清理脚本 |
| App 自动日志 | 已接入首版 | `client_app_logs`、`/internal/app/logs`、`/admin-api/v1/app-logs`、监控页登录排障卡、检查更新排障卡 | 更细的版本 / 设备 / 地区聚合和告警 |
| 后台审计 | 可直接接 | `admin_audit_logs`、`/internal/admin/audit-logs` | 后台账号 actor、角色、请求 ID |
| 后台账号安全 | 已接入首版 | `admin_users`、`admin_sessions`、`/admin-api/v1/auth/change-password` | 受控重置、账号禁用、角色管理 |
| 用户查询 | 已接入首版 | `app_accounts`、`auth_sessions`、`session_ab`、`session_round_archive`、`/admin-api/v1/users*` | session 管理、更多筛选和导出审批 |
| 会员 / 额度 | 已接入用户级只读、全局统计和扣次自动对账 | `user_entitlement`、`daily_usage`、`quota_ledger`、`quota_consume_outbox`、`topup_packs` | 后续接真实支付对账、退款和异常处理 |
| 今日农情 | 已接入状态查看和补跑 | `daily_agri_cards`、内部生成接口、`/admin-api/v1/today-agri/cards`、`/admin-api/v1/today-agri/generate`、`nongji-daily-agri-failed` AlertHub 告警 | 停用 API、首封告警邮件送达确认和发布记录 |
| 检查更新 | 已接入发布 / 停更配置、发布历史和排障日志 | `app_release_configs`、`app_release_events`、`/api/app/update`、`/admin-api/v1/app-update/android*`、`app_update.*` 自动日志 | APK 上传、完整回滚入口和更细二次确认 |
| 订单 / 订购 | 已接订单核查和窄口径人工补发，不能当正式收费完成 | `payment_orders` / `payment_notifications` 记录支付订单与通知，历史 `orders` 仍仅开发期权益变更记录，`/admin-api/v1/orders` 查询，`/admin-api/v1/orders/grant` 仅按已付款异常订单补发权益 | 生产小额实付验收、退款、对账、异常补偿和运营 SOP |
| 礼品卡 | 已接入首版 | `gift_card_batches`、`gift_cards`、`gift_card_redemption_attempts`、`/api/gift-cards/redeem`、`/admin-api/v1/gift-cards/*` | 批量发放、发放对象管理、更细风控；完整卡码批量导出暂不开放 |
| 产品洞察 | 已接入首版脱敏聚合 | `/admin-api/v1/insights`、`support_messages`、`client_app_logs`、`session_round_archive`、`gift_card_redemption_attempts` | 洞察日报、人工标签、代表短摘、处理状态和独立报表表 |

注意：当前迁移仍按 SQL 文件幂等执行，没有独立 `schema_migrations` 版本表；后续新增后台表必须继续保持幂等，避免重复 `ALTER` 造成启动风险。2026-06-13 已把早期 `004/005` 里 `expire_at` 改 nullable 的 `ALTER` 包成 `information_schema` 条件执行，降低每次启动重跑旧 SQL 的风险，但这不等于已经有正式迁移版本表。

### 第一版不要做的事

- 不做后台直接读写数据库的浏览器页面。
- 不把内部共享 secret 写进前端。
- 不开放大范围删除用户、导出全量数据、批量补权益、批量发礼品卡。
- 不在生产支付验收前用后台伪造真实支付收入或随手直接改权益；现有订单页只允许按后端真实支付订单核查，已付款但权益未成功的订单可由 owner / finance_ops 二次确认并写审计后补发权益，承接“已付款但权益未到账优先补权益”的客服口径。
- 不把聊天全文、图片 URL、token、模型 Key 铺到后台列表；主账号可在用户列表、帮助反馈会话列表和详情里直接看完整手机号，方便回访。客服会话正文、客服回复和处理备注可以记录排障必需的手机号、订单号、礼品卡码等信息；但这些正文和备注不得写入审计 detail、服务日志、项目文档、批量导出文件或低权限巡检响应，也不能包含后台密钥、token、AccessKey、模型 Key、数据库密码等系统秘密。
- 不把官网和管理后台做成同一个公开页面。

最小页面：

- 登录页和后台首页健康状态。
- 帮助与反馈：会话列表、详情、回复、未读 / 未处理队列、处理状态。
- 用户详情：按手机号 / user_id 查询会员、额度、扣次流水、反馈、订单、后续礼品卡和最近 App 自动日志；主账号可查看完整手机号并复制，方便回访。
- 用户来源与地区：展示注册 / 最近活跃的 masked IP、后端推断地区、用户自选地区、地区可信度、App 版本和设备型号；不展示完整 IP，手机号对主账号直接展示，低权限巡检账号仍脱敏。
- 用户真实反馈 / 产品洞察：首版已从帮助与反馈、App 自动日志、问诊归档计数和礼品卡兑换尝试中做脱敏聚合，展示趋势、固定分类和 Top 事件；下一步再补高频 bug、登录 / 上传 / 历史恢复卡点、用户不满意或反复追问的问诊场景、常见作物 / 病虫害、模型答偏线索和可改 UI / 提示词 / 后端规则的证据。页面不得直接把手机号、token、密钥、图片原图、图片 URL、反馈正文或完整聊天原文铺到后台。
- App 自动日志：按时间、等级、事件名、用户、App 版本、系统版本、设备型号筛选，先接 `GET /internal/app/logs`、SLS 最小日志集和 AlertHub 最小告警，后续再补更细趋势和告警邮件送达确认。
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

- 会员订单查询、支付回调状态、受控人工补权益、退款 / 对账状态。
- 异常补偿权益必须二次确认并写审计；待补扣日常走扣次自动对账，不作为负责人每天手工任务。
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
- 后台上线后继续接 SLS / 等价日志闭环，至少能查登录失败、权限拒绝、关键操作、接口 5xx 和数据库错误；当前 Go 请求日志和 Nginx error 已接 SLS，并已有 AlertHub 最小告警集，仍需确认首封邮件送达并补更细趋势。

参考：

- [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)：后台接口必须做服务端访问控制。
- [OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)：高风险操作、认证和授权失败应纳入可审计日志。

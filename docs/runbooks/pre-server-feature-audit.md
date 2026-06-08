# 买服务器前功能巡检记录

最后更新：2026-05-24

## 目的

在正式购买云资源前，按功能逐个深度巡检 Android 前端、Go 后端和上线依赖，避免买完服务器后才发现主链、权限、支付、存储或高并发边界没收口。

本文件只记录当前已巡检结论和后续要补的真实工作，不伪造未购买的服务器、域名、数据库、密钥或管理后台。

## 巡检原则

- 一次只收一个明确功能，先看当前代码和旧方案残留，再决定是否改动。
- 能用现有主链解决的，不另起一套新方案。
- 只把真实风险写进文档，不把“后面想做的功能”写成已经存在。
- 买服务器前不做盲目性能调参；买服务器后用 ECS / RDS / SLS 真实指标决定实例规格、连接池、索引和限流策略。

## 当前巡检进度

### 1. 会员中心与额度体系

结论：当前可以作为“占位展示 + 后端真相”继续推进，但不能在没有账号 token 和真实支付回调前开放收费。

当前代码真相：

- Android 主界面会员入口和设置页会员入口复用 `MembershipCenterBody`。
- Android 的开通 Plus、开通 Pro、升级 Pro、购买加油包按钮当前只提示“支付功能暂不可用”，不会调用后端订单接口。
- 后端 `/api/me` 是会员等级、到期时间、每日剩余次数、升级补偿次数和加油包次数的当前真相。
- Android `/api/me` 会员信息刷新带本地 request epoch；底部会员中心打开、设置页会员中心刷新或订购成功刷新重叠时，旧慢请求晚回来不会覆盖最新 UI 状态。
- 后端有效会员等级按 `tier_expire_at` 实时计算；Plus / Pro 到期或付费 tier 缺失到期时间时按 Free 处理。
- 扣次主链在回答归档成功后执行：先每日额度，再升级补偿，再加油包；`quota_ledger` 用 `user_id + client_msg_id` 防重复扣。
- 旧 `6元/100次` 只存在近期变更历史记录里；当前 Android 文案、后端常量和根规则都是 `6元/80次`。

上线前必须补：

- 接手机号登录或等价账号体系，生产环境启用 `APP_SECRET + AUTH_STRICT=true`，不能继续公开依赖裸 `X-User-Id`。
- 保持开发期订单直改接口关闭：生产必须 `APP_ENV=production`，且 `ALLOW_DEV_ORDER_ENDPOINTS` 不设置或为 false。
- 接真实支付下单、支付回调验签、金额 / 商品 / 用户二次校验、幂等发放、失败 / 退款 / 对账任务；不能只买服务器就把当前占位按钮改成真收费。
- 补最小管理 / 只读查询入口，至少能按 `user_id` 查 `user_entitlement`、`daily_usage`、`quota_ledger`、`topup_packs`、`upgrade_credits`、`orders`，用于客服处理会员异常。

后续优化：

- `/api/me` 后续可返回 `daily_limit`、套餐价格和加油包规格，减少 Android 端硬编码 `6 / 25 / 40`、`19.9 / 29.9`、`80次 / 6元` 带来的未来口径漂移。
- 补一条数据库集成测试覆盖“每日额度 -> 升级补偿 -> 加油包”的真实扣减顺序。

### 2. Go 后端高并发与性能边界

结论：当前 Go 后端不是“语言性能不够”，首版单实例可跑早期流量；真正风险在多后端实例前的共享存储、分布式锁、迁移和监控。

当前已有保护：

- 主聊天同一用户同一时间只允许一个活跃流，依赖 MySQL `chat_stream_inflight` 和 `UNIQUE KEY (user_id)`，不是纯内存锁。
- 同一 `client_msg_id` 的问答归档由 `session_round_ledger` 防重。
- 额度扣减由 `quota_ledger` 防重复扣。
- 模型 Key 池已支持 `DASHSCOPE_API_KEY_1/2/3` 和旧列表配置，按顺序主备使用，能在请求打开阶段遇到限流 / quota / 认证类错误时切换 Key；同一阿里云主账号多 Key 不扩真实 RPM / TPM。
- 今日农情生成使用数据库 lease，能防多实例重复生成同一天卡片。
- 图片上传当前限制为单张 JPEG 且 `<=1MB`，聊天图片 URL 只接受配置的公开基地址下 `/uploads/*.jpg`。

买服务器前已补的低风险优化：

- `server-go/internal/app/mysql.go` 的数据库连接池从固定 10 个连接改为可用环境变量调节，默认仍保持原值：
  - `MYSQL_MAX_OPEN_CONNS`，默认 10
  - `MYSQL_MAX_IDLE_CONNS`，默认 10，且不会超过 max open
  - `MYSQL_CONN_MAX_IDLE_SECONDS`，默认 300
  - `MYSQL_CONN_MAX_LIFETIME_SECONDS`，默认 1800

上线前必须注意：

- 如果首版不接 OSS，ECS 必须明确单台运行；因为当前 `/upload` 写本机磁盘，`/uploads/` 也是本机静态文件，多实例下上传落到 A 实例、后续请求或模型公网拉图打到 B 实例时可能 404。
- 如果要多实例，图片必须先接 OSS 或等价共享对象存储，并用 OSS 生命周期策略处理图片保存周期。
- 多实例前不要让多个后端实例首次启动同时跑迁移；数据库迁移应改成单独发布步骤，或至少加迁移锁。
- 主聊天 SSE 仍需要后续评估“单轮最大生成时长”，避免上游极端卡住长期占用 goroutine 和 inflight；不要用全局 `http.Client.Timeout` 直接砍流式请求。
- B/C 摘要的 `running` guard 和模型 Key 冷却仍是本进程级；多实例下可能重复摘要或各实例独立冷却 Key。主聊天用户级限流已支持 Redis，写回已有 `round_total` 校验，所以主要风险是成本和抗刷，不是旧摘要覆盖新轮次。
- 归档成功后、扣次前如果进程崩溃且短重试也失败，仍可能漏记一次成本；正式上线后要靠日志和对账巡检兜底。

买服务器后用真实指标决定：

- ECS 实例规格和后端实例数。
- RDS MySQL 规格、最大连接数、连接池参数、慢查询和索引调整。
- 是否把更多本地保护迁到 Redis / 网关级保护。
- 是否为 B/C 摘要加数据库 claim / lease，避免多实例重复调用 Qwen3.5-Flash。
- 是否补持久化模型调用 attempt / status 表，进一步压低极端重复开流或漏扣成本。

建议上线观察指标：

- 主聊天 SSE 中断率、上游 429 / 5xx、上游打开失败率。
- `append session round after stream failed`、`quota consume on DONE failed`、`summary extraction failed`。
- 图片上传失败、图片 URL 404、帮助与反馈发送失败。
- RDS 会话连接、连接数利用率、TPS / QPS、慢查询、行锁、IOPS、CPU / 内存。

参考阿里云资料：

- [RDS MySQL 监控指标](https://help.aliyun.com/zh/rds/apsaradb-rds-for-mysql/view-the-metrics-of-an-apsaradb-rds-for-mysql-instance)
- [RDS MySQL 错误日志和慢日志](https://help.aliyun.com/zh/rds/apsaradb-rds-for-mysql/view-error-logs-and-slow-logs)

### 3. 帮助与反馈

结论：当前主链能过早期内测，没有发现新旧方案并存。用户侧入口是设置页“帮助与反馈”，内部代码仍沿用 `support` 命名，不影响用户文案。

当前代码真相：

- Android 进入帮助与反馈页时拉 `GET /api/support/messages`，成功后调用 `POST /api/support/read` 标记后台 / 系统消息已读。
- 设置页红点由 `GET /api/support/summary` 的 `unread_count` 决定，只统计 `sender_type IN ('admin', 'system') AND read_by_user_at IS NULL`。
- 用户发送走 `POST /api/support/messages`；后台会话列表走 `GET /internal/support/conversations`，后台详情读取走 `GET /internal/support/messages?user_id=...`，后台回复走 `POST /internal/support/messages`。
- 内部后台接口由 `SUPPORT_ADMIN_SECRET` 保护，支持 `X-Support-Admin-Secret` 或 `Authorization: Bearer <secret>`。
- 帮助与反馈图片复用主聊天图片链：相机 / Photo Picker -> App 私有 JPEG 副本 -> `/upload` -> support 消息保存 URL；单次最多 4 张。
- 附件面板打开时，系统返回、手势返回和左上角返回都会优先收起附件面板；页面使用 `imePadding()`，输入框不会被键盘盖住。
- “删除所有历史对话”不会删除帮助与反馈消息。

本轮已补的保护：

- 新增 `server-go/internal/app/support_test.go`，覆盖帮助与反馈 payload 校验、图片 URL JSON 序列化、内部后台 secret 缺失 / 正确 / 错误三种情况。

上线前必须注意：

- 公开生产前仍必须接账号 token 并启用 `AUTH_STRICT=true`；否则裸 `X-User-Id` 能读写某个用户的帮助与反馈。
- 多后端实例前必须先上 OSS 或保持单实例；帮助与反馈图片同样依赖当前 `/upload` 本机磁盘和 `/uploads/` 静态读取。
- `SUPPORT_ADMIN_SECRET` 只能配置在服务端环境变量或未来管理后台后端，不能进 APK，不能写仓库。
- 当前内部后台接口只有共享 secret，没有后台账号、角色权限和 IP 限制；最小内部操作审计已开始落地，但还不能替代正式后台账号 / 权限系统。公开运营前至少要补最小后台或内网脚本。

买服务器后必须补：

- 配置 `SUPPORT_ADMIN_SECRET`、正式 `BASE_PUBLIC_URL / UPLOAD_BASE_URL`、SLS 日志。
- 管理后台最小版优先做：按用户查看会话、回复、未读 / 未处理列表、搜索、处理状态、审计日志。
- 账号注销 / 数据删除规则里明确帮助与反馈消息和图片是否删除、保留多久、由谁操作。
- 如果图片进入 OSS，补 OSS 生命周期策略；若涉及支付截图 / 隐私截图，后续评估私有读或后台受控查看。

建议上线观察指标：

- `get support summary failed`、`list support messages failed`、`create support message failed`、`mark support messages read failed`。
- 帮助与反馈图片上传失败、support 图片 URL 404、内部后台回复失败。

### 4. 礼品卡

结论：当前已从纯前端占位推进为可用的首版兑换系统。没有发现兑换码旧链路或 Android 直接改会员权益的并存方案。

当前代码真相：

- 设置页入口只显示“礼品卡”三个字，进入右进左出的轻量子页。
- 礼品卡页输入框不放占位提示，前端不限制字符类型、大小写或长度；输入为空时“兑换”按钮禁用。
- 输入内容后点“兑换”或键盘 Done，会调用后端 `POST /api/gift-cards/redeem`；成功后展示“兑换成功 / 确定”卡片并刷新 `/api/me` 同步会员中心，失败按后端错误展示用户可懂文案。
- Android 不保存完整卡码，不在本地判断卡码有效性，不直接改会员、额度、加油包、订单或 `quota_ledger`。
- App 内协议 / 隐私 / 风险文案已更新为“本版本不提供真实支付或农资商品交易；礼品卡仅支持官方卡码兑换会员权益”。

上线前必须注意：

- 真实礼品卡成功态必须继续以后端成功响应为准，不能在网络失败、解析失败、401、过期、已作废或低档位拒绝时假成功。
- 礼品卡规则必须以后端为唯一真相：卡类型、权益内容、有效期、可兑换次数、是否可叠加、是否绑定用户、是否可作废、异常处理都由后端控制。
- Android 只能提交用户输入并展示后端结果；不能在客户端判断礼品卡有效性或直接发权益。
- 后续账号注销 / 数据删除规则里要明确礼品卡兑换记录是否保留以及保留多久。

后续必须补：

- 批量发放名单、发放对象管理、批次详情钻取和更细风控统计。
- 商务发放时增加发放对象、发放渠道、外部备注和批次归属。
- 完整卡码可在后台受保护页面内查看和复制，服务端加密保存；兑换仍以后端 hash 校验为准，不做完整卡码批量导出。
- 继续观察限流和日志：连续兑换失败、撞库、已兑换、已过期、权益发放失败都要能查。

建议上线观察指标：

- 礼品卡兑换成功率、失败原因分布、同用户 / 同 IP 连续失败次数、权益发放失败、重复兑换冲突。

### 5. 检查更新

结论：当前检查更新主链已经从占位变成自有服务器 APK 分发，没有发现应用商店跳转、浏览器下载或旧占位方案并存。`/api/app/update` 本身是轻接口，高并发压力主要不在后端接口，而在 APK 下载带宽和发布配置准确性。

当前代码真相：

- Android 设置页点击“检查更新”后，请求 `GET /api/app/update?platform=android&version_code=<当前versionCode>&version_name=<当前versionName>`。
- 无更新时提示“已是最新版本”；有更新时弹“发现新版本”卡片，按钮为“稍后 / 立即更新”，更新说明兜底为“优化产品体验”。
- 后端由 `APP_ANDROID_LATEST_VERSION_CODE / APP_ANDROID_LATEST_VERSION_NAME / APP_ANDROID_APK_URL / APP_ANDROID_APK_SHA256 / APP_ANDROID_RELEASE_NOTES / APP_ANDROID_FORCE_UPDATE / APP_ANDROID_FILE_SIZE_BYTES` 控制 Android 最新版本；只有新版本号大于客户端当前版本号，且 APK 链接是公网 https，才返回可用更新。
- Android 下载 APK 到 App cache 后，会在调起系统安装页前校验最终响应仍是 https、可选文件大小、可选 SHA-256、包名等于当前 App 包名、APK `versionCode` 等于后端最新版本号且大于当前安装版本。
- Android 8+ 未授权安装未知应用时，会先打开系统授权页；普通 Android App 不能静默安装，最终仍由系统安装器让用户确认。

本轮已补的保护：

- 后端新增可选 `APP_ANDROID_APK_SHA256` 并透出 `apk_sha256`；无更新或 APK 链接无效时不会下发哈希。
- Android 下载后新增文件大小 / SHA-256 / 包名 / versionCode 校验，避免错包、坏包、半截包或低版本包进入系统安装页。

上线前必须注意：

- APK 建议放 OSS / CDN / 自有静态 HTTPS，不建议让 Go 后端动态服务大 APK。
- 签名证书必须固定；如果新 APK 换签名，Android 系统会拒绝覆盖安装。
- `versionCode` 必须单调递增；已经安装坏包的用户不能用低版本覆盖，只能再发更高 `versionCode` 的修复包。
- `force_update` 只是 App 内弹窗不显示“稍后”，不是系统级强制升级，仍不能绕过用户安装确认。
- 国内不同 ROM 的未知来源授权页、系统安装器、下载失败表现都可能不同，第一版正式更新必须真机验证。

买服务器后必须补：

- 配置正式域名 / HTTPS 和 APK 静态分发位置。
- 发布每个 APK 时记录 `versionCode`、版本名、文件大小、SHA-256、签名证书指纹、下载链接和发布时间。
- 后端运行环境变量配置 `APP_ANDROID_*` 后，用旧包真机跑完整链路：检查更新 -> 发现新版本 -> 授权安装未知应用 -> 下载 -> 校验 -> 系统安装页 -> 覆盖安装成功。
- 管理后台后续补“当前发布版本 / APK 链接 / 哈希 / 是否启用更新 / 一键停更 / 发布审计”。

建议上线观察指标：

- `/api/app/update` 5xx、配置了新版本但客户端无更新、APK 下载失败、文件大小或 SHA-256 校验失败、包名 / versionCode 校验失败、系统安装页打开失败。

### 6. 服务协议 / 隐私政策 / 风险提示

结论：当前这组页面主链可过早期内测，没有发现旧 WebView / 外部网页 / 旧平铺入口和当前目录页并存。正文口径与当前 Manifest 权限、图片入口、支付 / 礼品卡未接入状态基本一致。

当前代码真相：

- 设置页只有一个“服务协议”入口，进入本地内置目录页。
- 目录页包含 6 个二级页面：用户协议、隐私政策、第三方信息共享清单、个人信息收集清单、应用权限、风险提示。
- 6 个二级页面都走设置页右进左出的页面栈；左上角返回和系统返回键会先回到服务协议目录，再回到设置菜单。
- Android 主 Manifest 自身声明 `INTERNET / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE / READ_PHONE_STATE / REQUEST_INSTALL_PACKAGES`；引入阿里云融合认证 SDK 后，合并 Manifest 还会包含 `CHANGE_NETWORK_STATE / CHANGE_WIFI_STATE / RECEIVE_USER_PRESENT` 等登录认证所需权限；AndroidX WorkManager 还会合并 `WAKE_LOCK / RECEIVE_BOOT_COMPLETED / FOREGROUND_SERVICE` 等后台任务权限，当前应用权限页和隐私政策已按“带图待发送任务在后台 / 进程恢复 / 重启后有限重试”口径说明。
- App 当前已申请定位权限用于问诊地区上下文校准，但只上传系统反查出的省 / 市 / 区县等地区文本，不上传经纬度、不保存轨迹；当前仍不申请 App 相机、相册 / 存储读写、录音、通讯录、短信或通知权限。手机号一键登录 SDK 接入后会按需使用电话状态、网络状态和 Wi-Fi 状态来判断 SIM 卡、运营商网络和认证环境。
- Android Q+ 拍照成功后会把原始照片另存到系统相册 `Pictures/农技千查`，便于用户找回现场照片；本轮已把该口径补进隐私政策、个人信息收集清单和应用权限页。
- 用户可见正文没有暴露具体模型品牌、模型平台名或供应商账号信息，只使用“第三方大模型和云服务”这类必要委托处理口径。

已排查的旧方案：

- 没有发现用户可见的 `通义千问 / Qwen / DashScope / 百炼` 等具体模型或平台名。
- 没有发现联网加载协议正文或外部协议网页作为当前主链。
- 没有发现旧的“用户协议 / 隐私政策 / 风险提示”三行平铺入口继续生效。

上线前必须注意：

- 当前正文不是律师审定稿，只能作为当前产品内测 / 早期准备稿。
- 当前“删除所有历史对话”只删除问诊聊天历史、A/B/C 记忆和 30 天归档，不等于完整账号注销或全部个人信息删除。
- 真实服务器、域名、备案、OSS、SLS、Redis、手机号登录、短信、支付、定位、通知、统计 SDK 或第三方登录一旦接入，必须同步复核 6 个页面。
- 如果应用商店要求独立隐私政策 URL，需要把 App 内置正文同步到正式网页或可访问页面，保证 App 内外版本一致。

买服务器后必须补：

- 真实服务器和云服务商清单。
- 第三方大模型、短信、支付、对象存储、日志、缓存、统计或登录服务清单。
- 图片、聊天记录、帮助与反馈、额度流水、礼品卡兑换记录和日志的保存期限。
- 账号注销、个人信息查询 / 复制 / 更正 / 删除 / 撤回授权入口。
- 支付、退款、自动续费、会员到期、礼品卡规则的正式说明。
- ICP / App 备案号、公安联网备案信息和应用商店材料里的隐私政策链接。

详情入口见 [legal-privacy.md](D:/wuhao/docs/runbooks/legal-privacy.md)。

### 7. 主聊天与图片发送

结论：当前主聊天和图片发送链路可以继续作为首版主链推进，没有发现旧 Android 直连模型、旧完成接口、旧 active-zone、旧图片手势或旧上传通道在运行时并存。买服务器前不用因为 Go 语言本身做盲目性能优化，真正需要提前拍板的是鉴权、公开图片域名、单实例 / OSS 和真实监控。

当前代码真相：

- 文字、图片和图文混合问诊都只通过后端 `/api/chat/stream` 发起；Android 不保存模型 API Key，也不直连 Qwen / DashScope。
- Android 发送时先把用户消息和 assistant placeholder 插入 UI，再走 `SessionApi.streamChat`；图片发送会先把本地图片消息上屏并清空输入框，上传成功后复用同一个 `client_msg_id` 进入主对话。
- 图片先进入 App 私有 `files/composer_images` 稳定副本；合格 JPEG 直通，其他可解码图片转 JPEG 并压到最长边 / 单张 `<=1MB` 规则内。
- Android 上传入口是后端 `POST /upload`，请求带 `X-User-Id` 和可选 bearer token；后端只接受单张 `<=1MB` JPEG，并返回配置公开基地址下的 `https://.../uploads/*.jpg`。
- `/api/chat/stream` 会再次校验图片 URL：必须来自同一公开基地址、路径为 `/uploads/*.jpg`，不接受外部域名、非 jpg、query 或 fragment。
- 带图发送会排一个唯一 WorkManager 兜底任务；前台活跃或远端启动保护窗内不抢跑，只在 App 被杀或前台未可靠完成时补发。后台兜底遇到图片上传失败、网络中断、流异常结束、`409`、限流或临时上游错误时，会用同一 `client_msg_id` 指数退避重试；普通可恢复失败最多重试 5 次后移除 pending。
- 后端用 `chat_stream_inflight` 的同一用户活跃流唯一约束和 `client_msg_id + lease_token` 降低重复开流；轮次归档成功后才发 SSE `[DONE]`，扣次也在归档成功后执行。
- 历史恢复走 `/api/session/snapshot`；`a_rounds_for_ui` 优先来自 30 天归档，远端失败才回退本地窗口。
- 删除所有历史对话走 `POST /api/session/clear`；有活跃流时后端返回 409，成功后前端清 UI、本地快照、草稿、streaming draft、待发送 WorkManager 和私有 composer 图片。

已排查的旧方案：

- 未发现 `QwenClient / ModelService / ModelParams / BuildConfig.API_KEY / BAILIAN_API_KEY` 等 Android 直连模型主链。
- 旧 `/api/session/round_complete`、`/api/session/b`、`/api/session/c` 路由仍在，但只返回 `410 DEPRECATED_ENDPOINT`，不参与主链。
- 旧 `StreamingLocation / BottomActiveZone / renderBottomActiveZone / requestSendStartBottomSnap / followStreamingByDelta / dispatchRawDelta` 没有运行时残留；当前只有 composer 自己的 collapse overlay 命名，不是旧消息 active-zone。
- 旧 `ImagePreviewGesture.kt` 已删除；当前图片全屏预览走 Telephoto `ZoomableAsyncImage + HorizontalPager`。
- Android 端没有 OSS AK/SK；本轮只把 `ImageUploader.kt` 里仍写“上传 OSS”的过期注释改成当前真实 `/upload` 口径。

上线前必须注意：

- 生产环境必须配置 `APP_SECRET + AUTH_STRICT=true`，不能公开环境继续只靠裸 `X-User-Id`。
- `BASE_PUBLIC_URL / UPLOAD_BASE_URL` 必须是公网 https，且能被模型服务访问；否则图片上传成功也可能无法被主模型拉取。
- 如果首版不接 OSS，ECS 必须明确单台运行；多实例前必须先把 `/upload` 和 `/uploads/` 迁到 OSS 或等价共享对象存储。
- 数据库迁移应在发布流程中只执行一次，或补迁移锁；不要让多个后端实例首次启动同时抢跑迁移。
- 模型 Key 池可以短期填 2 到 3 把不同主账号 Key，`DASHSCOPE_API_KEY_1` 作为主 Key、`DASHSCOPE_API_KEY_2` 作为副 Key；朋友账号只能早期兜底，长期生产需要收回到自己可控账单和权限体系。
- 真实收费前仍必须补支付回调验签、账号绑定和对账，不能打开开发期订单直改接口。

买服务器后必须补：

- 决定图片是否首版接 OSS；如果接 OSS，补上传改造、访问策略、生命周期、图片删除策略和 SLS 监控。
- 配置并验证 `BASE_PUBLIC_URL / UPLOAD_BASE_URL`、HTTPS 证书、模型公网拉图、`/upload`、`/uploads/`、`/api/chat/stream` 全链路。
- 用真机跑弱网、多图、图片上传中杀 App、上传成功但流式未完成时杀 App、后台 WorkManager 恢复、删除历史时活跃流 409、冷启动 snapshot 恢复等回归。
- 多实例前仍需把 B/C 摘要 running guard 升级为数据库 lease / Redis / 网关级保护；聊天用户级限流已支持 Redis，主聊天同用户单流已由 MySQL `GET_LOCK` + `chat_stream_inflight` 跨进程控制。
- 观察 `append session round after stream failed`、`quota consume on DONE failed`、`summary extraction failed`、图片上传失败、图片 URL 404、前台 SSE 中断率和上游 429 / 5xx。

### 8. B/C 记忆与模型调用

结论：当前 B/C 记忆链路与现行口径一致，可以进入首版内测观察。B 层是通用短期记忆，负责接住最近对话主线；C 层是长期通用记忆，单文本内分为“长期通用记忆 / 用户画像 / 农业相关重点事件记忆”三块，不是通用知识库或病例流水账；没有发现 Android 端直连摘要模型或旧摘要接口仍在主链生效。买服务器前不需要继续盲改提示词，买服务器后重点补多实例下的摘要 claim / lease、日志告警和真实效果抽查。

当前代码真相：

- 主对话完成并成功归档后，后端在 goroutine 中调用 `SummaryService.ProcessSessionSummaries`，不会阻塞当前 SSE 完成态。
- B 层通用短期记忆由 `b_extraction_prompt.txt` 控制，默认 `<=500` 字，复杂最多 `<=700` 字，定位是全场景当前主线 / 当前事务短期承接。
- C 层长期通用记忆由 `c_extraction_prompt.txt` 控制，默认 `<=650` 字，复杂最多 `<=850` 字，输出固定三块：“长期通用记忆 / 用户画像 / 农业相关重点事件记忆”。它低频承接长期通用背景、用户画像和重点农业事件，不保存通用知识、短期病例流水账或具体剂量配方。
- B 层触发频率：Free / Plus 每 6 轮，Pro 每 9 轮；输入使用当前 A 层窗口。
- C 层触发频率：每 20 轮；输入使用旧 C 层长期通用记忆 + `session_round_archive` 最近 20 轮完整问答，不再用 A 层 6/9 轮窗口冒充 20 轮归档。
- 如果 C 层归档不足 20 轮，会保持 `pending_retry_c=true`，后续轮次完成后继续补提取。
- 摘要模型统一使用 `qwen3.5-flash`，非流式，显式 `temperature=0.8`，显式 `enable_thinking=false`，不联网。
- 单次摘要提取有 60 秒超时；模型失败、超时、写回失败或归档读取失败都会保持对应 `pending_retry_b / pending_retry_c`。
- 写回摘要时必须匹配触发快照的 `round_total`；如果处理期间已有新轮次完成，旧快照结果不会覆盖新轮次。

已排查的旧方案：

- Android 端没有摘要模型 API Key、摘要模型直连或 B/C 本地抽取逻辑；Android 只读取 `/api/session/snapshot` 返回的摘要字段用于本地快照结构兼容，不组装模型上下文。
- 旧 `/api/session/b`、`/api/session/c` 路由仍注册，但返回 `410 DEPRECATED_ENDPOINT`，不能绕过 `/api/chat/stream` 主链触发摘要。
- C 层已不再使用“旧 C + 当前 A 窗口 6/9 轮”作为长期记忆输入；当前代码会读取 `session_round_archive` 最近 20 轮完整问答。
- B/C 提示词没有引入外部知识库，也没有把通用农技规则库写进 C 层；当前仍是用户自身记忆，不是 RAG。

上线前必须注意：

- 多后端实例前，B/C 的 `running` guard 仍是进程内保护；同一用户同一层可能被多个实例同时提取，主要风险是重复调用 Qwen3.5-Flash 和同一 `round_total` 下最后写入者覆盖，通常不是用户会话数据错乱。
- `pending_retry_b / pending_retry_c` 只会在后续轮次完成后继续被处理；如果用户长期不再发问，失败摘要不会自己定时补账。
- C 层依赖 `session_round_archive` 30 天滚动保留；如果用户 20 轮间隔太久且旧轮次已过保存期，C 层可能因归档不足继续 pending。
- B/C 摘要质量最终取决于提示词和真实问诊内容，当前代码只能保证不丢账、不旧快照覆盖新轮次，不能保证每次抽取得完美。

买服务器后必须补：

- 多实例部署前，为 B/C 摘要增加数据库 claim / lease，或确认首版 ECS 单台运行。
- 在 SLS 中监控 `summary extraction failed`、`summary extraction skipped: already running`、`C summary extraction skipped: insufficient archived rounds`、`B/C summary write skipped: snapshot is stale`。
- 增加运维只读查询：按 `user_id` 查看 `round_total`、`pending_retry_b/c`、`b_summary`、`c_summary`、最近归档轮次和最近摘要错误日志。
- 上线后抽查真实用户的 B/C 输出，确认 B 不把长期画像写满、C 不吸收一次性病例 / 通用农技知识 / 具体剂量配方；必要时只调提示词，不先改主链。

### 9. 今日农情

结论：当前“今日农情”可以作为独立每日资讯卡片继续推进，没有进入主聊天消息、A/B/C 上下文、摘要或扣次链路。后端生成链路已用数据库 lease 防同一天并发生成，用户打开 App 只读缓存，缺失 / pending / failed 时 Android 静默不展示，不会临时多次调模型。

当前代码真相：

- 后端数据真源是 `daily_agri_cards`，主键为 `day_cn + scope`，当前 `scope=CN`。
- 用户侧接口是 `GET /api/today-agri-card`，需要普通用户鉴权，只读取当天 ready 卡片；没有 ready 卡片时返回 `missing / pending / failed` 状态。
- 内部生成接口是 `POST /internal/jobs/today-agri-card/generate`，只接受 `DAILY_AGRI_JOB_SECRET`，支持 `X-Internal-Job-Secret` 或 `Authorization: Bearer ...`。
- 生成前会尝试获取同一天同 scope 的数据库 lease，lease TTL 当前 5 分钟；已有 ready 卡片时直接返回，不重复生成。
- 生成链路当前保持 `qwen3.5-plus`，但不再走旧的 DashScope 原生 Generation 搜索链，而是改走百炼兼容模式 `Responses API + web_search`：显式 `temperature=0.8`、显式 `reasoning.effort=none`、`tool_choice=required`，并从 `web_search_call.action.sources[]` 提取真实来源 URL 做可信域名、https、近 7 天和去重校验。2026-06-08 在生产 ECS 实测发现 `qwen3.5-plus` 走旧原生 Generation + 联网搜索会稳定返回 `400 InvalidParameter / url error`，因此只调整今日农情的联网协议，不改主聊天模型。
- 生成时会读取过去 7 天已 ready 的卡片，把标题、摘要、来源、链接写进提示词，要求避免重复同链接、同标题或同一事件。
- 后端解析时要求 JSON 可解析、`card_name=今日农情`、严格 3 条有效 item、https 链接、发布时间近 7 天、URL 来自 DashScope 搜索来源、域名在可信官方 / 权威大站范围内。
- 后端会硬过滤广告、导购、联系方式、模型 / 提示词泄露、搜索参数、元表达、标题党词，以及过去 7 天和当天候选里的重复 URL / 重复标题；过滤后不足 3 条则不发布新卡片。
- Android 只把 ready 卡片作为 `ChatTimelineItem.TodayAgriCard` 插入展示层；真实 `messages` 仍只包含用户 / assistant，不写本地聊天快照，不参与发送、重试、复制、滚动工作线真值或后端上下文。

已排查的旧方案：

- 没有发现用户打开聊天页时临时触发今日农情模型生成的路径；Android 只调用用户侧只读接口。
- 没有发现今日农情写入 `session_ab`、`session_round_archive`、A/B/C 摘要或 quota 扣次的路径。
- 没有发现旧的本地静态假卡片作为真实主链；debug-only 文案预览里的示例卡片只用于隐藏预览。

上线前必须注意：

- `DAILY_AGRI_JOB_SECRET` 必须只放服务端环境变量和定时任务配置，不能进 APK 或仓库。
- 需要给内部生成接口配置真实定时触发，例如每天中国时间早上固定时间跑一次；用户打开 App 不负责补生成。
- 官方 / 权威域名白名单偏保守，可能导致某天过滤后不足 3 条而不展示，这是刻意选择，不要为了“每天必有”放宽到广告、软文或任意链接。
- 后端目前硬过滤重复 URL 和重复标题；“同一事件换标题”主要靠提示词和人工抽查约束，后续如果重复感强，再增加更强的事件指纹或人工审核，不先把今日农情塞进聊天上下文。

买服务器后必须补：

- 配置 `DAILY_AGRI_JOB_SECRET`、定时任务、SLS 日志和失败告警。
- 用内部生成接口跑一遍真实链路，检查 `daily_agri_cards.status/content_json/sources_json/error/lease_until`。
- 在管理后台第一版里补今日农情状态页：查看当天状态、失败原因、来源链接、手动补跑、停用当天卡片。
- 观察 `daily agri card generated`、`generate today agri card failed`、`get today agri card failed`、模型联网搜索失败、过滤后不足 3 条。
- 后续若做地区 / 作物个性化，必须新增 scope 或独立表设计，不能直接把今日农情混入用户聊天记忆。

参考资料：

- [阿里云百炼联网搜索](https://help.aliyun.com/zh/model-studio/web-search/)
- [阿里云百炼 DashScope API 参考](https://help.aliyun.com/zh/model-studio/qwen-api-via-dashscope)
- [阿里云百炼深度思考参数](https://help.aliyun.com/zh/model-studio/deep-thinking)

### 10. 账号 / 手机号登录与生产鉴权

结论：当前账号链路已经进入手机号登录骨架阶段，但还不是公开生产账号体系。后端已有严格鉴权开关、v2 session token、手机号账号表、旧本机身份迁移桥和短信 / 融合认证接口；Redis 认证短期限流已部署到 ECS 并健康。阿里云 SchemeCode、短信签名模板、DYPNS 环境变量和 Android 一键登录 SDK 客户端链路已落地；`api.nongjiqiancha.cn` HTTPS 已补齐，真正上线前缺的是 HTTPS 公网入口下的真机登录回归、AccessKey 轮换和旧 `X-User-Id` 兜底隔离，不是 Go 语言性能问题。

当前代码真相：

- Android 启动时由 `IdManager` 在本机 `SharedPreferences("app_ids")` 生成或读取 UUID 形式的本机 `user_id`；登录成功后会保存账号 `user_id`、v2 session token、token 到期时间、脱敏手机号和 `device_id`，并优先使用账号 `user_id`。
- Android 主要用户接口都会带 `X-User-Id: <当前 user_id>`；如果已登录则附加账号 session bearer token；Android 不再使用静态 `SESSION_API_TOKEN` 绕过登录。
- 图片上传 `/upload` 同样带 `X-User-Id` 和可选 bearer token。
- 后端 `ResolveAuthUserID` 优先验证 bearer token；验证成功时以 token 内的 `userID` 为准，不再使用 Android 传来的 `X-User-Id`。
- `AUTH_STRICT=true` 时，裸 `X-User-Id` 会被拒绝；必须配置 `APP_SECRET` 并提供可验证 bearer token 才能访问需要鉴权的接口。
- Android 已移除 `SESSION_API_TOKEN` 静态注入和运行时绕过；正式登录只走 per-user session token。
- 设置页“账号管理”里手机号会显示脱敏号码或未登录；“退出设备”已接 `POST /api/auth/logout`，只吊销当前设备 session 并回到登录门，不删除聊天、会员、额度、帮助与反馈、礼品卡或本机 `user_id`；注销账号仍未开放。真实可用动作包括“清理本机缓存”和“删除所有历史对话”：前者只清 `cacheDir/app_updates` 检查更新残留和 `cacheDir/composer_camera` 相机临时文件，不碰 `chat_ui_cache`、`files/composer_images` 或待发送 WorkManager 图文；后者只清问诊历史、A/B/C 记忆和 30 天归档，不删除会员、额度、帮助与反馈、礼品卡或本机 `user_id`。

已排查的旧方案：

- 没有发现旧的 Android 直连模型登录链、旧静态共享 token 正式化链或用户可点退出后清空账号的逻辑并存。
- 没有发现 Android 端注销逻辑并存；退出设备已收敛为当前 session 吊销，不做完整设备管理或账号注销。
- 身份来源已收敛为“未登录本机 `user_id` / 登录后账号 `user_id` + session token”；旧本机 `user_id` 只作为登录迁移桥，不再是登录后的长期身份真源。

上线前必须注意：

- 不能把共享静态 token 或测试用户 ID 当作生产登录方案；正式包必须通过手机号账号拿 per-user session token。
- 公开生产不能长期依赖裸 `X-User-Id`；它适合本地开发和早期闭环内测，不适合开放互联网环境。
- 如果直接开启 `AUTH_STRICT=true`，正式包必须已经能拿到 per-user session token，否则用户接口会 401。
- 手机号登录上线时，旧本机 UUID 用户的数据迁移 / 绑定已经有首版桥接，但仍需真机验证历史、额度、反馈和日志是否都能稳定归并到同一个手机号账号。

买服务器后必须补：

- 阿里云手机号认证收口：一键登录 Android SDK、SchemeCode、短信签名模板、DYPNS 环境变量和真机登录回归；Redis 分布式认证限流已接入，后续只需继续观察生产健康。
- 后端 token 刷新 / 主动吊销 / 多设备管理机制；Android 已保存 per-user token，但当前按“长期保持登录”口径不提供用户可点退出。
- 本机 `user_id` 到账号 `user_id` 的迁移策略需要继续真机验证，至少覆盖 `session_ab`、`session_round_archive`、会员 / 额度 / 加油包、帮助与反馈、订单 / 礼品卡未来表。
- 账号注销和个人信息查询 / 删除入口，明确聊天、图片、摘要、归档、反馈、会员权益、订单、礼品卡和日志的删除 / 匿名化 / 法定留存范围。
- 最小管理后台或只读脚本：能按手机号 / user_id 查身份绑定、会员、额度、反馈、订单、礼品卡和注销处理记录。

建议上线观察指标：

- 401 / 403 比例、token 验证失败原因、验证码发送失败 / 频率、同手机号多设备绑定冲突、旧本机 `user_id` 迁移失败。
- 严格模式切换前后，`/api/me`、`/api/chat/stream`、`/upload`、帮助与反馈、删除历史对话是否都能正常通过同一身份链。

### 11. 统一管理后台

结论：本章节原本是买服务器前的后台巡检，旧结论已被当前实现替代。当前统一管理后台首版已经上线到 `https://admin.nongjiqiancha.cn/`，形态是 Vite `admin` 前端 + `server-go` `/admin-api/v1/*`，具备后台账号 / session / CSRF / 角色 / 审计，覆盖登录、总览、监控面板、用户、会员额度、订单占位、礼品卡、帮助反馈、App 日志、今日农情、检查更新、审计和服务健康。仍未完成的是完整客服工单、SLS 告警 / 仪表盘、发布 / 回滚写操作、支付正式订单链路、礼品卡批量发放和更细运营报表。

当前代码真相：

- 后端真实后台入口现在以 `/admin-api/v1/*` 为主，浏览器后台走后台账号 session 和 CSRF；帮助与反馈、App 自动日志、今日农情内部生成、最小内部审计等 `/internal/*` 共享密钥接口仍保留给脚本 / 运维兼容，但不再是浏览器后台主链。
- 帮助与反馈内部接口由 `SUPPORT_ADMIN_SECRET` 保护，支持查最近会话、按 `user_id` 读消息和发送 `admin` 回复。
- 今日农情内部生成接口由 `DAILY_AGRI_JOB_SECRET` 保护，使用 `daily_agri_cards` 数据库 lease 防重复生成。
- 检查更新用户侧接口 `GET /api/app/update` 只读取 `APP_ANDROID_*` 环境变量，不提供发布、停更或历史版本管理。
- 后台操作审计已新增 `admin_audit_logs` 和 `GET /admin-api/v1/audit-logs` / `GET /internal/admin/audit-logs` 查询入口；后台登录、查询、客服回复、今日农情、检查更新、礼品卡生成 / 查询 / 作废 / 用户兑换等会记录操作元信息；不记录正文、图片 URL、手机号、token 或密钥。
- 会员 / 加油包 / 升级的后端开发期接口默认关闭，Android 也不会调用；它们不是正式支付后台。
- 礼品卡后端表、用户侧 `POST /api/gift-cards/redeem`、后台批次生成、完整卡码加密保存后页面查看复制、卡状态查询、未兑换卡作废、兑换尝试查询、失败原因聚合和 Android 礼品卡兑换入口均已接入；完整卡码不批量导出。
- Android 没有后台入口，也不调用任何 `/internal/*` 接口。

已排查的旧方案：

- 没有发现旧网页后台、旧 `/admin` 或 Android 端后台入口并存；`/internal/admin/audit-logs` 是当前新落的审计查询，不是旧后台。
- debug-only 预览面板里的会员成功、礼品卡成功、帮助与反馈样例都只是 UI 预览，不是后台。
- `support` 内部命名里的 `admin/system` 只是消息来源枚举，不代表已经有坐席后台或工单系统。

当前仍需继续补：

- 帮助与反馈后台：现有会话列表、详情、回复、状态队列、搜索、关闭和重开，后续补正式坐席分配、标签、站外通知和消息保存 / 删除规则。
- 用户详情页：现有账号ID / 脱敏手机号查询、授权角色查看完整手机号、会员、额度、扣次流水、反馈、订单、礼品卡和 App 日志，后续补更多筛选和导出审批。
- 检查更新发布页：当前版本、APK 链接、文件大小、SHA-256、是否启用、停更和发布审计；后续建议用 `app_releases` 表替代长期手改环境变量。
- 今日农情状态页：当天状态、失败原因、来源链接、手动补跑、停用当天卡片和审计。
- 礼品卡后续补批量发放、发放对象管理、商务渠道和更细风控；支付通过后再接正式订单、支付回调、退款、对账和人工补偿入口。

上线前必须注意：

- `SUPPORT_ADMIN_SECRET` 和 `DAILY_AGRI_JOB_SECRET` 是共享密钥，不是后台账号或角色权限体系；可选 `X-Admin-Actor` 只用于审计标记，不等于身份认证。
- 高风险操作不能只靠前端隐藏按钮，必须服务端鉴权、服务端授权和服务端审计。
- 补权益、发礼品卡、作废礼品卡、停更新、导出 / 删除用户数据等动作必须二次确认并落审计。
- 后台密钥、数据库密码、短信密钥、支付密钥不能写进仓库。

详情入口见 [management-backend.md](D:/wuhao/docs/runbooks/management-backend.md)。

### 12. 支付真实接入

结论：当前支付链路仍是安全占位状态，可以继续内测展示，但不能收费。Android 不调用订单接口，后端开发期订单接口默认关闭，生产环境还会强制关闭；真正接支付时必须改成“服务端创建订单 + 支付渠道异步通知 + 服务端验签回调 + 幂等发权益 + 对账”。

当前代码真相：

- Android 会员开通、升级和加油包按钮只提示“支付功能暂不可用”，不会调用后端订单接口。
- 后端仍有 `/api/tier/renew_plus`、`/api/tier/renew_pro`、`/api/tier/upgrade_plus_to_pro`、`/api/topup/buy`，但只是本地 / 内测开发期直改接口。
- 开发期订单接口默认返回 `PAYMENT_NOT_CONFIGURED`。
- 只有显式设置 `ALLOW_DEV_ORDER_ENDPOINTS=true` 且当前环境明确为 `APP_ENV / ENV / GO_ENV = local / dev / development / test` 时，开发期订单接口才会放行；缺失环境名也按关闭处理。
- 生产环境或环境名缺失时，即使误设 `ALLOW_DEV_ORDER_ENDPOINTS=true`，后端也会强制关闭开发期订单接口。
- 当前 `orders` 表只记录开发期成功结果，不是正式支付订单表。
- 当前没有真实支付 SDK、支付渠道、自动续费、退款或对账流程。

上线前必须注意：

- 不能把开发期订单接口打开当正式收费。
- 不能信任 Android 上报的“支付成功”；会员 / 加油包权益只能由服务端已验签支付结果触发。
- 正式支付必须校验订单号、支付渠道订单号、金额、商品类型、用户、支付状态和幂等键。
- 支付回调可能重复到达，权益发放必须幂等。
- 自动续费当前不存在；如果未来接入，必须单独补签约、解约、续费通知、扣款失败、协议文案和隐私清单。

买服务器后必须补：

- 选定支付渠道和商户主体，配置支付回调公网 HTTPS 地址。
- 新增正式订单表、支付创建接口、支付回调接口、支付状态查询接口和回调验签 / 解密能力。
- 在事务内发放 Plus、Pro、Plus 升 Pro 补偿或加油包，并写订单流水和权益发放结果。
- 管理后台补订单查询、退款 / 对账状态、人工补偿和审计。
- 更新协议、隐私政策、风险提示、第三方服务清单、支付 / 退款 / 自动续费说明和应用商店材料。

详情入口见 [payments.md](D:/wuhao/docs/runbooks/payments.md)。

## 后续待巡检功能队列

- 手机号登录真实实现：短信服务、验证码限流、token 签发、旧本机 `user_id` 迁移。
- 管理后台真实实现：后台账号、权限、审计、客服、用户、订单、礼品卡、更新和今日农情页面。

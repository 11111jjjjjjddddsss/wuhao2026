# 买服务器前功能巡检记录

最后更新：2026-05-22

## 目的

在正式购买云资源前，按功能逐个深度巡检 Android 前端、Go 后端和上线依赖，避免买完服务器后才发现主链、权限、支付、存储或高并发边界没收口。

本文件只记录当前已巡检结论和后续要补的真实工作，不伪造未购买的服务器、域名、数据库、密钥或管理后台。

## 巡检原则

- 一次只收一个明确功能，先看当前代码和旧方案残留，再决定是否改动。
- 能用现有主链解决的，不另起一套新方案。
- 只把真实风险写进文档，不把“后面想做的功能”写成已经存在。
- 买服务器前不做盲目性能调参；买服务器后用 SAE / RDS / SLS 真实指标决定实例规格、连接池、索引和限流策略。

## 当前巡检进度

### 1. 会员中心与额度体系

结论：当前可以作为“占位展示 + 后端真相”继续推进，但不能在没有账号 token 和真实支付回调前开放收费。

当前代码真相：

- Android 主界面会员入口和设置页会员入口复用 `MembershipCenterBody`。
- Android 的开通 Plus、开通 Pro、升级 Pro、购买加油包按钮当前只提示“支付暂未接入”，不会调用后端订单接口。
- 后端 `/api/me` 是会员等级、到期时间、每日剩余次数、升级补偿次数和加油包次数的当前真相。
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

结论：当前 Go 后端不是“语言性能不够”，首版单实例可跑早期流量；真正风险在多 SAE 实例前的共享存储、分布式锁、迁移和监控。

当前已有保护：

- 主聊天同一用户同一时间只允许一个活跃流，依赖 MySQL `chat_stream_inflight` 和 `UNIQUE KEY (user_id)`，不是纯内存锁。
- 同一 `client_msg_id` 的问答归档由 `session_round_ledger` 防重。
- 额度扣减由 `quota_ledger` 防重复扣。
- 模型 Key 池已支持 `DASHSCOPE_API_KEY_1/2/3` 和旧列表配置，能在请求打开阶段遇到限流 / quota / 认证类错误时切换 Key；同一阿里云主账号多 Key 不扩真实 RPM / TPM。
- 今日农情生成使用数据库 lease，能防多实例重复生成同一天卡片。
- 图片上传当前限制为单张 JPEG 且 `<=1MB`，聊天图片 URL 只接受配置的公开基地址下 `/uploads/*.jpg`。

买服务器前已补的低风险优化：

- `server-go/internal/app/mysql.go` 的数据库连接池从固定 10 个连接改为可用环境变量调节，默认仍保持原值：
  - `MYSQL_MAX_OPEN_CONNS`，默认 10
  - `MYSQL_MAX_IDLE_CONNS`，默认 10，且不会超过 max open
  - `MYSQL_CONN_MAX_IDLE_SECONDS`，默认 300
  - `MYSQL_CONN_MAX_LIFETIME_SECONDS`，默认 1800

上线前必须注意：

- 如果首版不接 OSS，SAE 必须明确单实例运行；因为当前 `/upload` 写本机磁盘，`/uploads/` 也是本机静态文件，多实例下上传落到 A 实例、后续请求或模型公网拉图打到 B 实例时可能 404。
- 如果要多实例，图片必须先接 OSS 或等价共享对象存储，并用 OSS 生命周期策略处理图片保存周期。
- 多实例前不要让多个 SAE 实例首次启动同时跑迁移；数据库迁移应改成单独发布步骤，或至少加迁移锁。
- 主聊天 SSE 仍需要后续评估“单轮最大生成时长”，避免上游极端卡住长期占用 goroutine 和 inflight；不要用全局 `http.Client.Timeout` 直接砍流式请求。
- B/C 摘要的 `running` guard、聊天本地限流、模型 Key 冷却都是本进程级；多实例下可能重复摘要或限流倍增。写回已有 `round_total` 校验，所以主要风险是成本和抗刷，不是旧摘要覆盖新轮次。
- 归档成功后、扣次前如果进程崩溃且短重试也失败，仍可能漏记一次成本；正式上线后要靠日志和对账巡检兜底。

买服务器后用真实指标决定：

- SAE 实例规格和实例数。
- RDS MySQL 规格、最大连接数、连接池参数、慢查询和索引调整。
- 是否接 Redis / 网关做分布式限流。
- 是否为 B/C 摘要加数据库 claim / lease，避免多实例重复调用 Qwen3.5-Flash。
- 是否补持久化模型调用 attempt / status 表，进一步压低极端重复开流或漏扣成本。

建议上线观察指标：

- 主聊天 SSE 中断率、上游 429 / 5xx、上游打开失败率。
- `append session round after stream failed`、`quota consume on DONE failed`、`summary extraction failed`。
- 图片上传失败、图片 URL 404、帮助与反馈发送失败。
- RDS 会话连接、连接数利用率、TPS / QPS、慢查询、行锁、IOPS、CPU / 内存。

参考阿里云资料：

- [SAE 自动弹性](https://help.aliyun.com/zh/sae/auto-elasticity/)
- [RDS MySQL 监控指标](https://help.aliyun.com/zh/rds/apsaradb-rds-for-mysql/view-the-metrics-of-an-apsaradb-rds-for-mysql-instance)
- [RDS MySQL 错误日志和慢日志](https://help.aliyun.com/zh/rds/apsaradb-rds-for-mysql/view-error-logs-and-slow-logs)

### 3. 帮助与反馈

结论：当前主链能过早期内测，没有发现新旧方案并存。用户侧入口是设置页“帮助与反馈”，内部代码仍沿用 `support` 命名，不影响用户文案。

当前代码真相：

- Android 进入帮助与反馈页时拉 `GET /api/support/messages`，成功后调用 `POST /api/support/read` 标记后台 / 系统消息已读。
- 设置页红点由 `GET /api/support/summary` 的 `unread_count` 决定，只统计 `sender_type IN ('admin', 'system') AND read_by_user_at IS NULL`。
- 用户发送走 `POST /api/support/messages`；后台回复走 `POST /internal/support/messages`，后台读取走 `GET /internal/support/messages?user_id=...`。
- 内部后台接口由 `SUPPORT_ADMIN_SECRET` 保护，支持 `X-Support-Admin-Secret` 或 `Authorization: Bearer <secret>`。
- 帮助与反馈图片复用主聊天图片链：相机 / Photo Picker -> App 私有 JPEG 副本 -> `/upload` -> support 消息保存 URL；单次最多 4 张。
- 附件面板打开时，系统返回、手势返回和左上角返回都会优先收起附件面板；页面使用 `imePadding()`，输入框不会被键盘盖住。
- “删除所有历史对话”不会删除帮助与反馈消息。

本轮已补的保护：

- 新增 `server-go/internal/app/support_test.go`，覆盖帮助与反馈 payload 校验、图片 URL JSON 序列化、内部后台 secret 缺失 / 正确 / 错误三种情况。

上线前必须注意：

- 公开生产前仍必须接账号 token 并启用 `AUTH_STRICT=true`；否则裸 `X-User-Id` 能读写某个用户的帮助与反馈。
- 多 SAE 实例前必须先上 OSS 或保持单实例；帮助与反馈图片同样依赖当前 `/upload` 本机磁盘和 `/uploads/` 静态读取。
- `SUPPORT_ADMIN_SECRET` 只能配置在服务端环境变量或未来管理后台后端，不能进 APK，不能写仓库。
- 当前内部后台接口只有共享 secret，没有后台账号、角色权限、IP 限制和审计；公开运营前至少要补最小后台或内网脚本。

买服务器后必须补：

- 配置 `SUPPORT_ADMIN_SECRET`、正式 `BASE_PUBLIC_URL / UPLOAD_BASE_URL`、SLS 日志。
- 管理后台最小版优先做：按用户查看会话、回复、未读 / 未处理列表、搜索、处理状态、审计日志。
- 账号注销 / 数据删除规则里明确帮助与反馈消息和图片是否删除、保留多久、由谁操作。
- 如果图片进入 OSS，补 OSS 生命周期策略；若涉及支付截图 / 隐私截图，后续评估私有读或后台受控查看。

建议上线观察指标：

- `get support summary failed`、`list support messages failed`、`create support message failed`、`mark support messages read failed`。
- 帮助与反馈图片上传失败、support 图片 URL 404、内部后台回复失败。

### 4. 礼品卡

结论：当前是纯前端占位页，不是可用兑换系统。没有发现真实后端兑换接口、兑换码旧链路或会误改会员权益的并存方案。

当前代码真相：

- 设置页入口只显示“礼品卡”三个字，进入右进左出的轻量子页。
- 礼品卡页输入框不放占位提示，前端不限制字符类型、大小写或长度；输入为空时“兑换”按钮禁用。
- 输入内容后点“兑换”或键盘 Done，只提示“礼品卡功能后续接入”，不调用后端，不修改会员、额度、加油包、订单或 `quota_ledger`。
- debug-only 预览面板里有“兑换成功 / 确定”卡片样式，但真实礼品卡页没有后端成功结果时不会弹成功卡片。
- App 内协议 / 隐私 / 风险文案已说明支付和礼品卡未正式接入前，不产生真实兑换权益。

上线前必须注意：

- 真实礼品卡接入前，不能把预览面板的“兑换成功”样式接到真实按钮上，不能假成功。
- 礼品卡规则必须以后端为唯一真相：卡类型、权益内容、有效期、可兑换次数、是否可叠加、是否绑定用户、是否可作废、异常处理都由后端控制。
- Android 只能提交用户输入并展示后端结果；不能在客户端判断礼品卡有效性或直接发权益。
- 后续账号注销 / 数据删除规则里要明确礼品卡兑换记录是否保留以及保留多久。

买服务器后必须补：

- 新增后端礼品卡表和兑换记录表，至少包含卡号 / 哈希、状态、权益类型、权益数量或会员档位、有效期、发放来源、兑换用户、兑换时间、作废时间、操作人和备注。
- 新增用户侧兑换接口，例如 `POST /api/gift-cards/redeem`，必须鉴权、幂等、防重复兑换、防撞库、服务端事务发权益。
- 新增管理后台或脚本：生成、发放、查询、作废、导出、查看兑换记录，所有操作要有审计。
- 配置限流和日志：连续兑换失败、撞库、已兑换、已过期、权益发放失败都要能查。
- 真兑换成功后，Android 才能弹“兑换成功 / 确定”卡片，并刷新 `/api/me` 同步会员中心。

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

- APK 建议放 OSS / CDN / 自有静态 HTTPS，不建议让 Go / SAE 动态服务大 APK。
- 签名证书必须固定；如果新 APK 换签名，Android 系统会拒绝覆盖安装。
- `versionCode` 必须单调递增；已经安装坏包的用户不能用低版本覆盖，只能再发更高 `versionCode` 的修复包。
- `force_update` 只是 App 内弹窗不显示“稍后”，不是系统级强制升级，仍不能绕过用户安装确认。
- 国内不同 ROM 的未知来源授权页、系统安装器、下载失败表现都可能不同，第一版正式更新必须真机验证。

买服务器后必须补：

- 配置正式域名 / HTTPS 和 APK 静态分发位置。
- 发布每个 APK 时记录 `versionCode`、版本名、文件大小、SHA-256、签名证书指纹、下载链接和发布时间。
- SAE 环境变量配置 `APP_ANDROID_*` 后，用旧包真机跑完整链路：检查更新 -> 发现新版本 -> 授权安装未知应用 -> 下载 -> 校验 -> 系统安装页 -> 覆盖安装成功。
- 管理后台后续补“当前发布版本 / APK 链接 / 哈希 / 是否启用更新 / 一键停更 / 发布审计”。

建议上线观察指标：

- `/api/app/update` 5xx、配置了新版本但客户端无更新、APK 下载失败、文件大小或 SHA-256 校验失败、包名 / versionCode 校验失败、系统安装页打开失败。

### 6. 服务协议 / 隐私政策 / 风险提示

结论：当前这组页面主链可过早期内测，没有发现旧 WebView / 外部网页 / 旧平铺入口和当前目录页并存。正文口径与当前 Manifest 权限、图片入口、支付 / 礼品卡未接入状态基本一致。

当前代码真相：

- 设置页只有一个“服务协议”入口，进入本地内置目录页。
- 目录页包含 6 个二级页面：用户协议、隐私政策、第三方信息共享清单、个人信息收集清单、应用权限、风险提示。
- 6 个二级页面都走设置页右进左出的页面栈；左上角返回和系统返回键会先回到服务协议目录，再回到设置菜单。
- Android Manifest 当前只声明 `INTERNET / ACCESS_NETWORK_STATE / REQUEST_INSTALL_PACKAGES`。
- App 当前不申请定位、App 相机、相册 / 存储读写、录音、通讯录、短信或通知权限。
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

## 后续待巡检功能队列

- 主聊天与图片发送：SSE、WorkManager、上传失败、历史恢复、删除历史对话。
- B/C 记忆与模型调用：摘要触发、失败重试、C 层 20 轮归档、锚点注入。
- 今日农情：生成任务、强制搜索、来源过滤、失败静默。
- 账号 / 手机号登录：本机 `user_id` 迁移、token、`AUTH_STRICT`。
- 统一管理后台：客服、用户、会员、礼品卡、更新、今日农情和日志入口。

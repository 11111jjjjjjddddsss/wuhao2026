# 农技千查仓库主规则

本文件是本仓库唯一主规则文档。以后任何 GPT、Codex、子窗口、接手窗口，在开始改代码前都先看这里。

当前仓库实际只有两条主线：
- Android 客户端：`app`
- Go 后端：`server-go`

历史调参、旧链路、旧口径都留在 git 历史里，不再继续堆在本文。

## 1. 文档规则

- 规则文档只保留这一份：[AGENTS.md](D:/wuhao/AGENTS.md)
- `app/AGENTS.md`、`server-go/AGENTS.md` 允许存在，但只作为各自目录树内的局部执行补充，不承担仓库主规则职责，口径必须服从根 [AGENTS.md](D:/wuhao/AGENTS.md)
- [docs/chat-ui-dynamic-interaction-logic.md](D:/wuhao/docs/chat-ui-dynamic-interaction-logic.md)、[docs/chat-ui-clean-state-checklist.md](D:/wuhao/docs/chat-ui-clean-state-checklist.md)、[docs/backend-boundaries.md](D:/wuhao/docs/backend-boundaries.md) 只作参考，不再承担主规则职责
- [docs/project-state/current-status.md](D:/wuhao/docs/project-state/current-status.md)、[docs/project-state/open-risks.md](D:/wuhao/docs/project-state/open-risks.md)、[docs/project-state/pending-decisions.md](D:/wuhao/docs/project-state/pending-decisions.md)、[docs/project-state/recent-changes.md](D:/wuhao/docs/project-state/recent-changes.md) 是项目交接记忆，不是主规则；后续任何 Codex 都应主动读取并自动维护
- [docs/adr](D:/wuhao/docs/adr) 用于沉淀“为什么这么定”；[docs/runbooks](D:/wuhao/docs/runbooks) 用于沉淀运维和排障入口；二者都必须和当前代码保持一致
- 参考文档允许保留历史分析，但必须明确标注“历史归档 / 仅供参考”，不允许继续冒充 active 真相
- 规则变更、实现边界变化、唯一真相变化，必须同次同步更新本文件
- 如果本文件与当前代码不一致，优先修本文，不允许放着过期规则不管

### 1.1 项目记忆机制

目标：
- 让新开的 Codex 窗口在不依赖用户重复口述的前提下，快速接上当前项目真相
- 让“当前状态 / 风险 / 待决策 / 运维入口 / 关键历史决策”都固化在仓库里，而不是散落在聊天记录里

默认读取顺序：
1. 根 [AGENTS.md](D:/wuhao/AGENTS.md)
2. 当前工作目录命中的局部 `AGENTS.md`
3. [docs/project-state/current-status.md](D:/wuhao/docs/project-state/current-status.md)
4. [docs/project-state/open-risks.md](D:/wuhao/docs/project-state/open-risks.md)
5. [docs/project-state/pending-decisions.md](D:/wuhao/docs/project-state/pending-decisions.md)
6. [docs/project-state/recent-changes.md](D:/wuhao/docs/project-state/recent-changes.md)
7. 当前任务相关的 ADR / runbook / 参考文档

自动维护规则：
- 以上项目记忆文件默认由 Codex 自己维护，不要求用户手工更新
- 每次任务完成后，若涉及当前状态、风险、待决策、运维入口、方案取舍、历史方案淘汰，必须同次同步更新对应文档
- 协作方式、交付闭环、GitHub 推送要求或“用户默认不懂代码 / 运维”的长期口径发生变化时，也要同次同步更新仓库内规则或项目记忆，不能只改聊天提示词
- 已废弃方案不能与现方案并列长期共存；若保留历史说明，必须明确标记“已废弃 / 仅供参考 / 被什么替代”
- `current-status.md` 只保留当前真相，不堆历史流水账
- `open-risks.md` 只保留未关闭风险；已解决项应移出或转入变更记录
- `pending-decisions.md` 只保留仍待拍板事项；已定事项转 ADR 或变更记录
- `recent-changes.md` 记录最近一段时间的重要变更，默认保留最新 20 条；更早内容以 git 历史和 ADR 为准
- 运维动作若新增脚本、命令、平台入口或回滚方法，必须同步更新对应 runbook
- 若发现记忆文件失真、过期、互相矛盾，修正文档本身属于本次任务的一部分，不能留到以后
- 聊天窗口、线程摘要、中转站压缩摘要和外部模型转述只能作为线索，不能替代仓库真相；凡是会影响后续执行的长期口径、排障结论、上线风险、SDK 接入边界、模型策略、云资源状态或用户明确拍板，必须沉淀到 `AGENTS.md`、`docs/project-state/*`、ADR 或 runbook。后续窗口如果只读到压缩摘要，要回到仓库文档和当前代码核验，不允许直接按摘要改业务代码
- 用户给出 Codex 线程 ID 时，优先使用线程读取工具提取旧窗口摘要和关键回合；读取结果仍只能作为线索，必须对照当前仓库文档、代码、git 历史和线上状态核验后，才允许更新项目记忆或修改业务代码。不要假设新窗口能自动知道所有旧线程 ID；如果用户没给 ID，只能从最近线程列表或仓库记忆里搜索线索

### 1.2 长期协作口径

- 默认把用户视为业务负责人，而不是技术执行者；用户不懂代码和运维时，Codex / OpenCode 要承担技术负责人职责，先给推荐结论和白话解释，再在高风险事项上请求确认。
- 除非用户明确要求“先别改”“只审查”“只讨论方案”，否则默认直接落地最小改动，不只停留在建议层。
- 每次完成仓库改动后的最终汇报固定包含四项：`改了什么`、`为什么这样改`、`验证了什么`、`还有什么风险或下一步`。
- 只要本次任务改了仓库代码或项目文档，默认同步项目记忆文件，并提交本地 git 后推送到 GitHub `origin/master`；如果因为网络、权限或用户明确暂停而没推送，必须直接说明阻塞原因。
- 需要跨窗口长期生效的协作规则，优先固化到 `AGENTS.md`、`docs/project-state/*`、ADR、runbook 或项目级桥接文档，不只留在聊天记录或本机个性化提示词里。

### 1.3 耐久型个性化协作规则

- 个性化提示词和仓库规则要尽量写成 1-2 年后仍能成立的工作方式，不要塞端口、版本号、备案状态、云资源 ID、模型版本、价格、审核进度等容易过期的事实；这些动态事实应放进 `docs/project-state/*`、ADR 或 runbook，并以当前代码和实际环境复核。
- 用户默认不需要理解实现细节。遇到复杂代码、云资源、发布、支付、日志、成本、安全、合规或模型链路问题时，Codex 要先替用户收敛成“推荐怎么做、为什么、风险是什么、还差什么外部条件”，再执行可安全落地的部分。
- 对用户已经明确定稿或强调“不要擅自改”的内容，必须先讨论再动手。当前长期保护项包括：主对话锚点、记忆文档提示词、今日农情提示词、官网首页定稿文案、主聊天滚动主方案，以及“模型输出方向只靠提示词控制、不新增后端输出硬限制”的口径。
- Android 新版本发布必须等用户明确口令。用户没有明确说“发布新版本 / 打正式包 / 对外下发 / 配置检查更新 / 上传应用商店”时，只能做代码修改、测试、提交、推送和必要的后端 / 后台运维收口；不要生成并对外发布正式 release APK，不要配置公开下载地址，不要启用检查更新下发，也不要替用户发应用商店版本。
- UI / 文案 / 状态改动要站在普通用户和代理测试视角复查，不只看正常路径；凡是 Android 用户可见 UI、文案、隐藏态或异常态变化，都要同步检查 debug-only 预览面板，确认测试包除预览面板和调试日志外与正式包主链一致。
- 当用户说“继续推进 / 全面检查 / 拉代理 / 往正式上线标准推进”时，默认目标是提高正式可用性、稳定性、安全性、可运营性和低成本运维能力；可以并行拉子代理巡检，但主窗口必须自己核验、取舍和收口，不能把未经核验的代理建议直接落地。
- 若代理或外部模型建议与用户拍板、仓库规则或当前代码真相冲突，先记录为“待讨论建议”或明确丢弃，不要为了显得更安全而反复堆新限制、新链路或新规则。

## 2. 产品与系统真相

项目名称：农技千查

产品定位：
- 农业 AI 问诊系统
- 用户通过文字、图片、图文混合咨询农作物问题
- AI 只提供农业技术参考建议，不提供绝对诊断

系统边界：
- 前端只负责 UI、输入、展示、交互
- 后端是唯一真相来源
- 以下业务逻辑必须由后端控制：用户鉴权、会员等级、调用次数、上下文组装、模型调用、成本统计
- Android 客户端禁止保存、注入或使用模型服务 API Key；主模型和摘要模型调用只能由后端发起，不允许重新引入客户端直连模型链
- 手机号账号骨架已开始落地：后端新增 `app_accounts`、`auth_sessions` 和 `user_id_migrations`，手机号登录成功后会按手机号 HMAC hash 归一到稳定 `acct_...` 账号ID，并签发带 `session_id` 的长期 v2 bearer token；`AUTH_SESSION_DAYS` 默认 3650 天，当前按“长期保持登录、省认证次数”口径处理，但已提供 `POST /api/auth/logout` 吊销当前设备 session，Android 账号管理页“退出设备”会调用该接口并清本地 auth token；完整设备管理 / 远程吊销后续再迭代。全局长期业务身份统一收敛到账号ID `acct_...`；会员、每日额度、加油包、礼品卡、订单、帮助反馈、App 日志、记忆文档和聊天归档都应归属于这个账号ID。底层表和 API 字段仍可能叫 `user_id`，但生产语义是账号ID；手机号是登录凭证和回访线索，不作为业务主键。`app_accounts` 保存 `phone_hash`、`phone_mask` 和用 `APP_SECRET` 派生密钥加密的 `phone_ciphertext`；后台仅授权角色可在用户 / 反馈详情中查看和复制完整手机号，日志、审计、SLS、Redis、文档和聊天输出不得打印完整手机号。旧账号若没有加密手机号字段，需用户下次短信验证码登录后补齐。Android 登录后使用账号ID，登录前本机 UUID 只作为旧身份迁移桥。当前仍保留裸 `X-User-Id` 兼容本地 / 迁移期调试；生产若配置 `AUTH_STRICT=true`，必须同时配置 `APP_SECRET`，此时裸 `X-User-Id` 会被拒绝，旧版无 `session_id` bearer token 默认也会被拒绝，只接受可查库吊销的 v2 session token；确需迁移兼容旧 bearer token 时必须显式配置 `AUTH_ALLOW_LEGACY_TOKEN=true`
- Android 已移除 `SESSION_API_TOKEN` 静态共享 token 运行时绕过，不再用测试 ID 作为登录桥接；正式登录必须使用后端按真实手机号账号动态签发的 per-user session token。Android 普通短信验证码登录 payload 会提交旧本机 `legacy_user_id` 作为迁移桥，后端只接受本机 UUID 形态 legacy ID 或可由旧 bearer token 证明的 legacy ID，不接受 `acct_...` 当 legacy bridge，避免账号互相合并；本机旧 `user_id` 到账号ID的数据迁移已经覆盖记忆文档、聊天归档、会员 / 额度 / 加油包 / 订单、帮助反馈、App 日志、礼品卡和兑换尝试、注销申请等长期业务数据。生产正式 token 和 Android 本地登录态都只允许 `acct_...` 账号ID，严格鉴权下非账号 session 会被拒绝；2026-06-11 已清理生产库旧测试本机 ID 残留，2026-06-15 只读巡检显示生产库已有首个 `acct_...` 账号，当前 `app_accounts=1 / auth_sessions=11 / auth_sessions_active=5 / 非 acct 用户资产归属=0 / acct 孤儿记录=0 / 缺加密手机号账号=0`。阿里云短信签名 `北京农技千问科技` 和验证码模板 `SMS_507135108` 已通过审核，用户已购买普通短信服务国内通用短信套餐包；后端普通短信链路走 `dysmsapi.SendSms`，验证码只存 Redis HMAC 摘要，模板变量传真实 `{"code":"123456"}`，账号登录成功后才清验证码，供应商返回超时 / 异常时不主动清刚缓存的验证码；当前 `/healthz` 显示 `dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok`，其中服务端旧 `/api/auth/fusion/*` 默认返回 `410 fusion_auth_disabled`，只有显式 `AUTH_FUSION_COMPAT_ENABLED=true` 才临时兼容历史包，新 Android 包不调用。Android 新包只保留普通短信验证码登录入口，登录页显示“农技千查 + 图标”、手机号、验证码、发送、登录和协议勾选；发送按钮成功后 60 秒倒计时，后端默认同一手机号 10 分钟最多 5 次发送、同一 IP 10 分钟最多 20 次发送、验证码 5 分钟有效。Android 已删除阿里云融合认证 AAR、融合 Activity、融合客户端调用、`READ_PHONE_STATE / ACCESS_WIFI_STATE / CHANGE_NETWORK_STATE` 和运营商取号明文域名例外，不再弹阿里云 SDK 页面或图形验证；未勾选协议时不请求后端、不初始化身份、不补报崩溃日志；登录页 / 短信阶段崩溃会在下次启动通过 `auth.app_crash` 上报安全摘要。已暴露过的主账号 AccessKey 上线前必须轮换。

Android 构建链：
- 对外安装身份：`applicationId = com.nongjiqiancha`，App 名称“农技千查”；Kotlin 源码包 / Gradle namespace 暂保留 `com.nongjiqianwen` 作为内部代码命名空间，不决定备案或安装身份。旧内测包 `com.nongjiqianwen` 不能通过检查更新覆盖安装成新包 `com.nongjiqiancha`，测试机需卸旧包重装；Android 构建固定使用 `UPLOAD_BASE_URL=https://api.nongjiqiancha.cn`，Android Studio 直接 Run 的 debug 包和正式 release 包都接正式 HTTPS 后端；`USE_BACKEND_AB` 固定开启，不再通过 Gradle 参数关闭后端主链或切换业务后端地址。debug 包与正式包的业务链路保持一致，区别只保留 debug-only 预览面板和调试日志；本机存在固定 release 签名配置时，debug 包也使用同一把 release 签名，确保 Android Studio 日常测试包和正式包同包名、同签名、同后端、同短信验证码登录链路。正式 release 构建必须配置固定签名，且 `UPLOAD_BASE_URL` 必须保持 https 生产地址，避免打出不接后端的正式包
- Android 首次隐私同意当前合并在登录页协议勾选中：未同意时可以看到登录页 UI，但不会初始化 `IdManager`、不会补报待发送崩溃日志，不会请求后端；用户勾选后才记录同意并进入正常登录链路，遗留后台待发送图文 Worker 也仍然等同意后才发起业务请求。
- Android 当前不再集成阿里云融合认证 SDK / AAR；`FusionOneLoginClient.kt`、`FusionAuthProtocolActivity.kt`、融合 Activity、融合 ProGuard 规则、融合构建开关和运营商明文网关例外均已删除。登录只走 Compose 自有手机号验证码表单和后端 `/api/auth/sms/send`、`/api/auth/sms/login`。后续登录排障优先看 App 日志 `auth.sms_send_failed`、`auth.sms_login_failed`、`auth.app_crash` 和后端 `sms send failed` / `sms login verify failed`，不要再按融合认证 SDK 页面、号码认证或图形验证方向排查新包。
- 正式签名：本机 release 签名配置保存在 `%USERPROFILE%\\.nongjiqiancha\\android-release-signing.properties`，备案 / 上架用公钥与指纹信息保存在 `%USERPROFILE%\\.nongjiqiancha\\android-release-public-info.txt`；私钥、密码和签名配置不进入仓库、聊天记忆或项目文档。正式 release 包必须使用这把固定签名
- Gradle wrapper：8.13
- Android Gradle Plugin：8.13.2
- Kotlin Android / Compose Compiler Gradle plugin：2.1.21
- Compose 编译已走 `org.jetbrains.kotlin.plugin.compose`，不再使用旧 `composeOptions.kotlinCompilerExtensionVersion`
- 图片全屏预览使用 Telephoto `zoomable-image-coil:0.19.0`，当前仍是 Coil 2 依赖线
- 带图片发送的延迟后台兜底使用 AndroidX WorkManager `work-runtime-ktx:2.11.2`；debug / release merged manifest 会包含 WorkManager 合并的 `WAKE_LOCK / RECEIVE_BOOT_COMPLETED / FOREGROUND_SERVICE` 等后台任务相关权限，当前应用权限页和隐私政策按“带图待发送任务在后台 / 进程恢复 / 重启后有限重试”口径说明，不把它写成 App 外推送、广告通知、定位、通讯录或短信能力

部署与基础设施：
- 部署：首版线上后端路线已从“SAE 镜像托管优先”转向“ECS 传统部署优先”。当前已购买阿里云 ECS `i-2ze5nrem0jrchln4f0eh`，位于 `华北2（北京）/ cn-beijing` 可用区 L，规格 `ecs.u1-c1m2.large`（2 vCPU / 4 GiB），Ubuntu 22.04 64 位，包年包月到期 `2027-06-01T16:00Z`，公网 IP `39.106.1.151`，私网 IP `192.168.1.237`，生产 VPC `nongjiqiancha-prod-vpc` / `vpc-2zeax2zowza2398b9dzot`，生产交换机 `nongjiqiancha-prod-beijing-l` / `vsw-2zemsq82lj2kp8za90aky`，安全组 `sg-2ze4tilwxw1h5w77lwl1`，固定公网带宽 5 Mbps。2026-05-30 已清理北京区空闲默认 VPC 和旧 SAE 自动交换机，当前北京区只保留这一条生产 VPC / 交换机。ECS 已通过 Cloud Assistant 初始化 `nongji` 系统用户、`/opt/nongjiqiancha/server`、`/etc/nongjiqiancha/server.env`、历史 `nongji-server.service`、双端口 slot `nongji-server-3000.service` / `nongji-server-3001.service`、Nginx 反向代理、基础限流、fail2ban 和 logrotate；`server-go` 已部署为单 ECS 双端口 slot，Nginx 在 `127.0.0.1:3000` / `127.0.0.1:3001` 之间切换 `api.nongjiqiancha.cn` 上游，Go 进程默认监听 `127.0.0.1:${PORT:-3000}`，只有显式配置 `LISTEN_ADDR` 或 `LISTEN_HOST` 才会改为其他监听地址，避免绕过 Nginx 直接暴露业务端口；生产健康检查以 readiness 脚本为准，当前硬门槛为 `ok=true / auth_strict=true / bailian=ok / sms=ok / dev_order_endpoints=false / redis=ok / upload_storage=oss`，`dypns*` 健康字段只作旧包兼容状态参考。阿里云 DNS 已创建 A 记录 `api.nongjiqiancha.cn / nongjiqiancha.cn / www.nongjiqiancha.cn -> 39.106.1.151`；2026-06-05 已通过 Let’s Encrypt / certbot 为 `api.nongjiqiancha.cn` 配置 Nginx 443 HTTPS，公网 `https://api.nongjiqiancha.cn/healthz` 返回 200，证书自动续期 timer 已启用；2026-06-06 已把 API HTTP 80 改为只保留 ACME challenge、其他请求 301 跳 HTTPS，不再直接反代业务 API；2026-06-06 已为 `nongjiqiancha.cn` / `www.nongjiqiancha.cn` 配置 Nginx 静态官网和 HTTPS，公网根域名 HTTP 跳转 HTTPS，HTTPS 首页返回 200；网站 ICP 备案已于 2026-06-05 管局审核通过，主体备案号为 `京ICP备2026031728号`，网站备案号为 `京ICP备2026031728号-1`，网站名称“农技千查”，域名 `nongjiqiancha.cn`；App 备案已通过，App 备案号为 `京ICP备2026031728号-2A`，Android 设置页底部低调展示该编号，协议 / 隐私基础信息也展示该编号；2026-06-10 网站公安联网备案号已下发：`京公网安备11010602202723号`；App 公安备案待按最终 App 信息在全国互联网安全管理服务平台提交，按网站 / App 开通后 30 日内处理；公安备案数据码不进入仓库；2026-06-05 已通过 Cloud Assistant 将 DashScope 主 / 副模型 Key 写入 ECS `/etc/nongjiqiancha/server.env` 主备槽位并重启服务，健康检查显示 `bailian=ok`，真实 Key 值不进入仓库、文档或聊天记忆；公网完整上线仍等 App 公安备案和真机登录 / 主聊天 / 图片问诊回归。此前曾在 `华北2（北京）/ cn-beijing` 创建标准版 SAE 应用 `nongjiqiancha`，AppId `366147d5-3760-4548-bd68-f38debbc5f23`，规格 `0.5 核 / 1GB / 单实例`，自动弹性未开启；该 SAE 应用只是默认 demo 镜像，未部署 `server-go` 真实后端，已于 2026-05-24 21:50 左右先停止，并于 2026-05-24 21:51 左右通过阿里云 CLI 删除，删除变更单 `14a360d3-e2b4-4b93-9701-b76dfcc7bfd9` 已提交成功；删除后 `ListApplications` 返回空列表，`TotalSize=0`。运维侧本机已安装阿里云 CLI，默认 Region 为 `cn-beijing`；真实 AccessKey 只允许保存在本机 CLI 配置或云端密钥管理中，不允许写入仓库、聊天记忆或文档
- 2026-06-16 最新后端已通过 [scripts/deploy-ecs-server.ps1](D:/wuhao/scripts/deploy-ecs-server.ps1) 部署到 ECS，并继续使用单机双端口 slot 发布；本次部署按 `server-go/go.mod` 的 `toolchain go1.26.4` 在 ECS 上下载补丁版 Go 工具链、运行 `go test ./...` 后重新编译二进制。[scripts/check-ecs-readiness.ps1](D:/wuhao/scripts/check-ecs-readiness.ps1) 最新复查显示 Nginx active upstream 为 `3001`、`nongji-server-3001 active/enabled`、HTTPS healthz 200，后台同域 `/admin-api/` 上游也已跟随 active slot `3001`，未登录访问 `/admin-api/v1/auth/me` 返回 401；`nongji-server-3000` 可能在排空 / 回滚窗口保持 active 但 disabled，公网流量以 Nginx active upstream 为准。短信资质 / 签名 / 验证码模板、Redis、OSS 上传后端、DashScope 主 / 副模型 Key 主备槽位、后台监控面板、账号ID收敛迁移、礼品卡生成 / 兑换 / 追溯 / 完整卡码加密查看、帮助反馈状态队列，以及检查更新发布配置 / 发布历史均已进入生产代码 / 环境并通过健康检查；服务端旧融合认证路由默认 410 停用，公网已验证 `/api/auth/fusion/token` 返回 `410 fusion_auth_disabled`，仅显式 `AUTH_FUSION_COMPAT_ENABLED=true` 才临时兼容历史包，新 Android 包不调用。后台静态前端最近已通过 [scripts/deploy-ecs-admin.ps1](D:/wuhao/scripts/deploy-ecs-admin.ps1) 部署到 `https://admin.nongjiqiancha.cn/`，公网首页 200。2026-06-05 网站 ICP 备案已通过，`api.nongjiqiancha.cn` HTTPS 已配置并公网验证通过，2026-06-06 根域名官网 HTTPS 已部署并公网验证通过，2026-06-10 官网已补网站公安备案 footer、警徽图标和独立用户协议 / 隐私政策页并部署验证通过，API HTTP 80 已改为 ACME challenge + 301 HTTPS 跳转，DashScope 主 / 副模型 Key 已写入 ECS 主备槽位并显示 `bailian=ok`，App 备案已通过并取得 `京ICP备2026031728号-2A`，网站公安备案号已下发并已补官网 footer；公网 SSH 22 已撤销且 ECS 本机 ssh 服务已停用，当前公网只放行 80 / 443 和 ICMP；App 公安备案、短信验证码登录 / 主聊天真机回归和上线前 AccessKey 轮换仍缺
- 后端运行边界：Go 服务使用显式 `http.Server`，默认限制慢请求头、请求读取、空闲连接和最大 header，`WriteTimeout` 默认保持 0 以兼容 SSE；通用 JSON body 默认 64KiB，App 日志接口 8KiB，超限返回 `413 body_too_large`，并按 `user_id + IP` 做默认 10 分钟 60 次短期限流；帮助与反馈用户发消息接口按 `user_id + IP` 做默认 10 分钟 20 条短期限流；`/upload` 按 `user_id + IP` 做默认 10 分钟 120 次短期限流，仍只接受单张 `<=1MiB` JPEG；后台登录额外按“用户名 hash + IP hash”做默认 10 分钟 10 次专用限流，只保护 `admin.nongjiqiancha.cn` 后台口令入口，不影响 App 用户登录 / 聊天 / 验证码；主聊天应用层用户限流默认 `20 次 / 60 秒`，可用 `CHAT_RATE_LIMIT_MAX_HITS`、`CHAT_RATE_LIMIT_WINDOW_SECONDS`、`CHAT_RATE_LIMIT_PRUNE_INTERVAL_SECONDS` 调整，配置 Redis 时跨进程共享，未配置 Redis 时回退单进程并清理过期用户桶；2026-06-13 线上 Nginx 主聊天 IP 级限制已调宽到 `60r/m`、burst 80，并移除聊天 `limit_conn`，避免共享网络用户被旧 `6r/m` / 2 连接误伤；模型出站请求限制拨号、TLS 握手、响应头等待和空闲连接，主聊天 SSE 默认 30 分钟最长持续时间兜底；上游错误 / 非 SSE 预览和记忆文档摘要响应读取上限为 64KiB，今日农情 JSON 响应读取上限为 1MiB；服务启动迁移会先获取 MySQL 全局锁，迁移锁释放失败会作为启动错误暴露，早期 `004/005` 迁移里的旧 DDL 已改成 `information_schema` 条件执行但仍没有独立 `schema_migrations` 版本表
- 域名：`nongjiqiancha.cn` 已在阿里云购买，用户口头确认域名实名认证 / 模板审核已通过；当前用于 `api.nongjiqiancha.cn`、根域名官网、管理后台和后续下载域名。当前已创建 `api`、`@`、`www`、`admin` A 记录指向 ECS 公网 IP `39.106.1.151`，并已完成 `api.nongjiqiancha.cn` HTTPS / Nginx 443 反代、`nongjiqiancha.cn` / `www.nongjiqiancha.cn` 官网 HTTPS 静态站，以及 `admin.nongjiqiancha.cn` 管理后台 HTTPS 静态托管和 `/admin-api/` 同域反代；网站 ICP 备案已通过，App 备案已通过并取得 `京ICP备2026031728号-2A`，网站公安备案号已下发并已补官网 footer，App 公安备案待补，下载域名仍需后续单独配置入口和证书
- 数据库：首版使用阿里云 RDS MySQL，当前实例 `rm-2zes3vmj76p85n8g1` 已创建并运行，MySQL 8.0、基础版、1 核 2GB、50GB、内网地址 `rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`、生产 VPC `vpc-2zeax2zowza2398b9dzot`、生产交换机 `vsw-2zemsq82lj2kp8za90aky`、到期时间 2027-05-24；当前自动备份保留 7 天，默认每周二 / 四 / 六 17:00-18:00 北京时间左右执行；已创建数据库 `nongjiqiancha`、应用账号 `nongji_app` 并授予该库读写权限，RDS 白名单当前为 `192.168.1.237`，后端环境变量已写入 ECS `/etc/nongjiqiancha/server.env`。数据库密码、`APP_SECRET`、内部 job / support secret 只保存在本机 `%USERPROFILE%\.nongjiqiancha\prod-secrets.json` 和 ECS 环境文件中，不进仓库。PolarDB 暂作为后续高并发 / 更高规格升级选项，不再作为个人创业首版默认采购项
- Go 后端数据库连接池可用 `MYSQL_MAX_OPEN_CONNS`、`MYSQL_MAX_IDLE_CONNS`、`MYSQL_CONN_MAX_IDLE_SECONDS`、`MYSQL_CONN_MAX_LIFETIME_SECONDS` 调整；默认仍为 10 open / 10 idle / 5 分钟 idle / 30 分钟 lifetime。RDS 规格、后端实例数和真实监控数据确定前不盲目调参
- 资源容量与续费提醒：后续 Codex 发现 ECS / RDS / Redis / OSS / 域名 / HTTPS 证书 / 模型套餐 / 短信认证资源接近容量、用量或到期阈值时，必须主动提前提示用户升级、续费或购买资源包；CLI 能查的资源状态优先用阿里云 CLI / Cloud Assistant 查，不让用户手工点控制台。2026-06-12 已在阿里云云监控创建邮件联系人组 `NongjiQianchaOps` 和 9 条 ECS / RDS / Redis 资源水位告警，覆盖 CPU / 内存 / 磁盘 / 连接高水位，先走邮件不走短信 / 电话；2026-06-14 已通过阿里云 CLI `InstallMonitoringAgent` 给 ECS 补装 CloudMonitor C++ 插件，ECS 上 `cloudmonitor.service` / `argusagent` 已 running；ECS 系统盘已绑定普通低频自动快照策略 `sp-2ze9ufwsu2i5hxm2wmrk` / `nongjiqiancha-prod-basic-7d`，每周二、周六北京时间 04:00 创建，保留 7 天，不跨地域复制；当前巡检入口和阈值以 [docs/runbooks/resource-capacity.md](D:/wuhao/docs/runbooks/resource-capacity.md) 为准
- OSS：已购买阿里云 OSS 标准-本地冗余存储包（华北2 / 北京）100GB，资源包实例 `OSSBAG-cn-mqq4sqfvr001`，有效期 `2026-05-24T15:00:00Z` 至 `2027-05-24T15:00:00Z`，用于抵扣标准存储容量费用；Bucket `nongjiqiancha-prod` 已在 `cn-beijing` 创建，标准存储、本地冗余、私有读写，2026-06-12 已开启默认服务端加密 `SSEAlgorithm=AES256`。当前生命周期：`uploads/` 问诊上传图 3 天自动删除，`support/` 帮助与反馈图片 30 天自动删除，`test-apks/` 内部 debug 测试包 3 天自动删除，未完成分片 1 天清理。2026-06-13 起，Android 帮助与反馈图片上传会给后端 `/upload` 传 `purpose=support`，后端写入 OSS `support/` 并返回 `/uploads/support/<file>.jpg`；普通问诊图片不传 purpose，继续写入 `uploads/` 并按 3 天删除。主聊天接口只接受普通 `/uploads/<file>.jpg`，不接受 `/uploads/support/<file>.jpg`，避免客服截图误进主模型图片链。帮助与反馈文字记录、发送人、时间和已读状态仍保存在 MySQL `support_messages` / `support_conversations`，不随 OSS 图片 30 天生命周期自动删除；正式保存 / 删除 / 注销处理规则仍需按合规口径收口。内部测试包只走私有 OSS `test-apks/debug/...` 限时签名链接，不能写入官网正式下载或 App 检查更新配置。`server-go` 已新增 OSS 上传存储后端；2026-05-31 已创建最小权限 RAM 子账号 / 策略并完成上传 / 下载 / 删除冒烟测试，生产 ECS 已配置 `UPLOAD_STORAGE_BACKEND=oss`、`OSS_BUCKET=nongjiqiancha-prod`、`OSS_ENDPOINT=https://oss-cn-beijing-internal.aliyuncs.com` 和 OSS 凭证，当前 `/healthz` 返回 `upload_storage=oss`，`/upload` 会写私有 OSS。App / 模型仍通过本后端 `/uploads/<file>.jpg` 或 `/uploads/support/<file>.jpg` 读取，不把 OSS AK/SK 下发 Android；未配置 OSS 的其他环境仍可回退本机 `/var/lib/nongjiqiancha/uploads`。Redis 开源版实例 `nongjiqiancha-prod-redis` / `r-2zet46zvmoo9wu3bic` 已于 2026-05-30 购买，规格 `256MB`、Redis 7.0、标准高可用主备、包年包月到期 `2027-05-30T16:00:00Z`，位于生产 VPC / 北京可用区 L，内网地址 `r-2zet46zvmoo9wu3bic.redis.rds.aliyuncs.com:6379`，白名单只放 `127.0.0.1` 和 ECS 私网 IP `192.168.1.237`；`server-go` 已新增可选 Redis 客户端和 Redis-backed 手机号短信发送 / 登录校验、App 自动日志、帮助与反馈用户发消息、上传短期限流，服务端旧融合认证路由默认停用，只有显式历史兼容窗口才会用到 token / 登录校验频控；key 只保存 hash，不保存明文手机号、验证码、token、聊天正文、反馈正文或图片内容；主聊天流、额度、归档、摘要和订单仍以 MySQL / 现有主链为真相，不得把 Redis 写成已接管业务主链
- OSS RAM 策略状态：2026-06-13 已通过阿里云 CLI 将 `NongjiQianchaOSSUploadPolicy` 默认版本更新到 v3，仅允许访问 `nongjiqiancha-prod` Bucket 本体、`uploads/*` 和 `support/*` 所需对象操作；不要把它放宽成全 Bucket 写权限，也不要把 OSS AK/SK 下发 Android
- Redis 生产状态：ECS `/etc/nongjiqiancha/server.env` 已配置 `REDIS_ADDR / REDIS_USERNAME / REDIS_PASSWORD / REDIS_DB`，`/healthz` 返回 `redis=ok`；2026-06-12 起 `/healthz` 和后台健康状态会对 Redis 做运行期短超时 `PING`，不再只看启动时 client 是否初始化。Redis 当前只服务手机号短信验证码、手机号登录校验、主聊天用户级频控、App 自动日志接收、帮助与反馈用户发消息和上传短期限流；服务端旧融合认证短期 token / 校验缓存只在显式历史兼容窗口使用。Redis 不承载聊天正文、图片、长期画像、会员资产、额度或订单真相；App 自动日志是非关键排障链路，Redis 限流操作异常时 fail open，避免日志系统拖垮用户主体验，短信 / 登录 / 礼品卡 / 上传 / 内部 secret 等安全或成本敏感入口仍保持 fail closed
- SLS：阿里云日志服务本体已开通，`GetSlsService` 返回 `Opened`；当前未购买 SLS 节省计划 / 资源包，已创建农技千查专用 Project `nongjiqiancha-prod-1159547719787456` 和 Logstore `server-go` / `nginx-error`，通过 ECS Logtail / ilogtail 采集 Go 服务 JSON 日志和 Nginx error log，TTL 7 天；2026-06-10 已通过 [scripts/setup-sls-alerts.ps1](D:/wuhao/scripts/setup-sls-alerts.ps1) 创建 5 条最小 AlertHub 告警，覆盖 Go 5xx、Go 非 SSE 慢请求、Nginx upstream 错误、今日农情生成失败、模型 Key / 认证关键配置错误，2026-06-12 已把模型 / 认证配置告警从宽泛 DYPNS 词收窄到明确配置错误关键词；正常 200 的 `/api/chat/stream` 长连接会记录为 `http_sse_stream`，不进入 `http_request_slow`。当前 SLS 告警进入 AlertHub，并已绑定邮件行动策略 `nongji-prod-email` 和仪表盘 `nongji-prod-ops`；不启用短信、电话或机器人，避免费用和噪音；ECS / RDS / Redis 资源水位已另由云监控邮件告警承接。App 侧当前新增最小自动日志接收骨架：Android 在关键失败点自动调用后端 `POST /api/app/logs`，登录前认证失败调用 `POST /api/app/logs/preauth` 且只允许 `auth.` 事件，未登录 / 登录页阶段闪退下次启动会补报 `auth.app_crash`，已登录运行阶段闪退补报 `app.crash`；短信发送和短信登录失败会记录 `auth.sms_send_failed` / `auth.sms_login_failed`，登录网络失败会记录 `auth.login_network_failed`；清数据 / 登录后主界面和设置页排障会记录 `ui.chat_startup_state`、`ui.chat_startup_bottom_snap_done`、`ui.chat_startup_bottom_snap_pending`、`ui.settings_main_opened`、`ui.account_management_opened` 等安全诊断事件，只带布尔状态、数量和阶段；后端写入 `client_app_logs` 表并打结构化服务日志，后台 App 日志和监控面板已支持按整组事件前缀、平台、包类型、版本、系统和设备排障筛选；第一版网页管理后台已部署到 `admin.nongjiqiancha.cn`，通过后台账号 / session / CSRF / 角色校验访问 `/admin-api/v1/app-logs`、帮助与反馈、审计、今日农情、礼品卡、用户和健康等页面；`GET /internal/app/logs`、`GET /internal/admin/audit-logs` 等共享密钥接口仍保留给脚本 / 运维兼容，不给浏览器前端持有内部 secret；不做用户手动上传日志，不上传聊天正文、AI 回复全文、完整堆栈、图片内容、图片 URL、token、手机号或模型 Key
- Android “检查更新”走自有服务器 APK 分发：后端 `GET /api/app/update` 优先读取 `app_release_configs`，无数据库记录时才回退 `APP_ANDROID_*` 环境变量；后台每次保存 / 停更会同步追加 `app_release_events` 发布历史，便于追溯操作者、版本、物料状态和时间；默认只做普通更新，不启用强制更新；更新说明默认留空并统一展示“修复已知问题，优化使用体验。”；只有更高版本号、HTTPS APK、SHA-256 和文件大小都齐全时才会对外返回可用更新。Android 端下载 https APK 后会校验最终 https、文件大小、SHA-256、包名和 `versionCode`，通过后才调起系统安装确认，不做静默安装，不走应用商店主链

## 3. 模型、上下文与会员

模型：
- 主模型：Qwen3.5-Plus，用于农业问诊分析、图片理解、推理判断
- 摘要模型：固定 `qwen-plus`，用于一次性提取一份用户记忆文档；摘要请求走 OpenAI 兼容模式 HTTP 原始接口，`enable_thinking=false` 必须放在请求顶层，不联网、非流式。后端不再保留轻量摘要候选，不再保留短期 / 长期分层灰度；旧长期记忆列和旧重试列会由迁移合并进现有记忆文档 / pending 标记后删除。`qwen-plus` 资源包只适合非思考实时推理且单次输入不超过 128K 的请求；摘要通常输入大、输出短，资源包是否省钱取决于输出占比，不能因为有资源包就认为一定省钱。2026-06-10 远端实测发现放在 `extra_body` 内不会真正关闭思考，会产生大量 `reasoning_tokens` 和明显更高延迟；顶层关闭后同类摘要请求回到正常 token 量级。用户已拍板质量优先，记忆文档先统一用 `qwen-plus`。2026-06-15 起，记忆提示词按用户最终拍板收口为四块“短期记忆 / 长期记忆 / 用户画像 / 农业事件”：短期记忆接住最近上下文，长期记忆保留跨多轮仍有用的稳定信息，用户画像记录用户明确给出的个人信息、地区、背景、偏好和限制，农业事件承接后续可能追踪的具体农事线索。提示词明确可利用摘要输入里的 `time` / `region`，关键症状、处理反馈、复查变化等尽量带已有日期 / 时间 / 时间范围；地点可信度低或未知时只作低可信背景。主对话模型对用户图片的客观描述若对后续有用，可作为“图片可见 / 本轮图片显示 / 用户图片中可见”的线索进入农业事件，但不得保存图片 URL / 文件名，也不得把图片线索写成确诊结论。用户没自称时，不把“家里种地 / 几亩地”自动改写成农户、种植户等身份标签；第三方转述保留来源和不确定边界；剂量、面积、倍数、浓度、兑水量、时间等关键参数照抄不换算；没有复查、检测或人工确认时，不把倾向判断写成诊断定论。后端不解析四块、不按内容卡扣，非空模型输出整体覆盖写入；“不能坑用户 / 不要过度压模型”等协作原则保留在项目文档和执行口径，不写进生产提示词。2026-06-12 已新增内部只读记忆探针，使用合成样本真实调用 `qwen-plus` 但不写用户数据，用于抽查四块、reasoning tokens 和模型机制泄露
- 当前所有真实模型调用统一显式设置 `temperature=0.8`：主对话、记忆文档摘要、今日农情生成都走后端同一个温度常量；`top_p / max_tokens / penalty` 等其他采样参数暂不显式设置，继续走模型服务默认值
- 后端模型 Key 池支持 `DASHSCOPE_API_KEY_1/2/3`、旧 `DASHSCOPE_API_KEY` 和 `DASHSCOPE_API_KEYS` 逗号 / 分号 / 换行列表，自动去重；当前生产按用户拍板使用 `DASHSCOPE_KEY_SELECTION_MODE=fallback`，也就是 `DASHSCOPE_API_KEY_1` 主钥匙优先吃满，`DASHSCOPE_API_KEY_2` 只做副钥匙失败兜底，不再因短窗口请求量或 token 用量阈值主动轮询分流。主对话、记忆文档摘要和今日农情共用该池。若模型请求打开阶段遇到 `401 / 403 / 429` 或带限流 / quota 语义的 `400`，后端会在流开始前切到下一把 Key，并把触发限流的 Key 短暂冷却，默认 1 秒，可用 `DASHSCOPE_KEY_COOLDOWN_SECONDS` 调整；一旦 SSE 流已经成功开始，不在同一条回复中途切 Key。后续如果真实流量长期打满主钥匙，再评估改回 `auto` 或 `round_robin` 做主副轮换。扩真实并发必须使用不同阿里云主账号的 Key；同一主账号下多个 API Key 共享该账号 RPM / TPM 限流，只适合轮换或应急，不算扩容
- 联网搜索另有阿里云官方 15 RPS 主账号级限制，按该主账号下所有 API Key 的联网搜索请求总和统计，不区分模型；超过时 API 不报错但搜索链路不触发。当前主聊天 `forced_search=false`，高并发或模型判断无需实时信息时可自然退成未联网回答，不额外做后端二次不联网重试；今日农情强制联网生成需单独观察生成任务成功率

上下文结构：
- A 层历史滑窗：Free / Plus 6 轮，Pro 9 轮
- 记忆文档：Free / Plus 每 6 轮、Pro 每 9 轮触发一次；后端用一份纯文字提示词、一次 `qwen-plus` 调用，覆盖更新一份自然语言用户记忆文档。物理存储暂沿用 `session_ab.b_summary` 字段，对外接口返回 `memory_document`，`b_summary` 仅作为兼容别名；旧长期记忆列不再使用并由迁移删除。记忆文档建议四块：“短期记忆 / 长期记忆 / 用户画像 / 农业事件”，一般约 1000-1400 个中文字符以内，复杂连续场景可更长一些，但这是提示词写作目标，不是后端硬截断；四块是提示词方向，不是后端硬结构。短期记忆负责接住最近正在处理的事项和下一轮要接的上下文；长期记忆记录跨多轮仍可能有用的稳定信息；用户画像记录用户明确给出的个人信息、稳定偏好和长期限制；农业事件放后续可能继续追踪的具体农事线索，并尽量保留已有时间、地点和客观图片线索。整体策略是覆盖更新而不是归档追加；最近对话、最新纠正和更可靠证据优先，旧记忆只作为融合材料，不得长期堆流水账。模型输出和最终存储都是纯文字；后端最大限度放开，不解析四块、不补旧块、不按诊断词 / 作物词 / 主题词 / 画像完整度拦截写入，只清理外层 Markdown 代码块和空白，模型返回非空文本就原样写回；只有模型失败、空回复、响应过大或写库冲突时才不写回并保留 `pending_retry_b`。记忆质量、四块是否写满、旧记忆是否保留、边界措辞和参数保真都交给提示词与后续抽查控制，避免卡太严导致新用户什么都存不了。记忆提示词要求不得把“倾向于 / 更像 / 可能 / 不能排除 / 未见明显...”升级成“确诊 / 确认为 / 已排除 / 已证实”等长期结论，除非历史对话里明确有用户复查、检测或人工确认；第三方或用户转述的判断必须写清“用户转述 / 朋友反馈 / 据用户说”等边界；关键数量和单位不得自行换算或补口径
- 锚点信息：约 1000 tokens，每轮必注入
- 待评估方向：后续如果一份记忆文档不够用，再评估结构化字段或独立农事事件表；当前不单独落复杂农事事件表 / 状态卡
- 原始问诊归档：成功完成的问答轮次会写入 `session_round_archive`，先按 30 天滚动保留；`/api/session/snapshot` 给 UI 的 `a_rounds_for_ui` 优先返回 30 天内最近 30 轮归档，并返回 `round_total` 让 Android 知道更早轮次数量。Android 主聊天只渲染最近 30 轮以保护长期使用性能；如果后端总轮数超过当前 UI 展示轮数，会在列表顶部用普通用户口径提示“更早若干轮已保留，后续对话会尽量接上”，不暴露后端归档、模型机制等内部表述。该归档只服务换机 / 重装后的 UI 历史恢复、后台排障和后续可能的离线分析，不进入每轮主模型上下文，不替代 A 层滑窗或记忆文档
- 删除所有历史对话：账号管理页会先弹“取消 / 确定”二次确认；确认后 Android 调 `POST /api/session/clear`，后端删除当前用户的 `session_ab`（A 层滑窗和记忆文档）和 `session_round_archive` 归档，并递增 `session_generation`。前端成功后清当前聊天 UI、本地聊天快照、输入草稿、streaming draft、待发送 WorkManager 任务和本地私有 composer 图片。该操作不删除会员 / 额度 / 加油包 / 礼品卡、帮助与反馈、`quota_ledger`、`session_round_ledger` 或本机 `user_id`。`/api/session/clear` 与 `/api/chat/stream` 获取同用户 inflight 租约前会共用 MySQL 用户级命名锁，避免“清空检查无活跃流”和“旧请求刚好开新流”并发穿透；`/api/chat/stream` 开模型前和归档前还会校验客户端随请求带上的 `session_generation`，用户一旦存在清空代际，后续缺失 `session_generation` 的请求直接按 stale 拒绝；旧 ledger replay 也会按完成时间与最近清空时间比对，清空前完成的同 `client_msg_id` 不允许在清空后幽灵回放。若同一用户当前有活跃主对话流，后端返回 `409 ACTIVE_CHAT_STREAM`，前端提示稍后再删除。Android 会持久化最新 `session_generation`，本地聊天窗口、streaming draft、composer draft 和 WorkManager pending 图文都会记录所属 generation；读取或后台补发时若与当前 generation 不一致会直接丢弃。Android 清空成功时还会递增本地 clear epoch，取消前台图片上传 job、pending 图片恢复、queued mainHandler 回调和待发送 WorkManager，并让本地聊天窗口 / streaming draft / composer draft 延迟保存、上传回调、后台 Worker 继续条件都复查 epoch / pending 是否仍存在，避免旧图文或旧回复在删除后回灌
- 时间 / 地点：后端每轮主对话必须注入当前时间、用户地点和地点可信度；历史轮次如果有后端 `created_at / region / region_source / region_reliability`，进入模型上下文时也要带轻量时间 / 地点前缀。时间以后端服务器时间为准，不用前端手机时间当业务真相；前端暂不显示每条消息时间戳或地点条。Android 已接入可选定位权限 `ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION`：进入聊天页时若已经授权，会静默刷新一次粗略地区；未授权时不在首屏立即弹系统权限框，而是在用户首次发送问诊时按需请求一次定位权限，首轮仍可用缓存地区或后端 IP 粗定位兜底，不阻塞提问。授权后每次发送问诊前尽量短窗口刷新系统定位，并只用系统 Geocoder 反查出的省 / 市 / 区县等地区文本传给后端；地区文本、来源和可信度随 `/api/chat/stream` JSON body 的 `region / region_source / region_reliability` 字段提交，不再放进自定义 HTTP header，避免中文 header 在 OkHttp / 代理链上触发非法字符问题；2 小时内的系统定位或短窗口网络定位标记为 `region_source=gps`、`region_reliability=reliable`，只能拿到更旧系统缓存或历史缓存时仍可传地区文本但必须降级为 `region_reliability=unreliable`；不上传经纬度，不保存轨迹。未授权、定位失败或未传 body 地区时，当前 Nginx 会透传真实客户端 IP 给 Go 服务，`ResolveRegionByIP` 会优先使用 ECS 本地免费离线 `ip2region` xdb 文件（`IP2REGION_V4_XDB_PATH` / 兼容 `IP2REGION_XDB_PATH`）把公网 IP 粗定位到省 / 市级，结果只作为 `region_source=ip`、`region_reliability=unreliable` 的低可信参考，不走收费 API、不放 RDS、不把完整 IP 注入模型；库未配置、私网 IP、代理漂移或查询失败时仍兜底 `未知 / unreliable`

图片规则：
- 单轮最多 4 张
- 压缩：Android 端生成上传副本时，若原图已经是 JPEG、<=1MB、最长边 <=1024px 且 EXIF 方向正常 / 未定义，则直接复制为私有上传副本，不重新编码；其他可解码图片统一转 JPEG，目标单张 <=1MB，按 `1024@Q85 -> 1024@Q80 -> 896@Q80 -> 896@Q70 -> 768@Q70 -> 640@Q60 -> 512@Q60` 固定序列降级；若极端可解码图片仍超 1MB，继续保持 Q60 等比缩小直到 <=1MB；全程不裁剪、不拉伸，保持原图比例
- 估算：1 张图约 1000 tokens
- 图片直接进入模型视觉上下文只保留 2 轮：用户发图的本轮，以及下一轮追问时再带一次；再往后不再把原图 / 图片 URL 直接塞给模型，只保留前面生成过的文字历史、图片客观描述和记忆摘要承接。工程实现上等价于“每次请求只带本轮新图 + 上一轮图”
- Android 端当前 `+` 入口只开放“相机 / 照片”两个入口；照片入口优先使用 Android 官方 Photo Picker 按剩余槽位请求选择上限，并在 manifest 声明官方 Photo Picker backport module 依赖，App 和后端继续兜底最多 4 张。输入框已有 4 张图片时，附件卡片顶部和相机 / 照片点击都统一提示“最多4张图片”
- 图片选中后会先生成 App 私有 `files/composer_images` 稳定上传副本：合格 JPEG 直接复制，其他可解码图片才压缩并转 JPEG；随后按最新剩余槽位二次截断后进入输入框壳体内的缩略图预览，最多 4 张。点发送后会先把带本地 `imageUris` 的用户消息上屏并清空输入框，再后台上传；若确认该图仍是 App 私有 `composer_images` 里的 JPEG 且 `<=1MB`，发送时直接复用这份已压缩或直通的字节，不再二次压缩
- 后端 `/upload` 同样要求鉴权，只接受单张 `<=1MB` JPEG 上传；`/api/chat/stream` 只接受本后端公开基地址下 `/uploads/*.jpg` 图片 URL，防止非 App 客户端绕过 Android 压缩链直接把外部大图或非上传域名塞给主模型
- 输入框图片预览和聊天区用户图片预览共用 `ImagePreviewPager.kt`，内部使用 Telephoto `ZoomableAsyncImage` + `HorizontalPager`，支持单击图片关闭、左右滑动切换同组图片、双指缩放、放大后拖动查看细节以及 Telephoto 的边界阻尼 / 嵌套滚动 handoff；旧 `ImagePreviewGesture.kt` 手写手势链已删除，不允许和 Telephoto 链并存。图片预览只属于 composer / 消息内容内部，不能进入聊天列表 bottom reserve 或工作线计算
- 聊天区用户消息缩略图有 12MB LRU 内存缓存；冷启动 / 换机 / 本地副本已清理后使用远端 URL 兜底时，单次远端缩略图读取最多 2MiB，异常大图直接跳过本地缩略图解码，避免回看历史图片时造成内存尖峰；远端历史图因 OSS 生命周期过期、404 或解码失败时，Android 主聊天显示“图片已过期”缩略图占位，全屏预览显示“图片已过期，仅保留文字记录”；帮助与反馈图片按 `support/` 30 天生命周期过期后同样显示过期占位，文字客服记录仍保留在 MySQL
- 带图发送会按 `chatScopeId + userMessageId` 排一个唯一 WorkManager 延迟兜底任务：前台仍是正常流式显示主人，后台只在前台不活跃且远端启动保护窗已过时才补发；前台一旦开始 `/api/chat/stream` 会写入 10 分钟保护窗；若后台兜底遇到图片上传失败、网络中断、流异常结束、`409 STREAM_IN_PROGRESS`、限流或临时上游错误，会用同一 `client_msg_id` 走 WorkManager 指数退避重试，普通可恢复失败最多重试 5 次后移除 pending，避免无限烧模型成本；若 assistant 失败态已存在远端图片 URL，用户点击重试也会重新登记同一条 pending，让 App 随后被杀时仍能由 WorkManager 兜底；后端 `chat_stream_inflight` 会按 `user_id` 限制同一用户同时只有一条活跃主流式请求，并用 `user_id + client_msg_id + lease_token` 做同消息幂等锁，确保活跃租约内只有一个上游模型流启动；重复同消息返回 `409 STREAM_IN_PROGRESS` 给前端走长窗口 snapshot 恢复，不同消息并发会被同一用户活跃锁拒掉，优先保护模型成本和扣次一致性
- 后端完成后的 replay 真源以 `session_round_ledger` / 归档成功为准，服务端只在轮次归档成功后才向客户端发送 SSE `[DONE]`；额度扣减在归档成功后执行，若 `ConsumeOnDone` 临时失败会按同一 `client_msg_id` 短重试，重复扣由 `quota_ledger` 唯一键防住；replay 只用于恢复已归档答案，不再根据当前档位 / 当前日期补扣旧轮次，避免会员档位变化或跨日后误扣。主模型上游开流不做服务端自动二次重试，Android 前台流也不对模型开流失败做静默多次重试；后台 WorkManager 只对同一条 pending 图片消息做带上限的可恢复失败重试。如果 App 在图片上传阶段或上传成功但尚未可靠完成远端请求时被系统杀掉，后台任务会复用本地稳定图片副本或已上传 URL 继续补发；若后端已用同一 `client_msg_id` 完成归档，则按 replay / snapshot 恢复收口；如果前台已经显示“发送失败”，后台任务会同步取消，不允许 UI 失败态和后台自动发送并存
- 记忆文档摘要由后端 `qwen-plus` 异步处理，摘要请求在 OpenAI 兼容模式请求顶层显式设置 `enable_thinking=false`，单次提取请求有 60 秒超时保护；不联网、非流式，不保留其它摘要候选或分层模型灰度。记忆文档使用当前 A 层 6/9 轮窗口更新；同一用户先有本进程运行中保护，配置 Redis 时还会用带 TTL 的 `nj:summary:lease:*` 分布式租约避免多实例重复提取；写回必须匹配触发时的 `round_total`，旧快照结果不能覆盖更新轮次。提取失败、超时或 Redis 租约暂不可用会保持 `pending_retry_b`，后续轮次完成后继续补提取。后端不按内容拦截或四段补全，非空模型输出会直接覆盖旧记忆；提示词负责尽量保留旧稳定画像、防止不确定判断变定论、第三方转述变系统事实、单次诉求变长期画像，以及面积 / 剂量 / 倍数被模型自行换算
- App 内相机优先让外部相机写入 App cache 下的 `NongjiFileProvider` 临时 URI；manifest 声明 `ACTION_IMAGE_CAPTURE` 查询，启动外部相机时会给输出 URI 加读写 grant flags、ClipData，并按可解析相机包显式授权，回调或启动失败后撤销授权。导入 App 私有 `composer_images` 成功后，Android Q+ 再把原始拍照结果复制到系统相册 `Pictures/农技千查`。临时文件、拍照取消和相机启动失败都会清理；只有 FileProvider 目标创建失败时才回退到直接创建相册 URI。相机待回调 URI、是否相册保存和临时文件路径当前用可保存状态暂存，降低外部相机期间 Activity 重建导致拍照结果丢失的概率
- 当前图片入口不额外申请相册 / 相机 / 存储权限：照片入口使用系统 Photo Picker，拍照入口使用外部相机写入 App 创建的 FileProvider URI，Android Q+ 复制到本 App 创建的相册图片不需要存储权限。定位权限只服务问诊地区上下文，不接地图 SDK、不上传经纬度、不保存轨迹

联网搜索：
- 默认能联网就联网，优先官方、正式、权威资料
- 仅在强时效、事实核对、外部数据查询、疑难复杂问题或高风险判断需要校准公开权威信息等场景触发
- 不允许凭印象硬改

今日农情：
- 今日农情是独立的每日资讯内容，后端数据结构和接口历史命名仍叫 card，但它不是聊天消息，不写 `session_ab` / `session_round_archive`，不触发摘要，不扣用户问诊次数。Android 只在主聊天时间线里当天今日农情视觉项后方的连续三轮用户发送中，显式携带 `today_agri_context_day`；本地缓存可以先用于视觉展示，但只有远端成功确认过当天卡片后才允许携带该 day，避免用户看到的旧缓存和模型读取的最新卡片错位。后端只接受等于服务器上海日期的 day，并临时读取当天 ready 农情作为系统背景，帮助模型理解“刚才/上面/第几条农情”这类紧邻追问。第四轮起自动不带；记忆文档整理、聊天归档、A/B/C、扣次和用户长期事实都不写入今日农情正文。不要改成关键词判断，也不要把今日农情伪装成远端聊天历史消息
- 后端数据真源是 `daily_agri_cards`，按 `day_cn + scope` 唯一保存；当前 scope 固定为 `CN`
- 用户侧只读接口是 `GET /api/today-agri-card`，需要用户鉴权，只读取已生成缓存，缺失 / pending / failed 时前端静默不展示，不在用户打开 App 时临时触发模型；近 30 天回看接口是 `GET /api/today-agri-cards`，同样只返回可展示的公开内容
- 内部生成接口是 `POST /internal/jobs/today-agri-card/generate`，只给定时任务 / 运维调用，必须携带 `DAILY_AGRI_JOB_SECRET`；生成前用数据库 lease 防并发。内部探针接口是 `POST /internal/jobs/today-agri-card/probe?runs=3`，只测试模型输出、来源、解析质量和 usage 成本，不写 `daily_agri_cards`，最多 5 次一组，仍只供运维内部使用
- 生成链路固定使用 `qwen3.5-plus` 的 OpenAI 兼容 `chat/completions` 非流式接口，显式设置 `temperature=0.8`、顶层 `enable_thinking=false`、`enable_search=true`、`search_options.search_strategy=turbo`、`search_options.forced_search=true` 和 `search_options.enable_source=true`；不保留 Flash、qwen-turbo、Responses 或 DashScope `text-generation/generation` 作为今日农情生产候选，不通过环境变量切模型。用户已明确轻量模型、质量不稳模型和即将下线模型不作为今日农情生产候选
- 今日农情默认不需要结构化 URL 来源列表下发给用户；OpenAI 兼容 Chat 联网链路不依赖结构化 `search_info.search_results`，`enable_source=true` 主要用于辅助模型在正文 JSON 里写短来源名称。公开接口只返回标题、摘要和短来源名称，不下发 URL、source_index 或 published_date，Android 单条农情不可点击跳浏览器。qwen3.5-plus 链不传 `freshness`、`prompt_intervene` 或 `assigned_site_list`，近 7 天、最新优先、种植侧、去重和来源质量全部放进主提示词控制，继续全网宽搜。`agent / agent_max` 属于多轮检索整合且通常带来更多输入 token 和延迟，今日农情默认不使用
- 今日农情生成最多 2 次，第二次只在首轮解析失败后换检索提示补救，仍走 `turbo`，不切 `agent`。后端只保留展示所需的最低技术兜底：模型响应能解析出 JSON、必须能取到 3 条标题 / 摘要非空的 item 才发布；超过 3 条只展示前 3 条，少于 3 条视为卡片不完整，不对用户发布。这里的“三条”是唯一硬数量要求，不要求模型必须输出 `card_name=今日农情`，也不按重复标题、主题词、发布日期、近 7 天链接、旧卡链接、养殖关键词、广告词、电商域名、社交域名或标题党拦截发布。内部 URL 只做私网 / 本机等不可追溯或危险地址清理，公开响应不下发链接。今日农情只取种植侧，养殖、水产不要；该方向以及广告软文、假新闻、重复事件和标题党主要通过提示词、内部探针、后台运营复核与 SLS 日志观察控制
- 生成时会读取过去 7 天已 ready 的今日农情内容，把标题 / 摘要 / 来源域名 / 原文短指纹喂给模型，要求今天不要重复同原文、同标题或同一事件；后端不按历史链接 / 标题或重复标题拦截发布
- 今日农情提示词版本当前为 `2026-06-15-v77`：提示词继续保持通用任务说明，不恢复旧版本长清单。唯一硬数量要求是 3 条；内容方向是中国种植侧新闻，面向普通大众用户，优先近 7 天公开来源、今天或昨天的新进展更优，三条尽量分散地区 / 作物 / 主题。v77 不限制具体作物，不单独排斥某个大田作物；大田作物、经济作物、设施农业、果树、蔬菜、茶叶等都可以，但更优先选择对生产有实际参考价值的内容，例如栽培管理、植保病虫、种子种苗、农资农机、技术推广、苗情墒情、产地流通 / 价格、政策补贴等真实进展。天气、气象、防灾或抢收可以作为其中一个角度，但不要三条都写成天气预报；写这类内容时要说明它对农事安排、防灾减损或田间管理的影响。养殖、水产、畜牧、猪肉 / 生猪、禽蛋、牛羊奶、饲料、兽药、渔业、鱼虾等主体不要。摘要目标约 90-130 个中文字符，写 2-3 句正常新闻短讯，一般不低于 80 字。以上仍是提示词层面的方向控制，不增加后端内容过滤或字数拦截；后端只做 JSON、3 条标题 / 摘要非空和私网 URL 安全兜底，质量继续靠提示词、探针和后台复核观察控制
- 后端只发布可解析 JSON、3 条标题摘要完整 item 的结果；主题方向、广告软文、假新闻、前端元表达、推荐理由、重复和标题党类内容主要靠提示词控制、探针抽查和后台复核，不靠后端按内容拦截。公开响应只包含 `title / summary / source`，不包含 URL、source_index 或条目日期
- Android 只把今日农情作为 `ChatTimelineItem.TodayAgriCard` 插入视觉时间线；真实 `messages` 仍只包含用户 / assistant 对话。主聊天视觉上按普通 AI 文本展示“今日农情 · 日期 + 3 条资讯”，标题加粗，序号使用 `一、二、三、` 中文标识，长按复用 AI 文本区同款“复制 / 全文复制”菜单；它仍是同一个正向 `LazyColumn` 里的普通列表项，不做 overlay、sticky 尾卡、浮层、关闭动画或真实聊天消息。如果当天内容加载时没有任何真实消息，它作为当天第一条视觉内容排在列表顶部，不参与启动历史底部贴底等待；如果已有真实消息，它锚在当时最后一条真实消息后方，本地缓存可先展示但必须等远端历史 hydrate 后再按真实尾部保存锚点，且只有远端当天卡片确认后才允许用于后三轮临时上下文。用户后续发送文字 / 图片 / 失败态消息时，新消息自然追加在它后方并把它往上顶。除首屏只有今日农情且没有真实消息这种顶部展示外，主聊天进入正常聊天流后，用户未主动浏览时按最新视觉尾部回底 / 静态贴底，今日农情在尾部时也参与视觉贴底。聊天页按上海日期定期检查跨天，跨天后先清空旧日期内容，只接受 `date_cn` 等于当前日期的 ready 内容，避免长时间前台或后台恢复时继续显示昨天内容。今日农情不参与本地聊天快照的真实消息、远端聊天历史、记忆文档或问诊扣次，也不能作为最新真实消息锚点；仅通过 `today_agri_context_day` 支持当天视觉项后三轮临时上下文；不提供手动关闭 / 手动隐藏入口，单条农情不可点击跳外部链接。设置页“今日农情”入口展示最近 30 天已 ready 的标题、摘要和来源名记录

会员与计费：
- Free：6 次 / 天
- Plus：19.9 元 / 月，25 次 / 天
- Pro：29.9 元 / 月，40 次 / 天
- Plus / Pro 到期后，后端按有效权益自动降回 Free 计算；旧 `user_entitlement.tier` 可以保留历史值，但接口和额度消费只认 `tier_expire_at` 后的有效 tier
- Plus / Pro 必须有有效 `tier_expire_at` 才算有效付费会员；若数据库出现 paid tier 但到期时间为空，后端按 Free 处理，避免脏数据变成永久会员
- 加油包：6 元 / 80 次，仅 Plus / Pro 可买
- 同一时刻只允许 1 个 active 加油包，用完再续
- 续费订单金额以后端 `orders.amount` 记账为准：Plus 19.9，Pro 29.9
- 支付未接入前，后端开发期订单变更接口默认返回 `PAYMENT_NOT_CONFIGURED`；即使显式设置 `ALLOW_DEV_ORDER_ENDPOINTS=true`，也必须同时把 `APP_ENV / ENV / GO_ENV` 设为 `local / dev / development / test` 才会放行，缺失环境名按关闭处理。正式支付必须走服务端验签后的支付回调 / 对账流程
- 当前代码里的超额消耗顺序：先每日额度，再升级补偿额度，再加油包；如果后续业务口径调整，再单独同步改代码和本文
- 当前成本核算口径：主对话是实时 SSE `qwen3.5-plus`，不吃 Batch Chat 半价；按当前公开价输入约 0.8 元 / 百万 tokens、输出约 4.8 元 / 百万 tokens 估算。现阶段不要再把 `0.018 ~ 0.019 元 / 轮` 称为“最保守”，更准确口径是：真实平均约 `0.008 ~ 0.015 元 / 轮`，偏重使用约 `0.018 ~ 0.022 元 / 轮`，连续 4 图 / 上一轮图片 / Pro 满 A 层 / 长输出 / 搜索等极重场景约 `0.025 ~ 0.030 元 / 轮`。后续上线后应记录每轮实际 `input_tokens / output_tokens / search` 再校准会员毛利

## 4. 提示词与后端真源

当前真源文件：
- 主对话锚点：[server-go/assets/system_anchor.txt](D:/wuhao/server-go/assets/system_anchor.txt)
- 记忆文档摘要提示词：[server-go/assets/summary_extraction_prompt.txt](D:/wuhao/server-go/assets/summary_extraction_prompt.txt)

规则：
- 主对话锚点和记忆文档提示词职责不同，不允许混写
- 主对话锚点属于高敏生产提示词；除非用户明确要求“修改主对话锚点 / 改 system_anchor.txt / 按这版落地”，否则 Codex 只能审查、说明问题、提出草案并先发给用户确认，不得擅自修改 [server-go/assets/system_anchor.txt](D:/wuhao/server-go/assets/system_anchor.txt)
- 锚点规则改动时，必须同次同步更新真源文件和 [AGENTS.md](D:/wuhao/AGENTS.md)
- 主对话锚点缺失或为空，属于主链配置问题，应 fail-fast
- 记忆文档摘要失败不拖垮主对话，但必须保留 `pending_retry_b`，后续轮次完成后继续补账

当前锚点执行重点：
- 对外称呼明确为“农技千查”；用户询问助手是谁时，可简短说明自己叫农技千查，是农业问答助手
- 当前轮输入优先，历史 / 摘要 / 联网只作参考
- 历史和摘要不是定论；同一作物 / 同一地块可承接仍有效信息，新问题不能直接套旧判断
- 记忆摘要仅供参考，用于上下文承接和减少重复追问；除非用户明确要求回顾历史，否则不主动复述记忆摘要内容、小标题或用户画像
- 地点可信度为 `unreliable` 或未知时，地区、气候和农时只能作低可信背景，不当成确定事实
- 普通问候 / 寒暄简短回应并引导回作物、症状、图片、地区或具体问题；不套农业诊断格式
- 信息不足时严禁直接下定论，应按可能性排序列出 2 到 3 个判断方向，并追问 1 到 2 个关键问题
- 图片先客观描述图片可见信息，再分析判断
- 涉及混配、浓度、倍数、亩用量、兑水量、面积换算等问题时，应先核对关键参数，再给出计算或使用建议
- 联网搜索只在必要时触发：强时效、强客观核对、用户明确要求、疑难复杂问题或高风险判断需要校准公开权威信息；同一轮最多一次
- 证件 / 登记 / 备案 / 审定类不做真伪裁决或合规背书，只给权威平台查询方法
- 会员、订单和账号相关问题只说明已公开的功能入口和规则，不查询、不猜测用户个人账户状态：会员中心可查看当前档位、剩余次数和权益；Plus / Pro 提供每日问答次数，加油包提供额外次数；账号管理用于查看手机号、退出登录、删除历史对话和注销账号申请。涉及个人档位、剩余次数、扣费、订单、支付结果、权益异常、账号问题、持续故障或个人数据处理时，不编造具体情况，引导用户到会员中心、账号管理或设置里的“帮助与反馈”处理；涉及退款、补偿、开通、关闭权益等处理结果时，不承诺最终结果，以 App 页面、订单记录和客服处理为准
- 商业相关内容不做推广、导购、背书或诋毁，但允许按用户要求做相关信息整理、标签解读、技术分析或中立比较

## 5. 开发与交付规则

改代码原则：
- 先读代码，再下刀
- 小范围改动，优先复用现有逻辑
- 不假设不存在的接口、文件或路径
- 能查官方资料就先查

风险控制：
- 本项目默认按“准确率优先”工作：复杂任务不要为了节省 token、时间或子代理次数而省略关键代码阅读、旧方案残留排查、官方资料核对、验证命令或必要的只读并行巡检；但也不能用无关大改、顺手重构、重复跑无意义命令来冒充严谨
- 一次只收一个明确问题
- 改完必须复盘影响范围，避免改了这个坏了那个
- 临时日志只服务当前问题，用完尽快删除；debug 面板不得进入 release 可见入口
- 当前 UI 文案 / 样式预览面板短期允许保留，但必须保持 debug-only / 隐藏入口，发版前确认 release 不可见。以后凡是改 Android 用户可见 UI、用户可见文案、隐藏态、异常态、设置页、会员 / 礼品卡 / 帮助反馈 / 检查更新 / 登录等入口时，必须同步检查并按需更新 debug-only 预览面板；若本次 UI 改动确实没有可预览状态，也要在最终汇报里说明已核对无需新增预览项。预览面板优先沿用 2026-04-29 这版模式：上半部分是可点的文案 / 场景条目，下半部分按正式组件样式渲染单项真实预览；不要只做纯文字列表，也不要为了看隐藏态去制造真实异常流程；若阶段性不再需要，再单独删除入口和相关文档
- 同一个问题如果已经连续两轮修改仍未解决，第三轮开始前必须先会诊，再继续改

Codex / OpenCode 子代理协作规则：
- 本仓库允许 Codex 和 OpenCode 同时协作；两边都必须服从同一套根规则、项目记忆、runbook 和交付闭环，不允许因为工具不同产生两套事实口径
- OpenCode / Codex 内部子代理可用于复杂任务的只读并行巡检，例如跨 Android / Go / 文档 / 运维的链路排查、代码 review、旧方案残留扫描和验证建议整理；这类内部子代理不等同于小米 / MiMo 等外部会诊
- 子代理默认只读，不直接改文件、不提交、不推送、不做生产运维；除非主窗口明确授权并限定文件、命令和影响范围
- 给子代理的任务必须写清只读或可写、检查范围、关键文件 / 函数、要返回的发现、风险、建议和验证命令；子代理输出只能作为证据和建议，主窗口必须对照当前代码核验后再下刀
- 主窗口负责最终修改、验证、文档同步、提交和推送；提交前必须复查 `git status`、`git diff`、近期提交和远端状态，只暂存本次意图内文件，不能覆盖或回滚另一边正在进行的并行改动

会诊优先级：
- 不要默认同时找多个外部模型并行会诊；先选一个最匹配的对象，避免用户重复复制和多头口径互相干扰
- 当前用户已明确不再使用 Gemini；本项目如需外部会诊，默认优先整理成发给小米 / MiMo 的短稿。除非用户之后明确指定，否则不要再建议 Gemini；如果用户明确点名 Claude / Kimi / 其他对象，则按用户点名执行
- Android Compose UI、滚动链、动画、渲染时序、布局抖动、输入区 / 工作线几何问题：当前默认优先建议用户找小米 / MiMo 会诊，但前提仍是 Codex 先本地锁定到具体代码点，再外发短稿
- 后端 Go、业务边界、接口职责、数据流、架构复盘、文档归纳：当前也默认优先整理成发给小米 / MiMo 的自包含短稿；若问题明显更适合别的对象，先说明原因再换
- OpenAI / Codex / API / 官方产品能力与限制：优先查官方文档或官方资料，不把小米 / MiMo、Claude 等外部模型当真源
- Codex 的职责始终是：结合仓库当前代码落地修改、检查旧方案是否并行、编译验证、更新文档并提交推送
- 如果问题类型已经明显匹配上面某一类，后续窗口应直接点名建议用户找对应对象，不要再让用户自己猜“该问谁”

会诊稿规则：
- 会诊前先完成本地代码排查；先确认当前唯一主链、已废弃旧链、仍在运行时生效的真实代码位置，再决定问什么
- 发给外部模型的会诊稿必须锚定当前仓库代码，至少写清：相关文件路径、关键函数/状态名、必要时的代码片段或行号、当前现象、已排除项、怀疑点、限制条件
- 会诊问题必须尽量收敛到具体实现，不要只发“为什么会抖”“怎么优化”这类抽象描述；要明确让对方基于当前代码回答，不要假设仓库里不存在的接口、状态或组件
- 若当前问题已经定位到某几个可疑点，会诊稿应直接要求对方围绕这些代码点给最小可落地方案，并明确禁止跑题到已被排除的方向
- 收到会诊建议后，Codex 必须先对照当前代码核验：有没有假设不存在的字段、有没有和现有主链冲突、有没有把旧方案重新带回来；核验不过不直接下刀
- 当前默认现实前提：外部会诊对象（尤其是小米 / MiMo 免费版）通常无法直接读取本地仓库、文件链接或真实运行环境；它们看到的只有用户手动转发过去的文字、截图、代码片段与日志
- 因此外发会诊稿必须自包含：不能只写“看 ChatScreen.kt”“看仓库最新代码”，而要把对方作答所必需的关键代码片段、状态名、调用顺序、限制条件直接贴进消息里；若缺少这些上下文，会诊结果默认不可信
- 发给小米 / MiMo 的会诊稿尤其要把“当前真实代码结构 + 关键片段 + 为什么没照抄上一条建议 + 当前不能碰的旧方案”写清楚；小米 / MiMo 可以负责方向判断，但 Codex 必须负责仓库级核验和落地
- 如果外部会诊通过手机聊天软件进行，优先压缩成“问题说明 + 关键代码片段 + 明确追问”的短稿，避免对方因为上下文不全继续脑补仓库结构

交付要求：
- Android 改动后编译：`./gradlew.bat :app:compileDebugKotlin`
- Android 改动默认只做编译验证，不主动执行 `:app:installDebug`；真机安装与回归默认由用户自行完成，只有用户明确要求 Codex 装机时才执行安装
- Android 若改动聊天页关键 UI 预热路径（`ChatScreen.kt`、`ChatStreamingRenderer.kt`、`ChatComposerPanel.kt`、`ChatComposerCoordinator.kt`、`ChatRecyclerViewHost.kt`，或 Selection / Markdown / LazyColumn / 输入框 IME 主链），必须同步检查 [docs/runbooks/android-baseline-profile.md](D:/wuhao/docs/runbooks/android-baseline-profile.md)：确认是否需要更新 `:baselineprofile` 的关键路径脚本、是否需要重新生成 Baseline Profile。小样式 / 文案通常不需要，但发版前若关键路径变更，应重跑 `:app:generateReleaseBaselineProfile`
- Go 后端改动后编译：`cd server-go && go build ./...`
- 每次改动后都要检查影响面
- 若改动影响当前真相、风险、待决策、运维口径或方案取舍，必须同步更新 `docs/project-state`、`docs/adr`、`docs/runbooks`
- 项目运维脚本、部署脚本、只读巡检脚本、回滚脚本、日志 / 容量 / 认证次数查询脚本属于业务仓库资产，必须随代码提交并推送到 `origin/master`；本机工具配置、个人软件设置、桌面快捷方式、一次性临时脚本、API 中转站配置和任何密钥文件不能提交
- 每次改动后都要提交本地 git，并推送到 `origin/master`
- 涉及 ECS、阿里云、Nginx、证书、备案、模型 Key 槽位、后端环境变量或生产健康状态的云端变更，必须同次同步更新仓库 runbook 和项目记忆，并在验证通过后提交推送到 `origin/master`；真实密钥值只写入服务器环境或本机密钥文件，不写入仓库、提交信息、聊天记忆或文档

## 6. Chat UI 总原则

总原则：
- 聊天 UI 必须由当前真实代码和当前真实状态直接决定
- 不允许靠本地历史、本地草稿、本地视口假状态“看起来正常”

Clean-State 定义：
- 清除 app 数据后首次启动
- 无本地聊天记录、无本地底部视口、无本地流式草稿、无旧 user_id 状态
- 若用户没有稳定账号 / 后端身份，清数据后应视为新用户 clean-state；若用户有稳定账号 / 手机号登录，后端可按该身份返回 30 天内最近 30 轮业务聊天记录，这是账号级业务恢复，不是本地 UI 状态回退。当前手机号账号骨架、普通短信验证码登录和 Android 登录门已落地；未登录或未配置完成时仍主要依赖本机 `user_id`，清数据导致本机 `user_id` 丢失后，30 天归档无法自动识别旧用户

Clean-State 必做回归的范围：
- 聊天列表布局
- 流式生成渲染
- 发送后上抬 / 小球锚点
- 自动滚动 / 手动打断 / 回到底部按钮
- 底部输入区
- 复制链
- 免责声明
- 冷启动 / 重进 app / 历史恢复

聊天框分层边界：
- [ChatScrollCoordinator.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt)：滚动状态机、发送后锚点、回到底部按钮
- [ChatStreamingRenderer.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatStreamingRenderer.kt)：waiting / streaming / settled 渲染
- [ChatComposerCoordinator.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatComposerCoordinator.kt)：输入框动态、IME、发送收口
- [ChatComposerPanel.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatComposerPanel.kt)：底部输入区 UI 宿主
- [ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt)：页面组装、测量值采集、状态接线
- [ChatRecyclerViewHost.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatRecyclerViewHost.kt)：纯 Compose `LazyColumn` 底座与 bottom padding 宿主；文件名只是历史命名残留，运行时已不是 `RecyclerView`

浮层退出动画规则：
- 整页浮层、半屏面板和居中卡片关闭时，不允许让主体内容整块 `fadeOut` 到下一层页面上；否则黑色文字、返回键、价格数字等高对比元素会在聊天页或上一层浮层上形成残影
- 页面型浮层关闭优先直接卸载；若后续确实保留滑出动画，面板内容不再叠加淡出，遮罩退出时间必须不早于面板主体退出时间
- 整页设置页关闭优先直接卸载或使用非淡出位移动画，不做整页淡出
- toast / 小型黑色提示浮层可以淡入淡出，因为它本来就是短暂提示，不承载页面结构

旧 `RecyclerView / AdapterDataObserver / DiffUtil / suppressLayout / frozenBottom / retainedBottomGap` 等旧滚动术语全部视为历史归档，不再执行。

## 7. 当前 Compose 列表滚动链唯一真相

### 7.1 总口径

- 聊天消息运行时当前只允许有一个主人：正向 `LazyColumn`
- `ChatRecyclerViewHost.kt` 当前使用：
  - `LazyColumn`
  - `items = ChatTimelineItem` 展示层；当前可能包含一个 `TodayAgriCard` UI-only 今日农情内容和真实 `messages`
  - 默认 `verticalArrangement = Arrangement.Bottom`，用于正向列表短内容不满一屏时也贴到底部工作线；唯一运行时例外是 `InitialWorklinePhase.TopUnreached / TopAnchoring`：清数据 / 删除历史后的首次真实业务内容尚未碰到 96dp 工作线前，以及触线后等待安全切回同帧底部锚点接住的极短交接内，临时使用 `Arrangement.Top` 让真实消息自然从顶部往下排
  - `messages` 仍按 oldest -> newest 存储；视觉底部最新真实消息通过 `ChatTimelineItem.Message` 反查 index，不能把今日农情内容当成最新真实消息锚点；今日农情按加载时机锚在视觉时间线中，没有真实消息时位于顶部，有真实消息时位于当时最后一条真实消息后，后续新消息自然排在其后
  - 回到底部 / AutoFollow 使用最新真实消息 index + `FORWARD_LIST_BOTTOM_SCROLL_OFFSET`，依赖 Compose 正向列表里 positive `scrollOffset` 会把 item 继续向上推并在列表末端 clamp 的语义，把最新消息底部压到工作线附近
- 底部 composer 仍是页面底部的独立 UI 宿主，负责输入、IME、placeholder、发送禁用与收口视觉；**它不是消息运行时主人**
- `ChatScreen.kt` 当前把消息列表和 composer 作为同一个页面 `Box` 下的兄弟层渲染：列表先铺满消息区域，composer 用 `align(Alignment.BottomCenter)` 固定在底部。composer 不再作为 `SubcomposeLayout` 的 child 参与列表同拍测量，避免 IME 动画每帧拖着列表一起 remeasure；composer 自己继续吃 `imePadding()`，根容器不吃 IME padding，以保持“键盘只移动输入框，不抬升消息工作线”
- 已经没有 true 入口的 `ChatComposerCollapseOverlay` / `composerCollapseOverlay*` 输入框残影旧链已删除；composer 收口只走底部固定 composer 的单一实测 reserve。后续不要为了压输入框残影恢复透明 overlay、bounds snapshot 或 overlay prewarm 旧链
- composer 内部内容高度不属于聊天列表 bottom reserve。长文本、当前图片缩略图预览、未来附件缩略图、图文混排等只能影响输入框内部布局 / 内部滚动 / composer 自身视觉高度，不能直接把历史消息区顶上去；聊天列表 reserve 只允许吃折叠态 composer 外壳、safe area / navigation bar、发送期锁定 reserve、工作线 gap。IME 动画只移动 composer，不允许重新进入列表 reserve。若未来产品明确要“附件栏顶起聊天区”，必须作为单独 external tray 重新设计和命名，不能复用输入内容高度偷渡进滚动链
- waiting 小球、streaming 正文、settled 完成态共用同一条 assistant 消息 item。`ChatScreen.kt` 当前已撤掉 streaming 小分割 / block item 化，不再把一条 assistant 在 `LazyColumn` 内派生成多个稳定 block item 和 active tail；`ChatStreamingRenderer.kt` 也不再保留完成态预切物理行 / committed TextMeasurer 渲染链，settled Markdown 和 streaming 使用同一套 soft-wrap block 渲染结构，优先恢复渲染树稳定。仍禁止恢复 overlay / active-zone / 第二滚动宿主
- mixed active-zone / overlay 运行时当前已退出主链：
  - `StreamingLocation`
  - `BottomActiveZoneSlice / resolveBottomActiveZoneSlice(...)`
  - `renderBottomActiveZone()`
  - active-zone 拖动接管 / Overlay 恢复门
  - `requestSendStartBottomSnap()` 那条“只滚 historyMessages”的发送起步链
- 工作线和静态贴底线继续共用同一个视觉高度：小球、streaming 正文、开机历史态、完成态尾部都落在 composer 折叠外壳上方 `96.dp` 工作线；工作线以下的空白必须露出来，用于免责声明 / 极端说明 / 底部呼吸区，不能把尾部文字压到输入框后面
- 正向列表会把 `CHAT_MESSAGE_ITEM_VERTICAL_PADDING` 从传给 `LazyColumn` 的 bottom padding 里扣掉，只补偿 item 外层 padding，不改变工作线本身；小球、streaming 正文、静态历史和完成态尾部仍应落在同一条 96dp 工作线
- 底部不应再出现额外可见空白；历史区浏览时，输入框弹起 / 收起也不应再带着消息区整体联动

### 7.2 五环节铁律

1. 发送起步
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt)
- 做法：在同一发送事务里同步完成输入框收口、`upsertUserMessage(...)`、assistant placeholder、`prepareScrollRuntimeForStreamingStart(...)` 和发送期 reserve 锁；普通有历史 / 已触线场景按正向列表口径同步请求最新消息 `lastIndex` 贴到底部工作线，`InitialWorklinePhase.TopUnreached` 阶段则暂时挡住自动回底；等真实内容底边碰到 96dp 工作线后先进入极短 `TopAnchoring`，仍保持 `Arrangement.Top`；只有列表可正向滚动、内容底边已超过工作线约 56dp 且用户没有触碰 / 拖动 / 浏览时，才在同一个执行点切回 `WorklineOwned` 并复用现有底部锚点强制接一次
- 当前目标：发送起步仍保持单一列表主人；不再靠 active zone / overlay 切主人来承接当前轮消息

2. AutoFollow / 回到底部
- 主人：[ChatScrollCoordinator.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScrollCoordinator.kt)
- 作用：继续只维护一套 `Idle / AutoFollow / UserBrowsing` 滚动状态机；`scrollToBottom(false)`、jump button 和 follow 都只围绕正向列表主链派生，不允许并存第二条补滚链

3. 发送期几何稳定
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt) 的正向列表主链、底部固定 composer 宿主与稳定折叠 reserve
- 作用：发送当拍允许即时清空输入框并立即插入消息，但列表实际吃到的底部 reserve 必须来自稳定折叠态 composer 外壳实测值 / 发送期锁定值，而不是 IME 动画帧或输入框内容高度；旧 `composerTopInViewportPx` 只保留为辅助几何 / fallback，不再重新升回唯一真相

4. 完成态收口
- 主人：[ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt) 的两阶段 finalize
- 作用：streaming -> settled 默认在同一个消息主线内完成；当前仍保留 `beginPendingStreamingFinalize(...) -> fresh bounds -> finalizeStreamingStop(...)` 这条两阶段 finalize，不允许回退成“同拍直接切 settled”的简化版；pending finalize 只等待 fresh settled bounds，不再额外调用 bottom align 精修，避免完成瞬间把长回复重新锚到上方。渲染层要求 streaming / settled 尽量复用同一套 soft-wrap block 结构，不能再引入完成态专用预切行渲染链造成收口重排；若最终会出现免责声明，streaming 阶段只预留几何高度，不提前显示文字

5. 用户浏览与首次进入
- 主人：用户手指 / [ChatScreen.kt](D:/wuhao/app/src/main/kotlin/com/nongjiqianwen/ChatScreen.kt)
- 作用：用户进入 `UserBrowsing` 后立即让权，但仍在同一个列表主人里浏览，不再发生 overlay/list 切管；冷启动且已有历史时，继续走 `scrollToBottom(false)` 贴到底部；从后台切回时不默认自动贴底

铁律：
- 同一时刻只能有一个消息主人控制滚动与渲染
- 新增任何 scroll 调用前，必须说明它属于哪一个环节
- 回到底部按钮只能从单一列表主链派生，不能另挂第二套“overlay 恢复 / active zone 回收”链

### 7.3 当前实现细则

- `ChatRecyclerViewHost.kt` 当前已切回正向列表底座；如果后续再调整顺序，必须连同当前 `messages` 的真实存储顺序一起检查，不能只改 `reverseLayout` 或只改 `items` 顺序
- `ChatScreen.kt` 当前已回到：
  - `messages` 作为 oldest -> newest 的唯一业务消息数据源；列表显示层可以包一层 `ChatTimelineItem` 承接 UI-only 今日农情内容，但不再通过 `chatListItems` 派生 streaming block item
  - `currentLastMessageContentBottomPx()` 的 fallback 按正向列表使用最新真实消息的 UI index；`InitialWorklinePhase.TopUnreached` 的触线判断另走 `currentInitialDocumentFlowBottomPx()`，按当前业务消息已测 bounds、streaming bottom 和可见消息 item 最大底边判断整段首屏文档流是否碰到工作线，避免首条超长用户消息把 assistant placeholder 挤到未组合区域后漏触发上抬
  - `currentBottomOverflowPx()` 按正向列表单主人口径计算最新消息底边与统一底部目标之间的绝对误差
- clean-state / 删除所有历史后的首屏体验当前用 `InitialWorklinePhase` 收口，而不是旧“稀疏首屏”方案：`WaitingForFirstSend -> TopUnreached -> TopAnchoring -> WorklineOwned`。只有发送前没有任何真实业务消息时才进入 `TopUnreached`；该阶段真实聊天消息、图片消息、图片上传 pending、失败态、重试态、assistant placeholder 和 streaming 小球都仍在同一个 `LazyColumn` 里，只是临时 `Arrangement.Top`，并 gate 普通回底 / AutoFollow 预锚 / sendStart bottom anchor。若清数据 / 首屏只有今日农情内容而没有真实消息，列表同样使用 Top 排列把它作为第一条视觉内容显示，但首屏触线判断、工作线交接和最新消息锚点仍只看真实业务消息。当前首屏文档流的最大可测底边到达或超过 96dp 工作线后，如果用户正在拖动、滚动或浏览，直接交给 `WorklineOwned` 且不抢手势；如果用户只是按住 / 触碰但未形成可切换条件，则继续停在 Top 流，不自动切换；如果用户未触碰 / 拖动 / 浏览，则先进入极短 `TopAnchoring`，继续保持 `Arrangement.Top` 并继续 gate 普通自动回底。`TopAnchoring` 只有在列表已经出现正向可滚范围、首屏文档流底边已超过工作线约 56dp、且用户没有触碰 / 拖动 / 浏览时，才同一执行点设置 `WorklineOwned` 并立刻复用现有 `requestProgrammaticForwardListBottomAnchor(force = true)` 接一次底部锚点，避免把锚定和 arrangement 切换拆到两帧造成“先掉一下再上抬”。后续发送若首屏文档流已经触线，发送入口不得降回 `TopUnreached`，必须继续走正常工作线锚点。`HandoffPending` 已从运行时状态机删除，不允许再作为 Top 布局和底部锚点之间的持续交接态；`ChatTimelineItem.SparseBottomSpacer`、`cleanStateSparseLayoutActive`、动态稀疏 padding / spacer、反向列表、overlay、raw delta 都仍是废弃旧方案，不允许并存恢复
- 用户清除 App 数据 / 缓存不是极端操作，必须作为常规回归路径：固定 UI 默认样式、设置页入口、登录页和主聊天基础布局必须来自当前 APK 代码；手机号账号、会员、额度、礼品卡、反馈、聊天历史和今日农情等业务数据登录后从后端恢复；本地缓存只用于加速和离线过渡，不能成为新 UI 是否存在、设置项是否出现、启动是否贴底的唯一来源。
- 发送起步当前保留的旧保护只有两样：
  - `lockedConversationBottomPaddingPx / sendStartBottomPaddingLockActive`
  - `sendStartAnchorActive`
- 这些保护当前只服务“发送起步短窗口”的 reserve / 放权稳定，**不是**旧 active-zone 时代那种运行时切管门
- `sendStartBottomPaddingLockActive` 期间，列表 bottom padding 与 streaming 工作线必须使用同一份锁定几何：`streamingWorklineBottomPx = lockedMessageViewportHeightPx - lockedConversationBottomPaddingPx`。不允许列表吃 locked padding、工作线却继续吃当前长文本输入框或实时 composer 高度，否则小球锚点会被长输入框顶高
- `observedCollapsedBottomReservePx`、`bottomBarHeightPx`、`latestConversationBottomPaddingPx` 等列表 reserve 相关值，不能从输入框当前内容高度中学习。输入框多行文字、图片预览、附件缩略图导致的 composer 内容扩展，只能停留在 composer 内部；只有稳定折叠态 composer 外壳、navigation bar / safe bottom、发送期锁定 reserve 和工作线 gap 能进入聊天列表 bottom padding。IME 动画期间不更新列表 reserve，只移动 composer 自己
- 当前已决定输入框 / IME 与消息列表解耦：streaming 过程中键盘抬起只移动输入框自己，不再抬升消息工作线；用户在生成中对消息列表产生超过 touch slop 的真实拖动时才进入 `UserBrowsing`，普通点按、长按或点图片不应暂停 AutoFollow。用户手动滑回正向列表物理底部（`canScrollForward == false`、手指已抬起、列表已停止）后，应先请求一次正向底部锚点再恢复 `AutoFollow`；半路只接近工作线不允许自动吸回
- streaming 期间当前不再做段落级 LazyColumn item 小分割，也不再保留 `StreamingBlockChatListItem / StreamingTextBlock / streamingBrowseBlockSnapshot / activeStreamingBlockIndex` 这套派生和新 active block `requestScrollToItem(0)` 接尾巴链。生成中的 assistant 仍是 `messages` 里的单个 item，`ChatStreamingRenderer.kt` 在这个 item 内负责 waiting / streaming / settled 显示；当前抢手问题改回正向列表上继续磨，优先保证用户上滑浏览时不被正在长高的最新 assistant 反向锚点拖回
- `ChatStreamingRenderer.kt` 的 active streaming 内容当前使用单个 soft-wrap `Text` 渲染正在吐字的段落 / 标题 / 列表正文，不再把 active 文本按物理行拆成多颗 `Text`，也不再对新字尾部做 fresh suffix 灰色高亮动画。active Markdown 仍实时吐字，但只有 `# ` / `- ` / `1. ` 等结构前缀后已经出现非空正文时才切成标题 / 列表 / 引用，不能把只有符号的半成品立刻结构化；已完成 / settled Markdown 也走同一套 soft-wrap block renderer。当前 renderer 是面向移动聊天的轻量 Markdown 子集，不是全量 Markwon / CommonMark / GFM 引擎；支持段落、标题、列表、引用、加粗、斜体、行内代码、链接和裸 URL，标准表格降级为手机可读项目行。2026-06-15 起，活跃 streaming 段对未闭合的 `**` / `*` / 反引号做 streaming-aware inline 渲染，已换行 / 空行分隔出去的旧块如果仍含未闭合加粗、斜体或行内代码，也继续按 streaming inline 规则显示，尽量避免闭合符号到达时才整行重排；DONE 到达后仍按打字节奏 drain 本地 reveal buffer，不一口气 flush 尾段。当前远端 waiting 小球最短展示约 1.8 秒、呼吸周期约 700ms；吐字节奏对中文通常 1 个字一拍，中文单字约 22ms，标点和换行保留短停顿，长英文 / 数字词块有单次 reveal 上限，省略号 / 破折号按强停顿处理，常见 emoji、肤色修饰、ZWJ 组合和旗帜不会被拆半，保持一边生成一边渲染，同时避免过慢或末尾整段一口气吐出
- `ChatStreamingRenderer.kt` streaming 期间如果内容已经满足免责声明触发条件，只预留 `assistantDisclaimerTextStyle()` 对应高度，不显示免责声明文字；settled 后才显示真实文案，避免尾部收口当拍突然增高
- `commitSendMessage()` 当前的真实顺序是：
  1. 输入框收口
  2. `upsertUserMessage(...)`
  3. 插入 assistant placeholder
  4. `prepareScrollRuntimeForStreamingStart(...)`
  5. 普通已触线场景置 `sendStartAnchorActive = true`；`TopUnreached` 阶段保持 `false`
  6. 普通已触线场景按正向列表口径同步请求最新真实消息 UI index + `FORWARD_LIST_BOTTOM_SCROLL_OFFSET`，让新插入的底部 assistant placeholder 成为视觉底部锚点；`TopUnreached` 阶段先让内容从顶部自然向下长，到工作线后再交回该锚点链
- `scrollToBottom(false)` 当前是正向列表主链口径；聊天页主调处应把“视觉底部最新真实消息”的 UI index 传给 coordinator，并使用 `FORWARD_LIST_BOTTOM_SCROLL_OFFSET`，不要回到 `scrollToItem(0)`
- 正向列表主链下不再运行旧 streaming 高度追滚：`BindChatListScrollEffects(...)` 不允许再调用 `followStreamingByDelta(...)`、`scrollBy(...)` 或 `dispatchRawDelta(...)` 去追 streaming 正文高度，`streamBottomFollowActive` 空壳状态也不再保留；streaming 期间只维护单一 `Idle / AutoFollow / UserBrowsing` 状态机、发送起步保护和正向底部锚点请求
- AutoFollow 中每次 reveal 提交前会先请求一次最新消息底部锚点；提交 `streamingMessageContent` 后，`ChatScreen.kt` 顶层通过 `SideEffect` 在同帧 apply changes 后、layout 前再次请求最新消息底部锚点，减少“新换行先进树、下一帧才贴底”造成的工作线下方冒头闪
- streaming 期间不能把同帧底部锚定、内部 remeasure 或程序滚动产生的 `isScrollInProgress` 直接当成用户浏览；只有真实拖动或已经进入 `UserBrowsing` 的惯性 / 浏览才暂停 AutoFollow，避免生成中偶发“先往下掉一点、再上去”的体感
- 高频 reveal 底部锚点请求使用一份 generation 守护，一帧后只允许最新请求关闭 `programmaticScroll`，避免旧取消任务把新程序滚动提前关掉后被误判成用户浏览
- 静态 / 开机 / 完成态到底不只看“文本 bottom 命中工作线”，还必须满足正向列表 `canScrollForward == false`，确保工作线以下完整 96dp 空白已经真正滚出来，不能出现看似贴线但还能继续往上扒出底部空白
- 首屏历史贴底不能“一次 scroll 后就关门”；必须等 `startupLayoutReady` 和底部固定 composer 宿主稳定实测高度都到位后多帧重试，并且只有文本 bottom 命中 96dp 工作线、`canScrollForward == false` 同时成立时，才允许把 `initialBottomSnapDone` 记为完成。若首屏贴底刚完成后稳定 bottom reserve 又更新，且用户还未开始新对话 / 未触碰滚动，可只补一次非动画回底修正，避免工作线以下空白没露全
- 开机历史态 / 完成态的底部 reserve 在“输入为空、无 focus、IME 收起、composer 非 settling、非发送锁”的折叠稳定窗口中，必须优先使用底部固定 composer 宿主的稳定实测高度 + `STREAM_VISIBLE_BOTTOM_GAP`，不能只吃启动估值或旧观察值；否则真机输入框真实高度大于估值时，工作线以下空白露不全，用户还能继续往上扒
- 用户进入 `UserBrowsing` 后，若明确回到正向列表物理底部（`canScrollForward == false` 且列表不再滚动 / 手指不再交互），可以立即先请求一次底部锚点再恢复 `AutoFollow`；旧的连续 2 帧工作线命中只作为兜底。这样避免 streaming 持续吐字把工作线容差打断，导致手动回底后长时间不跟随；上滑中或 fling 未停时仍不允许吸回
- `prepareScrollRuntimeForStreamingStart(...)` 当前会把 `scrollMode` 直接置为 `AutoFollow`，因为用户按发送本身就是回到底部看新回复的明确意图；不要在发送后继续保留 `UserBrowsing`
- 回到底部按钮不允许开机、程序回底、bounds 初次上报自己冒出来。按钮资格统一为：消息非空、键盘不可见、生命周期未抑制、用户滑动已经停下，并且正向列表仍可向前滚动且最新消息底边离 96dp 工作线超过 56dp 安全区。按钮不要再用旧反向 `firstVisibleItemIndex == 0` 口径，也不要再加发送后 IME 过渡伪锁。按钮显示是短 pulse：用户滑动过程中强制不显示；用户停止滑动后，再统一按动态 / 静态同一套离底资格判断，离底才出现一小会儿并自动隐藏；点击按钮必须直接滚到正向列表最新消息 `lastIndex` 并清掉 pulse
- pending finalize 不再运行 `alignVisibleChatListBottom(...)` 或完整 `scrollToBottom(false)`；吐完后的渲染树切换只等 fresh bounds 到位。若用户仍处于 AutoFollow，fresh bounds 到位后只请求一次正向底部锚点，再清 streaming 状态，避免完成瞬间主动滚动把可视窗口带到长回复上方
- 两阶段 finalize 当前必须继续保留，不能为了“看起来简单”回退到同拍 `isStreaming = false` 的旧写法
- `composerTopInViewportPx`、`messageViewportTopPx`、`inputFieldBoundsInWindow` 等旧几何状态继续保留给 selection / bounds / fallback 使用；后续不要再把它们升格为“第二套消息运行时主人”的真值来源
- 远端 hydrate、发送事务和本地 snapshot 继续只允许原地增改；不要再把消息替换链改回 `clear() + addAll()`

### 7.4 当前禁改清单

- 不要重新引入 mixed active-zone / overlay 运行时，不要再恢复：
  - `StreamingLocation`
  - `BottomActiveZoneSlice`
  - `renderBottomActiveZone()`
  - active-zone 拖动接管 / Overlay 恢复门
  - `requestSendStartBottomSnap()`
- 不要把当前正向列表重新嫁接到旧 overlay / placeholder / 追滚补偿主链
- 不要恢复 visual offset catch-up、overlay height follow、history-only send-start snap、placeholder/twin-tree finalize、clip/mask 这类已废弃路线
- 不要恢复 streaming 小分割 / block item 化，除非用户重新明确拍板并先做新的会诊
- 不要恢复 `scrollBy(...)` / `dispatchRawDelta(...)` 作为 streaming 高度补偿或回底精修
- 不要删除 `sendStartAnchorActive` 或发送期 reserve 锁，除非有同等级保护替代；发送瞬间抖动之前就是靠这条短窗口保护压住的
- 不要把两阶段 finalize 改回“同拍直接切 settled”；这会把尾部收口抖动和底部空白再带回来

### 7.5 当前回归重点

- 首屏进入且本地有历史时，是否仍稳定贴底
- 发送瞬间小球和历史文本是否仍抖动
- 正向列表下 streaming 工作线下一行是否还有冒头闪
- streaming 过程中上下拖动是否不抢手，用户想停哪里能停哪里
- 回到底部后是否继续 AutoFollow
- finalize 收口是否仍稳定，尤其含免责声明答案是否不再尾部增高微跳
- 输入框上方和静态文本底部是否不再出现额外白块

## 8. 其他聊天 UI 基线

消息复制链：
- 用户消息与 AI 消息共用同一条主链
- 用户消息保持纯文本显示
- AI 复制的是渲染后的可见正文，不是 Markdown 原文
- 黑卡片只保留：`复制`、`全文复制`

输入区选择菜单：
- 走系统选区和手柄
- 自定义黑底白字菜单
- 菜单保留：复制、粘贴、剪切、全选
- 菜单贴输入框上方，不压输入框，不飘到标题区

免责声明：
- streaming 期间不提前显示免责声明文字
- 如最终会出现免责声明，streaming 期间只允许保留稳定几何占位

Markdown 表格：
- 当前不做真表格渲染
- 模型若输出标准 Markdown 表格，streaming / 完成态都会在 renderer 内降级成普通可读的项目行文本；代码块内的 `|` 不参与表格降级
- emoji / 表情若偶发输出，按普通文本交给 Compose `Text` 渲染；提示词仍应尽量压住不要主动使用表情

消息链接：
- 当前只支持 assistant 完成态正文点击链接
- 单击直接走系统浏览器
- 不允许为链接再加额外悬浮层，不能破坏现有复制链

失败提示与重试：
- 用户发送失败：黑底胶囊 `发送失败 · 点击重发`
- AI 回复未完成：黑底胶囊 `回复未完成 · 点击重试`
- footer 必须渲染在各自消息 item 内部，不允许做成悬浮层
- `重发 / 重试` 不新增用户消息，不重复计轮次
- 失败消息正文和 footer 当前必须随本地聊天窗口快照一起持久化；切后台 / 杀进程 / 重进后仍要一起恢复，不能只剩正文、只剩历史，或只剩用户消息

自动恢复：
- 依赖稳定 `client_msg_id`
- 优先走后端快照对账，不是重新发一轮
- 同一 `client_msg_id` 不允许并发多条恢复链
- 恢复失败后才回落到黑底胶囊 `回复未完成 · 点击重试`

## 9. 后端与运维边界

当前后端目录唯一真相：
- [server-go](D:/wuhao/server-go)

规则：
- 不再使用不存在的 `server` 目录口径
- 图片上传、会员、上下文、模型、摘要、恢复等后端能力，默认都以 `server-go` 为准
- `/api/chat/stream` 当前允许纯文字、纯图片、图文混合；只有文字和图片都为空时才拒绝
- `/api/chat/stream` 在纯图片且用户未输入文字时，会由后端给模型补一条内部说明：“用户本轮只上传了图片，未补充文字描述……”，让模型先基于图片可见信息给农业技术参考判断并追问必要信息；该说明不作为用户可见消息展示
- 旧 `/api/session/round_complete`、`/api/session/b`、`/api/session/c` 已废弃并返回 `410 DEPRECATED_ENDPOINT`；当前轮次归档、记忆文档和图片上下文都必须走 `/api/chat/stream` 后端主链，避免旧接口绕过额度扣减或重复触发摘要模型
- 以后如需 Codex 参与运维，优先走脚本、CLI、OpenAPI 这类可审计入口
- 阿里云 CLI / OpenAPI / Cloud Assistant 能完成的云资源查询、配置、部署、证书、Nginx、健康检查、环境巡检和低风险运维，默认由 Codex 直接执行并验证，不再让用户手工去控制台点或复制命令；本机阿里云 CLI 当前按用户授权的高权限 AccessKey 使用，不把“改成最小权限 CLI”作为当前阻塞项。真实密钥仍不得打印、写入仓库或聊天记忆；涉及新增付费资源、删除资源、停服、轮换密钥、改生产关键环境变量或可能中断公网服务的动作，Codex 先说明影响并等用户确认后再执行

优先级：
- ECS / SAE：脚本 / CLI / OpenAPI 优先；当前 SAE 应用已删除，首版部署优先 ECS，若后续重新启用 SAE 必须重新创建资源并同步更新本文和 runbook
- 数据库：首版按 RDS MySQL / MySQL 兼容数据库口径准备；人工查看优先 DMS，Codex 优先只读查询、迁移脚本、备份脚本。若后续升级到 PolarDB，再同步修改本文和 runbook
- 日志：优先脚本化查询，不靠临时手点控制台

如果后续长期让 Codex 辅助发版、回滚、查日志、查库，应把入口固化进仓库，例如部署脚本、回滚脚本、日志脚本、数据库只读脚本。整体 App / 后端 / 管理后台的长期协助运维蓝图以 [docs/runbooks/operations-blueprint.md](D:/wuhao/docs/runbooks/operations-blueprint.md) 为入口；ECS / RDS / OSS / Redis、真实后端部署、`api` DNS A 记录、`api` HTTPS、根域名官网、`admin` 后台域名 / HTTPS / Nginx / 后台账号、网站 ICP 备案、App 备案号、网站公安联网备案号下发、农技千查专用 SLS 最小日志集和 5 条 SLS AlertHub 最小告警、云监控资源水位邮件告警、ECS 系统盘自动快照、数据库只读 / 后端数据边界巡检脚本、公网黑盒只读巡检脚本、检查更新发布配置 / 发布历史已落地，未落地的是 App 公安备案、真机完整回归、公网黑盒自动定时通知、首封 SLS 告警邮件确认和更细的登录 / 模型用量趋势；未落地项不伪造服务地址、密钥或后台入口，只记录待回填项。

## 10. 参考文档

以下文档只作参考：
- [README.md](D:/wuhao/README.md)
- [docs/chat-ui-dynamic-interaction-logic.md](D:/wuhao/docs/chat-ui-dynamic-interaction-logic.md)
- [docs/chat-ui-clean-state-checklist.md](D:/wuhao/docs/chat-ui-clean-state-checklist.md)
- [docs/backend-boundaries.md](D:/wuhao/docs/backend-boundaries.md)

如果参考文档与本文件冲突，以 [AGENTS.md](D:/wuhao/AGENTS.md) 为准。

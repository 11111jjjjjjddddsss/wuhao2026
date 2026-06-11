# 当前未关闭风险

最后更新：2026-06-11

## R1 运维入口仍需生产后台和告警通知闭环

- 状态：未关闭
- 说明：`docs/runbooks` 已建立，`operations-blueprint.md` 也已把整体 App / 后端 / 管理后台的 Codex 协助运维范围固定下来；ECS / RDS / Redis / OSS / DNS / HTTPS / 部署脚本、Go 请求级日志、ECS 日志查询脚本、农技千查专用 SLS Project / Logstore 已经有一批真实入口。第一版网页后台代码已落地为 Vite `admin` 前端 + `server-go` `/admin-api/v1/*`，并新增后台账号、session、CSRF、角色校验和审计；生产入口已部署到 `https://admin.nongjiqiancha.cn/`，Nginx 静态托管并同域反代 `/admin-api/`，后台域名 HTTPS 证书已签发，owner 账号已通过一次性 bootstrap 初始化，bootstrap 环境变量已从 ECS 清理。
- 风险：生产后台已上线并能登录，SLS 也已接入 5 条最小 AlertHub 告警，但还不能把“长期运营后台完全闭环”写成完成态；SLS 外部通知 / 仪表盘、数据库只读脚本、客服正式坐席分配 / 标签、礼品卡批量发放 / 发放对象管理、检查更新发布记录等仍需继续补。
- 补充：管理后台总方案见 [management-backend.md](D:/wuhao/docs/runbooks/management-backend.md)，详细页面设计见 [admin-dashboard-design.md](D:/wuhao/docs/runbooks/admin-dashboard-design.md)，架构决策见 [ADR-0004-admin-backend-architecture.md](D:/wuhao/docs/adr/ADR-0004-admin-backend-architecture.md)。后台当前覆盖登录、总览、监控面板、产品洞察、用户、会员额度、订单、礼品卡、帮助反馈、App 日志、今日农情、检查更新、审计和服务健康；监控面板已聚合真实业务表、App 自动日志、审计、健康状态和地区分布，并补了“当前结论 / 就绪-需处理-阻塞 / 登录与账号ID / 礼品卡与权益 / 客服反馈 / App质量”决策卡，以及登录排障、检查更新排障卡和明天真机回归清单；产品洞察首版已补脱敏聚合趋势、反馈主题命中、App 事件分类、Top App 事件和礼品卡失败原因，不返回聊天全文、反馈正文、图片 URL、手机号、token、模型 Key 或礼品卡完整码；礼品卡后台已补汇总、完整卡码加密保存后页面查看复制、尾号 / 批次 / 账号ID追溯和失败原因聚合；帮助反馈已补 open / replied / closed 队列、状态筛选、搜索、关闭和重开；今日农情补跑、检查更新停更、礼品卡作废、反馈状态更新等写操作已有按钮忙碌态和失败弹窗，长字段会换行，降低管理层试用时的误操作和表格撑破风险。真机回归清单只把登录、聊天、图片、礼品卡、今日农情、检查更新和反馈入口串起来辅助测试，不代表这些 Android 真机链路已经全部验收完成。SLS 已有 `nongji-server-5xx`、`nongji-server-slow`、`nongji-nginx-upstream`、`nongji-daily-agri-failed`、`nongji-model-auth-config` 5 条 AlertHub 最小告警，但外部通知 / 仪表盘、登录精准漏斗、发布 / 回滚写操作、产品洞察日报 / 人工标签 / 处理状态仍未接入；内部共享密钥接口仍保留给脚本兼容，但浏览器后台不应持有内部 secret。
- 后续动作：补 SLS 外部通知 / 仪表盘和数据库只读脚本；继续把真实发版、回滚、查日志、查库、客服回复、礼品卡或会员运营入口沉淀到 runbook，并按最小权限继续拆角色。

## R2 项目记忆已有程序化检查，但覆盖仍偏粗

- 状态：未关闭
- 说明：仓库里已有 `scripts/check_project_memory.py`，CI 也会在关键真相文件变更时检查是否同步更新了项目记忆文件；但当前检查粒度仍是“至少改了一份 memory 文件”，还不会判断 `current-status / open-risks / pending-decisions / recent-changes` 之间是否互相一致
- 风险：即使 CI 通过，记忆文件之间仍可能出现轻微漂移或局部过期，影响新窗口对“当前真相 / 已关闭风险 / 仍待决策”的判断
- 后续动作：后面视情况继续把校验收紧到“按变更类型要求更新特定文件或特定章节”，减少文档之间互相打架

## R3 GitHub 协作层尚未结构化

- 状态：未关闭
- 说明：目前仓库已有 CI，但还未补充 Issue 模板、PR 模板、CODEOWNERS、项目字段等协作骨架
- 风险：跨窗口协作能接住仓库内真相，但任务排队、责任归属、变更说明仍不够结构化
- 后续动作：需要时再按最小改动补 GitHub 协作层

## R4 聊天运行时正向列表主链已基本稳定，仍保留边角观察

- 状态：未关闭
- 说明：当前聊天消息运行时已按用户拍板从反向列表切回单一正向 `LazyColumn` 主人：`ChatRecyclerViewHost.kt` 不再使用 `reverseLayout` / `items.asReversed()`，`messages` 仍按 oldest -> newest 直接显示，最新消息在 `lastIndex`。mixed active-zone / overlay、小分割 list itemization、streaming `scrollBy` / `dispatchRawDelta` 高度补偿都不在主链运行
- 已验证：2026-04-28 用户真机反馈当前滚动链“确实很稳”。核心链路包括：`SideEffect` 同帧底部锚定压 streaming 下一行冒头闪；用户滑回正向列表物理底部后快速恢复 AutoFollow；96dp 工作线以下空白按当前贴底链露出；上滑 / 下滑整体不再抢手
- 剩余风险：仍需在更长回复、不同输入法 / 设备、含免责声明答案、冷启动首次点输入框等边角场景继续观察；如果再次出现冒头闪、尾部收口微动或手动回底不跟随，下一刀优先检查当前正向列表底部锚点、reveal 提交节奏、工作线 bounds、contentPadding 与 item padding 的几何关系，不恢复 overlay、小分割或 raw delta
- 风险补充：pending finalize 仍不主动 bottom align，主要依赖两阶段 fresh bounds、unified soft-wrap renderer 和 streaming 免责声明几何占位。若含免责声明答案收口仍微跳，优先在同一消息主人内复核高度来源，不恢复完整 `scrollToBottom(false)` 精修
- 风险补充：composer 当前已完成 P0 拆链、取消发送旧高度锁、统一收键盘路径为 `clearFocus(force = true)`，并删除死链 overlay prewarm snapshot 协程。若真机仍有输入框残影或冷启动首点迟钝，下一步才评估是否需要 `WindowInsetsAnimationCompat`；不要把 IME padding 挪到根容器去抬升消息列表，也不要动滚动主链
- 风险补充：极端恢复链已做低风险兜底：断网连续发送同文复用已有失败用户消息；远端 streaming 重启恢复失败会补 assistant 重试入口；切后台会清消息 / 输入选择菜单；`SessionApi` 保留 active SSE call 引用直到读循环退出，方便 reset / cancel。剩余观察点是：弱网下 OkHttp `readTimeout(60s)` 仍可能让小球等待较久；额度用完前端锁当前是本地会话级，App 被系统杀掉后仍以第一次后端 quota 返回为准；图片入口首版已接入相机 / 照片、后台下采样预览、压缩上传和 URL 恢复显示，相机 FileProvider URI 已补读写 flags、ClipData 和显式包授权，但仍需继续真机验证第三方相机 / 相册 URI、弱网多图上传失败、后端图片生命周期和 OSS 权限
- 后续动作：把当前正向列表滚动链作为稳定基线保留；短期只做边角验证和低风险清理，不再主动重构滚动主链

## R5 聊天页主文件偏重但暂不影响运行时主链

- 状态：未关闭
- 说明：`ChatScreen.kt` 当前承担聊天列表装配、滚动几何、发送 / 恢复、图片导入 / 重试、会员入口接线和 debug-only 文案预览等多类职责，文件偏长，后续维护成本高。当前已排查：会员中心 UI 本体在 `MembershipCenterSheet.kt`，图片全屏预览在 `ImagePreviewPager.kt`，都没有并入滚动主链；旧 active-zone / overlay / raw delta 方案未在运行时残留
- 风险：这属于可维护性风险，不等于用户端一定卡顿。真正运行时风险仍来自高频测量、Selection、Markdown、图片缩略图解码和滚动锚点状态；若为了“瘦身”贸然拆滚动几何或发送事务，反而可能把已稳定的工作线 / AutoFollow 打坏
- 后续动作：短期不拆 debug-only 文案预览面板，不动滚动主链。后续若要瘦身，优先搬无状态 / 低耦合 UI 片段，例如消息菜单、用户图片 strip、debug 预览 UI；不要第一刀拆 `commitSendMessage`、两阶段 finalize、滚动 coordinator 闭包或 `ChatRecyclerViewHost`

## R6 外部会诊仍依赖人工转发上下文

- 状态：未关闭
- 说明：小米 / MiMo 免费版、Claude 等外部模型默认看不到本地仓库，只能依赖用户通过聊天软件转发的代码片段、日志和截图；即使仓库内规则已收紧，会诊结果仍受转发上下文完整度影响。当前用户偏好已调整为：本项目后续外部会诊默认优先整理给小米 / MiMo
- 风险：如果外发内容不自包含，对方仍可能脑补仓库结构、假设不存在的接口，导致方案听起来合理但无法直接落地
- 后续动作：继续坚持“问题说明 + 关键代码片段 + 明确追问 + 已排除项 + 限制条件”的短稿格式；尤其发给小米 / MiMo 时要把当前真实代码结构、关键函数 / 状态和不能碰的旧方案写清楚。收到方案后先由 Codex 对照当前代码核验再下刀

## R7 真实后端已部署，公网正式上线条件仍未闭环

- 状态：未关闭
- 说明：首版部署路线已从“SAE 镜像托管优先”转向“ECS 传统部署优先”。ECS `i-2ze5nrem0jrchln4f0eh` 已购买并运行，位于 `华北2（北京）/ cn-beijing` 可用区 L，规格 `ecs.u1-c1m2.large`（2 vCPU / 4 GiB），Ubuntu 22.04 64 位，公网 IP `39.106.1.151`，私网 IP `192.168.1.237`，生产 VPC `nongjiqiancha-prod-vpc` / `vpc-2zeax2zowza2398b9dzot`，生产交换机 `nongjiqiancha-prod-beijing-l` / `vsw-2zemsq82lj2kp8za90aky`，安全组 `sg-2ze4tilwxw1h5w77lwl1`，固定公网带宽 5 Mbps，CLI 当前显示到期时间 `2027-06-01T16:00Z`；2026-05-30 已清理北京区空闲默认 VPC / 默认交换机和旧 SAE 自动交换机，当前只保留生产 VPC / 交换机。已初始化系统用户、部署目录、systemd、Nginx 反向代理、基础限流、fail2ban 和 logrotate，`server-go` 已部署并运行。Go 服务已收口为只监听本机回环地址，当前用 `nongji-server-3000.service` / `nongji-server-3001.service` 双端口 slot 发版，由 Nginx 在 `127.0.0.1:3000/3001` 之间切换，公网仍统一只走 Nginx；通用 JSON body 默认只读取 64KiB，App 日志接口单独限制 8KiB 且超限返回 413；模型出站请求已限制拨号、TLS 握手、响应头等待和空闲连接，SSE 响应已带 `X-Accel-Buffering: no`，主聊天流有 30 分钟最长持续时间兜底，记忆文档摘要非流式模型响应限制 64KiB，今日农情 JSON 响应限制 1MiB；服务启动迁移已加 MySQL 全局命名锁，迁移锁释放失败会作为启动错误暴露。阿里云 DNS 已创建 `api`、`@`、`www` A 记录指向 `39.106.1.151`；2026-06-05 截图确认网站 ICP 备案已通过，主体备案号 `京ICP备2026031728号`，网站备案号 `京ICP备2026031728号-1`；2026-06-05 已通过 Let’s Encrypt / certbot 为 `api.nongjiqiancha.cn` 配置 Nginx 443 HTTPS，公网 HTTPS healthz 返回 200；2026-06-06 已为根域名官网 `nongjiqiancha.cn` / `www.nongjiqiancha.cn` 配置 Nginx 静态站和 HTTPS，公网返回 200。2026-06-05 20:03 左右 App 备案订单已提交阿里云初审，订单号 `2036780517515`，页面提示预计 2026-06-07 20:00 前审核，App 备案号尚未下发；2026-06-10 网站公安联网备案号已下发：`京公网安备11010602202723号`，App 公安备案待 App 备案通过 / 正式信息齐后再补；公安备案数据码不进入仓库；同日已通过 Cloud Assistant 将 DashScope 主 / 副模型 Key 写入 ECS 主备槽位并重启服务，`/healthz` 显示 `bailian=ok`，真实 Key 值不进入仓库。RDS MySQL 实例 `rm-2zes3vmj76p85n8g1` 已创建数据库 `nongjiqiancha`、应用账号 `nongji_app`，白名单为 `192.168.1.237`，服务启动已完成迁移。旧 SAE demo 应用已删除，SAE `ListApplications` 为空。域名 `nongjiqiancha.cn` 已购买，用户口头确认实名认证 / 模板审核已通过，网站 ICP 已通过，App 备案已提交阿里云初审但尚未通过；网站公安备案已通过，App 公安备案和真机生产 API 回归仍未完成。OSS 标准-本地冗余存储包（华北2）100GB 已购买并生效，Bucket `nongjiqiancha-prod` 已在北京创建为私有标准本地冗余，并配置 `uploads/` 3 天、`support/` 30 天生命周期；`server-go` 已新增 OSS 上传存储后端，生产 ECS 已配置 OSS 环境变量并通过 `upload_storage=oss` 验证，当前会写私有 OSS。SLS 服务本体已开通且农技千查专用 Project / Logstore 已接入最小排障集：Project `nongjiqiancha-prod-1159547719787456`，Logstore `server-go` / `nginx-error`，TTL 7 天、1 shard，ECS Logtail 采集 `/var/log/nongjiqiancha/server.log` 和 `/var/log/nginx/error.log`；当前未购买 SLS 节省计划 / 资源包，已创建 5 条 AlertHub 最小告警，但外部通知 / 仪表盘仍未配置。北京区仍有 3 个阿里云系统 / 产品托管 Project，2026-05-30 已复查为云监控 / XTrace / 产品数据类，不直接删除。Redis 开源版实例 `nongjiqiancha-prod-redis` / `r-2zet46zvmoo9wu3bic` 已购买并运行，256MB、Redis 7.0、标准高可用主备、同生产 VPC / 北京可用区 L，白名单已限制到 ECS 私网 IP；`server-go` 已新增可选 Redis 客户端和短期限流 / 临时认证状态，当前主聊天用户级频控已可走 Redis，但主聊天内容、额度、归档、摘要和订单仍不依赖 Redis；PolarDB 暂作为后续高规格升级选项
- 风险：后端已能运行但还不是完整生产可用：模型 Key 主 / 副槽位已配置且 `/healthz bailian=ok`，但主聊天、图片问诊、今日农情还需要用真实 App token / 真机链路回归，仍可能暴露模型权限、额度、联网搜索、模型拉图或限流问题；`AUTH_STRICT=true` 已保护公网业务入口，手机号账号 / per-user token 骨架已落地，融合认证 SchemeCode / 包名 / 签名、DYPNS 基础凭证、短信签名和验证码模板已写入生产环境，`/healthz` 已显示 `dypns=ok / dypns_fusion=ok / dypns_sms=ok`，Android 融合认证 SDK / AAR 已接入登录页，但真机运营商一键登录和验证码登录完整回归仍未闭环，已暴露过的主账号 AccessKey 上线前必须轮换；Redis 认证限流已部署到 ECS 并通过 `/healthz redis=ok` 验证；网站 ICP 已过，根域名官网和 API HTTPS 已完成，App 备案已提交但尚未通过，网站公安备案号已下发并已补官网 footer，App 公安备案待补，App 生产 API 地址仍需真机回归后再稳定切过去；OSS Bucket、代码后端和生产 ECS OSS 环境变量均已准备好，当前线上上传后端已切到 OSS；后续加第二台 ECS 或回到 SAE 多实例前仍需用真实 App 链路验证 OSS 上传 / 读取 / 模型拉图；多实例前摘要仍需要跨实例保护；当前双端口发布只降低单机重启空窗和流式连接被立即踢掉的概率，不等于多机高可用或数据库迁移可自动回滚；公网 SSH 22 端口已撤销安全组放行且 ECS 本机 `ssh` 服务已停用，生产运维优先走阿里云 CLI + Cloud Assistant，若后续临时开 SSH 必须限定固定来源 IP 并用完关闭
- 补充：后端已支持 `DASHSCOPE_API_KEY_1/2/3` 主备 Key 池和限流前置切 Key，`DASHSCOPE_API_KEY_1` 健康时优先作为主 Key 使用，`DASHSCOPE_API_KEY_2` 可作为副 Key；但真实并发扩容必须使用不同阿里云主账号的 Key，同一主账号多个 API Key 共享 RPM / TPM 限流。朋友账号 Key 可短期兜底，但长期生产会带来账单、权限、密钥轮换和数据处理责任不在自己名下的运维风险
- 补充：2026-06-06 资源巡检确认当前 ECS / RDS / Redis / OSS 规格和用量都很宽裕，暂不需要立刻升配；同日发现一次 502 的根因是双端口发布的多个 `nongji-drain-stop-*` transient 排空任务叠加，而不是资源不足。已恢复 active slot，并把部署 / 回滚脚本改为发布或回滚前先清旧 drain 任务；readiness 脚本已改为 active slot inactive 或 Host healthz 非 200 时直接失败。Go 后端已补请求级日志和 ECS / SLS 查询脚本，SLS 当前已接入最小排障采集和 5 条 AlertHub 最小告警，剩余风险是还没有外部通知、仪表盘、资源水位告警和自动自愈，用户仍可能比系统更早发现部分 502 或资源异常
- 补充：2026-06-06 免费优先安全加固已执行：阿里云云安全中心免费版显示 ECS agent online、当前风险计数 0；阿里云 DDoS 基础防护按官方口径默认免费开启；Nginx 全局安全头兜底已补，官网部署脚本会保留 CSP / HSTS / Permissions-Policy；API HTTP 80 已改成 ACME challenge + 301 HTTPS 跳转；后端内部 secret 入口已补 IP / Redis 频控，主聊天 `client_msg_id` 已补 128 长度上限。当前不先买付费 WAF、云防火墙或 DDoS 高防，后续如果出现持续 Web 攻击、CC、基础 DDoS 防护触发黑洞或运营成本异常，再按 [security-hardening.md](D:/wuhao/docs/runbooks/security-hardening.md) 和 [security-threat-model.md](D:/wuhao/docs/runbooks/security-threat-model.md) 升级
- 补充：买服务器前高并发巡检结论已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。Go 语言本身不是当前瓶颈；首版单实例可跑早期。OSS 资源包、Bucket 生命周期、代码存储后端和生产 ECS OSS 配置已经就绪；若要多后端实例，仍需先用真实 App 链路验证 `/upload`、`/uploads/` 与模型公网拉图。数据库迁移已补 MySQL 全局锁，后续若进入正式多实例滚动发布，再考虑升级为单独迁移步骤和 `schema_migrations` 表
- 后续动作：下一步优先跟进 App 备案审核、App 公安备案、手机号登录、主聊天和图片问诊真机回归；上线前轮换已暴露过的主账号 AccessKey，优先改成专用 RAM 用户 / 最小权限凭证。网站 ICP 备案号和网站公安备案号已补到官网 footer；App 备案通过后再补 App 备案编号。OSS 上传后端环境变量和最小权限 RAM 凭证已写入 ECS，`/healthz upload_storage=oss` 已验证；继续验证真实 `/upload`、`/uploads/`、模型拉图和主聊天流。备份当前有 7 天默认策略，正式数据进入后再确认是否延长保留时间或加密 / 跨地域备份；SLS 当前已接 Go 后端请求日志、Nginx error log 和 5 条 AlertHub 最小告警，后续要补的是外部通知、仪表盘、资源水位告警和后台排障入口，而不是全量采集所有日志。若后续重新启用 SAE，则再回填 [deploy-sae.md](D:/wuhao/docs/runbooks/deploy-sae.md) 的镜像构建、环境变量、健康检查和回滚入口。按 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md) 固化模型 Key 所属账号、充值告警和轮换责任；RDS 规格确认后再按真实连接数配置 `MYSQL_MAX_OPEN_CONNS` 等连接池环境变量。当前本机阿里云 CLI 可读 ECS / RDS / OSS 资源包 / SLS 开通状态并可用 Cloud Assistant 运维 ECS；真实 AccessKey、公安备案数据码不进入仓库或文档，后续稳定后应轮换已暴露过的主账号 Key

## R8 结构化长期资产抽取尚未落地

- 状态：未关闭
- 说明：`server-go` 已新增 `session_round_archive` 保存成功完成轮次，并按 30 天滚动保留；`/api/session/snapshot` 的 `a_rounds_for_ui` 可优先返回 30 天内最近 30 轮归档。当前只保留一份自然语言记忆文档，按“短期承接 / 长期背景 / 用户画像 / 农业重点事件”四段覆盖更新；这些原始归档暂只是“可恢复 / 可批处理”的材料，尚未拆成独立结构化字段。
- 风险：如果后续迟迟不做批量抽取，30 天外原始问答会被滚动删除；长期护城河目前先停留在一份自然语言记忆文档，尚未形成更稳定的结构化长期背景、用户画像和农业重点事件记录。
- 后续动作：后续评估结构化记忆 schema、更新频率和抽取模型，优先用归档记录做离线 / 低频批处理，不在当前第一刀里实时抽取、不把归档内容每轮喂给模型；图片文件本身的 OSS / 本地 uploads 生命周期还需单独按成本和隐私策略配置。

## R9 图片发送后台兜底仍受系统调度和租约窗口影响

- 状态：未关闭
- 说明：Android 当前已为带图片发送接入 WorkManager 延迟兜底，覆盖“图片已进本地消息但 App 被杀、前台没来得及可靠送到后端”的场景；它不接管正常前台 SSE 直播，也不直接写 UI 聊天窗口。前台活跃标记、唯一 work 名、远端启动保护窗，以及后端 `chat_stream_inflight` 进行中锁 + lease token 共同用于避免同一 `client_msg_id` 重复启动上游模型流。后端 replay 真源已改为 `session_round_ledger` / 轮次归档成功，服务端只在归档成功后才向客户端发送 SSE `[DONE]`，避免“客户端已收完成态，但回答没归档”时返回空 replay
- 补充：额度扣减在轮次归档成功后执行；若 `ConsumeOnDone` 临时失败，服务端会按同一 `client_msg_id` 短重试，重复扣由 `quota_ledger` 唯一键防住。replay 现在只恢复已归档答案，不再按当前档位 / 当前日期补扣旧轮次，避免跨日或会员档位变化后误扣。若归档后扣减失败且短重试仍失败，当前更偏向“漏记你一次成本”，不把风险转成“乱扣用户次数”
- 补充：主模型自动开流重试已从 2 次收紧为 1 次，Android 前台流的自动 stream retry 已关闭。WorkManager 后台兜底只针对同一条 pending 图片消息，在图片上传失败、网络中断、流异常结束、`409 STREAM_IN_PROGRESS`、限流或临时上游错误时用同一 `client_msg_id` 退避重试；普通可恢复失败最多重试 5 次后移除 pending，避免弱网一抖就丢消息，也避免无限反复开流。`chat_stream_inflight` 获取结果改为校验 lease token，数据库新增同一 `user_id` 活跃流唯一约束，降低不同 `client_msg_id` 并发绕过额度预检查并多开 Qwen3.5-Plus 的风险；旧 `/api/session/round_complete`、`/api/session/b`、`/api/session/c` 已返回 410，不再参与主链
- 补充：买服务器前“主聊天与图片发送”巡检已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)，当前没有发现旧直连模型、旧完成接口、旧 active-zone、旧图片手势或旧上传通道在运行时并存；后续风险主要集中在真实公网 https 图片链、首版单实例 / OSS 取舍、弱网多图、后台恢复和多实例进程内保护迁移
- 补充：买服务器前“记忆文档与模型调用”巡检已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)，当前记忆文档触发频率、`pending_retry_b`、60 秒超时和 `round_total` 写回校验都与现行口径一致；摘要固定 `qwen-plus`，不再按层灰度。多实例前仍要补摘要数据库 claim / lease，避免重复调用当前配置的摘要模型和同一 `round_total` 下的非确定性覆盖。
- 风险：WorkManager 不是实时任务，系统可能延后执行；后端进行中锁当前用 30 分钟租约防死锁，若服务进程极端卡死且租约过期，后续请求才会重新接管。这个设计优先保护成本，不承诺像前台直播一样立刻可见；如果主模型上游已经被调用、但轮次还没归档就发生进程崩溃 / 上游断流 / 归档失败，同一 `client_msg_id` 后续仍可能重新开一次 Qwen3.5-Plus；当前已把自动开流重试限制在后台同一 pending 消息的有限次数内，但正式上线前若要进一步压成本风险，需要增加持久化 attempt / status 表或更完整的后端任务恢复链。如果模型已经吐完但归档写库本身失败，客户端不会收到完成态，该轮仍可能需要用户重试或人工排障。若服务进程在归档后、扣减前崩溃且短重试也失败，仍可能出现单轮成本漏记；后续若真实上线，应补后台对账任务或按业务日志做周期性巡检。记忆文档摘要当前已有 60 秒单次提取超时、本进程运行中保护和 `round_total` 写回校验，但多后端实例下仍可能重复调用当前配置的摘要模型，后续需要数据库 claim / lease 或确认首版单实例部署。
- 后续动作：短期把这版作为保守兜底真机观察，重点看切后台 / 杀进程后的图片消息是否能恢复、UI 是否不会消失；如果后续要做到 App 被杀后也像前台一样实时可见，需要后端提供更完整的进行中状态查询 / 结果缓存，再决定是否升级成长任务或服务通知方案

## R10 手机号登录生产接入仍需收口

- 状态：未关闭
- 说明：当前后端业务接口和 `/upload` 都要求身份头 / token。服务端已新增手机号账号表、登录 session 表、旧本机 `user_id` 迁移表和 v2 bearer token；Android 已新增登录门、账号 token 保存、验证码登录 UI 和调后端短信 / 融合认证接口的客户端代码。登录成功后使用账号ID `acct_...`，底层字段仍可能叫 `user_id`，生产语义统一按账号ID理解；手机号只是注册 / 登录凭证和脱敏展示信息，不作为业务主键。旧本机 UUID 只作为受控迁移桥，Android 一键登录和短信登录 payload 会提交 `legacy_user_id`；服务端只接受本机 UUID 形态 legacy ID 或可由旧 bearer token 证明的 legacy ID，不接受 `acct_...` 作为 legacy bridge，避免账号之间互相合并
- 补充：当前仍兼容 Android 早期阶段的裸 `X-User-Id` 本机身份兜底，方便迁移期联调；Android 已移除 `SESSION_API_TOKEN` 静态注入和运行时登录绕过，正式登录只使用后端按真实手机号账号签发的 per-user session token
- 补充：Redis 认证限流已覆盖融合认证 token 获取、融合认证登录校验、短信发送和短信登录校验；fusion token / fusion login 按 IP hash 限流，短信发送按手机号 hash + IP hash 和 IP hash 两层限流，短信登录按手机号 hash + IP hash 限流，避免 SDK 接入后刷 token、伪造 verify token 或轮换手机号直接消耗阿里云认证资源
- 风险：这还不是完整可上线登录：阿里云 `CreateSchemeConfig` 已返回 SchemeCode，且 DYPNS AccessKey / Secret、`DYPNS_FUSION_SCHEME_CODE`、包名、签名、短信签名和验证码模板已写入本机密钥文件与 ECS 环境；Android 一键登录 SDK / AAR 已导入并接到登录页，源码 manifest 仍显式声明 `READ_PHONE_STATE` 和设备网络 / Wi-Fi 状态权限；2026-06-08 已修复一键登录 SDK 初始化顺序导致的“一点就退”NPE 崩溃，并把本机 debug 测试包改为有 release 签名时同签名开启一键登录、缺签名才退验证码；2026-06-11 又补了三层保底：点击一键登录时按需申请 `READ_PHONE_STATE`，拒绝就直接切验证码；`vpn_active`、`no_active_cellular` 从 warning 升为前置拦截，避免明显高风险环境继续硬拉授权页；manifest 覆盖 `com.mobile.auth.gatewayauth.LoginAuthActivity` / `PrivacyDialogActivity` 主题为本地 translucent 兼容主题，尽量降低荣耀 / 华为 / Android 15+ 授权页拉起闪退风险。SDK 授权页已接自定义 UI，避免默认页按钮、其他手机号登录和协议区重叠，SDK 协议勾选框保持可见且默认未勾选；短信发送参数也已进一步收口为“只有显式配置 `DYPNS_SMS_TEMPLATE_PARAM` 时才传 `TemplateParam`”，避免验证码模板变量不匹配直接触发“非法参数”。但完整真机运营商网络回归仍未闭环，尤其需要确认荣耀 / 华为 / Android 15+ 在重新加回按需电话权限审批后授权页能稳定拉起并完成最终登录。当前 Android 100001 一键登录主链不再调用半程 verify-only，只在最终 `onVerifySuccess` 后由 `/api/auth/fusion/login` 消费一次 token；仍需用真机确认授权页可稳定拉起、最终登录成功、失败 / 取消会回落验证码。验证码登录仍需真实手机号收码 / 登录回归；已暴露过的主账号 AccessKey 需要在上线前轮换；Redis 认证限流已部署到 ECS 并健康；旧 `X-User-Id` 兜底在公开生产仍需严格关闭，旧无 `session_id` bearer token 也不应在公开生产兼容。所有会员、额度、加油包、订单、礼品卡、帮助反馈、App 日志、记忆文档和聊天归档都必须继续收敛到同一个账号ID，后续接支付不能再按手机号或本机 ID 分叉
- 后续动作：正式公开上线前必须完成 AccessKey 轮换、HTTPS 公网入口下的真机登录回归和旧 `X-User-Id` / 旧 bearer token 兜底隔离；DYPNS 基础凭证、SchemeCode、包名、签名、短信签名、验证码模板和 Android SDK 客户端链路已配置；Redis 验证码 / 限流已接入；`scripts/check-android-build-parity.ps1` 和 GitHub Android CI 已负责拦截 debug / release 后端、签名、一键登录主链、明文网络配置和 debug-only 预览面板再次分叉；生产环境保持 `APP_SECRET` 与 `AUTH_STRICT=true`，不要设置 `AUTH_ALLOW_LEGACY_TOKEN`

## R11 历史模型 Key 轮换确认

- 状态：未关闭
- 说明：Android 旧直连模型链和 `BuildConfig.API_KEY` 注入已从代码中清理，主链只允许经由后端 `/api/chat/stream` 调模型
- 风险：如果历史调试 APK / 旧包曾经打入真实模型 Key，仍应按密钥可能泄露处理；这不是当前代码风险，但属于上线前安全检查项
- 后续动作：确认历史包是否曾打入真实 Key；若有，轮换对应模型服务密钥。后续代码变更禁止重新引入 Android 客户端模型 Key 注入或直连模型客户端

## R12 会员订单接口仍需接真实支付回调

- 状态：未关闭
- 说明：Android 会员中心当前只展示支付占位提示，不会调用后端下单 / 续费 / 升级 / 加油包接口；`server-go` 里现有 `/api/tier/renew_plus`、`/api/tier/renew_pro`、`/api/tier/upgrade_plus_to_pro`、`/api/topup/buy` 仍是开发期直接变更接口，但默认已返回 `PAYMENT_NOT_CONFIGURED`，只有显式设置 `ALLOW_DEV_ORDER_ENDPOINTS=true` 且当前环境明确为 `local / dev / development / test` 时才允许本地调试，缺失环境名也按关闭处理
- 风险：这些接口仍不是正式支付真源；如果内测环境误开 `ALLOW_DEV_ORDER_ENDPOINTS=true`，非 App 客户端理论上仍可绕过真实支付直接请求会员变更。这不影响当前 Android UI 展示，也不影响每日额度 / 升级补偿 / 加油包扣次顺序本身，但属于上线前必须继续收口的业务安全风险
- 补充：会员/额度巡检结论已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。当前 Android 开通 / 升级 / 加油包按钮只提示“支付功能暂不可用”，没有调用订单接口；因此现阶段能继续作为规则展示，但买服务器本身不等于会员可以直接开卖
- 补充：买服务器前“支付真实接入”巡检已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)，并新增 [payments.md](D:/wuhao/docs/runbooks/payments.md)。当前没有真实支付渠道、SDK、自动续费、退款或对账；`orders` 表也只是开发期成功结果记录，不是正式支付订单表。管理后台订单页已能按账号ID或全局最近记录只读查询 `orders`，用于核查开发期会员变更 / 权益来源，但不提供补发、退款、对账或手动改权益
- 后续动作：接入真实支付时，把会员变更收敛到服务端验签后的支付回调 / 对账流程，并移除或彻底隔离开发期直接变更接口；生产环境保持 `ALLOW_DEV_ORDER_ENDPOINTS` 未设置 / false，并配置 `APP_ENV=production` 或等价环境变量作为额外保险。真实收费前还要接手机号登录 / token，把本机 `user_id` 与账号绑定，并开启 `AUTH_STRICT=true`。正式订单表需覆盖渠道订单号、金额、商品、状态、回调、退款和幂等发放结果

## R13 今日农情生成质量和失败告警仍需上线观察

- 状态：未关闭
- 说明：今日农情首版已接入独立后端链路和 Android UI-only 卡片；它不影响聊天主链、不扣用户问诊次数、不进入记忆文档或归档。当前生成固定 `qwen3.5-plus + OpenAI compatible chat/completions + enable_search=true + search_strategy=turbo + forced_search=true + enable_source=true + enable_thinking=false`，不保留其它模型候选或环境变量切换入口。生成前会把过去 7 天已 ready 的今日农情喂给模型要求去重；提示词版本 `2026-06-11-v59` 已按“今日农情最大限度放开，但养殖水产不要，适合普通群众阅读”收口，提示词目标仍是 3 条成稿内容、标题一行、摘要约 90-130 字、至少两句、短来源名、不带链接，并要求写清新闻和种植生产、农资、农时、防灾或流通的关系；价格类标题和摘要都要写出具体作物、品类、产区、市场或农资对象，不用“农产品价格”“批发价微降”“市场价格变化”这类泛泛标题独立成条；发布和 Android 展示不再把正好 3 条作为硬闸门，至少 2 条标题 / 摘要完整 item 即可成卡，1 条视为不完整。v59 大类守种植侧，小类不硬卡，天气、农时、价格、政策、补贴、平台建设、技术推广都可以用；这些只是选题方向，不是硬配额，不能为了凑类别牺牲真实性、时效性或种植侧相关性。养殖水产边界按材料主体判断，不按单个词机械判断；材料主体是养殖、水产、饲料或饲用原料则换成种植侧材料。服务端当前只保留 JSON 结构、至少 2 条标题 / 摘要非空 item 和私网 / 本机 URL 安全兜底；广告软文、假新闻、养殖水产、重复事件和标题党主要靠提示词、探针、后台运营复核与日志观察控制。生成最多 2 次，第二次只在首轮解析失败后换检索提示补救，仍走 `turbo`，不切 `agent`；兼容 Chat 链路通常没有结构化搜索来源列表，来源名主要来自模型 JSON 的 `source_name`，来源 URL 只作为内部追溯和后台排查，不下发给 Android 用户卡片。ECS systemd timer 和后台补跑都已落地。
- 风险：如果 `turbo` 返回的材料质量差、主题偏养殖水产、广告软文多或信息过期，模型仍可能产出不理想；后端当前不靠内容拦截解决这些问题，只有模型输出无法解析成 JSON 或少于 2 条标题 / 摘要可展示 item 时才不发布，前端静默不展示。`qwen3.5-plus` v59 链路仍需持续用生产探针确认 `ok_count`、`model_search_count`、`model_reasoning_tokens`、标题长度、来源名、过期材料、综合指数 / 综合行情凑数和养殖水产 / 广告软文是否被提示词压住；兼容 Chat 链路的 `source_count=0` 不等同于没联网。2026-06-11 v52 生产探针 `runs=3` 得到 `ok_count=3/3`，单次 total tokens 约 6.3k-6.6k，无 reasoning tokens，样本未见链接下发、代码块、养殖水产主体或明显广告软文；v58 虽把边界压得更稳，但线上仍出现 73-79 字的偏薄摘要，因此 v59 继续只做轻提示词加压，不加后端字数过滤，把“宁可接近 100 字，也不要 70 多字薄摘要”和“第二句不能只剩短收尾”写进主提示词。当前剩余观察点已经比较明确：要继续盯摘要是否偶发掉回 70 多字，以及价格类是否又退回泛标题。联网搜索会增加输入 token，并且搜索策略本身也有调用费用，所以仍要坚持“云端每天定时一次 + 后台补跑兜底”，不能改成用户打开 App 时临时生成。当前仅有 `scope=CN` 全国卡片，尚未做地区 / 作物个性化；`nongji-daily-agri-failed` AlertHub 告警已落地，但外部通知、自动重试策略和运营抽查节奏还需要继续收口。
- 补充：买服务器前今日农情巡检已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。当前没有发现用户打开 App 临时生成、写记忆文档、写归档、扣次或伪装成 `ChatMessage` 的旧链路；残余风险主要是质量运营，比如同一事件换标题、养殖水产漏入、广告软文和假新闻需要靠提示词、探针、后台人工抽查与后续告警约束。当前检索阶段已改为全网宽搜、不限定固定网站，发布阶段也不再用可信域名 / 近 7 天链接 / 主题词 / 正好 3 条做内容拦截；如果模型没有输出可解析 JSON 或少于 2 条标题 / 摘要完整内容，用户侧会静默不展示，需要通过后台补跑或内部探针排查。
- 补充：2026-06-09 已补今日农情坏缓存兜底：用户侧只展示 JSON 合法且结构完整的 ready 卡片，坏 `content_json` 不再把 `/api/today-agri-card` 打成 500；后台今日农情列表会把坏 `content_json / sources_json` 标成行内错误；补跑遇到 ready 但正文不可用的卡片时允许重新生成覆盖。该护栏只解决坏数据可恢复和后台可见性，不代表内容质量、自动告警或个性化已经闭环
- 后续动作：持续观察 SLS 日志关键词 `daily agri generation started` / `daily agri model response received` / `daily agri model output not displayable` / `daily agri card generated` / `generate today agri card failed`，并抽查 `daily_agri_cards` 当天 `status/content_json/error/model/search_strategy/prompt_version`。若连续失败，优先评估调整主提示词、增加探针观察、后台人工复核 / 补跑、自动重试和外部通知；不要回到可信域名白名单、固定站点限制、旧 `prompt_intervene` 路线、后端内容过滤或用户打开 App 临时多次调模型

## R14 帮助与反馈首版仍需客服工单能力补齐

- 状态：未关闭
- 说明：帮助与反馈当前已具备后端消息表、轻量会话状态表、用户侧历史 / 发送 / 已读接口，内部读取 / 回复接口，以及网页后台会话列表、详情、状态筛选、搜索、回复、关闭和重开入口；Android 也能展示历史、发送文字 / 图片反馈，并在设置页“帮助与反馈”行用红点提示未读后台 / 系统消息。
- 风险：当前仍不是完整客服系统，缺正式坐席分配、标签、站外推送、客服绩效、SLA、自动归档和专用图片生命周期。公开生产前仍必须保持账号 token 和 `AUTH_STRICT=true`，否则裸 `X-User-Id` 能读写某个用户的帮助与反馈；当前帮助与反馈图片仍复用 `/upload` -> `/uploads/*.jpg`，实际按问诊图 3 天生命周期处理，`support/` 30 天生命周期只是 OSS 预留规则；用户连续并发发送时自动回复仍可能存在重复插入窗口，正式客服面板前应继续收口事务 / 用户级锁。
- 后续动作：在统一管理后台继续补坐席分配、标签、站外通知、客服绩效和消息保存 / 删除规则；不要在 Android 端塞后台逻辑。后续若需要客服图片保留更久，应新增 support 专用上传目的或接口并切换 Android 帮助与反馈图片链；账号注销 / 数据删除规则还要明确帮助与反馈消息和图片是否删除、保留多久、由谁操作。

## R15 自有 APK 更新链路需要上线实机验证

- 状态：未关闭
- 说明：Android “检查更新”已按自有服务器 APK 分发接入，后端通过 `APP_ANDROID_*` 环境变量或后台 `app_release_configs` 返回最新版本、https APK 地址和可选 SHA-256，客户端下载后会先校验最终 https、文件大小、SHA-256、包名和 `versionCode`，再调起系统安装页；Android 会用 `app_update.*` 自动日志上报检查、下载、校验、安装权限和安装页拉起阶段，后台监控面板已有“检查更新排障”卡。
- 风险：普通 Android App 不能静默安装 APK；Android 8+ 需要用户给本 App “安装未知应用”授权，不同国产 ROM 的授权页和安装页可能存在差异。APK 下载地址、签名证书、`versionCode` 递增、https 证书、包名一致性、哈希配置和安装权限都需要真实环境验证。若后续发布 APK 签名证书变化，系统会拒绝覆盖安装；如果用户已经安装坏包，只能再发更高 `versionCode` 修复包，不能低版本回滚覆盖。后台排障日志能定位阶段，但不能替代旧包真机覆盖安装回归。
- 后续动作：正式发第一版 APK 更新前，按 `docs/runbooks/app-update.md` 用旧包真机验证“检查更新 -> 发现新版本 -> 授权安装未知应用 -> 下载 -> 校验 -> 系统安装页 -> 覆盖安装成功”；发布流程里固定签名证书、`versionCode` 递增规则、文件大小和 SHA-256 记录

## R16 礼品卡批量发放和精细风控仍未完成

- 状态：未关闭
- 说明：后端礼品卡批次、卡、兑换尝试表、后台创建 / 查询 / 作废接口、用户侧 `POST /api/gift-cards/redeem` 和 Android 设置页真实兑换入口已接入；兑换会事务内发会员权益，并记录成功 / 失败尝试、地区、脱敏 IP 和审计。后台已补全局汇总、完整卡码加密保存后页面查看复制、卡码尾号 / 批次 / 账号ID追溯、兑换尝试成功状态 / 失败原因筛选和最近 7 天失败原因聚合。Android 只在后端返回成功后展示“兑换成功”并刷新会员权益。
- 风险：后台当前仍没有批量发放名单、发放对象管理和更细风控统计；完整卡码可在后台页面内查看和复制，但不能写进备注、作废原因、审计 detail、日志、文档或导出文件。本次能力上线前生成的旧卡没有加密字段，后台不能反推出完整卡码。
- 后续动作：按 [gift-card.md](D:/wuhao/docs/runbooks/gift-card.md) 补批量发放名单、发放对象管理、商务发放渠道、外部备注和更细风控统计。Android 仍必须坚持只提交用户输入并展示后端结果，没有后端成功结果时不能弹“兑换成功”。

## R17 协议、隐私和风险提示仍需上线前合规复核

- 状态：未关闭
- 说明：Android 设置页已接入本地内置“用户协议 / 隐私政策 / 风险提示”正文，并把“服务协议”整理为目录页，额外展示“第三方信息共享清单 / 个人信息收集清单 / 应用权限”；2026-05-31 已按手机号登录骨架复核隐私政策、第三方信息共享清单和个人信息收集清单，写入手机号认证、短信验证码、登录账号标识、认证 token 和脱敏手机号说明；2026-06-07 已按官方礼品卡兑换更新 App 内相关口径。不写死未来不做农资交易，也不虚构当前已经接入支付、农资商品交易、OSS 图片链、SLS 业务日志或 Redis。当前已移除独立“数据管理”入口，“删除所有历史对话”收进“账号管理”页并已接 `POST /api/session/clear` 清理聊天历史、A 层滑窗、记忆文档和 30 天归档
- 风险：这些文案不是律师审定稿；公司主体、ICP备案、支付规则、礼品卡规则、农资交易规则、账号注销、隐私同意弹窗、完整个人信息查询 / 删除 / 导出接口、第三方服务 / SDK 清单、图片 OSS 保存周期和真实权限一旦变化，当前文案可能需要同步调整。当前“删除所有历史对话”只覆盖问诊聊天历史和摘要记忆，不等于完整账号注销或全部个人信息删除；当前 App 正文不暴露具体模型品牌，只按“第三方大模型和云服务”做必要告知。当前也尚未接首次启动隐私弹窗 / 勾选同意流程，上架或合规审核前可能需要补
- 补充：协议和隐私巡检入口已沉淀到 [legal-privacy.md](D:/wuhao/docs/runbooks/legal-privacy.md)，后续新增权限、SDK、支付、短信、手机号登录、OSS / SLS 或隐私政策 URL 时必须同步复核
- 后续动作：正式上架 / 公开推广前，按真实公司主体、备案、权限、SDK、第三方服务清单、数据保存周期、支付和账号注销流程复核三份文案；若新增定位、通知、相机、相册或存储权限，必须同步修改隐私政策、Manifest 和项目记忆

## R18 备案、软著和应用商店物料尚未最终稳定

- 状态：未关闭
- 说明：当前 App 名称按“农技千查”推进，公司主体仍是“北京农技千问科技有限公司”；Android 对外包名已定为 `com.nongjiqiancha`，本机已生成 release 签名并导出备案用 MD5 / SHA1 / SHA256 / RSA 公钥信息。当前已按 `农技千查 Android应用软件 V1.0` 生成软著申请信息草稿、操作说明书和源代码前后各 30 页摘录材料，材料位于 `D:/Desktop/农技千查软著材料`，但尚未由公司 / 授权经办人在中国版权保护中心提交并获证。最终图标仍需重做以避开应用商店“图标重复 / 混淆”类打回，软著 / 电子版权证书、备案号、正式截图、测试账号和应用商店物料还未全部落地
- 风险：备案和应用商店审核虽然不是同一个流程，但 App 名称、图标、包名、签名、公钥、协议、隐私政策和展示物料如果反复变化，可能导致备案变更、应用市场反复打回或上架材料互相不一致。旧内测包 `com.nongjiqianwen` 不能通过检查更新覆盖安装成新包 `com.nongjiqiancha`；如果存在旧包测试机，需要卸载旧包重装，不能把这次包名切换当普通自更新
- 后续动作：按 [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md) 固定最终图标、包名、签名证书和软著 / 电子版权材料；网站 ICP 已通过，网站公安备案号已下发并补到官网 footer，根域名官网和 `api.nongjiqiancha.cn` HTTPS 已完成，App 备案已提交阿里云初审，继续跟进 App 备案审核、App 公安备案、软著 / 电子版权和应用商店物料；App 备案通过后再补 App 备案编号和应用商店材料

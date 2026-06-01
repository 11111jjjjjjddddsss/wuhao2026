# 当前未关闭风险

最后更新：2026-06-01

## R1 运维入口仍以文档骨架为主

- 状态：未关闭
- 说明：`docs/runbooks` 已建立，`operations-blueprint.md` 也已把整体 App / 后端 / 管理后台的 Codex 协助运维范围固定下来，但仓库内尚未沉淀完整的 ECS / SAE 部署、回滚、日志、数据库只读脚本、管理后台和实际命令
- 风险：换窗口时能知道要看哪里，但真正执行运维仍可能依赖人工补充；ECS / RDS / OSS 资源虽然已部分购买，但真实后端服务、域名绑定、密钥注入、日志和后台入口尚未落地，仍不能把未部署的服务地址或后台入口写成既成事实
- 补充：买服务器前“统一管理后台”巡检已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)，并新增 [management-backend.md](D:/wuhao/docs/runbooks/management-backend.md)。当前没有网页后台、`/admin` 或 `/internal/admin` 路由；只有帮助与反馈内部接口、今日农情内部生成接口、App 自动日志 `POST /api/app/logs` / `client_app_logs` 骨架和若干环境变量 / runbook 规划
- 后续动作：后面一旦发生真实发版、回滚、查日志、查库、客服回复、礼品卡或会员运营，就把实际可执行入口补进 runbook、脚本或统一管理后台。第一版后台优先补后台账号、角色权限、操作审计、帮助与反馈、用户查询、App 自动日志查询、检查更新、今日农情状态页，不提前做重

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
- 说明：首版部署路线已从“SAE 镜像托管优先”转向“ECS 传统部署优先”。ECS `i-2ze5nrem0jrchln4f0eh` 已购买并运行，位于 `华北2（北京）/ cn-beijing` 可用区 L，规格 `ecs.u1-c1m2.large`（2 vCPU / 4 GiB），Ubuntu 22.04 64 位，公网 IP `39.106.1.151`，私网 IP `192.168.1.237`，生产 VPC `nongjiqiancha-prod-vpc` / `vpc-2zeax2zowza2398b9dzot`，生产交换机 `nongjiqiancha-prod-beijing-l` / `vsw-2zemsq82lj2kp8za90aky`，安全组 `sg-2ze4tilwxw1h5w77lwl1`，固定公网带宽 5 Mbps，到期时间 2027-05-24；2026-05-30 已清理北京区空闲默认 VPC / 默认交换机和旧 SAE 自动交换机，当前只保留生产 VPC / 交换机。已初始化系统用户、部署目录、systemd、Nginx 反向代理、基础限流、fail2ban 和 logrotate，`server-go` 已部署并运行。Go 服务已收口为只监听 `127.0.0.1:3000`，并通过显式 `http.Server` 配置慢请求头、请求读取、空闲连接和最大 header 限制，继续由 Nginx 统一对外；通用 JSON body 默认只读取 64KiB，App 日志接口单独限制 8KiB 且超限返回 413；模型出站请求已限制拨号、TLS 握手、响应头等待和空闲连接，SSE 响应已带 `X-Accel-Buffering: no`，主聊天流有 30 分钟最长持续时间兜底，B/C 摘要非流式模型响应限制 64KiB，今日农情 JSON 响应限制 1MiB；服务启动迁移已加 MySQL 全局命名锁，迁移锁释放失败会作为启动错误暴露。阿里云 DNS 已创建 A 记录 `api.nongjiqiancha.cn -> 39.106.1.151`，ECS 内解析和 HTTP healthz 已生效；本机和公网当前会因未备案被阿里云拦截到 `Non-compliance ICP Filing`。RDS MySQL 实例 `rm-2zes3vmj76p85n8g1` 已创建数据库 `nongjiqiancha`、应用账号 `nongji_app`，白名单为 `192.168.1.237`，服务启动已完成迁移。旧 SAE demo 应用已删除，SAE `ListApplications` 为空。域名 `nongjiqiancha.cn` 已购买，用户口头确认实名认证 / 模板审核已通过，但 ICP / App 备案、HTTPS 证书和正式后端入口绑定仍未完成。OSS 标准-本地冗余存储包（华北2）100GB 已购买并生效，Bucket `nongjiqiancha-prod` 已在北京创建为私有标准本地冗余，并配置 `uploads/` 3 天、`support/` 30 天生命周期；`server-go` 已新增 OSS 上传存储后端，生产 ECS 已配置 OSS 环境变量并通过 `upload_storage=oss` 验证，当前会写私有 OSS。SLS 服务本体已开通，但未购买节省计划 / 资源包，未创建农技千查专用日志 Project / Logstore，也未接入 `server-go` 日志采集；北京区仍有 3 个阿里云系统 / 产品托管 Project，2026-05-30 已复查为云监控 / XTrace / 产品数据类，不直接删除。Redis 开源版实例 `nongjiqiancha-prod-redis` / `r-2zet46zvmoo9wu3bic` 已购买并运行，256MB、Redis 7.0、标准高可用主备、同生产 VPC / 北京可用区 L，白名单已限制到 ECS 私网 IP；`server-go` 已新增可选 Redis 客户端和短信认证短期限流，主聊天流、额度、归档、摘要和订单仍不依赖 Redis；PolarDB 暂作为后续高规格升级选项
- 风险：后端已能运行但还不是完整生产可用：模型 Key 未配置，真实聊天会返回 `MODEL_BACKEND_NOT_CONFIGURED`；`AUTH_STRICT=true` 已保护公网业务入口，手机号账号 / per-user token 骨架已落地，但阿里云融合认证 Android SDK、SchemeCode、短信签名模板、ECS 登录相关环境变量和真机登录联调仍未闭环；Redis 认证限流已部署到 ECS 并通过 `/healthz redis=ok` 验证；HTTPS / 备案未闭环，App 生产 API 地址还不能稳定切过去；服务器环境里的 `BASE_PUBLIC_URL / UPLOAD_BASE_URL` 已是 `https://api.nongjiqiancha.cn`，但 Nginx 目前只监听 HTTP 80，未配置 443 证书，图片上传返回的 https 图片 URL 还不能算公网可用；OSS Bucket、代码后端和生产 ECS OSS 环境变量均已准备好，当前线上上传后端已切到 OSS；后续加第二台 ECS 或回到 SAE 多实例前仍需用真实 App 链路验证 OSS 上传 / 读取 / 模型拉图；多实例前摘要仍需要跨实例保护；22 端口当前仍对公网开放，虽已启用 fail2ban，但后续应在确认云助手 / 运维通道后进一步限制来源
- 补充：后端已支持 `DASHSCOPE_API_KEY_1/2/3` 多 Key 池和限流前置切 Key，但真实并发扩容必须使用不同阿里云主账号的 Key；同一主账号多个 API Key 共享 RPM / TPM 限流。朋友账号 Key 可短期兜底，但长期生产会带来账单、权限、密钥轮换和数据处理责任不在自己名下的运维风险
- 补充：买服务器前高并发巡检结论已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。Go 语言本身不是当前瓶颈；首版单实例可跑早期。OSS 资源包、Bucket 生命周期、代码存储后端和生产 ECS OSS 配置已经就绪；若要多后端实例，仍需先用真实 App 链路验证 `/upload`、`/uploads/` 与模型公网拉图。数据库迁移已补 MySQL 全局锁，后续若进入正式多实例滚动发布，再考虑升级为单独迁移步骤和 `schema_migrations` 表
- 后续动作：下一步优先补 DashScope 模型 Key、HTTPS 证书、ICP备案 / App 备案，以及手机号登录生产配置：从阿里云融合认证控制台拿到 `SchemeCode`，导入 Android 一键登录 SDK，配置 `DYPNS_*` 环境变量和短信签名 / 模板。OSS 上传后端环境变量和最小权限 RAM 凭证已写入 ECS，`/healthz upload_storage=oss` 已验证；HTTPS / 模型 Key 就绪后继续验证真实 `/upload`、`/uploads/` 和模型拉图链路。备份当前有 7 天默认策略，正式数据进入后再确认是否延长保留时间或加密 / 跨地域备份；SLS 可先用 ECS 本地 `journalctl` / Nginx 日志过渡，若接入则只创建农技千查专用 Project / Logstore、低保留天数和关键错误日志采集，不买节省计划。若后续重新启用 SAE，则再回填 [deploy-sae.md](D:/wuhao/docs/runbooks/deploy-sae.md) 的镜像构建、环境变量、健康检查和回滚入口。按 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md) 固化模型 Key 所属账号、充值告警和轮换责任；RDS 规格确认后再按真实连接数配置 `MYSQL_MAX_OPEN_CONNS` 等连接池环境变量。当前本机阿里云 CLI 可读 ECS / RDS / OSS 资源包 / SLS 开通状态并可用 Cloud Assistant 运维 ECS；真实 AccessKey 不进入仓库或文档，后续稳定后应轮换已暴露过的主账号 Key

## R8 C+ 长期资产抽取尚未落地

- 状态：未关闭
- 说明：`server-go` 已新增 `session_round_archive` 保存成功完成轮次，并按 30 天滚动保留；`/api/session/snapshot` 的 `a_rounds_for_ui` 可优先返回 30 天内最近 30 轮归档。但这些原始记录当前只是“可恢复 / 可批处理”的材料，尚未抽取成 C+ 通用用户画像 / 农业长期档案 / 使用偏好
- 风险：如果后续迟迟不做批量抽取，30 天外原始问答会被滚动删除；长期护城河目前先停留在现有 B 层短期记忆 / C 层用户长期记忆文本，尚未形成更稳定的结构化通用用户画像、农业长期档案和使用偏好
- 后续动作：后续评估 C+ schema、更新频率和抽取模型，优先用归档记录做离线 / 低频批处理，不在当前第一刀里实时抽取、不把归档内容每轮喂给模型；图片文件本身的 OSS / 本地 uploads 生命周期还需单独按成本和隐私策略配置

## R9 图片发送后台兜底仍受系统调度和租约窗口影响

- 状态：未关闭
- 说明：Android 当前已为带图片发送接入 WorkManager 延迟兜底，覆盖“图片已进本地消息但 App 被杀、前台没来得及可靠送到后端”的场景；它不接管正常前台 SSE 直播，也不直接写 UI 聊天窗口。前台活跃标记、唯一 work 名、远端启动保护窗，以及后端 `chat_stream_inflight` 进行中锁 + lease token 共同用于避免同一 `client_msg_id` 重复启动上游模型流。后端 replay 真源已改为 `session_round_ledger` / 轮次归档成功，服务端只在归档成功后才向客户端发送 SSE `[DONE]`，避免“客户端已收完成态，但回答没归档”时返回空 replay
- 补充：额度扣减在轮次归档成功后执行；若 `ConsumeOnDone` 临时失败，服务端会按同一 `client_msg_id` 短重试，重复扣由 `quota_ledger` 唯一键防住。replay 现在只恢复已归档答案，不再按当前档位 / 当前日期补扣旧轮次，避免跨日或会员档位变化后误扣。若归档后扣减失败且短重试仍失败，当前更偏向“漏记你一次成本”，不把风险转成“乱扣用户次数”
- 补充：主模型自动开流重试已从 2 次收紧为 1 次，Android 前台流的自动 stream retry 已关闭。WorkManager 后台兜底只针对同一条 pending 图片消息，在图片上传失败、网络中断、流异常结束、`409 STREAM_IN_PROGRESS`、限流或临时上游错误时用同一 `client_msg_id` 退避重试；普通可恢复失败最多重试 5 次后移除 pending，避免弱网一抖就丢消息，也避免无限反复开流。`chat_stream_inflight` 获取结果改为校验 lease token，数据库新增同一 `user_id` 活跃流唯一约束，降低不同 `client_msg_id` 并发绕过额度预检查并多开 Qwen3.5-Plus 的风险；旧 `/api/session/round_complete`、`/api/session/b`、`/api/session/c` 已返回 410，不再参与主链
- 补充：买服务器前“主聊天与图片发送”巡检已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)，当前没有发现旧直连模型、旧完成接口、旧 active-zone、旧图片手势或旧上传通道在运行时并存；后续风险主要集中在真实公网 https 图片链、首版单实例 / OSS 取舍、弱网多图、后台恢复和多实例进程内保护迁移
- 补充：买服务器前“B/C 记忆与模型调用”巡检已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)，当前 B/C 提取频率、`pending_retry_b/c`、60 秒超时和 `round_total` 写回校验都与现行口径一致；多实例前仍要补摘要数据库 claim / lease，避免重复调用 Qwen3.5-Flash 和同一 `round_total` 下的非确定性覆盖
- 风险：WorkManager 不是实时任务，系统可能延后执行；后端进行中锁当前用 30 分钟租约防死锁，若服务进程极端卡死且租约过期，后续请求才会重新接管。这个设计优先保护成本，不承诺像前台直播一样立刻可见；如果主模型上游已经被调用、但轮次还没归档就发生进程崩溃 / 上游断流 / 归档失败，同一 `client_msg_id` 后续仍可能重新开一次 Qwen3.5-Plus；当前已把自动开流重试限制在后台同一 pending 消息的有限次数内，但正式上线前若要进一步压成本风险，需要增加持久化 attempt / status 表或更完整的后端任务恢复链。如果模型已经吐完但归档写库本身失败，客户端不会收到完成态，该轮仍可能需要用户重试或人工排障。若服务进程在归档后、扣减前崩溃且短重试也失败，仍可能出现单轮成本漏记；后续若真实上线，应补后台对账任务或按业务日志做周期性巡检。B 层短期记忆 / C 层用户长期记忆当前已有 60 秒单次提取超时、本进程运行中保护和 `round_total` 写回校验，但多后端实例下仍可能重复调用 Qwen3.5-Flash，后续需要数据库 claim / lease 或确认首版单实例部署
- 后续动作：短期把这版作为保守兜底真机观察，重点看切后台 / 杀进程后的图片消息是否能恢复、UI 是否不会消失；如果后续要做到 App 被杀后也像前台一样实时可见，需要后端提供更完整的进行中状态查询 / 结果缓存，再决定是否升级成长任务或服务通知方案

## R10 手机号登录生产接入仍需收口

- 状态：未关闭
- 说明：当前后端业务接口和 `/upload` 都要求身份头 / token。服务端已新增手机号账号表、登录 session 表、旧本机 `user_id` 迁移表和 v2 bearer token；Android 已新增登录门、账号 token 保存、验证码登录 UI 和调后端短信 / 融合认证接口的客户端代码。登录成功后，旧本机 `user_id` 会作为迁移桥，聊天归档、额度账本、会员、帮助与反馈等用户数据会尽量迁到手机号账号下
- 补充：当前仍兼容 Android 早期阶段的裸 `X-User-Id` 本机身份兜底，方便迁移期联调；Android 已移除 `SESSION_API_TOKEN` 静态注入和运行时登录绕过，正式登录只使用后端按真实手机号账号签发的 per-user session token
- 补充：Redis 认证限流已覆盖融合认证 token 获取、短信发送和短信登录校验；fusion token 按 IP hash 限流，短信相关按手机号 hash + IP hash 限流，避免 SDK 接入后刷 token 或验证码直接消耗阿里云认证资源
- 风险：这还不是完整可上线登录：阿里云 `CreateSchemeConfig` 已能返回 `OK`，但 CLI 未返回 `SchemeCode`，需要从控制台核对并配置；Android 一键登录 SDK / AAR 尚未导入，当前一键登录按钮不会消耗融合认证试用次数，只提示先用验证码；短信登录依赖 `DYPNS_SMS_SIGN_NAME` 和 `DYPNS_SMS_TEMPLATE_CODE`，ECS 环境变量尚未配置；Redis 认证限流已部署到 ECS 并健康；旧 `X-User-Id` 兜底在公开生产仍需严格关闭
- 后续动作：正式公开上线前必须完成阿里云融合认证 Android SDK 接入、`DYPNS_FUSION_SCHEME_CODE`、包名 / 签名配置、短信签名 / 模板、ECS DYPNS 环境变量和真机登录回归；Redis 验证码 / 限流已接入；生产环境保持 `APP_SECRET` 与 `AUTH_STRICT=true`，并逐步移除或隔离裸 `X-User-Id` 兜底

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
- 补充：买服务器前“支付真实接入”巡检已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)，并新增 [payments.md](D:/wuhao/docs/runbooks/payments.md)。当前没有真实支付渠道、SDK、自动续费、退款或对账；`orders` 表也只是开发期成功结果记录，不是正式支付订单表
- 后续动作：接入真实支付时，把会员变更收敛到服务端验签后的支付回调 / 对账流程，并移除或彻底隔离开发期直接变更接口；生产环境保持 `ALLOW_DEV_ORDER_ENDPOINTS` 未设置 / false，并配置 `APP_ENV=production` 或等价环境变量作为额外保险。真实收费前还要接手机号登录 / token，把本机 `user_id` 与账号绑定，并开启 `AUTH_STRICT=true`。正式订单表需覆盖渠道订单号、金额、商品、状态、回调、退款和幂等发放结果

## R13 今日农情生成质量和调度仍需上线观察

- 状态：未关闭
- 说明：今日农情首版已接入独立后端链路和 Android UI-only 卡片；它不影响聊天主链、不扣用户问诊次数、不进入 A/B/C 或归档。当前生成依赖 `qwen3.5-plus + forced_search + search_strategy=max + enable_source`，生成前会把过去 7 天已 ready 的今日农情喂给模型要求去重，服务端会校验搜索来源、可信域名、近 7 天日期、广告 / 导购 / 泄露词，并硬过滤过去 7 天和当天候选里的重复链接 / 重复标题，过滤后不足 3 条则不发布新卡片
- 风险：`max` 搜索比主对话默认 Turbo 更慢且成本更高；如果当天搜索结果来源质量不足或 DashScope 未返回 `search_info.search_results`，后端会不发布，前端静默不展示。当前仅有 `scope=CN` 全国卡片，尚未做地区 / 作物个性化；云端 05:30 定时触发、失败重试、告警和人工补生成还需要真实后端 / 调度环境落地后验证
- 补充：买服务器前今日农情巡检已记录到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。当前没有发现用户打开 App 临时生成、写 A/B/C、写归档、扣次或伪装成 `ChatMessage` 的旧链路；残余风险主要是质量运营，比如同一事件换标题只能靠提示词和人工抽查约束、可信域名白名单偏严可能导致当天不足 3 条
- 后续动作：上线前按 `docs/runbooks/today-agri-card.md` 配置 `DAILY_AGRI_JOB_SECRET` 和定时触发；观察 SLS 日志关键词 `daily agri card generated` / `generate today agri card failed`，并抽查 `daily_agri_cards` 当天 `status/content_json/error`。若连续失败，再评估放宽可信域名、切 `turbo + assigned_site_list` 或做人工审核入口，不把失败转成用户打开 App 时临时多次调模型

## R14 帮助与反馈首版还没有完整管理后台

- 状态：未关闭
- 说明：帮助与反馈当前已具备后端消息表、用户侧历史 / 发送 / 已读接口，以及 `SUPPORT_ADMIN_SECRET` 保护的内部读取会话和发送后台回复接口；Android 也能展示历史、发送文字 / 图片反馈，并在设置页“帮助与反馈”行用红点提示未读后台 / 系统消息
- 风险：当前还不是完整客服系统，没有网页管理后台、后台账号 / 权限、工单分配、站外推送、消息搜索或未回复队列。后台想回复用户时需要先通过内部接口或未来管理后台调用，同步依赖真实服务器、数据库、上传公开域名和密钥配置。公开生产前仍必须接账号 token 并启用 `AUTH_STRICT=true`，否则裸 `X-User-Id` 能读写某个用户的帮助与反馈；多后端实例前也必须先上 OSS 或保持单实例，否则帮助与反馈图片同样受本机 `/upload` 存储影响
- 后续动作：正式服务器和管理后台规划落地后，再把帮助与反馈接入统一运营面板；优先补“按用户查看会话、发送回复、未读 / 未处理列表、基础权限和审计日志”，不要在 Android 端塞后台逻辑。账号注销 / 数据删除规则还要明确帮助与反馈消息和图片是否删除、保留多久、由谁操作

## R15 自有 APK 更新链路需要上线实机验证

- 状态：未关闭
- 说明：Android “检查更新”已按自有服务器 APK 分发接入，后端通过 `APP_ANDROID_*` 环境变量返回最新版本、https APK 地址和可选 SHA-256，客户端下载后会先校验最终 https、文件大小、SHA-256、包名和 `versionCode`，再调起系统安装页
- 风险：普通 Android App 不能静默安装 APK；Android 8+ 需要用户给本 App “安装未知应用”授权，不同国产 ROM 的授权页和安装页可能存在差异。APK 下载地址、签名证书、`versionCode` 递增、https 证书、包名一致性、哈希配置和安装权限都需要真实环境验证。若后续发布 APK 签名证书变化，系统会拒绝覆盖安装；如果用户已经安装坏包，只能再发更高 `versionCode` 修复包，不能低版本回滚覆盖
- 后续动作：正式发第一版 APK 更新前，按 `docs/runbooks/app-update.md` 用旧包真机验证“检查更新 -> 发现新版本 -> 授权安装未知应用 -> 下载 -> 校验 -> 系统安装页 -> 覆盖安装成功”；发布流程里固定签名证书、`versionCode` 递增规则、文件大小和 SHA-256 记录

## R16 礼品卡仍是前端占位

- 状态：未关闭
- 说明：Android 设置页“礼品卡”当前只是占位页：输入为空时按钮禁用，输入后点击只提示“礼品卡兑换暂不可用”；debug-only 预览面板里的“兑换成功 / 确定”只是未来成功态样式预览。当前没有后端礼品卡表、用户侧兑换接口、生成 / 发放 / 作废 / 查询后台，也不会修改会员、额度、加油包、订单或 `quota_ledger`
- 风险：如果后续在后端规则、幂等、防撞库、后台审计和权益发放事务没做完前就把前端成功态接到真实按钮，可能造成假成功、重复发权益、礼品卡撞库或人工无法追溯
- 后续动作：真实接入时以后端为唯一真相，按 [gift-card.md](D:/wuhao/docs/runbooks/gift-card.md) 先补礼品卡主表、兑换记录表、用户侧兑换接口、后台生成 / 发放 / 作废 / 查询能力、操作审计、限流和日志；Android 只提交用户输入并展示后端结果，没有后端成功结果时不能弹“兑换成功”

## R17 协议、隐私和风险提示仍需上线前合规复核

- 状态：未关闭
- 说明：Android 设置页已接入本地内置“用户协议 / 隐私政策 / 风险提示”正文，并把“服务协议”整理为目录页，额外展示“第三方信息共享清单 / 个人信息收集清单 / 应用权限”；2026-05-31 已按手机号登录骨架复核隐私政策、第三方信息共享清单和个人信息收集清单，写入手机号认证、短信验证码、登录账号标识、认证 token 和脱敏手机号说明。不写死未来不做农资交易，也不虚构当前已经接入支付、礼品卡兑换、农资商品交易、OSS 图片链、SLS 业务日志或 Redis。当前已移除独立“数据管理”入口，“删除所有历史对话”收进“账号管理”页并已接 `POST /api/session/clear` 清理聊天历史、A 层滑窗、B 层短期记忆、C 层用户长期记忆和 30 天归档
- 风险：这些文案不是律师审定稿；公司主体、ICP备案、支付规则、礼品卡规则、农资交易规则、账号注销、隐私同意弹窗、完整个人信息查询 / 删除 / 导出接口、第三方服务 / SDK 清单、图片 OSS 保存周期和真实权限一旦变化，当前文案可能需要同步调整。当前“删除所有历史对话”只覆盖问诊聊天历史和摘要记忆，不等于完整账号注销或全部个人信息删除；当前 App 正文不暴露具体模型品牌，只按“第三方大模型和云服务”做必要告知。当前也尚未接首次启动隐私弹窗 / 勾选同意流程，上架或合规审核前可能需要补
- 补充：协议和隐私巡检入口已沉淀到 [legal-privacy.md](D:/wuhao/docs/runbooks/legal-privacy.md)，后续新增权限、SDK、支付、短信、手机号登录、OSS / SLS 或隐私政策 URL 时必须同步复核
- 后续动作：正式上架 / 公开推广前，按真实公司主体、备案、权限、SDK、第三方服务清单、数据保存周期、支付和账号注销流程复核三份文案；若新增定位、通知、相机、相册或存储权限，必须同步修改隐私政策、Manifest 和项目记忆

## R18 备案、软著和应用商店物料尚未最终稳定

- 状态：未关闭
- 说明：当前 App 名称按“农技千查”推进，公司主体仍是“北京农技千问科技有限公司”；Android 对外包名已定为 `com.nongjiqiancha`，本机已生成 release 签名并导出备案用 MD5 / SHA1 / SHA256 / RSA 公钥信息。当前已按 `农技千查 Android应用软件 V1.0` 生成软著申请信息草稿、操作说明书和源代码前后各 30 页摘录材料，材料位于 `D:/Desktop/农技千查软著材料`，但尚未由公司 / 授权经办人在中国版权保护中心提交并获证。最终图标仍需重做以避开应用商店“图标重复 / 混淆”类打回，软著 / 电子版权证书、备案号、正式截图、测试账号和应用商店物料还未全部落地
- 风险：备案和应用商店审核虽然不是同一个流程，但 App 名称、图标、包名、签名、公钥、协议、隐私政策和展示物料如果反复变化，可能导致备案变更、应用市场反复打回或上架材料互相不一致。旧内测包 `com.nongjiqianwen` 不能通过检查更新覆盖安装成新包 `com.nongjiqiancha`；如果存在旧包测试机，需要卸载旧包重装，不能把这次包名切换当普通自更新
- 后续动作：按 [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md) 固定最终图标、包名、签名证书和软著 / 电子版权材料；买域名和中国内地云资源后立即启动 ICP / App 备案；备案等待期间并行做手机号登录、后端部署 / RDS / OSS / SLS 联调和应用商店物料准备；备案通过后补备案号和公安联网备案

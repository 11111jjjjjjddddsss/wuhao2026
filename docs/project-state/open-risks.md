# 当前未关闭风险

最后更新：2026-05-05

## R1 运维入口仍以文档骨架为主

- 状态：未关闭
- 说明：`docs/runbooks` 已建立，但仓库内尚未沉淀完整的 SAE 部署、回滚、日志、数据库只读脚本和实际命令
- 风险：换窗口时能知道要看哪里，但真正执行运维仍可能依赖人工补充
- 后续动作：后面一旦发生真实发版、回滚、查日志、查库，就把实际可执行入口补进 runbook 和脚本

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

## R7 正式云资源尚未采购

- 状态：未关闭
- 说明：当前仓库已经有 `server-go` 主线和 SAE / 回滚 / 日志 / 数据库只读 runbook 骨架，但正式服务器、数据库、域名、HTTPS 和日志项目都还未真正落地。首版采购倾向已从 PolarDB 调整为 RDS MySQL，PolarDB 暂作为后续高规格升级选项
- 风险：后续一旦开始后端联调、真实发版、环境变量注入或图片存储接入，容易因为真实环境参数缺失而临时拍脑袋，导致 runbook 和实际入口再次脱节
- 后续动作：采购前先按 `docs/runbooks/infra-readiness.md` 把 Region、环境命名、RDS MySQL 规格 / 备份 / 白名单、SAE、域名/HTTPS、OSS/SLS/Redis 是否首版接入这些问题拍板；第一套真实环境落地后，同次回填 deploy / rollback / logs / db-readonly runbook

## R8 C+ 长期资产抽取尚未落地

- 状态：未关闭
- 说明：`server-go` 已新增 `session_round_archive` 保存成功完成轮次，并按 30 天滚动保留；`/api/session/snapshot` 的 `a_rounds_for_ui` 可优先返回 30 天内最近 30 轮归档。但这些原始记录当前只是“可恢复 / 可批处理”的材料，尚未抽取成 C+ 用户农业画像 / 用户农业档案
- 风险：如果后续迟迟不做批量抽取，30 天外原始问答会被滚动删除；长期护城河仍主要停留在现有 B/C 摘要，无法形成更稳定的用户农业画像和农业档案
- 后续动作：后续评估 C+ schema、更新频率和抽取模型，优先用归档记录做离线 / 低频批处理，不在当前第一刀里实时抽取、不改 prompt、不把归档内容每轮喂给模型；图片文件本身的 OSS / 本地 uploads 生命周期还需单独按成本和隐私策略配置

## R9 图片发送后台兜底仍受系统调度和租约窗口影响

- 状态：未关闭
- 说明：Android 当前已为带图片发送接入 WorkManager 延迟兜底，覆盖“图片已进本地消息但 App 被杀、前台没来得及可靠送到后端”的场景；它不接管正常前台 SSE 直播，也不直接写 UI 聊天窗口。前台活跃标记、唯一 work 名、远端启动保护窗，以及后端 `chat_stream_inflight` 进行中锁 + lease token 共同用于避免同一 `client_msg_id` 重复启动上游模型流。后端 replay 真源已改为 `session_round_ledger` / 轮次归档成功，服务端只在归档成功后才向客户端发送 SSE `[DONE]`，避免“客户端已收完成态，但回答没归档”时返回空 replay
- 补充：额度扣减在轮次归档成功后执行；若 `ConsumeOnDone` 临时失败，服务端会按同一 `client_msg_id` 短重试，重复扣由 `quota_ledger` 唯一键防住。replay 现在只恢复已归档答案，不再按当前档位 / 当前日期补扣旧轮次，避免跨日或会员档位变化后误扣。若归档后扣减失败且短重试仍失败，当前更偏向“漏记你一次成本”，不把风险转成“乱扣用户次数”
- 补充：主模型自动开流重试已从 2 次收紧为 1 次，Android 前台流的自动 stream retry 已关闭，WorkManager 也只对 `409 STREAM_IN_PROGRESS` / 本地停止 / 限流做保守重试，不再对一般模型开流失败反复重试。`chat_stream_inflight` 获取结果改为校验 lease token，数据库新增同一 `user_id` 活跃流唯一约束，降低不同 `client_msg_id` 并发绕过额度预检查并多开 Qwen3.5-Plus 的风险；旧 `/api/session/round_complete`、`/api/session/b`、`/api/session/c` 已返回 410，不再参与主链
- 风险：WorkManager 不是实时任务，系统可能延后执行；后端进行中锁当前用 30 分钟租约防死锁，若服务进程极端卡死且租约过期，后续请求才会重新接管。这个设计优先保护成本，不承诺像前台直播一样立刻可见；如果模型已经吐完但归档写库本身失败，客户端不会收到完成态，该轮仍可能需要用户重试或人工排障。若服务进程在归档后、扣减前崩溃且短重试也失败，仍可能出现单轮成本漏记；后续若真实上线，应补后台对账任务或按业务日志做周期性巡检。B/C 摘要当前只有本进程运行中保护和 `round_total` 写回校验，多 SAE 实例下仍可能重复调用 Qwen3.5-Flash，后续需要数据库 claim / lease 或确认首版单实例部署
- 后续动作：短期把这版作为保守兜底真机观察，重点看切后台 / 杀进程后的图片消息是否能恢复、UI 是否不会消失；如果后续要做到 App 被杀后也像前台一样实时可见，需要后端提供更完整的进行中状态查询 / 结果缓存，再决定是否升级成长任务或服务通知方案

## R10 生产鉴权仍需上线前收口

- 状态：未关闭
- 说明：当前后端业务接口和 `/upload` 都要求身份头 / token，但仍兼容 Android 早期阶段的裸 `X-User-Id` 本机身份兜底；这方便单机开发和无登录阶段联调，但不适合作为公开生产鉴权
- 风险：如果直接公网开放并把裸 `X-User-Id` 当真实登录身份，理论上存在冒充 user_id 读取 / 污染会话、会员和额度状态的风险
- 后续动作：正式公开上线前必须接入手机号 / token / HMAC 等服务端可验证身份，并关闭裸 `X-User-Id` 生产兜底；这属于账号体系任务，不和本次图片发送兜底混在一刀里改

## R11 历史模型 Key 轮换确认

- 状态：未关闭
- 说明：Android 旧直连模型链和 `BuildConfig.API_KEY` 注入已从代码中清理，主链只允许经由后端 `/api/chat/stream` 调模型
- 风险：如果历史调试 APK / 旧包曾经打入真实模型 Key，仍应按密钥可能泄露处理；这不是当前代码风险，但属于上线前安全检查项
- 后续动作：确认历史包是否曾打入真实 Key；若有，轮换对应模型服务密钥。后续代码变更禁止重新引入 Android 客户端模型 Key 注入或直连模型客户端

## R12 会员订单接口仍需接真实支付回调

- 状态：未关闭
- 说明：Android 会员中心当前只展示支付占位提示，不会调用后端下单 / 续费 / 升级 / 加油包接口；`server-go` 里现有 `/api/tier/renew_plus`、`/api/tier/renew_pro`、`/api/tier/upgrade_plus_to_pro`、`/api/topup/buy` 仍是开发期直接变更接口，但默认已返回 `PAYMENT_NOT_CONFIGURED`，只有显式设置 `ALLOW_DEV_ORDER_ENDPOINTS=true` 才允许本地 / 内测调试使用
- 风险：这些接口仍不是正式支付真源；如果内测 / 生产环境误开 `ALLOW_DEV_ORDER_ENDPOINTS=true`，非 App 客户端理论上仍可绕过真实支付直接请求会员变更。这不影响当前 Android UI 展示，也不影响每日额度 / 升级补偿 / 加油包扣次顺序本身，但属于上线前必须继续收口的业务安全风险
- 后续动作：接入真实支付时，把会员变更收敛到服务端验签后的支付回调 / 对账流程，并移除或彻底隔离开发期直接变更接口；生产环境保持 `ALLOW_DEV_ORDER_ENDPOINTS` 未设置 / false

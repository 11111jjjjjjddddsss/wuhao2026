# 近期重要变更

说明：本文件默认只保留最近 20 条重要变更；当前因 4 月聊天 UI 主链多次大切换，暂保留较长历史方便排障，更早内容仍以 git 历史和 ADR 为准。
说明补充：本文件允许保留旧方案的历史记录；旧条目里若出现“反向列表 / requestScrollToItem(0) / asReversed()”或旧会诊对象选择等表述，默认都只是历史过程，不代表当前运行时真相或当前协作口径。当前真相始终以根 `AGENTS.md` 和 `docs/project-state/current-status.md` 为准。

## 2026-06-20

- 继续按“全盘挑刺、收口收尾、用户体验优先”落地低风险修复：帮助与反馈用户发送新增客户端 `client_msg_id` 幂等，Android 同一条反馈弱网失败后重试会沿用同一发送 ID，带图重试会复用已上传的 support 图片 URL，后端在同用户命名锁事务内按 `user_id + sender_type + client_msg_id` 唯一键返回原用户消息和原自动回复，不按正文相似度或手机号 / 订单号 / 礼品卡码做内容拦截；无网时帮助与反馈发送前直接提示“当前网络不可用”，不再卡在上传中。主聊天图片 URL 和 support 图片 URL 统一拒绝带 userinfo 的 HTTPS URL；今日农情主界面展示记录保存频控挪到 JSON、日期和锚点基础校验之后，坏请求不再白白消耗保存频控。后台用户详情抽屉补权益、订单、礼品卡兑换和最近反馈明细摘要，按手机号查到用户后不用再复制账号 ID 去多个页面追溯；正式上架检查把依赖正常但等待真机确认的手机号登录归为“人工确认”，`launch_only` 明细标签显示“上线准备”，不再误写成程序处理。资源容量巡检在云监控规则处于 `ALARM` 时普通模式输出 attention、`-Strict` 直接失败，避免资源已经报警但脚本假绿；资源严格门禁文档改成 `-SkipAuthUsage -Strict -RequireSlsExternalNotification -RequireSlsDashboard`，短信套餐包另跑短信脚本或人工确认。本轮不修改主对话锚点、今日农情提示词、记忆文档提示词、官网首页文案，不新增模型输出过滤、关键词拦截、相似度硬拦截、字数硬卡或 `max_tokens`，不发布正式 Android 包。

- 后置复核继续收口上线门禁和后台客服细节：ECS readiness 现在要求 inactive slot active 时必须有同端口 drain-stop 单元，并且当前 active upstream slot 不能挂 active drain-stop，避免旧排空任务残留造成假绿或误停当前线上服务；下载域名 TLS 检查加握手超时，避免巡检卡死。后台帮助反馈详情在 support 角色可搜 / 可看正文时，会把搜索命中的较早非系统消息合并进最近 200 条详情并提示数量，长会话排障不再“搜到会话但看不到命中正文”；无正文权限角色仍只看脱敏摘要。本轮不改三份保护提示词、不新增模型输出过滤、不发布正式 Android 包。

- 继续按产品负责人视角收口现有功能：后台监控把 `launch_only` 上线准备项和日常程序告警拆开，未发正式更新、暂无礼品卡库存和支付未接入不再让日常巡检变黄；今日农情 `disabled` 不再进日常待办。后台帮助反馈详情改为最近 200 条并提示截断，客服图片在后台 API 归一为同源 `/uploads/support/*.jpg`，后台 Nginx 代理该路径，support 图片响应改为 `private, no-store`；礼品卡备注占位提示不写手机号 / 完整卡码 / 密钥，状态和兑换结果中文化。Android 表格“复制表格”按钮退出文本选择范围，退出设备会区分“服务端已吊销”和“本机已退出但远端未确认”，崩溃补报只有 2xx 后才删 pending，弱网失败继续补报。严格生产鉴权下普通业务接口拒绝无 `session_id` 的旧 bearer token；下载域名巡检新增公网 TLS 到期检查，ECS readiness 会发现旧 slot 长期 active 且无排空单元。验证通过 `go test ./...`、后台 `npm run build`、Android `:app:compileDebugKotlin`、`check-admin-surface.mjs`、`check-android-download-domain.ps1` 和后台日常行动项巡检；本轮不改三份保护提示词、不新增模型输出过滤、不发布正式 Android 包。

- 继续按“全盘挑刺、收口收尾、测试包除预览面板外尽量等同正式包”落地低风险上线修复：后台监控不再把未发正式更新 / 暂无礼品卡库存当日常黄灯，空态、手机号展示和帮助反馈详情按钮顺序改成业务负责人更容易理解的口径；帮助反馈用户发消息改为先校验 JSON、长度和图片 URL 再消耗短期限流，坏格式 / 超长输入不烧用户次数，客服和用户仍可发送数字、手机号、订单号、礼品卡码等排障内容，不做内容拦截。正式 APK URL 边界统一收紧为 `download.nongjiqiancha.cn/android/releases/...apk` 稳定裸地址，后端、Android、后台、官网、release-match 和公网黑盒都拒绝外部域名、非 release 路径、测试包标记、userinfo、query 和 fragment；测试包 `-NoBuild` 会确认 Android 构建输入干净且 APK 不早于最新输入 commit，避免旧 debug 包误发。公网黑盒新增下载域名 / OSS CNAME 探针，后台 / 官网静态部署会清理本次远端临时文件并裁剪旧 release，官网旧 ECS `/test-apks/` 清不掉会失败暴露。Android 远端图片预览只信任生产 API 域名下单层 `/uploads/*.jpg` / `/uploads/support/*.jpg` HTTPS 图片；聊天渲染诊断不再 streaming 每增量解析全量消息，今日农情 96px 生产可见阈值补单测；App 内和官网隐私政策保存条款收成正式概括口径，具体留存仍在 runbook。已验证 `go test ./...`、后台 / 官网 build、Android 全量 debug 单测、Android parity、公网黑盒和脚本 AST；本轮不改主对话锚点、今日农情提示词、记忆文档提示词、官网首页文案，不新增模型输出过滤、关键词拦截、字数硬卡或 `max_tokens`，不发布正式 Android 包。

- 按“收尾收口、功能稳定、不胡乱修改”继续只处理上线前稳定性缺口：App 内和官网协议 / 隐私 / 清单页从过细工程说明收成正式概括文案并统一版本日期到 2026-06-20，保留服务边界、权限、第三方类别、保存和用户权利，官网首页定稿文案未改；Android debug-only 预览面板补齐检查更新失败原因、今日农情真实时间线组合、用户带图后台发送中和“已发送 · 正在同步回复”等异常态，今日农情 viewport 可见阈值从 24dp 调到 96dp，且下一轮新发送必须本次运行中真实看见过当天农情才带 `today_agri_context_day`，降低只露边或重启后离屏就被 AI 参考的风险；远端 snapshot 标记归档暂不可用时，Android 保留完整本地窗口兜底，不让部分远端快照擦掉本地时间线或今日农情锚点；同日晚到的主界面今日农情 pending 会在非 streaming、非用户浏览时落地，不再因用户已经发过消息而永久挂起；带图 WorkManager 后台上传阶段遇到登录失效会直接写 `auth` 终态失败。后端 `quota_consume_outbox` 继续不挡用户聊天，长期无法安全追扣时默认 40 次尝试或 7 天后自动转 `uncollectable`，后台监控新增“扣次自动对账”卡说明自动追账 / 自动终结；后端新增低频数据维护 worker，限量清理过期 `session_round_archive` 和 `client_app_logs`。测试包发布链纠正为低成本自动清理：只发 OSS 私有 `test-apks/debug/...` 短签名链接，正常由 OSS 3 天生命周期删除，`-UseEcsDownloadFallback` 已退役并硬拒绝，脚本里的旧 ECS 下载分支已删除，官网部署会移除旧 ECS `/test-apks/` 目录，测试包上传前固定校验 Bucket。正式 APK 物料校验新增旧包防误发检查，避免现存旧 `app-release.apk` 被误判可发布；后台角色文档同步 owner / support 处理帮助反馈、finance_ops 负责财务 / 权益页面，帮助反馈有搜索词时自动查全部历史。本轮不修改主对话锚点、今日农情提示词、记忆文档提示词、官网首页定稿文案，不新增模型输出过滤、关键词拦截、相似度硬拦截、字数硬卡或 `max_tokens`，不发布正式 Android 包。

- 继续按“拉满代理、全盘挑刺、联网校准、收口收尾”落地不碰保护提示词的实锤收口：Android 带图后台发送成功后，remote-completion marker 现在可读回已上传远端图片 URL，前台恢复轮询会把 URL 回填到用户图文气泡；远端 snapshot 还没恢复完整 AI 回复时，尾部显示“已发送 · 正在同步回复”，避免成功发送后变成无状态或误报失败。Go 后端 internal job 入口改成内部来源校验通过后先消耗内部 secret 限流，再校验密钥；内部来源只信任本机 loopback Nginx 注入的转发头，不信任普通私网直连伪造的 `X-Real-IP / X-Forwarded-For`。公网黑盒脚本从单个 `/internal` 探针扩到今日农情 generate/status/probe/manual、记忆探针、App 日志、审计日志和内部客服接口，本轮实测全部 403。今日农情自动生成遇到人工锁定但内容无效时返回 `manual_locked_invalid`，不再静默 200 跳过；客服关闭未回复会话也必须写备注；后台注销申请文案改为“线下处理完成 / 标记线下完成”，隐私政策拆清“完整问答归档约 30 天”和“连续问诊短期窗口 / 长期记忆”的保留口径。`AGENTS.md` SLS 告警口径改为近期 8 条且以脚本 / current-status 为准，续费证书自动化 runbook 补 OSS CNAME 证书同步条件动作。验证通过 `go test ./...`、Android `ChatTimelineItemsTest`、后台 `npm run build`、Android parity 和公网黑盒；本轮不修改主对话锚点、今日农情提示词、记忆文档提示词、官网首页文案，不新增模型输出过滤、关键词拦截、字数硬卡或 `max_tokens`，不发布正式 Android 包。

## 2026-06-19

- 继续收口 P1 用户体验和后端运维口：Android 带图 WorkManager 后台完整发送成功后会写入短期 remote-completion 标记并移除 pending，前台在远端 snapshot 暂时没恢复完整 AI 回复前不会把这条已完成图文误标为本地失败；恢复到完整 assistant 后再正常清理标记。后端今日农情生成 / 状态 / probe / manual、App 自动日志内部查询和内部审计查询统一改成 loopback / 私网来源 + 共享密钥双门槛；`get-today-agri-manual-status.ps1` 和 `publish-today-agri-manual.ps1` 默认通过 Cloud Assistant 进入 ECS，在本机 active slot 调用 `/internal/*`，DryRun 输出明确 `cloud_assistant_local`，避免后续窗口误以为还在公网直连 internal。公网黑盒脚本同步把 `/internal` 探针改为期望 403 来源拒绝；状态脚本已只读确认 20260620 为 `manual_locked=true / source_type=manual / item_count=3`。该轮不修改三份保护提示词、不新增模型输出过滤、不发布正式 Android 包。

- 按用户“把清理脚本删除，需要清理时再叫 Codex”的最新口径，删除 `scripts/clean-local-android-apks.ps1` 和 `scripts/clean-oss-test-apks.ps1`，并把当时 `scripts/publish-android-test-apk.ps1` 的发布后自动清 OSS 旧测试包、清 ECS `/test-apks/` 旧镜像、写 ECS cron 清理文件逻辑全部撤掉；当时测试包发布只负责生成 / 上传 debug/internal 包、输出并探测 `download.nongjiqiancha.cn` 限时签名链接，清理状态输出为 `test_apk_cleanup=manual_only`。该历史口径已被 2026-06-20 的 `test_apk_cleanup=oss_lifecycle_3d` 取代：云端测试包正常由 OSS `test-apks/` 3 天生命周期自动清理，仓库仍不保留本机 / OSS 清理脚本，也不写 ECS 清理 cron。已通过 Cloud Assistant 删除 ECS 旧 `/etc/cron.d/nongjiqiancha-test-apks-clean`，返回 `ecs_test_apk_cleanup_cron=removed`。`android-test-package.md`、`android-download-distribution.md`、`official-website.md`、`app-update.md` 和项目记忆同步改成“清理由用户明确提出后单次人工处理”，项目记忆脚本也移除已删除清理脚本清单。该轮不生成正式 release APK、不配置检查更新、不改官网正式下载、不改三份保护提示词、不新增模型输出过滤。

- 继续按“全盘挑刺、交互类重点、收口收尾”修复代理抓到的实际用户体验风险：Android 前台主聊天遇到后端 `409 STALE_SESSION_GENERATION` 时，不再把刚发的用户消息和 assistant 占位直接删掉，而是保留用户消息并落成可点击重试的 assistant 失败态，浮层提示“会话已更新，本次回复未完成”。带图 WorkManager 后台兜底遇到登录态 / session generation 变化时，不再静默移除 pending，而是写 `stale_session` 终态失败并保留已上传图片 URL，前台恢复后有失败 / 重发入口；后台完整 SSE 也会把 `STALE_SESSION_GENERATION` 保留为 `stale_session`，不再落成泛化 `bad_request`。今日农情远端模式下，如果本地残留同日 shown-day 但没有当天保存副本，不再跳过拉取，降低脏缓存导致“当天主界面农情不出现”的概率；本地非远端模式仍保持同日 shown-day 跳过。帮助反馈发送开始时会同步通知父层进入发送中，降低用户极快返回 / 关闭菜单导致发送协程和临时图片被撕掉的风险。`ChatTimelineItemsTest` 和 `check-android-build-parity.ps1` 已同步锁住这些边界；本轮不修改三份保护提示词、不新增模型输出过滤、不发布正式 Android 包。

- 新增 `docs/runbooks/codex-automations.md`，把本机 Codex 全局自动化标准配置写进仓库：今日农情人工发布按 22:00 / 23:00 双时间、`gpt-5.5 + xhigh + D:\wuhao`、只查公开新闻 / dry run / 脚本发布且不改仓库 / 提示词 / 过滤 / 主聊天链；运维自动化每天 23:00 只读跑后台行动项巡检；续费与证书巡检每周只读查资源、证书、套餐和成本。`AGENTS.md` 同步固化“Codex 本机聊天记录 / 归档线程只算临时缓存，长期真相必须写进仓库规则、项目记忆、ADR 或 runbook”。同步修正今日农情 ECS timer 为 05:35 主触发 + 05:50 / 06:10 补查，SLS 文档口径为 8 条应用告警。Android 复制菜单和输入框选择菜单补可点击无障碍语义，输入框选择菜单按屏幕边界夹住并允许窄屏横向滚动；登录失效清后台带图 pending 时写入 `auth` 终态失败，避免图文消息失去失败 / 重试入口；帮助反馈 2000 字按 Unicode 码点计数。后端记忆 Redis lease 低于摘要超时 + 缓冲时自动抬高，避免误配导致重复跑摘要模型；记忆探针不再回退固定 3000；今日农情状态脚本输出 `content_present / content_valid`；官网用户协议同步 App 内 6 段结构，隐私政策补帮助与反馈排障信息披露，后台礼品卡只读文案改为卡尾号 / 脱敏码。该轮不修改主对话锚点、今日农情提示词、记忆文档提示词、官网首页文案，不新增模型输出过滤、关键词拦截、字数硬卡或 `max_tokens`，不发布正式 Android 包。

- 按用户“不能卡渲染、正文必须一边生成一边渲染”的反馈，联网校准 Vercel Streamdown / Chat SDK streaming markdown、GFM 表格和 Android LazyColumn 后继续调整主聊天 renderer：正文仍实时打字渲染，不等整段完成；标准表格在表头 + 分隔线成立后先显示稳定表格壳，后续行按流式内容继续加入，避免等到整张表完成才突然吐出；加粗章节前缀采用“乐观但稳定”的轻分割线策略，`**处理建议**` 这类 active 尾行先按段落显示加粗文字和分割线，后续若继续接正文也保留分割线，完成独立成行后再按标题块渲染，不再一会儿出现一会儿消失。前台聊天流遇到 401 时先落“回复未完成 · 点击重试”再触发登录失效清理，带图 WorkManager 终态失败改为同一次本地提交里写失败态并移除 pending，降低进程被杀时失败记录和 pending 半写。后端主聊天若上游 `[DONE]` 但 assistant 正文为空，会发 `EMPTY_ASSISTANT_REPLY` SSE 错误并记录日志，不再伪装空白完成态；SLS 云上应用告警从 7 条补到 8 条，新增 `nongji-chat-stream-integrity`，并把 quota outbox needs_ops 自动重试纳入告警查询。App 内和官网隐私政策把账号注销口径收成“15 个工作日内完成核验和处理流程，并对依法可删除或匿名化的信息作相应处理”，避免承诺已经自动物理删除 / 匿名化全量账号数据。本轮不修改主对话锚点、今日农情提示词、记忆文档提示词、官网首页文案，不新增模型输出过滤、关键词拦截、字数硬卡或 `max_tokens`，不发布正式 Android 包。

- 继续收口主界面滚动 / 渲染 / 今日农情和后端归档边界：Android 今日农情 stable key 归一日期格式，避免 `2026-06-19` / `20260619` 导致 LazyList item 身份抖动；主界面远端恢复只接受当天、非空锚点、卡片日期同天的一条展示副本，旧日期或脏副本不会混进当前主界面；同一运行跨天后，旧历史尾部不再立刻触发新一天农情自动插入，必须等下一条新的完整 AI 回复后再释放。streaming 活跃尾行里的中文章节标题先按普通段落，行完成后才显示标题和轻分割线；中断路径不再 flush 未显露的 typewriter buffer，只保留用户已经看到的内容。后端主聊天在模型上游 DONE 后、给客户端最终 DONE 前，对 `AppendSessionRoundComplete` 做 3 次短重试；冲突 / archive missing 不重试，瞬时归档错误最终失败时发 `STREAM_ARCHIVE_FAILED` SSE 事件并让客户端走现有远端 snapshot 恢复链。App / 官网隐私政策把服务端运行和安全日志留存同步为约 180 天、近期排障较早低频保存。本轮不修改三份保护提示词、不新增模型输出过滤或 `max_tokens`，不发布正式 Android 包。

- 继续收口多代理挑刺后的 P1：Android 今日农情“已看见”判定改用当前输入区实际遮挡高度，避免键盘、长文本或多图预览盖住卡片时被误算已读；当天农情插入时只有插入前本来就在底部、且用户未拖动 / 未浏览 / 未交互，才补一次现有底部锚点，用户正在看上方内容时不强制拉底。失败 / 中断 AI 回复不再显示半成品“复制表格”；页面销毁时释放带图后台发送 active 内存标记，避免 WorkManager 被旧活跃标记长期挡住；Worker 终态失败会保存已上传远端图片 URL，前台最终落失败态前补回用户消息，重发复用同一组 URL，降低 `client_msg_id` 冲突。后端记忆摘要 pending job 成功写回后会继续追平剩余冻结 job，每个 job 都重新读取 fresh snapshot / `updated_at` / `session_generation`，失败或 stale 仍保持 pending。同步修正文档旧口径：流式中断 / 冷启动恢复失败态不暴露尚未 reveal 的尾部 buffer，正常 DONE 才按打字节奏排完。该轮不修改主对话锚点、今日农情提示词、记忆文档提示词，不新增模型输出过滤、关键词拦截、字数硬卡、`max_tokens`，不发布正式 Android 包。

- 按用户真机截图纠偏继续收口主聊天渲染：截图里的问题是 `• 农业场景：` 后面少字，不是输入框遮挡。Android renderer 新增单测锁住加粗 bullet 标签后冒号正文必须保留、下一行续句不能被下一条列表吞掉；如果新包“全文复制”同样没有冒号后的正文，应优先判断为模型原文输出了空标签，而不是当前渲染吃字。表格正文行多出的列不再被静默丢弃，统一合并进最后一列；流式中断 / 冷启动恢复失败态不暴露尚未 reveal 的尾部 buffer，仍以已展示内容作为可见失败快照，正常 DONE 才继续按打字节奏排完。今日农情可见判定扣除底部输入区覆盖高度，本地视觉锚点会在保存远端展示记录前冻结，避免保存接口慢或用户继续发送后卡片漂到最新 AI 回复后。本轮不修改主对话锚点、今日农情提示词、记忆文档提示词，不新增模型输出过滤、关键词拦截、字数硬卡、`max_tokens`，不发布正式 Android 包。

- 按不同 Android 版本 / 机型适配巡检先收口两处低风险 P1：`androidx.activity:activity-compose` 从 `1.9.2` 升到官方已修复 Photo / Video `ActivityResultContracts` URI 安全兼容问题的 `1.12.4`，降低部分系统补丁机型打不开 Photo Picker 的风险；`PendingChatSendWorker` 后台带图兜底调用完整 SSE 时给 `SessionApi.streamChatToCompletion` 增加 8 分钟墙钟取消，超时后按可恢复失败退避重试，避免普通 WorkManager Worker 在弱网 / 长回复下无上限挂住。该改动不改图片数量、图片压缩规则、主聊天前台 SSE、滚动链、模型提示词、模型输出过滤或 Android 正式发布；低端机长回复 / 表格渲染性能、定位权限精简、老系统相机相册保存一致性仍需后续单独评估。

- 继续按“低成本运维”口径收口 SLS 日志留存：没有新买 WAF / 高防 / CDN / SLS 资源包，没有新增 Logstore，没有采完整 Nginx access，也没有把聊天正文、AI 回复、图片 URL、完整手机号、token 或密钥写进 SLS。现有业务 Project 仍只保留 `server-go` / `nginx-error` 两个 Logstore、1 shard、邮件告警；为满足网络 / 安全日志 6 个月证据，TTL 调整为 180 天，但只保留 7 天热存储、173 天低频存储，避免 180 天全热存储。`setup-sls-logging.ps1` 会创建或更新该低成本分层配置，`check-sls-cost-guard.ps1` 同时检查 TTL 下限 / 上限、热存储天数、低频存储天数、Logstore 数、Shard、自动分裂、append meta 和归档存储漂移；日志 / 合规 / 成本 runbook 和项目记忆已同步。该改动不改变模型输出、不新增内容过滤、不发布 Android 包。

- 继续修正长期记忆提取失败的兜底边界：如果用户半夜忘记充值、模型摘要长期不可用，`session_ab.pending_memory_jobs_json` 可能排队多批冻结 job。主聊天现在不会只补队首 job，而是扫描整个 pending 队列，把所有已经滑出当前 A 窗口的轮次按顺序临时作为静默后台背景补给模型，并按 `client_msg_id` 去重；仍在 A 窗口里的轮次不重复补。每次摘要“提取 + 写库”成功后仍只弹出队首 job，剩余 job 后续继续提取和补偿。该改动不修改主对话锚点、今日农情提示词或记忆文档提示词，不新增模型输出过滤、关键词拦截、字数硬卡或 `max_tokens`。

- 本轮继续按“收口收尾、不发散”修复多代理抓到的实锤风险：记忆摘要提取失败时，待补偿 job 仍保留用于后台继续提取，但主聊天兜底只临时补入已经滑出当前 A 窗口的轮次，仍在普通滑窗里的轮次不重复灌给模型，避免“失败一次就整批上下文塞进去”和重复成本；旧本机身份合并到既有手机号账号时，`session_ab.a_json` 现在会合并 target/source A 窗口，避免目标账号已有 A 窗口时丢掉旧本机未摘要对话。Android 带图后台发送如果仍在 `PendingChatSendStore` 队列中，用户图文下方会显示“后台发送中 · 稍后自动重试”，终态失败仍走原来的“发送失败 / 重发”，避免弱网 pending 静默挂起。ECS 回滚脚本在 Nginx reload 失败时会恢复旧配置，运行期资源备份缺 `assets / migrations / go.mod / go.sum` 时拒绝混版本回滚；`current-status.md` 不再把某个历史 commit 写成长期当前线上版本，线上 revision 以 readiness 实测为准。本轮不修改主对话锚点、今日农情提示词、记忆文档提示词、官网首页文案，不新增模型输出过滤、关键词拦截、字数硬卡或 `max_tokens`，不发布正式 Android 包。

- 收口本轮多代理挑刺后的确定问题，不再继续扩大方案面：Android 主聊天仍保持一边生成一边渲染，但表格 streaming 尾部未换行行不再提前吞进表格，完成态 / 历史态无尾换行表格仍可解析；兼容 GFM 首尾管道不一致的常见写法，并按 header / separator 同列数收口，少列补空，多出来的正文列按“手机聊天不丢字”口径合并进最后一列，而不是按标准 GFM 直接忽略，遇到缩进代码、fence、引用、标题、列表等新 block 起点会断开，避免外侧管道表格后面的普通 `A | B` 段落被吞进去；表格复制按钮 streaming 期间完全隐藏，完成后才显示。活跃尾行未闭合的 `**处理建议` 和同一尾行已闭合的 `**处理建议**` 都先按普通段落，换行 / settled 确认为独立标题后才显示结构标题分割线，降低分割线出现又消失。今日农情远端 snapshot 晚回时若用户正在拖动 / 惯性滚动 / 浏览态 / 已交互，不替换当前可见消息列表，也不立刻插入保存项；消息 hydrate 和保存农情一起 pending，等非浏览态且没有新发送 / streaming 时先应用远端消息快照再应用农情；如果用户已经开始新发送或正在生成，晚到视觉 hydrate 不插入当前时间线。启动静态贴底共享浏览态刹车，`shouldReplaceHydratedMessages` 增加 id 和 `todayAgriContextDay` 比较，避免正文相同但锚点变化导致农情位置漂移；已插入过的当天农情即使用户紧接着发新消息，也按已展示视觉项稳定保留。后台用户详情 support summary 对只读 / 审计脱敏，帮助反馈列表 / 搜索忽略 system 自动回复作为最新正文；客服反馈能力改为站内基础链路 `partial`，不冒充完整客服运营闭环；检查更新按钮文案和后台窄屏布局继续收口；上线门禁把未接真实支付但入口关闭的状态标为 `payment closed guard / safe_placeholder`，汇总输出 `ready_without_paid_iap`，不冒充支付 ready；日常后台巡检输出 `scope=daily_actions_only`，避免把上线人工确认项忽略误读成正式上线全绿；ECS 发布失败恢复 API / 后台 Nginx 旧配置，后台静态部署验证入口 JS 资源；官网用户协议与 App 服务协议同步补“复制、截图、转发或对外使用 AI 生成内容需自行核验”的边界。该轮不修改主对话锚点、今日农情提示词、记忆文档提示词、官网首页文案，不新增模型输出过滤、关键词拦截、字数硬卡、`max_tokens`，不发布正式 Android 包。

- 后台敏感信息和主聊天边界继续按上线前口径收口：owner 继承最高权限，能在用户管理和帮助反馈查看 / 复制完整手机号并按完整手机号搜索；`support`、`finance_ops` 按职责可看手机号，`ops_readonly` / `auditor` 不再走 `phone_hash` 搜索，也不再收到客服正文短摘、处理备注或聊天轮次摘录。帮助反馈和客服备注仍允许用户 / 授权客服发送数字、手机号、订单号、礼品卡码等排障信息，不做内容拦截。记忆摘要 pending job 滑出当前 A 层窗口时，主聊天会把该冻结窗口临时作为静默后台背景补给模型，直到摘要写回成功，避免摘要失败期间跨轮丢记忆；这不额外调用摘要模型、不改记忆文档提示词。今日农情状态接口改读 raw status，人工锁定内容不会因旧 JSON 暂不可展示被自动兜底覆盖；Android 今日农情“已看见”改为实际可见高度阈值，表格复制按钮 streaming 期间完全隐藏。运维脚本补后台部署失败回滚旧静态包、SLS 意外启用规则告警、日志查询手机号脱敏和上线总门禁自动后台 smoke。该轮不改三份保护提示词、不加模型输出过滤、不发布正式 APK。

- 修复记忆提取里一处容易把正常并发误判 stale 的边界：`TouchSessionContext` 重复写入时不再更新 `session_ab.updated_at`，只更新地区、地区来源、可信度和最后活跃时间。这样用户马上发第 7 轮、后端只是记录地区 / 活跃时间，不会把第 6 轮冻结记忆 job 的写回版本打乱；真正推进快照版本的仍是完整 AI 回复归档、记忆写回、清空历史等会改变聊天 / 记忆事实的写入。新增单测锁住该 SQL 行为，现有记忆写回继续用 `round_total + updated_at + session_generation + cleared_at` 事务校验，不新增记忆提示词修改、不加模型输出过滤或 `max_tokens`。

- 按用户“消费 / 充值 / 购买 / 扣次应该全自动，不可能天天盯后台”的拍板，继续把额度待补扣从“后台可人工处理”推进到“后台自动追账 + 自动终结 + 异常提醒”：`needs_ops` 不再等同于停止自动处理，数据库短抖、锁等待、连接超时等可重试错误会按默认 6 小时低频自动追账，成功后转 `done`；历史或新产生的 `needs_ops` 到期后也会继续被 worker 捞起处理；后台 worker 处理 due job 前先原子 claim 并设置短租约，owner 已应急豁免 / 终结的行不会被后台又拿去补扣。`QUOTA_EXHAUSTED` 这类极少数不能安全追扣的业务边界不拿未来权益乱扣，会自动转 `uncollectable` 作为对账记录关闭，后台显示为“自动终结”。会员额度页和监控文案改为“扣次自动对账 / 自动追账”，不再把待补扣包装成 owner 日常待办；owner 操作接口只作为应急修账工具保留。SLS 告警脚本 / 巡检脚本保留 `nongji-quota-outbox-needs-ops` 历史规则名，但查询收窄为 claim / 状态写入失败；普通 `needs_ops` 自动追账排期和自动终结记录不发邮件，记忆摘要普通模型失败 / 写文档失败也不发邮件，只有待补偿状态写回失败才提醒。新增只读脚本 `scripts/check-admin-monitoring-actions.ps1`，可用后台账号读取 `/admin-api/v1/monitoring` 并把行动项脱敏输出成机器可读状态，默认不把上线准备 attention 当日常故障。本机已创建低权限 `ops_readonly` 自动巡检账号 `codex_ops_monitor`，密码只保存到本机私密 `prod-secrets.json`；本机 Codex 自动化 `运维自动化` 每天 23:00 可自动登录后台巡检，不再因缺凭据跳过。部署脚本也新增默认拒绝 `server-go` 脏工作区，避免未提交代码被打包上线但 revision 仍显示 HEAD。本轮不修改主对话锚点、今日农情提示词、记忆文档提示词，不新增模型输出过滤、关键词拦截、字数硬卡或 `max_tokens`。

- 继续按“不能让后台扣次失败挡住用户体验”收口额度待补扣后台治理：`quota_consume_outbox` 状态扩展为 `pending / done / failed / needs_ops / waived / uncollectable`，worker 自动重试到阈值后转 `needs_ops`；先补过 owner 应急接口，随后已按“全自动”口径把日常页面收成“扣次自动对账”，由系统继续追账或自动终结，owner 接口只作为技术应急工具保留。用户下一轮聊天仍不会因待补扣 `pending / failed / needs_ops` 被卡住。本轮不修改主对话锚点、今日农情提示词、记忆文档提示词，不新增模型输出过滤、关键词拦截、字数硬卡、`max_tokens` 或用户 / 客服内容拦截，也不部署线上服务。

- 继续按“仓库有失败推送 / 全盘挑刺 / 联网校准”收口：先确认所谓失败推送实际是 GitHub Android CI 红灯，不是 git push 失败；`e851da48` 已修正吐字节奏 parity 断言并跑绿 Android CI / Project Memory。随后补三处实质边角：聊天表格正文续行必须有表格边界，表格后一段普通 `A | B` 文字不会被吞进上一张表，双反引号行内代码里的 `|` 也不会拆成假列；今日农情只有实际进入当前 `LazyColumn` 可见 item 范围后才算“用户已看到”，只保存远端展示记录或插入 timeline 不会提前持久化 shown day 或携带 `today_agri_context_day`；旧本机账号迁移到手机号账号时同步复制 `quota_consume_outbox`，避免待补扣队列留在旧身份。另用仓库配置脚本刷新了 ECS 今日农情 systemd 脚本，远端脚本已是严格解析唯一 active slot、无固定 3000 回退；未触发一次性生成。本轮不修改主对话锚点、今日农情提示词、记忆文档提示词，不加模型输出过滤、不发布正式 APK。

- 修复上一笔 `173b2e7e` 的 GitHub `Android CI` 红灯：本地和远端 `master` 都已是 `173b2e7e`，不是 Git push 失败；失败点是 `check-android-build-parity.ps1` 仍锁着旧吐字节奏断言。现已把 parity 门禁同步到新口径：streaming 换行延迟 `86/66ms`、中文单字 `19ms`；小球最短展示、呼吸节奏和尺寸断言不动。本轮仍不发布正式 APK、不改三份保护提示词、不新增模型输出过滤。

- 多代理交互挑刺后继续补 Android 主聊天收口：`PendingChatSendWorker` 的后台带图兜底在终态失败时会写入本地终态失败标记，`ChatScreen` 的 pending 图片恢复循环先查 `/api/session/snapshot`，有完整远端回答就恢复成功，没有回答且确认终态失败时才把原用户图文气泡标成“发送失败 / 重发”；成功、取消、重试和删除历史会同步清理 pending 与终态失败标记，避免静默挂起或后台成功后前台误报失败。今日农情远端 snapshot 恢复兼容后端保存的用户 `client_msg_id` 锚点，会映射到对应 assistant 回复后方插入，不再因为锚点被后端归一而漂到最新回复后。聊天 renderer 只做启发式小修：紧凑编号小标题不在 active streaming 半行提前生效，表格头 + 分隔线半截流式保留换行，普通 `**重点** 后面还有 **正文**` 不误判成小标题分割线；AI 正文吐字速度按用户要求小幅加快，但小球最短展示、呼吸节奏和尺寸不动。新增单测覆盖这些边界；本轮不改三份保护提示词、不加模型输出过滤、不发布正式 APK。

- 按用户真机截图反馈继续修聊天 Markdown 细节：`ChatStreamingRenderer.kt` 新增“紧凑编号小标题”识别，只对 `1. 三大病害：`、`2. **三大虫害：**` 这类短、以冒号结尾的编号行压低行高和字重，后续紧接列表的上间距从 12dp 收到 6dp；普通长编号句仍按原正文列表渲染。debug-only “AI Markdown”预览样本同步补编号小标题 + 子弹列表，便于以后在调试面板里复核。已补单测覆盖短编号标题和长编号句不误伤。本轮只改 Android 渲染样式，不改三份保护提示词、不加模型输出过滤、不发布正式 APK。

- 继续按“拉满代理、全盘挑刺、联网校准”收口交互和运维边界：后端保存今日农情主界面展示记录前，会把 `assistant_...` 锚点归一成真实用户轮次并确认已存在 `session_round_archive` 归档，缺失时返回 `today_agri_anchor_not_archived`，避免写入后续无法恢复上下文的脏锚点；Android 删除历史后不再把当天重新保存成本地“已展示”，空态仍不显示今日农情，但新对话拿到完整 AI 回复后当天农情可重新出现并保存；表格解析识别行内代码里的 `|`，不再把 `` `N|P|K` `` 拆成假列；`probe-ecs-today-agri.ps1` 解析不到唯一 active slot 时直接失败，不再回退固定 3000；`check-ecs-readiness.ps1` 新增可选 `-ExpectedRevision` 用于核对线上是否为指定提交。同步更新发版 runbook：只说“修 bug”不等于发布 Android 新版本，正式 APK / 检查更新必须等用户明确口令。提交 `fbfc8bee` 已部署到 ECS，Nginx active upstream 和后台 upstream 均为 `3000`，`check-ecs-readiness.ps1 -ExpectedRevision fbfc8bee` 和 `check-public-blackbox.ps1` 均通过。本轮不修改主对话锚点、今日农情提示词、记忆文档提示词，不新增模型输出过滤、关键词拦截、字数硬卡或 `max_tokens`，不发布正式 APK。

- 按用户最新要求，根 `AGENTS.md` 明确把主对话锚点、今日农情提示词、记忆文档提示词列为最高敏保护提示词：没有用户明确授权落地时，只能审查、挑刺、解释风险和给候选草案，不能直接改对应文件、内嵌 prompt 文案或等价模型指令；“全盘挑刺 / 拉代理 / 联网校准 / 用户体验为主”也不自动授权改这三类提示词。

- 继续按交互类 P1 修复 Android 登录态体验：图片上传 `/upload` 遇到 401 会调用统一登录失效链路，清本地登录态并回登录页，登录页显示“登录已失效，请重新登录后继续使用”；用户主动“退出当前设备”改为本机退出优先，远端吊销请求弱网 / 失败时仍清本地 token、取消当前账号 pending 任务并回登录页，远端失败只记脱敏日志。登录失效和主动退出用 `AuthSessionClearReason.Invalid / LocalLogout` 区分，避免主动退出也显示“登录已失效”。`check-android-build-parity.ps1` 已加入静态守卫，防止上传 401 回登录和弱网退出兜底回潮。本轮不发布 APK。

- 提交 `c7b2b9dc` 已部署到 ECS：发布脚本在远端重新跑 `go test ./...` 并重建二进制，Nginx active upstream 切到 `3001`，后台 `/admin-api/` upstream 同步为 `3001`；readiness 显示公网 `/healthz` 返回 `revision=c7b2b9dc`、严格鉴权、百炼、短信、Redis 和 OSS 上传均正常，后台未登录接口继续 401。该部署只更新后端服务和运维发布链，不发布 Android APK、不配置检查更新。

- 继续补运维假绿：`rollback-ecs-server.ps1` 回滚会同步恢复同后缀 `REVISION.bak-*`，有 revision 的备份会在 upstream 和 public healthz 两处校验 revision；legacy 老备份没有 revision 时只允许在生产健康检查通过后带提示放行，避免把回滚前的新 commit 误当作已回滚证据。`check-ecs-readiness.ps1` 缺 `server_revision` 会失败；`check-resource-capacity.ps1` 会解析短信巡检输出的 `sms_package_status`，只有 `confirmed` 才不提醒，避免普通短信套餐包余额仍需控制台确认时资源巡检汇总成 ready。

- 按用户复核继续收口主聊天内部背景提示：`chat.go` 里历史轮次时间 / 地点前缀统一改成“后台背景时间 / 地点（仅供参考）”，不再写“仅供判断对话间隔 / 地区背景”。这是为了保留农业场景里按时间和地点判断农时、病虫窗口、区域管理建议的空间，同时继续避免模型主动扩展过往背景废话；不新增内容过滤、关键词拦截、字数硬卡或 `max_tokens`。

- 提交 `123d5f59` 已部署到 ECS：发布脚本在远端重新跑 `go test ./...` 并重建二进制，Nginx active upstream 切到 `3000`，readiness 显示公网 `/healthz` 返回 `revision=123d5f59`、严格鉴权、百炼、短信、Redis 和 OSS 上传均正常，后台未登录接口继续 401。该部署只更新后端服务和运维发布链，不发布 Android APK、不配置检查更新。

- 继续按“全盘挑刺、联网校准”落地两处低风险修复：主聊天 `chat.go` 的记忆文档注入说明从“用于上下文承接和减少重复追问”同步改成“后台背景信息中的记忆摘要，只作静默参考，回答聚焦本轮问题”，历史轮次时间 / 地点前缀也弱化为“后台背景时间 / 地点，仅供参考”，避免主锚点刚收口后又被内部 prompt 片段诱导模型展开记忆、历史或用户画像，同时保留时间 / 地点对农时和区域建议的正常参考空间。这只改提示词表达，不加后端内容过滤、关键词拦截、硬字数卡或 `max_tokens`。同时发布链新增机器可读 revision：部署脚本写 `/opt/nongjiqiancha/server/REVISION`，`/healthz` 返回 `revision`，发布切流前后校验 revision 等于本次 commit，公网 Nginx reload 后会等待 healthz 也露出本次 revision 再通过，readiness 打印 `server_revision`。

- 按用户确认，主对话锚点 `server-go/assets/system_anchor.txt` 的 `B. 信息使用` 第 `(8)` 条改为覆盖“后台背景信息（包括记忆摘要、历史轮次、用户画像、时间地点及系统补充）”。新口径要求这些背景只作静默参考，回答聚焦本轮问题；非直接相关时不要主动提及、展开、串联过往内容，也不要追加基于背景信息的顺带建议。该改动是提示词层面的表达收口，用于减少主模型把记忆 / 上下文 / 用户画像带成无关废话；不改记忆文档提示词、今日农情提示词、模型参数，不新增后端内容过滤、关键词拦截、相似度硬拦截、字数硬卡或 `max_tokens`。

- 主对话锚点收口提交 `c8caace6` 已通过 `scripts/deploy-ecs-server.ps1` 部署到 ECS，部署包提交为 `c8caace6`，Nginx active upstream 从 `3000` 切到 `3001`；部署过程在 ECS 上重新跑 `go test ./...` 并重建二进制，切换后 readiness 显示公网 healthz 200、严格鉴权、百炼、短信、Redis 和 OSS 上传均正常，后台未登录接口继续 401。该部署只更新后端服务和 `server-go/assets`，不发布 Android APK、不配置检查更新、不动官网或管理后台静态前端。

- 按用户“不能让后台扣次失败挡住用户体验”的拍板，`/api/chat/stream` 取消了入口处按当前用户 `quota_consume_outbox pending / failed` 返回 `QUOTA_SETTLEMENT_PENDING` 的前台门闩。`quota_consume_outbox` 仍在归档事务内作为待补扣队列写入，`quota_ledger` 继续按 `user_id + client_msg_id` 幂等防重复扣，服务端短重试和后台 worker 仍按回答完成时档位、自然日和完成时间补扣；后台总览 / 监控面板继续显示“待补扣”。当前取舍是前台放行、后台追账：不再把待补扣失败转成用户下一轮聊天卡死，剩余风险转成后台待处理和短期成本 / 额度对账压力，后续不要重新加用户侧限制。本轮不改主对话锚点、记忆提示词、今日农情提示词，不新增模型输出过滤或 `max_tokens`。

- 交互代理挑刺后补主聊天失败态和今日农情保存边界：`clearStaleFailureAffordancesForNewSend` 不再因为新发送擦掉旧失败用户消息或失败 AI 消息的重试入口，发送 / 重试起步也不再保留旧 `UserBrowsing` 浏览态，点击发送即交回现有 AutoFollow / 正向底部锚点主链。今日农情主卡在远端模式下不再“刚可见就持久化当天已展示”，只有远端展示项保存成功或 snapshot 已确认存在展示项时才写本地 shown day；若保存失败，下次启动仍可再次展示并继续补保存，避免当天卡片被本地标记抑制。本轮不恢复反向列表、overlay、raw delta、streaming 小分割，不改主对话锚点或模型输出限制。

## 2026-06-18

- 继续按“拉满代理 / 全盘挑刺 / 联网校准”收口上线前非肉眼风险：项目记忆脚本把整个 `app/**` 纳入 watch，本地裸跑会检查 staged / unstaged / untracked 工作树，GitHub PR 环境优先用 PR merge-base 避免只看最后一个 commit；新增 `scripts/check-server-migration-risk.ps1`，ECS 后端发布和上线总门禁会在打包前拦高风险迁移 SQL，发布脚本还会检查 `server-go` 新增顶层运行时代码目录是否漏打包。`check-launch-readiness.ps1 -IncludeBuilds` 新增官网 `site` production build。帮助反馈 `/internal/support/*` 共享密钥入口收紧为 loopback / 私网调用，公网客服管理继续必须走后台账号、CSRF、角色和审计。今日农情人工发布 / 状态脚本按北京时间默认日期并加请求超时，ECS 今日农情 timer 脚本严格解析唯一 active slot，不再误打固定端口。资源巡检错误输出继续脱敏，`check-android-build-parity.ps1` 和后端 handler 单测锁住 `today_agri_items_unavailable`。该轮不改主对话锚点、记忆提示词、今日农情提示词、官网首页文案，不加模型输出过滤、关键词拦截、字数硬卡或 `max_tokens`。

- 按用户“联网校准、学成熟渲染组件套路但不要直接替换主链”的口径继续全盘挑刺：新增 ADR-0005，记录 Markwon / commonmark-java / mikepenz / compose-markdown 的取舍，当前不把第三方 renderer 接进主聊天 UI，而是学习 AST/block model、stable block + active streaming block、结构节点组件化、复制/点击以整条消息 settled 为边界这些套路。代码侧同步清理 `ChatScreen.kt` 旧 Markdown parser、缓存和旧 block UI 残留，避免两套渲染口径并存；表格“复制表格”按钮改为整条 AI 消息完成后才可用，避免 streaming 中复制半截表格；`/api/session/snapshot` 增加 `today_agri_items_unavailable`，Android 区分“确定没有今日农情展示项”和“读取展示项失败”，读取失败时不清当前已显示农情。该改动不新增模型内容过滤、不改提示词、不改主聊天正向滚动主方案、不发布正式包。

- 按用户“今日农情保留，但出现瞬间不要强制拉到底部；其他开机进入 / 手动回底 / AI 生成仍尽量贴底”的口径继续收口主界面：Android 已删除今日农情专用 `force=true` 回底 effect 和遗留滚动状态，今日农情进入 timeline 的那一拍不再主动抢用户视线；后续仍作为普通视觉尾部参与主聊天正向列表的回到底部、开机贴底和 AutoFollow。聊天正文轻分割线继续只跟随结构标题，补稳未闭合但已独立成行的短加粗标题，下一行正文到达后不会把分割线打掉；active 半截加粗内容若已经带空格、像继续写正文，则先按普通段落处理，减少分割线先出现又撤回。表格分隔行不会触发正文分割线，相关 Android renderer 单测和 `check-android-build-parity.ps1` 门禁已补。本轮不删除今日农情、不改三份提示词、不加模型输出过滤、不改主聊天正向滚动主方案、不发布正式包。

- 继续按用户“Codex 自动化多一个时间、先判断明天有没有新闻、5.5 模型最高思维”的口径补今日农情人工发布：新增内部只读状态接口 `GET /internal/jobs/today-agri-card/status?day_cn=YYYYMMDD` 和脚本 `scripts/get-today-agri-manual-status.ps1`，自动化 / 人工发布前可先查目标日期是否已经 `source_type=manual / manual_locked=1`；`scripts/publish-today-agri-manual.ps1` 真实发布前也会重复检查，已人工锁定时输出 `skipped=true / reason=manual_locked` 并停止，避免 22:00 成功后 23:00 覆盖。后端 05:35 systemd timer 仍是每日一次，服务端内部最多 2 次模型尝试只用于解析失败重试；本机全局 Codex 自动化从每天 22:00 扩成 22:00 和 23:00，继续使用 `gpt-5.5`、`reasoning_effort=xhigh`。自动化只负责读 runbook、查公开新闻、写 3 条今日农情并通过脚本发布，不能修改仓库文件 / 本机配置 / 服务端提示词 / 项目文档 / 业务代码 / 脚本，不能 `apply_patch` 或 git 提交。

- 继续按“交互类重点 / 表格再看”挑刺收口：聊天 renderer 的 Markdown 表格识别要求分隔行至少 2 列且每列至少 3 个 `-`，`-|-` 这类短分隔和单列 `|---|` 不再误判成表格；缩进代码块内的 `|` 不做表格解析；生成中表格右上角“复制表格”先置灰，等完成态再可复制，避免复制半截内容。Android 批量图片上传现在会把 401 登录失效的明确提示透传给主聊天和帮助反馈入口，debug-only 预览也补了登录失效 / 上传失败两类提示。后端帮助反馈用户侧错误码改为稳定业务码，Android 可把 401、图片过多、空反馈等显示为用户能理解的文案。该改动不改主聊天滚动主方案、不加模型输出过滤、不发布正式包。

- 按用户最新“就按轮数、失败不能等下一个 6 / 9 轮周期、不能涨成本也不能跨轮丢记忆”的口径，收紧记忆文档自动提取：触发频率仍是 Free / Plus 每 6 条完整 AI 回复、Pro 每 9 条完整 AI 回复；触发时把当时 A 层 6 / 9 轮窗口冻结成 `session_ab.pending_memory_jobs_json` 队列里的一个待提取 job，每个 job 仍只包含这 6 / 9 轮窗口，不扩大到 30 轮归档输入。一旦模型失败、超时、写库失败、旧快照写回过期或 Redis 租约暂不可用导致 `pending_retry_b=1`，后续每一条新的完整 AI 回复归档后都会马上按队首冻结 job 再次尝试，不等下一个整除点，也不会让旧窗口随 A 层滑动丢失；如果旧 job 未成功时又到新的 6 / 9 轮触发点，会追加新的冻结 job 排队。写回改为事务内锁定当前 job，并用 `round_total + updated_at + session_generation + cleared_at` 条件保护，成功后只弹出队首 job；还有剩余 job 时继续 pending，删除历史后的旧异步结果不会写回新会话。本轮不改记忆提示词、不加模型输出过滤、不设置 `max_tokens`。

- 按代理交互审查落地一批低风险提示修复：Android 登录页在短信发送和登录阶段显示“发送中 / 登录中”；图片上传遇到 401 时提示“登录已失效，请重新登录后再上传图片”；主聊天 429 限流不再移除 AI 尾部重试入口，而是保留“回复未完成”类可重试尾巴；帮助与反馈发送遇到 401 / 409 / 429 / 超长 / 图片过多 / 空内容时给出对应白话提示。后台保留搜索能力和 CSRF 校验，只把 `csrf_required`、检查更新保存失败码、客服回复超长 / 通用失败翻译成运营能理解的提示；今日农情人工发布默认日期改为按北京时间计算，18:00 后填次日。该改动不关闭后台搜索、不修改检查更新静默检查策略、不发布正式包。

- 账号管理“删除历史对话”确认卡文案按用户定稿补齐顿号，展示为“将删除当前账号历史对话，并清除长期记忆。会员、礼品卡、反馈记录不受影响。”；该改动只改 Android 用户可见文案，不改变 `/api/session/clear` 的真实删除范围。

- 全盘上线审核继续补一批可落地的 P2 风险：短信验证码登录用 Redis 原子“校验 + 短处理中占位”挡住同验证码并发复用，成功才清验证码、失败释放占位；旧身份迁移复制账本 / 归档时统计 `INSERT IGNORE` 被忽略行，出现冲突则保留旧侧源记录并让迁移状态带 `_with_copy_conflicts`，避免静默删历史。后台帮助反馈完整正文和原图只给 `owner / support`，只读和审计角色看摘要、数量和状态；后台 CSRF token 不再写 localStorage，只从 cookie 取。资源容量严格巡检接入下载域名 / OSS CNAME / HTTPS 签名探针，CNAME 签名工具默认允许 `download-probes/` 只读探针前缀；ECS 后端回滚脚本按同一备份后缀同步恢复 `assets / migrations / go.mod / go.sum`，runbook 同步说明哪些内容仍不能自动回滚。Android 侧只修本地/远端图片都解码失败时的过期占位，以及协议长标题小屏大字体裁切风险；不改提示词、不加模型输出过滤、不改主聊天滚动主方案、不发布正式包、不部署线上服务。

- 全盘上线审核先补本机门禁和记录 GitHub workflow 权限缺口：`scripts/check-admin-surface.mjs` 已把后台帮助反馈正文脱敏、图片脱敏和 CSRF token 不落 localStorage 纳入静态守卫，本机已跑通 `admin` / `site` 的 `npm audit --audit-level=high` 与 build。原计划把高危依赖审计、后台 surface 检查和 `README.md` 项目记忆触发同步接入 GitHub workflow，但当前 GitHub OAuth 凭据缺少 `workflow` scope，远端拒绝推送 `.github/workflows/*` 改动；因此本轮不提交 workflow 文件，GitHub workflow hardening 留待有 workflow scope 的凭据后补。该处理不改 Android / Go 业务逻辑、不发布正式包、不部署线上服务。

- 第二轮上线前挑刺正在按当前 diff 继续拉代理复核；本轮已先落地几处明确问题：`/api/session/snapshot` 改为同一个只读 `REPEATABLE READ` 事务读取 `session_generation`、主快照、UI 归档和当天今日农情展示项，避免删除历史和远端 hydrate 并发时拼出“新 generation + 旧内容”的混合包，同时避免 GET 快照接口写入 `session_generation` 或与聊天归档写链抢锁；代理复核后又把 UI 归档和今日农情展示项读取失败改成降级为空并打 warning，保住主聊天快照和记忆文档不被附属展示表拖成 500。Android 主聊天今日农情允许远端已保存展示项在本轮被 suppress 后仍恢复，降低“一会儿有一会儿没”的体感，同时新增静态历史浏览标记，只有用户真实拖动离底时才不强制贴底，避免程序滚动残留误挡静态恢复；检查更新弹窗彻底按普通更新处理，即使底层字段出现 `force_update` 也保留“稍后”和点外关闭，客户端 APK URL 校验补齐 decoded path、`.apk` 后缀和 `..` 拒绝，和后端 / release-match 一起锁住只接受 `download.nongjiqiancha.cn/android/releases/` 正式路径；parity / release-match 脚本同步锁住当前不上强更和不混入测试包短签名链接的产品口径。今日农情旧缓存读取只要求前 3 条标题 / 摘要可展示，超过 3 条的旧 / 手工脏尾巴不再拖垮整张 ready 卡；解析日志里的 `display_sources` 改为只记录公开展示同款短来源名，避免 URL、手机号或 token-like 文本进入 SLS。该日志脱敏不影响模型生成、不拒绝发布、不改变标题 / 摘要展示，不属于内容过滤。用户最新拍板“所有模型输出不卡、不加过滤限制输出”已固化到根 `AGENTS.md` 和当前状态：主对话、今日农情、记忆整理及后续模型入口都不得新增后端内容过滤、关键词拦截、相似度硬拦截、字数硬卡、`max_tokens` 截断或按主题词卡发布 / 卡写入 / 卡展示；质量和方向只靠提示词、探针、后台抽查和人工运营控制，工程兜底仅限 JSON / 必需字段 / 请求响应大小 / URL 安全 / 日志脱敏 / 密钥保护 / 幂等和错误收口。

- 阿里云告警噪音按不误伤主监控的方式收口：本机全局阿里云 CLI 已从 `3.3.16` 升级到 `3.3.23`，云安全中心 1 条提醒级 OSS 可疑访问事件已核对为近期 OSS CNAME / 证书 / 配置巡检相关运维行为并按“我已手工处理”清零；阿里云 App 里看到的 `InstanceStatus:ArmsStopped` 是默认 CMS 工作空间的 ARMS / 云监控系统事件历史，原因是 `user_stop`，不是 ECS 停机，也不影响 Go 后端、RDS、Redis、OSS 或 App 主链。当前未发现 ARMS 活跃告警、通知策略或用户自建事件规则；默认 CMS 工作空间仍承载 CloudMonitor 2.0 底层免费指标容器，不能为了清红点删除工作空间或关闭 `NongjiQianchaOps`、SLS 应用告警和 9 条资源水位规则。系统事件历史 1 天窗口内仍可能在阿里云 App 里显示，若后续持续新增，再只针对 ARMS stopped 消息订阅 / 事件通知流做降噪。

- 给今日农情补人工发布并锁定能力：后端 `daily_agri_cards` 增加 `source_type / manual_locked / manual_by / manual_at`，新增后台 `POST /admin-api/v1/today-agri/manual` 和内部 `POST /internal/jobs/today-agri-card/manual`，后台今日农情页可填 8 位日期、3 条标题 / 摘要 / 来源并输入 `人工发布 YYYYMMDD` 确认发布；Codex 自动化 / 本机命令行使用 `scripts/publish-today-agri-manual.ps1`，从本机 secret 或环境变量读取 `DAILY_AGRI_JOB_SECRET`，不把密钥交给浏览器。人工发布仍写同一张每日农情表，`model/search_strategy/prompt_version=manual`，同一天自动生成和后台补跑会跳过人工锁定内容；没人人工发布时，原 ECS 05:35 自动生成继续兜底。后台列表和预览会显示“人工锁定 / 自动”和发布人 / 时间，runbook 和根规则同步写清该口径。本轮不修改主对话锚点、记忆提示词、今日农情提示词、官网首页文案、模型输出限制或主聊天滚动主方案。

- 多代理全盘找茬后补了几处非肉眼路径的正式上线坑：登录页协议勾选框从持久化隐私同意状态初始化，勾选本身不立即落同意，只有用户在已勾选状态下发送验证码或登录时才记录同意并初始化运行时；登录协议 / 隐私弹窗改为安全区内自定义宽度，降低小屏和大字号裁切风险。后端记忆文档写回从只校验 `round_total` 升级为校验 `round_total + updated_at + session_generation`，避免清空历史后旧摘要慢返回又写进新会话。检查更新客户端当前忽略 `force_update`，全部按可关闭普通更新展示；正式强更未来需单独拍板和回归。公网黑盒修正更新响应字段为 `latest_version_code`，release-match 正式 APK URL 检查补 query key URL-decode 和最终 URL 短签名拦截；本地 parity 脚本同步锁住这些口径。

- 按用户最新口径把主聊天今日农情收成“主界面历史式普通文本”：今日农情仍不写入真实聊天轮次、不进记忆、不进归档、不扣次，但一旦在主界面展示过，后端会按账号在 `today_agri_user_items` 保存当天一条主界面展示记录和展示用正文副本，正文副本只保留日期、标题、摘要和短来源名，不保存外部 URL；重开 App 或远端 snapshot hydrate 后按当天保存项恢复，让它像历史文本一样稳定存在，不再一会儿出现一会儿消失。远端模式下主界面不再用本地缓存先展示未确认农情。用户后续发送消息时，新消息自然排在它后方；账号管理页删除历史对话会清掉这条主界面展示记录，同一天不再自动冒回主界面；设置页“今日农情”近 30 天公开历史仍来自 `daily_agri_cards`，不随聊天历史删除。为避免删除历史后旧保存请求晚到又把农情插回来，后端保存展示记录时会校验 `session_generation`，清历史事务会先推进 generation 并清记录。Android 本地展示记录清空现在会真正删除本地 key，远端没有记录时不会再拿旧本地记录恢复。本轮不修改主对话锚点、记忆文档提示词、今日农情生成提示词、官网首页文案、真实支付、模型输出硬限制或主聊天正向滚动主方案。

- 本轮代码验证后已部署生产后端和官网静态站：`deploy-ecs-server.ps1` 通过双端口 slot 上线，ECS 端 `go test ./...` 通过后切换到 `3000` upstream，`check-ecs-readiness.ps1` 显示公网 healthz 200、`ok=true`、严格鉴权、百炼、短信、Redis 和 OSS 上传均正常，后台未登录接口继续 401；`deploy-ecs-site.ps1` 重新构建并发布官网 / 协议 / 隐私页，验证根域名、www、协议页、隐私页、ICP备案和公安备案标识均正常。本轮仍不发布正式 Android 版本，不配置检查更新，不把测试包链接写入官网正式下载或后台发布配置。

- 修正设置抽屉“今日农情”历史页的时间分组感：有历史卡片时不再把日期单独飘在卡片外，而是每个日期一张独立白底内容块，块内顶部低调显示日期和“今日农情”，再用一条浅分割线隔开标题和三条资讯；不再堆多条日期线，避免页面看起来太碎。加载、失败、空态仍沿用原灰底稳定容器。debug-only 预览面板同步更新“农情历史页”说明。

- 按用户要求给“删除历史对话”确认卡补一条简短记忆提示，并在 2026-06-18 按用户拍板改成更直白口径：“将删除当前账号历史对话，并清除长期记忆。会员、礼品卡、反馈记录不受影响。”；debug-only 预览面板同步更新该项说明。该改动只改用户可见文案，不改变 `/api/session/clear` 的真实删除范围。

- 按用户反馈把 App 内协议、隐私和风险提示语气继续收平：服务协议第 6 条从“禁止行为”改为“使用规范、责任和协议更新”，删除攻击接口、爬虫、撞库等吓人式黑名单表达；风险提示改成“建议 / 不建议 / 请谨慎核实 / AI 仅作辅助参考”的正式语气。官网用户协议和隐私政策同步更新，官网首页定稿文案不动，三份提示词不动。

- 按用户真机反馈“表格一闪后变成长段、分割线刚有又没、远端拉回别把渲染搞砸”收口聊天 renderer：标准 Markdown 表格现在在 streaming / 完成态 / 远端 snapshot 历史回放里都走同一个轻量表格组件。2026-06-18 按真机截图继续把手机端宽表改成纵向分组对比块，不再强做横向滑动宽表，避免右侧列露成竖字和单元格看起来空隙过大；表格内按钮改为“复制表格”，复制 TSV 给 Excel / WPS，消息“全文复制”遇到表格会输出人眼可读的分段文本。2 列、3 列、4 列及更多列都能接住，行缺列跳过空值，普通 `A | B` 句子不被误藏。生成中未确认成标准表格前按普通文字显示，表头分隔线到达后再整体切成表格组件；完成态 / 远端历史回放也不隐藏不完整表格行，只按原文普通文字显示。标题轻分割线仍不乱加到普通编号 / 表格 / 段落，只补稳 `# / ## / ###`、独立短加粗标题和 `一、...` 中文章节标题，保证生成现场和历史回放口径一致。今日农情在有远端历史源时等待 remote snapshot hydrate 后再进入 timeline，避免本地缓存先露出、远端历史回来又消失；已经进入本次可见 timeline 的农情继续保留。本轮同步更新 debug-only 文本渲染预览，不修改三份提示词、不新增模型输出硬限制、不改变主聊天正向滚动主方案、不发布正式包。

## 2026-06-17

- 最后一轮收尾代理复查结果：Android 主界面、Android 设置 / 会员 / 礼品卡、后端 stream / 会员 / 礼品卡、发布 / 下载 / 运维四个方向均未发现 P0/P1。代理确认 `5a863bf7` 不影响“后端已有完整答案自动恢复”，因为远端 snapshot 完整答案优先于本地 streaming draft 兜底；后端模型流使用独立超时上下文，客户端断开不会必然取消后端生成，已归档答案仍可通过 snapshot 恢复。只修了一处旧巡检文档漂移：`pre-server-feature-audit.md` 不再建议补并行 `app_releases` 主表或把强更写成当前默认能力，改为当前 `app_release_configs + app_release_events` 主链、普通更新默认口径。本轮不改代码、不改三份提示词、不发布正式包、不部署。

- 拉满代理做收尾前全盘复查后，修掉一个主界面冷启动恢复边角：本地保存的 streaming draft 被恢复成“回复未完成”尾巴时，只使用已经显示过的 `content`，不再把尚未按打字节奏 reveal 的 `revealBuffer` 一次性拼进可见正文；正常 `[DONE]` 后尾段仍按原 drain 节奏吐字。新增单测和 `check-android-build-parity.ps1` 门禁，防止恢复路径再次把未显示缓冲吐出来。同步校准项目文档：当前设置页不再包含“当前账号”入口，礼品卡输入是有占位提示、空白归一化和 64 字符长度上限；上线手册明确检查更新日常发布以后台 `app_release_configs` / 发布历史为主，`APP_ANDROID_*` 只作无数据库记录时兜底。本轮不修改三份提示词、不发布正式包、不部署后端或官网。

- 按代理全盘复查继续收口主界面滚动 / 顺序 / 极端恢复边角：今日农情主聊天项只有真正进入可见 timeline 后才打可见日志，且当天已展示日期必须等远端 snapshot hydrate 完成后才落盘；远端 hydrate 成功但不应显示今日农情时，会清掉运行时可见 / 记录门，避免本地旧窗口短暂显示后把当天误记为已展示。前台 SSE 中断和后台远端恢复 fallback 不再把还没按打字节奏 reveal 的 `streamingRevealBuffer` 拼进可见正文，正常 DONE 仍由原 drain 节奏负责尾段吐字，降低弱网 / replay 边界“后半段一口气冒出来”的风险。前台发送和后台 pending Worker 的网络门禁收紧到 Android `NET_CAPABILITY_VALIDATED`，假 Wi-Fi / 门户 Wi-Fi 不再被当成可用公网直接上传或 streaming。debug-only 预览面板最后同步检查更新、今日农情上下文和网络浮层说明，并用 `check-android-build-parity.ps1` 锁住。本轮不修改三份提示词、官网首页定稿文案、真实支付、模型输出硬限制或主聊天正向滚动主方案。

- 按“成熟 App 逻辑”继续收口主界面、检查更新和运营边界：今日农情主聊天口径同步为“空态不显示，安静历史后参考”，没有真实聊天消息时只显示欢迎语，只有可见尾部是完整 AI 回答时才在同一正向 `LazyColumn` 中跟随展示，失败 / 未完成 assistant 尾巴不算完成回答；用户已开始发送而农情未显示时，本次运行不突然插入。Android 检查更新遇到后端返回有更新但 APK 物料不完整时，用户侧统一提示“当前没有可用更新”，内部原因只留日志 / 后台排查；App 本地、后端、后台、官网和 release-match 脚本都把正式 APK URL 收紧到 `https://download.nongjiqiancha.cn/android/releases/...apk`，并继续拒绝测试包路径、短签名参数和外部 APK 域名。帮助与反馈弱网发送成功回调不再清掉用户随后输入的新草稿；礼品卡兑换中输入框不可编辑，配置异常文案改为普通不可用提示。后端 replay 完成态改为必须同时存在 `session_round_archive`，不再仅凭 ledger 向客户端发完成恢复，避免极端脏数据下用户收到 DONE 但恢复不到正文。本轮不修改主对话锚点、记忆文档提示词、今日农情提示词、官网首页定稿文案、真实支付或主聊天滚动主方案。

- 按主界面发送 / 收尾专项复查继续收口：前台 SSE 因 `stream_in_progress`、replay 或网络中断进入远端 snapshot 恢复时，如果当时 assistant 还没有正文，Android 不再直接移除这条 assistant placeholder，而是保留同一条尾巴并显示现有“正在重试...”状态；远端恢复成功后替换成完整答案，恢复失败后回到统一的“回复未完成 · 点击重试”。这只修用户视觉链不断裂，不新增第三套黑色胶囊，不改变后端 `[DONE]`、归档、扣次、replay 或模型输出口径。debug-only 设置预览面板补上 App 备案号 footer，今日农情预览文案同步成“空态不显示，安静历史后参考”，`check-android-build-parity.ps1` 已加门禁；本轮不修改三份提示词、官网首页文案、真实支付、模型输出硬限制或主聊天滚动主方案。

- 按用户“到底啥时间出现合适 / 用户刚发送完消息突然弹今日农情突兀”的最终口径，把主聊天今日农情从“每天可见就插入”收成“后端清晨生成，但只在不打断当前问诊的空闲窗口展示”：没有真实聊天消息时欢迎语仍是空态兜底且不会被今日农情压掉；只有安静打开且可见尾部是完整 AI 回答时，今日农情才跟在这条完成回答后方，失败 / 未完成 assistant 尾巴不算完整回答；今日农情一旦插入可见 timeline，Android 会写入本地已展示日期，同日关闭重开不再反复插入主聊天；如果用户先开始发送 / 生成且今日农情还没显示，本次运行抑制自动插入，不写已展示日期，避免刚发送完消息时突然弹出。当前运行时仍保持 `ChatTimelineItem.TodayAgriCard` 普通视觉文本项，不写入真实 `messages`、远端聊天历史、记忆、归档或扣次；设置页“今日农情”历史入口仍可查看最近记录。`ChatTimelineItemsTest` 新增“一天一次但本轮不消失 / 已开始问诊则本次不插入”单测，`check-android-build-parity.ps1` 新增 shown-day 和 suppress 门禁；本轮不修改三份提示词、不新增模型输出限制、不改变正向 `LazyColumn` 滚动主方案。

- 跑通并固化低成本 Android 下载链路：`download.nongjiqiancha.cn` 已 CNAME 到 OSS Bucket 并绑定 HTTPS，测试包发布脚本现在可用 `-UseOssSignedDownload` 生成自有下载域名签名链接，不再推荐走 ECS 5Mbps `/test-apks/` 路径；发布脚本会让 OSS `test-apks/debug/` 只保留最新内部测试包，并在走 OSS 签名下载时清掉 ECS 旧测试包镜像。新增 `check-android-download-domain.ps1`、`sign-oss-cname-url.py` 和 `sync-oss-download-certificate.ps1`，用于检查下载域名、生成 CNAME 签名 URL、以及 Let’s Encrypt 证书续期后同步 OSS CNAME 证书；本机也创建了每周续费 / 证书巡检自动化，只巡检和必要同步证书，不购买、不续费、不退订、不删除付费资源。正式发版仍等用户口令，且不能把 72 小时测试签名链接写进检查更新。

- 修复内部测试包发布脚本两处收尾问题：`publish-android-test-apk.ps1` 的 Git commit / clean tree 读取改为显式数组参数，避免 PowerShell 把 `status --porcelain` 当成单个 git 子命令；ECS 旧测试包镜像清理和备用 ECS 发布脚本改用 POSIX `sh` 兼容的 `set -eu`，避免阿里云 Cloud Assistant 默认 shell 不支持 `pipefail` 导致测试包已上传但收尾报失败。该修复只影响 debug/internal 测试包发布脚本稳定性，不改变正式发版口令、检查更新、官网正式下载、应用商店或 release APK 保留策略。

- 继续按代理复查发现的下载安全风险收口：测试包发布脚本现在裸跑默认走 `download.nongjiqiancha.cn + OSS` 签名下载，只有显式 `-UseEcsDownloadFallback` 才允许临时回退旧 ECS `/test-apks/` 路径；`-SkipEcsDownloadPublish` 保持纯 staging 语义，只上传 OSS 对象，不生成可发给用户的公网下载链接。`OssPrefix / 下载域名 / OSS endpoint / ECS 测试包根目录` 等运维参数加白名单，下载域名固定为 `download.nongjiqiancha.cn`，拒绝把 OSS 默认 endpoint 当用户下载域名。旧 OSS CNAME 证书同步脚本曾把下载域名私钥放进 Cloud Assistant 输出，已强制重签 `download.nongjiqiancha.cn` 免费证书并把同步脚本改成一次性 RSA 公钥 + AES 加密 payload 回传，Cloud Assistant 输出不再包含明文私钥；新证书已重新同步到 OSS，下载域名探测 ready。正式检查更新的后端 URL 校验和 `check-app-update-release-match.ps1` 也新增短签名参数拦截，拒绝带 `Expires / Signature / OSSAccessKeyId / security-token / x-oss-expires / x-oss-signature / x-oss-credential / x-oss-security-token` 等参数的 APK URL，避免把短期签名链接当正式 release 地址。

- 继续按主界面冷启动全链路复查修正两个边角：今日农情 `START` 锚点不再在重开 App 后迁移到最新真实消息，确保它仍像第一段普通文本一样在后续消息前方自然上移；远端 `/api/session/snapshot` 成功返回空历史时，会清掉本地已完成旧窗口，而不是因为远端为空就保留旧 UI。若清空后只剩当天今日农情视觉项，启动工作线相位会重新按 `TopUnreached` 文档流处理，不沿用旧历史的底部贴底标记。今日农情锚点保存新增 `remoteSnapshotHydrationComplete` 门，显示本地旧窗口可以先快，但保存锚点必须等远端快照成功完成；远端失败只做本地兜底展示、不落盘新锚点，避免锚到即将被替换的本地尾巴；启动日志也新增 `remote_snapshot_hydrated` 字段。`ChatTimelineItemsTest` 和 `check-android-build-parity.ps1` 已锁住 START 锚点、空远端快照替换、hydrate 后工作线相位重置和锚点等待远端快照；仍不修改主聊天正向列表、三份提示词、官网文案、真实支付或模型输出硬限制。

- 按用户“有历史拉历史、没有历史欢迎语兜底、今日农情放最后”的口径继续简化主界面空态：`shouldShowChatWelcomePlaceholder(...)` 不再因为今日农情存在而隐藏欢迎语；今日农情仍是同一个正向列表里的普通附加文本项，不当欢迎语、不当真实消息。单测覆盖“无真实消息 + 有今日农情”仍显示欢迎语，`check-android-build-parity.ps1` 也新增门禁，防止今日农情再次压掉开机欢迎语。本轮不修改今日农情提示词、主对话锚点、记忆文档提示词、滚动主方案或后端模型输出限制。

- 修复礼品卡后台生成确认链路漂移：服务端创建批次确认字段从只校验张数，收紧为“张数 + 档位 + 天数”，例如 `3 Pro 30`；后台前端 prompt 和 `check-admin-surface.mjs` 同步检查同一口径，单测覆盖错误天数、错误档位和空格归一化，降低管理层试用时误点真实 Plus / Pro 卡的风险。订单表“金额”列也改成“开发期金额”，避免支付未接入阶段被误读成真实收入。

- 修复卸载重装 / 清数据后首屏整屏空白的启动显示门：远端历史 snapshot 仍可在没有任何本地视觉内容时短暂等待，但等待期会显示普通欢迎壳，不再整屏空白；一旦 `messages`、今日农情视觉项或 streaming item 已进入同一个正向 `LazyColumn`，列表必须立即显示，启动贴底只作为后续校准继续运行，不再用 `initialBottomSnapDone` 把已有静态内容整屏透明隐藏。今日农情视觉拉取也不再等待聊天历史 hydrate 完成，先给清安装首屏一个可见内容；锚点保存仍等远端历史回来后按真实消息尾部或起始位置确定。用户手势“一扒就显示”的原因是旧链路会在拖动时把 `initialBottomSnapDone=true` 放开透明门；现在 `shouldRevealChatMessageList(...)`、`shouldShowChatWelcomePlaceholder(...)` 和 `ChatTimelineItemsTest` 已锁住“远端消息已存在就显示 / 等待远端时不空白”，`check-android-build-parity.ps1` 也禁止 `waitingForStaticTimelineBottomSnap` 回潮。本轮不改变正向列表、工作线、今日农情三轮上下文、三份提示词、官网首页文案、模型输出限制或真实支付。

- 补回普通 AI 回复的标题轻分割线触发：近期模型更多输出 `**处理建议**`、`**注意事项：**` 这类独立加粗标题，旧渲染器只识别 `# / ##` 标题，所以正常 AI 回复看起来没有分割线。现在 `ChatStreamingRenderer.kt` 同时识别 `# / ## / ###` 标题、独立短加粗标题和 streaming 中正在吐出的未闭合加粗标题；行内加粗正文和连续标题不触发多余分割线。debug-only 预览面板已加入独立加粗标题样例，单测和 `check-android-build-parity.ps1` 已锁住该口径。本轮不修改三份提示词、官网首页文案、真实支付、主聊天滚动主方案或模型输出硬限制。

- 补回今日农情普通文本里的轻分割线：当前主聊天今日农情仍是 `ChatTimelineItem.TodayAgriCard` 普通视觉文本项，不加黑框、不恢复卡片、不进入真实消息 / 历史 / 记忆 / 归档 / 扣次；只在“今日农情 · 日期”主标题下方加一条浅灰分割线，把标题和三条资讯轻轻隔开。`check-android-build-parity.ps1` 已新增门禁，锁住它必须保持普通 selectable 文本、标题加粗、轻分割线和“复制 / 全文复制”路径。本轮不修改三份提示词、官网首页文案、真实支付、主聊天滚动主方案或今日农情三轮临时上下文规则。

- 继续按代理 / 管理层试用前的普通用户视角收口 Android 可见细节：设置首页“服务协议”入口改为“协议与隐私”，让隐私政策、第三方信息共享清单、个人信息收集清单和应用权限入口更直观；账号管理页手机号行改为静态展示，不再带点击感和右箭头，避免被误判为失效换绑入口；会员中心规则说明补充“升级补偿和加油包未用完不随会员到期清零”。debug-only 预览面板同步更新设置入口、协议目录和会员规则说明；本轮不修改三份提示词、官网首页文案、真实支付、主聊天滚动主方案或今日农情三轮临时上下文规则。

- 历史过渡记录：当时修正内部测试包下载链路时，实测阿里云 OSS 默认公网 endpoint 会对 APK 返回 `ApkDownloadForbidden`，因此先让 ECS 通过 OSS 内网签名 URL 拉取并发布到 `https://nongjiqiancha.cn/test-apks/debug/...apk`，同时清理 ECS 和 OSS 旧测试包只留最新 1 个。2026-06-17 后该过渡方案已被 `download.nongjiqiancha.cn + OSS private object + signed URL` 主链替代，ECS `/test-apks/` 只作为临时回退，不挂官网正式下载按钮、不进入 App 检查更新、不替代正式 release 包。

- 更新 debug-only 预览面板的今日农情说明：预览项明确今日农情是主聊天普通文本项、标题加粗、正文可复制；上下文规则说明改为“远端当天确认后，后方连续三轮临时参考”。`check-android-build-parity.ps1` 已同步锁住这条新文案，防止预览面板和真实三轮临时上下文口径漂移。该改动只影响调试预览和质检门禁，不改变正式用户 UI、主聊天滚动链、三份提示词、后端模型输出限制或真实支付状态。

- 修复 GitHub Web CI 红灯：Vite 8 / Rolldown optional peer 依赖在 GitHub Linux `npm ci` 下比 Windows 本机更严格，先后报缺 `@emnapi/*`。当前已在 `admin/package.json`、`site/package.json` 显式声明 `@emnapi/core` 和 `@emnapi/runtime` 为开发期构建依赖，并刷新两份 lockfile，补齐 `@emnapi` 和 `@rolldown/binding-wasm32-wasi` 相关层级依赖；本机已分别在 `admin`、`site` 跑通 `npm ci` 与 `npm run build`。该改动只修前端构建依赖声明和锁文件，不改官网首页定稿文案、管理后台业务代码、Android、Go 后端、三份提示词、支付或主聊天滚动链。

- 历史记录：当时修复内部测试包 OSS 清理脚本兼容性；该旧清理脚本已在 2026-06-19 按用户最新口径删除，当前不再作为可运行工具。

- 继续按最后巡检发现的实风险收口：Android 今日农情去掉专用提前回底 effect，只在已经交给 `WorklineOwned` 的正常聊天流里做视觉尾部回底；首屏只有今日农情时继续走普通文本的 `TopUnreached -> TopAnchoring -> WorklineOwned` 安全交接，避免清数据后提前切 `Arrangement.Bottom` 造成下坠。修正 hydrated snapshot 恢复时旧命名参数导致的 Kotlin 编译失败；顶部“更早若干轮”提示和删除历史弹窗去掉“记忆承接”内部词。会员开发期 Plus 升 Pro 补偿计算统一用同一个 `now` 派生上海日期，避免极端跨午夜时今日剩余次数和剩余整天数不一致。`check_project_memory.py` 已把 `admin/`、`site/` 纳入 watched/current-status 覆盖；`check-app-update-release-match.ps1` 也会对 APK URL 做 URL 解码后再拦 `test-apks / debug / internal / staging`，和后端、官网的测试包护栏保持一致。本轮仍不修改主对话锚点、记忆提示词、今日农情提示词、官网首页文案、模型输出限制或真实支付。

- 按用户最新会员口径固化开发期权益规则：加油包和 Plus 升 Pro 生成的升级补偿次数都按永久有效处理，不随会员到期清零；每日额度仍是自然日额度，不跨天结转。Plus 升 Pro 不做“剩余 Plus 折成现金抵扣”的复杂折扣，用户按 Pro 开通价升级，后端把 Plus 剩余每日权益折成永久升级补偿次数；消耗顺序继续是每日额度 -> 升级补偿 -> 加油包。开发期订单 replay 已按账号和商品类型一起校验，同一 `order_id` 不能跨账号或跨 Plus / Pro / 升级 / 加油包复用，减少假测试把旧订单回放到错误权益上的风险。检查更新后台写入口也从前端确认加固到服务端确认：启用时必须提交当前 `versionCode`，停更时必须提交“停更”。真实支付仍未开放，正式下发新版本仍等用户口令。

- 历史记录：当时按“测试包只留最新、老的删除”新增过 OSS 测试包清理脚本并让发布脚本自动调用；该自动清理口径已在 2026-06-19 被用户改为“需要清理时再叫 Codex”，旧清理脚本和自动调用均已删除。`site/src/main.ts` 的 APK URL 校验仍会拦截 `test-apks / debug / internal / staging`，减少绕过部署脚本时测试包误挂官网的风险。本轮仍不生成正式 release 包、不配置检查更新、不改官网首页定稿文案。

- 继续按代理最后巡检收口 Android 可见细节：帮助与反馈历史消息图片条改为横向滚动，避免 320dp 窄屏下 4 张 70dp 缩略图挤出卡片；删除历史确认按钮从“确定”改为“确认删除”；会员中心未知权益状态下按钮不再写“同步后开通”，统一显示购买暂未开放；登录页配置类错误不再把“后端地址 / 短信模板配置”这类工程口径直出用户。聊天回归 runbook 和当前状态文档同步清掉今日农情旧 card-only top padding 口径。该轮不修改三份提示词、不接真实支付、不改变主聊天滚动主方案。

- 历史记录：当时补齐 GitHub Android CI 失败对应的项目记忆，原因是 APK 清理脚本和 Android parity 门禁提示未同步项目状态；该 APK 清理脚本已在 2026-06-19 删除。该失败不是 Android 编译、单测、打包或后端测试失败，也不涉及三份提示词、正式发版或线上服务变更。

- 按用户最新口径把今日农情滚动链收成普通视觉文本项：`TodayAgriCard` 仍只是 UI-only timeline item，不进入真实消息、远端历史、记忆、归档或扣次；但首屏布局、工作线触线判断、启动贴底和静态视觉尾部锚定不再保留旧“只有今日农情就跳过底部贴底 / 额外顶部 padding”的特殊分支。没有真实消息时，今日农情作为首条视觉内容从顶部自然向下排；内容底边到达 / 超过 96dp 工作线后，走同一套 `InitialWorklinePhase` 交接和视觉尾部底部锚定。`check-android-build-parity.ps1` 已改为禁止旧 card-only 顶部特例回潮，同时继续锁住正向列表、视觉尾部锚点和三轮临时上下文。本轮不修改主对话锚点、记忆提示词、今日农情提示词、官网首页文案、模型输出限制、真实支付或主聊天滚动主方案。

- 继续按正式上线口径收口后端和门禁：主聊天归档成功后先写 `quota_consume_outbox`，扣次成功标记 done，扣次临时失败时先短重试，仍失败则由后台 worker 按回答完成时的档位、自然日和完成时间补偿；replay 只恢复已归档答案，不按当前日期 / 当前会员档位补扣旧轮次，监控面板和总览会显示“待补扣”。模型输出方向仍只靠提示词控制，不新增 `max_tokens`、主题词过滤或内容硬限制；SSE 只加 256KiB 单行传输保护，防异常长行拖垮转发。历史轮次时间优先用请求开始时间，定位只在 GPS 来源时标可靠，IP / unknown 降级低可信。检查更新日常黑盒恢复要求 `/api/app/update` 返回 `has_update=false`，readiness 会拦误开的更新环境变量和强更开关；正式对外下发仍等用户口令。本地已跑 Go、Android、后台和官网构建门禁，并继续用子代理只读巡检辅助收口。

- 历史记录：当时按“先不打正式包、先给内部测试包下载链接，测试包存的时间短点，本地别留太多安装包”的要求，新增 debug/internal 测试包发布脚本和 runbook；测试包上传私有 OSS `test-apks/debug/...` 并生成限时签名链接，只用于人工下载验证，不进入 App 检查更新、官网正式下载、后台发布历史或应用商店。2026-06-19 起本机 APK 清理脚本已删除，需要清理本机产物时由用户明确提出后再人工处理。官网手机竖排问题同步按兼容性收口：新增 `site/vite.config.ts`，关闭官网 CSS 压缩，避免 Vite / Lightning CSS 把传统 `max-width` 媒体查询压成部分旧安卓浏览器不识别的 `width<=` 范围语法；官网首页文案不改。

- 继续收口 APP 全链路巡检里发现的低风险但关键细节：今日农情本地当天缓存可以先稳定展示，但 `today_agri_context_day` 只有在远端成功确认同一天 ready 内容后才会进入视觉项后的连续三轮用户发送；进入正常聊天流后，用户未主动浏览时最新视觉尾部仍按工作线贴底。这样避免“旧缓存看得到、模型却按今日上下文理解”的错位，也让今日农情作为普通文本视觉项随列表自然排版和回底。App 自动日志和后台审计日志脱敏继续加严：崩溃诊断复用敏感文本过滤，11 位以上整数型 attrs 默认丢弃，后台审计 User-Agent 走统一脱敏，避免手机号、token 或 URL 形态内容混进日志。本轮仍不修改主对话锚点、记忆提示词、今日农情提示词、官网首页文案、模型输出限制、真实支付或主聊天滚动主方案。

## 2026-06-16

- 按用户真机截图反馈继续收口主界面、今日农情和测试包边界：用户消息保持黑底白字气泡，AI 免责声明取消中等字重；streaming 吐字调到正常聊天节奏，小球最短展示约 1.8 秒、呼吸周期约 700ms、中文单字约 22ms，仍保持边生成边渲染和 DONE 后按打字节奏 drain。今日农情仍作为 `ChatTimelineItem.TodayAgriCard` 普通列表项插入正向 `LazyColumn`，视觉改为普通 AI 文本，标题加粗，序号使用 `一、二、三、` 中文标识，长按复用 AI 文本区同款“复制 / 全文复制”菜单；它不进入真实消息、远端聊天历史、记忆、归档或扣次，只在当天视觉项后方连续三轮发送里携带 `today_agri_context_day`，后端校验为服务器上海当天后临时注入当天农情作为系统背景，第四轮起自动不带。测试包新增独立 debug/internal OSS 临时链接发布脚本和 runbook，正式检查更新校验会拒绝测试包 URL；本轮仍不修改三份提示词、官网首页文案、模型输出限制、真实支付或主聊天滚动主方案，也不发布正式新版本。

- 用户明确拍板长期发版纪律：以后 Android 新版本发布必须等用户口令；用户没有明确说“发布新版本 / 打正式包 / 对外下发 / 配置检查更新 / 上传应用商店”时，不生成并对外发布正式 release APK，不配置公开下载地址，不启用检查更新下发，也不替用户发布应用商店版本。日常仍可继续做代码修改、测试、提交、推送和必要的后端 / 后台运维收口；正式包和用户可获取的新版本只在用户明确发版口令后推进。

- 按用户最新拍板继续简化礼品卡：不做“未来才生效 / 预约生效”，后台生成出来就是 active 可兑换卡，用户兑换成功即发放会员权益；后端兑换逻辑、礼品卡页汇总和监控面板统计都不再因为 `valid_from` 大于当前时间拦住 active 卡，“可兑换卡”只排除已过期 active 卡。后台礼品卡页同步把“已激活 / 激活账号ID / 已经生效”这类容易误解为单独激活动作的文案改成“权益已发放 / 兑换账号ID / 兑换成功立即发放”，监控面板快捷入口改为“礼品卡追溯”，检查更新页把“发布开关”和“是否会下发”分开，避免管理层试用时把开关状态误读成正式 APK 物料已齐；Plus 升 Pro 的升级补偿按同一个兑换业务时间计算，`check-admin-surface.mjs` 和礼品卡单测已锁住该口径。本轮不接真实支付、不修改三份提示词、官网首页文案、模型输出限制或主聊天滚动链。

- debug-only UI 预览面板同步补齐礼品卡最新口径：新增“礼品卡生效规则”预览项，明确生成即可兑换、兑换成功后会员权益立即发放、`valid_from` 只作创建追溯不作为预约生效门槛，失败原因会停留在礼品卡页内；“礼品卡成功样式”说明也改成兑换成功立即发放权益。`check-android-build-parity.ps1` 已新增防回退检查，避免以后改礼品卡真实规则却漏掉预览面板。本轮仍不打正式包、不修改三份提示词、不改变主聊天滚动链。

- 管理后台按管理层演示前误操作风险继续收口：检查更新启用时除普通确认外必须再输入本次 `versionCode`，停更必须输入“停更”；检查更新详情把“发布开关”和“是否会下发”分开，`versionCode / versionName / release notes` 改成“内部版本号 / 展示版本 / 更新说明”这类更容易理解的标签。礼品卡生成按钮改为“生成真实可兑换卡”，帮助反馈回复按钮改为“发送给用户（生产）”，监控面板快捷入口改为“礼品卡追溯 / 查看反馈队列 / 更新配置”，降低高权限账号演示时误点真实生产动作的概率；明天给管理层试后台仍建议优先用 `ops_readonly` 只读账号。

- 继续按“代理测试前往成熟 App 标准收口”的要求做低风险加固：AI 回复完成态含 Markdown 链接或裸 URL 时，仍保留真实 URL 注解可点，同时重新纳入 `SelectionContainer`，让长按复制 / 全复制不再因为有链接而消失；`check-android-build-parity.ps1` 已改为锁住“链接可点 + 文本可复制”。账号管理页把“注销账号”收成“申请注销账号”，确认标题 / 按钮改为提交申请口径，把“清理临时缓存”改成“清理临时缓存”，debug-only 预览面板同步更新。支付 readiness 门禁新增后台订单页只读文案、禁止订单写操作按钮和 `/admin-api/v1/orders` 只读路由检查；礼品卡新增 Plus 升 Pro 补偿和 Pro 兑 Plus 拒绝单测，继续保持“兑换成功立即生效”。后端 Dockerfile 同步到 `golang:1.26.4-alpine`，GitHub server CI 新增 `govulncheck`。官网隐私政策补“未开放或不可购买入口不会发起真实扣费”后，已通过 `scripts/deploy-ecs-site.ps1` 部署到 `nongjiqiancha.cn / www.nongjiqiancha.cn`，部署包 SHA-256 为 `7799752df9ce990c4c9a52b23cf15065908d46c1722f3b3df54340b32194624e`，部署脚本验证协议页、隐私页、备案和公安标识正常。本轮不修改三份提示词、官网首页文案、模型输出限制或主聊天滚动链，也不开放真实支付。

- 按用户拍板把礼品卡继续收成“简单立即生效”规则：后台生成卡码后即可兑换，首版不做预约生效 / 未来生效；用户兑换成功后权益立即生效。Android 礼品卡页失败原因现在会停留在页面内，同时保留短提示，避免用户错过无效码、已兑换、已作废、过期或频控提示；debug-only 预览面板新增“礼品卡失败提示”。后端新增单测锁住批次创建 `valid_from` 使用当前创建时间，runbook 同步该口径。本轮不接真实支付、不修改会员权益计算、不修改三份提示词、官网首页文案、模型输出限制或主聊天滚动链。

- 继续按“APP 全部往成熟 App 标准做 / 滚动渲染和首屏再细查 / 低成本安全别买高防”的要求收口：Android 主聊天仍保持边生成边渲染，只把节奏调到更正常，远端 waiting 小球最短展示从约 1.8 秒拉到约 2.3 秒，呼吸周期从 720ms 调到 780ms，中文单字、英文 token、标点和换行停顿整体放慢，DONE 后尾段继续按打字节奏排完；`check-android-build-parity.ps1` 已锁住这些节奏和主聊天正向 `LazyColumn` / 同帧底部锚定 / 今日农情普通列表项口径。后端管理后台读接口统一加 `adminDashboardTimeout` context，避免后台列表慢查询无限占连接；记忆文档摘要在原本进程内 running guard 外新增 Redis TTL 租约，扩多实例时同一用户不会重复烧摘要模型；公网黑盒补多个后台受保护接口未登录必须 401 / 403 的探针。安全策略仍是免费 / 低成本优先：安全组、Nginx / Go / Redis 限流、HTTPS、安全头、fail2ban、云安全中心免费版、SLS / 云监控和备份先跑稳，不买上万元高防；WAF / CDN / 高防等到真实攻击、静态下载流量或带宽问题出现再评估。本轮没有修改主对话锚点、记忆提示词、今日农情提示词、官网首页文案、模型输出过滤或主聊天滚动主方案；本机无在线 adb 设备，真机首屏 / 清数据 / 连续发送 / 弱网仍需新包回归。

- 提交 `c20f82e1` 已部署到生产 ECS 双端口 slot：远端运行 `go test ./...`、编译新二进制、切换 Nginx API 与后台 `/admin-api/` 上游后，当前 active upstream 为 `3001`，`nongji-server-3001 active/enabled`，旧 `3000` slot 处于排空 / 回滚窗口。部署后 `check-ecs-readiness.ps1` 显示 HTTPS healthz 200、后台未登录鉴权 401、`auth_strict=true / bailian=ok / sms=ok / redis=ok / upload_storage=oss`；公网黑盒 `warnings=0 / errors=0 / status=ready`。文档里“门禁”统一指这些构建、测试、readiness、黑盒、安全头、项目记忆等自动质检脚本 / 检查关卡，不是云上收费安全产品。

- 按“主界面所有功能 / 滚动渲染 / 代理测试前再深查”的要求继续收口：主聊天仍保持单个正向 `LazyColumn`、同帧 streaming 底部锚定和普通 AI 文本式今日农情，不恢复反向列表、overlay、raw delta、`scrollBy` 或 split streaming item。并行只读巡检后修了两处低风险实 bug：`stream_in_progress` 不再被排除在远端 snapshot 恢复之外，前台收到同一条消息 409 时会进入长窗口恢复；带图发送前台流和 WorkManager pending 现在使用同一个捕获的 `sessionGeneration`，降低清历史 / 清数据竞态下前后台代际不一致风险。`ImageUploader` 上传失败日志不再打印原始异常 message、堆栈或后端错误体，只保留状态码、错误码是否存在和异常类名，用户侧统一提示“图片上传失败，请稍后重试”。`check-android-build-parity.ps1` 已把 clean-state、409 恢复、显式 sessionGeneration、图片上传日志脱敏、主聊天滚动主链和今日农情普通列表项纳入门禁；后端单测锁住主对话、记忆文档、今日农情请求体都不设置 `max_tokens`。图片直接视觉上下文口径按用户视角统一为“发图本轮 + 下一轮追问”两轮。本轮不修改三份提示词、不新增后端模型输出硬限制、不接真实支付、不改变主聊天滚动主方案；本机无在线 adb 设备，真机仍需装新包验证清数据、连续发两条、弱网带图和今日农情主界面展示。

- `AGENTS.md` 补长期耐用的个性化协作规则：全局提示词 / 仓库规则只沉淀不易过期的工作方式，动态项目事实继续放项目记忆、ADR 和 runbook；明确用户默认不需要懂代码，Codex 要承担技术负责人收口职责；主对话锚点、记忆文档提示词、今日农情提示词、官网首页定稿文案、主聊天滚动主方案和模型输出只靠提示词控制等拍板项，后续窗口必须先讨论再改；代理建议必须由主窗口核验取舍。

- 按“文案写正式点、拉代理加宽检查面”的要求继续收口：已拉起 Android 主交互、后端业务安全、运维成本监控和文案合规四条只读巡检子代理；主窗口把后台监控页“处理顺序”区从口语化提示改为更正式的状态说明和处理入口说明。官网首页文案属于用户已定稿内容，本轮已撤回官网首页文案改动，只保留官网隐私政策里的“服务端运行日志”口径；App 内隐私政策和第三方能力清单明确未开放或不可购买入口不会向支付服务商提交支付信息或发起扣费，会员中心加油包未开放说明去掉“永久有效”硬承诺。后台订单、礼品卡、会员额度、监控页继续收掉“能不能继续上线 / 先别上架 / 程序崩了 / 会员盘子 / 这里不是假测试 / 老ID”等口语或开发口径；Android debug-only 预览面板里的礼品卡重复兑换说明改为“同一账号重复提交时提示权益已生效”。同步专项复查清除 App 数据后的 UI 回退链路：本机无在线 adb 设备，不能声称真机已彻底根治；代码层面已确认备份 / 迁移全排除、`sessionGeneration` 校验、snapshot / 今日农情 stale 回调、clear epoch 拦截和启动贴底日志齐全，并把 clean-state 关键护栏新增到 `check-android-build-parity.ps1`。用户同时明确拍板所有模型输出方向只通过提示词控制，后端不新增输出限制、词表过滤、结构硬解析、`max_tokens` 截断或模型输出硬上限；本轮未修改三份提示词、支付真实接入、官网首页定稿或主聊天滚动链。

- 按“明天给代理测，别让礼品卡和会员页误导用户”的角度继续收口：会员中心 Plus 用户看到 Pro 时按钮文案改为“支付升级暂未开放”，提示条明确“礼品卡仍可按规则开通或升级会员”；Android 礼品卡重复提交本人已兑换成功的卡时不再统一写“兑换成功”，后端响应新增 `replay=true`，App 显示“权益已生效 / 该礼品卡已兑换成功，会员权益已生效，无需重复兑换”，其他账号已兑或作废卡提示“这张礼品卡已被兑换或已作废”，卡码错误提示“礼品卡码不正确，请核对后重试”。debug-only 预览面板同步补“退出登录确认”和“礼品卡重复兑换”，并把隐藏成功提示从“未来支付成功样式 / 订购成功”收成“权益生效提示 / 权益已生效”，同步检查更新权限提示真实文案。后台礼品卡失败原因筛选占位补齐 `invalid_code`；开发期订单回放按全局 `order_id` 锁定，跨账号复用返回 `ORDER_ID_CONFLICT`，避免假测试把主键冲突误读成会员坏了。后端新增礼品卡 replay / 单行状态更新 / 订单冲突单测；后端和后台静态包已部署，当前 Nginx active upstream / 后台 upstream 均为 `3000`，readiness 与公网黑盒通过。本轮不接真实支付、不修改主对话锚点、记忆提示词、今日农情提示词或主聊天滚动链。

- 按用户“安卓组件、后端服务器组件、系统版本都要联网校准”的要求，继续做版本基线收口：Android 侧只升级官方元数据确认的低风险依赖，`core-ktx` 升到 `1.17.0`、`appcompat` 升到 `1.7.1`、Material 升到 `1.14.0`、Gson 升到 `2.14.0`、ExifInterface 升到 `1.4.2`，AndroidX test `ext:junit / runner / core` 升到 `1.3.0 / 1.7.0 / 1.7.0`；`ImageUploader` 改用 `JsonParser.parseString`，上传协议不变。`core-ktx 1.19.0` 实测要求 `compileSdk 37` 和 AGP 9.1+，因此未为追新强升；AGP 9、Kotlin 2.4、Compose BOM 2026.05、OkHttp 5 和 TypeScript 6 这类大版本 / 渲染敏感升级都暂缓到单独回归窗口。服务器系统继续保留 Ubuntu 22.04 LTS，不做生产单机原地大版本升级；24.04 LTS 作为后续新机迁移目标。同步把“每次 Android 用户可见 UI / 文案 / 状态改动都要检查 debug-only 预览面板”的规则写入根规则、Android 局部规则和 UI 文案 runbook。本轮不修改主对话锚点、记忆提示词、今日农情提示词、支付真实接入或主聊天滚动链。

- 继续按“被刷接口 / 真实 IP / 未来上负载均衡”的角度收口：线上 API / 后台 Nginx 已从 `proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for` 改为 `proxy_set_header X-Forwarded-For $remote_addr`，并继续覆盖 `X-Real-IP $remote_addr`，避免当前单 ECS 直连公网阶段把客户端伪造的 XFF 链传给 Go；`deploy-ecs-admin.ps1` 模板和 `harden-ecs-security.ps1` 同步该口径，`check-ecs-readiness.ps1` 新增 API / 后台代理头门禁，发现 `$proxy_add_x_forwarded_for` 回潮会直接失败。公网伪造 `X-Real-IP: 1.2.3.4` / `X-Forwarded-For: 5.6.7.8` 访问不存在路径后，Go 日志里的 `masked_ip` 未采用伪造 IP。后续若接 SLB / WAF / CDN，必须重新显式配置 Nginx `real_ip_header` / `set_real_ip_from`，不能直接沿用当前单机直连假设。

- 继续按“预防被打、低成本防护别漂”的角度补公网黑盒门禁：`check-public-blackbox.ps1` 新增安全响应头检查，API / 官网 / www / 后台都校验 HSTS、`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY` 和 Referrer Policy；官网 / www / 后台额外校验 CSP 与 Permissions Policy，后台 CSP 还校验 `connect-src 'self'` 和 `form-action 'self'`。当前公网实测通过，仍保留 HTTP 跳 HTTPS、后台未登录 401、官网备案 marker、后台静态资源等原有检查。该改动只增强只读门禁和安全 runbook，不改 Android、后端业务逻辑、三份提示词、支付真实接入或主聊天滚动链。

- 继续按“服务器安全、预防被打”的口径做供应链复查：`npm audit --audit-level=high` 发现 `admin` 和 `site` 的 Vite 6.4.3 通过 esbuild 命中高危构建链告警；该风险主要影响本机 / CI 构建链，不是用户访问线上页面时直接触发的运行时漏洞，但正式上线前不应保留。已将两个前端的 Vite 升到 `8.0.16` 并更新 `package-lock.json`，本机 Node `v24.12.0` 满足新版本要求；`admin npm run build`、`site npm run build`、两边 `npm audit --audit-level=high` 均通过且高危 audit 为 0。该改动只动管理后台 / 官网构建依赖，不修改 Android、Go 后端业务逻辑、三份提示词、支付真实接入或主聊天滚动链。

- 按“服务器好升级、预防被打”的口径继续做安全复查：线上安全组仍只放 `80 / 443 / ICMP`，ECS 本机 `ssh` inactive / disabled，Go 只监听本机回环端口，Nginx / 公网黑盒 / readiness 均通过；代码安全扫查未发现 pprof、默认裸 `ListenAndServe`、`InsecureSkipVerify`、前端 `innerHTML/eval/postMessage` 等高危模式。`govulncheck` 发现本机 Go 1.26.2 标准库和旧 `golang.org/x/net` 有已修复漏洞命中调用链，已将 `server-go/go.mod` 钉到 `toolchain go1.26.4`，并将 `golang.org/x/net` 升到 `v0.53.0`；复查 `govulncheck ./...` 显示当前代码实际调用链 0 漏洞。该提交已部署到 ECS，远端部署过程下载 Go 1.26.4、运行 `go test ./...` 并重新编译后切到 active upstream `3001`；上线后 readiness、公网黑盒和服务器性能快检均通过，Go / Nginx 24 小时 5xx / 429 仍为 0。当前仍不建议直接购买 WAF / 高防，先保留免费 / 低成本防护，等持续 Web 攻击、CC、带宽打满或 DDoS 黑洞等信号出现再升级。

- 按“明天给代理测试、往正式上线标准推进”的口径继续收口 Android / 后端 / 文案：今日农情主聊天展示从带边框资讯卡改成普通 AI 风格文本块，底层仍是 `ChatTimelineItem.TodayAgriCard` 的 UI-only 视觉项，不进入真实 `messages`、远端聊天历史、记忆文档或问诊扣次，也不恢复 overlay / sticky / 关闭动画；`check-android-build-parity.ps1` 和单测同步锁住该口径。`ChatStreamingRenderer` 补充 DONE 收尾兜底，纯 `**` / `*` / 反引号等不可见结构符号不会让本地 reveal buffer 卡住；active streaming 阶段链接只按普通文字显示，完成态链接仍可点击。帮助与反馈消息含 URL 时不再被 `SelectionContainer` 吞成纯文本，打开失败会提示“链接打开失败，请复制后打开”并只上报 scheme / 异常类名。礼品卡同一账号重复提交自己已兑换成功的同一卡码会按幂等成功返回既有结果，避免 App 丢失首次成功响应后误报失败；其他账号重复兑换仍失败。Android 内置服务协议 / 隐私政策、官网协议 / 隐私页和法律 runbook 同步收成正式上线口径：微信 / 支付宝只作为页面支持的官方渠道，未开放入口不发起真实扣费；账号注销是申请核验流程，按 15 个工作日内处理展示；App 自动日志按 30 天低成本排障窗口控制，服务端写入后限频清理超窗记录，巡检阈值同步 30 天；服务器安全 / 性能 / 扩容准备完成一轮复查，新增只读性能快检 `check-server-performance.ps1`，当前不升配、不买 WAF / 高防，扩容路线和安全升级触发线已固化到 runbook；官网清掉旧融合认证 / 一键登录 / 电话状态权限表述。本轮没有修改主对话锚点、记忆提示词、今日农情生成提示词或后端模型输出过滤。

## 2026-06-15

- 继续巡检帮助与反馈外壳的极端关闭场景：带图反馈正在发送或刚选图时，如果用户按返回、切页或关闭设置页导致页面销毁，当前选中的反馈临时图会统一清理，不再因为 `sending=true` 跳过清理而可能积在 App 私有 `composer_images` 目录。`check-android-build-parity.ps1` 已锁住帮助反馈销毁清理不能跳过发送中状态。该改动只处理帮助与反馈临时文件，不把反馈发送升级成主聊天带图 WorkManager 后台兜底，也不修改主聊天滚动链、今日农情、三份提示词或支付真实接入。

- 继续按“前端外壳极端交互”巡检 Android 设置壳层：设置页首页现在也注册系统返回键，走同一个 `handleBackClick()`，因此在设置首页按系统返回会关闭设置页，二级页仍先返回设置首页；`check-android-build-parity.ps1` 已把这一点纳入门禁，避免以后只保留二级页返回而首页返回无反馈。本轮只改设置外壳返回键和门禁，不修改主聊天滚动主链、今日农情插入链、三份提示词、支付真实接入或模型输出过滤。

- 继续按“极端网络 / 极端交互先补低风险护栏”的角度收口 Android 主聊天：联网校准 Android 官方网络能力口径后，发送前网络预检不再把 captive portal 门户 Wi-Fi 当作可用网络，但也不把 `NET_CAPABILITY_VALIDATED` 设为硬门槛，避免部分 ROM / 运营商网络误拦；AI 回复链接打开失败时会给用户短提示“链接打开失败，请复制后打开”，并只上报 `ui.link_open_failed` 的 scheme 和异常类名，不记录完整 URL、正文或敏感信息。`check-android-build-parity.ps1` 已锁住这两条护栏。本轮不修改主对话锚点、记忆提示词、今日农情提示词、模型输出过滤或主聊天滚动主链；本机未连接真机，飞行模式、弱网、门户 Wi-Fi、ROM 链接拦截和连续点击仍需新包真机回归。

- 继续按“清数据 / 慢网 / 重置竞态不能把主界面卡死”的角度收口动态交互：Android `SessionApi.getSnapshot()` 和 `getTodayAgriCard()` 在运行时 generation 变旧时不再静默 `return`，而是回调空结果，让 `ChatScreen` 的 hydrate / 今日农情协程继续走既有兜底，降低清历史、清数据、远端请求刚好返回时 UI 回退、今日农情消失或加载状态悬挂的风险；`check-android-build-parity.ps1` 已锁住这些 stale callback 不能回退。管理后台同步收高风险运营动作：帮助反馈状态从“已回复”改成“已处理/无需回复”，前端要求处理备注，后端在最新用户消息尚无后台回复时拒绝无备注标记；礼品卡生成增加真实权益提醒，确认字段后续已收紧为“张数 + 档位 + 天数”；检查更新启用确认展示 SHA-256、文件大小并提示先跑 release-match 校验；停更空配置不再显示为配置异常；`check-admin-surface.mjs` 锁住这些确认项。短信统计脚本原始 JSON 输出改为脱敏输出。后端和后台已部署到生产，`check-ecs-readiness.ps1` 显示 active upstream `3000`、后台 upstream 同为 `3000`、HTTPS healthz 200，后台静态包 `SHA256=b516a5bbb61fa2b63812816256cb8f72971f215966ca967ac78c7203ad4b364b`，公网黑盒 `status=ready`。本轮不修改主对话锚点、记忆提示词、今日农情提示词、模型输出过滤或主聊天滚动主链。

- 按用户确认“融合认证新包不用、已买套餐大概率退不了”的口径收敛成本巡检和监控表达：阿里云 CLI 只读查到两个 DYPNS / 融合认证套餐均为 `ManualRenewal`，不是自动续费；2026-05-31 包实付 `0` 元，2026-06-06 包实付约 `34.85` 元；安全退订询价 `InquiryPriceRefundInstance` 对两个包均返回 `CommodityNotSupported`，因此不走 CLI 退订。`check-aliyun-costs.ps1` 不再把已购融合包本身当 warning，只在自动续费或新增购买迹象出现时提醒；上线总门禁和监控后台“费用 / 套餐成本”人工项改为“已购沉没成本、确认不再使用、不自动续费、不新增购买”。本轮只改费用巡检、后台文案和项目记忆，不修改 Android、三份提示词、支付真实接入、模型输出过滤或主聊天滚动链。

- 继续按“新窗口第一眼不能被旧 slot 数字带偏”的角度校正项目记忆：实时 `check-ecs-readiness.ps1` 复查显示 Nginx active upstream 为 `3001`，后台 `/admin-api/` upstream 同为 `3001`，`nongji-server-3001 active/enabled`，`nongji-server-3000 active/disabled` 仅作为排空 / 回滚窗口保留；根 `AGENTS.md` 和 ECS 发版 runbook 已同步该口径，并明确公网流量以 Nginx active upstream 为准。本轮只是文档 / 记忆漂移修正，不重新部署后端、不修改 Android、三份提示词、支付真实接入、模型输出过滤或主聊天滚动链。

- 继续按“图片短留存要省钱，也要能证明规则没漂”的角度收紧 OSS 生命周期巡检：`check-resource-capacity.ps1` 现在会解析阿里云 OSS 生命周期 XML，逐条确认 `uploads/` 问诊图规则为启用且 3 天过期、`support/` 帮助反馈图规则为启用且 30 天过期，并要求两条规则都配置 1 天未完成分片清理；输出会显示 `lifecycle prefix=... status=... expiration_days=... abort_multipart_days=...`，避免旧文本包含式检查把不同规则拼成假绿。同步更新 OSS / 资源容量 runbook 和项目记忆，明确图片删除前仍可能产生少量存储、请求、生命周期处理和下载流量成本，但当前 100GB 存储包、压图和短生命周期足够早期使用。本轮不修改 Android、后端业务接口、三份提示词、模型输出过滤、支付真实接入或主聊天滚动链。

- 继续从“后台监控面板也要看到账单 / 套餐确认”的角度补上线人工项：`server-go` 的 `/admin-api/v1/monitoring` 现在会在 `launch_readiness` 里返回“费用 / 套餐成本”人工确认项，提示通过 `check-aliyun-costs.ps1` 或上线总门禁确认账户余额、DYPNS / 融合认证套餐处置、短信套餐余量、qwen-plus 资源包和百炼节省计划；前端已有人工确认区和正式上架检查会展示该项。后台不实时读取阿里云费用中心，不持有阿里云密钥，也不保存账单敏感截图、AccessKey 或密钥。后端单测和 `check-admin-surface.mjs` 已锁住该项。该后端改动已通过 `scripts/deploy-ecs-server.ps1` 部署到生产，当前 Nginx active upstream 与后台 `/admin-api/` upstream 同为 `3001`，公网黑盒 `status=ready`。本轮不修改 Android、三份提示词、模型输出过滤、支付真实接入或聊天滚动主链。

- 继续按“总门禁别漏账单和套餐成本”的口径把费用中心总账巡检接进 [check-launch-readiness.ps1](D:/wuhao/scripts/check-launch-readiness.ps1)：云资源段现在会调用 `check-aliyun-costs.ps1`，并把非 ready 结果显示为独立的 `aliyun costs` attention，用于提醒 DYPNS / 融合认证套餐仍存在、短信套餐包余额仍需控制台确认、模型资源包 / 节省计划或当月账单需要关注。这是经营成本提醒，不代表 ECS、后端、Android 或监控服务不可用；日常看报告可继续用 `-AllowAttentionExitZero`，正式 `-ReleaseGate` 仍要求 attention 被人工处理或确认。本轮只改只读门禁和项目记忆，不修改 Android、后端业务逻辑、三份提示词、模型输出过滤、支付真实接入或聊天滚动主链。

- 按“所有成本都要能查、能解释、能控住”的口径补费用中心总账巡检：新增 `scripts/check-aliyun-costs.ps1`，通过阿里云 BSS OpenAPI 只读查询账户余额、当月 / 上月产品账单、当前月明细、百炼每日走势、有效资源包和有效实例，带费用中心偶发超时重试和请求签名 / 账号字段脱敏，不买资源、不续费、不释放实例。当前实测账户可用余额约 `628.35` 元；2026-06 当月税前账单约 `130.1225` 元，主要是百炼约 `60.2716` 元、短信套餐包 `35` 元、DYPNS / 融合认证套餐 `34.85` 元，SLS 约 `0.0009` 元；百炼最近 5 天税前均值约 `0.0714` 元 / 天，qwen-plus 推理资源包仍剩约 `11.49M / 12M tokens`。脚本会把 DYPNS 订阅仍存在、短信套餐包未被资源包 API 暴露、百炼节省计划临近到期等输出为 `status=attention`；这是经营成本提醒，不代表服务故障。同步更新资源容量 runbook、当前状态、风险和项目记忆护栏。本轮不修改 Android、后端业务逻辑、三份提示词、模型输出过滤、支付真实接入或聊天滚动主链。

- 按“30 天聊天记录不算长，但要有工程护栏”的口径补数据留存和成本巡检：新增 `scripts/check-data-retention-cost.ps1`，通过 Cloud Assistant 在 ECS 内部只读统计 `session_round_archive / client_app_logs / support_messages / admin_audit_logs / session_round_ledger / quota_ledger / orders / gift_card_redemption_attempts / daily_agri_cards` 的行数、最早 / 最新时间和表体量，不输出正文、图片 URL、手机号、token 或密钥；`check-resource-capacity.ps1 -Strict` 已接入该脚本，`check_project_memory.py` 也把它纳入项目记忆护栏。本轮生产实测重点表合计约 `0.828MB`，`status=ready`。主聊天完整归档仍按 30 天滚动保留，不是第 30 天秒级删除；用户主动“删除历史对话”会立即清问诊历史、A 层和记忆文档。同步更新合规、数据边界、资源容量、SLS 和 App 日志 runbook，以及当前状态 / 风险文档。本轮不修改主对话锚点、记忆提示词、今日农情提示词、模型过滤或聊天滚动主链。

- 继续按“假测试支付可以点、但不能让门禁假绿”的边界收口：会员中心里的灰色“暂未开放 / 同步后开通 / 用完再续”按钮现在可点击出“不扣费”提示并记录 `payment.unavailable_clicked`，方便测试未开放购买入口的反馈和日志链路；“当前套餐 / 当前为 Pro / 剩余次数可用”仍不可点。`check-launch-readiness.ps1` 会把短信套餐包状态非 confirmed 计入 `sms usage and balance` attention，把正式支付未配置但购买入口关闭显示为 `payment closed guard` attention，不再把它们误写成全绿 ready；公网黑盒新增后台首页 DOM marker 和首个 JS 资产检查，短信单手机号明细输出也会脱敏 `PhoneNumber / PhoneNum`。监控面板首屏在服务健康但仍有程序 / 人工上线 attention 时会直接说明待处理数量。同步记录 SLS 成本口径：当前只按 7 天、少采集、邮件告警和 App 日志限流脱敏使用，用户量上来前还要补日志量 / 账单阈值或采样护栏。本轮不修改主对话锚点、记忆提示词、今日农情提示词、模型过滤或聊天滚动主链。

- 按用户要求继续把日志服务压成低成本形态：新增 `scripts/check-sls-cost-guard.ps1`，只读检查农技千查业务 SLS Project 当前只允许 `server-go / nginx-error` 两个 Logstore，TTL 不超过 7 天，每个 Logstore 不超过 1 shard，且未开启自动分裂、append meta 或归档存储；`check-resource-capacity.ps1 -Strict` 会调用它，`check_project_memory.py` 也把它纳入项目记忆护栏。本轮实测 `status=ready`，当前两条 Logstore 都是 7 天、1 shard、低成本配置。后续如果要增加 Logstore、采完整 Nginx access、延长保留期或扩 shard，必须先确认费用影响并更新 runbook / 项目记忆。

- 继续按“支付只能先做假测试，不做真实扣费”的边界收口：Android 主界面会员入口和设置页会员入口点击未开放购买时，会记录自有 App 日志 `payment.unavailable_clicked`，attrs 只带入口 `source`，用于观察需求和证明当前没有进入真实扣费链；`check-payment-readiness.ps1` 同步锁住这两个埋点、购买入口关闭、Android 不调用开发期订单接口、生产 `dev_order_endpoints=false`、后端 `PAYMENT_NOT_CONFIGURED` 防线以及支付宝 / 微信联调材料缺口。同步更新支付 runbook、App 日志 runbook、当前状态和 R12 风险；这仍只是安全占位 / 假测试，不代表支付宝沙箱或微信 App 支付真实下单、验签、回调已经完成。

- 继续按“检查更新能不能在旧包真机上顺利拉起系统安装页”的角度收口自有 APK 更新链路：`AppUpdateInstaller` 现在优先使用系统包安装器 action，失败再回退通用 APK `ACTION_VIEW`；安装包仍只来自 App 自己的 `cacheDir/app_updates`，并通过 `${applicationId}.fileprovider` 临时授予读取权限，不暴露裸文件路径。`scripts/check-android-build-parity.ps1` 同步锁住 `REQUEST_INSTALL_PACKAGES`、FileProvider `app_updates` 路径、未知来源授权返回后继续同一更新、防重复检查 / 下载、双 intent 安装兜底，以及 SHA / 文件大小 / 包名 / versionCode fail closed。后端、后台写入口、Android 下载器和发版脚本统一 200MB APK 上限；`check-app-update-release-match.ps1 -VerifyDownload` 会确认最终下载 URL 仍是 HTTPS，`check-launch-readiness.ps1 -AppUpdateReleaseGate -AppUpdatePreviousVersionCode <旧包版本号>` 会强制后台物料对账、下载回验和公网旧包版本探针。同步更新检查更新 runbook、当前状态和 R15 风险；这只是减少代码回潮和 ROM 兼容风险，旧包真机覆盖安装仍需正式物料验证。本轮不修改主聊天滚动链、三份提示词、模型输出过滤或支付真实接入。

- 继续按“主界面所有深度交互检查”的角度收口 Android 主链护栏：静态巡检未发现 `reverseLayout / asReversed / dispatchRawDelta / scrollBy / SparseBottomSpacer / BottomActiveZone / split streaming item` 等旧滚动渲染方案残留；`scripts/check-android-build-parity.ps1` 现在会锁住主聊天仍是单个可滚动正向 `LazyColumn`、streaming 底部锚定仍在同帧 `SideEffect`、今日农情仍是普通 `ChatTimelineItem.TodayAgriCard`、含链接 AI 回复不会被 `SelectionContainer` 吞成纯文本。新增 `ChatComposerPanelTest` 覆盖空输入、纯图片、streaming 中禁发、额度耗尽和超长输入的发送按钮状态，GitHub Android CI 新增 `:app:testDebugUnitTest`。支付 readiness 脚本同步输出支付宝沙箱和微信 App 支付前置配置缺口，不打印密钥值；当前本机仍缺两家调试所需 AppID / 商户号 / 私钥 / 公钥等材料。本轮没有修改主对话锚点、记忆提示词、今日农情提示词、后端模型过滤或聊天滚动主链；本机未连接真机，真实设备交互仍需下一次装包回归。

- 继续从“总负责人看后台不只是知道有事，还要知道怎么确认”的角度补监控面板：`launch_readiness` 新增可选 `confirm_hint` 字段，App 公安备案、AccessKey 轮换、最终真机回归、短信套餐余额、最终 release 物料和 SLS 首封告警邮件等人工项会在“上线人工确认项”和“正式上架检查”卡片里显示“确认方式”。前端只展示后端给出的提示，不新增写操作；`scripts/check-admin-surface.mjs` 和后端单测会锁住人工项必须有确认方式，避免以后只剩“待确认”但不知道去哪确认。本轮不改 Android、聊天滚动、三份提示词、支付真实接入或云资源。

- App 备案已按通过状态收口：Android 设置页底部新增低调小灰字 App 备案号 `京ICP备2026031728号-2A` 和工信部备案查询链接，服务协议 / 隐私政策基础信息同步展示该编号；后台 `launch_readiness` 将“App 备案”改为 ready，正式上线人工确认项不再列 App 备案，只保留 App 公安备案等未闭环事项。同步修正 ECS、官网、管理后台、上线计划、合规、支付材料和项目记忆文档里的旧口径。另按用户反馈“AI 发的网址点不动”收紧 Android AI 回复链接渲染：Markdown 链接和裸 URL 都保留真实 URL 注解、显示为蓝色下划线并走系统打开；含链接的 AI 回复优先保证短按可点。本轮不修改主对话锚点、记忆提示词、今日农情提示词、后端模型过滤或聊天滚动主链。

- 继续按“支付先调试但不假扣费”的边界补上线门禁：新增 [check-payment-readiness.ps1](D:/wuhao/scripts/check-payment-readiness.ps1)，检查 Android 会员 / 加油包购买入口仍关闭、Android 未调用开发期订单接口、后端开发期订单接口仍有 `PAYMENT_NOT_CONFIGURED` 防线、公网 `/healthz` 显示 `dev_order_endpoints=false`，并输出微信 / 支付宝回调 URL 建议；[check-launch-readiness.ps1](D:/wuhao/scripts/check-launch-readiness.ps1) 已接入该只读步骤。支付 runbook 同步补充微信产品开放状态需以商户后台实际可开通项为准、支付宝未上线 / 未开通阶段可走沙箱联调但生产仍以正式审核和配置为准。App 公安备案也按阿里云提示和官方文档补充为“全国互联网安全管理服务平台单独提交，按开通后 30 日内处理”。本轮不接真实支付 SDK、不保存支付密钥、不打开真实扣费入口。

- 复核用户反馈“设置里能看到今日农情，主界面文本区没有”后，生产日志显示今日农情当天生成成功，主界面 `/api/today-agri-card` 和设置页历史 `/api/today-agri-cards` 均返回 200，未见 `today_agri.fetch_failed`；当前手机仍是 2026-06-14 21:31 左右安装的旧 debug 包，晚于该包的 `29284b01 Restore today agri card after remote hydrate` 已修复远端历史 hydrate 后主聊天今日农情卡不恢复的问题。Android 主聊天新增 `today_agri.main_card_loaded` 和 `today_agri.main_card_visible` 两个只记录日期键、条数、列表数量、揭示状态和锚点状态的安全诊断事件，便于下一次真机装新包后区分“接口已拉到但未插入列表”还是“已插入但视觉不可见”；不记录今日农情标题 / 摘要、聊天正文、手机号、图片 URL 或 token，且不改变今日农情仍作为普通聊天列表项的交互口径。

- 继续按“短信门禁不能把发送统计当余额证明”的角度补只读巡检：`scripts/check-sms-usage.ps1` 除了阿里云 `QuerySendStatistics` 发送趋势，还会调用费用中心 `QueryResourcePackageInstances` 查询当前有效资源包，并用商品码、包类型、备注和适用产品判断是否出现短信类套餐包；当前实测只返回百炼推理资源包和 OSS 存储包，没有返回短信类套餐包，因此脚本输出 `sms_package_status=not_visible_manual_required`，提醒仍需去短信服务控制台确认普通短信套餐包余额、到期、余量预警和自动复购。该改动只增强只读门禁证据和 runbook，不改短信发送、后端鉴权、Android 运行逻辑、三份提示词、模型过滤或聊天滚动主链。

- 继续从“正式发 APK 时最容易人工抄错”的角度补检查更新发布护栏：新增只读脚本 `scripts/check-app-update-release-match.ps1`，先复用最终 `app-release.apk` 物料校验，再登录后台读取 `/admin-api/v1/app-update/android`，核对后台当前 `versionCode / versionName / SHA-256 / 文件大小` 与本地最终 APK 是否一致，并确认 APK URL 是 HTTPS；需要时可加 `-VerifyDownload` 下载后台链接重新计算大小和 SHA-256。`check-launch-readiness.ps1` 新增可选 `-CheckAppUpdateReleaseMatch` / `-VerifyAppUpdateDownload`，只在正式包已上传并准备走自有“检查更新”分发时启用，不把日常巡检或首版暂未启用更新误报为程序故障。该改动只增强只读发版对账和 runbook，不改 Android 运行逻辑、后端接口、三份提示词、模型过滤或聊天滚动主链。

- 已将管理后台前端提交 `c7f69281` 部署到生产 `https://admin.nongjiqiancha.cn/`：`scripts/deploy-ecs-admin.ps1` 本地 `npm run build` 通过，上传静态包 `SHA256=4d6eba82c39e3e324c7048414335bc7c67fc1fb6c8684ba2bfcb9877dbf9b921`，远端安装后 Nginx 配置检测通过，证书未到期无需续签；脚本验证 `admin-http-redirect=301`、`admin-https-root=200`、`admin-https-auth-me=401`。部署后 `check-public-blackbox.ps1`、`check-ecs-readiness.ps1` 和 `check-admin-surface.mjs` 均通过，当前 API 与后台 `/admin-api/` active upstream 同为 `3000`，生产健康标记 `auth_strict=true / bailian=ok / sms=ok / redis=ok / upload_storage=oss` 正常。该部署只上线后台监控页“程序需处理项”和“程序处理 / 人工确认”展示标签，不改 Android 包、后端接口、三份提示词、模型过滤或聊天滚动主链。

- 继续按“监控页要让总负责人直接知道谁能处理”的方向补首屏可行动性：管理后台监控页在“处理顺序”和“上线人工确认项”之间新增“程序需处理项”，只展示 `launch_readiness` 里 `manual!=true` 且未 ready 的条目，并优先把红色阻塞排在前面；每项保留负责人、状态和直达入口，文案明确这些通常能通过代码、配置、部署或后台操作推进。正式上架检查明细卡也新增“程序处理 / 人工确认”小标签，避免人工确认项在明细区仍被“需处理”误读成程序故障。`scripts/check-admin-surface.mjs` 同步锁住该区域、人工项不混进程序项，以及明细卡分类标签。该改动只影响后台前端展示和只读 surface 巡检，不改后端接口、Android 运行逻辑、三份提示词、模型过滤或聊天滚动主链。

- 继续按用户视频里“流式渲染不像成熟产品”的体感补 Android renderer 边界：`ChatStreamingRenderer.kt` 不换引擎、不等全文完成、不改正向 `LazyColumn`，只收紧 typewriter 和 inline Markdown。长英文 / 数字词块单次 reveal 限制到 8 个字符，省略号 / 破折号按强停顿处理；emoji cluster 扩到肤色修饰、ZWJ 组合和旗帜，避免半个符号闪一下；已换行 / 空行分隔出去的旧块如果仍含未闭合加粗、斜体或行内代码，会继续按 streaming inline 规则显示，减少 raw `**` 露出和加粗处宽度突变。同步补 `ChatStreamingRendererTest` 和聊天 UI 回归文档；本轮不改三份提示词、不加后端过滤、不恢复 overlay / 反向列表 / raw delta / scrollBy / 小分割 item。

- 继续按“监控页要像上线清单而不是工程表”的方向拆分顶部判断：管理后台监控页的上线摘要不再把所有非 ready 都合成一个“需处理”，而是拆成“程序需处理 / 人工确认 / 上架阻塞”三类，处理顺序和“当前结论”卡也同步用这套口径。备案、AccessKey 轮换、短信余额、SLS 首封邮件、最终真机回归这类人工项不会再和登录、模型、检查更新等程序状态混成同一个数字；`check-admin-surface.mjs` 会锁住这些关键词，避免后续前端改版又退回混合表达。该改动只影响后台前端展示和只读 surface 巡检，不改后端接口、Android 运行逻辑、三份提示词、模型过滤或聊天滚动主链。

- 继续按“后台排障不要靠记忆猜按钮”的方向补管理后台监控面板：登录排障卡新增“登录成功”直达 App 日志筛选，和已有登录前日志、请求网络失败、短信发送失败、短信登录失败、登录闪退、普通闪退、旧包融合记录一起覆盖短信登录真机回归的主要节点；`scripts/check-admin-surface.mjs` 也新增关键登录 / 检查更新排障筛选按钮契约检查，避免后续改页面时把这些入口删漏而 CI 不知道。该改动只影响后台前端和只读 surface 巡检，不改 Android 运行逻辑、后端业务接口、三份提示词、模型过滤或聊天滚动主链。

- 继续按“总负责人扫一眼不能被假绿误导”的方向补上线门禁和监控首屏：`check-launch-readiness.ps1` 现在会捕获 `check-sms-usage.ps1` 输出的 `sms_usage_status=attention`，把短信统计为空这类趋势风险计入总门禁 attention，而不是只按脚本退出码显示 ready；监控面板首屏如果正式上架检查仍有 `blocked` 项，即使运行健康项正常，也会显示阻塞色和“上架阻塞”状态，避免标题写着“上架仍有阻塞”但视觉上像完全正常。该改动只影响只读门禁输出和后台监控展示，不修改云资源、短信发送、Android 运行逻辑、后端业务接口、三份提示词或聊天滚动主链。

- 继续从“流式渲染各种符号不能显得廉价”的角度补 Android 主聊天 typewriter 边界：`ChatStreamingRenderer.kt` 的吐字 token 现在按 Unicode code point 和简单组合簇处理扩展汉字、emoji、变体选择符、组合 mark 和 ZWJ 组合 emoji，避免把 UTF-16 代理对或一个可见 emoji 拆成半个半个显示，减少流式过程中出现方块 / 半符号闪一下的风险。同步补 `ChatStreamingRendererTest` 覆盖扩展汉字、普通 emoji 和 ZWJ 组合 emoji；该改动不换渲染引擎、不改成整段完成后再显示、不恢复 overlay / 反向列表 / raw delta / scrollBy / 小分割 item，不改三份提示词、今日农情列表项身份或后端输出过滤。

- 继续从“正式包物料能不能被证明”角度补上线护栏：新增只读脚本 `scripts/check-android-release-artifact.ps1`，直接读取最终 `app-release.apk`，用 Android SDK `aapt` 校验包名、`versionCode`、`versionName`、release 不可调试和权限白名单，用 `apksigner verify --print-certs` 校验证书 SHA-256 与本机备案 / 上架公钥信息一致，并输出 `apk_size_bytes`、`apk_sha256` 供后台检查更新页填写。`build_apk.bat` 打包后会自动跑该校验；`check-launch-readiness.ps1 -IncludeBuilds / -ReleaseGate` 也会在 Android debug + release 构建后增加 `android release artifact` 步骤。该改动只增强正式 APK 物料校验，不改 Android 运行逻辑、后端接口、三份提示词、模型过滤或聊天滚动主链。

- 继续按“总负责人看门禁不被假绿带偏”的方向补上线脚本：`scripts/check-sms-usage.ps1` 在阿里云短信统计为空时会输出 `sms_usage_status=attention`，不再把空统计末尾写成单纯 ready，避免误读成短信套餐余额或验证码链路已无风险；`scripts/check-launch-readiness.ps1` 新增 `-ReleaseGate`，正式上架前会强制启用构建和后台登录后 owner smoke，并拒绝 `-AllowAttentionExitZero` 与任何 `-Skip*` 跳过项。同步更新 go-live runbook 和当前状态文档；本轮不修改主对话锚点、记忆提示词、今日农情提示词、模型过滤或聊天滚动主链。

- 继续按真机视频里“加粗处一伸一缩 / 小球和正文衔接不自然”的体感做最小渲染收口：`ChatStreamingRenderer.kt` 在 streaming 期间始终让最新视觉尾块走 streaming inline 规则，避免刚换行或尾块提交后马上切到 settled 规则造成加粗 / 斜体 / 行内代码轻微伸缩；纯 `**`、`*`、反引号这类不可见 Markdown 标记不再单独替换 waiting 小球，等第一个可见字一起进入正文，减少“小球消失后一拍空白”。同步补 `ChatStreamingRendererTest` 覆盖尾块 inline 模式和 pending marker reveal。该改动不换渲染引擎、不改成整段完成后再显示、不恢复 overlay / 反向列表 / raw delta / scrollBy / 小分割 item，不改三份提示词、今日农情列表项身份或后端输出过滤。

- 继续把云资源巡检输出改成业务负责人更容易理解的口径：按阿里云官方文档复核后，ECS 释放保护只适用于按量付费实例，RDS MySQL 释放保护只适用于按量付费或 Serverless；当前 ECS / RDS 都是包年包月，因此 `deletion_protection=false` 不是缺少保护。`check-resource-capacity.ps1` 现在会输出 `deletion_protection=not_applicable_prepaid` 和 `deletion_protection_applicable=False`，只有按量付费 / Serverless 且未开保护时才提示 warning；同步更新资源容量 runbook 和当前状态。该改动只优化巡检可读性，不修改云资源、不改后端 / Android 运行逻辑。

- 继续按“测试包和正式包权限口径一致、上架材料能解释清楚”的方向收口 Android 权限合规：`scripts/check-android-build-parity.ps1` 在确认 debug / release packaged manifest 权限集合一致之外，新增最终权限白名单校验，并锁住 AndroidX 合并的 `com.nongjiqiancha.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 必须为签名级应用私有广播保护权限。App 内隐私政策 / 应用权限页补充“后台待发送和应用私有广播保护”用途说明，debug-only 文案预览去掉容易误解为电话权限的“手机号认证”字样；同步更新隐私合规 runbook。该改动不新增运行时权限、不改登录、聊天滚动、三份提示词、模型输出过滤或后端接口。

- 已将提交 `5982361d` 部署到 ECS 和管理后台：`scripts/deploy-ecs-server.ps1` 远端 `go test ./...`、编译、新 slot health、Nginx 切换和后台 `/admin-api/` upstream 同步校验均通过，当前 Nginx active upstream 为 `3000`，后台 upstream 同为 `3000`，HTTPS healthz 200，未登录后台鉴权 401，`auth_strict=true / bailian=ok / sms=ok / redis=ok / upload_storage=oss` 正常且 `dev_order_endpoints=false`；`scripts/deploy-ecs-admin.ps1` 已重新部署 `https://admin.nongjiqiancha.cn/`，公网首页 200。部署后 `check-ecs-readiness.ps1` 和 `check-public-blackbox.ps1` 均通过，公网 API、官网、www、后台、协议页、公安图标和 HTTP->HTTPS 跳转状态为 ready。该部署只上线管理后台正式上架检查的 `manual` 区分和支付状态口径，不改 Android 真机包、三份提示词、模型输出过滤或聊天滚动主链。

- 继续按“总负责人看后台不被误导”的方向修正管理后台正式上架检查：`launch_readiness` 新增 `manual` 标记，前端“上线人工确认项”只展示显式人工确认项，不再把服务健康、App 错误或其它程序问题混进“这些不是程序崩了”的人工事项区。支付接入在购买入口关闭、开发期订单端点关闭时从 `blocked` 调整为 `attention`，明确不阻塞免费版、礼品卡内测或不含内购的正式上架；开放真实收费前仍必须完成支付申请、验签、回调、对账和权益发放闭环。App 备案、App 公安备案、AccessKey 轮换、最终真机回归、短信套餐余额、最终 release 物料和 SLS 首封邮件确认拆成显式人工项并加单测锁住。该改动只影响后台监控判断和展示，不改 Android、后端业务接口、三份提示词、模型输出过滤或聊天滚动主链。

- 按用户追问“渲染是不是很垃圾 / 小球和正文节奏还是不舒服”继续收口 Android 主聊天流式观感：不换渲染引擎，不改成整段完成后再显示，仍保持边生成边渲染；远端首个 chunk 太快返回时，waiting 小球最短展示从 1.5 秒轻微拉到 1.8 秒，让 720ms 呼吸动画至少有更完整的可见节奏；中文单字、普通 token、标点和换行的 reveal delay 整体放慢，避免长回复后段像一口气吐出；`onAdvance` 不再在写入新内容前抢先做一次程序化底部锚定，改为依赖现有 `SideEffect` 在新内容提交后的下一次 remeasure 请求底部锚定，降低生成中“先掉一点、再被拉回”的体感。同步补 renderer 单测锁住中文可读节奏和强标点停顿；不恢复 overlay、反向列表、小分割、raw delta 或 scrollBy 追滚，不改三份提示词、后端输出过滤、今日农情列表项身份或 96dp 工作线。

- 继续按“总负责人看上线门禁不误判”的方向补 `check-launch-readiness.ps1`：默认新增 `manual go-live checklist`，把 App 备案、App 公安备案、AccessKey 轮换、最终 APK 真机完整回归、SLS 首封告警邮件、普通短信套餐包余额和最终 release 物料一致性作为人工确认项直接打印；未通过临时环境变量显式确认时计入 attention。纯技术日常巡检可用 `-SkipManualGoLiveChecklist`，正式上线门禁不要跳过。该改动只增强上线判断输出，不改 Android / 后端运行逻辑，不改三份提示词、模型过滤或聊天滚动主链。

- 继续按“上线门禁要能给业务负责人看懂”的方向收口短信和位置口径：`scripts/check-sms-usage.ps1` 现在会校验阿里云短信统计接口返回状态，默认不按短信签名过滤，统计为空时默认短暂重试一次，并输出发送总量、成功、失败、无回执和空统计说明；空统计只作为趋势信号，不再被误读成套餐包余额或验证码链路一定无流量。同步修正项目记忆和 IP 定位 runbook 里残留的旧 `X-User-Region` 主链口径：当前 Android 通过 `/api/chat/stream` JSON body 传 `region / region_source / region_reliability`，旧 header 只是后端兼容兜底；`AGENTS.md` 也同步线上账号边界只读计数。本轮不修改主对话锚点、记忆提示词、今日农情提示词、模型输出过滤或聊天滚动主链。

- 继续按“上线前总负责人巡检”补自动化护栏和 Android 边界单测：`check-launch-readiness.ps1` 新增独立 `sms usage` 步骤，短信用量不再藏在资源巡检里；`check-backend-data-boundaries.ps1` 除了非 `acct_...` 资产归属，还会检查 `acct_...` 孤儿记录和 `app_accounts.phone_ciphertext` 缺失，线上本轮复查均为 0。Android 侧收窄 `SessionApi.resetUiRuntimeForCleanState()`，不再清空该 API 共享 handler 的全部回调，只递增 generation 并取消当前 SSE；今日农情时间线插入逻辑新增单测覆盖卡片-only、锚在真实消息后、锚点被裁剪后三种情况，继续锁住它是正向 `LazyColumn` 内普通视觉项。同步修正项目文档里位置通过 `/api/chat/stream` JSON body 传递、支付宝 App 支付正式链路需应用上线并开通 APP 支付产品、总门禁单独暴露短信统计等口径；本轮不修改三份提示词、不增加后端内容过滤、不改滚动主链。

- 继续按用户视频里“加粗处一伸一缩 / 后段一口气吐出 / 生成中轻微上下掉”的反馈收口 Android 主聊天流式渲染边界：`ChatStreamingRenderer.kt` 在 streaming 模式下如果当前帧刚好停在待续的 `**`、`*` 或反引号，不再把这些结构符号短暂当正文露出，等后续正文到达后继续按加粗 / 斜体 / 行内代码样式显示；settled 完成态仍保持严格规则，未闭合 Markdown 保留原符号，避免误吞用户可见内容。同步补 `ChatStreamingRendererTest` 覆盖待续符号、闭合加粗稳定显示和 DONE 后中文 reveal buffer 逐字 drain，补 `ChatScrollCoordinatorTest` 覆盖程序贴底不误判用户浏览、真实拖动抢占程序滚动；不改正向 `LazyColumn` 滚动主链、不改三份提示词、不增加后端输出过滤。

- 继续按“业务负责人看得懂巡检输出”的方向补只读门禁：`scripts/check-backend-data-boundaries.ps1` 的最近 24 小时 App warn / error Top 事件保留原始 `latest_created_at` 毫秒值，同时新增北京时间字段 `latest_created_at_cn=YYYY-MM-DD HH:mm:ss+08:00`，避免总门禁和后端数据边界巡检里只出现一串毫秒时间戳，难以判断闪退 / 登录失败是不是旧包噪声。该改动只影响只读巡检输出和 runbook，不查询日志 attrs、正文、手机号、URL、token 或模型 Key。

- 继续按上线门禁实时输出校正项目记忆：本轮 `check-launch-readiness.ps1 -AllowAttentionExitZero` 复查显示 ECS readiness、公网黑盒、SLS 告警、资源容量、后端数据边界、Android parity 和后台 surface 均为 ready，线上 Nginx active upstream 与后台 `/admin-api/` upstream 当前同为 `3001`；总门禁唯一 attention 仍是本机 PowerShell 未设置后台 owner 明文账号密码，登录后后台 smoke 按安全规则跳过。同步修正 `AGENTS.md` 和 `current-status.md` 里残留的 `3000` 当前 slot 口径，避免后续窗口按旧 active slot 做运维判断。

- 对齐上线总门禁和后台登录后 smoke 的凭据变量口径：`check-admin-authenticated-smoke.ps1` 原本支持 `NONGJI_ADMIN_USERNAME/PASSWORD` 和 `ADMIN_SMOKE_USERNAME/PASSWORD` 两组环境变量，但 `check-launch-readiness.ps1` 只识别前者，可能导致单独 smoke 可跑而总门禁误报缺凭据。本次让总门禁同样接受 `ADMIN_SMOKE_*` 兼容别名，并同步更新 go-live 与后台 runbook；不保存、不打印后台密码，也不改生产后台接口或鉴权逻辑。

- 继续按“业务负责人能看懂监控面板”的方向补 App 质量排障：后台 `/admin-api/v1/monitoring` 的 `auth_logs` 增加 `latest_crash_at`，只从 `client_app_logs.created_at` 聚合最近 24 小时 `app.crash / auth.app_crash` 的最新时间，不读取崩溃 attrs、正文、图片 URL、手机号、token 或完整堆栈；监控页“登录排障”“登录问题”“App质量”卡片会直接显示“最近闪退：时间”或“24h 无新闪退”，方便区分旧包历史闪退噪声和新包当前状态。该改动只增强后台只读监控可读性，不改 Android 运行逻辑、崩溃上报内容、提示词或后端业务链路。

- 修复 GitHub Android CI 最近两次红灯：失败提交不是 Android 编译或业务代码问题，而是 CI 干净 runner 在执行 `scripts/check-android-build-parity.ps1` 前只生成 manifest，没有显式生成 debug / release `BuildConfig.java`，导致 parity 脚本找不到生成产物。本次把 `.github/workflows/ci.yml` 的预生成步骤扩展为同时执行 `:app:generateDebugBuildConfig` 和 `:app:generateReleaseBuildConfig`，让 GitHub Actions 与本机验证前置条件一致；不改变 App 运行逻辑、滚动链、提示词、后端接口或正式 / 测试包口径。

- 历史记录：按用户视频反馈和仓库记忆深查主聊天 streaming 渲染 / 滚动：继续保留单一正向 `LazyColumn`、`SideEffect` 同帧底部锚定、96dp 工作线和两阶段 finalize，不恢复 overlay、反向列表、小分割或 raw delta。`ChatScrollCoordinator.kt` 收窄 streaming 期间 `isScrollInProgress` 的归因，只有真实拖动或已经进入 `UserBrowsing` 的惯性 / 浏览才暂停 AutoFollow，避免同帧锚定 / 内部 remeasure 被误判成用户浏览后出现“生成中先往下掉、再上去”的体感；当时 `ChatStreamingRenderer.kt` 新增 streaming-aware inline Markdown 子集，加粗、斜体、行内代码、Markdown 链接和裸 URL 在生成中尽量实时渲染，并把标准表格降级成手机可读项目行；该表格降级口径已在 2026-06-17 被“横向可滑轻量表格 + 复制按钮”替代。后端 DONE 后本地 reveal buffer 继续按打字节奏 drain 完再 finalize，不再把尾段一口气全吐出来。同步新增 renderer / scroll coordinator 单元测试和聊天 UI 回归 runbook 口径；本次不改三份提示词、不加后端内容过滤、不改变今日农情正常列表项身份。

- 按“测试包除了预览面板其他都要和正式包一样”的上线口径，补强 Android parity 门禁：`scripts/check-android-build-parity.ps1` 不再只扫源码模式，还会读取 Gradle 实际生成的 debug / release `BuildConfig.java` 和 packaged manifest，确认实际产物同包名 `com.nongjiqiancha`、同生产 HTTPS 后端、同 `USE_BACKEND_AB=true`、release 不带 `android:debuggable=true`、debug / release 权限集合一致；debug 只允许保留 Android Studio 诊断所需的 debuggable 属性。该改动只加强自动检查，不改 App 运行代码、滚动链、提示词或后端接口。

- 按用户反馈“主界面右上角加号不好看 / 图标有点小、颜色不重 / 稍小有点糊 / 左右稍微对称点”，把 Android 主聊天右上角会员中心入口从手绘“圆角方框 + 加号”换成透明底黑色线性旋转叶片：按 App 原始六片旋转叶片提取线条轮廓，不用原始 3D 绿叶位图、不带外部黑色背景、不保留原图暗纹；会员图标显示尺寸从 28/30dp 加到 36/38dp，左上角设置汉堡线条从 27/28dp 加到 31/32dp，线条加粗为纯黑，按钮点击区域、打开会员中心 / 设置的行为、会员权益 / 支付占位和后端接口均不变。该改动只影响主界面顶部视觉，不改滚动链、提示词或会员业务规则。

- 按用户反馈“最主要是看不到小球跳动”，只调整 Android 主聊天 waiting 小球和流式吐字节奏，不改滚动链、工作线、今日农情卡片、后端输出或三份已定稿提示词。远端首个 chunk 太快返回时，waiting 小球最短展示从约 1.05 秒拉到约 1.5 秒，覆盖现有 720ms 往返呼吸动画的一轮完整跳动；streaming reveal 从最多 2 个 token 一拍收为 1 个 token 一拍，中文单字和标点 / 换行停顿略放慢，让小球和正文都有正常聊天产品的可见节奏，但不做慢速朗读感。

- 按用户反馈“监控后台有些不一定能看懂”，继续把管理后台监控页往业务负责人可读方向收口：不改后端接口、权限或数据聚合，只在监控页顶部新增“处理顺序”区，明确先看颜色、先处理事项和真机回归入口；页面副标题、KPI 和卡片标题从“服务异常 / App报错 / 登录失败 / 关键队列 / 服务状态”等偏工程词，调整为“核心服务问题 / App异常 / 登录问题 / 运营队列 / 核心服务状态”等更直观口径，正式上架检查也改成“还能继续 / 需要确认 / 先别上架”的人工决策表达。`npm run build` 和 `scripts/check-admin-surface.mjs` 已通过。

- 继续补上线前 App 质量排障入口：新增只读脚本 [query-ecs-app-crashes.ps1](D:/wuhao/scripts/query-ecs-app-crashes.ps1)，通过 Cloud Assistant 在 ECS 内部读取生产 MySQL 里的 `client_app_logs`，专门查询 `app.crash` / `auth.app_crash` 闪退补报。脚本输出只保留崩溃签名聚合、包类型、版本号、系统版本、设备型号、脱敏用户类型、栈顶类 / 方法 / 行号和最多三条安全栈顶帧，不输出完整账号ID、手机号、token、图片 URL、聊天 / 反馈正文、完整 attrs、完整堆栈或模型 Key；[app-client-logs.md](D:/wuhao/docs/runbooks/app-client-logs.md) 同步补充使用方式和隐私边界。后续真机反馈“App 关闭 / 闪退”时优先跑该脚本，再结合后台 App 日志或 logcat 深挖。

- 按用户明确要求补充长期协作规则：主对话锚点 `server-go/assets/system_anchor.txt` 属于高敏生产提示词，后续 Codex 不得擅自修改。除非用户明确要求“修改主对话锚点 / 改 system_anchor.txt / 按这版落地”，否则只能审查、说明问题、提出草案并先发给用户确认；记忆文档和今日农情提示词如需微调，也应先说明影响再按用户确认推进。

- 按用户最终确认继续校准今日农情提示词到 `2026-06-15-v77` 并部署到 ECS：不再限制具体作物，也不单独点名排斥某个大田作物；选题以种植方面新闻为主，更优先找有生产价值或技术含量的内容，如栽培管理、植保病虫、种子种苗、农资农机、技术推广、苗情墒情、产地流通 / 价格和政策补贴。天气、气象、防灾或抢收仍可作为其中一个角度，但不要三条都写成天气预报，并要求写清对农事安排、防灾减损或田间管理的影响。该改动只调整今日农情提示词和防回归测试，不增加后端主题词 / 字数 / 来源硬过滤，不改变 Android 今日农情卡片作为普通列表项展示的主链。部署后 `check-ecs-readiness.ps1` 通过，Nginx active upstream 为 `3000`，`probe-ecs-today-agri.ps1 -Runs 3` 返回 `ok_count=3/3`、每轮 3 条完整 item、`prompt_version=2026-06-15-v77`、无 reasoning tokens；样本仍有天气切入，但第三轮技术 / 生产内容更明显，后续继续后台抽查。

- 按用户最终拍板更新记忆文档摘要提示词 [summary_extraction_prompt.txt](D:/wuhao/server-go/assets/summary_extraction_prompt.txt)：四块记忆收口为“短期记忆 / 长期记忆 / 用户画像 / 农业事件”，前三块保持通用记忆口径，第四块承接农技千查垂直农业事件。提示词明确摘要输入可利用每轮 `time / region`，关键症状、用药用肥、处理反馈、复查变化和阶段性判断尽量保留已有日期 / 时间 / 时间范围；地点可信度低或未知时只作低可信背景。主对话模型对用户图片的客观描述可作为“图片可见 / 本轮图片显示 / 用户图片中可见”的线索沉淀，但不保存图片 URL / 文件名，也不把图片线索写成确诊结论。同步更新提示词防回归测试和项目记忆；后端仍不解析四块、不做内容硬卡，非空输出整体覆盖写入。

## 2026-06-14

- 按用户继续校准主对话系统前置锚点的自称口径：首句从“农技千查的高级农业技术顾问”改为“对外称呼是农技千查”，并把询问助手自身时的回答改成“自己叫农技千查，是农业问答助手”，避免模型自我介绍时不知道自己的对外称呼。该改动只收口身份称呼和自我介绍边界，不改变农业问诊、会员、联网或证件查询规则。

- 继续按用户确认微调主对话系统前置锚点里的会员 / 订单 / 账号问题表述：去掉与会员权益无关的“检查更新”入口，改为“会员中心 / 账号管理 / 帮助与反馈”三类入口分工；同时补明退款、补偿、开通、关闭权益等处理结果不由模型承诺，以 App 页面、订单记录和客服处理为准。该改动只润色锚点边界，不改会员、订单、支付或账号后端逻辑。

- 按用户确认的措辞方向润色主对话系统前置锚点 [system_anchor.txt](D:/wuhao/server-go/assets/system_anchor.txt)：只调整表述顺序、斜杠堆叠、长句拆分和个别口语词，不改变规则含义、边界或执行结构。同步更新根 [AGENTS.md](D:/wuhao/AGENTS.md) 的锚点执行重点摘要；锚点仍保持当前轮优先、信息不足不下定论、图片先描述可见信息、联网搜索节制、证件 / 登记 / 备案 / 审定不做真伪裁决或合规背书等原口径。

- 按真机清数据反馈校准主聊天今日农情恢复口径：今日农情内容仍由后端独立 `daily_agri_cards` / `/api/today-agri-card` 提供，不写入 `/api/session/snapshot` 聊天历史轮次、不进入记忆文档、不扣问诊次数；Android 主聊天只把它作为 UI-only `ChatTimelineItem.TodayAgriCard` 插进同一个正向列表。清数据会清掉本机插入锚点，但登录后远端历史 hydrate 完成会再单独拉今日农情卡；拉到卡片后现在直接先展示，再保存当天锚点，避免因为本地锚点缺失让用户感觉“远端历史一回来卡片被冲没”。同时将 App 内置服务协议 / 隐私政策、官网协议页和 legal runbook 的联系邮箱统一更换为 `nongjiqiancha@foxmail.com`，旧邮箱已全仓清理。

- 继续按真机截图微调今日农情首屏卡片：在不改变 `ChatTimelineItem.TodayAgriCard` 正常列表项身份、不改滚动链和不做 overlay 的前提下，把只有农情卡片时的顶部安全距略增，卡片外层 / 内部横向和垂直间距再收紧一点，让黑色边框避开顶部标题栏遮挡，同时让摘要正文吃到更多宽度。后续仍需用户重新安装新包，在清数据后无真实消息、有后续消息、上下滑动三个场景真机看一眼。

- 按用户换窗口前确认的“今日农情卡片必须像正常消息列表项一样上下滑动”做过一轮卡片时代的小范围 UI 收口：当时继续保留 `ChatTimelineItem.TodayAgriCard` 插入同一个正向 `LazyColumn` 视觉时间线，不改成 overlay、浮层、sticky 尾卡或关闭动画，并收窄卡片间距让摘要正文更宽。该条是历史记录，当前最新口径已在 2026-06-17 改为普通 AI 文本视觉项和首屏文档流工作线交接，不再沿用旧 card-only 顶部特例。`./gradlew.bat :app:compileDebugKotlin` 和 `:app:assembleDebug` 已通过，只有既有弃用 warning。`scripts/check-android-build-parity.ps1` 同步显式按 UTF-8 读取源码，避免 Windows PowerShell 把 Kotlin 中文文案读成乱码后误报设置页 / 账号管理默认项缺失。

- 按用户继续追问“滚动链有没有被误伤、UI 回退有没有日志”补齐 Android 可查证据：复核最近几波主聊天滚动改动后，确认没有恢复反向列表、overlay、raw delta、小分割等旧链，首屏贴底仍是正向列表同一视觉尾部锚点；新增 `ui.chat_startup_state`、`ui.chat_startup_bottom_snap_done`、`ui.chat_startup_bottom_snap_pending`、`ui.settings_main_opened` 和 `ui.account_management_opened` App 自动日志，用布尔状态、数量和阶段追踪清数据 / 登录后首屏 reveal、贴底校准、设置页默认入口和账号页默认条目是否走当前 APK 代码，不上传聊天正文、手机号、图片 URL 或 token。Android parity 脚本同步要求这些日志事件保留，防止后续排障点被误删。

- 继续按真机反馈收口 Android 主界面和设置页稳定性：debug-only 文案预览面板的协议、隐私、权限、风险提示、会员中心、账号管理和礼品卡等长预览统一加高度约束 / 内部滚动，避免预览面板里点状态项时因嵌套滚动撑爆导致闪退；账号管理页文案进一步收短为“清理临时缓存 / 删除历史对话 / 退出登录 / 注销账号”，功能语义不变，清理缓存不删除聊天 / 会员 / 礼品卡，退出登录只吊销当前设备 session，注销账号仍是提交申请，debug-only 预览面板和二次确认弹窗也同步短口径，并把会员 / 帮助反馈 / 农情的“首次无缓存同步态”和“有旧数据后台合并”区分开。结合 Android 官方 Compose 列表 / 生命周期 / 副作用文档重新校准主聊天启动刷新：有本地聊天窗口时不再先透明隐藏等待底部校准，但仍必须执行一次真实启动贴底校准，不能把本地消息已存在误判为贴底已完成；远端 snapshot 回来后把“正文列表替换”和“失败 / 重试尾部状态更新”拆开，只有正文实质变化才替换消息列表，尾部状态照常更新但不带动整段正文重排，减少登录进入首页后文本整体闪一下的体感；会员中心有旧权益时改为静默刷新，失败也保留旧权益并显示同步失败，不再先切成读取中，失败提示里的“重新同步”已接回真实刷新；帮助与反馈、今日农情历史有缓存内容时，后台刷新失败只给短提示，不再整页切成失败态，客服消息首次载入也改为静态定位到底部；检查更新、礼品卡兑换、删除历史、退出登录、注销账号和验证码发送属于用户主动命令，仍保留短暂处理中态防重复点击。同步巡检阿里云告警：云监控 9 条 ECS / RDS / Redis 资源水位规则均为 OK，SLS 5 条应用日志告警为 ready；云安全中心 1 条提醒级 `OSS可疑访问行为` 已核对为 OSS 加密配置读取类运维行为并按“我已手工处理”收口，未创建长期白名单，文档不记录 AK / IP 等敏感细节。

- 按用户“服务协议 6 个板块按正式上线标准写，不暴露厂商 / 模型 / 系统机制，支付也按正式上线口径说明”的要求，重写 Android 设置页“服务协议”正文为 6 段正式口径：服务内容、农业建议边界、用户内容与账号权益、会员 / 支付 / 加油包 / 礼品卡、农资信息与交易边界、禁止行为和协议更新；隐私政策、第三方信息共享清单、个人信息收集清单、应用权限和风险提示同步去掉用户可见的具体云厂商、模型品牌、后台实现名、旧 token / SDK 机制和未落地服务商堆叠。支付条款可写微信支付 / 支付宝等页面支持方式和售后边界，但真实支付回调、验签、对账和发权益仍按 R12 / payments runbook 继续接入，未接通前 App 不能开放真实扣费入口。debug-only 预览面板里的检查更新预览卡片增加高度约束，避免嵌套滚动导致点“检查更新”预览时闪退；礼品卡页删除“兑换结果以后端记录为准，不显示完整卡号历史。”这句多余说明；远端历史图片过期的全屏预览和 debug-only 样式预览从黑底大块改为浅灰提示卡，仍保留“图片已过期，仅保留文字记录”的用户说明，不改 OSS 过期逻辑或聊天文字归档。

- 按用户“会员中心别有次数 / 价格 / 兑换扣次坑”的要求复查会员和礼品卡链路：后端现有测试已锁住 Plus 19.9、Pro 29.9、加油包 6 元 / 80 次和同一时刻 1 个 active 加油包；扣次顺序仍为每日额度 → 升级补偿 → 加油包；每日额度不是余额资产，不跨天结转；Plus / Pro 到期或 paid tier 缺失到期时间都会按 Free 算；礼品卡兑换后写入同一套 `user_entitlement`，有效 Plus / Pro 礼品卡会员同样满足加油包资格和升级补偿口径；礼品卡同档续期、Plus 升 Pro 折算补偿、低档不能覆盖高档。Android 端新增 `MembershipProductRules.kt` 集中会员展示常量，会员中心套餐卡、额度显示和礼品卡成功文案复用同一套次数 / 价格，礼品卡成功到期时间按北京时间格式化；会员中心规则区补“每日额度不结转；会员到期后按基础额度计算”。当前真实支付仍未接入，会员购买 / 升级 / 加油包按钮继续只展示规则并置灰，不调用订单接口。

- 继续按“最新数据 + 不闪一下”的用户诉求扫描 Android 里所有会向后端刷新数据的可见页面：主聊天首屏继续用 `session_generation` 校验的本地窗口先静态排版，再异步拉远端 `/api/session/snapshot`；会员权益在聊天 hydrate 后后台预取 `/api/me`，会员中心打开时仍刷新但保留旧权益；帮助与反馈 summary / messages 和今日农情历史改成按账号缓存旧列表，进入页面立即后台刷新，有旧内容时不再整页切成“读取中 / 正在同步”。检查更新、礼品卡兑换、短信验证码发送、登录等属于用户主动命令，继续显示短暂处理中或按钮 busy，不改成旧数据页，避免重复点击和误操作。

- 按真机反馈继续收口登录后主聊天首屏、设置页和加载闪烁：`ChatScreen.kt` 后端历史模式重新启用经过 `session_generation` 校验的本地聊天窗口作为首屏静态数据，远端 `/api/session/snapshot` 继续异步核对并以后端为主合并；消息列表会先不可见完成布局和底部锚定，再显示给用户，减少验证码登录成功后先看到文本再跳动的体感。设置页首页恢复独立“退出登录”浅灰卡片，仍走二次确认且只吊销当前设备 session，不删除历史对话、会员权益、礼品卡或反馈；账号管理页条目改为浅灰分组卡片，文字略收小。会员中心和帮助与反馈改成有旧数据先展示、后台刷新合并，避免每次进入先整页“读取中 / 正在同步”再闪一下。

- 按用户“主钥匙套餐优先吃满，不要 token 阈值提前消耗副钥匙”的最新口径，把 DashScope / 百炼 Key 池当前生产策略从 `auto` 调整为 `fallback`：`DASHSCOPE_API_KEY_1` 继续作为主钥匙，`DASHSCOPE_API_KEY_2` 只在主钥匙开流前返回限流 / 额度 / 鉴权类失败时同次请求兜底，不再因为 10 秒请求数或 token 用量阈值主动轮询分流。代码里的自动轮询能力仍保留为后续高峰可选方案，但当前生产不启用；1 秒 Key 冷却继续保留，避免主钥匙刚被限流时后续请求反复先撞主钥匙再多一次失败往返。阿里云官方文档同步确认百炼模型限流按主账号维度合并所有 RAM 子账号、业务空间和 API Key 调用量；联网搜索另有 15 RPS 主账号级限制，超限通常跳过搜索链路而不是直接报错。SSE 已经开始吐字后仍不在同一条回复中途切 Key，避免半条回复和重复成本。已部署到 ECS，readiness 实测 active upstream 为 `3001`、后台 upstream 同为 `3001`、HTTPS healthz 200。

- 提交 `570b46df` 已部署到 ECS 双端口 slot：远端 `go test ./...`、编译、新 slot health、Nginx 切换和后台 `/admin-api/` upstream 同步校验均通过，当前 Nginx active upstream 为 `3000`，后台 upstream 同为 `3000`，HTTPS healthz 200，未登录后台鉴权 401，`auth_strict=true / bailian=ok / sms=ok / redis=ok / upload_storage=oss` 均正常；公网黑盒 `check-public-blackbox.ps1` 复查 API、官网、www、后台、协议页、公安图标和 HTTP->HTTPS 跳转均为 ready。总门禁 `check-launch-readiness.ps1 -AllowAttentionExitZero` 复查 9 项，项目记忆、后台 surface、Android parity、ECS readiness、公网黑盒、SLS 告警、资源容量和后端数据边界均 ready，唯一 attention 仍是本机没有后台 owner 明文密码，登录后后台 smoke 按安全规则跳过。Android 端 UI 改动仍需用户重新安装新包才会生效。

- 按用户反馈把额度耗尽尾部状态收回通用失败链路：Android 不再展示单独的“今日额度已用完 · 点击重试”消息尾部，也从 debug-only 文案预览面板移除该独立条目；额度耗尽仍保留 assistant 失败态用于第二天恢复后重试，但尾部文案复用“回复未完成 · 点击重试”，当天点重试仍先走本地额度提示“今日额度已用完，请明天再试”。“发送失败 · 点击重发”仍只用于用户消息发送 / 上传失败，不新增第三套黑色胶囊，减少和中部短提示、输入框、今日农情卡片同时出现时的重叠风险；如果用户不点旧尾部而是直接发新消息，旧失败 / 未完成尾部会随新发送自动收起，失败用户消息会按放弃旧发送处理，空 assistant 占位会移除，避免历史黑胶囊长期挂在主界面。debug-only 文案预览面板把额度、网络、服务忙、上一条处理中、输入过长、图片异常和反馈失败统一收进“主界面中部浮层”分组，输入区只预览输入框本体，消息尾部只预览两条重试链路。清数据 / 空首屏和滚动链本轮只做小范围收口：当前仍由 `ChatTimelineItem` 视觉时间线、`bottomAnchorIndexOrMinusOne()` 视觉尾部锚点、clear epoch / hydrate guard 和 `InitialWorklinePhase` 兜住，并补齐“只有今日农情、没有真实消息”时手点回到底部的 no-op 边角；不恢复 overlay、反向列表或 raw delta。

- 继续按用户 / 总负责人视角做上线前巡检并收口一批低风险坑点：Android 主聊天“回到底部”按钮重新接回短暂显示状态，自动隐藏窗口从 1.2 秒延长到 2.2 秒，避免常驻压住内容但用户滚动后仍能重新唤出；今日农情卡片若原锚点消息因 30 轮窗口裁剪丢失，会插在“更早若干轮已保留”提示之后，不再跑到历史提示前面。设置页首页移除重复“退出登录”入口，只保留账号管理页“退出设备”二次确认，减少用户误以为两个入口语义不同；检查更新自动弹窗在用户点“稍后”时也记录已提示版本，手动检查仍可每次弹出，避免同一版本反复骚扰。登录页协议文字略放大到 12sp，仍必须用户手动勾选后才允许发送验证码 / 登录。后端账号旧 ID 迁移现在会把 `migrated / already_same_target / conflict_skipped` 等状态写进脱敏登录日志，冲突仍保护资产不合并但不再被误记为统一 accepted；礼品卡失败兑换流水写入和事务提交失败不再静默吞掉，避免后台追溯缺记录。回滚脚本和 runbook 同步加护栏：执行回滚必须显式传 `-BackupName ... -Apply`，只传 `-Apply` 会直接失败。

- 上线前巡检继续补 App 闪退排障质量：总门禁复查显示项目记忆、后台 surface、Android parity、ECS readiness、公网黑盒、SLS 告警、资源水位和后端数据边界均 ready，唯一 attention 仍是本机没有后台 owner 明文密码，无法自动跑登录后后台 smoke；生产 App 自动日志显示用户早上测试包崩溃已补报到 `auth.app_crash` / `app.crash`，但旧摘要里类名字段不完整。Android 和后端现已把 `exception / cause / top_class / top_method / top_line / stack_top / stack_next / stack_third` 作为安全崩溃诊断字段保留，并限制为类名、方法名和行号形态，URL、手机号、token、正文和图片信息仍丢弃；下次真机闪退时后台应能直接看到更完整的栈顶线索。

- 按真机截图继续收口主界面提示和今日农情质量：Android 全局短提示从输入框上方移到主界面中部浮层，菜单 / 会员页 / 图片面板 / 复制菜单打开时让位，避免和输入框、今日农情卡、跳到底部按钮或消息尾部重叠；额度耗尽仍保留消息流里的重试卡片，尾部文案复用“回复未完成 · 点击重试”，今天点击只提示额度，第二天额度恢复后可沿用原重试链继续发同一条；限流和活动流进行中等明确不可立即重试的中断只保留对应中部提示，网络 / 进程恢复等可恢复异常仍保留普通重试入口。跳到底部和静态底部锚点会按最新视觉尾部处理，让今日农情卡片作为时间线一员参与回底定位，但普通发送 / 工作线仍以真实消息为主。Android 还把中文定位地区从 HTTP header 改到 `/api/chat/stream` JSON body，避免 OkHttp 因非 ASCII `X-User-Region` 崩溃；后端优先读 body 中 `region / region_source / region_reliability`，再兜底旧 header / IP。设置页首页继续向 ChatGPT 设置页质感靠拢，使用白底、浅灰分组、书本感今日农情图标和单行菜单。今日农情提示词升级到 `2026-06-14-v76`，只轻微强化“不要一句话压缩稿、写 2-3 句完整短讯、信息量要够”，并继续鼓励经济作物、植保病虫、种植技术、农资农机、产地流通 / 价格和政策补贴，不增加后端字数 / 主题词过滤。生产部署后 `check-ecs-readiness.ps1` 通过，v76 探针 `runs=2` 为 `ok_count=2/2`，每轮 3 条完整 item，样本包含农业气象、无人机防控玉米病虫和农产品批发指数，摘要约 78-91 字；仍按“过线就收”观察，不恢复硬过滤。

- 继续按普通短信登录主线收口上线前巡检：服务端旧 `/api/auth/fusion/*` 路由暂不物理删除，但默认返回 `410 fusion_auth_disabled`，只有显式 `AUTH_FUSION_COMPAT_ENABLED=true` 才允许极短历史包兼容；`check-ecs-readiness.ps1` 同步硬拦生产误开该开关，并把普通短信 `SMS_ACCESS_KEY_ID / SMS_ACCESS_KEY_SECRET / SMS_SIGN_NAME / SMS_TEMPLATE_CODE` 列入脱敏 readiness 输出。Android 端做低风险体验修补：验证码输入键盘 Done 可直接登录、发送验证码 / 登录请求增加 busy 防重入、验证码框按最小高度适配大字体，主 Activity 锁竖屏避免登录和主界面在横屏手机上被挤乱，今日农情标题最多两行，礼品卡输入会自动去空格 / 横杠并转大写，检查更新不再因为仅弹出自动更新卡片就提前抑制后续提醒。公网黑盒脚本新增官网备案号、公安联网备案号、协议页和警徽图标检查；GitHub CI 删除旧 `FusionAuthProtocolActivity` 的 WebView 例外，防止融合协议页 / WebView 回潮。管理后台监控文案把礼品卡完整码可见性改成 `owner / finance_ops` 角色口径，避免误解为所有后台账号可见。阿里云资源巡检按官方文档校准：ECS 操作系统层内存指标需要 CloudMonitor 插件，本轮已通过阿里云 CLI `InstallMonitoringAgent` 给生产 ECS 补装 C++ 插件，ECS 上 `cloudmonitor.service` / `argusagent` 已 running；资源巡检会把云监控规则 `INSUFFICIENT_DATA` 暴露为 warning / attention，不再假绿。

- 本轮后端和后台已重新部署到生产：`scripts/deploy-ecs-server.ps1` 远端 `go test ./...`、编译、新 slot 健康检查、Nginx 切换和后台 `/admin-api/` upstream 同步校验均通过，当前 active upstream 为 `3001`；`scripts/deploy-ecs-admin.ps1` 已重新部署 `https://admin.nongjiqiancha.cn/`，公网首页 200，未登录 `/admin-api/v1/auth/me` 返回 401。部署后 `check-ecs-readiness.ps1`、`check-public-blackbox.ps1`、`check-sls-alert-readiness.ps1 -RequireExternalNotification -RequireDashboard -FailOnWarning`、`check-resource-capacity.ps1 -Strict`、`check-backend-data-boundaries.ps1` 和 `check-launch-readiness.ps1 -AllowAttentionExitZero` 均已复查；总门禁唯一 attention 仍是本机没有后台 owner 明文密码，登录后后台 smoke 按安全规则跳过。公网 `POST /api/auth/fusion/token` 已验证返回 `410 fusion_auth_disabled`，说明旧融合入口在线上默认关闭。

## 2026-06-13

- 按用户最新拍板把登录主链从阿里云融合认证切回普通短信验证码登录：用户已购买阿里云短信服务“国内通用短信套餐包”，本机 CLI 复查短信签名 `北京农技千问科技` 和模板 `SMS_507135108` 均为审核通过；后端现有普通短信链路走 `dysmsapi.SendSms`，`/healthz` 显示 `sms=ok / redis=ok`。Android 新包删除融合认证 AAR、`FusionOneLoginClient.kt`、`FusionAuthProtocolActivity.kt`、融合 Activity / ProGuard / 构建开关、电话状态 / Wi-Fi / 改网络权限和运营商取号明文域名例外；登录页只保留自有“农技千查 + 图标”、手机号、验证码、发送、登录和协议勾选，发送成功前端 60 秒倒计时，后端保留同手机号 5/10min、同 IP 20/10min、登录校验 10/10min 和 5 分钟验证码有效期。账号ID仍按手机号归一到同一个 `acct_...`，已注册账号ID和会员 / 礼品卡 / 记忆 / 聊天资产归属不因删除融合 SDK 改变。`scripts/check-android-build-parity.ps1` 已反向拦截融合认证 SDK / AAR / 权限 / 明文网关 / `/api/auth/fusion/*` 回潮，并要求 debug / release 除预览面板外继续同包名、同签名、同后端、同短信登录主链。服务端旧 `/api/auth/fusion/*` 暂保留给历史包兼容，新 Android 包不调用。

- 本轮后端和后台已按短信主线重新部署到生产：`scripts/deploy-ecs-server.ps1` 远端 `go test ./...`、编译、新 slot 健康检查、Nginx 切换和后台 `/admin-api/` upstream 同步校验均通过，当前 active upstream 为 `3000`；`scripts/deploy-ecs-admin.ps1` 已重新部署 `https://admin.nongjiqiancha.cn/`，公网首页 200，未登录 `/admin-api/v1/auth/me` 返回 401。SLS 5 条 AlertHub 告警已同步去掉 `fusion_auth_not_configured` 查询，并重新绑定 `nongji-prod-email` 行动策略和 `nongji-prod-ops` 仪表盘；`check-launch-readiness.ps1 -AllowAttentionExitZero` 显示项目记忆、后台 surface、Android parity、ECS readiness、公网黑盒、SLS 严格巡检、资源容量和后端数据边界均 ready，唯一 attention 是本机没有后台 owner 明文密码，登录后后台 smoke 按安全规则跳过。

- 历史过渡方案：曾短暂尝试把可见入口收口为“手机号输入 + 融合认证短信登录”，用户输入手机号后走 `/api/auth/fusion/token` -> 阿里云 100001 短信节点 -> 最终 `/api/auth/fusion/login`。该方案随后因 SDK 页面、图形验证和控制台策略不稳定被废弃；当前真相以上一条为准：新 Android 包只走自有普通短信验证码登录 `/api/auth/sms/send` / `/api/auth/sms/login`，不再集成或调用融合认证 SDK。

- 手机连上后继续排查一键登录：`adb` 确认设备在线，系统代理为空，当前手机存在 WiFi 与蜂窝数据环境；后端数据边界脚本显示 `app_accounts=1 / auth_sessions=1 / auth_sessions_active=0`，所有账号资产归属异常仍为 0。最近 24 小时一键登录失败 Top 集中在 `auth.fusion_verify_interrupt`，更像 SDK 授权页 / 运营商取号未完成，不是 App 因 WiFi 存在而硬拦。Android 登录页一键失败提示改成灰色中性提示，文案不再写“WiFi 或代理环境”；阿里云授权页 UI 继续隐藏 SDK 默认“登录”标题，并微调号码、按钮和协议位置，尽量降低 SDK 页面突兀感。该改动只动 Android 登录体验和项目记忆，不改手机号账号、会员、礼品卡、短信验证码或后端鉴权主链。

- 按真机反馈删除独立首次隐私同意大页：App 首次打开现在直接进入登录页，仍只在用户勾选登录页“我已阅读并同意《服务协议》《隐私政策》”后才记录隐私同意、初始化 `IdManager`、补报待发送崩溃日志并允许一键登录 / 短信验证码 / 短信登录；未勾选时不请求后端、不拉起融合认证 SDK、不申请电话状态权限。后台待发送图文 Worker 仍保留同意前 retry，不初始化身份或调用后端。`PrivacyConsentStore` 从独立页面文件拆成单独存储文件，构建一致性脚本同步改为护住登录页协议勾选和同意后初始化顺序。

- 同步更新 debug-only UI 文案预览面板：新增“登录与协议”分组，覆盖首次登录页、未勾选拦截和验证码兜底三种状态；检查更新权限提示改为“授权后返回 App 自动继续”口径；预览面板仍只在 debug 包显示，构建一致性脚本继续校验 debug / release 除预览面板和调试日志外保持同生产后端、同登录主链和同网络安全基线。

- 继续追真机一键登录图形验证和协议勾选错位：按阿里云官方文档确认 `onVerifyFailed` 后可以继续下一节点或结束场景，而 SDK 示例里的 `continueSceneWithTemplateId(..., false)` 会让失败继续走融合后续节点；Android 现在把一键登录失败、空 token、服务端换号失败、意外半程回调和补手机号失败统一改成结束融合场景并回 App 自己的验证码页，不再主动交给 SDK 图形认证 / SDK 短信兜底。登录页协议勾选框热区仍保持 48dp，但可见小方框改为居中，避免点击水波 / 阴影和方框错位。文档同步明确：控制台要在“认证策略设置”同时查全局策略和当前方案 Code 的方案策略，选择 100001 场景删除 / 关闭图形节点，不能关闭“融合认证解决方案”本身。

- 阿里云融合认证控制台策略同步收口：本机生产配置确认当前使用的方案 Code 与控制台“农技千查 / Android / FA000000009823740907 / 100001”一致；用户截图确认已在该方案策略里关闭两处“图形验证码”节点。首版推荐继续把阿里云 SDK 侧风控 / 短信 / 上行短信 / 通过手机号评分节点作为非必需能力处理，优先只保留号码认证一键登录，失败后回 App 自己的验证码页；策略侧变更按阿里云控制台口径等待约 5-10 分钟后再真机复测。

- 继续按真机登录反馈收口一键登录和账号页误操作：Android 一键登录初始化前调用阿里云融合认证 SDK 的 `useSDKSupplyCaptchaModule(false)`，本地强制关闭 SDK 内置图形认证模块；同时文档明确“功能开启页的图形认证关闭”不等于“融合方案策略里没有图形节点”，云端 100001 认证策略仍需确认关闭 / 删除图形验证节点，不能关闭“融合认证解决方案”本身，否则融合短信主链会被关掉。本条当时仍保留独立首次隐私同意页；随后已在同日把首次同意合并到登录页协议勾选，减少重复点同意；阿里云运营商授权页协议仍不默认勾选，避免替用户默认同意取号授权。账号管理页“退出设备”新增二次确认弹窗，退出只吊销当前设备 session，不删除历史对话、会员权益、礼品卡或反馈记录。构建一致性脚本已加护栏，防止 SDK 内置图形认证关闭被回退。该改动只影响 Android 登录 / 设置体验，不改后端账号ID、会员、礼品卡或短信验证码业务真相。

- 按真机截图反馈收口今日农情和设置页视觉：主聊天今日农情卡和侧边栏农情历史卡外框统一改为黑色细边，农情摘要不再按固定行数省略，避免 90-130 字摘要展示不全；设置页主菜单去掉每项下方说明文字，当前账号行也不再显示手机号小字，整体更简洁；今日农情菜单图标从叶片圆形改为更直观的简报 / 报纸线性图标。该改动只影响 Android 展示层，不改今日农情后端生成、提示词、登录、会员或礼品卡链路，已通过 Android debug/release Kotlin 编译和测试包 / 正式包一致性检查。

- 继续深挖 Android 主界面空白态、文字态和滚动交互，按“加载快不能靠缩水骗用户”的口径做低风险收口：主聊天本地联网预检不再要求 active network 必须是 WiFi / 蜂窝 / 以太网，只要系统报告具备互联网能力就放行真实 HTTPS 请求，避免 VPN、代理或 ROM 特殊网络被 App 提前误判离线；SSE 正常 DONE 但最终内容为空时，不再静默删除 assistant 占位并把 pending 后台任务标完成，而是保留“回复未完成 / 重试”失败态，让用户有明确反馈和重试入口。帮助与反馈消息区改为稳定 key 的 `LazyColumn`，长客服记录只渲染可见区域，不减少聊天记录、不边滚边联网；用户协议长文也先改成同类懒渲染页面，其它协议页保留原滚动以降低一次性改动风险。检查更新在 Android 8+ 跳“安装未知应用”设置后，用户返回 App 且权限已允许时会自动继续下载 / 安装流程，不再要求回到本页再手点一次。登录页品牌标题加窄屏 / 大字体单行省略保护，避免挤压一键登录和验证码表单。以上改动已通过 Android debug/release 编译、debug/release 打包和构建一致性检查；仍需真机覆盖长聊天、切后台回来、旧包检查更新和不同 ROM 授权页。

- 继续按主界面交互、登录和联网校对巡检 Android 前端：阿里云号码认证官方 FAQ 口径确认“移动数据 + WiFi”可正常尝试一键登录、纯 WiFi / 未开移动数据不可用，当前 App 保持有可用蜂窝数据就尝试、纯 WiFi / 无 SIM / SIM 未就绪回验证码登录，VPN / 系统代理仅 warning 并兜底验证码。主聊天 SSE 改为使用专用 OkHttp client，流式读取 `readTimeout=0`，普通 API 仍保持 60 秒读超时，减少模型长回复、后台切换或网络短抖时被客户端误断。用户消息图片展示补本地 / 远端双源兜底：发送成功后的图片若本地 URI 或缓存失效，会自动尝试远端 `/uploads/...`，缩略图和全屏预览共用同一兜底，不再明明 OSS 还在却显示过期。断网带图发送也会像纯文字一样生成聊天内失败气泡，保留本地缩略图并给“发送失败 / 重发”入口，不再只弹输入区提示让用户误以为图片没点上；仍不在离线时自动偷偷消耗额度。今日农情 Android 端展示条件收窄为只硬认 3 条标题 / 摘要完整 item，卡片标题显示仍统一“今日农情”，避免后端标题轻微变化导致整卡不显示；后端内容方向和三条硬数量要求不变，不恢复养殖 / 水产词表过滤或摘要字数硬卡。

- 继续跑上线巡检和生产探针：生产今日农情 `qwen3.5-plus + turbo + v74` 本轮累计 5 次探针均为 3 条完整 item、未返回 reasoning tokens，未见养殖 / 水产主体或外链下发，但摘要长度多在 75-87 字，按“过线就收、不恢复后端字数过滤、不继续堆细碎硬规则”观察。用户记忆文档探针确认四块结构、`qwen-plus`、顶层关闭思考和不暴露内部机制正常；默认样本里用户原话包含“不是专业搞这个”，因此模型写“非专业种植”不是凭空身份推断，生产提示词仍轻调为“画像写事实，不补身份判断”，不做后端词表过滤或结构硬解析。安全加固 runbook 同步修正 Nginx 口径：主聊天流已移除 `limit_conn`，不要再误读成同一公网 IP 两人聊天限制。进一步按多代理巡检结果补两处用户体验兜底：主聊天和帮助反馈的 Redis 限流异常时 fail open，避免 Redis 短抖挡问诊或客服；一键登录 `onVerifyInterrupt` 会立即回验证码登录，不再让用户等 30 秒全局超时。短信、登录校验、礼品卡、上传和内部 secret 等安全 / 成本敏感入口仍保持 fail closed。巡检脚本方面，`go test ./...`、Android debug/release Kotlin 编译、Android build parity、ECS readiness、公网黑盒、SLS 告警、资源容量和后端数据边界均通过；总门禁唯一 attention 仍是本机没有后台 owner 明文密码，无法自动跑登录后后台 smoke。

- 换上线前巡检角度继续补“假绿 / 误迁移 / 误强更”护栏：新增 [check-launch-readiness.ps1](D:/wuhao/scripts/check-launch-readiness.ps1) 作为总门禁，串联项目记忆、后台 surface、Android debug/release parity、ECS readiness、公网黑盒、SLS 严格巡检、资源容量严格巡检和后端账号资产边界；默认只要存在 attention 就返回非 0，只有显式 `-AllowAttentionExitZero` 才用于报告模式。旧本机 ID 迁移进一步收紧：生产默认不再接受裸 UUID 合并资产，只接受同一请求里旧 bearer token 能证明的旧 ID；`AUTH_ALLOW_UNPROVEN_LEGACY_UUID=true` 只留极短迁移 / 本地兼容，readiness 会硬拦。Android 崩溃补报成功后会清本地 pending，避免 `auth.app_crash` / `app.crash` 反复重复上报。检查更新强更兼容字段默认不生效，后台也拒绝保存强更，除非未来显式设置 `APP_UPDATE_ALLOW_FORCE_UPDATE=true`。资源容量脚本默认会把 SLS 告警漂移 warning 透传成 attention，不再吞成 ready；后台监控文案同步说明 SLS / 云监控状态来自最近严格脚本和仓库记录，不伪装成实时读取阿里云规则。

- 本轮后端和后台已重新部署到生产：`scripts/deploy-ecs-server.ps1` 远端 `go test ./...`、编译、新 slot 健康检查、Nginx 切换和后台 `/admin-api/` upstream 同步校验均通过，当前 active upstream 为 `3001`；`scripts/deploy-ecs-admin.ps1` 已重新部署 `https://admin.nongjiqiancha.cn/`，公网首页 200，未登录后台鉴权 401。部署后 `check-launch-readiness.ps1 -AllowAttentionExitZero` 串联 9 项巡检，项目记忆、后台 surface、Android parity、ECS readiness、公网黑盒、SLS 严格巡检、资源容量严格巡检和后端数据边界均 ready；只有登录后后台 smoke 因当前 PowerShell 没有后台账号明文密码按安全规则标为 attention。生产库仍是 `app_accounts=0 / auth_sessions=0 / 会员订单礼品卡资产=0`，所有账号资产归属异常为 0；Android 端改动已本地双包构建验证，但用户手机仍需重新安装新包后生效。

- 接支付宝 / 微信支付前先按官方文档校准支付 runbook：微信侧明确需要移动应用 AppID、商户号 `mchid`、商户 API 证书 / 序列号、APIv3 密钥、微信支付公钥或平台证书，APIv3 请求、应答、回调和 App 调起都要签名 / 验签，回调需解密后处理；支付宝侧明确服务端调用 `alipay.trade.app.pay` 生成 `orderStr`，Android 只调 SDK，同步返回只作刷新信号，真实发权益以后端验签后的异步通知或查单为准。申请材料同步固化到 [payments.md](D:/wuhao/docs/runbooks/payments.md)：主体、包名、正式签名、公网 HTTPS 域名、备案、商品描述、回调地址和密钥放置边界都已列清；当前仍不接真实渠道、不生成假支付、不把客户端“支付成功”当权益真相。

- Android debug-only 预览面板补齐近期上线前 UI 状态：汉堡菜单预览新增账号注销确认、普通检查更新下载中 / 安装未知应用权限提示、今日农情长摘要样本、今日农情历史页和同步失败态；今日农情预览样本更新为 3 条种植侧资讯，摘要接近正式展示长度，不包含养殖 / 水产 / 禽蛋肉奶价格类内容。检查更新默认口径同步改为普通更新：后台发布页不再露出强制更新勾选，更新说明可留空，后端和 App 统一展示“修复已知问题，优化使用体验。”；底层兼容字段不作为默认发版操作使用。

- 按多代理上线巡检结论收口一批低风险但容易“坑体验”的点：Android 帮助与反馈图片上传前重新复用主聊天压缩链，先压成 `<=1MiB` JPEG 再走 `/upload + purpose=support`，避免用户随手选的大图在客服反馈里直接被后端 1MiB 限制打失败；一键登录 VPN / 系统代理 warning 文案改成“可能失败，失败后验证码登录”，不再误写成已经切走；管理后台登录新增“用户名 hash + IP hash”默认 `10/10min` 专用限流，保护后台口令入口但不影响 App 用户登录、聊天或验证码；旧迁移 `004/005` 的 `expire_at` nullable DDL 改为 `information_schema` 条件执行，降低启动重跑旧 SQL 的风险；检查更新后台文案同步说明 App 启动会静默检查、用户也可手动检查，当前默认按普通更新处理。以上都不新增主聊天并发硬卡、不改会员资产归属、不恢复记忆文档或今日农情后端内容过滤。

- 本轮后端和后台已部署到生产：`scripts/deploy-ecs-server.ps1` 远端 `go test ./...`、编译、非当前 slot 健康检查、Nginx 切换和后台 `/admin-api/` upstream 同步校验均通过，当前 active upstream 为 `3000`；`scripts/deploy-ecs-admin.ps1` 已重新部署 `https://admin.nongjiqiancha.cn/`，公网首页 200，未登录后台鉴权 401。部署后 `check-ecs-readiness.ps1`、`check-public-blackbox.ps1` 和 `check-resource-capacity.ps1 -SkipAuthUsage -Strict` 均为 ready；新后台发布历史接口未登录返回 401，仍受后台鉴权保护。Android 端改动已经本地构建验证，但用户手机必须重新安装新 debug / release 包后才会生效，不会因为后端部署自动推送 APK。

- 管理后台继续补检查更新上线闭环：新增数据库表 `app_release_events`，后台每次 Android 检查更新保存 / 停更会和 `app_release_configs` 在同一事务内写入发布历史；新增 `GET /admin-api/v1/app-update/android/events` 和后台“发布历史”表，展示动作、版本、物料齐全度、操作人、时间和更新说明。该改动不改变 App 端 `/api/app/update` 判断逻辑，不增加用户限制，也不上传 APK；APK 上传、完整回滚入口和旧包真机覆盖安装仍是发版验收事项。

- 继续补上线前公网入口假绿护栏：新增只读脚本 [check-public-blackbox.ps1](D:/wuhao/scripts/check-public-blackbox.ps1)，不登录、不带后台密码、不读密钥，直接从公网请求 `api.nongjiqiancha.cn`、`nongjiqiancha.cn`、`www.nongjiqiancha.cn` 和 `admin.nongjiqiancha.cn`。脚本检查 API healthz 200 且包含 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss` 等关键标记，官网 / www / 后台首页 HTTPS 200，后台未登录 `/admin-api/v1/auth/me` 401，以及 API / 官网 / www / 后台 HTTP 入口 301 / 302 / 307 / 308 跳 HTTPS。首次公网运行 `warnings=0 / errors=0 / status=ready`；这补的是外部用户视角的可达性人工巡检，后续仍要把黑盒探测接成自动定时通知，不能替代登录后 owner smoke 或真机 App 回归。

- 监控后台继续补登录值班闭环：`GET /admin-api/v1/monitoring` 的 `auth_logs` 新增 `funnel` 登录阶段漏斗，按环境预检、电话权限、取认证 Token、SDK 初始化、授权页拉起、运营商取号、服务端换号、短信验证码和登录 / 运行闪退分组聚合最近 24 小时 `auth.*` / `app.crash` 自动日志。后台前端“登录排障”卡新增中文阶段表，每个阶段显示总数、明确成功、告警、错误和 Top 事件直达筛选；未知新事件仍保留在原 Top 事件表，避免漏看。该刀只读 `event / level / count`，不读取日志 attrs，不展示手机号、token、正文、图片 URL 或模型 Key，也不改变 Android 登录主链、短信 / 一键登录策略或任何用户限流。本轮后端和后台静态页已部署到生产，`check-ecs-readiness.ps1` 显示 active upstream 与后台 `/admin-api/` upstream 均为 `3001`，HTTPS healthz 200，未登录后台鉴权 401，`auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`；SLS 告警严格巡检、资源容量严格巡检和后端数据边界巡检均通过，登录后后台 smoke 因本机没有后台明文密码按安全规则跳过。

- 支付申请前把真实接入边界重新收口到 [payments.md](D:/wuhao/docs/runbooks/payments.md)：当前仍没有真实支付 SDK、支付渠道、自动续费、退款或对账，Android 会员入口不调用开发期订单接口，生产 readiness 继续硬拦 `ALLOW_DEV_ORDER_ENDPOINTS=true`。runbook 已按微信支付 / 支付宝官方文档补齐今晚申请可能要用的主体、包名、固定签名、公网 HTTPS 域名、备案状态、商品描述、回调地址建议和正式主链：Android 只调后端创建订单并拿 SDK 参数，权益发放只能由后端验签 / 解密后的异步回调和对账触发；后续正式订单表必须覆盖渠道订单号、金额、商品、状态、退款、回调和幂等发放结果。风险文档同步校正：手机号账号、per-user token、`AUTH_STRICT=true` 和 `acct_...` 资产归属护栏已落地，支付接入必须继续沿用同一个账号ID，不允许再按手机号、本机 ID 或客户端“支付成功”分叉发会员权益。

- 监控后台继续补上线闭环：新增“账号安全”后台页面和 `POST /admin-api/v1/auth/change-password`，所有后台角色都可自助修改自己的登录密码；服务端会校验当前密码、新密码最短 8 字符、拒绝新旧密码相同，改密成功后清除 `must_change_password` 并吊销同账号其它后台会话。若后台账号被标记为必须改密，除 `/auth/me`、`/auth/logout` 和 `/auth/change-password` 外，其它 `/admin-api/` 接口会返回 `password_change_required`，不靠前端隐藏页面兜安全。后台 surface 巡检已扩到 15 个路由 / 26 个 API 路径，账号安全路由和后端授权矩阵同步纳入检查；后台改密操作写审计，但不记录密码明文、hash、token 或完整请求体。本轮后端已通过 `scripts/deploy-ecs-server.ps1` 部署到 active upstream `3001`，后台静态前端已通过 `scripts/deploy-ecs-admin.ps1` 重新部署，readiness、SLS 告警严格巡检和资源容量严格巡检均通过；未登录访问改密接口返回 401，登录后后台 smoke 因本窗口没有后台明文密码按安全规则跳过。

- 按“主钥匙套餐优先、副钥匙不同阿里云主账号高峰兜底、用户无感”的口径调整 DashScope / 百炼 Key 池：`DASHSCOPE_API_KEY_1` 仍是主槽位，低中流量优先走主 Key；`DASHSCOPE_API_KEY_2` 作为副 Key，只在开流前遇到限流 / 额度 / 鉴权类错误时立即兜底，或 10 秒内达到 200 次模型请求、或 10 秒内已观测模型用量达到 600000 token 后进入 120 秒临时轮询窗口。该阈值不是用户限流，也不是常态分摊副 Key；token 阈值按 500 万 TPM 反推约为 72% 的 10 秒窗口容量，比单纯请求次数更适合长上下文、图片和长回复场景提前避开 TPM 压力，同时避免低流量过早消耗副钥匙。SSE 已经开始吐字后仍不在同一条回复中途换 Key，避免断流、重复回复和重复成本。生产 ECS 已用 Cloud Assistant 写入该配置并重新双端口发布，`check-ecs-readiness.ps1` 通过，`DASHSCOPE_API_KEY_1/2`、`DASHSCOPE_KEY_SELECTION_MODE=auto` 和自动轮询参数均为 set，真实 Key 值未输出、未写文档。

- 本轮后台敏感操作收口已部署到生产：后端通过 `scripts/deploy-ecs-server.ps1` 切到 active upstream `3000`，后台 `/admin-api/` upstream 同步为 `3000`；`scripts/deploy-ecs-admin.ps1` 已重新部署 `https://admin.nongjiqiancha.cn/`，HTTPS 首页 200，未登录后台鉴权 401。部署后 `check-ecs-readiness.ps1` 通过，healthz 仍为 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`；`check-sls-alert-readiness.ps1 -RequireExternalNotification -RequireDashboard -FailOnWarning` 和 `check-resource-capacity.ps1 -SkipAuthUsage -Strict` 均为 ready。登录后后台 smoke 脚本因本窗口没有后台明文密码按设计跳过，未打印或保存任何凭据。

- 继续把管理后台往“能上线值班、能查账、不误伤权限”的标准收口：owner 明确保留完整手机号和礼品卡完整卡码查看 / 复制能力，support / finance_ops 按职责可看完整手机号，finance_ops 可看完整卡码；其他只读 / 审计角色只显示脱敏信息，且前端和后端权限口径一致。礼品卡完整码现在不仅前端隐藏，后端列表和用户详情查询也只在 owner / finance_ops 授权时读取并解密 `code_ciphertext`，非授权角色不解密完整卡码。后台页面新增二次展示护栏：App 日志在前端再次脱敏敏感字段，客服图片只展示同源、无 query / hash、单层 `/uploads/support/*.jpg` 图片，今日农情来源只允许安全 HTTPS 链接，检查更新发布 / 停更和客服回复前二次确认。客服会话正文、客服回复和处理备注允许用户和授权客服发送数字、手机号、订单号、礼品卡码等排障必需信息；审计日志只记动作、长度、图片数量和结果，不写客服正文或备注正文。后台密钥、token、AccessKey、模型 Key 等系统秘密仍不能写进客服回复、处理备注、日志或审计。新增 `scripts/check-admin-surface.mjs` 巡检前端路由、按钮动作、后台 API 调用和服务端授权矩阵是否漂移；新增 `scripts/check-admin-authenticated-smoke.ps1`，可用临时后台账号环境变量登录生产后台并只读访问核心后台 API 后退出；readiness 脚本新增 `ADMIN_BOOTSTRAP_*` 残留硬拦，SLS 告警巡检升级到校验查询、触发条件、重复提醒、行动策略和仪表盘。管理后台 / SLS / 资源容量 / 当前状态 / 风险文档已同步，仍保留“首封 SLS 告警邮件送达、登录后 smoke 实跑、完整发版回滚入口、后台账号受控重置 / 禁用 / 角色管理”作为后续未闭环项。

- 继续巡检管理后台监控相关页面：把监控面板 `notes` 里的“不是完整告警中心”备注抽成可测试函数，并把服务健康页的 SLS 提示同步改为当前生产真相：5 条 SLS AlertHub 最小告警已绑定邮件行动策略和最小仪表盘，资源水位另走云监控邮件，剩余重点是首封 SLS 告警邮件送达确认、更细趋势和完整 Nginx access 聚合。不再在后台任何可见位置误写“外部通知、资源水位和仪表盘仍需继续补”。新增单测锁住监控 notes 的 SLS 口径，避免下次巡检又被旧文案带偏。

- 继续把监控面板往“上线值班可用”推进：后台 `/admin-api/v1/monitoring` 的 SLS / 客服反馈能力文案已和当前生产状态对齐，不再误写“外部通知行动策略、仪表盘和资源水位告警仍待补”；现在明确 5 条 SLS AlertHub 最小告警已绑定邮件行动策略和最小仪表盘，资源水位另走云监控邮件，剩余人工确认是首封 SLS 告警邮件真实送达。登录环境排障文案同步按 Android 真实事件语义校正：`auth.fusion_env_blocked` 只代表无网络 / 无 SIM / SIM 未就绪 / 无可用移动数据，VPN / 系统代理和 4G+WiFi 归到 `auth.fusion_env_warning` 且 App 已放行一键登录尝试。监控页第一屏新增“上线人工确认项”紧凑条，直接列出阻塞 / 需处理事项、负责人和入口，让非运维也能先看出正式上架还差哪些人工确认。帮助与反馈用户发送消息时，用户消息和系统自动回复已收进同一条 MySQL 命名锁 + 事务路径，避免同一用户并发连发时重复插入自动回复；锁名只保存用户ID hash，不泄漏账号ID。

- 按“客服图片和客服聊天记录别混”的口径收口帮助与反馈附件：Android 帮助与反馈上传图片继续复用后端 `/upload`，但新增 `purpose=support` 表单字段；后端据此把客服附件写入 OSS `support/` 并返回 `/uploads/support/<file>.jpg`，走已配置的 30 天生命周期。普通问诊图片不传 purpose，仍写 `uploads/` 并按 3 天删除；主聊天模型接口只接受普通 `/uploads/<file>.jpg`，拒绝 `/uploads/support/<file>.jpg`，避免客服截图误进问诊模型链。客服聊天记录正文、发送人、时间、已读状态仍保存在 MySQL `support_messages` / `support_conversations`，不随 OSS 图片 30 天生命周期自动删除；保存 / 删除 / 注销处理规则仍列为合规和客服系统后续收口项。上传 HTTP body 只把 multipart 额外预留从 `1MiB + 1KiB` 放宽到 `1MiB + 16KiB`，文件本身仍严格单张 `<=1MiB` JPEG。同步用阿里云 CLI 复查并更新 OSS 上传 RAM 策略默认版本到 v3，仅多放行 `support/*` 前缀，没有放大全桶写权限。

- 继续按“长期单对话别卡、但别让用户以为历史丢了”的口径收口主界面：`/api/session/snapshot` 的 `round_total` 已接到 Android，主聊天仍只渲染最近 30 轮保护手机性能；当后端总轮数超过当前 UI 展示轮数时，聊天列表顶部会用普通用户口径提示“更早若干轮已保留，后续对话会尽量接上”，不暴露长期记忆、后端归档等内部机制，清空历史后同步清掉提示。输入框对超长粘贴会本地截到 6000 字并提示“已保留前6000字，超出部分未保留”，后端仍保留同样上限兜底。记忆文档提示词的写作目标从旧 900 / 1200 字口径上调为一般约 1000-1400 个中文字符，复杂连续场景可更长，但仍是提示词建议而不是后端硬截断，内容少时不强行扩写。Android Manifest 和 parity 脚本同步删除当前融合认证 AAR 中不存在的 `PrivacyActivity` 校验，保留实际存在的 `LoginAuthActivity` / `PrivacyDialogActivity` / 融合认证 Activity 护栏，避免 lint 报假缺类。

- 本轮后端提示词已重新部署到 ECS 双端口 slot：远端 `go test ./...`、编译、新 slot 健康检查、Nginx 切换、API 与后台 `/admin-api/` upstream 端口校验均通过；当前 active upstream 为 `3001`，后台 upstream 同为 `3001`，HTTPS healthz 200，未登录后台鉴权 401，且 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`。部署后 `check-ecs-readiness.ps1`、`check-backend-data-boundaries.ps1`、`check-resource-capacity.ps1 -Strict` 均通过；当前仍没有正式用户、会员、订单或礼品卡资产，账号归属异常为 0。

- 按用户“查清现在到底有哪些限制，别做着做着坑正常用户”的要求，新增 [docs/runbooks/app-traffic-limits.md](D:/wuhao/docs/runbooks/app-traffic-limits.md)，把 Android、Go、Redis、Nginx、会员次数、上传大小、短信 / 一键登录限流、App 日志、帮助反馈、礼品卡、MySQL 连接池、SSE 时长和外部云资源容量边界统一列清。线上 Cloud Assistant 复查后，已把 Nginx 主聊天流从旧的 `6r/m`、burst 3、单 IP 2 连接调宽为 `60r/m`、burst 80，并移除聊天 `limit_conn`；普通 API 仍为 `60r/m` burst 80，上传仍为 `20r/m` burst 8。Go 后端没有全局 App 用户数 / DAU / 主聊天总并发硬卡，主聊天也不设置 `max_tokens`。当前口径是保留短信、上传、礼品卡等必要防刷限制；若真机测试出现 429，先确认来源，不新增全局主聊天并发硬闸、不改模型参数。

- 主界面性能按“少动主链、清掉明显浪费”的口径小步优化：Android 流式运行态不再把大段 `streamingMessageContent / streamingRevealBuffer` 放入 Activity saved-state，`isStreaming / streamingMessageId` 也同步改为运行时态，避免系统重建后出现“状态还在但内容为空”的错乱，恢复继续依赖已有流式草稿、远端快照和 pending 恢复链；流式吐字内部从反复 `drop()` 临时字符串改为索引推进，减少长回复时的临时分配和 GC 压力。聊天区远端图片缩略图新增 10 分钟失败短缓存，OSS 过期 / 失效图片滚回可见区时直接显示过期占位，不再反复发起 5 秒网络读取。后端 App 自动日志 Redis 限流改为异常时 fail open，避免日志系统影响主体验；短信、登录、礼品卡、上传和内部 secret 等安全 / 成本敏感入口仍 fail closed。

- 继续按普通用户体验和 Android 官方权限 / 可访问性口径小步收口：登录页和首次隐私同意门禁的协议勾选框视觉不变，但点击目标扩大到 48dp，降低用户点不中导致“融合短信登录登录老提示未同意”的概率；聊天页不再一进主界面就弹定位权限，未授权时改为用户首次发送问诊时按需请求一次，首轮仍可用缓存地区或后端 IP 粗定位兜底，不阻塞提问。阿里云融合认证协议承接页只允许加载农技千查官方 HTTPS 域名，禁止明文 HTTP 和外域跳转，网页缺失或加载失败时显示 App 内置协议要点兜底，并会同时根据协议 URL / title 判断展示用户协议或隐私政策要点。用户点一键登录时，fusion token 首次请求改用 6 秒短超时；VPN / 系统代理在仍检测到可用移动数据时从硬拦改成 warning 后继续尝试一键登录，失败再回验证码登录，纯 WiFi / 无可用移动数据仍直接验证码兜底。发版脚本补齐后台 `/admin-api/` upstream 端口校验，避免后台还指旧 slot 却被 401 健康检查误判成功。`check-android-build-parity.ps1` 已补 Android 侧回归护栏；主聊天滚动主链、登录 token 主链、会员 / 礼品卡、用户记忆和今日农情模型语义未改。

- 继续补后台读路径性能护栏：新增 `035_admin_order_gift_indexes.sql`，给订单按账号ID / 时间查询和礼品卡兑换失败原因聚合补索引，降低后续真实用户、订单和礼品卡日志增长后后台订单页 / 监控面板扫表的风险；该迁移只优化后台查询，不改变订单、会员、礼品卡兑换、升级补偿或账号ID归属规则。

- 本轮后端已重新部署到 ECS 双端口 slot：远端 `go test ./...`、编译、新 slot 健康检查、Nginx 切换、API 与后台 `/admin-api/` upstream 端口校验均通过；当前 active upstream 为 `3000`，后台 upstream 同为 `3000`，HTTPS healthz 200，未登录后台鉴权 401，且 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`。部署后资源巡检严格模式和后端数据边界巡检继续通过，当前没有正式用户、没有会员 / 订单 / 礼品卡资产，所有需要账号归属的表非 `acct_...` 计数仍为 0。

## 2026-06-12

- 继续按上线前“别假成功、别坑用户资产和安装包”的口径收口：发布脚本和回滚脚本现在都会把 API Nginx 上游与后台 `/admin-api/` 上游一起切到目标 slot，并在切换后验证生产 health 标记和后台未登录鉴权 401；回滚若发现后台上游没跟随会自动恢复 Nginx 配置并失败退出。发版 / 回滚 health 断言同步加入 `sms=ok`，避免短信配置坏了还被判定成功。`check-sls-alert-readiness.ps1` 新增 `-FailOnWarning`，`check-resource-capacity.ps1 -Strict` 会把 SLS 查询语句、严重级别或 runbook 注解漂移也作为失败，减少监控假绿。Android 检查更新客户端收紧为只有更高 `versionCode`、HTTPS APK、合法 SHA-256 和正数文件大小都齐全才展示 / 下载更新，下载器二次 fail closed，parity 脚本同步拦截回退。后端 App 日志 / 审计脱敏补 `apiKey / accessKeyId / accessKeySecret / modelKey` 等驼峰别名，后台用户详情改成精确 `user_id` 查询，并新增 `034_admin_performance_indexes.sql` 给后台账号、会员、当日额度、扣次、加油包和升级补偿统计补索引；主聊天、登录、会员 / 礼品卡、用户记忆和今日农情业务语义未改。本轮后端已部署到 ECS，active upstream 为 `3000`，后台 upstream 同为 `3000`，readiness 显示 HTTPS healthz 200、未登录后台鉴权 401，且 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`。

- 继续推进前后端性能和架构收口：Android 主界面删除已经没有 true 入口的 composer collapse overlay 旧链，移除多组无效 bounds 快照、overlay 预热状态和额外透明宿主，composer 收口继续只走底部固定 composer 的单一实测 reserve，不改消息列表主人、工作线、发送事务或两阶段 finalize；`scripts/check-android-build-parity.ps1` 同步拦截 `composerCollapseOverlay` / `ChatComposerCollapseOverlay` 等旧符号回潮。Go 后台总览 / 监控 / 产品洞察聚合接口增加 4 秒查询超时，并新增 `033_monitoring_query_indexes.sql`，给 `client_app_logs` 的等级 / 事件时间窗、`auth_sessions` 有效 session、`support_messages` 待回复队列补查询索引，降低用户量和 App 日志增长后后台刷新扫表或卡住的风险；主聊天、登录、会员 / 礼品卡、今日农情和用户记忆的业务语义未改。本轮后端已部署到 ECS，active upstream 为 `3001`，readiness 显示 HTTPS healthz 200、后台 upstream 同为 `3001`，且 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`。

- 继续把 SLS 告警从“能看到事件”推进到可上线闭环：云上 5 条应用告警已统一绑定邮件行动策略 `nongji-prod-email` 和最小仪表盘 `nongji-prod-ops`，仍不启用短信、电话或机器人以控制费用；`check-sls-alert-readiness.ps1 -RequireExternalNotification -RequireDashboard` 返回 `status=ready`，`check-resource-capacity.ps1 -SkipAuthUsage -Strict` 也已从 attention 收敛到 `status=ready`。`check-resource-capacity.ps1` 同步修正旧逻辑：默认也检查 SLS 通知 / 仪表盘，只有真的缺失才提示 `sls_alert_external_notification_or_dashboard_may_need_attention`，避免脚本假红灯继续吓人。公网黑盒只读脚本已在同日后续补齐；剩余运维风险改为首封告警邮件送达确认、公网黑盒自动定时通知、登录 / 模型用量趋势和真机回归。

- 继续补日志和性能 / 并发上线巡检的“别假绿”护栏：`check-backend-data-boundaries.ps1` 现在会输出最近 24 小时 App warn / error Top 事件，只展示事件名、等级、包类型、版本号、次数和最新时间，不输出 attrs、正文、URL、手机号或 token；线上当前 App 日志仍是联调数据，Top 事件集中在 `auth.fusion_env_blocked`、`auth.app_crash`、`auth.sms_send_failed`、`auth.sms_login_failed`、`auth.fusion_timeout` 和 `auth.fusion_verify_interrupt`。`check-resource-capacity.ps1` 现在会深度校验云监控 9 条资源规则的资源实例、阈值和周期，并可用 `-Strict` 把 SLS 外部通知 / dashboard 缺失变成失败；`check-sls-alert-readiness.ps1` 同步校验云上查询语句，`setup-sls-alerts.ps1` 已把模型 / 认证配置告警从宽泛 `dypns` 收窄到明确配置错误关键词。ECS / SLS 查询脚本扩了环境变量和密钥形态脱敏。今日农情公开响应补数量一致性：旧缓存或手工数据超过 3 条时只下发前 3 条，仍不按主题词、养殖词、广告词、可信域名或字数做内容过滤。

- 继续收口日志、资源告警和主界面性能：阿里云云监控已创建邮件联系人组 `NongjiQianchaOps`，并配置 9 条资源水位规则，覆盖 ECS CPU / 内存、RDS CPU / 内存 / 磁盘 / 连接、Redis CPU / 内存 / 连接；`scripts/check-resource-capacity.ps1` 已能复查联系人组、这些规则和自动快照状态；ECS 系统盘已绑定普通低频自动快照策略 `sp-2ze9ufwsu2i5hxm2wmrk` / `nongjiqiancha-prod-basic-7d`，每周二、周六北京时间 04:00 创建，保留 7 天，不启用跨地域复制；随后已补齐 SLS 应用日志 action policy / dashboard。Go 后端 `/healthz` 和后台健康状态现在会对 Redis 做运行期短超时 ping，不再只看启动时 client 是否初始化；正常 200 的 `/api/chat/stream` 长连接会记为 `http_sse_stream`，不再污染 `http_request_slow` 慢请求告警；模型开流失败日志补 `request_id / userId / clientMsgId / tier / prompt_chars / current_image_count`，不记录正文或图片 URL；该后端改动已部署到 ECS，当前 Nginx active upstream 为 `3001`，`check-ecs-readiness.ps1` 显示 HTTPS healthz 200 且 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`。Android 流式渲染期间不再每个增量提前构建“复制全文”，等流式结束 / settled 后再生成复制文本，减少长回复吐字时的 CPU 负担。

- 按“后端数据和运行设计别留坑”的上线前口径补了两条只读巡检：新增 `scripts/check-resource-capacity.ps1`，一次性查 ECS / 安全组 / 系统盘 / RDS / Redis / OSS / DNS / 域名 / HTTPS 证书 / SLS 告警 / DYPNS 认证用量，输出脱敏状态；新增 `scripts/check-backend-data-boundaries.ps1`，通过 Cloud Assistant 在 ECS 内只查 MySQL 表计数和 `acct_...` 归属异常，不读取手机号明文、聊天正文、反馈正文、图片 URL、礼品卡完整码、token 或模型 Key。线上巡检结果显示 ECS / RDS / Redis / OSS 容量宽裕，OSS Bucket `nongjiqiancha-prod` 已开启默认服务端 AES256 加密；线上库当前没有正式用户、没有会员 / 订单 / 礼品卡资产，所有需要账号归属的表非 `acct_...` 计数均为 0。文档同步新增 [backend-data-boundaries.md](D:/wuhao/docs/runbooks/backend-data-boundaries.md)，并更新资源容量、数据库只读、OSS、当前状态和风险文档；ECS / RDS 是包年包月，删除保护接口不适用，Redis 释放保护已开启，ECS 系统盘已开启普通低频自动快照，后续 attention 主要是首封 SLS 告警邮件送达确认和帮助反馈图片实际仍按 `/uploads/` 3 天过期。

- 继续按上线前“不被假绿坑到”的口径收紧服务器 readiness：`scripts/check-ecs-readiness.ps1` 里远端 `nginx -t` 不再 `|| true` 吞掉失败，Nginx 配置检测失败会直接退出并让本机检查失败。生产就绪检查现在同时挡 active slot 不活、后台 upstream 漂移、HTTPS healthz / 关键 health 标记异常、旧 token 兼容误开、开发订单入口误开和 Nginx 配置错误，避免反代层坏了还被报告成可上线。

- 总负责人视角继续收紧生产 readiness：`scripts/check-ecs-readiness.ps1` 现在把 `sms=ok` 也列为 healthz 必需标记，并会读取 ECS 环境文件的脱敏状态后硬拦 `AUTH_ALLOW_LEGACY_TOKEN=true` 和 `ALLOW_DEV_ORDER_ENDPOINTS=true`。也就是说公开生产检查不只看服务活着，还会挡住旧 bearer token 兼容、裸旧身份绕回和开发期订单直改入口误开，继续服务“全应用一个账号ID、正式登录必须手机号账号 session”的上线口径。

- 继续趁当前“还没有正式用户”的窗口收紧账号资产迁移：旧本机 ID 到 `acct_...` 的 `user_id_migrations` 映射改为不可改写，若同一个旧 ID 已经绑定到另一个账号，本次登录不再重映射、不再合并资产，避免账号之间互相吞会员、礼品卡、订单或记忆。会员权益合并同时补了 Plus 价值补偿：当 Pro 覆盖 Plus，或目标 Plus 被来源 Pro 覆盖时，剩余 Plus 当天次数和未来天数价值会转入 `upgrade_credits`，不让用户买过的 Plus 价值因为登录迁移被静默吃掉；新增单测锁住映射不可改写和补偿计算。

- 主界面继续按“别把 `ChatScreen.kt` 堆成屎山、但也不乱拆滚动主链”的口径做低风险拆分：聊天区用户图片 strip / 过期占位 / 预览关闭按钮移到 `UserMessageImageUi.kt`，图片预览下采样和 12MB LRU 缓存移到 `ChatImagePreview.kt`，`ChatScreen.kt` 只保留消息接线和状态。清数据 / 删除历史时又补了前台照片选择、外部相机回调的清除 epoch 守卫：如果用户清空期间异步导入才回来，会删除刚导入的私有图片并丢弃回调，不再把旧图片塞回 clean-state。Android parity 脚本同步拦截这些 UI / 解码代码回流到 `ChatScreen.kt`，并已兼容 Windows `powershell.exe` 和当前 shell。

- 已将提交 `86acc4b7` 部署到 ECS 双端口 slot：远端 `go test ./...`、编译、新 slot 健康检查、Nginx 切换均通过；`scripts/check-ecs-readiness.ps1` 显示当前 active upstream 为 `3000`，后台 `/admin-api/` upstream 同步跟随 `3000`，HTTPS healthz 200，未登录后台鉴权 401，且 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`。部署和 readiness 输出未读取或打印任何真实密钥。

- 主界面按“架构清晰、别把 `ChatScreen.kt` 继续堆成大泥团”的方向做第一刀低风险拆分：今日农情卡片的可展示校验、Compose 渲染和 debug 预览样本从 `ChatScreen.kt` 外移到 `TodayAgriCardUi.kt`，主聊天文件继续只负责视觉时间线插入、锚点和滚动接线；不改今日农情后端、拉取、三条硬要求、消息主链、工作线或发送 / 恢复事务。`scripts/check-android-build-parity.ps1` 同步新增架构护栏，防止今日农情 UI-only 代码又被塞回 `ChatScreen.kt`。

- Android 首次启动隐私同意链路补齐的历史记录：当时 `MainActivity` 会先检查本机隐私同意版本，未同意时只展示独立同意页，用户勾选并点击“同意并继续”后才初始化 `IdManager`、补报待发送崩溃日志并进入登录 / 聊天；拒绝则退出 App。遗留的后台待发送图文 Worker 也会先等隐私同意，不在同意前初始化身份或调用后端。该历史实现复用登录 / 设置页同一套服务协议和隐私政策正文，不额外引入网页或新文案真相。后续已在 2026-06-13 把首次同意合并到登录页协议勾选；debug / release 仍保持同后端、同登录主链、同网络安全配置，差异只保留 debug-only 预览和调试日志。

- 后台监控的安装包更新验收口径再收紧：`launch_readiness` 里“安装包更新”即使版本号、HTTPS APK、SHA-256 和文件大小都齐，也只标为 `attention`，提示“可进入真机覆盖安装验证”，不再标成 ready；前端上线前真机回归清单里检查更新物料齐 / 有日志也保持信息态，不染成绿色验收。真实 ready 仍以旧包真机完成“检查更新 -> 下载 -> 校验 -> 系统安装页 -> 覆盖安装成功”为准，避免管理后台把“物料齐”误导成“已经可正式上线”。

- 用户记忆和今日农情按最新“通用化表达、别过度强压”口径收口：用户记忆提示词保留“短期承接 / 长期背景 / 用户画像 / 农业重点事件”四个方向，但继续声明它们只是归位方向，不是后端硬结构；后端只清理外层代码块和空白，模型非空输出整体覆盖写入，不做四段解析、缺段保留、诊断词、作物词、主题词或画像完整度筛选。新增内部只读探针 `POST /internal/jobs/memory-document/probe` 和 `scripts/probe-ecs-memory-document.ps1`，使用合成样本真实调用 `qwen-plus`，不写用户数据；生产探针显示四块均能自然输出、`reasoning_tokens=null`、未暴露模型机制词。针对样本里把生活化身份表述概括成职业化标签的倾向，提示词只补轻边界：能用用户原话就用原话，不为了顺口把生活化表达改成职业化标签；用户用否定句、弱化句或生活化说法描述自己时，只按原话或中性背景记录，不反向概括成身份标签；不要因为对话涉及作物、地块、农资或农事，就自动给用户贴农业从业身份；除非用户自己这样称呼，不把“家里种地、自家几亩地”改写成农户、种植户、小农户这类身份标签。最终生产探针显示 qwen-plus 仍可能偶发身份泛化，当前按模型质量残余观察处理；后端仍不做词表过滤或内容卡扣。

- 今日农情提示词升到 `2026-06-12-v70` 并部署到 ECS：继续固定 `qwen3.5-plus + OpenAI兼容 chat/completions + search_strategy=turbo + forced_search=true + enable_source=true + 顶层 enable_thinking=false`，唯一硬数量要求仍是 3 条；摘要保留 90-130 字左右、一般不要明显低于 90 字的产品要求，但用“正常新闻短讯、避免太薄、不要套话”这类通用表达，不继续为个别小瑕疵堆细则。选题口径统一写成“养殖、水产不要”，不再用“动物类价格”这种不接地气的说法；后端仍不按养殖词、广告词、可信域名、发布日期、重复标题或字数做内容过滤，只做 JSON、3 条标题 / 摘要非空和私网 URL 安全兜底。生产探针 `scripts/probe-ecs-today-agri.ps1 -Runs 3` 验证 v70 为 `ok_count=3/3`、每轮 3 条完整 item、无 reasoning tokens，摘要长度约 89-115 字，样本主体为小麦机收、夏收天气、晒粮、尿素价格等种植侧材料。

- 一键登录体验按“能一键就尽量一键、明显不该拉 SDK 就直接验证码”再收口：登录页在申请 `READ_PHONE_STATE` 前先调用 `FusionOneLoginClient.precheckOneLoginEnvironment`，无网络、无 SIM、SIM 未就绪、纯 WiFi / 没有可用移动数据、VPN 或系统代理会直接切验证码登录并上报 `auth.fusion_env_blocked`，不先弹电话权限；有 SIM 且检测到可用移动数据能力时，即使当前 WiFi 开着也允许申请权限并尝试一键登录。`check-android-build-parity.ps1` 同步拦截删除前置预检或把 4G+WiFi 误判成 WiFi-only 的回归。本轮后端已重新部署到 ECS，active upstream 为 `3000`，readiness 显示 HTTPS healthz 200、`auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`，后台 upstream 同步跟随 `3000`。

- Android 主聊天删除残留本地假流式 / 假文案链路：无后端地址、远端失败或生命周期中断时，不再用固定农业示例文案伪装成 AI 回复；保留真实后端 SSE 的逐字展示、远端归档 snapshot 恢复和“回复未完成 · 点击重试”失败入口。极端切后台 / 系统杀进程时，如果本地保存过真实 streaming draft，冷启动会把已吐出的真实半截内容恢复为同一轮失败气泡并继续拉远端 snapshot，拉不到完整答案也不清成空白、不伪装完成。主界面动态链路复查后补了 pending finalize fresh bounds 约 1500ms 绝对超时兜底，正常仍等 settled renderer fresh bounds 再停止 streaming，极端切后台 / 布局回调丢失 / bottom reserve 迟迟不 ready 时不再无限停在生成态，超时路径不强拉底部，避免读旧几何大跳。今日农情 Android 展示也收成只校验并渲染前 3 条，且必须 3 条标题 / 摘要完整才展示，和后端“三条硬数量”保持一致。`check-android-build-parity.ps1` 同步拦截本地假流式残留和 Android 备份 / 数据迁移配置回退。后续排查聊天时以真实后端、归档和失败态为准，debug 包也不再用本地假回答绕过后端真相。

- 本轮后端已重新部署到 ECS，active upstream 切到 `3001`，`scripts/check-ecs-readiness.ps1` 显示 HTTPS healthz 200、`auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`，后台同域上游也跟随 `3001`。`scripts/probe-ecs-today-agri.ps1 -Runs 2` 验证 v67 为 `ok_count=2/2`、每轮 3 条完整 item、无 reasoning tokens，来源样本为农业农村部、山东农机化网、中国农资流通网，摘要长度约 76-92 字；三条硬要求已过生产探针，但摘要厚度仍按提示词和后台复核继续观察，不恢复后端字数硬过滤。

- 用户记忆提示词按最新拍板再校准：用户记忆按常规 AI 软件的通用用户记忆处理，不预设用户身份，不按内容类别做特殊排除或特殊分类；四块仍是“短期承接 / 长期背景 / 用户画像 / 农业重点事件”，用户画像写真正有用的个人信息、稳定偏好和长期限制，农业身份、作物、地块、设施等只有用户明确说成长期事实时才简写进画像，否则放到农业重点事件。翻旧 B/C 提示词后只吸收两点轻量边界：已明确解决且无后续价值的一次性事项可弱化，新问题、新地块或换作物时降低旧农事事件权重；不恢复 B/C 分层。后端仍不解析四段、不做内容硬过滤，非空模型输出整体覆盖写入。今日农情联网参考农业农村部、中国农业农村信息网、全国农技推广和农业气象类公开栏目口径，补入“生产动态、农事指导、病虫测报、苗情墒情、专项监测、农业气象、灾害预警、技术方案、单品 / 产区价格”等检索路标，受众口径保持“关心种植生产的普通人、种植户、农资门店和基层农技人员”，prompt_version 升到 `2026-06-12-v67`；继续保持必须 3 条、种植侧方向和后端不过滤卡死。

- 项目记忆 CI 护栏继续收紧：`scripts/check_project_memory.py` 不再只要求“关键文件变更时至少改一份 memory 文件”，而是按变更类型要求特定项目记忆文件。关键代码、runbook 或运维脚本变更默认必须同步 `recent-changes.md`；App / Go 后端 / runbook / ADR / 关键运维脚本等可能改变当前真相的变更必须同步 `current-status.md`；认证、部署、监控、支付、礼品卡、今日农情、上传、日志等风险敏感区域必须同步 `open-risks.md`；项目记忆校验策略本身变化还必须同步 `pending-decisions.md`。同时把关键项目脚本纳入 watched 范围，避免部署、回滚、readiness、SLS、Android parity 等脚本改了但 CI 不要求更新项目记忆。

- 登录细节继续按上线前体验 / 安全收口：Android 验证码登录现在只允许提交 6 位验证码，避免 4 / 5 位输入也打到后端造成无意义失败；一键登录 release logcat 不再打印阿里云 SDK 原始 `errorMsg / innerMsg`，只保留 template / node / error code / inner code，完整 SDK 文案仅 debug 包保留，`check-android-build-parity.ps1` 也同步拦截这两类回归。后端手机号登录成功触发旧本机 ID 迁移时新增脱敏结构化日志，记录 legacy ID hash、迁移来源类型和目标账号ID，方便以后核查资产合并；runbook 同步说明旧 bearer token 证明只用于迁移，不代表生产业务接口可以开启旧 token 鉴权，并把“未证明 UUID 迁移桥”列为公开放量后需要窗口化 / 审计 / 关闭的风险。本次后端已部署到 ECS，active upstream 从 `3001` 切到 `3000`；`deploy-ecs-server.ps1` 同步给阿里云 CLI 调用加 `connect/read timeout` 和重试，避免控制面短抖动反复打断分片上传。

- 后台监控页继续按产品负责人视角校准“上线前真机回归清单”：检查更新项现在先看配置是否合法、是否停更、HTTPS APK / SHA-256 / 文件大小是否齐全，再决定状态；即使 24 小时内已有 `app_update.*` 检查日志，只要正式下载物料未齐或更新停用，也不会显示为绿色验收。后端监控 action / launch readiness 文案同步从“建议补物料”收紧为“必须补 HTTPS APK、SHA-256 和文件大小；物料不齐时后端不会向旧版 App 下发新包”，并新增单测锁住安装包更新物料不齐只能保持 attention。运维蓝图和功能巡检文档同步改为后台 `app_release_configs` 是发布主链，`APP_ANDROID_*` 只是兜底。

- 继续按上线前“不要坑用户、测试包/正式包尽量一致”口径补护栏：GitHub Android CI 会先生成 debug / release 的 merged 和 packaged manifest，再跑 `scripts/check-android-build-parity.ps1`；parity 脚本不再在缺少生成 manifest 时静默跳过，必须确认阿里云融合认证 SDK Activity 最终主题和 `exported=false` 进入产物。CI 的 WebView 检查改为只禁旧 `gpt-demo` / `android_asset` 和非协议页 WebView，避免误杀合法的 `FusionAuthProtocolActivity`。检查更新后端改为只有更高版本号、HTTPS APK、SHA-256 和文件大小都齐全时才下发可用更新，物料不齐会返回无更新并记录 `missing_release_artifacts`；`gradle.properties` 和上传工具日志也同步去掉“可用 Gradle 属性 / 环境变量切换后端”的旧口径。

- 用户最新拍板今日农情“必须三条”，这是唯一硬数量要求；记忆文档和今日农情继续按“不能坑用户、不要过度压模型、通用化表达但守住安全边界”的折中方案走。今日农情提示词升到 `2026-06-12-v63`，要求模型必须输出 3 条，材料不够就扩大检索词继续找真实种植侧材料，不再允许 2 条成卡；后端、Android 和后台预览同步要求 3 条标题 / 摘要完整 item，但仍不恢复养殖词、广告词、可信域名、发布日期、重复标题等内容过滤，质量方向继续靠提示词、探针和后台复核。用户记忆提示词明确为“用户记忆整理器”，保留“短期承接 / 长期背景 / 用户画像 / 农业重点事件”四块建议结构：短期承接接住下一轮上下文，长期背景放稳定事实，用户画像放用户明确给出的个人背景、稳定偏好和长期限制，农业重点事件放后续可能追踪的农事线索；后端仍不解析四段、不按内容卡扣，非空输出即覆盖写入。后端和后台已重新部署，`scripts/check-ecs-readiness.ps1` 显示 active upstream `3001`、healthz 200、`auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / redis=ok / upload_storage=oss`；`scripts/probe-ecs-today-agri.ps1 -Runs 2` 最新验证 `v63` 仍为 `ok_count=2/2`，每轮 3 条完整 item，无 reasoning tokens，摘要长度样本约 67-98 字，厚度仍有波动；本轮短暂尝试 v64 / v65 强化摘要厚度，但线上探针反而更薄，已回退 v63，后续不恢复后端字数硬过滤，继续靠探针和后台复核小步校准。用户记忆本地 `Summary` 测试和真实提示词防回归测试已通过。

- Android 一键登录环境预检继续按官方 FAQ 和真机兜底口径收口：4G / 5G 或 4G+WiFi 可继续尝试一键登录，VPN / 系统代理和无可用移动数据直接回 App 验证码登录；验证码登录仍应在 WiFi、代理和移动数据环境都可用。`FusionOneLoginClient` 的系统代理识别补到 active network `LinkProperties.httpProxy`、Java proxy properties 和旧 Android proxy fallback 三层，`check-android-build-parity.ps1` 同步拦截删除该检测的回归。

- 继续按上线总负责人视角收口监控：新增 `scripts/check-sls-alert-readiness.ps1` 只读巡检 SLS 5 条最小告警是否存在、启用、进入 AlertHub、绑定行动策略和仪表盘；`scripts/setup-sls-alerts.ps1` 新增可选 `-ActionPolicyId` / `-DashboardId`，方便后续用户在 SLS 控制台建好行动策略和仪表盘后统一绑定，不把联系人手机号、机器人 webhook 或通知密钥写进仓库。当时云上巡检结果是 5 条规则均启用且进入 AlertHub，后续已在本日补齐外部邮件行动策略和最小仪表盘；本轮后端和后台已重新部署，`scripts/check-ecs-readiness.ps1` 显示 Nginx active upstream 与后台 `/admin-api/` upstream 均为 `3001`。后台监控页也校准了两处容易误导的上线判断：首屏和“当前结论”会区分“运行监控正常”和“正式上架仍有阻塞项”；支付接入文案改成“真实收费前阻塞，不阻塞免费版和礼品卡内测”。其中早些时候“2 条可预览”的今日农情后台口径已被本日顶部 `v63` 三条硬数量要求替代。

- 管理后台监控页把“明天真机回归清单”改成长期可用的“上线前真机回归清单”，并把今日农情回归说明从“App 首页”校准为“聊天页卡片和设置入口”，避免测试人员按过期时间或错误位置理解；这只改后台 UI 文案和项目记忆，不改变 Android / 后端业务链路。

- 本轮后端已部署到 ECS 双端口 slot，最终 Nginx active upstream 为 `3000`，后台 `/admin-api/` upstream 同步跟随 `3000`；`scripts/check-ecs-readiness.ps1` 显示 HTTPS healthz 200、未登录后台鉴权 401，且 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok / upload_storage=oss`。部署后 `scripts/probe-ecs-today-agri.ps1 -Runs 2` 验证 v62：`ok_count=2/2`，每次 3 条可展示 item，无 `reasoning_tokens`，标题主体已避开调研 / 平台过程词和养殖水产，但摘要长度仍约 71-77 字，低于 90-130 字目标；当前记录为质量观察项，不加后端字数硬过滤。ECS 日志尾部显示新部署后只有 readiness / 探针和公网扫描 404，旧短信 503 / 登录 500 都是部署前错误。

- 手机号登录环境策略按真机可用性再校准：验证码登录继续作为 WiFi、系统代理、VPN、移动数据环境都必须能走的正式兜底；一键登录仍避开 VPN / 系统代理和纯 WiFi / 无可用移动数据，直接切 App 验证码页；但 4G+WiFi 或当前活动网络不是蜂窝、同时系统仍检测到可用移动数据能力时，不再一概当成 WiFi-only 阻断，而是上报 `auth.fusion_env_warning` 后允许一键登录尝试。`check-android-build-parity.ps1` 同步拦截回归：必须检测全部网络的蜂窝数据能力，VPN / 系统代理必须阻断，纯 WiFi / 无蜂窝数据必须回验证码，4G+WiFi 只能 warning 不能硬禁。

- 后台监控和 runbook 同步把登录排障文案改成普通用户能理解的两段：`auth.fusion_env_blocked` 是无网络、无 SIM、SIM 未就绪、VPN / 系统代理或没有可用移动数据导致一键登录不该硬拉 SDK；`auth.fusion_env_warning` 是 4G+WiFi 或混合网络下已放行一键登录尝试。验证码登录不再被文案误导成必须关闭 WiFi / 代理，只要生产 HTTPS 后端可达就应作为正式兜底可用。

- 旧本机 ID 合并到 `acct_...` 时继续补账号资产护栏：如果目标账号已经有 `session_ab`，现在不会直接删除旧本机 A 层滑窗 / 记忆文档；目标为空时继承旧值，目标已有记忆时把旧记忆追加并标记 `pending_retry_b`，后续由摘要模型重整，避免登录迁移时静默丢记忆。回归测试新增长期身份表覆盖清单和 `session_ab` 合并 SQL 护栏，继续锁住会员、额度、加油包、订单、礼品卡、帮助反馈、App 日志、聊天归档、记忆文档和注销申请都归同一个账号ID。

- 历史归档：今日农情提示词曾升到 `2026-06-12-v62`，当时仍坚持“不加后端内容过滤、主要靠提示词控制方向”，并允许 2 条可展示 item 兜底发布。该数量口径已被本日顶部 `v63` “必须 3 条”的最新拍板替代；v62 只保留为历史过程。

## 2026-06-11

- 手机号登录继续按“明天真机必须能测”收口：后端短信发送在调用普通 Dysms `SendSms` 前仍先缓存 6 位验证码摘要，但如果供应商返回超时 / 异常，不再主动清掉 Redis 验证码，避免“短信实际已送达、用户填码时后端已删码”的情况；账号登录成功后才清码的规则不变。DYPNS / Dysms SDK 返回空 body 时现在返回稳定错误码，不再可能因 nil body 触发 panic。旧本机 ID 合并到 `acct_...` 时补迁移 `account_deletion_requests`，让注销申请也归到同一个账号ID。Android 一键登录环境预检把 VPN / 代理和当前活动网络非蜂窝从 warning 升为直接阻断并回 App 验证码登录，避免 WiFi / 代理环境继续硬拉阿里云 SDK 出图形验证 / 怪页 / ROM 闪退；构建一致性脚本新增已生成 merged / packaged manifest 检查，确认融合认证 SDK Activity 最终都有本地主题和 `exported=false`。

- 登录账号 ID 和资产归属上线前收紧：生产库只读审计确认 `app_accounts=0`、`auth_sessions=0`，只有 1 个旧测试本机 ID 残留在 `user_entitlement` 和 `daily_usage`；已按用户“没正式用户、无用 ID 可删”的口径清理旧测试归属，清理后用户资产表非 `acct_...` 归属合计为 0。后端新增保险丝：正式 v2 auth token 只允许签给 `acct_...`，生产严格鉴权下带 `session_id` 的非账号 token 也会直接 401；Android `SessionApi` 和 `IdManager` 都拒绝保存 / 认可非 `acct_...` 登录态。账号迁移 SQL 中 `daily_usage`、`upgrade_credits`、`session_generation` 改为派生 source 表，避免 MySQL 同表 `INSERT SELECT ... ON DUPLICATE KEY UPDATE` 歧义再次把短信登录打成 500。后端已部署到 ECS active slot `3000`，readiness 显示 `auth_strict=true / dypns_fusion=ok / dypns_sms=ok / sms=ok / redis=ok`；Android debug 包已重新 assemble，通过 `check-android-build-parity.ps1`。

- 手机号登录再收一刀保可用：Android 登录页重新改成“用户点一键登录时按需申请 `READ_PHONE_STATE`，拒绝就直接切验证码登录并记 `auth.fusion_permission_denied`”，`FusionOneLoginClient` 也把 `vpn_active`、`no_active_cellular` 从 warning 升成前置 `auth.fusion_env_blocked`，不再让明显高风险环境继续硬拉融合认证授权页；同时在 App manifest 覆盖 `com.mobile.auth.gatewayauth.LoginAuthActivity` / `PrivacyDialogActivity` 主题为本地 translucent 兼容主题，尽量降低荣耀 / 华为 / Android 15+ 授权页拉起闪退风险。服务端短信链路同步修正：`DYPNS_SMS_TEMPLATE_PARAM` 不再默认强塞 `{\"code\":\"##code##\"}`，只有显式配置模板变量时才传 `TemplateParam`，避免阿里云 `SendSmsVerifyCode` 因验证码模板变量不匹配先报“非法参数”再触发频控。`./gradlew.bat :app:compileDebugKotlin` 和 `cd server-go && go build ./...` 已通过。

- 今日农情提示词升到 `2026-06-11-v59`，继续坚持“不要坑用户，也不要过度压模型”的折中口径：不加后端内容过滤，不恢复可信域名白名单、固定站点限制、字数拦截或正好 3 条硬闸门，只在提示词里把摘要厚度再轻轻往上托一档。新要求是手机卡片继续按 3-4 行、偏 3.5 行来写，宁可接近 100 字，也不要收成 70 多字薄摘要；第二句不能只剩“需关注”“可留意”这类很短的收尾；如果某条仍只有 70 多字，就先补足具体事实和直接影响再输出。记忆文档这轮继续维持单份纯文字记忆摘要方案，真实样本复测仍稳定，不再大改。

- 今日农情线上 v58 探针 `runs=2` 继续 `ok_count=2/2`，`prompt_version=2026-06-11-v58`，无 reasoning tokens，单次 total tokens 约 7.22k；6 条标题和来源名可展示，本轮未再混入养殖 / 水产 / 猪肉主体，说明核心边界比 v57 稳一些。残余问题是摘要仍约 73-79 字，模型没有完全执行“不低于 85 字”；当前先记录为质量观察项，不继续无限堆提示词，也不加后端字数过滤。

- 今日农情线上 v57 探针 `runs=2` 继续 `ok_count=2/2`，`prompt_version=2026-06-11-v57`，无 reasoning tokens，单次 total tokens 约 7.2k；摘要长度略有改善但仍有 77-81 字样本，且第二轮混入“瘦肉型白条猪肉出厂价微涨”，踩到“养殖水产不要”的核心边界。提示词升到 `2026-06-11-v58`：仍不加后端关键词过滤，只在事实原则和输出前自检里要求最终标题 / 摘要若以猪肉、生猪、猪价、禽蛋、水产、饲料、饲用原料、兽药或鱼虾为主体，必须重写为种植侧材料。

- 今日农情线上 v56 探针 `runs=2` 继续 `ok_count=2/2`，响应已正确返回 `prompt_version=2026-06-11-v56` 和 `total_runs=2`，无 reasoning tokens，单次 total tokens 约 7.15k；但 6 条摘要仍约 68-84 字，偏薄问题没有被“通常至少两句”完全压住。提示词继续小幅升到 `2026-06-11-v57`：仍不加后端字数过滤，只把写作要求改成摘要必须像新闻短讯、至少两句、不低于 85 字，并在输出前自检里要求不足先重写该条；如果 v57 仍偶发偏短，后续先按运营抽查观察，不继续无限堆硬规则。

- 今日农情线上 v55 探针 `runs=2` 继续 `ok_count=2/2`，每轮 3 条可展示 item，`reasoning_tokens=null`，单次 total tokens 约 7.1k；标题和来源名能展示，但摘要仍有 69-83 字偏薄样本。提示词小幅升到 `2026-06-11-v56`：仍不加后端内容过滤或字数拦截，只在提示词里要求摘要通常至少两句，第一句写事实，第二句补直接影响、农时窗口、风险点、供应变化、农资 / 流通影响或基层可留意事项；内部探针响应同步返回 `prompt_version` 和 `total_runs`，方便后续巡查。

- 继续给今日农情和记忆文档主链补防回归护栏，不改变生产逻辑：今日农情测试名从“三条必需”改成“结构化内容可解析且忽略模型自带 URL”，避免后续窗口误以为发布仍硬要求正好 3 条；新增测试确认本机 / 内网来源 URL 只会被剥离，不会把整条可展示内容拦掉，继续符合“最大限度放开，提示词控方向”的口径。记忆文档新增旧快照写回失败的测试，确保 stale 摘要不会覆盖现有记忆，也不会误清 `pending_retry_b`；主对话记忆注入测试扩展为禁止旧“后台参考 / 后台摘要 / B层 / C层 / 内部机制”标签回流。`go test ./...` 和 Android `:app:compileDebugKotlin` 已通过。

- Android 一键登录入口又收掉了一层不必要前置拦截：登录页点击“本机号码一键登录”后不再先弹 `READ_PHONE_STATE` 运行时权限审批窗，而是保持用户同意协议后直接走现有网络 / SIM 环境预检、后端 fusion token 和阿里云融合认证 SDK 主链。仓库仍保留 manifest 里的 `READ_PHONE_STATE` 声明，先不同时删除，避免对当前融合认证 AAR / ROM 组合做过猛改动；后续以真机回归确认该声明是否还需要。同步删除已无调用的 `auth.fusion_permission_denied` 客户端日志口径，并更新当前状态 / 风险 / phone-auth runbook，避免后续窗口继续按“先弹电话权限”排障。

- Android 账号管理页退出 / 注销成功后的本地 UI 收口继续简化：`SessionApi.logoutCurrentSession()` 和 `requestAccountDeletion()` 成功后本来就会清本地 auth session、取消当前 SSE 并通过 `LoginGate` 的 auth invalid listener 自然切回登录页，因此账号管理页不再额外 `Activity.recreate()` 强刷整页。当前改为成功后只取消该账号的待发送 Work、收起设置页并交给登录门禁自然退回登录，避免整页重建造成的闪屏、状态重置和后续误把 `recreate()` 当成登录闭环必需步骤。`HamburgerMenuSheet` 内为此删除了仅服务旧重建方案的 `findActivityForHamburger()` 辅助函数；`./gradlew.bat :app:compileDebugKotlin` 已通过。

- 监控面板“正式上架检查”汇总口径补了一处状态一致性修复：后台 `launch_readiness` 之前把“注销申请”写成 `partial`，但前端汇总只按 `ready / attention / blocked` 统计，导致“需处理”数量和“下一步”选择可能漏算。现已统一为上线检查专用三态，前端汇总也改成把所有非 `ready`、非 `blocked` 状态都归到“需处理”，并新增后端单测锁住这条约束，避免后续再把 capability 的 `partial` 状态误混进 launch readiness。

- 今日农情线上 v53 探针 `runs=2` 得到 `ok_count=2/2`，每次 3 条可展示 item，未返回 reasoning tokens，单次 total tokens 约 7.1k-7.3k；来源名能写出，兼容 Chat 链路结构化 `source_count=0` 仍属预期。样本质量总体能跑通，但第一组摘要只有 76-79 个中文字符，偏薄。已把今日农情提示词小幅升到 `2026-06-11-v54`：仍不加后端字数过滤、不按内容拦截，只在提示词里要求摘要 90-130 字左右、尽量不低于 85 字，材料足够时补清楚进度、影响范围、农时窗口、风险点、供应变化或农资 / 流通影响，不能用套话凑字。v54 已部署到 ECS，生产探针 `runs=2` 继续 `ok_count=2/2`，6 条摘要约 94-112 字，无 reasoning tokens，单次 total tokens 约 7.16k-7.18k；第二组仍有一条综合价格类材料，后续靠探针和运营抽查观察，不为此恢复硬过滤。

- 阿里云云安全中心在 2026-06-11 07:49 左右报 3 条 CRITICAL“云助手异常命令”。只读排查确认事件命中的是本项目通过 Cloud Assistant `RunCommand` 下发的 `base64 | bash` 形态今日农情运维脚本：脚本会在 ECS 内读取 `DAILY_AGRI_JOB_SECRET` 并调用本机 `127.0.0.1` 的今日农情内部接口，云盾因此判断“命令内容包含恶意文本”。已把 Cloud Assistant 运维脚本统一改为先用 `SendFile` 投递脚本文件，再用短命令 `bash /tmp/*.sh` 执行；涉及 `check-ecs-readiness`、`deploy-ecs-server`、`deploy-ecs-admin`、`deploy-ecs-site`、`configure-ecs-daily-agri-job`、`query-ecs-logs`、`rollback-ecs-server`、`setup-sls-logging` 和 `harden-ecs-security`。本轮不在聊天或文档打印命令中的真实密钥。由于事件详情显示调用 AK 为主账号 AK 且调用 IP 与当前本机出口不完全一致，仍按“主账号 AccessKey 上线前必须轮换”处理，待用户确认后在控制台 / CLI 完成轮换并处理安全中心告警。

- 历史归档：记忆文档和今日农情提示词曾按“不要坑用户、不要过度压模型、适合普通群众”微调到 `2026-06-11-v53`，当时今日农情仍允许至少 2 条可展示 item 兜底。该数量口径已被本日顶部 `v63` “必须 3 条”的最新拍板替代。

- 管理后台监控面板登录口径继续校准：`账号登录` 能力从 `ready` 改为 `partial`，云端配置、账号ID迁移和日志入口已接入不等于一键登录 / 短信登录真机已验收；监控首页“登录与账号ID”卡片现在会在 24 小时无真实登录 session 时显示“待真机”，有 auth 异常时引导看 App 日志，避免产品侧看到绿灯误以为明天手机登录已经闭环。

- 历史归档：今日农情和记忆文档提示词曾按“不要坑用户、不要过度压模型、适合普通群众”收口到 `2026-06-11-v52` 并部署到 ECS，当时今日农情仍允许至少 2 条可展示 item 兜底。该数量口径已被本日顶部 `v63` “必须 3 条”的最新拍板替代；当时的记忆文档样本仍可作为历史质量参考。

- 历史归档：用户曾拍板“记忆、今日农情都不要后端内容过滤拦截，主要通过提示词把控方向，不能坑用户也不能过度压模型”，当时今日农情数量门槛一度放宽为至少 2 条可展示 item。该数量口径已被本日顶部 `v63` “必须 3 条”的最新拍板替代；“不恢复后端内容过滤、主要靠提示词和探针控制方向”的原则仍保留。

## 2026-06-10

- 官网合规页和公安备案 footer 补齐并已部署验证：`site` 新增 `/legal/user-agreement/` 与 `/legal/privacy-policy/` 两个静态页面，融合认证 SDK 协议 URL 改指向这两个独立页面；官网 footer 使用真实公安备案号 `京公网安备11010602202723号`、查询链接和本地警徽图标 `/gongan.png`。`scripts/deploy-ecs-site.ps1` 已补本地构建文件检查和远端验证，公网首页、`/gongan.png`、根域名 / www 的两条协议 URL 均返回 200，首页包含 ICP、公安备案号和公安查询链接。

- 一键登录真机风险继续压缩：`FusionOneLoginClient` 协议 URL 不再指向官网首页；全局 30 秒 timeout 若已进入服务端换号阶段，不再抢先 finish 失败，只上报 `auth.fusion_timeout_ignored`；`onSDKTokenUpdate` 若在主线程回调，直接返回当前 token 并上报 `auth.fusion_token_refresh_skipped`，避免同步网络请求卡住授权页。仍需真机完成本机号码一键登录和短信登录回归。

- 今日农情前端展示补边角：清空历史会同步清今日农情卡片锚点；当天卡片拉取失败后先 5 秒重试，之后每 15 分钟静默低频重试，避免当天后端补跑成功后 App 一直不出卡；卡片锚定延后到非 streaming / 非 active streaming item 时，避免插到正在生成的 assistant 后面扰动底部。近 30 天列表展示层去重、按生成时间和日期倒序、最多 30 条，这只是展示整理，不是内容过滤。

- 主对话记忆注入标签继续中性化：从旧“后台参考 / 后台摘要”改为“记忆摘要（仅供参考；用于上下文承接和减少重复追问；除非用户要求回顾历史，不要主动复述摘要内容、小标题或用户画像）”，避免把内部后台机制暴露给模型，同时保留“仅供参考、不要主动复述”的约束。
- 历史归档：今日农情联网链路和提示词曾在 `2026-06-10-v49` 收口到 `qwen3.5-plus + OpenAI 兼容 chat/completions + turbo`，并把“综合指数 / 综合行情 / 批发价格指数 / 菜篮子指数”压成背景材料。当前仍沿用同一模型和接口，但提示词、三条硬数量要求和线上探针结果以本日顶部记录、`current-status.md` 和 `today-agri-card.md` 为准。

- 历史归档：`POST /internal/jobs/today-agri-card/probe?runs=3` 曾在 ECS 生产环境用 `qwen3.5-plus + turbo + v48` 跑通，随后因综合批发指数 / 价格综述类内容偏多升级到 v49。该结果只保留为早期校准过程，当前生产探针以 2026-06-11 `v50` 记录为准。记忆文档也用生产环境样本做了不写库测试：新用户稀疏农业样本和产品 / 登录 / 提示词事务样本都能产出非空记忆摘要，约 1.4k-1.5k tokens，继续符合“能记多少记多少、不把一次性内容硬写成长期画像、不泄露系统机制”的折中口径。

- 历史归档：今日农情和记忆文档曾在本日早些时候收口到 `qwen-plus + DashScope text-generation/generation + turbo + enable_source + freshness=7 + prompt_intervene`，提示词版本 `2026-06-10-v36`。该条只保留为历史过程，已被 2026-06-11 顶部 `qwen3.5-plus + OpenAI兼容 chat/completions + turbo + v50` 当前口径替代。记忆文档提示词继续单文档四段“短期承接 / 长期背景 / 用户画像 / 农业重点事件”，参考旧 B/C 提示词补强“不机械拼接、旧稳定背景未否定继续保留、第三方转述保留来源和不确定性、关键参数照抄不换算、不泄露系统 / 模型 / 提示词 / API / token / 日志 / 运维信息”，并新增“四个方向只是阅读结构，同一线索不要在四段重复堆”的约束；后端仍不做内容硬过滤，非空模型输出直接覆盖写入。

- 历史归档：本日早些时候曾把后端部署到 ECS 并完成 `qwen-plus + turbo` 今日农情探针复核，`ok_count=5/5`，无 `reasoning_tokens`，样本里未发现链接下发、养殖水产词、广告词或“根据搜索结果”等元表达；但该结果属于旧生产候选验证，已被 2026-06-11 `qwen3.5-plus + OpenAI兼容 chat/completions + turbo + v50` 口径替代。记忆文档真实 `qwen-plus` 虚拟样本仍保留为有效复核：新用户少量信息、剂量面积、第三方转述和非农业 App 事务均能存住并守住“不换算、不确诊、不把转述当事实”的边界；单次 token 约 1.5k 到 1.65k，无 `reasoning_tokens`。

- 历史归档：今日农情提示词曾校准到 `2026-06-10-v29`，当时生产探针确认 `qwen-plus + text-generation + turbo` 能稳定返回可解析的 3 条卡片，但 v26/v27 样本里偶有旧日期材料混入、`source_name` 像文章标题 / 站点口号。该条只保留为历史过程，当前提示词以 2026-06-11 `v50` 为准。

- 统一记忆文档提示词做多维度校准：在“短期承接 / 长期背景 / 用户画像 / 农业重点事件”四段口径下，继续固定 `qwen-plus`、非流式、不联网、顶层 `enable_thinking=false`。本轮用农业连续问诊、旧记忆冲突、非农业 App 事务、剂量 / 面积、信息不足、稳定画像保留、地点不可靠、第三方转述等样本反复实测，重点拦住三类问题：把“1.5亩共8公斤”改成“8公斤/亩”、把“当地农技站说 / 朋友说”改成系统确认事实、把“倾向 / 可能 / 未见明显”写成诊断定论。提示词已补“旧长期背景和画像未被否定时继续保留”“宽过滤，不因担心误记就把有用线索整段删掉”“第三方转述保留来源和不确定性”“数量 / 面积 / 倍数 / 浓度 / 时间照抄不换算”的约束，并新增单测读取真实提示词文件做防回归检查。后端完全放开落库，只清理外层代码块和空白，不做四段解析、缺段保留或内容关键词硬过滤；典型测试样本单次记忆提取约 1.4k-2.0k tokens，上线后仍按日志观察真实用户长对话成本和质量。

- 清理已废弃 Flash 实验入口：删除旧本地诊断脚本 `scripts/verify_model_url.js`，避免继续保留 `qwen3-vl-flash` 图片诊断路径误导后续窗口。当前仓库真实模型链路仍只有三条：主聊天 `qwen3.5-plus`，记忆文档摘要 `qwen-plus`，今日农情 `qwen3.5-plus`；轻量 Flash 候选不再作为摘要、今日农情或可执行本地诊断方案。

- 管理后台“模型问诊”上线就绪判断改为看真实问诊证据：后端 `/admin-api/v1/monitoring` 不再只因为 healthz 里 `api=ok / bailian=ok` 就把模型问诊标为 ready；最近 24 小时没有真实问诊时显示“需处理”，只有文字问诊时仍提示图片问诊 / 模型拉图需要真机验证，最近 24 小时同时有文字和图片问诊记录才标 ready。这样能避免管理层看到模型 Key 健康就误以为主聊天和图片问诊已经完整验收。

- 账号管理退出 / 注销本地收口补强：Android `SessionApi.logoutCurrentSession` 成功后不再只清本地 auth token，而是和登录失效 / 注销申请共用本地账号运行时清理，清除 session generation、递增 runtime generation 并取消当前 SSE call，避免用户点“退出设备”后旧模型流或旧回调继续落回界面；账号管理页注销确认弹窗按钮从“确认注销”改为“提交并退出”，正文明确“提交申请后会立即退出当前账号”，继续保持“注销申请队列 + 后台合规处理”的语义，不写成自动物理删除完成。

- 用户拍板删除轻量摘要候选和独立长期记忆层：后端摘要链路收敛为一份自然语言记忆文档，物理字段暂沿用 `session_ab.b_summary`，对外返回 `memory_document`，旧长期记忆列只通过迁移 SQL 合并并删除。摘要模型固定为 `qwen-plus`，非流式、不联网、顶层 `enable_thinking=false`，不再保留分层灰度环境变量或轻量模型候选；提示词真源改为 `server-go/assets/summary_extraction_prompt.txt`，输出“短期承接 / 长期背景 / 用户画像 / 农业重点事件”四段，失败只保留 `pending_retry_b`。管理后台监控页模型口径同步改为主聊天、记忆文档摘要、今日农情三行。

- 本轮后端已部署到 ECS 双端口 slot：`scripts/deploy-ecs-server.ps1` 远端执行 `go test ./...`、编译、启动非当前 slot、切换 API 和后台 `/admin-api/` Nginx 上游并返回生产 healthz；随后 `scripts/check-ecs-readiness.ps1` 显示 active upstream 为 `3000`、`nongji-server-3000 active/enabled`、后台 upstream 同为 `3000`、HTTPS healthz 200，且 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / redis=ok / upload_storage=oss`，未登录访问 `/admin-api/v1/auth/me` 返回 401。部署 / readiness 输出只显示 set/empty，不打印真实 token、AccessKey、模型 Key 或密码。

- 历史归档：今日农情生产口径曾先固定为 `qwen-plus + DashScope text-generation/generation + turbo`，并在 `2026-06-10-v26` 阶段要求正好 3 条、一行标题、约 3-4 行摘要和短来源名。该模型和接口口径已被 2026-06-11 顶部 `qwen3.5-plus + OpenAI兼容 chat/completions + turbo + v50` 口径替代；历史里关于 v29 / v36 / v49 的全网宽搜、种植侧、养殖水产不要和后端低风险结构兜底，已吸收到 v50 主提示词和当前解析边界里。

- 历史归档：管理后台监控页早些时候新增“模型调用口径”卡片时，后端 `/admin-api/v1/monitoring` 曾列出今日农情 `qwen-plus + search_strategy=turbo` 强制联网，并一度细化为 DashScope text-generation 非流式、`freshness=7`、`prompt_intervene`。该条已被当前 `qwen3.5-plus + OpenAI兼容非流式 + forced_search=true` 替代；主聊天仍是 `qwen3.5-plus + search_strategy=turbo` 可联网但不强制，记忆文档摘要固定 `qwen-plus`。

- 历史归档：今日农情降本模型曾只做候选评估，不切生产。根据客服口径补测过轻量模型的多模态流式联网链，生产 ECS 当时能返回 200 并生成 3 条卡片；缺口是来源列表不如当时的 `qwen-plus + Generation + enable_source` 稳定，且输出仍可能包代码块。当前代码已删除这条轻量实验链，今日农情固定 `qwen3.5-plus`，来源名靠模型 JSON 的 `source_name`，结构化 URL 只作为内部追溯字段。

- 历史归档：曾修正 DashScope 兼容模式关闭思考参数，确认 `enable_thinking=false` 必须放在 HTTP 顶层，不能放回 `extra_body`。当时用 1-2k 字农业样本实测过旧分层摘要提示词，发现长期记忆容易把“更像 / 不能排除”升级成偏确定判断，因此后来提示词持续加入防诊断升级护栏。当前摘要链路已收敛为固定 `qwen-plus` 的单份记忆文档，继续保留顶层关闭思考和防诊断升级要求。

- 今日农情聊天页展示从“发送后退出”改为“视觉时间线系统卡片”：卡片仍不是 `ChatMessage`，不进本地快照、A 层滑窗、记忆文档、归档、摘要、重试或问诊扣次；当天 ready 卡片加载时若没有真实消息，就作为首屏第一条视觉内容排在顶部，已有真实消息时锚在当时最后一条真实消息后方。用户后续发送文字 / 图片 / 失败态消息时，卡片不再隐藏或播放 180ms 退出动画，新消息自然追加在卡片后方并把它往上顶；首屏只有今日农情时列表用 Top 排列展示，但工作线触线判断、自动跟随和最新真实消息锚点仍只看真实消息。

- 历史归档：今日农情曾继续保留 `qwen-plus + turbo + enable_source` 生产链路，不切低价模型。当时生产探针确认 `qwen-plus` 质量更稳，旧 text-generation 路由直接套轻量模型会返回参数错误。后续已按官方 OpenAI 兼容 Chat 联网口径切到 `qwen3.5-plus`，生产与手动补跑都固定走当前新链路。

- 注销申请后台补 15 个工作日 SLA 可视化：后端列表 / 创建 / 状态更新响应会返回 `due_at` 和 `overdue`，监控面板新增 `account_deletion_overdue`，后台注销申请页展示“处理期限 / 剩余天数 / 超期天数”，监控队列会把超期申请标为红色待处理。该改动只增强申请队列和合规处理提醒，不代表已经自动物理删除或匿名化全部账号数据。

- Android 真机排障和今日农情跨天边角继续补强：融合认证协议页承接页现在会在协议 URL 缺失 / 非法、非法跳转、主页面加载失败时上报 `auth.fusion_protocol_url_unavailable`、`auth.fusion_protocol_navigation_blocked`、`auth.fusion_protocol_load_failed`，只带 reason / scheme / WebView 错误码，不上传完整协议 URL；今日农情聊天页跨上海日期后会先清掉旧日期卡片，并只接受 `date_cn` 等于当前日期的 ready 卡片，避免 App 长时间前台或后台恢复后继续显示昨天内容。该改动不改变一键登录 100001 最终 `onVerifySuccess` 换号主链，不改今日农情模型 / 提示词 / 后端生成链，也不碰聊天滚动主链。

- 管理后台 SLS 状态文案对齐真实进度：监控 API 和后台服务健康页不再把 SLS 告警说成“未接 / planned”，统一改成“已有 5 条 AlertHub 最小告警；云监控资源水位已接，SLS 邮件行动策略和仪表盘随后已补”。该改动只修后台可见状态和上线检查口径，不改变 SLS 规则本身。

- 补充旧 Codex 线程读取和落库口径：用户提供线程 ID（例如当前长窗口 `019e8930-5af0-73c2-bffd-7777edfb5476`）时，后续窗口应优先用线程读取工具提取旧窗口摘要和关键回合；但线程内容、压缩摘要和外部模型转述仍只能作为线索，必须对照当前仓库文档、代码、git 历史和线上状态核验后，才允许更新项目记忆或修改业务代码。若用户没给线程 ID，不能假设新窗口天然知道所有旧窗口，只能从最近线程列表或仓库记忆里搜索线索。

- 接入 SLS 最小 AlertHub 告警并沉淀运维脚本：新增 `scripts/setup-sls-alerts.ps1`，按阿里云 CLI 幂等创建 / 更新 5 条规则：`nongji-server-5xx`、`nongji-server-slow`、`nongji-nginx-upstream`、`nongji-daily-agri-failed`、`nongji-model-auth-config`。脚本支持 `-DryRun`，Windows PowerShell 下会对 JSON 对象参数做转义以避免阿里云 CLI 丢引号；当时云上规则均为 `ENABLED`，当时仅进入 SLS AlertHub，不绑定短信、电话、机器人、邮件或自定义 action policy。同步更新 `logs-sls.md`、主规则和项目状态：SLS 不再是“完全没告警”；后续已补 SLS 邮件行动策略和最小仪表盘，资源水位另由云监控邮件承接，DYPNS 用量和模型成本告警仍要继续补。

- 登录排障 runbook 补“两个登录方式同时失败”的共同闭环分支：如果一键登录和短信登录同时失败或都触发闪退，后续不要只盯运营商取号 SDK，应优先检查生产 API 可达性、debug / release 同包名同签名同后端、session token 本地写入、`IdManager` 切账号ID、Compose / Activity 生命周期重复触发，以及登录成功后主界面跳转、hydrate、权益拉取是否抛异常；后台 `auth.*` 只能定位阶段，最终仍需结合 `AndroidRuntime` logcat 看共同崩溃栈。

## 2026-06-09

- 根 `AGENTS.md` 补充“压缩摘要不是真相源”规则：聊天窗口、线程摘要、中转站压缩摘要和外部模型转述只能作为线索，后续执行必须回到仓库文档和当前代码核验；凡是影响正式上线、SDK 接入、模型策略、云资源状态、排障结论或用户拍板的长期口径，都要沉淀到 `AGENTS.md`、`docs/project-state/*`、ADR 或 runbook，避免新窗口因上下文压缩失忆而把旧方案改回来。

- 今日农情提示词再按“固定 3 条、宽搜索、少硬过滤”收口：后端提示词版本升到 `2026-06-09-v22`，JSON 示例直接给满 3 条，规则明确 `items` 必须正好 3 条、标题 12-16 个中文字符一行读完、摘要约 3-4 行体量。检索仍用 `qwen-plus + turbo + enable_source + freshness=7`，不传 `assigned_site_list`，不限定固定网站；种植侧、养殖水产不要、排除广告软文 / 导购 / 假新闻 / 标题党主要靠提示词和内部探针控制，后端只保留 JSON 结构、正好 3 条、标题摘要非空、私网 / 明显电商 URL 清洗等低风险兜底。Android 读取和渲染继续要求正好 3 条，聊天页不提供链接点击、关闭叉号或“今日不再显示”，用户发送消息时先退出卡片再插入用户消息。

- 历史归档：今日农情提示词曾按“不限制固定网站”收口，当时根据阿里云百炼联网搜索文档不传 `assigned_site_list`，用 `qwen-plus + turbo + enable_source + freshness=7` 做全网宽搜，并在 `prompt_intervene` 和主提示词里明确不要只围绕少数官网 / 媒体、不要按站点白名单思路检索。该链路已被当前 `qwen3.5-plus + OpenAI兼容 chat/completions + turbo` 替代；种植侧、普通天气、畜牧水产养殖、广告软文和低质内容仍主要靠提示词、内部探针和后台运营抽查控制，后端发布端仍只做低风险结构兜底。

- 历史归档：今日农情联网模型边界曾按当时生产 ECS 探针修正为 `qwen-plus + turbo` 原生 Generation 强制联网链；中间短暂评估过低价模型和其它接口组合。当前代码只保留 `qwen3.5-plus + OpenAI兼容 chat/completions + turbo` 生产链路，不再保留轻量模型实验配置、其它接口候选或环境变量切换入口。

- 历史归档：今日农情曾短暂改为 `qwen-plus + turbo` 且发布 `2 到 3 条有效 item` 的宽口径。该条已被 2026-06-09 的固定 3 条方案替代，随后又被 2026-06-10 顶部 `v29` 口径继续放宽后端过滤：现在仍展示正好 3 条，但只做 JSON 可解析、标题 / 摘要非空和私网 / 本机 URL 安全兜底，方向主要靠提示词和探针控制。

- 历史归档：今日农情卡片曾短暂采用“发送时淡出退出”方案。该方案已被当前“视觉时间线系统卡片”替代：当天 ready 卡片按加载时的真实消息尾部锚定，没有真实消息则作为首屏第一条视觉内容；用户后续发送文字 / 图片 / 失败态消息时，卡片不再隐藏或播放退出动画，新消息自然追加在卡片后方并把它往上顶。卡片右上角不放关闭叉号，也不提供手动“今日不看”入口，仍不进入本地聊天快照、记忆文档、归档、扣次或后端上下文。

- App 自动日志继续补上测试包 / 正式包区分：Android 自动日志 payload 新增 `build_type=debug/release`，后端 `client_app_logs` 新增幂等迁移列和查询过滤，后台 App 日志页新增“包类型”筛选并在日志版本列显示 debug / release。该能力只用于明天真机排障和验证“测试包除预览面板外与正式包一致”，不改变登录、检查更新、聊天、图片、会员或额度业务链路，也不上传手机号、token、APK URL、图片 URL、聊天正文或礼品卡完整码。

- App 自动日志补上整组和版本 / 设备排障筛选并已部署：后端 `GET /admin-api/v1/app-logs` 和内部 `/internal/app/logs` 新增 `event_prefix`、`platform`、`app_version_code`、`app_version_name`、`os_version`、`device_model` 查询参数，精确 `event` 优先于前缀筛选；后台 App 日志页新增“event前缀”、平台、版本号 / 版本名、系统和设备输入框，监控页登录排障 / 检查更新排障增加“全部登录日志 / 全部更新日志”按钮，真机回归清单里的登录和检查更新项也能直接跳到 `auth.*` / `app_update.*` 整组日志。该能力只增强后台排障查询和审计 detail，不改变 Android 日志上报、不展示手机号、token、APK URL、图片 URL、聊天正文或礼品卡完整码。本轮已通过 `scripts/deploy-ecs-server.ps1` 部署后端并切到 active upstream `3001`，`scripts/check-ecs-readiness.ps1` 显示 HTTPS healthz 200 且 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / redis=ok / upload_storage=oss`；后台静态前端也已通过 `scripts/deploy-ecs-admin.ps1` 部署到 `https://admin.nongjiqiancha.cn/`，公网首页 200，未登录 `/admin-api/v1/auth/me` 返回 401。

- 管理后台监控页新增“明天真机回归清单”：前端在 `GET /admin-api/v1/monitoring` 既有数据上，把一键登录 / 短信登录、主聊天文字问诊、图片问诊 / 弱网发送、礼品卡兑换会员、今日农情显示、检查更新、帮助与反馈整理成一组可点击回归项；每项会按现有健康、App 日志、24h 新登录 session、问诊记录、礼品卡兑换记录、反馈消息、今日农情和更新日志给出“待真机 / 有登录 / 有记录 / 有兑换 / 有检查 / 看日志 / 先生成卡”等状态，并直达 App 日志、服务健康、礼品卡、今日农情、检查更新或帮助反馈页面。该清单只辅助明天真机测试和管理层试用排障，不替代真实 Android 回归，也不改变任何 App / 后端业务接口。

- 管理后台订单页从占位推进到只读核查入口：后端新增 `GET /admin-api/v1/orders`，授权角色可按账号ID筛选或留空查看最近订单 / 会员变更记录；前端订单页新增最近订单 KPI、成功 / 失败统计、金额粗略合计和账号ID列，导航文案改为“只读核查”。该能力只读读取现有开发期 `orders` 表，继续明确真实微信 / 支付宝支付、回调、退款、对账和自动补发权益未接入，不开放补发、退款或手动改权益按钮。

- 历史归档：今日农情坏缓存兜底曾按 `2 到 3 条` 结构处理，并让 Android 标题最多 2 行、摘要完整展示。该条已被后续固定 3 条展示口径替代；当前 v29 仍要求用户侧展示正好 3 条，标题一行、摘要最多 4 行，坏 ready 缓存仍不会打成 500。

- 一键登录失败提示继续按真机排障口径收口：Android SDK 初始化、授权页拉起、取号校验、超时等一键登录失败现在统一提示用户先关闭代理 / VPN、打开移动数据并确认默认数据卡，也可直接走验证码登录；后台监控“登录排障”卡同步加了这条检查顺序。该改动只改用户提示和后台说明，不改 100001 最终 `onVerifySuccess` token 登录主链、不调用半程 verify，也不改短信登录接口。

- 账号管理补上“清理本机缓存”真实入口：Android 新增 `LocalAppCacheCleaner`，设置页账号管理内可一键清理 `cacheDir/app_updates` 检查更新下载残留和 `cacheDir/composer_camera` 外部相机临时文件；清理动作跑在 IO 线程并做 cache 根目录边界校验。该入口不删除登录态、后端聊天历史、记忆文档、会员权益、礼品卡、帮助反馈、待发送 WorkManager 图文、`files/composer_images` 私有上传副本或 `chat_ui_cache` 草稿 / 快照，和“删除所有历史对话”保持清晰分工。

- 检查更新链路补上真机排障闭环：Android 会把检查开始、有新版本、手动检查无新版本、检查失败、需要安装未知应用权限、开始下载、下载失败、安装页打开失败、已拉起系统安装页等阶段用 `app_update.*` 自动日志上报；下载器同步返回更细失败原因，包括网络 / HTTP、非 HTTPS 跳转、文件过大、大小不一致、SHA-256 不一致、包名不一致、versionCode 不一致或未升版本、安装页 intent 打不开等。管理后台 `GET /admin-api/v1/monitoring` 新增 `app_update_logs` 聚合，监控页新增“检查更新排障”卡和直达 App 日志筛选按钮；日志只存阶段、版本号、是否强更、物料是否配置、失败原因和 HTTP 状态，不上传 APK URL、SHA-256、手机号、token 或密钥。

- 管理后台“产品洞察”从占位页推进到首版脱敏聚合报表：后端新增 `GET /admin-api/v1/insights`，前端“产品洞察”页展示今日 / 24h / 7d / 30d 用户增长、登录 session、问诊、图片问诊、App 异常、登录排障、反馈、礼品卡和今日农情失败趋势，同时展示反馈主题固定关键词命中、App 事件分类、Top App 事件和礼品卡失败原因。该接口只返回计数、比例、事件名和固定分类，不返回聊天全文、反馈正文、图片 URL、手机号、token、模型 Key 或礼品卡完整码；监控能力卡同步把“产品洞察”从 planned 改成 partial，表示首版可看，但洞察日报、人工标签、代表短摘、处理状态和独立报表表仍待补。

- 新增 Android debug / release 构建一致性护栏：`scripts/check-android-build-parity.ps1` 会检查 Android 构建固定走 `https://api.nongjiqiancha.cn`、`USE_BACKEND_AB=true`、debug 有 release 签名时同签名并开启融合短信登录、release 必须开启一键登录、debug 不允许另加 manifest / 明文网络分叉、100001 一键登录只把最终 `onVerifySuccess` token 交给 `/api/auth/fusion/login`、不在半程回调消费 token，以及 debug-only 预览面板必须由 `BuildConfig.DEBUG` 隔离。GitHub Android CI 已接入该脚本，后续再改登录、构建或网络安全配置时会自动挡住“测试包和正式包业务链路又走偏”的改动。

- 管理后台登录排障按钮继续细化：此前“一键登录失败”按钮只筛 `auth.fusion_verify_failed`，会漏掉取 fusion token、SDK 初始化、授权页拉起、SDK token auth、服务端换号、超时和授权页未完成等真实失败阶段；现在登录排障卡把这些阶段拆成独立筛选按钮，并把短信拆成“短信发送失败 / 短信登录失败”，方便明天真机测试时直接定位卡在哪一步。

- 监控面板把登录排障继续拆细：`GET /admin-api/v1/monitoring` 的 `auth_logs` 新增并展示 `env_blocked`、`env_warnings` 和 `login_network_failures` 三个计数，前端“登录排障”卡增加“环境不满足 / 可疑环境 / 请求网络失败”小指标和直达 App 日志筛选按钮，待处理事项也会单独提示 SIM / 移动数据 / 默认数据卡 / VPN / 生产 API 可达性问题。这样明天真机测试时，后台不只看到“登录失败”，而是能先判断问题更像手机环境、代理网络、SDK 授权页还是服务端 token 校验。

- 加固 Android 登录前排障和后台操作反馈：一键登录现在会在拉取 fusion token 前先做 App 可控的网络 / SIM 环境预检，无网络、无 SIM、没有蜂窝模块、SIM 未就绪会直接回落验证码登录并上报 `auth.fusion_env_blocked`；VPN / 无蜂窝等可疑环境会上报 `auth.fusion_env_warning`，让后台能区分“环境不满足”和“SDK / 服务端校验失败”。同时按阿里云 SDK FAQ 补 `CHANGE_NETWORK_STATE` 普通权限，并给移动 / 联通 / 电信取号网关 `onekey.cmpassport.com`、`enrichgw.10010.com`、`uac.189.cn` 做域名级明文放行，App 其他网络仍默认禁止明文 HTTP。短信 / 一键登录服务端失败也会按网络、HTTP 状态和后端错误码给出更明确提示并上报脱敏日志，不上传手机号、验证码、verify token 或完整 URL。后台今日农情补跑、检查更新停更、礼品卡作废、反馈状态更新等写操作补了按钮忙碌态、重复点击保护和失败弹窗；监控“App质量”把登录失败和闪退补报纳入判断，长账号ID / 卡码 / 错误字段会自动换行，减少管理层试用时看不懂或点了没反应。

- 补强登录排障和闪退补报链路：Android 新增最小 `AppCrashReporter`，进程崩溃时只在本机保存异常类名、顶层代码位置、线程名、登录阶段和时间戳，下次启动后自动通过现有 App 自动日志上报；未登录 / 登录页阶段统一走 `auth.app_crash` 预登录日志，已登录运行阶段走 `app.crash`，不上传完整堆栈、手机号、验证码、token、URL、聊天正文或图片内容。一键登录、短信发送和短信登录都会设置崩溃阶段标记，方便定位“点登录就退”到底崩在 SDK 初始化、拉授权页、服务端校验还是短信流程。

- 监控面板新增“登录排障”卡：`GET /admin-api/v1/monitoring` 新增 `auth_logs`，并把认证失败、闪退补报加入今日 / 24h / 7d 窗口和待处理事项。后台前端会展示最近 24 小时认证失败、一键登录失败、短信失败、登录前日志、闪退补报、最近出现时间和 Top 事件，并提供“登录前日志 / 一键登录失败 / 短信失败 / 登录闪退 / 普通闪退”按钮直接跳转 App 日志筛选。后端聚合对空日志返回 0 和 `[]`，避免生产库无日志时监控页 500。

- 明确双卡 / 代理 / 网络切换口径：一键登录依赖运营商网关取号能力，双卡手机按当前默认移动数据卡 / 当前蜂窝数据链路处理，App 不承诺在授权页里选卡；用户要用另一张卡应先在系统里切默认移动数据卡，或者直接走验证码登录。VPN / 国外代理、纯 Wi-Fi、移动数据关闭、SIM 卡欠费 / 未激活、网络切换中都可能导致 SDK 环境检测或授权页失败，后续真机排障优先看后台“登录排障”和 `auth.*` / `auth.app_crash` 自动日志。

## 2026-06-08

- 补齐登录前自动日志链路：此前 `POST /api/app/logs` 需要账号鉴权，导致一键登录拉起失败、SDK 初始化失败、最终 token 校验失败、短信发送失败这类“还没拿到账号 token”的问题无法进入后台 App 日志。后端新增 `POST /api/app/logs/preauth`，只允许 `auth.` 前缀事件并统一写为 `user_id=preauth`，继续使用 8KiB body 上限、字段清洗、脱敏 IP 和 Redis / 单进程短期限流；Android 一键登录和短信登录失败点会 fire-and-forget 上报 `auth.fusion_*`、`auth.sms_*`，只带阶段、节点和错误码等安全字段，不上传手机号、verify token、短信验证码、URL、正文或图片内容。Android 端也补了敏感 value 过滤，字段值里出现 URL、token、AccessKey 或连续 11 位数字时直接丢弃。

- 继续把测试包和正式包登录链路收成一致：本机存在固定 release 签名配置时，`debug` 构建也使用同一把 release 签名并开启 `ENABLE_FUSION_ONE_LOGIN`，因此 Android Studio 直接 Run / 打 debug 包也按 `com.nongjiqiancha + 正式签名 + 正式 HTTPS 后端` 走一键登录；只有缺少 release 签名配置的环境才关闭一键登录、退到验证码登录。正式 release 包仍强制要求 release 签名和 HTTPS 后端。阿里云融合认证授权页改用 `startSceneWithTemplateId(..., AlicomFusionAuthUICallBack)` 接入自定义 UI，拉开手机号、登录按钮、其他手机号登录和协议区位置，SDK 协议勾选框保持可见且默认未勾选；当前 100001 融合短信主链不再调用半程 verify-only，不再提前消费 token，只在最终 `onVerifySuccess` 后调 `/api/auth/fusion/login`。短信后端同步收紧为最小参数：默认模板变量只保留 `{"code":"##code##"}`，不再默认传 `SchemeName=农技千查`，`DYPNS_SMS_SCHEME_NAME` 只有显式配置才发送，降低阿里云 `SendSmsVerifyCode` 返回“非法参数”的风险。

- 修复 Android 本机号码一键登录“一点就退”崩溃：根因是 `FusionOneLoginClient` 在 `AlicomFusionBusiness.initWithToken(...)` 之前调用 `setAlicomFusionAuthCallBack(...)`，阿里云融合认证 SDK 内部 `authProxy` 尚未创建时被解引用导致 NPE。现按 SDK 实际依赖顺序先 `initWithToken` 初始化并适配页面，再挂认证回调；场景启动、继续、停止和销毁都加了安全兜底，SDK 初始化 / 拉起失败时切回验证码登录，不再让 App 进程崩溃。已用本地 AAR 字节码确认 `initWithToken` 会创建 `authProxy`，`setAlicomFusionAuthCallBack` 会使用该对象；有固定 release 签名配置的 debug 包和 release 包都会启用一键登录，缺签名配置的本机 debug 包才回落验证码登录。最终融合短信登录真实手机号回归仍需用户在关闭代理、优先手机流量条件下继续确认。

- 轻量收紧 Android 登录页视觉：`LoginScreen.kt` 将品牌黑圆内绿色叶片前景继续放大，让叶片更贴近外圈；协议勾选框不再用字体对号，改为 Canvas 两段线绘制的常规勾；“我已阅读并同意《服务协议》《隐私政策》”收成单个单行可点击协议文本，减少真机窄屏换行。该改动只影响登录页 UI，不改一键登录 / 短信登录 / 协议内容 / 后端认证逻辑。

- 修复后台管理页 `overview / monitoring / users / entitlements` 一批 `500 internal_error`：根因是生产 MySQL 老表大量仍是 `utf8mb4_unicode_ci`，而 MySQL 8 新连接默认更偏 `utf8mb4_0900_ai_ci`，后台最近新增的 `tier/status/sender_type` 比较和 `COALESCE(..., 'free')` 一类 SQL 在生产上触发 `Illegal mix of collations`。后端 `OpenDB` 现统一把连接默认 collation 钉为 `utf8mb4_unicode_ci`，并补了单测覆盖 raw DSN / mysql:// URL / 显式 override 三种入口，避免今后后台再因为连接级排序规则飘掉而随机炸页。

- 继续收口后台口径和正式默认文案：`GET /admin-api/v1/entitlements/summary` 新增 `account_member_users`，把“已收敛账号会员”和“迁移期老ID会员”拆开返回；后台会员额度页同步改成更实话实说的展示，避免账号ID收敛期把“账号用户”“账号内会员”“当前会员总数”混成一个口径。礼品卡汇总新增 `redeemable_count`，礼品卡页顶部 KPI 从“可用卡”改成真正的“可兑换卡”，并明确全量 active 卡与当前可兑换卡不是一个口径；后台客服回复成功后，会话也会顺手把当前 admin 用户名写入 `support_conversations.assigned_to`，减少“明明回复过了但处理人还是空的”这种运营脏状态；帮助与反馈详情页现在会直接渲染截图缩略图并支持新页打开原图，不再只写“有几张图但不展开”。Android 侧把更新弹窗默认文案收成长期通用正式版：标题改为“版本更新”，无 release notes 时默认显示“修复已知问题，优化使用体验。”，并把“已是最新版本”收成“当前已是最新版本”；debug 预览面板里的“检查更新”描述也同步更新到最新口径，避免预览和正式页文案漂移。

- 继续收紧监控和客服状态流：后台总览、监控窗口、地区分布里的用户数改为优先按 `user_id_migrations` 归并后再去重统计，已迁移老 `user_id` 会并到账号 ID，未迁移老 ID 也不会被直接漏掉；前端文案同步改成“去重用户”，避免把账号收敛期的盘子说得太满。帮助反馈会话在后台回复后，如果之前是 `closed`，现在会自动转回 `replied` 并清掉 `closed_at`，不再出现“已经回复但仍显示已关闭”的假状态。礼品卡批次表也把原来的“未用”列明确改成 `active（未兑）`，避免和顶部“可兑换卡”口径混淆。

- 今日农情和 App 更新继续往正式链路收口：Android 设置页现在会在进入主界面后静默请求一次 `GET /api/app/update`，如果服务端返回更高版本且当前设备还没对这个 `latest_version_code` 看过弹窗，就自动弹一次“发现新版本”；用户点“稍后 / 立即更新”即可，之后同一版本号不再重复骚扰，主链仍不是系统通知。今日农情后台新增 `POST /admin-api/v1/today-agri/generate`，`owner / content_ops` 可直接补跑；ECS 侧新增 [configure-ecs-daily-agri-job.ps1](D:/wuhao/scripts/configure-ecs-daily-agri-job.ps1) 安装 systemd service + timer，生产主线改为“云端定时生成 + 后台人工补跑兜底”。

- 历史归档：当时曾把今日农情临时收成 `qwen3.5-plus + Responses web_search` 过渡方案，并记录“没有改成 `qwen-plus`”。该条后来被 `qwen-plus + turbo + enable_source + freshness=7` 原生 Generation 强制联网链替代；当前又已切到 `qwen3.5-plus + OpenAI兼容 chat/completions + turbo + forced_search=true`，仍与主聊天 `chat/completions + enable_search=true + search_strategy=turbo + forced_search=false` 分开。

- 检查更新从“只读看板”推进到“后台可操作发布”：新增数据库表 `app_release_configs`，`/api/app/update` 现在优先读取后台写入的 Android 发布配置，无记录时才回退 `APP_ANDROID_*` 环境变量；后台新增 `POST /admin-api/v1/app-update/android`，`owner / release_ops` 可直接维护 versionCode、版本名、HTTPS APK、SHA-256、文件大小、更新说明、强制更新和停更状态，页面同步展示配置来源、最后操作人和最后更新时间。监控面板的安装包更新卡也会显示“已启用 / 已停更”，不再把“有物料但当前停更”误读成已经对外发布。当前主链仍是用户手动点“检查更新”，系统自动推送还未接通知权限和推送服务。

- Android 和后台继续按正式上架口径收口：`build_apk.bat` 默认改为打正式签名 release 包，不再默认产出 debug 包充当测试安装包；debug manifest 不再单独放开明文 HTTP；主聊天去掉“后端未配置时本地 fake stream”这类 debug 业务兜底，只继续保留 debug-only 预览面板。后台新增 `GET /admin-api/v1/entitlements/summary`，会员额度页不再只会按账号ID查单人，而是能直接看注册用户、会员总数、Free / Plus / Pro 分布、7 / 30 天内到期、今日基础额度用满、有加油包余额和有升级补偿人数。Android `/api/me` 现会在当前有效会员正对应礼品卡兑换记录时返回 `membership_source=gift_card`，会员中心顶端会显示“礼品卡开通 · 当前档位每日 25 / 40 次”，礼品卡兑换成功卡片也会直接写出档位、30 天和每日次数；debug 预览面板同步改成“账号短 ID / 礼品卡开通 / 真实成功文案”口径，不再沿用旧本机 ID、空成功卡壳或模糊权限说明。监控面板里“登录与账号ID”也从“登录正常”改成“服务配置正常，真机回归为准”，避免把配置健康误说成真机已验证。

- 管理后台监控面板继续往正式运营视角收口：`GET /admin-api/v1/monitoring` 新增 `user_regions`，按账号最近一次已识别地区拆出“注册用户地区”和“当前会员地区”两块；前端监控页把原来的“地区分布”明确成“最近 30 天问诊地区分布”，并新增“用户地区概览”卡组，分别展示注册用户和当前有效 Plus / Pro 会员的大概地区盘子、已识别覆盖数和 Top 地区。这里用的是账号最近地区做近似经营视角，不把它包装成精确注册地。同步更新后台 runbook 和项目状态记忆，避免后续窗口还按旧口径理解监控页。

- 长期协作提示词继续收口为稳定版本：项目级 [docs/opencode-codex-bridge.md](D:/wuhao/docs/opencode-codex-bridge.md) 新增“长期协作默认模式”，明确用户默认是业务负责人、模型承担技术负责人角色、除非明确只审查否则默认直接落地、最终回复固定按“改了什么 / 为什么这样改 / 验证了什么 / 还有什么风险或下一步”收口，以及凡有仓库改动默认同步项目记忆并提交推送到 GitHub `origin/master`。根 [AGENTS.md](D:/wuhao/AGENTS.md) 同步镜像这套口径，避免规则只留在聊天里或只留在本机工具配置里；项目级 `opencode.json` 无需改动，仍继续加载该桥接文档。

- 后台手机号回访能力补齐：`app_accounts` 新增加密 `phone_ciphertext` 字段，用户下一次一键登录或短信登录时会用 `APP_SECRET` 派生密钥加密保存完整手机号；后台用户列表 / 用户详情和帮助反馈会话对授权角色返回完整手机号并提供复制入口，用户管理和帮助反馈搜索框输入完整 11 位手机号时会在服务端按 `phone_hash` 精确匹配。账号ID仍是全局业务主键，手机号只作为登录凭证和回访线索；日志、审计 detail、SLS、Redis、文档和聊天输出仍不得打印完整手机号，旧账号需下次登录后才能补齐完整号。

- 继续按正式上架路线收口：Android `UPLOAD_BASE_URL` 固定为 `https://api.nongjiqiancha.cn`，`USE_BACKEND_AB` 固定开启，debug 包和 release 包业务后端保持一致，区别只保留 debug-only 预览面板和调试日志；管理后台把监控状态文案统一为“就绪 / 需处理 / 阻塞”，并继续补空数据兜底、角色写操作隐藏、帮助反馈默认待回复队列、礼品卡激活账号跳转用户详情、账号ID迁移时同步重建反馈会话索引。后端空批次 / 空卡 / 空兑换尝试 / 空用户明细相关列表统一返回 `[]`，避免后台页面因空数据读取 `.length` 崩溃；用户兑换礼品卡时不再解密完整卡码，完整码只用于后台受权限查看。

- 本轮生产部署已完成：`scripts/deploy-ecs-server.ps1` 将后端部署到 ECS 双端口 slot，当前 Nginx active upstream 为 `3001`，`scripts/check-ecs-readiness.ps1` 显示 HTTPS healthz 200 且 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / redis=ok / upload_storage=oss`；`scripts/deploy-ecs-admin.ps1` 已重新部署 `https://admin.nongjiqiancha.cn/`，公网首页 200，未登录访问 `/admin-api/v1/monitoring` 返回 401。部署和检查输出不打印真实 token、AccessKey、模型 Key 或密码。

- 管理后台继续往正式可用推进：礼品卡新增 `code_ciphertext` 加密字段，新生成完整卡码会加密保存，后台礼品卡列表可直接查看和复制，仍用 hash 做兑换校验且不把完整卡码写入日志 / 审计 detail / 文档；帮助反馈新增 `support_conversations` 轻量会话状态表，后台支持待回复 / 已回复 / 已关闭队列、账号ID / 脱敏手机号 / 最近消息搜索、回复、关闭、标已回复和重开；监控面板新增“就绪 / 需处理 / 阻塞”摘要和客服反馈决策卡，并把反馈 open / replied / closed 队列纳入关键队列展示。

- 将礼品卡完整码、帮助反馈状态队列和监控面板增强提交 `cd6314c2` 部署到 ECS 和管理后台：远端 `go test ./...`、编译、切换双端口 slot、Nginx 配置检查和公网 healthz 均通过，当前 Nginx active upstream 为 `3000`；后台静态前端重新构建并部署到 `https://admin.nongjiqiancha.cn/`，公网首页 200，未登录访问 `/admin-api/v1/monitoring` 返回 401。部署后 readiness 显示 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / redis=ok / upload_storage=oss`，输出不打印任何 token、AccessKey、模型 Key 或密码。

- 修复管理后台礼品卡页空数据崩溃：后端 `GET /admin-api/v1/gift-cards/summary` 的 `failure_reasons` 空结果现在稳定返回 `[]`，前端对 summary / 批次 / 卡 / 兑换尝试数组也做 null 兜底，生产库还没有礼品卡时页面不再报 `Cannot read properties of null (reading 'length')`。监控面板同步补礼品卡批次数和总卡数，生产库 0 张可兑换卡会作为“先生成礼品卡”的待处理事项显示，避免把“还没生成正式卡”误看成兑换链路坏了。

- 管理后台监控面板补“正式上架检查”区：`GET /admin-api/v1/monitoring` 新增 `launch_readiness`，把后端健康、手机号登录、模型问诊、礼品卡权益、安装包更新、支付接入、备案与上架材料、日志告警、客服反馈按“就绪 / 需处理 / 阻塞”展示。支付申请未完成、App 备案 / 公安备案和上线前 AccessKey 轮换不会被伪装成已完成；礼品卡可作为支付接入前的真实权益激活链路。

- 继续推进管理后台生产化细节：前端侧栏和监控快捷入口开始按后台角色矩阵隐藏无权页面，减少 support / content_ops / release_ops / finance_ops 等角色点进 403 的误操作；服务端权限仍是唯一硬边界，前端隐藏只做体验收敛。监控窗口里的登录 session 口径拆清：`active_sessions` 表示当前有效 App session 总量，新增 `recent_auth_sessions` 表示当前窗口内新创建 / 登录 session，前端表格展示“新登录 session / 当前有效 session”，不再把 `updated_at` 当作真实活跃度。

- 多代理只读巡检后继续收口管理后台监控面板小 bug：前端修复窄屏筛选栏横向溢出、宽表撑破卡片、健康异常字符串只显示信息态、`action_items.level=error` 时顶部结论不变红、`formatTime(0)` 空值判断不严和后端 route 拼错时按钮静默消失等问题；监控“当前结论”会优先跳到真实触发红 / 黄的处理入口。后端修正礼品卡“可用”数量，只统计当前已生效且未过期的 active 卡；App 错误 Top 的聚合函数开始尊重调用方 limit，监控传 Top10 就返回 10；检查更新页和监控页的 `config_valid` 口径统一，并新增 `download_artifacts_complete` 单独提示 HTTPS APK、SHA-256 和文件大小是否齐全；监控返回的 action / capability route 会按当前后台角色裁剪，避免无权限账号点进 403。新增后端契约单测覆盖监控 route / level / status、角色裁剪和检查更新配置口径。

## 2026-06-07

- 将账号ID收敛、礼品卡追溯和监控面板增强提交 `5321ffe9` 部署到 ECS 和管理后台：远端 `go test ./...`、编译、切换双端口 slot、Nginx 配置检查和公网 healthz 均通过，当前 Nginx active upstream 为 `3001`；后台静态前端重新构建并部署到 `https://admin.nongjiqiancha.cn/`，公网首页 200，未登录访问 `/admin-api/v1/monitoring` 返回 401。部署后轻量冒烟确认 `https://api.nongjiqiancha.cn/healthz` 为 200，返回 `auth_strict=true / bailian=ok / dypns=ok / dypns_fusion=ok / dypns_sms=ok / redis=ok / upload_storage=oss`；融合认证 token 入口返回 200 且有 token / scheme，短信发送接口对非法手机号返回 400。输出不打印任何 token、AccessKey、模型 Key 或密码。

- 继续按正式上架路线收敛账号、礼品卡和监控面板：明确全局长期业务身份统一为账号ID `acct_...`，手机号注册 / 登录只作为凭证归到该账号ID下，底层 `user_id` 字段在生产语义上按账号ID理解；Android 融合短信登录和兼容短信登录 payload 会带旧本机 `legacy_user_id`，后端只接受本机 UUID 形态或可由旧 bearer token 证明的 legacy ID，不接受 `acct_...` 作为迁移桥，并把礼品卡兑换和兑换尝试纳入旧 ID 到账号ID的数据迁移。礼品卡后台新增 summary 接口、卡码尾号 / 批次 / 账号ID追溯、兑换尝试成功状态和失败原因筛选、最近 7 天失败原因聚合；监控面板前端新增“当前结论 / 登录与账号ID / 礼品卡与权益 / App质量”决策卡和快捷入口，继续往非运维也能看懂的生产面板推进。debug 包与正式包默认接同一个生产 HTTPS 后端，区别只保留 debug-only 预览面板和调试日志。

- 轻量补充 OpenCode CI 记忆同步提示：全局 OpenCode 提示词和 [docs/opencode-codex-bridge.md](D:/wuhao/docs/opencode-codex-bridge.md) 写明 Android CI 会运行 `scripts/check_project_memory.py`，改动 `app/src/main/kotlin/...`、`server-go/`、`docs/adr/`、`docs/runbooks/`、`README.md`、`app/AGENTS.md` 或 `server-go/AGENTS.md` 等关键真相文件时，需要判断并同步更新 `AGENTS.md` 或 `docs/project-state/*`，提交前优先本地运行项目记忆检查。该规则只补轻量提示，不把大型项目记忆重新加入自动 `instructions`。

- 深度推进礼品卡和后台监控：Android 设置页“礼品卡”接入 `POST /api/gift-cards/redeem`，只在后端成功后展示兑换成功并刷新会员权益；后端收紧手机号登录时旧本机 `user_id` 迁移证明，避免未验证 legacy id 跟随登录迁移；礼品卡后台新增 `POST /admin-api/v1/gift-cards/void`，可由 finance 权限作废未兑换卡并写审计。监控面板 `GET /admin-api/v1/monitoring` 新增 `action_items` 和 `capabilities`，前端改为“整体状态 / 先处理事项 / KPI / 服务状态 / 后台能力状态 / 明细表”的简洁视图；礼品卡页新增可用 / 已兑 / 作废 / 失败尝试 KPI，并明确历史完整卡码不可导出是安全设计。主聊天 ledger 新增 `request_hash`，同一 `client_msg_id` 若对应不同内容会返回冲突，避免重复幂等键误回放；Android 自更新下载保留默认 200MB 硬上限。本提交 `d3c4a4ac` 已部署后端和管理后台到 ECS，公网验证 `https://api.nongjiqiancha.cn/healthz` 返回 200、`https://admin.nongjiqiancha.cn/` 返回 200，未登录访问 `/admin-api/v1/monitoring` 返回 401。

- 轻量补强 OpenCode 代管规则：全局 OpenCode 提示词和 [docs/opencode-codex-bridge.md](D:/wuhao/docs/opencode-codex-bridge.md) 同步加入“用户不懂代码和运维时默认承担技术负责人角色”“高风险生产 / 支付 / 账号 / 数据库 / 密钥 / 部署 / 花钱动作先白话说明并等确认”“扫描审查类请求默认只读、不下刀”的三条规则；不把大型项目记忆重新加入自动 `instructions`，避免增加普通对话 token 成本。

- 管理后台继续补监控面板和可用性细节：`server-go` 新增 `GET /admin-api/v1/monitoring`，聚合服务健康、今日 / 24h / 7d 使用情况、App 自动日志错误、待回复反馈、今日农情、礼品卡兑换异常、后台操作失败和最近 30 天地区分布；`admin` 前端新增“监控面板”，把红黄绿状态、真实已接数据和“未开放 / 后续接”的能力分清楚。后台左侧菜单点击后保留原滚动位置，避免点底部菜单后视角跳回顶部；后台品牌图标从文字占位改为绿叶图标。该面板不是完整 SLS 告警中心，也不开放发布 / 回滚 / 支付 / 批量高风险操作。

- 收口 OpenCode 自动加载配置以降低桌面端卡死 / 无回复概率：全局 `C:/Users/Administrator/.config/opencode/opencode.json` 不再无条件加载本仓库 `AGENTS.md` 和 4 份项目记忆，项目级 [opencode.json](D:/wuhao/opencode.json) 也改为只自动加载 [docs/opencode-codex-bridge.md](D:/wuhao/docs/opencode-codex-bridge.md)。桥接文档同步写明：根规则和项目记忆仍是任务开始前必须读取的真相，但不再作为每条轻量消息的无条件自动 prompt，避免普通问候也携带超大上下文导致模型中转超时、sidecar session 丢失或 UI 长时间转圈。

- 第一版管理后台进入代码并部署到生产：新增 Vite `admin` 前端，覆盖登录、总览、用户管理、会员额度、订单、礼品卡、帮助反馈、App 日志、今日农情、检查更新、审计、产品洞察和服务健康；`server-go` 新增 `/admin-api/v1/*` 后台 API、后台账号 / session / CSRF、角色校验、bootstrap 初始化和审计。用户详情现在可查当前会员档位 / 到期、每日额度、扣次流水、加油包包明细、升级补偿、订单记录、礼品卡兑换和兑换尝试；礼品卡后端新增批次、卡、兑换尝试表，后台可生成 Plus / Pro 礼品卡批次，完整卡码只在创建成功当次返回，用户侧 `POST /api/gift-cards/redeem` 事务内发会员权益。生产入口已部署到 `https://admin.nongjiqiancha.cn/`，Nginx 同域反代 `/admin-api/`，后台域名 HTTPS 证书已签发，owner 账号已通过一次性 bootstrap 初始化，随后已清理 ECS `ADMIN_BOOTSTRAP_*` 环境变量并重启验证；公网已验证首页、登录和总览 API。Android 礼品卡页后续仍需真机兑换回归，后台还要补批量发放和失败原因聚合。

- 明确 OpenCode / Codex 的“准确率优先”工作模式：根 [AGENTS.md](D:/wuhao/AGENTS.md)、全局 OpenCode 提示词和 [docs/opencode-codex-bridge.md](D:/wuhao/docs/opencode-codex-bridge.md) 同步写入，复杂任务不要为了省 token、时间或子代理次数而省略关键代码阅读、旧方案残留排查、官方资料核对、验证命令或必要的只读并行巡检；同时禁止用无关大改、顺手重构或重复跑无意义命令来冒充严谨。本次只改协作规则，不涉及 Android / Go 业务代码。

- 继续补强 Codex / OpenCode 并行协作规则：根 [AGENTS.md](D:/wuhao/AGENTS.md)、全局 OpenCode 提示词和 [docs/opencode-codex-bridge.md](D:/wuhao/docs/opencode-codex-bridge.md) 同步写入“复杂任务可用内部子代理做只读并行巡检，主窗口统一核验、最小改动、验证、文档同步、提交推送”的口径，并明确 Codex / OpenCode 同时工作时只暂存本次意图内文件，不覆盖对方并行改动。该规则用于让两套工具无缝切换，不涉及 Android / Go 业务代码。

- 补强 OpenCode 接手与交付闭环规则：全局 OpenCode 提示词增加“每次改动必须说明改了什么、为什么、风险和验证；仓库改动默认提交并推送；跨对话窗口继续生效的规则必须固化到仓库文档；修改全局配置时判断是否需要镜像到仓库”的硬约束。仓库内 [docs/opencode-codex-bridge.md](D:/wuhao/docs/opencode-codex-bridge.md) 同步写入同一口径，确保 GitHub 同步和换窗口接手也能读到；本次不改 Android / Go 业务代码。

- 管理后台方案细化：新增 [admin-dashboard-design.md](D:/wuhao/docs/runbooks/admin-dashboard-design.md) 作为后台页面级设计文档，覆盖总览、用户、地区与来源、会员与额度、订单、礼品卡、帮助与反馈、App 日志、今日农情、检查更新、产品洞察、审计日志和权限角色；新增 [ADR-0004-admin-backend-architecture.md](D:/wuhao/docs/adr/ADR-0004-admin-backend-architecture.md)，明确后台采用 Vite 静态前端 + `server-go` 管理 API，不另起后台服务。产品洞察按脱敏聚合报表做，后续 Codex 优先读取洞察报表，不直接长期读取生产库完整聊天全文；后台初始账号只能通过一次性初始化脚本或环境变量写入 hash，不把明文账号密码写进仓库。

- 网站公安联网备案号已下发：`京公网安备11010602202723号`；官网 footer 已补真实编号、查询链接和警徽图标。公安备案数据码、证件号或平台账号信息不进入仓库；App 公安备案后续等 App 备案通过 / 正式信息齐后再补。

- 继续按多代理巡检收口一批前后端和运维小坑：今日农情模型非 2xx 错误不再把上游原始 body 写入错误链 / 日志 / 数据库；App 自动日志 attrs 不只按敏感 key 过滤，也会丢弃普通字段名里包含 URL、token、AccessKey、手机号等敏感文本的 value；上传 multipart 超限明确返回 `413 body_too_large`，损坏 multipart 返回 `invalid multipart`。Android 图片导入和后台待发送补发在压缩前增加 32MB 原图读取上限，自更新 APK 下载增加默认 200MB 硬上限并按后端 file size 下载中断言；debug 测试包真实流式渲染速度与 release 对齐。内部客服回复不再对任意 `user_id` 自动创建用户资产行，必须已有帮助与反馈会话。运维侧 readiness 补 `dypns / dypns_fusion / dypns_sms / dev_order_endpoints=false` 断言，官网部署脚本改为断言 HTTP / HTTPS 状态码，回滚和 SLS 日志 runbook 修正为当前双端口 slot / 最小 SLS 真实入口；当时同时明确帮助与反馈图片仍复用 `/uploads/` 3 天生命周期，`support/` 30 天只是预留规则，后续已在 2026-06-13 切到 `support/`。

- 按前后端深度巡检结果修复一轮小 bug / 风险点：Android 融合认证 SDK token 鉴权成功回调增加场景启动防重，避免重复拉起融合认证场景；登录页品牌绿叶改用透明 launcher 前景并由小黑圆底裁切承托，协议勾选区改为可换行布局；一键登录 token 接口失败文案不再把临时异常误报为“未配置”。Go 后端不再把模型上游非 2xx / 非 SSE 原始响应 body 写入日志或返回客户端，B/C 摘要错误同样只保留 HTTP 状态；App 自动日志自由 `message` 增加敏感文本降级并从服务端结构化日志字段中移除；主聊天用户输入服务端校验对齐 App 端 6000 字上限，仅兜底绕过 App 的直接接口调用，不限制模型输出或 B/C 摘要输出。同步修正 SLS、HTTP healthz 和双端口 slot 相关 runbook 旧口径。

- Android 登录页品牌标题补绿叶标识：在“农技千查”左侧加入小黑色圆形底的绿色叶片图标，圆底贴近绿叶、不使用完整黑底方形 App 图标，保持白底登录页更稳的品牌识别；不改一键登录、验证码登录、协议勾选或后端认证逻辑。

- 官网首屏文案继续按用户定稿收口：主标题改为“看清作物问题”，主文案改为“基于原生视觉语言模型，结合作物照片与症状描述，梳理现场线索，输出可供进一步核查的农技参考”；右侧删除“能力亮点”小标题，三项能力改为“原生视觉感知 / 多模态线索融合 / 稳健农技推理”。本次不公开点名 `Qwen3.5-Plus`，只吸收其“原生视觉 / 多模态 / 推理”能力表达；同时放大官网品牌绿叶图标并收紧图标与“农技千查”的间距。

## 2026-06-06

- 官网文案按公开 AI 产品页表达重新收口：不再把“联网校准”作为公开核心能力标题，首屏主张改为“农业视觉智能，看懂作物现场”，能力点改为“视觉语言问诊 / 农事上下文承接 / 稳妥判断路径”，强调基于视觉语言模型把作物照片、症状、地区农时和处理反馈整理成可追问、可复盘的农技参考，同时保留“内容仅供农技参考，不能替代线下诊断”的边界。该条为当时历史过程，当前官网文案已由 2026-06-07 条目替代。

- 继续按 Product Design + 联网参考收口农技千查官网：删除“面向田间的图文问诊” eyebrow，不再把“照片理解 / 连续问诊”当卖点；首屏改成左侧品牌主张、右侧能力栏的暗色一页结构，logo 与“农技千查”收紧成品牌 lockup，主标题改为“用视觉语言模型，看清作物问题”，能力点收口为“先进视觉语言模型 / 联网校准 / 农业场景参考”。footer 增加公司主体“北京农技千问科技有限公司”并继续保留 ICP；已重新部署到 `https://nongjiqiancha.cn/` 和 `https://www.nongjiqiancha.cn/`，公网验证新标题、公司名、联网校准和 ICP 均在线。该条为当时历史过程，当前官网文案已由上一条替代。

- 修复 Android Studio 直接 Run 后端地址为空的问题：当时 `app/build.gradle.kts` 默认使用 `UPLOAD_BASE_URL=https://api.nongjiqiancha.cn` 并允许显式覆盖；2026-06-08 已进一步锁定为生产 HTTPS 后端，不再通过 Gradle 参数切换业务后端地址。debug 包和正式包业务链路保持一致，差异主要保留 debug-only UI 文案预览等调试入口，不能再因为未传 Gradle 参数显示“后端地址未配置”。同时把 release-like 打包任务护栏从精确 `assembleRelease / bundleRelease / packageRelease` 扩展到包含 `release` 的 assemble / bundle / package 任务，避免 `nonMinifiedRelease` 等特殊产物绕过签名和 HTTPS 检查；本地已删除 4 月残留的旧 `nonMinifiedRelease` 生成 APK，避免误装旧包。登录页删除“农业图文问诊参考工具”副标题，入口更简洁。

- 再次收口农技千查官网：按克制暗色一页展示重写 `site`，删除顶部导航、产品 / 下载锚点、对话展示、复杂能力卡片和“安卓版准备中”等内部流程文案；品牌处使用透明背景的绿色叶片 `brand-mark.png` 与“农技千查”并排，正文采用“面向田间的图文问诊 / 让作物问题更清楚”口径，只保留安卓版下载按钮和居中 ICP footer。下载按钮只有注入合法 `https://...apk` 时才启用，未配置时不可点击；官网继续不展示公安备案占位。

- 按坏人视角做一轮安全排查并落地低风险加固：`/api/chat/stream` 的 `client_msg_id` 增加 128 字节上限，提前对齐 MySQL `VARCHAR(128)`，避免恶意超长幂等 ID 打到数据库层制造错误；新增内部 secret 入口 Redis / IP 短期限流，覆盖 `SUPPORT_ADMIN_SECRET` 保护的内部 App 日志、审计、帮助与反馈查询 / 回复，以及 `DAILY_AGRI_JOB_SECRET` 保护的今日农情生成；`SUPPORT_ADMIN_SECRET` 比对补长度检查后再常量时间比较。通过 Cloud Assistant 把 `api.nongjiqiancha.cn` 的 HTTP 80 改成 ACME challenge + 301 HTTPS 跳转，不再直接反代业务 API；部署 / 回滚 / readiness 脚本改为本机 HTTPS `--resolve` 健康检查。新增 [security-threat-model.md](D:/wuhao/docs/runbooks/security-threat-model.md) 固化攻击面、已防护项和剩余风险；`go test ./...` 已通过。

- 补齐 Go 后端请求级结构化日志底座：`Server.Handler()` 统一包一层 access log middleware，生成或透传 `X-Request-Id`，响应头回写同一 ID；记录 `http_request / http_request_slow / http_request_error`，字段只包含 method、path、status、duration、response bytes、masked IP、auth mode、可选 user_id 和 UA，不记录 query string、请求 body、Authorization、手机号、图片 URL 或模型 Key。慢请求阈值默认 `ACCESS_LOG_SLOW_MS=3000` 毫秒，`/healthz` 和 `/uploads/` 成功请求默认降噪，SSE Flusher 保持透传。新增只读脚本 [query-ecs-logs.ps1](D:/wuhao/scripts/query-ecs-logs.ps1)，可通过 Cloud Assistant 查询 Go 错误 / 慢请求 / 请求尾部、Nginx error 和 Nginx `429/5xx`，输出做基础脱敏；同步更新日志与运维 runbook。

- 接入农技千查专用 SLS 最小日志集：创建 Project `nongjiqiancha-prod-1159547719787456`，Logstore `server-go` / `nginx-error`，TTL 7 天、1 shard；ECS 安装 Logtail / ilogtail 并连接机器组 `nongjiqiancha-prod-ecs`，采集 `/var/log/nongjiqiancha/server.log` 和 `/var/log/nginx/error.log`。Go 服务新增 `LOG_FILE_PATH` 支持，把 JSON 日志同时写 stdout 和文件；ECS 环境写入 `LOG_FILE_PATH=/var/log/nongjiqiancha/server.log`，并配置 7 天 logrotate。新增 [setup-sls-logging.ps1](D:/wuhao/scripts/setup-sls-logging.ps1) 和 [query-sls-logs.ps1](D:/wuhao/scripts/query-sls-logs.ps1)。当前不把聊天正文、AI 回复、图片 URL / 内容、手机号、token、模型 Key、数据库密码或完整 Nginx access 全量采进 SLS。

- 轻量优化 Android App 低风险 UI：登录页补充产品副标题，主按钮和验证码按钮圆角统一为更柔和的 12dp，登录状态提示区分成功 / 进行中与错误提示色；会员中心把“相关功能未开放”提示从黑色强提示改成淡绿色说明条，并收小关闭按钮字形；设置菜单给账号管理、帮助与反馈、检查更新、礼品卡和服务协议补短副标题，提升扫读性。本轮不改主聊天滚动链、输入框状态机、图片发送或后端接口。

- 历史归档：按 Product Design 方向收紧农技千查官网：导航去掉图标和“备案”入口，正文删除“备案与服务说明”大块，只保留 App 介绍、图文问诊 / 农业大模型 / 原生 Android 体验三项能力和安卓版下载入口；下载待开放文案不再向普通用户暴露 App 备案、公安备案、真机回归等内部流程；footer 只保留 `京ICP备2026031728号-1`，公安备案号下发前不展示占位（现已下发并补 footer）。主视觉把 App 图标放在首屏主体位置，并用展示裁切让绿色叶片更大、黑底边界更轻；不改 Android launcher 图标资源，避免影响 App 备案和物料一致性。

- 执行免费优先安全加固：确认阿里云云安全中心免费版已生效，ECS 安全中心 agent online、当前风险计数 0；阿里云 DDoS 基础防护按官方口径默认免费开启。通过 CLI 撤销生产安全组公网 `TCP 22 / 0.0.0.0/0`，并通过 Cloud Assistant 停止 / 禁用 ECS 本机 `ssh` 服务，当前公网只放行 `80 / 443` 和 ICMP；生产运维优先走阿里云 CLI + Cloud Assistant。通过 Cloud Assistant 写入 Nginx 全局安全头兜底 `/etc/nginx/conf.d/nongjiqiancha-security.conf` 并 reload；官网部署脚本也补 `Content-Security-Policy`、`Permissions-Policy` 和 HSTS。统一加固官网 / 后端部署、回滚、readiness、登录用量和安全巡检脚本的阿里云 CLI 错误捕获：失败时先捕获 stderr 并脱敏 `AccessKeyId / Signature / SignatureNonce / Content` 等签名参数，再输出错误。新增 [security-hardening.md](D:/wuhao/docs/runbooks/security-hardening.md) 和 [harden-ecs-security.ps1](D:/wuhao/scripts/harden-ecs-security.ps1)，后续免费安全巡检和复刻加固走脚本。

- 只读复查手机号一键登录链路：未发现 Android 旧 `SESSION_API_TOKEN` 绕过登录、客户端模型直连或 `/api/auth/fusion/verify` 半程接口误签发账号 token；半程 verify-only 只返回 `ok / phone_mask`，最终 `onVerifySuccess` 才调用 `/api/auth/fusion/login` 换账号 session token。仍需真机重点确认阿里融合认证 verify token 半程校验后，最终 login 是否允许再次校验同一 token；如果出现半程成功但最终失败，再按 SDK 语义调整。

- 历史归档：曾复查过旧分层记忆重写和失败重试机制；该方案已被当前“一份记忆文档 + `pending_retry_b` + `qwen-plus`”替代。后续又已把本进程运行中保护补成 Redis TTL 租约，用于降低多实例重复抽取和非确定性覆盖风险。

- 继续复查云资源 / CDN / OSS 生命周期：阿里云官方口径确认 CDN 不是纯免费资源，基础按下行流量 / 带宽等计费，HTTPS 静态请求虽有月度免费额度但超出仍会计费；当前早期不启用 CDN，不把问诊私有图片改成 CDN 长缓存，仍走后端 `/uploads/` 中转 + OSS 生命周期。CLI 复查 `nongjiqiancha-prod` lifecycle 确认 `uploads/` 3 天自动删除、`support/` 30 天自动删除、未完成分片 1 天清理均为 Enabled；最近 6 小时 Go 服务未见业务错误，Nginx error log 主要是公网扫描 `.env / phpinfo / json key` 被限流拦截。同步校正 ECS 到期口径为 CLI 当前显示的 `2027-06-01T16:00Z`。

- 复查并最小收口主对话锚点、B 层和 C 层提示词：锚点保留“信息不足时严禁直接下定论，必须列 2 到 3 种可能性并追问关键问题”的强规则，同时补充地点可信度为 `unreliable / 未知` 时不能把地区、气候和农时当确定事实，普通问候 / 寒暄只需简短接住并引导回具体农业问题。B 层仍是通用短期记忆，只补“单纯问候、客套、无后续价值闲聊、已处理完的一次性 App 操作不写入当前主线”，并要求低可信地区不写成确定事实。C 层仍是三块长期通用记忆，只补多作物、多地块、多棚室、多农资或多事件必须分清对象，且低可信地区不写成长期稳定事实，避免长期记忆混写污染后续对话。

- 复查 Android 定位上下文链路并补可信度防御：每次发送前仍优先刷新系统定位并反查省 / 市 / 区县，但只有 2 小时内的系统定位或短窗口网络定位会标记 `reliable`；如果只能拿到更旧的系统缓存或历史缓存，继续传地区文本但降级为 `unreliable`，避免用户换城市后旧定位污染模型判断。后端 `ParseRegionFromHeaders` 同步拒绝空 / 无效地区头，坏头部会回落到 IP 粗定位兜底；新增对应 Go 测试。

- 修复 ECS 双端口发布 drain-stop 叠加导致的 502 风险：CLI / readiness 巡检发现 Nginx active upstream 指向 `3001`，但 `nongji-server-3001` 一度处于 inactive，公网 API healthz 返回 502；排查确认不是资源不足或模型 / 数据库故障，而是多次发布的 transient `nongji-drain-stop-*` 定时任务叠加，把当前 active slot 也停掉。已通过 Cloud Assistant 启动当前 active slot 并验证 `https://api.nongjiqiancha.cn/healthz` 200；`deploy-ecs-server.ps1` / `rollback-ecs-server.ps1` 新增发布 / 回滚前清理旧 drain 任务，`check-ecs-readiness.ps1` 改为 active slot inactive、Host healthz 非 200 或关键 health 标记缺失时直接失败，避免 502 被误判通过。

- 新增云资源容量与续费巡检入口：通过 CLI 复查 ECS / RDS / Redis / OSS 规格和实时用量，当前资源足够备案等待期、真机联调、早期内测和小流量上线；ECS 2 核 4G 当前负载约 0、可用内存约 2.9GiB、系统盘约 7% 使用；RDS 1 核 2G / 50G 当前磁盘约 3.0GiB、近 15 分钟 CPU 约 0.7%、内存约 11%；Redis 256MiB 当前内存约 4.4MiB、CPU 0 到 0.4%；OSS Bucket 当前 0MB。新增 [resource-capacity.md](D:/wuhao/docs/runbooks/resource-capacity.md)，明确 CPU / 内存 / 磁盘 / 带宽 / RDS / Redis / OSS / 到期提醒阈值，以后 Codex 发现接近阈值必须提前提示升级、续费或购买资源包。

- Android 接入问诊地区定位权限：`AndroidManifest.xml` 新增 `ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION`，App 进入聊天页后请求一次定位授权；授权后每次发送问诊前尽量短窗口刷新系统定位，并用系统 Geocoder 反查为省 / 市 / 区县等地区文本，通过 `X-User-Region` / `X-Region-Source=gps` / `X-Region-Reliability=reliable` 传给后端。纯文字前台流、带图前台流和 WorkManager 待发送任务都带同一地区上下文；App 不上传经纬度、不保存轨迹。未授权、定位失败或反查失败时继续由后端 `ip2region` IP 粗定位或未知兜底；同步更新 App 内隐私政策 / 个人信息收集清单 / 应用权限和项目记忆。

- 后端地点注入补免费离线 IP 粗定位：`ResolveRegionByIP` 从原来的固定“未知 / unreliable”升级为可选读取 ECS 本地 `ip2region` xdb（`IP2REGION_V4_XDB_PATH`，兼容 `IP2REGION_XDB_PATH`）做公网 IP 到省 / 市级地区解析，结果仍标记为 `region_source=ip / region_reliability=unreliable`，只作为主对话参考；私网 IP、库未配置、代理漂移或查询失败继续兜底未知。该方案不走收费 API、不调用第三方免费接口、不放 RDS、不把完整 IP 注入模型；新增 [ip-region.md](D:/wuhao/docs/runbooks/ip-region.md) 和 readiness 检查项，当前作为 Android 定位未授权 / 失败时的兜底链路。

- 历史归档：主对话锚点补 B/C 记忆静默使用规则：B/C 记忆只作后台参考，用于减少重复追问和保持连续性；除非用户明确要求回顾历史，否则主模型不要主动复述记忆内容、层级名称或用户画像。`/api/chat/stream` 注入给主模型的 B/C 标签同步改为“B层通用短期记忆（仅供参考）”和“C层长期通用记忆（仅供参考）”。同时复查时间 / 地点注入链路：每轮只注入当前时间、用户地点和地点可信度；Nginx 会透传真实客户端 IP 给 Go 服务，但 IP 只用于限流、脱敏日志和地区推断，不把完整 IP 注入模型。该条复查时 `ResolveRegionByIP` 仍返回未知；随后已在同日接入免费离线 `ip2region` 粗定位，见本日更新条目。

- 历史归档：曾按分层记忆方案收口短期和长期画像。该方案已删除，当前只保留一份自然语言记忆文档，四段分别承接短期、长期背景、用户画像和农业重点事件。

- 主对话锚点补联网校准触发条件：在原有强时效、强客观核对和用户明确要求基础上，增加“疑难问题、复杂问题、高风险判断需要校准公开权威信息”时可触发联网搜索；仍保留同轮最多一次、能不联网则不联网、关键参数缺失先追问 1 到 2 条的限制，避免把联网搜索当成知识库或替代田间信息判断。

- 按 Product Design 轻量审查结果收紧 App 内几个占位 UI：礼品卡页输入后不再出现可兑换黑色按钮，按钮固定置灰显示“暂未开放”；会员中心加油包说明和按钮统一为未开放口径，不再出现“可订购但禁用”的冲突；帮助与反馈超过 2000 字会截断并提示，不再静默拒绝输入，且只在消息 / 加载状态变化时自动滚到底，避免点输入框或选图时打断用户查看旧记录；帮助与反馈空态图标从文本问号换成线性对话图标；账号管理“注销账号”弱化为不可点的“暂未开放”占位，避免像真实危险操作。

- 部署农技千查官网到 ECS 根域名：新增 [deploy-ecs-site.ps1](D:/wuhao/scripts/deploy-ecs-site.ps1)，自动构建 Vite 静态站、同步 `@` / `www` A 记录到 `39.106.1.151`、分片上传 `site/dist`、发布到 `/var/www/nongjiqiancha-site/current`、写入 Nginx 静态站配置并通过 certbot 签发 `nongjiqiancha.cn` / `www.nongjiqiancha.cn` 免费 HTTPS 证书。公网验证 `http://nongjiqiancha.cn/` 301 到 HTTPS，`https://nongjiqiancha.cn/` 和 `https://www.nongjiqiancha.cn/` 均返回官网首页 200，页面包含“农技千查 / 安卓下载 / 京ICP备2026031728号-1”；后续新版已改为公安备案号下发前不展示占位（现已下发并补 footer），不伪造编号。

- 将 ECS 后端发布方式从单服务重启升级为单机双端口 slot 切换：`scripts/deploy-ecs-server.ps1` / `scripts/rollback-ecs-server.ps1` 现在共用远端互斥锁，自动判断 Nginx 当前上游 `3000/3001`，在非当前端口启动新 slot 并通过生产 healthz 断言后再切 Nginx；切换失败会恢复 Nginx 备份并停止新 slot，切换成功后禁用旧 slot / 历史 `nongji-server.service`，再延迟停止旧进程给 SSE 连接排空。2026-06-06 首次迁移当时 active upstream 为 `127.0.0.1:3001`；后续巡检以 `current-status.md` 和 readiness 输出为准。

- 优化 ECS 部署脚本健康检查顺序：`scripts/deploy-ecs-server.ps1` 在重启 `nongji-server` 后会先等待 Go 服务本机 `127.0.0.1:3000/healthz` 返回 200，再通过 Nginx / Host 入口检查 `api.nongjiqiancha.cn`，避免脚本刚重启就撞上游空窗并打印整页 502 HTML。该改动只减少部署输出里的瞬时 502 噪音；单台 ECS 单进程重启期间，真实零 502 仍需后续蓝绿 / 双端口切换或多实例滚动发布。

- 优化帮助与反馈自动回复文案：逻辑仍保持短问候、纯图片 / 见图、通用兜底三类，不新增细分类；文案改成更正式的客服口径，使用“客服会在本页跟进回复 / 后续回复会在本页显示”，不承诺具体时效，也不把自动回复包装成智能客服。

- 将帮助与反馈自动回复简单兜底版 `server-go` 提交 `8c72b973` 部署到 ECS：远端 `go test ./...`、编译、systemd 重启、Nginx 配置检查均通过；重启瞬间出现过一次 502，随后 readiness 复查显示 systemd active、Nginx OK、Host healthz 200、`bailian=ok`、`dypns=ok`、`dypns_fusion=ok`、`dypns_sms=ok`、`redis=ok`、`upload_storage=oss`。readiness 输出只记录 set/empty，不打印真实密钥值。

- 将帮助与反馈自动回复收紧后的 `server-go` 提交 `cd8926a6` 部署到 ECS：远端 `go test ./...`、编译、systemd 重启、Nginx 配置检查均通过；重启瞬间出现过一次 502，随后 readiness 复查显示 systemd active、Nginx OK、Host healthz 200、`bailian=ok`、`dypns=ok`、`dypns_fusion=ok`、`dypns_sms=ok`、`redis=ok`、`upload_storage=oss`。readiness 输出只记录 set/empty，不打印真实密钥值。

- 按“简单化”重新收口帮助与反馈自动回复：删除登录、更新、图片、会员、历史、隐私、农业咨询等细分自动回复，只保留短问候、纯图片 / 见图、通用兜底三类文案。通用兜底统一说明本页主要处理 App 使用反馈，农业咨询可回主聊天页继续问，并请用户补充时间、页面提示或截图；通用和纯图片兜底共享 24 小时冷却，避免短时间重复刷系统回复；测试同步改成简单规则。

- 将帮助与反馈自动回复口径收口后的 `server-go` 当前提交 `086444a4` 部署到 ECS：远端 `go test ./...`、编译、systemd 重启、Nginx 配置检查和 Host healthz 均通过；重启瞬间出现过一次 502，随后 readiness 复查显示 systemd active、Nginx OK、Host healthz 200、`bailian=ok`、`dypns=ok`、`dypns_fusion=ok`、`dypns_sms=ok`、`redis=ok`、`upload_storage=oss`。readiness 输出只记录 set/empty，不打印真实密钥值。

- 打磨未开放功能和帮助反馈体验：会员中心套餐区新增“会员购买暂未开放”提示，开通 / 升级 / 加油包订购按钮统一置灰显示暂未开放或当前状态，避免内测用户误以为已经可真实交易；礼品卡页提前显示“礼品卡暂未开放”和后续开放说明，输入后也只提示暂未开放，不调用后端、不发权益。帮助与反馈发送过程中新增“正在上传图片...”/“正在提交反馈...”状态文案，弱网多图时不再只有按钮置灰；后端固定自动回复文案从“尽快核实处理”收口为“已提交，可继续补充时间、步骤或截图”，不承诺即时客服或 SLA。

- 新增农技千查官网首版 Vite 静态站：`site` 目录提供 App 介绍、安卓下载入口、服务边界说明和备案 footer，使用仓库内正式黑底绿色 App 图标，保持官网 / App / 备案材料识别一致。安卓下载链接不在代码里写死，可通过 `VITE_ANDROID_APK_URL` 在构建时注入；未配置时页面显示下载待开放，避免 App 备案、公安备案和真机回归完成前假链接。footer 已展示网站备案号 `京ICP备2026031728号-1` 并链接工信部备案系统；后续新版已改为公安备案号下发前不展示占位（现已下发并补 footer），不伪造编号。新增 [official-website.md](D:/wuhao/docs/runbooks/official-website.md) 记录构建、下载链接和备案 footer 规则。

- 帮助与反馈补轻量自动回复和一轮 UI 打磨：用户首次提交反馈、提出可规则识别的常见 App 使用问题，或距上一条用户反馈已超过 24 小时再次提交时，后端会写入一条 `sender_type=system` 固定回复并随响应返回 `auto_reply`；Android 新版发送成功后会把用户消息和自动回复一次追加显示，并立即标记该自动回复已读，避免自己发完还亮红点。自动回复只按关键词兜住“你好 / 在吗 / 怎么用 / 登录验证码 / 检查更新 / 图片上传 / 会员次数 / 历史记录 / 隐私协议 / 农业问题请回主聊天”等轻量问答，不调用模型、不承诺 SLA、不替代后台人工回复；后台会话列表的 `needs_reply` 改按最新非 `system` 消息判断，自动回复不会盖掉用户待回复状态。前端同时给帮助与反馈页增加更清晰的空态说明、淡绿灰消息区、居中的“系统提示”气泡，并按消息 ID 合并历史拉取和发送返回，降低自动回复重复 / 被覆盖风险。

## 2026-06-05

- 给 DashScope / 百炼模型 Key 池补自动高并发平滑分流：默认 `DASHSCOPE_KEY_SELECTION_MODE=auto`（留空也按 auto），平稳期继续主 Key 优先，保持 `DASHSCOPE_API_KEY_1` 主 Key 口径；短窗口内模型请求量达到阈值，或开流前出现限流 / 鉴权类 failover 时，后端会自动进入一段请求级轮询分流窗口，默认 10 秒内 20 次请求触发、持续 60 秒，窗口结束后自动回到主 Key 优先，不需要上线后手动改环境或为了切模式重启。仍支持显式 `fallback` 强制主备、`round_robin / rr` 强制轮询；开流前 `401 / 403 / 429` 和限流类 `400` 的自动 failover 与 1 秒冷却保留，SSE 已开始后仍不在同一条回复中途切 Key。已部署到 ECS，并通过 Cloud Assistant 显式写入 `DASHSCOPE_KEY_SELECTION_MODE=auto`、自动轮询阈值和保持时间，readiness 显示相关环境均为 set、`bailian=ok`；同步更新模型 Key runbook、`.env.example`、readiness 脚本、根规则和项目状态；真实 Key 值不进入仓库、文档或聊天复述。

- 复核阿里云官方联网搜索限流口径：联网搜索是 15 RPS、按阿里云主账号维度统计所有 API Key 的搜索请求总和，超过时 API 不报错但搜索链路不会触发。当前主聊天 `forced_search=false`，高并发或模型判断无需实时信息时本来就可能不联网回答；因此不额外做“搜索限流后后端再发一轮不联网请求”的兜底，避免多打一轮模型请求。Key 池 auto 仍负责模型请求分流和开流前错误 failover；今日农情强制联网生成需要单独观察任务成功率。

- 将包含后台监控面板前置内部接口、最小操作审计和 DashScope 主备 Key 优先逻辑的当前 `server-go` 工作树部署到 ECS：远端 `go test ./...`、编译、替换二进制、重启 `nongji-server`、Nginx 配置检查和 Host healthz 均通过；重启瞬间出现过一次 502，脚本随后等到 healthz 200 且 `bailian=ok`。本机 readiness 复查显示 systemd active、Nginx OK、Host healthz 200、`DASHSCOPE_API_KEY_1=set`、`DASHSCOPE_API_KEY_2=set`、`DASHSCOPE_API_KEY_3=empty`、`DASHSCOPE_KEY_COOLDOWN_SECONDS=set`、`redis=ok`、`upload_storage=oss`；输出只记录 set/empty，不打印真实 Key 值。

- `server-go/internal/app/bailian.go` 将百炼模型 Key 池从健康 Key 轮询收口为主备优先：`DASHSCOPE_API_KEY_1` 是首选主 Key，`DASHSCOPE_API_KEY_2` 可作为副 Key，旧 `DASHSCOPE_API_KEY` 和 `DASHSCOPE_API_KEYS` 继续作为兼容入口但不抢在专用槽位前。主对话、B/C 摘要和今日农情仍共用同一池；开流前遇到 `401 / 403 / 429` 或带限流 / quota 语义的 `400` 会切下一把并短暂冷却，默认 1 秒，可用 `DASHSCOPE_KEY_COOLDOWN_SECONDS` 调整；SSE 已打开后不在同一条回复中途切 Key。2026-06-05 已通过 Cloud Assistant 把主 / 副模型 Key 写入 ECS `/etc/nongjiqiancha/server.env` 的主备槽位并重启服务，公网 healthz 显示 `bailian=ok`；仍需真实 App 主聊天 / 图片问诊回归。本轮未把任何真实模型 Key 写入仓库、文档或聊天复述。

- 通过阿里云 CLI / Cloud Assistant 在 ECS 上为 `api.nongjiqiancha.cn` 配置 Let’s Encrypt / certbot 免费 DV 证书和 Nginx 443 HTTPS：Nginx 仍反代本机 `127.0.0.1:3000`，证书路径记录在 ECS `/etc/letsencrypt/live/api.nongjiqiancha.cn/`，私钥只记录路径不记录内容；证书有效期到 2026-09-03，certbot 自动续期 timer 已启用。公网 `https://api.nongjiqiancha.cn/healthz` 已返回 200，HTTP 80 暂保留给调试 / 迁移观察；当时 `/healthz` 仍显示 `bailian=missing_key`，同日晚间已补上 DashScope 主 / 副模型 Key 槽位并显示 `bailian=ok`。

- 在后台监控面板前置地基上继续补最小内部操作审计：新增 `admin_audit_logs` 迁移和 `GET /internal/admin/audit-logs` 只读查询，现有 `GET /internal/app/logs`、帮助与反馈内部读取 / 回复、今日农情内部生成会写入审计元信息；可选 `X-Admin-Actor` / `X-Support-Admin-Actor` 只用于标记操作人，不替代鉴权。审计只保存 actor、动作、目标、成功 / 失败、状态码、脱敏 IP、UA 和少量过滤条件 / 计数，不保存反馈正文、日志 attrs 原文、图片 URL、手机号、token 或密钥。该能力仍只是网页管理后台前置地基，不是完整后台账号、角色权限或 SLS 告警系统。

- 帮助与反馈补明天后台面板会直接用到的只读内部会话列表：新增 `GET /internal/support/conversations`，按最近时间返回用户会话、最新消息、消息数、用户未读后台 / 系统消息数和 `needs_reply` 标记；仍由 `SUPPORT_ADMIN_SECRET` 保护，查询动作写入审计，但审计只记筛选条件和返回条数，不记录反馈正文或图片 URL。同步给 `support_messages` 加后台列表索引；这仍是内部接口地基，不是完整客服坐席或工单系统。

- 按 debug / release merged manifest 复核 App 权限披露：应用权限页和隐私政策补充 WorkManager 依赖合并的 `WAKE_LOCK / RECEIVE_BOOT_COMPLETED / FOREGROUND_SERVICE` 等后台任务口径，明确只用于带图消息在 App 切后台、进程被系统回收或设备重启后按同一条待发送任务有限重试，不用于 App 外推送、广告通知、定位、通讯录或短信读取；同步更新 `docs/runbooks/legal-privacy.md` 和项目状态。

- 截图确认阿里云管局已审核通过网站 ICP 备案：主体备案号 `京ICP备2026031728号`，网站备案号 `京ICP备2026031728号-1`，网站名称“农技千查”，域名 `nongjiqiancha.cn`；随后按阿里云 App 备案流程提交 Android App 资料，订单号 `2036780517515`，2026-06-05 20:03 左右进入阿里云初审，页面提示预计 2026-06-07 20:00 前审核，需注意接听阿里云备案审核电话。公安联网备案已生成数据码并有关联网站备案号，但尚未提交到全国互联网安全管理服务平台；数据码不写入仓库、文档或聊天复述。同步更新根规则、当前状态、未关闭风险、上线计划和 ECS runbook，把“ICP 未通过”改为“网站 ICP 已通过，App 备案已提交初审，仍缺 App 备案通过、公安备案、DashScope 模型 Key 和真机回归”；同日晚间已继续补上 `api.nongjiqiancha.cn` HTTPS 和 DashScope 主 / 副模型 Key 槽位。

## 2026-06-04

- 夜间前后端深度维护先落低风险边界：Android 主 manifest 显式声明一键登录所需 `READ_PHONE_STATE` 和设备网络 / Wi-Fi 状态权限，降低 release 构建依赖 AAR manifest merge 的不确定性；`verifyFusionTokenOnly()` 不再只看 HTTP 200，而是要求后端 verify-only 响应体 `ok=true` 才算半程校验通过。App 自动日志 attrs 在 Android 端和 Go 后端都新增敏感 key 过滤，丢弃 `phone / token / url / uri / body / message / content` 等字段名对应的值；图片上传 DEBUG 日志改为只打印脱敏 URL 和响应 body 长度，不再打印完整上传 URL 或响应 body。同步修正 README 的输入边界为支持纯文字、纯图片和图文混合，并更新项目记忆 / runbook，把明天要做的管理后台明确为受保护网页后台，而当前只有内部 API 地基、没有后台账号 / 权限 / 审计系统。

## 2026-06-03

- 补后台监控面板前置地基和一键登录半程校验收口：后端新增 `POST /api/auth/fusion/verify`，只给阿里云融合认证 SDK 半程校验使用，不签发账号 session、不迁移旧用户数据；Android `FusionOneLoginClient` 的 `onHalfWayVerifySuccess` 改为调用该 verify-only 接口，最终 `onVerifySuccess` 仍走 `/api/auth/fusion/login` 换账号 token。后端 App 自动日志在已有 `POST /api/app/logs` 写入基础上新增 `GET /internal/app/logs` 只读内部查询，暂复用 `SUPPORT_ADMIN_SECRET`，支持按时间、用户、事件名和等级筛选并返回明细 / 聚合摘要，给后续管理后台和日志面板接入。帮助与反馈输入区加号 / 发送按钮尺寸同步主聊天窄屏规则，加号点击也改成同主聊天一致的开关行为；相机 / 照片附件面板仍复用同一组件和同一图标尺寸。

## 2026-06-01

- 夜间深度巡检后补登录与后端防刷收口：后端 `AUTH_STRICT=true` 默认不再接受旧无 `session_id` bearer token，除非显式设置 `AUTH_ALLOW_LEGACY_TOKEN=true`，让 `POST /api/auth/logout` 的 session 吊销语义真正闭环；`GetClientIP` 只信任来自本机 / 内网代理的 `X-Real-IP` / `X-Forwarded-For`，降低伪造转发头绕过限流风险；`POST /api/auth/fusion/login` 新增默认 IP hash 10 分钟 20 次限流，短信发送新增 IP 总量 10 分钟 20 次限流，防止伪造 verify token 或轮换手机号消耗阿里云认证资源。Android 侧补 invalid auth 事件：会话接口二次 401 后清本地 auth、取消当前流并把登录门切回未登录；融合认证 SDK 首版增加失败兜底和 30 秒超时兜底，避免一键登录 busy 卡住。同步更新 Redis / 手机号登录 / 隐私 / ADR 文档，明确当前 UI 主链是 ADR-0003 的单一正向 `LazyColumn`，不是旧 overlay、bottom active zone 或反向列表。`go test ./...`、`.\gradlew.bat :app:compileDebugKotlin`、`git diff --check`、项目记忆检查和 ECS 发版均通过；当前 ECS 部署提交为 `e8a0f361`，readiness 显示 `bailian=missing_key` 仍是预期未配模型 Key。
- 补高并发 / 扩机预备护栏：主聊天 `/api/chat/stream` 用户级频控从单进程内存限流升级为“有 Redis 就走 Redis、无 Redis 回退单进程”，Redis key 只保存 `user_id` hash，不保存聊天正文、图片、token 或手机号；同一用户同时一条主聊天流本来已经由 MySQL `GET_LOCK` + `chat_stream_inflight` 跨进程控制。新增 `docs/runbooks/scaling-readiness.md`，把以后从单机原地升级、多 ECS、RDS 扩容到轻量队列的步骤和仍缺的 B/C 摘要 lease、滚动发布、SLS 统一日志等准备项固化下来。
- 按小米会诊建议补“退出设备”最小闭环：后端新增 `POST /api/auth/logout`，校验当前 bearer token 后把对应 `auth_sessions.revoked_at` 置为当前时间，后续同 token 会被 `requireAuth` 拒绝；Android 账号管理页“退出设备”不再只是占位提示，成功后清本地 auth token 并重建 Activity 回到登录门。该刀只吊销当前设备 session，不做完整设备列表、远程踢设备或账号注销，不删除聊天历史、会员资产、额度、帮助与反馈或本机 `user_id`。
- 复查手机号登录和 ECS 部署会诊资料时，发现 `docs/runbooks/deploy-ecs.md` 仍残留 DYPNS `missing_key / missing_config` 的旧健康检查口径；已同步改为当前真实状态：`dypns=ok / dypns_fusion=ok / dypns_sms=ok / redis=ok / upload_storage=oss`，并把下一步从“配置 DYPNS / 短信模板”改为“上线前轮换已暴露主账号 AccessKey、HTTPS / 模型 Key 后做真实登录和图片链路回归”。当时业务阻塞仍是 DashScope 模型 Key、HTTPS / 备案和真机登录回归，不是手机号认证配置缺失；截至 2026-06-05，`api` HTTPS 和网站 ICP 已补齐，仍缺 DashScope 模型 Key、App 备案通过、公安备案和真机回归。
- 复查登录页验证码登录展开态：验证码输入框和“发送”按钮原本视觉不齐，原因是验证码框使用 Material3 `label` 后预留了浮动标签高度，而发送按钮按普通 56dp 高度绘制；现改为验证码框使用 `placeholder`、验证码行 `CenterVertically`、输入框和发送按钮都固定 56dp，并给发送按钮最小宽度，避免两个边框上下错位。当时“后端地址未配置”代表安装包没有带 `UPLOAD_BASE_URL`，不是服务器掉线；2026-06-08 已锁定为正式 HTTPS 后端，不再使用临时 HTTP 直连构建口径。
- 记录后期管理后台规划：新增“用户真实反馈 / 产品洞察”模块设想，后续从帮助与反馈、App 自动日志和聊天归档里做脱敏聚合，提取高频 bug、登录 / 上传 / 历史恢复卡点、模型答偏线索、常见作物 / 病虫害和可改 UI / 提示词 / 后端规则的证据。当前只是 runbook 规划，不代表已有后台、定时任务或生产数据扫库；Codex 后续只应基于脱敏报表分析，不直接定期读取生产库原始聊天全文。
- 接入阿里云融合认证 Android 官方 SDK 首版链路：把控制台下载的 `fusionauth-1.2.15-online-release.aar` 放入 `app/libs` 并通过 Gradle `fileTree` 引入，补 ProGuard keep；新增 `FusionOneLoginClient.kt`，登录页在用户同意协议后按需申请 `READ_PHONE_STATE`，从后端拉取 `auth_token + scheme_code` 初始化 SDK，SDK 成功返回 verify token 后调用 `/api/auth/fusion/login` 换取账号 token，失败 / 取消则回落验证码登录。后端 `/api/auth/fusion/token` 同步返回 `scheme_code`，App 内隐私政策、第三方信息共享清单、个人信息收集清单和应用权限已补阿里云融合认证 SDK、电话状态、网络状态 / Wi-Fi 状态口径；当前还未做用户真机回归，仍需备案 / HTTPS 后统一复测。
- 复查主聊天输入框 `+` 面板和帮助与反馈 `+` 面板：两处“相机 / 照片”入口本来已经复用同一个 `ComposerAttachmentBottomSheet`，面板图标统一为同一尺寸常量；同步把帮助与反馈输入框 `+` 图标从 25dp 调整为 26dp，和主聊天输入框正常宽度下的入口图标一致。该刀只改图标尺寸常量和帮助反馈入口尺寸，不改图片选择、拍照、上传或发送链路。
- `LoginScreen.kt` 精简并打磨手机号登录页：按用户要求删除“登录后同步问诊记录、会员权益和多设备使用状态”和“登录后不会清空本机记录，旧记录会自动迁移到账户”两句说明；协议勾选区改为可点击的《服务协议》《隐私政策》，弹出设置页同一套本地内置正文，避免登录页和设置页两套协议口径并存。该刀只改登录页展示与协议入口，不改手机号短信接口、融合认证 SDK 状态、账号迁移后端、会员权益或聊天滚动链。
- 通过阿里云 CLI 查询到短信资质已通过，短信签名 `北京农技千问科技` 已通过，并提交 / 审核通过验证码模板 `农技千查登录验证码`（模板 Code 已写入本机生产密钥文件和 ECS `/etc/nongjiqiancha/server.env`，不在文档记录密钥类信息）；重启 `nongji-server` 后 `/healthz` 显示 `dypns=ok / dypns_fusion=ok / dypns_sms=ok / redis=ok / upload_storage=oss`。当时 `api.nongjiqiancha.cn` 仍受 ICP 备案拦截，已临时让 Nginx 接受 `39.106.1.151` Host 直连用于真机验证码登录联调，并新增 debug-only manifest 允许调试包明文 HTTP；release 仍强制 https `UPLOAD_BASE_URL`。截至 2026-06-05，网站 ICP 和 `api` HTTPS 已补齐，正式图片问诊仍待模型 Key 和真机 App 链路回归。
- 通过阿里云 CLI 为农技千查创建融合认证 Android 方案，并把 DYPNS AccessKey / Secret、`DYPNS_FUSION_SCHEME_CODE`、包名 `com.nongjiqiancha` 和 release 签名 MD5 去冒号小写值写入本机密钥文件与 ECS `/etc/nongjiqiancha/server.env`；重启 `nongji-server` 后只读 readiness 显示 ECS service active、Nginx OK、Host healthz 200、`dypns=ok`、`dypns_fusion=ok`、`DYPNS_FUSION_SCHEME_CODE=set`、`redis=ok`、`upload_storage=oss`。短信签名 / 模板随后已补齐并使 `dypns_sms=ok`；此前截图暴露过的主账号 AccessKey 必须在上线前轮换。
- 继续补上传成本防刷边界：`/upload` 在已有登录鉴权、单张 `<=1MiB`、仅 JPEG、公开 base URL 必须 https 和 OSS 私有写入基础上新增短期限流，默认同一 `user_id + IP` 10 分钟 120 次；配置 Redis 时跨进程共享，未配置 Redis 时回退本进程限流。Redis key 只保存 `user_id` hash 和 IP hash，不保存图片内容、图片 URL、手机号、token、聊天正文或模型 Key；该刀只保护上传入口和 OSS 成本，不改 Android 压缩链、图片预览、WorkManager、聊天流、额度、摘要或帮助与反馈消息结构。`go test ./...`、`git diff --check` 和项目记忆检查已通过；commit `065a3f73` 已部署到 ECS，readiness 复查显示 systemd active、Nginx OK、Host healthz 200、`redis=ok`、`upload_storage=oss`。
- 继续补帮助与反馈防刷边界：`POST /api/support/messages` 在已有登录鉴权、正文 2000 字、单次最多 4 图和图片 URL 校验基础上新增短期限流，默认同一 `user_id + IP` 10 分钟 20 条；配置 Redis 时跨进程共享，未配置 Redis 时回退本进程限流。Redis key 只保存 `user_id` hash 和 IP hash，不保存反馈正文、图片内容、手机号、token、聊天正文或模型 Key；该刀只保护帮助与反馈写库，不改客服历史读取、后台回复、图片上传、聊天主链、额度、摘要或 Android 滚动链。`go test ./...`、`git diff --check` 和项目记忆检查已通过；commit `4bedc613` 已部署到 ECS，readiness 复查显示 systemd active、Nginx OK、Host healthz 200、`redis=ok`、`upload_storage=oss`。
- 继续补后端防刷边界：`POST /api/app/logs` 在已有登录鉴权、8KiB body 上限和字段清洗基础上新增短期限流，默认同一 `user_id + IP` 10 分钟 60 次；配置 Redis 时跨进程共享，未配置 Redis 时回退本进程限流。Redis key 只保存 `user_id` hash 和 IP hash，不保存明文手机号、token、聊天正文、图片内容或反馈正文；该刀只防 App 自动日志异常循环写库，不改日志字段、Android 上报点、帮助与反馈、聊天主链或 SLS 方案。`go test ./...`、`git diff --check` 和项目记忆检查已通过；commit `ac52cc55` 已部署到 ECS，readiness 复查显示 systemd active、Nginx OK、Host healthz 200、`redis=ok`、`upload_storage=oss`。
- 在已有 Redis 认证限流基础上，补齐融合认证 token 入口的短期限流：`POST /api/auth/fusion/token` 现在默认按 IP hash 做 10 分钟 20 次频控，配置 Redis 时跨进程共享，未配置 Redis 时回退本进程限流；该 key 不保存明文 IP、手机号、验证码、token、聊天正文或图片内容。此刀只保护融合认证 SDK 后续接入后的阿里云 token 获取入口，不改短信发送 / 登录校验、不改账号 session 签发、不碰主聊天流、额度、归档、摘要、OSS 或 Android 滚动链。`go test ./...`、`git diff --check` 和项目记忆检查已通过；commit `9be4cc76` 已部署到 ECS，readiness 复查显示 systemd active、Nginx OK、Host healthz 200、`redis=ok`、`upload_storage=oss`；当时缺的 DYPNS 融合认证 / 短信配置已在 2026-06-01 补齐，当前仍缺 DashScope 模型 Key。
- 后端 Redis 先按最小生产口径接到认证限流，不碰聊天主链：`server-go` 新增可选 Redis 客户端，配置 `REDIS_ADDR / REDIS_USERNAME / REDIS_PASSWORD` 后启动会先 ping，失败 fail-fast；短信发送 `POST /api/auth/sms/send` 和短信登录校验 `POST /api/auth/sms/login` 改为可走 Redis 分布式短期限流，key 只保存 scope、手机号 hash 和 IP hash，不保存明文手机号、验证码、token、聊天正文或图片内容；未配置 Redis 时仍回退本进程限流。主聊天 `/api/chat/stream`、额度扣减、轮次归档、摘要和订单仍走原 MySQL / 现有主链，没有切 Redis。`go test ./...` 已通过；随后已把 `REDIS_*` 写入 ECS 环境文件并部署 commit `5b86941b`，只读就绪检查显示 systemd active、Nginx OK、Host healthz 200、`redis=ok`、`upload_storage=oss`；当时缺的 DYPNS 融合认证 / 短信配置已在 2026-06-01 补齐，当前仍缺 DashScope 模型 Key。

## 2026-05-31

- OSS 生产上传后端已真实切通：创建最小权限 RAM 子账号 / 策略并绑定 `nongjiqiancha-prod` 上传前缀所需对象权限，使用新凭证完成 OSS 上传 / 下载 / 删除冒烟测试；随后通过 Cloud Assistant 写入 ECS `/etc/nongjiqiancha/server.env`，重启 `nongji-server` 后 `/healthz` 返回 `upload_storage=oss`。本轮没有把 OSS AK/SK 写入仓库、文档或 Android，App / 模型仍走本后端 `/uploads/<file>.jpg` 读取。当时仍缺 DashScope 模型 Key、DYPNS 融合认证 / 短信配置、HTTPS 和备案闭环；Redis 认证限流已在 2026-06-01 接入并上线，网站 ICP 和 `api` HTTPS 已在 2026-06-05 补齐。
- 补远端图片过期展示和只读生产检查入口：主聊天远端历史图如果因 OSS 生命周期删除、404 或解码失败，缩略图显示“图片已过期”，全屏预览显示“图片已过期，仅保留文字记录”；帮助与反馈图片同样在远端图不可用时显示过期占位；debug-only 文案预览面板新增“图片已过期”占位预览。该刀只改远端图不可用展示和预览面板，不改图片上传、WorkManager、模型上下文、首屏 / 96dp 工作线或正常滚动链。同时新增 `scripts/check-ecs-readiness.ps1` 只读检查脚本，已验证 ECS systemd active、Nginx OK、Host healthz 200。后续 OSS 已完成生产切换，DYPNS 融合认证 / 短信配置和 Redis 认证限流已在 2026-06-01 接入并上线；当前仍缺 DashScope 模型 Key。`git diff --check` 和 `.\gradlew.bat :app:compileDebugKotlin` 已通过。
- 将后端新版源码包 `a08bf15e` 部署到 ECS：Cloud Assistant 分片下发、ECS 上 `go test ./...`、编译、备份旧二进制、替换并重启均完成；重启瞬间 Nginx healthz 曾短暂 502，延迟复查 systemd active、Go 直连和 Nginx Host healthz 均 200，返回 `ok=true / auth_strict=true / bailian=missing_key / dev_order_endpoints=false / upload_storage=local`。本轮同时修复 ECS 发版脚本：等待 `SendFile` 真正成功、远端构建默认走 `goproxy.cn,direct`，并让 healthz 重试且校验 HTTP 200，避免把重启瞬间 502 当成功。
- 生产化继续收口：Android 移除 `SESSION_API_TOKEN` BuildConfig 字段和运行时登录绕过，登录门、会话接口和图片上传只认手机号账号 session token；后端开发期会员订单接口改成“必须显式 `ALLOW_DEV_ORDER_ENDPOINTS=true` 且环境名为 `local / dev / development / test` 才放行”，缺失环境名也关闭；阿里云 OSS Bucket `nongjiqiancha-prod` 已创建为北京私有标准本地冗余，并配置 `uploads/` 问诊图 3 天、`support/` 预留反馈图 30 天生命周期，`server-go` 新增可配置 OSS 上传存储后端，生产 ECS 配好 OSS 环境变量后 `/upload` 写私有 OSS、App / 模型仍走本后端 `/uploads/<file>.jpg`；融合认证签名指纹参数按阿里云 Android 口径改为去冒号小写。`go test ./...`、`go build ./...` 和 `.\gradlew.bat :app:compileDebugKotlin` 已通过。
- 手机号登录先落最小账号骨架：`server-go` 新增手机号账号表、登录 session 表、旧本机 `user_id` 迁移表、v2 bearer token、阿里云融合认证 / 短信验证码服务端接口和短信发送单进程限流；手机号登录成功后用手机号 HMAC hash 归一到稳定 `acct_...` 用户，并把旧本机用户的聊天归档、额度账本、会员、帮助与反馈等数据尽量迁到账户下，`chat_stream_inflight` 这类临时租约不迁移。Android 新增登录门和登录页，登录成功后保存长期 token 并使用账号 `user_id`；`AUTH_SESSION_DAYS` 默认 3650 天，当前按长期保持登录、省认证次数处理。本日随后已移除静态 `SESSION_API_TOKEN` 运行时绕过，不再保留测试 token 登录口。一键登录后端 token 接口已留好，但因阿里云 Android SDK / SchemeCode 尚未接完，前端暂不消耗融合认证试用次数，先引导验证码登录。`go test ./...`、`.\gradlew.bat :app:compileDebugKotlin` 已通过。
- 收到小米 / MiMo 对 `TopAnchoring` 的二次会诊后，修正上一刀“两帧交接”风险：`TopAnchoring` 不再先 force 锚点、`withFrameNanos` 等一帧、再切 `WorklineOwned`；现在只有在列表可正向滚动、首屏文档流底边已超过 96dp 工作线约 56dp、且用户没有触碰 / 拖动 / 浏览时，才同一执行点设置 `WorklineOwned` 并立刻复用现有 `requestProgrammaticForwardListBottomAnchor(force = true)` 接一次。`TopAnchoring` 的 effect 同步纳入文档流底边 / 工作线 / 安全越线结果作为触发 key，避免 streaming 或图片布局继续长高后停在旧判断；用户正在拖动 / 滚动 / 浏览时仍只交权不抢锚；用户只是触碰时不自动切换。同步确认图片 pending / 图片失败 / assistant 失败态均通过整条消息 Column bounds 进入首屏文档流判断。
- 按小米 / MiMo 最新会诊和代理复查，把首屏触线交接从“TopUnreached 直接切 Bottom”改成极短 `TopAnchoring` 桥：清数据 / 删除历史后的首屏真实内容仍先用 `Arrangement.Top` 从上往下排；文档流底边触到 96dp 工作线后，如果用户正在拖动 / 浏览只交权不抢手势，如果用户未浏览则继续保持 Top 布局，等列表已经有正向可滚范围时复用现有强制底部锚点接一次，下一帧再交回 `WorklineOwned / Arrangement.Bottom`。这刀只改首屏交接状态机和对应 gate，不恢复 `HandoffPending`、`SparseBottomSpacer`、`cleanStateSparse`、TextMeasurer 稀疏估高、反向列表、overlay 或 raw delta，也不改正常有历史后的工作线滚动链。
- 今日农情展示口径收口：同一用户当天只要还没有发送过文字、图片或失败态消息，今日农情可以展示；当天一旦发送，就把 `date_cn` 规范化出的 `yyyyMMdd` 日期键写入本机 `chat_ui_cache` 并让今日农情当天永久隐藏；删除历史后当天也不重新显示，第二天拉到新日期卡片后再出现。真实 `messages`、A/B/C、扣次、发送、重试和工作线判断仍不包含今日农情。
- 按小米 / MiMo 会诊和 3 路代理复查结论，撤掉首屏 `HandoffPending` 运行时交接态：首屏状态机收口为 `WaitingForFirstSend -> TopUnreached -> WorklineOwned`。`TopUnreached` 只负责让清数据 / 删除历史后的真实业务内容从顶部往下排；文档流底边到达 96dp 工作线或出现正向可滚范围时，直接交回正常 `Arrangement.Bottom` 主链，用户未触碰时同拍只请求一次现有正向底部锚点，用户正在拖动 / 浏览时只交权不抢手势。发送入口新增“已触线就不再回到 TopUnreached”的判断，`prepareScrollRuntimeForStreamingStart` 只在真实拖动 / 滚动时保留 `UserBrowsing`，避免 stale 浏览态导致后续“你好”不跟随工作线。旧 `SparseBottomSpacer`、`cleanStateSparse`、TextMeasurer 稀疏估高、反向列表、overlay、raw delta 仍未恢复。
- 用户真机截图复现清数据 / 删除历史后的首屏超长文字仍会压到输入框下方。再拉 3 路代理并联网核对 Compose `LazyListState` / `LazyColumn` 官方文档后，确认不是旧 UI 回退或旧稀疏方案残留，而是 `TopUnreached` 触线检测只看最后一条逻辑消息；发送后最后一条通常是 assistant placeholder / 小球，首条超长用户消息会把它挤到未组合区域，导致 `currentLastMessageContentBottomPx()` 返回 `-1`，上抬锚点不触发。本轮 `ChatScreen.kt` 新增首屏专用 `currentInitialDocumentFlowBottomPx()`，按当前业务消息已测 content bounds、streaming bottom 和可见消息 item 最大底边判断整段文档流是否碰到 96dp 工作线；首屏阶段 content bounds 跟踪也放宽到当前文档流内消息。触线后仍复用现有正向底部锚点进入 `HandoffPending`，不改正常 `WorklineOwned / Arrangement.Bottom`、96dp 工作线、AutoFollow、图片 / 失败态 bounds，也不恢复 `SparseBottomSpacer`、`cleanStateSparse`、TextMeasurer 稀疏估高、反向列表、overlay 或 raw delta。`git diff --check` 和 `.\gradlew.bat :app:compileDebugKotlin` 已通过，仅剩既有 deprecated 警告。
- 再拉 3 路代理复查首屏未触线链路后补两处小护栏：本地确认运行时代码没有 `SparseBottomSpacer`、`cleanStateSparse`、动态稀疏 padding / spacer、TextMeasurer 稀疏估高、反向列表、`asReversed()` 或 raw delta 旧链残留；`ChatScreen.kt` 只在 `InitialWorklinePhase.TopUnreached` 下清理已经回到物理底部且不再拖动 / 滚动的 stale `UserBrowsing / userInteracting`，避免首屏图片失败或弱网后用户轻划一下就卡住 handoff；本地聊天窗口快照新增 `initialWorklineOwned` 标记，首屏未触线 / HandoffPending 时被系统杀掉再进入，仍能恢复为未触线阶段，真正交给 96dp 工作线后才持久化为 owned。正常 `WorklineOwned / Arrangement.Bottom`、96dp 工作线、AutoFollow、图片 / 失败态 bounds、今日农情隐藏规则和旧滚动主链未改。`git diff --check` 和 `.\gradlew.bat :app:compileDebugKotlin` 已通过，仅剩既有 `LocalLifecycleOwner` deprecated 警告。
- 用户真机复测指出首屏触线时仍有“先下掉再上抬”，且首屏长文本时小球 / 尾部没有及时抬到工作线。确认根因是 `HandoffPending` 交给 Bottom 仍太早，且 `HandoffPending` 继续 suppress 普通锚点会让长文本触线后的自动上抬慢一拍。本轮按“首屏文档流”收口：`TopUnreached` 仍不滚；触线进入 `HandoffPending` 时继续保持 Top 布局，但不再 suppress 自动底部锚点，并立刻复用现有正向底部锚点把最新内容 / 小球压到 96dp 工作线；只有列表已经真实从顶部滚动过（`canScrollBackward`）且最新内容不在工作线下方时，才交回 `WorklineOwned / Arrangement.Bottom` 主链。该刀不全局抬高工作线、不改完成态底部空白、不恢复旧 spacer / dynamic padding / raw delta 链。`git diff --check` 和 `.\gradlew.bat :app:compileDebugKotlin` 已通过。

## 2026-05-30

- 将后端最新 `server-go` 变更部署到 ECS：本地打包 `176c20a0` 源码后通过 Cloud Assistant 分片下发到 `/tmp`，ECS 上校验 SHA-256、运行 `go test ./...`、编译、备份旧二进制、替换并重启 `nongji-server`。重启瞬间 Nginx healthz 曾出现一次 502，延迟复查后直接 Go 端和 Nginx Host healthz 均返回 `ok=true / auth_strict=true / bailian=missing_key / dev_order_endpoints=false`，服务监听仍为 `127.0.0.1:3000`，未读取或打印任何生产密钥。
- 新增可复用 ECS 发版脚本 `scripts/deploy-ecs-server.ps1`：本地打包 `server-go`、按 Cloud Assistant `SendFile` 32KB 限制分片、ECS 上校验 SHA-256、跑 `go test ./...`、编译、备份旧二进制、替换、重启、`nginx -t` 和 Host healthz；脚本支持 `-PackageOnly` 只验证打包，不读取或打印生产环境变量。
- 新增 ECS 二进制回滚脚本 `scripts/rollback-ecs-server.ps1`：默认只通过 Cloud Assistant 列出 `/opt/nongjiqiancha/server/nongji-server.bak-*` 和当前二进制，真正回滚必须传 `-BackupName nongji-server.bak-YYYYMMDDHHMMSS -Apply`，执行前会校验备份名格式，回滚后重启服务、跑 `nginx -t` 和 Host healthz。本轮已用只读列表模式验证，未执行回滚。
- 收到子代理对 `InitialWorklinePhase` 当前实现的复查后，补一处 handoff 极端手势护栏：`HandoffPending` 只保留 `SideEffect` 同拍的一次 `requestProgrammaticForwardListBottomAnchor(force = true)`，下一帧只把阶段交还 `WorklineOwned`，不再第二次 force 回底，降低用户刚好开始拖动时被抢一次手势的风险。不改触线判断、不改 96dp 工作线、不改图片 / 失败态 bounds，也不恢复旧 spacer / dynamic padding 稀疏链。
- 后端继续做一刀低风险防打 / 高并发可调参数：`server-go` 主聊天应用层用户限流从硬编码 `20 次 / 60 秒` 改为可通过 `CHAT_RATE_LIMIT_MAX_HITS`、`CHAT_RATE_LIMIT_WINDOW_SECONDS`、`CHAT_RATE_LIMIT_PRUNE_INTERVAL_SECONDS` 调整，默认值保持不变；限流器新增过期用户桶清理，避免长时间运行时被大量过期 user_id 撑大内存。该刀不接 Redis、不改 Nginx IP 限流、不改模型调用、扣次、归档、inflight 租约或 SSE 主链。`go test ./...` 已通过。
- 重新按“未触线阶段”实现清数据 / 删除历史后的首屏消息布局，并复查旧方案残留：`ChatScreen.kt` 新增 `InitialWorklinePhase`，只有发送前没有任何真实业务消息时才进入 `TopUnreached`；该阶段临时 `Arrangement.Top`，真实用户消息、图片 pending / 失败、assistant placeholder、失败 footer 和 streaming 小球都在同一个正向 `LazyColumn` 里从顶部自然向下排，并 gate 普通回底、AutoFollow 预锚、sendStart bottom anchor 和 `scrollToBottom(false)`。最新真实消息内容底边到达 96dp 工作线后进入 `HandoffPending`，force 一次正向底部锚点并切回 `WorklineOwned / Arrangement.Bottom`，之后完全交回原 96dp 工作线、发送期 bottom-lock 与 AutoFollow 主链。今日农情卡片在未触线阶段临时隐藏，避免 UI-only 卡片提前触线；`SparseBottomSpacer`、`cleanStateSparseLayoutActive`、动态稀疏 padding / spacer、反向列表、overlay、raw delta 等旧方案没有恢复。`.\gradlew.bat :app:compileDebugKotlin` 已通过，仅剩既有 `LocalLifecycleOwner` deprecated 警告。
- 购买并核验阿里云 Redis 开源版最小实例：`nongjiqiancha-prod-redis` / `r-2zet46zvmoo9wu3bic`，Redis 7.0、256MB、标准高可用主备、包年包月到期 `2027-05-30T16:00:00Z`，位于生产 VPC `vpc-2zeax2zowza2398b9dzot` 和北京可用区 L 交换机 `vsw-2zemsq82lj2kp8za90aky`，内网地址 `r-2zet46zvmoo9wu3bic.redis.rds.aliyuncs.com:6379`。已把白名单收敛到 `127.0.0.1` 和 ECS 私网 IP `192.168.1.237`，并从 ECS 验证 DNS 与 TCP 6379 可达。当时 `server-go` 尚未接入 Redis，仅作为验证码、限流、缓存和多实例锁的预备资源；后续已在 2026-06-01 接入认证短期限流。新增 `docs/runbooks/redis.md`。
- 清理阿里云北京区无关网络资源：通过 CLI 确认 ECS、RDS、网卡、安全组、NAT、EIP、SLB、SAE 均未占用空闲资源后，删除旧 SAE 自动交换机 `vsw-2ze3elcd2iad6n1madi5g`、空默认交换机 `vsw-2zemrmbor6c886z5rul20` 和空默认 VPC `vpc-2zeceqyrcmnxhoaxxzjks`；生产 VPC 改名为 `nongjiqiancha-prod-vpc`，生产交换机改名为 `nongjiqiancha-prod-beijing-l`，系统路由表改名为 `nongjiqiancha-prod-system-rt`。当前北京区只保留生产 VPC `vpc-2zeax2zowza2398b9dzot`、生产交换机 `vsw-2zemsq82lj2kp8za90aky` 和系统路由表 `vtb-2ze7xjciht46x324zgt7z`；ECS / RDS 仍在该生产网络内。
- 同步复查 SLS：北京区仍只有 `default-cms...`、`proj-xtrace...`、`aliyun-product-data...` 这 3 个阿里云系统 / 产品托管日志 Project，未发现农技千查专用业务日志 Project。为避免影响云监控、XTrace / APM 或产品事件，本轮不删除 SLS Project；后续若确认不用 ARMS / APM 或旧 SAE 产品事件，应先从对应产品控制台关闭 / 清理，再删除 SLS 资源。

## 2026-05-28

- 拉后端安全 / Android 长用性能会诊后继续收口：B/C 摘要模型非流式响应现在同样限制 64KiB，避免异常上游大包绕过主聊天 body cap；App 自动日志接口的 8KiB `MaxBytesReader` 超限统一返回 `413 body_too_large`；MySQL schema migration 全局锁释放失败会作为启动错误暴露，不再静默吞掉；聊天区远端历史图片缩略图预览单次读取最多 2MiB，异常大图跳过本地缩略图解码。30 轮 UI 长用复查结论是：前端和后端都已把 UI 历史收在最近 30 轮内，列表长度本身不是当前卡顿主风险，后续更该关注超长单条回复、图片缩略图和真实 release Baseline Profile。本轮已在 ECS 上用同源 `server-go` 跑 `go test ./...` 后编译并重启 `nongji-server`，Nginx Host healthz 仍返回 `ok=true / auth_strict=true / bailian=missing_key / dev_order_endpoints=false`。
- 复查上线构建与部署记忆时补一处正式包护栏：Android release 任务现在会在 `SESSION_API_TOKEN` 非空时 fail-fast，避免把本地 / 内测共享静态 token 打进正式 APK 导致多设备串成同一用户；同时把根 `AGENTS.md` 和部署 / 查库 runbook 里的 RDS 白名单口径统一为当前真实 `192.168.1.237`。
- 继续做后端安全 / 高并发第五刀：上游模型错误响应和非 SSE 响应的错误体预览限制为 64KiB，今日农情 DashScope JSON 响应限制为 1MiB，避免异常上游返回大包时被 `io.ReadAll` 无上限读入内存；正常主聊天 SSE 正文流仍按流式转发，不套全局 `http.Client.Timeout`。
- 继续做后端安全 / 高并发第四刀：服务启动迁移改为先在单连接上获取 MySQL 全局命名锁 `nongji_schema_migration`，拿到锁后再按顺序执行 `migrations/*.sql`，完成后释放锁；迁移整体默认 2 分钟超时，可用 `MYSQL_MIGRATION_TIMEOUT_SECONDS` 调整。当前仍是单 ECS，但这能提前避免后续滚动发布 / 多实例同时执行 DDL 抢 metadata lock；本轮不新增 `schema_migrations` 表，历史幂等 SQL 仍按现有方式执行。
- 继续做后端安全 / 高并发第三刀：通用 JSON body 解析增加默认 64KiB 读取上限，并拒绝同一个请求体里夹带多段 JSON；超过限制返回 `413 body_too_large`。App 自动日志接口仍保留更小的 8KiB 上限，图片上传仍按单张 JPEG `<=1MiB` 单独处理。本轮只改请求体解析边界和错误返回，不改接口字段、业务扣次或数据库表结构。
- 继续做后端安全 / 高并发第二刀：模型出站 HTTP client 改为复用带拨号、TLS 握手、响应头等待和空闲连接限制的 `http.Transport`，但不设置全局 `http.Client.Timeout`，避免误杀 SSE 正文流；主聊天流增加 `CHAT_STREAM_MAX_DURATION_SECONDS` 兜底，默认 30 分钟；SSE 响应增加 `X-Accel-Buffering: no`，提示 Nginx 不缓冲流式响应。本轮只改出站连接和 SSE 头部 / 生命周期，不改计费、鉴权、归档或 Android 滚动链。
- 后端继续推进并做首轮高并发 / 安全硬化：本地 `server-go` 已通过 `go test ./...` 和 `go build -buildvcs=false ./...`；ECS 上刷新到当前仓库二进制后，`nongji-server` 已从历史 `*:3000` 收口为 `127.0.0.1:3000`，继续只由 Nginx 对外代理。本轮代码层把 `cmd/server` 从裸 `http.ListenAndServe` 改成显式 `http.Server`，默认限制慢请求头、请求读取、空闲连接和最大 header，并支持优雅停机；`WriteTimeout` 默认保持 `0`，避免误杀 SSE 长回答。仍未配置 DashScope 模型 Key，公网域名仍受未备案拦截，HTTPS 仍待证书配置。
- 用户真机截图确认：无网图片失败原本被 `SparseBottomSpacer` 顶在上方，但再次发送文字后失败图片和新消息都会掉回工作线，说明 clean-state 稀疏首屏仍在和正常滚动链互相抢位置。本轮最终撤掉“首发靠上 / 稀疏首屏”运行时例外：`ChatScreen.kt` 删除 `SparseBottomSpacer`、`cleanStateSparseLayoutActive`、稀疏高度预估和相关 gate，真实聊天消息、图片 pending / 失败、重试态和 streaming 小球统一回到 96dp 工作线、发送期 bottom-lock 与 AutoFollow 主链。后续空白首屏体验不再通过移动真实消息解决，只能考虑欢迎语、示例问题或今日农情这类非聊天消息占位；不要恢复 `Arrangement.Top`、反向列表、active-zone、overlay、raw delta、contentPadding 稀疏或 spacer 稀疏旧方案。
- 针对用户真机发现“清数据 / 删除历史后无网发图片失败，随后再发消息又从底部工作线出来”的边角问题，拉 3 路代理复查 clean-state 稀疏首屏。两路工程审查确认根因不是滚动主链漏锚，而是稀疏态把失败图片用户消息当成普通历史，同时 `canScrollForward` 会被 UI-only `SparseBottomSpacer` 自己误触发，导致稀疏态提前退出；一路产品审查建议若仍复杂可整条撤掉首发靠上。该条为中间过程：当时曾先保留首发靠上并收窄规则，随后已被上一条“撤掉稀疏首屏、统一回 96dp 工作线”替代。
- 备案提交后同步启动软件著作权材料准备：确认软著与 ICP / App 备案、前后端继续开发可以并行；软著按 `农技千查 Android应用软件 V1.0` 快照口径推进，后续正常 UI 修复、后端联调、登录 / 支付 / OSS / SLS 接入不影响本次 V1.0 申请。本机桌面已生成 `D:/Desktop/农技千查软著材料`，包含申请信息草稿、提交步骤简表、操作说明书 docx、Android 客户端源代码前后各 30 页摘录 docx 和阅读版 txt；源代码摘录只取 Android 客户端 Kotlin 源码，不包含 release 签名配置、私钥、密码、本机 secret、模型 Key 或后端生产环境变量。新增 [software-copyright.md](D:/wuhao/docs/runbooks/software-copyright.md) 固化申请入口、材料路径和并行关系。
- 用户真机确认 spacer 方案已把 clean-state 首发用户消息和 waiting 小球顶到顶部栏下方，但 streaming 前几行吐字仍有“一上一下跳闪”。本轮定位为：`SparseBottomSpacer` 高度此前主要等 LazyColumn 实测内容高度回写，文本新行先增高一帧、下一帧 spacer 再缩回，形成补偿式跳动。`ChatScreen.kt` 现在只在 clean-state 稀疏态 active 且 streaming 中，用 `TextMeasurer` 按当前 assistant streaming 文本、实际 item 宽度、renderer 同款段落 / 标题 / 列表 / 编号 / 引用样式、block 间距和免责声明 reserve 预估内容高度，并与真实 bounds 单调高度取最大值，让 spacer 在同一帧先收缩；streaming 结束后回到真实 bounds 优先。失败态 footer、图片消息、图片上传失败等仍继续走真实 Column bounds 参与退出判断。不恢复 overlay、raw delta、反向列表或 `Arrangement.Top`。
- 小米 / MiMo 会诊确认真机仍从工作线出发时，`contentPadding.bottom` 方案大概率被 LazyListState 首帧锚点 / viewport 高度时序抵消。本轮把 clean-state 稀疏定位从“动态加大 bottom padding”改为“末尾 UI-only `SparseBottomSpacer` item”：`ChatTimelineItem` 新增透明 spacer，稀疏态按消息实测高度 / 首帧估算高度计算 spacer 高度并追加在真实消息之后，`Arrangement.Bottom` 保持不变，让 spacer 作为真实 item 把 user/assistant 内容顶到顶部栏下方约 `32dp`；内容高度测量排除 spacer 自身，退出条件改为内容到工作线且 spacer 缩回容差内或列表可滚动。仍不恢复 `Arrangement.Top`、反向列表、active-zone、overlay 或 raw delta。
- 用户真机复测反馈首发消息仍在工作线附近往上滚后，继续收紧稀疏态即时判定：普通回底请求、程序回底、`scrollToBottom(false)`、streaming 同帧预锚和 sendStart lock 不再依赖可能晚一拍的 `cleanStateSparseLayoutEligible` 派生值，而是直接读取 `cleanStateSparseLayoutActive && 当前 messages 已有业务消息`。这样发送事务里刚插入首条 user/assistant 时，普通工作线回底链不会抢在稀疏 bottom padding 生效前先把列表锚到工作线。
- 三路子代理复查 `Arrangement.Bottom + 动态 bottom padding` 稀疏首屏方案后补护栏：稀疏态实测内容高度改为只单调增，避免 streaming 重排或图片稳定时临时 bottom padding 来回伸缩；稀疏退出除了 `canScrollForward` 外，还要求最新内容底边到达工作线且临时 bottom padding 已缩回容差内，减少提前退出造成的微动；有图片的用户消息统一用整条消息 Column bounds 参与工作线判断，避免未来图片 / 文字顺序调整后底边漏算；列表 bottom padding 的 sendStart lock 消费显式排除稀疏态，固定“稀疏首发绝不吃发送起步锁”的代码不变量。同步把 2026-05-26 历史 `Arrangement.Top` 条目标注为已被本日方案替代。
- 收到小米 / MiMo 会诊后复核 clean-state 首轮“用户消息下掉再上抬”根因：旧稀疏主链确实会在内容到达工作线时从 `Arrangement.Top` 切回 `Arrangement.Bottom`，存在一拍 layout jump 风险。本轮移除稀疏态 arrangement 切换，`ChatRecyclerViewHost.kt` 全程保持 `Arrangement.Bottom`；`ChatScreen.kt` 改为按稀疏态实测内容高度临时加大 bottom padding，并随文字 / 图片 / 间距 / 失败 footer 真实高度增长自然缩回正常 96dp 工作线。没有照抄会诊稿里的 dynamic top padding，因为在 `Arrangement.Bottom` 下额外 top padding 对短内容位置会被抵消；本轮使用 bottom padding 承接同一目标，避免新旧两套锚点并存。
- 用户真机反馈清数据后首发消息仍落在大屏手机中上位置，不够靠近顶部，并且首轮 streaming 吐字到一部分时顶部用户消息会先往下掉一下再往上抬。确认稀疏首屏在顶部栏保留高度外还叠加固定呼吸区，先把 `CLEAN_STATE_SPARSE_TOP_OFFSET` 收到 `32dp`，让 clean-state 首条内容更接近顶部栏下方；同时 clean-state 稀疏首发不再启用普通发送起步 bottom-lock / `sendStartAnchorActive`，避免内容刚到工作线时和锁释放叠加造成一拍下掉再上抬。正常有历史消息发送仍保留发送起步锁；“未到工作线不拉底、到工作线恢复 AutoFollow / 小球工作线”的主规则不变。
- 再拉 3 路代理专项复查 clean-state 首轮工作线逻辑：两路确认当前主链已经符合“空白首屏自然往下排，内容底边到 96dp 工作线后恢复正常 AutoFollow / 小球工作线”，且没有旧 active-zone、反向列表、raw delta、overlay 第二主人或旧图片手势链并存；一轮极端场景会诊指出首轮长回复跨工作线可能多等一帧才锚底、删除历史后旧 LazyListState 可提前归零、图片上传等待期拖动意图可能被 streaming start 重置。已按最小改动补护栏：删除历史成功立即请求列表归零；稀疏退出后若用户未浏览则同拍请求正向底部锚点，不再先等一帧；稀疏态 / 图片上传等待态的真实拖动会标记 `UserBrowsing`，进入 streaming 时保留该浏览意图。IME / 输入框展开不抬升消息工作线的既定规则未改。

## 2026-05-26

- 按用户“是不是只检测有没有到工作线”的思路又拉 3 路会诊复查 clean-state 稀疏首屏：结论是当前方案本质已经是“首次业务消息前进入稀疏；真实内容底边没到 96dp 工作线就自然增长；到达工作线或内容可滚动后恢复正常工作线”，不建议推倒成一次性发送前 / 发送后检测。基于极端场景会诊只补两处小护栏：稀疏态退出后只有在用户未拖动、未浏览且列表滚动已停止时才主动请求正向底部锚点，避免首轮长回答跨工作线时把正在浏览的用户拉回底部；纯图片用户消息会用消息 Column 真实 bounds 参与底边判断，减少对 LazyList item fallback 的依赖。本轮继续确认没有恢复旧 active-zone、反向列表、overlay 或 raw delta 链。
- 深度复查 clean-state / 删除历史链路后继续补护栏：`ChatScreen.kt` 删除历史成功会把 `hasStartedConversation`、首屏贴底完成标记和滚动模式一并归回 clean-state；稀疏布局启用后先把正向列表钉回顶部，再允许 `canScrollForward` 触发退出，避免删除前旧滚动位置把首轮短内容提前拉回底部。后端 replay 链同步收紧：用户存在 `session_generation` 清空代际后，缺失 generation 的 `/api/chat/stream` 直接按 stale 拒绝；`session_round_ledger` replay 会检查完成时间，清空前完成的同 `client_msg_id` 不允许在清空后幽灵回放。新增后端单测锁住缺失 generation 和清空前 ledger replay 的判定。
- 服务协议 6 个板块再复核成备案 / 上架前更像成品的口径：用户协议、隐私政策、第三方信息共享清单、个人信息收集清单、应用权限和风险提示不再使用“后续接入 / 当前未接入”式阶段性正文；统一按“本版本不提供真实支付、自动续费、礼品卡兑换或农资商品交易”表达当前边界，同时保留服务范围变化时按页面、协议、订单和资质信息明确展示的口径。会员中心支付提示改为“支付功能暂不可用”，账号 / 礼品卡等未开放入口改为“暂不可用 / 登录后可用”类用户文案，不再展示开发占位感。
- 主聊天 clean-state / 删除历史后的首屏稀疏布局按用户确认收口：发送前若没有用户 / assistant 业务消息，`ChatScreen.kt` 会临时启用 `cleanStateSparseLayoutActive`，让正向 `LazyColumn` 从默认 `Arrangement.Bottom` 切到 `Arrangement.Top` 加轻量 top offset，避免首条用户消息和短回答硬贴在底部工作线；最新真实消息的实测底边到达或超过正常 96dp 工作线、或正向列表从顶部还能继续向下滚动（`canScrollForward`）时，自动恢复默认底部工作线和 AutoFollow 锚点。退出判断使用真实布局坐标，文字、图片、图文间距和失败 footer 都算，不按第几轮、文本长度或是否有图片做猜测；本轮没有恢复旧 active-zone、反向列表、overlay 或 raw delta 链。本条为历史记录，2026-05-28 已被“撤掉稀疏首屏、真实聊天消息统一回 96dp 工作线”方案替代。
- 子代理复查 clean-state 稀疏布局后补极端护栏：稀疏退出条件收为 `canScrollForward` 或真实消息底边到达 96dp 工作线，避免 `canScrollBackward` 吃到删除历史前残留滚动位置后把首发提前拉回底部；稀疏态临时不插入 UI-only 今日农情卡片，避免非业务卡片把首条图文提前推过工作线；稀疏退出后的底部恢复统一走 programmatic bottom anchor，避免被 streaming 手势状态机误判成用户浏览。删除历史成功会取消前台图片上传 job，并用 `chatHistoryClearEpoch` / pending 复查拦截上传回调、pending 图片恢复、WorkManager 后台补发、queued mainHandler 旧回调和本地窗口 / streaming draft / composer draft 延迟保存，避免“图文正在上传或后台补发时删除历史，旧消息晚回来又插回 UI / 开流”；后端 `/api/session/clear` 与 `/api/chat/stream` 获取同用户 inflight 租约前共用 MySQL 用户级命名锁，并新增 `session_generation` 表，清空历史递增 generation，开模型前和归档前校验 generation，不匹配的旧流不会写回会话。Android 同步持久化最新 generation，本地聊天窗口、streaming draft、composer draft 和 WorkManager pending 图文都会带 generation，不匹配时直接丢弃，避免新进程后台补发旧图文。streaming 期间普通点按不再直接进入 `UserBrowsing`，只有超过 touch slop 的真实拖动才暂停 AutoFollow。同步确认 baseline profile 脚本无需更新。
- 主聊天正文渲染巡检并补齐链接可见性：AI 回复的轻量 Markdown 链接和裸网址继续走 `LinkAnnotation.Url`，现在统一显示蓝色下划线；用户消息气泡从纯文本改为识别 `http://`、`https://` 和 `www.` 裸网址，保持可选择复制的同时可点击打开。同步在 debug-only UI 文案预览面板新增“文本渲染”分组，用主界面真实组件预览 AI Markdown、简单 Markdown 表格和用户链接气泡。当前简单表格仍按手机窄屏友好的条目形式展示，不渲染横向网格，避免聊天页小屏溢出；复杂 Markdown、HTML、代码块高亮和图片 Markdown 不作为主链支持范围。
- 主聊天轻量 Markdown 继续做边界加固：粗体、行内代码和斜体标记只有确认成对时才会被吞掉；单个 `*` 增加边界判断，避免农业计算、配比或公式里的 `亩数*亩用量*浓度` 被误判成斜体后丢失乘号。debug-only 文本渲染预览同步加入该公式样例。
- 主聊天特殊符号渲染继续收口：裸 URL 末尾的常见中英文收尾括号 / 引号不再算进链接；`**` 粗体标记增加 ASCII 公式边界判断，避免 `2**3**4` 这类公式 / 程序表达被误吞。debug-only 文本渲染预览同步加入 `≤ / °C / % / 斜杠 / ± / N-P-K / **` 等符号样例。
- 主聊天通用特殊符号再巡检：表格拆列从直接 `split('|')` 改为识别转义竖线 `\|`，主渲染和流式渲染两条路径保持一致，避免 `A\|B` 这类普通符号内容被误拆成多列；debug-only 文本渲染预览同步加入 `@#$%^&+=~ / 括号 / 引号 / 箭头 / 数学符号 / emoji / 转义竖线` 等通用符号样例。
- 帮助与反馈消息文本不接完整 Markdown，改为纯文本加裸 URL 链接化：客服 / 用户消息气泡支持 `http://`、`https://`、`www.` 链接点击和选择复制，URL 末尾常见收尾标点不进入链接；输入框继续走 `BasicTextField` 系统原生复制 / 剪切 / 粘贴 / 全选菜单。键盘抬起时消息区域仍会随 `imePadding()` 变矮，但输入框获得焦点后会自动滚到底部，避免最新消息被缩窄视口挡住。
- 对比主界面输入框后，帮助与反馈输入框补齐核心稳定逻辑：输入状态从纯 `String` 改为 `TextFieldValue`，保留系统选择区间；无图片时文本编辑区至少保留一行 22dp，编辑区最高放宽到 132dp，`BasicTextField` 限制 `minLines=1 / maxLines=6`，避免长按选择、复制 / 剪切或键盘 toolbar 介入时输入区向 0 高度收缩。仍保留系统原生复制 / 剪切 / 粘贴菜单，不引入主聊天自定义黑色卡片。
- debug-only UI 文案样式预览面板继续补齐帮助与反馈整体状态：新增独立“帮助与反馈”分组，覆盖对话链接、空态、同步中、同步失败、图片输入和长文本输入；预览复用真实 `HamburgerSupportFeedbackContent`，用于检查图片预览条、输入框抬高、发送中态、加载失败重试和长文本不收缩，不新增 release 可见入口。
- 帮助与反馈空态文案收口为更正式的服务进展口吻：“提交反馈后，客服回复和处理进展会在这里显示。”只改用户可见空态文案，不改消息、图片、已读或发送链路。
- 相机 / 照片附件面板继续做视觉对齐：主聊天和帮助与反馈共用同一 `ComposerAttachmentBottomSheet`，本轮只把照片图标的内部绘制边界从偏窄调整为和相机图标相同的光学宽度，避免两个 30dp 图标盒子里照片看起来小一圈；不改相机、Photo Picker、上传或图片上限逻辑。

## 2026-05-25

- 按用户拍板固定 App 对外身份：App 名称继续“农技千查”，Android `applicationId` 从旧内测口径 `com.nongjiqianwen` 切到 `com.nongjiqiancha`；Kotlin 源码包 / Gradle namespace 暂保留 `com.nongjiqianwen` 作为内部代码命名空间，避免无意义大搬家。同步更新检查更新下载文件名、相机相册导出文件名前缀和 baseline profile 目标包名；本机生成固定 release 签名，备案用 MD5 / SHA1 / SHA256 / RSA 公钥信息写到 `%USERPROFILE%\\.nongjiqiancha\\android-release-public-info.txt`，签名配置写到 `%USERPROFILE%\\.nongjiqiancha\\android-release-signing.properties`，私钥和密码不进仓库。release 任务增加签名配置和 https `UPLOAD_BASE_URL` fail-fast，避免正式包没接后端；旧内测包 `com.nongjiqianwen` 不能通过检查更新覆盖安装成新包，测试机需卸旧包重装，后续自更新只支持 `com.nongjiqiancha` 同包名升级。

- 按用户要求把设置页“服务协议”6 个页面重写为成品口径：用户协议、隐私政策、第三方信息共享清单、个人信息收集清单、应用权限、风险提示统一更新到 2026-05-25。新文案按当前真实功能说明网络、图片、会员额度、帮助与反馈、检查更新、第三方大模型和云服务，不再使用“买服务器前 / 后续再补”的半成品语气；同时不把未来肥料等农资交易写死为不开展，也不虚构当前已经接入支付、手机号登录、礼品卡兑换、农资商品交易、OSS 图片链、SLS 业务日志或 Redis。同步更新 [legal-privacy.md](D:/wuhao/docs/runbooks/legal-privacy.md)、[go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md)、[app-update.md](D:/wuhao/docs/runbooks/app-update.md) 和项目记忆。

- 前后端巡检后收紧 ECS 后端监听口径：`server-go` 默认监听地址从 `:PORT` 改为 `127.0.0.1:${PORT:-3000}`，只让 Nginx 暴露公网入口；若后续 SAE / 容器平台需要全网卡监听，必须显式设置 `LISTEN_ADDR` 或 `LISTEN_HOST`。同步补单测锁住默认回环监听、host+port 和完整地址覆盖逻辑，并清理 [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md)、[deploy-sae.md](D:/wuhao/docs/runbooks/deploy-sae.md)、[backend-boundaries.md](D:/wuhao/docs/backend-boundaries.md) 里的旧 DNS / RDS / SAE 口径，避免后续窗口把历史方案当主链。

- 新增 App 自动日志最小接收骨架：后端新增 `POST /api/app/logs` 和 `client_app_logs` 表，走现有用户鉴权，限制请求大小、事件名、消息和 attrs 长度，并同步打一条结构化服务日志；Android 在远端快照失败 / 解析失败、前台 SSE 中断、后台图片消息流失败、图片上传失败、帮助与反馈发送失败、检查更新失败 / 解析失败等关键失败点自动 fire-and-forget 上报。当前不做用户手动上传日志，不上传聊天正文、AI 回复全文、图片内容 / URL、手机号、token 或模型 Key；这只是后续后台监控面板 / SLS 接入前的轻量骨架。新增 [app-client-logs.md](D:/wuhao/docs/runbooks/app-client-logs.md) 固定事件、隐私边界和后续查询口径。

- 更新 debug-only UI 文案样式预览面板：标题栏右上角新增关闭按钮，仍保留点空白关闭，避免只能点外层空白退出；面板外层补 safe drawing，降低横屏 / 刘海屏 debug QA 时贴边风险；新增“适配与回退”分组，覆盖远端快照失败兜底、删除历史 hydrate 拦截、图片预览安全区和会员面板短屏安全区；会员中心分组补窄屏套餐 / 加油包挤压样例，方便小屏和大字体风险快速核对。该入口仍挂在 debug 包顶部标题点击，不进入 release 可见主界面。

- 按用户要求拉多路会诊复查 Android 小屏 / 横屏适配和“UI 回退”风险后，补一组最小护栏：会员中心底部面板按顶部 safe drawing 限制最大高度，套餐 / 加油包标题和价格行增加权重与省略，避免小屏或大字体挤出；输入框图片预览、聊天图片预览、帮助与反馈图片预览和全屏页码补横向 safe drawing，删除历史确认弹窗补纵向安全区。`ChatScreen.kt` 的远端历史 hydrate 在 `/api/session/snapshot` 失败时不再把普通本地 30 轮窗口整段回灌，只保留 pending 图片发送和失败态恢复入口；删除所有历史对话成功后用清除 epoch 拦截并发中的旧 hydrate 结果，避免旧本地快照晚回来制造“清了又回退”的观感。复查未发现运行时旧 active-zone、反向列表、手写图片手势链或 Android 直连模型链复活；本轮不触碰聊天滚动主链。

- 用阿里云 CLI / Cloud Assistant 复查 ECS 接通链路并补齐 DNS：确认 `nongji-server.service` 已运行，Nginx 本机带 `Host: api.nongjiqiancha.cn` 访问 `/healthz` 返回 `ok=true / auth_strict=true / bailian=missing_key / dev_order_endpoints=false`，`APP_ENV/ENV/GO_ENV=production`、`AUTH_STRICT=true`、开发期订单接口关闭，RDS 已支撑服务启动和迁移；通过阿里云 DNS 创建 A 记录 `api.nongjiqiancha.cn -> 39.106.1.151`，ECS 内解析域名和 HTTP healthz 已生效。本机 Windows 当前可能因代理 / fake DNS 显示 `198.18.x.x`，不能作为云端解析失败依据。当前仍未配置 DashScope 模型 Key，Nginx 也只监听 HTTP 80，服务器环境里的 `BASE_PUBLIC_URL / UPLOAD_BASE_URL` 虽已是 `https://api.nongjiqiancha.cn`，但 HTTPS 证书 / 备案未闭环前，图片上传返回的 https 图片 URL 和 Android 生产 HTTPS 链路还不能算正式可用。用户发来的阿里云 `ARMSStopped` 和安全评分截图属于监控 / 安全治理项，不代表 ECS 或 Go 后端已停止。

- 多代理全量复查 Android UI 后继续补边界护栏：输入框图片/字数提示层改到输入框壳体之后绘制并显式置顶，避免有图片时被白色输入卡遮住；纯文字发送失败会主动请求列表底部锚点，用户/助手失败 footer 也纳入消息底部测量，避免失败态露不全或底部工作线误判；今日农情卡片横向轨道改为复用聊天列表 / 输入框边界；图片全屏预览页码和关闭按钮改走状态栏安全区；检查更新弹窗补纵向安全区和长说明滚动；无后端地址时的本地假流式回答收口为 debug-only，正式包只走失败态，避免绕开后端真相。只改现有运行时 UI 组件，不引入旧 active-zone / 反向列表 / 手写图片手势链；按 baseline profile runbook 判断为边界视觉和测量修复，不需要更新预热脚本。

- 多代理复查清数据后 Android UI 回归：设置首页标题从外层浮层收进 `HamburgerMenuMainPage` 自己的内容区，删除运行时第二处“设置”标题源，避免标题和设置列表在 clean-state / 动画重组时分层错位或看起来丢失；主聊天页顶部左右按钮统一使用 48dp 真实触控尺寸参与遮罩高度和视觉边界计算，并给左上设置入口补语义标签。只改 `ChatScreen.kt` / `HamburgerMenuSheet.kt` 的 UI 归属和尺寸参数，不改聊天滚动、输入框、远端历史恢复、会员 / 反馈 / 检查更新业务链路；按 baseline profile runbook 判断属于小视觉修复，不需要更新预热脚本。

- 完成 ECS 首版后端部署准备并跑起 `server-go`：通过 Cloud Assistant 初始化 `nongji` 系统用户、部署目录、`/etc/nongjiqiancha/server.env`、`nongji-server.service`、Nginx 反向代理、基础限流、fail2ban 和 logrotate；RDS 创建库 `nongjiqiancha`、账号 `nongji_app` 并放通 ECS 私网 IP `192.168.1.237`，服务启动已完成当前 MySQL 迁移，`/healthz` 返回 `ok=true / auth_strict=true / bailian=missing_key / dev_order_endpoints=false`。当前未配置 DashScope 模型 Key，真实聊天会安全返回 `MODEL_BACKEND_NOT_CONFIGURED`，未授权请求返回 401；公网正式可用仍待 DNS、HTTPS、备案、模型 Key 和 Android 登录 token 链。部署时修复 `007_single_user_session_ab.sql`、`008_single_user_round_ledger.sql` 的 MySQL 不兼容 `DROP COLUMN IF EXISTS` 写法，改为仓库既有的 `information_schema + PREPARE` 动态 DDL 风格。OSS Bucket 创建仍被 OSS 返回 `UserDisable` 阻塞，SLS 服务已开通但只看到阿里云系统 / 产品托管项目，暂不删除也不购买节省计划。

## 2026-05-24

- 记录夜间云资源采购状态：ECS `i-2ze5nrem0jrchln4f0eh` 已购买并运行，规格 `ecs.u1-c1m2.large`（2 vCPU / 4 GiB），Ubuntu 22.04，公网 IP `39.106.1.151`，私网 IP `192.168.1.237`，VPC / 交换机与 RDS 同在北京可用区 L；RDS `rm-2zes3vmj76p85n8g1` 继续运行但白名单 / 数据库账号 / 库名尚未配置；OSS 标准-本地冗余存储包（华北2）100GB 已购买并生效，资源包实例 `OSSBAG-cn-mqq4sqfvr001`，但 Bucket 尚未创建，`server-go` 图片上传链也尚未接 OSS；域名 `nongjiqiancha.cn` 用户口头确认实名认证 / 模板审核已通过。同步更新根规则、当前状态、风险、待决策、ECS / SAE / OSS / infra runbook，明确今晚不再需要购买 ACR 企业版、Redis、CDN、SLS 或下行流量包，下一步是配置安全组、RDS 白名单 / 账号、创建 OSS Bucket、部署后端和推进备案 / HTTPS。

- 按用户确认删除 SAE demo 应用并收口部署路线口径：先通过阿里云 CLI 停止北京 SAE 应用 `nongjiqiancha` / AppId `366147d5-3760-4548-bd68-f38debbc5f23`，随后删除该应用，删除变更单 `14a360d3-e2b4-4b93-9701-b76dfcc7bfd9` 提交成功，验证 `ListApplications` 返回空列表、`TotalSize=0`。该 SAE 应用此前只是默认 demo 镜像，未部署 `server-go`，不承载对外流量。首版后端部署路线从“SAE 镜像托管优先”转向“优先评估 / 准备 ECS 传统部署”；ACR 企业版暂不作为当前必买项，ECS 尚未购买，后续真实部署入口仍待固化。同步更新根规则、当前状态、待决策、风险和 infra / SAE runbook，避免 SAE 与 ECS 两套方案并列误判为同时生效。

- 阿里云北京区 RDS MySQL 已落地到首版最小规格：在 VPC `vpc-2zeax2zowza2398b9dzot` 下创建北京可用区 L 交换机 `nongjiqiancha-rds-beijing-l` / `vsw-2zemsq82lj2kp8za90aky` / `192.168.1.0/24`，并创建 RDS MySQL 实例 `rm-2zes3vmj76p85n8g1`，MySQL 8.0、基础版、1 核 2GB、50GB、内网地址 `rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`、到期时间 2027-05-24，当前自动备份保留 7 天。当前白名单仍是默认 `127.0.0.1`，数据库账号 / 库名 / 后端环境变量、默认备份策略是否调整和真实后端部署入口仍待配置。同步更新根规则、当前状态、待决策、风险和 infra / SAE runbook。

- 首版云资源进入真实落地阶段：阿里云 `华北2（北京）/ cn-beijing` 曾创建标准版 SAE 应用 `nongjiqiancha`，AppId `366147d5-3760-4548-bd68-f38debbc5f23`，规格 `0.5 核 / 1GB / 单实例`，自动弹性未开启；当时仍是 SAE 默认 demo 镜像，尚未部署 `server-go`，该 demo 应用后续已删除，见本日最新条目。域名 `nongjiqiancha.cn` 已购买，仍需实名认证 / 模板审核、DNS 解析、ICP / App 备案、HTTPS 证书和正式入口绑定。用户本机已安装阿里云 CLI，默认 Region 为 `cn-beijing`，可通过 OpenAPI 读取云资源；真实 AccessKey 只保存在本机 CLI 配置中，不写入仓库、聊天记忆或项目文档，后续稳定后建议轮换已暴露过的主账号 Key。同步更新根规则、当前状态、待决策、风险、infra readiness 和 SAE 发版 runbook。

- 会员中心弱网动态小修：`ChatScreen.kt` 给 `/api/me` 会员信息刷新增加本地 request epoch，底部会员中心打开、设置页会员中心刷新或订购成功刷新发生重叠时，只允许最新一次请求写回 `membershipEntitlement / membershipLoadState`，避免旧慢请求晚回来覆盖新状态导致“刚同步成功又变未同步 / 旧额度”的 UI 闪动。只改会员 UI 状态收口，不改 `/api/me`、会员权益、扣次顺序、支付占位或后端真相；按 baseline profile 规则判断不需要更新预热脚本。

- 按用户确认重写 B/C 记忆提示词：B 层从偏农业病例承接调整为“全场景通用短期记忆”，明确目的是真实保留最近对话主线，让下一轮能自然接得住；C 层调整为“全场景长期通用记忆”，当时已开始覆盖长期背景、用户画像、稳定偏好和历史经验，并明确不做通用知识库或病例流水账。同步更新根规则、项目状态、待决策、风险和上线巡检文档；后端字段、触发频率、模型参数、A/B/C 注入链路均不变。

- 按用户确认继续精简主对话锚点 E(2)：删除“不要每轮机械套完整模板”，保留“以自然分段为主，禁止表格，关键点少量加粗”。B 层和 C 层提示词不变。

- 按用户确认做两处轻量收口：`ChatScreen.kt` 主聊天消息列表横向边距改为和底部输入框卡片一致，小屏 / 手机宽度从 `10/14dp` 调到 `12/16dp`，让正文、顶部图标视觉边界和输入框边界统一；主对话锚点删除“用户问题描述不清时...”这一条，让模型按自身能力判断真实诉求，D 组重新编号。只改样式参数和锚点文本，不改聊天滚动链、输入框 reserve、B/C 提示词或模型参数；按 baseline profile 规则判断不需要更新预热脚本。

- 按用户确认继续收口主对话锚点 C(2)：会员和 APP 使用问题统一成一条边界，不再让模型自行理解“通用产品规则 / 已知入口”，而是明确只回答本 APP 已知固定入口和公开规则：会员中心、帮助与反馈、检查更新、Plus/Pro 每日问答次数和加油包；涉及个人账户、订单、扣费、权益异常、持续故障或个人数据时仍不编造账户信息。礼品卡和服务协议继续不写入锚点，B 层和 C 层提示词不变。

- 按用户确认整理主对话锚点分组：把“用户问题描述不清时，先判断真实诉求；能给临时安全建议的，先简短给建议，再追问关键缺口”从 C. 用户交互边界与推进移到 D. 问题处理，避免交互边界和问题处理职责混在一起；句子原意不变，B 层和 C 层提示词不变。

- 按用户确认微调主对话锚点 F(1)：商业中立改为“不推广、不导购、不背书；不诋毁任何品牌、厂家、商家、商品或服务”，并明确可按用户要求做相关信息整理、标签解读、技术分析或中立比较，覆盖用户询问厂家、商家、商品、服务等场景。B 层和 C 层提示词不变。

- 按用户确认微调主对话锚点：D(2) 改为“涉及混配、浓度、倍数、亩用量、兑水量、面积换算等问题时，应先核对关键参数，再给出计算或使用建议”，删除“缺参数时先追问，不直接给确定剂量”，避免在用量换算类问题里把模型压得过紧。B 层和 C 层提示词不变。

- 多代理继续复查动态交互和弱网链路后，补三处小护栏：帮助与反馈进入页后，已读接口改为携带本次已拉取到的最后一条后台 / 系统消息 ID，后端只把 `id <= last_seen_message_id` 的消息标为已读，避免加载与已读之间新到的回复被误清红点；主对话 `/api/chat/stream` 改为在读取 A/B/C 快照和组装 prompt 前先拿同用户活跃流锁，`/api/session/clear` 能稳定识别刚开始的流并返回 409，避免删除历史后旧快照再写回；纯文字失败消息恢复网络后手动重输同文发送也会复用最后一条同文失败用户消息 ID，带远端图片 URL 的 assistant 重试也重新登记同一条 pending，让 App 被杀后仍有 WorkManager 兜底。同步更新动态回归 runbook。

- 代理分三路复查弱网、切后台 / 杀进程恢复和消息尾端动态逻辑后，按最小改动补两处真实边角：纯文字无网发送现在会在生成失败用户气泡后同步清空输入框和草稿，避免用户恢复网络后又点一次把同一文本变成第二个新 `client_msg_id`；文字和图片发送起点的本地聊天窗口改为同步落盘，降低刚点发送就杀 App 时尾部用户消息 / assistant placeholder 来不及进本地窗口的概率。带图发送的 WorkManager 后台兜底也从“部分可恢复错误直接失败”改为对图片上传失败、网络中断、流异常结束、`409`、临时上游错误等可恢复失败做同一 `client_msg_id` 的指数退避重试，普通可恢复失败最多重试 5 次后移除 pending，兼顾弱网恢复和模型成本上限。消息尾端仍保持单消息主人、免责声明 settled 后显示、失败 footer 和 debug-only 预览主链不变。

## 2026-05-23

- `ChatScreen.kt` 修正主聊天页顶部按钮和正文“看起来不齐”的根因：此前正文对齐的是 44dp 顶部按钮触控框外边界，但图标笔画在按钮内部还有约 7-8dp 内缩，视觉上会显得顶部两个图标被夹在正文里面。本轮保留正文左右边界不变，让顶部按钮触控框再向外放，按图标实际笔画与正文边界对齐；底部输入框横向留白保持原样，避免连带改变 composer 视觉。

- `HamburgerMenuSheet.kt` 对齐设置页返回按钮和主聊天页汉堡按钮的顶部高度：设置页返回按钮保持 48dp 点击尺寸、左侧位置、标题和卡片布局不变，只把相对状态栏的顶部偏移统一到 4dp，并让 debug-only“设置外层”预览复用同一高度常量，避免正式页和预览页再次分叉。

- 复巡“清数据 / 清缓存后 UI 状态回退”与 debug-only 预览面板：确认当前 Android 已关闭 Auto Backup / Data Extraction，本地 `app_ids`、`chat_ui_cache`、`pending_chat_sends`、`files/composer_images` 不会被系统云备份 / 设备迁移回灌；清 App 数据后无稳定账号应进入 clean-state，清缓存只影响 `cacheDir` 临时文件。`ChatScreen.kt` 同步把 App 内“删除所有历史对话”成功后的本地 `render_window / stream_draft / composer_draft` 清理改成先同步 `commit()` 收口，再异步删私有图片，避免 UI 已清空但进程立刻被杀时旧本地快照在远端 snapshot 失败后短暂回灌。debug-only 预览面板新增“清数据回归”分组，并补“设置外层”预览，能看到返回键、标题和设置首页整体位置。

- `HamburgerMenuSheet.kt` 只微调设置页左上返回按钮位置：在保持 48dp 点击尺寸、标题位置、首页卡片大小、行高和业务入口不变的前提下，把返回按钮相对状态栏下移 4dp，降低单手点左上角时“顶得太高”的感觉。卡片密度本轮不再继续压缩，避免破坏当前已经确认合适的设置首页视觉。

- `ChatScreen.kt` 轻调主聊天消息区横向边距：手机宽度下消息列表左右对称收窄，`<360dp` 从 14dp 调到 18dp，`360~600dp` 从 18dp 调到 24dp，让正文左边界更接近顶部左右图标形成的视觉轨道。只改列表内容区 padding，不改顶部图标、输入框、composer reserve、96dp 工作线、AutoFollow、发送起步或 streaming / finalize 主链；按 baseline profile runbook 判断属于小视觉参数，不需要更新 baselineprofile 脚本。

## 2026-05-22

- 巡检“支付真实接入 / 会员订单”链路：确认 Android 会员开通、升级和加油包按钮仍只提示“支付暂未接入”，不会调用后端订单接口；后端 `/api/tier/renew_plus`、`/api/tier/renew_pro`、`/api/tier/upgrade_plus_to_pro`、`/api/topup/buy` 仍只是本地 / 内测开发期直改接口，默认返回 `PAYMENT_NOT_CONFIGURED`，且生产环境会强制关闭。当前没有真实支付渠道、SDK、自动续费、退款或对账，`orders` 表也不是正式支付订单表。本轮新增 [payments.md](D:/wuhao/docs/runbooks/payments.md)，固定后续真实接入必须走服务端创建订单、支付渠道异步通知、验签 / 解密、金额 / 商品 / 用户校验、幂等发权益和对账，不能打开开发接口当正式收费。

- 巡检“统一管理后台 / 内部运营入口”：确认当前没有网页后台、`/admin`、`/internal/admin` 或 Android 后台入口；真实内部入口只有帮助与反馈读取 / 回复、今日农情内部生成、检查更新环境变量配置，以及仍关闭的会员开发期接口。`SUPPORT_ADMIN_SECRET` 和 `DAILY_AGRI_JOB_SECRET` 只是共享密钥，不是后台账号、权限或审计体系；礼品卡成功卡片只是 debug-only 样式预览。本轮新增 [management-backend.md](D:/wuhao/docs/runbooks/management-backend.md)，并把买服务器后最小后台范围固定为：后台账号 / 角色 / 审计、帮助与反馈、用户详情、检查更新发布、今日农情状态、审计日志；支付和礼品卡接入后再补订单、对账、礼品卡生成 / 发放 / 作废。

- 巡检“账号 / 手机号登录与生产鉴权”链路：确认当前真实身份仍是 Android 本机 `IdManager` 生成的 UUID，经 `X-User-Id` 传给后端；后端支持 `APP_SECRET + Authorization: Bearer <签名token>`，`AUTH_STRICT=true` 时会关闭裸 `X-User-Id`，但 Android 还没有手机号登录、短信验证码、动态 token 签发、退出或注销账号主链。设置页账号管理里的手机号 / 退出设备 / 注销账号仍是占位，只有“删除所有历史对话”是真实动作。本轮不改业务代码，只把 `SESSION_API_TOKEN` 不能作为正式 release 共享静态 token、公开生产必须补 per-user token、本机 `user_id` 到账号迁移、账号注销 / 查询 / 删除入口和最小后台查询项写入 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)、[deploy-sae.md](D:/wuhao/docs/runbooks/deploy-sae.md) 和风险记忆。

- 历史归档：当时巡检“今日农情”时记录的是早期 `qwen3.5-plus + search_strategy=max + 严格 3 条 + https` 方案。该条只保留为历史过程，已被当前 `qwen3.5-plus + OpenAI兼容 chat/completions + turbo + forced_search=true`、正好 3 条、来源只做内部追溯、用户侧只展示标题摘要的方案替代；用户侧仍只读 ready 缓存，不在用户打开 App 时临时触发模型。

- 历史归档：曾巡检过旧分层记忆与模型调用链路。当前已被“一份记忆文档”替代，Android 没有摘要模型直连，旧完成接口仍不是主链；后续已补摘要 Redis TTL 租约，SLS 观察项和只读查询项继续按运维节奏完善。

- 巡检“主聊天与图片发送”主链：确认 Android 端仍只通过后端 `/api/chat/stream` 发起文字 / 图片 / 图文混合问诊，图片先进入 App 私有 `composer_images` 稳定副本并经 `/upload` 换成同一公开基地址下的 `https /uploads/*.jpg`，后端再次校验图片 URL 后才进模型；带图发送的唯一 WorkManager 兜底、后端 `chat_stream_inflight` 同用户活跃流约束、归档成功后才发 `[DONE]` 和扣次、`/api/session/snapshot` 历史恢复、`/api/session/clear` 删除历史 409 防活跃流都和当前口径一致。没有发现旧 Android 直连模型、旧 `/api/session/round_complete` 主链、旧 active-zone、旧图片手势或旧上传通道并存；本轮只把 `ImageUploader.kt` 里“上传 OSS”的过期注释改为当前真实“上传后端 /upload，未来 OSS 只能由后端接入”，并把买服务器后必须验证公网 https 图片链、单实例 / OSS、弱网多图、后台恢复和 SLS 指标写入 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。

- 巡检“服务协议 / 隐私政策 / 风险提示”组：确认当时设置页只有一个“服务协议”目录入口，下面 6 个本地内置二级页面（用户协议、隐私政策、第三方信息共享清单、个人信息收集清单、应用权限、风险提示）都走设置页右进左出页面栈；没有发现旧 WebView、外部协议网页、旧平铺三行入口或用户可见具体模型平台名残留。当时 Manifest 主链只声明 `INTERNET / ACCESS_NETWORK_STATE / REQUEST_INSTALL_PACKAGES`，正文口径和当时不申请定位、App 相机、相册 / 存储读写、录音、通讯录、短信、通知权限一致；同时补充 Android Q+ 拍照成功后会把原始照片另存到系统相册 `Pictures/农技千查`，避免只写 App 私有目录导致保存位置说明不完整。新增 [legal-privacy.md](D:/wuhao/docs/runbooks/legal-privacy.md)，并把买服务器后必须补的真实云服务商、第三方服务、数据保存期限、账号注销 / 查询 / 删除入口、备案号和隐私政策 URL 写入巡检记录；当前权限真相以 [legal-privacy.md](D:/wuhao/docs/runbooks/legal-privacy.md) 为准。

- 巡检“检查更新 / 自有 APK 分发”链路：确认当前没有应用商店跳转、浏览器下载或旧占位方案并存，主链是 Android 设置页请求 `GET /api/app/update`，后端由 `APP_ANDROID_*` 环境变量返回 https APK，Android 下载到 cache 后通过 FileProvider 调起系统安装页。后端新增可选 `APP_ANDROID_APK_SHA256` 并透出 `apk_sha256`；Android 下载后新增最终 https、文件大小、SHA-256、包名和 `versionCode` 校验，避免错包、坏包、半截包或低版本包进入系统安装页。同步更新 [app-update.md](D:/wuhao/docs/runbooks/app-update.md)、[pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md) 和风险记忆，明确 APK 建议放 OSS / CDN / 静态 HTTPS，发布时记录文件大小、SHA-256、签名指纹，回滚只能停更或发更高 `versionCode` 修复包。

- 巡检礼品卡占位链路：确认当前没有后端兑换接口、兑换码旧链路或会误改会员权益的并存方案，Android 礼品卡页输入后只提示“礼品卡功能后续接入”，不调用后端、不发权益、不假弹真实成功；debug-only “兑换成功 / 确定”仅是未来成功态样式预览。新增 [gift-card.md](D:/wuhao/docs/runbooks/gift-card.md)，并在 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md) 和风险记忆里固定后续真实接入原则：后端是唯一真相，真实接入前要先补礼品卡主表、兑换记录、幂等兑换接口、后台生成 / 发放 / 作废 / 查询、审计、限流和日志。

- 继续按“一个功能一个功能”巡检帮助与反馈：确认当前没有新旧方案并存，用户侧主链是 `/api/support/summary`、`/api/support/messages`、`/api/support/read`，后台回复先走 `SUPPORT_ADMIN_SECRET` 保护的 `/internal/support/*`，Android 红点进入页后会标记已读，附件面板返回优先级和 IME padding 已按现有主链收口。新增 `server-go/internal/app/support_test.go` 覆盖 payload 校验、图片 URL JSON 序列化和内部后台 secret 校验；同步把公开生产前必须补账号 token / `AUTH_STRICT=true`、多实例前必须上 OSS 或单实例、后续管理后台要补账号权限 / 审计 / 未处理列表、账号注销要明确帮助与反馈消息和图片保存删除规则，写入 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)、[support-feedback.md](D:/wuhao/docs/runbooks/support-feedback.md) 和项目风险记忆。

- 买服务器前功能巡检开始沉淀到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)：首轮收口会员中心 / 额度体系和 Go 后端高并发 / 性能边界。会员/额度当前确认没有新旧方案并存，Android 只做“支付暂未接入”占位展示，后端仍是 `/api/me`、`quota_ledger`、`topup_packs`、`upgrade_credits` 等真相；真实收费前必须补账号 token、生产 `AUTH_STRICT=true`、正式支付回调验签和对账。后端高并发结论是 Go 语言本身不是当前瓶颈，首版单实例可跑早期；多 SAE 实例前必须先处理本机图片存储迁 OSS、迁移抢跑、本进程限流 / B-C running guard 等边界。`server-go/internal/app/mysql.go` 同步把数据库连接池从固定 10 个连接改为可用 `MYSQL_MAX_OPEN_CONNS / MYSQL_MAX_IDLE_CONNS / MYSQL_CONN_MAX_IDLE_SECONDS / MYSQL_CONN_MAX_LIFETIME_SECONDS` 配置，默认值保持原样，并新增单测锁住默认值、环境变量覆盖和 idle 不超过 open 的回退逻辑。

- 新增 [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md)，把下一阶段上线推进顺序固化为：先定 App 名称 / 图标 / 包名 / 签名 / 协议 / 软著材料，买域名和中国内地云资源后立即启动 ICP / App 备案；手机号登录、SAE、RDS、OSS、SLS、帮助与反馈、检查更新、模型 Key 池和真机联调在备案等待期间并行推进；备案通过后补备案号、正式域名 / HTTPS、公安联网备案和应用商店物料。同步更新 runbook 入口、当前状态、待决策和风险记忆，避免后续把“做完手机登录再备案”误当成主顺序。

- 历史归档：曾给旧摘要提取补 60 秒超时保护。当前记忆文档摘要继续保留 60 秒超时，失败只保持 `pending_retry_b`，后续轮次完成后继续补提取；主对话 SSE 和今日农情链路不跟随改全局 `http.Client.Timeout`。

- `server-go/internal/app/bailian.go` 将百炼模型 Key 池从单纯 `DASHSCOPE_API_KEYS` 逗号轮询扩展为 `DASHSCOPE_API_KEY_1/2/3` 三个独立账号槽位，并继续兼容旧 `DASHSCOPE_API_KEY` 和 `DASHSCOPE_API_KEYS`；后端会自动去重、轮询，主对话、B/C 摘要和今日农情共用同一池。模型请求打开阶段若遇到 `401 / 403 / 429` 或带限流 / quota 语义的 `400`，会在流开始前切下一把 Key，并对触发限流的 Key 做 60 秒冷却；SSE 一旦成功打开，不在同一条回复中途切 Key。新增单测覆盖专用槽位去重、429 切 Key 和冷却跳过；新增 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md) 记录同一阿里云主账号多 Key 共享限流、扩容必须用不同主账号 Key 的运维口径。

- 历史归档：曾把旧长期层输入改为归档轮次。该分层方案已删除，当前归档只用于 UI 历史恢复、后台排障和后续可能的离线分析，不再作为独立长期层提取输入。

- `server-go/assets/b_extraction_prompt.txt` 将 B 层从偏“累计背景摘要”收口为“通用短期记忆”：优先保留当前主线、已确认事实、用户纠正、新增信息、仍有效判断倾向、待确认关键点和后续必须承接的限制条件；农业问题保留作物 / 地块 / 症状 / 用药用肥 / 图片证据等短期判断线索，非农业问题保留当前事务目标和已确认事实，但不编造账户、订单、扣费、剩余次数或后台处理结果。后端当时同步调整注入标签；当前标签已进一步收口为“B层通用短期记忆（仅供参考）”。数据库字段、B 层触发频率、C 层摘要提示词、A 层滑窗、模型名称和 `temperature=0.8` 均不变。

## 2026-05-20

- `server-go/assets/system_anchor.txt` 继续按用户确认小幅调整 C/F 边界：会员问题可回答通用产品规则，涉及个人档位、剩余次数、扣减记录、订单、支付或权益异常时不编造账户信息；App 使用问题可按已知入口给简短通用说明，持续故障 / 个人数据再引导“帮助与反馈”；商业中立允许按成分、用途、适用场景、风险点和标签信息做技术性比较，但不做品牌背书或导购。礼品卡不写入锚点，农资安全新增硬条款本次不加，B/C 摘要提示词不动。

- 随机巡检后补两处低风险收口：`HamburgerMenuSheet.kt` 的帮助与反馈页在用户选图后未发送即离开时，会清理仍挂在草稿里的私有 `composer_images` 图片，避免无引用临时图长期积累；`bailian_test.go` 扩展主对话和今日农情模型请求断言，锁住关闭思考、搜索开关、搜索策略、强制搜索和来源字段，避免后续参数口径误回流。业务链路、B/C 摘要提示词和模型温度不变。

- `server-go/assets/system_anchor.txt` 按用户最终确认版本收口主对话锚点：保留 A-F 六段结构和“信息不足不能下定论 / 图片先客观详细描述 / 单次输出≤1000字”等核心硬约束；删除“用户话语处理”、图片问诊初步线索、内部思考 / 调取历史重复说明，以及图片上下文、农资标签、品牌商品等用户点名删除条款；补强历史摘要不是定论、同一作物 / 同一地块才承接、文件上传引导、问题不清先给临时安全建议、混配 / 浓度 / 亩用量先核关键参数、不要机械套模板和安全提醒不冗长。B/C 摘要提示词本次不动。

- 全仓库产品名称口径统一为“农技千查”：主规则、README、主对话锚点、今日农情系统提示、App 内协议 / 隐私 / 风险文案和运维 / 项目记忆文档同步替换产品名；服务提供者公司主体仍按用户提供信息保留为“北京农技千问科技有限公司”，不跟随产品名机械改动。

- `server-go/internal/app/bailian.go` / `summary.go` 统一真实模型调用温度：新增后端统一常量 `unifiedModelTemperature=0.8`，主对话、摘要和今日农情生成全部显式使用该温度；`top_p / max_tokens / penalty` 等其他采样参数继续不显式设置，走模型服务默认值。补单测锁住主对话、摘要和今日农情请求里的温度字段；同步更新根规则和当前状态记忆。

- `HamburgerMenuSheet.kt` / `ChatScreen.kt` 同步删除历史确认文案：确认卡片标题从“删除所有历史对话？”改为“是否删除所有历史对话”，debug-only 预览面板对应入口也改为同一句。只改用户可见文案和预览入口，不改删除接口、清理范围、确认按钮、取消按钮、账号数据或聊天滚动链。

- `server-go` / `SessionApi.kt` / `HamburgerMenuSheet.kt` / `ChatScreen.kt` 接入“删除所有历史对话”真链路：账号管理页点击后先弹取消 / 确定确认卡片；确定后调用 `POST /api/session/clear`，后端删除当前用户 `session_ab`（A 层滑窗、B 摘要、C 长期记忆）和 `session_round_archive` 归档，前端成功后清当前聊天 UI、本地聊天快照、草稿、streaming draft、待发送 WorkManager 任务和本地私有 composer 图片。该操作不删除会员、额度、加油包、礼品卡、帮助与反馈、`quota_ledger` 或本机 `user_id`；若当前有活跃生成流，后端返回 `409 ACTIVE_CHAT_STREAM`，前端提示稍后再删。debug-only 预览面板新增删除历史确认卡片。

- `HamburgerMenuSheet.kt` 继续把设置首页卡片本体放大：主菜单内容左右外边距从 18dp 收窄到 14dp，让白色卡片更接近屏幕两侧，和上一轮放大的文字、图标、行高匹配；二级页仍保留原阅读边距，避免协议 / 账号等页面过满。只调设置首页卡片宽度，不改业务入口或返回逻辑。

- `HamburgerMenuSheet.kt` 把设置首页从上一版“偏小偏瘦”回放半档：白底浅边框、内缩分割线和右侧箭头保留不变，行高从 50dp 提到 52dp，主标题 / 副标题 / 图标 / 红点 / 箭头 / 左上返回键同步略放大，卡片间距和圆角也轻微回补。只调设置首页视觉重量，不回退到旧灰底大卡片，也不改任何业务链路。

- `HamburgerMenuSheet.kt` 重新收口设置首页排版：菜单首页补居中“设置”标题，左上角返回按钮缩小一档；主设置卡片从偏重灰底改为白底浅边框，分割线改成图标后内缩线，行高、标题、副标题、图标和红点继续下调一档，并给可进入 / 可操作的主菜单项补轻量右箭头，减少左侧堆叠和灰块压迫感。只改设置首页视觉和 debug-only 预览共用组件，不改会员中心、帮助与反馈、检查更新、礼品卡、服务协议正文、二级页返回链或聊天滚动链。

## 2026-05-17

- `HamburgerMenuSheet.kt` 修正服务协议目录和协议详情页之间的返回动画方向：从“服务协议”目录进入用户协议、隐私政策、第三方清单、个人信息清单、应用权限和风险提示时仍按右进左出；从任一详情页返回目录时改为左进右出，不再被通用“目标不是设置首页”规则误判成继续往里进。只改协议二级页动画方向，不改协议正文、设置菜单结构或其他二级页返回链。

- `HamburgerMenuSheet.kt` 轻收设置页视觉密度：设置首页入口行高、左右留白、图标槽、标题字号、副标题字号、红点、分割线和卡片圆角统一下调一档；账号管理和服务协议目录等二级列表行也同步收小，减少“大块头 / 憨厚感”。设置里的会员中心入口跟随主菜单密度变化，但会员中心二级页内容本体不动。只改设置页视觉密度，不改会员权益、帮助与反馈接口、检查更新、礼品卡、协议正文或聊天滚动链。

- `HamburgerMenuSheet.kt` 再次收口设置页结构：删除独立“数据管理”入口、页面、图标分支和 debug-only 预览项；“删除所有历史对话”移到“账号管理”子页，仍只提示“历史对话删除后续接入”，不做假删除；主设置页“服务协议”并入“帮助与反馈 / 检查更新 / 礼品卡”同一卡片组，不再单独占一个卡片。同步更新当前状态和风险记忆。

- `HamburgerMenuSheet.kt` 收紧“数据管理”页面：页面只保留“优化体验”和“删除所有历史对话”两项，移除“我的数据说明 / 申请查询个人信息 / 申请删除个人信息”三项。优化体验继续显示“未开启”，但口径明确为后续产品体验改进类可选用途，不影响问诊历史、上下文记忆或帮助与反馈客服记录；删除所有历史对话仍是后续接入提示，不在没有后端真删除接口时假装清库或只清本地 UI。debug-only 预览面板标题说明同步更新。

- `HamburgerMenuSheet.kt` 按 DeepSeek 参考结构重整设置页的协议与数据入口：主设置页新增“数据管理”行，原来平铺的“服务协议 / 隐私政策 / 风险提示”收敛为一个“服务协议”入口；点入后是目录页，包含“用户协议 / 隐私政策 / 第三方信息共享清单 / 个人信息收集清单 / 应用权限 / 风险提示”。法律详情页返回时先回目录页，再回设置页，避免多级页面直接跳出。数据管理页当前只做真实能力边界内的轻量说明和申请入口提示：“数据用于优化体验”显示未开启，个人信息查询 / 删除提示通过帮助与反馈或邮箱申请，“删除所有历史对话”仍是后续接入提示，不假装已经有后台删除 / 导出能力。debug-only 文案预览面板同步新增这些页面预览。只改设置页 UI、文案目录和项目记忆，不改真实后端删除接口、账号注销、第三方 SDK、权限声明、会员 / 礼品卡或聊天滚动链。

- `HamburgerMenuSheet.kt` 复核并精简“服务协议 / 隐私政策 / 风险提示”：三份用户可见正文统一把面向用户的“你”改为“您”，debug-only 帮助与反馈示例客服回复也同步改尊称；服务协议从 19 节压成 10 节，合并 AI 边界 / 标识 / 责任限制、内容与图片、会员与礼品卡、反馈与检查更新等重复条款；隐私政策去掉 `B/C`、具体模型品牌和内部云资源细节，改为“第三方大模型和云服务 / 第三方和系统能力清单”的用户可读口径；风险提示从 9 条压成 6 条。App 正文不再暴露通义千问 / DashScope / Qwen 等具体模型或平台名，正式上线前再按真实接入情况补第三方服务清单。只改本地法务/风险文案和项目记忆，不改后端模型、权限、接口、会员、礼品卡或聊天滚动链。

- `HamburgerMenuSheet.kt` 接入本地内置“服务协议 / 隐私政策 / 风险提示”三份二级页：设置页点对应入口不再提示占位，而是右进左出打开正文；协议主体按“北京农技千问科技有限公司”和 `nongjiqiancha@foxmail.com` 填写。当时隐私政策按当时真实权限写明只声明网络、网络状态、安装更新 APK 相关权限；照片走系统 Photo Picker，拍照走外部相机 + FileProvider 临时 URI，当时不申请定位、相机、相册 / 存储读写、录音、通讯录、短信或通知权限，也不做 App 外通知 / 推送；同时补充模型处理、本地缓存、第三方 / 系统能力、用户权利和未来可能权限边界。服务协议补 AI 生成内容标识、礼品卡规则、禁止行为、知识产权和责任限制。风险提示压成短而明确的 9 条，覆盖误判、图片局限、农资标签、官方事项、时效信息、生产损失、未成年人、紧急情况和非农业用途。debug-only 预览面板同步新增三份页面预览。只改协议/隐私/风险入口和本地文案，不改会员、礼品卡、后端接口或聊天滚动链；当前权限真相以 [legal-privacy.md](D:/wuhao/docs/runbooks/legal-privacy.md) 为准。

- `HamburgerMenuSheet.kt` 将“检查更新 / 发现新版本”卡片的更新说明统一为“优化产品体验”：真实弹窗在后端未返回 `release_notes` 时使用该兜底文案，debug-only 预览面板也同步展示同一句；`docs/runbooks/app-update.md` 同步推荐 `APP_ANDROID_RELEASE_NOTES` 使用同一文案。只改检查更新卡片文案，不改下载、安装、后端版本判断或回滚链路。

- 新增 `docs/runbooks/operations-blueprint.md`，把后期 Codex 协助整体 App、Go 后端、RDS / 日志 / 成本、版本发布 / 回滚、帮助与反馈、礼品卡、会员和统一管理后台的运维范围先存进仓库；`AGENTS.md`、`infra-readiness.md`、`runbooks/README.md`、`current-status.md`、`open-risks.md` 和 `pending-decisions.md` 同步改口径。当前仍不伪造未购买的服务器实例、域名、数据库地址或密钥，真实环境落地后再把可执行入口回填到 deploy / rollback / logs / db / app-update 等 runbook。

- `server-go` / `SessionApi.kt` / `HamburgerMenuSheet.kt` 接入自有服务器 APK 分发的“检查更新”：后端新增 `GET /api/app/update`，由环境变量 `APP_ANDROID_LATEST_VERSION_CODE / APP_ANDROID_LATEST_VERSION_NAME / APP_ANDROID_APK_URL / APP_ANDROID_RELEASE_NOTES / APP_ANDROID_FORCE_UPDATE / APP_ANDROID_FILE_SIZE_BYTES` 控制最新 Android 版本，且只接受 https APK 链接。Android 设置页“检查更新”不再是占位，点击后请求后端；无更新提示“已是最新版本”，有更新弹“发现新版本”卡片，按钮为“稍后 / 立即更新”，立即更新会下载 APK 到 App cache 并通过 FileProvider 调起系统安装确认。Android 8+ 如未允许本 App 安装未知应用，会先打开系统授权页。debug-only 预览面板同步补“检查更新 / 发现新版本”卡片预览；`docs/runbooks/app-update.md` 补了更直白的发布和回滚步骤。只接版本检查与下载安装壳，不接应用商店、不做静默安装、不改聊天滚动链或会员 / 礼品卡业务。

- `HamburgerMenuSheet.kt` 微调帮助与反馈和礼品卡：帮助与反馈页后台消息气泡标签从“我们”改为“客服”，空态文案同步改成“把问题发给客服，回复会显示在这里。”，并移除标题下“这里会保留你的反馈和回复。”说明句；礼品卡输入框继续不放占位提示，边框改为纯黑。只改用户可见文案和边框视觉，不改 `/api/support/*`、图片附件、红点已读、礼品卡后端占位或聊天滚动链。

## 2026-05-16

- `HamburgerMenuSheet.kt` / `ChatScreen.kt` 将设置页用户可见入口从“客服反馈”统一改名为“帮助与反馈”：主设置页入口、二级页标题、页面说明、空态文案、后台消息气泡标签和 debug-only 预览面板同步改名；后端接口、数据库表和 Android 内部 `Support*` 命名保持不变，避免重命名影响既有 `/api/support/*` 主链。同步更新锚点提示词和帮助与反馈 runbook / 项目记忆。

- 夜间代理巡检后收口两类非 UI 风险：`004/005/006/016` 迁移不再使用 MySQL 兼容性不稳的 `ADD COLUMN IF NOT EXISTS`，统一改成 `information_schema.COLUMNS` 检查后再动态执行 `ALTER TABLE`，并让新库的 `015_support_messages` 直接带 `image_urls_json` 字段；`AGENTS.md` 和 `docs/runbooks/android-edge-case-regression.md` 同步当前口径，debug-only 文案预览面板短期允许保留，但必须保持隐藏 / debug-only，release 不可见，阶段性不再需要时再删除。只改迁移兼容和文档口径，不改运行时接口、会员权益、聊天滚动链或帮助与反馈 UI。

- `HamburgerMenuSheet.kt` 继续收口客服反馈附件面板返回链：客服页把附件面板打开状态同步给汉堡浮层外层返回按钮，左上角圆形返回键现在和系统返回键同一优先级，附件面板可见时先收起“相机 / 照片”卡片，第二次才退回设置菜单。只改返回分发，不改客服消息接口、图片上传、红点已读、礼品卡或聊天滚动链。

- `HamburgerMenuSheet.kt` / `ChatScreen.kt` 补客服反馈返回键和预览面板：客服反馈附件面板可见时新增本页 BackHandler，系统返回键优先收起“相机 / 照片”卡片，不再直接退回设置菜单；客服反馈发送前会检查后端是否配置，发送接口失败统一提示“发送失败，请检查网络后重试”。debug-only 预览面板新增“礼品卡兑换成功”卡片、“客服反馈图片面板”和“客服反馈发送失败”三项，其中礼品卡成功卡片下方有“确定”，点击后关闭；真实礼品卡页仍等待后端成功分支，不假弹兑换成功。

- `ChatComposerPanel.kt` / `HamburgerMenuSheet.kt` 复查客服反馈和礼品卡：客服反馈继续复用主聊天附件面板结构，但在客服页显式关闭农业问诊拍摄提示，不再显示“建议拍清病斑、异常部位、叶背或果实”，只保留中性的最多 4 张限制；礼品卡子页把输入框和兑换按钮改成页面中部上下两行布局，兑换按钮字体加大。礼品卡仍未接后端兑换接口，因此不假弹“兑换成功”卡片，后续只能在真实后端成功分支接成功卡片。

- `server-go` / `SessionApi.kt` / `HamburgerMenuSheet.kt` 接入首版“客服反馈”站内消息：后端新增 `support_messages` 表，用户侧提供客服摘要、历史列表、发送消息和已读接口；内部后台侧先提供 `SUPPORT_ADMIN_SECRET` 保护的读取用户会话和发送客服回复接口，后续管理后台可直接复用。Android 设置页“客服反馈”从占位提示改为右进左出的 GPT 风格消息页，进入后联网拉取历史，发送反馈写入后端；后台客服 / 系统新消息会让设置页“客服反馈”行右侧出现小红点，进入客服页并成功同步后调用已读接口刷新红点。debug-only 预览面板同步新增客服反馈页面预览。顺手把主设置页“礼品卡”入口下方“领取会员权益”副标题删除，只保留“礼品卡”三个字。本次不做实时聊天、完整网页管理后台、工单流转或系统推送通知。

- `HamburgerMenuSheet.kt` / `SessionApi.kt` / `server-go` 给客服反馈补图片附件：客服页输入区复用主聊天同款底部输入框壳、加号、发送箭头、附件面板和图片预览条，可从相机拍照或照片选择添加图片；图片仍按主聊天规则导入 App 私有 JPEG 副本、最多 4 张、上传前不超过 1MB，并通过 `/upload` 取得后端 URL 后写入 `support_messages.image_urls_json`。客服消息气泡可展示图片缩略图并点开全屏预览；客服页外层补 `imePadding()`，键盘弹起时输入框不再被盖住，历史列表继续在中间区域滚动。只开放主聊天图片 helper / composer 预览组件给客服复用，不改主聊天发送事务、LazyColumn、AutoFollow、streaming 或 finalize 主链。

## 2026-05-14

- `MembershipCenterSheet.kt` 调整会员中心额外次数胶囊：升级补偿次数和加油包次数不再在“套餐”标题下方上下分两行，而是放在套餐标题下面的同一行横排展示；继续不放回标题右侧，避免抢标题空间。只改会员中心余额提示布局，不改后端权益、扣次顺序、支付占位或聊天滚动链。

- `ChatScreen.kt` 更新 debug-only UI 文案样式预览面板：预览列表从全量平铺改为“一级标题可展开 / 收起 + 二级条目查看”的折叠结构，默认只展开第一组，避免预览项越来越多时列表过长；同步补上设置内会员中心、账号管理、礼品卡三个二级页面预览，复用正式设置页组件。只改 debug 预览入口，不改 release 可见 UI、会员权益、礼品卡后端或聊天滚动链。

- 复查汉堡设置页所有二级页面的小屏显示：主设置页、账号管理、礼品卡和设置内会员中心均是全屏安全区 + 纵向滚动结构；顺手把会员中心“升级补偿次数 / 加油包”余额胶囊从套餐标题旁横排改为标题下方纵向展示，避免两类余额同时存在时在 320dp 窄屏横向挤出。只改会员中心余额提示布局，不改后端权益、扣次顺序、支付占位或聊天滚动链。

- `HamburgerMenuSheet.kt` 微调设置页会员中心和礼品卡视觉：设置内会员中心子页把短 ID 收进“会员中心”标题行，不再单独占一行高度；礼品卡输入区增加浅色边框，同时把礼品卡入口图标外扩到和其他设置图标一致的视觉占位。只改汉堡设置页视觉，不接礼品卡后端、不改会员权益、支付占位或聊天滚动链。

- `HamburgerMenuSheet.kt` 将设置页里的“会员中心”改成右进左出的设置子页：主界面右上角会员入口仍保持底部会员面板；设置页入口不再关闭汉堡页或弹底部面板，而是复用 `MembershipCenterSheet.kt` 的会员内容组件和 `/api/me` 数据在设置栈内展示，返回键先回到设置菜单。只改入口形态和会员内容复用，不新增后端接口、不修改会员权益、支付占位或聊天滚动链。

- `HamburgerMenuSheet.kt` 将设置页“兑换码”可见入口和子页改名为“礼品卡”：入口继续放在“检查更新”下方，图标保持线性礼物盒；礼品卡输入框不再展示占位提示或底部说明，不再由前端限制英文 / 数字 / 短横线、自动大写或 32 位长度，改为原样接收用户输入并等待后续后端规则裁决。当前仍只是 UI 壳，不新增后端兑换表、不调用兑换接口、不修改会员权益、聊天滚动链或主界面。

- 随机巡检会员中心和设置页后做低风险收口：`ChatScreen.kt` 现在从汉堡设置页打开会员中心时会先关闭汉堡页，避免会员中心遮罩背后残留设置页、关闭后又露回汉堡；`MembershipCenterSheet.kt` 的“支付暂未接入”提示会 1.5 秒后自动消失；`HamburgerMenuSheet.kt` 的兑换码输入提示与允许字符对齐，自动大写固定 `Locale.US`，空兑换码时按钮为真正禁用态，输入完成后支持键盘 Done 触发同一占位提示。会员中心规则口径、后端会员权益、聊天滚动链和图片链不变。

- `HamburgerMenuSheet.kt` 调整“兑换码”入口位置和图标：入口从会员中心旁边挪到“检查更新”下方，降低陌生用户刚进设置页时的付费压迫感；兑换码图标从票券加号改成更克制的线性礼物盒，继续使用 24dp 图标槽、同一线宽和黑白设置页风格。只改汉堡设置页视觉和入口顺序，不接兑换后端、不修改会员权益、聊天滚动链或主界面。

- `HamburgerMenuSheet.kt` 给汉堡设置页新增“兑换码”入口和右进左出的轻量子页：页面可输入英文 / 数字 / 短横线兑换码，自动转大写并限制 32 位，按钮当前只提示“兑换码功能后续接入”。这版只做 UI 壳和返回栈，暂不新增后端兑换码表、不调用兑换接口、不修改会员权益或本机身份；后续真实兑换会员需要单独设计兑换码生成、过期、一次性使用、防撞库和权益写入规则。

- `HamburgerMenuSheet.kt` 修正账号管理子页的 Android 系统返回键行为：现在左上角返回和手机底部 / 系统返回键都会先从账号管理退回汉堡菜单页，只有已经在菜单首页时才关闭汉堡页回到主界面。账号页自己的 `BackHandler` 只在账号子页启用，菜单首页、会员中心、附件面板等仍交给 `ChatScreen.kt` 的主返回分发处理，避免浮层叠加时系统返回被汉堡页抢走。只改返回分发，不改账号页三项占位、不调后端、不清本机身份或聊天记录。

- `HamburgerMenuSheet.kt` 给汉堡设置页接入“账号管理”首版轻量子页：点击账号管理后从右向左滑入，返回时回到菜单页，页面只放“手机号 / 退出设备 / 注销账号”三项。当前没有真实手机号登录、退出设备或注销账号后端，所以三项只给后续接入轻提示，不调用新接口，不清 `IdManager`，不影响聊天记录、会员中心、聊天滚动链、图片链或后端身份真源。

- `HamburgerMenuSheet.kt` 统一汉堡设置页图标视觉占位：按同一个 24dp 图标槽重新收拢各图标的视觉盒子，服务协议文档图标放大到约 64% 宽、72% 高，会员、账号、检查更新、风险提示、客服反馈等图标统一收在约 68% 到 72% 的视觉范围内，检查更新同步降线宽，减少有的大有的小的问题。只改设置页图标 Canvas 几何，不改菜单结构、文案、会员中心业务、聊天滚动链、输入框、图片链或后端接口。

- `server-go/internal/app/quota.go` / `MembershipCenterSheet.kt` 将加油包从 `6元/100次` 调整为 `6元/80次`：后端新购加油包 `topupPackRemaining` 改为 80，会员中心展示改为“额外80次 / ¥6 / 80次”，同一时刻 1 个 active 加油包、Plus / Pro 才可订购、永久有效、用完再续和消耗顺序都不变。现有数据库里已购买的 active 加油包不做迁移，仍按原记录剩余次数继续用。
- 同步沉淀当前成本核算口径：主对话是实时 SSE `qwen3.5-plus`，不吃 Batch Chat 半价；按输入约 0.8 元 / 百万 tokens、输出约 4.8 元 / 百万 tokens 估算。`0.018 ~ 0.019 元 / 轮` 不再称为最保守成本，改为偏重使用估算；项目记忆采用真实平均约 `0.008 ~ 0.015 元 / 轮`、偏重使用约 `0.018 ~ 0.022 元 / 轮`、极重连续图文搜索约 `0.025 ~ 0.030 元 / 轮` 三档。Plus 19.9 / 25 次每天、Pro 29.9 / 40 次每天暂不调整；后续应记录每轮实际 token 和搜索使用量再校准毛利。

## 2026-05-13

- `ChatScreen.kt` 继续放大主界面右上角会员中心图标内部加号：外框大小和线宽仍保持轻量状态，只把加号横竖长度从画布 26% 提到 30%，让图标内部符号更清楚。只改主界面会员入口 Canvas 几何，不改汉堡页、会员中心业务、聊天滚动链、输入框、图片链或后端接口。

- `ChatScreen.kt` 微调主界面右上角会员中心图标内部加号：外框大小和线宽保持上一版轻量状态，只把加号横竖长度从画布 22% 回升到 26%，避免外框收轻后内部加号显得偏小。只改主界面会员入口 Canvas 几何，不改汉堡页、会员中心业务、聊天滚动链、输入框、图片链或后端接口。

- `ChatScreen.kt` 微收主界面右上角会员中心“圆角方框 + 加号”图标：外框从画布 66% 收到 62%，线宽从 9% 压到 7.8%，加号线宽和长度同步收轻，降低顶部栏右侧入口的视觉重量；汉堡设置页里的会员图标保持原尺寸，避免菜单行里显小。只改主界面会员入口 Canvas 几何，不改会员中心业务、汉堡页、聊天滚动链、输入框、图片链或后端接口。

- 夜间代理会诊后做一轮高风险收口：`AUTH_STRICT=true` 现在会真正关闭裸 `X-User-Id` 兜底，只接受 `APP_SECRET` 签名 bearer token；图片上传 / 聊天图片 URL 校验不再用请求转发头推导公开基地址，必须配置 `BASE_PUBLIC_URL` 或 `UPLOAD_BASE_URL` 为公开 `https` 域名；开发期订单接口即使误设 `ALLOW_DEV_ORDER_ENDPOINTS=true`，在 `APP_ENV / ENV / GO_ENV = prod / production` 时仍强制关闭。Android 侧同步收紧 `onChromeMeasured` 的折叠高度采样条件，避免多行输入框高度污染聊天列表 reserve / 96dp 工作线；debug-only UI 预览面板文案改成“正式组件或调试近似样式”，汉堡页预览说明同步无标题设置页；附件提示统一为“单次最多4张图片”。本次不改聊天列表主人、图片压缩上传链、会员支付真实方案或今日农情生成链。

- `HamburgerMenuSheet.kt` 精简汉堡设置页顶部：删除居中的“农技千查”标题，只保留左上角返回按钮和下方设置入口组，并把内容区 top padding 调到 78dp，避免第一组卡片删标题后顶到返回按钮下方；同时把“服务协议”文档图标上下压短一点，减少文档图标显得过长的问题。只改汉堡设置页视觉布局和图标 Canvas，不改菜单结构、会员中心、点击占位、聊天滚动链、输入框、图片链或后端接口。

- `ChatComposerPanel.kt` 收小 `+` 附件面板里的“相机 / 照片”灰色入口块：底部面板最小高度从 292dp 收到 270dp，内容左右留白从 30dp 加到 38dp，两个入口间距从 12dp 加到 24dp，单块高度从 104dp 收到 90dp，图标从 34dp 收到 30dp，文字从 18sp 收到 17sp，让两个触摸块不再显得又大又挤。已按 baseline profile runbook 复查：本次不改输入框聚焦、IME、LazyColumn、发送起步或首屏预热路径，暂不需要更新 profile 脚本。只改附件面板视觉密度，不改相机 / Photo Picker 入口、图片最多 4 张规则、上传链、聊天滚动链或后端接口。

- `ChatScreen.kt` 单独收小聊天主界面右上角会员中心图标：顶部栏图标槽靠近标题，上一版 72% 外框显得偏大，因此主界面外框回收到 66%、加号回收到约 28%；汉堡设置页里的会员图标继续保留 72% 大占位，保证菜单行里不显小。只改主界面会员入口图标 Canvas 几何，不改汉堡页、会员中心业务、聊天滚动链、输入框、图片链或后端接口。

- `HamburgerMenuSheet.kt` / `ChatScreen.kt` 放大两处会员中心“圆角方框 + 加号”图标的整体占位：外框从画布 64% 提到 72%，加号横竖长度从约 30% 提到约 34%，解决汉堡设置页 24dp 图标槽内看起来偏小的问题；主界面右上角会员入口同步保持同款图标。只改图标 Canvas 几何，不改会员中心业务、菜单结构、聊天滚动链、输入框、图片链或后端接口。

- `HamburgerMenuSheet.kt` / `ChatScreen.kt` 继续对照 ChatGPT 订阅图标微调两处会员中心“圆角方框 + 加号”：加号保持居中，但横竖长度从 24% 放到约 30%，线宽从外框约 82% 回到约 92%，避免看起来偏小，同时不恢复到过粗状态。只改图标 Canvas 几何，不改会员中心业务、菜单结构、聊天滚动链、输入框、图片链或后端接口。

- `HamburgerMenuSheet.kt` / `ChatScreen.kt` 微调两处会员中心“圆角方框 + 加号”图标：把加号从偏下的 0.55 视觉位置移回正中心，横竖线同步缩短，线宽降到外框约 82%，避免小尺寸下显得偏粗、偏沉。只改图标 Canvas 几何，不改会员中心业务、菜单结构、聊天滚动链、输入框、图片链或后端接口。

- `HamburgerMenuSheet.kt` / `ChatScreen.kt` 将两处会员中心入口图标从“盾牌 + 加号”统一改为参考 ChatGPT 订阅入口的“圆角方框 + 加号”：汉堡设置页“会员中心”和聊天页右上角会员入口保持同一套黑色线性 Canvas 图标，语义更接近订阅 / 会员开通，也避免盾牌被误读成安全中心。只改图标绘制，不改会员中心业务、菜单结构、聊天滚动链、输入框、图片链或后端接口。

- `HamburgerMenuSheet.kt` / `ChatScreen.kt` 统一会员中心入口图标：汉堡设置页会员中心恢复为原先“盾牌 + 加号”图标；聊天页右上角会员中心入口不再展示 `ic_membership_leaf` 绿叶图片，改为同款黑色线性“盾牌 + 加号”Canvas 图标，避免 App 内继续露出未注册下来的叶子标识。已检查 baseline profile runbook：本次只改顶部小图标绘制，不改首屏 / 列表 / 输入框 / 滚动关键路径，暂不需要更新 profile 脚本。

- `HamburgerMenuSheet.kt` 小调汉堡设置页图标语义：会员中心图标从“盾牌 + 加号”改成更像会员权益的“盾牌 / 徽章 + 星光”，避免被误读成医疗或保障；隐私政策锁图标整体略放大，提升和其他 24dp 图标的视觉一致性。只改图标 Canvas，不改菜单结构、会员中心、点击占位、聊天滚动链、输入框、图片链或后端接口。

- `HamburgerMenuSheet.kt` 对照 ChatGPT 设置页截图收轻汉堡设置页视觉密度：页面左右边距小收、标题和菜单文字字号下调，菜单标题字重从 Medium 改为 Normal，返回圆按钮和图标缩小，菜单图标同步收为 24dp；分组内白色分割线取消左侧缩进，改为贯穿整张卡片。只改汉堡设置页排版和预览样式，不改菜单结构、会员中心、点击占位、聊天滚动链、输入框、图片链或后端接口。

- 多代理最终复查今日农情后补两处非架构加固：后端 `validateDailyAgriItem` 把“值得看 / 参考意义 / 对农户有用 / 根据搜索结果 / 本条新闻”等前端元表达和“速看 / 必看 / 重磅 / 紧急 / 暴涨 / 利好 / 震惊”等标题党词升级为硬过滤；Android 今日农情卡片增加 560dp 最大宽度，手机竖屏继续铺满，大屏 / 横屏不无限拉宽。同步把 runbook 示例里的“可关注本地走货节奏”改成纯事实口吻。仍不改生成架构、定时任务接口、点击跳浏览器、聊天消息主人或滚动锚点。

- 历史归档：`ChatScreen.kt` 曾经收口过今日农情卡片头部和条目来源 / 日期展示。该口径已被当前“用户侧只展示标题 + 摘要、卡片头部显示当天日期、单条不可点击跳外部链接”替代；当前仍不改后端生成、消息列表主人或滚动锚点。

- `ChatScreen.kt` 微调今日农情卡片展示：外层横向边距从 40dp 收到 20dp，去掉每条农情左侧 1/2/3 黑色编号圆点，让固定三条资讯以标题、摘要、来源日期为主，避免预览和正式聊天页里显得过窄、过像编号列表。只改今日农情 UI 样式，不改生成接口、后端校验、点击跳浏览器、聊天消息主链或滚动工作线。

- 多代理优化今日农情提示词：把首句从“今天值得看的”改为“面向中国农业生产经营场景”，删掉会诱导前端露馅的“参考意义”口径，新增农业实用价值排序、自然资讯口吻、反标题党、反网页注入和 JSON 字符串约束；摘要要求只写事实、数据和直接农业影响，禁止“值得看 / 对农户有用 / 参考意义 / 根据搜索结果”等元表达。只改生成提示词和记忆 / runbook 口径，不改接口、后端发布校验、Android 展示和聊天滚动链。

- 历史归档：多代理审查“今日农情”后曾做过一轮偏严格的低风险加固，包括不接受未来 `published_date`、要求模型显式带 `card_name = 今日农情`、失败原因按 UTF-8 安全截断、Android 只在 `status=ready` 且正好 3 条有效 item 时展示。当前 v29 已按用户拍板最大限度放开：后端不再把 `card_name`、发布日期、重复标题、主题词、广告词或养殖关键词作为硬过滤，只保留 JSON 可解析、3 条标题 / 摘要非空和私网 / 本机 URL 安全兜底；今日农情仍不进 A/B/C、不扣问诊次数、不由用户打开 App 触发生成。

- 历史归档：今日农情早期近 7 天去重曾写作给 `qwen3.5-plus` prompt 且过滤后不足 3 条不发布，随后又短暂允许 `2 到 3 条有效 item` 发布。当前已替换为 `qwen3.5-plus + OpenAI兼容 chat/completions + turbo`、正好 3 条、提示词控方向、后端低风险结构兜底。

## 2026-05-11

- 历史归档：首版“今日农情”接入时使用过 `qwen3.5-plus + search_strategy=max`、3 条 item、https-only 的早期规则。当前已替换为 `qwen3.5-plus + OpenAI兼容 chat/completions + turbo + forced_search=true`，发布正好 3 条有效 item，来源 URL 只做内部追溯，用户侧只展示标题摘要；今日农情仍不是 `ChatMessage`，不进入本地聊天快照、A/B/C 上下文、归档、摘要或问诊扣次。

## 2026-05-10

- `ChatScreen.kt` / `chat.go` / Android 备份规则做了一轮前后端守门复查后的低风险收口：未发送文字草稿不再在启动 reset 时被立刻清掉，`client_msg_id` 已完成的后端 replay 提前到 prompt 组装、模型 Key、进行中锁和额度检查之前返回，拿到 inflight 锁后还会二次复查完成态，减少已完成轮次恢复失败、重复走重链以及完成临界点重复开主模型的机会；`backup_rules.xml` / `data_extraction_rules.xml` 同步排除 device-protected 本地域，继续避免清数据 / 迁移后旧本地 UI 状态回流。后端同时新增 `013_topup_packs_lookup_index.sql`，给加油包状态查询补 `user_id/status/expire_at/created_at` 复合索引。同步更新 R9，保留“主模型已开但未归档时仍需后续持久化 attempt/status 才能彻底避免二次开流”的上线前风险。
- `HamburgerMenuSheet.kt` 轻微放大汉堡设置页“客服反馈”气泡图标：外框左右和上下占位扩大，尾巴下探一点，内部横线同步加长，让它在同一 26dp 图标列里不再比账号、检查更新、隐私锁等图标显小。只改反馈图标 Canvas，不改菜单结构、会员中心、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 将汉堡设置页“账号管理”图标从裸头像 / 肩线改成参考 Lucide `circle-user-round` 的账号圆形图标：外圈代表账号入口，内部保留头像和肩线，小尺寸下比原来更完整、更容易识别。只改账号图标 Canvas，不改菜单结构、会员中心、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 继续对齐汉堡设置页图标视觉尺寸：参考 Lucide `refresh-cw` 的双弧刷新结构，把“检查更新”图标改成上下两段弧线 + 两个拐角箭头，小尺寸下更容易识别；同时轻微放大“隐私政策”的锁体 / 锁梁，并扩宽“服务协议”文档图标，让这组图标在同一 26dp 画布内更接近统一视觉占位。只改图标 Canvas，不改菜单结构、会员中心、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 再收一轮汉堡设置页图标细节：检查更新图标去掉实心箭头，改回圆弧末端两条分叉线并加粗放大，更像直观刷新箭头；隐私政策锁图标改为一条 U 形锁梁接方块，避免上方半弧和下方锁体交界处线条外溢。只改图标 Canvas，不改菜单结构、会员中心、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 重做汉堡设置页两个弱识别图标：检查更新从细线箭头改成环形线 + 实心箭头尖，避免小尺寸下箭头看不见；隐私政策从抽象盾牌改为更直白的锁形线条图标。只改图标 Canvas，不改菜单结构、会员中心、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 去掉汉堡设置页标题下方的短 ID：账号相关信息后续归到“账号管理”，汉堡页顶部只保留“农技千查”，让页面更干净也继续释放首屏高度；会员中心仍保留短 ID，用于后续支付 / 客服核对身份。只改汉堡页顶部展示和项目记忆，不改会员中心、菜单结构、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 继续微调汉堡设置页图标：账号管理的小人图标头像略放大、肩线横向放宽，避免看起来胳膊太短；风险提示三角里的感叹号小点加重，提升小尺寸下的清晰度。只改图标 Canvas 几何，不改菜单结构、布局、会员中心、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 微调汉堡设置页首屏观感：全页内容顶部 padding 从 52dp 收到 36dp，让“农技千查 / ID”和菜单组整体上移，底部“退出登录”更容易露出；同时把“检查更新”图标改成更清晰的环形箭头，箭头尖更明显。只改汉堡设置页布局和图标 Canvas，不改菜单结构、会员中心、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` / `ChatScreen.kt` 精简汉堡设置页：删除首版占位里的“外观 / 数据与隐私 / 关于”，把“反馈问题”改为“客服反馈”，“用户协议”改为“服务协议”，新增“账号管理”和“检查更新”。当前入口结构为“会员中心 / 账号管理”“客服反馈 / 检查更新”“服务协议 / 隐私政策 / 风险提示”“退出登录”，副文案只保留会员中心、风险提示和退出登录；debug-only 文案预览同步更新。只改汉堡页菜单文案、分组和线条图标，不改会员中心、聊天滚动链、输入框、图片链、登录体系或后端接口。
- `HamburgerMenuSheet.kt` 修正汉堡页横向转场方向：左上角汉堡入口对应左侧页面语义，设置页打开改为从左侧滑入，返回时向左收回；继续只做位移动画，不叠加 fade、不加白底兜底。只改汉堡页转场方向，不改会员中心、`+` 附件面板、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 给汉堡全屏设置页补上短横向转场：设置页打开时从右侧滑入，返回时向右滑出，时长控制在 180ms / 150ms，只做位移动画、不叠加 fade、不加白底兜底，让返回主聊天界面时有方向感，避免“瞬间切掉”被感知成主界面轻微抖动。只改汉堡页转场，不改会员中心、`+` 附件面板、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 进一步收口汉堡页返回后的主界面轻微抖动：全屏设置页不再包 `AnimatedVisibility`，`visible=false` 时直接不渲染，避免退出时动画容器仍参与一帧 transition 收尾；会员中心和 `+` 附件面板暂保持上一版直接 `ExitTransition.None` 卸载。只改汉堡全屏设置页承载方式，不改汉堡菜单内容、会员中心、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` / `MembershipCenterSheet.kt` / `ChatComposerPanel.kt` 撤掉上一版浮层关闭时的延迟遮罩和 64ms 纯背景覆盖：联网核对 Android Compose 官方文档后，改为关闭时直接 `ExitTransition.None` 卸载浮层，进场动画保留，避免汉堡返回、会员中心关闭和 `+` 附件面板关闭时出现白闪、残影或多层退场不同步。同步更新根规则里的浮层退出口径；不改浮层内容、会员权益、附件入口、支付占位、聊天滚动链、图片链或后端接口。
- `HamburgerMenuSheet.kt` / `MembershipCenterSheet.kt` / `ChatComposerPanel.kt` 按会诊结果继续收口浮层残影：会员中心和 `+` 附件面板退出时，面板主体先滑出，遮罩延迟到主体退出后再淡出，避免底层聊天页过早透出；汉堡设置页关闭后额外保留 64ms 的纯背景覆盖，盖住返回键 / elevation 阴影可能残留的最后一帧。只改退场时序，不改浮层内容、入口层级、会员权益、附件逻辑、聊天滚动链或后端接口。
- `ChatComposerPanel.kt` / `MembershipCenterSheet.kt` / `AGENTS.md` 继续统一浮层退出规则：`+` 附件底部面板退出时不再让面板内容淡出，遮罩跟到面板滑出结束；“订购成功”居中卡片关闭时也直接卸载，不再短淡出；根规则新增浮层退出动画约束，后续整页设置、半屏面板和居中卡片都避免主体内容半透明压到下一层形成残影。只改浮层退出动画和规则文档，不改附件入口、会员权益、支付占位、聊天滚动链、图片链或后端接口。
- `HamburgerMenuSheet.kt` / `MembershipCenterSheet.kt` 收口浮层关闭残影：汉堡设置页关闭时不再整页淡出，避免返回键半透明残留在聊天页左上角；会员中心关闭时保留遮罩到面板滑出结束，并取消面板内容淡出，避免价格 / 次数文字在底层页面上形成残影闪烁。只改浮层退出动画，不改入口层级、会员权益、汉堡菜单内容、聊天滚动链、输入框、图片链或后端接口。
- `ChatScreen.kt` 修复从汉堡设置页打开会员中心时底层背景跳回聊天页的问题：汉堡页入口现在只打开会员中心，不再同时关闭汉堡设置页；会员中心仍按更高层级盖在上方，关闭会员中心后会回到汉堡设置页。只改浮层可见状态切换，不改会员中心内容、汉堡页布局、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 修复汉堡设置页浮层返回键点击失效：返回按钮现在放在全屏滚动内容之后绘制，保证位于最上层接收点击，并在点击时触发轻触觉反馈后关闭设置页。只改汉堡设置页返回按钮层级和反馈，不改设置入口、会员中心、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 将汉堡设置页返回按钮从内容滚动 Column 中拆出来，改为左上角独立浮层；“农技千查 / ID”和下方设置卡片不再被返回按钮高度顶下去，内容区从更靠上的位置开始。只改汉堡设置页布局结构，不改会员中心、聊天滚动链、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 继续轻微上移汉堡设置页内容：顶部内容 padding 从 14dp 再收到 8dp，页面块间距从 16dp 收到 14dp，在保留状态栏安全区的前提下减少顶部空白。只改汉堡设置页布局密度，不改聊天主界面、会员中心、输入框、图片链或后端接口。
- `HamburgerMenuSheet.kt` 微调左上角汉堡设置页首屏垂直位置：顶部内容 padding 从 22dp 收到 14dp，页面块间距从 22dp 收到 16dp，让“农技千查 / ID”和第一组设置入口整体上移，减少标题上方空白。只改汉堡设置页布局密度，不改会员中心、聊天滚动链、输入框、图片链或后端接口。
- `MembershipCenterSheet.kt` 小修会员中心读取中态：`/api/me` 重新加载或支付成功后刷新时，套餐标题后方的“升级补偿次数 / 加油包”胶囊和加油包卡片只使用已加载完成的最新 entitlement；读取中 / 未同步时不再短暂沿用上一轮旧余额。只改加载态展示一致性，不改 `/api/me` 字段、会员权益、按钮 gating、支付占位或扣次逻辑。
- `server-go/internal/app/quota.go` 修复加油包扣减状态判断：扣加油包时先锁定并读取扣减前 `remaining`，再决定本次扣完后是否把包标为 `used_up`，避免 MySQL 单表 `UPDATE` 赋值顺序导致 `remaining=2` 时提前变成 `used_up`，让 100 次加油包少用 1 次。新增单测锁住 `2 -> active`、`1 -> used_up`；不改每日额度、升级补偿、加油包消耗顺序、会员 UI 或支付占位。
- `MembershipCenterSheet.kt` / `ChatScreen.kt` 继续调整会员中心动作文案：套餐按钮保持“开通 Plus / 开通 Pro / 同步后开通”，加油包从“购买加油包 / Plus Pro 可买”改为“订购加油包 / Plus Pro 可订购”；Pro 用户看 Plus 卡片时从“Pro 已包含”改为“当前为 Pro”，避免因为 Plus / Pro 次数不同而让“已包含”听起来不严谨。debug-only 预览面板同步标明 Pro 状态下 Plus 卡片会显示“当前为 Pro”；只改用户可见文案和预览说明，不改会员等级、订购资格、支付占位、扣次顺序或后端接口。
- `MembershipCenterSheet.kt` / `ChatScreen.kt` 将 Free 到期后仍有加油包余额时的按钮文案从“剩余可用”改为“剩余次数可用”，并同步 debug-only UI 文案预览面板里的“加油包：Free剩余”说明，让用户更明确这是历史剩余次数还能继续用，而不是 Free 可以直接续订加油包。只改会员中心按钮文案、预览说明和项目记忆，不改 `/api/me`、加油包订购资格、扣次顺序、支付占位或后端接口。

## 2026-05-08

- `ChatScreen.kt` / `MembershipCenterSheet.kt` / `HamburgerMenuSheet.kt` / `AndroidManifest.xml` 根据多代理 UI 复查补一组低风险兜底：失败尾部“点击重发 / 点击重试”在生成中、发送收口中或图片上传中会置灰并在函数入口直接返回，避免删掉旧失败状态但没有真正发起请求；聊天区用户图片预览改为按顺序本地 URI 优先、远端 URL 补位，避免发送成功后一张图同时以本地和远端重复显示；会员中心读取中不再沿用旧档位启用购买按钮，Free 到期后仍有加油包余额时按钮显示“剩余可用”而不是“用完再续”；汉堡菜单补 navigation bar 避让并让 debug-only 预览覆盖完整菜单入口；manifest 增加 `ACTION_IMAGE_CAPTURE` 查询声明，输入框删除未发送缩略图时同步清理私有上传副本。只做 UI / 本地状态 / 相机授权兜底，不改会员权益、扣次顺序、后端接口、图片压缩序列、WorkManager、聊天滚动链或 96dp 工作线。
- `MembershipCenterSheet.kt` / `ChatScreen.kt` 统一会员中心顶部状态条颜色层级：`今日剩余` 和当前档位名共用同一标题色，剩余次数和到期日期 / 基础额度共用同一信息色，并同步 debug-only 预览面板说明，避免同一个小框里四处颜色不一致。只改会员中心视觉层级和预览说明，不改会员权益、扣次、补偿次数、加油包或后端接口。
- `MembershipCenterSheet.kt` 继续调整会员中心顶部状态条字号层级：上方“今日剩余”和当前档位名改为 13sp Bold，下方剩余次数、到期日期 / 基础额度收为 13sp 普通字重，让标题比信息值更明确。只改会员中心视觉层级，不改会员权益、扣次、补偿次数、加油包或后端接口。
- `ChatScreen.kt` 同步更新 debug-only UI 文案预览面板里的会员中心状态条说明：Free / Plus / Pro 预览项现在明确标注“标题 / 档位加粗，次数 / 到期普通”，方便核对刚调整过的会员顶部状态条层级。只改 debug 预览说明，不改真实会员中心业务逻辑、扣次、支付占位或后端接口。
- `MembershipCenterSheet.kt` 微调会员中心顶部状态条字重：左侧“今日剩余”和右侧当前档位名保持加粗，下面的剩余次数、到期日期 / 基础额度改为普通字重，避免数字和日期抢过标题层级。只改会员中心视觉层级，不改 `/api/me`、会员权益、补偿次数、加油包、支付占位或扣次顺序。
- 会员中心 / 会员后端做一轮前后端审查后收口高风险点：Android 端会员信息未同步时，Plus / Pro 开通按钮改为置灰“同步后开通”，避免在 `/api/me` 未拿到后端真相时启动购买；会员中心刷新到可用每日额度、升级补偿或加油包后，会清掉当天本地“次数用完”发送锁。后端开发期订单接口 `/api/tier/renew_plus`、`/api/tier/renew_pro`、`/api/tier/upgrade_plus_to_pro`、`/api/topup/buy` 默认返回 `PAYMENT_NOT_CONFIGURED`，只有显式设置 `ALLOW_DEV_ORDER_ENDPOINTS=true` 才允许本地 / 内测调试；Plus / Pro 若没有有效 `tier_expire_at` 会按 Free 处理，避免脏数据永久会员；`012_chat_stream_single_user.sql` 的唯一索引迁移改为幂等，避免后端重启重复执行迁移失败；删除未使用的 Android `USE_BACKEND_ENTITLEMENT` BuildConfig 开关，避免误以为会员中心还有前端真相开关。只收口会员 / 扣次安全边界，不改聊天滚动链、图片链、Plus 升 Pro 补偿算法或正式支付回调方案。
- `MembershipCenterSheet.kt` / `ChatScreen.kt` 调整会员中心额外次数展示：顶部“今日剩余”状态条只保留今日剩余、当前档位和到期 / 基础额度；升级补偿和加油包从状态条内部挪到“套餐”标题后方两个胶囊里，并把“补偿”改成“升级补偿次数”。Free 用户如果是会员到期后仍有升级补偿或加油包，也会在套餐标题后继续展示；“套餐”标题固定 24dp 视觉高度并和两个额外次数胶囊居中对齐。debug-only UI 文案预览面板同步改成可预览这类状态；图片无障碍读屏文案继续留在正式组件的 `contentDescription` 里，但不再出现在文案预览面板。
- `ChatScreen.kt` 收掉两个低风险图片兜底 P3：聊天区用户图片预览改为本地 `imageUris` 优先、远端 `imageUrls` 兜底，弱网下只要本地副本还在就不先卡远端图；冷启动 / 远端 hydrate 后如果同一窗口里有多条仍在 WorkManager 队列中的本地图文 pending 消息，会把这些 pending 消息一起纳入远端归档恢复轮询，不再只盯最后一条，并且这种补旧答案的恢复不会打断用户后面新发起的活跃 SSE。`composerCollapseOverlayVisible` 复查后确认仍参与输入框收口遮罩和高度释放，不是可直接删除的死链；本次不动输入框收口、聊天滚动链、图片上传、扣次、WorkManager 调度或后端接口。

## 2026-05-06

- `ChatScreen.kt` / `HamburgerMenuSheet.kt` / `MembershipCenterSheet.kt` 接入首版左上角汉堡设置页：汉堡按钮现在打开黑白简洁的设置页，顶部展示 App 名和本机短 ID，入口包括会员中心、外观、数据与隐私、用户协议、隐私政策、风险提示、反馈问题、关于和退出登录。除“会员中心”会复用现有会员面板外，其余入口先显示“功能后续接入 / 登录功能后续接入”轻提示，不做登录页、不做头像、不清空聊天、不接多会话历史、不新增后端接口。会员中心标题同步展示短 ID；debug-only UI 文案预览面板补齐会员标题 ID、汉堡菜单、会员读取中、Free 仍有补偿 / 加油包、Free 加油包不可买等隐藏态。已复查 baseline profile runbook：本次新增设置页入口，不改聊天首屏 / 列表 / 输入框 / 滚动关键路径，暂不需要更新 profile 脚本。
- `MembershipCenterSheet.kt` 统一会员中心顶部状态条左右两列字号：左侧“今日剩余”和右侧档位名同为 12sp，左侧剩余次数和右侧到期日期同为 15sp，让“补偿 x次 / 加油包 x次”两个胶囊落在同一视觉高度。只改会员中心状态条视觉，不改会员权益、扣次顺序、支付占位或后端逻辑。
- `MembershipCenterSheet.kt` 再收细会员中心额度条和套餐按钮：今日剩余数字从 18sp 收到 17sp，到期日期从 12sp 放到 13sp；补偿 / 加油包胶囊统一固定 24dp 高并垂直居中，避免左右看起来高低不一。当前同档套餐按钮改为灰色“当前套餐”，Plus 用户仍可点“升级 Pro”，Pro 用户不能降买 Plus；当前支付占位、会员权益、扣次顺序和后端订单逻辑均不变。
- `MembershipCenterSheet.kt` 继续微调会员中心状态条和订购成功卡片：升级补偿胶囊放在“今日剩余”数字下方，加油包胶囊放在右侧套餐 / 到期日期下方，避免两个额外次数都挤在左下角；“订购成功”确认按钮从通栏白胶囊收为 168dp 小胶囊，降低按钮视觉重量。只改会员中心视觉排版，不改 `/api/me`、会员权益、补偿次数、加油包规则、扣次顺序或支付占位。
- `MembershipCenterSheet.kt` / `ChatScreen.kt` 把会员中心顶部状态条里的额外次数从纯文字“升级补偿 x 次 · 加油包 x 次”改成底部独立的轻量胶囊标签“补偿 x次 / 加油包 x次”，避免和右侧 Plus / 到期日期抢同一行空间；debug-only UI 文案预览面板同步补齐会员中心套餐区、加油包可买 / 未用完、支付暂未接入、订购成功和规则说明等隐藏态。只改状态条视觉表达和 debug 预览覆盖，不改 `/api/me`、升级补偿次数、加油包次数、扣次顺序或支付占位。
- `MembershipCenterSheet.kt` / `ChatScreen.kt` 继续收细会员中心顶部状态条视觉：把主数字从 20sp 收到 18sp，右侧套餐 / 到期日改成整组靠右但组内左对齐，并把 debug 预览面板里的会员样例标题改成“Free 基础额度 / Plus 额外次数 / Pro 到期日 / 会员信息未同步”，避免预览标题和状态条内容重复，也避免 `Pro` 和日期贴右边显得别扭。只改状态条视觉和 debug 预览标题，不改 `/api/me`、会员权益、扣次顺序或支付占位。
- `MembershipCenterSheet.kt` 调整会员中心顶部状态条排版：把“升级补偿 / 加油包”额外次数从左右 Row 里移到状态条底部独立一行，避免 Plus + 到期日 + 额外次数同时出现时把右侧“到期 yyyy-MM-dd”挤成两行。debug-only 预览面板继续复用真实状态条组件；不改 `/api/me`、会员权益、扣次顺序或支付占位。
- `ChatScreen.kt` / `MembershipCenterSheet.kt` 给 debug-only UI 文案预览面板补上会员中心顶部状态条分组：直接复用真实 `MembershipQuotaSummary`，可预览 Free 基础额度、Plus 带升级补偿 / 加油包、Pro 到期日和未同步四种“平时不一定看得到”的状态。只放开组件可见性并增加 debug 预览样例，不改 `/api/me`、会员权益、支付占位、扣次顺序或正式会员中心逻辑。

## 2026-05-05

- `MembershipCenterSheet.kt` 把 Plus 升 Pro 规则文案调整为“升级 Pro 后，Plus 剩余权益会自动折成补偿次数。”，让会员中心读起来更顺、更产品化。只改用户可见文案和项目记忆，不改升级补偿算法、订单占位、支付接口、会员权益、扣次顺序或聊天链路。
- `ChatScreen.kt` 重新整理 debug-only UI 文案预览面板：从一串平铺列表改为“顶部与空态 / 输入区 / 附件面板 / 图片与预览 / 消息尾部 / 异常浮层 / 选择菜单 / 预览面板”分组，补齐“正在重发... / 正在重试...”、输入框图片角标、全屏图片页码、图片无障碍文案和输入菜单变体。预览项继续复用真实常量和正式组件样式，只服务 debug 包核对隐藏态文案，不改真实触发逻辑、图片选择 / 上传、聊天滚动链或会员逻辑。
- `MembershipCenterSheet.kt` 对齐会员中心和后端权益口径后小修 UI：加油包卡片读取 `/api/me.topup_remaining`，当前仍有未用完加油包时按钮显示“用完再续”并置灰，避免用户以为能重复买；Plus 升 Pro 规则文案说明剩余权益会自动折成补偿次数。复查结果：Plus / Pro 每日次数、到期自动按 Free、升级补偿次数、加油包仅 Plus / Pro 可买、扣次顺序均与后端一致；真实支付回调尚未接入的后端订单接口风险已转入 `open-risks.md`。
- `ChatScreen.kt` 给真实后端 SSE 首段回复加 500ms 小球最短显示闸门：远端请求开始后，如果首段内容在 500ms 内返回，会先让 waiting 小球至少显示到 500ms 再开始吐字；若模型本身超过 500ms 才回，则不额外延迟。该闸门只缓冲远端第一批 chunk，并把完成 / 中断回调排在首批 chunk 释放之后，避免小球一闪而过；本地假流仍保持原有 2.2 秒最短小球时间，聊天滚动链、扣次、WorkManager 和后端逻辑均不变。
- `ChatScreen.kt` 小修图片失败重发 / AI 尾部重试的离线体感：如果失败消息还只有本地图片、需要重新上传，点击“点击重发 / 点击重试”时先检查网络；无网络时只提示“当前网络不可用”，原失败胶囊不消失。有网络并开始重传时，胶囊原位变成“正在重发... / 正在重试...”，上传成功并真正进入重发后才消失，上传失败则恢复原失败文案，避免无服务器 / 离线测试时胶囊闪一下、列表轻微下拽。文字重试、已上传图片重试、发送 / WorkManager / 后端扣次和聊天滚动链均不变。
- `MembershipCenterSheet.kt` 继续收口会员中心底部规则区：删除底部“当前支付功能暂未接入”说明，把规则压成“Plus升级Pro / 扣次顺序”两条短文案，并把升级说明统一放进轻量浅灰卡片，避免裸文字松散也避免多张卡片拉长页面。右上角关闭叉号继续放大；购买按钮点击后的“支付暂未接入”即时提示、真实支付成功预留卡片、套餐 / 加油包规则和后端逻辑均不变。
- `MembershipCenterSheet.kt` / `ChatScreen.kt` 为后续真实支付成功预留统一收口 UI：会员中心可显示中间黑底白字卡片“订购成功 / 确定”，用户点确定后关闭卡片并重新拉取 `/api/me` 同步当前套餐、今日剩余、升级补偿和加油包次数。当前支付功能仍未接入，现有开通 / 升级 / 购买按钮继续只提示“支付暂未接入”，不会假弹成功，也不调用后端订单接口。
- 历史归档：摘要模型曾做过轻量候选切换并补关闭思考参数单测。当前这条轻量候选已删除，摘要固定 `qwen-plus`、顶层 `enable_thinking=false`、非流式、不联网。
- `ChatScreen.kt` 将主聊天页背景从 `#F6F7F8` 轻微提亮到 `#F8F9FA`，让页面观感更透亮，同时保留输入框白壳、用户文字气泡边框 / 阴影、AI 文本、附件面板、图片预览和滚动链不变。
- `ChatComposerPanel.kt` 收小 `+` 附件底部卡片里的“相机 / 照片”入口框：入口卡片高度从 118dp 降到 104dp，圆角和上下留白同步收紧，底部面板左右内边距略增，让两个入口不再显得过大笨重。相机 / 照片图标、文案、Photo Picker、相机 FileProvider、图片私有副本、压缩上传和聊天滚动链均不变。
- `ChatScreen.kt` / `ChatComposerCameraStore.kt` 先做聊天页瘦身的第一刀：把 App 内相机目标创建、FileProvider 授权 / 撤销、相册发布 / 删除、临时拍照文件清理等纯相机文件 helper 从 `ChatScreen.kt` 搬到独立文件，调用点和逻辑保持不变。该改动只降低 `ChatScreen.kt` 维护压力，不改相机行为、图片私有副本、压缩上传、图片预览、发送恢复、会员中心或聊天滚动链。
- `ChatScreen.kt` 轻微增强用户文字消息气泡的黑白灰分层：用户气泡边框从浅蓝灰改为更中性的浅灰并略加粗，同时增加约 1dp 的低透明黑色阴影，让用户消息和 AI 正文更容易区分。只改用户文字气泡视觉，不改用户图片缩略图、AI 消息渲染、输入框、图片预览、消息发送或聊天滚动链。
- `MembershipCenterSheet.kt` 继续收口会员中心 Plus / Pro / 加油包文案：Plus 展示“每天25次问诊 / 图文问题随时问 / 记忆与上下文更强”，Pro 展示“每天40次问诊 / 复杂问题推理更强 / 适合多作物、多地块复盘”，加油包副标题改为“额外100次”，说明压成“Plus / Pro 可买，永久有效，用完再续。”用户可见套餐卡片不再展示具体上下文轮数或说明书式加油包解释；只改 UI 文案，不改 `/api/me` 字段、支付占位、会员周期、扣次顺序、加油包购买限制或聊天链路。
- `ChatScreen.kt` / `ChatComposerPanel.kt` 小修相机入口细节：拍照成功后复制到系统相册的目录名对齐当前 App 名称为 `Pictures/农技千查`；`ComposerCameraIcon` 内部镜头圆心从 `0.575h` 上移到 `0.555h`，让相机图标视觉重心更贴近用户截图。只改相册目录名和 Canvas 几何比例，不动相机 FileProvider / Photo Picker / 私有图片副本 / 压缩上传 / 图片预览 / 滚动链。
- `server-go/internal/app/upload.go` / `chat.go` / `chat_test.go` / `ImageUploader.kt` 补上后端图片入口兜底：`POST /upload` 改为先鉴权，只收单张 `<=1MB` JPEG，Android 上传同步带 `X-User-Id` 和可选 bearer token；`/api/chat/stream` 对图片 URL 增加后端校验，只接受当前公开基地址下的 `/uploads/*.jpg`，防止非 App 客户端绕过 Android 端压缩 / 转 JPEG / 4 张限制，把外部大图或非上传域名直接塞进主模型。新增单测覆盖合法上传 URL、外部域名、非 `/uploads` 路径和非 JPG 后缀；不改 Android 图片压缩序列、输入框预览、聊天区预览、WorkManager 和扣次顺序。
- `AndroidManifest.xml` / `ChatScreen.kt` 复查相机 / 相册跨机型兼容后做低风险加固：照片入口继续使用 Android 官方 Photo Picker，并按官方建议在 manifest 声明 Photo Picker backport module 依赖，帮助旧系统 / Android Go + GMS 设备安装回传选择器模块；相机入口从 `TakePicture()` 改为显式构造 `ACTION_IMAGE_CAPTURE` intent，仍写入同一个 `NongjiFileProvider` 临时 URI，但额外加读写 grant flags、ClipData，并对可解析相机包显式授权，回调或启动失败后撤销授权。私有 `composer_images` 副本、<=1MB 压缩 / 直通、拍照后相册保存、发送 / WorkManager / 后端扣次链、图片预览和聊天滚动链均不变。
- `server-go/internal/app/inflight.go` / `quota.go` / `chat.go` / `server.go` / `store.go` / `SessionApi.kt` / `PendingChatSendWorker.kt` / `SessionSnapshot.kt` / `ChatScreen.kt` 再按“扣用户次数、主模型成本、摘要模型成本、相机 / 相册兼容”复查并收紧边界：`chat_stream_inflight` 新增同一 `user_id` 活跃流唯一约束，迁移前会保留每个用户最新租约并清理重复行，避免不同 `client_msg_id` 并发打穿额度预检查后同时多开 Qwen3.5-Plus；`daily_usage` 在扣减事务内改为 `FOR UPDATE`，并补单测确认每日额度按后端上海时区 0 点换日，请求开始时记录的 `day_cn` 决定本轮扣哪一天。已归档 replay 不再尝试补扣旧轮次，避免跨日 / 会员档位变化后误扣用户；连续数据库异常下残余风险转为可能漏记一次成本，后续需后台对账。旧 `/api/session/round_complete`、`/api/session/b`、`/api/session/c` 统一返回 410，同时删除 Android 旧 `appendA/updateB/updateC` 客户端方法、旧请求体和后端旧写入死代码，避免绕过主链扣次或重复触发摘要模型；Android 前台 SSE 自动 stream retry 关闭，WorkManager 只对进行中 / 本地停止 / 限流做保守重试，不再对一般模型开流失败反复补发。相机 FileProvider 改为 App 自定义 `NongjiFileProvider` 子类，外部相机拍完后的相册发布继续检查 `IS_PENDING=0` 更新结果，失败会清理占位；图片压缩、输入框预览、聊天区预览和滚动链不变。
- `server-go/internal/app/chat.go` / `inflight.go` / `server.go` / `store.go` / `summary.go` 复查昨晚 WorkManager、相机、主模型、摘要模型和扣次链后补三处后端保险：主模型上游开流不再自动做第二次 `OpenStream` 重试，降低同一轮极端双调 Qwen3.5-Plus 的成本风险；`chat_stream_inflight` 获取结果改为对比 lease token，不再依赖 MySQL `RowsAffected` 语义；旧 `/api/session/round_complete` 若发现同一 `client_msg_id` 主流式仍在进行中，会返回 `STREAM_IN_PROGRESS`，不再抢写主链。当日已归档轮次 replay 时会按原完成日期异步尝试补 `quota_ledger`，重复扣仍由唯一键拦住；隔天历史轮次不在用户 replay 时硬补，避免会员档位变化后按错误权益补旧账。摘要 B/C 写入增加“用户 + 层”运行中保护和 `round_total` 版本校验，避免摘要模型并发重复跑太多以及旧摘要覆盖新轮次。`ChatScreen.kt` 同步小修相机保存到系统相册的发布结果判断：只有 `IS_PENDING=0` 发布成功才认为相册保存成功，失败时清理占位；输入框上传副本和压缩链不变。本次不改聊天滚动链、图片预览手势、输入框布局或会员 UI。

## 2026-05-04

- `ChatScreen.kt` / `server-go/internal/app/chat.go` / `server.go` 复查并加固“后台恢复 + 不重复扣费”链：Android 端把 SSE `replay` 事件也纳入长窗口 snapshot 恢复，前台打开时若发现仍在 WorkManager 队列里的本地图文消息，会继续轮询后端归档并恢复完整 AI 回复，不再只能等下次冷启动。后端 `/api/session/round_complete` 对 replay 不再触发摘要任务，避免旧接口重放时重复调用摘要模型。`/api/chat/stream` 继续只在轮次归档成功后才向客户端发送 `[DONE]`；额度扣减在归档成功后执行，若 `ConsumeOnDone` 临时失败会按同一 `client_msg_id` 短重试，重复扣由 `quota_ledger` 唯一键防住。该口径优先保证用户能拿到已归档答案，连续数据库异常导致的漏记成本仍列入未关闭风险观察。
- `ChatScreen.kt` 调整 App 内相机拍照主链：优先给外部相机传 App cache 下的 FileProvider 临时 URI，拍照成功后先导入 App 私有 `composer_images` 副本，导入成功才在 Android Q+ 把原始拍照结果复制到系统相册 `Pictures/农技千查`；拍照取消、相机启动失败和临时文件都会清理。该改动替代 5 月 3 日“优先直接写 MediaStore 相册 URI”的方案，避开部分手机外部相机返回成功但 App 立刻读不到 MediaStore 内容导致“图片无法读取，请重新选择”的问题，不改图片压缩、上传、输入框预览、聊天区预览或滚动链。
- `app/build.gradle.kts` / `ChatScreen.kt` / `ApiConfig.kt` 清理 Android 客户端旧直连模型链：删除 `QwenClient.kt`、`ModelService.kt`、`ModelParams.kt`，移除 `BuildConfig.API_KEY` 注入和启动时的旧 `QwenClient.resetUiRuntimeForCleanState()` 调用；`ApiConfig` 只保留当前后端 `/api/chat/stream` 路径。Android 端不再从 `local.properties`、`BAILIAN_API_KEY` 或 Gradle property 读取模型 Key，主对话和图片问诊只允许经由后端发起模型调用；`gradle.properties` 的旧 API_KEY 配置注释已删除，独立诊断脚本 `scripts/verify_model_url.js` 明确标注“仅本地诊断、不属于 Android 运行时”，且只读取 `DASHSCOPE_API_KEY`。同步把旧直连链风险从“待清理代码风险”改为“如历史 APK 曾打入真实 Key，则需轮换密钥”的上线前检查项。
- `server-go/internal/app/chat.go` 收紧 SSE 完成边界：上游 `[DONE]` 不再立即透传给 App，而是先等 `AppendSessionRoundComplete(...)` 成功写入轮次归档 / ledger 后，再向客户端发送 `data: [DONE]`；若归档失败，客户端不会误收完成态，会按中断 / 重试恢复。
- `server-go/internal/app/chat.go` / `store.go` / `ChatScreen.kt` 继续加固图片发送后台兜底的幂等和恢复边界：`/api/chat/stream` 的完成 replay 改以 `session_round_ledger` / 轮次归档成功为真源，不再只看 `quota_ledger`；完成收口先归档成功轮次，额度扣减随后执行并依赖 `quota_ledger` 对同一 `client_msg_id` 幂等，避免“客户端已收完成态但回答没归档”导致空 replay；`chat_stream_inflight` 锁前移到限流 / 额度预检查之前，让重复请求稳定返回 `409 STREAM_IN_PROGRESS` 走恢复链。Android 端把后台冲突恢复窗口拉长到约 10 分钟，并把本地图片 pending 失败态扫描从尾部消息扩展到当前窗口内所有未完成本地图消息，减少“前台显示失败但后台还在跑”和非尾部 pending 丢重试入口的边角风险。同步把裸 `X-User-Id` 生产鉴权和历史模型 Key 轮换确认列入未关闭风险，后续上线前单独收口。
- `ChatScreen.kt` / `SessionApi.kt` / `server-go/internal/app/chat.go` 为带图片发送接入 WorkManager 延迟兜底和后端进行中幂等锁：图片用户消息上屏并写入本地快照后，会按 `chatScopeId + userMessageId` 排一个唯一后台任务；前台上传 / 发起 `/api/chat/stream` 时标记该消息为 active，后台只重试不抢跑，前台开始远端请求后再写入 10 分钟保护窗。后端新增 `chat_stream_inflight` 表和 lease token，同一 `user_id + client_msg_id` 在完成归档前只允许一个上游模型流启动，重复请求返回 `409 STREAM_IN_PROGRESS`，前端对该原因走长窗口 snapshot 恢复而不是快速失败，完成后继续按 replay / snapshot 收口；前台 SSE 也会识别 replay 事件，不再把 replay + DONE 当成空回复完成。前台上传失败并显示“发送失败”时会取消对应后台任务，避免 UI 显示失败但后台偷偷消耗额度；冷启动 hydrate 会保留仍在后台队列中的图片用户消息，不让它从 UI 消失。同步接入 AndroidX WorkManager `work-runtime-ktx:2.11.2`，并更新当前状态 / 风险记忆。
- 历史归档：当时曾保留 `scripts/verify_model_url.js` 作为本地图像模型 URL 诊断脚本，后来用户拍板删除 Flash 相关实验路径，本轮已删除该脚本；后续不要再按这条历史记录恢复 `qwen3-vl-flash` 或客户端 / 本地直连模型诊断链。

## 2026-05-03

- `ChatScreen.kt` 补齐图片上传阶段被杀后的恢复兜底：本地聊天窗口读取 / 远端合并时会识别“尾部用户消息只有本地 `imageUris`、没有远端 `imageUrls`”的图片尾巴，并标记为 `network` 失败态；冷启动 / 远端 hydrate 时这类消息不再误走远端 assistant 恢复等待，而是直接保留成“发送失败 · 点击重发”，用户点重发时复用既有本地图重新上传。正常上传中的运行时快照不提前标失败；图片上传成功后会在后台协程里先落盘带 `imageUrls` 的用户消息快照，再发起 `/api/chat/stream`，避免进程在极窄窗口被杀后丢掉远端恢复链；stage 阶段旧异步快照写入前也会确认该消息仍处于“只有本地图、没有远端 URL”的上传中状态，避免旧快照晚到覆盖带 URL 的新快照。该改动只是“不丢消息、可手动重发 / 可恢复”的小兜底，真正跨进程自动执行“图片上传 + 发起对话”仍需后续接 WorkManager。本次不拆 debug 预览面板、不改图片压缩规则、不动聊天滚动链或 96dp 工作线。
- 代码结构复盘：`ChatScreen.kt` 当前确实偏重，主要沉重在聊天页滚动几何、发送 / 恢复、图片链和 debug-only 文案预览共同驻留；会员中心 UI 已在 `MembershipCenterSheet.kt`，图片全屏预览已在 `ImagePreviewPager.kt`，旧 active-zone / overlay / raw delta 未在运行时残留。该问题先记录为维护性风险，暂不为“瘦身”拆预览面板或滚动主链。
- `server-go/internal/app/chat.go` 恢复纯图片问诊的内部模型提示：当用户本轮只上传图片、未输入文字时，后端在多模态 content 里先加入一条用户不可见的说明，引导模型先基于图片可见信息给农业技术参考判断，并在作物 / 部位 / 症状 / 环境不足时追问必要信息；纯文字和图文混合请求不受影响。同步更新后端单测、根规则和当前状态记忆。
- `ChatScreen.kt` 加强后端流式回复的切后台恢复：后端 `/api/chat/stream` 已用独立上游 context 继续跑完模型并在 DONE 后归档；Android 端现在在远端历史模式下从后台回到前台时，会围绕当前 `clientMsgId` 持续拉取 `/api/session/snapshot`，若后端已完成则直接用归档轮次恢复完整 AI 回复并取消本地流式连接；冷启动发现尾部用户消息未配对 AI 回复时，也延长同一恢复窗口，减少用户切 App / 锁屏后回来只看到“回复未完成”的概率。只改远端恢复窗口和生命周期接线，不改 SSE 正常直播、额度扣减、图片上传压缩、聊天滚动链或 96dp 工作线。
- `ChatScreen.kt` 优化失败尾部胶囊交互：用户消息“发送失败 · 点击重发”和 AI 消息“回复未完成 · 点击重试”点击时复用现有轻触觉反馈；带图片的重试 / 重发在通过额度与网络检查后立即收起失败胶囊，若重新上传图片仍失败再恢复失败状态，避免用户点完后胶囊长时间不动误以为没点中。只改失败尾部点击反馈和重试状态收口，不改图片压缩上传规则、SSE 主链、聊天滚动链或 96dp 工作线。
- `ImageUploader.kt` / `ChatScreen.kt` 收口图片异常提示：删除旧的压缩超限用户可见兜底和 debug 预览项，压缩链会继续等比缩小直到 `<=1MB`；图片读取失败文案改为“图片无法读取，请重新选择”，避免和“能解码就自动转 JPEG”的真实逻辑冲突，也避免多图场景里暗示用户知道是哪一张坏图。同步更新 UI 文案台账和 Android 边界回归文档。
- `MembershipCenterSheet.kt` 收紧会员中心底部面板弹起动画：遮罩淡入从 120ms 收到 80ms，卡片上滑从 220ms 收到 165ms，退出动画也同步缩短，减少点击右上角叶片后“略慢、略卡”的体感。只改会员中心弹层动画参数，不改 `/api/me` 加载、套餐文案、支付占位、聊天滚动链或输入框。
- `ChatScreen.kt` 为图片进入输入框增加合格 JPEG 直通：若原图已经是 JPEG、`<=1MB`、最长边 `<=1024px` 且 EXIF 方向正常 / 未定义，直接复制到 App 私有 `composer_images` 作为上传副本，不重新编码；PNG、超大图、方向需要修正或 EXIF 读取异常的图片仍走既有 `ImageUploader.compressImage(...)` 序列。发送链继续复用私有 JPEG，不做二次压缩；不改图片数量、上传接口、相机相册入口、聊天滚动链或 96dp 工作线。
- `ChatScreen.kt` 调整 App 内相机拍照落点：Android Q+ 优先把原始拍照结果写入系统相册 `Pictures/农技千查`，让用户以后能在手机相册里找回现场记录；输入框 / 聊天区 / 上传仍使用导入时压缩并复制到 App 私有 `composer_images` 的 JPEG 副本，发送时若确认该副本仍是私有 JPEG 且 `<=1MB` 会直接复用，不再二次压缩。拍照取消 / 相机启动失败会清理未完成相册占位；旧 Android 仍回退到 App cache 临时 URI。本次不新增相机、相册、存储或定位权限，不改照片选择、图片上限、压缩序列、上传接口、聊天滚动链或 96dp 工作线。
- `ChatScreen.kt` 轻微收小右上角会员绿叶图标的视觉尺寸：普通宽度从 31dp 调到 30dp，小屏从 29dp 调到 28dp；点击热区、黑底图标资源、左侧菜单、会员中心入口和聊天滚动链均不变。该改动只为减轻顶栏右侧视觉重量。
- `ImagePreviewPager.kt` 关闭图片预览在捏回最小 1 倍时的 under-zoom 触觉反馈：Telephoto 的最小缩放边界改为 `OverzoomEffect.Disabled`，避免用户双指缩回正常大小时手机连续震动；最大放大边界仍保留 RubberBanding，输入框图片预览和聊天区图片预览继续共用同一组件。只改图片预览缩放手感，不改图片选择、压缩上传、消息发送或聊天滚动链。
- `MembershipCenterSheet.kt` 继续收口会员中心顶部：删除标题下“次数、追问记忆和加油包”副文案；顶部状态条左侧保持“今日剩余”，有升级补偿 / 加油包时才用小字补充对应次数，右侧只显示当前档位，Free 显示“基础额度”、Plus / Pro 显示到期日期，未同步时只显示“未同步”。确认后端 Plus / Pro 周期当前是 30 天；套餐到期暂不做主动提醒，到期后仍由后端自动按 Free 权益计算。
- `MembershipCenterSheet.kt` 精简会员中心首屏：移除占空间的“当前套餐”大状态卡，把当前档位和今日剩余次数收成一条轻量状态条；套餐区不再展示免费版大卡，直接露出 Plus / Pro 两个付费套餐，避免用户一进来先看到重复的免费套餐说明。支付链、后端 `/api/me` 读取、Plus 升 Pro 规则、加油包规则和聊天滚动链均不变。
- `ChatScreen.kt` 优化后端历史模式下的聊天页开机首帧：首次组合不再同步读取并解析本地 30 轮聊天窗口，页面先用空本地快照进入首屏，随后异步并行读取本地快照和远端 `/api/session/snapshot`。远端成功时仍以后端历史为主，并合并本地失败消息 / 待恢复用户尾巴；远端失败时异步回退到本地历史。滚动链仍保持正向 `LazyColumn + Arrangement.Bottom + lastIndex`，不恢复反向列表、overlay、小分割或 raw delta。
- `ChatScreen.kt` / `MembershipCenterSheet.kt` / `SessionApi.kt` 接入首版会员中心：右上角绿色叶片打开黑白简洁的底部面板，面板读取 `/api/me` 的有效档位、今日剩余、升级补偿、加油包和到期时间，展示 Free / Plus / Pro 的真实后端权益、Plus 升 Pro 补偿规则、加油包规则和消耗顺序。当前支付链尚未接入，开通 / 升级 / 购买按钮只在会员面板内提示“支付暂未接入”，不调用后端订单接口，避免绕过真实支付。左侧菜单仍保持空入口，本次不改聊天滚动链、输入框或额度发送 gate。
- `ImagePreviewPager.kt` 收紧全屏图片预览的单击关闭判定：外层快速 tap detector 现在会记录首帧是否多指、过程中是否新增第二根手指，以及同一手势内是否出现多个相关 pointer；只在单指、未移动、短按释放时关闭预览。一旦本轮出现多指，会等所有手指都抬起后才重新允许下一次单击关闭，避免双指缩放时第一根手指先抬起后第二根手指被重新当成单击。输入框图片预览和聊天区用户图片预览继续共用 Telephoto + `HorizontalPager`；旧 `ImagePreviewGesture.kt` 手写链未恢复。

## 2026-05-02

- Android 构建链升级到 Kotlin Android / Compose Compiler Gradle plugin `2.1.21`，并按 Kotlin 2.x 官方迁移方式应用 `org.jetbrains.kotlin.plugin.compose`、删除旧 `composeOptions.kotlinCompilerExtensionVersion`；图片全屏预览依赖升级到 Telephoto `zoomable-image-coil:0.19.0`。输入框图片预览和聊天区用户图片预览改为共用新增 `ImagePreviewPager.kt`，内部使用 Telephoto `ZoomableAsyncImage` + `HorizontalPager`，让缩放、拖动、边界阻尼和 pager handoff 交给成熟库处理；旧 `ImagePreviewGesture.kt` 及两个入口里的手写全屏 zoom page 已删除，缩略图轻量解码函数继续保留。本次不改图片选择、压缩上传、发送链、聊天滚动链或 96dp 工作线。
- `ImagePreviewPager.kt` 把关闭预览从外层整屏 `clickable` 改到 Telephoto `ZoomableAsyncImage(onClick=...)`，修正 zoomable 组件消费 tap 后“点图片不退出预览”的问题；同时把全屏预览 22dp 留白改为 Telephoto `contentPadding`，让库内部按真实内容区域做边界判断。输入框图片预览和聊天区用户图片预览继续共用同一个组件。
- `ImagePreviewPager.kt` 在全屏预览外层增加 Initial pass 快速单击检测，避免 Telephoto `onClick` 等待双击判定窗口导致点图退出迟钝；该检测不消费事件，双指缩放、拖动查看和左右滑仍交给 Telephoto / Pager。输入框图片预览和聊天区用户图片预览继续共用同一个组件。
- `ChatScreen.kt` 收口带图发送前的重复压缩：选图 / 拍照进输入框时已经压缩并复制到 App 私有 `composer_images` 的 JPEG，点发送时若确认仍是这份私有 JPEG 且大小 `<=1MB`，直接复用其字节上传；只有旧缓存、外部 URI、非 JPEG 或超限图片才重新走 `ImageUploader.compressImage(...)`。图片数量、压缩目标、上传接口、后端上下文和聊天滚动链均不变。
- `ChatComposerPanel.kt` 将带图输入框 placeholder 从“描述作物、部位或症状，会更准”收口为“描述作物、部位或症状会更准”，去掉“症状”和“会更准”之间的逗号，让底部提示更顺。debug-only UI 文案预览面板复用同一常量自动同步；同步更新 UI 文案台账。
- `ChatScreen.kt` 将 debug-only UI 文案预览面板里的“最多4张图片”拆成“图片数量浮层”和“已满附件面板”两个样式项，避免黑色浮层和底部附件卡片在同一个预览里叠加，误以为真实运行时会固定同时显示。实际图片数量限制、浮层文案和附件面板逻辑均不变。
- `ChatScreen.kt` 为聊天区用户图片缩略图增加 12MB LRU 内存缓存：同一张已发送图片滚出屏幕后再滚回来时优先复用已解码缩略图，减少重复读私有文件 / 拉远端图 / Bitmap 解码导致的卡顿。该缓存只影响聊天列表缩略图展示，不改变图片压缩上传、全屏预览、30 轮 UI 窗口或聊天滚动链。
- `ChatScreen.kt` 将 30 轮 UI 窗口裁剪后的运行时清理从失败状态扩展到选区 bounds 和消息内容 bounds 缓存，避免长期单对话使用时旧 messageId 对应的测量缓存继续积累。只清理已被消息窗口裁掉的旧 id，不改变消息裁剪策略、滚动链或文案预览。
- `ChatScreen.kt` 在本地聊天窗口落盘后清理 App 私有 `composer_images` 里的孤儿 JPEG：只保留当前 30 轮 UI 窗口仍引用的本地图和输入框待发送图，已被窗口裁剪且不再被消息引用的旧本地图会后台删除，避免长期单对话使用时旧图继续占手机空间。远端图片 URL 不受影响，后端 30 天归档仍以 URL 恢复 UI。
- `ChatComposerPanel.kt` 将带图输入框 placeholder 从“补充作物、部位或症状会更准”调整为“描述作物、部位或症状，会更准”，避免“补充作物”读起来生硬；debug-only UI 文案预览面板复用同一常量自动同步。
- `ChatComposerPanel.kt` 将 `+` 附件底部拍摄建议从“建议拍清病斑、整棵植株、叶背或果实”调整为“建议拍清病斑、异常部位、叶背或果实”，避免要求用户必须拍整株；同步更新 UI 文案台账。预览面板复用同一常量自动同步。
- `ChatScreen.kt` 重新整理 debug-only UI 文案预览面板：上半部分列表不再用“主界面标题 / 网络 / 图片格式”这类分类名当主标题，而是直接显示用户实际会看到的文案，并把触发场景放进副标题；补齐附件面板里的“相机 / 照片 / 单次最多4张照片 / 建议拍清...”等内部文案条目。`回复未完成 / 重试` 与 `发送失败 / 重发` 抽成共享常量，正式尾部和预览面板复用同一口径。同步在文案台账里明确：预览面板不会自动扫描 Compose 文案，新增用户可见文案时必须同步维护清单。
- `ChatScreen.kt` / `ChatComposerPanel.kt` 收口 4 张图片已满时的附件面板文案：debug-only UI 文案预览面板不再单列“附件已满”，该状态合并到“图片数量”样式预览里；实际 `+` 附件卡片在已满状态只保留顶部“最多4张图片”，不再重复显示底部“单次最多4张照片 / 拍摄建议”。未满状态的相机 / 照片入口和拍摄建议保持不变，不改图片选择、压缩上传、发送链、预览手势、聊天滚动链或 96dp 工作线。
- `ChatScreen.kt` / `ChatComposerPanel.kt` / `ImagePreviewGesture.kt` 删除“图片处理中，请稍候”和“部分图片无法读取，已跳过”两类主界面提示及其 debug 文案预览项：带图发送等待期间重复点图片入口 / 删除图片改为静默忽略，多选导入时部分失败只保留可读取图片；全失败走图片读取失败兜底提示。输入框图片预览和聊天区用户图片预览的全屏页改用共享手势接管：未放大时单指横滑交给 `HorizontalPager`，双指缩放或图片已放大后才由图片本身接管拖动，修正全屏预览左右滑动被缩放手势吃掉的问题。只改图片预览手势、低频提示和文案预览面板，不改图片选择上限、压缩上传链、发送链、滚动链或 96dp 工作线。
- `ChatScreen.kt` / `ChatComposerPanel.kt` / `ChatComposerCoordinator.kt` 重新扫描主聊天页可见提示词，并把 debug-only UI 文案预览面板补齐到当前真实主界面文案：新增带图输入框 placeholder、附件底部面板、附件已满、图片读取失败、相机异常和图片处理中的样式预览；标题、欢迎态、placeholder、附件面板、输入超长、网络 / 限流 / 中断和图片异常提示改为复用真实常量，避免预览面板和运行时文案漂移。带图发送等待期间的操作提示从旧的上传中说法收口为“图片处理中，请稍候”。继续不把上传服务未配置 / 响应格式错误 / 网络超时等技术型上传细节放回主界面预览；真实上传失败仍落在消息尾部“发送失败 · 点击重发”。
- `ChatScreen.kt` / `ChatComposerPanel.kt` 为输入框图片和聊天区用户图片的全屏预览接入横向 Pager：点击任意缩略图会从对应页打开，可左右滑动查看同组最多 4 张图片，并保留原有双指缩放 / 拖动查看能力和右上关闭按钮。预览顶部增加轻量页码提示。本次只改图片全屏预览交互，不改图片选择、压缩上传、消息发送、聊天滚动链或 96dp 工作线；已检查 baseline profile runbook，不需要更新当前首屏 / 滚动 / 输入框预热脚本。
- `ChatScreen.kt` / `ChatComposerPanel.kt` 收口图片相关用户提示：debug-only UI 文案预览面板的图片项只保留“图片格式 / 图片超限 / 图片数量”，删除“图片上传”类别以及“未配置上传服务 / 上传失败：响应格式错误 / 上传异常”等技术型预览提示；“图片数量”分类只预览黑色浮层“最多4张图片”。输入框已有 4 张图片时，`+` 附件卡片顶部和相机 / 照片点击都统一提示“最多4张图片”。上传失败仍保留用户消息尾部“发送失败 · 点击重发”，不再额外弹“图片上传”类浮层；内部上传错误字符串仍留在上传工具链里方便排障。按代理审查补齐两个边界：异步导入完成后会按最新剩余槽位二次截断并清理溢出私有副本，防止连续快速选图突破 4 张；失败 assistant 重试时如果用户消息只有本地图片副本、没有远端 URL，会先重新上传本地图再请求模型。相机待回调 URI 改为 `rememberSaveable` 字符串，降低外部相机期间 Activity 重建导致拍照结果丢失的概率。本次不改图片压缩序列、Photo Picker 剩余槽位、上传接口、图片预览、消息滚动链或 96dp 工作线。
- `ChatScreen.kt` / `ChatComposerPanel.kt` 修正图片离线失败态和重启恢复：选图 / 拍照后先把图片压缩并复制到 App 私有 `files/composer_images` 稳定副本，后续输入框预览、用户消息本地预览、上传重试都读这份副本，避免系统 Photo Picker / 相机临时 URI 在 App 退出后失效导致图片只剩占位。聊天区消息展示改为优先远端 `imageUrls`、再用本地 `imageUris`，避免已上传成功的历史图被过期本地 URI 抢先渲染。输入框预览和聊天区图片全屏预览都支持双指缩放 / 拖动查看。带图上传等待态只做一次底部定位，不再保留 streaming 发送锚点；静态列表只要用户开始拖动，就关闭启动贴底补偿，降低无服务器 / 上传失败态下上滑被吸回底部的风险。本次未改图片选择上限、压缩序列、后端上下文或正向列表滚动主链；已检查 baseline profile runbook，不需要更新当前预热脚本。

## 2026-05-01

- `ChatScreen.kt` / `ChatComposerPanel.kt` 修正图片发送体感：带图点击发送后，先把本地 `imageUris` 用户消息插入聊天区并清空输入框，再后台压缩上传；上传成功后用同一条用户消息补远端 `imageUrls` 并继续请求模型，上传失败则保留本地图片消息并标记“发送失败”供重试，避免上传慢 / 失败时看起来像点发送没反应。同时把输入框内待发送缩略图继续放大到 100dp，并把 composer 图片 reserve 提到 116dp、文本区保底提到 92dp，让图片靠输入框自动撑高而不是压缩文字输入区。只改图片发送前端 staging、输入框图片尺寸和项目记忆，不改图片选择上限、压缩序列、后端上下文或聊天滚动链。
- `ChatScreen.kt` 补齐发送后用户消息图片体验：聊天区用户图片缩略图从 86dp 放大到 112dp，仍按最多 4 张、两列网格展示；点击任意已发送图片可打开全屏预览，本地 `imageUris` 和远端 `imageUrls` 都走同一预览入口。只改用户消息图片展示和预览，不改图片选择、压缩上传、后端上下文、用户文字气泡或聊天滚动链。
- `ChatComposerPanel.kt` 优化输入框内图片预览：缩略图从 60dp 放大到 76dp，超过一屏宽度时可横向滑动，点击缩略图可进入全屏预览；带图时输入框壳体会按图片预览高度整体上抬，并给文本输入区保留正常可输入高度，避免图片把文字区压成一行。图片仍作为同一条待发送内容随文字发送到上方聊天区，但不塞进 `BasicTextField` 光标流；图片选择上限、压缩上传链、用户消息展示和聊天滚动链均未改动。已检查 baseline profile runbook，本次不改变当前首屏 / 滚动 / 首次点输入框预热脚本。
- `ImageUploader.kt` 统一图片压缩为工程稳定版：上传前全部输出 JPEG，先走 `1024@Q85 -> 1024@Q80 -> 896@Q80 -> 896@Q70 -> 768@Q70 -> 640@Q60 -> 512@Q60`，仍超过 1MB 的极端可解码图片继续保持 Q60 等比缩小直到 <=1MB；全程不裁剪、不拉伸，解码失败走图片读取失败兜底提示。同时把解码中间态长边从 4096px 收到约 2048px，降低超大原图在低端机上的内存压力。只改 Android 上传前压缩链和项目记忆，不改图片选择 UI、上传接口、后端归档、模型上下文图片保留规则或聊天滚动链。
- `ChatComposerPanel.kt` 优化 `+` 附件底部卡片提示语：把提示拆成两行，第一行深色半粗“单次最多4张照片”，第二行浅灰“建议拍清病斑、整棵植株、叶背或果实”，避免上一版半粗和灰字挤在同一行导致“叶背”被拆开。只改提示文案和局部字重 / 行距，不改底部弹卡片布局、相机 / 照片入口、图片选择上限、压缩上传链或聊天滚动链。
- `ChatScreen.kt` 将照片入口从旧 `GetMultipleContents()` 换成 Android 官方 `PickVisualMedia` / `PickMultipleVisualMedia` 组合，并按当前输入框剩余槽位分别请求 1、2、3、4 张上限；支持官方 Photo Picker 的系统会尽量在选图阶段限制数量，不支持 / 兜底选择器仍保留 App 回来后的 4 张截断提示和后端硬限制。只改照片 picker launcher，不改底部附件卡片布局、相机入口、图片压缩上传链、输入框预览或聊天滚动链。
- `ChatComposerPanel.kt` 继续微调 `+` 附件底部卡片里的相机图标：按用户给的第 5 个参考图把顶部小凸起压低、放平、略加宽，并把相机轮廓整体铺满到更接近照片图标的视觉尺寸，避免真机看起来比“照片”入口小一圈。只改相机图标 Canvas 几何，不改照片图标、底部弹卡片布局、图片上传链、输入框或聊天滚动链。
- `ChatScreen.kt` 为普通输入框补本机草稿持久化：未发送文字会在输入变化后轻量写入 `chat_ui_cache`，切后台 / 锁屏时同步落盘；再次进入同一 `chatScopeId` 会回填，发送成功或清空运行时会清掉。该草稿只保存文字，不保存待上传图片 URI，避免临时图片权限 / 文件生命周期污染聊天 UI；滚动链、图片上传链和后端归档不变。同次，`server-go` 会员有效等级改为按 `tier_expire_at` 实时计算，Plus / Pro 到期后接口和额度消费按 Free 处理，避免过期 tier 继续享受付费额度或购买加油包。
- 新增项目级 `opencode.json` 和 `docs/opencode-codex-bridge.md`，让 OpenCode 在 `D:\wuhao` 项目中显式加载根 `AGENTS.md`、OpenCode 接手提示词以及 `docs/project-state` 四份项目记忆文件。项目配置不绑定固定模型或 provider，后续可继续在 OpenCode 全局配置 / 客户端下拉中切换 MiMo、Kimi、Claude、OpenRouter 等模型；同时把编辑权限设为需确认，并要求 OpenCode 按“先读真相、排查旧方案、最小改动、改后自查、同步记忆、提交并推送”的仓库流程工作。只改开发工具接入配置和记忆入口，不改 Android / Go 业务代码。
- `ChatComposerPanel.kt` 微调 `+` 底部附件卡片里的相机图标：旧图标顶部用斜线拼出相机轮廓，真机观感偏笨重；按用户从候选里选定的第 5 个方向，改为带顶部小凸起的一笔相机轮廓，并把相机 / 照片两个入口图标的视觉高度拉齐。只改相机图标 Canvas 绘制，不改底部弹卡片结构、照片入口、图片上传 / 压缩 / 发送链、聊天滚动链或 96dp 工作线。
- `ChatScreen.kt` / `ChatComposerPanel.kt` 将 `+` 附件入口从输入框上方小面板改为底部弹出的白色圆角卡片：页面背景加轻量遮罩，卡片里只保留“相机 / 照片”两个大入口和一行农业拍摄提示，点击外部可关闭。原输入框内部 `ComposerAttachmentMenu` 已退出，不再和新 bottom sheet 并存；相机 / 相册 launcher、图片压缩上传、输入框缩略图、最多 4 张、上传中锁定等功能链路未改，聊天滚动链和 96dp 工作线不动。
- `ChatScreen.kt` / `ChatComposerPanel.kt` / `ImageUploader.kt` 接入 `+` 入口的首版图片输入：加号面板只放“相机 / 照片”两项和一行农业拍摄提示，选图后图片进入输入框缩略图预览并按 1-4 编号；发送支持图片-only / 图文混合，先后台压缩到单张 <=1MB，再按用户选择顺序上传并把 URL 传给 `SessionApi.StreamOptions.images`。上传中会锁住本次输入和附件操作，避免用户继续改内容后被误清；本地消息用 `imageUris` 预览，远端 hydrate / 重装后用 `imageUrls` 兜底显示。图片预览只影响 composer 内部和用户消息内容，不进入聊天列表 bottom reserve / 96dp 工作线；baseline profile 脚本本次不更新，因为相机 / 相册是外部系统 picker 路径，不在当前聊天首屏 / 滚动 / 输入框关键预热脚本里。
- `ChatScreen.kt` 小范围调整顶部 chrome：右上角旧菱形图标替换为圆形黑底叶子 App 图标，作为后续会员中心外露入口占位；左侧菜单图标略放大，标题“农技千查”在真机反馈后回落到 17sp，会员入口改为一张预合成的 `ic_membership_leaf.png`（黑圆 + 绿叶），代码里只按单一尺寸显示且右上图标再轻微收小 1dp，避免直接引用 Android adaptive launcher 图标导致启动崩溃，也避免外层圆 / 内层叶子两套尺寸反复互相打架；顶部遮挡帘子高度略收短。只改顶栏视觉和旧菱形函数清理，不改聊天滚动链、输入框、debug 文案预览或会员业务逻辑。
- `ChatScreen.kt` 将 debug-only UI 文案预览面板入口从右上角菱形按钮挪到顶部标题“农技千查”的 debug 点击上；正式 release 仍不可见，右上角按钮释放出来，预留给后续会员中心入口。只改调试入口接线，不改文案预览内容、主界面右上角图标样式、滚动链或输入框。

## 2026-04-29

- `ChatScreen.kt` / `SessionApi.kt` 按极端条件审查补低风险健壮性：无网络连续点同一段输入时复用已有失败用户消息，不再刷出多条相同“发送失败”气泡；后端 streaming 进程被杀后若远端 snapshot 3 次追不回答案，会在原用户消息下补一个 `回复未完成 · 点击重试` 的 assistant 失败入口；切后台 / 锁屏时主动清掉消息复制菜单和输入框选择菜单；`SessionApi` 不再在 `onResponse` 开头清空 active SSE call，而是等响应读循环退出后再清，方便 reset / cancel 抓到正在读的流。滚动链、渲染链、输入框几何均未改动。
- `ChatScreen.kt` / `ChatComposerPanel.kt` 为日额度耗尽补前端当天锁：后端返回 `quota` 后，当前本地会话当天记住额度已用完；输入有文字时发送键显示灰态但仍可点击出“今日额度已用完，请明天再试”提示，不再真正发送；用户点击失败消息尾部的重发 / 重试也只弹同一额度提示，不删除原失败状态、不插入新的 assistant 占位。该锁只服务前端体验，后端额度仍是唯一真相。
- `ChatScreen.kt` / `ChatStreamingRenderer.kt` 只调整 AI 免责声明样式、不改免责声明文案：原 `本回答由AI生成，内容仅供参考。` 保持在回答尾部靠左显示，去掉斜体、背景和边框，改成 14sp 小灰字；streaming 阶段继续用同一个结构透明占位，settled 后显示真实文字，避免尾部收口重新引入高度跳动。debug 文案预览同步展示同款样式。
- `AGENTS.md` / `docs/runbooks/android-ui-copy-inventory.md` 记录 debug-only 文案样式预览面板的复用规则：以后若删除后还要重建，继续采用“上半部分可点条目 + 下半部分正式样式预览”的模式，不再只做纯文字清单；同时把失败 / 重试尾部的当前黑底胶囊口径同步进主规则，避免后续窗口读到旧 `未发送 / 重发` 口径。
- `ChatScreen.kt` 小范围调整消息异常尾部样式：debug 文案预览里的分类名从“AI异常 / 用户异常”改成“回复中断 / 发送失败”；正式消息尾部的 `回复未完成 · 点击重试`、`发送失败 · 点击重发` 改为黑底小胶囊，整颗可点，并把胶囊高度、左右留白和字号略放大，降低裸文字工具感和“小短按钮”观感。只改低频异常态样式，不改失败重试逻辑、发送链、滚动链或输入框。
- `QwenClient.kt` / `ChatScreen.kt` 删除“有图片时必须带文字描述”的客户端拦截和 debug 文案预览项。后续图片问诊允许文字、图片、图文混合三种入口；若用户只上传图片，客户端会在发给模型的内部文本里轻量标注“本轮只上传图片、未补充文字”，让模型先按图片可见信息给参考判断并追问必要信息。该内部说明不作为用户可见提示，不改变最多 4 张图片和图片格式 / 大小限制。
- 新增 `docs/runbooks/android-edge-case-regression.md`，把用户关心但难以口述的极端条件拆成可执行手测项：无网络发送、生成中断网、弱网 / 高延迟、限流、额度耗尽、streaming 中切后台、键盘打开切后台、锁屏、上滑 / 回底 / finalize、超长输入、图片异常、清数据 / 重装、debug 文案预览入口等。该清单不改变 App 行为，用于后续真机回归和定位问题。
- `ChatScreen.kt` 临时新增 debug-only UI 文案样式预览面板：debug 包中点击右上角菱形按钮会弹出主界面、消息状态、额度 / 网络 / 图片等用户可能看到的条目；点任意条目后，下方用对应真实 UI 样式预览黑色浮层、消息尾部、免责声明、复制菜单等效果，点空白关闭。该入口由 `BuildConfig.DEBUG` 限制，正式 release 包不可见；核对完成后应删除，避免长期保留调试 UI。
- 新增 `docs/runbooks/android-ui-copy-inventory.md`，把 Android 端用户可能看到的主界面、消息状态、输入框菜单、额度 / 网络 / 中断、图片相关隐藏态文案做成台账，方便后续逐条核对，不再靠真机撞异常才知道文案长什么样。该文档只服务产品文案核对，不改变 App 行为。
- `ChatScreen.kt` 将额度不足的用户提示从“当前次数不足，请稍后再试”统一为“今日额度已用完，请明天再试”，包括常规中断浮层和隐藏的 interrupted retry 兜底路径，避免“稍后再试”在日额度耗尽场景下误导用户；只改文案，不改会员 / 额度判断逻辑。
- `ChatScreen.kt` 小范围收口主界面失败态文案：用户消息发送失败时，尾部状态从“未发送 / 重发”改为“发送失败 / 重发”，让含义更直接；只改可见文案，不改发送重试逻辑、滚动链、输入框或渲染结构。
- `ChatScreen.kt` / `ChatComposerCoordinator.kt` 按输入框回缩残影会诊先落两刀低风险优化：所有主动收键盘路径统一只调用 `focusManager.clearFocus(force = true)`，不再同帧叠加 `keyboardController?.hide()`，避免部分 IME 在双触发下出现“先收又弹 / 慢一拍”的回缩感；同时删除已经没有显示入口的 composer collapse overlay prewarm snapshot 协程，避免 focus / IME 变化时仍无意义地延迟两帧抓取旧 bounds。发送收口旧高度锁已保持为 0，滚动链、96dp 工作线、SideEffect 同帧锚定、composer 外观尺寸和按钮样式均未改动。
- `ChatScreen.kt` / `ChatComposerPanel.kt` 对底部输入框做外观微调：输入框外壳高度略增、四角圆角略收、左右外边距加大；`+` 按钮改为裸黑加号，发送键保持黑色圆底白色箭头，并按真机反馈把两个按钮从偏大的 48dp/50dp 收到小屏 34dp、常规屏 36dp，避免按钮视觉压过输入框。此次只调整 composer 外观参数，不改正向列表滚动链、96dp 工作线、SideEffect 同帧锚定、两阶段 finalize 或输入框 / IME 与列表解耦规则。

## 2026-04-28

- `server-go` 为历史轮次补入模型可用时间 / 地点上下文：`SessionRound` 新增 `created_at / region / region_source / region_reliability`，成功完成轮次写入 A 层滑窗和 30 天归档时使用后端服务器时间与当前轮地点；`/api/session/snapshot` 返回的 A 层 / UI 归档轮次也会携带这些字段。主对话本来已经每轮注入当前时间 + 用户地点，本次新增的是历史轮次进入模型上下文时附加“历史轮次时间：...（Asia/Shanghai）”和“历史轮次地点：...；地点可信度：...”前缀，让模型能判断这次问诊和上一轮隔了多久、当时大概在哪里。前端 `ARound` 只做兼容解析，不把每条消息时间戳 / 地点显示给用户。
- 新增 `docs/adr/ADR-0003-forward-chat-ui-stable-main-chain.md`，把当前已稳定的正向聊天 UI 主链正式沉淀：单一正向 `LazyColumn` 主人、96dp 工作线、小球锚点依赖稳定折叠 reserve + 发送锁 + 底部锚定、`SideEffect` 同帧锚定、两阶段 finalize、composer/IME 与列表解耦，以及禁止恢复反向列表 / overlay / active-zone / 小分割 / raw delta 的原因。
- 历史记录：`ChatScreen.kt` / `ChatStreamingRenderer.kt` 对 streaming 渲染做小范围体验收口：把 reveal batch 从 4 个 token / 40ms 收到 2 个 token / 28ms，让中文通常 1 到 2 个字一拍，减少“几个字一坨蹦出来”的呆感，同时避免每个汉字都单独重组导致长回复压力过大；聊天列表左右 padding 小幅加大，让不同宽度手机上正文边缘有更多呼吸感；当时 renderer 内增加的是标准 Markdown 表格兜底降级，会把 `| header |` 这类表格转成普通项目行文本，避免模型偶发输出表格时撑乱布局。该表格降级口径已在 2026-06-17 被“横向可滑轻量表格 + 复制按钮”替代。滚动链、96dp 工作线、SideEffect 同帧锚定、两阶段 finalize 均未改动。
- `docs/runbooks/chat-ui-regression.md` 从 4 月 19 日旧热点文档更新为当前正向列表基线回归入口：补齐禁改旧链、首屏 / streaming / 用户滚动 / IME / Markdown 表格 / clean-state 回归项，以及后续模拟器跨机型矩阵（小屏、常规屏、大屏、字体缩放、导航模式、Android 版本、输入法）。外部会诊默认对象也同步改为小米 / MiMo。
- `server-go` 新增 `session_round_archive` 归档表：成功完成的问答轮次会在 `Store.AppendSessionRoundComplete(...)` 同事务写入归档，按 30 天滚动保留；`/api/session/snapshot` 的 `a_rounds_for_ui` 优先返回 30 天内最近 30 轮归档，A/B/C 主上下文仍保持原来的短窗口和摘要，不把归档内容每轮喂给模型。
- 同步明确“清数据”和“换机恢复”的边界：本地 UI 缓存 / 草稿 / 旧视口不允许通过 Android 备份恢复；但如果用户有稳定账号 / 后端身份，后端返回最近 30 轮业务聊天记录属于账号级恢复，不是 UI 回退。匿名本机 UUID 清数据后仍是 clean-state，新身份不会拿到旧记录。
- 基础设施首版采购口径从“SAE + PolarDB”调整为“SAE + RDS MySQL 优先”。RDS MySQL 更符合当前个人创业、无专职运维、成本敏感阶段；PolarDB 暂作为后续高并发 / 更高规格升级选项。`infra-readiness.md`、`pending-decisions.md` 和 `open-risks.md` 已同步改口径，避免采购时沿用旧默认。
- 项目记忆补充了后端长期资产和 C+ 方向：当前已先落 30 天原始问诊归档，后续再评估把 C 层升级成 `C+ = 长期摘要 + 用户农业画像 + 用户农业档案`。当前没有改变 A/B/C prompt 或主模型调用链。
- 用户真机反馈当前正向列表滚动链“确实很稳”。项目记忆已把 `SideEffect` 同帧底部锚定、物理底部恢复 AutoFollow、96dp 工作线贴底、禁止恢复反向列表 / 小分割 / overlay / raw delta 等规则继续固化为稳定基线；`open-risks.md` 中对应风险从“主链待验证”降为“核心已稳定，继续观察边角场景”。

## 2026-04-27

- `ChatScreen.kt` 新增 debug-only 启动诊断日志 `ChatStartup`，用于复查“清数据后 UI 是否从旧状态恢复”的老问题。启动时记录本地 `chat_ui_cache` 读取到的消息数、failed state 数、streaming draft 是否存在、是否启用后端 hydrate；后端 `SessionApi.getSnapshot()` 返回后记录 remote / hydrated 消息数以及是否应用到当前列表。日志只在 `BuildConfig.DEBUG` 输出，且只打印 `chatScopeId` 后 8 位，不改变本地 snapshot、后端 hydrate、滚动链或 UI 行为。
- `AndroidManifest.xml` 继续加固 clean-state / 清数据后不回灌旧 UI：在已有 `android:allowBackup="false"` 基础上，新增 `dataExtractionRules` 和 `fullBackupContent`，显式排除 cloud backup、device transfer、shared preferences、files、databases 和 external 数据。目的不是改变聊天 UI，而是防止 `chat_ui_cache`、`app_ids`、旧 UI metrics / snapshot 这类本地状态在清数据、重装或设备迁移后被系统 / 厂商备份链恢复回来。
- `ChatScreen.kt` 对发送路径再收一刀输入框回缩时序：发送清空输入后只调用 `focusManager.clearFocus(force = true)`，不再在同一帧额外调用 `keyboardController?.hide()`。这只影响点击发送后的键盘回缩，目的是避免部分 IME 在 `clearFocus + hide` 双触发下多抖一拍；点空白收键盘、拖列表收键盘、生命周期暂停收键盘等其他入口保持不变，滚动链 / 96dp 工作线 / SideEffect 同帧锚定不动。
- `ChatComposerCoordinator.kt` 按会诊核验后的最小安全刀取消发送收口旧高度锁：`prepareComposerCollapse(...)` 现在不再把发送前的输入内容高度 / chrome 高度写进 `composerSettlingMinHeightPx` 和 `composerSettlingChromeHeightPx`，也不再 suppress cursor。发送时清空输入后 composer 可直接回到空态高度，避免“文字已经没了但旧高度还撑几帧”的残影；本次不删 overlay 死链、不改 `clearFocus + keyboardController.hide()` 时序、不动滚动链 / 96dp 工作线。
- `ChatScrollCoordinator.kt` 按小米 / MiMo 会诊修正手动下滑回底后的 AutoFollow 恢复：`UserBrowsing` 中如果正向列表已经到物理底部（`canScrollForward == false`）、手指已抬起且列表停止，会先请求一次 `lastIndex + FORWARD_LIST_BOTTOM_SCROLL_OFFSET` 底部锚点，再切回 `AutoFollow`。旧的连续 2 帧工作线命中仍保留为兜底；这避免 streaming 持续吐字反复打断工作线容差，导致用户明明滑到底但过一会儿才继续跟随。不改 SideEffect 同帧锚定、不恢复 `scrollBy` / overlay / 小分割。
- `ChatScreen.kt` 按小米 / MiMo 会诊继续补开机贴底完成条件：首屏历史贴底现在必须等底部固定 composer 宿主的稳定实测高度到位后才允许把 `initialBottomSnapDone` 关门；同时增加一次仅限冷启动历史态、用户未开始新对话且未触碰滚动的 post-snap 修正，stable bottom reserve 晚一拍变大时会再做一次非动画回底，确保 96dp 工作线以下空白完整露出。pending finalize 需要恢复底部锚点时也会等 bottom reserve ready 后再请求正向底部锚点。这刀不恢复 `SubcomposeLayout`，不让 IME 动画进入列表 reserve。
- 根 `AGENTS.md`、`app/AGENTS.md`、`server-go/AGENTS.md` 同步调整外部会诊默认口径：本项目后续如需外部会诊，默认优先整理成发给小米 / MiMo 的自包含短稿，不再默认 Claude，也继续不建议 Gemini。因为小米 / MiMo 免费版通常不能直接读取仓库，外发内容必须直接贴当前真实代码结构、关键片段、状态名、调用顺序、已排除方案和限制条件；收到建议后仍由 Codex 对照仓库核验再落地。
- `ChatScreen.kt` 按输入框 IME 丝滑度会诊先落 P0 保守刀：composer 从旧 `SubcomposeLayout` 的列表同测量链里拆出，改成页面 `Box` 底部固定兄弟层，列表继续铺满消息区并只吃稳定折叠态 composer reserve + 96dp 工作线 gap。composer 自己仍保留 `imePadding()`，根容器不吃 IME padding，所以键盘弹起 / 回缩只移动输入框，不再让列表和工作线每帧跟着 remeasure。这刀暂不删除 `ChatComposerCollapseOverlay` / prewarm，避免一次性改动发送收口；若真机仍有残影，再围绕 overlay / focus-hide 时序单独处理。
- `ChatScrollCoordinator.kt` 将 `UserBrowsing -> AutoFollow` 的手动回底确认从连续 5 帧收短到 2 帧。原因是真机反馈“手动往下滑回到底部后，自动跟随不够利索”，而 streaming 正在继续长高时 5 帧确认容易被新内容打断；2 帧仍保留防瞬态误吸回缓冲，但能更快恢复 AutoFollow。不改 SideEffect 同帧锚定、不改 renderer、不恢复 `scrollBy` / overlay / 小分割。
- 用户真机初测确认 `901df5a` 的 SideEffect 同帧锚定已经压住正向列表 streaming 工作线下方“下一行冒头闪”：内容变更触发重组后，`SideEffect` 在 apply changes 后、layout 前请求最新消息底部锚点，使 soft-wrap 增高和列表底部 clamp 在同一帧内完成。当前结论是这条方案有效，不需要恢复小分割、overlay、raw delta / `scrollBy` 或旧物理行 renderer；后续只继续观察长回复 / 不同机型是否复现。
- `ChatScreen.kt` 按会诊结论把正向列表 AutoFollow 的 streaming 底部锚定从“内容更新后同回调再补一次 `requestScrollToItem(...)`”改成顶层 `SideEffect` 同帧锚定：`streamingMessageContent` 触发重组后，SideEffect 在 apply changes 后、layout 前请求最新消息 `lastIndex + FORWARD_LIST_BOTTOM_SCROLL_OFFSET`，让 soft-wrap 新行增高和底部锚定尽量落在同一帧，专门压工作线下方下一行冒头闪。本次不改 renderer、不恢复小分割、不恢复 overlay / active-zone，也不使用 `scrollBy` / `dispatchRawDelta`。
- `ChatScreen.kt` 将统一工作线视觉 gap 从 `80.dp` 抬到 `96.dp`，小球首发锚点、streaming 正文底边、开机历史态和完成态尾部都继续围绕这条工作线，工作线以下空白保持可见，用于免责声明 / 极端说明 / 底部呼吸区。同次，启动显示门不再让本地已有消息 / 首次欢迎空态硬等 hydrate barrier，减少开机白屏时间；AutoFollow reveal 继续围绕正向底部锚点压工作线下方下一行冒头闪，后续已从同回调补锚收敛为顶层 `SideEffect` 同帧锚定。没有恢复小分割、overlay / active-zone、`scrollBy` / `dispatchRawDelta` 或旧 reverse-list 链。
- `ChatScreen.kt` 随后收紧静态到底判定：开机历史态 / 完成态不再只看文本 bottom 是否命中 96dp 工作线，还必须满足正向列表 `canScrollForward == false`。这样只有工作线以下完整 96dp 空白已经真正露出来时，才认为已贴底，避免“还能继续往上扒出底部空白”的假贴底。
- `ChatScreen.kt` 对照反向列表贴底稳定时期的记录，把首屏历史贴底恢复成“多帧确认后再关门”：`startupLayoutReady` 后最多重试 6 帧 `scrollToBottom(false)`，只有文本 bottom 命中 96dp 工作线且 `canScrollForward == false` 同时成立，才设置 `initialBottomSnapDone = true`。这专门修“一次 scroll 后还没真正露出工作线以下空白，却已经关门”的假贴底。
- `ChatScreen.kt` 继续恢复当时贴底成功的 realtime composer 几何口径：开机历史态 / 完成态在输入为空、无 focus、IME 收起、composer 非 settling、非发送锁时，底部 reserve 优先使用同拍实测 `measuredComposerHeightPx + 96dp`，不再只依赖启动估值或旧观察值。这样真机输入框真实高度大于估值时，工作线以下空白也能完整露出来。

## 2026-04-26

- `ChatRecyclerViewHost.kt` / `ChatScreen.kt` / `ChatScrollCoordinator.kt` 按用户最终拍板把聊天主链切回正向 `LazyColumn`：`messages` 仍按 oldest -> newest 直接显示，短内容不满一屏时由 `verticalArrangement = Arrangement.Bottom` 贴底，回到底部 / AutoFollow 改用最新消息 `lastIndex + FORWARD_LIST_BOTTOM_SCROLL_OFFSET`，并删除回底链里旧 `scrollBy` bottom-align 精修。发送起步仍保留 `sendStartAnchorActive` 与 bottom padding lock，小球 / streaming 正文 / 完成态尾部继续落在 80dp 工作线；列表 bottom padding 只扣除消息 item 外层 8dp padding 来补偿正向 item 外壳，不改变工作线本身。streaming reveal 提交前会先请求底部锚点，专门压正向列表下一行从工作线下方冒头的一帧；高频请求用 generation 守护，避免旧的一帧后取消任务误关新的 `programmaticScroll`；用户进入 `UserBrowsing` 后必须连续 5 帧稳定到底才恢复 `AutoFollow`，避免 pre-anchor 瞬态到底造成吸回。`ChatStreamingRenderer.kt` 在 streaming 期间用同一个免责声明 `Text` 透明占位、不提前显示文字，fresh settled bounds 到位后 AutoFollow 只请求一次正向底部锚点，降低 finalize 尾部增高闪动风险。代码层面已复查无 `reverseLayout/asReversed`、小分割 list itemization、overlay/active-zone、`scrollBy/dispatchRawDelta` streaming 高度补偿残留。
- `ChatStreamingRenderer.kt` 继续清理小分割撤回后的渲染残留：删除旧的 `StreamingRenderedLines`、`buildStableStreamingLineBuffer(...)`、committed 物理行预切 renderer、fresh suffix 灰色高亮动画和 active block 预测量 helper；`RendererAssistantMarkdownContentImpl(...)` 现在和 streaming 一样走 soft-wrap block renderer，并通过现有 inline Markdown cache 保留加粗 / 链接 / code。这样 streaming -> settled 收口不再从 soft-wrap streaming 树切到另一套 TextMeasurer / 预切行树，专门压吐完后行高 / 行宽微动。`ChatScreen.kt` 里小分割相关 `StreamingBlockChatListItem / StreamingTextBlock / activeStreamingBlockIndex / streaming_tail` 等符号已复查无残留。
- `ChatScreen.kt` 撤掉 streaming 小分割 / block item 化主线：列表显示重新直接使用 `messages`，不再派生 `StreamingBlockChatListItem / StreamingTextBlock / streamingBrowseBlockSnapshot / activeStreamingBlockIndex`，也不再在新 active block 产生时额外 `requestScrollToItem(0)` 接尾巴。当前 waiting 小球、streaming 正文、settled 完成态回到同一个 assistant item 内完成；这是按真机反馈优先恢复整体渲染树稳定，接受后续需要重新评估“小幅上滑仍在本条消息内”的原始抢手问题。
- `ChatScreen.kt` 按会诊结论给 AutoFollow 新 active block 贴底请求加用户浏览 epoch 软取消：用户触碰 streaming 列表进入 `UserBrowsing` 时递增 epoch；新 block 产生后如果需要 `requestScrollToItem(0)` 接尾巴，会先等一帧让同帧 pointer 事件有机会落地，再只按 epoch / `scrollMode` / `userInteracting` 复核，用户已接管则取消本次旧 AutoFollow 请求。此前尝试用 `firstVisibleItemIndex == 0 && offset == 0` 做严格 position 复核会误杀正常回底跟随，已回撤；这次不改 block 拆分、不删 `requestScrollToItem(0)`、不恢复 `scrollBy` / overlay。
- `ChatScreen.kt` / `ChatStreamingRenderer.kt` 针对视频里“下一行先冒黑点、半截 Markdown 憋字换身份、分割线时有时无”的反馈收口 streaming 渲染判定：waiting 小球改为只在整条 assistant 完全没内容时显示，段落切换产生的空 active block 改成零高度；active Markdown 仍实时吐字，但只有结构前缀后已有非空正文时才切成标题 / 列表 / 引用，避免 `# `、`- `、`1. ` 这类半成品先撑出结构样式；跨 block 的一级 / 二级标题分割线改由 `ChatScreen.kt` 全局派生后传给 renderer，避免每个 block 只看自己内部 previous 导致分割线丢失。本次不改 block 拆分、AutoFollow、回到底部按钮安全区，也不恢复 overlay / scrollBy 补偿。
- `ChatStreamingRenderer.kt` 针对“吐字那一行本身有点跳 / 闪、下一行有时冒头闪”的反馈，收窄 active streaming 显示层：active 段落 / 标题 / 列表正文改为单个 soft-wrap `Text` 自己换行，不再在 streaming 态按物理行拆成多颗 `Text`，也不再给新字尾部做 fresh suffix 灰色高亮动画。这样换行时不会发生“上一行变 stable Text + 新建下一行 active Text”的局部树切换，滚动状态机、block item 化、完成态 Markdown 渲染和选择链不动。
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 先针对最新反馈收两处低风险口径：回到底部按钮增加 56dp 离底安全区，只有 `firstVisibleItemIndex != 0` 或 `firstVisibleItemScrollOffset > 56.dp` 才有资格在停止滑动后出现，避免贴着工作线附近轻微离底也冒按钮；用户手动回到反向列表真实底部 `index=0 / offset=0` 即可恢复 `AutoFollow`，不再额外依赖 streaming content bounds 命中 4dp 工作线，减少“已经回到底部但不跟随”的误判。AutoFollow 新 block 贴底也从挂起式 `scrollToItem(0)` 改为 `requestScrollToItem(0)`，交给下一次 remeasure 接住新尾巴，降低切 block 边界的吐字闪动风险。
- `ChatScreen.kt` 调整回到底部按钮显示时机：按钮不再在用户滑动过程中出现或续亮，用户驱动的列表滚动发生时会先强制隐藏；等滚动停止后一帧，再按动态 / 静态统一的离底资格判断是否 pulse，离底才短暂出现并自动隐藏。streaming 触碰列表现在只负责切 `UserBrowsing`，不再立即点火按钮，避免用户浏览时按钮一直挡眼。
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 复盘上一刀后撤掉 active block 随 `scrollMode` 动态换 key 的做法。所有 streaming block 现在统一使用稳定 `messageId:streaming_block:<index>` key，避免用户触碰列表进入 `UserBrowsing` 时同一可见块从 `streaming_tail` 换成 block key 造成上下窜；AutoFollow 贴底时如果新 active block 产生，只在非用户接管窗口同步 `scrollToItem(0)` 接住新尾巴，不做 streaming 高度 `scrollBy` 补偿。pending finalize 期间也继续用 force-stable block 渲染，避免完成前先合回普通单条 item；同时手动滑回真实底部 `index=0 / offset=0` 且严格命中 4dp 工作线后可恢复 `AutoFollow`，解决“回到底部工作线但不继续跟随”的反馈。
- `ChatScreen.kt` 继续收口 streaming 小幅上滑的残余抢手和完成瞬间重排闪动：active block 兜底上限从 360 字收小到 180 字，并把中文逗号 / 顿号 / 冒号纳入超长无空行文本的安全切点；AutoFollow 贴底时 active block 仍用固定 `messageId:streaming_tail` key 保持最新尾巴贴底，进入 `UserBrowsing` 后 active block 改用自身 block key，让当前可见 block 完成切分后能随 key 迁移到稳定 item，不再继续锚住新 tail。streaming 完成后也会保留已完成 block 快照，回到底部或下一轮发送再合回完整 assistant item，避免 finish 当拍从多 block 树合成巨型 settled item 造成视觉闪一下。这刀不新增 `scrollBy` / `dispatchRawDelta`，不恢复 overlay / active-zone，也不改 `ChatScrollCoordinator.kt` 状态机。
- `ChatScreen.kt` 针对 streaming 中“小幅上滑仍被 index 0 长高带回工作线”的抢手问题，把上一版用户触发的单刀 prefix/tail 拆分改成持续 block item 化：生成过程中按段落边界派生多个稳定 block item 和一个 active block item，代码块内不切分，超长无空行内容按句子 / 空白边界兜底切分，避免视觉底部 index `0` 的 active block 重新长成巨型 item。稳定 block 使用 `messageId:streaming_block:<index>` key，active block 使用固定 `messageId:streaming_tail` key，以兼顾浏览锚点稳定和贴底 AutoFollow。这不改 `ChatScrollCoordinator.kt`，不新增 overlay / active-zone / streaming `scrollBy` / `dispatchRawDelta`；点击回到底部会清掉完成后浏览态 block 快照，恢复完整 streaming item 并继续跟随。
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 撤掉 pending finalize 阶段的主动底部精修：完成态仍保留 `beginPendingStreamingFinalize(...) -> fresh settled bounds -> finalizeStreamingStop(...)` 两阶段，但不再在 fresh bounds 到位后调用 `alignVisibleChatListBottom(...)`。真机反馈吐完后可视窗口会掉头跑到长回复上方，定位到这条完成瞬间主动滚动链风险高于收益；当前让 settled 渲染树自然落地，不恢复完整 `scrollToBottom(false)`、不恢复 `requestScrollToItem(0)` finalize pin，也不恢复旧 overlay / height follow 链。

## 2026-04-25

- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 删除发送后没人解锁的 `suppressJumpButtonForImeTransition` 伪门，并把回到底部按钮资格统一成“消息非空 + 键盘不可见 + 生命周期未抑制 +（反向列表真实离底或 streaming 已进入 UserBrowsing）”。此前发送路径会把 `suppressJumpButtonForImeTransition` 设为 true，但正常 chat 流程没有恢复 false，导致发过一条消息后按钮长期被压死；同时按钮离底判断已直接读取 `chatListState.firstVisibleItemIndex / firstVisibleItemScrollOffset`，不再吃 24px metrics bucket。
- `ChatScrollCoordinator.kt` 修正回到底部按钮 pulse 信号被提前消费的竞态：此前 `BindJumpButtonPulseEffect(...)` 在 `showJumpButton=false` 时也会把新的 `userScrollSignal` 标记为已处理，streaming 态触碰列表切 `UserBrowsing` 后如果 show 条件晚一拍才变 true，按钮信号已经被吃掉，表现为动态生成中按钮不出。现在只有 `showJumpButton=true` 时才消费信号；show 条件未满足时只隐藏按钮、不吞本次触发。
- `ChatScreen.kt` 修正 streaming 态回到底部按钮“状态已进 UserBrowsing 但按钮不出”的漏触发：此前按钮 pulse 只靠 `chatListUserDragging / recyclerScrollInProgress` 这类滚动状态点火，真机上流式生成时用户触碰列表先由 pointer hook 切到 `UserBrowsing`，但 drag / scroll 状态可能没在同一拍命中，导致 `showJumpButton` 有资格却没有 pulse。现在 `markStreamingUserBrowsingFromPointer()` 在确认用户触碰 streaming 列表后同步递增 `jumpButtonUserScrollSignal`，只补按钮点火，不新增 scroll 调用，不恢复旧 overlay / active-zone / streaming follow 链。
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 收口回到底部按钮显示和点击语义：按钮不再只因为 `!atBottom` / `!nearWorkline` 就自动 pulse。静态态离底资格看反向列表真实位置 `firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset > 0`；streaming 态由用户触碰 / 拖动消息列表进入 `UserBrowsing` 后触发 pulse，避免生成中 `index/offset` 尚未变化导致按钮不出。点击按钮也改为直接 `scrollToItem(0)` 回到反向列表真实底部，避免复用通用 bottom bounds 精修链后没有真正回到 `index 0 / offset 0`。
- `ChatScreen.kt` 继续收紧 streaming 手势与键盘边界：`isAtStreamingWorklineStrict()` 现在除了工作线 bounds 命中，还要求反向列表真实处在底部 `firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0`，防止轻微上滑后因旧 bounds 仍在容差内立刻恢复 `AutoFollow`。同时 streaming 中输入框聚焦 / IME 可见会直接解除 `sendStartAnchorActive`、结束 `sendUiSettling` 并清掉 `lockedConversationBottomPaddingPx`，让键盘外部几何立刻进入 bottom padding / 工作线计算；仍不恢复 IME 主动滚动。
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 再次收紧“用户手指优先”：`handleChatListScrollStateChanged(...)` 现在会先处理用户拖动，再处理 `programmaticScroll`，避免程序滚动保护窗口吞掉真机拖动事件，造成“我已经上滑但还被往下带”。同时 `scrollToBottom(...)` / `alignVisibleChatListBottom(...)` 的程序对齐循环新增 `shouldContinue` 刹车，用户一旦进入 `UserBrowsing` 或正在拖动，正在跑的对齐循环也会停。随后撤掉 streaming 输入框聚焦 / IME 可见时额外发起的 `alignVisibleChatListBottom(...)` 主动精修，键盘抬升改回依赖 reverse-list + `contentPadding.bottom` 的自然重排，避免 IME / 手势边界又多一脚程序滚动抢手。
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 继续压 streaming 期间“上滑被吸回 / 抢手”和点输入框不跟键盘抬升的问题：用户拖动或任何非程序滚动现在只要处在 `isStreaming || hasStreamingItem` 窗口就会进入 `UserBrowsing`；从 `UserBrowsing` 恢复 `AutoFollow` 的工作线命中带从原 16dp 收到 4dp，避免停在工作线附近就被吸回去。同时，streaming 中用户主动聚焦输入框 / IME 可见时会释放发送起步锚点锁，并允许 realtime composer 几何跟随键盘外部抬升；输入框内部文字 / 图片内容高度仍不允许进入聊天列表 reserve。
- `ChatScreen.kt` 将统一工作线视觉 gap 从 `64.dp` 调整为 `80.dp`。这是单点设计参数变更，会同时上移小球首发锚点、streaming 正文底边、静态完成态贴底目标和回到底部目标；目的只是让输入框上方预留给免责声明 / 极端说明 / 底部呼吸区的空间更舒展，不改变反向列表单主人、发送期 reserve 锁、两阶段 finalize 或输入框内容高度隔离规则。
- `ChatScrollCoordinator.kt` 收紧 streaming 手势优先级：此前只有 `collectIsDraggedAsState()` 判定为拖动时才切 `UserBrowsing`，真机上可能出现反向列表已进入 `isScrollInProgress`、但 drag state 还没同步命中的窗口，AutoFollow 继续贴底导致用户感觉“上滑被往下带”。现在 streaming 中任何非程序性的列表滚动都会立即进入 `UserBrowsing`，直到列表停稳且严格回到工作线才恢复 `AutoFollow`，专门压“上滑/下滑不要抢手”的问题。
- 文档同步补充 composer 内容高度边界：输入框里的多行文字、未来图片预览、附件缩略图、图文混排都只属于 composer 内部内容高度，不能进入聊天列表 bottom reserve，也不能影响小球工作线 / 历史消息贴底。聊天列表只认折叠态 composer 外壳、safe area / IME / 底部外部几何、发送期锁定 reserve 和工作线 gap；若未来产品明确要“附件栏顶起聊天区”，必须作为单独 external tray 重新设计，不能复用输入内容高度。

## 2026-04-24

- `ChatScreen.kt` / `ChatComposerCoordinator.kt` 修正输入长文本误抬消息区的问题。根因是 `onChromeMeasured` 会把输入框展开后的 chrome 高度写进 `inputChromeRowHeightPx`，随后 `bottomBarHeightPx / effectiveBottomBarHeightPx` 或 realtime composer 高度被当成列表 reserve，导致输入框内文字行数也能把外部消息区顶上去。当前 `bottomBarHeightPx` 只允许在输入为空、未聚焦、非发送收口窗口时吸收新的 chrome 实测值；静态列表 bottom padding 只吃 `observedCollapsedBottomReservePx`（折叠态 reserve）或启动估值；streaming 需要跟随键盘/底部宿主时，也会从实测 composer 高度里剥掉当前输入框内容高度，只保留相对 safe bottom 的外部抬升量。原则是：输入框里的文本内容不影响外部消息高度，只有键盘/底部宿主外部几何能影响工作线。
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 又把 finalize handoff 从完整 `scrollToBottom(false)` 收回成“只精修当前可见底边”的 `alignVisibleChatListBottom(...)`。上一轮 pending finalize 里复用完整回底链会先执行 `scrollToItem(0)`，长回复完成态收口时容易被重新锚定整条 item，表现为文本瞬间上跳、底部出现大块空白；当前只在 fresh Settled bounds 仍有效的 pending 窗口内按 bottom delta 精修，不再在 finalize 窗口重跑 index 定位。该 helper 复用已修正的 reverse-list `scrollBy(-alignDelta)` 方向，不恢复 overlay / active-zone / raw follow 旧方案。
- `ChatScrollCoordinator.kt` 修正反向列表底部对齐的 `scrollBy` 符号，恢复到反向列表刚落地时的 `scrollBy(-alignDelta)` 口径。当前 `alignDelta = 目标工作线 - 最新消息底边`，在 `LazyColumn(reverseLayout = true)` 下若继续用正号，finalize/回底发现底部空白时会把内容越推越高，8 次对齐循环会放大成“大块空白”。这刀只改 reverse-list bottom align 方向，不动小球工作线、发送期 reserve 锁、两阶段 finalize 或 overlay/active-zone 已废弃链。
- `AndroidManifest.xml` 已关闭 Android Auto Backup（`android:allowBackup="false"`），避免 `chat_ui_cache` 这类本地 UI / 聊天窗口快照在“清除数据 / 重装 / 系统恢复”后又被系统云备份悄悄恢复成旧状态。`ChatScreen.kt` 同时给清空本地快照的 clean-state 启动清掉文件级 markdown 缓存，并给恢复草稿写回盘加了“草稿仍真实存在”检查，防止内存旧草稿在清缓存后重新固化回本地。
- `ChatScreen.kt` 已撤掉上一刀的输入区 reserve 持久化读取 / 写入。真机反馈开机直接出现一大片底部空白，根因是本机曾写入过偏大的 `chat_ui_metrics.collapsed_bottom_reserve_px`，启动时把它当 bottom bar 估值会直接撑大列表底部 padding。当前恢复为启动只用代码内固定估值，折叠态 reserve 仍只在本次运行内由稳定实测值写入内存，不再信任磁盘 UI 高度。
- `ChatScreen.kt` 继续恢复发送起步历史口径：当本次发送会收口 composer 时，`lockedConversationBottomPaddingPx` 只允许使用“折叠态 reserve + STREAM_VISIBLE_BOTTOM_GAP”；若本轮还没采到折叠态实测值，则退回固定 `STARTUP_BOTTOM_BAR_HEIGHT_ESTIMATE + STREAM_VISIBLE_BOTTOM_GAP`。不再 fallback 到 `latestConversationBottomPaddingPx` 或 `stableComposerBottomBarHeightPx`，避免长文本输入框当前高度把小球工作线直接顶高。
- `ChatScreen.kt` 同步恢复发送起步“同源工作线”规则：`sendStartBottomPaddingLockActive` 期间，`streamingWorklineBottomPx` 直接由 `lockedMessageViewportHeightPx - lockedConversationBottomPaddingPx` 得出，不再同时让列表吃 locked padding、而工作线判断仍吃当前输入框 / composer 高度。这样发送当拍的小球、释放保护、后续 AutoFollow 判断都使用同一份折叠态锁值，避免小球被长文本输入框高度顶高。
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 把 finalize 收口后的 restore 从完整 `scrollToBottom(false)` 改成只对当前可见底部消息做 `alignVisibleChatListBottom(...)` 精修。完成态 fresh bounds 已经证明最终 assistant 在屏幕上，此时再 `scrollToItem(0)` 会重新锚定整条长 item，可能把正文底部甩到工作线上方造成大块空白；现在只按 bottom delta 精修，不重跑整套回底定位。
- `ChatScreen.kt` 给发送起步 reserve 预热再补了一层同步兜底：`commitSendMessage(...)` 进入发送事务前，如果当前列表没有在使用 realtime composer 几何、且 `latestConversationBottomPaddingPx` 已经是稳定 collapsed 口径，就直接从这份共享 measure 结果推导 `preSendStableCollapsedReservePx`。这样冷启动首次发送即使 `observedCollapsedBottomReservePx` 还没来得及被 SideEffect 写入，也能优先用同拍稳定 reserve，进一步减少退回固定 `STARTUP_BOTTOM_BAR_HEIGHT_ESTIMATE` 的概率；长文本输入框实时高度仍不会被采入。
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 已撤掉 finalize 后的主动 bottom restore 精修。真机反馈完成瞬间会间歇性大幅上跳、留下明显大空白，定位为 handoff 窗口里 `messageContentBoundsById` 被清后，restore 读到 `visibleItemsInfo[0]` 的过渡态并放大成滚动 delta。当前两阶段 finalize 已经等 fresh settled bounds，收口后不再额外滚动；最多接受极小的固定 px 误差，避免间歇性大跳。
- `ChatScreen.kt` 又把 finalize 收口补偿从“完成后 restore”前移到 pending finalize 保护期内：`snapshotFlow` 等到目标 assistant 的 Settled fresh bounds 后，直接用这份 `LayoutCoordinates` bottom 和当前工作线算一次带限幅的 `scrollBy(...)`，再调用 `finalizeStreamingStop(false)`。这样补偿 Streaming -> Settled 渲染树高度差时不再走 `currentLastMessageContentBottomPx()` 的 `visibleItemsInfo[0]` fallback，也不在 `streamingMessageId` 清空后的混沌帧补滚，专门压收口时文本上跳并留下大块空白的问题。
- `ChatScreen.kt` 继续调整 finalize 收口：上一条 pending 内限幅 `scrollBy(...)` 真机仍无法压住“大块空白”，说明相对 delta / 符号或 fresh bounds 时机仍不可靠。当前改为等目标 assistant 的 Settled fresh bounds 到位后，不再算相对位移，直接 `requestScrollToItem(index = 0)`，让 `LazyColumn(reverseLayout = true)` 在下一次 remeasure 里按最新 item 的绝对 index 重新钉回底部 padding 基线，然后再 `finalizeStreamingStop(false)`。这仍保持单一反向列表主人，不恢复 overlay / active-zone。
- 对比 Compose 1.10.4 源码后确认，`requestScrollToItem(0)` 只请求 index 在下一次 remeasure 定位，不能替代项目自己的“正文底边贴工作线”精修；上一刀仍会留下大块空白。当前改为：pending finalize 等目标 assistant 的 Settled fresh bounds 到位后，先把 `streamingContentBottomPx` 更新成这份 fresh bottom，再在 `isStreaming` / `pendingStreamingFinalizeMessageId` 仍有效的保护期内调用既有 `scrollToBottom(false)`，复用 reverse-list 主链里的 bottom align；随后 `finalizeStreamingStop(false)`，不再在清空 streaming 状态后补滚。
- `ChatScreen.kt` 收紧了发送起步 reserve 观察值的写入窗口：`observedCollapsedBottomReservePx` 现在只有在非 streaming、非 pending finalize、非发送锁、非 composer settling、非 collapse overlay、输入为空、无 focus、IME 收起时才允许更新。这样长文本发送后的收口 overlay 高度不会被误记成折叠态 reserve，避免连续发送后小球锚点逐渐跑高。同次把 finalize 后是否补 `scrollToBottom(false)` 的容差放宽到 `BOTTOM_POSITION_TOLERANCE`，吞掉完成态 fresh-bounds 的几 px 误差，继续压尾部收口轻微抖动。
- `ChatScreen.kt` 修正了上一刀 finalize restore 过粗的问题：完成态收口后不再只按单向 overflow 判断“是否到底”，而是按带符号的 bottom align delta 做绝对值容差判断；如果需要补回底，会先等 finalized settled tree 真正落地一帧，再复核并执行 `scrollToBottom(false)`。这样几 px 的 fresh-bounds 误差不会再抖一下，但文本被留在工作线上方的大空白会被补回。
- `ChatScreen.kt` 继续收紧尾部收口：assistant 消息不再在 pending finalize 与 finalized 后从“active streaming 分支”切到 `SelectableRenderedStaticMessageContent(...)` 另一棵 Composable 树，而是统一走同一个 `CompositionLocalProvider -> key -> ChatStreamingRenderer` 渲染路径。pending finalize 时会把 renderMode / fresh 参数收敛成 settled 口径，`finishStreaming()` 也会先把 `streamingMessageContent` 归一化到和最终 `msg.content` 一致，避免收口瞬间因为 Composable 身份、fresh 参数和尾部空白归一化同时变化导致轻微抖动。
- `ChatScreen.kt` 修正了静态态键盘弹起时消息区整体跟着上抬的问题：`SubcomposeLayout` 里列表 bottom padding 不再无条件使用带 `imePadding()` 的 `measuredComposerHeightPx`。现在只有 streaming / hasStreamingItem / 首次贴底这类需要实时工作线的场景才把 composer 实时高度纳入消息列表 reserve；普通静态输入聚焦时，列表只保留稳定的输入框底部高度，不再把 IME 高度也当成消息区底部 padding。
- 真机反馈反向列表单主人主链已基本稳定后，尾部收口只剩轻微抖动；本轮只在 `restoreBottomAnchorIfNeededAfterStreamingStop(...)` 加了一个极小 finalize restore 容差。原因是静态贴底容差仍为 `0.dp`，finish 后 fresh bounds 若只差 1-2px 也会触发一次 `scrollToBottom(false)` 精修，容易表现为收口那一下轻抖。现在 finalize restore 仅在超出 `BOTTOM_POSITION_TOLERANCE / 4` 时才补回底，启动、发送、拖动、反向列表主链不动。
- Claude 复审稿指出两处可继续收紧的 reverse-list 口径：`currentBottomOverflowPx()` 不应再用 `abs(...)` 把“内容底边高于目标”也当成未贴底，现在已改成只返回正向欠滚距离；`prepareScrollRuntimeForStreamingStart(...)` 也从 `Idle` 改为直接进入 `AutoFollow`，确保用户按发送后不会残留 `UserBrowsing` 语义。另一个复审提到的 `currentLastMessageContentBottomPx()` fallback index 问题当前代码已是 `visibleItemsInfo.index == 0`，不再指向最旧消息。
- 代理复审后继续收口反向列表单主人主链的旧残留：`ChatScreen.kt` 已移除不再生效的 `streamingWrapGuardTargetLineCount`，发送起步在插入 user + assistant placeholder 后始终同步 `requestScrollToItem(0)`，startup 首次回底后会立即标记 `initialBottomSnapDone`，pending finalize 在 fresh bounds 到位后会重新确认用户没有进入 `UserBrowsing` 再决定是否补 `scrollToBottom(false)`。`ChatScrollCoordinator.kt` 同步移除了旧正向 / overlay 时代的 `followStreamingByDelta(...)` 追滚链和 `streamBottomFollowActive` 空壳状态，UserBrowsing 只在严格命中工作线后才自动恢复 AutoFollow，发送起步保护遇到用户接管会立即释放。
- `ChatRecyclerViewHost.kt` / `ChatScreen.kt` / `ChatScrollCoordinator.kt` 已停止继续修 mixed active-zone / overlay 运行时，正式切回“单一运行时主人 + 反向列表”主线。当前 `ChatRecyclerViewHost.kt` 已改为 `LazyColumn(reverseLayout = true)` + `items.asReversed()`；`chatListMessages` 重新收平到 `messages`；`currentLastMessageContentBottomPx()` 的 fallback 与 `scrollToBottom(false)` 也同步回到 reverse-list 口径，底部最新显示项按 index `0` 处理。
- `ChatScreen.kt` 当前已删除 mixed active-zone 主链的核心切管结构：`StreamingLocation`、`BottomActiveZoneSlice / resolveBottomActiveZoneSlice(...)`、`renderBottomActiveZone()`、Overlay 恢复门、active-zone 拖动接管和 `requestSendStartBottomSnap()` 都已退出运行时主路径。聊天消息重新只由列表承接；底部 composer 继续保留为输入 UI 宿主，但不再承担消息运行时所有权。
- 发送起步当前也已回到 list-side 单主人口径：`commitSendMessage()` 在同一事务里继续完成输入框收口、user + assistant placeholder 插入、`prepareScrollRuntimeForStreamingStart(...)`、`sendStartAnchorActive = true` 和发送期 reserve 锁；同时按 reverse-list 语义同步 `requestScrollToItem(0)` 回到底部。旧 active-zone 时代那种“先切层、再只滚 historyMessages”的发送起步链已经删除。
- 完成态收口这轮没有回退。`beginPendingStreamingFinalize(...) -> fresh bounds -> finalizeStreamingStop(...)` 这条两阶段 finalize 继续保留；本次重构只移除了 mixed active-zone / overlay 的运行时切管，不把尾部收口稳定性重新换掉。
- 项目记忆同步切换：`current-status.md`、`open-risks.md` 和根 `AGENTS.md` 已把当前真相收口到“反向列表单主人”；旧的 active-zone / overlay 路线保留在 git 历史与 ADR 中，只作为历史归档，不再冒充当前运行时真相。

## 2026-04-23（历史 active-zone / overlay 阶段，仅供归档）

- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 又补了一刀底部统一活跃区的发送起步与拖动所有权保护：发送事务里重新把 `sendStartAnchorActive` 置回 `true`，让 `BindChatListScrollEffects(...)` 在 active zone 主链里继续承担发送起步保护窗口；其 release 条件也已收紧成“命中工作线、composer 已稳定、并且列表已经停止滚动”后再连续一拍放行，避免 history list 的发送回底尚未停稳时就提前让权。同次，底部活跃区手动浏览后的 Overlay 恢复也不再是“永久不自动恢复”，而是改成：拖动起手先把 `bottomActiveZoneOverlayRestoreArmed` 置 `false` 并交回 `LAZY_COLUMN`，只有等列表真正停下、滚动模式回到 `AutoFollow`、且仍命中工作线后，才先 re-arm，再下一拍恢复 `OVERLAY`。这刀只针对“发送瞬间仍抖一下”和“上下拖动时正文乱窜 / 与用户消息重叠”这两条主链收口，不重开旧 placeholder / overlay follow / requestScrollToItem 行锚定。`./gradlew.bat :app:compileDebugKotlin` 已通过。
- `ChatScreen.kt` 继续收口了底部统一活跃区的底部量算口径：`LazyColumn` 现在重新量满整个消息区高度，不再只量到 `conversationBodyHeightPx`；Overlay 可见时，列表 `bottomPaddingPx` 改为统一吃“composer reserve + breathing gap + active zone height”，不再把 composer reserve 和 active zone 分别落在“裁短列表高度”和“底部 padding”两处双重避让。同时，`currentHistoryListBottomTargetPx()` 与 send-start 锁期间的有效 bottom padding 也同步改成沿用这同一口径，避免 history list 的底边目标仍按旧公式漏掉 composer reserve，继续留下“输入框上方像多出一整节白色高度 / 静态底部文本下方空白过大 / 发送瞬间抖一下”这组回归。`./gradlew.bat :app:compileDebugKotlin` 已通过。
- `ChatScreen.kt` 又继续收口了底部统一活跃区的布局与发送起步顺序：`SubcomposeLayout` 里 `LazyColumn` 不再在 Overlay 模式下被 `conversationBodyHeightPx - bottomActiveZoneHeightPx` 硬裁短，而是继续量满 `conversationBodyHeightPx`，并把 `bottomActiveZoneHeightPx` 作为 `listBottomPaddingPx` 覆盖到底部空区；`currentHistoryListBottomTargetPx()` 同步改成在 Overlay 下使用 `viewportEndOffset - measuredBottomActiveZoneHeightPx` 作为 history list 的正确底边目标，避免 `scrollToBottom(false)` 永远误判没贴底。同次，发送起步又恢复为在 `commitSendMessage()` 的同一拍直接切 `StreamingLocation.OVERLAY`，`requestSendStartBottomSnap()` 只再对 `historyMessages` 做回底，不再经历“先滚完整列表、下一帧再切 active zone”这一拍。`finalizeStreamingStop()` / `resetStreamingUiState(...)` / `uiRuntimeResetKey` 里清 `anchoredUserMessageId`、收回 `LAZY_COLUMN` 的退场逻辑继续保留。`./gradlew.bat :app:compileDebugKotlin` 已通过。
- `ChatScreen.kt` 继续收口了底部统一活跃区的进退场时机：发送起步不再在插入用户消息/assistant placeholder 的同一拍立刻切 `StreamingLocation.OVERLAY`，而是先保持 `LAZY_COLUMN`，让首个 `requestSendStartBottomSnap()` 用整条消息列表完成一次真实 `scrollToBottom(false)`；回底做完后，如果仍在 streaming 且未进入 `UserBrowsing`，才切进底部活跃区继续承接当前轮 user / assistant。与此同时，`finalizeStreamingStop()`、`resetStreamingUiState(...)` 和 `uiRuntimeResetKey` 进入时都会显式清掉 `anchoredUserMessageId` 并把 `streamingLocation` 收回 `LAZY_COLUMN`，专门压“输入框上方多出一整节白色空白 / 静态底部文本拖不动 / 重进聊天页不贴底”这组回归。`./gradlew.bat :app:compileDebugKotlin` 已通过。
- `ChatScreen.kt` 针对底部统一活跃区这轮真机回归又补了三处最小修正：一是 `renderBottomActiveZone()` 根宿主新增竖向拖动接管，拖动到底部活跃区时会立刻切 `scrollMode = UserBrowsing`、把 `streamingLocation` 交回 `LAZY_COLUMN`，并把本次 drag delta 直接交给 `chatListState.dispatchRawDelta(...)`，解决“生成中文本和静态底部文本在 active zone 区域里完全划不动、像抢手”的问题；二是 Overlay 模式下 `scrollToBottom(false)` 不再把 history list 的 `currentBottomAlignDeltaPx` 写死成 0，而是重新按 `viewportEndOffset - historyContentBottom` 做精修，继续收掉发送后 / finalize 后的大块底部空白；三是发送起步短窗口重新补回 `lockedConversationBottomPaddingPx` 锁，锁值优先吃 `observedCollapsedBottomReservePx + STREAM_VISIBLE_BOTTOM_GAP`，直到 `requestSendStartBottomSnap()` 真正执行完 `scrollToBottom(false)` 后才释放，专门压这轮结构刀把“小球和历史文本先抖一下”又带回来的回归。`./gradlew.bat :app:compileDebugKotlin` 已通过。
- `ChatScreen.kt` 又补了一刀几何口径收口：`BottomActiveZoneSlice` 接管渲染后，`isNearStreamingWorkline()`、`isAtStreamingWorklineStrict()` 和静态 `atBottom` / restore 判定已不再在 `OVERLAY` 模式下继续吃单条 assistant 的 `streamingContentBottomPx`。现在 Overlay streaming 期间 workline 命中直接短路为 true；而 streaming 结束后，如果底部活跃区仍在场，静态“是否到底”会改用 history list 自己的 viewportEndOffset 和最后一条历史消息 bottom 比较。这样 `restoreBottomAnchorIfNeededAfterStreamingStop()`、静态回到底部按钮等逻辑在 Overlay 模式下不再因为 assistant bounds 偏高而误判。
- `ChatScreen.kt` 已从“assistant-only overlay 第一刀”继续推进到**底部统一活跃区宿主**。当前新增 `BottomActiveZoneSlice / resolveBottomActiveZoneSlice(...)`，在 `StreamingLocation.OVERLAY` 下把“当前轮用户消息 + 当前 assistant（waiting / streaming / settled）+ 前置 1 条历史尾巴”从 `messages` 中切出来，由 `renderBottomActiveZone()` 在 `SubcomposeLayout` 里统一渲染；`LazyColumn` 此时只再渲染更早的 `historyMessages`。这不是继续给旧 Overlay 补追滚，而是直接拆掉“overlay body + 列表 placeholder + pending follow bridge”这条旧桥。`./gradlew.bat :app:compileDebugKotlin` 已通过。
- `ChatScreen.kt` 的 `SubcomposeLayout` 当前也不再让底部活跃区继续盖在列表上方。现在会先测 composer、再测底部活跃区，然后把 `LazyColumn` 和欢迎语只量到“去掉 composer reserve 和底部活跃区高度之后的剩余空间”；同时 Overlay 模式下列表 `bottomPaddingPx` 会退回 0，不再叠加那套给 active assistant 让位的旧 reserve。也就是说，当前列表和底部活跃区已经开始真正分区，而不是靠 placeholder / alpha twin / 追滚去假装贴合。
- 发送起步当前也已跟随结构刀改口：`commitSendMessage()` 仍在同一发送事务里完成输入框收口、`upsertUserMessage(...)`、assistant placeholder、`prepareScrollRuntimeForStreamingStart(...)` 和切 `streamingLocation = OVERLAY`，但不再对 assistant placeholder 发 `requestScrollToItem(index, offset)`。现在改成在切入底部活跃区后，补一发新的 `scrollToBottom(false)`，把“只剩更早历史的 `LazyColumn`”压回底部；当前轮 user / assistant 由底部活跃区自己贴底承接。`jumpToBottom`、`UserBrowsing` 和列表侧的 fallback 主链仍保留。
- `6f4f13d Tighten overlay finalize shell isomorphism` 已在同日回退为 `e765c94 Revert "Tighten overlay finalize shell isomorphism"`。原因是用户真机反馈“连接处重影、尾部微抖、滑动发虚”都没有得到改善，这刀零收益且继续污染判断。当前代码已明确回到上一条 Overlay 基线：保留 Overlay 实测高度占位、`onSizeChanged + SideEffect + dispatchRawDelta(...)` 的列表同步上推、pending finalize 禁 overlay follow、Overlay pending finalize 切 `Settled + disclaimer`，但不再额外补 pending finalize 的外壳 padding / `selectionEnabled` 同构实验。
- `ChatScreen.kt` 继续收紧了 Overlay 正文把底层列表往上推的时机：此前 Overlay 高度增长时，是先在 `onSizeChanged` 里记下新高度，再由 `LaunchedEffect + followStreamingByDelta(scrollBy)` 追一拍去补滚；真机视频里仍能看到 streaming 正文和历史文本/用户气泡之间像“擦边重影”一样差一帧。现在改成在 `onSizeChanged` 时累计本次真实高度增量，再由 `SideEffect + LazyListState.dispatchRawDelta(...)` 直接把这份增量打给底层 `LazyColumn`，并在 `dispatchRawDelta(...)` 只消费部分 delta 时把剩余量保留下来，下个成功重组继续追，不再悄悄丢失。同时，`beginPendingStreamingFinalize(...)` 和 overlay size 监听都会在 `pending finalize` 期间关闭这条 Overlay follow，避免收尾切到 `Settled + disclaimer` 时自己再推自己一下，把尾部轻微抖动放大。`./gradlew.bat :app:compileDebugKotlin` 已通过。
- `ChatScreen.kt` 继续补上了 Overlay 第一刀缺的“列表同步上推”链：此前虽然已经把 `LazyColumn` 对 active assistant 的占位从一行假高度改成 Overlay 实际高度，但底层历史文本 / 用户气泡仍不会随着 Overlay 继续吐字自动往上让位。现在新增了 `streamingOverlayMeasuredHeightPx` 和 `streamingOverlayFollowLastHeightPx`，通过 Overlay 的 `onSizeChanged` 记录当前真实高度，并在 Overlay 模式且用户未进入 `UserBrowsing` 时，复用现有 `followStreamingByDelta(...)` 的程序滚动保护，按“本次 Overlay 高度增长的 delta”同步 `scrollBy` 底层列表。也就是 Overlay 继续长高多少，历史列表就同步上推多少，不再停在原地等正文压上去。`./gradlew.bat :app:compileDebugKotlin` 已通过。
- `ChatScreen.kt` 修正了 Overlay 第一刀里最直接的重叠问题：此前 `LazyColumn` 在 `suppressStreamingBodyInList` 时只给 active assistant item 留了一行 paragraph lineHeight 的假占位，Overlay 一旦长成多行正文，就会直接压住上方历史文本和用户气泡。现在 `SubcomposeLayout` 会先同拍测出 Overlay 的真实高度，再把这份实际高度传回 `renderChatList(...)` 作为列表占位；也就是“Overlay 多高，列表就给它留多高”，不再出现整段正文浮在历史消息上方的硬重叠。这刀不改小球起步、不改 Overlay 切层条件、不改旧 fallback 主链。`./gradlew.bat :app:compileDebugKotlin` 已通过。
- `ChatScreen.kt` 继续把 Overlay 第一刀的收尾交接补成更接近 completed 同构：`pendingStreamingFinalizeMessageId` 非空但 Overlay 仍在场时，Overlay 已不再固定走 `StreamingRenderMode.Streaming + showDisclaimer = false`，而是切到 `StreamingRenderMode.Settled + showDisclaimer = true`。这刀不重写 Overlay 主结构、不动发送起步小球链、不重开 `dispatchRawDelta` / wrap guard，只专门压“Overlay 消失、列表 completed alpha 恢复那一拍”因为 renderMode / disclaimer 高度差带来的尾部轻微抖动风险。`./gradlew.bat :app:compileDebugKotlin` 已通过，真机尾抖是否继续减轻待回归。

## 2026-04-22
- `ChatScreen.kt` 按 ADR-0002 落地 Bottom-Anchored Streaming Overlay 第一刀。新增 `StreamingLocation.OVERLAY / LAZY_COLUMN`：发送起步仍走原 `LazyColumn` 小球锚点链；streaming 正文有内容且用户停留底部时，正文由 `SubcomposeLayout` 同层 Overlay 承接，底边锚在 composer 上方工作线，`LazyColumn` 内 active assistant item 不再双画同一份正文。Overlay 模式下 `onAdvance` 跳过 `resolveStreamingWrapGuardDecision(...)` / `dispatchRawDelta(...)` / wrap guard hold，`BindChatListScrollEffects(...)` 也不再对 streaming 正文做 follow delta。用户进入 `UserBrowsing` 时交回 `LazyColumn`；回到底部且仍 streaming、无文字选择/输入选择/拖动/fling/程序滚动/发送锚点保护/composer settling 时恢复 Overlay。`./gradlew.bat :app:compileDebugKotlin` 已通过，真机体感待验证。
- 新增 [ADR-0002](D:/wuhao/docs/adr/ADR-0002-streaming-overlay-for-active-assistant.md)，正式把下一轮 Android 生成态 UI 方向从局部补丁收敛到 Bottom-Anchored Streaming Overlay。当前判断：streaming 下一行残影、每行上推轻微发抖、完成态尾部轻微抖动共同来自“生成态 assistant 正文在正向 `LazyColumn` item 内动态长高”。已排除并禁止继续盲试的方向包括 clip/mask、renderer gate、32ms hold、requestScrollToItem 行锚定、hard bounds wait 和继续调 `dispatchRawDelta`。下一窗口若继续 Android UI，应直接按 ADR-0002 写实施计划并开 Overlay 第一刀：底部 AutoFollow 态 streaming 正文进 Overlay，小球锚点和当前发送起步链不重写。
- ADR-0002 随最新产品目标补充：用户上滑时可以把当前 streaming 正文交回 `LazyColumn`，避免 overlay 遮挡历史；但只要用户回到底部且仍在 streaming，就必须恢复 Overlay。也就是说“本轮不再回 Overlay”只能作为临时降级，不是最终方案。切回条件必须避开文字选择、输入选择、手指拖动和惯性滚动中途，其他滚动链仍不重写。
- `ChatComposerPanel.kt` 把输入框占位文案“描述种植问题”的隐藏条件从“生成中或发送收口中都隐藏”收窄为“仅 composer / IME 收口几何仍未稳定时隐藏”。当前生成中只要输入框已经清空且收口稳定，占位文案会提前回来，不再等 streaming finalize 最后一拍才和 completed 切换、发送按钮恢复一起出现。这刀只改输入框视觉显示门，不改发送禁用逻辑、工作线、streaming wrap guard、AutoFollow 或 finalize 主链。

## 2026-04-21

- 按真机“尾部完成又开始抖”的反馈，已把 `9d36f10` 之后围绕“继续压 streaming 下一行冒头残影”的实验刀整体回退，回到“Baseline Profile 预热系统完成后”的代码口径。也就是说，`requestScrollToItem(index, scrollOffset)` remeasure 行锚定、32ms time-based hold、terminal drain 禁新锚定、以及相关文档口径都撤掉；当前 streaming 新行仍以此前的 reveal-layer wrap guard + active block pre-measure + strict follow gate 为基线，先优先恢复尾部收口稳定，再决定是否重新会诊残影问题
- 根 `AGENTS.md` 和 [docs/runbooks/android-baseline-profile.md](D:/wuhao/docs/runbooks/android-baseline-profile.md) 已把“聊天页关键 UI 改动要同步检查预热系统”固化成规则：后续如果改 `ChatScreen.kt`、`ChatStreamingRenderer.kt`、`ChatComposerPanel.kt`、`ChatComposerCoordinator.kt`、`ChatRecyclerViewHost.kt`，或 Selection / Markdown / LazyColumn / 输入框 IME 主链，Codex 必须主动判断是否要更新 `:baselineprofile` 脚本、是否要重新生成 Baseline Profile；小样式 / 文案通常不用，但发版前关键路径变更要重跑 `:app:generateReleaseBaselineProfile`
- Android 客户端新增 `:baselineprofile` 模块并在 `app` 模块接入 `androidx.baselineprofile`：当前 `BaselineProfileGenerator` 覆盖冷启动进入聊天页、长文区域上下滑、输入框聚焦 / 输入 / 收起这条关键 UI 预热路径；`ChatMacrobenchmark` 同时提供 startup 与聊天滑动 / 输入框帧耗验证。该模块不点发送、不触发后端 / 模型调用，只服务 Compose 首次组合、TextLayout、Selection、LazyColumn 和输入框链路的冷启动预编译。新增 [docs/runbooks/android-baseline-profile.md](D:/wuhao/docs/runbooks/android-baseline-profile.md) 记录生成与验证命令
- `ChatScreen.kt` 继续做了一刀低风险滑动减负：消息选择 toolbar 用的 `messageSelectionBoundsById` 不再在普通滑动时被每条可见消息的 `onGloballyPositioned` 每帧写入 Compose `mutableStateMapOf`。当前普通滑动只刷新一份非 state 的 bounds cache，保证长按复制仍能立刻拿到位置；只有当前消息正在文字选择或已有 pending toolbar 等待 bounds 时，才把该消息 bounds 写入 state map 触发 toolbar 跟随。`messageContentBoundsById`、工作线、AutoFollow、streaming wrap guard 和 SelectionContainer 结构都不动
- `ChatRecyclerViewHost.kt` / `ChatScreen.kt` 给正向 `LazyColumn` 补上了按消息角色分组的 `contentType`：当前只区分 `USER` / `ASSISTANT`，不继续拆 assistant 的 waiting / streaming / settled，避免完成态切换时因为 item 类型变化导致整棵 assistant 内容树 remount。这刀只优化 LazyColumn 的节点复用池，降低用户气泡和长 assistant markdown 在滑入滑出时互相复用造成的轻微帧感；不碰工作线、AutoFollow、SelectionContainer、streaming wrap guard 或 markdown 宽度测量主链
- `ChatStreamingRenderer.kt` 继续按真机“静态 / 动态长文本滑动都有轻微不丝滑”的方向做了一刀更靠近根因的渲染减负：settled assistant markdown 现在只在整条消息外层保留一个 `BoxWithConstraints` 取得宽度，并把同一个 message 级 `TextMeasurer` 和 `availableWidthPx` 传给各个 committed block；committed paragraph/heading/bullet/numbered 不再各自创建 `TextMeasurer` 或在 bullet/numbered 内部再套 `BoxWithConstraints`。这刀只覆盖完成态 markdown 的 committed blocks，最后一个 active block、streaming 生成主链、SelectionContainer、bounds 上报和 LazyColumn 滚动链都不动
- `ChatScreen.kt` 修复了一个偶发“生成已结束、用户已输入新文字，但发送键仍保持灰色，直到把列表扒到底才亮”的状态卡住问题。根因是两阶段 finalize 在用户浏览态下也继续等待 completed assistant 的 fresh bounds 上报；如果最终 assistant 当时不可见，bounds 不会上报，`isStreaming` 就会长期保持 true，composer 的 `isStreamingOrSettling` 也会继续禁用发送。现在只有需要恢复底部锚点时才等待 fresh bounds；用户浏览态这种不需要回底的 finalize 会直接结束 streaming 状态，不再阻塞下一次输入发送
- `ChatStreamingRenderer.kt` 对 assistant markdown 渲染做了一刀很小的滑动减负：settled/streaming block 内的 paragraph / heading / bullet / numbered `TextStyle` 改为局部 `remember` 复用，并把 settled markdown block key 从 `index + model.hashCode()` 收成仅按 block index。目的只是减少长文本滑入视口时的对象创建、字符串 key 拼接和 bullet/number 宽度测量缓存噪音；没有动 `BoxWithConstraints`、SelectionContainer、bounds 上报、LazyColumn 滚动链或 streaming wrap guard，避免把上一刀已撤回的“文字调整感”重新带回来
- 已回退 `323f507 Reduce chat selection scroll churn`。那一刀把普通滑动期的 message selection bounds 改成普通缓存，并给 `snapshotFlow(readChatListMetrics)` 额外加了分桶 distinct；真机体感反馈可能带来文字“调整 / 动一下”的感觉，所以先撤回，避免把丝滑度问题越修越玄。当前保留上一版较安全的减负基线：item 线性反查移除、列表 padding 缓存、bounds/chat metrics 相同值去重、content bounds 跟踪收窄、jump button offset 分桶、streaming wrap guard pre-measure 小缓存
- `ChatScreen.kt` 修复了“聊天列表还在滚动/惯性中点输入框，输入框刚打开又立刻被关掉”的小 bug。原逻辑在 `recyclerScrollInProgress && imeVisible` 时会立即 `clearFocus + hide keyboard`，把 fling 惯性滚动也当成需要收键盘；现在这条收键盘链只在用户手指正在拖动列表（`chatListUserDragging`）时触发，列表惯性未停时点输入框不再被误杀。正常在消息区主动拖动时收键盘的行为仍保留
- `ChatRecyclerViewHost.kt` / `ChatScreen.kt` 先做了一轮聊天文本上下滑动丝滑度的低风险优化：列表宿主改为直接接收消息对象和稳定 key，移除 `itemIds = messages.map { ... }` 与每个可见 item 内部的 `messages.firstOrNull { ... }` 线性反查；`LazyColumn` 的 `PaddingValues` 改为按 padding/density `remember` 缓存；`ChatScreen.kt` 对 chat metrics、message content bounds、root/viewport/composer bounds 等高频回写点增加相同值去重，并进一步把 content bounds 跟踪收窄到最后一条消息 / active streaming / pending finalize，把 jump button 需要的首项滚动 offset 按 24px 分桶。动态生成侧只额外给 `onAdvance` wrap guard 的 active block pre-measure 加了 4 条小缓存，减少同内容同宽度的重复 `TextMeasurer` 测量。这刀不处理 streaming 下一行冒头残影，也不改工作线 / wrap guard 语义 / AutoFollow / finalize 主链

## 2026-04-20

- `ChatScreen.kt` / `ChatStreamingRenderer.kt` 把 streaming 新行防闪主链继续收口到 reveal 提交口：`onAdvance` 不再对 `advance.content` 无条件 commit，而是新增 `streamingWrapGuardTargetLineCount` 这层一次性 wrap guard。当前若 active block pre-measure 检测到“这批字符会让物理行数增加”，代码会先按实测高度差 `dispatchRawDelta(...)` 预滚，并暂时 hold 这一拍的 content/fresh 提交；下一拍同一目标行数再次出现时再放行真正 commit。这样遵守的是“上一行没完全推上去前，下一行不出现”，不是等新行进树后再做遮挡
- `ChatScreen.kt` 又继续收紧了一刀 wrap guard 的 release：当前不再按“同一目标 lineCount 再来一次”直接放行，而是要求先观察到当前已渲染内容底边已真实上移到工作线之上（`currentStreamingContentBottomPx() < currentStreamingLegalBottomPx()`）后才允许 commit；同时在 `LazyListState.canScrollBackward == false` 时直接放弃 hold，避免短内容或无可用预滚空间场景卡死
- `ChatStreamingRenderer.kt` / `ChatScreen.kt` 同次把旧 reveal 残留口径清掉了：`StreamingRevealMode.Conservative`、`strictLineReveal`、`lineRevealLocked`、`streamingLineAdvanceTick` 这一整条空转 plumbing 已删除，`ensureStreamingRevealJob(...)` 也去掉了空的 `onTick` 回调，避免下一窗口再误以为仓库里还存在一套可接线的旧行级 gate
- `ChatScreen.kt` / `ChatStreamingRenderer.kt` 把 streaming 新行防闪从“叶子 renderer 显示门闩”切到了 `onAdvance` 前馈预滚：`rememberGatedStreamingRenderedLines(...)` 已删除，两个 active renderer 现在直接渲染原始 `StreamingRenderedLines`；同时新增 `measureStreamingActiveBlockLayout(...)` 用当前 active block 的真实样式和宽度做 pre-measure，`onAdvance` 在写入下一拍 `streamingMessageContent` 前若检测到物理行数增加，就先按实测高度差 `scrollBy(deltaPx)`，旧 bounds -> follow 主链继续保留做后续精修。随后又补了一刀精度：前馈宽度与真实 assistant 宿主的 `chromeMaxWidth` 对齐，补偿高度也改成“文本实测高度”和“最小行高 * 行数”取更大值，避免只轻一点但仍残留几像素闪露
- `ChatScreen.kt` 继续收口了前馈预滚的时机：`preScrollStreamingLineAdvanceIfNeeded(...)` 已从挂起式 `listState.scrollBy(...)` 改成同步 `listState.dispatchRawDelta(...)`。目的不是改补偿量，而是避免 `scrollBy` 在旧内容仍较短时先进入 scroll session、delta 被当前 layout bounds 提前截断；现在前馈偏移会和同一个 `onAdvance` 同步块里的 content 更新一起进入下一帧 layout，再看真机是否能把剩余那一层“轻微残影”压掉
- `ChatScreen.kt` 继续收口了 clean-state 发送起步观察值的写入链：共享 measure 宿主现在会在 `renderChatList(...)` 的 `SideEffect` 里，于“输入为空 + 无 focus + IME 已收起 + composer 非 settling + 未处于 sendStart lock”的稳定窗口中，直接用首个有效 `bottomPaddingPx - STREAM_VISIBLE_BOTTOM_GAP` 种下 `observedCollapsedBottomReservePx`；同时把 `composerTopInViewportPx` 那条旧观察链收窄成“只有列表侧 `latestConversationBottomPaddingPx` 还没产出时才允许写入”的启动 fallback。上一版无条件 cold-start 预热已删除，避免 clean-state 首发后把 focus/send 锁窗口里的 padding 误记成稳定 reserve

## 2026-04-19
- `ChatScreen.kt` 把右侧用户消息气泡的最大宽度从 `chromeMaxWidth * 0.8 / 432.dp` 小幅放宽到 `0.84 / 448.dp`。目的只是让长段中文别那么早换行，视觉上更舒展一点；当前仍保持用户气泡是右侧消息气泡，不把它放大到接近整屏
- `ChatScreen.kt` 补了一刀用户消息气泡对比度：在聊天页底色改成中性浅灰后，用户消息原来的 `#F4F4F7` 底色和页面背景太接近，边界开始发糊。当前已把用户气泡改成纯白底，并补了一条极轻的描边，让右侧用户消息在浅灰页面上重新有清晰边界；这刀只动用户气泡样式，不改 assistant 文本区、工作线或滚动链
- `ChatScreen.kt` 修正了聊天页最外层“点空白处收键盘”的手势判定：现在会先区分轻点和拖动，并同时检查手势起点/终点是否都在输入框外。输入框内部长文本上下滑动、以及从输入框内部起手的拖动，不再误触发 `clearFocus + hide keyboard`，专门压“长文本编辑时一滑就收输入法、光标位置跟着乱跳”的问题
- `ChatComposerPanel.kt` / `ChatScreen.kt` 继续做了一轮输入区纯样式收口：聊天页底色统一改成中性浅灰 `#F6F7F8`，输入框保持纯白；外壳阴影从均匀 `shadow()` 收成偏下沉的双层 `dropShadow()`，让下边立体感更重；同时把输入框常态高度抬到 `92/96dp`，长文本上限放到 `232/248dp`，`maxLines` 提到 8。当前这刀仍只动外观和长文本增长上限，不改工作线、滚动链、发送期 `bottomPaddingPx` 锁或 finalize 主链
- `ChatComposerPanel.kt` / `ChatScreen.kt` 先做了一版“只换壳子”的输入区重排：宿主白底去掉，输入框改成更接近悬浮卡片的圆角浮层，加号收进左下、发送键收进右下，并同步调整了输入区尺寸与描边/阴影参数。当前这刀只动 composer 外壳与尺寸参数，不改单次 `requestScrollToItem(index, offset)`、发送期 `bottomPaddingPx` 锁、工作线算法或 finalize 主链；工作线与底部免责空白仍继续跟随 composer 的真实测量高度
- `8fb410f` 已回退 `1cdbf23 Tighten streaming line promotion gate`。当前冻结基线继续保留 `ff4480f` 的 strict follow gate 和 `283f118` 的基础显示门闩；“工作线下面下一行提前冒头 / 一闪一消失”仍按未收口风险处理，明天若继续会诊就从这条基线出发，不再顺手多撤其他滚动链修复
- `ChatScreen.kt` / `ChatScrollCoordinator.kt` 把 streaming follow 的“是否已经回到工作线”判断从发送起步 release gate 里拆开：原 `isNearStreamingWorkline()` 继续给 sendStart 保护释放等宽容差消费者用，但新增了只给 follow suppression 使用的 `isAtStreamingWorklineStrict()`，把上容差从 `assistantLineStepPx`（一整行高度）收到 `BOTTOM_POSITION_TOLERANCE(16dp)`。这样代码不再主动允许工作线下方先露出一整行才开始 follow；发送起步 release gate 则保持原宽容差，不把已收住的发送保护重新打坏
- `ChatStreamingRenderer.kt` 新增了一个只落在显示层的 streaming 行级门闩：当 `buildStableStreamingLineBuffer(...)` 第一次测出“上一行升格为 stable、下一行开始成为 activeLine”时，渲染层先继续保留上一拍已经显示出来的整组行结果，等 activeLine 后续再次真实吐字时再放行新的整组 `stableLines + activeLine`。这刀只改 renderer 单文件，不改 `ChatScreen.kt` 的 `revealMode = Free`、`onTick = {}` 或 `streamingLineAdvanceTick` 接线，专门压“工作线下面下一行提前冒头、一闪一消失”的行级 reveal 问题，同时避免恢复旧 fresh-line lock preview 的锁空串塌陷
- 静态态贴底精度继续做了最小一刀：`ChatScreen.kt` 把 `STATIC_BOTTOM_POSITION_TOLERANCE` 从 `1.dp` 收到 `0.dp`，只取消“代码主动允许 1dp 偏差存在”这件事，不动工作线、`STREAM_VISIBLE_BOTTOM_GAP`、bounds 取源和现有 `alignChatListBottom()` 主链。当前策略是先用最小风险的容差收口看能否压掉“最后一丝”，若真机上仍残留，再单独评估 Float 精度，而不是直接上 scrollBy 探针
- 静态态贴底又收紧了一刀：`ChatRecyclerViewHost.kt` 已移除正向列表尾部那颗额外的 `1dp` footer spacer，`ChatScreen.kt` 里的静态到底容差也继续收紧；首屏历史贴底、完成态归位和静态回到底部按钮不再把这颗尾项误当成“还没到底的真实内容”，专门压“看起来只差一丝、往上还能轻轻扒一点”的残余体感，同时不动工作线以下真正要保留的 `STREAM_VISIBLE_BOTTOM_GAP`
- assistant 失败态继续补齐到 0 token 场景：`ChatScreen.kt` 现在不会再把“首 token 前就失败”的 assistant 直接删掉并只弹顶部 hint，而是会保留对应的 assistant placeholder item、写入 failed assistant state，并允许本地 snapshot 持久化这类“空内容但 failed assistant”的 item。这样切后台 / 杀进程 / 重进后，`回复未完成 / 重试` 仍然有稳定锚点
- `docs/runbooks/chat-ui-regression.md` 当前已补上“切后台 / 杀进程后，完成态正文和失败态 footer 都必须恢复”的回归项，后续真机回归需要明确覆盖 completed assistant、`未发送/重发`、`回复未完成/重试` 这 3 类场景，避免只测正文不测 footer
- 本地聊天窗口持久化继续扩成 snapshot 口径：`ChatScreen.kt` 当前不再只落 `List<ChatMessage>`，而是把消息正文、failed user state、failed assistant state 一起保存到同一个本地 snapshot；读取时继续兼容旧数组格式缓存。这样 `未发送/重发`、`回复未完成/重试` 这两类 footer 在切后台、杀进程、重进后也能跟着消息一起恢复，不再只剩正文或只剩用户消息
- 带后端模式下的首屏 hydrate 也补了一刀：`SessionApi.getSnapshot()` 回来的远端历史如果还没覆盖本地失败尾巴，`ChatScreen.kt` 会把这些本地失败消息和 failed-state metadata 一起并进 hydrated snapshot，而不是让远端快照无脑把它们擦掉；同时启动时的 trailing recoverable user 也会跳过本地 failed user，避免把“未发送”误当成需要对远端做 assistant recovery 的正常尾轮
- 发送起步高度基准继续收窄：`ChatScreen.kt` 普通发送且会收口 composer 时，`conversationBottomPaddingLockPx` 不再吃当前多行输入框量出来的 `stableComposerBottomBarHeightPx`，也不再吃拍脑袋的 collapsed 常量；运行时会在“输入为空 + 无 focus + IME 已收起 + composer 非 settling”的稳定收口窗口记录真实底部 reserve（`observedCollapsedBottomReservePx`），发送起步优先直接复用这份观察值，`requestScrollToItem(index, offset)` 也继续从这份 lock 反推最终工作线，专门压“小球长文本更高、短文本更低”的现象
- 为了减少冷启动第一次发送时观察值还没采到的风险，`ChatScreen.kt` 当前又补了一层更早的预热：只要页面处于“输入为空 + 无 focus + IME 已收起 + composer 非 settling + 未处于 sendStart lock”的稳定收口窗口，就会优先从共享 measure 宿主已经拿到的 `latestConversationBottomPaddingPx` 预热 `observedCollapsedBottomReservePx`（减去 `STREAM_VISIBLE_BOTTOM_GAP`）；`composerTopInViewportPx` 那条旧观察链继续保留，作为后续校准
- 静态态贴底精度继续收口：`ChatScreen.kt` 当前把“首屏历史贴底 / 完成态归位 / 静态回到底部按钮”的到底容差从 streaming 工作线命中带里拆开，改用更紧的静态容差；专门压“已经到底但还能再往上扒出一丁点空白”的感觉，不动 streaming 过程中的工作线跟随口径
- 本地聊天窗口持久化补了一刀：`ChatScreen.kt` 的 `persistableMessagesSnapshot()` 不再把 `pendingStreamingFinalizeMessageId` 对应的 assistant 当成 transient streaming item 过滤掉。两阶段 finalize 第一阶段一旦已经把 completed 内容写回 `messages`，不论是正常 finish 还是切后台同步收口，本地落盘都会带上这条 assistant，专门修“明明生成过、切出去回来记录里只剩用户消息”
- 发送起步工作线又补了一刀同源修正：`ChatScreen.kt` 里 `commitSendMessage()` 在选定这次要锁给 `LazyColumn` 的 `conversationBottomPaddingLockPx` 后，会直接用这份锁值反推本次 `requestScrollToItem(index, offset)` 的最终 offset，不再让列表实际吃进去的是“锁定 padding”，而发送起步 offset 还继续沿用另一条预先派生的工作线快照；`pendingStartAnchorScrollOffsetPx` 只保留为锁值拿不到时的兜底
- 历史规则已单独落库：仓库当前明确把“发送瞬间小球首发位置”和“底部空白 / 完成态上跳”拆成两类问题。旧历史里多次出现“小球一上抬就出事”，本质是当时发送起步抖动、`conversationBottomPaddingPx` 连续变化和 finalize 几何链都没收口；现在发送微抖已由列表 `bottomPaddingPx` 锁压住，底部空白也已由两阶段 finalize 收口，所以这两类问题理论上已经拆开。只是按最新产品回归，小球一旦上抬到中部以上，失败态和短文本收口又会变差，所以当前仍固定回工作线
- “中部偏上首发锚点”方案已回收：`ChatScreen.kt` 恢复由 `sendStartWorklineBottomPx` 驱动 `pendingStartAnchorScrollOffsetPx`，发送瞬间的小球重新稳定贴在工作线；发送期 `bottomPaddingPx` 锁、streaming / finalize / 首屏历史态主链都保持不动
- Android 回归协作口径补充：后续这台机器上的 Android 改动默认只做 `./gradlew.bat :app:compileDebugKotlin` 编译验证，不再主动执行 `:app:installDebug`；真机安装与回归默认由用户自行完成，只有用户明确要求 Codex 装机时才执行安装
- 按真机逐帧 trace 继续收口发送微抖：`ChatScreen.kt` 当前在发送事务里新增了“列表 `bottomPaddingPx` 快照锁”，并已继续把普通发送的锁值从“发送瞬间旧 padding”修正成“最近一次观察到的稳定收口 reserve + gap”这一份终态值；如果观察值还没采到，才回退现有 `stableComposerBottomBarHeightPx / bottomBarHeightPx` 兜底链。现在只有 `collapseComposer = false` 的不收口发送才退回当前 `conversationBottomPaddingPx` 快照；`sendStartAnchorActive` 期间 `ChatRecyclerViewHost` 只继续把这份锁值喂给 `LazyColumn` 的 `bottomPaddingPx`，保护窗口退出后再退回实时值。它只锁列表 contentPadding 消费点，不锁 workline / overflow / 全局 reserve
- 本地临时日志已完成一次“加日志 -> 真机抓逐帧 trace -> 立即删除日志”的排查闭环：当前已确认发送微抖发生时 `sendStartAnchorActive` 仍为 `true`，`followStreamingByDelta(...)` 一次都没执行；真正先连续变化的是 `composerTopInViewportPx`、共享 measure 宿主给出的 `conversationBottomPaddingPx`、`streamingWorklineBottomPx` 与 `firstVisibleItemScrollOffset`。本轮没有把临时日志留在主链里，运行时代码已恢复到排查前状态
- 继续收口“发送瞬间先上再下”的微抖：`ChatScrollCoordinator.kt` 的发送起步保护释放条件已从“命中工作线容差就放行”收紧成“命中工作线且 composer 已稳定后，再连续一帧命中才放行”。当前不改发送顺序、不改几何计算，只延后 `sendStartAnchorActive` 的真正 release，避免 `requestScrollToItem(index, offset)` 首次命中后，composer 仍在 settling 或刚稳定的边界帧就过早让 follow delta 接管
- 按最新真机反馈，首次进入聊天页且本地有历史时的贴底已确认收口：`ChatScreen.kt` 的 `startupLayoutReady + isWithinBottomTolerance()` 首屏重试链、启动窗口临时 realtime composer geometry，以及 `ChatScrollCoordinator.kt` 里 `scrollToBottom(false)` 非动画路径的正向 hard bottom reposition 这三处修正继续作为当前真相保留
- 文档口径继续收平：根 `AGENTS.md`、`docs/project-state/current-status.md`、`docs/project-state/open-risks.md` 与 `docs/runbooks/chat-ui-regression.md` 已统一把当前主问题收敛为“发送瞬间整块消息区轻微上下抖一下”；首屏贴底改为已收口事项，完成态跳到长文本开头和发送后输入框回缩改为回归观察项
- 历史记录：当时会诊规则曾按用户偏好收口为默认整理给 Claude 的自包含短稿，不再继续把 Gemini 当成 Android UI 默认会诊对象；该口径已在 2026-04-27 后续条目中被“小米 / MiMo 优先”替代
- 基础设施记忆继续补齐：新增 `docs/runbooks/infra-readiness.md`，把“服务器还没买”这一现状对应到正式云资源采购前检查单；`pending-decisions.md` 与 `open-risks.md` 也同步补上“正式云资源首版怎么落 / 正式云资源尚未采购”两项，避免后续第一次上云时再靠聊天记录回忆

## 2026-04-18

- 正向底座的 `scrollToBottom(false)` 非动画路径已收紧：如果最后一条底边仍未进入可视区，会先做一次 hard bottom reposition，再交给 `alignChatListBottom()` 做工作线精修
- 首屏首次贴底 effect 改为等 `startupLayoutReady` 后持续重试，只有命中底部容差才把 `initialBottomSnapDone` 记完成，避免“一次滚完就关门”
- 启动窗口允许临时参考一次 realtime composer geometry，把首屏最后几像素压准到工作线；命中后立即退回静态口径，不把历史区输入框联动带回来
- `LazyListState` 启动若本地有历史，会先从最后一条历史消息起步，并关闭 `rememberSaveable` 的旧滚动恢复，减少首几秒落在长文本中段造成的重影 / 闪动
- `scrollToBottom(false)` 与 finalize 收口都不再使用 `requestScrollToItem(lastIndex)` 这类 top-anchor，把“跳到长文本开头”改为复用现有静态底线主链
- `ChatScrollCoordinator.kt` 的 streaming AutoFollow 改为按“当前内容底边 - 工作线底边”的单次 delta 做 `scrollBy` 微调，减少 fake streaming 过程中的重叠闪烁
- 首屏显示链继续清理：列表 / 欢迎语 reveal 只再依赖 hydration barrier，stale streaming runtime 在冷启动 reset 时主动清空，继续收口首屏白屏与空列表

## 2026-04-17

- `ChatScreen.kt` 的消息列表与 composer 改为 `SubcomposeLayout` 共享 measure 宿主，同拍产出底部 reserve，不再完全依赖 `composerTopInViewportPx` 异步回写链
- `sendUiSettling` 收紧成只覆盖发送起步同步窗口，专门压“发送长文本后输入框有时不回缩”的时序竞态
- `ChatStreamingRenderer.kt` 统一最外层宿主、删除 fresh-line lock 预览、把 block 间距改成独立 `Spacer`，继续减少 streaming 下掉与完成态微重排
- 两阶段 finalize 进一步收紧为“等 settled fresh bounds 到位后再切 streaming 状态”，后台期间暂停等待，继续压完成后上跳和底部留白
- 根 `AGENTS.md` 新增已修复问题禁改清单与会诊稿规则，要求外部会诊稿默认自包含代码片段、限制条件和已排除项

## 2026-04-16

- 发送链撤掉 `withTimeoutOrNull` 延后收口实验，回到“输入框瞬间清空回缩 + placeholder 同事务插入”的单一发送主链
- 聊天 UI 当前热点与 runbook 开始沉淀进仓库，`scripts/check_project_memory.py` 与 CI 已接入项目记忆检查
- streaming block 改为 unified host，外壳 key 收口为稳定 block index，减少流式阶段 remount
- 普通 idle / 历史浏览状态下不再让列表长期追实时 composer 几何，避免历史区和输入框重新联动
- 2026-06-08：继续收紧正式上线口径。后台总览和监控里的 `chat_users`、`support_users` 改为只统计已收敛到 `app_accounts` 的账号用户，不再把迁移期老 `user_id` 混进运营盘子；Android 侧把菜单、帮助与反馈、会员中心等长期高频文案统一成更正式、可长期复用的默认版，减少后续反复改字。

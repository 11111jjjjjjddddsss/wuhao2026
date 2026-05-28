# 近期重要变更

说明：本文件默认只保留最近 20 条重要变更；当前因 4 月聊天 UI 主链多次大切换，暂保留较长历史方便排障，更早内容仍以 git 历史和 ADR 为准。
说明补充：本文件允许保留旧方案的历史记录；旧条目里若出现“反向列表 / requestScrollToItem(0) / asReversed()”或旧会诊对象选择等表述，默认都只是历史过程，不代表当前运行时真相或当前协作口径。当前真相始终以根 `AGENTS.md` 和 `docs/project-state/current-status.md` 为准。

## 2026-05-28

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

- 按用户确认重写 B/C 记忆提示词：B 层从偏农业病例承接调整为“全场景短期工作记忆”，明确目的是真实保留最近对话主线，让下一轮能自然接得住；C 层从“用户长期农业记忆”调整为“全场景用户长期记忆”，同时覆盖农业长期档案、通用用户画像、稳定偏好和历史经验，但明确不做通用知识库或病例流水账。同步更新根规则、项目状态、待决策、风险和上线巡检文档；后端字段、触发频率、模型参数、A/B/C 注入链路均不变。

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

- 巡检“今日农情”链路并对照阿里云百炼官方文档校准参数：确认用户侧 `GET /api/today-agri-card` 只读当天 ready 缓存，缺失 / pending / failed 时 Android 静默不展示，不会在用户打开 App 时临时触发模型；内部 `POST /internal/jobs/today-agri-card/generate` 由 `DAILY_AGRI_JOB_SECRET` 保护并用 `daily_agri_cards(day_cn, scope)` 数据库 lease 防同一天并发生成。今日农情走 DashScope 原生 Generation 的 `qwen3.5-plus`，显式 `temperature=0.8`、关闭思考、强制联网搜索、`search_strategy=max`、`enable_source=true`、`freshness=7`；后端要求 JSON、严格 3 条、https、近 7 天、URL 来自搜索来源、可信域名、过滤广告 / 导购 / 泄露词 / 标题党并对近 7 天 URL / 标题去重。Android 只把 ready 卡片作为 `ChatTimelineItem.TodayAgriCard` 展示层插入，不进 `messages`、本地快照、A/B/C、摘要或扣次。本轮不改业务代码，只把定时任务、SLS 告警、后台状态页、同一事件语义去重和可信域名偏严风险写入 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)、[today-agri-card.md](D:/wuhao/docs/runbooks/today-agri-card.md) 和风险记忆。

- 巡检“B/C 记忆与模型调用”链路：确认主对话完成归档后才异步触发 `SummaryService`，B 层 Free / Plus 每 6 轮、Pro 每 9 轮，用当前 A 层窗口 + 旧 B 摘要生成短期工作记忆；C 层每 20 轮，用 `session_round_archive` 最近 20 轮完整问答 + 旧 C 生成用户长期农业记忆，不再用 6/9 轮 A 窗口冒充长期输入。B/C 均走 `qwen3.5-flash`、非流式、`temperature=0.8`、关闭思考、不联网；模型失败、超时、归档不足或写回失败都会保留 `pending_retry_b/c`，写回带 `round_total` 校验，旧快照不会覆盖新轮次。旧 `/api/session/b`、`/api/session/c`、`/api/session/round_complete` 仍只返回 410，Android 没有摘要模型直连。本轮不改提示词和触发频率，只把多实例前需补摘要数据库 claim / lease、SLS 观察项和只读查询项写入 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。

- 巡检“主聊天与图片发送”主链：确认 Android 端仍只通过后端 `/api/chat/stream` 发起文字 / 图片 / 图文混合问诊，图片先进入 App 私有 `composer_images` 稳定副本并经 `/upload` 换成同一公开基地址下的 `https /uploads/*.jpg`，后端再次校验图片 URL 后才进模型；带图发送的唯一 WorkManager 兜底、后端 `chat_stream_inflight` 同用户活跃流约束、归档成功后才发 `[DONE]` 和扣次、`/api/session/snapshot` 历史恢复、`/api/session/clear` 删除历史 409 防活跃流都和当前口径一致。没有发现旧 Android 直连模型、旧 `/api/session/round_complete` 主链、旧 active-zone、旧图片手势或旧上传通道并存；本轮只把 `ImageUploader.kt` 里“上传 OSS”的过期注释改为当前真实“上传后端 /upload，未来 OSS 只能由后端接入”，并把买服务器后必须验证公网 https 图片链、单实例 / OSS、弱网多图、后台恢复和 SLS 指标写入 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)。

- 巡检“服务协议 / 隐私政策 / 风险提示”组：确认当前设置页只有一个“服务协议”目录入口，下面 6 个本地内置二级页面（用户协议、隐私政策、第三方信息共享清单、个人信息收集清单、应用权限、风险提示）都走设置页右进左出页面栈；没有发现旧 WebView、外部协议网页、旧平铺三行入口或用户可见具体模型平台名残留。Manifest 当前只声明 `INTERNET / ACCESS_NETWORK_STATE / REQUEST_INSTALL_PACKAGES`，正文口径和当前不申请定位、App 相机、相册 / 存储读写、录音、通讯录、短信、通知权限一致；同时补充 Android Q+ 拍照成功后会把原始照片另存到系统相册 `Pictures/农技千查`，避免只写 App 私有目录导致保存位置说明不完整。新增 [legal-privacy.md](D:/wuhao/docs/runbooks/legal-privacy.md)，并把买服务器后必须补的真实云服务商、第三方服务、数据保存期限、账号注销 / 查询 / 删除入口、备案号和隐私政策 URL 写入巡检记录。

- 巡检“检查更新 / 自有 APK 分发”链路：确认当前没有应用商店跳转、浏览器下载或旧占位方案并存，主链是 Android 设置页请求 `GET /api/app/update`，后端由 `APP_ANDROID_*` 环境变量返回 https APK，Android 下载到 cache 后通过 FileProvider 调起系统安装页。后端新增可选 `APP_ANDROID_APK_SHA256` 并透出 `apk_sha256`；Android 下载后新增最终 https、文件大小、SHA-256、包名和 `versionCode` 校验，避免错包、坏包、半截包或低版本包进入系统安装页。同步更新 [app-update.md](D:/wuhao/docs/runbooks/app-update.md)、[pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md) 和风险记忆，明确 APK 建议放 OSS / CDN / 静态 HTTPS，发布时记录文件大小、SHA-256、签名指纹，回滚只能停更或发更高 `versionCode` 修复包。

- 巡检礼品卡占位链路：确认当前没有后端兑换接口、兑换码旧链路或会误改会员权益的并存方案，Android 礼品卡页输入后只提示“礼品卡功能后续接入”，不调用后端、不发权益、不假弹真实成功；debug-only “兑换成功 / 确定”仅是未来成功态样式预览。新增 [gift-card.md](D:/wuhao/docs/runbooks/gift-card.md)，并在 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md) 和风险记忆里固定后续真实接入原则：后端是唯一真相，真实接入前要先补礼品卡主表、兑换记录、幂等兑换接口、后台生成 / 发放 / 作废 / 查询、审计、限流和日志。

- 继续按“一个功能一个功能”巡检帮助与反馈：确认当前没有新旧方案并存，用户侧主链是 `/api/support/summary`、`/api/support/messages`、`/api/support/read`，后台回复先走 `SUPPORT_ADMIN_SECRET` 保护的 `/internal/support/*`，Android 红点进入页后会标记已读，附件面板返回优先级和 IME padding 已按现有主链收口。新增 `server-go/internal/app/support_test.go` 覆盖 payload 校验、图片 URL JSON 序列化和内部后台 secret 校验；同步把公开生产前必须补账号 token / `AUTH_STRICT=true`、多实例前必须上 OSS 或单实例、后续管理后台要补账号权限 / 审计 / 未处理列表、账号注销要明确帮助与反馈消息和图片保存删除规则，写入 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)、[support-feedback.md](D:/wuhao/docs/runbooks/support-feedback.md) 和项目风险记忆。

- 买服务器前功能巡检开始沉淀到 [pre-server-feature-audit.md](D:/wuhao/docs/runbooks/pre-server-feature-audit.md)：首轮收口会员中心 / 额度体系和 Go 后端高并发 / 性能边界。会员/额度当前确认没有新旧方案并存，Android 只做“支付暂未接入”占位展示，后端仍是 `/api/me`、`quota_ledger`、`topup_packs`、`upgrade_credits` 等真相；真实收费前必须补账号 token、生产 `AUTH_STRICT=true`、正式支付回调验签和对账。后端高并发结论是 Go 语言本身不是当前瓶颈，首版单实例可跑早期；多 SAE 实例前必须先处理本机图片存储迁 OSS、迁移抢跑、本进程限流 / B-C running guard 等边界。`server-go/internal/app/mysql.go` 同步把数据库连接池从固定 10 个连接改为可用 `MYSQL_MAX_OPEN_CONNS / MYSQL_MAX_IDLE_CONNS / MYSQL_CONN_MAX_IDLE_SECONDS / MYSQL_CONN_MAX_LIFETIME_SECONDS` 配置，默认值保持原样，并新增单测锁住默认值、环境变量覆盖和 idle 不超过 open 的回退逻辑。

- 新增 [go-live-plan.md](D:/wuhao/docs/runbooks/go-live-plan.md)，把下一阶段上线推进顺序固化为：先定 App 名称 / 图标 / 包名 / 签名 / 协议 / 软著材料，买域名和中国内地云资源后立即启动 ICP / App 备案；手机号登录、SAE、RDS、OSS、SLS、帮助与反馈、检查更新、模型 Key 池和真机联调在备案等待期间并行推进；备案通过后补备案号、正式域名 / HTTPS、公安联网备案和应用商店物料。同步更新 runbook 入口、当前状态、待决策和风险记忆，避免后续把“做完手机登录再备案”误当成主顺序。

- 随机巡检所有模型调用链后，给 B/C 摘要提取补 60 秒超时保护：`SummaryService` 调 `qwen3.5-flash` 时用独立 `context.WithTimeout` 包住非流式摘要请求，避免网络层极端挂起导致同一用户同一层的进程内 `running` guard 长期占用、后续摘要一直跳过。超时仍按失败处理，保持 `pending_retry_b / pending_retry_c`，后续轮次完成后继续补提取；主对话 SSE 和今日农情链路不跟随改全局 `http.Client.Timeout`，避免影响流式回答。新增单测覆盖摘要超时后会释放 running guard。

- `server-go/internal/app/bailian.go` 将百炼模型 Key 池从单纯 `DASHSCOPE_API_KEYS` 逗号轮询扩展为 `DASHSCOPE_API_KEY_1/2/3` 三个独立账号槽位，并继续兼容旧 `DASHSCOPE_API_KEY` 和 `DASHSCOPE_API_KEYS`；后端会自动去重、轮询，主对话、B/C 摘要和今日农情共用同一池。模型请求打开阶段若遇到 `401 / 403 / 429` 或带限流 / quota 语义的 `400`，会在流开始前切下一把 Key，并对触发限流的 Key 做 60 秒冷却；SSE 一旦成功打开，不在同一条回复中途切 Key。新增单测覆盖专用槽位去重、429 切 Key 和冷却跳过；新增 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md) 记录同一阿里云主账号多 Key 共享限流、扩容必须用不同主账号 Key 的运维口径。

- `server-go` 将 C 层从“旧 C + 当前 A 窗口 6/9 轮”的滚动摘要输入，改为每 20 轮从 `session_round_archive` 读取最近 20 轮完整问答并融合旧 C 重写；归档不足 20 轮时不凑合提取，保持 `pending_retry_c`，后续轮次完成后继续补提取。C 层提示词同步收口为“用户长期农业记忆”，只保留用户自己的长期农业画像、稳定种植档案、历史高频问题、用户纠正和踩坑经验，明确不做通用农技知识库、不保存通用病害常识、标准防治流程、剂量配方或品牌背书。B 层仍按 Free / Plus 每 6 轮、Pro 每 9 轮用 A 窗口更新；B/C 失败仍保留 `pending_retry_b / pending_retry_c`。同步更新根规则、项目记忆和后端单测。

- `server-go/assets/b_extraction_prompt.txt` 将 B 层从偏“累计背景摘要”收口为“短期工作记忆”：优先保留当前主线、已确认事实、用户纠正、新增信息、仍有效判断倾向、待确认关键点和后续必须承接的限制条件；农业问题保留作物 / 地块 / 症状 / 用药用肥 / 图片证据等短期判断线索，非农业问题保留当前事务目标和已确认事实，但不编造账户、订单、扣费、剩余次数或后台处理结果。后端注入标签同步改为“B层短期记忆（仅供参考）”。数据库字段、B 层触发频率、C 层摘要提示词、A 层滑窗、模型名称和 `temperature=0.8` 均不变。

## 2026-05-20

- `server-go/assets/system_anchor.txt` 继续按用户确认小幅调整 C/F 边界：会员问题可回答通用产品规则，涉及个人档位、剩余次数、扣减记录、订单、支付或权益异常时不编造账户信息；App 使用问题可按已知入口给简短通用说明，持续故障 / 个人数据再引导“帮助与反馈”；商业中立允许按成分、用途、适用场景、风险点和标签信息做技术性比较，但不做品牌背书或导购。礼品卡不写入锚点，农资安全新增硬条款本次不加，B/C 摘要提示词不动。

- 随机巡检后补两处低风险收口：`HamburgerMenuSheet.kt` 的帮助与反馈页在用户选图后未发送即离开时，会清理仍挂在草稿里的私有 `composer_images` 图片，避免无引用临时图长期积累；`bailian_test.go` 扩展主对话和今日农情模型请求断言，锁住关闭思考、搜索开关、搜索策略、强制搜索和来源字段，避免后续参数口径误回流。业务链路、B/C 摘要提示词和模型温度不变。

- `server-go/assets/system_anchor.txt` 按用户最终确认版本收口主对话锚点：保留 A-F 六段结构和“信息不足不能下定论 / 图片先客观详细描述 / 单次输出≤1000字”等核心硬约束；删除“用户话语处理”、图片问诊初步线索、内部思考 / 调取历史重复说明，以及图片上下文、农资标签、品牌商品等用户点名删除条款；补强历史摘要不是定论、同一作物 / 同一地块才承接、文件上传引导、问题不清先给临时安全建议、混配 / 浓度 / 亩用量先核关键参数、不要机械套模板和安全提醒不冗长。B/C 摘要提示词本次不动。

- 全仓库产品名称口径统一为“农技千查”：主规则、README、主对话锚点、今日农情系统提示、App 内协议 / 隐私 / 风险文案和运维 / 项目记忆文档同步替换产品名；服务提供者公司主体仍按用户提供信息保留为“北京农技千问科技有限公司”，不跟随产品名机械改动。

- `server-go/internal/app/bailian.go` / `summary.go` 统一真实模型调用温度：新增后端统一常量 `unifiedModelTemperature=0.8`，主对话 `qwen3.5-plus`、B/C 摘要 `qwen3.5-flash` 和今日农情 `qwen3.5-plus` 生成全部显式使用该温度；`top_p / max_tokens / penalty` 等其他采样参数继续不显式设置，走模型服务默认值。补单测锁住主对话、摘要和今日农情请求里的温度字段；同步更新根规则和当前状态记忆。

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

- `HamburgerMenuSheet.kt` 接入本地内置“服务协议 / 隐私政策 / 风险提示”三份二级页：设置页点对应入口不再提示占位，而是右进左出打开正文；协议主体按“北京农技千问科技有限公司”和 `465989879@qq.com` 填写。隐私政策按当前真实权限写明只声明网络、网络状态、安装更新 APK 相关权限；照片走系统 Photo Picker，拍照走外部相机 + FileProvider 临时 URI，当前不申请定位、相机、相册 / 存储读写、录音、通讯录、短信或通知权限，当前也不做 App 外通知 / 推送；同时补充模型处理、本地缓存、第三方 / 系统能力、用户权利和未来可能权限边界。服务协议补 AI 生成内容标识、礼品卡规则、禁止行为、知识产权和责任限制。风险提示压成短而明确的 9 条，覆盖误判、图片局限、农资标签、官方事项、时效信息、生产损失、未成年人、紧急情况和非农业用途。debug-only 预览面板同步新增三份页面预览。只改协议/隐私/风险入口和本地文案，不改会员、礼品卡、后端接口或聊天滚动链。

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

- `ChatScreen.kt` 继续收口今日农情卡片头部：去掉“今日农情”标题右侧的卡片日期，只保留每条农情底部自己的来源和发布日期，避免顶部日期与条目日期重复。只改卡片展示文案密度，不改后端生成、点击跳转、消息列表主人或滚动锚点。

- `ChatScreen.kt` 微调今日农情卡片展示：外层横向边距从 40dp 收到 20dp，去掉每条农情左侧 1/2/3 黑色编号圆点，让固定三条资讯以标题、摘要、来源日期为主，避免预览和正式聊天页里显得过窄、过像编号列表。只改今日农情 UI 样式，不改生成接口、后端校验、点击跳浏览器、聊天消息主链或滚动工作线。

- 多代理优化今日农情提示词：把首句从“今天值得看的”改为“面向中国农业生产经营场景”，删掉会诱导前端露馅的“参考意义”口径，新增农业实用价值排序、自然资讯口吻、反标题党、反网页注入和 JSON 字符串约束；摘要要求只写事实、数据和直接农业影响，禁止“值得看 / 对农户有用 / 参考意义 / 根据搜索结果”等元表达。只改生成提示词和记忆 / runbook 口径，不改接口、后端发布校验、Android 展示和聊天滚动链。

- 多代理审查“今日农情”后做低风险加固：后端发布校验不再接受未来 `published_date`，模型输出必须显式带 `card_name = 今日农情`，失败原因按 UTF-8 安全截断；Android 读取端只在接口 `status=ready` 且正好 3 条有效 item 时展示卡片，渲染端也改为正好 3 条才渲染，避免 pending / failed 或多条混杂数据误展示；runbook 示例同步改成 3 条 item，继续保持今日农情不进 A/B/C、不扣问诊次数、不由用户打开 App 触发生成。

- `server-go` 今日农情生成链路补近 7 天去重：生成前读取过去 7 天已 ready 的 `daily_agri_cards`，把标题 / 摘要 / 来源 / 链接喂给 `qwen3.5-plus` prompt，明确要求不重复同 URL、同标题或同一事件；服务端解析结果时同步硬过滤过去 7 天和当天候选里的重复链接 / 重复标题，过滤后不足 3 条仍不发布新卡片。提示词也收紧为优先有直接农业参考价值的事实类农情，少选空泛会议、一般部署和表态新闻。

## 2026-05-11

- `server-go` / `ChatScreen.kt` / `SessionApi.kt` 接入首版“今日农情”：后端新增 `daily_agri_cards` 迁移、`GET /api/today-agri-card` 只读接口和带 `DAILY_AGRI_JOB_SECRET` 的内部生成接口，生成链路使用 DashScope 原生 Generation 调 `qwen3.5-plus`，显式关闭思考模式，强制联网搜索 `search_strategy=max` 并返回来源；服务端用数据库 lease 防并发，并校验 JSON、3 条 item、https、近 7 天、来源 URL 必须来自搜索结果、可信域名和广告 / 泄露词过滤。Android 在历史 hydrate 后拉取卡片，作为 `ChatTimelineItem.TodayAgriCard` 插入聊天列表展示层，三条卡片点击打开来源 URL；它不是 `ChatMessage`，不进入本地聊天快照、A/B/C 上下文、归档、摘要或问诊扣次。debug-only 文案预览面板同步新增“今日农情”样式预览；缺失 / pending / failed 时前端静默不展示，不阻塞聊天页。

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
- `server-go/internal/app/summary.go` 将 B / C 摘要模型从 `qwen-flash` 切到 `qwen3.5-flash`，并在摘要请求里显式传 `extra_body.enable_thinking=false`；主对话 `qwen3.5-plus` 已经显式关闭思考模式，本次保持不变。新增后端单测锁住摘要模型名和关闭思考参数；同步更新根规则和项目记忆，删除“后续评估切到 Qwen3.5-Flash”的待决策项；不改主模型、摘要触发时机、写回校验、扣次顺序、Android UI 或聊天滚动链。
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
- `ChatScreen.kt` / `ChatStreamingRenderer.kt` 对 streaming 渲染做小范围体验收口：把 reveal batch 从 4 个 token / 40ms 收到 2 个 token / 28ms，让中文通常 1 到 2 个字一拍，减少“几个字一坨蹦出来”的呆感，同时避免每个汉字都单独重组导致长回复压力过大；聊天列表左右 padding 小幅加大，让不同宽度手机上正文边缘有更多呼吸感；同时在 renderer 内增加标准 Markdown 表格兜底降级，把 `| header |` 这类表格转成普通项目行文本，避免模型偶发输出表格时撑乱布局。滚动链、96dp 工作线、SideEffect 同帧锚定、两阶段 finalize 均未改动。
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

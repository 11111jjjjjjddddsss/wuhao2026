# 当前状态

最后更新：2026-05-14

## 项目概况

- 项目：农技千问
- 目标：面向农作物问题提供农业技术参考建议，不提供绝对诊断
- 当前仓库两条主线：Android 客户端 `app`，Go 后端 `server-go`

## 当前代码真相

- Android 端当前使用 Jetpack Compose 聊天界面，不再依赖 WebView 模板页面
- Android 客户端已清理旧直连模型链：不再包含 `QwenClient.kt` / `ModelService.kt` / `ModelParams.kt`，也不再从 `local.properties`、`BAILIAN_API_KEY` 或 Gradle property 注入模型 `API_KEY`。主对话和图片问诊只能通过后端 `/api/chat/stream` 发起模型调用
- Android Auto Backup / Data Extraction 当前已关闭并显式排除：`allowBackup=false`，同时通过 `backup_rules.xml` / `data_extraction_rules.xml` 排除 cloud backup、device transfer、credential-protected / device-protected shared preferences、files、databases 和 external 数据。本地聊天窗口快照、流式草稿、`app_ids`、旧 UI metrics 等都只作为本机运行时缓存，不允许被系统云备份 / 设备迁移在清数据 / 重装后恢复成旧 UI 状态。后端仍是业务真相来源
- Go 后端当前同时保存两类资产：`session_ab.a_json` 仍是 A 层滑窗，写入新轮次后会裁剪到 Free / Plus 6 轮、Pro 9 轮；`b_summary` / `c_summary` 仍是摘要文本；成功完成的问答轮次会额外写入 `session_round_archive`，按 30 天滚动保留，用于 UI 历史恢复和后续批量抽取。`/api/chat/stream` 允许纯文字、纯图片、图文混合；纯图片且用户未输入文字时，后端会给模型补一条内部说明，引导先基于图片可见信息做农业技术参考判断并追问必要信息，该说明不作为用户可见消息。当前尚未实现 C+ 的用户农业画像 / 用户农业档案字段
- `/api/session/snapshot` 当前继续返回 `a_json` / `a_rounds_full` 作为 A 层窗口，同时 `a_rounds_for_ui` 会优先返回 30 天内最近 30 轮 `session_round_archive`。前端本地仍会用 `LOCAL_RENDER_ROUND_LIMIT = 30` 裁 UI 窗口；换机 / 重装后只要用户身份能对上后端 `user_id`，UI 可拉到最近 30 轮业务聊天记录。当前项目还没有手机号 / 账号登录体系，实际主要依赖本机 `user_id`；若清数据后本机 `user_id` 丢失并生成新身份，则不会恢复旧记录
- Android 聊天页在后端历史模式下不再把本地 30 轮聊天窗口作为首帧同步启动数据：`ChatScreen.kt` 首次组合先用空本地快照放出页面壳，随后在 `LaunchedEffect` 内异步读取本地快照并等待远端 `/api/session/snapshot`。远端快照成功时仍以后端历史为主，并把本地失败消息 / 待恢复用户尾巴并回去；远端快照失败时才异步回退到本地窗口，避免首屏被 SharedPreferences + Gson 解析阻塞，同时不丢失败 / 中断恢复入口
- Android 普通输入框草稿当前会写入本机 `chat_ui_cache` 的 `composer_draft_*` 键：用户切 App、锁屏或后台被系统回收后，未发送文字下次进入仍会回填；发送成功 / 清空运行时会清掉该草稿。图片缩略图暂不做跨进程草稿恢复，避免本地 URI 权限和临时文件生命周期带来脏状态
- 主对话每轮都会由后端注入当前时间和地点：`chat.go` 组装 `当前时间：yyyy-MM-dd HH:mm:ss（Asia/Shanghai）；用户地点：...；地点可信度：...`。历史轮次当前也会携带 `created_at / region / region_source / region_reliability`，进入模型上下文时以前缀“历史轮次时间：...”和“历史轮次地点：...”注入，让模型知道这轮距离当前大概多久、当时大概在哪里；前端暂不显示每条消息时间戳或地点条。当前 Android 还没有定位权限 / 地区选择主链，若请求里未带 `X-User-Region`，后端只能用 IP / 未知兜底，因此“地点详细点”需要后续单独做用户地区采集。天气 API 暂不接入，后续只在确有强需求和成本预算时再评估
- 当前产品策略倾向已记录为待决策：C 层后续可能升级为 `C+ = 长期摘要 + 用户农业画像 + 用户农业档案`；在代码落地前，当前真实实现仍是现有 `c_summary`
- 基础设施首版采购倾向已调整为 `SAE + RDS MySQL`：SAE 继续适合当前无运维团队阶段，数据库首版倾向使用阿里云 RDS MySQL 以降低成本和运维复杂度；PolarDB 暂作为后续高并发 / 更高规格升级选项，不再作为个人创业首版默认采购项
- 会员有效等级由后端按 `tier_expire_at` 实时计算：Plus / Pro 到期后对 `/api/me`、每日额度、加油包购买资格、Plus 升 Pro 入口都按 Free 处理；若数据库出现 Plus / Pro 但 `tier_expire_at` 为空，也按 Free 处理，避免脏数据变成永久会员；Plus 升 Pro 时仍会把 Plus 剩余额度折成永久升级补偿次数，Pro 到期后未用完的升级补偿 / 加油包仍可按当前消耗顺序继续用于超额请求；后端扣减加油包时按扣减前已锁定的剩余次数判断状态，只有扣完最后 1 次后才把该加油包标为 `used_up`
- Android 端右上角黑色线性“圆角方框 + 加号”当前作为会员中心入口并已接入首版会员中心底部面板：打开时读取 `/api/me` 的 `tier / tier_expire_at / daily_remaining / topup_remaining / upgrade_remaining`。后端 Plus / Pro 会员周期当前为 30 天。面板标题下不再显示“次数、追问记忆和加油包”副文案；顶部只用一条轻量状态条展示今日剩余额度和当前档位，Free 只显示“基础额度”，不显示到期日期；Plus / Pro 显示到期日期；未同步或读取中时只显示“未同步 / 读取中”，不重复显示“未连接”，且套餐开通按钮置灰显示“同步后开通”，不允许在会员真相未同步时启动开通。若存在升级补偿次数或加油包次数，会在“套餐”标题后以胶囊展示“升级补偿次数 x次 / 加油包 x次”，Free 用户如果是 Plus / Pro 到期后仍剩升级补偿或加油包，也照样展示并可按后端消耗顺序继续使用。套餐区不再单列免费版大卡，直接展示 Plus / Pro 付费套餐；当前同档套餐按钮置灰显示“当前套餐”，Pro 用户看到 Plus 卡片时显示“当前为 Pro”，Plus 用户可点“升级 Pro”，Pro 用户不能降买 Plus。套餐卡片用户可见文案不展示具体 A 层轮数，只展示 Plus 25 次 / Pro 40 次、图文问题、记忆与上下文、复杂问题推理和多作物多地块复盘等产品化权益表达。Plus 升 Pro 规则在底部卡片里展示“升级 Pro 后，Plus 剩余权益会自动折成补偿次数。”；加油包文案明确 `6元/80次`、仅 Plus / Pro 可订购、永久有效、用完再续；若 `/api/me.topup_remaining > 0`，Plus / Pro 按“用完再续”置灰，Free 到期后仍有加油包余额则显示“剩余次数可用”，避免暗示 Free 可直接续订。当前支付功能尚未接入，开通 / 升级 / 订购加油包按钮只在会员面板内提示“支付暂未接入”，不会调用后端订单接口；后端开发期订单变更接口默认返回 `PAYMENT_NOT_CONFIGURED`，只有显式设置 `ALLOW_DEV_ORDER_ENDPOINTS=true` 且当前环境不是 `APP_ENV / ENV / GO_ENV = prod / production` 时才允许本地 / 内测调试；真实支付成功后的统一收口 UI 已预留为中间黑底白字卡片“订购成功 / 确定”，确认后会刷新 `/api/me` 同步会员中心数据，并在刷新到可用额度 / 补偿 / 加油包后清掉当天“次数用完”的本地发送锁。套餐到期暂不做主动提醒，到期后由后端自动按 Free 权益计算
- 当前成本核算口径已按真实代码重算：主对话是实时 SSE `qwen3.5-plus`，不吃 Batch Chat 半价；按当前公开价输入约 0.8 元 / 百万 tokens、输出约 4.8 元 / 百万 tokens 估算。`0.018 ~ 0.019 元 / 轮` 不再称为最保守成本，只作为偏重使用场景估算的一部分；当前项目记忆采用三档：真实平均约 `0.008 ~ 0.015 元 / 轮`，偏重使用约 `0.018 ~ 0.022 元 / 轮`，连续 4 图 / 上一轮图片 / Pro 满 A 层 / 长输出 / 搜索等极重场景约 `0.025 ~ 0.030 元 / 轮`。Plus / Pro 会员阶梯暂不调整；加油包调整为 `6元/80次`，同一时刻仍只允许 1 个 active 加油包。后续上线后应记录每轮实际 `input_tokens / output_tokens / search` 再校准会员毛利。
- Android 左上角汉堡按钮当前已接入首版简洁设置页：使用独立 `HamburgerMenuSheet.kt`，不做头像、不清空聊天、不接多会话历史。页面顶部不再展示 App 名或短 ID，只保留左上角返回按钮和设置入口组；入口包括“会员中心 / 账号管理 / 客服反馈 / 检查更新 / 服务协议 / 隐私政策 / 风险提示 / 退出登录”。其中“会员中心”复用现有会员底部面板并继续展示短 ID，方便后续支付 / 客服核对；“账号管理”现在进入右进左出的轻量子页，先放“手机号 / 退出设备 / 注销账号”三项，手机号显示未绑定，各项只给后续接入轻提示；账号管理子页的左上角返回和系统返回键都会先回到汉堡菜单页，只有菜单首页的返回才关闭汉堡页；其余入口先显示“功能后续接入”或“登录功能后续接入”的轻提示，等待后续真实账号、一键登录、协议页、隐私页、客服反馈链路和检查更新接入；当前不调用新后端接口，不修改 `IdManager.resetUserId()`，不影响聊天滚动链
- 今日农情首版已接入为独立每日资讯卡片：后端新增 `daily_agri_cards` 表、`GET /api/today-agri-card` 只读接口和 `POST /internal/jobs/today-agri-card/generate` 内部生成接口。生成链路走 DashScope 原生 Generation 协议调用 `qwen3.5-plus`，显式关闭思考模式，强制联网搜索，`search_strategy=max`，`enable_source=true`；服务端用数据库 lease 防并发，生成前会读取过去 7 天已 ready 的今日农情，把标题 / 摘要 / 来源 / 链接喂给模型，要求当天不要重复同链接、同标题或同一事件；服务端也会硬过滤过去 7 天和当天候选里的重复链接 / 重复标题，并只发布 JSON 可解析、严格 3 条、https、近 7 天、来源 URL 来自搜索结果且域名可信的“今日农情”。广告、导购、软文、模型 / 提示词泄露类内容会被过滤，过滤后不足 3 条则不发布新卡片。Android 在后端历史 hydrate 完成后调用 `/api/today-agri-card`，只有 3 条有效 item 才以 `ChatTimelineItem.TodayAgriCard` 插入聊天列表展示层；它不是 `ChatMessage`，不进入本地聊天快照、A/B/C 上下文、`session_ab`、`session_round_archive`、摘要或问诊扣次。缺失 / pending / failed 时前端静默不展示，不阻塞聊天页；点击单条农情只用系统浏览器打开来源 URL
- Android 构建链当前为 Gradle wrapper 8.13、Android Gradle Plugin 8.13.2、Kotlin Android / Compose Compiler Gradle plugin 2.1.21、WorkManager `work-runtime-ktx:2.11.2`；Compose 编译已使用 `org.jetbrains.kotlin.plugin.compose`，不再使用旧 `composeOptions.kotlinCompilerExtensionVersion`
- 聊天消息运行时当前是**单一正向列表主人**：`ChatRecyclerViewHost.kt` 使用普通 `LazyColumn`，`messages` 仍按 oldest -> newest 存储并直接传给列表，视觉底部最新消息是 `lastIndex`
- 底部 composer 仍是页面底部的独立 UI 宿主，继续负责输入、IME、placeholder、发送禁用与收口视觉；**它不是消息运行时主人**
- `ChatScreen.kt` 当前把消息列表和 composer 作为页面 `Box` 内的兄弟层渲染：列表铺满消息区，composer 用 `align(Alignment.BottomCenter)` 固定在底部。composer 已从旧 `SubcomposeLayout` 测量链里拆出，键盘动画不再每帧拖着列表一起 remeasure；composer 自己继续吃 `imePadding()`，根容器不吃 IME padding，以保持“键盘只移动输入框，不抬升消息工作线”
- composer 内部内容高度不作为聊天列表 reserve 真值。多行文字、当前图片缩略图预览、未来附件缩略图、图文混排等只能影响输入框内部布局 / 内部滚动 / composer 自身视觉高度；聊天列表 bottom padding 只允许吃折叠态 composer 外壳、safe area / navigation bar、发送期锁定 reserve 和工作线 gap。IME 动画只移动 composer，不进入列表 reserve
- Android 端 `+` 入口当前已接入首版图片输入：点击后从底部弹出附件卡片，只开放“相机 / 照片”两项；照片入口优先使用 Android 官方 Photo Picker 按剩余槽位请求选择上限，manifest 同步声明官方 Photo Picker backport module 依赖，支持的系统会在选图阶段限制到本轮最多 4 张，不支持 / 兜底选择器仍由 App 回来后截断并提示。输入框已有 4 张图片时，附件卡片顶部和相机 / 照片点击都统一提示“最多4张图片”。图片选中后会先生成 App 私有 `files/composer_images` 稳定上传副本：若原图已经是 JPEG、`<=1MB`、最长边 `<=1024px` 且 EXIF 方向正常 / 未定义，则直接复制不重新编码；其他可解码图片才走压缩并统一转 JPEG。生成副本后按最新剩余槽位二次截断并进入输入框壳体内的缩略图预览，最多 4 张；缩略图可点开全屏预览，输入框图片预览和聊天区用户图片预览共用 `ImagePreviewPager.kt`，内部使用 Telephoto `ZoomableAsyncImage` + `HorizontalPager`，均支持单击图片关闭、左右滑动切换同组图片、双指缩放、放大后拖动查看细节以及 Telephoto 的边界阻尼 / 嵌套滚动 handoff；单击关闭只在单指轻点且未移动时触发，一旦出现多指缩放或新增第二根手指，会等所有手指都离开后才重新允许下一次单击关闭，避免缩放时误退出预览。旧 `ImagePreviewGesture.kt` 手写手势链已删除。带图时 composer 会增加自身高度和文本输入保底空间，不把图片塞进 `BasicTextField` 光标流。点发送后会先把带本地 `imageUris` 的用户消息上屏并清空输入框，再后台上传；若确认该图片仍是 App 私有 `composer_images` 里的 JPEG 且大小 `<=1MB`，发送时直接复用这份已压缩或直通的字节，不再二次压缩。当前压缩序列为 `1024@Q85 -> 1024@Q80 -> 896@Q80 -> 896@Q70 -> 768@Q70 -> 640@Q60 -> 512@Q60`，极端可解码图片仍超限时继续保持 Q60 等比缩小，直到 <=1MB；全程不裁剪、不拉伸。上传等待态只做一次底部定位，不保留 streaming 发送锚点；上传成功后按用户选择顺序把远端 URL 写入 `SessionApi.StreamOptions.images` 并继续请求模型，上传失败则保留该用户消息并标记发送失败，不再额外弹“图片上传”类浮层；若 App 在图片上传阶段或上传成功但尚未可靠完成远端请求时被系统杀掉，WorkManager 延迟兜底会复用本地稳定副本或已上传 URL 补发；服务端只会在轮次归档成功后向 App 发送 SSE `[DONE]`，额度扣减在归档成功后执行；若 `ConsumeOnDone` 临时失败会按同一 `client_msg_id` 短重试，重复扣由 `quota_ledger` 唯一键防住；冷启动 / 远端 hydrate 会保留仍在后台队列中的图片用户消息，不把它误标成最终失败或从 UI 消失；同一 30 轮窗口里如果有多条仍在 WorkManager 队列中的图片用户消息，前端会把这些 pending 消息一起纳入远端归档恢复轮询，不再只盯最后一条；若失败 assistant 重试时只有本地图片副本没有远端 URL，会先重新上传本地图。App 内相机优先让外部相机写入 App cache 下的 `NongjiFileProvider` 临时 URI；启动外部相机时会给输出 URI 加读写 grant flags、ClipData，并按可解析相机包显式授权，回调或启动失败后撤销授权；导入 App 私有 `composer_images` 成功后，Android Q+ 再把原始拍照结果复制到系统相册 `Pictures/农技千查`；临时文件、拍照取消和相机启动失败都会清理；只有 FileProvider 目标创建失败时才回退到直接创建相册 URI。当前 Photo Picker、外部相机 FileProvider URI 和 Android Q+ 相册复制不额外申请相册 / 相机 / 存储权限；定位采集仍未接入，不顺手声明定位权限。相机待回调 URI、是否相册保存和临时文件路径当前用可保存状态暂存，降低外部相机期间 Activity 重建导致拍照结果丢失的概率。聊天区用户消息在本地副本仍存在时优先用 `imageUris` 预览，远端 `imageUrls` 作为冷启动 / 换机 / 本地副本已清理后的兜底；聊天区用户消息图片缩略图当前为 112dp，并有 12MB LRU 内存缓存减少滑动回看时重复读文件 / 拉远端图 / 解码。前端本地 30 轮 UI 窗口裁剪后，消息已不再引用的 App 私有 `composer_images` 旧 JPEG 会在本地窗口落盘后后台清理，避免长期单对话使用时旧图继续占手机空间；远端 URL 和后端 30 天归档不受影响。图片预览高度只影响 composer / 消息内容内部，不进入列表 reserve / 96dp 工作线
- 后端图片入口同步收紧：`POST /upload` 现在要求同一套用户身份头 / token，只接受单张 `<=1MB` JPEG；服务端必须配置 `BASE_PUBLIC_URL` 或 `UPLOAD_BASE_URL` 为公开 `https` 基地址后才会返回图片 URL；`/api/chat/stream` 会校验图片 URL 必须来自该公开基地址下的 `/uploads/*.jpg`，不再信任客户端任意外部图片 URL，也不再用请求转发头临时推导图片可信域名。Android 上传会给 `/upload` 带 `X-User-Id` 和可选 `Authorization`，仍只上传前端生成的私有 JPEG 副本
- 图片 pending 远端恢复只用于补旧答案：如果恢复轮询期间用户又发起了新的活跃 SSE，这条补旧答案链不会取消或打断当前新对话流
- SSE 完成态当前以轮次归档为用户可恢复真相：服务端只在 `AppendSessionRoundComplete(...)` 成功写入 ledger / 归档后才发送 `[DONE]`；`ConsumeOnDone` 在归档成功后执行，若临时失败会按同一 `client_msg_id` 短重试，重复扣由 `quota_ledger` 唯一键防住。replay 只恢复已归档答案，不再补扣旧轮次，且会在 prompt 组装、模型 Key 检查、进行中锁和额度检查之前优先返回；若首查未完成但随后成功拿到 inflight 锁，开主模型前还会二次复查完成态，降低同一 `client_msg_id` 在完成临界点重复开流的机会。每日额度按后端上海时区自然日计算，请求开始时记录 `day_cn`，23:59 发起但 00:00 后完成仍扣发起当天，00:00 后新发起的请求走新一天额度。主模型上游开流当前不再自动二次重试，Android 前台流和 WorkManager 也不再对模型开流失败做静默多次重试，避免同一轮极端情况下多调 Qwen3.5-Plus。`chat_stream_inflight` 获取结果以 lease token 为准，并新增同一用户活跃流唯一约束：同一用户同一时间只允许一条活跃主流式请求，重复 / 并发请求优先返回进行中或失败恢复，不再并行启动多条模型流。旧 `/api/session/round_complete`、`/api/session/b`、`/api/session/c` 已废弃并返回 410，避免旧接口绕过扣次或重复触发摘要模型。这样优先避免“回答已归档但用户收不到完成态”的卡死，同时收紧重复调模型 / 重复扣费风险；连续数据库异常或进程在归档后扣减前崩溃且短重试也失败时，仍可能漏记一次成本，列入未关闭风险观察
- B / C 摘要当前由后端 `SummaryService` 异步触发 Qwen3.5-Flash，并显式关闭思考模式：同一用户同一层有本进程内运行中保护，避免并发重复抽取；摘要写回时会校验 `session_ab.round_total` 必须仍等于触发时快照轮次，旧快照结果不会覆盖更新轮次。若摘要过程中又有新轮次完成，旧摘要写入会被跳过，pending 状态保留到后续触发继续处理
- 当前工作线视觉 gap 为 `96.dp`，也就是小球、streaming 正文底边、开机历史态和完成态尾部都应落在 composer 折叠外壳上方约 96dp 的位置；工作线以下的空白必须露出来，用于免责声明 / 极端说明 / 底部呼吸区，不能把尾部文字压到输入框后面

## 聊天 UI 主链

- `ChatRecyclerViewHost.kt` 当前是正向 `LazyColumn`，没有 `reverseLayout`，没有 `items.asReversed()`
- `ChatRecyclerViewHost.kt` 使用 `verticalArrangement = Arrangement.Bottom`，确保短内容不满一屏时也贴在底部工作线附近，而不是停在顶部 padding
- `ChatScreen.kt` 当前使用 `ChatTimelineItem` 作为列表展示层，允许插入一个 UI-only 今日农情卡片；真实业务消息仍只来自 `messages`，不再通过 `chatListItems` 派生 streaming block item
- streaming 小分割 / block item 化已撤掉：`StreamingBlockChatListItem / StreamingTextBlock / streamingBrowseBlockSnapshot / activeStreamingBlockIndex / streaming_tail` 等符号在主链无残留
- mixed active-zone / overlay 运行时已退出主链：
  - `StreamingLocation`
  - `BottomActiveZoneSlice / resolveBottomActiveZoneSlice(...)`
  - `renderBottomActiveZone()`
  - active-zone 拖动接管 / Overlay 恢复门
  - `requestSendStartBottomSnap()`
- 当前禁止恢复 streaming 高度追滚补偿：`followStreamingByDelta(...)`、`scrollBy(...)`、`dispatchRawDelta(...)`、`streamBottomFollowActive` 不在主链运行
- 回到底部 / AutoFollow 使用最新消息 `lastIndex + FORWARD_LIST_BOTTOM_SCROLL_OFFSET`；该 offset 依赖 Compose 正向列表 positive `scrollOffset` 会把 item 继续向上推并在列表末端 clamp 的语义
- 正向列表传给 `LazyColumn` 的 bottom padding 会扣掉 `CHAT_MESSAGE_ITEM_VERTICAL_PADDING`，只补偿消息 item 外层 padding，不改变工作线本身
- 每次 reveal 提交前，AutoFollow 会先请求一次最新消息底部锚点；提交新的 `streamingMessageContent` 后，`ChatScreen.kt` 顶层通过 `SideEffect` 在同帧 apply changes 后、layout 前再次请求最新消息底部锚点，专门压正向列表下一行先从工作线下方冒头的一帧
- 大白话口径：以前是“新字 / 新行先长出来，列表下一拍才把它拉回工作线”，所以用户能看见下面冒一下；现在是“新字 / 新行要长出来的同一拍，先把列表底部钉在工作线”，画到屏幕时已经归位，所以冒头闪被压住
- 高频 reveal 底部锚点请求带 generation 守护，一帧后只允许最新请求关闭 `programmaticScroll`，避免旧取消任务把新程序滚动提前关掉后被误判成用户浏览
- 启动显示门不再把本地已有消息 / 首次欢迎空态硬等到 hydrate barrier 后才显示；有本地消息、已有 streaming item 或尚未开始过对话时，列表/欢迎壳可以先显示，减少开机白屏时间。历史消息贴底仍走正向列表最新消息 `lastIndex + FORWARD_LIST_BOTTOM_SCROLL_OFFSET` 主链
- 静态 / 开机 / 完成态到底必须同时满足文本底边命中 96dp 工作线以及 `chatListState.canScrollForward == false`，避免“文本看似贴线，但工作线以下空白还没完整露出、还能继续往上扒”的状态被误判为到底
- 首屏历史贴底恢复成多帧确认：等 `startupLayoutReady` 和底部固定 composer 宿主稳定实测高度都到位后最多连续重试 6 帧，只有文本底边命中 96dp 工作线且 `canScrollForward == false` 时才把 `initialBottomSnapDone` 记完成；贴底刚完成后如果 stable bottom reserve 又更新，且用户还没开始新对话 / 没触碰滚动，会再做一次非动画回底修正，避免一次 `scrollToBottom(false)` 尚未真正露出底部空白就关门
- 开机历史态 / 完成态在输入为空、无 focus、IME 收起、composer 非 settling、非发送锁的折叠稳定窗口中，列表 bottom padding 优先吃底部固定 composer 宿主的稳定实测高度再加 96dp 工作线 gap，不再只靠启动估值 / 旧观察值；这是为了保证工作线以下空白完整露出来，同时避免 IME 动画帧进入列表测量链
- 用户进入 `UserBrowsing` 后，如果用户明确滑回正向列表物理底部（`canScrollForward == false`、手指已抬起、列表已停止），会先请求一次正向底部锚点再恢复 `AutoFollow`；连续 2 帧工作线稳定命中只保留为兜底，避免 streaming 持续吐字打断容差导致手动回底后长时间不跟随
- 回到底部按钮仍保留 56dp 安全区：用户滑动过程中不显示，停止滑动后如果正向列表仍可向前滚动且最新消息底边离 96dp 工作线超过安全区，才短暂出现；点击后滚到最新消息 `lastIndex` 并恢复对应滚动模式
- 2026-04-28 用户真机反馈：当前正向列表滚动链整体“确实很稳”，小米 / MiMo 会诊后落地的 SideEffect 同帧锚定、物理底部恢复 AutoFollow、96dp 工作线贴底等主链规则继续作为当前稳定基线保留；后续不要再恢复反向列表、小分割、overlay 或 raw delta

## 渲染与收口

- waiting 小球、streaming 正文、settled 完成态共用同一条 assistant 消息 item，不再切第二滚动主人
- `ChatStreamingRenderer.kt` 当前 active streaming 和 settled Markdown 都走 soft-wrap block renderer，并复用 inline Markdown cache 保留加粗 / 链接 / code；旧 committed 物理行预切 / TextMeasurer 渲染链已移除，避免 streaming -> settled 收口时换渲染模型导致行高 / 行宽微动
- active Markdown 仍实时吐字，但 `# ` / `- ` / `1. ` / `> ` 这类结构前缀必须等后面已有非空正文才结构化，避免只有符号的半成品先变标题 / 列表再重排
- streaming reveal 当前对中文通常 1 到 2 个字一拍，英文 / 数字仍按词块吐出，减少“几个中文字一坨蹦出来”的体感；仍不恢复新字尾部灰色高亮动画，也不把吐字频率推到每个汉字都单独重组
- 标准 Markdown 表格当前不做真表格控件，renderer 会把表格行降级成普通项目行文本，保证模型偶发输出表格时至少可读、不撑乱聊天布局；代码块内的 `|` 不做表格降级。emoji / 表情若偶发输出，继续按普通文本由 Compose `Text` 承接
- streaming 期间不提前显示免责声明文字；如果内容已满足免责声明触发条件，只预留同等几何高度，settled 后才显示真实文案，避免尾部收口当拍突然增高
- 完成态收口继续保留两阶段 finalize：
  - `beginPendingStreamingFinalize(...)`
  - fresh bounds 到位后 `finalizeStreamingStop(...)`
  - 不回退成同拍直接切 settled
  - 不在 finalize 当拍恢复旧 bottom align 精修；若仍处于 AutoFollow，只在 fresh bounds 到位后请求一次正向底部锚点，避免长回复完成时窗口被重新锚到上方

## 当前调试焦点

- 这轮正向列表的目标，是牺牲反向列表那套“最新 item 天然视觉底部”的物理模型，换回用户上滑浏览时更稳定的正向滚动体感
- 输入框发送收口当前已取消旧高度锁：发送时不再用发送前的输入内容高度 / chrome 高度撑住 `composerSettlingMinHeightPx` / `composerSettlingChromeHeightPx`，优先让 composer 随输入清空直接回到空态高度；所有主动收键盘路径统一只调用 `focusManager.clearFocus(force = true)`，不再同帧额外调用 `keyboardController.hide()`，避免部分输入法在双触发下出现多一拍残影。已经没有显示入口的 composer collapse overlay prewarm snapshot 协程也已删除，后续若仍有明显迟钝，再单独评估 `WindowInsetsAnimationCompat`，不先动消息列表主链
- DEBUG 包新增 `ChatStartup` 诊断日志，用来区分清数据 / 重装后看到旧内容时到底来自本地 `chat_ui_cache`、本地 streaming draft，还是后端 `SessionApi.getSnapshot()` hydrate。日志不参与 release 行为，不改变 UI 逻辑；真机排查时可用 `adb logcat -s ChatStartup`
- 异常与生命周期兜底当前继续围绕“不中断主滚动链”小范围加固：日额度耗尽后，当前本地会话当天会把发送键置灰但保留点击提示；断网连续点同一段输入时复用已有失败用户消息，不再刷出多条相同失败消息；后端 streaming 进程被杀后若远端 snapshot 追不回答案，会在用户消息下方补一个 `回复未完成 · 点击重试` 的 assistant 失败入口；远端历史模式下，`/api/chat/stream` 打到后端后会由后端继续读完上游并在 DONE 后写入 `session_round_archive`，Android 切后台 / 锁屏再回来时会围绕当前 `clientMsgId` 持续拉取 `/api/session/snapshot`，若后端已完成则直接恢复完整 AI 回复，冷启动发现尾部用户消息缺少 AI 回复时也走同一恢复窗口；带图片发送当前已接入 WorkManager 延迟兜底：用户消息上屏并落盘后会按 `chatScopeId + userMessageId` 排一个唯一后台任务，前台活跃时后台只重试不抢跑，前台一旦开始 `/api/chat/stream` 会写入 10 分钟远端启动保护窗；后端同步使用 `chat_stream_inflight` 进行中锁和 lease token，并通过数据库唯一约束限制同一 `user_id` 同时只有一条活跃主流式请求，避免不同 `client_msg_id` 并发打穿额度预检查并多开主模型。重复请求在完成归档前返回 `409 STREAM_IN_PROGRESS` 给客户端走长窗口 snapshot 恢复，完成后的 replay 真源以 `session_round_ledger` / 归档成功为准，服务端只在轮次归档成功后才向客户端发送 SSE `[DONE]`，额度扣减也在归档成功后执行，避免 quota ledger 已写、客户端已收完成态但回答未归档时返回空 replay；旧请求释放时也只能释放自己的锁。前台 SSE 会识别 replay 事件，不会把 replay + DONE 当成空回复完成。若前台上传失败并已经显示“发送失败”，对应后台任务会取消，不会一边显示失败一边后台偷偷消耗额度。切后台 / 锁屏时仍会收起输入焦点并清掉消息 / 输入选择菜单，避免回来还挂着复制黑卡片；`SessionApi` 的当前 SSE call 会等响应读循环退出后再清空引用，保证 reset / cancel 能尽量取消正在读的远端流
- 当前最需要真机验证的是：
  1. 首屏进入有历史时是否稳定贴底
  2. 发送瞬间小球是否第一时间出现在工作线，历史文本是否不抖
  3. streaming 工作线下一行冒头闪已由用户初测确认被 `SideEffect` 同帧锚定压住，后续只需继续观察长回复 / 不同机型是否复现
  4. 用户上滑 / 下滑是否不抢手，想停哪里能停哪里
  5. 用户点击回到底部、或手动下滑到物理底部后是否恢复 AutoFollow
  6. finalize 收口是否稳定，尤其含免责声明答案是否不再尾部增高微跳
  7. 输入框上方和静态文本底部是否不再出现额外白块
  8. 冷启动首次点输入框、键盘弹起 / 回缩是否比旧 `SubcomposeLayout` 版本更利索；如果仍有残影，下一步只评估 `WindowInsetsAnimationCompat` 或输入法平台差异，不先动消息列表主链

## 当前交接入口

- 主规则：`AGENTS.md`
- Android 局部补充：`app/AGENTS.md`
- Go 后端局部补充：`server-go/AGENTS.md`
- 当前风险：`docs/project-state/open-risks.md`
- 待定事项：`docs/project-state/pending-decisions.md`
- 近期变更：`docs/project-state/recent-changes.md`
- 关键决策：`docs/adr`
- 运维与排障入口：`docs/runbooks`

## 当前阶段判断

- mixed active-zone / overlay 架构经过多轮补丁后，已经证明不适合作为最终方案；继续补只会在“发送抖、拖动乱窜、消息重叠”和“闪烁 / 收口抖”之间来回换问题
- 反向列表单主人虽然压住了大部分底部冒头闪，但在最新 assistant item 内小幅上滑时仍会被动态长高锚点带一下，用户体感不可接受
- 当前仓库已按用户拍板切回“**单一运行时主人 + 正向列表**”路线；这不是恢复旧正向 overlay / placeholder / 追滚补偿，而是：
  - 正向 `LazyColumn`
  - 保留 send-start 保护窗口
  - 保留两阶段 finalize
  - 保留 unified soft-wrap renderer
  - 删除 mixed active-zone、小分割 itemization、streaming raw delta / scrollBy 追滚

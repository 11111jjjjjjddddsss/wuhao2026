# 今日农情 Runbook

本文记录“今日农情”每日资讯内容的当前运维入口。后端表、接口和部分代码历史命名仍叫 card，但主聊天用户侧现在按普通 AI 文本展示，仍作为普通聊天列表项处理。

## 当前真相

- 今日农情是独立每日资讯内容，不是聊天消息
- 不写 `session_ab` / `session_round_archive`，不触发摘要，不进入记忆文档，不扣用户问诊次数；主界面一旦展示过，会按账号在 `today_agri_user_items` 保存当天一条展示记录和展示用正文副本，正文副本只保留日期、标题、摘要和短来源名，不保留外部 URL。删除历史对话会清掉主界面展示记录；设置页“今日农情”近 30 天公开历史仍来自 `daily_agri_cards`，不随聊天历史删除。Android 只在主聊天时间线里当天今日农情视觉项后方的连续两轮用户发送中显式携带 `today_agri_context_day`，后端校验为服务器上海当天后临时注入当天农情作为系统背景，第三轮起自动不带
- 当前只有全国卡片：`scope = CN`
- 数据真源表：`daily_agri_cards`
- 用户只读接口：`GET /api/today-agri-card`
- 用户近 30 天回看接口：`GET /api/today-agri-cards`
- 内部生成接口：`POST /internal/jobs/today-agri-card/generate`
- 内部状态接口：`GET /internal/jobs/today-agri-card/status?day_cn=YYYYMMDD`，供脚本 / Codex 自动化先查目标日期是否已人工锁定，必须带 `DAILY_AGRI_JOB_SECRET`
- 内部探针接口：`POST /internal/jobs/today-agri-card/probe?runs=3`，只测模型输出 / 来源 / 解析质量，不写 `daily_agri_cards`
- 内部人工发布接口：`POST /internal/jobs/today-agri-card/manual`，只供脚本 / Codex 自动化使用，必须带 `DAILY_AGRI_JOB_SECRET`
- 上述 `/internal/jobs/today-agri-card/*` 共享密钥入口同时要求调用来源是 loopback / 私网地址；公网本机脚本不再直接带 secret 打公网 internal。Codex 自动化和本机命令行默认通过 Cloud Assistant 进 ECS，再在 ECS 本机 `127.0.0.1:<active slot>` 调用内部接口；浏览器后台继续走 `/admin-api/v1/today-agri/*`
- 后台补跑接口：`POST /admin-api/v1/today-agri/generate`，仅 `owner / content_ops`
- 后台人工发布接口：`POST /admin-api/v1/today-agri/manual`，仅 `owner / content_ops`；该接口仍写同一张 `daily_agri_cards`，不是第二套内容系统
- 当前生产推荐主链：ECS systemd timer 每天约 05:35 主触发一次，并有约 05:50 / 06:10 两次早晨补查；后台补跑只作为异常兜底。人工发布适合晚上准备次日 3 条内容，发布后会标记 `source_type=manual / manual_locked=1 / manual_by / manual_at`，同一天自动生成和补跑只复用缓存，不覆盖人工内容。没人人工发布时，原自动生成继续兜底
- 主聊天当前直接走原千问主链；今日农情仍固定独立走 OpenAI 兼容 `chat/completions + qwen3.5-plus + enable_search=true + search_strategy=turbo + forced_search=true + enable_source=true`，两条链路分开，不互相影响。`enable_thinking=false` 必须放在今日农情请求顶层。`agent / agent_max` 属于多轮检索整合且通常带来更多输入 token 和更长延迟，今日农情默认不使用；当前不保留 Flash、qwen-turbo、Responses、multimodal 或 DashScope `text-generation/generation` 作为今日农情生产候选，也不提供环境变量模型切换入口。用户端已经取消外部链接点击，公开接口只返回标题、摘要和短来源名称；URL、source_index 和发布日期只保留在服务端存储、后台和内部探针里，用于事实核对、去重和排查，不下发给 Android 用户文本

## 环境变量

- `DASHSCOPE_PRIMARY_API_KEY_1...4`、`DASHSCOPE_SECONDARY_API_KEY_1`，或旧兼容 `DASHSCOPE_API_KEY_*` / `DASHSCOPE_API_KEY` / `DASHSCOPE_API_KEYS`：百炼模型 Key 池；主组 / 副组配置、轮询和限流口径见 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md)
- `BAILIAN_BASE_URL`：可选，今日农情和主聊天默认使用 `https://dashscope.aliyuncs.com/compatible-mode/v1`
- `DAILY_AGRI_JOB_SECRET`：内部生成接口密钥，必须配置；不要写入仓库
- 今日农情模型固定为 `qwen3.5-plus`；当前不支持通过环境变量切换模型

当前生产模型固定为 `qwen3.5-plus`，今日农情继续独立于主聊天模型调用。默认链使用 OpenAI 兼容 `chat/completions`，顶层关闭思考，强制 `turbo` 联网并开启 `enable_source=true`。这条兼容链通常不返回 DashScope 原生 `search_info.search_results` 结构化来源列表，所以来源名主要由提示词要求模型写入 JSON 的 `source_name`。2026-06-10 用户已拍板删除低价轻量候选；当前不再评估其它模型作为今日农情模型。

官方联网搜索文档口径：`search_strategy=turbo` 是默认且适合大多数场景的搜索策略；OpenAI 兼容 Chat 链路支持 `enable_search + search_options`。`qwen3.5-plus` 属于混合思考模型，`enable_thinking=false` 可在顶层关闭。`agent / agent_max` 会多轮信息检索与整合，通常会带来更多输入 token 和更长延迟。因此今日农情默认坚持 `qwen3.5-plus + compatible chat + turbo`，不迁移到 Responses `web_search` / agent 策略。

模型提示词版本当前为 `2026-06-28-v79`。v79 是在 v78 基础上的排版小修：必须输出 3 条仍是今日农情唯一硬数量要求；面向普通大众用户，写成手机资讯卡片，先说清发生了什么，再说明对农事安排、防灾减损、田间管理、农资选择、产地销售或政策申报有什么参考；语言自然、具体、正式，不写成文件通报，也不过度口语化。优先近 7 天公开来源，今天或昨天的新进展更优；三条尽量分散地区、作物和主题，近 7 天已推送过的同地区、同作物、同一事件尽量避开，确需继续写同类主题时必须有明显新进展，不能换标题重复写。选题仍以种植方面新闻为主，不限制具体作物；大田作物、经济作物、设施农业、果树、蔬菜、茶叶、特色作物等都可以，但不要总集中在水稻、蔬菜、夏收夏种和天气提醒。更优先选择对生产或经营有实际参考价值的内容，例如栽培管理、植保病虫、种子种苗、农资农机、技术推广、土壤墒情、节水灌溉、产地流通 / 价格、仓储保鲜、政策补贴、地方农技推广等真实进展。摘要目标约 90-130 个中文字符，写 2-3 句完整短讯，信息量要够，一般别低于 80 字；`summary` 字段允许用 JSON 转义换行 `\n` 拆成 2-3 个短段，避免手机端显示成一整块长段。能具体到地区、作物、措施、价格、面积、补贴金额或进度时就写清楚，来源不确定的数字宁可省略。这里的字数和分段都是提示词目标，不是后端字数硬卡，不恢复 `max_tokens`、截断、关键词拦截或字数过滤。

今日农情按“种植侧，养殖、水产不要”的口径由提示词控制，但不做后端关键词过滤：作物、种子种苗、农资农机、植保、病虫测报、苗情墒情、农业气象 / 灾害、种植侧价格流通、政策补贴、技术推广等都可以选；养殖、水产、畜牧、猪肉 / 生猪、禽蛋、牛羊奶、饲料、兽药、渔业、鱼虾等主体不要。这些是方向和边界，不是后端词表。

后端解析器不按主题、可信域名、近 7 天链接、旧卡链接、同批重复标题、发布日期、养殖关键词、广告词或摘要字数等内容做发布阻断，只保留最低技术兜底：能解析出 JSON，必须有 3 条标题 / 摘要非空的 item，内部 URL 不保存私网 / 本机地址，公开响应不下发链接或内部字段；如果旧缓存或手工数据里超过 3 条，公开接口也只返回前 3 条，保证用户侧始终是三条。广告软文、假新闻、养殖水产、重复事件和摘要厚薄主要靠提示词、生成探针、后台运营复核与后续日志观察控制。

Android 展示口径：今日农情不是聊天消息，只作为 `ChatTimelineItem.TodayAgriCard` 插入视觉时间线；真实 `messages` 仍只包含用户 / assistant 对话。主聊天视觉上按普通 AI 文本展示“今日农情 · 日期 + 3 条资讯”，标题加粗，序号使用 `一、二、三、` 中文标识，长按复用 AI 文本区同款“复制 / 全文复制”菜单；从用户视觉和滚动体验看，它必须像一条普通列表文本一样上下滑动，不能做成 overlay、sticky 尾卡、浮层、边框卡片或关闭动画。没有真实聊天时，主界面仍只显示欢迎语，今日农情不替代空态；已有完整 AI 回答历史时，今日农情跟在当时最后一条完整回答后方，并保存一条主界面展示记录和展示用正文副本。后续重开 App 或远端 snapshot hydrate 后，按这条记录恢复当天这一条内容，让它像历史文本一样稳定存在；远端模式下主聊天只显示后端 snapshot 返回的当天保存项，不再用本地缓存先露出一条未确认农情。用户后续发送文字 / 图片 / 失败态消息时，新消息自然追加在其后方并把它往上顶。删除历史对话会清掉主界面展示记录；设置页“今日农情”入口展示近 30 天已 ready 的标题、摘要和来源名记录，不随聊天历史删除。聊天页不提供手动关闭 / 手动隐藏入口，单条农情不可点击跳外部链接。

主聊天远端 snapshot 需要区分“当天确实没有保存展示项”和“读取 `today_agri_user_items` 临时失败”：后端会在读取失败降级返回主聊天快照时带 `today_agri_items_unavailable=true`，Android 收到该标记时保留本轮已经显示的今日农情，不把它当成后端确认删除；只有读取成功且没有当天展示项时，才清主界面展示项。

临时上下文口径：只有当天视觉项后方的连续两轮用户发送会带 `today_agri_context_day`。远端模式下，主聊天必须等后端 snapshot 已有当天保存项后才展示今日农情；后端只接受等于服务器上海日期的 day，且只读取当天 ready 农情作为临时系统背景，用来理解“刚才 / 上面 / 第几条农情”这类紧邻追问；不做用户文本关键词猜测，不把今日农情写入归档、记忆文档、A/B/C、扣次或用户长期事实。失败重试、带图后台 pending 和同一 `client_msg_id` 应复用首次发送时固化的 day，避免重试时 hash 不一致。

文本排版口径：主聊天里的今日农情 UI 仍集中在 `TodayAgriCardUi.kt`，当前使用普通 AI 文本、加粗标题、中文序号标识和正文 / 来源分层展示。调这个块时优先改 `TodayAgriCardUi.kt`，不要重写聊天滚动链，也不要把它塞进真实 `messages`。

## 生成接口

生成接口只允许定时任务或人工运维在 ECS 本机 / VPC 内部调用。该入口必须同时满足 loopback / 私网来源和 `DAILY_AGRI_JOB_SECRET`，并默认按 scope + IP 做 10 分钟 120 次短期限流，配置 Redis 时跨实例共享。下面的 curl 示例应在 ECS 内部或 Cloud Assistant 脚本里执行，不是让本机从公网直接访问：

```powershell
curl.exe -X POST "$env:BACKEND_BASE_URL/internal/jobs/today-agri-card/generate" `
  -H "X-Internal-Job-Secret: $env:DAILY_AGRI_JOB_SECRET"
```

也支持：

```powershell
curl.exe -X POST "$env:BACKEND_BASE_URL/internal/jobs/today-agri-card/generate" `
  -H "Authorization: Bearer $env:DAILY_AGRI_JOB_SECRET"
```

内部生成接口预期返回会保留服务端能拿到的来源字段，便于后台和运维排查；当前兼容 Chat 链路通常没有结构化搜索来源 URL，所以 `url` / `published_date` 可能为空，`source` 主要来自模型 JSON 的 `source_name`。这不是 App 用户公开响应：

```json
{
  "status": "ready",
  "card": {
    "date_cn": "20260511",
    "title": "今日农情",
    "items": [
      {
        "title": "华北麦区防干热风",
        "summary": "华北多地小麦进入灌浆关键期，气象部门提醒关注高温干风，适时浇水稳粒重。",
        "url": "https://www.weather.com.cn/agri/2026/05/11/123456.shtml",
        "source": "中国天气网",
        "published_date": "2026-05-11"
      },
      {
        "title": "早稻病虫进入防控期",
        "summary": "南方早稻陆续分蘖拔节，植保系统提示加强纹枯病和稻飞虱巡查。",
        "url": "https://www.natesc.org.cn/News/202605/t20260511_123456.htm",
        "source": "全国农技推广网",
        "published_date": "2026-05-11"
      },
      {
        "title": "蔬菜价格稳中有降",
        "summary": "批发市场监测显示多类蔬菜供应增加，部分叶菜价格回落，本地走货节奏同步变化。",
        "url": "https://www.moa.gov.cn/xw/qg/202605/t20260511_123456.htm",
        "source": "农业农村部",
        "published_date": "2026-05-11"
      }
    ]
  }
}
```

## App 读取接口

App 使用普通用户身份读取：

```powershell
curl.exe "$env:BACKEND_BASE_URL/api/today-agri-card" `
  -H "X-User-Id: <user-id>"
```

如果当天没有 ready 卡片，接口返回 `missing` / `pending` / `failed` 等状态；Android 会静默不展示，不阻塞聊天页。

用户侧公开响应只包含标题、摘要和短来源名称，不包含 URL、source_index 或条目日期：

```json
{
  "status": "ready",
  "card": {
    "date_cn": "20260511",
    "title": "今日农情",
    "items": [
      {
        "title": "华北麦区防干热风",
        "summary": "华北多地小麦进入灌浆关键期，气象部门提醒关注高温干风，适时浇水稳粒重。",
        "source": "中国天气网"
      }
    ]
  }
}
```

设置页“今日农情”回看近 30 天：

```powershell
curl.exe "$env:BACKEND_BASE_URL/api/today-agri-cards" `
  -H "X-User-Id: <user-id>"
```

如果数据库里当天记录状态是 `ready` 但 `content_json` 不是合法 JSON，或少于 3 条标题 / 摘要都可展示的 item，用户侧会按不可展示状态处理，不再返回 500；后台补跑遇到这种“ready 但正文不可用”的卡片时允许重新生成覆盖。

## 内部探针

探针用于验证当前固定 `qwen3.5-plus + compatible chat/completions + turbo` 的 JSON 执行力、来源名、解析通过率、usage 和新闻质量。探针不会写入 `daily_agri_cards`，也不会改变用户当天看到的卡片。该入口同样必须在 ECS 本机 / VPC 内部调用并带 `DAILY_AGRI_JOB_SECRET`，`runs` 默认 1，最多 3。

2026-06-28 提示词已升到 v79：在保持 `qwen3.5-plus + turbo`、3 条 JSON、提示词控制质量和不增加后端内容过滤的前提下，只把摘要排版从“一整块短讯”改成允许 `summary` 使用 JSON 转义换行 `\n` 的 2-3 个短段，便于 Android 现有今日农情文本渲染按自然换行分块显示。v79 继续保留 v78 的避重和选题质量口径，仍不按主题词、摘要字数、近 7 天链接、可信域名或养殖水产词做发布阻断，不恢复模型输出截断、`max_tokens` 或字数过滤；内容质量继续靠提示词、内部探针、后台抽查和人工发布观察。2026-06-23 v78 生产探针曾得到 `ok_count=1/1`，2026-06-15 v77 生产探针曾得到 `ok_count=3/3`，每次均为 3 条完整 item，未返回 `reasoning_tokens`；样本包含雨后田管 / 复产、夏收、农垦标准、茶园修复、晚播小麦收官等。2026-06-14 v76 生产探针曾得到 `ok_count=2/2`，2026-06-13 v74 生产探针曾累计 `runs=5` 得到 `ok_count=5/5`，2026-06-12 v70 生产探针曾得到 `ok_count=3/3`；更早 v67 / v52-v55 的探针记录只作历史排障参考。兼容 Chat 链路返回的结构化 `sources[]` 可能为空，质量判断仍要看 `source_name`、正文事实和后台抽查。

```powershell
curl.exe -X POST "$env:BACKEND_BASE_URL/internal/jobs/today-agri-card/probe?runs=3" `
  -H "X-Internal-Job-Secret: $env:DAILY_AGRI_JOB_SECRET"
```

返回字段重点看：

- `ok_count`：通过后端最小解析的次数
- `runs[].source_count`：DashScope 返回的结构化搜索来源数量；当前兼容 Chat 链路可能长期为 0，不等同于没联网
- `runs[].candidate_items / displayable_items / invalid_reasons`：模型候选数量、可展示数量和格式原因；不是内容过滤结果
- `runs[].display_sources`：最终采用来源域名或来源名
- `runs[].model_input_tokens / model_output_tokens / model_total_tokens / model_reasoning_tokens / model_search_count`：可用时返回的 usage、思考 token 与搜索次数；重点确认 `model_reasoning_tokens` 为 0 或未返回
- `runs[].card.items[].url`：内部追溯 URL；当前兼容 Chat 链路通常为空，不下发给 Android 用户卡片
- `runs[].card.items[].source`：短来源名，公开接口会下发给 Android 展示；不得包含 URL、手机号、联系方式或推广文案
- `runs[].sources[]`：内部排查用搜索来源列表；当前兼容 Chat 链路可能为空，重点转看 `source_name`、`model_search_count` 和正文事实质量

探针返回中会带搜索来源 URL，因此只能作为内部运维接口使用，不要下发给 Android，也不要把 `DAILY_AGRI_JOB_SECRET` 写进仓库、APK、聊天记录或后台前端。

## 排查 SQL

只读查看当天状态：

```sql
SELECT day_cn, scope, status, model, search_strategy, prompt_version,
       generated_at, lease_until, error, updated_at
FROM daily_agri_cards
WHERE day_cn = DATE_FORMAT(CONVERT_TZ(NOW(), @@session.time_zone, '+08:00'), '%Y%m%d')
  AND scope = 'CN';
```

查看卡片正文：

```sql
SELECT content_json, sources_json
FROM daily_agri_cards
WHERE day_cn = 'YYYYMMDD' AND scope = 'CN';
```

## 日志关键词

- `daily agri card generated`
- `daily agri generation started`
- `daily agri model response received`
- `daily agri model output not displayable`
- `daily agri probe started`
- `daily agri probe card displayable`
- `daily agri probe output not displayable`
- `generate today agri card failed`
- `get today agri card failed`
- `mark daily agri card failed state failed`

`daily agri model response received` 会尽量记录 `source_count`、`content_chars`、`model_input_tokens`、`model_output_tokens`、`model_total_tokens` 和 `model_search_count`。若模型响应没有返回 usage 字段，日志里可能只有来源数量和正文长度。

## 失败处理

1. 确认模型 Key 池存在，且对应账号的模型联网搜索权限可用；多账号限流排查见 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md)。
2. 确认 `DAILY_AGRI_JOB_SECRET` 已配置，调用头和服务端环境一致。
3. 确认迁移 `014_daily_agri_cards.sql` 已执行。
4. 查询 `daily_agri_cards` 当天状态和 `error`。
5. 如果状态是 `pending` 且 `lease_until` 未过期，等待当前任务完成。
6. 如果状态是 `failed` 或 lease 已过期，可人工重新调用内部生成接口。
7. 如果错误是 `dashscope status 400` 且审计 / 日志里没有业务解析报错，优先核对 OpenAI 兼容 `chat/completions` 请求体是否仍为顶层 `messages + enable_thinking=false + enable_search=true + search_options.search_strategy=turbo + forced_search=true + enable_source=true`，不要先怀疑定时器或后台补跑按钮。
8. 如果用户侧或后台曾出现今日农情 500，优先看 `content_json / sources_json` 是否为坏 JSON 或结构不完整。当前后台今日农情列表会把这类问题标成 `content_json_invalid`、`content_shape_invalid`、`sources_json_invalid` 或 `sources_shape_invalid`，不会再让整页 500；可直接用后台补跑当天卡片。

## 后台补跑

当前管理后台“今日农情”页已经可以直接补跑当天卡片，浏览器不持有 `DAILY_AGRI_JOB_SECRET`，而是走后台账号 / session / CSRF 和服务端角色校验：

- 页面：`https://admin.nongjiqiancha.cn/#today-agri`
- 接口：`POST /admin-api/v1/today-agri/generate`
- 角色：`owner`、`content_ops`

补跑逻辑是幂等的：

- 今天已经有 `ready` 卡片时，后台会直接复用现有结果
- 今天是 `missing / failed / lease 过期 pending` 时，会重新生成
- 真正密钥调用仍发生在服务端，不把 `DAILY_AGRI_JOB_SECRET` 下发给浏览器

## 人工发布

人工发布用于“晚上由 Codex 或人工整理好次日今日农情，直接写入后台并锁定”的场景。它不修改今日农情提示词，不调用模型，不绕过 `daily_agri_cards`，也不把内容写成聊天历史。

- 页面：`https://admin.nongjiqiancha.cn/#today-agri`
- 接口：`POST /admin-api/v1/today-agri/manual`
- 脚本：`scripts/publish-today-agri-manual.ps1`
- 状态脚本：`scripts/get-today-agri-manual-status.ps1`
- 内部脚本接口：`POST /internal/jobs/today-agri-card/manual`
- 内部状态接口：`GET /internal/jobs/today-agri-card/status?day_cn=YYYYMMDD`
- 角色：`owner`、`content_ops`
- 日期：8 位 `YYYYMMDD`，后台页面晚上 18:00 后默认填次日
- 内容：固定 3 条，每条标题和摘要必填，来源可填短来源名
- 确认：必须输入 `人工发布 YYYYMMDD`，防止手滑覆盖
- 写入：`status=ready`、`model/search_strategy/prompt_version=manual`、`source_type=manual`、`manual_locked=1`

Codex 自动化或本机命令行推荐先查状态、再发布。状态脚本默认按北京时间计算目标日期，18:00 后检查次日；如果要覆盖当天，显式传 `-DayCN YYYYMMDD`。脚本默认通过 Cloud Assistant 进入 ECS，在本机 active slot 调用内部状态接口；只有显式 `-UsePublicInternalApi` 才尝试旧直连模式，且生产后端仍会要求来源是 loopback / 私网地址，因此日常不要使用该开关。脚本带 30 秒默认请求超时，可用 `-TimeoutSec` 调整，避免网络卡住时长期挂起；输出只包含日期、状态、来源类型、人工锁定标记、条目数和 `should_publish`，不打印密钥：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\get-today-agri-manual-status.ps1 -DayCN 20260619
```

如果状态脚本返回 `manual_locked=true` 且 `source_type=manual`，说明这一天已经由人工 / Codex 锁定，自动化应跳过，不再查新闻或覆盖内容。真实发布脚本在发送前也会重新调用同一个内部状态接口；所以本机 Codex 自动化即使 22:00 成功、23:00 再运行，也会安全跳过已发布日期。

发布脚本默认按北京时间计算目标日期，18:00 后发布次日；如果要覆盖当天，显式传 `-DayCN YYYYMMDD`。脚本默认通过 Cloud Assistant 把待发布 JSON 传到 ECS，由 ECS 读取 `/etc/nongjiqiancha/server.env` 中的 `DAILY_AGRI_JOB_SECRET` 并调用本机 active slot；本机不会读取或打印内部密钥。只有显式 `-UsePublicInternalApi` 才尝试旧直连模式，生产日常不要使用。请求带 30 秒默认超时，可用 `-TimeoutSec` 调整；输出只包含日期、状态、标题和来源，不打印密钥：

本地检查参数和确认词时先加 `-DryRun`，dry run 不读密钥、不发送请求；确认内容没问题后再去掉 `-DryRun` 真实发布。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\publish-today-agri-manual.ps1 `
  -DayCN 20260618 `
  -Title1 "全国冬小麦收获近九成" `
  -Summary1 "据中央气象台夏收夏种服务信息，截至6月15日，全国冬小麦已收获3.04亿亩、进度89.48%。6月18日多数夏收区天气适宜机收，局地小雨地区要抢晴收晒、通风归仓，降低穗发芽和霉变风险。" `
  -Source1 "中央气象台" `
  -Title2 "黄淮海夏播玉米抓紧适墒播种" `
  -Summary2 "全国农技中心近期发布黄淮海夏播玉米技术指导，提示小麦收后要疏通田间沟渠、根据墒情适时早播，北部地区力争6月20日前完成播种。墒情不足地块先造墒，雨后地块注意排水散墒，争取一播全苗。" `
  -Source2 "全国农技中心" `
  -Title3 "果蔬生产继续推进绿色提质" `
  -Summary3 "全国农技中心6月14日在广西北海召开果树蔬菜专家指导组会议，研判果蔬产业形势并部署高质量发展。近期多地设施蔬菜观摩也集中展示番茄、豇豆等新品种和水肥一体化、绿色防控技术，夏季管理重点仍是控温降湿和减药增效。" `
  -Source3 "全国农技中心"
```

人工发布后，当天 / 次日清晨自动任务和后台“补跑今天”看到 `manual_locked=1` 会直接跳过，不会覆盖人工内容。若要重新改人工内容，仍从后台“人工发布”再次提交同一天 3 条内容即可覆盖并继续锁定。若完全没人写，ECS 定时任务仍按 05:35 左右自动生成。

本机 Codex 全局自动化 `今日农情人工发布` 当前配置为每天北京时间 22:00 和 23:00 执行，模型 `gpt-5.5`、`reasoning_effort=xhigh`、工作目录 `D:\wuhao`。自动化只负责读本 runbook 的写作口径、核对公开新闻、写 3 条今日农情并通过脚本发布；它不能修改任何仓库文件或本机配置，不能 `apply_patch`，不能 `git add / commit / push`，不能改服务端今日农情提示词、项目文档、业务代码、脚本、模型输出限制或主聊天滚动链。Codex 自动化只是人工新闻发布助手，不是后端今日农情系统替代品；若电脑未开机或 Codex 本机任务没跑，ECS 后端 05:35 自动生成仍是兜底。如果需要“电脑开机后补跑错过的 22:00 / 23:00”，需另做 Windows 启动补偿任务，当前未配置。完整本机自动化标准配置见 [codex-automations.md](D:/wuhao/docs/runbooks/codex-automations.md)。

## 定时任务

当前 ECS 推荐用 [configure-ecs-daily-agri-job.ps1](D:/wuhao/scripts/configure-ecs-daily-agri-job.ps1) 安装 systemd service + timer：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\configure-ecs-daily-agri-job.ps1 -RunOnce
```

默认会在 ECS 上写入：

- `/usr/local/bin/nongji-generate-today-agri.sh`
- `nongji-daily-agri.service`
- `nongji-daily-agri.timer`

默认主 timer 使用 `*-*-* 21:35:00 UTC`，对应北京时间次日 `05:35` 左右；脚本还会默认追加 `21:50 UTC / 22:10 UTC` 两次早晨补查，防止 05:35 正好遇到未过期 pending lease 或外层短暂异常后当天没人再捞。多次触发仍调用同一个服务端生成接口：遇到人工锁定或 ready 内容会直接复用 / 跳过，不会覆盖人工稿，也不会重复发布；只有 missing、failed、过期 pending 或 ready 但结构不可展示时才会重新生成。脚本会顺手 `-RunOnce` 触发一次，便于安装完立刻验证；`Persistent=true` 负责 ECS 停机错过时在启动后补触发。服务端生成流程内部最多做 2 次模型生成尝试，用来接住单次模型输出解析失败。2026-06-18 起，安装脚本生成的 ECS 侧 bash 会从 Nginx 配置解析唯一 active upstream port，解析结果不是唯一值时直接失败，不再悄悄 fallback 到固定端口，避免双端口切换后打到旧 slot。

## 质量边界

后端只发布同时满足以下条件的结果：

- JSON 可解析
- 必须能取到 3 条标题和摘要都非空的 item；超过 3 条只展示前 3 条
- 搜索来源 URL 只用于内部追溯、去重和后台排查；当前兼容 Chat 链路通常没有结构化来源 URL，公开响应只展示短来源名，不展示 URL、source_index 或条目日期
- 解析时如果存在结构化搜索来源，优先信 `source_index` 对应的来源 URL，并忽略模型自拟 `link_url / url`；如果没有结构化来源，则只保存模型给出的短 `source_name`
- URL 只作为内部追溯，不作为用户展示条件
- 后端不按重复标题、过去 7 天链接 / 标题、可信域名、主题词、发布日期、首页 / 栏目页形态、养殖水产词、广告词或真假判断做发布阻断
- 生成前会带入过去 7 天已 ready 的今日农情，要求模型不要重复同原文、同标题或同一事件；种植侧方向、养殖水产不要、排除广告软文 / 导购 / 假新闻 / 元表达 / 标题党，主要通过提示词控制和后台探针复核，不靠过严代码过滤卡死来源
- 选题按农业实用价值排序，优先具体地区、具体作物 / 品类、明确风险 / 农时 / 价格 / 补贴 / 流通影响的信息
- 标题尽量一行读完，摘要约 90-130 个中文字符、一般别低于 80 个中文字符、手机卡片约 3-4 行体量；宁可接近 100 字，也不要收成 70 多字薄通知；标题和摘要必须是自然资讯口吻，禁止“值得看 / 参考意义 / 对农户有用 / 根据搜索结果”等元表达、推荐理由和标题党话术

如果模型输出无法解析成 JSON，或少于 3 条标题 / 摘要都可展示的 item，才不发布新卡片。不要为了当天一定有卡片而放宽到用户打开 App 时临时多次调模型；需要排查时走内部探针或后台补跑。

## 上线前检查

- 内部生成接口只允许定时任务 / 运维调用，不能由 Android 用户打开 App 时触发。
- 生成接口要配置 `DAILY_AGRI_JOB_SECRET`，并在 ECS / 定时任务侧保存，不进入 APK 或仓库。
- 后台至少能查看当天 `status/error/content_json/sources_json/lease_until`，并能手动补跑当天卡片。
- 如果连续失败，先查模型 Key、联网搜索权限、搜索来源返回、模型是否没有输出可解析 JSON、是否少于 3 条标题 / 摘要可展示 item、来源名是否异常、正文是否被代码块或非 JSON 包住；内容质量问题优先调提示词和探针，不要加后端内容过滤。

## 参考资料

- [阿里云百炼联网搜索](https://help.aliyun.com/zh/model-studio/web-search/)
- [阿里云百炼 OpenAI 兼容](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope)
- [阿里云百炼深度思考参数](https://help.aliyun.com/zh/model-studio/deep-thinking)

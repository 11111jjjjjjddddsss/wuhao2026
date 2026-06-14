# 今日农情 Runbook

本文记录“今日农情”每日资讯卡片的当前运维入口。

## 当前真相

- 今日农情是独立每日资讯卡片，不是聊天消息
- 不进入主聊天上下文或记忆文档，不写 `session_ab` / `session_round_archive`，不触发摘要，不扣用户问诊次数
- 当前只有全国卡片：`scope = CN`
- 数据真源表：`daily_agri_cards`
- 用户只读接口：`GET /api/today-agri-card`
- 用户近 30 天回看接口：`GET /api/today-agri-cards`
- 内部生成接口：`POST /internal/jobs/today-agri-card/generate`
- 内部探针接口：`POST /internal/jobs/today-agri-card/probe?runs=3`，只测模型输出 / 来源 / 解析质量，不写 `daily_agri_cards`
- 后台补跑接口：`POST /admin-api/v1/today-agri/generate`，仅 `owner / content_ops`
- 当前生产推荐主链：ECS systemd timer 每天自动触发一次生成，后台补跑只作为异常兜底
- 主聊天联网链仍是百炼兼容模式 `chat/completions + enable_search=true + search_strategy=turbo + forced_search=false`；今日农情固定独立走 OpenAI 兼容 `chat/completions + qwen3.5-plus + enable_search=true + search_strategy=turbo + forced_search=true + enable_source=true`，两条链路分开，不互相影响。`enable_thinking=false` 必须放在请求顶层。`agent / agent_max` 属于多轮检索整合且通常带来更多输入 token 和更长延迟，今日农情默认不使用；当前不保留 Flash、qwen-turbo、Responses、multimodal 或 DashScope `text-generation/generation` 作为生产候选，也不提供环境变量模型切换入口。用户端已经取消外部链接点击，公开接口只返回标题、摘要和短来源名称；URL、source_index 和发布日期只保留在服务端存储、后台和内部探针里，用于事实核对、去重和排查，不下发给 Android 用户卡片

## 环境变量

- `DASHSCOPE_API_KEY_1/2/3`、旧 `DASHSCOPE_API_KEY` 或 `DASHSCOPE_API_KEYS`：百炼模型主备 Key 池；多账号配置和限流口径见 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md)
- `BAILIAN_BASE_URL`：可选，今日农情和主聊天默认使用 `https://dashscope.aliyuncs.com/compatible-mode/v1`
- `DAILY_AGRI_JOB_SECRET`：内部生成接口密钥，必须配置；不要写入仓库
- 今日农情模型固定为 `qwen3.5-plus`；当前不支持通过环境变量切换模型

当前生产模型固定为 `qwen3.5-plus`，今日农情继续独立于主聊天模型调用。默认链使用 OpenAI 兼容 `chat/completions`，顶层关闭思考，强制 `turbo` 联网并开启 `enable_source=true`。这条兼容链通常不返回 DashScope 原生 `search_info.search_results` 结构化来源列表，所以来源名主要由提示词要求模型写入 JSON 的 `source_name`。2026-06-10 用户已拍板删除低价轻量候选；当前不再评估其它模型作为今日农情模型。

官方联网搜索文档口径：`search_strategy=turbo` 是默认且适合大多数场景的搜索策略；OpenAI 兼容 Chat 链路支持 `enable_search + search_options`。`qwen3.5-plus` 属于混合思考模型，`enable_thinking=false` 可在顶层关闭。`agent / agent_max` 会多轮信息检索与整合，通常会带来更多输入 token 和更长延迟。因此今日农情默认坚持 `qwen3.5-plus + compatible chat + turbo`，不迁移到 Responses `web_search` / agent 策略。

模型提示词版本当前为 `2026-06-14-v76`。v76 继续使用通用化短任务说明：必须输出 3 条，这是今日农情唯一硬数量要求；面向普通大众用户，写成手机资讯卡片；优先近 7 天公开来源，今天或昨天的新进展更优；三条尽量分散地区、作物或主题；不要三条都写成天气 / 气象预报，农业气象、防灾或抢收最多作为其中一个角度，另外优先找经济作物、植保病虫、种植技术、农资农机、产地流通 / 价格、政策补贴等真实进展；摘要目标约 90-130 个中文字符，写 2-3 句完整短讯，信息量要够，一般别低于 80 字。这里的字数是提示词目标，不是后端字数硬卡，不恢复 `max_tokens`、截断或字数过滤。

今日农情按“种植侧，养殖、水产不要”的口径由提示词控制，但不做后端关键词过滤：作物、种子种苗、农资农机、植保、病虫测报、苗情墒情、农业气象 / 灾害、种植侧价格流通、政策补贴、技术推广等都可以选；养殖、水产、畜牧、猪肉 / 生猪、禽蛋、牛羊奶、饲料、兽药、渔业、鱼虾等主体不要。这些是方向和边界，不是后端词表。

后端解析器不按主题、可信域名、近 7 天链接、旧卡链接、同批重复标题、发布日期、养殖关键词、广告词或摘要字数等内容做发布阻断，只保留最低技术兜底：能解析出 JSON，必须有 3 条标题 / 摘要非空的 item，内部 URL 不保存私网 / 本机地址，公开响应不下发链接或内部字段；如果旧缓存或手工数据里超过 3 条，公开接口也只返回前 3 条，保证用户侧始终是三条。广告软文、假新闻、养殖水产、重复事件和摘要厚薄主要靠提示词、生成探针、后台运营复核与后续日志观察控制。

Android 展示口径：今日农情不是聊天消息，只作为 `ChatTimelineItem.TodayAgriCard` 插入视觉时间线；真实 `messages` 仍只包含用户 / assistant 对话。当天卡片加载时如果没有真实消息，卡片作为第一条视觉内容排在列表顶部；如果已有真实消息，卡片锚在当时最后一条真实消息后方。用户发送文字 / 图片 / 失败态消息后，不再隐藏卡片，也不播放退出动画，新消息自然追加在卡片后方并把它往上顶；删除历史 / 清数据后的首屏也按“第一条系统卡片”处理，不把它做成浮层或 sticky 尾卡。普通发送 / 工作线仍以真实消息为主，但“跳到底部”和静态底部锚点会按最新视觉尾部处理，避免只有今日农情或今日农情在尾部时回底位置错乱。聊天页会按上海日期定期检查跨天，跨天后先清掉旧日期卡片并只接受 `date_cn` 等于当前日期的卡片，避免 App 长时间前台或后台恢复时继续挂着昨天内容。聊天页卡片右上角不放关闭叉号，不提供手动关闭 / 手动隐藏入口，单条农情不可点击跳外部链接。设置页新增“今日农情”入口，展示近 30 天已 ready 的标题、摘要和来源名记录。

## 生成接口

生成接口只允许定时任务或人工运维调用。该入口必须带 `DAILY_AGRI_JOB_SECRET`，并默认按 scope + IP 做 10 分钟 120 次短期限流，配置 Redis 时跨实例共享：

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

探针用于验证当前固定 `qwen3.5-plus + compatible chat/completions + turbo` 的 JSON 执行力、来源名、解析通过率、usage 和新闻质量。探针不会写入 `daily_agri_cards`，也不会改变用户当天看到的卡片。该入口同样必须带 `DAILY_AGRI_JOB_SECRET`，`runs` 默认 1，最多 5。

2026-06-14 生产环境 `qwen3.5-plus + turbo + v76` 继续跑探针，`runs=2` 得到 `ok_count=2/2`，每次均为 3 条完整 item，`prompt_version=2026-06-14-v76`，未返回 `reasoning_tokens`；样本包含农业气象、无人机防控玉米病虫和农产品批发指数，天气没有占满三条，摘要长度约 78-91 字。v76 是当前“短提示词、三条硬要求、不过度压模型、不恢复后端内容过滤”的收尾版本；摘要仍按提示词目标观察，不为个别略短恢复后端字数过滤、模型输出截断或继续追加细碎硬规则。2026-06-13 v74 生产探针曾累计 `runs=5` 得到 `ok_count=5/5`，摘要长度约 75-87 字；2026-06-12 v70 生产探针曾得到 `ok_count=3/3`，摘要长度约 89-115 字；更早 v67 / v52-v55 的探针记录只作历史排障参考。兼容 Chat 链路返回的结构化 `sources[]` 可能为空，质量判断仍要看 `source_name`、正文事实和后台抽查。

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

## 定时任务

当前 ECS 推荐用 [configure-ecs-daily-agri-job.ps1](D:/wuhao/scripts/configure-ecs-daily-agri-job.ps1) 安装 systemd service + timer：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\configure-ecs-daily-agri-job.ps1 -RunOnce
```

默认会在 ECS 上写入：

- `/usr/local/bin/nongji-generate-today-agri.sh`
- `nongji-daily-agri.service`
- `nongji-daily-agri.timer`

默认 timer 使用 `*-*-* 21:35:00 UTC`，对应北京时间次日 `05:35` 左右；脚本会顺手 `-RunOnce` 触发一次，便于安装完立刻验证。

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
- 标题尽量一行读完，摘要约 90-130 个中文字符、尽量不低于 85 个中文字符、手机卡片约 3-4 行体量，偏 3.5 行；宁可接近 100 字，也不要收成 70 多字薄通知；标题和摘要必须是自然资讯口吻，禁止“值得看 / 参考意义 / 对农户有用 / 根据搜索结果”等元表达、推荐理由和标题党话术

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

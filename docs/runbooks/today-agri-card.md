# 今日农情 Runbook

本文记录“今日农情”每日资讯卡片的当前运维入口。

## 当前真相

- 今日农情是独立每日资讯卡片，不是聊天消息
- 不进入 A/B/C 上下文，不写 `session_ab` / `session_round_archive`，不触发摘要，不扣用户问诊次数
- 当前只有全国卡片：`scope = CN`
- 数据真源表：`daily_agri_cards`
- 用户只读接口：`GET /api/today-agri-card`
- 用户近 30 天回看接口：`GET /api/today-agri-cards`
- 内部生成接口：`POST /internal/jobs/today-agri-card/generate`
- 内部探针接口：`POST /internal/jobs/today-agri-card/probe?runs=3`，只测模型输出 / 来源 / 解析质量，不写 `daily_agri_cards`
- 后台补跑接口：`POST /admin-api/v1/today-agri/generate`，仅 `owner / content_ops`
- 当前生产推荐主链：ECS systemd timer 每天自动触发一次生成，后台补跑只作为异常兜底
- 主聊天联网链仍是百炼兼容模式 `chat/completions + enable_search=true + search_strategy=turbo + forced_search=false`；今日农情独立走 DashScope 原生 Generation + `qwen-plus + enable_search=true + search_strategy=turbo + forced_search=true + enable_source=true`，两条链路分开，不互相影响。`agent / agent_max` 属于多轮检索整合且会额外收费，今日农情默认不使用；`freshness=7` 已随请求体传入，不使用 `assigned_site_list`，保持全网宽搜。`prompt_intervene` 引导“近 7 天、种植侧、全网宽搜、排除养殖和低质内容”，不限定固定网站、不按站点白名单思路检索；生成最多 2 次，第二次只在首轮质量校验失败后换检索提示补救，继续走 `turbo`。用户端已经取消外部链接点击，公开接口只返回标题、摘要和短来源名称；搜索来源 URL、source_index 和发布日期只保留在服务端存储、后台和内部探针里，用于事实核对、去重和排查，不下发给 Android 用户卡片

## 环境变量

- `DASHSCOPE_API_KEY_1/2/3`、旧 `DASHSCOPE_API_KEY` 或 `DASHSCOPE_API_KEYS`：百炼模型主备 Key 池；多账号配置和限流口径见 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md)
- `DASHSCOPE_BASE_URL`：可选，默认 `https://dashscope.aliyuncs.com/api/v1`
- `DAILY_AGRI_JOB_SECRET`：内部生成接口密钥，必须配置；不要写入仓库

当前生产默认模型已切到 `qwen-plus`，今日农情继续独立于主聊天模型调用。2026-06-08 在生产 ECS 实测确认：`qwen3.5-plus` 走旧 DashScope 原生 Generation + 联网搜索会稳定返回 `400 InvalidParameter / url error`，同机同 Key 改走 Responses `web_search` 正常；2026-06-09 / 2026-06-10 生产探针进一步确认：`qwen-flash + turbo + enable_source` 返回 200、可返回搜索来源且更快，但严格 JSON / source_index 执行力偏弱，关闭来源后还出现过偏软文 / 推广味内容；`qwen-plus + turbo + enable_source` 可返回 5-7 个左右来源，单次约 4k-7k tokens、延迟约 8-10 秒，JSON 解析和 3 条内容质量更适合作为生产主线。根据客服口径补测后确认：`qwen3.5-flash` 的联网 `search_strategy=turbo` 需要走 `multimodal-generation/generation` 路由、流式输出和思考模式；`qwen3.5-flash + multimodal-generation/generation + stream=true + enable_thinking=true + search_strategy=turbo + enable_source=true` 在生产 ECS 返回 200，耗时约 4.6 秒，总量约 753 tokens，其中 reasoning 约 363 tokens，能生成 3 条内容，质量初看可用。该路由缺口是没有像当前 `qwen-plus + text-generation/generation + enable_source` 一样返回稳定 `search_results` 来源列表，且输出仍可能包代码块；因此如果后续要用 3.5flash 降本，需要单独实现 multimodal 流式解析、JSON 清洗、来源名兜底、usage 采集和后台探针灰度，不可直接把 `dailyAgriCardModel` 改成 3.5flash。用户已明确 `qwen-flash` 质量不行、`qwen-turbo` 即将下线，二者都不作为今日农情生产候选。当前生产仍为 `qwen-plus + turbo` 原生 Generation 强制联网链，搜索来源 URL 只用于内部核对和去重；这个调整只影响今日农情独立生成链，不影响主聊天模型、B/C 摘要或主聊天联网策略。

官方联网搜索文档口径：DashScope 协议支持返回搜索来源；`turbo` 是默认且适合大多数场景的搜索策略；`agent / agent_max` 会额外按次计费，Responses API 的 `web_search` 计费也按 agent 策略。中国内地搜索策略费当前为 `turbo 3 元 / 千次`、`max 4 元 / 千次`、`agent 4 元 / 千次`，所以 agent 策略费本身不是比 turbo 贵很多，但它会多轮信息检索与整合，通常会带来更多输入 token 和更长延迟。因此今日农情默认坚持 `turbo`，只有后续日志证明 `qwen-plus + turbo` 仍无法稳定产出高质量来源时再重新评估。

截至 2026-06-10，按阿里云官方联网搜索模型列表、客服口径和本项目实测，今日农情候选层级如下：第一梯队继续是 `qwen-plus` / `qwen-plus-latest` 的 DashScope text-generation + `turbo + enable_source` 稳态链；第二梯队是 `qwen3.5-flash` 的 DashScope multimodal-generation + `stream=true + enable_thinking=true + search_strategy=turbo` 降本备用链，需要单独实现、解析、usage 采集和灰度；质量替代候选是 `qwen3.5-plus`，适合未来 `qwen-plus` 下线或质量不足时评估，但成本高于 flash；`qwen3.6-flash`、`qwen3.7-plus` 等官方标注“仅 Responses API 支持”的新模型可以作为后续研究，不混入当前生产链；`qwen-flash` 因实测执行力和内容质量偏松不作为生产候选；`qwen-turbo` 因用户已明确即将下线，不再评估。

模型提示词内部现在要求必须输出 3 条成稿内容；如果已经找到 3 条高质量且主题 / 地区不重复的材料，可以停止继续深挖，不再把 2 条作为正常发布结果。检索阶段保持全网宽搜，不把来源卡死在农业农村部、中国农业信息网、农民日报等少数网站；同等质量下优先官方、农业农村部门、农技推广、气象、主流媒体、农业专业媒体、地方农业信息、市场流通、农资、种业、植保等正式来源。今日农情按“农业大类分种植和养殖，当前只取种植侧”的口径由提示词控制：种子 / 种苗 / 种业、作物、病虫害、植保农药、肥料水肥、农机、农产品价格流通、补贴保险，以及明确影响作物和农时的农业气象风险可以选；畜牧、水产、养殖、动物疫病、生猪、猪肉、猪价、家禽、禽蛋、蛋鸡、肉鸡、牛羊、肉牛、肉羊、奶牛、奶业、饲料、兽药、渔业、水产养殖、鱼虾等养殖侧内容由提示词排除；普通天气预报、生活天气、旅游出行天气不作为独立选题。后端解析器不再做大面积主题、可信域名、近 7 天链接、旧卡链接重复等硬过滤，只保留 JSON 结构、固定标题、正好 3 条、标题 / 摘要非空、同批重复标题和内部 URL 安全兜底；广告软文、假新闻、养殖侧和重复事件主要靠提示词、生成探针、后台运营复核与后续日志观察控制。若模型 `source_index` 指到首页 / 栏目页等低价值来源，URL 只作为内部追溯，不影响内容卡片的用户展示。

Android 展示口径：今日农情不是聊天消息，只作为 `ChatTimelineItem.TodayAgriCard` 插入视觉时间线；真实 `messages` 仍只包含用户 / assistant 对话。当天卡片加载时如果没有真实消息，卡片作为第一条视觉内容排在列表顶部；如果已有真实消息，卡片锚在当时最后一条真实消息后方。用户发送文字 / 图片 / 失败态消息后，不再隐藏卡片，也不播放退出动画，新消息自然追加在卡片后方并把它往上顶；删除历史 / 清数据后的首屏也按“第一条系统卡片”处理，不把它做成浮层或 sticky 尾卡。聊天页会按上海日期定期检查跨天，跨天后先清掉旧日期卡片并只接受 `date_cn` 等于当前日期的卡片，避免 App 长时间前台或后台恢复时继续挂着昨天内容。聊天页卡片右上角不放关闭叉号，不提供手动关闭 / 手动隐藏入口，单条农情不可点击跳外部链接。设置页新增“今日农情”入口，展示近 30 天已 ready 的标题、摘要和来源名记录。

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

内部生成接口预期返回会保留来源字段，便于后台和运维排查；这不是 App 用户公开响应：

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

如果数据库里当天记录状态是 `ready` 但 `content_json` 不是合法 JSON，或不是完整 3 条“今日农情”结构，用户侧会按不可展示状态处理，不再返回 500；后台补跑遇到这种“ready 但正文不可用”的卡片时允许重新生成覆盖。

## 内部探针

探针用于验证 `qwen-plus + turbo` 的来源、JSON 执行力、过滤通过率和 usage 成本，不会写入 `daily_agri_cards`，也不会改变用户当天看到的卡片。该入口同样必须带 `DAILY_AGRI_JOB_SECRET`，`runs` 默认 1，最多 5。

```powershell
curl.exe -X POST "$env:BACKEND_BASE_URL/internal/jobs/today-agri-card/probe?runs=3" `
  -H "X-Internal-Job-Secret: $env:DAILY_AGRI_JOB_SECRET"
```

返回字段重点看：

- `ok_count`：通过后端解析和质量校验的次数
- `runs[].source_count`：DashScope 返回的搜索来源数量
- `runs[].candidate_items / valid_items / reject_reasons`：模型候选数量、通过数量和过滤原因
- `runs[].valid_source_hosts`：最终采用来源域名
- `runs[].model_input_tokens / model_output_tokens / model_total_tokens / model_search_count`：可用时返回的 usage 与搜索次数
- `runs[].card.items[].url`：内部追溯 URL，优先来自 `source_index` 映射的搜索来源，不信模型自拟 URL；不下发给 Android 用户卡片
- `runs[].card.items[].source`：短来源名，公开接口会下发给 Android 展示；不得包含 URL、手机号、联系方式或推广文案
- `runs[].sources[]`：内部排查用搜索来源列表，重点看是否仍被首页 / 栏目页 / 专题页淹没

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
- `daily agri candidate rejected`
- `daily agri probe started`
- `daily agri probe card accepted`
- `daily agri probe candidate rejected`
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
7. 如果错误是 `dashscope status 400` 且审计 / 日志里没有业务解析报错，优先核对今天这条链是否误回到了 `qwen3.5-flash` 或旧的 DashScope 原生 Generation 联网搜索路径，不要先怀疑定时器或后台补跑按钮。
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

- JSON 可解析，标题固定“今日农情”
- 正好 3 条有效 item，标题和摘要都非空
- 搜索来源 URL 只用于内部追溯、去重和后台排查；公开响应只展示短来源名，不展示 URL、source_index 或条目日期
- 解析时优先信 `source_index` 对应的 DashScope 搜索来源 URL，忽略模型自拟 `link_url / url`
- 如果 `source_index` 指到低价值来源，URL 只作为内部追溯，不作为用户展示条件
- 后端只做同批重复标题兜底，不再硬过滤过去 7 天链接 / 标题，也不按可信域名、主题词、发布日期、首页 / 栏目页形态做发布阻断
- 生成前会带入过去 7 天已 ready 的今日农情，要求模型不要重复同原文、同标题或同一事件；种植侧方向、排除养殖、排除广告软文 / 导购 / 假新闻 / 元表达 / 标题党，主要通过提示词控制和后台探针复核，不靠过严代码过滤卡死来源
- 选题按农业实用价值排序，优先具体地区、具体作物 / 品类、明确风险 / 农时 / 价格 / 补贴 / 流通影响的信息
- 标题尽量一行读完，摘要约 3 行体量；标题和摘要必须是自然资讯口吻，禁止“值得看 / 参考意义 / 对农户有用 / 根据搜索结果”等元表达、推荐理由和标题党话术

结构校验后不足 3 条时，不发布新卡片。不要为了当天一定有卡片而放宽到用户打开 App 时临时多次调模型；需要排查时走内部探针或后台补跑。

## 上线前检查

- 内部生成接口只允许定时任务 / 运维调用，不能由 Android 用户打开 App 时触发。
- 生成接口要配置 `DAILY_AGRI_JOB_SECRET`，并在 ECS / 定时任务侧保存，不进入 APK 或仓库。
- 后台至少能查看当天 `status/error/content_json/sources_json/lease_until`，并能手动补跑当天卡片。
- 如果连续失败，先查模型 Key、联网搜索权限、搜索来源返回、模型是否没有按 3 条 JSON 输出、同批标题是否重复，不要直接放宽到广告、软文或任意来源。

## 参考资料

- [阿里云百炼联网搜索](https://help.aliyun.com/zh/model-studio/web-search/)
- [阿里云百炼 DashScope API 参考](https://help.aliyun.com/zh/model-studio/qwen-api-via-dashscope)
- [阿里云百炼深度思考参数](https://help.aliyun.com/zh/model-studio/deep-thinking)

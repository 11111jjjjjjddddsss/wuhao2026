# 今日农情 Runbook

本文记录“今日农情”每日资讯卡片的当前运维入口。

## 当前真相

- 今日农情是独立每日资讯卡片，不是聊天消息
- 不进入 A/B/C 上下文，不写 `session_ab` / `session_round_archive`，不触发摘要，不扣用户问诊次数
- 当前只有全国卡片：`scope = CN`
- 数据真源表：`daily_agri_cards`
- 用户只读接口：`GET /api/today-agri-card`
- 内部生成接口：`POST /internal/jobs/today-agri-card/generate`
- 后台补跑接口：`POST /admin-api/v1/today-agri/generate`，仅 `owner / content_ops`
- 当前生产推荐主链：ECS systemd timer 每天自动触发一次生成，后台补跑只作为异常兜底
- 主聊天联网链仍是百炼兼容模式 `chat/completions + enable_search=true + search_strategy=turbo + forced_search=false`；今日农情独立走 `Responses API + web_search + tool_choice=required`，两条链路分开，不互相影响

## 环境变量

- `DASHSCOPE_API_KEY_1/2/3`、旧 `DASHSCOPE_API_KEY` 或 `DASHSCOPE_API_KEYS`：百炼模型主备 Key 池；多账号配置和限流口径见 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md)
- `DASHSCOPE_BASE_URL`：可选，默认 `https://dashscope.aliyuncs.com/api/v1`
- `DAILY_AGRI_JOB_SECRET`：内部生成接口密钥，必须配置；不要写入仓库

当前生产默认模型仍是 `qwen3.5-plus`，但今日农情不再走旧的 DashScope 原生 Generation 联网搜索，而是改走 Responses API + `web_search`。原因是 2026-06-08 在生产 ECS 实测发现：`qwen3.5-plus` 走 DashScope 原生 Generation + 联网搜索会稳定返回 `400 InvalidParameter / url error`，但同一台机器、同一套 Key、同一模型改走 Responses API + `web_search` 可正常返回 200，并且仍能拿到搜索来源 URL 列表供后端做可信域名、https、去重和来源校验。这个调整只影响今日农情独立生成链，不影响主聊天模型。

模型提示词内部现在会要求先给 5 到 6 条候选，后端再按“来源可信、近 7 天、链接在搜索来源里、和近 7 天及当天不重复”的规则截取前 3 条对外发布。这样做是为了降低“模型只给 3 条但过滤后只剩 2 条”的失败率；用户侧最终仍只会看到 3 条。

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

预期返回：

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
        "url": "https://www.weather.com.cn/",
        "source": "中国天气网",
        "published_date": "2026-05-11"
      },
      {
        "title": "早稻病虫进入防控期",
        "summary": "南方早稻陆续分蘖拔节，植保系统提示加强纹枯病和稻飞虱巡查。",
        "url": "https://www.natesc.org.cn/",
        "source": "全国农技推广网",
        "published_date": "2026-05-11"
      },
      {
        "title": "蔬菜价格稳中有降",
        "summary": "批发市场监测显示多类蔬菜供应增加，部分叶菜价格回落，本地走货节奏同步变化。",
        "url": "https://www.moa.gov.cn/",
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
- `generate today agri card failed`
- `get today agri card failed`
- `mark daily agri card failed state failed`

## 失败处理

1. 确认模型 Key 池存在，且对应账号的模型联网搜索权限可用；多账号限流排查见 [model-key-pool.md](D:/wuhao/docs/runbooks/model-key-pool.md)。
2. 确认 `DAILY_AGRI_JOB_SECRET` 已配置，调用头和服务端环境一致。
3. 确认迁移 `014_daily_agri_cards.sql` 已执行。
4. 查询 `daily_agri_cards` 当天状态和 `error`。
5. 如果状态是 `pending` 且 `lease_until` 未过期，等待当前任务完成。
6. 如果状态是 `failed` 或 lease 已过期，可人工重新调用内部生成接口。
7. 如果错误是 `dashscope status 400` 且审计 / 日志里没有业务解析报错，优先核对今天这条链是否误回到了旧的 DashScope 原生 Generation 联网搜索路径，不要先怀疑定时器或后台补跑按钮。

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
- 有且只有 3 条有效 item
- 链接为 `https`
- 发布时间在近 7 天
- 链接来自 DashScope 返回的搜索结果
- 域名在可信官方 / 权威大站范围内
- 生成前会带入过去 7 天已 ready 的今日农情，要求模型不要重复同链接、同标题或同一事件
- 后端会硬过滤过去 7 天和当天候选里的重复链接 / 重复标题
- 不包含广告、导购、招商、联系方式、模型名、提示词、搜索参数、前端元表达、推荐理由、标题党等敏感或低质内容
- 选题按农业实用价值排序，优先具体地区、具体作物 / 品类、明确风险 / 农时 / 价格 / 补贴 / 流通影响的信息
- 标题和摘要必须是自然资讯口吻，禁止“值得看 / 参考意义 / 对农户有用 / 根据搜索结果”等元表达、推荐理由和标题党话术

过滤后不足 3 条时，不发布新卡片。不要为了当天一定有卡片而放宽到任意链接或用户打开 App 时临时多次调模型。

## 上线前检查

- 内部生成接口只允许定时任务 / 运维调用，不能由 Android 用户打开 App 时触发。
- 生成接口要配置 `DAILY_AGRI_JOB_SECRET`，并在 ECS / 定时任务侧保存，不进入 APK 或仓库。
- 后台至少能查看当天 `status/error/content_json/sources_json/lease_until`，并能手动补跑当天卡片。
- 如果连续失败，先查模型 Key、联网搜索权限、搜索来源返回、可信域名过滤和近 7 天去重，不要直接放宽到广告、软文或任意来源。

## 参考资料

- [阿里云百炼联网搜索](https://help.aliyun.com/zh/model-studio/web-search/)
- [阿里云百炼 DashScope API 参考](https://help.aliyun.com/zh/model-studio/qwen-api-via-dashscope)
- [阿里云百炼深度思考参数](https://help.aliyun.com/zh/model-studio/deep-thinking)

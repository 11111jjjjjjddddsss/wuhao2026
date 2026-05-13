# 今日农情 Runbook

本文记录“今日农情”每日资讯卡片的当前运维入口。

## 当前真相

- 今日农情是独立每日资讯卡片，不是聊天消息
- 不进入 A/B/C 上下文，不写 `session_ab` / `session_round_archive`，不触发摘要，不扣用户问诊次数
- 当前只有全国卡片：`scope = CN`
- 数据真源表：`daily_agri_cards`
- 用户只读接口：`GET /api/today-agri-card`
- 内部生成接口：`POST /internal/jobs/today-agri-card/generate`

## 环境变量

- `DASHSCOPE_API_KEY` 或 `DASHSCOPE_API_KEYS`：百炼模型 Key
- `DASHSCOPE_BASE_URL`：可选，默认 `https://dashscope.aliyuncs.com/api/v1`
- `DAILY_AGRI_JOB_SECRET`：内部生成接口密钥，必须配置；不要写入仓库

## 生成接口

生成接口只允许定时任务或人工运维调用：

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
        "summary": "批发市场监测显示多类蔬菜供应增加，部分叶菜价格回落，可关注本地走货节奏。",
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

1. 确认 `DASHSCOPE_API_KEY(S)` 存在，且模型联网搜索权限可用。
2. 确认 `DAILY_AGRI_JOB_SECRET` 已配置，调用头和服务端环境一致。
3. 确认迁移 `014_daily_agri_cards.sql` 已执行。
4. 查询 `daily_agri_cards` 当天状态和 `error`。
5. 如果状态是 `pending` 且 `lease_until` 未过期，等待当前任务完成。
6. 如果状态是 `failed` 或 lease 已过期，可人工重新调用内部生成接口。

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
- 不包含广告、导购、招商、联系方式、模型名、提示词、搜索参数等敏感或低质内容
- 选题按农业实用价值排序，优先具体地区、具体作物 / 品类、明确风险 / 农时 / 价格 / 补贴 / 流通影响的信息
- 标题和摘要必须是自然资讯口吻，禁止“值得看 / 参考意义 / 对农户有用 / 根据搜索结果”等元表达、推荐理由和标题党话术

过滤后不足 3 条时，不发布新卡片。不要为了当天一定有卡片而放宽到任意链接或用户打开 App 时临时多次调模型。

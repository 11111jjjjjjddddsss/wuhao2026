# 博查 Web Search 接入说明（联网搜索）

## Endpoint

- `https://api.bocha.cn/v1/web-search`（POST，JSON）

## 响应解析路径

- 成功结果列表：`root.data.webPages.value[]`（数组，每项含 name、url、snippet/summary）
- 展示层仅取前 5 条，灰字工具块挂在对应 assistant 气泡下，不注入模型 messages

## body.code 映射（业务失败）

| code | reason       | 前端文案（reasonToCopy）     |
|------|--------------|------------------------------|
| 401  | auth         | API 密钥无效，请检查配置。   |
| 403  | quota        | 余额不足，请充值后重试。     |
| 429  | rate_limit   | 请求过于频繁，请稍后重试。   |
| 400  | bad_request  | 请求参数有误，请重试。       |
| 其他 | server       | 服务繁忙，稍后重试。         |

HTTP 层：超时 → timeout；断网/IO → network；非 2xx → server。

## 4 条冒烟用例（验收必跑）

1. **正常 query**：如「阿里巴巴2024年的esg报告」→ 对应 assistant 气泡下出现灰字块，至少 1 条结果（标题+URL+摘要/片段），来源为 data.webPages.value。
2. **错 key/缺 key**：401 → 灰字「API 密钥无效，请检查配置。」，不显示「暂无结果」、不崩溃。
3. **403/429**：余额不足或限流 → 灰字「余额不足，请充值后重试。」或「请求过于频繁，请稍后重试。」，不崩溃。
4. **断网/超时**：灰字「网络不可用，点击重试。」或「请求超时（60 秒），点击重试。」，UI 不卡死、不崩溃。

---

*联网搜索板块已达标可冻结；实现见 `app/.../BochaClient.kt` 与前端 `reasonToCopy` / `renderToolResult`。*

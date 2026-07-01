# GPT 中转站评测参数 Runbook

本文记录第三方 GPT 中转站的本地评测口径，以及后端可选 `GPT_RELAY_*` 候选链路的当前边界，方便后续复测联网、图片、响应速度、成本和灰度开关。

当前生产主聊天已启用 `GPT_RELAY_*` 作为可插拔候选，首字前失败或超时回退百炼 / 千问。本文不代表可以恢复旧 `CHAT_PRIMARY_*`，也不允许把模型 Key、真实中转 URL、账号分组、后台截图里的密钥或订单信息写进仓库。

## 边界

- 真实 Key 和真实中转站 URL 只允许放在本机私密配置里，不能进仓库、日志、后台页面、项目记忆或聊天复述。
- Android 端仍然不能保存、注入或直连任何模型 Key。
- 评测时可以记录参数模板、速度、token、错误码和质量结论；不要记录完整密钥、完整请求头或完整供应商账单页。
- 当前结论用于人工评估、供应商更换和 `GPT_RELAY_*` 灰度观察。若要扩大流量、替换供应商或改变首字 / 思考 / 联网策略，必须重新走安全、稳定性、成本、隐私和真机回归决策。

## 当前代码接入口

后端已经有可开关的 `GPT_RELAY_*` 候选链路：

- `GPT_RELAY_ENABLED=false` 或未配置完整 Key / endpoint 时不触达 GPT，可快速回到千问主链。
- 不复用旧 `CHAT_PRIMARY_*`；旧变量仍应保持退役，readiness 继续拒绝 `CHAT_PRIMARY_ENABLED=true`。
- 只走 Responses 流式接口；不走普通 Chat Completions 联网。
- Key 配置兼容 `sk-...`、非 `sk-` 单 token、`label token`、`name=value` 和 `provider:token` 形态，方便后续换供应商；真实 Key 仍只进私密环境。
- 支持多个 Key 轮询；开流前失败或可重试状态可在当前 provider 内换下一把 Key，不做 GPT relay Key 冷却。当前 provider 在 20 秒内没有上游可见正文时回退 Bailian / Qwen。
- 单把 Key 开流前失败、可重试 HTTP 状态和后续换 Key 恢复会写入轻量脱敏日志：只记录 `attempt / max_attempts / key_slot / elapsed_ms / error_kind / status / will_retry` 等排障字段，`key_slot` 仅为配置槽位名（如 `GPT_RELAY_API_KEY_3` 或 `GPT_RELAY_API_KEYS_2`），不记录真实 Key、prompt、正文、图片 URL 或中转站完整地址。
- 多 provider 运行时代码会按用户请求轮 provider 起手；当前生产已因中转联盟首字不稳定收口为单 provider，只保留 `GPT_RELAY_PROVIDER_2_LABEL=大大科技` 和 5 把私密 Key。中转联盟 `GPT_RELAY_PROVIDER_1_*` 已从 ECS 私密环境移除并保留本机私密备份，后期需要再恢复。当前每一波 20 秒只打大大科技；provider 内部 Key 选择在并发下原子推进。
- 当前首字策略为单段 20 秒：从后端收到用户请求开始算，包含 provider 起手、同平台 Key 切换、连接、TLS、响应头等待和模型首个可见正文前等待；20 秒内没有上游可见正文首字，就回退 Bailian / Qwen。已吐出可见正文后不在同一条回复中途切模型。
- GPT relay 不再使用旧连接 / TLS / 响应头阶段切换限制，也不再使用 7+7 同轮短探针。这里的首字只认后端收到上游的用户可见正文，不认 `response.created`、搜索事件、思考 / reasoning 事件、心跳或空白 delta。
- GPT 请求带主对话锚点、`【输出约束】` / 回答参考范本、时间地点、记忆、历史上下文、本轮文字和图片；GPT 比千问只额外多一段 `【GPT专用规则】`（联网 / 带图高风险深度思考规则）。
- 为提高上游 prompt cache 命中，当前组装顺序把稳定规则前置：千问 / 百炼链路是“主对话锚点 + `【输出约束】` / 回答参考范本”先行，动态时间地点放到本轮用户消息前；GPT relay 的 Responses `instructions` 是“主对话锚点 + `【输出约束】` / 回答参考范本 + `【GPT专用规则】`”先行，记忆、历史上下文、动态时间地点等后置。今日农情不再进入主聊天 prompt，也不再影响请求 hash。该改动只调顺序和移除农情上下文注入，不改任何提示词正文或联网参数。
- 当前生产固定 `reasoning.effort=medium`、`web_search.search_context_size=low`、`tool_choice=auto`，并追加“一次联网、够用就答”的联网规则。`high` 曾临时验证，但图片问诊首字明显变慢，暂不作为生产口径。
- 不设置 `temperature`、`top_p`、`max_tokens`、`max_output_tokens` 或图片 `detail`。
- `/healthz` 和后台只暴露 `gpt_relay=disabled / ok / missing_config` 等非敏感状态，不暴露真实 URL、Key 数量、供应商名或账号分组。

## 首字兜底

当前 GPT relay 不再使用进程内熔断、Key 冷却、旧网络阶段切换或 7+7 同轮短探针。运行时只保留一个主动回退条件：后端在 20 秒内没有收到当前平台的上游用户可见正文首字，就回退 Bailian / Qwen；Key 只允许在同一个 provider 内快速轮换。`GPT_RELAY_CIRCUIT_*` 和 `GPT_RELAY_KEY_COOLDOWN_SECONDS` 属于退役变量，不应继续配置。

灰度前先在服务器私密环境写入脱敏模板对应的真实配置，跑 Key 连接性、文字、图片、联网和并发测试；观察慢、贵、错、断或隐私不可接受时，直接关 `GPT_RELAY_ENABLED=false` 回千问。当前生产只留大大科技时，重点观察大大科技首字、总耗时、图片识别、联网 token 和账单；中转联盟后期恢复前必须重新跑同样 smoke。

## 本机私密配置

本机中转站配置放在用户目录下的私密文件，Key 使用 Windows DPAPI 加密保存。

仓库只记录这一层抽象：

```text
provider_name=<本机私密配置中的供应商名>
base_url=<本机私密配置中的 OpenAI-compatible base url>
responses_url=<本机私密配置中的 Responses endpoint>
keys=<本机 DPAPI 加密后的 key 列表>
```

不要把 `base_url` 的真实值、`Authorization` 头或任何密钥明文抄进本文。

## 普通文字评测

优先走 Responses 接口。

```json
{
  "model": "gpt-5.5",
  "stream": true,
  "reasoning": {
    "effort": "medium"
  },
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "input_text",
          "text": "直接回答问题。先给结论，再给依据和下一步。不要表格，不要客套。"
        }
      ]
    }
  ]
}
```

评测重点：

- `first_byte_ms`：服务端开始返回 SSE 的时间。
- `first_text_ms`：用户可见正文首字时间。
- `total_ms`：完整结束时间。
- `input_tokens / output_tokens / reasoning_tokens`。
- 是否断流、卡死、429、503 或返回空正文。

2026-06-30 线上观察：第三方中转站控制台偶发 `input=0 / output=0 / cost=0` 的流式记录，不等于后端发送了空 prompt，也不等于请求完全没连上。当前主要按两类排查：

1. GPT 流已打开，但 20 秒内没有用户可见正文、客户端断开或上游首字前失败，后端主动关闭 GPT 流并回退 Bailian / Qwen；中转站没有拿到最终 usage / completed 事件，于是留下 0 用量残留。
2. GPT relay 内部某一把 Key 在当前 provider 内开流前失败，被后端立刻换同 provider 内下一把 Key；如果后续 Key 成功，用户最终仍看到 GPT 回复，Bailian / Qwen 不会产生调用量，但中转站控制台会留下前置 0 行。

判断时不要只看中转站面板。先看后端 SLS：`gpt relay key attempt failed` / `gpt relay key attempt retryable status` / `gpt relay key attempt recovered` 用来解释单把 Key 的 0 行；`gpt relay stream fallback to bailian`、`gpt relay fallback selected bailian`、`chat stream finished.provider`、`first_visible_ms`、`upstream_open_ms`、`model_input_tokens` 用来判断整条用户请求最终走 GPT 还是回退千问。

## 图片评测

图片评测必须同时分清官方 Responses 格式和 Chat Completions 格式，不要把两套 JSON 混着写。

官方 Responses 图片输入是：

```json
{
  "type": "input_image",
  "image_url": "data:image/jpeg;base64,<base64>",
  "detail": "auto"
}
```

也就是 `image_url` 是字符串，`detail` 和 `image_url` 平级。

官方 Chat Completions 图片输入是：

```json
{
  "type": "image_url",
  "image_url": {
    "url": "data:image/jpeg;base64,<base64>",
    "detail": "auto"
  }
}
```

也就是 `image_url` 是对象，`detail` 放在这个对象里。

对第三方中转站，不能只测 Responses。必须把 Responses 正确格式和 Chat 正确格式并排测，因为中转站可能把 Responses 转成 Chat、丢 `detail`、压缩图片、转存图片，或者两条接口走不同上游路由。

对 `gpt-5.5`，优先省略 `detail` 走模型默认细节；再用 `detail=auto`、`detail=high` 做对照。官方图像指南还提到 `original`，但本轮第三方中转评测不先硬传 `original`，避免被中转站参数白名单拒绝；只有确认该站完整支持后再单独对照。`high` / `original` 都不是“农技识图必准”开关。

```json
{
  "model": "gpt-5.5",
  "stream": true,
  "temperature": 0.3,
  "reasoning": {
    "effort": "medium"
  },
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "input_text",
          "text": "只看图片，不要联网。不给作物名。判断这张图最像什么病虫害或生理问题。先给主判断，再说依据、验证点、处理方向。证据不足就降置信。不要表格，不要客套，控制在400字内。"
        },
        {
          "type": "input_image",
          "image_url": "data:image/jpeg;base64,<base64>"
        }
      ]
    }
  ]
}
```

当前实测口径：

- 2026-06-29 第一轮曾用 `detail=high` 统一测试，但两家中转上没有明显改善识图质量，token 统计也没有明显变化。
- 2026-06-29 追加测试显示：同一张图上，省略 `detail` 反而比 `auto / high` 更稳；`temperature=0` 只适合 A/B 排查时压住随机摇摆，不适合作为正式用户回答口径。真实产品若要评估 GPT 图片问诊，优先低温但不要归零，例如 `temperature=0.2-0.4`，并靠提示词保留候选、验证点和风险边界。
- 2026-06-29 夜间用同一张本地极小玉米锈病样图并排打 Responses / Chat 正确格式：两站都能成功接收图片，单张图输入 token 约 5.2k；其中一家 Responses 和 Chat 都误向红蜘蛛 / 叶螨，另一家 Responses 误向红蜘蛛但 Chat 能说到锈病。这个结果只能说明两套正确格式都能被中转站接收，不能证明接口识图质量差，因为该轮病害样图本身只有约 10-20KB，最大样图也只有约 96KB，远低于真实手机原图质量。
- 2026-06-29 随后改用两张 Wikimedia 原始番茄晚疫病大图，按 App 单张 1MiB 限制压到约 865-887KiB 后重新跑 Responses 流式、中等思考、`detail` 省略：两家中转都识别到晚疫 / 疫霉类方向。首条 SSE 约 2.4-4.6 秒，可见正文首字约 3.8-5.9 秒，完整结束约 9.3-12.5 秒；单图输入 token 约 11.3k，`reasoning_tokens` 仍显示 0。这个结果说明清晰大图下 R / Responses 路线不是“带图就废”，前一轮小图误判不能作为接口质量结论。
- 给出作物信息或让用户补作物，明显比单纯调高清参数更有效。
- 本地放大 / 锐化只对极小图的某些锈病样本有帮助，对白粉病等粉状 / 点状症状可能把霉层误变成虫卵、白粉虱或螨害颗粒，因此不能默认全局增强。
- 本地样图如果只有十几 KB，病斑、粉疱、霉层细节本来就弱；评测结论不能直接代表用户 1MiB 左右原图。后续应补真实手机原图、裁剪近景、整株图和多图组合对照。
- 图片任务的 `reasoning_tokens` 经常低于文字任务，streaming 使用统计还可能显示为 0；不要只看这一项判断模型有没有认真看图。
- 本轮小图评测不能作为 GPT 中转站图片问诊的最终质量结论。后续若要判断 R / Responses 路线或 Chat 路线是否可用，必须继续换更多 1MiB 左右真实手机原图、清晰近景、整株图和多图组合重新测；当前只能说“清晰番茄晚疫大图在 R / Responses 流式上可用”，不能外推到所有作物和所有病害。

更稳的图片问诊提示词方向：

```text
只看图片，不要联网。判断这张图最像什么病虫害或生理问题。

先识别可见证据，不要把所有密集白点/褐点都默认判成螨害。
必须区分：粉状霉层、锈色粉疱、刺吸针点、普通坏死斑。

给主判断、候选、关键验证点。
证据不足就降置信。
不要表格，控制在 300 字内。
```

如果用户能提供作物，提示词前面加一句：

```text
这是<作物>叶片。
```

这比硬调 `detail=high` 或把温度降到 0 更能稳定农技判断。

## 联网搜索评测

联网搜索只用 Responses 工具调用评测。普通 Chat Completions 接口不要当作可靠联网入口。

当前可用模板：

```json
{
  "model": "gpt-5.5",
  "stream": true,
  "reasoning": {
    "effort": "medium"
  },
  "tools": [
    {
      "type": "web_search",
      "search_context_size": "low"
    }
  ],
  "tool_choice": "auto",
  "input": [
    {
      "role": "user",
      "content": [
        {
          "type": "input_text",
          "text": "只允许联网检索一次。拿到够用信息后立刻快速回答。不要解释搜索过程，不要说我会检索或我查到。直接给结论。\n\n问题：<用户问题>"
        }
      ]
    }
  ]
}
```

参数说明：

- `tools[].type=web_search`：本轮中转测试里这个可用；不要默认换成 `web_search_preview`。
- `search_context_size=low`：当前推荐低上下文，优先控成本和首字速度。
- `tool_choice=auto`：让模型自己决定是否调工具；提示词里再要求“只查一次、够用就答”。
- `reasoning.effort=medium`：当前折中档。`high` 更慢，低档质量不稳；后续可按真实质量再调。
- `max_tool_calls=1`：本轮中转测试返回不支持，不能当硬限制依赖。当前只能靠提示词软限制“一次搜索”。

不要加太多“限定网站”。限定过死会漏信息，尤其农业新闻、政策、价格、病虫害时效信息来源不固定。

## 联网实测结论

2026-06-29 本地评测结论：

- “只查一次 + low 搜索上下文 + medium 思考”能把工具调用压到 1 次，但单次联网仍可能吃约 14k-15k 输入 token。
- 首字时间通常在几秒到十几秒；完整结束时间常见 13-27 秒，供应商晚高峰会波动。
- 这个联网不像只返回几条搜索摘要，更像会拉较多网页内容给模型，因此 token 成本明显高于外部轻量搜索 API。
- 联网回答质量比纯关键词搜索自然，但成本和速度都不适合当前主聊天默认开启。

当前建议：

- 不把第三方 GPT 联网默认开成全量主链。
- 如果以后要用，只适合先通过 `GPT_RELAY_*` 小流量灰度，且必须有成本告警、首字超时、失败回退和人工观察。
- 主聊天日常问诊仍优先千问 / 百炼当前链路；`GPT_RELAY_*` 只能作为可关可退的候选链。

## 并发与质量评测

同一供应商评测时至少记录：

```text
provider
key_alias
endpoint_type=responses|chat
model
reasoning_effort
image_count
search_enabled
search_context_size
tool_call_count
first_byte_ms
first_text_ms
total_ms
input_tokens
output_tokens
reasoning_tokens
status_code
error_code
quality_note
```

并发测试不要只看“都返回 200”。要同时看：

- 首字是否排队变慢。
- 是否出现 429 / 503。
- 是否有流式半截卡死。
- 是否返回空正文。
- 图片问诊是否稳定识别作物和病虫害大类。
- 联网是否按一次搜索收住，token 是否失控。

## 不要做

- 不要把中转站 Key 或真实 URL 写进仓库。
- 不要把中转站默认接成全量生产主聊天，除非用户单独明确拍板并完成整套回归。
- 不要用 `CHAT_PRIMARY_*` 当回滚开关；当前 readiness 应继续拒绝 `CHAT_PRIMARY_ENABLED=true`。
- 不要让 Android 直连中转站。
- 不要只因为某一轮回答像 GPT、语气好，就忽略图片识别、首字速度、断流、token 成本和隐私责任。

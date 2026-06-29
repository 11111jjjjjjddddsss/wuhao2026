# GPT 中转站评测参数 Runbook

本文只记录第三方 GPT 中转站的本地评测口径，方便后续复测联网、图片、响应速度和成本。

当前生产主聊天仍是百炼 / 千问主链。本文不是生产接入方案，不代表可以恢复 `CHAT_PRIMARY_*`，也不允许把模型 Key、真实中转 URL、账号分组、后台截图里的密钥或订单信息写进仓库。

## 边界

- 真实 Key 和真实中转站 URL 只允许放在本机私密配置里，不能进仓库、日志、后台页面、项目记忆或聊天复述。
- Android 端仍然不能保存、注入或直连任何模型 Key。
- 评测时可以记录参数模板、速度、token、错误码和质量结论；不要记录完整密钥、完整请求头或完整供应商账单页。
- 当前结论只用于人工评估。若未来要重新进生产，必须重新走安全、稳定性、成本、隐私和真机回归决策。

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

## 图片评测

图片仍走 Responses `input_image`，不要说作物名，让模型自己识别。

对 `gpt-5.5`，优先省略 `detail` 走模型默认细节；再用 `detail=auto` 和 `detail=high` 做对照。官方图像指南口径里，`gpt-5.5` 的 `auto` / 省略默认接近原图细节；但 API reference 的 `input_image.detail` 枚举仍主要列 `low / high / auto`，所以中转站评测不要先硬传 `original`，避免被中转站参数白名单拒绝。`high` 不是“农技识图必准”开关。

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
- 给出作物信息或让用户补作物，明显比单纯调高清参数更有效。
- 本地放大 / 锐化只对极小图的某些锈病样本有帮助，对白粉病等粉状 / 点状症状可能把霉层误变成虫卵、白粉虱或螨害颗粒，因此不能默认全局增强。
- 图片任务的 `reasoning_tokens` 经常低于文字任务，streaming 使用统计还可能显示为 0；不要只看这一项判断模型有没有认真看图。
- 多图、病害图、作物不明时，两家中转都不够稳，容易把真菌病斑、锈病小疱、白粉病等误判成螨害或泛化叶斑。当前不能替代千问主图片问诊。

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

- 不把第三方 GPT 联网恢复成主链。
- 如果以后要用，只适合低频、明确需要新资料的问题，且必须有成本告警、首字超时、失败回退和人工观察。
- 主聊天日常问诊仍优先千问 / 百炼当前链路。

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
- 不要把中转站重新接成生产主聊天，除非用户单独明确拍板并完成整套回归。
- 不要用 `CHAT_PRIMARY_*` 当回滚开关；当前 readiness 应继续拒绝 `CHAT_PRIMARY_ENABLED=true`。
- 不要让 Android 直连中转站。
- 不要只因为某一轮回答像 GPT、语气好，就忽略图片识别、首字速度、断流、token 成本和隐私责任。

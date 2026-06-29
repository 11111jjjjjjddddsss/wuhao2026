# 模型 Key 池 Runbook

本文记录后端 DashScope / 百炼模型 Key 池的当前配置方式和排查口径。

## 当前真相

- Android 客户端不保存、不注入、不直连模型 Key；所有模型调用都只从 `server-go` 后端发起。
- 同一个阿里云主账号下的多个 API Key 共享该主账号的模型 RPM / TPM 限流，不能靠同账号多建 Key 扩真实并发。阿里云官方限流说明写明：限流按主账号下所有 RAM 子账号、业务空间、API Key 的调用总和计算。参考：[阿里云百炼限流说明](https://help.aliyun.com/zh/model-studio/rate-limit)。
- 如果目标是扩容前期并发，Key 池里的 Key 应来自不同阿里云主账号；同账号多个 Key 只适合轮换、隔离和应急，不适合当扩容方案。
- 主对话 `qwen3.5-plus`、记忆文档摘要 `qwen-plus`、今日农情 `qwen3.5-plus` 共用同一个 DashScope / 百炼 Key 池。当前生产口径支持“主账号 Key 组 + 副账号 Key 组”：主账号 4 把 Key 先在主组内轮询消耗，只有主组 Key 在开流前连续遇到限流 / 额度 / 鉴权类失败时，才切到副账号 Key 兜底。记忆文档摘要固定 `qwen-plus`，今日农情固定 `qwen3.5-plus`，不再保留轻量模型候选或环境变量切换入口。
- 2026-06-27 起，旧第三方中转站 / `gpt-5.5` / OpenAI Responses 优先主聊天链路已退出生产。`CHAT_PRIMARY_*` 不再是受支持的主聊天配置；readiness 会在发现 `CHAT_PRIMARY_ENABLED=true` 时失败，提醒清理环境变量并使用 Bailian / Qwen 主链。2026-06-29 后端新增独立的可选 `GPT_RELAY_*` 候选链路，默认关闭、不复用旧变量名；只有显式配置并开启后才会在主聊天开流前尝试 Responses 中转，失败或 15 秒无可见正文会回退 Bailian / Qwen。日志脱敏脚本可继续保留旧变量名并新增 `GPT_RELAY_*`，只用于覆盖历史日志或残留环境里的密钥形态。

## 环境变量

推荐在后端运行环境变量里显式区分主组和副组；ECS 路线可放在服务器环境文件 / systemd EnvironmentFile 中，若后续重新启用 SAE 再放到 SAE 环境变量：

```text
DASHSCOPE_PRIMARY_API_KEY_1=<主账号Key 1>
DASHSCOPE_PRIMARY_API_KEY_2=<主账号Key 2>
DASHSCOPE_PRIMARY_API_KEY_3=<主账号Key 3>
DASHSCOPE_PRIMARY_API_KEY_4=<主账号Key 4>
DASHSCOPE_SECONDARY_API_KEY_1=<副账号Key 1>
```

也支持列表形式：

```text
DASHSCOPE_PRIMARY_API_KEYS=<逗号/分号/换行分隔的多个主账号Key>
DASHSCOPE_SECONDARY_API_KEYS=<逗号/分号/换行分隔的多个副账号Key>
```

只要配置了 `DASHSCOPE_PRIMARY_*` 或 `DASHSCOPE_SECONDARY_*`，后端会优先使用这套分组配置，旧平铺变量只保留兼容，不再混入主副分组。

旧平铺配置仍兼容：

```text
DASHSCOPE_API_KEY_1=<主Key>
DASHSCOPE_API_KEY_2=<副Key>
DASHSCOPE_API_KEY_3=<账号C的Key，可暂空>
```

兼容旧配置：

```text
DASHSCOPE_API_KEY=<单Key旧配置>
DASHSCOPE_API_KEYS=<逗号/分号/换行分隔的多个Key>
```

读取顺序：

```text
DASHSCOPE_PRIMARY_API_KEY_1...50
DASHSCOPE_PRIMARY_API_KEYS
DASHSCOPE_SECONDARY_API_KEY_1...50
DASHSCOPE_SECONDARY_API_KEYS
```

如果没有配置分组变量，则回退旧读取顺序：

```text
DASHSCOPE_API_KEY_1
DASHSCOPE_API_KEY_2
DASHSCOPE_API_KEY_3
DASHSCOPE_API_KEY
DASHSCOPE_API_KEYS
```

后端会自动去重，重复 Key 不会重复进入主备池。推荐正式配置优先使用 `DASHSCOPE_PRIMARY_* / DASHSCOPE_SECONDARY_*`；旧 `DASHSCOPE_API_KEY_1/2/3`、`DASHSCOPE_API_KEY` 和 `DASHSCOPE_API_KEYS` 只作为兼容入口。

已退役主聊天中转站配置：

```text
CHAT_PRIMARY_ENABLED=false
```

说明：

- `CHAT_PRIMARY_*` 只作为历史残留和脱敏对象保留说明，不再用于主聊天生产链路。
- 如果服务器环境里仍有 `CHAT_PRIMARY_ENABLED=true`，请移除或设为 `false` 后重启后端；当前 readiness 会把它视为错误配置。
- 不要再把中转站 Key、真实 URL、账号分组或生产环境变量写入业务仓库、聊天记录、日志或后台页面。若用户明确要求记录评测参数，只能写脱敏模板和测试结论，入口见 [gpt-relay-evaluation.md](D:/wuhao/docs/runbooks/gpt-relay-evaluation.md)。

可选 GPT 中转候选链路配置：

```text
GPT_RELAY_ENABLED=false
GPT_RELAY_BASE_URL=<OpenAI-compatible base url>
# 或：GPT_RELAY_RESPONSES_URL=<Responses endpoint>
GPT_RELAY_API_KEYS=<逗号/分号/换行分隔的多个 relay key>
# 或：GPT_RELAY_API_KEY_1...50
GPT_RELAY_MODEL=gpt-5.5
GPT_RELAY_REASONING_EFFORT=medium
GPT_RELAY_FIRST_VISIBLE_TIMEOUT_SECONDS=15
GPT_RELAY_KEY_MAX_ATTEMPTS=5
```

说明：

- `GPT_RELAY_*` 是独立的新候选链路，不等于旧 `CHAT_PRIMARY_*` 复活。
- 默认 `GPT_RELAY_ENABLED=false` 或缺关键配置时完全不触达 GPT，中转站故障不会影响当前 Bailian / Qwen 主链。
- 真实 Key、真实 URL、供应商名、账号分组和后台订单信息只允许放在服务器私密环境或本机私密配置，不进仓库、不进日志、不进后台页面、不在聊天中复述。
- GPT relay 的思考档位默认 `reasoning.effort=medium`，只允许通过 `GPT_RELAY_REASONING_EFFORT=high` 临时切到 `high` 做生产观察；其它值会回落到 `medium`。联网仍固定 `web_search.search_context_size=low`、`tool_choice=auto` 和“用户明确要求查 / 实时信息才联网，必须只搜索一次、快速回答”的联网规则，当前代码不会把搜索上下文改成 `large`，避免误开高成本路径。
- 多 Key 会轮询；默认单轮最多尝试 5 把，某把开流前失败会立刻换下一把，失败 Key 只进入短冷却，不会让用户等 30 秒。
- GPT relay 不带千问专用 `【输出约束】` / 回答参考范本；它只带主对话锚点、时间地点、记忆、上下文、本轮文字和图片。
- 关闭或回滚只需要移除 `GPT_RELAY_*` 配置，或设置 `GPT_RELAY_ENABLED=false` 后重启服务；不需要 Android 发版。

## 运行策略

- 当前生产策略：分组配置下，主账号 Key 组优先，主组健康时按请求在主组内轮询，副账号 Key 不参与；主组开流前全部触发限流 / 额度 / 鉴权类失败后，才进入副组兜底。这样能让主账号套餐优先扣费，同时避免某一把主 Key 的短暂异常卡住同次请求。
- 旧平铺配置下，`DASHSCOPE_KEY_SELECTION_MODE=fallback` 仍表示平稳期始终优先使用第一把可用 Key；`DASHSCOPE_API_KEY_1` 健康且未冷却时，不因为请求数或 token 用量阈值主动消耗 `DASHSCOPE_API_KEY_2`。
- 强制主备优先可显式设置 `DASHSCOPE_KEY_SELECTION_MODE=fallback`（当前生产口径），用于主账号有套餐、希望主 Key 尽量吃满、副 Key 只做失败兜底的场景。
- 可选自动高峰分流：`auto` 模式会按两个信号临时进入请求级轮询分流：默认 10 秒内达到 200 次模型请求，或 10 秒内已观测模型用量达到 600000 token。窗口内健康 Key 会按轮询顺序选择，默认持续 120 秒，窗口结束后自动回到主 Key 优先。该模式后续只有在真实高峰长期打满主 Key、且用户接受副 Key 主动承担流量时再启用。
- 可选自动故障分流：`auto` 模式下如果某把 Key 开流前已经触发限流 / 鉴权类 failover，后端也会自动进入一段轮询窗口，避免后续高峰继续压同一把 Key；当前 `fallback` 模式不主动开启这段轮询窗口，但同一次请求内的失败切副 Key 仍保留。
- 强制平滑分流仍可显式设置 `DASHSCOPE_KEY_SELECTION_MODE=round_robin`（或 `rr`）。该模式在同一批请求间按轮询顺序选择健康 Key，仍保留每次请求内的限流 / 鉴权失败兜底切换。
- 如果某把 Key 在模型请求打开阶段返回 `401 / 403 / 429`，或返回带限流 / quota 语义的 `400`，后端会在响应交给业务层前换下一把 Key 再试。
- 触发上述限流 / 鉴权类失败的 Key 会进入短暂冷却，默认 1 秒，可用 `DASHSCOPE_KEY_COOLDOWN_SECONDS` 调整；后续请求会优先跳过冷却中的 Key。
- 主对话只在 SSE 流真正开始前换 Key；一旦上游已经返回成功 SSE，后端不会在同一条回复生成过程中切换 Key，避免半条回复和重复成本。
- `CHAT_PRIMARY_*` 已退出主聊天运行路径；如果需要排查历史中转站问题，只看历史日志和 recent-changes，不要在生产重新打开该配置。需要灰度第三方 GPT 时只允许走默认关闭的 `GPT_RELAY_*`，并保留 Bailian / Qwen 快速回退。
- 如果所有 Key 都限流或不可用，最后一次上游错误会正常返回给业务层，Android 仍按现有失败提示处理。

## 联网搜索限流

- 阿里云官方文档说明：联网搜索限流是 15 RPS，按阿里云主账号维度计算，所有 API Key 的联网搜索请求总和计入，不区分模型。
- 超过联网搜索限流时，API 不会报错，但搜索链路不会触发；主聊天会自然退成未联网回答，不需要后端额外打一轮“不联网重试”。
- 这个 15 RPS 是联网搜索链路限制，不等同于模型 RPM / TPM。模型请求仍受对应模型的 RPM / TPM 约束，Key 池 auto 模式只负责请求级分流和开流前错误 failover。
- 主聊天默认 `forced_search=false`，所以模型判断无需联网、或联网搜索触发限流时，都可能不触发搜索；当后端识别到价格、行情、购买渠道、查资料等明确联网意图时，会保留 `ForceSearch=true` 并按原口径走 `turbo` 搜索。今日农情是强制联网生成，仍需单独观察生成任务成功率。

新增配置：

```text
DASHSCOPE_KEY_SELECTION_MODE=fallback
# 取值：fallback（默认）|auto|round_robin|rr
# fallback 是当前生产口径；下面 auto 阈值保留为可选高峰分流配置，fallback 模式下不生效。
DASHSCOPE_AUTO_ROUND_ROBIN_MIN_REQUESTS=200
DASHSCOPE_AUTO_ROUND_ROBIN_TOKEN_THRESHOLD=600000
DASHSCOPE_AUTO_ROUND_ROBIN_WINDOW_SECONDS=10
DASHSCOPE_AUTO_ROUND_ROBIN_HOLD_SECONDS=120
```

## 配置建议

- 当前生产建议：4 把主账号 Key 放在 `DASHSCOPE_PRIMARY_API_KEY_1...4`，副账号 Key 放在 `DASHSCOPE_SECONDARY_API_KEY_1`。主账号 4 把 Key 对应同一套餐账号，作为主扣费组；副账号只做开流前异常兜底。
- 如果两把 Key 来自同一个阿里云主账号，它们只提供故障兜底、轮换和短暂冷却切换，不增加真实 RPM / TPM。
- 朋友账号 Key 适合作为短期兜底，不建议作为长期生产主力：账单、密钥轮换、数据处理责任和账号权限都不在自己名下，后续迁移成本会变高。
- 正式生产期尽量使用自己可控的多个主体 / 主账号，并把充值、告警、密钥轮换、日志排查都收回到自己的运维清单里。

## 排查

1. 确认后端运行环境变量已配置至少一把 Key，且没有把真实 Key 写进仓库。
2. 如果仍频繁限流，先确认 Key 是否来自不同阿里云主账号；同主账号多个 Key 不会增加真实 RPM / TPM。
3. 查看后端日志里的上游状态码：`429` 通常是请求或 token 限流，`401 / 403` 多数是 Key 权限、状态或账号问题。
4. 如果主聊天慢，先看日志里的 `provider`：当前生产应为 `bailian`。若新日志里仍出现 `primary_responses` 或 `primary`，说明线上 revision、环境变量或历史日志查询窗口需要重新核对；当前新代码 `/healthz` 不再输出 `chat_primary`。
5. 如果只有今日农情失败，确认该 Key 所在账号是否开通联网搜索能力；今日农情当前固定使用 `qwen3.5-plus + OpenAI compatible chat/completions + enable_search=true + search_strategy=turbo + forced_search=true + enable_source=true + enable_thinking=false`。`agent / agent_max` 会带来更多检索和 token 成本，今日农情默认不用。日志会尽量记录 `model_input_tokens / model_output_tokens / model_total_tokens / model_reasoning_tokens / model_search_count`，优先用这些字段判断成本、思考是否关闭和搜索是否触发；兼容 Chat 链路通常没有结构化搜索来源列表，`source_count=0` 不一定表示没联网。2026-06-08 的 `qwen3.5-plus + Responses web_search` 只是旧排障阶段结论，不再是当前生产主线。
6. 如果在评估记忆文档摘要模型成本，不要只看 `qwen-plus` 资源包单价。按用户提供的两档包价计算：`12000千 token / 11.66元` 折合约 `0.972元 / 百万 token`，`110000千 token / 99.4元` 折合约 `0.904元 / 百万 token`；而 `qwen-plus` 按量输入约 `0.8元 / 百万`、输出约 `2元 / 百万`，后一档资源包也要输出 token 占比超过约 `8.6%` 才比 plus 按量更划算。记忆文档摘要通常是输入长、输出短，资源包本身不是省钱保证；当前先按质量优先固定 `qwen-plus`。
7. 如果要临时回滚到单 Key，只保留 `DASHSCOPE_API_KEY` 或只保留 `DASHSCOPE_API_KEY_1`，删除其它 Key 槽位后重启后端。`CHAT_PRIMARY_*` 已退出主聊天主链，不能再作为回滚开关；若环境里还有 `CHAT_PRIMARY_ENABLED=true`，readiness 会失败，需移除或设为 `false`。

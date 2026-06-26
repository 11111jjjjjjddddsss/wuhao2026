# 模型 Key 池 Runbook

本文记录后端 DashScope / 百炼模型 Key 池的当前配置方式和排查口径。

## 当前真相

- Android 客户端不保存、不注入、不直连模型 Key；所有模型调用都只从 `server-go` 后端发起。
- 同一个阿里云主账号下的多个 API Key 共享该主账号的模型 RPM / TPM 限流，不能靠同账号多建 Key 扩真实并发。阿里云官方限流说明写明：限流按主账号下所有 RAM 子账号、业务空间、API Key 的调用总和计算。参考：[阿里云百炼限流说明](https://help.aliyun.com/zh/model-studio/rate-limit)。
- 如果目标是扩容前期并发，Key 池里的 Key 应来自不同阿里云主账号；同账号多个 Key 只适合轮换、隔离和应急，不适合当扩容方案。
- 当前后端会对主对话 `qwen3.5-plus`、记忆文档摘要 `qwen-plus`、今日农情 `qwen3.5-plus` 共用同一个 Key 池，按配置顺序做主备使用。生产当前使用 `DASHSCOPE_KEY_SELECTION_MODE=fallback`：主 Key 优先吃满，副 Key 只在主 Key 开流前失败时兜底。记忆文档摘要固定 `qwen-plus`，今日农情固定 `qwen3.5-plus`，不再保留轻量模型候选或环境变量切换入口。
- 主对话另有可选的“优先中转站”链路：`CHAT_PRIMARY_ENABLED=true` 时，后端会先调用中转站 OpenAI 兼容流式接口；开流前失败、超时、非 2xx 或非 SSE 时，立即回落原 DashScope / 百炼主备 Key 池。一旦上游 SSE 已开始，不在同一条回复中途切换。

## 环境变量

推荐在后端运行环境变量里预留 2 到 3 个独立账号 Key；ECS 路线可放在服务器环境文件 / systemd EnvironmentFile 中，若后续重新启用 SAE 再放到 SAE 环境变量：

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
DASHSCOPE_API_KEY_1
DASHSCOPE_API_KEY_2
DASHSCOPE_API_KEY_3
DASHSCOPE_API_KEY
DASHSCOPE_API_KEYS
```

后端会自动去重，重复 Key 不会重复进入主备池。推荐正式配置优先使用 `DASHSCOPE_API_KEY_1/2/3`；旧 `DASHSCOPE_API_KEY` 和 `DASHSCOPE_API_KEYS` 只作为兼容入口。

可选主聊天中转站配置：

```text
CHAT_PRIMARY_ENABLED=true
CHAT_PRIMARY_BASE_URL=<中转站基础地址>
CHAT_PRIMARY_CHAT_COMPLETIONS_URL=<可选，完整 /chat/completions 地址>
CHAT_PRIMARY_API_KEY=<中转站Key>
CHAT_PRIMARY_MODEL=gpt-5.5
CHAT_PRIMARY_FORCE_SEARCH=true
CHAT_PRIMARY_DIAL_TIMEOUT_SECONDS=3
CHAT_PRIMARY_TLS_HANDSHAKE_TIMEOUT_SECONDS=3
CHAT_PRIMARY_RESPONSE_HEADER_TIMEOUT_SECONDS=3
CHAT_PRIMARY_IDLE_CONN_TIMEOUT_SECONDS=60
```

说明：

- `CHAT_PRIMARY_CHAT_COMPLETIONS_URL` 为空时，后端会把 `CHAT_PRIMARY_BASE_URL` 拼成 OpenAI 兼容 `/v1/chat/completions`。
- 中转站请求显式发送 `temperature=0.8`、`enable_thinking=false`、`enable_search=true` 和流式返回，不发送 `top_p`、`max_tokens` 或 `thinking_budget`。
- `CHAT_PRIMARY_FORCE_SEARCH=true` 表示优先中转站链路默认强制联网；回落千问时仍按原主聊天搜索策略。
- 中转站密钥只能放服务器环境变量 / 本机私密配置，不写入仓库、runbook、聊天记录、日志或后台页面。

## 运行策略

- 当前生产策略：`DASHSCOPE_KEY_SELECTION_MODE=fallback`。平稳期始终优先使用第一把可用 Key；`DASHSCOPE_API_KEY_1` 健康且未冷却时，不因为请求数或 token 用量阈值主动消耗 `DASHSCOPE_API_KEY_2`。
- 强制主备优先可显式设置 `DASHSCOPE_KEY_SELECTION_MODE=fallback`（当前生产口径），用于主账号有套餐、希望主 Key 尽量吃满、副 Key 只做失败兜底的场景。
- 可选自动高峰分流：`auto` 模式会按两个信号临时进入请求级轮询分流：默认 10 秒内达到 200 次模型请求，或 10 秒内已观测模型用量达到 600000 token。窗口内健康 Key 会按轮询顺序选择，默认持续 120 秒，窗口结束后自动回到主 Key 优先。该模式后续只有在真实高峰长期打满主 Key、且用户接受副 Key 主动承担流量时再启用。
- 可选自动故障分流：`auto` 模式下如果某把 Key 开流前已经触发限流 / 鉴权类 failover，后端也会自动进入一段轮询窗口，避免后续高峰继续压同一把 Key；当前 `fallback` 模式不主动开启这段轮询窗口，但同一次请求内的失败切副 Key 仍保留。
- 强制平滑分流仍可显式设置 `DASHSCOPE_KEY_SELECTION_MODE=round_robin`（或 `rr`）。该模式在同一批请求间按轮询顺序选择健康 Key，仍保留每次请求内的限流 / 鉴权失败兜底切换。
- 如果某把 Key 在模型请求打开阶段返回 `401 / 403 / 429`，或返回带限流 / quota 语义的 `400`，后端会在响应交给业务层前换下一把 Key 再试。
- 触发上述限流 / 鉴权类失败的 Key 会进入短暂冷却，默认 1 秒，可用 `DASHSCOPE_KEY_COOLDOWN_SECONDS` 调整；后续请求会优先跳过冷却中的 Key。
- 主对话只在 SSE 流真正开始前换 Key；一旦上游已经返回成功 SSE，后端不会在同一条回复生成过程中切换 Key，避免半条回复和重复成本。
- 主对话优先中转站同样只在开流前回落：如果中转站已经返回成功 SSE 头，后端会继续读该条流；若后续首字慢或内容质量差，本轮不会中途换到千问，避免半条回答和重复扣费。需要优化首字体验时，优先调小 `CHAT_PRIMARY_RESPONSE_HEADER_TIMEOUT_SECONDS` 或直接关闭 `CHAT_PRIMARY_ENABLED`。
- 如果所有 Key 都限流或不可用，最后一次上游错误会正常返回给业务层，Android 仍按现有失败提示处理。

## 联网搜索限流

- 阿里云官方文档说明：联网搜索限流是 15 RPS，按阿里云主账号维度计算，所有 API Key 的联网搜索请求总和计入，不区分模型。
- 超过联网搜索限流时，API 不会报错，但搜索链路不会触发；主聊天会自然退成未联网回答，不需要后端额外打一轮“不联网重试”。
- 这个 15 RPS 是联网搜索链路限制，不等同于模型 RPM / TPM。模型请求仍受对应模型的 RPM / TPM 约束，Key 池 auto 模式只负责请求级分流和开流前错误 failover。
- 当前主聊天 `forced_search=false`，所以模型判断无需联网、或联网搜索触发限流时，都可能不触发搜索；今日农情是强制联网生成，仍需单独观察生成任务成功率。

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

- 前期可以先配两把 Key：`DASHSCOPE_API_KEY_1` 作为主 Key，`DASHSCOPE_API_KEY_2` 作为副 Key。
- 当前生产口径是“主 Key 套餐优先，副 Key 失败兜底”：低中高流量都先走 `DASHSCOPE_API_KEY_1`；出现开流前限流 / 额度 / 鉴权类错误时立即尝试副 Key；不再因为短窗口请求数或 token 压力阈值提前轮询。等真实流量长期打满主 Key、且账单可接受副 Key 主动承担流量时，再评估切回 `auto` 或 `round_robin`。
- 第三把 `DASHSCOPE_API_KEY_3` 先留空，后续真实并发上来后再补。
- 如果两把 Key 来自同一个阿里云主账号，它们只提供故障兜底、轮换和短暂冷却切换，不增加真实 RPM / TPM。
- 朋友账号 Key 适合作为短期兜底，不建议作为长期生产主力：账单、密钥轮换、数据处理责任和账号权限都不在自己名下，后续迁移成本会变高。
- 正式生产期尽量使用自己可控的多个主体 / 主账号，并把充值、告警、密钥轮换、日志排查都收回到自己的运维清单里。

## 排查

1. 确认后端运行环境变量已配置至少一把 Key，且没有把真实 Key 写进仓库。
2. 如果仍频繁限流，先确认 Key 是否来自不同阿里云主账号；同主账号多个 Key 不会增加真实 RPM / TPM。
3. 查看后端日志里的上游状态码：`429` 通常是请求或 token 限流，`401 / 403` 多数是 Key 权限、状态或账号问题。
4. 如果主聊天慢，先看日志里的 `provider`：`primary` 表示走中转站，`bailian` 表示已回落千问。`/healthz` 的 `chat_primary=ok` 只代表中转站配置完整，不代表每条实际生成质量或首字速度都达标。
5. 如果只有今日农情失败，确认该 Key 所在账号是否开通联网搜索能力；今日农情当前固定使用 `qwen3.5-plus + OpenAI compatible chat/completions + enable_search=true + search_strategy=turbo + forced_search=true + enable_source=true + enable_thinking=false`。`agent / agent_max` 会带来更多检索和 token 成本，今日农情默认不用。日志会尽量记录 `model_input_tokens / model_output_tokens / model_total_tokens / model_reasoning_tokens / model_search_count`，优先用这些字段判断成本、思考是否关闭和搜索是否触发；兼容 Chat 链路通常没有结构化搜索来源列表，`source_count=0` 不一定表示没联网。2026-06-08 的 `qwen3.5-plus + Responses web_search` 只是旧排障阶段结论，不再是当前生产主线。
6. 如果在评估记忆文档摘要模型成本，不要只看 `qwen-plus` 资源包单价。按用户提供的两档包价计算：`12000千 token / 11.66元` 折合约 `0.972元 / 百万 token`，`110000千 token / 99.4元` 折合约 `0.904元 / 百万 token`；而 `qwen-plus` 按量输入约 `0.8元 / 百万`、输出约 `2元 / 百万`，后一档资源包也要输出 token 占比超过约 `8.6%` 才比 plus 按量更划算。记忆文档摘要通常是输入长、输出短，资源包本身不是省钱保证；当前先按质量优先固定 `qwen-plus`。
7. 如果要临时回滚到单 Key，只保留 `DASHSCOPE_API_KEY` 或只保留 `DASHSCOPE_API_KEY_1`，删除其它 Key 槽位后重启后端。若要回滚中转站主链，只设置 `CHAT_PRIMARY_ENABLED=false` 或删除 `CHAT_PRIMARY_API_KEY` 后重启后端，不需要改 Android。

# 模型 Key 池 Runbook

本文记录后端 DashScope / 百炼模型 Key 池的当前配置方式和排查口径。

## 当前真相

- Android 客户端不保存、不注入、不直连模型 Key；所有模型调用都只从 `server-go` 后端发起。
- 同一个阿里云主账号下的多个 API Key 共享该主账号的模型 RPM / TPM 限流，不能靠同账号多建 Key 扩真实并发。阿里云官方限流说明写明：限流按主账号下所有 RAM 子账号、业务空间、API Key 的调用总和计算。参考：[阿里云百炼限流说明](https://help.aliyun.com/zh/model-studio/rate-limit)。
- 如果目标是扩容前期并发，Key 池里的 Key 应来自不同阿里云主账号；同账号多个 Key 只适合轮换、隔离和应急，不适合当扩容方案。
- 当前后端会对主对话 `qwen3.5-plus`、B/C 摘要 `qwen3.5-flash`、今日农情 `qwen3.5-plus` 共用同一个 Key 池，按配置顺序做主备使用。

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

## 运行策略

- 正常请求优先使用第一把可用 Key；`DASHSCOPE_API_KEY_1` 健康且未冷却时，不会主动轮询到 `DASHSCOPE_API_KEY_2`。
- 如果某把 Key 在模型请求打开阶段返回 `401 / 403 / 429`，或返回带限流 / quota 语义的 `400`，后端会在响应交给业务层前换下一把 Key 再试。
- 触发上述限流 / 鉴权类失败的 Key 会进入短暂冷却，默认 1 秒，可用 `DASHSCOPE_KEY_COOLDOWN_SECONDS` 调整；后续请求会优先跳过冷却中的 Key。
- 主对话只在 SSE 流真正开始前换 Key；一旦上游已经返回成功 SSE，后端不会在同一条回复生成过程中切换 Key，避免半条回复和重复成本。
- 如果所有 Key 都限流或不可用，最后一次上游错误会正常返回给业务层，Android 仍按现有失败提示处理。

## 配置建议

- 前期可以先配两把 Key：`DASHSCOPE_API_KEY_1` 作为主 Key，`DASHSCOPE_API_KEY_2` 作为副 Key。
- 第三把 `DASHSCOPE_API_KEY_3` 先留空，后续真实并发上来后再补。
- 如果两把 Key 来自同一个阿里云主账号，它们只提供故障兜底、轮换和短暂冷却切换，不增加真实 RPM / TPM。
- 朋友账号 Key 适合作为短期兜底，不建议作为长期生产主力：账单、密钥轮换、数据处理责任和账号权限都不在自己名下，后续迁移成本会变高。
- 正式生产期尽量使用自己可控的多个主体 / 主账号，并把充值、告警、密钥轮换、日志排查都收回到自己的运维清单里。

## 排查

1. 确认后端运行环境变量已配置至少一把 Key，且没有把真实 Key 写进仓库。
2. 如果仍频繁限流，先确认 Key 是否来自不同阿里云主账号；同主账号多个 Key 不会增加真实 RPM / TPM。
3. 查看后端日志里的上游状态码：`429` 通常是请求或 token 限流，`401 / 403` 多数是 Key 权限、状态或账号问题。
4. 如果只有今日农情失败，确认该 Key 所在账号是否开通联网搜索能力；今日农情强制 `enable_search=true`、`search_strategy=max`。
5. 如果要临时回滚到单 Key，只保留 `DASHSCOPE_API_KEY` 或只保留 `DASHSCOPE_API_KEY_1`，删除其它 Key 槽位后重启后端。

# Codex 自动化 Runbook

本文记录“农技千查”本机 Codex 全局自动化的期望配置和安全边界，方便后续窗口接手核查。Codex 自动化属于本机全局工具配置，不是业务仓库代码；仓库只记录标准口径，实际创建 / 修改仍以 Codex App 自动化页面或官方自动化工具为准。

## 总边界

- 自动化默认项目目录：`D:\wuhao`
- 自动化运行前应先读取根 `AGENTS.md`、`docs/project-state/current-status.md`、`docs/project-state/recent-changes.md` 和对应 runbook。
- 自动化不得打印密钥、token、AccessKey、数据库密码、模型 Key、内部 job secret 或后台密码。
- 自动化不得修改主对话锚点、今日农情提示词、记忆文档提示词、官网首页定稿文案或主聊天滚动主方案。
- 自动化不得新增模型输出内容过滤、关键词拦截、相似度硬拦截、字数硬卡、`max_tokens` 截断或用户 / 客服内容拦截。
- 自动化若不是专门的代码维护任务，不得 `apply_patch`，不得 `git add / commit / push`，不得修改业务仓库文件或本机配置。
- 需要买资源、续费、删除云资源、轮换 AccessKey、发布正式 Android 包、配置检查更新对外下发时，只能报告建议和证据，不能自动执行高风险动作。

## 今日农情人工发布

用途：晚上由 Codex 根据公开农业新闻写作并发布次日 / 当日“今日农情”三条内容；它只是人工发布助手，不替代 ECS 后端自动生成兜底。

期望配置：

- 名称：`今日农情人工发布`
- 项目：`wuhao`
- 工作目录：`D:\wuhao`
- 运行环境：本地
- 模型：`gpt-5.5`
- 推理强度：`xhigh`
- 时间：北京时间每天 `22:00` 和 `23:00`

如果 Codex App 一个自动化只能绑定一个时间，应拆成两个启用状态的自动化：

- `今日农情人工发布 22点`：每天北京时间 `22:00`
- `今日农情人工发布 23点`：每天北京时间 `23:00`

执行口径：

1. 读取 `AGENTS.md`、`docs/project-state/current-status.md`、`docs/project-state/recent-changes.md`、`docs/runbooks/today-agri-card.md`。
2. 按 Asia/Shanghai 判断目标日期：18:00 后默认发布次日，18:00 前默认发布当天；如用户在当前线程另有明确日期，以用户日期为准。
3. 先运行状态脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\get-today-agri-manual-status.ps1 -DayCN YYYYMMDD
```

4. 如果返回 `manual_locked=true` 且 `source_type=manual`，或 `should_publish=false`，应跳过，不查新闻、不覆盖。
5. 需要发布时，查最近 7 天中国种植侧公开农业新闻，先拉出 6 到 10 个候选，再选三条分散地区 / 作物 / 主题且有生产参考价值的内容；候选不够时才收窄，不要一开始只盯三夏、防灾、蔬菜价格这几类安全题。选题可覆盖栽培管理、植保病虫、苗情墒情、政策补贴、农资农机、种业种苗、节水灌溉、绿色防控、技术培训、产地流通、批发价格、仓储保鲜、特色作物、地方农技推广和区域生产动态；最近 3 天已写过的同地区 / 同作物 / 同进展，只有出现明显新进展才继续写，否则优先换方向。放宽选题不等于写养殖水产、广告软文、旧闻、泛政策口号或没生产关系的宏观新闻；去重靠模型主动避开旧内容，不新增后端内容过滤。
6. 先用 `scripts/publish-today-agri-manual.ps1 -DryRun` 校验参数和确认词；正常后去掉 `-DryRun` 发布。
7. 发布失败时只报告失败原因和三条草稿，不打印密钥；后端 ECS 05:35 左右自动生成兜底继续存在。

禁止事项：

- 不改今日农情提示词，不改脚本，不改业务代码，不改项目文档。
- 不调用模型生成接口直接写库，不绕过发布脚本。
- 不把今日农情写入聊天归档、记忆文档或扣次链。
- 不在 23:00 覆盖 22:00 已成功人工锁定的内容。

已知边界：

- 项目级 Codex 自动化要求运行时间点本机开机、Codex App 正在运行、项目路径仍存在。电脑关机 / 休眠错过 22:00 和 23:00 时，当前仓库没有 Windows 开机补跑任务；若需要补跑，应另做启动补偿设计，并先确认不会覆盖已人工锁定内容。
- ECS systemd 今日农情 timer 是独立后端兜底，约北京时间 05:35 主触发，并有早晨补查；它不依赖本机 Codex。

## 运维自动化

用途：每天只读巡检后台行动项、待处理队列、登录 / 闪退 / 今日农情 / 扣次自动对账等日常状态，目标是异常才提醒，不要求负责人天天打开后台。

期望配置：

- 名称：`运维自动化`
- 工作目录：`D:\wuhao`
- 运行环境：本地
- 时间：每天北京时间 `23:00`

核心命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-admin-monitoring-actions.ps1
```

边界：

- 只读巡检；低权限后台巡检账号密码只保存在本机私密 `%USERPROFILE%\.nongjiqiancha\prod-secrets.json`，不得写入仓库或输出。
- 默认不把上线人工确认项当成日常故障；正式上线门禁另跑 `check-launch-readiness.ps1`。
- 发现异常应报告原因、入口和建议命令，不自动购买资源、不自动删资源、不自动发布正式 App。

## 续费与证书巡检

用途：低频检查 ECS / RDS / Redis / OSS / 域名 / HTTPS 证书 / OSS 下载域名证书 / 模型套餐 / 短信包 / 账户余额 / 成本趋势。

期望配置：

- 名称：`农技千查续费与证书巡检`
- 工作目录：`D:\wuhao`
- 运行环境：本地
- 时间：每周一次即可

建议只读命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-resource-capacity.ps1 -Strict
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-aliyun-costs.ps1
```

条件动作：

- 如果 `check-resource-capacity.ps1 -Strict` 或 `scripts/check-android-download-domain.ps1` 明确提示 `download.nongjiqiancha.cn` 的 ECS / Let’s Encrypt 证书已续期但 OSS CNAME 证书仍是旧证书，才运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\sync-oss-download-certificate.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-android-download-domain.ps1
```

边界：

- 能查就查，能提醒就提醒；不能自动续费、自动购买资源包、自动关闭云服务或自动删除告警。
- 下载域名证书同步只做“证书已续期但 OSS 仍未同步”的修复闭环，不作为每天常规动作；脚本不得打印私钥、AccessKey 或证书私密内容。

## 核查方式

若当前窗口没有 Codex 自动化工具权限，应直接说明“本窗口无法修改本机全局自动化”，并把本 runbook 的配置发给用户或新线程处理。不要为了绕过 UI 锁去修改 Codex 内部数据库、缓存文件或未公开配置；这种破解式改法不稳定，也可能破坏本机 Codex。

官方口径：Codex project-scoped automations 需要本机 Codex App 在计划时间运行、所选项目仍在磁盘上，自动化可选择项目、提示词、节奏和执行环境；线程自动化适合依赖当前会话上下文的任务，项目 / 独立自动化适合固定周期任务。

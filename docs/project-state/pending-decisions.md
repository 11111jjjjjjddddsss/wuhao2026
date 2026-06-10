# 待决策事项

最后更新：2026-06-10

## D1 运维入口优先固化到什么形式

- 当前选项：仓库脚本、阿里云 CLI / OpenAPI、统一管理后台、纯 runbook
- 现状：主规则已明确“脚本 / CLI / OpenAPI 优先”；`docs/runbooks/operations-blueprint.md` 已补整体 App / 后端 / 管理后台的 Codex 协助运维总蓝图。ECS、RDS、Redis、OSS、DNS、部署 / 回滚脚本、SLS 最小日志集和第一版网页后台代码已经落地；后台形态已定为 Vite `admin` 静态前端 + `server-go` `/admin-api/v1/*` 管理 API，首版已有后台账号 / session / CSRF / 角色校验 / 审计
- 待定原因：仍需结合真实发版、回滚、查日志、查库、客服回复、礼品卡和会员运营频率，决定哪些能力继续做网页后台，哪些沉淀成脚本 / CLI 封装；SLS 已有最小 AlertHub 告警，但外部通知 / 仪表盘和数据库只读脚本仍待补齐

## D2 是否引入 GitHub 协作模板层

- 当前选项：保持仓库内文档为主，或继续补 Issue 模板 / PR 模板 / CODEOWNERS / Projects
- 现状：当前先落了仓库内长期记忆骨架，GitHub 协作模板还没加
- 待定原因：等后续多人协作、运维频率、需求管理复杂度上来后再决定

## D3 项目记忆是否继续细化到“按变更类型校验特定章节”

- 当前选项：维持当前“至少更新一份 memory 文件”的校验，或继续细化为“改 UI 主链必须同步更新 current-status/recent-changes，改风险口径必须同步更新 open-risks”等更严格规则
- 现状：`scripts/check_project_memory.py` 和 CI 已接入，程序化校验不再是空白；当前待定的是要不要继续收紧粒度
- 待定原因：更严格的校验能减少文档漂移，但也会提高提交门槛，需要结合后续实际误报率和维护成本再决定

## D4 正式云资源首版怎么落

- 当前选项：首版已按 `ECS + RDS MySQL + OSS + 域名/HTTPS` 跑最小生产链推进；`SAE + ACR` 镜像托管路线已降级为备选。PolarDB 暂作为后续高并发 / 更高规格升级选项，不再作为个人创业首版默认采购项；短期先单环境跑通，是否拆测试 / 生产等真实联调后再评估
- 现状：Region 已按 `华北2（北京）/ cn-beijing` 落地。ECS `i-2ze5nrem0jrchln4f0eh` 已购买并运行，可用区 L，规格 `ecs.u1-c1m2.large`（2 vCPU / 4 GiB），Ubuntu 22.04 64 位，公网 IP `39.106.1.151`，私网 IP `192.168.1.237`，生产 VPC `nongjiqiancha-prod-vpc` / `vpc-2zeax2zowza2398b9dzot`，生产交换机 `nongjiqiancha-prod-beijing-l` / `vsw-2zemsq82lj2kp8za90aky`，安全组 `sg-2ze4tilwxw1h5w77lwl1`，固定公网带宽 5 Mbps；2026-05-30 已删除空闲默认 VPC / 默认交换机和旧 SAE 自动交换机。已初始化部署 `server-go`，当前通过 `nongji-server-3000.service` / `nongji-server-3001.service` 双端口 slot + Nginx 反向代理运行，`AUTH_STRICT=true`、开发期订单接口关闭，DashScope 主 / 副模型 Key 已配置并显示 `bailian=ok`。此前曾创建标准版 SAE 应用 `nongjiqiancha`，AppId `366147d5-3760-4548-bd68-f38debbc5f23`，只是默认 demo 镜像，未部署真实后端，已于 2026-05-24 删除，删除后 `ListApplications` 返回空列表。域名 `nongjiqiancha.cn` 已购买，用户口头确认实名认证 / 模板审核已通过；阿里云 DNS 已创建 `api` / `@` / `www` A 记录指向 `39.106.1.151`，ECS 内解析和 HTTP healthz 已生效；2026-06-05 已通过 Let’s Encrypt / certbot 配置 `api.nongjiqiancha.cn` Nginx 443 HTTPS，并公网验证 HTTPS healthz 200；2026-06-06 已部署根域名官网并配置 `nongjiqiancha.cn` / `www.nongjiqiancha.cn` HTTPS；网站 ICP 已于 2026-06-05 通过，App 备案已于 2026-06-05 20:03 左右提交阿里云初审，网站公安联网备案已于 2026-06-07 提交，仍待 App 备案通过、网站公安备案号、App 公安备案和真机登录 / 主聊天 / 图片问诊回归。RDS MySQL 实例 `rm-2zes3vmj76p85n8g1` 已创建并运行，MySQL 8.0、基础版、1 核 2GB、50GB、北京可用区 L、交换机 `vsw-2zemsq82lj2kp8za90aky`、内网地址 `rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`；已创建库 `nongjiqiancha` 和账号 `nongji_app`，RDS 白名单当前为 `192.168.1.237`，迁移已跑通。Redis 开源版实例 `nongjiqiancha-prod-redis` / `r-2zet46zvmoo9wu3bic` 已购买并运行，256MB、Redis 7.0、标准高可用主备、同生产 VPC / 北京可用区 L；`server-go` 已新增可选 Redis 客户端和认证短期限流，主聊天流、额度、归档、摘要和订单仍不依赖 Redis。OSS 标准-本地冗余存储包（华北2）100GB 已购买并生效，资源包实例 `OSSBAG-cn-mqq4sqfvr001`；Bucket `nongjiqiancha-prod` 已创建并配置 `uploads/` 3 天、`support/` 30 天生命周期，代码已新增 OSS 上传后端，ECS 环境变量已收口并通过 `upload_storage=oss` 验证。SLS 服务本体已开通，农技千查专用 Project / Logstore 已接入 Go 服务 JSON 日志和 Nginx error log，TTL 7 天；2026-06-10 已创建 5 条最小 AlertHub 告警，覆盖 Go 5xx、Go 慢请求、Nginx upstream 错误、今日农情生成失败、模型 Key / DYPNS 配置错误，但外部通知 / 仪表盘仍待补。仓库已有 `docs/runbooks/deploy-ecs.md`、`docs/runbooks/deploy-sae.md`、`docs/runbooks/infra-readiness.md`、`docs/runbooks/redis.md`、`docs/runbooks/official-website.md` 和 `docs/runbooks/go-live-plan.md`；本机阿里云 CLI 已能通过 OpenAPI / Cloud Assistant 运维 ECS / RDS / Redis / SLS / OSS 资源包，SAE 应用列表当前为空
- 待定原因：模型 Key 归属、生产充值告警和轮换责任仍需固化；SLS 外部通知 / 仪表盘、默认 7 天 RDS 备份是否调整、数据库迁移是否长期改成独立发布步骤或加锁、App 备案审核通过 / 公安备案、App 生产 API 真机回归仍需继续落地；Android 正式登录 token 链已落首版账号 / session / 迁移骨架，融合认证 SchemeCode / 包名 / 签名、DYPNS 基础凭证、短信签名模板和 Android 融合认证 SDK 客户端链路已写入生产配置 / 代码主链，但 AccessKey 轮换、HTTPS 公网入口下的真机登录联调仍待闭环

## D5 记忆文档后续是否结构化

- 当前事实：后端只维护一份自然语言记忆文档，物理字段暂沿用 `session_ab.b_summary`，对外返回 `memory_document`；旧长期记忆列和旧重试标记只允许出现在迁移 SQL 中用于合并并删除遗留列。记忆文档固定由 `qwen-plus` 非流式、不联网、顶层 `enable_thinking=false` 生成，不再保留轻量模型候选或分层灰度环境变量。
- 当前四段：`短期承接`、`长期背景`、`用户画像`、`农业重点事件`。
- 当前倾向：先不做复杂 RAG、知识图谱、独立农事事件表、病例卡实时抽取或双模型全量复核；先用一份记忆文档覆盖更新，既接住短期连续性，也保留稳定长期背景和农业重点事件。
- 待定原因：等真实用户数据和误记忆案例出现后，再评估是否把四段拆成结构化字段；如果未来新增结构化记忆或独立农事事件表，账号管理“删除所有历史对话”必须同链路纳入清理。

## D6 时间 / 地点上下文后续增强

- 当前事实：`server-go/internal/app/chat.go` 每轮主对话已经注入当前上海时间（精确到秒）和用户地点；也就是说模型知道“当前是什么时间 / 用户大概在哪里”。Android 已接入可选定位权限，用户授权后每次发送问诊前尽量短窗口刷新系统定位，并只把系统 Geocoder 反查出的省 / 市 / 区县等地区文本作为 `X-User-Region` 传给后端；App 不上传经纬度，不保存轨迹。未授权、定位失败或未传 `X-User-Region` 时后端会用服务器拿到的公网 IP 走免费离线 `ip2region` xdb 粗定位，结果只是省 / 市级 `unreliable` 参考；库未配置、私网 IP、代理漂移或查询失败时仍兜底 `未知 / unreliable`
- 已落地部分：`SessionRound.created_at / region / region_source / region_reliability` 已接入后端快照和前端兼容解析，历史轮次进入模型上下文时会增加轻量前缀“历史轮次时间：...（Asia/Shanghai）”和“历史轮次地点：...；地点可信度：...”；前端暂不显示每条消息时间戳或地点条
- 仍待决策：是否要把更细的地块 / 基地 / 作物茬口 / 棚室编号等沉淀进 C 层“农业相关重点事件记忆”或未来结构化字段；天气 API 暂不接入，只有明确需要实时天气 / 温湿度 / 降雨时再评估成本。定位当前只做到地区文本，不做轨迹、地图或地块坐标管理

## D7 今日农情失败告警、重试和个性化怎么落

- 当前事实：代码已接入 `daily_agri_cards`、`GET /api/today-agri-card`、`GET /api/today-agri-cards`、内部 `POST /internal/jobs/today-agri-card/generate` 和后台 `POST /admin-api/v1/today-agri/generate`；ECS systemd timer 已作为生产主线落地，Android 只展示已 ready 的正好 3 条“今日农情”标题 + 摘要，缺失时静默不展示。生成模型固定 `qwen-plus`，联网入口为 DashScope `text-generation/generation + enable_search=true + search_strategy=turbo + enable_source=true + freshness=7 + prompt_intervene`；不保留其它模型候选、其它接口候选或环境变量切换入口。`agent / agent_max` 会带来更多检索和 token 成本，今日农情默认不使用；生成最多 2 次，第二次仍走 `turbo`；只做种植侧，养殖侧主要靠提示词排除；来源 URL 只做内部追溯和后台排查，不作为用户可点击入口
- 当前倾向：继续保持“云端定时生成 + 后台人工补跑兜底”的主线，不让用户打开 App 时触发生成风暴
- 仍待决策：失败是否做自动重试、告警先走 SLS 还是继续人工巡检、是否允许后台下架单条或重发当天卡片、未来是否做地区 / 作物个性化。当前首版只做全国 `CN` 卡片，不做按用户画像推送

## D8 是否后续新增独立农事事件表

- 当前事实：当前不单独实现复杂农事状态卡，也不新增 `agri_case_cards` 表；农业相关重点事件先由记忆文档第四段“农业重点事件”承接。纯图片输入已由后端补内部提示词，避免用户只发图时模型缺少文字意图
- 当前倾向：先按一份记忆文档方案跑一段时间，不做关键词 gate、不做每轮独立抽取、不做独立事件表。后续只有当农业重点事件段出现明显容量不足、旧事件污染、难以后台排查或真实用户强依赖多事件追踪时，再评估独立农事事件表
- 防污染原则：即使在记忆文档里记录农业重点事件，也必须写成“近期提到 / 曾讨论 / 用户反馈 / 仍需核对”等状态，不把未确认方向写成确定诊断；当前输入、当前图片和用户最新纠正永远优先

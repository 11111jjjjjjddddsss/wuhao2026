# ADR-0004 管理后台采用 Vite 前端 + Go 管理 API

日期：2026-06-07

## 背景

农技千查当前已经有 Android App、Go 后端、ECS、RDS、Redis、OSS、SLS、帮助与反馈内部接口、App 自动日志和最小内部审计。下一步需要统一管理后台，覆盖用户管理、会员、额度、礼品卡、订单、App 日志、帮助反馈、今日农情、检查更新、审计和产品洞察。

用户希望后台功能尽量完整。当前支付仍未接入正式渠道；礼品卡后端、后台账号、session、CSRF、角色权限和审计已经按第一版后台架构落地。

## 决策

第一版管理后台采用：

- 前端：独立 Vite 静态前端，建议目录为 `admin` 或 `site-admin`，不混入公开官网 `site`。
- 后端：复用 `server-go`，新增管理后台 JSON API，不另起 Node / Java / Python 后端。
- 部署：ECS + Nginx，优先独立子域名 `admin.nongjiqiancha.cn`，后台静态文件和后台 API 独立 location。
- 鉴权：服务端后台账号、session、CSRF、角色权限和登录失败限制，不把共享 secret 写入浏览器。
- 数据：浏览器只访问 Go 管理 API，不直连 MySQL、Redis、OSS、SLS 或阿里云 OpenAPI。
- 审计：所有后台登录、查询、回复、改版本、补权益、礼品卡、订单异常处理和今日农情操作必须落服务端审计。

## 原因

- Go 后端已经是业务唯一真相来源，复用它能减少一套服务、日志、密钥和部署面。
- Vite 足够承载后台 SPA，构建和部署简单。
- 当前阶段最需要“查、看、回复、审计”，不是重型运营系统。
- 后台若直接暴露数据库、SLS 或共享 secret，会扩大安全面，也不利于审计。

## 阶段边界

第一阶段只做：

- 后台账号 / session / 权限 / 审计。
- 总览、用户查询、帮助与反馈、App 日志、今日农情、检查更新。
- 会员、额度、订单、礼品卡先只读或占位。
- 产品洞察先做脱敏聚合报表，不铺完整聊天全文。

支付接入后再做：

- 正式订单回调、对账、退款和权益发放异常处理。
- 礼品卡批次、生成、发放、兑换、作废和导出。
- 人工补权益和高风险财务操作。

实施状态更新（2026-06-19）：

- 第一阶段已经不再只是“只读或占位”。礼品卡生成、兑换、作废、完整卡码加密查看、批次 / 尾号 / 账号追溯，会员额度只读追溯，帮助反馈队列，今日农情人工发布和检查更新配置都已落地。
- 真实支付、正式订单回调、退款、对账和自动权益发放仍未接入；这些继续按支付接入后的高风险财务能力处理。
- 当前运行状态以 [current-status.md](D:/wuhao/docs/project-state/current-status.md)、[open-risks.md](D:/wuhao/docs/project-state/open-risks.md) 和 [management-backend.md](D:/wuhao/docs/runbooks/management-backend.md) 为准，本 ADR 保留原始架构取舍和阶段意图。

## 安全要求

- 后台初始账号必须通过一次性初始化脚本或环境变量写入数据库 hash，不能写进仓库。
- 明文密码、模型 Key、AccessKey、数据库密码、内部 secret 不进入前端、文档或聊天复述。
- 高风险操作必须二次确认并写审计。
- 后台列表默认脱敏展示手机号、IP、卡号、图片和聊天来源。

## 后续影响

- 新增后台页面时优先更新 [management-backend.md](D:/wuhao/docs/runbooks/management-backend.md) 和 [admin-dashboard-design.md](D:/wuhao/docs/runbooks/admin-dashboard-design.md)。
- 如果未来引入独立后台服务，需要新增 ADR 说明为什么不再复用 Go 后端。

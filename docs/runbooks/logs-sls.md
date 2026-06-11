# 日志排查 Runbook

最后更新：2026-06-12

## 目的

记录 ECS / SLS 相关日志排查的当前有效入口。SAE 当前已删除，仅作为历史备选。

## 当前现状

- 阿里云 SLS 服务本体已开通，`GetSlsService` 返回 `Opened`
- 农技千查专用 SLS 已接入最小生产排障集：
  - Project：`nongjiqiancha-prod-1159547719787456`
  - Logstore：`server-go`，采集 `/var/log/nongjiqiancha/server.log`
  - Logstore：`nginx-error`，采集 `/var/log/nginx/error.log`
  - TTL：7 天
  - Shard：1
  - ECS 采集器：Logtail / ilogtail，机器组 `nongjiqiancha-prod-ecs`，当前连接 IP `192.168.1.237`
- 当前未购买 SLS 节省计划 / 资源包；先按低保留天数、小日志量过渡
- 北京区当前可见 3 个阿里云系统 / 产品托管 Project：
  - `default-cms-1159547719787456-cn-beijing`：CMS / 云监控托管
  - `proj-xtrace-fcd9c65ffd26d6c2a8e8f356745e42db-cn-beijing`：XTrace / APM 托管
  - `aliyun-product-data-1159547719787456-cn-beijing`：产品托管数据，当前包含 `sae_event`
- 这些项目不是农技千查业务日志，不应在未确认用途 / 费用 / 依赖前直接删除
- 2026-05-30 曾通过阿里云 CLI 复查北京区只有这 3 个系统 / 产品托管 Project；2026-06-06 已新增农技千查专用 Project，但仍不删除上述系统 / 产品托管 Project。`default-cms...` 和 `proj-xtrace...` 可能影响云监控 / XTrace / APM 视图或历史，`aliyun-product-data...` 当前包含旧产品事件 `sae_event`；若后续确认确实不需要 ARMS / APM 或旧 SAE 产品事件，先在对应产品控制台关闭 / 清理，再删除 SLS Project / Logstore
- `server-go` 当前日志同时写 ECS 本地 `systemd` journal 和 `/var/log/nongjiqiancha/server.log`；SLS 采集文件日志，Nginx error log 由 SLS 采集，Nginx access 仍先用本地查询脚本筛 `429/5xx`
- Go 后端已补统一请求级结构化日志：每个非降噪业务请求都会写 `request_id / method / path / status / duration_ms / response_bytes / masked_ip / auth_mode / user_id(如有) / user_agent(如有)`，不记录 query string、请求 body、Authorization、手机号、图片 URL 或模型 Key
- 响应头会回写 `X-Request-Id`；App 自动日志、Nginx 和 Go journal 后续可用该 ID 串联排障
- 健康检查 `/healthz` 和 `/uploads/` 静态图片成功请求默认降噪；但 4xx / 5xx 或慢请求仍会记录
- 慢请求阈值由 `ACCESS_LOG_SLOW_MS` 控制，默认 `3000` 毫秒；`0` 表示关闭慢请求标记
- 2026-06-10 已创建 5 条最小 SLS AlertHub 告警规则，覆盖 Go 5xx、Go 慢请求、Nginx upstream 错误、今日农情生成失败、模型 Key / DYPNS 配置错误。当前只进入 SLS AlertHub，不绑定短信、电话、机器人、邮件或自定义 action policy；通知送达、仪表盘和资源水位告警仍未闭环

## SLS 告警规则

告警脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\setup-sls-alerts.ps1
```

如果已经在 SLS 控制台创建好行动策略 / 仪表盘，可以把对应 ID 传给脚本，把 5 条规则统一绑定；不要把联系人手机号、机器人 webhook、邮箱或通知模板密钥写进仓库：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\setup-sls-alerts.ps1 -ActionPolicyId <sls-action-policy-id> -DashboardId <sls-dashboard-id>
```

只预演、不创建或更新：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\setup-sls-alerts.ps1 -DryRun
```

只读巡检当前告警闭环，不修改云上配置：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-sls-alert-readiness.ps1
```

若要把外部通知 / 仪表盘作为发布门槛，可加严格参数；当前生产会因为尚未绑定 action policy / dashboard 返回失败：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File D:\wuhao\scripts\check-sls-alert-readiness.ps1 -RequireExternalNotification -RequireDashboard
```

当前已创建的最小规则：

| 规则名 | Logstore | 查询 | 条件 | 重复提醒 |
| --- | --- | --- | --- | --- |
| `nongji-server-5xx` | `server-go` | `http_request_error \| select count(1) as cnt` | `cnt > 0` | 30 分钟 |
| `nongji-server-slow` | `server-go` | `http_request_slow \| select count(1) as cnt` | `cnt >= 5` | 60 分钟 |
| `nongji-nginx-upstream` | `nginx-error` | `upstream \| select count(1) as cnt` | `cnt > 0` | 30 分钟 |
| `nongji-daily-agri-failed` | `server-go` | `generate today agri card failed \| select count(1) as cnt` | `cnt > 0` | 60 分钟 |
| `nongji-model-auth-config` | `server-go` | `missing_key OR MODEL_BACKEND_NOT_CONFIGURED OR dypns \| select count(1) as cnt` | `cnt > 0` | 60 分钟 |

验证云上规则：

```powershell
aliyun sls list-alerts --region cn-beijing --project nongjiqiancha-prod-1159547719787456 --size 20
aliyun sls get-alert --region cn-beijing --project nongjiqiancha-prod-1159547719787456 --alert-name nongji-server-5xx
```

边界：

- 这些规则是“最小生产兜底”，不是完整告警中心
- 当前只投递到 AlertHub，后台不会自动弹窗，用户也不会收到系统通知
- 2026-06-12 只读巡检确认 5 条规则均存在、启用且进入 AlertHub，但 `actionPolicyId=空`、未关联 dashboard；这不是脚本失败，而是外部通知和仪表盘尚未闭环
- 后续仍需配置 action policy / 联系人 / 通知渠道、仪表盘、ECS / RDS / Redis / OSS 资源水位、DYPNS 认证用量和模型成本告警
- 不要把聊天正文、AI 回复全文、完整手机号、图片 URL、token、模型 Key 或数据库密码加入 SLS 查询、告警消息或通知模板

## 当前查询入口

优先用项目脚本通过 Cloud Assistant 拉取 ECS 本机脱敏日志摘要：

```powershell
.\scripts\query-ecs-logs.ps1 -Lines 240
```

该脚本会查看：

- Go 请求错误和慢请求：`http_request_error` / `http_request_slow`
- Go 请求尾部：`http_request`
- Go WARN / ERROR 尾部
- Nginx error log 尾部
- Nginx access log 里的 `429 / 5xx`

也可以直接查 SLS 最小采集集：

```powershell
.\scripts\query-sls-logs.ps1 -Minutes 30 -Line 50
```

SLS 重点查：

- `server-go`：`http_request_error`、`http_request_slow`、`server bootstrap failed`、`upstream`
- `nginx-error`：`limiting requests`、`connect() failed`、`upstream`、`SSL_do_handshake`

当前 SLS 不采集完整 Nginx access log；如果要看 Nginx `429 / 5xx` access 摘要，仍使用 `query-ecs-logs.ps1`。

脚本输出会对 Bearer、`sk-...`、AccessKey、签名参数、query string 和完整 IPv4 做基础脱敏；仍禁止把原始生产密钥、token 或完整请求正文复制进聊天 / 文档。

通过 Cloud Assistant 查看后端日志：

```powershell
aliyun ecs RunCommand --RegionId cn-beijing --Type RunShellScript --InstanceId.1 i-2ze5nrem0jrchln4f0eh --CommandContent "journalctl -u nongji-server-3000 -u nongji-server-3001 -n 120 --no-pager" --Timeout 120
```

服务器内常用日志命令：

```bash
journalctl -u nongji-server-3000 -u nongji-server-3001 -n 120 --no-pager
journalctl -u nongji-server-3000 -u nongji-server-3001 -f
tail -100 /var/log/nginx/access.log
tail -100 /var/log/nginx/error.log
systemctl status nongji-server-3000 --no-pager
systemctl status nongji-server-3001 --no-pager
systemctl status nginx --no-pager
```

## 常用关键词

- `server bootstrap failed`：启动阶段失败，多数是数据库连接、迁移、assets / migrations 路径或环境变量问题
- `http_request`：普通业务请求日志
- `http_request_slow`：超过 `ACCESS_LOG_SLOW_MS` 的慢请求，优先看 `path / duration_ms / request_id`
- `http_request_error`：Go handler 返回 5xx，优先看 `path / status / request_id`，再查同一 request id 附近的业务错误日志
- `MODEL_BACKEND_NOT_CONFIGURED` / `missing_key`：通常表示 DashScope 模型 Key 缺失、槽位为空或服务未读取到配置
- `unauthorized`：`AUTH_STRICT=true` 下缺少有效 bearer token
- `RATE_LIMITED`：后端用户级限流
- Nginx `429`：Nginx IP 级限流命中
- Nginx `502`：当前 active upstream 对应的后端 slot 未启动、重启中或本机端口不可达

## 排障顺序

1. 先跑 `.\scripts\query-ecs-logs.ps1 -Lines 240`，看是否有 `http_request_error`、`http_request_slow`、Nginx `502/429`。
2. 如果有 `request_id`，围绕这个 ID 查同一时间段 Go journal 的业务日志。
3. 如果只有 Nginx `502`，先跑 [check-ecs-readiness.ps1](D:/wuhao/scripts/check-ecs-readiness.ps1)，确认 active upstream slot、Nginx 和 `/healthz`。
4. 如果是 App 端失败但 Go / Nginx 没异常，再查 `GET /internal/app/logs` 或后续管理后台的 App 自动日志。
5. 如果是今日农情、记忆文档摘要或模型链路，继续按对应 runbook 查模型 Key 池、联网搜索和生成任务状态。

## SLS 接入建议

- 首版已经创建农技千查专用 Project / Logstore，设置 7 天 TTL，先采集 Go 结构化日志和 Nginx error log
- 暂不把所有 Nginx access log、聊天正文、AI 回复、图片 URL、手机号、token、模型 Key、数据库密码采进 SLS
- 如果后续 App 自动日志要进 SLS，优先从 `client_app_logs` 做管理后台查询或脱敏聚合，不直接上传原始聊天内容
- 如果日志量上涨，再评估资源包、告警规则、仪表盘和更细的采集过滤
- 不删除阿里云系统 / 产品托管 Project，除非先确认没有依赖且不会影响云监控 / APM / 产品事件

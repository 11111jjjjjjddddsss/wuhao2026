# 日志排查 Runbook

最后更新：2026-05-30

## 目的

记录 ECS / SLS 相关日志排查的当前有效入口。SAE 当前已删除，仅作为历史备选。

## 当前现状

- 阿里云 SLS 服务本体已开通，`GetSlsService` 返回 `Opened`
- 当前未购买 SLS 节省计划 / 资源包，也未创建农技千查专用 Project / Logstore
- 北京区当前可见 3 个阿里云系统 / 产品托管 Project：
  - `default-cms-1159547719787456-cn-beijing`：CMS / 云监控托管
  - `proj-xtrace-fcd9c65ffd26d6c2a8e8f356745e42db-cn-beijing`：XTrace / APM 托管
  - `aliyun-product-data-1159547719787456-cn-beijing`：产品托管数据，当前包含 `sae_event`
- 这些项目不是农技千查业务日志，不应在未确认用途 / 费用 / 依赖前直接删除
- 2026-05-30 已通过阿里云 CLI 复查：北京区仍只有这 3 个系统 / 产品托管 Project，没有农技千查专用 Project。`default-cms...` 和 `proj-xtrace...` 可能影响云监控 / XTrace / APM 视图或历史，`aliyun-product-data...` 当前包含旧产品事件 `sae_event`；本轮不删除这些 SLS 资源。若后续确认确实不需要 ARMS / APM 或旧 SAE 产品事件，先在对应产品控制台关闭 / 清理，再删除 SLS Project / Logstore
- `server-go` 当前日志先走 ECS 本地 `systemd` journal；Nginx 日志在 `/var/log/nginx/access.log` 和 `/var/log/nginx/error.log`

## 当前查询入口

通过 Cloud Assistant 查看后端日志：

```powershell
aliyun ecs RunCommand --RegionId cn-beijing --Type RunShellScript --InstanceId.1 i-2ze5nrem0jrchln4f0eh --CommandContent "journalctl -u nongji-server -n 120 --no-pager" --Timeout 120
```

服务器内常用日志命令：

```bash
journalctl -u nongji-server -n 120 --no-pager
journalctl -u nongji-server -f
tail -100 /var/log/nginx/access.log
tail -100 /var/log/nginx/error.log
systemctl status nongji-server --no-pager
systemctl status nginx --no-pager
```

## 常用关键词

- `server bootstrap failed`：启动阶段失败，多数是数据库连接、迁移、assets / migrations 路径或环境变量问题
- `MODEL_BACKEND_NOT_CONFIGURED` / `missing_key`：通常表示 DashScope 模型 Key 缺失、槽位为空或服务未读取到配置
- `unauthorized`：`AUTH_STRICT=true` 下缺少有效 bearer token
- `RATE_LIMITED`：后端用户级限流
- Nginx `429`：Nginx IP 级限流命中
- Nginx `502`：后端进程未启动、重启中或本机 3000 端口不可达

## SLS 接入建议

- 首版可以先用本地 journal / Nginx 日志过渡，不急着购买 SLS 节省计划
- 若接 SLS，只创建农技千查专用 Project / Logstore，设置低保留天数，先采集关键后端错误日志、Nginx 4xx / 5xx 和今日农情任务结果
- 不删除阿里云系统 / 产品托管 Project，除非先确认没有依赖且不会影响云监控 / APM / 产品事件

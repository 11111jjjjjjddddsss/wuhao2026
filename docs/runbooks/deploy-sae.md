# SAE 发版 Runbook

最后更新：2026-05-24

## 目的

记录农技千查后端部署到阿里云 SAE 的当前有效入口。

## 当前现状

- 主规则已明确 SAE 优先走脚本、CLI、OpenAPI 等可审计入口
- 当前已在阿里云 `华北2（北京）/ cn-beijing` 创建标准版 SAE 应用 `nongjiqiancha`，AppId `366147d5-3760-4548-bd68-f38debbc5f23`，规格 `0.5 核 / 1GB / 单实例`，自动弹性未开启
- 当前 VPC 为 `vpc-2zeax2zowza2398b9dzot`；SAE 默认交换机为北京可用区 F `vsw-2ze3elcd2iad6n1madi5g`；RDS MySQL 使用同 VPC 下北京可用区 L 交换机 `nongjiqiancha-rds-beijing-l` / `vsw-2zemsq82lj2kp8za90aky` / `192.168.1.0/24`
- 当前 RDS MySQL 实例 `rm-2zes3vmj76p85n8g1` 已创建并运行，MySQL 8.0、基础版、1 核 2GB、50GB、内网地址 `rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`；当前自动备份保留 7 天，默认每周二 / 四 / 六 17:00-18:00 北京时间左右执行；白名单仍是默认 `127.0.0.1`，数据库账号 / 库名 / SAE 环境变量尚未配置
- 当前 SAE 仍运行默认 demo 镜像：`cn-beijing.online-vpc.cr.sae.aliyuncs.com/sae-serverless-public/sae-demo:microservice-java-provider-v1.0`，尚未部署 `server-go` 真实后端镜像
- 本机已安装阿里云 CLI 到 `C:/Users/Administrator/AppData/Local/Programs/AliyunCLI/aliyun.exe`，默认 Region 为 `cn-beijing`。查询 SAE 时当前需要显式 endpoint：`--endpoint sae.cn-beijing.aliyuncs.com`
- 本仓库当前尚未固化正式发版脚本或标准化发布命令

## 后续补充要求

- 一旦形成稳定发版流程，应在本文件补齐：
  - 发版前检查
  - 实际执行命令或脚本入口
  - 成功判定方式
  - 回滚触发条件
  - 相关环境变量来源

## 当前可用检查命令

```powershell
& "$env:LOCALAPPDATA\Programs\AliyunCLI\aliyun.exe" sae ListApplications --RegionId cn-beijing --endpoint sae.cn-beijing.aliyuncs.com
```

该命令只能说明 CLI 能访问 SAE 和当前应用壳子存在，不代表 `server-go` 已部署成功。

## 当前必须确认的环境变量

- `BASE_PUBLIC_URL` 或 `UPLOAD_BASE_URL`：后端公开 `https` 基地址；`/upload` 返回图片 URL 和 `/api/chat/stream` 图片 URL 校验都只认这里，不再从请求转发头临时推导可信域名
- `APP_ENV=production`：生产环境建议显式配置，防止开发期订单接口被误开
- `APP_SECRET` + `AUTH_STRICT=true`：生产如果要关闭裸 `X-User-Id` 兜底，必须同时配置签名 token 密钥；在正式账号体系上线前，是否开启需要和 Android 登录 / token 发放链一起确认
- Android `SESSION_API_TOKEN`：只能作为本地 / 内测固定用户调试桥接；正式 release APK 不应打入共享静态 token。严格模式下后端会优先使用 bearer token 里的 `userID`，共享静态 token 会把不同设备合并成同一个 token 用户
- `ALLOW_DEV_ORDER_ENDPOINTS`：生产保持未设置或 false；即使误设为 true，只要 `APP_ENV / ENV / GO_ENV` 是 `prod / production`，后端也会强制关闭开发期订单接口

## 暂行原则

- 在正式脚本固化前，任何发版动作都应优先沉淀成脚本或明确命令，避免长期依赖控制台手点

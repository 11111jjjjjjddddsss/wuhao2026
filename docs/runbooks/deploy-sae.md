# SAE 发版 Runbook

最后更新：2026-05-13

## 目的

记录农技千问后端部署到阿里云 SAE 的当前有效入口。

## 当前现状

- 主规则已明确 SAE 优先走脚本、CLI、OpenAPI 等可审计入口
- 本仓库当前尚未固化正式发版脚本或标准化发布命令

## 后续补充要求

- 一旦形成稳定发版流程，应在本文件补齐：
  - 发版前检查
  - 实际执行命令或脚本入口
  - 成功判定方式
  - 回滚触发条件
  - 相关环境变量来源

## 当前必须确认的环境变量

- `BASE_PUBLIC_URL` 或 `UPLOAD_BASE_URL`：后端公开 `https` 基地址；`/upload` 返回图片 URL 和 `/api/chat/stream` 图片 URL 校验都只认这里，不再从请求转发头临时推导可信域名
- `APP_ENV=production`：生产环境建议显式配置，防止开发期订单接口被误开
- `APP_SECRET` + `AUTH_STRICT=true`：生产如果要关闭裸 `X-User-Id` 兜底，必须同时配置签名 token 密钥；在正式账号体系上线前，是否开启需要和 Android 登录 / token 发放链一起确认
- `ALLOW_DEV_ORDER_ENDPOINTS`：生产保持未设置或 false；即使误设为 true，只要 `APP_ENV / ENV / GO_ENV` 是 `prod / production`，后端也会强制关闭开发期订单接口

## 暂行原则

- 在正式脚本固化前，任何发版动作都应优先沉淀成脚本或明确命令，避免长期依赖控制台手点

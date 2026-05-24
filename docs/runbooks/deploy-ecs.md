# ECS 发版 Runbook

最后更新：2026-05-24

## 目的

记录农技千查后端部署到阿里云 ECS 的当前有效入口。当前首版后端路线优先按 ECS 传统部署推进，替代此前的 SAE + ACR 镜像托管路线。

## 当前现状

- 真实 ECS 尚未购买，不能伪造实例 ID、公网 IP、系统盘、登录用户、部署目录或安全组
- 旧 SAE demo 应用已删除，当前没有 SAE 应用承载后端或对外流量
- RDS MySQL 实例 `rm-2zes3vmj76p85n8g1` 已创建并运行，内网地址 `rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`，当前白名单仍是默认 `127.0.0.1`
- 域名 `nongjiqiancha.cn` 已购买，但 DNS、ICP备案、HTTPS 证书和正式 API 入口尚未完成
- OSS / SLS / Redis 尚未接入

## ECS 买完后必须回填

- ECS 实例 ID、地域、可用区、规格、系统镜像、VPC / 交换机、安全组
- 部署用户、部署目录、二进制路径、配置文件 / 环境变量来源
- `systemd` 服务名、启动 / 停止 / 重启 / 查看日志命令
- Nginx / Caddy 反向代理配置、HTTPS 证书来源、健康检查 URL
- RDS 白名单 / 安全组放通方式
- OSS 图片存储是否启用；若未启用，必须明确只跑单台 ECS
- 发布、回滚、备份和排障命令

## 暂行部署原则

- 首版先单台 ECS 跑通 `server-go`
- 数据放 RDS，图片优先规划进 OSS；若暂不接 OSS，本机 `/uploads` 只允许单台 ECS 阶段使用
- 后端环境变量只放在服务器或云端密钥配置中，不写入仓库
- 发布前先本地 `cd server-go && go build ./...`
- 服务器上用 `systemd` 守护 `server-go`，不要长期依赖手动前台运行
- 对外入口由 Nginx / Caddy 反向代理到后端本地端口

## 当前禁止

- 不把数据库密码、模型 Key、短信 Key 或 AccessKey 写进本文
- 不把旧 SAE AppId 当成可部署目标
- 不在未接 OSS 时扩到两台 ECS 或多后端实例
- 不用裸公网端口长期直接暴露后端，正式入口必须走 HTTPS 反向代理

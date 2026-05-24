# ECS 发版 Runbook

最后更新：2026-05-24

## 目的

记录农技千查后端部署到阿里云 ECS 的当前有效入口。当前首版后端路线按 ECS 传统部署推进，替代此前的 SAE + ACR 镜像托管路线。

## 当前现状

- ECS 已购买并运行：实例 `i-2ze5nrem0jrchln4f0eh`，名称 / 主机名 `iZ2ze5nrem0jrchln4f0ehZ`，地域 `cn-beijing`，可用区 `cn-beijing-l`，规格 `ecs.u1-c1m2.large`（2 vCPU / 4 GiB），Ubuntu 22.04 64 位，公网 IP `39.106.1.151`，私网 IP `192.168.1.237`，VPC `vpc-2zeax2zowza2398b9dzot`，交换机 `vsw-2zemsq82lj2kp8za90aky`，安全组 `sg-2ze4tilwxw1h5w77lwl1`，固定公网带宽 5 Mbps，到期时间 2027-05-24
- 当前 ECS 只是空 Ubuntu 主机，尚未初始化系统用户、部署目录、`systemd`、反向代理、HTTPS 或 `server-go`
- 当前安全组仍需收口：Ubuntu 不需要公网 3389；后续应关闭 3389，按需保留 / 限制 22，并开放 80 / 443 给正式 HTTP / HTTPS 入口
- Cloud Assistant 在线，后续初始化可优先用阿里云 CLI / 云助手执行可审计命令，不必依赖控制台手点或在聊天里暴露服务器密码
- 旧 SAE demo 应用已删除，当前没有 SAE 应用承载后端或对外流量
- RDS MySQL 实例 `rm-2zes3vmj76p85n8g1` 已创建并运行，内网地址 `rm-2zes3vmj76p85n8g1.mysql.rds.aliyuncs.com:3306`，当前白名单仍是默认 `127.0.0.1`
- 域名 `nongjiqiancha.cn` 已购买，用户口头确认实名认证 / 模板审核已通过；DNS、ICP备案、HTTPS 证书和正式 API 入口尚未完成
- OSS 标准-本地冗余存储包（华北2）100GB 已购买并生效，资源包实例 `OSSBAG-cn-mqq4sqfvr001`；当前还没有 Bucket，后端图片上传链仍未接 OSS
- SLS / Redis 尚未购买或接入

## 部署前必须回填

- 部署用户、部署目录、二进制路径、配置文件 / 环境变量来源
- `systemd` 服务名、启动 / 停止 / 重启 / 查看日志命令
- Nginx / Caddy 反向代理配置、HTTPS 证书来源、健康检查 URL
- RDS 白名单 / 安全组放通方式
- OSS Bucket、图片访问策略、生命周期和后端上传接入方式；若代码暂不接 OSS，必须明确只跑单台 ECS
- 发布、回滚、备份和排障命令

## 暂行部署原则

- 首版先单台 ECS 跑通 `server-go`
- 数据放 RDS，图片优先规划进 OSS；若暂不接 OSS，本机 `/uploads` 只允许单台 ECS 阶段使用
- 后端环境变量只放在服务器或云端密钥配置中，不写入仓库
- 发布前先本地 `cd server-go && go build ./...`
- 服务器上用 `systemd` 守护 `server-go`，不要长期依赖手动前台运行
- 对外入口由 Nginx / Caddy 反向代理到后端本地端口
- RDS 白名单优先只放通 ECS 私网 IP `192.168.1.237` 或等价的最小内网来源；不要把数据库公网暴露成大范围来源
- OSS Bucket 建议 `cn-beijing`、标准存储、本地冗余、私有读写、阻止公共访问，后端通过受控上传 / 读取链路使用；旧问诊图片生命周期先按短周期清理策略设计

## 当前禁止

- 不把数据库密码、模型 Key、短信 Key 或 AccessKey 写进本文
- 不把旧 SAE AppId 当成可部署目标
- 不在未接 OSS 时扩到两台 ECS 或多后端实例
- 不用裸公网端口长期直接暴露后端，正式入口必须走 HTTPS 反向代理

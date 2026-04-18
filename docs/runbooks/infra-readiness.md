# 上线前基础设施准备清单

最后更新：2026-04-19

## 目的

在正式购买云资源前，把“农技千问”上线所需的基础设施、依赖顺序、关键配置和补文档动作先固定下来，避免后面边买边猜。

## 当前现状

- Android 客户端与 `server-go` 后端代码主线已存在
- 仓库内已有 SAE / 日志 / 回滚 / 数据库只读 runbook 骨架
- 正式云资源当前仍未采购；尚未形成真实环境名、账号、Region、实例规格、数据库实例或日志项目

## 最小上线资源清单

### 必需

- 阿里云 SAE：部署 `server-go`
- PolarDB（MySQL 兼容）或等效托管 MySQL：主业务数据
- 域名与 HTTPS 证书：对外 API 入口
- 基础密钥与环境变量托管：模型 Key、数据库连接、JWT / Session 密钥等

### 高优先级可选

- OSS：图片上传与持久化
- SLS：后端日志检索与告警
- Redis：验证码、缓存、临时会话或限流

## 建议采购顺序

1. 先确定 Region、账号归属、命名规范
2. 再买数据库和 SAE，先让后端有一条最小可运行链
3. 然后补域名、HTTPS、OSS、SLS
4. 最后再看 Redis 是否要在首版一起上

## 采购前必须拍板的问题

- 生产环境是否直接上 SAE，还是先用测试环境 / 预发环境验证
- 数据库首版是否直接买 PolarDB，还是先用更轻量的托管 MySQL 过渡
- 图片是否首版就落 OSS；如果不是，前端图片能力要不要先受限
- 日志是否首版就接 SLS；如果不是，后端最小日志保留方案是什么
- 是否一开始就拆测试 / 生产两套环境，还是先单环境跑通

## 资源买完后必须回填仓库的地方

- `docs/runbooks/deploy-sae.md`：真实发版入口、命令、成功判定
- `docs/runbooks/rollback.md`：真实回滚入口
- `docs/runbooks/logs-sls.md`：真实日志项目 / 查询入口
- `docs/runbooks/db-readonly.md`：真实数据库只读连接方式
- `docs/project-state/current-status.md`：环境现状
- `docs/project-state/open-risks.md`：关闭“未采购正式云资源”相关风险
- `docs/project-state/pending-decisions.md`：移除已拍板项

## 暂行原则

- 在正式资源落地前，不把任何假定的实例名、域名、密钥来源写成既成事实
- 真正采购完成前，runbook 只记录“要拍板什么”和“买完后补哪里”，不伪造部署命令
- 一旦出现第一套真实环境，必须同次把对应 runbook 补成可执行入口

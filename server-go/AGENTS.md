# Go 后端子树执行补充

本文件只在 `server-go` 目录树内生效，用于补充 Go 后端的局部执行口径；主规则仍以根 [AGENTS.md](D:/wuhao/AGENTS.md) 为准。

## 开始前先读

1. 根 [AGENTS.md](D:/wuhao/AGENTS.md)
2. [docs/project-state/current-status.md](D:/wuhao/docs/project-state/current-status.md)
3. [docs/project-state/open-risks.md](D:/wuhao/docs/project-state/open-risks.md)
4. [docs/project-state/recent-changes.md](D:/wuhao/docs/project-state/recent-changes.md)
5. 本次任务相关的 ADR、runbook、接口或迁移文件

## 当前后端真相

- 后端唯一目录是 `server-go`
- 后端是业务唯一真相来源：鉴权、会员、调用次数、上下文组装、模型调用、成本统计都以后端为准
- 提示词真源在 `server-go/assets`
- 图片上传、会员、上下文、摘要、恢复等能力默认都以 `server-go` 当前代码为准

## 执行要求

- 先排查是否已有旧链路、旧脚本、旧口径残留，避免新老逻辑并存
- 改动接口、运维入口、部署方式、日志查询、数据库口径时，除代码外还要同步更新 `docs/project-state`、`docs/adr`、`docs/runbooks`
- Go 后端改动完成后执行：`cd server-go && go build ./...`

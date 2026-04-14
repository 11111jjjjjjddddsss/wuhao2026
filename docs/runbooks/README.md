# 运维 Runbook 入口

本目录用于沉淀“可审计、可交接、可复用”的运维入口，目标是让 Codex 和人工在换窗口、换人、换时间后都能快速找到正确入口。

当前 runbook：

- [deploy-sae.md](D:/wuhao/docs/runbooks/deploy-sae.md)：SAE 发版入口
- [rollback.md](D:/wuhao/docs/runbooks/rollback.md)：回滚入口
- [logs-sls.md](D:/wuhao/docs/runbooks/logs-sls.md)：日志排查入口
- [db-readonly.md](D:/wuhao/docs/runbooks/db-readonly.md)：数据库只读排查入口

维护规则：

- 只写当前有效入口，不堆过期截图和一次性口令
- 若实际操作步骤变化，必须同次更新对应 runbook
- 涉及密钥时只记录来源和注入方式，不把密钥写进仓库

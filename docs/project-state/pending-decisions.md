# 待决策事项

最后更新：2026-04-28

## D1 运维入口优先固化到什么形式

- 当前选项：仓库脚本、阿里云 CLI / OpenAPI、纯 runbook
- 现状：主规则已明确“脚本 / CLI / OpenAPI 优先”，但具体落地入口还未统一
- 待定原因：需要结合你后续实际发版、回滚、查日志、查库频率再决定先补哪一条

## D2 是否引入 GitHub 协作模板层

- 当前选项：保持仓库内文档为主，或继续补 Issue 模板 / PR 模板 / CODEOWNERS / Projects
- 现状：当前先落了仓库内长期记忆骨架，GitHub 协作模板还没加
- 待定原因：等后续多人协作、运维频率、需求管理复杂度上来后再决定

## D3 项目记忆是否继续细化到“按变更类型校验特定章节”

- 当前选项：维持当前“至少更新一份 memory 文件”的校验，或继续细化为“改 UI 主链必须同步更新 current-status/recent-changes，改风险口径必须同步更新 open-risks”等更严格规则
- 现状：`scripts/check_project_memory.py` 和 CI 已接入，程序化校验不再是空白；当前待定的是要不要继续收紧粒度
- 待定原因：更严格的校验能减少文档漂移，但也会提高提交门槛，需要结合后续实际误报率和维护成本再决定

## D4 正式云资源首版怎么落

- 当前选项：直接采购 SAE + PolarDB + 域名/HTTPS 跑最小生产链，或先上一套测试/预发环境再买正式规格
- 现状：仓库已有 `docs/runbooks/deploy-sae.md` 等运维骨架，也新补了 `docs/runbooks/infra-readiness.md` 作为采购前检查单；但真实云资源、Region、环境命名和实例规格都还没定
- 待定原因：你现在还没买服务器，正式环境资源一旦落地，就会影响后续部署、日志、数据库、域名、环境变量和 runbook 的真实入口

## D5 C+ 长期记忆怎么落地

- 当前倾向：不先做复杂 RAG、知识图谱、病例卡实时抽取或双模型全量复核；先把 C 层升级成 `C+ = 长期摘要 + 用户农业画像 + 用户农业档案`
- 当前理解：
  - 长期摘要：继续承担现有 C 层“跨多轮的稳定背景和结论”职责
  - 用户农业画像：沉淀用户常见地区、主种作物、种植类型、偏好的管理方式、风险承受能力、常问问题类型等
  - 用户农业档案：沉淀相对稳定的农业生产信息，例如作物结构、棚室/露地/果园/大田类型、常见地块或基地特征、历史高频问题；对流转地和换作物场景要避免当成永久病历，只作为“可能相关背景”
- 模型倾向：后续评估把 C 层 / C+ 提取从当前摘要模型切到 `Qwen3.5-Flash`，理由是成本仍可控，但指令遵循和结构化抽取能力可能比普通 Flash 更稳
- 当前代码现状：`server-go` 仍只有 `session_ab.c_summary` 这个 C 层文本字段，尚未实现用户农业画像 / 农业档案字段，也没有 C+ 专用 prompt 或 schema
- 待定原因：需要先决定 C+ 的字段边界、更新频率、是否仍与 `c_summary` 同表存储，以及是否先补全量原始问诊归档；否则直接改 prompt 容易把画像、档案和摘要揉成一团，后续不好检索和复盘

## D6 是否先保存全量原始问诊记录

- 当前倾向：先做。原因是当前 A/B/C 只服务上下文，不等于长期资产；没有原始问诊日志，后续再谈批量抽取、用户农业画像、农业档案、相似案例或质检复盘都会缺材料
- 当前代码现状：`session_ab.a_json` 只保留 A 层滑窗，Free / Plus 6 轮、Pro 9 轮；`session_round_ledger` 只存 `user_id + client_msg_id + created_at` 做幂等；`quota_ledger` 只存扣费流水；没有 append-only 的长期原始对话表
- 最小落点建议：新增 `session_round_archive` 或同名长期归档表，先只保存成功完成轮次的 `user_id / client_msg_id / user_text / user_images_json / assistant_text / source / created_at`，唯一键 `(user_id, client_msg_id)`；写入点优先放在 `Store.AppendSessionRoundComplete(...)`，因为 `/api/chat/stream` 和 `/api/session/round_complete` 都汇到这里
- 暂不做：不在第一刀里做大模型实时抽取、不改变 A/B/C prompt、不把归档内容每轮喂回模型、不加向量库

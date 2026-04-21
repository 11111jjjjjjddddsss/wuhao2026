# 待决策事项

最后更新：2026-04-21

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

## D5 Android 聊天 UI 下一轮只围绕哪一条继续会诊

- 当前选项：明天仅围绕“streaming 下一行提前冒头 / 一闪一消失”发 Claude 自包含短稿；或先冻结当前基线继续观察，不重新会诊整条滚动链
- 现状：滚动主链、发送微抖、首屏贴底和 finalize 归位按当前真机口径已收口；当前冻结基线保留 `ff4480f` 的 strict follow gate，但 streaming 新行主修法已经切到 `ChatScreen.kt` 的 `onAdvance` reveal-layer wrap guard。旧 `rememberGatedStreamingRenderedLines(...)`、preview lock、draw-phase clip，以及 `StreamingRevealMode.Conservative / strictLineReveal / lineRevealLocked / streamingLineAdvanceTick` 这条 reveal 空转链都已删除
- 待定原因：当前唯一开放问题已经缩到 `onAdvance` 提交口与 active block pre-measure 的交界；如果下一窗口不写死“只看 wrap guard / pre-measure / follow refine”，很容易把已收口的滚动链问题重新一起翻回来

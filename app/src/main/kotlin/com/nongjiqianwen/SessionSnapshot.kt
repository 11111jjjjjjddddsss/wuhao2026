package com.nongjiqianwen

/**
 * 聊天记录两条线分离：客户端与后端的会话快照契约。
 * A/B 真相在后端 DB；UI 裁剪（如 RENDER_WINDOW、hardTrimMessages）仅展示层，不得用于回写或推导 A/B。
 */

/** GET /api/session/snapshot 响应：B 摘要 + A 全量（供 B 提取）+ 最近 24 轮（仅 UI 展示） */
data class SessionSnapshot(
    val b_summary: String,
    val a_rounds_full: List<ARound>,
    val a_rounds_for_ui: List<ARound>
) {
    /** 兼容旧接口只返回 a_rounds 时：视为 for_ui，full 用 for_ui */
    constructor(b_summary: String, a_rounds: List<ARound>) : this(b_summary, a_rounds, a_rounds)
}

/** 单轮对话 (user, assistant) */
data class ARound(
    val user: String,
    val assistant: String
)

/** POST /api/session/append-a 请求体：仅 onComplete 后追加一轮 A */
data class AppendABody(
    val user_id: String,
    val session_id: String,
    val user_message: String,
    val assistant_message: String
)

/** POST /api/session/update-b 请求体：B 覆盖写成功后才允许后端清 A */
data class UpdateBBody(
    val user_id: String,
    val session_id: String,
    val b_summary: String
)

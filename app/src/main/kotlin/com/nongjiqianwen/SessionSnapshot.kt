package com.nongjiqianwen

/**
 * 聊天记录两条线分离：客户端与后端的会话快照契约。
 * A 层滑窗和记忆文档真相在后端 DB；UI 裁剪（如 RENDER_WINDOW、hardTrimMessages）仅展示层，不得用于回写或推导后端上下文。
 */

/** GET /api/session/snapshot 响应：记忆文档 + A 全量 + 最近 30 轮（仅 UI 展示） */
data class SessionSnapshot(
    val memory_document: String,
    val b_summary: String,
    val a_rounds_full: List<ARound>,
    val a_rounds_for_ui: List<ARound>,
    val round_total: Int = 0,
    val session_generation: Int = 0
) {
    /** 兼容旧接口只返回 a_rounds 时：视为 for_ui，full 用 for_ui */
    constructor(memoryDocument: String, a_rounds: List<ARound>) : this(memoryDocument, memoryDocument, a_rounds, a_rounds)
    constructor(memoryDocument: String, a_rounds_full: List<ARound>, a_rounds_for_ui: List<ARound>, session_generation: Int = 0) :
        this(
            memoryDocument,
            memoryDocument,
            a_rounds_full,
            a_rounds_for_ui,
            a_rounds_full.size.coerceAtLeast(a_rounds_for_ui.size),
            session_generation
        )
}

/** 单轮对话 (user, assistant) */
data class ARound(
    val client_msg_id: String? = null,
    val user: String,
    val user_images: List<String> = emptyList(),
    val assistant: String,
    val created_at: Long = 0L,
    val region: String? = null,
    val region_source: String? = null,
    val region_reliability: String? = null
)

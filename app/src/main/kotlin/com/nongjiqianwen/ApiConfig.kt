package com.nongjiqianwen

import java.util.UUID

/**
 * 客户端请求标识与未来服务端常驻 API 接口占位。
 * 当前仍直连 DashScope；后期切 CNB 时只改此处 baseUrl + 鉴权，不动 App 主链路。
 */
object ApiConfig {
    /** 当前请求 ID（每次 getReply 生成）；userId/sessionId 由 IdManager 提供 */
    fun nextRequestId(): String = "req_${UUID.randomUUID()}"

    /** 服务端常驻 API 接口形态（占位，当前未使用） */
    const val PATH_CHAT = "/chat"
    const val PATH_CHAT_STREAM = "/api/chat/stream"
    const val PATH_USAGE = "/usage"
    const val PATH_AUTH = "/auth"
}

/**
 * AB 层数据结构占位：不做 B 层摘要/锚点真实逻辑，仅留形状供后期接入。
 * - a_messages: 最近 N 轮对话（供服务端上下文）
 * - b_summary: B 层摘要字符串（占位）
 * - meta: 作物/地区/时间等（占位）
 */
data class ABContextPlaceholder(
    val aMessages: List<Any>,
    val bSummary: String,
    val meta: Map<String, String>
)

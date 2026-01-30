package com.nongjiqianwen

import java.util.UUID

/**
 * 客户端请求标识与未来服务端常驻 API 接口占位。
 * 当前仍直连 DashScope；后期切 CNB 时只改此处 baseUrl + 鉴权，不动 App 主链路。
 */
object ApiConfig {
    /** 当前请求 ID（每次 getReply 生成） */
    fun nextRequestId(): String = "req_${UUID.randomUUID()}"

    /** 用户 ID（占位，后期会员/审计用；先填 UUID） */
    var userId: String = UUID.randomUUID().toString()
        private set

    /** 会话 ID（占位，后期审计用；先填 UUID） */
    var sessionId: String = UUID.randomUUID().toString()
        private set

    /** 服务端常驻 API 接口形态（占位，当前未使用） */
    const val PATH_CHAT = "/chat"
    const val PATH_CHAT_STREAM = "/chat/stream"
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

package com.nongjiqianwen

import java.util.UUID

/**
 * 客户端请求标识与后端 API 路径。
 * 模型调用只允许经由后端，Android 不再直连模型服务。
 */
object ApiConfig {
    /** 当前请求 ID；userId 由 IdManager 提供。 */
    fun nextRequestId(): String = "req_${UUID.randomUUID()}"

    const val PATH_CHAT_STREAM = "/api/chat/stream"
}

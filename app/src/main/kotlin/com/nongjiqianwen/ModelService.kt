package com.nongjiqianwen

import java.util.UUID

/**
 * 模型服务接口：真流式 SSE，错误兜底，requestId/耗时日志。
 * 当前直连 DashScope；后期切服务端常驻 API 时只改此处与 QwenClient 的 URL/鉴权，不动 App 主链路。
 */
object ModelService {

    /**
     * 获取模型回复（真流式 SSE）
     * @param onInterrupted 流被取消或读超时时调用（切后台/Watchdog），用于 UI 标记“已中断”
     */
    fun getReply(
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (() -> Unit)? = null
    ) {
        val userId = IdManager.getInstallId()
        val sessionId = IdManager.getSessionId()
        val requestId = ApiConfig.nextRequestId()
        QwenClient.callApi(userId, sessionId, requestId, userMessage, imageUrlList, onChunk, onComplete, onInterrupted)
    }
}


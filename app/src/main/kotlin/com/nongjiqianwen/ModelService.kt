package com.nongjiqianwen

import java.util.UUID

/**
 * 模型服务接口：真流式 SSE，错误兜底，requestId/streamId/耗时日志。
 * 取消策略：仅在新请求或用户点停止时 cancel 旧请求；切后台不 cancel。
 */
object ModelService {

    /**
     * 获取模型回复（真流式 SSE）
     * @param onInterrupted 流被取消/读超时/错误时调用，reason=canceled|interrupted|error，仅用于 UI badge，不写正文
     */
    fun getReply(
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        streamId: String,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (reason: String) -> Unit
    ) {
        QwenClient.cancelCurrentRequest()
        val userId = IdManager.getInstallId()
        val sessionId = IdManager.getSessionId()
        val requestId = ApiConfig.nextRequestId()
        QwenClient.callApi(userId, sessionId, requestId, streamId, userMessage, imageUrlList, onChunk, onComplete, onInterrupted)
    }
}


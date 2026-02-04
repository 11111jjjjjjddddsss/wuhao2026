package com.nongjiqianwen

import java.util.UUID

/**
 * 模型服务接口：非流式一次返回；错误兜底；stop/cancel 占位保留。
 */
object ModelService {

    /**
     * 获取模型回复（非流式，一次 onChunk(完整文本) + onComplete）
     * @param chatModel 传 "plus" 为专家模式（qwen3-vl-plus），否则 qwen3-vl-flash
     * @param toolInfo 本轮联网搜索结果，以「极低参考性·工具信息」投喂主对话；空则省略
     * @param onInterrupted 错误时调用，仅用于 UI badge
     */
    fun getReply(
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        streamId: String,
        chatModel: String? = null,
        toolInfo: String? = null,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (reason: String) -> Unit
    ) {
        QwenClient.cancelCurrentRequest()
        val userId = IdManager.getInstallId()
        val sessionId = IdManager.getSessionId()
        val requestId = ApiConfig.nextRequestId()
        val systemAnchor = SystemAnchor.getText()
        QwenClient.callApi(userId, sessionId, requestId, streamId, systemAnchor, userMessage, imageUrlList, chatModel, toolInfo, onChunk, onComplete, onInterrupted)
    }
}


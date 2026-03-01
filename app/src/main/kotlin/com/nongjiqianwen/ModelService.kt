package com.nongjiqianwen

/**
 * 模型服务接口：非流式丢次返回；错误兜底；stop/cancel 占位保留?
 * 当前仅走主对话调用链，不包含外部联网工具注入。
 */
object ModelService {

    /**
     * 获取模型回复（非流式，一?onChunk(完整文本) + onComplete?
     * @param chatModel 主对话档位标识（free/plus/pro）；主对话模型固?MODEL_MAIN(qwen3.5-plus)，B 摘要固定 MODEL_B_SUMMARY(qwen-flash)
     * @param onInterrupted 閿欒鏃惰皟鐢紝浠呯敤浜?UI badge
     */
    fun getReply(
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        streamId: String,
        chatModel: String? = null,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (reason: String) -> Unit,
        onInterruptedResumable: ((streamId: String, reason: String) -> Unit)? = null
    ) {
        QwenClient.cancelCurrentRequest()
        val userId = IdManager.getClientId()
        val sessionId = IdManager.getSessionId()
        val requestId = ApiConfig.nextRequestId()
        QwenClient.callApi(
            userId = userId,
            sessionId = sessionId,
            requestId = requestId,
            streamId = streamId,
            userMessage = userMessage,
            imageUrlList = imageUrlList,
            chatModel = chatModel,
            onChunk = onChunk,
            onComplete = onComplete,
            onInterrupted = onInterrupted,
            onInterruptedResumable = onInterruptedResumable
        )
    }

    /** 补全请求：无 tools，userMessage 为请从我已输出的内容继续…? prefix；同丢轮继续生成，成功?onComplete ?A?*/
    fun getReplyContinuation(
        streamId: String,
        continuationUserMessage: String,
        chatModel: String? = null,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (reason: String) -> Unit,
        onInterruptedResumable: ((streamId: String, reason: String) -> Unit)? = null
    ) {
        QwenClient.cancelCurrentRequest()
        val userId = IdManager.getClientId()
        val sessionId = IdManager.getSessionId()
        val requestId = ApiConfig.nextRequestId()
        QwenClient.callApi(
            userId = userId,
            sessionId = sessionId,
            requestId = requestId,
            streamId = streamId,
            userMessage = continuationUserMessage,
            imageUrlList = emptyList(),
            chatModel = chatModel,
            onChunk = onChunk,
            onComplete = onComplete,
            onInterrupted = onInterrupted,
            onInterruptedResumable = onInterruptedResumable
        )
    }
}


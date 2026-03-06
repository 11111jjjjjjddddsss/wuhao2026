package com.nongjiqianwen

/**
 * 模型服务接口：非流式单次返回；错误兜底；保留 stop/cancel 扩展位。
 * 当前仅走主对话调用链，不包含外部联网工具注入。
 */
object ModelService {

    /**
     * 获取模型回复：非流式返回完整文本，回调 onChunk(完整文本) 后进入 onComplete。
     * @param chatModel 主对话档位标识（free/plus/pro）；主对话模型固定 MODEL_MAIN(qwen3.5-plus)
     * @param onInterrupted 错误时调用，仅用于 UI 状态提示
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

    /** 补全请求：无 tools，userMessage 为“请从我已输出的内容继续”一类前缀；成功后进入 onComplete。 */
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

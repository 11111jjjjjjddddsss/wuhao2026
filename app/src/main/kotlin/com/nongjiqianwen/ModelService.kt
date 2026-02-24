package com.nongjiqianwen

/**
 * 模型服务接口：非流式一次返回；错误兜底；stop/cancel 占位保留。
 * 联网搜索：由模型自行判断，工程侧不提前搜、不替模型判断；仅提供 BochaClient.webSearch 纯工具，本接口不传 toolInfo。
 */
object ModelService {

    /**
     * 获取模型回复（非流式，一次 onChunk(完整文本) + onComplete）
     * @param chatModel 主对话档位标识（free/plus/pro）；主对话模型固定 MODEL_MAIN(qwen3.5-plus)，B 摘要固定 MODEL_B_SUMMARY(qwen-flash)
     * @param onInterrupted 错误时调用，仅用于 UI badge
     */
    fun getReply(
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        streamId: String,
        chatModel: String? = null,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (reason: String) -> Unit,
        onToolInfo: ((streamId: String, toolName: String, text: String) -> Unit)? = null,
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
            toolInfo = null,
            onChunk = onChunk,
            onComplete = onComplete,
            onInterrupted = onInterrupted,
            onToolInfo = onToolInfo,
            onInterruptedResumable = onInterruptedResumable
        )
    }

    /** 补全请求：无 tools，userMessage 为“请从我已输出的内容继续…”+ prefix；同一轮继续生成，成功才 onComplete 写 A。 */
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
            toolInfo = null,
            onChunk = onChunk,
            onComplete = onComplete,
            onInterrupted = onInterrupted,
            onToolInfo = null,
            onInterruptedResumable = onInterruptedResumable
        )
    }
}

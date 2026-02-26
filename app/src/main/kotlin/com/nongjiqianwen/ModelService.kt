package com.nongjiqianwen

/**
 * 妯″瀷鏈嶅姟鎺ュ彛锛氶潪娴佸紡涓€娆¤繑鍥烇紱閿欒鍏滃簳锛泂top/cancel 鍗犱綅淇濈暀銆?
 * 当前仅走主对话调用链，不包含外部联网工具注入。
 */
object ModelService {

    /**
     * 鑾峰彇妯″瀷鍥炲锛堥潪娴佸紡锛屼竴娆?onChunk(瀹屾暣鏂囨湰) + onComplete锛?
     * @param chatModel 涓诲璇濇。浣嶆爣璇嗭紙free/plus/pro锛夛紱涓诲璇濇ā鍨嬪浐瀹?MODEL_MAIN(qwen3.5-plus)锛孊 鎽樿鍥哄畾 MODEL_B_SUMMARY(qwen-flash)
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

    /** 琛ュ叏璇锋眰锛氭棤 tools锛寀serMessage 涓衡€滆浠庢垜宸茶緭鍑虹殑鍐呭缁х画鈥︹€? prefix锛涘悓涓€杞户缁敓鎴愶紝鎴愬姛鎵?onComplete 鍐?A銆?*/
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


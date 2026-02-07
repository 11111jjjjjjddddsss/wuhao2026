package com.nongjiqianwen

import android.util.Log

/**
 * 模型服务接口：非流式一次返回；错误兜底；stop/cancel 占位保留。
 * 新增二阶段联网搜索策略：模型决策 → 工程执行 → 最终答复
 */
object ModelService {
    private const val TAG = "ModelService"

    /**
     * 获取模型回复（非流式，一次 onChunk(完整文本) + onComplete）
     * @param chatModel 传 "plus" 为专家模式（qwen3-vl-plus），否则 qwen3-vl-flash
     * @param onInterrupted 错误时调用，仅用于 UI badge
     */
    fun getReply(
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        streamId: String,
        chatModel: String? = null,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (reason: String) -> Unit
    ) {
        // 第一阶段：决策阶段
        decisionPhase(userMessage, imageUrlList, streamId, chatModel, onChunk, onComplete, onInterrupted)
    }

    /**
     * 第一阶段：决策阶段
     * 模型输出 NEED_SEARCH=0/1 和 QUERY=xxx
     */
    private fun decisionPhase(
        userMessage: String,
        imageUrlList: List<String>,
        streamId: String,
        chatModel: String?,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)?,
        onInterrupted: (reason: String) -> Unit
    ) {
        QwenClient.cancelCurrentRequest()
        val userId = IdManager.getInstallId()
        val sessionId = IdManager.getSessionId()
        val requestId = ApiConfig.nextRequestId()
        
        // 决策阶段不传入任何工具信息
        QwenClient.callApi(
            userId = userId,
            sessionId = sessionId,
            requestId = requestId,
            streamId = streamId,
            userMessage = userMessage,
            imageUrlList = imageUrlList,
            chatModel = chatModel,
            toolInfo = null,
            onChunk = { chunk ->
                // 解析模型输出，查找 NEED_SEARCH 和 QUERY
                val decision = parseSearchDecision(chunk)
                if (decision != null) {
                    if (decision.needSearch) {
                        // 需要搜索，进入执行阶段
                        executionPhase(
                            userMessage = userMessage,
                            imageUrlList = imageUrlList,
                            streamId = streamId,
                            chatModel = chatModel,
                            query = decision.query,
                            onChunk = onChunk,
                            onComplete = onComplete,
                            onInterrupted = onInterrupted
                        )
                    } else {
                        // 不需要搜索，直接返回决策阶段的回答
                        onChunk(chunk)
                        onComplete?.invoke()
                    }
                } else {
                    // 未找到决策指令，继续等待完整回答
                    onChunk(chunk)
                }
            },
            onComplete = {
                // 决策阶段完成，如果还没有触发执行阶段，则完成
                onComplete?.invoke()
            },
            onInterrupted = onInterrupted
        )
    }

    /**
     * 第二阶段：执行阶段
     * 执行搜索并生成最终答复
     */
    private fun executionPhase(
        userMessage: String,
        imageUrlList: List<String>,
        streamId: String,
        chatModel: String?,
        query: String,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)?,
        onInterrupted: (reason: String) -> Unit
    ) {
        Thread {
            // 执行搜索
            val searchResult = performWebSearch(query)
            
            // 生成最终答复
            QwenClient.cancelCurrentRequest()
            val userId = IdManager.getInstallId()
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
                toolInfo = searchResult,
                onChunk = onChunk,
                onComplete = onComplete,
                onInterrupted = onInterrupted
            )
        }.start()
    }

    /**
     * 执行联网搜索
     */
    private fun performWebSearch(query: String): String {
        return try {
            val result = BochaClient.webSearch(query)
            if (result != null && result.isNotBlank()) {
                val timestamp = System.currentTimeMillis()
                "时间戳：$timestamp\n$result"
            } else {
                val timestamp = System.currentTimeMillis()
                "时间戳：$timestamp\n未命中相关结果"
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "web-search error", e)
            val timestamp = System.currentTimeMillis()
            "时间戳：$timestamp\n搜索失败"
        }
    }

    /**
     * 解析搜索决策
     * 格式：NEED_SEARCH=0/1\nQUERY=xxx
     */
    private fun parseSearchDecision(text: String): SearchDecision? {
        val lines = text.split("\n")
        var needSearch = false
        var query = ""
        
        for (line in lines) {
            when {
                line.startsWith("NEED_SEARCH=") -> {
                    val value = line.substringAfter("NEED_SEARCH=").trim()
                    needSearch = value == "1"
                }
                line.startsWith("QUERY=") -> {
                    query = line.substringAfter("QUERY=").trim()
                }
            }
        }
        
        return if (needSearch || query.isNotBlank()) {
            SearchDecision(needSearch, query)
        } else {
            null
        }
    }

    private data class SearchDecision(val needSearch: Boolean, val query: String)
}


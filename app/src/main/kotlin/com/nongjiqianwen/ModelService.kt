package com.nongjiqianwen

import android.os.Handler
import android.os.Looper

/**
 * 模型服务接口
 * 支持流式输出（通过回调方式）
 */
object ModelService {
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * 获取模型回复（非流式）
     * @param userMessage 用户输入的消息
     * @param imageUrlList 图片URL列表（可选，必须是公网可访问的URL）
     * @param onChunk 回调函数，返回完整响应文本
     * @param onComplete 请求完成回调（可选），请求结束时调用
     */
    fun getReply(
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null
    ) {
        // 直接调用 QwenClient
        QwenClient.callApi(userMessage, imageUrlList, onChunk, onComplete)
    }
}

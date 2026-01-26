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
     * 获取模型回复
     * @param userMessage 用户输入的消息
     * @param onChunk 流式输出回调，每次接收到数据块时调用
     * @param onComplete 请求完成回调（可选），请求结束时调用
     */
    fun getReply(userMessage: String, onChunk: (String) -> Unit, onComplete: (() -> Unit)? = null) {
        // 直接调用 QwenClient，它内部会处理流式返回
        QwenClient.callApi(userMessage, onChunk, onComplete)
    }
}

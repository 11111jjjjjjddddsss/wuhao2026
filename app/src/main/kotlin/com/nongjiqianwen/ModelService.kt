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
     */
    fun getReply(userMessage: String, onChunk: (String) -> Unit) {
        // 模拟流式返回：将回复拆分成多个 chunk
        val fullReply = "已收到，后续接模型"
        val chunks = listOf("已收到", "，", "后续", "接", "模型")
        
        chunks.forEachIndexed { index, chunk ->
            handler.postDelayed({
                onChunk(chunk)
            }, (index * 200).toLong()) // 每个 chunk 间隔 200ms
        }
    }
}

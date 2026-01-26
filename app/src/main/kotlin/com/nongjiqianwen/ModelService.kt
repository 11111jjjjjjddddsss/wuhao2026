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
        // 在后台线程执行网络请求
        Thread {
            val content = QwenClient.callApi(userMessage)
            // 在主线程回调返回结果
            handler.post {
                if (content != null) {
                    onChunk(content)
                } else {
                    onChunk("模型暂不可用")
                }
            }
        }.start()
    }
}

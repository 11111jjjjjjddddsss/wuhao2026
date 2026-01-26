package com.nongjiqianwen

/**
 * 模型服务接口
 * 支持流式输出（通过回调方式）
 */
object ModelService {
    /**
     * 获取模型回复
     * @param userMessage 用户输入的消息
     * @param onChunk 流式输出回调，每次接收到数据块时调用（当前实现为一次性返回完整内容）
     */
    fun getReply(userMessage: String, onChunk: (String) -> Unit) {
        // 当前实现：直接返回完整回复
        // 未来可改为流式：多次调用 onChunk 传递数据块
        onChunk("已收到，后续接模型")
    }
}

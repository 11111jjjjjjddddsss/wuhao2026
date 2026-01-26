package com.nongjiqianwen

object FakeModelService {
    /**
     * 模拟模型回复
     * @param userMessage 用户输入的消息
     * @return 系统回复内容
     */
    fun getReply(userMessage: String): String {
        return "已收到，后续接模型"
    }
}

package com.nongjiqianwen

import java.util.UUID

/**
 * 模型服务接口：真流式 SSE，错误兜底，requestId/耗时日志。
 * 当前直连 DashScope；后期切服务端常驻 API 时只改此处与 QwenClient 的 URL/鉴权，不动 App 主链路。
 */
object ModelService {

    /**
     * 获取模型回复（真流式 SSE）
     * @param userMessage 用户输入
     * @param imageUrlList 图片 URL 列表（图在前、text 在后，最多 4 张，有图必有文字）
     * @param onChunk 逐 chunk 追加
     * @param onComplete 成功/失败均调用，保证 UI 可恢复
     */
    fun getReply(
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null
    ) {
        val requestId = ApiConfig.nextRequestId()
        QwenClient.callApi(requestId, userMessage, imageUrlList, onChunk, onComplete)
    }
}


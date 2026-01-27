package com.nongjiqianwen

data class Message(
    val text: String,
    val isUser: Boolean, // true表示用户消息，false表示系统回复
    val imageUris: List<String> = emptyList() // 图片URI列表（Base64或本地URI）
)

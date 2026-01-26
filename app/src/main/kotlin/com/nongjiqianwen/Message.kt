package com.nongjiqianwen

data class Message(
    val text: String,
    val isUser: Boolean // true表示用户消息，false表示系统回复
)

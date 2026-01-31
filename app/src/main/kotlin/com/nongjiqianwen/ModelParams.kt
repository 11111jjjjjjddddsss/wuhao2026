package com.nongjiqianwen

/**
 * 模型参数（冻结，工程层禁止私改）
 * A 层主对话与 B 层提取共用此唯一配置。
 */
object ModelParams {
    const val TEMPERATURE = 0.85
    const val TOP_P = 0.9
    const val MAX_TOKENS = 4000
    const val FREQUENCY_PENALTY = 0.0
    const val PRESENCE_PENALTY = 0.0
}

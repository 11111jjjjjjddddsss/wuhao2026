package com.nongjiqianwen

object InputNormalizer {
    /**
     * 规范化输入
     * @param input 原始输入
     * @return 规范化后的输入，如果为空则返回 null
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        return if (trimmed.isEmpty()) null else trimmed
    }
}

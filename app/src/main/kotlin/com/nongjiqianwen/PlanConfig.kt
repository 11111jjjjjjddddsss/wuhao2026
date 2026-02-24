package com.nongjiqianwen

enum class PlanId {
    FREE, PLUS, PRO
}

data class PlanConfig(
    val id: PlanId,
    val chatPerDay: Int,
    val aWindow: Int,
    val bExtractRounds: Int,
    val maxImages: Int
) {
    companion object {
        private val FREE = PlanConfig(PlanId.FREE, 6, 6, 6, 4)
        private val PLUS = PlanConfig(PlanId.PLUS, 20, 6, 6, 4)
        private val PRO = PlanConfig(PlanId.PRO, 35, 9, 9, 4)

        fun fromChatModel(chatModel: String?): PlanConfig {
            return when (chatModel?.trim()?.lowercase()) {
                "plus" -> PLUS
                "pro" -> PRO
                else -> FREE
            }
        }
    }
}

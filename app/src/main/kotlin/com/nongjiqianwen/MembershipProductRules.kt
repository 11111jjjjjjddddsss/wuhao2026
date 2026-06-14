package com.nongjiqianwen

internal const val MEMBERSHIP_FREE_DAILY_LIMIT = 6
internal const val MEMBERSHIP_PLUS_DAILY_LIMIT = 25
internal const val MEMBERSHIP_PRO_DAILY_LIMIT = 40
internal const val MEMBERSHIP_TOPUP_COUNT = 80

internal const val MEMBERSHIP_PLUS_PRICE_TEXT = "¥19.9/月"
internal const val MEMBERSHIP_PRO_PRICE_TEXT = "¥29.9/月"
internal const val MEMBERSHIP_TOPUP_PRICE_TEXT = "¥6 / 80次"

internal fun membershipTierDisplayName(tier: String): String =
    when (tier) {
        "plus" -> "Plus"
        "pro" -> "Pro"
        "unknown" -> "--"
        else -> "Free"
    }

internal fun membershipDailyLimitForTier(tier: String): Int =
    when (tier) {
        "plus" -> MEMBERSHIP_PLUS_DAILY_LIMIT
        "pro" -> MEMBERSHIP_PRO_DAILY_LIMIT
        else -> MEMBERSHIP_FREE_DAILY_LIMIT
    }

internal fun membershipPaidDailyLimitForTier(tier: String?): Int? =
    when (tier) {
        "plus" -> MEMBERSHIP_PLUS_DAILY_LIMIT
        "pro" -> MEMBERSHIP_PRO_DAILY_LIMIT
        else -> null
    }

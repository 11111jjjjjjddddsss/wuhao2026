package com.nongjiqianwen

internal val bareWebUrlRegex = Regex(
    "(?i)(?<![A-Za-z0-9._%+\\-@])" +
        "((?:https?://)?(?:www\\.)?" +
        "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+" +
        "(?:com|cn|net|org|gov|edu|io|ai|top|xyz|info|biz|cc|tv|me|app|dev|site|tech)" +
        "(?::\\d{1,5})?" +
        "(?:[/?#][A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?)"
)

internal fun normalizeWebLinkTarget(raw: String): String {
    val trimmed = raw.trim().removePrefix("<").removeSuffix(">")
    if (trimmed.isBlank()) return raw.trim()
    return if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

internal fun trimBareWebUrlDisplayText(raw: String): String {
    val trailingPunctuation = ".,;:!?，。；：！？)]}）】》」』”\"'"
    return raw.trimEnd { it in trailingPunctuation }
}

package com.nongjiqianwen

import androidx.compose.ui.geometry.Rect

internal data class ComposerCollapsePreparation(
    val settlingMinHeightPx: Int,
    val settlingChromeHeightPx: Int,
    val shouldSuppressCursor: Boolean,
    val shouldClearFocus: Boolean
)

internal data class ComposerOverlaySnapshot(
    val hostBoundsInWindow: Rect,
    val chromeBoundsInWindow: Rect,
    val bottomHeightPx: Int
)

internal fun deriveComposerStableBottomBarHeightPx(
    inputChromeRowHeightPx: Int,
    safeBottomInsetPx: Int,
    startupBottomBarHeightEstimatePx: Int
): Int {
    return (inputChromeRowHeightPx + safeBottomInsetPx)
        .coerceAtLeast(startupBottomBarHeightEstimatePx)
}

internal fun shouldApplyComposerBottomBarHeight(
    currentBottomBarHeightPx: Int,
    stableBottomBarHeightPx: Int,
    imeVisible: Boolean,
    jitterTolerancePx: Int
): Boolean {
    val deltaPx = kotlin.math.abs(currentBottomBarHeightPx - stableBottomBarHeightPx)
    return currentBottomBarHeightPx != stableBottomBarHeightPx &&
        (imeVisible || deltaPx > jitterTolerancePx)
}

internal fun prepareComposerCollapse(
    inputContentHeightPx: Int,
    startupInputContentHeightEstimatePx: Int,
    inputChromeRowHeightPx: Int
): ComposerCollapsePreparation {
    return ComposerCollapsePreparation(
        settlingMinHeightPx = inputContentHeightPx.coerceAtLeast(startupInputContentHeightEstimatePx),
        settlingChromeHeightPx = inputChromeRowHeightPx,
        shouldSuppressCursor = true,
        shouldClearFocus = true
    )
}

internal fun shouldReleaseComposerSettling(
    inputText: String,
    inputFieldFocused: Boolean,
    composerSettlingMinHeightPx: Int,
    composerSettlingChromeHeightPx: Int,
    sendUiSettling: Boolean,
    imeVisible: Boolean,
    bottomBarHeightPx: Int,
    stableBottomBarHeightPx: Int,
    jitterTolerancePx: Int
): Boolean {
    if (inputText.isNotEmpty() || inputFieldFocused) return true
    if (composerSettlingMinHeightPx <= 0 && composerSettlingChromeHeightPx <= 0) return false
    if (sendUiSettling) return false
    if (imeVisible) return false
    return kotlin.math.abs(bottomBarHeightPx - stableBottomBarHeightPx) <= jitterTolerancePx
}

internal fun shouldDismissComposerCollapseOverlay(
    overlayVisible: Boolean,
    inputText: String,
    inputFieldFocused: Boolean,
    sendUiSettling: Boolean,
    imeVisible: Boolean,
    composerSettlingChromeHeightPx: Int,
    bottomBarHeightPx: Int,
    stableBottomBarHeightPx: Int,
    jitterTolerancePx: Int
): Boolean {
    if (!overlayVisible) return false
    if (inputText.isNotEmpty() || inputFieldFocused) return true
    if (sendUiSettling || imeVisible) return false
    if (composerSettlingChromeHeightPx > 0) return false
    return kotlin.math.abs(bottomBarHeightPx - stableBottomBarHeightPx) <= jitterTolerancePx
}

internal fun resolveBottomContentReservedHeightPx(
    overlayVisible: Boolean,
    overlayBottomHeightPx: Int,
    effectiveBottomBarHeightPx: Int
): Int {
    return if (overlayVisible && overlayBottomHeightPx > 0) {
        overlayBottomHeightPx
    } else {
        effectiveBottomBarHeightPx
    }
}

internal fun resolveComposerOverlayHintText(
    composerStatusHintVisible: Boolean,
    composerStatusHintText: String,
    inputLimitHintVisible: Boolean
): String? {
    return when {
        composerStatusHintVisible && composerStatusHintText.isNotBlank() -> composerStatusHintText
        inputLimitHintVisible -> "已超过6000字，暂时不能发送"
        else -> null
    }
}

internal fun captureComposerOverlaySnapshot(
    hostBoundsInWindow: Rect?,
    chromeBoundsInWindow: Rect?,
    bottomHeightPx: Int
): ComposerOverlaySnapshot? {
    val hostBounds = hostBoundsInWindow ?: return null
    val chromeBounds = chromeBoundsInWindow ?: return null
    return ComposerOverlaySnapshot(
        hostBoundsInWindow = hostBounds,
        chromeBoundsInWindow = chromeBounds,
        bottomHeightPx = bottomHeightPx
    )
}

package com.nongjiqianwen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect

internal data class ChatComposerRuntimeState(
    val inputLimitHintVisible: MutableState<Boolean>,
    val inputLimitHintTick: MutableIntState,
    val composerStatusHintVisible: MutableState<Boolean>,
    val composerStatusHintTick: MutableIntState,
    val composerStatusHintText: MutableState<String>,
    val inputFieldFocused: MutableState<Boolean>,
    val suppressInputCursor: MutableState<Boolean>,
    val inputContentHeightPx: MutableIntState,
    val composerSettlingMinHeightPx: MutableIntState,
    val composerSettlingChromeHeightPx: MutableIntState,
    val sendUiSettling: MutableState<Boolean>,
    val inputFieldBoundsInWindow: MutableState<Rect?>,
    val composerHostBoundsInWindow: MutableState<Rect?>,
    val composerChromeBoundsInWindow: MutableState<Rect?>,
    val composerCollapseOverlayVisible: MutableState<Boolean>,
    val composerCollapseOverlayHostBoundsSnapshot: MutableState<Rect?>,
    val composerCollapseOverlayChromeBoundsSnapshot: MutableState<Rect?>,
    val composerCollapseOverlayBottomHeightPx: MutableIntState,
    val composerCollapseOverlayPrewarmed: MutableState<Boolean>
)

@Composable
internal fun rememberChatComposerRuntimeState(
    chatScopeId: String,
    startupInputContentHeightEstimatePx: Int
): ChatComposerRuntimeState {
    val inputLimitHintVisible = remember(chatScopeId) { mutableStateOf(false) }
    val inputLimitHintTick = remember(chatScopeId) { mutableIntStateOf(0) }
    val composerStatusHintVisible = remember(chatScopeId) { mutableStateOf(false) }
    val composerStatusHintTick = remember(chatScopeId) { mutableIntStateOf(0) }
    val composerStatusHintText = remember(chatScopeId) { mutableStateOf("") }
    val inputFieldFocused = remember(chatScopeId) { mutableStateOf(false) }
    val suppressInputCursor = remember(chatScopeId) { mutableStateOf(false) }
    val inputContentHeightPx = remember(chatScopeId, startupInputContentHeightEstimatePx) {
        mutableIntStateOf(startupInputContentHeightEstimatePx)
    }
    val composerSettlingMinHeightPx = remember(chatScopeId) { mutableIntStateOf(0) }
    val composerSettlingChromeHeightPx = remember(chatScopeId) { mutableIntStateOf(0) }
    val sendUiSettling = remember(chatScopeId) { mutableStateOf(false) }
    val inputFieldBoundsInWindow = remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    val composerHostBoundsInWindow = remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    val composerChromeBoundsInWindow = remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    val composerCollapseOverlayVisible = remember(chatScopeId) { mutableStateOf(false) }
    val composerCollapseOverlayHostBoundsSnapshot = remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    val composerCollapseOverlayChromeBoundsSnapshot = remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    val composerCollapseOverlayBottomHeightPx = remember(chatScopeId) { mutableIntStateOf(0) }
    val composerCollapseOverlayPrewarmed = remember(chatScopeId) { mutableStateOf(false) }
    return remember(chatScopeId, startupInputContentHeightEstimatePx) {
        ChatComposerRuntimeState(
            inputLimitHintVisible = inputLimitHintVisible,
            inputLimitHintTick = inputLimitHintTick,
            composerStatusHintVisible = composerStatusHintVisible,
            composerStatusHintTick = composerStatusHintTick,
            composerStatusHintText = composerStatusHintText,
            inputFieldFocused = inputFieldFocused,
            suppressInputCursor = suppressInputCursor,
            inputContentHeightPx = inputContentHeightPx,
            composerSettlingMinHeightPx = composerSettlingMinHeightPx,
            composerSettlingChromeHeightPx = composerSettlingChromeHeightPx,
            sendUiSettling = sendUiSettling,
            inputFieldBoundsInWindow = inputFieldBoundsInWindow,
            composerHostBoundsInWindow = composerHostBoundsInWindow,
            composerChromeBoundsInWindow = composerChromeBoundsInWindow,
            composerCollapseOverlayVisible = composerCollapseOverlayVisible,
            composerCollapseOverlayHostBoundsSnapshot = composerCollapseOverlayHostBoundsSnapshot,
            composerCollapseOverlayChromeBoundsSnapshot = composerCollapseOverlayChromeBoundsSnapshot,
            composerCollapseOverlayBottomHeightPx = composerCollapseOverlayBottomHeightPx,
            composerCollapseOverlayPrewarmed = composerCollapseOverlayPrewarmed
        )
    }
}

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
    @Suppress("UNUSED_PARAMETER") inputContentHeightPx: Int,
    @Suppress("UNUSED_PARAMETER") startupInputContentHeightEstimatePx: Int,
    @Suppress("UNUSED_PARAMETER") inputChromeRowHeightPx: Int
): ComposerCollapsePreparation {
    return ComposerCollapsePreparation(
        settlingMinHeightPx = 0,
        settlingChromeHeightPx = 0,
        shouldSuppressCursor = false,
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
    effectiveBottomBarHeightPx: Int,
    extraReservedHeightPx: Int
): Int {
    val baseReservedHeightPx = if (overlayVisible && overlayBottomHeightPx > 0) {
        overlayBottomHeightPx
    } else {
        effectiveBottomBarHeightPx
    }
    return baseReservedHeightPx + extraReservedHeightPx.coerceAtLeast(0)
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

@Composable
internal fun BindComposerRuntimeEffects(
    chatScopeId: String,
    inputChromeMeasured: Boolean,
    inputText: String,
    inputFieldFocused: Boolean,
    composerSettlingMinHeightPxState: MutableIntState,
    composerSettlingChromeHeightPxState: MutableIntState,
    sendUiSettling: Boolean,
    imeVisible: Boolean,
    bottomBarHeightPxState: MutableIntState,
    inputChromeRowHeightPx: Int,
    stableBottomBarHeightPx: Int,
    jitterTolerancePx: Int,
    composerCollapseOverlayVisibleState: MutableState<Boolean>,
    composerHostBoundsInWindow: Rect?,
    composerChromeBoundsInWindow: Rect?,
    effectiveBottomBarHeightPx: Int,
    composerCollapseOverlayHostBoundsSnapshotState: MutableState<Rect?>,
    composerCollapseOverlayChromeBoundsSnapshotState: MutableState<Rect?>,
    composerCollapseOverlayBottomHeightPxState: MutableIntState,
    composerCollapseOverlayPrewarmedState: MutableState<Boolean>,
    startupLayoutReady: Boolean
) {
    LaunchedEffect(
        inputChromeMeasured,
        inputChromeRowHeightPx,
        inputText,
        inputFieldFocused,
        sendUiSettling
    ) {
        if (!inputChromeMeasured) return@LaunchedEffect
        if (inputText.isNotEmpty() || inputFieldFocused || sendUiSettling) {
            return@LaunchedEffect
        }
        if (
            shouldApplyComposerBottomBarHeight(
                currentBottomBarHeightPx = bottomBarHeightPxState.intValue,
                stableBottomBarHeightPx = stableBottomBarHeightPx,
                imeVisible = imeVisible,
                jitterTolerancePx = jitterTolerancePx
            )
        ) {
            bottomBarHeightPxState.intValue = stableBottomBarHeightPx
        }
    }

    LaunchedEffect(
        composerSettlingMinHeightPxState.intValue,
        composerSettlingChromeHeightPxState.intValue,
        sendUiSettling,
        imeVisible,
        inputFieldFocused,
        inputText,
        bottomBarHeightPxState.intValue,
        inputChromeRowHeightPx
    ) {
        if (inputText.isNotEmpty() || inputFieldFocused) {
            composerSettlingMinHeightPxState.intValue = 0
            composerSettlingChromeHeightPxState.intValue = 0
            return@LaunchedEffect
        }
        if (
            shouldReleaseComposerSettling(
                inputText = inputText,
                inputFieldFocused = inputFieldFocused,
                composerSettlingMinHeightPx = composerSettlingMinHeightPxState.intValue,
                composerSettlingChromeHeightPx = composerSettlingChromeHeightPxState.intValue,
                sendUiSettling = sendUiSettling,
                imeVisible = imeVisible,
                bottomBarHeightPx = bottomBarHeightPxState.intValue,
                stableBottomBarHeightPx = stableBottomBarHeightPx,
                jitterTolerancePx = jitterTolerancePx
            )
        ) {
            composerSettlingMinHeightPxState.intValue = 0
            composerSettlingChromeHeightPxState.intValue = 0
            return@LaunchedEffect
        }
        if (composerSettlingMinHeightPxState.intValue <= 0 && composerSettlingChromeHeightPxState.intValue <= 0) {
            return@LaunchedEffect
        }
        if (sendUiSettling || !imeVisible) return@LaunchedEffect
        withFrameNanos { }
        if (!sendUiSettling && !inputFieldFocused && inputText.isEmpty()) {
            composerSettlingMinHeightPxState.intValue = 0
            composerSettlingChromeHeightPxState.intValue = 0
        }
    }

    LaunchedEffect(
        composerCollapseOverlayVisibleState.value,
        sendUiSettling,
        imeVisible,
        composerSettlingChromeHeightPxState.intValue,
        bottomBarHeightPxState.intValue,
        inputChromeRowHeightPx,
        inputFieldFocused,
        inputText
    ) {
        if (
            shouldDismissComposerCollapseOverlay(
                overlayVisible = composerCollapseOverlayVisibleState.value,
                inputText = inputText,
                inputFieldFocused = inputFieldFocused,
                sendUiSettling = sendUiSettling,
                imeVisible = imeVisible,
                composerSettlingChromeHeightPx = composerSettlingChromeHeightPxState.intValue,
                bottomBarHeightPx = bottomBarHeightPxState.intValue,
                stableBottomBarHeightPx = stableBottomBarHeightPx,
                jitterTolerancePx = jitterTolerancePx
            )
        ) {
            composerCollapseOverlayVisibleState.value = false
            return@LaunchedEffect
        }
        if (!composerCollapseOverlayVisibleState.value) return@LaunchedEffect
        if (inputText.isNotEmpty() || inputFieldFocused) {
            composerCollapseOverlayVisibleState.value = false
            return@LaunchedEffect
        }
        if (
            shouldDismissComposerCollapseOverlay(
                overlayVisible = composerCollapseOverlayVisibleState.value,
                inputText = inputText,
                inputFieldFocused = inputFieldFocused,
                sendUiSettling = sendUiSettling,
                imeVisible = imeVisible,
                composerSettlingChromeHeightPx = composerSettlingChromeHeightPxState.intValue,
                bottomBarHeightPx = bottomBarHeightPxState.intValue,
                stableBottomBarHeightPx = stableBottomBarHeightPx,
                jitterTolerancePx = jitterTolerancePx
            )
        ) {
            repeat(2) { withFrameNanos { } }
            if (
                shouldDismissComposerCollapseOverlay(
                    overlayVisible = composerCollapseOverlayVisibleState.value,
                    inputText = inputText,
                    inputFieldFocused = inputFieldFocused,
                    sendUiSettling = sendUiSettling,
                    imeVisible = imeVisible,
                    composerSettlingChromeHeightPx = composerSettlingChromeHeightPxState.intValue,
                    bottomBarHeightPx = bottomBarHeightPxState.intValue,
                    stableBottomBarHeightPx = stableBottomBarHeightPx,
                    jitterTolerancePx = jitterTolerancePx
                )
            ) {
                composerCollapseOverlayVisibleState.value = false
            }
        }
    }

    LaunchedEffect(
        composerCollapseOverlayVisibleState.value,
        composerHostBoundsInWindow,
        composerChromeBoundsInWindow,
        effectiveBottomBarHeightPx,
        composerCollapseOverlayPrewarmedState.value
    ) {
        if (composerCollapseOverlayVisibleState.value) return@LaunchedEffect
        if (composerCollapseOverlayPrewarmedState.value) return@LaunchedEffect
        captureComposerOverlaySnapshot(
            hostBoundsInWindow = composerHostBoundsInWindow,
            chromeBoundsInWindow = composerChromeBoundsInWindow,
            bottomHeightPx = effectiveBottomBarHeightPx
        )?.let { snapshot ->
            composerCollapseOverlayHostBoundsSnapshotState.value = snapshot.hostBoundsInWindow
            composerCollapseOverlayChromeBoundsSnapshotState.value = snapshot.chromeBoundsInWindow
            composerCollapseOverlayBottomHeightPxState.intValue = snapshot.bottomHeightPx
            composerCollapseOverlayPrewarmedState.value = true
        }
    }

    LaunchedEffect(
        imeVisible,
        inputFieldFocused,
        composerCollapseOverlayVisibleState.value,
        composerHostBoundsInWindow,
        composerChromeBoundsInWindow,
        effectiveBottomBarHeightPx
    ) {
        if (composerCollapseOverlayVisibleState.value) return@LaunchedEffect
        if (!imeVisible && !inputFieldFocused) return@LaunchedEffect
        if (composerHostBoundsInWindow == null || composerChromeBoundsInWindow == null) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        if (composerCollapseOverlayVisibleState.value) return@LaunchedEffect
        if (!imeVisible && !inputFieldFocused) return@LaunchedEffect
        captureComposerOverlaySnapshot(
            hostBoundsInWindow = composerHostBoundsInWindow,
            chromeBoundsInWindow = composerChromeBoundsInWindow,
            bottomHeightPx = effectiveBottomBarHeightPx
        )?.let { snapshot ->
            composerCollapseOverlayHostBoundsSnapshotState.value = snapshot.hostBoundsInWindow
            composerCollapseOverlayChromeBoundsSnapshotState.value = snapshot.chromeBoundsInWindow
            composerCollapseOverlayBottomHeightPxState.intValue = snapshot.bottomHeightPx
            composerCollapseOverlayPrewarmedState.value = true
        }
    }

    LaunchedEffect(chatScopeId) {
        composerCollapseOverlayBottomHeightPxState.intValue = 0
        composerCollapseOverlayHostBoundsSnapshotState.value = null
        composerCollapseOverlayChromeBoundsSnapshotState.value = null
        composerCollapseOverlayPrewarmedState.value = false
    }

    LaunchedEffect(startupLayoutReady) {
        if (!startupLayoutReady) {
            composerCollapseOverlayPrewarmedState.value = false
        }
    }
}

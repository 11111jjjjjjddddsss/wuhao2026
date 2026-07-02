package com.nongjiqianwen

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private const val HAMBURGER_PAGE_ENTER_MS = 180
private const val HAMBURGER_PAGE_EXIT_MS = 150
private const val SUPPORT_MESSAGE_MAX_CHARS = 2000
internal const val SUPPORT_SEND_FAILED_HINT = "发送失败，请检查网络后重试"
private val HamburgerBackButtonTopPadding = 4.dp
private const val APP_UPDATE_PROMPT_PREFS = "app_update_prompt"
private const val APP_UPDATE_LAST_PROMPTED_VERSION_CODE_KEY = "last_prompted_version_code"
private const val APP_UPDATE_PENDING_INSTALL_VERSION_CODE_KEY = "pending_install_version_code"
private const val APP_ICP_RECORD_NUMBER = "京ICP备2026031728号-2A"

private fun String.supportMessageCharCount(): Int =
    codePointCount(0, length)

private fun String.takeSupportMessageChars(maxChars: Int): String {
    if (maxChars <= 0) return ""
    if (supportMessageCharCount() <= maxChars) return this
    val endIndex = offsetByCodePoints(0, maxChars)
    return substring(0, endIndex)
}

private fun supportFeedbackPayloadFingerprint(
    body: String,
    images: List<ComposerImageAttachment>
): String = buildString {
    append(body)
    append('\n')
    images.forEach { image ->
        append(image.uri)
        append('\n')
    }
}

private fun Context.loadLastPromptedUpdateVersionCode(): Int =
    applicationContext
        .getSharedPreferences(APP_UPDATE_PROMPT_PREFS, Context.MODE_PRIVATE)
        .getInt(APP_UPDATE_LAST_PROMPTED_VERSION_CODE_KEY, 0)

private fun Context.saveLastPromptedUpdateVersionCode(versionCode: Int) {
    if (versionCode <= 0) return
    applicationContext
        .getSharedPreferences(APP_UPDATE_PROMPT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt(APP_UPDATE_LAST_PROMPTED_VERSION_CODE_KEY, versionCode)
        .apply()
}

private fun Context.clearLastPromptedUpdateVersionCode(versionCode: Int) {
    if (versionCode <= 0) return
    val prefs = applicationContext.getSharedPreferences(APP_UPDATE_PROMPT_PREFS, Context.MODE_PRIVATE)
    if (prefs.getInt(APP_UPDATE_LAST_PROMPTED_VERSION_CODE_KEY, 0) != versionCode) return
    prefs.edit().remove(APP_UPDATE_LAST_PROMPTED_VERSION_CODE_KEY).apply()
}

private fun Context.loadPendingInstallAttemptVersionCode(): Int =
    applicationContext
        .getSharedPreferences(APP_UPDATE_PROMPT_PREFS, Context.MODE_PRIVATE)
        .getInt(APP_UPDATE_PENDING_INSTALL_VERSION_CODE_KEY, 0)

private fun Context.savePendingInstallAttemptVersionCode(versionCode: Int) {
    if (versionCode <= 0) return
    applicationContext
        .getSharedPreferences(APP_UPDATE_PROMPT_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putInt(APP_UPDATE_PENDING_INSTALL_VERSION_CODE_KEY, versionCode)
        .apply()
}

private fun Context.clearPendingInstallAttemptVersionCode(versionCode: Int = 0) {
    val prefs = applicationContext.getSharedPreferences(APP_UPDATE_PROMPT_PREFS, Context.MODE_PRIVATE)
    val pendingVersionCode = prefs.getInt(APP_UPDATE_PENDING_INSTALL_VERSION_CODE_KEY, 0)
    if (pendingVersionCode <= 0) return
    if (versionCode > 0 && pendingVersionCode != versionCode) return
    prefs.edit().remove(APP_UPDATE_PENDING_INSTALL_VERSION_CODE_KEY).apply()
}

@Suppress("DEPRECATION")
private fun Context.currentInstalledVersionCode(): Long =
    runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }.getOrDefault(BuildConfig.VERSION_CODE.toLong())

private fun buildSupportLinkedText(
    text: String,
    linkColor: Color,
    linkInteractionListener: LinkInteractionListener? = null
): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val bareUrl = bareWebUrlRegex.find(text, index)
            if (bareUrl == null) {
                append(text.substring(index))
                break
            }
            if (bareUrl.range.first > index) {
                append(text.substring(index, bareUrl.range.first))
            }
            val displayText = trimBareWebUrlDisplayText(bareUrl.value)
            if (displayText.isEmpty()) {
                append(bareUrl.value)
                index = bareUrl.range.last + 1
                continue
            }
            withLink(
                LinkAnnotation.Url(
                    url = normalizeWebLinkTarget(displayText),
                    linkInteractionListener = linkInteractionListener
                )
            ) {
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(displayText)
                }
            }
            index = bareUrl.range.first + displayText.length
        }
    }
}

private fun Context.cleanupPendingComposerCameraImage(
    uriString: String?,
    galleryBacked: Boolean,
    temporaryFilePath: String?
) {
    val uri = uriString
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Uri.parse(it) }.getOrNull() }
    if (uri != null) {
        revokeComposerCameraUri(uri)
    }
    if (galleryBacked && uri != null) {
        deleteGalleryComposerCameraImage(uri)
    } else {
        deleteTemporaryComposerCameraImage(temporaryFilePath)
    }
}

private fun mergeSupportMessagesById(
    current: List<SessionApi.SupportMessage>,
    incoming: List<SessionApi.SupportMessage>
): List<SessionApi.SupportMessage> {
    val byId = linkedMapOf<Long, SessionApi.SupportMessage>()
    val withoutId = mutableListOf<SessionApi.SupportMessage>()
    (current + incoming).forEach { message ->
        val id = message.id
        if (id == null) {
            withoutId += message
        } else {
            val previous = byId[id]
            byId[id] = if (previous?.readByUserAt != null && message.readByUserAt == null) {
                message.copy(readByUserAt = previous.readByUserAt)
            } else {
                message
            }
        }
    }
    return (withoutId + byId.values).sortedWith(
        compareBy<SessionApi.SupportMessage> { it.createdAt ?: 0L }
            .thenBy { it.id ?: Long.MAX_VALUE }
    )
}

@Composable
internal fun HamburgerMenuSheet(
    visible: Boolean,
    userId: String,
    membershipEntitlement: SessionApi.EntitlementSnapshot?,
    membershipLoadState: MembershipLoadState,
    membershipPaymentState: MembershipPaymentState = MembershipPaymentState(),
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onRequestMembershipRefresh: () -> Unit,
    onStartMembershipPayment: (MembershipPaymentProduct) -> Unit,
    onClearChatHistory: () -> Unit,
    onPlaceholderClick: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var page by remember(visible) { mutableStateOf(HamburgerMenuPage.Menu) }
    var legalSubpage by remember(visible) { mutableStateOf(false) }
    var supportSummary by remember(userId) { mutableStateOf<SessionApi.SupportSummary?>(null) }
    var supportRefreshTick by remember(userId) { mutableStateOf(0) }
    var cachedSupportMessages by remember(userId) { mutableStateOf<List<SessionApi.SupportMessage>>(emptyList()) }
    var cachedTodayAgriHistoryCards by remember(userId) { mutableStateOf<List<SessionApi.TodayAgriCard>>(emptyList()) }
    var supportAttachmentMenuVisible by remember(visible) { mutableStateOf(false) }
    var supportAttachmentCloseRequest by remember(visible) { mutableStateOf(0) }
    var supportFeedbackSending by remember(visible) { mutableStateOf(false) }
    var mainLogoutDialogVisible by rememberSaveable(visible) { mutableStateOf(false) }
    var mainLogoutSubmitting by remember(visible) { mutableStateOf(false) }
    var updateChecking by remember(userId) { mutableStateOf(false) }
    var updateDialogInfo by remember(userId) { mutableStateOf<SessionApi.AppUpdateInfo?>(null) }
    var updateDownloading by remember(userId) { mutableStateOf(false) }
    var updateDownloadProgress by remember(userId) { mutableStateOf<AppUpdateInstaller.DownloadProgress?>(null) }
    var updateDownloadJob by remember(userId) { mutableStateOf<Job?>(null) }
    var pendingInstallPermissionUpdate by remember(userId) { mutableStateOf<SessionApi.AppUpdateInfo?>(null) }
    var pendingInstallAttemptUpdate by remember(userId) { mutableStateOf<SessionApi.AppUpdateInfo?>(null) }
    var settingsMainOpenLogged by remember(visible, userId) { mutableStateOf(false) }
    var accountManagementOpenLogged by remember(visible, userId) { mutableStateOf(false) }
    fun performButtonHaptic() {
        val handled = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!handled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    fun showNotice(text: String) {
        onPlaceholderClick(text)
    }
    fun checkAppUpdate(userTriggered: Boolean) {
        if (updateChecking || updateDownloading) return
        updateChecking = true
        SessionApi.reportClientLog(
            level = "info",
            event = "app_update.check_started",
            message = "App update check started",
            attrs = mapOf("user_triggered" to userTriggered)
        )
        if (userTriggered) {
            showNotice("正在检查更新...")
        }
        SessionApi.getAppUpdate { info ->
            updateChecking = false
            when {
                info == null -> {
                    if (userTriggered) {
                        showNotice("检查更新失败，请稍后重试")
                    }
                }
                info.usableUpdate -> {
                    SessionApi.reportClientLog(
                        level = "info",
                        event = "app_update.available",
                        message = "App update available",
                        attrs = appUpdateLogAttrs(info, userTriggered)
                    )
                    val latestVersionCode = info.latestVersionCode ?: 0
                    val shouldAutoPrompt =
                        userTriggered ||
                            latestVersionCode <= 0 ||
                            latestVersionCode > context.loadLastPromptedUpdateVersionCode()
                    if (shouldAutoPrompt) {
                        updateDialogInfo = info
                    } else if (userTriggered) {
                        updateDialogInfo = info
                    }
                }
                else -> {
                    if (userTriggered) {
                        SessionApi.reportClientLog(
                            level = "info",
                            event = "app_update.no_update",
                            message = "No app update available",
                            attrs = appUpdateLogAttrs(info, true)
                        )
                    }
                    if (userTriggered) {
                        showNotice("当前没有可用更新")
                    }
                }
            }
        }
    }
    fun dismissAppUpdateDialog(info: SessionApi.AppUpdateInfo) {
        info.latestVersionCode
            ?.takeIf { it > 0 }
            ?.let { context.saveLastPromptedUpdateVersionCode(it) }
        val activeDownload = updateDownloading || updateDownloadJob?.isActive == true
        if (activeDownload) {
            updateDownloadJob?.cancel()
            SessionApi.reportClientLog(
                level = "info",
                event = "app_update.download_cancelled",
                message = "App update download cancelled by user",
                attrs = appUpdateLogAttrs(info, true)
            )
            showNotice("已取消下载，可稍后再更新")
        }
        updateDownloading = false
        updateDownloadProgress = null
        updateDownloadJob = null
        pendingInstallPermissionUpdate = null
        updateDialogInfo = null
    }
    fun startAppUpdate(update: SessionApi.AppUpdateInfo) {
        if (updateDownloading) return
        val appContext = context.applicationContext
        if (!AppUpdateInstaller.canRequestInstallPackages(appContext)) {
            val opened = AppUpdateInstaller.openInstallPermissionSettings(appContext)
            pendingInstallPermissionUpdate = update.takeIf { opened }
            SessionApi.reportClientLog(
                level = "warn",
                event = "app_update.install_permission_required",
                message = "App update install permission required",
                attrs = appUpdateLogAttrs(update, true) + mapOf("settings_opened" to opened)
            )
            showNotice(if (opened) "允许安装更新包后返回继续" else "请先允许安装更新包")
            return
        }
        pendingInstallPermissionUpdate = null
        updateDownloading = true
        updateDownloadProgress = AppUpdateInstaller.DownloadProgress(
            downloadedBytes = 0L,
            totalBytes = update.fileSizeBytes
        )
        showNotice("正在下载更新...")
        val job = scope.launch {
            try {
                SessionApi.reportClientLog(
                    level = "info",
                    event = "app_update.download_started",
                    message = "App update download started",
                    attrs = appUpdateLogAttrs(update, true)
                )
                val download = AppUpdateInstaller.downloadApkDetailed(appContext, update) { progress ->
                    withContext(Dispatchers.Main) {
                        updateDownloadProgress = progress
                    }
                }
                updateDownloading = false
                updateDownloadJob = null
                val apkFile = download.file
                if (apkFile == null) {
                    updateDownloadProgress = null
                    SessionApi.reportClientLog(
                        level = "warn",
                        event = "app_update.download_failed",
                        message = "App update download failed",
                        attrs = appUpdateLogAttrs(update, true) + mapOf(
                            "reason" to (download.reason?.name ?: "unknown"),
                            "http_status" to (download.httpStatus ?: 0)
                        )
                    )
                    showNotice(appUpdateDownloadFailureText(download.reason))
                    return@launch
                }
                updateDownloadProgress = AppUpdateInstaller.DownloadProgress(
                    downloadedBytes = update.fileSizeBytes ?: apkFile.length(),
                    totalBytes = update.fileSizeBytes ?: apkFile.length()
                )
                updateDialogInfo = null
                updateDownloadProgress = null
                val install = AppUpdateInstaller.installApkDetailed(appContext, apkFile)
                if (!install.started) {
                    SessionApi.reportClientLog(
                        level = "warn",
                        event = "app_update.install_intent_failed",
                        message = "App update install intent failed",
                        attrs = appUpdateLogAttrs(update, true) + mapOf(
                            "reason" to (install.reason?.name ?: "unknown")
                        )
                    )
                    showNotice("系统安装页面打开失败，请稍后再试")
                } else {
                    pendingInstallAttemptUpdate = update
                    update.latestVersionCode
                        ?.takeIf { it > 0 }
                        ?.let {
                            context.savePendingInstallAttemptVersionCode(it)
                            context.saveLastPromptedUpdateVersionCode(it)
                        }
                    SessionApi.reportClientLog(
                        level = "info",
                        event = "app_update.install_started",
                        message = "App update install started",
                        attrs = appUpdateLogAttrs(update, true)
                    )
                }
            } catch (_: CancellationException) {
                updateDownloading = false
                updateDownloadProgress = null
                updateDownloadJob = null
            }
        }
        updateDownloadJob = job
    }
    fun reconcilePendingInstallAttempt(showIncompleteNotice: Boolean) {
        val inMemoryAttempt = pendingInstallAttemptUpdate
        val targetVersionCode = inMemoryAttempt
            ?.latestVersionCode
            ?.takeIf { it > 0 }
            ?: context.loadPendingInstallAttemptVersionCode()
        if (targetVersionCode <= 0) {
            if (inMemoryAttempt != null) {
                pendingInstallAttemptUpdate = null
            }
            return
        }
        val installedVersionCode = context.currentInstalledVersionCode()
        val attrs = (inMemoryAttempt?.let { appUpdateLogAttrs(it, true) } ?: mapOf(
            "latest_version_code" to targetVersionCode,
            "persisted_attempt" to true
        )) + mapOf("installed_version_code" to installedVersionCode)
        if (installedVersionCode >= targetVersionCode.toLong()) {
            pendingInstallAttemptUpdate = null
            context.clearPendingInstallAttemptVersionCode(targetVersionCode)
            context.saveLastPromptedUpdateVersionCode(targetVersionCode)
            SessionApi.reportClientLog(
                level = "info",
                event = "app_update.install_completed",
                message = "App update install completed",
                attrs = attrs
            )
        } else {
            pendingInstallAttemptUpdate = null
            context.clearPendingInstallAttemptVersionCode(targetVersionCode)
            context.clearLastPromptedUpdateVersionCode(targetVersionCode)
            SessionApi.reportClientLog(
                level = "warn",
                event = "app_update.install_not_completed",
                message = "App update install page returned without version change",
                attrs = attrs
            )
            if (showIncompleteNotice) {
                showNotice("更新未完成，可稍后继续安装")
            }
        }
    }
    DisposableEffect(lifecycleOwner, pendingInstallPermissionUpdate, pendingInstallAttemptUpdate, updateDownloading) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val pendingUpdate = pendingInstallPermissionUpdate
            if (pendingUpdate != null && !updateDownloading) {
                if (AppUpdateInstaller.canRequestInstallPackages(context.applicationContext)) {
                    pendingInstallPermissionUpdate = null
                    showNotice("已允许安装，继续下载更新...")
                    startAppUpdate(pendingUpdate)
                    return@LifecycleEventObserver
                }
                return@LifecycleEventObserver
            }
            reconcilePendingInstallAttempt(showIncompleteNotice = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(userId) {
        reconcilePendingInstallAttempt(showIncompleteNotice = false)
    }
    LaunchedEffect(visible, userId) {
        if (visible) {
            reconcilePendingInstallAttempt(showIncompleteNotice = false)
        }
    }
    fun handleBackClick() {
        if (page == HamburgerMenuPage.Support && supportFeedbackSending) {
            showNotice("正在发送，请稍后")
            return
        }
        if (page == HamburgerMenuPage.Support && supportAttachmentMenuVisible) {
            supportAttachmentCloseRequest += 1
            return
        }
        supportAttachmentMenuVisible = false
        if (legalSubpage && page.isLegalDetailPage()) {
            legalSubpage = false
            page = HamburgerMenuPage.LegalHub
            return
        }
        if (page != HamburgerMenuPage.Menu) {
            legalSubpage = false
            page = HamburgerMenuPage.Menu
        } else {
            onDismiss()
        }
    }
    fun openLegalDetail(target: HamburgerMenuPage) {
        legalSubpage = true
        page = target
    }
    fun finishAfterAuthSessionCleared() {
        supportAttachmentMenuVisible = false
        legalSubpage = false
        page = HamburgerMenuPage.Menu
        onDismiss()
    }
    LaunchedEffect(visible, page, supportSummary?.unreadCount) {
        if (!visible || page != HamburgerMenuPage.Menu || settingsMainOpenLogged) {
            return@LaunchedEffect
        }
        settingsMainOpenLogged = true
        SessionApi.reportClientLog(
            level = "info",
            event = "ui.settings_main_opened",
            message = "Settings main opened",
            attrs = mapOf(
                "logged_in" to (IdManager.getAuthPhoneMask() != null),
                "menu_row_count" to 8,
                "has_support_unread" to ((supportSummary?.unreadCount ?: 0) > 0)
            )
        )
    }
    LaunchedEffect(visible, page) {
        if (!visible || page != HamburgerMenuPage.Account || accountManagementOpenLogged) {
            return@LaunchedEffect
        }
        accountManagementOpenLogged = true
        SessionApi.reportClientLog(
            level = "info",
            event = "ui.account_management_opened",
            message = "Account management opened",
            attrs = mapOf(
                "logged_in" to (IdManager.getAuthPhoneMask() != null),
                "account_row_count" to 5
            )
        )
    }
    LaunchedEffect(visible, supportRefreshTick) {
        if (!visible) return@LaunchedEffect
        SessionApi.getSupportSummary { summary ->
            if (summary != null) {
                supportSummary = summary
            }
        }
    }
    LaunchedEffect(userId) {
        if (!SessionApi.hasBackendConfigured()) return@LaunchedEffect
        reconcilePendingInstallAttempt(showIncompleteNotice = false)
        delay(1200)
        checkAppUpdate(userTriggered = false)
    }
    BackHandler(enabled = visible) {
        handleBackClick()
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(durationMillis = HAMBURGER_PAGE_ENTER_MS)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(durationMillis = HAMBURGER_PAGE_EXIT_MS)
        ),
        modifier = modifier.fillMaxSize()
    ) {
        Surface(
            color = Color.White,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val isForwardNavigation = when {
                            initialState == HamburgerMenuPage.LegalHub && targetState.isLegalDetailPage() -> true
                            initialState.isLegalDetailPage() && targetState == HamburgerMenuPage.LegalHub -> false
                            targetState != HamburgerMenuPage.Menu -> true
                            else -> false
                        }
                        if (isForwardNavigation) {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(durationMillis = HAMBURGER_PAGE_ENTER_MS)
                            ) togetherWith slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(durationMillis = HAMBURGER_PAGE_EXIT_MS)
                            )
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { -it },
                                animationSpec = tween(durationMillis = HAMBURGER_PAGE_ENTER_MS)
                            ) togetherWith slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(durationMillis = HAMBURGER_PAGE_EXIT_MS)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "hamburger_menu_page"
                ) { currentPage ->
                    when (currentPage) {
                        HamburgerMenuPage.Menu -> {
                            HamburgerMenuMainPage(
                                supportUnread = (supportSummary?.unreadCount ?: 0) > 0,
                                onOpenMembership = {
                                    performButtonHaptic()
                                    onRequestMembershipRefresh()
                                    page = HamburgerMenuPage.Membership
                                },
                                onOpenAccount = {
                                    performButtonHaptic()
                                    page = HamburgerMenuPage.Account
                                },
                                onOpenRedeem = {
                                    performButtonHaptic()
                                    page = HamburgerMenuPage.Redeem
                                },
                                onOpenSupport = {
                                    performButtonHaptic()
                                    supportAttachmentMenuVisible = false
                                    page = HamburgerMenuPage.Support
                                },
                                onOpenTodayAgri = {
                                    performButtonHaptic()
                                    page = HamburgerMenuPage.TodayAgri
                                },
                                onOpenLegalHub = {
                                    performButtonHaptic()
                                    legalSubpage = false
                                    page = HamburgerMenuPage.LegalHub
                                },
                                onCheckUpdate = {
                                    performButtonHaptic()
                                    checkAppUpdate(userTriggered = true)
                                },
                                onLogoutClick = {
                                    performButtonHaptic()
                                    if (IdManager.getAuthPhoneMask() == null) {
                                        showNotice("请先登录")
                                    } else {
                                        mainLogoutDialogVisible = true
                                    }
                                },
                                onPlaceholderClick = ::showNotice
                            )
                        }
                        HamburgerMenuPage.Membership -> {
                            HamburgerMembershipCenterPage(
                                userId = userId,
                                entitlement = membershipEntitlement,
                                loadState = membershipLoadState,
                                paymentState = membershipPaymentState,
                                onRetryLoad = onRequestMembershipRefresh,
                                onStartPayment = { product ->
                                    performButtonHaptic()
                                    onStartMembershipPayment(product)
                                }
                            )
                        }
                        HamburgerMenuPage.Account -> {
                            HamburgerAccountManagementPage(
                                onPendingAction = ::showNotice,
                                onClearChatHistory = onClearChatHistory,
                                onAuthSessionCleared = ::finishAfterAuthSessionCleared
                            )
                        }
                        HamburgerMenuPage.Redeem -> {
                            HamburgerRedeemCodePage(
                                onPendingAction = ::showNotice,
                                onRedeemSuccess = onRequestMembershipRefresh
                            )
                        }
                        HamburgerMenuPage.Support -> {
                            HamburgerSupportFeedbackPage(
                                attachmentCloseRequest = supportAttachmentCloseRequest,
                                initialMessages = cachedSupportMessages,
                                onPendingAction = ::showNotice,
                                onConversationChanged = {
                                    supportRefreshTick += 1
                                },
                                onMessagesChanged = { messages ->
                                    cachedSupportMessages = messages
                                },
                                onSendingChanged = { sending ->
                                    supportFeedbackSending = sending
                                },
                                onAttachmentMenuVisibilityChanged = { visible ->
                                    supportAttachmentMenuVisible = visible
                                }
                            )
                        }
                        HamburgerMenuPage.TodayAgri -> {
                            HamburgerTodayAgriHistoryPage(
                                initialCards = cachedTodayAgriHistoryCards,
                                onPendingAction = ::showNotice,
                                onCardsChanged = { cards ->
                                    cachedTodayAgriHistoryCards = cards
                                }
                            )
                        }
                        HamburgerMenuPage.LegalHub -> {
                            HamburgerLegalHubPage(
                                onOpenUserAgreement = { openLegalDetail(HamburgerMenuPage.ServiceAgreement) },
                                onOpenPrivacyPolicy = { openLegalDetail(HamburgerMenuPage.PrivacyPolicy) },
                                onOpenThirdPartyList = { openLegalDetail(HamburgerMenuPage.ThirdPartyList) },
                                onOpenPersonalInfoList = { openLegalDetail(HamburgerMenuPage.PersonalInfoList) },
                                onOpenPermissionList = { openLegalDetail(HamburgerMenuPage.PermissionList) },
                                onOpenRiskNotice = { openLegalDetail(HamburgerMenuPage.RiskNotice) }
                            )
                        }
                        HamburgerMenuPage.ServiceAgreement -> {
                            HamburgerServiceAgreementPage()
                        }
                        HamburgerMenuPage.PrivacyPolicy -> {
                            HamburgerPrivacyPolicyPage()
                        }
                        HamburgerMenuPage.ThirdPartyList -> {
                            HamburgerThirdPartyListPage()
                        }
                        HamburgerMenuPage.PersonalInfoList -> {
                            HamburgerPersonalInfoListPage()
                        }
                        HamburgerMenuPage.PermissionList -> {
                            HamburgerPermissionListPage()
                        }
                        HamburgerMenuPage.RiskNotice -> {
                            HamburgerRiskNoticePage()
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.5.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .statusBarsPadding()
                        .padding(start = 18.dp, top = HamburgerBackButtonTopPadding)
                        .size(48.dp)
                        .semantics {
                            contentDescription =
                                if (page == HamburgerMenuPage.Menu) "关闭设置" else "返回设置"
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                performButtonHaptic()
                                handleBackClick()
                            }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        HamburgerBackIcon(
                            tint = Color(0xFF111111),
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            }
        }
    }
    if (mainLogoutDialogVisible) {
        HamburgerLogoutConfirmDialog(
            submitting = mainLogoutSubmitting,
            onDismiss = {
                if (!mainLogoutSubmitting) mainLogoutDialogVisible = false
            },
            onConfirm = {
                if (mainLogoutSubmitting) return@HamburgerLogoutConfirmDialog
                val accountScopeId = IdManager.getUserId()
                mainLogoutSubmitting = true
                SessionApi.logoutCurrentSession { result ->
                    mainLogoutSubmitting = false
                    if (result.localCleared) {
                        mainLogoutDialogVisible = false
                        PendingChatSendWorkScheduler.cancelAllForScope(context, accountScopeId)
                        showNotice(if (result.remoteConfirmed) "已退出当前设备" else "本机已退出，服务端吊销未确认")
                        finishAfterAuthSessionCleared()
                    } else {
                        showNotice("退出失败，请检查网络后重试")
                    }
                }
            }
        )
    }
    updateDialogInfo?.let { info ->
        HamburgerAppUpdateDialog(
            update = info,
            downloading = updateDownloading,
            downloadProgress = updateDownloadProgress,
            installPermissionPending = pendingInstallPermissionUpdate != null,
            onDismiss = {
                dismissAppUpdateDialog(info)
            },
            onInstall = {
                performButtonHaptic()
                startAppUpdate(info)
            }
        )
    }
}

@Composable
private fun HamburgerAppUpdateDialog(
    update: SessionApi.AppUpdateInfo,
    downloading: Boolean,
    downloadProgress: AppUpdateInstaller.DownloadProgress?,
    installPermissionPending: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    val latestName = update.latestVersionName?.takeIf { it.isNotBlank() }
    val latestCode = update.latestVersionCode
    val versionText = when {
        latestName != null && latestCode != null -> "版本 $latestName ($latestCode)"
        latestName != null -> "版本 $latestName"
        latestCode != null -> "版本 $latestCode"
        else -> "新版本"
    }
    val sizeText = formatAppUpdateSize(update.fileSizeBytes)
    val notes = update.releaseNotes?.trim().orEmpty()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                .padding(horizontal = 28.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            HamburgerAppUpdateCard(
                versionText = versionText,
                sizeText = sizeText,
                notes = notes,
                downloading = downloading,
                downloadProgress = downloadProgress,
                installPermissionPending = installPermissionPending,
                onDismiss = onDismiss,
                onInstall = onInstall,
                modifier = Modifier.widthIn(max = 340.dp)
            )
        }
    }
}

@Composable
private fun HamburgerAppUpdateCard(
    versionText: String,
    sizeText: String?,
    notes: String,
    downloading: Boolean,
    downloadProgress: AppUpdateInstaller.DownloadProgress?,
    installPermissionPending: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 18.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "发现新版本",
                color = Color(0xFF111111),
                fontSize = 20.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = listOfNotNull(versionText, sizeText).joinToString(" · "),
                color = Color(0xFF6D7178),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Text(
                text = notes.ifBlank { "优化使用体验。" },
                color = Color(0xFF222222),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            if (downloading) {
                val progressText = downloadProgress?.percent?.let { percent ->
                    "正在下载 $percent%"
                } ?: "正在下载更新包..."
                Text(
                    text = progressText,
                    color = Color(0xFF6D7178),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                AppUpdateDownloadProgressBar(
                    fraction = downloadProgress?.fraction ?: 0f,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "网络较慢时可以点“稍后”取消，之后再更新。",
                    color = Color(0xFF8A8F98),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            } else if (installPermissionPending) {
                Text(
                    text = "需允许安装更新包，返回后自动继续。",
                    color = Color(0xFF6D7178),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            } else {
                Text(
                    text = "将打开系统安装确认页。",
                    color = Color(0xFF6D7178),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color(0xFFF0F1F2),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "稍后",
                            color = Color(0xFF111111),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Surface(
                    color = if (downloading) Color(0xFFD6D8DC) else Color(0xFF111111),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(
                            enabled = !downloading,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onInstall
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (downloading) "下载中" else "立即更新",
                            color = if (downloading) Color(0xFF777B82) else Color.White,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateDownloadProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val progress = fraction.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier.heightIn(min = 6.dp, max = 6.dp)
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = Color(0xFFE8EAED),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = radius
        )
        if (progress > 0f) {
            drawRoundRect(
                color = Color(0xFF111111),
                topLeft = Offset.Zero,
                size = Size(width = size.width * progress, height = size.height),
                cornerRadius = radius
            )
        }
    }
}

@Composable
internal fun HamburgerAppUpdateDialogPreview(
    downloading: Boolean = false,
    installPermissionPending: Boolean = false
) {
    HamburgerAppUpdateCard(
        versionText = "版本 1.0.1 (2)",
        sizeText = "38.5MB",
        notes = "优化使用体验。",
        downloading = downloading,
        downloadProgress = if (downloading) {
            AppUpdateInstaller.DownloadProgress(
                downloadedBytes = 12L * 1024L * 1024L,
                totalBytes = 38L * 1024L * 1024L
            )
        } else {
            null
        },
        installPermissionPending = installPermissionPending,
        onDismiss = {},
        onInstall = {},
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
    )
}

private fun formatAppUpdateSize(bytes: Long?): String? {
    val value = bytes ?: return null
    if (value <= 0L) return null
    val mb = value / 1024.0 / 1024.0
    return String.format(Locale.US, "%.1fMB", mb)
}

private fun appUpdateLogAttrs(
    update: SessionApi.AppUpdateInfo,
    userTriggered: Boolean
): Map<String, Any?> =
    mapOf(
        "user_triggered" to userTriggered,
        "current_version_code" to (update.currentVersionCode ?: BuildConfig.VERSION_CODE),
        "latest_version_code" to (update.latestVersionCode ?: 0),
        "has_update" to (update.hasUpdate == true),
        "force_update" to (update.forceUpdate == true),
        "apk_configured" to !update.apkUrl.isNullOrBlank(),
        "sha_configured" to !update.apkSha256.isNullOrBlank(),
        "size_configured" to ((update.fileSizeBytes ?: 0L) > 0L)
    )

@Composable
private fun HamburgerMembershipCenterPage(
    userId: String,
    entitlement: SessionApi.EntitlementSnapshot?,
    loadState: MembershipLoadState,
    paymentState: MembershipPaymentState = MembershipPaymentState(),
    onRetryLoad: () -> Unit,
    onStartPayment: (MembershipPaymentProduct) -> Unit
) {
    HamburgerMembershipCenterContent(
        userId = userId,
        entitlement = entitlement,
        loadState = loadState,
        paymentState = paymentState,
        onRetryLoad = onRetryLoad,
        onStartPayment = onStartPayment,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 32.dp)
    )
}

private fun appUpdateDownloadFailureText(reason: AppUpdateInstaller.DownloadFailureReason?): String =
    when (reason) {
        AppUpdateInstaller.DownloadFailureReason.MissingReleaseMetadata -> "当前没有可用更新"
        AppUpdateInstaller.DownloadFailureReason.Sha256Mismatch,
        AppUpdateInstaller.DownloadFailureReason.PackageInfoMissing,
        AppUpdateInstaller.DownloadFailureReason.PackageNameMismatch,
        AppUpdateInstaller.DownloadFailureReason.PackageSignatureMissing,
        AppUpdateInstaller.DownloadFailureReason.PackageSignatureMismatch,
        AppUpdateInstaller.DownloadFailureReason.VersionCodeMismatch,
        AppUpdateInstaller.DownloadFailureReason.VersionCodeNotNewer -> "安装包校验未通过，请稍后再试"
        AppUpdateInstaller.DownloadFailureReason.NonHttpsRedirect,
        AppUpdateInstaller.DownloadFailureReason.InvalidUrl -> "更新地址异常，请稍后再试"
        AppUpdateInstaller.DownloadFailureReason.ExpectedSizeTooLarge,
        AppUpdateInstaller.DownloadFailureReason.ContentTooLarge,
        AppUpdateInstaller.DownloadFailureReason.ContentLengthMismatch,
        AppUpdateInstaller.DownloadFailureReason.CopyTooLarge,
        AppUpdateInstaller.DownloadFailureReason.DownloadedSizeMismatch -> "安装包大小异常，请稍后再试"
        AppUpdateInstaller.DownloadFailureReason.HttpStatus,
        AppUpdateInstaller.DownloadFailureReason.Network -> "网络不稳定，更新下载失败"
        AppUpdateInstaller.DownloadFailureReason.CacheDirUnavailable,
        AppUpdateInstaller.DownloadFailureReason.RenameFailed -> "本机缓存不可用，更新下载失败"
        else -> "更新下载失败，请稍后重试"
    }

private data class HamburgerLegalSection(
    val title: String,
    val body: String
)

@Composable
private fun HamburgerLegalTextPage(
    title: String,
    meta: String? = null,
    intro: String? = null,
    sections: List<HamburgerLegalSection>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "title") {
            HamburgerLegalPageTitle(title)
        }
        meta?.let { text ->
            item(key = "meta") {
                Text(
                    text = text,
                    color = Color(0xFF5F646D),
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            }
        }
        intro?.let { text ->
            item(key = "intro") {
                Text(
                    text = text,
                    color = Color(0xFF30343A),
                    fontSize = 14.5.sp,
                    lineHeight = 23.sp
                )
            }
        }
        itemsIndexed(
            items = sections,
            key = { index, section -> "section-$index-${section.title}" }
        ) { _, section ->
            HamburgerAgreementSection(
                title = section.title,
                body = section.body
            )
        }
    }
}

@Composable
private fun HamburgerMembershipCenterContent(
    userId: String,
    entitlement: SessionApi.EntitlementSnapshot?,
    loadState: MembershipLoadState,
    paymentState: MembershipPaymentState = MembershipPaymentState(),
    onRetryLoad: () -> Unit,
    onStartPayment: (MembershipPaymentProduct) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HamburgerMembershipTitle(userId = userId)
        MembershipCenterBody(
            entitlement = entitlement,
            loadState = loadState,
            paymentState = paymentState,
            onStartPayment = onStartPayment,
            onRetryLoad = onRetryLoad
        )
    }
}

@Composable
private fun HamburgerMembershipTitle(userId: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "会员中心",
            color = Color(0xFF111111),
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "（ID ${compactUserId(userId)}）",
            color = Color(0xFF8A8E96),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun HamburgerMenuMainPage(
    supportUnread: Boolean,
    onOpenMembership: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenRedeem: () -> Unit,
    onOpenSupport: () -> Unit,
    onOpenTodayAgri: () -> Unit,
    onOpenLegalHub: () -> Unit,
    onCheckUpdate: () -> Unit,
    onLogoutClick: () -> Unit,
    onPlaceholderClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = "设置",
                color = Color(0xFF111111),
                fontSize = 21.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
        HamburgerMenuGroup {
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Membership,
                title = "会员中心",
                onClick = onOpenMembership
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Account,
                title = "账号管理",
                onClick = onOpenAccount
            )
        }

        HamburgerMenuGroup {
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Feedback,
                title = "帮助与反馈",
                showBadge = supportUnread,
                onClick = onOpenSupport
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.TodayAgri,
                title = "今日农情",
                onClick = onOpenTodayAgri
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Update,
                title = "检查更新",
                showChevron = false,
                onClick = onCheckUpdate
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Redeem,
                title = "礼品卡",
                onClick = onOpenRedeem
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Document,
                title = "隐私与协议",
                onClick = onOpenLegalHub
            )
        }

        HamburgerMenuGroup {
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Logout,
                title = "退出登录",
                destructive = true,
                showChevron = false,
                onClick = onLogoutClick
            )
        }

        AppFilingFooter(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
    }
}

@Composable
private fun AppFilingFooter(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "App备案号：$APP_ICP_RECORD_NUMBER",
            color = Color(0xFF8B9098),
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HamburgerLegalHubPage(
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenThirdPartyList: () -> Unit,
    onOpenPersonalInfoList: () -> Unit,
    onOpenPermissionList: () -> Unit,
    onOpenRiskNotice: () -> Unit
) {
    HamburgerLegalHubContent(
        onOpenUserAgreement = onOpenUserAgreement,
        onOpenPrivacyPolicy = onOpenPrivacyPolicy,
        onOpenThirdPartyList = onOpenThirdPartyList,
        onOpenPersonalInfoList = onOpenPersonalInfoList,
        onOpenPermissionList = onOpenPermissionList,
        onOpenRiskNotice = onOpenRiskNotice,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 32.dp)
    )
}

@Composable
private fun HamburgerLegalHubContent(
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenThirdPartyList: () -> Unit,
    onOpenPersonalInfoList: () -> Unit,
    onOpenPermissionList: () -> Unit,
    onOpenRiskNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        HamburgerLegalPageTitle("隐私与协议")
        HamburgerAccountGroup(
            modifier = Modifier.padding(top = 22.dp)
        ) {
            HamburgerLegalHubRow("用户协议", onOpenUserAgreement)
            HamburgerMenuDivider()
            HamburgerLegalHubRow("隐私政策", onOpenPrivacyPolicy)
            HamburgerMenuDivider()
            HamburgerLegalHubRow("第三方信息共享清单", onOpenThirdPartyList)
            HamburgerMenuDivider()
            HamburgerLegalHubRow("个人信息收集清单", onOpenPersonalInfoList)
            HamburgerMenuDivider()
            HamburgerLegalHubRow("应用权限", onOpenPermissionList)
            HamburgerMenuDivider()
            HamburgerLegalHubRow("风险提示", onOpenRiskNotice)
        }
    }
}

@Composable
private fun HamburgerLegalHubRow(
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(0xFF111111),
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            modifier = Modifier.weight(1f)
        )
        HamburgerChevronIcon(
            tint = Color(0xFFAAAEB5),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
internal fun HamburgerLegalHubPagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerLegalHubContent(
            onOpenUserAgreement = {},
            onOpenPrivacyPolicy = {},
            onOpenThirdPartyList = {},
            onOpenPersonalInfoList = {},
            onOpenPermissionList = {},
            onOpenRiskNotice = {},
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun HamburgerServiceAgreementPage() {
    HamburgerServiceAgreementContent(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 34.dp)
    )
}

@Composable
internal fun HamburgerServiceAgreementContent(
    modifier: Modifier = Modifier
) {
    val sections = remember {
        listOf(
            HamburgerLegalSection(
                title = "一、服务内容与适用范围",
                body = "农技千查面向农业种植和作物管理场景，提供文字问答、图片辅助分析、历史记录、会员权益、帮助与反馈、今日农情、检查更新及 App 内明确展示的相关服务。具体功能、权益、价格、有效期和适用条件，以 App 页面和平台记录为准。"
            ),
            HamburgerLegalSection(
                title = "二、农业建议的使用边界",
                body = "AI 回复、今日农情，以及客服提供的农业技术说明仅供参考，不构成官方认定、检测报告、收益承诺或唯一处理方案。复制、截图、转发或对外使用相关内容前，请结合现场情况自行核验。涉及用药用肥、农资购买、质量争议、赔付或重大生产决策时，请以产品标签、官方信息、检测机构或线下专业意见为准。"
            ),
            HamburgerLegalSection(
                title = "三、您提交的内容与账号权益",
                body = "您提交的文字、图片、反馈和补充材料仍归您或原权利人所有。为提供服务、处理反馈、核对权益和保障安全，我们会在必要范围内处理相关内容。请确认您有权提交，并尽量避免上传与农业问题无关或涉及他人权益的敏感材料。"
            ),
            HamburgerLegalSection(
                title = "四、会员、支付、加油包和礼品卡",
                body = "Plus / Pro 会员按 30 天计算，为一次性购买，不自动续费；每日额度不结转，会员到期后按基础权益计算。加油包为额外问诊次数，未用完次数长期保留；Plus 升级 Pro 的实际应付金额、礼品卡权益和加油包规则，以 App 页面和平台记录为准。付费服务应通过 App 页面展示的官方渠道完成；未展示可购买状态的入口不会发起真实扣费。已付款但权益未到账时，请通过帮助与反馈联系客服；核实后优先补发权益，重复扣款、无法提供服务、依法或支付平台规则应退款的情形，按核验结果处理退款或其他售后。支付凭证或发票需求可通过帮助与反馈提交，我们会按实际经营和税务规则处理。"
            ),
            HamburgerLegalSection(
                title = "五、农资信息和购买提醒",
                body = "本服务可能展示农资标签解读、查询路径、使用注意事项或市场信息整理等内容。相关信息仅供参考，不构成购买建议、效果保证或质量承诺。您在 App 外购买农资时，请自行核对经营主体、产品资质、适用作物、票据凭证和当地法规。"
            ),
            HamburgerLegalSection(
                title = "六、使用规范、责任和协议更新",
                body = "请您在合法、真实、合理的范围内使用本服务，不得侵犯他人权益或影响平台正常运行。我们可能根据产品功能、法律法规或运营需要更新本协议，重要变化会通过 App 内页面、弹窗或其他合理方式提示。如有疑问，可通过邮箱 nongjiqiancha@foxmail.com 联系我们。"
            )
        )
    }
    HamburgerLegalTextPage(
        title = "用户协议",
        meta = "更新日期：2026年6月28日\n生效日期：2026年6月28日\n服务提供者：北京农技千问科技有限公司\n联系邮箱：nongjiqiancha@foxmail.com\nApp备案号：$APP_ICP_RECORD_NUMBER",
        sections = sections,
        modifier = modifier,
    )
}

@Composable
private fun HamburgerAgreementSection(
    title: String,
    body: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            color = Color(0xFF111111),
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            color = Color(0xFF30343A),
            fontSize = 14.5.sp,
            lineHeight = 23.sp
        )
    }
}

@Composable
internal fun HamburgerServiceAgreementPagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerServiceAgreementContent(
            modifier = Modifier
                .padding(14.dp)
                .heightIn(max = 560.dp)
        )
    }
}

@Composable
private fun HamburgerPrivacyPolicyPage() {
    HamburgerPrivacyPolicyContent(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 34.dp)
    )
}

@Composable
internal fun HamburgerPrivacyPolicyContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HamburgerLegalPageTitle("隐私政策")
        Text(
            text = "更新日期：2026年6月28日\n生效日期：2026年6月28日\n服务提供者：北京农技千问科技有限公司\n联系邮箱：nongjiqiancha@foxmail.com\nApp备案号：$APP_ICP_RECORD_NUMBER",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、处理原则",
            body = "我们遵循合法、正当、必要和诚信原则处理个人信息，仅在实现农技千查功能、履行协议、保障安全、处理投诉争议和依法合规所需范围内使用相关信息。非必要信息由您自主选择是否提供；缺少必要信息时，相关功能可能无法正常使用。"
        )
        HamburgerAgreementSection(
            title = "二、基础运行和登录",
            body = "为维持服务运行、区分账号、恢复历史和保障权益，我们会处理必要的账号、登录、设备、网络、版本、运行状态和安全诊断信息。手机号用于验证码登录、账号识别和权益核对；App 不读取通讯录、短信内容或通话记录。"
        )
        HamburgerAgreementSection(
            title = "三、问诊、图片和反馈",
            body = "为提供农业技术参考、历史恢复和客服处理，我们会处理您主动提交的文字、图片、反馈内容、必要上下文、回复结果和服务状态。授权粗略定位后，App 仅用于生成地区文本辅助判断农时和地区背景；不向农技千查服务器上传经纬度，不保存轨迹，系统定位和地理编码能力按设备系统规则处理。请避免上传与农业问题无关的身份证件、银行卡、完整人脸、他人隐私或商业秘密。"
        )
        HamburgerAgreementSection(
            title = "四、会员、支付、礼品卡和更新",
            body = "会员、加油包、礼品卡、订单、退款、权益生效和检查更新会处理完成对应功能所需的信息。付费服务仅通过 App 页面展示的官方渠道发起；未展示可购买状态的入口不会发起真实扣费。"
        )
        HamburgerAgreementSection(
            title = "五、权限和系统能力",
            body = "App 会在功能需要时使用网络访问、网络状态、定位、后台待发送和安装更新相关系统能力。照片入口使用系统照片选择器，拍照入口调用系统或外部相机；拍照成功后可能在系统相册另存一份照片，便于您找回现场图片。当前不申请相册 / 存储读写、录音、通讯录、短信、电话状态、Wi-Fi 状态或通知权限，也不做 App 外推送通知。"
        )
        HamburgerAgreementSection(
            title = "六、第三方服务",
            body = "我们可能通过平台服务器调用云计算、数据存储、智能分析、短信、支付宝支付等服务，用于系统运行、验证码登录、问诊分析、图片理解、今日农情、故障排查、权益核对和付费服务处理。相关服务只在实现对应功能所必需的范围内处理必要信息。当前不接入广告、地图、推送或统计类第三方 SDK。"
        )
        HamburgerAgreementSection(
            title = "七、结构化清单",
            body = "为便于您和应用市场审核人员查阅，App 内“隐私与协议”目录提供第三方信息共享清单、个人信息收集清单、应用权限和风险提示。您可以在这些页面查看对应功能场景、涉及信息、权限边界、第三方服务和 AI 建议风险。"
        )
        HamburgerAgreementSection(
            title = "八、保存、安全和共享",
            body = "我们按实现服务、权益核对、安全保障、争议处理和依法合规所需的期限保存信息。问诊图片、反馈图片、问答归档、App 自动日志和运行安全日志会按较短周期或必要期限清理；连续问诊所需的短期窗口和长期记忆会保留至您删除历史对话、账号注销处理、达到业务目的或依法依规应处理时；会员、订单、礼品卡、反馈和账号记录按功能、争议处理和法定要求所需期限保存。达到目的后，我们会删除、匿名化或按规则处理。我们不会出售您的个人信息；除依法依规、取得授权、实现服务必要委托处理、处理投诉争议或保护安全外，不会向无关第三方提供您的个人信息。"
        )
        HamburgerAgreementSection(
            title = "九、您的权利",
            body = "您可以通过 App 内功能、帮助与反馈或联系邮箱 nongjiqiancha@foxmail.com，要求查询、复制、更正、删除相关信息，撤回授权，咨询账号注销或投诉处理方式。提交账号注销申请后，我们通常在 15 个工作日内完成核验和处理流程，并对依法可删除或匿名化的信息作相应处理；交易核验、安全风控、争议处理等必要记录会按规则保留或去标识化。删除历史对话用于删除问诊聊天历史和长期记忆，不等于完整账号注销，也不会删除会员、礼品卡和反馈记录。"
        )
        HamburgerAgreementSection(
            title = "十、未成年人保护",
            body = "本服务主要面向具备农业生产、种植管理或相关咨询需求的用户。未成年人应在监护人指导下使用；未满十四周岁儿童提交个人信息前，应取得监护人同意。请不要主动上传未成年人照片、身份信息、联系方式或其他与农业问题无关的敏感内容。"
        )
        HamburgerAgreementSection(
            title = "十一、政策更新",
            body = "我们可能根据产品变化、法律法规、权限清单、合作服务或运营需要更新本政策。涉及处理目的、处理方式、个人信息种类或重要权利义务变化时，会通过 App 内页面、弹窗或其他合理方式提示，并在需要时重新取得您的授权。"
        )
    }
}

@Composable
private fun HamburgerRiskNoticePage() {
    HamburgerRiskNoticeContent(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 34.dp)
    )
}

@Composable
private fun HamburgerRiskNoticeContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HamburgerLegalPageTitle("风险提示")
        Text(
            text = "更新日期：2026年6月28日\n生效日期：2026年6月28日",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        Text(
            text = "农技千查提供农业技术参考，不能替代现场诊断、官方认定、检测结论、农资质量承诺或线下专业服务。涉及用药用肥、农资购买、重大损失、赔付争议或安全风险，建议结合线下情况复核。",
            color = Color(0xFF30343A),
            fontSize = 14.5.sp,
            lineHeight = 23.sp
        )
        HamburgerAgreementSection(
            title = "一、AI 仅供参考",
            body = "病虫害、药害、肥害、缺素、冻害、旱涝、根系问题和生理障碍可能表现相似。AI 回答可能有偏差、遗漏或误判，也不保证防效、产量、收益或避免损失。"
        )
        HamburgerAgreementSection(
            title = "二、图片不等于现场",
            body = "图片只反映拍摄瞬间和局部。病斑、叶背、根系、果实、虫体、整株和田间环境没有拍清时，不建议仅凭图片直接做大面积用药、毁苗、停水停肥、索赔或投诉等决定。"
        )
        HamburgerAgreementSection(
            title = "三、药肥农资看标签",
            body = "涉及农药、肥料、调节剂、种子、基质、设备等农资，请以产品标签、登记信息、质量证明、当地法规、安全间隔期和线下农技人员意见为准。不建议仅凭 AI 建议超范围、超剂量、混配或在不适宜天气下使用；购买农资时请核对经营主体、产品资质、适用作物和票据凭证。"
        )
        HamburgerAgreementSection(
            title = "四、官方和时效信息以最新发布为准",
            body = "检疫、补贴、备案、登记、审定、证件真伪、价格、天气、预警、质量争议、赔付和行政流程，AI 只能提供一般说明或查询路径，请以官方平台和当地主管部门最新信息为准。"
        )
        HamburgerAgreementSection(
            title = "五、权益和支付风险",
            body = "会员、加油包、礼品卡和支付相关权益，以 App 页面、平台记录、订单记录、支付渠道结果和法律规定为准。涉及 App 外私下收款、代充、代兑换、非官方客服或包治包赔承诺时，请谨慎核实。"
        )
        HamburgerAgreementSection(
            title = "六、未成年人需监护",
            body = "未成年人应在监护人指导下使用。请尽量避免上传未成年人照片、身份信息、联系方式或其他无关敏感内容；未满十四周岁提交个人信息应取得监护人同意。"
        )
        HamburgerAgreementSection(
            title = "七、紧急情况优先线下处理",
            body = "发生大面积突发病害、药害、灾害、食品安全、人身安全或重大财产风险时，请优先联系当地农业农村部门、植保站、应急或监管机构以及线下专业人员。AI 回复仅作辅助参考，请以现场专业处理为先。"
        )
    }
}

@Composable
private fun HamburgerLegalPageTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFF111111),
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 3,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 48.dp, end = 48.dp, top = 14.dp)
    )
}

@Composable
internal fun HamburgerPrivacyPolicyPagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerPrivacyPolicyContent(
            modifier = Modifier
                .padding(14.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun HamburgerThirdPartyListPage() {
    HamburgerThirdPartyListContent(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 34.dp)
    )
}

@Composable
private fun HamburgerThirdPartyListContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HamburgerLegalPageTitle("第三方信息共享清单")
        Text(
            text = "更新日期：2026年6月28日",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、基本原则",
            body = "我们不会出售您的个人信息。除依法依规、取得授权、实现服务必要委托处理、处理投诉争议或保护安全外，不会向无关第三方提供您的个人信息。我们会按 SDK、API 接口、云服务托管、系统能力等类型列明共享情况；如第三方服务新增或变更，我们会更新本清单。第三方具体处理规则以其公开隐私政策或与我们的服务约定为准。"
        )
        HamburgerAgreementSection(
            title = "二、智能分析和云服务",
            body = "共享类型：服务端 API 接口 / 云服务委托处理。\n服务提供方：阿里云计算有限公司及其关联服务平台。\n服务类别：服务器侧云计算、数据存储和智能分析服务。\n使用场景：农业问答、图片理解、记忆摘要、今日农情、历史恢复、故障排查和权益核对。\n涉及信息：您主动提交的文字、图片、地区文本、必要上下文、AI 回复、账号标识、运行状态和安全诊断信息。\n共享方式：由农技千查服务器在必要范围内调用服务接口，Android 端不直接连接第三方智能分析服务。\n第三方处理规则：阿里云法律声明及隐私权政策，以及对应云产品公开规则或我们与其签署的服务约定。"
        )
        HamburgerAgreementSection(
            title = "三、云资源和存储",
            body = "共享类型：云服务托管 / 服务端访问控制。\n服务提供方：阿里云计算有限公司及其关联服务平台。\n服务类别：云数据库、对象存储、缓存、日志和 HTTPS 基础设施。\n使用场景：保存账号、会话、图片、会员权益、礼品卡、帮助反馈、检查更新、后台审计和安全诊断信息。\n涉及信息：完成对应功能所需的账号标识、图片文件、反馈内容、订单 / 权益状态、版本和运行状态。\n共享方式：由平台服务器托管和访问控制，按规则留存、清理或去标识化。\n第三方处理规则：阿里云法律声明及隐私权政策，以及对应云产品公开规则或我们与其签署的服务约定。"
        )
        HamburgerAgreementSection(
            title = "四、手机号登录和短信服务",
            body = "共享类型：服务端 API 接口。\n服务提供方：阿里云计算有限公司及其短信服务平台。\n服务类别：第三方短信验证码服务。\n使用场景：发送登录验证码、校验账号登录请求、保障账号安全。\n涉及信息：手机号、验证码发送状态、发送时间和必要的安全风控信息。\n共享方式：由农技千查服务器调用短信服务接口发送验证码；验证码需要您手动输入，App 不读取短信内容、通讯录或通话记录。\n第三方处理规则：阿里云法律声明及隐私权政策，以及短信服务公开规则或我们与其签署的服务约定。\n拒绝影响：拒绝提供手机号将无法完成账号登录。"
        )
        HamburgerAgreementSection(
            title = "五、系统能力",
            body = "共享类型：系统能力调用 / 用户主动授权。\n服务提供方：Android 系统、系统照片选择器、您设备上的外部相机或系统安装确认页。\n服务类别：Android 系统能力或您设备上的外部相机应用。\n使用场景：选择照片、拍照、打开系统浏览器、下载后调起系统安装确认页。\n涉及信息：您主动选择或拍摄的图片、系统安装确认状态和必要的本地文件授权。\n共享方式：仅在您主动点击对应功能时由系统处理；这些能力不是 App 内嵌的广告、统计或推送 SDK。"
        )
        HamburgerAgreementSection(
            title = "六、支付服务",
            body = "共享类型：支付宝 APP 支付 SDK / 支付服务接口。\nSDK 名称：App 支付宝客户端 SDK。\nSDK 包名：com.alipay.sdk。\n当前接入版本：15.8.42。\n服务提供方：支付宝（中国）网络技术有限公司及支付宝开放平台相关服务。\n服务类别：支付渠道服务。\n使用场景：您主动购买会员、加油包或其他付费服务时，调起支付宝完成支付。\n涉及信息：订单号、服务 / 权益信息、支付金额、支付状态、退款和售后处理所需信息，以及支付宝 SDK 完成支付所需的设备和支付环境必要信息。\n共享方式：由农技千查后端创建订单并生成支付参数，Android 端仅将支付参数交给支付宝 SDK 调起支付；权益发放以后端验签通知或订单状态核验为准，客户端同步结果不直接发放权益。\n第三方处理规则：支付宝隐私权政策及支付宝开放平台公开规则。"
        )
        HamburgerAgreementSection(
            title = "七、当前未接入的第三方 SDK",
            body = "当前版本不接入广告、地图、推送、统计、社交分享、通讯录、短信读取、电话状态或 Wi-Fi 状态类第三方 SDK。支付宝 SDK 仅在您主动购买付费服务时调用；基础开源组件和 Android 系统组件不作为独立第三方收集个人信息，相关数据处理仍以本政策和 App 内清单为准。"
        )
    }
}

@Composable
internal fun HamburgerThirdPartyListPagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerThirdPartyListContent(
            modifier = Modifier
                .padding(14.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun HamburgerPersonalInfoListPage() {
    HamburgerPersonalInfoListContent(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 34.dp)
    )
}

@Composable
private fun HamburgerPersonalInfoListContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HamburgerLegalPageTitle("个人信息收集清单")
        Text(
            text = "更新日期：2026年6月28日",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、基础运行",
            body = "信息类型：账号标识、登录状态、设备基础信息、网络状态、App 版本、运行状态和安全诊断信息。\n使用目的：维持服务运行、区分账号、恢复历史、保障权益和排查故障。\n使用场景：启动、登录、接口请求、崩溃补报、后台排障和安全风控。\n收集方式：App 自动生成或服务器在请求过程中记录。\n处理方式：由 App 和平台服务器在必要范围内收集、传输、存储、查询和清理。\n拒绝影响：基础运行信息是在线服务必要信息；无法提供时，登录、问诊、反馈或权益核对可能无法正常使用。"
        )
        HamburgerAgreementSection(
            title = "二、AI 问诊",
            body = "信息类型：您主动提交的文字、图片、必要历史上下文、地区文本、AI 回复、记忆摘要和服务状态。\n使用目的：生成农业技术参考建议、支持连续问诊、恢复历史和改进问题处理。\n使用场景：文字问诊、图片问诊、图文混合问诊、失败重试和历史恢复。\n收集方式：由您主动输入、选择图片或授权粗略定位后生成地区文本。\n处理方式：由 App 上传至平台服务器，服务器组装上下文并调用智能分析服务；授权粗略定位后仅生成地区文本，不向农技千查服务器上传经纬度，不保存轨迹，系统定位和地理编码能力按设备系统规则处理。\n拒绝影响：您可以不上传图片或不授权定位；不授权定位时仍可问诊，但地区背景可能不够准确。"
        )
        HamburgerAgreementSection(
            title = "三、图片上传",
            body = "信息类型：您主动选择或拍摄的图片、图片上传状态、图片访问路径和本地缩略图缓存。\n使用目的：图片分析、历史展示、失败重试、帮助与反馈附件和客服排障。\n使用场景：问诊上传图片、帮助反馈发图、全屏预览和后台待发送。\n收集方式：由您主动选择照片或主动拍照后提供。\n处理方式：图片经平台服务器上传和读取；拍照成功后系统可能在相册 Pictures/农技千查 另存一份照片，便于您找回现场图片。\n拒绝影响：不提供图片时仍可文字问诊，但图片识别和图片反馈功能无法使用。"
        )
        HamburgerAgreementSection(
            title = "四、会员、支付、礼品卡和更新",
            body = "信息类型：会员档位、额度、加油包、礼品卡兑换状态、订单 / 权益状态、版本号、安装包校验信息和更新结果。\n使用目的：核对权益、处理兑换、展示更新、校验安装包和处理售后争议。\n使用场景：会员中心、礼品卡兑换、检查更新、主动购买付费服务后的订单和退款处理。\n收集方式：由您主动兑换、主动检查更新、主动下单，或由服务器根据权益变化生成。\n处理方式：由平台服务器记录和核对；支付由支付宝等页面展示的官方渠道处理，权益发放以后端验签通知或订单状态核验为准。\n拒绝影响：不提供礼品卡码或订单必要信息时，将无法兑换权益或完成购买 / 售后处理。"
        )
        HamburgerAgreementSection(
            title = "五、帮助与反馈",
            body = "信息类型：您提交的反馈文字、图片、发送时间、已读状态、客服回复、处理状态和必要账号线索。\n使用目的：站内沟通、问题排查、客服回复、争议处理和服务改进。\n使用场景：提交问题、补充截图、查看客服回复、后台客服处理和状态追踪。\n收集方式：由您主动提交，或由客服在后台回复和处理时生成。\n处理方式：由 App 和平台服务器传输、保存、按账号隔离展示，并按规则访问控制和清理。\n拒绝影响：不提交反馈不影响主问诊，但我们可能无法定位或处理您的具体问题。"
        )
        HamburgerAgreementSection(
            title = "六、账号注销和权利请求",
            body = "信息类型：注销申请、处理状态、账号核验结果和依法需要保留或去标识化的权益 / 交易 / 安全记录。\n使用目的：核验身份、处理注销、响应查询 / 复制 / 更正 / 删除 / 撤回授权等请求。\n使用场景：账号管理、帮助与反馈、邮件联系和后台线下处理。\n收集方式：由您在账号管理、反馈或邮件中主动提出请求。\n处理方式：提交申请后通常在 15 个工作日内完成核验和处理流程；删除历史对话不等于完整账号注销。\n拒绝影响：不提交相关请求不影响继续使用；提交注销并完成处理后，相关账号服务将停止。"
        )
    }
}

@Composable
internal fun HamburgerPersonalInfoListPagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerPersonalInfoListContent(
            modifier = Modifier
                .padding(14.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun HamburgerPermissionListPage() {
    HamburgerPermissionListContent(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 34.dp)
    )
}

@Composable
private fun HamburgerPermissionListContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HamburgerLegalPageTitle("应用权限")
        Text(
            text = "更新日期：2026年6月28日",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、网络访问",
            body = "权限 / 能力：INTERNET。\n调用场景：连接平台服务、短信验证码登录、上传图片、流式回答、会员权益、帮助与反馈、今日农情和检查更新。\n调用时机：您使用在线功能或 App 需要同步服务状态时。\n拒绝 / 关闭影响：没有网络访问时，在线问诊、登录、反馈和更新等功能无法正常使用。"
        )
        HamburgerAgreementSection(
            title = "二、网络状态",
            body = "权限 / 能力：ACCESS_NETWORK_STATE。\n调用场景：判断网络是否可用，在登录、上传、问诊、反馈和检查更新失败时给出提示。\n调用时机：发起联网功能前或网络请求失败后。\n拒绝 / 关闭影响：可能无法准确提示网络状态，但不影响您在系统层面管理网络连接。\n边界：当前不申请 Wi-Fi 状态权限，不读取 Wi-Fi 列表。"
        )
        HamburgerAgreementSection(
            title = "三、安装更新包",
            body = "权限 / 能力：REQUEST_INSTALL_PACKAGES。\n调用场景：仅在您主动点击“立即更新”后，下载官方安装包并调起系统安装确认页。\n调用时机：您主动检查更新并确认安装时。\n拒绝 / 关闭影响：您可以继续使用当前版本，但无法通过 App 内自有更新入口安装新版本。\n边界：App 不做静默安装，不后台自动安装，也不从非官方地址安装更新包。"
        )
        HamburgerAgreementSection(
            title = "四、后台待发送任务",
            body = "权限 / 能力：WAKE_LOCK、RECEIVE_BOOT_COMPLETED、FOREGROUND_SERVICE 和应用私有广播保护权限。\n调用场景：带图消息在您离开 App、进程被系统回收或设备重启后，对同一条待发送任务进行有限重试。\n调用时机：仅存在您已主动发送但尚未完成的带图待发送任务时。\n拒绝 / 关闭影响：系统可能延后或终止待发送任务，您需要重新打开 App 后重试。\n边界：该能力不用于 App 外推送、广告通知、定位跟踪、读取通讯录或读取短信。"
        )
        HamburgerAgreementSection(
            title = "五、相机和照片",
            body = "权限 / 能力：系统照片选择器、外部相机和 FileProvider 临时文件授权。\n调用场景：您主动选择图片或拍照后用于问诊、帮助反馈和预览。\n调用时机：您点击相机或照片入口时。\n拒绝 / 关闭影响：无法使用图片问诊或图片反馈，但仍可文字问诊。\n边界：当前不申请 App 相机权限，不申请相册 / 存储读写权限；照片入口只访问您本次主动选择的图片。拍照成功后，系统可能在相册 Pictures/农技千查 另存一份照片。"
        )
        HamburgerAgreementSection(
            title = "六、定位和未申请权限",
            body = "权限 / 能力：ACCESS_COARSE_LOCATION。\n调用场景：仅在问诊需要时申请粗略定位，用于生成省、市、区县等地区文本，辅助判断农时和地区背景。\n调用时机：首次发送问诊或后续发送问诊前需要刷新地区文本时；不会在 App 首屏无场景弹出权限框。\n拒绝 / 关闭影响：仍可文字或图片问诊，但回答会缺少您所在地区的农时背景，可能不够贴合当地情况。\n边界：不向农技千查服务器上传经纬度，不保存轨迹，系统定位和地理编码能力按设备系统规则处理；不用于地图、广告、统计或推送。当前不申请相册 / 存储读写、录音、通讯录、短信、电话状态、Wi-Fi 状态或通知权限，也不做 App 外推送通知。"
        )
    }
}

@Composable
internal fun HamburgerPermissionListPagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerPermissionListContent(
            modifier = Modifier
                .padding(14.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
internal fun HamburgerRiskNoticePagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerRiskNoticeContent(
            modifier = Modifier
                .padding(14.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
internal fun HamburgerMembershipCenterPagePreview(userId: String) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 560.dp, max = 680.dp)
        ) {
            HamburgerMembershipCenterPage(
                userId = userId,
                entitlement = SessionApi.EntitlementSnapshot(
                    tier = "plus",
                    tierExpireAt = System.currentTimeMillis() + 24L * 24L * 60L * 60L * 1000L,
                    dailyRemaining = 18,
                    topupRemaining = 0,
                    membershipSource = "gift_card",
                    giftCardRedeemedAt = System.currentTimeMillis() - 6L * 24L * 60L * 60L * 1000L
                ),
                loadState = MembershipLoadState.Loaded,
                onRetryLoad = {},
                onStartPayment = {}
            )
        }
    }
}

@Composable
internal fun HamburgerMenuSheetPreview(userId: String) {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HamburgerMenuPreviewGroups()
            AppFilingFooter(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun HamburgerMenuShellPreview(userId: String) {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 560.dp, max = 680.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 2.5.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = HamburgerBackButtonTopPadding)
                    .size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    HamburgerBackIcon(
                        tint = Color(0xFF111111),
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
            Text(
                text = "设置",
                color = Color(0xFF111111),
                fontSize = 21.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 84.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HamburgerMenuPreviewGroups()
                AppFilingFooter(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HamburgerMenuPreviewGroups() {
    HamburgerMenuGroup {
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Membership,
                title = "会员中心",
                onClick = {}
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Account,
                title = "账号管理",
                onClick = {}
            )
    }
    HamburgerMenuGroup {
        HamburgerMenuRow(
            icon = HamburgerMenuIcon.Feedback,
            title = "帮助与反馈",
            showBadge = true,
            onClick = {}
        )
        HamburgerMenuDivider()
        HamburgerMenuRow(
            icon = HamburgerMenuIcon.TodayAgri,
            title = "今日农情",
            onClick = {}
        )
        HamburgerMenuDivider()
        HamburgerMenuRow(
            icon = HamburgerMenuIcon.Update,
            title = "检查更新",
            showChevron = false,
            onClick = {}
        )
        HamburgerMenuDivider()
        HamburgerMenuRow(
            icon = HamburgerMenuIcon.Redeem,
            title = "礼品卡",
            onClick = {}
        )
        HamburgerMenuDivider()
        HamburgerMenuRow(
            icon = HamburgerMenuIcon.Document,
            title = "隐私与协议",
            onClick = {}
        )
    }
    HamburgerMenuGroup {
        HamburgerMenuRow(
            icon = HamburgerMenuIcon.Logout,
            title = "退出登录",
            destructive = true,
            showChevron = false,
            onClick = {}
        )
    }
}

@Composable
private fun HamburgerAccountManagementPage(
    onPendingAction: (String) -> Unit,
    onClearChatHistory: () -> Unit,
    onAuthSessionCleared: () -> Unit
) {
    HamburgerAccountManagementContent(
        onPendingAction = onPendingAction,
        onClearChatHistory = onClearChatHistory,
        onAuthSessionCleared = onAuthSessionCleared,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 32.dp)
    )
}

@Composable
private fun HamburgerAccountManagementContent(
    onPendingAction: (String) -> Unit,
    onClearChatHistory: () -> Unit,
    onAuthSessionCleared: () -> Unit,
    modifier: Modifier = Modifier
) {
    var deleteHistoryDialogVisible by rememberSaveable { mutableStateOf(false) }
    var logoutDialogVisible by rememberSaveable { mutableStateOf(false) }
    var accountDeletionDialogVisible by rememberSaveable { mutableStateOf(false) }
    var deleteHistorySubmitting by remember { mutableStateOf(false) }
    var accountDeletionSubmitting by remember { mutableStateOf(false) }
    var cacheCleanupSubmitting by remember { mutableStateOf(false) }
    var logoutSubmitting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val phoneMask = IdManager.getAuthPhoneMask()

    Column(modifier = modifier) {
        Text(
            text = "账号管理",
            color = Color(0xFF111111),
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(top = 14.dp)
        )

        Text(
            text = "账户",
            color = Color(0xFF8A8E96),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 18.dp, top = 28.dp, bottom = 10.dp)
        )

        HamburgerAccountGroup {
            HamburgerAccountInfoRow(
                title = "手机号",
                value = phoneMask ?: "未登录"
            )
        }

        HamburgerAccountGroup(
            modifier = Modifier.padding(top = 22.dp)
        ) {
            HamburgerAccountActionRow(
                title = if (cacheCleanupSubmitting) "清理中" else "清理临时缓存",
                danger = false,
                onClick = {
                    if (cacheCleanupSubmitting) return@HamburgerAccountActionRow
                    cacheCleanupSubmitting = true
                    val appContext = context.applicationContext
                    scope.launch {
                        val ok = LocalAppCacheCleaner.clearTemporaryCaches(appContext)
                        cacheCleanupSubmitting = false
                        onPendingAction(if (ok) "临时文件已清理" else "清理失败，请稍后重试")
                    }
                }
            )
            HamburgerMenuDivider()
            HamburgerAccountActionRow(
                title = "删除历史对话",
                onClick = { deleteHistoryDialogVisible = true }
            )
            HamburgerMenuDivider()
            HamburgerAccountActionRow(
                title = if (logoutSubmitting) "退出中" else "退出登录",
                onClick = {
                    if (phoneMask == null) {
                        onPendingAction("请先登录")
                        return@HamburgerAccountActionRow
                    }
                    if (logoutSubmitting) return@HamburgerAccountActionRow
                    logoutDialogVisible = true
                }
            )
        }

        HamburgerAccountGroup(
            modifier = Modifier.padding(top = 18.dp)
        ) {
            HamburgerAccountActionRow(
                title = if (accountDeletionSubmitting) "提交中" else "申请注销账号",
                enabled = phoneMask != null && !accountDeletionSubmitting,
                danger = true,
                trailingText = if (phoneMask == null) "请先登录" else null,
                onClick = { accountDeletionDialogVisible = true }
            )
        }
    }

    if (deleteHistoryDialogVisible) {
        HamburgerDeleteHistoryConfirmDialog(
            deleting = deleteHistorySubmitting,
            onDismiss = {
                if (!deleteHistorySubmitting) deleteHistoryDialogVisible = false
            },
            onConfirm = {
                if (deleteHistorySubmitting) return@HamburgerDeleteHistoryConfirmDialog
                deleteHistorySubmitting = true
                SessionApi.clearSessionHistory { result ->
                    deleteHistorySubmitting = false
                    when (result) {
                        SessionApi.ClearSessionHistoryResult.Success -> {
                            deleteHistoryDialogVisible = false
                            onClearChatHistory()
                            onPendingAction("历史对话已删除")
                        }
                        SessionApi.ClearSessionHistoryResult.ActiveStream -> {
                            onPendingAction("当前有回复生成中，稍后再删除")
                        }
                        SessionApi.ClearSessionHistoryResult.Failure -> {
                            onPendingAction("删除失败，请检查网络后重试")
                        }
                    }
                }
            }
        )
    }
    if (logoutDialogVisible) {
        HamburgerLogoutConfirmDialog(
            submitting = logoutSubmitting,
            onDismiss = {
                if (!logoutSubmitting) logoutDialogVisible = false
            },
            onConfirm = {
                if (logoutSubmitting) return@HamburgerLogoutConfirmDialog
                val accountScopeId = IdManager.getUserId()
                logoutSubmitting = true
                SessionApi.logoutCurrentSession { result ->
                    logoutSubmitting = false
                    if (result.localCleared) {
                        logoutDialogVisible = false
                        PendingChatSendWorkScheduler.cancelAllForScope(context, accountScopeId)
                        onPendingAction(if (result.remoteConfirmed) "已退出登录" else "本机已退出，服务端吊销未确认")
                        onAuthSessionCleared()
                    } else {
                        onPendingAction("退出失败，请检查网络后重试")
                    }
                }
            }
        )
    }
    if (accountDeletionDialogVisible) {
        HamburgerAccountDeletionConfirmDialog(
            submitting = accountDeletionSubmitting,
            onDismiss = {
                if (!accountDeletionSubmitting) accountDeletionDialogVisible = false
            },
            onConfirm = {
                if (accountDeletionSubmitting) return@HamburgerAccountDeletionConfirmDialog
                val accountScopeId = IdManager.getUserId()
                accountDeletionSubmitting = true
                SessionApi.requestAccountDeletion { ok ->
                    accountDeletionSubmitting = false
                    if (ok) {
                        accountDeletionDialogVisible = false
                        PendingChatSendWorkScheduler.cancelAllForScope(context, accountScopeId)
                        onPendingAction("注销申请已提交，已退出当前账号")
                        onAuthSessionCleared()
                    } else {
                        onPendingAction("提交失败，请检查网络后重试")
                    }
                }
            }
        )
    }
}

@Composable
internal fun HamburgerAccountManagementPagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerAccountManagementContent(
            onPendingAction = {},
            onClearChatHistory = {},
            onAuthSessionCleared = {},
            modifier = Modifier
                .padding(14.dp)
                .heightIn(max = 560.dp)
        )
    }
}

@Composable
internal fun HamburgerTodayAgriHistoryPagePreview(loadFailed: Boolean = false) {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        HamburgerTodayAgriHistoryContent(
            cards = if (loadFailed) emptyList() else listOf(
                uiCopyPreviewTodayAgriCard(),
                uiCopyPreviewTodayAgriLongSummaryCard().copy(dateCn = "20260612")
            ),
            loading = false,
            loadFailed = loadFailed,
            onRetry = {},
            modifier = Modifier
                .padding(14.dp)
                .heightIn(min = 520.dp, max = 650.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
private fun HamburgerTodayAgriHistoryPage(
    initialCards: List<SessionApi.TodayAgriCard> = emptyList(),
    onPendingAction: (String) -> Unit,
    onCardsChanged: (List<SessionApi.TodayAgriCard>) -> Unit = {}
) {
    var cards by remember { mutableStateOf(initialCards) }
    var loading by remember { mutableStateOf(initialCards.isEmpty()) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadTick by remember { mutableStateOf(0) }

    LaunchedEffect(initialCards) {
        if (cards.isEmpty() && initialCards.isNotEmpty()) {
            cards = initialCards
            loading = false
        }
    }
    LaunchedEffect(cards) {
        onCardsChanged(cards)
    }

    LaunchedEffect(loadTick) {
        loading = cards.isEmpty()
        loadFailed = false
        SessionApi.getRecentTodayAgriCards { loaded ->
            loading = false
            if (loaded == null) {
                loadFailed = true
                onPendingAction("农情同步失败")
                return@getRecentTodayAgriCards
            }
            loadFailed = false
            cards = loaded
        }
    }

    HamburgerTodayAgriHistoryContent(
        cards = cards,
        loading = loading,
        loadFailed = loadFailed,
        onRetry = { loadTick += 1 },
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 32.dp)
    )
}

@Composable
private fun HamburgerTodayAgriHistoryContent(
    cards: List<SessionApi.TodayAgriCard>,
    loading: Boolean,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(top = 14.dp)
        ) {
            Text(
                text = "今日农情",
                color = Color(0xFF111111),
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "近30天种植、农资和市场简报",
                color = Color(0xFF7D828A),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
        }

        if (cards.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (loadFailed) {
                    HamburgerSupportStatusText(text = "当前显示上次同步内容，稍后可重试")
                }
                cards.forEachIndexed { index, card ->
                    HamburgerTodayAgriHistoryDaySection(
                        card = card,
                        topPadding = if (index > 0) 1.dp else 0.dp
                    )
                }
            }
        } else {
            Surface(
                color = Color(0xFFF5F6F8),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when {
                        loading -> {
                            HamburgerSupportStatusText(text = "正在同步农情...")
                        }
                        loadFailed -> {
                            HamburgerSupportStatusText(text = "农情同步失败")
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(0.8.dp, Color(0xFFE1E4E8)),
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onRetry
                                    )
                            ) {
                                Text(
                                    text = "重试",
                                    color = Color(0xFF111111),
                                    fontSize = 14.sp,
                                    lineHeight = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                                )
                            }
                        }
                        else -> {
                            HamburgerTodayAgriEmptyState()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HamburgerTodayAgriHistoryDaySection(
    card: SessionApi.TodayAgriCard,
    topPadding: Dp = 0.dp
) {
    val dateText = hamburgerTodayAgriDateText(card.dateCn).ifBlank { "日期未明" }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(0.8.dp, Color(0xFFE4E6EA)),
            modifier = Modifier.fillMaxWidth()
        ) {
            HamburgerTodayAgriHistoryCard(
                card = card,
                dateText = dateText,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun HamburgerTodayAgriEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 12.dp)
    ) {
        Surface(
            color = Color.White,
            shape = CircleShape,
            border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
        ) {
            HamburgerMenuGlyph(
                icon = HamburgerMenuIcon.TodayAgri,
                tint = Color(0xFF111111),
                modifier = Modifier
                    .size(42.dp)
                    .padding(9.dp)
            )
        }
        Text(
            text = "暂无可展示的农情简报",
            color = Color(0xFF4B5158),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "有可用简报后会在这里留存",
            color = Color(0xFF858A91),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HamburgerTodayAgriHistoryCard(
    card: SessionApi.TodayAgriCard,
    dateText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = dateText,
                color = Color(0xFF7C8188),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "今日农情",
                color = Color(0xFF111111),
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        HorizontalDivider(thickness = 0.7.dp, color = Color(0xFFE8EAEE))
        card.items.orEmpty().take(3).forEachIndexed { index, item ->
            HamburgerTodayAgriHistoryItem(item = item, index = index)
        }
    }
}

@Composable
private fun HamburgerTodayAgriHistoryItem(
    item: SessionApi.TodayAgriCardItem,
    index: Int
) {
    val title = item.title.orEmpty().trim()
    val summary = item.summary.orEmpty().trim()
    val source = item.source.orEmpty().trim()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (title.isNotEmpty()) {
            Text(
                text = hamburgerTodayAgriItemTitle(index, title),
                color = Color(0xFF111111),
                fontSize = 15.5.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            text = summary,
            color = Color(0xFF4F535A),
            fontSize = 13.5.sp,
            lineHeight = 20.sp
        )
        if (source.isNotEmpty()) {
            Text(
                text = "来源：$source",
                color = Color(0xFF868B91),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun hamburgerTodayAgriItemTitle(index: Int, title: String): String {
    val prefix = when (index) {
        0 -> "一、"
        1 -> "二、"
        2 -> "三、"
        else -> "${index + 1}. "
    }
    return "$prefix$title"
}

@Composable
private fun HamburgerSupportFeedbackPage(
    attachmentCloseRequest: Int = 0,
    initialMessages: List<SessionApi.SupportMessage> = emptyList(),
    onPendingAction: (String) -> Unit,
    onConversationChanged: () -> Unit,
    onMessagesChanged: (List<SessionApi.SupportMessage>) -> Unit = {},
    onSendingChanged: (Boolean) -> Unit = {},
    onAttachmentMenuVisibilityChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf(initialMessages) }
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(initialMessages.isEmpty()) }
    var loadFailed by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sendingHint by remember { mutableStateOf<String?>(null) }
    var loadTick by remember { mutableStateOf(0) }
    var attachmentMenuVisible by remember { mutableStateOf(false) }
    val selectedImages = remember { mutableStateListOf<ComposerImageAttachment>() }
    var pendingSupportClientMsgId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSupportPayloadFingerprint by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSupportUploadedImageFingerprint by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSupportUploadedImageUrls by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraImageGalleryBacked by rememberSaveable { mutableStateOf(false) }
    var pendingCameraImageTemporaryFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(sending) {
        onSendingChanged(sending)
    }

    DisposableEffect(Unit) {
        onDispose {
            onSendingChanged(false)
            val contextForCleanup = context.applicationContext
            val imagesForCleanup = selectedImages.toList()
            val pendingCameraUri = pendingCameraImageUriString
            val pendingCameraGalleryBacked = pendingCameraImageGalleryBacked
            val pendingCameraTemporaryFilePath = pendingCameraImageTemporaryFilePath
            if (imagesForCleanup.isNotEmpty() || pendingCameraUri != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    imagesForCleanup.forEach(contextForCleanup::deleteComposerImageAttachment)
                    contextForCleanup.cleanupPendingComposerCameraImage(
                        uriString = pendingCameraUri,
                        galleryBacked = pendingCameraGalleryBacked,
                        temporaryFilePath = pendingCameraTemporaryFilePath
                    )
                }
            }
        }
    }

    BackHandler(enabled = attachmentMenuVisible) {
        attachmentMenuVisible = false
    }
    LaunchedEffect(attachmentMenuVisible) {
        onAttachmentMenuVisibilityChanged(attachmentMenuVisible)
    }
    LaunchedEffect(attachmentCloseRequest) {
        if (attachmentCloseRequest > 0) {
            attachmentMenuVisible = false
        }
    }
    LaunchedEffect(initialMessages) {
        if (messages.isEmpty() && initialMessages.isNotEmpty()) {
            messages = initialMessages
            loading = false
        }
    }
    LaunchedEffect(messages) {
        onMessagesChanged(messages)
    }

    fun addSupportImageUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remainingSlots = 4 - selectedImages.size
        if (remainingSlots <= 0) {
            onPendingAction("最多4张图片")
            return
        }
        attachmentMenuVisible = false
        val selectedUris = uris.take(remainingSlots)
        scope.launch {
            val importedImages = withContext(Dispatchers.IO) {
                selectedUris.mapNotNull { uri ->
                    context.importComposerImageToPrivateStorage(uri)
                }
            }
            if (importedImages.isEmpty()) {
                onPendingAction(ImageUploader.DECODE_FAIL_MESSAGE)
                return@launch
            }
            val latestRemainingSlots = 4 - selectedImages.size
            if (latestRemainingSlots <= 0) {
                withContext(Dispatchers.IO) {
                    importedImages.forEach(context::deleteComposerImageAttachment)
                }
                onPendingAction("最多4张图片")
                return@launch
            }
            val imagesToAdd = importedImages.take(latestRemainingSlots)
            val overflowImages = importedImages.drop(latestRemainingSlots)
            if (overflowImages.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    overflowImages.forEach(context::deleteComposerImageAttachment)
                }
            }
            selectedImages.addAll(imagesToAdd)
            if (overflowImages.isNotEmpty() || uris.size > remainingSlots) {
                onPendingAction("最多4张图片")
            }
        }
    }

    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) addSupportImageUris(listOf(uri))
    }
    val photoPickerTwoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(2)
    ) { uris ->
        addSupportImageUris(uris)
    }
    val photoPickerThreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        addSupportImageUris(uris)
    }
    val photoPickerFourLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        addSupportImageUris(uris)
    }
    val imageFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        addSupportImageUris(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = pendingCameraImageUriString?.let(Uri::parse)
        val galleryBacked = pendingCameraImageGalleryBacked
        val temporaryFilePath = pendingCameraImageTemporaryFilePath
        pendingCameraImageUriString = null
        pendingCameraImageGalleryBacked = false
        pendingCameraImageTemporaryFilePath = null
        if (uri != null) {
            context.revokeComposerCameraUri(uri)
        }
        val success = result.resultCode == Activity.RESULT_OK
        if (success && uri != null) {
            scope.launch {
                val importedImage = withContext(Dispatchers.IO) {
                    val imported = context.importComposerImageToPrivateStorage(uri)
                    if (imported != null) {
                        if (galleryBacked) {
                            if (!context.publishGalleryComposerCameraImage(uri)) {
                                context.deleteGalleryComposerCameraImage(uri)
                            }
                        } else {
                            context.saveComposerCameraImageToGallery(uri)
                        }
                    } else if (galleryBacked) {
                        context.deleteGalleryComposerCameraImage(uri)
                    }
                    if (!galleryBacked) {
                        deleteTemporaryComposerCameraImage(temporaryFilePath)
                    }
                    imported
                }
                if (importedImage == null) {
                    onPendingAction(ImageUploader.DECODE_FAIL_MESSAGE)
                    return@launch
                }
                val latestRemainingSlots = 4 - selectedImages.size
                if (latestRemainingSlots <= 0) {
                    withContext(Dispatchers.IO) {
                        context.deleteComposerImageAttachment(importedImage)
                    }
                    onPendingAction("最多4张图片")
                    return@launch
                }
                selectedImages.add(importedImage)
            }
        } else if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    if (galleryBacked) {
                        context.deleteGalleryComposerCameraImage(uri)
                    } else {
                        deleteTemporaryComposerCameraImage(temporaryFilePath)
                    }
                }
            }
        }
    }

    fun launchSupportImageFilePicker() {
        val remainingSlots = 4 - selectedImages.size
        if (remainingSlots <= 0) {
            onPendingAction("最多4张图片")
            return
        }
        attachmentMenuVisible = false
        imageFilePickerLauncher.launch(arrayOf("image/*"))
    }

    fun launchSupportPhotoPicker() {
        val remainingSlots = 4 - selectedImages.size
        if (remainingSlots <= 0) {
            onPendingAction("最多4张图片")
            return
        }
        attachmentMenuVisible = false
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        when (remainingSlots) {
            1 -> singlePhotoPickerLauncher.launch(request)
            2 -> photoPickerTwoLauncher.launch(request)
            3 -> photoPickerThreeLauncher.launch(request)
            else -> photoPickerFourLauncher.launch(request)
        }
    }

    fun launchSupportCamera() {
        if (selectedImages.size >= 4) {
            onPendingAction("最多4张图片")
            return
        }
        val target = context.createComposerCameraImageTarget()
        if (target == null) {
            onPendingAction(CAMERA_OPEN_FAILED_HINT_TEXT)
            return
        }
        pendingCameraImageUriString = target.uri.toString()
        pendingCameraImageGalleryBacked = target.galleryBacked
        pendingCameraImageTemporaryFilePath = target.temporaryFilePath
        attachmentMenuVisible = false
        val cameraIntent = buildComposerCameraIntent(target.uri)
        context.grantComposerCameraUri(target.uri, cameraIntent)
        runCatching {
            cameraLauncher.launch(cameraIntent)
        }.onFailure {
            context.revokeComposerCameraUri(target.uri)
            pendingCameraImageUriString = null
            pendingCameraImageGalleryBacked = false
            pendingCameraImageTemporaryFilePath = null
            scope.launch {
                withContext(Dispatchers.IO) {
                    if (target.galleryBacked) {
                        context.deleteGalleryComposerCameraImage(target.uri)
                    } else {
                        deleteTemporaryComposerCameraImage(target.temporaryFilePath)
                    }
                }
            }
            onPendingAction(CAMERA_OPEN_FAILED_HINT_TEXT)
        }
    }

    suspend fun uploadSupportImagesForSend(images: List<ComposerImageAttachment>): Pair<List<String>?, String?> =
        withContext(Dispatchers.IO) {
            if (images.isEmpty()) return@withContext emptyList<String>() to null
            val uploadBytes = mutableListOf<ByteArray>()
            for (image in images) {
                val bytes = context.readImageBytes(Uri.parse(image.uri))
                    ?: return@withContext null to ImageUploader.DECODE_FAIL_MESSAGE
                val bytesForUpload = if (ImageUploader.canUseOriginalJpegForUpload(bytes)) {
                    bytes
                } else {
                    ImageUploader.compressImage(bytes)?.bytes
                        ?: return@withContext null to ImageUploader.DECODE_FAIL_MESSAGE
                }
                uploadBytes.add(bytesForUpload)
            }
            val result = ImageUploader.uploadImagesWithResult(
                uploadBytes,
                purpose = ImageUploader.UploadPurpose.Support
            )
            if (result.urls == null) {
                null to (result.errorMessage ?: "图片上传失败，请稍后再试")
            } else {
                result.urls to null
            }
        }

    LaunchedEffect(loadTick) {
        loading = messages.isEmpty()
        loadFailed = false
        SessionApi.getSupportMessages { loaded ->
            loading = false
            if (loaded == null) {
                if (messages.isEmpty()) {
                    loadFailed = true
                } else {
                    onPendingAction("消息同步失败")
                }
                return@getSupportMessages
            }
            loadFailed = false
            messages = mergeSupportMessagesById(messages, loaded)
            val lastSeenMessageId = loaded
                .asSequence()
                .filter { it.senderType == "admin" || it.senderType == "system" }
                .mapNotNull { it.id }
                .maxOrNull()
            if (lastSeenMessageId == null) {
                onConversationChanged()
                return@getSupportMessages
            }
            SessionApi.markSupportRead(lastSeenMessageId = lastSeenMessageId) {
                if (it) {
                    messages = messages.map { message ->
                        val messageId = message.id
                        if (message.senderType == "user" ||
                            message.readByUserAt != null ||
                            messageId == null ||
                            messageId > lastSeenMessageId
                        ) {
                            message
                        } else {
                            message.copy(readByUserAt = System.currentTimeMillis())
                        }
                    }
                }
                onConversationChanged()
            }
        }
    }

    fun markAutoReplyRead(autoReplyMessageId: Long, attempt: Int = 0) {
        SessionApi.markSupportRead(lastSeenMessageId = autoReplyMessageId) { marked ->
            if (marked) {
                messages = messages.map { message ->
                    if (message.id == autoReplyMessageId) {
                        message.copy(readByUserAt = System.currentTimeMillis())
                    } else {
                        message
                    }
                }
                onConversationChanged()
                return@markSupportRead
            }
            if (attempt == 0) {
                scope.launch {
                    delay(600)
                    markAutoReplyRead(autoReplyMessageId, attempt = 1)
                }
            } else {
                onConversationChanged()
            }
        }
    }

    fun sendMessage() {
        val submittedText = inputValue.text
        val body = inputValue.text.trim()
        val imageSnapshot = selectedImages.take(4)
        if ((body.isEmpty() && imageSnapshot.isEmpty()) || sending) return
        if (!SessionApi.hasBackendConfigured()) {
            onPendingAction(SUPPORT_SEND_FAILED_HINT)
            return
        }
        if (inputValue.text.supportMessageCharCount() > SUPPORT_MESSAGE_MAX_CHARS) {
            onPendingAction("最多输入2000字")
            return
        }
        val payloadFingerprint = supportFeedbackPayloadFingerprint(body, imageSnapshot)
        val shouldStartNewPendingSupportMessage =
            pendingSupportClientMsgId.isNullOrBlank() ||
            pendingSupportPayloadFingerprint != payloadFingerprint
        val clientMsgId = if (shouldStartNewPendingSupportMessage) {
            "support-${UUID.randomUUID()}".also {
                pendingSupportClientMsgId = it
                pendingSupportPayloadFingerprint = payloadFingerprint
                pendingSupportUploadedImageFingerprint = null
                pendingSupportUploadedImageUrls = null
            }
        } else {
            pendingSupportClientMsgId.orEmpty()
        }
        attachmentMenuVisible = false
        sending = true
        onSendingChanged(true)
        sendingHint = if (imageSnapshot.isNotEmpty()) "正在上传图片..." else "正在提交反馈..."
        scope.launch {
            val cachedImageUrls = pendingSupportUploadedImageUrls
                ?.takeIf { pendingSupportUploadedImageFingerprint == payloadFingerprint }
                ?.lines()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
            val (imageUrls, uploadError) = if (cachedImageUrls != null) {
                cachedImageUrls to null
            } else {
                uploadSupportImagesForSend(imageSnapshot).also { (uploadedUrls, _) ->
                    if (uploadedUrls != null && imageSnapshot.isNotEmpty()) {
                        pendingSupportUploadedImageFingerprint = payloadFingerprint
                        pendingSupportUploadedImageUrls = uploadedUrls.joinToString("\n")
                    }
                }
            }
            if (imageUrls == null) {
                sending = false
                val errorText = uploadError ?: "图片上传失败，请稍后再试"
                sendingHint = errorText
                onPendingAction(errorText)
                return@launch
            }
            sendingHint = "正在提交反馈..."
            SessionApi.sendSupportMessage(body = body, images = imageUrls, clientMsgId = clientMsgId) { nullableResult ->
                sending = false
                val result = nullableResult ?: run {
                    sendingHint = SUPPORT_SEND_FAILED_HINT
                    onPendingAction(SUPPORT_SEND_FAILED_HINT)
                    return@sendSupportMessage
                }
                result.errorMessage?.takeIf { it.isNotBlank() }?.let { errorText ->
                    sendingHint = errorText
                    onPendingAction(errorText)
                    return@sendSupportMessage
                }
                val sent = result.message ?: run {
                    sendingHint = SUPPORT_SEND_FAILED_HINT
                    onPendingAction(SUPPORT_SEND_FAILED_HINT)
                    return@sendSupportMessage
                }
                sendingHint = null
                pendingSupportClientMsgId = null
                pendingSupportPayloadFingerprint = null
                pendingSupportUploadedImageFingerprint = null
                pendingSupportUploadedImageUrls = null
                if (inputValue.text == submittedText) {
                    inputValue = TextFieldValue("")
                }
                selectedImages.removeAll(imageSnapshot.toSet())
                scope.launch(Dispatchers.IO) {
                    imageSnapshot.forEach(context::deleteComposerImageAttachment)
                }
                loadFailed = false
                val visibleMessages = result.visibleMessages
                messages = mergeSupportMessagesById(messages, visibleMessages)
                val autoReplyMessageId = result.autoReply?.id
                if (autoReplyMessageId == null) {
                    onConversationChanged()
                    return@sendSupportMessage
                }
                markAutoReplyRead(autoReplyMessageId)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        HamburgerSupportFeedbackContent(
            messages = messages,
            inputValue = inputValue,
            selectedImages = selectedImages,
            loading = loading,
            loadFailed = loadFailed,
            sending = sending,
            sendingHint = sendingHint,
            onInputChange = { next ->
                if (!sending && sendingHint != null) {
                    sendingHint = null
                }
                if (next.text.supportMessageCharCount() <= SUPPORT_MESSAGE_MAX_CHARS) {
                    inputValue = next
                } else {
                    val clipped = next.text.takeSupportMessageChars(SUPPORT_MESSAGE_MAX_CHARS)
                    inputValue = next.copy(
                        text = clipped,
                        selection = TextRange(clipped.length)
                    )
                    onPendingAction("最多输入2000字")
                }
            },
            onAddClick = {
                if (!sending) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val shouldShowAttachmentMenu = !attachmentMenuVisible
                    attachmentMenuVisible = shouldShowAttachmentMenu
                    if (shouldShowAttachmentMenu) {
                        focusManager.clearFocus(force = true)
                    }
                } else {
                    onPendingAction("正在发送，请稍后")
                }
            },
            onRemoveImage = { image ->
                if (!sending) {
                    selectedImages.remove(image)
                    scope.launch(Dispatchers.IO) {
                        context.deleteComposerImageAttachment(image)
                    }
                } else {
                    onPendingAction("正在发送，请稍后")
                }
            },
            onSend = ::sendMessage,
            onRetry = { loadTick += 1 },
            onStatusHint = onPendingAction,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 18.dp)
        )
        ComposerAttachmentBottomSheet(
            visible = attachmentMenuVisible,
            limitReached = selectedImages.size >= 4,
            limitHintText = "最多4张图片",
            supportingHintText = null,
            modifier = Modifier.fillMaxSize(),
            onDismiss = { attachmentMenuVisible = false },
            onCameraClick = ::launchSupportCamera,
            onPhotoClick = ::launchSupportPhotoPicker,
            onFileClick = ::launchSupportImageFilePicker
        )
    }
}

@Composable
private fun HamburgerSupportFeedbackContent(
    messages: List<SessionApi.SupportMessage>,
    inputValue: TextFieldValue,
    selectedImages: List<ComposerImageAttachment>,
    loading: Boolean,
    loadFailed: Boolean,
    sending: Boolean,
    sendingHint: String?,
    onInputChange: (TextFieldValue) -> Unit,
    onAddClick: () -> Unit,
    onRemoveImage: (ComposerImageAttachment) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onStatusHint: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = messages.lastIndex.coerceAtLeast(0)
    )
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val actionCircleSize = if (screenWidth < 360.dp) 44.dp else 48.dp
    val addIconSize = if (screenWidth < 360.dp) 26.dp else 28.dp
    val inputText = inputValue.text
    val hasContent = inputText.trim().isNotEmpty() || selectedImages.isNotEmpty()
    val canSend = hasContent && !sending && inputText.supportMessageCharCount() <= SUPPORT_MESSAGE_MAX_CHARS
    var inputFocused by remember { mutableStateOf(false) }
    var previousMessageCount by remember { mutableStateOf(messages.size) }
    var didInitialMessageScroll by remember { mutableStateOf(messages.isNotEmpty()) }

    LaunchedEffect(messages.size, loading, loadFailed) {
        val oldCount = previousMessageCount
        val nearBottom = oldCount <= 1 ||
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= oldCount - 2 } != false
        val shouldInitialScroll = !didInitialMessageScroll && messages.isNotEmpty()
        previousMessageCount = messages.size
        val shouldAutoScroll = when {
            shouldInitialScroll -> true
            loading || loadFailed || messages.isEmpty() -> false
            oldCount == 0 -> true
            messages.size > oldCount && nearBottom -> true
            else -> false
        }
        if (shouldAutoScroll) {
            delay(80)
            if (shouldInitialScroll || oldCount == 0) {
                listState.scrollToItem(messages.lastIndex)
            } else {
                listState.animateScrollToItem(messages.lastIndex)
            }
            if (shouldInitialScroll) {
                didInitialMessageScroll = true
            }
        }
    }

    Column(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(top = 14.dp)
        ) {
            Text(
                text = "帮助与反馈",
                color = Color(0xFF111111),
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "提交使用问题、账号问题和故障截图，并查看处理进度",
                color = Color(0xFF7D828A),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
        }

        Surface(
            color = Color(0xFFF5F6F8),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 16.dp)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
            ) {
                when {
                    messages.isNotEmpty() -> {
                        if (loadFailed) {
                            item(key = "stale-sync-warning") {
                                HamburgerSupportInlineSyncWarning(onRetry = onRetry)
                            }
                        }
                        itemsIndexed(
                            items = messages,
                            key = { index, message ->
                                message.id?.let { "message-$it" }
                                    ?: "local-$index-${message.createdAt}-${message.senderType}"
                            }
                        ) { _, message ->
                            HamburgerSupportMessageBubble(
                                message = message,
                                onStatusHint = onStatusHint
                            )
                        }
                    }
                    loading -> {
                        item(key = "loading") {
                            HamburgerSupportStatusText(text = "正在读取反馈记录...")
                        }
                    }
                    loadFailed -> {
                        item(key = "failed") {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                HamburgerSupportStatusText(text = "反馈记录加载失败")
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFFE1E4E8)),
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onRetry
                                    )
                                ) {
                                    Text(
                                        text = "重试",
                                        color = Color(0xFF111111),
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    messages.isEmpty() -> {
                        item(key = "empty") {
                            HamburgerSupportEmptyState()
                        }
                    }
                }
            }
        }

        if (sendingHint != null) {
            Text(
                text = sendingHint,
                color = Color(0xFF4E5661),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        ComposerChromeRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (sendingHint != null) 8.dp else 12.dp),
            addButtonSize = actionCircleSize,
            addIconSize = addIconSize,
            sendButtonSize = actionCircleSize,
            inputChromeSurface = Color.White,
            inputChromeBorder = Color(0xFFE1E4E8),
            inputFieldSurface = Color.White,
            inputFieldBorder = Color(0xFFE1E4E8),
            inputBarHeight = 96.dp,
            inputBarMaxHeight = 214.dp,
            onAddClick = onAddClick,
            attachmentsContent = if (selectedImages.isNotEmpty()) {
                {
                    ComposerImagePreviewStrip(
                        images = selectedImages,
                        onRemoveImage = onRemoveImage
                    )
                }
            } else {
                null
            },
            inputContent = {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = if (selectedImages.isNotEmpty()) 86.dp else 22.dp, max = 132.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = "描述问题",
                            color = Color(0xFFAEAFB4),
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        )
                    }
                    BasicTextField(
                        value = inputValue,
                        onValueChange = onInputChange,
                        singleLine = false,
                        minLines = 1,
                        maxLines = 6,
                        textStyle = TextStyle(
                            color = Color(0xFF111111),
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (canSend) onSend()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 22.dp)
                            .onFocusChanged { inputFocused = it.isFocused }
                    )
                }
            },
            sendButtonEnabled = canSend,
            sendButtonBackgroundColor = if (canSend) Color(0xFF111111) else Color(0xFFD3D4D6),
            sendButtonTint = if (canSend) Color.White else Color(0xFF7F8083),
            onSendClick = {
                if (canSend) onSend()
            }
        )
    }
}

@Composable
private fun HamburgerSupportEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 12.dp)
    ) {
        Text(
            text = "提交问题后，处理进展会在这里持续更新",
            color = Color(0xFF4B5158),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "提交后可继续补充说明和截图",
            color = Color(0xFF858A91),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HamburgerSupportInlineSyncWarning(onRetry: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE1E4E8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "当前显示上次同步内容",
                color = Color(0xFF4E5661),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = Color(0xFF111111),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRetry
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "重试",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun HamburgerSupportStatusText(text: String) {
    Text(
        text = text,
        color = Color(0xFF737780),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    )
}

@Composable
private fun HamburgerSupportMessageBubble(
    message: SessionApi.SupportMessage,
    onStatusHint: (String) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isUser = message.senderType == "user"
    val isSystem = message.senderType == "system"
    val timestamp = formatSupportMessageTime(message.createdAt)
    val body = message.body.orEmpty()
    val imageUrls = message.imageUrls.orEmpty()
    val bodyColor = when {
        isUser -> Color.White
        isSystem -> Color(0xFF3F444B)
        else -> Color(0xFF111111)
    }
    val linkColor = if (isUser) Color.White else Color(0xFF111111)
    val linkInteractionListener = remember(context, uriHandler) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            runCatching { uriHandler.openUri(url) }
                .onFailure { error ->
                    onStatusHint("链接打开失败，请复制后打开")
                    SessionApi.reportClientLog(
                        level = "warn",
                        event = "ui.link_open_failed",
                        message = "Support feedback link open failed",
                        attrs = mapOf(
                            "source" to "support_feedback",
                            "scheme" to url.substringBefore(":", missingDelimiterValue = "")
                                .lowercase()
                                .take(12),
                            "exception" to error.javaClass.simpleName
                        )
                    )
                }
        }
    }
    val renderedBody = remember(body, linkColor, linkInteractionListener) {
        buildSupportLinkedText(body, linkColor, linkInteractionListener)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when {
            isUser -> Arrangement.End
            isSystem -> Arrangement.Center
            else -> Arrangement.Start
        }
    ) {
        Column(
            horizontalAlignment = when {
                isUser -> Alignment.End
                isSystem -> Alignment.CenterHorizontally
                else -> Alignment.Start
            },
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.widthIn(max = if (isSystem) 330.dp else 310.dp)
        ) {
            Text(
                text = when {
                    isUser -> "我"
                    isSystem -> "系统提示"
                    else -> "客服"
                },
                color = if (isSystem) Color(0xFF6D7178) else Color(0xFF8A8E96),
                fontSize = 11.sp,
                fontWeight = if (isSystem) FontWeight.Medium else FontWeight.Normal,
                lineHeight = 14.sp
            )
            if (body.isNotBlank()) {
                Surface(
                    color = when {
                        isUser -> Color(0xFF111111)
                        isSystem -> Color(0xFFF5F6F8)
                        else -> Color.White
                    },
                    shape = RoundedCornerShape(
                        topStart = if (isSystem) 14.dp else 18.dp,
                        topEnd = if (isSystem) 14.dp else 18.dp,
                        bottomEnd = if (isUser) 6.dp else if (isSystem) 14.dp else 18.dp,
                        bottomStart = if (isUser) 18.dp else if (isSystem) 14.dp else 6.dp
                    ),
                    border = when {
                        isUser -> null
                        isSystem -> BorderStroke(0.8.dp, Color(0xFFE4E6EA))
                        else -> BorderStroke(0.7.dp, Color(0xFFE1E4E8))
                    }
                ) {
                    val textModifier = Modifier.padding(
                        horizontal = if (isSystem) 13.dp else 14.dp,
                        vertical = if (isSystem) 9.dp else 10.dp
                    )
                    SelectionContainer {
                        Text(
                            text = renderedBody,
                            color = bodyColor,
                            fontSize = if (isSystem) 14.sp else 15.sp,
                            lineHeight = if (isSystem) 21.sp else 22.sp,
                            modifier = textModifier
                        )
                    }
                }
            }
            if (imageUrls.isNotEmpty()) {
                HamburgerSupportMessageImageStrip(
                    imageUrls = imageUrls,
                    isUser = isUser
                )
            }
            if (timestamp.isNotEmpty()) {
                Text(
                    text = timestamp,
                    color = Color(0xFF9AA0A8),
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
private fun HamburgerSupportMessageImageStrip(
    imageUrls: List<String>,
    isUser: Boolean
) {
    val previewImages = imageUrls.take(4)
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val unavailableImages = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(previewImages) {
        unavailableImages.keys
            .filterNot { it in previewImages }
            .forEach { unavailableImages.remove(it) }
    }
    Row(
        horizontalArrangement = if (isUser) {
            Arrangement.spacedBy(8.dp, Alignment.End)
        } else {
            Arrangement.spacedBy(8.dp)
        },
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        previewImages.forEachIndexed { index, imageUrl ->
            HamburgerSupportMessageImageThumb(
                imageUrl = imageUrl,
                index = index,
                isUser = isUser,
                onUnavailableChanged = { unavailable ->
                    if (unavailable) {
                        unavailableImages[imageUrl] = true
                    } else {
                        unavailableImages.remove(imageUrl)
                    }
                },
                onClick = { previewIndex = index }
            )
        }
    }
    previewIndex?.let { index ->
        val unavailablePages = previewImages
            .mapIndexedNotNull { page, imageUrl ->
                page.takeIf { unavailableImages[imageUrl] == true }
            }
            .toSet()
        Dialog(
            onDismissRequest = { previewIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000))
            ) {
                ImagePreviewPager(
                    models = previewImages,
                    initialPage = index,
                    contentDescription = "图片预览",
                    onDismiss = { previewIndex = null },
                    unavailablePages = unavailablePages
                )
                Surface(
                    color = Color(0x99111111),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .statusBarsPadding()
                        .padding(top = 10.dp, end = 22.dp)
                        .size(38.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { previewIndex = null }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        HamburgerCloseIcon(tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HamburgerSupportMessageImageThumb(
    imageUrl: String,
    index: Int,
    isUser: Boolean,
    onUnavailableChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    var loadUnavailable by remember(imageUrl) { mutableStateOf(false) }
    LaunchedEffect(imageUrl) {
        loadUnavailable = false
        onUnavailableChanged(false)
        val decoded = withContext(Dispatchers.IO) {
            context.decodeChatImagePreview(imageUrl, targetSize = 360)
        }
        bitmap = decoded
        loadUnavailable = decoded == null && imageUrl.isRemoteImageSource()
        onUnavailableChanged(loadUnavailable)
    }
    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (isUser) Color(0xFF2A2A2A) else Color(0xFFE9EAED))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        val previewBitmap = bitmap
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = "第${index + 1}张图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = if (loadUnavailable) "暂时不可用" else "图片",
                color = if (isUser) Color(0xFFD8DADF) else Color(0xFF777C85),
                fontSize = if (loadUnavailable) 11.sp else 13.sp,
                lineHeight = 16.sp,
                fontWeight = if (loadUnavailable) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Surface(
            color = Color(0xAA111111),
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
        ) {
            Text(
                text = "${index + 1}",
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

internal enum class HamburgerSupportFeedbackPreviewVariant {
    Normal,
    Empty,
    Loading,
    Failed,
    ImageInput,
    LongInput
}

@Composable
internal fun HamburgerSupportFeedbackPagePreview(
    variant: HamburgerSupportFeedbackPreviewVariant = HamburgerSupportFeedbackPreviewVariant.Normal
) {
    val now = System.currentTimeMillis()
    val baseMessages = listOf(
        SessionApi.SupportMessage(
            id = 1,
            senderType = "user",
            body = "我这边会员权益好像没有刷新，麻烦帮我看一下。",
            imageUrls = listOf("support-preview-user-image-1"),
            createdAt = now - 32L * 60L * 1000L
        ),
        SessionApi.SupportMessage(
            id = 2,
            senderType = "system",
            body = "您的反馈已提交。为便于定位，请继续补充问题发生时间、操作步骤或截图；如需进一步沟通，我们会通过本页面回复您。",
            createdAt = now - 26L * 60L * 1000L,
            readByUserAt = now - 26L * 60L * 1000L
        ),
        SessionApi.SupportMessage(
            id = 3,
            senderType = "admin",
            body = "收到，客服已经帮您同步了一次。您重新打开会员中心看看，如果还不对，把截图发过来；官网地址 https://nongjiqiancha.cn 也可以复制后打开。",
            createdAt = now - 18L * 60L * 1000L
        )
    )
    val messages = when (variant) {
        HamburgerSupportFeedbackPreviewVariant.Normal,
        HamburgerSupportFeedbackPreviewVariant.ImageInput,
        HamburgerSupportFeedbackPreviewVariant.LongInput -> baseMessages
        HamburgerSupportFeedbackPreviewVariant.Empty,
        HamburgerSupportFeedbackPreviewVariant.Loading,
        HamburgerSupportFeedbackPreviewVariant.Failed -> emptyList()
    }
    val selectedImages = when (variant) {
        HamburgerSupportFeedbackPreviewVariant.ImageInput -> listOf(
            ComposerImageAttachment("support-preview-composer-image-1"),
            ComposerImageAttachment("support-preview-composer-image-2")
        )
        else -> emptyList()
    }
    val inputValue = when (variant) {
        HamburgerSupportFeedbackPreviewVariant.Empty,
        HamburgerSupportFeedbackPreviewVariant.Loading,
        HamburgerSupportFeedbackPreviewVariant.Failed -> TextFieldValue("")
        HamburgerSupportFeedbackPreviewVariant.ImageInput -> TextFieldValue("补充截图说明，会员中心显示的剩余次数和首页不一致。")
        HamburgerSupportFeedbackPreviewVariant.LongInput -> TextFieldValue(
            "我把情况详细说一下：昨天清除数据后重新打开 App，会员中心显示正常，但是回到主界面以后又提示次数不足；我尝试退出设置页再进入，消息还能复制，网址也能点开。请帮我确认是不是后台同步延迟。"
        )
        HamburgerSupportFeedbackPreviewVariant.Normal -> TextFieldValue("我再试一下")
    }
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerSupportFeedbackContent(
            messages = messages,
            inputValue = inputValue,
            selectedImages = selectedImages,
            loading = variant == HamburgerSupportFeedbackPreviewVariant.Loading,
            loadFailed = variant == HamburgerSupportFeedbackPreviewVariant.Failed,
            sending = variant == HamburgerSupportFeedbackPreviewVariant.ImageInput,
            sendingHint = if (variant == HamburgerSupportFeedbackPreviewVariant.ImageInput) "正在上传图片..." else null,
            onInputChange = {},
            onAddClick = {},
            onRemoveImage = {},
            onSend = {},
            onRetry = {},
            onStatusHint = {},
            modifier = Modifier
                .padding(14.dp)
                .heightIn(min = 520.dp, max = 620.dp)
        )
    }
}

@Composable
private fun HamburgerDeleteHistoryConfirmDialog(
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (!deleting) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                .padding(horizontal = 28.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            HamburgerDeleteHistoryConfirmCard(
                deleting = deleting,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                modifier = Modifier.widthIn(max = 340.dp)
            )
        }
    }
}

@Composable
private fun HamburgerDeleteHistoryConfirmCard(
    deleting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 18.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "删除历史对话？",
                color = Color(0xFF111111),
                fontSize = 20.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "将删除当前账号历史对话，并清除长期记忆。会员、礼品卡、反馈记录不受影响。",
                color = Color(0xFF33363D),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color(0xFFF0F1F2),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(
                            enabled = !deleting,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "取消",
                            color = Color(0xFF111111),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Surface(
                    color = if (deleting) Color(0xFFD8DADF) else Color(0xFFD24646),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(
                            enabled = !deleting,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onConfirm
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (deleting) "删除中" else "确认删除",
                            color = if (deleting) Color(0xFF777B82) else Color.White,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HamburgerDeleteHistoryConfirmPreview() {
    HamburgerDeleteHistoryConfirmCard(
        deleting = false,
        onDismiss = {},
        onConfirm = {},
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun HamburgerLogoutConfirmDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (!submitting) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                .padding(horizontal = 28.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            HamburgerLogoutConfirmCard(
                submitting = submitting,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                modifier = Modifier.widthIn(max = 340.dp)
            )
        }
    }
}

@Composable
private fun HamburgerLogoutConfirmCard(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 18.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "确认退出当前设备",
                color = Color(0xFF111111),
                fontSize = 20.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "退出后需要重新登录。历史对话、会员权益、礼品卡和反馈记录不会删除。",
                color = Color(0xFF33363D),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color(0xFFF0F1F2),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(
                            enabled = !submitting,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "取消",
                            color = Color(0xFF111111),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Surface(
                    color = if (submitting) Color(0xFFD8DADF) else Color(0xFFD24646),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(
                            enabled = !submitting,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onConfirm
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (submitting) "退出中" else "退出",
                            color = if (submitting) Color(0xFF777B82) else Color.White,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HamburgerLogoutConfirmPreview() {
    HamburgerLogoutConfirmCard(
        submitting = false,
        onDismiss = {},
        onConfirm = {},
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun HamburgerAccountDeletionConfirmDialog(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (!submitting) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                .padding(horizontal = 28.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            HamburgerAccountDeletionConfirmCard(
                submitting = submitting,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                modifier = Modifier.widthIn(max = 340.dp)
            )
        }
    }
}

@Composable
private fun HamburgerAccountDeletionConfirmCard(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 18.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "提交注销申请？",
                color = Color(0xFF111111),
                fontSize = 20.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "提交后会退出当前账号。工作人员核验后，会在 15 个工作日内按规则处理账号和相关记录。",
                color = Color(0xFF33363D),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color(0xFFF0F1F2),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(
                            enabled = !submitting,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "取消",
                            color = Color(0xFF111111),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Surface(
                    color = if (submitting) Color(0xFFD8DADF) else Color(0xFFD24646),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable(
                            enabled = !submitting,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onConfirm
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (submitting) "提交中" else "提交申请",
                            color = if (submitting) Color(0xFF777B82) else Color.White,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HamburgerAccountDeletionConfirmPreview() {
    HamburgerAccountDeletionConfirmCard(
        submitting = false,
        onDismiss = {},
        onConfirm = {},
        modifier = Modifier.fillMaxWidth()
    )
}

private fun giftCardRedeemSuccessText(result: SessionApi.GiftCardRedeemResult): String {
    val tierRaw = result.appliedTier?.lowercase(Locale.ROOT) ?: result.tier?.lowercase(Locale.ROOT)
    val tier = when (tierRaw) {
        "pro" -> "Pro"
        "plus" -> "Plus"
        else -> "会员"
    }
    val dailyCount = membershipPaidDailyLimitForTier(tierRaw)
    val days = result.durationDays?.takeIf { it > 0 }?.let { "${it}天" } ?: "权益"
    val expire = result.membershipExpireAt?.takeIf { it > 0L }?.let { millis ->
        SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(Date(millis))
    }
    val quota = dailyCount?.let { "，每日 ${it} 次" }.orEmpty()
    val detail = if (expire.isNullOrBlank()) {
        "$tier $days 已生效$quota"
    } else {
        "$tier $days 已生效$quota，到期 $expire"
    }
    return if (result.replay) {
        "该礼品卡已兑换成功，会员权益已生效，无需重复兑换。$detail"
    } else {
        detail
    }
}

private fun normalizeGiftCardCodeInput(raw: String): String =
    raw.asSequence()
        .filterNot { it.isWhitespace() || it == '-' }
        .joinToString("")
        .uppercase()
        .take(64)

private fun hamburgerTodayAgriDateText(dateCn: String?): String {
    val raw = dateCn?.trim().orEmpty()
    if (raw.length != 8 || raw.any { !it.isDigit() }) return ""
    val month = raw.substring(4, 6).toIntOrNull() ?: return ""
    val day = raw.substring(6, 8).toIntOrNull() ?: return ""
    if (month !in 1..12 || day !in 1..31) return ""
    return "${month}月${day}日"
}

@Composable
private fun HamburgerRedeemCodePage(
    onPendingAction: (String) -> Unit,
    onRedeemSuccess: () -> Unit
) {
    HamburgerRedeemCodeContent(
        onPendingAction = onPendingAction,
        onRedeemSuccess = onRedeemSuccess,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 32.dp)
    )
}

@Composable
private fun HamburgerRedeemCodeContent(
    onPendingAction: (String) -> Unit,
    onRedeemSuccess: () -> Unit = {},
    modifier: Modifier = Modifier,
    initialCode: String = "",
    initialError: String? = null
) {
    var redeemCode by remember(initialCode) { mutableStateOf(initialCode) }
    var redeeming by remember { mutableStateOf(false) }
    var redeemResult by remember { mutableStateOf<SessionApi.GiftCardRedeemResult?>(null) }
    var redeemError by remember(initialCode, initialError) { mutableStateOf(initialError?.takeIf { it.isNotBlank() }) }
    fun submitRedeem() {
        val code = normalizeGiftCardCodeInput(redeemCode)
        if (redeemCode != code) {
            redeemCode = code
        }
        if (redeeming) return
        if (code.isBlank()) {
            redeemError = "请输入礼品卡码"
            onPendingAction(redeemError.orEmpty())
            return
        }
        redeemError = null
        redeeming = true
        SessionApi.redeemGiftCard(code) { result, error ->
            redeeming = false
            if (result?.ok == true) {
                redeemResult = result
                redeemCode = ""
                redeemError = null
                onRedeemSuccess()
                onPendingAction(
                    if (result.replay) {
                        "该礼品卡已兑换成功，权益已生效"
                    } else {
                        "兑换成功，会员权益已更新"
                    }
                )
            } else {
                redeemError = error ?: "兑换失败，请稍后再试"
                onPendingAction(redeemError.orEmpty())
            }
        }
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "礼品卡",
            color = Color(0xFF111111),
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(top = 14.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp),
            contentAlignment = Alignment.Center
        ) {
            val result = redeemResult
            if (result != null) {
                HamburgerRedeemSuccessCard(
                    result = result,
                    onConfirm = { redeemResult = null },
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "输入卡码兑换会员权益",
                        color = Color(0xFF111111),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        color = Color(0xFFFAFBFC),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = redeemCode,
                                enabled = !redeeming,
                                onValueChange = { next ->
                                    if (!redeeming) {
                                        redeemCode = normalizeGiftCardCodeInput(next)
                                        if (redeemError != null) {
                                            redeemError = null
                                        }
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submitRedeem() }
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(
                                    color = Color(0xFF111111),
                                    fontSize = 18.sp,
                                    lineHeight = 25.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center
                                ),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (redeemCode.isBlank()) {
                                            Text(
                                                text = "请输入礼品卡码",
                                                color = Color(0xFF9AA0A8),
                                                fontSize = 18.sp,
                                                lineHeight = 25.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }
                    redeemError?.takeIf { it.isNotBlank() }?.let { errorText ->
                        Text(
                            text = errorText,
                            color = Color(0xFFB3261E),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val canSubmit = redeemCode.isNotBlank() && !redeeming
                    Surface(
                        color = if (canSubmit) Color(0xFF111111) else Color(0xFFE3E5E8),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clickable(
                                enabled = canSubmit,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { submitRedeem() }
                            )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (redeeming) "兑换中" else "兑换",
                                color = if (canSubmit) Color.White else Color(0xFF8A8E96),
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HamburgerRedeemSuccessCard(
    result: SessionApi.GiftCardRedeemResult? = null,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF111111),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 18.dp,
        modifier = modifier.widthIn(max = 320.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = if (result?.replay == true) "权益已生效" else "兑换成功",
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (result != null) {
                Text(
                    text = giftCardRedeemSuccessText(result),
                    color = Color(0xFFD9DCE0),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )
            }
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .widthIn(min = 168.dp)
                    .heightIn(min = 40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onConfirm
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "确定",
                        color = Color(0xFF111111),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
internal fun HamburgerRedeemCodePagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerRedeemCodeContent(
            onPendingAction = {},
            initialCode = "NJQW2026",
            modifier = Modifier
                .padding(14.dp)
                .heightIn(max = 560.dp)
        )
    }
}

@Composable
internal fun HamburgerRedeemFailurePagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerRedeemCodeContent(
            onPendingAction = {},
            initialCode = "NJQW2026",
            initialError = "礼品卡码不正确，请核对后重试",
            modifier = Modifier
                .padding(14.dp)
                .heightIn(max = 560.dp)
        )
    }
}

@Composable
internal fun HamburgerRedeemSuccessCardPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 136.dp),
        contentAlignment = Alignment.Center
    ) {
        HamburgerRedeemSuccessCard(
            result = SessionApi.GiftCardRedeemResult(
                ok = true,
                tier = "plus",
                appliedTier = "plus",
                durationDays = 30,
                membershipExpireAt = System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L,
                redeemedAt = System.currentTimeMillis()
            ),
            onConfirm = {},
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}

@Composable
internal fun HamburgerRedeemReplayCardPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 136.dp),
        contentAlignment = Alignment.Center
    ) {
        HamburgerRedeemSuccessCard(
            result = SessionApi.GiftCardRedeemResult(
                ok = true,
                replay = true,
                tier = "plus",
                appliedTier = "plus",
                durationDays = 30,
                membershipExpireAt = System.currentTimeMillis() + 24L * 24L * 60L * 60L * 1000L,
                redeemedAt = System.currentTimeMillis() - 6L * 24L * 60L * 60L * 1000L
            ),
            onConfirm = {},
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}

@Composable
private fun HamburgerAccountGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color(0xFFF2F2F2),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun HamburgerAccountInfoRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(0xFF111111),
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = Color(0xFF8A8E96),
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun HamburgerAccountActionRow(
    title: String,
    enabled: Boolean = true,
    danger: Boolean = true,
    trailingText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = when {
                !enabled -> Color(0xFF8A8E96)
                danger -> Color(0xFFD24646)
                else -> Color(0xFF111111)
            },
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                color = Color(0xFF9AA0A6),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun HamburgerMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color(0xFFF2F2F2),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun HamburgerMenuDivider(startIndent: Dp = 0.dp) {
    HorizontalDivider(
        thickness = 4.dp,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startIndent)
    )
}

@Composable
private fun HamburgerMenuRow(
    icon: HamburgerMenuIcon,
    title: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    showBadge: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 57.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HamburgerMenuGlyph(
            icon = icon,
            tint = if (destructive) Color(0xFFD24646) else Color(0xFF111111),
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = if (destructive) Color(0xFFD24646) else Color(0xFF111111),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showBadge) {
            Surface(
                color = Color(0xFFE5484D),
                shape = CircleShape,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(7.dp)
            ) {}
        }
        if (showChevron) {
            HamburgerChevronIcon(
                tint = Color(0xFFB7BBC2),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(18.dp)
            )
        }
    }
}

private enum class HamburgerMenuIcon {
    Membership,
    Redeem,
    Account,
    Update,
    Document,
    Privacy,
    Risk,
    Feedback,
    TodayAgri,
    Logout
}

private enum class HamburgerMenuPage {
    Menu,
    Membership,
    Redeem,
    Account,
    Support,
    TodayAgri,
    LegalHub,
    ServiceAgreement,
    PrivacyPolicy,
    ThirdPartyList,
    PersonalInfoList,
    PermissionList,
    RiskNotice
}

private fun HamburgerMenuPage.isLegalDetailPage(): Boolean =
    this == HamburgerMenuPage.ServiceAgreement ||
        this == HamburgerMenuPage.PrivacyPolicy ||
        this == HamburgerMenuPage.ThirdPartyList ||
        this == HamburgerMenuPage.PersonalInfoList ||
        this == HamburgerMenuPage.PermissionList ||
        this == HamburgerMenuPage.RiskNotice

private fun formatSupportMessageTime(createdAt: Long?): String {
    val timestamp = createdAt ?: return ""
    if (timestamp <= 0L) return ""
    val now = System.currentTimeMillis()
    val pattern = if (now - timestamp in 0L..24L * 60L * 60L * 1000L) "HH:mm" else "MM-dd HH:mm"
    return try {
        SimpleDateFormat(pattern, Locale.CHINA).format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}

@Composable
private fun HamburgerCloseIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.13f
        drawLine(
            color = tint,
            start = Offset(size.width * 0.22f, size.height * 0.22f),
            end = Offset(size.width * 0.78f, size.height * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.78f, size.height * 0.22f),
            end = Offset(size.width * 0.22f, size.height * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun HamburgerBackIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.13f
        drawLine(
            color = tint,
            start = Offset(size.width * 0.72f, size.height * 0.18f),
            end = Offset(size.width * 0.28f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.28f, size.height * 0.5f),
            end = Offset(size.width * 0.72f, size.height * 0.82f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun HamburgerChevronIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.14f
        drawLine(
            color = tint,
            start = Offset(size.width * 0.34f, size.height * 0.20f),
            end = Offset(size.width * 0.66f, size.height * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.66f, size.height * 0.50f),
            end = Offset(size.width * 0.34f, size.height * 0.80f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun HamburgerMenuGlyph(
    icon: HamburgerMenuIcon,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = size.minDimension * 0.09f
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        when (icon) {
            HamburgerMenuIcon.Membership -> {
                val plusStrokeWidth = strokeWidth * 0.92f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.16f, h * 0.16f),
                    size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.68f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.15f, h * 0.15f),
                    style = stroke
                )
                drawLine(tint, Offset(w * 0.35f, h * 0.50f), Offset(w * 0.65f, h * 0.50f), plusStrokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.35f), Offset(w * 0.50f, h * 0.65f), plusStrokeWidth, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.Redeem -> {
                val giftStrokeWidth = strokeWidth
                val giftStroke = Stroke(
                    width = giftStrokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                val leftBow = Path().apply {
                    moveTo(w * 0.50f, h * 0.38f)
                    cubicTo(w * 0.39f, h * 0.16f, w * 0.20f, h * 0.24f, w * 0.28f, h * 0.38f)
                    cubicTo(w * 0.35f, h * 0.48f, w * 0.45f, h * 0.43f, w * 0.50f, h * 0.38f)
                }
                val rightBow = Path().apply {
                    moveTo(w * 0.50f, h * 0.38f)
                    cubicTo(w * 0.61f, h * 0.16f, w * 0.80f, h * 0.24f, w * 0.72f, h * 0.38f)
                    cubicTo(w * 0.65f, h * 0.48f, w * 0.55f, h * 0.43f, w * 0.50f, h * 0.38f)
                }
                drawPath(leftBow, tint, style = giftStroke)
                drawPath(rightBow, tint, style = giftStroke)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.13f, h * 0.41f),
                    size = androidx.compose.ui.geometry.Size(w * 0.74f, h * 0.17f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, h * 0.05f),
                    style = giftStroke
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.17f, h * 0.57f),
                    size = androidx.compose.ui.geometry.Size(w * 0.66f, h * 0.30f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, h * 0.05f),
                    style = giftStroke
                )
                drawLine(tint, Offset(w * 0.50f, h * 0.41f), Offset(w * 0.50f, h * 0.87f), giftStrokeWidth, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.Account -> {
                drawCircle(tint, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
                drawCircle(tint, radius = w * 0.13f, center = Offset(w * 0.50f, h * 0.39f), style = stroke)
                drawArc(
                    color = tint,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(w * 0.27f, h * 0.58f),
                    size = androidx.compose.ui.geometry.Size(w * 0.46f, h * 0.30f),
                    style = stroke
                )
            }
            HamburgerMenuIcon.Update -> {
                val updateStrokeWidth = strokeWidth * 0.92f
                val updateStroke = Stroke(
                    width = updateStrokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                drawArc(
                    color = tint,
                    startAngle = 190f,
                    sweepAngle = 148f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.18f),
                    size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.64f),
                    style = updateStroke
                )
                drawArc(
                    color = tint,
                    startAngle = 10f,
                    sweepAngle = 148f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.18f),
                    size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.64f),
                    style = updateStroke
                )
                val topTip = Offset(w * 0.81f, h * 0.36f)
                drawLine(tint, topTip, Offset(w * 0.81f, h * 0.22f), updateStrokeWidth, cap = StrokeCap.Round)
                drawLine(tint, topTip, Offset(w * 0.67f, h * 0.36f), updateStrokeWidth, cap = StrokeCap.Round)
                val bottomTip = Offset(w * 0.19f, h * 0.64f)
                drawLine(tint, bottomTip, Offset(w * 0.33f, h * 0.64f), updateStrokeWidth, cap = StrokeCap.Round)
                drawLine(tint, bottomTip, Offset(w * 0.19f, h * 0.78f), updateStrokeWidth, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.Document -> {
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.14f)
                    lineTo(w * 0.66f, h * 0.14f)
                    lineTo(w * 0.82f, h * 0.29f)
                    lineTo(w * 0.82f, h * 0.86f)
                    lineTo(w * 0.18f, h * 0.86f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, Offset(w * 0.32f, h * 0.48f), Offset(w * 0.69f, h * 0.48f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.32f, h * 0.63f), Offset(w * 0.69f, h * 0.63f), strokeWidth, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.Privacy -> {
                val shackle = Path().apply {
                    moveTo(w * 0.29f, h * 0.42f)
                    lineTo(w * 0.29f, h * 0.32f)
                    cubicTo(
                        w * 0.29f, h * 0.10f,
                        w * 0.71f, h * 0.10f,
                        w * 0.71f, h * 0.32f
                    )
                    lineTo(w * 0.71f, h * 0.42f)
                }
                drawPath(shackle, tint, style = stroke)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.17f, h * 0.40f),
                    size = androidx.compose.ui.geometry.Size(w * 0.66f, h * 0.48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, h * 0.08f),
                    style = stroke
                )
                drawCircle(tint, radius = w * 0.052f, center = Offset(w * 0.50f, h * 0.62f))
                drawLine(tint, Offset(w * 0.50f, h * 0.67f), Offset(w * 0.50f, h * 0.75f), strokeWidth, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.Risk -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.15f)
                    lineTo(w * 0.84f, h * 0.83f)
                    lineTo(w * 0.16f, h * 0.83f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.37f), Offset(w * 0.5f, h * 0.58f), strokeWidth, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.70f))
            }
            HamburgerMenuIcon.Feedback -> {
                val path = Path().apply {
                    moveTo(w * 0.16f, h * 0.20f)
                    lineTo(w * 0.84f, h * 0.20f)
                    lineTo(w * 0.84f, h * 0.68f)
                    lineTo(w * 0.58f, h * 0.68f)
                    lineTo(w * 0.43f, h * 0.84f)
                    lineTo(w * 0.43f, h * 0.68f)
                    lineTo(w * 0.16f, h * 0.68f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, Offset(w * 0.32f, h * 0.45f), Offset(w * 0.68f, h * 0.45f), strokeWidth, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.TodayAgri -> {
                val bookStroke = Stroke(
                    width = strokeWidth * 0.95f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
                val leftPage = Path().apply {
                    moveTo(w * 0.17f, h * 0.20f)
                    cubicTo(w * 0.28f, h * 0.15f, w * 0.40f, h * 0.18f, w * 0.50f, h * 0.28f)
                    lineTo(w * 0.50f, h * 0.83f)
                    cubicTo(w * 0.39f, h * 0.75f, w * 0.28f, h * 0.74f, w * 0.17f, h * 0.80f)
                    close()
                }
                val rightPage = Path().apply {
                    moveTo(w * 0.83f, h * 0.20f)
                    cubicTo(w * 0.72f, h * 0.15f, w * 0.60f, h * 0.18f, w * 0.50f, h * 0.28f)
                    lineTo(w * 0.50f, h * 0.83f)
                    cubicTo(w * 0.61f, h * 0.75f, w * 0.72f, h * 0.74f, w * 0.83f, h * 0.80f)
                    close()
                }
                drawPath(leftPage, tint, style = bookStroke)
                drawPath(rightPage, tint, style = bookStroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.28f), Offset(w * 0.50f, h * 0.83f), strokeWidth * 0.85f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.28f, h * 0.39f), Offset(w * 0.41f, h * 0.42f), strokeWidth * 0.75f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.59f, h * 0.42f), Offset(w * 0.72f, h * 0.39f), strokeWidth * 0.75f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.28f, h * 0.56f), Offset(w * 0.41f, h * 0.59f), strokeWidth * 0.75f, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.59f, h * 0.59f), Offset(w * 0.72f, h * 0.56f), strokeWidth * 0.75f, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.Logout -> {
                drawLine(tint, Offset(w * 0.18f, h * 0.18f), Offset(w * 0.18f, h * 0.82f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.18f, h * 0.18f), Offset(w * 0.44f, h * 0.18f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.18f, h * 0.82f), Offset(w * 0.44f, h * 0.82f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.40f, h * 0.50f), Offset(w * 0.84f, h * 0.50f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.68f, h * 0.34f), Offset(w * 0.84f, h * 0.50f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.68f, h * 0.66f), Offset(w * 0.84f, h * 0.50f), strokeWidth, cap = StrokeCap.Round)
            }
        }
    }
}

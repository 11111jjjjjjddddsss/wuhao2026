package com.nongjiqianwen

import android.app.Activity
import android.net.Uri
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HAMBURGER_PAGE_ENTER_MS = 180
private const val HAMBURGER_PAGE_EXIT_MS = 150
private const val SUPPORT_MESSAGE_MAX_CHARS = 2000
internal const val SUPPORT_SEND_FAILED_HINT = "发送失败，请检查网络后重试"
private val HamburgerBackButtonTopPadding = 4.dp
private val supportBareUrlRegex = Regex("(?i)\\b((?:https?://|www\\.)[^\\s<>()]+)")

private fun normalizeSupportLinkTarget(raw: String): String {
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

private fun trimSupportBareUrlDisplayText(raw: String): String {
    val trailingPunctuation = ".,;:!?，。；：！？)]}）】》」』”\"'"
    return raw.trimEnd { it in trailingPunctuation }
}

private fun buildSupportLinkedText(text: String, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val bareUrl = supportBareUrlRegex.find(text, index)
            if (bareUrl == null) {
                append(text.substring(index))
                break
            }
            if (bareUrl.range.first > index) {
                append(text.substring(index, bareUrl.range.first))
            }
            val displayText = trimSupportBareUrlDisplayText(bareUrl.value)
            if (displayText.isEmpty()) {
                append(bareUrl.value)
                index = bareUrl.range.last + 1
                continue
            }
            withLink(LinkAnnotation.Url(normalizeSupportLinkTarget(displayText))) {
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(displayText)
                }
            }
            index = bareUrl.range.first + displayText.length
        }
    }
}

@Composable
internal fun HamburgerMenuSheet(
    visible: Boolean,
    userId: String,
    membershipEntitlement: SessionApi.EntitlementSnapshot?,
    membershipLoadState: MembershipLoadState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onRequestMembershipRefresh: () -> Unit,
    onMembershipPaymentUnavailable: () -> Unit,
    onClearChatHistory: () -> Unit,
    onPlaceholderClick: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var noticeText by remember(visible) { mutableStateOf<String?>(null) }
    var page by remember(visible) { mutableStateOf(HamburgerMenuPage.Menu) }
    var legalSubpage by remember(visible) { mutableStateOf(false) }
    var supportSummary by remember(visible) { mutableStateOf<SessionApi.SupportSummary?>(null) }
    var supportRefreshTick by remember(visible) { mutableStateOf(0) }
    var supportAttachmentMenuVisible by remember(visible) { mutableStateOf(false) }
    var supportAttachmentCloseRequest by remember(visible) { mutableStateOf(0) }
    var updateChecking by remember(visible) { mutableStateOf(false) }
    var updateDialogInfo by remember(visible) { mutableStateOf<SessionApi.AppUpdateInfo?>(null) }
    var updateDownloading by remember(visible) { mutableStateOf(false) }
    fun performButtonHaptic() {
        val handled = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!handled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    fun showNotice(text: String) {
        noticeText = text
        onPlaceholderClick(text)
    }
    fun checkAppUpdate() {
        if (updateChecking || updateDownloading) return
        updateChecking = true
        showNotice("正在检查更新...")
        SessionApi.getAppUpdate { info ->
            updateChecking = false
            when {
                info == null -> showNotice("检查更新失败，请稍后重试")
                info.usableUpdate -> updateDialogInfo = info
                else -> showNotice("已是最新版本")
            }
        }
    }
    fun startAppUpdate(update: SessionApi.AppUpdateInfo) {
        if (updateDownloading) return
        val appContext = context.applicationContext
        if (!AppUpdateInstaller.canRequestInstallPackages(appContext)) {
            val opened = AppUpdateInstaller.openInstallPermissionSettings(appContext)
            showNotice(if (opened) "请允许安装未知应用后再继续更新" else "请先允许安装未知应用")
            return
        }
        updateDownloading = true
        showNotice("正在下载更新...")
        scope.launch {
            val apkFile = AppUpdateInstaller.downloadApk(appContext, update)
            updateDownloading = false
            if (apkFile == null) {
                showNotice("更新下载失败，请稍后重试")
                return@launch
            }
            updateDialogInfo = null
            val started = AppUpdateInstaller.installApk(appContext, apkFile)
            if (!started) {
                showNotice("安装页面打开失败")
            }
        }
    }
    fun handleBackClick() {
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
    LaunchedEffect(noticeText) {
        if (noticeText == null) return@LaunchedEffect
        delay(1500)
        noticeText = null
    }
    LaunchedEffect(visible, supportRefreshTick) {
        if (!visible) return@LaunchedEffect
        SessionApi.getSupportSummary { summary ->
            supportSummary = summary
        }
    }
    BackHandler(enabled = visible && page != HamburgerMenuPage.Menu) {
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
            color = Color(0xFFF8F9FA),
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
                                onOpenLegalHub = {
                                    performButtonHaptic()
                                    legalSubpage = false
                                    page = HamburgerMenuPage.LegalHub
                                },
                                onCheckUpdate = {
                                    performButtonHaptic()
                                    checkAppUpdate()
                                },
                                onPlaceholderClick = ::showNotice
                            )
                        }
                        HamburgerMenuPage.Membership -> {
                            HamburgerMembershipCenterPage(
                                userId = userId,
                                entitlement = membershipEntitlement,
                                loadState = membershipLoadState,
                                onPaymentUnavailable = {
                                    performButtonHaptic()
                                    onMembershipPaymentUnavailable()
                                }
                            )
                        }
                        HamburgerMenuPage.Account -> {
                            HamburgerAccountManagementPage(
                                onPendingAction = ::showNotice,
                                onClearChatHistory = onClearChatHistory
                            )
                        }
                        HamburgerMenuPage.Redeem -> {
                            HamburgerRedeemCodePage(
                                onPendingAction = ::showNotice
                            )
                        }
                        HamburgerMenuPage.Support -> {
                            HamburgerSupportFeedbackPage(
                                attachmentCloseRequest = supportAttachmentCloseRequest,
                                onPendingAction = ::showNotice,
                                onConversationChanged = {
                                    supportRefreshTick += 1
                                },
                                onAttachmentMenuVisibilityChanged = { visible ->
                                    supportAttachmentMenuVisible = visible
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
                noticeText?.let { text ->
                    Surface(
                        color = Color(0xEE111111),
                        shape = RoundedCornerShape(999.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 36.dp)
                    ) {
                        Text(
                            text = text,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
                updateDialogInfo?.let { info ->
                    HamburgerAppUpdateDialog(
                        update = info,
                        downloading = updateDownloading,
                        onDismiss = {
                            if (!updateDownloading) updateDialogInfo = null
                        },
                        onInstall = {
                            performButtonHaptic()
                            startAppUpdate(info)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HamburgerAppUpdateDialog(
    update: SessionApi.AppUpdateInfo,
    downloading: Boolean,
    onDismiss: () -> Unit,
    onInstall: () -> Unit
) {
    val latestName = update.latestVersionName?.takeIf { it.isNotBlank() }
    val latestCode = update.latestVersionCode
    val versionText = when {
        latestName != null && latestCode != null -> "版本 $latestName ($latestCode)"
        latestName != null -> "版本 $latestName"
        latestCode != null -> "版本 $latestCode"
        else -> "发现新版本"
    }
    val sizeText = formatAppUpdateSize(update.fileSizeBytes)
    val notes = update.releaseNotes?.trim().orEmpty()
    val forceUpdate = update.forceUpdate == true
    Dialog(
        onDismissRequest = {
            if (!downloading && !forceUpdate) onDismiss()
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
            HamburgerAppUpdateCard(
                versionText = versionText,
                sizeText = sizeText,
                notes = notes,
                forceUpdate = forceUpdate,
                downloading = downloading,
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
    forceUpdate: Boolean,
    downloading: Boolean,
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
                text = notes.ifBlank { "优化产品体验" },
                color = Color(0xFF222222),
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
            if (downloading) {
                Text(
                    text = "正在下载更新...",
                    color = Color(0xFF6D7178),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!forceUpdate) {
                    Surface(
                        color = Color(0xFFF0F1F2),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clickable(
                                enabled = !downloading,
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
internal fun HamburgerAppUpdateDialogPreview() {
    HamburgerAppUpdateCard(
        versionText = "版本 1.0.1 (2)",
        sizeText = "38.5MB",
        notes = "优化产品体验",
        forceUpdate = false,
        downloading = false,
        onDismiss = {},
        onInstall = {},
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatAppUpdateSize(bytes: Long?): String? {
    val value = bytes ?: return null
    if (value <= 0L) return null
    val mb = value / 1024.0 / 1024.0
    return String.format(Locale.US, "%.1fMB", mb)
}

@Composable
private fun HamburgerMembershipCenterPage(
    userId: String,
    entitlement: SessionApi.EntitlementSnapshot?,
    loadState: MembershipLoadState,
    onPaymentUnavailable: () -> Unit
) {
    HamburgerMembershipCenterContent(
        userId = userId,
        entitlement = entitlement,
        loadState = loadState,
        onPaymentUnavailable = onPaymentUnavailable,
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
private fun HamburgerMembershipCenterContent(
    userId: String,
    entitlement: SessionApi.EntitlementSnapshot?,
    loadState: MembershipLoadState,
    onPaymentUnavailable: () -> Unit,
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
            paymentNoticeResetKey = userId,
            onPaymentUnavailable = onPaymentUnavailable
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
    onOpenLegalHub: () -> Unit,
    onCheckUpdate: () -> Unit,
    onPlaceholderClick: (String) -> Unit
) {
    val phoneMask = IdManager.getAuthPhoneMask()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 53.dp),
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
                subtitle = "套餐、额度和加油包",
                onClick = onOpenMembership
            )
            HamburgerMenuDivider(startIndent = 52.dp)
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
            HamburgerMenuDivider(startIndent = 52.dp)
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Update,
                title = "检查更新",
                onClick = onCheckUpdate
            )
            HamburgerMenuDivider(startIndent = 52.dp)
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Redeem,
                title = "礼品卡",
                onClick = onOpenRedeem
            )
            HamburgerMenuDivider(startIndent = 52.dp)
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Document,
                title = "服务协议",
                onClick = onOpenLegalHub
            )
        }

        HamburgerMenuGroup {
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Account,
                title = "当前账号",
                subtitle = phoneMask ?: "未登录",
                destructive = false,
                showChevron = false,
                onClick = { onPlaceholderClick(if (phoneMask != null) "当前设备已保持登录" else "请先登录") }
            )
        }
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
        HamburgerLegalPageTitle("服务协议")
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 34.dp)
    )
}

@Composable
internal fun HamburgerServiceAgreementContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HamburgerLegalPageTitle("用户协议")
        Text(
            text = "更新日期：2026年5月25日\n生效日期：2026年5月25日\n服务提供者：北京农技千问科技有限公司\n联系邮箱：465989879@qq.com",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、服务性质",
            body = "农技千查面向农业种植、作物管理、病虫害线索排查、农资信息理解和田间管理复盘等场景，提供基于文字、图片和图文混合输入的农业技术参考服务。AI 回复、今日农情和客服回复均不构成官方认定、检测报告、行政结论、专家签字意见、收益承诺或唯一处理方案。"
        )
        HamburgerAgreementSection(
            title = "二、我们提供的功能",
            body = "本服务包括农业问答、图片辅助分析、历史恢复、会员额度、加油包规则展示、礼品卡入口、帮助与反馈、今日农情和检查更新等功能。具体可用范围、次数、价格、权益、适用条件和展示内容，以 App 页面、服务端记录和当时生效规则为准。未开放或仅作规则展示的功能，不产生实际收费、兑换或权益变更。"
        )
        HamburgerAgreementSection(
            title = "三、您提交的内容与图片",
            body = "您提交的文字、图片、反馈和补充材料仍归您或原权利人所有。为提供问答、图片分析、历史展示、失败重试、客服处理和安全保障，我们会在必要范围内处理相关内容。请确保您有权提交这些内容，并尽量只上传与农业问题相关的材料；不要上传身份证件、银行卡、人脸隐私、他人隐私、商业秘密、违法侵权或与服务无关的内容。"
        )
        HamburgerAgreementSection(
            title = "四、AI 建议的使用边界",
            body = "AI 可能不准确、不完整、过时或无法覆盖现场条件。涉及农药、肥料、种子、调节剂、检疫、补贴、备案、登记、审定、质量争议、赔付或重大生产决策时，请以产品标签、官方平台、当地农业农村部门、检测机构、农技人员或其他有资质主体意见为准。请不要把 AI 回复伪装成官方证明、检测报告、行政结论、司法证据、农资真伪背书或专家签字意见。"
        )
        HamburgerAgreementSection(
            title = "五、会员、支付、加油包和礼品卡",
            body = "会员套餐、每日次数、加油包、升级补偿、优惠、礼品卡、订单、退款和权益生效规则，以 App 页面、后端记录、实际支付或兑换结果及法律规定为准。本版本不提供真实支付、自动续费或礼品卡兑换；相关入口仅展示服务规则，不会向您扣费，也不会发放兑换权益。服务范围变化时，以届时明确展示的价格、商品或服务内容、支付方式、退款规则、权益生效方式和必要资质信息为准。"
        )
        HamburgerAgreementSection(
            title = "六、农资信息和交易边界",
            body = "本服务可能展示农资相关标签解读、登记信息查询路径、使用注意事项、价格或市场信息整理等内容。相关信息仅供参考，不构成购买建议、效果保证或质量背书。本版本不提供农资商品交易；App 内关于肥料、农药、种子等农资的信息整理不等同于商品销售、质量承诺或售后承诺。"
        )
        HamburgerAgreementSection(
            title = "七、帮助与反馈、检查更新",
            body = "“帮助与反馈”用于产品问题、账号权益、异常订单、资料核对和服务沟通，客服回复会在站内展示；它不是紧急农情、灾害处置、即时人工诊断或赔付承诺通道。检查更新由您主动点击后连接服务器，下载 APK 并调起系统安装确认；App 不做静默安装。"
        )
        HamburgerAgreementSection(
            title = "八、禁止行为",
            body = "请不要上传违法、侵权、虚假、有害、无关、侵犯隐私或商业秘密的内容；不要冒充官方、专家、平台人员或他人，不传播虚假农情，不诱导错误用药用肥或违规经营；不要攻击接口、爬虫抓取、刷量、绕过额度、逆向工程、批量撞库礼品卡、转售账号权益或恶意消耗服务资源。"
        )
        HamburgerAgreementSection(
            title = "九、隐私、知识产权与未成年人",
            body = "我们会按照隐私政策处理您的个人信息。App 的程序、界面、文案、标识和服务逻辑归我们或相关权利人所有；您提交的内容仍归您或原权利人所有。未成年人应在监护人指导下使用，不应进行与其民事行为能力不符的付费、交易或生产决策；未满十四周岁的儿童提交个人信息应取得监护人同意。"
        )
        HamburgerAgreementSection(
            title = "十、责任说明、协议更新与联系我们",
            body = "我们不保证 AI 建议一定正确，也不承诺一定增产、治好病虫害或避免损失。因未核验现场、提供信息不实、违反标签或法规、擅自扩大用药用肥、第三方服务异常或不可抗力造成的损失，由相应责任方依法承担。我们可能根据产品、法律法规或运营需要更新本协议，重要变化会以 App 内页面、弹窗或其他合理方式提示。您不同意的，可以停止使用相关服务；如有问题可通过邮箱 465989879@qq.com 联系我们。"
        )
    }
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
            modifier = Modifier.padding(14.dp)
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
            text = "更新日期：2026年5月31日\n生效日期：2026年5月31日\n服务提供者：北京农技千问科技有限公司\n联系邮箱：465989879@qq.com",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、我们如何处理个人信息",
            body = "我们遵循合法、正当、必要和诚信原则处理个人信息，只在实现农技千查功能、履行协议、保障安全、处理投诉争议和依法合规所需范围内收集、使用、存储、委托处理或删除信息。我们不会因您拒绝提供非必要信息而拒绝提供基本功能，但缺少必要信息时，相关功能可能无法正常使用。"
        )
        HamburgerAgreementSection(
            title = "二、基础运行和身份标识",
            body = "为维持服务运行、区分会话和保障账号权益，我们会处理本机用户标识、登录账号标识、手机号脱敏信息、认证 token、请求时间、接口路径、版本号、网络地址、设备网络状态、错误日志、会员额度状态和必要运行缓存。手机号用于登录认证、账号识别、历史恢复和权益核对；App 不读取通讯录或短信内容。"
        )
        HamburgerAgreementSection(
            title = "三、AI 问诊和图片处理",
            body = "为生成农业技术参考建议，我们会处理您输入的文字、主动上传的图片、必要历史上下文、AI 回复、摘要、后端服务器时间、粗略地区信息和可信度。当前不读取手机 GPS 精确定位；地区信息主要来自您主动提供、网络粗略判断或未知兜底。图片会导入 App 私有目录，按图片规则压缩或直通为 JPEG 后上传，用于图片分析、历史展示、失败重试和反馈附件。"
        )
        HamburgerAgreementSection(
            title = "四、相机、照片和系统相册",
            body = "照片入口使用 Android 系统 Photo Picker，只访问您本次主动选择的图片；拍照入口调用外部相机，并通过 FileProvider 临时授权外部相机写入 App 创建的 URI，不申请 App 相机权限。Android Q 及以上系统，拍照成功后会把原始照片另存到系统相册 Pictures/农技千查，便于您找回现场照片。请避免上传无关人像、身份证件、银行卡、车牌、位置痕迹、他人隐私或商业秘密。"
        )
        HamburgerAgreementSection(
            title = "五、会员、支付、农资交易和检查更新",
            body = "会员和额度功能会处理会员档位、到期时间、每日剩余次数、升级补偿和加油包余额。本版本不提供真实支付、礼品卡兑换或农资商品交易，不会处理支付账号、支付状态、兑换记录或商品订单。检查更新会使用当前版本号、平台信息和下载 APK 缓存，用于判断是否有新版本并调起系统安装页。"
        )
        HamburgerAgreementSection(
            title = "六、帮助与反馈",
            body = "帮助与反馈会处理您提交的文字、图片、客服回复、已读状态、发送时间和必要用户标识，用于站内沟通、问题排查、权益核对和服务处理。请不要在反馈中提交与问题无关的身份证件、银行卡、完整人脸、联系方式、他人隐私或商业秘密。"
        )
        HamburgerAgreementSection(
            title = "七、当前权限说明",
            body = "当前 App 声明的权限包括：网络访问，用于连接后端、上传图片、流式回答、会员、反馈、今日农情和检查更新；网络状态，用于判断网络是否可用；安装未知应用相关权限，仅用于您主动点击“立即更新”后下载 APK 并调起系统安装确认页。当前不申请定位权限、相册 / 存储读写权限、录音、通讯录、短信或通知权限，也不做 App 外推送通知。"
        )
        HamburgerAgreementSection(
            title = "八、第三方大模型和云服务",
            body = "我们通过服务端调用境内云服务器、数据库、大模型服务、手机号认证和短信服务，用于后端运行、登录认证、生成农业技术参考建议、图片理解、摘要处理、今日农情生成、故障排查和安全保障。上传图片由后端服务器保存；必要运行日志用于排查故障和保障服务安全。"
        )
        HamburgerAgreementSection(
            title = "九、本地缓存和后端保存",
            body = "App 会在本机保存必要运行缓存，包括本机用户标识、聊天窗口快照、未发送文字草稿、待发送任务、私有图片副本、图片预览缓存和更新 APK 缓存。后端会保存会话、摘要、成功问答归档、额度流水、帮助与反馈、今日农情、上传图片地址和必要日志，用于历史恢复、服务质量改进、权益核对和故障排查。除法律法规另有要求外，我们会在实现处理目的所需的合理期限内保存信息。"
        )
        HamburgerAgreementSection(
            title = "十、第三方和系统能力清单",
            body = "云服务器和数据库用于后端运行、保存会话、图片地址、额度、反馈和必要日志；第三方大模型服务用于问诊回复、图片理解、摘要和今日农情；手机号认证和短信服务用于一键登录或验证码登录；系统浏览器、系统安装器、外部相机、Android Photo Picker 只在您主动点击相关功能时调用。本版本不包含广告、地图、推送、统计 SDK、支付 SDK、友盟、Bugly、极光或 Firebase。"
        )
        HamburgerAgreementSection(
            title = "十一、共享和公开",
            body = "我们不会出售您的个人信息。除依法依规、取得授权、实现服务必要委托处理、处理投诉争议或保护安全外，不会向无关第三方提供您的个人信息。AI 回答、客服回复和今日农情默认只在 App 内展示，不会主动公开您的个人问诊内容。"
        )
        HamburgerAgreementSection(
            title = "十二、您的权利",
            body = "您可以通过 App 内功能、帮助与反馈或联系邮箱 465989879@qq.com，要求查询、复制、更正、删除相关信息，撤回授权，咨询账号注销或投诉处理方式。我们会在核验身份并确认合法可行后处理；撤回授权、删除信息或注销账号可能影响相关功能。当前“删除所有历史对话”只删除问诊聊天历史和相关记忆，不等于完整账号注销。"
        )
        HamburgerAgreementSection(
            title = "十三、未成年人和敏感信息",
            body = "未成年人应在监护人指导下使用本服务。未满十四周岁的儿童个人信息需要监护人同意。农业图片可能包含人物、房屋、车牌、地块位置、票据、联系方式或损失情况，请上传前遮挡无关敏感信息。"
        )
        HamburgerAgreementSection(
            title = "十四、安全措施和保存期限",
            body = "我们会采取访问控制、传输加密、日志审计、最小必要处理等合理措施保护数据安全，并在实现服务目的所需的合理期限内保存信息。但互联网环境无法保证绝对安全，发现异常时请及时联系我们。"
        )
        HamburgerAgreementSection(
            title = "十五、政策更新",
            body = "我们可能根据产品变化、法律法规、权限清单、第三方服务或运营需要更新本政策。涉及处理目的、处理方式、个人信息种类或重要权利义务变化时，会通过 App 内页面、弹窗或其他合理方式提示，并在需要时重新取得您的授权。"
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
            text = "更新日期：2026年5月25日\n生效日期：2026年5月25日",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        Text(
            text = "农技千查提供农业技术参考，不能替代现场诊断、官方认定、检测结论、商品质量承诺或线下专业服务。涉及用药用肥、农资购买、重大损失、赔付争议或安全风险，请先线下复核。",
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
            body = "图片只反映拍摄瞬间和局部。病斑、叶背、根系、果实、虫体、整株和田间环境没拍清时，不要只凭图片做大面积用药、毁苗、停水停肥、索赔或投诉。"
        )
        HamburgerAgreementSection(
            title = "三、药肥农资看标签",
            body = "涉及农药、肥料、调节剂、种子、基质、设备等农资，请以产品标签、登记信息、质量证明、当地法规、安全间隔期和线下农技人员意见为准。不要仅凭 AI 建议超范围、超剂量、混配或在不适宜天气下使用；购买农资时请核对经营主体、商品资质、适用作物、售后规则和发票凭证。"
        )
        HamburgerAgreementSection(
            title = "四、官方和时效信息以最新发布为准",
            body = "检疫、补贴、备案、登记、审定、证件真伪、价格、天气、预警、质量争议、赔付和行政流程，AI 只能提供一般说明或查询路径，请以官方平台和当地主管部门最新信息为准。"
        )
        HamburgerAgreementSection(
            title = "五、交易和权益风险",
            body = "本版本不提供农资商品交易、真实支付或礼品卡兑换。会员、加油包、礼品卡、支付、农资交易或售后服务的权益，以 App 页面、服务端记录、订单记录、支付平台、商品标签和商家规则为准。请不要相信 App 外私下收款、代充、代兑换、非官方客服或承诺包治包赔的信息。"
        )
        HamburgerAgreementSection(
            title = "六、未成年人需监护",
            body = "未成年人应在监护人指导下使用。不要上传未成年人照片、身份信息、联系方式或其他无关敏感内容；未满十四周岁提交个人信息应取得监护人同意。"
        )
        HamburgerAgreementSection(
            title = "七、紧急情况别等 AI",
            body = "发生大面积突发病害、药害、灾害、食品安全、人身安全或重大财产风险时，请立即联系当地农业农村部门、植保站、应急或监管机构以及线下专业人员，不要等待 AI 回复。"
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
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(top = 14.dp)
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
            modifier = Modifier.padding(14.dp)
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
            text = "更新日期：2026年5月31日",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、基本原则",
            body = "我们不会出售您的个人信息。除依法依规、取得授权、实现服务必要委托处理、处理投诉争议或保护安全外，不会向无关第三方提供您的个人信息。"
        )
        HamburgerAgreementSection(
            title = "二、第三方大模型和云服务",
            body = "我们通过服务端调用境内第三方云计算和大模型服务，用于生成农业技术参考建议、图片理解、摘要处理和今日农情生成。相关服务只在实现问诊、摘要或今日农情功能所必需的范围内处理您提交的文字、图片、必要上下文和必要日志。"
        )
        HamburgerAgreementSection(
            title = "三、云资源和存储",
            body = "云服务器和数据库用于后端运行、保存会话、图片地址、额度、反馈、更新信息和必要日志。上传图片由后端服务器保存，必要运行日志用于排查故障和保障服务安全。"
        )
        HamburgerAgreementSection(
            title = "四、手机号认证和短信服务",
            body = "登录时，我们会通过境内第三方手机号认证和短信服务完成一键登录或验证码校验；必要时处理手机号、验证码校验结果、设备和网络相关认证参数。当前验证码需要您手动输入，App 不读取短信内容。"
        )
        HamburgerAgreementSection(
            title = "五、系统能力",
            body = "系统浏览器、系统安装器、外部相机和 Android Photo Picker 只在您主动点击相关功能时调用。它们属于系统能力，不是 App 内嵌的广告、统计或推送 SDK。"
        )
        HamburgerAgreementSection(
            title = "六、本版本未使用的第三方 SDK",
            body = "本版本不包含广告、地图、推送、统计 SDK、支付 SDK、友盟、Bugly、极光或 Firebase；不申请定位、通讯录、短信、录音或通知权限。服务范围、第三方处理方式或一键登录客户端 SDK 发生变化时，我们会更新清单和隐私政策。"
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
            modifier = Modifier.padding(14.dp)
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
            text = "更新日期：2026年5月31日",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、基础运行",
            body = "为维持服务运行，我们会处理本机用户标识、登录账号标识、手机号脱敏信息、认证 token、请求时间、接口路径、版本号、网络地址、设备网络状态、错误日志和必要运行缓存。"
        )
        HamburgerAgreementSection(
            title = "二、AI 问诊",
            body = "为生成农业技术参考建议，我们会处理您输入的文字、上传图片、必要历史上下文、后端服务器时间、粗略地区信息、AI 回复和摘要。当前不读取手机 GPS 精确定位。"
        )
        HamburgerAgreementSection(
            title = "三、图片上传",
            body = "您主动选择或拍摄的图片会导入 App 私有目录，按图片规则压缩或直通为 JPEG 后上传，用于图片分析、历史展示、失败重试和帮助与反馈附件。Android Q 及以上系统，拍照成功后会把原始照片另存到系统相册 Pictures/农技千查。"
        )
        HamburgerAgreementSection(
            title = "四、会员、礼品卡和更新",
            body = "会员和额度功能会处理会员档位、到期时间、每日剩余次数、升级补偿和加油包余额。本版本不提供真实支付、礼品卡兑换或农资商品交易，不会处理支付账号、支付状态、兑换记录或商品订单。检查更新会使用当前版本号、平台信息和下载 APK 缓存。"
        )
        HamburgerAgreementSection(
            title = "五、帮助与反馈",
            body = "帮助与反馈会处理您提交的文字、图片、客服回复、已读状态和发送时间，用于站内沟通、问题排查和服务处理。"
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
            modifier = Modifier.padding(14.dp)
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
            text = "更新日期：2026年5月25日",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、网络访问",
            body = "用于连接后端、上传图片、流式回答、会员、帮助与反馈、今日农情和检查更新。"
        )
        HamburgerAgreementSection(
            title = "二、网络状态",
            body = "用于判断网络是否可用，并在网络异常时给出提示。"
        )
        HamburgerAgreementSection(
            title = "三、安装更新 APK",
            body = "仅用于您主动点击“立即更新”后下载 APK 并调起 Android 系统安装确认页；App 不做静默安装。"
        )
        HamburgerAgreementSection(
            title = "四、相机和照片",
            body = "当前照片入口使用 Android 系统 Photo Picker，只访问您本次主动选择的图片；拍照入口调用外部相机并通过 FileProvider 授权临时写入，不申请 App 相机权限。Android Q 及以上系统，拍照成功后会把原始照片另存到系统相册 Pictures/农技千查。"
        )
        HamburgerAgreementSection(
            title = "五、当前不申请的权限",
            body = "本版本不申请定位权限、相册 / 存储读写权限、录音、通讯录、短信或通知权限；也不做 App 外推送通知。"
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
            modifier = Modifier.padding(14.dp)
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
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
internal fun HamburgerMembershipCenterPagePreview(userId: String) {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerMembershipCenterContent(
            userId = userId,
            entitlement = SessionApi.EntitlementSnapshot(
                tier = "plus",
                tierExpireAt = System.currentTimeMillis() + 24L * 24L * 60L * 60L * 1000L,
                dailyRemaining = 18,
                topupRemaining = 73,
                upgradeRemaining = 12
            ),
            loadState = MembershipLoadState.Loaded,
            onPaymentUnavailable = {},
            modifier = Modifier.padding(14.dp)
        )
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
                .heightIn(min = 560.dp)
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
                    .padding(start = 14.dp, end = 14.dp, top = 84.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                HamburgerMenuPreviewGroups()
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
            subtitle = "套餐、额度和加油包",
            onClick = {}
        )
        HamburgerMenuDivider(startIndent = 52.dp)
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
        HamburgerMenuDivider(startIndent = 52.dp)
        HamburgerMenuRow(
            icon = HamburgerMenuIcon.Update,
            title = "检查更新",
            onClick = {}
        )
        HamburgerMenuDivider(startIndent = 52.dp)
        HamburgerMenuRow(
            icon = HamburgerMenuIcon.Redeem,
            title = "礼品卡",
            onClick = {}
        )
        HamburgerMenuDivider(startIndent = 52.dp)
        HamburgerMenuRow(
            icon = HamburgerMenuIcon.Document,
            title = "服务协议",
            onClick = {}
        )
    }
    HamburgerMenuGroup {
        HamburgerMenuRow(
            icon = HamburgerMenuIcon.Account,
            title = "当前账号",
            subtitle = "138****8000",
            destructive = false,
            showChevron = false,
            onClick = {}
        )
    }
}

@Composable
private fun HamburgerAccountManagementPage(
    onPendingAction: (String) -> Unit,
    onClearChatHistory: () -> Unit
) {
    HamburgerAccountManagementContent(
        onPendingAction = onPendingAction,
        onClearChatHistory = onClearChatHistory,
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
    modifier: Modifier = Modifier
) {
    var deleteHistoryDialogVisible by rememberSaveable { mutableStateOf(false) }
    var deleteHistorySubmitting by remember { mutableStateOf(false) }
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
                value = phoneMask ?: "未登录",
                onClick = { onPendingAction(if (phoneMask != null) "当前账号已登录" else "请先登录") }
            )
        }

        HamburgerAccountGroup(
            modifier = Modifier.padding(top = 22.dp)
        ) {
            HamburgerAccountActionRow(
                title = "删除所有历史对话",
                onClick = { deleteHistoryDialogVisible = true }
            )
            HamburgerMenuDivider()
            HamburgerAccountActionRow(
                title = "退出设备",
                onClick = { onPendingAction("当前设备会保持登录") }
            )
        }

        HamburgerAccountGroup(
            modifier = Modifier.padding(top = 18.dp)
        ) {
            HamburgerAccountActionRow(
                title = "注销账号",
                onClick = { onPendingAction("账号注销暂不可用，可通过帮助与反馈联系我们") }
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
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun HamburgerSupportFeedbackPage(
    attachmentCloseRequest: Int = 0,
    onPendingAction: (String) -> Unit,
    onConversationChanged: () -> Unit,
    onAttachmentMenuVisibilityChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<SessionApi.SupportMessage>>(emptyList()) }
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var loadTick by remember { mutableStateOf(0) }
    var attachmentMenuVisible by remember { mutableStateOf(false) }
    val selectedImages = remember { mutableStateListOf<ComposerImageAttachment>() }
    var pendingCameraImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraImageGalleryBacked by rememberSaveable { mutableStateOf(false) }
    var pendingCameraImageTemporaryFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            if (!sending && selectedImages.isNotEmpty()) {
                val contextForCleanup = context.applicationContext
                val imagesForCleanup = selectedImages.toList()
                CoroutineScope(Dispatchers.IO).launch {
                    imagesForCleanup.forEach(contextForCleanup::deleteComposerImageAttachment)
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
                uploadBytes.add(bytes)
            }
            val urls = ImageUploader.uploadImages(uploadBytes)
            if (urls == null) null to "图片上传失败，请稍后再试" else urls to null
        }

    LaunchedEffect(loadTick) {
        loading = true
        loadFailed = false
        SessionApi.getSupportMessages { loaded ->
            loading = false
            if (loaded == null) {
                loadFailed = true
                return@getSupportMessages
            }
            messages = loaded
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

    fun sendMessage() {
        val body = inputValue.text.trim()
        val imageSnapshot = selectedImages.take(4)
        if ((body.isEmpty() && imageSnapshot.isEmpty()) || sending) return
        if (!SessionApi.hasBackendConfigured()) {
            onPendingAction(SUPPORT_SEND_FAILED_HINT)
            return
        }
        if (inputValue.text.length > SUPPORT_MESSAGE_MAX_CHARS) {
            onPendingAction("最多输入2000字")
            return
        }
        attachmentMenuVisible = false
        sending = true
        scope.launch {
            val (imageUrls, uploadError) = uploadSupportImagesForSend(imageSnapshot)
            if (imageUrls == null) {
                sending = false
                onPendingAction(uploadError ?: "图片上传失败，请稍后再试")
                return@launch
            }
            SessionApi.sendSupportMessage(body = body, images = imageUrls) { sent ->
                sending = false
                if (sent == null) {
                    onPendingAction(SUPPORT_SEND_FAILED_HINT)
                    return@sendSupportMessage
                }
                inputValue = TextFieldValue("")
                selectedImages.removeAll(imageSnapshot.toSet())
                scope.launch(Dispatchers.IO) {
                    imageSnapshot.forEach(context::deleteComposerImageAttachment)
                }
                loadFailed = false
                messages = messages + sent
                onConversationChanged()
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
            onInputChange = { next ->
                if (next.text.length <= SUPPORT_MESSAGE_MAX_CHARS) {
                    inputValue = next
                }
            },
            onAddClick = {
                if (!sending) {
                    focusManager.clearFocus(force = true)
                    attachmentMenuVisible = true
                }
            },
            onRemoveImage = { image ->
                if (!sending) {
                    selectedImages.remove(image)
                    scope.launch(Dispatchers.IO) {
                        context.deleteComposerImageAttachment(image)
                    }
                }
            },
            onSend = ::sendMessage,
            onRetry = { loadTick += 1 },
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
            onPhotoClick = ::launchSupportPhotoPicker
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
    onInputChange: (TextFieldValue) -> Unit,
    onAddClick: () -> Unit,
    onRemoveImage: (ComposerImageAttachment) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val inputText = inputValue.text
    val hasContent = inputText.trim().isNotEmpty() || selectedImages.isNotEmpty()
    val canSend = hasContent && !sending && inputText.length <= SUPPORT_MESSAGE_MAX_CHARS
    var inputFocused by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, loading, loadFailed, selectedImages.size, inputFocused) {
        delay(80)
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = modifier) {
        Text(
            text = "帮助与反馈",
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

        Surface(
            color = Color(0xFFF0F1F2),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    loading -> {
                        HamburgerSupportStatusText(text = "正在同步消息...")
                    }
                    loadFailed -> {
                        HamburgerSupportStatusText(text = "消息同步失败")
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
                    messages.isEmpty() -> {
                        HamburgerSupportStatusText(text = "提交反馈后，客服回复和处理进展会在这里显示。")
                    }
                    else -> {
                        messages.forEach { message ->
                            HamburgerSupportMessageBubble(message = message)
                        }
                    }
                }
            }
        }

        ComposerChromeRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            addButtonSize = 36.dp,
            addIconSize = 25.dp,
            sendButtonSize = 36.dp,
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
                            text = if (selectedImages.isNotEmpty()) "补充图片说明" else "输入反馈内容",
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
private fun HamburgerSupportMessageBubble(message: SessionApi.SupportMessage) {
    val isUser = message.senderType == "user"
    val timestamp = formatSupportMessageTime(message.createdAt)
    val body = message.body.orEmpty()
    val imageUrls = message.imageUrls.orEmpty()
    val bodyColor = if (isUser) Color.White else Color(0xFF111111)
    val linkColor = if (isUser) Color.White else Color(0xFF1463D9)
    val renderedBody = remember(body, linkColor) { buildSupportLinkedText(body, linkColor) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(
                text = if (isUser) "我" else "客服",
                color = Color(0xFF8A8E96),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
            if (body.isNotBlank()) {
                Surface(
                    color = if (isUser) Color(0xFF111111) else Color.White,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomEnd = if (isUser) 6.dp else 18.dp,
                        bottomStart = if (isUser) 18.dp else 6.dp
                    ),
                    border = if (isUser) null else BorderStroke(0.7.dp, Color(0xFFE1E4E8))
                ) {
                    SelectionContainer {
                        Text(
                            text = renderedBody,
                            color = bodyColor,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
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
                text = if (loadUnavailable) "已过期" else "图片",
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
            senderType = "admin",
            body = "收到，客服已经帮您同步了一次。您重新打开会员中心看看，如果还不对，把截图发过来。也可以看 https://api.nongjiqiancha.cn/help。",
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
            onInputChange = {},
            onAddClick = {},
            onRemoveImage = {},
            onSend = {},
            onRetry = {},
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
                text = "是否删除所有历史对话",
                color = Color(0xFF111111),
                fontSize = 20.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "会清空聊天记录、问诊摘要和长期记忆。会员、加油包、礼品卡和帮助与反馈不会删除。此操作不可恢复。",
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
                            text = if (deleting) "删除中" else "确定",
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
private fun HamburgerRedeemCodePage(
    onPendingAction: (String) -> Unit
) {
    HamburgerRedeemCodeContent(
        onPendingAction = onPendingAction,
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
    modifier: Modifier = Modifier,
    initialCode: String = ""
) {
    var redeemCode by remember(initialCode) { mutableStateOf(initialCode) }
    val canRedeem = redeemCode.isNotBlank()
    fun submitRedeem() {
        if (canRedeem) {
            onPendingAction("礼品卡兑换暂不可用")
        }
    }
    Column(modifier = modifier) {
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
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                            onValueChange = { next -> redeemCode = next },
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
                            )
                        )
                    }
                }
                Surface(
                    color = if (canRedeem) Color(0xFF111111) else Color(0xFFE3E5E8),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .clickable(
                            enabled = canRedeem,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { submitRedeem() }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "兑换",
                            color = if (canRedeem) Color.White else Color(0xFF8A8E96),
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

@Composable
private fun HamburgerRedeemSuccessCard(
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
                text = "兑换成功",
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
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
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
internal fun HamburgerRedeemSuccessCardPreview() {
    var visible by remember { mutableStateOf(true) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 136.dp),
        contentAlignment = Alignment.Center
    ) {
        if (visible) {
            HamburgerRedeemSuccessCard(
                onConfirm = { visible = false },
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        } else {
            Text(
                text = "已关闭",
                color = Color(0xFF70747B),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun HamburgerAccountGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
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
    value: String,
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
            .padding(start = 20.dp, end = 16.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(0xFF111111),
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = Color(0xFF8A8E96),
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
        HamburgerChevronIcon(
            tint = Color(0xFFAAAEB5),
            modifier = Modifier
                .padding(start = 10.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun HamburgerAccountActionRow(
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
            color = Color(0xFFD24646),
            fontSize = 17.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HamburgerMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(0.6.dp, Color(0xFFEDEFF2)),
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
        thickness = 1.dp,
        color = Color(0xFFF1F2F4),
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
    subtitle: String? = null,
    destructive: Boolean = false,
    showBadge: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 17.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HamburgerMenuGlyph(
            icon = icon,
            tint = if (destructive) Color(0xFFD24646) else Color(0xFF111111),
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier
                .padding(start = 13.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = if (destructive) Color(0xFFD24646) else Color(0xFF111111),
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = if (destructive) Color(0xFFD24646).copy(alpha = 0.72f) else Color(0xFF686C74),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
                    .padding(start = 10.dp)
                    .size(16.dp)
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
    Logout
}

private enum class HamburgerMenuPage {
    Menu,
    Membership,
    Redeem,
    Account,
    Support,
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

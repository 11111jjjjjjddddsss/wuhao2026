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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onPlaceholderClick: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var noticeText by remember(visible) { mutableStateOf<String?>(null) }
    var page by remember(visible) { mutableStateOf(HamburgerMenuPage.Menu) }
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
        if (page != HamburgerMenuPage.Menu) {
            page = HamburgerMenuPage.Menu
        } else {
            onDismiss()
        }
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
                        if (targetState != HamburgerMenuPage.Menu) {
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
                                onOpenServiceAgreement = {
                                    performButtonHaptic()
                                    page = HamburgerMenuPage.ServiceAgreement
                                },
                                onOpenPrivacyPolicy = {
                                    performButtonHaptic()
                                    page = HamburgerMenuPage.PrivacyPolicy
                                },
                                onOpenRiskNotice = {
                                    performButtonHaptic()
                                    page = HamburgerMenuPage.RiskNotice
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
                                onPendingAction = ::showNotice
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
                        HamburgerMenuPage.ServiceAgreement -> {
                            HamburgerServiceAgreementPage()
                        }
                        HamburgerMenuPage.PrivacyPolicy -> {
                            HamburgerPrivacyPolicyPage()
                        }
                        HamburgerMenuPage.RiskNotice -> {
                            HamburgerRiskNoticePage()
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .statusBarsPadding()
                        .padding(start = 18.dp, top = 10.dp)
                        .size(50.dp)
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
                            modifier = Modifier.size(24.dp)
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
                .padding(horizontal = 28.dp),
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
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
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
    onOpenServiceAgreement: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenRiskNotice: () -> Unit,
    onCheckUpdate: () -> Unit,
    onPlaceholderClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 78.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HamburgerMenuGroup {
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Membership,
                title = "会员中心",
                subtitle = "套餐、额度和加油包",
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
                icon = HamburgerMenuIcon.Update,
                title = "检查更新",
                onClick = onCheckUpdate
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Redeem,
                title = "礼品卡",
                onClick = onOpenRedeem
            )
        }

        HamburgerMenuGroup {
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Document,
                title = "服务协议",
                onClick = onOpenServiceAgreement
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Privacy,
                title = "隐私政策",
                onClick = onOpenPrivacyPolicy
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Risk,
                title = "风险提示",
                subtitle = "AI 建议仅供参考",
                onClick = onOpenRiskNotice
            )
        }

        HamburgerMenuGroup {
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Logout,
                title = "退出登录",
                subtitle = "登录功能后续接入",
                destructive = true,
                onClick = { onPlaceholderClick("登录功能后续接入") }
            )
        }
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
private fun HamburgerServiceAgreementContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "服务协议",
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
            text = "更新日期：2026年5月17日\n生效日期：2026年5月17日\n服务提供者：北京农技千问科技有限公司\n联系邮箱：465989879@qq.com",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、特别提示",
            body = "农技千问是一款农业 AI 问诊与技术参考工具。你可以通过文字、图片或图文混合方式咨询农作物相关问题，系统会基于你提供的信息生成农业技术参考建议。AI 输出不构成绝对诊断、行政认定、农资质量鉴定、处方或强制性操作指令。"
        )
        HamburgerAgreementSection(
            title = "二、服务内容",
            body = "我们提供农业问题咨询、图片辅助分析、历史对话展示、会员权益、礼品卡、帮助与反馈、今日农情和版本更新等功能。具体功能可能根据产品迭代、法规要求、服务器状态或运营安排进行调整。"
        )
        HamburgerAgreementSection(
            title = "三、账号与使用规范",
            body = "你应合法、合理使用本服务，不得上传违法违规、侵权、虚假、有害或与农业咨询无关的内容，不得攻击、干扰、爬取、逆向工程或以异常方式消耗系统资源。因你提供的信息不完整、不准确或现场条件变化导致建议偏差的，需要你自行结合实际情况判断。"
        )
        HamburgerAgreementSection(
            title = "四、农业 AI 建议边界",
            body = "本服务的回答仅用于农业技术参考。涉及农药、肥料、种子、检疫、补贴、备案、登记、审定、质量争议、赔付或重大生产决策时，请以产品标签、官方平台、当地农业农村部门、检测机构、农技人员或其他有资质主体的意见为准。"
        )
        HamburgerAgreementSection(
            title = "五、用户内容与图片",
            body = "你保留对自己上传内容依法享有的权利。为提供问诊、图片识别、历史记录、客服反馈和安全风控等必要服务，你同意我们在必要范围内处理你提交的文字、图片和使用记录。请勿上传他人隐私、商业秘密或你无权使用的内容。"
        )
        HamburgerAgreementSection(
            title = "六、帮助与反馈",
            body = "你可以通过 App 内“帮助与反馈”提交问题、截图或图片，客服回复会在站内展示。该入口用于产品问题和服务沟通，不是紧急农情、灾害处置或即时人工诊断通道。"
        )
        HamburgerAgreementSection(
            title = "七、未成年人",
            body = "未成年人应在监护人指导下使用本服务。未满十四周岁的儿童使用本服务并提交个人信息时，应取得监护人同意。请勿上传未成年人照片、身份信息或其他无关敏感内容。"
        )
        HamburgerAgreementSection(
            title = "八、会员、次数与费用",
            body = "会员套餐、每日次数、加油包、礼品卡、优惠和支付规则以 App 页面、后端记录及实际支付结果为准。若后续接入支付，订单、退款、权益生效和异常处理将按页面展示规则、支付平台规则及法律规定执行。"
        )
        HamburgerAgreementSection(
            title = "九、服务变更与中断",
            body = "因系统维护、网络故障、第三方服务异常、模型服务波动、设备兼容、不可抗力或安全风控需要，服务可能出现延迟、中断、失败或内容展示异常。我们会在合理范围内尽力修复，但不承诺服务永久不间断或完全无误。"
        )
        HamburgerAgreementSection(
            title = "十、知识产权",
            body = "本 App 的界面、程序、文案、标识、服务逻辑和相关内容的知识产权归我们或相关权利人所有。未经许可，你不得复制、改编、传播、抓取、反向工程或用于商业竞争目的。"
        )
        HamburgerAgreementSection(
            title = "十一、隐私与个人信息保护",
            body = "我们会按照法律法规和隐私政策处理你的个人信息。隐私政策将说明我们如何收集、使用、存储、保护和对外提供信息，以及你如何行使查询、更正、删除、注销等权利。"
        )
        HamburgerAgreementSection(
            title = "十二、协议更新",
            body = "我们可能根据产品变化、法律法规或运营需要更新本协议。更新后会在 App 内展示新版协议；如更新涉及你的重要权利义务，我们会以合理方式提示。你继续使用服务即表示接受更新后的协议。"
        )
        HamburgerAgreementSection(
            title = "十三、法律适用与争议解决",
            body = "本协议适用中华人民共和国法律。因本协议或服务产生争议，双方应先友好协商；协商不成的，任何一方可依法向有管辖权的人民法院提起诉讼。"
        )
        HamburgerAgreementSection(
            title = "十四、联系我们",
            body = "如果你对本协议、服务使用或权益处理有问题，可以通过邮箱 465989879@qq.com 联系我们。"
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
private fun HamburgerPrivacyPolicyContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HamburgerLegalPageTitle("隐私政策")
        Text(
            text = "更新日期：2026年5月17日\n生效日期：2026年5月17日\n服务提供者：北京农技千问科技有限公司\n联系邮箱：465989879@qq.com",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、我们如何收集和使用信息",
            body = "为提供农业 AI 问诊、图片分析、历史对话、会员权益、帮助与反馈、检查更新等功能，我们会在必要范围内处理你主动提交的文字、图片、反馈内容，以及服务运行产生的必要记录。"
        )
        HamburgerAgreementSection(
            title = "二、你主动提供的信息",
            body = "你在问诊、帮助与反馈或其他功能中输入的文字、上传的图片、选择的附件、发送的反馈和客服会话内容，会用于生成农业技术参考建议、展示历史记录、处理问题反馈和改进服务。请勿上传他人隐私、身份证件、银行卡、商业秘密或你无权使用的图片。"
        )
        HamburgerAgreementSection(
            title = "三、账号、设备和使用记录",
            body = "当前 App 主要使用本机生成的用户标识维持对话、额度和反馈归属；后续接入手机号或其他账号体系时，会按页面提示处理对应账号信息。服务器可能记录请求时间、接口路径、网络地址、错误日志、版本信息、额度消费和模型调用统计，用于安全风控、故障排查、成本核算和服务改进。"
        )
        HamburgerAgreementSection(
            title = "四、图片、相机和相册",
            body = "当前照片入口使用 Android 系统 Photo Picker，你只会授权本次主动选择的图片；当前拍照入口调用外部相机写入 App 创建的临时文件，并在导入后生成 App 私有图片副本用于上传和预览。当前 App 不申请相册读取权限，也不申请相机权限。"
        )
        HamburgerAgreementSection(
            title = "五、定位和地区信息",
            body = "当前 Android 客户端不申请定位权限，也不会读取 GPS 精确位置。后端可能基于网络情况获得粗略地区，或在未来由你主动选择 / 填写地区，用于让农业建议更贴近当地作物、气候和农时。若后续需要精确定位，会在使用前另行征得授权。"
        )
        HamburgerAgreementSection(
            title = "六、当前权限说明",
            body = "当前 App 声明的权限包括：网络访问，用于连接后端、上传图片、流式获取回答和检查更新；网络状态，用于判断网络可用性；安装未知应用相关权限，仅用于你主动点击“立即更新”后下载并调起系统安装页。当前不申请定位权限、相机权限、录音权限、通讯录权限、短信权限或读写存储权限。"
        )
        HamburgerAgreementSection(
            title = "七、本地存储和缓存",
            body = "App 会在本机保存必要运行缓存，例如本机用户标识、聊天窗口快照、未发送文字草稿、待上传图片副本、图片预览缓存和下载的更新 APK 缓存。你清除 App 数据后，这些本地缓存会被系统删除；已同步到后端的业务记录仍以后端保存规则为准。"
        )
        HamburgerAgreementSection(
            title = "八、信息共享和第三方服务",
            body = "为实现服务，我们可能使用云服务器、数据库、对象存储、日志服务、模型服务、支付服务或系统组件处理必要信息。我们不会出售你的个人信息；对外提供信息时会遵循合法、正当、必要原则，并要求相关服务方按约定保护数据安全。"
        )
        HamburgerAgreementSection(
            title = "九、保存期限和安全措施",
            body = "我们会在实现服务目的所需的最短合理期限内保存信息。当前成功完成的问答轮次会用于历史恢复和后续批量抽取，相关保存策略以后端真实规则为准。我们会采取访问控制、传输加密、日志审计等合理措施保护数据，但互联网环境下无法保证绝对安全。"
        )
        HamburgerAgreementSection(
            title = "十、你的权利",
            body = "你可以通过 App 内功能或联系邮箱 465989879@qq.com，要求查询、更正、删除相关信息，或咨询账号注销、撤回授权和投诉处理方式。具体处理会在核验身份和确认合法可行后进行。"
        )
        HamburgerAgreementSection(
            title = "十一、未成年人保护",
            body = "未成年人应在监护人指导下使用本服务。若你是未成年人的监护人，并发现未成年人向我们提交了不适当信息，可以通过联系邮箱要求处理。"
        )
        HamburgerAgreementSection(
            title = "十二、政策更新",
            body = "我们可能根据产品变化、法律法规或运营需要更新本政策。更新后会在 App 内展示新版政策；如涉及重要权利义务变化，我们会以合理方式提示。"
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
            text = "更新日期：2026年5月17日\n生效日期：2026年5月17日",
            color = Color(0xFF5F646D),
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
        HamburgerAgreementSection(
            title = "一、AI 建议仅供参考",
            body = "农技千问会根据你提供的文字、图片和上下文生成农业技术参考建议。由于图片角度、清晰度、作物品种、地区、天气、土壤、用药史和管理条件可能不完整，AI 回答可能存在偏差、遗漏或误判。"
        )
        HamburgerAgreementSection(
            title = "二、不能替代现场诊断",
            body = "病虫害、药害、肥害、缺素、冻害、旱涝、根系问题和生理性障碍可能表现相似。重要生产决策前，请结合田间现场、当地农技人员、检测机构、产品标签和官方信息综合判断。"
        )
        HamburgerAgreementSection(
            title = "三、图片/文字输入风险",
            body = "一张图或少量描述不能替代现场调查；图片只反映拍摄瞬间和局部。不要只凭图片直接大面积用药、毁苗、停水停肥、索赔或投诉；重要操作前应补充现场信息并线下复核。"
        )
        HamburgerAgreementSection(
            title = "四、农药、肥料和农资使用风险",
            body = "涉及农药、肥料、调节剂、种子、基质、设备或其他农资时，请优先遵守产品标签、登记信息、当地法规和安全间隔期要求。不要仅凭 AI 建议超范围、超剂量、混配或在不适宜天气条件下使用农资。"
        )
        HamburgerAgreementSection(
            title = "五、官方事项以主管部门为准",
            body = "涉及检疫、补贴、备案、登记、审定、证件真伪、质量争议、赔付、处罚或行政流程的内容，AI 只能提供查询路径和一般性说明，不能替代主管部门、司法机关、检测机构或专业人员的结论。"
        )
        HamburgerAgreementSection(
            title = "六、时效和联网信息风险",
            body = "农业政策、天气、价格、病虫害预警和农资登记状态可能变化。即使系统使用联网搜索，也可能受到来源更新延迟、地区差异或信息质量影响。强时效事项请以官方或当地最新发布为准。"
        )
        HamburgerAgreementSection(
            title = "七、图片和历史上下文限制",
            body = "系统通常只重点参考当前轮和上一轮图片，更早图片可能不再进入模型上下文。若病斑、叶背、根系、果实、虫体或田间环境没有拍清，建议补充更清晰图片和关键背景。"
        )
        HamburgerAgreementSection(
            title = "八、生产损失风险",
            body = "农业生产受天气、土壤、水肥、品种、病虫害、人工操作和市场价格等多因素影响。使用本服务不代表保证防治效果、产量、品质、收益或避免损失。"
        )
        HamburgerAgreementSection(
            title = "九、安全操作提醒",
            body = "进行施药、施肥、修剪、采收、设备操作或其他现场作业时，请遵守安全规范，佩戴必要防护用品，避免污染水源、伤害人员或影响农产品安全。"
        )
        HamburgerAgreementSection(
            title = "十、紧急情况",
            body = "发生大面积突发病害、药害、灾害、食品安全或人身安全风险时，请及时联系当地农业农村部门、植保站、应急或监管机构以及线下专业人员，不要等待 AI 回复。"
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
            HamburgerMenuGroup {
                HamburgerMenuRow(
                    icon = HamburgerMenuIcon.Membership,
                    title = "会员中心",
                    subtitle = "套餐、额度和加油包",
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
                    icon = HamburgerMenuIcon.Update,
                    title = "检查更新",
                    onClick = {}
                )
                HamburgerMenuDivider()
                HamburgerMenuRow(
                    icon = HamburgerMenuIcon.Redeem,
                    title = "礼品卡",
                    onClick = {}
                )
            }
            HamburgerMenuGroup {
                HamburgerMenuRow(
                    icon = HamburgerMenuIcon.Document,
                    title = "服务协议",
                    onClick = {}
                )
                HamburgerMenuDivider()
                HamburgerMenuRow(
                    icon = HamburgerMenuIcon.Privacy,
                    title = "隐私政策",
                    onClick = {}
                )
                HamburgerMenuDivider()
                HamburgerMenuRow(
                    icon = HamburgerMenuIcon.Risk,
                    title = "风险提示",
                    subtitle = "AI 建议仅供参考",
                    onClick = {}
                )
            }
            HamburgerMenuGroup {
                HamburgerMenuRow(
                    icon = HamburgerMenuIcon.Logout,
                    title = "退出登录",
                    subtitle = "登录功能后续接入",
                    destructive = true,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun HamburgerAccountManagementPage(
    onPendingAction: (String) -> Unit
) {
    HamburgerAccountManagementContent(
        onPendingAction = onPendingAction,
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
    modifier: Modifier = Modifier
) {
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
                value = "未绑定",
                onClick = { onPendingAction("手机号登录后续接入") }
            )
        }

        HamburgerAccountGroup(
            modifier = Modifier.padding(top = 22.dp)
        ) {
            HamburgerAccountActionRow(
                title = "退出设备",
                onClick = { onPendingAction("登录功能后续接入") }
            )
        }

        HamburgerAccountGroup(
            modifier = Modifier.padding(top = 18.dp)
        ) {
            HamburgerAccountActionRow(
                title = "注销账号",
                onClick = { onPendingAction("账号注销后续接入") }
            )
        }
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
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<SessionApi.SupportMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var loadTick by remember { mutableStateOf(0) }
    var attachmentMenuVisible by remember { mutableStateOf(false) }
    val selectedImages = remember { mutableStateListOf<ComposerImageAttachment>() }
    var pendingCameraImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraImageGalleryBacked by rememberSaveable { mutableStateOf(false) }
    var pendingCameraImageTemporaryFilePath by rememberSaveable { mutableStateOf<String?>(null) }

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
            SessionApi.markSupportRead {
                if (it) {
                    messages = messages.map { message ->
                        if (message.senderType == "user" || message.readByUserAt != null) {
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
        val body = inputText.trim()
        val imageSnapshot = selectedImages.take(4)
        if ((body.isEmpty() && imageSnapshot.isEmpty()) || sending) return
        if (!SessionApi.hasBackendConfigured()) {
            onPendingAction(SUPPORT_SEND_FAILED_HINT)
            return
        }
        if (body.length > SUPPORT_MESSAGE_MAX_CHARS) {
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
                inputText = ""
                selectedImages.removeAll(imageSnapshot.toSet())
                scope.launch(Dispatchers.IO) {
                    imageSnapshot.forEach(context::deleteComposerImageAttachment)
                }
                loadFailed = false
                messages = messages + sent
                SessionApi.markSupportRead {
                    onConversationChanged()
                }
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
            inputText = inputText,
            selectedImages = selectedImages,
            loading = loading,
            loadFailed = loadFailed,
            sending = sending,
            onInputChange = { next ->
                if (next.length <= SUPPORT_MESSAGE_MAX_CHARS) {
                    inputText = next
                }
            },
            onAddClick = {
                if (!sending) {
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
    inputText: String,
    selectedImages: List<ComposerImageAttachment>,
    loading: Boolean,
    loadFailed: Boolean,
    sending: Boolean,
    onInputChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onRemoveImage: (ComposerImageAttachment) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val hasContent = inputText.trim().isNotEmpty() || selectedImages.isNotEmpty()
    val canSend = hasContent && !sending && inputText.length <= SUPPORT_MESSAGE_MAX_CHARS

    LaunchedEffect(messages.size, loading, loadFailed, selectedImages.size) {
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
                        HamburgerSupportStatusText(text = "把问题发给客服，回复会显示在这里。")
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
                        .heightIn(min = if (selectedImages.isNotEmpty()) 86.dp else 0.dp, max = 112.dp),
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
                        value = inputText,
                        onValueChange = onInputChange,
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
                        modifier = Modifier.fillMaxWidth()
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
                    Text(
                        text = body,
                        color = if (isUser) Color.White else Color(0xFF111111),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
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
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        previewImages.forEachIndexed { index, imageUrl ->
            HamburgerSupportMessageImageThumb(
                imageUrl = imageUrl,
                index = index,
                isUser = isUser,
                onClick = { previewIndex = index }
            )
        }
    }
    previewIndex?.let { index ->
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
                    onDismiss = { previewIndex = null }
                )
                Surface(
                    color = Color(0x99111111),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 34.dp, end = 22.dp)
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
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageUrl) {
        bitmap = withContext(Dispatchers.IO) {
            context.decodeChatImagePreview(imageUrl, targetSize = 360)
        }
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
                text = "图片",
                color = if (isUser) Color(0xFFD8DADF) else Color(0xFF777C85),
                fontSize = 13.sp,
                lineHeight = 18.sp,
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

@Composable
internal fun HamburgerSupportFeedbackPagePreview() {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA))
    ) {
        HamburgerSupportFeedbackContent(
            messages = listOf(
                SessionApi.SupportMessage(
                    id = 1,
                    senderType = "user",
                    body = "我这边会员权益好像没有刷新，麻烦帮我看一下。",
                    createdAt = System.currentTimeMillis() - 32L * 60L * 1000L
                ),
                SessionApi.SupportMessage(
                    id = 2,
                    senderType = "admin",
                    body = "收到，客服已经帮你同步了一次。你重新打开会员中心看看，如果还不对，把截图发过来。",
                    createdAt = System.currentTimeMillis() - 18L * 60L * 1000L
                )
            ),
            inputText = "我再试一下",
            selectedImages = emptyList(),
            loading = false,
            loadFailed = false,
            sending = false,
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
            onPendingAction("礼品卡功能后续接入")
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
        shape = RoundedCornerShape(22.dp),
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
            .heightIn(min = 64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 24.dp, end = 18.dp, top = 17.dp, bottom = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(0xFF111111),
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = Color(0xFF8A8E96),
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
        HamburgerChevronIcon(
            tint = Color(0xFFAAAEB5),
            modifier = Modifier
                .padding(start = 10.dp)
                .size(18.dp)
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
            .heightIn(min = 64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(0xFFD24646),
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HamburgerMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color(0xFFF0F1F2),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun HamburgerMenuDivider() {
    HorizontalDivider(
        thickness = 2.dp,
        color = Color(0xFFF8F9FA),
        modifier = Modifier.fillMaxWidth()
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
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HamburgerMenuGlyph(
            icon = icon,
            tint = if (destructive) Color(0xFFD24646) else Color(0xFF111111),
            modifier = Modifier.size(24.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = if (destructive) Color(0xFFD24646) else Color(0xFF111111),
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = if (destructive) Color(0xFFD24646).copy(alpha = 0.72f) else Color(0xFF686C74),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showBadge) {
            Surface(
                color = Color(0xFFE5484D),
                shape = CircleShape,
                modifier = Modifier.size(8.dp)
            ) {}
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
    ServiceAgreement,
    PrivacyPolicy,
    RiskNotice
}

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

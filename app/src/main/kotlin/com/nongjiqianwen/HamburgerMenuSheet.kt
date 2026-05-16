package com.nongjiqianwen

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HAMBURGER_PLACEHOLDER_HINT = "功能后续接入"
private const val HAMBURGER_PAGE_ENTER_MS = 180
private const val HAMBURGER_PAGE_EXIT_MS = 150
private const val SUPPORT_MESSAGE_MAX_CHARS = 2000

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
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    var noticeText by remember(visible) { mutableStateOf<String?>(null) }
    var page by remember(visible) { mutableStateOf(HamburgerMenuPage.Menu) }
    var supportSummary by remember(visible) { mutableStateOf<SessionApi.SupportSummary?>(null) }
    var supportRefreshTick by remember(visible) { mutableStateOf(0) }
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
        page = HamburgerMenuPage.Menu
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
                                    page = HamburgerMenuPage.Support
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
                                onPendingAction = ::showNotice,
                                onConversationChanged = {
                                    supportRefreshTick += 1
                                }
                            )
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
                                if (page != HamburgerMenuPage.Menu) {
                                    page = HamburgerMenuPage.Menu
                                } else {
                                    onDismiss()
                                }
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
            }
        }
    }
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
                title = "客服反馈",
                showBadge = supportUnread,
                onClick = onOpenSupport
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Update,
                title = "检查更新",
                onClick = { onPlaceholderClick(HAMBURGER_PLACEHOLDER_HINT) }
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
                onClick = { onPlaceholderClick(HAMBURGER_PLACEHOLDER_HINT) }
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Privacy,
                title = "隐私政策",
                onClick = { onPlaceholderClick(HAMBURGER_PLACEHOLDER_HINT) }
            )
            HamburgerMenuDivider()
            HamburgerMenuRow(
                icon = HamburgerMenuIcon.Risk,
                title = "风险提示",
                subtitle = "AI 建议仅供参考",
                onClick = { onPlaceholderClick(HAMBURGER_PLACEHOLDER_HINT) }
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
                    title = "客服反馈",
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
    onPendingAction: (String) -> Unit,
    onConversationChanged: () -> Unit
) {
    var messages by remember { mutableStateOf<List<SessionApi.SupportMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var loadTick by remember { mutableStateOf(0) }

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
        if (body.isEmpty() || sending) return
        if (body.length > SUPPORT_MESSAGE_MAX_CHARS) {
            onPendingAction("最多输入2000字")
            return
        }
        sending = true
        SessionApi.sendSupportMessage(body) { sent ->
            sending = false
            if (sent == null) {
                onPendingAction("发送失败，请稍后再试")
                return@sendSupportMessage
            }
            inputText = ""
            loadFailed = false
            messages = messages + sent
            SessionApi.markSupportRead {
                onConversationChanged()
            }
        }
    }

    HamburgerSupportFeedbackContent(
        messages = messages,
        inputText = inputText,
        loading = loading,
        loadFailed = loadFailed,
        sending = sending,
        onInputChange = { next ->
            if (next.length <= SUPPORT_MESSAGE_MAX_CHARS) {
                inputText = next
            }
        },
        onSend = ::sendMessage,
        onRetry = { loadTick += 1 },
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 18.dp)
    )
}

@Composable
private fun HamburgerSupportFeedbackContent(
    messages: List<SessionApi.SupportMessage>,
    inputText: String,
    loading: Boolean,
    loadFailed: Boolean,
    sending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val canSend = inputText.trim().isNotEmpty() && !sending && inputText.length <= SUPPORT_MESSAGE_MAX_CHARS

    LaunchedEffect(messages.size, loading, loadFailed) {
        delay(80)
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = modifier) {
        Text(
            text = "客服反馈",
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
            text = "这里会保留你和客服的历史消息。",
            color = Color(0xFF6D7178),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
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
                        HamburgerSupportStatusText(text = "把问题发给我们，客服回复后会显示在这里。")
                    }
                    else -> {
                        messages.forEach { message ->
                            HamburgerSupportMessageBubble(message = message)
                        }
                    }
                }
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(0.8.dp, Color(0xFFE1E4E8)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp, max = 108.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = "输入反馈内容",
                            color = Color(0xFF9AA0A8),
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
                Surface(
                    color = if (canSend) Color(0xFF111111) else Color(0xFFE3E5E8),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.clickable(
                        enabled = canSend,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSend
                    )
                ) {
                    Text(
                        text = if (sending) "发送中" else "发送",
                        color = if (canSend) Color.White else Color(0xFF8A8E96),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp)
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
private fun HamburgerSupportMessageBubble(message: SessionApi.SupportMessage) {
    val isUser = message.senderType == "user"
    val timestamp = formatSupportMessageTime(message.createdAt)
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
                    text = message.body.orEmpty(),
                    color = if (isUser) Color.White else Color(0xFF111111),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
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
                    body = "收到，我们已经帮你同步了一次。你重新打开会员中心看看，如果还不对，把截图发过来。",
                    createdAt = System.currentTimeMillis() - 18L * 60L * 1000L
                )
            ),
            inputText = "我再试一下",
            loading = false,
            loadFailed = false,
            sending = false,
            onInputChange = {},
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
            .verticalScroll(rememberScrollState())
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

        HamburgerAccountGroup(
            modifier = Modifier.padding(top = 28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFFAFBFC),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.8.dp, Color(0xFFE1E4E8)),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = redeemCode,
                            onValueChange = { next -> redeemCode = next },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color(0xFF111111),
                                fontSize = 17.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (canRedeem) {
                                        onPendingAction("礼品卡功能后续接入")
                                    }
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Surface(
                    color = if (canRedeem) Color(0xFF111111) else Color(0xFFE3E5E8),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.clickable(
                        enabled = canRedeem,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onPendingAction("礼品卡功能后续接入") }
                    )
                ) {
                    Text(
                        text = "兑换",
                        color = if (canRedeem) Color.White else Color(0xFF8A8E96),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
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
    Support
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

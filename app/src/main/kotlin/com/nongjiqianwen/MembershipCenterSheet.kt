package com.nongjiqianwen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.util.Locale
import kotlinx.coroutines.delay

internal enum class MembershipLoadState { Idle, Loading, Loaded, Failed }

private const val MEMBERSHIP_SCRIM_ENTER_MS = 80
private const val MEMBERSHIP_SHEET_ENTER_MS = 165

@Composable
internal fun MembershipCenterBottomSheet(
    visible: Boolean,
    entitlement: SessionApi.EntitlementSnapshot?,
    loadState: MembershipLoadState,
    purchaseSuccessVisible: Boolean,
    userId: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onPaymentUnavailable: () -> Unit,
    onPurchaseSuccessConfirm: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(82f)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = MEMBERSHIP_SCRIM_ENTER_MS)),
            exit = ExitTransition.None
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = MEMBERSHIP_SHEET_ENTER_MS)
            ) + fadeIn(animationSpec = tween(durationMillis = MEMBERSHIP_SCRIM_ENTER_MS)),
            exit = ExitTransition.None,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val topSafeInset = with(density) {
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top).getTop(this).toDp()
                }
                val sheetMaxHeight = (maxHeight - topSafeInset).coerceAtMost(720.dp)

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetMaxHeight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MembershipCenterHeader(userId = userId, onDismiss = onDismiss)
                        MembershipCenterBody(
                            entitlement = entitlement,
                            loadState = loadState,
                            paymentNoticeResetKey = visible,
                            onPaymentUnavailable = onPaymentUnavailable
                        )
                    }
                }
            }
        }
        MembershipPurchaseSuccessCard(
            visible = visible && purchaseSuccessVisible,
            onConfirm = onPurchaseSuccessConfirm,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 42.dp)
        )
    }
}

@Composable
private fun MembershipCenterHeader(
    userId: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "会员中心",
                color = Color(0xFF111111),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "（ID ${compactUserId(userId)}）",
                color = Color(0xFF7B7F87),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, end = 12.dp)
            )
        }
        Surface(
            shape = CircleShape,
            color = Color(0xFFF5F6F7),
            border = BorderStroke(0.7.dp, Color(0xFFE2E4E8)),
            modifier = Modifier
                .size(42.dp)
                .semantics {
                    contentDescription = "关闭会员中心"
                    role = Role.Button
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onDismiss
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "×",
                    color = Color(0xFF202124),
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun MembershipCenterHeaderPreview(userId: String) {
    MembershipCenterHeader(
        userId = userId,
        onDismiss = {}
    )
}

@Composable
internal fun MembershipCenterBody(
    entitlement: SessionApi.EntitlementSnapshot?,
    loadState: MembershipLoadState,
    paymentNoticeResetKey: Any?,
    onPaymentUnavailable: () -> Unit
) {
    var paymentNoticeVisible by remember(paymentNoticeResetKey) { mutableStateOf(false) }
    LaunchedEffect(paymentNoticeVisible) {
        if (!paymentNoticeVisible) return@LaunchedEffect
        delay(1500)
        paymentNoticeVisible = false
    }
    MembershipQuotaSummary(
        entitlement = entitlement,
        loadState = loadState
    )
    val loadedEntitlement = entitlement.takeIf { loadState == MembershipLoadState.Loaded }
    if (paymentNoticeVisible) {
        MembershipInlineNotice(text = "支付功能暂不可用")
    }
    MembershipPlanSection(
        activeTier = entitlement.activeMembershipTier(loadState),
        upgradeRemaining = loadedEntitlement?.upgradeRemaining ?: 0,
        topupRemaining = loadedEntitlement?.topupRemaining ?: 0,
        onPaymentUnavailable = {
            paymentNoticeVisible = true
            onPaymentUnavailable()
        }
    )
    MembershipTopupCard(
        activeTier = entitlement.activeMembershipTier(loadState),
        topupRemaining = loadedEntitlement?.topupRemaining ?: 0,
        onPaymentUnavailable = {
            paymentNoticeVisible = true
            onPaymentUnavailable()
        }
    )
    MembershipRulesSection()
}

@Composable
private fun MembershipInlineNotice(text: String) {
    Surface(
        color = Color(0xFF111111),
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun MembershipPurchaseSuccessCard(
    visible: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 90)),
        exit = ExitTransition.None,
        modifier = modifier
            .fillMaxWidth()
            .zIndex(90f)
    ) {
        Surface(
            color = Color(0xFF111111),
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 18.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "订购成功",
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
                        .width(168.dp)
                        .height(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
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
}

@Composable
internal fun MembershipQuotaSummary(
    entitlement: SessionApi.EntitlementSnapshot?,
    loadState: MembershipLoadState
) {
    val tier = entitlement.activeMembershipTier(loadState)
    val tierName = membershipSummaryTierName(tier, loadState)
    val limit = membershipDailyLimit(tier)
    val dailyRemaining = entitlement?.dailyRemaining
    val tierSubText = membershipSummaryTierSubText(
        tier = tier,
        loadState = loadState,
        expireAtMs = entitlement?.tierExpireAt
    )
    val summaryTitleColor = Color(0xFF151515)
    val summaryValueColor = Color(0xFF5F636B)
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.7.dp, Color(0xFFE4E6EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "今日剩余",
                        color = summaryTitleColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (tier == "unknown") "--" else if (dailyRemaining == null) "-- / $limit" else "$dailyRemaining / $limit 次",
                        color = summaryValueColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Text(
                        text = tierName,
                        color = summaryTitleColor,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (tierSubText != null) {
                        Text(
                            text = tierSubText,
                            color = summaryValueColor,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MembershipPlanSectionPreview(
    activeTier: String,
    upgradeRemaining: Int = 0,
    topupRemaining: Int = 0
) {
    MembershipPlanSection(
        activeTier = activeTier,
        upgradeRemaining = upgradeRemaining,
        topupRemaining = topupRemaining,
        onPaymentUnavailable = {}
    )
}

@Composable
internal fun MembershipTopupCardPreview(
    activeTier: String,
    topupRemaining: Int
) {
    MembershipTopupCard(
        activeTier = activeTier,
        topupRemaining = topupRemaining,
        onPaymentUnavailable = {}
    )
}

@Composable
internal fun MembershipPaymentNoticePreview() {
    MembershipInlineNotice(text = "支付功能暂不可用")
}

@Composable
internal fun MembershipPurchaseSuccessPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp),
        contentAlignment = Alignment.Center
    ) {
        MembershipPurchaseSuccessCard(
            visible = true,
            onConfirm = {},
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}

@Composable
internal fun MembershipRulesPreview() {
    MembershipRulesSection()
}

@Composable
private fun MembershipExtraCountPill(text: String) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(0.7.dp, Color(0xFFE2E4E8)),
        modifier = Modifier
            .height(24.dp)
            .widthIn(max = 132.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color(0xFF666A72),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MembershipPlanSection(
    activeTier: String,
    upgradeRemaining: Int,
    topupRemaining: Int,
    onPaymentUnavailable: () -> Unit
) {
    val membershipSynced = activeTier != "unknown"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MembershipPlanSectionTitle(
            upgradeRemaining = upgradeRemaining,
            topupRemaining = topupRemaining
        )
        MembershipPlanCard(
            name = "Plus",
            price = "¥19.9/月",
            active = activeTier == "plus",
            highlights = listOf("每天25次问诊", "图文问题随时问", "记忆与上下文更强"),
            actionText = when (activeTier) {
                "unknown" -> "同步后开通"
                "plus" -> "当前套餐"
                "pro" -> "当前为 Pro"
                else -> "开通 Plus"
            },
            actionEnabled = membershipSynced && activeTier == "free",
            onActionClick = onPaymentUnavailable
        )
        MembershipPlanCard(
            name = "Pro",
            price = "¥29.9/月",
            badge = "推荐",
            active = activeTier == "pro",
            highlights = listOf("每天40次问诊", "复杂问题推理更强", "适合多作物、多地块复盘"),
            actionText = when (activeTier) {
                "unknown" -> "同步后开通"
                "plus" -> "升级 Pro"
                "pro" -> "当前套餐"
                else -> "开通 Pro"
            },
            actionEnabled = membershipSynced && activeTier != "pro",
            onActionClick = onPaymentUnavailable
        )
    }
}

@Composable
private fun MembershipPlanSectionTitle(
    upgradeRemaining: Int,
    topupRemaining: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "套餐",
            color = Color(0xFF111111),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 24.sp
            ),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .height(24.dp)
                .wrapContentHeight(Alignment.CenterVertically)
        )
        if (upgradeRemaining > 0 || topupRemaining > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (upgradeRemaining > 0) {
                    MembershipExtraCountPill(text = "升级补偿次数 ${upgradeRemaining}次")
                }
                if (topupRemaining > 0) {
                    MembershipExtraCountPill(text = "加油包 ${topupRemaining}次")
                }
            }
        }
    }
}

@Composable
private fun MembershipPlanCard(
    name: String,
    price: String,
    active: Boolean,
    highlights: List<String>,
    actionText: String,
    actionEnabled: Boolean,
    onActionClick: () -> Unit,
    badge: String? = null
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 0.9.dp,
            color = if (active) Color(0xFF111111) else Color(0xFFE2E4E8)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        color = Color(0xFF111111),
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (active) {
                        MembershipPill(text = "当前")
                    } else if (badge != null) {
                        MembershipPill(text = badge)
                    }
                }
                Text(
                    text = price,
                    color = Color(0xFF111111),
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                highlights.forEach { item ->
                    Text(
                        text = item,
                        color = Color(0xFF666A72),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            MembershipActionButton(
                text = actionText,
                enabled = actionEnabled,
                onClick = onActionClick
            )
        }
    }
}

@Composable
private fun MembershipTopupCard(
    activeTier: String,
    topupRemaining: Int,
    onPaymentUnavailable: () -> Unit
) {
    val hasActiveTopup = topupRemaining > 0
    val isPaidTier = activeTier == "plus" || activeTier == "pro"
    val canBuy = isPaidTier && !hasActiveTopup
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.9.dp, Color(0xFFE2E4E8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "加油包",
                        color = Color(0xFF111111),
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "额外80次",
                        color = Color(0xFF747881),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "¥6 / 80次",
                    color = Color(0xFF111111),
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
            Text(
                text = "Plus / Pro 可订购，永久有效，用完再续。",
                color = Color(0xFF666A72),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            MembershipActionButton(
                text = when {
                    hasActiveTopup && isPaidTier -> "用完再续"
                    hasActiveTopup -> "剩余次数可用"
                    canBuy -> "订购加油包"
                    else -> "Plus / Pro 可订购"
                },
                enabled = canBuy,
                onClick = onPaymentUnavailable
            )
        }
    }
}

@Composable
private fun MembershipPill(text: String) {
    Surface(
        color = Color(0xFFF2F3F5),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(0.7.dp, Color(0xFFE2E4E8))
    ) {
        Text(
            text = text,
            color = Color(0xFF4F535A),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MembershipActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = if (enabled) Color(0xFF111111) else Color(0xFFEDEFF2)
    val contentColor = if (enabled) Color.White else Color(0xFF8A8E96)
    Surface(
        color = color,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MembershipRulesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        HorizontalDivider(thickness = 0.7.dp, color = Color(0xFFE4E6EA))
        Text(
            text = "规则说明",
            color = Color(0xFF111111),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            color = Color(0xFFF8F9FA),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(0.7.dp, Color(0xFFE7E9ED)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MembershipRuleLine(
                    title = "Plus升级Pro",
                    body = "升级 Pro 后，Plus 剩余权益会自动折成补偿次数。"
                )
                HorizontalDivider(thickness = 0.7.dp, color = Color(0xFFE7E9ED))
                MembershipRuleLine(
                    title = "扣次顺序",
                    body = "每日额度 → 升级补偿 → 加油包。"
                )
            }
        }
    }
}

@Composable
private fun MembershipRuleLine(
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF111111),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            color = Color(0xFF686C74),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

private fun SessionApi.EntitlementSnapshot?.normalizedTier(): String =
    when (this?.tier?.trim()?.lowercase()) {
        "plus" -> "plus"
        "pro" -> "pro"
        else -> "free"
    }

private fun SessionApi.EntitlementSnapshot?.activeMembershipTier(loadState: MembershipLoadState): String =
    if (loadState != MembershipLoadState.Loaded) {
        "unknown"
    } else {
        normalizedTier()
    }

private fun membershipTierName(tier: String): String =
    when (tier) {
        "plus" -> "Plus"
        "pro" -> "Pro"
        "unknown" -> "--"
        else -> "Free"
    }

private fun membershipDailyLimit(tier: String): Int =
    when (tier) {
        "plus" -> 25
        "pro" -> 40
        else -> 6
    }

private fun membershipSummaryTierName(tier: String, loadState: MembershipLoadState): String =
    when (loadState) {
        MembershipLoadState.Loading -> "读取中"
        MembershipLoadState.Failed -> "未同步"
        else -> membershipTierName(tier)
    }

private fun membershipSummaryTierSubText(
    tier: String,
    loadState: MembershipLoadState,
    expireAtMs: Long?
): String? =
    when {
        loadState == MembershipLoadState.Loading || loadState == MembershipLoadState.Failed -> null
        tier == "free" -> "基础额度"
        tier == "plus" || tier == "pro" -> formatMembershipExpireDate(expireAtMs)?.let { "到期 $it" }
        else -> null
    }

private fun formatMembershipExpireDate(expireAtMs: Long?): String? {
    if (expireAtMs == null || expireAtMs <= 0L) return null
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    }.format(java.util.Date(expireAtMs))
}

internal fun compactUserId(userId: String): String {
    val normalized = userId.filter { it.isLetterOrDigit() }
    val compact = normalized.takeLast(8).ifBlank { "未生成" }
    return compact.uppercase(Locale.US)
}

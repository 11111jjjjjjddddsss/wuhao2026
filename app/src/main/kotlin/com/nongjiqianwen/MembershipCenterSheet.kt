package com.nongjiqianwen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

internal enum class MembershipLoadState { Idle, Loading, Loaded, Failed }

@Composable
internal fun MembershipCenterBottomSheet(
    visible: Boolean,
    entitlement: SessionApi.EntitlementSnapshot?,
    loadState: MembershipLoadState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onPaymentUnavailable: () -> Unit
) {
    var paymentNoticeVisible by remember(visible) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(82f)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = fadeOut(animationSpec = tween(durationMillis = 100))
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
                animationSpec = tween(durationMillis = 220)
            ) + fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 160)
            ) + fadeOut(animationSpec = tween(durationMillis = 120)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MembershipCenterHeader(onDismiss = onDismiss)
                    MembershipQuotaSummary(
                        entitlement = entitlement,
                        loadState = loadState
                    )
                    if (paymentNoticeVisible) {
                        MembershipInlineNotice(text = "支付暂未接入")
                    }
                    MembershipPlanSection(
                        activeTier = entitlement.activeMembershipTier(loadState),
                        onPaymentUnavailable = {
                            paymentNoticeVisible = true
                            onPaymentUnavailable()
                        }
                    )
                    MembershipTopupCard(
                        activeTier = entitlement.activeMembershipTier(loadState),
                        onPaymentUnavailable = {
                            paymentNoticeVisible = true
                            onPaymentUnavailable()
                        }
                    )
                    MembershipRulesSection()
                }
            }
        }
    }
}

@Composable
private fun MembershipCenterHeader(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "会员中心",
                color = Color(0xFF111111),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "次数、追问记忆和加油包",
                color = Color(0xFF73777F),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        Surface(
            shape = CircleShape,
            color = Color(0xFFF5F6F7),
            border = BorderStroke(0.7.dp, Color(0xFFE2E4E8)),
            modifier = Modifier
                .size(42.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "×",
                    color = Color(0xFF202124),
                    fontSize = 24.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
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
private fun MembershipQuotaSummary(
    entitlement: SessionApi.EntitlementSnapshot?,
    loadState: MembershipLoadState
) {
    val tier = entitlement.activeMembershipTier(loadState)
    val tierName = when (loadState) {
        MembershipLoadState.Loading -> "读取中"
        MembershipLoadState.Failed -> "未连接"
        else -> membershipTierName(tier)
    }
    val limit = membershipDailyLimit(tier)
    val dailyRemaining = entitlement?.dailyRemaining
    val expireText = formatMembershipExpireDate(entitlement?.tierExpireAt)
        ?: when (tier) {
            "unknown" -> "暂未连接后端"
            "free" -> "未开通会员"
            else -> "暂未读取到期时间"
        }
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.7.dp, Color(0xFFE4E6EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "今日剩余",
                    color = Color(0xFF747881),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Text(
                    text = if (tier == "unknown") "--" else if (dailyRemaining == null) "-- / $limit" else "$dailyRemaining / $limit 次",
                    color = Color(0xFF111111),
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "当前 $tierName",
                    color = Color(0xFF151515),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        loadState == MembershipLoadState.Loading -> "读取中"
                        loadState == MembershipLoadState.Failed -> "未连接"
                        tier == "free" -> "基础额度"
                        else -> "到期 $expireText"
                    },
                    color = Color(0xFF747881),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun MembershipPlanSection(
    activeTier: String,
    onPaymentUnavailable: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "套餐",
            color = Color(0xFF111111),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        MembershipPlanCard(
            name = "Plus",
            price = "¥19.9/月",
            active = activeTier == "plus",
            highlights = listOf("每天25次问诊", "参考最近6轮问答", "可购买加油包"),
            actionText = when (activeTier) {
                "plus" -> "续费 Plus"
                "pro" -> "Pro 已包含"
                else -> "开通 Plus"
            },
            actionEnabled = activeTier != "pro",
            onActionClick = onPaymentUnavailable
        )
        MembershipPlanCard(
            name = "Pro",
            price = "¥29.9/月",
            badge = "推荐",
            active = activeTier == "pro",
            highlights = listOf("每天40次问诊", "参考最近9轮问答", "适合多作物连续追问"),
            actionText = when (activeTier) {
                "plus" -> "升级 Pro"
                "pro" -> "续费 Pro"
                else -> "开通 Pro"
            },
            actionEnabled = true,
            onActionClick = onPaymentUnavailable
        )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        color = Color(0xFF111111),
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold
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
                    fontWeight = FontWeight.SemiBold
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
    onPaymentUnavailable: () -> Unit
) {
    val canBuy = activeTier == "plus" || activeTier == "pro"
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "加油包",
                        color = Color(0xFF111111),
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "永久有效，用完再买",
                        color = Color(0xFF747881),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                Text(
                    text = "¥6 / 100次",
                    color = Color(0xFF111111),
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "仅 Plus / Pro 可购买。同一时间只能有1个未用完加油包。",
                color = Color(0xFF666A72),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            MembershipActionButton(
                text = if (canBuy) "购买加油包" else "Plus / Pro 可买",
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(thickness = 0.7.dp, color = Color(0xFFE4E6EA))
        Text(
            text = "规则说明",
            color = Color(0xFF111111),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        MembershipRuleBlock(
            title = "Plus 升 Pro",
            body = "购买 Pro 后获得新的 Pro 月。Plus 剩余权益会折成升级补偿次数，不是补差价。"
        )
        MembershipRuleBlock(
            title = "补偿怎么算",
            body = "今天剩余 Plus 次数 + 剩余完整天数 × 25。补偿次数永久有效。"
        )
        MembershipRuleBlock(
            title = "加油包",
            body = "6元100次，仅 Plus / Pro 可买；同一时间只能有1个未用完加油包，用完再买。"
        )
        MembershipRuleBlock(
            title = "次数顺序",
            body = "回答完成后扣1次。先用当天次数，再用升级补偿，最后用加油包。"
        )
        Text(
            text = "当前支付功能暂未接入，本页先展示规则。AI回答仅供农业技术参考，不作绝对诊断。",
            color = Color(0xFF7A7E86),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.widthIn(max = 520.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
    }
}

@Composable
private fun MembershipRuleBlock(
    title: String,
    body: String
) {
    Surface(
        color = Color(0xFFF8F9FA),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.7.dp, Color(0xFFE7E9ED)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF111111),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                color = Color(0xFF686C74),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

private fun SessionApi.EntitlementSnapshot?.normalizedTier(): String =
    when (this?.tier?.trim()?.lowercase()) {
        "plus" -> "plus"
        "pro" -> "pro"
        else -> "free"
    }

private fun SessionApi.EntitlementSnapshot?.activeMembershipTier(loadState: MembershipLoadState): String =
    if (this == null && loadState != MembershipLoadState.Loaded) {
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

private fun membershipMemoryRounds(tier: String): Int =
    if (tier == "pro") 9 else 6

private fun formatMembershipExpireDate(expireAtMs: Long?): String? {
    if (expireAtMs == null || expireAtMs <= 0L) return null
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    }.format(java.util.Date(expireAtMs))
}

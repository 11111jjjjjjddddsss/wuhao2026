package com.nongjiqianwen

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val HAMBURGER_PLACEHOLDER_HINT = "功能后续接入"
private const val HAMBURGER_PAGE_ENTER_MS = 180
private const val HAMBURGER_PAGE_EXIT_MS = 150

@Composable
internal fun HamburgerMenuSheet(
    visible: Boolean,
    userId: String,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onOpenMembership: () -> Unit,
    onPlaceholderClick: (String) -> Unit
) {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    var noticeText by remember(visible) { mutableStateOf<String?>(null) }
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 22.dp, end = 22.dp, top = 36.dp, bottom = 34.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "农技千查",
                            color = Color(0xFF111111),
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

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
                            onClick = { showNotice(HAMBURGER_PLACEHOLDER_HINT) }
                        )
                    }

                    HamburgerMenuGroup {
                        HamburgerMenuRow(
                            icon = HamburgerMenuIcon.Feedback,
                            title = "客服反馈",
                            onClick = { showNotice(HAMBURGER_PLACEHOLDER_HINT) }
                        )
                        HamburgerMenuDivider()
                        HamburgerMenuRow(
                            icon = HamburgerMenuIcon.Update,
                            title = "检查更新",
                            onClick = { showNotice(HAMBURGER_PLACEHOLDER_HINT) }
                        )
                    }

                    HamburgerMenuGroup {
                        HamburgerMenuRow(
                            icon = HamburgerMenuIcon.Document,
                            title = "服务协议",
                            onClick = { showNotice(HAMBURGER_PLACEHOLDER_HINT) }
                        )
                        HamburgerMenuDivider()
                        HamburgerMenuRow(
                            icon = HamburgerMenuIcon.Privacy,
                            title = "隐私政策",
                            onClick = { showNotice(HAMBURGER_PLACEHOLDER_HINT) }
                        )
                        HamburgerMenuDivider()
                        HamburgerMenuRow(
                            icon = HamburgerMenuIcon.Risk,
                            title = "风险提示",
                            subtitle = "AI 建议仅供参考",
                            onClick = { showNotice(HAMBURGER_PLACEHOLDER_HINT) }
                        )
                    }

                    HamburgerMenuGroup {
                        HamburgerMenuRow(
                            icon = HamburgerMenuIcon.Logout,
                            title = "退出登录",
                            subtitle = "登录功能后续接入",
                            destructive = true,
                            onClick = { showNotice("登录功能后续接入") }
                        )
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
                        .padding(start = 22.dp, top = 8.dp)
                        .size(58.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                performButtonHaptic()
                                onDismiss()
                            }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        HamburgerBackIcon(
                            tint = Color(0xFF111111),
                            modifier = Modifier.size(28.dp)
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
            Text(
                text = "农技千查",
                color = Color(0xFF111111),
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
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
                    onClick = {}
                )
                HamburgerMenuDivider()
                HamburgerMenuRow(
                    icon = HamburgerMenuIcon.Update,
                    title = "检查更新",
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
private fun HamburgerMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color(0xFFF0F1F2),
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
private fun HamburgerMenuDivider() {
    HorizontalDivider(
        thickness = 2.dp,
        color = Color(0xFFF8F9FA),
        modifier = Modifier.padding(start = 64.dp)
    )
}

@Composable
private fun HamburgerMenuRow(
    icon: HamburgerMenuIcon,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        HamburgerMenuGlyph(
            icon = icon,
            tint = if (destructive) Color(0xFFD24646) else Color(0xFF111111),
            modifier = Modifier.size(26.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = if (destructive) Color(0xFFD24646) else Color(0xFF111111),
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = if (destructive) Color(0xFFD24646).copy(alpha = 0.72f) else Color(0xFF686C74),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private enum class HamburgerMenuIcon {
    Membership,
    Account,
    Update,
    Document,
    Privacy,
    Risk,
    Feedback,
    Logout
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
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.08f)
                    lineTo(w * 0.86f, h * 0.28f)
                    lineTo(w * 0.78f, h * 0.78f)
                    lineTo(w * 0.50f, h * 0.92f)
                    lineTo(w * 0.22f, h * 0.78f)
                    lineTo(w * 0.14f, h * 0.28f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, Offset(w * 0.36f, h * 0.55f), Offset(w * 0.64f, h * 0.55f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.50f, h * 0.41f), Offset(w * 0.50f, h * 0.69f), strokeWidth, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.Account -> {
                drawCircle(tint, radius = w * 0.17f, center = Offset(w * 0.50f, h * 0.30f), style = stroke)
                drawArc(
                    color = tint,
                    startAngle = 204f,
                    sweepAngle = 132f,
                    useCenter = false,
                    topLeft = Offset(w * 0.14f, h * 0.46f),
                    size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.48f),
                    style = stroke
                )
            }
            HamburgerMenuIcon.Update -> {
                val updateStrokeWidth = strokeWidth * 1.05f
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
                    topLeft = Offset(w * 0.14f, h * 0.14f),
                    size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.72f),
                    style = updateStroke
                )
                drawArc(
                    color = tint,
                    startAngle = 10f,
                    sweepAngle = 148f,
                    useCenter = false,
                    topLeft = Offset(w * 0.14f, h * 0.14f),
                    size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.72f),
                    style = updateStroke
                )
                val topTip = Offset(w * 0.84f, h * 0.34f)
                drawLine(tint, topTip, Offset(w * 0.84f, h * 0.16f), updateStrokeWidth, cap = StrokeCap.Round)
                drawLine(tint, topTip, Offset(w * 0.66f, h * 0.34f), updateStrokeWidth, cap = StrokeCap.Round)
                val bottomTip = Offset(w * 0.16f, h * 0.66f)
                drawLine(tint, bottomTip, Offset(w * 0.34f, h * 0.66f), updateStrokeWidth, cap = StrokeCap.Round)
                drawLine(tint, bottomTip, Offset(w * 0.16f, h * 0.84f), updateStrokeWidth, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.Document -> {
                val path = Path().apply {
                    moveTo(w * 0.23f, h * 0.12f)
                    lineTo(w * 0.63f, h * 0.12f)
                    lineTo(w * 0.80f, h * 0.30f)
                    lineTo(w * 0.80f, h * 0.88f)
                    lineTo(w * 0.23f, h * 0.88f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, Offset(w * 0.34f, h * 0.46f), Offset(w * 0.68f, h * 0.46f), strokeWidth, cap = StrokeCap.Round)
                drawLine(tint, Offset(w * 0.34f, h * 0.63f), Offset(w * 0.68f, h * 0.63f), strokeWidth, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.Privacy -> {
                val shackle = Path().apply {
                    moveTo(w * 0.32f, h * 0.43f)
                    lineTo(w * 0.32f, h * 0.34f)
                    cubicTo(
                        w * 0.32f, h * 0.12f,
                        w * 0.68f, h * 0.12f,
                        w * 0.68f, h * 0.34f
                    )
                    lineTo(w * 0.68f, h * 0.43f)
                }
                drawPath(shackle, tint, style = stroke)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.20f, h * 0.42f),
                    size = androidx.compose.ui.geometry.Size(w * 0.60f, h * 0.44f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, h * 0.08f),
                    style = stroke
                )
                drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.62f))
                drawLine(tint, Offset(w * 0.50f, h * 0.67f), Offset(w * 0.50f, h * 0.74f), strokeWidth, cap = StrokeCap.Round)
            }
            HamburgerMenuIcon.Risk -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.12f)
                    lineTo(w * 0.88f, h * 0.84f)
                    lineTo(w * 0.12f, h * 0.84f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, Offset(w * 0.5f, h * 0.37f), Offset(w * 0.5f, h * 0.58f), strokeWidth, cap = StrokeCap.Round)
                drawCircle(tint, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.70f))
            }
            HamburgerMenuIcon.Feedback -> {
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.22f)
                    lineTo(w * 0.82f, h * 0.22f)
                    lineTo(w * 0.82f, h * 0.66f)
                    lineTo(w * 0.56f, h * 0.66f)
                    lineTo(w * 0.42f, h * 0.84f)
                    lineTo(w * 0.42f, h * 0.66f)
                    lineTo(w * 0.18f, h * 0.66f)
                    close()
                }
                drawPath(path, tint, style = stroke)
                drawLine(tint, Offset(w * 0.34f, h * 0.44f), Offset(w * 0.66f, h * 0.44f), strokeWidth, cap = StrokeCap.Round)
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

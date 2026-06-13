package com.nongjiqianwen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun LoginGate(
    onPrivacyAccepted: () -> Unit,
    content: @Composable () -> Unit
) {
    var loggedIn by remember { mutableStateOf(IdManager.isLoggedIn()) }
    DisposableEffect(Unit) {
        val removeListener = SessionApi.addAuthInvalidListener {
            loggedIn = false
        }
        onDispose { removeListener() }
    }
    fun handlePrivacyAccepted() {
        onPrivacyAccepted()
        loggedIn = IdManager.isLoggedIn()
    }
    if (loggedIn) {
        content()
    } else {
        LoginScreen(
            onPrivacyAccepted = ::handlePrivacyAccepted,
            onLoginSuccess = { loggedIn = true }
        )
    }
}

@Composable
private fun LoginScreen(
    onPrivacyAccepted: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    LaunchedEffect(Unit) {
        LaunchUiGate.chatReady = true
    }

    var phone by remember { mutableStateOf("") }
    val context = LocalContext.current
    var agreed by remember { mutableStateOf(PrivacyConsentStore.isAccepted(context)) }
    val fusionSmsLoginEnabled = BuildConfig.ENABLE_FUSION_SMS_LOGIN
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var legalPage by remember { mutableStateOf<LoginLegalPage?>(null) }
    val agreementText = remember { buildLoginAgreementText() }
    DisposableEffect(Unit) {
        onDispose {
            FusionOneLoginClient.cancelActiveScene("login_screen_dispose")
        }
    }

    fun acceptAgreementIfNeeded() {
        if (!PrivacyConsentStore.isAccepted(context)) {
            onPrivacyAccepted()
        }
    }

    fun requireAgreement(): Boolean {
        if (!agreed) {
            message = "请先同意服务协议和隐私政策"
            return false
        }
        acceptAgreementIfNeeded()
        return true
    }

    fun startFusionSmsLogin(activity: Activity) {
        AppCrashReporter.setAuthStage("auth.fusion_sms_start")
        busy = true
        message = "正在打开验证码登录"
        SessionApi.reportAuthClientLog(
            level = "info",
            event = "auth.fusion_sms_start_requested",
            message = "fusion sms login requested",
            attrs = mapOf("stage" to "login_screen_sms_start")
        )
        FusionOneLoginClient.startSmsLogin(
            activity = activity,
            verificationPhone = phone.takeIf(::isValidMainlandPhone)
        ) { ok, error ->
            busy = false
            if (ok) {
                onLoginSuccess()
            } else {
                message = error ?: "融合认证未完成，请稍后再试"
            }
        }
    }

    Surface(color = Color(0xFFF7F8FA), modifier = Modifier.fillMaxSize()) {
        legalPage?.let { page ->
            LoginLegalDialog(
                page = page,
                onDismiss = { legalPage = null }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LoginBrandLogo(Modifier.width(50.dp))
                    Text(
                        text = "农技千查",
                        color = Color(0xFF111111),
                        fontSize = 40.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(50.dp))
                }
                Spacer(Modifier.height(32.dp))

                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                    singleLine = true,
                    label = { Text("手机号") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (!requireAgreement()) {
                            return@Button
                        }
                        if (!isValidMainlandPhone(phone)) {
                            message = "请输入正确的手机号"
                            return@Button
                        }
                        if (!fusionSmsLoginEnabled) {
                            message = "当前安装包未开启融合认证短信，请安装最新测试包"
                            return@Button
                        }
                        val activity = context.findActivity()
                        if (activity == null) {
                            SessionApi.reportAuthClientLog(
                                level = "warn",
                                event = "auth.fusion_activity_unavailable",
                                message = "fusion login activity unavailable",
                                attrs = mapOf("stage" to "sms_button_click")
                            )
                            message = "融合认证暂不可用，请稍后再试"
                            return@Button
                        }
                        startFusionSmsLogin(activity)
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("验证码登录", fontSize = 17.sp, letterSpacing = 0.sp)
                }

                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoginAgreementCheckbox(
                            checked = agreed,
                            onCheckedChange = {
                                agreed = it
                                if (it) {
                                    message = null
                                    acceptAgreementIfNeeded()
                                }
                            },
                        )
                        Spacer(Modifier.size(7.dp))
                        ClickableText(
                            text = agreementText,
                            modifier = Modifier.weight(1f, fill = false),
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF575D66),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.sp
                            ),
                            softWrap = true,
                            maxLines = 2,
                            onClick = { offset ->
                                agreementText.getStringAnnotations(start = offset, end = offset)
                                    .firstOrNull()
                                    ?.let { annotation ->
                                        when (annotation.tag) {
                                            "service" -> legalPage = LoginLegalPage.ServiceAgreement
                                            "privacy" -> legalPage = LoginLegalPage.PrivacyPolicy
                                        }
                                    }
                            }
                        )
                    }
                }
                message?.let {
                    val positive = it.contains("已发送") || it.startsWith("正在")
                    val noticeShape = RoundedCornerShape(10.dp)
                    val noticeTextColor = if (positive) Color(0xFF3E6B2F) else Color(0xFF4E5661)
                    val noticeBackground = if (positive) Color(0xFFF3F8F1) else Color(0xFFF1F3F5)
                    val noticeBorder = if (positive) Color(0xFFD8E8D1) else Color(0xFFD9DEE5)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = noticeTextColor,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        letterSpacing = 0.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(noticeBackground, noticeShape)
                            .border(BorderStroke(0.8.dp, noticeBorder), noticeShape)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginBrandLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = 1.6f,
                        scaleY = 1.6f
                    )
            )
        }
    }
}

private fun buildLoginAgreementText() = buildAnnotatedString {
    append("我已阅读并同意")
    pushStringAnnotation(tag = "service", annotation = "service")
    withStyle(
        SpanStyle(
            color = Color(0xFF111111),
            fontWeight = FontWeight.SemiBold
        )
    ) {
        append("《服务协议》")
    }
    pop()
    pushStringAnnotation(tag = "privacy", annotation = "privacy")
    withStyle(
        SpanStyle(
            color = Color(0xFF111111),
            fontWeight = FontWeight.SemiBold
        )
    ) {
        append("《隐私政策》")
    }
    pop()
}

@Composable
private fun LoginAgreementCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (checked) Color(0xFF111111) else Color(0xFF747682)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(23.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(BorderStroke(1.6.dp, borderColor), RoundedCornerShape(4.dp))
        ) {
            if (checked) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    val strokeWidth = 2.4.dp.toPx()
                    drawLine(
                        color = Color(0xFF111111),
                        start = Offset(size.width * 0.16f, size.height * 0.52f),
                        end = Offset(size.width * 0.40f, size.height * 0.76f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF111111),
                        start = Offset(size.width * 0.40f, size.height * 0.76f),
                        end = Offset(size.width * 0.86f, size.height * 0.24f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginLegalDialog(
    page: LoginLegalPage,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFFFFFFFF),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(0.8.dp, Color(0xFFE2E4E8)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = if (page == LoginLegalPage.ServiceAgreement) "服务协议" else "隐私政策",
                        color = Color(0xFF111111),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("关闭", color = Color(0xFF111111), letterSpacing = 0.sp)
                    }
                }
                HorizontalDivider(color = Color(0xFFE8EAEE))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (page) {
                        LoginLegalPage.ServiceAgreement -> {
                            HamburgerServiceAgreementContent(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                            )
                        }
                        LoginLegalPage.PrivacyPolicy -> {
                            HamburgerPrivacyPolicyContent(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class LoginLegalPage {
    ServiceAgreement,
    PrivacyPolicy
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun isValidMainlandPhone(value: String): Boolean =
    value.length == 11 && value.firstOrNull() == '1' && value.all(Char::isDigit)

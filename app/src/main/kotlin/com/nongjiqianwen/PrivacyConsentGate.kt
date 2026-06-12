package com.nongjiqianwen

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

object PrivacyConsentStore {
    private const val PREFS_NAME = "privacy_consent"
    private const val KEY_ACCEPTED_VERSION = "accepted_version"
    private const val KEY_ACCEPTED_AT_MS = "accepted_at_ms"
    private const val CURRENT_VERSION = 1

    fun isAccepted(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ACCEPTED_VERSION, 0) >= CURRENT_VERSION

    fun accept(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACCEPTED_VERSION, CURRENT_VERSION)
            .putLong(KEY_ACCEPTED_AT_MS, System.currentTimeMillis())
            .apply()
    }
}

@Composable
fun PrivacyConsentGate(
    onAccepted: () -> Unit,
    onDeclined: () -> Unit
) {
    LaunchedEffect(Unit) {
        LaunchUiGate.chatReady = true
    }

    var checked by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var legalPage by remember { mutableStateOf<PrivacyConsentLegalPage?>(null) }
    val agreementText = remember { buildPrivacyConsentAgreementText() }

    Surface(color = Color(0xFFF7F8FA), modifier = Modifier.fillMaxSize()) {
        legalPage?.let { page ->
            PrivacyConsentLegalDialog(
                page = page,
                onDismiss = { legalPage = null }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.8.dp, Color(0xFFE2E4E8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 430.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "农技千查",
                        color = Color(0xFF111111),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "请先阅读并同意服务协议和隐私政策",
                        color = Color(0xFF30333A),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "同意后才会进入登录、问诊、图片上传、帮助与反馈、今日农情和检查更新等功能；不同意可以退出 App。",
                        color = Color(0xFF575D66),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoginAgreementCheckbox(
                            checked = checked,
                            onCheckedChange = {
                                checked = it
                                if (it) message = null
                            }
                        )
                        Spacer(Modifier.size(8.dp))
                        ClickableText(
                            text = agreementText,
                            modifier = Modifier.weight(1f),
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color(0xFF575D66),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                letterSpacing = 0.sp
                            ),
                            onClick = { offset ->
                                agreementText.getStringAnnotations(start = offset, end = offset)
                                    .firstOrNull()
                                    ?.let { annotation ->
                                        legalPage = when (annotation.tag) {
                                            "service" -> PrivacyConsentLegalPage.ServiceAgreement
                                            "privacy" -> PrivacyConsentLegalPage.PrivacyPolicy
                                            else -> null
                                        }
                                    }
                            }
                        )
                    }
                    message?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = it,
                            color = Color(0xFF8A5A00),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            letterSpacing = 0.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF4D8), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = {
                            if (!checked) {
                                message = "请勾选同意后继续"
                            } else {
                                onAccepted()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("同意并继续", fontSize = 16.sp, letterSpacing = 0.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onDeclined,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("不同意，退出", color = Color(0xFF111111), letterSpacing = 0.sp)
                    }
                }
            }
        }
    }
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
            .size(23.dp)
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .background(Color.Transparent, RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (checked) Color(0xFF111111) else Color.Transparent, RoundedCornerShape(4.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
                .background(Color.Transparent, RoundedCornerShape(4.dp))
        )
        if (checked) {
            Text("✓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        } else {
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.6.dp, borderColor),
                modifier = Modifier.fillMaxSize()
            ) {}
        }
    }
}

private fun buildPrivacyConsentAgreementText() = buildAnnotatedString {
    append("我已阅读并同意")
    pushStringAnnotation(tag = "service", annotation = "service")
    withStyle(SpanStyle(color = Color(0xFF111111), fontWeight = FontWeight.SemiBold)) {
        append("《服务协议》")
    }
    pop()
    pushStringAnnotation(tag = "privacy", annotation = "privacy")
    withStyle(SpanStyle(color = Color(0xFF111111), fontWeight = FontWeight.SemiBold)) {
        append("《隐私政策》")
    }
    pop()
}

@Composable
private fun PrivacyConsentLegalDialog(
    page: PrivacyConsentLegalPage,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color.White,
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
                        text = if (page == PrivacyConsentLegalPage.ServiceAgreement) "服务协议" else "隐私政策",
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
                        PrivacyConsentLegalPage.ServiceAgreement -> {
                            HamburgerServiceAgreementContent(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                            )
                        }
                        PrivacyConsentLegalPage.PrivacyPolicy -> {
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

private enum class PrivacyConsentLegalPage {
    ServiceAgreement,
    PrivacyPolicy
}

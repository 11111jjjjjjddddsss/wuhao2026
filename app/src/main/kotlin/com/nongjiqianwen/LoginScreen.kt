package com.nongjiqianwen

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LoginGate(content: @Composable () -> Unit) {
    var loggedIn by remember {
        mutableStateOf(
            IdManager.isLoggedIn() || BuildConfig.SESSION_API_TOKEN.trim().isNotEmpty()
        )
    }
    if (loggedIn) {
        content()
    } else {
        LoginScreen(onLoginSuccess = { loggedIn = true })
    }
}

@Composable
private fun LoginScreen(onLoginSuccess: () -> Unit) {
    LaunchedEffect(Unit) {
        LaunchUiGate.chatReady = true
    }

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var agreed by remember { mutableStateOf(false) }
    var smsMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(0) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown -= 1
        }
    }

    Surface(color = Color(0xFFF7F8FA), modifier = Modifier.fillMaxSize()) {
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
                Text(
                    text = "农技千查",
                    color = Color(0xFF111111),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "登录后同步问诊记录、会员权益和多设备使用状态",
                    color = Color(0xFF6C717A),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.sp
                )
                Spacer(Modifier.height(36.dp))

                Button(
                    onClick = {
                        if (!agreed) {
                            message = "请先同意服务协议和隐私政策"
                            return@Button
                        }
                        smsMode = true
                        message = "一键登录客户端 SDK 正在接入，先使用验证码登录；这里不会消耗一键登录试用次数"
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("本机号码一键登录", fontSize = 17.sp, letterSpacing = 0.sp)
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        smsMode = !smsMode
                        message = null
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = if (smsMode) "收起验证码登录" else "验证码登录",
                        color = Color(0xFF111111),
                        fontSize = 16.sp,
                        letterSpacing = 0.sp
                    )
                }

                if (smsMode) {
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                        singleLine = true,
                        label = { Text("手机号") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter(Char::isDigit).take(6) },
                            singleLine = true,
                            label = { Text("验证码") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                if (!agreed) {
                                    message = "请先同意服务协议和隐私政策"
                                    return@OutlinedButton
                                }
                                if (!isValidMainlandPhone(phone)) {
                                    message = "请输入正确的手机号"
                                    return@OutlinedButton
                                }
                                busy = true
                                message = null
                                SessionApi.sendSmsCode(phone) { ok, error ->
                                    busy = false
                                    if (ok) {
                                        countdown = 60
                                        message = "验证码已发送"
                                    } else {
                                        message = error ?: "验证码发送失败"
                                    }
                                }
                            },
                            enabled = !busy && countdown == 0,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text(
                                text = if (countdown > 0) "${countdown}s" else "发送",
                                color = Color(0xFF111111),
                                letterSpacing = 0.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            if (!agreed) {
                                message = "请先同意服务协议和隐私政策"
                                return@Button
                            }
                            if (!isValidMainlandPhone(phone) || code.length < 4) {
                                message = "请填写手机号和验证码"
                                return@Button
                            }
                            busy = true
                            message = null
                            SessionApi.loginWithSms(phone, code) { ok, error ->
                                busy = false
                                if (ok) {
                                    onLoginSuccess()
                                } else {
                                    message = error ?: "登录失败，请稍后再试"
                                }
                            }
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111111)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("登录", fontSize = 17.sp, letterSpacing = 0.sp)
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                    Text(
                        text = "我已阅读并同意《服务协议》《隐私政策》",
                        color = Color(0xFF575D66),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        letterSpacing = 0.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = Color(0xFF8A5A00),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        letterSpacing = 0.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF4D8), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "登录后不会清空本机记录，旧记录会自动迁移到账户",
                    color = Color(0xFF8A8F98),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}

private fun isValidMainlandPhone(value: String): Boolean =
    value.length == 11 && value.firstOrNull() == '1' && value.all(Char::isDigit)

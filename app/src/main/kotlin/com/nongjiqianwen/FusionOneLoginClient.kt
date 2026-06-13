package com.nongjiqianwen

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.alicom.fusion.auth.AlicomFusionAuthCallBack
import com.alicom.fusion.auth.AlicomFusionAuthUICallBack
import com.alicom.fusion.auth.AlicomFusionBusiness
import com.alicom.fusion.auth.HalfWayVerifyResult
import com.alicom.fusion.auth.AlicomFusionLog
import com.alicom.fusion.auth.error.AlicomFusionEvent
import com.alicom.fusion.auth.numberauth.AlicomFusionSwitchLogin
import com.alicom.fusion.auth.numberauth.FusionNumberAuthModel
import com.alicom.fusion.auth.smsauth.AlicomFusionVerifyCodeView
import com.alicom.fusion.auth.token.AlicomFusionAuthToken
import com.alicom.fusion.auth.upsms.AlicomFusionUpSMSView
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object FusionOneLoginClient {
    private const val TAG = "FusionOneLogin"
    private const val LOGIN_TEMPLATE_ID = "100001"
    private const val PROTOCOL_ACTION = "com.nongjiqiancha.FUSION_AUTH_PROTOCOL"
    private const val LOGIN_TIMEOUT_MS = 30_000L
    private const val VERIFY_FAILED_FALLBACK_MS = 1_500L
    private const val SERVICE_AGREEMENT_URL = "https://nongjiqiancha.cn/legal/user-agreement/"
    private const val PRIVACY_POLICY_URL = "https://nongjiqiancha.cn/legal/privacy-policy/"
    private const val ONE_LOGIN_FALLBACK_MESSAGE = "一键登录未成功，已切换到验证码登录；验证码登录可在 WiFi 或代理环境下继续使用"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loginInFlight = AtomicBoolean(false)
    private val loginGeneration = AtomicLong(0L)

    @Volatile
    private var currentBusiness: AlicomFusionBusiness? = null

    @Volatile
    private var currentCompleted: AtomicBoolean? = null

    fun precheckOneLoginEnvironment(context: Context): String? {
        val environment = inspectAuthEnvironment(context)
        val blockReason = environment.blockReason() ?: return null
        reportAuthEnvironment(
            level = "warn",
            event = "auth.fusion_env_blocked",
            stage = "pre_permission_env_check",
            environment = environment,
            reason = blockReason
        )
        return environment.userMessageFor(blockReason)
    }

    fun cancelActiveScene(reason: String) {
        loginGeneration.incrementAndGet()
        val business = currentBusiness
        val completed = currentCompleted
        if (business == null) {
            loginInFlight.set(false)
            AppCrashReporter.clearAuthStage()
            return
        }
        mainHandler.post {
            if (completed != null && !completed.compareAndSet(false, true)) return@post
            reportAuthLog(
                level = "info",
                event = "auth.fusion_scene_cancelled",
                stage = reason.take(48),
                error = null
            )
            runCatching { business.stopSceneWithTemplateId(LOGIN_TEMPLATE_ID) }
            runCatching { business.destory() }
            if (currentBusiness === business) {
                currentBusiness = null
                currentCompleted = null
            }
            loginInFlight.set(false)
            AppCrashReporter.clearAuthStage()
        }
    }

    fun start(
        activity: Activity,
        verificationPhone: String? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (!loginInFlight.compareAndSet(false, true)) {
            onResult(false, "一键登录正在处理中，请稍候")
            return
        }
        val generation = loginGeneration.incrementAndGet()
        if (!activity.isUsableForFusionAuth()) {
            AppCrashReporter.clearAuthStage()
            loginInFlight.set(false)
            reportAuthLog(
                level = "warn",
                event = "auth.fusion_activity_unavailable",
                stage = "start",
                error = null
            )
            onResult(false, "一键登录暂不可用，请使用验证码登录")
            return
        }
        AppCrashReporter.setAuthStage("auth.fusion_env_check")
        val environment = inspectAuthEnvironment(activity)
        val blockReason = environment.blockReason()
        if (blockReason != null) {
            AppCrashReporter.clearAuthStage("auth.fusion_env_check")
            loginInFlight.set(false)
            reportAuthEnvironment(
                level = "warn",
                event = "auth.fusion_env_blocked",
                stage = "env_check",
                environment = environment,
                reason = blockReason
            )
            onResult(false, environment.userMessageFor(blockReason))
            return
        }
        environment.warningReason()?.let { reason ->
            reportAuthEnvironment(
                level = "info",
                event = "auth.fusion_env_warning",
                stage = "env_check",
                environment = environment,
                reason = reason
            )
        }
        AppCrashReporter.setAuthStage("auth.fusion_token")
        SessionApi.requestFusionAuthToken { snapshot, error ->
            if (generation != loginGeneration.get()) {
                return@requestFusionAuthToken
            }
            if (snapshot?.usable != true) {
                AppCrashReporter.clearAuthStage("auth.fusion_token")
                loginInFlight.set(false)
                reportAuthLog(
                    level = "warn",
                    event = "auth.fusion_token_failed",
                    stage = "fusion_token",
                    error = null
                )
                onResult(false, error ?: ONE_LOGIN_FALLBACK_MESSAGE)
                return@requestFusionAuthToken
            }
            if (!activity.isUsableForFusionAuth()) {
                AppCrashReporter.clearAuthStage("auth.fusion_token")
                loginInFlight.set(false)
                onResult(false, "一键登录已取消，请使用验证码登录")
                return@requestFusionAuthToken
            }
            startWithToken(activity, snapshot, verificationPhone, generation, onResult)
        }
    }

    private fun startWithToken(
        activity: Activity,
        snapshot: SessionApi.FusionAuthTokenSnapshot,
        verificationPhone: String?,
        generation: Long,
        onResult: (Boolean, String?) -> Unit
    ) {
        val business = AlicomFusionBusiness()
        val completed = AtomicBoolean(false)
        val sceneStarted = AtomicBoolean(false)
        val serverLoginStarted = AtomicBoolean(false)
        AppCrashReporter.setAuthStage("auth.fusion_sdk_init")
        currentBusiness?.let { previous ->
            safeStopScene(previous)
            safeDestroy(previous)
        }
        currentBusiness = business
        currentCompleted = completed

        AlicomFusionLog.setLogEnable(BuildConfig.DEBUG)
        reportAuthLog(
            level = "info",
            event = "auth.fusion_sdk_init_start",
            stage = "sdk_init_start",
            error = null
        )
        val token = AlicomFusionAuthToken().apply {
            setAuthToken(snapshot.authToken.orEmpty())
        }
        val initResult = runCatching {
            business.initWithToken(activity.applicationContext, snapshot.schemeCode.orEmpty(), token)
            business.setProtocolAction(PROTOCOL_ACTION)
            business.adapterPageShape(true)
        }
        if (initResult.isFailure) {
            logFusionWarning("fusion sdk init failed", initResult.exceptionOrNull())
            reportAuthLog(
                level = "error",
                event = "auth.fusion_sdk_init_failed",
                stage = "sdk_init",
                error = null
            )
            finish(business, completed, false, ONE_LOGIN_FALLBACK_MESSAGE, onResult)
            return
        }

        val callbackAttached = runCatching {
            business.setAlicomFusionAuthCallBack(object : AlicomFusionAuthCallBack {
                override fun onSDKTokenUpdate(): AlicomFusionAuthToken {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        reportAuthLog(
                            level = "info",
                            event = "auth.fusion_token_refresh_skipped",
                            stage = "token_refresh_main_thread",
                            error = null
                        )
                        return AlicomFusionAuthToken().apply {
                            setAuthToken(snapshot.authToken.orEmpty())
                        }
                    }
                    val fresh = SessionApi.requestFusionAuthTokenBlocking()
                    if (fresh?.usable != true) {
                        reportAuthLog(
                            level = "warn",
                            event = "auth.fusion_token_refresh_failed",
                            stage = "token_refresh",
                            error = null
                        )
                    }
                    return AlicomFusionAuthToken().apply {
                        setAuthToken(fresh?.authToken?.takeIf { it.isNotBlank() } ?: snapshot.authToken.orEmpty())
                    }
                }

                override fun onSDKTokenAuthSuccess() {
                    activity.runOnUiThread {
                        if (generation != loginGeneration.get()) {
                            finish(business, completed, false, "一键登录已取消，请使用验证码登录", onResult)
                            return@runOnUiThread
                        }
                        if (!completed.get() && sceneStarted.compareAndSet(false, true)) {
                            if (!activity.isUsableForFusionAuth()) {
                                reportAuthLog(
                                    level = "warn",
                                    event = "auth.fusion_activity_unavailable",
                                    stage = "scene_start",
                                    error = null
                                )
                                finish(
                                    business,
                                    completed,
                                    false,
                                    "一键登录已取消，请使用验证码登录",
                                    onResult
                                )
                                return@runOnUiThread
                            }
                            reportAuthLog(
                                level = "info",
                                event = "auth.fusion_scene_starting",
                                stage = "scene_start",
                                error = null
                            )
                            val startResult = runCatching {
                                AppCrashReporter.setAuthStage("auth.fusion_scene_start")
                                business.startSceneWithTemplateId(
                                    activity,
                                    LOGIN_TEMPLATE_ID,
                                    createAuthUiCallback(business, completed, onResult)
                                )
                            }
                            if (startResult.isFailure) {
                                logFusionWarning("fusion scene start failed", startResult.exceptionOrNull())
                                reportAuthLog(
                                    level = "error",
                                    event = "auth.fusion_scene_start_failed",
                                    stage = "scene_start",
                                    error = null
                                )
                                finish(
                                    business,
                                    completed,
                                    false,
                                    ONE_LOGIN_FALLBACK_MESSAGE,
                                    onResult
                                )
                            } else {
                                reportAuthLog(
                                    level = "info",
                                    event = "auth.fusion_scene_start_invoked",
                                    stage = "scene_start",
                                    error = null
                                )
                            }
                        }
                    }
                }

                override fun onSDKTokenAuthFailure(
                    failToken: AlicomFusionAuthToken?,
                    error: AlicomFusionEvent?
                ) {
                    Log.w(TAG, "fusion sdk token auth failed: ${describeEvent(error)}")
                    reportAuthLog(
                        level = "warn",
                        event = "auth.fusion_sdk_token_auth_failed",
                        stage = "token_auth",
                        error = error
                    )
                    finish(business, completed, false, ONE_LOGIN_FALLBACK_MESSAGE, onResult)
                }

                override fun onVerifySuccess(
                    token: String?,
                    nodeName: String?,
                    event: AlicomFusionEvent?
                ) {
                    AppCrashReporter.setAuthStage("auth.fusion_verify_success")
                    if (generation != loginGeneration.get()) {
                        safeContinueScene(business, false)
                        finish(business, completed, false, "一键登录已取消，请使用验证码登录", onResult)
                        return
                    }
                    val verifyToken = token.orEmpty().trim()
                    if (verifyToken.isEmpty()) {
                        safeContinueScene(business, false)
                        reportAuthLog(
                            level = "warn",
                            event = "auth.fusion_empty_verify_token",
                            stage = "verify",
                            error = event,
                            nodeName = nodeName
                        )
                        finish(business, completed, false, ONE_LOGIN_FALLBACK_MESSAGE, onResult)
                        return
                    }
                    if (!serverLoginStarted.compareAndSet(false, true)) {
                        reportAuthLog(
                            level = "warn",
                            event = "auth.fusion_verify_duplicate",
                            stage = "verify",
                            error = event,
                            nodeName = nodeName
                        )
                        return
                    }
                    AppCrashReporter.setAuthStage("auth.fusion_server_login")
                    SessionApi.loginWithFusionVerifyToken(
                        verifyToken = verifyToken,
                        shouldCommitSession = { !completed.get() }
                    ) { ok, loginError ->
                        if (ok) {
                            safeContinueScene(business, true)
                            finish(business, completed, true, null, onResult)
                        } else {
                            safeContinueScene(business, false)
                            reportAuthLog(
                                level = "warn",
                                event = "auth.fusion_login_failed",
                                stage = "server_login",
                                error = event
                            )
                            finish(
                                business,
                                completed,
                                false,
                                loginError ?: ONE_LOGIN_FALLBACK_MESSAGE,
                                onResult
                            )
                        }
                    }
                }

                override fun onHalfWayVerifySuccess(
                    nodeName: String?,
                    maskToken: String?,
                    event: AlicomFusionEvent?,
                    verifyResult: HalfWayVerifyResult?
                ) {
                    Log.w(TAG, "unexpected fusion half-way callback for one-login: node=$nodeName ${describeEvent(event)}")
                    reportAuthLog(
                        level = "warn",
                        event = "auth.fusion_halfway_unexpected",
                        stage = "halfway",
                        error = event,
                        nodeName = nodeName
                    )
                    verifyResult?.verifyResult(false)
                    safeContinueScene(business, false)
                    finish(business, completed, false, ONE_LOGIN_FALLBACK_MESSAGE, onResult)
                }

                override fun onVerifyFailed(error: AlicomFusionEvent?, nodeName: String?) {
                    if (serverLoginStarted.get() && !completed.get()) {
                        reportAuthLog(
                            level = "info",
                            event = "auth.fusion_verify_failed_ignored",
                            stage = "verify",
                            error = error,
                            nodeName = nodeName
                        )
                        return
                    }
                    AppCrashReporter.setAuthStage("auth.fusion_verify_failed")
                    Log.w(TAG, "fusion verify failed: node=$nodeName ${describeEvent(error)}")
                    reportAuthLog(
                        level = "warn",
                        event = "auth.fusion_verify_failed",
                        stage = "verify",
                        error = error,
                        nodeName = nodeName
                    )
                    safeContinueScene(business, false)
                    mainHandler.postDelayed({
                        if (completed.get() || serverLoginStarted.get()) return@postDelayed
                        finish(business, completed, false, ONE_LOGIN_FALLBACK_MESSAGE, onResult)
                    }, VERIFY_FAILED_FALLBACK_MS)
                }

                override fun onTemplateFinish(event: AlicomFusionEvent?) {
                    if (serverLoginStarted.get() && !completed.get()) {
                        reportAuthLog(
                            level = "info",
                            event = "auth.fusion_template_finish_ignored",
                            stage = "template_finish",
                            error = event
                        )
                        return
                    }
                    AppCrashReporter.setAuthStage("auth.fusion_template_finish")
                    Log.i(TAG, "fusion template finished: ${describeEvent(event)}")
                    reportAuthLog(
                        level = "info",
                        event = "auth.fusion_template_finished",
                        stage = "template_finish",
                        error = event
                    )
                    finish(business, completed, false, "一键登录未完成，可继续使用验证码登录", onResult)
                }

                override fun onAuthEvent(event: AlicomFusionEvent?) {
                    reportAuthLog(
                        level = "info",
                        event = "auth.fusion_auth_event",
                        stage = "auth_event",
                        error = event
                    )
                }

                override fun onGetPhoneNumberForVerification(
                    nodeId: String?,
                    event: AlicomFusionEvent?
                ): String {
                    val normalizedPhone = verificationPhone
                        ?.filter(Char::isDigit)
                        ?.takeIf(::isValidMainlandPhoneNumber)
                    reportAuthLog(
                        level = "warn",
                        event = "auth.fusion_get_phone_for_verification",
                        stage = "get_phone_for_verification",
                        error = event,
                        nodeName = nodeId
                    )
                    if (normalizedPhone == null) {
                        safeContinueScene(business, false)
                        finish(
                            business,
                            completed,
                            false,
                            "请使用验证码登录",
                            onResult
                        )
                    }
                    return normalizedPhone ?: "19999999999"
                }

                override fun onVerifyInterrupt(event: AlicomFusionEvent?) {
                    reportAuthLog(
                        level = "warn",
                        event = "auth.fusion_verify_interrupt",
                        stage = "verify_interrupt",
                        error = event
                    )
                }
            })
        }.isSuccess
        if (!callbackAttached) {
            logFusionWarning("fusion callback attach failed")
            reportAuthLog(
                level = "error",
                event = "auth.fusion_callback_attach_failed",
                stage = "callback_attach",
                error = null
            )
            finish(business, completed, false, ONE_LOGIN_FALLBACK_MESSAGE, onResult)
            return
        }
        mainHandler.postDelayed({
            if (completed.get()) return@postDelayed
            if (serverLoginStarted.get()) {
                reportAuthLog(
                    level = "info",
                    event = "auth.fusion_timeout_ignored",
                    stage = "server_login",
                    error = null
                )
                return@postDelayed
            }
            reportAuthLog(
                level = "warn",
                event = "auth.fusion_timeout",
                stage = "timeout",
                error = null
            )
            finish(business, completed, false, ONE_LOGIN_FALLBACK_MESSAGE, onResult)
        }, LOGIN_TIMEOUT_MS)
    }

    private fun createAuthUiCallback(
        business: AlicomFusionBusiness,
        completed: AtomicBoolean,
        onResult: (Boolean, String?) -> Unit
    ): AlicomFusionAuthUICallBack =
        object : AlicomFusionAuthUICallBack {
            override fun onPhoneNumberVerifyUICustomView(
                templateId: String?,
                nodeId: String?,
                model: FusionNumberAuthModel?
            ) {
                if (model == null) {
                    reportAuthLog(
                        level = "warn",
                        event = "auth.fusion_ui_model_null",
                        stage = "ui_custom",
                        error = null,
                        nodeName = nodeId
                    )
                    return
                }
                configureNumberAuthModel(model)
                model.setSwitchLoginBack(
                    AlicomFusionSwitchLogin {
                        finish(business, completed, false, "请使用验证码登录", onResult)
                    }
                )
            }

            override fun onSMSCodeVerifyUICustomView(
                templateId: String?,
                nodeId: String?,
                isAutoInput: Boolean,
                view: AlicomFusionVerifyCodeView?
            ) {
                view?.getTitleContentTV()?.text = "验证码登录"
            }

            override fun onSMSSendVerifyUICustomView(
                templateId: String?,
                nodeId: String?,
                view: AlicomFusionUpSMSView?,
                phoneNumber: String?,
                smsContent: String?
            ) {
                view?.getTitleContentTV()?.text = "短信验证"
            }
        }

    private fun configureNumberAuthModel(model: FusionNumberAuthModel) {
        val configured = runCatching {
            model.getBuilder()
                .setStatusBarColor(Color.WHITE)
                .setBottomNavColor(Color.WHITE)
                .setLightColor(true)
                .setNavHidden(true)
                .setNavReturnHidden(false)
                .setNavText("农技千查")
                .setNavTextColor(Color.rgb(17, 17, 17))
                .setNavTextSizeDp(17)
                .setLogoHidden(true)
                .setSloganHidden(true)
                .hiddenNumberCountry(true)
                .setNumberColor(Color.rgb(17, 17, 17))
                .setNumberSizeDp(28)
                .setNumberLayoutGravity(Gravity.CENTER_HORIZONTAL)
                .setNumFieldOffsetY(136)
                .setLogBtnText("本机号码一键登录")
                .setLogBtnTextColor(Color.WHITE)
                .setLogBtnTextSizeDp(17)
                .setLogBtnBackgroundDrawable(loginButtonBackground())
                .setLogBtnHeight(50)
                .setLogBtnMarginLeftAndRight(32)
                .setLogBtnOffsetY(224)
                .hiddenSwtichLogin(true)
                .setSwitchAccText("其他手机号登录")
                .setSwitchAccTextColor(Color.rgb(17, 17, 17))
                .setSwitchAccTextSizeDp(15)
                .setSwitchOffsetY(340)
                .setCheckboxHidden(false)
                .setCheckBoxWidth(18)
                .setCheckBoxHeight(18)
                .setPrivacyState(false)
                .setPrivacyBefore("我已阅读并同意")
                .setPrivacyEnd("")
                .setPrivacyTextSizeDp(11)
                .setPrivacyMargin(20)
                .setPrivacyOffsetY_B(36)
                .setAppPrivacyOne("《服务协议》", SERVICE_AGREEMENT_URL)
                .setAppPrivacyTwo("《隐私政策》", PRIVACY_POLICY_URL)
                .setAppPrivacyColor(Color.rgb(17, 17, 17), Color.rgb(87, 93, 102))
                .setProtocolAction(PROTOCOL_ACTION)
                .setPackageName(BuildConfig.APPLICATION_ID)
                .setProtocolGravity(Gravity.CENTER)
                .setProtocolLayoutGravity(Gravity.CENTER_HORIZONTAL)
                .setPrivacyAlertIsNeedShow(false)
                .setLogBtnToastHidden(false)
                .setPageBackgroundDrawable(GradientDrawable().apply { setColor(Color.WHITE) })
                .create()
        }.onFailure {
            reportAuthLog(
                level = "warn",
                event = "auth.fusion_ui_config_failed",
                stage = "ui_config",
                error = null
            )
        }.getOrNull()
        if (configured != null) {
            copySdkModelFields(configured, model)
        }
        runCatching {
            model.setProtocolChecked(false)
            model.setExpandAuthPageCheckedScope(true)
        }
    }

    private fun copySdkModelFields(source: FusionNumberAuthModel, target: FusionNumberAuthModel) {
        val clazz = FusionNumberAuthModel::class.java
        for (field in clazz.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
            if (field.name == "switchLogin") continue
            runCatching {
                field.isAccessible = true
                field.set(target, field.get(source))
            }
        }
    }

    private fun loginButtonBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.rgb(17, 17, 17))
            cornerRadius = 12f
        }

    private fun reportAuthLog(
        level: String,
        event: String,
        stage: String,
        error: AlicomFusionEvent?,
        nodeName: String? = null
    ) {
        val attrs = mutableMapOf<String, Any?>("stage" to stage)
        if (!nodeName.isNullOrBlank()) attrs["node"] = nodeName.take(48)
        error?.let {
            attrs["error_code"] = it.getErrorCode()?.take(64).orEmpty()
            attrs["inner_code"] = it.getInnerCode()?.take(64).orEmpty()
        }
        SessionApi.reportAuthClientLog(
            level = level,
            event = event,
            message = event,
            attrs = attrs
        )
    }

    private fun reportAuthEnvironment(
        level: String,
        event: String,
        stage: String,
        environment: AuthEnvironmentSnapshot,
        reason: String
    ) {
        SessionApi.reportAuthClientLog(
            level = level,
            event = event,
            message = event,
            attrs = mapOf(
                "stage" to stage,
                "reason" to reason,
                "network" to environment.networkLabel(),
                "sim_state" to environment.simStateLabel,
                "sim_count" to (environment.simCount ?: 0)
            )
        )
    }

    private data class AuthEnvironmentSnapshot(
        val hasActiveNetwork: Boolean,
        val hasInternetCapability: Boolean,
        val hasCellularTransport: Boolean,
        val hasAnyCellularInternetTransport: Boolean,
        val hasWifiTransport: Boolean,
        val hasVpnTransport: Boolean,
        val hasProxyConfigured: Boolean,
        val simState: Int?,
        val simCount: Int?
    ) {
        val simStateLabel: String
            get() = when (simState) {
                TelephonyManager.SIM_STATE_ABSENT -> "absent"
                TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "network_locked"
                TelephonyManager.SIM_STATE_PIN_REQUIRED -> "pin_required"
                TelephonyManager.SIM_STATE_PUK_REQUIRED -> "puk_required"
                TelephonyManager.SIM_STATE_READY -> "ready"
                TelephonyManager.SIM_STATE_NOT_READY -> "not_ready"
                TelephonyManager.SIM_STATE_PERM_DISABLED -> "perm_disabled"
                TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "card_io_error"
                TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "card_restricted"
                TelephonyManager.SIM_STATE_UNKNOWN, null -> "unknown"
                else -> "other"
            }

        fun networkLabel(): String =
            listOfNotNull(
                "active".takeIf { hasActiveNetwork },
                "internet".takeIf { hasInternetCapability },
                "cellular".takeIf { hasCellularTransport },
                "cellular_available".takeIf { hasAnyCellularInternetTransport && !hasCellularTransport },
                "wifi".takeIf { hasWifiTransport },
                "vpn".takeIf { hasVpnTransport },
                "proxy".takeIf { hasProxyConfigured }
            ).ifEmpty { listOf("none") }.joinToString(separator = "+")

        fun blockReason(): String? =
            when {
                !hasActiveNetwork || !hasInternetCapability -> "no_network"
                simCount == 0 -> "no_sim"
                simState == TelephonyManager.SIM_STATE_ABSENT -> "no_sim"
                simState in setOf(
                    TelephonyManager.SIM_STATE_NETWORK_LOCKED,
                    TelephonyManager.SIM_STATE_PIN_REQUIRED,
                    TelephonyManager.SIM_STATE_PUK_REQUIRED,
                    TelephonyManager.SIM_STATE_NOT_READY,
                    TelephonyManager.SIM_STATE_PERM_DISABLED,
                    TelephonyManager.SIM_STATE_CARD_IO_ERROR,
                    TelephonyManager.SIM_STATE_CARD_RESTRICTED
                ) -> "sim_not_ready"
                !hasAnyCellularInternetTransport -> "no_cellular_data"
                else -> null
            }

        fun warningReason(): String? =
            when {
                hasVpnTransport -> "vpn_active"
                hasProxyConfigured -> "proxy_active"
                !hasCellularTransport && hasAnyCellularInternetTransport -> "wifi_with_cellular_available"
                hasWifiTransport -> "wifi_and_cellular"
                else -> null
            }

        fun userMessageFor(reason: String): String =
            when (reason) {
                "no_network" -> "当前网络不可用，请联网后重试，或使用验证码登录"
                "no_sim" -> "未检测到可用 SIM 卡，请插卡并打开移动数据，或使用验证码登录"
                "sim_not_ready" -> "SIM 卡暂不可用，请确认默认移动数据卡正常，或使用验证码登录"
                "vpn_active" -> "检测到代理或 VPN，一键取号可能失败；失败后可使用验证码登录"
                "proxy_active" -> "检测到系统代理，一键取号可能失败；失败后可使用验证码登录"
                "no_cellular_data" -> "一键登录需要可用移动数据，已切换到验证码登录；验证码登录可继续使用"
                else -> ONE_LOGIN_FALLBACK_MESSAGE
            }
    }

    private fun inspectAuthEnvironment(context: Context): AuthEnvironmentSnapshot {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivity?.activeNetwork
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
        @Suppress("DEPRECATION")
        val allNetworks = connectivity?.allNetworks.orEmpty()
        val hasAnyCellularInternetTransport = allNetworks.any { candidate ->
            val candidateCapabilities = connectivity?.getNetworkCapabilities(candidate)
            candidateCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true &&
                candidateCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
        val hasProxyConfigured = isSystemProxyConfigured(context, connectivity, network)
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simState = runCatching { telephony?.simState }.getOrNull()
        val simCount = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                telephony?.activeModemCount
            } else {
                @Suppress("DEPRECATION")
                telephony?.phoneCount
            }
        }.getOrNull()
        return AuthEnvironmentSnapshot(
            hasActiveNetwork = network != null,
            hasInternetCapability = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            hasCellularTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            hasAnyCellularInternetTransport = hasAnyCellularInternetTransport,
            hasWifiTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            hasVpnTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            hasProxyConfigured = hasProxyConfigured,
            simState = simState,
            simCount = simCount
        )
    }

    private fun isSystemProxyConfigured(
        context: Context,
        connectivity: ConnectivityManager?,
        network: Network?
    ): Boolean {
        val activeNetworkProxy = runCatching {
            network?.let { connectivity?.getLinkProperties(it)?.httpProxy }
        }.getOrNull()?.let { proxy ->
            !proxy.host.isNullOrBlank() || proxy.port > 0
        } == true
        if (activeNetworkProxy) return true

        val javaProxy = listOf(
            "http.proxyHost",
            "https.proxyHost",
            "socksProxyHost"
        ).any { key ->
            !System.getProperty(key).isNullOrBlank()
        }
        if (javaProxy) return true

        @Suppress("DEPRECATION")
        return !android.net.Proxy.getHost(context).isNullOrBlank()
    }

    private fun finish(
        business: AlicomFusionBusiness,
        completed: AtomicBoolean,
        ok: Boolean,
        message: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (!completed.compareAndSet(false, true)) return
        mainHandler.post {
            runCatching { business.stopSceneWithTemplateId(LOGIN_TEMPLATE_ID) }
            runCatching { business.destory() }
            AppCrashReporter.clearAuthStage()
            loginInFlight.set(false)
            if (currentBusiness === business) {
                currentBusiness = null
                currentCompleted = null
            }
            onResult(ok, message)
        }
    }

    private fun safeContinueScene(business: AlicomFusionBusiness, passed: Boolean) {
        mainHandler.post {
            runCatching { business.continueSceneWithTemplateId(LOGIN_TEMPLATE_ID, passed) }
        }
    }

    private fun safeStopScene(business: AlicomFusionBusiness) {
        mainHandler.post {
            runCatching { business.stopSceneWithTemplateId(LOGIN_TEMPLATE_ID) }
        }
    }

    private fun safeDestroy(business: AlicomFusionBusiness) {
        mainHandler.post {
            runCatching { business.destory() }
        }
    }

    private fun describeEvent(event: AlicomFusionEvent?): String {
        if (event == null) return "event=null"
        val parts = mutableListOf(
            "template=${event.templatedId}",
            "node=${event.nodeId}",
            "code=${event.getErrorCode()}",
            "inner=${event.getInnerCode()}"
        )
        if (BuildConfig.DEBUG) {
            parts += "msg=${event.getErrorMsg()}"
            parts += "innerMsg=${event.getInnerMsg()}"
        }
        return parts.joinToString(separator = " ")
    }

    private fun logFusionWarning(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG && throwable != null) {
            Log.w(TAG, message, throwable)
        } else {
            Log.w(TAG, message)
        }
    }

    private fun isValidMainlandPhoneNumber(raw: String): Boolean =
        raw.length == 11 && raw.firstOrNull() == '1' && raw.all(Char::isDigit)

    private fun Activity.isUsableForFusionAuth(): Boolean {
        if (isFinishing) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed) return false
        val state = (this as? LifecycleOwner)?.lifecycle?.currentState ?: return true
        return state.isAtLeast(Lifecycle.State.RESUMED)
    }
}

package com.nongjiqianwen

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
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

object FusionOneLoginClient {
    private const val TAG = "FusionOneLogin"
    private const val LOGIN_TEMPLATE_ID = "100001"
    private const val LOGIN_TIMEOUT_MS = 30_000L
    private const val VERIFY_FAILED_FALLBACK_MS = 1_500L
    private const val SERVICE_AGREEMENT_URL = "https://nongjiqiancha.cn/"
    private const val PRIVACY_POLICY_URL = "https://nongjiqiancha.cn/"
    private const val ONE_LOGIN_FALLBACK_MESSAGE = "一键登录未成功，请关闭代理/VPN、打开移动数据并确认默认数据卡；也可用验证码登录"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentBusiness: AlicomFusionBusiness? = null

    fun start(activity: Activity, onResult: (Boolean, String?) -> Unit) {
        AppCrashReporter.setAuthStage("auth.fusion_env_check")
        val environment = inspectAuthEnvironment(activity)
        val blockReason = environment.blockReason()
        if (blockReason != null) {
            AppCrashReporter.clearAuthStage("auth.fusion_env_check")
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
            if (snapshot?.usable != true) {
                AppCrashReporter.clearAuthStage("auth.fusion_token")
                reportAuthLog(
                    level = "warn",
                    event = "auth.fusion_token_failed",
                    stage = "fusion_token",
                    error = null
                )
                onResult(false, error ?: ONE_LOGIN_FALLBACK_MESSAGE)
                return@requestFusionAuthToken
            }
            startWithToken(activity, snapshot, onResult)
        }
    }

    private fun startWithToken(
        activity: Activity,
        snapshot: SessionApi.FusionAuthTokenSnapshot,
        onResult: (Boolean, String?) -> Unit
    ) {
        val business = AlicomFusionBusiness()
        val completed = AtomicBoolean(false)
        val sceneStarted = AtomicBoolean(false)
        AppCrashReporter.setAuthStage("auth.fusion_sdk_init")
        currentBusiness?.let { previous ->
            safeStopScene(previous)
            safeDestroy(previous)
        }
        currentBusiness = business

        AlicomFusionLog.setLogEnable(false)
        val token = AlicomFusionAuthToken().apply {
            setAuthToken(snapshot.authToken.orEmpty())
        }
        val initResult = runCatching {
            business.initWithToken(activity.applicationContext, snapshot.schemeCode.orEmpty(), token)
            business.adapterPageShape(true)
        }
        if (initResult.isFailure) {
            Log.w(TAG, "fusion sdk init failed", initResult.exceptionOrNull())
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
                    val fresh = SessionApi.requestFusionAuthTokenBlocking()
                    return AlicomFusionAuthToken().apply {
                        setAuthToken(fresh?.authToken.orEmpty())
                    }
                }

                override fun onSDKTokenAuthSuccess() {
                    activity.runOnUiThread {
                        if (!completed.get() && sceneStarted.compareAndSet(false, true)) {
                            val startResult = runCatching {
                                AppCrashReporter.setAuthStage("auth.fusion_scene_start")
                                business.startSceneWithTemplateId(
                                    activity,
                                    LOGIN_TEMPLATE_ID,
                                    createAuthUiCallback(business, completed, onResult)
                                )
                            }
                            if (startResult.isFailure) {
                                Log.w(TAG, "fusion scene start failed", startResult.exceptionOrNull())
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
                    val verifyToken = token.orEmpty().trim()
                    if (verifyToken.isEmpty()) {
                        safeContinueScene(business, false)
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
                    val verifyToken = maskToken.orEmpty().trim()
                    Log.w(TAG, "unexpected fusion half-way callback for one-login: node=$nodeName ${describeEvent(event)}")
                    reportAuthLog(
                        level = "warn",
                        event = "auth.fusion_halfway_unexpected",
                        stage = "halfway",
                        error = event,
                        nodeName = nodeName
                    )
                    verifyResult?.verifyResult(verifyToken.isNotEmpty())
                }

                override fun onVerifyFailed(error: AlicomFusionEvent?, nodeName: String?) {
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
                        finish(business, completed, false, ONE_LOGIN_FALLBACK_MESSAGE, onResult)
                    }, VERIFY_FAILED_FALLBACK_MS)
                }

                override fun onTemplateFinish(event: AlicomFusionEvent?) {
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

                override fun onAuthEvent(event: AlicomFusionEvent?) = Unit

                override fun onGetPhoneNumberForVerification(
                    nodeId: String?,
                    event: AlicomFusionEvent?
                ): String = ""

                override fun onVerifyInterrupt(event: AlicomFusionEvent?) = Unit
            })
        }.isSuccess
        if (!callbackAttached) {
            Log.w(TAG, "fusion callback attach failed")
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
                if (model == null) return
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
                .setLogBtnOffsetY(230)
                .setSwitchAccText("其他手机号登录")
                .setSwitchAccTextColor(Color.rgb(17, 17, 17))
                .setSwitchAccTextSizeDp(15)
                .setSwitchOffsetY(300)
                .setCheckboxHidden(false)
                .setCheckBoxWidth(18)
                .setCheckBoxHeight(18)
                .setPrivacyState(false)
                .setPrivacyBefore("我已阅读并同意")
                .setPrivacyEnd("")
                .setPrivacyTextSizeDp(11)
                .setPrivacyMargin(20)
                .setPrivacyOffsetY_B(64)
                .setAppPrivacyOne("《服务协议》", SERVICE_AGREEMENT_URL)
                .setAppPrivacyTwo("《隐私政策》", PRIVACY_POLICY_URL)
                .setAppPrivacyColor(Color.rgb(17, 17, 17), Color.rgb(87, 93, 102))
                .setProtocolGravity(Gravity.CENTER)
                .setProtocolLayoutGravity(Gravity.CENTER_HORIZONTAL)
                .setPrivacyAlertIsNeedShow(false)
                .setLogBtnToastHidden(false)
                .setPageBackgroundDrawable(GradientDrawable().apply { setColor(Color.WHITE) })
                .create()
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
        val hasWifiTransport: Boolean,
        val hasVpnTransport: Boolean,
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
                "wifi".takeIf { hasWifiTransport },
                "vpn".takeIf { hasVpnTransport }
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
                else -> null
            }

        fun warningReason(): String? =
            when {
                hasVpnTransport -> "vpn_active"
                !hasCellularTransport -> "no_active_cellular"
                else -> null
            }

        fun userMessageFor(reason: String): String =
            when (reason) {
                "no_network" -> "当前网络不可用，请联网后重试，或使用验证码登录"
                "no_sim" -> "未检测到可用 SIM 卡，请插卡并打开移动数据，或使用验证码登录"
                "sim_not_ready" -> "SIM 卡暂不可用，请确认默认移动数据卡正常，或使用验证码登录"
                else -> ONE_LOGIN_FALLBACK_MESSAGE
            }
    }

    private fun inspectAuthEnvironment(context: Context): AuthEnvironmentSnapshot {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivity?.activeNetwork
        val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
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
            hasWifiTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            hasVpnTransport = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
            simState = simState,
            simCount = simCount
        )
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
            if (currentBusiness === business) {
                currentBusiness = null
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
        return listOf(
            "template=${event.templatedId}",
            "node=${event.nodeId}",
            "code=${event.getErrorCode()}",
            "inner=${event.getInnerCode()}",
            "msg=${event.getErrorMsg()}",
            "innerMsg=${event.getInnerMsg()}"
        ).joinToString(separator = " ")
    }
}

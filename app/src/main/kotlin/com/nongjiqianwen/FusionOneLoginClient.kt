package com.nongjiqianwen

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import com.alicom.fusion.auth.smsauth.AlicomFusionAutoInputView
import com.alicom.fusion.auth.smsauth.AlicomFusionInputView
import com.alicom.fusion.auth.smsauth.AlicomFusionSendVerifyCodeView
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
    private const val SERVICE_AGREEMENT_URL = "https://nongjiqiancha.cn/legal/user-agreement/"
    private const val PRIVACY_POLICY_URL = "https://nongjiqiancha.cn/legal/privacy-policy/"
    private const val FUSION_SMS_FALLBACK_MESSAGE = "融合认证未完成，请稍后再试"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loginInFlight = AtomicBoolean(false)
    private val loginGeneration = AtomicLong(0L)

    @Volatile
    private var currentBusiness: AlicomFusionBusiness? = null

    @Volatile
    private var currentCompleted: AtomicBoolean? = null

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

    fun startSmsLogin(
        activity: Activity,
        verificationPhone: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        val normalizedPhone = verificationPhone
            ?.filter(Char::isDigit)
            ?.takeIf(::isValidMainlandPhoneNumber)
        if (normalizedPhone == null) {
            onResult(false, "请输入正确的手机号")
            return
        }
        start(
            activity = activity,
            verificationPhone = normalizedPhone,
            fallbackMessage = FUSION_SMS_FALLBACK_MESSAGE,
            onResult = onResult
        )
    }

    private fun start(
        activity: Activity,
        verificationPhone: String? = null,
        fallbackMessage: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (!loginInFlight.compareAndSet(false, true)) {
            onResult(false, "融合认证正在处理中，请稍候")
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
            onResult(false, "融合认证暂不可用，请稍后再试")
            return
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
                onResult(false, error ?: fallbackMessage)
                return@requestFusionAuthToken
            }
            if (!activity.isUsableForFusionAuth()) {
                AppCrashReporter.clearAuthStage("auth.fusion_token")
                loginInFlight.set(false)
                onResult(false, "融合认证已取消，请稍后再试")
                return@requestFusionAuthToken
            }
            startWithToken(activity, snapshot, verificationPhone, generation, fallbackMessage, onResult)
        }
    }

    private fun startWithToken(
        activity: Activity,
        snapshot: SessionApi.FusionAuthTokenSnapshot,
        verificationPhone: String?,
        generation: Long,
        fallbackMessage: String,
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
            business.useSDKSupplyCaptchaModule(false)
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
            finish(business, completed, false, fallbackMessage, onResult)
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
                            finish(business, completed, false, "融合认证已取消，请稍后再试", onResult)
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
                                    "融合认证已取消，请稍后再试",
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
                                    fallbackMessage,
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
                    finish(business, completed, false, fallbackMessage, onResult)
                }

                override fun onVerifySuccess(
                    token: String?,
                    nodeName: String?,
                    event: AlicomFusionEvent?
                ) {
                    AppCrashReporter.setAuthStage("auth.fusion_verify_success")
                    if (generation != loginGeneration.get()) {
                        finish(business, completed, false, "融合认证已取消，请稍后再试", onResult)
                        return
                    }
                    val verifyToken = token.orEmpty().trim()
                    if (verifyToken.isEmpty()) {
                        reportAuthLog(
                            level = "warn",
                            event = "auth.fusion_empty_verify_token",
                            stage = "verify",
                            error = event,
                            nodeName = nodeName
                        )
                        finish(business, completed, false, fallbackMessage, onResult)
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
                                loginError ?: fallbackMessage,
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
                    Log.w(TAG, "unexpected fusion half-way callback for sms login: node=$nodeName ${describeEvent(event)}")
                    reportAuthLog(
                        level = "warn",
                        event = "auth.fusion_halfway_unexpected",
                        stage = "halfway",
                        error = event,
                        nodeName = nodeName
                    )
                    verifyResult?.verifyResult(false)
                    finish(business, completed, false, fallbackMessage, onResult)
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
                    finish(business, completed, false, fallbackMessage, onResult)
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
                    finish(business, completed, false, fallbackMessage, onResult)
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
                        finish(
                            business,
                            completed,
                            false,
                            "请输入正确的手机号",
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
                    finish(business, completed, false, fallbackMessage, onResult)
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
            finish(business, completed, false, fallbackMessage, onResult)
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
            finish(business, completed, false, fallbackMessage, onResult)
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
                        finish(business, completed, false, "融合认证未完成，请稍后再试", onResult)
                    }
                )
            }

            override fun onSMSCodeVerifyUICustomView(
                templateId: String?,
                nodeId: String?,
                isAutoInput: Boolean,
                view: AlicomFusionVerifyCodeView?
            ) {
                view?.let(::configureSmsCodeView)
            }

            override fun onSMSSendVerifyUICustomView(
                templateId: String?,
                nodeId: String?,
                view: AlicomFusionUpSMSView?,
                phoneNumber: String?,
                smsContent: String?
            ) {
                view?.let(::configureUpSmsView)
            }
        }

    private fun configureSmsCodeView(view: AlicomFusionVerifyCodeView) {
        runCatching {
            view.getRootRl()?.setBackgroundColor(Color.WHITE)
            view.getTitleRl()?.setBackgroundColor(Color.WHITE)
            view.getContentRL()?.setBackgroundColor(Color.WHITE)
            view.getTitleContentTV()?.apply {
                text = "验证码登录"
                setTextColor(Color.rgb(17, 17, 17))
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            configureSmsInputView(view.getInputView())
            configureSmsAutoInputView(view.getAutoInputView())
            configureSmsSendCodeView(view.getSendVerifyCodeView())
        }.onFailure {
            reportAuthLog(
                level = "warn",
                event = "auth.fusion_sms_ui_config_failed",
                stage = "sms_ui_config",
                error = null
            )
        }
    }

    private fun configureSmsInputView(view: AlicomFusionInputView?) {
        view ?: return
        view.getInputNumberRootRL()?.setBackgroundColor(Color.WHITE)
        view.getInputNumberET()?.apply {
            hint = "手机号"
            setTextColor(Color.rgb(17, 17, 17))
            setHintTextColor(Color.rgb(112, 118, 128))
            textSize = 16f
        }
        view.getmCountryTV()?.apply {
            setTextColor(Color.rgb(17, 17, 17))
            textSize = 16f
        }
        view.getInputNumberRequestCodeTV()?.apply {
            text = "发送验证码"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = loginButtonBackground()
        }
        view.getPrivacyTV()?.setTextColor(Color.rgb(87, 93, 102))
    }

    private fun configureSmsAutoInputView(view: AlicomFusionAutoInputView?) {
        view ?: return
        view.getAutoInputRootRL()?.setBackgroundColor(Color.WHITE)
        view.getAutoInputPhoneNumTV()?.apply {
            setTextColor(Color.rgb(17, 17, 17))
            textSize = 22f
        }
        view.getAutoInputPhoneHintTV()?.apply {
            text = "请接收短信验证码"
            setTextColor(Color.rgb(87, 93, 102))
            textSize = 13f
        }
        view.getAutoInputRequestCodeTV()?.apply {
            text = "发送验证码"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = loginButtonBackground()
        }
    }

    private fun configureSmsSendCodeView(view: AlicomFusionSendVerifyCodeView?) {
        view ?: return
        view.getSendsmsRootRl()?.setBackgroundColor(Color.WHITE)
        view.getSendSmsHint()?.apply {
            setTextColor(Color.rgb(87, 93, 102))
            textSize = 16f
        }
        val codeBoxBackground = codeInputBackground()
        listOf(
            view.getFirstCode(),
            view.getSecondCode(),
            view.getThirdCode(),
            view.getFourCode(),
            view.getFivthCode(),
            view.getSixthCode()
        ).forEach { code ->
            code?.apply {
                setTextColor(Color.rgb(17, 17, 17))
                setHintTextColor(Color.rgb(144, 149, 158))
                textSize = 18f
                background = codeBoxBackground.constantState?.newDrawable() ?: codeInputBackground()
            }
        }
        view.getSendSmsCode()?.apply {
            text = "登录"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            background = loginButtonBackground()
        }
    }

    private fun configureUpSmsView(view: AlicomFusionUpSMSView) {
        runCatching {
            view.getRootRl()?.setBackgroundColor(Color.WHITE)
            view.getTitleRl()?.setBackgroundColor(Color.WHITE)
            view.getTitleContentTV()?.apply {
                text = "短信验证"
                setTextColor(Color.rgb(17, 17, 17))
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            listOf(
                view.getSmsRemindTV(),
                view.getSmsContentTV(),
                view.getReceiveSmsNumberTV(),
                view.getHadSenTSmsTV()
            ).forEach { text ->
                text?.setTextColor(Color.rgb(87, 93, 102))
            }
            view.getSendSmsTV()?.apply {
                setTextColor(Color.WHITE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                background = loginButtonBackground()
            }
        }.onFailure {
            reportAuthLog(
                level = "warn",
                event = "auth.fusion_upsms_ui_config_failed",
                stage = "upsms_ui_config",
                error = null
            )
        }
    }

    private fun configureNumberAuthModel(model: FusionNumberAuthModel) {
        val configured = runCatching {
            model.getBuilder()
                .setStatusBarColor(Color.WHITE)
                .setBottomNavColor(Color.WHITE)
                .setLightColor(true)
                .setNavHidden(true)
                .hiddenLoginText(true)
                .setNavReturnHidden(false)
                .setNavText("农技千查")
                .setNavTextColor(Color.rgb(17, 17, 17))
                .setNavTextSizeDp(17)
                .setLogoHidden(true)
                .setSloganHidden(true)
                .hiddenNumberCountry(true)
                .setNumberColor(Color.rgb(17, 17, 17))
                .setNumberSizeDp(26)
                .setNumberLayoutGravity(Gravity.CENTER_HORIZONTAL)
                .setNumFieldOffsetY(118)
                .setLogBtnText("验证码登录")
                .setLogBtnTextColor(Color.WHITE)
                .setLogBtnTextSizeDp(17)
                .setLogBtnBackgroundDrawable(loginButtonBackground())
                .setLogBtnHeight(50)
                .setLogBtnMarginLeftAndRight(32)
                .setLogBtnOffsetY(198)
                .hiddenSwtichLogin(true)
                .setSwitchAccText("其他手机号登录")
                .setSwitchAccTextColor(Color.rgb(17, 17, 17))
                .setSwitchAccTextSizeDp(15)
                .setSwitchOffsetY(286)
                .setCheckboxHidden(false)
                .setCheckBoxWidth(18)
                .setCheckBoxHeight(18)
                .setPrivacyState(false)
                .setPrivacyBefore("我已阅读并同意")
                .setPrivacyEnd("")
                .setPrivacyTextSizeDp(11)
                .setPrivacyMargin(20)
                .setPrivacyOffsetY_B(52)
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

    private fun codeInputBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            setStroke(1, Color.rgb(183, 188, 196))
            cornerRadius = 6f
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

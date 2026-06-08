package com.nongjiqianwen

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentBusiness: AlicomFusionBusiness? = null

    fun start(activity: Activity, onResult: (Boolean, String?) -> Unit) {
        SessionApi.requestFusionAuthToken { snapshot, error ->
            if (snapshot?.usable != true) {
                reportAuthLog(
                    level = "warn",
                    event = "auth.fusion_token_failed",
                    stage = "fusion_token",
                    error = null
                )
                onResult(false, error ?: "一键登录暂不可用，请使用验证码登录")
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
            finish(business, completed, false, "一键登录暂不可用，请使用验证码登录", onResult)
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
                                    "一键登录暂不可用，请使用验证码登录",
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
                    finish(business, completed, false, "一键登录暂不可用，请使用验证码登录", onResult)
                }

                override fun onVerifySuccess(
                    token: String?,
                    nodeName: String?,
                    event: AlicomFusionEvent?
                ) {
                    val verifyToken = token.orEmpty().trim()
                    if (verifyToken.isEmpty()) {
                        safeContinueScene(business, false)
                        return
                    }
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
                                loginError ?: "一键登录校验失败，请使用验证码登录",
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
                        finish(business, completed, false, "一键登录校验失败，请使用验证码登录", onResult)
                    }, VERIFY_FAILED_FALLBACK_MS)
                }

                override fun onTemplateFinish(event: AlicomFusionEvent?) {
                    Log.i(TAG, "fusion template finished: ${describeEvent(event)}")
                    reportAuthLog(
                        level = "info",
                        event = "auth.fusion_template_finished",
                        stage = "template_finish",
                        error = event
                    )
                    finish(business, completed, false, "一键登录未完成，请使用验证码登录", onResult)
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
            finish(business, completed, false, "一键登录暂不可用，请使用验证码登录", onResult)
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
            finish(business, completed, false, "一键登录超时，请使用验证码登录", onResult)
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

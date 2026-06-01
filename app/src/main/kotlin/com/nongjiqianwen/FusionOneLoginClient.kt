package com.nongjiqianwen

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.alicom.fusion.auth.AlicomFusionAuthCallBack
import com.alicom.fusion.auth.AlicomFusionBusiness
import com.alicom.fusion.auth.HalfWayVerifyResult
import com.alicom.fusion.auth.AlicomFusionLog
import com.alicom.fusion.auth.error.AlicomFusionEvent
import com.alicom.fusion.auth.token.AlicomFusionAuthToken
import java.util.concurrent.atomic.AtomicBoolean

object FusionOneLoginClient {
    private const val LOGIN_TEMPLATE_ID = "100001"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentBusiness: AlicomFusionBusiness? = null

    fun start(activity: Activity, onResult: (Boolean, String?) -> Unit) {
        SessionApi.requestFusionAuthToken { snapshot, error ->
            if (snapshot?.usable != true) {
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
        currentBusiness?.let { previous ->
            runCatching { previous.stopSceneWithTemplateId(LOGIN_TEMPLATE_ID) }
            runCatching { previous.destory() }
        }
        currentBusiness = business

        AlicomFusionLog.setLogEnable(false)
        business.setAlicomFusionAuthCallBack(object : AlicomFusionAuthCallBack {
            override fun onSDKTokenUpdate(): AlicomFusionAuthToken {
                val fresh = SessionApi.requestFusionAuthTokenBlocking()
                return AlicomFusionAuthToken().apply {
                    setAuthToken(fresh?.authToken.orEmpty())
                }
            }

            override fun onSDKTokenAuthSuccess() {
                activity.runOnUiThread {
                    if (!completed.get()) {
                        business.startSceneWithTemplateId(activity, LOGIN_TEMPLATE_ID)
                    }
                }
            }

            override fun onSDKTokenAuthFailure(
                failToken: AlicomFusionAuthToken?,
                error: AlicomFusionEvent?
            ) {
                finish(business, completed, false, "一键登录初始化失败，请使用验证码登录", onResult)
            }

            override fun onVerifySuccess(
                token: String?,
                nodeName: String?,
                event: AlicomFusionEvent?
            ) {
                val verifyToken = token.orEmpty().trim()
                if (verifyToken.isEmpty()) {
                    runCatching { business.continueSceneWithTemplateId(LOGIN_TEMPLATE_ID, false) }
                    return
                }
                SessionApi.loginWithFusionVerifyToken(verifyToken) { ok, loginError ->
                    if (ok) {
                        runCatching { business.continueSceneWithTemplateId(LOGIN_TEMPLATE_ID, true) }
                        finish(business, completed, true, null, onResult)
                    } else {
                        runCatching { business.continueSceneWithTemplateId(LOGIN_TEMPLATE_ID, false) }
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
                if (verifyToken.isEmpty()) {
                    verifyResult?.verifyResult(false)
                    return
                }
                SessionApi.loginWithFusionVerifyToken(verifyToken) { ok, _ ->
                    verifyResult?.verifyResult(ok)
                }
            }

            override fun onVerifyFailed(error: AlicomFusionEvent?, nodeName: String?) {
                runCatching { business.continueSceneWithTemplateId(LOGIN_TEMPLATE_ID, false) }
            }

            override fun onTemplateFinish(event: AlicomFusionEvent?) {
                finish(business, completed, false, "一键登录未完成，请使用验证码登录", onResult)
            }

            override fun onAuthEvent(event: AlicomFusionEvent?) = Unit

            override fun onGetPhoneNumberForVerification(
                nodeId: String?,
                event: AlicomFusionEvent?
            ): String = ""

            override fun onVerifyInterrupt(event: AlicomFusionEvent?) = Unit
        })

        val token = AlicomFusionAuthToken().apply {
            setAuthToken(snapshot.authToken.orEmpty())
        }
        business.initWithToken(activity.applicationContext, snapshot.schemeCode.orEmpty(), token)
        business.adapterPageShape(true)
    }

    private fun finish(
        business: AlicomFusionBusiness,
        completed: AtomicBoolean,
        ok: Boolean,
        message: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (!completed.compareAndSet(false, true)) return
        runCatching { business.stopSceneWithTemplateId(LOGIN_TEMPLATE_ID) }
        runCatching { business.destory() }
        if (currentBusiness === business) {
            currentBusiness = null
        }
        mainHandler.post {
            onResult(ok, message)
        }
    }
}

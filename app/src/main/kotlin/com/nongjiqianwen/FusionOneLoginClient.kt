package com.nongjiqianwen

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.alicom.fusion.auth.AlicomFusionAuthCallBack
import com.alicom.fusion.auth.AlicomFusionBusiness
import com.alicom.fusion.auth.HalfWayVerifyResult
import com.alicom.fusion.auth.AlicomFusionLog
import com.alicom.fusion.auth.error.AlicomFusionEvent
import com.alicom.fusion.auth.token.AlicomFusionAuthToken
import java.util.concurrent.atomic.AtomicBoolean

object FusionOneLoginClient {
    private const val TAG = "FusionOneLogin"
    private const val LOGIN_TEMPLATE_ID = "100001"
    private const val LOGIN_TIMEOUT_MS = 30_000L
    private const val VERIFY_FAILED_FALLBACK_MS = 1_500L
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
        val sceneStarted = AtomicBoolean(false)
        currentBusiness?.let { previous ->
            runCatching { previous.stopSceneWithTemplateId(LOGIN_TEMPLATE_ID) }
            runCatching { previous.destory() }
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
                                business.startSceneWithTemplateId(activity, LOGIN_TEMPLATE_ID)
                            }
                            if (startResult.isFailure) {
                                Log.w(TAG, "fusion scene start failed", startResult.exceptionOrNull())
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
                    SessionApi.loginWithFusionVerifyToken(verifyToken) { ok, loginError ->
                        if (ok) {
                            safeContinueScene(business, true)
                            finish(business, completed, true, null, onResult)
                        } else {
                            safeContinueScene(business, false)
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
                    SessionApi.verifyFusionTokenOnly(verifyToken) { ok ->
                        verifyResult?.verifyResult(ok)
                    }
                }

                override fun onVerifyFailed(error: AlicomFusionEvent?, nodeName: String?) {
                    Log.w(TAG, "fusion verify failed: node=$nodeName ${describeEvent(error)}")
                    safeContinueScene(business, false)
                    mainHandler.postDelayed({
                        finish(business, completed, false, "一键登录校验失败，请使用验证码登录", onResult)
                    }, VERIFY_FAILED_FALLBACK_MS)
                }

                override fun onTemplateFinish(event: AlicomFusionEvent?) {
                    Log.i(TAG, "fusion template finished: ${describeEvent(event)}")
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
            finish(business, completed, false, "一键登录暂不可用，请使用验证码登录", onResult)
            return
        }
        mainHandler.postDelayed({
            finish(business, completed, false, "一键登录超时，请使用验证码登录", onResult)
        }, LOGIN_TIMEOUT_MS)
    }

    private fun finish(
        business: AlicomFusionBusiness,
        completed: AtomicBoolean,
        ok: Boolean,
        message: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (!completed.compareAndSet(false, true)) return
        safeStopScene(business)
        safeDestroy(business)
        if (currentBusiness === business) {
            currentBusiness = null
        }
        mainHandler.post {
            onResult(ok, message)
        }
    }

    private fun safeContinueScene(business: AlicomFusionBusiness, passed: Boolean) {
        runCatching { business.continueSceneWithTemplateId(LOGIN_TEMPLATE_ID, passed) }
    }

    private fun safeStopScene(business: AlicomFusionBusiness) {
        runCatching { business.stopSceneWithTemplateId(LOGIN_TEMPLATE_ID) }
    }

    private fun safeDestroy(business: AlicomFusionBusiness) {
        runCatching { business.destory() }
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

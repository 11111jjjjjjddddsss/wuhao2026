package com.nongjiqianwen

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class FusionAuthProtocolActivity : Activity() {
    private val allowedProtocolHosts = setOf("nongjiqiancha.cn", "www.nongjiqiancha.cn")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pageUrlCandidates = protocolUrlCandidates()
        val pageUrl = pageUrlCandidates.firstOrNull(::isAllowedProtocolUrl)
        val pageTitle = resolveProtocolTitle()
        var fallbackShown = false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        root.addView(
            TextView(this).apply {
                text = pageTitle
                textSize = 17f
                gravity = Gravity.CENTER
                setTextColor(0xFF111111.toInt())
                setOnClickListener { finish() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (52 * resources.displayMetrics.density).toInt()
                )
            }
        )

        if (pageUrl == null) {
            reportProtocolLog(
                event = "auth.fusion_protocol_url_unavailable",
                message = "fusion auth protocol url unavailable",
                attrs = mapOf(
                    "reason" to if (pageUrlCandidates.isEmpty()) "missing" else "invalid",
                    "candidate_count" to pageUrlCandidates.size.coerceAtMost(8)
                )
            )
            fallbackShown = true
            root.addProtocolFallback(
                pageTitle = pageTitle,
                pageUrl = pageUrlCandidates.firstOrNull(),
                note = "协议网页暂时无法打开，下面先显示 App 内置协议要点。"
            )
            setContentView(root)
            return
        }

        root.addView(
            WebView(this).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString().orEmpty()
                        return if (isAllowedProtocolUrl(url)) {
                            false
                        } else {
                            reportProtocolLog(
                                event = "auth.fusion_protocol_navigation_blocked",
                                message = "fusion auth protocol navigation blocked",
                                attrs = mapOf(
                                    "scheme" to safeScheme(request?.url?.scheme),
                                    "is_main_frame" to (request?.isForMainFrame == true)
                                )
                            )
                            true
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            reportProtocolLog(
                                event = "auth.fusion_protocol_load_failed",
                                message = "fusion auth protocol page load failed",
                                attrs = mapOf(
                                    "error_code" to (error?.errorCode ?: 0),
                                    "scheme" to safeScheme(request.url?.scheme)
                                )
                            )
                            if (!fallbackShown) {
                                fallbackShown = true
                                val failedWebView = view
                                failedWebView?.post {
                                    root.removeView(failedWebView)
                                    root.addProtocolFallback(
                                        pageTitle = pageTitle,
                                        pageUrl = pageUrl,
                                        note = "协议网页加载失败，下面先显示 App 内置协议要点。"
                                    )
                                }
                            }
                        }
                    }
                }
                loadUrl(pageUrl)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
        )
        setContentView(root)
    }

    override fun onDestroy() {
        findWebView(window.decorView as? ViewGroup)?.let { webView ->
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun protocolUrlCandidates(): List<String> =
        sequenceOf(
            intent?.dataString,
            intent?.getStringExtra("url"),
            intent?.getStringExtra("URL"),
            intent?.getStringExtra("protocolUrl"),
            intent?.getStringExtra("protocolURL"),
            intent?.getStringExtra("PROTOCOL_WEB_VIEW_URL")
        )
            .mapNotNull { it?.trim() }
            .filter { it.isNotEmpty() }
            .toList()

    private fun resolveProtocolTitle(): String =
        sequenceOf(
            intent?.getStringExtra("title"),
            intent?.getStringExtra("name"),
            intent?.getStringExtra("protocolName")
        )
            .mapNotNull { it?.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.take(32)
            ?: "服务协议"

    private fun isAllowedProtocolUrl(raw: String): Boolean {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
        val host = uri.host?.trim()?.lowercase(Locale.US) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && host in allowedProtocolHosts
    }

    private fun LinearLayout.addProtocolFallback(
        pageTitle: String,
        pageUrl: String?,
        note: String
    ) {
        addView(
            ScrollView(context).apply {
                addView(
                    TextView(context).apply {
                        text = buildString {
                            append(note)
                            append("\n\n")
                            append(resolveFallbackProtocolText(pageTitle, pageUrl))
                        }
                        textSize = 15f
                        setTextColor(0xFF30343A.toInt())
                        setLineSpacing(0f, 1.18f)
                        setPadding(dp(22), dp(18), dp(22), dp(28))
                    }
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
        )
    }

    private fun resolveFallbackProtocolText(pageTitle: String, pageUrl: String?): String {
        val path = runCatching { Uri.parse(pageUrl.orEmpty()).encodedPath.orEmpty() }
            .getOrDefault("")
            .lowercase(Locale.US)
        val isPrivacy = pageTitle.contains("隐私") ||
            path.contains("privacy") ||
            path.contains("privacy-policy")
        return if (isPrivacy) {
            """
            隐私政策

            更新日期：2026年6月1日
            生效日期：2026年6月1日
            服务提供者：北京农技千问科技有限公司
            联系邮箱：465989879@qq.com

            我们只在实现农技千查功能、履行协议、保障安全、处理投诉争议和依法合规所需范围内处理个人信息。手机号用于登录认证、账号识别、历史恢复和权益核对；本机号码一键登录需要读取电话状态和必要设备网络状态，用于判断 SIM 卡、运营商网络和认证环境；App 不读取通讯录、短信内容或通话记录。

            AI 问诊会处理您主动输入的文字、上传的图片、必要历史上下文、AI 回复、摘要、后端服务器时间、粗略地区信息和可信度。定位权限只用于把系统定位反查为省、市、区县等地区文本；我们不上传经纬度，不保存轨迹。未授权、定位失败或系统无法反查时，后端会使用网络 IP 粗略判断或未知兜底。

            照片入口使用 Android 系统 Photo Picker，只访问您本次主动选择的图片；拍照入口调用外部相机，通过 FileProvider 临时授权外部相机写入 App 创建的 URI。帮助与反馈、礼品卡兑换、会员额度和检查更新只处理完成相关功能所必需的信息。

            本版本不包含广告、地图、推送、统计 SDK、支付 SDK、友盟、Bugly、极光或 Firebase；不申请通讯录、短信、录音或通知权限。您可以通过 App 内帮助与反馈或邮箱联系我们，依法行使查询、更正、删除、注销等权利。
            """.trimIndent()
        } else {
            """
            用户协议

            更新日期：2026年5月25日
            生效日期：2026年5月25日
            服务提供者：北京农技千问科技有限公司
            联系邮箱：465989879@qq.com

            农技千查面向农业种植、作物管理、病虫害线索排查、农资信息理解和田间管理复盘等场景，提供基于文字、图片和图文混合输入的农业技术参考服务。AI 回复、今日农情和客服回复均不构成官方认定、检测报告、行政结论、专家签字意见、收益承诺或唯一处理方案。

            您提交的文字、图片、反馈和补充材料仍归您或原权利人所有。请确保您有权提交这些内容，并尽量只上传与农业问题相关的材料；不要上传身份证件、银行卡、人脸隐私、他人隐私、商业秘密、违法侵权或与服务无关的内容。

            涉及农药、肥料、种子、调节剂、检疫、补贴、备案、登记、审定、质量争议、赔付或重大生产决策时，请以产品标签、官方平台、当地农业农村部门、检测机构、农技人员或其他有资质主体意见为准。

            会员套餐、每日次数、加油包、升级补偿、优惠、礼品卡、订单、退款和权益生效规则，以 App 页面、后端记录、实际支付或兑换结果及法律规定为准。本版本不提供真实支付、自动续费或农资商品交易。

            请不要攻击接口、刷量、绕过额度、逆向工程、批量撞库礼品卡、转售账号权益或恶意消耗服务资源。我们可能根据产品、法律法规或运营需要更新本协议，重要变化会以 App 内页面、弹窗或其他合理方式提示。
            """.trimIndent()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun safeScheme(raw: String?): String =
        raw
            ?.trim()
            ?.lowercase()
            ?.filter { it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' }
            ?.take(24)
            ?.ifBlank { "unknown" }
            ?: "unknown"

    private fun reportProtocolLog(
        event: String,
        message: String,
        attrs: Map<String, Any?>
    ) {
        SessionApi.reportAuthClientLog(
            level = "warn",
            event = event,
            message = message,
            attrs = attrs
        )
    }

    private fun findWebView(group: ViewGroup?): WebView? {
        group ?: return null
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child is WebView) return child
            if (child is ViewGroup) {
                findWebView(child)?.let { return it }
            }
        }
        return null
    }
}

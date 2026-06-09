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
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class FusionAuthProtocolActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pageUrlCandidates = protocolUrlCandidates()
        val pageUrl = pageUrlCandidates.firstOrNull(::isAllowedProtocolUrl)
        val pageTitle = resolveProtocolTitle()
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
            root.addView(
                TextView(this).apply {
                    text = "协议页面暂时无法打开，请返回后重试"
                    textSize = 15f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF575D66.toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                }
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
        return uri.scheme.equals("https", ignoreCase = true) ||
            uri.scheme.equals("http", ignoreCase = true)
    }

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

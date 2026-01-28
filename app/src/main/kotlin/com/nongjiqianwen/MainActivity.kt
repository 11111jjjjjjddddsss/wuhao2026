package com.nongjiqianwen

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var isRequesting = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建WebView
        webView = WebView(this)
        setContentView(webView)
        
        // 配置WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
        }
        
        // 注入JavaScript接口
        webView.addJavascriptInterface(AndroidJSInterface(), "AndroidInterface")
        
        // 设置WebViewClient
        webView.webViewClient = WebViewClient()
        
        // 加载HTML文件
        webView.loadUrl("file:///android_asset/gpt-demo.html")
    }
    
    /**
     * JavaScript接口，用于WebView与Android通信
     */
    inner class AndroidJSInterface {
        @JavascriptInterface
        fun sendMessage(text: String, imageUrlListJson: String) {
            runOnUiThread {
                if (isRequesting) return@runOnUiThread
                
                // 解析图片URL列表（必须是公网可访问的URL）
                val imageUrlList = try {
                    if (imageUrlListJson == "null" || imageUrlListJson.isBlank()) {
                        emptyList()
                    } else {
                        val jsonElement = com.google.gson.JsonParser().parse(imageUrlListJson)
                        if (jsonElement.isJsonNull) {
                            emptyList()
                        } else {
                            val jsonArray = jsonElement.asJsonArray
                            // 只保留有效的HTTP/HTTPS URL
                            jsonArray.mapNotNull { element ->
                                val url = element.asString
                                if (url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                                    url
                                } else {
                                    null
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "解析图片URL列表失败", e)
                    emptyList()
                }
                
                // 检查是否有输入
                val hasInput = text.isNotBlank() || imageUrlList.isNotEmpty()
                if (!hasInput) return@runOnUiThread
                
                // 设置请求状态
                isRequesting = true
                
                // 发送请求
                ModelService.getReply(
                    userMessage = text,
                    imageUrlList = imageUrlList,
                    onChunk = { chunk ->
                        runOnUiThread {
                            // 调用JavaScript函数，传递chunk（转义特殊字符）
                            val escapedChunk = chunk
                                .replace("\\", "\\\\")
                                .replace("'", "\\'")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r")
                            webView.evaluateJavascript("window.onChunkReceived('$escapedChunk');", null)
                        }
                    },
                    onComplete = {
                        runOnUiThread {
                            isRequesting = false
                            // 调用JavaScript函数，通知完成
                            webView.evaluateJavascript("window.onCompleteReceived();", null)
                        }
                    }
                )
            }
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

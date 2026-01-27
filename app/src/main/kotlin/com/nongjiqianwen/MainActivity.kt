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
        fun sendMessage(text: String, imageBase64ListJson: String) {
            runOnUiThread {
                if (isRequesting) return@runOnUiThread
                
                // 解析图片Base64列表
                // 无图时传递 null，确保 images 字段不存在
                // 严格校验：每一项必须非空且长度合理
                val imageBase64List = try {
                    if (imageBase64ListJson == "null" || imageBase64ListJson.isBlank()) {
                        emptyList()
                    } else {
                        val jsonElement = com.google.gson.JsonParser().parse(imageBase64ListJson)
                        if (jsonElement.isJsonNull) {
                            emptyList()
                        } else {
                            val jsonArray = jsonElement.asJsonArray
                            // 严格过滤：只保留非空且长度合理的 base64 字符串
                            jsonArray.mapNotNull { element ->
                                val base64 = element.asString
                                // 校验：非空、长度合理（至少 10 字符）、不包含非法字符
                                if (base64.isNotBlank() && 
                                    base64.length >= 10 && 
                                    !base64.contains("content://") && 
                                    !base64.contains("file://")) {
                                    base64
                                } else {
                                    null
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "解析图片列表失败", e)
                    emptyList()
                }
                
                // 检查是否有输入
                val hasInput = text.isNotBlank() || imageBase64List.isNotEmpty()
                if (!hasInput) return@runOnUiThread
                
                // 设置请求状态
                isRequesting = true
                
                // 发送请求
                ModelService.getReply(
                    userMessage = text,
                    imageBase64List = imageBase64List,
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

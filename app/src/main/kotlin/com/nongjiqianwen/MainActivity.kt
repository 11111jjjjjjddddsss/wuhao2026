package com.nongjiqianwen

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream
import java.io.InputStream

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
                
                // 验证：有图必须有文字
                val hasText = text.isNotBlank()
                val hasImages = imageBase64ListJson != "null" && imageBase64ListJson.isNotBlank()
                
                if (hasImages && !hasText) {
                    Log.e("MainActivity", "有图片但无文字，阻止发送")
                    webView.evaluateJavascript("alert('图片必须带文字说明，请输入文字后再发送');", null)
                    return@runOnUiThread
                }
                
                // 检查是否有输入
                if (!hasText && !hasImages) return@runOnUiThread
                
                // 设置请求状态
                isRequesting = true
                
                // 处理图片：base64 → 压缩 → 上传 → 获取URL
                if (hasImages) {
                    processImagesAndSend(text, imageBase64ListJson)
                } else {
                    // 纯文本，直接发送
                    sendToModel(text, emptyList())
                }
            }
        }
        
        /**
         * 处理图片：解析base64 → 压缩 → 上传 → 获取URL → 发送模型请求
         */
        private fun processImagesAndSend(text: String, imageBase64ListJson: String) {
            Thread {
                try {
                    // 解析base64列表
                    val imageBase64List = try {
                        val jsonElement = com.google.gson.JsonParser().parse(imageBase64ListJson)
                        if (jsonElement.isJsonNull) {
                            emptyList()
                        } else {
                            val jsonArray = jsonElement.asJsonArray
                            jsonArray.mapNotNull { element ->
                                val base64 = element.asString
                                if (base64.isNotBlank() && base64.length > 10) {
                                    base64
                                } else {
                                    null
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "解析图片base64列表失败", e)
                        emptyList()
                    }
                    
                    // 验证图片数量（最多4张）
                    if (imageBase64List.size > 4) {
                        runOnUiThread {
                            isRequesting = false
                            webView.evaluateJavascript("alert('最多只能选择4张图片');", null)
                        }
                        return@Thread
                    }
                    
                    if (imageBase64List.isEmpty()) {
                        // 没有有效图片，按纯文本处理
                        sendToModel(text, emptyList())
                        return@Thread
                    }
                    
                    Log.d("MainActivity", "开始处理 ${imageBase64List.size} 张图片")
                    
                    // base64 → 压缩 → 上传
                    val imageBytesList = mutableListOf<ByteArray>()
                    imageBase64List.forEachIndexed { index, base64 ->
                        try {
                            // base64解码
                            val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                            
                            // 压缩图片
                            val inputStream = ByteArrayInputStream(imageBytes)
                            val compressedBytes = ImageUploader.compressImage(inputStream)
                            
                            if (compressedBytes != null) {
                                imageBytesList.add(compressedBytes)
                                Log.d("MainActivity", "图片[$index] 压缩完成: ${compressedBytes.size} bytes")
                            } else {
                                Log.e("MainActivity", "图片[$index] 压缩失败")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "处理图片[$index]失败", e)
                        }
                    }
                    
                    if (imageBytesList.isEmpty()) {
                        runOnUiThread {
                            isRequesting = false
                            webView.evaluateJavascript("alert('图片处理失败，请重试');", null)
                        }
                        return@Thread
                    }
                    
                    // 上传图片（并发上传，等待全部完成）
                    val imageUrls = ImageUploader.uploadImages(imageBytesList)
                    
                    if (imageUrls == null || imageUrls.isEmpty()) {
                        runOnUiThread {
                            isRequesting = false
                            webView.evaluateJavascript("alert('图片上传失败，请检查网络或配置');", null)
                        }
                        return@Thread
                    }
                    
                    // 验证URL都是https
                    val validUrls = imageUrls.filter { it.startsWith("https://") }
                    if (validUrls.size != imageUrls.size) {
                        Log.e("MainActivity", "部分图片URL不是https: $imageUrls")
                        runOnUiThread {
                            isRequesting = false
                            webView.evaluateJavascript("alert('图片URL无效，请重试');", null)
                        }
                        return@Thread
                    }
                    
                    Log.d("MainActivity", "图片处理完成，获取到 ${validUrls.size} 个URL")
                    
                    // 发送模型请求
                    sendToModel(text, validUrls)
                    
                } catch (e: Exception) {
                    Log.e("MainActivity", "处理图片失败", e)
                    runOnUiThread {
                        isRequesting = false
                        webView.evaluateJavascript("alert('图片处理失败: ${e.message}');", null)
                    }
                }
            }.start()
        }
        
        /**
         * 发送模型请求
         */
        private fun sendToModel(text: String, imageUrlList: List<String>) {
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
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

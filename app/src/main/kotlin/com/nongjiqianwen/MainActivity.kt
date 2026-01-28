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
                    Log.d("MainActivity", "有图片但无文字，阻止发送")
                    webView.evaluateJavascript("alert('请补充文字说明');", null)
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
                    
                    // 静默处理：最多4张（前端已限制，这里再次确保）
                    val validImageList = imageBase64List.take(4)
                    
                    if (validImageList.isEmpty()) {
                        // 没有有效图片，按纯文本处理
                        sendToModel(text, emptyList())
                        return@Thread
                    }
                    
                    Log.d("MainActivity", "开始处理 ${validImageList.size} 张图片")
                    
                    // base64 → 压缩 → 上传
                    val imageBytesList = mutableListOf<ByteArray>()
                    validImageList.forEachIndexed { index, base64 ->
                        try {
                            // base64解码
                            val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                            
                            // 压缩图片（EXIF矫正 + 缩放 + 压缩）
                            val compressResult = ImageUploader.compressImage(imageBytes)
                            
                            if (compressResult != null) {
                                imageBytesList.add(compressResult.bytes)
                                Log.d("MainActivity", "图片[$index] 处理完成: ${compressResult.originalWidth}x${compressResult.originalHeight} -> ${compressResult.compressedWidth}x${compressResult.compressedHeight}, ${compressResult.compressedSize} bytes")
                            } else {
                                Log.e("MainActivity", "图片[$index] 压缩失败，跳过")
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "处理图片[$index]失败", e)
                        }
                    }
                    
                    if (imageBytesList.isEmpty()) {
                        // 静默失败：按纯文本处理，不暴露错误给前端
                        Log.e("MainActivity", "所有图片处理失败，按纯文本发送")
                        sendToModel(text, emptyList())
                        return@Thread
                    }
                    
                    // 上传图片（并发上传，等待全部完成）
                    val imageUrls = ImageUploader.uploadImages(imageBytesList)
                    
                    if (imageUrls == null || imageUrls.isEmpty()) {
                        // 静默失败：按纯文本处理，不暴露错误给前端
                        Log.e("MainActivity", "图片上传失败，按纯文本发送")
                        sendToModel(text, emptyList())
                        return@Thread
                    }
                    
                    // 验证URL都是https
                    val validUrls = imageUrls.filter { it.startsWith("https://") }
                    if (validUrls.size != imageUrls.size) {
                        Log.e("MainActivity", "部分图片URL不是https，按纯文本发送")
                        sendToModel(text, emptyList())
                        return@Thread
                    }
                    
                    Log.d("MainActivity", "图片处理完成，获取到 ${validUrls.size} 个URL")
                    
                    // 发送模型请求
                    sendToModel(text, validUrls)
                    
                } catch (e: Exception) {
                    // 静默失败：按纯文本处理，不暴露错误给前端
                    Log.e("MainActivity", "处理图片失败", e)
                    sendToModel(text, emptyList())
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

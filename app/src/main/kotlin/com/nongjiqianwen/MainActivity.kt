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
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var isRequesting = false

    /** 压缩后 bytes 短期缓存，供重试使用：imageId -> (bytes, 写入时间戳)。TTL 60s，LRU 最多 4 条 */
    private val compressedBytesCache = ConcurrentHashMap<String, Pair<ByteArray, Long>>()

    companion object {
        private const val CACHE_TTL_MS = 60_000L
        private const val CACHE_MAX_SIZE = 4
    }

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
        /**
         * 上传图片（逐张上传，回传状态）
         * @param imageDataListJson JSON数组：[{imageId: "img_xxx", base64: "..."}, ...]
         */
        @JavascriptInterface
        fun uploadImages(imageDataListJson: String) {
            Thread {
                try {
                    // 解析图片数据列表（含 requestId，用于回调去重）
                    val imageDataList = try {
                        val jsonElement = com.google.gson.JsonParser().parse(imageDataListJson)
                        if (jsonElement.isJsonNull) {
                            emptyList()
                        } else {
                            val jsonArray = jsonElement.asJsonArray
                            jsonArray.mapNotNull { element ->
                                val obj = element.asJsonObject
                                val imageId = obj.get("imageId")?.asString
                                val base64 = obj.get("base64")?.asString
                                val requestId = obj.get("requestId")?.asString ?: ""
                                if (imageId != null && base64 != null && base64.isNotBlank()) {
                                    Triple(imageId, base64, requestId)
                                } else {
                                    null
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "解析图片数据列表失败", e)
                        emptyList()
                    }
                    
                    // 验证：最多4张
                    if (imageDataList.size > 4) {
                        runOnUiThread {
                            webView.evaluateJavascript("alert('最多选择4张图片');", null)
                        }
                        return@Thread
                    }
                    
                    // 逐张上传
                    imageDataList.forEach { (imageId, base64, requestId) ->
                        uploadSingleImage(imageId, base64, requestId)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "上传图片失败", e)
                }
            }.start()
        }
        
        /**
         * 重试单张图片上传（不传 base64，用缓存 bytes 重传；缓存缺失则回传 fail + 请重新选择该图片）
         * @param imageId 图片ID
         * @param requestId 本次尝试ID（回传前端用于去重）
         */
        @JavascriptInterface
        fun retryImage(imageId: String, requestId: String) {
            Thread {
                Log.d("MainActivity", "重试图片上传: $imageId, requestId=$requestId")
                val cached = compressedBytesCache[imageId]
                val now = System.currentTimeMillis()
                if (cached == null || (now - cached.second) > CACHE_TTL_MS) {
                    runOnUiThread {
                        val escapedImageId = escapeJs(imageId)
                        val escapedRequestId = escapeJs(requestId)
                        val escapedMsg = escapeJs("请重新选择该图片")
                        webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'fail', null, '$escapedRequestId', '$escapedMsg');", null)
                    }
                    return@Thread
                }
                uploadSingleImageWithBytes(imageId, cached.first, requestId)
            }.start()
        }
        
        /**
         * 发送消息（此时图片已上传成功，传入的是URL列表）
         * @param text 用户文字
         * @param imageUrlsJson JSON数组：["https://...", ...] 或 "null"
         */
        @JavascriptInterface
        fun sendMessage(text: String, imageUrlsJson: String) {
            runOnUiThread {
                if (isRequesting) return@runOnUiThread
                
                // 验证：有图必须有文字
                val hasText = text.isNotBlank()
                val hasImages = imageUrlsJson != "null" && imageUrlsJson.isNotBlank()
                
                if (hasImages && !hasText) {
                    Log.d("MainActivity", "有图片但无文字，阻止发送")
                    webView.evaluateJavascript("alert('请补充文字说明');", null)
                    return@runOnUiThread
                }
                
                // 检查是否有输入
                if (!hasText && !hasImages) return@runOnUiThread
                
                // 设置请求状态
                isRequesting = true
                
                // 解析图片URL列表
                val imageUrlList = if (hasImages) {
                    try {
                        val jsonElement = com.google.gson.JsonParser().parse(imageUrlsJson)
                        if (jsonElement.isJsonNull) {
                            emptyList()
                        } else {
                            val jsonArray = jsonElement.asJsonArray
                            jsonArray.mapNotNull { element ->
                                val url = element.asString
                                if (url.isNotBlank() && url.startsWith("https://")) {
                                    url
                                } else {
                                    null
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "解析图片URL列表失败", e)
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                
                // 发送模型请求
                sendToModel(text, imageUrlList)
            }
        }
        
        /**
         * 上传单张图片（逐张处理；回传 requestId 供前端去重）
         */
        private fun uploadSingleImage(imageId: String, base64: String, requestId: String) {
            Thread {
                try {
                    // 回传：开始上传（带 requestId）
                    runOnUiThread {
                        val escapedImageId = escapeJs(imageId)
                        val escapedRequestId = escapeJs(requestId)
                        webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'uploading', null, '$escapedRequestId', null);", null)
                    }
                    
                    // base64 解码后即用，不长期驻留
                    val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                    
                    // 压缩：EXIF 矫正 → 等比缩放长边 1600px → JPEG quality=75（不裁剪）
                    val compressResult = ImageUploader.compressImage(imageBytes)
                    
                    if (compressResult == null) {
                        Log.e("MainActivity", "图片[$imageId] 压缩失败")
                        runOnUiThread {
                            val escapedImageId = escapeJs(imageId)
                            val escapedRequestId = escapeJs(requestId)
                            webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'fail', null, '$escapedRequestId', null);", null)
                        }
                        return@Thread
                    }
                    
                    Log.d("MainActivity", "=== 图片[$imageId] 处理完成 ===")
                    Log.d("MainActivity", "原图尺寸: ${compressResult.originalWidth}x${compressResult.originalHeight}")
                    Log.d("MainActivity", "压缩后尺寸: ${compressResult.compressedWidth}x${compressResult.compressedHeight}")
                    Log.d("MainActivity", "压缩后字节数: ${compressResult.compressedSize} bytes")
                    
                    putCompressedCache(imageId, compressResult.bytes)
                    uploadSingleImageWithBytes(imageId, compressResult.bytes, requestId)
                    
                } catch (e: Exception) {
                    Log.e("MainActivity", "处理图片[$imageId]失败", e)
                    runOnUiThread {
                        val escapedImageId = escapeJs(imageId)
                        val escapedRequestId = escapeJs(requestId)
                        webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'fail', null, '$escapedRequestId', null);", null)
                    }
                }
            }.start()
        }
        
        /** 用已压缩 bytes 上传（首次或重试共用）；失败回调不带 errorMessage，由调用方决定 */
        private fun uploadSingleImageWithBytes(imageId: String, imageBytes: ByteArray, requestId: String) {
            runOnUiThread {
                val escapedImageId = escapeJs(imageId)
                val escapedRequestId = escapeJs(requestId)
                webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'uploading', null, '$escapedRequestId', null);", null)
            }
            ImageUploader.uploadImage(
                imageBytes = imageBytes,
                onSuccess = { url ->
                    Log.d("MainActivity", "=== 图片[$imageId] 上传成功 ===")
                    val maskedUrl = url.replace(Regex("/([^/]+)$"), "/***")
                    Log.d("MainActivity", "上传URL（脱敏）: $maskedUrl")
                    runOnUiThread {
                        val escapedImageId = escapeJs(imageId)
                        val escapedUrl = url.replace("\\", "\\\\").replace("'", "\\'")
                        val escapedRequestId = escapeJs(requestId)
                        webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'success', '$escapedUrl', '$escapedRequestId', null);", null)
                    }
                },
                onError = { error ->
                    Log.e("MainActivity", "=== 图片[$imageId] 上传失败 ===")
                    Log.e("MainActivity", "错误原因: $error")
                    runOnUiThread {
                        val escapedImageId = escapeJs(imageId)
                        val escapedRequestId = escapeJs(requestId)
                        val escapedError = escapeJs(error)
                        webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'fail', null, '$escapedRequestId', '$escapedError');", null)
                    }
                }
            )
        }
        
        private fun putCompressedCache(imageId: String, bytes: ByteArray) {
            val now = System.currentTimeMillis()
            synchronized(compressedBytesCache) {
                compressedBytesCache.entries.removeIf { (_, v) -> (now - v.second) > CACHE_TTL_MS }
                while (compressedBytesCache.size >= CACHE_MAX_SIZE) {
                    val oldest = compressedBytesCache.entries.minByOrNull { it.value.second } ?: break
                    compressedBytesCache.remove(oldest.key)
                }
                compressedBytesCache[imageId] = Pair(bytes, now)
            }
        }
        
        private fun escapeJs(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'")
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

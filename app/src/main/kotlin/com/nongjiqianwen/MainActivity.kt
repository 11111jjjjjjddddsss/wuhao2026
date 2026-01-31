package com.nongjiqianwen

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var isRequesting = false
    /** 单飞：同会话只允许一个流在飞；当前在飞的 streamId，仅其 complete 时清 isRequesting */
    private var currentStreamId: String? = null
    /** 切后台时 true，onResume 清空并补发缓存；不在此 cancel 请求 */
    private var isInBackground = false

    /** streamId -> [(type, data)]：切后台时缓存 chunk/complete/interrupted，onResume 按序补发 */
    private val bufferedEventsByStreamId = mutableMapOf<String, MutableList<Pair<String, String?>>>()
    /** 后台缓存上限：字数、流数、时间(ms)；超限丢弃并用 badge 提示 */
    private var backgroundBufferStartMs: Long = 0L
    /** 因流数超限被丢弃的 streamId，onResume 时补发 cache_overflow badge */
    private val overflowedStreamIds = mutableSetOf<String>()

    /** 压缩后 bytes 短期缓存，供重试使用：imageId -> (bytes, 写入时间戳)。TTL 60s，LRU 最多 4 条 */
    private val compressedBytesCache = ConcurrentHashMap<String, Pair<ByteArray, Long>>()

    /** A/B 层：streamId -> 本轮 user 消息（完整轮次时用于 addRound） */
    private val pendingUserByStreamId = mutableMapOf<String, String>()
    /** A/B 层：streamId -> 本轮 assistant 内容累积（完整轮次时用于 addRound） */
    private val pendingAssistantByStreamId = mutableMapOf<String, StringBuilder>()

    companion object {
        private const val CACHE_TTL_MS = 60_000L
        private const val CACHE_MAX_SIZE = 4
        private const val BACKGROUND_CACHE_MAX_CHARS = 20_000
        private const val BACKGROUND_CACHE_MAX_STREAMS = 3
        private const val BACKGROUND_CACHE_MAX_MS = 60_000L
    }

    /** JS 字符串安全：反斜杠/单引号 + U+2028/U+2029（否则断串） */
    private fun escapeJs(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IdManager.init(applicationContext)
        SystemAnchor.init(applicationContext)
        BExtractionPrompt.init(applicationContext)
        ABLayerManager.init(applicationContext)

        webView = WebView(this)
        setContentView(webView)

        // Debug：长按 WebView 5 秒弹出“重置 install_id”（仅用于测试）
        setupResetInstallIdOnLongPress()

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
        
        // 加载HTML文件（唯一模板）
        val loadUrl = "file:///android_asset/gpt-demo.html"
        Log.d("MainActivity", "WebView loadUrl=$loadUrl")
        webView.loadUrl(loadUrl)
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
         * 停止当前流式生成（用户点击 Stop 时调用）
         */
        @JavascriptInterface
        fun stopGeneration() {
            runOnUiThread {
                QwenClient.cancelCurrentRequest()
            }
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
         * @param streamId 前端生成的流ID，回调时写对应气泡（取消时旧气泡落终态）
         */
        @JavascriptInterface
        fun sendMessage(text: String, imageUrlsJson: String, streamId: String?) {
            runOnUiThread {
                // 单飞：不挡连点；连点 = 取消旧流再开新流，getReply 内 cancelCurrentRequest

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

                val sid = streamId?.takeIf { it.isNotBlank() } ?: "stream_${System.currentTimeMillis()}"
                currentStreamId = sid
                isRequesting = true
                sendToModel(text, imageUrlList, sid)
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

        /** 切后台时按 streamId 缓存事件，受字数/流数/时间三上限；超限丢弃并记 cache_overflow */
        private fun dispatchChunk(streamId: String, chunk: String) {
            runOnUiThread {
                if (isInBackground) {
                    synchronized(bufferedEventsByStreamId) {
                        val now = System.currentTimeMillis()
                        val overTime = (now - backgroundBufferStartMs) > BACKGROUND_CACHE_MAX_MS
                        val totalChars = bufferedEventsByStreamId.values.sumOf { list -> list.sumOf { (it.second?.length ?: 0) } }
                        val overChars = (totalChars + chunk.length) > BACKGROUND_CACHE_MAX_CHARS
                        val streamOrder = bufferedEventsByStreamId.keys.toList()
                        val overStreams = streamOrder.size >= BACKGROUND_CACHE_MAX_STREAMS && streamId !in bufferedEventsByStreamId
                        if (overTime || overChars) {
                            val list = bufferedEventsByStreamId.getOrPut(streamId) { mutableListOf() }
                            if (list.isEmpty() || list.last().first != "interrupted" || list.last().second != "cache_overflow") {
                                list.add("interrupted" to "cache_overflow")
                            } else Unit
                        } else if (overStreams) {
                            val oldest = streamOrder.first()
                            bufferedEventsByStreamId.remove(oldest)
                            overflowedStreamIds.add(oldest)
                            if (bufferedEventsByStreamId.size < BACKGROUND_CACHE_MAX_STREAMS) {
                                bufferedEventsByStreamId.getOrPut(streamId) { mutableListOf() }.add("chunk" to chunk)
                            } else Unit
                        } else {
                            bufferedEventsByStreamId.getOrPut(streamId) { mutableListOf() }.add("chunk" to chunk)
                        }
                    }
                } else {
                    pendingAssistantByStreamId.getOrPut(streamId) { StringBuilder() }.append(chunk)
                    val esc = escapeJs(streamId)
                    val escChunk = chunk.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
                    webView.evaluateJavascript("window.onChunkReceived && window.onChunkReceived('$esc', '$escChunk');", null)
                }
            }
        }

        private fun dispatchComplete(streamId: String) {
            runOnUiThread {
                if (isInBackground) {
                    synchronized(bufferedEventsByStreamId) { bufferedEventsByStreamId.getOrPut(streamId) { mutableListOf() }.add("complete" to null) }
                } else {
                    if (streamId == currentStreamId) { isRequesting = false; currentStreamId = null }
                    val userMsg = pendingUserByStreamId.remove(streamId) ?: ""
                    val assistantMsg = pendingAssistantByStreamId.remove(streamId)?.toString() ?: ""
                    if (userMsg.isNotBlank() || assistantMsg.isNotBlank()) {
                        ABLayerManager.onRoundComplete(userMsg, assistantMsg)
                    }
                    val esc = escapeJs(streamId)
                    webView.evaluateJavascript("window.onCompleteReceived && window.onCompleteReceived('$esc');", null)
                }
            }
        }

        private fun dispatchInterrupted(streamId: String, reason: String) {
            runOnUiThread {
                pendingUserByStreamId.remove(streamId)
                pendingAssistantByStreamId.remove(streamId)
                if (streamId == currentStreamId) {
                    isRequesting = false
                    currentStreamId = null
                }
                if (isInBackground) {
                    synchronized(bufferedEventsByStreamId) { bufferedEventsByStreamId.getOrPut(streamId) { mutableListOf() }.add("interrupted" to reason) }
                } else {
                    val esc = escapeJs(streamId)
                    val escReason = escapeJs(reason)
                    webView.evaluateJavascript("window.onStreamInterrupted && window.onStreamInterrupted('$esc', '$escReason');", null)
                }
            }
        }

        /**
         * 发送模型请求（streamId 贯穿；仅新请求时 cancel 旧请求，切后台不断网、缓存补发）
         */
        private fun sendToModel(text: String, imageUrlList: List<String>, streamId: String) {
            pendingUserByStreamId[streamId] = text
            ModelService.getReply(
                userMessage = text,
                imageUrlList = imageUrlList,
                streamId = streamId,
                onChunk = { chunk -> dispatchChunk(streamId, chunk) },
                onComplete = { dispatchComplete(streamId) },
                onInterrupted = { reason -> dispatchInterrupted(streamId, reason) }
            )
        }
    }

    /** onResume 时补发切后台期间缓存的 chunk/complete/interrupted；合并连续 chunk 并按 stream 分批 post 避免 UI 卡顿 */
    private fun flushBufferedEventsToWebView() {
        val copy: Map<String, List<Pair<String, String?>>>
        val overflowed: Set<String>
        synchronized(bufferedEventsByStreamId) {
            copy = bufferedEventsByStreamId.toMap().mapValues { it.value.toList() }
            bufferedEventsByStreamId.clear()
            overflowed = overflowedStreamIds.toSet()
            overflowedStreamIds.clear()
        }
        overflowed.forEach { streamId ->
            pendingUserByStreamId.remove(streamId)
            pendingAssistantByStreamId.remove(streamId)
            val esc = escapeJs(streamId)
            webView.evaluateJavascript("window.onStreamInterrupted && window.onStreamInterrupted('$esc', 'cache_overflow');", null)
        }
        val streamList = copy.entries.toList()
        fun replayStreamAt(index: Int) {
            if (index >= streamList.size) return
            val (streamId, events) = streamList[index]
            val esc = escapeJs(streamId)
            val merged = mergeChunksAndReplayOrder(events)
            var assistantAccum = StringBuilder()
            merged.forEach { (type, data) ->
                when (type) {
                    "chunk" -> {
                        assistantAccum.append(data ?: "")
                        val escChunk = (data ?: "").replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
                        webView.evaluateJavascript("window.onChunkReceived && window.onChunkReceived('$esc', '$escChunk');", null)
                    }
                    "complete" -> {
                        val userMsg = pendingUserByStreamId.remove(streamId) ?: ""
                        val assistantMsg = assistantAccum.toString()
                        if (userMsg.isNotBlank() || assistantMsg.isNotBlank()) {
                            ABLayerManager.onRoundComplete(userMsg, assistantMsg)
                        }
                        if (streamId == currentStreamId) { isRequesting = false; currentStreamId = null }
                        webView.evaluateJavascript("window.onCompleteReceived && window.onCompleteReceived('$esc');", null)
                    }
                    "interrupted" -> {
                        pendingUserByStreamId.remove(streamId)
                        if (streamId == currentStreamId) { isRequesting = false; currentStreamId = null }
                        val escReason = escapeJs(data ?: "")
                        webView.evaluateJavascript("window.onStreamInterrupted && window.onStreamInterrupted('$esc', '$escReason');", null)
                    }
                }
            }
            webView.post { replayStreamAt(index + 1) }
        }
        webView.post { replayStreamAt(0) }
    }

    /** 将同一流内连续 chunk 合并为一条，保持 complete/interrupted 顺序，减少 JS 调用次数 */
    private fun mergeChunksAndReplayOrder(events: List<Pair<String, String?>>): List<Pair<String, String?>> {
        val out = mutableListOf<Pair<String, String?>>()
        var chunkAcc = StringBuilder()
        for ((type, data) in events) {
            when (type) {
                "chunk" -> chunkAcc.append(data ?: "")
                "complete", "interrupted" -> {
                    if (chunkAcc.isNotEmpty()) {
                        out.add("chunk" to chunkAcc.toString())
                        chunkAcc = StringBuilder()
                    }
                    out.add(type to data)
                }
            }
        }
        if (chunkAcc.isNotEmpty()) out.add("chunk" to chunkAcc.toString())
        return out
    }

    override fun onPause() {
        super.onPause()
        isInBackground = true
        backgroundBufferStartMs = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        if (isInBackground) {
            isInBackground = false
            flushBufferedEventsToWebView()
        }
    }

    override fun onStop() {
        super.onStop()
        // 不在此 cancel：仅新请求或用户点停止时 cancel，切后台不断网
    }

    private var resetInstallIdRunnable: Runnable? = null

    private fun setupResetInstallIdOnLongPress() {
        resetInstallIdRunnable = Runnable {
            AlertDialog.Builder(this)
                .setTitle("Debug")
                .setMessage("重置 install_id？仅用于测试")
                .setPositiveButton("重置") { _, _ ->
                    val newId = IdManager.resetInstallId()
                    Toast.makeText(this, "install_id 已重置", Toast.LENGTH_SHORT).show()
                    Log.d("MainActivity", "resetInstallId: $newId")
                }
                .setNegativeButton("取消", null)
                .show()
        }
        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> resetInstallIdRunnable?.let { webView.postDelayed(it, 5000) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetInstallIdRunnable?.let { webView.removeCallbacks(it) }
            }
            false
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

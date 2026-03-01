package com.nongjiqianwen

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
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
    /** 单飞：同会话只允许一个流在飞；当前在飞的 streamId，仅?complete 时清 isRequesting */
    private var currentStreamId: String? = null
    /** 切后台时 true，onResume 清空并补发缓存；不在?cancel 请求 */
    private var isInBackground = false

    /** streamId -> [(type, data)]：切后台时缓?chunk/complete/interrupted，onResume 按序补发 */
    private val bufferedEventsByStreamId = mutableMapOf<String, MutableList<Pair<String, String?>>>()
    /** 后台缓存上限：字数流数时?ms)；超限丢弃并?badge 提示 */
    private var backgroundBufferStartMs: Long = 0L
    /** 因流数超限被丢弃?streamId，onResume 时补?cache_overflow badge */
    private val overflowedStreamIds = mutableSetOf<String>()

    /** 压缩?bytes 短期缓存，供重试使用：imageId -> (bytes, 写入时间?。TTL 60s，LRU 朢?4 ?*/
    private val compressedBytesCache = ConcurrentHashMap<String, Pair<ByteArray, Long>>()

    /** A/B 层：streamId -> 本轮 user 消息（完整轮次时用于 addRound?*/
    private val pendingUserByStreamId = mutableMapOf<String, String>()
    /** A/B 层：streamId -> 本轮 assistant 内容累积（完整轮次时用于 addRound?*/
    private val pendingAssistantByStreamId = mutableMapOf<String, StringBuilder>()
    private val pendingChatModelByStreamId = mutableMapOf<String, String?>()
    private val clientMsgIdByStreamId = mutableMapOf<String, String>()
    private val clientMsgIdUpdatedAtByStreamId = mutableMapOf<String, Long>()

    /** 后台断流无感补全：待补全时非 null? 秒节流用 lastFailAt */
    private data class ResumeState(
        val streamId: String,
        val lastUserInputText: String,
        val assistantPrefix: String,
        var isWaitingResume: Boolean,
        var lastFailAt: Long,
        val lastChatModel: String?
    )
    private var resumeState: ResumeState? = null
    private var currentChatModelForResume: String? = null

    companion object {
        private const val RESUME_THROTTLE_MS = 3000L
        private const val CONTINUATION_PREFIX_MAX = 800
        private const val CACHE_TTL_MS = 60_000L
        private const val CACHE_MAX_SIZE = 4
        private const val MAX_IMAGE_UPLOAD_BYTES = 1024 * 1024
        private const val BACKGROUND_CACHE_MAX_CHARS = 20_000
        private const val BACKGROUND_CACHE_MAX_STREAMS = 3
        private const val BACKGROUND_CACHE_MAX_MS = 60_000L
        private const val CLIENT_MSG_ID_CACHE_MAX = 2000
        private const val CLIENT_MSG_ID_CACHE_TRIM_TO = 1500
    }

    /** JS 字符串安全：反斜?单引?+ U+2028/U+2029（否则断串） */
    private fun escapeJs(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")

    private fun putClientMsgId(streamId: String, clientMsgId: String) {
        if (streamId.isBlank() || clientMsgId.isBlank()) return
        clientMsgIdByStreamId[streamId] = clientMsgId
        clientMsgIdUpdatedAtByStreamId[streamId] = System.currentTimeMillis()
        QwenClient.setClientMsgIdForStream(streamId, clientMsgId)
    }

    private fun removeClientMsgId(streamId: String) {
        if (streamId.isBlank()) return
        clientMsgIdByStreamId.remove(streamId)
        clientMsgIdUpdatedAtByStreamId.remove(streamId)
        QwenClient.clearClientMsgIdForStream(streamId)
    }

    private fun markClientMsgIdCompleted(streamId: String) {
        if (streamId.isBlank()) return
        if (clientMsgIdByStreamId.containsKey(streamId)) {
            clientMsgIdUpdatedAtByStreamId[streamId] = System.currentTimeMillis()
        }
        QwenClient.clearClientMsgIdForStream(streamId)
        pruneClientMsgIdCacheIfNeeded()
    }

    private fun pruneClientMsgIdCacheIfNeeded() {
        if (clientMsgIdByStreamId.size <= CLIENT_MSG_ID_CACHE_MAX) return
        val removeCount = (clientMsgIdByStreamId.size - CLIENT_MSG_ID_CACHE_TRIM_TO).coerceAtLeast(0)
        if (removeCount <= 0) return
        val protectedKeys = mutableSetOf<String>()
        currentStreamId?.takeIf { it.isNotBlank() }?.let { protectedKeys.add(it) }
        protectedKeys.addAll(pendingUserByStreamId.keys)
        protectedKeys.addAll(pendingAssistantByStreamId.keys)
        synchronized(bufferedEventsByStreamId) {
            protectedKeys.addAll(bufferedEventsByStreamId.keys)
        }
        val staleKeys = clientMsgIdUpdatedAtByStreamId.entries
            .asSequence()
            .filter { (key, _) -> key !in protectedKeys }
            .sortedBy { it.value }
            .take(removeCount)
            .map { it.key }
            .toList()
        staleKeys.forEach { key ->
            clientMsgIdByStreamId.remove(key)
            clientMsgIdUpdatedAtByStreamId.remove(key)
            QwenClient.clearClientMsgIdForStream(key)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IdManager.init(applicationContext)
        SystemAnchor.init(applicationContext)
        BExtractionPrompt.init(applicationContext)
        ABLayerManager.init(applicationContext)

        webView = WebView(this)
        setContentView(webView)

        // Debug锛氶暱鎸?WebView 5 绉掑脊鍑衡€滈噸缃?install_id鈥濓紙浠呯敤浜庢祴璇曪級
        if (BuildConfig.DEBUG) {
            setupResetInstallIdOnLongPress()
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
        }
        
        // 娉ㄥ叆JavaScript鎺ュ彛
        androidJSInterface = AndroidJSInterface()
        webView.addJavascriptInterface(androidJSInterface!!, "AndroidInterface")
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "MainActivity",
                        "backend switches: USE_BACKEND_AB=${BuildConfig.USE_BACKEND_AB}, USE_BACKEND_ENTITLEMENT=${BuildConfig.USE_BACKEND_ENTITLEMENT}",
                    )
                }
                webView.evaluateJavascript(
                    "if(typeof USE_BACKEND_AB!=='undefined'){USE_BACKEND_AB=${BuildConfig.USE_BACKEND_AB};}window.USE_BACKEND_AB=${BuildConfig.USE_BACKEND_AB};if(typeof USE_BACKEND_ENTITLEMENT!=='undefined'){USE_BACKEND_ENTITLEMENT=${BuildConfig.USE_BACKEND_ENTITLEMENT};}window.USE_BACKEND_ENTITLEMENT=${BuildConfig.USE_BACKEND_ENTITLEMENT};",
                    null,
                )
                if (BuildConfig.USE_BACKEND_AB && (BuildConfig.UPLOAD_BASE_URL?.trim() ?: "").isNotEmpty()) {
                    SessionApi.getSnapshot(IdManager.getSessionId()) { snapshot ->
                        runOnUiThread {
                            if (snapshot != null) {
                                ABLayerManager.loadSnapshot(snapshot)
                                val list = snapshot.a_rounds_for_ui.map { mapOf("user" to it.user, "assistant" to it.assistant) }
                                val jsonStr = com.google.gson.Gson().toJson(list)
                                val escaped = jsonStr.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                                webView.evaluateJavascript("if(window.setInitialHistory) window.setInitialHistory(JSON.parse(\"$escaped\"));", null)
                                if (BuildConfig.DEBUG) Log.d("MainActivity", "snapshot loaded a_rounds_full=${snapshot.a_rounds_full.size} injected for_ui=${snapshot.a_rounds_for_ui.size}")
                            } else {
                                if (BuildConfig.DEBUG) Log.w("MainActivity", "GET snapshot 失败(503/timeout/网络)，不回本地、不注入；降级策略：仅本次不展示历史")
                                webView.evaluateJavascript("if(typeof showToast==='function')showToast('会话同步失败，请稍后重试');", null)
                            }
                        }
                    }
                }
            }
        }

        // 加载HTML文件（唯丢模板?
        val loadUrl = "file:///android_asset/gpt-demo.html"
        if (BuildConfig.DEBUG) {
            Log.d("MainActivity", "WebView loadUrl=$loadUrl")
        }
        webView.loadUrl(loadUrl)
    }
    
    /**
     * JavaScript鎺ュ彛锛岀敤浜嶹ebView涓嶢ndroid閫氫俊
     */
    inner class AndroidJSInterface {
        /**
         * 上传图片（张上传，回传状态）
         * @param imageDataListJson JSON鏁扮粍锛歔{imageId: "img_xxx", base64: "..."}, ...]
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
                        Log.e("MainActivity", "瑙ｆ瀽鍥剧墖鏁版嵁鍒楄〃澶辫触", e)
                        emptyList()
                    }
                    
                    if (imageDataList.size > 4) {
                        runOnUiThread {
                            webView.evaluateJavascript("if(typeof showToast==='function')showToast('鏈€澶?寮犲浘鐗?);else alert('鏈€澶?寮犲浘鐗?);", null)
                        }
                        return@Thread
                    }
                    
                    // 閫愬紶涓婁紶
                    imageDataList.forEach { (imageId, base64, requestId) ->
                        uploadSingleImage(imageId, base64, requestId)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "涓婁紶鍥剧墖澶辫触", e)
                }
            }.start()
        }
        
        /**
         * 鍋滄褰撳墠娴佸紡鐢熸垚锛堢敤鎴风偣鍑?Stop 鏃惰皟鐢級
         */
        @JavascriptInterface
        fun stopGeneration() {
            runOnUiThread {
                QwenClient.cancelCurrentRequest()
                SessionApi.cancelCurrentStream()
            }
        }


        /**
         * 返回设备本地时间字符串：YYYY-MM-DD HH:mm（周X?时区?
         */
        @JavascriptInterface
        fun getEntitlement(callbackId: String) {
            SessionApi.getEntitlement() { json ->
                runOnUiThread {
                    val escId = escapeJs(callbackId)
                    val js = if (json != null) {
                        val escaped = json.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                        "if(window._entitlementCallback && window._entitlementCallback['$escId']) window._entitlementCallback['$escId'](JSON.parse(\"$escaped\"));"
                    } else {
                        "if(window._entitlementCallback && window._entitlementCallback['$escId']) window._entitlementCallback['$escId'](null);"
                    }
                    webView.evaluateJavascript("$js delete window._entitlementCallback['$escId'];", null)
                }
            }
        }

        @JavascriptInterface
        fun getLocalDateTime(): String {
            return try {
                val zone = java.time.ZoneId.of("Asia/Shanghai")
                val now = java.time.ZonedDateTime.now(zone)
                val week = arrayOf("日", "一", "二", "三", "四", "五", "六")[now.dayOfWeek.value % 7]
                String.format("%04d-%02d-%02d %02d:%02d（周%s?%s",
                    now.year, now.monthValue, now.dayOfMonth,
                    now.hour, now.minute, week, zone.id)
            } catch (_: Exception) {
                try {
                    val now = java.util.Calendar.getInstance()
                    val week = arrayOf("日", "一", "二", "三", "四", "五", "六")[now.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                    val zoneName = now.timeZone.id
                    String.format("%04d-%02d-%02d %02d:%02d（周%s?%s",
                        now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH) + 1, now.get(java.util.Calendar.DAY_OF_MONTH),
                        now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), week, zoneName)
                } catch (_: Exception) {
                    ""
                }
            }
        }

        /**
         * 重试单张图片上传（不?base64，用缓存 bytes 重传；缓存缺失则回传 fail + 请重新择该图片）
         * @param imageId 鍥剧墖ID
         * @param requestId 鏈灏濊瘯ID锛堝洖浼犲墠绔敤浜庡幓閲嶏級
         */
        @JavascriptInterface
        fun retryImage(imageId: String, requestId: String) {
            Thread {
                if (BuildConfig.DEBUG) {
                    Log.d("MainActivity", "閲嶈瘯鍥剧墖涓婁紶: $imageId, requestId=$requestId")
                }
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
         * 发消息（此时图片已上传成功，传入的是URL列表?
         * @param text 鐢ㄦ埛鏂囧瓧
         * @param imageUrlsJson JSON鏁扮粍锛歔"https://...", ...] 鎴?"null"
         * @param streamId 前端生成的流ID，回调时写对应气泡（取消时旧气泡落终态）
         * @param model 可，前端传入档位标识（free/plus/pro）；主对话模型固?MODEL_MAIN(qwen3.5-plus)
         */
        @JavascriptInterface
        fun sendMessage(text: String, imageUrlsJson: String, streamId: String?, model: String?) {
            sendMessage(text, imageUrlsJson, streamId, model, null)
        }

        @JavascriptInterface
        fun sendMessage(text: String, imageUrlsJson: String, streamId: String?, model: String?, clientMsgId: String?) {
            runOnUiThread {
                // 单飞：不挡连点；连点 = 取消旧流再开新流，getReply ?cancelCurrentRequest

                val hasText = text.isNotBlank()
                val hasImages = imageUrlsJson != "null" && imageUrlsJson.isNotBlank()
                if (hasImages && !hasText) {
                    if (BuildConfig.DEBUG) Log.d("MainActivity", "有图片但无文字，阻止发送")
                    webView.evaluateJavascript("if(typeof showToast==='function')showToast('请补充文字描述后再发送');", null)
                    return@runOnUiThread
                }
                if (!hasText && !hasImages) return@runOnUiThread

                val imageUrlList = if (hasImages) {
                    try {
                        val jsonElement = com.google.gson.JsonParser().parse(imageUrlsJson)
                        if (jsonElement.isJsonNull) emptyList()
                        else {
                            val jsonArray = jsonElement.asJsonArray
                            jsonArray.mapNotNull { e ->
                                val url = e.asString
                                if (url.isNotBlank() && url.startsWith("https://")) url else null
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "瑙ｆ瀽鍥剧墖URL鍒楄〃澶辫触", e)
                        emptyList()
                    }
                } else emptyList()

                if (imageUrlList.size > 4) {
                    if (BuildConfig.DEBUG) Log.d("MainActivity", "图片超过4张，阻止发送")
                    webView.evaluateJavascript("if(typeof showToast==='function')showToast('单次最多4张');", null)
                    return@runOnUiThread
                }

                val sid = streamId?.takeIf { it.isNotBlank() } ?: "stream_${System.currentTimeMillis()}"
                val cid = clientMsgId?.takeIf { it.isNotBlank() } ?: sid
                // 发前断网拦截：无网不转圈，直接在该条 assistant 显示「当前无网络，未发? 重试
                if (!isNetworkAvailable()) {
                    val esc = escapeJs(sid)
                    val escReason = escapeJs("no_network")
                    webView.evaluateJavascript("window.onStreamInterrupted && window.onStreamInterrupted('$esc', '$escReason');", null)
                    return@runOnUiThread
                }
                putClientMsgId(sid, cid)
                currentStreamId = sid
                isRequesting = true
                sendToModel(text, imageUrlList, sid, model?.takeIf { it.isNotBlank() })
            }
        }

        /** 灰字「已中断 · 点击继续」点击时由前端调用，触发补全请求（无 tools?*/
        @JavascriptInterface
        fun requestResume(streamId: String?) {
            runOnUiThread {
                triggerResume(streamId?.takeIf { it.isNotBlank() })
            }
        }

        @SuppressLint("MissingPermission")
        private fun isNetworkAvailable(): Boolean {
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            @Suppress("DEPRECATION")
            return cm.activeNetworkInfo?.isConnected == true
        }
        
        /**
         * 上传单张图片（张处理；回?requestId 供前端去重）
         */
        private fun uploadSingleImage(imageId: String, base64: String, requestId: String) {
            Thread {
                try {
                    // 回传：开始上传（?requestId?
                    runOnUiThread {
                        val escapedImageId = escapeJs(imageId)
                        val escapedRequestId = escapeJs(requestId)
                        webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'uploading', null, '$escapedRequestId', null);", null)
                    }
                    
                    // base64 瑙ｇ爜鍚庡嵆鐢紝涓嶉暱鏈熼┗鐣?
                    val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                    
                    // 压缩：EXIF 矫正 ?固定序列 1024@82?024@80?024@75?024@70?96@70?68@70?40@70?40@60?12@60（目标≤1MB?
                    val compressResult = ImageUploader.compressImage(imageBytes)
                    
                    if (compressResult == null) {
                        Log.e("MainActivity", "鍥剧墖[$imageId] 瑙ｇ爜澶辫触锛屼笉璋冪敤妯″瀷/涓嶆墸璐?涓嶈杞")
                        runOnUiThread {
                            val escapedImageId = escapeJs(imageId)
                            val escapedRequestId = escapeJs(requestId)
                            val escapedErr = escapeJs(ImageUploader.DECODE_FAIL_MESSAGE)
                            webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'fail', null, '$escapedRequestId', '$escapedErr');", null)
                        }
                        return@Thread
                    }
                    if (compressResult.compressedSize > MAX_IMAGE_UPLOAD_BYTES) {
                        Log.e("MainActivity", "图片[$imageId] 压缩后仍?MB，不调用模型/不扣?不计轮次")
                        runOnUiThread {
                            val escapedImageId = escapeJs(imageId)
                            val escapedRequestId = escapeJs(requestId)
                            val escapedErr = escapeJs(ImageUploader.SIZE_LIMIT_FAIL_MESSAGE)
                            webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'fail', null, '$escapedRequestId', '$escapedErr');", null)
                        }
                        return@Thread
                    }
                    
                    if (BuildConfig.DEBUG) {
                        Log.d("MainActivity", "=== 鍥剧墖[$imageId] 澶勭悊瀹屾垚 ===")
                        Log.d("MainActivity", "鍘熷浘灏哄: ${compressResult.originalWidth}x${compressResult.originalHeight}")
                        Log.d("MainActivity", "鍘嬬缉鍚庡昂瀵? ${compressResult.compressedWidth}x${compressResult.compressedHeight}")
                        Log.d("MainActivity", "鍘嬬缉鍚庡瓧鑺傛暟: ${compressResult.compressedSize} bytes")
                    }
                    
                    putCompressedCache(imageId, compressResult.bytes)
                    uploadSingleImageWithBytes(imageId, compressResult.bytes, requestId)
                    
                } catch (e: Exception) {
                    Log.e("MainActivity", "澶勭悊鍥剧墖[$imageId]澶辫触", e)
                    runOnUiThread {
                        val escapedImageId = escapeJs(imageId)
                        val escapedRequestId = escapeJs(requestId)
                        val escapedErr = escapeJs(ImageUploader.DECODE_FAIL_MESSAGE)
                        webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'fail', null, '$escapedRequestId', '$escapedErr');", null)
                    }
                }
            }.start()
        }
        
        /** 用已压缩 bytes 上传（首次或重试共用）；失败回调不带 errorMessage，由调用方决?*/
        private fun uploadSingleImageWithBytes(imageId: String, imageBytes: ByteArray, requestId: String) {
            runOnUiThread {
                val escapedImageId = escapeJs(imageId)
                val escapedRequestId = escapeJs(requestId)
                webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'uploading', null, '$escapedRequestId', null);", null)
            }
            ImageUploader.uploadImage(
                imageBytes = imageBytes,
            onSuccess = { url ->
                if (BuildConfig.DEBUG) {
                    Log.d("MainActivity", "=== 鍥剧墖[$imageId] 涓婁紶鎴愬姛 ===")
                    val maskedUrl = url.replace(Regex("/([^/]+)$"), "/***")
                    Log.d("MainActivity", "涓婁紶URL锛堣劚鏁忥級: $maskedUrl")
                }
                runOnUiThread {
                        val escapedImageId = escapeJs(imageId)
                        val escapedUrl = url.replace("\\", "\\\\").replace("'", "\\'")
                        val escapedRequestId = escapeJs(requestId)
                        webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'success', '$escapedUrl', '$escapedRequestId', null);", null)
                    }
                },
                onError = { error ->
                    Log.e("MainActivity", "=== 鍥剧墖[$imageId] 涓婁紶澶辫触 ===")
                    Log.e("MainActivity", "閿欒鍘熷洜: $error")
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

        /** 切后台时?streamId 缓存事件，受字数/流数/时间三上限；超限丢弃并记 cache_overflow */
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
                    val chatModel = pendingChatModelByStreamId.remove(streamId)
                    if (userMsg.isNotBlank() || assistantMsg.isNotBlank()) {
                        ABLayerManager.onRoundComplete(userMsg, assistantMsg, chatModel)
                    }
                    val esc = escapeJs(streamId)
                    val escClientMsgId = escapeJs(clientMsgIdByStreamId[streamId] ?: "")
                    webView.evaluateJavascript("window.onCompleteReceived && window.onCompleteReceived('$esc', '$escClientMsgId');", null)
                    markClientMsgIdCompleted(streamId)
                }
            }
        }

        private fun dispatchInterrupted(streamId: String, reason: String) {
            runOnUiThread {
                pendingUserByStreamId.remove(streamId)
                pendingAssistantByStreamId.remove(streamId)
                pendingChatModelByStreamId.remove(streamId)
                removeClientMsgId(streamId)
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
         * 发模型请求（streamId 贯穿；仅新请求时 cancel 旧请求，切后台不断网、缓存补发）
         * @param chatModel 主对话档位标识（free/plus/pro）；主对话模型固?MODEL_MAIN(qwen3.5-plus)，B 摘要固定 MODEL_B_SUMMARY(qwen-flash)
         */
        private fun sendToModel(text: String, imageUrlList: List<String>, streamId: String, chatModel: String? = null) {
            pendingUserByStreamId[streamId] = text
            pendingChatModelByStreamId[streamId] = chatModel
            currentChatModelForResume = chatModel
            if (BuildConfig.USE_BACKEND_AB) {
                if (!SessionApi.hasBackendConfigured()) {
                    dispatchInterrupted(streamId, "backend_unavailable")
                    return
                }
                val clientMsgId = clientMsgIdByStreamId[streamId] ?: streamId
                SessionApi.streamChat(
                    options = SessionApi.StreamOptions(
                        sessionId = IdManager.getSessionId(),
                        clientMsgId = clientMsgId,
                        text = text,
                        images = imageUrlList
                    ),
                    onChunk = { chunk -> dispatchChunk(streamId, chunk) },
                    onComplete = {
                        resumeState?.takeIf { it.streamId == streamId }?.let { it.isWaitingResume = false }
                        dispatchComplete(streamId)
                    },
                    onInterrupted = { reason -> dispatchInterrupted(streamId, reason) }
                )
                return
            }

            ModelService.getReply(
                userMessage = text,
                imageUrlList = imageUrlList,
                streamId = streamId,
                chatModel = chatModel,
                onChunk = { chunk -> dispatchChunk(streamId, chunk) },
                onComplete = {
                    resumeState?.takeIf { it.streamId == streamId }?.let { it.isWaitingResume = false }
                    dispatchComplete(streamId)
                },
                onInterrupted = { reason -> dispatchInterrupted(streamId, reason) },
                onInterruptedResumable = { sid, _ ->
                    runOnUiThread {
                        val userMsg = pendingUserByStreamId[sid] ?: ""
                        val prefix = pendingAssistantByStreamId[sid]?.toString() ?: ""
                        if (userMsg.isNotBlank() || prefix.isNotBlank()) {
                            resumeState = ResumeState(sid, userMsg, prefix, true, System.currentTimeMillis(), currentChatModelForResume)
                        }
                        dispatchInterrupted(sid, "resumable")
                    }
                }
            )
        }

        /** 补全请求：无 tools，同丢 streamId 续写；成功则 dispatchComplete ?A，失败保持灰?*/
        fun triggerResume(streamId: String?) {
            val state = resumeState ?: return
            val sid = streamId ?: state.streamId
            if (state.streamId != sid || !state.isWaitingResume || state.lastUserInputText.isBlank()) return
            val now = System.currentTimeMillis()
            if (now - state.lastFailAt < RESUME_THROTTLE_MS) return
            state.lastFailAt = now
            val continuationUserMessage = state.lastUserInputText + "\n请从我已输出的内容继续，避免重复。已输出前缀如下：\n" + state.assistantPrefix.take(CONTINUATION_PREFIX_MAX)
            pendingUserByStreamId[sid] = state.lastUserInputText
            pendingAssistantByStreamId[sid] = StringBuilder(state.assistantPrefix)
            currentStreamId = sid
            isRequesting = true
            if (BuildConfig.USE_BACKEND_AB) {
                if (!SessionApi.hasBackendConfigured()) {
                    dispatchInterrupted(sid, "backend_unavailable")
                    return
                }
                val resumeClientMsgId = "resume_${System.currentTimeMillis()}_${sid}"
                putClientMsgId(sid, resumeClientMsgId)
                SessionApi.streamChat(
                    options = SessionApi.StreamOptions(
                        sessionId = IdManager.getSessionId(),
                        clientMsgId = resumeClientMsgId,
                        text = continuationUserMessage,
                        images = emptyList()
                    ),
                    onChunk = { chunk -> dispatchChunk(sid, chunk) },
                    onComplete = {
                        state.isWaitingResume = false
                        resumeState = null
                        dispatchComplete(sid)
                    },
                    onInterrupted = { _ ->
                        runOnUiThread {
                            val prefix = pendingAssistantByStreamId[sid]?.toString() ?: state.assistantPrefix
                            resumeState = ResumeState(sid, state.lastUserInputText, prefix, true, System.currentTimeMillis(), state.lastChatModel)
                            dispatchInterrupted(sid, "resumable")
                        }
                    }
                )
                return
            }
            ModelService.getReplyContinuation(
                streamId = sid,
                continuationUserMessage = continuationUserMessage,
                chatModel = state.lastChatModel,
                onChunk = { chunk -> dispatchChunk(sid, chunk) },
                onComplete = {
                    state.isWaitingResume = false
                    resumeState = null
                    dispatchComplete(sid)
                },
                onInterrupted = { _ -> dispatchInterrupted(sid, "error") },
                onInterruptedResumable = { _, _ ->
                    runOnUiThread {
                        val prefix = pendingAssistantByStreamId[sid]?.toString() ?: state.assistantPrefix
                        resumeState = ResumeState(sid, state.lastUserInputText, prefix, true, System.currentTimeMillis(), state.lastChatModel)
                        dispatchInterrupted(sid, "resumable")
                    }
                }
            )
        }
    }

    private var androidJSInterface: AndroidJSInterface? = null

    /** onResume 时补发切后台期间缓存?chunk/complete/interrupted；合并连?chunk 并按 stream 分批 post 避免 UI 卡顿 */
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
            removeClientMsgId(streamId)
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
                        val chatModel = pendingChatModelByStreamId.remove(streamId)
                        if (userMsg.isNotBlank() || assistantMsg.isNotBlank()) {
                            ABLayerManager.onRoundComplete(userMsg, assistantMsg, chatModel)
                        }
                        if (streamId == currentStreamId) { isRequesting = false; currentStreamId = null }
                        val escClientMsgId = escapeJs(clientMsgIdByStreamId[streamId] ?: "")
                        webView.evaluateJavascript("window.onCompleteReceived && window.onCompleteReceived('$esc', '$escClientMsgId');", null)
                        markClientMsgIdCompleted(streamId)
                    }
                    "interrupted" -> {
                        pendingUserByStreamId.remove(streamId)
                        pendingChatModelByStreamId.remove(streamId)
                        removeClientMsgId(streamId)
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

    /** 将同丢流内连续 chunk 合并为一条，保持 complete/interrupted 顺序，减?JS 调用次数 */
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
            resumeState?.let { state ->
                if (state.isWaitingResume && state.lastUserInputText.isNotBlank() && (System.currentTimeMillis() - state.lastFailAt >= RESUME_THROTTLE_MS)) {
                    androidJSInterface?.triggerResume(state.streamId)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 不在?cancel：仅新请求或用户点停止时 cancel，切后台不断?
    }

    private var resetInstallIdRunnable: Runnable? = null

    private fun setupResetInstallIdOnLongPress() {
        if (!BuildConfig.DEBUG) return
        resetInstallIdRunnable = Runnable {
            AlertDialog.Builder(this)
                .setTitle("Debug")
                .setMessage("閲嶇疆 install_id锛熶粎鐢ㄤ簬娴嬭瘯")
                .setPositiveButton("閲嶇疆") { _, _ ->
                    val newId = IdManager.resetInstallId()
                    Toast.makeText(this, "install_id 已重置", Toast.LENGTH_SHORT).show()
                    if (BuildConfig.DEBUG) {
                        Log.d("MainActivity", "resetInstallId: $newId")
                    }
                }
                .setNegativeButton("鍙栨秷", null)
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

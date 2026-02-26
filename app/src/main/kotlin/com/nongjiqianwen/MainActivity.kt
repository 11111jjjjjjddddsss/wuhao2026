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
    /** 鍗曢锛氬悓浼氳瘽鍙厑璁镐竴涓祦鍦ㄩ锛涘綋鍓嶅湪椋炵殑 streamId锛屼粎鍏?complete 鏃舵竻 isRequesting */
    private var currentStreamId: String? = null
    /** 鍒囧悗鍙版椂 true锛宱nResume 娓呯┖骞惰ˉ鍙戠紦瀛橈紱涓嶅湪姝?cancel 璇锋眰 */
    private var isInBackground = false

    /** streamId -> [(type, data)]锛氬垏鍚庡彴鏃剁紦瀛?chunk/complete/interrupted锛宱nResume 鎸夊簭琛ュ彂 */
    private val bufferedEventsByStreamId = mutableMapOf<String, MutableList<Pair<String, String?>>>()
    /** 鍚庡彴缂撳瓨涓婇檺锛氬瓧鏁般€佹祦鏁般€佹椂闂?ms)锛涜秴闄愪涪寮冨苟鐢?badge 鎻愮ず */
    private var backgroundBufferStartMs: Long = 0L
    /** 鍥犳祦鏁拌秴闄愯涓㈠純鐨?streamId锛宱nResume 鏃惰ˉ鍙?cache_overflow badge */
    private val overflowedStreamIds = mutableSetOf<String>()

    /** 鍘嬬缉鍚?bytes 鐭湡缂撳瓨锛屼緵閲嶈瘯浣跨敤锛歩mageId -> (bytes, 鍐欏叆鏃堕棿鎴?銆俆TL 60s锛孡RU 鏈€澶?4 鏉?*/
    private val compressedBytesCache = ConcurrentHashMap<String, Pair<ByteArray, Long>>()

    /** A/B 灞傦細streamId -> 鏈疆 user 娑堟伅锛堝畬鏁磋疆娆℃椂鐢ㄤ簬 addRound锛?*/
    private val pendingUserByStreamId = mutableMapOf<String, String>()
    /** A/B 灞傦細streamId -> 鏈疆 assistant 鍐呭绱Н锛堝畬鏁磋疆娆℃椂鐢ㄤ簬 addRound锛?*/
    private val pendingAssistantByStreamId = mutableMapOf<String, StringBuilder>()
    private val pendingChatModelByStreamId = mutableMapOf<String, String?>()
    private val clientMsgIdByStreamId = mutableMapOf<String, String>()
    private val clientMsgIdUpdatedAtByStreamId = mutableMapOf<String, Long>()

    /** 鍚庡彴鏂祦鏃犳劅琛ュ叏锛氬緟琛ュ叏鏃堕潪 null锛? 绉掕妭娴佺敤 lastFailAt */
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

    /** JS 瀛楃涓插畨鍏細鍙嶆枩鏉?鍗曞紩鍙?+ U+2028/U+2029锛堝惁鍒欐柇涓诧級 */
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
                if (BuildConfig.USE_BACKEND_AB && (BuildConfig.UPLOAD_BASE_URL?.trim() ?: "").isNotEmpty()) {
                    SessionApi.getSnapshot(IdManager.getClientId(), IdManager.getSessionId()) { snapshot ->
                        runOnUiThread {
                            if (snapshot != null) {
                                ABLayerManager.loadSnapshot(snapshot)
                                val list = snapshot.a_rounds_for_ui.map { mapOf("user" to it.user, "assistant" to it.assistant) }
                                val jsonStr = com.google.gson.Gson().toJson(list)
                                val escaped = jsonStr.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
                                webView.evaluateJavascript("if(window.setInitialHistory) window.setInitialHistory(JSON.parse(\"$escaped\"));", null)
                                if (BuildConfig.DEBUG) Log.d("MainActivity", "snapshot loaded a_rounds_full=${snapshot.a_rounds_full.size} injected for_ui=${snapshot.a_rounds_for_ui.size}")
                            } else {
                                if (BuildConfig.DEBUG) Log.w("MainActivity", "GET snapshot 澶辫触(503/timeout/缃戠粶)锛屼笉鍥為€€鏈湴銆佷笉娉ㄥ叆锛涢檷绾х瓥鐣ワ細浠呮湰娆′笉灞曠ず鍘嗗彶")
                                webView.evaluateJavascript("if(typeof showToast==='function')showToast('浼氳瘽鍚屾澶辫触锛岃绋嶅悗閲嶈瘯');", null)
                            }
                        }
                    }
                }
            }
        }

        // 鍔犺浇HTML鏂囦欢锛堝敮涓€妯℃澘锛?
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
         * 涓婁紶鍥剧墖锛堥€愬紶涓婁紶锛屽洖浼犵姸鎬侊級
         * @param imageDataListJson JSON鏁扮粍锛歔{imageId: "img_xxx", base64: "..."}, ...]
         */
        @JavascriptInterface
        fun uploadImages(imageDataListJson: String) {
            Thread {
                try {
                    // 瑙ｆ瀽鍥剧墖鏁版嵁鍒楄〃锛堝惈 requestId锛岀敤浜庡洖璋冨幓閲嶏級
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
            }
        }


        /**
         * 杩斿洖璁惧鏈湴鏃堕棿瀛楃涓诧細YYYY-MM-DD HH:mm锛堝懆X锛?鏃跺尯鍚?
         */
        @JavascriptInterface
        fun getEntitlement(callbackId: String) {
            SessionApi.getEntitlement(IdManager.getClientId()) { json ->
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
                val zone = java.time.ZoneId.of("Asia/Taipei")
                val now = java.time.ZonedDateTime.now(zone)
                val week = arrayOf("日", "一", "二", "三", "四", "五", "六")[now.dayOfWeek.value % 7]
                String.format("%04d-%02d-%02d %02d:%02d锛堝懆%s锛?%s",
                    now.year, now.monthValue, now.dayOfMonth,
                    now.hour, now.minute, week, zone.id)
            } catch (_: Exception) {
                try {
                    val now = java.util.Calendar.getInstance()
                    val week = arrayOf("日", "一", "二", "三", "四", "五", "六")[now.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                    val zoneName = now.timeZone.id
                    String.format("%04d-%02d-%02d %02d:%02d锛堝懆%s锛?%s",
                        now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH) + 1, now.get(java.util.Calendar.DAY_OF_MONTH),
                        now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE), week, zoneName)
                } catch (_: Exception) {
                    ""
                }
            }
        }

        /**
         * 閲嶈瘯鍗曞紶鍥剧墖涓婁紶锛堜笉浼?base64锛岀敤缂撳瓨 bytes 閲嶄紶锛涚紦瀛樼己澶卞垯鍥炰紶 fail + 璇烽噸鏂伴€夋嫨璇ュ浘鐗囷級
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
         * 鍙戦€佹秷鎭紙姝ゆ椂鍥剧墖宸蹭笂浼犳垚鍔燂紝浼犲叆鐨勬槸URL鍒楄〃锛?
         * @param text 鐢ㄦ埛鏂囧瓧
         * @param imageUrlsJson JSON鏁扮粍锛歔"https://...", ...] 鎴?"null"
         * @param streamId 鍓嶇鐢熸垚鐨勬祦ID锛屽洖璋冩椂鍐欏搴旀皵娉★紙鍙栨秷鏃舵棫姘旀场钀界粓鎬侊級
         * @param model 鍙€夛紝鍓嶇浼犲叆妗ｄ綅鏍囪瘑锛坒ree/plus/pro锛夛紱涓诲璇濇ā鍨嬪浐瀹?MODEL_MAIN(qwen3.5-plus)
         */
        @JavascriptInterface
        fun sendMessage(text: String, imageUrlsJson: String, streamId: String?, model: String?) {
            sendMessage(text, imageUrlsJson, streamId, model, null)
        }

        @JavascriptInterface
        fun sendMessage(text: String, imageUrlsJson: String, streamId: String?, model: String?, clientMsgId: String?) {
            runOnUiThread {
                // 鍗曢锛氫笉鎸¤繛鐐癸紱杩炵偣 = 鍙栨秷鏃ф祦鍐嶅紑鏂版祦锛実etReply 鍐?cancelCurrentRequest

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
                // 鍙戦€佸墠鏂綉鎷︽埅锛氭棤缃戜笉杞湀锛岀洿鎺ュ湪璇ユ潯 assistant 鏄剧ず銆屽綋鍓嶆棤缃戠粶锛屾湭鍙戦€併€傘€? 閲嶈瘯
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

        /** 鐏板瓧銆屽凡涓柇 路 鐐瑰嚮缁х画銆嶇偣鍑绘椂鐢卞墠绔皟鐢紝瑙﹀彂琛ュ叏璇锋眰锛堟棤 tools锛?*/
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
         * 涓婁紶鍗曞紶鍥剧墖锛堥€愬紶澶勭悊锛涘洖浼?requestId 渚涘墠绔幓閲嶏級
         */
        private fun uploadSingleImage(imageId: String, base64: String, requestId: String) {
            Thread {
                try {
                    // 鍥炰紶锛氬紑濮嬩笂浼狅紙甯?requestId锛?
                    runOnUiThread {
                        val escapedImageId = escapeJs(imageId)
                        val escapedRequestId = escapeJs(requestId)
                        webView.evaluateJavascript("window.onImageUploadStatus('$escapedImageId', 'uploading', null, '$escapedRequestId', null);", null)
                    }
                    
                    // base64 瑙ｇ爜鍚庡嵆鐢紝涓嶉暱鏈熼┗鐣?
                    val imageBytes = Base64.decode(base64, Base64.DEFAULT)
                    
                    // 鍘嬬缉锛欵XIF 鐭 鈫?鍥哄畾搴忓垪 1024@82鈫?024@80鈫?024@75鈫?024@70鈫?96@70鈫?68@70鈫?40@70鈫?40@60鈫?12@60锛堢洰鏍団墹1MB锛?
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
                        Log.e("MainActivity", "鍥剧墖[$imageId] 鍘嬬缉鍚庝粛瓒?MB锛屼笉璋冪敤妯″瀷/涓嶆墸璐?涓嶈杞")
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
        
        /** 鐢ㄥ凡鍘嬬缉 bytes 涓婁紶锛堥娆℃垨閲嶈瘯鍏辩敤锛夛紱澶辫触鍥炶皟涓嶅甫 errorMessage锛岀敱璋冪敤鏂瑰喅瀹?*/
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

        /** 鍒囧悗鍙版椂鎸?streamId 缂撳瓨浜嬩欢锛屽彈瀛楁暟/娴佹暟/鏃堕棿涓変笂闄愶紱瓒呴檺涓㈠純骞惰 cache_overflow */
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
         * 鍙戦€佹ā鍨嬭姹傦紙streamId 璐┛锛涗粎鏂拌姹傛椂 cancel 鏃ц姹傦紝鍒囧悗鍙颁笉鏂綉銆佺紦瀛樿ˉ鍙戯級
         * @param chatModel 涓诲璇濇。浣嶆爣璇嗭紙free/plus/pro锛夛紱涓诲璇濇ā鍨嬪浐瀹?MODEL_MAIN(qwen3.5-plus)锛孊 鎽樿鍥哄畾 MODEL_B_SUMMARY(qwen-flash)
         */
        private fun sendToModel(text: String, imageUrlList: List<String>, streamId: String, chatModel: String? = null) {
            pendingUserByStreamId[streamId] = text
            pendingChatModelByStreamId[streamId] = chatModel
            currentChatModelForResume = chatModel
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

        /** 琛ュ叏璇锋眰锛氭棤 tools锛屽悓涓€ streamId 缁啓锛涙垚鍔熷垯 dispatchComplete 鍐?A锛屽け璐ヤ繚鎸佺伆瀛?*/
        fun triggerResume(streamId: String?) {
            val state = resumeState ?: return
            val sid = streamId ?: state.streamId
            if (state.streamId != sid || !state.isWaitingResume || state.lastUserInputText.isBlank()) return
            val now = System.currentTimeMillis()
            if (now - state.lastFailAt < RESUME_THROTTLE_MS) return
            state.lastFailAt = now
            val continuationUserMessage = state.lastUserInputText + "\n璇蜂粠鎴戝凡杈撳嚭鐨勫唴瀹圭户缁紝閬垮厤閲嶅銆傚凡杈撳嚭鍓嶇紑濡備笅锛歕n" + state.assistantPrefix.take(CONTINUATION_PREFIX_MAX)
            pendingUserByStreamId[sid] = state.lastUserInputText
            pendingAssistantByStreamId[sid] = StringBuilder(state.assistantPrefix)
            currentStreamId = sid
            isRequesting = true
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

    /** onResume 鏃惰ˉ鍙戝垏鍚庡彴鏈熼棿缂撳瓨鐨?chunk/complete/interrupted锛涘悎骞惰繛缁?chunk 骞舵寜 stream 鍒嗘壒 post 閬垮厤 UI 鍗￠】 */
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

    /** 灏嗗悓涓€娴佸唴杩炵画 chunk 鍚堝苟涓轰竴鏉★紝淇濇寔 complete/interrupted 椤哄簭锛屽噺灏?JS 璋冪敤娆℃暟 */
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
        // 涓嶅湪姝?cancel锛氫粎鏂拌姹傛垨鐢ㄦ埛鐐瑰仠姝㈡椂 cancel锛屽垏鍚庡彴涓嶆柇缃?
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

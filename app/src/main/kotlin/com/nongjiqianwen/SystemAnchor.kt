package com.nongjiqianwen

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicLong

/**
 * 系统锚点：从 assets/system_anchor.txt 加载，供每次请求注入为 system role。
 * 兜底规则：注入失败时优先重读完整锚点（Level-1），不可用时降级短锚点（Level-2）；
 * system 全局只保留 1 条，内容只覆盖不拼接，严禁累加膨胀。
 */
object SystemAnchor {
    private const val TAG = "SystemAnchor"
    private const val ASSET_PATH = "system_anchor.txt"
    private const val MIN_VALID_LENGTH = 200
    private const val WARN_THROTTLE_MS = 60_000L

    @Volatile
    private var cachedText: String = ""

    @Volatile
    private var appContext: Context? = null

    private val lock = Any()
    private var lastWarnReason: String? = null
    private var lastWarnTimeMs: Long = 0L

    /** 兜底短锚点：完整锚点不可用时使用；长度须 >= MIN_VALID_LENGTH(200)，避免被误判 too_short */
    private const val FALLBACK_ANCHOR = """【系统兜底短锚点】
你是"农技千问"，资深农业技术顾问。仅提供农业技术建议与可行方案，不替用户做最终决策。
当前轮输入优先；历史仅用于语义承接。输出控制在约800字以内；用自然段与要点列表表达；禁止表情符号。
信息不足时先追问1–2条关键参数（作物/地区/生育期/症状或图片/近期操作），不得用臆测代替信息。
涉及不可逆或高风险操作（如清园、拔除、重度用药、停产处理等）不得武断结论；需说明不确定性，并给更低风险验证/替代方案。
联网：默认不联网；仅强时效（天气/价格/政策/预警等）、强客观核对（法规标准/官方名单/登记备案入口与路径/厂家主体等）、或用户明确要求权威出处引用时才可联网；能不联网继续推进就不联；同一轮最多一次；未命中须说明不确定，不得伪造"已查到"。
用户询问会员档位/配额/剩余次数/扣减原因：只提示"请在会员中心查看当前档位与剩余次数"；不解释系统内部机制与原因；不提训练数据/模型来源等。"""

    /** 必须在 Application 或 MainActivity 启动时调用一次 */
    fun init(context: Context) {
        if (cachedText.isNotEmpty()) return
        synchronized(lock) {
            if (cachedText.isNotEmpty()) return
            appContext = context.applicationContext
            try {
                context.assets.open(ASSET_PATH).use { input ->
                    cachedText = InputStreamReader(input, Charsets.UTF_8).readText().trim()
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "系统锚点已加载，长度=${cachedText.length}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载 system_anchor.txt 失败", e)
                cachedText = ""
            }
            if (FALLBACK_ANCHOR.length < MIN_VALID_LENGTH) {
                Log.w(TAG, "FALLBACK_ANCHOR 长度不足 $MIN_VALID_LENGTH，可能被误判为 too_short")
            }
        }
    }

    /** 供 QwenClient 每次请求注入；未 init 或加载失败时返回空字符串 */
    fun getText(): String = cachedText

    /** 短锚点，仅当完整锚点不可用时使用 */
    fun getFallbackAnchor(): String = FALLBACK_ANCHOR

    /**
     * Slow path：检测到注入失败时重读 assets，尝试恢复完整锚点。
     * @return true 表示读取并校验通过（trim 非空且长度≥MIN_VALID_LENGTH）
     */
    fun reloadFromAssets(): Boolean {
        val ctx = appContext ?: return false
        synchronized(lock) {
            try {
                ctx.assets.open(ASSET_PATH).use { input ->
                    val raw = InputStreamReader(input, Charsets.UTF_8).readText().trim()
                    if (raw.length >= MIN_VALID_LENGTH) {
                        cachedText = raw
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "reloadFromAssets 失败", e)
            }
            return false
        }
    }

    /**
     * 确保 messages 中恰好有一条 system 且位于 index 0，内容有效（覆盖式，不累加）。
     * 注入失败时：Level-1 重读完整锚点并覆盖；Level-1 不可用时 Level-2 使用短锚点。
     */
    fun ensureSystemRole(messagesArray: com.google.gson.JsonArray) {
        var reason = "ok"
        val systemIndices = mutableListOf<Int>()
        for (i in 0 until messagesArray.size()) {
            val el = messagesArray.get(i)
            if (el.isJsonObject && el.asJsonObject.get("role")?.asString == "system") {
                systemIndices.add(i)
            }
        }
        when {
            systemIndices.isEmpty() -> reason = "missing_system"
            systemIndices.size > 1 -> reason = "multi_system"
            systemIndices[0] != 0 -> reason = "not_at_zero"
            else -> {
                val first = messagesArray.get(systemIndices[0]).asJsonObject
                val content = first.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                val fallbackTrim = getFallbackAnchor().trim()
                when {
                    content == null || content.isEmpty() -> reason = "empty"
                    content.length < MIN_VALID_LENGTH && content != fallbackTrim -> reason = "too_short"
                    else -> { /* valid：已是 fallback 时不再因 too_short 触发兜底，防循环 */ }
                }
            }
        }
        if (reason == "ok") return

        var contentToUse: String? = null
        var strategy = "Level-2(fallback)"

        if (reloadFromAssets()) {
            val full = getText()
            if (full.isNotBlank() && full.length >= MIN_VALID_LENGTH) {
                contentToUse = full
                strategy = "Level-1(full)"
            }
        }
        if (contentToUse == null) {
            contentToUse = getFallbackAnchor()
        }

        applySystemFix(messagesArray, contentToUse!!)
        logWarnThrottled(reason, strategy)
    }

    private fun applySystemFix(messagesArray: com.google.gson.JsonArray, content: String) {
        val nonSystemRefs = mutableListOf<com.google.gson.JsonElement>()
        for (i in 0 until messagesArray.size()) {
            val el = messagesArray.get(i)
            if (el.isJsonObject && el.asJsonObject.get("role")?.asString != "system") {
                nonSystemRefs.add(el)
            }
        }
        while (messagesArray.size() > 0) messagesArray.remove(0)
        val systemObj = JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", content)
        }
        messagesArray.add(systemObj)
        nonSystemRefs.forEach { messagesArray.add(it) }
    }

    private fun logWarnThrottled(reason: String, strategy: String) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (lastWarnReason == reason && (now - lastWarnTimeMs) < WARN_THROTTLE_MS) return
            lastWarnReason = reason
            lastWarnTimeMs = now
        }
        Log.w(TAG, "system锚点兜底 reason=$reason strategy=$strategy buildType=${if (BuildConfig.DEBUG) "debug" else "release"}")
    }
}

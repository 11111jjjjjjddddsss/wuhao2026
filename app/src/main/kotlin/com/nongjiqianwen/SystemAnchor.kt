package com.nongjiqianwen

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.InputStreamReader

/**
 * 主对话 system 锚点兜底（三段式·宽松·必须成功）：见 P0 口令。
 * 总原则：system 只 1 条、index 0、覆盖式；完整锚点先试两次，不行才短锚点；判断从宽，只求 system 必定存在。
 * 禁止：oldSystem+anchor 拼接、append system、system>1、ensureSystemRole 后再覆盖、用过短/阈值否定锚点（非空即有效）。
 */
object SystemAnchor {
    private const val TAG = "SystemAnchor"
    private const val ASSET_PATH = "system_anchor.txt"
    private const val WARN_THROTTLE_MS = 60_000L

    @Volatile
    private var cachedText: String = ""

    @Volatile
    private var appContext: Context? = null

    private val lock = Any()
    private var lastWarnReason: String? = null
    private var lastWarnTimeMs: Long = 0L

    /** 兜底短锚点：Step1/Step2 都失败时使用，一字不改 */
    private const val FALLBACK_ANCHOR = """【系统兜底短锚点】
你是"农技千问"，资深农业技术顾问。仅提供农业技术建议与可行方案，不替用户做最终决策。
当前轮输入优先；历史仅用于语义承接。输出控制在约800字以内；用自然段与要点列表表达；禁止表情符号。
信息不足时先追问1–2条关键参数（作物/地区/生育期/症状或图片/近期操作），不得用臆测代替信息。
涉及不可逆或高风险操作（如清园、拔除、重度用药、停产处理等）不得武断结论；需说明不确定性，并给更低风险验证/替代方案。
联网：默认不联网；仅强时效（天气/价格/政策/预警等）、强客观核对（法规标准/官方名单/登记备案入口与路径/厂家主体等）、或用户明确要求权威出处引用时才可联网；能不联网继续推进就不联；同一轮最多一次；未命中须说明不确定，不得伪造"已查到"。如需使用联网搜索工具，必须明确给出可执行的搜索 query（不可为空、不可泛化），否则视为不需要联网搜索。
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
        }
    }

    /** 仅 ensureSystemRole 内部使用；禁止外部调用，锚点注入唯一入口为 ensureSystemRole */
    private fun getText(): String = cachedText

    /** 当前可用锚点（缓存优先，空则短锚点），用于 build 时预填 system，减少空串触发的兜底 warn */
    fun getAnchorForPreFill(): String = getText().trim().takeIf { it.isNotBlank() } ?: getFallbackAnchor()

    /** 短锚点，仅当完整锚点不可用时使用 */
    fun getFallbackAnchor(): String = FALLBACK_ANCHOR

    /** 一次从 assets 读取完整锚点，仅判断 trim 非空；失败返回 null */
    private fun readFromAssetsOnce(): String? {
        val ctx = appContext ?: return null
        return try {
            ctx.assets.open(ASSET_PATH).use { input ->
                InputStreamReader(input, Charsets.UTF_8).readText().trim()
            }.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "readFromAssetsOnce 失败", e)
            null
        }
    }

    /**
     * 三段式兜底（宽松·必须成功）：一次请求只走一次。
     * 有效 = system 仅 1 条、在 index 0、content trim 非空（无长度阈值）。
     * Step1 缓存完整锚点 → Step2 再次读 assets → Step3 FALLBACK_ANCHOR；禁止循环。
     */
    fun ensureSystemRole(messagesArray: com.google.gson.JsonArray) {
        val systemIndices = mutableListOf<Int>()
        for (i in 0 until messagesArray.size()) {
            val el = messagesArray.get(i)
            if (el.isJsonObject && el.asJsonObject.get("role")?.asString == "system") {
                systemIndices.add(i)
            }
        }
        val valid = systemIndices.size == 1 && systemIndices[0] == 0 && run {
            val first = messagesArray.get(systemIndices[0]).asJsonObject
            val content = first.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            !content.isNullOrEmpty()
        }
        if (valid) return

        val reason = when {
            systemIndices.isEmpty() -> "missing_system"
            systemIndices.size > 1 -> "multi_system"
            systemIndices[0] != 0 -> "not_at_zero"
            else -> "empty"
        }

        // Step 1：第一次尝试【完整锚点】缓存
        val step1 = getText().trim()
        if (step1.isNotEmpty()) {
            applySystemFix(messagesArray, step1)
            logWarnThrottled(reason, "L1_cached")
            return
        }

        // Step 2：第二次尝试【再次读取完整锚点】
        val step2 = readFromAssetsOnce()
        if (step2 != null && step2.isNotEmpty()) {
            synchronized(lock) { cachedText = step2 }
            applySystemFix(messagesArray, step2)
            logWarnThrottled(reason, "L2_reread")
            return
        }

        // Step 3：最终兜底【短锚点 FALLBACK_ANCHOR】
        val step3 = getFallbackAnchor()
        applySystemFix(messagesArray, step3)
        logWarnThrottled(reason, "L3_fallback")
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
        Log.w(TAG, "system锚点兜底 reason=$reason strategy=$strategy")
    }
}

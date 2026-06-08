package com.nongjiqianwen

import android.content.Context
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

object AppCrashReporter {
    private const val PREFS_NAME = "app_crash_reports"
    private const val KEY_PENDING = "pending_crash"
    private val installed = AtomicBoolean(false)
    private val handlingCrash = AtomicBoolean(false)

    @Volatile
    private var authStage: String? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (handlingCrash.compareAndSet(false, true)) {
                runCatching { persistCrash(appContext, thread, throwable) }
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun setAuthStage(stage: String?) {
        authStage = stage
    }

    fun clearAuthStage(stage: String? = null) {
        if (stage == null || authStage == stage) {
            authStage = null
        }
    }

    fun flushPendingReport(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PENDING, null)?.takeIf { it.isNotBlank() } ?: return
        prefs.edit().remove(KEY_PENDING).apply()
        val values = raw.split('\n')
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
        val savedEvent = values["event"].orEmpty().ifBlank { "app.crash" }
        val event = if (savedEvent == "app.crash" && !IdManager.isLoggedIn()) "auth.app_crash" else savedEvent
        val attrs = mapOf(
            "stage" to values["stage"].orEmpty().ifBlank { if (event.startsWith("auth.")) "prelogin" else "" },
            "exception" to values["exception"].orEmpty(),
            "cause" to values["cause"].orEmpty(),
            "thread" to values["thread"].orEmpty(),
            "top_class" to values["top_class"].orEmpty(),
            "top_method" to values["top_method"].orEmpty(),
            "top_line" to values["top_line"].orEmpty(),
            "crashed_at_ms" to values["crashed_at_ms"].orEmpty()
        ).filterValues { it.isNotBlank() }
        if (event.startsWith("auth.")) {
            SessionApi.reportAuthClientLog(
                level = "error",
                event = event,
                message = event,
                attrs = attrs
            )
        } else {
            SessionApi.reportClientLog(
                level = "error",
                event = event,
                message = event,
                attrs = attrs
            )
        }
    }

    private fun persistCrash(context: Context, thread: Thread, throwable: Throwable) {
        val stage = authStage.orEmpty().take(64)
        val isAuthCrash = stage.startsWith("auth.")
        val top = throwable.stackTrace.firstOrNull()
        val payload = listOf(
            "event" to if (isAuthCrash) "auth.app_crash" else "app.crash",
            "stage" to stage,
            "exception" to throwable.javaClass.name.take(160),
            "cause" to (throwable.cause?.javaClass?.name ?: "").take(160),
            "thread" to thread.name.take(80),
            "top_class" to (top?.className ?: "").take(160),
            "top_method" to (top?.methodName ?: "").take(96),
            "top_line" to (top?.lineNumber?.takeIf { it > 0 }?.toString() ?: ""),
            "crashed_at_ms" to System.currentTimeMillis().toString()
        ).joinToString("\n") { (key, value) ->
            "$key=${value.replace('\n', ' ').replace('\r', ' ')}"
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING, payload)
            .commit()
    }
}

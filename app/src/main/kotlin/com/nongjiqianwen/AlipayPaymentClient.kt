package com.nongjiqianwen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import com.alipay.sdk.app.PayTask
import java.util.concurrent.Executors

internal enum class AlipayPaymentSyncStatus {
    Success,
    Processing,
    Cancelled,
    NetworkError,
    Failed
}

internal data class AlipayPaymentSyncResult(
    val status: AlipayPaymentSyncStatus,
    val resultStatus: String
)

internal object AlipayPaymentClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun pay(
        activity: Activity,
        orderString: String,
        onResult: (AlipayPaymentSyncResult) -> Unit
    ) {
        val safeOrderString = orderString.trim()
        if (safeOrderString.isEmpty()) {
            onResult(AlipayPaymentSyncResult(AlipayPaymentSyncStatus.Failed, ""))
            return
        }
        executor.execute {
            val result = runCatching {
                PayTask(activity).payV2(safeOrderString, true)
            }.getOrElse { emptyMap() }
            val resultStatus = result["resultStatus"].orEmpty()
            val status = when (resultStatus) {
                "9000" -> AlipayPaymentSyncStatus.Success
                "8000", "6004", "6006" -> AlipayPaymentSyncStatus.Processing
                "6001" -> AlipayPaymentSyncStatus.Cancelled
                "6002" -> AlipayPaymentSyncStatus.NetworkError
                else -> AlipayPaymentSyncStatus.Failed
            }
            mainHandler.post {
                onResult(AlipayPaymentSyncResult(status = status, resultStatus = resultStatus))
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

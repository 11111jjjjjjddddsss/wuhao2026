package com.nongjiqianwen

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    private companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )
        IdManager.init(this)
        ABLayerManager.init(this)
        preloadSessionSnapshot()
        setContent {
            MaterialTheme {
                Surface {
                    ChatScreen()
                }
            }
        }
    }

    private fun preloadSessionSnapshot() {
        if (!BuildConfig.USE_BACKEND_AB || !SessionApi.hasBackendConfigured()) return
        SessionApi.getSnapshot(IdManager.getSessionId()) { snapshot ->
            if (snapshot == null) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "session snapshot preload skipped: empty")
                }
                return@getSnapshot
            }
            ABLayerManager.loadSnapshot(snapshot)
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "session snapshot preloaded: a=${snapshot.a_rounds_full.size} bLen=${snapshot.b_summary.length} cLen=${snapshot.c_summary.length}",
                )
            }
        }
    }
}

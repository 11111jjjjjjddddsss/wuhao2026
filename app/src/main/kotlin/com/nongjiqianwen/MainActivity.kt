package com.nongjiqianwen

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

object LaunchUiGate {
    @Volatile
    var chatReady: Boolean = false

    @Volatile
    var splashDeadlineMs: Long = 0L
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        LaunchUiGate.chatReady = false
        LaunchUiGate.splashDeadlineMs = SystemClock.uptimeMillis() + 140L
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !LaunchUiGate.chatReady && SystemClock.uptimeMillis() < LaunchUiGate.splashDeadlineMs
        }
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
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ChatScreen()
                }
            }
        }
    }
}

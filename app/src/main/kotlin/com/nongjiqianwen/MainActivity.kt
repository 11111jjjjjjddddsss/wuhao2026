package com.nongjiqianwen

import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

object LaunchUiGate {
    @Volatile
    var chatReady: Boolean = false

    @Volatile
    var splashDeadlineMs: Long = 0L
}

class MainActivity : ComponentActivity() {
    private var postPrivacyRuntimeInitialized = false

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCrashReporter.install(applicationContext)
        LaunchUiGate.chatReady = false
        LaunchUiGate.splashDeadlineMs = SystemClock.uptimeMillis() + 900L
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !LaunchUiGate.chatReady && SystemClock.uptimeMillis() < LaunchUiGate.splashDeadlineMs
        }
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.WHITE,
                darkScrim = Color.WHITE
            )
        )
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        ComposeFoundationFlags.isNewContextMenuEnabled = false
        ComposeFoundationFlags.isSmartSelectionEnabled = false
        val privacyAcceptedOnCreate = PrivacyConsentStore.isAccepted(this)
        if (privacyAcceptedOnCreate) {
            initializePostPrivacyConsentRuntime()
        }
        setContent {
            var privacyAccepted by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(privacyAcceptedOnCreate)
            }
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    if (privacyAccepted) {
                        LoginGate {
                            ChatScreen()
                        }
                    } else {
                        PrivacyConsentGate(
                            onAccepted = {
                                PrivacyConsentStore.accept(this@MainActivity)
                                initializePostPrivacyConsentRuntime()
                                privacyAccepted = true
                            },
                            onDeclined = {
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun initializePostPrivacyConsentRuntime() {
        if (postPrivacyRuntimeInitialized) return
        postPrivacyRuntimeInitialized = true
        IdManager.init(this)
        AppCrashReporter.flushPendingReport(applicationContext)
    }
}

package com.nongjiqianwen

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
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
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val iconView = splashScreenViewProvider.iconView.apply {
                pivotX = width / 2f
                pivotY = height / 2f
                scaleX = 0.9f
                scaleY = 0.9f
                alpha = 1f
                visibility = View.VISIBLE
            }

            iconView.animate()
                .rotationBy(74f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(760L)
                .withEndAction {
                    splashScreenViewProvider.view.animate()
                        .alpha(0f)
                        .setDuration(120L)
                        .withEndAction { splashScreenViewProvider.remove() }
                        .start()
                }
                .start()
        }
        IdManager.init(this)
        setContent {
            MaterialTheme {
                Surface { ChatScreen() }
            }
        }
    }
}

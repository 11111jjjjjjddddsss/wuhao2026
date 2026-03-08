package com.nongjiqianwen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setOnExitAnimationListener { splashViewProvider ->
            val iconView = splashViewProvider.iconView
            iconView.scaleX = 0.9f
            iconView.scaleY = 0.9f

            val scaleX = ObjectAnimator.ofFloat(iconView, "scaleX", 0.9f, 1f).apply {
                duration = 720L
                interpolator = DecelerateInterpolator()
            }
            val scaleY = ObjectAnimator.ofFloat(iconView, "scaleY", 0.9f, 1f).apply {
                duration = 720L
                interpolator = DecelerateInterpolator()
            }
            val fade = ObjectAnimator.ofFloat(splashViewProvider.view, "alpha", 1f, 0f).apply {
                duration = 120L
                startDelay = 600L
                interpolator = DecelerateInterpolator()
            }

            AnimatorSet().apply {
                playTogether(scaleX, scaleY, fade)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        splashViewProvider.remove()
                    }
                })
                start()
            }
        }

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

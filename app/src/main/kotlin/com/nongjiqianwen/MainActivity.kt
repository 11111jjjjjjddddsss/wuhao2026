package com.nongjiqianwen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
                Surface(color = Color(0xFF030303)) {
                    var showLaunchOverlay by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(760)
                        showLaunchOverlay = false
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        ChatScreen()
                        if (showLaunchOverlay) {
                            LaunchOverlay()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchOverlay() {
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(0.96f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        rotation.animateTo(
            targetValue = 68f,
            animationSpec = tween(durationMillis = 760, easing = LinearOutSlowInEasing)
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 760, easing = FastOutSlowInEasing)
        )
        delay(560)
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030303)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(248.dp)
                .graphicsLayer(
                    rotationZ = rotation.value,
                    scaleX = scale.value,
                    scaleY = scale.value,
                    alpha = alpha.value
                )
        )
    }
}

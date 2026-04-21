package com.nongjiqianwen.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal const val TARGET_PACKAGE = "com.nongjiqianwen"

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateChatBaselineProfile() {
        baselineProfileRule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            exerciseChatCriticalPath()
        }
    }
}

internal fun MacrobenchmarkScope.exerciseChatCriticalPath() {
    device.waitForIdle()
    scrollChatContent(device)
    focusComposer(device)
    device.executeShellCommand("input text nongjiqianwen")
    device.waitForIdle()
    device.pressBack()
    device.waitForIdle()
    scrollChatContent(device)
}

private fun scrollChatContent(device: UiDevice) {
    val centerX = device.displayWidth / 2
    val topY = (device.displayHeight * 0.32f).toInt()
    val bottomY = (device.displayHeight * 0.76f).toInt()
    repeat(2) {
        device.swipe(centerX, bottomY, centerX, topY, 24)
        device.waitForIdle()
        device.swipe(centerX, topY, centerX, bottomY, 24)
        device.waitForIdle()
    }
}

private fun focusComposer(device: UiDevice) {
    val composer =
        device.wait(Until.findObject(By.textContains("描述")), 1_000) ?:
            device.wait(Until.findObject(By.textContains("Ask")), 1_000) ?:
            device.wait(Until.findObject(By.textContains("Reply")), 1_000)
    if (composer != null) {
        composer.click()
    } else {
        device.click(device.displayWidth / 2, (device.displayHeight * 0.88f).toInt())
    }
    device.waitForIdle()
}

package com.nongjiqianwen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityBoundaryTest {
    @Test
    fun stableAppUpdateApkUrlRejectsDecoratedReleaseUrls() {
        assertTrue(isStableAppUpdateApkUrlValue("https://download.nongjiqiancha.cn/android/releases/4/nongjiqiancha-1.0.4.apk"))
        assertTrue(isStableAppUpdateApkUrlValue("https://download.nongjiqiancha.cn:443/android/releases/4/nongjiqiancha-1.0.4.apk"))

        listOf(
            "https://download.nongjiqiancha.cn:4443/android/releases/4/nongjiqiancha-1.0.4.apk",
            "https://download.nongjiqiancha.cn/android/releases/4/nongjiqiancha-1.0.4.apk?token=ordinary",
            "https://user:pass@download.nongjiqiancha.cn/android/releases/4/nongjiqiancha-1.0.4.apk",
            "https://download.nongjiqiancha.cn/android/releases/4/nongjiqiancha-1.0.4.apk#fragment",
            "https://download.nongjiqiancha.cn/test-apks/debug/nongjiqiancha-debug.apk",
            "https://example.com/android/releases/4/nongjiqiancha-1.0.4.apk"
        ).forEach { url ->
            assertFalse("expected update URL to be rejected: $url", isStableAppUpdateApkUrlValue(url))
        }
    }

    @Test
    fun remoteImagePreviewOnlyTrustsBackendUploadUrls() {
        assertTrue("https://api.nongjiqiancha.cn/uploads/abc_123.jpg".isTrustedRemoteImageSource())
        assertTrue("https://api.nongjiqiancha.cn:443/uploads/abc_123.jpg".isTrustedRemoteImageSource())
        assertTrue("https://api.nongjiqiancha.cn/uploads/support/feedback-1.jpg".isTrustedRemoteImageSource())

        listOf(
            "http://api.nongjiqiancha.cn/uploads/abc_123.jpg",
            "https://api.nongjiqiancha.cn:4443/uploads/abc_123.jpg",
            "https://api.nongjiqiancha.cn/uploads/abc_123.jpg?token=ordinary",
            "https://api.nongjiqiancha.cn/uploads/abc_123.jpg#fragment",
            "https://user:pass@api.nongjiqiancha.cn/uploads/abc_123.jpg",
            "https://example.com/uploads/abc_123.jpg",
            "https://api.nongjiqiancha.cn/uploads/support/nested/abc.jpg",
            "https://api.nongjiqiancha.cn/uploads/abc_123.png",
            "https://api.nongjiqiancha.cn/uploads/%2e%2e/abc_123.jpg"
        ).forEach { url ->
            assertFalse("expected remote preview URL to be rejected: $url", url.isTrustedRemoteImageSource())
        }
    }
}

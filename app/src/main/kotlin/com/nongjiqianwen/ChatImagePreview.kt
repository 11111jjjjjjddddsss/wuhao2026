package com.nongjiqianwen

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.request.CachePolicy
import coil.request.ImageRequest
import okhttp3.Headers
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.util.Locale

private const val CHAT_IMAGE_PREVIEW_CACHE_MAX_KB = 12 * 1024
private const val CHAT_REMOTE_PREVIEW_MAX_BYTES = 2 * 1024 * 1024
private const val CHAT_REMOTE_PREVIEW_FAILURE_CACHE_MAX_ENTRIES = 256
private const val CHAT_REMOTE_PREVIEW_FAILURE_TTL_MS = 10 * 60 * 1000L

private val chatImagePreviewCache = object : LruCache<String, ImageBitmap>(CHAT_IMAGE_PREVIEW_CACHE_MAX_KB) {
    override fun sizeOf(key: String, value: ImageBitmap): Int {
        return ((value.width * value.height * 4) / 1024).coerceAtLeast(1)
    }
}
private val chatImagePreviewCacheLock = Any()
private val chatRemotePreviewFailureCache = object : LruCache<String, Long>(CHAT_REMOTE_PREVIEW_FAILURE_CACHE_MAX_ENTRIES) {}

private fun cachedChatImagePreview(cacheKey: String): ImageBitmap? =
    synchronized(chatImagePreviewCacheLock) {
        chatImagePreviewCache.get(cacheKey)
    }

private fun cacheChatImagePreview(cacheKey: String, bitmap: ImageBitmap): ImageBitmap =
    synchronized(chatImagePreviewCacheLock) {
        chatImagePreviewCache.put(cacheKey, bitmap)
        chatRemotePreviewFailureCache.remove(cacheKey)
        bitmap
    }

private fun isRemotePreviewTemporarilyUnavailable(cacheKey: String, nowMs: Long = SystemClock.elapsedRealtime()): Boolean =
    synchronized(chatImagePreviewCacheLock) {
        val expiresAt = chatRemotePreviewFailureCache.get(cacheKey) ?: return@synchronized false
        if (expiresAt > nowMs) {
            true
        } else {
            chatRemotePreviewFailureCache.remove(cacheKey)
            false
        }
    }

private fun markRemotePreviewTemporarilyUnavailable(cacheKey: String, nowMs: Long = SystemClock.elapsedRealtime()) {
    synchronized(chatImagePreviewCacheLock) {
        chatRemotePreviewFailureCache.put(cacheKey, nowMs + CHAT_REMOTE_PREVIEW_FAILURE_TTL_MS)
    }
}

internal fun clearSupportChatImagePreviewCache() {
    synchronized(chatImagePreviewCacheLock) {
        val supportKeys = chatImagePreviewCache.snapshot().keys.filter { it.startsWith(SUPPORT_IMAGE_PREVIEW_CACHE_PREFIX) }
        supportKeys.forEach { chatImagePreviewCache.remove(it) }
        val supportFailureKeys = chatRemotePreviewFailureCache.snapshot().keys.filter { it.startsWith(SUPPORT_IMAGE_PREVIEW_CACHE_PREFIX) }
        supportFailureKeys.forEach { chatRemotePreviewFailureCache.remove(it) }
    }
}

internal fun InputStream.readPreviewBytes(maxBytes: Int): ByteArray? {
    val buffer = ByteArray(8 * 1024)
    val output = ByteArrayOutputStream()
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun chatPreviewInSampleSize(width: Int, height: Int, targetSize: Int): Int {
    var sampleSize = 1
    if (width > targetSize || height > targetSize) {
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / sampleSize >= targetSize && halfHeight / sampleSize >= targetSize) {
            sampleSize *= 2
        }
    }
    return sampleSize.coerceAtLeast(1)
}

internal fun Context.decodeChatImagePreview(
    source: String,
    targetSize: Int = 512
): ImageBitmap? {
    val cacheKey = chatImagePreviewCacheKey(source)
    cachedChatImagePreview(cacheKey)?.let { return it }
    val isRemote = source.isRemoteImageSource()
    if (isRemote && !source.isTrustedRemoteImageSource()) {
        return null
    }
    if (isRemote && isRemotePreviewTemporarilyUnavailable(cacheKey)) {
        return null
    }
    val decoded = runCatching {
        val bytes = if (isRemote) {
            val connection = URL(source).openConnection().apply {
                connectTimeout = 5000
                readTimeout = 5000
                supportImageAuthorizationHeader(source)?.let { header ->
                    setRequestProperty("Authorization", header)
                }
            }
            connection.getInputStream().use { it.readPreviewBytes(CHAT_REMOTE_PREVIEW_MAX_BYTES) }
        } else {
            readImageBytes(Uri.parse(source))
        } ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@runCatching null
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = chatPreviewInSampleSize(bounds.outWidth, bounds.outHeight, targetSize = targetSize)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?.asImageBitmap()
            ?.let { cacheChatImagePreview(cacheKey, it) }
    }.getOrNull()
    if (isRemote && decoded == null) {
        markRemotePreviewTemporarilyUnavailable(cacheKey)
    }
    return decoded
}

internal fun authenticatedSupportImageModel(context: Context, model: Any?): Any? {
    val source = model as? String ?: return model
    val authHeader = supportImageAuthorizationHeader(source) ?: return model
    return ImageRequest.Builder(context)
        .data(source)
        .memoryCacheKey(chatImagePreviewCacheKey(source))
        .memoryCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .headers(Headers.Builder().add("Authorization", authHeader).build())
        .build()
}

internal fun String.isSupportRemoteImageSource(): Boolean {
    if (!isTrustedRemoteImageSource()) return false
    val uri = runCatching { URI(trim()) }.getOrNull() ?: return false
    val rawPath = uri.rawPath ?: return false
    val path = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)
    return path.lowercase(Locale.US).startsWith("/uploads/support/")
}

private fun supportImageAuthorizationHeader(source: String): String? {
    if (!source.isSupportRemoteImageSource()) return null
    val token = IdManager.getAuthToken()?.trim().orEmpty()
    if (token.isBlank()) return null
    return "Bearer $token"
}

private const val SUPPORT_IMAGE_PREVIEW_CACHE_PREFIX = "support:"

private fun chatImagePreviewCacheKey(source: String): String {
    if (!source.isSupportRemoteImageSource()) return source
    val accountKey = IdManager.getAuthenticatedUserId()?.trim().orEmpty()
    return "$SUPPORT_IMAGE_PREVIEW_CACHE_PREFIX$accountKey:$source"
}

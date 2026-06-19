package com.nongjiqianwen

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL

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

private fun cachedChatImagePreview(source: String): ImageBitmap? =
    synchronized(chatImagePreviewCacheLock) {
        chatImagePreviewCache.get(source)
    }

private fun cacheChatImagePreview(source: String, bitmap: ImageBitmap): ImageBitmap =
    synchronized(chatImagePreviewCacheLock) {
        chatImagePreviewCache.put(source, bitmap)
        chatRemotePreviewFailureCache.remove(source)
        bitmap
    }

private fun isRemotePreviewTemporarilyUnavailable(source: String, nowMs: Long = SystemClock.elapsedRealtime()): Boolean =
    synchronized(chatImagePreviewCacheLock) {
        val expiresAt = chatRemotePreviewFailureCache.get(source) ?: return@synchronized false
        if (expiresAt > nowMs) {
            true
        } else {
            chatRemotePreviewFailureCache.remove(source)
            false
        }
    }

private fun markRemotePreviewTemporarilyUnavailable(source: String, nowMs: Long = SystemClock.elapsedRealtime()) {
    synchronized(chatImagePreviewCacheLock) {
        chatRemotePreviewFailureCache.put(source, nowMs + CHAT_REMOTE_PREVIEW_FAILURE_TTL_MS)
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
    cachedChatImagePreview(source)?.let { return it }
    val isRemote = source.isRemoteImageSource()
    if (isRemote && !source.isTrustedRemoteImageSource()) {
        return null
    }
    if (isRemote && isRemotePreviewTemporarilyUnavailable(source)) {
        return null
    }
    val decoded = runCatching {
        val bytes = if (isRemote) {
            val connection = URL(source).openConnection().apply {
                connectTimeout = 5000
                readTimeout = 5000
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
            ?.let { cacheChatImagePreview(source, it) }
    }.getOrNull()
    if (isRemote && decoded == null) {
        markRemotePreviewTemporarilyUnavailable(source)
    }
    return decoded
}

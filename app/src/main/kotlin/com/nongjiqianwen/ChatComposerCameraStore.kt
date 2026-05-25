package com.nongjiqianwen

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

private const val COMPOSER_CAMERA_ALBUM_NAME = "农技千查"
private val COMPOSER_CAMERA_URI_PERMISSION_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

internal data class ComposerCameraImageTarget(
    val uri: Uri,
    val galleryBacked: Boolean,
    val temporaryFilePath: String? = null
)

internal fun Context.createComposerCameraImageTarget(): ComposerCameraImageTarget? {
    createTemporaryComposerCameraImageTarget()?.let { target ->
        return target
    }
    createGalleryComposerCameraImageUri()?.let { uri ->
        return ComposerCameraImageTarget(uri = uri, galleryBacked = true)
    }
    return null
}

private fun Context.createGalleryComposerCameraImageUri(): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "nongjiqiancha_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$COMPOSER_CAMERA_ALBUM_NAME"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }.getOrNull()
}

private fun Context.createTemporaryComposerCameraImageTarget(): ComposerCameraImageTarget? {
    return runCatching {
        val imageDir = File(cacheDir, "composer_camera").apply { mkdirs() }
        val imageFile = File.createTempFile("camera_", ".jpg", imageDir)
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            imageFile
        )
        ComposerCameraImageTarget(
            uri = uri,
            galleryBacked = false,
            temporaryFilePath = imageFile.absolutePath
        )
    }.getOrNull()
}

internal fun buildComposerCameraIntent(uri: Uri): Intent {
    return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, uri)
        clipData = ClipData.newRawUri("composer_camera_output", uri)
        addFlags(COMPOSER_CAMERA_URI_PERMISSION_FLAGS)
    }
}

internal fun Context.grantComposerCameraUri(uri: Uri, intent: Intent) {
    val cameraActivities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    cameraActivities.forEach { info ->
        val packageName = info.activityInfo?.packageName ?: return@forEach
        runCatching {
            grantUriPermission(packageName, uri, COMPOSER_CAMERA_URI_PERMISSION_FLAGS)
        }
    }
}

internal fun Context.revokeComposerCameraUri(uri: Uri) {
    runCatching {
        revokeUriPermission(uri, COMPOSER_CAMERA_URI_PERMISSION_FLAGS)
    }
}

internal fun Context.publishGalleryComposerCameraImage(uri: Uri): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        contentResolver.update(uri, values, null, null) > 0
    }.getOrDefault(false)
}

internal fun Context.deleteGalleryComposerCameraImage(uri: Uri) {
    runCatching {
        contentResolver.delete(uri, null, null)
    }
}

internal fun Context.saveComposerCameraImageToGallery(sourceUri: Uri) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "nongjiqiancha_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$COMPOSER_CAMERA_ALBUM_NAME"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val outputUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching
        var saved = false
        try {
            val copiedBytes = contentResolver.openInputStream(sourceUri)?.use { input ->
                contentResolver.openOutputStream(outputUri)?.use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching
            if (copiedBytes <= 0L) return@runCatching
            val publishValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            val publishCount = contentResolver.update(outputUri, publishValues, null, null)
            if (publishCount <= 0) return@runCatching
            saved = true
        } finally {
            if (!saved) {
                contentResolver.delete(outputUri, null, null)
            }
        }
    }
}

internal fun deleteTemporaryComposerCameraImage(path: String?) {
    if (path.isNullOrBlank()) return
    runCatching {
        File(path).takeIf { it.isFile }?.delete()
    }
}

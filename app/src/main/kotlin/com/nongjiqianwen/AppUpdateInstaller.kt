package com.nongjiqianwen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.Locale

object AppUpdateInstaller {
    private const val DEFAULT_MAX_APK_DOWNLOAD_BYTES = 200L * 1024L * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    enum class DownloadFailureReason {
        InvalidUrl,
        CacheDirUnavailable,
        Network,
        HttpStatus,
        NonHttpsRedirect,
        MissingBody,
        ExpectedSizeTooLarge,
        ContentTooLarge,
        ContentLengthMismatch,
        CopyTooLarge,
        DownloadedSizeMismatch,
        Sha256Mismatch,
        PackageInfoMissing,
        PackageNameMismatch,
        VersionCodeMismatch,
        VersionCodeNotNewer,
        RenameFailed,
        Unexpected
    }

    data class DownloadResult(
        val file: File? = null,
        val reason: DownloadFailureReason? = null,
        val httpStatus: Int? = null
    )

    enum class InstallFailureReason {
        FileMissing,
        IntentFailed
    }

    data class InstallResult(
        val started: Boolean,
        val reason: InstallFailureReason? = null
    )

    fun canRequestInstallPackages(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun downloadApk(context: Context, update: SessionApi.AppUpdateInfo): File? =
        downloadApkDetailed(context, update).file

    suspend fun downloadApkDetailed(context: Context, update: SessionApi.AppUpdateInfo): DownloadResult =
        withContext(Dispatchers.IO) {
            val apkUrl = update.apkUrl?.trim().orEmpty()
            if (!apkUrl.startsWith("https://")) {
                return@withContext DownloadResult(reason = DownloadFailureReason.InvalidUrl)
            }
            val outputDir = File(context.cacheDir, "app_updates")
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                return@withContext DownloadResult(reason = DownloadFailureReason.CacheDirUnavailable)
            }
            outputDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".apk") || file.name.endsWith(".tmp"))) {
                    file.delete()
                }
            }
            val versionCode = update.latestVersionCode ?: 0
            val outputFile = File(outputDir, "nongjiqiancha-$versionCode.apk")
            val tempFile = File(outputDir, "${outputFile.name}.tmp")
            try {
                val request = Request.Builder().url(apkUrl).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext DownloadResult(
                            reason = DownloadFailureReason.HttpStatus,
                            httpStatus = response.code
                        )
                    }
                    if (!response.request.url.isHttps) {
                        return@withContext DownloadResult(reason = DownloadFailureReason.NonHttpsRedirect)
                    }
                    val body = response.body
                        ?: return@withContext DownloadResult(reason = DownloadFailureReason.MissingBody)
                    val expectedSizeBytes = update.fileSizeBytes ?: 0L
                    if (expectedSizeBytes > DEFAULT_MAX_APK_DOWNLOAD_BYTES) {
                        return@withContext DownloadResult(reason = DownloadFailureReason.ExpectedSizeTooLarge)
                    }
                    val maxDownloadBytes = expectedSizeBytes
                        .takeIf { it > 0L }
                        ?.coerceAtMost(DEFAULT_MAX_APK_DOWNLOAD_BYTES)
                        ?: DEFAULT_MAX_APK_DOWNLOAD_BYTES
                    val contentLength = body.contentLength()
                    if (contentLength > maxDownloadBytes) {
                        return@withContext DownloadResult(reason = DownloadFailureReason.ContentTooLarge)
                    }
                    if (expectedSizeBytes > 0L && contentLength > 0L && contentLength != expectedSizeBytes) {
                        return@withContext DownloadResult(reason = DownloadFailureReason.ContentLengthMismatch)
                    }
                    tempFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            if (!copyToWithLimit(input, output, maxDownloadBytes)) {
                                tempFile.delete()
                                return@withContext DownloadResult(reason = DownloadFailureReason.CopyTooLarge)
                            }
                        }
                    }
                    val verifyFailure = verifyDownloadedApkFailure(context, tempFile, update)
                    if (verifyFailure != null) {
                        tempFile.delete()
                        return@withContext DownloadResult(reason = verifyFailure)
                    }
                    if (outputFile.exists()) outputFile.delete()
                    if (!tempFile.renameTo(outputFile)) {
                        return@withContext DownloadResult(reason = DownloadFailureReason.RenameFailed)
                    }
                    DownloadResult(file = outputFile)
                }
            } catch (_: IOException) {
                tempFile.delete()
                DownloadResult(reason = DownloadFailureReason.Network)
            } catch (_: IllegalArgumentException) {
                tempFile.delete()
                DownloadResult(reason = DownloadFailureReason.InvalidUrl)
            } catch (_: Exception) {
                tempFile.delete()
                DownloadResult(reason = DownloadFailureReason.Unexpected)
            }
        }

    fun installApk(context: Context, apkFile: File): Boolean {
        return installApkDetailed(context, apkFile).started
    }

    fun installApkDetailed(context: Context, apkFile: File): InstallResult {
        if (!apkFile.exists()) return InstallResult(started = false, reason = InstallFailureReason.FileMissing)
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            InstallResult(started = true)
        } catch (_: Exception) {
            InstallResult(started = false, reason = InstallFailureReason.IntentFailed)
        }
    }

    private fun verifyDownloadedApkFailure(
        context: Context,
        apkFile: File,
        update: SessionApi.AppUpdateInfo
    ): DownloadFailureReason? {
        val expectedSizeBytes = update.fileSizeBytes ?: 0L
        if (expectedSizeBytes > 0L && apkFile.length() != expectedSizeBytes) {
            return DownloadFailureReason.DownloadedSizeMismatch
        }

        val expectedSha256 = normalizeSha256(update.apkSha256)
        if (expectedSha256 != null && sha256Hex(apkFile) != expectedSha256) {
            return DownloadFailureReason.Sha256Mismatch
        }

        val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: return DownloadFailureReason.PackageInfoMissing
        if (packageInfo.packageName != context.packageName) {
            return DownloadFailureReason.PackageNameMismatch
        }

        val apkVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        val expectedVersionCode = update.latestVersionCode?.toLong()
        if (expectedVersionCode != null && expectedVersionCode > 0L && apkVersionCode != expectedVersionCode) {
            return DownloadFailureReason.VersionCodeMismatch
        }
        if (apkVersionCode <= BuildConfig.VERSION_CODE) {
            return DownloadFailureReason.VersionCodeNotNewer
        }
        return null
    }

    private fun normalizeSha256(raw: String?): String? {
        val value = raw
            ?.trim()
            ?.replace(":", "")
            ?.lowercase(Locale.US)
            .orEmpty()
        if (value.length != 64) return null
        return value.takeIf { hex ->
            hex.all { ch -> ch in '0'..'9' || ch in 'a'..'f' }
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun copyToWithLimit(input: InputStream, output: OutputStream, maxBytes: Long): Boolean {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read.toLong()
            if (total > maxBytes) return false
            output.write(buffer, 0, read)
        }
        return true
    }
}

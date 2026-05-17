package com.nongjiqianwen

import android.content.ActivityNotFoundException
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
import java.util.concurrent.TimeUnit

object AppUpdateInstaller {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

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
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    suspend fun downloadApk(context: Context, update: SessionApi.AppUpdateInfo): File? =
        withContext(Dispatchers.IO) {
            val apkUrl = update.apkUrl?.trim().orEmpty()
            if (!apkUrl.startsWith("https://")) return@withContext null
            val outputDir = File(context.cacheDir, "app_updates")
            if (!outputDir.exists() && !outputDir.mkdirs()) return@withContext null
            outputDir.listFiles()?.forEach { file ->
                if (file.isFile && (file.name.endsWith(".apk") || file.name.endsWith(".tmp"))) {
                    file.delete()
                }
            }
            val versionCode = update.latestVersionCode ?: 0
            val outputFile = File(outputDir, "nongjiqianwen-$versionCode.apk")
            val tempFile = File(outputDir, "${outputFile.name}.tmp")
            val request = Request.Builder().url(apkUrl).get().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    tempFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    if (outputFile.exists()) outputFile.delete()
                    if (!tempFile.renameTo(outputFile)) return@withContext null
                    outputFile
                }
            } catch (_: IOException) {
                tempFile.delete()
                null
            } catch (_: IllegalArgumentException) {
                tempFile.delete()
                null
            }
        }

    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false
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
            true
        } catch (_: Exception) {
            false
        }
    }
}

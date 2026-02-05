package com.nongjiqianwen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * 图片上传工具类
 * 负责：压缩图片 → 上传OSS → 返回https URL
 */
object ImageUploader {
    private const val TAG = "ImageUploader"
    private const val MAX_IMAGE_COUNT = 4
    /** 输入规则 P0：最长边 ≤1280px */
    private const val MAX_LONG_EDGE = 1280
    /** 输入规则 P0：单张 ≤800KB */
    private const val MAX_SIZE_BYTES = 800 * 1024
    /** 优先质量 80，超 800KB 再降 75 */
    private const val JPEG_QUALITY_HIGH = 80
    private const val JPEG_QUALITY_LOW = 75
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    /**
     * 压缩图片（中等压缩）
     * 处理流程：EXIF方向矫正 → 缩放长边1600px → JPEG quality=75
     * @param imageBytes 原始图片字节数组
     * @return 压缩后的字节数组和原图尺寸信息，失败返回null
     */
    fun compressImage(imageBytes: ByteArray): CompressResult? {
        return try {
            // 读取原始图片（用于获取EXIF和尺寸）
            val inputStream1 = ByteArrayInputStream(imageBytes)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream1, null, options)
            inputStream1.close()
            
            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            
            // 读取EXIF方向信息
            val inputStream2 = ByteArrayInputStream(imageBytes)
            val exif = ExifInterface(inputStream2)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream2.close()
            
            // 重新读取bitmap用于处理
            val inputStream3 = ByteArrayInputStream(imageBytes)
            val originalBitmap = BitmapFactory.decodeStream(inputStream3)
            inputStream3.close()
            if (originalBitmap == null) {
                Log.e(TAG, "无法解码图片")
                return null
            }
            
            // EXIF方向矫正
            val correctedBitmap = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(originalBitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(originalBitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(originalBitmap, 270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(originalBitmap, horizontal = true)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(originalBitmap, vertical = true)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    val rotated = rotateBitmap(originalBitmap, 90f)
                    flipBitmap(rotated, horizontal = true)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    val rotated = rotateBitmap(originalBitmap, 270f)
                    flipBitmap(rotated, horizontal = true)
                }
                else -> originalBitmap
            }
            
            val correctedWidth = correctedBitmap.width
            val correctedHeight = correctedBitmap.height
            
            // 计算缩放比例（长边缩到MAX_LONG_EDGE）
            val scale = if (correctedWidth > correctedHeight) {
                minOf(1.0f, MAX_LONG_EDGE.toFloat() / correctedWidth)
            } else {
                minOf(1.0f, MAX_LONG_EDGE.toFloat() / correctedHeight)
            }
            
            // 缩放图片（长边 ≤ MAX_LONG_EDGE）
            var scaledBitmap = if (scale < 1.0f) {
                val newWidth = (correctedWidth * scale).toInt()
                val newHeight = (correctedHeight * scale).toInt()
                Bitmap.createScaledBitmap(correctedBitmap, newWidth, newHeight, true)
            } else {
                correctedBitmap
            }
            
            // 转 JPEG：先质量 80，若 >800KB 再降 75；仍超限则按比例再缩放直至 ≤800KB
            var compressedBytes = compressToJpeg(scaledBitmap, JPEG_QUALITY_HIGH)
            if (compressedBytes.size > MAX_SIZE_BYTES) {
                compressedBytes = compressToJpeg(scaledBitmap, JPEG_QUALITY_LOW)
            }
            var resultBitmap = scaledBitmap
            var scaleFactor = 1.0f
            while (compressedBytes.size > MAX_SIZE_BYTES && scaleFactor > 0.25f) {
                scaleFactor *= 0.85f
                val w = (resultBitmap.width * scaleFactor).toInt().coerceAtLeast(1)
                val h = (resultBitmap.height * scaleFactor).toInt().coerceAtLeast(1)
                val next = Bitmap.createScaledBitmap(resultBitmap, w, h, true)
                if (resultBitmap != scaledBitmap && resultBitmap != correctedBitmap) resultBitmap.recycle()
                resultBitmap = next
                compressedBytes = compressToJpeg(resultBitmap, JPEG_QUALITY_LOW)
            }
            
            val finalWidth = resultBitmap.width
            val finalHeight = resultBitmap.height
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "=== 图片压缩日志(P0: 长边≤${MAX_LONG_EDGE}px, ≤${MAX_SIZE_BYTES / 1024}KB) ===")
                Log.d(TAG, "原图: ${originalWidth}x${originalHeight}, 压缩后: ${finalWidth}x${finalHeight}, ${compressedBytes.size} bytes")
            }
            
            if (resultBitmap != scaledBitmap && resultBitmap != correctedBitmap) {
                resultBitmap.recycle()
            }
            if (scaledBitmap != correctedBitmap && scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            if (correctedBitmap != originalBitmap) {
                correctedBitmap.recycle()
            }
            originalBitmap.recycle()
            
            CompressResult(
                bytes = compressedBytes,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                compressedWidth = finalWidth,
                compressedHeight = finalHeight,
                compressedSize = compressedBytes.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "图片压缩失败", e)
            null
        }
    }
    
    /** 输出 JPEG 字节数组（质量 75–80），用于 ≤800KB 控制 */
    private fun compressToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
    
    /**
     * 旋转bitmap
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * 翻转bitmap
     */
    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean = false, vertical: Boolean = false): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) {
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }
            if (vertical) {
                postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
            }
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * 压缩结果
     */
    data class CompressResult(
        val bytes: ByteArray,
        val originalWidth: Int,
        val originalHeight: Int,
        val compressedWidth: Int,
        val compressedHeight: Int,
        val compressedSize: Int
    )
    
    /**
     * 上传图片：走后端 POST /upload（multipart）-> 后端写入 OSS -> 返回 https 公网 URL
     * 禁止在 APP 内写 OSS AK/SK。未配置 UPLOAD_BASE_URL 时回调 onError。
     */
    fun uploadImage(
        imageBytes: ByteArray,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "=== 开始上传图片 ===")
            Log.d(TAG, "上传图片大小: ${imageBytes.size} bytes")
        }
        
        val baseUrl = BuildConfig.UPLOAD_BASE_URL?.trim() ?: ""
        if (baseUrl.isEmpty()) {
            Log.e(TAG, "上传失败：UPLOAD_BASE_URL 未配置（请在 gradle.properties 或 buildConfig 中配置后端地址）")
            Log.e(TAG, "HTTP状态码=未配置, 错误=OSS/后端接口未配置")
            onError("未配置上传服务")
            return
        }
        
        val uploadUrl = baseUrl.trimEnd('/') + "/upload"
        
        Thread {
            var tempFile: File? = null
            try {
                tempFile = File.createTempFile("upload_", ".jpg")
                FileOutputStream(tempFile).use { it.write(imageBytes) }
                
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        tempFile.name,
                        tempFile.asRequestBody("image/jpeg".toMediaType())
                    )
                    .build()
                
                val request = Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build()
                
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    val bodyStr = response.body?.string() ?: ""
                    
                    // 冻结协议：成功 200 仅认根级 url；失败 !=200 仅认根级 error
                    if (!response.isSuccessful) {
                        val errorMsg = try {
                            val json = com.google.gson.JsonParser().parse(bodyStr).asJsonObject
                            json.get("error")?.takeIf { it.isJsonPrimitive }?.asString ?: "HTTP $code"
                        } catch (_: Exception) {
                            "HTTP $code"
                        }
                        Log.e(TAG, "上传失败：HTTP状态码=$code, error=$errorMsg")
                        onError(errorMsg)
                        return@use
                    }
                    
                    // 只解析根级 url，不兼容 data.url 等其他字段
                    val json = com.google.gson.JsonParser().parse(bodyStr).asJsonObject
                    val url = json.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                    
                    if (!url.isNullOrBlank() && url.startsWith("https://")) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "上传成功：URL（脱敏）=${url.replace(Regex("/([^/]+)$"), "/***")}")
                        }
                        onSuccess(url)
                    } else {
                        Log.e(TAG, if (BuildConfig.DEBUG) "上传失败：响应无根级 url, body=${bodyStr.take(200)}" else "上传失败：响应无根级 url")
                        onError("上传失败：响应格式错误")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "上传异常", e)
                Log.e(TAG, "上传失败：异常=${e.message}")
                onError("上传异常: ${e.message}")
            } finally {
                tempFile?.delete()
            }
        }.start()
    }
    
    /**
     * 批量上传图片（并发上传，等待全部完成）
     * @param imageBytesList 图片字节数组列表（最多4张）
     * @return 成功返回URL列表，失败返回null
     */
    fun uploadImages(imageBytesList: List<ByteArray>): List<String>? {
        if (imageBytesList.isEmpty() || imageBytesList.size > MAX_IMAGE_COUNT) {
            Log.e(TAG, "图片数量无效: ${imageBytesList.size}，应在1-${MAX_IMAGE_COUNT}之间")
            return null
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "=== UPLOAD_URLS ===")
            Log.d(TAG, "开始上传 ${imageBytesList.size} 张图片")
        }
        
        val urls = mutableListOf<String>()
        val latch = CountDownLatch(imageBytesList.size)
        val errorRef = AtomicReference<String?>(null)
        
        // 并发上传
        imageBytesList.forEachIndexed { index, imageBytes ->
            Thread {
                try {
                    var uploadSuccess = false
                    uploadImage(
                        imageBytes = imageBytes,
                        onSuccess = { url ->
                            synchronized(urls) {
                                urls.add(url)
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "图片[$index] 上传成功: $url")
                                }
                            }
                            uploadSuccess = true
                            latch.countDown()
                        },
                        onError = { error ->
                            Log.e(TAG, "图片[$index] 上传失败: $error")
                            errorRef.set(error)
                            latch.countDown()
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "图片[$index] 上传异常", e)
                    errorRef.set(e.message)
                    latch.countDown()
                }
            }.start()
        }
        
        // 等待全部完成
        latch.await()
        
        if (errorRef.get() != null || urls.size != imageBytesList.size) {
            Log.e(TAG, "图片上传失败: ${errorRef.get()}, 成功: ${urls.size}/${imageBytesList.size}")
            return null
        }
        
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "所有图片上传成功: ${urls.size} 张")
            Log.d(TAG, "=== UPLOAD_URLS（脱敏）===")
            urls.forEachIndexed { index, url ->
                val maskedUrl = url.replace(Regex("/([^/]+)$"), "/***")
                Log.d(TAG, "UPLOAD_URLS[$index]: $maskedUrl")
            }
        }
        
        return urls
    }
}

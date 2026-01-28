package com.nongjiqianwen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
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
    private const val MAX_LONG_EDGE = 1600 // 长边最大尺寸
    private const val JPEG_QUALITY = 75 // 中等压缩质量
    
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
            
            // 缩放图片
            val scaledBitmap = if (scale < 1.0f) {
                val newWidth = (correctedWidth * scale).toInt()
                val newHeight = (correctedHeight * scale).toInt()
                Bitmap.createScaledBitmap(correctedBitmap, newWidth, newHeight, true)
            } else {
                correctedBitmap
            }
            
            // 转换为JPEG字节数组
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val compressedBytes = outputStream.toByteArray()
            
            // 记录日志
            Log.d(TAG, "=== 图片压缩日志 ===")
            Log.d(TAG, "原图尺寸: ${originalWidth}x${originalHeight}")
            Log.d(TAG, "EXIF矫正后: ${correctedWidth}x${correctedHeight} (orientation=$orientation)")
            Log.d(TAG, "压缩后尺寸: ${scaledBitmap.width}x${scaledBitmap.height}")
            Log.d(TAG, "压缩后字节数: ${compressedBytes.size} bytes")
            
            // 释放bitmap
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
                compressedWidth = scaledBitmap.width,
                compressedHeight = scaledBitmap.height,
                compressedSize = compressedBytes.size
            )
        } catch (e: Exception) {
            Log.e(TAG, "图片压缩失败", e)
            null
        }
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
     * 上传图片到OSS（需要配置OSS上传接口）
     * @param imageBytes 压缩后的图片字节数组
     * @param onSuccess 成功回调，返回https URL
     * @param onError 失败回调
     */
    fun uploadImage(
        imageBytes: ByteArray,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "=== 开始上传图片 ===")
        Log.d(TAG, "上传图片大小: ${imageBytes.size} bytes")
        
        // TODO: 配置OSS上传接口URL
        // 示例配置：
        // val uploadUrl = "https://your-oss-endpoint.com/upload"
        // 或者使用阿里云OSS SDK上传
        
        // 当前实现：返回占位URL（仅用于测试，实际需要配置真实OSS）
        // 实际使用时，需要：
        // 1. 配置OSS endpoint和bucket
        // 2. 实现上传逻辑，返回公网可访问的https URL
        // 3. 确保URL有效期覆盖本次请求（≥10分钟）
        
        // 模拟上传延迟（实际应该调用真实上传接口）
        Thread {
            try {
                // 模拟上传过程
                Thread.sleep(500)
                
                // 临时方案：返回错误，提示需要配置
                Log.e(TAG, "=== OSS上传接口未配置 ===")
                Log.e(TAG, "请在 ImageUploader.uploadImage() 中配置OSS上传接口")
                Log.e(TAG, "上传失败：HTTP状态码=未配置, 错误=OSS接口未配置")
                
                onError("图片上传功能未配置：请在ImageUploader.uploadImage()中配置OSS上传接口")
            } catch (e: Exception) {
                Log.e(TAG, "上传异常", e)
                Log.e(TAG, "上传失败：异常=${e.message}")
                onError("上传异常: ${e.message}")
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
        
        Log.d(TAG, "=== UPLOAD_URLS ===")
        Log.d(TAG, "开始上传 ${imageBytesList.size} 张图片")
        
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
                                Log.d(TAG, "图片[$index] 上传成功: $url")
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
        
        Log.d(TAG, "所有图片上传成功: ${urls.size} 张")
        Log.d(TAG, "=== UPLOAD_URLS（脱敏）===")
        urls.forEachIndexed { index, url ->
            // 脱敏：只显示域名和路径，隐藏具体文件名
            val maskedUrl = url.replace(Regex("/([^/]+)$"), "/***")
            Log.d(TAG, "UPLOAD_URLS[$index]: $maskedUrl")
        }
        
        return urls
    }
}

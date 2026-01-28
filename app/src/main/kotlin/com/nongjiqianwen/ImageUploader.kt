package com.nongjiqianwen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
     * @param inputStream 图片输入流
     * @return 压缩后的字节数组，失败返回null
     */
    fun compressImage(inputStream: InputStream): ByteArray? {
        return try {
            // 读取原始图片
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            if (originalBitmap == null) {
                Log.e(TAG, "无法解码图片")
                return null
            }
            
            val width = originalBitmap.width
            val height = originalBitmap.height
            
            // 计算缩放比例（长边缩到MAX_LONG_EDGE）
            val scale = if (width > height) {
                minOf(1.0f, MAX_LONG_EDGE.toFloat() / width)
            } else {
                minOf(1.0f, MAX_LONG_EDGE.toFloat() / height)
            }
            
            // 压缩图片
            val compressedBitmap = if (scale < 1.0f) {
                val newWidth = (width * scale).toInt()
                val newHeight = (height * scale).toInt()
                Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
            } else {
                originalBitmap
            }
            
            // 转换为JPEG字节数组
            val outputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val compressedBytes = outputStream.toByteArray()
            
            Log.d(TAG, "图片压缩完成: ${width}x${height} -> ${compressedBitmap.width}x${compressedBitmap.height}, ${compressedBytes.size} bytes")
            
            // 释放bitmap
            if (compressedBitmap != originalBitmap) {
                compressedBitmap.recycle()
            }
            originalBitmap.recycle()
            
            compressedBytes
        } catch (e: Exception) {
            Log.e(TAG, "图片压缩失败", e)
            null
        }
    }
    
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
        // TODO: 配置OSS上传接口URL
        // 示例配置：
        // val uploadUrl = "https://your-oss-endpoint.com/upload"
        // 或者使用阿里云OSS SDK上传
        
        // 当前实现：返回占位URL（仅用于测试，实际需要配置真实OSS）
        // 实际使用时，需要：
        // 1. 配置OSS endpoint和bucket
        // 2. 实现上传逻辑，返回公网可访问的https URL
        // 3. 确保URL有效期覆盖本次请求
        
        Log.e(TAG, "=== OSS上传接口未配置 ===")
        Log.e(TAG, "请在 ImageUploader.uploadImage() 中配置OSS上传接口")
        Log.e(TAG, "上传图片大小: ${imageBytes.size} bytes")
        
        // 临时方案：返回错误，提示需要配置
        onError("图片上传功能未配置：请在ImageUploader.uploadImage()中配置OSS上传接口")
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
        urls.forEachIndexed { index, url ->
            Log.d(TAG, "UPLOAD_URLS[$index]: $url")
        }
        
        return urls
    }
}

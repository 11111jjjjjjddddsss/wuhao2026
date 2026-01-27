package com.nongjiqianwen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val MAX_IMAGE_SIZE = 10 * 1024 * 1024 // 10MB
    private const val MAX_DIMENSION = 2048 // 最大尺寸
    
    /**
     * 将图片URI转换为Base64编码
     * @param uri 图片URI
     * @param inputStream 图片输入流
     * @return Base64编码的图片字符串，失败返回null
     */
    fun encodeImageToBase64(uri: Uri, inputStream: InputStream): String? {
        return try {
            // 读取图片
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap == null) {
                Log.e(TAG, "无法解码图片: $uri")
                return null
            }
            
            // 压缩图片（如果太大）
            val compressedBitmap = compressBitmap(bitmap)
            
            // 转换为Base64
            val outputStream = ByteArrayOutputStream()
            compressedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val byteArray = outputStream.toByteArray()
            
            // 检查大小
            if (byteArray.size > MAX_IMAGE_SIZE) {
                Log.w(TAG, "图片过大: ${byteArray.size} bytes")
                return null
            }
            
            val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            Log.d(TAG, "图片编码成功: ${byteArray.size} bytes -> ${base64.length} chars")
            base64
        } catch (e: Exception) {
            Log.e(TAG, "图片编码失败: $uri", e)
            null
        }
    }
    
    /**
     * 压缩图片（如果尺寸过大）
     */
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return bitmap
        }
        
        val scale = minOf(MAX_DIMENSION.toFloat() / width, MAX_DIMENSION.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}

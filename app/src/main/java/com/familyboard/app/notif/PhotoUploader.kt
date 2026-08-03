package com.familyboard.app.notif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 갤러리/카메라로 얻은 이미지 Uri 를 압축(1MB 이상이면 ~500KB 목표)해 서버에 업로드하고 URL 을 반환.
 */
object PhotoUploader {
    private const val ONE_MB = 1_000_000
    private const val TARGET = 500_000
    private const val MAX_DIM = 1600

    suspend fun compressAndUpload(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bytes = runCatching { readAndCompress(context, uri) }.getOrNull() ?: return@withContext null
        NotifyApi.uploadPhoto(bytes)
    }

    private fun readAndCompress(context: Context, uri: Uri): ByteArray {
        val original = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        if (original.size < ONE_MB) return original // 1MB 미만이면 원본 그대로

        var bmp = BitmapFactory.decodeByteArray(original, 0, original.size)
            ?: return original
        val maxSide = maxOf(bmp.width, bmp.height)
        if (maxSide > MAX_DIM) {
            val scale = MAX_DIM.toFloat() / maxSide
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        }
        var quality = 85
        var out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > TARGET && quality > 40) {
            quality -= 10
            out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        return out.toByteArray()
    }
}

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

        // 원본 크기만 먼저 읽어 inSampleSize 로 다운샘플 디코드(초고해상도 OOM 방지)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        val opts = BitmapFactory.Options().apply {
            var s = 1
            var w = bounds.outWidth; var h = bounds.outHeight
            while (w / 2 >= MAX_DIM || h / 2 >= MAX_DIM) { w /= 2; h /= 2; s *= 2 }
            inSampleSize = s
        }
        val decoded = BitmapFactory.decodeByteArray(original, 0, original.size, opts) ?: return original
        val maxSide = maxOf(decoded.width, decoded.height)
        val bmp: Bitmap = if (maxSide > MAX_DIM) {
            val scale = MAX_DIM.toFloat() / maxSide
            val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            if (scaled !== decoded) decoded.recycle()
            scaled
        } else decoded
        var quality = 85
        var out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > TARGET && quality > 40) {
            quality -= 10
            out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        bmp.recycle()
        return out.toByteArray()
    }
}

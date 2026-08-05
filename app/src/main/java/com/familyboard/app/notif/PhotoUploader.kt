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

    private const val MAX_VIDEO = 60_000_000
    private const val MAX_DIM_HI = 3000
    private const val HI_TARGET = 9_000_000

    /** 재미진 곳용 고화질 업로드: 12MB 이하 원본은 그대로(가장 또렷), 초과 시 최대 3000px·q92로만 살짝 줄임. */
    suspend fun uploadImageHiQ(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val original = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull()
            ?: return@withContext null
        val mime = context.contentResolver.getType(uri).orEmpty()
        val ext = when {
            mime.contains("png") -> "png"; mime.contains("webp") -> "webp"; mime.contains("gif") -> "gif"; else -> "jpg"
        }
        val useOriginal = original.size <= 12_000_000
        val bytes = if (useOriginal) original else (runCatching { compressHiQ(original) }.getOrNull() ?: original)
        if (bytes.size > MAX_VIDEO) return@withContext null
        NotifyApi.uploadFile(bytes, if (useOriginal) ext else "jpg")
    }

    private fun compressHiQ(original: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        val opts = BitmapFactory.Options().apply {
            var s = 1; var w = bounds.outWidth; var h = bounds.outHeight
            while (w / 2 >= MAX_DIM_HI || h / 2 >= MAX_DIM_HI) { w /= 2; h /= 2; s *= 2 }
            inSampleSize = s
        }
        val decoded = BitmapFactory.decodeByteArray(original, 0, original.size, opts) ?: return original
        val maxSide = maxOf(decoded.width, decoded.height)
        val bmp: Bitmap = if (maxSide > MAX_DIM_HI) {
            val scale = MAX_DIM_HI.toFloat() / maxSide
            val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            if (scaled !== decoded) decoded.recycle(); scaled
        } else decoded
        var quality = 92
        var out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        while (out.size() > HI_TARGET && quality > 75) {
            quality -= 7; out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        bmp.recycle()
        return out.toByteArray()
    }

    /** 원본 파일(영상 등)을 그대로 업로드. 60MB 초과 시 null. */
    suspend fun uploadRaw(context: Context, uri: Uri, ext: String): String? = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull()
            ?: return@withContext null
        if (bytes.size > MAX_VIDEO) return@withContext null
        NotifyApi.uploadFile(bytes, ext)
    }

    /** 영상 첫 프레임을 썸네일로 추출·업로드(실패 시 null). */
    suspend fun uploadVideoThumb(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            val r = android.media.MediaMetadataRetriever()
            r.setDataSource(context, uri)
            val bmp = r.getFrameAtTime(0) ?: r.frameAtTime
            r.release()
            if (bmp == null) null else {
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                bmp.recycle()
                out.toByteArray()
            }
        }.getOrNull() ?: return@withContext null
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

package com.familyboard.app.notif

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.familyboard.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 서버 version.json 의 최신 버전 정보 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notes: String,
    val sha256: String = "", // 있으면 다운로드 APK 무결성 검증에 사용
)

/**
 * 인앱 업데이트: 헤르메스 서버의 /apk/version.json 을 조회해 현재 설치 버전보다 높으면 알림.
 * APK 는 /apk/ 에서 다운로드 후 설치 인텐트로 사이드로드.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private val base get() = BuildConfig.NOTIFY_BASE_URL.trimEnd('/')

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (base.isBlank()) return@withContext null
        runCatching {
            val conn = (URL("$base/apk/version.json").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
            }
            val txt = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val o = JSONObject(txt)
            val vc = o.getInt("versionCode")
            if (vc > BuildConfig.VERSION_CODE) {
                UpdateInfo(
                    versionCode = vc,
                    versionName = o.optString("versionName"),
                    url = o.optString("url"),
                    notes = o.optString("notes"),
                    sha256 = o.optString("sha256"),
                )
            } else null
        }.onFailure { Log.w(TAG, "업데이트 확인 실패", it) }.getOrNull()
    }

    /** 설치 버전 비교 없이 서버 version.json 의 최신 릴리스 notes 만 조회(업데이트 요청 알림 문구용). 실패 시 "". */
    suspend fun latestNotes(): String = withContext(Dispatchers.IO) {
        if (base.isBlank()) return@withContext ""
        runCatching {
            val conn = (URL("$base/apk/version.json").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
            }
            val txt = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            JSONObject(txt).optString("notes")
        }.onFailure { Log.w(TAG, "latestNotes 실패", it) }.getOrDefault("")
    }

    /** APK 다운로드 → 캐시에 저장 (성공 시 File). expectedSha256 이 있으면 무결성 검증 후 불일치 시 실패. */
    suspend fun downloadApk(context: Context, url: String, expectedSha256: String = ""): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000; readTimeout = 60000
                }
                val out = File(context.cacheDir, "update.apk")
                conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                conn.disconnect()
                if (expectedSha256.isNotBlank()) {
                    val actual = sha256Of(out)
                    if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) {
                        Log.w(TAG, "APK 해시 불일치 — 설치 중단 (expected=$expectedSha256 actual=$actual)")
                        out.delete()
                        return@runCatching null
                    }
                }
                out
            }.onFailure { Log.w(TAG, "APK 다운로드 실패", it) }.getOrNull()
        }

    private fun sha256Of(file: File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(8192)
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** 설치 인텐트 실행 (사용자가 '알 수 없는 앱 설치 허용' 후 설치) */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

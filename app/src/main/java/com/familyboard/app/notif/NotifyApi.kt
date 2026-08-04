package com.familyboard.app.notif

import android.util.Log
import com.familyboard.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 헤르메스 알림 릴레이 서버 클라이언트.
 *  - register: 이 기기의 FCM 토큰을 멤버와 함께 등록
 *  - notify: 특정 멤버들에게 등록 알림 발송 요청 (서버가 actor 제외 후 FCM 발송)
 * 서버 주소/시크릿은 local.properties → BuildConfig 로 주입.
 */
object NotifyApi {
    private const val TAG = "NotifyApi"
    private val base get() = BuildConfig.NOTIFY_BASE_URL.trimEnd('/')
    private val secret get() = BuildConfig.NOTIFY_SECRET

    private fun enabled() = base.isNotBlank()

    suspend fun register(memberId: String, token: String) {
        if (!enabled()) return
        val body = JSONObject().put("memberId", memberId).put("token", token)
        post("/register", body)
    }

    suspend fun notify(actor: String, targets: List<String>, title: String, body: String) {
        notifyData(actor, targets, title, body, emptyMap())
    }

    /** title/body 외에 커스텀 data 필드를 함께 전송(긴급 연락/위치 공유 등). */
    suspend fun notifyData(
        actor: String, targets: List<String>, title: String, body: String, data: Map<String, String>,
    ) {
        if (!enabled() || targets.isEmpty()) return
        val json = JSONObject()
            .put("actor", actor)
            .put("targets", JSONArray(targets))
            .put("title", title)
            .put("body", body)
        if (data.isNotEmpty()) {
            val d = JSONObject()
            data.forEach { (k, v) -> d.put(k, v) }
            json.put("data", d)
        }
        post("/notify", json)
    }

    /** 사진 바이트 업로드 → 공개 URL 반환(실패 시 null) */
    suspend fun uploadPhoto(bytes: ByteArray): String? {
        if (!enabled()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("$base/upload").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/octet-stream")
                    if (secret.isNotBlank()) setRequestProperty("X-FB-Key", secret)
                }
                conn.outputStream.use { it.write(bytes) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code in 200..299) JSONObject(text).optString("url").ifBlank { null } else null
            }.onFailure { Log.w(TAG, "uploadPhoto 실패", it) }.getOrNull()
        }
    }

    private suspend fun post(path: String, json: JSONObject) = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (secret.isNotBlank()) setRequestProperty("X-FB-Key", secret)
            }
            conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            Log.i(TAG, "POST $path -> $code")
        }.onFailure { Log.w(TAG, "POST $path 실패", it) }
    }
}

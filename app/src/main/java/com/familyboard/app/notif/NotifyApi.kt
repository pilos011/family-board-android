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
/** 네이버 플레이스 파싱 결과 */
data class PlaceInfo(
    val name: String = "",
    val category: String = "",
    val address: String = "",
    val score: Double? = null,
    val reviews: Int? = null,
    val hours: String = "",
    val image: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
)

/** 범용 링크(유튜브/웹) 미리보기 파싱 결과 */
data class LinkInfo(val title: String = "", val image: String = "", val url: String = "")

/** 발굴 추천 결과 1건(Google 실제 장소·평점). naverName=네이버 검색용 정리된 상호. */
data class Recommendation(
    val name: String, val naverName: String, val category: String, val address: String, val dist: Double?,
    val rating: Double?, val ratingCount: Int, val reason: String,
    val lat: Double?, val lng: Double?,
)

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

    /** 네이버 플레이스 공유 링크에서 상호/종목/영업시간/주소 파싱(서버 위임). 실패 시 null. */
    suspend fun parsePlace(url: String): PlaceInfo? {
        if (!enabled() || url.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("$base/place/parse").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (secret.isNotBlank()) setRequestProperty("X-FB-Key", secret)
                }
                conn.outputStream.use { it.write(JSONObject().put("url", url).toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) return@runCatching null
                val o = JSONObject(text)
                PlaceInfo(
                    name = o.optString("name"),
                    category = o.optString("category"),
                    address = o.optString("address"),
                    score = if (o.isNull("score")) null else o.optDouble("score"),
                    reviews = if (o.isNull("reviews")) null else o.optInt("reviews"),
                    hours = o.optString("hours"),
                    image = o.optString("image"),
                    lat = if (o.isNull("lat")) null else o.optDouble("lat"),
                    lng = if (o.isNull("lng")) null else o.optDouble("lng"),
                )
            }.onFailure { Log.w(TAG, "parsePlace 실패", it) }.getOrNull()
        }
    }

    /** 유튜브/웹 링크에서 제목·썸네일(og) 파싱(서버 위임). 실패 시 null. */
    suspend fun parseLink(url: String): LinkInfo? {
        if (!enabled() || url.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("$base/link/parse").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (secret.isNotBlank()) setRequestProperty("X-FB-Key", secret)
                }
                conn.outputStream.use { it.write(JSONObject().put("url", url).toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) return@runCatching null
                val o = JSONObject(text)
                LinkInfo(title = o.optString("title"), image = o.optString("image"), url = o.optString("url").ifBlank { url })
            }.onFailure { Log.w(TAG, "parseLink 실패", it) }.getOrNull()
        }
    }

    /** 카카오 검색+Groq 선별로 '놓친 장소' 2~3곳 발굴. lat/lng 는 거리 계산·바이어스용(선택). 실패 시 빈 목록. */
    suspend fun recommend(
        board: String, category: String, region: String, savedNames: List<String>, lat: Double?, lng: Double?,
    ): List<Recommendation> {
        if (!enabled()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("board", board).put("category", category).put("region", region)
                    .put("savedNames", JSONArray(savedNames))
                if (lat != null && lng != null) { body.put("x", lng); body.put("y", lat) } // 카카오: x=경도, y=위도
                val conn = (URL("$base/recommend").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 10000; readTimeout = 20000; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (secret.isNotBlank()) setRequestProperty("X-FB-Key", secret)
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) return@runCatching emptyList<Recommendation>()
                val arr = JSONObject(text).optJSONArray("items") ?: return@runCatching emptyList<Recommendation>()
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Recommendation(
                        name = o.optString("name"), naverName = o.optString("naverName").ifBlank { o.optString("name") },
                        category = o.optString("category"),
                        address = o.optString("address"),
                        dist = if (o.isNull("dist")) null else o.optDouble("dist"),
                        rating = if (o.isNull("rating")) null else o.optDouble("rating"),
                        ratingCount = o.optInt("ratingCount"),
                        reason = o.optString("reason"),
                        lat = if (o.isNull("lat")) null else o.optDouble("lat"),
                        lng = if (o.isNull("lng")) null else o.optDouble("lng"),
                    )
                }
            }.onFailure { Log.w(TAG, "recommend 실패", it) }.getOrDefault(emptyList())
        }
    }

    /** 대용량 파일(영상 등) 업로드 → 공개 URL 반환. ext 확장자로 저장(예: "mp4"). 실패 시 null. */
    suspend fun uploadFile(bytes: ByteArray, ext: String): String? {
        if (!enabled()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("$base/uploadfile").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 60000
                    doOutput = true
                    setFixedLengthStreamingMode(bytes.size)
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setRequestProperty("X-FB-Ext", ext)
                    if (secret.isNotBlank()) setRequestProperty("X-FB-Key", secret)
                }
                conn.outputStream.use { it.write(bytes) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code in 200..299) JSONObject(text).optString("url").ifBlank { null } else null
            }.onFailure { Log.w(TAG, "uploadFile 실패", it) }.getOrNull()
        }
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

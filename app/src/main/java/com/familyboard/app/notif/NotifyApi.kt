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

    /** 설치 이력(FCM 토큰 등록된) member id 목록. 관리자 접속현황에서 '설치 안 한 사람' 제외용. */
    suspend fun registeredMembers(): List<String> {
        if (!enabled()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("$base/tokens/members").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 10000; readTimeout = 10000
                    if (secret.isNotBlank()) setRequestProperty("X-FB-Key", secret)
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) return@runCatching emptyList<String>()
                val arr = JSONObject(text).optJSONArray("members") ?: return@runCatching emptyList<String>()
                (0 until arr.length()).map { arr.getString(it) }
            }.onFailure { Log.w(TAG, "registeredMembers 실패", it) }.getOrDefault(emptyList())
        }
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

    data class GPlace(val name: String, val lat: Double, val lng: Double, val link: String)

    /** 구글 지도 공유 링크에서 장소명·좌표 파싱(서버 위임, 리다이렉트 추적). 실패 시 null. 여행 위시리스트용. */
    suspend fun parseGooglePlace(url: String): GPlace? {
        if (!enabled() || url.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("$base/gplace/parse").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 10000; readTimeout = 15000; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (secret.isNotBlank()) setRequestProperty("X-FB-Key", secret)
                }
                conn.outputStream.use { it.write(JSONObject().put("url", url).toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) return@runCatching null
                val o = JSONObject(text)
                GPlace(
                    name = o.optString("name"),
                    lat = if (o.isNull("lat")) 0.0 else o.optDouble("lat"),
                    lng = if (o.isNull("lng")) 0.0 else o.optDouble("lng"),
                    link = o.optString("link", url),
                )
            }.onFailure { Log.w(TAG, "parseGooglePlace 실패", it) }.getOrNull()
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
        radius: Int? = null, limit: Int? = null,
    ): List<Recommendation> {
        if (!enabled()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("board", board).put("category", category).put("region", region)
                    .put("savedNames", JSONArray(savedNames))
                if (lat != null && lng != null) { body.put("x", lng); body.put("y", lat) } // x=경도, y=위도
                if (radius != null) body.put("radius", radius) // '근처' 모드: 반경(m)
                if (limit != null) body.put("limit", limit) // 반환 개수(위젯=10곳)
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

    /** 코코달인 product_id 목록 → (id,상품명) 목록. 서버(/coco/names)가 코코달인 likeList로 이름 파싱. */
    suspend fun cocoNames(ids: List<String>): List<Pair<String, String>> {
        if (!enabled() || ids.isEmpty()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("ids", JSONArray(ids))
                val conn = (URL("$base/coco/names").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 10000; readTimeout = 15000; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    if (secret.isNotBlank()) setRequestProperty("X-FB-Key", secret)
                }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) return@runCatching emptyList<Pair<String, String>>()
                val arr = JSONObject(text).optJSONArray("items") ?: return@runCatching emptyList<Pair<String, String>>()
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val nm = o.optString("name")
                    if (nm.isBlank()) null else o.optString("id") to nm
                }
            }.onFailure { Log.w(TAG, "cocoNames 실패", it) }.getOrDefault(emptyList())
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

    // ─────────── 가족 사진첩(album/myalbum) — Postgres(notify) REST 클라이언트 ───────────
    data class AlbumPage(val items: List<com.familyboard.app.data.model.ListItem>, val nextTakenAt: Long?, val nextId: String?)

    private fun albumItemFrom(o: JSONObject): com.familyboard.app.data.model.ListItem {
        val url = o.optString("url").ifBlank { o.optJSONArray("photoUrls")?.optString(0).orEmpty() }
        val likesArr = o.optJSONArray("likes")
        val likes = if (likesArr != null) (0 until likesArr.length()).map { likesArr.optString(it) } else emptyList()
        val cArr = o.optJSONArray("comments") ?: o.optJSONArray("progress")
        val comments = if (cArr != null) (0 until cArr.length()).map {
            val c = cArr.optJSONObject(it) ?: JSONObject()
            com.familyboard.app.data.model.ProgressNote(c.optString("text"), c.optString("by"), c.optString("dateIso"))
        } else emptyList()
        return com.familyboard.app.data.model.ListItem(
            id = o.optString("id"), board = o.optString("board"),
            photoUrls = if (url.isNotBlank()) listOf(url) else emptyList(),
            takenAt = o.optLong("takenAt"), dateIso = o.optString("dateIso"),
            likes = likes, progress = comments, rotation = o.optInt("rotation"),
            lat = o.optDouble("lat", 0.0), lng = o.optDouble("lng", 0.0), address = o.optString("address"),
            createdBy = o.optString("createdBy"), createdAt = o.optLong("createdAt"),
            text = o.optString("caption").ifBlank { o.optString("text") },
            fileName = o.optString("fileName"),
        )
    }

    // HttpURLConnection: GET/POST/DELETE 만(PATCH 미지원 → 부분수정은 POST /album/:id/update).
    private fun albumHttp(method: String, path: String, body: JSONObject?): String? {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10000; readTimeout = 20000
            if (secret.isNotBlank()) setRequestProperty("X-FB-Key", secret)
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }
        conn.disconnect()
        return if (code in 200..299) text else null
    }

    suspend fun albumList(board: String, limit: Int, beforeTakenAt: Long?, beforeId: String?, createdBy: String?): AlbumPage? =
        withContext(Dispatchers.IO) {
            if (!enabled()) return@withContext null
            runCatching {
                val sb = StringBuilder("/album?board=$board&limit=$limit")
                if (beforeTakenAt != null) sb.append("&beforeTakenAt=$beforeTakenAt&beforeId=")
                    .append(java.net.URLEncoder.encode(beforeId.orEmpty(), "UTF-8"))
                if (!createdBy.isNullOrBlank()) sb.append("&createdBy=").append(java.net.URLEncoder.encode(createdBy, "UTF-8"))
                val text = albumHttp("GET", sb.toString(), null) ?: return@runCatching null
                val o = JSONObject(text)
                val arr = o.optJSONArray("items") ?: org.json.JSONArray()
                val items = (0 until arr.length()).map { albumItemFrom(arr.getJSONObject(it)) }
                val next = o.optJSONObject("next")
                AlbumPage(items, if (next != null) next.optLong("beforeTakenAt") else null, next?.optString("beforeId"))
            }.onFailure { Log.w(TAG, "albumList 실패", it) }.getOrNull()
        }

    suspend fun albumAdd(
        board: String, url: String, takenAt: Long, dateIso: String, createdBy: String,
        lat: Double, lng: Double, address: String, fileName: String = "",
    ): com.familyboard.app.data.model.ListItem? = withContext(Dispatchers.IO) {
        if (!enabled()) return@withContext null
        runCatching {
            val body = JSONObject().put("board", board).put("url", url).put("takenAt", takenAt)
                .put("dateIso", dateIso).put("createdBy", createdBy).put("lat", lat).put("lng", lng).put("address", address)
                .put("fileName", fileName) // 원본 파일명(백업·EXIF 없을 때 시각 보조)
            albumHttp("POST", "/album", body)?.let { albumItemFrom(JSONObject(it)) }
        }.onFailure { Log.w(TAG, "albumAdd 실패", it) }.getOrNull()
    }

    suspend fun albumLike(id: String, memberId: String) = albumMutate("/album/$id/like", JSONObject().put("memberId", memberId))
    suspend fun albumComment(id: String, text: String, by: String) = albumMutate("/album/$id/comment", JSONObject().put("text", text).put("by", by))
    // 댓글 삭제/수정은 인덱스 기준(서버가 현재 상태에서 직접 조작 → 타인 댓글 유실 방지).
    suspend fun albumCommentDelete(id: String, index: Int) = albumMutate("/album/$id/comment/delete", JSONObject().put("index", index))
    suspend fun albumCommentEdit(id: String, index: Int, text: String) = albumMutate("/album/$id/comment/edit", JSONObject().put("index", index).put("text", text))
    suspend fun albumUpdate(id: String, fields: JSONObject) = albumMutate("/album/$id/update", fields)

    private suspend fun albumMutate(path: String, body: JSONObject): com.familyboard.app.data.model.ListItem? =
        withContext(Dispatchers.IO) {
            if (!enabled()) return@withContext null
            runCatching { albumHttp("POST", path, body)?.let { albumItemFrom(JSONObject(it)) } }
                .onFailure { Log.w(TAG, "albumMutate 실패 $path", it) }.getOrNull()
        }

    suspend fun albumDelete(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!enabled()) return@withContext false
        runCatching { albumHttp("DELETE", "/album/$id", null) != null }.getOrDefault(false)
    }

    suspend fun albumMemories(board: String, month: Int, day: Int): List<com.familyboard.app.data.model.ListItem> =
        withContext(Dispatchers.IO) {
            if (!enabled()) return@withContext emptyList()
            runCatching {
                val text = albumHttp("GET", "/album/memories?board=$board&month=$month&day=$day", null) ?: return@runCatching emptyList()
                val arr = JSONObject(text).optJSONArray("items") ?: org.json.JSONArray()
                (0 until arr.length()).map { albumItemFrom(arr.getJSONObject(it)) }
            }.onFailure { Log.w(TAG, "albumMemories 실패", it) }.getOrDefault(emptyList())
        }

    suspend fun albumCount(board: String, createdBy: String?): Int = withContext(Dispatchers.IO) {
        if (!enabled()) return@withContext 0
        runCatching {
            val q = "/album/count?board=$board" +
                (if (!createdBy.isNullOrBlank()) "&createdBy=" + java.net.URLEncoder.encode(createdBy, "UTF-8") else "")
            albumHttp("GET", q, null)?.let { JSONObject(it).optInt("count") } ?: 0
        }.getOrDefault(0)
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

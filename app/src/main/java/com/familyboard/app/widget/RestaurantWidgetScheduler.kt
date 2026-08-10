package com.familyboard.app.widget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.location.LocationManager
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.familyboard.app.MainActivity
import com.familyboard.app.R
import com.familyboard.app.data.model.PlaceBoards
import com.familyboard.app.notif.Recommendation
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalTime
import java.util.Calendar

/**
 * 맛집 추천 위젯의 스케줄·위치·사진·렌더링.
 *
 * 핵심 설계(폭주 방지):
 *  - **10분마다(09~23시) TICK 알람으로 위치 확인** → 시군구 변경(또는 1시간 경과/무캐시)이면 WorkManager 로 20곳 재검색+사진 캐시.
 *  - **화면 회전은 ROTATE 알람(약 10초)** → 캐시에서 순차(0→n-1→0)로 그려 넣기만(네트워크·WorkManager 없음).
 *  - **onUpdate 에서는 절대 fetch 를 enqueue 하지 않음.** (WorkManager 가 내부 컴포넌트를 토글→PACKAGE_CHANGED
 *    →APPWIDGET_UPDATE→onUpdate→enqueue… 무한 루프가 났던 원인. 초기 fetch 는 onEnabled 에서 1회만.)
 */
object RestaurantWidgetScheduler {

    private const val PREFS = "restaurant_widget"
    private const val KEY_LIST = "list"
    private const val KEY_LAST = "lastIndex"
    private const val KEY_CHECK_LAT = "checkLat" // 직전 위치 확인 좌표(이동거리 계산용)
    private const val KEY_CHECK_LNG = "checkLng"
    private const val KEY_SIGUNGU = "siGunGu"    // 직전 재검색 시군구(변경 판정용)
    private const val KEY_FETCH_AT = "fetchAt"   // 마지막 재검색 시각(시간당 폴백용)
    private const val TICK_REQ = 7301
    private const val ROTATE_REQ = 7305
    private const val MAIN_REQ = 7303
    private const val NAVER_REQ = 7304
    private const val PHOTO_MAX_W = 300
    private const val ROTATE_MS = 10_000L
    private const val TICK_MS = 10 * 60_000L      // 위치 확인 주기(10분)
    private const val MOVE_THRESHOLD_M = 1000.0   // 이 이상 이동해야 시군구 확인(불필요 지오코딩 방지)
    const val FALLBACK_MS = 60 * 60_000L          // 이동 없어도 1시간마다 재검색(평점·사진 신선도)

    private const val HOME_LAT = 37.6437   // 폴백: 백석동(홈)
    private const val HOME_LNG = 126.7896
    private const val HOUR_START = 9        // 갱신 시간대 09~23시
    private const val HOUR_END = 23
    private const val TITLE_BASE = "🍽 맛집 찾기"

    data class Pick(
        val name: String, val lat: Double, val lng: Double, val addr: String,
        val rating: Double, val ratingCount: Int, val distMeters: Int, val category: String,
    )
    data class Loc(val lat: Double, val lng: Double, val real: Boolean)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private fun am(ctx: Context) = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun inWindow(): Boolean = LocalTime.now().hour in HOUR_START..HOUR_END

    // ─────────── 알람: 시간당 TICK(fetch) ───────────
    private fun tickPending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, TICK_REQ, Intent(ctx, RestaurantWidget::class.java).setAction(RestaurantWidget.ACTION_TICK), piFlags,
    )

    /** 다음 위치 확인(TICK) 예약: 창(09~23시) 안이면 +10분, 밖이면 다음 09:00. Doze 친화 inexact. */
    fun scheduleNextTick(ctx: Context) {
        val a = am(ctx) ?: return
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now + TICK_MS }
        if (cal.get(Calendar.HOUR_OF_DAY) !in HOUR_START..HOUR_END) { // 창 밖이면 다음 09:00
            cal.timeInMillis = now
            cal.set(Calendar.HOUR_OF_DAY, HOUR_START); cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 5); cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        runCatching { a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, tickPending(ctx)) }
    }

    /**
     * 10분 주기 위치 확인. 다음 틱 재예약 후, 창 안 & 실제 위치 있음일 때만:
     * 무캐시 / 1시간 경과 / 1km 이상 이동 이면 재검색 작업 enqueue.
     * (실제 시군구 변경 판정·재검색 여부는 Worker 가 지오코딩으로 최종 결정 → 같은 시군구면 재검색 안 함)
     */
    fun onTick(ctx: Context) {
        scheduleNextTick(ctx)
        if (!inWindow()) return
        val loc = bestLocation(ctx)
        if (!loc.real) return // 실제 위치 없으면(폴백뿐) 이번 주기 스킵 — 억지로 GPS 안 켬
        val p = prefs(ctx)
        val la = p.getFloat(KEY_CHECK_LAT, 0f).toDouble()
        val ln = p.getFloat(KEY_CHECK_LNG, 0f).toDouble()
        val moved = if (la == 0.0 && ln == 0.0) Double.MAX_VALUE else distanceMeters(la, ln, loc.lat, loc.lng)
        // '이동 시에만' 재검색: 시간 기반(hourly) 트리거 제거 → 무캐시(최초)이거나 1km 이상 이동했을 때만 검사.
        // (Google API 과다호출 절감. 실제 재검색 여부는 Worker 가 시군구 변경으로 최종 결정)
        if (count(ctx) <= 0 || moved >= MOVE_THRESHOLD_M) {
            p.edit().putFloat(KEY_CHECK_LAT, loc.lat.toFloat()).putFloat(KEY_CHECK_LNG, loc.lng.toFloat()).apply()
            enqueueFetch(ctx)
        }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    // Worker 가 시군구 변경/시간 경과 판정에 쓰는 상태
    fun lastSiGunGu(ctx: Context): String? = prefs(ctx).getString(KEY_SIGUNGU, null)
    fun setSiGunGu(ctx: Context, s: String) { prefs(ctx).edit().putString(KEY_SIGUNGU, s).apply() }
    fun lastFetchAt(ctx: Context): Long = prefs(ctx).getLong(KEY_FETCH_AT, 0L)
    fun markFetched(ctx: Context) { prefs(ctx).edit().putLong(KEY_FETCH_AT, System.currentTimeMillis()).apply() }

    // ─────────── 알람: 약 10초 ROTATE(화면 회전, 네트워크 없음) ───────────
    private fun rotatePending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, ROTATE_REQ, Intent(ctx, RestaurantWidget::class.java).setAction(RestaurantWidget.ACTION_ROTATE), piFlags,
    )

    private fun hasWidgets(ctx: Context): Boolean {
        val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, RestaurantWidget::class.java))
        return ids != null && ids.isNotEmpty()
    }

    /** 캐시가 있고 위젯이 있을 때만 10초 뒤 회전 예약. RTC(non-wakeup)라 화면 꺼지면 자연히 멈춤(절전). */
    fun scheduleRotate(ctx: Context) {
        if (count(ctx) <= 0 || !hasWidgets(ctx)) return // 위젯 없으면 회전 알람 재예약 안 함(고아 알람 방지)
        val a = am(ctx) ?: return
        runCatching { a.set(AlarmManager.RTC, System.currentTimeMillis() + ROTATE_MS, rotatePending(ctx)) }
    }

    fun cancel(ctx: Context) {
        am(ctx)?.let { it.cancel(tickPending(ctx)); it.cancel(rotatePending(ctx)) }
        runCatching { WorkManager.getInstance(ctx).cancelUniqueWork("restaurant_widget") }
    }

    /** 시간당 1회 데이터 조회(WorkManager). onUpdate 에서 호출 금지! (onEnabled/TICK/수동만) */
    fun enqueueFetch(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<RestaurantUpdateWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("restaurant_widget", ExistingWorkPolicy.REPLACE, req)
    }

    // ─────────── 위치 ───────────
    fun bestLocation(ctx: Context): Loc {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val best = lm?.let {
                listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                    .mapNotNull { p -> runCatching { it.getLastKnownLocation(p) }.getOrNull() }
                    .maxByOrNull { l -> l.time }
            }
            if (best != null) return Loc(best.latitude, best.longitude, true)
        }
        return Loc(HOME_LAT, HOME_LNG, false)
    }

    // ─────────── 캐시(목록 + 사진 파일) ───────────
    private fun photoFile(ctx: Context, i: Int) = File(ctx.filesDir, "widget_photo_$i.jpg")

    fun clearPhotos(ctx: Context) { for (i in 0 until 24) runCatching { photoFile(ctx, i).delete() } }

    /** 추천 목록 저장(순서 유지 = 사진 파일 인덱스와 일치). */
    fun saveList(ctx: Context, recs: List<Recommendation>) {
        val arr = JSONArray()
        recs.forEach { r ->
            arr.put(
                JSONObject()
                    .put("name", r.naverName.ifBlank { r.name })
                    .put("lat", r.lat ?: 0.0).put("lng", r.lng ?: 0.0)
                    .put("addr", r.address).put("category", r.category)
                    .put("rating", r.rating ?: 0.0).put("ratingCount", r.ratingCount)
                    .put("distM", r.dist?.let { (it * 1000).toInt() } ?: -1),
            )
        }
        prefs(ctx).edit().putString(KEY_LIST, arr.toString()).putInt(KEY_LAST, -1).apply()
    }

    private fun loadList(ctx: Context): List<Pick> = runCatching {
        val s = prefs(ctx).getString(KEY_LIST, null) ?: return emptyList()
        val arr = JSONArray(s)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Pick(
                name = o.optString("name"), lat = o.optDouble("lat", 0.0), lng = o.optDouble("lng", 0.0),
                addr = o.optString("addr"), category = o.optString("category"),
                rating = o.optDouble("rating", 0.0), ratingCount = o.optInt("ratingCount"),
                distMeters = o.optInt("distM", -1),
            )
        }
    }.getOrDefault(emptyList())

    fun count(ctx: Context): Int = loadList(ctx).size

    // ─────────── 사진 ───────────
    /** URL 다운로드 → 축소 → widget_photo_<i>.jpg 저장. 성공 true. */
    fun cachePhoto(ctx: Context, url: String, i: Int, maxW: Int = PHOTO_MAX_W): Boolean = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 12000; instanceFollowRedirects = true
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxW * 2) sample *= 2
        val opt = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt) ?: return@runCatching false
        if (bmp.width > maxW) {
            val h = (bmp.height.toFloat() * maxW / bmp.width).toInt().coerceAtLeast(1)
            bmp = Bitmap.createScaledBitmap(bmp, maxW, h, true)
        }
        photoFile(ctx, i).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    }.getOrDefault(false)

    private fun loadPhoto(ctx: Context, i: Int): Bitmap? = runCatching {
        val f = photoFile(ctx, i)
        if (!f.exists()) return null
        BitmapFactory.decodeFile(f.absolutePath)?.let { roundBitmap(it, 24f) }
    }.getOrNull()

    private fun roundBitmap(src: Bitmap, radiusPx: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    // ─────────── 클릭 인텐트 ───────────
    private fun mainPending(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WIDGET_NAV, PlaceBoards.RESTAURANT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, MAIN_REQ, i, piFlags)
    }

    private fun rotateButtonPending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, ROTATE_REQ + 1, Intent(ctx, RestaurantWidget::class.java).setAction(RestaurantWidget.ACTION_ROTATE), piFlags,
    )

    /** 네이버플레이스에서 해당 가게 열기(앱 AI 추천과 동일: "상호 + 시/군/구" 검색, 길찾기 아님). */
    private fun naverPending(ctx: Context, pick: Pick): PendingIntent {
        val siGunGu = regionOf(pick.addr).split(" ").getOrNull(1).orEmpty()
        val query = listOf(pick.name, siGunGu).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (query.isBlank()) return mainPending(ctx)
        val url = "https://map.naver.com/p/search/" + URLEncoder.encode(query, "UTF-8")
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, NAVER_REQ, i, piFlags)
    }

    // 주소 → "시도 시/군/구"(앱 PlaceListScreen.regionOf 와 동일). 네이버 검색어의 지역 부분.
    private val SIDO_SHORT = mapOf(
        "서울특별시" to "서울", "부산광역시" to "부산", "대구광역시" to "대구", "인천광역시" to "인천",
        "광주광역시" to "광주", "대전광역시" to "대전", "울산광역시" to "울산", "세종특별자치시" to "세종",
        "경기도" to "경기", "강원도" to "강원", "강원특별자치도" to "강원", "충청북도" to "충북", "충청남도" to "충남",
        "전라북도" to "전북", "전북특별자치도" to "전북", "전라남도" to "전남", "경상북도" to "경북", "경상남도" to "경남",
        "제주특별자치도" to "제주", "제주도" to "제주",
    )
    private fun regionOf(address: String): String {
        val toks = address.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (toks.isEmpty()) return ""
        val sido = SIDO_SHORT[toks[0]] ?: SIDO_SHORT.values.firstOrNull { toks[0].startsWith(it) } ?: toks[0].take(2)
        val gu = toks.getOrNull(1)?.takeIf { it.endsWith("시") || it.endsWith("군") || it.endsWith("구") } ?: ""
        return listOf(sido, gu).filter { it.isNotBlank() }.joinToString(" ")
    }

    // ─────────── 렌더링 ───────────
    private fun fmtDist(m: Int): String =
        if (m < 0) "" else if (m < 1000) "${m}m" else String.format("%.1fkm", m / 1000.0)

    private fun metaLine(p: Pick): String {
        val parts = ArrayList<String>()
        if (p.rating > 0) parts.add("⭐${p.rating}")
        if (p.ratingCount > 0) parts.add("리뷰 ${p.ratingCount}")
        fmtDist(p.distMeters).takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString(" · ")
    }

    /** 순차 회전: 다음 인덱스(0→1→…→n-1→0)로 진행. 비어 있으면 안내. */
    fun renderNext(ctx: Context) {
        val list = loadList(ctx)
        if (list.isEmpty()) { renderEmpty(ctx, "불러오는 중…"); return }
        val i = (prefs(ctx).getInt(KEY_LAST, -1) + 1) % list.size
        prefs(ctx).edit().putInt(KEY_LAST, i).apply()
        renderCard(ctx, list[i], loadPhoto(ctx, i))
    }

    /** 현재 인덱스 유지하며 다시 그림(진행 안 함). onUpdate/재부팅용. */
    fun renderCurrent(ctx: Context) {
        val list = loadList(ctx)
        if (list.isEmpty()) { renderEmpty(ctx, "불러오는 중…"); return }
        var i = prefs(ctx).getInt(KEY_LAST, -1)
        if (i < 0 || i >= list.size) i = 0
        prefs(ctx).edit().putInt(KEY_LAST, i).apply()
        renderCard(ctx, list[i], loadPhoto(ctx, i))
    }

    fun renderEmpty(ctx: Context, status: String) {
        val rv = baseViews(ctx)
        rv.setTextViewText(R.id.widget_title, TITLE_BASE)
        rv.setTextViewText(R.id.widget_meta, status)
        rv.setTextViewText(R.id.widget_addr, "")
        rv.setTextViewText(R.id.widget_desc, "탭해서 맛집 화면 열기")
        rv.setOnClickPendingIntent(R.id.widget_card, mainPending(ctx))
        push(ctx, rv)
    }

    private fun renderCard(ctx: Context, pick: Pick, bmp: Bitmap?) {
        val rv = baseViews(ctx)
        rv.setTextViewText(R.id.widget_title, "$TITLE_BASE → ${pick.name}") // 가게명은 타이틀 줄에
        rv.setTextViewText(R.id.widget_meta, metaLine(pick))
        rv.setTextViewText(R.id.widget_addr, if (pick.addr.isBlank()) "" else "📍 ${pick.addr}")
        rv.setTextViewText(R.id.widget_desc, pick.category)
        if (bmp != null) rv.setImageViewBitmap(R.id.widget_photo, bmp)
        rv.setOnClickPendingIntent(R.id.widget_card, naverPending(ctx, pick))
        push(ctx, rv)
    }

    private fun baseViews(ctx: Context): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_restaurant)
        rv.setOnClickPendingIntent(R.id.widget_top, mainPending(ctx))
        rv.setOnClickPendingIntent(R.id.widget_refresh, rotateButtonPending(ctx))
        return rv
    }

    private fun push(ctx: Context, rv: RemoteViews) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, RestaurantWidget::class.java))
        if (ids != null && ids.isNotEmpty()) mgr.updateAppWidget(ids, rv)
    }
}

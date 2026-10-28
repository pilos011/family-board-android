package com.familyboard.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.familyboard.app.BuildConfig
import com.familyboard.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * 주차 위치 1x1 위젯 — 아파트 RTLS 서버는 **Home Assistant 가 대표로 조회**하고 위젯은 HA 센서만 읽음(v1.0.155).
 * (예전엔 가족 폰마다 RTLS 서버를 30분마다 직접 조회 → 폰 수만큼 요청이 늘어남.)
 * - HA: 자동화 '주차위치 업데이트'가 06:00~22:30 매시 :00·:30 에 RTLS 1회 조회 → sensor.moning_jucaceung / geuraenjeo_jucaceung.
 * - **자동 갱신**: 06~22시 매시 :02·:32 에 HA 센서를 읽음(무음, RTLS 호출 없음).
 *   (안드로이드는 '위젯이 보이는 순간'을 알려주지 않아 주기 갱신으로 대체.)
 * - 탭 → HA 에 즉시 재조회를 요청(homeassistant.update_entity, 폰당 [TAP_REFRESH_GAP_MS] 에 1번) 후 센서를 읽어
 *   "B2 / B4"(위=모닝, 아래=그랜저)로 표시. HA 센서 값 '출차' 면 "출차".
 */
class ParkingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        // 기회성 조회: 시스템/런처가 위젯을 다시 그릴 때(홈 복귀·재부팅·리사이즈 등) 최근 조회가 오래됐으면 HA 를 1회 읽는다.
        // 추가 권한·새 알람 없음(어차피 불리는 콜백에 얹음), 쓰로틀로 과조회 방지, 컴포넌트 토글 안 해 루프 아님.
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_TS, 0L)
        if (last <= 0L || System.currentTimeMillis() - last >= MIN_AUTO_GAP_MS) fetchAsync(context, false)
        else render(context)
        scheduleNextTick(context) // 재부팅/앱 업데이트 후 자동 갱신 재예약
    }

    override fun onEnabled(context: Context) {
        scheduleNextTick(context)
        fetchAsync(context, false) // 최초 1회 조회(창 무관)
    }

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickPending(context)); am.cancel(revertPending(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // APPWIDGET_UPDATE → onUpdate / onEnabled / onDisabled
        when (intent.action) {
            ACTION_REFRESH -> fetchAsync(context, true)                       // 탭: 진행표시 O + HA 재조회 요청
            ACTION_TICK -> { scheduleNextTick(context); if (inWindow()) fetchAsync(context, false) } // 자동: 무음, HA 읽기만
            ACTION_REVERT -> render(context)                                  // 신선도 만료 재렌더(→ '확인')
        }
    }

    /** 조회 → 저장 → 렌더. showProgress=true(탭)면 '조회 중/실패'를 보여주고 HA 에 재조회도 요청, 자동 갱신은 무음·읽기만. */
    private fun fetchAsync(ctx: Context, showProgress: Boolean) {
        if (widgetIds(ctx).isEmpty()) return
        if (showProgress) applyRows(ctx, "조회", "중…")
        val pending = goAsync()
        Thread {
            try {
                if (showProgress) requestHaRefresh(ctx)
                val res = fetch()
                if (res != null) {
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(KEY_M, res.first).putString(KEY_G, res.second)
                        .putLong(KEY_TS, System.currentTimeMillis()).apply()
                    render(ctx); scheduleRevert(ctx)
                } else {
                    if (showProgress) applyRows(ctx, "조회", "실패") else render(ctx)
                }
            } finally { pending.finish() }
        }.start()
    }

    companion object {
        private const val PREFS = "parking_widget"
        private const val KEY_M = "floor_morning"
        private const val KEY_G = "floor_grandeur"
        private const val KEY_TS = "fetched_at"
        private const val KEY_REFRESH_TS = "ha_refresh_at"
        private const val REQ_TAP = 7501
        private const val REQ_REVERT = 7502
        private const val REQ_TICK = 7503
        private const val WINDOW_MS = 35 * 60 * 1000L   // 이 시간 내 조회값이면 층 표시(30분 자동갱신보다 약간 길게)
        private const val TICK_MINUTE = 2                // 자동 갱신 = 매시 :02·:32 (HA 가 :00·:30 에 조회한 직후)
        private const val HOUR_START = 6                 // 자동 갱신 시간대 06~22시 (HA 조회 06:00~22:30 과 맞춤)
        private const val HOUR_END = 22
        private const val TAP_REFRESH_GAP_MS = 60 * 1000L // 탭 연타해도 HA 재조회 요청은 폰당 1분에 1번
        private const val MIN_AUTO_GAP_MS = 2 * 60 * 1000L // onUpdate 기회성 조회 쓰로틀(최근 2분 내 조회면 skip)

        private const val ACTION_REFRESH = "com.familyboard.app.widget.PARKING_REFRESH"
        private const val ACTION_REVERT = "com.familyboard.app.widget.PARKING_REVERT"
        private const val ACTION_TICK = "com.familyboard.app.widget.PARKING_TICK"

        // HA rest 센서(configuration.yaml rest:, 104동 3406호 RTLS 를 차량 시리얼로 파싱). 값 = 층(B2 등) 또는 '출차'.
        private const val ENTITY_MORNING = "sensor.moning_jucaceung"      // 모닝 주차층 (시리얼 A006524)
        private const val ENTITY_GRANDEUR = "sensor.geuraenjeo_jucaceung" // 그랜저 주차층 (시리얼 A033882)
        private val haBase get() = BuildConfig.HA_BASE_URL.trimEnd('/')
        private val haToken get() = BuildConfig.HA_TOKEN

        private fun inWindow(): Boolean = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in HOUR_START..HOUR_END

        private fun widgetIds(ctx: Context): IntArray =
            AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, ParkingWidget::class.java)) ?: IntArray(0)

        /** 마지막 조회 층을 계속 표시(주차 자리는 주차 중 바뀌지 않음). 한 번도 조회 안 됐으면 "주차/확인"(탭 유도).
         *  30분 자동 갱신·탭으로 최신화되고, 조회 결과 층이 비면 "출차". 신선도(WINDOW_MS)로 "확인" 띄우지 않음
         *  — inexact 알람(Doze)으로 자동 갱신이 늦어도 마지막 위치는 유효하기 때문. */
        fun render(ctx: Context) {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (p.getLong(KEY_TS, 0L) <= 0L) { applyClick(ctx); return } // 최초(조회 이력 없음)만 탭 유도
            fun v(floor: String?) = if (floor.isNullOrBlank()) "출차" else floor
            applyRows(ctx, v(p.getString(KEY_M, null)), v(p.getString(KEY_G, null)))
        }

        /** HA 센서 두 개를 읽어 (모닝, 그랜저) 층 반환. '출차' 는 ""(→ render 가 "출차"). HA 연결 실패·센서 unavailable 이면 null(마지막 값 유지). */
        private fun fetch(): Pair<String, String>? {
            if (haBase.isBlank() || haToken.isBlank()) return null
            val m = haFloor(ENTITY_MORNING) ?: return null
            val g = haFloor(ENTITY_GRANDEUR) ?: return null
            return m to g
        }

        private fun haFloor(entity: String): String? = try {
            val con = haConnection("/api/states/$entity", "GET", readMs = 6000)
            val body = con.inputStream.bufferedReader().use { it.readText() }
            con.disconnect()
            when (val s = JSONObject(body).optString("state").trim()) {
                "", "unknown", "unavailable" -> null
                "출차" -> ""
                else -> s
            }
        } catch (e: Exception) { null }

        /** 탭: HA 에 RTLS 즉시 재조회 요청(두 센서가 한 요청을 공유 → 1회 조회). 폰당 [TAP_REFRESH_GAP_MS] 에 1번만, 실패해도 읽기는 진행. */
        private fun requestHaRefresh(ctx: Context) {
            if (haBase.isBlank() || haToken.isBlank()) return
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - p.getLong(KEY_REFRESH_TS, 0L) in 0L until TAP_REFRESH_GAP_MS) return
            p.edit().putLong(KEY_REFRESH_TS, now).apply()
            runCatching {
                val con = haConnection("/api/services/homeassistant/update_entity", "POST", readMs = 12000)
                val body = JSONObject().put("entity_id", JSONArray().put(ENTITY_MORNING).put(ENTITY_GRANDEUR))
                con.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                con.responseCode // 서비스 호출은 RTLS 조회가 끝난 뒤 응답
                con.disconnect()
            }
        }

        private fun haConnection(path: String, method: String, readMs: Int): HttpURLConnection =
            (URL("$haBase$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 6000; readTimeout = readMs
                setRequestProperty("Authorization", "Bearer $haToken")
                if (method == "POST") { doOutput = true; setRequestProperty("Content-Type", "application/json") }
            }

        private fun applyRows(ctx: Context, line1: String, line2: String) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_parking)
            rv.setViewVisibility(R.id.parking_click, View.GONE)
            rv.setViewVisibility(R.id.parking_morning, View.VISIBLE)
            rv.setViewVisibility(R.id.parking_gap, View.VISIBLE)
            rv.setViewVisibility(R.id.parking_grandeur, View.VISIBLE)
            rv.setTextViewText(R.id.parking_morning, line1)
            rv.setTextViewText(R.id.parking_grandeur, line2)
            push(ctx, rv)
        }

        private fun applyClick(ctx: Context) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_parking)
            rv.setViewVisibility(R.id.parking_morning, View.GONE)
            rv.setViewVisibility(R.id.parking_gap, View.GONE)
            rv.setViewVisibility(R.id.parking_grandeur, View.GONE)
            rv.setViewVisibility(R.id.parking_click, View.VISIBLE)
            rv.setTextViewText(R.id.parking_click, "주차\n확인")
            push(ctx, rv)
        }

        private fun push(ctx: Context, rv: RemoteViews) {
            val ids = widgetIds(ctx)
            if (ids.isEmpty()) return
            val tap = Intent(ctx, ParkingWidget::class.java).setAction(ACTION_REFRESH)
            val pi = PendingIntent.getBroadcast(
                ctx, REQ_TAP, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.parking_root, pi)
            AppWidgetManager.getInstance(ctx).updateAppWidget(ids, rv)
        }

        private fun tickPending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, REQ_TICK, Intent(ctx, ParkingWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        private fun revertPending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, REQ_REVERT, Intent(ctx, ParkingWidget::class.java).setAction(ACTION_REVERT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** 다음 자동 갱신 예약: 다음 :02 또는 :32(HA 조회 직후), 창(06~22시) 밖이면 다음 06:02. Doze 친화 inexact. */
        fun scheduleNextTick(ctx: Context) {
            if (widgetIds(ctx).isEmpty()) return
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val cal = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1) // 최소 1분 뒤
                val m = get(Calendar.MINUTE)
                when {
                    m <= TICK_MINUTE -> set(Calendar.MINUTE, TICK_MINUTE)
                    m <= TICK_MINUTE + 30 -> set(Calendar.MINUTE, TICK_MINUTE + 30)
                    else -> { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, TICK_MINUTE) }
                }
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (h !in HOUR_START..HOUR_END) {
                if (h > HOUR_END) cal.add(Calendar.DAY_OF_MONTH, 1) // 밤이면 다음날 아침
                cal.set(Calendar.HOUR_OF_DAY, HOUR_START); cal.set(Calendar.MINUTE, TICK_MINUTE)
            }
            am.set(AlarmManager.RTC, cal.timeInMillis, tickPending(ctx))
        }

        /** 신선도(WINDOW_MS) 만료 시 1회 재렌더 → '주차/확인'으로 복귀(재조회 아님). */
        private fun scheduleRevert(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.set(AlarmManager.RTC, System.currentTimeMillis() + WINDOW_MS + 2000, revertPending(ctx))
        }
    }
}

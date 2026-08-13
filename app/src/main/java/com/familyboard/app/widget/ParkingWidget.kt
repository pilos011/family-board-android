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
import com.familyboard.app.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

/**
 * 주차 위치 1x1 위젯 (아파트 RTLS 태그 조회).
 * - 탭 → 그 순간 현재 주차 층을 조회해 "B2 / B4"(위=모닝, 아래=그랜저)로 표시.
 * - **자동 갱신**: 06~23시 30분마다 백그라운드로 조회(무음) → 페이지를 넘겨 볼 때 대체로 최신.
 *   (안드로이드는 '위젯이 보이는 순간'을 알려주지 않아 주기 갱신으로 대체.)
 * - 최근 [WINDOW_MS] 내 조회값이 있으면 층 표시, 없으면(야간 등) "주차/확인"(탭 유도).
 * - 데이터 없으면(시리얼 행 없음/층 빈값) "출차". RTLS 는 세대(104동 3406호)라 한 요청에 두 차량이 옴.
 */
class ParkingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context)
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
            ACTION_REFRESH -> fetchAsync(context, true)                       // 탭: 진행표시 O
            ACTION_TICK -> { scheduleNextTick(context); if (inWindow()) fetchAsync(context, false) } // 자동: 무음
            ACTION_REVERT -> render(context)                                  // 신선도 만료 재렌더(→ '확인')
        }
    }

    /** 조회 → 저장 → 렌더. showProgress=true(탭)면 '조회 중/실패'를 보여주고, 자동 갱신은 무음. */
    private fun fetchAsync(ctx: Context, showProgress: Boolean) {
        if (widgetIds(ctx).isEmpty()) return
        if (showProgress) applyRows(ctx, "조회", "중…")
        val pending = goAsync()
        Thread {
            try {
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
        private const val REQ_TAP = 7501
        private const val REQ_REVERT = 7502
        private const val REQ_TICK = 7503
        private const val WINDOW_MS = 35 * 60 * 1000L   // 이 시간 내 조회값이면 층 표시(30분 자동갱신보다 약간 길게)
        private const val TICK_MS = 30 * 60 * 1000L     // 자동 갱신 주기 30분(HA 15분 inexact에 배칭돼 추가 부담 미미)
        private const val HOUR_START = 6                 // 자동 갱신 시간대 06~23시
        private const val HOUR_END = 23

        private const val ACTION_REFRESH = "com.familyboard.app.widget.PARKING_REFRESH"
        private const val ACTION_REVERT = "com.familyboard.app.widget.PARKING_REVERT"
        private const val ACTION_TICK = "com.familyboard.app.widget.PARKING_TICK"

        // 104동 3406호. 한 번 요청하면 두 차량이 함께 응답에 담김.
        private const val API_URL =
            "http://122.199.183.213/rtlsTag/serial/action.do?method=serial.Login&dongId=104&hoId=3406&serialId=A006524"
        private const val SERIAL_MORNING = "A006524"   // 차량1 모닝
        private const val SERIAL_GRANDEUR = "A033882"  // 차량2 그랜저

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

        private fun fetch(): Pair<String, String>? = try {
            val con = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000; readTimeout = 6000; requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val html = con.inputStream.bufferedReader().use { it.readText() }
            con.disconnect()
            (floorFor(html, SERIAL_MORNING) ?: "") to (floorFor(html, SERIAL_GRANDEUR) ?: "")
        } catch (e: Exception) { null }

        /** 해당 시리얼의 tr 행에서 carFloorNameArea 층 반환. 없거나 비면 null(=출차). */
        private fun floorFor(html: String, serial: String): String? {
            val idx = html.indexOf("'$serial'")
            if (idx < 0) return null
            val after = html.substring(idx)
            val m = Regex("carFloorNameArea\">\\s*([^<]*?)\\s*</div>").find(after) ?: return null
            return m.groupValues[1].trim().ifBlank { null }
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

        /** 다음 자동 갱신 예약: +30분, 창(06~23시) 밖이면 다음 06:00. Doze 친화 inexact. */
        fun scheduleNextTick(ctx: Context) {
            if (widgetIds(ctx).isEmpty()) return
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + TICK_MS }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (h !in HOUR_START..HOUR_END) {
                if (h > HOUR_END) cal.add(Calendar.DAY_OF_MONTH, 1) // 밤이면 다음날 아침
                cal.set(Calendar.HOUR_OF_DAY, HOUR_START); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
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

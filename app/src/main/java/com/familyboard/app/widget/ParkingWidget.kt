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

/**
 * 주차 위치 1x1 위젯 (아파트 RTLS 태그 조회).
 * - 탭 → 그 순간 현재 주차 층을 조회해 "모닝 B2" / "그랜저 B4"로 표시.
 * - 조회 후 [WINDOW_MS](20분) 동안은 조회값 유지, 그 이후엔 "모닝 확인" / "그랜저 확인"으로
 *   되돌려 과도한 조회를 막는다(자동 폴링 없음 — 오직 탭할 때만 네트워크 호출).
 * - 해당 차량 데이터가 없으면(시리얼 행 없음/층 빈값) "출차"로 표시.
 * 응답은 HTML. 한 번의 요청에 세대(104동 3406호)의 두 차량이 모두 담겨오므로,
 * 각 차량은 onclick 의 시리얼로 매칭해 층을 뽑는다(순서/누락에 안전).
 */
class ParkingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = render(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // APPWIDGET_UPDATE → onUpdate
        when (intent.action) {
            ACTION_REFRESH -> {
                // 탭: 즉시 '조회 중' 표시 후 백그라운드 조회.
                applyRows(context, "조회", "중…")
                val pending = goAsync()
                Thread {
                    try {
                        val res = fetch()
                        if (res != null) {
                            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putString(KEY_M, res.first)
                                .putString(KEY_G, res.second)
                                .putLong(KEY_TS, System.currentTimeMillis())
                                .apply()
                            render(context)
                            scheduleRevert(context)
                        } else {
                            applyRows(context, "조회", "실패")
                        }
                    } finally {
                        pending.finish()
                    }
                }.start()
            }
            ACTION_REVERT -> render(context) // 20분 경과 재렌더(→ '확인')
        }
    }

    companion object {
        private const val PREFS = "parking_widget"
        private const val KEY_M = "floor_morning"
        private const val KEY_G = "floor_grandeur"
        private const val KEY_TS = "fetched_at"
        private const val REQ_TAP = 7501
        private const val REQ_REVERT = 7502
        private const val WINDOW_MS = 20 * 60 * 1000L

        private const val ACTION_REFRESH = "com.familyboard.app.widget.PARKING_REFRESH"
        private const val ACTION_REVERT = "com.familyboard.app.widget.PARKING_REVERT"

        // 104동 3406호. 한 번 요청하면 두 차량이 함께 응답에 담김.
        private const val API_URL =
            "http://122.199.183.213/rtlsTag/serial/action.do?method=serial.Login&dongId=104&hoId=3406&serialId=A006524"
        private const val SERIAL_MORNING = "A006524"   // 차량1 모닝
        private const val SERIAL_GRANDEUR = "A033882"  // 차량2 그랜저

        /** 캐시 + 20분 규칙으로 현재 표시 렌더. 20분 경과면 큰 "클릭" 한 줄, 아니면 조회값 두 줄. */
        fun render(ctx: Context) {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val ts = p.getLong(KEY_TS, 0L)
            val fresh = ts > 0L && System.currentTimeMillis() - ts <= WINDOW_MS
            if (!fresh) { applyClick(ctx); return }
            // 라벨 없이 층만: 첫째 줄=모닝(베이지), 둘째 줄=그랜저(검정). 데이터 없으면 "출차".
            fun v(floor: String?) = if (floor.isNullOrBlank()) "출차" else floor
            applyRows(ctx, v(p.getString(KEY_M, null)), v(p.getString(KEY_G, null)))
        }

        private fun fetch(): Pair<String, String>? = try {
            val con = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val html = con.inputStream.bufferedReader().use { it.readText() }
            con.disconnect()
            // "" = 출차(행 없음/층 빈값)
            (floorFor(html, SERIAL_MORNING) ?: "") to (floorFor(html, SERIAL_GRANDEUR) ?: "")
        } catch (e: Exception) {
            null
        }

        /** 해당 시리얼의 tr 행을 찾아 그 안의 carFloorNameArea 층을 반환. 없거나 비면 null(=출차). */
        private fun floorFor(html: String, serial: String): String? {
            val idx = html.indexOf("'$serial'")
            if (idx < 0) return null
            val after = html.substring(idx)
            val m = Regex("carFloorNameArea\">\\s*([^<]*?)\\s*</div>").find(after) ?: return null
            return m.groupValues[1].trim().ifBlank { null }
        }

        /** 조회값 두 줄 모드(위=모닝 베이지 / 아래=그랜저 검정). 로딩·실패 문구도 여기로. */
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

        /** 20분 경과: 1/2 구분 없이 "주차 / 확인" 두 줄(배경은 레이아웃 기본 남색). */
        private fun applyClick(ctx: Context) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_parking)
            rv.setViewVisibility(R.id.parking_morning, View.GONE)
            rv.setViewVisibility(R.id.parking_gap, View.GONE)
            rv.setViewVisibility(R.id.parking_grandeur, View.GONE)
            rv.setViewVisibility(R.id.parking_click, View.VISIBLE)
            rv.setTextViewText(R.id.parking_click, "주차\n확인")
            push(ctx, rv)
        }

        /** ids 확인 + 탭(=조회) 클릭 인텐트 연결 후 반영. */
        private fun push(ctx: Context, rv: RemoteViews) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ParkingWidget::class.java))
            if (ids == null || ids.isEmpty()) return
            val tap = Intent(ctx, ParkingWidget::class.java).setAction(ACTION_REFRESH)
            val pi = PendingIntent.getBroadcast(
                ctx, REQ_TAP, tap, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.parking_root, pi)
            mgr.updateAppWidget(ids, rv)
        }

        /** 20분 뒤 한 번 재렌더 → '확인' 상태로 자연 복귀(재조회 없음, 루프 아님). */
        private fun scheduleRevert(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val i = Intent(ctx, ParkingWidget::class.java).setAction(ACTION_REVERT)
            val pi = PendingIntent.getBroadcast(
                ctx, REQ_REVERT, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.set(AlarmManager.RTC, System.currentTimeMillis() + WINDOW_MS + 2000, pi)
        }
    }
}

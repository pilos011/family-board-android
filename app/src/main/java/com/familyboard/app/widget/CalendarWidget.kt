package com.familyboard.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.familyboard.app.MainActivity
import com.familyboard.app.R
import org.json.JSONArray
import java.time.LocalDate

/**
 * 가족 달력 위젯(4x5). 이번 달 그리드(일요일 시작, 6주) + 공휴일/가족 일정을 텍스트로 표시(앱 달력과 유사, 음력 제외).
 * 데이터는 앱이 [CalendarWidgetData] 로 캐시한 걸 읽어 렌더(위젯 자체는 네트워크 없음).
 * 탭 → 앱 가족 달력. 날짜 갱신: onUpdate + 자정(DATE_CHANGED)/시간변경.
 */
class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = render(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> render(context)
        }
    }

    companion object {
        const val NAV = "calendar" // 탭 시 이동 대상(AppNav 가 Routes.CALENDAR 로 매핑)
        private const val REQ = 7501

        fun render(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, CalendarWidget::class.java))
            if (ids == null || ids.isEmpty()) return
            val pkg = ctx.packageName

            val today = LocalDate.now()
            val first = today.withDayOfMonth(1)
            val offset = first.dayOfWeek.value % 7
            val gridStart = first.minusDays(offset.toLong())

            // 이번 달 캐시(공휴일/일정)만 사용. 달이 바뀌었는데 앱이 아직 재계산 못 했으면 숫자만.
            val prefs = ctx.getSharedPreferences(CalendarWidgetData.PREFS, Context.MODE_PRIVATE)
            val cells: JSONArray? =
                if (prefs.getString(CalendarWidgetData.KEY_MONTH, null) == CalendarWidgetData.monthKey(today))
                    runCatching { JSONArray(prefs.getString(CalendarWidgetData.KEY_CELLS, "[]")) }.getOrNull()
                else null

            val rv = RemoteViews(pkg, R.layout.widget_calendar)
            rv.setTextViewText(R.id.cal_title, "${today.year}년 ${today.monthValue}월")
            rv.removeAllViews(R.id.cal_grid)
            for (w in 0 until 6) {
                val row = RemoteViews(pkg, R.layout.widget_cal_row)
                for (dow in 0 until 7) {
                    val idx = w * 7 + dow
                    val date = gridStart.plusDays(idx.toLong())
                    val inMonth = date.monthValue == today.monthValue && date.year == today.year
                    val cellObj = cells?.optJSONObject(idx)
                    val cell = RemoteViews(pkg, R.layout.widget_cal_cell)
                    cell.setTextViewText(R.id.cal_cell_day, date.dayOfMonth.toString())

                    // 날짜 숫자 색/배경(오늘=주황 원+흰색, 공휴일/일요일=빨강, 토요일=파랑, 이달 외=회색)
                    if (date == today) {
                        cell.setInt(R.id.cal_cell_day, "setBackgroundResource", R.drawable.widget_today_bg)
                        cell.setTextColor(R.id.cal_cell_day, Color.WHITE)
                    } else {
                        cell.setInt(R.id.cal_cell_day, "setBackgroundResource", 0)
                        // 앱과 동일: 일/공휴일=빨강, 토=파랑, 그외 Ink. 이달 외는 35% 투명.
                        val baseRgb = when {
                            (cellObj?.has("hol") == true) || dow == 0 -> 0xE03131L
                            dow == 6 -> 0x1C7ED6L
                            else -> 0x2B2B2EL
                        }
                        val alpha = if (inMonth) 0xFFL else 0x59L
                        cell.setTextColor(R.id.cal_cell_day, ((alpha shl 24) or baseRgb).toInt())
                    }

                    // 라벨: 공휴일(빨강) + 일정(구성원색) 최대 2 + "+N"
                    cell.removeAllViews(R.id.cal_cell_labels)
                    if (cellObj != null) {
                        if (cellObj.has("hol")) {
                            val name = if (cellObj.optBoolean("holStart", true)) cellObj.optString("hol") else ""
                            addLabel(ctx, cell, name, 0xFFE03131.toInt(), Color.WHITE)
                        }
                        val ev = cellObj.optJSONArray("ev")
                        val n = ev?.length() ?: 0
                        for (li in 0 until minOf(n, 2)) {
                            val lo = ev!!.getJSONObject(li)
                            addLabel(ctx, cell, lo.optString("t"), lo.optInt("c"), Color.WHITE)
                        }
                        if (n > 2) addLabel(ctx, cell, "+${n - 2}", 0x00000000, 0xFF888888.toInt())
                    }
                    row.addView(R.id.cal_row, cell)
                }
                rv.addView(R.id.cal_grid, row)
            }

            val i = Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_WIDGET_NAV, NAV)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                ctx, REQ, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.cal_root, pi)
            mgr.updateAppWidget(ids, rv)
        }

        private fun addLabel(ctx: Context, cell: RemoteViews, text: String, bgColor: Int, textColor: Int) {
            val l = RemoteViews(ctx.packageName, R.layout.widget_cal_label)
            l.setTextViewText(R.id.cal_label, text)
            l.setInt(R.id.cal_label, "setBackgroundColor", bgColor)
            l.setTextColor(R.id.cal_label, textColor)
            cell.addView(R.id.cal_cell_labels, l)
        }
    }
}

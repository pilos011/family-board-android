package com.familyboard.app.widget

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.familyboard.app.data.Family
import com.familyboard.app.data.RecurrenceExpander
import com.familyboard.app.data.model.CalendarEvent
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 앱이 '이번 달' 달력 위젯 데이터(공휴일 + 반복 전개된 일정)를 계산해 캐시.
 * 위젯([CalendarWidget])은 네트워크/Firestore 없이 이 캐시만으로 텍스트를 그린다.
 * (앱 달력과 동일 파이프라인: [RecurrenceExpander] + vm.holidays + [Family] 색)
 */
object CalendarWidgetData {
    const val PREFS = "calendar_widget"
    const val KEY_MONTH = "month"
    const val KEY_CELLS = "cells"

    fun monthKey(d: LocalDate) = "${d.year}-${d.monthValue}"

    fun update(ctx: Context, events: List<CalendarEvent>, holidays: Map<String, String>) {
        val today = LocalDate.now()
        val first = today.withDayOfMonth(1)
        val offset = first.dayOfWeek.value % 7 // 일요일 시작
        val gridStart = first.minusDays(offset.toLong())
        val byDate = RecurrenceExpander.expand(events, gridStart, gridStart.plusDays(41))

        val cells = JSONArray()
        for (idx in 0 until 42) {
            val date = gridStart.plusDays(idx.toLong())
            val iso = date.toString()
            val o = JSONObject().put("d", date.dayOfMonth)
            holidays[iso]?.let { name ->
                o.put("hol", name)
                o.put("holStart", holidays[date.minusDays(1).toString()] != name) // 연휴 시작 칸만 이름
            }
            val ev = JSONArray()
            byDate[iso]?.forEach { de ->
                ev.put(
                    JSONObject()
                        .put("t", if (de.spanStart) de.event.title else "") // 시작 칸만 제목, 이어지는 칸은 색 막대만
                        .put("c", Family.colorOfIds(de.event.memberIds).toArgb()),
                )
            }
            o.put("ev", ev)
            cells.put(o)
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_MONTH, monthKey(today))
            .putString(KEY_CELLS, cells.toString())
            .apply()
        CalendarWidget.render(ctx)
    }
}

package com.familyboard.app.data

import com.familyboard.app.data.model.CalendarEvent
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** 특정 날짜에 놓이는 일정 + 그 날이 (그 회차) 기간의 시작/끝인지 (연속 막대 렌더용). */
data class DayEvent(
    val event: CalendarEvent,
    val spanStart: Boolean,
    val spanEnd: Boolean,
)

/**
 * 반복 일정을 실제 발생일로 전개해, 화면 구간(window) 안 날짜별 일정 맵을 만든다.
 * 여러 날 일정은 시작~종료 기간의 각 날짜에 놓되, 각 날의 spanStart/spanEnd 로 막대 모양을 구분한다.
 */
object RecurrenceExpander {

    fun expand(
        events: List<CalendarEvent>,
        winStart: LocalDate,
        winEnd: LocalDate,
    ): Map<String, List<DayEvent>> {
        val map = HashMap<String, MutableList<DayEvent>>()
        for (e in events) {
            val start = runCatching { LocalDate.parse(e.startDateIso) }.getOrNull() ?: continue
            val end = runCatching { LocalDate.parse(e.endDateIso) }.getOrNull() ?: start
            val duration = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0)
            when (e.repeat) {
                "weekly" -> expandWeekly(map, e, start, duration, winStart, winEnd)
                "monthly" -> expandMonthly(map, e, start, duration, winStart, winEnd)
                "yearly" -> if (e.lunar) expandLunarYearly(map, e, start, duration, winStart, winEnd)
                            else expandYearly(map, e, start, duration, winStart, winEnd)
                else -> placeSpan(map, e, start, duration, winStart, winEnd)
            }
        }
        return map
    }

    private fun placeSpan(
        map: HashMap<String, MutableList<DayEvent>>,
        e: CalendarEvent,
        occStart: LocalDate,
        duration: Long,
        winStart: LocalDate,
        winEnd: LocalDate,
    ) {
        val occEnd = occStart.plusDays(duration)
        if (occEnd.isBefore(winStart) || occStart.isAfter(winEnd)) return
        var d = if (occStart.isBefore(winStart)) winStart else occStart
        val last = if (occEnd.isAfter(winEnd)) winEnd else occEnd
        var guard = 0
        while (!d.isAfter(last) && guard < 400) {
            map.getOrPut(d.toString()) { mutableListOf() }
                .add(DayEvent(e, spanStart = d == occStart, spanEnd = d == occEnd))
            d = d.plusDays(1); guard++
        }
    }

    private fun expandWeekly(
        map: HashMap<String, MutableList<DayEvent>>,
        e: CalendarEvent, start: LocalDate, duration: Long, winStart: LocalDate, winEnd: LocalDate,
    ) {
        val earliest = winStart.minusDays(duration)
        val daysFromStart = ChronoUnit.DAYS.between(start, earliest)
        val n0 = if (daysFromStart <= 0) 0L else (daysFromStart + 6) / 7
        var occ = start.plusWeeks(n0)
        var guard = 0
        while (!occ.isAfter(winEnd) && guard < 300) {
            if (!occ.isBefore(start) && !e.exdates.contains(occ.toString()))
                placeSpan(map, e, occ, duration, winStart, winEnd)
            occ = occ.plusWeeks(1); guard++
        }
    }

    private fun expandMonthly(
        map: HashMap<String, MutableList<DayEvent>>,
        e: CalendarEvent, start: LocalDate, duration: Long, winStart: LocalDate, winEnd: LocalDate,
    ) {
        val startMonths = ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(winStart))
        var n = if (startMonths <= 1) 0L else startMonths - 1
        var guard = 0
        while (guard < 200) {
            val ym = YearMonth.from(start).plusMonths(n)
            val occStart = LocalDate.of(ym.year, ym.month, start.dayOfMonth.coerceAtMost(ym.lengthOfMonth()))
            val validDay = start.dayOfMonth <= ym.lengthOfMonth()
            if (occStart.isAfter(winEnd)) break
            if (validDay && !occStart.isBefore(start) && !e.exdates.contains(occStart.toString()))
                placeSpan(map, e, occStart, duration, winStart, winEnd)
            n++; guard++
        }
    }

    private fun expandYearly(
        map: HashMap<String, MutableList<DayEvent>>,
        e: CalendarEvent, start: LocalDate, duration: Long, winStart: LocalDate, winEnd: LocalDate,
    ) {
        var year = maxOf(start.year, winStart.year - 1)
        while (year <= winEnd.year + 1) {
            val occStart = runCatching { start.withYear(year) }.getOrNull()
            if (occStart != null && !occStart.isBefore(start) && !e.exdates.contains(occStart.toString()))
                placeSpan(map, e, occStart, duration, winStart, winEnd)
            year++
        }
    }

    private fun expandLunarYearly(
        map: HashMap<String, MutableList<DayEvent>>,
        e: CalendarEvent, start: LocalDate, duration: Long, winStart: LocalDate, winEnd: LocalDate,
    ) {
        val lunar = LunarCalendar.fields(start)
        var year = maxOf(start.year, winStart.year - 1)
        while (year <= winEnd.year + 1) {
            val occStart = LunarCalendar.solarForLunar(
                year, lunar.month, lunar.day, lunar.leap, start.monthValue, start.dayOfMonth,
            )
            if (occStart != null && !occStart.isBefore(start) && !e.exdates.contains(occStart.toString()))
                placeSpan(map, e, occStart, duration, winStart, winEnd)
            year++
        }
    }
}

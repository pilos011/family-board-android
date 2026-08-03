package com.familyboard.app.data

import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.time.LocalDate
import java.time.ZoneId

/**
 * 양력 → 한국 음력 변환. 안드로이드 내장 ICU ChineseCalendar 사용(API 24+).
 * 한국 음력은 중국 음력과 동일 계산(KST 기준).
 */
object LunarCalendar {
    private val seoulZone = ZoneId.of("Asia/Seoul")
    private val icuSeoul: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

    /** 음력 필드 */
    data class Lunar(val month: Int, val day: Int, val leap: Boolean)

    private fun ccFor(date: LocalDate): ChineseCalendar {
        val millis = date.atTime(12, 0).atZone(seoulZone).toInstant().toEpochMilli()
        val cc = ChineseCalendar(icuSeoul, ULocale.KOREA)
        cc.timeInMillis = millis
        return cc
    }

    fun fields(date: LocalDate): Lunar {
        val cc = ccFor(date)
        return Lunar(
            month = cc.get(ChineseCalendar.MONTH) + 1,   // 0-based → 1-based
            day = cc.get(ChineseCalendar.DAY_OF_MONTH),
            leap = cc.get(ChineseCalendar.IS_LEAP_MONTH) == 1,
        )
    }

    /** "6.15", 윤달이면 "윤6.15" */
    fun label(date: LocalDate): String {
        val l = fields(date)
        return (if (l.leap) "윤" else "") + "${l.month}.${l.day}"
    }

    /**
     * 주어진 양력 연도(year)에서 음력 (month/day/leap)에 해당하는 양력 날짜.
     * anchor(원본 양력 월/일) 기준 ±50일을 탐색. 없으면 null(그 해에 해당 음력일이 없는 경우 등).
     */
    fun solarForLunar(year: Int, month: Int, day: Int, leap: Boolean, anchorMonth: Int, anchorDay: Int): LocalDate? {
        val anchor = runCatching {
            LocalDate.of(year, anchorMonth, anchorDay.coerceAtMost(28))
        }.getOrDefault(LocalDate.of(year, 1, 1))
        for (offset in -50..50) {
            val d = anchor.plusDays(offset.toLong())
            val l = fields(d)
            if (l.month == month && l.day == day && l.leap == leap) return d
        }
        return null
    }
}

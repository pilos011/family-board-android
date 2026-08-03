package com.familyboard.app.data

import java.time.LocalDate

/**
 * 24절기 계산 (통용 수성공식, 21세기 2000~2099 유효).
 * day = int(Y * 0.2422 + C) - int(Y / 4),  Y = 연도 - 2000.
 * 드문 예외 연도에서 ±1일 오차 가능(가족 캘린더 용도로 충분).
 */
object SolarTerms {
    // month -> [(C상수, 한글명)] (월별 2개, 날짜 순)
    private val table: Map<Int, List<Pair<Double, String>>> = mapOf(
        1 to listOf(5.4055 to "소한", 20.12 to "대한"),
        2 to listOf(3.87 to "입춘", 18.73 to "우수"),
        3 to listOf(5.63 to "경칩", 20.646 to "춘분"),
        4 to listOf(4.81 to "청명", 20.1 to "곡우"),
        5 to listOf(5.52 to "입하", 21.04 to "소만"),
        6 to listOf(5.678 to "망종", 21.37 to "하지"),
        7 to listOf(7.108 to "소서", 22.83 to "대서"),
        8 to listOf(7.5 to "입추", 23.13 to "처서"),
        9 to listOf(7.646 to "백로", 23.042 to "추분"),
        10 to listOf(8.318 to "한로", 23.438 to "상강"),
        11 to listOf(7.438 to "입동", 22.36 to "소설"),
        12 to listOf(7.18 to "대설", 22.6 to "동지"),
    )

    /** 그 날이 절기면 이름, 아니면 null */
    fun of(date: LocalDate): String? {
        val y = date.year
        if (y < 2000 || y > 2099) return null
        val yy = y - 2000
        table[date.monthValue]?.forEach { (c, name) ->
            val day = (yy * 0.2422 + c).toInt() - (yy / 4)
            if (day == date.dayOfMonth) return name
        }
        return null
    }
}

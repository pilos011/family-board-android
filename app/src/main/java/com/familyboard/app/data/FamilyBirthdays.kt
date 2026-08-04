package com.familyboard.app.data

import java.time.LocalDate

/**
 * 가족 생일 (D-Day 카운트다운 기본 항목).
 * 표시 순서 고정: 선일 → 은선 → 준영 → 준호.
 */
object FamilyBirthdays {
    val list: List<Pair<String, LocalDate>> = listOf(
        "seonil" to LocalDate.of(1974, 2, 21),
        "eunseon" to LocalDate.of(1976, 4, 12),
        "junyoung" to LocalDate.of(2006, 6, 26),
        "junho" to LocalDate.of(2008, 11, 20),
    )
}

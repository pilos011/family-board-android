package com.familyboard.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 남은 날 계산 결과 */
data class LifeStats(
    val remaining: Long,        // 남은 날
    val passed: Long,           // 지나온 날
    val total: Long,            // 총 생애일
    val progressPercent: Double,// 진행률(%)
    val remainingThisYear: Long,// 올해 남은 날
)

/**
 * 인생 버킷: 선일/은선의 생일·기대수명(하드코딩)으로 남은 날 등을 계산.
 * - 선일: 1974-02-21 출생, 85세(2059-10-25)까지 생존 가정
 * - 은선: 1976-04-16 출생, 선일보다 5.8년 더 생존 가정
 */
object BucketLife {
    private val SEONIL_DEATH = LocalDate.of(2059, 10, 25)

    private val birth = mapOf(
        "seonil" to LocalDate.of(1974, 2, 21),
        "eunseon" to LocalDate.of(1976, 4, 16),
    )
    private val death = mapOf(
        "seonil" to SEONIL_DEATH,
        "eunseon" to SEONIL_DEATH.plusDays((5.8 * 365.25).toLong()), // +5.8년
    )

    fun supports(memberId: String?): Boolean = memberId != null && birth.containsKey(memberId)

    fun stats(memberId: String, today: LocalDate = LocalDate.now()): LifeStats? {
        val b = birth[memberId] ?: return null
        val d = death[memberId] ?: return null
        val passed = ChronoUnit.DAYS.between(b, today).coerceAtLeast(0)
        val remaining = ChronoUnit.DAYS.between(today, d).coerceAtLeast(0)
        val total = ChronoUnit.DAYS.between(b, d).coerceAtLeast(1)
        val pct = passed.toDouble() / total.toDouble() * 100.0
        val remThisYear = ChronoUnit.DAYS.between(today, LocalDate.of(today.year + 1, 1, 1))
        return LifeStats(remaining, passed, total, pct, remThisYear)
    }
}

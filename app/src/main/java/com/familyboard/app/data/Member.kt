package com.familyboard.app.data

import androidx.compose.ui.graphics.Color

/**
 * 가족 구성원. 앱 최초 실행 시 본인 이름을 선택하면 phone 이 내부적으로 매핑된다.
 * (안드로이드는 자기 폰 번호를 안정적으로 읽지 못해 '이름 선택' 방식을 사용)
 */
data class Member(
    val id: String,
    val name: String,
    val phone: String,
    val color: Color,
)

object Family {
    val members: List<Member> = listOf(
        Member("seonil", "선일", "010-3112-2692", Color(0xFFF783AC)),
        Member("eunseon", "은선", "010-3624-2692", Color(0xFF868E96)),
        Member("junyoung", "준영", "010-2546-2692", Color(0xFF4DABF7)),
        Member("junho", "준호", "010-2738-2692", Color(0xFF20C997)),
    )

    /** "모두"(가족 공용) 항목을 나타내는 가상 멤버 id */
    const val ALL_ID = "all"
    val allColor = Color(0xFF4C6EF5)

    fun byId(id: String?): Member? = members.firstOrNull { it.id == id }

    fun colorOf(id: String?): Color =
        if (id == ALL_ID || id == null) allColor else byId(id)?.color ?: allColor

    fun nameOf(id: String?): String =
        if (id == ALL_ID || id == null) "모두" else byId(id)?.name ?: "모두"

    /** 복수 담당자 대표 색: 1명이면 그 색, 여러 명이거나 공용이면 가족색 */
    fun colorOfIds(ids: List<String>): Color = when {
        ids.isEmpty() || ids.contains(ALL_ID) -> allColor
        ids.size == 1 -> colorOf(ids.first())
        else -> colorOf(ids.first())
    }

    /** 복수 담당자 이름: "선일, 은선" / 공용이면 "모두" */
    fun namesOf(ids: List<String>): String =
        if (ids.isEmpty() || ids.contains(ALL_ID)) "모두"
        else ids.mapNotNull { byId(it)?.name }.joinToString(", ").ifBlank { "모두" }
}

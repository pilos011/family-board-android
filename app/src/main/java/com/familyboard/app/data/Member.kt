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

    /** D-Day 항목을 달력에 표시할 때 쓰는 전용 색(주황) + 가상 멤버 id. */
    const val DDAY_ID = "dday"
    val ddayColor = Color(0xFFF08C00)

    fun byId(id: String?): Member? = members.firstOrNull { it.id == id }

    fun colorOf(id: String?): Color = when (id) {
        DDAY_ID -> ddayColor
        ALL_ID, null -> allColor
        else -> byId(id)?.color ?: allColor
    }

    fun nameOf(id: String?): String =
        if (id == ALL_ID || id == null) "모두" else byId(id)?.name ?: "모두"

    /** 아바타용 한 글자 이니셜. 준영/준호는 앞 글자가 같아 뒤 글자로 구분. */
    fun initialOf(id: String?): String = when (id) {
        "junyoung" -> "영"
        "junho" -> "호"
        else -> nameOf(id).take(1)
    }

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

    /** 대상 멤버 이름(가운뎃점): "선일·은선·준호". 비었거나 전체/4명이면 "모두". (홈 일정 보드용) */
    fun targetNames(ids: List<String>): String {
        if (ids.isEmpty() || ids.contains(ALL_ID) || members.all { it.id in ids }) return "모두"
        return members.filter { it.id in ids }.joinToString("·") { it.name }.ifBlank { "모두" }
    }
}

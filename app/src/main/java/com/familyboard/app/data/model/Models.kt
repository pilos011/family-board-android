package com.familyboard.app.data.model

/** 캘린더 일정. Firestore 직렬화를 위해 모든 필드에 기본값을 둔다. */
data class CalendarEvent(
    val id: String = "",
    val title: String = "",
    val startDateIso: String = "", // yyyy-MM-dd (시작일)
    val endDateIso: String = "",   // yyyy-MM-dd (종료일, 여러 날 일정 지원)
    val allDay: Boolean = false,
    val startTime: String = "",    // "HH:mm" 24시간 (allDay=false 일 때)
    val endTime: String = "",
    val memberIds: List<String> = listOf("all"), // 담당자(복수). ["all"] = 가족 공용
    val repeat: String = "",       // "" | "weekly" | "monthly" | "yearly"
    val lunar: Boolean = false,    // 음력 기준 일정 여부
    val reminder: String = "none", // 알림: none/atTime/5m/15m/30m/1h/2h/1d/2d
    val createdBy: String = "",    // 등록한 멤버 id (등록 알림에서 본인 제외용)
    val description: String = "",  // 상세 내용(최대 300자, URL 포함 가능)
    val photoUrls: List<String> = emptyList(), // 첨부 사진 URL(최대 5장)
    val exdates: List<String> = emptyList(),   // 반복 일정에서 제외된 날짜(yyyy-MM-dd)
)

/** 리스트(장보기/할 일/용돈 정산) 항목. */
data class ListItem(
    val id: String = "",
    val text: String = "",
    val checked: Boolean = false,
    val board: String = "",       // BoardType.key 또는 AllowanceBoards.*
    val createdBy: String = "",    // 등록한 member id
    val amount: Long = 0,          // 용돈 정산 금액(원). 그 외 보드는 0
    val memberIds: List<String> = listOf("all"), // 담당자(복수). ["all"]=공용
    val mustDo: Boolean = false,   // 버킷리스트 "꼭 하자!" 토글 (대표페이지 상단 노출)
    val description: String = "",  // 버킷 상세 내용
    val photoUrls: List<String> = emptyList(), // 버킷 첨부 사진
    val progress: List<ProgressNote> = emptyList(), // 버킷 진행 이력(카드 메모)
    val icon: String = "",         // 버킷 꾸미기 아이콘 키 (BucketIcons)
    val dateIso: String = "",      // D-Day 목표 날짜(yyyy-MM-dd)
    val yearly: Boolean = false,   // D-Day 매년 반복 여부
    val notifyIds: List<String> = emptyList(), // D-Day 알림 대상(모두=4인). 비었으면 알림 없음
    val homePinned: Boolean = false, // D-Day를 홈 화면 카운트다운에 게시할지
    val link: String = "",         // 장소 북마크 링크(네이버 플레이스 등). 맛집/가볼 곳 보드
    val rating: Long = 0,          // 장소 별점 0~5 (0=미방문). 맛집/가볼 곳 보드
    val address: String = "",      // 장소 전체 도로명 주소(네비 연동용). 맛집/가볼 곳 보드
    val naverScore: Double = 0.0,  // 네이버 평점(정렬용). 맛집/가볼 곳
    val lat: Double = 0.0,         // 위도(거리 정렬용)
    val lng: Double = 0.0,         // 경도(거리 정렬용)
    // 댓글은 progress(ProgressNote: text/by/dateIso)를 재사용
    // 파싱 요약(종목·별점·영업시간)은 description 을 재사용
)

/** 버킷 진행 이력 메모 */
data class ProgressNote(
    val text: String = "",
    val by: String = "",       // 작성 멤버 id
    val dateIso: String = "",  // yyyy-MM-dd
)

/** 인생 버킷 리스트 보드 키 (부부 공용, 단일). */
object BucketBoards {
    const val BOARD = "bucket"
}

/** D-Day(카운트다운) 보드 키. 가족 모두 사용. */
object DDayBoard {
    const val BOARD = "dday"
}

/** 장소 북마크 보드(맛집/가볼 곳). 링크·별점(0~5)·댓글 지원, 가족 공용. */
object PlaceBoards {
    const val RESTAURANT = "restaurant"
    const val VISIT = "visit"
    fun titleOf(board: String): String = when (board) {
        RESTAURANT -> "맛집"
        VISIT -> "가볼 곳"
        else -> "장소"
    }
    fun isPlace(board: String?): Boolean = board == RESTAURANT || board == VISIT
}

/** 용돈 정산 보드 키 (아이별). 준영/준호만 사용. */
object AllowanceBoards {
    const val JUNYOUNG = "allowance_junyoung"
    const val JUNHO = "allowance_junho"
}

/** 리스트 보드 종류. */
enum class BoardType(val key: String, val title: String) {
    SHOPPING("shopping", "장보기"),
    TODO("todo", "할 일"),
    NOTICE("notice", "가족 공지사항"); // 부모(선일/은선) 전용

    companion object {
        fun fromKey(key: String?): BoardType = entries.firstOrNull { it.key == key } ?: SHOPPING
    }
}

/** 한국 공휴일 (공공데이터포털 특일정보 API). */
data class Holiday(
    val dateIso: String = "",
    val name: String = "",
)

/**
 * 알림 시점 옵션. 기본값은 알림 없음. (실제 알림 발송 스케줄링은 후속 작업)
 * 사용자 지정은 "custom:yyyy-MM-dd" 형태로 저장되며, 해당일의 이벤트 시작 시간에 알림.
 */
object Reminders {
    const val CUSTOM = "custom"

    val options = listOf(
        "none" to "알림 없음",
        "atTime" to "이벤트 시간에",
        "5m" to "5분 전",
        "15m" to "15분 전",
        "30m" to "30분 전",
        "1h" to "1시간 전",
        "2h" to "2시간 전",
        "1d" to "1일 전",
        "2d" to "2일 전",
        CUSTOM to "사용자 지정",
    )

    fun isCustom(key: String): Boolean = key == CUSTOM || key.startsWith("custom:")

    fun label(key: String): String {
        if (key.startsWith("custom:")) return "사용자 지정 (${key.removePrefix("custom:")})"
        return options.firstOrNull { it.first == key }?.second ?: "알림 없음"
    }
}

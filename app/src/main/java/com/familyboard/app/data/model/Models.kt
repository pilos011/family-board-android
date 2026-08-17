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

/**
 * 달력·위젯에 '표시 전용'으로 얹는 가상 이벤트의 id 접두어(D-Day 항목/가족 생일).
 * 저장·편집 대상이 아니므로 날짜 시트 등에서 이 접두어면 제외한다. (접두어 중복 하드코딩 방지)
 */
object SyntheticEvent {
    const val DDAY = "dday_"
    const val BDAY = "bday_"
    fun isDisplayOnly(id: String): Boolean = id.startsWith(DDAY) || id.startsWith(BDAY)
}

/** 사용자 접속 현황(관리자 확인용): 마지막 접속 시각 + 앱 버전. Firestore presence/{memberId}. */
data class Presence(
    val memberId: String = "",
    val lastSeen: Long = 0,        // epoch millis(마지막 앱 실행 시각)
    val versionName: String = "",  // 예: "1.0.54"
    val versionCode: Int = 0,
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
    val category: String = "",     // 네이버 종목(한식·카페 등). 맛집/가볼 곳 필터용. 여행: 구글 카테고리(호텔·리조트 등)
    val region: String = "",       // 여행 위시리스트: 나라+도시(예: "베트남 하노이"). 필터용
    val naverScore: Double = 0.0,  // 네이버 평점(정렬용). 맛집/가볼 곳
    val lat: Double = 0.0,         // 위도(거리 정렬용)
    val lng: Double = 0.0,         // 경도(거리 정렬용)
    val createdAt: Long = 0,       // 생성 시각(epoch millis). 등록순 정렬용(재미진 곳/문서함 등)
    val viewedBy: List<String> = emptyList(), // 이 항목을 본(클릭한) 멤버 id 목록(재미진 곳)
    val likes: List<String> = emptyList(),    // 좋아요 누른 멤버 id 목록(가족 사진첩 ❤️)
    val takenAt: Long = 0,         // 가족 사진첩: 사진 촬영 시각(epoch millis, EXIF). 0=미상→dateIso/createdAt 사용. 월 구분·정렬 기준
    val rotation: Int = 0,         // 가족 사진첩: 표시 회전 각도(0/90/180/270, 시계방향). 원본은 보존, 화면에서만 회전. 공유·다운로드 시 적용
    // 가족 사진첩 촬영 위치는 lat/lng(GPS EXIF) + address(역지오코딩 지명) 재사용
    val fileName: String = "",     // 문서함: 원본 파일명(확장자 포함). text=표시 제목(편집 가능)
    val fileMime: String = "",     // 문서함: MIME 타입(열기용)
    val fileSize: Long = 0,        // 문서함: 파일 크기(bytes)
    val checkedAt: Long = 0,       // 체크된 시각(epoch millis). 장보기 체크 항목 3일 후 자동삭제 판정용(미체크=0)
    val usedBy: String = "",       // 가족 쿠폰함: '사용완료' 누른 멤버 id(빈값=미사용). checked=true 와 함께 세팅, 이 사람만 취소 가능
    // 댓글은 progress(ProgressNote: text/by/dateIso)를 재사용
    // 파싱 요약(종목·별점·영업시간)은 description 을 재사용
    // 문서함 열람 대상은 memberIds 를 재사용(["all"]=모두, 지정 시 해당 멤버만)
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

/** 여행 위시리스트: 구글 지도 공유로 담는 국내·해외 가볼 곳(네이버 가볼곳과 별도).
 *  text=장소명, link=구글 지도 링크(열기용), address=주소, lat/lng=좌표, description=메모, checked=다녀옴. */
object TravelBoard {
    const val BOARD = "travel"
    const val TITLE = "여행 위시리스트"
    private val admins = setOf("seonil", "eunseon")
    /** 편집·삭제: 담은 사람 본인 또는 부모(선일·은선). */
    fun canManage(memberId: String?, item: ListItem): Boolean =
        memberId != null && (memberId == item.createdBy || memberId in admins)
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

/** 재미진 곳: 유튜브/웹/이미지 공유 게시판. text=제목, link=URL(이미지는 빈값), photoUrls[0]=썸네일/이미지.
 *  BOARD=가족 공용, PRIVATE=내 것만(작성자 본인만 조회). */
object FunBoard {
    const val BOARD = "fun"
    const val PRIVATE = "myfun"
    const val RECIPE = "recipe"   // 요리 레시피(재미진 곳과 같은 형식, 공용). 공유 대상 아님 — 재미진 곳에서 '이동'으로만 들어옴
    const val TITLE = "재미진 곳"
    const val TITLE_PRIVATE = "내 재미진 곳"
    const val TITLE_RECIPE = "요리 레시피"
    fun titleOf(board: String) = when (board) {
        PRIVATE -> TITLE_PRIVATE
        RECIPE -> TITLE_RECIPE
        else -> TITLE
    }
}

/** 가족 쿠폰함: 문자·링크·이미지로 공유된 쿠폰을 모아 가족이 사용. 재미진 곳/내 재미진 곳에서
 *  롱클릭 '가족 쿠폰함으로 이동'으로 들어옴(요리 레시피 방식). photoUrls[0]=이미지, link=링크, text=제목/코드.
 *  checked=사용완료(회색 흐림·탭 불가), usedBy=사용완료 누른 멤버(그 사람만 취소 가능). */
object CouponBoard {
    const val BOARD = "coupon"
    const val TITLE = "가족 쿠폰함"
    val admins = setOf("seonil", "eunseon") // 선일·은선: 쿠폰 삭제 권한
    fun canDelete(memberId: String?): Boolean = memberId != null && memberId in admins
}

/**
 * 가족 공유 문서함: pdf·이미지·docx·엑셀 등 파일 공유. text=제목(기본 파일명, 편집 가능),
 * photoUrls[0]=서버 파일 URL, fileName/fileMime/fileSize=원본 메타, createdAt=올린 시각(최신순),
 * memberIds=열람 대상(["all"]=모두 / 지정 시 해당 멤버만·올린이·관리자는 항상).
 */
/** 가족 사진첩 보드 키. 한 장=한 항목(photoUrls[0]), dateIso=촬영일, likes=❤️, progress=댓글. */
object AlbumBoard {
    const val BOARD = "album"             // 가족 사진첩(공용)
    const val PRIVATE = "myalbum"         // 내 사진첩(본인만 조회·편집)
    const val TITLE = "가족 사진첩"
    const val TITLE_PRIVATE = "내 사진첩"
    const val ADMIN = "seonil" // 선일: 가족 사진첩 모든 사진 삭제 권한(내 사진첩엔 무관)
    // 월 태그(예: "2026-01" → "오사카 가족여행"). 한 달=한 항목(id="albumtag_<yyyy-MM>", dateIso=키, text=태그).
    // 가족 사진첩 태그는 가족 모두 수정. 내 사진첩 태그는 본인만(id에 멤버 포함).
    const val TAG_BOARD = "albumtag"
    const val TAG_BOARD_PRIVATE = "myalbumtag"
}

object DocBoard {
    const val BOARD = "docs"
    const val TITLE = "가족 공유 문서함"
    const val ADMIN = "seonil" // 선일: 모든 문서 수정·삭제 권한

    /** 열람 권한: 공개(모두/빈 대상)거나, 올린이·관리자거나, 지정 대상에 포함될 때. */
    fun visibleTo(item: ListItem, memberId: String?): Boolean {
        val ids = item.memberIds
        if (ids.isEmpty() || ids.contains("all")) return true
        if (memberId.isNullOrBlank()) return false
        return memberId == item.createdBy || memberId == ADMIN || ids.contains(memberId)
    }

    /** 수정·삭제 권한: 올린이 본인 또는 관리자(선일). */
    fun canManage(item: ListItem, memberId: String?): Boolean =
        !memberId.isNullOrBlank() && (memberId == item.createdBy || memberId == ADMIN)
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

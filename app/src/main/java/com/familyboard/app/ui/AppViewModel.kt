package com.familyboard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyboard.app.FamilyBoardApp
import com.familyboard.app.data.model.BoardType
import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.Family
import com.familyboard.app.data.FamilyBirthdays
import com.familyboard.app.notif.NotifyApi
import com.familyboard.app.notif.ReminderScheduler
import com.familyboard.app.notif.UpdateChecker
import com.familyboard.app.notif.UpdateInfo
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 온보딩 게이팅용 사용자 상태 */
sealed interface UserState {
    data object Loading : UserState
    data object NeedSelect : UserState
    data class Selected(val id: String) : UserState
}

/** 공유로 받은 장소(이름/링크/파싱요약/주소). 저장 위치(맛집/가볼 곳) 선택 대기용. */
data class SharedPlace(
    val name: String,
    val link: String,
    val description: String = "",
    val address: String = "",
    val category: String = "",  // 네이버 종목(맛집/가볼곳 필터용)
    val image: String = "",
    val images: List<String> = emptyList(), // 여러 장 묶음(재미진 곳)
    val naverScore: Double = 0.0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val loading: Boolean = false,
    val isFun: Boolean = false,    // true=재미진 곳(유튜브/웹/이미지/영상)
    val isTravel: Boolean = false, // true=여행 위시리스트(구글 지도 공유)
)

/** 다른 앱에서 '공유'로 받은 파일(문서함 저장 대기용). */
data class PendingDoc(
    val uri: android.net.Uri,
    val name: String,
    val uploading: Boolean = false,
)

/**
 * 앱 전역 상태/동작 허브. 화면들은 이 VM 을 공유한다(activity 스코프).
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as FamilyBoardApp).container
    private val board = container.boardRepository
    private val holidayRepo = container.holidayRepository

    val currentMemberId: StateFlow<String?> =
        container.currentUserStore.currentMemberId
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val userState: StateFlow<UserState> =
        container.currentUserStore.currentMemberId
            .map { if (it.isNullOrBlank()) UserState.NeedSelect else UserState.Selected(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UserState.Loading)

    val events: StateFlow<List<CalendarEvent>> =
        board.events().stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    val shoppingItems: StateFlow<List<ListItem>> =
        board.items(BoardType.SHOPPING.key)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    val todoItems: StateFlow<List<ListItem>> =
        board.items(BoardType.TODO.key)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    // 가족 공지사항 (부모 전용)
    val noticeItems: StateFlow<List<ListItem>> =
        board.items(BoardType.NOTICE.key)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    // D-Day (카운트다운, 가족 모두)
    val ddayItems: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.DDayBoard.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    /** D-Day 항목 → '표시 전용' 캘린더 이벤트. 전역 events 엔 넣지 않음(알림·홈 보드 부작용 방지). */
    private fun ddayToEvent(item: ListItem): CalendarEvent? {
        if (item.dateIso.isBlank()) return null
        return CalendarEvent(
            id = "${com.familyboard.app.data.model.SyntheticEvent.DDAY}${item.id}", title = item.text,
            startDateIso = item.dateIso, endDateIso = item.dateIso, allDay = true,
            memberIds = listOf(Family.DDAY_ID),          // 달력에서 D-Day 전용색(주황)
            repeat = if (item.yearly) "yearly" else "",  // 매년 반복이면 올해·이후 해마다 표시
        )
    }
    val ddayCalendarEvents: StateFlow<List<CalendarEvent>> =
        ddayItems.map { list -> list.mapNotNull(::ddayToEvent) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    /** 가족 생일(내장, 영구) → 매년 반복 표시 이벤트(각자 색 + 🎂). 삭제 불필요·항상 표시. */
    private val birthdayEvents: List<CalendarEvent> = FamilyBirthdays.list.map { (id, date) ->
        CalendarEvent(
            id = "${com.familyboard.app.data.model.SyntheticEvent.BDAY}$id", title = "🎂 ${Family.nameOf(id)}",
            startDateIso = date.toString(), endDateIso = date.toString(), allDay = true,
            memberIds = listOf(id), repeat = "yearly",
        )
    }

    /** 가족 달력·위젯이 그릴 이벤트 = 일반 일정 + D-Day + 가족 생일(표시용). add/edit/delete 자동 반영. */
    val calendarEvents: StateFlow<List<CalendarEvent>> =
        combine(events, ddayCalendarEvents) { e, d -> e + d + birthdayEvents }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    // 사용자 커스텀 체크리스트 정의(board="customlists"). 본인만 화면에 표시.
    val customLists: StateFlow<List<ListItem>> =
        board.items("customlists")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    /** 임의 보드 키의 항목 스트림(커스텀 리스트 포함). */
    fun boardItems(boardKey: String): kotlinx.coroutines.flow.Flow<List<ListItem>> = board.items(boardKey)

    // 커스텀 리스트도 보드키별 공유 StateFlow 로 캐시(재방문 시 값 유지·매번 콜드 재구독 방지).
    private val boardItemsStates = mutableMapOf<String, StateFlow<List<ListItem>>>()
    fun boardItemsState(boardKey: String): StateFlow<List<ListItem>> =
        boardItemsStates.getOrPut(boardKey) {
            board.items(boardKey).stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())
        }

    // 장소 북마크 보드(맛집/가볼 곳). 초기값 null=아직 로딩 전(스피너), 빈 리스트=진짜 없음.
    val restaurantItems: StateFlow<List<ListItem>?> =
        board.items(com.familyboard.app.data.model.PlaceBoards.RESTAURANT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), null)
    val visitItems: StateFlow<List<ListItem>?> =
        board.items(com.familyboard.app.data.model.PlaceBoards.VISIT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), null)
    fun placeItems(boardKey: String): StateFlow<List<ListItem>?> =
        if (boardKey == com.familyboard.app.data.model.PlaceBoards.RESTAURANT) restaurantItems else visitItems

    // 가족 공유 문서함(pdf·이미지·docx·엑셀 등). 항목 수가 많지 않아 실시간 전체 조회 + 클라 정렬/권한필터.
    // null=로딩 전(스피너), 빈 리스트=없음.
    val docItems: StateFlow<List<ListItem>?> =
        board.items(com.familyboard.app.data.model.DocBoard.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), null)

    // 가족 쿠폰함(문자·링크·이미지 쿠폰). 항목 적어 실시간 전체 조회. null=로딩 전, 빈=없음.
    val couponItems: StateFlow<List<ListItem>?> =
        board.items(com.familyboard.app.data.model.CouponBoard.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), null)

    /** 쿠폰 상세 수정: 제목(text)·유효기간(dateIso, yyyy-MM-dd, 빈값=없음)·메모(description).
     *  공유한 사람(createdBy)·관리자만 화면에서 호출. */
    fun updateCoupon(id: String, text: String, dateIso: String, memo: String) = viewModelScope.launch {
        runCatching {
            board.updateFields(id, mapOf("text" to text.trim(), "dateIso" to dateIso, "description" to memo.trim()))
        }
    }

    /** 쿠폰 사용완료 토글: 미사용→사용완료(본인 기록). 이미 사용완료면 '누른 본인만' 취소 가능(남 것은 무동작). */
    fun toggleCouponUsed(item: ListItem) = viewModelScope.launch {
        val me = currentMemberId.value.orEmpty()
        if (me.isBlank()) return@launch
        runCatching {
            // checkedAt 는 쓰지 않음 — 장보기 3일 자동삭제 스윕(board=shopping)과 무관하게 유지.
            if (!item.checked) {
                board.updateFields(item.id, mapOf("checked" to true, "usedBy" to me))
            } else if (item.usedBy == me) {
                board.updateFields(item.id, mapOf("checked" to false, "usedBy" to ""))
            }
        }
    }

    // 여행 위시리스트(구글 지도 공유). 실시간 전체 조회. null=로딩 전, 빈=없음.
    val travelItems: StateFlow<List<ListItem>?> =
        board.items(com.familyboard.app.data.model.TravelBoard.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), null)

    /** 공유(구글 지도) → 여행 위시리스트 저장. pendingShare(isTravel) 에서 호출. */
    fun saveTravel() = viewModelScope.launch {
        val s = pendingShare.value ?: return@launch
        if (s.loading) return@launch
        val nm = s.name.trim().let { if (it.isBlank() || it.endsWith("중…")) "새 장소" else it }
        runCatching {
            board.upsertItem(
                ListItem(
                    board = com.familyboard.app.data.model.TravelBoard.BOARD,
                    text = nm, link = s.link, address = s.address, lat = s.lat, lng = s.lng,
                    createdBy = currentMemberId.value.orEmpty(), createdAt = System.currentTimeMillis(),
                ),
            )
        }
        pendingShare.value = null
    }

    /** 여행 위시리스트 '다녀옴'(checked) 토글. */
    fun toggleTravelVisited(item: ListItem) = viewModelScope.launch {
        runCatching { board.updateFields(item.id, mapOf("checked" to !item.checked)) }
    }

    /** 여행 위시리스트 편집: 장소명(text)·메모(description). */
    fun updateTravel(id: String, name: String, memo: String) = viewModelScope.launch {
        runCatching { board.updateFields(id, mapOf("text" to name.trim(), "description" to memo.trim())) }
    }

    // 가족/내 사진첩 — Firestore 대신 Postgres(notify REST). 페이지 누적 로드(전량 리스너 아님 → 읽기 급증 방지).
    // null=로딩 전(스피너), 빈 리스트=없음. 화면 진입 시 refreshAlbum() 으로 채운다.
    private val _albumItems = kotlinx.coroutines.flow.MutableStateFlow<List<ListItem>?>(null)
    val albumItems: StateFlow<List<ListItem>?> = _albumItems
    private val _myAlbumItems = kotlinx.coroutines.flow.MutableStateFlow<List<ListItem>?>(null)
    val myAlbumItems: StateFlow<List<ListItem>?> = _myAlbumItems

    private fun albumFlow(albumBoard: String) =
        if (albumBoard == com.familyboard.app.data.model.AlbumBoard.PRIVATE) _myAlbumItems else _albumItems

    /** 사진첩을 처음부터 다시 로드(페이지 누적, 최신 촬영순). 화면 진입/새로고침 시 호출. */
    fun refreshAlbum(albumBoard: String = com.familyboard.app.data.model.AlbumBoard.BOARD) = viewModelScope.launch {
        val flow = albumFlow(albumBoard)
        val isPrivate = albumBoard == com.familyboard.app.data.model.AlbumBoard.PRIVATE
        val owner = if (isPrivate) currentMemberId.value.orEmpty() else null
        if (isPrivate && owner.isNullOrBlank()) { flow.value = emptyList(); return@launch } // 소유자 미상 → 전체 조회 금지(유출 방지)
        val hadData = flow.value?.isNotEmpty() == true
        val acc = mutableListOf<ListItem>()
        var bt: Long? = null; var bid: String? = null
        var completed = false // 마지막 페이지까지 정상 도달?
        while (true) {
            val page = com.familyboard.app.notif.NotifyApi.albumList(albumBoard, 200, bt, bid, owner) ?: break // null=조회 실패
            acc += page.items
            if (!hadData) flow.value = acc.toList() // 첫 로드만 페이지 도착마다 점진 표시(최근분 먼저)
            if (page.nextTakenAt == null) { completed = true; break }
            bt = page.nextTakenAt; bid = page.nextId
        }
        // 정상 완료 → 전체 반영. 중간 실패면 기존 데이터를 부분결과로 덮어쓰지 않음(첫 로드로 데이터가 아예 없을 때만 표시).
        if (completed) flow.value = acc.toList()
        else if (flow.value == null) flow.value = acc.toList()
    }

    /** 좋아요/회전/댓글 등으로 바뀐 항목을 로컬 리스트에 반영(리스너 없으니 직접 갱신). */
    private fun patchAlbumLocal(item: ListItem) {
        for (flow in listOf(_albumItems, _myAlbumItems)) {
            val cur = flow.value ?: continue
            val idx = cur.indexOfFirst { it.id == item.id }
            if (idx >= 0) flow.value = cur.toMutableList().also { it[idx] = item }
        }
    }
    private fun removeAlbumLocal(id: String) {
        for (flow in listOf(_albumItems, _myAlbumItems)) {
            val cur = flow.value ?: continue
            if (cur.any { it.id == id }) flow.value = cur.filter { it.id != id }
        }
    }

    /** 월 태그: "yyyy-MM" → 태그 문자열. 사진첩 월 헤더 강조·검색용. 가족 사진첩=가족 모두 입력·수정. */
    val albumTags: StateFlow<Map<String, String>> =
        board.items(com.familyboard.app.data.model.AlbumBoard.TAG_BOARD)
            .map { list -> list.filter { it.dateIso.isNotBlank() && it.text.isNotBlank() }.associate { it.dateIso to it.text } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyMap())

    // 내 사진첩 월 태그(본인 것만).
    val myAlbumTags: StateFlow<Map<String, String>> =
        combine(board.items(com.familyboard.app.data.model.AlbumBoard.TAG_BOARD_PRIVATE), currentMemberId) { list, me ->
            list.filter { it.createdBy == me && it.dateIso.isNotBlank() && it.text.isNotBlank() }.associate { it.dateIso to it.text }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyMap())

    /** 월 태그 설정(빈 값이면 삭제). tagBoard=공용/개인. 개인은 id에 멤버 포함(사용자별 분리). */
    fun setAlbumTag(monthKey: String, tag: String, tagBoard: String = com.familyboard.app.data.model.AlbumBoard.TAG_BOARD) = viewModelScope.launch {
        if (monthKey.isBlank()) return@launch
        val me = currentMemberId.value.orEmpty()
        val id = if (tagBoard == com.familyboard.app.data.model.AlbumBoard.TAG_BOARD_PRIVATE) "${tagBoard}_${me}_$monthKey" else "${tagBoard}_$monthKey"
        val t = tag.trim()
        runCatching {
            if (t.isBlank()) board.deleteItem(id)
            else board.upsertItem(
                ListItem(
                    id = id, board = tagBoard, dateIso = monthKey, text = t,
                    createdBy = me, createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** 사진 메타: 촬영 시각 + GPS 좌표 + 지명. (방향은 서버 썸네일 Jimp가 EXIF 자동보정하므로 앱에서 회전 안 함) */
    private data class PhotoMeta(val takenAt: Long, val lat: Double, val lng: Double, val place: String)

    /** 사진첩에 사진 여러 장 업로드(원본 보존). 촬영 시각·장소를 EXIF에서 읽어 함께 저장.
     *  중복(같은 촬영시각이 이미 앨범에 있음)은 업로드하지 않고 건너뛴다. */
    fun addAlbumPhotos(uris: List<android.net.Uri>, albumBoard: String = com.familyboard.app.data.model.AlbumBoard.BOARD) = viewModelScope.launch {
        val me = currentMemberId.value.orEmpty()
        val cr = getApplication<android.app.Application>().contentResolver
        val isPrivate = albumBoard == com.familyboard.app.data.model.AlbumBoard.PRIVATE
        // 이미 앨범에 있는 촬영시각(같은 사진 재선택 방지). 연사(같은 시각·다른 사진)를 살리려고
        // 배치 내 중복은 촬영시각+파일크기 조합으로 판정.
        val existingTaken = ((if (isPrivate) myAlbumItems.value else albumItems.value) ?: emptyList())
            .mapNotNull { it.takenAt.takeIf { t -> t > 0 } }.toHashSet()
        val batchSeen = HashSet<String>()
        var added = 0
        var skipped = 0
        for (uri in uris) {
            runCatching {
                val bytes = withContext(Dispatchers.IO) { cr.openInputStream(uri)?.use { it.readBytes() } }
                    ?: return@runCatching
                val mime = cr.getType(uri) ?: ""
                val ext = when {
                    mime.contains("png") -> "png"; mime.contains("webp") -> "webp"
                    mime.contains("gif") -> "gif"; mime.contains("heic") || mime.contains("heif") -> "heic"
                    else -> "jpg"
                }
                val displayName = withContext(Dispatchers.IO) {
                    runCatching {
                        cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }.getOrNull()
                }.orEmpty()
                // EXIF·DATE_TAKEN 없으면 파일명(displayName)에서 촬영시각 보조 추출(서버/웹과 동일 규칙).
                val meta = withContext(Dispatchers.IO) { readPhotoMeta(bytes, uri, displayName) }
                if (meta.takenAt > 0 && existingTaken.contains(meta.takenAt)) { skipped++; return@runCatching } // 이미 앨범에 있음
                if (!batchSeen.add("${meta.takenAt}_${bytes.size}")) { skipped++; return@runCatching }         // 이 배치에서 완전 동일 사진
                val url = com.familyboard.app.notif.NotifyApi.uploadFile(bytes, ext) ?: return@runCatching
                val dateIso = java.time.Instant.ofEpochMilli(meta.takenAt)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                com.familyboard.app.notif.NotifyApi.albumAdd(
                    albumBoard, url, meta.takenAt, dateIso, me, meta.lat, meta.lng, meta.place, displayName,
                ) ?: return@runCatching
                added++
            }
        }
        if (uris.isNotEmpty()) {
            val msg = if (skipped > 0) "${added}장 추가 · 중복 ${skipped}장 제외" else "${added}장 추가했어요"
            android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_SHORT).show()
        }
        if (added > 0) refreshAlbum(albumBoard) // 서버 반영분 다시 로드
    }

    /** EXIF(촬영시각·GPS) 읽기. ExifInterface → MediaStore DATE_TAKEN → 파일명 → 현재 시각 순. */
    private fun readPhotoMeta(bytes: ByteArray, uri: android.net.Uri, displayName: String = ""): PhotoMeta {
        var takenAt = 0L
        var lat = 0.0
        var lng = 0.0
        // 1) 선택기가 준 바이트에서 EXIF(촬영시각·GPS). 방향은 서버 썸네일이 자동보정하므로 앱에선 읽지 않음.
        runCatching {
            val exif = androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes))
            takenAt = exif.dateTimeOriginal ?: exif.dateTimeDigitized ?: exif.dateTime ?: 0L
            exif.latLong?.let { lat = it[0]; lng = it[1] }
        }
        // 2) GPS가 없으면(선택기가 위치 제거) 원본 요청으로 재시도(ACCESS_MEDIA_LOCATION 필요, MediaStore uri일 때)
        if (lat == 0.0 && lng == 0.0 && android.os.Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val cr = getApplication<android.app.Application>().contentResolver
                val orig = android.provider.MediaStore.setRequireOriginal(uri)
                cr.openInputStream(orig)?.use { ins ->
                    val e2 = androidx.exifinterface.media.ExifInterface(ins)
                    e2.latLong?.let { lat = it[0]; lng = it[1] }
                    if (takenAt <= 0L) takenAt = e2.dateTimeOriginal ?: e2.dateTime ?: 0L
                }
            }
        }
        // 3) 촬영시각을 못 읽으면 갤러리 DATE_TAKEN
        if (takenAt <= 0L) {
            takenAt = runCatching {
                getApplication<android.app.Application>().contentResolver.query(
                    uri, arrayOf(android.provider.MediaStore.Images.Media.DATE_TAKEN), null, null, null
                )?.use { c ->
                    val i = c.getColumnIndex(android.provider.MediaStore.Images.Media.DATE_TAKEN)
                    if (c.moveToFirst() && i >= 0 && !c.isNull(i)) c.getLong(i) else 0L
                } ?: 0L
            }.getOrDefault(0L)
        }
        // 4) EXIF·DATE_TAKEN 다 없으면 원본 파일명에서(스캔·다운로드·메신저 저장 사진 등).
        if (takenAt <= 0L) takenAt = filenameTakenMs(displayName)
        if (takenAt <= 0L) takenAt = System.currentTimeMillis()
        val place = if (lat != 0.0 || lng != 0.0) reverseGeocodeShort(lat, lng) else ""
        return PhotoMeta(takenAt, lat, lng, place)
    }

    /** 원본 파일명에서 촬영시각 추출(EXIF·DATE_TAKEN 없을 때). 20160904_123145 / IMG_..._... / 2016-09-04 12.31.45.
     *  시각 없으면 그 날짜 정오. 못 찾거나 불가능한 날짜(2월31일 등)면 0. LocalDateTime.of가 무효날짜를 던져 검증됨. KST. */
    private fun filenameTakenMs(name: String): Long {
        if (name.isBlank()) return 0L
        val zone = java.time.ZoneId.systemDefault()
        Regex("(20\\d{2})[-_.]?(\\d{2})[-_.]?(\\d{2})[ _T-]?(\\d{2})[-_.:]?(\\d{2})[-_.:]?(\\d{2})").find(name)?.let { m ->
            val (y, mo, da, h, mi, s) = m.destructured
            runCatching {
                return java.time.LocalDateTime.of(y.toInt(), mo.toInt(), da.toInt(), h.toInt(), mi.toInt(), s.toInt())
                    .atZone(zone).toInstant().toEpochMilli()
            }
        }
        Regex("(20\\d{2})[-_.]?(\\d{2})[-_.]?(\\d{2})").find(name)?.let { m ->
            val (y, mo, da) = m.destructured
            runCatching {
                return java.time.LocalDate.of(y.toInt(), mo.toInt(), da.toInt()).atTime(12, 0)
                    .atZone(zone).toInstant().toEpochMilli()
            }
        }
        return 0L
    }

    /** GPS 좌표 → 짧은 지명(시/구·동). 실패 시 빈 문자열. */
    private fun reverseGeocodeShort(lat: Double, lng: Double): String = runCatching {
        val gc = android.location.Geocoder(getApplication(), java.util.Locale.KOREA)
        @Suppress("DEPRECATION")
        val list = gc.getFromLocation(lat, lng, 1)
        val a = list?.firstOrNull() ?: return ""
        listOfNotNull(a.locality ?: a.adminArea, a.subLocality ?: a.thoroughfare)
            .distinct().joinToString(" ").trim()
    }.getOrDefault("")

    /** 좌표 → 전체 주소 한 줄(여행 위시리스트용, 해외 포함). 실패 시 빈 문자열. */
    private fun reverseGeocodeFull(lat: Double, lng: Double): String = runCatching {
        val gc = android.location.Geocoder(getApplication(), java.util.Locale.KOREA)
        @Suppress("DEPRECATION")
        val a = gc.getFromLocation(lat, lng, 1)?.firstOrNull() ?: return ""
        (a.getAddressLine(0)
            ?: listOfNotNull(a.countryName, a.adminArea, a.locality, a.thoroughfare).joinToString(" ")).trim()
    }.getOrDefault("")

    /** 사진첩 좋아요 토글(서버가 토글 → 갱신된 항목 반영). */
    fun toggleAlbumLike(item: ListItem) = viewModelScope.launch {
        val me = currentMemberId.value.orEmpty()
        if (me.isBlank()) return@launch
        com.familyboard.app.notif.NotifyApi.albumLike(item.id, me)?.let { patchAlbumLocal(it) }
    }

    /**
     * 사진 회전: 원본은 그대로 두고 표시 각도(rotation)만 갱신 → 즉시 반영·화질 보존.
     * delta 90=시계방향, 왼쪽(반시계)=270. 화면은 이 각도로 썸네일을 돌려 표시, 공유·다운로드 시 원본에 적용.
     */
    fun rotateAlbumPhoto(item: ListItem, delta: Int) = viewModelScope.launch {
        val next = ((item.rotation + delta) % 360 + 360) % 360
        com.familyboard.app.notif.NotifyApi.albumUpdate(item.id, org.json.JSONObject().put("rotation", next))
            ?.let { patchAlbumLocal(it) }
    }

    /** 사진첩 댓글 추가/삭제/수정 — progress(ProgressNote)를 서버에서 갱신(장소 댓글과 별개, Postgres). */
    fun addAlbumComment(item: ListItem, text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        com.familyboard.app.notif.NotifyApi.albumComment(item.id, text.trim(), currentMemberId.value.orEmpty())
            ?.let { patchAlbumLocal(it) }
    }
    // 삭제·수정은 인덱스 기준 서버 조작(전체교체 아님) → 그 사이 추가된 타인 댓글이 유실되지 않음.
    fun deleteAlbumComment(item: ListItem, index: Int) = viewModelScope.launch {
        if (index !in item.progress.indices) return@launch
        com.familyboard.app.notif.NotifyApi.albumCommentDelete(item.id, index)?.let { patchAlbumLocal(it) }
    }
    fun updateAlbumComment(item: ListItem, index: Int, text: String) = viewModelScope.launch {
        if (index !in item.progress.indices || text.isBlank()) return@launch
        com.familyboard.app.notif.NotifyApi.albumCommentEdit(item.id, index, text.trim())?.let { patchAlbumLocal(it) }
    }

    /** 사진 삭제(메타데이터). */
    fun deleteAlbumPhoto(item: ListItem) = viewModelScope.launch {
        if (com.familyboard.app.notif.NotifyApi.albumDelete(item.id)) removeAlbumLocal(item.id)
    }

    // 홈 "오늘, 그날의 추억" — 앨범 전체를 안 읽고 오늘 MM-DD 사진만 서버에서 조회.
    private val _todayMemories = kotlinx.coroutines.flow.MutableStateFlow<List<ListItem>>(emptyList())
    val todayMemories: StateFlow<List<ListItem>> = _todayMemories
    fun loadTodayMemories() = viewModelScope.launch {
        val now = java.time.LocalDate.now()
        _todayMemories.value = com.familyboard.app.notif.NotifyApi.albumMemories(
            com.familyboard.app.data.model.AlbumBoard.BOARD, now.monthValue, now.dayOfMonth,
        )
    }

    // 리스트 화면 사진첩 카드 개수 — count 쿼리(전체 로드 없이).
    private val _albumCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val albumCountFlow: StateFlow<Int> = _albumCount
    private val _myAlbumCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val myAlbumCountFlow: StateFlow<Int> = _myAlbumCount
    fun loadAlbumCounts() = viewModelScope.launch {
        _albumCount.value = com.familyboard.app.notif.NotifyApi.albumCount(com.familyboard.app.data.model.AlbumBoard.BOARD, null)
        val me = currentMemberId.value.orEmpty()
        _myAlbumCount.value = if (me.isBlank()) 0 // 소유자 미상 → 전체 카운트 조회 금지
        else com.familyboard.app.notif.NotifyApi.albumCount(com.familyboard.app.data.model.AlbumBoard.PRIVATE, me)
    }

    /** 대상 목록에 같은 사진(=같은 파일 URL)이 이미 있는지. 촬영시각만 같은 별개 사진(연사·다른 카메라)을
     *  중복으로 오판해 원본을 삭제하지 않도록 URL 일치만 본다. */
    private fun albumListHasDup(target: List<ListItem>, item: ListItem): Boolean {
        val url = item.photoUrls.firstOrNull()
        if (url.isNullOrBlank()) return false
        return target.any { it.photoUrls.firstOrNull() == url }
    }

    /** 대상 앨범의 현재 항목 전부를 REST로 조회(중복 판정용). 내 사진첩은 본인 것만. 조회 실패 시 null. */
    private suspend fun targetAlbumItems(toBoard: String): List<ListItem>? {
        val owner = if (toBoard == com.familyboard.app.data.model.AlbumBoard.PRIVATE) currentMemberId.value.orEmpty() else null
        val first = com.familyboard.app.notif.NotifyApi.albumList(toBoard, 200, null, null, owner) ?: return null
        val acc = first.items.toMutableList()
        var bt = first.nextTakenAt; var bid = first.nextId
        while (bt != null) {
            val page = com.familyboard.app.notif.NotifyApi.albumList(toBoard, 200, bt, bid, owner) ?: break
            acc += page.items; bt = page.nextTakenAt; bid = page.nextId
        }
        return acc
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(getApplication(), msg, android.widget.Toast.LENGTH_SHORT).show()

    /** 사진을 다른 앨범으로 이동(board 변경). 대상에 이미 있으면 옮기지 않고 원본을 삭제. 내 사진첩으로 옮기면 소유자도 나로. */
    fun moveAlbumPhoto(item: ListItem, toBoard: String) = viewModelScope.launch {
        val target = targetAlbumItems(toBoard) ?: run { toast("잠시 후 다시 시도해 주세요"); return@launch }
        if (albumListHasDup(target, item)) {
            if (com.familyboard.app.notif.NotifyApi.albumDelete(item.id)) removeAlbumLocal(item.id)
            toast("이미 있는 사진이라 원본을 정리했어요")
            return@launch
        }
        val fields = org.json.JSONObject().put("board", toBoard)
        if (toBoard == com.familyboard.app.data.model.AlbumBoard.PRIVATE) fields.put("createdBy", currentMemberId.value.orEmpty())
        if (com.familyboard.app.notif.NotifyApi.albumUpdate(item.id, fields) != null) {
            removeAlbumLocal(item.id) // 현재 목록에서 빠짐(대상은 열 때 로드)
            toast("옮겼어요")
        } else toast("옮기기 실패")
    }

    /** 사진을 다른 앨범으로 복사(새 항목·같은 사진 파일, 소유자=나, 좋아요·댓글 초기화). 대상에 이미 있으면 복사 안 함. */
    fun copyAlbumPhoto(item: ListItem, toBoard: String) = viewModelScope.launch {
        val target = targetAlbumItems(toBoard) ?: run { toast("잠시 후 다시 시도해 주세요"); return@launch }
        if (albumListHasDup(target, item)) { toast("이미 있는 사진이에요"); return@launch }
        val me = currentMemberId.value.orEmpty()
        val added = com.familyboard.app.notif.NotifyApi.albumAdd(
            toBoard, item.photoUrls.firstOrNull().orEmpty(), item.takenAt, item.dateIso, me, item.lat, item.lng, item.address, item.fileName,
        )
        if (added != null) {
            if (albumFlow(toBoard).value != null) refreshAlbum(toBoard) // 대상이 이미 로드돼 있으면 갱신
            toast("복사했어요")
        } else toast("복사 실패")
    }

    /** 일괄 다운로드(화면 이탈해도 완료되도록 viewModelScope). 저장 로직은 화면의 saveAlbumToDownloads 재사용. */
    fun downloadAlbumOriginals(items: List<ListItem>) = viewModelScope.launch {
        if (items.isEmpty()) return@launch
        val ctx = getApplication<android.app.Application>()
        toast("${items.size}장 저장 중…")
        var ok = 0
        for (it in items) if (com.familyboard.app.ui.lists.saveAlbumToDownloads(ctx, it)) ok++
        toast("Download 폴더에 ${ok}/${items.size}장 저장했어요")
    }

    // 재미진 곳(유튜브/웹/이미지 게시판). 페이지 방식(이전/다음 + 이어보기).
    // 한 페이지 FUN_PAGE개. 페이지 상태/로딩은 화면(FunListScreen)이 관리하고,
    // VM 은 1회성 조회(fetchFunPage) · 전체 개수 · 마지막 본 페이지 저장만 담당.
    // BOARD=공용, PRIVATE=내것(쿼리에서 createdBy 로 본인 것만).
    private val userStore = container.currentUserStore

    /** 항목 추가/삭제 시 개수 다시 집계하기 위한 트리거. */
    private val funRefresh = MutableStateFlow(0)
    private fun bumpFunRefresh() { funRefresh.value++ }

    // 리스트 화면 카드에 표시할 "전체" 개수(집계 count, 페이지와 무관).
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val funCount: StateFlow<Int> =
        funRefresh.flatMapLatest {
            kotlinx.coroutines.flow.flow {
                emit(runCatching { board.countByBoard(com.familyboard.app.data.model.FunBoard.BOARD) }.getOrDefault(0))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myFunCount: StateFlow<Int> =
        combine(funRefresh, currentMemberId) { _, me -> me }
            .flatMapLatest { me ->
                kotlinx.coroutines.flow.flow {
                    emit(if (me.isNullOrBlank()) 0
                    else runCatching { board.countByBoard(com.familyboard.app.data.model.FunBoard.PRIVATE, me) }.getOrDefault(0))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recipeCount: StateFlow<Int> =
        funRefresh.flatMapLatest {
            kotlinx.coroutines.flow.flow {
                emit(runCatching { board.countByBoard(com.familyboard.app.data.model.FunBoard.RECIPE) }.getOrDefault(0))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), 0)

    fun funCountFor(boardKey: String): StateFlow<Int> = when (boardKey) {
        com.familyboard.app.data.model.FunBoard.PRIVATE -> myFunCount
        com.familyboard.app.data.model.FunBoard.RECIPE -> recipeCount
        else -> funCount
    }

    /** 페이지 방식 1회성 조회. [afterCreatedAt] 이후부터 [limit]개(최신순/등록순). 내것은 본인 것만. */
    suspend fun fetchFunPage(boardKey: String, ascending: Boolean, afterCreatedAt: Long?, limit: Int): List<ListItem> {
        val isPrivate = boardKey == com.familyboard.app.data.model.FunBoard.PRIVATE
        val createdBy = if (isPrivate) currentMemberId.value else null
        if (isPrivate && createdBy.isNullOrBlank()) return emptyList()
        return runCatching { board.pageByBoard(boardKey, limit, createdBy, ascending, afterCreatedAt) }.getOrDefault(emptyList())
    }

    /** 길찾기 기본 앱("항상" 선택). 빈 값=매번 선택창. */
    val navDefaultApp: StateFlow<String> =
        userStore.navDefaultApp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), "")
    fun setNavDefaultApp(key: String) = viewModelScope.launch { userStore.setNavDefaultApp(key) }

    /** 홈 배경 선택: "cork"(기본) / "family"(우리집 알림판 이미지). */
    val homeBackground: StateFlow<String> =
        userStore.homeBackground.stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), "cork")
    fun setHomeBackground(v: String) = viewModelScope.launch { userStore.setHomeBackground(v) }

    /** 마지막 본 페이지(1-based, 0=없음) 흐름/저장 — 방향별(최신순/등록순) 이어보기용. */
    fun lastFunPage(boardKey: String, ascending: Boolean): kotlinx.coroutines.flow.Flow<Int> =
        userStore.lastFunPage(boardKey, ascending)
    fun saveLastFunPage(boardKey: String, ascending: Boolean, page: Int) =
        viewModelScope.launch { userStore.setLastFunPage(boardKey, ascending, page) }

    /** 네이버 플레이스 등에서 공유받은 장소(저장 위치 선택 대기). */
    val pendingShare: MutableStateFlow<SharedPlace?> = MutableStateFlow(null)

    /** 다른 앱에서 '공유'로 받은 파일(문서함 저장 대기). */
    val pendingDoc: MutableStateFlow<PendingDoc?> = MutableStateFlow(null)

    /** 위젯 등에서 특정 화면으로 이동 요청(보드 키). AppNav 가 관찰해 이동 후 clear. */
    val pendingWidgetNav: MutableStateFlow<String?> = MutableStateFlow(null)
    fun requestWidgetNav(board: String) { pendingWidgetNav.value = board }
    fun clearWidgetNav() { pendingWidgetNav.value = null }

    /** 공유받은 파일(pdf·docx·엑셀 등) → 문서함 저장 확인 대기. */
    fun handleSharedDocument(uri: android.net.Uri) {
        val (name, _) = queryFileNameSize(getApplication<Application>().contentResolver, uri)
        pendingDoc.value = PendingDoc(uri, name.ifBlank { "문서" })
    }

    /** 대기 중인 공유 파일을 문서함에 업로드. onDone(성공, 실패사유). */
    fun savePendingDoc(onDone: (Boolean, String?) -> Unit) {
        val p = pendingDoc.value ?: return
        if (p.uploading) return
        pendingDoc.value = p.copy(uploading = true)
        addDocFromUri(p.uri) { ok, err -> pendingDoc.value = null; onDone(ok, err) }
    }

    fun clearPendingDoc() { pendingDoc.value = null }

    /** 공유 텍스트 처리. 쿠팡→장보기(바로 담기), 네이버 플레이스→장소, 그 외 링크→재미진 곳. 링크 인식 시 true. */
    fun handleSharedText(raw: String?, subject: String?): Boolean {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return false
        val url = Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', ']') ?: ""
        if (url.isBlank()) return false
        var name = text.replace(url, " ").split('\n').map { it.trim() }
            .firstOrNull { it.isNotBlank() && it != "[네이버지도]" }.orEmpty()
        if (name.isBlank()) name = subject?.trim().orEmpty()

        val allLinks = Regex("https?://\\S+").findAll(text)
            .map { it.value.trimEnd('.', ',', ')', ']', '"') }.distinct().toList()
        // 코코달인 → product_id 추출 후 서버가 상품명 파싱해 장보기에 담기(단·복수)
        val cocoIds = allLinks.filter { it.contains("cocodalin", ignoreCase = true) }
            .mapNotNull { Regex("product_id=(\\d+)").find(it)?.groupValues?.get(1) }.distinct()
        if (cocoIds.isNotEmpty()) { addCocodalinToShopping(cocoIds); return true }
        // 쿠팡 → 공유 텍스트/og:title 이름으로 바로 장보기(단·복수)
        val coupangLinks = allLinks.filter { it.contains("coupang", ignoreCase = true) || it.contains("coupa.ng", ignoreCase = true) }
        if (coupangLinks.isNotEmpty()) {
            if (coupangLinks.size == 1) addShoppingLink(coupangLinks[0], coupangNameFromText(text)) else addShoppingLinks(text, coupangLinks)
            return true
        }

        // 구글 지도 공유 → 여행 위시리스트(별도 카드). 서버가 좌표·장소명 파싱, 주소는 앱 역지오코딩.
        val isGoogleMap = url.contains("maps.app.goo.gl") || url.contains("google.com/maps") ||
            url.contains("goo.gl/maps") || url.contains("maps.google")
        if (isGoogleMap) {
            pendingShare.value = SharedPlace(name.ifBlank { "장소 불러오는 중…" }, url, loading = true, isTravel = true)
            viewModelScope.launch {
                val g = com.familyboard.app.notif.NotifyApi.parseGooglePlace(url)
                val cur = pendingShare.value ?: return@launch
                if (g != null && (g.name.isNotBlank() || g.lat != 0.0 || g.lng != 0.0)) {
                    val addr = withContext(Dispatchers.IO) {
                        if (g.lat != 0.0 || g.lng != 0.0) reverseGeocodeFull(g.lat, g.lng) else ""
                    }
                    pendingShare.value = cur.copy(
                        name = g.name.ifBlank { name.ifBlank { "새 장소" } }, address = addr,
                        lat = g.lat, lng = g.lng, loading = false, isTravel = true,
                    )
                } else pendingShare.value = cur.copy(name = if (name.isBlank()) "새 장소" else name, loading = false, isTravel = true)
            }
            return true
        }

        val isNaverPlace = url.contains("naver.me") || url.contains("map.naver.com") || url.contains("place.naver.com")
        if (isNaverPlace) {
            pendingShare.value = SharedPlace(name.ifBlank { "장소 불러오는 중…" }, url, loading = true, isFun = false)
            viewModelScope.launch {
                val info = com.familyboard.app.notif.NotifyApi.parsePlace(url)
                val cur = pendingShare.value ?: return@launch
                pendingShare.value = if (info != null && info.name.isNotBlank())
                    cur.copy(name = info.name, description = buildPlaceDesc(info), address = info.address,
                        category = info.category, image = info.image, naverScore = info.score ?: 0.0,
                        lat = info.lat ?: 0.0, lng = info.lng ?: 0.0, loading = false)
                else cur.copy(name = if (name.isBlank()) "새 장소" else name, loading = false)
            }
        } else {
            // 유튜브/웹 링크 → 재미진 곳. 유튜브는 공유 텍스트에 실제 영상 제목이 담겨올 때가 많지만,
            // ⚠️ 쇼츠 등은 공유 텍스트/제목(subject)이 "- YouTube"/"YouTube" 로 오기도 함 → 그건 무시하고
            //    서버 oEmbed 결과(쇼츠도 처리됨)를 쓴다. 반대로 서버가 실패하면 공유 텍스트를 쓴다.
            val isYoutube = url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)
            val sharedName = name.trim().trimEnd('-', '|', '·', '–', ' ').trim()
            fun useless(s: String) = s.isBlank() || s.trimStart('-', ' ').equals("youtube", ignoreCase = true)
            pendingShare.value = SharedPlace(sharedName.ifBlank { "불러오는 중…" }, url, loading = true, isFun = true)
            viewModelScope.launch {
                val info = com.familyboard.app.notif.NotifyApi.parseLink(url)
                val cur = pendingShare.value ?: return@launch
                val fetched = info?.title?.trim().orEmpty()
                val finalName = when {
                    isYoutube && !useless(sharedName) -> sharedName   // 유튜브: 쓸만한 공유 텍스트 제목 우선
                    !useless(fetched) -> fetched                      // 아니면 서버 oEmbed(쇼츠 포함)
                    !useless(sharedName) -> sharedName
                    else -> "링크"
                }
                pendingShare.value = cur.copy(name = finalName, image = info?.image.orEmpty(), loading = false, isFun = true)
            }
        }
        return true
    }

    /** 코코달인 product_id 목록 → 서버가 상품명 파싱 → 장보기에 전부 담기(요청 id마다 1개, 못 받으면 폴백 이름). */
    private fun addCocodalinToShopping(ids: List<String>) {
        viewModelScope.launch {
            val nameById = com.familyboard.app.notif.NotifyApi.cocoNames(ids).toMap() // id→name(서버가 준 것만)
            val me = currentMemberId.value.orEmpty()
            ids.forEach { id ->
                val nm = nameById[id]?.ifBlank { null } ?: "코코달인 상품 $id"
                runCatching {
                    board.upsertItem(ListItem(
                        text = nm.take(80),
                        link = "https://www.cocodalin.com/product_view.html?product_id=$id",
                        board = BoardType.SHOPPING.key, createdBy = me, createdAt = System.currentTimeMillis()))
                }
            }
            android.widget.Toast.makeText(getApplication(), "장보기에 ${ids.size}개 담았어요", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** 쇼핑 링크 1개(쿠팡) → 장보기 바로 추가. 이름=공유 텍스트 우선, 없으면 링크 og:title, 폴백 "상품". */
    private fun addShoppingLink(url: String, textName: String) {
        viewModelScope.launch {
            var name = cleanShopName(textName)
            if (name.isBlank()) name = cleanShopName(com.familyboard.app.notif.NotifyApi.parseLink(url)?.title.orEmpty())
            if (name.isBlank()) name = "상품"
            runCatching {
                board.upsertItem(ListItem(
                    text = name, link = url, board = BoardType.SHOPPING.key,
                    createdBy = currentMemberId.value.orEmpty(), createdAt = System.currentTimeMillis()))
            }
            android.widget.Toast.makeText(getApplication(), "장보기에 담았어요: $name", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** 쇼핑 링크 여러 개(한 텍스트) → 각 라인에서 이름 뽑아 장보기에 전부 담기. */
    private fun addShoppingLinks(fullText: String, links: List<String>) {
        val lines = fullText.split('\n')
        viewModelScope.launch {
            var count = 0
            for (link in links) {
                val line = lines.firstOrNull { it.contains(link.take(25)) }.orEmpty()
                var nm = cleanShopName(line.replace(Regex("https?://\\S+"), " ").trim())
                if (nm.isBlank()) nm = cleanShopName(com.familyboard.app.notif.NotifyApi.parseLink(link)?.title.orEmpty())
                if (nm.isBlank()) nm = "상품"
                runCatching {
                    board.upsertItem(ListItem(text = nm, link = link, board = BoardType.SHOPPING.key,
                        createdBy = currentMemberId.value.orEmpty(), createdAt = System.currentTimeMillis()))
                }
                count++
            }
            android.widget.Toast.makeText(getApplication(), "장보기에 ${count}개 담았어요", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 쿠팡 공유 텍스트에서 상품명 추출. 공유 형식은 보통 [프로모]\n[상품명]\n[링크].
     * 프로모 문구는 바뀔 수 있으니 문구 매칭에 의존하지 않고 **링크 바로 위 줄**을 상품명으로 본다.
     */
    private fun coupangNameFromText(fullText: String): String {
        val lines = fullText.split('\n').map { it.trim() }
        val urlIdx = lines.indexOfFirst { it.contains(Regex("https?://")) }
        val picked = if (urlIdx > 0) {
            (urlIdx - 1 downTo 0).map { lines[it] }.firstOrNull { it.isNotBlank() }.orEmpty()
        } else {
            // 링크가 첫 줄이거나 없음 → URL만 지운 마지막 텍스트 줄
            lines.map { it.replace(Regex("https?://\\S+"), "").trim() }.lastOrNull { it.isNotBlank() }.orEmpty()
        }
        return cleanShopName(picked)
    }

    /** 상품명 정리: og:title 등의 뒤쪽 "- 쿠팡!"/"- 코코달인" 꼬리만 제거(프로모는 위치 기반 추출로 이미 회피). */
    private fun cleanShopName(raw: String): String {
        val s = raw.trim().replace(Regex("\\s*[-|]\\s*(쿠팡|코코달인)!?\\s*$"), "").trim()
        return s.take(80)
    }
    private fun buildPlaceDesc(i: com.familyboard.app.notif.PlaceInfo): String {
        val lines = mutableListOf<String>()
        val head = buildString {
            if (i.category.isNotBlank()) append(i.category)
            if (i.score != null) {
                if (isNotEmpty()) append(" · ")
                append("★${i.score}")
                if (i.reviews != null) append(" (리뷰 ${i.reviews})")
            }
        }
        if (head.isNotBlank()) lines.add(head)
        if (i.hours.isNotBlank()) lines.add("영업 ${i.hours}")
        return lines.joinToString("\n")
    }
    fun savePlace(boardKey: String) {
        val s = pendingShare.value ?: return
        if (s.loading) return
        val nm = s.name.trim().let { if (it.isBlank() || it == "장소 불러오는 중…") "새 장소" else it }
        addPlace(boardKey, nm, s.link, s.description, s.address, s.image, s.naverScore, s.lat, s.lng, s.category)
        pendingShare.value = null
    }
    fun clearPendingShare() { pendingShare.value = null }

    fun saveFun(boardKey: String) {
        val s = pendingShare.value ?: return
        if (s.loading) return
        val photos = if (s.images.isNotEmpty()) s.images else if (s.image.isNotBlank()) listOf(s.image) else emptyList()
        val isImage = s.link.isBlank() && photos.isNotEmpty()
        val nm = s.name.trim().let { if (it.isBlank() || it.endsWith("중…")) (if (isImage) "이미지" else "링크") else it }
        addFun(boardKey, nm, s.link, photos)
        pendingShare.value = null
    }
    fun addFun(boardKey: String, title: String, link: String, photoUrls: List<String> = emptyList()) = viewModelScope.launch {
        runCatching {
            board.upsertItem(ListItem(text = title.trim(), link = link.trim(), photoUrls = photoUrls,
                board = boardKey, createdBy = currentMemberId.value.orEmpty(),
                createdAt = System.currentTimeMillis()))
        }
        bumpFunRefresh()
    }
    /** 공유받은 이미지 여러 장 → 한 항목으로(고화질 업로드). */
    fun handleSharedImages(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        if (uris.size == 1) { handleSharedImage(uris[0]); return }
        pendingShare.value = SharedPlace(name = "이미지 올리는 중…", link = "", loading = true, isFun = true)
        viewModelScope.launch {
            val urls = uris.mapNotNull { com.familyboard.app.notif.PhotoUploader.uploadImageHiQ(getApplication(), it) }
            val cur = pendingShare.value ?: return@launch
            pendingShare.value = if (urls.isNotEmpty()) cur.copy(name = "이미지 ${urls.size}장", images = urls, link = "", loading = false)
            else null
        }
    }
    /** 공유받은 영상(mp4 등) → 업로드 + 썸네일. 확장자는 실제 mime에서 판별(기본 mp4). */
    fun handleSharedVideo(uri: android.net.Uri, hintExt: String) {
        pendingShare.value = SharedPlace(name = "영상 올리는 중…", link = "", loading = true, isFun = true)
        viewModelScope.launch {
            val ext = resolveVideoExt(uri, hintExt)
            val videoUrl = com.familyboard.app.notif.PhotoUploader.uploadRaw(getApplication(), uri, ext)
            val cur = pendingShare.value ?: return@launch
            if (videoUrl == null) { pendingShare.value = null; return@launch }
            val thumb = com.familyboard.app.notif.PhotoUploader.uploadVideoThumb(getApplication(), uri).orEmpty()
            pendingShare.value = cur.copy(name = "공유 영상", link = videoUrl, image = thumb, loading = false)
        }
    }
    private fun resolveVideoExt(uri: android.net.Uri, hintExt: String): String {
        val valid = Regex("[a-z0-9]{1,5}")
        val cr = getApplication<android.app.Application>().contentResolver
        val fromMime = cr.getType(uri)?.let { android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return listOf(fromMime, hintExt).mapNotNull { it?.lowercase() }
            .firstOrNull { it.matches(valid) } ?: "mp4"
    }
    /** id로 항목 1건 조회(공유받은 항목 열기용). */
    suspend fun fetchItemById(id: String): ListItem? = runCatching { board.getItemById(id) }.getOrNull()

    /** 공유받은 재미진 항목 열기 대기 id(알림 탭 → 그 항목 자동 열림). FunListScreen 이 관찰 후 clear. */
    val pendingSharedFun = MutableStateFlow<String?>(null)
    fun requestOpenSharedFun(itemId: String) { pendingSharedFun.value = itemId }
    fun clearSharedFun() { pendingSharedFun.value = null }

    /** 재미진 곳/내 재미진 곳 항목을 선택한 가족에게 공유(FCM). 받는 사람이 탭하면 그 항목이 열린다. */
    fun shareFunToFamily(item: ListItem, targetIds: List<String>) = viewModelScope.launch {
        val actor = currentMemberId.value.orEmpty()
        val targets = targetIds.filter { it != actor }.distinct()
        if (targets.isEmpty() || item.id.isBlank()) return@launch
        runCatching {
            NotifyApi.notifyData(
                actor, targets, "🎬 재미진 항목 공유",
                "${Family.nameOf(actor)}님이 재미진 항목을 공유했습니다. 확인해 보세요.",
                mapOf("type" to "funshare", "itemId" to item.id),
            )
        }
    }

    /** 재미진 곳/내 재미진 곳 항목을 다른 보드로 '이동'(복사 아님 — 같은 항목의 board 만 변경). 요리법 이동에 사용. */
    fun moveFunTo(item: ListItem, targetBoard: String) = viewModelScope.launch {
        // 같은 id 유지 → board 변경 = 이동. createdAt 을 now 로 갱신해 대상 목록 최상단에.
        // 사용상태(checked/usedBy/checkedAt) 초기화 — 쿠폰함으로 옮길 때 '사용완료'가 딸려오지 않게.
        runCatching {
            board.upsertItem(item.copy(
                board = targetBoard, createdAt = System.currentTimeMillis(),
                checked = false, usedBy = "", checkedAt = 0,
            ))
        }
        bumpFunRefresh()
    }

    /** 재미진 곳 항목을 다른 재미진 곳 보드(공용/내것)로 복사. */
    fun copyFunTo(item: ListItem, targetBoard: String) = viewModelScope.launch {
        runCatching {
            board.upsertItem(ListItem(
                text = item.text, link = item.link, photoUrls = item.photoUrls,
                board = targetBoard, createdBy = currentMemberId.value.orEmpty(),
                createdAt = System.currentTimeMillis()))
        }
        bumpFunRefresh()
    }
    /** 재미진 곳 항목을 현재 사용자가 봤다고 표시(중복 방지, arrayUnion). */
    fun markFunViewed(item: ListItem) {
        val me = currentMemberId.value.orEmpty()
        if (me.isBlank() || item.viewedBy.contains(me)) return
        viewModelScope.launch { runCatching { board.markViewed(item.id, me) } }
    }

    /** 홈 공지 강조를 현재 사용자가 '확인'했다고 표시(viewedBy arrayUnion). markFunViewed 와 동일 로직. */
    fun markNoticeSeen(item: ListItem) = markFunViewed(item)
    fun updateFun(item: ListItem, title: String, link: String, image: String = item.photoUrls.firstOrNull().orEmpty()) = viewModelScope.launch {
        // 여러 장 묶음은 대표 이미지가 그대로면 전체 보존, 바뀌면 그 이미지로 대체
        val photos = when {
            image.isBlank() -> item.photoUrls
            item.photoUrls.size > 1 && item.photoUrls.firstOrNull() == image -> item.photoUrls
            else -> listOf(image)
        }
        runCatching {
            board.updateFields(item.id, mapOf("text" to title.trim(), "link" to link.trim(), "photoUrls" to photos))
        }
    }
    /** 공유받은 이미지 → 서버 업로드(고화질) 후 저장 대기(재미진 곳). */
    fun handleSharedImage(uri: android.net.Uri) {
        pendingShare.value = SharedPlace(name = "이미지 올리는 중…", link = "", loading = true, isFun = true)
        viewModelScope.launch {
            val url = com.familyboard.app.notif.PhotoUploader.uploadImageHiQ(getApplication(), uri)
            val cur = pendingShare.value ?: return@launch
            pendingShare.value = if (url != null) cur.copy(name = "공유 이미지", image = url, link = "", loading = false, isFun = true)
            else null // 업로드 실패 시 대기 취소
        }
    }

    // ---- 가족 공유 문서함 ----
    private val MAX_DOC_BYTES = 58_000_000 // 서버 /uploadfile 한도(60MB) 이내 여유

    /** 파일 URI 업로드 후 문서함 항목 생성(제목=파일명, 열람=모두). onDone(성공, 실패사유). */
    fun addDocFromUri(uri: android.net.Uri, onDone: (Boolean, String?) -> Unit) = viewModelScope.launch {
        val cr = getApplication<Application>().contentResolver
        val (rawName, size) = queryFileNameSize(cr, uri)
        val mime = cr.getType(uri).orEmpty()
        val name = rawName.ifBlank { "문서" }
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { cr.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull()
        }
        if (bytes == null) { onDone(false, "파일을 읽을 수 없어요"); return@launch }
        if (bytes.size > MAX_DOC_BYTES) { onDone(false, "58MB 이하 파일만 올릴 수 있어요"); return@launch }
        val url = com.familyboard.app.notif.NotifyApi.uploadFile(bytes, docExt(name, mime))
        if (url.isNullOrBlank()) { onDone(false, "업로드 실패(네트워크 확인)"); return@launch }
        val ok = runCatching {
            board.upsertItem(ListItem(
                text = titleFromFileName(name), board = com.familyboard.app.data.model.DocBoard.BOARD,
                createdBy = currentMemberId.value.orEmpty(),
                memberIds = listOf(Family.ALL_ID),
                photoUrls = listOf(url), fileName = name, fileMime = mime, fileSize = size,
                createdAt = System.currentTimeMillis()))
        }.isSuccess
        onDone(ok, if (ok) null else "저장 실패")
    }

    /** 파일명에서 확장자를 뺀 기본 제목. (예: "가족여행.pdf" → "가족여행") */
    private fun titleFromFileName(name: String): String =
        name.substringBeforeLast('.', name).ifBlank { name }

    /** 문서 제목·열람 대상 수정(올린이/관리자). viewerIds 비거나 all 포함이면 모두 공개. */
    fun updateDoc(item: ListItem, title: String, viewerIds: List<String>) = viewModelScope.launch {
        val cleanTitle = title.trim().ifBlank { titleFromFileName(item.fileName).ifBlank { "문서" } }
        val viewers = if (viewerIds.isEmpty() || viewerIds.contains(Family.ALL_ID)) listOf(Family.ALL_ID) else viewerIds
        runCatching { board.updateFields(item.id, mapOf("text" to cleanTitle, "memberIds" to viewers)) }
    }

    private fun queryFileNameSize(cr: android.content.ContentResolver, uri: android.net.Uri): Pair<String, Long> {
        var name = ""; var size = 0L
        runCatching {
            cr.query(uri, null, null, null, null)?.use { c ->
                val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (ni >= 0) name = c.getString(ni).orEmpty()
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        return name to size
    }

    private fun docExt(name: String, mime: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase()
        if (fromName.isNotBlank() && fromName.length <= 5 && fromName.all { it.isLetterOrDigit() }) return fromName
        return android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase() ?: "bin"
    }

    fun addPlace(boardKey: String, name: String, link: String, description: String = "", address: String = "",
                 image: String = "", naverScore: Double = 0.0, lat: Double = 0.0, lng: Double = 0.0,
                 category: String = "") = viewModelScope.launch {
        if (name.isBlank()) return@launch
        runCatching {
            board.upsertItem(ListItem(text = name.trim(), link = link.trim(), description = description,
                address = address, category = category, photoUrls = if (image.isBlank()) emptyList() else listOf(image),
                naverScore = naverScore, lat = lat, lng = lng,
                board = boardKey, createdBy = currentMemberId.value.orEmpty()))
        }
    }
    fun updatePlace(item: ListItem, name: String, link: String,
                    description: String = item.description, address: String = item.address,
                    image: String = item.photoUrls.firstOrNull().orEmpty(),
                    category: String = item.category) = viewModelScope.launch {
        runCatching {
            board.updateFields(item.id, mapOf(
                "text" to name.trim(), "link" to link.trim(), "description" to description, "address" to address,
                "category" to category,
                "photoUrls" to (if (image.isBlank()) emptyList<String>() else listOf(image)),
            ))
        }
    }
    /** 편집 다이얼로그에서 네이버 링크로 정보 가져오기(콜백으로 결과 전달). */
    fun fetchPlaceInfo(url: String, onResult: (com.familyboard.app.notif.PlaceInfo?) -> Unit) = viewModelScope.launch {
        onResult(com.familyboard.app.notif.NotifyApi.parsePlace(url))
    }
    /** '놓친 장소' 발굴 추천(저장된 곳 제외). region 비면(전체) 현재 시군구로 한정. radius 지정 시 '근처' 반경 모드(역지오코딩 생략). */
    fun recommendPlace(
        board: String, category: String, region: String, savedNames: List<String>, lat: Double?, lng: Double?,
        radius: Int? = null,
        onResult: (List<com.familyboard.app.notif.Recommendation>) -> Unit,
    ) = viewModelScope.launch {
        val effectiveRegion =
            if (radius == null && region.isBlank() && lat != null && lng != null) reverseDistrict(lat, lng).orEmpty() else region
        onResult(com.familyboard.app.notif.NotifyApi.recommend(board, category, effectiveRegion, savedNames, lat, lng, radius))
    }

    /** 좌표 → 현재 시군구 문자열(예: "고양시 일산동구"). 실패 시 null. */
    private suspend fun reverseDistrict(lat: Double, lng: Double): String? =
        kotlinx.coroutines.withTimeoutOrNull(4000) {
            runCatching {
                val geo = android.location.Geocoder(getApplication<Application>(), java.util.Locale.KOREA)
                val addr = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    kotlinx.coroutines.suspendCancellableCoroutine<android.location.Address?> { cont ->
                        geo.getFromLocation(lat, lng, 1, object : android.location.Geocoder.GeocodeListener {
                            override fun onGeocode(results: MutableList<android.location.Address>) { if (cont.isActive) cont.resume(results.firstOrNull()) }
                            override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                        })
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        @Suppress("DEPRECATION") geo.getFromLocation(lat, lng, 1)?.firstOrNull()
                    }
                } ?: return@runCatching null
                val line = addr.getAddressLine(0)
                    ?: listOfNotNull(addr.adminArea, addr.locality, addr.subLocality).joinToString(" ")
                districtFrom(line)
                    ?: listOfNotNull(addr.locality ?: addr.subAdminArea, addr.subLocality)
                        .filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
            }.getOrNull()
        }

    /** 주소 문자열에서 '시 + 구/군'만 추출(예: "…경기도 고양시 일산동구 백석동…" → "고양시 일산동구"). */
    private fun districtFrom(addressLine: String): String? {
        val toks = addressLine.split(' ', ',').map { it.trim() }.filter { it.isNotBlank() }
        val si = toks.firstOrNull { it.endsWith("시") }
        val guGun = toks.firstOrNull { it.endsWith("구") || it.endsWith("군") }
        return listOfNotNull(si, guGun).distinct().joinToString(" ").ifBlank { null }
    }
    fun describePlace(info: com.familyboard.app.notif.PlaceInfo): String = buildPlaceDesc(info)
    fun fetchLinkInfo(url: String, onResult: (com.familyboard.app.notif.LinkInfo?) -> Unit) = viewModelScope.launch {
        onResult(com.familyboard.app.notif.NotifyApi.parseLink(url))
    }
    fun setPlaceRating(item: ListItem, rating: Int) = viewModelScope.launch {
        runCatching { board.upsertItem(item.copy(rating = rating.toLong().coerceIn(0L, 5L))) }
    }
    // 댓글(progress)은 필드 단위로만 갱신 — 전체 문서 set로 덮으면 동시에 눌린 좋아요/회전/별점이
    // stale 스냅샷으로 사라짐(사진첩은 한 문서에 likes·rotation·progress 공존).
    fun addPlaceComment(item: ListItem, text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        val note = com.familyboard.app.data.model.ProgressNote(
            text = text.trim(), by = currentMemberId.value.orEmpty(), dateIso = LocalDate.now().toString())
        runCatching { board.updateFields(item.id, mapOf("progress" to (item.progress + note))) }
    }
    fun deletePlaceComment(item: ListItem, index: Int) = viewModelScope.launch {
        if (index < 0 || index >= item.progress.size) return@launch
        runCatching { board.updateFields(item.id, mapOf("progress" to item.progress.filterIndexed { i, _ -> i != index })) }
    }
    fun updatePlaceComment(item: ListItem, index: Int, text: String) = viewModelScope.launch {
        if (index < 0 || index >= item.progress.size || text.isBlank()) return@launch
        val updated = item.progress.mapIndexed { i, n -> if (i == index) n.copy(text = text.trim()) else n }
        runCatching { board.updateFields(item.id, mapOf("progress" to updated)) }
    }

    val allowanceJunyoung: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.AllowanceBoards.JUNYOUNG)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    val allowanceJunho: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.AllowanceBoards.JUNHO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    // 인생 버킷 (부부 공용, 단일 보드)
    val bucketItems: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.BucketBoards.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(60_000), emptyList())

    fun bucketById(id: String): ListItem? = bucketItems.value.firstOrNull { it.id == id }

    /** 사용 가능한 앱 업데이트(없으면 null) */
    val updateInfo: MutableStateFlow<UpdateInfo?> = MutableStateFlow(null)

    /** dateIso -> 공휴일명 */
    val holidays: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    private val loadedMonths = mutableSetOf<String>()

    init {
        // 하단탭·홈 핵심(작은) 데이터만 예열 → 첫 진입 스피너 제거. ⚠️ 사진첩(album)·문서함(docs)처럼
        // 커질 수 있는 컬렉션은 예열에서 제외(앱 켤 때마다 전체를 읽으면 Firestore 읽기 쿼타 폭증) → 화면 열 때 로드.
        listOf(
            events, calendarEvents, noticeItems, ddayItems, shoppingItems, todoItems,
            restaurantItems, visitItems, allowanceJunyoung, allowanceJunho,
        ).forEach { it.launchIn(viewModelScope) }

        checkUpdateChallenge() // 앱 업데이트 챌린지: 업데이트 후 재시작 시 보상 지급 판정

        // 일정/담당자 변경 시 미리 알림 예약 동기화
        viewModelScope.launch {
            combine(events, currentMemberId) { evs, mid -> evs to mid }.collect { (evs, mid) ->
                ReminderScheduler.reconcile(getApplication(), evs, mid)
            }
        }
        // D-Day/생일 알림 예약 (앱 실행/항목 변경 시 다음 회차 재예약)
        viewModelScope.launch {
            combine(ddayItems, currentMemberId) { items, mid -> items to mid }.collect { (items, mid) ->
                com.familyboard.app.notif.DDayReminderScheduler.reconcile(getApplication(), items, mid)
            }
        }
        // 본인 확정 시 FCM 토큰을 서버에 등록 (등록 알림 수신용)
        viewModelScope.launch {
            currentMemberId.filterNotNull().distinctUntilChanged().collect { mid ->
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    NotifyApi.register(mid, token)
                }
                // Home Assistant 기기정보 5분 주기 리포트 예약(+즉시 1회)
                runCatching {
                    com.familyboard.app.notif.HaReporter.report(getApplication(), mid)
                    com.familyboard.app.notif.HaReportScheduler.schedule(getApplication())
                }
                // 접속 현황 기록(관리자 확인용): 마지막 접속 시각 + 앱 버전
                runCatching {
                    board.updatePresence(com.familyboard.app.data.model.Presence(
                        memberId = mid, lastSeen = System.currentTimeMillis(),
                        versionName = com.familyboard.app.BuildConfig.VERSION_NAME,
                        versionCode = com.familyboard.app.BuildConfig.VERSION_CODE,
                    ))
                }
            }
        }
        // 앱 업데이트 확인은 '홈 메뉴로 이동할 때'마다 수행(AppNav 에서 route==HOME 관찰 → refreshUpdate). 주기 폴링 안 함.
        // 장바구니 위젯: 장보기 미체크(살) 항목 수 갱신
        viewModelScope.launch {
            shoppingItems.collect { items ->
                com.familyboard.app.widget.ShoppingWidget.setCount(getApplication(), items.count { !it.checked })
            }
        }
        // 가족 달력 위젯: 이번 달(±1) 공휴일 로드 후, 일정/공휴일 변경 시 위젯 데이터 재계산
        viewModelScope.launch {
            val ym = YearMonth.now()
            loadHolidayDiskCache(ym) // 디스크 캐시 즉시 반영(콜드스타트에 공휴일 바로 표시), 지난 달은 재조회 생략
            ensureHolidays(ym.minusMonths(1)); ensureHolidays(ym); ensureHolidays(ym.plusMonths(1))
            combine(calendarEvents, holidays) { e, h -> e to h }.collect { (e, h) ->
                com.familyboard.app.widget.CalendarWidgetData.update(getApplication(), e, h)
            }
        }
    }

    /** 홈 화면 진입 등에서 업데이트를 다시 확인. 새 버전을 찾을 때만 설정(실패/최신이면 기존 상태 유지). */
    fun refreshUpdate() = viewModelScope.launch { UpdateChecker.check()?.let { updateInfo.value = it } }

    /** 가족 접속 현황(관리자 화면 전용). 화면 진입 시 refreshPresence 로 최신화. */
    val presence: MutableStateFlow<List<com.familyboard.app.data.model.Presence>> = MutableStateFlow(emptyList())
    /** 설치 이력(FCM 토큰 등록된) member id — '한 번도 설치 안 한 사람' 제외용. */
    val installedMembers: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    fun refreshPresence() = viewModelScope.launch {
        runCatching { presence.value = board.getPresence() }
        runCatching { installedMembers.value = NotifyApi.registeredMembers() }
    }

    /** 관리자 → 특정 가족에게 앱 업데이트 요청 알림(FCM). 탭하면 앱에서 바로 업데이트 창이 뜬다. */
    fun sendUpdateRequest(memberId: String) = viewModelScope.launch {
        val actor = currentMemberId.value.orEmpty()
        val notes = com.familyboard.app.notif.UpdateChecker.latestNotes() // version.json notes = 그 릴리스 개선사항
        val body = buildString {
            append("앱 업데이트가 가능합니다.\n")
            append("이 알림을 누르면 바로 업데이트됩니다.")
            if (notes.isNotBlank()) { append("\n\n주요 개선\n"); append(notes) }
        }
        runCatching {
            NotifyApi.notifyData(
                actor, listOf(memberId), "⬆️ 앱 업데이트 안내", body,
                mapOf("type" to "updatereq"), // 탭 → 앱에서 업데이트 창 자동 표시
            )
        }
    }

    /** 업데이트 요청 알림 탭 → 홈에서 업데이트 창 자동 표시 대기. HomeScreen 이 관찰 후 clear. */
    val pendingOpenUpdate = MutableStateFlow(false)
    fun requestOpenUpdate() { pendingOpenUpdate.value = true }
    fun clearOpenUpdate() { pendingOpenUpdate.value = false }

    // ─────────── 앱 업데이트 챌린지(용돈 미션) ───────────
    /** 관리자 → 아이에게 "업데이트 하면 용돈" 미션 알림(FCM). 탭 → 업데이트 → 완료 시 용돈 자동 지급. */
    fun sendUpdateChallenge(memberId: String, reward: Int = 2000) = viewModelScope.launch {
        val actor = currentMemberId.value.orEmpty()
        val body = "앱 업데이트 하면 즉시 용돈 ${"%,d".format(reward)}원! 🎉\n이 알림을 누르면 바로 시작돼요."
        runCatching {
            NotifyApi.notifyData(
                actor, listOf(memberId), "🎮 용돈 미션 챌린지!", body,
                mapOf("type" to "updatechallenge", "reward" to reward.toString()),
            )
        }
    }

    /** 아이가 챌린지 알림을 탭했을 때: 수락 상태(현재 버전·보상)를 저장하고 업데이트 창을 띄운다.
     *  DataStore 에 저장하므로 앱 업데이트(재설치) 후 새 버전이 checkUpdateChallenge 로 보상 지급. */
    fun acceptUpdateChallenge(reward: Int) = viewModelScope.launch {
        userStore.setUpdateChallenge(com.familyboard.app.BuildConfig.VERSION_CODE, reward, System.currentTimeMillis())
        requestOpenUpdate()
    }

    /** 챌린지 성공 → 용돈 화면으로 이동 + 토스트 대기. AppNav 가 관찰 후 clear. */
    val pendingChallengeSuccess = MutableStateFlow(false)
    fun clearChallengeSuccess() { pendingChallengeSuccess.value = false }

    /** 앱 시작 시(=업데이트 후 재시작 포함) 챌린지 완료 판정. 버전이 수락 시점보다 올랐으면
     *  본인 용돈에 '수행완료' 항목+보상 추가 후 용돈 화면 이동/토스트. 중복 지급 방지로 먼저 clear. */
    private fun checkUpdateChallenge() = viewModelScope.launch {
        val ch = userStore.updateChallengeOnce() ?: return@launch
        // 만료: 수락 후 14일 지나도록 완료 안 됐으면 폐기(무관한 미래 업데이트에 지급되는 것 방지).
        if (ch.acceptedAt > 0L && System.currentTimeMillis() - ch.acceptedAt > 14L * 24 * 3600 * 1000) {
            userStore.clearUpdateChallenge(); return@launch
        }
        if (com.familyboard.app.BuildConfig.VERSION_CODE <= ch.fromVersion) return@launch // 아직 업데이트 전 → 대기 유지
        val meId = userStore.currentMemberOnce().orEmpty()
        if (meId.isBlank()) return@launch // 본인 미선택 → 다음 실행에 처리(대기 유지)
        if (!Family.isChild(meId)) { userStore.clearUpdateChallenge(); return@launch }
        val allowBoard = "allowance_$meId"
        val today = java.time.LocalDate.now()
        // ⚠️ 지급을 먼저(성공 확인) 하고 나서 정리·성공표시. 실패 시 플래그 유지 → 다음 실행에 재시도.
        //    id 를 결정적으로 줘서 set 재시도해도 같은 문서 덮어쓰기(중복 지급 없음).
        val ok = runCatching {
            board.upsertItem(
                ListItem(
                    id = "updchallenge_${meId}_${ch.fromVersion}",
                    text = "${today.monthValue}월 ${today.dayOfMonth}일 - 앱 업데이트 챌린지 수행완료!",
                    board = allowBoard, createdBy = meId, amount = ch.reward.toLong(), checked = false,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }.isSuccess
        if (ok) {
            userStore.clearUpdateChallenge()
            pendingChallengeSuccess.value = true // 용돈 화면 이동 + 성공 토스트
        }
    }

    fun itemsFor(boardKey: String): StateFlow<List<ListItem>> = when (boardKey) {
        BoardType.TODO.key -> todoItems
        BoardType.NOTICE.key -> noticeItems
        else -> shoppingItems
    }

    fun selectMember(id: String) = viewModelScope.launch {
        container.currentUserStore.setCurrentMember(id)
    }

    fun ensureHolidays(month: YearMonth) = viewModelScope.launch {
        val key = month.toString()
        if (!loadedMonths.add(key)) return@launch
        val fetched = holidayRepo.holidays(month.year, month.monthValue)
        if (fetched.isNotEmpty()) {
            holidays.value = holidays.value + fetched.associate { it.dateIso to it.name }
            saveHolidayDiskCacheAsync() // 조회 성공분을 디스크에 반영(다음 콜드스타트에 즉시 표시)
        }
    }

    /** 공휴일 디스크 캐시 로드: 저장분을 즉시 표시하고, '지난 달'은 확정이라 재조회를 생략한다.
     *  (현재·미래 달은 대체공휴일 등 늦은 변경 가능 → loadedMonths에 안 넣어 재조회로 최신화.) */
    private fun loadHolidayDiskCache(current: YearMonth) {
        runCatching {
            val f = java.io.File(getApplication<android.app.Application>().filesDir, "holidays_cache.json")
            if (!f.exists()) return
            val o = org.json.JSONObject(f.readText())
            o.optJSONObject("days")?.let { days ->
                val map = mutableMapOf<String, String>()
                days.keys().forEach { map[it] = days.getString(it) }
                if (map.isNotEmpty()) holidays.value = holidays.value + map
            }
            o.optJSONArray("months")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val m = arr.getString(i)
                    val ym = runCatching { YearMonth.parse(m) }.getOrNull()
                    if (ym != null && ym < current) loadedMonths.add(m) // 지난 달 = 확정 → 재조회 안 함
                }
            }
        }.onFailure { android.util.Log.w("HolidayCache", "load 실패", it) }
    }

    /** 현재 holidays/loadedMonths 스냅샷을 IO 스레드에서 파일로 저장(작은 JSON). */
    private fun saveHolidayDiskCacheAsync() {
        val months = loadedMonths.toList() // main 스냅샷
        val days = holidays.value          // 불변 맵 스냅샷
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val o = org.json.JSONObject()
                o.put("months", org.json.JSONArray(months))
                val d = org.json.JSONObject()
                days.forEach { (k, v) -> d.put(k, v) }
                o.put("days", d)
                java.io.File(getApplication<android.app.Application>().filesDir, "holidays_cache.json").writeText(o.toString())
            }.onFailure { android.util.Log.w("HolidayCache", "save 실패", it) }
        }
    }

    fun addEvent(event: CalendarEvent, notifyTargets: List<String> = emptyList()) = viewModelScope.launch {
        runCatching { board.upsertEvent(event) }
        notifyEventRegistered(event, notifyTargets)
    }

    /** 등록 알림: 사용자가 명시적으로 선택한 가족에게만 발송(기본은 없음, 등록자 제외). */
    private suspend fun notifyEventRegistered(e: CalendarEvent, notifyTargets: List<String>) {
        val actor = e.createdBy
        val targets = notifyTargets.filter { it != actor }.distinct()
        if (targets.isEmpty()) return
        val title = "📌 ${Family.nameOf(actor)}님이 일정을 등록했어요"
        val body = "\"${e.title}\"\n📅 ${eventWhenText(e)}"
        runCatching { NotifyApi.notify(actor, targets, title, body) }
    }
    fun updateEvent(event: CalendarEvent) = viewModelScope.launch { runCatching { board.upsertEvent(event) } }
    fun deleteEvent(id: String) = viewModelScope.launch { runCatching { board.deleteEvent(id) } }

    /** 반복 일정에서 특정 날짜만 제외(그 회차만 삭제) */
    fun excludeOccurrence(event: CalendarEvent, dateIso: String) = viewModelScope.launch {
        if (!event.exdates.contains(dateIso)) {
            runCatching { board.upsertEvent(event.copy(exdates = event.exdates + dateIso)) }
        }
    }

    fun eventById(id: String): CalendarEvent? = events.value.firstOrNull { it.id == id }

    fun addItem(item: ListItem) = viewModelScope.launch { runCatching { board.upsertItem(item) } }
    fun updateItem(item: ListItem) = viewModelScope.launch { runCatching { board.upsertItem(item) } }

    /** 커스텀 리스트 삭제: 자식 항목 먼저 삭제 후 정의 문서 삭제(중간 실패 시 정의가 남아 재시도 가능, 고아 방지). */
    fun deleteCustomList(listId: String) = viewModelScope.launch {
        runCatching { board.deleteByBoard(listId); board.deleteItem(listId) }
    }

    /** 할 일 추가 + 태깅된 담당자에게 등록 알림(등록자 제외). 장보기 등 다른 보드는 사용하지 않음. */
    fun addTodoWithNotify(item: ListItem) = viewModelScope.launch {
        runCatching { board.upsertItem(item) }
        val actor = item.createdBy
        val targets = (if (item.memberIds.contains(Family.ALL_ID)) Family.members.map { it.id } else item.memberIds)
            .filter { it != actor }
            .distinct()
        if (targets.isEmpty()) return@launch
        val body = "\"${item.text}\" 할 일이 등록됐어요\n등록: ${Family.nameOf(actor)}"
        runCatching { NotifyApi.notify(actor, targets, "✅ 새 할 일", body) }
    }

    /** 공지 추가 + 가족(작성자 제외) 전원에게 등록 알림. 공지는 부모(선일/은선)만 올림. */
    fun addNoticeWithNotify(item: ListItem) = viewModelScope.launch {
        runCatching { board.upsertItem(item) }
        val actor = item.createdBy
        val targets = Family.members.map { it.id }.filter { it != actor }.distinct()
        if (targets.isEmpty()) return@launch
        val body = "\"${item.text}\"\n등록: ${Family.nameOf(actor)}"
        runCatching { NotifyApi.notify(actor, targets, "📢 새 가족 공지", body) }
    }

    fun toggleItem(id: String, checked: Boolean) = viewModelScope.launch { runCatching { board.setChecked(id, checked) } }
    fun deleteItem(id: String) = viewModelScope.launch { runCatching { board.deleteItem(id) }; bumpFunRefresh() }

    /** 긴급 연락 발송: 대상에게 전체화면 긴급 알림. wantLocation 이면 위치공유 요청 버튼 노출. */
    fun sendEmergency(targetIds: List<String>, message: String, wantLocation: Boolean) = viewModelScope.launch {
        val actor = currentMemberId.value.orEmpty()
        val targets = targetIds.filter { it != actor }.distinct()
        if (targets.isEmpty()) return@launch
        val data = mapOf(
            "type" to "emergency",
            "sender" to actor,
            "msg" to message,
            "wantLoc" to if (wantLocation) "1" else "0",
        )
        runCatching {
            NotifyApi.notifyData(actor, targets, "빠른 연락 요청", "${Family.nameOf(actor)}님의 빠른 연락 요청", data)
        }
    }

    /**
     * 용돈 정산: 체크된 항목들을 대상 아이에게 완료 알림으로 보내고 목록에서 삭제.
     * 메시지 예) "엄마의 용돈 정산 15,000원이 완료 되었습니다. 확인해보세요."
     */
    fun settleAllowance(targetMemberId: String, items: List<ListItem>) = viewModelScope.launch {
        if (items.isEmpty()) return@launch
        val actor = currentMemberId.value.orEmpty()
        val parent = when (actor) {
            "eunseon" -> "엄마"
            "seonil" -> "아빠"
            else -> Family.nameOf(actor)
        }
        // 항목별로 삭제하고 성공한 것만 집계 → 실제로 정산된 항목만 알림에 반영
        // (일괄 실패 시 알림 누락, 일부 실패 시 유령 알림/재정산을 방지)
        val settled = items.filter { runCatching { board.deleteItem(it.id) }.isSuccess }
        if (settled.isEmpty()) return@launch
        val settledTotal = settled.sumOf { it.amount }
        val msg = buildString {
            append("${parent}가 아래 항목을 정산했어요.\n")
            append("\n")
            settled.forEach {
                append("\t\t${it.text.ifBlank { "항목" }} %,d원\n".format(it.amount))
            }
            append("\t\t---------------\n")
            append("\t\t합계 : %,d원\n".format(settledTotal))
            append("\n")
            append("❤️ 사랑해 아들~")
        }
        runCatching { NotifyApi.notify(actor, listOf(targetMemberId), "용돈 정산 완료", msg) }
    }

    /** 여러 항목의 체크 상태를 한 번에 설정(전체 체크/해제). */
    fun setCheckedAll(items: List<ListItem>, checked: Boolean) = viewModelScope.launch {
        runCatching { items.forEach { if (it.checked != checked) board.setChecked(it.id, checked) } }
    }

    /**
     * 용돈 조르기: 자녀(준영/준호)가 엄마(은선)에게 체크한 항목의 정산을 조른다.
     * 항목은 삭제하지 않는다. 알림 제목 "조르기".
     */
    fun nudgeAllowance(childMemberId: String, childName: String, items: List<ListItem>) = viewModelScope.launch {
        if (items.isEmpty()) return@launch
        val total = items.sumOf { it.amount }
        val body = buildString {
            append("${childName}에게서 용돈 정산 요청이 왔습니다.\n")
            append("\n")
            items.forEach {
                append("\t\t${it.text.ifBlank { "항목" }} %,d원\n".format(it.amount))
            }
            append("\t\t---------------\n")
            append("\t\t합계 : %,d원\n".format(total))
            append("\n")
            append("❤️ 사랑해요 엄마~")
        }
        runCatching { NotifyApi.notify(childMemberId, listOf("eunseon"), "조르기", body) }
    }
}

private val KR_DOW = listOf("월", "화", "수", "목", "금", "토", "일")

private fun krDate(d: LocalDate): String =
    "${d.monthValue}월 ${d.dayOfMonth}일 (${KR_DOW[d.dayOfWeek.value - 1]})"

private fun krTime(hhmm: String): String {
    val t = runCatching { LocalTime.parse(hhmm) }.getOrNull() ?: return ""
    val ampm = if (t.hour < 12) "오전" else "오후"
    val h = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$ampm $h:%02d".format(t.minute)
}

/** 알림용 친근한 일시 표기 ("8월 10일 (월) 오후 6:00" / 하루 종일 / 여러 날 ~) */
private fun eventWhenText(e: CalendarEvent): String {
    val d1 = runCatching { LocalDate.parse(e.startDateIso) }.getOrNull() ?: return ""
    val d2 = runCatching { LocalDate.parse(e.endDateIso.ifBlank { e.startDateIso }) }.getOrNull() ?: d1
    return when {
        d2 != d1 -> "${krDate(d1)} ~ ${krDate(d2)}"
        e.allDay -> "${krDate(d1)} · 하루 종일"
        e.startTime.isNotBlank() -> "${krDate(d1)} ${krTime(e.startTime)}"
        else -> krDate(d1)
    }
}

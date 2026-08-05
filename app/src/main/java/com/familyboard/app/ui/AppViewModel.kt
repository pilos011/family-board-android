package com.familyboard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.familyboard.app.FamilyBoardApp
import com.familyboard.app.data.model.BoardType
import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.Family
import com.familyboard.app.notif.NotifyApi
import com.familyboard.app.notif.ReminderScheduler
import com.familyboard.app.notif.UpdateChecker
import com.familyboard.app.notif.UpdateInfo
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    val image: String = "",
    val naverScore: Double = 0.0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val loading: Boolean = false,
    val isFun: Boolean = false, // true=재미진 곳(유튜브/웹), false=장소(맛집/가볼곳)
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
        board.events().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shoppingItems: StateFlow<List<ListItem>> =
        board.items(BoardType.SHOPPING.key)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todoItems: StateFlow<List<ListItem>> =
        board.items(BoardType.TODO.key)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 가족 공지사항 (부모 전용)
    val noticeItems: StateFlow<List<ListItem>> =
        board.items(BoardType.NOTICE.key)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // D-Day (카운트다운, 가족 모두)
    val ddayItems: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.DDayBoard.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 사용자 커스텀 체크리스트 정의(board="customlists"). 본인만 화면에 표시.
    val customLists: StateFlow<List<ListItem>> =
        board.items("customlists")
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 임의 보드 키의 항목 스트림(커스텀 리스트 포함). */
    fun boardItems(boardKey: String): kotlinx.coroutines.flow.Flow<List<ListItem>> = board.items(boardKey)

    // 장소 북마크 보드(맛집/가볼 곳)
    val restaurantItems: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.PlaceBoards.RESTAURANT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val visitItems: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.PlaceBoards.VISIT)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun placeItems(boardKey: String): StateFlow<List<ListItem>> =
        if (boardKey == com.familyboard.app.data.model.PlaceBoards.RESTAURANT) restaurantItems else visitItems

    // 재미진 곳(유튜브/웹/이미지 게시판). BOARD=공용, PRIVATE=내것(화면에서 createdBy 필터)
    val funItems: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.FunBoard.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val myFunItems: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.FunBoard.PRIVATE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun funBoardItems(boardKey: String): StateFlow<List<ListItem>> =
        if (boardKey == com.familyboard.app.data.model.FunBoard.PRIVATE) myFunItems else funItems

    /** 네이버 플레이스 등에서 공유받은 장소(저장 위치 선택 대기). */
    val pendingShare: MutableStateFlow<SharedPlace?> = MutableStateFlow(null)

    /** 공유 텍스트 처리. 네이버 플레이스 링크 → 장소(맛집/가볼곳), 그 외 링크 → 재미진 곳. 서버로 정보 파싱. */
    fun handleSharedText(raw: String?, subject: String?) {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return
        val url = Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', ']') ?: ""
        var name = text.replace(url, " ").split('\n').map { it.trim() }
            .firstOrNull { it.isNotBlank() && it != "[네이버지도]" }.orEmpty()
        if (name.isBlank()) name = subject?.trim().orEmpty()
        val isNaverPlace = url.contains("naver.me") || url.contains("map.naver.com") || url.contains("place.naver.com")

        if (isNaverPlace) {
            pendingShare.value = SharedPlace(name.ifBlank { "장소 불러오는 중…" }, url, loading = true, isFun = false)
            viewModelScope.launch {
                val info = com.familyboard.app.notif.NotifyApi.parsePlace(url)
                val cur = pendingShare.value ?: return@launch
                pendingShare.value = if (info != null && info.name.isNotBlank())
                    cur.copy(name = info.name, description = buildPlaceDesc(info), address = info.address,
                        image = info.image, naverScore = info.score ?: 0.0,
                        lat = info.lat ?: 0.0, lng = info.lng ?: 0.0, loading = false)
                else cur.copy(name = if (name.isBlank()) "새 장소" else name, loading = false)
            }
        } else if (url.isNotBlank()) {
            // 유튜브/웹 링크 → 재미진 곳
            pendingShare.value = SharedPlace(name.ifBlank { "불러오는 중…" }, url, loading = true, isFun = true)
            viewModelScope.launch {
                val info = com.familyboard.app.notif.NotifyApi.parseLink(url)
                val cur = pendingShare.value ?: return@launch
                pendingShare.value = if (info != null && (info.title.isNotBlank() || info.image.isNotBlank()))
                    cur.copy(name = info.title.ifBlank { name.ifBlank { "링크" } }, image = info.image, loading = false, isFun = true)
                else cur.copy(name = name.ifBlank { "링크" }, loading = false, isFun = true)
            }
        }
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
        addPlace(boardKey, nm, s.link, s.description, s.address, s.image, s.naverScore, s.lat, s.lng)
        pendingShare.value = null
    }
    fun clearPendingShare() { pendingShare.value = null }

    fun saveFun(boardKey: String) {
        val s = pendingShare.value ?: return
        if (s.loading) return
        val isImage = s.link.isBlank() && s.image.isNotBlank()
        val nm = s.name.trim().let { if (it.isBlank() || it == "불러오는 중…" || it == "이미지 올리는 중…") (if (isImage) "이미지" else "링크") else it }
        addFun(boardKey, nm, s.link, s.image)
        pendingShare.value = null
    }
    fun addFun(boardKey: String, title: String, link: String, image: String = "") = viewModelScope.launch {
        runCatching {
            board.upsertItem(ListItem(text = title.trim(), link = link.trim(),
                photoUrls = if (image.isBlank()) emptyList() else listOf(image),
                board = boardKey, createdBy = currentMemberId.value.orEmpty(),
                createdAt = System.currentTimeMillis()))
        }
    }
    /** 재미진 곳 항목을 현재 사용자가 봤다고 표시(중복 방지, arrayUnion). */
    fun markFunViewed(item: ListItem) {
        val me = currentMemberId.value.orEmpty()
        if (me.isBlank() || item.viewedBy.contains(me)) return
        viewModelScope.launch { runCatching { board.markViewed(item.id, me) } }
    }
    fun updateFun(item: ListItem, title: String, link: String, image: String = item.photoUrls.firstOrNull().orEmpty()) = viewModelScope.launch {
        runCatching {
            board.updateFields(item.id, mapOf(
                "text" to title.trim(), "link" to link.trim(),
                "photoUrls" to (if (image.isBlank()) emptyList<String>() else listOf(image)),
            ))
        }
    }
    /** 공유받은 이미지 → 서버 업로드 후 저장 대기(재미진 곳). */
    fun handleSharedImage(uri: android.net.Uri) {
        pendingShare.value = SharedPlace(name = "이미지 올리는 중…", link = "", loading = true, isFun = true)
        viewModelScope.launch {
            val url = com.familyboard.app.notif.PhotoUploader.compressAndUpload(getApplication(), uri)
            val cur = pendingShare.value ?: return@launch
            pendingShare.value = if (url != null) cur.copy(name = "공유 이미지", image = url, link = "", loading = false, isFun = true)
            else null // 업로드 실패 시 대기 취소
        }
    }

    fun addPlace(boardKey: String, name: String, link: String, description: String = "", address: String = "",
                 image: String = "", naverScore: Double = 0.0, lat: Double = 0.0, lng: Double = 0.0) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        runCatching {
            board.upsertItem(ListItem(text = name.trim(), link = link.trim(), description = description,
                address = address, photoUrls = if (image.isBlank()) emptyList() else listOf(image),
                naverScore = naverScore, lat = lat, lng = lng,
                board = boardKey, createdBy = currentMemberId.value.orEmpty()))
        }
    }
    fun updatePlace(item: ListItem, name: String, link: String,
                    description: String = item.description, address: String = item.address,
                    image: String = item.photoUrls.firstOrNull().orEmpty()) = viewModelScope.launch {
        runCatching {
            board.updateFields(item.id, mapOf(
                "text" to name.trim(), "link" to link.trim(), "description" to description, "address" to address,
                "photoUrls" to (if (image.isBlank()) emptyList<String>() else listOf(image)),
            ))
        }
    }
    /** 편집 다이얼로그에서 네이버 링크로 정보 가져오기(콜백으로 결과 전달). */
    fun fetchPlaceInfo(url: String, onResult: (com.familyboard.app.notif.PlaceInfo?) -> Unit) = viewModelScope.launch {
        onResult(com.familyboard.app.notif.NotifyApi.parsePlace(url))
    }
    fun describePlace(info: com.familyboard.app.notif.PlaceInfo): String = buildPlaceDesc(info)
    fun fetchLinkInfo(url: String, onResult: (com.familyboard.app.notif.LinkInfo?) -> Unit) = viewModelScope.launch {
        onResult(com.familyboard.app.notif.NotifyApi.parseLink(url))
    }
    fun setPlaceRating(item: ListItem, rating: Int) = viewModelScope.launch {
        runCatching { board.upsertItem(item.copy(rating = rating.toLong().coerceIn(0L, 5L))) }
    }
    fun addPlaceComment(item: ListItem, text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        val note = com.familyboard.app.data.model.ProgressNote(
            text = text.trim(), by = currentMemberId.value.orEmpty(), dateIso = LocalDate.now().toString())
        runCatching { board.upsertItem(item.copy(progress = item.progress + note)) }
    }
    fun deletePlaceComment(item: ListItem, index: Int) = viewModelScope.launch {
        if (index < 0 || index >= item.progress.size) return@launch
        runCatching { board.upsertItem(item.copy(progress = item.progress.filterIndexed { i, _ -> i != index })) }
    }
    fun updatePlaceComment(item: ListItem, index: Int, text: String) = viewModelScope.launch {
        if (index < 0 || index >= item.progress.size || text.isBlank()) return@launch
        val updated = item.progress.mapIndexed { i, n -> if (i == index) n.copy(text = text.trim()) else n }
        runCatching { board.upsertItem(item.copy(progress = updated)) }
    }

    val allowanceJunyoung: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.AllowanceBoards.JUNYOUNG)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allowanceJunho: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.AllowanceBoards.JUNHO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 인생 버킷 (부부 공용, 단일 보드)
    val bucketItems: StateFlow<List<ListItem>> =
        board.items(com.familyboard.app.data.model.BucketBoards.BOARD)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun bucketById(id: String): ListItem? = bucketItems.value.firstOrNull { it.id == id }

    /** 사용 가능한 앱 업데이트(없으면 null) */
    val updateInfo: MutableStateFlow<UpdateInfo?> = MutableStateFlow(null)

    /** dateIso -> 공휴일명 */
    val holidays: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
    private val loadedMonths = mutableSetOf<String>()

    init {
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
            }
        }
        // 앱 업데이트 확인
        viewModelScope.launch { updateInfo.value = UpdateChecker.check() }
    }

    /** 홈 화면 진입 등에서 업데이트를 다시 확인(최신이거나 확인 실패 시 배지 사라짐). */
    fun refreshUpdate() = viewModelScope.launch { updateInfo.value = UpdateChecker.check() }

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

    fun toggleItem(id: String, checked: Boolean) = viewModelScope.launch { runCatching { board.setChecked(id, checked) } }
    fun deleteItem(id: String) = viewModelScope.launch { runCatching { board.deleteItem(id) } }

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

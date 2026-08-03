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
import java.time.YearMonth

/** 온보딩 게이팅용 사용자 상태 */
sealed interface UserState {
    data object Loading : UserState
    data object NeedSelect : UserState
    data class Selected(val id: String) : UserState
}

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
        // 본인 확정 시 FCM 토큰을 서버에 등록 (등록 알림 수신용)
        viewModelScope.launch {
            currentMemberId.filterNotNull().distinctUntilChanged().collect { mid ->
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    NotifyApi.register(mid, token)
                }
            }
        }
        // 앱 업데이트 확인
        viewModelScope.launch { updateInfo.value = UpdateChecker.check() }
    }

    fun itemsFor(boardKey: String): StateFlow<List<ListItem>> =
        if (boardKey == BoardType.TODO.key) todoItems else shoppingItems

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

    fun addEvent(event: CalendarEvent) = viewModelScope.launch {
        board.upsertEvent(event)
        notifyEventRegistered(event)
    }

    /** 등록 알림: 등록자 외에, 태깅된(또는 모두) 가족에게 푸시 요청 */
    private suspend fun notifyEventRegistered(e: CalendarEvent) {
        val actor = e.createdBy
        if (actor.isBlank()) return
        val targets = (if (e.memberIds.contains(Family.ALL_ID)) Family.members.map { it.id } else e.memberIds)
            .filter { it != actor }
        if (targets.isEmpty()) return
        val whenText = if (e.allDay) "하루 종일" else e.startTime
        val body = listOfNotNull(
            e.startDateIso.ifBlank { null },
            whenText.ifBlank { null },
            "등록: ${Family.nameOf(actor)}",
        ).joinToString(" · ")
        runCatching { NotifyApi.notify(actor, targets, "새 일정: ${e.title}", body) }
    }
    fun updateEvent(event: CalendarEvent) = viewModelScope.launch { board.upsertEvent(event) }
    fun deleteEvent(id: String) = viewModelScope.launch { board.deleteEvent(id) }

    /** 반복 일정에서 특정 날짜만 제외(그 회차만 삭제) */
    fun excludeOccurrence(event: CalendarEvent, dateIso: String) = viewModelScope.launch {
        if (!event.exdates.contains(dateIso)) {
            board.upsertEvent(event.copy(exdates = event.exdates + dateIso))
        }
    }

    fun eventById(id: String): CalendarEvent? = events.value.firstOrNull { it.id == id }

    fun addItem(item: ListItem) = viewModelScope.launch { board.upsertItem(item) }
    fun updateItem(item: ListItem) = viewModelScope.launch { board.upsertItem(item) }

    fun toggleItem(id: String, checked: Boolean) = viewModelScope.launch { board.setChecked(id, checked) }
    fun deleteItem(id: String) = viewModelScope.launch { board.deleteItem(id) }

    /**
     * 용돈 정산: 체크된 항목들을 대상 아이에게 완료 알림으로 보내고 목록에서 삭제.
     * 메시지 예) "엄마의 용돈 정산 15,000원이 완료 되었습니다. 확인해보세요."
     */
    fun settleAllowance(targetMemberId: String, items: List<ListItem>) = viewModelScope.launch {
        if (items.isEmpty()) return@launch
        val actor = currentMemberId.value.orEmpty()
        val total = items.sumOf { it.amount }
        val parent = when (actor) {
            "eunseon" -> "엄마"
            "seonil" -> "아빠"
            else -> Family.nameOf(actor)
        }
        val msg = buildString {
            append("${parent}가 아래 항목을 정산했어요.\n")
            append("\n")
            items.forEach {
                append("\t\t${it.text.ifBlank { "항목" }} %,d원\n".format(it.amount))
            }
            append("\t\t---------------\n")
            append("\t\t합계 : %,d원\n".format(total))
            append("\n")
            append("❤️ 사랑해 아들~")
        }
        runCatching { NotifyApi.notify(actor, listOf(targetMemberId), "용돈 정산 완료", msg) }
        items.forEach { board.deleteItem(it.id) }
    }

    /** 여러 항목의 체크 상태를 한 번에 설정(전체 체크/해제). */
    fun setCheckedAll(items: List<ListItem>, checked: Boolean) = viewModelScope.launch {
        items.forEach { if (it.checked != checked) board.setChecked(it.id, checked) }
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

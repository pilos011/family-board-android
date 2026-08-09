package com.familyboard.app.data.repo

import com.familyboard.app.data.model.BoardType
import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.data.model.ListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * 인메모리 구현. 앱 재시작 시 초기화되며 기기 간 동기화되지 않는다.
 * UI/흐름 확인 및 Firestore 연동 전 데모 용도.
 */
class InMemoryBoardRepository : BoardRepository {

    private val eventsFlow = MutableStateFlow<List<CalendarEvent>>(seedEvents())
    private val itemsFlow = MutableStateFlow<List<ListItem>>(seedItems())

    override fun events(): Flow<List<CalendarEvent>> = eventsFlow.asStateFlow()

    override suspend fun upsertEvent(event: CalendarEvent) {
        val e = if (event.id.isBlank()) event.copy(id = UUID.randomUUID().toString()) else event
        eventsFlow.value = eventsFlow.value.filter { it.id != e.id } + e
    }

    override suspend fun deleteEvent(id: String) {
        eventsFlow.value = eventsFlow.value.filter { it.id != id }
    }

    override fun items(board: String): Flow<List<ListItem>> =
        itemsFlow.map { list -> list.filter { it.board == board } }

    override suspend fun countByBoard(board: String, createdBy: String?): Int =
        itemsFlow.value.count { it.board == board && (createdBy == null || it.createdBy == createdBy) }

    override suspend fun pageByBoard(
        board: String, limit: Int, createdBy: String?, ascending: Boolean, afterCreatedAt: Long?,
    ): List<ListItem> {
        var list = itemsFlow.value.filter { it.board == board && (createdBy == null || it.createdBy == createdBy) }
        list = if (ascending) list.sortedBy { it.createdAt } else list.sortedByDescending { it.createdAt }
        // startAt(포함)과 동일하게 경계값 포함 → 호출측에서 id 중복제거
        if (afterCreatedAt != null)
            list = list.filter { if (ascending) it.createdAt >= afterCreatedAt else it.createdAt <= afterCreatedAt }
        return list.take(limit)
    }

    override suspend fun getItemById(id: String): ListItem? = itemsFlow.value.find { it.id == id }

    override suspend fun upsertItem(item: ListItem) {
        val i = if (item.id.isBlank()) item.copy(id = UUID.randomUUID().toString()) else item
        itemsFlow.value = itemsFlow.value.filter { it.id != i.id } + i
    }

    override suspend fun setChecked(id: String, checked: Boolean) {
        itemsFlow.value = itemsFlow.value.map { if (it.id == id) it.copy(checked = checked) else it }
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun updateFields(id: String, fields: Map<String, Any>) {
        itemsFlow.value = itemsFlow.value.map { item ->
            if (item.id != id) item else {
                var x = item
                fields.forEach { (k, v) ->
                    x = when (k) {
                        "text" -> x.copy(text = v as String)
                        "link" -> x.copy(link = v as String)
                        "photoUrls" -> x.copy(photoUrls = v as List<String>)
                        "description" -> x.copy(description = v as String)
                        "address" -> x.copy(address = v as String)
                        "naverScore" -> x.copy(naverScore = (v as Number).toDouble())
                        "lat" -> x.copy(lat = (v as Number).toDouble())
                        "lng" -> x.copy(lng = (v as Number).toDouble())
                        else -> x
                    }
                }
                x
            }
        }
    }

    override suspend fun markViewed(id: String, memberId: String) {
        itemsFlow.value = itemsFlow.value.map {
            if (it.id == id && !it.viewedBy.contains(memberId)) it.copy(viewedBy = it.viewedBy + memberId) else it
        }
    }

    override suspend fun deleteItem(id: String) {
        itemsFlow.value = itemsFlow.value.filter { it.id != id }
    }

    override suspend fun deleteByBoard(board: String) {
        itemsFlow.value = itemsFlow.value.filter { it.board != board }
    }

    override suspend fun updatePresence(presence: com.familyboard.app.data.model.Presence) {}
    override suspend fun getPresence(): List<com.familyboard.app.data.model.Presence> = emptyList()

    private fun seedEvents(): List<CalendarEvent> {
        val today = LocalDate.now().toString()
        return listOf(
            CalendarEvent(
                id = UUID.randomUUID().toString(), title = "가족 저녁 식사",
                startDateIso = today, endDateIso = today,
                allDay = false, startTime = "18:30", endTime = "19:30", memberIds = listOf("all"),
            ),
            CalendarEvent(
                id = UUID.randomUUID().toString(), title = "은선 병원 예약",
                startDateIso = today, endDateIso = today,
                allDay = false, startTime = "10:00", endTime = "11:00", memberIds = listOf("eunseon"),
            ),
        )
    }

    private fun seedItems(): List<ListItem> = listOf(
        ListItem(UUID.randomUUID().toString(), "우유", false, BoardType.SHOPPING.key, "seonil"),
        ListItem(UUID.randomUUID().toString(), "사과", true, BoardType.SHOPPING.key, "eunseon"),
        ListItem(UUID.randomUUID().toString(), "재활용 배출", false, BoardType.TODO.key, "junho"),
    )
}

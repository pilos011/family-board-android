package com.familyboard.app.data.repo

import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.data.model.ListItem
import kotlinx.coroutines.flow.Flow

/**
 * 일정·리스트 데이터 접근 추상화.
 * 현재 활성 구현: [InMemoryBoardRepository] (오프라인 데모용).
 * google-services.json 추가 후 firebase 패키지의 Firestore 구현으로 교체하면 가족 4명 실시간 공유.
 */
interface BoardRepository {
    fun events(): Flow<List<CalendarEvent>>
    suspend fun upsertEvent(event: CalendarEvent)
    suspend fun deleteEvent(id: String)

    fun items(board: String): Flow<List<ListItem>>
    suspend fun upsertItem(item: ListItem)
    suspend fun setChecked(id: String, checked: Boolean)
    suspend fun deleteItem(id: String)
    suspend fun deleteByBoard(board: String)
}

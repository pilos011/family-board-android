package com.familyboard.app.data.repo

import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.model.Presence
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

    /** 보드 전체 항목 수(집계 count, 문서를 내려받지 않음). [createdBy] 지정 시 해당 작성자만. */
    suspend fun countByBoard(board: String, createdBy: String? = null): Int

    /**
     * 페이지 방식(이전/다음) 1회성 조회. createdAt 정렬([ascending] false=최신순).
     * [afterCreatedAt] 지정 시 그 값 이후부터(커서). 최대 [limit]개.
     */
    suspend fun pageByBoard(
        board: String, limit: Int, createdBy: String? = null,
        ascending: Boolean = false, afterCreatedAt: Long? = null,
    ): List<ListItem>

    /** id로 항목 1건 조회(보드/작성자 무관). 공유받은 항목 열기용. 없으면 null. */
    suspend fun getItemById(id: String): ListItem?

    suspend fun upsertItem(item: ListItem)
    suspend fun setChecked(id: String, checked: Boolean)
    suspend fun updateFields(id: String, fields: Map<String, Any>)
    suspend fun markViewed(id: String, memberId: String)
    suspend fun deleteItem(id: String)
    suspend fun deleteByBoard(board: String)

    /** 접속 현황(관리자용): 내 접속 기록 upsert / 전체 조회. */
    suspend fun updatePresence(presence: Presence)
    suspend fun getPresence(): List<Presence>
}

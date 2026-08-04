package com.familyboard.app.data.firebase

import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.repo.BoardRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Firestore 기반 실시간 공유 구현 (가족 4명 공용).
 *
 * 활성화 방법:
 *  1) Firebase 콘솔에서 프로젝트 생성 → Android 앱(패키지 com.familyboard.app) 등록
 *  2) 받은 google-services.json 을 app/ 폴더에 넣기
 *  3) build.gradle(root/app)과 libs.versions.toml 의 google-services 관련 주석 해제
 *  4) AppContainer 에서 InMemoryBoardRepository → FirestoreBoardRepository 로 교체
 *
 * 컬렉션: events / items (문서 id = 모델 id)
 */
class FirestoreBoardRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : BoardRepository {

    private val eventsCol get() = db.collection("events")
    private val itemsCol get() = db.collection("items")

    override fun events(): Flow<List<CalendarEvent>> = callbackFlow {
        val reg = eventsCol.addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { it.toObject(CalendarEvent::class.java) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    override suspend fun upsertEvent(event: CalendarEvent) {
        val e = if (event.id.isBlank()) event.copy(id = UUID.randomUUID().toString()) else event
        eventsCol.document(e.id).set(e).await()
    }

    override suspend fun deleteEvent(id: String) {
        eventsCol.document(id).delete().await()
    }

    override fun items(board: String): Flow<List<ListItem>> = callbackFlow {
        val reg = itemsCol.whereEqualTo("board", board).addSnapshotListener { snap, err ->
            if (err != null) { close(err); return@addSnapshotListener }
            trySend(snap?.documents?.mapNotNull { it.toObject(ListItem::class.java) } ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    override suspend fun upsertItem(item: ListItem) {
        val i = if (item.id.isBlank()) item.copy(id = UUID.randomUUID().toString()) else item
        itemsCol.document(i.id).set(i).await()
    }

    override suspend fun setChecked(id: String, checked: Boolean) {
        itemsCol.document(id).update("checked", checked).await()
    }

    override suspend fun deleteItem(id: String) {
        itemsCol.document(id).delete().await()
    }

    override suspend fun deleteByBoard(board: String) {
        val snap = itemsCol.whereEqualTo("board", board).get().await()
        for (doc in snap.documents) doc.reference.delete().await()
    }
}

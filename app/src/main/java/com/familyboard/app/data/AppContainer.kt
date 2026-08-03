package com.familyboard.app.data

import android.content.Context
import com.familyboard.app.data.firebase.FirestoreBoardRepository
import com.familyboard.app.data.repo.BoardRepository
import com.familyboard.app.data.repo.HolidayRepository

/**
 * 간단한 수동 DI 컨테이너. 저장소 구현을 한 곳에서 교체한다.
 *
 * Firestore 실시간 공유 사용 중. (오프라인 데모로 되돌리려면 InMemoryBoardRepository() 로 교체)
 */
class AppContainer(context: Context) {
    val boardRepository: BoardRepository = FirestoreBoardRepository()
    val holidayRepository: HolidayRepository = HolidayRepository()
    val currentUserStore: CurrentUserStore = CurrentUserStore(context.applicationContext)
}

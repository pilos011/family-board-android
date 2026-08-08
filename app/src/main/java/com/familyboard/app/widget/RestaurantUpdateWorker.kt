package com.familyboard.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyboard.app.data.model.PlaceBoards
import com.familyboard.app.notif.NotifyApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 시간당 1회 실행: 현재 위치 → 근처 5km 맛집 최대 20곳 조회 → 사진 캐시 → 목록 저장 → 0번부터 표시 + 순차 회전 시작.
 * 화면 회전(10초)은 ROTATE 알람이 캐시만으로 처리하므로 여기(네트워크)는 시간당 1회뿐. (블로킹 IO 라 IO 디스패처)
 */
class RestaurantUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val loc = RestaurantWidgetScheduler.bestLocation(ctx)
        val recs = runCatching {
            NotifyApi.recommend(
                board = PlaceBoards.RESTAURANT, category = "", region = "",
                savedNames = emptyList(), lat = loc.lat, lng = loc.lng, radius = 5000, limit = 20,
            )
        }.getOrDefault(emptyList())

        if (recs.isEmpty()) {
            // 조회 실패/결과 없음 → 기존 캐시가 있으면 현재 항목 유지, 없으면 안내.
            if (RestaurantWidgetScheduler.count(ctx) > 0) {
                RestaurantWidgetScheduler.renderCurrent(ctx)
                RestaurantWidgetScheduler.scheduleRotate(ctx)
            } else {
                RestaurantWidgetScheduler.renderEmpty(ctx, "근처 추천을 찾지 못했어요")
            }
            return@withContext Result.success()
        }

        RestaurantWidgetScheduler.clearPhotos(ctx)
        RestaurantWidgetScheduler.saveList(ctx, recs) // 인덱스 -1 로 리셋 → 첫 회전이 0번부터
        recs.forEachIndexed { i, r -> if (r.image.isNotBlank()) RestaurantWidgetScheduler.cachePhoto(ctx, r.image, i) }
        RestaurantWidgetScheduler.renderNext(ctx)     // 0번부터 순서대로 표시 시작
        RestaurantWidgetScheduler.scheduleRotate(ctx) // 10초 순차 회전 시작
        Result.success()
    }
}

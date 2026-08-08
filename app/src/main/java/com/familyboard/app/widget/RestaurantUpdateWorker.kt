package com.familyboard.app.widget

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyboard.app.data.model.PlaceBoards
import com.familyboard.app.notif.NotifyApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 맛집 위젯 갱신 작업. 현재 위치의 시군구를 역지오코딩해,
 * 무캐시 / 1시간 경과 / 시군구 변경 중 하나면 근처 5km 맛집 20곳을 재검색·사진 캐시·표시.
 * 같은 시군구이고 신선하면 재검색하지 않음(현재 카드 유지). 블로킹 IO 라 IO 디스패처.
 */
class RestaurantUpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val loc = RestaurantWidgetScheduler.bestLocation(ctx)
        val siGunGu = currentDistrict(ctx, loc.lat, loc.lng) // 실패 시 null
        val nocache = RestaurantWidgetScheduler.count(ctx) <= 0
        val hourlyDue = System.currentTimeMillis() - RestaurantWidgetScheduler.lastFetchAt(ctx) >= RestaurantWidgetScheduler.FALLBACK_MS
        val districtChanged = siGunGu != null && siGunGu != RestaurantWidgetScheduler.lastSiGunGu(ctx)

        // 시군구 그대로 + 신선 + 캐시 있음 → 재검색 안 함(현재 카드 유지)
        if (!(nocache || hourlyDue || districtChanged)) {
            RestaurantWidgetScheduler.renderCurrent(ctx)
            RestaurantWidgetScheduler.scheduleRotate(ctx)
            return@withContext Result.success()
        }

        val recs = runCatching {
            NotifyApi.recommend(
                board = PlaceBoards.RESTAURANT, category = "", region = "",
                savedNames = emptyList(), lat = loc.lat, lng = loc.lng, radius = 5000, limit = 20,
            )
        }.getOrDefault(emptyList())

        if (recs.isEmpty()) {
            if (RestaurantWidgetScheduler.count(ctx) > 0) {
                RestaurantWidgetScheduler.renderCurrent(ctx); RestaurantWidgetScheduler.scheduleRotate(ctx)
            } else {
                RestaurantWidgetScheduler.renderEmpty(ctx, "근처 추천을 찾지 못했어요")
            }
            return@withContext Result.success()
        }

        RestaurantWidgetScheduler.clearPhotos(ctx)
        RestaurantWidgetScheduler.saveList(ctx, recs) // 인덱스 -1 리셋 → 0번부터
        recs.forEachIndexed { i, r -> if (r.image.isNotBlank()) RestaurantWidgetScheduler.cachePhoto(ctx, r.image, i) }
        RestaurantWidgetScheduler.markFetched(ctx)
        if (siGunGu != null) RestaurantWidgetScheduler.setSiGunGu(ctx, siGunGu)
        RestaurantWidgetScheduler.renderNext(ctx)
        RestaurantWidgetScheduler.scheduleRotate(ctx)
        Result.success()
    }

    /** 좌표 → 시군구 문자열(예: "고양시 일산동구"). 실패/시간초과 시 null. (앱 reverseDistrict 와 동일 방식) */
    private suspend fun currentDistrict(ctx: Context, lat: Double, lng: Double): String? =
        withTimeoutOrNull(4000) {
            runCatching {
                val geo = Geocoder(ctx, Locale.KOREA)
                val addr: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine<Address?> { cont ->
                        geo.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(results: MutableList<Address>) { if (cont.isActive) cont.resume(results.firstOrNull()) }
                            override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(null) }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION") geo.getFromLocation(lat, lng, 1)?.firstOrNull()
                }
                if (addr == null) return@runCatching null
                val d = listOfNotNull(addr.locality ?: addr.subAdminArea, addr.subLocality)
                    .filter { it.isNotBlank() }.joinToString(" ")
                if (d.isNotBlank()) d else addr.adminArea?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
}

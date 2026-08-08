package com.familyboard.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/**
 * 맛집 추천 홈 위젯. 상단 탭→맛집 화면, 하단 카드=근처 추천(탭→네이버지도), 🔄=다음 곳.
 * - 데이터: 시간당 1회(09~22시) WorkManager 조회+캐시.  회전: 약 10초 ROTATE 알람(캐시만, 네트워크 없음).
 * - onUpdate 에서 fetch enqueue 금지(PACKAGE_CHANGED→onUpdate 무한루프 방지). 초기 fetch 는 onEnabled 에서만.
 */
class RestaurantWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        RestaurantWidgetScheduler.scheduleNextTick(context)
        RestaurantWidgetScheduler.enqueueFetch(context) // 최초 1회 조회(여기서만)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        RestaurantWidgetScheduler.renderCurrent(context)    // 현재 항목 표시(진행 안 함)
        RestaurantWidgetScheduler.scheduleNextTick(context) // 시간당 조회 예약
        RestaurantWidgetScheduler.scheduleRotate(context)   // 회전 예약(캐시 있을 때만)
    }

    override fun onDisabled(context: Context) {
        RestaurantWidgetScheduler.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK -> {
                RestaurantWidgetScheduler.scheduleNextTick(context)
                if (RestaurantWidgetScheduler.inWindow()) RestaurantWidgetScheduler.enqueueFetch(context)
            }
            ACTION_ROTATE -> {
                RestaurantWidgetScheduler.renderNext(context) // 캐시에서 다음 곳(순차, 네트워크 없음)
                RestaurantWidgetScheduler.scheduleRotate(context)
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.familyboard.app.widget.TICK"     // 시간당 조회
        const val ACTION_ROTATE = "com.familyboard.app.widget.ROTATE" // 약 10초 화면 회전 / 🔄
    }
}

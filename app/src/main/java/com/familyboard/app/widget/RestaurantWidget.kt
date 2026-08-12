package com.familyboard.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.WorkManager
import com.familyboard.app.MainActivity
import com.familyboard.app.R
import com.familyboard.app.data.model.PlaceBoards

/**
 * 맛집 바로가기 위젯(1x1). 데이터·네트워크·구글 호출 없음 — 탭하면 앱의 '맛집' 화면으로 바로 이동.
 * (구버전의 '추천/위치/사진' 위젯을 대체. 구글 Places 호출을 위젯에서 완전히 제거.)
 */
class RestaurantWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        cancelLegacy(context) // 예전 버전이 남긴 위치 알람/작업 정리(1회)
        val rv = RemoteViews(context.packageName, R.layout.widget_restaurant)
        rv.setOnClickPendingIntent(R.id.widget_root, openAppPending(context))
        mgr.updateAppWidget(ids, rv)
    }

    override fun onDisabled(context: Context) { cancelLegacy(context) }

    /** 탭 → 앱 실행 후 '맛집' 화면으로 이동(MainActivity 가 EXTRA_WIDGET_NAV 처리). */
    private fun openAppPending(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WIDGET_NAV, PlaceBoards.RESTAURANT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            ctx, 7300, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 구버전(추천 위젯)이 예약해둔 10분 TICK/회전 알람과 WorkManager 작업을 취소(잔여 배터리 소모 방지). */
    private fun cancelLegacy(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        listOf(
            7301 to "com.familyboard.app.widget.TICK",
            7305 to "com.familyboard.app.widget.ROTATE",
            7306 to "com.familyboard.app.widget.ROTATE",
        ).forEach { (req, action) ->
            runCatching {
                val pi = PendingIntent.getBroadcast(
                    ctx, req, Intent(ctx, RestaurantWidget::class.java).setAction(action), flags,
                )
                am?.cancel(pi)
            }
        }
        runCatching { WorkManager.getInstance(ctx).cancelUniqueWork("restaurant_widget") }
    }
}

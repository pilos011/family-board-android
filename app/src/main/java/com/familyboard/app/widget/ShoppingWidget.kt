package com.familyboard.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.familyboard.app.MainActivity
import com.familyboard.app.R
import com.familyboard.app.data.model.BoardType

/**
 * 장바구니(장보기) 바로가기 1x1 위젯.
 * - 탭 → 앱 장보기 화면(listDetail/shopping).
 * - 담긴(미체크) 항목 수를 우상단 배지로 표시.
 * 개수는 앱이 [setCount] 로 갱신(장보기 항목 변경/앱 실행 시). 위젯 자체는 네트워크·주기작업 없음.
 */
class ShoppingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = render(context)

    companion object {
        private const val PREFS = "shopping_widget"
        private const val KEY_COUNT = "count"
        private const val REQ = 7401

        /** 앱에서 호출: 장보기 항목 수 저장 + 위젯 갱신. */
        fun setCount(ctx: Context, count: Int) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_COUNT, count).apply()
            render(ctx)
        }

        fun render(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ShoppingWidget::class.java))
            if (ids == null || ids.isEmpty()) return
            val count = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_COUNT, 0)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_shopping)
            if (count > 0) {
                rv.setTextViewText(R.id.shopping_count, if (count > 99) "99+" else count.toString())
                rv.setViewVisibility(R.id.shopping_count, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.shopping_count, View.GONE)
            }
            val i = Intent(ctx, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_WIDGET_NAV, BoardType.SHOPPING.key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                ctx, REQ, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.shopping_root, pi)
            mgr.updateAppWidget(ids, rv)
        }
    }
}

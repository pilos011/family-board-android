package com.familyboard.app.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** 5분 주기로 HA 리포트를 예약(AlarmManager). 발화 시 HaReportReceiver 가 전송 후 다음 회차를 재예약. */
object HaReportScheduler {
    private const val ACTION = "com.familyboard.app.HA_REPORT"
    const val INTERVAL_MS = 5 * 60 * 1000L

    fun schedule(context: Context, delayMs: Long = INTERVAL_MS) {
        if (!HaReporter.enabled()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(context))
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(context))
            }
        } catch (se: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(context))
        }
    }

    private fun pi(context: Context): PendingIntent {
        val intent = Intent(context, HaReportReceiver::class.java).apply { action = ACTION }
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f = f or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 9100, intent, f)
    }
}

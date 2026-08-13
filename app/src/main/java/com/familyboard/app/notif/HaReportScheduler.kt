package com.familyboard.app.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** 15분 주기로 HA 리포트를 예약(AlarmManager). 발화 시 HaReportReceiver 가 전송 후 다음 회차를 재예약.
 *  배터리 절약: 재실·배터리 텔레메트리라 정확한 시각이 불필요 → inexact(Doze 배칭) 알람 사용. */
object HaReportScheduler {
    private const val ACTION = "com.familyboard.app.HA_REPORT"
    const val INTERVAL_MS = 15 * 60 * 1000L // 배터리 절약: 5분→15분(웨이크업·HTTPS 호출 1/3로)

    fun schedule(context: Context, delayMs: Long = INTERVAL_MS) {
        if (!HaReporter.enabled()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + delayMs
        // 정확 알람(setExact…) 대신 inexact(setAndAllowWhileIdle) — OS가 다른 알람과 배칭해 Doze 깨움 최소화.
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(context)) }
    }

    private fun pi(context: Context): PendingIntent {
        val intent = Intent(context, HaReportReceiver::class.java).apply { action = ACTION }
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f = f or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 9100, intent, f)
    }
}

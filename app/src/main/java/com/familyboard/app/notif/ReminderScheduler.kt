package com.familyboard.app.notif

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.CalendarEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 일정 미리 알림을 AlarmManager 로 예약/취소한다. (시간 기반 → 앱이 꺼져 있어도 동작)
 * "나에게 해당되는(태깅됐거나 모두) + 알림이 설정된" 일정만 예약한다.
 * 프로세스 생존 동안 예약 상태를 기억해 삭제된 일정은 취소한다.
 * (기기 재부팅 시 재예약은 후속 과제: BOOT_COMPLETED 리시버 필요)
 */
object ReminderScheduler {
    const val CHANNEL_ID = "event_reminders"
    private const val TAG = "ReminderScheduler"
    private const val ACTION = "com.familyboard.app.REMINDER"
    private val scheduled = mutableSetOf<String>()

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "일정 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "가족보드 일정 미리 알림"
                }
            )
        }
    }

    @Synchronized
    fun reconcile(context: Context, events: List<CalendarEvent>, currentMemberId: String?) {
        ensureChannel(context)
        val relevant = events.filter { it.reminder != "none" && isForMe(it, currentMemberId) }
        val newIds = relevant.map { it.id }.toSet()
        (scheduled - newIds).forEach { cancel(context, it) }
        relevant.forEach { schedule(context, it) }
        scheduled.clear(); scheduled.addAll(newIds)
    }

    private fun isForMe(e: CalendarEvent, mid: String?): Boolean =
        e.memberIds.contains(Family.ALL_ID) || (mid != null && e.memberIds.contains(mid))

    private fun schedule(context: Context, e: CalendarEvent) {
        val triggerAt = triggerMillis(e) ?: return
        if (triggerAt <= System.currentTimeMillis()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, e)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.i(TAG, "예약: ${e.title} @ $triggerAt")
        } catch (se: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancel(context: Context, eventId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, eventId, null))
    }

    private fun pendingIntent(context: Context, e: CalendarEvent): PendingIntent =
        pendingIntent(context, e.id, e)

    private fun pendingIntent(context: Context, eventId: String, e: CalendarEvent?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION
            data = android.net.Uri.parse("familyboard://reminder/$eventId")
            putExtra("notifId", eventId.hashCode())
            if (e != null) {
                putExtra("title", e.title.ifBlank { "일정" })
                putExtra("text", contentText(e))
            }
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, eventId.hashCode(), intent, flags)
    }

    private fun contentText(e: CalendarEvent): String {
        val time = if (e.allDay) "하루 종일" else koreanTime(e.startTime)
        return listOfNotNull("가족보드 일정", time.ifBlank { null }).joinToString(" · ")
    }

    private fun triggerMillis(e: CalendarEvent): Long? {
        val zone = ZoneId.systemDefault()
        val reminder = e.reminder
        return try {
            if (reminder.startsWith("custom:")) {
                val date = LocalDate.parse(reminder.removePrefix("custom:"))
                val time = eventTime(e)
                LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
            } else {
                val date = LocalDate.parse(e.startDateIso)
                val base = LocalDateTime.of(date, eventTime(e))
                base.minusMinutes(offsetMinutes(reminder)).atZone(zone).toInstant().toEpochMilli()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "트리거 계산 실패: ${e.title}", t); null
        }
    }

    private fun eventTime(e: CalendarEvent): LocalTime =
        if (e.allDay || e.startTime.isBlank()) LocalTime.of(9, 0)
        else runCatching { LocalTime.parse(e.startTime) }.getOrDefault(LocalTime.of(9, 0))

    private fun offsetMinutes(reminder: String): Long = when (reminder) {
        "atTime" -> 0
        "5m" -> 5
        "15m" -> 15
        "30m" -> 30
        "1h" -> 60
        "2h" -> 120
        "1d" -> 1440
        "2d" -> 2880
        else -> 0
    }

    private fun koreanTime(hhmm: String): String {
        val p = hhmm.split(":")
        if (p.size < 2) return ""
        val h = p[0].toIntOrNull() ?: return ""
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$ampm $h12:${p[1]}"
    }
}

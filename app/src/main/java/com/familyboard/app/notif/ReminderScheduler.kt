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
import com.familyboard.app.data.RecurrenceExpander
import com.familyboard.app.data.model.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 일정 미리 알림을 AlarmManager 로 예약/취소. (시간 기반 → 앱이 꺼져 있어도 동작)
 * 반복 일정은 "다음 회차"의 알림을 예약하고, 알림이 울리면 ReminderReceiver 가 그 다음 회차를 이어서 예약한다.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "event_reminders"
    private const val TAG = "ReminderScheduler"
    private const val ACTION = "com.familyboard.app.REMINDER"
    private const val PREFS = "reminder_sched"
    private const val KEY = "ids"

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
        // 예약된 id 를 영속 저장 → 프로세스/재부팅 후에도 삭제·변경된 일정의 유령 알람을 취소
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prev = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        (prev - newIds).forEach { cancel(context, it) }
        relevant.forEach { scheduleFrom(context, it, System.currentTimeMillis()) }
        prefs.edit().putStringSet(KEY, newIds).apply()
    }

    /** 재부팅 복원용: 취소·prefs 조작 없이 현재 대상 일정의 알람만 다시 무장(빈 스냅샷이 예약을 지우지 않게). */
    @Synchronized
    fun rearm(context: Context, events: List<CalendarEvent>, currentMemberId: String?) {
        ensureChannel(context)
        events.filter { it.reminder != "none" && isForMe(it, currentMemberId) }
            .forEach { scheduleFrom(context, it, System.currentTimeMillis()) }
    }

    private fun isForMe(e: CalendarEvent, mid: String?): Boolean =
        e.memberIds.contains(Family.ALL_ID) || (mid != null && e.memberIds.contains(mid))

    /** fromMillis 이후 가장 이른 알림 시각으로 예약 (없으면 미예약). 반복 회차 지원. */
    fun scheduleFrom(context: Context, e: CalendarEvent, fromMillis: Long) {
        val triggerAt = nextReminderTrigger(e, fromMillis) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, e)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.i(TAG, "예약: ${e.title} @ $triggerAt (repeat=${e.repeat})")
        } catch (se: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    private fun cancel(context: Context, eventId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntentById(context, eventId))
    }

    /** 알림 발화 후, 다음 회차를 이어서 예약 (반복 일정용). */
    fun rescheduleNext(context: Context, e: CalendarEvent) {
        if (e.repeat.isBlank()) return
        scheduleFrom(context, e, System.currentTimeMillis() + 60_000)
    }

    private fun pendingIntent(context: Context, e: CalendarEvent): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION
            data = android.net.Uri.parse("familyboard://reminder/${e.id}")
            putExtra("notifId", e.id.hashCode())
            putExtra("title", e.title.ifBlank { "일정" })
            putExtra("text", contentText(e))
            // 다음 회차 재예약용 필드
            putExtra("eid", e.id)
            putExtra("sdate", e.startDateIso)
            putExtra("edate", e.endDateIso)
            putExtra("allday", e.allDay)
            putExtra("stime", e.startTime)
            putExtra("etime", e.endTime)
            putExtra("repeat", e.repeat)
            putExtra("lunar", e.lunar)
            putExtra("reminder", e.reminder)
            putExtra("exdates", e.exdates.joinToString(","))
        }
        return PendingIntent.getBroadcast(context, e.id.hashCode(), intent, flags())
    }

    private fun pendingIntentById(context: Context, eventId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION
            data = android.net.Uri.parse("familyboard://reminder/$eventId")
        }
        return PendingIntent.getBroadcast(context, eventId.hashCode(), intent, flags())
    }

    private fun flags(): Int {
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f = f or PendingIntent.FLAG_IMMUTABLE
        return f
    }

    /** 인텐트 extras 로부터 재예약에 필요한 최소 CalendarEvent 복원 */
    fun eventFromIntent(intent: Intent): CalendarEvent? {
        val eid = intent.getStringExtra("eid") ?: return null
        return CalendarEvent(
            id = eid,
            title = intent.getStringExtra("title") ?: "",
            startDateIso = intent.getStringExtra("sdate") ?: "",
            endDateIso = intent.getStringExtra("edate") ?: "",
            allDay = intent.getBooleanExtra("allday", false),
            startTime = intent.getStringExtra("stime") ?: "",
            endTime = intent.getStringExtra("etime") ?: "",
            repeat = intent.getStringExtra("repeat") ?: "",
            lunar = intent.getBooleanExtra("lunar", false),
            reminder = intent.getStringExtra("reminder") ?: "none",
            exdates = (intent.getStringExtra("exdates") ?: "").split(",").filter { it.isNotBlank() },
        )
    }

    private fun contentText(e: CalendarEvent): String {
        val time = if (e.allDay) "하루 종일" else koreanTime(e.startTime)
        return listOfNotNull("가족보드 일정", time.ifBlank { null }).joinToString(" · ")
    }

    private fun nextReminderTrigger(e: CalendarEvent, fromMillis: Long): Long? {
        val zone = ZoneId.systemDefault()
        val reminder = e.reminder
        if (reminder == "none") return null
        return try {
            if (reminder.startsWith("custom:")) {
                val date = LocalDate.parse(reminder.removePrefix("custom:"))
                val t = LocalDateTime.of(date, eventTime(e)).atZone(zone).toInstant().toEpochMilli()
                if (t > fromMillis) t else null
            } else if (e.repeat.isBlank()) {
                val date = LocalDate.parse(e.startDateIso)
                val t = LocalDateTime.of(date, eventTime(e)).minusMinutes(offsetMinutes(reminder))
                    .atZone(zone).toInstant().toEpochMilli()
                if (t > fromMillis) t else null
            } else {
                // 반복: fromDate 부근부터 400일 창에서 발생일(회차 시작)을 모아 가장 이른 미래 알림 선택
                val fromDate = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate().minusDays(3)
                val map = RecurrenceExpander.expand(listOf(e), fromDate, fromDate.plusDays(400))
                val occStarts = map.entries
                    .filter { entry -> entry.value.any { it.spanStart } }
                    .map { LocalDate.parse(it.key) }
                    .sorted()
                val time = eventTime(e)
                val offset = offsetMinutes(reminder)
                for (occ in occStarts) {
                    val t = LocalDateTime.of(occ, time).minusMinutes(offset).atZone(zone).toInstant().toEpochMilli()
                    if (t > fromMillis) return t
                }
                null
            }
        } catch (t: Throwable) {
            Log.w(TAG, "트리거 계산 실패: ${e.title}", t); null
        }
    }

    private fun eventTime(e: CalendarEvent): LocalTime =
        if (e.allDay || e.startTime.isBlank()) LocalTime.of(9, 0)
        else runCatching { LocalTime.parse(e.startTime) }.getOrDefault(LocalTime.of(9, 0))

    private fun offsetMinutes(reminder: String): Long = when (reminder) {
        "atTime" -> 0; "5m" -> 5; "15m" -> 15; "30m" -> 30
        "1h" -> 60; "2h" -> 120; "1d" -> 1440; "2d" -> 2880
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

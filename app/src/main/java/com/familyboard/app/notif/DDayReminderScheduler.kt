package com.familyboard.app.notif

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.familyboard.app.data.Family
import com.familyboard.app.data.FamilyBirthdays
import com.familyboard.app.data.model.ListItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId

/**
 * D-Day/생일 알림을 AlarmManager 로 예약. 기기마다 로컬로 예약하므로 서버 없이도 각자 알림을 받는다.
 *  - 생일: D-7, D-1 을 가족 모두(모든 기기)가 예약 → 전원 알림.
 *  - 사용자 D-Day: notifyIds 에 '내'가 포함된 경우에만 이 기기에서 D-7, D-1 예약.
 * 오전 9시에 발화. 앱 실행 시 reconcile 로 다음 회차를 다시 예약한다.
 */
object DDayReminderScheduler {
    private const val TAG = "DDayReminder"
    private const val ACTION = "com.familyboard.app.DDAY_REMINDER"
    private const val PREFS = "dday_sched"
    private const val KEY = "keys"
    private val NOTIFY_HOUR = LocalTime.of(9, 0)

    private data class Alarm(val key: String, val trigger: Long, val title: String, val text: String)

    @Synchronized
    fun reconcile(context: Context, ddayItems: List<ListItem>, currentMemberId: String?) {
        ReminderScheduler.ensureChannel(context)
        val alarms = buildAlarms(ddayItems, currentMemberId)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val newKeys = alarms.map { it.key }.toSet()
        // 사라진 예약 취소 (영속 저장으로 재부팅/재시작 후에도 정리)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prev = prefs.getStringSet(KEY, emptySet()) ?: emptySet()
        (prev - newKeys).forEach { am.cancel(pi(context, it, "", "")) }
        armAlarms(context, am, alarms)
        prefs.edit().putStringSet(KEY, newKeys).apply()
        Log.i(TAG, "예약 ${alarms.size}건")
    }

    /** 재부팅 복원용: 취소·prefs 조작 없이 현재 대상 알람을 다시 무장(생일은 Firestore 없이도 가능). */
    @Synchronized
    fun rearm(context: Context, ddayItems: List<ListItem>, currentMemberId: String?) {
        ReminderScheduler.ensureChannel(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        armAlarms(context, am, buildAlarms(ddayItems, currentMemberId))
    }

    private fun buildAlarms(ddayItems: List<ListItem>, currentMemberId: String?): List<Alarm> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        val alarms = mutableListOf<Alarm>()

        // 1) 가족 생일 — 당사자는 당일 축하, 나머지는 D-7·D-1
        FamilyBirthdays.list.forEach { (id, birth) ->
            val next = nextAnniversary(birth, today)
            val name = Family.nameOf(id)
            val dateText = "${next.monthValue}월 ${next.dayOfDay()}일 (${krDow(next)})"
            if (id == currentMemberId) {
                addOne(alarms, zone, now, key = "bday_${id}_0", date = next,
                    title = "🎉 생일 축하해요!", text = "오늘은 ${name}님의 생일이에요 🎂")
            } else {
                addPair(alarms, zone, now, keyBase = "bday_$id", target = next,
                    d7Title = "🎂 ${name}님 생일 일주일 전", d1Title = "🎂 내일은 ${name}님 생일!", text = dateText)
            }
        }

        // 2) 사용자 D-Day (내가 알림 대상일 때만)
        val me = currentMemberId
        if (me != null) {
            ddayItems.forEach { item ->
                if (!item.notifyIds.contains(me)) return@forEach
                val base = runCatching { LocalDate.parse(item.dateIso) }.getOrNull() ?: return@forEach
                val target = if (item.yearly) nextAnniversary(base, today) else base
                val title = item.text.ifBlank { "D-Day" }
                addPair(alarms, zone, now, keyBase = "dday_${item.id}", target = target,
                    d7Title = "📌 $title 일주일 전", d1Title = "📌 내일 $title",
                    text = "${target.year}년 ${target.monthValue}월 ${target.dayOfDay()}일 (${krDow(target)})")
            }
        }
        return alarms
    }

    private fun armAlarms(context: Context, am: AlarmManager, alarms: List<Alarm>) {
        alarms.forEach { a ->
            val p = pi(context, a.key, a.title, a.text)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.trigger, p)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.trigger, p)
                }
            } catch (se: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, a.trigger, p)
            }
        }
    }

    private fun addPair(
        alarms: MutableList<Alarm>, zone: ZoneId, now: Long, keyBase: String,
        target: LocalDate, d7Title: String, d1Title: String, text: String,
    ) {
        listOf(7 to d7Title, 1 to d1Title).forEach { (daysBefore, title) ->
            addOne(alarms, zone, now, "${keyBase}_$daysBefore", target.minusDays(daysBefore.toLong()), title, text)
        }
    }

    private fun addOne(
        alarms: MutableList<Alarm>, zone: ZoneId, now: Long,
        key: String, date: LocalDate, title: String, text: String,
    ) {
        val at = LocalDateTime.of(date, NOTIFY_HOUR).atZone(zone).toInstant().toEpochMilli()
        if (at > now) alarms.add(Alarm(key, at, title, text))
    }

    private fun pi(context: Context, key: String, title: String, text: String): android.app.PendingIntent {
        val intent = Intent(context, DDayReminderReceiver::class.java).apply {
            action = ACTION
            data = android.net.Uri.parse("familyboard://dday/$key")
            putExtra("notifId", key.hashCode())
            putExtra("title", title)
            putExtra("text", text)
        }
        var f = android.app.PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f = f or android.app.PendingIntent.FLAG_IMMUTABLE
        return android.app.PendingIntent.getBroadcast(context, key.hashCode(), intent, f)
    }

    private fun nextAnniversary(date: LocalDate, today: LocalDate): LocalDate {
        val md = MonthDay.of(date.monthValue, date.dayOfMonth)
        var next = md.atYear(today.year)
        if (next.isBefore(today)) next = md.atYear(today.year + 1)
        return next
    }

    private fun LocalDate.dayOfDay() = dayOfMonth
    private val KR = listOf("월", "화", "수", "목", "금", "토", "일")
    private fun krDow(d: LocalDate) = KR[d.dayOfWeek.value - 1]
}

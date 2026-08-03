package com.familyboard.app.notif

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.familyboard.app.R

/**
 * 예약된 시각에 호출되어 폰 알림을 띄운다.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "일정 알림"
        val text = intent.getStringExtra("text") ?: "가족보드 일정"
        val notifId = intent.getIntExtra("notifId", title.hashCode())

        // 반복 일정이면 다음 회차 알림을 이어서 예약
        ReminderScheduler.eventFromIntent(intent)?.let { ReminderScheduler.rescheduleNext(context, it) }

        ReminderScheduler.ensureChannel(context)

        val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPi = openIntent?.let {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
            PendingIntent.getActivity(context, notifId, it, flags)
        }

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { contentPi?.let { setContentIntent(it) } }
            .build()

        // Android 13+ 알림 권한 확인
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }
}

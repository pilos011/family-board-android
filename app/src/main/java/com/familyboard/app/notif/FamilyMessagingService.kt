package com.familyboard.app.notif

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.familyboard.app.R
import com.familyboard.app.data.CurrentUserStore
import com.familyboard.app.data.Family
import com.familyboard.app.ui.emergency.EmergencyActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * FCM 수신. 토큰 갱신 시 서버에 재등록하고, 알림 종류에 따라 폰 알림/전체화면을 띄운다.
 */
class FamilyMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CH_EMERGENCY = "emergency"
    }

    override fun onNewToken(token: String) {
        scope.launch {
            val memberId = CurrentUserStore(applicationContext).currentMemberId.first()
            if (!memberId.isNullOrBlank()) {
                NotifyApi.register(memberId, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            "emergency" -> showEmergency(
                sender = data["sender"] ?: "",
                msg = data["msg"] ?: (message.notification?.body ?: ""),
                wantLoc = data["wantLoc"] == "1",
            )
            "location" -> showLocation(
                sender = data["sender"] ?: "",
                lat = data["lat"] ?: return,
                lng = data["lng"] ?: return,
            )
            "funshare" -> showFunShare(
                title = data["title"] ?: "재미진 항목 공유",
                body = data["body"] ?: "",
                itemId = data["itemId"] ?: return,
            )
            "updatereq" -> showUpdateReq(
                title = data["title"] ?: "앱 업데이트 안내",
                body = data["body"] ?: "",
            )
            else -> {
                val n = message.notification
                val title = n?.title ?: data["title"] ?: "가족보드"
                val body = n?.body ?: data["body"] ?: ""
                showNotification(title, body)
            }
        }
    }

    // ─────────── 긴급 연락: 전체화면 알림 ───────────
    private fun showEmergency(sender: String, msg: String, wantLoc: Boolean) {
        ensureEmergencyChannel()
        val full = Intent(this, EmergencyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EmergencyActivity.EXTRA_SENDER, sender)
            putExtra(EmergencyActivity.EXTRA_MESSAGE, msg)
            putExtra(EmergencyActivity.EXTRA_WANT_LOC, wantLoc)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(this, 7001, full, flags)

        val name = Family.nameOf(sender)
        val notif = NotificationCompat.Builder(this, CH_EMERGENCY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⏳ ${name}님의 빠른 연락 요청")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()

        if (canNotify()) NotificationManagerCompat.from(this).notify(9001, notif)
        // 앱이 포그라운드면 곧바로 전체화면 시도(백그라운드는 FSI가 처리)
        runCatching { startActivity(full) }
    }

    // ─────────── 위치 공유 수신 ───────────
    private fun showLocation(sender: String, lat: String, lng: String) {
        ReminderScheduler.ensureChannel(this)
        val name = Family.nameOf(sender)
        val geo = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode("$name 위치")})")
        val view = Intent(Intent.ACTION_VIEW, geo)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(this, 8001, view, flags)

        val body = "${name} 알림 확인하였으며, 위치를 공유합니다."
        val notif = NotificationCompat.Builder(this, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📍 위치 공유")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n탭하면 지도에서 위치를 봅니다"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        if (canNotify()) NotificationManagerCompat.from(this).notify(("loc$sender").hashCode(), notif)
    }

    // ─────────── 재미진 항목 공유: 탭하면 그 항목이 열리도록 itemId 전달 ───────────
    private fun showFunShare(title: String, body: String, itemId: String) {
        ReminderScheduler.ensureChannel(this)
        val open = Intent(this, com.familyboard.app.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(com.familyboard.app.MainActivity.EXTRA_OPEN_FUN, itemId)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(this, ("fun$itemId").hashCode(), open, flags)
        val notif = NotificationCompat.Builder(this, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        if (canNotify()) NotificationManagerCompat.from(this).notify(("fun$itemId").hashCode(), notif)
    }

    // ─────────── 업데이트 요청: 탭하면 앱에서 업데이트 창이 바로 뜨도록 ───────────
    private fun showUpdateReq(title: String, body: String) {
        ReminderScheduler.ensureChannel(this)
        val open = Intent(this, com.familyboard.app.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(com.familyboard.app.MainActivity.EXTRA_OPEN_UPDATE, true)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(this, "updatereq".hashCode(), open, flags)
        val notif = NotificationCompat.Builder(this, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        if (canNotify()) NotificationManagerCompat.from(this).notify("updatereq".hashCode(), notif)
    }

    // ─────────── 일반 알림 ───────────
    private fun showNotification(title: String, body: String) {
        ReminderScheduler.ensureChannel(this)
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPi = openIntent?.let {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
            PendingIntent.getActivity(this, title.hashCode(), it, flags)
        }
        val notification = NotificationCompat.Builder(this, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { contentPi?.let { setContentIntent(it) } }
            .build()
        if (canNotify()) NotificationManagerCompat.from(this).notify((title + body).hashCode(), notification)
    }

    private fun ensureEmergencyChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH_EMERGENCY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_EMERGENCY, "빠른 연락 요청", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "빠른 연락 요청 전체화면 알림"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
                    setBypassDnd(true)
                }
            )
        }
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

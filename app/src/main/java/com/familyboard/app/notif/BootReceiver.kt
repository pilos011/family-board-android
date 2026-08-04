package com.familyboard.app.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyboard.app.FamilyBoardApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 재부팅 시 AlarmManager 예약이 모두 사라지므로, 부팅 완료 후 알림/리포트 예약을 복원한다.
 *  - 일정 미리 알림, 생일/D-Day 알림: Firestore 첫 스냅샷을 받아 reconcile
 *  - HA 5분 리포트: 재예약
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = (app as FamilyBoardApp).container
                val mid = container.currentUserStore.currentMemberId.first()
                withTimeoutOrNull(15_000) {
                    val events = container.boardRepository.events().first()
                    ReminderScheduler.reconcile(app, events, mid)
                    val dday = container.boardRepository.items("dday").first()
                    DDayReminderScheduler.reconcile(app, dday, mid)
                }
                HaReportScheduler.schedule(app)
            } catch (_: Throwable) {
                // 부팅 직후 네트워크/인증 지연 등은 무시. 다음 앱 실행 시 다시 예약됨.
            } finally {
                pending.finish()
            }
        }
    }
}

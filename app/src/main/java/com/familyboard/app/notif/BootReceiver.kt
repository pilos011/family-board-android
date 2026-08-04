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
                val mid = withTimeoutOrNull(5_000) { container.currentUserStore.currentMemberId.first() }

                // Firestore/인증과 무관하게 항상 복원: HA 5분 리포트 + 가족 생일 알림
                runCatching { HaReportScheduler.schedule(app) }
                runCatching { DDayReminderScheduler.rearm(app, emptyList(), mid) }

                // Firestore 가 준비되면 일정 미리알림 + 사용자 D-Day 도 rearm(취소 없이 무장만 → 빈 스냅샷이 예약을 지우지 않음)
                withTimeoutOrNull(15_000) {
                    runCatching {
                        val events = container.boardRepository.events().first()
                        ReminderScheduler.rearm(app, events, mid)
                    }
                    runCatching {
                        val dday = container.boardRepository.items("dday").first()
                        DDayReminderScheduler.rearm(app, dday, mid)
                    }
                }
            } catch (_: Throwable) {
                // 부팅 직후 네트워크/인증 지연 등은 무시. 다음 앱 실행 시 reconcile 로 정합성 회복.
            } finally {
                pending.finish()
            }
        }
    }
}

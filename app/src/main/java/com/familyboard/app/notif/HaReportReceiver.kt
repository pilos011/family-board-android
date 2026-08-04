package com.familyboard.app.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyboard.app.data.CurrentUserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 5분마다 발화 → 현재 사용자 기기 정보를 HA로 전송하고 다음 회차를 재예약. */
class HaReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val memberId = CurrentUserStore(app).currentMemberId.first()
                HaReporter.report(app, memberId)
            } finally {
                HaReportScheduler.schedule(app) // 다음 5분 재예약
                pending.finish()
            }
        }
    }
}

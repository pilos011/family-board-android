package com.familyboard.app.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.Presence
import com.familyboard.app.ui.AppViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ManageScreen(
    modifier: Modifier = Modifier,
    vm: AppViewModel,
    onOpenEmergency: () -> Unit,
    onOpenNotice: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.refreshPresence() }
    val presence by vm.presence.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp),
    ) {
        Text("관리 기능", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("부모(선일·은선)를 위한 도구", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        Spacer(Modifier.height(20.dp))

        ToolCard(
            icon = Icons.Default.Campaign,
            tint = Color(0xFFE8894A),
            title = "빠른 연락 요청",
            desc = "전화·문자를 안 받을 때 전체화면으로 요청",
            onClick = onOpenEmergency,
        )
        Spacer(Modifier.height(12.dp))
        ToolCard(
            icon = Icons.Default.PushPin,
            tint = Color(0xFFE8A13A),
            title = "가족 공지사항",
            desc = "가족이 함께 지킬 안내·규칙 (부모 관리)",
            onClick = onOpenNotice,
        )
        Spacer(Modifier.height(20.dp))
        PresenceCard(presence)
    }
}

/** 가족 접속 현황: 각 구성원의 마지막 접속 시각·앱 버전(관리자 확인용). */
@Composable
private fun PresenceCard(presence: List<Presence>) {
    val byId = presence.associateBy { it.memberId }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("가족 접속 현황", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("각자 마지막 접속·설치 버전", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(Modifier.height(10.dp))
            Family.members.forEach { m ->
                val p = byId[m.id]
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(m.color))
                    Spacer(Modifier.size(8.dp))
                    Text(m.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp))
                    Text(
                        if (p != null && p.versionName.isNotBlank()) "v${p.versionName}" else "—",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.width(74.dp),
                    )
                    Text(
                        relTime(p?.lastSeen ?: 0),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** epoch millis → "방금/N분 전/N시간 전/N일 전/날짜". 0 이면 기록 없음. */
private fun relTime(ts: Long): String {
    if (ts <= 0L) return "기록 없음"
    val m = (System.currentTimeMillis() - ts) / 60000L
    return when {
        m < 0 -> "방금"
        m < 1 -> "방금"
        m < 60 -> "${m}분 전"
        m < 1440 -> "${m / 60}시간 전"
        m < 43200 -> "${m / 1440}일 전"
        else -> java.text.SimpleDateFormat("yyyy.MM.dd", java.util.Locale.KOREA).format(java.util.Date(ts))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolCard(icon: ImageVector, tint: Color, title: String, desc: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint) }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(desc, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

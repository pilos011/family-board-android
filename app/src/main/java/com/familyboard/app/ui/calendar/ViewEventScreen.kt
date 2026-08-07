package com.familyboard.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.data.model.Reminders
import com.familyboard.app.ui.AppViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Ink = Color(0xFF2B2B2E)

/** 반복 일정의 특정 회차 날짜 범위(dateIso = 그 회차 시작일 기준). */
private fun occurrenceRange(e: CalendarEvent, dateIso: String): String {
    val s = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return dateRange(e)
    val dur = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(e.startDateIso), LocalDate.parse(e.endDateIso.ifBlank { e.startDateIso }))
    }.getOrDefault(0L).coerceAtLeast(0L)
    return if (dur == 0L) s.toString() else "$s ~ ${s.plusDays(dur)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewEventScreen(
    vm: AppViewModel,
    eventId: String,
    dateIso: String,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val events by vm.events.collectAsStateWithLifecycle()
    val event = remember(events, eventId) { events.firstOrNull { it.id == eventId } }
    var showDelete by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("일정") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    if (event != null) IconButton(onClick = { onEdit(eventId) }) { Icon(Icons.Default.Edit, "수정") }
                },
            )
        },
    ) { padding ->
        if (event == null) {
            Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("삭제된 일정입니다.", color = Ink.copy(alpha = 0.5f))
            }
            return@Scaffold
        }

        val time = if (event.allDay) "하루 종일"
        else listOf(event.startTime, event.endTime).filter { it.isNotBlank() }.joinToString(" ~ ")

        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState())
                .padding(20.dp).fillMaxWidth(),
        ) {
            Text(event.title, style = MaterialTheme.typography.headlineMedium, color = Ink)
            Spacer(Modifier.height(12.dp))
            MemberTags(event.memberIds)
            Spacer(Modifier.height(16.dp))

            // 반복 일정은 원본 첫 회차가 아니라, 지금 연 회차(dateIso)의 날짜를 보여준다
            DetailRow("날짜", if (event.repeat.isBlank()) dateRange(event) else occurrenceRange(event, dateIso))
            DetailRow("시간", if (time.isNotBlank()) time else "미정")
            if (event.repeat.isNotBlank()) DetailRow("반복", repeatLabel(event.repeat))
            if (event.lunar) DetailRow("음력", "예")
            if (event.reminder != "none") DetailRow("알림", Reminders.label(event.reminder))

            if (event.description.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("상세 내용", fontWeight = FontWeight.SemiBold, color = Ink.copy(alpha = 0.6f))
                Spacer(Modifier.height(6.dp))
                DescriptionText(event.description)
            }
            if (event.photoUrls.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("사진", fontWeight = FontWeight.SemiBold, color = Ink.copy(alpha = 0.6f))
                Spacer(Modifier.height(6.dp))
                PhotoStrip(event.photoUrls)
            }

            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onEdit(eventId) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, null); Spacer(Modifier.size(6.dp)); Text("수정")
                }
                OutlinedButton(
                    onClick = { showDelete = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.size(6.dp)); Text("삭제")
                }
            }
        }
    }

    if (showDelete && event != null) {
        if (event.repeat.isNotBlank()) {
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("반복 일정 삭제") },
                text = { Text("이 일정은 반복 일정입니다. 어떻게 삭제할까요?") },
                confirmButton = {
                    TextButton(onClick = { showDelete = false; vm.deleteEvent(eventId); onBack() }) { Text("모든 반복 삭제") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            showDelete = false; vm.excludeOccurrence(event, dateIso); onBack()
                        }) { Text("이 날짜만") }
                        TextButton(onClick = { showDelete = false }) { Text("취소") }
                    }
                },
            )
        } else {
            // 일반(비반복) 일정도 다른 리스트처럼 삭제 확인
            AlertDialog(
                onDismissRequest = { showDelete = false },
                title = { Text("일정 삭제") },
                text = { Text("'${event.title}' 일정을 삭제할까요?") },
                confirmButton = {
                    TextButton(onClick = { showDelete = false; vm.deleteEvent(eventId); onBack() }) { Text("삭제") }
                },
                dismissButton = { TextButton(onClick = { showDelete = false }) { Text("취소") } },
            )
        }
    }
}

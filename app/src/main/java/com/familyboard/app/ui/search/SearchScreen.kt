package com.familyboard.app.ui.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.ui.AppViewModel
import java.time.LocalDate

private val Ink = Color(0xFF2B2B2E)
private val WeekFull = listOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")

private sealed interface Row {
    data class Header(val date: LocalDate) : Row
    data class Item(val event: CalendarEvent) : Row
    data class Marker(val text: String) : Row
}

@Composable
fun SearchScreen(vm: AppViewModel, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val events by vm.events.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    val rows = remember(events, query) { buildRows(events, query, today) }
    val targetIndex = remember(rows) {
        rows.indexOfFirst { it is Row.Header && !(it.date.isBefore(today)) }
            .let { if (it >= 0) it else rows.indexOfLast { r -> r is Row.Header } }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(targetIndex, rows.size) {
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        SearchBar(query = query, onQuery = { query = it }, onBack = onBack, onClear = { query = "" })

        if (rows.none { it is Row.Item }) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    if (query.isBlank()) "일정이 없어요." else "\"$query\" 검색 결과가 없어요.",
                    color = Ink.copy(alpha = 0.5f),
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(rows) { _, row ->
                    when (row) {
                        is Row.Header -> DateHeader(row.date)
                        is Row.Item -> EventRow(row.event)
                        is Row.Marker -> Text(
                            row.text,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun buildRows(events: List<CalendarEvent>, query: String, today: LocalDate): List<Row> {
    val q = query.trim()
    val filtered = events
        .filter { it.startDateIso.isNotBlank() }
        .filter { q.isBlank() || it.title.contains(q, ignoreCase = true) || Family.namesOf(it.memberIds).contains(q, true) }
        .sortedWith(compareBy({ it.startDateIso }, { it.startTime }))

    val rows = mutableListOf<Row>()
    rows.add(Row.Marker(koreanDate(today) + " 이전 검색"))
    filtered.groupBy { LocalDate.parse(it.startDateIso) }
        .toSortedMap()
        .forEach { (date, evs) ->
            rows.add(Row.Header(date))
            evs.forEach { rows.add(Row.Item(it)) }
        }
    rows.add(Row.Marker(koreanDate(today.plusYears(1)) + " 이후 검색"))
    return rows
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onBack: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Ink) }
        TextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            placeholder = { Text("일정 검색") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) { Icon(Icons.Default.Close, "지우기") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF1F3F5),
                unfocusedContainerColor = Color(0xFFF1F3F5),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(14.dp),
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    Text(
        koreanDate(date),
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F3F5))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Ink.copy(alpha = 0.75f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
}

@Composable
private fun EventRow(event: CalendarEvent) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 왼쪽 시간
        Column(Modifier.width(64.dp)) {
            if (event.allDay) {
                Text("하루 종일", fontSize = 12.sp, color = Ink.copy(alpha = 0.7f))
            } else {
                if (event.startTime.isNotBlank())
                    Text(koreanTime(event.startTime), fontSize = 13.sp, color = Ink)
                if (event.endTime.isNotBlank())
                    Text(koreanTime(event.endTime), fontSize = 11.sp, color = Ink.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.size(8.dp))
        // 오른쪽 색상 카드
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                .background(Family.colorOfIds(event.memberIds)).padding(14.dp),
        ) {
            Text(event.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, null, tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text(Family.namesOf(event.memberIds), color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp)
            }
        }
    }
}

private fun koreanDate(d: LocalDate): String =
    "${d.monthValue}월 ${d.dayOfMonth}일 ${WeekFull[d.dayOfWeek.value % 7]}"

private fun koreanTime(hhmm: String): String {
    val parts = hhmm.split(":")
    if (parts.size < 2) return hhmm
    val h = parts[0].toIntOrNull() ?: return hhmm
    val m = parts[1]
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$ampm $h12:$m"
}

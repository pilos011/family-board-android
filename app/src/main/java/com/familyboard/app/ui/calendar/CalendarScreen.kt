package com.familyboard.app.ui.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyboard.app.data.Family
import com.familyboard.app.data.LunarCalendar
import com.familyboard.app.data.RecurrenceExpander
import com.familyboard.app.data.SolarTerms
import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.ui.AppViewModel
import java.time.LocalDate
import java.time.YearMonth

private val Sunday = Color(0xFFE03131)
private val Saturday = Color(0xFF1C7ED6)
private val GridLine = Color(0xFFECECEC)
private val Ink = Color(0xFF2B2B2E)

private val WeekdayNames = listOf("일", "월", "화", "수", "목", "금", "토")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onAddEvent: (LocalDate, LocalDate) -> Unit,
    onEditEvent: (String) -> Unit,
    onSearch: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val events by vm.events.collectAsStateWithLifecycle()
    val holidays by vm.holidays.collectAsStateWithLifecycle()

    LaunchedEffect(month) { vm.ensureHolidays(month) }

    // 6주 그리드 표시 구간 (일요일 시작)
    val gridStart = remember(month) {
        val first = month.atDay(1)
        first.minusDays((first.dayOfWeek.value % 7).toLong())
    }
    val eventsByDate = remember(events, month) {
        RecurrenceExpander.expand(events, gridStart, gridStart.plusDays(41))
    }

    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxSize()) {
            MonthHeader(
                month = month,
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
                onToday = { month = YearMonth.now(); selected = LocalDate.now() },
                onSearch = onSearch,
            )
            WeekdayHeader()
            MonthGrid(
                month = month,
                selected = selected,
                today = LocalDate.now(),
                gridStart = gridStart,
                eventsByDate = eventsByDate,
                holidays = holidays,
                modifier = Modifier.weight(1f),
                onSelect = { selected = it; sheetOpen = true },
                onAddRange = { s, e -> onAddEvent(s, e) },
            )
        }

        FloatingActionButton(
            onClick = { onAddEvent(selected, selected) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
        ) { Icon(Icons.Default.Add, contentDescription = "일정 추가", tint = Color.White) }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = Color.White,
        ) {
            DaySheet(
                date = selected,
                holidayName = holidays[selected.toString()],
                events = eventsByDate[selected.toString()].orEmpty(),
                onAdd = { sheetOpen = false; onAddEvent(selected, selected) },
                onEdit = { id -> sheetOpen = false; onEditEvent(id) },
                onDeleteAll = { id -> vm.deleteEvent(id) },
                onExcludeOccurrence = { ev -> vm.excludeOccurrence(ev, selected.toString()) },
            )
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${month.year}년 ${month.monthValue}월",
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "일정 검색", tint = Ink) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onToday) { Text("오늘") }
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "이전 달", tint = Ink) }
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "다음 달", tint = Ink) }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth()) {
        WeekdayNames.forEachIndexed { i, d ->
            Text(
                d,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
                color = when (i) { 0 -> Sunday; 6 -> Saturday; else -> Ink },
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    gridStart: LocalDate,
    eventsByDate: Map<String, List<CalendarEvent>>,
    holidays: Map<String, String>,
    modifier: Modifier = Modifier,
    onSelect: (LocalDate) -> Unit,
    onAddRange: (LocalDate, LocalDate) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragAnchor by remember { mutableStateOf<Int?>(null) }
    var dragCurrent by remember { mutableStateOf<Int?>(null) }

    fun cellAt(off: Offset): Int {
        if (size.width == 0 || size.height == 0) return 0
        val col = (off.x / (size.width / 7f)).toInt().coerceIn(0, 6)
        val row = (off.y / (size.height / 6f)).toInt().coerceIn(0, 5)
        return row * 7 + col
    }

    val dragLo = if (dragAnchor != null && dragCurrent != null) minOf(dragAnchor!!, dragCurrent!!) else -1
    val dragHi = if (dragAnchor != null && dragCurrent != null) maxOf(dragAnchor!!, dragCurrent!!) else -2

    Column(
        modifier
            .fillMaxWidth()
            .onSizeChanged { size = it }
            .pointerInput(gridStart, size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startIdx = cellAt(down.position)
                    dragAnchor = startIdx
                    dragCurrent = startIdx
                    var dragged = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change == null || !change.pressed) break
                        val idx = cellAt(change.position)
                        if (idx != startIdx) { dragged = true; change.consume() }
                        dragCurrent = idx
                    }
                    val a = dragAnchor
                    val b = dragCurrent
                    dragAnchor = null
                    dragCurrent = null
                    if (a != null && b != null) {
                        val s = minOf(a, b); val e = maxOf(a, b)
                        if (!dragged || s == e) onSelect(gridStart.plusDays(s.toLong()))
                        else onAddRange(gridStart.plusDays(s.toLong()), gridStart.plusDays(e.toLong()))
                    }
                }
            },
    ) {
        repeat(6) { w ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                repeat(7) { d ->
                    val idx = w * 7 + d
                    val date = gridStart.plusDays(idx.toLong())
                    DayCell(
                        date = date,
                        inMonth = date.month == month.month,
                        isToday = date == today,
                        isSelected = date == selected,
                        inDragRange = idx in dragLo..dragHi,
                        dayOfWeek = d,
                        events = eventsByDate[date.toString()].orEmpty(),
                        holidayName = holidays[date.toString()],
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    inDragRange: Boolean,
    dayOfWeek: Int,
    events: List<CalendarEvent>,
    holidayName: String?,
    modifier: Modifier = Modifier,
) {
    val isHoliday = holidayName != null
    val numberColor = when {
        isHoliday || dayOfWeek == 0 -> Sunday
        dayOfWeek == 6 -> Saturday
        else -> Ink
    }.copy(alpha = if (inMonth) 1f else 0.35f)

    val lunar = remember(date) { LunarCalendar.label(date) }
    val term = remember(date) { SolarTerms.of(date) }

    val cellBg = when {
        inDragRange -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> Color.White
    }
    Box(
        modifier = modifier
            .border(0.5.dp, GridLine)
            .background(cellBg)
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.size(20.dp)
                    .then(if (isToday) Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    color = if (isToday) Color.White else numberColor,
                    fontSize = 12.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
            if (holidayName != null && inMonth) {
                Text(
                    holidayName,
                    color = Sunday,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(2.dp))
            events.take(2).forEach { EventLabel(it) }
            if (events.size > 2) {
                Text(
                    "+${events.size - 2}",
                    fontSize = 9.sp,
                    color = Ink.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }
        // 오른쪽 하단 음력 + 절기 (작고 옅게)
        Text(
            if (term != null) "$lunar ($term)" else lunar,
            modifier = Modifier.align(Alignment.BottomEnd),
            fontSize = 8.sp,
            maxLines = 1,
            color = if (term != null) Color(0xFF8A6D3B).copy(alpha = if (inMonth) 1f else 0.4f)
            else Color(0xFFB0B0B0).copy(alpha = if (inMonth) 1f else 0.4f),
        )
    }
}

@Composable
private fun EventLabel(event: CalendarEvent) {
    Box(
        Modifier.fillMaxWidth()
            .padding(bottom = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Family.colorOfIds(event.memberIds))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            event.title,
            color = Color.White,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun DaySheet(
    date: LocalDate,
    holidayName: String?,
    events: List<CalendarEvent>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleteAll: (String) -> Unit,
    onExcludeOccurrence: (CalendarEvent) -> Unit,
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${date.monthValue}월 ${date.dayOfMonth}일 (${WeekdayNames[date.dayOfWeek.value % 7]})",
                style = MaterialTheme.typography.titleLarge,
                color = Ink,
            )
            if (holidayName != null) {
                Spacer(Modifier.size(8.dp))
                Chip(holidayName, Sunday)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (events.isEmpty()) {
            Text(
                "일정이 없어요.",
                color = Ink.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            events.forEach { ev ->
                EventCard(
                    event = ev,
                    expanded = expandedId == ev.id,
                    onToggle = { expandedId = if (expandedId == ev.id) null else ev.id },
                    onEdit = { onEdit(ev.id) },
                    onDeleteAll = { onDeleteAll(ev.id); if (expandedId == ev.id) expandedId = null },
                    onExcludeOccurrence = { onExcludeOccurrence(ev); if (expandedId == ev.id) expandedId = null },
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White)
            Spacer(Modifier.size(6.dp))
            Text("이 날 일정 추가")
        }
    }
}

@Composable
private fun EventCard(
    event: CalendarEvent,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDeleteAll: () -> Unit,
    onExcludeOccurrence: () -> Unit,
) {
    val time = if (event.allDay) "하루 종일"
    else listOf(event.startTime, event.endTime).filter { it.isNotBlank() }.joinToString(" ~ ")
    var showDelete by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onToggle() }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            MemberTags(event.memberIds)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(
                    if (time.isNotBlank()) time else "시간 미정",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink.copy(alpha = 0.6f),
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            if (event.description.isNotBlank()) {
                DescriptionText(event.description)
                Spacer(Modifier.height(8.dp))
            }
            if (event.photoUrls.isNotEmpty()) {
                PhotoStrip(event.photoUrls)
                Spacer(Modifier.height(8.dp))
            }
            DetailRow("시간", if (time.isNotBlank()) time else "미정")
            DetailRow("날짜", dateRange(event))
            if (event.repeat.isNotBlank()) DetailRow("반복", repeatLabel(event.repeat))
            if (event.lunar) DetailRow("음력", "예")
            if (event.reminder != "none") DetailRow("알림", com.familyboard.app.data.model.Reminders.label(event.reminder))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null); Spacer(Modifier.size(6.dp)); Text("수정")
                }
                OutlinedButton(onClick = { if (event.repeat.isNotBlank()) showDelete = true else onDeleteAll() }) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.size(6.dp)); Text("삭제")
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("반복 일정 삭제") },
            text = { Text("이 일정은 반복 일정입니다. 어떻게 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDeleteAll() }) { Text("모든 반복 삭제") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDelete = false; onExcludeOccurrence() }) { Text("이 날짜만") }
                    TextButton(onClick = { showDelete = false }) { Text("취소") }
                }
            },
        )
    }
}

/** 첨부 사진 썸네일 가로 스크롤 + 탭 시 전체보기 */
@Composable
private fun PhotoStrip(urls: List<String>) {
    var viewing by remember { mutableStateOf<String?>(null) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        urls.forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).clickable { viewing = url },
                contentScale = ContentScale.Crop,
            )
        }
    }
    viewing?.let { url ->
        Dialog(onDismissRequest = { viewing = null }) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { viewing = null },
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** 상세 내용 표시. URL 은 클릭 시 외부 브라우저로 연다. */
@Composable
private fun DescriptionText(text: String) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            val matcher = android.util.Patterns.WEB_URL.matcher(text)
            var last = 0
            while (matcher.find()) {
                val s = matcher.start(); val e = matcher.end()
                if (s > last) append(text.substring(last, s))
                val url = text.substring(s, e)
                pushStringAnnotation("URL", url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(url) }
                pop()
                last = e
            }
            if (last < text.length) append(text.substring(last))
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = Ink),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { ann ->
                val url = if (ann.item.startsWith("http")) ann.item else "http://${ann.item}"
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        },
    )
}

/** 담당자 태그(색상 + 이름)를 세로로 쌓아 표시 */
@Composable
private fun MemberTags(memberIds: List<String>) {
    val ids = if (memberIds.isEmpty() || memberIds.contains(Family.ALL_ID)) listOf(Family.ALL_ID) else memberIds
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ids.forEach { id ->
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F3F5))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Family.colorOf(id)))
                Spacer(Modifier.size(6.dp))
                Text(Family.nameOf(id), fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Ink.copy(alpha = 0.5f), modifier = Modifier.width(56.dp), fontSize = 14.sp)
        Text(value, color = Ink, fontSize = 14.sp)
    }
}

private fun repeatLabel(key: String): String = when (key) {
    "weekly" -> "매주"
    "monthly" -> "매월"
    "yearly" -> "매년"
    else -> ""
}

private fun dateRange(e: CalendarEvent): String =
    if (e.endDateIso.isBlank() || e.endDateIso == e.startDateIso) e.startDateIso
    else "${e.startDateIso} ~ ${e.endDateIso}"

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

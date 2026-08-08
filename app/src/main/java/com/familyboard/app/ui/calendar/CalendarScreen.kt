package com.familyboard.app.ui.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyboard.app.data.DayEvent
import com.familyboard.app.data.Family
import com.familyboard.app.data.LunarCalendar
import com.familyboard.app.data.RecurrenceExpander
import com.familyboard.app.data.SolarTerms
import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.notif.UpdateChecker
import com.familyboard.app.ui.AppViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

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
    onViewEvent: (String, String) -> Unit,
    onSearch: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val events by vm.calendarEvents.collectAsStateWithLifecycle() // 일반 일정 + D-Day(표시용)
    val holidays by vm.holidays.collectAsStateWithLifecycle()

    var showYearPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

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
                onYearClick = { showYearPicker = true },
                onMonthClick = { showMonthPicker = true },
            )
            WeekdayHeader()
            AnimatedContent(
                targetState = month,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    // 다음 달이면 왼쪽으로, 이전 달이면 오른쪽으로 슬라이드
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(320)) { w -> dir * w } + fadeIn(tween(320)))
                        .togetherWith(slideOutHorizontally(tween(320)) { w -> -dir * w } + fadeOut(tween(320)))
                },
                label = "monthSlide",
            ) { m ->
                val gStart = remember(m) {
                    val f = m.atDay(1); f.minusDays((f.dayOfWeek.value % 7).toLong())
                }
                val evByDate = remember(events, m) {
                    RecurrenceExpander.expand(events, gStart, gStart.plusDays(41))
                }
                MonthGrid(
                    month = m,
                    selected = selected,
                    today = LocalDate.now(),
                    gridStart = gStart,
                    eventsByDate = evByDate,
                    holidays = holidays,
                    modifier = Modifier.fillMaxSize(),
                    onSelect = { selected = it; sheetOpen = true },
                    onAddRange = { s, e -> onAddEvent(s, e) },
                    onPrevMonth = { month = month.minusMonths(1) },
                    onNextMonth = { month = month.plusMonths(1) },
                )
            }
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
                // D-Day·생일 가상 이벤트는 시트에서 제외(편집 대상 아님, 그리드에만 표시)
                events = eventsByDate[selected.toString()].orEmpty().map { it.event }
                    .filter { !it.id.startsWith("dday_") && !it.id.startsWith("bday_") },
                onAdd = { sheetOpen = false; onAddEvent(selected, selected) },
                onView = { id -> sheetOpen = false; onViewEvent(id, selected.toString()) },
            )
        }
    }

    if (showYearPicker) {
        YearPickerDialog(
            current = month.year,
            onPick = { month = month.withYear(it); showYearPicker = false },
            onDismiss = { showYearPicker = false },
        )
    }
    if (showMonthPicker) {
        MonthPickerDialog(
            current = month.monthValue,
            onPick = { month = month.withMonth(it); showMonthPicker = false },
            onDismiss = { showMonthPicker = false },
        )
    }
}

@Composable
private fun YearPickerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val years = remember(current) { (current - 60..current + 20).toList() }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val i = years.indexOf(current)
        if (i >= 0) listState.scrollToItem((i - 3).coerceAtLeast(0))
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.padding(vertical = 12.dp).width(220.dp)) {
                Text("연도 선택", Modifier.padding(start = 20.dp, bottom = 8.dp),
                    fontWeight = FontWeight.Bold, color = Ink)
                LazyColumn(state = listState, modifier = Modifier.height(320.dp)) {
                    items(years) { y ->
                        val on = y == current
                        Text(
                            "${y}년",
                            Modifier.fillMaxWidth().clickable { onPick(y) }
                                .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .padding(vertical = 12.dp),
                            textAlign = TextAlign.Center,
                            color = if (on) MaterialTheme.colorScheme.primary else Ink,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthPickerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
            Column(Modifier.padding(16.dp).width(280.dp)) {
                Text("월 선택", Modifier.padding(bottom = 12.dp), fontWeight = FontWeight.Bold, color = Ink)
                for (row in 0..3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (col in 0..2) {
                            val m = row * 3 + col + 1
                            val on = m == current
                            Box(
                                Modifier.weight(1f).padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primary else Color(0xFFF1F3F5))
                                    .clickable { onPick(m) }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${m}월", color = if (on) Color.White else Ink,
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
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
    onYearClick: () -> Unit,
    onMonthClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${month.year}년",
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onYearClick() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "${month.monthValue}월",
            style = MaterialTheme.typography.headlineMedium,
            color = Ink,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onMonthClick() }
                .padding(horizontal = 4.dp, vertical = 2.dp),
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
    eventsByDate: Map<String, List<DayEvent>>,
    holidays: Map<String, String>,
    modifier: Modifier = Modifier,
    onSelect: (LocalDate) -> Unit,
    onAddRange: (LocalDate, LocalDate) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragAnchor by remember { mutableStateOf<Int?>(null) }
    var dragCurrent by remember { mutableStateOf<Int?>(null) }
    // 드래그로 정한 기간(상대 인덱스 s..e). 손을 떼면 바로 추가하지 않고 확인 버튼을 띄운다.
    var pendingRange by remember(gridStart) { mutableStateOf<Pair<Int, Int>?>(null) }
    val haptic = LocalHapticFeedback.current

    fun cellAt(off: Offset): Int {
        if (size.width == 0 || size.height == 0) return 0
        val col = (off.x / (size.width / 7f)).toInt().coerceIn(0, 6)
        val row = (off.y / (size.height / 6f)).toInt().coerceIn(0, 5)
        return row * 7 + col
    }

    // 하이라이트 구간: 진행 중 드래그 우선, 없으면 확인 대기 중인 pendingRange
    val hlLo: Int
    val hlHi: Int
    if (dragAnchor != null && dragCurrent != null) {
        hlLo = minOf(dragAnchor!!, dragCurrent!!); hlHi = maxOf(dragAnchor!!, dragCurrent!!)
    } else if (pendingRange != null) {
        hlLo = pendingRange!!.first; hlHi = pendingRange!!.second
    } else { hlLo = -1; hlHi = -2 }

    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size = it }
                .pointerInput(gridStart, size) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startIdx = cellAt(down.position)
                        val slop = viewConfiguration.touchSlop

                        // 1) 롱프레스 타임아웃 안에 이동/해제 여부로 동작 판정
                        val phase = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id }
                                if (ch == null || !ch.pressed) return@withTimeoutOrNull "tap"
                                val dx = ch.position.x - down.position.x
                                val dy = ch.position.y - down.position.y
                                if (abs(dx) > slop || abs(dy) > slop) return@withTimeoutOrNull "swipe"
                            }
                            @Suppress("UNREACHABLE_CODE") "swipe"
                        }

                        when (phase) {
                            // 롱프레스(움직임 없이 유지) → 여러 날 선택 모드
                            null -> {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dragAnchor = startIdx
                                dragCurrent = startIdx
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    dragCurrent = cellAt(ch.position)
                                    ch.consume()
                                    if (!ch.pressed) break
                                }
                                val a = dragAnchor; val b = dragCurrent
                                dragAnchor = null; dragCurrent = null
                                if (a != null && b != null) {
                                    val s = minOf(a, b); val e = maxOf(a, b)
                                    if (s == e) { pendingRange = null; onSelect(gridStart.plusDays(s.toLong())) }
                                    else pendingRange = s to e   // 바로 추가하지 않고 확인 버튼 표시
                                }
                            }
                            // 빠른 탭 → 그날 선택
                            "tap" -> {
                                pendingRange = null
                                onSelect(gridStart.plusDays(startIdx.toLong()))
                            }
                            // 스와이프 → 방향 판정으로 월 이동 (좌/위=이전은 아래 참고)
                            else -> {
                                var lastPos = down.position
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    lastPos = ch.position
                                    ch.consume()
                                    if (!ch.pressed) break
                                }
                                val dx = lastPos.x - down.position.x
                                val dy = lastPos.y - down.position.y
                                val thX = (if (size.width > 0) size.width else 1000) * 0.12f
                                val thY = (if (size.height > 0) size.height else 1000) * 0.10f
                                if (abs(dx) >= abs(dy)) {
                                    // 왼쪽으로 밀기=다음 달, 오른쪽=이전 달
                                    if (dx <= -thX) onNextMonth() else if (dx >= thX) onPrevMonth()
                                } else {
                                    // 위로 밀기=이전 달, 아래로=다음 달
                                    if (dy <= -thY) onPrevMonth() else if (dy >= thY) onNextMonth()
                                }
                            }
                        }
                    }
                },
        ) {
            repeat(6) { w ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    repeat(7) { d ->
                        val idx = w * 7 + d
                        val date = gridStart.plusDays(idx.toLong())
                        val hName = holidays[date.toString()]
                        // 같은 이름의 공휴일이 연달아 있으면 사용자 일정처럼 하나의 막대로 이어 그림
                        val hSpanStart = hName != null && holidays[date.minusDays(1).toString()] != hName
                        val hSpanEnd = hName != null && holidays[date.plusDays(1).toString()] != hName
                        DayCell(
                            date = date,
                            inMonth = date.month == month.month,
                            isToday = date == today,
                            isSelected = date == selected,
                            inDragRange = idx in hlLo..hlHi,
                            dayOfWeek = d,
                            dayEvents = eventsByDate[date.toString()].orEmpty(),
                            holidayName = hName,
                            holidaySpanStart = hSpanStart,
                            holidaySpanEnd = hSpanEnd,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }

        // 드래그로 정한 기간 확인(추가/취소) — 뗀 자리(마지막 행) 근처에 표시
        pendingRange?.let { (s, e) ->
            val sDate = gridStart.plusDays(s.toLong())
            val eDate = gridStart.plusDays(e.toLong())
            val cellH = if (size.height > 0) size.height / 6 else 0
            val midRow = ((s / 7) + (e / 7)) / 2
            val yPx = (midRow * cellH + cellH / 2)
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, yPx) }
                    .shadow(6.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${sDate.monthValue}/${sDate.dayOfMonth} ~ ${eDate.monthValue}/${eDate.dayOfMonth}",
                    color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(6.dp))
                TextButton(onClick = { pendingRange = null }) { Text("취소") }
                Button(onClick = {
                    pendingRange = null
                    onAddRange(sDate, eDate)
                }) { Text("추가") }
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
    dayEvents: List<DayEvent>,
    holidayName: String?,
    holidaySpanStart: Boolean = true,
    holidaySpanEnd: Boolean = true,
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
            .padding(vertical = 2.dp), // 가로 패딩 없음 → 여러 날 막대가 칸 끝까지 이어짐
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.padding(start = 3.dp).size(20.dp)
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
            Spacer(Modifier.height(2.dp))
            if (holidayName != null) {
                HolidayLabel(
                    name = holidayName,
                    dayOfWeek = dayOfWeek,
                    spanStart = holidaySpanStart,
                    spanEnd = holidaySpanEnd,
                    dim = !inMonth,
                )
            }
            dayEvents.take(2).forEach { EventLabel(it, dayOfWeek) }
            if (dayEvents.size > 2) {
                Text(
                    "+${dayEvents.size - 2}",
                    fontSize = 9.sp,
                    color = Ink.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        // 오른쪽 하단 음력 + 절기 (작고 옅게)
        Text(
            if (term != null) "$lunar ($term)" else lunar,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp),
            fontSize = 8.sp,
            maxLines = 1,
            color = if (term != null) Color(0xFF8A6D3B).copy(alpha = if (inMonth) 1f else 0.4f)
            else Color(0xFFB0B0B0).copy(alpha = if (inMonth) 1f else 0.4f),
        )
    }
}

@Composable
private fun HolidayLabel(name: String, dayOfWeek: Int, spanStart: Boolean, spanEnd: Boolean, dim: Boolean) {
    // 사용자 일정 막대(EventLabel)와 동일한 방식: 회차/주 양끝에서 모서리 둥글게 → 연휴가 하나의 막대로 이어짐
    val roundLeft = spanStart || dayOfWeek == 0
    val roundRight = spanEnd || dayOfWeek == 6
    val r = 4.dp
    val shape = RoundedCornerShape(
        topStart = if (roundLeft) r else 0.dp, bottomStart = if (roundLeft) r else 0.dp,
        topEnd = if (roundRight) r else 0.dp, bottomEnd = if (roundRight) r else 0.dp,
    )
    Box(
        Modifier.fillMaxWidth()
            .padding(bottom = 2.dp, start = if (roundLeft) 1.dp else 0.dp, end = if (roundRight) 1.dp else 0.dp)
            .clip(shape)
            .background(Sunday.copy(alpha = if (dim) 0.4f else 1f))
            .height(14.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // 이름은 막대(주 세그먼트)의 시작 칸에만 (폰트 크기는 원래대로)
        if (roundLeft) {
            Text(
                name,
                color = Color.White,
                fontSize = 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EventLabel(dayEvent: DayEvent, dayOfWeek: Int) {
    val e = dayEvent.event
    // 이 칸이 (그 회차) 기간의 시작/끝이거나 주(週)의 양끝(일/토)이면 모서리를 둥글게 → 여러 날은 하나의 막대처럼 이어짐
    val roundLeft = dayEvent.spanStart || dayOfWeek == 0
    val roundRight = dayEvent.spanEnd || dayOfWeek == 6
    val r = 4.dp
    val shape = RoundedCornerShape(
        topStart = if (roundLeft) r else 0.dp, bottomStart = if (roundLeft) r else 0.dp,
        topEnd = if (roundRight) r else 0.dp, bottomEnd = if (roundRight) r else 0.dp,
    )
    Box(
        Modifier.fillMaxWidth()
            .padding(bottom = 2.dp, start = if (roundLeft) 1.dp else 0.dp, end = if (roundRight) 1.dp else 0.dp)
            .clip(shape)
            .background(Family.colorOfIds(e.memberIds))
            .height(17.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // 제목은 막대(주 세그먼트)의 시작 칸에만 표시
        if (roundLeft) {
            Text(
                e.title,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DaySheet(
    date: LocalDate,
    holidayName: String?,
    events: List<CalendarEvent>,
    onAdd: () -> Unit,
    onView: (String) -> Unit,
) {
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
                EventCard(event = ev, onClick = { onView(ev.id) })
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
private fun EventCard(event: CalendarEvent, onClick: () -> Unit) {
    val time = if (event.allDay) "하루 종일"
    else listOf(event.startTime, event.endTime).filter { it.isNotBlank() }.joinToString(" ~ ")
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        MemberTags(event.memberIds)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.titleMedium, color = Ink)
            val multiDay = event.endDateIso.isNotBlank() && event.endDateIso != event.startDateIso
            if (multiDay) {
                Text(
                    spanText(event),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    if (time.isNotBlank()) time else "시간 미정",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink.copy(alpha = 0.6f),
                )
            }
            val extras = buildList {
                if (event.photoUrls.isNotEmpty()) add("사진 ${event.photoUrls.size}")
                if (event.description.isNotBlank()) add("메모")
                if (event.repeat.isNotBlank()) add(repeatLabel(event.repeat))
            }
            if (extras.isNotEmpty()) {
                Text(extras.joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text("보기", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

/** 첨부 사진 썸네일 가로 스크롤 + 탭 시 전체보기 */
@Composable
internal fun PhotoStrip(urls: List<String>) {
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
internal fun DescriptionText(text: String) {
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
internal fun MemberTags(memberIds: List<String>) {
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
internal fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Ink.copy(alpha = 0.5f), modifier = Modifier.width(56.dp), fontSize = 14.sp)
        Text(value, color = Ink, fontSize = 14.sp)
    }
}

internal fun repeatLabel(key: String): String = when (key) {
    "weekly" -> "매주"
    "biweekly" -> "격주"
    "monthly" -> "매월"
    "yearly" -> "매년"
    else -> ""
}

/** 여러 날 일정의 "시작일시 ~ 종료일시" 문구. 하루종일/00:00 이면 시간 없이 날짜만. */
internal fun spanText(e: CalendarEvent): String {
    val s = runCatching { LocalDate.parse(e.startDateIso) }.getOrNull()
    val en = runCatching { LocalDate.parse(e.endDateIso) }.getOrNull()
    if (s == null || en == null) return dateRange(e)
    fun d(x: LocalDate) = "${x.monthValue}월 ${x.dayOfMonth}일"
    val noTime = e.allDay || (isZeroTime(e.startTime) && isZeroTime(e.endTime))
    return if (noTime) "${d(s)} ~ ${d(en)}"
    else "${d(s)} ${koTime(e.startTime)} ~ ${d(en)} ${koTime(e.endTime)}"
}

private fun isZeroTime(t: String): Boolean = t.isBlank() || t == "00:00"

private fun koTime(hhmm: String): String {
    val p = hhmm.split(":")
    if (p.size < 2) return ""
    val h = p[0].toIntOrNull() ?: return ""
    val ampm = if (h < 12) "오전" else "오후"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$ampm $h12:${p[1]}"
}

internal fun dateRange(e: CalendarEvent): String =
    if (e.endDateIso.isBlank() || e.endDateIso == e.startDateIso) e.startDateIso
    else "${e.startDateIso} ~ ${e.endDateIso}"

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

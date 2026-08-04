package com.familyboard.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.R
import com.familyboard.app.data.RecurrenceExpander
import com.familyboard.app.ui.AppViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// 손글씨/라운드 폰트 (번들)
private val Gaegu = FontFamily(
    Font(R.font.gaegu, FontWeight.Normal),
    Font(R.font.gaegu_bold, FontWeight.Bold),
)
private val NanumPen = FontFamily(Font(R.font.nanum_pen))
private val NanumGothic = FontFamily(
    Font(R.font.nanum_gothic, FontWeight.Normal),
    Font(R.font.nanum_gothic_bold, FontWeight.Bold),
)

private val Ink = Color(0xFF3A2C1D)
private val NoteColors = listOf(Color(0xFFFFF3A8), Color(0xFFC7EFD0), Color(0xFFFFCBD3), Color(0xFFBFE0FF))
private val PinColors = listOf(Color(0xFFD63B2F), Color(0xFF2F7FD6), Color(0xFFE8A13A), Color(0xFF37B24D))

// 준호 수능 관련 고정 일정
private val SUNEUNG = LocalDate.of(2026, 11, 19)
private val KrDow = listOf("월", "화", "수", "목", "금", "토", "일")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenCalendar: () -> Unit,
    onOpenDday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val notices by vm.noticeItems.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }

    val checkedNotices = remember(notices) { notices.filter { it.checked }.take(4) }

    // 일정 보드: 올해 안(오늘 한 달 전 ~ 12/31)에서 지난 2개 + 다가오는 4개
    val schedule = remember(events, today) {
        val yearEnd = LocalDate.of(today.year, 12, 31)
        val winStart = today.minusMonths(1)
        val occ = RecurrenceExpander.expand(events, winStart, yearEnd)
            .flatMap { (dateStr, day) ->
                day.filter { it.spanStart }.map { LocalDate.parse(dateStr) to it.event }
            }
            .sortedBy { it.first }
        val past = occ.filter { it.first.isBefore(today) }.takeLast(2)
        val upcoming = occ.filter { !it.first.isBefore(today) }.take(4)
        past to upcoming
    }
    val ddayValue = remember(today) { ChronoUnit.DAYS.between(today, SUNEUNG).toInt() }

    Box(modifier.fillMaxSize()) {
        // 코르크 보드 배경(고정)
        Image(
            painter = painterResource(R.drawable.cork_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Color(0x14000000))) // 살짝 어둡게

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            TitleSign()
            Spacer(Modifier.height(20.dp))

            SectionLabel("가족 공지사항")
            Spacer(Modifier.height(8.dp))
            if (checkedNotices.isEmpty()) {
                Text(
                    "관리 기능 > 가족 공지사항에서 체크하면\n여기 포스트잇으로 붙어요.",
                    fontFamily = NanumPen, fontSize = 18.sp, color = Color.White,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    checkedNotices.forEachIndexed { i, n -> PostIt(n.text, i) }
                }
            }
            Spacer(Modifier.height(26.dp))

            SectionLabel("일정 보드")
            Spacer(Modifier.height(8.dp))
            ScheduleBoard(past = schedule.first, upcoming = schedule.second, today = today, onClick = onOpenCalendar)
            Spacer(Modifier.height(26.dp))

            Chalkboard(dday = ddayValue, onClick = onOpenDday)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TitleSign() {
    val shape = RoundedCornerShape(22.dp)
    val faceH = 96.dp
    Box(Modifier.fillMaxWidth().height(faceH + 12.dp)) {
        // 옆면(두께) — 앞면보다 아래로 내려 어두운 나무색이 보이게
        Box(
            Modifier.fillMaxWidth().height(faceH).align(Alignment.TopCenter)
                .offset(y = 11.dp).clip(shape).background(Color(0xFF3A2410)),
        )
        // 앞면 패널
        Box(
            Modifier.fillMaxWidth().height(faceH).align(Alignment.TopCenter)
                .shadow(16.dp, shape).clip(shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.wood_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 베벨: 밝은 안쪽 테두리 + 어두운 외곽으로 입체감
            Box(Modifier.fillMaxSize().border(3.dp, Color(0x40FFE9C7), shape))
            Box(Modifier.fillMaxSize().border(1.dp, Color(0x55000000), shape))
            // 모서리 나사
            Screw(Modifier.align(Alignment.TopStart).padding(12.dp))
            Screw(Modifier.align(Alignment.TopEnd).padding(12.dp))
            Screw(Modifier.align(Alignment.BottomStart).padding(12.dp))
            Screw(Modifier.align(Alignment.BottomEnd).padding(12.dp))
            Text(
                "준준패밀리 보드",
                fontFamily = Gaegu, fontWeight = FontWeight.Bold, fontSize = 34.sp,
                color = Color(0xFFFFF6E8),
                style = TextStyle(shadow = Shadow(Color(0xB3000000), Offset(0f, 3f), 6f)),
            )
        }
    }
}

@Composable
private fun Screw(modifier: Modifier) {
    Box(
        modifier.size(13.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFF0DFBE), Color(0xFF6E5230)))),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(8.dp).height(2.dp).background(Color(0x66000000)))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontFamily = NanumGothic, fontWeight = FontWeight.Bold, fontSize = 18.sp,
        color = Color.White,
        modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun PostIt(text: String, index: Int) {
    val rot = listOf(-2.5f, 1.8f, -1.2f, 2.4f)[index % 4]
    Box(
        Modifier.width(156.dp).height(130.dp).rotate(rot)
            .shadow(6.dp, RoundedCornerShape(3.dp))
            .background(NoteColors[index % NoteColors.size], RoundedCornerShape(3.dp)),
    ) {
        Box(
            Modifier.align(Alignment.TopCenter).size(16.dp).clip(CircleShape)
                .background(PinColors[index % PinColors.size]),
        )
        Text(
            text,
            fontFamily = NanumPen, fontSize = 20.sp, color = Color(0xFF40331F),
            lineHeight = 22.sp,
            modifier = Modifier.padding(top = 18.dp, start = 14.dp, end = 14.dp, bottom = 12.dp),
            maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ScheduleBoard(
    past: List<Pair<LocalDate, com.familyboard.app.data.model.CalendarEvent>>,
    upcoming: List<Pair<LocalDate, com.familyboard.app.data.model.CalendarEvent>>,
    today: LocalDate,
    onClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)).background(Color(0xFFFDFAF3))
            .clickable { onClick() }.padding(16.dp),
    ) {
        // 집게
        Box(
            Modifier.align(Alignment.TopCenter).width(58.dp).height(16.dp)
                .background(Color(0xFF55555A), RoundedCornerShape(4.dp)),
        )
        Column(Modifier.padding(top = 12.dp)) {
            if (past.isEmpty() && upcoming.isEmpty()) {
                Text("표시할 일정이 없어요.", color = Ink.copy(alpha = 0.5f), fontSize = 14.sp)
            }
            past.forEach { (d, e) -> EventLine(d, e, pastStyle = true) }
            if (past.isNotEmpty() && upcoming.isNotEmpty()) Spacer(Modifier.height(2.dp))
            upcoming.forEach { (d, e) -> EventLine(d, e, pastStyle = false) }
        }
    }
}

@Composable
private fun EventLine(
    date: LocalDate,
    e: com.familyboard.app.data.model.CalendarEvent,
    pastStyle: Boolean,
) {
    // 여러 날 일정은 시작~종료로 표시
    val dur = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(e.startDateIso), LocalDate.parse(e.endDateIso.ifBlank { e.startDateIso }))
    }.getOrDefault(0L).coerceAtLeast(0L)
    val end = date.plusDays(dur)
    val multi = end != date
    val dateLabel = if (multi) "${date.monthValue}/${date.dayOfMonth}~${end.monthValue}/${end.dayOfMonth}"
    else "${date.monthValue}/${date.dayOfMonth}"
    val sub = when {
        multi -> ""
        e.allDay -> " · 하루 종일"
        e.startTime.isNotBlank() -> " · ${e.startTime}"
        else -> ""
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            dateLabel,
            fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (pastStyle) Color(0xFFA89A86) else Ink.copy(alpha = 0.7f),
            modifier = Modifier.widthIn(min = 46.dp),
        )
        Spacer(Modifier.size(8.dp))
        Icon(Icons.Default.Event, null, tint = if (pastStyle) Color(0xFFCDBFA8) else Color(0xFFE8794A), modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            e.title + sub,
            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (pastStyle) Color(0xFFA89A86) else Ink,
            textDecoration = if (pastStyle) TextDecoration.LineThrough else TextDecoration.None,
        )
    }
}

private val BoardGreen = Color(0xFF22382E)
private val Gold = Color(0xFFE7B24C)
private val Chalk = Color(0xFFF2EAD6)
private val ChalkSoft = Color(0xFFB9C7BA)

@Composable
private fun Chalkboard(dday: Int, onClick: () -> Unit) {
    val ddayLabel = when {
        dday == 0 -> "D-DAY"; dday > 0 -> "D-$dday"; else -> "D+${-dday}"
    }
    Box(
        Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2C4A3B), BoardGreen)))
            .clickable { onClick() }.padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("🎓 중요 카운트다운", fontFamily = Gaegu, color = ChalkSoft, fontSize = 15.sp)
                    Text("준호 수능", fontFamily = NanumGothic, fontWeight = FontWeight.Bold, color = Chalk, fontSize = 22.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(ddayLabel, fontFamily = Gaegu, fontWeight = FontWeight.Bold, color = Gold, fontSize = 46.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x66F2EAD6)))
            Spacer(Modifier.height(10.dp))
            Text("주요 일정", color = ChalkSoft, fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(8.dp))
            ExamRow("9월 모의평가", "9/2 (수)", false)
            ExamRow("응시원서 현장 접수", "8/24 ~ 9/4", false)
            ExamRow("수능 시험일", "11/19 (목)", true)
            ExamRow("성적 통지일", "12/11 (금)", false)
        }
    }
}

@Composable
private fun ExamRow(name: String, value: String, hi: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(if (hi) "★" else "✦", color = if (hi) Gold else ChalkSoft, fontSize = 13.sp)
        Spacer(Modifier.size(8.dp))
        Text(name, color = Chalk, fontSize = 14.sp, fontWeight = if (hi) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, color = if (hi) Color.White else Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

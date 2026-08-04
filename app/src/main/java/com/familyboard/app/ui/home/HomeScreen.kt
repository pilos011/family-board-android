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
import androidx.compose.ui.draw.drawBehind
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
import com.familyboard.app.data.Family
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

private val KrDow = listOf("월", "화", "수", "목", "금", "토", "일")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
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
    // 홈 카운트다운: D-Day 항목 중 '홈 게시' 체크된 것. 준호 수능은 특별 박스(주요 일정 포함).
    val ddayItems by vm.ddayItems.collectAsStateWithLifecycle()
    val pinned = remember(ddayItems, today) {
        ddayItems.filter { it.homePinned && it.dateIso.isNotBlank() }.mapNotNull { itm ->
            val base = runCatching { LocalDate.parse(itm.dateIso) }.getOrNull() ?: return@mapNotNull null
            val target = if (itm.yearly) nextAnniversary(base, today) else base
            Triple(itm, ChronoUnit.DAYS.between(today, target).toInt(), target)
        }
    }
    val special = pinned.firstOrNull { it.first.text == "준호 수능" }
    val others = pinned.filter { it.first.text != "준호 수능" }.sortedBy { it.second }

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
            ScheduleBoard(past = schedule.first, upcoming = schedule.second)
            Spacer(Modifier.height(26.dp))

            special?.let { (item, d, _) ->
                CountdownBox(title = item.text, dday = d, dateText = null, examList = true)
            }
            others.forEach { (item, d, t) ->
                Spacer(Modifier.height(12.dp))
                CountdownBox(title = item.text, dday = d, dateText = krDate(t), examList = false)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TitleSign() {
    // 사용자가 제작한 타이틀 이미지 사용
    val shape = RoundedCornerShape(18.dp)
    Image(
        painter = painterResource(R.drawable.jun_title),
        contentDescription = "준준가족 보드",
        modifier = Modifier.fillMaxWidth().shadow(6.dp, shape).clip(shape),
        contentScale = ContentScale.FillWidth,
    )
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

private val Paper = Color(0xFFFDFAF3)
private val PaperRule = Color(0xFFEADFC9)
private val DatePast = Color(0xFFA89A86)
private val DateUp = Color(0xFF6F5C46)

@Composable
private fun ScheduleBoard(
    past: List<Pair<LocalDate, com.familyboard.app.data.model.CalendarEvent>>,
    upcoming: List<Pair<LocalDate, com.familyboard.app.data.model.CalendarEvent>>,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxWidth()) {
        // 크림 종이 클립보드 (mockup 구성)
        Column(
            Modifier.fillMaxWidth().shadow(6.dp, shape).clip(shape).background(Paper)
                .drawBehind {
                    val gap = 30.dp.toPx(); var y = 42.dp.toPx()
                    while (y < size.height) {
                        drawLine(PaperRule, Offset(0f, y), Offset(size.width, y), 1f); y += gap
                    }
                }
                .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 14.dp),
        ) {
            if (past.isEmpty() && upcoming.isEmpty()) {
                Text("표시할 일정이 없어요.", color = DatePast, fontSize = 14.sp)
            }
            if (past.isNotEmpty()) {
                PaperDivider("지난 일정")
                past.forEach { (d, e) -> EventLine(d, e, pastStyle = true) }
            }
            if (upcoming.isNotEmpty()) {
                PaperDivider("다가오는 일정")
                upcoming.forEach { (d, e) -> EventLine(d, e, pastStyle = false) }
            }
        }
        // 집게(불독 클립)
        Box(
            Modifier.align(Alignment.TopCenter).width(60.dp).height(16.dp)
                .clip(RoundedCornerShape(4.dp)).background(Color(0xFF55555A)),
        )
    }
}

@Composable
private fun PaperDivider(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE4D8C1)))
        Text(label, color = Color(0xFFB6A488), fontSize = 11.sp, letterSpacing = 1.sp)
        Box(Modifier.weight(1f).height(1.dp).background(Color(0xFFE4D8C1)))
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
    val dotColor = if (pastStyle) Color(0xFFCDBFA8) else Family.colorOfIds(e.memberIds)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            dateLabel,
            fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (pastStyle) DatePast else DateUp,
            modifier = Modifier.widthIn(min = 46.dp),
        )
        Spacer(Modifier.size(10.dp))
        Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.size(9.dp))
        Text(
            e.title + sub,
            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (pastStyle) DatePast else Ink,
            textDecoration = if (pastStyle) TextDecoration.LineThrough else TextDecoration.None,
        )
    }
}

private val BoardGreen = Color(0xFF22382E)
private val Gold = Color(0xFFE7B24C)
private val Chalk = Color(0xFFF2EAD6)
private val ChalkSoft = Color(0xFFB9C7BA)

@Composable
private fun CountdownBox(title: String, dday: Int, dateText: String?, examList: Boolean) {
    val ddayLabel = when {
        dday == 0 -> "D-DAY"; dday > 0 -> "D-$dday"; else -> "D+${-dday}"
    }
    Box(
        Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2C4A3B), BoardGreen)))
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontFamily = NanumGothic, fontWeight = FontWeight.Bold, color = Chalk, fontSize = 24.sp)
                    if (!examList && dateText != null) {
                        Text(dateText, color = ChalkSoft, fontSize = 13.sp)
                    }
                }
                Text(ddayLabel, fontFamily = Gaegu, fontWeight = FontWeight.Bold, color = Gold, fontSize = 46.sp)
            }
            if (examList) {
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
}

private fun nextAnniversary(date: LocalDate, today: LocalDate): LocalDate {
    val md = java.time.MonthDay.of(date.monthValue, date.dayOfMonth)
    var next = md.atYear(today.year)
    if (next.isBefore(today)) next = md.atYear(today.year + 1)
    return next
}

private fun krDate(d: LocalDate): String = "${d.monthValue}월 ${d.dayOfMonth}일 (${KrDow[d.dayOfWeek.value - 1]})"

@Composable
private fun ExamRow(name: String, value: String, hi: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(if (hi) "★" else "✦", color = if (hi) Gold else ChalkSoft, fontSize = 13.sp)
        Spacer(Modifier.size(8.dp))
        Text(name, color = Chalk, fontSize = 14.sp, fontWeight = if (hi) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(value, color = if (hi) Color.White else Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

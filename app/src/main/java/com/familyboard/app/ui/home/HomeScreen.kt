package com.familyboard.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.familyboard.app.notif.UpdateChecker
import com.familyboard.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// 손글씨/라운드 폰트 (번들)
private val NanumPen = FontFamily(Font(R.font.nanum_pen))
private val NanumGothic = FontFamily(
    Font(R.font.nanum_gothic, FontWeight.Normal),
    Font(R.font.nanum_gothic_bold, FontWeight.Bold),
)

private val Ink = Color(0xFF3A2C1D)
private val NoteColors = listOf(Color(0xFFFFF3A8), Color(0xFFC7EFD0), Color(0xFFFFCBD3), Color(0xFFBFE0FF))
private val PinColors = listOf(Color(0xFFD63B2F), Color(0xFF2F7FD6), Color(0xFFE8A13A), Color(0xFF37B24D))

private val KrDow = listOf("월", "화", "수", "목", "금", "토", "일")
private val KrDowFull = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

// 날씨 기본 위치(고양시). 홈 알림판용 대략 위치.
private const val HOME_LAT = 37.6584
private const val HOME_LNG = 126.8320

/** 현재 위치(마지막 known). 권한/좌표 없으면 고양 기본값. 날씨용 대략 위치. */
@android.annotation.SuppressLint("MissingPermission")
private fun homeLocation(context: android.content.Context): Pair<Double, Double> {
    val fine = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val coarse = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (fine || coarse) {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        val loc = lm?.let {
            listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { p -> runCatching { it.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
        }
        if (loc != null) return loc.latitude to loc.longitude
    }
    return HOME_LAT to HOME_LNG
}

/** WMO weather code → 이모지 아이콘. */
private fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67 -> "🌧️"
    in 71..77 -> "🌨️"
    in 80..82 -> "🌦️"
    85, 86 -> "🌨️"
    in 95..99 -> "⛈️"
    else -> "🌡️"
}

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenEvent: (String, String) -> Unit,
    onOpenDday: () -> Unit,
    onOpenNotice: () -> Unit,
    canManageNotice: Boolean,
    modifier: Modifier = Modifier,
) {
    val notices by vm.noticeItems.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val nowTime = remember { java.time.LocalTime.now() } // 진입 시각(오늘 일정 지남 판정 기준)
    val updateInfo by vm.updateInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 오늘/내일 날씨: 현재 위치 기준(권한/좌표 없으면 고양 기본값), 진입 시 + 1시간마다 갱신
    var weather by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            val (la, lo) = homeLocation(context)
            weather = com.familyboard.app.notif.WeatherApi.today2(la, lo)
            kotlinx.coroutines.delay(3_600_000L)
        }
    }
    val scope = rememberCoroutineScope()
    var showUpdate by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var showMaker by remember { mutableStateOf(false) }
    // 타이틀 아래 '만든이' 라벨은 잠깐 보였다 사라짐
    LaunchedEffect(showMaker) { if (showMaker) { kotlinx.coroutines.delay(1800); showMaker = false } }
    // 앱 시작뿐 아니라 홈 화면으로 진입할 때마다 업데이트 재확인
    LaunchedEffect(Unit) { vm.refreshUpdate() }

    val checkedNotices = remember(notices) { notices.filter { it.checked }.take(4) }

    // 일정 보드: 롤링 윈도우(오늘 한 달 전 ~ 3개월 후)에서 지난 2개 + 다가오는 4개.
    // 고정 연말(12/31) 대신 롤링으로 두어 연말에도 내년 초 일정이 "다가오는 일정"에 보이게 함.
    val schedule = remember(events, today, nowTime) {
        val winEnd = today.plusMonths(3)
        val winStart = today.minusMonths(1)
        val occ = RecurrenceExpander.expand(events, winStart, winEnd)
            .flatMap { (dateStr, day) ->
                val d = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                if (d == null) emptyList() else day.filter { it.spanStart }.map { d to it.event }
            }
            .sortedBy { it.first }
        // 지난 일정 판정: 날짜가 오늘 이전이거나, "오늘 시작하는 단일(당일) 일정"인데 시작 시간이 현재 시각을 지난 경우.
        // (여러 날 일정은 오늘 시작했어도 진행 중으로 보고 제외)
        fun isPast(pair: Pair<LocalDate, com.familyboard.app.data.model.CalendarEvent>): Boolean {
            val (d, e) = pair
            if (d.isBefore(today)) return true
            if (d.isEqual(today) && !e.allDay && e.startTime.isNotBlank()) {
                val end = runCatching { LocalDate.parse(e.endDateIso.ifBlank { e.startDateIso }) }.getOrNull() ?: d
                val multiDay = end.isAfter(d)
                val st = runCatching { java.time.LocalTime.parse(e.startTime) }.getOrNull()
                if (!multiDay && st != null && st.isBefore(nowTime)) return true
            }
            return false
        }
        val past = occ.filter { isPast(it) }.takeLast(2)
        val upcoming = occ.filterNot { isPast(it) }.take(4)
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
            TitleSign(
                updateAvailable = updateInfo != null,
                onUpdate = { showUpdate = true },
                onTitleClick = { showMaker = true },
            )
            // 타이틀 이미지 바로 아래에 만든이 표시(탭 시 잠깐)
            if (showMaker) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.background(Color(0x99000000), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("버전 ${com.familyboard.app.BuildConfig.VERSION_NAME}",
                            fontFamily = NanumGothic, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                        Spacer(Modifier.height(6.dp))
                        Text("만든이 : 김선일", fontFamily = NanumGothic, fontSize = 13.sp, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            SectionLabel("가족 공지사항")
            Spacer(Modifier.height(8.dp))
            if (checkedNotices.isEmpty()) {
                Text(
                    "관리 기능 > 가족 공지사항에서 체크하면\n여기 포스트잇으로 붙어요.",
                    fontFamily = NanumPen, fontSize = 18.sp, color = Color.White,
                )
            } else {
                // 화면 폭과 무관하게 항상 한 줄에 2개씩(각 절반 폭). 플립3 등 좁은 폭에서 세로로 접히지 않게 함.
                // 부모(선일·은선)만 롱클릭으로 공지 화면 이동. 자녀는 반응 없음.
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    checkedNotices.chunked(2).forEachIndexed { rowIdx, rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEachIndexed { colIdx, n ->
                                PostIt(
                                    n.text, rowIdx * 2 + colIdx,
                                    modifier = Modifier.weight(1f),
                                    onLongPress = if (canManageNotice) onOpenNotice else null,
                                )
                            }
                            // 마지막 줄에 1개만 있으면 왼쪽 절반 폭을 유지하도록 빈칸 채움
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))

            SectionLabel("일정 보드")
            Spacer(Modifier.height(8.dp))
            ScheduleBoard(past = schedule.first, upcoming = schedule.second, today = today, weather = weather, onOpenEvent = onOpenEvent)
            Spacer(Modifier.height(26.dp))

            special?.let { (item, d, _) ->
                CountdownBox(title = item.text, dday = d, dateText = null, examList = true, onLongPress = onOpenDday)
            }
            others.forEach { (item, d, t) ->
                Spacer(Modifier.height(12.dp))
                CountdownBox(title = item.text, dday = d, dateText = krDate(t), examList = false, onLongPress = onOpenDday)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showUpdate) {
        val info = updateInfo
        AlertDialog(
            onDismissRequest = { if (!downloading) showUpdate = false },
            title = { Text("새 버전 ${info?.versionName ?: ""} 있어요") },
            text = {
                Column {
                    Text("업데이트가 있습니다. 지금 설치할까요?")
                    if (!info?.notes.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(info!!.notes, color = Ink.copy(alpha = 0.6f))
                    }
                    if (downloading) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text("다운로드 중…")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !downloading && info != null,
                    onClick = {
                        val url = info?.url ?: return@TextButton
                        downloading = true
                        scope.launch {
                            val f = UpdateChecker.downloadApk(context, url, info.sha256)
                            downloading = false
                            if (f != null) { showUpdate = false; UpdateChecker.installApk(context, f) }
                        }
                    },
                ) { Text("지금 설치") }
            },
            dismissButton = { TextButton(enabled = !downloading, onClick = { showUpdate = false }) { Text("나중에") } },
        )
    }
}

@Composable
private fun TitleSign(updateAvailable: Boolean, onUpdate: () -> Unit, onTitleClick: () -> Unit) {
    // 사용자 제작 타이틀 이미지 + 약간의 두께감 + 과하지 않은 라운딩. 비율 유지 75% 크기, 가운데.
    // 타이틀 탭 → 만든이 표시(라벨은 상위에서 타이틀 바로 아래에 렌더). 오른쪽 빈 공간 가운데에 업데이트 아이콘(있을 때).
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth(0.75f)) {
            Box(Modifier.matchParentSize().offset(y = 4.dp).clip(shape).background(Color(0xFF4A3018)))
            Image(
                painter = painterResource(R.drawable.jun_title),
                contentDescription = "준준가족 보드",
                modifier = Modifier.fillMaxWidth().shadow(6.dp, shape).clip(shape)
                    .clickable { onTitleClick() },
                contentScale = ContentScale.FillWidth,
            )
        }
        if (updateAvailable) {
            Box(
                Modifier.align(Alignment.CenterEnd).offset(x = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                UpdateBell(onClick = onUpdate)
            }
        }
    }
}

/** 업데이트 있을 때 뜨는 원형 배지(위로 화살표). 계속 통통 튀며 맥동해 눈에 잘 띈다. */
@Composable
private fun UpdateBell(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "update")
    val bounce by t.animateFloat(
        initialValue = 0f, targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(560, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce",
    )
    val pulse by t.animateFloat(
        initialValue = 0.92f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(560, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .size(38.dp)
            .offset(y = bounce.dp)
            .scale(pulse)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFFF6B6B), Color(0xFFE03131))))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Upgrade, "업데이트 있음",
            tint = Color.White, modifier = Modifier.size(24.dp),
        )
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
private fun PostIt(text: String, index: Int, modifier: Modifier = Modifier, onLongPress: (() -> Unit)? = null) {
    val rot = listOf(-2.5f, 1.8f, -1.2f, 2.4f)[index % 4]
    val haptic = LocalHapticFeedback.current
    Box(
        modifier.height(130.dp).rotate(rot)
            .shadow(6.dp, RoundedCornerShape(3.dp))
            .background(NoteColors[index % NoteColors.size], RoundedCornerShape(3.dp))
            .then(
                if (onLongPress != null) Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    })
                } else Modifier,
            ),
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
    today: LocalDate,
    weather: Pair<Int, Int>?,
    onOpenEvent: (String, String) -> Unit,
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
            // 상단: 왼쪽=오늘 날짜/요일, 오른쪽=오늘·내일 날씨
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "오늘은 ${today.monthValue}월 ${today.dayOfMonth}일 ${KrDowFull[today.dayOfWeek.value - 1]}",
                    fontFamily = NanumGothic, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DateUp,
                    modifier = Modifier.weight(1f),
                )
                weather?.let { (t, tm) ->
                    Text("오늘 ${weatherEmoji(t)}  내일 ${weatherEmoji(tm)}", fontSize = 13.sp, color = DateUp)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (past.isEmpty() && upcoming.isEmpty()) {
                Text("표시할 일정이 없어요.", color = DatePast, fontSize = 14.sp)
            }
            if (past.isNotEmpty()) {
                PaperDivider("지난 일정")
                past.forEach { (d, e) -> EventLine(d, e, pastStyle = true, onOpenEvent = onOpenEvent) }
            }
            if (upcoming.isNotEmpty()) {
                PaperDivider("다가오는 일정")
                upcoming.forEach { (d, e) -> EventLine(d, e, pastStyle = false, onOpenEvent = onOpenEvent) }
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
    onOpenEvent: (String, String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    // 여러 날 일정은 시작~종료로 표시
    val dur = runCatching {
        ChronoUnit.DAYS.between(LocalDate.parse(e.startDateIso), LocalDate.parse(e.endDateIso.ifBlank { e.startDateIso }))
    }.getOrDefault(0L).coerceAtLeast(0L)
    val end = date.plusDays(dur)
    val multi = end != date
    val dowS = KrDow[date.dayOfWeek.value - 1]
    val dowE = KrDow[end.dayOfWeek.value - 1]
    // 당일 시간있는 일정은 요일 오른쪽 같은 줄에("8/7(금) 14:00"). 종일은 표시 안 함. 여러 날은 "8/11~8/14(금)".
    val timeSuffix = if (!multi && !e.allDay && e.startTime.isNotBlank()) " ${e.startTime}" else ""
    val dateLabel = if (multi) "${date.monthValue}/${date.dayOfMonth}~${end.monthValue}/${end.dayOfMonth}(${dowE})"
    else "${date.monthValue}/${date.dayOfMonth}(${dowS})$timeSuffix"
    val dotColor = if (pastStyle) Color(0xFFCDBFA8) else Family.colorOfIds(e.memberIds)
    Row(
        Modifier.fillMaxWidth()
            .pointerInput(e.id, date) {
                detectTapGestures(onLongPress = {
                    if (e.id.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenEvent(e.id, date.toString())
                    }
                })
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 날짜(+당일 시간)를 한 줄로. 여러 날 일정 폭에 딱 맞춰 고정 → 점 정렬은 유지하되 빈 공간 최소화.
        Text(
            dateLabel,
            fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (pastStyle) DatePast else DateUp,
            modifier = Modifier.widthIn(min = 88.dp),
        )
        Spacer(Modifier.size(6.dp))
        Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.size(9.dp))
        Text(
            e.title,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (pastStyle) DatePast else Ink,
            textDecoration = if (pastStyle) TextDecoration.LineThrough else TextDecoration.None,
        )
        // 오른쪽 정렬: 대상 인원 이름(여러 명 "선일·은선·준호", 전체 "모두")
        Spacer(Modifier.size(8.dp))
        Text(
            Family.targetNames(e.memberIds),
            fontSize = 12.sp, maxLines = 1,
            color = if (pastStyle) DatePast else DateUp.copy(alpha = 0.75f),
        )
    }
}

private val BoardGreen = Color(0xFF22382E)
private val Gold = Color(0xFFE7B24C)
private val Chalk = Color(0xFFF2EAD6)
private val ChalkSoft = Color(0xFFB9C7BA)

@Composable
private fun CountdownBox(title: String, dday: Int, dateText: String?, examList: Boolean, onLongPress: () -> Unit) {
    val ddayLabel = when {
        dday == 0 -> "D-DAY"; dday > 0 -> "D-$dday"; else -> "D+${-dday}"
    }
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2C4A3B), BoardGreen)))
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress()
                })
            }
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
                Text(ddayLabel, fontFamily = NanumGothic, fontWeight = FontWeight.Bold, color = Gold, fontSize = 46.sp)
            }
            if (examList) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x66F2EAD6)))
                Spacer(Modifier.height(10.dp))
                Text("주요 일정", color = ChalkSoft, fontSize = 12.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                // NOTE: 준호 수능(제목이 정확히 "준호 수능"인 D-Day) 전용 맞춤 박스.
                // 아래 시험 일정은 해당 연도 상수라 매년(수능일 발표 후) 수동 갱신 필요.
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

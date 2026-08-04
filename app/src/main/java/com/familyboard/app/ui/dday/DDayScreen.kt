package com.familyboard.app.ui.dday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.Family
import com.familyboard.app.data.FamilyBirthdays
import com.familyboard.app.data.model.DDayBoard
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.ui.AppViewModel
import com.familyboard.app.ui.bucket.BucketIcons
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private val Ink = Color(0xFF2B2B2E)
private val KrDow = listOf("월", "화", "수", "목", "금", "토", "일")

/** 화면에 그릴 카운트다운 한 줄 */
private data class DRow(
    val id: String?,        // 사용자 항목이면 id, 생일이면 null
    val title: String,
    val target: LocalDate,  // 다음 도래일
    val dday: Int,
    val sub: String,
    val isBirthday: Boolean,
    val icon: String = "",  // 사용자 항목 꾸미기 아이콘 키
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DDayScreen(
    vm: AppViewModel,
    currentMemberId: String?,
    onBack: () -> Unit,
) {
    val me = currentMemberId ?: Family.ALL_ID
    val items by vm.ddayItems.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    var editItem by remember { mutableStateOf<ListItem?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val userRows = remember(items, today) {
        items.mapNotNull { it.toUserRow(today) }
            // 다가오는 것부터, 지난 1회성 항목은 뒤로
            .sortedWith(compareBy({ it.dday < 0 }, { if (it.dday >= 0) it.dday else -it.dday }))
    }
    val birthdayRows = remember(today) {
        FamilyBirthdays.list.map { (id, birth) -> birthdayRow(id, birth, today) }
            .sortedBy { it.dday } // 다가오는 생일 순
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("D-Day") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Color(0xFF5C7CFA)) {
                Icon(Icons.Default.Add, "D-Day 추가", tint = Color.White)
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            if (userRows.isNotEmpty()) {
                items(userRows, key = { it.id!! }) { r ->
                    DDayCard(row = r, onClick = { items.firstOrNull { it.id == r.id }?.let { editItem = it } })
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cake, null, tint = Ink.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("가족 생일", fontWeight = FontWeight.Bold, color = Ink.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(2.dp))
            }
            items(birthdayRows) { r -> DDayCard(row = r, onClick = null) }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAdd) {
        DDayEditDialog(
            initial = null,
            onSave = { title, date, yearly, homePinned, icon, notifyIds ->
                vm.addItem(
                    ListItem(text = title, board = DDayBoard.BOARD, createdBy = me,
                        dateIso = date.toString(), yearly = yearly, icon = icon,
                        notifyIds = notifyIds, homePinned = homePinned)
                )
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    editItem?.let { item ->
        DDayEditDialog(
            initial = item,
            onSave = { title, date, yearly, homePinned, icon, notifyIds ->
                vm.updateItem(item.copy(text = title, dateIso = date.toString(), yearly = yearly,
                    icon = icon, notifyIds = notifyIds, homePinned = homePinned))
                editItem = null
            },
            onDelete = { vm.deleteItem(item.id); editItem = null },
            onDismiss = { editItem = null },
        )
    }
}

@Composable
private fun DDayCard(row: DRow, onClick: (() -> Unit)?) {
    val col = ddayColor(row.dday)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.isBirthday) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Family.colorOf(birthdayMemberId(row))),
                contentAlignment = Alignment.Center,
            ) { Text(Family.initialOf(birthdayMemberId(row)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Spacer(Modifier.size(12.dp))
        } else {
            BucketIcons.of(row.icon)?.let { iv ->
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color(0x1A5C7CFA)),
                    contentAlignment = Alignment.Center,
                ) { Icon(iv, null, tint = Color(0xFF5C7CFA), modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.size(12.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (row.isBirthday) "${row.title} 생일" else row.title,
                fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(row.sub, color = Ink.copy(alpha = 0.5f), fontSize = 12.5.sp)
        }
        Text(
            ddayLabel(row.dday),
            color = col, fontWeight = FontWeight.Bold, fontSize = 20.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DDayEditDialog(
    initial: ListItem?,
    onSave: (String, LocalDate, Boolean, Boolean, String, List<String>) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initial?.text ?: "") }
    var date by remember {
        mutableStateOf(
            runCatching { LocalDate.parse(initial?.dateIso ?: "") }.getOrDefault(LocalDate.now())
        )
    }
    var yearly by remember { mutableStateOf(initial?.yearly ?: false) }
    var homePinned by remember { mutableStateOf(initial?.homePinned ?: false) }
    var notifyIds by remember { mutableStateOf(initial?.notifyIds ?: emptyList()) }
    var showNotify by remember { mutableStateOf(false) }
    var icon by remember { mutableStateOf(initial?.icon ?: "") }
    var showIcons by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "D-Day 수정" else "D-Day 추가") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("제목 (예: 크루즈 탑승)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF1F3F5)).clickable { showDate = true }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("날짜", color = Ink.copy(alpha = 0.6f))
                    Spacer(Modifier.weight(1f))
                    Text(krFullDate(date), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Home, null, tint = Ink.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("홈 화면에 게시", Modifier.weight(1f))
                    Switch(checked = homePinned, onCheckedChange = { homePinned = it })
                }

                // 알림: 기본 접힘, 대상 미선택(=알림 없음). 선택 시 일주일 전·1일 전 알림.
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { showNotify = !showNotify }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("알림 설정", Modifier.weight(1f), color = Ink.copy(alpha = 0.8f))
                    Text(
                        if (notifyIds.isEmpty()) "없음" else "${notifyIds.size}명",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        if (showNotify) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = Ink.copy(alpha = 0.6f),
                    )
                }
                if (showNotify) {
                    Text("선택한 가족에게 일주일 전·1일 전 알림", color = Ink.copy(alpha = 0.5f), fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    DDayNotifyPicker(selected = notifyIds, onSelect = { notifyIds = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Repeat, null, tint = Ink.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("매년 반복", Modifier.weight(1f))
                    Switch(checked = yearly, onCheckedChange = { yearly = it })
                }

                // 아이콘: 기본은 접힌 상태, 탭하면 펼쳐서 선택
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { showIcons = !showIcons }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("아이콘", Modifier.weight(1f), color = Ink.copy(alpha = 0.8f))
                    BucketIcons.of(icon)?.let { iv ->
                        Icon(iv, null, tint = Color(0xFF5C7CFA), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(
                        if (showIcons) "접기" else if (icon.isBlank()) "추가" else "변경",
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        if (showIcons) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, tint = Ink.copy(alpha = 0.6f),
                    )
                }
                if (showIcons) {
                    DDayIconPicker(selected = icon, onSelect = { icon = if (icon == it) "" else it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), date, yearly, homePinned, icon, notifyIds) },
            ) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.size(4.dp)); Text("삭제")
                    }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDate = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DDayNotifyPicker(selected: List<String>, onSelect: (List<String>) -> Unit) {
    val allIds = remember { Family.members.map { it.id } }
    val allOn = selected.isNotEmpty() && allIds.all { selected.contains(it) }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NotifyChip("모두", allOn) { onSelect(if (allOn) emptyList() else allIds) }
        Family.members.forEach { m ->
            NotifyChip(m.name, selected.contains(m.id)) {
                val cur = selected.toMutableList()
                if (cur.contains(m.id)) cur.remove(m.id) else cur.add(m.id)
                onSelect(cur)
            }
        }
    }
}

@Composable
private fun NotifyChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) Color(0xFF5C7CFA) else Color(0xFFF1F3F5))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        color = if (on) Color.White else Color(0xFF555555),
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        fontSize = 13.sp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DDayIconPicker(selected: String, onSelect: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BucketIcons.all.forEach { (key, iv) ->
                val on = key == selected
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) Color(0xFF5C7CFA) else Color(0xFFF1F3F5))
                        .border(
                            width = if (on) 0.dp else 1.dp,
                            color = Color(0xFFE0E0E0), shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelect(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(iv, key, tint = if (on) Color.White else Ink.copy(alpha = 0.7f),
                        modifier = Modifier.size(21.dp))
                }
            }
        }
    }
}

// ─────────────────────────── 계산 helpers ───────────────────────────

private fun ListItem.toUserRow(today: LocalDate): DRow? {
    val d = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return null
    val target = if (yearly) nextAnniversary(d, today) else d
    val dday = ChronoUnit.DAYS.between(today, target).toInt()
    val sub = buildString {
        append(krFullDate(target))
        if (yearly) append(" · 매년")
    }
    return DRow(id = id, title = text.ifBlank { "(제목 없음)" }, target = target, dday = dday, sub = sub, isBirthday = false, icon = icon)
}

private fun birthdayRow(memberId: String, birth: LocalDate, today: LocalDate): DRow {
    val next = nextAnniversary(birth, today)
    val dday = ChronoUnit.DAYS.between(today, next).toInt()
    val currentAge = java.time.Period.between(birth, today).years // 오늘 기준 현재 만 나이
    val sub = "${next.monthValue}월 ${next.dayOfMonth}일 (${KrDow[next.dayOfWeek.value - 1]}) · 만 ${currentAge}세"
    return DRow(id = null, title = Family.nameOf(memberId), target = next, dday = dday, sub = sub, isBirthday = true)
}

/** (월,일) 기준 오늘 이후(당일 포함)의 다음 도래일. 2/29 는 평년엔 2/28 로. */
private fun nextAnniversary(date: LocalDate, today: LocalDate): LocalDate {
    val md = MonthDay.of(date.monthValue, date.dayOfMonth)
    var next = md.atYear(today.year)
    if (next.isBefore(today)) next = md.atYear(today.year + 1)
    return next
}

private fun ddayLabel(d: Int): String = when {
    d == 0 -> "D-DAY"
    d > 0 -> "D-$d"
    else -> "D+${-d}"
}

private fun ddayColor(d: Int): Color = when {
    d < 0 -> Color(0xFFADB5BD)
    d == 0 -> Color(0xFFE8590C)
    d <= 7 -> Color(0xFFE03131)
    d <= 30 -> Color(0xFFE8790C)
    else -> Color(0xFF5C7CFA)
}

private fun krFullDate(d: LocalDate): String =
    "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일 (${KrDow[d.dayOfWeek.value - 1]})"

/** 생일 행에서 멤버 색을 얻기 위한 역-매핑 */
private fun birthdayMemberId(row: DRow): String =
    Family.members.firstOrNull { it.name == row.title }?.id ?: Family.ALL_ID

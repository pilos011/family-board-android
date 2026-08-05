package com.familyboard.app.ui.calendar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.CalendarEvent
import com.familyboard.app.data.model.Reminders
import com.familyboard.app.notif.PhotoUploader
import com.familyboard.app.ui.AppViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private val WeekShort = listOf("일", "월", "화", "수", "목", "금", "토")
private val Ink = Color(0xFF2B2B2E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    vm: AppViewModel,
    startIso: String,
    endIso: String,
    defaultMemberId: String?,
    onBack: () -> Unit,
    editEventId: String? = null,
) {
    val eventsState by vm.events.collectAsStateWithLifecycle()
    val editing = remember(eventsState, editEventId) {
        editEventId?.let { id -> eventsState.firstOrNull { it.id == id } }
    }
    // 수정 진입인데 대상이 아직 로딩 전이거나 삭제됨 → 빈 폼(새 일정 생성) 오작동 방지
    if (editEventId != null && editing == null) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text("일정 수정") },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                if (eventsState.isEmpty()) CircularProgressIndicator()
                else Text("삭제된 일정입니다.", color = Ink.copy(alpha = 0.5f))
            }
        }
        return
    }
    val startD = remember(startIso) { runCatching { LocalDate.parse(startIso) }.getOrDefault(LocalDate.now()) }
    val endD = remember(endIso) { runCatching { LocalDate.parse(endIso) }.getOrDefault(startD) }
    val isRange = endD.isAfter(startD)

    fun parseT(s: String, def: LocalTime) = runCatching { LocalTime.parse(s) }.getOrDefault(def)
    fun initStart() = editing?.let {
        runCatching { LocalDateTime.of(LocalDate.parse(it.startDateIso), parseT(it.startTime, LocalTime.of(18, 0))) }.getOrNull()
    } ?: LocalDateTime.of(startD, LocalTime.of(18, 0))
    fun initEnd() = editing?.let {
        runCatching {
            LocalDateTime.of(LocalDate.parse(it.endDateIso.ifBlank { it.startDateIso }), parseT(it.endTime, LocalTime.of(19, 0)))
        }.getOrNull()
    } ?: LocalDateTime.of(endD, LocalTime.of(19, 0))

    var title by remember { mutableStateOf(editing?.title ?: "") }
    var description by remember { mutableStateOf(editing?.description ?: "") }
    var allDay by remember { mutableStateOf(editing?.allDay ?: isRange) } // 여러 날 드래그면 기본 하루종일
    // 시작/종료를 LocalDateTime 으로 관리 → 시작 변경 시 종료가 기간을 유지하며 따라감
    var start by remember { mutableStateOf(initStart()) }
    var end by remember { mutableStateOf(initEnd()) }
    var memberIds by remember { mutableStateOf(editing?.memberIds ?: listOf(defaultMemberId ?: Family.ALL_ID)) }
    var repeat by remember { mutableStateOf(editing?.repeat ?: "") }
    var lunar by remember { mutableStateOf(editing?.lunar ?: false) }
    var reminder by remember { mutableStateOf(editing?.reminder ?: "none") }
    // 등록 시 알림 보낼 가족(선택). 기본 없음 → 아무에게도 안 보냄.
    var notifyIds by remember { mutableStateOf(emptyList<String>()) }
    var pick by remember { mutableStateOf<Pick?>(null) }

    var photoUrls by remember { mutableStateOf(editing?.photoUrls ?: emptyList<String>()) }
    var uploading by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun uploadFrom(uri: Uri) {
        if (photoUrls.size >= 5) return
        uploading = true
        scope.launch {
            val url = PhotoUploader.compressAndUpload(context, uri)
            if (url != null) photoUrls = photoUrls + url
            uploading = false
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) uploadFrom(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) uploadFrom(u)
    }
    fun launchCamera() {
        val file = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraLauncher.launch(cameraUri!!)
    }

    // 시작을 바꾸면 종료도 같은 간격만큼 이동(기간 유지). 종료가 시작보다 앞서지 않게.
    fun updateStart(newStart: LocalDateTime) {
        val delta = Duration.between(start, newStart)
        end = end.plus(delta)
        start = newStart
        if (!end.isAfter(start)) end = start.plusHours(1)
    }

    fun updateEnd(newEnd: LocalDateTime) {
        end = if (newEnd.isAfter(start)) newEnd else start.plusHours(1)
    }

    fun save() {
        if (title.isBlank()) return
        val ev = CalendarEvent(
            id = editing?.id ?: "",
            title = title.trim(),
            startDateIso = start.toLocalDate().toString(),
            endDateIso = end.toLocalDate().toString(),
            allDay = allDay,
            startTime = if (allDay) "" else fmt(start.toLocalTime()),
            endTime = if (allDay) "" else fmt(end.toLocalTime()),
            memberIds = memberIds,
            repeat = repeat,
            lunar = lunar,
            reminder = reminder,
            createdBy = editing?.createdBy ?: (defaultMemberId ?: ""),
            description = description.trim(),
            photoUrls = photoUrls,
            exdates = editing?.exdates ?: emptyList(),
        )
        if (editing != null) vm.updateEvent(ev) else vm.addEvent(ev, notifyIds)
        onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (editing != null) "일정 수정" else "일정 추가") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = { IconButton(onClick = { save() }) { Icon(Icons.Default.Check, "저장") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("제목") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 300) description = it },
                label = { Text("상세 내용 (선택)") },
                placeholder = { Text("메모, 링크(URL) 등") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
                supportingText = { Text("${description.length}/300") },
            )
            Spacer(Modifier.height(8.dp))

            PhotoSection(
                photoUrls = photoUrls,
                uploading = uploading,
                onPickGallery = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickCamera = { launchCamera() },
                onRemove = { url -> photoUrls = photoUrls - url },
            )
            Spacer(Modifier.height(8.dp))

            SwitchRow("하루 종일", allDay) { allDay = it }
            Divider()

            DateTimeRow(
                icon = Icons.Default.CalendarMonth,
                date = start.toLocalDate(), time = start.toLocalTime(), allDay = allDay,
                onDate = { pick = Pick.StartDate }, onTime = { pick = Pick.StartTime },
            )
            Divider()
            DateTimeRow(
                icon = Icons.Default.CalendarMonth,
                date = end.toLocalDate(), time = end.toLocalTime(), allDay = allDay,
                onDate = { pick = Pick.EndDate }, onTime = { pick = Pick.EndTime },
            )
            Divider()
            Spacer(Modifier.height(12.dp))

            Text("누구의 일정?", style = MaterialTheme.typography.titleMedium)
            Text("여러 명 선택 가능", style = MaterialTheme.typography.bodyLarge, color = Ink.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))
            MemberPicker(selected = memberIds, onSelect = { memberIds = it })

            if (editing == null) {
                Spacer(Modifier.height(16.dp))
                Text("알림 보낼 가족 (선택)", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (notifyIds.isEmpty()) "선택하지 않으면 알림을 보내지 않아요"
                    else "${notifyIds.size}명에게 등록 알림을 보냅니다",
                    style = MaterialTheme.typography.bodyLarge, color = Ink.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(8.dp))
                NotifyPicker(selected = notifyIds, onSelect = { notifyIds = it })
            }

            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            ReminderRow(reminder = reminder, onSelect = { reminder = it })
            Divider()
            Spacer(Modifier.height(12.dp))

            Text("기타", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Repeat, null, tint = Ink.copy(alpha = 0.6f))
                Spacer(Modifier.size(10.dp))
                Text("반복 일정", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            RepeatPicker(selected = repeat, onSelect = { repeat = it })
            Spacer(Modifier.height(8.dp))
            SwitchRow("음력", lunar) { lunar = it }
            Spacer(Modifier.height(16.dp))
        }
    }

    when (pick) {
        Pick.StartDate -> DatePickerFor(
            start.toLocalDate(),
            onConfirm = { updateStart(LocalDateTime.of(it, start.toLocalTime())); pick = null },
            onCancel = { pick = null },
        )
        Pick.EndDate -> DatePickerFor(
            end.toLocalDate(),
            onConfirm = { updateEnd(LocalDateTime.of(it, end.toLocalTime())); pick = null },
            onCancel = { pick = null },
        )
        Pick.StartTime -> TimePickerFor(start.toLocalTime()) {
            updateStart(LocalDateTime.of(start.toLocalDate(), it)); pick = null
        }
        Pick.EndTime -> TimePickerFor(end.toLocalTime()) {
            updateEnd(LocalDateTime.of(end.toLocalDate(), it)); pick = null
        }
        null -> {}
    }
}

private enum class Pick { StartDate, EndDate, StartTime, EndTime }

@Composable
private fun DateTimeRow(
    icon: ImageVector,
    date: LocalDate,
    time: LocalTime,
    allDay: Boolean,
    onDate: () -> Unit,
    onTime: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Ink.copy(alpha = 0.6f))
        Spacer(Modifier.size(12.dp))
        Text(
            dateLabel(date),
            modifier = Modifier.weight(1f).clickable { onDate() },
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
        if (!allDay) {
            Text(
                koreanTime(time),
                modifier = Modifier.clickable { onTime() }.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerFor(initial: LocalDate, onConfirm: (LocalDate) -> Unit, onCancel: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = {
                val d = state.selectedDateMillis
                    ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: initial
                onConfirm(d)
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("취소") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerFor(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    var open by remember { mutableStateOf(true) }
    if (!open) return
    AlertDialog(
        onDismissRequest = { open = false; onPicked(initial) },
        confirmButton = {
            TextButton(onClick = { open = false; onPicked(LocalTime.of(state.hour, state.minute)) }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = { open = false; onPicked(initial) }) { Text("취소") } },
        text = { Box(Modifier.fillMaxWidth(), Alignment.Center) { TimePicker(state = state) } },
    )
}

@Composable
private fun ReminderRow(reminder: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Notifications, null, tint = Ink.copy(alpha = 0.6f))
            Spacer(Modifier.size(10.dp))
            Text("알림", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text(Reminders.label(reminder), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Reminders.options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        if (key == Reminders.CUSTOM) showCustom = true else onSelect(key)
                    },
                    trailingIcon = {
                        val sel = key == reminder || (key == Reminders.CUSTOM && Reminders.isCustom(reminder))
                        if (sel) Icon(Icons.Default.Check, null)
                    },
                )
            }
        }
    }
    if (showCustom) {
        val initial = remember(reminder) {
            if (reminder.startsWith("custom:"))
                runCatching { LocalDate.parse(reminder.removePrefix("custom:")) }.getOrDefault(LocalDate.now())
            else LocalDate.now()
        }
        DatePickerFor(
            initial,
            onConfirm = { onSelect("custom:$it"); showCustom = false },
            onCancel = { showCustom = false },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RepeatPicker(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("weekly" to "매주", "biweekly" to "격주", "monthly" to "매월", "yearly" to "매년")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (key, label) ->
            val on = selected == key
            Row(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color(0xFFF1F3F5))
                    .clickable { onSelect(if (on) "" else key) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else Color(0xFFDDE1E6)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.size(6.dp))
                Text(label, color = if (on) MaterialTheme.colorScheme.primary else Color(0xFF444444))
            }
        }
    }
}

@Composable
private fun MemberPicker(selected: List<String>, onSelect: (List<String>) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val allOn = selected.isEmpty() || selected.contains(Family.ALL_ID)
        MemberDot("모두", Family.allColor, allOn) { onSelect(listOf(Family.ALL_ID)) }
        Family.members.forEach { m ->
            MemberDot(m.name, m.color, selected.contains(m.id)) {
                val cur = selected.filter { it != Family.ALL_ID }.toMutableList()
                if (cur.contains(m.id)) cur.remove(m.id) else cur.add(m.id)
                onSelect(if (cur.isEmpty()) listOf(Family.ALL_ID) else cur)
            }
        }
    }
}

@Composable
private fun NotifyPicker(selected: List<String>, onSelect: (List<String>) -> Unit) {
    val allIds = remember { Family.members.map { it.id } }
    val allOn = selected.isNotEmpty() && allIds.all { selected.contains(it) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    Row(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) MaterialTheme.colorScheme.primary else Color(0xFFF1F3F5))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Notifications, null,
            tint = if (on) Color.White else Color(0xFFAAAAAA), modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(label, color = if (on) Color.White else Color(0xFF555555),
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun MemberDot(label: String, color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(if (isSelected) 52.dp else 44.dp).clip(CircleShape).background(color).clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) { if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PhotoSection(
    photoUrls: List<String>,
    uploading: Boolean,
    onPickGallery: () -> Unit,
    onPickCamera: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Text("사진 (${photoUrls.size}/5)", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        photoUrls.forEach { url ->
            Box(Modifier.size(76.dp)) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape)
                        .background(Color(0x99000000)).clickable { onRemove(url) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Close, "삭제", tint = Color.White, modifier = Modifier.size(15.dp)) }
            }
        }
        if (uploading) {
            Box(Modifier.size(76.dp), Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        }
        if (photoUrls.size < 5) {
            AddPhotoButton(Icons.Default.PhotoLibrary, "갤러리", onPickGallery)
            AddPhotoButton(Icons.Default.PhotoCamera, "카메라", onPickCamera)
        }
    }
}

@Composable
private fun AddPhotoButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F3F5)).clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = Ink.copy(alpha = 0.6f))
    }
}

private fun fmt(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

private fun dateLabel(d: LocalDate): String =
    "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일 (${WeekShort[d.dayOfWeek.value % 7]})"

private fun koreanTime(t: LocalTime): String {
    val ampm = if (t.hour < 12) "오전" else "오후"
    val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
    return "$ampm %d:%02d".format(h12, t.minute)
}

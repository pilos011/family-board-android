package com.familyboard.app.ui.bucket

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.BucketLife
import com.familyboard.app.data.Family
import com.familyboard.app.data.LifeStats
import com.familyboard.app.data.model.BucketBoards
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.model.ProgressNote
import com.familyboard.app.ui.AppViewModel
import com.familyboard.app.ui.calendar.DescriptionText
import com.familyboard.app.ui.calendar.PhotoStrip
import com.familyboard.app.ui.common.PhotoPickerRow
import java.time.LocalDate

private val Ink = Color(0xFF2B2B2E)
private val Purple = Color(0xFF845EF7)

// ─────────────────────────── 대표 페이지 ───────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BucketHomeScreen(
    vm: AppViewModel,
    currentMemberId: String?,
    onOpenList: () -> Unit,
    onBack: () -> Unit,
) {
    val me = currentMemberId ?: "seonil"
    val items by vm.bucketItems.collectAsStateWithLifecycle()
    val featured = remember(items) { items.filter { it.mustDo }.take(10) }
    val stats = remember(me) { BucketLife.stats(me) }
    val spouse = remember(me) { BucketLife.spouseName(me) }
    val homeTitle = if (spouse != null) "${spouse}과 함께하는 인생 버킷 리스트" else "인생 버킷 리스트"

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(homeTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenList, containerColor = Purple) {
                Icon(Icons.Default.Visibility, "버킷 목록 보기", tint = Color.White)
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            if (stats != null) LifeBox(stats)
            Spacer(Modifier.height(12.dp))
            QuoteBox()
            Spacer(Modifier.height(20.dp))
            Text("꼭 하자! 목록", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(8.dp))
            if (featured.isEmpty()) {
                Text(
                    "아직 '꼭 하자!'로 표시한 항목이 없어요.\n오른쪽 아래 버튼으로 버킷 목록을 열어 표시해 보세요.",
                    color = Ink.copy(alpha = 0.5f),
                )
            } else {
                featured.forEach { itm ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp))
                            .background(Purple.copy(alpha = 0.10f)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            BucketIcons.of(itm.icon) ?: Icons.Default.Star,
                            null, tint = Purple, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            itm.text,
                            Modifier.weight(1f),
                            color = if (itm.checked) Ink.copy(alpha = 0.4f) else Ink,
                            textDecoration = if (itm.checked) TextDecoration.LineThrough else TextDecoration.None,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeBox(stats: LifeStats) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Purple.copy(alpha = 0.12f)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("나의 남은 날", fontSize = 14.sp, color = Ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Text(
            "%,d 일".format(stats.remaining),
            fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Purple,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("지나온 날", "%,d 일".format(stats.passed))
            Stat("진행률", "%.1f%%".format(stats.progressPercent))
            Stat("올해 남은 날", "${stats.remainingThisYear}일")
        }
    }
}

@Composable
private fun QuoteBox() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2B2B2E)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "\"우물쭈물하다가 내 이럴 줄 알았지\"",
            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text("— 조지 버나드 쇼의 묘비 글귀 —", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Ink.copy(alpha = 0.5f))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

// ─────────────────────────── 목록 ───────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BucketListScreen(
    vm: AppViewModel,
    onOpenAdd: () -> Unit,
    onOpenView: (String) -> Unit,
    onBack: () -> Unit,
) {
    val items by vm.bucketItems.collectAsStateWithLifecycle()
    val sorted = remember(items) { items.sortedBy { it.checked } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("버킷 목록") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenAdd, containerColor = Purple) {
                Icon(Icons.Default.Add, "버킷 추가", tint = Color.White)
            }
        },
    ) { padding ->
        if (sorted.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text("버킷 항목이 없어요.\n오른쪽 아래 +로 추가해 보세요.", color = Ink.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(sorted, key = { it.id }) { itm ->
                    BucketRow(
                        item = itm,
                        onToggle = { vm.toggleItem(itm.id, it) },
                        onMustDo = { vm.updateItem(itm.copy(mustDo = !itm.mustDo)) },
                        onClick = { onOpenView(itm.id) },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun BucketRow(item: ListItem, onToggle: (Boolean) -> Unit, onMustDo: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface).clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = onToggle)
        Text(
            item.text,
            Modifier.weight(1f),
            color = if (item.checked) Ink.copy(alpha = 0.4f) else Ink,
            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
        )
        BucketIcons.of(item.icon)?.let { iv ->
            Icon(iv, null, tint = Purple, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        MustDoChip(on = item.mustDo, onClick = onMustDo)
    }
}

@Composable
private fun MustDoChip(on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) Purple else Color(0xFFF1F3F5))
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, null, tint = if (on) Color.White else Color(0xFFAAAAAA), modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(4.dp))
        Text("꼭 하자!", color = if (on) Color.White else Color(0xFF888888), fontSize = 12.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

// ─────────────────────────── 추가/수정 ───────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BucketAddScreen(
    vm: AppViewModel,
    currentMemberId: String?,
    editId: String?,
    onBack: () -> Unit,
) {
    val me = currentMemberId ?: "seonil"
    val editing = remember(editId) { editId?.let { vm.bucketById(it) } }
    var title by remember { mutableStateOf(editing?.text ?: "") }
    var description by remember { mutableStateOf(editing?.description ?: "") }
    var photoUrls by remember { mutableStateOf(editing?.photoUrls ?: emptyList<String>()) }
    var icon by remember { mutableStateOf(editing?.icon ?: "") }

    fun save() {
        if (title.isBlank()) return
        val item = ListItem(
            id = editing?.id ?: "",
            text = title.trim(),
            checked = editing?.checked ?: false,
            board = BucketBoards.BOARD,
            createdBy = editing?.createdBy ?: me,
            mustDo = editing?.mustDo ?: false,
            description = description.trim(),
            photoUrls = photoUrls,
            progress = editing?.progress ?: emptyList(),
            icon = icon,
        )
        if (editing != null) vm.updateItem(item) else vm.addItem(item)
        onBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (editing != null) "버킷 수정" else "버킷 추가") },
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
                .padding(20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("제목") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = description, onValueChange = { if (it.length <= 500) description = it },
                label = { Text("상세 내용 (선택)") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp), minLines = 2,
                supportingText = { Text("${description.length}/500") },
            )
            PhotoPickerRow(photoUrls = photoUrls, onChange = { photoUrls = it })

            Text("아이콘 꾸미기 (선택)", fontWeight = FontWeight.SemiBold, color = Ink.copy(alpha = 0.6f))
            IconPicker(selected = icon, onSelect = { icon = if (icon == it) "" else it })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BucketIcons.all.forEach { (key, iv) ->
            val on = key == selected
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Purple else Color(0xFFF1F3F5))
                    .border(
                        width = if (on) 0.dp else 1.dp,
                        color = Color(0xFFE0E0E0),
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(iv, key, tint = if (on) Color.White else Ink.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ─────────────────────────── 보기(상세 + 진행 이력) ───────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BucketViewScreen(
    vm: AppViewModel,
    itemId: String,
    currentMemberId: String?,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
) {
    val me = currentMemberId ?: "seonil"
    val items by vm.bucketItems.collectAsStateWithLifecycle()
    val item = remember(items, itemId) { items.firstOrNull { it.id == itemId } }
    var note by remember { mutableStateOf("") }
    var editIndex by remember { mutableStateOf(-1) }
    var editText by remember { mutableStateOf("") }
    var confirmDeleteItem by remember { mutableStateOf(false) }
    var deleteHistoryNote by remember { mutableStateOf<ProgressNote?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("버킷") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    if (item != null) IconButton(onClick = { onEdit(itemId) }) { Icon(Icons.Default.Edit, "수정") }
                },
            )
        },
    ) { padding ->
        if (item == null) {
            Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text("삭제된 항목입니다.", color = Ink.copy(alpha = 0.5f))
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BucketIcons.of(item.icon)?.let { iv ->
                    Icon(iv, null, tint = Purple, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    item.text, style = MaterialTheme.typography.headlineMedium, color = Ink,
                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f),
                )
                MustDoChip(on = item.mustDo, onClick = { vm.updateItem(item.copy(mustDo = !item.mustDo)) })
            }
            Spacer(Modifier.height(6.dp))
            Text("등록: ${Family.nameOf(item.createdBy)}", fontSize = 13.sp, color = Ink.copy(alpha = 0.5f))

            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                DescriptionText(item.description)
            }
            if (item.photoUrls.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                PhotoStrip(item.photoUrls)
            }

            Spacer(Modifier.height(24.dp))
            Text("진행 이력", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text("이 꿈을 위해 있었던 일을 기록해서 서로 리마인드해요.", fontSize = 12.sp, color = Ink.copy(alpha = 0.5f))
            Spacer(Modifier.height(10.dp))

            // 이력 추가
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    placeholder = { Text("진행 사항 메모") }, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp), singleLine = true,
                )
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = {
                    if (note.isNotBlank()) {
                        val n = ProgressNote(text = note.trim(), by = me, dateIso = LocalDate.now().toString())
                        vm.updateItem(item.copy(progress = item.progress + n))
                        note = ""
                    }
                }) { Icon(Icons.Default.Add, "이력 추가", tint = Purple) }
            }
            Spacer(Modifier.height(12.dp))

            if (item.progress.isEmpty()) {
                Text("아직 기록이 없어요.", color = Ink.copy(alpha = 0.4f))
            } else {
                for (i in item.progress.indices.reversed()) {
                    val p = item.progress[i]
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F3F5)).padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
                    ) {
                        Text(p.text, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${Family.nameOf(p.by)} · ${p.dateIso}",
                                fontSize = 11.sp, color = Ink.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { editIndex = i; editText = p.text }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, "이력 수정", tint = Ink.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { deleteHistoryNote = p },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Default.Delete, "이력 삭제", tint = Ink.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onEdit(itemId) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, null); Spacer(Modifier.size(6.dp)); Text("수정")
                }
                OutlinedButton(onClick = { confirmDeleteItem = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.size(6.dp)); Text("삭제")
                }
            }
        }
    }

    if (editIndex >= 0 && item != null && editIndex < item.progress.size) {
        AlertDialog(
            onDismissRequest = { editIndex = -1 },
            title = { Text("진행 이력 수정") },
            text = {
                OutlinedTextField(
                    value = editText, onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), minLines = 2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editText.isNotBlank()) {
                        val next = item.progress.toMutableList()
                        next[editIndex] = next[editIndex].copy(text = editText.trim())
                        vm.updateItem(item.copy(progress = next))
                    }
                    editIndex = -1
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editIndex = -1 }) { Text("취소") } },
        )
    }

    // 버킷 항목 삭제 확인
    if (confirmDeleteItem) {
        AlertDialog(
            onDismissRequest = { confirmDeleteItem = false },
            title = { Text("삭제") },
            text = { Text("\"${item?.text ?: "이 항목"}\" 항목을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteItem = false; vm.deleteItem(itemId); onBack() }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteItem = false }) { Text("취소") } },
        )
    }

    // 진행 이력 삭제 확인 (인덱스 대신 항목 매칭 — 다이얼로그 여는 사이 목록이 바뀌어도 안전)
    if (deleteHistoryNote != null && item != null) {
        val note = deleteHistoryNote!!
        AlertDialog(
            onDismissRequest = { deleteHistoryNote = null },
            title = { Text("진행 이력 삭제") },
            text = { Text("이 진행 이력을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    val next = item.progress.toMutableList()
                    val ix = next.indexOf(note)
                    if (ix >= 0) { next.removeAt(ix); vm.updateItem(item.copy(progress = next)) }
                    deleteHistoryNote = null
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteHistoryNote = null }) { Text("취소") } },
        )
    }
}

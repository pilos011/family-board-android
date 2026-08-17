package com.familyboard.app.ui.lists

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.model.TravelBoard
import com.familyboard.app.ui.AppViewModel

/**
 * 여행 위시리스트: 구글 지도 공유로 담은 국내·해외 가볼 곳. 저장 시 서버가 Places API 로
 * 사진·별점·카테고리·나라+도시를 1회 보강. 탭=구글 지도 열기, 롱클릭=메뉴(메모·편집·다녀옴·삭제).
 * 카테고리·지역 필터(가볼 곳 방식). 다녀온 곳은 뒤로.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelScreen(vm: AppViewModel, currentMemberId: String?, onBack: () -> Unit) {
    val items by vm.travelItems.collectAsStateWithLifecycle()
    val me = currentMemberId
    val context = LocalContext.current
    var actionId by remember { mutableStateOf<String?>(null) }
    var editId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var catFilter by remember { mutableStateOf<String?>(null) }
    var regionFilter by remember { mutableStateOf<String?>(null) }
    var showFilter by remember { mutableStateOf(false) }

    val all = items ?: emptyList()
    val catCounts = remember(all) { all.groupingBy { it.category }.eachCount().filterKeys { it.isNotBlank() } }
    val regionCounts = remember(all) { all.groupingBy { it.region }.eachCount().filterKeys { it.isNotBlank() } }
    // 선택된 필터 값이 목록에서 사라지면(편집·삭제로) 빈 화면에 갇히지 않게 해제
    LaunchedEffect(catCounts) { if (catFilter != null && !catCounts.containsKey(catFilter)) catFilter = null }
    LaunchedEffect(regionCounts) { if (regionFilter != null && !regionCounts.containsKey(regionFilter)) regionFilter = null }

    // 필터 적용 → 안 가본 곳 먼저, 다녀온 곳 뒤. 그 안에서 최신순.
    val sorted = remember(all, catFilter, regionFilter) {
        all.filter { (catFilter == null || it.category == catFilter) && (regionFilter == null || it.region == regionFilter) }
            .sortedWith(compareBy<ListItem> { if (it.checked) 1 else 0 }.thenByDescending { it.createdAt })
    }

    fun openMap(item: ListItem) {
        val uri = when {
            item.link.isNotBlank() -> Uri.parse(item.link)
            item.lat != 0.0 || item.lng != 0.0 -> Uri.parse("geo:${item.lat},${item.lng}?q=${item.lat},${item.lng}(${Uri.encode(item.text)})")
            else -> null
        }
        if (uri == null) { Toast.makeText(context, "위치 정보가 없어요", Toast.LENGTH_SHORT).show(); return }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { Toast.makeText(context, "지도를 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(TravelBoard.TITLE) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
        )
        when {
            items == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            all.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "아직 담은 여행지가 없어요.\n구글 지도에서 장소를 '공유'로 담아보세요.\n(국내·해외 모두)",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                )
            }
            else -> Column(Modifier.fillMaxSize()) {
                // 필터 바: [필터] 버튼 + 선택 요약 (가볼 곳과 동일 구조). 칩은 바텀시트에.
                if (catCounts.isNotEmpty() || regionCounts.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val active = catFilter != null || regionFilter != null
                        Row(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (active) MaterialTheme.colorScheme.primary else Color(0xFFF1F3F5))
                                .clickable { showFilter = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Tune, "필터", tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("필터", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.width(10.dp))
                        val summary = listOfNotNull(catFilter, regionFilter).joinToString(" · ").ifBlank { "전체" }
                        Text("$summary · ${sorted.size}곳", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                if (sorted.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text("조건에 맞는 곳이 없어요. (필터 확인)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(sorted, key = { it.id }) { item ->
                        TravelCard(item = item, onOpen = { openMap(item) }, onLongPress = { actionId = item.id })
                    }
                }
            }
        }
    }

    actionId?.let { id ->
        val item = all.firstOrNull { it.id == id }
        if (item == null) LaunchedEffect(id) { actionId = null }
        else {
            val canManage = TravelBoard.canManage(me, item)
            AlertDialog(
                onDismissRequest = { actionId = null },
                title = { Text(item.text.ifBlank { "장소" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        val sub = listOfNotNull(item.category.ifBlank { null }, item.region.ifBlank { null }).joinToString(" · ")
                        if (sub.isNotBlank()) { Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(4.dp)) }
                        if (item.naverScore > 0.0) { Text(ratingText(item), fontSize = 13.sp); Spacer(Modifier.height(6.dp)) }
                        Text("메모", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (item.description.isNotBlank()) SelectionContainer { Text(item.description, fontSize = 13.sp) }
                        else Text("없음 (편집에서 추가)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        if (item.checked) { Spacer(Modifier.height(6.dp)); Text("✅ 다녀왔어요", fontSize = 13.sp, color = Color(0xFF12B886), fontWeight = FontWeight.Bold) }
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = { vm.toggleTravelVisited(item); actionId = null }) { Text(if (item.checked) "안 갔음" else "다녀옴") }
                        if (canManage) TextButton(onClick = { editId = id; actionId = null }) { Text("편집") }
                        if (canManage) TextButton(onClick = { pendingDelete = id; actionId = null }) { Text("삭제", color = Color(0xFFE03131)) }
                        TextButton(onClick = { actionId = null }) { Text("닫기") }
                    }
                },
            )
        }
    }

    editId?.let { id ->
        val item = all.firstOrNull { it.id == id }
        if (item == null) LaunchedEffect(id) { editId = null }
        else TravelEditDialog(
            item = item,
            onSave = { name, cat, memo -> vm.updateTravel(id, name, cat, memo); editId = null },
            onDismiss = { editId = null },
        )
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("여행지 삭제") },
            text = { Text("이 장소를 목록에서 지울까요?") },
            confirmButton = { TextButton(onClick = { vm.deleteItem(id); pendingDelete = null }) { Text("삭제", color = Color(0xFFE03131)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }

    // 필터 바텀시트: 카테고리·지역 칩(개수 포함)을 한눈에 (가볼 곳과 동일 구조)
    if (showFilter) {
        ModalBottomSheet(onDismissRequest = { showFilter = false }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                if (catCounts.isNotEmpty()) {
                    Text("카테고리", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    FilterFlow {
                        FilterChip("전체", all.size, catFilter == null) { catFilter = null }
                        catCounts.entries.sortedByDescending { it.value }.forEach { (c, n) ->
                            FilterChip(c, n, catFilter == c) { catFilter = if (catFilter == c) null else c }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (regionCounts.isNotEmpty()) {
                    Text("지역 (나라·도시)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    FilterFlow {
                        FilterChip("전체", all.size, regionFilter == null) { regionFilter = null }
                        regionCounts.entries.sortedByDescending { it.value }.forEach { (r, n) ->
                            FilterChip(r, n, regionFilter == r) { regionFilter = if (regionFilter == r) null else r }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { catFilter = null; regionFilter = null }, modifier = Modifier.weight(1f)) { Text("초기화") }
                    Button(onClick = { showFilter = false }, modifier = Modifier.weight(2f)) { Text("${sorted.size}곳 보기") }
                }
            }
        }
    }
}

private fun ratingText(item: ListItem): String {
    if (item.naverScore <= 0.0) return ""
    val cnt = if (item.amount > 0) " (리뷰 ${item.amount})" else ""
    return "⭐ ${item.naverScore}$cnt"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TravelCard(item: ListItem, onOpen: () -> Unit, onLongPress: () -> Unit) {
    val visited = item.checked
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (visited) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val photo = com.familyboard.app.notif.NotifyApi.placePhotoUrl(item.photoUrls.firstOrNull())
            if (!photo.isNullOrBlank()) {
                AsyncImage(
                    model = photo, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE3FAFC)), Alignment.Center) {
                    Icon(Icons.Default.Place, null, tint = Color(0xFF1098AD))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.text.ifBlank { "장소" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (visited) {
                        Spacer(Modifier.width(6.dp))
                        Text("다녀옴", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFF12B886), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                // 카테고리 · 지역 칩 + 별점
                val chips = listOfNotNull(item.category.ifBlank { null }, item.region.ifBlank { null })
                if (chips.isNotEmpty() || item.naverScore > 0.0) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        chips.forEach { c ->
                            Text(c, fontSize = 11.sp, color = Color(0xFF0C8599), fontWeight = FontWeight.Medium, modifier = Modifier.background(Color(0xFFE3FAFC), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        if (item.naverScore > 0.0) Text(ratingText(item), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
                // 메모 미리보기(20자) — 가볼 곳 주소 자리 대신
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    val preview = item.description.replace("\n", " ").let { if (it.length > 20) it.take(20) + "…" else it }
                    Text("📝 $preview", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun FilterChip(label: String, count: Int, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) MaterialTheme.colorScheme.primary else Color(0xFFF1F3F5))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Color.White else Color(0xFF444444))
        Spacer(Modifier.width(4.dp))
        Text("$count", fontSize = 11.sp, color = if (on) Color.White.copy(alpha = 0.85f) else Color(0xFF999999))
    }
}

@Composable
private fun TravelEditDialog(item: ListItem, onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.text) }
    var category by remember(item.id) { mutableStateOf(item.category) }
    var memo by remember(item.id) { mutableStateOf(item.description) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("여행지 편집") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                    label = { Text("장소명") }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = category, onValueChange = { category = it.take(30) }, singleLine = true,
                    label = { Text("카테고리(필터)") }, placeholder = { Text("예: 5성급 호텔, 맛집, 관광") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = memo, onValueChange = { memo = it }, minLines = 3, maxLines = 8,
                    label = { Text("메모") }, placeholder = { Text("예약·영업시간·꿀팁 등 (길게 붙여넣기 가능)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, category, memo) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

package com.familyboard.app.ui.lists

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.model.PlaceBoards
import com.familyboard.app.ui.AppViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val Gold = Color(0xFFF6B23C)
private const val EUNSEON = "eunseon"

private enum class SortMode(val label: String) { NAVER("네이버 평점순"), EUNSEON_SCORE("은선 평점순"), DISTANCE("거리순") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceListScreen(
    vm: AppViewModel,
    boardKey: String,
    currentMemberId: String?,
    onBack: () -> Unit,
) {
    val items by vm.placeItems(boardKey).collectAsStateWithLifecycle()
    val title = PlaceBoards.titleOf(boardKey)
    val context = LocalContext.current
    val canRate = currentMemberId == EUNSEON

    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ListItem?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.NAVER) }
    var myLoc by remember { mutableStateOf<Location?>(null) }
    var ratingConfirm by remember { mutableStateOf<Pair<ListItem, Int>?>(null) }
    var editComment by remember { mutableStateOf<Triple<ListItem, Int, String>?>(null) }
    // 삭제 확인용: 삭제하려는 장소 / 삭제하려는 댓글(장소+인덱스)
    var pendingDelete by remember { mutableStateOf<ListItem?>(null) }
    var commentDelete by remember { mutableStateOf<Pair<ListItem, Int>?>(null) }
    // 필터: 카테고리(종목)/지역(시·군·구). null=전체. 바텀시트 열림 상태.
    var catFilter by remember(boardKey) { mutableStateOf<String?>(null) }
    var regionFilter by remember(boardKey) { mutableStateOf<String?>(null) }
    var showFilter by remember { mutableStateOf(false) }
    // 발굴 추천(카카오+Groq): 결과 목록 / 로딩
    var recommends by remember { mutableStateOf<List<com.familyboard.app.notif.Recommendation>>(emptyList()) }
    var recommending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { myLoc = lastKnownLocation(context) }
    // 정렬/필터 바꾸면 항상 맨 위로 이동
    LaunchedEffect(sortMode, catFilter, regionFilter) { listState.scrollToItem(0) }

    // 필터 옵션(데이터에 실제 있는 값만) + 개수
    val catCounts = remember(items) { items.groupingBy { it.category.ifBlank { "기타" } }.eachCount() }
    val regionCounts = remember(items) { items.groupingBy { regionOf(it.address).ifBlank { "기타" } }.eachCount() }

    val filtered = remember(items, catFilter, regionFilter) {
        items.filter {
            (catFilter == null || it.category.ifBlank { "기타" } == catFilter) &&
                (regionFilter == null || regionOf(it.address).ifBlank { "기타" } == regionFilter)
        }
    }
    val sorted = remember(filtered, sortMode, myLoc) {
        when (sortMode) {
            SortMode.NAVER -> filtered.sortedWith(compareByDescending<ListItem> { it.naverScore }.thenBy { it.text })
            SortMode.EUNSEON_SCORE -> filtered.sortedWith(compareByDescending<ListItem> { it.rating }.thenBy { it.text })
            SortMode.DISTANCE -> {
                val me = myLoc
                if (me == null) filtered
                else filtered.sortedBy { distanceOrNull(me, it) ?: Double.MAX_VALUE }
            }
        }
    }

    fun openLink(link: String) {
        if (link.isBlank()) { Toast.makeText(context, "저장된 링크가 없어요", Toast.LENGTH_SHORT).show(); return }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            .onFailure { Toast.makeText(context, "링크를 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }
    // 추천 장소를 네이버 지도에서 열기. 상호만으로 검색(주소까지 넣으면 장소검색이 실패함).
    fun openNaver(name: String) {
        val q = java.net.URLEncoder.encode(name.trim(), "UTF-8")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://map.naver.com/p/search/$q"))) }
            .onFailure { Toast.makeText(context, "지도를 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "장소 추가", tint = Color.White)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 필터 바: [필터] 버튼 + 현재 선택 요약(개수)
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(if (catFilter != null || regionFilter != null) MaterialTheme.colorScheme.primary else Color(0xFFF1F3F5))
                        .clickable { showFilter = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val active = catFilter != null || regionFilter != null
                    Icon(Icons.Default.Tune, "필터", tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("필터", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.size(10.dp))
                val summary = listOfNotNull(catFilter, regionFilter).joinToString(" · ").ifBlank { "전체" }
                Text("$summary · ${sorted.size}곳", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                // 근처 추천(Groq)
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable(enabled = !recommending) {
                            val loc = myLoc ?: lastKnownLocation(context).also { myLoc = it }
                            if (regionFilter == null && loc == null) {
                                Toast.makeText(context, "지역을 선택하거나 위치를 켜주세요", Toast.LENGTH_SHORT).show()
                            } else {
                                recommending = true; recommends = emptyList()
                                val savedNames = items.map { it.text }
                                vm.recommendPlace(boardKey, catFilter ?: "", regionFilter ?: "", savedNames, loc?.latitude, loc?.longitude) { list ->
                                    recommending = false
                                    recommends = list
                                    if (list.isEmpty()) Toast.makeText(context, "추천할 새 장소를 못 찾았어요", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.AutoAwesome, "추천", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.size(5.dp))
                    Text(if (recommending) "추천 중…" else "추천받기", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                }
            }
            // 발굴 추천 결과: 놓친 곳 2~3개(탭 시 네이버 지도)
            if (recommends.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("아직 안 담은 근처 추천", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Close, "닫기", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp).clickable { recommends = emptyList() })
                    }
                    recommends.forEach { rec ->
                        Spacer(Modifier.size(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { openNaver(rec.name) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                val star = rec.rating?.let { "★$it" + (if (rec.ratingCount > 0) " (${rec.ratingCount})" else "") }.orEmpty()
                                val dist = rec.dist?.let { " · " + fmtDist(it) }.orEmpty()
                                Text("${rec.name}${if (star.isNotBlank()) " · $star" else ""}$dist", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (rec.reason.isNotBlank())
                                    Text(rec.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.OpenInNew, "네이버 지도", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            // 정렬 선택
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortMode.entries.forEach { m ->
                    val on = m == sortMode
                    Text(
                        m.label,
                        fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) MaterialTheme.colorScheme.primary else Color(0xFFF1F3F5))
                            .clickable {
                                if (m == SortMode.DISTANCE && myLoc == null) {
                                    myLoc = lastKnownLocation(context)
                                    if (myLoc == null) Toast.makeText(context, "현재 위치를 알 수 없어요(위치 권한/GPS 확인)", Toast.LENGTH_SHORT).show()
                                }
                                sortMode = m
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }

            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "아직 등록된 장소가 없어요.\n네이버 플레이스에서 공유하거나\n오른쪽 아래 +로 추가하세요.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sorted, key = { it.id }) { place ->
                        PlaceCard(
                            item = place,
                            currentMemberId = currentMemberId,
                            canRate = canRate,
                            distanceText = myLoc?.let { distanceOrNull(it, place)?.let { d -> fmtDist(d) } },
                            onOpenLink = { openLink(place.link) },
                            onRateRequest = { n -> ratingConfirm = place to n },
                            onAddComment = { t -> vm.addPlaceComment(place, t) },
                            onEditComment = { i, t -> editComment = Triple(place, i, t) },
                            onDeleteComment = { i -> commentDelete = place to i },
                            onEdit = { editItem = place },
                            onDelete = { pendingDelete = place },
                        )
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        PlaceEditDialog(vm, null,
            onSave = { n, l, d, a, img, cat -> vm.addPlace(boardKey, n, l, d, a, img, category = cat); showAdd = false },
            onDismiss = { showAdd = false })
    }
    editItem?.let { it0 ->
        PlaceEditDialog(vm, it0,
            onSave = { n, l, d, a, img, cat -> vm.updatePlace(it0, n, l, d, a, img, category = cat); editItem = null },
            onDismiss = { editItem = null })
    }
    // 별점 등록/수정 확인 (은선만 진입)
    ratingConfirm?.let { (item, n) ->
        val editing = item.rating > 0
        AlertDialog(
            onDismissRequest = { ratingConfirm = null },
            title = { Text(if (editing) "별점 수정" else "별점 등록") },
            text = { Text("★ ${n}점으로 ${if (editing) "수정" else "등록"}할까요?") },
            confirmButton = { TextButton(onClick = { vm.setPlaceRating(item, n); ratingConfirm = null }) { Text(if (editing) "수정" else "등록") } },
            dismissButton = { TextButton(onClick = { ratingConfirm = null }) { Text("취소") } },
        )
    }
    // 댓글 수정
    editComment?.let { (item, idx, cur) ->
        var t by remember(item.id, idx) { mutableStateOf(cur) }
        AlertDialog(
            onDismissRequest = { editComment = null },
            title = { Text("댓글 수정") },
            text = { OutlinedTextField(value = t, onValueChange = { t = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) },
            confirmButton = { TextButton(enabled = t.isNotBlank(), onClick = { vm.updatePlaceComment(item, idx, t); editComment = null }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { editComment = null }) { Text("취소") } },
        )
    }
    // 장소 삭제 확인
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("삭제") },
            text = { Text("\"${target.text.ifBlank { "이 장소" }}\" 항목을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteItem(target.id); pendingDelete = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
    // 댓글 삭제 확인
    commentDelete?.let { (item, idx) ->
        AlertDialog(
            onDismissRequest = { commentDelete = null },
            title = { Text("댓글 삭제") },
            text = { Text("이 댓글을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { vm.deletePlaceComment(item, idx); commentDelete = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { commentDelete = null }) { Text("취소") } },
        )
    }

    // 필터 바텀시트: 카테고리·지역 칩(개수 포함)을 한눈에
    if (showFilter) {
        ModalBottomSheet(onDismissRequest = { showFilter = false }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                Text("카테고리", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp))
                FilterFlow {
                    FilterChip("전체", items.size, catFilter == null) { catFilter = null }
                    catCounts.entries.sortedByDescending { it.value }.forEach { (c, n) ->
                        FilterChip(c, n, catFilter == c) { catFilter = if (catFilter == c) null else c }
                    }
                }
                Spacer(Modifier.size(16.dp))
                Text("지역 (시·군·구)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp))
                FilterFlow {
                    FilterChip("전체", items.size, regionFilter == null) { regionFilter = null }
                    regionCounts.entries.sortedByDescending { it.value }.forEach { (r, n) ->
                        FilterChip(r, n, regionFilter == r) { regionFilter = if (regionFilter == r) null else r }
                    }
                }
                Spacer(Modifier.size(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { catFilter = null; regionFilter = null }, modifier = Modifier.weight(1f)) { Text("초기화") }
                    androidx.compose.material3.Button(onClick = { showFilter = false }, modifier = Modifier.weight(2f)) {
                        Text("${sorted.size}곳 보기")
                    }
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
        Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = if (on) Color.White else Color(0xFF444444))
        Spacer(Modifier.size(4.dp))
        Text("$count", fontSize = 11.sp, color = if (on) Color.White.copy(alpha = 0.85f) else Color(0xFF999999))
    }
}

@Composable
private fun PlaceCard(
    item: ListItem,
    currentMemberId: String?,
    canRate: Boolean,
    distanceText: String?,
    onOpenLink: () -> Unit,
    onRateRequest: (Int) -> Unit,
    onAddComment: (String) -> Unit,
    onEditComment: (Int, String) -> Unit,
    onDeleteComment: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showComments by remember { mutableStateOf(false) }
    var comment by remember { mutableStateOf("") }
    val rating = item.rating.toInt()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row {
                val photo = item.photoUrls.firstOrNull()
                if (!photo.isNullOrBlank()) {
                    // 우리 서버 이미지는 500px 썸네일 사용(외부 네이버 이미지는 그대로)
                    AsyncImage(
                        model = funThumbUrl(photo), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).clickable { onOpenLink() },
                    )
                    Spacer(Modifier.size(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.text.ifBlank { "이름 없음" },
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable { onOpenLink() },
                        )
                        if (distanceText != null) {
                            Text(distanceText, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                        }
                        if (item.link.isNotBlank()) {
                            Icon(Icons.Default.OpenInNew, "링크 열기", tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp).size(20.dp).clickable { onOpenLink() })
                        }
                    }
                    if (item.description.isNotBlank()) {
                        Text(item.description, fontSize = 13.sp, lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
                    }
                    if (item.address.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text("📍 ${item.address}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // 은선 별점 (은선만 변경 가능, 0 = 미방문)
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { n ->
                    val star = Modifier.size(26.dp).padding(1.dp)
                    Icon(
                        if (n <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "별 $n",
                        tint = if (n <= rating) Gold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = if (canRate) star.clickable { onRateRequest(if (rating == n) 0 else n) } else star,
                    )
                }
                Spacer(Modifier.size(8.dp))
                if (rating == 0) {
                    Text("미방문", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.background(Color(0xFFECECEC), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                } else {
                    Text("은선 ★$rating", fontSize = 12.sp, color = Gold, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showComments = !showComments }) { Text("댓글 ${item.progress.size}", fontSize = 13.sp) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "수정", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "삭제", modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
            }

            if (showComments) {
                Spacer(Modifier.height(4.dp))
                item.progress.forEachIndexed { i, note ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("${Family.nameOf(note.by)} · ${note.dateIso}", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(note.text, fontSize = 14.sp)
                        }
                        if (note.by == currentMemberId) {
                            Icon(Icons.Default.Edit, "댓글 수정",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(15.dp).clickable { onEditComment(i, note.text) })
                            Spacer(Modifier.size(10.dp))
                            Icon(Icons.Default.Close, "댓글 삭제",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(15.dp).clickable { onDeleteComment(i) })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = comment, onValueChange = { comment = it },
                        placeholder = { Text("댓글 남기기", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(20.dp),
                    )
                    IconButton(onClick = { if (comment.isNotBlank()) { onAddComment(comment); comment = "" } }) {
                        Icon(Icons.Default.Send, "등록", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceEditDialog(
    vm: AppViewModel,
    item: ListItem?,
    onSave: (String, String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(item?.text ?: "") }
    var link by remember { mutableStateOf(item?.link ?: "") }
    var address by remember { mutableStateOf(item?.address ?: "") }
    var description by remember { mutableStateOf(item?.description ?: "") }
    var image by remember { mutableStateOf(item?.photoUrls?.firstOrNull() ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "") }
    var fetching by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "장소 추가" else "장소 수정") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("이름") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("네이버 링크 (선택)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                TextButton(
                    enabled = !fetching && link.isNotBlank(),
                    onClick = {
                        fetching = true
                        vm.fetchPlaceInfo(link.trim()) { info ->
                            fetching = false
                            if (info != null && info.name.isNotBlank()) {
                                name = info.name; address = info.address; description = vm.describePlace(info)
                                category = info.category
                                if (info.image.isNotBlank()) image = info.image
                                Toast.makeText(context, "정보를 가져왔어요", Toast.LENGTH_SHORT).show()
                            } else Toast.makeText(context, "정보를 가져오지 못했어요", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text(if (fetching) "가져오는 중…" else "네이버에서 정보 가져오기") }
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("주소 (선택)") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), link.trim(), description, address.trim(), image, category.trim()) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

// ─────────── 지역(주소 파싱) ───────────
private val SIDO_SHORT = mapOf(
    "서울특별시" to "서울", "부산광역시" to "부산", "대구광역시" to "대구", "인천광역시" to "인천",
    "광주광역시" to "광주", "대전광역시" to "대전", "울산광역시" to "울산", "세종특별자치시" to "세종",
    "경기도" to "경기", "강원도" to "강원", "강원특별자치도" to "강원", "충청북도" to "충북", "충청남도" to "충남",
    "전라북도" to "전북", "전북특별자치도" to "전북", "전라남도" to "전남", "경상북도" to "경북", "경상남도" to "경남",
    "제주특별자치도" to "제주", "제주도" to "제주",
)

/** 도로명 주소 → "시도 시/군/구"(예: 경기 고양시, 부산 남구). 못 찾으면 "". */
private fun regionOf(address: String): String {
    val toks = address.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (toks.isEmpty()) return ""
    val sido = SIDO_SHORT[toks[0]] ?: SIDO_SHORT.values.firstOrNull { toks[0].startsWith(it) } ?: toks[0].take(2)
    val gu = toks.getOrNull(1)?.takeIf { it.endsWith("시") || it.endsWith("군") || it.endsWith("구") } ?: ""
    return listOf(sido, gu).filter { it.isNotBlank() }.joinToString(" ")
}

// ─────────── 위치/거리 ───────────
@SuppressLint("MissingPermission")
private fun lastKnownLocation(context: Context): Location? {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        .maxByOrNull { it.time }
}

/** 두 지점 거리(km). 좌표 없으면 null. */
private fun distanceOrNull(me: Location, item: ListItem): Double? {
    if (item.lat == 0.0 && item.lng == 0.0) return null
    val r = 6371.0
    val dLat = Math.toRadians(item.lat - me.latitude)
    val dLng = Math.toRadians(item.lng - me.longitude)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(me.latitude)) * cos(Math.toRadians(item.lat)) * sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun fmtDist(km: Double): String =
    if (km < 1.0) "${(km * 1000).toInt()}m" else "%.1fkm".format(km)

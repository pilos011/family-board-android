package com.familyboard.app.ui.lists

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.model.PlaceBoards
import com.familyboard.app.ui.AppViewModel

private val Gold = Color(0xFFF6B23C)

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
    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ListItem?>(null) }

    val sorted = remember(items) { items.sortedWith(compareByDescending<ListItem> { it.rating }.thenBy { it.text }) }

    fun openLink(link: String) {
        if (link.isBlank()) { Toast.makeText(context, "저장된 링크가 없어요", Toast.LENGTH_SHORT).show(); return }
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            .onFailure { Toast.makeText(context, "링크를 열 수 없어요", Toast.LENGTH_SHORT).show() }
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
        if (sorted.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "아직 등록된 장소가 없어요.\n네이버 플레이스에서 공유하거나\n오른쪽 아래 +로 추가하세요.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(sorted, key = { it.id }) { place ->
                    PlaceCard(
                        item = place,
                        currentMemberId = currentMemberId,
                        onOpenLink = { openLink(place.link) },
                        onRate = { r -> vm.setPlaceRating(place, r) },
                        onAddComment = { t -> vm.addPlaceComment(place, t) },
                        onDeleteComment = { i -> vm.deletePlaceComment(place, i) },
                        onEdit = { editItem = place },
                        onDelete = { vm.deleteItem(place.id) },
                    )
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    if (showAdd) {
        PlaceEditDialog(vm, null,
            onSave = { n, l, d, a, img -> vm.addPlace(boardKey, n, l, d, a, img); showAdd = false },
            onDismiss = { showAdd = false })
    }
    editItem?.let { it0 ->
        PlaceEditDialog(vm, it0,
            onSave = { n, l, d, a, img -> vm.updatePlace(it0, n, l, d, a, img); editItem = null },
            onDismiss = { editItem = null })
    }
}

@Composable
private fun PlaceCard(
    item: ListItem,
    currentMemberId: String?,
    onOpenLink: () -> Unit,
    onRate: (Int) -> Unit,
    onAddComment: (String) -> Unit,
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
            // 상단: 대표 사진 + (이름/요약/주소)
            Row {
                val photo = item.photoUrls.firstOrNull()
                if (!photo.isNullOrBlank()) {
                    AsyncImage(
                        model = photo, contentDescription = null,
                        contentScale = ContentScale.Crop,
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
                        if (item.link.isNotBlank()) {
                            Icon(Icons.Default.OpenInNew, "링크 열기", tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp).clickable { onOpenLink() })
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
            // 별점 (0 = 미방문)
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { n ->
                    Icon(
                        if (n <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "별 $n",
                        tint = if (n <= rating) Gold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(26.dp).clickable { onRate(if (rating == n) 0 else n) }.padding(1.dp),
                    )
                }
                Spacer(Modifier.size(8.dp))
                if (rating == 0) {
                    Text("미방문", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.background(Color(0xFFECECEC), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            // 하단: 댓글 토글 + 수정/삭제
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showComments = !showComments }) {
                    Text("댓글 ${item.progress.size}", fontSize = 13.sp)
                }
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
                            Icon(Icons.Default.Close, "댓글 삭제",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp).clickable { onDeleteComment(i) })
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
    onSave: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(item?.text ?: "") }
    var link by remember { mutableStateOf(item?.link ?: "") }
    var address by remember { mutableStateOf(item?.address ?: "") }
    var description by remember { mutableStateOf(item?.description ?: "") }
    var image by remember { mutableStateOf(item?.photoUrls?.firstOrNull() ?: "") }
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
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), link.trim(), description, address.trim(), image) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

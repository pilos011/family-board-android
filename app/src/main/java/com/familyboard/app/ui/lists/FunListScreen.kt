package com.familyboard.app.ui.lists

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyboard.app.data.model.FunBoard
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunListScreen(
    vm: AppViewModel,
    boardKey: String,
    isPrivate: Boolean,
    currentMemberId: String?,
    onBack: () -> Unit,
) {
    val all by vm.funBoardItems(boardKey).collectAsStateWithLifecycle()
    val items = remember(all, isPrivate, currentMemberId) {
        if (isPrivate) all.filter { it.createdBy == currentMemberId } else all
    }
    val context = LocalContext.current
    val isParent = currentMemberId == "seonil" || currentMemberId == "eunseon"
    fun canEdit(it: ListItem) = it.createdBy == currentMemberId || isParent

    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ListItem?>(null) }
    var actionItem by remember { mutableStateOf<ListItem?>(null) }
    var viewerImages by remember { mutableStateOf<List<String>?>(null) }
    var youtubeOn by remember { mutableStateOf(true) }
    var websiteOn by remember { mutableStateOf(true) }
    var oldestFirst by remember { mutableStateOf(false) }
    var hideViewed by remember { mutableStateOf(false) }

    val shown = remember(items, youtubeOn, websiteOn, oldestFirst, hideViewed, currentMemberId) {
        var f = items.filter { val yt = isYoutube(it.link); (youtubeOn && yt) || (websiteOn && !yt) }
        if (hideViewed) f = f.filter { !it.viewedBy.contains(currentMemberId) }
        if (oldestFirst) f.sortedBy { it.createdAt } else f.sortedByDescending { it.createdAt }
    }

    fun open(link: String) {
        if (link.isBlank()) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            .onFailure { Toast.makeText(context, "링크를 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }
    // 영상은 명시적 MIME으로 열어 브라우저 다운로드가 아니라 동영상 플레이어로 재생
    fun playVideo(link: String) {
        if (link.isBlank()) return
        val l = link.substringBefore('?').lowercase()
        val mime = when {
            l.endsWith(".mp4") -> "video/mp4"
            l.endsWith(".mov") -> "video/quicktime"
            l.endsWith(".webm") -> "video/webm"
            l.endsWith(".mkv") -> "video/x-matroska"
            l.endsWith(".3gp") -> "video/3gpp"
            else -> "video/*"
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(link), mime))
        }.onFailure { Toast.makeText(context, "재생할 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(FunBoard.titleOf(boardKey) + if (isPrivate) " · 나만 볼 수 있어요" else "") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "추가", tint = Color.White)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TogglePill("유튜브", youtubeOn) { youtubeOn = !youtubeOn }
                TogglePill("웹사이트", websiteOn) { websiteOn = !websiteOn }
                TogglePill("등록순", oldestFirst) { oldestFirst = !oldestFirst }
                TogglePill("이미 본 게시물 제외", hideViewed) { hideViewed = !hideViewed }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (items.isEmpty()) "아직 게시물이 없어요.\n유튜브·웹·이미지를 '공유 → 준준가족 보드'\n또는 오른쪽 아래 +로 담아보세요."
                        else "표시할 항목이 없어요. (필터 확인)",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown.chunked(4)) { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems.forEach { post ->
                                FunCell(post, Modifier.weight(1f),
                                    viewed = currentMemberId != null && post.viewedBy.contains(currentMemberId),
                                    onClick = {
                                        vm.markFunViewed(post)
                                        when {
                                            post.link.isBlank() && post.photoUrls.isNotEmpty() -> viewerImages = post.photoUrls
                                            isVideo(post.link) -> playVideo(post.link)
                                            else -> open(post.link)
                                        }
                                    },
                                    onLongPress = { if (canEdit(post)) actionItem = post })
                            }
                            repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // 이미지 전체보기: 여러 장은 상하로 이어서 스크롤. Coil 디스크 캐시로 두 번째부터 재다운로드 없음.
    viewerImages?.let { urls ->
        Dialog(onDismissRequest = { viewerImages = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())
                    .clickable { viewerImages = null },
            ) {
                urls.forEach { u ->
                    AsyncImage(model = u, contentDescription = null, contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (showAdd) FunEditDialog(vm, null, onSave = { t, l, img -> vm.addFun(boardKey, t, l, if (img.isBlank()) emptyList() else listOf(img)); showAdd = false }, onDismiss = { showAdd = false })
    editItem?.let { it0 -> FunEditDialog(vm, it0, onSave = { t, l, img -> vm.updateFun(it0, t, l, img); editItem = null }, onDismiss = { editItem = null }) }
    actionItem?.let { it0 ->
        AlertDialog(
            onDismissRequest = { actionItem = null },
            title = { Text(it0.text.ifBlank { "게시물" }) },
            text = { Text("이 게시물을 어떻게 할까요?") },
            confirmButton = { TextButton(onClick = { editItem = it0; actionItem = null }) { Text("수정") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.deleteItem(it0.id); actionItem = null }) { Text("삭제", color = Color(0xFFE03131)) }
                    TextButton(onClick = { actionItem = null }) { Text("취소") }
                }
            },
        )
    }
}

private fun isYoutube(link: String) = link.contains("youtu.be", true) || link.contains("youtube.com", true)
private fun isVideo(link: String): Boolean {
    val l = link.substringBefore('?').lowercase()
    return l.endsWith(".mp4") || l.endsWith(".mov") || l.endsWith(".webm") || l.endsWith(".mkv") || l.endsWith(".3gp")
}

@Composable
private fun TogglePill(label: String, on: Boolean, onToggle: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (on) MaterialTheme.colorScheme.primary else Color(0xFFF1F3F5))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun FunCell(item: ListItem, modifier: Modifier, viewed: Boolean, onClick: () -> Unit, onLongPress: () -> Unit) {
    Column(
        modifier.pointerInput(item.id) {
            detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
        },
    ) {
        val photo = item.photoUrls.firstOrNull()
        val video = isVideo(item.link)
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFECECEC)).alpha(if (viewed) 0.35f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            if (!photo.isNullOrBlank()) {
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.PlayCircle, null, tint = Color(0xFFB0B0B0), modifier = Modifier.size(30.dp))
            }
            if (video) Icon(Icons.Default.PlayCircle, "영상", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(34.dp))
            if (item.photoUrls.size > 1) {
                Text("+${item.photoUrls.size - 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(item.text.ifBlank { "제목 없음" }, fontSize = 11.sp, lineHeight = 13.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
            color = if (viewed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FunEditDialog(vm: AppViewModel, item: ListItem?, onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(item?.text ?: "") }
    var link by remember { mutableStateOf(item?.link ?: "") }
    var image by remember { mutableStateOf(item?.photoUrls?.firstOrNull() ?: "") }
    var fetching by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "게시물 추가" else "게시물 수정") },
        text = {
            Column {
                OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("링크 (유튜브/웹)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                TextButton(enabled = !fetching && link.isNotBlank(), onClick = {
                    fetching = true
                    vm.fetchLinkInfo(link.trim()) { info ->
                        fetching = false
                        if (info != null && (info.title.isNotBlank() || info.image.isNotBlank())) {
                            if (info.title.isNotBlank()) title = info.title
                            if (info.image.isNotBlank()) image = info.image
                            Toast.makeText(context, "정보를 가져왔어요", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(context, "정보를 가져오지 못했어요", Toast.LENGTH_SHORT).show()
                    }
                }) { Text(if (fetching) "가져오는 중…" else "링크 정보 가져오기") }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("제목") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = link.isNotBlank() || image.isNotBlank(),
                onClick = { onSave(title.trim().ifBlank { "링크" }, link.trim(), image) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

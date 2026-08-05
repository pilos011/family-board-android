package com.familyboard.app.ui.lists

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.familyboard.app.data.model.FunBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val shareScope = rememberCoroutineScope()
    val isParent = currentMemberId == "seonil" || currentMemberId == "eunseon"
    fun canEdit(it: ListItem) = it.createdBy == currentMemberId || isParent

    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ListItem?>(null) }
    var actionItem by remember { mutableStateOf<ListItem?>(null) }
    var viewerImages by remember { mutableStateOf<List<String>?>(null) }
    var playUrl by remember { mutableStateOf<String?>(null) }
    var youtubeOn by remember { mutableStateOf(true) }
    var websiteOn by remember { mutableStateOf(true) }
    var imageOn by remember { mutableStateOf(true) }
    var oldestFirst by remember { mutableStateOf(false) }
    var hideViewed by remember { mutableStateOf(false) }

    val shown = remember(items, youtubeOn, websiteOn, imageOn, oldestFirst, hideViewed, currentMemberId) {
        var f = items.filter {
            val yt = isYoutube(it.link)
            val img = it.link.isBlank() && it.photoUrls.isNotEmpty()
            val web = !yt && !img
            (youtubeOn && yt) || (imageOn && img) || (websiteOn && web)
        }
        if (hideViewed) f = f.filter { !it.viewedBy.contains(currentMemberId) }
        if (oldestFirst) f.sortedBy { it.createdAt } else f.sortedByDescending { it.createdAt }
    }

    fun open(link: String) {
        if (link.isBlank()) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            .onFailure { Toast.makeText(context, "링크를 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }
    fun shareText(text: String) {
        runCatching {
            context.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "공유"))
        }.onFailure { Toast.makeText(context, "공유할 수 없어요", Toast.LENGTH_SHORT).show() }
    }
    // 안드로이드 표준 공유. 이미지 항목은 실제 파일을, 그 외(유튜브/웹/영상)는 링크를 공유.
    fun shareItem(item: ListItem) {
        val isImg = item.link.isBlank() && item.photoUrls.isNotEmpty()
        if (!isImg) {
            shareText(listOf(item.text, item.link).filter { it.isNotBlank() }.joinToString("\n")); return
        }
        shareScope.launch {
            val uris = withContext(Dispatchers.IO) {
                item.photoUrls.mapIndexedNotNull { i, url ->
                    runCatching {
                        val ext = url.substringBefore('?').substringAfterLast('.', "jpg").take(4).ifBlank { "jpg" }
                        val f = java.io.File(context.cacheDir, "share_$i.$ext")
                        java.net.URL(url).openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
                        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                    }.getOrNull()
                }
            }
            if (uris.isEmpty()) { shareText(listOf(item.text).plus(item.photoUrls).filter { it.isNotBlank() }.joinToString("\n")); return@launch }
            val send = if (uris.size == 1)
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            send.type = "image/*"
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (item.text.isNotBlank()) send.putExtra(Intent.EXTRA_TEXT, item.text)
            runCatching { context.startActivity(Intent.createChooser(send, "공유")) }
                .onFailure { Toast.makeText(context, "공유할 수 없어요", Toast.LENGTH_SHORT).show() }
        }
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
                TogglePill("이미지", imageOn) { imageOn = !imageOn }
                TogglePill("등록순", oldestFirst) { oldestFirst = !oldestFirst }
                TogglePill("안 본", hideViewed) { hideViewed = !hideViewed }
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(shown, key = { it.id }) { post ->
                        FunCell(post, Modifier,
                            viewed = currentMemberId != null && post.viewedBy.contains(currentMemberId),
                            onClick = {
                                vm.markFunViewed(post)
                                when {
                                    isVideo(post.link) -> playUrl = post.link
                                    post.link.isBlank() && post.photoUrls.isNotEmpty() -> viewerImages = post.photoUrls
                                    else -> open(post.link)
                                }
                            },
                            onLongPress = { actionItem = post })
                    }
                }
            }
        }
    }

    // 이미지 전체보기. 한 장이면 바로 확대뷰, 여러 장이면 세로로 훑어보고 탭하면 확대뷰.
    viewerImages?.let { urls ->
        Dialog(onDismissRequest = { viewerImages = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            if (urls.size == 1) {
                ZoomOverlay(urls.first()) { viewerImages = null }
            } else {
                var zoomUrl by remember { mutableStateOf<String?>(null) }
                StackViewer(urls, onLongOpen = { zoomUrl = it }, onClose = { viewerImages = null })
                zoomUrl?.let { u -> ZoomOverlay(u) { zoomUrl = null } }
            }
        }
    }

    playUrl?.let { url -> VideoPlayerDialog(url) { playUrl = null } }

    if (showAdd) FunEditDialog(vm, null, onSave = { t, l, img -> vm.addFun(boardKey, t, l, if (img.isBlank()) emptyList() else listOf(img)); showAdd = false }, onDismiss = { showAdd = false })
    editItem?.let { it0 -> FunEditDialog(vm, it0, onSave = { t, l, img -> vm.updateFun(it0, t, l, img); editItem = null }, onDismiss = { editItem = null }) }
    actionItem?.let { it0 ->
        val editable = canEdit(it0)
        val toPrivate = boardKey != FunBoard.PRIVATE
        val transferLabel = if (toPrivate) "내 재미진 곳으로 전달" else "재미진 곳으로 전달"
        val transferTarget = if (toPrivate) FunBoard.PRIVATE else FunBoard.BOARD
        val transferMsg = if (toPrivate) "내 재미진 곳에 담았어요" else "재미진 곳에 담았어요"
        AlertDialog(
            onDismissRequest = { actionItem = null },
            title = { Text(it0.text.ifBlank { "게시물" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    Text("이 게시물을 어떻게 할까요?", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    TextButton(onClick = {
                        vm.copyFunTo(it0, transferTarget)
                        Toast.makeText(context, transferMsg, Toast.LENGTH_SHORT).show(); actionItem = null
                    }) { Text(transferLabel) }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { shareItem(it0); actionItem = null }) { Text("공유") }
                    if (editable) TextButton(onClick = { editItem = it0; actionItem = null }) { Text("수정") }
                }
            },
            dismissButton = {
                Row {
                    if (editable) TextButton(onClick = { vm.deleteItem(it0.id); actionItem = null }) { Text("삭제", color = Color(0xFFE03131)) }
                    TextButton(onClick = { actionItem = null }) { Text("취소") }
                }
            },
        )
    }
}

/** 여러 장 훑어보기: 세로로 이어 가로 꽉차게. 탭하면 확대뷰로. */
@Composable
private fun StackViewer(urls: List<String>, onLongOpen: (String) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Text("이미지를 길게 누르면 확대돼요", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            items(urls) { u ->
                // 스크롤 중 오터치 방지: 탭이 아니라 롱클릭으로 확대. 표시는 화면폭 수준으로 디코드(스크롤 부드럽게)
                val ctx = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(u).size(1280).build(),
                    contentDescription = null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().pointerInput(u) { detectTapGestures(onLongPress = { onLongOpen(u) }) },
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Default.Close, "닫기", tint = Color.White)
        }
    }
}

/**
 * 단일 이미지 확대뷰. 원본 해상도 로드.
 * - 기본(배율 1): 세로 스크롤(네이티브 플링)로 상하 빠르고 부드럽게 이동, 가로 꽉참.
 * - 두 손가락 핀치로 확대, 확대 중엔 한 손가락 드래그로 이동, 더블탭 토글.
 */
@Composable
private fun ZoomOverlay(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context).data(url).size(coil.size.Size.ORIGINAL).build(),
    )
    val scroll = rememberScrollState()
    var scale by remember { mutableStateOf(1f) }
    var tx by remember { mutableStateOf(0f) }
    var ty by remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = scale <= 1f)) {
            Image(
                painter = painter, contentDescription = null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
                    .pointerInput(url) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val e = awaitPointerEvent()
                                if (e.changes.size >= 2) {
                                    scale = (scale * e.calculateZoom()).coerceIn(1f, 5f)
                                    val p = e.calculatePan(); tx += p.x; ty += p.y
                                    e.changes.forEach { it.consume() }
                                } else if (scale > 1f) {
                                    val p = e.calculatePan(); tx += p.x; ty += p.y
                                    e.changes.forEach { it.consume() }
                                }
                                // 배율 1 + 한 손가락: 소비하지 않음 → 부모 verticalScroll이 플링 처리
                            } while (e.changes.any { it.pressed })
                            if (scale <= 1f) { tx = 0f; ty = 0f }
                        }
                    }
                    .pointerInput(url) {
                        detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; tx = 0f; ty = 0f })
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = tx; translationY = ty },
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Default.Close, "닫기", tint = Color.White)
        }
    }
}

@Composable
private fun VideoPlayerDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(url) { onDispose { player.release() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Default.Close, "닫기", tint = Color.White)
            }
        }
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
        val ctx = LocalContext.current
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFECECEC)).alpha(if (viewed) 0.35f else 1f),
            contentAlignment = Alignment.Center,
        ) {
            if (!photo.isNullOrBlank()) {
                // 썸네일은 작게 디코드(그리드 스크롤 부드럽게)
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(photo).size(400).build(),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
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

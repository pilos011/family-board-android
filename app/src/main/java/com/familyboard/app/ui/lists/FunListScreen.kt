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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.FunBoard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    val loadScope = rememberCoroutineScope()
    val isParent = currentMemberId == "seonil" || currentMemberId == "eunseon"
    fun canEdit(it: ListItem) = it.createdBy == currentMemberId || isParent

    var editItem by remember { mutableStateOf<ListItem?>(null) }
    var actionItem by remember { mutableStateOf<ListItem?>(null) }
    var pendingDelete by remember { mutableStateOf<ListItem?>(null) }
    var viewerImages by remember { mutableStateOf<List<String>?>(null) }
    var playUrl by remember { mutableStateOf<String?>(null) }
    var shareTarget by remember { mutableStateOf<ListItem?>(null) }        // 가족에게 공유할 항목
    var shareSel by remember { mutableStateOf<Set<String>>(emptySet()) }   // 공유 대상 선택(멤버 id)
    var youtubeOn by remember { mutableStateOf(true) }
    var websiteOn by remember { mutableStateOf(true) }
    var imageOn by remember { mutableStateOf(true) }
    var oldestFirst by remember { mutableStateOf(false) }
    var hideViewed by remember { mutableStateOf(false) }

    // 페이지 방식: 한 페이지 pageSize개. 최신순(기본)/등록순은 서버 정렬로 페이지 이동.
    val pageSize = 60
    val totalCount by vm.funCountFor(boardKey).collectAsStateWithLifecycle()

    // 현재 방향으로 위에서부터 순서대로 불러온 항목(연속). 방향 바뀌면 초기화.
    var loaded by remember(boardKey) { mutableStateOf<List<ListItem>>(emptyList()) }
    var pageIndex by remember(boardKey) { mutableStateOf(0) }
    var loading by remember(boardKey) { mutableStateOf(false) }
    var resumeDismissed by remember(boardKey) { mutableStateOf(false) }
    var entrySavedPage by remember(boardKey) { mutableStateOf(0) }

    // 표시 필터(유튜브/웹/이미지/안 본)가 하나라도 걸려 있으면 '필터 모드'.
    val filtering = !(youtubeOn && websiteOn && imageOn && !hideViewed)
    fun matches(it: ListItem): Boolean {
        val yt = isYoutube(it.link)
        val img = it.link.isBlank() && it.photoUrls.isNotEmpty()
        val web = !yt && !img
        return ((youtubeOn && yt) || (imageOn && img) || (websiteOn && web)) &&
            (!hideViewed || !it.viewedBy.contains(currentMemberId))
    }

    // 필요한 개수만큼 로드 보장(부족하면 1회 조회로 채움 → 이어보기 점프/전체 로드도 한 번에).
    // Mutex 로 직렬화 → 페이지 연타 시 동시 조회 경합(중복/누락) 방지.
    val loadMutex = remember(boardKey) { Mutex() }
    suspend fun ensureLoaded(target: Int) = loadMutex.withLock {
        if (loaded.size >= target) return@withLock
        if (totalCount in 1..loaded.size) return@withLock
        loading = true
        try {
            val after = loaded.lastOrNull()?.createdAt
            // 포함 커서(startAt)라 경계 1건이 다시 오므로 +1 요청해 순증가분을 맞춤
            val need = (target - loaded.size) + if (after != null) 1 else 0
            val batch = vm.fetchFunPage(boardKey, oldestFirst, after, need)
            val seen = loaded.mapTo(HashSet()) { it.id }
            loaded = loaded + batch.filter { seen.add(it.id) }
        } finally { loading = false }
    }

    // 필터 모드면 '전체 로드분을 필터한 목록'으로, 아니면 서버 페이징 목록(loaded)으로 페이징.
    val filtered = remember(loaded, filtering, youtubeOn, websiteOn, imageOn, hideViewed, currentMemberId) {
        if (filtering) loaded.filter(::matches) else loaded
    }
    // 페이지 수: 필터 모드는 '필터 결과' 기준(그래서 실제 보이는 만큼만), 비필터는 서버 전체 개수 기준.
    val totalPages = (if (filtering) (filtered.size + pageSize - 1) / pageSize
        else (totalCount + pageSize - 1) / pageSize).coerceAtLeast(1)

    fun goToPage(n: Int) {
        val clamped = n.coerceIn(0, totalPages - 1)
        pageIndex = clamped
        resumeDismissed = true
        vm.saveLastFunPage(boardKey, oldestFirst, clamped + 1)
        // 필터 모드면 전체가 있어야 정확히 페이징되므로 전체 로드, 아니면 해당 페이지까지만.
        loadScope.launch { ensureLoaded(if (filtering) totalCount else (clamped + 1) * pageSize) }
    }

    // 첫 진입 & 방향(등록순) 변경 시: 초기화 → 그 방향의 마지막 본 페이지 스냅샷 → 1페이지 로드
    LaunchedEffect(boardKey, oldestFirst) {
        loaded = emptyList(); pageIndex = 0; resumeDismissed = false
        entrySavedPage = vm.lastFunPage(boardKey, oldestFirst).first()
        ensureLoaded(pageSize)
    }
    // 필터가 켜지면 전체를 한 번에 불러와(한 쿼리) 필터 결과로 정확히 페이징.
    LaunchedEffect(filtering, totalCount) {
        if (filtering && totalCount > 0) ensureLoaded(totalCount)
    }
    // 총 페이지가 줄면(삭제·필터) 현재 페이지를 범위 안으로 보정
    LaunchedEffect(totalPages) {
        if (pageIndex > totalPages - 1) pageIndex = (totalPages - 1).coerceAtLeast(0)
    }

    // 현재 페이지 구간(필터 모드는 필터 결과에서, 비필터는 loaded 에서).
    val shown = remember(filtered, pageIndex) {
        val start = pageIndex * pageSize
        if (start < filtered.size) filtered.subList(start, minOf(filtered.size, start + pageSize)) else emptyList()
    }
    val showResume = entrySavedPage > 1 && entrySavedPage <= totalPages && pageIndex == 0 && !resumeDismissed

    fun open(link: String) {
        if (link.isBlank()) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            .onFailure { Toast.makeText(context, "링크를 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }

    // 공유받은 항목 열기(알림 탭): 재미진 곳 보드에서 id로 조회해 영상/이미지/링크로 자동 오픈.
    val pendingShared by vm.pendingSharedFun.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShared, boardKey) {
        val id = pendingShared ?: return@LaunchedEffect
        if (boardKey != FunBoard.BOARD) return@LaunchedEffect
        val item = vm.fetchItemById(id)
        vm.clearSharedFun()
        if (item != null) when {
            isVideo(item.link) -> playUrl = item.link
            item.link.isBlank() && item.photoUrls.isNotEmpty() -> viewerImages = item.photoUrls
            else -> open(item.link)
        }
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
        bottomBar = {
            FunPageBar(
                page = pageIndex + 1, totalPages = totalPages, loading = loading,
                onPrev = { if (pageIndex > 0) goToPage(pageIndex - 1) },
                onNext = { if (pageIndex < totalPages - 1) goToPage(pageIndex + 1) },
                onGoto = { p -> goToPage(p - 1) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 표시 필터를 바꾸면 현재 페이지가 비어 보일 수 있으니 1페이지로 리셋(등록순은 아래서 전체 리셋)
                TogglePill("유튜브", youtubeOn) { youtubeOn = !youtubeOn; pageIndex = 0 }
                TogglePill("웹사이트", websiteOn) { websiteOn = !websiteOn; pageIndex = 0 }
                TogglePill("이미지", imageOn) { imageOn = !imageOn; pageIndex = 0 }
                TogglePill("등록순", oldestFirst) { oldestFirst = !oldestFirst }
                TogglePill("안 본", hideViewed) { hideViewed = !hideViewed; pageIndex = 0 }
            }
            if (showResume) {
                ResumeBanner(
                    page = entrySavedPage,
                    onResume = { goToPage(entrySavedPage - 1) },
                    onDismiss = { resumeDismissed = true },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    loading && shown.isEmpty() -> CircularProgressIndicator()
                    totalCount == 0 -> Text(
                        if (boardKey == FunBoard.RECIPE)
                            "아직 레시피가 없어요.\n재미진 곳/내 재미진 곳에서\n'요리 레시피로 이동'으로 담아보세요."
                        else
                            "아직 게시물이 없어요.\n유튜브·웹·이미지를 '공유'로 담아보세요.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                    shown.isEmpty() -> Text(
                        "이 페이지에 표시할 항목이 없어요. (필터 확인)",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        gridItems(shown, key = { it.id }) { post ->
                            FunCell(post, Modifier,
                                viewed = currentMemberId != null && post.viewedBy.contains(currentMemberId),
                                onClick = {
                                    vm.markFunViewed(post)
                                    // 낙관적 갱신: 비실시간 페이지라 로컬 loaded 도 즉시 '봤음'으로(회색·"안 본" 필터 반영)
                                    if (currentMemberId != null && !post.viewedBy.contains(currentMemberId)) {
                                        loaded = loaded.map {
                                            if (it.id == post.id) it.copy(viewedBy = it.viewedBy + currentMemberId) else it
                                        }
                                    }
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

    editItem?.let { it0 -> FunEditDialog(vm, it0, onSave = { t, l, img ->
        vm.updateFun(it0, t, l, img)
        // 낙관적 갱신(비실시간): 로컬 loaded 도 즉시 반영. photoUrls 계산은 VM.updateFun 과 동일 규칙.
        val photos = when {
            img.isBlank() -> it0.photoUrls
            it0.photoUrls.size > 1 && it0.photoUrls.firstOrNull() == img -> it0.photoUrls
            else -> listOf(img)
        }
        loaded = loaded.map { if (it.id == it0.id) it.copy(text = t.trim(), link = l.trim(), photoUrls = photos) else it }
        editItem = null
    }, onDismiss = { editItem = null }) }
    actionItem?.let { it0 ->
        val editable = canEdit(it0)
        val isRecipe = boardKey == FunBoard.RECIPE
        val toPrivate = boardKey != FunBoard.PRIVATE
        val transferLabel = if (toPrivate) "내 재미진 곳으로 전달 (나만 보기)" else "재미진 곳으로 전달 (가족과 공유)"
        val transferTarget = if (toPrivate) FunBoard.PRIVATE else FunBoard.BOARD
        val transferMsg = if (toPrivate) "내 재미진 곳에 담았어요" else "재미진 곳에 담았어요"
        AlertDialog(
            onDismissRequest = { actionItem = null },
            title = { Text(it0.text.ifBlank { "게시물" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text("이 게시물을 어떻게 할까요?", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (isRecipe) {
                        // 요리법 → 재미진 곳으로 '이동'(빼내기)
                        TextButton(
                            onClick = {
                                vm.moveFunTo(it0, FunBoard.BOARD)
                                loaded = loaded.filterNot { it.id == it0.id } // 낙관적 제거
                                Toast.makeText(context, "재미진 곳으로 옮겼어요", Toast.LENGTH_SHORT).show(); actionItem = null
                            },
                            modifier = Modifier.fillMaxWidth().offset(y = (-10).dp),
                        ) { Text("재미진 곳으로 이동") }
                    } else {
                        // 다른 재미진 곳으로 '복사'(전달)
                        TextButton(
                            onClick = {
                                vm.copyFunTo(it0, transferTarget)
                                Toast.makeText(context, transferMsg, Toast.LENGTH_SHORT).show(); actionItem = null
                            },
                            modifier = Modifier.fillMaxWidth().offset(y = (-10).dp),
                        ) { Text(transferLabel) }
                        // 요리법으로 '이동'(복사 아님 — 원본이 여기서 빠짐)
                        TextButton(
                            onClick = {
                                vm.moveFunTo(it0, FunBoard.RECIPE)
                                loaded = loaded.filterNot { it.id == it0.id } // 낙관적 제거
                                Toast.makeText(context, "요리 레시피로 옮겼어요", Toast.LENGTH_SHORT).show(); actionItem = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("요리 레시피로 이동") }
                    }
                    // 가족에게 공유: 선택한 가족에게 알림 → 받는 사람이 탭하면 이 항목이 열림
                    TextButton(
                        onClick = { shareTarget = it0; actionItem = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("👨‍👩‍👧‍👦 가족에게 공유", color = MaterialTheme.colorScheme.primary) }
                    Spacer(Modifier.height(10.dp)) // 전달↔요리 레시피와 같은 간격
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        if (editable) TextButton(onClick = { pendingDelete = it0; actionItem = null }) { Text("삭제", color = Color(0xFFE03131)) }
                        TextButton(onClick = { actionItem = null }) { Text("취소") }
                        TextButton(onClick = { shareItem(it0); actionItem = null }) { Text("공유") }
                        if (editable) TextButton(onClick = { editItem = it0; actionItem = null }) { Text("수정") }
                    }
                }
            },
        )
    }
    // 가족에게 공유: 받을 가족 선택(나 제외, 복수 선택)
    shareTarget?.let { it0 ->
        val others = Family.members.filter { it.id != currentMemberId }
        AlertDialog(
            onDismissRequest = { shareTarget = null; shareSel = emptySet() },
            title = { Text("가족에게 공유") },
            text = {
                Column {
                    Text("받을 가족을 선택하세요 (여러 명 가능)", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.height(6.dp))
                    others.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable { shareSel = if (shareSel.contains(m.id)) shareSel - m.id else shareSel + m.id }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = shareSel.contains(m.id),
                                onCheckedChange = { on -> shareSel = if (on) shareSel + m.id else shareSel - m.id },
                            )
                            Text(m.name, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = shareSel.isNotEmpty(), onClick = {
                    vm.shareFunToFamily(it0, shareSel.toList())
                    Toast.makeText(context, "${shareSel.size}명에게 공유했어요", Toast.LENGTH_SHORT).show()
                    shareTarget = null; shareSel = emptySet()
                }) { Text("보내기") }
            },
            dismissButton = { TextButton(onClick = { shareTarget = null; shareSel = emptySet() }) { Text("취소") } },
        )
    }

    // 삭제 확인
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("삭제") },
            text = { Text("이 게시물을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteItem(target.id)
                    loaded = loaded.filterNot { it.id == target.id }  // 낙관적 갱신(비실시간)
                    pendingDelete = null
                }) {
                    Text("삭제", color = Color(0xFFE03131))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
}

/** 지난번 본 페이지로 바로 이동하는 배너. */
@Composable
private fun ResumeBanner(page: Int, onResume: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            buildAnnotatedString {
                append("지난번 ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${page}페이지") }
                append("까지 봤어요")
            },
            modifier = Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "이어보기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary)
                .clickable { onResume() }.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Spacer(Modifier.size(4.dp))
        Icon(
            Icons.Default.Close, "닫기", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable { onDismiss() }.padding(2.dp),
        )
    }
}

/** 현재 페이지 주변 + 처음/끝 번호. -1 은 생략(…) 표시. */
private fun pageWindow(cur: Int, total: Int): List<Int> {
    if (total <= 7) return (1..total).toList()
    val set = listOf(1, cur - 1, cur, cur + 1, total).filter { it in 1..total }.toSortedSet()
    val out = mutableListOf<Int>()
    var prev = 0
    for (p in set) {
        if (prev != 0 && p - prev > 1) out.add(-1)
        out.add(p); prev = p
    }
    return out
}

/** 하단 페이지 이동 바: ‹ 이전 · 번호(현재 강조) · 다음 › */
@Composable
private fun FunPageBar(
    page: Int, totalPages: Int, loading: Boolean,
    onPrev: () -> Unit, onNext: () -> Unit, onGoto: (Int) -> Unit,
) {
    Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrev, enabled = page > 1) {
                Icon(Icons.Default.ChevronLeft, "이전 페이지")
            }
            pageWindow(page, totalPages).forEach { p ->
                when (p) {
                    -1 -> Text(
                        "…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                    page -> Box(
                        Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary).padding(horizontal = 11.dp, vertical = 5.dp),
                    ) { Text("$p", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                    else -> Text(
                        "$p", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onGoto(p) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
            IconButton(onClick = onNext, enabled = page < totalPages) {
                Icon(Icons.Default.ChevronRight, "다음 페이지")
            }
        }
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
internal fun ZoomOverlay(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    // 파일로 받아 SubsamplingScaleImageView(부분 디코딩)로 표시 → 세로 2만px 같은 초대형도
    // 원본 해상도 유지하며 가로 꽉차게 세로 스크롤 + 핀치 확대(글자 읽기 가능).
    var file by remember(url) { mutableStateOf<java.io.File?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        file = null; failed = false
        val f = withContext(Dispatchers.IO) {
            runCatching {
                val ext = url.substringBefore('?').substringAfterLast('.', "jpg").take(5).ifBlank { "jpg" }
                val out = java.io.File(context.cacheDir, "view_${url.hashCode()}.$ext")
                if (!out.exists() || out.length() == 0L)
                    java.net.URL(url).openStream().use { i -> out.outputStream().use { i.copyTo(it) } }
                out
            }.getOrNull()
        }
        if (f != null) file = f else failed = true
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val f = file
        when {
            f != null -> AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView(ctx).apply {
                        setMinimumScaleType(com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.SCALE_TYPE_CUSTOM)
                        setPanLimit(com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
                        setDoubleTapZoomDuration(200)
                        setOnImageEventListener(object : com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.DefaultOnImageEventListener() {
                            override fun onReady() {
                                val vw = width.toFloat(); val iw = sWidth.toFloat()
                                if (vw > 0f && iw > 0f) {
                                    val fill = vw / iw            // 가로 꽉차게
                                    minScale = fill
                                    maxScale = maxOf(fill * 4f, 3f) // 원본 이상으로 확대 허용(글자 읽기)
                                    setDoubleTapZoomScale(maxOf(fill * 2f, 1f))
                                    setScaleAndCenter(fill, android.graphics.PointF(iw / 2f, 0f)) // 위(시작)부터
                                }
                            }
                        })
                        setImage(com.davemorrissey.labs.subscaleview.ImageSource.uri(android.net.Uri.fromFile(f)))
                    }
                },
            )
            failed -> Text("이미지를 열 수 없어요", color = Color.White, modifier = Modifier.align(Alignment.Center))
            else -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
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

/** 우리 서버(/photos/)에 올린 이미지는 그리드에서 500px 썸네일(/thumb/)로 빠르게. 외부 URL은 그대로. */
fun funThumbUrl(url: String): String =
    if (url.contains("/photos/")) url.replaceFirst("/photos/", "/thumb/") else url

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
                // 그리드는 서버 500px 썸네일 사용(첫 로드 빠르고 로컬 캐시에 오래 남음)
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(funThumbUrl(photo)).size(400).build(),
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
    // 게시물 유형: 이미지(링크 없음+사진) vs 링크/영상. remember 는 항목별로 초기화(키=id).
    val isImagePost = item != null && item.link.isBlank() && item.photoUrls.isNotEmpty()
    var title by remember(item?.id) { mutableStateOf(item?.text ?: "") }
    var link by remember(item?.id) { mutableStateOf(item?.link ?: "") }
    var image by remember(item?.id) { mutableStateOf(item?.photoUrls?.firstOrNull() ?: "") }
    var fetching by remember(item?.id) { mutableStateOf(false) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "게시물 추가" else "게시물 수정") },
        text = {
            Column {
                if (isImagePost) {
                    // 이미지 게시물: 미리보기 + 제목만(링크 없음)
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(funThumbUrl(item!!.photoUrls.first())).size(600).build(),
                        contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    if (item.photoUrls.size > 1) {
                        Spacer(Modifier.height(4.dp))
                        Text("이미지 ${item.photoUrls.size}장", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = image, onValueChange = { image = it }, label = { Text("이미지 주소") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("제목") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                } else {
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
            }
        },
        confirmButton = {
            TextButton(enabled = link.isNotBlank() || image.isNotBlank(),
                onClick = { onSave(title.trim().ifBlank { if (isImagePost) "이미지" else "링크" }, link.trim(), image) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

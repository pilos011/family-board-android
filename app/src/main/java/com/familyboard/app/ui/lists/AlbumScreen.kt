package com.familyboard.app.ui.lists

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.AlbumBoard
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun albumThumb(u: String): String = if (u.contains("/photos/")) u.replace("/photos/", "/thumb/") else u

/** 갤러리 이미지 1건: URI + 촬영/추가 시각(millis, 최근순·빠른스크롤 년월 표시용). */
private data class GalleryImg(val uri: android.net.Uri, val millis: Long)

/** 갤러리 이미지를 최근순으로 조회. 기본은 '카메라' 앨범(DCIM/Camera)만. 비면 전체로 폴백. */
private fun queryGalleryImages(cr: android.content.ContentResolver): List<GalleryImg> {
    val cam = if (Build.VERSION.SDK_INT >= 29)
        queryImages(cr, "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("%DCIM/Camera%"))
    else emptyList()
    return if (cam.isNotEmpty()) cam else queryImages(cr, null, null)
}

private fun queryImages(
    cr: android.content.ContentResolver,
    selection: String?,
    args: Array<String>?,
): List<GalleryImg> {
    val idCol = MediaStore.Images.Media._ID
    val takenCol = MediaStore.Images.Media.DATE_TAKEN
    val addedCol = MediaStore.Images.Media.DATE_ADDED
    val sort = "$takenCol DESC, $addedCol DESC"
    val out = ArrayList<GalleryImg>()
    runCatching {
        cr.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(idCol, takenCol, addedCol), selection, args, sort)?.use { c ->
            val ii = c.getColumnIndexOrThrow(idCol)
            val ti = c.getColumnIndex(takenCol)
            val ai = c.getColumnIndex(addedCol)
            while (c.moveToNext()) {
                val taken = if (ti >= 0 && !c.isNull(ti)) c.getLong(ti) else 0L
                val added = if (ai >= 0 && !c.isNull(ai)) c.getLong(ai) * 1000 else 0L
                out.add(
                    GalleryImg(
                        android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(ii)),
                        if (taken > 0) taken else added,
                    ),
                )
            }
        }
    }
    return out
}

/** millis → "yyyy년 M월". 0이면 빈 문자열. */
private fun ymLabel(millis: Long): String = if (millis <= 0) "" else runCatching {
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).let { "${it.year}년 ${it.monthValue}월" }
}.getOrDefault("")

/** 사진첩 그리드 평면 항목(월 헤더 + 사진). 빠른 스크롤 인덱스↔년월 매핑용. key="yyyy-MM"(태그·검색·이동용). */
private sealed interface AlbumEntry {
    data class Header(val month: String, val key: String) : AlbumEntry
    data class Photo(val item: ListItem) : AlbumEntry
}

private fun entryMonth(e: AlbumEntry?): String = when (e) {
    is AlbumEntry.Header -> e.month
    is AlbumEntry.Photo -> monthLabelOf(e.item)
    null -> ""
}

/** 정렬된 사진 목록 → [월 헤더 + 사진] 평면 리스트(월 desc). 헤더 key="yyyy-MM". */
private fun buildAlbumFlat(sorted: List<ListItem>): List<AlbumEntry> = buildList {
    sorted.groupBy { monthKeyOf(it) }.forEach { (key, photos) ->
        add(AlbumEntry.Header(photos.firstOrNull()?.let { monthLabelOf(it) } ?: monthLabelFromKey(key), key))
        photos.forEach { add(AlbumEntry.Photo(it)) }
    }
}

/** 촬영 시각 → "yyyy-MM"(월 태그·그룹 키). */
private fun monthKeyOf(it: ListItem): String = runCatching {
    java.time.Instant.ofEpochMilli(capMillis(it)).atZone(java.time.ZoneId.systemDefault())
        .let { d -> String.format("%04d-%02d", d.year, d.monthValue) }
}.getOrDefault("")

/** "yyyy-MM" → "yyyy년 M월". */
private fun monthLabelFromKey(key: String): String = runCatching {
    val (y, m) = key.split("-"); "${y.toInt()}년 ${m.toInt()}월"
}.getOrDefault(key)

/** Coil이 디코딩한(작은) 비트맵을 지정 각도로 회전 — 네트워크 없이 즉시. 원본 미변경. */
private class RotateTransformation(private val degrees: Int) : Transformation {
    override val cacheKey: String = "rotate_$degrees"
    override suspend fun transform(input: android.graphics.Bitmap, size: Size): android.graphics.Bitmap {
        if (degrees % 360 == 0) return input
        val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return android.graphics.Bitmap.createBitmap(input, 0, 0, input.width, input.height, m, true)
    }
}

/**
 * 공유·다운로드용 회전 적용. degrees=사용자 수동 회전.
 * 수동 회전이 없으면 원본 그대로(뷰어가 EXIF 방향을 반영 → 화면과 동일).
 * 수동 회전이 있으면 원본 EXIF 방향 + 수동 각도를 합산해 픽셀에 굽고 EXIF는 제거.
 */
private fun applyRotation(bytes: ByteArray, degrees: Int, png: Boolean): ByteArray {
    if (degrees % 360 == 0) return bytes
    val exifDeg = runCatching {
        androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).rotationDegrees
    }.getOrDefault(0)
    val total = ((exifDeg + degrees) % 360 + 360) % 360
    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val m = android.graphics.Matrix().apply { postRotate(total.toFloat()) }
    val r = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    val out = java.io.ByteArrayOutputStream()
    if (png) r.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    else r.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
    return out.toByteArray()
}

/** 원본 다운로드 + 회전 적용 → (bytes, 확장자). 공유·다운로드 공용(단일·일괄). 실패 시 null. */
private suspend fun albumBytesAndExt(item: ListItem): Pair<ByteArray, String>? = withContext(Dispatchers.IO) {
    val url = item.photoUrls.firstOrNull() ?: return@withContext null
    runCatching {
        val srcExt = url.substringBefore('?').substringAfterLast('.', "jpg").take(4).ifBlank { "jpg" }
        val png = srcExt.equals("png", true)
        val raw = java.net.URL(url).openStream().use { it.readBytes() }
        val bytes = applyRotation(raw, item.rotation, png)
        val ext = if (item.rotation % 360 != 0 && !png) "jpg" else srcExt
        bytes to ext
    }.getOrNull()
}

/** 공유용: 원본을 캐시에 써서 FileProvider uri 반환. cacheBase는 고유해야 함(다중 공유 시 파일 겹침 방지). */
private suspend fun albumShareUri(context: android.content.Context, item: ListItem, cacheBase: String): android.net.Uri? {
    val (bytes, ext) = albumBytesAndExt(item) ?: return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val f = java.io.File(context.cacheDir, "$cacheBase.$ext")
            f.outputStream().use { it.write(bytes) }
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        }.getOrNull()
    }
}

/** 다운로드: 원본을 공용 Download 폴더에 저장. 성공 여부 반환. (VM 일괄 다운로드에서도 재사용) */
internal suspend fun saveAlbumToDownloads(context: android.content.Context, item: ListItem): Boolean {
    val (bytes, ext) = albumBytesAndExt(item) ?: return false
    return withContext(Dispatchers.IO) {
        runCatching {
            val name = "가족사진_${System.currentTimeMillis()}_${(item.photoUrls.firstOrNull()?.hashCode() ?: 0) and 0xffff}.$ext"
            val mime = when (ext.lowercase()) {
                "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; "heic", "heif" -> "image/heic"
                else -> "image/jpeg"
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val cr = context.contentResolver
                val cv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return@runCatching false
                cr.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching false
                cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0)
                cr.update(uri, cv, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val f = java.io.File(dir, name)
                f.outputStream().use { it.write(bytes) }
                android.media.MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), arrayOf(mime), null)
                true
            }
        }.getOrDefault(false)
    }
}

/** 촬영 시각 기준 millis(EXIF takenAt 우선 → dateIso 자정 → createdAt). 월 구분·정렬 기준. */
private fun capMillis(it: ListItem): Long = when {
    it.takenAt > 0 -> it.takenAt
    it.dateIso.isNotBlank() -> runCatching {
        java.time.LocalDate.parse(it.dateIso).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrDefault(it.createdAt)
    else -> it.createdAt
}

private fun monthLabelOf(it: ListItem): String = runCatching {
    java.time.Instant.ofEpochMilli(capMillis(it)).atZone(java.time.ZoneId.systemDefault())
        .let { d -> "${d.year}년 ${d.monthValue}월" }
}.getOrElse { "날짜 미상" }

/** 썸네일 하단 표기: 촬영 시각(있으면 날짜+시간, 없으면 날짜만). */
private fun dateTimeLabelOf(it: ListItem): String = runCatching {
    val z = java.time.Instant.ofEpochMilli(capMillis(it)).atZone(java.time.ZoneId.systemDefault())
    if (it.takenAt > 0) String.format("%d.%d.%d %02d:%02d", z.year, z.monthValue, z.dayOfMonth, z.hour, z.minute)
    else String.format("%d.%d.%d", z.year, z.monthValue, z.dayOfMonth)
}.getOrDefault("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(vm: AppViewModel, isPrivate: Boolean = false, onBack: () -> Unit) {
    val albumBoard = if (isPrivate) AlbumBoard.PRIVATE else AlbumBoard.BOARD
    val tagBoard = if (isPrivate) AlbumBoard.TAG_BOARD_PRIVATE else AlbumBoard.TAG_BOARD
    val screenTitle = if (isPrivate) AlbumBoard.TITLE_PRIVATE else AlbumBoard.TITLE
    // 이동/복사 대상(반대편 앨범)
    val otherAlbumBoard = if (isPrivate) AlbumBoard.BOARD else AlbumBoard.PRIVATE
    val otherAlbumName = if (isPrivate) AlbumBoard.TITLE else AlbumBoard.TITLE_PRIVATE
    val items by (if (isPrivate) vm.myAlbumItems else vm.albumItems).collectAsStateWithLifecycle()
    val me by vm.currentMemberId.collectAsStateWithLifecycle()
    // 진입 시 사진첩 로드(Firestore 리스너 대신 REST 페이지 누적). 화면 열 때/private 전환 시 새로고침.
    // me 도 키로 둬서, 멤버 ID가 늦게 로드돼도 내 사진첩이 빈 채로 남지 않고 다시 조회된다.
    LaunchedEffect(isPrivate, me) { vm.refreshAlbum(if (isPrivate) AlbumBoard.PRIVATE else AlbumBoard.BOARD) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<ListItem?>(null) }
    // 홈 '그날의 추억' 사진 탭 → 가족 앨범에서 그 사진 뷰어 자동 열기(로드되면 열고 대기 해제).
    if (!isPrivate) {
        val pendingPhoto by vm.pendingOpenAlbumPhoto.collectAsStateWithLifecycle()
        LaunchedEffect(pendingPhoto, items) {
            val id = pendingPhoto ?: return@LaunchedEffect
            if (items?.any { it.id == id } == true) { selectedId = id; vm.clearOpenAlbumPhoto() }
            // 아직 로드 안 됐으면 items 갱신(페이지 누적)마다 재실행되어 열린다.
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 월 태그(강조·검색) + 검색/편집 상태 + 그리드(스크롤 이동용)를 상단에서 관리.
    val albumTags by (if (isPrivate) vm.myAlbumTags else vm.albumTags).collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var editTagKey by remember { mutableStateOf<String?>(null) }
    val sortedAll = remember(items) { (items ?: emptyList()).sortedByDescending { capMillis(it) } }
    val flat = remember(sortedAll) { buildAlbumFlat(sortedAll) }
    val albumGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    // 앱 자체 사진 그리드(최근순·스크롤 위치 기억·다중선택). 외부 갤러리/문서앱은 열 때마다 위치가
    // 초기화되고 앨범을 다시 골라야 해서, 앱 안에서 직접 그리드로 고르게 한다.
    var showPicker by remember { mutableStateOf(false) }
    val pickerGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val readPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showPicker = true
        else Toast.makeText(context, "사진 접근 권한이 필요해요", Toast.LENGTH_SHORT).show()
    }
    fun pick() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, readPerm) == PackageManager.PERMISSION_GRANTED) showPicker = true
        else permLauncher.launch(readPerm)
    }

    // 다중 선택 모드 상태.
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    fun exitSelection() { selectionMode = false; selectedIds.clear() }
    fun toggleSelect(id: String) { if (!selectedIds.remove(id)) selectedIds.add(id) }

    // 단일 공유(원본). 캐시 파일명은 고유하게(연속 공유 시 이전 파일과 안 겹치게).
    fun sharePhoto(item: ListItem) {
        scope.launch {
            val uri = albumShareUri(context, item, "album_share_${System.currentTimeMillis()}")
            if (uri == null) { Toast.makeText(context, "공유할 수 없어요", Toast.LENGTH_SHORT).show(); return@launch }
            val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri).setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(send, "사진 공유")) }
                .onFailure { Toast.makeText(context, "공유할 수 없어요", Toast.LENGTH_SHORT).show() }
        }
    }

    // 단일 다운로드(원본 → 공용 Download).
    fun downloadPhoto(item: ListItem) {
        scope.launch {
            val ok = saveAlbumToDownloads(context, item)
            Toast.makeText(context, if (ok) "Download 폴더에 저장했어요" else "저장 실패", Toast.LENGTH_SHORT).show()
        }
    }

    // 여러 장 공유(원본, ACTION_SEND_MULTIPLE).
    fun shareSelected(list: List<ListItem>) {
        if (list.isEmpty()) return
        scope.launch {
            Toast.makeText(context, "${list.size}장 준비 중…", Toast.LENGTH_SHORT).show()
            val uris = list.mapIndexedNotNull { i, it -> albumShareUri(context, it, "album_share_$i") }
            if (uris.isEmpty()) { Toast.makeText(context, "공유할 수 없어요", Toast.LENGTH_SHORT).show(); return@launch }
            val send = if (uris.size == 1)
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            send.type = "image/*"; send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(send, "${uris.size}장 공유")) }
                .onFailure { Toast.makeText(context, "공유할 수 없어요", Toast.LENGTH_SHORT).show() }
            exitSelection()
        }
    }

    // 여러 장 다운로드: 화면을 나가도 끝까지 저장되도록 viewModelScope(VM)에서 수행.
    fun downloadSelected(list: List<ListItem>) {
        if (list.isEmpty()) return
        vm.downloadAlbumOriginals(list)
        exitSelection()
    }

    androidx.activity.compose.BackHandler(enabled = selectionMode) { exitSelection() }

    Column(Modifier.fillMaxSize()) {
        if (selectionMode) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { exitSelection() }) { Icon(Icons.Default.Close, "선택 취소") }
                Text("${selectedIds.size}장 선택", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                val allSelected = sortedAll.isNotEmpty() && selectedIds.size >= sortedAll.size
                TextButton(onClick = {
                    if (allSelected) selectedIds.clear()
                    else { selectedIds.clear(); selectedIds.addAll(sortedAll.map { it.id }) }
                }) { Text(if (allSelected) "전체 해제" else "전체 선택") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                Text(screenTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { selectionMode = true }) { Icon(Icons.Default.Checklist, "선택") }
                IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, "태그 검색") }
                IconButton(onClick = { pick() }) { Icon(Icons.Default.AddAPhoto, "사진 추가") }
            }
        }

        Box(Modifier.weight(1f)) {
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 사진이 없어요", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { pick() }) { Text("+ 가족 사진 올리기") }
                }
            }
            else -> {
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3), state = albumGridState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            flat.size,
                            key = { i -> when (val e = flat[i]) { is AlbumEntry.Header -> "h:${e.key}"; is AlbumEntry.Photo -> e.item.id } },
                            span = { i -> if (flat[i] is AlbumEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                        ) { i ->
                            when (val e = flat[i]) {
                                is AlbumEntry.Header -> AlbumMonthHeader(
                                    label = e.month,
                                    tag = albumTags[e.key].orEmpty(),
                                    onEdit = { editTagKey = e.key },
                                )
                                is AlbumEntry.Photo -> AlbumTile(
                                    item = e.item,
                                    canDelete = e.item.createdBy == me || me == AlbumBoard.ADMIN, // 업로더 또는 선일
                                    otherAlbumName = otherAlbumName,
                                    selectionMode = selectionMode,
                                    selected = e.item.id in selectedIds,
                                    onToggleSelect = { toggleSelect(e.item.id) },
                                    onOpen = { selectedId = e.item.id },
                                    onRotate = { vm.rotateAlbumPhoto(e.item, 270) }, // 270=반시계(왼쪽으로)
                                    onShare = { sharePhoto(e.item) },
                                    onMove = { vm.moveAlbumPhoto(e.item, otherAlbumBoard) },
                                    onCopy = { vm.copyAlbumPhoto(e.item, otherAlbumBoard) },
                                    onDownload = { downloadPhoto(e.item) },
                                    onDelete = { confirmDelete = e.item },
                                )
                            }
                        }
                    }
                    FastScroller(albumGridState) { i -> entryMonth(flat.getOrNull(i)) }
                }
            }
        }
        } // Box(weight)
        // 선택 모드 하단 액션바(다운로드·공유·삭제)
        if (selectionMode) {
            val selItems = sortedAll.filter { it.id in selectedIds }
            AlbumSelectionBar(
                count = selItems.size,
                onDownload = { downloadSelected(selItems) },
                onShare = { shareSelected(selItems) },
                onDelete = {
                    val canDel = selItems.filter { it.createdBy == me || (!isPrivate && me == AlbumBoard.ADMIN) }
                    when {
                        selItems.isEmpty() -> {}
                        canDel.isEmpty() -> Toast.makeText(context, "삭제 권한이 있는 사진이 없어요", Toast.LENGTH_SHORT).show()
                        else -> confirmDeleteSelected = true
                    }
                },
            )
        }
    }

    val curIndex = sortedAll.indexOfFirst { it.id == selectedId }
    // 뷰어 도중 사진이 사라지면(본인·타인 삭제 등) 선택 해제. 컴포지션 중 상태 쓰기 대신 이펙트로.
    LaunchedEffect(selectedId, curIndex) {
        if (selectedId != null && curIndex < 0) selectedId = null
    }
    if (selectedId != null && curIndex >= 0) {
        AlbumViewer(
            photos = sortedAll, startIndex = curIndex, me = me, vm = vm,
            onShare = { sharePhoto(it) }, onDownload = { downloadPhoto(it) },
            onClose = { selectedId = null },
        )
    }

    if (showPicker) {
        InAppPhotoPicker(
            gridState = pickerGridState,
            onConfirm = { uris -> if (uris.isNotEmpty()) vm.addAlbumPhotos(uris, albumBoard); showPicker = false },
            onClose = { showPicker = false },
        )
    }

    // 월 태그 편집(월 헤더 탭/롱클릭). 가족 모두 입력·수정.
    editTagKey?.let { key ->
        AlbumTagDialog(
            monthLabel = monthLabelFromKey(key),
            initial = albumTags[key].orEmpty(),
            onSave = { vm.setAlbumTag(key, it, tagBoard); editTagKey = null },
            onDismiss = { editTagKey = null },
        )
    }

    // 태그 검색 → 해당 년월로 이동.
    if (showSearch) {
        val navigableKeys = remember(flat) { flat.filterIsInstance<AlbumEntry.Header>().map { it.key }.toSet() }
        AlbumSearchDialog(
            tags = albumTags,
            navigableKeys = navigableKeys,
            onPick = { key ->
                val idx = flat.indexOfFirst { it is AlbumEntry.Header && it.key == key }
                if (idx >= 0) scope.launch { albumGridState.scrollToItem(idx) }
                showSearch = false
            },
            onDismiss = { showSearch = false },
        )
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("사진 삭제") },
            text = { Text("이 사진을 삭제할까요? 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteAlbumPhoto(target); confirmDelete = null }) {
                    Text("삭제", color = Color(0xFFE03131))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("취소") } },
        )
    }

    // 선택한 여러 장 삭제(권한 있는 것만).
    if (confirmDeleteSelected) {
        val selItems = sortedAll.filter { it.id in selectedIds }
        val deletable = selItems.filter { it.createdBy == me || (!isPrivate && me == AlbumBoard.ADMIN) }
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text("사진 삭제") },
            text = {
                Text(
                    if (deletable.size < selItems.size)
                        "선택한 ${selItems.size}장 중 삭제 권한이 있는 ${deletable.size}장을 삭제할까요? 되돌릴 수 없어요."
                    else "선택한 ${selItems.size}장을 삭제할까요? 되돌릴 수 없어요.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteSelected = false
                    val skipped = selItems.size - deletable.size
                    deletable.forEach { vm.deleteAlbumPhoto(it) }
                    Toast.makeText(
                        context,
                        if (skipped > 0) "${deletable.size}장 삭제 · 권한 없는 ${skipped}장은 제외했어요"
                        else "${deletable.size}장 삭제했어요",
                        Toast.LENGTH_SHORT,
                    ).show()
                    exitSelection()
                }) { Text("삭제", color = Color(0xFFE03131)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteSelected = false }) { Text("취소") } },
        )
    }
}

private val AlbumAccent = Color(0xFFE8590C)

/** 선택 모드 하단 액션바: 다운로드·공유·삭제. */
@Composable
private fun AlbumSelectionBar(count: Int, onDownload: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    androidx.compose.material3.Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelBarAction(Icons.Default.Download, "다운로드", count > 0, onDownload)
            SelBarAction(Icons.Default.Share, "공유", count > 0, onShare)
            SelBarAction(Icons.Default.Delete, "삭제", count > 0, onDelete)
        }
    }
}

@Composable
private fun RowScope.SelBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = tint)
    }
}

/** 월 대표 타이틀(옵션 A): 주황 강조 바 + 큰 월 + 태그(핀). 탭/롱클릭 시 태그 편집. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumMonthHeader(label: String, tag: String, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp).height(IntrinsicSize.Min)
            .combinedClickable(onClick = onEdit, onLongClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().heightIn(min = 22.dp).clip(RoundedCornerShape(2.dp)).background(AlbumAccent))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (tag.isNotBlank()) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, tint = AlbumAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tag, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("＋ 태그 달기", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** 월 태그 편집 다이얼로그(가족 모두 입력·수정). 빈 값으로 저장하면 태그 제거. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumTagDialog(monthLabel: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Sell, null, tint = AlbumAccent) },
        title = { Text("월 태그 편집") },
        text = {
            Column {
                Text(monthLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("예: 오사카 가족여행, 퍼스트가든") },
                    singleLine = false, maxLines = 3, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Group, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.width(5.dp))
                    Text("가족 모두 입력·수정할 수 있어요", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("저장", color = AlbumAccent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 태그 검색 다이얼로그: 태그/년월로 검색 → 결과 클릭 시 그 년월로 이동. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSearchDialog(
    tags: Map<String, String>,
    navigableKeys: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val results = remember(q, tags, navigableKeys) {
        val query = q.trim()
        tags.entries
            .filter { it.key in navigableKeys }
            .filter { query.isBlank() || it.value.contains(query, ignoreCase = true) || monthLabelFromKey(it.key).contains(query) }
            .sortedByDescending { it.key }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp).widthIn(max = 340.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = AlbumAccent)
                    Spacer(Modifier.width(7.dp))
                    Text("태그 검색", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "닫기") }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = q, onValueChange = { q = it },
                    placeholder = { Text("예: 오사카, 여행, 2026") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (results.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                        Text(
                            if (tags.isEmpty()) "아직 등록된 태그가 없어요\n월 타이틀을 눌러 태그를 달아보세요" else "검색 결과가 없어요",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                } else {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        results.forEach { (key, tag) ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .clickable { onPick(key) }.padding(vertical = 10.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(AlbumAccent))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(monthLabelFromKey(key), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(tag, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 앱 자체 사진 선택 그리드: 최근순, 다중선택(순번 표시), 스크롤 위치는 상위 gridState로 유지된다. */
@Composable
private fun InAppPhotoPicker(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onConfirm: (List<android.net.Uri>) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var images by remember { mutableStateOf<List<GalleryImg>?>(null) }
    val selected = remember { mutableStateListOf<android.net.Uri>() }
    LaunchedEffect(Unit) {
        images = withContext(Dispatchers.IO) { queryGalleryImages(context.contentResolver) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "닫기") }
                    Text("사진 선택", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                        Text(if (selected.isEmpty()) "추가" else "추가 (${selected.size})")
                    }
                }
                val list = images
                when {
                    list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("기기에 사진이 없어요") }
                    else -> Box(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3), state = gridState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(list, key = { it.uri.toString() }) { img ->
                                val idx = selected.indexOf(img.uri)
                                val sel = idx >= 0
                                Box(
                                    Modifier.aspectRatio(1f).background(Color(0xFFF1F3F5))
                                        .clickable { if (sel) selected.remove(img.uri) else selected.add(img.uri) },
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(img.uri).size(300).build(),
                                        contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                    )
                                    if (sel) {
                                        Box(Modifier.fillMaxSize().background(Color(0x553B82F6)))
                                        Box(
                                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape)
                                                .background(Color(0xFF3B82F6)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("${idx + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        FastScroller(gridState) { i -> ymLabel(list.getOrNull(i)?.millis ?: 0L) }
                    }
                }
            }
        }
    }
}

private data class FsMetrics(
    val scrollFrac: Float, // 0..1 스크롤 진행률(맨 아래에서 정확히 1)
    val thumbFrac: Float,  // 트랙 대비 썸 크기(내용 많을수록 작게)
    val maxIndex: Int,     // 스크롤 가능한 최대 '첫 보이는 항목' 인덱스(= 총항목 − 화면당 항목수)
    val total: Int,
    val firstVisible: Int,
)

/**
 * 빠른 스크롤(사진첩·선택기 공용). ⚠️ v1.0.128: firstVisibleItemIndex/count 근사(점진 로드로 부정확)
 * 대신 **그리드 layoutInfo** 로 계산 — 총항목수는 매 프레임 라이브(로드되며 늘어도 정확), 진행률은
 * (첫보이는인덱스+항목내부오프셋)/(총항목−화면당항목수) 라 **콘텐츠 실제 맨 아래에서 정확히 1**,
 * 썸 크기는 화면비율에 비례(스마트). 썸 잡고 상대 이동, 썸 밖 누르면 무시(뒤 사진 탭 유지).
 */
@Composable
private fun BoxScope.FastScroller(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    labelAt: (Int) -> String,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var trackH by remember { mutableFloatStateOf(0f) }
    var dragThumbTop by remember { mutableFloatStateOf(0f) } // 드래그 중 썸 상단 위치(px)
    val minThumbPx = with(density) { 48.dp.toPx() }

    val metrics by remember(gridState) {
        derivedStateOf {
            val li = gridState.layoutInfo
            val total = li.totalItemsCount
            val vis = li.visibleItemsInfo
            if (total <= 0 || vis.isEmpty()) FsMetrics(0f, 1f, 1, total, 0)
            else {
                val first = vis.first()
                val firstSize = first.size.height.coerceAtLeast(1)
                val intra = (gridState.firstVisibleItemScrollOffset.toFloat() / firstSize).coerceIn(0f, 1f)
                // 같은 줄 항목수(사진줄=열수, 헤더=1)로 줄 내부 진행을 보정 → 줄마다 끊기지 않고 부드럽게.
                val perRow = vis.count { it.row == first.row }.coerceAtLeast(1)
                val scrolled = gridState.firstVisibleItemIndex + intra * perRow
                val maxIndex = (total - vis.size).coerceAtLeast(1)
                FsMetrics(
                    scrollFrac = (scrolled / maxIndex).coerceIn(0f, 1f),
                    thumbFrac = (vis.size.toFloat() / total).coerceIn(0.06f, 1f),
                    maxIndex = maxIndex, total = total, firstVisible = gridState.firstVisibleItemIndex,
                )
            }
        }
    }

    if (metrics.total < 30) return // 짧으면 스크롤바 숨김

    val thumbHpx = (trackH * metrics.thumbFrac).coerceIn(minThumbPx, trackH.coerceAtLeast(minThumbPx))
    val maxOffset = (trackH - thumbHpx).coerceAtLeast(0f)
    val thumbY = (if (dragging) dragThumbTop else metrics.scrollFrac * maxOffset).coerceIn(0f, maxOffset)
    val thumbHdp = with(density) { thumbHpx.toDp() }
    val labelIdx = (if (dragging && maxOffset > 0f) ((dragThumbTop / maxOffset) * metrics.maxIndex).roundToInt() else metrics.firstVisible)
        .coerceIn(0, metrics.total - 1)
    val label = labelAt(labelIdx)

    Box(
        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(40.dp)
            .onSizeChanged { trackH = it.height.toFloat() }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                val grabTol = with(density) { 24.dp.toPx() } // 썸 위아래 여유(잡기 쉽게)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val th = (trackH * metrics.thumbFrac).coerceIn(minThumbPx, trackH.coerceAtLeast(minThumbPx))
                    val mo = (trackH - th).coerceAtLeast(0f)
                    val curTop = metrics.scrollFrac * mo // 눌린 순간의 썸 위치
                    if (down.position.y < curTop - grabTol || down.position.y > curTop + th + grabTol) return@awaitEachGesture
                    val grabOffset = (down.position.y - curTop).coerceIn(0f, th) // 썸 안에서 잡은 지점
                    var started = false
                    try {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            if (!started && kotlin.math.abs(ch.position.y - down.position.y) > slop) { started = true; dragging = true }
                            if (started) {
                                dragThumbTop = (ch.position.y - grabOffset).coerceIn(0f, mo) // 잡은 지점 유지=튐 없음
                                val frac = if (mo > 0f) dragThumbTop / mo else 0f
                                val target = (frac * metrics.maxIndex).roundToInt().coerceIn(0, metrics.total - 1)
                                scope.launch { gridState.scrollToItem(target) }
                                ch.consume()
                            }
                        }
                    } finally {
                        dragging = false // 취소·재구성으로 드래그가 멈춰도 상태 고착 방지
                    }
                }
            },
    ) {
        // 현재 위치 막대(썸) — 내용량 비례 크기, 원래의 반투명 파랑(잡으면 진하게).
        Box(
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, thumbY.roundToInt()) }
                .padding(end = 5.dp).size(width = 14.dp, height = thumbHdp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (dragging) Color(0xFF3B82F6) else Color(0x993B82F6)),
        )
    }
    // 년월 버블 — 부모 Box(전체 폭)에 그려 40dp 띠 폭에 잘리지 않게 함. 핸들 왼쪽에 표시.
    if (dragging && label.isNotBlank()) {
        Text(
            label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1,
            modifier = Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbY.roundToInt()) }
                .padding(end = 50.dp)
                .background(Color(0xE6000000), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumTile(
    item: ListItem,
    canDelete: Boolean,
    otherAlbumName: String,     // 이동/복사 대상 앨범 이름("내 사진첩" 또는 "가족 사진첩")
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
    onRotate: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val url = item.photoUrls.firstOrNull().orEmpty()
    var menu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Column {
        Box {
            Box(
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1F3F5))
                    .combinedClickable(
                        onClick = { if (selectionMode) onToggleSelect() else onOpen() },
                        onLongClick = { if (selectionMode) onToggleSelect() else menu = true },
                    ),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(albumThumb(url)).size(400).crossfade(true)
                        .transformations(RotateTransformation(item.rotation)).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
                if (selectionMode) {
                    if (selected) Box(Modifier.fillMaxSize().background(Color(0x553B82F6)))
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape)
                            .background(if (selected) Color(0xFF3B82F6) else Color(0x66000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("왼쪽으로 회전") },
                    leadingIcon = { Icon(Icons.Default.RotateLeft, null) },
                    onClick = { menu = false; onRotate() },
                )
                DropdownMenuItem(
                    text = { Text("다운로드") },
                    leadingIcon = { Icon(Icons.Default.Download, null) },
                    onClick = { menu = false; onDownload() },
                )
                DropdownMenuItem(
                    text = { Text("공유") },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = { menu = false; onShare() },
                )
                // 이동은 원본을 반대편으로 옮겨(공유 앨범에선 모두에게서 사라짐) 삭제와 동급이므로
                // 삭제 권한(올린이·관리자)일 때만. 복사는 원본을 남기므로 누구나 가능.
                if (canDelete) {
                    DropdownMenuItem(
                        text = { Text("${otherAlbumName}으로 이동") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, null) },
                        onClick = { menu = false; onMove() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("${otherAlbumName}으로 복사") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = { menu = false; onCopy() },
                )
                if (canDelete) {
                    DropdownMenuItem(
                        text = { Text("삭제", color = Color(0xFFE03131)) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE03131)) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        // 하단 캡션: 촬영 날짜·시간 + 댓글 수(있을 때) + 좋아요 수
        Row(Modifier.fillMaxWidth().padding(top = 3.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(dateTimeLabelOf(item), fontSize = 10.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
            if (item.progress.isNotEmpty()) {
                Text("💬 ${item.progress.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Spacer(Modifier.width(6.dp))
            }
            if (item.likes.isNotEmpty()) {
                Text("❤️ ${item.likes.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumViewer(
    photos: List<ListItem>, startIndex: Int, me: String?, vm: AppViewModel,
    onShare: (ListItem) -> Unit, onDownload: (ListItem) -> Unit, onClose: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))) { photos.size }
    var zoomUrl by remember { mutableStateOf<String?>(null) } // 더블탭 시 썸네일을 전체화면으로 확대(원본 미다운로드)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF0000000))) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { photos.getOrNull(it)?.id ?: it }) { page ->
                photos.getOrNull(page)?.let { item ->
                    AlbumViewerPage(item = item, me = me, vm = vm, onZoom = { zoomUrl = it },
                        onShare = onShare, onDownload = onDownload)
                }
            }
            // 상단: 위치(n/총) + 닫기
            Text(
                "${pagerState.currentPage + 1} / ${photos.size}",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                    .background(Color(0x66000000), RoundedCornerShape(999.dp)).padding(horizontal = 12.dp, vertical = 3.dp),
            )
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                Icon(Icons.Default.Close, "닫기", tint = Color.White)
            }
            // 더블탭 → 썸네일을 전체화면 확대(핀치·더블탭·팬, SubsamplingScaleImageView 재사용. 원본 미다운로드).
            // Dialog 창 안에 올려야 페이저 위로 보인다(바깥에 두면 창 뒤로 가려짐).
            zoomUrl?.let { ZoomOverlay(url = it, onClose = { zoomUrl = null }) }
        }
    }
}

/** 뷰어 하단 액션 한 줄(아이콘+텍스트, 어두운 배경용 — 상위 롱클릭 메뉴와 톤 일치). */
@Composable
private fun ViewerMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = tint, fontSize = 15.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumViewerPage(
    item: ListItem, me: String?, vm: AppViewModel, onZoom: (String) -> Unit,
    onShare: (ListItem) -> Unit, onDownload: (ListItem) -> Unit,
) {
    val url = item.photoUrls.firstOrNull().orEmpty()
    val liked = me != null && item.likes.contains(me)
    var comment by remember(item.id) { mutableStateOf("") }
    val canDelete = item.createdBy == me || me == AlbumBoard.ADMIN // 업로더 또는 선일
    var confirmDeletePhoto by remember(item.id) { mutableStateOf(false) }
    var confirmDeleteComment by remember(item.id) { mutableStateOf(-1) }
    val meta = remember(item) {
        listOfNotNull(dateTimeLabelOf(item).ifBlank { null }, item.address.ifBlank { null }).joinToString(" · ")
    }
    val whiteFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
        focusedBorderColor = Color.White.copy(alpha = 0.7f), unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
        focusedLabelColor = Color.White.copy(alpha = 0.85f), unfocusedLabelColor = Color.White.copy(alpha = 0.55f),
        focusedPlaceholderColor = Color.White.copy(alpha = 0.5f), unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Spacer(Modifier.height(44.dp))
        // 보기용은 500px 썸네일(속도·데이터 절약). 더블탭하면 그 썸네일을 전체화면 확대. 회전 각도 적용.
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(albumThumb(url)).crossfade(true)
                .transformations(RotateTransformation(item.rotation)).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .pointerInput(url) { detectTapGestures(onDoubleTap = { if (url.isNotBlank()) onZoom(albumThumb(url)) }) },
            contentScale = ContentScale.FillWidth,
        )
        Text("사진을 더블탭하면 크게 볼 수 있어요. 다운로드 및 공유는 원본 해상도로 제공이 됩니다.",
            color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, lineHeight = 15.sp,
            modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        if (meta.isNotBlank()) {
            Text(meta, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
        }
        Text("올린이: ${Family.nameOf(item.createdBy)}", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { vm.toggleAlbumLike(item) }) {
                Text((if (liked) "❤️ " else "🤍 ") + item.likes.size, color = Color.White, fontSize = 16.sp)
            }
            if (item.likes.isNotEmpty()) {
                Text(item.likes.mapNotNull { Family.nameOf(it) }.joinToString(", "),
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        item.progress.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("${Family.nameOf(c.by)} ", color = Color(0xFF9EC5FF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(c.text, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (c.by == me || me == AlbumBoard.ADMIN) { // 작성자 또는 선일
                    Text("삭제", color = Color(0xFFFF8A8A), fontSize = 12.sp,
                        modifier = Modifier.clickable { confirmDeleteComment = i }.padding(start = 6.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = comment, onValueChange = { comment = it },
                label = { Text("댓글 달기") }, singleLine = true, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f), colors = whiteFieldColors)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { if (comment.isNotBlank()) { vm.addAlbumComment(item, comment); comment = "" } }) {
                Text("등록", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        // 하단 액션(상위 롱클릭 메뉴처럼 아이콘+텍스트): 다운로드 · 공유 · (권한 시) 삭제
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.15f)))
        Spacer(Modifier.height(4.dp))
        ViewerMenuRow(Icons.Default.Download, "다운로드", Color.White) { onDownload(item) }
        ViewerMenuRow(Icons.Default.Share, "공유", Color.White) { onShare(item) }
        if (canDelete) {
            ViewerMenuRow(Icons.Default.Delete, "사진 삭제", Color(0xFFFF8A8A)) { confirmDeletePhoto = true }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (confirmDeletePhoto) {
        AlertDialog(
            onDismissRequest = { confirmDeletePhoto = false },
            title = { Text("사진 삭제") },
            text = { Text("이 사진을 삭제할까요? 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { confirmDeletePhoto = false; vm.deleteAlbumPhoto(item) }) {
                    Text("삭제", color = Color(0xFFE03131))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePhoto = false }) { Text("취소") } },
        )
    }
    if (confirmDeleteComment >= 0) {
        val idx = confirmDeleteComment
        AlertDialog(
            onDismissRequest = { confirmDeleteComment = -1 },
            title = { Text("댓글 삭제") },
            text = { Text("이 댓글을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteComment = -1; vm.deleteAlbumComment(item, idx) }) {
                    Text("삭제", color = Color(0xFFE03131))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteComment = -1 }) { Text("취소") } },
        )
    }
}

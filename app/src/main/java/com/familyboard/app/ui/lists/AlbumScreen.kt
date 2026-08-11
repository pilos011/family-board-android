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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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

/** 사진첩 그리드 평면 항목(월 헤더 + 사진). 빠른 스크롤 인덱스↔년월 매핑용. */
private sealed interface AlbumEntry {
    data class Header(val month: String) : AlbumEntry
    data class Photo(val item: ListItem) : AlbumEntry
}

private fun entryMonth(e: AlbumEntry?): String = when (e) {
    is AlbumEntry.Header -> e.month
    is AlbumEntry.Photo -> monthLabelOf(e.item)
    null -> ""
}

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
fun AlbumScreen(vm: AppViewModel, onBack: () -> Unit) {
    val items by vm.albumItems.collectAsStateWithLifecycle()
    val me by vm.currentMemberId.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<ListItem?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // 사진 공유: 원본 파일을 캐시로 받아 표준 공유 시트로.
    fun sharePhoto(item: ListItem) {
        val url = item.photoUrls.firstOrNull() ?: return
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val srcExt = url.substringBefore('?').substringAfterLast('.', "jpg").take(4).ifBlank { "jpg" }
                    val png = srcExt.equals("png", true)
                    val raw = java.net.URL(url).openStream().use { it.readBytes() }
                    val bytes = applyRotation(raw, item.rotation, png)
                    val ext = if (item.rotation % 360 != 0 && !png) "jpg" else srcExt
                    val f = java.io.File(context.cacheDir, "album_share.$ext")
                    f.outputStream().use { it.write(bytes) }
                    androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                }.getOrNull()
            }
            if (uri == null) { Toast.makeText(context, "공유할 수 없어요", Toast.LENGTH_SHORT).show(); return@launch }
            val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri).setType("image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(send, "사진 공유")) }
                .onFailure { Toast.makeText(context, "공유할 수 없어요", Toast.LENGTH_SHORT).show() }
        }
    }

    // 원본을 폰의 공용 Download 폴더에 저장.
    fun downloadPhoto(item: ListItem) {
        val url = item.photoUrls.firstOrNull() ?: return
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val srcExt = url.substringBefore('?').substringAfterLast('.', "jpg").take(4).ifBlank { "jpg" }
                    val png = srcExt.equals("png", true)
                    val raw = java.net.URL(url).openStream().use { it.readBytes() }
                    val bytes = applyRotation(raw, item.rotation, png)
                    val ext = if (item.rotation % 360 != 0 && !png) "jpg" else srcExt
                    val name = "가족사진_${System.currentTimeMillis()}.$ext"
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
                        val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                            ?: return@runCatching false
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
            Toast.makeText(context, if (ok) "Download 폴더에 저장했어요" else "저장 실패", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
            Text("가족 사진첩", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { pick() }) { Icon(Icons.Default.AddAPhoto, "사진 추가") }
        }

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
                val sorted = remember(list) { list.sortedByDescending { capMillis(it) } }
                // 월 헤더 + 사진을 한 평면 리스트로(빠른 스크롤 인덱스↔년월 매핑용)
                val flat = remember(sorted) {
                    buildList {
                        sorted.groupBy { monthLabelOf(it) }.forEach { (month, photos) ->
                            add(AlbumEntry.Header(month))
                            photos.forEach { add(AlbumEntry.Photo(it)) }
                        }
                    }
                }
                val albumGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3), state = albumGridState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(
                            flat.size,
                            key = { i -> when (val e = flat[i]) { is AlbumEntry.Header -> "h:${e.month}"; is AlbumEntry.Photo -> e.item.id } },
                            span = { i -> if (flat[i] is AlbumEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                        ) { i ->
                            when (val e = flat[i]) {
                                is AlbumEntry.Header -> Text(
                                    e.month, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                                )
                                is AlbumEntry.Photo -> AlbumTile(
                                    item = e.item,
                                    canDelete = e.item.createdBy == me || me == AlbumBoard.ADMIN, // 업로더 또는 선일
                                    onOpen = { selectedId = e.item.id },
                                    onRotate = { vm.rotateAlbumPhoto(e.item, 270) }, // 270=반시계(왼쪽으로)
                                    onShare = { sharePhoto(e.item) },
                                    onDownload = { downloadPhoto(e.item) },
                                    onDelete = { confirmDelete = e.item },
                                )
                            }
                        }
                    }
                    FastScroller(albumGridState, flat.size) { i -> entryMonth(flat.getOrNull(i)) }
                }
            }
        }
    }

    val sortedAll = remember(items) { (items ?: emptyList()).sortedByDescending { capMillis(it) } }
    val curIndex = sortedAll.indexOfFirst { it.id == selectedId }
    // 뷰어 도중 사진이 사라지면(본인·타인 삭제 등) 선택 해제. 컴포지션 중 상태 쓰기 대신 이펙트로.
    LaunchedEffect(selectedId, curIndex) {
        if (selectedId != null && curIndex < 0) selectedId = null
    }
    if (selectedId != null && curIndex >= 0) {
        AlbumViewer(photos = sortedAll, startIndex = curIndex, me = me, vm = vm, onClose = { selectedId = null })
    }

    if (showPicker) {
        InAppPhotoPicker(
            gridState = pickerGridState,
            onConfirm = { uris -> if (uris.isNotEmpty()) vm.addAlbumPhotos(uris); showPicker = false },
            onClose = { showPicker = false },
        )
    }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("사진 삭제") },
            text = { Text("이 사진을 삭제할까요? 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteItem(target.id); confirmDelete = null }) {
                    Text("삭제", color = Color(0xFFE03131))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("취소") } },
        )
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
                        FastScroller(gridState, list.size) { i -> ymLabel(list.getOrNull(i)?.millis ?: 0L) }
                    }
                }
            }
        }
    }
}

/** 우측 빠른 스크롤: 오른쪽 띠 어디를 드래그해도 그 위치로 점프하고, 그 지점의 "년월" 버블을 표시.
 *  labelAt(index)=해당 스크롤 위치(그리드 항목 index)의 년월 문자열. */
@Composable
private fun BoxScope.FastScroller(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    count: Int,
    labelAt: (Int) -> String,
) {
    if (count < 30) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var trackH by remember { mutableFloatStateOf(0f) }
    var activeY by remember { mutableFloatStateOf(0f) }
    val thumbH = 56.dp
    val thumbHpx = with(density) { thumbH.toPx() }
    val maxOffset = (trackH - thumbHpx).coerceAtLeast(0f)
    val progress by remember { derivedStateOf { if (count <= 1) 0f else gridState.firstVisibleItemIndex.toFloat() / (count - 1) } }

    fun jumpTo(y: Float) {
        activeY = y.coerceIn(0f, trackH)
        val frac = if (trackH > 0f) activeY / trackH else 0f
        val target = (frac * (count - 1)).roundToInt().coerceIn(0, count - 1)
        scope.launch { gridState.scrollToItem(target) }
    }

    val thumbY = if (dragging) (activeY - thumbHpx / 2f).coerceIn(0f, maxOffset) else progress * maxOffset
    val labelIdx = if (dragging && trackH > 0f) ((activeY / trackH) * (count - 1)).roundToInt().coerceIn(0, count - 1)
    else gridState.firstVisibleItemIndex
    val label = labelAt(labelIdx.coerceIn(0, count - 1))

    Box(
        Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(40.dp)
            .onSizeChanged { trackH = it.height.toFloat() }
            .pointerInput(count) {
                // 세로 드래그가 touchSlop을 넘으면 그때부터 소비(스크롤). 단순 탭은 소비 안 해
                // 뒤 사진의 클릭이 살아남음(오른쪽 열 사진도 열기/선택 가능).
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var started = false
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        if (!started && kotlin.math.abs(ch.position.y - down.position.y) > slop) {
                            started = true; dragging = true
                        }
                        if (started) { jumpTo(ch.position.y); ch.consume() }
                    }
                    if (started) dragging = false
                }
            },
    ) {
        // 핸들
        Box(
            Modifier.align(Alignment.TopEnd).offset { IntOffset(0, thumbY.roundToInt()) }
                .padding(end = 5.dp).size(width = 11.dp, height = thumbH)
                .clip(RoundedCornerShape(6.dp))
                .background(if (dragging) Color(0xFF3B82F6) else Color(0x663B82F6)),
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
    onOpen: () -> Unit,
    onRotate: () -> Unit,
    onShare: () -> Unit,
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
                    .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(albumThumb(url)).size(400).crossfade(true)
                        .transformations(RotateTransformation(item.rotation)).build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                )
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
                if (canDelete) {
                    DropdownMenuItem(
                        text = { Text("삭제", color = Color(0xFFE03131)) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFE03131)) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        // 하단 캡션: 촬영 날짜·시간 + 좋아요 수
        Row(Modifier.fillMaxWidth().padding(top = 3.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(dateTimeLabelOf(item), fontSize = 10.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
            if (item.likes.isNotEmpty()) {
                Text("❤️ ${item.likes.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumViewer(photos: List<ListItem>, startIndex: Int, me: String?, vm: AppViewModel, onClose: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0))) { photos.size }
    var zoomUrl by remember { mutableStateOf<String?>(null) } // 더블탭 시 썸네일을 전체화면으로 확대(원본 미다운로드)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xF0000000))) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { photos.getOrNull(it)?.id ?: it }) { page ->
                photos.getOrNull(page)?.let { item ->
                    AlbumViewerPage(item = item, me = me, vm = vm, onZoom = { zoomUrl = it })
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumViewerPage(item: ListItem, me: String?, vm: AppViewModel, onZoom: (String) -> Unit) {
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
        Text("더블탭하면 크게 볼 수 있어요", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp,
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
            TextButton(onClick = { if (comment.isNotBlank()) { vm.addPlaceComment(item, comment); comment = "" } }) {
                Text("등록", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (canDelete) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmDeletePhoto = true }) {
                Text("사진 삭제", color = Color(0xFFFF8A8A))
            }
        }
        Spacer(Modifier.height(40.dp))
    }

    if (confirmDeletePhoto) {
        AlertDialog(
            onDismissRequest = { confirmDeletePhoto = false },
            title = { Text("사진 삭제") },
            text = { Text("이 사진을 삭제할까요? 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = { confirmDeletePhoto = false; vm.deleteItem(item.id) }) {
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
                TextButton(onClick = { confirmDeleteComment = -1; vm.deletePlaceComment(item, idx) }) {
                    Text("삭제", color = Color(0xFFE03131))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteComment = -1 }) { Text("취소") } },
        )
    }
}

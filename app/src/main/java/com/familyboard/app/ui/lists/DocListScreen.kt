package com.familyboard.app.ui.lists

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.DocBoard
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocListScreen(
    vm: AppViewModel,
    currentMemberId: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val docsState by vm.docItems.collectAsStateWithLifecycle()

    var uploading by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ListItem?>(null) }
    var pendingDelete by remember { mutableStateOf<ListItem?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            uploading = true
            vm.addDocFromUri(uri) { ok, err ->
                uploading = false
                Toast.makeText(context, if (ok) "문서를 올렸어요" else (err ?: "실패했어요"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 열람 권한이 있는 문서만, 최근 올린 것이 위로
    val visible = remember(docsState, currentMemberId) {
        docsState?.filter { DocBoard.visibleTo(it, currentMemberId) }?.sortedByDescending { it.createdAt }
    }

    fun openDoc(doc: ListItem) {
        val url = doc.photoUrls.firstOrNull().orEmpty()
        if (url.isBlank()) { Toast.makeText(context, "파일을 찾을 수 없어요", Toast.LENGTH_SHORT).show(); return }
        opening = true
        scope.launch {
            val file = downloadToCache(context, url, doc.fileName.ifBlank { doc.text })
            opening = false
            if (file == null) { Toast.makeText(context, "내려받기 실패(네트워크 확인)", Toast.LENGTH_SHORT).show(); return@launch }
            runCatching {
                val fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val mime = doc.fileMime.ifBlank { guessMime(file.name) }
                val view = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                // createChooser(1회성) 대신 기본 실행 → 안드로이드 '연결 프로그램(한 번만/항상)' 창이 뜨고,
                // '항상' 선택 시 시스템 기본 앱으로 기억됨(맛집 내비 선택과 동일한 경험).
                context.startActivity(view)
            }.onFailure { Toast.makeText(context, "이 형식을 열 수 있는 앱이 없어요", Toast.LENGTH_SHORT).show() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(DocBoard.TITLE, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (!uploading) picker.launch(arrayOf("*/*")) },
                containerColor = DocsColor,
            ) {
                if (uploading) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.Add, "파일 올리기", tint = Color.White)
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                visible == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                visible.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), modifier = Modifier.size(56.dp))
                        Spacer(Modifier.size(12.dp))
                        Text("아직 공유된 문서가 없어요", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(Modifier.size(4.dp))
                        Text("아래 + 로 pdf·사진·문서를 올려보세요", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { doc ->
                        DocRow(
                            doc = doc,
                            canManage = DocBoard.canManage(doc, currentMemberId),
                            onOpen = { openDoc(doc) },
                            onEdit = { editItem = doc },
                            onDelete = { pendingDelete = doc },
                        )
                    }
                }
            }

            if (opening) Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp)); Text("여는 중…")
                }
            }
        }
    }

    editItem?.let { doc ->
        DocEditDialog(doc = doc, onDismiss = { editItem = null }) { title, viewerIds ->
            vm.updateDoc(doc, title, viewerIds); editItem = null
        }
    }

    pendingDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("문서 삭제") },
            text = { Text("'${doc.text}' 문서를 삭제할까요?") },
            confirmButton = { TextButton(onClick = { vm.deleteItem(doc.id); pendingDelete = null }) { Text("삭제", color = Color(0xFFE03131)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocRow(
    doc: ListItem,
    canManage: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val ext = fileExtOf(doc)
    val (icon, tint) = docIcon(ext)
    val restricted = !(doc.memberIds.isEmpty() || doc.memberIds.contains("all"))

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onOpen, onLongClick = { if (canManage) menu = true })
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(doc.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(3.dp))
            val meta = listOf(Family.nameOf(doc.createdBy), fmtDate(doc.createdAt), fmtSize(doc.fileSize))
                .filter { it.isNotBlank() }.joinToString(" · ")
            Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (restricted) {
                Spacer(Modifier.size(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = DocsColor, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.size(3.dp))
                    Text("${Family.targetNames(doc.memberIds)}만", fontSize = 11.sp, color = DocsColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (canManage) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "메뉴") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("수정") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("삭제", color = Color(0xFFE03131)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocEditDialog(doc: ListItem, onDismiss: () -> Unit, onSave: (String, List<String>) -> Unit) {
    var title by remember { mutableStateOf(doc.text) }
    // 열람 대상: 비었거나 all 포함이면 "모두"
    val initial = if (doc.memberIds.isEmpty() || doc.memberIds.contains("all")) emptyList() else doc.memberIds
    var viewers by remember { mutableStateOf(initial) }
    val isAll = viewers.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("문서 수정") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("제목") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.size(14.dp))
                Text("볼 수 있는 사람", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.size(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ViewerChip("모두", isAll) { viewers = emptyList() }
                    Family.members.forEach { m ->
                        val on = !isAll && viewers.contains(m.id)
                        ViewerChip(m.name, on) {
                            viewers = when {
                                isAll -> listOf(m.id)                 // 모두 → 이 사람만
                                on -> viewers - m.id                   // 해제(다 빠지면 모두)
                                else -> viewers + m.id
                            }
                        }
                    }
                }
                if (!isAll) {
                    Spacer(Modifier.size(6.dp))
                    Text("지정한 사람과 올린이·관리자(선일)만 볼 수 있어요", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onSave(title.trim(), viewers) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun ViewerChip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (on) DocsColor else Color(0xFFF1F3F5))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

private val DocsColor = Color(0xFF3B5BDB)

private fun fileExtOf(doc: ListItem): String {
    val fromFile = doc.fileName.substringAfterLast('.', "").lowercase()
    return if (fromFile.isNotBlank()) fromFile else doc.text.substringAfterLast('.', "").lowercase()
}

private fun docIcon(ext: String): Pair<ImageVector, Color> = when (ext) {
    "pdf" -> Icons.Default.PictureAsPdf to Color(0xFFE03131)
    "doc", "docx", "hwp", "hwpx", "txt", "rtf", "md" -> Icons.Default.Description to Color(0xFF1971C2)
    "xls", "xlsx", "csv" -> Icons.Default.TableChart to Color(0xFF2F9E44)
    "ppt", "pptx" -> Icons.Default.Slideshow to Color(0xFFE8590C)
    "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp" -> Icons.Default.Image to Color(0xFF7048E8)
    "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip to Color(0xFF868E96)
    "mp4", "mov", "avi", "mkv", "webm" -> Icons.Default.Movie to Color(0xFFF06595)
    else -> Icons.Default.Description to Color(0xFF868E96)
}

private fun fmtSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> String.format("%.1fMB", bytes / 1024.0 / 1024.0)
}

private fun fmtDate(millis: Long): String {
    if (millis <= 0) return ""
    val d = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val now = java.time.LocalDate.now()
    return if (d.year == now.year) "${d.monthValue}/${d.dayOfMonth}"
    else "${d.year % 100}.${d.monthValue}.${d.dayOfMonth}"
}

private fun guessMime(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
}

private suspend fun downloadToCache(context: android.content.Context, url: String, fileName: String): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val safe = fileName.ifBlank { "file" }.replace(Regex("[\\\\/:*?\"<>|]"), "_").takeLast(120)
            val dir = File(context.cacheDir, "docs").apply { mkdirs() }
            val out = File(dir, safe)
            (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 60000
            }.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            out
        }.getOrNull()
    }

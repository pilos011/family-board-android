package com.familyboard.app.ui.lists

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyboard.app.data.model.FunBoard
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunListScreen(
    vm: AppViewModel,
    currentMemberId: String?,
    onBack: () -> Unit,
) {
    val items by vm.funItems.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isParent = currentMemberId == "seonil" || currentMemberId == "eunseon"
    fun canEdit(it: ListItem) = it.createdBy == currentMemberId || isParent

    var showAdd by remember { mutableStateOf(false) }
    var editItem by remember { mutableStateOf<ListItem?>(null) }
    var actionItem by remember { mutableStateOf<ListItem?>(null) }

    fun open(link: String) {
        if (link.isBlank()) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
            .onFailure { Toast.makeText(context, "링크를 열 수 없어요", Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(FunBoard.TITLE) },
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
        if (items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "아직 게시물이 없어요.\n유튜브·웹페이지를 '공유 → 준준가족 보드'\n또는 오른쪽 아래 +로 담아보세요.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        } else {
            // 한 행에 4개
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.chunked(4).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowItems.forEach { post ->
                            FunCell(post, Modifier.weight(1f),
                                onClick = { open(post.link) },
                                onLongPress = { if (canEdit(post)) actionItem = post })
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }

    if (showAdd) FunEditDialog(vm, null, onSave = { t, l, img -> vm.addFun(t, l, img); showAdd = false }, onDismiss = { showAdd = false })
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

@Composable
private fun FunCell(item: ListItem, modifier: Modifier, onClick: () -> Unit, onLongPress: () -> Unit) {
    Column(
        modifier.pointerInput(item.id) {
            detectTapGestures(onTap = { onClick() }, onLongPress = { onLongPress() })
        },
    ) {
        val photo = item.photoUrls.firstOrNull()
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFECECEC)),
            contentAlignment = Alignment.Center,
        ) {
            if (!photo.isNullOrBlank()) {
                AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Default.PlayCircle, null, tint = Color(0xFFB0B0B0), modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(item.text.ifBlank { "제목 없음" }, fontSize = 11.sp, lineHeight = 13.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
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
            TextButton(enabled = link.isNotBlank(), onClick = { onSave(title.trim().ifBlank { "링크" }, link.trim(), image) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

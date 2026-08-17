package com.familyboard.app.ui.lists

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.model.TravelBoard
import com.familyboard.app.ui.AppViewModel

/**
 * 여행 위시리스트: 구글 지도 공유로 담은 국내·해외 가볼 곳. 탭=구글 지도로 열기,
 * 롱클릭=메뉴(주소·메모·편집·다녀옴·삭제). '다녀옴'은 맨 뒤로. 담기=다른 앱에서 구글 지도 '공유'.
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

    // 안 가본 곳(미체크) 먼저, 다녀온 곳은 뒤. 그 안에서 최신순.
    val sorted = remember(items) {
        (items ?: emptyList()).sortedWith(compareBy<ListItem> { if (it.checked) 1 else 0 }.thenByDescending { it.createdAt })
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
            sorted.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "아직 담은 여행지가 없어요.\n구글 지도에서 장소를 '공유'로 담아보세요.\n(국내·해외 모두)",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(sorted, key = { it.id }) { item ->
                    TravelCard(item = item, onOpen = { openMap(item) }, onLongPress = { actionId = item.id })
                }
            }
        }
    }

    actionId?.let { id ->
        val item = sorted.firstOrNull { it.id == id }
        if (item == null) actionId = null
        else {
            val canManage = TravelBoard.canManage(me, item)
            AlertDialog(
                onDismissRequest = { actionId = null },
                title = { Text(item.text.ifBlank { "장소" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        if (item.address.isNotBlank()) { Text("📍 ${item.address}", fontSize = 13.sp); Spacer(Modifier.height(6.dp)) }
                        Text("메모", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (item.description.isNotBlank()) SelectionContainer { Text(item.description, fontSize = 13.sp) }
                        else Text("없음", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
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
        val item = sorted.firstOrNull { it.id == id }
        if (item == null) editId = null
        else TravelEditDialog(
            item = item,
            onSave = { name, memo -> vm.updateTravel(id, name, memo); editId = null },
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
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.text.ifBlank { "장소" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (visited) {
                        Spacer(Modifier.width(6.dp))
                        Text("다녀옴", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFF12B886), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                if (item.address.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(item.address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text("📝 ${item.description}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Place, "지도로 열기", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TravelEditDialog(item: ListItem, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(item.id) { mutableStateOf(item.text) }
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
                    value = memo, onValueChange = { memo = it }, minLines = 3, maxLines = 8,
                    label = { Text("메모") }, placeholder = { Text("예약·영업시간·꿀팁 등 (길게 붙여넣기 가능)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, memo) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

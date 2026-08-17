package com.familyboard.app.ui.lists

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.CouponBoard
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.ui.AppViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

// 유효기간 라벨: "D-3" / "오늘까지" / "만료" / null(없음)
private fun expiryLabel(dateIso: String): String? {
    if (dateIso.isBlank()) return null
    val d = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: return null
    val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
    return when {
        days < 0 -> "만료"
        days == 0L -> "오늘까지"
        else -> "D-$days"
    }
}

/**
 * 가족 쿠폰함(v1.0.141): 3열 그리드. 사용완료는 맨 뒤, 그 앞은 만료일 가까운 순.
 * 카드 **탭=쿠폰 이미지/링크/텍스트 바로 열기**, **롱클릭=메뉴(유효기간·메모·편집·삭제)**. 사용완료=회색+사용자.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponScreen(vm: AppViewModel, currentMemberId: String?, onBack: () -> Unit) {
    val items by vm.couponItems.collectAsStateWithLifecycle()
    val me = currentMemberId.orEmpty()
    val isAdmin = CouponBoard.canDelete(currentMemberId)
    val context = LocalContext.current
    var viewer by remember { mutableStateOf<String?>(null) }   // 이미지 확대
    var actionId by remember { mutableStateOf<String?>(null) } // 롱클릭 메뉴 대상
    var editId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    // 사용완료는 맨 뒤(1), 미사용(0) 먼저. 그 안에서 만료일 가까운 순(없으면 뒤), 그 다음 최신순.
    val sorted = remember(items) {
        (items ?: emptyList()).sortedWith(
            compareBy<ListItem> { if (it.checked) 1 else 0 }
                .thenBy { it.dateIso.ifBlank { "9999-99-99" } }
                .thenByDescending { it.createdAt },
        )
    }

    fun openCoupon(item: ListItem) {
        val url = item.photoUrls.firstOrNull().orEmpty()
        when {
            url.isNotBlank() -> viewer = url // 이미지 쿠폰: 이미지 자체를 크게
            item.link.isNotBlank() -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
                .onFailure { Toast.makeText(context, "링크를 열 수 없어요", Toast.LENGTH_SHORT).show() }
            item.text.isNotBlank() -> {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("coupon", item.text))
                Toast.makeText(context, "쿠폰 내용을 복사했어요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(CouponBoard.TITLE) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
        )
        when {
            items == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            sorted.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "아직 쿠폰이 없어요.\n재미진 곳/내 재미진 곳에서\n'가족 쿠폰함으로 이동'으로 담아보세요.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                items(sorted, key = { it.id }) { item ->
                    CouponCard(
                        item = item, me = me,
                        onOpen = { openCoupon(item) },       // 탭 = 이미지/링크/텍스트 바로
                        onLongPress = { actionId = item.id }, // 롱클릭 = 메뉴
                        onToggleUsed = { vm.toggleCouponUsed(item) },
                    )
                }
            }
        }
    }

    viewer?.let { ZoomOverlay(it) { viewer = null } }

    // 롱클릭 메뉴: 유효기간·메모 + 링크 열기 + 편집(작성자·관리자)·삭제(관리자)
    actionId?.let { id ->
        val item = sorted.firstOrNull { it.id == id }
        if (item == null) actionId = null
        else CouponActionDialog(
            item = item, me = me, isAdmin = isAdmin,
            onOpenLink = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }
                    .onFailure { Toast.makeText(context, "링크를 열 수 없어요", Toast.LENGTH_SHORT).show() }
                actionId = null
            },
            onCopyMemo = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("memo", item.description))
                Toast.makeText(context, "메모를 복사했어요", Toast.LENGTH_SHORT).show()
            },
            onEdit = { editId = id; actionId = null },
            onDelete = { pendingDelete = id; actionId = null },
            onClose = { actionId = null },
        )
    }

    // 편집: 제목·유효기간(달력)·메모
    editId?.let { id ->
        val item = sorted.firstOrNull { it.id == id }
        if (item == null) editId = null
        else CouponEditDialog(
            item = item,
            onSave = { title, dateIso, memo -> vm.updateCoupon(id, title, dateIso, memo); editId = null },
            onDismiss = { editId = null },
        )
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("쿠폰 삭제") },
            text = { Text("이 쿠폰을 삭제할까요?") },
            confirmButton = { TextButton(onClick = { vm.deleteItem(id); pendingDelete = null }) { Text("삭제", color = Color(0xFFE03131)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CouponCard(item: ListItem, me: String, onOpen: () -> Unit, onLongPress: () -> Unit, onToggleUsed: () -> Unit) {
    val used = item.checked
    val canCancel = used && item.usedBy == me
    val url = item.photoUrls.firstOrNull().orEmpty()
    val exp = expiryLabel(item.dateIso)
    Card(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).combinedClickable(onClick = onOpen, onLongClick = onLongPress)) {
                if (url.isNotBlank()) {
                    AsyncImage(model = funThumbUrl(url), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(Modifier.fillMaxSize().padding(8.dp)) {
                        Text(if (item.link.isNotBlank()) "🔗 링크" else "🎟️ 쿠폰", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(3.dp))
                        Text(item.text.ifBlank { item.link }, fontSize = 11.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (exp != null) {
                    Text(
                        exp, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                            .background(if (exp == "만료") Color(0xE0E03131) else Color(0xCC0CA678), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
                if (url.isNotBlank() && item.text.isNotBlank()) {
                    Text(
                        item.text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                if (used) {
                    Box(Modifier.fillMaxSize().background(Color(0xCCB0B0B0)).padding(4.dp), Alignment.Center) {
                        Text(if (item.usedBy.isNotBlank()) "사용완료\n${Family.nameOf(item.usedBy)}" else "사용완료", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
            val label = if (used) "사용완료" else "사용"
            val btnColor = if (used) Color(0xFF868E96) else Color(0xFF0CA678)
            Box(
                Modifier.fillMaxWidth().background(btnColor).let { if (!used || canCancel) it.clickable { onToggleUsed() } else it }.padding(vertical = 6.dp),
                Alignment.Center,
            ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun CouponActionDialog(
    item: ListItem, me: String, isAdmin: Boolean,
    onOpenLink: () -> Unit, onCopyMemo: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit,
) {
    val canEdit = item.createdBy == me || isAdmin
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.text.ifBlank { "쿠폰" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val expFull = if (item.dateIso.isBlank()) "없음" else "${item.dateIso} (${expiryLabel(item.dateIso)})"
                Text("유효기간: $expFull", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                if (item.link.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onOpenLink, contentPadding = PaddingValues(0.dp)) { Text("🔗 링크 열기") }
                }
                Spacer(Modifier.height(8.dp))
                Text("메모", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                if (item.description.isNotBlank()) {
                    SelectionContainer { Text(item.description, fontSize = 13.sp) } // 길게 눌러 복사 가능
                    TextButton(onClick = onCopyMemo, contentPadding = PaddingValues(0.dp)) { Text("메모 복사") }
                } else {
                    Text("없음", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                if (item.checked) {
                    Spacer(Modifier.height(6.dp))
                    Text("사용완료 - ${Family.nameOf(item.usedBy)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF868E96))
                }
            }
        },
        confirmButton = {
            Row {
                if (canEdit) TextButton(onClick = onEdit) { Text("편집") }
                if (isAdmin) TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFFE03131)) }
                TextButton(onClick = onClose) { Text("닫기") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CouponEditDialog(item: ListItem, onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember(item.id) { mutableStateOf(item.text) }
    var memo by remember(item.id) { mutableStateOf(item.description) }
    var dateIso by remember(item.id) { mutableStateOf(item.dateIso) }
    var showDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("쿠폰 편집") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(60) }, singleLine = true,
                    label = { Text("제목/짧은 설명") }, placeholder = { Text("예: 스타벅스 아메리카노 쿠폰") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("유효기간: ${dateIso.ifBlank { "없음" }}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showDate = true }) { Text("날짜 선택") }
                    if (dateIso.isNotBlank()) TextButton(onClick = { dateIso = "" }) { Text("지우기") }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = memo, onValueChange = { memo = it }, minLines = 3, maxLines = 8,
                    label = { Text("메모") }, placeholder = { Text("혜택 조건·사용 가능 매장 등 (길게 붙여넣기 가능)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, dateIso, memo) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    if (showDate) {
        val initMillis = runCatching { LocalDate.parse(dateIso) }.getOrNull()
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
            ?: LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateIso = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    showDate = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
}

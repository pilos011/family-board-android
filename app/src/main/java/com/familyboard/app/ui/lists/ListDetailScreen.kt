package com.familyboard.app.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.BoardType
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    vm: AppViewModel,
    boardKey: String,
    currentMemberId: String?,
    onBack: () -> Unit,
) {
    // 고정 보드(장보기/할일/공지) 또는 사용자 커스텀 리스트
    val knownBoard = remember(boardKey) { BoardType.entries.firstOrNull { it.key == boardKey } }
    val isCustom = knownBoard == null
    val customLists by vm.customLists.collectAsStateWithLifecycle()
    val customDef = customLists.firstOrNull { it.id == boardKey }
    val title = knownBoard?.title ?: customDef?.text ?: "목록"
    // 장보기·공지·커스텀(본인 전용): 담당 없이 등록자만 표시하는 단순 목록
    val simpleList = isCustom || knownBoard == BoardType.SHOPPING || knownBoard == BoardType.NOTICE
    // 고정 보드는 이미 공유 StateFlow 재사용(이중 구독·빈상태 깜빡임 방지), 커스텀만 콜드 flow
    val items by if (isCustom) {
        remember(boardKey) { vm.boardItems(boardKey) }.collectAsStateWithLifecycle(initialValue = emptyList())
    } else {
        vm.itemsFor(boardKey).collectAsStateWithLifecycle()
    }
    var input by remember { mutableStateOf("") }
    var tagIds by remember { mutableStateOf(listOf(Family.ALL_ID)) }
    // 삭제 확인용: 삭제하려는 항목(개별) / 리스트 자체 삭제 여부
    var pendingDelete by remember { mutableStateOf<ListItem?>(null) }
    var confirmListDelete by remember { mutableStateOf(false) }

    fun add() {
        if (input.isBlank()) return
        // 장보기·공지·커스텀은 담당 없이(알림 없음), 할 일은 선택한 담당자에게 등록 알림.
        val item = ListItem(
            text = input.trim(),
            checked = false,
            board = boardKey,
            createdBy = currentMemberId ?: Family.ALL_ID,
            memberIds = if (simpleList) listOf(Family.ALL_ID) else tagIds,
        )
        if (knownBoard == BoardType.TODO) vm.addTodoWithNotify(item) else vm.addItem(item)
        input = ""
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    // 장보기: 쿠팡 장바구니 바로가기(앱 있으면 앱, 없으면 웹)
                    if (knownBoard == BoardType.SHOPPING) {
                        val ctx = LocalContext.current
                        Row(
                            Modifier.padding(end = 6.dp).clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFE03131).copy(alpha = 0.12f))
                                .clickable { openCoupangCart(ctx) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.ShoppingCart, "쿠팡 장바구니", tint = Color(0xFFE03131), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("쿠팡", color = Color(0xFFE03131), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (isCustom && customDef != null) {
                        IconButton(onClick = { confirmListDelete = true }) {
                            Icon(Icons.Default.Delete, "리스트 삭제")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // 담당자 태깅 선택 (할 일 전용, 장보기·공지사항은 담당 없음)
                    if (!simpleList) {
                        Text("담당", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(Modifier.size(4.dp))
                        MemberChips(selected = tagIds, onSelect = { tagIds = it })
                        Spacer(Modifier.size(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("항목 추가") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true,
                        )
                        Spacer(Modifier.size(8.dp))
                        IconButton(
                            onClick = { add() },
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        ) { Icon(Icons.Default.Add, "추가", tint = Color.White) }
                    }
                }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "항목이 없어요.\n아래에서 추가해 보세요.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        } else {
            val sorted = remember(items) { items.sortedBy { it.checked } }
            LazyColumn(
                Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.size(4.dp)) }
                items(sorted, key = { it.id }) { itm ->
                    ItemRow(
                        item = itm,
                        // 장보기·공지사항: 추가한 사람 표시 / 할 일: 담당자 표시
                        rightIds = if (simpleList) listOf(itm.createdBy) else itm.memberIds,
                        showSource = knownBoard == BoardType.SHOPPING, // 장보기만 출처 배지(직접/쿠팡/코코달인)
                        onToggle = { vm.toggleItem(itm.id, it) },
                        onDelete = { pendingDelete = itm },
                    )
                }
            }
        }
    }

    // 개별 항목 삭제 확인
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("항목 삭제") },
            text = { Text("\"${target.text}\" 항목을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteItem(target.id); pendingDelete = null }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("취소") }
            },
        )
    }

    // 커스텀 리스트 자체 삭제 확인
    if (confirmListDelete) {
        AlertDialog(
            onDismissRequest = { confirmListDelete = false },
            title = { Text("리스트 삭제") },
            text = { Text("\"$title\" 리스트를 삭제할까요?\n안에 있는 항목도 함께 사라져요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmListDelete = false
                    vm.deleteCustomList(boardKey); onBack()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmListDelete = false }) { Text("취소") }
            },
        )
    }
}

/** 담당자 선택 칩 (모두 + 가족 4명, 복수 선택) */
@Composable
private fun MemberChips(selected: List<String>, onSelect: (List<String>) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val allOn = selected.isEmpty() || selected.contains(Family.ALL_ID)
        Chip("모두", Family.allColor, allOn) { onSelect(listOf(Family.ALL_ID)) }
        Family.members.forEach { m ->
            Chip(m.name, m.color, !allOn && selected.contains(m.id)) {
                val cur = selected.filter { it != Family.ALL_ID }.toMutableList()
                if (cur.contains(m.id)) cur.remove(m.id) else cur.add(m.id)
                // 4명 모두 선택되면 개별 체크 해제하고 "모두"로
                val allFour = Family.members.all { cur.contains(it.id) }
                onSelect(if (cur.isEmpty() || allFour) listOf(Family.ALL_ID) else cur)
            }
        }
    }
}

@Composable
private fun Chip(label: String, color: Color, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) color else Color(0xFFF1F3F5))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
        }
        Text(label, color = if (on) Color.White else Color(0xFF444444), fontSize = 13.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ItemRow(item: ListItem, rightIds: List<String>, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, showSource: Boolean = false) {
    val context = LocalContext.current
    val hasLink = item.link.isNotBlank()
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = onToggle)
        Text(
            item.text,
            modifier = Modifier.weight(1f)
                .then(if (hasLink) Modifier.clickable { openUrl(context, item.link) } else Modifier),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
            color = when {
                item.checked -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                hasLink -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
        // 출처 배지(장보기): 직접 / 쿠팡 / 코코달인
        if (showSource) {
            val (srcLabel, srcColor) = when {
                item.link.contains("coupang", ignoreCase = true) || item.link.contains("coupa.ng", ignoreCase = true) -> "쿠팡" to Color(0xFFE03131)
                item.link.contains("cocodalin", ignoreCase = true) -> "코코달인" to Color(0xFFF08C00)
                else -> "직접" to Color(0xFF868E96)
            }
            Box(
                Modifier.padding(end = 4.dp).clip(RoundedCornerShape(6.dp))
                    .background(srcColor.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp),
            ) { Text(srcLabel, fontSize = 10.sp, color = srcColor, fontWeight = FontWeight.Bold) }
        }
        // (링크 아이콘 없음 — 링크 있는 항목은 제목이 강조색이고 제목 탭으로 브라우저 열림)
        // 담당자(할 일) 또는 추가한 사람(장보기)
        MemberTagDots(rightIds)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, "삭제", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

/** 쿠팡 장바구니를 연다: 앱이 있으면 쿠팡 앱으로 강제(웹뷰/네이티브 장바구니), 없으면 브라우저 폴백. */
private fun openCoupangCart(context: android.content.Context) {
    val pkg = "com.coupang.mobile"
    val cart = "https://cart.coupang.com/cartView.pang"
    val attempts = listOf(
        // 1) 장바구니 URL을 쿠팡 앱으로 강제
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(cart)).setPackage(pkg),
        // 2) 쿠팡 앱 스킴(인앱 웹뷰로 장바구니)
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("coupang://web?url=" + android.net.Uri.encode(cart))).setPackage(pkg),
        // 3) 웹 폴백(앱 미설치)
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(cart)),
    )
    for (i in attempts) {
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(i); true }.getOrDefault(false)) return
    }
}

/** 링크를 기본 브라우저(또는 해당 앱)로 연다. */
private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        val u = if (url.startsWith("http")) url else "https://$url"
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 항목의 담당자를 색상 원(이니셜)으로 표시. 공용이면 회색 '공용'. */
@Composable
private fun MemberTagDots(memberIds: List<String>) {
    if (memberIds.isEmpty() || memberIds.contains(Family.ALL_ID)) {
        Box(
            Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFECECEC))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) { Text("공용", fontSize = 11.sp, color = Color(0xFF777777)) }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        memberIds.take(3).forEach { id ->
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(Family.colorOf(id)),
                contentAlignment = Alignment.Center,
            ) {
                Text(Family.initialOf(id), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

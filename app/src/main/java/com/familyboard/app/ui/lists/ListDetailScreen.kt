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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    val board = remember(boardKey) { BoardType.fromKey(boardKey) }
    val items by vm.itemsFor(boardKey).collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var tagIds by remember { mutableStateOf(listOf(Family.ALL_ID)) }

    fun add() {
        if (input.isBlank()) return
        vm.addListItemWithNotify(
            ListItem(
                text = input.trim(),
                checked = false,
                board = board.key,
                createdBy = currentMemberId ?: Family.ALL_ID,
                memberIds = tagIds,
            ),
            board.title,
        )
        input = ""
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(board.title) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // 담당자 태깅 선택
                    Text("담당", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.size(4.dp))
                    MemberChips(selected = tagIds, onSelect = { tagIds = it })
                    Spacer(Modifier.size(8.dp))
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
                        onToggle = { vm.toggleItem(itm.id, it) },
                        onDelete = { vm.deleteItem(itm.id) },
                    )
                }
            }
        }
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
            Chip(m.name, m.color, selected.contains(m.id)) {
                val cur = selected.filter { it != Family.ALL_ID }.toMutableList()
                if (cur.contains(m.id)) cur.remove(m.id) else cur.add(m.id)
                onSelect(if (cur.isEmpty()) listOf(Family.ALL_ID) else cur)
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
private fun ItemRow(item: ListItem, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
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
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
            color = if (item.checked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.onSurface,
        )
        // 담당자 태그
        MemberTagDots(item.memberIds)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, "삭제", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
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
                Text(Family.nameOf(id).take(1), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

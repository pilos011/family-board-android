package com.familyboard.app.ui.allowance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.AllowanceBoards
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.ui.AppViewModel
import kotlinx.coroutines.flow.StateFlow

private val Ink = Color(0xFF2B2B2E)

@Composable
fun AllowanceScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AllowanceSection(
            vm = vm, name = "준영", memberId = "junyoung",
            boardKey = AllowanceBoards.JUNYOUNG, itemsFlow = vm.allowanceJunyoung,
            modifier = Modifier.weight(1f),
        )
        Divider(thickness = 8.dp, color = MaterialTheme.colorScheme.background)
        AllowanceSection(
            vm = vm, name = "준호", memberId = "junho",
            boardKey = AllowanceBoards.JUNHO, itemsFlow = vm.allowanceJunho,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AllowanceSection(
    vm: AppViewModel,
    name: String,
    memberId: String,
    boardKey: String,
    itemsFlow: StateFlow<List<ListItem>>,
    modifier: Modifier = Modifier,
) {
    val items by itemsFlow.collectAsStateWithLifecycle()
    val outstanding = items.filter { !it.checked }.sumOf { it.amount }
    var titleInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ListItem?>(null) }

    fun add() {
        val amt = amountInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
        if (titleInput.isBlank() && amt == 0L) return
        vm.addItem(
            ListItem(
                text = titleInput.trim(), checked = false,
                board = boardKey, createdBy = memberId, amount = amt,
            )
        )
        titleInput = ""; amountInput = ""
    }

    Column(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
    ) {
        // 헤더: 이름 + 정산 요청 합계
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Family.colorOf(memberId)),
                contentAlignment = Alignment.Center) {
                Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(10.dp))
            Text(name, style = MaterialTheme.typography.titleLarge, color = Ink)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("정산 요청", fontSize = 12.sp, color = Ink.copy(alpha = 0.5f))
                Text(formatWon(outstanding), fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (items.isEmpty()) {
                item {
                    Text("항목이 없어요. 아래에서 추가하세요.",
                        color = Ink.copy(alpha = 0.45f),
                        modifier = Modifier.padding(vertical = 10.dp))
                }
            }
            items(items, key = { it.id }) { itm ->
                ItemRow(
                    item = itm,
                    onToggle = { vm.toggleItem(itm.id, it) },
                    onEdit = { editing = itm },
                    onDelete = { vm.deleteItem(itm.id) },
                )
            }
        }

        // 추가 입력
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = titleInput, onValueChange = { titleInput = it },
                placeholder = { Text("항목 (예: 점심값)") },
                modifier = Modifier.weight(1f), singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.size(6.dp))
            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it.filter { c -> c.isDigit() }.take(9) },
                placeholder = { Text("금액") },
                modifier = Modifier.width(110.dp), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
            )
            IconButton(onClick = { add() }) {
                Icon(Icons.Default.Add, "추가", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    editing?.let { item ->
        EditDialog(
            item = item,
            onSave = { t, a -> vm.updateItem(item.copy(text = t, amount = a)); editing = null },
            onDelete = { vm.deleteItem(item.id); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ItemRow(item: ListItem, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onEdit() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = onToggle)
        Text(
            item.text.ifBlank { "(제목 없음)" },
            modifier = Modifier.weight(1f),
            color = if (item.checked) Ink.copy(alpha = 0.4f) else Ink,
            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            formatWon(item.amount),
            modifier = Modifier.width(96.dp),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.SemiBold,
            color = if (item.checked) Ink.copy(alpha = 0.4f) else Ink,
        )
        // 정산 완료(체크)된 항목은 금액 오른쪽에 삭제 아이콘. 미완료는 자리만 확보해 정렬 유지.
        if (item.checked) {
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Delete, "삭제", tint = Color(0xFFE03131))
            }
        } else {
            Spacer(Modifier.size(40.dp))
        }
    }
}

@Composable
private fun EditDialog(
    item: ListItem,
    onSave: (String, Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var t by remember(item.id) { mutableStateOf(item.text) }
    var a by remember(item.id) { mutableStateOf(if (item.amount == 0L) "" else item.amount.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("항목 수정") },
        text = {
            Column {
                OutlinedTextField(
                    value = t, onValueChange = { t = it }, label = { Text("항목") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = a, onValueChange = { a = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("금액") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(t.trim(), a.toLongOrNull() ?: 0L) }) { Text("저장") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.size(4.dp)); Text("삭제")
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

/** 천단위 콤마 + "원" */
private fun formatWon(n: Long): String = "%,d원".format(n)

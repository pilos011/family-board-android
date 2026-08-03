package com.familyboard.app.ui.allowance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
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
    val me by vm.currentMemberId.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AllowanceSection(
            vm = vm, name = "준영", memberId = "junyoung", currentMemberId = me,
            boardKey = AllowanceBoards.JUNYOUNG, itemsFlow = vm.allowanceJunyoung,
            modifier = Modifier.weight(1f),
        )
        Divider(thickness = 8.dp, color = MaterialTheme.colorScheme.background)
        AllowanceSection(
            vm = vm, name = "준호", memberId = "junho", currentMemberId = me,
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
    currentMemberId: String?,
    boardKey: String,
    itemsFlow: StateFlow<List<ListItem>>,
    modifier: Modifier = Modifier,
) {
    val items by itemsFlow.collectAsStateWithLifecycle()
    val outstanding = items.filter { !it.checked }.sumOf { it.amount }
    val checkedItems = items.filter { it.checked }
    val settleAmount = checkedItems.sumOf { it.amount }
    // 현재 사용자가 부모(선일/은선)면 [정산], 자녀(준영/준호)면 [조르기]
    val isParent = currentMemberId == "seonil" || currentMemberId == "eunseon"
    var titleInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ListItem?>(null) }
    var showSettle by remember { mutableStateOf(false) }
    var showNudge by remember { mutableStateOf(false) }

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
            Spacer(Modifier.size(10.dp))
            if (isParent) {
                FilledTonalButton(
                    onClick = { showSettle = true },
                    enabled = checkedItems.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                ) { Text("정산", fontWeight = FontWeight.SemiBold) }
            } else if (currentMemberId == memberId) {
                // 자녀는 본인 섹션에서만 조르기 가능
                // 전체 체크 토글: 모두 체크돼 있으면 해제, 아니면 전체 체크
                val allChecked = items.isNotEmpty() && items.all { it.checked }
                IconButton(
                    onClick = { vm.setCheckedAll(items, !allChecked) },
                    enabled = items.isNotEmpty(),
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Default.DoneAll,
                        if (allChecked) "전체 해제" else "전체 체크",
                        tint = if (allChecked) MaterialTheme.colorScheme.primary else Ink.copy(alpha = 0.55f),
                    )
                }
                Spacer(Modifier.size(6.dp))
                FilledTonalButton(
                    onClick = { showNudge = true },
                    enabled = checkedItems.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                ) { Text("조르기", fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("정산 대기 잔액", fontSize = 11.sp, color = Ink.copy(alpha = 0.5f))
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

        // 추가 입력 (컴팩트 높이)
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactField(
                value = titleInput, onValueChange = { titleInput = it },
                placeholder = "항목 (예: 점심값)",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(6.dp))
            CompactField(
                value = amountInput,
                onValueChange = { amountInput = it.filter { c -> c.isDigit() }.take(9) },
                placeholder = "금액",
                modifier = Modifier.width(110.dp),
                keyboardType = KeyboardType.Number,
            )
            IconButton(onClick = { add() }) {
                Icon(Icons.Default.Add, "추가", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (showSettle) {
        AlertDialog(
            onDismissRequest = { showSettle = false },
            title = { Text("용돈 정산") },
            text = {
                Text(
                    "$name 에게 정산 알림을 보낼까요?\n\n" +
                        "· 금액: ${formatWon(settleAmount)}\n" +
                        "· 체크된 ${checkedItems.size}개 항목은 목록에서 삭제됩니다.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.settleAllowance(memberId, checkedItems)
                    showSettle = false
                }) { Text("보내기") }
            },
            dismissButton = { TextButton(onClick = { showSettle = false }) { Text("취소") } },
        )
    }

    if (showNudge) {
        AlertDialog(
            onDismissRequest = { showNudge = false },
            title = { Text("조르기") },
            text = {
                Text(
                    "엄마에게 정산 누적금액 정산을 조릅니다.\n\n" +
                        "· 금액: ${formatWon(settleAmount)}\n" +
                        "· 체크한 ${checkedItems.size}개 항목 (삭제되지 않아요)",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.nudgeAllowance(memberId, name, checkedItems)
                    showNudge = false
                }) { Text("조르기") }
            },
            dismissButton = { TextButton(onClick = { showNudge = false }) { Text("취소") } },
        )
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

/** 기본 OutlinedTextField(56dp)보다 약 25% 낮은(42dp) 컴팩트 입력 필드. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val interaction = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(42.dp),
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = Ink, fontSize = 15.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        interactionSource = interaction,
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = inner,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                placeholder = { Text(placeholder, fontSize = 15.sp, color = Ink.copy(alpha = 0.4f)) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                container = {
                    OutlinedTextFieldDefaults.ContainerBox(
                        enabled = true,
                        isError = false,
                        interactionSource = interaction,
                        colors = OutlinedTextFieldDefaults.colors(),
                        shape = RoundedCornerShape(12.dp),
                    )
                },
            )
        },
    )
}

/** 천단위 콤마 + "원" */
private fun formatWon(n: Long): String = "%,d원".format(n)

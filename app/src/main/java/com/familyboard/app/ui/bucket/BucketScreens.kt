package com.familyboard.app.ui.bucket

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familyboard.app.data.BucketLife
import com.familyboard.app.data.Family
import com.familyboard.app.data.model.BucketBoards
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.ui.AppViewModel

private val Ink = Color(0xFF2B2B2E)
private val Purple = Color(0xFF845EF7)

/** 대표 페이지: 남은 날 박스 + '꼭 하자!' 항목 10개 + 눈 버튼 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BucketHomeScreen(
    vm: AppViewModel,
    currentMemberId: String?,
    onOpenList: () -> Unit,
    onBack: () -> Unit,
) {
    val me = currentMemberId ?: "seonil"
    val items by vm.bucketItemsFor(me).collectAsStateWithLifecycle()
    val featured = remember(items) { items.filter { it.mustDo }.take(10) }
    val stats = remember(me) { BucketLife.stats(me) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("인생 버킷 리스트") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenList, containerColor = Purple) {
                Icon(Icons.Default.Visibility, "버킷 목록 보기", tint = Color.White)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(20.dp)) {
            if (stats != null) LifeBox(stats)
            Spacer(Modifier.height(20.dp))
            Text("꼭 하자! 목록", style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(8.dp))
            if (featured.isEmpty()) {
                Text(
                    "아직 '꼭 하자!'로 표시한 항목이 없어요.\n오른쪽 아래 버튼으로 버킷 목록을 열어 표시해 보세요.",
                    color = Ink.copy(alpha = 0.5f),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(featured, key = { it.id }) { itm ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Purple.copy(alpha = 0.10f)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Star, null, tint = Purple, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(10.dp))
                            Text(
                                itm.text,
                                Modifier.weight(1f),
                                color = if (itm.checked) Ink.copy(alpha = 0.4f) else Ink,
                                textDecoration = if (itm.checked) TextDecoration.LineThrough else TextDecoration.None,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LifeBox(stats: com.familyboard.app.data.LifeStats) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Purple.copy(alpha = 0.12f)).padding(20.dp),
    ) {
        Text("나의 남은 날", fontSize = 14.sp, color = Ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))
        Text("%,d 일".format(stats.remaining), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Purple)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("지나온 날", "%,d 일".format(stats.passed))
            Stat("진행률", "%.1f%%".format(stats.progressPercent))
            Stat("올해 남은 날", "${stats.remainingThisYear}일")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Ink.copy(alpha = 0.5f))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

/** 버킷 목록: 사람 전환(배우자 공유) + 항목 카드 + (+) 추가 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BucketListScreen(
    vm: AppViewModel,
    currentMemberId: String?,
    onBack: () -> Unit,
) {
    val me = currentMemberId ?: "seonil"
    var person by remember { mutableStateOf(me) }
    val items by vm.bucketItemsFor(person).collectAsStateWithLifecycle()
    val sorted = remember(items) { items.sortedBy { it.checked } }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("버킷 목록") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Purple) {
                Icon(Icons.Default.Add, "버킷 추가", tint = Color.White)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // 사람 전환 (선일/은선)
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("seonil", "eunseon").forEach { id ->
                    val on = person == id
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (on) Purple else Color(0xFFF1F3F5))
                            .clickable { person = id }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${Family.nameOf(id)}의 버킷",
                            color = if (on) Color.White else Color(0xFF555555),
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            if (sorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("버킷 항목이 없어요.\n오른쪽 아래 +로 추가해 보세요.", color = Ink.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sorted, key = { it.id }) { itm ->
                        BucketRow(
                            item = itm,
                            onToggle = { vm.toggleItem(itm.id, it) },
                            onMustDo = { vm.updateItem(itm.copy(mustDo = !itm.mustDo)) },
                            onDelete = { vm.deleteItem(itm.id) },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("${Family.nameOf(person)}의 버킷 추가") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("예: 오로라 보러 가기") }, singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        vm.addItem(
                            ListItem(
                                text = text.trim(), checked = false,
                                board = BucketBoards.of(person), createdBy = me,
                            )
                        )
                    }
                    showAdd = false
                }) { Text("추가") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun BucketRow(
    item: ListItem,
    onToggle: (Boolean) -> Unit,
    onMustDo: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.checked, onCheckedChange = onToggle)
        Text(
            item.text,
            Modifier.weight(1f),
            color = if (item.checked) Ink.copy(alpha = 0.4f) else Ink,
            textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
        )
        MustDoChip(on = item.mustDo, onClick = onMustDo)
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Close, "삭제", tint = Ink.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MustDoChip(on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (on) Purple else Color(0xFFF1F3F5))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Star, null,
            tint = if (on) Color.White else Color(0xFFAAAAAA),
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            "꼭 하자!",
            color = if (on) Color.White else Color(0xFF888888),
            fontSize = 12.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

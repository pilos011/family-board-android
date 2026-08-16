package com.familyboard.app.ui.lists

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.familyboard.app.data.model.CouponBoard
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.ui.AppViewModel

/**
 * 가족 쿠폰함: 재미진 곳/내 재미진 곳에서 '가족 쿠폰함으로 이동'으로 담긴 쿠폰(이미지/링크/텍스트) 목록.
 * 탭=열기(이미지 뷰어·링크 열기·텍스트 복사). '사용완료'=회색 흐림+탭 불가(누른 본인만 취소). 관리자(선일·은선) 삭제.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponScreen(vm: AppViewModel, currentMemberId: String?, onBack: () -> Unit) {
    val items by vm.couponItems.collectAsStateWithLifecycle()
    val me = currentMemberId.orEmpty()
    val isAdmin = CouponBoard.canDelete(currentMemberId)
    val context = LocalContext.current
    var viewer by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ListItem?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(CouponBoard.TITLE) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
        )
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "아직 쿠폰이 없어요.\n재미진 곳/내 재미진 곳에서\n'가족 쿠폰함으로 이동'으로 담아보세요.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                )
            }
            else -> {
                val sorted = remember(list) { list.sortedByDescending { it.createdAt } }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    items(sorted, key = { it.id }) { item ->
                        CouponCard(
                            item = item, me = me, isAdmin = isAdmin,
                            onOpen = {
                                val url = item.photoUrls.firstOrNull().orEmpty()
                                when {
                                    // 링크 우선(웹/유튜브 쿠폰은 썸네일도 있으므로 이미지보다 링크 먼저 열기).
                                    item.link.isNotBlank() -> runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)))
                                    }.onFailure { Toast.makeText(context, "링크를 열 수 없어요", Toast.LENGTH_SHORT).show() }
                                    url.isNotBlank() -> viewer = url // 이미지 쿠폰: 원본 뷰어(코드 잘 보이게)
                                    item.text.isNotBlank() -> {
                                        val cm = context.getSystemService(ClipboardManager::class.java)
                                        cm?.setPrimaryClip(ClipData.newPlainText("coupon", item.text))
                                        Toast.makeText(context, "쿠폰 내용을 복사했어요", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onToggleUsed = { vm.toggleCouponUsed(item) },
                            onDelete = { pendingDelete = item },
                        )
                    }
                }
            }
        }
    }

    viewer?.let { ZoomOverlay(it) { viewer = null } } // FunListScreen 의 확대 뷰어 재사용

    pendingDelete?.let { it0 ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("쿠폰 삭제") },
            text = { Text("이 쿠폰을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteItem(it0.id); pendingDelete = null }) {
                    Text("삭제", color = Color(0xFFE03131))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun CouponCard(
    item: ListItem, me: String, isAdmin: Boolean,
    onOpen: () -> Unit, onToggleUsed: () -> Unit, onDelete: () -> Unit,
) {
    val used = item.checked
    val canCancel = used && item.usedBy == me // 사용완료 누른 본인만 취소 가능
    val url = item.photoUrls.firstOrNull().orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 내용 — 사용완료면 탭 불가(열리지 않음)
            Box(Modifier.fillMaxSize().let { if (!used) it.clickable { onOpen() } else it }) {
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = funThumbUrl(url), contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(Modifier.fillMaxSize().padding(14.dp)) {
                        Text(
                            if (item.link.isNotBlank()) "🔗 링크 쿠폰" else "🎟️ 쿠폰",
                            fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.text.ifBlank { item.link }, fontSize = 13.sp,
                            maxLines = 6, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // 이미지+링크 쿠폰: 탭하면 링크가 열린다는 표시(좌상단 🔗 배지).
            if (url.isNotBlank() && item.link.isNotBlank() && !used) {
                Text(
                    "🔗", fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .background(Color(0x66000000), CircleShape).padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
            // 사용완료: 회색 흐림 (버튼·삭제 아이콘보다 먼저 그려 그 위로 버튼이 오게)
            if (used) {
                Box(Modifier.fillMaxSize().background(Color(0xCCB0B0B0)), Alignment.Center) {
                    Text("사용완료", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            // 관리자 삭제(우상단) — 밝은 카드에서도 보이게 어두운 원 배경.
            if (isAdmin) {
                IconButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Icon(
                        Icons.Default.Delete, "삭제", tint = Color.White,
                        modifier = Modifier.background(Color(0x66000000), CircleShape).padding(4.dp),
                    )
                }
            }
            // 하단 사용완료/취소 버튼 (흐림 위에 그려 항상 조작 가능)
            val label = when { !used -> "사용완료"; canCancel -> "사용 취소"; else -> "사용됨" }
            val btnColor = if (used) Color(0xFF868E96) else Color(0xFF0CA678)
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(btnColor)
                    .let { if (!used || canCancel) it.clickable { onToggleUsed() } else it }
                    .padding(vertical = 9.dp),
                Alignment.Center,
            ) {
                Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

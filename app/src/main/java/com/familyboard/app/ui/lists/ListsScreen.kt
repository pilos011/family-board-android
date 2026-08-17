package com.familyboard.app.ui.lists

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.familyboard.app.R
import com.familyboard.app.data.BucketLife
import com.familyboard.app.data.model.BoardType
import com.familyboard.app.data.model.ListItem
import com.familyboard.app.data.model.PlaceBoards
import com.familyboard.app.ui.AppViewModel
import com.familyboard.app.ui.bucket.BucketIcons
import com.familyboard.app.ui.theme.ShoppingBlue
import com.familyboard.app.ui.theme.TodoGreen

@Composable
fun ListsScreen(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    onOpenBoard: (String) -> Unit,
    onOpenBucket: () -> Unit,
    onOpenDday: () -> Unit,
    onOpenPlace: (String) -> Unit,
    onOpenFun: () -> Unit,
    onOpenMyFun: () -> Unit,
    onOpenRecipe: () -> Unit,
    onOpenDocs: () -> Unit,
    onOpenAlbum: () -> Unit,
    onOpenMyAlbum: () -> Unit,
    onOpenCoupon: () -> Unit = {},
    onOpenTravel: () -> Unit = {},
) {
    val shopping by vm.shoppingItems.collectAsStateWithLifecycle()
    val todo by vm.todoItems.collectAsStateWithLifecycle()
    val restaurant by vm.restaurantItems.collectAsStateWithLifecycle()
    val visit by vm.visitItems.collectAsStateWithLifecycle()
    val funCount by vm.funCount.collectAsStateWithLifecycle()
    val myFunCount by vm.myFunCount.collectAsStateWithLifecycle()
    val recipeCount by vm.recipeCount.collectAsStateWithLifecycle()
    val currentMemberId by vm.currentMemberId.collectAsStateWithLifecycle()
    val docs by vm.docItems.collectAsStateWithLifecycle()
    val docCount = docs?.count { com.familyboard.app.data.model.DocBoard.visibleTo(it, currentMemberId) } ?: 0
    // 사진첩 개수는 count 쿼리로만(전체 로드 없이 — Postgres 이관). 화면 진입 시 갱신.
    val albumCount by vm.albumCountFlow.collectAsStateWithLifecycle()
    val myAlbumCount by vm.myAlbumCountFlow.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadAlbumCounts() }
    val coupons by vm.couponItems.collectAsStateWithLifecycle()
    val couponCount = coupons?.count { !it.checked } ?: 0 // 아직 안 쓴 쿠폰 수
    val travels by vm.travelItems.collectAsStateWithLifecycle()
    val travelCount = travels?.count { !it.checked } ?: 0 // 아직 안 가본 곳 수
    val showBucket = BucketLife.supports(currentMemberId)
    val spouse = BucketLife.spouseName(currentMemberId)
    val customLists by vm.customLists.collectAsStateWithLifecycle()
    val myLists = customLists.filter { it.createdBy == currentMemberId }
    var showCreate by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text("리스트", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))
            if (showBucket) {
                BucketWideCard(spouseName = spouse, onClick = onOpenBucket)
                Spacer(Modifier.height(16.dp))
            }
            // 4개 카테고리를 한 줄(버킷 카드와 같은 118dp 높이)로 컴팩트하게
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactBoardCard("장보기", shopping.size, ShoppingBlue, Icons.Default.ShoppingCart, Modifier.weight(1f)) { onOpenBoard(BoardType.SHOPPING.key) }
                CompactBoardCard("할 일", todo.size, TodoGreen, Icons.Default.CheckCircle, Modifier.weight(1f)) { onOpenBoard(BoardType.TODO.key) }
                CompactBoardCard("맛집", restaurant?.size ?: 0, RestaurantColor, Icons.Default.Restaurant, Modifier.weight(1f)) { onOpenPlace(PlaceBoards.RESTAURANT) }
                CompactBoardCard("가볼 곳", visit?.size ?: 0, VisitColor, Icons.Default.Place, Modifier.weight(1f)) { onOpenPlace(PlaceBoards.VISIT) }
            }
            Spacer(Modifier.height(10.dp))
            // 둘째 행: 재미진 곳(공용) · 내 재미진 곳(나만) · 가족 공유 문서함
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactBoardCard("재미진 곳", funCount, FunColor, Icons.Default.PlayCircle, Modifier.weight(1f)) { onOpenFun() }
                CompactBoardCard("내 재미진 곳", myFunCount, MyFunColor, Icons.Default.Lock, Modifier.weight(1f)) { onOpenMyFun() }
                CompactBoardCard("문서함", docCount, DocsColor, Icons.Default.FolderShared, Modifier.weight(1f)) { onOpenDocs() }
                CompactBoardCard("요리 레시피", recipeCount, RecipeColor, Icons.Default.RestaurantMenu, Modifier.weight(1f)) { onOpenRecipe() }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AlbumHalfCard(
                    "가족 사진첩", if (albumCount > 0) "추억 ${albumCount}장" else "가족 사진 모으기",
                    Color(0xFFE8590C), Icons.Default.PhotoLibrary, Modifier.weight(1f), onOpenAlbum,
                )
                AlbumHalfCard(
                    "내 사진첩", if (myAlbumCount > 0) "내 사진 ${myAlbumCount}장" else "나만의 사진첩",
                    Color(0xFF7C5CFA), Icons.Default.Lock, Modifier.weight(1f), onOpenMyAlbum,
                )
            }
            Spacer(Modifier.height(16.dp))
            // D-Day를 가족 사진 카드 크기(반쪽)로 축소하고 오른쪽에 가족 쿠폰함 카드.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AlbumHalfCard(
                    "D-Day", "기념일·생일 카운트다운",
                    Color(0xFF5C7CFA), Icons.Default.HourglassBottom, Modifier.weight(1f), onOpenDday,
                )
                AlbumHalfCard(
                    "가족 쿠폰함", if (couponCount > 0) "쿠폰 ${couponCount}장" else "쿠폰 모으기",
                    Color(0xFF0CA678), Icons.Default.ConfirmationNumber, Modifier.weight(1f), onOpenCoupon,
                )
            }
            Spacer(Modifier.height(16.dp))
            AlbumHalfCard(
                "여행 위시리스트", if (travelCount > 0) "가고 싶은 곳 ${travelCount}곳" else "구글 지도로 여행지 모으기",
                Color(0xFF1098AD), Icons.Default.TravelExplore, Modifier.fillMaxWidth(), onOpenTravel,
            )

            // 내가 만든 나만의 체크리스트
            myLists.forEach { list ->
                Spacer(Modifier.height(16.dp))
                CustomListCard(list = list, onClick = { onOpenBoard(list.id) })
            }
            Spacer(Modifier.height(90.dp)) // FAB 여백
        }

        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
        ) { Icon(Icons.Default.Add, "새 비공유 체크리스트", tint = Color.White) }
    }

    if (showCreate) {
        CreateListDialog(
            onCreate = { name, icon ->
                vm.addItem(
                    ListItem(text = name, icon = icon, board = "customlists",
                        createdBy = currentMemberId ?: "")
                )
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomListCard(list: ListItem, onClick: () -> Unit) {
    val accent = Color(0xFF9775FA)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(84.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(BucketIcons.of(list.icon) ?: Icons.Default.Checklist, null, tint = accent) }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(list.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("나만 보는 체크리스트", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateListDialog(onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 비공유 체크리스트") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("리스트 이름") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text("아이콘 선택", fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().heightIn(max = 190.dp).verticalScroll(rememberScrollState())) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BucketIcons.all.forEach { (key, iv) ->
                            val on = key == icon
                            Box(
                                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (on) Color(0xFF9775FA) else Color(0xFFF1F3F5))
                                    .border(if (on) 0.dp else 1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                                    .clickable { icon = if (icon == key) "" else key },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(iv, key, tint = if (on) Color.White else Color(0xFF555555),
                                    modifier = Modifier.size(21.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), icon) }) { Text("만들기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumHalfCard(
    title: String,
    subtitle: String,
    container: Color,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(96.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(7.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.weight(1f))
            Text(subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BucketWideCard(spouseName: String?, onClick: () -> Unit) {
    val title = if (spouseName != null) "${spouseName}과 함께하는\n인생 버킷 리스트" else "인생 버킷 리스트"
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(118.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 배경 사진(노을·산 일러스트)
            Image(
                painter = painterResource(R.drawable.bucket_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 글자 가독성용 어둡게 처리
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.15f)),
                    ),
                ),
            )
            Row(
                Modifier.fillMaxSize().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("남은 날 안에 같이 꼭 하고 싶은 것들", color = Color.White.copy(alpha = 0.92f),
                        fontWeight = FontWeight.Medium)
                }
                Icon(painterResource(R.drawable.ic_bucket), null, tint = Color.White,
                    modifier = Modifier.height(46.dp))
            }
        }
    }
}

private val RestaurantColor = Color(0xFFFF922B)
private val VisitColor = Color(0xFF22B8CF)
private val FunColor = Color(0xFFF06595)
private val MyFunColor = Color(0xFF9775FA)
private val DocsColor = Color(0xFF3B5BDB)
private val RecipeColor = Color(0xFFE8590C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactBoardCard(
    title: String,
    count: Int,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(118.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp,
                lineHeight = 14.sp, maxLines = 2, textAlign = TextAlign.Center)
            Spacer(Modifier.height(2.dp))
            Text("${count}개", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
        }
    }
}

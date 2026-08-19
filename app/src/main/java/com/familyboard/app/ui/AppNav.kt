package com.familyboard.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.familyboard.app.ui.calendar.AddEventScreen
import com.familyboard.app.ui.calendar.CalendarScreen
import com.familyboard.app.ui.calendar.ViewEventScreen
import com.familyboard.app.ui.allowance.AllowanceScreen
import com.familyboard.app.ui.bucket.BucketAddScreen
import com.familyboard.app.ui.bucket.BucketHomeScreen
import com.familyboard.app.ui.bucket.BucketListScreen
import com.familyboard.app.ui.bucket.BucketViewScreen
import com.familyboard.app.ui.dday.DDayScreen
import com.familyboard.app.ui.emergency.EmergencySendScreen
import com.familyboard.app.ui.home.HomeScreen
import com.familyboard.app.data.model.FunBoard
import com.familyboard.app.data.model.PlaceBoards
import com.familyboard.app.ui.lists.DocListScreen
import com.familyboard.app.ui.lists.FunListScreen
import com.familyboard.app.ui.lists.ListDetailScreen
import com.familyboard.app.ui.lists.PlaceListScreen
import com.familyboard.app.ui.manage.ManageScreen
import com.familyboard.app.ui.lists.ListsScreen
import com.familyboard.app.ui.onboarding.OnboardingScreen
import com.familyboard.app.ui.search.SearchScreen

private object Routes {
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val LISTS = "lists"
    const val ALLOWANCE = "allowance"
    const val MANAGE = "manage"
    const val EMERGENCY = "emergency"
    const val ADD_EVENT = "addEvent/{startIso}/{endIso}"
    const val EDIT_EVENT = "editEvent/{eventId}"
    const val VIEW_EVENT = "viewEvent/{eventId}/{dateIso}"
    const val LIST_DETAIL = "listDetail/{board}"
    const val PLACE = "place/{board}"
    const val FUN = "fun"
    const val MYFUN = "myfun"
    const val RECIPE = "recipe"
    const val DOCS = "docs"
    const val ALBUM = "album"                     // 사진첩(인자 없이 이동)
    const val ALBUM_ROUTE = "album?photo={photo}" // 컴포저블 등록용(사진 인자 선택). "album" 도 이 라우트에 매칭
    fun album(photoId: String) = "album?photo=$photoId" // 특정 사진 뷰어를 바로 여는 이동(홈 '그날의 추억')
    const val MYALBUM = "myalbum"
    const val COUPON = "coupon"
    const val TRAVEL = "travel"
    const val DDAY = "dday"
    const val BUCKET_HOME = "bucketHome"
    const val BUCKET_LIST = "bucketList"
    const val BUCKET_ADD = "bucketAdd"
    const val BUCKET_EDIT = "bucketEdit/{itemId}"
    const val BUCKET_VIEW = "bucketView/{itemId}"
    fun bucketEdit(itemId: String) = "bucketEdit/$itemId"
    fun bucketView(itemId: String) = "bucketView/$itemId"
    const val SEARCH = "search"
    fun addEvent(startIso: String, endIso: String) = "addEvent/$startIso/$endIso"
    fun editEvent(eventId: String) = "editEvent/$eventId"
    fun viewEvent(eventId: String, dateIso: String) = "viewEvent/$eventId/$dateIso"
    fun listDetail(board: String) = "listDetail/$board"
    fun place(board: String) = "place/$board"
}

@Composable
fun AppRoot() {
    val vm: AppViewModel = viewModel()
    val userState by vm.userState.collectAsStateWithLifecycle()

    when (userState) {
        UserState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        UserState.NeedSelect -> OnboardingScreen(onSelected = vm::selectMember)
        is UserState.Selected -> MainScaffold(vm)
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route == Routes.HOME || route == Routes.CALENDAR || route == Routes.LISTS ||
        route == Routes.ALLOWANCE || route == Routes.MANAGE
    // 하단바 선택 표시는 '클릭 즉시' 반영. route(currentBackStackEntry)는 전환 애니메이션 뒤에 갱신돼
    // 선택 표시가 늦게 뜨므로, 탭 누르는 순간 selectedTab 을 바꾸고 route 로는 사후 동기화만(뒤로가기 등).
    var selectedTab by remember { mutableStateOf(route ?: Routes.HOME) }
    LaunchedEffect(route) {
        if (route == Routes.HOME || route == Routes.CALENDAR || route == Routes.LISTS ||
            route == Routes.ALLOWANCE || route == Routes.MANAGE
        ) selectedTab = route!!
    }
    val currentMemberId by vm.currentMemberId.collectAsStateWithLifecycle()
    val isParent = currentMemberId == "seonil" || currentMemberId == "eunseon"
    val pendingShare by vm.pendingShare.collectAsStateWithLifecycle()
    val docShare by vm.pendingDoc.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 위젯 탭 → 해당 보드 화면으로 이동(맛집/가볼곳=place, 장보기 등=listDetail). 한 번 이동 후 clear.
    val pendingWidgetNav by vm.pendingWidgetNav.collectAsStateWithLifecycle()
    LaunchedEffect(pendingWidgetNav) {
        val board = pendingWidgetNav ?: return@LaunchedEffect
        when (board) {
            // 달력은 하단 탭 → 홈 기준(navigateShare). 맛집/가볼곳/장보기 등은 리스트 카드 →
            // 뒤로가기 시 리스트 메인으로 가도록 [HOME, LISTS, 대상] 백스택.
            Routes.CALENDAR -> nav.navigateShare(Routes.CALENDAR)
            PlaceBoards.RESTAURANT, PlaceBoards.VISIT -> nav.navigateShareUnderLists(Routes.place(board))
            else -> nav.navigateShareUnderLists(Routes.listDetail(board))
        }
        vm.clearWidgetNav()
    }

    // 업데이트 확인은 HomeScreen 의 ON_RESUME(진입/포그라운드 복귀)에서 수행 → 여기선 안 함.
    // (route 변경 기반은 앱이 계속 살아있으면 같은 화면 복귀 때 재실행이 안 됨)

    // 공유받은 재미진 항목(알림 탭): 재미진 곳 화면으로 이동 → FunListScreen 이 그 항목을 열고 clear.
    val pendingSharedFun by vm.pendingSharedFun.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSharedFun) { if (pendingSharedFun != null) nav.navigateShareUnderLists(Routes.FUN) }

    // 업데이트 요청 알림 탭: 홈으로 이동 → HomeScreen 이 업데이트 창 자동 표시.
    val pendingOpenUpdate by vm.pendingOpenUpdate.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenUpdate) { if (pendingOpenUpdate) nav.navigateShare(Routes.HOME) }

    // 용돈 미션(업데이트 챌린지) 성공: 용돈 화면으로 이동 + 성공 토스트.
    val challengeSuccess by vm.pendingChallengeSuccess.collectAsStateWithLifecycle()
    LaunchedEffect(challengeSuccess) {
        if (challengeSuccess) {
            nav.navigateShare(Routes.ALLOWANCE)
            Toast.makeText(context, "업데이트 챌린지 성공! 🎉", Toast.LENGTH_LONG).show()
            vm.clearChallengeSuccess()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == Routes.HOME,
                        onClick = { selectedTab = Routes.HOME; nav.navigateTab(Routes.HOME) },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("홈") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == Routes.CALENDAR,
                        onClick = { selectedTab = Routes.CALENDAR; nav.navigateTab(Routes.CALENDAR) },
                        icon = { Icon(Icons.Default.CalendarMonth, null) },
                        label = { Text("가족 달력") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == Routes.LISTS,
                        onClick = { selectedTab = Routes.LISTS; nav.navigateTab(Routes.LISTS) },
                        icon = { Icon(Icons.Default.Checklist, null) },
                        label = { Text("리스트") },
                    )
                    NavigationBarItem(
                        selected = selectedTab == Routes.ALLOWANCE,
                        onClick = { selectedTab = Routes.ALLOWANCE; nav.navigateTab(Routes.ALLOWANCE) },
                        icon = { Icon(Icons.Default.Savings, null) },
                        label = { Text("용돈 정산") },
                    )
                    if (isParent) {
                        NavigationBarItem(
                            selected = selectedTab == Routes.MANAGE,
                            onClick = { selectedTab = Routes.MANAGE; nav.navigateTab(Routes.MANAGE) },
                            icon = { Icon(Icons.Default.Tune, null) },
                            label = { Text("관리 기능") },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(inner),
            // 메뉴 간 전환 페이드를 기본 700ms → 350ms 로 절반 단축
            enterTransition = { fadeIn(tween(350)) },
            exitTransition = { fadeOut(tween(350)) },
            popEnterTransition = { fadeIn(tween(350)) },
            popExitTransition = { fadeOut(tween(350)) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    onOpenEvent = { id, dateIso -> nav.navigate(Routes.viewEvent(id, dateIso)) },
                    onOpenDday = { nav.navigate(Routes.DDAY) },
                    onOpenNotice = { nav.navigate(Routes.listDetail("notice")) },
                    canManageNotice = isParent,
                    onOpenMemory = { id -> nav.navigate(Routes.album(id)) },
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    vm = vm,
                    onAddEvent = { s, e -> nav.navigate(Routes.addEvent(s.toString(), e.toString())) },
                    onViewEvent = { id, dateIso -> nav.navigate(Routes.viewEvent(id, dateIso)) },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.LISTS) {
                ListsScreen(
                    vm = vm,
                    onOpenBoard = { nav.navigate(Routes.listDetail(it)) },
                    onOpenBucket = { nav.navigate(Routes.BUCKET_HOME) },
                    onOpenDday = { nav.navigate(Routes.DDAY) },
                    onOpenPlace = { nav.navigate(Routes.place(it)) },
                    onOpenFun = { nav.navigate(Routes.FUN) },
                    onOpenMyFun = { nav.navigate(Routes.MYFUN) },
                    onOpenRecipe = { nav.navigate(Routes.RECIPE) },
                    onOpenDocs = { nav.navigate(Routes.DOCS) },
                    onOpenAlbum = { nav.navigate(Routes.ALBUM) },
                    onOpenMyAlbum = { nav.navigate(Routes.MYALBUM) },
                    onOpenCoupon = { nav.navigate(Routes.COUPON) },
                    onOpenTravel = { nav.navigate(Routes.TRAVEL) },
                )
            }
            composable(Routes.FUN) {
                FunListScreen(vm = vm, boardKey = FunBoard.BOARD, isPrivate = false,
                    currentMemberId = currentMemberId, onBack = { nav.popBackStack() })
            }
            composable(Routes.RECIPE) {
                FunListScreen(vm = vm, boardKey = FunBoard.RECIPE, isPrivate = false,
                    currentMemberId = currentMemberId, onBack = { nav.popBackStack() })
            }
            composable(Routes.MYFUN) {
                FunListScreen(vm = vm, boardKey = FunBoard.PRIVATE, isPrivate = true,
                    currentMemberId = currentMemberId, onBack = { nav.popBackStack() })
            }
            composable(Routes.DOCS) {
                DocListScreen(vm = vm, currentMemberId = currentMemberId, onBack = { nav.popBackStack() })
            }
            composable(
                Routes.ALBUM_ROUTE,
                arguments = listOf(androidx.navigation.navArgument("photo") {
                    type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null
                }),
            ) { entry ->
                com.familyboard.app.ui.lists.AlbumScreen(
                    vm = vm, isPrivate = false,
                    openPhotoId = entry.arguments?.getString("photo"), // 홈 추억에서 온 경우 그 사진 뷰어 자동 열기
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.MYALBUM) {
                com.familyboard.app.ui.lists.AlbumScreen(vm = vm, isPrivate = true, onBack = { nav.popBackStack() })
            }
            composable(Routes.COUPON) {
                com.familyboard.app.ui.lists.CouponScreen(vm = vm, currentMemberId = currentMemberId, onBack = { nav.popBackStack() })
            }
            composable(Routes.TRAVEL) {
                com.familyboard.app.ui.lists.TravelScreen(vm = vm, currentMemberId = currentMemberId, onBack = { nav.popBackStack() })
            }
            composable(Routes.PLACE) { entry ->
                PlaceListScreen(
                    vm = vm,
                    boardKey = entry.arguments?.getString("board").orEmpty(),
                    currentMemberId = currentMemberId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.DDAY) {
                DDayScreen(vm = vm, currentMemberId = currentMemberId, onBack = { nav.popBackStack() })
            }
            composable(Routes.BUCKET_HOME) {
                BucketHomeScreen(
                    vm = vm,
                    currentMemberId = currentMemberId,
                    onOpenList = { nav.navigate(Routes.BUCKET_LIST) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.BUCKET_LIST) {
                BucketListScreen(
                    vm = vm,
                    onOpenAdd = { nav.navigate(Routes.BUCKET_ADD) },
                    onOpenView = { id -> nav.navigate(Routes.bucketView(id)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.BUCKET_ADD) {
                BucketAddScreen(vm = vm, currentMemberId = currentMemberId, editId = null,
                    onBack = { nav.popBackStack() })
            }
            composable(Routes.BUCKET_EDIT) { entry ->
                BucketAddScreen(vm = vm, currentMemberId = currentMemberId,
                    editId = entry.arguments?.getString("itemId"), onBack = { nav.popBackStack() })
            }
            composable(Routes.BUCKET_VIEW) { entry ->
                BucketViewScreen(
                    vm = vm,
                    itemId = entry.arguments?.getString("itemId").orEmpty(),
                    currentMemberId = currentMemberId,
                    onEdit = { id -> nav.navigate(Routes.bucketEdit(id)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.ALLOWANCE) {
                AllowanceScreen(vm = vm)
            }
            composable(Routes.MANAGE) {
                ManageScreen(
                    vm = vm,
                    onOpenEmergency = { nav.navigate(Routes.EMERGENCY) },
                    onOpenNotice = { nav.navigate(Routes.listDetail("notice")) },
                )
            }
            composable(Routes.EMERGENCY) {
                EmergencySendScreen(vm = vm, currentMemberId = currentMemberId, onBack = { nav.popBackStack() })
            }
            composable(Routes.ADD_EVENT) { entry ->
                AddEventScreen(
                    vm = vm,
                    startIso = entry.arguments?.getString("startIso").orEmpty(),
                    endIso = entry.arguments?.getString("endIso").orEmpty(),
                    defaultMemberId = currentMemberId,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.EDIT_EVENT) { entry ->
                AddEventScreen(
                    vm = vm,
                    startIso = "",
                    endIso = "",
                    defaultMemberId = currentMemberId,
                    onBack = { nav.popBackStack() },
                    editEventId = entry.arguments?.getString("eventId"),
                )
            }
            composable(Routes.VIEW_EVENT) { entry ->
                ViewEventScreen(
                    vm = vm,
                    eventId = entry.arguments?.getString("eventId").orEmpty(),
                    dateIso = entry.arguments?.getString("dateIso").orEmpty(),
                    onEdit = { id -> nav.navigate(Routes.editEvent(id)) },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.LIST_DETAIL) { entry ->
                ListDetailScreen(
                    vm = vm,
                    boardKey = entry.arguments?.getString("board").orEmpty(),
                    currentMemberId = currentMemberId,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }

    // 네이버 플레이스 등에서 공유받았을 때: 맛집/가볼 곳 중 저장 위치 선택
    pendingShare?.let { sp ->
        if (sp.isTravel) {
            val body = if (sp.loading) "구글 지도에서 정보 가져오는 중…"
            else buildList {
                add(sp.name)
                val sub = listOf(sp.category, sp.region).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) add(sub)
                if (sp.naverScore > 0.0) add("⭐ ${sp.naverScore}" + if (sp.ratingCount > 0) " (리뷰 ${sp.ratingCount})" else "")
                if (sp.address.isNotBlank()) add("📍 ${sp.address}")
            }.joinToString("\n")
            AlertDialog(
                onDismissRequest = { vm.clearPendingShare() },
                title = { Text("여행 위시리스트에 저장") },
                text = { Text(body) },
                confirmButton = {
                    TextButton(enabled = !sp.loading, onClick = { vm.saveTravel(); nav.navigateShareUnderLists(Routes.TRAVEL) }) { Text("저장") }
                },
                dismissButton = { TextButton(onClick = { vm.clearPendingShare() }) { Text("취소") } },
            )
        } else if (sp.isFun) {
            val body = if (sp.loading) "정보 가져오는 중…" else sp.name
            AlertDialog(
                onDismissRequest = { vm.clearPendingShare() },
                title = { Text("어디에 담을까요?") },
                text = { Text(body) },
                confirmButton = {
                    TextButton(enabled = !sp.loading, onClick = { vm.saveFun(FunBoard.BOARD); nav.navigateShareUnderLists(Routes.FUN) }) { Text("재미진 곳") }
                },
                dismissButton = {
                    Row {
                        TextButton(enabled = !sp.loading, onClick = { vm.saveFun(FunBoard.PRIVATE); nav.navigateShareUnderLists(Routes.MYFUN) }) { Text("내 재미진 곳") }
                        TextButton(onClick = { vm.clearPendingShare() }) { Text("취소") }
                    }
                },
            )
        } else {
            val body = if (sp.loading) "네이버에서 정보 가져오는 중…"
            else listOf(sp.name, sp.description, if (sp.address.isNotBlank()) "📍 ${sp.address}" else "")
                .filter { it.isNotBlank() }.joinToString("\n")
            AlertDialog(
                onDismissRequest = { vm.clearPendingShare() },
                title = { Text("어디에 저장할까요?") },
                text = { Text(body) },
                confirmButton = {
                    TextButton(enabled = !sp.loading, onClick = {
                        vm.savePlace(PlaceBoards.RESTAURANT); nav.navigateShareUnderLists(Routes.place(PlaceBoards.RESTAURANT))
                    }) { Text("맛집") }
                },
                dismissButton = {
                    Row {
                        TextButton(enabled = !sp.loading, onClick = {
                            vm.savePlace(PlaceBoards.VISIT); nav.navigateShareUnderLists(Routes.place(PlaceBoards.VISIT))
                        }) { Text("가볼 곳") }
                        TextButton(onClick = { vm.clearPendingShare() }) { Text("취소") }
                    }
                },
            )
        }
    }

    // 다른 앱에서 '공유'로 받은 파일 → 가족 공유 문서함 저장 확인
    docShare?.let { pd ->
        AlertDialog(
            onDismissRequest = { if (!pd.uploading) vm.clearPendingDoc() },
            title = { Text("문서함에 저장") },
            text = { Text(if (pd.uploading) "저장 중…" else pd.name) },
            confirmButton = {
                TextButton(enabled = !pd.uploading, onClick = {
                    vm.savePendingDoc { ok, err ->
                        if (ok) nav.navigateShareUnderLists(Routes.DOCS)
                        else Toast.makeText(context, err ?: "저장 실패", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(enabled = !pd.uploading, onClick = { vm.clearPendingDoc() }) { Text("취소") } },
        )
    }
}

/**
 * 공유로 저장한 뒤 이동. 시작 지점(홈) 위에 목적지 하나만 남겨 중복 누적을 방지한다.
 * (공유를 여러 번 하면 재미진 곳 등이 백스택에 쌓여 뒤로가기가 안 먹던 문제 해결)
 */
private fun androidx.navigation.NavController.navigateShare(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
    }
}

/**
 * 외부 앱 '공유'로 저장 후 이동(재미진 곳·내 재미진 곳·여행·맛집·가볼 곳·문서함). 이 대상들은 모두
 * 리스트 탭의 카드라, 뒤로가기 시 홈이 아니라 **리스트 메인**으로 가도록 [HOME, LISTS, target] 백스택을 만든다.
 */
private fun androidx.navigation.NavController.navigateShareUnderLists(route: String) {
    navigate(Routes.LISTS) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
    }
    navigate(route) { launchSingleTop = true }
}

private fun androidx.navigation.NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

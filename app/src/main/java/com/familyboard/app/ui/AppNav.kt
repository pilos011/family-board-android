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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
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
    val currentMemberId by vm.currentMemberId.collectAsStateWithLifecycle()
    val isParent = currentMemberId == "seonil" || currentMemberId == "eunseon"
    val pendingShare by vm.pendingShare.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == Routes.HOME,
                        onClick = { nav.navigateTab(Routes.HOME) },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("홈") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.CALENDAR,
                        onClick = { nav.navigateTab(Routes.CALENDAR) },
                        icon = { Icon(Icons.Default.CalendarMonth, null) },
                        label = { Text("가족 달력") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.LISTS,
                        onClick = { nav.navigateTab(Routes.LISTS) },
                        icon = { Icon(Icons.Default.Checklist, null) },
                        label = { Text("리스트") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.ALLOWANCE,
                        onClick = { nav.navigateTab(Routes.ALLOWANCE) },
                        icon = { Icon(Icons.Default.Savings, null) },
                        label = { Text("용돈 정산") },
                    )
                    if (isParent) {
                        NavigationBarItem(
                            selected = route == Routes.MANAGE,
                            onClick = { nav.navigateTab(Routes.MANAGE) },
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
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    vm = vm,
                    onOpenEvent = { id, dateIso -> nav.navigate(Routes.viewEvent(id, dateIso)) },
                    onOpenDday = { nav.navigate(Routes.DDAY) },
                    onOpenNotice = { nav.navigate(Routes.listDetail("notice")) },
                    canManageNotice = isParent,
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
                )
            }
            composable(Routes.FUN) {
                FunListScreen(vm = vm, boardKey = FunBoard.BOARD, isPrivate = false,
                    currentMemberId = currentMemberId, onBack = { nav.popBackStack() })
            }
            composable(Routes.MYFUN) {
                FunListScreen(vm = vm, boardKey = FunBoard.PRIVATE, isPrivate = true,
                    currentMemberId = currentMemberId, onBack = { nav.popBackStack() })
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
        if (sp.isFun) {
            val body = if (sp.loading) "정보 가져오는 중…" else sp.name
            AlertDialog(
                onDismissRequest = { vm.clearPendingShare() },
                title = { Text("어디에 담을까요?") },
                text = { Text(body) },
                confirmButton = {
                    TextButton(enabled = !sp.loading, onClick = { vm.saveFun(FunBoard.BOARD); nav.navigate(Routes.FUN) }) { Text("재미진 곳") }
                },
                dismissButton = {
                    Row {
                        TextButton(enabled = !sp.loading, onClick = { vm.saveFun(FunBoard.PRIVATE); nav.navigate(Routes.MYFUN) }) { Text("내 재미진 곳") }
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
                        vm.savePlace(PlaceBoards.RESTAURANT); nav.navigate(Routes.place(PlaceBoards.RESTAURANT))
                    }) { Text("맛집") }
                },
                dismissButton = {
                    Row {
                        TextButton(enabled = !sp.loading, onClick = {
                            vm.savePlace(PlaceBoards.VISIT); nav.navigate(Routes.place(PlaceBoards.VISIT))
                        }) { Text("가볼 곳") }
                        TextButton(onClick = { vm.clearPendingShare() }) { Text("취소") }
                    }
                },
            )
        }
    }
}

private fun androidx.navigation.NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

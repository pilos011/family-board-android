package com.familyboard.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import com.familyboard.app.ui.AppRoot
import com.familyboard.app.ui.AppViewModel
import com.familyboard.app.ui.PermItem
import com.familyboard.app.ui.PermissionGateScreen
import com.familyboard.app.ui.theme.FamilyBoardTheme

class MainActivity : ComponentActivity() {

    // AppRoot 의 viewModel() 과 동일 인스턴스(액티비티 스코프) — 공유 인텐트를 여기서 넣는다.
    private val vm: AppViewModel by viewModels()

    companion object {
        /** 위젯 상단 탭 인텐트에 담기는 대상 보드 키(예: "restaurant"). */
        const val EXTRA_WIDGET_NAV = "widget_nav"
        /** 재미진 항목 공유 알림 탭 시 열 항목 id. */
        const val EXTRA_OPEN_FUN = "open_fun_item"
        /** 업데이트 요청 알림 탭 시 업데이트 창 자동 표시. */
        const val EXTRA_OPEN_UPDATE = "open_update"
        /** 용돈 미션(업데이트 챌린지) 알림 탭 시 보상 금액(원). >0 이면 챌린지 수락. */
        const val EXTRA_UPDATE_CHALLENGE = "update_challenge_reward"
    }

    // 시스템 글자 크기(폰트 스케일)와 무관하게 앱 내부는 항상 '기본(1.0)'으로 강제.
    // (은선폰처럼 시스템 글자 크기를 최대로 둔 경우 UI가 넘치는 문제 방지)
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = 1.0f
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    // 권한 게이트: 필수 권한(알림·배터리·전체화면·오버레이)이 다 허용돼야 앱 내용이 열린다.
    // 위치·앨범·앱설치는 선택(게이트 제외). 게이트 항목은 Compose state 로 두어 승인 시 즉시 반영.
    private var permsReady by mutableStateOf(false)
    private var autoRevokeOn by mutableStateOf(false)     // '미사용 앱 권한 자동제거'가 켜져 있나
    private var autoRevokeChecked = false                 // 한 번 '꺼짐' 확인되면 재조회 중단(F5)
    private var mandatoryState by mutableStateOf(emptyList<PermItem>())
    private var optionalState by mutableStateOf(emptyList<PermItem>())
    private var pendingEntryIntent: Intent? = null        // 게이트 중 들어온 공유/위젯 인텐트 보류(F3)
    private val permFlags get() = getSharedPreferences("perm_flags", Context.MODE_PRIVATE)

    // 첫 실행 1회: 알림(필수)+위치(선택)를 함께 프라이밍(F4). 이후엔 게이트 버튼으로.
    private val startupPrimeLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            // 실제로 다이얼로그가 뜬(응답된) 권한만 '물어봄' 표시 — 이후 영구거부 판별의 기준(F1/추가리뷰 #2).
            permFlags.edit().apply {
                if (result.containsKey(Manifest.permission.POST_NOTIFICATIONS)) putBoolean("asked_notif", true)
                if (result.containsKey(Manifest.permission.ACCESS_FINE_LOCATION)) putBoolean("asked_loc", true)
            }.apply()
            if (needBgLoc()) runCatching { bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
            afterPerm()
        }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { afterPerm() }
    private val fineLocLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (needBgLoc()) runCatching { bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
            afterPerm()
        }
    private val bgLocLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { afterPerm() }
    private val batteryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { afterPerm() }
    private val fullScreenLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { afterPerm() }
    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { afterPerm() }
    private val autoRevokeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { afterPerm() }
    private val appSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { afterPerm() }

    /** 권한 요청/설정에서 돌아온 뒤 재검사 → 게이트 통과 처리. 전진 전용: 한 번 통과하면 사용 중 재게이트 안 함(#4).
     *  게이트가 안 보일 땐(통과 상태) 숨은 목록을 재구축하거나 자동제거를 재조회하지 않는다(#3). */
    private fun afterPerm() {
        if (permsReady) return
        permsReady = allMandatoryGranted()
        rebuildPermItems()
        refreshAutoRevokeStatus()
        if (permsReady) drainPendingEntryIntent()
    }

    /** 게이트 카드 목록을 현재 권한 상태로 다시 만든다(상태 갱신 → 자동 재구성, F6). */
    private fun rebuildPermItems() {
        mandatoryState = mandatoryItems()
        optionalState = optionalItems()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        permsReady = allMandatoryGranted()
        if (!permsReady) { rebuildPermItems(); refreshAutoRevokeStatus() } // 게이트 보일 때만 준비(#3)
        primeStartupPermissions()      // 첫 실행 1회 알림+위치 프라이밍
        processEntryIntent(intent)     // 게이트 중이면 보류, 아니면 바로 처리
        setContent {
            FamilyBoardTheme(darkTheme = false) {
                if (permsReady) {
                    AppRoot()
                } else {
                    PermissionGateScreen(required = mandatoryState, optional = optionalState)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processEntryIntent(intent)
    }

    /** 위젯 상단 탭 → 보드 이동 / 재미진 항목 공유 알림 탭 → 그 항목 열기. AppNav·FunListScreen 이 관찰. */
    private fun handleWidgetIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_WIDGET_NAV)?.takeIf { it.isNotBlank() }?.let { vm.requestWidgetNav(it) }
        intent?.getStringExtra(EXTRA_OPEN_FUN)?.takeIf { it.isNotBlank() }?.let { vm.requestOpenSharedFun(it) }
        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE, false) == true) vm.requestOpenUpdate()
        intent?.getIntExtra(EXTRA_UPDATE_CHALLENGE, -1)?.takeIf { it > 0 }?.let {
            vm.acceptUpdateChallenge(it)
            intent?.removeExtra(EXTRA_UPDATE_CHALLENGE) // 재생성 시 재실행 방지
        }
    }

    /** '공유 → 가족 알림판'으로 들어온 텍스트/이미지/영상/파일 처리. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val type = intent.type ?: ""
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = singleStream(intent)
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                // 쿠팡/코코달인은 이미지+캡션 형태로도 공유됨 → 텍스트에 쇼핑 링크가 있으면 이미지 무시하고 장보기로
                val shopLinkInText = !sharedText.isNullOrBlank() &&
                    Regex("coupang|coupa\\.ng|cocodalin", RegexOption.IGNORE_CASE).containsMatchIn(sharedText)
                when {
                    shopLinkInText -> {
                        val handled = vm.handleSharedText(sharedText, intent.getStringExtra(Intent.EXTRA_SUBJECT))
                        if (!handled) Toast.makeText(this, "담지 못했어요", Toast.LENGTH_SHORT).show()
                    }
                    type.startsWith("image/") -> stream?.let { vm.handleSharedImage(it) }
                        ?: Toast.makeText(this, "이미지를 읽지 못했어요", Toast.LENGTH_SHORT).show()
                    type.startsWith("video/") -> stream?.let { vm.handleSharedVideo(it, type.substringAfter('/')) }
                        ?: Toast.makeText(this, "영상을 읽지 못했어요", Toast.LENGTH_SHORT).show()
                    // 링크(텍스트) 공유 → 쿠팡=장보기 바로 / 네이버=장소 / 그 외=재미진 곳
                    !sharedText.isNullOrBlank() && stream == null -> {
                        val handled = vm.handleSharedText(sharedText, intent.getStringExtra(Intent.EXTRA_SUBJECT))
                        if (!handled) Toast.makeText(this, "링크가 없어 담지 못했어요", Toast.LENGTH_SHORT).show()
                    }
                    // 그 외 파일(pdf·docx·엑셀·hwp 등) → 가족 공유 문서함
                    stream != null -> vm.handleSharedDocument(stream)
                    else -> Toast.makeText(this, "공유한 내용을 처리하지 못했어요", Toast.LENGTH_SHORT).show()
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> if (type.startsWith("image/")) {
                val uris = multiStream(intent)
                if (uris.isNotEmpty()) vm.handleSharedImages(uris)
                else Toast.makeText(this, "이미지를 읽지 못했어요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun singleStream(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)

    private fun multiStream(intent: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)) ?: emptyList()

    // 설정 화면(배터리·전체화면·오버레이·자동제거·앱설정)에서 돌아오면 재검사 → 게이트/앱 전환.
    override fun onResume() {
        super.onResume()
        afterPerm()
    }

    /** 필수 4종(알림·배터리·전체화면·오버레이)이 모두 허용됐나. 위치·앨범·앱설치는 게이트 제외(선택). */
    private fun allMandatoryGranted(): Boolean =
        !needNotif() && !needBattery() && !needFullScreen() && !needOverlay()

    /** 첫 실행 1회: 알림(필수)+위치(선택)를 함께 요청. 이후엔 게이트 버튼으로(F4). */
    private fun primeStartupPermissions() {
        if (permFlags.getBoolean("startup_primed", false)) return
        val perms = buildList {
            if (needNotif()) add(Manifest.permission.POST_NOTIFICATIONS)
            if (needFineLoc()) add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (perms.isEmpty()) { permFlags.edit().putBoolean("startup_primed", true).apply(); return }
        // launch 성공(다이얼로그 표시)했을 때만 '프라이밍 완료' 기록 — 실패하면 다음 실행에 재시도. asked_* 는 콜백에서.
        val launched = runCatching { startupPrimeLauncher.launch(perms.toTypedArray()) }.isSuccess
        if (launched) permFlags.edit().putBoolean("startup_primed", true).apply()
    }

    /** 게이트 중(필수 미승인)이면 공유/위젯 인텐트를 보류했다가, 통과 후 처리(F3). */
    private fun processEntryIntent(i: Intent?) {
        if (i == null) return
        if (permsReady) { handleShareIntent(i); handleWidgetIntent(i) } else pendingEntryIntent = i
    }
    private fun drainPendingEntryIntent() {
        val i = pendingEntryIntent ?: return
        pendingEntryIntent = null
        handleShareIntent(i); handleWidgetIntent(i)
    }

    /** '미사용 앱 권한 자동제거'가 켜져 있는지 비동기 조회 → 켜져 있을 때만 안내 카드. 꺼짐 확인되면 재조회 중단(F5). */
    private fun refreshAutoRevokeStatus() {
        if (autoRevokeChecked && !autoRevokeOn) return
        runCatching {
            val future = PackageManagerCompat.getUnusedAppRestrictionsStatus(this)
            future.addListener({
                val on = runCatching {
                    when (future.get()) {
                        UnusedAppRestrictionsConstants.API_30_BACKPORT,
                        UnusedAppRestrictionsConstants.API_30,
                        UnusedAppRestrictionsConstants.API_31 -> true
                        else -> false // DISABLED(사용자가 끔)·ERROR·FEATURE_NOT_AVAILABLE
                    }
                }.getOrDefault(false)
                runOnUiThread { autoRevokeOn = on; autoRevokeChecked = true; rebuildPermItems() }
            }, ContextCompat.getMainExecutor(this))
        }
    }

    /** 앱 상세 설정으로 이동(런타임 권한 영구거부 시 유일한 승인 경로, F1). */
    private fun openAppSettings() {
        runCatching {
            appSettingsLauncher.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName"))
            )
        }
    }

    /** 런타임 권한 버튼: 한 번 거부면 재요청(다이얼로그), 영구거부면 앱 설정으로(F1). */
    private fun requestRuntime(perm: String, launcher: androidx.activity.result.ActivityResultLauncher<String>, askedKey: String) {
        when {
            ActivityCompat.shouldShowRequestPermissionRationale(this, perm) -> launcher.launch(perm)
            permFlags.getBoolean(askedKey, false) -> openAppSettings() // 영구거부(다시 묻지 않음)
            else -> { permFlags.edit().putBoolean(askedKey, true).apply(); launcher.launch(perm) }
        }
    }

    // ─────────── 게이트 버튼이 호출하는 권한 요청들 ───────────
    private fun launchNotif() { if (needNotif()) requestRuntime(Manifest.permission.POST_NOTIFICATIONS, notifLauncher, "asked_notif") }
    private fun launchBattery() {
        runCatching {
            batteryLauncher.launch(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:$packageName"))
            )
        }
    }
    private fun launchFullScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        runCatching {
            fullScreenLauncher.launch(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:$packageName"))
            )
        }
    }
    private fun launchOverlay() {
        runCatching {
            overlayLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(Uri.parse("package:$packageName"))
            )
        }
    }
    private fun launchLocation() { if (needFineLoc()) requestRuntime(Manifest.permission.ACCESS_FINE_LOCATION, fineLocLauncher, "asked_loc") }
    private fun launchAutoRevoke() {
        runCatching { autoRevokeLauncher.launch(IntentCompat.createManageUnusedAppRestrictionsIntent(this, packageName)) }
    }

    /** 게이트 필수 항목(모두 허용해야 앱이 열림). granted 는 매 재구성마다 라이브로 계산. */
    private fun mandatoryItems(): List<PermItem> = listOf(
        PermItem("알림 표시", "일정·빠른 연락 등 모든 알림을 받으려면 필요해요.", !needNotif(), ::launchNotif),
        PermItem("배터리 최적화 예외", "앱이 백그라운드에서 꺼지지 않고 알림을 놓치지 않게 해요.", !needBattery(), ::launchBattery),
        PermItem("전체 화면 알림", "빠른 연락 요청이 올 때 화면을 꽉 채워 알려요.", !needFullScreen(), ::launchFullScreen),
        PermItem("다른 앱 위에 표시", "사용 중에도 빠른 연락 요청을 전체 화면으로 띄워요.", !needOverlay(), ::launchOverlay),
    )

    /** 선택 항목(게이트에 영향 없음): 위치 + (켜져 있으면) 미사용 권한 자동제거 끄기 안내. */
    private fun optionalItems(): List<PermItem> {
        val list = mutableListOf(
            PermItem("위치", "빠른 연락 때 내 위치 공유·주변 맛집 찾기에만 써요. 원치 않으면 안 켜도 돼요.", !needFineLoc(), ::launchLocation),
        )
        if (autoRevokeOn) list += PermItem(
            "미사용 시 권한 자동삭제 끄기",
            "오래 안 쓰면 안드로이드가 권한을 지워요. 설정에서 이걸 꺼 두면 계속 유지돼요(직접 꺼 주세요).",
            false, ::launchAutoRevoke,
        )
        return list
    }

    private fun needNotif(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun needFineLoc(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED

    private fun needBgLoc(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED

    private fun needBattery(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun needFullScreen(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return !nm.canUseFullScreenIntent()
    }

    // 빠른 연락 강제 전체화면용: '다른 앱 위에 표시' 미허용이면 요청 대상.
    private fun needOverlay(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)
}

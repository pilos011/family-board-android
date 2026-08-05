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
import androidx.core.content.ContextCompat
import com.familyboard.app.ui.AppRoot
import com.familyboard.app.ui.AppViewModel
import com.familyboard.app.ui.theme.FamilyBoardTheme

class MainActivity : ComponentActivity() {

    // AppRoot 의 viewModel() 과 동일 인스턴스(액티비티 스코프) — 공유 인텐트를 여기서 넣는다.
    private val vm: AppViewModel by viewModels()

    // 시스템 글자 크기(폰트 스케일)와 무관하게 앱 내부는 항상 '기본(1.0)'으로 강제.
    // (은선폰처럼 시스템 글자 크기를 최대로 둔 경우 UI가 넘치는 문제 방지)
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.fontScale = 1.0f
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    // 시작 시 필요한 권한을 순차로 요청 (알림 → 배터리 최적화 예외 → 전체화면 알림)
    private var permStep = 0

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { nextPermStep() }
    private val fineLocLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { nextPermStep() }
    private val bgLocLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { nextPermStep() }
    private val batteryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { nextPermStep() }
    private val fullScreenLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { nextPermStep() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (needNotif() || needFineLoc() || needBgLoc() || needBattery() || needFullScreen()) {
            permStep = 0
            nextPermStep()
        }
        handleShareIntent(intent)
        setContent {
            FamilyBoardTheme(darkTheme = false) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /** '공유 → 준준가족 보드'로 들어온 텍스트/이미지/영상 처리. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent == null) return
        val type = intent.type ?: ""
        when (intent.action) {
            Intent.ACTION_SEND -> when {
                type == "text/plain" -> {
                    vm.handleSharedText(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_SUBJECT))
                    if (vm.pendingShare.value == null) Toast.makeText(this, "링크가 없어 담지 못했어요", Toast.LENGTH_SHORT).show()
                }
                type.startsWith("image/") -> singleStream(intent)?.let { vm.handleSharedImage(it) }
                    ?: Toast.makeText(this, "이미지를 읽지 못했어요", Toast.LENGTH_SHORT).show()
                type.startsWith("video/") -> singleStream(intent)?.let { vm.handleSharedVideo(it, type.substringAfter('/')) }
                    ?: Toast.makeText(this, "영상을 읽지 못했어요", Toast.LENGTH_SHORT).show()
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

    /** 다음 미허용 권한을 하나씩 요청. 각 요청이 끝나면 콜백에서 다시 호출된다. */
    private fun nextPermStep() {
        when (permStep++) {
            0 -> if (needNotif()) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else nextPermStep()

            1 -> if (needFineLoc()) {
                fineLocLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else nextPermStep()

            2 -> if (needBgLoc()) {
                runCatching { bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
                    .onFailure { nextPermStep() }
            } else nextPermStep()

            3 -> if (needBattery()) {
                runCatching {
                    batteryLauncher.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }.onFailure { nextPermStep() }
            } else nextPermStep()

            4 -> if (needFullScreen()) {
                runCatching {
                    fullScreenLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                            .setData(Uri.parse("package:$packageName"))
                    )
                }.onFailure { nextPermStep() }
            } else nextPermStep()

            else -> { /* 완료 */ }
        }
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
}

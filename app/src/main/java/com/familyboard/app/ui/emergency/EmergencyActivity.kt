package com.familyboard.app.ui.emergency

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.familyboard.app.R
import com.familyboard.app.notif.ReminderScheduler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.familyboard.app.data.CurrentUserStore
import com.familyboard.app.data.Family
import com.familyboard.app.notif.NotifyApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** 긴급 연락 수신 시 잠금화면 위로 뜨는 전체화면 알림. */
class EmergencyActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SENDER = "sender"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_WANT_LOC = "wantLoc"
        const val EXTRA_TEST = "test"
    }

    private var testMode = false

    private val locPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) shareLocation() else toast("위치 권한이 필요해요")
        }

    private var senderId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 잠금화면 위로, 화면 켜기
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        senderId = intent.getStringExtra(EXTRA_SENDER).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val wantLoc = intent.getBooleanExtra(EXTRA_WANT_LOC, false)
        testMode = intent.getBooleanExtra(EXTRA_TEST, false)
        // 전체화면을 띄웠으니 상태바의 긴급 알림은 정리
        androidx.core.app.NotificationManagerCompat.from(this).cancel(9001)
        vibrateAlarm()

        setContent {
            WaitingContent(
                senderName = Family.nameOf(senderId),
                message = message,
                showLocation = wantLoc,
                onCall = { callSender() },
                onShareLocation = { requestAndShareLocation() },
                onClose = { finish() },
            )
        }
    }

    private fun callSender() {
        val phone = Family.byId(senderId)?.phone ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.replace("-", "")}")))
        }
    }

    private fun requestAndShareLocation() {
        val granted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) shareLocation() else locPermLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun shareLocation() {
        lifecycleScope.launch {
            val loc = fetchLocation()
            if (testMode) {
                // 혼자 테스트: 내 폰에 '위치 공유됨' 알림을 띄우고, 탭하면 지도앱으로 연다.
                // 실제 위치를 못 찾아도 예시 좌표로 흐름을 확인할 수 있게 한다.
                val lat = loc?.latitude ?: 37.5665
                val lng = loc?.longitude ?: 126.9780
                val meName = Family.nameOf(CurrentUserStore(applicationContext).currentMemberId.first())
                postLocalLocation(meName, lat, lng)
                toast(
                    if (loc != null) "테스트: 위치 알림을 보냈어요. 알림을 탭해 지도로 확인하세요"
                    else "테스트: 실제 위치를 못 찾아 예시 좌표로 표시했어요. 알림을 탭하세요"
                )
                return@launch
            }
            if (loc == null) { toast("위치를 가져오지 못했어요"); return@launch }
            val me = CurrentUserStore(applicationContext).currentMemberId.first().orEmpty()
            runCatching {
                NotifyApi.notifyData(
                    actor = me, targets = listOf(senderId),
                    title = "📍 위치 공유", body = "${Family.nameOf(me)}님이 위치를 공유했어요",
                    data = mapOf(
                        "type" to "location", "sender" to me,
                        "lat" to loc.latitude.toString(), "lng" to loc.longitude.toString(),
                    ),
                )
            }
            toast("위치를 보냈어요")
        }
    }

    /** 테스트용: 내 폰에 위치 공유 알림을 직접 띄운다(탭하면 지도앱). */
    private fun postLocalLocation(name: String, lat: Double, lng: Double) {
        ReminderScheduler.ensureChannel(this)
        val geo = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode("공유된 위치")})")
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(this, 8100, Intent(Intent.ACTION_VIEW, geo), flags)
        val body = "$name 알림 확인하였으며, 위치를 공유합니다."
        val n = NotificationCompat.Builder(this, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📍 위치 공유")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n탭하면 지도에서 위치를 봅니다"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val ok = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.app.ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (ok) NotificationManagerCompat.from(this).notify(8100, n)
    }

    private fun vibrateAlarm() {
        val pattern = longArrayOf(0, 600, 300, 600, 300, 600)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION") v.vibrate(pattern, -1)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLocation(): Location? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val last = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return last
            }
            // 8초 안에 fix 를 못 잡으면 마지막 알려진 위치로 폴백(무한 대기 방지)
            return kotlinx.coroutines.withTimeoutOrNull(8000) {
                suspendCancellableCoroutine { cont ->
                    val signal = CancellationSignal()
                    runCatching {
                        lm.getCurrentLocation(provider, signal, mainExecutor) { loc -> cont.resume(loc ?: last) }
                    }.onFailure { cont.resume(last) }
                    cont.invokeOnCancellation { signal.cancel() }
                }
            } ?: last
        }
        return last
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}

// 애타게 기다리는 따뜻한 톤
private val WarmBg1 = Color(0xFFFBEFD8)
private val WarmBg2 = Color(0xFFF3D6AF)
private val WarmInk = Color(0xFF5A4632)
private val WarmAccent = Color(0xFFE8894A)

@Composable
private fun WaitingContent(
    senderName: String,
    message: String,
    showLocation: Boolean,
    onCall: () -> Unit,
    onShareLocation: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(WarmBg1, WarmBg2)))
            .padding(20.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Default.Close, "닫기", tint = WarmInk.copy(alpha = 0.55f))
        }

        Column(
            Modifier.fillMaxSize().padding(top = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(com.familyboard.app.R.drawable.call_wait),
                contentDescription = null,
                modifier = Modifier.size(180.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(6.dp))
            Text("빠른 연락 요청", color = WarmInk, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text("${senderName}님이 애타게 기다리고 있어요", color = WarmInk.copy(alpha = 0.7f), fontSize = 15.sp)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .padding(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    message.ifBlank { "연락 부탁해요." },
                    color = WarmInk, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, lineHeight = 32.sp,
                )
            }
            Spacer(Modifier.height(30.dp))

            Button(
                onClick = onCall,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WarmAccent, contentColor = Color.White),
            ) {
                Icon(Icons.Default.Call, null); Spacer(Modifier.size(8.dp))
                Text("바로 연락", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            if (showLocation) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onShareLocation,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmInk.copy(alpha = 0.10f), contentColor = WarmInk,
                    ),
                ) {
                    Icon(Icons.Default.LocationOn, null); Spacer(Modifier.size(8.dp))
                    Text("내 위치 공유", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

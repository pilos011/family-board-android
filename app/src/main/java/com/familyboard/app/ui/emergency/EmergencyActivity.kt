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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
        vibrateAlarm()

        setContent {
            EmergencyContent(
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
            if (loc == null) { toast("위치를 가져오지 못했어요"); return@launch }
            if (testMode) {
                // 혼자 테스트: 내 폰에 '위치 공유됨' 알림을 띄우고, 탭하면 지도앱으로 연다.
                postLocalLocation(loc.latitude, loc.longitude)
                toast("테스트: 위치 알림을 보냈어요. 알림을 탭해 지도로 확인하세요")
                return@launch
            }
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
    private fun postLocalLocation(lat: Double, lng: Double) {
        ReminderScheduler.ensureChannel(this)
        val geo = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode("공유된 위치")})")
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(this, 8100, Intent(Intent.ACTION_VIEW, geo), flags)
        val n = NotificationCompat.Builder(this, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("📍 위치 공유 (테스트)")
            .setContentText("탭하면 지도에서 위치를 봅니다")
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
            return suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                runCatching {
                    lm.getCurrentLocation(provider, signal, mainExecutor) { loc -> cont.resume(loc ?: last) }
                }.onFailure { cont.resume(last) }
                cont.invokeOnCancellation { signal.cancel() }
            }
        }
        return last
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}

private val EmergencyRed = Color(0xFFD6293E)
private val EmergencyDark = Color(0xFF7A1020)

@Composable
private fun EmergencyContent(
    senderName: String,
    message: String,
    showLocation: Boolean,
    onCall: () -> Unit,
    onShareLocation: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(EmergencyRed, EmergencyDark)))
            .padding(20.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Default.Close, "닫기", tint = Color.White)
        }

        Column(
            Modifier.fillMaxSize().padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🚨", fontSize = 64.sp)
            Spacer(Modifier.height(8.dp))
            Text("긴급 연락", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text("보낸 사람 · $senderName", color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    message.ifBlank { "긴급히 연락 바랍니다." },
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, lineHeight = 32.sp,
                )
            }
            Spacer(Modifier.height(36.dp))

            Button(
                onClick = onCall,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = EmergencyRed),
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
                        containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.LocationOn, null); Spacer(Modifier.size(8.dp))
                    Text("내 위치 공유", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

package com.familyboard.app.notif

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.familyboard.app.BuildConfig
import com.familyboard.app.data.Family
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 앱 사용자의 기기 정보(위치·배터리·충전모드·소리모드)를 Home Assistant REST API 로 전송.
 * HA 주소/토큰은 local.properties → BuildConfig 로 주입. (엔티티는 멤버 id 접두어)
 */
object HaReporter {
    private val base get() = BuildConfig.HA_BASE_URL.trimEnd('/')
    private val token get() = BuildConfig.HA_TOKEN

    fun enabled() = base.isNotBlank() && token.isNotBlank()

    @SuppressLint("MissingPermission")
    suspend fun report(context: Context, memberId: String?) = withContext(Dispatchers.IO) {
        if (!enabled() || memberId.isNullOrBlank()) return@withContext
        val name = Family.nameOf(memberId)

        // 배터리 / 충전 (sticky broadcast)
        val bat = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bat != null) {
            val level = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = bat.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val status = bat.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = bat.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val chargeText = when {
                !charging -> "미충전"
                plugged == BatteryManager.BATTERY_PLUGGED_AC -> "AC 충전"
                plugged == BatteryManager.BATTERY_PLUGGED_USB -> "USB 충전"
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "무선 충전"
                else -> "충전중"
            }
            if (pct >= 0) {
                postState(
                    "sensor.${memberId}_battery", pct.toString(),
                    JSONObject().put("unit_of_measurement", "%").put("device_class", "battery")
                        .put("friendly_name", "$name 배터리"),
                )
            }
            postState(
                "sensor.${memberId}_charging", chargeText,
                JSONObject().put("is_charging", charging).put("friendly_name", "$name 충전"),
            )
        }

        // 소리 모드
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringer = when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "소리"
            AudioManager.RINGER_MODE_VIBRATE -> "진동"
            AudioManager.RINGER_MODE_SILENT -> "무음"
            else -> "알 수 없음"
        }
        postState(
            "sensor.${memberId}_ringer", ringer,
            JSONObject().put("friendly_name", "$name 소리모드"),
        )

        // 재실/외출: 집 WiFi(집 LAN) 접속 여부로 판정 — SSID 대신 집 서브넷 IP 로 판별해 위치 권한이 필요 없음.
        // (아이들이 위치 공유를 거부해도 동작. HA 자동화용 device_tracker + 읽기용 sensor 둘 다 보고.)
        val home = onHomeLan(context)
        postState(
            "device_tracker.${memberId}_wifi", if (home) "home" else "not_home",
            JSONObject().put("source_type", "router").put("friendly_name", "$name 집WiFi"),
        )
        postState(
            "sensor.${memberId}_presence", if (home) "재실" else "외출",
            JSONObject().put("friendly_name", "$name 재실여부").put("icon", if (home) "mdi:home" else "mdi:home-export-outline"),
        )

        // 위치 (권한 있을 때, 마지막 알려진 위치)
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val loc = listOf(
                LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER,
            ).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
            if (loc != null) {
                postState(
                    "device_tracker.${memberId}_phone", "not_home",
                    JSONObject()
                        .put("source_type", "gps")
                        .put("latitude", loc.latitude)
                        .put("longitude", loc.longitude)
                        .put("gps_accuracy", loc.accuracy.toInt())
                        .put("friendly_name", "$name 위치"),
                )
            }
        }
    }

    // 집 서브넷 접두어(헤르메스 192.168.10.23 와 같은 대역). 이 대역 IP 를 WiFi 로 받았으면 '집'.
    private const val HOME_SUBNET_PREFIX = "192.168.10."

    /** WiFi 로 집 LAN(HOME_SUBNET_PREFIX)에 연결돼 있으면 true = 재실. 위치 권한 불필요(SSID 안 읽음). */
    private fun onHomeLan(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return false // WiFi 아니면 외출
            val lp = cm.getLinkProperties(net) ?: return false
            lp.linkAddresses.any { it.address.hostAddress?.startsWith(HOME_SUBNET_PREFIX) == true }
        } catch (e: Exception) {
            false
        }
    }

    private fun postState(entity: String, state: String, attributes: JSONObject) {
        runCatching {
            val conn = (URL("$base/api/states/$entity").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000; readTimeout = 6000; doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }
            val body = JSONObject().put("state", state).put("attributes", attributes)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
        }
    }
}

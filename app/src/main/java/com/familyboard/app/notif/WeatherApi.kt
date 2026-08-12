package com.familyboard.app.notif

import com.familyboard.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 날씨(WMO weather code + 최고/최저 기온). */
object WeatherApi {
    /** 하루 날씨: 코드 + 최고/최저 기온(℃, 반올림 정수). */
    data class DayWeather(val code: Int, val high: Int, val low: Int)

    /**
     * 오늘/내일 날씨(각각 코드+최고/최저). 실패 시 null.
     * 우선 **헤르메스 서버 캐시(`/weather`)**를 읽는다(백석동 고정, 서버가 매시간 1회 조회 → 가족 앱 공유).
     * 서버가 아직 준비 안 됐거나 장애면 Open-Meteo 직접 조회로 폴백.
     */
    suspend fun twoDay(lat: Double, lng: Double): Pair<DayWeather, DayWeather>? = withContext(Dispatchers.IO) {
        fromServer() ?: fromOpenMeteo(lat, lng)
    }

    /** 서버 캐시 `/weather` → {today:{code,high,low}, tomorrow:{...}}. */
    private fun fromServer(): Pair<DayWeather, DayWeather>? = runCatching {
        val base = BuildConfig.NOTIFY_BASE_URL.trimEnd('/')
        if (base.isBlank()) return@runCatching null
        val conn = (URL("$base/weather").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 5000; readTimeout = 5000
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) return@runCatching null
        val o = JSONObject(text)
        fun day(k: String): DayWeather {
            val j = o.getJSONObject(k)
            return DayWeather(j.getInt("code"), j.getInt("high"), j.getInt("low"))
        }
        day("today") to day("tomorrow")
    }.getOrNull()

    /** 폴백: Open-Meteo 무료 공개 API 직접 조회(키 불필요). */
    private fun fromOpenMeteo(lat: Double, lng: Double): Pair<DayWeather, DayWeather>? = runCatching {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=Asia%2FSeoul&forecast_days=2"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 8000; readTimeout = 8000
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        conn.disconnect()
        if (code !in 200..299) return@runCatching null
        val daily = JSONObject(text).getJSONObject("daily")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        if (codes.length() < 2 || highs.length() < 2 || lows.length() < 2) return@runCatching null
        fun day(i: Int) = DayWeather(codes.getInt(i), Math.round(highs.getDouble(i)).toInt(), Math.round(lows.getDouble(i)).toInt())
        day(0) to day(1)
    }.getOrNull()
}

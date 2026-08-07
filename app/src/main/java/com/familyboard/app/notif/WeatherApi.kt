package com.familyboard.app.notif

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 날씨(WMO weather code + 최고/최저 기온). Open-Meteo 무료 공개 API(키 불필요). */
object WeatherApi {
    /** 하루 날씨: 코드 + 최고/최저 기온(℃, 반올림 정수). */
    data class DayWeather(val code: Int, val high: Int, val low: Int)

    /** 오늘/내일 날씨(각각 코드+최고/최저). 실패 시 null. */
    suspend fun twoDay(lat: Double, lng: Double): Pair<DayWeather, DayWeather>? = withContext(Dispatchers.IO) {
        runCatching {
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
}

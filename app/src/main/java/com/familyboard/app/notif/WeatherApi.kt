package com.familyboard.app.notif

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 날씨(WMO weather code + 오늘 최고/최저 기온). Open-Meteo 무료 공개 API(키 불필요). */
object WeatherApi {
    /** 오늘 날씨: 코드 + 최고/최저 기온(℃, 반올림 정수). */
    data class TodayWeather(val code: Int, val high: Int, val low: Int)

    /** 오늘 날씨(코드+최고/최저). 실패 시 null. */
    suspend fun todayHighLow(lat: Double, lng: Double): TodayWeather? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=Asia%2FSeoul&forecast_days=1"
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
            if (codes.length() < 1 || highs.length() < 1 || lows.length() < 1) null
            else TodayWeather(codes.getInt(0), Math.round(highs.getDouble(0)).toInt(), Math.round(lows.getDouble(0)).toInt())
        }.getOrNull()
    }
}

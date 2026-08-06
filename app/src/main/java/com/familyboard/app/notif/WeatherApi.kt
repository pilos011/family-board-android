package com.familyboard.app.notif

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 오늘/내일 날씨(WMO weather code). Open-Meteo 무료 공개 API(키 불필요). */
object WeatherApi {
    /** (오늘코드, 내일코드). 실패 시 null. */
    suspend fun today2(lat: Double, lng: Double): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng" +
                "&daily=weather_code&timezone=Asia%2FSeoul&forecast_days=2"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 8000; readTimeout = 8000
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299) return@runCatching null
            val arr = JSONObject(text).getJSONObject("daily").getJSONArray("weather_code")
            if (arr.length() < 2) null else (arr.getInt(0) to arr.getInt(1))
        }.getOrNull()
    }
}

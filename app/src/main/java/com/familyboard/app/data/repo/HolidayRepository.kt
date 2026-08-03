package com.familyboard.app.data.repo

import android.util.Log
import com.familyboard.app.BuildConfig
import com.familyboard.app.data.model.Holiday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 한국 공휴일 조회 — 공공데이터포털 "특일 정보"(한국천문연구원) getRestDeInfo.
 * BuildConfig.HOLIDAY_API_KEY (local.properties 의 holiday.api.key) 가 비어 있으면 빈 목록 반환.
 * 연/월 단위로 조회하고 메모리에 캐시한다.
 */
class HolidayRepository {

    private val cache = mutableMapOf<String, List<Holiday>>()
    private val mutex = Mutex()

    suspend fun holidays(year: Int, month: Int): List<Holiday> {
        val key = "%04d-%02d".format(year, month)
        cache[key]?.let { return it }

        val apiKey = BuildConfig.HOLIDAY_API_KEY
        if (apiKey.isBlank()) return emptyList()

        return mutex.withLock {
            cache[key]?.let { return it }
            // API 실패(키만료/네트워크) 시 고정 공휴일 fallback (음력 명절·대체공휴일은 미반영, 비상용)
            val result = runCatching { fetch(year, month, apiKey) }
                .onFailure { Log.w(TAG, "공휴일 조회 실패 $key - fallback 사용", it) }
                .getOrElse { hardcodedFallback(year, month) }
            cache[key] = result
            result
        }
    }

    private suspend fun fetch(year: Int, month: Int, apiKey: String): List<Holiday> =
        withContext(Dispatchers.IO) {
            val url = URL(
                "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo" +
                    "?serviceKey=$apiKey&solYear=$year&solMonth=%02d".format(month) +
                    "&numOfRows=100&_type=json"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            try {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                conn.disconnect()
            }
        }

    private fun parse(body: String): List<Holiday> {
        val itemsNode = JSONObject(body)
            .optJSONObject("response")?.optJSONObject("body")
            ?.optJSONObject("items") ?: return emptyList()

        // item 은 단건이면 Object, 다건이면 Array 로 온다.
        val list = mutableListOf<Holiday>()
        when (val item = itemsNode.opt("item")) {
            is JSONArray -> for (i in 0 until item.length()) toHoliday(item.getJSONObject(i))?.let(list::add)
            is JSONObject -> toHoliday(item)?.let(list::add)
        }
        return list
    }

    private fun toHoliday(o: JSONObject): Holiday? {
        if (o.optString("isHoliday") != "Y") return null
        val loc = o.optString("locdate")          // yyyymmdd
        if (loc.length != 8) return null
        val iso = "${loc.substring(0, 4)}-${loc.substring(4, 6)}-${loc.substring(6, 8)}"
        return Holiday(iso, o.optString("dateName"))
    }

    /** 매년 고정 공휴일만 (대체공휴일·설날/추석 등 음력 명절은 미반영). API 실패 시 비상용. */
    private fun hardcodedFallback(year: Int, month: Int): List<Holiday> {
        val fixed = mapOf(
            (1 to 1) to "신정", (3 to 1) to "삼일절", (5 to 5) to "어린이날",
            (6 to 6) to "현충일", (8 to 15) to "광복절", (10 to 3) to "개천절",
            (10 to 9) to "한글날", (12 to 25) to "크리스마스",
        )
        return fixed.filterKeys { it.first == month }.map { (md, name) ->
            Holiday("%04d-%02d-%02d".format(year, md.first, md.second), name)
        }
    }

    companion object { private const val TAG = "HolidayRepository" }
}

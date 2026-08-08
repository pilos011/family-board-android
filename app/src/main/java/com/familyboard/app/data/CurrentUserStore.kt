package com.familyboard.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "family_board_prefs")

/** 최초 실행 시 선택한 '본인' 멤버 id 를 저장/복원한다. */
class CurrentUserStore(private val context: Context) {

    private val keyMemberId = stringPreferencesKey("current_member_id")

    val currentMemberId: Flow<String?> =
        context.dataStore.data.map { it[keyMemberId] }

    suspend fun setCurrentMember(id: String) {
        context.dataStore.edit { it[keyMemberId] = id }
    }

    // 재미진 곳 "마지막 본 페이지"(1-based, 0=없음). 보드+정렬방향별로 저장 → 다음에 이어보기.
    // (최신순/등록순은 페이지 내용이 달라 방향별로 따로 기억한다.)
    private fun funPageKey(board: String, ascending: Boolean) =
        intPreferencesKey("fun_last_page_${board}_${if (ascending) "asc" else "desc"}")

    fun lastFunPage(board: String, ascending: Boolean): Flow<Int> =
        context.dataStore.data.map { it[funPageKey(board, ascending)] ?: 0 }

    suspend fun setLastFunPage(board: String, ascending: Boolean, page: Int) {
        context.dataStore.edit { it[funPageKey(board, ascending)] = page }
    }

    // 길찾기 기본 앱("항상" 선택 시 저장). 빈 값=매번 선택창.
    private val keyNavApp = stringPreferencesKey("nav_default_app")
    val navDefaultApp: Flow<String> = context.dataStore.data.map { it[keyNavApp] ?: "" }
    suspend fun setNavDefaultApp(key: String) {
        context.dataStore.edit { if (key.isBlank()) it.remove(keyNavApp) else it[keyNavApp] = key }
    }

    // 홈 배경 선택: "cork"(기본 코르크) / "family"(우리집 알림판 이미지). 기본 cork.
    private val keyHomeBg = stringPreferencesKey("home_background")
    val homeBackground: Flow<String> = context.dataStore.data.map { it[keyHomeBg] ?: "cork" }
    suspend fun setHomeBackground(v: String) {
        context.dataStore.edit { it[keyHomeBg] = v }
    }
}

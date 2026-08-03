package com.familyboard.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
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
}

package com.glassous.dsha.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "dsha_profiles")

/**
 * 连接组的持久化：整份列表序列化成 JSON 存进 DataStore 的单个 key。
 * 组数量是个位数，整表读写最简单，也天然避免了多实例并发问题。
 */
class ProfileRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 全部连接组，按最近使用时间倒序。 */
    val profiles: Flow<List<Profile>> = context.profileDataStore.data
        .map { prefs -> decode(prefs[KEY_PROFILES]) }

    suspend fun find(id: String): Profile? = profiles.first().firstOrNull { it.id == id }

    /** 新增或覆盖同 id 的组。 */
    suspend fun upsert(profile: Profile) {
        update { current ->
            val index = current.indexOfFirst { it.id == profile.id }
            if (index >= 0) current.toMutableList().also { it[index] = profile } else current + profile
        }
    }

    suspend fun delete(id: String) {
        update { current -> current.filterNot { it.id == id } }
    }

    /** 记录最近一次连接时间，用于列表排序。 */
    suspend fun touch(id: String) {
        update { current ->
            current.map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it }
        }
    }

    private suspend fun update(transform: (List<Profile>) -> List<Profile>) {
        context.profileDataStore.edit { prefs ->
            val next = transform(decode(prefs[KEY_PROFILES]))
            prefs[KEY_PROFILES] = json.encodeToString(next.sortedByDescending { it.lastUsedAt })
        }
    }

    private fun decode(raw: String?): List<Profile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<Profile>>(raw) }
            .onFailure { Log.w(TAG, "连接组数据解析失败，已按空列表处理", it) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.lastUsedAt }
    }

    private companion object {
        const val TAG = "ProfileRepository"
        val KEY_PROFILES = stringPreferencesKey("profiles_json")
    }
}

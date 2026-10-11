package com.glassous.dsha.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 连接组的持久化：整份列表手写成 JSON 存进 SharedPreferences 的单个 key。
 * 组数量是个位数，整表读写最简单，也天然避免了多实例并发问题。
 *
 * 存储全部走平台自带 API（SharedPreferences + org.json），不引入 datastore 与序列化库；
 * 对外接口（Flow + suspend 函数）与之前的实现保持一致。
 */
class ProfileRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 内存中的当前列表，仅在落盘成功后才更新。 */
    private val state = MutableStateFlow(read())

    /** 全部连接组，按最近使用时间倒序。 */
    val profiles: Flow<List<Profile>> = state

    suspend fun find(id: String): Profile? = state.value.firstOrNull { it.id == id }

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
        val next = transform(state.value).sortedByDescending { it.lastUsedAt }
        val raw = encode(next)
        // commit 而非 apply：先落盘成功再更新内存，避免界面显示一条没存下去的组
        val saved = withContext(Dispatchers.IO) { prefs.edit().putString(KEY_PROFILES, raw).commit() }
        if (saved) {
            state.value = next
        } else {
            Log.w(TAG, "连接组写入失败，本次修改未生效")
        }
    }

    private fun read(): List<Profile> = decode(prefs.getString(KEY_PROFILES, null))

    private fun encode(profiles: List<Profile>): String {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("baseUrl", profile.baseUrl)
                    .put("username", profile.username)
                    .put("password", profile.password)
                    .put("credentialsEnabled", profile.credentialsEnabled)
                    .put("lastUsedAt", profile.lastUsedAt)
                    // 只存版本号本身：读取时由 HarnessVersion.fromStored 找回枚举，
                    // 已下线的版本号会自然回退到当前适配的最高版本
                    .put("harnessVersion", profile.harnessVersion.label)
                    .put("adaptiveCompatEnabled", profile.adaptiveCompatEnabled),
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<Profile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val username = item.optString("username")
                val password = item.optString("password")
                Profile(
                    id = item.optString("id").ifBlank { Profile.newId() },
                    name = item.optString("name"),
                    baseUrl = item.optString("baseUrl"),
                    username = username,
                    password = password,
                    // 旧数据没有这个键（凭据开关原先在应用设置里）：已填写凭据的组按启用迁移，
                    // 避免升级后原有凭据被静默忽略；没填凭据的组保持默认的直接进入
                    credentialsEnabled = item.optBoolean(
                        "credentialsEnabled",
                        username.isNotBlank() || password.isNotEmpty(),
                    ),
                    lastUsedAt = item.optLong("lastUsedAt"),
                    // 旧数据没有这两个键：默认开启适配 + 当前适配的最高版本，与加入该设置前的行为一致
                    adaptiveCompatEnabled = item.optBoolean("adaptiveCompatEnabled", true),
                    harnessVersion = HarnessVersion.fromStored(item.optString("harnessVersion")),
                )
            }
        }
            .onFailure { Log.w(TAG, "连接组数据解析失败，已按空列表处理", it) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.lastUsedAt }
    }

    private companion object {
        const val TAG = "ProfileRepository"
        const val PREFS_NAME = "dsha_profiles"
        const val KEY_PROFILES = "profiles_json"
    }
}

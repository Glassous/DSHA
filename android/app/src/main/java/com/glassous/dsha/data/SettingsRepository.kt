package com.glassous.dsha.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用级设置。
 *
 * @param credentialsEnabled 是否使用连接组里的账号密码。DeepSeek Harness 本身没有登录模块，
 *   账号密码只是 1Panel 等反向代理在入口加 Basic 鉴权的产物，默认关闭；
 *   关闭后连接直接进入，已填写的凭据会被忽略。
 * @param adaptiveCompatEnabled 是否注入针对 DeepSeek Harness 0.2.0-rc.2 的手机端响应式适配。
 *   适配深度绑定该版本的页面结构，Harness 更新后可能反而引入问题，关闭后仅保留基本页面显示。
 * @param onboardingCompleted 首次启动的快捷设置是否已完成确认；未确认（含中途退出）时每次启动都会再弹。
 */
data class AppSettings(
    val credentialsEnabled: Boolean = false,
    val adaptiveCompatEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "dsha_settings")

/** 应用级设置的持久化：字段少，逐项存进 DataStore 即可。 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            credentialsEnabled = prefs[KEY_CREDENTIALS] ?: false,
            adaptiveCompatEnabled = prefs[KEY_ADAPTIVE_COMPAT] ?: true,
            onboardingCompleted = prefs[KEY_ONBOARDING] ?: false,
        )
    }

    suspend fun setCredentialsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_CREDENTIALS] = enabled }
    }

    suspend fun setAdaptiveCompatEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_ADAPTIVE_COMPAT] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_ONBOARDING] = completed }
    }

    private companion object {
        val KEY_CREDENTIALS = booleanPreferencesKey("credentials_enabled")
        val KEY_ADAPTIVE_COMPAT = booleanPreferencesKey("adaptive_compat_enabled")
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
    }
}

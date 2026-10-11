package com.glassous.dsha.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * 应用级设置。
 *
 * 网页适配（响应式适配开关与 Harness 版本）不在这里：它随连接组存放，
 * 因为同一个应用可能同时连向不同版本的 Harness，见 [Profile]。
 *
 * @param credentialsEnabled 是否使用连接组里的账号密码。DeepSeek Harness 本身没有登录模块，
 *   账号密码只是 1Panel 等反向代理在入口加 Basic 鉴权的产物，默认关闭；
 *   关闭后连接直接进入，已填写的凭据会被忽略。
 * @param onboardingCompleted 首次启动的快捷设置是否已完成确认；未确认（含中途退出）时每次启动都会再弹。
 * @param agreementAccepted 用户协议是否已同意。未同意时快捷设置停在协议页、应用不可用；
 *   同意后不再要求，协议内容仍可在设置页随时查看。
 * @param autoUpdateCheck 启动时是否自动检测新版本，默认开启；关闭后不再发起查询。
 * @param ignoredUpdateVersion 已忽略的版本号。该版本不再提示更新，直到出现更新的版本。
 */
data class AppSettings(
    val credentialsEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val agreementAccepted: Boolean = false,
    val autoUpdateCheck: Boolean = true,
    val ignoredUpdateVersion: String? = null,
)

/**
 * 应用级设置的持久化：字段少，逐项存进 SharedPreferences（平台 API，不引入 datastore）；
 * 对外接口（Flow + suspend 函数）与之前的实现保持一致。
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(read())

    val settings: Flow<AppSettings> = state

    suspend fun setCredentialsEnabled(enabled: Boolean) = writeBoolean(KEY_CREDENTIALS, enabled)

    suspend fun setOnboardingCompleted(completed: Boolean) = writeBoolean(KEY_ONBOARDING, completed)

    suspend fun setAgreementAccepted(accepted: Boolean) = writeBoolean(KEY_AGREEMENT, accepted)

    suspend fun setAutoUpdateCheck(enabled: Boolean) = writeBoolean(KEY_AUTO_UPDATE, enabled)

    /** 忽略某个版本的更新提示：只记版本号，出现更新的版本时照常提示。 */
    suspend fun setIgnoredUpdateVersion(version: String) = writeString(KEY_IGNORED_UPDATE, version)

    private fun read(): AppSettings = AppSettings(
        credentialsEnabled = prefs.getBoolean(KEY_CREDENTIALS, false),
        onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING, false),
        agreementAccepted = prefs.getBoolean(KEY_AGREEMENT, false),
        autoUpdateCheck = prefs.getBoolean(KEY_AUTO_UPDATE, true),
        ignoredUpdateVersion = prefs.getString(KEY_IGNORED_UPDATE, null),
    )

    private suspend fun writeBoolean(key: String, value: Boolean) = write(key) { putBoolean(key, value) }

    private suspend fun writeString(key: String, value: String) = write(key) { putString(key, value) }

    /**
     * 写入统一入口，[change] 描述要改哪一项。
     * 用 commit 而非 apply：先落盘成功再更新内存，避免界面显示一个没存下去的开关状态。
     */
    private suspend fun write(key: String, change: SharedPreferences.Editor.() -> Unit) {
        val saved = withContext(Dispatchers.IO) {
            val editor = prefs.edit()
            change(editor)
            editor.commit()
        }
        if (saved) {
            state.value = read()
        } else {
            Log.w(TAG, "设置写入失败：$key")
        }
    }

    private companion object {
        const val TAG = "SettingsRepository"
        const val PREFS_NAME = "dsha_settings"
        const val KEY_CREDENTIALS = "credentials_enabled"
        const val KEY_ONBOARDING = "onboarding_completed"
        const val KEY_AGREEMENT = "agreement_accepted"
        const val KEY_AUTO_UPDATE = "auto_update_check"
        const val KEY_IGNORED_UPDATE = "ignored_update_version"
    }
}

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
 * @param credentialsEnabled 是否使用连接组里的账号密码。DeepSeek Harness 本身没有登录模块，
 *   账号密码只是 1Panel 等反向代理在入口加 Basic 鉴权的产物，默认关闭；
 *   关闭后连接直接进入，已填写的凭据会被忽略。
 * @param adaptiveCompatEnabled 是否注入针对 DeepSeek Harness 0.2.0-rc.2 的手机端响应式适配。
 *   适配深度绑定该版本的页面结构，Harness 更新后可能反而引入问题，关闭后仅保留基本页面显示。
 * @param onboardingCompleted 首次启动的快捷设置是否已完成确认；未确认（含中途退出）时每次启动都会再弹。
 * @param agreementAccepted 用户协议是否已同意。未同意时快捷设置停在协议页、应用不可用；
 *   同意后不再要求，协议内容仍可在设置页随时查看。
 */
data class AppSettings(
    val credentialsEnabled: Boolean = false,
    val adaptiveCompatEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val agreementAccepted: Boolean = false,
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

    suspend fun setCredentialsEnabled(enabled: Boolean) = write(KEY_CREDENTIALS, enabled)

    suspend fun setAdaptiveCompatEnabled(enabled: Boolean) = write(KEY_ADAPTIVE_COMPAT, enabled)

    suspend fun setOnboardingCompleted(completed: Boolean) = write(KEY_ONBOARDING, completed)

    suspend fun setAgreementAccepted(accepted: Boolean) = write(KEY_AGREEMENT, accepted)

    private fun read(): AppSettings = AppSettings(
        credentialsEnabled = prefs.getBoolean(KEY_CREDENTIALS, false),
        adaptiveCompatEnabled = prefs.getBoolean(KEY_ADAPTIVE_COMPAT, true),
        onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING, false),
        agreementAccepted = prefs.getBoolean(KEY_AGREEMENT, false),
    )

    private suspend fun write(key: String, value: Boolean) {
        // commit 而非 apply：先落盘成功再更新内存，避免界面显示一个没存下去的开关状态
        val saved = withContext(Dispatchers.IO) { prefs.edit().putBoolean(key, value).commit() }
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
        const val KEY_ADAPTIVE_COMPAT = "adaptive_compat_enabled"
        const val KEY_ONBOARDING = "onboarding_completed"
        const val KEY_AGREEMENT = "agreement_accepted"
    }
}

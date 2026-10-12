package com.glassous.dsha

import android.app.Application
import android.content.pm.ApplicationInfo
import android.webkit.WebView
import com.glassous.dsha.data.ProfileRepository
import com.glassous.dsha.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 应用入口：持有全局唯一的连接组与设置仓库，ViewModel 通过 Application 获取，
 * 避免为了几个字段引入 DI 框架。
 */
class DshaApplication : Application() {

    val profileRepository: ProfileRepository by lazy { ProfileRepository(this) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    /**
     * 应用级协程作用域：承载不能随页面销毁而取消的写操作。
     *
     * 页面 ViewModel 的 scope 在返回时就被取消，而"改完立刻返回"的修改必须写完
     * （见连接组编辑页的实时保存）；这些任务都很短，不会在这里积累。
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // 调试包打开 WebView 远程调试，方便排查网页壳里的登录流程
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}

/** ViewModel 里的便捷访问方式：`application.profileRepository`。 */
val Application.profileRepository: ProfileRepository
    get() = (this as DshaApplication).profileRepository

/** ViewModel 里的便捷访问方式：`application.settingsRepository`。 */
val Application.settingsRepository: SettingsRepository
    get() = (this as DshaApplication).settingsRepository

/** ViewModel 里的便捷访问方式：`application.applicationScope`。 */
val Application.applicationScope: CoroutineScope
    get() = (this as DshaApplication).applicationScope

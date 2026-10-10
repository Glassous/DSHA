package com.glassous.dsha

import android.app.Application
import android.content.pm.ApplicationInfo
import android.webkit.WebView
import com.glassous.dsha.data.ProfileRepository

/**
 * 应用入口：持有全局唯一的连接组仓库，ViewModel 通过 Application 获取，
 * 避免为了几个字段引入 DI 框架。
 */
class DshaApplication : Application() {

    val profileRepository: ProfileRepository by lazy { ProfileRepository(this) }

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

package com.glassous.dsha.ui.web

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glassous.dsha.data.Profile
import com.glassous.dsha.profileRepository
import com.glassous.dsha.settingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 网页壳的界面状态：进度、返回栈、加载错误。 */
class WebViewModel(
    application: Application,
    private val profileId: String,
) : AndroidViewModel(application) {

    private val repository = application.profileRepository

    var profile by mutableStateOf<Profile?>(null)
        private set

    /** 首屏要加载的地址。 */
    var startUrl by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(true)
        private set

    var progress by mutableIntStateOf(0)

    var loadError by mutableStateOf(false)

    /** 网页首次加载完成（进度到 100）：启动动画进入"即将准备就绪"提示阶段。 */
    var loadFinished by mutableStateOf(false)

    /** 提示阶段结束：撤掉启动动画露出网页，之后页面内导航不再遮挡。 */
    var pageReady by mutableStateOf(false)

    /** 应用设置：是否使用连接组里的账号密码（默认关闭，直接进入）。 */
    var credentialsEnabled by mutableStateOf(false)
        private set

    /** 应用设置：是否注入针对 Harness 0.2.0-rc.2 的手机端响应式适配（默认开启）。 */
    var adaptiveCompatEnabled by mutableStateOf(true)
        private set

    /**
     * 重新加载前重置状态：让启动动画完整重走一遍。
     * progress 归零是必需的——即使旧值已是 100，也要让监听进度的 effect 重新触发。
     */
    fun restartLoading() {
        progress = 0
        loadError = false
        loadFinished = false
        pageReady = false
    }

    init {
        // 应用级设置与连接组并行读取：两者都先于 WebView 创建（loading 阶段）完成
        viewModelScope.launch {
            val appSettings = application.settingsRepository.settings.first()
            credentialsEnabled = appSettings.credentialsEnabled
            adaptiveCompatEnabled = appSettings.adaptiveCompatEnabled
        }
        viewModelScope.launch {
            val found = repository.find(profileId)
            if (found == null) {
                loading = false
                return@launch
            }
            repository.touch(found.id)
            profile = found
            startUrl = found.baseUrl
            loading = false
        }
    }
}

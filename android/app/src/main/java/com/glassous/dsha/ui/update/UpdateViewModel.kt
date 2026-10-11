package com.glassous.dsha.ui.update

import android.app.Application
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glassous.dsha.data.ReleaseInfo
import com.glassous.dsha.data.SemanticVersion
import com.glassous.dsha.data.UpdateChecker
import com.glassous.dsha.settingsRepository
import com.glassous.dsha.ui.settings.currentLanguageCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 更新检测的状态。检测结果一律以弹窗呈现，因此除 [Checking]（设置页按钮显示进度）外，
 * 其余状态都对应一个弹窗（见 UpdateDialog / UpdateMessageDialog）。
 */
sealed interface UpdateState {

    /** 空闲：没有进行中的检测，也没有要展示的结果。 */
    data object Idle : UpdateState

    /** 手动检测进行中。 */
    data object Checking : UpdateState

    /** 手动检测的结论：当前已是最新版本。 */
    data object UpToDate : UpdateState

    /** 手动检测的结论：查询失败（无网络、限流等）。 */
    data object Failed : UpdateState

    /** 查询到新版本。 */
    data class Available(val release: ReleaseInfo) : UpdateState
}

/** 是否要弹结果弹窗：空闲与进行中都不弹。 */
val UpdateState.popsDialog: Boolean
    get() = this is UpdateState.Available || this == UpdateState.UpToDate || this == UpdateState.Failed

/**
 * 更新检测：进入应用时自动查一次，设置页也能手动触发，两者共用同一份状态，
 * 因此结果弹窗只需要在导航宿主里挂一处（对哪个页面触发的检测都成立）。
 *
 * 自动与手动的差别只在"说什么"：自动检测静默——查询失败、版本不新、已被忽略都不出声，
 * 只有确实有新版本才弹窗；手动检测是用户主动发起的，进行中、已是最新、失败都要有明确反馈，
 * 且不再理会"忽略此版本"（用户既然手动点了，就如实告诉他）。
 *
 * ViewModel 跟随 Activity：换语言等配置变化重建界面时不会重复查询，重启应用才会再查一次。
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = application.settingsRepository
    private val app = application

    /** 当前安装的版本，与 GitHub Release 的 tag 同源（tag 为 `v` + 这个版本号）。 */
    val currentVersion: String = readVersionName()

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    init {
        autoCheck()
    }

    /** 进入应用时的自动检测：全程静默，只有查到更新才置上有弹窗的状态。 */
    private fun autoCheck() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.autoUpdateCheck) return@launch
            val latest = UpdateChecker.latestRelease(app.currentLanguageCode()) ?: return@launch
            if (latest.version == settings.ignoredUpdateVersion) return@launch
            if (!SemanticVersion.isNewer(latest.version, currentVersion)) return@launch
            // 迟到的自动检测结果不覆盖用户已经发起的操作：手动检测进了设置页，就别再插一脚
            if (state == UpdateState.Idle) state = UpdateState.Available(latest)
        }
    }

    /** 手动检测：进行中会挡住重复点击，结束后必定给出一个结论。 */
    fun checkNow() {
        if (state == UpdateState.Checking) return
        state = UpdateState.Checking
        viewModelScope.launch {
            val latest = UpdateChecker.latestRelease(app.currentLanguageCode())
            state = when {
                latest == null -> UpdateState.Failed
                SemanticVersion.isNewer(latest.version, currentVersion) -> UpdateState.Available(latest)
                else -> UpdateState.UpToDate
            }
        }
    }

    /** 关掉结果弹窗：自动检测的结果本次启动不再显示，手动检测的结论也到此读完。 */
    fun dismiss() {
        state = UpdateState.Idle
    }

    /** 忽略此版本：记进设置，之后自动检测不再为它弹窗。 */
    fun ignoreCurrent() {
        val ignored = (state as? UpdateState.Available)?.release?.version ?: return
        state = UpdateState.Idle
        viewModelScope.launch { settingsRepository.setIgnoredUpdateVersion(ignored) }
    }

    private fun readVersionName(): String = runCatching {
        app.packageManager
            .getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName
    }.getOrNull().orEmpty()
}

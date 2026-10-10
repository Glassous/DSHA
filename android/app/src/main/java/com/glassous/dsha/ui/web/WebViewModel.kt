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

    var canGoBack by mutableStateOf(false)

    var loadError by mutableStateOf(false)

    init {
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

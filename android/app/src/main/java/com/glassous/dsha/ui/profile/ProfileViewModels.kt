package com.glassous.dsha.ui.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glassous.dsha.R
import com.glassous.dsha.data.HarnessVersion
import com.glassous.dsha.data.Profile
import com.glassous.dsha.data.UrlUtils
import com.glassous.dsha.profileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 组列表：只需要一份列表 + 删除操作。 */
class ProfilesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.profileRepository

    val profiles: StateFlow<List<Profile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}

/** 组编辑：表单字段、校验与保存/删除。 */
class ProfileEditViewModel(
    application: Application,
    private val profileId: String?,
) : AndroidViewModel(application) {

    private val repository = application.profileRepository

    val isEditing: Boolean = profileId != null

    var loaded by mutableStateOf(false)
        private set

    /** 编辑时保留原有的最近使用时间，避免保存后列表排序跳变。 */
    private var lastUsedAt: Long = 0L

    var baseUrl by mutableStateOf("")
    var name by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordVisible by mutableStateOf(false)

    /** 本组是否使用账号密码；关闭时表单不显示凭据字段，保存也不校验。 */
    var credentialsEnabled by mutableStateOf(false)

    /** 本组的网页适配：是否注入手机端响应式适配；关闭时表单不显示版本行。 */
    var adaptiveCompatEnabled by mutableStateOf(true)

    /** 本组的网页适配：适配所依据的 Harness 版本。 */
    var harnessVersion by mutableStateOf(HarnessVersion.latest)

    /** 本组记住的访问认证密码（插件 `@fonlan/dsh-web-auth`）；空串表示没记住过。 */
    var webAuthPassword by mutableStateOf("")
    var webAuthPasswordVisible by mutableStateOf(false)

    /** 本组的访问认证：遇到插件登录页时是否静默自动登录。 */
    var webAuthAutoLogin by mutableStateOf(false)

    /**
     * 打开本页时是否已经记住过访问认证密码：决定访问认证那一段是否出现。
     *
     * 可见性不能直接绑在 [webAuthPassword] 的当前值上——否则用户在输入框里一清空文本，
     * 整段（连同自动登录开关）会当场消失。以进入页面时的状态为准，保存后再打开才按新状态决定。
     */
    var webAuthRemembered by mutableStateOf(false)
        private set

    var baseUrlError by mutableStateOf<Int?>(null)
        private set
    var usernameError by mutableStateOf<Int?>(null)
        private set
    var passwordError by mutableStateOf<Int?>(null)
        private set
    var saving by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            if (profileId != null) {
                repository.find(profileId)?.let(::apply)
            }
            loaded = true
        }
    }

    fun save(onSaved: () -> Unit) {
        val normalized = UrlUtils.normalizeBaseUrl(baseUrl)
        baseUrlError = when {
            baseUrl.isBlank() -> R.string.error_base_url_required
            normalized == null -> R.string.error_base_url_invalid
            else -> null
        }
        // 本组关闭凭据时不需要账号密码，直接跳过校验（已填写的值原样保留）
        usernameError =
            if (credentialsEnabled && username.isBlank()) R.string.error_username_required else null
        passwordError =
            if (credentialsEnabled && password.isEmpty()) R.string.error_password_required else null
        if (baseUrlError != null || usernameError != null || passwordError != null) return

        saving = true
        viewModelScope.launch {
            repository.upsert(buildProfile(normalized.orEmpty()))
            saving = false
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = profileId ?: return
        viewModelScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }

    private fun buildProfile(normalizedUrl: String) = Profile(
        id = profileId ?: Profile.newId(),
        name = name.trim(),
        baseUrl = normalizedUrl,
        username = username.trim(),
        password = password,
        credentialsEnabled = credentialsEnabled,
        lastUsedAt = lastUsedAt,
        adaptiveCompatEnabled = adaptiveCompatEnabled,
        harnessVersion = harnessVersion,
        webAuthPassword = webAuthPassword,
        // 密码被清空时自动登录没有意义，一并关掉：下次打开该连接会重新弹窗询问
        webAuthAutoLogin = webAuthPassword.isNotEmpty() && webAuthAutoLogin,
    )

    private fun apply(profile: Profile) {
        baseUrl = profile.baseUrl
        name = profile.name
        username = profile.username
        password = profile.password
        credentialsEnabled = profile.credentialsEnabled
        lastUsedAt = profile.lastUsedAt
        adaptiveCompatEnabled = profile.adaptiveCompatEnabled
        harnessVersion = profile.harnessVersion
        webAuthPassword = profile.webAuthPassword
        webAuthAutoLogin = profile.webAuthAutoLogin
        webAuthRemembered = profile.webAuthPassword.isNotEmpty()
    }
}

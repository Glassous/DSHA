package com.glassous.dsha.ui.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glassous.dsha.R
import com.glassous.dsha.applicationScope
import com.glassous.dsha.data.HarnessVersion
import com.glassous.dsha.data.Profile
import com.glassous.dsha.data.UrlUtils
import com.glassous.dsha.profileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 连续输入的合并窗口：停止输入这段时间后落盘，避免每敲一个字就写一次。 */
private const val AUTO_SAVE_DELAY_MS = 400L

/** 组列表：只需要一份列表 + 删除操作。 */
class ProfilesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = application.profileRepository

    val profiles: StateFlow<List<Profile>> = repository.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}

/**
 * 组编辑：表单字段、实时校验与自动保存/删除。
 *
 * 没有"保存"这一步：每一项改动都在停止输入片刻后自动落盘，校验不通过的内容不写入，
 * 字段下方的错误提示因此也是"这项还没存下去"的反馈；列表页直接跟着仓库的变化更新。
 */
class ProfileEditViewModel(
    application: Application,
    private val profileId: String?,
) : AndroidViewModel(application) {

    private val repository = application.profileRepository

    /**
     * 落盘用的应用级作用域：写操作不能随页面销毁被取消。
     * 用户改完立刻返回时页面协程会被取消，而这次修改必须写完，见 [flush]。
     */
    private val writeScope: CoroutineScope = application.applicationScope

    val isEditing: Boolean = profileId != null

    var loaded by mutableStateOf(false)
        private set

    /** 本组的 id：编辑既有组时来自路由，新建时进页面就生成，首次落盘后所有修改都写进同一条。 */
    private val id: String = profileId ?: Profile.newId()

    /** 编辑时保留原有的最近使用时间，避免保存后列表排序跳变。 */
    private var lastUsedAt: Long = 0L

    var baseUrl by mutableStateOf("")
    var name by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordVisible by mutableStateOf(false)

    /** 本组是否使用账号密码；关闭时表单不显示凭据字段，也不校验。 */
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
     * 整段（连同自动登录开关）会当场消失。以进入页面时的状态为准，重新进入页面才按新状态决定。
     */
    var webAuthRemembered by mutableStateOf(false)
        private set

    var baseUrlError by mutableStateOf<Int?>(null)
        private set
    var usernameError by mutableStateOf<Int?>(null)
        private set
    var passwordError by mutableStateOf<Int?>(null)
        private set

    /** 地址是否曾经填出过内容：决定清空后是否提示"必填"（新建页刚打开时不该一进来就报错）。 */
    private var baseUrlEverFilled: Boolean = false

    /** 最新一次编辑的表单快照，等合并窗口过去后落盘；为空表示没有等待中的修改。 */
    private var pendingSnapshot: FormSnapshot? = null

    /** 正在执行的落盘任务：新修改只更新待存快照，由它顺带写掉，避免两次写同一份连接组列表。 */
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            if (profileId != null) repository.find(profileId)?.let(::apply)
            loaded = true
            observeForm()
        }
    }

    /**
     * 返回前兜底：不等合并窗口，把等待中的修改立刻交给落盘。
     * 页面协程此刻已在退场路上，而落盘跑在应用级作用域上，最后一次修改不会因为返回而丢。
     */
    fun flush() {
        ensureSaving()
    }

    override fun onCleared() {
        super.onCleared()
        // 系统返回手势等不走顶栏按钮的路径同样要兜底：待存快照在这里交出去落盘
        ensureSaving()
    }

    fun delete(onDeleted: () -> Unit) {
        val id = profileId ?: return
        // 删除优先：还在等合并窗口的修改直接作废，否则它会在删除之后把这条记录重新写回来
        pendingSnapshot = null
        saveJob?.cancel()
        writeScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }

    /**
     * 实时保存：监听表单的每一项，停止输入片刻后把这段时间里的变化合并成一次落盘。
     *
     * 每次输入都先记进 [pendingSnapshot]，合并窗口只推迟写入时机——这样即使页面很快被销毁，
     * 最后一次修改也还留在待存快照里，由 [flush] 与 [onCleared] 接手写掉。
     */
    private suspend fun observeForm() {
        snapshotFlow { currentSnapshot() }
            // 首个发射是装载完成后的当前表单，不需要再存一遍
            .drop(1)
            .collectLatest { snapshot ->
                if (snapshot.baseUrl.isNotBlank()) baseUrlEverFilled = true
                pendingSnapshot = snapshot
                delay(AUTO_SAVE_DELAY_MS)
                ensureSaving()
            }
    }

    /** 确保有一个落盘任务在跑；已有任务时不重复启动，由它把最新快照顺带写掉。 */
    private fun ensureSaving() {
        if (saveJob?.isActive == true) return
        saveJob = writeScope.launch { drainSaves() }
    }

    private suspend fun drainSaves() {
        while (true) {
            val snapshot = pendingSnapshot ?: return
            pendingSnapshot = null
            persist(snapshot)
        }
    }

    /**
     * 落盘一次：先校验，有错就只提示不写入，改好后的输入会再次触发保存。
     * 地址还没填出有效值时整条不存——新建页刚打开不该在列表里留下一个空组。
     */
    private suspend fun persist(snapshot: FormSnapshot) {
        val normalized = UrlUtils.normalizeBaseUrl(snapshot.baseUrl)
        baseUrlError = when {
            snapshot.baseUrl.isBlank() ->
                if (isEditing || baseUrlEverFilled) R.string.error_base_url_required else null
            normalized == null -> R.string.error_base_url_invalid
            else -> null
        }
        // 本组关闭凭据时不需要账号密码，直接跳过校验（已填写的值原样保留）
        usernameError =
            if (snapshot.credentialsEnabled && snapshot.username.isBlank()) R.string.error_username_required else null
        passwordError =
            if (snapshot.credentialsEnabled && snapshot.password.isEmpty()) R.string.error_password_required else null
        if (normalized == null || usernameError != null || passwordError != null) return

        repository.upsert(profileOf(snapshot, normalized))
    }

    /** 当前表单快照：只含会被保存的字段，输入框可见性等等界面状态不参与。 */
    private fun currentSnapshot() = FormSnapshot(
        baseUrl = baseUrl,
        name = name,
        username = username,
        password = password,
        credentialsEnabled = credentialsEnabled,
        adaptiveCompatEnabled = adaptiveCompatEnabled,
        harnessVersion = harnessVersion,
        webAuthPassword = webAuthPassword,
        webAuthAutoLogin = webAuthAutoLogin,
    )

    private fun profileOf(snapshot: FormSnapshot, normalizedUrl: String) = Profile(
        id = id,
        name = snapshot.name.trim(),
        baseUrl = normalizedUrl,
        username = snapshot.username.trim(),
        password = snapshot.password,
        credentialsEnabled = snapshot.credentialsEnabled,
        lastUsedAt = lastUsedAt,
        adaptiveCompatEnabled = snapshot.adaptiveCompatEnabled,
        harnessVersion = snapshot.harnessVersion,
        webAuthPassword = snapshot.webAuthPassword,
        // 密码被清空时自动登录没有意义，一并关掉：下次打开该连接会重新弹窗询问
        webAuthAutoLogin = snapshot.webAuthPassword.isNotEmpty() && snapshot.webAuthAutoLogin,
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
        baseUrlEverFilled = profile.baseUrl.isNotBlank()
    }

    /** 表单里所有会被保存的字段：任何一项变化都产生新快照，作为实时保存的触发源。 */
    private data class FormSnapshot(
        val baseUrl: String,
        val name: String,
        val username: String,
        val password: String,
        val credentialsEnabled: Boolean,
        val adaptiveCompatEnabled: Boolean,
        val harnessVersion: HarnessVersion,
        val webAuthPassword: String,
        val webAuthAutoLogin: Boolean,
    )
}

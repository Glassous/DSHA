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

    var loadError by mutableStateOf(false)

    /** 网页首次加载完成（进度到 100）：启动动画进入"即将准备就绪"提示阶段。 */
    var loadFinished by mutableStateOf(false)

    /** 提示阶段结束：撤掉启动动画露出网页，之后页面内导航不再遮挡。 */
    var pageReady by mutableStateOf(false)

    /**
     * 访问认证闸门：非空表示插件（`@fonlan/dsh-web-auth`）的登录页正被本地接管，
     * 启动动画覆盖层保持不撤——用户看到的是应用自己的密码弹窗，而不是那张网页登录页。
     */
    var webAuthGate by mutableStateOf<WebAuthGate?>(null)
        private set

    /**
     * 上一次在弹窗里输入的密码：提交失败后重新弹窗时保留用户刚输入的内容，
     * 不用已记住的旧密码把它盖掉。每次闸门重新抬起（本次会话第一次检测）时清空。
     */
    var webAuthLastInput by mutableStateOf("")
        private set

    /** 已提交待验证的访问密码：登录页消失即视为成功，把它记进连接组。 */
    private var pendingPassword: String? = null

    /**
     * 用户是否已经明确放弃过本地接管（取消弹窗）。
     *
     * 放弃之后本次不再重新接管——否则用户在网页登录页上手动试一次、被服务端打回来，
     * 本地弹窗又跳出来盖住页面，等于没把「不想用它」这个选择当回事。
     * 等页面不再是插件登录页（手动登录成功）后自动解除，下次会话过期仍会正常接管。
     */
    private var webAuthSuppressed = false

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

    /**
     * 主框架开始了一次新导航。
     *
     * 闸门激活期间必须复位加载序列：登录表单提交后浏览器会跳到应用页，那是全新的一次加载，
     * 要按它自己的进度重新走「进度到 100 → 停留提示 → 淡出」。不正视这一点，登录页自己那次
     * 100% 的回调会把状态提前标成"已就绪"，闸门落下时露出的就是还没渲染完的页面。
     *
     * 闸门未激活时不动任何状态：首次加载之后，页面内导航照旧不遮挡网页。
     */
    fun onNavigationStarted() {
        if (webAuthGate != null) restartLoading()
    }

    /**
     * 处理一次访问认证页检测结果。
     *
     * @return 非空表示这一次要静默自动登录，值是待提交的密码（调用方负责真的去驱动网页表单）；
     *   null 表示闸门已切到弹窗态，等候用户输入。
     */
    fun onWebAuthPageDetected(page: WebAuthPage): String? {
        if (webAuthSuppressed) return null
        val current = profile
        val remembered = current?.webAuthPassword.orEmpty()
        // 首次设置（引导流程）、刚退出登录、上次提交失败：都交给用户自己决定
        val askUser = page.mode == WebAuthMode.SETUP ||
            page.loggedOut ||
            page.errorText.isNotEmpty()
        if (askUser) {
            raiseGate(
                WebAuthGate.Prompt(
                    setup = page.mode == WebAuthMode.SETUP,
                    errorText = page.errorText,
                ),
            )
            return null
        }
        if (current?.webAuthAutoLogin == true && remembered.isNotEmpty()) {
            // 提交后登录页消失即算成功，因此这里就把本次密码挂上
            pendingPassword = remembered
            raiseGate(WebAuthGate.AutoLoggingIn)
            return remembered
        }
        raiseGate(WebAuthGate.Prompt(setup = false, errorText = ""))
        return null
    }

    /** 用户在弹窗里提交了密码：闸门进入等待态，登录页消失即视为成功。 */
    fun onWebAuthSubmitted(password: String) {
        pendingPassword = password
        webAuthLastInput = password
        webAuthGate = WebAuthGate.Submitting
    }

    /**
     * 页面已经不再是插件登录页：落下闸门。
     *
     * 这里刻意不把 [pageReady] 置真——新页面仍是新的一次加载，启动动画要按它自己的进度走完，
     * 露出时才是渲染完整的页面。
     */
    fun onWebAuthLeft() {
        // 已经离开插件登录页：解除「不再接管」的抑制，下次会话过期照常接管
        webAuthSuppressed = false
        if (webAuthGate == null) return
        webAuthGate = null
        val submitted = pendingPassword
        pendingPassword = null
        submitted?.let(::rememberWebAuthPassword)
    }

    /**
     * 放弃本地接管（用户取消弹窗，或提交脚本没能生效）：落下闸门并直接露出网页原生登录页，
     * 用户可以手动操作，不会被空白的覆盖层卡住。
     */
    fun onWebAuthAbandoned() {
        webAuthSuppressed = true
        webAuthGate = null
        pendingPassword = null
        webAuthLastInput = ""
        // 网页登录页本身早已加载完成（闸门是在它加载完之后才抬起的），直接撤掉遮挡即可
        pageReady = true
    }

    /** 抬升闸门：复位加载序列，让覆盖层一直保持到认证结束后的新页面真正加载完。 */
    private fun raiseGate(next: WebAuthGate) {
        if (webAuthGate == null) webAuthLastInput = ""
        restartLoading()
        webAuthGate = next
    }

    /** 把登录成功后实际用到的访问密码记进连接组（值没变就不必回写）。 */
    private fun rememberWebAuthPassword(password: String) {
        val current = profile ?: return
        if (password.isEmpty() || current.webAuthPassword == password) return
        val updated = current.copy(webAuthPassword = password)
        profile = updated
        viewModelScope.launch { repository.upsert(updated) }
    }

    init {
        // 连接组（凭据开关、网页适配、Harness 版本都随它来）先于 WebView 创建（loading 阶段）完成
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

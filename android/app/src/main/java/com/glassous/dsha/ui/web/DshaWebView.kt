package com.glassous.dsha.ui.web

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.HttpAuthHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.glassous.dsha.data.HarnessVersion
import com.glassous.dsha.data.Profile
import com.glassous.dsha.data.UrlUtils
import com.glassous.dsha.ui.web.adapt.applyBaseAdapt
import com.glassous.dsha.ui.web.adapt.applyShellCompat
import com.glassous.dsha.ui.web.adapt.applyVersionAdapt
import java.util.Locale
import kotlinx.coroutines.delay
import org.json.JSONObject

/** WebView 里发生、需要向用户提示一次的事件。 */
enum class WebEvent {
    /** 已经尝试自动填充但页面没有跳转，提示用户手动输入。 */
    AUTOFILL_FALLBACK,

    /** 外层的 HTTP Basic 鉴权未通过（账号密码不对或组里没填）。 */
    AUTH_FAILED,

    NO_BROWSER,
    DOWNLOAD_STARTED,
    DOWNLOAD_FAILED,
}

/**
 * 网页壳本体。WebView 只在 [remember] 中创建一次，
 * 因此调用方需要保证同一个实例只服务同一个连接组（用 `key(profile.id)` 包一层即可）。
 *
 * 是否使用账号密码应答鉴权与自动填充取自 [Profile.credentialsEnabled]：同一实例只服务一个
 * 连接组，本页存活期间该值不会变，改动它需要离开本页再重新进入（WebView 随之重建）。
 *
 * @param adaptiveCompatEnabled 是否注入该版本专属的组件级改造；关闭后不做任何组件改造，
 *   页面仍由基础注入层（安全区、视口高度链兜底）保证正常显示
 * @param harnessVersion 版本层适配所依据的 Harness 版本，取自连接组自身
 * @param safeTopDp 状态栏高度（dp）：作为顶部安全距离注入网页内部，顶栏图案才能伸到状态栏底下
 * @param safeBottomDp 导航栏高度（dp）：注入网页内部的底部安全距离；软键盘弹出时传 0（那时视图已被抬起）
 * @param onNavigationStarted 每次主框架导航开始时回调：访问认证流程中据此复位启动加载序列，
 *   让登录成功后的新页面重新完整走一遍「加载 → 就绪提示 → 撤除」
 * @param onWebAuthPage 每次整页加载完成后回调访问认证页的检测结果；
 *   null 表示当前页面不是该插件的登录页（也用于「已经离开登录页」的判定）
 */
@Composable
fun DshaWebView(
    profile: Profile,
    startUrl: String,
    adaptiveCompatEnabled: Boolean,
    harnessVersion: HarnessVersion,
    modifier: Modifier = Modifier,
    safeTopDp: Float = 0f,
    safeBottomDp: Float = 0f,
    onWebViewCreated: (WebView) -> Unit = {},
    onProgress: (Int) -> Unit = {},
    onCanGoBackChanged: (Boolean) -> Unit = {},
    onNavigationStarted: () -> Unit = {},
    onLoadError: (Boolean) -> Unit = {},
    onWebAuthPage: (WebAuthPage?) -> Unit = {},
    onBackgroundColor: (Color) -> Unit = {},
    onEvent: (WebEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val alive = remember { mutableStateOf(true) }
    val pendingFileCallback = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val onProgressState = rememberUpdatedState(onProgress)
    val onCanGoBackState = rememberUpdatedState(onCanGoBackChanged)
    val onNavigationStartedState = rememberUpdatedState(onNavigationStarted)
    val onLoadErrorState = rememberUpdatedState(onLoadError)
    val onWebAuthPageState = rememberUpdatedState(onWebAuthPage)
    val onEventState = rememberUpdatedState(onEvent)
    val onCreatedState = rememberUpdatedState(onWebViewCreated)
    val onBackgroundColorState = rememberUpdatedState(onBackgroundColor)
    val safeTopState = rememberUpdatedState(safeTopDp)
    val safeBottomState = rememberUpdatedState(safeBottomDp)
    // 设置可能在页面存活期间被改动：WebView 回调里始终读最新值，无需重建 WebView
    val adaptiveCompatEnabledState = rememberUpdatedState(adaptiveCompatEnabled)
    val harnessVersionState = rememberUpdatedState(harnessVersion)

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = pendingFileCallback.value
        pendingFileCallback.value = null
        // 必须回调，否则网页侧的 file input 会永久卡住
        callback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
        )
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 是否授权都不影响下载本身 */ }

    // 页面必须在 WebView 拿到真实尺寸之后再加载：否则前端读到的 window.innerHeight 是错的，
    // 会按错误的视口高度做居中与布局（表现为"居中的加载动画跑到很上面"）。
    val measuredSize = remember { mutableStateOf<IntSize?>(null) }

    val webView = remember {
        WebView(context).apply {
            settings.applySettings()
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = buildWebViewClient(
                context = context,
                profile = profile,
                alive = alive,
                safeTop = { safeTopState.value },
                safeBottom = { safeBottomState.value },
                adaptiveCompatEnabled = { adaptiveCompatEnabledState.value },
                harnessVersion = { harnessVersionState.value },
                onCanGoBack = { value -> onCanGoBackState.value(value) },
                onNavigationStarted = { onNavigationStartedState.value() },
                onLoadError = { failed -> onLoadErrorState.value(failed) },
                onWebAuthPage = { page -> onWebAuthPageState.value(page) },
                onBackgroundColor = { color -> onBackgroundColorState.value(color) },
                onEvent = { event -> onEventState.value(event) },
            )
            webChromeClient = buildWebChromeClient(
                pendingFileCallback = pendingFileCallback,
                fileLauncher = fileLauncher,
                onProgress = { value -> onProgressState.value(value) },
            )
            setDownloadListener(
                buildDownloadListener(
                    context = context,
                    profile = profile,
                    notificationLauncher = notificationLauncher,
                    onEvent = { event -> onEventState.value(event) },
                ),
            )
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.onSizeChanged { size ->
            if (measuredSize.value == null && size.width > 0 && size.height > 0) {
                measuredSize.value = size
            }
        },
    )

    val size = measuredSize.value
    LaunchedEffect(webView, size) {
        if (size == null) return@LaunchedEffect
        onCreatedState.value(webView)
        webView.loadUrl(startUrl)
    }

    // 状态栏/导航栏/键盘高度变化（旋转、折叠屏、软键盘）时同步刷新注入。
    // 首次加载由 onPageFinished 负责（那时页面才存在），这里只在尺寸确定后跟随 Insets 变化。
    //
    // 两层都只按"当次加载时的连接组配置"注入，不做反向清理：适配开关与版本属于连接组，
    // 只会在重新进入本页（重建 WebView）时改变，本页存活期间不会变。
    LaunchedEffect(webView, size, safeTopDp, safeBottomDp, adaptiveCompatEnabled, harnessVersion) {
        if (size == null) return@LaunchedEffect
        // 基础层无条件施加：它只保证页面能显示，关掉响应式适配后同样需要
        applyBaseAdapt(webView, safeTopDp, safeBottomDp)
        if (adaptiveCompatEnabled) applyVersionAdapt(webView, harnessVersion, safeTopDp, safeBottomDp)
    }

    // 状态栏图标的明暗要实时跟随网页顶部背景：深浅主题切换、无刷新跳转都可能改底色，
    // 因此持续采样；颜色稳定后放慢频率，颜色变化才向上抛出（避免无谓重组）。
    LaunchedEffect(webView) {
        var last: Color? = null
        var stableRounds = 0
        while (true) {
            delay(
                if (stableRounds >= STABLE_BACKGROUND_ROUNDS) BACKGROUND_IDLE_SAMPLE_INTERVAL_MS
                else BACKGROUND_SAMPLE_INTERVAL_MS,
            )
            if (measuredSize.value == null) continue
            sampleBackgroundColor(webView, safeTopState.value) { color ->
                if (color == last) {
                    stableRounds++
                } else {
                    last = color
                    stableRounds = 0
                    onBackgroundColorState.value(color)
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            alive.value = false
            runCatching {
                webView.stopLoading()
                webView.webChromeClient = null
                webView.webViewClient = WebViewClient()
                webView.setDownloadListener(null)
                webView.removeAllViews()
                webView.destroy()
            }.onFailure { Log.w(TAG, "销毁 WebView 失败", it) }
        }
    }
}

private const val TAG = "DshaWebView"
private const val MAX_AUTH_ATTEMPTS = 3
private const val AUTOFILL_MAX_ATTEMPTS = 4
private const val AUTOFILL_RETRY_DELAY_MS = 600L
private const val AUTOFILL_FALLBACK_DELAY_MS = 4_000L

/**
 * 注入网页的尺寸一律用 dp：网页 viewport 为 `width=device-width, initial-scale=1`，
 * 即 1 CSS px = 1 dp；传物理像素会在高密度屏上被放大 2~3 倍。
 */
private fun fmtDp(value: Float): String = String.format(Locale.US, "%.2f", value)

/** 网页顶部背景色的采样节奏：颜色未稳定时密集采样，稳定后降频。 */
private const val BACKGROUND_SAMPLE_INTERVAL_MS = 500L
private const val BACKGROUND_IDLE_SAMPLE_INTERVAL_MS = 2_000L
private const val STABLE_BACKGROUND_ROUNDS = 4


private fun WebSettings.applySettings() {
    javaScriptEnabled = true
    domStorageEnabled = true
    loadsImagesAutomatically = true
    // 保持 WebView 默认的视口行为：布局视口与视觉视口一致，
    // 页面按自身的 viewport meta 与 WebView 尺寸布局，不做宽视口/overview 缩放。
    useWideViewPort = false
    loadWithOverviewMode = false
    setSupportZoom(true)
    builtInZoomControls = true
    displayZoomControls = false
    mediaPlaybackRequiresUserGesture = false
    allowFileAccess = false
    allowContentAccess = true
    // 允许 window.open：不打开会拖垮整个界面的弹窗（不少前端把登录/详情放在新窗口里）
    setSupportMultipleWindows(true)
    javaScriptCanOpenWindowsAutomatically = true
}

private fun buildWebViewClient(
    context: Context,
    profile: Profile,
    alive: MutableState<Boolean>,
    safeTop: () -> Float,
    safeBottom: () -> Float,
    adaptiveCompatEnabled: () -> Boolean,
    harnessVersion: () -> HarnessVersion,
    onCanGoBack: (Boolean) -> Unit,
    onNavigationStarted: () -> Unit,
    onLoadError: (Boolean) -> Unit,
    onWebAuthPage: (WebAuthPage?) -> Unit,
    onBackgroundColor: (Color) -> Unit,
    onEvent: (WebEvent) -> Unit,
): WebViewClient = object : WebViewClient() {

    /** 每个 host+realm 已应答的次数，防止凭据错误时陷入 401 → 应答 → 401 的死循环。 */
    private val authAttempts = mutableMapOf<String, Int>()

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        shouldOpenExternally(context, profile, request.url, onEvent)

    /**
     * 自建服务常在外层套一道 HTTP Basic 鉴权（返回 401 + WWW-Authenticate: Basic）。
     * 桌面浏览器会弹出原生输入框，WebView 不会：不在这里用组里的凭据应答，
     * 主框架就会直接以 ERR_HTTP_RESPONSE_CODE_FAILURE 加载失败。
     */
    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String,
    ) {
        val key = "$host|$realm"
        val attempts = authAttempts[key] ?: 0
        // 本组关闭凭据时一律放弃应答：站点确实需要 Basic 鉴权时会走失败流程
        if (!profile.credentialsEnabled || profile.username.isBlank() || attempts >= MAX_AUTH_ATTEMPTS) {
            handler.cancel()
            onEvent(WebEvent.AUTH_FAILED)
            return
        }
        authAttempts[key] = attempts + 1

        // 同时写进 WebView 的鉴权缓存：WebSocket 握手、Worker、iframe 这类请求不会再触发本回调，
        // 只能靠缓存里的凭据过掉外层的 Basic 鉴权。
        runCatching {
            WebViewDatabase.getInstance(context)
                .setHttpAuthUsernamePassword(host, realm, profile.username, profile.password)
        }.onFailure { Log.w(TAG, "写入 HTTP 鉴权缓存失败", it) }

        handler.proceed(profile.username, profile.password)
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        onLoadError(false)
        // 主框架开始新一次导航：访问认证流程要据此复位启动加载序列
        onNavigationStarted()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        onCanGoBack(view.canGoBack())
        probeWebAuthPage(view, alive, onWebAuthPage)
        // 本组关闭凭据时不自动填充登录表单
        if (profile.credentialsEnabled) {
            scheduleAutoFill(view, profile, alive, attempt = 0, onFallback = { onEvent(WebEvent.AUTOFILL_FALLBACK) })
        }
        // 基础层：安全区 + 视口高度链兜底，只保证页面能显示，不碰任何组件；
        // 与版本、与适配开关都无关，因此放在开关判断之外
        applyBaseAdapt(view, safeTop(), safeBottom())
        applyShellCompat(view)
        // 版本层：该版本专属的组件级改造，按连接组选定的 Harness 版本注入；
        // 关闭响应式适配时不做任何改造，页面仍按基础层正常显示
        if (adaptiveCompatEnabled()) {
            applyVersionAdapt(view, harnessVersion(), safeTop(), safeBottom())
        }
        // 每次整页加载完成后重新采样：状态栏图标明暗要跟随网页顶部背景
        sampleBackgroundColor(view, safeTop(), onBackgroundColor)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) onLoadError(true)
        Log.w(TAG, "请求失败 ${error.errorCode} ${error.description} ${request.url}")
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        onCanGoBack(view.canGoBack())
    }
}

private fun buildWebChromeClient(
    pendingFileCallback: MutableState<ValueCallback<Array<Uri>>?>,
    fileLauncher: ActivityResultLauncher<Intent>,
    onProgress: (Int) -> Unit,
): WebChromeClient = object : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgress(newProgress)
    }

    /** 页面里的报错只落到 Logcat，方便需要时用 adb 排查。 */
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
            Log.e(
                TAG,
                "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
            )
        }
        return true
    }

    /** 不另开窗口，把 window.open 的目标交回当前 WebView，否则弹窗会被静默丢弃。 */
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = view
        resultMsg.sendToTarget()
        return true
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        pendingFileCallback.value?.onReceiveValue(null)
        pendingFileCallback.value = filePathCallback
        return runCatching {
            fileLauncher.launch(fileChooserParams.createIntent())
            true
        }.getOrElse {
            pendingFileCallback.value = null
            filePathCallback.onReceiveValue(null)
            false
        }
    }
}

private fun buildDownloadListener(
    context: Context,
    profile: Profile,
    notificationLauncher: ActivityResultLauncher<String>,
    onEvent: (WebEvent) -> Unit,
): DownloadListener = DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
    // blob:/data: 的下载需要网页侧用 JS 读出内容再落盘，这里不做处理
    if (url.startsWith("blob:") || url.startsWith("data:")) {
        onEvent(WebEvent.DOWNLOAD_FAILED)
        return@DownloadListener
    }
    runCatching {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val manager = context.getSystemService(DownloadManager::class.java)
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mimeType)
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        userAgent?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("User-Agent", it) }
        // 带鉴权插件的部署会校验 Cookie，下载请求必须复用 WebView 的会话
        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
        // 外层 HTTP Basic 鉴权不认 Cookie，下载器需要额外带上 Authorization
        UrlUtils.basicAuthHeader(profile.username, profile.password)
            ?.let { request.addRequestHeader("Authorization", it) }

        manager.enqueue(request)
        onEvent(WebEvent.DOWNLOAD_STARTED)
    }.onFailure {
        Log.w(TAG, "下载失败", it)
        onEvent(WebEvent.DOWNLOAD_FAILED)
    }
}



/**
 * 采样网页顶部背景色，用于决定状态栏图标的明暗（沉浸后状态栏直接浮在网页上）。
 *
 * 取页面顶边中间位置的元素，沿祖先链找第一个不透明背景，与浏览器实际的叠色顺序一致；
 * 整条链都透明时按 WebView 默认白底处理。半透明色按白色底合成，
 * 页面用深色主题、而后端仍是白底等情况都能拿到真实颜色。
 *
 * 采样点要避开注入给 `html` 的顶部 padding（那一带露出的是画布背景，命中的是 html 自身），
 * 所以从安全高度往下 2px 取点；抽屉展开时该点自然落在侧栏上，图标随之切换也符合预期。
 */
private fun sampleBackgroundColor(webView: WebView, safeTopDp: Float, onResult: (Color) -> Unit) {
    webView.evaluateJavascript(buildBackgroundColorScript(safeTopDp)) { raw ->
        val parts = raw?.trim('"').orEmpty().split(',')
        if (parts.size != 3) return@evaluateJavascript
        val rgb = parts.mapNotNull { it.trim().toIntOrNull() }
        if (rgb.size != 3) return@evaluateJavascript
        onResult(Color(rgb[0], rgb[1], rgb[2]))
    }
}

private fun buildBackgroundColorScript(safeTopDp: Float): String = """
    (function () {
      try {
        function parse(color) {
          if (!color || color === 'transparent') { return null; }
          var m = color.match(/rgba?\(([^)]+)\)/);
          if (!m) { return null; }
          var parts = m[1].split(',');
          var a = parts.length > 3 ? parseFloat(parts[3]) : 1;
          if (!(a > 0)) { return null; }
          var mix = function (v) {
            var n = parseInt(v, 10);
            if (isNaN(n)) { return null; }
            return Math.round(n * a + 255 * (1 - a));
          };
          var r = mix(parts[0]), g = mix(parts[1]), b = mix(parts[2]);
          if (r === null || g === null || b === null) { return null; }
          return r + ',' + g + ',' + b;
        }
        var node = null;
        try {
          var y = Math.round(${fmtDp(safeTopDp)}) + 2;
          node = document.elementFromPoint(Math.floor(window.innerWidth / 2), y);
        } catch (e) { node = null; }
        if (!node) { node = document.body || document.documentElement; }
        while (node && node.nodeType === 1) {
          var current = parse(getComputedStyle(node).backgroundColor);
          if (current) { return current; }
          node = node.parentElement;
        }
        return '255,255,255';
      } catch (e) {
        return '';
      }
    })();
"""

/** 同站点链接留在壳内，其余交给系统浏览器；返回 true 表示链接已被外部处理。 */
private fun shouldOpenExternally(
    context: Context,
    profile: Profile,
    target: Uri,
    onEvent: (WebEvent) -> Unit,
): Boolean {
    val scheme = target.scheme?.lowercase().orEmpty()
    if (scheme != "http" && scheme != "https") {
        if (scheme.isEmpty()) return false
        return launchExternally(context, target, onEvent)
    }
    if (UrlUtils.isSameSite(target.toString(), profile.baseUrl)) return false
    return launchExternally(context, target, onEvent)
}

private fun launchExternally(context: Context, target: Uri, onEvent: (WebEvent) -> Unit): Boolean =
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "没有可处理该链接的应用：$target", e)
        onEvent(WebEvent.NO_BROWSER)
        true
    }

/**
 * 检测当前页面是不是访问认证插件（`@fonlan/dsh-web-auth`）的登录页。
 *
 * 每次整页加载只查一次：插件页面是服务端渲染的，走到 onPageFinished 时 DOM 已经就绪，
 * 不需要像 SPA 那样重试。回调 null 表示「这一页不是插件登录页」，界面层据此判定
 * 已经离开登录页（登录成功后回到应用页就是这种情况）。
 */
private fun probeWebAuthPage(
    webView: WebView,
    alive: MutableState<Boolean>,
    onResult: (WebAuthPage?) -> Unit,
) {
    webView.evaluateJavascript(WEB_AUTH_DETECT_SCRIPT) { raw ->
        if (!alive.value) return@evaluateJavascript
        onResult(parseWebAuthPage(raw))
    }
}

/**
 * 在真实登录页里填充账号密码并提交。
 *
 * DeepSeek Harness 本体不提供账号密码登录，鉴权由第三方插件或反向代理提供，
 * 路径与字段名因部署而异，所以这里不猜接口，而是在页面上下文中直接操作 DOM：
 * 这样天然兼容隐藏的 CSRF 字段、多语言界面以及 TOTP 二次验证的跳转。
 */
private fun scheduleAutoFill(
    webView: WebView,
    profile: Profile,
    alive: MutableState<Boolean>,
    attempt: Int,
    onFallback: () -> Unit,
) {
    if (!alive.value) return

    webView.evaluateJavascript(buildFillScript(profile)) { raw ->
        if (!alive.value) return@evaluateJavascript
        val result = raw?.trim('"').orEmpty()
        when {
            result == "ok" -> {
                val filledUrl = webView.url
                webView.postDelayed({
                    if (alive.value && webView.url == filledUrl) onFallback()
                }, AUTOFILL_FALLBACK_DELAY_MS)
            }

            result == "nopage" && attempt < AUTOFILL_MAX_ATTEMPTS -> {
                // 单页应用可能晚于 onPageFinished 渲染出登录表单，几次重试足够覆盖
                webView.postDelayed({
                    scheduleAutoFill(webView, profile, alive, attempt + 1, onFallback)
                }, AUTOFILL_RETRY_DELAY_MS)
            }

            result.startsWith("error") -> {
                Log.w(TAG, "自动填充脚本报错：$result")
                onFallback()
            }

            else -> Unit // skip：本次加载已经注入过，忽略即可
        }
    }
}

private fun buildFillScript(profile: Profile): String {
    val user = JSONObject.quote(profile.username)
    val pass = JSONObject.quote(profile.password)
    val userSelector = JSONObject.quote(
        "input[name=\"username\"],input[name=\"user\"],input[name=\"email\"]," +
            "input[name=\"account\"],input[name=\"login\"]," +
            "input[type=text],input[type=email],input:not([type])",
    )

    return """
        (function () {
          try {
            if (window.__dshaFilled === true) { return 'skip'; }
            /* 访问认证插件的登录页由本地闸门接管，Basic 自动填充不参与，避免两边抢填同一个密码框 */
            if ($WEB_AUTH_FILL_GUARD) { return 'skip'; }
            var pwd = document.querySelector('input[type=password]');
            if (!pwd) { return 'nopage'; }
            function setValue(el, value) {
              var desc = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value');
              if (desc && desc.set) { desc.set.call(el, value); } else { el.value = value; }
              el.dispatchEvent(new Event('input', { bubbles: true }));
              el.dispatchEvent(new Event('change', { bubbles: true }));
            }
            var user = document.querySelector($userSelector);
            if (user) { setValue(user, $user); }
            setValue(pwd, $pass);
            window.__dshaFilled = true;
            var form = pwd.form;
            if (form) {
              if (form.requestSubmit) { form.requestSubmit(); } else { form.submit(); }
            } else {
              var button = document.querySelector('button[type=submit],input[type=submit]');
              if (button) { button.click(); }
            }
            return 'ok';
          } catch (e) {
            return 'error:' + e;
          }
        })();
    """.trimIndent()
}

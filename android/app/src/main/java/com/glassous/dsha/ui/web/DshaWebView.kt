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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.glassous.dsha.data.Profile
import com.glassous.dsha.data.UrlUtils
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
 */
@Composable
fun DshaWebView(
    profile: Profile,
    startUrl: String,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {},
    onProgress: (Int) -> Unit = {},
    onCanGoBackChanged: (Boolean) -> Unit = {},
    onLoadError: (Boolean) -> Unit = {},
    onEvent: (WebEvent) -> Unit = {},
) {
    val context = LocalContext.current
    val alive = remember { mutableStateOf(true) }
    val pendingFileCallback = remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val onProgressState = rememberUpdatedState(onProgress)
    val onCanGoBackState = rememberUpdatedState(onCanGoBackChanged)
    val onLoadErrorState = rememberUpdatedState(onLoadError)
    val onEventState = rememberUpdatedState(onEvent)
    val onCreatedState = rememberUpdatedState(onWebViewCreated)

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
                onCanGoBack = { value -> onCanGoBackState.value(value) },
                onLoadError = { failed -> onLoadErrorState.value(failed) },
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
private const val SHELL_COMPAT_DELAY_MS = 1_500L
private const val DIALOG_MIN_SIDE_PX = 2
private const val DIALOG_MAX_WIDTH_PX = 380
private const val DIALOG_VIEWPORT_MARGIN_PX = 48

/**
 * 视口单位兜底：部分设备的 WebView 里，以视口为基准的百分比与 `vh` 都会解析成 **0**
 * （JS 侧的 `innerHeight` 却是正常值），于是所有依赖它们的高度/宽度约束都会塌陷。
 * 这里全部改用 JS 读到的像素值，绕过单位解析；只在量到塌陷时才动手，正常环境不受影响。
 *
 * 两处受影响的地方：
 * 1. DSH 的 base.css 用 `html、body、#root { height: 100% }` 逐级传高度（见其底座样式），
 *    整条链塌成 0，界面只剩溢出可见；
 * 2. 它的弹窗卡片是 `width: min(380px, 100%)` + `max-height: 100%`，同样会被算成 0 尺寸，
 *    表现为"只看到背景模糊、看不到弹窗面板"（遮罩用的是 inset 拉伸，所以它没事）。
 */
private val SHELL_COMPAT_JS = """
    (function () {
      try {
        var html = document.documentElement;
        var body = document.body;
        var root = document.getElementById('root');
        var height = Math.round(window.innerHeight || 0);
        if (height <= 0) { height = Math.round(html.clientHeight || 0); }

        var applyRootHeight = function () {
          var h = Math.round(window.innerHeight || height);
          if (h <= 0) { return; }
          var px = h + 'px';
          html.style.setProperty('height', px, 'important');
          if (body !== null) { body.style.setProperty('height', px, 'important'); }
          if (root !== null) { root.style.setProperty('height', px, 'important'); }
        };

        var collapsed = height > 0 && (
          html.getBoundingClientRect().height <= 0 ||
          (body !== null && body.getBoundingClientRect().height <= 0) ||
          (root !== null && root.getBoundingClientRect().height === 0));
        if (collapsed) {
          window.__dshaRootFixed = true;
          applyRootHeight();
          // 前端可能已按塌陷尺寸量过一次，补一次 resize 让它重新测量
          window.dispatchEvent(new Event('resize'));
        }

        // 弹窗卡片：量到 0 尺寸才补，等价于它自己想要的 min(380px,100%) / max-height:100%
        var fixDialogs = function () {
          var list = document.querySelectorAll('[role=dialog],[role=alertdialog]');
          var w = Math.max(Math.round(window.innerWidth || 0) - $DIALOG_VIEWPORT_MARGIN_PX, 0);
          var h = Math.max(Math.round(window.innerHeight || 0) - $DIALOG_VIEWPORT_MARGIN_PX, 0);
          if (w <= 0 || h <= 0) { return; }
          var width = Math.min($DIALOG_MAX_WIDTH_PX, w);
          for (var i = 0; i < list.length; i++) {
            var el = list[i];
            var rescued = el.hasAttribute('data-dsha-compat');
            var rect = el.getBoundingClientRect();
            // 没量到 0 尺寸的正常弹窗一律不碰；已兜底过的继续跟随视口尺寸
            if (!rescued && rect.width > $DIALOG_MIN_SIDE_PX && rect.height > $DIALOG_MIN_SIDE_PX) {
              continue;
            }
            if (!rescued) { el.setAttribute('data-dsha-compat', '1'); }
            el.style.setProperty('width', width + 'px', 'important');
            el.style.setProperty('height', 'auto', 'important');
            el.style.setProperty('max-height', h + 'px', 'important');
          }
        };
        fixDialogs();

        if (!window.__dshaCompatBound) {
          window.__dshaCompatBound = true;
          var remeasure = function () {
            if (window.__dshaRootFixed) { applyRootHeight(); }
            fixDialogs();
          };
          window.addEventListener('resize', remeasure);
          window.addEventListener('orientationchange', function () { setTimeout(remeasure, 300); });
          // 弹窗是动态挂载到 body 上的，监听 body 的直接子节点变化就够了（不必 subtree，避免流式渲染时频繁触发）
          if (body !== null && typeof MutationObserver === 'function') {
            new MutationObserver(fixDialogs).observe(body, { childList: true });
          }
        }
        return 'ok';
      } catch (e) {
        return 'error:' + e;
      }
    })();
"""

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
    onCanGoBack: (Boolean) -> Unit,
    onLoadError: (Boolean) -> Unit,
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
        if (profile.username.isBlank() || attempts >= MAX_AUTH_ATTEMPTS) {
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
    }

    override fun onPageFinished(view: WebView, url: String?) {
        onCanGoBack(view.canGoBack())
        scheduleAutoFill(view, profile, alive, attempt = 0, onFallback = { onEvent(WebEvent.AUTOFILL_FALLBACK) })
        applyShellCompat(view)
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

/** 页面稳定后做一次视口单位兜底，并按需持续看护动态挂载的弹窗（详见 [SHELL_COMPAT_JS]）。 */
private fun applyShellCompat(webView: WebView) {
    webView.postDelayed({
        webView.evaluateJavascript(SHELL_COMPAT_JS) { result ->
            Log.d(TAG, "视口兜底: ${result?.trim('"')}")
        }
    }, SHELL_COMPAT_DELAY_MS)
}

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

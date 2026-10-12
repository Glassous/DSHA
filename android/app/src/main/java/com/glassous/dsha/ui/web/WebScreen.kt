package com.glassous.dsha.ui.web

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.glassous.dsha.R
import com.glassous.dsha.ui.dshaViewModel
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** 加载完成后的提示停留时长：这段时间内文案为"即将准备就绪"。 */
private const val READY_HINT_DURATION_MS = 1_500L

/** 启动动画层撤除时的原地淡出时长。 */
private const val LOADING_FADE_MS = 200

@Composable
fun WebScreen(
    profileId: String,
    onBack: () -> Unit,
) {
    val viewModel: WebViewModel = dshaViewModel(key = "web-$profileId") { application ->
        WebViewModel(application, profileId)
    }
    // 用 LocalResources 而不是 LocalContext.current.resources：后者不跟随配置变化
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    // 「复制错误信息」用（Android 13 起复制后系统自己会弹提示，这里不再额外反馈）
    val context = LocalContext.current
    var messageRes by remember { mutableStateOf<Int?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var closeConfirmVisible by remember { mutableStateOf(false) }

    val profile = viewModel.profile
    val startUrl = viewModel.startUrl

    // 顶栏沉浸：网页直接铺满整屏（含状态栏区域），顶部与底部的安全距离都注入网页内部，
    // 由 html 的 padding 承担（border-box，页面总高不变，因此不会多出上下滚动）。
    // 网页 viewport 为 initial-scale=1，1 CSS px = 1 dp，故这里下发的必须是 dp 值。
    val density = LocalDensity.current
    val safeTopDp = with(density) { WindowInsets.statusBars.getTop(density).toDp().value }
    val navBarsBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp().value }
    val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp().value }
    // 键盘弹出时 WebView 已整体上移，不再重复避让底部导航栏
    val safeBottomDp = if (imeBottomDp > navBarsBottomDp) 0f else navBarsBottomDp
    // 网页顶部背景色（实时采样）；WebView 初始是白底，首次采样到之前先用白色兜底
    var webBackground by remember { mutableStateOf(Color.White) }

    // 沉浸后状态栏直接浮在网页之上，图标明暗必须跟随网页背景而不是应用主题；
    // 离开本页时再交还给应用主题（否则会影响其它页面）。
    val view = LocalView.current
    val appDark = isSystemInDarkTheme()
    if (!view.isInEditMode) {
        DisposableEffect(webBackground, appDark) {
            val window = (view.context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val lightIcons = webBackground.luminance() > 0.5f
            controller?.isAppearanceLightStatusBars = lightIcons
            controller?.isAppearanceLightNavigationBars = lightIcons
            onDispose {
                controller?.isAppearanceLightStatusBars = !appDark
                controller?.isAppearanceLightNavigationBars = !appDark
            }
        }
    }

    LaunchedEffect(messageRes) {
        messageRes?.let { res ->
            snackbarHostState.showSnackbar(resources.getString(res))
            messageRes = null
        }
    }

    // 首次加载到 100% 后不立即露出网页：先停留两秒（文案切到"即将准备就绪"）。
    // 失败时不进入就绪阶段：错误优先，覆盖层原地切成本地渲染的错误提示
    LaunchedEffect(viewModel.progress) {
        if (viewModel.progress >= 100 && viewModel.loadError == null) viewModel.loadFinished = true
    }
    // 停留结束后再撤掉启动动画；之后的页面内导航不再遮挡网页。
    // key 带上 loadError：失败会让本 effect 重启并跳过置位，因此 4xx/5xx 之后不会再有
    // "即将准备就绪"，错误提示也就不会被覆盖层自己的时序顶掉
    LaunchedEffect(viewModel.loadFinished, viewModel.loadError) {
        if (viewModel.loadFinished && viewModel.loadError == null) {
            delay(READY_HINT_DURATION_MS)
            viewModel.pageReady = true
        }
    }

    // 返回键：优先回退网页历史；已到起点时先弹确认弹窗，确认后才关闭本页
    BackHandler {
        val current = webView
        when {
            // 还没有网页（加载中或缺组）时没有可关闭的服务页面，直接返回
            current == null -> onBack()
            // 访问认证进行中（静默自动登录或已提交，此时没有弹窗）：返回等同于放弃本地接管，
            // 撤掉遮挡露出网页登录页。弹窗自己的返回由 Dialog 先消费，走 onDismiss。
            viewModel.webAuthGate != null -> viewModel.onWebAuthAbandoned()
            // 用户主动返回：错误提示按用户的意愿撤下，回上一页重新加载
            current.canGoBack() -> {
                viewModel.dismissLoadError()
                current.goBack()
            }

            else -> closeConfirmVisible = true
        }
    }

    // 提交脚本没能生效（页面已被换掉、或填进去的值过不了页面自身校验）：
    // 放弃本地接管，露出网页原生登录页，别让用户卡在覆盖层里
    val onWebAuthSubmitResult: (Boolean) -> Unit = { submitted ->
        if (!submitted) viewModel.onWebAuthAbandoned()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        },
        // 顶栏沉浸：顶部不在此预留，网页铺满整屏（含状态栏区域）；
        // 底部由注入网页内部的底部安全距离保证内容不被小白条遮挡；左右仍避让挖孔区。
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val currentProfile = profile
        val currentStartUrl = startUrl
        when {
            // 阶段一：本地读取连接组，尚未创建 WebView（此时还谈不上加载失败，故不带错误分支）
            viewModel.loading -> StartupStage(
                error = null,
                loadingText = stringResource(R.string.web_starting),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            currentProfile == null || currentStartUrl == null -> MissingProfileContent(
                onBack = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            else -> key(currentProfile.id) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding(),
                ) {
                    DshaWebView(
                        profile = currentProfile,
                        startUrl = currentStartUrl,
                        // 网页适配按连接组走：同一个应用可能同时连向不同版本的 Harness
                        adaptiveCompatEnabled = currentProfile.adaptiveCompatEnabled,
                        harnessVersion = currentProfile.harnessVersion,
                        // 铺满整屏（含状态栏区域）：顶部安全高度由注入网页内部的 html padding 承担
                        modifier = Modifier.fillMaxSize(),
                        safeTopDp = safeTopDp,
                        safeBottomDp = safeBottomDp,
                        onWebViewCreated = { created -> webView = created },
                        onBackgroundColor = { color -> webBackground = color },
                        onProgress = { value -> viewModel.progress = value },
                        // 闸门激活期间每次主框架导航都要复位加载序列：登录提交后跳回应用页时，
                        // 启动动画得按新页面自己的进度重走一遍，露出时才是渲染完整的页面
                        onNavigationStarted = { viewModel.onNavigationStarted() },
                        onLoadError = { error -> viewModel.reportLoadError(error) },
                        onWebAuthPage = { page ->
                            if (page == null) {
                                // 已不是插件登录页：认证结束，闸门落下（是提交成功则顺手记住密码）
                                viewModel.onWebAuthLeft()
                            } else {
                                val autoLoginPassword = viewModel.onWebAuthPageDetected(page)
                                if (autoLoginPassword != null) {
                                    // 静默自动登录：全程只显示「自动登录中」，不弹窗、不露出网页登录页
                                    val target = webView
                                    if (target == null) {
                                        viewModel.onWebAuthAbandoned()
                                    } else {
                                        submitWebAuth(
                                            webView = target,
                                            password = autoLoginPassword,
                                            // 登录态没有确认框，传同一份即可
                                            confirm = autoLoginPassword,
                                            onResult = onWebAuthSubmitResult,
                                        )
                                    }
                                }
                            }
                        },
                        onEvent = { event ->
                            messageRes = when (event) {
                                WebEvent.AUTOFILL_FALLBACK -> R.string.web_autofill_fallback
                                WebEvent.NO_BROWSER -> R.string.web_no_browser
                                WebEvent.DOWNLOAD_STARTED -> R.string.web_download_started
                                WebEvent.DOWNLOAD_FAILED -> R.string.web_download_failed
                            }
                        },
                    )

                    // 阶段二：网页加载资源期间同样不露出 web，用启动动画整体盖住；
                    // 加载完成后再多停留 1.5 秒（"即将准备就绪"），随后原地快速淡出。
                    // 加载失败时覆盖层不撤：内容原地换成错误圆与错误码，等用户刷新或退出
                    AnimatedVisibility(
                        // 访问认证闸门没落下就始终遮挡：插件登录页在认证结束前一律不许露出来
                        visible = viewModel.webAuthGate != null ||
                            !viewModel.pageReady ||
                            viewModel.loadError != null,
                        // 进入无动画，撤除时只做 alpha 淡出：不位移、不缩放，网页从下方渐显
                        enter = EnterTransition.None,
                        exit = fadeOut(animationSpec = tween(LOADING_FADE_MS)),
                    ) {
                        StartupStage(
                            error = viewModel.loadError,
                            loadingText = stringResource(
                                when {
                                    // 静默自动登录期间：文案跟着闸门状态走
                                    viewModel.webAuthGate is WebAuthGate.AutoLoggingIn ->
                                        R.string.web_auto_logging_in

                                    viewModel.loadFinished -> R.string.web_ready_soon
                                    else -> R.string.web_starting_harness
                                },
                            ),
                            onCopyError = {
                                viewModel.loadError?.let { error ->
                                    copyLoadError(context, error, profile?.displayName)
                                }
                            },
                            onRefresh = {
                                // 刷新同样完整重走启动动画
                                viewModel.restartLoading()
                                webView?.reload()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    if (closeConfirmVisible) {
        CloseConfirmDialog(
            // 顶栏被移除了，这里承担顶栏的职责：显示当前服务名称 + 刷新入口
            title = profile?.displayName ?: stringResource(R.string.app_title),
            canRefresh = webView != null,
            onRefresh = {
                closeConfirmVisible = false
                // 刷新同样完整重走启动动画
                viewModel.restartLoading()
                webView?.reload()
            },
            onConfirm = {
                closeConfirmVisible = false
                onBack()
            },
            onDismiss = { closeConfirmVisible = false },
        )
    }

    // 访问认证：插件登录页由本地弹窗接管。静默自动登录时不弹窗，只剩覆盖层与「自动登录中」
    val webAuthGate = viewModel.webAuthGate
    if (webAuthGate is WebAuthGate.Prompt) {
        // 失败重弹时优先保留用户刚输入的内容，其次才是连接组里已记住的密码
        val prefill = viewModel.webAuthLastInput.ifEmpty { profile?.webAuthPassword.orEmpty() }
        val submit: (String) -> Unit = { password ->
            val target = webView
            if (target == null) {
                viewModel.onWebAuthAbandoned()
            } else {
                viewModel.onWebAuthSubmitted(password)
                submitWebAuth(target, password, password, onWebAuthSubmitResult)
            }
        }
        if (webAuthGate.setup) {
            WebAuthSetupDialog(
                initialPassword = prefill,
                serverError = webAuthGate.errorText,
                onConfirm = submit,
                // 取消＝放弃本地接管：撤掉遮挡，露出网页原生的登录页让用户自己操作
                onDismiss = { viewModel.onWebAuthAbandoned() },
            )
        } else {
            WebAuthLoginDialog(
                initialPassword = prefill,
                serverError = webAuthGate.errorText,
                onConfirm = submit,
                onDismiss = { viewModel.onWebAuthAbandoned() },
            )
        }
    }
}

/** 启动动画与错误圆的容器尺寸：两者必须一致，切换时才不会位移或跳动。 */
private val STARTUP_INDICATOR_SIZE = 80.dp

/** 错误圆内感叹号的尺寸。 */
private val ERROR_MARK_SIZE = 40.dp

/** 指示器两种形态之间过渡的进入 / 退出时长：放慢一点，形状的变化才看得清。 */
private const val INDICATOR_ENTER_MS = 420
private const val INDICATOR_EXIT_MS = 320

/** 错误操作区自身的淡入淡出时长。 */
private const val ACTION_FADE_IN_MS = 260
private const val ACTION_FADE_OUT_MS = 160

/** 操作区与错误码之间的距离。 */
private val ACTION_TOP_GAP = 12.dp

/** 复制与刷新两颗按钮之间的水平间距。 */
private val ACTION_GAP = 12.dp

/** 「已复制」回执的停留时长：够看清，又不至于一直挂着。 */
private const val COPIED_HINT_DURATION_MS = 2_000L

/** 复制出去的时间戳格式：24 小时制加时区偏移，任何语言下都是同一串机器可读的值。 */
private val COPY_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.US)

/**
 * 启动覆盖层：加载期间是形状过渡指示器 + 阶段文案，失败时原地换成错误圆 + 错误码，
 * 下方再给出复制与刷新入口。
 *
 * 指示器与文案在两个状态下必须落在同一坐标：指示器共用同一个尺寸固定的槽位，文案共用同一处
 * 槽位（错误时只是内容换成错误码，字号与行数都不变），操作区则靠 [zeroHeightOverlay] 只画在
 * 文案下方、不参与本列的高度统计——否则它会把这列顶高，居中的指示器与文案都会跟着往上跑。
 *
 * Material 3 没有定义「加载指示器 → 错误」的规范动效，这段过渡是自定义的；
 * 错误形态是静止的，不做循环动画。
 */
@Composable
private fun StartupStage(
    error: WebLoadError?,
    loadingText: String,
    // 只有错误态才用得上这两个动作，纯加载的调用点（阶段一）可以不给
    onCopyError: () -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 复制回执：用自增计数当 key 而不是布尔值，这样连点也每次都重新计时；
    // 错误对象本身当 key，换了一轮失败就从头开始亮
    var copyTicks by remember(error) { mutableIntStateOf(0) }
    var copied by remember(error) { mutableStateOf(false) }
    LaunchedEffect(copyTicks) {
        if (copyTicks == 0) return@LaunchedEffect
        copied = true
        delay(COPIED_HINT_DURATION_MS)
        copied = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(
            targetState = error == null,
            transitionSpec = {
                val enter = fadeIn(animationSpec = tween(INDICATOR_ENTER_MS)) +
                    scaleIn(animationSpec = tween(INDICATOR_ENTER_MS), initialScale = 0.8f)
                val exit = fadeOut(animationSpec = tween(INDICATOR_EXIT_MS)) +
                    scaleOut(animationSpec = tween(INDICATOR_EXIT_MS), targetScale = 0.8f)
                enter togetherWith exit
            },
            contentAlignment = Alignment.Center,
            label = "startup-indicator",
            modifier = Modifier.size(STARTUP_INDICATOR_SIZE),
        ) { loading ->
            if (loading) StartupIndicator() else ErrorIndicator()
        }
        Spacer(Modifier.height(16.dp))
        // 失败时这一行只显示错误码；要完整上下文就用下方的「复制错误信息」带走
        Text(
            text = error?.code ?: loadingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(animationSpec = tween(ACTION_FADE_IN_MS)),
            exit = fadeOut(animationSpec = tween(ACTION_FADE_OUT_MS)),
            modifier = Modifier.zeroHeightOverlay(),
        ) {
            // 底色与主页底部的新增按钮（FAB 的默认容器色）一致，两颗按钮共用同一套配色
            val containerColor = FloatingActionButtonDefaults.containerColor
            val contentColor = contentColorFor(containerColor)
            Row(
                modifier = Modifier.padding(top = ACTION_TOP_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = {
                        // 回执只是提示，不拦着再次复制：点几次就复制几次
                        onCopyError()
                        copyTicks++
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                ) {
                    // 两个文案叠在一起，取较宽的那个定宽：按钮宽度不随文案切换而跳动
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.action_copy_error),
                            modifier = Modifier.alpha(if (copied) 0f else 1f),
                        )
                        Text(
                            text = stringResource(R.string.action_copied),
                            modifier = Modifier.alpha(if (copied) 1f else 0f),
                        )
                    }
                }
                Spacer(Modifier.width(ACTION_GAP))
                FilledTonalIconButton(
                    onClick = onRefresh,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.cd_refresh),
                    )
                }
            }
        }
    }
}

/**
 * 只负责绘制，不占高度：内容照常测量并画在自己的位置上，但向父级汇报 0 高度。
 *
 * 错误态的操作区靠它做到「画在文案下方，却不把这一列顶高」，上方指示器与文案的居中结果
 * 因此与加载态完全一致——两个状态来回切换时不会有任何位移。
 */
private fun Modifier.zeroHeightOverlay(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, 0) { placeable.place(0, 0) }
}

/** 加载形态：MD3 Expressive 的形状过渡指示器（实验 API）。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StartupIndicator() {
    // 指示器默认容器 48dp，放大到 80dp（图形随容器等比缩放）
    LoadingIndicator(modifier = Modifier.size(STARTUP_INDICATOR_SIZE))
}

/**
 * 错误形态：实心圆与加载动画同色、同尺寸，内部的感叹号与下方文案同色。
 *
 * 圆与感叹号必须拆成两层：单个 vector 的 tint 会覆盖全部 path，一份 drawable 给不了两种颜色。
 */
@Composable
private fun ErrorIndicator() {
    Box(
        modifier = Modifier
            .size(STARTUP_INDICATOR_SIZE)
            .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_error_exclamation),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ERROR_MARK_SIZE),
        )
    }
}

/**
 * 把错误信息写进系统剪贴板。
 *
 * 界面上只留一个错误码，复制出去的这份则要能直接拿去排障：错误自身的码、说明与地址，
 * 再加上「哪个连接、什么时候、什么应用与系统」这些离开现场就补不回来的上下文。
 * Android 13 起复制后系统自己会弹提示，这里不再额外给反馈。
 *
 * @param profileName 出错连接组的名称：同一台设备上可能配了好几个组，光看地址未必分得清是哪个
 */
private fun copyLoadError(context: Context, error: WebLoadError, profileName: String?) {
    val title = context.getString(
        if (error.kind == WebErrorKind.Auth) R.string.web_auth_failed else R.string.web_loading_failed,
    )
    // 服务端没给说明时（HTTP/2 起响应根本不再带原因短语）用标准短语兜一句，
    // 否则用户复制到的就只是一个光秃秃的数字
    val detail = error.detail.ifBlank { standardHttpReason(error.code.toIntOrNull()) }
    val text = buildString {
        append(title)
        append('\n')
        append(context.getString(R.string.error_copy_code, error.code))
        if (detail.isNotBlank()) {
            append('\n')
            append(context.getString(R.string.error_copy_detail, detail))
        }
        error.url?.takeIf { it.isNotBlank() }?.let {
            append('\n')
            append(context.getString(R.string.error_copy_url, it))
        }
        profileName?.takeIf { it.isNotBlank() }?.let {
            append('\n')
            append(context.getString(R.string.error_copy_profile, it))
        }
        append('\n')
        append(
            context.getString(
                R.string.error_copy_time,
                COPY_TIME_FORMATTER.format(ZonedDateTime.now()),
            ),
        )
        readAppVersion(context).takeIf { it.isNotBlank() }?.let {
            append('\n')
            append(context.getString(R.string.error_copy_app_version, it))
        }
        append('\n')
        append(context.getString(R.string.error_copy_system, systemDescription()))
    }
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.error_copy_label), text))
}

/**
 * 常见 HTTP 状态码的标准短语。
 *
 * 用的就是 RFC 里那串英文：HTTP/2 起响应不再携带原因短语，服务端自定义的说明也常常为空，
 * [WebLoadError.detail] 因此可能是空串。有了这张表，用户至少知道 502 是网关的问题、
 * 503 是服务暂时不可用，而不是只有一个数字。网络层的负数码不在这张表里，原样返回空串。
 */
private fun standardHttpReason(code: Int?): String = when (code) {
    400 -> "Bad Request"
    401 -> "Unauthorized"
    403 -> "Forbidden"
    404 -> "Not Found"
    405 -> "Method Not Allowed"
    408 -> "Request Timeout"
    429 -> "Too Many Requests"
    500 -> "Internal Server Error"
    502 -> "Bad Gateway"
    503 -> "Service Unavailable"
    504 -> "Gateway Timeout"
    else -> ""
}

/** 系统与机型：WebView 的渲染与网络行为差异大多出在这两者上，排障时第一个就会被问到。 */
private fun systemDescription(): String =
    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MODEL}"

/** 当前安装的版本号，与 GitHub Release 的 tag 同源（tag 为 `v` + 这个版本号）。 */
private fun readAppVersion(context: Context): String = runCatching {
    context.packageManager
        .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        .versionName
}.getOrNull().orEmpty()

/** 关闭确认：顶栏已移除，这里同时给出当前服务名称与刷新入口。 */
@Composable
private fun CloseConfirmDialog(
    title: String,
    canRefresh: Boolean,
    onRefresh: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh, enabled = canRefresh) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh),
                        contentDescription = stringResource(R.string.cd_refresh),
                    )
                }
            }
        },
        text = { Text(stringResource(R.string.web_close_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun MissingProfileContent(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(R.string.web_profile_missing),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}

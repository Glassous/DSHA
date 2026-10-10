package com.glassous.dsha.ui.web

import android.app.Activity
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.glassous.dsha.R
import com.glassous.dsha.ui.dshaViewModel
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
    var messageRes by remember { mutableStateOf<Int?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    // 区分"站点鉴权失败"与普通网络错误，错误页才能给出正确指引
    var authFailed by remember { mutableStateOf(false) }
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

    // 首次加载到 100% 后不立即露出网页：先停留两秒（文案切到"即将准备就绪"）
    LaunchedEffect(viewModel.progress) {
        if (viewModel.progress >= 100) viewModel.loadFinished = true
    }
    // 停留结束后再撤掉启动动画；之后的页面内导航不再遮挡网页。
    // key 用 loadFinished（置位后不再变化），延迟不会因进度回调重启而中断
    LaunchedEffect(viewModel.loadFinished) {
        if (viewModel.loadFinished) {
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
            current.canGoBack() -> current.goBack()
            else -> closeConfirmVisible = true
        }
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
            // 阶段一：本地读取连接组，尚未创建 WebView
            viewModel.loading -> StartupLoading(
                text = stringResource(R.string.web_starting),
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
                        credentialsEnabled = viewModel.credentialsEnabled,
                        adaptiveCompatEnabled = viewModel.adaptiveCompatEnabled,
                        // 铺满整屏（含状态栏区域）：顶部安全高度由注入网页内部的 html padding 承担
                        modifier = Modifier.fillMaxSize(),
                        safeTopDp = safeTopDp,
                        safeBottomDp = safeBottomDp,
                        onWebViewCreated = { created -> webView = created },
                        onBackgroundColor = { color -> webBackground = color },
                        onProgress = { value -> viewModel.progress = value },
                        onLoadError = { failed -> viewModel.loadError = failed },
                        onEvent = { event ->
                            if (event == WebEvent.AUTH_FAILED) authFailed = true
                            messageRes = when (event) {
                                WebEvent.AUTOFILL_FALLBACK -> R.string.web_autofill_fallback
                                WebEvent.AUTH_FAILED -> R.string.web_auth_failed
                                WebEvent.NO_BROWSER -> R.string.web_no_browser
                                WebEvent.DOWNLOAD_STARTED -> R.string.web_download_started
                                WebEvent.DOWNLOAD_FAILED -> R.string.web_download_failed
                            }
                        },
                    )

                    // 阶段二：网页加载资源期间同样不露出 web，用启动动画整体盖住；
                    // 加载完成后再多停留 1.5 秒（"即将准备就绪"），随后原地快速淡出
                    AnimatedVisibility(
                        visible = !viewModel.pageReady && !viewModel.loadError,
                        // 进入无动画，撤除时只做 alpha 淡出：不位移、不缩放，网页从下方渐显
                        enter = EnterTransition.None,
                        exit = fadeOut(animationSpec = tween(LOADING_FADE_MS)),
                    ) {
                        StartupLoading(
                            text = stringResource(
                                if (viewModel.loadFinished) {
                                    R.string.web_ready_soon
                                } else {
                                    R.string.web_starting_harness
                                },
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    if (viewModel.loadError) {
                        LoadErrorContent(
                            authFailed = authFailed,
                            onRetry = {
                                authFailed = false
                                // 重试同样完整重走启动动画
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
                authFailed = false
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
}

/** 启动动画：MD3 Expressive 的形状过渡指示器（实验 API），下方一行说明文字。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StartupLoading(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 指示器默认容器 48dp，放大到 80dp（图形随容器等比缩放）
        LoadingIndicator(
            modifier = Modifier.size(80.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
                        imageVector = Icons.Filled.Refresh,
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
            imageVector = Icons.Filled.Warning,
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

@Composable
private fun LoadErrorContent(
    authFailed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = stringResource(
                if (authFailed) R.string.web_auth_failed else R.string.web_loading_failed,
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(
                if (authFailed) R.string.web_auth_failed_desc else R.string.web_loading_failed_desc,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

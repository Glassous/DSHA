package com.glassous.dsha.ui.web

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glassous.dsha.R
import com.glassous.dsha.ui.dshaViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    val profile = viewModel.profile
    val startUrl = viewModel.startUrl

    // 网页要自行避让的底部高度：键盘收起时等于导航栏（小白条）高度；键盘弹出时
    // WebView 已整体上移（imePadding），再注入导航栏高度会让输入框与键盘之间多出一段空隙。
    val density = LocalDensity.current
    val navBarsBottomPx = WindowInsets.navigationBars.getBottom(density)
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val bottomInsetPx = if (imeBottomPx > navBarsBottomPx) 0 else navBarsBottomPx

    LaunchedEffect(messageRes) {
        messageRes?.let { res ->
            snackbarHostState.showSnackbar(resources.getString(res))
            messageRes = null
        }
    }

    // 返回键优先回退网页历史，无法回退时交给系统走默认返回（回到组列表）
    BackHandler(enabled = viewModel.canGoBack) {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // 顶部交给 TopAppBar 自己伸入状态栏；底部不预留导航栏，让网页延伸到小白条区域，
        // 再由注入的底部抬起高度保证内容不被遮挡。
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = profile?.displayName ?: stringResource(R.string.app_title),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { webView?.reload() },
                            enabled = webView != null && !viewModel.loadError,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                if (viewModel.progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { viewModel.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        drawStopIndicator = {},
                    )
                }
            }
        },
    ) { innerPadding ->
        val currentProfile = profile
        val currentStartUrl = startUrl
        when {
            viewModel.loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

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
                        modifier = Modifier.fillMaxSize(),
                        bottomInsetPx = bottomInsetPx,
                        onWebViewCreated = { created -> webView = created },
                        onProgress = { value -> viewModel.progress = value },
                        onCanGoBackChanged = { value -> viewModel.canGoBack = value },
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

                    if (viewModel.loadError) {
                        LoadErrorContent(
                            authFailed = authFailed,
                            onRetry = {
                                authFailed = false
                                viewModel.loadError = false
                                webView?.reload()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
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

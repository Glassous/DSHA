package com.glassous.dsha.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.glassous.dsha.ui.dshaViewModel
import com.glassous.dsha.ui.profile.ProfileEditScreen
import com.glassous.dsha.ui.profile.ProfileListScreen
import com.glassous.dsha.ui.settings.OnboardingDialog
import com.glassous.dsha.ui.settings.SettingsScreen
import com.glassous.dsha.ui.settings.SettingsViewModel
import com.glassous.dsha.ui.web.WebScreen

/** 路由定义集中在这里，避免各界面硬编码字符串。 */
object DshaRoutes {

    const val ARG_PROFILE_ID = "profileId"

    const val PROFILES = "profiles"
    const val EDIT = "edit"
    const val SETTINGS = "settings"

    /** 新建（profileId 缺省）与编辑共用一条路由。 */
    const val EDIT_PATTERN = "$EDIT?$ARG_PROFILE_ID={$ARG_PROFILE_ID}"

    const val WEB_PATTERN = "web/{$ARG_PROFILE_ID}"

    fun edit(profileId: String? = null): String =
        if (profileId.isNullOrBlank()) EDIT else "$EDIT?$ARG_PROFILE_ID=${Uri.encode(profileId)}"

    fun web(profileId: String): String = "web/${Uri.encode(profileId)}"
}

/** 覆盖式转场时长：进入与退出用同一节奏，观感统一。 */
private const val TRANSITION_DURATION_MS = 300

@Composable
fun DshaNavHost(navController: NavHostController = rememberNavController()) {
    val settingsViewModel: SettingsViewModel = dshaViewModel { SettingsViewModel(it) }
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    // 弹窗被关掉后本次会话不再显示；未点"完成"确认时，下次启动仍会弹出
    var onboardingDismissed by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = DshaRoutes.PROFILES,
        // 覆盖式转场：新页面盖在旧页面上方从右滑入，旧页面保持原位不动（被覆盖）；
        // 返回时当前页向右滑出，露出下方静止的旧页面。
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(TRANSITION_DURATION_MS),
            )
        },
        // 旧页面保持原位不动、让新页面盖上去：用一段与滑入同时长的 alpha 1 → 1 动画
        // 占住整个转场时间，避免旧页面提前退场导致背后露出窗口底色。
        exitTransition = { fadeOut(targetAlpha = 1f, animationSpec = tween(TRANSITION_DURATION_MS)) },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(TRANSITION_DURATION_MS),
            )
        },
        predictivePopEnterTransition = { EnterTransition.None },
        predictivePopExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(TRANSITION_DURATION_MS),
            )
        },
    ) {
        composable(DshaRoutes.PROFILES) {
            ProfileListScreen(
                onConnect = { profileId -> navController.navigate(DshaRoutes.web(profileId)) },
                onEdit = { profileId -> navController.navigate(DshaRoutes.edit(profileId)) },
                onNew = { navController.navigate(DshaRoutes.edit()) },
                onSettings = { navController.navigate(DshaRoutes.SETTINGS) },
            )
        }

        composable(
            route = DshaRoutes.EDIT_PATTERN,
            arguments = listOf(
                navArgument(DshaRoutes.ARG_PROFILE_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            ProfileEditScreen(
                profileId = entry.arguments?.getString(DshaRoutes.ARG_PROFILE_ID),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = DshaRoutes.WEB_PATTERN,
            arguments = listOf(
                navArgument(DshaRoutes.ARG_PROFILE_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            WebScreen(
                profileId = entry.arguments?.getString(DshaRoutes.ARG_PROFILE_ID).orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(DshaRoutes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }

    // 快捷设置：首次启动（尚未完成确认）时弹出；点"完成"保存并标记后才不再出现
    val currentSettings = settings
    if (currentSettings != null && !currentSettings.onboardingCompleted && !onboardingDismissed) {
        OnboardingDialog(
            initialCredentialsEnabled = currentSettings.credentialsEnabled,
            initialAdaptiveCompatEnabled = currentSettings.adaptiveCompatEnabled,
            onDismiss = { onboardingDismissed = true },
            onConfirm = { credentialsEnabled, adaptiveCompatEnabled ->
                onboardingDismissed = true
                settingsViewModel.completeOnboarding(credentialsEnabled, adaptiveCompatEnabled)
            },
        )
    }
}
